// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.telas

import br.com.deepgwt.veiculo.Sinais
import java.util.concurrent.atomic.AtomicLong

/** Parte 4: mídia do Android Auto (canal de comandos, volante, barra, painel). */
internal class MotorDaMidia(private val n: NucleoDoLancador) {

    private val log get() = n.amb.registro
    private val canal get() = n.amb.canalAa
    private val esp get() = n.espelhamento

    private val travaDoAudio = Any()
    private var audioLidoEm: Long? = null
    private var audioGuardado = false

    private val travaDoAtivo = Any()
    private var ativoGuardadoEm: Long? = null
    private var ativoGuardado = false

    /** A5: horário (monotônico) da última leitura do DCM com AA ativo. */
    @Volatile private var evidenciaDoDcmEm = 0L

    private val logDoRadio = IntervaloMinimo(3_000)
    private val reconexao = IntervaloMinimo(4_000)

    private val travaDoVolante = Any()
    private var ultimaTeclaAceita: TeclaAceita? = null

    /** E5/E6: cada tecla nova invalida a reconciliação/reserva pendente. */
    private val geracao = AtomicLong()

    @Volatile var mudoEm = 0L
        private set

    // ── A. estado ───────────────────────────────────────────────────────────────────────────────

    /** A3. */
    fun cederAoRadioNativo(motivo: String): Boolean = n.semFalhar("ceder ao rádio", false) {
        if (n.barra.rotaNativaDoAndroidAuto()) return@semFalhar false
        val estado = lerDoCarro(Sinais.SYS_RADIO_PLAY_STATE.chave)
        val fonte = lerDoCarro(Sinais.SYS_BASIC_AUDIO_SOURCE_APP.chave)
        val cede = RegrasDaMidia.cedeAoRadio(false, estado, fonte)
        if (cede && logDoRadio.tentar(n.agora())) log.info("cede ao rádio nativo ($motivo): estado=$estado fonte=$fonte")
        cede
    }

    private fun lerDoCarro(chave: String): String? =
        try {
            n.amb.carro.lerDoCarro(chave)
        } catch (e: Exception) {
            null
        }

    /** A4: guardado por 3 s, salvo [forcarLeitura]. */
    fun audioTocando(motivo: String, forcarLeitura: Boolean = false): Boolean = n.semFalhar("áudio do AA", false) {
        if (cederAoRadioNativo(motivo)) return@semFalhar false
        if (!forcarLeitura) {
            synchronized(travaDoAudio) {
                val lido = audioLidoEm
                val agora = n.agora()
                if (lido != null && agora >= lido && agora - lido < 3_000) return@semFalhar audioGuardado
            }
        }
        val texto = if (n.amb.shell.disponivel()) {
            n.rodar("dumpsys audio 2>/dev/null | grep -i 'USAGE_AAUTO_MEDIA' || true")
        } else {
            ""
        }
        val tocando = RegrasDaMidia.audioDoAaTocando(texto)
        synchronized(travaDoAudio) {
            audioGuardado = tocando
            audioLidoEm = n.agora()
        }
        tocando
    }

    fun marcarEvidenciaDoDcm() {
        evidenciaDoDcmEm = n.agora()
    }

    private fun evidenciaDoDcmValida() = RegrasDaMidia.evidenciaDoDcmValida(evidenciaDoDcmEm, n.agora())

    /** A6 (núcleo, sem log): evidência do DCM, USB de projeção ou canal ativo. */
    fun nucleoDaSessao(): Boolean =
        evidenciaDoDcmValida() || esp.usbConfigurado() || RegrasDaMidia.canalAtivo(canal.statusSeJaLigado())

    /** A6. */
    fun sessaoPronta(motivo: String): Boolean = n.semFalhar("sessão pronta", false) {
        if (cederAoRadioNativo(motivo)) return@semFalhar false
        if (nucleoDaSessao()) return@semFalhar true
        if (haTarefaDoAndroidAuto()) {
            log.depuracao("sessão do AA pronta pela tarefa ($motivo)")
            return@semFalhar true
        }
        audioTocando(motivo)
    }

    private fun haTarefaDoAndroidAuto(): Boolean =
        n.lista().tarefasOnde { RegrasDoEspelhamento.ehAndroidAuto(it) }.isNotEmpty()

    /** A7. */
    fun canalAtivoSeJaLigado(motivo: String): Boolean = n.semFalhar("canal ativo ($motivo)", false) {
        RegrasDaMidia.canalAtivo(canal.statusSeJaLigado())
    }

