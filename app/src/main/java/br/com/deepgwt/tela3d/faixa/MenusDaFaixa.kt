package br.com.deepgwt.tela3d.faixa

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import br.com.deepgwt.tela3d.catalogo.ItemOculto
import br.com.deepgwt.tela3d.catalogo.PlacaDoItem
import kotlin.math.max
import kotlin.math.min

/** Os popups da faixa: só um fica aberto de cada vez. */
enum class TipoDePopup { EDICAO, HUB, OCULTOS, FOLHA }

/** Uma linha do menu do hub: ícone, texto e o que fazer ao tocar. */
class LinhaDoHub(val icone: android.graphics.drawable.Drawable?, val texto: String, val aoTocar: () -> Unit)

/** Uma linha do seletor de ocultos. */
class LinhaDeOculto(val item: ItemOculto, val placaEscura: Boolean, val icone: android.graphics.drawable.Drawable?)

/**
 * Menu de edição, menu do hub e seletor de apps ocultos (A9), e o registro dos popups abertos. Todos são
 * janelas de popup focáveis, de fundo transparente: tocar fora ou voltar fecha.
 */
class MenusDaFaixa(
    private val atividade: Activity,
    private val tema: TemaDoCromoPadrao,
    private val raiz: () -> View?,
) {
    private val d = Densidade(atividade)
    private val abertos = LinkedHashMap<TipoDePopup, PopupWindow>()

    /** Algum popup está aberto. */
    fun haPopupAberto(): Boolean = abertos.isNotEmpty()

    /** Fecha todos os popups abertos. */
    fun fecharTodos() {
        if (abertos.isEmpty()) return
        val copia = abertos.values.toList()
        abertos.clear()
        for (popup in copia) {
            try {
                popup.setOnDismissListener(null)
                popup.dismiss()
            } catch (e: Exception) {
                // a janela já tinha sumido
            }
        }
    }

    /** Fecha o popup de um tipo, se aberto. */
    fun fechar(tipo: TipoDePopup) {
        val popup = abertos.remove(tipo) ?: return
        try {
            popup.setOnDismissListener(null)
            popup.dismiss()
        } catch (e: Exception) {
            // já fechado
        }
    }

    /** Cria um popup focável de fundo transparente em volta de [conteudo]. */
    fun criarPopup(conteudo: View, largura: Int = ViewGroup.LayoutParams.WRAP_CONTENT, altura: Int = ViewGroup.LayoutParams.WRAP_CONTENT): PopupWindow {
        val popup = PopupWindow(conteudo, largura, altura, true)
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popup.isOutsideTouchable = true
        return popup
    }

    /** Registra e mostra [popup]; fecha antes qualquer outro. A falha ao mostrar só descarta o popup. */
    fun exibir(tipo: TipoDePopup, popup: PopupWindow, mostrar: (PopupWindow) -> Unit) {
        fecharTodos()
        popup.setOnDismissListener { if (abertos[tipo] === popup) abertos.remove(tipo) }
        abertos[tipo] = popup
        try {
            mostrar(popup)
        } catch (e: Exception) {
            abertos.remove(tipo)
        }
    }

    // --- menu de edição ---

    /** Menu acima do [ancora]: coluna de linhas de texto; tocar numa linha fecha o menu e executa a ação. */
    fun mostrarMenuDeEdicao(ancora: View, linhas: List<Pair<String, () -> Unit>>) {
        val coluna = colunaDeMenu(elevacaoDp = 12)
        coluna.setPadding(d.px(18), d.px(12), d.px(18), d.px(12))
        coluna.minimumWidth = d.px(220)
        for ((texto, acao) in linhas) {
            val linha = Estilos.texto(atividade, 16f, Color.WHITE).apply {
                this.text = texto
                gravity = Gravity.CENTER_VERTICAL
                minHeight = d.px(48)
                setPadding(d.px(10), d.px(12), d.px(10), d.px(12))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    fechar(TipoDePopup.EDICAO)
                    acao()
                }
            }
            coluna.addView(linha, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val popup = criarPopup(coluna)
        popup.elevation = d.px(12).toFloat()
        exibir(TipoDePopup.EDICAO, popup) { mostrarAcima(it, coluna, ancora, d.px(8)) }
    }

    // --- menu do hub ---

    /** Menu do hub GWM acima e centrado no [ancora], 10 de folga. Sem linhas, mostra `Nenhum app GWM`. */
    fun mostrarMenuDoHub(ancora: View, linhas: List<LinhaDoHub>) {
        val coluna = colunaDeMenu(elevacaoDp = 14)
        coluna.setPadding(d.px(18), d.px(12), d.px(18), d.px(12))
        coluna.minimumWidth = d.px(280)
        if (linhas.isEmpty()) {
            val vazio = Estilos.texto(atividade, 15f, 0x99FFFFFF.toInt()).apply {
                text = "Nenhum app GWM"
                setPadding(d.px(10), d.px(8), d.px(10), d.px(8))
            }
            coluna.addView(vazio)
        }
        for (linha in linhas) {
            val vista = LinearLayout(atividade).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = d.px(56)
                setPadding(d.px(14), d.px(8), d.px(14), d.px(8))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    fechar(TipoDePopup.HUB)
                    linha.aoTocar()
                }
            }
            val icone = ImageView(atividade)
            icone.setImageDrawable(linha.icone)
            vista.addView(
                icone,
                LinearLayout.LayoutParams(d.px(40), d.px(40)).apply { rightMargin = d.px(14) },
            )
            vista.addView(Estilos.texto(atividade, 17f, Color.WHITE).apply { text = linha.texto })
            coluna.addView(vista, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val popup = criarPopup(coluna)
        popup.elevation = d.px(14).toFloat()
        exibir(TipoDePopup.HUB, popup) { mostrarAcima(it, coluna, ancora, d.px(10)) }
    }

    // --- seletor de ocultos ---

    /** Lista de apps ocultos no centro da tela; tocar numa linha fecha e chama [aoEscolher]. */
    fun mostrarSeletorDeOcultos(linhas: List<LinhaDeOculto>, aoEscolher: (String) -> Unit) {
        val coluna = colunaDeMenu(elevacaoDp = 12)
        coluna.setPadding(d.px(14), d.px(10), d.px(14), d.px(10))
        coluna.addView(
            Estilos.texto(atividade, 14f, 0x99FFFFFF.toInt()).apply {
                text = "Hidden apps"
                setPadding(d.px(6), 0, d.px(6), d.px(6))
            },
        )
        for (linha in linhas) {
            val vista = LinearLayout(atividade).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = d.px(52)
                setPadding(d.px(12), d.px(8), d.px(12), d.px(8))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    fechar(TipoDePopup.OCULTOS)
                    aoEscolher(linha.item.pacote)
                }
            }
            val lado = d.px(PLACA_PEQUENA_DP)
            val placa = FrameLayout(atividade)
            val placaDoItem: PlacaDoItem = if (linha.placaEscura) PlacaDoItem.Escura else PlacaDoItem.Clara
            placa.background = tema.placaDeIcone(placaDoItem, raioDp = 9)
            Estilos.arredondar(placa, d.pxF(9f), recortar = true)
            val icone = ImageView(atividade)
            icone.setImageDrawable(linha.icone)
            icone.scaleType = ImageView.ScaleType.CENTER_CROP
            val interno = d.px(ICONE_PEQUENO_DP)
            placa.addView(icone, FrameLayout.LayoutParams(interno, interno, Gravity.CENTER))
            vista.addView(placa, LinearLayout.LayoutParams(lado, lado).apply { rightMargin = d.px(10) })
            vista.addView(
                Estilos.texto(atividade, 16f, Color.WHITE).apply {
                    text = linha.item.rotulo
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    maxWidth = d.px(240)
                },
            )
            coluna.addView(vista, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val rolagem = ScrollView(atividade).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(coluna)
        }
        val altura = alturaDaTela() - d.px(48)
        rolagem.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val popup = criarPopup(rolagem, altura = min(rolagem.measuredHeight, altura))
        popup.elevation = d.px(12).toFloat()
        exibir(TipoDePopup.OCULTOS, popup) { janela ->
            val pai = raiz() ?: return@exibir
            janela.showAtLocation(pai, Gravity.CENTER, 0, 0)
        }
    }

    // --- ajudantes ---

    private fun colunaDeMenu(elevacaoDp: Int): LinearLayout = LinearLayout(atividade).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(FUNDO_DO_MENU)
        elevation = d.px(elevacaoDp).toFloat()
    }

    private fun alturaDaTela(): Int = atividade.resources.displayMetrics.heightPixels

    /** Mostra [popup] com a base [folga] acima do topo de [ancora] e centrado nele. */
    private fun mostrarAcima(popup: PopupWindow, conteudo: View, ancora: View, folga: Int) {
        conteudo.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val largura = conteudo.measuredWidth
        val altura = conteudo.measuredHeight
        val deslocX = (ancora.width - largura) / 2
        val deslocY = -(ancora.height + altura + folga)
        popup.showAsDropDown(ancora, deslocX, max(deslocY, -alturaDaTela()))
    }

    companion object {
        const val FUNDO_DO_MENU: Int = 0xF2141820.toInt()
        const val PLACA_PEQUENA_DP = 46
        const val ICONE_PEQUENO_DP = 36
    }
}
