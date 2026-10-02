package br.com.opengw.barra

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import br.com.opengw.nucleo.Preferencias
import br.com.opengw.nucleo.Shell
import br.com.opengw.veiculo.Veiculo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * A mídia de pé (vive enquanto o serviço da barra vive): sobe as fontes, roda os laços e executa os
 * comandos (mídia §A, B, F–I, L, M). As regras estão em [NucleoDaMidia] e [RegrasDaMidia].
 */
internal class MotorDaMidia(private val contexto: Context) {

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val integracoes get() = Barra.integracoes
    private val lancador get() = integracoes.lancador

    private val ambiente = object : NucleoDaMidia.Ambiente {
        override val lancador: LancadorParaABarra get() = this@MotorDaMidia.lancador
        override fun agoraMs() = ThreadPrincipal.agoraMs()
        override fun projecaoNoPainel(): String = EstadoDaBarra.projecaoNoPainel
        override fun lerRetrato(): MidiaAgora = MidiaDaBarra.agora
        override fun escreverRetrato(m: MidiaAgora) = MidiaDaBarra.publicar(m)
        override fun naPrincipal(bloco: () -> Unit) = ThreadPrincipal.postar(bloco)
        override fun registrar(categoria: String, campos: Map<String, Any?>) = registrarDiagnostico(categoria, campos)
        override fun log(mensagem: String) {
            Log.i(TAG, mensagem)
        }
        override fun usbDaProjecaoPronto(): Boolean? = estadoUsbDaProjecao()
    }

    val nucleo = NucleoDaMidia(ambiente)
    private val central = CentralDeMidia(contexto).apply {
        aoDesligar = { nucleo.central = CentralDeMidiaLida.NENHUMA }
    }
    private val leitorUsb = LeitorUsbDaCentral(contexto)
    private val sessoes = SessoesDeMidia(
        contexto, escopo, nucleo,
        componenteHabilitado = { componenteDoOuvinte().takeIf { ouvinteHabilitado() } },
        pedirLiberacao = { escopo.launch { liberarOuvinte() } },
    )

