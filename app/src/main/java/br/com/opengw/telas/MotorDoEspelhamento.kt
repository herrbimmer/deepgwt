package br.com.opengw.telas

import br.com.opengw.nucleo.Preferencias
import br.com.opengw.veiculo.Sinais
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

/** Parte 3: Android Auto e CarPlay nos displays (detecção, abertura, guardas, vigias, partida). */
internal class MotorDoEspelhamento(private val n: NucleoDoLancador) {

    private val log get() = n.amb.registro
    private val prefs get() = n.amb.prefs
    private val relogio get() = n.amb.relogio
    private val telas get() = n.telas

    // ── estado ──────────────────────────────────────────────────────────────────────────────────

    private val travaDoUsb = Any()
    private var usbLidoEm: Long? = null
    private var usbGuardado = false

    /** F8: horários em ms do relógio de parede; zero = nunca. */
    @Volatile private var usbObservado: Boolean? = null
    @Volatile private var usbConfigurouEm = 0L
    @Volatile private var usbDesconectouEm = 0L
    @Volatile private var vistoNoPainelEm = 0L
    @Volatile private var vistoNo0Em = 0L

    /** K3/K4/K14: horário (parede) da última ida ao painel. Só diagnóstico. */
    @Volatile var idaAoPainelEm = 0L
        private set

    /** H1: tolerância de partida (monotônico). */
    @Volatile private var toleranciaDePartidaAte = 0L

    /** C8: "visto velho" (monotônico; zero = não). */
    @Volatile private var aaVistoVelhoEm = 0L

    private val travaDoCarPlay = Mutex()
    @Volatile var estadoDoCarPlay = EstadoDoCarPlay.DESCONECTADO
        private set
    private val marcaDePreparacao = AtomicBoolean(false)

    private val guardaPublicaDoCarPlay = IntervaloMinimo(3_500)
    private val guardaDoCarPlayPorJanela = IntervaloMinimo(8_000)
    private val restauracaoDoCarPlay = IntervaloMinimo(3_500)
    private val duplicataDoCarPlay = IntervaloMinimo(3_500)
    private val sondagemDoCarPlay = IntervaloMinimo(1_200)
    private val reafirmacaoDoCarPlay = IntervaloMinimo(3_500)
    private val sondagemDoAndroidAuto = IntervaloMinimo(1_200)
    private val reinicioDoAndroidAuto = IntervaloMinimo(5_000)
    private val guardaDoAndroidAuto = IntervaloMinimo(2_500)
    private val limpezaDoAndroidAuto = IntervaloMinimo(10_000)
    private val logDaEvidencia = IntervaloMinimo(3_000)

    private val travaDaJanelaDoAa = Any()
    private var ultimaJanelaDoAa: String? = null
    private var ultimaAcaoDoAa: AcaoDaGuardaDoAa? = null
    private var ultimaGuardaDoAaEm: Long? = null

    private val vigiaIniciada = AtomicBoolean(false)
    private val partidaRodando = AtomicBoolean(false)

    // ── B. classificação e detecção ─────────────────────────────────────────────────────────────

    /** B2: o USB de projeção está configurado? Guardado por 500 ms. */
    fun usbConfigurado(): Boolean {
        synchronized(travaDoUsb) {
            val agora = n.agora()
            val lido = usbLidoEm
            if (lido != null && agora >= lido && agora - lido < 500) return usbGuardado
        }
        var texto = try {
            n.amb.sistema.lerArquivo(ARQUIVO_DO_USB)
        } catch (e: Exception) {
            null
        }
        if (texto.isNullOrBlank()) texto = n.rodar("cat $ARQUIVO_DO_USB")
        val configurado = RegrasDoEspelhamento.usbConfigurado(texto)
        synchronized(travaDoUsb) {
            usbGuardado = configurado
            usbLidoEm = n.agora()
        }
        return configurado
    }

    private fun tarefaNoDisplay(pacote: String, display: Int): TarefaNaTela? =
        n.lista().tarefaDoPacoteNoDisplay(pacote, display)

    fun tarefaDoCarPlayNoDisplay(display: Int) = tarefaNoDisplay(PacotesDaCentral.CARPLAY, display)
    fun tarefaDoAndroidAutoNoDisplay(display: Int) = tarefaNoDisplay(PacotesDaCentral.ANDROID_AUTO, display)

    private fun carPlayNaLista(display: Int): Boolean {
        val lista = n.lista()
        return lista.tarefaDoPacoteNoDisplay(PacotesDaCentral.CARPLAY, display) != null ||
            lista.tarefasOnde { RegrasDoEspelhamento.ehCarPlay(it) }.any { it.display == display }
    }

    /** B3. */
    fun carPlayNoDisplay(display: Int): Boolean = n.semFalhar("carPlayNoDisplay($display)", false) {
        carPlayNaLista(display) || RegrasDoEspelhamento.ehCarPlay(telas.pacoteNoTopo(display))
    }

    /** B4: "janela do AA no display". */
    fun janelaDoAndroidAuto(display: Int): Boolean = n.semFalhar("janela do AA ($display)", false) {
        val lista = n.lista()
        lista.tarefaDoPacoteNoDisplay(PacotesDaCentral.ANDROID_AUTO, display) != null ||
            lista.tarefasOnde { RegrasDoEspelhamento.ehAndroidAuto(it) }.any { it.display == display } ||
            RegrasDoEspelhamento.ehAndroidAuto(telas.pacoteNoTopo(display))
    }

    /** B4: "pronto para o display". */
    private fun androidAutoProntoPara(display: Int): Boolean {
        if (n.midia.nucleoDaSessao()) {
            evidencia("núcleo da sessão", display)
            return true
        }
        if (!janelaDoAndroidAuto(display)) return false
        val motivo = when {
            RegrasDoEspelhamento.ehAndroidAuto(telas.pacoteNoTopo(display)) -> "frente do display"
            n.barra.rotaNativaDoAndroidAuto() -> "rota nativa"
            n.midia.audioTocando("pronto para $display") -> "áudio tocando"
            else -> null
        }
        if (motivo != null) evidencia(motivo, display)
        return motivo != null
    }

    private fun evidencia(motivo: String, display: Int) {
        if (logDaEvidencia.tentar(n.agora())) log.depuracao("AA pronto para o display $display: $motivo")
    }

    /** B4. */
    fun androidAutoNoDisplay(display: Int): Boolean = n.semFalhar("androidAutoNoDisplay($display)", false) {
        janelaDoAndroidAuto(display) && androidAutoProntoPara(display)
    }

    /** B5. */
    fun noDisplay(display: Int): Boolean = n.semFalhar("noDisplay($display)", false) {
        carPlayNoDisplay(display) || androidAutoNoDisplay(display) ||
            RegrasDoEspelhamento.pacoteDeEspelhamento(telas.pacoteNoTopo(display)) != null
    }

    /** B6: CarPlay vence Android Auto. */
    fun ativoNoDisplay(display: Int): String? = n.semFalhar("ativoNoDisplay($display)", null) {
        if (carPlayNaLista(display)) return@semFalhar PacotesDaCentral.CARPLAY
        if (janelaDoAndroidAuto(display)) {
            if (androidAutoProntoPara(display)) return@semFalhar PacotesDaCentral.ANDROID_AUTO
            log.depuracao("AA velho no display $display")
            return@semFalhar null
        }
        when (RegrasDoEspelhamento.pacoteDeEspelhamento(telas.pacoteNoTopo(display))) {
            PacotesDaCentral.CARPLAY -> PacotesDaCentral.CARPLAY
            PacotesDaCentral.ANDROID_AUTO -> if (androidAutoProntoPara(display)) PacotesDaCentral.ANDROID_AUTO else null
            else -> null
        }
    }

