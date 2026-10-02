package br.com.opengw

import br.com.opengw.ui.Fontes
import br.com.opengw.ui.TemaOpenGw
import br.com.opengw.ui.Cores
import br.com.opengw.nucleo.OpenGw
import br.com.opengw.veiculo.Veiculo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import kotlin.math.cos
import kotlin.math.sin
import br.com.opengw.managers.WheelTorqueEstimator
import br.com.opengw.barra.EstadoDaBarra
import br.com.opengw.veiculo.Sinais
import br.com.opengw.nucleo.Preferencias
import br.com.opengw.ui.liquidGlass
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Tela cheia que abre sozinha quando o SPORT+ é ativado (ver SportPlusController.activate())
 * e se fecha sozinha quando ele é desativado — mesmo padrão da atividade do painel expandido da barra.
 */
class SportsDisplayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)

        EstadoDaBarra.painelEsportivoAberto = true
        applyFullscreen()

        setContent {
            // Esta tela é escura por desenho: número do mostrador em branco puro, marcações em
            // branco e ImpTokens.TextPrimary em 0xFFF5F5F5. O tema da atividade herda de DayNight,
            // então com a central em modo diurno o esquema claro entraria por baixo de tudo.
            TemaOpenGw() {
                LaunchedEffect(Unit) {
                    snapshotFlow { EstadoDaBarra.painelEsportivoAberto }
                        .distinctUntilChanged()
                        .collect { expanded -> if (!expanded && !isFinishing) finish() }
                }
                SportsDisplayContent(onClose = { EstadoDaBarra.painelEsportivoAberto = false })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyFullscreen()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyFullscreen()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        EstadoDaBarra.painelEsportivoAberto = false
        super.onBackPressed()
    }

    override fun onDestroy() {
        if (isFinishing) EstadoDaBarra.painelEsportivoAberto = false
        super.onDestroy()
    }

    /**
     * Tela cheia imersiva por cima de tudo: sem barras, sem recorte nas bordas, janela acelerada
     * por hardware. As máscaras ficam em constantes porque são as mesmas em qualquer reaplicação.
     */
    private fun applyFullscreen() {
        window.setFlags(FLAG_TELA_CHEIA, FLAG_TELA_CHEIA)
        window.addFlags(FLAGS_DA_JANELA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val atributos = window.attributes
            atributos.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = atributos
        }
        window.decorView.systemUiVisibility = BITS_DA_INTERFACE_IMERSIVA
    }

    companion object {
        private const val FLAG_TELA_CHEIA = WindowManager.LayoutParams.FLAG_FULLSCREEN

        private val FLAGS_DA_JANELA = listOf(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        ).reduce { juntas, flag -> juntas or flag }

        @Suppress("DEPRECATION")
        private const val BITS_DA_INTERFACE_IMERSIVA =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LOW_PROFILE

        fun createIntent(context: Context): Intent =
            Intent(context, SportsDisplayActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}

private enum class PowerUnit(val label: String, val fromKw: (Float) -> Float) {
    KW("kW", { it }),
    CV("cv", { it * 1.35962f }),
    HP("hp", { it * 1.34102f });

    companion object {
        fun fromKey(key: String?): PowerUnit = entries.firstOrNull { it.name == key } ?: KW
    }
}

/**
 * O chão desta tela é dela, não da janela.
 *
 * `Theme.OpenGw.TelaCheia` herda de `Theme.MaterialComponents.DayNight.NoActionBar`, e
 * `values-night/themes.xml` não o sobrescreve. Com a central em modo diurno o `windowBackground` é
 * branco — e o conteúdo desta tela é claro por desenho, então o painel simplesmente sumia. O painel
 * expandido da barra já pintava o próprio fundo; este não pintava.
 */
private val SportsGround = Brush.verticalGradient(
    listOf(Color(0xFF0A0A0C), Color(0xFF150B0A), Color(0xFF0A0A0C))
)

/** Acima disto a leitura não é potência, é código de erro — ver o -1001 de `motor_power`. */
private const val MAX_PLAUSIBLE_POWER_KW = 1000f

/** Piso de rotação para a estimativa de torque (P/ω diverge quando ω → 0). */
private const val MIN_RPM_FOR_TORQUE_ESTIMATE = 200f

/**
 * AS ESCALAS NÃO SÃO MAIS FIXAS
 *
 * O fim de escala estava cravado em 160 kW e 400 Nm. Não existe carro nenhum por trás desses
 * números: um H6 GT híbrido passa dos 160 kW de sistema com folga, e o ponteiro simplesmente
 * encostava no fim e ficava lá — um mostrador que satura é um mostrador que não informa.
 *
 * Em vez de trocar um chute por outro, a escala é escolhida pelo que o CARRO já mostrou ser capaz
 * de entregar: o degrau mais baixo que cobre o pico da sessão. O primeiro degrau é o piso (nunca
 * menor que isso, para o mostrador não ficar nervoso em uso urbano) e a escala sobe sozinha na
 * primeira vez que o carro pede mais. Ela não desce dentro da sessão — o botão "Zerar", que já
 * zerava os picos, também devolve a escala ao piso.
 *
 * As marcações saem do fim de escala escolhido (0, ¼, ½, ¾, fim), então continuam redondas em
 * qualquer degrau.
 */
internal val POWER_SCALE_LADDER_KW = listOf(240f, 320f, 400f, 500f, 600f, 800f)
internal val TORQUE_SCALE_LADDER_NM = listOf(600f, 800f, 1000f, 1400f, 2000f, 3000f)

internal fun pickScaleCeiling(ladder: List<Float>, observedPeak: Float): Float =
    ladder.firstOrNull { it >= observedPeak } ?: ladder.last()

internal fun majorsFor(ceiling: Float): List<Float> =
    listOf(0f, ceiling * 0.25f, ceiling * 0.5f, ceiling * 0.75f, ceiling)

/**
 * A potência que o mostrador deve exibir, em kW, ou `null` quando não há leitura confiável.
 *
 * OS -1001 kW COM O CARRO PARADO
 *
 * `car.ev_info.motor_power` devolve -1001 quando a central não tem leitura para dar. Isso não é
 * dedução: está na captura do próprio veículo (knowledge/session_2026-09-15/raw_http/telemetry.json)
 * com `motor_power = '-1001'` e `motor_speed = '0'` — carro parado. Esta tela mandava esse número
 * direto para o mostrador.
 *
 * A potência elétrica real vem do par tensão × corrente do pack, que é o mesmo cálculo que o painel
 * virtual já faz há tempo (InstrumentProjector2: `batteryVoltage * batteryCurrent / 1000`). Na mesma
 * captura essas duas chaves vieram populadas (306,0 V e 1,0 A) enquanto `motor_power` estava
 * inválida — por isso o par é a fonte primária, e `motor_power` só entra quando cai dentro de uma
 * faixa plausível.
 *
 * O sinal é preservado: em regeneração a corrente inverte e a potência fica negativa. O mostrador
 * prende o ponteiro em zero (`coerceIn` no GaugeRing), mas o número lido continua honesto.
 */
internal fun resolvePowerKw(packVolts: Float?, packAmps: Float?, reportedPowerKw: Float?): Float? {
    if (packVolts != null && packAmps != null && packVolts.isFinite() && packAmps.isFinite() && packVolts > 0f) {
        return packVolts * packAmps / 1000f
    }
    if (reportedPowerKw == null || !reportedPowerKw.isFinite()) return null
    if (reportedPowerKw <= -MAX_PLAUSIBLE_POWER_KW || reportedPowerKw >= MAX_PLAUSIBLE_POWER_KW) return null
    return reportedPowerKw
}

/**
 * Torque estimado (Nm) = potência (W) / velocidade angular (rad/s), ou `null` sem base para estimar.
 *
 * `motor_speed` é tratado como RPM — não documentado, melhor esforço — e o resultado nunca é
 * apresentado como medição.
 *
 * O piso de rotação não é decoração: P/ω diverge quando ω → 0, e a 5 rpm uma potência banal vira
 * dezenas de milhares de Nm. Abaixo do piso a estimativa não é publicada; um traço é mais honesto
 * que um número inventado.
 *
 * ESTA FUNÇÃO NÃO ALIMENTA MAIS O MOSTRADOR — ver [WheelTorqueEstimator]
 *
 * Ela continua aqui porque a conta está certa e é o caminho bom NUM CARRO QUE RESPONDA `motor_speed`.
 * Este não responde: a captura do próprio veículo traz `motor_power = -1001` (sentinela de "sem
 * leitura"), `motor_speed = 0` e `rear_motor_speed` vazia, então o piso de rotação cortava sempre e
 * o mostrador de torque exibia um traço em qualquer situação — parado ou em arrancada. O que a tela
 * usa agora é o torque nas RODAS, deduzido do movimento do carro, que não depende desse bloco.
 */
internal fun estimateTorqueNm(powerKw: Float?, motorSpeedRpm: Float?): Float? {
    if (powerKw == null || powerKw <= 0f) return null
    if (motorSpeedRpm == null || motorSpeedRpm < MIN_RPM_FOR_TORQUE_ESTIMATE) return null
    return (powerKw * 1000f) / (motorSpeedRpm * (2f * Math.PI.toFloat() / 60f))
}

/**
 * Percentual de bateria, ou `null` sem leitura boa.
 *
 * `car.ev_info.soc_of_battery` devolve **-1.0** quando a central não tem o valor — está na captura
 * do carro, ao lado de `cur_battery_power_percentage = 38`, que é a leitura boa do mesmo instante.
 * A tela usava `?:` entre as duas, e `?:` só cai para a segunda quando a primeira é NULA: "-1.0"
 * é um Float perfeitamente válido, então a alternativa nunca era consultada e o painel escrevia
 * **-1%** com a bateria em 38%.
 *
 * A regra aqui é por FAIXA, não por nulidade: um percentual fora de 0..100 não é percentual.
 */
internal fun resolveBatteryPercent(socOfBattery: Float?, curBatteryPercentage: Float?): Float? {
    fun plausivel(v: Float?): Float? =
        v?.takeIf { it.isFinite() && it >= 0f && it <= 100f }
    return plausivel(socOfBattery) ?: plausivel(curBatteryPercentage)
}

@Composable
private fun SportsDisplayContent(onClose: () -> Unit) {
    val prefs = remember {
        OpenGw.contextoProtegido.getSharedPreferences("opengw_prefs", Context.MODE_PRIVATE)
    }
    var unit by remember {
        mutableStateOf(PowerUnit.fromKey(prefs.getString(Preferencias.SPORTS_DISPLAY_POWER_UNIT.chave, null)))
    }

    val dataMap = remember {
        mutableStateMapOf<String, String>().apply {
            Veiculo.valoresConhecidos().forEach { (k, v) -> put(k, v ?: "") }
        }
    }
    DisposableEffect(Unit) {
        val listener = Veiculo.Ouvinte { key, value -> dataMap[key] = value ?: "" }
        Veiculo.observar(listener)
        onDispose { Veiculo.pararDeObservar(listener) }
    }

    val rpm = dataMap[Sinais.CAR_BASIC_ENGINE_SPEED.chave]?.toFloatOrNull()
    val batteryPct = resolveBatteryPercent(
        socOfBattery = dataMap[Sinais.CAR_EV_INFO_SOC_OF_BATTERY.chave]?.toFloatOrNull(),
        curBatteryPercentage = dataMap[Sinais.CAR_EV_INFO_CUR_BATTERY_POWER_PERCENTAGE.chave]?.toFloatOrNull(),
    )
    val speedKmh = dataMap[Sinais.CAR_BASIC_VEHICLE_SPEED.chave]?.toFloatOrNull()

    val powerKw = resolvePowerKw(
        packVolts = dataMap[Sinais.CAR_EV_INFO_POWER_BATTERY_VOLTAGE.chave]?.toFloatOrNull(),
        packAmps = dataMap[Sinais.CAR_EV_INFO_POWER_BATTERY_CURRENT.chave]?.toFloatOrNull(),
        reportedPowerKw = dataMap[Sinais.CAR_EV_INFO_MOTOR_POWER.chave]?.toFloatOrNull(),
    )

    // O torque vem do MOVIMENTO do carro, não do bloco do motor elétrico — ver
    // [WheelTorqueEstimator] para o porquê. Cada leitura nova de velocidade entra no estimador;
    // `elapsedRealtime` porque a conta é uma derivada no tempo.
    val torqueEstimator = remember { WheelTorqueEstimator() }
    var torqueEstimateNm by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(speedKmh) {
        torqueEstimator.sample(speedKmh, android.os.SystemClock.elapsedRealtime())
        // Só o esforço de tração é "torque sendo gerado". Freando, a conta fica negativa — é
        // informação verdadeira, mas não é o que este mostrador mede.
        torqueEstimateNm = torqueEstimator.torqueNm()?.coerceAtLeast(0f)
    }

    // Picos da sessão (desde que essa tela foi aberta) -- zera junto com o cronômetro de 0-100
    // no botão "Zerar", já que ambos só fazem sentido como "melhor tentativa desta sessão".
    var peakRpm by remember { mutableStateOf(0f) }
    var peakPowerKw by remember { mutableStateOf(0f) }
    var peakTorqueNm by remember { mutableStateOf(0f) }
    LaunchedEffect(rpm) { if ((rpm ?: 0f) > peakRpm) peakRpm = rpm!! }
    LaunchedEffect(powerKw) { if ((powerKw ?: 0f) > peakPowerKw) peakPowerKw = powerKw!! }
    LaunchedEffect(torqueEstimateNm) {
        if ((torqueEstimateNm ?: 0f) > peakTorqueNm) peakTorqueNm = torqueEstimateNm!!
    }

    // Cronômetro 0-100 km/h: dispara ao sair do repouso (<1 km/h) sem corrida em andamento,
    // conclui ao cruzar 100 km/h, e cancela (sem gravar) se a velocidade cair de volta perto de
    // zero antes de chegar lá -- assim uma freada no meio do caminho não registra um tempo falso.
    var zeroToHundredMs by remember { mutableStateOf<Long?>(null) }
    var runStartElapsedMs by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(speedKmh) {
        val speed = speedKmh ?: return@LaunchedEffect
        val now = android.os.SystemClock.elapsedRealtime()
        val running = runStartElapsedMs
        when {
            running == null && speed < 1f -> Unit
            running == null && speed >= 1f -> runStartElapsedMs = now
            running != null && speed >= 100f -> {
                zeroToHundredMs = now - running
                runStartElapsedMs = null
            }
            running != null && speed < 1f -> runStartElapsedMs = null
        }
    }
    val zeroToHundredLabel = when {
        runStartElapsedMs != null -> "Cronometrando..."
        zeroToHundredMs != null -> "%.2fs".format(zeroToHundredMs!! / 1000f)
        else -> "—"
    }

    /**
     * A BARRA INFERIOR COMIA O RODAPÉ DESTA TELA
     *
     * A janela sobe com FLAG_LAYOUT_NO_LIMITS (de propósito: o fundo precisa ir até a borda), então
     * ela ocupa a tela toda — inclusive a faixa onde o OpenGW desenha a própria barra inferior, que
     * é uma janela de overlay por cima de tudo. Com 32dp iguais nos quatro lados, a linha de baixo
     * (RPM / Bateria / 0-100) ficava atrás da barra.
     *
     * O desconto vem de `EstadoDaBarra`, que é a MESMA fonte que a barra usa para se desenhar
     * (`EstadoDaBarra.alturaDp`) e é estado do Compose: se o usuário mudar a
     * altura da barra em Ajustes Básicos, ou a barra se esconder, esta tela acompanha no mesmo
     * quadro. Não é um número copiado que envelhece.
     */
    val bottomInset = if (EstadoDaBarra.visivel) EstadoDaBarra.alturaDp.dp else 0.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SportsGround)
            .clickable(enabled = false) {},
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 32.dp, end = 32.dp, top = 32.dp, bottom = 32.dp + bottomInset),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("SPORT+", fontFamily = Fontes.titulo, fontSize = 22.sp, letterSpacing = 2.sp, color = Cores.destaque)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .liquidGlass(shape = CircleShape)
                            .clickable {
                                peakRpm = 0f
                                peakPowerKw = 0f
                                peakTorqueNm = 0f
                                zeroToHundredMs = null
                                runStartElapsedMs = null
                                torqueEstimator.reset()
                                torqueEstimateNm = null
                            }
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text("Zerar", fontFamily = Fontes.texto, fontSize = 14.sp, color = Cores.textoSecundario)
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .liquidGlass(shape = CircleShape)
                            .clickable { onClose() }
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text("Fechar", fontFamily = Fontes.texto, fontSize = 14.sp, color = Cores.textoPrincipal)
                    }
                }
            }

            // O degrau é escolhido pelo pico da sessão E pelo valor instantâneo: numa arrancada a
            // escala precisa já estar aberta no quadro em que o carro passa do fim antigo, não no
            // quadro seguinte.
            val powerMaxKw = pickScaleCeiling(
                POWER_SCALE_LADDER_KW,
                maxOf(peakPowerKw, powerKw ?: 0f)
            )
            // Os valores ficam crus, sem arredondar. Arredondar aqui deslocava as marcações: em
            // cv o topo da escala vale 217,54 e o rótulo virava 218, então a última marcação era
            // desenhada 0,2% depois do fim do arco.
            val powerMajors = majorsFor(powerMaxKw).map { unit.fromKw(it) }
            val powerValueDisplay = powerKw?.let { unit.fromKw(it) } ?: 0f
            val torqueMaxNm = pickScaleCeiling(
                TORQUE_SCALE_LADDER_NM,
                maxOf(peakTorqueNm, torqueEstimateNm ?: 0f)
            )
            val torqueMajors = majorsFor(torqueMaxNm)
            val torqueValueDisplay = torqueEstimateNm ?: 0f

            Row(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = true),
                horizontalArrangement = Arrangement.spacedBy(40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GaugeRing(
                    modifier = Modifier.weight(1f).fillMaxHeight().clickable {
                        val next = PowerUnit.entries[(unit.ordinal + 1) % PowerUnit.entries.size]
                        unit = next
                        prefs.edit { putString(Preferencias.SPORTS_DISPLAY_POWER_UNIT.chave, next.name) }
                    },
                    value = powerValueDisplay,
                    max = unit.fromKw(powerMaxKw),
                    majors = powerMajors,
                    valueText = powerKw?.let { "%.0f".format(powerValueDisplay) } ?: "—",
                    unitLabel = unit.label,
                    caption = "Potência" + if (peakPowerKw > 0f) "  ·  MAX %.0f".format(unit.fromKw(peakPowerKw)) else ""
                )
                GaugeRing(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    value = torqueValueDisplay,
                    max = torqueMajors.last(),
                    majors = torqueMajors,
                    valueText = torqueEstimateNm?.let { "%.0f".format(it) } ?: "—",
                    unitLabel = "Nm",
                    caption = "Torque nas rodas (est.)" +
                        if (peakTorqueNm > 0f) "  ·  MAX %.0f".format(peakTorqueNm) else ""
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "RPM",
                    value = rpm?.let { "%.0f".format(it) } ?: "—",
                    valueFontSize = 28.sp,
                    subValue = if (peakRpm > 0f) "MAX %.0f".format(peakRpm) else null
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "Bateria",
                    value = batteryPct?.let { "%.0f%%".format(it) } ?: "—",
                    valueFontSize = 28.sp
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "0-100 km/h",
                    value = zeroToHundredLabel,
                    valueFontSize = 28.sp
                )
            }
        }
    }
}

