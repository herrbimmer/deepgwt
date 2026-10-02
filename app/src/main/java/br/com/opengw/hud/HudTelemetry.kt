package br.com.opengw.hud

import br.com.opengw.nucleo.Preferencias
import br.com.opengw.nucleo.OpenGw
import br.com.opengw.veiculo.Veiculo
import android.util.Log
import br.com.opengw.managers.WheelTorqueEstimator
import br.com.opengw.barra.MidiaDaBarra
import br.com.opengw.midia.RadioNowPlaying
import br.com.opengw.veiculo.Sinal
import br.com.opengw.veiculo.Sinais
/**
 * O que o HUD mostra, lido do barramento do carro.
 *
 * COMO OS VALORES CHEGAM
 *
 * O `ServiceManager` já é o único ponto de leitura do ControlService neste app: ele mantém um cache
 * e avisa quem estiver ouvindo. Aqui só se faz duas coisas — pedir que as chaves do HUD passem a
 * ser monitoradas ([ServiceManager.ensureKeysMonitored], porque a maioria delas não está na lista
 * padrão) e traduzir o texto cru em algo desenhável.
 *
 * MÍDIA NÃO VEM DO CARRO
 *
 * Título, artista e capa saem do `MediaSession` do Android, não do barramento: quem já os coleta é
 * a mídia da barra inferior ([MidiaDaBarra], mesmo processo), então a página de mídia lê de lá. Rádio é a exceção — a
 * estação é do carro e chega por `sys.radio.cur_channel_info`.
 *
 * O QUE ESTÁ MEDIDO E O QUE É LEITURA
 *
 * `car.basic.door_status` é um vetor com um slot por porta; os índices 0..3 (DE, DD, TE, TD) e o 5
 * (porta-malas) são os mesmos que o launcher 3D usa para desenhar o carro aberto, e o estado bate
 * com o veículo. O slot 4 não é usado. As lâmpadas `car.ipk_light.*` existem no dicionário
 * (`existence: FACT`), mas a semântica delas é INFERIDA: tratamos "diferente de 0 e de vazio" como
 * acesa, que é a convenção do resto do dicionário. Um alerta que nunca acender no carro é isso, e
 * está dito aqui em vez de escondido.
 */
object HudTelemetry {

    private const val TAG = "HudTelemetry"

