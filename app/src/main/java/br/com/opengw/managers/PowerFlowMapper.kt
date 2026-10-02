package br.com.opengw.managers

import br.com.opengw.veiculo.Veiculo
import android.os.Handler
import android.os.Looper
import android.util.Log
import br.com.opengw.veiculo.Sinais
import br.com.opengw.resolvePowerKw
import kotlin.math.abs

/**
 * Publica `haval.power.flow` — o retrato de PARA ONDE a potência está indo neste instante.
 *
 * ## Por que isto existe
 *
 * O raio-X do conjunto (o ícone de capô no launcher 3D) e o cartão POWER da tela inicial leem uma
 * chave só: `haval.power.flow`. Está escrito no próprio launcher, em `CAR_SIGNALS`:
 *
 *     // Derived by the power-flow mapper. Packed v1|state|ice|front|rear.
 *     powerFlow: "haval.power.flow"
 *
 * Esse mapeador de fluxo nunca existiu neste repositório. O launcher foi escrito contra um
 * produtor que ninguém chegou a escrever, e o efeito no carro é exatamente o relatado: o raio-X
 * abre, o modelo aparece, e nada dentro dele se mexe — sem setas de fluxo, sem saber se o motor a
 * combustão está girando, sem tração marcada em roda nenhuma. Não é o desenho que está quebrado, é
 * que ninguém nunca respondeu a pergunta que ele faz.
 *
 * Sem a chave o launcher cai em `CAR_POWER_IDLE` (`_powerFlowRaw`), que é o retrato de "carro
 * parado, nada acontecendo" — e é isso que ele desenha, para sempre.
 *
 * ## O formato, lido do consumidor
 *
 *     v1|state|ice|front|rear
 *
 *  - `state`: `idle` | `ev` | `hybrid` | `ice` | `regen` | `charge`
 *  - `ice`:   `1` se o motor a combustão está GIRANDO agora, `0` se não
 *  - `front`, `rear`: inteiros com sinal. O launcher só usa o SINAL
 *    (`Math.sign` em `_powerUnpackFlow`): positivo = tracionando, negativo = regenerando,
 *    zero = desligado. O valor absoluto não é lido — vai `1`/`-1` para não sugerir precisão que
 *    não existe.
 *
 * Qualquer outra coisa é recusada pelo launcher (`_powerUnpackFlow` devolve `null`) e o cartão
 * volta para "Fluxo indisponível". Não há tolerância a formato aqui: ou sai assim, ou não serve.
 *
 * ## De onde sai cada campo
 *
 * `ice` NÃO vem de `car.basic.engine_state`. Essa chave é o estado de ignição do SISTEMA e fica em
 * "13" o tempo inteiro dirigindo, mesmo com o motor a combustão desligando sozinho — está escrito
 * em THEME_AUTHORING_GUIDE.md §7.2, com a receita que esta classe segue: giro real
 * (`car.basic.engine_speed`) com histerese, para o indicador não piscar em volta do corte.
 *
 * O sentido da potência vem do pacote de tração (tensão × corrente), que é a MESMA conta que o
 * Painel Esportivo e o ASD já usam ([resolvePowerKw]) — de propósito: três lugares do produto
 * dizendo coisas diferentes sobre o que o carro está fazendo seria pior do que não dizer nada.
 * Positivo = pacote entregando (tração), negativo = pacote recebendo (regeneração/carga), que é a
 * convenção que o resto do app já assume (cartão Dinâmica do painel da barra: `kw < -0.5 → REGEN`).
 *
 * ## O que esta classe NÃO sabe
 *
 * Não existe leitura de torque nem de potência POR EIXO nesta central — `motor_power` devolve o
 * sentinela -1001 e `rear_motor_speed` vem vazia (captura do carro em
 * knowledge/session_2026-09-15). Então `front`/`rear` são uma ATRIBUIÇÃO, não uma medida: a tração
 * elétrica é creditada ao eixo dianteiro, e o traseiro só acende se `rear_motor_speed` um dia
 * responder num carro que o tenha. É melhor do que a alternativa (não desenhar nada), e está
 * marcado aqui para ninguém ler o desenho como telemetria de eixo.
 *
 * Motor a combustão carregando o pacote com o carro PARADO sai como `idle` + `ice=1` ("Marcha
 * lenta" no launcher), não como `regen`: regeneração com as rodas paradas seria mentira, e `charge`
 * é reservado para o plugue, que é o único caso sem ambiguidade.
 */
