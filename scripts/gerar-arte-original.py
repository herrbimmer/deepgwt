#!/usr/bin/env python3
"""Gera as imagens de fundo e a arte do cabeçalho do OPEN GW — todas originais, feitas por código.

Existe porque as imagens que vinham antes não eram nossas:
  * 20 papéis de parede do Wallhaven (cada um de um autor, sem licença de redistribuição);
  * uma foto de divulgação da GWM baixada do site da marca.
Aqui cada pixel sai de funções matemáticas (gradientes, brilhos, linhas), então não há material de
terceiros e o resultado se reproduz com `python3 scripts/gerar-arte-original.py`.

Saídas:
  app/src/main/assets/backgrounds/opengw-01.jpg … opengw-20.jpg   (presets do fundo do cluster)
  app/src/main/assets/www/assets/backgrounds/opengw-01.jpg … 20   (presets do launcher 3D)
  instalador/public/header-art.jpg                                (cabeçalho do instalador)

Requer: Pillow e numpy.
"""
from __future__ import annotations

import math
import pathlib
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

RAIZ = pathlib.Path(__file__).resolve().parent.parent
PASTAS_DE_FUNDO = [
    RAIZ / "app/src/main/assets/backgrounds",
    RAIZ / "app/src/main/assets/www/assets/backgrounds",
]
ARTE_DO_CABECALHO = RAIZ / "instalador/public/header-art.jpg"

LARGURA, ALTURA = 1920, 1080

# Paletas sóbrias de painel de carro: nada de roxo, nada de rosa.
PALETAS = [
    ("azul", (36, 120, 255), (0, 200, 255)),
    ("verde-agua", (0, 205, 170), (70, 225, 120)),
    ("ambar", (255, 176, 40), (255, 96, 32)),
    ("vermelho", (235, 52, 62), (255, 128, 84)),
]
PADROES = ["horizonte", "aurora", "faixas", "aneis", "malha"]


def coordenadas(w: int, h: int):
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    return x / w, y / h


def cor(rgb) -> np.ndarray:
    return np.array(rgb, dtype=np.float32) / 255.0


def brilho(x, y, cx, cy, rx, ry, rgb, forca):
    """Mancha suave (gaussiana elíptica) de uma cor."""
    d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2
    return np.exp(-d)[..., None] * cor(rgb) * forca