    private val CHAVES =
            listOf(
                    Sinais.CAR_BASIC_VEHICLE_SPEED,
                    Sinais.CAR_MAP_TSR_NAV_SPEED_LIMIT,
                    Sinais.CAR_MAP_TSR_NAV_SPEED_LIMIT_SIGN_STATUS,
                    Sinais.CAR_BASIC_INSTANT_FUEL_CONSUMPTION,
                    Sinais.CAR_BASIC_REMAIN_FUEL_PERCENTAGE,
                    Sinais.CAR_EV_INFO_FUEL_MODE_REMAIN_ODOMETER,
                    Sinais.CAR_EV_INFO_ELECTRIC_MODE_REMAIN_ODOMETER,
                    Sinais.CAR_EV_INFO_CUR_BATTERY_POWER_PERCENTAGE,
                    Sinais.CAR_BASIC_TOTAL_ODOMETER,
                    Sinais.CAR_BASIC_OUTSIDE_TEMP,
                    Sinais.CAR_BASIC_INSIDE_TEMP,
                    Sinais.CAR_BASIC_GEAR_STATUS,
                    Sinais.CAR_BASIC_DOOR_STATUS,
                    // Quem decide se a cortina de alerta pode aparecer: ver [prontoParaDirigir].
                    Sinais.CAR_BASIC_DRIVING_READY_STATE,
                    Sinais.CAR_BASIC_TIREPRESS_WARNING,
                    Sinais.SYS_RADIO_CUR_CHANNEL_INFO,
                    Sinais.SYS_RADIO_RDS_CUR_CHANNEL_INFO,
                    // Sem o estado, a página de mídia mostraria a última estação sintonizada com o
                    // rádio desligado — ver [estacaoDeRadio].
                    Sinais.SYS_RADIO_PLAY_STATE,
                    // Powertrain: a potência sai do par tensão x corrente do pack (mesma conta do
                    // painel virtual e do Painel Esportivo); o torque é ESTIMADO pela velocidade
                    // (ver estimadorDeTorque), não por estas chaves.
                    Sinais.CAR_BASIC_ENGINE_SPEED,
                    Sinais.CAR_EV_INFO_MOTOR_SPEED,
                    Sinais.CAR_EV_INFO_REAR_MOTOR_SPEED,
                    Sinais.CAR_EV_INFO_MOTOR_POWER,
                    Sinais.CAR_EV_INFO_POWER_BATTERY_VOLTAGE,
                    Sinais.CAR_EV_INFO_POWER_BATTERY_CURRENT,
                    Sinais.CAR_DRIVE_SETTING_DRIVE_MODE,
                    // Lâmpadas de aviso do painel: o relatório pediu "outros alertas relevantes".
                    Sinais.CAR_IPK_LIGHT_SEAT_BELT_WARNING_INDICATOR,
                    // Qual cinto: vetor {DE, DD, TE, TC, TD} (ver [lugaresSemCinto]).
                    Sinais.CAR_BASIC_SEAT_BELT_WARNING,
                    Sinais.CAR_IPK_LIGHT_FUEL_LOW,
                    Sinais.CAR_IPK_LIGHT_TPMS_WARNING,
                    Sinais.CAR_IPK_LIGHT_ENGINE_MALFUNCTION,
                    Sinais.CAR_IPK_LIGHT_ENGINE_WATER_TEMP_HIGH,
                    Sinais.CAR_IPK_LIGHT_ENGINE_OIL_LOW_PRESSURE_WARNING,
                    Sinais.CAR_IPK_LIGHT_BRAKING_SYSTEM_INDICATOR,
                    Sinais.CAR_IPK_LIGHT_AIRBAG,
                    Sinais.CAR_IPK_LIGHT_EPARK_INDICATOR,
            )

    /**
     * O desenho que acompanha um alerta.
     *
     * São formas, não arquivos: o HUD tem densidade própria e qualquer drawable do app sairia com
     * tamanho errado lá. Cada valor é desenhado à mão em [HudOverlayView] (e espelhado na prévia
     * do instalador, `hud-editor/render.ts`). TEMPERATURA, OLEO e ESTACIONAMENTO entraram na 9.4:
     * antes óleo e superaquecimento usavam o bloco do motor e o freio de estacionamento o (!) do
     * freio, e três avisos diferentes tinham a mesma cara.
     */
    enum class Icone {
        PORTA,
        PORTA_MALAS,
        CINTO,
        MOTOR,
        TEMPERATURA,
        OLEO,
        PNEU,
        COMBUSTIVEL,
        FREIO,
        ESTACIONAMENTO,
        AVISO,
    }

    /**
     * Um alerta pronto para o para-brisa: desenho, o que aconteceu e onde.
     *
     * [detalhe] é o que responde "qual porta" / "qual cinto": vazio quando não há localização.
     * [grave] separa o que é de segurança imediata (vermelho, aro pulsando: cinto, airbag, freio,
     * óleo, temperatura, motor) do que é aviso (âmbar: portas, pneus, combustível, freio de
     * estacionamento) — a mesma divisão das lâmpadas do painel.
     */
    data class Alerta(
            val icone: Icone,
            val titulo: String,
            val detalhe: String = "",
            val grave: Boolean = true
    )

    /** O rótulo pequeno acima do título na cortina: de que sistema é o aviso. */
    fun categoriaDe(icone: Icone): String =
            when (icone) {
                Icone.CINTO -> "Segurança"
                Icone.PORTA, Icone.PORTA_MALAS -> "Carroceria"
                Icone.MOTOR, Icone.TEMPERATURA, Icone.OLEO -> "Motor"
                Icone.PNEU -> "Pneus"
                Icone.COMBUSTIVEL -> "Combustível"
                Icone.FREIO, Icone.ESTACIONAMENTO -> "Freios"
                Icone.AVISO -> "Atenção"
            }

