package br.com.opengw.managers.asd

import br.com.opengw.nucleo.OpenGw
import br.com.opengw.veiculo.Veiculo
import br.com.opengw.veiculo.comandos.LeiturasDoCarro
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import br.com.opengw.veiculo.Sinal
import br.com.opengw.veiculo.Sinais
import br.com.opengw.nucleo.Preferencias
import kotlin.math.min

/**
 * Orquestra o ASD (Active Sound Design): lê telemetria real do veículo via [ServiceManager]
 * (velocidade, marcha e estado do motor — e, best-effort, posição do pedal), alimenta o
 * [DrivetrainSimulator] a ~100Hz e envia o RPM/Load resultantes ao [AsdAudioEngine].
 *
 * QUANDO TOCA (Alpha 6.3): o usuário escolhe em [AsdAtivacao] — com o carro pronto para rodar
 * (READY, o comportamento de sempre) ou só enquanto o motor a combustão estiver girando de verdade,
 * detectado pelo giro real com histerese ([DetectorDeMotorACombustao]). Opcionalmente fica mudo em
 * P com o carro parado. Entrar e sair do som é sempre com fade ([AsdAudioEngine.fadeOut]), porque
 * no modo "motor a combustão" o som acompanha um motor que liga e desliga sozinho.
 *
 * O QUE TOCA: [AsdPerfilDeSom] — as amostras do motor a combustão (com rotação simulada ou, se o
 * usuário quiser, o giro real do motor quando ele está ligado) ou um zumbido elétrico sintetizado
 * que sobe com a velocidade.
 *
 * IMPORTANTE: não existe, hoje, nenhuma chave confirmada de posição do pedal do acelerador nesta
 * plataforma (Sinal não tinha uma até este recurso ser criado). [Sinais.CAR_BASIC_ACCELERATOR_PEDAL_POSITION]
 * é um chute não verificado. Por isso o acelerador não depende mais só dela: [AsdSignals] tenta
 * saída de energia e potência do motor antes de cair na estimativa por aceleração, e publica em
 * [uiFonteAcelerador] qual leitura venceu — conferir isso no carro é o que transforma a inferência
 * em fato (ou mostra que a chave precisa ser corrigida em Ajustes > ASD).
 *
 * O freio também deixou de ser `0.0` fixo: [AsdSignals.resolverFreio] deriva a intensidade de
 * frenagem da regeneração/potência negativa, que é o que dá ao câmbio simulado a redução de marcha
 * em desaceleração — antes ela nunca acontecia pelo motivo certo, porque o simulador nunca sabia
 * que o carro estava freando.
 */
object AsdManager {
    private const val TAG = "AsdManager"
    // ~100Hz. Era 20 ms: metade do atraso entre uma leitura nova do barramento e o som saía daqui.
    // O tick é aritmética barata (lê o cache do motor e avança o simulador).
    private const val TICK_MS = 10L
    private const val SAIDA_MAXIMA_MS = 2000L

    // A escala do fallback por aceleração mora em AsdSignals.ACELERACAO_DE_PE_NO_FUNDO, junto com
    // as outras fontes, para que a cascata inteira possa ser testada num lugar só.
    // Antes era uma arrancada rápida (WOT só de 1-10s, ~9s pra passar pelas 8 marchas — menos de
    // 1.5s por marcha nas mais baixas). Rápido demais pra ouvir com atenção onde exatamente o
    // áudio corta — usuário relatou o corte em RPM diferente a cada tentativa (5000, depois 2000),
    // o que é consistente com "não deu tempo de ouvir direito" tanto quanto com uma causa real
    // variável. WOT agora sustentado por muito mais tempo (~45s) pra dar vários segundos de
    // permanência em cada marcha/faixa de RPM, inclusive na crítica ~2000rpm.
    private const val TEST_DURATION_S = 60.0
    private const val TEST_MAX_SPEED_MS = 90.0 // teto de segurança, não precisa ser alcançado
    private const val TEST_ACCEL_MS2 = 2.0
    private const val TEST_ENGINE_BRAKE_MS2 = 1.8
    private const val TEST_WOT_END_S = 46.0

    private val prefs by lazy {
        OpenGw.contextoProtegido.getSharedPreferences("opengw_prefs", Context.MODE_PRIVATE)
    }

    private val drivetrain = DrivetrainSimulator()
    private val detectorDeCombustao = DetectorDeMotorACombustao()

    // Estado da máquina de ativação. Só é lido e escrito na thread do ASD ([handler]).
    // [soando]: motor de áudio e simulador ativos (inclusive durante a saída suave).
    // [saindo]: saída suave em curso; o desligamento de fato acontece quando ela termina.
    // [jaSoouNesteCiclo]: o motor simulado já rodou nesta ignição — a próxima partida é a quente.
    private var soando = false
    private var saindo = false
    private var saindoDesdeMs = 0L
    private var jaSoouNesteCiclo = false