    // ── B7. destinos gravados ───────────────────────────────────────────────────────────────────

    fun destinoDoCarPlay(): Int = prefs.inteiro(ChavesDoLancador.DESTINO_DO_CARPLAY) ?: -1
    fun destinoDoAndroidAuto(): Int = prefs.inteiro(ChavesDoLancador.DESTINO_DO_ANDROID_AUTO) ?: -1
    fun carPlayQuerOPainel(): Boolean = destinoDoCarPlay() == 3
    fun androidAutoQuerOPainel(): Boolean = destinoDoAndroidAuto() == 3

    fun gravarDestinoDoCarPlay(display: Int, motivo: String) {
        prefs.gravar(mapOf(ChavesDoLancador.DESTINO_DO_CARPLAY to display))
        if (display == 0 || display == 3) n.rodar("setprop persist.haval.carplay.desired_display $display")
        n.diario("carplay_destino", mapOf("display" to display, "motivo" to motivo))
    }

    fun gravarDestinoDoAndroidAuto(display: Int) {
        prefs.gravar(mapOf(ChavesDoLancador.DESTINO_DO_ANDROID_AUTO to display))
    }

    // ── C. Android Auto ─────────────────────────────────────────────────────────────────────────

    /** C1: config do AA para o display (da dada, senão a gravada, senão a predefinida). */
    fun configDoAndroidAutoPara(display: Int, base: ConfigDoApp?): ConfigDoApp {
        val origem = base ?: telas.configuracao(PacotesDaCentral.ANDROID_AUTO)
            ?: PacotesDaCentral.predefinida(PacotesDaCentral.ANDROID_AUTO)!!
        val (l, a) = n.resolucao(display)
        return origem.copy(
            pacote = PacotesDaCentral.ANDROID_AUTO,
            atividade = PacotesDaCentral.ATIVIDADE_ANDROID_AUTO,
            display = display, x = 0, y = 0, largura = l, altura = a, ignorarAreaDoTema = true,
        )
    }

    fun limitesDoAndroidAuto(display: Int): IntArray = GeometriaDoLancador.limitesDoAndroidAuto(display, n.ajustes())

    /** A4: os dois serviços do AA. */
    fun prepararServicosDoAndroidAuto() {
        n.rodar(
            "am startservice --user 0 -a com.ts.androidauto.action.AndroidAutoService " +
                "-n com.ts.androidauto.projectionservice/.AndroidAutoService",
        )
        n.rodar(
            "am startservice --user 0 -a com.ts.androidauto.action.AndroidAutoRemoteUiService " +
                "-n com.ts.androidauto.app/.AndroidAutoRemoteUiService",
        )
    }

    /** A8: abre a atividade do AA no display. */
    private fun abrirAtividadeDoAndroidAuto(display: Int): String {
        val componente = "${PacotesDaCentral.ANDROID_AUTO}/${PacotesDaCentral.ATIVIDADE_ANDROID_AUTO}"
        return if (display == 0) {
            n.rodar("am start -n $componente --display 0 --windowingMode 1 -f 0x14000000")
        } else {
            n.rodar("am start --display $display --windowingMode 5 --activity-multiple-task -f 0x18000000 -n $componente")
        }
    }

    fun viewStateDoAndroidAuto(display: Int) =
        n.rodar("am broadcast -a ts.car.androidauto.view_state --es state foreground --ei displayId $display")

    fun focoDeVideoDoAndroidAuto(display: Int) =
        n.rodar(
            "am broadcast -a com.ts.androidauto.action.AndroidAutoService --es \"command\" \"requestVideoFocus\" " +
                "--ei \"displayId\" $display",
        )

    /** C3.3: foco do AA (sem o pedido de vídeo se o rádio nativo está ativo). */
    fun focoDoAndroidAuto(display: Int) {
        viewStateDoAndroidAuto(display)
        if (!n.midia.cederAoRadioNativo("foco do AA no $display")) focoDeVideoDoAndroidAuto(display)
    }

    /** C3. */
    suspend fun redimensionarEFocarAndroidAuto(tarefa: TarefaNaTela, display: Int, limites: IntArray) {
        if (display == 0) n.rodar("am stack set-windowing-mode ${tarefa.pilha} 1")
        if (tarefa.limites == null || !tarefa.limites.contentEquals(limites)) {
            telas.redimensionarPilha(tarefa.pilha, limites)
        } else {
            log.depuracao("AA já em ${limites.contentToString()}")
        }
        n.esperar(160)
        focoDoAndroidAuto(display)
    }

    /** C4: abre o AA num display (de 2-C1..C4, guardas e depuração). */
    suspend fun abrirAndroidAuto(config: ConfigDoApp, motivo: String) {
        val c = configDoAndroidAutoPara(config.display, config)
        val d = c.display
        val limites = telas.limitesEfetivos(c)
        val anterior = n.lista().tarefaDoPacote(PacotesDaCentral.ANDROID_AUTO)?.display
        log.info("AA → display $d ($motivo)")

        telas.vaoNoPainel(d, limites)
        gravarDestinoDoAndroidAuto(d)
        prepararServicosDoAndroidAuto()
        n.amb.canalAa.garantirLigado()

        if (d != 0) {
            telas.despejar(d, PacotesDaCentral.ANDROID_AUTO)
            n.barra.desmarcarRestaurado(PacotesDaCentral.ANDROID_AUTO)
        } else {
            n.barra.marcarRestaurado(PacotesDaCentral.ANDROID_AUTO)
        }

        tarefaDoAndroidAutoNoDisplay(d)?.let { ja ->
            redimensionarEFocarAndroidAuto(ja, d, limites)
            fecharJanelasDoAndroidAuto(exceto = ja.pilha)
            if (d == 3) superficieVelhaDoAndroidAuto(ja)
            avisarTroca(d, anterior, carPlay = false, motivo = motivo)
            return
        }

        val emOutro = n.lista().tarefaDoPacote(PacotesDaCentral.ANDROID_AUTO)
        if (emOutro != null) {
            telas.guardarLimites(PacotesDaCentral.ANDROID_AUTO, emOutro.display, emOutro.limites)
            val lista = n.lista()
            if (lista.tarefasNaPilha(emOutro.pilha) > 1) {
                trazerOutraTarefaDaPilha(emOutro.pilha, PacotesDaCentral.ANDROID_AUTO)
                n.esperar(220)
                abrirAtividadeDoAndroidAuto(d)
            } else if (n.falhou(telas.moverPilha(emOutro.pilha, d))) {
                abrirAtividadeDoAndroidAuto(d)
            }
        } else {
            abrirAtividadeDoAndroidAuto(d)
        }

        n.esperar(700)
        var achada = tarefaDoAndroidAutoNoDisplay(d)
        if (achada == null) {
            n.rodar("am force-stop ${PacotesDaCentral.ANDROID_AUTO}")
            n.esperar(650)
            prepararServicosDoAndroidAuto()
            abrirAtividadeDoAndroidAuto(d)
            n.esperar(900)
            achada = tarefaDoAndroidAutoNoDisplay(d)
        }
        if (achada != null) {
            redimensionarEFocarAndroidAuto(achada, d, limites)
            fecharJanelasDoAndroidAuto(exceto = achada.pilha)
            n.emSegundoPlano("focos do AA no $d") {
                n.esperar(500)
                focoDoAndroidAuto(d)
                n.esperar(900)
                focoDoAndroidAuto(d)
                if (d == 3) {
                    n.esperar(1200)
                    tarefaDoAndroidAutoNoDisplay(3)?.let { superficieVelhaDoAndroidAuto(it) }
                }
            }
        } else {
            log.erro("AA não apareceu no display $d ($motivo)")
        }
        avisarTroca(d, anterior, carPlay = false, motivo = motivo)
    }

