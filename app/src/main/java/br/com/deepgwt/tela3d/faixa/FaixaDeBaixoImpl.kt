package br.com.deepgwt.tela3d.faixa

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.SharedPreferences
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import br.com.deepgwt.tela3d.catalogo.ItemDoCatalogo
import br.com.deepgwt.tela3d.catalogo.ItemDoHub
import br.com.deepgwt.tela3d.catalogo.MotivoDeMudanca
import br.com.deepgwt.tela3d.catalogo.OuvinteDoCatalogo
import br.com.deepgwt.tela3d.catalogo.PlacaDoItem
import br.com.deepgwt.tela3d.catalogo.TipoDeMudanca
import org.json.JSONObject

/**
 * A faixa de baixo da tela 3D: célula "Cards", fileira de apps com revelação animada, linha de cartões,
 * menus de edição, folha PERSONALIZAR, seletor de ocultos, botão flutuante do modo lado a lado, tema do
 * cromo e as reservas de tela. Só conhece os vizinhos pelas interfaces de `Contratos.kt`.
 *
 * Toda a UI e todas as preferências ficam na thread principal.
 *
 * @param prefs o arquivo `deepgwt_lancador`: aqui só `dockSurfaceMode`, `fabX` e `fabY` (o resto do
 *   estado dos apps é do catálogo)
 */
