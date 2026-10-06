// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.luz

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.Visualizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * Os dois efeitos com música (casos 54–78): pulsos nos graves e a onda do álbum. Um de cada vez;
 * parar restaura o que mudou na luz original do carro.
 */
internal class EfeitosDeMusica(
    contexto: Context,
    private val ajustes: () -> AjustesDaLuz,
    private val modo: () -> ModoDeConducao,
    private val fita: FitaDoMotor,
    private val carro: CarroDaLuz,
    private val fontes: FontesDaLuz,
    private val escopo: CoroutineScope,
    private val relogio: Relogio,
    private val registro: Registro,
) : ControleDaMusica {

    private val contexto: Context = contexto.applicationContext
    private val graves = Graves()
    private val onda = OndaDoAlbum()

    @Synchronized
    override fun ligar(modo: ModoMusical) {
        when (modo) {
            ModoMusical.GRAVES -> {
                onda.parar()
                graves.ligar()
            }
            ModoMusical.ONDA_DO_ALBUM -> {
                graves.parar()
                onda.ligar()
            }
        }
    }

    @Synchronized
    override fun parar() {
        graves.parar()
        onda.parar()
        DiagnosticoDaLuz.musica { DiagnosticoDaMusica() }
    }

    private fun conectada(): Boolean = fita.estado.value is EstadoDaConexao.Conectada

    /** Caso 54. */
    private fun corBase(): CorLed = CorDeAcento.corBaseDaMusica(modo())

    // ============================================================================== graves

    private inner class Graves {

        private val trava = Any()
        private var escopoDoEfeito: CoroutineScope? = null
        private var visualizador: Visualizer? = null
        private var tarefaDoMicrofone: Job? = null

        @Volatile
        private var microfoneLigado = false

        private var ponteMudou = false
        private var valorAnteriorDaPonte: String? = null
        private var detectorDigital = DetectorDigital()
        private var detectorDoMicrofone = DetectorDoMicrofone()
        private var ultimoDigitalUtil = NUNCA
        private var ultimaBatida = NUNCA
        private var volta: Job? = null

        private val ouvinteDaCentral = OuvinteDoCarro { chave, valor ->
            if (chave == ChavesDoCarro.FREQUENCIA_DA_MUSICA) aoQuadro(FonteDaMusica.CENTRAL, LeitorDaCentral.ler(valor))
        }

        fun ligar() {
            val e: CoroutineScope
            synchronized(trava) {
                if (escopoDoEfeito != null) return
                e = CoroutineScope(escopo.coroutineContext + SupervisorJob(escopo.coroutineContext[Job]))
                escopoDoEfeito = e
                detectorDigital = DetectorDigital()
                detectorDoMicrofone = DetectorDoMicrofone()
                ultimoDigitalUtil = NUNCA
                ultimaBatida = NUNCA
            }
            DiagnosticoDaLuz.musica { DiagnosticoDaMusica(ativa = true) }
            val ponte = subirPonte()
            if (PermissoesDaLuz.podeGravarAudio(contexto)) {
                iniciarDigital(e)
            } else if (ponte) {
                DiagnosticoDaLuz.musica { it.copy(aviso = "aguardando a central") }
            } else {
                DiagnosticoDaLuz.musica {
                    it.copy(ativa = false, erro = "Sem permissão de áudio e sem o sinal da central")
                }
            }
        }

        fun parar() {
            val e: CoroutineScope
            val v: Visualizer?
            synchronized(trava) {
                e = escopoDoEfeito ?: return
                escopoDoEfeito = null
                volta?.cancel()
                volta = null
                v = visualizador
                visualizador = null
                pararMicrofone()
            }
            // Fora da trava: o retorno do Visualizer também a usa.
            v?.let { soltar(it) }
            descerPonte()
            e.cancel()
        }

        // --------------------------------------------------------------- ponte da central (caso 60)

        private fun subirPonte(): Boolean = try {
            val atual = carro.ler(ChavesDoCarro.RITMO_DA_LUZ_ORIGINAL)
            if (atual?.trim() != "1") {
                carro.escrever(ChavesDoCarro.RITMO_DA_LUZ_ORIGINAL, "1")
                ponteMudou = true
                valorAnteriorDaPonte = atual
            }
            carro.acompanhar(listOf(ChavesDoCarro.FREQUENCIA_DA_MUSICA))
            carro.observar(ouvinteDaCentral)
            true
        } catch (ex: Exception) {
            registro.anotar("ponte da central não subiu: $ex")
            false
        }

        private fun descerPonte() {
            try {
                carro.pararDeObservar(ouvinteDaCentral)
            } catch (ex: Exception) {
                registro.anotar("ao soltar a ponte da central: $ex")
            }
            if (ponteMudou) {
                val anterior = valorAnteriorDaPonte?.trim()?.takeIf { it.isNotEmpty() } ?: "0"
                try {
                    carro.escrever(ChavesDoCarro.RITMO_DA_LUZ_ORIGINAL, anterior)
                } catch (ex: Exception) {
                    registro.anotar("ao restaurar rhythmic_switch: $ex")
                }
            }
            ponteMudou = false
            valorAnteriorDaPonte = null
        }

        // --------------------------------------------------------------- digital (caso 56)

        private fun iniciarDigital(e: CoroutineScope) {
            val v = criarVisualizador()
            if (v == null) {
                iniciarMicrofone(e)
                return
            }
            synchronized(trava) {
                if (escopoDoEfeito !== e) {
                    soltar(v)
                    return
                }
                visualizador = v
            }
            e.launch {
                relogio.esperar(ESPERA_PELO_DIGITAL_MS)
                val semSinal = synchronized(trava) { relogio.agora() - ultimoDigitalUtil > ESPERA_PELO_DIGITAL_MS }
                if (semSinal && !microfoneLigado) iniciarMicrofone(e)
            }
        }

        private fun criarVisualizador(): Visualizer? {
            val v = try {
                Visualizer(0)
            } catch (ex: Throwable) {
                registro.anotar("Visualizer indisponível: $ex")
                return null
            }
            return try {
                val faixa = Visualizer.getCaptureSizeRange()
                v.setCaptureSize(TAMANHO_DA_CAPTURA.coerceIn(faixa[0], faixa[1]))
                val taxa = min(Visualizer.getMaxCaptureRate(), TAXA_MAXIMA_MHZ)
                val r = v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) = Unit

                    override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        aoQuadro(FonteDaMusica.DIGITAL, detectorDigital.processar(fft))
                    }
                }, taxa, false, true)
                check(r == Visualizer.SUCCESS) { "setDataCaptureListener devolveu $r" }
                val ligou = v.setEnabled(true)
                check(ligou == Visualizer.SUCCESS) { "setEnabled devolveu $ligou" }
                v
            } catch (ex: Throwable) {
                registro.anotar("Visualizer não ligou: $ex")
                soltar(v)
                null
            }
        }

        private fun soltar(v: Visualizer) {
            try {
                v.setEnabled(false)
            } catch (ex: Throwable) {
                // já solto
            }
            try {
                v.release()
            } catch (ex: Throwable) {
                // já solto
            }
        }

        // --------------------------------------------------------------- microfone (caso 57)

        @SuppressLint("MissingPermission")
        private fun iniciarMicrofone(e: CoroutineScope) {
            if (!PermissoesDaLuz.podeGravarAudio(contexto)) return
            synchronized(trava) {
                if (escopoDoEfeito !== e || microfoneLigado) return
                microfoneLigado = true
            }
            val minimo = AudioRecord.getMinBufferSize(TAXA_DO_MICROFONE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val gravador = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    TAXA_DO_MICROFONE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    max(minimo, 1_024),
                )
            } catch (ex: Throwable) {
                registro.anotar("AudioRecord: $ex")
                null
            }
            val gravando = gravador != null && gravador.state == AudioRecord.STATE_INITIALIZED && try {
                gravador.startRecording()
                true
            } catch (ex: Throwable) {
                registro.anotar("startRecording: $ex")
                false
            }
            if (gravador == null || !gravando) {
                try {
                    gravador?.release()
                } catch (ex: Throwable) {
                    // nada a soltar
                }
                microfoneLigado = false
                DiagnosticoDaLuz.musica { it.copy(erro = "Microfone indisponível") }
                return
            }
            registro.anotar("graves pelo microfone")
            val tarefa = e.launch(Dispatchers.IO) {
                val janela = ShortArray(JANELA_DO_MICROFONE)
                try {
                    while (isActive && microfoneLigado) {
                        val lidas = try {
                            gravador.read(janela, 0, janela.size)
                        } catch (ex: Exception) {
                            -1
                        }
                        if (lidas <= 0) {
                            delay(40)
                            continue
                        }
                        aoQuadro(FonteDaMusica.MICROFONE, detectorDoMicrofone.processar(janela, lidas))
                    }
                } finally {
                    try {
                        gravador.stop()
                    } catch (ex: Throwable) {
                        // já parado
                    }
                    gravador.release()
                }
            }
            synchronized(trava) { tarefaDoMicrofone = tarefa }
        }

        private fun pararMicrofone() {
            microfoneLigado = false
            tarefaDoMicrofone?.cancel()
            tarefaDoMicrofone = null
        }

        // --------------------------------------------------------------- batida (casos 59, 61)

        private fun aoQuadro(fonte: FonteDaMusica, r: ResultadoDoDetector) {
            synchronized(trava) {
                val e = escopoDoEfeito ?: return
                val agora = relogio.agora()
                when (fonte) {
                    FonteDaMusica.DIGITAL -> if (r.nivel >= NIVEL_DIGITAL_UTIL) {
                        ultimoDigitalUtil = agora
                        if (microfoneLigado) pararMicrofone()
                    } else if (microfoneLigado) {
                        return
                    }
                    FonteDaMusica.MICROFONE -> if (agora - ultimoDigitalUtil < MICROFONE_CALADO_MS) return
                    FonteDaMusica.CENTRAL -> if (visualizador != null || microfoneLigado) return
                    else -> return
                }
                DiagnosticoDaLuz.musica { it.copy(ativa = true, fonte = fonte, nivel = r.nivel, aviso = null) }
                if (!r.batida || agora - ultimaBatida < INTERVALO_ENTRE_BATIDAS_MS) return
                val a = ajustes()
                if (!a.ligado || !a.musica || a.modoMusical != ModoMusical.GRAVES || !conectada()) return
                ultimaBatida = agora
                val acento = CorDeAcento.calcular(corBase(), r.nivel)
                volta?.cancel()
                volta = e.launch {
                    fita.enviar(Quadros.cor(acento, a.saida, a.ordemDmx, a.ordemBle))
                    relogio.esperar(DURACAO_DO_ACENTO_MS)
                    val b = ajustes()
                    if (b.ligado && b.musica && b.modoMusical == ModoMusical.GRAVES && conectada()) {
                        fita.enviar(Quadros.cor(corBase(), b.saida, b.ordemDmx, b.ordemBle))
                    }
                }
            }
            DiagnosticoDaLuz.musica { it.copy(ultimoEventoEm = System.currentTimeMillis()) }
        }
    }

    // ============================================================================== onda do álbum

    private inner class OndaDoAlbum {

        private var tarefa: Job? = null

        fun ligar() {
            if (tarefa?.isActive == true) return
            DiagnosticoDaLuz.musica { DiagnosticoDaMusica(ativa = true, fonte = FonteDaMusica.ALBUM) }
            tarefa = escopo.launch {
                try {
                    laco()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    registro.anotar("onda do álbum parou: $e")
                    DiagnosticoDaLuz.musica { it.copy(erro = "Onda do álbum parou: ${e.javaClass.simpleName}") }
                }
            }
        }

        fun parar() {
            tarefa?.cancel()
            tarefa = null
        }

        private suspend fun laco() {
            var assinatura: String? = null
            var paleta: List<CorLed> = PaletaDoAlbum.RESERVA
            var passo = 0
            var configuracao: List<Any>? = null
            var tocandoAntes: Boolean? = null
            var restaurado: String? = null
            val enviados = HashMap<String, String>()

            while (true) {
                val a = ajustes()
                if (!a.ligado || !a.musica || a.modoMusical != ModoMusical.ONDA_DO_ALBUM) {
                    relogio.esperar(OCIOSA_MS)
                    continue
                }

                val midia = lerMidia()
                if (midia == null || !midia.temAlbum) {
                    val base = corBase()
                    val chave = "${base.r},${base.g},${base.b}|${a.saida}|${a.ordemDmx}|${a.ordemBle}"
                    if (chave != restaurado && conectada() && fita.enviar(Quadros.cor(base, a.saida, a.ordemDmx, a.ordemBle))) {
                        restaurado = chave
                    }
                    DiagnosticoDaLuz.musica { it.copy(ativa = true, fonte = FonteDaMusica.ALBUM, nivel = 0.0, aviso = "aguardando álbum") }
                    relogio.esperar(OCIOSA_MS)
                    continue
                }
                restaurado = null

                val nova = assinaturaDe(midia)
                if (nova != assinatura) {
                    assinatura = nova
                    val capa = midia.capa
                    val cores = withContext(Dispatchers.Default) {
                        try {
                            fontes.coresDaCapa.cores(capa, nova)
                        } catch (e: Throwable) {
                            registro.anotar("cores da capa: $e")
                            CoresDaCapa.RESERVA
                        }
                    }
                    paleta = PaletaDoAlbum.montar(cores)
                    passo = 0
                    enviados.clear()
                }
                val novaConfiguracao = listOf(
                    a.efeitoDoAlbum, a.velocidadeDoAlbum, a.saidaDoAlbum, a.modoBleDoAlbum, a.modoDmxDoAlbum, a.ordemDmx, a.ordemBle,
                )
                if (novaConfiguracao != configuracao) {
                    configuracao = novaConfiguracao
                    passo = 0
                    enviados.clear()
                }
                if (midia.tocando != tocandoAntes) {
                    tocandoAntes = midia.tocando
                    enviados.clear()
                }

                if (!conectada()) {
                    DiagnosticoDaLuz.musica { it.copy(aviso = "BLE não conectado") }
                    relogio.esperar(OCIOSA_MS)
                    continue
                }

                val estatica = PaletaDoAlbum.corEstatica(paleta)
                if (!midia.tocando) {
                    if (a.saidaDoAlbum.incluiDmx) enviarSeMudou(enviados, "DMX", listOf(Quadros.corDmx(estatica, a.ordemDmx)))
                    if (a.saidaDoAlbum.incluiBle) enviarSeMudou(enviados, "BLE", listOf(Quadros.corBle(estatica, a.ordemBle)))
                    DiagnosticoDaLuz.musica {
                        it.copy(fonte = FonteDaMusica.ALBUM, nivel = ContasDeCor.intensidade(estatica), aviso = "pausado: cor fixa")
                    }
                    relogio.esperar(OCIOSA_MS)
                    continue
                }

                var porQuadro = false
                var nivel = ContasDeCor.intensidade(estatica)
                val animada by lazy(LazyThreadSafetyMode.NONE) { PaletaDoAlbum.corAnimada(a.efeitoDoAlbum, paleta, passo) }
                if (a.saidaDoAlbum.incluiDmx) {
                    if (a.modoDmxDoAlbum == ModoDoAlbum.ESTATICO) {
                        enviarSeMudou(enviados, "DMX", listOf(Quadros.corDmx(estatica, a.ordemDmx)))
                    } else {
                        val nativo = PaletaDoAlbum.modoNativo(a.efeitoDoAlbum, estatica)
                        if (nativo != null) {
                            enviarSeMudou(enviados, "DMX", Quadros.efeitoNativo(nativo, a.velocidadeDoAlbum, SaidaDaLuz.DMX))
                        } else {
                            val q = Quadros.corDmx(animada, a.ordemDmx)
                            fita.enviar(listOf(q))
                            enviados["DMX"] = Hex.deBytes(q)
                            nivel = ContasDeCor.intensidade(animada)
                            porQuadro = true
                        }
                    }
                }
                if (a.saidaDoAlbum.incluiBle) {
                    if (a.modoBleDoAlbum == ModoDoAlbum.ESTATICO) {
                        enviarSeMudou(enviados, "BLE", listOf(Quadros.corBle(estatica, a.ordemBle)))
                    } else {
                        val q = Quadros.corBle(animada, a.ordemBle)
                        fita.enviar(listOf(q))
                        enviados["BLE"] = Hex.deBytes(q)
                        nivel = ContasDeCor.intensidade(animada)
                        porQuadro = true
                    }
                }
                val n = nivel
                DiagnosticoDaLuz.musica {
                    it.copy(ativa = true, fonte = FonteDaMusica.ALBUM, nivel = n, ultimoEventoEm = System.currentTimeMillis(), aviso = null)
                }
                if (porQuadro) {
                    passo++
                    relogio.esperar(PaletaDoAlbum.intervaloMs(a.velocidadeDoAlbum))
                } else {
                    relogio.esperar(OCIOSA_MS)
                }
            }
        }

        /** Só manda se mudou desde o último envio daquela saída. */
        private suspend fun enviarSeMudou(enviados: MutableMap<String, String>, canal: String, quadros: List<ByteArray>) {
            val chave = Hex.deQuadros(quadros)
            if (enviados[canal] == chave) return
            if (fita.enviar(quadros)) enviados[canal] = chave
        }

        private suspend fun lerMidia(): MidiaTocando? = try {
            withContext(Dispatchers.Main) { fontes.midia.agora() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            registro.anotar("mídia: $e")
            null
        }

        private fun assinaturaDe(m: MidiaTocando): String {
            val capa = m.capa
            val daCapa: List<String?> = if (capa == null) {
                emptyList()
            } else {
                try {
                    listOf(capa.width.toString(), capa.height.toString(), capa.byteCount.toString(), capa.config?.name)
                } catch (e: Exception) {
                    emptyList()
                }
            }
            return PaletaDoAlbum.assinatura(listOf(m.pacote, m.titulo, m.artista, m.album) + daCapa)
        }
    }

    private companion object {
        const val NUNCA = Long.MIN_VALUE / 4
        const val TAMANHO_DA_CAPTURA = 512
        const val TAXA_MAXIMA_MHZ = 12_000
        const val ESPERA_PELO_DIGITAL_MS = 3_000L
        const val NIVEL_DIGITAL_UTIL = 0.06
        const val TAXA_DO_MICROFONE = 8_000
        const val JANELA_DO_MICROFONE = 512
        const val MICROFONE_CALADO_MS = 1_200L
        const val INTERVALO_ENTRE_BATIDAS_MS = 380L
        const val DURACAO_DO_ACENTO_MS = 120L
        const val OCIOSA_MS = 1_000L
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