    /** 2-E8: traz a outra tarefa da pilha para a frente; true se havia e o comando não falhou. */
    private fun trazerOutraTarefaDaPilha(pilha: Int, pacote: String): Boolean {
        val outra = n.lista().outraTarefaDaPilha(pilha, pacote) ?: return false
        val saida = n.rodar("am start -n ${outra.pacote}/${n.escapar(outra.atividade)} --display ${outra.display} -f 0x14000000")
        return !n.falhou(saida)
    }

    /** C5: em segundo plano. */
    fun reaplicarLimitesDoAndroidAutoNoPainel() {
        n.emSegundoPlano("reaplicar limites do AA no painel") {
            val t = tarefaDoAndroidAutoNoDisplay(3)
            if (t != null) {
                redimensionarEFocarAndroidAuto(t, 3, limitesDoAndroidAuto(3))
            } else {
                log.info("AA fora do painel: o deslocamento vale na próxima projeção")
            }
        }
    }

    /** C6: devolve "agiu" sempre que passou da checagem de superfície velha. */
    suspend fun superficieVelhaDoAndroidAuto(tarefa: TarefaNaTela): Boolean {
        if (!sondagemDoAndroidAuto.tentar(n.agora())) return false
        if (!RegrasDoEspelhamento.superficieVelha(lerSuperficie(CAMADA_DO_ANDROID_AUTO), CAMADA_DO_ANDROID_AUTO)) return false
        if (!reinicioDoAndroidAuto.tentar(n.agora())) return true
        log.aviso("superfície do AA no painel velha: reinício visual (pilha ${tarefa.pilha})")
        n.rodar("am force-stop ${PacotesDaCentral.ANDROID_AUTO}")
        n.esperar(650)
        prepararServicosDoAndroidAuto()
        abrirAtividadeDoAndroidAuto(3)
        n.esperar(1100)
        val nova = tarefaDoAndroidAutoNoDisplay(3)
        if (nova == null) log.aviso("AA não voltou ao painel") else redimensionarEFocarAndroidAuto(nova, 3, limitesDoAndroidAuto(3))
        n.esperar(900)
        log.info("superfície do AA depois: ${RegrasDoEspelhamento.bufferDaCamada(lerSuperficie(CAMADA_DO_ANDROID_AUTO), CAMADA_DO_ANDROID_AUTO)}")
        return true
    }

    private fun lerSuperficie(camada: String): String =
        n.rodar("dumpsys SurfaceFlinger | grep -A24 -F '$camada' || true")

    /** C7: fecha as janelas do AA (menos a pilha [exceto]). */
    fun fecharJanelasDoAndroidAuto(exceto: Int?) = fecharJanelas(PacotesDaCentral.ANDROID_AUTO, exceto)

    private fun fecharJanelas(pacote: String, exceto: Int?) {
        val lista = n.lista()
        val pilhas = lista.tarefasDoPacote(pacote).map { it.pilha }.distinct().filter { it != exceto }
        for (pilha in pilhas) {
            if (lista.tarefasNaPilha(pilha) > 1) {
                log.info("pilha $pilha de $pacote tem outras tarefas: mantida")
            } else {
                n.rodar("am stack remove $pilha")
            }
        }
    }

    /** 2-C7 para o AA. */
    fun fecharAndroidAuto() {
        gravarDestinoDoAndroidAuto(0)
        fecharJanelasDoAndroidAuto(exceto = null)
        telas.avisarDisplay(3)
    }

    /** C8. */
    suspend fun limparAndroidAutoSemConexao(motivo: String) = n.semFalhar("limpar AA sem conexão", Unit) {
        val agora = n.agora()
        if (!limpezaDoAndroidAuto.tentar(agora)) return@semFalhar
        if (usbConfigurado()) {
            aaVistoVelhoEm = 0
            return@semFalhar
        }
        if (RegrasDaMidia.canalAtivo(n.amb.canalAa.statusSeJaLigado())) {
            aaVistoVelhoEm = 0
            return@semFalhar
        }
        val dcm = try {
            n.dependencias.dcm.ler()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LeituraDoDcm(false, "")
        }
        if (dcm.androidAutoAtivo) {
            n.midia.marcarEvidenciaDoDcm()
            aaVistoVelhoEm = 0
            return@semFalhar
        }
        if (n.lista().tarefasDoPacote(PacotesDaCentral.ANDROID_AUTO).isEmpty()) {
            aaVistoVelhoEm = 0
            return@semFalhar
        }
        val remover = RegrasDoEspelhamento.removerAaSemConexao(
            usbConfigurado = false, canalAtivo = false, haTarefas = true,
            aaQuerOPainel = androidAutoQuerOPainel(),
            rotaNativa = n.barra.rotaNativaDoAndroidAuto(),
            vistoVelhoEm = aaVistoVelhoEm, agora = n.agora(), toleranciaMs = 30_000,
        )
        if (!remover) {
            if (aaVistoVelhoEm <= 0) aaVistoVelhoEm = n.agora()
            log.info("AA sem conexão ($motivo): ainda não remove")
            return@semFalhar
        }
        log.aviso("AA sem conexão há 30 s ($motivo): fechando as janelas")
        fecharJanelasDoAndroidAuto(exceto = null)
        aaVistoVelhoEm = 0
        telas.avisarDisplay(3)
        n.reposicionarBarra()
    }

    /** C9. */
    fun configDeAberturaAutomatica(config: ConfigDoApp): ConfigDoApp =
        RegrasDoEspelhamento.configDeAberturaAutomatica(
            config,
            prefs.booleano(Preferencias.AUTO_MOVE_PROJECTION_TO_CLUSTER, true),
        )

    /** Só em build de depuração. */
    fun androidAutoNoDisplayParaDepuracao(display: Int, motivo: String) {
        if (!n.amb.sistema.depuracao) {
            log.aviso("AA no display $display: só em build de depuração")
            return
        }
        if (display != 0 && display != 3) {
            log.aviso("AA no display $display: só 0 ou 3")
            return
        }
        n.emSegundoPlano("AA no $display (depuração)") { abrirAndroidAuto(configDoAndroidAutoPara(display, null), motivo) }
    }

    // ── K. CarPlay ──────────────────────────────────────────────────────────────────────────────

    private fun mudarMarcaDePreparacao(valor: Boolean) {
        if (marcaDePreparacao.getAndSet(valor) != valor) telas.avisarDisplay(3)
    }

