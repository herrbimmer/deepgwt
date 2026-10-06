package br.com.deepgwt.tela3d.cartoes

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import br.com.deepgwt.tela3d.faixa.Densidade
import br.com.deepgwt.tela3d.ponte.CartaoInferior

/** O que um cartão pede à linha (menus e comandos). */
internal interface ServicosDoCartao {
    fun enviar(comando: String)
    fun abrirMenuRapido(cartao: CartaoDaLinha, linhas: List<br.com.deepgwt.tela3d.ponte.LinhaDeMenu>)
}

/** Fontes compartilhadas. */
internal object Fontes {
    val MEDIO: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val NORMAL: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)

    fun texto(contexto: Context, sp: Float, cor: Int, medio: Boolean): TextView = TextView(contexto).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(cor)
        typeface = if (medio) MEDIO else NORMAL
        includeFontPadding = false
    }
}

/**
 * O cartão visual padrão (B1–B6): cabeçalho (selo Demo, título, indicador "abre" ou ícone-fonte) e corpo
 * (texto à esquerda, gráfico à direita; ou o gráfico inteiro nos largos). Lê SEMPRE o descritor atual ao
 * ser tocado, então atualizar valores nunca deixa menu ou ação para trás.
 */
internal class CartaoVisual(
    contexto: Context,
    anfitriao: AnfitriaoDosCartoes,
    d: Densidade,
    private val servicos: ServicosDoCartao,
    inicial: CartaoInferior,
) : CartaoDaLinha(contexto, anfitriao, d) {
    private var atual: CartaoInferior = inicial
    override val descritor: CartaoInferior get() = atual
    override val larguraEmDp: Int = MedidasDaLinha.larguraDp(inicial.id)

    private val seloDemo = Fontes.texto(contexto, 10.5f, 0, true)
    private val titulo = Fontes.texto(contexto, 12.5f, 0, true)
    private val indicador = Fontes.texto(contexto, 20f, 0, false)
    private val iconeDeOrigem = ImageView(contexto)
    private val valor = Fontes.texto(contexto, VALOR_GRANDE_SP, 0, true)
    private val detalhe = Fontes.texto(contexto, 11.5f, 0, false)
    private val grafico = GraficoDoCartao(contexto, { tema }, d.valor)

    private var lavagemAtual = 0
    private var pacoteDoIcone: String? = null
    private val ehLargo = MedidasDaLinha.ehLargo(inicial.id)
    private val ehPotencia = inicial.id == "power"
    private val ehNavegacao = inicial.id == "navigation"

    init {
        val vertical = LinearLayout(contexto).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            setPadding(d.px(16), d.px(12), d.px(16), d.px(12))
        }
        addView(vertical, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // cabeçalho
        val cabecalho = LinearLayout(contexto).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
        }
        seloDemo.text = "Demo"
        seloDemo.visibility = View.GONE
        cabecalho.addView(seloDemo, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginEnd = d.px(7)
        })
        titulo.isSingleLine = true
        titulo.ellipsize = TextUtils.TruncateAt.END
        titulo.setPadding(0, d.px(2), 0, 0) // medido nas capturas: o título fica 1 px abaixo do centro
        cabecalho.addView(titulo, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (ehNavegacao) {
            iconeDeOrigem.scaleType = ImageView.ScaleType.FIT_CENTER
            iconeDeOrigem.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            iconeDeOrigem.visibility = View.GONE
            cabecalho.addView(iconeDeOrigem, LinearLayout.LayoutParams(d.px(30), d.px(30)).apply { gravity = Gravity.BOTTOM })
        } else {
            indicador.text = "›"
            indicador.gravity = Gravity.CENTER
            indicador.alpha = 0.7f
            indicador.translationY = d.pxF(2.5f) // medido nas capturas
            indicador.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            cabecalho.addView(indicador, LinearLayout.LayoutParams(d.px(16), LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        vertical.addView(cabecalho, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, d.px(20)))

        // corpo
        valor.isSingleLine = true
        valor.ellipsize = TextUtils.TruncateAt.END
        detalhe.setLineSpacing(0f, 1.08f)
        detalhe.maxLines = TextosDosCartoes.maximoDeLinhasDoDetalhe(inicial)
        detalhe.ellipsize = TextUtils.TruncateAt.END

        val corpoParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        when {
            ehLargo -> {
                val corpo = FrameLayout(contexto).apply { clipChildren = false }
                corpoParams.topMargin = d.px(2)
                corpo.addView(grafico, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                vertical.addView(corpo, corpoParams)
            }
            ehPotencia -> {
                val corpo = LinearLayout(contexto).apply {
                    orientation = LinearLayout.HORIZONTAL
                    clipChildren = false
                }
                corpoParams.topMargin = d.px(2)
                corpo.addView(grafico, LinearLayout.LayoutParams(d.px(150), LinearLayout.LayoutParams.MATCH_PARENT).apply { marginEnd = d.px(12) })
                val coluna = colunaDeTexto()
                coluna.gravity = Gravity.CENTER_VERTICAL
                corpo.addView(coluna, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
                vertical.addView(corpo, corpoParams)
            }
            else -> {
                val corpo = FrameLayout(contexto).apply { clipChildren = false }
                val coluna = colunaDeTexto()
                coluna.gravity = Gravity.BOTTOM
                corpo.addView(coluna, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT).apply {
                    marginEnd = d.px(80)
                })
                corpo.addView(grafico, FrameLayout.LayoutParams(d.px(70), d.px(70), Gravity.END or Gravity.CENTER_VERTICAL))
                vertical.addView(corpo, corpoParams)
            }
        }

        // toques
        setOnClickListener { aoTocar() }
        setOnLongClickListener { aoTocarLongo() }
        grafico.setOnClickListener { aoTocarNoGrafico() }
        grafico.isClickable = false

        reaplicarCores()
        aplicar(inicial, forcar = true)
    }

    private fun colunaDeTexto(): LinearLayout {
        val coluna = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
        }
        coluna.addView(valor, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        coluna.addView(detalhe, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = d.px(FOLGA_ENTRE_VALOR_E_DETALHE_DP)
        })
        return coluna
    }

    // --- valores (A4) ---

    /** Atualiza tudo o que o descritor controla, sem reconstruir. */
    fun aplicar(c: CartaoInferior, forcar: Boolean = false) {
        atual = c
        if (!ehLargo) {
            val grande = TextosDosCartoes.valorGrande(c)
            if (forcar || valor.text.toString() != grande) valor.text = grande
            val texto = TextosDosCartoes.detalhe(c)
            if (forcar || detalhe.text.toString() != texto) detalhe.text = texto
            detalhe.visibility = if (texto.isEmpty()) View.GONE else View.VISIBLE
        }
        if (forcar || titulo.text.toString() != c.titulo) titulo.text = c.titulo
        seloDemo.visibility = if (c.demo) View.VISIBLE else View.GONE
        grafico.definir(c)
        val temAcaoNoGrafico = c.acaoDoIcone.isNotEmpty() && !ehLargo
        grafico.isClickable = temAcaoNoGrafico && !emEdicao
        grafico.isFocusable = temAcaoNoGrafico && !emEdicao
        if (temAcaoNoGrafico) {
            grafico.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            grafico.contentDescription = TextosDosCartoes.descricaoDoGrafico(c)
        } else {
            grafico.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            grafico.contentDescription = null
        }
        if (ehNavegacao) atualizarIconeDeOrigem(forcar)
        val lavagem = LavagemDosCartoes.cor(c.estado)
        if (forcar || lavagem != lavagemAtual) {
            lavagemAtual = lavagem
            aplicarFundo(lavagem)
        }
        contentDescription = TextosDosCartoes.descricao(c)
        invalidate()
    }

    private fun atualizarIconeDeOrigem(forcar: Boolean) {
        val pacote = atual.pacoteDoApp.ifEmpty { null }
        if (!forcar && pacote == pacoteDoIcone) return
        redesenharIconeDeOrigem()
    }

    /** Pede de novo o ícone-fonte ao anfitrião (o do usuário pode ter mudado). */
    fun redesenharIconeDeOrigem() {
        val pacote = atual.pacoteDoApp.ifEmpty { null }
        pacoteDoIcone = pacote
        val icone: Drawable? = if (pacote == null) null else anfitriao.iconeDeOrigem(pacote, null)
        iconeDeOrigem.setImageDrawable(icone)
        iconeDeOrigem.visibility = if (icone == null) View.GONE else View.VISIBLE
    }

    fun usaOPacote(pacote: String): Boolean = ehNavegacao && atual.pacoteDoApp == pacote

    // --- tema (A5) ---

    override fun reaplicarTema() {
        aplicarFundo(lavagemAtual)
        reaplicarCores()
        grafico.invalidate()
    }

    private fun reaplicarCores() {
        seloDemo.setTextColor(tema.acento)
        titulo.setTextColor(tema.corDeRotuloMuted)
        indicador.setTextColor(tema.corDeRotuloMuted)
        valor.setTextColor(tema.corDeRotulo)
        detalhe.setTextColor(tema.corDeRotuloMuted)
    }

    // --- toques ---

    private fun aoTocar() {
        when (val a = PoliticaDeToque.aoTocar(atual)) {
            is AcaoDoToque.AbrirMenu -> servicos.abrirMenuRapido(this, a.linhas)
            is AcaoDoToque.Comando -> servicos.enviar(a.comando)
            else -> Unit
        }
    }

    private fun aoTocarNoGrafico() {
        val a = PoliticaDeToque.aoTocarNoGrafico(atual)
        if (a is AcaoDoToque.Comando) servicos.enviar(a.comando)
    }

    private fun aoTocarLongo(): Boolean {
        servicos.enviar(PoliticaDeToque.aoTocarLongo(atual))
        return true
    }

    companion object {
        /** Tamanho fixo do valor grande (o ajuste automático da página original nunca passou de 16 sp). */
        const val VALOR_GRANDE_SP = 16f

        /**
         * A spec pede margem superior de 4 dp no detalhe; nas capturas de referência a distância entre o
         * valor grande e o detalhe é 3 px menor do que essa margem produz com as fontes do emulador.
         */
        const val FOLGA_ENTRE_VALOR_E_DETALHE_DP = 1
    }
}
