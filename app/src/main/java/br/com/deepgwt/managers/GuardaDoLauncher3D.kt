// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.managers

import br.com.deepgwt.nucleo.Shell
import br.com.deepgwt.nucleo.DeepGwt
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import br.com.deepgwt.nucleo.Preferencias
/**
 * O topo da tela principal lido do `am stack list` — só o que a guarda do launcher precisa.
 *
 * Os dois formatos que interessam têm o mesmo esqueleto: Android 9 (a central) escreve
 * `Stack id=…`, o 10+ (o emulador) escreve `RootTask id=…`; os dois trazem `displayId=`, uma linha
 * `configuration=` com `mActivityType=home|standard|…` e as tasks com `visible=` e `topActivity=`.
 * A lista vem de cima para baixo em cada tela, então a primeira pilha visível da tela 0 é a da frente.
 */
internal object TopoDaTelaPrincipal {

    data class Topo(val pacote: String, val inicio: Boolean)

    private val CABECALHO = Regex("""^\s*(?:Stack|RootTask) id=\d+\b.*\bdisplayId=(\d+)""")
    private val LIMITES = Regex("""bounds=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]""")
    private val TIPO = Regex("""mActivityType=(\w+)""")
    private val TASK = Regex("""^\s*taskId=\d+:\s*([\w.]+)/\S*.*?\bvisible=(true|false)""")
    private val TOPO = Regex("""topActivity=ComponentInfo\{([\w.]+)/""")

    /** Uma pilha que cobre pelo menos isto da tela em cada eixo conta como "a da frente". */
    private const val COBERTURA = 0.85

    /**
     * A pilha visível mais alta da tela [displayId]. Com [largura]/[altura] da tela, pilhas que
     * cobrem só um pedaço dela (uma janela flutuante, o mapa embutido do launcher automotivo)
     * não contam como "a da frente": o que está atrás delas continua sendo a tela que se vê.
     */
    fun ler(stackList: String, displayId: Int = 0, largura: Int = 0, altura: Int = 0): Topo? {
        var naTela = false
        var inicio = false
        var parcial = false
        for (linha in stackList.lineSequence()) {
            val cabecalho = CABECALHO.find(linha)
            if (cabecalho != null) {
                naTela = cabecalho.groupValues[1].toIntOrNull() == displayId
                inicio = false
                parcial = largura > 0 && altura > 0 && LIMITES.find(linha)?.let { m ->
                    val (l, t, r, b) = m.destructured
                    (r.toInt() - l.toInt()) < largura * COBERTURA || (b.toInt() - t.toInt()) < altura * COBERTURA
                } == true
                continue
            }
            if (!naTela) continue
            TIPO.find(linha)?.let { inicio = it.groupValues[1] == "home" }
            val task = TASK.find(linha) ?: continue
            if (task.groupValues[2] != "true") continue
            if (parcial && !inicio) continue
            val pacote = TOPO.find(linha)?.groupValues?.get(1) ?: task.groupValues[1]
            return Topo(pacote, inicio)
        }
        return null
    }
}

/**
 * Mantém o launcher 3D como tela inicial quando o sistema sozinho não mantém (Alpha 7.8).
 *
 * ## O RELATO
 *
 * "A aba que permite selecionar qual launcher você quer utilizar não aplica de fato o launcher 3D
 * como padrão." A troca era só `cmd package set-home-activity` — a preferência de HOME do Android.
 * Ela vale para quem PEDE a tela inicial ao Android (a tecla Início, o boot padrão). A tela de
 * fábrica desta central (`com.beantechs.mediacenter`) é aberta também por caminhos da própria
 * GWM que não perguntam nada ao Android, e aí a preferência não tem voto.
 *
 * ## O QUE A GUARDA FAZ
 *
 * Com o launcher 3D escolhido ([Preferencias.HOME_LAUNCHER3D_FIXO]), quando a tela de
 * fábrica chega à frente COMO TELA INICIAL (pilha do tipo `home` no topo da tela principal) logo
 * depois de um pedido de início, o DeepGWT abre o launcher 3D por cima. "Pedido de início" é:
 *  - a tecla Início (o serviço de acessibilidade vê as teclas);
 *  - um toque na barra do sistema (o painel lateral com a casinha), seguido da tela de fábrica;
 *  - o carro acabando de ligar ou a tela acordando (uma janela curta, que encolhe depois da
 *    primeira devolução).
 *
 * FORA desses momentos a tela de fábrica fica: é ela que também mostra rádio e mídia, e o
 * motorista pode abri-la de propósito. Abrir pelo launcher 3D ou pela barra do DeepGWT libera
 * ([liberarFabrica]) — os dois avisam.
 *
 * ## SEGURANÇA
 *
 * - Mais de [MAX_DEVOLUCOES] devoluções em [JANELA_DAS_DEVOLUCOES_MS] = algo brigando (ou o
 *   launcher 3D caindo): a guarda para por [PAUSA_MS] e registra.
 * - Voltar à tela de fábrica desliga tudo: Telas Iniciais no DeepGWT, o painel do launcher 3D,
 *   o instalador (Ajustes → Launcher 3D) ou, pelo cabo, o broadcast de
 *   `LauncherDeFabricaReceiver`. Desinstalar o DeepGWT também desfaz (o Android descarta a
 *   preferência que aponta para um app removido).
 */