    fun carPlayPreparandoOPainel(): Boolean = marcaDePreparacao.get()

    private fun idaAoPainelEmAndamento(): Boolean =
        marcaDePreparacao.get() || estadoDoCarPlay == EstadoDoCarPlay.PREPARANDO_O_PAINEL

    private fun voltaEmAndamento(): Boolean = estadoDoCarPlay == EstadoDoCarPlay.VOLTANDO_A_PRINCIPAL

    private fun atualizarEstado(reconciliar: Boolean) {
        val observado = RegrasDoEspelhamento.estadoObservado(carPlayNoDisplay(3), carPlayNoDisplay(0))
        val novo = RegrasDoEspelhamento.estadoAtualizado(estadoDoCarPlay, observado, reconciliar)
        if (novo != estadoDoCarPlay) {
            log.info("CarPlay: $estadoDoCarPlay → $novo")
            estadoDoCarPlay = novo
        }
    }

    /** K1: pedido de abrir (de `abrir`/`enviarParaDisplay`): 0 → principal; outro → painel (3). */
    suspend fun pedidoDeAbrirCarPlay(config: ConfigDoApp, motivo: String) {
        if (config.display == 0) carPlayNaPrincipal(config, motivo, gravarDestino = true)
        else carPlayNoPainel(config.copy(display = 3), motivo)
    }

    /** K2. Propaga exceção depois de passar a "em recuperação". */
    suspend fun carPlayNaPrincipal(config: ConfigDoApp?, motivo: String, gravarDestino: Boolean) {
        travaDoCarPlay.withLock {
            atualizarEstado(reconciliar = false)
            if (estadoDoCarPlay == EstadoDoCarPlay.NA_PRINCIPAL) {
                if (gravarDestino) gravarDestinoDoCarPlay(0, motivo)
                return
            }
            mudarMarcaDePreparacao(false)
            estadoDoCarPlay = EstadoDoCarPlay.VOLTANDO_A_PRINCIPAL
            try {
                iniciarCarPlayNoDisplay(config, 0, motivo, gravarDestino)
            } catch (e: Throwable) {
                estadoDoCarPlay = EstadoDoCarPlay.EM_RECUPERACAO
                throw e
            }
            atualizarEstado(reconciliar = true)
            if (estadoDoCarPlay != EstadoDoCarPlay.NA_PRINCIPAL) estadoDoCarPlay = EstadoDoCarPlay.EM_RECUPERACAO
        }
    }

    /** K3. */
    suspend fun carPlayNoPainel(config: ConfigDoApp?, motivo: String) {
        travaDoCarPlay.withLock {
            atualizarEstado(reconciliar = false)
            if (estadoDoCarPlay == EstadoDoCarPlay.NO_PAINEL) {
                gravarDestinoDoCarPlay(3, motivo)
                guardarCarPlayNoPainel(motivo)
                return
            }
            estadoDoCarPlay = EstadoDoCarPlay.PREPARANDO_O_PAINEL
            mudarMarcaDePreparacao(true)
            try {
                gravarDestinoDoCarPlay(3, motivo)
                n.esperar(260)
                iniciarCarPlayNoDisplay(config, 3, motivo, gravarDestino = false)
            } catch (e: Throwable) {
                estadoDoCarPlay = EstadoDoCarPlay.EM_RECUPERACAO
                throw e
            } finally {
                mudarMarcaDePreparacao(false)
            }
            atualizarEstado(reconciliar = true)
            if (estadoDoCarPlay != EstadoDoCarPlay.NO_PAINEL) estadoDoCarPlay = EstadoDoCarPlay.EM_RECUPERACAO
        }
    }

    /** A8: abre a atividade do CarPlay no display. */
    private fun abrirAtividadeDoCarPlay(display: Int): String {
        val componente = "${PacotesDaCentral.CARPLAY}/${PacotesDaCentral.ATIVIDADE_CARPLAY}"
        return if (display == 0) {
            n.rodar("am start -f 0x14000000 -n $componente")
        } else {
            n.rodar("am start --display $display --windowingMode 5 --activity-multiple-task -f 0x18000000 -n $componente")
        }
    }

    /** 2-E9: tira o foco do CarPlay num display trazendo a primeira tarefa comum dele. */
    private fun tirarFocoDoCarPlay(display: Int): Boolean {
        val comum = n.lista().primeiraTarefaComum(display, n.pacoteDoApp) ?: return false
        val saida = n.rodar(telas.comandoDeAbrir(comum.pacote, comum.atividade, display, 1, "0x14000000"))
        return !n.falhou(saida)
    }

    /** K4. */
    private suspend fun iniciarCarPlayNoDisplay(config: ConfigDoApp?, d: Int, motivo: String, gravarDestino: Boolean) {
        log.depuracao("CarPlay: config ${config?.pacote ?: "gravada/predefinida"} para o display $d")
        val limites = n.telaCheia(d)
        val anterior = n.lista().tarefaDoPacote(PacotesDaCentral.CARPLAY)?.display
        val inicio = relogio.parede() / 1000.0
        log.info("CarPlay → display $d ($motivo)")

        if (gravarDestino) gravarDestinoDoCarPlay(d, motivo)

        if (d == 3) {
            idaAoPainelEm = relogio.parede()
            val no0 = tarefaDoCarPlayNoDisplay(0)
            if (no0 != null && moverPilhaVivaDo0ParaOPainel(no0, limites)) return
        }

        val emOutro = n.lista().tarefaDoPacote(PacotesDaCentral.CARPLAY)
        if (emOutro != null && emOutro.display != d && d != 0) {
            telas.guardarLimites(PacotesDaCentral.CARPLAY, emOutro.display, emOutro.limites)
            if (n.lista().tarefasNaPilha(emOutro.pilha) > 1) {
                trazerOutraTarefaDaPilha(emOutro.pilha, PacotesDaCentral.CARPLAY)
                n.esperar(220)
            } else if (emOutro.display == 0) {
                tirarFocoDoCarPlay(0)
                n.esperar(300)
            }
        }

        if (d != 0) {
            telas.despejar(d, PacotesDaCentral.CARPLAY)
            n.barra.desmarcarRestaurado(PacotesDaCentral.CARPLAY)
        } else {
            n.barra.marcarRestaurado(PacotesDaCentral.CARPLAY)
        }

        prepararServicosDoCarPlay()
        pedirInterfaceDoCarPlay()

        if (d == 0) {
            val fora = n.lista().tarefasDoPacote(PacotesDaCentral.CARPLAY).firstOrNull { it.display != 0 }
            if (fora != null) {
                telas.moverPilha(fora.pilha, 0)
                n.esperar(700)
                val no0 = tarefaDoCarPlayNoDisplay(0)
                if (no0 != null) {
                    telaCheiaSemBroadcasts(no0, limites)
                    fecharJanelasDoCarPlay(exceto = no0.pilha)
                    avisarTroca(0, anterior, carPlay = true, motivo = motivo)
                    return
                }
                log.aviso("CarPlay não chegou ao display 0 pela pilha; abrindo a atividade")
            }
            fecharJanelasDoCarPlay(exceto = null)
            n.esperar(250)
        }

        var saida = abrirAtividadeDoCarPlay(d)
        n.esperar(700)
        var achada = tarefaDoCarPlayNoDisplay(d)

        if (achada == null) {
            val outro = n.lista().tarefaDoPacote(PacotesDaCentral.CARPLAY)?.takeIf { it.display != d }
            if (outro != null) {
                if (d != 0 && RegrasDoEspelhamento.reusoDaInstancia(saida)) {
                    if (n.lista().tarefasNaPilha(outro.pilha) > 1 && trazerOutraTarefaDaPilha(outro.pilha, PacotesDaCentral.CARPLAY)) {
                        n.esperar(220)
                        prepararServicosDoCarPlay()
                        saida = abrirAtividadeDoCarPlay(d)
                        n.esperar(700)
                        achada = tarefaDoCarPlayNoDisplay(d)
                    }
                    if (achada == null && tirarFocoDoCarPlay(outro.display)) {
                        n.esperar(220)
                        prepararServicosDoCarPlay()
                        saida = abrirAtividadeDoCarPlay(d)
                        n.esperar(700)
                        achada = tarefaDoCarPlayNoDisplay(d)
                    }
                    if (achada == null) {
                        log.erro("CarPlay preso no display ${outro.display} (reuso da instância)")
                        avisarTroca(d, anterior, carPlay = true, motivo = motivo)
                        return
                    }
                } else if (d == 0) {
                    n.rodar("am stack remove ${outro.pilha}")
                    n.esperar(250)
                    prepararServicosDoCarPlay()
                    abrirAtividadeDoCarPlay(d)
                    n.esperar(700)
                    achada = tarefaDoCarPlayNoDisplay(d)
                } else {
                    prepararServicosDoCarPlay()
                    abrirAtividadeDoCarPlay(d)
                    n.esperar(700)
                    achada = tarefaDoCarPlayNoDisplay(d)
                }
            }
        }

        if (achada != null) {
            if (d == 3) telaCheiaSemBroadcasts(achada, limites) else redimensionarEFocarCarPlay(achada, d, limites)
            fecharJanelasDoCarPlay(exceto = achada.pilha)
            val deNovo = tarefaDoCarPlayNoDisplay(d)
            if (d == 3) {
                n.esperar(650)
                if (deNovo != null) reafirmarSuperficieDoCarPlay(deNovo)
            }
        } else {
            log.erro("CarPlay não apareceu no display $d ($motivo)")
        }

        recuperarImagemDoCarPlay(d, inicio)
        avisarTroca(d, anterior, carPlay = true, motivo = motivo)
    }

