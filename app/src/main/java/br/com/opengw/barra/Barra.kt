package br.com.opengw.barra

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import br.com.opengw.nucleo.OpenGw
import br.com.opengw.nucleo.Preferencias

/** Ponto de entrada do módulo. Tudo volta na hora; chamar na thread principal salvo indicação. */
object Barra {
    const val ALTURA_PADRAO_DP = 60
    const val ALTURA_MINIMA_DP = 40
    const val ALTURA_MAXIMA_DP = 140

    /** Faixa que continua na tela com a barra escondida e recebe o deslize de volta. */
    const val FAIXA_DE_GATILHO_DP = 40

    internal const val TAG = "OpenGW.Barra"

    /** Log que não derruba os testes da JVM (lá o `Log` do Android não existe). */
    internal fun registrarNoLog(mensagem: String) {
        runCatching { Log.i(TAG, mensagem) }
    }

    @Volatile
    internal var integracoes: IntegracoesDaBarra = IntegracoesDaBarra.VAZIAS
        private set

    /** Liga as dependências de outros módulos. A sala suja chama uma vez, antes de tudo. */
    @JvmStatic
    fun configurar(integracoes: IntegracoesDaBarra) {
        this.integracoes = integracoes
    }

    /**
     * A classe do serviço que [mostrar]/[parar] usam. Padrão: o nosso. Com a D-1 (nome antigo como
     * subclasse), a sala suja pode apontar para a subclasse; nunca rodam dois (o segundo para).
     */
    @JvmStatic
    @Volatile
    var classeDoServico: Class<out ServicoDaBarra> = ServicoDaBarra::class.java

    /** A atividade do painel expandido aberta pelo serviço (idem). */
    @JvmStatic
    @Volatile
    var classeDaAtividadeDoPainel: Class<out AtividadeDoPainel> = AtividadeDoPainel::class.java

    /**
     * Sobe o serviço da barra, ou reafirma a barra na tela se ele já está de pé (serviço §A).
     * Não olha a preferência `enable_persistent_bottom_bar` (quem chama decide). Sem permissão de
     * sobreposição: não sobe e devolve false.
     */
    @JvmStatic
    fun mostrar(contexto: Context): Boolean {
        if (!Settings.canDrawOverlays(contexto)) return false
        return runCatching { contexto.startService(Intent(contexto, classeDoServico)) != null }
            .onFailure { registrarNoLog("serviço da barra não subiu: ${it.message}") }
            .getOrDefault(false)
    }

    /** Para o serviço: tira as janelas, devolve `wm overscan` a zero (serviço §P). */
    @JvmStatic
    fun parar(contexto: Context) {
        runCatching { contexto.stopService(Intent(contexto, classeDoServico)) }
        ServicoDaBarra.instancia?.let { s ->
            if (s.javaClass != classeDoServico) runCatching { s.stopSelf() }
        }
    }

    /** O serviço está de pé agora? */
    @JvmStatic
    val ativa: Boolean
        get() = ServicoDaBarra.instancia != null

    // ── Foco externo e projeção (serviço §H) ───────────────────────────────────────────

    /** Fim da supressão da restauração por foco (controles de mídia do painel). */
    @Volatile
    private var fimDaSupressao = 0L

    /** O painel chama a cada controle de mídia: 1,5 s sem restauração por foco externo. */
    @JvmStatic
    fun suspenderRestauracaoPorFoco() {
        fimDaSupressao = ThreadPrincipal.agoraMs() + RegrasDoServico.SUPRESSAO_POR_CONTROLE_MS
    }

    /** Uma janela de outro app veio à frente (vem do serviço de acessibilidade, via lançador). */
    @JvmStatic
    fun aoFocoExterno(pacote: String?, motivo: String) {
        ThreadPrincipal.executar {
            val s = EstadoDaBarra
            val proprio = ServicoDaBarra.instancia?.packageName ?: runCatching { OpenGw.contexto.packageName }.getOrDefault("")
            val supressao = RegrasDoServico.supressaoAtiva(s.painelExpandido, ThreadPrincipal.agoraMs(), fimDaSupressao)
            if (!RegrasDoServico.restaurarPorFocoExterno(pacote, proprio, s.visivel, s.painelExpandido, supressao)) return@executar
            val manterCartao = RegrasDoServico.manterCartaoDoCarro(pacote, s.cartaoAbertoPeloCarro, s.cartao)
            s.painelExpandido = false
            s.visivel = true
            s.fecharMenus()
            s.ajusteRapido = null
            s.escolhaDeModo = null
            if (!manterCartao) s.fecharCartao()
            registrarNoLog("restaurada por foco externo: $pacote ($motivo)")
            ServicoDaBarra.instancia?.pedirLayoutDasJanelas()
        }
    }

