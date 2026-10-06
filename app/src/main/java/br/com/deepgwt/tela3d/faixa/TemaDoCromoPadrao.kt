package br.com.deepgwt.tela3d.faixa

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import br.com.deepgwt.tela3d.catalogo.MedidasDaPlacaDoDock
import br.com.deepgwt.tela3d.catalogo.PlacaDoItem
import kotlin.math.roundToInt

/**
 * Tema do cromo nativo: tema claro/escuro, acento e os materiais (vidro fosco, placa, painel de popup,
 * fundo de item de menu, placas de ícone). Os materiais são desenhados na hora e refletem o estado atual:
 * quem os guarda precisa refazê-los quando [versao] mudar.
 */
class TemaDoCromoPadrao(contexto: Context, acentoInicial: Int = CoresDoCromo.ACENTO_PADRAO) : TemaDoCromo {
    private val d = Densidade(contexto)

    override var versao: Int = 0
        private set
    override var claro: Boolean = false
        private set
    override var acento: Int = acentoInicial
        private set

    override val corDeRotulo: Int get() = CoresDoCromo.corDeRotulo(claro)
    override val corDeRotuloMuted: Int get() = CoresDoCromo.corDeRotuloMuted(claro)
    override val corDeGlifo: Int get() = CoresDoCromo.corDeGlifo(claro)

    /** Muda tema e acento; devolve se algo mudou (e então [versao] sobe). */
    fun atualizar(claro: Boolean = this.claro, acento: Int = this.acento): Boolean {
        if (claro == this.claro && acento == this.acento) return false
        this.claro = claro
        this.acento = acento
        versao++
        return true
    }

    override fun misturar(a: Int, b: Int, fracao: Float): Int = CoresDoCromo.misturar(a, b, fracao)

    // --- vidro fosco (A3) ---

    override fun materialVidroFosco(selecionado: Boolean, lavagem: Int): Drawable {
        val normal = vidro(selecionado = false, lavagem = lavagem)
        val marcado = vidro(selecionado = true, lavagem = lavagem)
        val estados = StateListDrawable()
        estados.addState(intArrayOf(android.R.attr.state_pressed), marcado)
        estados.addState(intArrayOf(android.R.attr.state_selected), marcado)
        estados.addState(intArrayOf(), if (selecionado) vidro(selecionado = true, lavagem = lavagem) else normal)
        return estados
    }

    private fun vidro(selecionado: Boolean, lavagem: Int): Drawable {
        var corpoCima = if (claro) 0xF2FFFFFF.toInt() else 0xE6303034.toInt()
        var corpoBaixo = if (claro) 0xE6F2F2F7.toInt() else 0xEB202023.toInt()
        val aroCima = if (claro) 0x1F000000 else 0x3DFFFFFF
        val aroBaixo = if (claro) 0x0D000000 else 0x14FFFFFF
        if (lavagem != 0) corpoBaixo = misturar(corpoBaixo, lavagem, if (claro) 0.16f else 0.22f)
        if (selecionado) {
            val alvo = if (claro) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            corpoCima = misturar(corpoCima, alvo, 0.10f)
            corpoBaixo = misturar(corpoBaixo, alvo, 0.08f)
        }
        val inset = maxOf(1, (0.75f * d.valor).roundToInt())
        return camadas(
            gradiente(aroCima, aroBaixo, d.px(26)),
            gradiente(corpoCima, corpoBaixo, d.px(25)),
            inset,
        )
    }

    // --- placa dos chips (A12) ---

    override fun materialPlaca(selecionada: Boolean): Drawable {
        val corpoCima: Int
        val corpoBaixo: Int
        val aroCima: Int
        val aroBaixo: Int
        if (!claro) {
            if (selecionada) {
                corpoCima = 0xF2454549.toInt(); corpoBaixo = 0xF2303034.toInt()
                aroCima = 0x70FFFFFF; aroBaixo = 0x12FFFFFF
            } else {
                corpoCima = 0xDC3A3A3E.toInt(); corpoBaixo = 0xE2262629.toInt()
                aroCima = 0x33FFFFFF; aroBaixo = 0x12FFFFFF
            }
        } else {
            if (selecionada) {
                corpoCima = 0xFFFFFFFF.toInt(); corpoBaixo = 0xFFE9EEF5.toInt()
                aroCima = 0x66000000; aroBaixo = 0x0F000000
            } else {
                corpoCima = 0xFAFFFFFF.toInt(); corpoBaixo = 0xF0EFEFF4.toInt()
                aroCima = 0x1F000000; aroBaixo = 0x0F000000
            }
        }
        val inset = maxOf(1, (0.75f * d.valor).roundToInt())
        return camadas(gradiente(aroCima, aroBaixo, d.px(16)), gradiente(corpoCima, corpoBaixo, d.px(15)), inset)
    }

    // --- popups ---

    override fun painelDePopup(): GradientDrawable {
        val fundo = GradientDrawable()
        fundo.cornerRadius = d.px(14).toFloat()
        fundo.setColor(if (claro) 0xFCF7FAFC.toInt() else 0xFA0B1016.toInt())
        fundo.setStroke(d.contorno(), if (claro) 0x2225303B else 0x26FFFFFF)
        return fundo
    }