    /** K5: devolve se moveu. */
    private suspend fun moverPilhaVivaDo0ParaOPainel(tarefa: TarefaNaTela, limites: IntArray): Boolean {
        val lista = n.lista()
        val seguro = RegrasDoEspelhamento.moverPilhaVivaEhSeguro(
            tarefa.display, lista.tarefasNaPilha(tarefa.pilha),
            lista.tarefaDoPacoteNoDisplay(PacotesDaCentral.CARPLAY, 3) != null,
        )
        if (!seguro) {
            log.info("CarPlay: pilha do 0 não pode ser movida (pilha ${tarefa.pilha})")
            return false
        }
        telas.despejar(3, PacotesDaCentral.CARPLAY)
        n.barra.desmarcarRestaurado(PacotesDaCentral.CARPLAY)
        prepararServicosDoCarPlay()
        val saida = telas.moverPilha(tarefa.pilha, 3)
        if (n.falhou(saida)) log.aviso("CarPlay: move-stack para o 3 falhou: $saida")
        n.esperar(700)
        val no3 = tarefaDoCarPlayNoDisplay(3) ?: return false
        telaCheiaSemBroadcasts(no3, limites)
        fecharJanelasDoCarPlay(exceto = no3.pilha)
        n.esperar(650)
        reafirmarSuperficieDoCarPlay(no3)
        avisarTroca(3, 0, carPlay = true, motivo = "pilha viva do 0 para o 3")
        return true
    }

    /** K6: só recria a janela quando não há tarefa no display e o logcat mostra problema de imagem. */
    private suspend fun recuperarImagemDoCarPlay(d: Int, inicioSegundos: Double) {
        if (tarefaDoCarPlayNoDisplay(d) != null) {
            log.depuracao("CarPlay no display $d: só confere")
            return
        }
        focoDoCarPlay(d)
        n.esperar(650)
        val saude = lerSaude(inicioSegundos)
        log.info("saúde do CarPlay: $saude")
        if (!saude.pedeRecriar) return
        if (tarefaDoCarPlayNoDisplay(d) != null) return

        val pilhasAntes = n.lista().tarefasDoPacote(PacotesDaCentral.CARPLAY).map { it.pilha }.toSet()
        prepararServicosDoCarPlay()
        if (d == 0) {
            fecharJanelasDoCarPlay(exceto = null)
            n.esperar(250)
        }
        abrirAtividadeDoCarPlay(d)
        n.esperar(900)
        if (d != 0) {
            val nova = n.lista().tarefaDoPacote(PacotesDaCentral.CARPLAY)
            if (nova != null && nova.pilha in pilhasAntes) {
                n.rodar("am stack remove ${nova.pilha}")
                n.esperar(300)
                abrirAtividadeDoCarPlay(d)
                n.esperar(900)
            }
        }
        val achada = tarefaDoCarPlayNoDisplay(d)
        if (achada != null) {
            redimensionarEFocarCarPlay(achada, d, n.telaCheia(d))
            fecharJanelasDoCarPlay(exceto = achada.pilha)
        }
        n.esperar(850)
        log.info("saúde do CarPlay depois: ${lerSaude(inicioSegundos)}")
    }

    private fun lerSaude(inicioSegundos: Double): SaudeDoCarPlay {
        val desde = RegrasDoEspelhamento.desdeDaSaude(inicioSegundos, relogio.parede() / 1000.0)
        val texto = n.rodar(
            "logcat -d -v threadtime,epoch -t 800 | grep -Ei " +
                "'cpScreen|NdkMediaCodec|MediaCodec|jsurface|setSurface|isCarPlayConnected|mCarPlayConnected|" +
                "notifyConnectedStatusChange|UsbCarplay|DeviceConnectedState|CarPlaySession|CarPlayIconManager|" +
                "MC-driver|DcController' || true",
        )
        return RegrasDoEspelhamento.saudeDoCarPlay(texto, desde)
    }

    /** K7. */
    fun fecharJanelasDoCarPlay(exceto: Int?) = fecharJanelas(PacotesDaCentral.CARPLAY, exceto)

    /** 2-C7 para o CarPlay. */
    fun fecharCarPlay() {
        fecharJanelasDoCarPlay(exceto = null)
        prepararServicosDoCarPlay()
        telas.avisarDisplay(3)
    }

    /** K8. */
    private suspend fun reafirmarSuperficieDoCarPlay(tarefa: TarefaNaTela) {
        if (!sondagemDoCarPlay.tentar(n.agora())) return
        if (!RegrasDoEspelhamento.superficieVelha(lerSuperficie(CAMADA_DO_CARPLAY), CAMADA_DO_CARPLAY)) return
        if (!reafirmacaoDoCarPlay.tentar(n.agora())) return
        log.aviso("superfície do CarPlay no painel velha: reafirmando (pilha ${tarefa.pilha})")
        n.rodar("setprop persist.haval.carplay.video.height 720")
        n.esperar(250)
        abrirAtividadeDoCarPlay(3)
        n.esperar(850)
        log.info("superfície do CarPlay depois: ${RegrasDoEspelhamento.bufferDaCamada(lerSuperficie(CAMADA_DO_CARPLAY), CAMADA_DO_CARPLAY)}")
    }