@Composable
private fun GaugeRing(
    modifier: Modifier = Modifier,
    value: Float,
    max: Float,
    majors: List<Float>,
    valueText: String,
    unitLabel: String,
    caption: String,
) {
    val trackColor = Color(0xFF3A0C0A)
    val fillColor = Color(0xFFFF3B2E)
    val startAngle = 135f
    val sweep = 270f
    val fraction = if (max > 0f) (value / max).coerceIn(0f, 1f) else 0f
    val density = LocalDensity.current

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = true),
            contentAlignment = Alignment.Center
        ) {
            // O mostrador desenha três coisas FORA do raio do anel: o brilho (1,9x a espessura do
            // traço, logo 0,95x para cada lado), o dente da marcação e o rótulo dela. O raio
            // precisa descontar essas folgas. Antes não descontava: o rótulo do meio da escala cai
            // exatamente em 270° (topo) e era pintado 7% do diâmetro acima da área do componente —
            // e o Compose não recorta sozinho, então ele invadia a linha de cima.
            //
            // O desconto não é igual em cima e embaixo. O arco vai de 135° a 405°, então ele
            // encosta no topo, mas para 45° antes do fundo: a demanda para baixo é sin(45°) =
            // 0,7071 da de cima. Centrar o círculo no meio da caixa desperdiçaria essa diferença e
            // encolheria o anel à toa, que é o defeito oposto. O centro é calculado, não assumido.
            val escala = minOf(maxWidth, maxHeight)
            val traco = escala * 0.085f
            val rotulo = escala * 0.05f
            val dente = traco * 0.7f
            val brilho = traco * 0.95f
            val folgaTopo = maxOf(brilho, dente + rotulo * 1.5f)
            val folgaExterna = maxOf(brilho, dente + rotulo * 0.9f)
            val porAltura = (maxHeight - folgaTopo - folgaExterna * 0.7071f - rotulo * 0.6f) / 1.7071f
            // cos(22,5°) = 0,9239 é a marcação mais lateral de uma escala de cinco pontos.
            val porLargura = (maxWidth / 2f) / 0.9239f - dente - rotulo * 1.8f
            val raio = minOf(porAltura, porLargura).coerceAtLeast(1.dp)
            val centroY = folgaTopo + raio

            // O número central acompanha o anel. Com 48sp fixos ele estourava o miolo assim que o
            // anel ficava menor (tela mais baixa) ou o valor tinha três dígitos em cv/hp. `toSp()`
            // parte de dp, então a fonte segue a densidade da tela e não a escala de fonte do
            // Android: um mostrador não pode mudar de tamanho porque alguém aumentou a fonte do
            // sistema.
            val tamanhoValor = with(density) { (raio * 0.36f).toSp() }
            val tamanhoUnidade = with(density) { (raio * 0.115f).toSp() }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = traco.toPx()
                val labelSize = rotulo.toPx()
                val radius = raio.toPx()
                val center = Offset(size.width / 2f, centroY.toPx())
                val topLeft = Offset(center.x - radius, center.y - radius)
                val arcSize = Size(radius * 2f, radius * 2f)

                drawArc(
                    color = fillColor.copy(alpha = 0.28f),
                    startAngle = startAngle,
                    sweepAngle = sweep * fraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth * 1.9f, cap = StrokeCap.Butt)
                )
                drawArc(
                    color = trackColor,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )
                drawArc(
                    color = fillColor,
                    startAngle = startAngle,
                    sweepAngle = sweep * fraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )

                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    alpha = 210
                    textSize = labelSize
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }

                majors.forEach { major ->
                    val majorFraction = if (max > 0f) (major / max).coerceIn(0f, 1f) else 0f
                    val angleRad = Math.toRadians((startAngle + sweep * majorFraction).toDouble())
                    val dx = cos(angleRad).toFloat()
                    val dy = sin(angleRad).toFloat()
                    val tickInner = radius - strokeWidth * 0.7f
                    val tickOuter = radius + strokeWidth * 0.7f
                    val labelRadius = tickOuter + labelSize * 0.9f

                    drawLine(
                        color = Color.White.copy(alpha = 0.85f),
                        start = Offset(center.x + dx * tickInner, center.y + dy * tickInner),
                        end = Offset(center.x + dx * tickOuter, center.y + dy * tickOuter),
                        strokeWidth = 3.dp.toPx()
                    )

                    drawContext.canvas.nativeCanvas.drawText(
                        "%.0f".format(major),
                        center.x + dx * labelRadius,
                        center.y + dy * labelRadius + labelSize * 0.35f,
                        paint
                    )
                }

                val tipAngleRad = Math.toRadians((startAngle + sweep * fraction).toDouble())
                val tipX = center.x + cos(tipAngleRad).toFloat() * radius
                val tipY = center.y + sin(tipAngleRad).toFloat() * radius
                drawCircle(color = Color(0xFFFFA000), radius = strokeWidth * 0.34f, center = Offset(tipX, tipY))
                drawCircle(color = Color.White, radius = strokeWidth * 0.15f, center = Offset(tipX, tipY))
            }

            // O texto segue o centro do círculo, não o centro da caixa — os dois deixaram de ser o
            // mesmo ponto quando o raio passou a ser calculado.
            Column(
                modifier = Modifier.offset(y = centroY - maxHeight / 2f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    valueText,
                    fontFamily = Fontes.titulo,
                    fontSize = tamanhoValor,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    unitLabel,
                    fontFamily = Fontes.texto,
                    fontSize = tamanhoUnidade,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            caption,
            fontFamily = Fontes.texto,
            fontSize = 14.sp,
            color = Cores.textoSecundario,
            maxLines = 1
        )
    }
}

@Composable
private fun MetricTile(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    valueFontSize: androidx.compose.ui.unit.TextUnit,
    subValue: String? = null,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .liquidGlass()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(20.dp)
    ) {
        Text(label, fontFamily = Fontes.texto, fontSize = 14.sp, color = Cores.textoSecundario)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            value,
            fontFamily = Fontes.titulo,
            fontSize = valueFontSize,
            fontWeight = FontWeight.Bold,
            color = Cores.textoPrincipal
        )
        if (subValue != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(subValue, fontFamily = Fontes.texto, fontSize = 12.sp, color = Cores.textoSecundario)
        }
    }
}