    // Rotação e carga alisadas quando o som não vem do simulador (giro real ou perfil elétrico).
    // O barramento publica em degraus; sem alisar, cada degrau seria um salto de tom.
    private var rpmSemSimulador = 0.0
    private var cargaSemSimulador = 0.0
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var lastTickMs = 0L
    private var lastSpeedMs = 0.0
    private var started = false
    private var testElapsedS = 0.0
    private var testSpeedMs = 0.0
    private var lastLoggedTestSecond = -1
    // Se true, o teste ligou o motor simulado sozinho (motor real estava desligado) e deve
    // desligá-lo de novo ao terminar; se o motor real já estava ligado, o teste só "pega emprestado"
    // o ciclo normal e não deve parar nada ao final.
    private var testOwnsEngineOn = false

    // Estado observável pela UI (Compose lê estes campos diretamente; atualizados a partir da
    // thread do ASD, o que é seguro pois MutableState do Compose é thread-safe para leitura/escrita).
    val uiRpm = mutableFloatStateOf(0f)
    val uiGearLabel = mutableStateOf("–")
    val uiLoad = mutableFloatStateOf(0f)
    val uiThrottlePercent = mutableIntStateOf(0)
    val uiSpeedKmh = mutableFloatStateOf(0f)
    val uiEngineOn = mutableStateOf(false)
    val uiMotorACombustao = mutableStateOf(false)
    val uiSoando = mutableStateOf(false)
    val uiRotacaoReal = mutableStateOf(false)

    // Diagnóstico visível: de onde saiu o número que está governando o som, e como o câmbio está
    // configurado. Sem isto, a cascata de fontes de [AsdSignals] seria invisível e só um logcat
    // diria se a chave do pedal responde neste carro.
    val uiFonteAcelerador = mutableStateOf(AsdSignals.Fonte.NENHUMA)
    val uiFonteFreio = mutableStateOf(AsdSignals.Fonte.NENHUMA)
    val uiBrakePercent = mutableIntStateOf(0)
    val uiCadencia = mutableStateOf(DrivetrainSimulator.Cadencia.NORMAL)
    val uiFaseDaTroca = mutableStateOf(DrivetrainSimulator.Fase.NENHUMA)

    // "Testar ASD": simula uma arrancada completa (0 a ~220km/h e de volta) sem precisar do
    // carro em movimento, pra poder validar o motor de áudio parado, em P. Usa o mesmo
    // DrivetrainSimulator/AsdAudioEngine do fluxo real — só a fonte de throttle/velocidade muda
    // (roteirizada por tempo em vez de vir do barramento do veículo).
    val isTesting = mutableStateOf(false)

    // Rede de segurança do loop de controle: achado nesta investigação que a AsdManagerThread
    // podia morrer silenciosamente (thread-dump via kill -3 mostrava ela simplesmente ausente,
    // sem bloqueio, sem crash do processo) alguns segundos após tocar. Isso bate com uma exceção
    // não capturada escapando do Runnable de [scheduleTick] — uma vez que isso acontece, o Looper
    // daquela HandlerThread termina e o RPM/volume ficam congelados pra sempre (o áudio "corta e
    // não volta"). Capturar aqui garante que, seja lá o que for que lance (inclusive algo fora do
    // motor de áudio, que já tem sua própria rede de segurança em [AsdAudioEngine.update]), o
    // reagendamento do próximo tick sempre acontece — o loop nunca mais pode morrer de vez.
    val tickFailureCountState = mutableIntStateOf(0)
    private var tickFailureCount = 0

    private val telemetryListener = Veiculo.Ouvinte { key, _ -> onTelemetryChanged(key) }

