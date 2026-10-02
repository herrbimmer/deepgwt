package br.com.opengw.managers

import br.com.opengw.nucleo.Shell
import br.com.opengw.nucleo.OpenGw
import br.com.opengw.veiculo.Veiculo
import android.content.Context
import android.util.Log
import br.com.opengw.macros.GestureStore
import br.com.opengw.macros.MacroGestureRouter
import br.com.opengw.barra.EstadoDaBarra
import br.com.opengw.veiculo.Sinais
import br.com.opengw.barra.RetornoDeGesto
import br.com.opengw.nucleo.Preferencia
import br.com.opengw.nucleo.Preferencias
import br.com.opengw.projection.ScreenProjectionController
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Gestos rápidos em qualquer tela, lidos direto do `/dev/input`.
 *
 * - 3 dedos pra cima / pra baixo  -> sobe / desce a temperatura (motorista E passageiro juntos)
 * - 3 dedos pra direita / esquerda -> aumenta / diminui a velocidade do ventilador
 * - 4 dedos, toque duplo          -> abre a seleção de área para projetar no painel
 *
 * Os dois grupos têm interruptores separados em Ajustes, mas compartilham este leitor: subir dois
 * `getevent` por nó de toque para ler o mesmo fluxo seria desperdício puro.
 *
 * ## O RETORNO NA TELA NÃO É ENFEITE
 *
 * Como este leitor **não consome** o toque (ver abaixo), nada na tela muda quando o carro obedece —
 * a menos que o app de clima esteja aberto, e quase nunca está. Quem arrasta três dedos não tem
 * como saber se foi entendido, se o arrasto foi curto demais, ou se a função está desligada. Na
 * dúvida, repete o gesto; e é assim que a temperatura vai parar no teto. Por isso cada gesto que
 * mexe no ar publica um [RetornoDeGesto], que o serviço da barra inferior mostra por ~2 s numa janela
 * intocável.
 *
 * ## Por que ler `/dev/input` em vez de usar a View/AccessibilityService
 *
 * Um gesto que precisa valer "em qualquer tela" não pode depender da nossa janela receber o toque:
 * a barra é um overlay de poucos dp na borda de baixo, e uma janela em tela cheia que capturasse
 * toque roubaria o toque do app da frente. `onMotionEvent` da AccessibilityService só existe a
 * partir da API 34 e este carro é Android 9 (ver [RemoteControlOps]).
 *
 * Ler o stream do `getevent` resolve os dois: enxergamos o multitoque cru de todas as telas e
 * **não consumimos nada** -- os eventos seguem normalmente pro app da frente. O preço é que o gesto
 * não cancela o toque que o app embaixo também recebeu; por isso o gatilho só dispara no
 * levantar dos dedos e exige deslocamento grande, pra não competir com scroll de 1 dedo.
 *
 * O parsing segue o protocolo multitouch B do Linux (slots + tracking id).
 */
object QuickGesturesManager {
    private const val TAG = "QuickGestures"

    /**
     * Deslocamento mínimo pra valer como gesto, como FRAÇÃO do eixo do próprio digitalizador.
     *
     * Não dá pra usar pixels aqui: o `getevent` entrega unidades cruas do painel, que raramente
     * batem com a resolução da tela -- o emulador, por exemplo, reporta o eixo em 0..32767. Medir
     * em fração do eixo vale igual nos dois casos.
     */
    private const val MIN_TRAVEL_FRACTION = 0.15f

    /** Fração adicional do eixo por degrau extra: um arrasto mais longo mexe mais. */
    private const val FRACTION_PER_EXTRA_STEP = 0.15f

    /** Teto de degraus por gesto, pra um arrastão não jogar a temperatura de ponta a ponta. */
    private const val MAX_STEPS = 6

    private const val TEMP_MIN = 16.0f
    private const val TEMP_MAX = 32.0f
    private const val TEMP_STEP = 0.5f
    private const val FAN_MIN = 0
    private const val FAN_MAX = 7