def suave(a, b, t):
    t = np.clip((t - a) / (b - a), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def base_grafite(x, y):
    """Fundo quase preto com leve subida de tom para o centro."""
    centro = np.exp(-(((x - 0.5) / 0.9) ** 2 + ((y - 0.5) / 0.9) ** 2))
    tom = 0.020 + 0.030 * centro
    return np.stack([tom, tom * 1.02, tom * 1.08], axis=-1).astype(np.float32)


def horizonte(x, y, a, b, v):
    hy = 0.56 + 0.04 * v
    img = base_grafite(x, y)
    img += brilho(x, y, 0.5, hy, 0.55, 0.05, a, 0.95)
    img += brilho(x, y, 0.5, hy - 0.10, 0.9, 0.30, b, 0.20)
    chao = suave(hy, 1.0, y) * (1 - suave(hy, hy + 0.04, y))
    img += (chao[..., None] * cor(a)) * 0.10
    # Estrada em perspectiva: duas bordas que convergem no ponto de fuga.
    camada = Image.new("L", (LARGURA, ALTURA), 0)
    d = ImageDraw.Draw(camada)
    vx, vy = LARGURA * 0.5, ALTURA * hy
    for lado in (-1, 1):
        d.line([(vx + lado * 6, vy), (vx + lado * LARGURA * (0.34 + 0.06 * v), ALTURA)], fill=255, width=3)
    for t in np.linspace(0.08, 1.0, 9):
        yy = vy + (ALTURA - vy) * t * t
        meio = 0.5 * LARGURA
        largura = 4 + 22 * t * t
        d.line([(meio, yy), (meio, yy + 22 * t * t)], fill=170, width=int(largura))
    estrada = np.asarray(camada.filter(ImageFilter.GaussianBlur(1.4)), dtype=np.float32) / 255.0
    img += (estrada * (0.2 + 0.8 * suave(vy, ALTURA, y * ALTURA)))[..., None] * cor(a) * 0.55
    return img


def aurora(x, y, a, b, v):
    img = base_grafite(x, y)
    img += brilho(x, y, 0.22 + 0.10 * v, 0.30, 0.34, 0.26, a, 0.80)
    img += brilho(x, y, 0.78 - 0.08 * v, 0.70, 0.40, 0.30, b, 0.62)
    img += brilho(x, y, 0.55, 0.50, 0.55, 0.20, a, 0.14)
    return img


def faixas(x, y, a, b, v):
    img = base_grafite(x, y)
    ang = math.radians(24 + 8 * v)
    s = x * math.cos(ang) + y * math.sin(ang)
    for pos, larg, rgb, f in [(0.52, 0.060, a, 0.85), (0.70, 0.020, b, 0.70), (0.34, 0.012, b, 0.45), (0.86, 0.040, a, 0.30)]:
        faixa = np.exp(-(((s - pos - 0.03 * v) / larg) ** 2))
        img += faixa[..., None] * cor(rgb) * f * (0.55 + 0.45 * np.exp(-((y - 0.5) / 0.55) ** 2))[..., None]
    img += brilho(x, y, 0.62, 0.45, 0.50, 0.40, a, 0.10)
    return img


def aneis(x, y, a, b, v):
    img = base_grafite(x, y)
    cx, cy = 0.5 + 0.18 * (v - 0.5), 0.52
    r = np.sqrt(((x - cx) * (LARGURA / ALTURA)) ** 2 + (y - cy) ** 2)
    frac = (r * 7.0) % 1.0
    aneis_ = np.exp(-(((frac - 0.5) / 0.035) ** 2)) * np.exp(-r * 1.6)
    img += aneis_[..., None] * cor(a) * 0.70
    miolo = np.exp(-((r / 0.16) ** 2))
    img += miolo[..., None] * cor(b) * 0.45
    return img


def malha(x, y, a, b, v):
    hy = 0.50 + 0.05 * v
    img = base_grafite(x, y)
    img += brilho(x, y, 0.5, hy, 0.60, 0.06, a, 0.90)
    img += brilho(x, y, 0.5, hy - 0.12, 0.85, 0.30, b, 0.16)
    abaixo = y > hy
    prof = np.where(abaixo, 1.0 / np.maximum(y - hy, 1e-3), 1.0)
    lin_h = np.abs(((prof * 0.30) % 1.0) - 0.5)
    lin_v = np.abs((((x - 0.5) * prof * 2.4) % 1.0) - 0.5)
    linhas = np.maximum(np.exp(-((lin_h / 0.05) ** 2)), np.exp(-((lin_v / 0.04) ** 2)))
    img += (linhas * suave(hy + 0.03, hy + 0.22, y) * 0.60)[..., None] * cor(a)
    return img


GERADORES = {"horizonte": horizonte, "aurora": aurora, "faixas": faixas, "aneis": aneis, "malha": malha}


def finalizar(img: np.ndarray, semente: int) -> Image.Image:
    h, w, _ = img.shape
    x, y = coordenadas(w, h)
    vinheta = 1.0 - 0.38 * (((x - 0.5) / 0.75) ** 2 + ((y - 0.5) / 0.75) ** 2)
    img = img * np.clip(vinheta, 0.45, 1.0)[..., None]
    img = 1.0 - np.exp(-img * 1.45)  # compressão suave, sem estourar o branco
    rng = np.random.default_rng(semente)
    img = img + rng.normal(0.0, 0.8 / 255.0, img.shape).astype(np.float32)  # evita faixas no escuro
    return Image.fromarray((np.clip(img, 0, 1) * 255.0 + 0.5).astype(np.uint8))


def gerar_fundos() -> list[str]:
    nomes: list[str] = []
    indice = 0
    x, y = coordenadas(LARGURA, ALTURA)
    for padrao in PADROES:
        for p, (_, a, b) in enumerate(PALETAS):
            indice += 1
            variacao = (indice % 4) / 3.0
            imagem = finalizar(GERADORES[padrao](x, y, a, b, variacao), 1000 + indice)
            nome = f"opengw-{indice:02d}.jpg"
            for pasta in PASTAS_DE_FUNDO:
                pasta.mkdir(parents=True, exist_ok=True)
                imagem.save(pasta / nome, "JPEG", quality=86, optimize=True, progressive=True)
            nomes.append(f"{nome} — {padrao}, paleta {PALETAS[p][0]}")
    return nomes


def gerar_cabecalho() -> None:
    w, h = 1600, 640
    x, y = coordenadas(w, h)
    img = base_grafite(x, y)
    img += brilho(x, y, 0.50, 0.62, 0.46, 0.06, (36, 120, 255), 0.95)
    img += brilho(x, y, 0.50, 0.40, 0.70, 0.40, (0, 200, 255), 0.13)
    camada = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(camada)
    vx, vy = w * 0.5, h * 0.62
    for lado in (-1, 1):
        d.line([(vx + lado * 5, vy), (vx + lado * w * 0.36, h)], fill=255, width=3)
    estrada = np.asarray(camada.filter(ImageFilter.GaussianBlur(1.2)), dtype=np.float32) / 255.0
    img += (estrada * suave(vy / h, 1.0, y))[..., None] * cor((36, 120, 255)) * 0.55
    imagem = finalizar(img, 7)
    ARTE_DO_CABECALHO.parent.mkdir(parents=True, exist_ok=True)
    imagem.save(ARTE_DO_CABECALHO, "JPEG", quality=88, optimize=True, progressive=True)


def escrever_fontes(nomes: list[str]) -> None:
    linhas = [
        "# Presets de fundo do cluster e do launcher",
        "",
        "Imagens **originais do OPEN GW**, geradas por código — nenhuma foi baixada ou copiada de",
        "terceiros. Cada pixel sai de gradientes, brilhos e linhas calculados em",
        "`scripts/gerar-arte-original.py`; para reproduzir: `python3 scripts/gerar-arte-original.py`.",
        "",
        "Elas substituem os 20 papéis de parede do Wallhaven que vinham aqui até a Alpha 9.6 (cada um",
        "de um autor, sem licença de redistribuição). Os nomes antigos (`wallhaven-*.jpg`) continuam",
        "valendo para quem os tinha escolhido: o app os troca pelo equivalente novo (ver",
        "`utils/PresetBackgrounds.kt` e `LEGACY_WALLHAVEN_IDS` no launcher).",
        "",
        "| Arquivo | Origem |",
        "| --- | --- |",
    ]
    linhas += [f"| `{n.split(' — ')[0]}` | gerada ({n.split(' — ')[1]}) |" for n in nomes]
    linhas += ["", f"Total: {len(nomes)} imagens, 1920×1080, licença do OPEN GW (ver `LICENSE`).", ""]
    for pasta in PASTAS_DE_FUNDO:
        (pasta / "SOURCES.md").write_text("\n".join(linhas), encoding="utf-8")


if __name__ == "__main__":
    lista = gerar_fundos()
    gerar_cabecalho()
    escrever_fontes(lista)
    print(f"{len(lista)} fundos + cabeçalho gerados", file=sys.stderr)