    @Synchronized
    fun start() {
        if (started) return
        started = true
        AsdAudioEngine.setChannel(getAudioChannel())
        AsdAudioEngine.setBufferAprendido(prefs.getInt(Preferencias.ASD_LEARNED_BUFFER_FRAMES.chave, 0))
        AsdAudioEngine.aoAprenderBuffer = { frames ->
            prefs.edit().putInt(Preferencias.ASD_LEARNED_BUFFER_FRAMES.chave, frames).apply()
        }
        Veiculo.observar(telemetryListener)
        Veiculo.acompanhar(
            listOf(
                Sinais.CAR_BASIC_ENGINE_STATE.chave,
                // Giro real: decide "motor a combustão ligado" e alimenta a rotação real.
                Sinais.CAR_BASIC_ENGINE_SPEED.chave,
                Sinais.CAR_BASIC_VEHICLE_SPEED.chave,
                Sinais.CAR_BASIC_GEAR_STATUS.chave,
                // Fontes novas do acelerador/freio e a que define a cadência do câmbio. Pedir para
                // monitorar é o que faz o barramento publicar a chave; sem isso `getData` devolve
                // o valor que por acaso já estivesse em cache — ou nada.
                Sinais.CAR_EV_INFO_ENERGY_OUTPUT_PERCENTAGE.chave,
                Sinais.CAR_EV_INFO_ENERGY_RECOVERY_INFO.chave,
                Sinais.CAR_EV_INFO_MOTOR_POWER.chave,
                Sinais.CAR_EV_INFO_POWER_BATTERY_VOLTAGE.chave,
                Sinais.CAR_EV_INFO_POWER_BATTERY_CURRENT.chave,
                Sinais.CAR_DRIVE_SETTING_DRIVE_MODE.chave,
                Sinais.CAR_BASIC_EPB_STATE.chave,
                pedalPropertyKey()
            )
        )
        handlerThread = HandlerThread("AsdManagerThread").also { it.start() }
        handler = Handler(handlerThread!!.looper)
        lastTickMs = SystemClock.elapsedRealtime()
        scheduleTick()
        Log.i(TAG, "ASD manager started")
    }

    private fun scheduleTick() {
        handler?.postDelayed({
            try {
                tick()
            } catch (e: Exception) {
                tickFailureCount++
                tickFailureCountState.intValue = tickFailureCount
                Log.e(TAG, "ASD tick falhou, loop de controle continua: ${e.message}", e)
            }
            scheduleTick()
        }, TICK_MS)
    }

    private fun onTelemetryChanged(key: String) {
        // Ignição e giro decidem se o som existe; reagir na hora (e não no próximo tick) é o que
        // faz o som entrar junto com o motor. A decisão roda sempre na thread do ASD.
        if (key == Sinais.CAR_BASIC_ENGINE_STATE.chave || key == Sinais.CAR_BASIC_ENGINE_SPEED.chave) {
            handler?.post { avaliarAtivacao() }
        }
    }

    /**
     * Decide se o som deve existir agora e conduz a entrada/saída suave. Roda na thread do ASD a
     * cada tick e a cada mudança de ignição/giro.
     */
    private fun avaliarAtivacao() {
        if (isTesting.value) return
        // `engine_state` com ignição: 11 (ligado/READY parado), 12 (partida), 13 (rodando).
        val ignicao = LeiturasDoCarro.ignicaoLigada(Veiculo.ler(Sinais.CAR_BASIC_ENGINE_STATE.chave))
        val combustao = if (ignicao) {
            detectorDeCombustao.atualizar(Veiculo.ler(Sinais.CAR_BASIC_ENGINE_SPEED.chave))
        } else {
            detectorDeCombustao.zerar()
            jaSoouNesteCiclo = false
            false
        }
        if (uiEngineOn.value != ignicao) uiEngineOn.value = ignicao
        if (uiMotorACombustao.value != combustao) uiMotorACombustao.value = combustao

        val gearRaw = Veiculo.ler(Sinais.CAR_BASIC_GEAR_STATUS.chave)
        val velocidade = Veiculo.ler(Sinais.CAR_BASIC_VEHICLE_SPEED.chave)?.toDoubleOrNull() ?: 0.0
        val quer = isMasterEnabled() && AsdDecisao.deveSoar(
            ativacao = getAtivacao(),
            ignicaoLigada = ignicao,
            motorACombustao = combustao,
            silenciarEmP = isSilenciarEmP(),
            emP = gearRaw == "3" || gearRaw == "P",
            velocidadeKmh = velocidade
        )

        if (quer) {
            if (!soando || saindo) ligarSom()
        } else if (soando && !saindo) {
            saindo = true
            saindoDesdeMs = SystemClock.elapsedRealtime()
            AsdAudioEngine.fadeOut()
        }
        // O fade é conduzido pela thread de render; se ela estiver presa (recriando a faixa depois
        // de o audioserver cair, por exemplo), o silêncio nunca seria "concluído" e o som ficaria
        // pendurado. Passado o dobro do necessário, desliga assim mesmo.
        if (saindo && (AsdAudioEngine.saidaConcluida() ||
                SystemClock.elapsedRealtime() - saindoDesdeMs > SAIDA_MAXIMA_MS)
        ) {
            desligarSom()
        }
    }

    private fun ligarSom() {
        pushVolumeConfigToEngine()
        if (!drivetrain.running) drivetrain.start(quente = jaSoouNesteCiclo)
        AsdAudioEngine.start()
        soando = true
        saindo = false
        jaSoouNesteCiclo = true
        uiSoando.value = true
    }

