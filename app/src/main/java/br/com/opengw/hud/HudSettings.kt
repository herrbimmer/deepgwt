package br.com.opengw.hud

import br.com.opengw.nucleo.OpenGw
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import br.com.opengw.nucleo.Preferencia
import br.com.opengw.nucleo.Preferencias

/**
 * Os ajustes da camada do HUD, gravados em `opengw_prefs` e espelhados em estado observável.
 *
 * Central e HUD rodam no mesmo processo, então a tela de Configurações e a [HudOverlayView] leem o
 * mesmo objeto: mexer no controle deslizante repinta o HUD no mesmo quadro, sem broadcast.
 *
 * TAMANHOS EM PORCENTAGEM, NUNCA EM sp/dp
 *
 * O HUD é um display privado de 480x240 com densidade própria — um `sp` herdado da central sai com
 * tamanho errado lá. Tudo aqui é percentual da ALTURA real da view, resolvido em pixel no desenho.
 */
object HudSettings {

    /**
     * O número da velocidade ocupa esta fração da altura do HUD.
     *
     * O PADRÃO É 30 PORQUE 55 ERA GRANDE DEMAIS NO PARA-BRISA
     *
     * O número desenhado aqui é ampliado pela ótica do HUD antes de chegar ao olho, então a mesma
     * fração que parece equilibrada na prévia da central sai enorme projetada no vidro. 30% é o
     * valor pedido depois de ver o resultado no carro. Quem já mexeu no controle deslizante não é
     * afetado: a preferência gravada continua valendo, e só quem nunca tocou nela recebe o padrão
     * novo.
     */
    const val SPEED_SIZE_MIN = 20
    const val SPEED_SIZE_MAX = 80
    const val SPEED_SIZE_DEFAULT = 30

    const val SLI_SIZE_MIN = 10
    const val SLI_SIZE_MAX = 45
    const val SLI_SIZE_DEFAULT = 28

    /**
     * Espessura da fonte, na escala de peso do OpenType (400 = normal, 700 = negrito).
     *
     * POR QUE 200 NO PISO E NÃO 100
     *
     * O HUD é ótico: o que ele desenha vira luz projetada no para-brisa, e um traço de peso 100
     * atravessado por sol de frente simplesmente não chega ao olho. 200 já é fino o bastante para
     * resolver a queixa de "fonte muito espessa" sem produzir um número que some em pleno dia.
     *
     * O PADRÃO É 700 PORQUE ERA O QUE JÁ ESTAVA DESENHADO
     *
     * Antes deste ajuste o desenho pedia `Typeface.BOLD` fixo, que é peso 700. Manter 700 como
     * padrão é o que faz uma atualização do app não mexer no HUD de quem estava satisfeito.
     */
    const val PESO_MIN = 200
    const val PESO_MAX = 900
    const val PESO_PASSO = 100
    const val PESO_PADRAO = 700

    /**
     * Peso fixo da cortina de alerta, que NÃO obedece ao ajuste acima.
     *
     * O alerta é o único conteúdo do HUD que o motorista não escolheu ver. Deixá-lo afinar junto
     * com o resto permitiria configurar o carro para sussurrar "porta aberta" — é a mesma razão
     * pela qual a cortina também ignora a escala de página.
     */
    const val PESO_DO_ALERTA = 800

    /**
     * Os três atalhos que o relatório pediu. Qualquer outro RGB continua válido.
     *
     * ARITMÉTICA, E NÃO A CLASSE DA PLATAFORMA
     *
     * Estas constantes são inicializadas junto com o objeto, e o objeto agora é alcançado por
     * [HudPaginas] — que É testado numa JVM sem Android, onde as classes gráficas da plataforma
     * são esqueletos que estouram ao serem chamados. Uma constante de cor não precisa da
     * plataforma para existir: ARGB opaco é 0xFF no topo e um byte por canal.
     */
    val PRESET_VERMELHO = argbOpaco(255, 0, 0)
    val PRESET_BRANCO = argbOpaco(255, 255, 255)
    val PRESET_AZUL = argbOpaco(64, 160, 255)

    /** Cinza-claro dos rótulos, unidades e cabeçalho — o `corSecundaria` que o desenho tinha fixo. */
    val COR_ROTULO_PADRAO = argbOpaco(170, 180, 190)

