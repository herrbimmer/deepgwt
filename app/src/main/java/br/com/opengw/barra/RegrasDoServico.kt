package br.com.opengw.barra

import kotlin.math.max

/** Um retângulo `[esquerda, topo, direita, base]` em px da janela; vazio = nada tocável. */
data class Retangulo(val esquerda: Int, val topo: Int, val direita: Int, val base: Int) {
    val vazio: Boolean get() = direita <= esquerda || base <= topo

    companion object {
        val VAZIO = Retangulo(0, 0, 0, 0)
    }
}

/** O que fazer com o cartão quando o carro mexe no volume (serviço casos 31–33). */
internal class CartaoPeloCarro {
    @Volatile
    private var ultimoVolume: String? = null

    /** Caso 31: a semente (o motor entrega o valor atual a quem acabou de se registrar). */
    fun semear(volume: String?) {
        ultimoVolume = volume
    }

    /**
     * Caso 32: este aviso pede o cartão? (Antes das guardas do caso 33.) [valor] é o valor do aviso.
     */
    fun avisoPedeCartao(chave: String, valor: String?): Boolean = when (chave) {
        RegrasDoServico.VOLUME -> {
            val anterior = ultimoVolume
            ultimoVolume = valor
            !valor.isNullOrEmpty() && anterior != null && anterior != valor
        }
        RegrasDoServico.ACAO_DO_VOLUME -> {
            val v = valor?.trim().orEmpty()
            v.isNotEmpty() && v != "0"
        }
        else -> false
    }
}

/** As regras puras do serviço da barra (casos 9, 13, 16, 29, 30, 33, 37, 27, 28). Sem Android. */
internal object RegrasDoServico {

    const val VOLUME = "sys.settings.audio.media_volume"
    const val ACAO_DO_VOLUME = "sys.settings.audio.volume_adjust_action"
    const val FAIXA_DO_VOLUME = "sys.settings.audio.media_volume_range"
    const val AA_APP = "com.ts.androidauto.app"

    /** Buraco à esquerda com o Android Auto no display 0 (o ícone do trilho fica sob a barra). */
    const val BURACO_DO_AA_PX = 100

    // ── Regiões tocáveis (caso 9) ──────────────────────────────────────────────────────

    /** `esquerda` da região: 100 px com o AA no display 0, senão o recuo (≥ 0). */
    fun esquerdaDaRegiao(aaNoDisplay0: Boolean, recuoEsquerdoPx: Int): Int =
        if (aaNoDisplay0) BURACO_DO_AA_PX else max(recuoEsquerdoPx, 0)

    /** Janela da barra. */
    fun regiaoDaBarra(
        visivel: Boolean,
        painelExpandido: Boolean,
        larguraPx: Int,
        alturaDaJanelaPx: Int,
        densidade: Float,
        recuoEsquerdoPx: Int,
        aaNoDisplay0: Boolean,
    ): Retangulo {
        if (painelExpandido) return Retangulo.VAZIO
        val esquerda = esquerdaDaRegiao(aaNoDisplay0, recuoEsquerdoPx)
        return if (visivel) {
            val piso = (80 * densidade).toInt()
            Retangulo(esquerda, alturaDaJanelaPx - max(piso, alturaDaJanelaPx), larguraPx, alturaDaJanelaPx)
        } else {
            Retangulo(esquerda, 0, larguraPx, (Barra.FAIXA_DE_GATILHO_DP * densidade).toInt())
        }
    }

    /** Janela de menus: inteira quando algo pede toque, senão vazia. */
    fun regiaoDosMenus(algoPedeToque: Boolean, larguraPx: Int, alturaPx: Int): Retangulo =
        if (algoPedeToque) Retangulo(0, 0, larguraPx, alturaPx) else Retangulo.VAZIO

    // ── Janela de menus (casos 13–14) ──────────────────────────────────────────────────

    /** Caso 13: o painel expandido NÃO entra (ele é uma atividade). */
    fun mostrarMenus(
        menuDeApps: Boolean,
        menuDeConducao: Boolean,
        menuDeClima: Boolean,
        gaveta: Boolean,
        cartao: FuncaoDoAjuste?,
        escolhaDeModo: EscolhaDeModo?,
    ): Boolean = menuDeApps || menuDeConducao || menuDeClima || gaveta || cartao != null || escolhaDeModo != null

    /** Caso 14: ao acender, só a coluna estava à mostra (escolha de modo ativa e nada pedindo toque)? */
    fun soAColuna(escolhaDeModo: EscolhaDeModo?, algoPedeToque: Boolean): Boolean =
        escolhaDeModo != null && !algoPedeToque

    const val ATRASO_DE_FECHAR_A_COLUNA_MS = 420L

    // ── Recuo dos apps (caso 16) ────────────────────────────────────────────────────────