class FaixaDeBaixoImpl(
    private val atividade: Activity,
    private val prefs: SharedPreferences,
    private val janelas: ServicosDeJanelas,
    private val catalogo: CatalogoParaFaixa,
    private val projecao: PresencaDeProjecaoParaFaixa,
    private val pagina: PaginaDaCentral,
    private val principal: Handler = Handler(Looper.getMainLooper()),
) : FaixaDeBaixo {
    private val d = Densidade(atividade)
    private val temaDoCromo = TemaDoCromoPadrao(atividade)
    override val tema: TemaDoCromo get() = temaDoCromo

    private var cartoes: LinhaDeCartoes? = null

    // views
    private var raiz: FrameLayout? = null
    private val banda = View(atividade)
    private val faixa = LinearLayout(atividade)
    private val conteudo = FrameLayout(atividade)
    private val fileira = LinearLayout(atividade)
    private val rolagemApps = RolagemComElastico(atividade, ESMAECIMENTO_APPS_DP)
    private val linhaDeCartoes = LinearLayout(atividade)
    private lateinit var rolagemCartoes: View

    private val menus = MenusDaFaixa(atividade, temaDoCromo) { raiz }
    private val folha = FolhaDePersonalizacao(atividade, temaDoCromo, menus, catalogo.sobreposicoes) { raiz }
    private var botaoFlutuante: BotaoFlutuanteDeApps? = null

    private val itens = LinkedHashMap<String, ItemDaFileira>()
    private var cartaoAtual: CartaoEspacoDeTrabalho? = null
    private val animadoresDaRevelacao = ArrayList<ValueAnimator>()

    // estado
    private var montada = false
    private var destruida = false
    private var revelada = false
    private var escondidaPelaPagina = false
    private var modoDaCasca = ModoDaCasca.APP_E_CARRO
    private var modoInformado = false
    private var superficie = SuperficieDoDock.LANCADOR
    private var editandoCartoes = false
    private var tipoDeProjecao = TipoDeProjecao.NENHUMA
    private var telaDoEstudio = TelasDoEstudio.NENHUMA
    private var estudioAberto = false
    private var modoDoPreenchimento = "car"
    private var preenchimentoVisivel = true
    private var estilo = EstiloDoDock.PADRAO

    private val planoB = Runnable { revelar() }
    private val enviarPlacaAgora = Runnable { enviarPlaca() }

    /** Liga a linha de cartões (módulo vizinho): se a faixa já foi montada, monta a linha já. */
    fun ligarLinhaDeCartoes(linha: LinhaDeCartoes) {
        cartoes = linha
        if (montada) linha.montarEm(linhaDeCartoes)
    }

    // ------------------------------------------------------------------------------------------
    // Montagem (F1, F2)

    override fun montar(raiz: FrameLayout) {
        if (montada) return
        this.raiz = raiz
        superficie = SuperficieDoDock.LANCADOR
        estilo = EstiloDoDock.daPreferencia(prefs.getString(PREF_ESTILO, null))
        if (!modoInformado) modoDaCasca = janelas.modoDaCasca()
        tipoDeProjecao = projecao.atual()

        construirViews()
        raiz.addView(banda, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.BOTTOM))
        raiz.addView(
            faixa,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, d.px(MedidasDaFaixa.ALTURA_DA_FAIXA_DP), Gravity.BOTTOM),
        )
        botaoFlutuante = BotaoFlutuanteDeApps(atividade, prefs, janelas, pagina) { this.raiz }
        montada = true

        catalogo.registrarOuvinte(OuvinteDoCatalogo { aoCatalogoMudar(it) })
        projecao.aoMudar { aoMudarProjecao(it) }
        cartoes?.montarEm(linhaDeCartoes)

        reconciliar(refazerTudo = true)
        aplicarSuperficie(animar = false)
        aplicarEstiloNasViews()
        aplicarReservas()
        aplicarVisibilidade()
        if (modoDaCasca == ModoDaCasca.LADO_A_LADO) botaoFlutuante?.mostrar()

        faixa.addOnLayoutChangeListener { _, esq, topo, dir, base, esqAntes, topoAntes, dirAntes, baseAntes ->
            if (esq != esqAntes || topo != topoAntes || dir != dirAntes || base != baseAntes) {
                alinharLinhaDeCartoes()
                pagina.avisarLayoutDaCasca()
                agendarPlaca()
            }
        }
        raiz.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> if (!destruida) aplicarReservas() }
        principal.postDelayed(planoB, RevelacaoDaFileira.PLANO_B_MS)
    }

    private fun construirViews() {
        banda.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x52000000, 0xA6000000.toInt()))
        banda.isClickable = false
        banda.isFocusable = false
        banda.visibility = View.GONE

        faixa.orientation = LinearLayout.HORIZONTAL
        faixa.gravity = Gravity.TOP or Gravity.START
        faixa.clipChildren = false
        faixa.clipToPadding = false
        faixa.visibility = View.INVISIBLE

        conteudo.clipChildren = true
        faixa.addView(
            conteudo,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f),
        )

        fileira.orientation = LinearLayout.HORIZONTAL
        fileira.gravity = Gravity.TOP or Gravity.START
        fileira.clipChildren = false
        fileira.clipToPadding = false
        fileira.setPadding(d.px(12), d.px(39), d.px(24), 0)
        rolagemApps.addView(fileira, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        conteudo.addView(rolagemApps, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        linhaDeCartoes.orientation = LinearLayout.HORIZONTAL
        linhaDeCartoes.clipChildren = false
        rolagemCartoes = criarContainerDaLinhaDeCartoes(linhaDeCartoes)
        rolagemCartoes.visibility = View.GONE
        conteudo.addView(rolagemCartoes, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    // ------------------------------------------------------------------------------------------
    // Ciclo de vida

    override fun aoPausar() {
        menus.fecharTodos()
        cartoes?.aoPausar()
    }

    override fun aoDestruir() {
        if (destruida) return
        destruida = true
        principal.removeCallbacks(planoB)
        principal.removeCallbacks(enviarPlacaAgora)
        cancelarRevelacao()
        menus.fecharTodos()
        botaoFlutuante?.remover()
        botaoFlutuante = null
        raiz?.let {
            it.removeView(faixa)
            it.removeView(banda)
        }
        itens.clear()
        cartaoAtual = null
        cartoes = null
    }

    // ------------------------------------------------------------------------------------------
    // Ponte JS

    override fun aplicarIndicadores(json: String) {
        if (!montada || destruida) return
        val novo = IndicadoresDaFaixa.interpretar(json) ?: return

        var temaMudou = false
        var acentoMudou = false
        if (novo.temaClaro != null || novo.acento != null) {
            val claroAntes = temaDoCromo.claro
            val acentoAntes = temaDoCromo.acento
            temaDoCromo.atualizar(novo.temaClaro ?: claroAntes, novo.acento ?: acentoAntes)
            temaMudou = temaDoCromo.claro != claroAntes
            acentoMudou = temaDoCromo.acento != acentoAntes
        }
        novo.estudioAberto?.let { estudioAberto = it }
        novo.estiloDoDock?.let { definirEstilo(it) }
        val edicaoAntes = editandoCartoes
        novo.editandoCartoes?.let { editandoCartoes = it }
        if (editandoCartoes != edicaoAntes) aplicarSuperficie(animar = false)
        novo.telaDoEstudio?.let { tela ->
            if (tela != telaDoEstudio) {
                telaDoEstudio = tela
                cartaoAtual?.aoMudarTelaDoEstudio(tela)
            }
        }

        cartoes?.let { linha ->
            try {
                linha.aplicarIndicadores(JSONObject(json))
            } catch (e: Exception) {
                Log.w(ETIQUETA, "Falha ao repassar os indicadores aos cartões", e)
            }
        }
        if (temaMudou || acentoMudou) {
            repintarTudo()
            if (temaMudou) {
                catalogo.avisarTemaMudou()
                cartoes?.aoMudarTema()
            }
            if (acentoMudou) cartoes?.aoMudarAcento()
        }
        aplicarVisibilidade()
    }

    /** Último `updateCenterFill` aceito: (modo, visível). O chip que mostraria isso está descartado. */
    val preenchimentoDoCentro: Pair<String, Boolean> get() = modoDoPreenchimento to preenchimentoVisivel

    override fun aplicarPreenchimentoCentral(json: String) {
        val novo = IndicadoresDaFaixa.interpretarPreenchimento(json) ?: return
        novo.modo?.let { modoDoPreenchimento = it }
        novo.visivel?.let { preenchimentoVisivel = it }
    }

    override fun pedirEscondida(escondida: Boolean) {
        if (escondida == escondidaPelaPagina) return
        escondidaPelaPagina = escondida
        if (montada) aplicarVisibilidade()
    }

    override fun aoPaginaCarregada() {
        escondidaPelaPagina = false
        if (!montada || destruida) return
        aplicarVisibilidade()
        pagina.avisarLayoutDaCasca()
        agendarPlaca()
    }

    // ------------------------------------------------------------------------------------------
    // Visibilidade e revelação (F3, F4, F13)

    private fun aplicarVisibilidade() {
        faixa.visibility = when (RegrasDaFaixa.estadoDaFaixa(revelada, escondidaPelaPagina, modoDaCasca)) {
            EstadoDaFaixa.VISIVEL -> View.VISIBLE
            EstadoDaFaixa.INVISIVEL -> View.INVISIBLE
            EstadoDaFaixa.GONE -> View.GONE
        }
        aplicarBanda()
        agendarPlaca()
    }

    private fun aplicarBanda() {
        val altura = reservas().alturaDaBandaPx
        banda.visibility = if (RegrasDaFaixa.bandaVisivel(revelada, altura)) View.VISIBLE else View.GONE
    }

    override fun revelar() {
        if (revelada || destruida) return
        revelada = true
        principal.removeCallbacks(planoB)
        if (!montada) return
        aplicarVisibilidade()
        val visiveis = (0 until fileira.childCount).mapNotNull { fileira.getChildAt(it) as? ItemDaFileira }
        if (modoDaCasca == ModoDaCasca.LADO_A_LADO) {
            visiveis.forEach { it.alpha = 1f }
            janelas.restaurarAppsDoLadoALado()
            return
        }
        if (superficie != SuperficieDoDock.LANCADOR || visiveis.isEmpty()) {
            // A fileira não está à mostra: a linha de cartões aparece de uma vez, sem animação.
            visiveis.forEach { it.alpha = 1f }
            return
        }
        animarRevelacao(visiveis)
    }

    private fun animarRevelacao(visiveis: List<ItemDaFileira>) {
        cancelarRevelacao()
        val n = visiveis.size
        for ((i, item) in visiveis.withIndex()) {
            val animador = ValueAnimator.ofFloat(0f, 1f)
            animador.duration = RevelacaoDaFileira.DURACAO_MS
            animador.startDelay = RevelacaoDaFileira.atrasoMs(n, i)
            animador.interpolator = DecelerateInterpolator(RevelacaoDaFileira.FATOR_DA_DESACELERACAO)
            animador.addUpdateListener {
                val t = it.animatedValue as Float
                item.translationX = d.pxF(RevelacaoDaFileira.deslocamentoDp(t))
                item.alpha = RevelacaoDaFileira.alfa(t)
                item.rastroPx = d.pxF(RevelacaoDaFileira.rastroDp(t))
            }
            animador.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    item.translationX = 0f
                    item.alpha = 1f
                    item.rastroPx = 0f
                    animadoresDaRevelacao.remove(animation)
                }
            })
            animadoresDaRevelacao.add(animador)
            animador.start()
        }
    }

    private fun cancelarRevelacao() {
        val copia = animadoresDaRevelacao.toList()
        animadoresDaRevelacao.clear()
        for (a in copia) a.cancel()
    }

    // ------------------------------------------------------------------------------------------
    // Casca (F31) e proporção

    override fun aoMudarModoDaCasca(modo: ModoDaCasca) {
        modoInformado = true
        modoDaCasca = modo
        if (!montada || destruida) return
        if (modo == ModoDaCasca.LADO_A_LADO) botaoFlutuante?.mostrar() else botaoFlutuante?.remover()
        aplicarVisibilidade()
        aplicarReservas()
        pagina.avisarLayoutDaCasca()
    }

    override fun aoMudarProporcao(proporcao: Proporcao) {
        botaoFlutuante?.atualizarDestaque(proporcao)
        if (montada && !destruida) pagina.avisarLayoutDaCasca()
    }

    override fun registrarUsoDeApp(pacote: String) {
        if (pacote.isEmpty()) return
        catalogo.registrarAbertura(pacote)
    }

    override fun aoMudarProjecao(tipo: TipoDeProjecao) {
        tipoDeProjecao = tipo
        if (montada && !destruida) reconciliar()
    }

    // ------------------------------------------------------------------------------------------
    // Medidas (F28 a F30, F35)

    private fun medidas(): MedidasDaTela = pagina.medidasAtuais().copy(modo = modoDaCasca)

    override fun reservas(): ReservasDaFaixa = calcularReservas(medidas())

    override fun topoDaFaixaNaTelaPx(): Int? {
        if (!montada || faixa.height <= 0 || faixa.visibility == View.GONE) return null
        val posicao = IntArray(2)
        faixa.getLocationOnScreen(posicao)
        return posicao[1]
    }

    override fun prefereAbrirComoJanela(pacote: String): Boolean = catalogo.prefereJanela(pacote)

    override fun aoMudarLayout() {
        if (montada && !destruida) aplicarReservas()
    }

    private fun aplicarReservas() {
        if (!montada) return
        val r = reservas()
        val parametros = faixa.layoutParams as? FrameLayout.LayoutParams
        if (parametros != null && (parametros.height != r.alturaDaFaixaPx || parametros.bottomMargin != r.margemInferiorPx)) {
            parametros.height = r.alturaDaFaixaPx
            parametros.bottomMargin = r.margemInferiorPx
            faixa.layoutParams = parametros
        }
        if (faixa.paddingLeft != r.insetEsquerdoPx || faixa.paddingRight != r.insetDireitoPx) {
            faixa.setPadding(r.insetEsquerdoPx, 0, r.insetDireitoPx, 0)
        }
        val paramsDaBanda = banda.layoutParams as? FrameLayout.LayoutParams
        if (paramsDaBanda != null && paramsDaBanda.height != r.alturaDaBandaPx) {
            paramsDaBanda.height = r.alturaDaBandaPx
            banda.layoutParams = paramsDaBanda
        }
        aplicarBanda()
        alinharLinhaDeCartoes()
    }

    /** F35: alinha o primeiro cartão com a borda esquerda da área de widgets. */
    private fun alinharLinhaDeCartoes() {
        if (!montada) return
        val margem = (conteudo.layoutParams as? LinearLayout.LayoutParams)?.leftMargin ?: 0
        val preenchimento = RegrasDaFaixa.preenchimentoEsquerdoDaLinhaDeCartoes(
            areaEsquerdaEsq = janelas.limitesDaAreaEsquerda().left,
            paginaEsq = pagina.medidasAtuais().paginaEsq,
            preenchimentoEsquerdoDaFaixa = faixa.paddingLeft,
            margemEsquerdaDoConteiner = margem,
        )
        if (linhaDeCartoes.paddingLeft != preenchimento) {
            linhaDeCartoes.setPadding(preenchimento, linhaDeCartoes.paddingTop, linhaDeCartoes.paddingRight, linhaDeCartoes.paddingBottom)
        }
    }

    // ------------------------------------------------------------------------------------------
    // Superfícies (F7, F10)

    private fun definirSuperficie(nova: SuperficieDoDock, animar: Boolean, comando: String?) {
        superficie = nova
        prefs.edit().putString(PREF_SUPERFICIE, nova.valor).apply()
        aplicarSuperficie(animar)
        if (comando != null) pagina.comandoDoDock(comando)
    }

    private fun aplicarSuperficie(animar: Boolean) {
        if (!montada) return
        val parametros = conteudo.layoutParams as? LinearLayout.LayoutParams
        val margem = d.px(RegrasDaFaixa.margemDoConteinerDp(superficie))
        if (parametros != null && parametros.leftMargin != margem) {
            parametros.leftMargin = margem
            conteudo.layoutParams = parametros
        }
        alinharLinhaDeCartoes()
        val mostrarApps = RegrasDaFaixa.mostrarFileiraDeApps(superficie, editandoCartoes)
        if (mostrarApps) trocarConteudo(rolagemApps, rolagemCartoes, animar) else trocarConteudo(rolagemCartoes, rolagemApps, animar)
        agendarPlaca()
    }

    /** A vista que entra vai a alfa 1 em 180 ms; a que sai vai a 0 em 160 ms e fica gone. */
    private fun trocarConteudo(entra: View, sai: View, animar: Boolean) {
        entra.animate().cancel()
        sai.animate().cancel()
        if (!animar) {
            entra.alpha = 1f
            entra.visibility = View.VISIBLE
            sai.visibility = View.GONE
            sai.alpha = 1f
            return
        }
        if (entra.visibility != View.VISIBLE) {
            entra.alpha = 0f
            entra.visibility = View.VISIBLE
        }
        entra.animate().alpha(1f).setDuration(ENTRADA_MS).start()
        if (sai.visibility == View.VISIBLE) {
            sai.animate().alpha(0f).setDuration(SAIDA_MS).withEndAction {
                sai.visibility = View.GONE
                sai.alpha = 1f
            }.start()
        }
    }

    // ------------------------------------------------------------------------------------------
    // Tema (F8, F9)

    private fun repintarTudo() {
        for (item in itens.values) item.atualizarPlaca()
        cartaoAtual?.aplicarTema()
    }

    // ------------------------------------------------------------------------------------------
    // Hospedagem dos cartões (F23, F27)

    override fun criarCartaoEspacoDeTrabalho(): View {
        val cartao = CartaoEspacoDeTrabalho(atividade, temaDoCromo, acoesDoCartao)
        cartao.layoutParams = LinearLayout.LayoutParams(
            d.px(CartaoEspacoDeTrabalho.LARGURA_DP),
            d.px(CartaoEspacoDeTrabalho.ALTURA_DP),
        ).apply { rightMargin = d.px(CartaoEspacoDeTrabalho.MARGEM_DIREITA_DP) }
        cartao.iniciar(telaDoEstudio)
        cartaoAtual = cartao
        return cartao
    }

    override fun criarContainerDaLinhaDeCartoes(linha: View): View {
        val rolagem = RolagemComElastico(atividade, ESMAECIMENTO_CARTOES_DP)
        (linha.parent as? ViewGroup)?.removeView(linha)
        rolagem.addView(linha, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        return rolagem
    }

    private val acoesDoCartao = object : CartaoEspacoDeTrabalho.Acoes {
        override fun aoTocarApps() = definirSuperficie(SuperficieDoDock.LANCADOR, animar = true, comando = COMANDO_MOSTRAR_APPS)

        override fun aoTocarDesktops() = abrirTelaDoEstudio(TelasDoEstudio.PAGINAS, COMANDO_LAYOUT_PAGINAS)

        override fun aoTocarCartoesEWidgets() = abrirTelaDoEstudio(TelasDoEstudio.CARTOES_E_WIDGETS, COMANDO_LAYOUT_CARTOES)

        override fun aoTocarAparencia() = abrirTelaDoEstudio(TelasDoEstudio.APARENCIA, COMANDO_LAYOUT_APARENCIA)

        override fun aoTocarRetornar() {
            cartaoAtual?.mostrarAcoes()
            if (estudioAberto) pagina.comandoDoDock(COMANDO_FECHAR_ESTUDIO)
        }
    }

    private fun abrirTelaDoEstudio(tela: String, comando: String) {
        telaDoEstudio = tela
        cartaoAtual?.aoMudarTelaDoEstudio(tela)
        pagina.comandoDoDock(comando)
    }

    // ------------------------------------------------------------------------------------------
    // Fileira de apps (F11 a F16)

    private fun aoCatalogoMudar(motivo: MotivoDeMudanca) {
        if (!montada || destruida) return
        when (motivo.tipo) {
            TipoDeMudanca.TEMA -> repintarTudo()
            TipoDeMudanca.PERSONALIZACAO -> aoPersonalizacaoMudou(motivo.pacote)
            TipoDeMudanca.PACOTE_INSTALADO, TipoDeMudanca.PACOTE_REMOVIDO ->
                reconciliar(refazer = setOfNotNull(motivo.pacote))
            TipoDeMudanca.OCULTOS, TipoDeMudanca.RECENTES, TipoDeMudanca.JANELA -> reconciliar()
        }
    }

    private fun aoPersonalizacaoMudou(chave: String?) {
        reconciliar(refazer = setOfNotNull(chave))
        if (chave != null && chave != SobreposicoesDoDock.CHAVE_DO_HUB) cartoes?.redesenharChipDeOrigemDoApp(chave)
    }

    /**
     * Refaz a fileira a partir do catálogo: reaproveita as views dos itens que continuam, cria as dos
     * novos (já opacos depois da revelação) e descarta os que saíram. Os ícones só são refeitos para os
     * itens novos, os pedidos em [refazer] ou todos com [refazerTudo].
     */
    private fun reconciliar(refazer: Set<String> = emptySet(), refazerTudo: Boolean = false) {
        if (!montada || destruida) return
        val linha = consultar { catalogo.linhaDeApps() } ?: emptyList()
        val recentes = consultar { catalogo.recentes() } ?: emptyList()
        val hub = consultar { catalogo.hubGwm() }
        val plano = PlanoDaFileira.montar(
            projecaoPresente = tipoDeProjecao != TipoDeProjecao.NENHUMA,
            hubVisivel = hub != null,
            recentes = recentes.map { it.pacote },
            linha = linha.map { AppDaLinha(it.pacote, it.oculto) },
        )
        val dados = HashMap<String, ItemDoCatalogo>()
        for (i in linha) dados[i.pacote] = i
        for (i in recentes) dados[i.pacote] = i

        val ordem = ArrayList<ItemDaFileira>(plano.size)
        val mantidos = HashSet<String>()
        for (entrada in plano) {
            val existente = itens[entrada.chave]
            val vista = existente ?: criarItem(entrada)
            val refazerEste = existente == null || refazerTudo || entrada.chave in refazer
            when (entrada.tipo) {
                TipoDeItem.PROJECAO -> vincularProjecao(vista)
                TipoDeItem.HUB -> hub?.let { vincularHub(vista, it) }
                TipoDeItem.LADO_A_LADO -> if (refazerEste) vincularLadoALado(vista)
                TipoDeItem.APP -> {
                    val app = dados[entrada.chave] ?: continue
                    if (refazerEste || vista.rotulo != app.rotulo) vincularApp(vista, app)
                }
            }
            itens[entrada.chave] = vista
            mantidos.add(entrada.chave)
            ordem.add(vista)
        }
        itens.keys.retainAll(mantidos)
        colocarNaFileira(ordem)
    }

    private inline fun <T> consultar(bloco: () -> T): T? = try {
        bloco()
    } catch (e: Exception) {
        Log.w(ETIQUETA, "Falha ao consultar o catálogo", e)
        null
    }

    private fun criarItem(entrada: ItemPlanejado): ItemDaFileira {
        val item = ItemDaFileira(atividade, entrada.chave, temaDoCromo)
        item.definirReflexo(estilo.reflexo)
        item.alpha = if (revelada) 1f else 0f
        item.layoutParams = LinearLayout.LayoutParams(d.px(ItemDaFileira.LARGURA_DP), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            rightMargin = d.px(ItemDaFileira.MARGEM_DIREITA_DP)
        }
        when (entrada.tipo) {
            TipoDeItem.PROJECAO -> item.setOnClickListener { janelas.abrirProjecao(tipoDeProjecao) }
            TipoDeItem.LADO_A_LADO -> item.setOnClickListener { janelas.definirModoDaCasca(ModoDaCasca.LADO_A_LADO) }
            TipoDeItem.HUB -> {
                item.setOnClickListener { abrirMenuDoHub(item) }
                item.setOnLongClickListener {
                    abrirMenuDeEdicao(item)
                    true
                }
            }
            TipoDeItem.APP -> {
                item.setOnClickListener { abrirApp(entrada.chave, item.rotulo) }
                item.setOnLongClickListener {
                    abrirMenuDeEdicao(item)
                    true
                }
            }
        }
        return item
    }

    private fun ladoDoIcone(): Int = d.px(MEDIDA_DO_ICONE_DP)

    private fun vincularApp(vista: ItemDaFileira, app: ItemDoCatalogo) {
        vista.vincular(app.rotulo, catalogo.normalizarIconeAdaptativo(app.icone, ladoDoIcone()), app.placa)
    }

    private fun vincularHub(vista: ItemDaFileira, hub: ItemDoHub) {
        vista.vincular(hub.rotulo, catalogo.normalizarIconeAdaptativo(hub.icone, ladoDoIcone()), hub.placa)
    }

    private fun vincularProjecao(vista: ItemDaFileira) {
        val tipo = tipoDeProjecao
        vista.vincular(projecao.rotulo(tipo), projecao.icone(tipo), PlacaDoItem.Clara)
    }

    private fun vincularLadoALado(vista: ItemDaFileira) {
        val glifo = BitmapDrawable(atividade.resources, Glifos.ladoALado(ladoDoIcone(), COR_DO_GLIFO_LADO_A_LADO))
        vista.vincular("Side by Side", glifo, PlacaDoItem.Escura, "Side by Side: two apps split the screen")
    }

    private fun colocarNaFileira(ordem: List<ItemDaFileira>) {
        val igual = fileira.childCount == ordem.size && ordem.indices.all { fileira.getChildAt(it) === ordem[it] }
        if (igual) return
        fileira.removeAllViews()
        for (item in ordem) fileira.addView(item)
    }

    // ------------------------------------------------------------------------------------------
    // Estilo do dock (10.3): ícones nativos, vidro desenhado pela página

    private fun definirEstilo(novo: EstiloDoDock) {
        if (novo == estilo) return
        estilo = novo
        prefs.edit().putString(PREF_ESTILO, novo.valor).apply()
        if (montada && !destruida) aplicarEstiloNasViews()
    }

    /** Põe a fileira e a célula "Cards" na altura do estilo e liga ou desliga o reflexo. */
    private fun aplicarEstiloNasViews() {
        val topo = d.px(estilo.topoDaFileiraDp)
        fileira.setPadding(d.px(12), topo, d.px(24), 0)
        for (item in itens.values) item.definirReflexo(estilo.reflexo)
        agendarPlaca()
    }

    /** Junta os pedidos do mesmo quadro de layout num só envio. */
    private fun agendarPlaca() {
        if (!montada || destruida) return
        principal.removeCallbacks(enviarPlacaAgora)
        principal.post(enviarPlacaAgora)
    }

    /**
     * Manda à página o retângulo do vidro, em px CSS (= dp) com a origem na página. A bandeja fica
     * escondida quando a faixa não está à mostra ou quando a superfície é a linha de cartões.
     */
    private fun enviarPlaca() {
        if (!montada || destruida) return
        val aMostra = RegrasDaFaixa.estadoDaFaixa(revelada, escondidaPelaPagina, modoDaCasca) == EstadoDaFaixa.VISIVEL &&
            RegrasDaFaixa.mostrarFileiraDeApps(superficie, editandoCartoes) &&
            faixa.width > 0 && faixa.height > 0
        val visivel = aMostra && estilo.temPlaca
        if (!visivel) {
            pagina.enviarPlacaDoDock(estilo.valor, false, 0f, 0f, 0f, 0f)
            return
        }
        val m = medidas()
        val onde = IntArray(2)
        faixa.getLocationOnScreen(onde)
        val margem = d.px(MARGEM_DA_PLACA_DP)
        val esq = if (estilo.placaNaLarguraToda) m.paginaEsq else onde[0] + faixa.paddingLeft - margem
        val dir = if (estilo.placaNaLarguraToda) m.paginaDir else onde[0] + faixa.width - faixa.paddingRight + margem
        val topo = onde[1] + d.px(estilo.placaTopoDp)
        val base = estilo.placaBaseDp?.let { onde[1] + d.px(it) } ?: m.paginaBase
        val dens = m.densidade
        pagina.enviarPlacaDoDock(
            estilo.valor,
            true,
            (esq - m.paginaEsq) / dens,
            (topo - m.paginaTopo) / dens,
            (dir - esq) / dens,
            (base - topo) / dens,
        )
    }

    // ------------------------------------------------------------------------------------------
    // Abrir apps, menus (F15 a F21)

    /** Abre o app pelo caminho completo das janelas e o registra nos recentes; o app falso do emulador só avisa. */
    private fun abrirApp(pacote: String, rotulo: String) {
        if (catalogo.ehStubDeEmulador(pacote)) {
            Toast.makeText(atividade, "$rotulo (emulator stub)", Toast.LENGTH_SHORT).show()
            return
        }
        janelas.abrirApp(pacote, rotulo)
        registrarUsoDeApp(pacote)
    }

    private fun abrirMenuDeEdicao(item: ItemDaFileira) {
        val chave = item.chave
        val ehHub = chave == SobreposicoesDoDock.CHAVE_DO_HUB
        val podeJanela = !ehHub && catalogo.podeAlternarJanela(chave)
        val linhas = RegrasDoMenuDeEdicao.linhas(
            podeAbrirComoJanela = podeJanela,
            abrirComoJanelaMarcado = podeJanela && catalogo.prefereJanela(chave),
            haOcultos = catalogo.ocultos().isNotEmpty(),
            podeDesinstalar = !ehHub && catalogo.podeDesinstalar(chave),
        )
        val acoes = linhas.map { linha ->
            val acao: () -> Unit = {
                when (linha) {
                    LinhaDoMenuDeEdicao.PERSONALIZAR -> abrirFolha(item)
                    LinhaDoMenuDeEdicao.OCULTAR -> if (ehHub) catalogo.ocultarHubGwm() else catalogo.ocultar(chave)
                    LinhaDoMenuDeEdicao.ABRIR_COMO_JANELA, LinhaDoMenuDeEdicao.ABRIR_COMO_JANELA_MARCADO -> {
                        catalogo.alternarAbrirComoJanela(chave)
                    }
                    LinhaDoMenuDeEdicao.REEXIBIR -> abrirSeletorDeOcultos()
                    LinhaDoMenuDeEdicao.DESINSTALAR -> catalogo.desinstalar(chave)
                }
            }
            linha.texto to acao
        }
        menus.mostrarMenuDeEdicao(item, acoes)
    }

    private fun abrirMenuDoHub(item: ItemDaFileira) {
        val linhas = consultar { catalogo.fixadosDoHub() }.orEmpty().map { fixado ->
            LinhaDoHub(catalogo.iconeDoFixado(fixado.pacote), fixado.rotulo) { abrirApp(fixado.pacote, fixado.rotulo) }
        }
        menus.mostrarMenuDoHub(item, linhas)
    }

    private fun abrirSeletorDeOcultos() {
        val ocultos = consultar { catalogo.ocultos() }.orEmpty()
        if (ocultos.isEmpty()) return
        val lado = d.px(MenusDaFaixa.ICONE_PEQUENO_DP)
        val linhas = ocultos.map { LinhaDeOculto(it, catalogo.usaPlacaEscura(it.pacote), catalogo.normalizarIconeAdaptativo(it.icone, lado)) }
        menus.mostrarSeletorDeOcultos(linhas) { pacote -> catalogo.restaurar(pacote) }
    }

    private fun abrirFolha(item: ItemDaFileira) {
        val chave = item.chave
        val padrao = catalogo.rotuloPadrao(chave)
        val dica = if (padrao.isEmpty() || padrao == chave && item.rotulo.isNotEmpty()) item.rotulo.ifEmpty { chave } else padrao
        folha.mostrar(item, chave, dica, catalogo.sobreposicoes.nome(chave)) { aoPersonalizacaoMudou(chave) }
    }

    companion object {
        private const val ETIQUETA = "DeepGWT/Faixa"
        const val PREF_SUPERFICIE = "dockSurfaceMode"
        const val PREF_ESTILO = "dockStyle"
        const val MARGEM_DA_PLACA_DP = 6
        const val ESMAECIMENTO_APPS_DP = 72
        const val ESMAECIMENTO_CARTOES_DP = 42
        const val MEDIDA_DO_ICONE_DP = 60
        const val ENTRADA_MS = 180L
        const val SAIDA_MS = 160L
        const val COR_DO_GLIFO_LADO_A_LADO: Int = 0xFFEAF2F8.toInt()

        const val COMANDO_MOSTRAR_APPS = "showLauncher"
        const val COMANDO_ABRIR_ESTUDIO = "openDesktopStudio"
        const val COMANDO_LAYOUT_PAGINAS = "openLayoutDesktops"
        const val COMANDO_LAYOUT_CARTOES = "openLayoutCards"
        const val COMANDO_LAYOUT_APARENCIA = "openLayoutAppearance"
        const val COMANDO_FECHAR_ESTUDIO = "closeDesktopStudio"
    }
}