    private fun argbOpaco(r: Int, g: Int, b: Int): Int =
            (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    /** Descarta o alfa de [cor] e devolve o mesmo RGB, opaco. Mesmo motivo do [argbOpaco]. */
    private fun opaco(cor: Int): Int = cor or (0xFF shl 24)

    /**
     * As preferências, ou `null` quando não há processo de app por baixo.
     *
     * POR QUE ANULÁVEL E NÃO DIRETO
     *
     * [br.com.opengw.nucleo.OpenGw.contextoProtegido] só existe dentro do processo da central. Fora dele — nos
     * testes de unidade, que rodam numa JVM sem Android — ele estoura, e o `@After` de um teste de
     * páginas derrubava a classe inteira, inclusive os testes de porta aberta que nada têm a ver
     * com preferência nenhuma. Sem preferência, os valores ficam nos padrões em memória: é o mesmo
     * comportamento de uma primeira instalação, que é exatamente o que um teste quer ver.
     */
    private fun prefs() =
            runCatching {
                        OpenGw.contextoProtegido
                                .getSharedPreferences("opengw_prefs", Context.MODE_PRIVATE)
                    }
                    .getOrNull()

    // Espelhos observáveis. A UI lê/escreve por aqui; o setter grava a preferência junto.
    var speedSizePct by mutableIntStateOf(SPEED_SIZE_DEFAULT)
        private set

    var speedColor by mutableIntStateOf(PRESET_BRANCO)
        private set

    var sliSizePct by mutableIntStateOf(SLI_SIZE_DEFAULT)
        private set

    var showSli by mutableStateOf(true)
        private set

    var showAlerts by mutableStateOf(true)
        private set

    /** Espessura de tudo o que o HUD escreve, exceto a cortina de alerta ([PESO_DO_ALERTA]). */
    var pesoDaFonte by mutableIntStateOf(PESO_PADRAO)
        private set

    // ---- Controle pelo volante ---------------------------------------------------------------

    /**
     * Os direcionais do volante trocam as páginas do HUD.
     *
     * DESLIGADO DEVOLVE O VOLANTE AO CARRO, NÃO DESLIGA O HUD
     *
     * As quatro teclas (1024-1027) são as mesmas que navegam os cards do painel. Enquanto a camada
     * do HUD está no ar e este interruptor está ligado, elas pertencem ao HUD; desligá-lo faz
     * [HudWheelInput] devolver o evento inteiro para o tratamento normal do carro, e o HUD passa a
     * mostrar a página escolhida aqui na central, parada. É o "modo padrão do carro" com a camada
     * ainda desenhando.
     */
    var wheelControlEnabled by mutableStateOf(true)
        private set

    // ---- Páginas: ordem, quais entram e a cara de cada uma ------------------------------------

    /**
     * A ordem em que os direcionais andam pelas páginas.
     *
     * Guardada por NOME do enum, não por índice: acrescentar uma página nova no meio de
     * [HudPaginas.Pagina] não embaralha a ordem de quem já configurou a dele. Nome que não existe
     * mais é descartado na leitura, e página nova que ainda não está gravada entra no fim.
     */
    var ordemPaginas by mutableStateOf(HudPaginas.Pagina.entries.toList())
        private set

    /** As páginas que participam do rodízio. Nunca fica vazio — ver [ajustarPaginaAtiva]. */
    var paginasAtivas by mutableStateOf(HudPaginas.Pagina.entries.toSet())
        private set

    /** Cor do valor principal, por página. Ausente = o branco padrão do desenho. */
    var coresDePagina by mutableStateOf(emptyMap<HudPaginas.Pagina, Int>())
        private set

    /** Escala do conteúdo, por página, em % de 100. Ausente = 100. */
    var escalasDePagina by mutableStateOf(emptyMap<HudPaginas.Pagina, Int>())
        private set

    const val ESCALA_MIN = 70
    const val ESCALA_MAX = 140
    const val ESCALA_PADRAO = 100

    // ---- Tema do HUD (Alpha 5.9) ---------------------------------------------------------------
    //
    // O que só o tema do instalador ajusta ([HudTema], [aplicarTema]). Os padrões são exatamente o
    // desenho da 5.8: sem tema aplicado, nada disto muda um pixel.

    /** Rótulos, unidades, cabeçalho, "km/h", artista. A cortina de alerta não usa: é fixa. */
    var corRotulo by mutableIntStateOf(COR_ROTULO_PADRAO)
        private set

    var fonte by mutableStateOf(HudFonte.PADRAO)
        private set

    /** O nome da página no alto das páginas que não são a de velocidade. */
    var mostrarCabecalho by mutableStateOf(true)
        private set

    /** A fileira de pontinhos que diz em qual página o HUD está. */
    var mostrarPontos by mutableStateOf(true)
        private set

    /** "km/h" embaixo do número da velocidade. */
    var unidadeVelocidade by mutableStateOf(true)
        private set

    /** A placa de limite à esquerda da velocidade em vez de à direita. */
    var placaAEsquerda by mutableStateOf(false)
        private set

    /** Campos das páginas de números que o tema trocou. Ausente = [HudLayout.padrao]. */
    var layoutsDePagina by mutableStateOf(emptyMap<HudPaginas.Pagina, HudLayout>())
        private set

    /** Nome de página que o tema trocou. Ausente = [HudPaginas.Pagina.titulo]. */
    var titulosDePagina by mutableStateOf(emptyMap<HudPaginas.Pagina, String>())
        private set

    /** Nome do tema aplicado por último, ou null quando o HUD está no padrão. */
    var nomeDoTema by mutableStateOf<String?>(null)
        private set

    // ---- Modo noturno (Alpha 9.5) -------------------------------------------------------------

    /**
     * O HUD projeta luz no para-brisa: o branco que lê bem ao sol ofusca à noite. O modo noturno
     * escurece o desenho inteiro (páginas e cortina de alerta) para [brilhoNoturno] % e, se
     * [noturnoAmbar], puxa as cores para o âmbar, que cansa menos o olho no escuro.
     *
     * AUTOMÁTICO segue o farol, pela mesma regra e pela mesma preferência dos temas do painel
     * ([br.com.opengw.veiculo.HeadlightPolicy], "Acender o modo noturno"):
     * HUD e painel escurecem juntos. Vem DESLIGADO: quem não escolher, vê o HUD como sempre.
     */
    var modoNoturno by mutableStateOf(HudModoNoturno.DESLIGADO)
        private set

    var brilhoNoturno by mutableIntStateOf(BRILHO_NOTURNO_PADRAO)
        private set

    var noturnoAmbar by mutableStateOf(false)
        private set

    const val BRILHO_NOTURNO_MIN = 20
    const val BRILHO_NOTURNO_MAX = 90
    const val BRILHO_NOTURNO_PADRAO = 55

    fun ajustarModoNoturno(valor: HudModoNoturno) {
        modoNoturno = valor
        prefs()?.edit { putString(Preferencias.HUD_NIGHT_MODE.chave, valor.id) }
        HudOverlayController.requestRedraw()
    }

    fun ajustarBrilhoNoturno(valor: Int) {
        val v = valor.coerceIn(BRILHO_NOTURNO_MIN, BRILHO_NOTURNO_MAX)
        brilhoNoturno = v
        prefs()?.edit { putInt(Preferencias.HUD_NIGHT_BRIGHTNESS.chave, v) }
        HudOverlayController.requestRedraw()
    }

    fun ajustarNoturnoAmbar(valor: Boolean) {
        noturnoAmbar = valor
        prefs()?.edit { putBoolean(Preferencias.HUD_NIGHT_AMBER.chave, valor) }
        HudOverlayController.requestRedraw()
    }

    /** O modo noturno vale AGORA? [farolAceso] só é consultado no automático. */
    fun noturnoAtivo(farolAceso: () -> Boolean): Boolean = when (modoNoturno) {
        HudModoNoturno.DESLIGADO -> false
        HudModoNoturno.SEMPRE -> true
        HudModoNoturno.AUTOMATICO -> farolAceso()
    }

    @Volatile private var carregado = false

    /**
     * Relê as preferências mesmo que já tenham sido lidas.
     *
     * POR QUE ESTE MÉTODO PRECISA EXISTIR
     *
     * [load] é guardada por [carregado] — ela tem de ser, porque é chamada na primeira leitura de
     * QUALQUER ajuste, inclusive de dentro do desenho, e reler o arquivo a cada quadro seria
     * absurdo. Só que quem troca de motorista reescreve as preferências por baixo e depois pede
     * uma releitura: com o guarda no lugar, essa releitura não fazia nada e o HUD continuava com
     * os ajustes do motorista anterior até o app reiniciar. Este método derruba o guarda uma vez
     * e relê. É o que o [br.com.opengw.perfis.ReconciliadorDeAjustes] chama.
     */
    @Synchronized
    fun recarregar() {
        carregado = false
        load()
    }

    /** Lê as preferências para os espelhos. Idempotente; chamada na primeira leitura de qualquer um. */
    @Synchronized
    fun load() {
        if (carregado) return
        val p = prefs()
        if (p == null) {
            // Marca como carregado mesmo assim: sem preferências, tentar de novo a cada leitura
            // só repetiria a mesma exceção capturada, quadro após quadro.
            carregado = true
            return
        }
        speedSizePct =
                p.getInt(Preferencias.HUD_SPEED_SIZE_PCT.chave, SPEED_SIZE_DEFAULT)
                        .coerceIn(SPEED_SIZE_MIN, SPEED_SIZE_MAX)
        speedColor = p.getInt(Preferencias.HUD_SPEED_COLOR.chave, PRESET_BRANCO)
        sliSizePct =
                p.getInt(Preferencias.HUD_SLI_SIZE_PCT.chave, SLI_SIZE_DEFAULT)
                        .coerceIn(SLI_SIZE_MIN, SLI_SIZE_MAX)
        showSli = p.getBoolean(Preferencias.HUD_SHOW_SLI.chave, true)
        showAlerts = p.getBoolean(Preferencias.HUD_SHOW_ALERTS.chave, true)
        pesoDaFonte = arredondarPeso(p.getInt(Preferencias.HUD_FONT_WEIGHT.chave, PESO_PADRAO))
        wheelControlEnabled = p.getBoolean(Preferencias.HUD_WHEEL_CONTROL.chave, true)
        ordemPaginas = lerOrdem(p.getString(Preferencias.HUD_PAGE_ORDER.chave, null))
        paginasAtivas = lerAtivas(p.getString(Preferencias.HUD_PAGES_ENABLED.chave, null))
        coresDePagina = lerMapa(p.getString(Preferencias.HUD_PAGE_COLORS.chave, null))
        escalasDePagina =
                lerMapa(p.getString(Preferencias.HUD_PAGE_SCALES.chave, null)).mapValues {
                    it.value.coerceIn(ESCALA_MIN, ESCALA_MAX)
                }
        corRotulo = opaco(p.getInt(Preferencias.HUD_LABEL_COLOR.chave, COR_ROTULO_PADRAO))
        fonte = HudFonte.porId(p.getString(Preferencias.HUD_FONT_FAMILY.chave, null)) ?: HudFonte.PADRAO
        mostrarCabecalho = p.getBoolean(Preferencias.HUD_SHOW_HEADER.chave, true)
        mostrarPontos = p.getBoolean(Preferencias.HUD_SHOW_DOTS.chave, true)
        unidadeVelocidade = p.getBoolean(Preferencias.HUD_SHOW_SPEED_UNIT.chave, true)
        placaAEsquerda = p.getString(Preferencias.HUD_SLI_SIDE.chave, null) == LADO_ESQUERDA
        layoutsDePagina = lerLayouts(p.getString(Preferencias.HUD_PAGE_LAYOUTS.chave, null))
        titulosDePagina = lerTitulos(p.getString(Preferencias.HUD_PAGE_TITLES.chave, null))
        nomeDoTema = p.getString(Preferencias.HUD_THEME_NAME.chave, null)?.ifBlank { null }
        modoNoturno = HudModoNoturno.porId(p.getString(Preferencias.HUD_NIGHT_MODE.chave, null))
        brilhoNoturno =
                p.getInt(Preferencias.HUD_NIGHT_BRIGHTNESS.chave, BRILHO_NOTURNO_PADRAO)
                        .coerceIn(BRILHO_NOTURNO_MIN, BRILHO_NOTURNO_MAX)
        noturnoAmbar = p.getBoolean(Preferencias.HUD_NIGHT_AMBER.chave, false)
        carregado = true
    }

    // ---- Leitura e escrita das listas ---------------------------------------------------------

    private fun paginaPorNome(nome: String): HudPaginas.Pagina? =
            HudPaginas.Pagina.entries.firstOrNull { it.name == nome }

    /**
     * Ordem gravada + o que faltar, no fim.
     *
     * Um nome desconhecido (página removida numa versão futura) some; uma página que existe no
     * código mas não na preferência (versão que acrescentou uma) entra no fim, na ordem do enum.
     * Assim nenhuma atualização do app derruba a configuração nem esconde uma página nova.
     */
    private fun lerOrdem(cru: String?): List<HudPaginas.Pagina> {
        val gravada =
                cru?.split(",")
                        ?.mapNotNull { paginaPorNome(it.trim()) }
                        ?.distinct()
                        .orEmpty()
        val faltando = HudPaginas.Pagina.entries.filter { it !in gravada }
        return gravada + faltando
    }

    /** Ausente = todas ativas. Presente e vazio seria um HUD sem nada; vira todas também. */
    private fun lerAtivas(cru: String?): Set<HudPaginas.Pagina> {
        if (cru == null) return HudPaginas.Pagina.entries.toSet()
        val lidas = cru.split(",").mapNotNull { paginaPorNome(it.trim()) }.toSet()
        return lidas.ifEmpty { HudPaginas.Pagina.entries.toSet() }
    }

    private fun lerMapa(cru: String?): Map<HudPaginas.Pagina, Int> {
        if (cru.isNullOrBlank()) return emptyMap()
        val saida = mutableMapOf<HudPaginas.Pagina, Int>()
        for (parte in cru.split(",")) {
            val ig = parte.indexOf('=')
            if (ig <= 0) continue
            val pagina = paginaPorNome(parte.substring(0, ig).trim()) ?: continue
            val valor = parte.substring(ig + 1).trim().toIntOrNull() ?: continue
            saida[pagina] = valor
        }
        return saida
    }

    private fun gravarMapa(chave: Preferencia, mapa: Map<HudPaginas.Pagina, Int>) {
        prefs()?.edit {
            putString(chave.chave, mapa.entries.joinToString(",") { "${it.key.name}=${it.value}" })
        }
    }

    fun ajustarTamanhoVelocidade(valor: Int) {
        val v = valor.coerceIn(SPEED_SIZE_MIN, SPEED_SIZE_MAX)
        speedSizePct = v
        prefs()?.edit { putInt(Preferencias.HUD_SPEED_SIZE_PCT.chave, v) }
        HudOverlayController.requestRedraw()
    }

    fun ajustarCorVelocidade(cor: Int) {
        // Alfa sempre opaco: o HUD é ótico e um número translúcido some no para-brisa.
        val v = opaco(cor)
        speedColor = v
        prefs()?.edit { putInt(Preferencias.HUD_SPEED_COLOR.chave, v) }
        HudOverlayController.requestRedraw()
    }

    fun ajustarTamanhoSli(valor: Int) {
        val v = valor.coerceIn(SLI_SIZE_MIN, SLI_SIZE_MAX)
        sliSizePct = v
        prefs()?.edit { putInt(Preferencias.HUD_SLI_SIZE_PCT.chave, v) }
        HudOverlayController.requestRedraw()
    }

    fun ajustarMostrarSli(valor: Boolean) {
        showSli = valor
        prefs()?.edit { putBoolean(Preferencias.HUD_SHOW_SLI.chave, valor) }
        HudOverlayController.requestRedraw()
    }

    fun ajustarMostrarAlertas(valor: Boolean) {
        showAlerts = valor
        prefs()?.edit { putBoolean(Preferencias.HUD_SHOW_ALERTS.chave, valor) }
        HudOverlayController.requestRedraw()
    }

    /**
     * Arredonda [valor] para o múltiplo de [PESO_PASSO] mais próximo, dentro da faixa.
     *
     * O controle deslizante da tela entrega um `Float` contínuo, e `Typeface.create` aceita
     * qualquer inteiro de 1 a 1000 — mas a família que o Android tem instalada só existe em alguns
     * pesos, então 643 e 700 desenham igual. Arredondar de cem em cem faz o rótulo ("Negrito")
     * dizer a verdade sobre o que está na tela, em vez de anunciar um número que a fonte ignorou.
     */
    fun arredondarPeso(valor: Int): Int {
        val presos = valor.coerceIn(PESO_MIN, PESO_MAX)
        val passos = Math.round((presos - PESO_MIN).toFloat() / PESO_PASSO)
        return (PESO_MIN + passos * PESO_PASSO).coerceIn(PESO_MIN, PESO_MAX)
    }

    fun ajustarPesoDaFonte(valor: Int) {
        val v = arredondarPeso(valor)
        if (v == pesoDaFonte) return
        pesoDaFonte = v
        prefs()?.edit { putInt(Preferencias.HUD_FONT_WEIGHT.chave, v) }
        HudOverlayController.requestRedraw()
    }

    /** Nome do peso [peso], na nomenclatura que o usuário reconhece de um editor de texto. */
    fun descreverPeso(peso: Int = pesoDaFonte): String =
            when (arredondarPeso(peso)) {
                200 -> "Fina"
                300 -> "Leve"
                400 -> "Normal"
                500 -> "Média"
                600 -> "Seminegrito"
                700 -> "Negrito"
                800 -> "Extranegrito"
                else -> "Preta"
            }

    fun ajustarControleVolante(valor: Boolean) {
        wheelControlEnabled = valor
        prefs()?.edit { putBoolean(Preferencias.HUD_WHEEL_CONTROL.chave, valor) }
        if (!valor) ControleDoHud.encerrar("controle desligado em Telas")
        // Sem repintar: o que muda é quem recebe a tecla, não o que está desenhado.
    }

    /**
     * Anda a página [pagina] em [passo] posições na ordem. Nas pontas não faz nada — embrulhar a
     * lista ao arrastar o primeiro item para cima é o tipo de surpresa que faz o usuário perder de
     * vista o que mexeu.
     */
    fun moverPagina(pagina: HudPaginas.Pagina, passo: Int) {
        val lista = ordemPaginas.toMutableList()
        val de = lista.indexOf(pagina)
        if (de < 0) return
        val para = de + passo
        if (para !in lista.indices) return
        lista.removeAt(de)
        lista.add(para, pagina)
        ordemPaginas = lista
        prefs()?.edit {
            putString(Preferencias.HUD_PAGE_ORDER.chave, lista.joinToString(",") { it.name })
        }
        HudPaginas.reconciliar()
    }

    /**
     * Liga ou desliga uma página no rodízio.
     *
     * A última ativa não pode ser desligada: um HUD sem página nenhuma não tem o que desenhar e
     * não teria como voltar pelo volante. A tela chama [podeDesativar] antes para deixar o
     * interruptor cinza em vez de recusar o toque sem explicação.
     */
    fun ajustarPaginaAtiva(pagina: HudPaginas.Pagina, ativa: Boolean) {
        if (!ativa && !podeDesativar(pagina)) return
        val novo = if (ativa) paginasAtivas + pagina else paginasAtivas - pagina
        paginasAtivas = novo
        prefs()?.edit {
            putString(
                    Preferencias.HUD_PAGES_ENABLED.chave,
                    novo.joinToString(",") { it.name }
            )
        }
        HudPaginas.reconciliar()
    }

    fun podeDesativar(pagina: HudPaginas.Pagina): Boolean =
            !(pagina in paginasAtivas && paginasAtivas.size <= 1)

    /**
     * Cor do valor principal da página.
     *
     * VELOCIDADE não tem entrada própria de propósito: a cor dela já é [speedColor], que tem
     * controle dedicado na tela. Duas fontes para a mesma cor seria uma contradizendo a outra.
     */
    fun corDaPagina(pagina: HudPaginas.Pagina): Int =
            if (pagina == HudPaginas.Pagina.VELOCIDADE) speedColor
            else coresDePagina[pagina] ?: PRESET_BRANCO

    fun ajustarCorDaPagina(pagina: HudPaginas.Pagina, cor: Int) {
        if (pagina == HudPaginas.Pagina.VELOCIDADE) {
            ajustarCorVelocidade(cor)
            return
        }
        val v = opaco(cor)
        coresDePagina = coresDePagina + (pagina to v)
        gravarMapa(Preferencias.HUD_PAGE_COLORS, coresDePagina)
        HudOverlayController.requestRedraw()
    }

    /** Multiplicador do corpo de letra da página. 1.0 é o desenho original. */
    fun escalaDaPagina(pagina: HudPaginas.Pagina): Float =
            (escalasDePagina[pagina] ?: ESCALA_PADRAO).coerceIn(ESCALA_MIN, ESCALA_MAX) / 100f

    fun escalaDaPaginaPct(pagina: HudPaginas.Pagina): Int =
            (escalasDePagina[pagina] ?: ESCALA_PADRAO).coerceIn(ESCALA_MIN, ESCALA_MAX)

    fun ajustarEscalaDaPagina(pagina: HudPaginas.Pagina, pct: Int) {
        escalasDePagina = escalasDePagina + (pagina to pct.coerceIn(ESCALA_MIN, ESCALA_MAX))
        gravarMapa(Preferencias.HUD_PAGE_SCALES, escalasDePagina)
        HudOverlayController.requestRedraw()
    }

    /** Volta ordem, ativas, cores e escalas ao estado de fábrica. Não toca em SLI nem alertas. */
    fun restaurarPaginas() {
        ordemPaginas = HudPaginas.Pagina.entries.toList()
        paginasAtivas = HudPaginas.Pagina.entries.toSet()
        coresDePagina = emptyMap()
        escalasDePagina = emptyMap()
        prefs()?.edit {
            remove(Preferencias.HUD_PAGE_ORDER.chave)
            remove(Preferencias.HUD_PAGES_ENABLED.chave)
            remove(Preferencias.HUD_PAGE_COLORS.chave)
            remove(Preferencias.HUD_PAGE_SCALES.chave)
        }
        HudPaginas.reconciliar()
    }

    // ---- Tema do HUD ---------------------------------------------------------------------------

    private const val LADO_ESQUERDA = "esquerda"

    /** O nome que a página mostra no cabeçalho e na lista de Telas. */
    fun tituloDaPagina(pagina: HudPaginas.Pagina): String = titulosDePagina[pagina] ?: pagina.titulo

    /** Os campos da página de números, ou null para velocidade e mídia (desenho próprio). */
    fun layoutDaPagina(pagina: HudPaginas.Pagina): HudLayout? =
            layoutsDePagina[pagina] ?: HudLayout.padrao(pagina)

    /** `PAGINA=A1+A2/B1+B2+B3,...` — só nomes de enum, então vírgula e barra nunca colidem. */
    private fun lerLayouts(cru: String?): Map<HudPaginas.Pagina, HudLayout> {
        if (cru.isNullOrBlank()) return emptyMap()
        val saida = mutableMapOf<HudPaginas.Pagina, HudLayout>()
        for (parte in cru.split(",")) {
            val ig = parte.indexOf('=')
            if (ig <= 0) continue
            val pagina = paginaPorNome(parte.substring(0, ig).trim()) ?: continue
            HudLayout.deTexto(pagina, parte.substring(ig + 1))?.let { saida[pagina] = it }
        }
        return saida
    }

    /** Nome livre pode ter vírgula e igual: guardado em JSON, e não no formato `A=B,C=D`. */
    private fun lerTitulos(cru: String?): Map<HudPaginas.Pagina, String> {
        if (cru.isNullOrBlank()) return emptyMap()
        val obj =
                runCatching { kotlinx.serialization.json.Json.parseToJsonElement(cru) }.getOrNull()
                        as? kotlinx.serialization.json.JsonObject ?: return emptyMap()
        val saida = mutableMapOf<HudPaginas.Pagina, String>()
        for ((nome, valor) in obj) {
            val pagina = paginaPorNome(nome) ?: continue
            val texto = (valor as? kotlinx.serialization.json.JsonPrimitive)?.content?.trim().orEmpty()
            if (texto.isNotEmpty()) saida[pagina] = texto.take(HudTema.TITULO_MAX)
        }
        return saida
    }

    /**
     * Aplica um tema inteiro, numa gravação só.
     *
     * Escreve nas mesmas preferências que os controles da tela de Telas: depois de aplicado, mexer
     * num controle ajusta o tema aplicado — não existe um "modo tema" que os controles não
     * alcançam. Não toca no que é do carro (camada ligada, tela do HUD, controle pelo volante).
     */
    @Synchronized
    fun aplicarTema(tema: HudTema) {
        load()
        val ordem = tema.paginas.map { it.pagina }
        val ativas = tema.paginas.filter { it.ativa }.map { it.pagina }.toSet()
        val cores = tema.paginas.mapNotNull { p -> p.cor?.let { p.pagina to opaco(it) } }
                .filter { it.first != HudPaginas.Pagina.VELOCIDADE }.toMap()
        val escalas = tema.paginas.mapNotNull { p -> p.escala?.let { p.pagina to it.coerceIn(ESCALA_MIN, ESCALA_MAX) } }
                .filter { it.first != HudPaginas.Pagina.VELOCIDADE }.toMap()
        val layouts = tema.paginas.mapNotNull { p -> p.layout?.let { p.pagina to it } }
                .filter { HudLayout.aceitaCampos(it.first) }.toMap()
        val titulos = tema.paginas.mapNotNull { p -> p.titulo?.let { p.pagina to it } }.toMap()

        speedSizePct = tema.tamanhoVelocidade.coerceIn(SPEED_SIZE_MIN, SPEED_SIZE_MAX)
        speedColor = opaco(tema.corVelocidade)
        sliSizePct = tema.tamanhoPlaca.coerceIn(SLI_SIZE_MIN, SLI_SIZE_MAX)
        showSli = tema.mostrarPlaca
        showAlerts = tema.alertas
        pesoDaFonte = arredondarPeso(tema.peso)
        ordemPaginas = lerOrdem(ordem.joinToString(",") { it.name })
        paginasAtivas = ativas.ifEmpty { HudPaginas.Pagina.entries.toSet() }
        coresDePagina = cores
        escalasDePagina = escalas
        corRotulo = opaco(tema.corRotulo)
        fonte = tema.fonte
        mostrarCabecalho = tema.cabecalho
        mostrarPontos = tema.pontos
        unidadeVelocidade = tema.unidadeVelocidade
        placaAEsquerda = tema.placaAEsquerda
        layoutsDePagina = layouts
        titulosDePagina = titulos
        nomeDoTema = tema.nome.trim().ifBlank { null }

        prefs()?.edit {
            putInt(Preferencias.HUD_SPEED_SIZE_PCT.chave, speedSizePct)
            putInt(Preferencias.HUD_SPEED_COLOR.chave, speedColor)
            putInt(Preferencias.HUD_SLI_SIZE_PCT.chave, sliSizePct)
            putBoolean(Preferencias.HUD_SHOW_SLI.chave, showSli)
            putBoolean(Preferencias.HUD_SHOW_ALERTS.chave, showAlerts)
            putInt(Preferencias.HUD_FONT_WEIGHT.chave, pesoDaFonte)
            putString(Preferencias.HUD_PAGE_ORDER.chave, ordemPaginas.joinToString(",") { it.name })
            putString(Preferencias.HUD_PAGES_ENABLED.chave, paginasAtivas.joinToString(",") { it.name })
            putString(Preferencias.HUD_PAGE_COLORS.chave, cores.entries.joinToString(",") { "${it.key.name}=${it.value}" })
            putString(Preferencias.HUD_PAGE_SCALES.chave, escalas.entries.joinToString(",") { "${it.key.name}=${it.value}" })
            putInt(Preferencias.HUD_LABEL_COLOR.chave, corRotulo)
            putString(Preferencias.HUD_FONT_FAMILY.chave, fonte.id)
            putBoolean(Preferencias.HUD_SHOW_HEADER.chave, mostrarCabecalho)
            putBoolean(Preferencias.HUD_SHOW_DOTS.chave, mostrarPontos)
            putBoolean(Preferencias.HUD_SHOW_SPEED_UNIT.chave, unidadeVelocidade)
            putString(Preferencias.HUD_SLI_SIDE.chave, if (placaAEsquerda) LADO_ESQUERDA else "direita")
            putString(
                    Preferencias.HUD_PAGE_LAYOUTS.chave,
                    layouts.entries.joinToString(",") { "${it.key.name}=${it.value.paraTexto()}" }
            )
            putString(
                    Preferencias.HUD_PAGE_TITLES.chave,
                    kotlinx.serialization.json.JsonObject(
                                    titulos.entries.associate {
                                        it.key.name to kotlinx.serialization.json.JsonPrimitive(it.value)
                                    }
                            )
                            .toString()
            )
            if (nomeDoTema != null) putString(Preferencias.HUD_THEME_NAME.chave, nomeDoTema)
            else remove(Preferencias.HUD_THEME_NAME.chave)
        }
        HudPaginas.reconciliar()
    }

    /** O HUD como está agora, como tema — é o que `GET /hud/tema` devolve ao instalador. */
    fun temaAtual(): HudTema {
        load()
        return HudTema(
                nome = nomeDoTema ?: "",
                corRotulo = corRotulo,
                fonte = fonte,
                peso = pesoDaFonte,
                cabecalho = mostrarCabecalho,
                pontos = mostrarPontos,
                tamanhoVelocidade = speedSizePct,
                corVelocidade = speedColor,
                unidadeVelocidade = unidadeVelocidade,
                mostrarPlaca = showSli,
                tamanhoPlaca = sliSizePct,
                placaAEsquerda = placaAEsquerda,
                alertas = showAlerts,
                paginas =
                        ordemPaginas.map { p ->
                            HudTema.Pagina(
                                    pagina = p,
                                    ativa = p in paginasAtivas,
                                    cor = coresDePagina[p],
                                    escala = escalasDePagina[p]?.takeIf { it != ESCALA_PADRAO },
                                    titulo = titulosDePagina[p],
                                    layout = layoutsDePagina[p]
                            )
                        }
        )
    }

    /**
     * Tira o tema: todo ajuste de aparência volta ao de fábrica, inclusive os que a tela de Telas
     * mexe (velocidade, placa, espessura, páginas). O controle pelo volante e a camada ligada
     * ficam como estão — são do carro, não do tema.
     */
    fun restaurarPadrao() = aplicarTema(HudTema.padrao())

    /** Nome curto do preset quando a cor atual é um deles; senão o hexadecimal. */
    fun descreverCor(cor: Int = speedColor): String =
            when (cor) {
                PRESET_VERMELHO -> "Vermelho"
                PRESET_BRANCO -> "Branco"
                PRESET_AZUL -> "Azul"
                else -> String.format("#%06X", cor and 0xFFFFFF)
            }
}

/** Quando o HUD escurece (Alpha 9.5). O [id] é o que vai para a preferência e para o instalador. */
enum class HudModoNoturno(val id: String, val rotulo: String) {
    DESLIGADO("desligado", "Desligado"),
    AUTOMATICO("automatico", "Com o farol"),
    SEMPRE("sempre", "Sempre");

    companion object {
        fun porId(id: String?): HudModoNoturno = entries.firstOrNull { it.id == id } ?: DESLIGADO
    }
}
