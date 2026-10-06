package br.com.deepgwt.tela3d.catalogo

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log

/**
 * Ícone adaptativo sem máscara: o primeiro plano é ampliado até o conteúdo ocupar cerca de 82 % do
 * lado, e o fundo e o primeiro plano são desenhados num quadrado cheio (sem círculo/squircle).
 *
 * A parte numérica é pura (testada em JVM); o desenho usa o Android.
 */
internal object NormalizacaoDeIcone {
    /** Lado da medição do primeiro plano, em pixels. */
    const val LADO_DA_MEDICAO = 96

    /** Alfa (de 255) acima do qual um pixel conta como conteúdo. */
    const val ALFA_MINIMO = 16

    const val OCUPACAO_ALVO = 0.82f
    const val FRACAO_MINIMA = 0.35f
    const val ESCALA_MINIMA = 1.0f
    const val ESCALA_MAXIMA = 1.6f

    /**
     * Fração do lado que o conteúdo do primeiro plano ocupa: a maior dimensão da menor caixa que
     * contém todo pixel com alfa > [ALFA_MINIMO], dividida por [LADO_DA_MEDICAO] (a caixa conta
     * `máx − mín + 1` pixels). Nada visível: 1.0.
     *
     * @param pixels pixels ARGB, linha por linha, [largura] × [altura]
     */
    fun fracaoDeConteudo(pixels: IntArray, largura: Int, altura: Int, ladoDeReferencia: Int = LADO_DA_MEDICAO): Float {
        if (largura <= 0 || altura <= 0 || pixels.size < largura * altura || ladoDeReferencia <= 0) return 1.0f
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = -1
        var maxY = -1
        for (y in 0 until altura) {
            val base = y * largura
            for (x in 0 until largura) {
                val alfa = pixels[base + x] ushr 24
                if (alfa > ALFA_MINIMO) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < 0) return 1.0f
        val caixaL = maxX - minX + 1
        val caixaA = maxY - minY + 1
        return maxOf(caixaL, caixaA) / ladoDeReferencia.toFloat()
    }

    /** `0.82 / max(fração, 0.35)`, limitada a [1.0, 1.6]: amplia, nunca reduz. */
    fun escalaDePreenchimento(fracao: Float): Float {
        val escala = OCUPACAO_ALVO / maxOf(fracao, FRACAO_MINIMA)
        return minOf(maxOf(escala, ESCALA_MINIMA), ESCALA_MAXIMA)
    }

    /**
     * Deslocamento `d` (≤ 0) dos limites `(d, d, T − d, T − d)`: `−round(T × (escala − 1) / 2)`,
     * arredondando meio para cima.
     */
    fun deslocamento(lado: Int, escala: Float): Int {
        val d = Math.round(lado * (escala - 1f) / 2f)
        return -d
    }

    /**
     * Normaliza o ícone para um bitmap quadrado [lado]×[lado]. Se não é adaptativo (ou o lado é
     * ≤ 0, ou o ícone é nulo) devolve o MESMO ícone. Qualquer exceção: loga e devolve o original.
     * Não mexe nos bounds nem no estado do drawable recebido (que pode ser compartilhado).
     */
    fun normalizar(recursos: Resources, icone: Drawable?, lado: Int): Drawable? {
        if (icone == null || lado <= 0 || icone !is AdaptiveIconDrawable) return icone
        return try {
            val frente = icone.foreground
            val fundo = icone.background
            val fracao = medirFracao(frente)
            val escala = escalaDePreenchimento(fracao)
            val d = deslocamento(lado, escala)
            val bitmap = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
            val tela = Canvas(bitmap)
            DrawablesSeguros.desenhar(fundo, tela, d, d, lado - d, lado - d)
            DrawablesSeguros.desenhar(frente, tela, d, d, lado - d, lado - d)
            BitmapDrawable(recursos, bitmap)
        } catch (e: Exception) {
            Log.w(ETIQUETA, "Falha ao normalizar ícone adaptativo", e)
            icone
        }
    }

    private fun medirFracao(frente: Drawable?): Float {
        if (frente == null) return 1.0f
        return try {
            val lado = LADO_DA_MEDICAO
            val bitmap = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
            DrawablesSeguros.desenhar(frente, Canvas(bitmap), 0, 0, lado, lado)
            val pixels = IntArray(lado * lado)
            bitmap.getPixels(pixels, 0, lado, 0, 0, lado, lado)
            bitmap.recycle()
            fracaoDeConteudo(pixels, lado, lado)
        } catch (e: Exception) {
            1.0f
        }
    }

    private const val ETIQUETA = "DeepGWT/Catalogo"
}

/** Desenho de drawables que podem ser compartilhados com o sistema sem sujar bounds/estado. */
internal object DrawablesSeguros {
    /** Cópia mutável: `constantState.newDrawable().mutate()`; sem constantState, o próprio `mutate()`. */
    fun copiaMutavel(drawable: Drawable): Drawable {
        val estado = drawable.constantState
        return estado?.newDrawable()?.mutate() ?: drawable.mutate()
    }

    /**
     * Desenha [drawable] em [tela] nos limites dados, numa cópia; os bounds do original ficam como
     * estavam. Drawable nulo: não faz nada.
     */
    fun desenhar(drawable: Drawable?, tela: Canvas, esquerda: Int, topo: Int, direita: Int, base: Int) {
        if (drawable == null) return
        val alvo = copiaMutavel(drawable)
        val antigos = alvo.copyBounds()
        try {
            alvo.setBounds(esquerda, topo, direita, base)
            alvo.draw(tela)
        } finally {
            alvo.setBounds(antigos)
        }
    }
}