object GuardaDoLauncher3D {
    private const val TAG = "GuardaDoLauncher3D"

    /** Quanto um toque na barra do sistema ou a tecla Início "vale" como pedido de início. */
    internal const val JANELA_DO_PEDIDO_MS = 3_000L

    /** Depois de ligar ou acordar a tela (encurtada depois da primeira devolução). */
    internal const val JANELA_DA_PARTIDA_MS = 60_000L
    internal const val JANELA_DO_DESPERTAR_MS = 20_000L

    /** Quanto tempo a tela de fábrica aberta de propósito fica livre da guarda. */
    internal const val LIBERACAO_MS = 8_000L

    internal const val FIM_DA_PARTIDA_APOS_DEVOLVER_MS = 10_000L

    /** Quanto a tela de fábrica pode demorar a chegar à frente depois do pedido de início. */
    internal const val ESPERA_DA_TELA_MS = 1_200L

    internal const val MAX_DEVOLUCOES = 4
    internal const val JANELA_DAS_DEVOLUCOES_MS = 30_000L
    internal const val PAUSA_MS = 180_000L

    /** A Activity HOME da tela inicial 3D (nome de classe dentro deste pacote). Antes era `com.havalh6.viewer.MainActivity`. */
    const val COMPONENTE_3D = "br.com.deepgwt.tela3d.ponte.TelaInicial3dActivity"

    /** O componente que a versão anterior do app registrou como tela inicial preferida; hoje não existe mais. */
    internal const val COMPONENTE_3D_ANTIGO = "com.havalh6.viewer.MainActivity"

    @Volatile private var pedidoAte = 0L
    @Volatile private var pedidoEm = 0L
    @Volatile private var partidaAte = 0L
    @Volatile private var liberadoAte = 0L
    @Volatile private var pausadoAte = 0L
    @Volatile private var fabricaVistaEm = 0L
    @Volatile private var fabricaVista: String = HomeLauncherController.FACTORY_PACKAGE
    private val devolucoes = ArrayDeque<Long>()

    private val fila: Handler by lazy {
        Handler(HandlerThread("GuardaDoLauncher3D").apply { start() }.looper)
    }
    private val verificar = Runnable { verificarAgora() }

    @Volatile private var receptorRegistrado = false