    private fun desligarSom() {
        drivetrain.stop()
        AsdAudioEngine.stop()
        soando = false
        saindo = false
        uiSoando.value = false
        uiRotacaoReal.value = false
    }

    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        val dtSeconds = (now - lastTickMs).coerceAtLeast(1) / 1000.0
        lastTickMs = now

        if (isTesting.value) {
            testTick(dtSeconds)
            return
        }

        avaliarAtivacao()
        if (!drivetrain.running) return

        val speedKmh = Veiculo.ler(Sinais.CAR_BASIC_VEHICLE_SPEED.chave)?.toDoubleOrNull() ?: 0.0
        val gearRaw = Veiculo.ler(Sinais.CAR_BASIC_GEAR_STATUS.chave)
        // Barramento real: 2=D, 3=P, 4=R (ver InstrumentProjector2.getGearLabel). O
        // A fonte simulada do motor (build "simulator", QEMU) emite as letras já traduzidas ("D"/"P"/"N").
        val inDrive = gearRaw == "2" || gearRaw == "D"

        // A cadência é lida a cada tick e não guardada: o motorista pode trocar o modo de condução
        // no meio da curva, e o câmbio tem de responder na hora — o simulador só não interrompe uma
        // troca já em andamento.
        val cadencia = getCadenciaEscolhida().fixa
            ?: AsdSignals.cadenciaParaModo(Veiculo.ler(Sinais.CAR_DRIVE_SETTING_DRIVE_MODE.chave))
        drivetrain.cadencia = cadencia
        uiCadencia.value = cadencia

        val potenciaKw = readPowerKw()
        val acelerador = AsdSignals.resolverAcelerador(
            pedalBruto = Veiculo.ler(pedalPropertyKey())?.toDoubleOrNull(),
            saidaDeEnergiaBruta = Veiculo.ler(Sinais.CAR_EV_INFO_ENERGY_OUTPUT_PERCENTAGE.chave)?.toDoubleOrNull(),
            potenciaKw = potenciaKw,
            aceleracaoMs2 = (speedKmh / 3.6 - lastSpeedMs) / dtSeconds
        )
        val freio = AsdSignals.resolverFreio(
            regeneracaoBruta = Veiculo.ler(Sinais.CAR_EV_INFO_ENERGY_RECOVERY_INFO.chave)?.toDoubleOrNull(),
            potenciaKw = potenciaKw,
            // Só conta como freio parado: com o carro andando, o EPB puxado seria um defeito, e
            // tratar como frenagem máxima calaria o motor justamente enquanto ele deveria soar.
            freioDeMao = speedKmh < 1.0 && isParkingBrakeOn()
        )

        // O simulador avança SEMPRE, mesmo quando não é ele quem soa: se o usuário trocar de perfil
        // ou o motor real desligar no meio da viagem, a marcha simulada já está coerente com a
        // velocidade, em vez de nascer na 1ª a 100 km/h e subir as oito com o motor no corte.
        drivetrain.update(dtSeconds, acelerador.valor, freio.valor, speedKmh, inDrive)
        val rpmReal = AsdDecisao.rpmReal(
            getFonteDeRotacao(),
            detectorDeCombustao.girando,
            Veiculo.ler(Sinais.CAR_BASIC_ENGINE_SPEED.chave)
        )
        publicarNoMotorDeAudio(dtSeconds, speedKmh, acelerador.valor, freio.valor, rpmReal)
        uiThrottlePercent.intValue = (acelerador.valor * 100).toInt()
        uiBrakePercent.intValue = (freio.valor * 100).toInt()
        uiFonteAcelerador.value = acelerador.fonte
        uiFonteFreio.value = freio.fonte
        uiFaseDaTroca.value = drivetrain.fase
        uiSpeedKmh.floatValue = speedKmh.toFloat()

