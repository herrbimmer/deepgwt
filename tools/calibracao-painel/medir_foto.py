#!/usr/bin/env python3
# Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
"""Mede, em px do painel (1920x720), o que o carro desenha por cima da grade de calibração.

Entrada: foto (ou quadro de vídeo) do painel com a grade da Calibração do painel (Alpha 8.8) e as
quatro marcas de canto visíveis. Saída na pasta --saida:
  retificada.png  a foto corrigida para 1920x720 (perspectiva + lente)
  caixas.png      a retificada com as caixas detectadas
  deteccoes.json  caixas por cor (vermelho, ambar, verde) em px do painel

Método: homografia pelas 4 marcas (centros em 50,50 / 1870,50 / 50,673 / 1870,670 — o triângulo
azul tem o centroide em y=673), refinada por centenas de pontos das linhas de 100 px da grade e
uma correção cúbica de lente. O script imprime o erro: rms de 1 a 2 px é o esperado.

Uso:
  python3 medir_foto.py foto.jpg --marcas "102,202 1171,211 118,571 1153,558" --saida saida/
As coordenadas de --marcas são aproximadas (±15 px na foto), na ordem vermelho, verde, azul, amarelo.
Só depende de numpy e Pillow.
"""
import argparse, json, os
from collections import deque
import numpy as np
from PIL import Image, ImageDraw

W, H = 1920, 720
DISPLAY = {'r': (50, 50), 'g': (1870, 50), 'b': (50, 673), 'y': (1870, 670)}


def mascara_marca(a, k):
    r, g, b = [a[..., i].astype(int) for i in range(3)]
    if k == 'r': return (r > 150) & (g < 110) & (b < 120)
    if k == 'g': return (g > 150) & (r < 130) & (b < 150)
    if k == 'b': return (b > 150) & (r < 110) & (g < 170) & (b > g + 20)
    return (r > 180) & (g > 150) & (b < 110)


def homografia(src, dst):
    A = []
    for (x, y), (u, v) in zip(src, dst):
        A.append([x, y, 1, 0, 0, 0, -u * x, -u * y, -u])
        A.append([0, 0, 0, x, y, 1, -v * x, -v * y, -v])
    _, _, vt = np.linalg.svd(np.array(A, float))
    return vt[-1].reshape(3, 3) / vt[-1][-1]


def aplica(Hm, x, y):
    x = np.asarray(x, float); y = np.asarray(y, float)
    w = Hm[2, 0] * x + Hm[2, 1] * y + Hm[2, 2]
    return (Hm[0, 0] * x + Hm[0, 1] * y + Hm[0, 2]) / w, (Hm[1, 0] * x + Hm[1, 1] * y + Hm[1, 2]) / w


def bilinear(img, u, v):
    u = np.clip(u, 0, img.shape[1] - 2); v = np.clip(v, 0, img.shape[0] - 2)
    x0 = np.floor(u).astype(int); y0 = np.floor(v).astype(int); fx = u - x0; fy = v - y0
    return (img[y0, x0] * (1 - fx) * (1 - fy) + img[y0, x0 + 1] * fx * (1 - fy)
            + img[y0 + 1, x0] * (1 - fx) * fy + img[y0 + 1, x0 + 1] * fx * fy)


def base(x, y, grau=3):
    x = np.asarray(x, float) / W - .5; y = np.asarray(y, float) / H - .5
    return np.stack([x ** i * y ** j for i in range(grau + 1) for j in range(grau + 1 - i)], -1)


