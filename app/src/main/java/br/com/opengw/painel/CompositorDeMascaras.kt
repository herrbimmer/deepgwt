package br.com.opengw.painel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color

/** Um pedaço das máscaras nativas: o bitmap, onde fica e a opacidade. */
internal class PecaDaMascara(val bitmap: Bitmap, val x: Int, val y: Int, val largura: Int, val altura: Int, val opacidade: Float)

/** O resultado da composição (L.1): as peças a mostrar, por baixo do WebView. */
internal class MascarasCompostas(val pecas: List<PecaDaMascara>)

/**
 * Compõe as máscaras nativas fora da principal (L.9, D3). Guarda a máscara global sem vão enquanto
 * (tema, papel) não mudar; com vão, aplica numa cópia.
 */
internal class CompositorDeMascaras(private val contexto: Context, private val registro: RegistroDoPainel) {

    private var chaveDaGlobal: Any? = null
    private var global: Bitmap? = null

    @Synchronized
    fun esquecer() {
        chaveDaGlobal = null
        global = null
    }

    @Synchronized
    fun compor(plano: PlanoDasMascaras): MascarasCompostas? {
        // (a) Máscara global.
        val papel = plano.papel
        if (papel != null) {
            val base = globalSemVao(plano.tema, papel)
            if (base != null) {
                val bitmap = plano.vao?.let { ImagensDoPainel.comVao(base, it) } ?: base
                return MascarasCompostas(listOf(PecaDaMascara(bitmap, 0, 0, ImagensDoPainel.LARGURA, ImagensDoPainel.ALTURA, 1f)))
            }
        }
        // (b) Por elemento (sem papel: a imagem do tema, ou transparente/preta pelo tamanho).
        if (plano.elementos.isEmpty()) return null
        val pecas = ArrayList<PecaDaMascara>()
        for (e in plano.elementos) {
            val area = e.area
            var bitmap: Bitmap? = e.imagem?.let { imagem ->
                ImagensDoPainel.decodificar(contexto, imagem, area.largura, area.altura)?.let {
                    if (it.width == area.largura && it.height == area.altura) it else Bitmap.createScaledBitmap(it, area.largura, area.altura, true)
                }
            }
            if (bitmap == null) {
                if (e.transparenteSemImagem) continue
                bitmap = ImagensDoPainel.solido(area.largura, area.altura, Color.BLACK)
            }
            val vao = plano.vao
            if (vao != null && vao.cruza(area)) bitmap = ImagensDoPainel.comVao(bitmap, vao, area.esquerda, area.topo)
            pecas.add(PecaDaMascara(bitmap, area.esquerda, area.topo, area.largura, area.altura, e.opacidade))
        }
        return if (pecas.isEmpty()) null else MascarasCompostas(pecas)
    }

    private fun globalSemVao(tema: String, papel: ImagemDoPainel): Bitmap? {
        val chave = tema to papel
        if (chave == chaveDaGlobal) global?.let { return it }
        val decodificado = ImagensDoPainel.decodificar(contexto, papel)
        if (decodificado == null) {
            registro.info("máscara global: o papel de parede não decodificou ($papel)")
            chaveDaGlobal = null
            global = null
            return null
        }
        // Os intermediários nunca foram mostrados: podem ser reciclados já.
        val coberto = ImagensDoPainel.cobrir(decodificado)
        decodificado.recycle()
        val recortado = ImagensDoPainel.recortarPelaForma(coberto)
        coberto.recycle()
        chaveDaGlobal = chave
        global = recortado
        return recortado
    }
}
