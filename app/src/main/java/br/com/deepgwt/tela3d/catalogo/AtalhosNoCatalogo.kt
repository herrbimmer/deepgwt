package br.com.deepgwt.tela3d.catalogo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import br.com.deepgwt.ajustes.AtalhosDeAjuste
import br.com.deepgwt.ajustes.RegrasDosAtalhos

/**
 * Os atalhos de ajuste (Alpha 10.0) na grade de apps do launcher: cada um é uma entrada com um
 * "pacote" falso (`deepgwt.atalho:<id>`), o rótulo do ajuste e um ícone de interruptor. Quem abre o
 * "app" é a ponte, que reconhece o prefixo e liga ou desliga o ajuste em vez de abrir janela.
 */
internal object AtalhosNoCatalogo {
    private const val ETIQUETA = "AtalhosNoCatalogo"

    private val FUNDO = 0xFF202733.toInt()
    private val TRILHO = 0xFF3D8BFF.toInt()
    private val BOLINHA = 0xFFFFFFFF.toInt()

    /** Uma entrada por atalho, na ordem de criação. Nunca lança. */
    fun entradas(): List<EntradaDoCatalogo> = try {
        AtalhosDeAjuste.listar().map { id ->
            EntradaDoCatalogo(RegrasDosAtalhos.pacoteDe(id), AtalhosDeAjuste.rotulo(id), icone())
        }
    } catch (e: Exception) {
        Log.w(ETIQUETA, "Falha ao montar os atalhos de ajuste", e)
        emptyList()
    }

    /** Placa escura com um interruptor ligado no meio. PNG 96×96 como data URL. */
    private fun icone(): String? {
        val lado = CodificadorDeIcone.LADO
        val bitmap = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
        try {
            val tela = Canvas(bitmap)
            val tinta = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            val raio = GeometriaDoIconeDoCatalogo.raioDaPlaca(lado)
            tinta.color = FUNDO
            tela.drawRoundRect(RectF(0f, 0f, lado.toFloat(), lado.toFloat()), raio, raio, tinta)
            // O interruptor: trilho em pílula e a bolinha à direita (ligado).
            val larg = lado * 0.58f
            val alt = lado * 0.30f
            val esq = (lado - larg) / 2f
            val topo = (lado - alt) / 2f
            tinta.color = TRILHO
            tela.drawRoundRect(RectF(esq, topo, esq + larg, topo + alt), alt / 2f, alt / 2f, tinta)
            tinta.color = BOLINHA
            tela.drawCircle(esq + larg - alt / 2f, topo + alt / 2f, alt * 0.38f, tinta)
            return CodificadorDeIcone.bitmapParaDataUrl(bitmap)
        } finally {
            bitmap.recycle()
        }
    }
}