        lastSpeedMs = speedKmh / 3.6
    }

    /**
     * Escolhe o que vai para o motor de áudio: o simulador, o giro real do motor ou o zumbido
     * elétrico — e publica os números correspondentes para a tela.
     */
    private fun publicarNoMotorDeAudio(
        dt: Double,
        speedKmh: Double,
        acelerador: Double,
        freio: Double,
        rpmReal: Double?
    ) {
        val perfil = getPerfilDeSom()
        val usarSimulador = perfil == AsdPerfilDeSom.COMBUSTAO && rpmReal == null
        if (usarSimulador) {
            AsdAudioEngine.update(drivetrain.engineRpm, drivetrain.load)
            uiRpm.floatValue = drivetrain.engineRpm.toFloat()
            uiGearLabel.value = drivetrain.gearLabel
            uiLoad.floatValue = drivetrain.load.toFloat()
            if (uiRotacaoReal.value) uiRotacaoReal.value = false
            // Mantém o alisado colado no simulador, para uma troca para o giro real não saltar.
            rpmSemSimulador = drivetrain.engineRpm
            cargaSemSimulador = drivetrain.load
            return
        }

        val rpmAlvo = if (perfil == AsdPerfilDeSom.ELETRICO) {
            AsdDsp.rpmEquivalente(AsdDsp.frequenciaDoZumbido(AsdDsp.rotacaoEletricaVirtual(speedKmh)))
        } else {
            rpmReal!!
        }
        val cargaAlvo = AsdDecisao.cargaSemSimulador(acelerador, freio, speedKmh)
        if (rpmSemSimulador <= 0.0) rpmSemSimulador = rpmAlvo
        // ~80 ms na rotação: suaviza os degraus do barramento sem atrasar a resposta.
        rpmSemSimulador += (rpmAlvo - rpmSemSimulador) * min(1.0, dt * 12.0)
        // Carga: rápida ao pisar, mais lenta ao aliviar — a mesma assimetria do simulador.
        val kCarga = if (cargaAlvo > cargaSemSimulador) 14.0 else 6.0
        cargaSemSimulador += (cargaAlvo - cargaSemSimulador) * min(1.0, dt * kCarga)

        AsdAudioEngine.update(rpmSemSimulador, cargaSemSimulador)
        uiRpm.floatValue = rpmSemSimulador.toFloat()
        uiGearLabel.value = if (perfil == AsdPerfilDeSom.ELETRICO) "EV" else "real"
        uiLoad.floatValue = cargaSemSimulador.toFloat()
        val real = perfil == AsdPerfilDeSom.COMBUSTAO
        if (uiRotacaoReal.value != real) uiRotacaoReal.value = real
    }

    /**
     * Potência instantânea em kW pela MESMA conta do Painel Esportivo — tensão × corrente do pacote
     * quando as duas respondem, senão a potência relatada. Reaproveitar em vez de derivar de novo é
     * o que garante que o som e o mostrador não discordem sobre o que o carro está fazendo.
     */
    private fun readPowerKw(): Double? =
        br.com.opengw.resolvePowerKw(
            packVolts = Veiculo.ler(Sinais.CAR_EV_INFO_POWER_BATTERY_VOLTAGE.chave)?.toFloatOrNull(),
            packAmps = Veiculo.ler(Sinais.CAR_EV_INFO_POWER_BATTERY_CURRENT.chave)?.toFloatOrNull(),
            reportedPowerKw = Veiculo.ler(Sinais.CAR_EV_INFO_MOTOR_POWER.chave)?.toFloatOrNull()
        )?.toDouble()

    private fun isParkingBrakeOn(): Boolean {
        val epb = Veiculo.ler(Sinais.CAR_BASIC_EPB_STATE.chave)?.trim()
        if (!epb.isNullOrEmpty()) {
            epb.toFloatOrNull()?.let { return it != 0f }
            return epb.equals("true", ignoreCase = true)
        }
        val mao = Veiculo.ler(Sinais.CAR_BASIC_HAND_BRAKE_STATUS.chave)?.trim()
        if (mao.isNullOrEmpty()) return false
        mao.toFloatOrNull()?.let { return it != 0f }
        return mao.equals("true", ignoreCase = true)
    }

    private fun pedalPropertyKey(): String =
        prefs.getString(Preferencias.ASD_PEDAL_PROPERTY_KEY.chave, null)
            ?.takeIf { it.isNotBlank() }
            ?: Sinais.CAR_BASIC_ACCELERATOR_PEDAL_POSITION.chave

    /**
     * Inicia uma arrancada simulada (throttle roteirizado, não vindo do carro) pra validar o
     * motor de áudio do ASD sem precisar dirigir. Funciona parado, em P — liga o motor simulado
     * sozinho se o motor real ainda não estiver rodando.
     */
    @Synchronized
    fun startTest() {
        if (isTesting.value) return
        if (!isMasterEnabled()) return
        testElapsedS = 0.0
        testSpeedMs = 0.0
        lastLoggedTestSecond = -1
        isTesting.value = true
        AsdAudioEngine.beginDump()
        // `soando && !saindo`: se o som estava saindo, o teste o assume e o traz de volta.
        testOwnsEngineOn = !(soando && !saindo)
        if (testOwnsEngineOn) {
            pushVolumeConfigToEngine()
            AsdAudioEngine.start()
            if (!drivetrain.running) drivetrain.start()
        }
        Log.i(TAG, "Teste do ASD iniciado")
    }

    @Synchronized
    fun stopTest() {
        if (!isTesting.value) return
        isTesting.value = false
        // O teste ligou o som por conta própria: desliga, e o próximo tick decide de novo — se o
        // carro pedir som, ele volta com a entrada suave de sempre.
        if (testOwnsEngineOn) {
            drivetrain.stop()
            AsdAudioEngine.stop()
            soando = false
            saindo = false
            uiSoando.value = false
        }
        Log.i(TAG, "Teste do ASD encerrado")
    }

    private fun testTick(dt: Double) {
        testElapsedS += dt
        val t = testElapsedS

        // Roteiro: 0-1s sobe o pé (idle->WOT), 1s-TEST_WOT_END_S aceleração total sustentada
        // (WOT contínuo por ~45s, dando vários segundos de permanência em cada marcha/faixa de
        // RPM — antes eram só ~9s pra passar pelas 8 marchas inteiras, rápido demais pra ouvir
        // com atenção onde o áudio corta), depois tira o pé até um cruzeiro leve, mantém cruzeiro,
        // solta o pé de vez e freia-motor até o teste acabar.
        val throttle = when {
            t < 1.0 -> t / 1.0
            t < TEST_WOT_END_S -> 1.0
            t < TEST_WOT_END_S + 2.0 -> 1.0 - (t - TEST_WOT_END_S) / 2.0 * 0.7
            t < TEST_WOT_END_S + 6.0 -> 0.3
            t < TEST_WOT_END_S + 8.0 -> 0.3 * (1.0 - (t - (TEST_WOT_END_S + 6.0)) / 2.0)
            else -> 0.0
        }.coerceIn(0.0, 1.0)

        val accel = if (throttle > 0.02) throttle * TEST_ACCEL_MS2 else -TEST_ENGINE_BRAKE_MS2
        testSpeedMs = (testSpeedMs + accel * dt).coerceIn(0.0, TEST_MAX_SPEED_MS)
        val speedKmh = testSpeedMs * 3.6
        // A desaceleração do roteiro vira freio de verdade para o simulador. Sem isso a parte final
        // do teste exercitaria só o alívio do pedal, e as reduções em frenagem — que são metade do
        // que se quer OUVIR num teste de câmbio — nunca apareceriam.
        val brake = if (throttle > 0.02) 0.0 else (TEST_ENGINE_BRAKE_MS2 / AsdSignals.ACELERACAO_DE_PE_NO_FUNDO).coerceIn(0.0, 1.0)

        // O teste roda sempre em NORMAL: ele existe para comparar builds, e uma cadência que
        // variasse com o modo do carro tornaria duas execuções não comparáveis.
        drivetrain.cadencia = DrivetrainSimulator.Cadencia.NORMAL
        drivetrain.update(dt, throttle, brake, speedKmh, true)
        // O teste respeita o perfil escolhido; a rotação é sempre a simulada, porque parado o
        // motor real (se estiver ligado) não acompanha a arrancada roteirizada.
        publicarNoMotorDeAudio(dt, speedKmh, throttle, brake, rpmReal = null)
        uiThrottlePercent.intValue = (throttle * 100).toInt()
        uiBrakePercent.intValue = (brake * 100).toInt()
        uiCadencia.value = DrivetrainSimulator.Cadencia.NORMAL
        uiFaseDaTroca.value = drivetrain.fase
        uiSpeedKmh.floatValue = speedKmh.toFloat()

        // Diagnóstico: 1 linha/segundo com RPM/marcha/load, pra correlacionar no logcat com
        // exatamente que faixa de RPM coincide com um corte de áudio relatado, sem depender só
        // do ouvido — essencial já que o usuário relatou o corte em RPM diferente a cada tentativa.
        val second = t.toInt()
        if (second != lastLoggedTestSecond) {
            lastLoggedTestSecond = second
            Log.d(
                TAG,
                "Teste ASD t=${second}s rpm=${drivetrain.engineRpm.toInt()} " +
                    "marcha=${drivetrain.gear} load=${"%.2f".format(drivetrain.load)}"
            )
        }

        if (testElapsedS >= TEST_DURATION_S) {
            stopTest()
        }
    }

    fun testProgress(): Float = (testElapsedS / TEST_DURATION_S).toFloat().coerceIn(0f, 1f)

    /**
     * O ASD nasce DESLIGADO.
     *
     * Ele injeta som de motor pelos alto-falantes por cima do que o carro já toca: ninguém que
     * instala a central pela primeira vez pede isso sem saber, e um som que aparece sozinho na
     * primeira viagem lê-se como defeito, não como recurso. Quem quiser liga em Telas -> ASD, e a
     * escolha fica gravada — o padrão só vale enquanto a chave nunca foi escrita.
     */
    fun isMasterEnabled(): Boolean =
        prefs.getBoolean(Preferencias.ASD_MASTER_ENABLED.chave, false) && br.com.opengw.licenca.Portao.permite(br.com.opengw.licenca.Recurso.ASD)

    fun setMasterEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(Preferencias.ASD_MASTER_ENABLED.chave, enabled).apply()
        if (!enabled) {
            // Desligar pela tela é imediato, sem fade: é o usuário pedindo silêncio.
            naThreadDoAsd {
                if (isTesting.value) stopTest()
                desligarSom()
            }
        } else {
            naThreadDoAsd { avaliarAtivacao() }
        }
    }

    fun getVolume(): Int = prefs.getInt(Preferencias.ASD_VOLUME.chave, 50)

    fun setVolume(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        prefs.edit().putInt(Preferencias.ASD_VOLUME.chave, clamped).apply()
        AsdAudioEngine.setMasterVolume(clamped)
    }

    fun getPedalPropertyKeyOverride(): String =
        prefs.getString(Preferencias.ASD_PEDAL_PROPERTY_KEY.chave, "") ?: ""

    fun setPedalPropertyKeyOverride(key: String) {
        prefs.edit().putString(Preferencias.ASD_PEDAL_PROPERTY_KEY.chave, key.trim()).apply()
        if (started) {
            Veiculo.acompanhar(listOf(pedalPropertyKey()))
        }
    }

    // Canal de saída fixo em ALARME: é o único canal que a central roteia de forma confiável para
    // o alto-falante independentemente do estado de mídia/foco, então não é mais escolhível pelo
    // usuário — o ASD sempre sai pelo canal de alarme.
    fun getAudioChannel(): AsdAudioChannel = AsdAudioChannel.ALARM

    fun getVolumeMode(): AsdVolumeMode =
        AsdVolumeMode.fromKey(prefs.getString(Preferencias.ASD_VOLUME_MODE.chave, null))

    fun setVolumeMode(mode: AsdVolumeMode) {
        prefs.edit().putString(Preferencias.ASD_VOLUME_MODE.chave, mode.name).apply()
        AsdAudioEngine.setVolumeMode(mode)
    }

    fun getRpmVolumeLow(): Int = prefs.getInt(Preferencias.ASD_VOLUME_RPM_LOW.chave, 30)

    fun getRpmVolumeHigh(): Int = prefs.getInt(Preferencias.ASD_VOLUME_RPM_HIGH.chave, 80)

    fun setRpmVolumeCurve(lowPercent: Int, highPercent: Int) {
        val low = lowPercent.coerceIn(0, 100)
        val high = highPercent.coerceIn(0, 100)
        prefs.edit()
            .putInt(Preferencias.ASD_VOLUME_RPM_LOW.chave, low)
            .putInt(Preferencias.ASD_VOLUME_RPM_HIGH.chave, high)
            .apply()
        AsdAudioEngine.setRpmVolumeCurve(low, high)
    }

    fun getPositionX(): Int = prefs.getInt(Preferencias.ASD_POSITION_X.chave, 0)

    fun getPositionY(): Int = prefs.getInt(Preferencias.ASD_POSITION_Y.chave, 0)

    /** Salva a posição espacial. x/y em -100..+100 (X: esquerda→direita, Y: trás→frente). */
    fun setPosition(xPercent: Int, yPercent: Int) {
        val x = xPercent.coerceIn(-100, 100)
        val y = yPercent.coerceIn(-100, 100)
        prefs.edit()
            .putInt(Preferencias.ASD_POSITION_X.chave, x)
            .putInt(Preferencias.ASD_POSITION_Y.chave, y)
            .apply()
        AsdAudioEngine.setPosition(x / 100f, y / 100f)
    }

    // Abafamento FIXO EM 100% desde 2026-09-16. O ajuste era um slider e um interruptor; na
    // prática qualquer valor abaixo de 100 deixava o motor simulado soando dentro da cabine em vez
    // de vindo de fora, que é o efeito que se quer. Em vez de manter um controle cujo único valor
    // útil é o máximo, o valor virou constante e o controle saiu da tela (ver AsdScreen).
    const val MUFFLE_AMOUNT = 100

    fun isMuffleEnabled(): Boolean = true

    fun getMuffleAmount(): Int = MUFFLE_AMOUNT

    /** Mantida pela ponte GwLink e pelo boot; o valor é sempre o mesmo. */
    fun setMuffle(enabled: Boolean = true, amountPercent: Int = MUFFLE_AMOUNT) {
        prefs.edit()
            .putBoolean(Preferencias.ASD_MUFFLE_ENABLED.chave, true)
            .putInt(Preferencias.ASD_MUFFLE_AMOUNT.chave, MUFFLE_AMOUNT)
            .apply()
        AsdAudioEngine.setMuffle(true, MUFFLE_AMOUNT)
    }

    /**
     * Relê as preferências e faz o motor de som obedecer a elas — sem tocar em nenhuma.
     *
     * Existe para a troca de perfil de motorista: ali as preferências são gravadas em bloco, por
     * fora dos `set*` desta classe, e sem este empurrão o ASD continuaria tocando com o volume,
     * a curva e a posição de quem dirigia antes até a central reiniciar.
     */
    fun reconciliarComAsPreferencias() {
        if (isMasterEnabled()) {
            start()
            naThreadDoAsd { avaliarAtivacao() }
        } else {
            naThreadDoAsd { desligarSom() }
        }
        pushVolumeConfigToEngine()
    }

    /** Roda [bloco] na thread do ASD, ou aqui mesmo se o gerenciador ainda não foi iniciado. */
    private fun naThreadDoAsd(bloco: () -> Unit) {
        val h = handler
        if (h == null) bloco() else h.post(bloco)
    }

    /** Empurra volume fixo + modo + curva por RPM + posição + muffle para o motor de uma vez. */
    private fun pushVolumeConfigToEngine() {
        AsdAudioEngine.setMasterVolume(getVolume())
        AsdAudioEngine.setVolumeMode(getVolumeMode())
        AsdAudioEngine.setRpmVolumeCurve(getRpmVolumeLow(), getRpmVolumeHigh())
        AsdAudioEngine.setPosition(getPositionX() / 100f, getPositionY() / 100f)
        AsdAudioEngine.setMuffle(isMuffleEnabled(), getMuffleAmount())
        AsdAudioEngine.setPerfil(getPerfilDeSom())
        AsdAudioEngine.setTom(getTom())
    }

    // ── Opções de som e ativação (Alpha 6.3) ────────────────────────────────────────────────
    // Todas caem no `opengw_prefs` e, por isso, viajam sozinhas nos perfis de motorista
    // (EscopoDoPerfil inclui tudo o que não exclui explicitamente).

    fun getPerfilDeSom(): AsdPerfilDeSom =
        AsdPerfilDeSom.fromKey(prefs.getString(Preferencias.ASD_SOUND_PROFILE.chave, null))

    fun setPerfilDeSom(perfil: AsdPerfilDeSom) {
        prefs.edit().putString(Preferencias.ASD_SOUND_PROFILE.chave, perfil.name).apply()
        AsdAudioEngine.setPerfil(perfil)
    }

    fun getAtivacao(): AsdAtivacao =
        AsdAtivacao.fromKey(prefs.getString(Preferencias.ASD_ACTIVATION.chave, null))

    fun setAtivacao(ativacao: AsdAtivacao) {
        prefs.edit().putString(Preferencias.ASD_ACTIVATION.chave, ativacao.name).apply()
        naThreadDoAsd { avaliarAtivacao() }
    }

    fun getFonteDeRotacao(): AsdFonteDeRotacao =
        AsdFonteDeRotacao.fromKey(prefs.getString(Preferencias.ASD_RPM_SOURCE.chave, null))

    fun setFonteDeRotacao(fonte: AsdFonteDeRotacao) {
        prefs.edit().putString(Preferencias.ASD_RPM_SOURCE.chave, fonte.name).apply()
    }

    fun getCadenciaEscolhida(): AsdCadenciaEscolhida =
        AsdCadenciaEscolhida.fromKey(prefs.getString(Preferencias.ASD_CADENCE.chave, null))

    fun setCadenciaEscolhida(cadencia: AsdCadenciaEscolhida) {
        prefs.edit().putString(Preferencias.ASD_CADENCE.chave, cadencia.name).apply()
    }

    /** Tom do motor em porcentagem, -20 .. +20. */
    fun getTom(): Int = prefs.getInt(Preferencias.ASD_PITCH.chave, 0).coerceIn(-20, 20)

    fun setTom(percent: Int) {
        val clamped = percent.coerceIn(-20, 20)
        prefs.edit().putInt(Preferencias.ASD_PITCH.chave, clamped).apply()
        AsdAudioEngine.setTom(clamped)
    }

    fun isSilenciarEmP(): Boolean = prefs.getBoolean(Preferencias.ASD_MUTE_IN_PARK.chave, false)

    fun setSilenciarEmP(enabled: Boolean) {
        prefs.edit().putBoolean(Preferencias.ASD_MUTE_IN_PARK.chave, enabled).apply()
        naThreadDoAsd { avaliarAtivacao() }
    }
}