    /** A8. */
    fun temJanelaDoAndroidAuto(): Boolean = n.semFalhar("janela do AA", false) {
        if (cederAoRadioNativo("janela do AA")) return@semFalhar false
        haTarefaDoAndroidAuto()
    }

    /** A9: AA ativo para a mídia (volante e painel). */
    fun androidAutoAtivoParaMidia(): Boolean = n.semFalhar("AA ativo para a mídia", false) {
        if (cederAoRadioNativo("AA ativo")) return@semFalhar guardarAtivo(false)
        val barra = n.barra
        if (barra.rotaNativaDoAndroidAuto()) return@semFalhar guardarAtivo(true)
        if (RegrasDaMidia.aaPelaBarra(barra.pacoteDaMidia, barra.espelhamentoNoPainel)) return@semFalhar guardarAtivo(true)
        synchronized(travaDoAtivo) {
            val em = ativoGuardadoEm
            val agora = n.agora()
            if (em != null && agora >= em && agora - em <= 1_500) return@semFalhar ativoGuardado
        }
        val ativo = RegrasDaMidia.aaAtivoPelaSessao(
            ativoNoPainel = { esp.ativoNoDisplay(3) },
            nucleoDaSessao = ::nucleoDaSessao,
            canal = { canal.statusSeJaLigado() },
            audio = { audioTocando("AA ativo") },
            haTarefaDoAa = ::haTarefaDoAndroidAuto,
        )
        guardarAtivo(ativo)
    }

    private fun guardarAtivo(valor: Boolean): Boolean {
        synchronized(travaDoAtivo) {
            ativoGuardado = valor
            ativoGuardadoEm = n.agora()
        }
        return valor
    }

    // ── B. reconectar o aparelho ────────────────────────────────────────────────────────────────

    private suspend fun reconectarAparelho() {
        val status = canal.status()
        if (status != null && status !in setOf(-1, 5, 6, 10)) return
        if (!reconexao.tentar(n.agora())) {
            log.depuracao("reconexão do aparelho do AA: menos de 4 s")
            return
        }
        val escolhido = RegrasDaMidia.aparelhoParaReconectar(canal.aparelhos().orEmpty())
        if (escolhido == null) {
            log.info("reconexão do AA: nenhum aparelho candidato")
            return
        }
        if (escolhido.semFio) canal.ouvirSemFio(escolhido) else canal.conectar(escolhido)
        n.esperar(650)
        log.info("reconexão do AA: status agora ${RegrasDaMidia.nomeDoEnlace(canal.status())}")
    }

    // ── C. preparar o alvo ──────────────────────────────────────────────────────────────────────

    /** C2 (V19). */
    fun displayAlvo(): Int = RegrasDaMidia.displayAlvo(
        esp.janelaDoAndroidAuto(3), esp.janelaDoAndroidAuto(0), esp.destinoDoAndroidAuto(),
    )

    private suspend fun garantirCanal() {
        if (!canal.vivo()) {
            canal.garantirLigado()
            n.esperar(180)
        }
    }

    /** C1. */
    suspend fun prepararAlvo(motivo: String, display: Int?, pedirFocoDeVideo: Boolean): Boolean =
        n.semFalhar("preparar alvo ($motivo)", false) {
            if (cederAoRadioNativo(motivo)) return@semFalhar false
            val alvo = display ?: displayAlvo()
            if (esp.ativoNoDisplay(alvo) == PacotesDaCentral.CARPLAY) {
                log.info("mídia do AA ($motivo): o CarPlay está no display $alvo")
                return@semFalhar false
            }
            if (!esp.janelaDoAndroidAuto(alvo) && !sessaoPronta(motivo) && !n.barra.rotaNativaDoAndroidAuto()) {
                log.info("mídia do AA ($motivo): sem janela, sem sessão, sem rota nativa")
                return@semFalhar false
            }
            if (!canal.vivo()) {
                canal.garantirLigado()
                n.esperar(180)
                if (!canal.vivo()) {
                    esp.prepararServicosDoAndroidAuto()
                    canal.garantirLigado()
                    n.esperar(180)
                }
            } else {
                log.depuracao("canal do AA já vivo")
            }
            if (canal.vivo()) reconectarAparelho()
            esp.viewStateDoAndroidAuto(alvo)
            if (pedirFocoDeVideo) esp.focoDeVideoDoAndroidAuto(alvo)
            n.esperar(90)
            true
        }

    /** C3. */
    fun preferirTeclaAap(motivo: String): Boolean = !canalAtivoSeJaLigado(motivo) && sessaoPronta(motivo)

    // ── M. comandos ─────────────────────────────────────────────────────────────────────────────