def componentes(m, minimo=25, folga=3):
    alt, larg = m.shape; rot = np.zeros((alt, larg), bool); saida = []
    for p in zip(*np.nonzero(m)):
        if rot[p]: continue
        fila = deque([p]); rot[p] = True; membros = [p]
        while fila:
            y, x = fila.popleft()
            for dy in range(-folga, folga + 1):
                for dx in range(-folga, folga + 1):
                    yy, xx = y + dy, x + dx
                    if 0 <= yy < alt and 0 <= xx < larg and m[yy, xx] and not rot[yy, xx]:
                        rot[yy, xx] = True; fila.append((yy, xx)); membros.append((yy, xx))
        if len(membros) >= minimo:
            a = np.array(membros)
            saida.append([int(a[:, 1].min()), int(a[:, 0].min()), int(a[:, 1].max()), int(a[:, 0].max()), len(membros)])
    return saida


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('foto')
    ap.add_argument('--marcas', required=True, help='"rx,ry gx,gy bx,by yx,yy" aproximados na foto')
    ap.add_argument('--saida', default='.')
    args = ap.parse_args()
    os.makedirs(args.saida, exist_ok=True)
    a = np.asarray(Image.open(args.foto).convert('RGB')).astype(float)
    aprox = [tuple(int(v) for v in p.split(',')) for p in args.marcas.split()]

    dst = []
    for k, (x, y) in zip('rgby', aprox):
        R = 22
        ys, xs = np.nonzero(mascara_marca(a[y - R:y + R, x - R:x + R], k))
        if len(xs) < 20: raise SystemExit(f'marca {k} não encontrada perto de {x},{y}')
        dst.append((xs.mean() + x - R, ys.mean() + y - R))
    H0 = homografia([DISPLAY[k] for k in 'rgby'], dst)

    # Linhas azuladas da grade: azul - metade do vermelho apaga texto branco e amarelo.
    L = a[..., 2] - 0.5 * a[..., 0]
    D = np.arange(-9, 9.01, 0.25)
    pd, pf = [], []

    def mede(vertical, C, amostras):
        for s in amostras:
            xs, ys = (C + D, np.full_like(D, s)) if vertical else (np.full_like(D, s), C + D)
            perfil = bilinear(L, *aplica(H0, xs, ys))
            i = int(np.argmax(perfil))
            if i < 2 or i > len(D) - 3 or perfil[i] - np.median(perfil) < 12: continue
            y1, y2, y3 = perfil[i - 1], perfil[i], perfil[i + 1]; den = y1 - 2 * y2 + y3
            off = D[i] + (0.125 * (y1 - y3) / den if den else 0)
            X, Y = (C + off, s) if vertical else (s, C + off)
            u, v = aplica(H0, X, Y)
            pd.append((C, s) if vertical else (s, C)); pf.append((float(u), float(v)))

    for X in range(100, 1900, 100): mede(True, X, [125, 165, 225, 265, 345, 375, 425, 465, 525, 565, 625, 655])
    for Y in range(100, 700, 100): mede(False, Y, [x + 60 for x in range(100, 1800, 100)] + [x + 85 for x in range(100, 1800, 100)])
    P = np.array(pf); Dd = np.array(pd)
    u0, v0 = aplica(H0, Dd[:, 0], Dd[:, 1]); B = base(Dd[:, 0], Dd[:, 1])
    ok = np.ones(len(P), bool)
    for _ in range(2):
        cu, *_ = np.linalg.lstsq(B[ok], (P[:, 0] - u0)[ok], rcond=None)
        cv, *_ = np.linalg.lstsq(B[ok], (P[:, 1] - v0)[ok], rcond=None)
        ok = np.hypot(P[:, 0] - u0 - B @ cu, P[:, 1] - v0 - B @ cv) < 1.5
    Hi = np.linalg.inv(H0)
    x0, y0 = aplica(Hi, P[ok, 0], P[ok, 1]); Bi = base(x0, y0)
    ciu, *_ = np.linalg.lstsq(Bi, Dd[ok, 0] - x0, rcond=None)
    civ, *_ = np.linalg.lstsq(Bi, Dd[ok, 1] - y0, rcond=None)
    err = np.hypot(Dd[ok, 0] - x0 - Bi @ ciu, Dd[ok, 1] - y0 - Bi @ civ)
    print(f'pontos da grade: {ok.sum()}/{len(P)} · erro em px do painel: rms {np.sqrt((err ** 2).mean()):.2f}, máx {err.max():.2f}')

    gx, gy = np.meshgrid(np.arange(W) + .5, np.arange(H) + .5)
    uu, vv = aplica(H0, gx, gy); Bg = base(gx.ravel(), gy.ravel())
    uu = uu + (Bg @ cu).reshape(uu.shape); vv = vv + (Bg @ cv).reshape(vv.shape)
    ret = np.clip(np.stack([bilinear(a[..., c], uu, vv) for c in range(3)], -1), 0, 255).astype(np.uint8)
    Image.fromarray(ret).save(os.path.join(args.saida, 'retificada.png'))

    r, g, b = [ret[..., i].astype(int) for i in range(3)]
    verm = (r > 140) & (r > g + 70) & (r > b + 50) & (g < 120)
    cores = {
        'vermelho': verm,
        'ambar': (r > 170) & (g > 90) & (b < 110) & (r > b + 90) & ~verm,
        'verde': (g > 140) & (g > r + 50) & (g > b + 20),
    }
    # O que é da própria grade: as marcas coloridas e a linha amarela "Avisos recebidos".
    fora = np.zeros((H, W), bool)
    fora[28:72, 28:72] = True; fora[28:72, 1848:1892] = True; fora[645:695, 1845:1895] = True
    cores['ambar'][280:306, 70:1850] = False
    img = Image.fromarray(ret); d = ImageDraw.Draw(img)
    tinta = {'vermelho': (255, 60, 60), 'ambar': (255, 170, 0), 'verde': (0, 255, 0)}
    res = []
    for k, m in cores.items():
        for x0_, y0_, x1_, y1_, n in componentes(m & ~fora):
            res.append({'cor': k, 'x': x0_, 'y': y0_, 'w': x1_ - x0_ + 1, 'h': y1_ - y0_ + 1, 'px': n})
            d.rectangle([x0_ - 2, y0_ - 2, x1_ + 2, y1_ + 2], outline=tinta[k])
    res.sort(key=lambda z: (z['x'], z['y']))
    img.save(os.path.join(args.saida, 'caixas.png'))
    json.dump({'erro_rms_px': round(float(np.sqrt((err ** 2).mean())), 2), 'caixas': res},
              open(os.path.join(args.saida, 'deteccoes.json'), 'w'), ensure_ascii=False, indent=1)
    print(f'{len(res)} caixas → {args.saida}')


if __name__ == '__main__':
    main()
# Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
