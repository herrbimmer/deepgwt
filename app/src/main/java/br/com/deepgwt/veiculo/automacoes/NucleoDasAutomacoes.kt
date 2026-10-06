// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.automacoes

import br.com.deepgwt.nucleo.Preferencias
import br.com.deepgwt.veiculo.TravaDeMovimento
import br.com.deepgwt.veiculo.comandos.Agendados
import br.com.deepgwt.veiculo.comandos.CarroDoMotor
import br.com.deepgwt.veiculo.comandos.DiarioDoResto
import br.com.deepgwt.veiculo.comandos.PreferenciasDoMotor
import br.com.deepgwt.veiculo.ProjecaoNasTelas
import br.com.deepgwt.veiculo.comandos.ShellDoMotor
import br.com.deepgwt.veiculo.comandos.numeroDoCarro
import br.com.deepgwt.veiculo.motor.Agenda
import br.com.deepgwt.veiculo.motor.RegistroDoMotor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * As automações do carro (módulo 3.3b, `docs/independencia/specs/motor-automacoes.md`): quando e com
 * que condições agir sozinho. As ações moram em 3.3c (comandos) e 3.3e (sistema). Sem Android.
 *
 * Eventos: o gancho volta na hora e o trabalho vai, em ordem, para a linha serial do motor (A.3).
 * Cada caso é isolado (A.4). As preferências são lidas na hora de cada decisão.
 */