    private val despertar = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_ON) {
                armarPartida(JANELA_DO_DESPERTAR_MS, "tela acordou")
            }
        }
    }

    /** A escolha gravada, e só se o nível libera o launcher 3D (Alpha 8.0). */
    fun ativa(): Boolean =
        runCatching {
            DeepGwt.contextoProtegido
                .getSharedPreferences("deepgwt_prefs", Context.MODE_PRIVATE)
                .getBoolean(Preferencias.HOME_LAUNCHER3D_FIXO.chave, false) &&
                br.com.deepgwt.licenca.Portao.permite(br.com.deepgwt.licenca.Recurso.LAUNCHER_3D)
        }.getOrDefault(false)

    /** O app (ou o serviço de acessibilidade) acabou de subir: conta como o carro ligando. */
    @JvmStatic
    fun aoIniciar() {
        val contexto = DeepGwt.contexto
        if (!receptorRegistrado) {
            receptorRegistrado = true
            runCatching {
                contexto.applicationContext.registerReceiver(despertar, IntentFilter(Intent.ACTION_SCREEN_ON))
            }.onFailure { receptorRegistrado = false }
        }
        if (!ativa()) return
        HomeLauncherController.reafirmarEmSegundoPlano()
        armarPartida(JANELA_DA_PARTIDA_MS, "partida")
    }

    /** Tecla Início (vista pelo serviço de acessibilidade). */
    @JvmStatic
    fun aoApertarInicio() {
        if (!ativa()) return
        armarPedido()
        agendar(350L)
    }

    private fun armarPedido() {
        pedidoEm = SystemClock.elapsedRealtime()
        pedidoAte = pedidoEm + JANELA_DO_PEDIDO_MS
    }

    /** Um toque em alguma janela: da barra do sistema, conta como pedido de início. */
    @JvmStatic
    fun aoTocar(pacote: String?) {
        if (pacote == null || !ehBarraDoSistema(pacote)) return
        if (!ativa()) return
        armarPedido()
    }

    /** Uma janela veio à frente na tela principal. */
    @JvmStatic
    fun aoMudarJanela(pacote: String?) {
        if (pacote == null || !ativa()) return
        val proprio = DeepGwt.contexto.packageName
        if (pacote == proprio || ehBarraDoSistema(pacote)) return
        if (pacote == HomeLauncherController.FACTORY_PACKAGE || HomeLauncherController.ehTelaInicial(pacote)) {
            fabricaVistaEm = SystemClock.elapsedRealtime()
            fabricaVista = pacote
        }
        // Confere com qualquer janela, não só a da tela de fábrica: nem toda tela inicial avisa
        // pela própria janela (o launcher automotivo avisa pelo mapa embutido nela). Quem decide é
        // o topo da tela, lido em [verificarAgora].
        val agora = SystemClock.elapsedRealtime()
        if (agora < pedidoAte || agora < partidaAte) agendar(250L)
    }

    /** Quem abre a tela de fábrica DE PROPÓSITO avisa aqui (launcher 3D, barra do DeepGWT). */
    @JvmStatic
    fun liberarFabrica(motivo: String) {
        liberadoAte = SystemClock.elapsedRealtime() + LIBERACAO_MS
        pedidoAte = 0L
        partidaAte = 0L
        Log.w(TAG, "tela de fábrica liberada: $motivo")
    }

    /** Atalho para os pontos que abrem apps por Intent: libera se o destino é a tela de fábrica. */
    @JvmStatic
    fun aoAbrirPorEscolha(intent: Intent?) {
        val pacote = intent?.component?.packageName ?: intent?.`package` ?: return
        if (pacote == HomeLauncherController.FACTORY_PACKAGE) liberarFabrica("aberta pelo launcher 3D")
    }

    @JvmStatic
    fun aoAbrirPacote(pacote: String?) {
        if (pacote == HomeLauncherController.FACTORY_PACKAGE) liberarFabrica("aberta pelo DeepGWT")
    }

    private fun armarPartida(duracaoMs: Long, motivo: String) {
        if (!ativa()) return
        partidaAte = SystemClock.elapsedRealtime() + duracaoMs
        Log.w(TAG, "armada ($motivo)")
        agendar(1_200L)
    }

    private fun agendar(atrasoMs: Long) {
        fila.removeCallbacks(verificar)
        fila.postDelayed(verificar, atrasoMs)
    }

    internal fun ehBarraDoSistema(pacote: String): Boolean = pacote.contains("systemui", ignoreCase = true)

    private fun verificarAgora() {
        if (!ativa()) return
        val agora = SystemClock.elapsedRealtime()
        if (agora < liberadoAte || agora < pausadoAte) return
        if (agora >= pedidoAte && agora >= partidaAte) return
        val lista = runCatching { Shell.executar(arrayOf("am", "stack", "list")) }
            .getOrNull().orEmpty()
        if (lista.isBlank()) {
            // Sem Shizuku não dá para saber se a tela de fábrica abriu COMO início. O pedido de
            // início (tecla, toque na barra do sistema, partida) seguido dela já é sinal forte.
            if (agora - fabricaVistaEm < 1_500L) devolver(fabricaVista, agora)
            return
        }
        val tela = android.util.DisplayMetrics().also {
            (DeepGwt.contexto.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager)
                .defaultDisplay.getRealMetrics(it)
        }
        val topo = TopoDaTelaPrincipal.ler(lista, 0, tela.widthPixels, tela.heightPixels) ?: return
        val proprio = DeepGwt.contexto.packageName
        if (topo.pacote == proprio) return
        if (!topo.inicio) {
            // Depois de tocar na barra do sistema o motorista abriu outra coisa (a grade de apps,
            // o clima): o toque não era para ir ao início. Só depois de um instante — a tela de
            // fábrica pode demorar a chegar, e antes dela o topo ainda é o app de antes. A partida
            // não se apaga aqui: telas do painel também mandam eventos, e ela tem prazo próprio.
            if (agora - pedidoEm > ESPERA_DA_TELA_MS) pedidoAte = 0L
            return
        }
        devolver(topo.pacote, agora)
    }

    private fun devolver(deQuem: String, agora: Long) {
        while (devolucoes.isNotEmpty() && agora - devolucoes.first() > JANELA_DAS_DEVOLUCOES_MS) devolucoes.removeFirst()
        if (devolucoes.size >= MAX_DEVOLUCOES) {
            pausadoAte = agora + PAUSA_MS
            devolucoes.clear()
            Log.e(TAG, "$MAX_DEVOLUCOES devoluções em ${JANELA_DAS_DEVOLUCOES_MS / 1000}s: pausando a guarda por ${PAUSA_MS / 1000}s")
            return
        }
        devolucoes.addLast(agora)
        pedidoAte = 0L
        // Na partida o carro às vezes reabre a tela de fábrica logo em seguida; depois de uma
        // devolução a partida só vale mais uns segundos, para não pegar o motorista abrindo a mídia.
        if (partidaAte > agora) partidaAte = minOf(partidaAte, agora + FIM_DA_PARTIDA_APOS_DEVOLVER_MS)
        Log.w(TAG, "$deQuem abriu como tela inicial: devolvendo o launcher 3D")
        abrirLauncher3D(DeepGwt.contexto)
    }

    fun abrirLauncher3D(context: Context) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .setComponent(ComponentName(context.packageName, COMPONENTE_3D))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        runCatching { context.startActivity(intent) }
            .onFailure { Log.e(TAG, "não deu para abrir o launcher 3D: ${it.message}") }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
