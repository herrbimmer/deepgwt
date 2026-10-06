package br.com.deepgwt.tela3d.faixa

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * O primeiro cartão da linha de cartões, "Espaço de trabalho" (F23 a F26): vidro fosco de 212×124 com duas
 * faces que se alternam, a das ações (Apps e Layout) e a do layout (grade 2×2 do estúdio de páginas).
 */
class CartaoEspacoDeTrabalho(
    contexto: Context,
    private val tema: TemaDoCromoPadrao,
    private val acoes: Acoes,
) : FrameLayout(contexto) {

    /** O que os toques do cartão pedem à faixa. */
    interface Acoes {
        fun aoTocarApps()
        fun aoTocarDesktops()
        fun aoTocarCartoesEWidgets()
        fun aoTocarAparencia()
        fun aoTocarRetornar()
    }

    private val d = Densidade(contexto)

    private val faceDeAcoes = FrameLayout(contexto)
    private val faceDeLayout = LinearLayout(contexto)
    private val titulo: TextView
    private val acaoApps: LinearLayout
    private val acaoLayout: LinearLayout
    private val glifoApps = ImageView(contexto)
    private val glifoLayout = ImageView(contexto)
    private val rotuloApps: TextView
    private val rotuloLayout: TextView

    private val celulas = LinkedHashMap<String, TextView>()
    private val celulaRetorno = ImageView(contexto)

    private var emLayout = false
    private var telaDoEstudio: String = TelasDoEstudio.NENHUMA

    init {
        elevation = d.px(3).toFloat()
        Estilos.arredondar(this, d.pxF(26f))
        contentDescription = "Workspace: app launcher and layout manager"

        titulo = Estilos.texto(contexto, 12.5f, tema.corDeRotuloMuted, medio = true).apply {
            text = TITULO
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        faceDeAcoes.addView(
            titulo,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                leftMargin = d.px(16)
                topMargin = d.px(12)
            },
        )

        val linhaDeAcoes = LinearLayout(contexto).apply { orientation = LinearLayout.HORIZONTAL }
        rotuloApps = rotuloDeAcao()
        rotuloLayout = rotuloDeAcao()
        acaoApps = montarAcao(glifoApps, rotuloApps, "Apps", "Show app launcher") { acoes.aoTocarApps() }
        acaoLayout = montarAcao(glifoLayout, rotuloLayout, "Layout", "Show layout shortcuts") { mostrarLayout() }
        linhaDeAcoes.addView(acaoApps, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        linhaDeAcoes.addView(acaoLayout, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        faceDeAcoes.addView(linhaDeAcoes, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(faceDeAcoes, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        faceDeLayout.orientation = LinearLayout.VERTICAL
        faceDeLayout.setPadding(d.px(6), d.px(6), d.px(6), d.px(6))
        val linha1 = LinearLayout(contexto)
        val linha2 = LinearLayout(contexto)
        linha1.addView(celulaDeTexto(TelasDoEstudio.PAGINAS, "Desktops") { acoes.aoTocarDesktops() })
        linha1.addView(celulaDeTexto(TelasDoEstudio.CARTOES_E_WIDGETS, "Cards & widgets") { acoes.aoTocarCartoesEWidgets() })
        linha2.addView(celulaDeTexto(TelasDoEstudio.APARENCIA, "Appearance") { acoes.aoTocarAparencia() })
        celulaRetorno.apply {
            scaleType = ImageView.ScaleType.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = "Return to workspace"
            setOnClickListener { acoes.aoTocarRetornar() }
        }
        linha2.addView(celulaRetorno, parametrosDeCelula())
        faceDeLayout.addView(linha1, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        faceDeLayout.addView(linha2, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        faceDeLayout.visibility = View.GONE
        addView(faceDeLayout, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        aplicarTema()
    }

    /** O cartão está na face do layout. */
    val naFaceDeLayout: Boolean get() = emLayout

    /** Repinta fundo, glifos, rótulos e células com o tema atual. */
    fun aplicarTema() {
        background = tema.materialVidroFosco(false)
        titulo.setTextColor(tema.corDeRotuloMuted)
        val ladoDoGlifo = d.px(GLIFO_DP)
        glifoApps.setImageDrawable(BitmapDrawable(resources, Glifos.apps(ladoDoGlifo, tema.corDeRotulo)))
        glifoLayout.setImageDrawable(BitmapDrawable(resources, Glifos.layout(ladoDoGlifo, tema.corDeRotulo)))
        rotuloApps.setTextColor(tema.corDeRotulo)
        rotuloLayout.setTextColor(tema.corDeRotulo)
        val ondulacao = ColorStateList.valueOf(CoresDoCromo.comAlfa(tema.corDeRotulo, 0x33))
        acaoApps.background = RippleDrawable(ondulacao, null, ColorDrawable(0xFFFFFFFF.toInt()))
        acaoLayout.background = RippleDrawable(ondulacao, null, ColorDrawable(0xFFFFFFFF.toInt()))
        celulaRetorno.setImageDrawable(
            BitmapDrawable(resources, Glifos.setaEsquerda(d.px(22), tema.acento)),
        )
        aplicarCelulas()
    }

    /** Estado de partida de um cartão novo, sem animação: tela do estúdio e, se há tela aberta, a face do layout. */
    fun iniciar(tela: String) {
        telaDoEstudio = tela
        aplicarCelulas()
        if (tela.isNotEmpty()) {
            emLayout = true
            faceDeAcoes.visibility = View.GONE
            faceDeLayout.visibility = View.VISIBLE
        }
    }

    /** A tela do estúdio mudou; se passou a ser uma tela conhecida, o cartão vai para a face do layout. */
    fun aoMudarTelaDoEstudio(tela: String) {
        val anterior = telaDoEstudio
        telaDoEstudio = tela
        aplicarCelulas()
        if (tela.isNotEmpty() && tela != anterior) mostrarLayout()
    }

    /** Alterna para a face do layout (com animação). */
    fun mostrarLayout() = trocarFace(true)

    /** Volta para a face das ações (com animação). */
    fun mostrarAcoes() = trocarFace(false)

    private fun trocarFace(layout: Boolean) {
        if (layout == emLayout) return
        emLayout = layout
        val entra: View = if (layout) faceDeLayout else faceDeAcoes
        val sai: View = if (layout) faceDeAcoes else faceDeLayout
        entra.animate().cancel()
        sai.animate().cancel()
        entra.alpha = 0f
        entra.visibility = View.VISIBLE
        entra.animate().alpha(1f).setDuration(ENTRADA_MS).start()
        sai.animate().alpha(0f).setDuration(SAIDA_MS).withEndAction {
            sai.visibility = View.GONE
            sai.alpha = 1f
        }.start()
    }

    // --- construção ---

    private fun rotuloDeAcao(): TextView = Estilos.texto(context, 12.5f, tema.corDeRotulo, medio = true).apply {
        gravity = Gravity.CENTER
        maxLines = 1
    }

    private fun montarAcao(
        glifo: ImageView,
        rotulo: TextView,
        texto: String,
        descricao: String,
        aoTocar: () -> Unit,
    ): LinearLayout {
        val coluna = LinearLayout(context)
        coluna.orientation = LinearLayout.VERTICAL
        coluna.gravity = Gravity.CENTER_HORIZONTAL
        coluna.setPadding(0, d.px(30), 0, d.px(6))
        coluna.isClickable = true
        coluna.isFocusable = true
        coluna.contentDescription = descricao
        coluna.setOnClickListener { aoTocar() }
        rotulo.text = texto
        val lado = d.px(GLIFO_DP)
        coluna.addView(glifo, LinearLayout.LayoutParams(lado, lado))
        coluna.addView(
            rotulo,
            LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = d.px(5) },
        )
        return coluna
    }

    private fun parametrosDeCelula(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
            setMargins(d.px(2), d.px(2), d.px(2), d.px(2))
        }

    private fun celulaDeTexto(tela: String, texto: String, aoTocar: () -> Unit): TextView {
        val vista = Estilos.texto(context, 13f, tema.corDeRotulo, medio = true).apply {
            this.text = texto
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            isClickable = true
            isFocusable = true
            contentDescription = "Open $texto"
            setOnClickListener { aoTocar() }
            layoutParams = parametrosDeCelula()
        }
        celulas[tela] = vista
        return vista
    }

    /** Fundo, cor de texto e ondulação das quatro células da grade. */
    private fun aplicarCelulas() {
        for ((tela, vista) in celulas) {
            val atual = tela == telaDoEstudio
            vista.background = fundoDeCelula(
                if (atual) CoresDoCromo.comAlfa(tema.acento, 0x2E) else corDeCelulaComum(),
            )
            vista.setTextColor(if (atual) tema.acento else tema.corDeRotulo)
        }
        celulaRetorno.background = fundoDeCelula(CoresDoCromo.comAlfa(tema.acento, 0x33))
    }

    private fun corDeCelulaComum(): Int = if (tema.claro) 0x0F000000 else 0x12FFFFFF

    private fun fundoDeCelula(preenchimento: Int): RippleDrawable {
        val raio = d.pxF(14f)
        val conteudo = GradientDrawable().apply {
            cornerRadius = raio
            setColor(preenchimento)
        }
        val mascara = GradientDrawable().apply {
            cornerRadius = raio
            setColor(0xFFFFFFFF.toInt())
        }
        return RippleDrawable(ColorStateList.valueOf(CoresDoCromo.comAlfa(tema.corDeRotulo, 0x33)), conteudo, mascara)
    }

    companion object {
        const val TITULO = "Espaço de trabalho"
        const val LARGURA_DP = 212
        const val ALTURA_DP = 124
        const val MARGEM_DIREITA_DP = 12
        const val GLIFO_DP = 32
        const val ENTRADA_MS = 160L
        const val SAIDA_MS = 120L
    }
}