    override fun fundoDeItemDeMenu(selecionado: Boolean, separado: Boolean): Drawable {
        val estados = StateListDrawable()
        estados.addState(intArrayOf(android.R.attr.state_pressed), retanguloDeMenu(CoresDoCromo.comAlfa(acento, 0x40), acento))
        val repouso = when {
            selecionado -> retanguloDeMenu(CoresDoCromo.comAlfa(acento, 0x2E), acento)
            separado -> retanguloDeMenu(0, if (claro) 0x2225303B else 0x22FFFFFF)
            else -> fundoDeCampo()
        }
        estados.addState(intArrayOf(), repouso)
        return estados
    }

    /** Fundo de campo (campo de nome, item "normal" de menu): raio 10. */
    fun fundoDeCampo(): GradientDrawable = retanguloDeMenu(
        if (claro) 0x0F25303B else 0x14FFFFFF,
        if (claro) 0x1A25303B else 0x1AFFFFFF,
    )

    private fun retanguloDeMenu(preenchimento: Int, contorno: Int): GradientDrawable {
        val fundo = GradientDrawable()
        fundo.cornerRadius = d.px(10).toFloat()
        fundo.setColor(preenchimento)
        fundo.setStroke(d.contorno(), contorno)
        return fundo
    }

    /** Cor do contorno de uma célula da grade de ícones da folha. */
    val corDoContornoDeCelula: Int get() = if (claro) 0x2225303B else 0x33FFFFFF

    // --- placas de ícone (A4) ---

    /** Placa de 70×70 de um item da fileira: clara, escura ou de cor própria. */
    fun placaDeIcone(placa: PlacaDoItem, raioDp: Int = MedidasDaPlacaDoDock.RAIO_DP): Drawable {
        if (placa is PlacaDoItem.Cor) {
            val fundo = GradientDrawable()
            fundo.cornerRadius = d.px(raioDp).toFloat()
            fundo.setColor(placa.argb)
            fundo.setStroke(MedidasDaPlacaDoDock.bordaEmPx(d.valor), MedidasDaPlacaDoDock.TRACO_DA_COR_SOLIDA.toInt())
            return fundo
        }
        val escura = placa is PlacaDoItem.Escura
        val corpoCima: Long
        val corpoBaixo: Long
        val aroCima: Long
        val aroBaixo: Long
        if (escura) {
            aroCima = MedidasDaPlacaDoDock.ESCURA_CONTORNO_CIMA
            aroBaixo = MedidasDaPlacaDoDock.ESCURA_CONTORNO_BAIXO
            if (claro) {
                corpoCima = MedidasDaPlacaDoDock.ESCURA_MIOLO_TEMA_CLARO_CIMA
                corpoBaixo = MedidasDaPlacaDoDock.ESCURA_MIOLO_TEMA_CLARO_BAIXO
            } else {
                corpoCima = MedidasDaPlacaDoDock.ESCURA_MIOLO_TEMA_ESCURO_CIMA
                corpoBaixo = MedidasDaPlacaDoDock.ESCURA_MIOLO_TEMA_ESCURO_BAIXO
            }
        } else if (claro) {
            aroCima = MedidasDaPlacaDoDock.CLARA_CONTORNO_TEMA_CLARO_CIMA
            aroBaixo = MedidasDaPlacaDoDock.CLARA_CONTORNO_TEMA_CLARO_BAIXO
            corpoCima = MedidasDaPlacaDoDock.CLARA_MIOLO_TEMA_CLARO_CIMA
            corpoBaixo = MedidasDaPlacaDoDock.CLARA_MIOLO_TEMA_CLARO_BAIXO
        } else {
            aroCima = MedidasDaPlacaDoDock.CLARA_CONTORNO_TEMA_ESCURO_CIMA
            aroBaixo = MedidasDaPlacaDoDock.CLARA_CONTORNO_TEMA_ESCURO_BAIXO
            corpoCima = MedidasDaPlacaDoDock.CLARA_MIOLO_TEMA_ESCURO_CIMA
            corpoBaixo = MedidasDaPlacaDoDock.CLARA_MIOLO_TEMA_ESCURO_BAIXO
        }
        return camadas(
            gradiente(aroCima.toInt(), aroBaixo.toInt(), d.px(raioDp)),
            gradiente(corpoCima.toInt(), corpoBaixo.toInt(), d.px(raioDp - 1)),
            MedidasDaPlacaDoDock.bordaEmPx(d.valor),
        )
    }


    // --- ajudantes ---

    private fun gradiente(cima: Int, baixo: Int, raioPx: Int): GradientDrawable {
        val g = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(cima, baixo))
        g.cornerRadius = raioPx.toFloat()
        return g
    }

    private fun camadas(aro: Drawable, corpo: Drawable, inset: Int): LayerDrawable {
        val camadas = LayerDrawable(arrayOf(aro, corpo))
        camadas.setLayerInset(1, inset, inset, inset, inset)
        return camadas
    }
}