    /** K9: só com o canal ativado (status 2). */
    private suspend fun pedirInterfaceDoCarPlay() {
        val canal = n.amb.canalCp
        for (volta in 1..6) {
            if (canal.vivo()) break
            canal.pedirLigacao()
            n.esperar(120)
        }
        val status = canal.status()
        if (status != 2) {
            log.info("CarPlay: canal com status $status; não pede o modo normal")
            return
        }
        if (!canal.modoNormal()) log.aviso("CarPlay: modo normal falhou")
    }

    /** K10. */
    fun prepararServicosDoCarPlay() {
        n.rodar("setprop persist.haval.carplay.video.height 720")
        if (!RegrasDoEspelhamento.pidVivo(n.rodar("pidof com.ts.carplay"))) {
            n.rodar("am startservice -n com.ts.carplay/.CarPlayService")
        }
        if (!RegrasDoEspelhamento.pidVivo(n.rodar("pidof com.ts.carplay.app"))) {
            n.rodar("am startservice -n com.ts.carplay.app/.service.CarPlayRemoteService")
        }
    }

    /** K11. */
    private fun focoDoCarPlay(d: Int) {
        n.rodar("am broadcast -a ts.car.carplay.view_state --es state foreground --ei displayId $d")
        n.rodar(
            "am broadcast -a com.ts.carplay.action.VIDEO_FOCUS_CHANGE --es \"focus\" \"com.ts.carplay.app\" " +
                "--ei \"displayId\" $d",
        )
    }

    /** K12. */
    private suspend fun redimensionarEFocarCarPlay(tarefa: TarefaNaTela, d: Int, limites: IntArray) {
        if (d == 0) n.rodar("am stack set-windowing-mode ${tarefa.pilha} 1")
        telas.redimensionarPilha(tarefa.pilha, limites)
        n.esperar(120)
        focoDoCarPlay(d)
    }

    /** K13: sem broadcasts de vídeo (mantém a rota nativa). */
    private fun telaCheiaSemBroadcasts(tarefa: TarefaNaTela, limites: IntArray) {
        if (tarefa.limites == null || !tarefa.limites.contentEquals(limites)) telas.redimensionarPilha(tarefa.pilha, limites)
    }

    /** K14. */
    private fun avisarTroca(d: Int, anterior: Int?, carPlay: Boolean, motivo: String) {
        if (carPlay && d == 3) idaAoPainelEm = relogio.parede()
        telas.avisarDisplay(d)
        if (anterior != null && anterior != d) telas.avisarDisplay(anterior)
        if (d == 3) {
            try {
                n.barra.reabrirAposEspelhamentoNoPainel(motivo)
            } catch (e: Exception) {
                log.aviso("barra falhou ao reabrir: ${e.message}")
            }
        }
        if (d == 0) {
            telas.avisarDisplay(3)
            n.reposicionarBarra()
        }
    }

    // ── F. guardas do CarPlay no painel ─────────────────────────────────────────────────────────

    /** F1. */
    internal suspend fun conferirCarPlayNoPainel(motivo: String) {
        if (!carPlayQuerOPainel()) return
        if (idaAoPainelEmAndamento()) return
        if (tarefaDoCarPlayNoDisplay(3) != null) {
            vistoNoPainelEm = relogio.parede()
            log.depuracao("CarPlay no painel ($motivo)")
            return
        }
        if (tarefaDoCarPlayNoDisplay(0) != null) {
            if (vistoNo0Em <= 0) vistoNo0Em = relogio.parede()
            if (quarentenaDo0()) {
                val velocidade = RegrasDoEspelhamento.velocidade(n.amb.carro.ler(Sinais.CAR_BASIC_VEHICLE_SPEED.chave))
                log.info("CarPlay no 0 em quarentena ($motivo)")
                n.diario("carplay_quarentena", mapOf("motivo" to motivo, "velocidade" to velocidade))
                return
            }
        }
        val estavel = carPlayEstavelNo0()
        if (estavel == null) {
            if (n.lista().tarefasDoPacote(PacotesDaCentral.CARPLAY).isEmpty()) {
                val usb = observarUsb()
                if (instabilidade(usb, semTarefas = true)) return
                if (vistoNoPainelEm > 0 && relogio.parede() - vistoNoPainelEm in 0..60_000) {
                    recriarNoPainel()
                } else {
                    limparDestinoVelho("conferir: sem CarPlay")
                }
            }
            return
        }
        devolverDo0ParaOPainel(estavel)
    }

    /** F2. */
    fun guardarCarPlayNoPainel(motivo: String) {
        if (!carPlayQuerOPainel()) return
        if (!guardaPublicaDoCarPlay.tentar(n.agora())) {
            log.depuracao("guarda do CarPlay ignorada ($motivo): menos de 3,5 s")
            return
        }
        n.emSegundoPlano("guarda do CarPlay ($motivo)") {
            n.esperar(900)
            conferirCarPlayNoPainel(motivo)
            n.esperar(1800)
            conferirCarPlayNoPainel(motivo)
        }
    }

    /** F3 (de 2-G1). */
    fun guardaDoCarPlayPorJanela(pacote: String) {
        if (!carPlayQuerOPainel()) return
        if (!RegrasDoEspelhamento.guardaDoCarPlayConfere(pacote)) return
        if (!guardaDoCarPlayPorJanela.tentar(n.agora())) return
        val doApp = pacote == n.pacoteDoApp
        n.emSegundoPlano("guarda do CarPlay pela janela $pacote") {
            n.esperar(if (doApp) 650 else 1100)
            conferirCarPlayNoPainel("janela $pacote")
            n.esperar(if (doApp) 1500 else 2200)
            conferirCarPlayNoPainel("janela $pacote")
        }
    }

    /** F4: a tarefa do CarPlay estável (≥ 800 ms) no 0, ou null. */
    private suspend fun carPlayEstavelNo0(): TarefaNaTela? {
        val inicio = n.agora()
        var vistaDesde: Long? = null
        while (n.agora() - inicio < 3_000) {
            if (tarefaDoCarPlayNoDisplay(3) != null) {
                log.depuracao("CarPlay apareceu no painel durante a espera")
                return null
            }
            val no0 = tarefaDoCarPlayNoDisplay(0)
            if (no0 != null) {
                val desde = vistaDesde ?: n.agora().also { vistaDesde = it }
                if (n.agora() - desde >= 800) return no0
            } else {
                vistaDesde = null
            }
            n.esperar(300)
        }
        return null
    }