    private const val TRACKING_ID_UP = "ffffffff"

    /**
     * Deslocamento máximo, como fração do eixo, para um toque de 4 dedos ainda contar como TOQUE.
     *
     * Quatro dedos encostando na tela nunca param exatamente no mesmo pixel — a folga existe para
     * isso, e é pequena o bastante para não confundir com um arrasto de propósito.
     */
    private const val MAX_TRAVEL_FRACTION_TAP = 0.07f

    /**
     * Duração máxima de um toque, em ms. Mais que isso é um dedo apoiado, não um toque.
     *
     * Medida do PRIMEIRO dedo que encosta ao ÚLTIMO que sai. Com quatro dedos essa janela tem de
     * cobrir a mão inteira pousando e saindo, o que não acontece no mesmo instante: 400 ms era
     * apertado a ponto de recusar toques normais.
     */
    private const val MAX_TAP_MS = 650L

    /** Janela entre o primeiro e o segundo toque de 4 dedos. */
    private const val DOUBLE_TAP_WINDOW_MS = 900L

    /** Teto de nós de toque escutados ao mesmo tempo (tela principal + passageiro, com folga). */
    private const val MAX_TOUCH_DEVICES = 3

    private data class Slot(
        var x: Int = 0,
        var y: Int = 0,
        var startX: Int = 0,
        var startY: Int = 0,
        var active: Boolean = false,
        // Um flag por eixo: o X e o Y de um dedo chegam em linhas separadas do getevent, e um flag
        // só fazia a primeira linha (X) carimbar startY com o Y ainda zerado -- o gesto saía com a
        // direção vertical invertida e com deslocamento igual à coordenada absoluta.
        var hasStartX: Boolean = false,
        var hasStartY: Boolean = false,
        /**
         * Já recebeu coordenada em cada eixo neste gesto. Só zeram no reset do tracker, não no
         * levantar do dedo -- é o levantar que dispara a avaliação, e aí os dados ainda têm que
         * estar lá.
         */
        var sawX: Boolean = false,
        var sawY: Boolean = false,
        /**
         * O caminho deste dedo, em unidades cruas do digitalizador (x,y intercalados).
         *
         * Só é preenchido quando existe gesto desenhado gravado e ligado
         * ([GestureStore.temGestosAtivos]): os gestos de 3 e 4 dedos precisam apenas do começo e do
         * fim, e guardar centenas de pontos por toque para jogá-los fora seria desperdício num
         * leitor que roda o tempo todo.
         */
        val caminho: ArrayList<Int> = ArrayList()
    )

    /**
     * Teto de amostras guardadas por dedo (cada amostra são dois inteiros).
     *
     * 600 amostras são ~6 s de traço a 100 Hz. Um desenho leva bem menos; o teto existe para um
     * dedo apoiado na tela não encher a lista sem fim.
     */
    private const val MAX_AMOSTRAS = 600

    @Volatile private var running = false
    private val streamedDevices = java.util.Collections.synchronizedList(ArrayList<String>())

    /**
     * Estado de um gesto por dispositivo de toque.
     *
     * Precisa ser por dispositivo: a tela principal e a do passageiro são dois nós de `/dev/input`
     * independentes, cada um com a sua numeração de slot. Um estado só misturaria os dedos das duas
     * telas e contaria 3 dedos onde havia 2 + 1.
     */
    private class GestureTracker(val device: String, val rangeX: Int, val rangeY: Int) {
        val slots = HashMap<Int, Slot>()
        var currentSlot = 0
        var peakFingers = 0

        /** Quando o primeiro dedo deste gesto encostou. Só serve para separar toque de apoio. */
        var inicioDoGestoMs = 0L

        /**
         * Quando terminou o último toque válido de 4 dedos NESTE nó de toque.
         *
         * Mora no tracker, e não no objeto, pelo mesmo motivo que o resto do estado: a tela do
         * motorista e a do passageiro são dois nós independentes. Um contador só faria um toque em
         * cada tela contar como toque duplo.
         *
         * NÃO é zerado por [reset], que roda ao fim de cada gesto: é justamente entre um gesto e o
         * seguinte que este valor precisa sobreviver.
         */
        var ultimoToqueDe4Ms = 0L