    /** M1. */
    private suspend fun teclaAap(tecla: Int): Boolean {
        val ordinal = RegrasDaMidia.ordinalAap(tecla) ?: return false
        garantirCanal()
        val desceu = canal.tecla(ordinal, TeclasDaMidia.DOWN)
        n.esperar(70)
        val subiu = canal.tecla(ordinal, TeclasDaMidia.UP)
        return desceu && subiu
    }

    /** M2. */
    suspend fun pularFaixa(paraFrente: Boolean, jaPreparado: Boolean): Boolean = n.semFalhar("pular faixa", false) {
        if (!jaPreparado && !prepararAlvo("pular faixa", null, true)) return@semFalhar false
        teclaAap(if (paraFrente) TeclasDaMidia.NEXT else TeclasDaMidia.PREVIOUS)
    }

    /** M3. */
    suspend fun tocarOuPausarPorTeclaAap(tocandoAgora: Boolean): Boolean = n.semFalhar("tocar/pausar AAP", false) {
        if (!prepararAlvo("tocar/pausar AAP", null, true)) return@semFalhar false
        teclaAap(if (tocandoAgora) TeclasDaMidia.PAUSE else TeclasDaMidia.PLAY)
    }

    /** M4. */
    suspend fun alternarPorTeclaAap(): Boolean = n.semFalhar("alternar AAP", false) {
        if (!prepararAlvo("alternar AAP", null, true)) return@semFalhar false
        teclaAap(TeclasDaMidia.PLAY_PAUSE)
    }

    /** M5. */
    suspend fun tocarOuPausarDireto(tocandoAgora: Boolean): Boolean =
        direto(if (tocandoAgora) TeclasDaMidia.PAUSE else TeclasDaMidia.PLAY, "tocar/pausar direto")

    /** M5 e o "direto para um alvo" de E5: `PAUSE` → pause (29); `PLAY` → play (28). */
    private suspend fun direto(tecla: Int, motivo: String): Boolean = n.semFalhar(motivo, false) {
        if (cederAoRadioNativo(motivo)) return@semFalhar false
        if (!prepararAlvo(motivo, null, true)) return@semFalhar false
        garantirCanal()
        val musicaAntes = canal.musica()
        val progressoAntes = canal.progresso()
        n.esperar(520)
        log.depuracao(
            "AA direto: música ${RegrasDaMidia.nomeDaMusica(musicaAntes)}→${RegrasDaMidia.nomeDaMusica(canal.musica())}, " +
                "progresso $progressoAntes→${canal.progresso()}",
        )
        val enviado = if (tecla == TeclasDaMidia.PAUSE) canal.pausar() else canal.tocar()
        n.esperar(120)
        log.depuracao("AA direto: música agora ${RegrasDaMidia.nomeDaMusica(canal.musica())}")
        enviado
    }

    // ── E. volante ──────────────────────────────────────────────────────────────────────────────

    /** E1–E7: true = o app não deve tratar mais esta tecla. */
    fun teclaDoVolante(tecla: Int, acao: Int): Boolean = n.semFalhar("tecla do volante $tecla", false) {
        if (tecla !in TeclasDaMidia.DO_VOLANTE) return@semFalhar false
        if (acao != TeclasDaMidia.DOWN && acao != TeclasDaMidia.UP) return@semFalhar false
        if (!androidAutoAtivoParaMidia()) return@semFalhar false

        val agora = n.agora()
        synchronized(travaDoVolante) {
            if (RegrasDaMidia.duplicataDoVolante(ultimaTeclaAceita, tecla, acao, agora)) {
                log.depuracao("tecla $tecla/$acao duplicada")
                return@semFalhar true
            }
            ultimaTeclaAceita = TeclaAceita(tecla, acao, agora)
        }

        val barra = n.barra
        when (tecla) {
            TeclasDaMidia.MUTE, TeclasDaMidia.VOLUME_MUTE -> {
                mudoEm = agora
                if (acao == TeclasDaMidia.UP && barra.rotaNativaDoAndroidAuto()) {
                    n.emSegundoPlano("mudo do AA") {
                        val ok = barra.alternarMudoDoAndroidAuto()
                        log.info("mudo do AA pela rota nativa: $ok")
                    }
                } else {
                    log.depuracao("mudo do volante $acao")
                }
            }
            TeclasDaMidia.PLAY_PAUSE, TeclasDaMidia.PLAY, TeclasDaMidia.PAUSE, TeclasDaMidia.CENTRAL_TOCAR_PAUSAR ->
                if (acao == TeclasDaMidia.UP) tocarOuPausarPeloVolante(tecla, acao)
            TeclasDaMidia.NEXT, TeclasDaMidia.PREVIOUS ->
                if (acao == TeclasDaMidia.UP && barra.rotaNativaDoAndroidAuto()) pularPeloVolante(tecla)
        }
        true
    }