    private val reabrirPainel = Runnable {
        val s = EstadoDaBarra
        if (!s.visivel && !s.painelExpandido) return@Runnable
        val jaExpandido = s.painelExpandido
        s.visivel = true
        s.painelExpandido = true
        s.fecharTudo()
        // Já expandido: o serviço não vê mudança; abre aqui (singleTop, sem duplicar).
        if (jaExpandido) ServicoDaBarra.instancia?.abrirAtividadeDoPainel()
    }

    /** Uma projeção acabou de ir para o painel de instrumentos (display 3): reabrir o painel? */
    @JvmStatic
    fun aoProjecaoIrParaOPainel(motivo: String) {
        ThreadPrincipal.executar {
            val pref = PreferenciasDaBarra.fonte()?.booleano(Preferencias.CLUSTER_PROJECTION_OPENS_DASHBOARD.chave, true) ?: true
            val s = EstadoDaBarra
            if (!RegrasDoServico.reabrirAposProjecao(pref, s.visivel, s.painelExpandido)) return@executar
            registrarNoLog("projeção foi ao painel ($motivo): reabrindo o painel em 650 ms")
            ThreadPrincipal.cancelar(reabrirPainel)
            ThreadPrincipal.postarComAtraso(RegrasDoServico.ATRASO_DE_REABRIR_MS, reabrirPainel)
        }
    }

    internal fun cancelarReaberturaPendente() {
        ThreadPrincipal.cancelar(reabrirPainel)
    }

    /** Relê as preferências para o estado (perfil trocado, instalador, nível de licença). */
    @JvmStatic
    fun recarregarPreferencias() {
        val fonte = PreferenciasDaBarra.fonte()
        if (fonte == null) {
            registrarNoLog("preferências indisponíveis; estado mantido")
            return
        }
        val licenca = runCatching { integracoes.licenca.indicadorDeRecursosLiberado() }.getOrDefault(false)
        PreferenciasDaBarra.aplicar(PreferenciasDaBarra.lerEspelho(fonte, licenca))
    }

    /**
     * A classe do ouvinte de notificações que está (ou deve estar) liberada no sistema. Padrão: o
     * nosso. Na troca, se a sala suja mantiver o nome antigo como subclasse (D-1), aponta para ela
     * antes de subir a barra: é esse nome que está gravado em `enabled_notification_listeners`.
     */
    @JvmStatic
    @Volatile
    var classeDoOuvinteDeNotificacoes: Class<out android.service.notification.NotificationListenerService> =
        OuvinteDeNotificacoesDaBarra::class.java

    /** Componente do ouvinte de notificações da barra, para quem precisa liberá-lo no sistema. */
    @JvmStatic
    fun componenteDoOuvinteDeNotificacoes(contexto: android.content.Context): android.content.ComponentName =
        android.content.ComponentName(contexto.packageName, classeDoOuvinteDeNotificacoes.name)

    // Contas puras usadas também pelo lançador (estado §C).

    @JvmStatic
    fun overscanDaBarraDp(alturaDp: Int, explicitoDp: Int?): Int =
        ContasDaBarra.overscanDaBarraDp(alturaDp, explicitoDp)

    @JvmStatic
    @JvmOverloads
    fun limitarOverscanDp(overscanDp: Int, alturaDp: Int, deslocamentoDp: Int = 0): Int =
        ContasDaBarra.limitarOverscanDp(overscanDp, alturaDp, deslocamentoDp)

    @JvmStatic
    fun overscanEmPx(overscanDp: Int, densidade: Float): Int = ContasDaBarra.overscanEmPx(overscanDp, densidade)
}