object PowerFlowMapper {

    private const val TAG = "PowerFlowMapper"

    /** A chave derivada que o launcher 3D consome. */
    const val KEY_FLOW = "haval.power.flow"

    /** Espelho booleano simples do mesmo fato — consumido pelo gravador de trajetos do launcher. */
    const val KEY_ICE = "haval.power.ice"

    /**
     * Histerese do motor a combustão, em rpm. Os dois números vêm de THEME_AUTHORING_GUIDE.md §7.2,
     * onde já estavam escritos como a receita correta para esta central.
     */
    private const val ICE_ON_RPM = 400f
    private const val ICE_OFF_RPM = 150f

    /** Acima disto o pacote está TRACIONANDO, não só alimentando o carro parado. */
    private const val DRIVE_KW = 1.0f

    /** Abaixo disto (negativo) o pacote está RECEBENDO energia. */
    private const val REGEN_KW = -0.5f

    /** Acima disto o carro está em movimento para efeito de regeneração. */
    private const val MOVING_KMH = 1.0f

    /**
     * Espera antes de recalcular, em ms.
     *
     * `dispatchAllData()` despeja ~800 chaves num laço — sem juntar os pedidos, um retrato de
     * telemetria publicaria a chave derivada dezenas de vezes no mesmo milissegundo. A espera junta
     * a rajada num cálculo só, e no uso normal (uma chave mudando de cada vez) é imperceptível.
     */
    private const val COALESCE_MS = 120L

    /**
     * Preguiçoso e tolerante à ausência de Looper DE PROPÓSITO.
     *
     * Construído na inicialização do objeto, ele amarrava a classe inteira ao Android: num teste de
     * JVM pura `Looper.getMainLooper()` devolve nulo, o construtor de `Handler` estoura, e o que
     * falha não é o agendamento — é `ExceptionInInitializerError` em QUALQUER acesso à classe,
     * inclusive à máquina de estados, que é justamente a parte que não tem nada de Android e é a
     * que precisa de teste.
     *
     * Sem Looper o agendamento simplesmente não acontece e `packedNow()` continua respondendo. É o
     * comportamento certo dos dois lados: no carro há Looper e a coalescência funciona; fora dele a
     * conta é pura e direta.
     */
    private val handler: Handler? by lazy {
        runCatching { Looper.getMainLooper()?.let { Handler(it) } }.getOrNull()
    }

    /** Últimos valores crus vistos passar. Só destas chaves — o resto do barramento é ignorado. */
    @Volatile private var engineRpm: Float? = null
    @Volatile private var packVolts: Float? = null
    @Volatile private var packAmps: Float? = null
    @Volatile private var reportedKw: Float? = null
    @Volatile private var speedKmh: Float? = null
    @Volatile private var rearMotorRpm: Float? = null
    @Volatile private var gunConnected: Boolean = false
    @Volatile private var charging: Boolean = false

    /** Estado com histerese do motor a combustão. */
    @Volatile private var iceOn: Boolean = false

    /** Último pacote publicado, para não repetir a mesma coisa no barramento. */
    @Volatile private var lastPublished: String? = null

    private val recompute = Runnable { publish(force = false) }

    private val WATCHED: Set<String> = setOf(
        Sinais.CAR_BASIC_ENGINE_SPEED.chave,
        Sinais.CAR_EV_INFO_POWER_BATTERY_VOLTAGE.chave,
        Sinais.CAR_EV_INFO_POWER_BATTERY_CURRENT.chave,
        Sinais.CAR_EV_INFO_MOTOR_POWER.chave,
        Sinais.CAR_BASIC_VEHICLE_SPEED.chave,
        Sinais.CAR_EV_INFO_REAR_MOTOR_SPEED.chave,
        Sinais.CAR_EV_INFO_CHARGING_GUN_CONN_STATE.chave,
        Sinais.CAR_EV_INFO_CHARGING_GUN_AC_CONN_STATE.chave,
        Sinais.CAR_EV_INFO_CHARGING_STATE.chave
    )

    /** A chave interessa a este mapeador? Barato de chamar por chave do barramento. */
    @JvmStatic
    fun watches(key: String?): Boolean = key != null && WATCHED.contains(key)

