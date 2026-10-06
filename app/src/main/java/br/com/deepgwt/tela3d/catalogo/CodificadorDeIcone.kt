package br.com.deepgwt.tela3d.catalogo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import java.io.ByteArrayOutputStream

/** Desenha um ícone em PNG 96×96 e devolve como data URL. Só desenha cópias; não suja o drawable de origem. */
internal object CodificadorDeIcone {
    /** Lado dos ícones entregues à página. */
    const val LADO = GeometriaDoIconeDoCatalogo.LADO

    /**
     * Ícone do catálogo: bitmap ARGB 96×96 transparente. Com [corDaPlaca], pinta antes um retângulo
     * arredondado (raio 0,22 × 96) e desenha o ícone recuado 15 px de cada lado; sem placa, o ícone
     * ocupa tudo. Ícone nulo: null.
     */
    fun paraDataUrl(icone: Drawable?, corDaPlaca: Int?): String? {
        if (icone == null) return null
        val bitmap = Bitmap.createBitmap(LADO, LADO, Bitmap.Config.ARGB_8888)
        try {
            val tela = Canvas(bitmap)
            if (corDaPlaca != null) {
                val tinta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = corDaPlaca
                }
                val raio = GeometriaDoIconeDoCatalogo.raioDaPlaca(LADO)
                tela.drawRoundRect(RectF(0f, 0f, LADO.toFloat(), LADO.toFloat()), raio, raio, tinta)
                val recuo = GeometriaDoIconeDoCatalogo.recuoDoIcone(LADO)
                DrawablesSeguros.desenhar(icone, tela, recuo, recuo, LADO - recuo, LADO - recuo)
            } else {
                DrawablesSeguros.desenhar(icone, tela, 0, 0, LADO, LADO)
            }
            return bitmapParaDataUrl(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    /** PNG qualidade 100 como `data:image/png;base64,...` (sem quebras de linha). */
    fun bitmapParaDataUrl(bitmap: Bitmap): String? {
        val saida = ByteArrayOutputStream(8 * 1024)
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, saida)) return null
        return JsonDoCatalogo.dataUrlDePng(saida.toByteArray())
    }
}