    /** Recuo (dp) e deslocamento (dp) de um app. */
    data class RecuoDoApp(val overscanDp: Int, val deslocamentoDp: Int)

    fun recuoDoApp(
        pacote: String?,
        alturaDp: Int,
        manual: RecuoManual?,
        medidos: Map<String, Int>,
        explicitoDp: Int?,
    ): RecuoDoApp {
        val padrao = ContasDaBarra.overscanDaBarraDp(alturaDp, explicitoDp)
        if (manual != null) {
            return RecuoDoApp(
                ContasDaBarra.limitarOverscanDp(manual.overscanDp, alturaDp, manual.deslocamentoDp),
                manual.deslocamentoDp,
            )
        }
        val medido = pacote?.let { medidos[it] }
        if (medido != null) return RecuoDoApp(medido.coerceIn(0, max(padrao, 0)), 0)
        return RecuoDoApp(padrao, 0)
    }

    /** Limites dos extras do `UPDATE_BAR_POSITION` (S-D3). */
    const val DESLOCAMENTO_MINIMO_DP = -150
    const val DESLOCAMENTO_MAXIMO_DP = 150
    const val EXTRA_AUSENTE_OVERSCAN = -1
    const val EXTRA_AUSENTE_DESLOCAMENTO = -101

    /**
     * O receptor de posição com extras (caso 16, S-D3 corrigido): o recuo passa pelo limite do
     * caso 6 e o deslocamento fica em −150..150. Sem nenhum extra → null (só reaplicar).
     */
    fun recuoPeloReceptor(
        overscanExtra: Int,
        deslocamentoExtra: Int,
        atual: RecuoDoApp,
        alturaDp: Int,
    ): RecuoDoApp? {
        val temOverscan = overscanExtra != EXTRA_AUSENTE_OVERSCAN
        val temDeslocamento = deslocamentoExtra != EXTRA_AUSENTE_DESLOCAMENTO
        if (!temOverscan && !temDeslocamento) return null
        val desl = (if (temDeslocamento) deslocamentoExtra else atual.deslocamentoDp)
            .coerceIn(DESLOCAMENTO_MINIMO_DP, DESLOCAMENTO_MAXIMO_DP)
        val over = if (temOverscan) overscanExtra else atual.overscanDp
        return RecuoDoApp(ContasDaBarra.limitarOverscanDp(over, alturaDp, desl), desl)
    }

    // ── Foco externo e painel (casos 29–30) ────────────────────────────────────────────

    /** Pacotes que não tiram o painel expandido da frente. */
    val PASSIVOS: Set<String> = setOf(
        "com.android.systemui", "com.beantechs.launcher", "com.beantechs.mediacenter",
        "com.beantechs.mediacenter.h5.core", "com.beantechs.vehiclecenter",
        "com.ts.carplay", "com.ts.carplay.app", "com.ts.androidauto", "com.ts.androidauto.app",
        "com.ts.androidauto.projectionservice",
    )

    /** Caso 29: restaurar a barra por este foco externo? */
    fun restaurarPorFocoExterno(
        pacote: String?,
        proprio: String,
        visivel: Boolean,
        painelExpandido: Boolean,
        supressaoAtiva: Boolean,
    ): Boolean {
        val p = pacote?.trim().orEmpty()
        if (p.isEmpty() || p == proprio) return false
        if (painelExpandido && supressaoAtiva) return false
        if (painelExpandido && p in PASSIVOS) return false
        if (!visivel && !painelExpandido) return false
        return true
    }

    /** Supressão por controle do painel: ativa só com o painel expandido e `agora ≤ fim`. */
    fun supressaoAtiva(painelExpandido: Boolean, agora: Long, fim: Long): Boolean = painelExpandido && agora <= fim

    /** Caso 29, exceção: o cartão do carro fica quando é o próprio pop-up de volume chegando. */
    fun manterCartaoDoCarro(pacote: String?, cartaoAbertoPeloCarro: Boolean, cartao: FuncaoDoAjuste?): Boolean =
        cartaoAbertoPeloCarro && cartao == FuncaoDoAjuste.VOLUME &&
            pacote.orEmpty().contains("systemui", ignoreCase = true)

    /** Caso 30: reabrir o painel quando a projeção vai ao painel de instrumentos? */
    fun reabrirAposProjecao(preferencia: Boolean, visivel: Boolean, painelExpandido: Boolean): Boolean =
        preferencia && (visivel || painelExpandido)

    const val ATRASO_DE_REABRIR_MS = 650L
    const val SUPRESSAO_POR_CONTROLE_MS = 1_500L

    // ── Cartão do carro (caso 33) ───────────────────────────────────────────────────────

