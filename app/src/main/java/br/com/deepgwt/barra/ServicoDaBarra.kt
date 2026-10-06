// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.barra

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.snapshotFlow
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import br.com.deepgwt.nucleo.Preferencias
import br.com.deepgwt.nucleo.Shell
import br.com.deepgwt.ui.TemaDeepGwt
import br.com.deepgwt.veiculo.Veiculo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * O serviço da barra (barra-inferior-servico.md): duas janelas sobrepostas presas ao x=0 físico, o
 * recuo dos apps (`wm overscan`), esconder (pelo dedo, sozinha, na frente da projeção), o encaixe pela
 * tela, a acessibilidade, o cartão de volume aberto pelo carro, as janelas auxiliares e a mídia.
 *
 * Manifesto (pela sala suja): não exportado, `android:enabled="true"`. `START_STICKY`: recriado sem
 * intent, comporta-se como um pedido de mostrar.
 */
open class ServicoDaBarra : LifecycleService() {

    private lateinit var wm: WindowManager
    private var ativo = false

    private var janelaDaBarra: JanelaCompose? = null
    private var janelaDeMenus: JanelaCompose? = null
    private lateinit var auxiliares: JanelasAuxiliares
    private lateinit var encaixe: EncaixeNaTela
    private var motor: MotorDaMidia? = null

    private val presaDaBarra = PresaAoZero("barra") { larguraFisica() }
    private val presaDosMenus = PresaAoZero("menus") { larguraFisica() }

    /** Comandos `wm` em ordem, fora da principal. */
    private val filaDoShell = Executors.newSingleThreadExecutor { r -> Thread(r, "DeepGWT.Barra.Shell") }

    // Geometria e recuo
    private var maiorPainelVisto = 0
    private var reesconderAgendado = false
    private val reesconder = Runnable {
        reesconderAgendado = false
        if (EstadoDaBarra.painelLateralOculto) Thread({ PainelLateral.apply(true) }, "DeepGWT.Barra.Painel").start()
    }

    @Volatile
    private var aplicadoPx = -1

    @Volatile
    private var ultimoTratado: String? = null
    private var recuoAtual = RegrasDoServico.RecuoDoApp(Barra.ALTURA_PADRAO_DP, 0)

    // Laço
    private var contagemDaTarja = 0
    private var voltas = 0
    private var escondidaPorProjecao = false

    // Janela de menus
    private var menusAcesos = false
    private var ultimaAcesaSoColuna = false
    private val apagarMenus = Runnable { aplicarAlfaDosMenus(acesa = false, tocavel = false) }

    // Cartão de volume
    private val cartaoDoCarro = CartaoPeloCarro()
    private var janelaDoVolume: JanelaDoVolumeNoTopo? = null
    private var donoDoVolume: DonoDaJanela? = null

    private var receptorRegistrado = false

    private val densidade: Float get() = resources.displayMetrics.density
    private fun alturaPx(): Int = (EstadoDaBarra.alturaDp * densidade).toInt()
    private fun gatilhoPx(): Int = (Barra.FAIXA_DE_GATILHO_DP * densidade).toInt()
    private val integracoes get() = Barra.integracoes