    /** Nome de cada porta, na ordem dos slots de `car.basic.door_status`. */
    private val NOME_DA_PORTA =
            mapOf(
                    0 to "Dianteira esquerda",
                    1 to "Dianteira direita",
                    2 to "Traseira esquerda",
                    3 to "Traseira direita",
                    Veiculo.POSICAO_DO_PORTA_MALAS to "Porta-malas",
            )

    private val PORTA_ABREVIADA =
            mapOf(
                    "Dianteira esquerda" to "Diant. esq.",
                    "Dianteira direita" to "Diant. dir.",
                    "Traseira esquerda" to "Tras. esq.",
                    "Traseira direita" to "Tras. dir.",
            )

    /**
     * Lâmpada -> desenho + texto curto que cabe no HUD.
     *
     * Os títulos dizem o que fazer ou o que está errado ("Afivele o cinto", "Verifique os pneus"),
     * e não só o nome da lâmpada: "Cinto de segurança" sozinho não dizia se era aviso ou lembrete.
     */
    private val ALERTA_DA_LAMPADA =
            linkedMapOf(
                    Sinais.CAR_IPK_LIGHT_SEAT_BELT_WARNING_INDICATOR to
                            Alerta(Icone.CINTO, "Afivele o cinto"),
                    Sinais.CAR_IPK_LIGHT_AIRBAG to Alerta(Icone.AVISO, "Falha no airbag"),
                    Sinais.CAR_IPK_LIGHT_BRAKING_SYSTEM_INDICATOR to
                            Alerta(Icone.FREIO, "Verifique os freios"),
                    Sinais.CAR_IPK_LIGHT_ENGINE_OIL_LOW_PRESSURE_WARNING to
                            Alerta(Icone.OLEO, "Pressão do óleo baixa", "Pare assim que puder"),
                    Sinais.CAR_IPK_LIGHT_ENGINE_WATER_TEMP_HIGH to
                            Alerta(Icone.TEMPERATURA, "Motor superaquecido", "Pare assim que puder"),
                    Sinais.CAR_IPK_LIGHT_ENGINE_MALFUNCTION to
                            Alerta(Icone.MOTOR, "Verifique o motor"),
                    Sinais.CAR_IPK_LIGHT_TPMS_WARNING to
                            Alerta(Icone.PNEU, "Verifique os pneus", grave = false),
                    Sinais.CAR_IPK_LIGHT_FUEL_LOW to
                            Alerta(Icone.COMBUSTIVEL, "Combustível baixo", grave = false),
                    Sinais.CAR_IPK_LIGHT_EPARK_INDICATOR to
                            Alerta(
                                    Icone.ESTACIONAMENTO,
                                    "Freio de estacionamento",
                                    "Acionado com o carro andando",
                                    grave = false
                            ),
            )

    /**
     * Nome de cada lugar, na ordem dos slots de `car.basic.seat_belt_warning` ({DE, DD, TE, TC, TD},
     * 1 = solto). A ordem é a que o launcher 3D já usa para desenhar os cintos (www/index.html,
     * leitura `seat_belt_warning`); no carro só o `{1,0,0,0,0}` do motorista sem cinto foi visto —
     * os outros slots seguem essa leitura e estão **a confirmar**.
     */
    private val NOME_DO_LUGAR = listOf("Motorista", "Passageiro", "Traseiro esquerdo", "Traseiro central", "Traseiro direito")
    private val LUGAR_ABREVIADO = listOf("Motorista", "Passageiro", "Tras. esq.", "Tras. centro", "Tras. dir.")

    /** Velocidade a partir da qual o freio de estacionamento acionado vira aviso. */
    private const val EPARK_ANDANDO_KMH = 5

