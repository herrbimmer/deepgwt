package br.com.deepgwt.tela3d.faixa

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * O botão flutuante ☰ e o menu dele (F31 a F34), que só existem no modo de dois apps lado a lado. Fica como
 * janela de sobreposição do sistema; sem a permissão, dentro da janela da Activity. O botão nunca se mexe
 * ao abrir o menu: a janela cresce para os dois lados e sobe (D9). Limite de arrasto: a página (D11).
 */
class BotaoFlutuanteDeApps(
    private val atividade: Activity,
    private val prefs: SharedPreferences,
    private val janelas: ServicosDeJanelas,
    private val pagina: PaginaDaCentral,
    private val raiz: () -> FrameLayout?,
) {
    private val d = Densidade(atividade)
    private val gerenteDeJanelas = atividade.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val botao = TextView(atividade)
    private val menu = LinearLayout(atividade)
    private val conteudo = LinearLayout(atividade)
    private val scrim = View(atividade)
    private val botoesDeProporcao = LinkedHashMap<Proporcao, View>()

    private var anexado = false
    private var comoJanelaDoSistema = false
    private var menuAberto = false
    private var scrimNaRaiz = false

    /** Canto superior esquerdo do botão, em px de tela; o menu aberto não o muda. */
    private var posicaoDoBotao = PontoEmPx(0, 0)

    private var paramsDaJanela: WindowManager.LayoutParams? = null
    private var paramsDoScrim: WindowManager.LayoutParams? = null

    // estado do toque
    private var toqueX = 0f
    private var toqueY = 0f
    private var posicaoNoToque = PontoEmPx(0, 0)
    private var arrastando = false

    init {
        construir()
    }

    /** O botão está na tela. */
    val visivel: Boolean get() = anexado

    // --- ciclo de vida ---

    /** Mostra o botão (F31). Idempotente. */
    fun mostrar() {
        if (anexado) return
        posicaoDoBotao = limitar(posicaoInicial())
        val sistema = Settings.canDrawOverlays(atividade) && anexarComoJanelaDoSistema()
        if (!sistema && !anexarNaRaiz()) {
            Log.w(ETIQUETA, "Sem raiz para anexar o botão flutuante")
            return
        }
        anexado = true
        comoJanelaDoSistema = sistema
        atualizarDestaque(janelas.proporcao())
        janelas.recalcularAncoras()
        janelas.reprenderAppsNasAreas()
        pagina.avisarLayoutDaCasca()
    }

    /** Tira o botão, o menu e o scrim da tela (ao sair do lado a lado e ao destruir). */
    fun remover() {
        if (!anexado) {
            menuAberto = false
            return
        }
        fecharMenu(reposicionar = false)
        anexado = false
        try {
            if (comoJanelaDoSistema) {
                gerenteDeJanelas.removeViewImmediate(conteudo)
                if (scrim.isAttachedToWindow) gerenteDeJanelas.removeViewImmediate(scrim)
            } else {
                (conteudo.parent as? ViewGroup)?.removeView(conteudo)
            }
        } catch (e: Exception) {
            Log.w(ETIQUETA, "Falha ao remover o botão flutuante", e)
        }
        janelas.cromoNoTopo(false)
    }

    /** O destaque do menu segue a proporção atual. */
    fun atualizarDestaque(proporcao: Proporcao) {
        for ((p, vista) in botoesDeProporcao) vista.isSelected = p == proporcao
    }

    /** Fecha o menu sem mexer na posição (a Activity pausou, por exemplo). */
    fun fecharMenuSeAberto() {
        if (menuAberto) fecharMenu()
    }

    // --- anexar ---

    private fun anexarComoJanelaDoSistema(): Boolean {
        return try {
            val janela = WindowManager.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                FLAGS_DA_JANELA,
                PixelFormat.TRANSLUCENT,
            )
            janela.gravity = Gravity.TOP or Gravity.START
            janela.x = posicaoDoBotao.x
            janela.y = posicaoDoBotao.y
            // O scrim fica numa janela própria, ABAIXO da do botão, e só recebe toque com o menu aberto.
            val tela = WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                FLAGS_DA_JANELA or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT,
            )
            tela.gravity = Gravity.TOP or Gravity.START
            conteudo.elevation = 0f
            gerenteDeJanelas.addView(scrim, tela)
            try {
                gerenteDeJanelas.addView(conteudo, janela)
            } catch (e: Exception) {
                gerenteDeJanelas.removeViewImmediate(scrim)
                throw e
            }
            paramsDaJanela = janela
            paramsDoScrim = tela
            true
        } catch (e: Exception) {
            Log.w(ETIQUETA, "Não foi possível criar a janela do botão flutuante", e)
            false
        }
    }

    private fun anexarNaRaiz(): Boolean {
        val pai = raiz() ?: return false
        conteudo.elevation = d.px(48).toFloat()
        pai.addView(conteudo, parametrosNaRaiz(posicaoDoBotao))
        return true
    }

    private fun parametrosNaRaiz(posicao: PontoEmPx): FrameLayout.LayoutParams {
        val origem = IntArray(2)
        raiz()?.getLocationOnScreen(origem)
        return FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = posicao.x - origem[0]
            topMargin = posicao.y - origem[1]
        }
    }

    /** Põe a janela (botão e menu) com o canto superior esquerdo em [posicao], em px de tela. */
    private fun moverJanela(posicao: PontoEmPx) {
        if (!anexado && paramsDaJanela == null && conteudo.parent == null) return
        try {
            if (comoJanelaDoSistema) {
                val p = paramsDaJanela ?: return
                p.x = posicao.x
                p.y = posicao.y
                gerenteDeJanelas.updateViewLayout(conteudo, p)
            } else {
                conteudo.layoutParams = parametrosNaRaiz(posicao)
            }
        } catch (e: Exception) {
            Log.w(ETIQUETA, "Falha ao mover o botão flutuante", e)
        }
    }

    // --- posição ---

    private fun posicaoInicial(): PontoEmPx {
        if (prefs.contains(PREF_X) && prefs.contains(PREF_Y)) {
            return PontoEmPx(prefs.getInt(PREF_X, 0), prefs.getInt(PREF_Y, 0))
        }
        val m = pagina.medidasAtuais()
        val tela = atividade.resources.displayMetrics
        val largura = if (m.larguraRealPx > 0) m.larguraRealPx else tela.widthPixels
        val altura = if (m.alturaRealPx > 0) m.alturaRealPx else tela.heightPixels
        return PosicaoDoBotao.padrao(largura, altura, d.px(PosicaoDoBotao.LADO_DP), d.px(PosicaoDoBotao.FOLGA_INFERIOR_DP))
    }

    private fun areaPermitida(): AreaDoBotao {
        val m = pagina.medidasAtuais()
        val tela = atividade.resources.displayMetrics
        val largura = if (m.larguraRealPx > 0) m.larguraRealPx else tela.widthPixels
        val altura = if (m.alturaRealPx > 0) m.alturaRealPx else tela.heightPixels
        // O botão fica na página, acima da barra inferior.
        val base = if (m.paginaBase > 0) minOf(m.paginaBase, altura) else altura
        return AreaDoBotao(0, 0, largura, base)
    }

    private fun limitar(p: PontoEmPx): PontoEmPx {
        val lado = d.px(PosicaoDoBotao.LADO_DP)
        return PosicaoDoBotao.limitar(p.x, p.y, lado, lado, areaPermitida())
    }

    // --- toque ---

    @SuppressLint("ClickableViewAccessibility")
    private fun aoTocarNoBotao(evento: MotionEvent): Boolean {
        when (evento.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                toqueX = evento.rawX
                toqueY = evento.rawY
                posicaoNoToque = posicaoDoBotao
                arrastando = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (evento.rawX - toqueX).roundToInt()
                val dy = (evento.rawY - toqueY).roundToInt()
                if (!arrastando && PosicaoDoBotao.comecouArrasto(dx, dy)) {
                    arrastando = true
                    if (menuAberto) fecharMenu()
                }
                if (arrastando) {
                    posicaoDoBotao = limitar(PontoEmPx(posicaoNoToque.x + dx, posicaoNoToque.y + dy))
                    moverJanela(posicaoDoBotao)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (arrastando) {
                    arrastando = false
                    prefs.edit().putInt(PREF_X, posicaoDoBotao.x).putInt(PREF_Y, posicaoDoBotao.y).apply()
                } else if (evento.actionMasked == MotionEvent.ACTION_UP) {
                    alternarMenu()
                }
            }
        }
        return true
    }

    // --- menu ---

    private fun alternarMenu() {
        if (menuAberto) fecharMenu() else abrirMenu()
    }

    private fun abrirMenu() {
        if (menuAberto) return
        menuAberto = true
        menu.visibility = View.VISIBLE
        (botao.layoutParams as LinearLayout.LayoutParams).topMargin = d.px(PosicaoDoBotao.FOLGA_DO_MENU_DP)
        botao.requestLayout()
        botao.setTextColor(COR_DO_ICONE_ABERTO)
        mostrarScrim()
        atualizarDestaque(janelas.proporcao())
        if (!comoJanelaDoSistema) janelas.cromoNoTopo(true)
        moverJanela(posicaoDaJanelaComMenu())
    }

    private fun fecharMenu(reposicionar: Boolean = true) {
        if (!menuAberto) return
        menuAberto = false
        menu.visibility = View.GONE
        (botao.layoutParams as LinearLayout.LayoutParams).topMargin = 0
        botao.requestLayout()
        botao.setTextColor(COR_DO_ICONE)
        esconderScrim()
        if (!comoJanelaDoSistema && anexado) janelas.cromoNoTopo(false)
        if (reposicionar) moverJanela(posicaoDoBotao)
    }

    /** O menu fica acima do botão e centrado nele; a janela vai à esquerda por metade da diferença de largura. */
    private fun posicaoDaJanelaComMenu(): PontoEmPx {
        val tela = areaPermitida()
        conteudo.measure(
            View.MeasureSpec.makeMeasureSpec(tela.dir, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(max(tela.base, 1), View.MeasureSpec.AT_MOST),
        )
        val lado = d.px(PosicaoDoBotao.LADO_DP)
        val larguraDoMenu = max(conteudo.measuredWidth, d.px(PosicaoDoBotao.LARGURA_DO_MENU_DP))
        val alturaDoMenu = menu.measuredHeight
        val bruta = PosicaoDoBotao.janelaComMenu(
            posicaoDoBotao, lado, larguraDoMenu, alturaDoMenu, d.px(PosicaoDoBotao.FOLGA_DO_MENU_DP),
        )
        // Perto das bordas a janela inteira tem que caber na tela.
        val x = bruta.x.coerceIn(0, max(0, tela.dir - larguraDoMenu))
        return PontoEmPx(x, max(0, bruta.y))
    }

    private fun mostrarScrim() {
        if (comoJanelaDoSistema) {
            val p = paramsDoScrim ?: return
            p.flags = p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            try {
                gerenteDeJanelas.updateViewLayout(scrim, p)
            } catch (e: Exception) {
                Log.w(ETIQUETA, "Falha ao ligar o scrim", e)
            }
        } else {
            val pai = raiz() ?: return
            if (scrim.parent == null) {
                // Por cima da página e logo abaixo do botão: tocar fora fecha o menu (D10).
                val indice = pai.indexOfChild(conteudo).let { if (it < 0) pai.childCount else it }
                pai.addView(scrim, indice, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                scrimNaRaiz = true
            }
        }
    }

    private fun esconderScrim() {
        if (comoJanelaDoSistema) {
            val p = paramsDoScrim ?: return
            p.flags = p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            try {
                if (scrim.isAttachedToWindow) gerenteDeJanelas.updateViewLayout(scrim, p)
            } catch (e: Exception) {
                Log.w(ETIQUETA, "Falha ao desligar o scrim", e)
            }
        } else if (scrimNaRaiz) {
            (scrim.parent as? ViewGroup)?.removeView(scrim)
            scrimNaRaiz = false
        }
    }

    // --- construção das views (A7) ---

    private fun construir() {
        scrim.setBackgroundColor(Color.TRANSPARENT)
        scrim.isClickable = true
        scrim.isFocusable = true
        scrim.setOnClickListener { fecharMenu() }

        conteudo.orientation = LinearLayout.VERTICAL
        conteudo.gravity = Gravity.CENTER_HORIZONTAL

        montarMenu()
        conteudo.addView(menu, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val lado = d.px(PosicaoDoBotao.LADO_DP)
        botao.text = "☰"
        botao.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 26f)
        botao.setTextColor(COR_DO_ICONE)
        botao.gravity = Gravity.CENTER
        botao.includeFontPadding = false
        botao.elevation = d.px(8).toFloat()
        botao.isClickable = true
        botao.isFocusable = true
        botao.contentDescription = "Side by Side apps"
        val fundo = GradientDrawable()
        fundo.shape = GradientDrawable.OVAL
        fundo.setColor(0xEB0E121A.toInt())
        fundo.setStroke(max(1, (1.5f * d.valor).roundToInt()), 0x33FFFFFF)
        botao.background = fundo
        botao.setOnTouchListener { _, evento -> aoTocarNoBotao(evento) }
        conteudo.addView(botao, LinearLayout.LayoutParams(lado, lado))
    }

    private fun montarMenu() {
        menu.orientation = LinearLayout.VERTICAL
        menu.gravity = Gravity.CENTER_HORIZONTAL
        menu.minimumWidth = d.px(PosicaoDoBotao.LARGURA_DO_MENU_DP)
        menu.setPadding(d.px(14), d.px(14), d.px(14), d.px(14))
        menu.elevation = d.px(10).toFloat()
        val fundo = GradientDrawable()
        fundo.cornerRadius = d.px(14).toFloat()
        fundo.setColor(0xF00E121A.toInt())
        fundo.setStroke(d.contorno(), 0x24FFFFFF)
        menu.background = fundo
        menu.visibility = View.GONE

        menu.addView(rotuloDeSecao("SPLIT"))
        val proporcoes = LinearLayout(atividade)
        proporcoes.orientation = LinearLayout.HORIZONTAL
        proporcoes.gravity = Gravity.CENTER
        for ((proporcao, pesos) in listOf(
            Proporcao.UM_UM to (1f to 1f),
            Proporcao.DOIS_UM to (2f to 1f),
            Proporcao.UM_DOIS to (1f to 2f),
        )) {
            val celula = FrameLayout(atividade)
            celula.background = fundoDeBotao()
            celula.isClickable = true
            celula.isFocusable = true
            celula.contentDescription = "Split ${proporcao.valor}"
            celula.setOnClickListener {
                janelas.definirProporcao(proporcao)
                atualizarDestaque(proporcao)
            }
            celula.addView(
                GlifoDeProporcao(atividade, pesos.first, pesos.second, d),
                FrameLayout.LayoutParams(d.px(44), d.px(26), Gravity.CENTER),
            )
            botoesDeProporcao[proporcao] = celula
            proporcoes.addView(
                celula,
                LinearLayout.LayoutParams(d.px(76), d.px(58)).apply { setMargins(d.px(4), 0, d.px(4), 0) },
            )
        }
        menu.addView(proporcoes)

        menu.addView(rotuloDeSecao("LAYOUT"))
        val sair = textoDeBotao("Exit Side by Side", 13f).apply {
            setPadding(d.px(12), d.px(12), d.px(12), d.px(12))
            setOnClickListener {
                fecharMenu()
                if (janelas.modoDaCasca() != ModoDaCasca.APP_E_CARRO) janelas.definirModoDaCasca(ModoDaCasca.APP_E_CARRO)
            }
        }
        menu.addView(
            sair,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = d.px(4); bottomMargin = d.px(4)
            },
        )

        val acoes = LinearLayout(atividade)
        acoes.orientation = LinearLayout.HORIZONTAL
        acoes.gravity = Gravity.CENTER
        acoes.setPadding(0, d.px(8), 0, 0)
        val trocar = textoDeBotao("SWAP", 14f).apply {
            setPadding(d.px(18), d.px(14), d.px(18), d.px(14))
            setOnClickListener {
                fecharMenu()
                janelas.trocarApps()
            }
        }
        val salvar = textoDeBotao("SAVE", 14f).apply {
            setPadding(d.px(18), d.px(14), d.px(18), d.px(14))
            setOnClickListener {
                fecharMenu()
                janelas.salvarPadraoLadoALado()
            }
        }
        for (b in listOf(trocar, salvar)) {
            acoes.addView(
                b,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(d.px(4), 0, d.px(4), 0) },
            )
        }
        menu.addView(acoes, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun rotuloDeSecao(texto: String): TextView = Estilos.texto(atividade, 12f, 0x80EAF2F8.toInt()).apply {
        this.text = texto
        letterSpacing = 0.06f
        gravity = Gravity.CENTER
        maxLines = 1
        setPadding(0, d.px(6), 0, d.px(6))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun textoDeBotao(texto: String, sp: Float): TextView = Estilos.texto(atividade, sp, COR_DO_ICONE).apply {
        this.text = texto
        gravity = Gravity.CENTER
        maxLines = 1
        background = fundoDeBotao()
        isClickable = true
        isFocusable = true
    }

    /** Fundo dos botões do menu: normal e selecionado (raio 10, contorno 1). */
    private fun fundoDeBotao(): StateListDrawable {
        fun retangulo(preenchimento: Int, contorno: Int) = GradientDrawable().apply {
            cornerRadius = d.px(10).toFloat()
            setColor(preenchimento)
            setStroke(d.contorno(), contorno)
        }
        val estados = StateListDrawable()
        estados.addState(intArrayOf(android.R.attr.state_selected), retangulo(0x2E4FD6E8, 0xA64FD6E8.toInt()))
        estados.addState(intArrayOf(), retangulo(0x10FFFFFF, 0x29FFFFFF))
        return estados
    }

    /** Duas barras lado a lado nas proporções do botão (pesos). */
    private class GlifoDeProporcao(contexto: Context, private val pesoA: Float, private val pesoB: Float, private val d: Densidade) : View(contexto) {
        private val tinta = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD9EAF2F8.toInt() }

        override fun onDraw(canvas: Canvas) {
            val vao = d.pxF(4f)
            val total = width - vao
            val larguraA = total * pesoA / (pesoA + pesoB)
            val raio = d.pxF(2f)
            canvas.drawRoundRect(RectF(0f, 0f, larguraA, height.toFloat()), raio, raio, tinta)
            canvas.drawRoundRect(RectF(larguraA + vao, 0f, width.toFloat(), height.toFloat()), raio, raio, tinta)
        }
    }

    companion object {
        private const val ETIQUETA = "DeepGWT/Faixa"
        const val PREF_X = "fabX"
        const val PREF_Y = "fabY"
        const val FLAGS_DA_JANELA: Int = 16777480
        const val COR_DO_ICONE: Int = 0xFFEAF2F8.toInt()
        const val COR_DO_ICONE_ABERTO: Int = 0xFF4FD6E8.toInt()
    }
}