internal class NucleoDasAutomacoes(
    private val carro: CarroDoMotor,
    private val prefs: PreferenciasDoMotor,
    private val agenda: Agenda,
    private val acoes: AcoesDoCarro,
    private val sistema: AcoesDoSistema,
    private val shell: ShellDoMotor,
    private val pronto: () -> Boolean,
    private val trava: (exigirP: Boolean, exigirLeitura: Boolean) -> TravaDeMovimento.Motivo?,
    /** Relógio monotônico (`elapsedRealtime`), para os 10 s de C.3. */
    private val relogio: () -> Long,
    /** Hora local em minutos do dia (G.2). */
    private val minutosDoDia: () -> Int,
    /** Roda na thread principal (os projetores). */
    private val naPrincipal: (() -> Unit) -> Unit,
    private val registro: RegistroDoMotor,
    private val diario: DiarioDoResto,
    private val decisoes: DecisoesDasAutomacoes = DecisoesDasAutomacoes(),
) {

    @Volatile var ganchos: GanchosDePartida = GanchosDePartida.VAZIO
    @Volatile var projecao: ProjecaoNasTelas = ProjecaoNasTelas.VAZIA

    /** J: o estado processado do carro (não o cache do READY). Começa falso. */
    @Volatile var carroDesligado: Boolean = false
        private set

    private val agendados = Agendados(agenda)

    // Estado mexido só na linha do motor.
    private var vidrosFechadosPelaVelocidade = false
    private var tetoFechadoPelaVelocidade = false
    private var cameraFechadaNestaAbertura = false
    /** O último estado lido da câmera 360 (`sys.avm.preview_status`); nulo = nenhum ainda. */
    private var ultimoEstadoDaCamera: String? = null
    /**
     * Alpha 9.8: a câmera foi aberta pelo botão com o carro parado e a preferência de permitir está
     * ligada. Vale até a câmera fechar; enquanto vale, B.6/B.7 não a fecham.
     */
    private var cameraAbertaPeloBotao = false
    /** O último READY processado: true = ligado, false = desligado, null = nenhum ainda. */
    private var ultimoReady: Boolean? = null
    private var ultimoFecharAoDesligar: Long? = null

    /** G.1: uma vez por processo, na partida. */
    private val cortinaDaPartidaRodou = AtomicBoolean(false)

    // ── A. Recepção dos eventos ──

    /** A.1–A.3: descarta antes do "pronto" do conjunto; senão enfileira e volta. */
    fun aoChegarDoCarro(chave: String, valor: String?) {
        if (valor == null) return
        val prontoAgora = try {
            pronto()
        } catch (e: Exception) {
            false
        }
        if (!prontoAgora) return
        try {
            agenda.executar { tratar(chave, valor) }
        } catch (e: Exception) {
            registro.erro("automações: não deu para enfileirar $chave", e)
        }
    }

    /** B: um evento, já na linha. B.1 antes; depois no máximo um caso de B.2–B.11. */
    internal fun tratar(chave: String, valor: String) {
        if (chave == CAMERA_360 || chave == PAINEL_DO_AR) isolado("guardas de projeção") { guardasDaProjecao(chave, valor) }
        when (chave) {
            DISTRACAO, FADIGA -> isolado("monitoramento forçado") { monitoramentoForcado(chave, valor) }
            AVAS -> isolado("AVAS forçado") { avasForcado(valor) }
            CAMERA_DO_MOTORISTA -> isolado("câmera do motorista") { cameraDoMotorista(valor) }
            RETROVISOR -> isolado("retrovisores") { retrovisores(valor) }
            VELOCIDADE -> isolado("velocidade") { velocidade(valor) }
            CAMERA_360 -> isolado("câmera 360 parada") { cameraAbriuParada(valor) }
            READY -> isolado("READY") { ready(valor) }
            AR_LIGADO -> isolado("ventilação do banco") { ventilacao(valor, "ar = ${valor.trim()}") }
            TEMPERATURA_INTERNA -> isolado("suavização do ar") { suavizacao() }
            ALVO_DE_CARGA -> isolado("híbrido") { acoes.aplicarAlvoDeCargaHev("alvo mudou") }
            RESERVA -> isolado("híbrido") {
                if (valor.trim() == "2") acoes.aplicarAlvoDeCargaHev("entrou em Prioritário")
            }
            MODO_DE_POTENCIA -> isolado("híbrido") {
                if (valor.trim() == "0") acoes.aplicarAlvoDeCargaHev("entrou em HEV")
            }
        }
    }

    // ── B. Automações por evento ──

    /** B.1: a janela nativa por cima do painel faz o CarPlay/AA perderem o vídeo do painel. */
    private fun guardasDaProjecao(chave: String, valor: String) {
        val v = valor.trim()
        val p = projecao
        val aaNoPainel = p.androidAutoNoDisplay(DISPLAY_DO_PAINEL)
        if (chave == CAMERA_360) {
            val motivo = "câmera 360 = $v"
            if (aaNoPainel) {
                when (v) {
                    "1" -> p.pulsoDoAndroidAutoDuranteJanelaNativa(motivo)
                    "0" -> p.pulsoDoAndroidAutoAoSairDaJanelaNativa(motivo)
                }
            } else {
                p.guardarCarPlayNoPainel(motivo)
                p.guardarAndroidAutoNoPainelAposJanelaNativa(motivo)
            }
        } else {
            val motivo = "painel do ar = $v"
            if (aaNoPainel) {
                registro.info("guardas: $motivo com o Android Auto no painel; nada a fazer")
            } else {
                p.guardarCarPlayNoPainel(motivo)
                p.guardarAndroidAutoNoPainelAposJanelaNativa(motivo)
            }
        }
    }

    /** B.2. */
    private fun monitoramentoForcado(chave: String, valor: String) {
        if (valor.trim() != "1" || !prefs.booleano(Preferencias.DISABLE_MONITORING.chave, false)) return
        registro.info("monitoramento religou por $chave; desligando de novo")
        acoes.monitoramento(false)
    }

    /** B.3. */
    private fun avasForcado(valor: String) {
        if (valor.trim() != "1" || !prefs.booleano(Preferencias.DISABLE_AVAS.chave, false)) return
        registro.info("som para pedestres religou; desligando de novo")
        acoes.somParaPedestres(false)
    }

    /** B.4: só fecha com o carro desligado (o gatilho de verdade é o READY, B.8). */
    private fun cameraDoMotorista(valor: String) {
        if (valor.trim() != "0") return
        if (carroDesligado) {
            fecharAoDesligar("câmera do motorista parou")
        } else {
            registro.info("câmera do motorista parou com o carro ligado; vidros e teto ficam como estão")
        }
    }

    /** B.5: retrovisores recolhidos parado em P. Leitura nula = não pode (D-A4). */
    private fun retrovisores(valor: String) {
        if (valor.trim() != "0") return
        val velocidade = numeroDoCarro(carro.lerDoCarro(VELOCIDADE))
        val marcha = carro.lerDoCarro(MARCHA)?.trim()
        if (velocidade == null || marcha == null) {
            registro.info("retrovisores recolhidos: sem leitura de velocidade/marcha; nada feito")
            return
        }
        if (velocidade > 0 || !emP(marcha)) {
            registro.info("retrovisores recolhidos fora de 'parado em P' (v=$velocidade, marcha=$marcha); nada feito")
            return
        }
        if (prefs.booleano(Preferencias.CLOSE_WINDOW_ON_FOLD_MIRROR.chave, false)) isolado("vidros (retrovisor)") { acoes.fecharTodasAsJanelas() }
        if (prefs.booleano(Preferencias.CLOSE_SUNROOF_ON_FOLD_MIRROR.chave, false)) isolado("teto (retrovisor)") { acoes.fecharTeto(true) }
        if (prefs.booleano(Preferencias.DISABLE_BLUETOOTH_ON_FOLD_MIRROR.chave, false)) {
            isolado("bluetooth (retrovisor)") { sistema.desligarBluetoothGuardando("retrovisor") }
        }
        if (prefs.booleano(Preferencias.DISABLE_HOTSPOT_ON_FOLD_MIRROR.chave, false)) {
            isolado("hotspot (retrovisor)") { sistema.desligarHotspotGuardando() }
        }
    }

    /** B.6. */
    private fun velocidade(valor: String) {
        val v = numeroDoCarro(valor)
        if (v == null) {
            registro.aviso("velocidade ilegível '$valor'; evento ignorado")
            return
        }
        if (v > prefs.decimal(Preferencias.SPEED_THRESHOLD.chave, LIMIAR_PADRAO) && !vidrosFechadosPelaVelocidade) {
            if (prefs.booleano(Preferencias.CLOSE_WINDOWS_ON_SPEED.chave, false)) isolado("vidros (velocidade)") { acoes.fecharTodasAsJanelas() }
            vidrosFechadosPelaVelocidade = true
        }
        if (v > prefs.decimal(Preferencias.SUNROOF_SPEED_THRESHOLD.chave, LIMIAR_PADRAO) && !tetoFechadoPelaVelocidade) {
            if (prefs.booleano(Preferencias.CLOSE_SUNROOF_ON_SPEED.chave, false)) isolado("teto (velocidade)") { acoes.fecharTeto(false) }
            tetoFechadoPelaVelocidade = true
        }
        if (v <= REARME_KMH && (vidrosFechadosPelaVelocidade || tetoFechadoPelaVelocidade)) {
            vidrosFechadosPelaVelocidade = false
            tetoFechadoPelaVelocidade = false
        }
        if (v <= 0 && prefs.booleano(Preferencias.DISABLE_AVM_CAR_STOPPED.chave, false)) {
            val marcha = carro.ler(MARCHA)?.trim()
            if (marcha.isNullOrEmpty() || emR(marcha)) return
            if (acoes.cameraAbertaPeloApp) return
            if (cameraAbertaPeloBotao && prefs.booleano(Preferencias.AVM_MANUAL_WHEN_STOPPED.chave, false)) return
            if (decisoes.cameraParadaUmaVezPorAbertura && cameraFechadaNestaAbertura) return
            acoes.fecharCamera360()
            cameraFechadaNestaAbertura = true
        }
    }

    /** B.7. Fora das condições, a marca do app não é limpa (D-A9, como hoje). */
    private fun cameraAbriuParada(valor: String) {
        val v = valor.trim()
        val abriuAgora = v == "1" && ultimoEstadoDaCamera != null && ultimoEstadoDaCamera != "1"
        ultimoEstadoDaCamera = v
        if (v != "1") cameraAbertaPeloBotao = false
        if (v == "1") cameraFechadaNestaAbertura = false
        if (!prefs.booleano(Preferencias.DISABLE_AVM_CAR_STOPPED.chave, false)) return
        val velocidade = numeroDoCarro(carro.ler(VELOCIDADE)) ?: return
        if (velocidade > 0) return
        val marcha = carro.ler(MARCHA)?.trim()
        if (marcha.isNullOrEmpty() || emR(marcha)) return
        if (v == "1") {
            // Parado, fora da ré, a câmera passou de fechada a aberta: foi o botão (a automática abre
            // andando ou em ré). Com a permissão ligada ela fica aberta até fechar.
            if (abriuAgora && prefs.booleano(Preferencias.AVM_MANUAL_WHEN_STOPPED.chave, false)) {
                cameraAbertaPeloBotao = true
                return
            }
            if (cameraAbertaPeloBotao && prefs.booleano(Preferencias.AVM_MANUAL_WHEN_STOPPED.chave, false)) return
            if (!acoes.cameraAbertaPeloApp) {
                acoes.fecharCamera360()
                cameraFechadaNestaAbertura = true
            }
        } else {
            acoes.cameraAbertaPeloApp = false
        }
    }

    /** B.8 (com D-A2). */
    private fun ready(valor: String) {
        val v = valor.trim()
        val desligado = RegrasDasAutomacoes.readyDesligado(v)
        if (decisoes.readyComBorda && v.isEmpty()) {
            registro.info("READY vazio: desconhecido; ignorado")
            return
        }
        val anterior = ultimoReady
        val ligado = !desligado
        if (decisoes.readyComBorda && anterior == ligado) return
        ultimoReady = ligado
        if (desligado) desligou() else ligou(anterior)
    }

    private fun desligou() {
        carroDesligado = true
        isolado("fechar ao desligar") { fecharAoDesligar("READY desligou") }
        if (prefs.booleano(Preferencias.DISABLE_BLUETOOTH_ON_POWER_OFF.chave, false)) {
            isolado("bluetooth (desligou)") { sistema.desligarBluetoothGuardando("desligou") }
        }
        if (prefs.booleano(Preferencias.DISABLE_HOTSPOT_ON_POWER_OFF.chave, false)) {
            isolado("hotspot (desligou)") { sistema.desligarHotspotGuardando() }
        }
        isolado("ar no máximo (desligou)") { if (acoes.arNoMaximoAtivo) acoes.sairDoArNoMaximo() }
    }

    private fun ligou(anterior: Boolean?) {
        carroDesligado = false
        isolado("religar bluetooth") { sistema.religarBluetoothSeFomosNos("ligou") }
        isolado("religar hotspot") { sistema.religarHotspotSeFomosNos() }
        isolado("ar no máximo (ligou)") {
            if (prefs.booleano(Preferencias.ENABLE_MAX_AC_ON_UNLOCK.chave, false) && !acoes.arNoMaximoAtivo) {
                acoes.ligarArNoMaximoSeQuente()
            }
        }
        isolado("híbrido (ligou)") { acoes.aplicarAlvoDeCargaHev("ao ligar") }
        isolado("perfil ao ligar") { ganchos.perfilAoLigarOCarro() }
        if (decisoes.cortinaACadaReady && anterior == false) isolado("cortina (READY)") { cortinaAoLigar("READY ligou") }
    }

    /** B.9: ar ligado → ventilação 3; desligado → 0; outro valor, nada. */
    private fun ventilacao(valorDoAr: String?, motivo: String) {
        if (!prefs.booleano(Preferencias.ENABLE_SEAT_VENTILATION_ON_AC_ON.chave, false)) return
        val nivel = when (valorDoAr?.trim()) {
            "1" -> "3"
            "0" -> "0"
            else -> null
        }
        anotar("ventilação do banco", mapOf("motivo" to motivo, "ar" to valorDoAr, "nivel" to nivel))
        if (nivel != null) carro.escrever(VENTILACAO_DO_MOTORISTA, nivel)
    }

    /** B.10. */
    private fun suavizacao() {
        if (prefs.booleano(Preferencias.ENABLE_MAX_AC_ON_UNLOCK.chave, false) && acoes.arNoMaximoAtivo) {
            acoes.suavizarArNoMaximo()
        }
    }

    // ── C. Fechar ao desligar ──

    private fun fecharAoDesligar(motivo: String) {
        val vidros = prefs.booleano(Preferencias.CLOSE_WINDOW_ON_POWER_OFF.chave, false)
        val teto = prefs.booleano(Preferencias.CLOSE_SUNROOF_ON_POWER_OFF.chave, false)
        if (!vidros && !teto) return
        val recusa = try {
            trava(false, false)
        } catch (e: Exception) {
            null
        }
        if (recusa != null) {
            registro.info("fechar ao desligar ($motivo) recusado: ${recusa.texto}")
            return
        }
        val agora = relogio()
        val ultimo = ultimoFecharAoDesligar
        if (ultimo != null && agora - ultimo < INTERVALO_FECHAR_AO_DESLIGAR_MS) return
        ultimoFecharAoDesligar = agora
        registro.info("fechar ao desligar: $motivo (vidros=$vidros, teto=$teto)")
        if (vidros) isolado("vidros (desligar)") { acoes.fecharTodasAsJanelas() }
        if (teto) isolado("teto (desligar)") { acoes.fecharTeto(true) }
    }

    // ── D. Partida ──

    /** D.1/D.2: os passos dentro da partida (cada um isolado, D-A7). */
    fun aoPartir(real: Boolean) {
        if (!real) {
            naLinha("funções do carro") { ganchos.funcoesDoCarro() }
            principal("projetores") { ganchos.iniciarProjetores() }
            return
        }
        isolado("volume inicial") { volumeInicial() }
        isolado("monitoramento") {
            if (prefs.booleano(Preferencias.DISABLE_MONITORING.chave, false)) acoes.monitoramento(false)
        }
        isolado("AVAS") {
            if (prefs.booleano(Preferencias.DISABLE_AVAS.chave, false)) acoes.somParaPedestres(false)
        }
        isolado("fechar vidros ao trancar") {
            if (prefs.booleano(Preferencias.CLOSE_WINDOW_ON_LOCK_VEHICLE.chave, false)) acoes.fecharJanelasAoTrancar(true)
        }
        isolado("brilho automático") {
            if (prefs.booleano(Preferencias.ENABLE_AUTO_BRIGHTNESS.chave, false)) acoes.ligarBrilhoAutomatico()
        }
        isolado("botões do volante") { migrarBotoesDoVolante() }
        isolado("apps do sistema") { sistema.garantirAppsDoSistema() }
        isolado("debloat") { sistema.garantirAppsDesativados() }
        isolado("consistência de viagem") { ganchos.iniciarConsistenciaDeViagem() }
    }

    /** D.8: depois do "pronto" do conjunto, sem bloquear a partida. */
    fun depoisDoPronto() {
        naLinha("funções do carro") { ganchos.funcoesDoCarro() }
        if (prefs.booleano(Preferencias.ENABLE_OPEN_SUNROOF_CURTAIN_ON_START.chave, false) &&
            cortinaDaPartidaRodou.compareAndSet(false, true)
        ) {
            naLinha("cortina ao ligar") { cortinaAoLigar("partida") }
        }
        agendados.agendar("conferencia", CONFERENCIA_MS) { conferencia() }
        agendados.agendar("hibrido8", HIBRIDO_1_MS) { isolado("híbrido +8 s") { acoes.aplicarAlvoDeCargaHev("partida+8s") } }
        agendados.agendar("hibrido18", HIBRIDO_2_MS) { isolado("híbrido +18 s") { acoes.aplicarAlvoDeCargaHev("partida+18s") } }
        agendados.repetir("monitor", MONITOR_MS, MONITOR_MS) { isolado("híbrido (monitor)") { acoes.aplicarAlvoDeCargaHev("monitor") } }
        naLinha("janelas livres") { janelasLivres() }
        principal("projetores") { ganchos.iniciarProjetores() }
    }

    /** Cancela tudo o que está agendado (conferência, híbrido, cortina, retentativas). */
    fun encerrar() {
        agendados.cancelarTodos()
        isolado("cancelar o ar no máximo") { acoes.cancelarAgendadosDoAr() }
        isolado("cancelar as voltas de bluetooth/hotspot") { sistema.cancelarRetentativas() }
    }

    /** D.3. */
    private fun volumeInicial() {
        if (!prefs.booleano(Preferencias.SET_STARTUP_VOLUME.chave, false)) return
        val volume = prefs.inteiro(Preferencias.STARTUP_VOLUME.chave, VOLUME_AUSENTE)
        if (volume == VOLUME_AUSENTE) return
        carro.escrever(VOLUME_DA_MIDIA, volume.toString())
    }

    // ── E. Conferência 6 s depois da partida ──

    private fun conferencia() {
        try {
            val ready = carro.lerDoCarro(READY)?.trim()
            when {
                ready.isNullOrEmpty() -> Unit
                RegrasDasAutomacoes.readyDesligado(ready) -> carroDesligado = true
                else -> carroDesligado = false
            }
            val ligado = !ready.isNullOrEmpty() && !RegrasDasAutomacoes.readyDesligado(ready)
            val arLigado = carro.lerDoCarro(AR_LIGADO)
            anotar(
                "conferência da partida",
                mapOf(
                    "ready" to ready,
                    "carroDesligado" to carroDesligado,
                    "bluetoothParaReligar" to prefs.booleano(Preferencias.BLUETOOTH_STATE_ON_POWER_OFF.chave, false),
                    "bluetoothLigado" to sistema.bluetoothLigado,
                    "ventilacaoComOAr" to prefs.booleano(Preferencias.ENABLE_SEAT_VENTILATION_ON_AC_ON.chave, false),
                    "ar" to arLigado,
                    "resultado" to when {
                        ligado -> "ligado: religar"
                        ready.isNullOrEmpty() -> "READY desconhecido: nada"
                        else -> "desligado: nada"
                    },
                ),
            )
            if (!ligado) return
            isolado("religar bluetooth (conferência)") { sistema.religarBluetoothSeFomosNos("conferência da partida") }
            isolado("religar hotspot (conferência)") { sistema.religarHotspotSeFomosNos() }
            isolado("ventilação (conferência)") { ventilacao(arLigado, "conferência") }
        } catch (e: Exception) {
            anotar("conferência da partida falhou", mapOf("erro" to e.javaClass.simpleName, "mensagem" to e.message))
        }
    }

    // ── G. Cortina do teto ao ligar ──

    private fun cortinaAoLigar(gatilho: String) {
        if (!prefs.booleano(Preferencias.ENABLE_OPEN_SUNROOF_CURTAIN_ON_START.chave, false)) return
        val agora = minutosDoDia()
        val inicio = RegrasDasAutomacoes.minutos(
            prefs.inteiro(Preferencias.OPEN_SUNROOF_CURTAIN_START_HOUR.chave, 18),
            prefs.inteiro(Preferencias.OPEN_SUNROOF_CURTAIN_START_MINUTE.chave, 0),
        )
        val fim = RegrasDasAutomacoes.minutos(
            prefs.inteiro(Preferencias.OPEN_SUNROOF_CURTAIN_END_HOUR.chave, 9),
            prefs.inteiro(Preferencias.OPEN_SUNROOF_CURTAIN_END_MINUTE.chave, 0),
        )
        val dentro = RegrasDasAutomacoes.dentroDaJanela(agora, inicio, fim)
        anotar(
            "cortina ao ligar",
            mapOf(
                "gatilho" to gatilho,
                "hora" to RegrasDasAutomacoes.hora(agora),
                "janela" to "${RegrasDasAutomacoes.hora(inicio)}–${RegrasDasAutomacoes.hora(fim)}",
                "dentro" to dentro,
            ),
        )
        if (!dentro) return
        val maxima = prefs.decimal(Preferencias.OPEN_SUNROOF_CURTAIN_MAX_TEMP.chave, SEM_CHECAGEM_DE_TEMPERATURA)
        if (maxima == SEM_CHECAGEM_DE_TEMPERATURA) {
            abrirCortinaEmBreve(gatilho)
        } else {
            temperaturaDaCortina(gatilho, maxima.toDouble(), 1)
        }
    }

    /** G.4: no boot a leitura volta nula por alguns segundos; 5 s × 6 tentativas no total. */
    private fun temperaturaDaCortina(gatilho: String, maxima: Double, tentativa: Int) {
        val crua = carro.lerDoCarro(TEMPERATURA_EXTERNA)
        val t = numeroDoCarro(crua)
        if (t == null) {
            if (tentativa < TENTATIVAS_DA_CORTINA) {
                anotar("cortina ao ligar: sem temperatura", mapOf("tentativa" to tentativa, "leitura" to crua))
                agendados.agendar("cortina", ESPERA_DA_TEMPERATURA_MS) { temperaturaDaCortina(gatilho, maxima, tentativa + 1) }
            } else {
                anotar("cortina ao ligar: desisti sem temperatura", mapOf("tentativa" to tentativa, "leitura" to crua))
            }
            return
        }
        val abre = t <= maxima
        anotar("cortina ao ligar: temperatura", mapOf("tentativa" to tentativa, "leitura" to crua, "maxima" to maxima, "abre" to abre))
        if (abre) abrirCortinaEmBreve(gatilho)
    }

    /** G.5. */
    private fun abrirCortinaEmBreve(gatilho: String) {
        agendados.agendar("cortina", ESPERA_PARA_ABRIR_A_CORTINA_MS) {
            isolado("abrir a cortina") { acoes.abrirCortina() }
            anotar("cortina ao ligar: pedido de abrir", mapOf("gatilho" to gatilho))
        }
    }

    // ── H. Migração dos botões do volante ──

    private fun migrarBotoesDoVolante() {
        if (!prefs.booleano(Preferencias.ENABLE_STEERING_WHEEL_CUSTOM_BUTTONS.chave, false)) return
        val botoes = listOf(
            Preferencias.STEERING_WHEEL_CUSTOM_BUTON1_ACTION_ORIGINAL.chave to "bean_sw_custom_key1_config",
            Preferencias.STEERING_WHEEL_CUSTOM_BUTON2_ACTION_ORIGINAL.chave to "bean_sw_custom_key2_config",
        )
        for ((preferencia, ajuste) in botoes) {
            val original = RegrasDasAutomacoes.originalDoBotao(prefs.texto(preferencia))
            if (original == null) {
                registro.info("botões do volante: $preferencia ausente ou inválido; $ajuste fica como está")
                continue
            }
            isolado("botão $ajuste") { shell.executar(arrayOf("settings", "put", "system", ajuste, original)) }
        }
        prefs.gravarBooleano(Preferencias.ENABLE_STEERING_WHEEL_CUSTOM_BUTTONS.chave, false)
    }

    // ── I. Ajustes do Android ──

    /** I.1: dentro da partida, antes dos receptores. */
    fun ajustesDoAndroidNaPartida(componente: String?, pacote: String) {
        if (componente.isNullOrBlank()) {
            registro.aviso("sem o componente do serviço de acessibilidade; a lista não é mexida")
        } else {
            isolado("serviços de acessibilidade") {
                val lista = if (decisoes.somarAcessibilidade) {
                    val atual = shell.executar(arrayOf("settings", "get", "secure", "enabled_accessibility_services"))
                    RegrasDasAutomacoes.somarServico(atual, componente)
                } else {
                    componente.trim()
                }
                if (lista != null) shell.executar(arrayOf("settings", "put", "secure", "enabled_accessibility_services", lista))
            }
            isolado("acessibilidade ligada") { shell.executar(arrayOf("settings", "put", "secure", "accessibility_enabled", "1")) }
        }
        isolado("WRITE_SECURE_SETTINGS") {
            shell.executar(arrayOf("pm", "grant", pacote, "android.permission.WRITE_SECURE_SETTINGS"))
        }
    }

    /** I.2: depois do "pronto"; falha ignorada. */
    private fun janelasLivres() {
        for (ajuste in listOf("enable_freeform_support", "force_resizable_activities")) {
            isolado("janelas livres: $ajuste") { shell.executar(arrayOf("sh", "-c", "settings put global $ajuste 1")) }
        }
    }

    // ── Interno ──

    private fun emP(marcha: String) = marcha == "3" || marcha.equals("P", ignoreCase = true)

    private fun emR(marcha: String) = marcha == "4" || marcha.equals("R", ignoreCase = true)

    private fun naLinha(oQue: String, tarefa: () -> Unit) {
        try {
            agenda.executar { isolado(oQue, tarefa) }
        } catch (e: Exception) {
            registro.erro("automações: não deu para enfileirar '$oQue'", e)
        }
    }

    private fun principal(oQue: String, tarefa: () -> Unit) {
        try {
            naPrincipal { isolado(oQue, tarefa) }
        } catch (e: Exception) {
            registro.erro("automações: não deu para mandar '$oQue' à thread principal", e)
        }
    }

    private inline fun isolado(oQue: String, bloco: () -> Unit) {
        try {
            bloco()
        } catch (e: Exception) {
            registro.erro("automação '$oQue' falhou", e)
        }
    }

    private fun anotar(evento: String, detalhes: Map<String, Any?>) {
        registro.info("$evento $detalhes")
        try {
            diario(evento, detalhes)
        } catch (e: Exception) {
            registro.aviso("diário falhou em '$evento'", e)
        }
    }

    companion object {
        const val CAMERA_360 = "sys.avm.preview_status"
        const val PAINEL_DO_AR = "car.hvac.panel_display_notify"
        const val DISTRACAO = "car.frs_setting.distraction_detection_enable"
        const val FADIGA = "car.frs_setting.fatigue_monitoring_enable"
        const val AVAS = "car.ev.setting.avas_enable"
        const val CAMERA_DO_MOTORISTA = "car.dms.work_state"
        const val RETROVISOR = "car.drive.setting.outside_view_mirror_fold_state"
        const val VELOCIDADE = "car.basic.vehicle_speed"
        const val MARCHA = "car.basic.gear_status"
        const val READY = "car.basic.driving_ready_state"
        const val AR_LIGADO = "car.hvac.power_mode"
        const val TEMPERATURA_INTERNA = "car.basic.inside_temp"
        const val TEMPERATURA_EXTERNA = "car.basic.outside_temp"
        const val ALVO_DE_CARGA = "car.ev_setting.charge_soc_target_config"
        const val RESERVA = "car.ev_setting.power_reserve_config"
        const val MODO_DE_POTENCIA = "car.ev_setting.power_model_config"
        const val VENTILACAO_DO_MOTORISTA = "car.comfort_setting.driver_seat_ventilation_level"
        const val VOLUME_DA_MIDIA = "sys.settings.audio.media_volume"

        const val DISPLAY_DO_PAINEL = 3
        const val LIMIAR_PADRAO = 15.0f
        const val REARME_KMH = 10.0
        const val INTERVALO_FECHAR_AO_DESLIGAR_MS = 10_000L
        const val VOLUME_AUSENTE = -1

        const val CONFERENCIA_MS = 6_000L
        const val HIBRIDO_1_MS = 8_000L
        const val HIBRIDO_2_MS = 18_000L
        const val MONITOR_MS = 30_000L

        const val SEM_CHECAGEM_DE_TEMPERATURA = -1.0f
        const val TENTATIVAS_DA_CORTINA = 6
        const val ESPERA_DA_TEMPERATURA_MS = 5_000L
        const val ESPERA_PARA_ABRIR_A_CORTINA_MS = 2_000L
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