    /**
     * O último valor de cada chave, aqui do lado.
     *
     * O desenho do HUD NÃO pode chamar `ServiceManager.getData` a cada quadro: quando a chave não
     * está no cache do ServiceManager — e uma chave que o carro nunca responde nunca entra lá —
     * cada leitura vira uma chamada de binder ao ControlService. Vinte e uma chaves a cada segundo,
     * na thread que desenha, por tempo indeterminado. Com o espelho aqui, o desenho só lê memória e
     * quem escreve é o ouvinte.
     */
    private val valores = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** As chaves do farol, para o modo noturno automático ([farolAceso]). */
    private val CHAVES_DO_FAROL =
            br.com.opengw.veiculo.HeadlightPolicy.CHAVES_DE_ORIGEM +
                    br.com.opengw.veiculo.HeadlightPolicy.CHAVE_DO_CONTRATO

    private val ouvinte = Veiculo.Ouvinte { chave, valor ->
        if (CHAVES.any { it.chave == chave } || chave in CHAVES_DO_FAROL) {
            valores[chave] = valor.orEmpty()
            if (chave == Sinais.CAR_BASIC_VEHICLE_SPEED.chave) amostrarVelocidade(valor)
            HudOverlayController.requestRedraw()
        }
    }

    /**
     * O torque que a página de powertrain mostra, deduzido do movimento do carro.
     *
     * P/ω não produz número nesta central: `motor_speed` vem 0 e `rear_motor_speed` vazia na
     * captura do veículo, e a página mostrava `--` para sempre. É o mesmo estimador do Painel
     * Esportivo, alimentado aqui a cada leitura nova de velocidade — os dois mostradores dizem o
     * mesmo número. Acesso sincronizado: quem escreve é o binder, quem lê é o desenho.
     */
    private val estimadorDeTorque = WheelTorqueEstimator()

    private fun amostrarVelocidade(cru: String?) {
        synchronized(estimadorDeTorque) {
            estimadorDeTorque.sample(
                    cru?.trim()?.toFloatOrNull(),
                    android.os.SystemClock.elapsedRealtime()
            )
        }
    }

    @Volatile private var ligado = false
    @Volatile private var ultimaPrimeMs = 0L

    /**
     * Quantos pedaços do app precisam desta telemetria agora.
     *
     * POR QUE CONTAR EM VEZ DE UM BOOLEANO
     *
     * Havia um dono só — a Activity do HUD. Agora há dois: a tela de Telas também liga a
     * telemetria enquanto as miniaturas das páginas estão à vista, para elas mostrarem os números
     * de verdade do carro em vez de traços. Com um booleano, fechar a tela de Telas chamaria
     * [stop] e apagaria a telemetria do HUD que estava no ar do lado. Quem chama [start] é quem
     * chama [stop]; a escuta só cai quando o último interessado sai.
     */
    private var interessados = 0

    /** Começa a ouvir. Chamada pela Activity do HUD ao subir e pela tela de Telas ao mostrar as prévias. */
    @Synchronized
    fun start() {
        interessados++
        if (ligado) return
        ligado = true
        runCatching { Veiculo.acompanhar(CHAVES.map { it.chave } + CHAVES_DO_FAROL) }
                .onFailure { Log.w(TAG, "Não foi possível registrar as chaves do HUD", it) }
        Veiculo.observar(ouvinte)
        primeirasLeituras()
    }

    /** Larga a escuta, mas só quando o último interessado sair. Ver [interessados]. */
    @Synchronized
    fun stop() {
        if (interessados > 0) interessados--
        if (interessados > 0) return
        if (!ligado) return
        ligado = false
        runCatching { Veiculo.pararDeObservar(ouvinte) }
        valores.clear()
        synchronized(estimadorDeTorque) { estimadorDeTorque.reset() }
        ultimaPrimeMs = 0L
    }