    /** F5. */
    internal suspend fun devolverDo0ParaOPainel(tarefa: TarefaNaTela) {
        val limites = n.telaCheia(3)
        idaAoPainelEm = relogio.parede()
        if (moverPilhaVivaDo0ParaOPainel(tarefa, limites)) return
        prepararServicosDoCarPlay()
        pedirInterfaceDoCarPlay()
        tirarFocoDoCarPlay(0)
        n.esperar(300)
        val saida = abrirAtividadeDoCarPlay(3)
        n.esperar(900)
        var no3 = tarefaDoCarPlayNoDisplay(3)
        if (no3 == null && RegrasDoEspelhamento.reusoDaInstancia(saida)) {
            tirarFocoDoCarPlay(0)
            n.esperar(350)
            prepararServicosDoCarPlay()
            abrirAtividadeDoCarPlay(3)
            n.esperar(900)
            no3 = tarefaDoCarPlayNoDisplay(3)
        }
        if (no3 == null) {
            log.erro("CarPlay não voltou ao painel")
            return
        }
        telaCheiaSemBroadcasts(no3, limites)
        n.esperar(650)
        reafirmarSuperficieDoCarPlay(no3)
        fecharJanelasDoCarPlay(exceto = no3.pilha)
        avisarTroca(3, 0, carPlay = true, motivo = "devolvido do 0")
    }

    /** F6. */
    private suspend fun recriarNoPainel() {
        if (!usbConfigurado()) {
            log.info("CarPlay sumiu do painel, mas sem USB de projeção")
            return
        }
        prepararServicosDoCarPlay()
        pedirInterfaceDoCarPlay()
        tirarFocoDoCarPlay(0)
        n.esperar(300)
        abrirAtividadeDoCarPlay(3)
        n.esperar(900)
        val no3 = tarefaDoCarPlayNoDisplay(3)
        if (no3 == null) {
            log.aviso("CarPlay não foi recriado no painel")
            return
        }
        redimensionarEFocarCarPlay(no3, 3, n.telaCheia(3))
        avisarTroca(3, null, carPlay = true, motivo = "recriado no painel")
    }

    /** F7. */
    fun protegerCartaoDoArNaTransicao(): Boolean = n.semFalhar("proteger cartão do ar", false) {
        carPlayQuerOPainel() && (
            marcaDePreparacao.get() ||
                RegrasDoEspelhamento.toleranciaDeReconexao(relogio.parede(), usbDesconectouEm, usbConfigurouEm, 5 * 60_000L) ||
                quarentenaDo0()
            )
    }

    /** F8: observa o USB; a primeira observação só estabelece a base. */
    internal fun observarUsb(): Boolean {
        val usb = usbConfigurado()
        val antes = usbObservado
        if (antes != usb) {
            usbObservado = usb
            log.info("USB de projeção: ${if (usb) "configurado" else "desconectado"}")
            n.diario("usb_projecao", mapOf("configurado" to usb))
            if (antes != null) {
                if (usb) {
                    usbConfigurouEm = relogio.parede()
                } else {
                    usbDesconectouEm = relogio.parede()
                    vistoNo0Em = 0
                }
            }
        }
        return usb
    }

    private fun quarentenaDo0(): Boolean =
        RegrasDoEspelhamento.quarentenaDo0(relogio.parede(), usbDesconectouEm, usbConfigurouEm, vistoNo0Em, 5 * 60_000L)

    private fun instabilidade(usb: Boolean, semTarefas: Boolean): Boolean =
        RegrasDoEspelhamento.instabilidade(
            carPlayQuerOPainel(), semTarefas, usb, relogio.parede(), usbDesconectouEm, usbConfigurouEm, 30_000L,
        )

    /** F9. */
    private fun limparDestinoVelho(motivo: String) {
        if (destinoDoCarPlay() != 3) return
        prefs.gravar(mapOf(ChavesDoLancador.DESTINO_DO_CARPLAY to 0))
        vistoNoPainelEm = 0
        n.rodar("setprop persist.haval.carplay.desired_display 0")
        log.info("destino velho do CarPlay limpo ($motivo)")
        n.diario("carplay_destino_velho", mapOf("motivo" to motivo))
    }

    /** F10. */
    private fun reconciliarDestino() {
        if (voltaEmAndamento()) return
        val lista = n.lista()
        val no3 = lista.tarefaDoPacoteNoDisplay(PacotesDaCentral.CARPLAY, 3)
        val no0 = lista.tarefaDoPacoteNoDisplay(PacotesDaCentral.CARPLAY, 0)
        if (no3 != null && no0 == null && destinoDoCarPlay() != 3) gravarDestinoDoCarPlay(3, "tarefa real no painel")
    }

    /** F11. */
    private fun tirarDuplicataDo0(no3: TarefaNaTela, no0: TarefaNaTela) {
        if (!duplicataDoCarPlay.tentar(n.agora())) return
        if (n.lista().tarefasNaPilha(no0.pilha) > 1) {
            log.info("CarPlay duplicado no 0 numa pilha mista: mantido")
            return
        }
        n.rodar("am stack remove ${no0.pilha}")
        avisarTroca(3, 0, carPlay = true, motivo = "duplicata no 0 (painel na pilha ${no3.pilha})")
    }

    // ── G. guardas do Android Auto no painel ────────────────────────────────────────────────────

    /** G1. */
    internal suspend fun conferirAndroidAutoNoPainel(motivo: String, acao: AcaoDaGuardaDoAa) {
        if (ativoNoDisplay(3) == PacotesDaCentral.CARPLAY) return
        val no3 = tarefaDoAndroidAutoNoDisplay(3)
        if (no3 != null) {
            if (acao == AcaoDaGuardaDoAa.FOCO_DE_VIDEO) {
                if (!superficieVelhaDoAndroidAuto(no3)) focoDoAndroidAuto(3)
            } else {
                log.depuracao("AA no painel ($motivo)")
            }
            return
        }
        if (!androidAutoQuerOPainel()) return
        abrirAndroidAuto(configDoAndroidAutoPara(3, null), "restaurar no painel: $motivo")
    }

    private fun elegivelParaGuardasDoAndroidAuto(): Boolean =
        RegrasDoEspelhamento.aaElegivelParaGuardas(ativoNoDisplay(3), androidAutoQuerOPainel())

    /** G2 (atrasos curtos). A conferência de elegibilidade vai ao shell: roda em segundo plano. */
    fun guardarAndroidAutoNoPainel(motivo: String) = guardaPublicaDoAndroidAuto(motivo, 450, 950)

    /** G2 (atrasos longos). */
    fun guardarAndroidAutoNoPainelAposJanelaNativa(motivo: String) = guardaPublicaDoAndroidAuto(motivo, 1600, 2200)

    private fun guardaPublicaDoAndroidAuto(motivo: String, primeira: Long, segunda: Long) {
        n.emSegundoPlano("guarda do AA ($motivo)") {
            if (!elegivelParaGuardasDoAndroidAuto()) return@emSegundoPlano
            if (!guardaDoAndroidAuto.tentar(n.agora())) {
                log.depuracao("guarda do AA ignorada ($motivo): menos de 2,5 s")
                return@emSegundoPlano
            }
            n.esperar(primeira)
            conferirAndroidAutoNoPainel(motivo, AcaoDaGuardaDoAa.CONFERIR)
            n.esperar(segunda)
            conferirAndroidAutoNoPainel(motivo, AcaoDaGuardaDoAa.CONFERIR)
        }
    }