    /** Caso 33: as guardas para abrir o cartão pelo carro. */
    fun podeAbrirCartaoPeloCarro(
        cartaoPeloCarro: Boolean,
        arrastandoCartao: Boolean,
        cartao: FuncaoDoAjuste?,
        cartaoAbertoPeloCarro: Boolean,
        painelExpandido: Boolean,
    ): Boolean = cartaoPeloCarro && !arrastandoCartao && !(cartao != null && !cartaoAbertoPeloCarro) && !painelExpandido

    // ── Projeção na frente (caso 37) ───────────────────────────────────────────────────

    /** "Na projeção": um dos cinco pacotes, ou um nome derivado que o lançador reconhece. */
    fun naProjecao(topo: String?, reconhecida: String?): Boolean =
        (topo != null && topo in RegrasDaMidia.PACOTES_DE_PROJECAO) || reconhecida != null

    /** O que fazer com a visibilidade nesta volta (caso 37). */
    enum class AcaoDaProjecao { NADA, ESCONDER, MOSTRAR }

    /**
     * Caso 37. [escondidaPorProjecao] é a marca; devolve a ação e a marca nova.
     */
    fun saidaDaFrente(
        preferencia: Boolean,
        naProjecao: Boolean,
        escondidaPorProjecao: Boolean,
        visivel: Boolean,
    ): Pair<AcaoDaProjecao, Boolean> = when {
        !preferencia -> if (escondidaPorProjecao) AcaoDaProjecao.MOSTRAR to false else AcaoDaProjecao.NADA to false
        naProjecao && !escondidaPorProjecao -> if (!visivel) AcaoDaProjecao.NADA to false else AcaoDaProjecao.ESCONDER to true
        naProjecao -> AcaoDaProjecao.NADA to true
        escondidaPorProjecao -> AcaoDaProjecao.MOSTRAR to false
        else -> AcaoDaProjecao.NADA to false
    }

    // ── Indicador de CPU/RAM (casos 27–28) ─────────────────────────────────────────────

    /** Caso 27: aparece com o indicador ligado e o painel fechado. */
    fun indicadorAparece(indicadorLigado: Boolean, painelExpandido: Boolean) = indicadorLigado && !painelExpandido

    /** Caso 27: 0 topo-início, 1 topo-fim, 2 baixo-início, 3 e outros baixo-fim. */
    enum class Canto { TOPO_INICIO, TOPO_FIM, BAIXO_INICIO, BAIXO_FIM }

    fun canto(valor: Int): Canto = when (valor) {
        0 -> Canto.TOPO_INICIO
        1 -> Canto.TOPO_FIM
        2 -> Canto.BAIXO_INICIO
        else -> Canto.BAIXO_FIM
    }

    /** O texto do indicador; `--` para valor ausente (dois espaços entre as partes). */
    fun textoDoIndicador(cpu: Int?, ram: Int?): String = "CPU ${cpu?.let { "$it%" } ?: "--"}  RAM ${ram?.let { "$it%" } ?: "--"}"
}

/** Caso 28: o amostrador de CPU e RAM, sobre o texto dos arquivos. */
internal class AmostradorDeRecursos {
    private var totalAnterior: Long? = null
    private var ociosoAnterior: Long? = null

    fun zerar() {
        totalAnterior = null
        ociosoAnterior = null
    }

    /** CPU em % a partir do texto de `/proc/stat`; a primeira amostra só aquece a base. */
    fun cpu(procStat: String?): Int? {
        val linha = procStat?.lineSequence()?.firstOrNull { it.startsWith("cpu ") } ?: return null
        val numeros = linha.removePrefix("cpu ").trim().split(Regex("\\s+")).mapNotNull { it.toLongOrNull() }
        if (numeros.size < 4) return null
        val total = numeros.sum()
        val ocioso = numeros[3] + (numeros.getOrNull(4) ?: 0L)
        val tAnt = totalAnterior
        val oAnt = ociosoAnterior
        totalAnterior = total
        ociosoAnterior = ocioso
        if (tAnt == null || oAnt == null) return null
        val dTotal = total - tAnt
        val dOcioso = ocioso - oAnt
        if (dTotal <= 0 || dOcioso < 0) return null
        return ((dTotal - dOcioso).toDouble() / dTotal * 100).toInt().coerceIn(0, 100)
    }

    companion object {
        /** RAM em % a partir do texto de `/proc/meminfo`. */
        fun ram(meminfo: String?): Int? {
            if (meminfo == null) return null
            fun valor(nome: String): Long? = meminfo.lineSequence()
                .firstOrNull { it.startsWith("$nome:") }
                ?.let { Regex("\\d+").find(it)?.value?.toLongOrNull() }
            val total = valor("MemTotal") ?: return null
            val disponivel = valor("MemAvailable") ?: return null
            if (total <= 0) return null
            return ((total - disponivel).toDouble() / total * 100).toInt().coerceIn(0, 100)
        }
    }
}