    /**
     * Preenche o espelho uma vez. Repetida no máximo a cada [INTERVALO_PRIME_MS] enquanto faltar
     * chave: subir o HUD antes de o ControlService responder é normal (a camada pode voltar no
     * boot), e sem uma segunda tentativa o HUD ficaria com traços até alguém mexer no carro.
     */
    private fun primeirasLeituras() {
        val agora = android.os.SystemClock.uptimeMillis()
        if (agora - ultimaPrimeMs < INTERVALO_PRIME_MS) return
        ultimaPrimeMs = agora
        for (c in CHAVES.map { it.chave } + CHAVES_DO_FAROL) {
            if (valores.containsKey(c)) continue
            val v = runCatching { Veiculo.ler(c) }.getOrNull() ?: continue
            valores[c] = v
        }
    }

    /**
     * Farol aceso, pela mesma regra e preferência dos temas do painel (Alpha 9.5, modo noturno do
     * HUD). Lê só o espelho [valores]: é chamada dentro do desenho.
     */
    fun farolAceso(): Boolean {
        val politica = br.com.opengw.veiculo.HeadlightPolicy
        val fonte = runCatching {
            br.com.opengw.nucleo.OpenGw.contextoProtegido
                    .getSharedPreferences("opengw_prefs", android.content.Context.MODE_PRIVATE)
                    .getString(
                            br.com.opengw.nucleo.Preferencias.NIGHT_MODE_TRIGGER.chave,
                            politica.FONTE_PADRAO
                    )
        }.getOrNull() ?: politica.FONTE_PADRAO
        return politica.resolver(fonte, politica.sinaisDe { valores[it] }, politica.horaLocal())
    }

    private const val INTERVALO_PRIME_MS = 5_000L

    private fun bruto(c: Sinal): String = bruto(c.chave)

    private fun bruto(chave: String): String {
        val v = valores[chave]
        if (v != null) return v
        // Falta chave: tenta completar (no máximo uma vez a cada 5 s) e responde com o que houver.
        if (ligado) primeirasLeituras()
        return valores[chave].orEmpty()
    }

    private fun numero(c: Sinal): Float? = bruto(c).trim().toFloatOrNull()

    /** Uma lâmpada está acesa quando o valor existe e não é 0 — escalar ou vetor. */
    private fun acesa(c: Sinal): Boolean = HudFormato.algumAceso(bruto(c)) == true

    // ---------------------------------------------------------------- o que o HUD desenha

    /** Velocidade inteira, ou null quando o carro ainda não respondeu. */
    fun velocidadeKmh(): Int? = numero(Sinais.CAR_BASIC_VEHICLE_SPEED)?.toInt()

    /**
     * Limite da via lido pela câmera/navegação, ou null.
     *
     * O status vem junto de propósito: sem ele não dá para distinguir "a via é 40" de "a câmera
     * perdeu a placa e o último número ficou velho". Só um status igual a 1 vale como placa válida.
     *
     * O número chega como vetor na captura do carro (`{-2147483648,-2147483648}` sem placa); o
     * `toFloat` direto nunca lia placa nenhuma nesse formato. [HudFormato.limiteDaVia] aceita os dois.
     */
    fun limiteDaViaKmh(): Int? {
        val status = bruto(Sinais.CAR_MAP_TSR_NAV_SPEED_LIMIT_SIGN_STATUS).trim().lowercase()
        if (status != "1" && status != "true" && status != "on" && status != "active") return null
        return HudFormato.limiteDaVia(bruto(Sinais.CAR_MAP_TSR_NAV_SPEED_LIMIT))
    }

    /**
     * A estação sintonizada, já legível: `"FM 103,9 MHz"`.
     *
     * Antes esta função devolvia o vetor CRU do barramento, e o HUD desenhava `{103900,0,1,1}` na
     * linha da estação. Devolve vazio com o rádio parado — senão a página de mídia anunciaria uma
     * estação enquanto o motorista ouve outra coisa pelo Bluetooth.
     */
    fun estacaoDeRadio(): String = radioTocando()?.linhaUnica.orEmpty()

    /**
     * As três chaves do rádio, ou null com ele parado.
     *
     * Lidas do espelho local, como todo o resto: antes esta leitura ia direto ao
     * `ServiceManager.getData`, que vira chamada de binder quando a chave não está no cache —
     * dentro do desenho, a cada quadro. As três chaves já estão em [CHAVES].
     */
    fun radioTocando(): RadioNowPlaying.Leitura? {
        val leitura = RadioNowPlaying.de { chave -> bruto(chave) }
        return leitura.takeIf { it.estaTocando && RadioNowPlaying.estacao(it.canal) != null }
    }