        fun reset() {
            slots.clear()
            currentSlot = 0
            peakFingers = 0
            inicioDoGestoMs = 0L
        }

        fun slot(index: Int): Slot = slots.getOrPut(index) { Slot() }
    }

    private fun prefs() =
        OpenGw.contextoProtegido.getSharedPreferences("opengw_prefs", Context.MODE_PRIVATE)

    /** Gestos de clima: Premium (Alpha 8.0). O de projeção acompanha o círculo, que é Drive. */
    fun isEnabled(): Boolean =
        prefs().getBoolean(Preferencias.QUICK_GESTURES_ENABLED.chave, false) &&
            br.com.opengw.licenca.Portao.permite(br.com.opengw.licenca.Recurso.GESTOS)

    /**
     * O toque duplo com 4 dedos. Nasce LIGADO, ao contrário dos gestos de clima.
     *
     * São funções diferentes com interruptores diferentes (ver [Preferencia]): um gesto
     * que mexe na temperatura do carro merece opt-in, um que só abre uma seleção de área na tela
     * não desfaz nada e não precisa.
     */
    fun isProjectionGestureEnabled(): Boolean =
        prefs().getBoolean(Preferencias.PROJECTION_GESTURE_ENABLED.chave, true)

    fun setEnabled(enabled: Boolean) {
        prefs().edit().putBoolean(Preferencias.QUICK_GESTURES_ENABLED.chave, enabled).apply()
        reconciliar()
    }

    fun setProjectionGestureEnabled(enabled: Boolean) {
        prefs().edit().putBoolean(Preferencias.PROJECTION_GESTURE_ENABLED.chave, enabled).apply()
        reconciliar()
    }

    /**
     * Liga ou desliga o leitor conforme o estado dos DOIS interruptores.
     *
     * É um leitor só para os dois grupos de gesto, então nenhum dos dois pode decidir sozinho:
     * desligar os gestos de clima com o de projeção ligado tem de deixar o leitor de pé, e vice-
     * versa. Cada `set*` mexe na sua preferência e delega a decisão para cá — um único lugar que
     * sabe a regra.
     */
    private fun reconciliar() {
        // Cada um já responde pelo nível (Alpha 8.0): gestos de clima e de macro são Premium, o de
        // projeção acompanha o círculo (Drive).
        if (isEnabled() || isProjectionGestureEnabled() || GestureStore.temGestosAtivos()) start()
        else stop()
    }

    /**
     * Guardar ou não o caminho de cada dedo.
     *
     * Lido uma vez por gesto (no primeiro dedo que encosta) e não a cada linha do `getevent`: são
     * milhares de linhas por segundo, e uma leitura de SharedPreferences por linha seria I/O em
     * cima do fluxo de toque.
     */
    @Volatile private var guardandoCaminho = false

    /** Sobe o leitor se alguma das preferências pedir. Idempotente. */
    fun startIfEnabled() {
        reconciliar()
    }

    /**
     * O LEITOR NÃO PODE DESISTIR PORQUE O SHIZUKU AINDA NÃO SUBIU
     *
     * Havia aqui um `if (!isShizukuAvailable()) return` com um log e nada mais. Quem chama isto na
     * partida é o início do serviço principal, que roda ANTES de o binder do Shizuku
     * chegar — quase sempre. O leitor então nunca subia, e como os gestos de clima nascem
     * desligados, ninguém percebia: o único gesto ligado por padrão é o toque duplo com 4 dedos, e
     * ele simplesmente não existia no carro.
     *
     * Agora a espera é do supervisor, que já é um laço de repetição com sono. `running` passa a
     * true na hora, então `stop()` continua funcionando e chamadas repetidas continuam sendo
     * idempotentes — só a espera pelo shell mudou de lugar.
     */
    @Synchronized
    fun start() {
        if (running) return
        running = true
        Thread({ supervisorLoop() }, "QuickGestures-supervisor").start()
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        val devices = synchronized(streamedDevices) { streamedDevices.toList() }
        streamedDevices.clear()
        // runCommandOnBackground não devolve handle do processo, então o encerramento é pelo pkill
        // do comando exato que subimos.
        devices.forEach { device ->
            runCatching {
                Shell.executar(
                    arrayOf("sh", "-c", "pkill -f 'getevent -l $device' || true")
                )
            }
        }
        Log.w(TAG, "Quick gestures stopped")
    }