    /**
     * Recebe uma chave que acabou de ser publicada. Chamado de dentro do despacho de telemetria,
     * então não faz nada caro aqui: guarda o valor e agenda o cálculo.
     */
    @JvmStatic
    fun onSignal(key: String?, value: String?) {
        if (key == null || !WATCHED.contains(key)) return
        val num = value?.trim()?.toFloatOrNull()
        when (key) {
            Sinais.CAR_BASIC_ENGINE_SPEED.chave -> engineRpm = num
            Sinais.CAR_EV_INFO_POWER_BATTERY_VOLTAGE.chave -> packVolts = num
            Sinais.CAR_EV_INFO_POWER_BATTERY_CURRENT.chave -> packAmps = num
            Sinais.CAR_EV_INFO_MOTOR_POWER.chave -> reportedKw = num
            Sinais.CAR_BASIC_VEHICLE_SPEED.chave -> speedKmh = num
            Sinais.CAR_EV_INFO_REAR_MOTOR_SPEED.chave -> rearMotorRpm = num
            Sinais.CAR_EV_INFO_CHARGING_GUN_CONN_STATE.chave,
            Sinais.CAR_EV_INFO_CHARGING_GUN_AC_CONN_STATE.chave -> gunConnected = isOn(value)
            Sinais.CAR_EV_INFO_CHARGING_STATE.chave -> charging = isOn(value)
        }
        handler?.let {
            it.removeCallbacks(recompute)
            it.postDelayed(recompute, COALESCE_MS)
        }
    }

    /**
     * Republica mesmo sem mudança. É o que o retrato pedido pelo launcher precisa: `dispatchAllData`
     * reemite as chaves CRUAS do carro, e uma chave derivada não está lá — sem isto o launcher
     * abriria com o raio-X vazio até algum sinal se mexer, que num carro parado é quase nunca.
     */
    @JvmStatic
    fun republish() {
        val h = handler
        if (h == null) {
            publish(force = true)
            return
        }
        h.removeCallbacks(recompute)
        h.post { publish(force = true) }
    }

    /**
     * "Ligado" tolerante a formato. THEME_AUTHORING_GUIDE.md §7.5: não há garantia de que um campo
     * booleano do carro chegue como "1"/"0".
     */
    private fun isOn(raw: String?): Boolean {
        val v = raw?.trim()?.lowercase() ?: return false
        if (v.isEmpty()) return false
        v.toFloatOrNull()?.let { return it != 0f }
        return v == "true" || v == "on" || v == "yes"
    }

    /** Estado atual do conjunto, já no formato que o launcher consome. */
    internal fun packedNow(): String {
        val rpm = engineRpm
        if (rpm != null && rpm.isFinite()) {
            if (!iceOn && rpm >= ICE_ON_RPM) iceOn = true
            else if (iceOn && rpm <= ICE_OFF_RPM) iceOn = false
        }

        val kw = resolvePowerKw(packVolts, packAmps, reportedKw)
        val moving = (speedKmh ?: 0f) > MOVING_KMH
        val rearSpinning = (rearMotorRpm ?: 0f).let { it.isFinite() && abs(it) > 1f }

        val plugged = gunConnected && charging
        val driving = kw != null && kw >= DRIVE_KW
        val recovering = kw != null && kw <= REGEN_KW

        val state: String
        val front: Int
        when {
            plugged -> { state = "charge"; front = 0 }
            recovering && moving -> { state = "regen"; front = -1 }
            driving && iceOn -> { state = "hybrid"; front = 1 }
            driving -> { state = "ev"; front = 1 }
            iceOn && moving -> { state = "ice"; front = 1 }
            else -> { state = "idle"; front = 0 }
        }
        // O eixo traseiro só acende com evidência própria: nesta central `rear_motor_speed` vem
        // vazia, então na prática ele fica em 0 — e é isso mesmo, o carro é de tração dianteira.
        val rear = if (rearSpinning && front != 0) front else 0

        return "v1|$state|${if (iceOn) 1 else 0}|$front|$rear"
    }

    private fun publish(force: Boolean) {
        val packed = runCatching { packedNow() }
            .onFailure { Log.e(TAG, "Falha ao montar o fluxo de potência", it) }
            .getOrNull() ?: return

        if (!force && packed == lastPublished) return
        lastPublished = packed

        runCatching {
            Veiculo.publicar(KEY_FLOW, packed)
            Veiculo.publicar(KEY_ICE, if (iceOn) "1" else "0")
        }.onFailure { Log.e(TAG, "Falha ao publicar $KEY_FLOW", it) }

        Log.i(TAG, "$KEY_FLOW = $packed${if (force) " (retrato)" else ""}")
    }
}