    /** Consumo instantâneo já interpretado (km/L andando, L/h parado, ou só elétrico). */
    fun consumo(): HudFormato.Consumo? =
            HudFormato.consumo(bruto(Sinais.CAR_BASIC_INSTANT_FUEL_CONSUMPTION))

    fun autonomiaCombustivelKm(): Int? =
            numero(Sinais.CAR_EV_INFO_FUEL_MODE_REMAIN_ODOMETER)?.toInt()

    fun autonomiaEletricaKm(): Int? =
            numero(Sinais.CAR_EV_INFO_ELECTRIC_MODE_REMAIN_ODOMETER)?.toInt()

    fun combustivelPct(): Int? = numero(Sinais.CAR_BASIC_REMAIN_FUEL_PERCENTAGE)?.toInt()

    fun bateriaPct(): Int? =
            numero(Sinais.CAR_EV_INFO_CUR_BATTERY_POWER_PERCENTAGE)?.toInt()

    fun odometroKm(): Int? = numero(Sinais.CAR_BASIC_TOTAL_ODOMETER)?.toInt()

    fun temperaturaExterna(): Float? = HudFormato.temperatura(bruto(Sinais.CAR_BASIC_OUTSIDE_TEMP))

    fun midiaTitulo(): String = MidiaDaBarra.agora.titulo.orEmpty().trim()

    fun midiaArtista(): String = MidiaDaBarra.agora.artista.orEmpty().trim()

    fun midiaAlbum(): String = MidiaDaBarra.agora.album.orEmpty().trim()

    fun midiaTocando(): Boolean = MidiaDaBarra.agora.tocando

    fun midiaCapa(): android.graphics.Bitmap? = MidiaDaBarra.agora.capa

    /** "Spotify", "Bluetooth"… ou vazio quando o app não é conhecido. */
    fun midiaApp(): String = HudFormato.appDeMidia(MidiaDaBarra.agora.pacote)

    /**
     * Portas abertas agora, pelo nome. Lista vazia = tudo fechado; null = o carro ainda não
     * respondeu (que é diferente de "tudo fechado" e o HUD não deve anunciar nada nesse caso).
     */
    fun portasAbertas(): List<String>? = portasAbertasEm(bruto(Sinais.CAR_BASIC_DOOR_STATUS))

    /** A leitura do vetor, separada do carro para poder ser testada sem ele. */
    internal fun portasAbertasEm(cru: String): List<String>? {
        // O carro manda `{1,0,0,0,0,0}`: com as chaves grudadas no primeiro e no último slot, a
        // dianteira esquerda e o porta-malas nunca eram lidos. [HudFormato.slots] tira as chaves.
        val slots = HudFormato.slots(cru) ?: return null
        val abertas = mutableListOf<String>()
        for ((indice, nome) in NOME_DA_PORTA) {
            val valor = slots.getOrNull(indice) ?: continue
            if (valor != 0) abertas.add(nome)
        }
        return abertas
    }

    /**
     * O carro está em READY (pronto para andar)?
     *
     * `car.basic.driving_ready_state` vale "1" com o sistema de alta tensão ativo; "0" e "-1" são
     * os dois jeitos de dizer que não está — é a mesma leitura que o ServiceManager já usa para
     * saber que o veículo desligou, repetida aqui em vez de importada porque o desenho do HUD lê
     * só o espelho local e nunca chama binder.
     *
     * A chave AUSENTE não é a mesma coisa que "não está pronto": enquanto o ControlService não
     * respondeu — e num carro que não publique esta chave, nunca — a resposta é `true`. Um aviso de
     * segurança que some sozinho porque uma leitura faltou é pior do que um aviso a mais; a
     * supressão só vale quando o carro efetivamente disse que não está em READY.
     */
    fun prontoParaDirigir(): Boolean {
        val cru = bruto(Sinais.CAR_BASIC_DRIVING_READY_STATE).trim()
        if (cru.isEmpty()) {
            return !valores.containsKey(Sinais.CAR_BASIC_DRIVING_READY_STATE.chave)
        }
        return cru != "0" && cru != "-1"
    }