    /** E5. */
    private fun tocarOuPausarPeloVolante(tecla: Int, acao: Int) {
        val barra = n.barra
        val rota = barra.rotaNativaDoAndroidAuto()
        val tocandoNativo = if (rota) barra.rotaNativaTocando() else null
        val alvo = RegrasDaMidia.alvoDoVolante(tecla, tocandoNativo, barra.tocando) ?: return
        barra.dicaDeReproducao(PACOTE_DA_MIDIA_DO_AA, alvo)
        if (!RegrasDaMidia.reconciliarVolante(tecla, acao, true, rota, esp.androidAutoQuerOPainel())) return
        val minha = geracao.incrementAndGet()
        n.emSegundoPlano("reconciliar tocar/pausar") {
            n.esperar(900)
            if (geracao.get() != minha) return@emSegundoPlano
            val enviou = tentarAlvo(alvo)
            if (alvo || enviou) return@emSegundoPlano
            n.esperar(2_400)
            if (geracao.get() != minha) return@emSegundoPlano
            tentarAlvo(alvo)
        }
    }

    private suspend fun tentarAlvo(tocar: Boolean): Boolean {
        val barra = n.barra
        return if (barra.rotaNativaDoAndroidAuto()) {
            barra.alvoPelaRotaNativa(tocar, "reconciliação do volante")
        } else {
            direto(if (tocar) TeclasDaMidia.PLAY else TeclasDaMidia.PAUSE, "direto para o alvo")
        }
    }

    /** E6 (com a rota nativa ativa). */
    private fun pularPeloVolante(tecla: Int) {
        val barra = n.barra
        barra.zerarProgressoAposTrocaDeFaixa("volante")
        val minha = geracao.incrementAndGet()
        val assinatura = assinaturaDaMidia()
        n.emSegundoPlano("reserva do pular") {
            n.esperar(900)
            if (geracao.get() != minha) return@emSegundoPlano
            if (assinaturaDaMidia() != assinatura) return@emSegundoPlano
            garantirCanal()
            val enviou = if (tecla == TeclasDaMidia.NEXT) canal.proxima() else canal.anterior()
            if (enviou) barra.zerarProgressoAposTrocaDeFaixa("reserva do volante")
        }
    }

    private fun assinaturaDaMidia(): String {
        val b = n.barra
        return "${b.pacoteDaMidia}|${b.tituloDaMidia}|${b.artistaDaMidia}|${b.albumDaMidia}|${b.duracaoDaMidiaMs}"
    }

    // ── F. painel e acessibilidade ──────────────────────────────────────────────────────────────

    /** F1: 1 = anterior, 2 = próxima; com o AA ativo só registra. */
    fun comandoDeMidiaDoPainel(comando: Int): Boolean {
        if (comando != 1 && comando != 2) return false
        if (!androidAutoAtivoParaMidia()) return false
        log.info("comando de mídia do painel $comando com o AA ativo (só registro)")
        return true
    }

    /** F2. */
    fun teclaMereceDiagnostico(tecla: Int): Boolean = tecla in TeclasDaMidia.DO_VOLANTE

    /** F2 (consulta; ver D1/D11). */
    fun mensagemDoPainelMereceDiagnostico(mensagem: Int): Boolean =
        mensagem in 130..140 && androidAutoAtivoParaMidia()

    // ── H. depuração ────────────────────────────────────────────────────────────────────────────

    /** H1: uma linha com o estado do canal. */
    fun estadoParaDepuracao(motivo: String): String = n.semFalhar("estado para depuração", "erro") {
        canal.garantirLigado()
        val vivo = canal.vivo()
        val status = if (vivo) canal.status() else null
        val musica = if (vivo) canal.musica() else null
        val progresso = if (vivo) canal.progresso() else null
        val aparelhos = if (vivo) canal.aparelhos() else null
        val resumo = aparelhos?.joinToString(";") { "modo=${it.modoAtual},status=${it.status},enlace=${it.enlace}" } ?: "-"
        "[$motivo] binder=$vivo pedida=${canal.ligacaoPedida()} enlace=${status ?: "?"}(${RegrasDaMidia.nomeDoEnlace(status)}) " +
            "musica=${musica ?: "?"}(${RegrasDaMidia.nomeDaMusica(musica)}) progresso=${progresso ?: -1} aparelhos=$resumo"
    }

    companion object {
        /** O pacote que aparece como fonte de mídia do AA (3-A1). */
        const val PACOTE_DA_MIDIA_DO_AA = "com.ts.androidauto"
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