    /**
     * G3 (de 2-G1). A volta do AA ao painel é conferida antes da elegibilidade: com o AA fora do
     * painel ele não é "o ativo no 3", e a volta nunca aconteceria.
     */
    internal suspend fun guardaDoAndroidAutoPorJanela(pacote: String) {
        val querPainel = androidAutoQuerOPainel()
        if (querPainel && RegrasDoEspelhamento.ehAndroidAuto(pacote)) {
            val displayDoAa = n.lista().tarefasDoPacote(PacotesDaCentral.ANDROID_AUTO).firstOrNull { it.display != 3 }?.display
            if (RegrasDoEspelhamento.aaDeveVoltarAoPainel(pacote, querPainel, janelaDoAndroidAuto(3), displayDoAa)) {
                n.esperar(250)
                conferirAndroidAutoNoPainel("janela $pacote fora do painel", AcaoDaGuardaDoAa.CONFERIR)
                return
            }
        }
        if (!elegivelParaGuardasDoAndroidAuto()) return
        val acao = RegrasDoEspelhamento.acaoDaGuardaDoAa(pacote, n.pacoteDoApp) ?: return
        val agora = n.agora()
        synchronized(travaDaJanelaDoAa) {
            if (RegrasDoEspelhamento.pularGuardaDoAa(ultimaJanelaDoAa, ultimaAcaoDoAa, ultimaGuardaDoAaEm, pacote, acao, agora, 4_000)) return
            ultimaJanelaDoAa = pacote
            ultimaAcaoDoAa = acao
            ultimaGuardaDoAaEm = agora
        }
        val esperas = if (acao == AcaoDaGuardaDoAa.CONFERIR) longArrayOf(1600, 2200) else longArrayOf(500, 1200, 3500, 4000)
        for (espera in esperas) {
            n.esperar(espera)
            conferirAndroidAutoNoPainel("janela $pacote", acao)
        }
    }

    // ── H. vigias e partida ─────────────────────────────────────────────────────────────────────

    /** H1: uma vez por processo. */
    fun iniciarVigiaDoCarPlayNoPainel() {
        if (!vigiaIniciada.compareAndSet(false, true)) return
        n.emSegundoPlano("vigia do CarPlay no painel") {
            comecoDaVigia()
            n.esperar(4_000)
            while (true) {
                try {
                    voltaDaVigia()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    log.erro("vigia do CarPlay", e)
                    n.diario("carplay_vigia_falhou", mapOf("erro" to e.toString()))
                }
                n.esperar(1_000)
            }
        }
    }

    internal fun comecoDaVigia() {
        val destino = destinoDoCarPlay()
        if (destino == 3 && tarefaDoCarPlayNoDisplay(3) == null) {
            toleranciaDePartidaAte = n.agora() + 65_000
            n.rodar("setprop persist.haval.carplay.desired_display 3")
            n.diario("carplay_tolerancia_de_partida", mapOf("segundos" to 65))
        } else if (destino == 0 || destino == 3) {
            n.rodar("setprop persist.haval.carplay.desired_display $destino")
        }
    }

    /** H1.2: uma volta do laço. */
    internal suspend fun voltaDaVigia() {
        reconciliarDestino()
        if (!carPlayQuerOPainel()) return
        if (idaAoPainelEmAndamento()) return
        val usb = observarUsb()
        val tarefas = n.lista().tarefasDoPacote(PacotesDaCentral.CARPLAY)
        if (tarefas.isEmpty()) {
            vistoNo0Em = 0
            val tolerancia = n.agora() < toleranciaDePartidaAte
            if (tolerancia && !usb) {
                log.depuracao("vigia: partida sem USB ainda; mantém o destino")
                return
            }
            val instavel = instabilidade(usb, semTarefas = true)
            val vistoHaPouco = vistoNoPainelEm > 0 && relogio.parede() - vistoNoPainelEm in 0..60_000
            if (!tolerancia && !instavel && !vistoHaPouco) {
                limparDestinoVelho("vigia: sem CarPlay")
                return
            }
            if (instavel) {
                log.depuracao("vigia: USB instável; mantém")
                return
            }
            if (!restauracaoDoCarPlay.tentar(n.agora())) return
            recriarNoPainel()
            return
        }
        val no3 = tarefas.firstOrNull { it.display == 3 }
        val no0 = tarefas.firstOrNull { it.display == 0 }
        if (no3 != null) {
            vistoNo0Em = 0
            if (telas.pacoteNoTopo(0) == n.pacoteDoApp) {
                log.depuracao("vigia: CarPlay no painel com o app na frente do 0")
            } else {
                vistoNoPainelEm = relogio.parede()
            }
            if (no0 != null && no0.pilha != no3.pilha) tirarDuplicataDo0(no3, no0)
            return
        }
        if (no0 != null) {
            if (vistoNo0Em <= 0) {
                vistoNo0Em = relogio.parede()
                n.diario("carplay_visto_no_0", emptyMap())
            }
            if (quarentenaDo0()) {
                log.depuracao("vigia: CarPlay no 0 em quarentena")
                return
            }
            if (!restauracaoDoCarPlay.tentar(n.agora())) return
            devolverDo0ParaOPainel(no0)
        }
    }

    /** H3: uma vez por boot. */
    fun iniciarPartidaDoCarPlay() {
        if (!partidaRodando.compareAndSet(false, true)) return
        n.emSegundoPlano("partida do CarPlay") {
            try {
                partidaDoCarPlay()
            } finally {
                partidaRodando.set(false)
            }
        }
    }

    internal suspend fun partidaDoCarPlay() {
        val token = RegrasDoEspelhamento.tokenDoBoot(n.rodar("cat /proc/sys/kernel/random/boot_id"))
            ?: "unknown-${relogio.parede()}"
        if (prefs.texto(ChavesDoLancador.TOKEN_DA_PARTIDA_DO_CARPLAY) == token) {
            log.info("partida do CarPlay já feita neste boot")
            return
        }
        val gravarToken = { prefs.gravar(mapOf(ChavesDoLancador.TOKEN_DA_PARTIDA_DO_CARPLAY to token)) }
        repeat(30) {
            val dcm = try {
                n.dependencias.dcm.ler()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LeituraDoDcm(false, "")
            }
            if (dcm.androidAutoAtivo) {
                n.midia.marcarEvidenciaDoDcm()
                log.info("partida: Android Auto ativo pelo DCM; CarPlay fica quieto")
                gravarToken()
                return
            }
            val manterPainel = destinoDoCarPlay() == 3
            if (tarefaDoCarPlayNoDisplay(0) != null) {
                if (manterPainel) n.rodar("setprop persist.haval.carplay.desired_display 3")
                else gravarDestinoDoCarPlay(0, "partida")
                gravarToken()
                return
            }
            if (!usbConfigurado()) {
                n.esperar(2_000)
                return@repeat
            }
            try {
                carPlayNaPrincipal(null, "partida", gravarDestino = !manterPainel)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.erro("partida do CarPlay", e)
            }
            if (tarefaDoCarPlayNoDisplay(0) != null) {
                gravarToken()
                return
            }
            n.esperar(2_000)
        }
        log.aviso("partida do CarPlay: 30 tentativas sem CarPlay no 0; tenta na próxima partida do serviço")
    }

    companion object {
        const val ARQUIVO_DO_USB = "/sys/class/android_usb/android0/state"
        const val CAMADA_DO_CARPLAY =
            "SurfaceView - com.ts.carplay.app/com.ts.carplay.app.ui.display.view.CarPlayDisplayActivity"
        const val CAMADA_DO_ANDROID_AUTO =
            "SurfaceView - com.ts.androidauto.app/com.ts.androidauto.app.display.AapActivity"
    }
}