    @Suppress("DEPRECATION")
    private fun metricasReais(): DisplayMetrics? =
        runCatching { DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) } }.getOrNull()

    @Suppress("DEPRECATION")
    private fun larguraDoApp(): Int = runCatching { DisplayMetrics().also { wm.defaultDisplay.getMetrics(it) }.widthPixels }.getOrDefault(0)

    private fun larguraFisica(): Int = metricasReais()?.widthPixels ?: 0
    private fun alturaFisica(): Int = metricasReais()?.heightPixels ?: 0

    // ── A. Partida ──────────────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        val outra = instancia
        if (outra != null && outra !== this) {
            Log.w(TAG, "outro serviço da barra já está de pé (${outra.javaClass.name}); este para")
            stopSelf()
            return
        }
        instancia = this
        ativo = true
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        auxiliares = JanelasAuxiliares(this)
        encaixe = EncaixeNaTela(this, lifecycleScope, acessoDoEncaixe)

        // 1–3
        Barra.recarregarPreferencias()
        EstadoDaBarra.painelLateralOculto = PainelLateral.isEnabled()
        if (EstadoDaBarra.painelLateralOculto) PainelLateral.reapply()
        EstadoDaBarra.visivel = true
        // 4 (sem permissão ou janela recusada: o serviço se encerra)
        if (!montarJanelas()) {
            stopSelf()
            return
        }
        // 5–6
        observarEstado()
        runCatching {
            integracoes.sportPlus.iniciarMonitoramento(this) {
                ThreadPrincipal.executar { EstadoDaBarra.pedirAvisoDoSportPlus = true }
            }
        }.onFailure { Log.w(TAG, "SPORT+ não iniciou: ${it.message}") }
        // 7
        registrarReceptorDePosicao()
        // 8
        motor = MotorDaMidia(this).also { it.iniciar() }
        // 9
        iniciarCartaoDoCarro()
        observarCamadaDeAcessibilidade()
        // 10
        runCatching { integracoes.telaInicial.aoIniciarBarra() }
        // 11
        lacoPrincipal()
        // 12
        lifecycleScope.launch(Dispatchers.IO) { garantirAcessibilidade() }
        // 13
        EsconderSozinha.rearmar()
    }

    /** Caso 2: um pedido de mostrar (ou o sistema recriando o serviço). */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (!ativo) return START_NOT_STICKY
        if (intent?.action == ACAO_DEPURACAO) {
            depurar(intent)
            return START_STICKY
        }
        if (janelaDaBarra?.entregue != true) {
            Log.w(TAG, "janela da barra não entregue: remontando as duas")
            desmontarJanelas()
            if (!montarJanelas()) {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        EstadoDaBarra.visivel = true
        EsconderSozinha.rearmar()
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (ativo) medirRecuoEsquerdo()
    }

    // ── B. As duas janelas ─────────────────────────────────────────────────────────────

    private val flagsDasJanelas =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

    @Suppress("DEPRECATION")
    private val visibilidadeImersiva =
        View.SYSTEM_UI_FLAG_LOW_PROFILE or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

    /** Caso 5–6 e 19. Caso 7: quem remonta tira as antigas antes. */
    @Suppress("DEPRECATION")
    private fun montarJanelas(): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "sem permissão de sobreposição")
            return false
        }
        val largura = larguraFisica().takeIf { it > 0 } ?: WindowManager.LayoutParams.MATCH_PARENT
        val gravidade = Gravity.BOTTOM or Gravity.RIGHT
        val pBarra = JanelaCompose.parametros(largura, alturaPx(), flagsDasJanelas, gravidade).apply {
            systemUiVisibility = visibilidadeImersiva
            y = if (EstadoDaBarra.visivel) (recuoAtual.deslocamentoDp * densidade).toInt()
            else ContasDaBarra.deslocamentoEscondidoPx(alturaPx(), gatilhoPx())
            title = "DeepGWT barra"
        }
        val barra = JanelaCompose(
            this, pBarra, "barra",
            regiao = { regiaoDaBarra() },
            aoDispor = { j ->
                presaDaBarra.aoDispor(j)
                medirRecuoEsquerdo()
            },
        ) { ConteudoDaJanelaDaBarra() }
        if (!barra.montar()) return false
        janelaDaBarra = barra

        val pMenus = JanelaCompose.parametros(
            largura,
            alturaFisica().takeIf { it > 0 } ?: WindowManager.LayoutParams.MATCH_PARENT,
            flagsDasJanelas or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            gravidade,
        ).apply {
            systemUiVisibility = visibilidadeImersiva
            y = 0
            windowAnimations = 0
            alpha = 0f
            title = "DeepGWT menus da barra"
        }
        val menus = JanelaCompose(
            this, pMenus, "menus",
            regiao = { regiaoDosMenus() },
            aoDispor = { presaDosMenus.aoDispor(it) },
        ) { ConteudoDaJanelaDeMenus() }
        if (menus.montar()) janelaDeMenus = menus else Log.w(TAG, "janela de menus recusada")
        menusAcesos = false
        medirRecuoEsquerdo()
        // Caso 19: recuo logo após entregar a janela.
        aplicarRecuo(calcularRecuo(ultimoTratado ?: packageName))
        return true
    }

    private fun desmontarJanelas() {
        janelaDaBarra?.desmontar()
        janelaDaBarra = null
        janelaDeMenus?.desmontar()
        janelaDeMenus = null
        menusAcesos = false
    }

    /** Caso 10: as duas janelas recalculam regiões. */
    internal fun pedirLayoutDasJanelas() {
        janelaDaBarra?.pedirLayout()
        janelaDeMenus?.pedirLayout()
    }

    private fun aaNoDisplay0(): Boolean =
        runCatching { integracoes.lancador.projecaoReconhecida(EstadoDaBarra.pacoteNoDisplay0) }.getOrNull() ==
            RegrasDoServico.AA_APP

    /** Caso 9, janela da barra. */
    private fun regiaoDaBarra(): Retangulo {
        val v = janelaDaBarra?.vista ?: return Retangulo.VAZIO
        val s = EstadoDaBarra
        return RegrasDoServico.regiaoDaBarra(
            s.visivel, s.painelExpandido, v.width, v.height, densidade, s.recuoEsquerdoPx, aaNoDisplay0(),
        )
    }

    /** Caso 9, janela de menus. */
    private fun regiaoDosMenus(): Retangulo {
        val v = janelaDeMenus?.vista ?: return Retangulo.VAZIO
        return RegrasDoServico.regiaoDosMenus(EstadoDaBarra.algoPedeToque(), v.width, v.height)
    }

    // ── C. Geometria publicada e janela de menus ───────────────────────────────────────

    /** Caso 12. */
    private fun medirRecuoEsquerdo() {
        val fisica = larguraFisica()
        val app = larguraDoApp()
        val observado = if (fisica > 0 && app > 0) fisica - app else 0
        if (observado > maiorPainelVisto) maiorPainelVisto = observado
        val s = EstadoDaBarra
        if (s.painelLateralOculto && observado > 0 && !reesconderAgendado) {
            reesconderAgendado = true
            ThreadPrincipal.postarComAtraso(REESCONDER_MS, reesconder)
        }
        val recuo = if (s.painelLateralOculto) 0 else maiorPainelVisto
        if (s.recuoEsquerdoPx != recuo) s.recuoEsquerdoPx = recuo
        val medida = janelaDaBarra?.vista?.width ?: 0
        if (medida > 0 && s.larguraDaJanelaPx != medida) s.larguraDaJanelaPx = medida
    }

    /** Casos 13–14. */
    private fun atualizarMenus(mostrar: Boolean, toque: Boolean, soColuna: Boolean) {
        ThreadPrincipal.cancelar(apagarMenus)
        when {
            mostrar -> {
                if (!menusAcesos) ultimaAcesaSoColuna = soColuna
                aplicarAlfaDosMenus(acesa = true, tocavel = toque)
            }
            menusAcesos && ultimaAcesaSoColuna ->
                ThreadPrincipal.postarComAtraso(RegrasDoServico.ATRASO_DE_FECHAR_A_COLUNA_MS, apagarMenus)
            else -> aplicarAlfaDosMenus(acesa = false, tocavel = false)
        }
        pedirLayoutDasJanelas()
    }

    private fun aplicarAlfaDosMenus(acesa: Boolean, tocavel: Boolean) {
        val j = janelaDeMenus ?: return
        j.params.alpha = if (acesa) 1f else 0f
        j.params.flags = if (acesa && tocavel) {
            j.params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            j.params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        menusAcesos = acesa
        j.atualizar()
    }

    // ── Observação do estado (casos 13–14, 17, 18, 25–27, 36, 39) ──────────────────────

    private fun observarEstado() {
        val s = EstadoDaBarra
        lifecycleScope.launch {
            snapshotFlow {
                val mostrar = RegrasDoServico.mostrarMenus(
                    s.menuDeAppsAberto, s.menuDeConducaoAberto, s.menuDeClimaAberto, s.gavetaAberta, s.cartao, s.escolhaDeModo,
                )
                val toque = s.algoPedeToque()
                Triple(mostrar, toque, RegrasDoServico.soAColuna(s.escolhaDeModo, toque))
            }.distinctUntilChanged().collect { (mostrar, toque, soColuna) -> atualizarMenus(mostrar, toque, soColuna) }
        }
        lifecycleScope.launch {
            snapshotFlow { s.painelExpandido }.distinctUntilChanged().drop(1).collect { expandido ->
                if (expandido) abrirAtividadeDoPainel()
                pedirLayoutDasJanelas()
            }
        }
        lifecycleScope.launch {
            snapshotFlow { s.visivel }.distinctUntilChanged().drop(1).collect { aoMudarVisibilidade(it) }
        }
        lifecycleScope.launch {
            snapshotFlow { s.alturaDp }.distinctUntilChanged().drop(1).collect { aoMudarAltura() }
        }
        lifecycleScope.launch {
            snapshotFlow {
                listOf(
                    s.visivel, s.painelExpandido, s.menuDeAppsAberto, s.menuDeConducaoAberto, s.menuDeClimaAberto,
                    s.gavetaAberta, s.cartao != null, s.ajusteRapido != null, s.escolhaDeModo != null, s.esconderSozinha,
                )
            }.distinctUntilChanged().collect { EsconderSozinha.rearmar() }
        }
        lifecycleScope.launch {
            snapshotFlow {
                listOf(
                    RegrasDoServico.indicadorAparece(s.indicadorLigado, s.painelExpandido),
                    s.indicadorFonteSp, s.indicadorCanto, s.indicadorXDp, s.indicadorYDp,
                )
            }.distinctUntilChanged().collect {
                auxiliares.indicador(it[0] as Boolean, it[1] as Int, it[2] as Int, it[3] as Int, it[4] as Int)
            }
        }
        lifecycleScope.launch {
            snapshotFlow { s.retornoDeGesto }.distinctUntilChanged().collect { auxiliares.retorno(it) }
        }
        lifecycleScope.launch {
            snapshotFlow { s.pedirAvisoDoSportPlus }.distinctUntilChanged().collect { auxiliares.aviso(it) }
        }
    }

    // ── E. Recuo dos apps e deslocamento ───────────────────────────────────────────────

    /** Caso 16: prioridade manual → medido → padrão. */
    private fun calcularRecuo(pacote: String?): RegrasDoServico.RecuoDoApp {
        val prefs = PreferenciasDaBarra.abrir()
        val fonte = prefs?.let { FonteDoSharedPreferences(it) }
        val manual = pacote?.let { p ->
            ContasDaBarra.recuoManual(fonte?.texto(Preferencias.BOTTOM_BAR_OVERRIDES.chave), p)
        }
        val medidos = ContasDaBarra.lerRecuosMedidos(fonte?.texto(Preferencias.BOTTOM_BAR_AUTO_OVERSCAN.chave))
        val explicito = fonte?.let { PreferenciasDaBarra.overscanExplicito(it) }
        return RegrasDoServico.recuoDoApp(pacote, EstadoDaBarra.alturaDp, manual, medidos, explicito)
    }

    private fun shell(vararg comando: String) {
        runCatching { filaDoShell.execute { Shell.executar(arrayOf(*comando)) } }
    }

    /** Caso 16, aplicar (principal). */
    private fun aplicarRecuo(r: RegrasDoServico.RecuoDoApp) {
        recuoAtual = r
        if (!EstadoDaBarra.visivel) {
            aplicadoPx = 0
            shell("wm", "overscan", "0,0,0,0")
            return
        }
        val px = ContasDaBarra.overscanEmPx(r.overscanDp, densidade)
        aplicadoPx = px
        shell("wm", "overscan", "0,0,0,$px")
        janelaDaBarra?.let {
            it.params.y = (r.deslocamentoDp * densidade).toInt()
            it.atualizar()
        }
    }

    /** Caso 17. */
    private fun aoMudarVisibilidade(visivel: Boolean) {
        val j = janelaDaBarra ?: return
        if (visivel) {
            j.params.height = alturaPx()
            // S-D2 corrigido: reaplica o deslocamento do app.
            j.params.y = (recuoAtual.deslocamentoDp * densidade).toInt()
            j.atualizar()
            aplicarRecuo(recuoAtual)
        } else {
            EstadoDaBarra.painelExpandido = false
            EstadoDaBarra.fecharTudo()
            j.params.height = alturaPx()
            j.params.y = ContasDaBarra.deslocamentoEscondidoPx(alturaPx(), gatilhoPx())
            j.atualizar()
            aplicadoPx = 0
            shell("wm", "overscan", "0,0,0,0")
        }
        pedirLayoutDasJanelas()
    }

    /** Caso 18. */
    private fun aoMudarAltura() {
        encaixe.esquecer()
        val j = janelaDaBarra ?: return
        j.params.height = alturaPx()
        if (!EstadoDaBarra.visivel) j.params.y = ContasDaBarra.deslocamentoEscondidoPx(alturaPx(), gatilhoPx())
        j.atualizar()
        aplicarRecuo(calcularRecuo(ultimoTratado ?: packageName))
    }

    /** Receptor `UPDATE_BAR_POSITION` (caso 16, S-D3). */
    private val receptorDePosicao = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val i = intent ?: return
            val over = i.getIntExtra("overscan", RegrasDoServico.EXTRA_AUSENTE_OVERSCAN)
            val desl = i.getIntExtra("offset", RegrasDoServico.EXTRA_AUSENTE_DESLOCAMENTO)
            val semExtras = over == RegrasDoServico.EXTRA_AUSENTE_OVERSCAN && desl == RegrasDoServico.EXTRA_AUSENTE_DESLOCAMENTO
            if (semExtras) {
                ultimoTratado = null // o laço reaplica na próxima volta
                return
            }
            if (i.`package` != packageName) {
                Log.w(TAG, "posição com extras de fora do app ignorada")
                return
            }
            if (EstadoDaBarra.pacoteNoDisplay0.isEmpty()) return
            val novo = RegrasDoServico.recuoPeloReceptor(over, desl, recuoAtual, EstadoDaBarra.alturaDp) ?: return
            aplicarRecuo(novo)
        }
    }

    private fun registrarReceptorDePosicao() {
        runCatching {
            ContextCompat.registerReceiver(this, receptorDePosicao, IntentFilter(ACAO_POSICAO), ContextCompat.RECEIVER_NOT_EXPORTED)
            receptorRegistrado = true
        }.onFailure { Log.w(TAG, "receptor de posição: ${it.message}") }
    }

    // ── D. Laço de 1 s ─────────────────────────────────────────────────────────────────

    private fun lacoPrincipal() {
        lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                runCatching { volta() }.onFailure { Log.w(TAG, "volta do laço: ${it.message}") }
                withContext(Dispatchers.Main) { pedirLayoutDasJanelas() }
                delay(1_000)
            }
        }
    }

    private fun telaInicial(pacote: String): Boolean = runCatching {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.queryIntentActivities(i, 0).any { it.activityInfo?.packageName == pacote }
    }.getOrDefault(false)

    private suspend fun volta() {
        val l = integracoes.lancador
        val topo = l.pacoteNoTopo(0)
        val noPainel = l.projecaoAtivaNoDisplay(3)
        val ehInicial = topo != null && topo != EstadoDaBarra.pacoteNoDisplay0 && telaInicial(topo)
        withContext(Dispatchers.Main) {
            val s = EstadoDaBarra
            s.projecaoNoPainel = noPainel ?: ""
            if (topo != null) {
                if (topo != s.pacoteNoDisplay0) {
                    s.pacoteNoDisplay0 = topo
                    if (!ehInicial) s.pacoteEscolhido = topo
                }
            } else {
                s.pacoteNoDisplay0 = packageName
            }
        }
        // c) restaurados que não rodam mais
        if (EstadoDaBarra.appsRestaurados.isNotEmpty()) {
            val pilhas = Shell.executar(arrayOf("am", "stack", "list"))
            if (pilhas.isNotEmpty()) EstadoDaBarra.appsRestaurados.reter { pilhas.contains(it) }
        }
        val anterior = ultimoTratado
        if (topo != null) {
            // d)
            if (topo != anterior) {
                contagemDaTarja = 5
                runCatching { integracoes.telaInicial.aoMudarAppNoDisplay0(topo) }
            }
            // e)
            voltas++
            if (contagemDaTarja > 0) {
                contagemDaTarja--
                if (contagemDaTarja % 2 == 0) {
                    voltas = 0
                    runCatching { l.corrigirTarjaPreta(topo) }
                    if (contagemDaTarja == 0) encaixe.conferir(topo)
                }
            } else if (voltas >= 30) {
                voltas = 0
                runCatching { l.corrigirTarjaPreta(topo) }
                encaixe.conferir(topo)
            }
            // f)
            if (topo != anterior) {
                ultimoTratado = topo
                val esconder = PreferenciasDaBarra.fonte()?.booleano(Preferencias.BOTTOM_BAR_AUTO_HIDE.chave, false) ?: false
                withContext(Dispatchers.Main) {
                    EstadoDaBarra.esconderSozinha = esconder
                    aplicarRecuo(calcularRecuo(topo))
                }
            }
        }
        // g)
        sairDaFrenteDaProjecao(topo)
    }

    /** Caso 37. */
    private suspend fun sairDaFrenteDaProjecao(topo: String?) {
        val pref = PreferenciasDaBarra.fonte()?.booleano(Preferencias.HIDE_BAR_ON_ANDROID_AUTO.chave, true) ?: true
        val reconhecida = runCatching { integracoes.lancador.projecaoReconhecida(topo) }.getOrNull()
        val na = RegrasDoServico.naProjecao(topo, reconhecida)
        withContext(Dispatchers.Main) {
            val (acao, marca) = RegrasDoServico.saidaDaFrente(pref, na, escondidaPorProjecao, EstadoDaBarra.visivel)
            escondidaPorProjecao = marca
            when (acao) {
                RegrasDoServico.AcaoDaProjecao.ESCONDER -> EstadoDaBarra.visivel = false
                RegrasDoServico.AcaoDaProjecao.MOSTRAR -> EstadoDaBarra.visivel = true
                RegrasDoServico.AcaoDaProjecao.NADA -> Unit
            }
        }
    }

    // ── F. Encaixe ──────────────────────────────────────────────────────────────────────

    private val acessoDoEncaixe = object : EncaixeNaTela.Acesso {
        override fun aplicadoPx() = this@ServicoDaBarra.aplicadoPx
        override fun ultimoTratado() = this@ServicoDaBarra.ultimoTratado
        override fun barraLivre(): Boolean {
            val s = EstadoDaBarra
            return s.visivel && !s.algoPedeToque() && s.cartao == null && s.ajusteRapido == null &&
                s.escolhaDeModo == null && !s.painelExpandido
        }
        override fun reaplicarRecuo(pacote: String) {
            if (ultimoTratado == pacote) aplicarRecuo(calcularRecuo(pacote))
        }
    }

    // ── I. Cartão de volume aberto pelo carro e a camada de acessibilidade ────────────

    private val ouvinteDoVolume = Veiculo.Ouvinte { chave, valor ->
        if ((chave == RegrasDoServico.VOLUME || chave == RegrasDoServico.ACAO_DO_VOLUME) &&
            cartaoDoCarro.avisoPedeCartao(chave, valor)
        ) {
            // S-D4: o aviso chega na thread do motor; o estado é escrito na principal.
            ThreadPrincipal.postar { abrirCartaoPeloCarro("$chave=$valor") }
        }
    }

    /** Caso 31. */
    private fun iniciarCartaoDoCarro() {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { Veiculo.acompanhar(listOf(RegrasDoServico.ACAO_DO_VOLUME, RegrasDoServico.FAIXA_DO_VOLUME)) }
                .onFailure { Log.w(TAG, "acompanhar volume: ${it.message}") }
            cartaoDoCarro.semear(runCatching { Veiculo.ler(RegrasDoServico.VOLUME) }.getOrNull())
            Veiculo.observar(ouvinteDoVolume)
        }
    }

    /** Caso 33 (principal). */
    private fun abrirCartaoPeloCarro(motivo: String) {
        val s = EstadoDaBarra
        if (!RegrasDoServico.podeAbrirCartaoPeloCarro(
                s.cartaoPeloCarro, s.arrastandoCartao, s.cartao, s.cartaoAbertoPeloCarro, s.painelExpandido,
            )
        ) return
        Log.i(TAG, "[VOLUME] cartão pelo carro ($motivo)")
        s.cartaoAbertoPeloCarro = true
        s.cartao = FuncaoDoAjuste.VOLUME
        s.gatilhoDoCartao += 1
    }

    private fun retanguloDoVolume(): IntArray = GeometriaDoVolumeNoTopo.retangulo(
        larguraFisica().takeIf { it > 0 } ?: resources.displayMetrics.widthPixels,
        EstadoDaBarra.recuoEsquerdoPx,
        densidade,
    )

    /** Caso 34. */
    private fun observarCamadaDeAcessibilidade() {
        lifecycleScope.launch {
            CamadaDeAcessibilidade.servico.collectLatest { servico ->
                desmontarVolumeDaCamada()
                if (servico == null) return@collectLatest
                val dono = DonoDaJanela()
                val janela = JanelaDoVolumeNoTopo(servico, { dono.ligar(it) }) {
                    TemaDeepGwt() { CartaoDeVolume(naCamada = true) }
                }
                if (!janela.montar(retanguloDoVolume())) {
                    dono.destruir()
                    return@collectLatest
                }
                janelaDoVolume = janela
                donoDoVolume = dono
                EstadoDaBarra.volumeNaCamadaDeAcessibilidade = true
                try {
                    snapshotFlow {
                        Triple(
                            EstadoDaBarra.cartao == FuncaoDoAjuste.VOLUME,
                            !EstadoDaBarra.cartaoAbertoPeloCarro,
                            EstadoDaBarra.recuoEsquerdoPx,
                        )
                    }.distinctUntilChanged().collect { (aberto, tocavel, _) ->
                        janela.atualizar(aberto, tocavel, retanguloDoVolume())
                    }
                } finally {
                    desmontarVolumeDaCamada()
                }
            }
        }
    }

    private fun desmontarVolumeDaCamada() {
        janelaDoVolume?.desmontar()
        janelaDoVolume = null
        donoDoVolume?.destruir()
        donoDoVolume = null
        EstadoDaBarra.volumeNaCamadaDeAcessibilidade = false
    }

    // ── K. Ligar o serviço de acessibilidade ──────────────────────────────────────────

    /** Caso 35 (fundo). S-D8: argv sem shell no meio. */
    private suspend fun garantirAcessibilidade() {
        val componente = integracoes.componenteDaAcessibilidade.trim()
        if (componente.isEmpty()) return
        if (!EsperaDoShell.esperar()) return
        // Pelo ContentResolver, nunca pelo shell: vazio de falha seria lido como lista vazia e a
        // reescrita apagaria os serviços do carro. Leitura nula/falha → fim.
        val lista = runCatching {
            Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        }.getOrNull() ?: return
        val atual = lista.trim()
        val alvo = android.content.ComponentName.unflattenFromString(componente)
        val contem = atual.split(':').any {
            val c = it.trim()
            c.equals(componente, ignoreCase = true) || (alvo != null && android.content.ComponentName.unflattenFromString(c) == alvo)
        }
        if (contem) return
        val nova = if (atual.isEmpty() || atual == "null") componente else "$atual:$componente"
        runCatching {
            Shell.executar(arrayOf("settings", "put", "secure", Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, nova))
            Shell.executar(arrayOf("settings", "put", "secure", Settings.Secure.ACCESSIBILITY_ENABLED, "1"))
            Log.i(TAG, "serviço de acessibilidade ligado")
        }.onFailure { Log.w(TAG, "acessibilidade: ${it.message}") }
    }

    // ── O. Painel expandido ────────────────────────────────────────────────────────────

    /** Caso 39. */
    internal fun abrirAtividadeDoPainel() {
        runCatching {
            startActivity(
                Intent(this, Barra.classeDaAtividadeDoPainel)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }.onFailure { Log.w(TAG, "painel não abriu: ${it.message}") }
    }

    // ── A.3 Canal de depuração ─────────────────────────────────────────────────────────

    private fun depurar(intent: Intent) {
        val comando = intent.getStringExtra("command")?.trim().orEmpty()
        Thread({
            val agora = ThreadPrincipal.agoraMs()
            when (comando) {
                "state" -> motor?.registrarEstado() ?: Log.i(TAG, "[ESTADO] mídia parada")
                "gesto_temp" -> ThreadPrincipal.postar {
                    EstadoDaBarra.retornoDeGesto = RetornoDeGesto(RetornoDeGesto.Tipo.TEMPERATURA, 23.5f, 16f, 32f, 1, false, agora)
                }
                "gesto_fan" -> ThreadPrincipal.postar {
                    EstadoDaBarra.retornoDeGesto = RetornoDeGesto(RetornoDeGesto.Tipo.VENTILACAO, 4f, 0f, 7f, 1, false, agora)
                }
                "gesto_limite" -> ThreadPrincipal.postar {
                    EstadoDaBarra.retornoDeGesto = RetornoDeGesto(RetornoDeGesto.Tipo.VENTILACAO, 7f, 0f, 7f, 1, true, agora)
                }
                "card_toggle" -> MidiaDaBarra.tocarOuPausar()
                "card_next" -> MidiaDaBarra.proxima()
                "card_prev" -> MidiaDaBarra.anterior()
                else -> Log.w(TAG, "comando de depuração desconhecido: '$comando'")
            }
        }, "DeepGWT.Barra.Depuracao").start()
    }

    // ── P. Fim ──────────────────────────────────────────────────────────────────────────

    override fun onDestroy() {
        if (ativo) {
            ativo = false
            if (instancia === this) instancia = null
            ThreadPrincipal.cancelar(reesconder)
            ThreadPrincipal.cancelar(apagarMenus)
            EsconderSozinha.cancelar()
            Barra.cancelarReaberturaPendente()
            motor?.parar()
            motor = null
            runCatching { integracoes.sportPlus.pararMonitoramento() }
            EstadoDaBarra.retornoDeGesto = null
            auxiliares.desmontarTudo()
            desmontarVolumeDaCamada()
            Veiculo.pararDeObservar(ouvinteDoVolume)
            if (receptorRegistrado) runCatching { unregisterReceiver(receptorDePosicao) }
            receptorRegistrado = false
            runCatching {
                filaDoShell.execute {
                    if (DecisoesDaBarra.WM_SIZE_RESET_AO_PARAR) Shell.executar(arrayOf("wm", "size", "reset"))
                    Shell.executar(arrayOf("wm", "overscan", "0,0,0,0"))
                }
            }
            filaDoShell.shutdown()
            desmontarJanelas()
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "DeepGWT.Barra"
        const val ACAO_DEPURACAO = "br.com.deepgwt.DEBUG_MEDIA_COMMAND"
        const val ACAO_POSICAO = "br.com.deepgwt.UPDATE_BAR_POSITION"
        const val REESCONDER_MS = 5_000L

        /** O serviço de pé agora (um só por processo). */
        @Volatile
        internal var instancia: ServicoDaBarra? = null
            private set
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