    /** Uma thread só para a sequência do mudo (os atrasos são curtos e em ordem). */
    private val agendaDoMudo = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "OpenGW.Barra.Mudo") }
    private val mudo = MudoDoAndroidAuto(
        carro = object : MudoDoAndroidAuto.Carro {
            override fun lerDoCarro(chave: String) = Veiculo.lerDoCarro(chave)
            override fun lerDoCache(chave: String) = Veiculo.ler(chave)
            override fun escrever(chave: String, valor: String) = Veiculo.escrever(chave, valor)
        },
        agendar = { atraso, tarefa ->
            runCatching { agendaDoMudo.schedule({ runCatching(tarefa) }, atraso, TimeUnit.MILLISECONDS) }
        },
        publicar = { m -> ThreadPrincipal.postar { nucleo.publicarMudo(m) } },
        mudoVisual = { MidiaDaBarra.agora.mudo },
    )

    private var lacos: List<Job> = emptyList()

    // ── Ciclo de vida (casos 1–2) ───────────────────────────────────────────────────────

    /** Na principal. */
    fun iniciar() {
        MidiaDaBarra.motor = this
        sessoes.iniciar()
        val acesso = escopo.launch { lacoDeAcessoAsNotificacoes() }
        runCatching {
            integracoes.carPlay.iniciar { a -> ThreadPrincipal.executar { nucleo.publicarCarPlay(a) } }
        }.onFailure { Log.w(TAG, "CarPlay não iniciou: ${it.message}") }
        iniciarSinaisDoRadio()
        central.ligar()
        val lacoCentral = escopo.launch { lacoDaCentral() }
        val lacoUsb = escopo.launch { lacoDoUsb() }
        val lacoDesconexao = escopo.launch { lacoDeDesconexao() }
        iniciarMonitorDoMudo()
        lacos = listOf(acesso, lacoCentral, lacoUsb, lacoDesconexao)
    }

    /** Na principal. */
    fun parar() {
        if (MidiaDaBarra.motor === this) MidiaDaBarra.motor = null
        lacos.forEach { it.cancel() }
        escopo.cancel()
        agendaDoMudo.shutdownNow()
        central.desligar()
        sessoes.parar(limpar = false)
        nucleo.limparTudo()
        runCatching { integracoes.carPlay.parar() }
        Veiculo.pararDeObservar(ouvinteDoRadio)
        Veiculo.pararDeObservar(ouvinteDoMudo)
    }

    // ── Diagnóstico ─────────────────────────────────────────────────────────────────────

    private val ultimoDiagnosticoUsb = java.util.concurrent.atomic.AtomicReference<String?>(null)

    /** O registro USB só sai quando (etapa + campos ordenados) muda (caso 39). */
    private fun registrarDiagnostico(categoria: String, campos: Map<String, Any?>) {
        if (categoria == "usb_media") {
            val chave = campos.toSortedMap().toString()
            if (ultimoDiagnosticoUsb.getAndSet(chave) == chave) return
            Log.i(TAG, "[USB] $chave")
        }
        runCatching { integracoes.registro.registrar(categoria, campos) }
    }

    // ── Acesso às notificações (casos 3–7) ──────────────────────────────────────────────

    private fun componenteDoOuvinte(): ComponentName = Barra.componenteDoOuvinteDeNotificacoes(contexto)

    private fun listaDoSistema(): String? = runCatching {
        Settings.Secure.getString(contexto.contentResolver, OUVINTES)
    }.getOrNull()

    private fun contem(lista: String?, componente: ComponentName): Boolean =
        lista.orEmpty().split(':').any { ComponentName.unflattenFromString(it.trim()) == componente }

    /** Caso 3. */
    private fun ouvinteHabilitado(): Boolean = contem(listaDoSistema(), componenteDoOuvinte())

    /** Caso 4: laço de 30 s. */
    private suspend fun lacoDeAcessoAsNotificacoes() {
        while (escopo.isActive) {
            runCatching { repararOuvintes() }
            if (ouvinteHabilitado()) {
                // Hipótese (perguntas no relatório): habilitado e sessões caídas → sobe de novo.
                if (!sessoes.dePe) withContext(Dispatchers.Main) { sessoes.reiniciar() }
            } else {
                runCatching { liberarOuvinte() }
            }
            delay(30_000)
        }
    }

    /** Caso 4 (c)–(g). */
    private suspend fun liberarOuvinte() {
        if (!EsperaDoShell.esperar()) return
        val componente = componenteDoOuvinte()
        val texto = componente.flattenToString()
        // (d) pelo ContentResolver, nunca pelo shell: vazio de falha seria lido como lista vazia.
        val lista = listaDoSistema() ?: return
        if (contem(lista, componente)) return
        guardarCopiaDosOuvintes(lista)
        Shell.executar(arrayOf("cmd", "notification", "allow_listener", texto))
        var mudou = contem(listaDoSistema(), componente)
        if (!mudou) {
            val antes = lista.split(':').map { it.trim() }.filter { it.isNotEmpty() }
            val nova = (antes + texto).joinToString(":")
            // Só reescreve se o resultado contém tudo o que havia.
            if (antes.all { nova.split(':').contains(it) }) {
                Shell.executar(arrayOf("settings", "put", "secure", OUVINTES, nova))
                mudou = contem(listaDoSistema(), componente)
            }
        }
        if (mudou || !sessoes.dePe) withContext(Dispatchers.Main) { sessoes.reiniciar() }
    }

    /** Caso 5. */
    private fun repararOuvintes() {
        val copia = PreferenciasDaBarra.abrir()?.getString(Preferencias.NOTIFICATION_LISTENERS_BACKUP.chave, null)
            ?.split(':')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        if (copia.isEmpty()) return
        val lista = listaDoSistema() ?: return
        val atuais = lista.split(':').map { it.trim() }.toSet()
        val faltam = copia.filter { it !in atuais }
        if (faltam.isEmpty() || !Shell.disponivel()) return
        for (c in faltam) Shell.executar(arrayOf("cmd", "notification", "allow_listener", c))
    }

    /** Caso 6: só cresce; grava só se o tamanho mudou. */
    private fun guardarCopiaDosOuvintes(lista: String) {
        val prefs = PreferenciasDaBarra.abrir() ?: return
        val chave = Preferencias.NOTIFICATION_LISTENERS_BACKUP.chave
        val atual = prefs.getString(chave, null)?.split(':')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        val proprio = contexto.packageName
        val novos = lista.split(':').map { it.trim() }.filter {
            it.isNotEmpty() && ComponentName.unflattenFromString(it)?.packageName != proprio
        }
        val junto = LinkedHashSet(atual).apply { addAll(novos) }
        if (junto.size != atual.size) prefs.edit().putString(chave, junto.joinToString(":")).apply()
    }

    // ── Notificações (caso 18) ──────────────────────────────────────────────────────────

    fun notificacao(pacote: String, titulo: String?, artista: String?, album: String?, capa: android.graphics.Bitmap?) {
        ThreadPrincipal.postar { nucleo.publicarNotificacao(pacote, titulo, artista, album, capa) }
    }

    // ── Rádio (casos 27–28) ─────────────────────────────────────────────────────────────

    private val sinaisDoRadio = setOf(
        RegrasDaMidia.SINAL_PLAY_STATE, RegrasDaMidia.SINAL_CANAL,
        RegrasDaMidia.SINAL_CANAL_RDS, RegrasDaMidia.SINAL_FONTE_DE_AUDIO,
    )

    private val ouvinteDoRadio = Veiculo.Ouvinte { chave, valor ->
        if (chave in sinaisDoRadio && RegrasDaMidia.sinalDoRadioProtege(chave, valor, inicial = false)) {
            ThreadPrincipal.postar { nucleo.ativarProtecaoDoRadio("sinal $chave=$valor") }
        }
    }

    private fun iniciarSinaisDoRadio() {
        Veiculo.observar(ouvinteDoRadio)
        escopo.launch {
            for (chave in listOf(RegrasDaMidia.SINAL_PLAY_STATE, RegrasDaMidia.SINAL_FONTE_DE_AUDIO)) {
                val valor = runCatching { Veiculo.ler(chave) }.getOrNull()
                if (RegrasDaMidia.sinalDoRadioProtege(chave, valor, inicial = true)) {
                    ThreadPrincipal.postar { nucleo.ativarProtecaoDoRadio("inicial $chave=$valor") }
                }
            }
        }
    }

    // ── USB da projeção (casos 34–35) ──────────────────────────────────────────────────

    @Volatile
    private var usbGuardado: Pair<Long, Boolean?>? = null

    /** Caso 34, guardado por 3 s (inclusive "não lido"). */
    private fun estadoUsbDaProjecao(): Boolean? {
        val agora = ThreadPrincipal.agoraMs()
        usbGuardado?.let { (quando, valor) -> if (agora - quando in 0 until 3_000) return valor }
        var texto = runCatching { File(ARQUIVO_USB).readText() }.getOrNull()?.takeIf { it.isNotBlank() }
        if (texto == null && Shell.disponivel()) {
            texto = Shell.executar(arrayOf("sh", "-c", "cat $ARQUIVO_USB 2>/dev/null || true")).takeIf { it.isNotBlank() }
        }
        val valor = texto?.let { RegrasDaMidia.usbDaProjecaoPronto(it) }
        usbGuardado = agora to valor
        return valor
    }

    /** Caso 35: laço de 1,5 s. */
    private suspend fun lacoDeDesconexao() {
        var anterior: Boolean? = null
        while (escopo.isActive) {
            runCatching {
                val pronto = estadoUsbDaProjecao()
                if (pronto != null && pronto != anterior) Log.i(TAG, "USB da projeção: ${if (pronto) "pronto" else "desconectado"}")
                if (pronto != null) anterior = pronto
                if (pronto == false) {
                    lancador.limparJanelasDoAndroidAutoSeDesconectado("usb_desconectado")
                    val sessaoPronta = nucleo.sessaoAAPronta()
                    ThreadPrincipal.postar {
                        when (RegrasDaMidia.limpezaPorDesconexao(MidiaDaBarra.agora.pacote, sessaoPronta)) {
                            LimpezaPorDesconexao.CARPLAY -> nucleo.limparCarPlay()
                            LimpezaPorDesconexao.ANDROID_AUTO -> nucleo.limparAA()
                            LimpezaPorDesconexao.NADA -> Unit
                        }
                    }
                }
            }.onFailure { Log.w(TAG, "laço de desconexão: ${it.message}") }
            delay(1_500)
        }
    }

    // ── Central de mídia (caso 24) ─────────────────────────────────────────────────────

    private suspend fun lacoDaCentral() {
        var lida = CentralDeMidiaLida.NENHUMA
        while (escopo.isActive) {
            runCatching { voltaDaCentral(lida)?.let { lida = it } }
                .onFailure { Log.w(TAG, "laço da central: ${it.message}") }
            delay(1_500)
        }
    }

    /** Uma volta; devolve a leitura (para registrar mudanças) ou null se pulou. */
    private fun voltaDaCentral(anterior: CentralDeMidiaLida): CentralDeMidiaLida? {
        if (central.binderDeReproducao() == null) return null
        val lida = CentralDeMidiaLida(central.fonteAtual(), central.fonteDeAudioAtual())
        if (lida != anterior) Log.i(TAG, "central: fonte=${lida.fonte} áudio=${lida.fonteDeAudio}")
        nucleo.central = lida
        if (lida.androidAutoNativo) {
            if (!nucleo.transporteAAPronto()) {
                ThreadPrincipal.postar { nucleo.limparAA() }
                return lida
            }
            nucleo.anularProtecaoPorAA()
            val info = central.info(CentralDeMidiaLida.FONTE_AA)
            val estado = central.estado(CentralDeMidiaLida.FONTE_AA)
            val capa = info?.imagem?.let { CapaDaMidia.normalizar(it) }
                ?: info?.urlDaImagem?.let { CapaDaMidia.deUri(contexto.contentResolver, it) }
            ThreadPrincipal.postar {
                if (info != null && info.temMetadados) {
                    nucleo.aplicarMetadadosAA(info, estado, capa, nucleo.transporteAAProntoGuardado)
                } else if (!nucleo.aplicarEstadoAA(estado, nucleo.transporteAAProntoGuardado)) {
                    nucleo.preservarAANativo()
                }
            }
        }
        if (lida.radioLocal) ThreadPrincipal.postar { nucleo.ativarProtecaoDoRadio("central em rádio") }
        return lida
    }

    // ── USB pela central (casos 36–39) ─────────────────────────────────────────────────

    private suspend fun lacoDoUsb() {
        while (escopo.isActive) {
            runCatching { voltaDoUsb() }.onFailure {
                registrarDiagnostico("usb_media", mapOf("etapa" to "tick_failed", "erro" to it.message))
            }
            delay(2_000)
        }
    }

    private suspend fun voltaDoUsb() {
        val c = nucleo.central
        if (c.vazia) return
        if (!c.usb) {
            leitorUsb.limparCache()
            withContext(Dispatchers.Main) {
                if (nucleo.limparUsb()) sessoes.publicarAMelhor()
            }
            return
        }
        val faixa = candidatoUsb()
        if (faixa == null) return
        val donoNaFoto = MidiaDaBarra.agora.pacote
        val painelNaFoto = EstadoDaBarra.projecaoNoPainel
        val concorrente = sessoes.concorrenteTocando()
        val transporte = if (RegrasDaMidia.eAndroidAuto(donoNaFoto) || RegrasDaMidia.eAndroidAuto(painelNaFoto)) {
            nucleo.transporteAAPronto()
        } else {
            false
        }
        withContext(Dispatchers.Main) {
            val dono = MidiaDaBarra.agora.pacote
            val painel = EstadoDaBarra.projecaoNoPainel
            val chegou = (RegrasDaMidia.eAndroidAuto(dono) || RegrasDaMidia.eAndroidAuto(painel)) &&
                !(RegrasDaMidia.eAndroidAuto(donoNaFoto) || RegrasDaMidia.eAndroidAuto(painelNaFoto))
            if (RegrasDaMidia.publicarUsb(nucleo.central, dono, painel, concorrente, transporte || chegou, faixa.tocando)) {
                nucleo.aplicarUsb(faixa)
            } else {
                if (nucleo.limparUsb()) sessoes.publicarAMelhor()
                registrarDiagnostico(
                    "usb_media",
                    mapOf("etapa" to "deferred", "dono" to dono, "projecao" to painel, "sessao" to concorrente),
                )
            }
        }
    }

    /** Caso 37. */
    private fun candidatoUsb(): FaixaUsb? {
        val info = central.info(CentralDeMidiaLida.FONTE_USB)?.takeIf { it.fonte == 0 || it.fonte == CentralDeMidiaLida.FONTE_USB }
        val estado = central.estado(CentralDeMidiaLida.FONTE_USB)?.takeIf { it.fonte == 0 || it.fonte == CentralDeMidiaLida.FONTE_USB }
        val capaDaInfo = info?.imagem?.let { CapaDaMidia.normalizar(it) }
            ?: info?.urlDaImagem?.let { CapaDaMidia.deUri(contexto.contentResolver, it) }
        val precisaDeReserva = info?.titulo == null || info.artista == null || info.album == null ||
            capaDaInfo == null || estado == null
        val reserva = if (precisaDeReserva) leitorUsb.instantaneo() else null
        val tags = reserva?.caminho?.takeIf { info?.artista == null || info?.album == null || capaDaInfo == null }
            ?.let { leitorUsb.tags(it) }
        val titulo = info?.titulo ?: reserva?.titulo ?: PreferenciasDaCentral.nomeSemExtensao(reserva?.caminho ?: info?.caminho)
            ?: return null
        return FaixaUsb(
            titulo = titulo,
            artista = info?.artista ?: tags?.artista,
            album = info?.album ?: tags?.album,
            capa = capaDaInfo ?: tags?.capa,
            caminho = reserva?.caminho ?: info?.caminho,
            duracaoMs = estado?.duracaoMs?.takeIf { it > 0 } ?: info?.duracaoMs?.takeIf { it > 0 }
                ?: reserva?.duracaoMs?.takeIf { it > 0 } ?: tags?.duracaoMs ?: 0L,
            decorridoMs = estado?.progressoValido ?: reserva?.posicaoMs ?: 0L,
            tocando = estado?.tocando ?: reserva?.tocando ?: false,
        )
    }

    // ── Mudo (casos 59–62) ──────────────────────────────────────────────────────────────

    private val ouvinteDoMudo = Veiculo.Ouvinte { chave, _ ->
        if (chave == MudoDoAndroidAuto.MUTE_STATE || chave == MudoDoAndroidAuto.MEDIA_MUTE_STATE) {
            // M-D5: ler fora do aviso do motor, publicar na principal.
            escopo.launch {
                val m = mudo.lerMudo() ?: return@launch
                ThreadPrincipal.postar { nucleo.publicarMudo(m) }
            }
        }
    }

    private fun iniciarMonitorDoMudo() {
        // M-D4: o motor não acompanhava essas chaves; sem isso o aviso quase nunca vinha.
        runCatching { Veiculo.acompanhar(listOf(MudoDoAndroidAuto.MUTE_STATE, MudoDoAndroidAuto.MEDIA_MUTE_STATE)) }
        Veiculo.observar(ouvinteDoMudo)
        escopo.launch {
            val m = mudo.lerMudo() ?: return@launch
            ThreadPrincipal.postar { nucleo.publicarMudo(m) }
        }
    }

    /** Caso 59 (guardas na thread de quem chama). */
    fun alternarMudoDoAndroidAuto(): Boolean {
        if (nucleo.protecaoAtiva(consultar = true)) {
            ThreadPrincipal.postar { nucleo.limparAA() }
            return false
        }
        if (lancador.deixarParaAMidiaNativa("mudo")) return false
        return mudo.alternar()
    }

    // ── Comandos (casos 47–58) ──────────────────────────────────────────────────────────

    private suspend fun naPrincipal(bloco: () -> Unit) = withContext(Dispatchers.Main) { bloco() }

    /** Caso 47. */
    fun buscar(posicaoMs: Long) {
        val m = MidiaDaBarra.agora
        if (m.duracaoMs <= 0) return
        val alvo = posicaoMs.coerceIn(0L, m.duracaoMs)
        escopo.launch {
            val aceito = when {
                RegrasDaMidia.eCarPlay(m.pacote) -> withContext(Dispatchers.Main) { integracoes.carPlay.buscar(alvo) }
                RegrasDaMidia.eAndroidAuto(m.pacote) -> false
                else -> sessoes.controleCom(PlaybackState.ACTION_SEEK_TO, m.pacote)?.let {
                    runCatching { it.transportControls.seekTo(alvo) }.isSuccess
                } ?: false
            }
            if (aceito) naPrincipal { nucleo.posicaoBuscada(alvo) }
        }
    }

    /** Caso 49. */
    fun pular(paraFrente: Boolean) {
        escopo.launch {
            val dono = MidiaDaBarra.agora.pacote
            val ok = when {
                nucleo.rotaAAParaOPainel() -> comandoDePuloAA(paraFrente, "painel").also { if (it) naPrincipal { nucleo.donoPassaAoAA() } }
                RegrasDaMidia.eCarPlay(dono) -> withContext(Dispatchers.Main) {
                    if (paraFrente) integracoes.carPlay.proxima() else integracoes.carPlay.anterior()
                }
                RegrasDaMidia.eAndroidAuto(dono) -> comandoDePuloAA(paraFrente, "painel")
                else -> {
                    val acao = if (paraFrente) PlaybackState.ACTION_SKIP_TO_NEXT else PlaybackState.ACTION_SKIP_TO_PREVIOUS
                    sessoes.controleCom(acao, dono)?.let {
                        runCatching {
                            if (paraFrente) it.transportControls.skipToNext() else it.transportControls.skipToPrevious()
                        }.isSuccess
                    } ?: false
                }
            }
            if (!ok) Log.i(TAG, "${if (paraFrente) "próxima" else "anterior"} não atendida (dono=$dono)")
        }
    }

    /** Caso 50 (também `androidAutoProxima/Anterior`). */
    suspend fun comandoDePuloAA(paraFrente: Boolean, motivo: String): Boolean {
        if (nucleo.protecaoAtiva(consultar = true)) {
            naPrincipal { nucleo.limparAA() }
            return false
        }
        if (lancador.deixarParaAMidiaNativa(motivo)) return false
        val agora = ThreadPrincipal.agoraMs()
        if (!RegrasDaMidia.aceitaComando(agora, nucleo.ultimoComandoAA, RegrasDaMidia.INTERVALO_PROXIMA_MS)) {
            Log.i(TAG, "comando AA duplicado ($motivo)")
            return false
        }
        nucleo.ultimoComandoAA = agora
        if (!lancador.prepararComandoAndroidAuto(motivo)) return false
        val enviado = lancador.pularPeloAap(paraFrente)
        if (enviado) naPrincipal { nucleo.trocouDeFaixa(motivo) }
        return enviado
    }

    /** Caso 51. */
    fun tocarOuPausar() {
        val tocando = MidiaDaBarra.agora.tocando
        escopo.launch {
            val dono = MidiaDaBarra.agora.pacote
            val ok = when {
                nucleo.rotaAAParaOPainel() -> reproducaoAA(tocando).also { if (it) naPrincipal { nucleo.donoPassaAoAA() } }
                RegrasDaMidia.eCarPlay(dono) -> withContext(Dispatchers.Main) { integracoes.carPlay.tocarOuPausar(tocando) }
                else -> {
                    val acao = if (tocando) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY
                    sessoes.controleCom(acao, dono)?.let {
                        runCatching { if (tocando) it.transportControls.pause() else it.transportControls.play() }.isSuccess
                    } ?: false
                }
            }
            if (!ok) Log.i(TAG, "tocar/pausar não atendido (dono=$dono)")
        }
    }

    /** Caso 52. */
    private suspend fun reproducaoAA(visualTocando: Boolean): Boolean {
        val audio = lancador.audioDoAndroidAutoAtivo("efetivo", forcarLeitura = true)
        val nativo = androidAutoTocandoPelaCentral()
        val efetivo = RegrasDaMidia.reproducaoEfetiva(
            visualTocando, audio, status = null, nativo = nativo,
            rotaNativaAtiva = rotaNativaDoAndroidAutoAtiva(), palpite = nucleo.palpiteAA,
        )
        val alvo = !efetivo
        val pararVale = !alvo && audio
        if (nucleo.protecaoAtiva(consultar = true)) {
            naPrincipal { nucleo.limparAA() }
            return false
        }
        if (lancador.deixarParaAMidiaNativa("tocar_pausar")) return false
        val agora = ThreadPrincipal.agoraMs()
        if (!RegrasDaMidia.aceitaComando(agora, nucleo.ultimoComandoAA, RegrasDaMidia.INTERVALO_TOCAR_PAUSAR_MS)) {
            Log.i(TAG, "tocar/pausar AA duplicado")
            return false
        }
        nucleo.ultimoComandoAA = agora
        if (nucleo.central.androidAutoNativo && nucleo.transporteAAPronto()) {
            val enviado = comandoPelaCentral(alvo)
            if (enviado) escopo.launch { verificar(alvo, pararVale, marcar = false) }
            return enviado
        }
        if (!lancador.prepararComandoAndroidAuto("tocar_pausar", lancador.pedirFocoDeVideoParaAlvo(alvo))) return false
        // Caso 55: soltar o foco de pausa — não portado (M-4), não há pedido a soltar.
        if (lancador.reproducaoDireta(efetivo) && verificar(alvo, pararVale)) return true
        if (lancador.reproducaoPeloAap(efetivo) && verificar(alvo, pararVale)) return true
        if (teclaDeMidia(alvo) && verificar(alvo, pararVale)) return true
        return false
    }

    private fun teclaDeMidia(tocar: Boolean): Boolean = runCatching {
        val am = contexto.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val codigo = if (tocar) KeyEvent.KEYCODE_MEDIA_PLAY else KeyEvent.KEYCODE_MEDIA_PAUSE
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, codigo))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, codigo))
    }.isSuccess

    /** Caso 53. */
    private suspend fun verificar(alvo: Boolean, pararVale: Boolean, marcar: Boolean = true): Boolean {
        delay(1_350)
        val audio = lancador.audioDoAndroidAutoAtivo("verificar", forcarLeitura = true)
        val ok = RegrasDaMidia.verificar(alvo, audio, status = null, pararVale = pararVale)
        Log.i(TAG, "verificação AA alvo=${if (alvo) "tocar" else "pausar"}: ${if (ok) "pegou" else "não pegou"}")
        if (ok && marcar) naPrincipal { nucleo.marcarAlvo(alvo) }
        return ok
    }

    /** Caso 54. */
    private suspend fun comandoPelaCentral(tocar: Boolean): Boolean {
        val codigo = if (tocar) CentralDeMidia.TX_RETOMAR else CentralDeMidia.TX_PAUSAR
        if (!central.servicoVivo()) {
            central.ligar()
            delay(180)
        }
        if (central.binderDeReproducao() == null) {
            central.ligar()
            delay(180)
            if (central.binderDeReproducao() == null) return false
        }
        var enviado = central.comando(codigo, CentralDeMidiaLida.FONTE_AA)
        if (!enviado) {
            central.ligar()
            delay(180)
            if (central.binderDeReproducao() != null) enviado = central.comando(codigo, CentralDeMidiaLida.FONTE_AA)
        }
        if (enviado) naPrincipal { nucleo.marcarAlvo(tocar) }
        return enviado
    }

    /** `androidAutoReproducaoPelaCentral` (lançador): caso 54 sem verificar. */
    suspend fun reproducaoAAPelaCentral(tocar: Boolean, motivo: String): Boolean {
        if (!nucleo.central.androidAutoNativo || !nucleo.transporteAAPronto()) return false
        Log.i(TAG, "reprodução AA pela central ($motivo)")
        return comandoPelaCentral(tocar)
    }

    /** Caso 58, final: o estado da fonte 402 resolvido pelo alvo. Fundo. */
    fun androidAutoTocandoPelaCentral(): Boolean? {
        if (!nucleo.transporteAAPronto()) return null
        val e = central.estado(CentralDeMidiaLida.FONTE_AA)?.takeIf { it.fonte == CentralDeMidiaLida.FONTE_AA || it.fonte == 0 }
        return nucleo.resolverPeloAlvo(e?.tocando, e?.progressoValido)
    }

    /** A rota nativa da central para o AA: fonte 402 e transporte pronto (último calculado). */
    fun rotaNativaDoAndroidAutoAtiva(): Boolean =
        nucleo.central.androidAutoNativo && nucleo.transporteAAProntoGuardado

    fun trocouDeFaixa(motivo: String): Boolean {
        ThreadPrincipal.executar { nucleo.trocouDeFaixa(motivo) }
        return true
    }

    fun dicaDoVolante(tocando: Boolean) {
        ThreadPrincipal.executar { nucleo.dicaDoVolante(tocando) }
    }

    /** Canal de depuração `state`. */
    fun registrarEstado() {
        val m = MidiaDaBarra.agora
        Log.i(
            TAG,
            "[ESTADO] dono=${m.pacote} título=${m.titulo} artista=${m.artista} álbum=${m.album} " +
                "tocando=${m.tocando} mudo=${m.mudo} dur=${m.duracaoMs} pos=${m.decorridoMs} " +
                "central=${nucleo.central} sessões=${sessoes.dePe}",
        )
    }

    companion object {
        private const val TAG = "OpenGW.Barra.Midia"
        const val OUVINTES = "enabled_notification_listeners"
        const val ARQUIVO_USB = "/sys/class/android_usb/android0/state"
    }
}

/** Esperar o Shell ficar de pé (até 20 s, testando a cada 1 s). */
internal object EsperaDoShell {
    suspend fun esperar(limiteMs: Long = 20_000): Boolean {
        var esperado = 0L
        while (true) {
            if (Shell.disponivel()) return true
            if (esperado >= limiteMs) return false
            delay(1_000)
            esperado += 1_000
        }
    }
}