    /**
     * Todos os alertas a mostrar, já em ordem de importância: porta aberta primeiro (é o que o
     * relatório pediu por nome), depois as lâmpadas na ordem de [ALERTA_DA_LAMPADA].
     *
     * FORA DE READY, NADA. O HUD é o para-brisa de quem está dirigindo: com o carro parado e
     * desligado, porta aberta, freio de estacionamento e cinto solto são o estado NORMAL, e a
     * cortina de alerta cobre a página inteira para anunciar o óbvio — e continuava lá enquanto o
     * motorista carregava as compras. A partir daqui a cortina só existe com o carro em READY, que
     * é quando um aviso desses de fato pede ação.
     */
    fun alertas(): List<Alerta> {
        if (!prontoParaDirigir()) return emptyList()
        val lampadas = mutableListOf<Alerta>()
        for ((lampada, alerta) in ALERTA_DA_LAMPADA) {
            if (!acesa(lampada)) continue
            lampadas +=
                    when (alerta.icone) {
                        Icone.CINTO -> alertaDoCinto(lugaresSemCinto(bruto(Sinais.CAR_BASIC_SEAT_BELT_WARNING)))
                        Icone.COMBUSTIVEL ->
                                autonomiaCombustivelKm()?.takeIf { it > 0 }
                                        ?.let { alerta.copy(detalhe = "Autonomia ${HudFormato.milhar(it)} km") }
                                        ?: alerta
                        // Parado em READY o freio de estacionamento acionado é o normal (P + freio):
                        // anunciar isso cobria o HUD a cada partida. Vira aviso só andando.
                        Icone.ESTACIONAMENTO ->
                                if ((velocidadeKmh() ?: 0) < EPARK_ANDANDO_KMH) continue else alerta
                        else -> alerta
                    }
        }
        return montarAlertas(portasAbertas(), lampadas)
    }

    /**
     * Os lugares com cinto solto, pelo nome. null = o carro não mandou o vetor (a lâmpada acesa
     * continua valendo, só sem dizer qual lugar).
     */
    internal fun lugaresSemCinto(cru: String): List<String>? {
        val slots = HudFormato.slots(cru) ?: return null
        return NOME_DO_LUGAR.indices.filter { (slots.getOrNull(it) ?: 0) != 0 }.map { NOME_DO_LUGAR[it] }
    }

    /** O alerta do cinto com o lugar no detalhe; três ou mais lugares vão abreviados. */
    internal fun alertaDoCinto(lugares: List<String>?): Alerta {
        val lista = lugares.orEmpty()
        val detalhe =
                lista.joinToString(" · ") {
                    if (lista.size >= 3) LUGAR_ABREVIADO[NOME_DO_LUGAR.indexOf(it)] else it
                }
        return Alerta(Icone.CINTO, if (lista.size >= 2) "Afivele os cintos" else "Afivele o cinto", detalhe)
    }

    /** A montagem do texto, separada das leituras do carro para poder ser testada sem ele. */
    internal fun montarAlertas(portas: List<String>?, lampadas: List<Alerta>): List<Alerta> {
        val saida = mutableListOf<Alerta>()
        if (portas != null && portas.isNotEmpty()) {
            // O porta-malas sozinho merece o próprio desenho e o próprio texto: "porta aberta"
            // mandaria o motorista olhar as quatro portas por engano.
            val soPortaMalas = portas.size == 1 && portas[0] == NOME_DA_PORTA[Veiculo.POSICAO_DO_PORTA_MALAS]
            saida.add(
                    when {
                        soPortaMalas -> Alerta(Icone.PORTA_MALAS, "Porta-malas aberto", grave = false)
                        portas.size == 1 -> Alerta(Icone.PORTA, "Porta aberta", portas[0], grave = false)
                        else ->
                                Alerta(
                                        Icone.PORTA,
                                        "${portas.size} portas abertas",
                                        // Três ou mais nomes por extenso não cabem nem em duas
                                        // linhas do HUD; abreviados, cabem e continuam claros.
                                        portas.joinToString(" · ") {
                                            if (portas.size >= 3) PORTA_ABREVIADA[it] ?: it else it
                                        },
                                        grave = false
                                )
                    }
            )
        }
        saida.addAll(lampadas)
        return saida
    }

