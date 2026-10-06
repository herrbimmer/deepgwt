// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.painel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import java.io.InputStream

/**
 * Bitmaps do painel: decodificar o papel de parede, ajustar a 1920×720 (escala que cobre, corte
 * centrado), recortar pela forma do painel de fábrica, abrir o vão e pintar a cor com vinheta.
 * Sempre fora da principal (D3 e D2 da camada do display 1).
 */
internal object ImagensDoPainel {

    const val LARGURA = 1920
    const val ALTURA = 720

    /** Decodifica com amostragem (sem passar muito de [largura]×[altura]); null se não decodifica. */
    fun decodificar(contexto: Context, imagem: ImagemDoPainel, largura: Int = LARGURA, altura: Int = ALTURA): Bitmap? = try {
        val abrir: () -> InputStream = when (imagem) {
            is ImagemDoPainel.Arquivo -> { -> java.io.FileInputStream(imagem.caminho) }
            is ImagemDoPainel.Asset -> { -> contexto.assets.open(imagem.caminho) }
        }
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        abrir().use { BitmapFactory.decodeStream(it, null, limites) }
        if (limites.outWidth <= 0 || limites.outHeight <= 0) {
            null
        } else {
            var amostra = 1
            while (limites.outWidth / (amostra * 2) >= largura && limites.outHeight / (amostra * 2) >= altura) amostra *= 2
            val opcoes = BitmapFactory.Options().apply {
                inSampleSize = amostra
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            abrir().use { BitmapFactory.decodeStream(it, null, opcoes) }
        }
    } catch (e: Throwable) {
        null
    }

    /** Ajusta para cobrir [largura]×[altura] (escala que cobre, corte centrado), ARGB. */
    fun cobrir(origem: Bitmap, largura: Int = LARGURA, altura: Int = ALTURA): Bitmap {
        val saida = Bitmap.createBitmap(largura, altura, Bitmap.Config.ARGB_8888)
        val escala = maxOf(largura.toFloat() / origem.width, altura.toFloat() / origem.height)
        val w = origem.width * escala
        val h = origem.height * escala
        val matriz = Matrix().apply {
            setScale(escala, escala)
            postTranslate((largura - w) / 2f, (altura - h) / 2f)
        }
        Canvas(saida).drawBitmap(origem, matriz, Paint(Paint.FILTER_BITMAP_FLAG))
        return saida
    }

    /** L.2: o papel (já 1920×720) só dentro dos quatro retângulos do painel de fábrica. */
    fun recortarPelaForma(papel: Bitmap): Bitmap {
        val saida = Bitmap.createBitmap(LARGURA, ALTURA, Bitmap.Config.ARGB_8888)
        val tela = Canvas(saida)
        for (r in FormaDoPainelDeFabrica.RETANGULOS) {
            val area = Rect(r.esquerda, r.topo, r.direita, r.base)
            tela.drawBitmap(papel, area, area, null)
        }
        return saida
    }

    /** Uma cópia com [vao] (em coordenadas do painel) apagado; [deslocamento] = onde o bitmap começa. */
    fun comVao(origem: Bitmap, vao: Retangulo, deslocamentoX: Int = 0, deslocamentoY: Int = 0): Bitmap {
        val copia = origem.copy(Bitmap.Config.ARGB_8888, true)
        val limpar = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        Canvas(copia).drawRect(
            (vao.esquerda - deslocamentoX).toFloat(),
            (vao.topo - deslocamentoY).toFloat(),
            (vao.direita - deslocamentoX).toFloat(),
            (vao.base - deslocamentoY).toFloat(),
            limpar,
        )
        return copia
    }

    /** Um bitmap de cor sólida (ARGB). */
    fun solido(largura: Int, altura: Int, cor: Int): Bitmap =
        Bitmap.createBitmap(largura.coerceAtLeast(1), altura.coerceAtLeast(1), Bitmap.Config.ARGB_8888).apply { eraseColor(cor) }

    /** O pedaço [area] do papel 1920×720. */
    fun recorte(papel: Bitmap, area: Retangulo): Bitmap = Bitmap.createBitmap(papel, area.esquerda, area.topo, area.largura, area.altura)

    /**
     * B.1 da camada do display 1: 640×240 com a cor e, se houver vinheta, um gradiente radial
     * centrado (raio 0,62 × 640) de transparente (0) a transparente (0,45) a preto com o alfa da
     * vinheta (1), achatado na vertical por 240/640.
     */
    fun corComVinheta(cor: RegrasDoDisplay1.CorComVinheta): Bitmap {
        val w = RegrasDoDisplay1.LARGURA_DA_COR
        val h = RegrasDoDisplay1.ALTURA_DA_COR
        val saida = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val tela = Canvas(saida)
        tela.drawColor(Color.rgb(cor.vermelho, cor.verde, cor.azul))
        if (cor.vinheta > 0) {
            val cx = w / 2f
            val cy = h / 2f
            val gradiente = RadialGradient(
                cx, cy, RegrasDoDisplay1.RAIO_DA_VINHETA,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(cor.alfaDaVinheta, 0, 0, 0)),
                floatArrayOf(0f, RegrasDoDisplay1.INICIO_DA_VINHETA, 1f),
                Shader.TileMode.CLAMP,
            )
            gradiente.setLocalMatrix(Matrix().apply { setScale(1f, h.toFloat() / w, cx, cy) })
            tela.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { shader = gradiente })
        }
        return saida
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