    /**
     * Nós de toque: os que anunciam ABS_MT_POSITION_X no `getevent -pl`.
     *
     * Escutar *todos* os `/dev/input` sem filtro traria os botões do volante e o resto do barramento
     * -- tráfego alto e inútil. Por outro lado, ficar num nó só erraria quando o carro tem tela do
     * passageiro, que é um nó separado. Daí o filtro por capacidade, com teto de [MAX_TOUCH_DEVICES]
     * pra um dispositivo que exponha dezenas de nós (o emulador expõe 11) não virar dezenas de
     * processos.
     */
    private fun findTouchDevices(): List<Pair<String, Pair<Int, Int>>> {
        val dump = Shell.executar(arrayOf("sh", "-c", "getevent -pl 2>/dev/null"))
        if (dump.isBlank()) return emptyList()
        val found = LinkedHashMap<String, Pair<Int, Int>>()
        var device: String? = null
        var rangeX = 0
        var rangeY = 0
        fun flush() {
            val current = device ?: return
            if (rangeX > 0 && rangeY > 0 && !found.containsKey(current)) {
                found[current] = rangeX to rangeY
            }
        }
        dump.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("add device")) {
                flush()
                device = trimmed.substringAfter(":").trim().takeIf { it.startsWith("/dev/input/") }
                rangeX = 0
                rangeY = 0
            } else if (trimmed.startsWith("ABS_MT_POSITION_X")) {
                rangeX = parseAxisRange(trimmed)
            } else if (trimmed.startsWith("ABS_MT_POSITION_Y")) {
                rangeY = parseAxisRange(trimmed)
            }
        }
        flush()
        return found.entries.take(MAX_TOUCH_DEVICES).map { it.key to it.value }
    }

    /**
     * Extrai `max - min` de uma linha de eixo do `getevent -pl`, por exemplo:
     * `ABS_MT_POSITION_X     : value 0, min 0, max 32767, fuzz 0, flat 0, resolution 0`
     */
    private fun parseAxisRange(line: String): Int {
        val min = Regex("min (-?\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: return 0
        val max = Regex("max (-?\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: return 0
        return (max - min).coerceAtLeast(0)
    }

    private fun supervisorLoop() {
        var avisouDaEspera = false
        while (running) {
            if (!Shell.disponivel()) {
                if (!avisouDaEspera) {
                    Log.w(TAG, "Shizuku ainda não está de pé; o leitor de gestos espera por ele")
                    avisouDaEspera = true
                }
                sleepQuietly(5_000)
                continue
            }
            if (avisouDaEspera) {
                Log.w(TAG, "Shizuku de pé: subindo o leitor de gestos")
                avisouDaEspera = false
            }
            val devices = findTouchDevices()
            if (devices.isEmpty()) {
                Log.w(TAG, "No multitouch device found in 'getevent -pl'; retrying in 30s")
                sleepQuietly(30_000)
                continue
            }
            synchronized(streamedDevices) {
                streamedDevices.clear()
                streamedDevices.addAll(devices.map { it.first })
            }
            Log.w(TAG, "Listening for 3-finger gestures on $devices")
            val done = java.util.concurrent.CountDownLatch(devices.size)
            devices.forEach { (device, ranges) -> streamDevice(device, ranges, done) }
            runCatching { done.await() }
            if (running) {
                Log.w(TAG, "getevent streams ended; restarting in 3s")
                sleepQuietly(3_000)
            }
        }
    }

    private fun streamDevice(
        device: String,
        ranges: Pair<Int, Int>,
        done: java.util.concurrent.CountDownLatch
    ) {
        val tracker = GestureTracker(device, ranges.first, ranges.second)
        Shell.emSegundoPlano(
            arrayOf("sh", "-c", "getevent -l $device"),
            object : Shell.Ouvinte {
                override fun linha(texto: String) {
                    if (!running) return
                    runCatching { handleLine(tracker, texto) }
                        .onFailure { Log.w(TAG, "Failed to parse '$texto'", it) }
                }

                override fun terminou(codigo: Int) {
                    done.countDown()
                }
            }
        )
    }

    private fun sleepQuietly(ms: Long) {
        runCatching { Thread.sleep(ms) }
    }

    /**
     * Uma linha do `getevent -l`, já sem o prefixo de device (streamamos um device por processo):
     * `EV_ABS       ABS_MT_POSITION_X    000003e8`
     */
    private fun handleLine(tracker: GestureTracker, raw: String) {
        val parts = raw.trim().split(Regex("\\s+"))
        if (parts.size < 3) return
        val code = parts[parts.size - 2]
        val valueHex = parts[parts.size - 1]
        when (code) {
            "ABS_MT_SLOT" -> tracker.currentSlot = valueHex.toIntOrNull(16) ?: 0
            "ABS_MT_TRACKING_ID" -> {
                val s = tracker.slot(tracker.currentSlot)
                if (valueHex == TRACKING_ID_UP) {
                    s.active = false
                    s.hasStartX = false
                    s.hasStartY = false
                    if (tracker.slots.none { it.value.active }) onAllFingersUp(tracker)
                } else {
                    s.active = true
                    s.hasStartX = false
                    s.hasStartY = false
                    s.caminho.clear()
                    val active = tracker.slots.count { it.value.active }
                    if (active > tracker.peakFingers) tracker.peakFingers = active
                    if (tracker.inicioDoGestoMs == 0L) {
                        tracker.inicioDoGestoMs = android.os.SystemClock.uptimeMillis()
                        // Uma leitura de preferência por GESTO, não por linha -- ver
                        // [guardandoCaminho].
                        guardandoCaminho = GestureStore.temGestosAtivos()
                    }
                }
            }
            "ABS_MT_POSITION_X" -> {
                val s = tracker.slot(tracker.currentSlot)
                s.x = valueHex.toIntOrNull(16) ?: return
                s.sawX = true
                if (!s.hasStartX) {
                    s.startX = s.x
                    s.hasStartX = true
                }
            }
            "ABS_MT_POSITION_Y" -> {
                val s = tracker.slot(tracker.currentSlot)
                s.y = valueHex.toIntOrNull(16) ?: return
                s.sawY = true
                if (!s.hasStartY) {
                    s.startY = s.y
                    s.hasStartY = true
                }
            }
            // Uma amostra por quadro do digitalizador, e não por linha: o X e o Y de um dedo chegam
            // em linhas separadas (e um eixo que não mudou não é reemitido), então amostrar em
            // qualquer uma das duas produziria pontos com metade da coordenada velha. O SYN_REPORT
            // é exatamente a marca de "este quadro está completo".
            "SYN_REPORT" -> {
                if (!guardandoCaminho) return
                tracker.slots.values.forEach { slot ->
                    if (!slot.active) return@forEach
                    if (!slot.sawX && !slot.sawY) return@forEach
                    if (slot.caminho.size >= MAX_AMOSTRAS * 2) return@forEach
                    slot.caminho.add(slot.x)
                    slot.caminho.add(slot.y)
                }
            }
        }
    }

    private fun onAllFingersUp(tracker: GestureTracker) {
        val fingers = tracker.peakFingers
        // `||`, não `&&`: o input core do Linux não reemite um eixo absoluto cujo valor não mudou,
        // então um arrasto perfeitamente vertical pode não gerar NENHUM ABS_MT_POSITION_X. Exigir os
        // dois eixos descartava justamente os gestos retos. Eixo sem evento = deslocamento zero
        // naquele eixo, que é o significado correto (o slot nasce zerado a cada gesto no reset).
        val moved = tracker.slots.values.filter { it.sawX || it.sawY }.toList()
        val duracaoMs = if (tracker.inicioDoGestoMs == 0L) Long.MAX_VALUE
        else android.os.SystemClock.uptimeMillis() - tracker.inicioDoGestoMs
        tracker.reset()

        if (fingers >= 4) {
            // `>= 4`, não `== 4`: quatro dedos na tela de um carro encostam quase sempre com a
            // palma ou o polegar de raspão, e o digitalizador reporta o quinto contato. Com a
            // igualdade, esse toque caía no `fingers != 3` lá embaixo e virava nada -- o gesto
            // "funcionava" só quando a mão encostava perfeitamente, que é quase nunca.
            avaliarToqueDe4(tracker, moved, duracaoMs)
            return
        }
        // Um e dois dedos são o território dos gestos DESENHADOS (ver [MacroGestureRouter]). Vêm
        // antes do corte dos 3 dedos porque são a faixa que o resto deste leitor sempre descartou:
        // nada aqui muda de comportamento para quem não gravou gesto nenhum.
        if (fingers <= 2) {
            avaliarGestoDesenhado(tracker, moved, fingers)
            return
        }
        if (fingers != 3 || moved.isEmpty()) return
        // O leitor sobe se QUALQUER um dos dois gestos estiver ligado (ver `reconciliar`), então
        // cada rota confere a própria preferência -- senão desligar os gestos de clima deixaria
        // eles funcionando enquanto o de projeção estivesse ligado.
        if (!isEnabled()) return

        val dx = moved.map { it.x - it.startX }.average()
        val dy = moved.map { it.y - it.startY }.average()
        val vertical = abs(dy) >= abs(dx)
        val range = (if (vertical) tracker.rangeY else tracker.rangeX).coerceAtLeast(1)
        val travel = (if (vertical) abs(dy) else abs(dx)) / range
        if (travel < MIN_TRAVEL_FRACTION) return

        val steps =
            (1 + (travel - MIN_TRAVEL_FRACTION) / FRACTION_PER_EXTRA_STEP)
                .roundToInt()
                .coerceIn(1, MAX_STEPS)
        if (vertical) {
            // Y cresce pra baixo: dy negativo = dedos subindo = mais quente.
            adjustTemperature(if (dy < 0) steps else -steps)
        } else {
            adjustFanSpeed(if (dx > 0) steps else -steps)
        }
    }

    /**
     * Um traço de um ou dois dedos: será que é um gesto gravado?
     *
     * A resposta é "não" na esmagadora maioria das vezes, e tem de ser barata. Por isso os cortes
     * vêm antes do reconhecedor: sem caminho guardado (ninguém gravou gesto), sai; com menos de
     * três amostras, sai; com dois dedos variando a distância entre si, é pinça e sai. Só o que
     * sobra vira um traço normalizado.
     *
     * Roda na própria thread do `getevent`: o reconhecimento é aritmética sobre 64 pontos por gesto
     * gravado — microssegundos — e a execução da macro já sai daqui para a fila do [MacroEngine].
     * Subir uma thread por arrasto de tela seria mais caro que o trabalho em si.
     */
    private fun avaliarGestoDesenhado(tracker: GestureTracker, moved: List<Slot>, dedos: Int) {
        if (!guardandoCaminho || dedos !in 1..2) return
        val comCaminho = moved.filter { it.caminho.size >= 6 }
        if (comCaminho.isEmpty()) return

        val rx = tracker.rangeX.coerceAtLeast(1).toFloat()
        val ry = tracker.rangeY.coerceAtLeast(1).toFloat()

        if (dedos == 2) {
            if (comCaminho.size < 2) return
            val a = comCaminho[0]
            val b = comCaminho[1]
            // Distâncias em fração de tela, não em unidades cruas: os eixos X e Y do digitalizador
            // raramente têm a mesma escala, e uma pinça horizontal mediria diferente de uma
            // vertical.
            val inicial = kotlin.math.hypot((a.startX - b.startX) / rx, (a.startY - b.startY) / ry)
            val fim = kotlin.math.hypot((a.x - b.x) / rx, (a.y - b.y) / ry)
            if (MacroGestureRouter.pareceUmaPinca(inicial, fim)) return
        }

        // O dedo com mais amostras é o que desenhou. Com dois dedos juntos os dois traçam a mesma
        // forma; o mais longo é simplesmente o que o digitalizador acompanhou melhor.
        val principal = comCaminho.maxByOrNull { it.caminho.size } ?: return
        val bruto = principal.caminho
        val normalizado = FloatArray(bruto.size)
        var i = 0
        while (i < bruto.size - 1) {
            normalizado[i] = bruto[i] / rx
            normalizado[i + 1] = bruto[i + 1] / ry
            i += 2
        }
        runCatching { MacroGestureRouter.aoTracar(normalizado, dedos) }
            .onFailure { Log.e(TAG, "Falha ao avaliar gesto desenhado", it) }
    }

    /**
     * Toque duplo com 4 dedos -> abre a seleção da área da central que vai para o painel.
     *
     * Exige TOQUE, não arrasto: quatro dedos arrastando é, em várias telas, um gesto do próprio
     * sistema, e roubar isso seria pior do que não ter atalho. Por isso as duas amarras --
     * deslocamento pequeno ([MAX_TRAVEL_FRACTION_TAP]) e duração curta ([MAX_TAP_MS]).
     *
     * O primeiro toque não faz nada; é o segundo, dentro de [DOUBLE_TAP_WINDOW_MS], que abre. Um
     * toque só com quatro dedos acontece sem querer (apoiar a mão na tela); dois seguidos, não.
     */
    private fun avaliarToqueDe4(tracker: GestureTracker, moved: List<Slot>, duracaoMs: Long) {
        if (!isProjectionGestureEnabled()) {
            Log.d(TAG, "Toque de 4 dedos ignorado: o atalho está desligado em Ajustes")
            return
        }
        if (moved.isEmpty()) return
        if (duracaoMs > MAX_TAP_MS) {
            // Este log é a única forma de descobrir, do carro, por que o atalho não abriu: o
            // leitor não consome o toque, então na tela não aparece absolutamente nada.
            Log.d(TAG, "Toque de 4 dedos recusado: durou ${duracaoMs}ms (teto ${MAX_TAP_MS}ms)")
            return
        }

        val maiorDeslocamento = moved.maxOf { slot ->
            val dx = abs(slot.x - slot.startX).toFloat() / tracker.rangeX.coerceAtLeast(1)
            val dy = abs(slot.y - slot.startY).toFloat() / tracker.rangeY.coerceAtLeast(1)
            maxOf(dx, dy)
        }
        if (maiorDeslocamento > MAX_TRAVEL_FRACTION_TAP) {
            Log.d(
                TAG,
                "Toque de 4 dedos recusado: virou arrasto (${"%.3f".format(maiorDeslocamento)} " +
                    "do eixo, teto $MAX_TRAVEL_FRACTION_TAP)"
            )
            return
        }

        val agora = android.os.SystemClock.uptimeMillis()
        val anterior = tracker.ultimoToqueDe4Ms
        if (anterior != 0L && agora - anterior <= DOUBLE_TAP_WINDOW_MS) {
            tracker.ultimoToqueDe4Ms = 0L
            Log.w(TAG, "Toque duplo com 4 dedos: abrindo a seleção de área para o painel")
            runCatching { ScreenProjectionController.abrirSelecao() }
                .onFailure { Log.e(TAG, "Falha ao abrir a seleção de área", it) }
        } else {
            Log.d(TAG, "Primeiro toque de 4 dedos registrado; aguardando o segundo em ${DOUBLE_TAP_WINDOW_MS}ms")
            tracker.ultimoToqueDe4Ms = agora
        }
    }

    /** Sobe/desce os dois lados juntos, como o usuário pediu (motorista e passageiro). */
    private fun adjustTemperature(steps: Int) {
        var mostradoAoMotorista: Float? = null
        listOf(
            Sinais.CAR_HVAC_DRIVER_TEMPERATURE,
            Sinais.CAR_HVAC_PASS_TEMPERATURE
        ).forEach { key ->
            val current = Veiculo.lerDoCarro(key.chave)?.toFloatOrNull() ?: 22.0f
            val next = (current + steps * TEMP_STEP).coerceIn(TEMP_MIN, TEMP_MAX)
            if (next != current) {
                Veiculo.escrever(
                    key.chave,
                    String.format(java.util.Locale.US, "%.1f", next)
                )
            }
            // O mostrador segue o lado do MOTORISTA. Os dois sobem juntos, mas se um já estava no
            // teto e o outro não, mostrar o do motorista é o que corresponde a quem fez o gesto.
            if (key == Sinais.CAR_HVAC_DRIVER_TEMPERATURE) mostradoAoMotorista = next
        }
        val valor = mostradoAoMotorista ?: return
        publicarRetorno(
            RetornoDeGesto(
                tipo = RetornoDeGesto.Tipo.TEMPERATURA,
                valor = valor,
                minimo = TEMP_MIN,
                maximo = TEMP_MAX,
                degraus = steps,
                noLimite = (steps > 0 && valor >= TEMP_MAX) || (steps < 0 && valor <= TEMP_MIN),
                carimbo = android.os.SystemClock.elapsedRealtime()
            )
        )
        Log.w(TAG, "3-finger gesture: temperature ${if (steps > 0) "+" else ""}${steps * TEMP_STEP}")
    }

    private fun adjustFanSpeed(steps: Int) {
        val key = Sinais.CAR_HVAC_FAN_SPEED.chave
        val current = Veiculo.lerDoCarro(key)?.toIntOrNull() ?: 0
        val next = (current + steps).coerceIn(FAN_MIN, FAN_MAX)
        // O RETORNO SAI MESMO QUANDO O VALOR NÃO MUDA
        //
        // Antes daqui havia um `return` quando `next == current`: sem escrita no carro, e sem nada
        // na tela. Mas "já está no máximo" é justamente o momento em que quem fez o gesto MAIS
        // precisa de resposta -- sem ela, a leitura natural é "o carro não entendeu", e o gesto é
        // repetido. O mostrador aparece igual, com a marca de fim de curso.
        if (next != current) {
            Veiculo.escrever(key, next.toString())
        }
        publicarRetorno(
            RetornoDeGesto(
                tipo = RetornoDeGesto.Tipo.VENTILACAO,
                valor = next.toFloat(),
                minimo = FAN_MIN.toFloat(),
                maximo = FAN_MAX.toFloat(),
                degraus = steps,
                noLimite = next == current,
                carimbo = android.os.SystemClock.elapsedRealtime()
            )
        )
        Log.w(TAG, "3-finger gesture: fan $current -> $next")
    }

    /**
     * Entrega a confirmação para a camada de UI.
     *
     * Vai para a thread principal de propósito: isto é chamado da thread que lê o `getevent`, e o
     * estado do Compose é observado por uma janela de overlay. Escrever fora da main funcionaria
     * (snapshot state é seguro entre threads), mas a UI reagiria numa ordem que depende do
     * escalonador -- e um mostrador que confirma um gesto é exatamente o lugar onde a ordem se vê.
     */
    private fun publicarRetorno(feedback: RetornoDeGesto) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            EstadoDaBarra.retornoDeGesto = feedback
        }
    }
}