    // ------------------------------------------------------------------------ powertrain

    /**
     * Potência instantânea em kW, ou null sem leitura confiável.
     *
     * Mesma derivação do Painel Esportivo — tensão x corrente do pack primeiro, `motor_power` só
     * como reserva e dentro de faixa plausível (ele devolve -1001 quando não tem leitura). Reusar a
     * função em vez de copiar a regra mantém os dois mostradores dizendo o mesmo número.
     */
    fun potenciaKw(): Float? =
            br.com.opengw.resolvePowerKw(
                    packVolts = numero(Sinais.CAR_EV_INFO_POWER_BATTERY_VOLTAGE),
                    packAmps = numero(Sinais.CAR_EV_INFO_POWER_BATTERY_CURRENT),
                    reportedPowerKw = numero(Sinais.CAR_EV_INFO_MOTOR_POWER),
            )

    /**
     * Torque de tração nas rodas, ESTIMADO pelo movimento ([estimadorDeTorque]), nunca apresentado
     * como medição. Só o esforço de tração: freando a conta fica negativa, e isso não é torque
     * sendo gerado — mesma regra do Painel Esportivo.
     */
    fun torqueNm(): Float? =
            synchronized(estimadorDeTorque) { estimadorDeTorque.torqueNm() }?.coerceAtLeast(0f)

    fun rotacaoMotorRpm(): Int? = numero(Sinais.CAR_BASIC_ENGINE_SPEED)?.toInt()

    fun rotacaoMotorTraseiroRpm(): Int? =
            numero(Sinais.CAR_EV_INFO_REAR_MOTOR_SPEED)?.toInt()

    /**
     * O eixo traseiro está empurrando agora?
     *
     * O eixo traseiro desta central é elétrico: rotação traseira diferente de zero é o eixo traseiro
     * empurrando, o que se lê como AWD. Sem rotação traseira não dá para afirmar nada — e "4x2"
     * seria uma afirmação que o dado não sustenta. Antes o modo de condução ("Normal") aparecia
     * no lugar, com o rótulo "tração"; agora cada um tem o seu.
     */
    fun tracaoAwd(): Boolean {
        val traseiro = rotacaoMotorTraseiroRpm()
        return traseiro != null && traseiro != 0
    }

    private val MODO_DE_CONDUCAO =
            mapOf(
                    "0" to "Normal",
                    "1" to "Sport",
                    "2" to "Eco",
                    "3" to "Neve",
                    "4" to "Areia",
                    "5" to "Lama",
                    "11" to "AWD",
            )

    fun modoDeConducao(): String {
        val cru = bruto(Sinais.CAR_DRIVE_SETTING_DRIVE_MODE).trim()
        return MODO_DE_CONDUCAO[cru] ?: if (cru.isEmpty()) "--" else cru
    }

    fun marcha(): String = HudFormato.marcha(bruto(Sinais.CAR_BASIC_GEAR_STATUS)) ?: "--"

    fun temperaturaInterna(): Float? = HudFormato.temperatura(bruto(Sinais.CAR_BASIC_INSIDE_TEMP))

    /**
     * true quando o carro está avisando de pressão de pneu; null quando não respondeu.
     *
     * O valor é um vetor, um slot por roda (`{0,0,0,0}` na captura): qualquer slot aceso vale.
     */
    fun avisoDePneu(): Boolean? = HudFormato.algumAceso(bruto(Sinais.CAR_BASIC_TIREPRESS_WARNING))
}
