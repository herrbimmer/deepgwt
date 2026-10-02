package br.com.opengw.hud

import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Um dado que uma página de números do HUD pode mostrar.
 *
 * AS TRÊS PÁGINAS DE NÚMEROS VIRARAM DADOS
 *
 * Até a 5.8, Consumo, Powertrain e Veículo eram três funções de desenho com os valores escritos à
 * mão. A grade delas já era uma só (linha A grande, linha B menor — ver [HudOverlayView]); o que
 * mudava era a lista de valores. Agora a lista é um [HudLayout], e o tema do HUD feito no
 * instalador pode trocar o que cada página mostra. Sem tema, [HudLayout.padrao] devolve exatamente
 * as listas antigas — e o desenho sai pixel a pixel igual ao da 5.8.
 *
 * [nome] é o que o editor do instalador mostra no seletor; o rótulo que vai para o HUD é o da
 * [Leitura], mais curto, e é o mesmo texto que as páginas já usavam.
 *
 * [opcional]: o campo some da linha (e a coluna com ele) quando não há o que dizer — bateria num
 * carro sem leitura dela, tração quando o eixo traseiro não está empurrando. Os outros ficam no
 * lugar com `--`, porque um buraco que aparece e some confunde mais do que um traço.
 */
enum class HudCampo(val nome: String, val opcional: Boolean = false) {
    CONSUMO("Consumo instantâneo"),
    AUTONOMIA("Autonomia"),
    TANQUE("Tanque"),
    BATERIA("Bateria", opcional = true),
    POTENCIA("Potência"),
    TORQUE("Torque estimado"),
    ROTACAO("Rotação do motor"),
    MODO("Modo de condução"),
    TRACAO("Tração AWD", opcional = true),
    ODOMETRO("Odômetro"),
    MARCHA("Marcha"),
    TEMP_FORA("Temperatura externa"),
    TEMP_DENTRO("Temperatura interna"),
    PNEUS("Pneus"),
    VELOCIDADE("Velocidade"),
    LIMITE("Limite da via"),
    HORA("Hora");

    /**
     * O que o HUD escreve para um campo: rótulo em caixa-alta em cima, valor e unidade embaixo.
     *
     * @param alerta o valor sai no vermelho de alerta (pneu a verificar).
     * @param barra fração 0..1 de uma barra fina embaixo do valor (níveis), ou null.
     */
    data class Leitura(
            val rotulo: String,
            val valor: String,
            val unidade: String = "",
            val alerta: Boolean = false,
            val barra: Float? = null
    )

    companion object {
        fun porNome(nome: String): HudCampo? = entries.firstOrNull { it.name == nome.trim() }
    }
}

/**
 * O que uma página de números mostra: a linha A (um ou dois valores grandes) e a linha B (até três
 * menores). Imutável e sem Android — viaja no tema, é gravado na preferência e é testado na JVM.
 */
data class HudLayout(val linhaA: List<HudCampo>, val linhaB: List<HudCampo>) {

    /** Texto da preferência: `A1+A2/B1+B2+B3`. */
    fun paraTexto(): String =
            linhaA.joinToString("+") { it.name } + "/" + linhaB.joinToString("+") { it.name }

    companion object {
        const val MAX_A = 2
        const val MAX_B = 3

        /**
         * O que cada página mostrava na 5.8. `null` para as páginas que não são de números
         * (velocidade e mídia têm desenho próprio e não aceitam campos).
         */
        fun padrao(pagina: HudPaginas.Pagina): HudLayout? =
                when (pagina) {
                    HudPaginas.Pagina.CONSUMO ->
                            HudLayout(
                                    listOf(HudCampo.CONSUMO),
                                    listOf(HudCampo.AUTONOMIA, HudCampo.TANQUE, HudCampo.BATERIA)
                            )
                    HudPaginas.Pagina.POWERTRAIN ->
                            HudLayout(
                                    listOf(HudCampo.POTENCIA, HudCampo.TORQUE),
                                    listOf(HudCampo.ROTACAO, HudCampo.MODO, HudCampo.TRACAO)
                            )
                    HudPaginas.Pagina.VEICULO ->
                            HudLayout(
                                    listOf(HudCampo.ODOMETRO, HudCampo.MARCHA),
                                    listOf(HudCampo.TEMP_FORA, HudCampo.TEMP_DENTRO, HudCampo.PNEUS)
                            )
                    else -> null
                }

        fun aceitaCampos(pagina: HudPaginas.Pagina): Boolean = padrao(pagina) != null

        /**
         * Corta nos limites da grade e recusa a página sem valor grande: uma linha A vazia deixaria
         * o alto do HUD em branco com três números pequenos no pé. Nesse caso vale o padrão.
         */
        fun normalizar(pagina: HudPaginas.Pagina, a: List<HudCampo>, b: List<HudCampo>): HudLayout? {
            val padrao = padrao(pagina) ?: return null
            val linhaA = a.take(MAX_A)
            if (linhaA.isEmpty()) return padrao
            return HudLayout(linhaA, b.take(MAX_B))
        }

        /** Lê `A1+A2/B1+B2+B3`; nome desconhecido some, texto ilegível devolve null. */
        fun deTexto(pagina: HudPaginas.Pagina, texto: String): HudLayout? {
            val partes = texto.split('/')
            if (partes.isEmpty() || partes.size > 2) return null
            val ler = { s: String -> s.split('+').filter { it.isNotBlank() }.mapNotNull { HudCampo.porNome(it) } }
            return normalizar(pagina, ler(partes[0]), if (partes.size > 1) ler(partes[1]) else emptyList())
        }
    }
}

/**
 * A leitura de [campo] agora, do espelho de [HudTelemetry] — nunca do carro, nunca pelo binder.
 * `null` só para campo [HudCampo.opcional] sem dado, que some da linha.
 *
 * Os textos são os que as páginas da 5.8 escreviam, um a um. Não é gosto: é o que mantém o desenho
 * sem tema idêntico ao anterior.
 */
internal fun lerCampo(campo: HudCampo): HudCampo.Leitura? =
        when (campo) {
            HudCampo.CONSUMO ->
                    when (val c = HudTelemetry.consumo()) {
                        is HudFormato.Consumo.Andando ->
                                HudCampo.Leitura("Instantâneo", HudFormato.kmPorLitro(c.kmPorLitro), "km/L")
                        is HudFormato.Consumo.Parado ->
                                HudCampo.Leitura("Parado", HudFormato.decimal(c.litrosPorHora), "L/h")
                        HudFormato.Consumo.Eletrico ->
                                HudCampo.Leitura("Motor a combustão desligado", "Elétrico")
                        null -> HudCampo.Leitura("Instantâneo", "--", "km/L")
                    }
            HudCampo.AUTONOMIA -> {
                val autonomia = HudTelemetry.autonomiaCombustivelKm()
                val eletrica = HudTelemetry.autonomiaEletricaKm()
                val total =
                        if (eletrica != null && eletrica > 0 && autonomia != null) autonomia + eletrica
                        else autonomia
                HudCampo.Leitura("Autonomia", total?.let { HudFormato.milhar(it) } ?: "--", "km")
            }
            HudCampo.TANQUE -> {
                val tanque = HudTelemetry.combustivelPct()
                HudCampo.Leitura("Tanque", tanque?.toString() ?: "--", "%", barra = tanque?.let { it / 100f })
            }
            HudCampo.BATERIA ->
                    HudTelemetry.bateriaPct()?.let {
                        HudCampo.Leitura("Bateria", it.toString(), "%", barra = it / 100f)
                    }
            HudCampo.POTENCIA -> {
                val potencia = HudTelemetry.potenciaKw()
                val regenerando = potencia != null && potencia < -0.5f
                HudCampo.Leitura(
                        if (regenerando) "Regenerando" else "Potência",
                        potencia?.let { abs(it).roundToInt().toString() } ?: "--",
                        "kW"
                )
            }
            HudCampo.TORQUE ->
                    HudCampo.Leitura(
                            "Torque est.",
                            HudTelemetry.torqueNm()?.let { HudFormato.milhar(it.roundToInt()) } ?: "--",
                            "Nm"
                    )
            HudCampo.ROTACAO ->
                    HudCampo.Leitura(
                            "Rotação",
                            HudTelemetry.rotacaoMotorRpm()?.let { HudFormato.milhar(it) } ?: "--",
                            "rpm"
                    )
            HudCampo.MODO -> HudCampo.Leitura("Modo", HudTelemetry.modoDeConducao())
            HudCampo.TRACAO -> if (HudTelemetry.tracaoAwd()) HudCampo.Leitura("Tração", "AWD") else null
            HudCampo.ODOMETRO ->
                    HudCampo.Leitura(
                            "Odômetro",
                            HudTelemetry.odometroKm()?.let { HudFormato.milhar(it) } ?: "--",
                            "km"
                    )
            HudCampo.MARCHA -> HudCampo.Leitura("Marcha", HudTelemetry.marcha())
            HudCampo.TEMP_FORA ->
                    HudCampo.Leitura("Fora", HudTelemetry.temperaturaExterna()?.let { HudFormato.graus(it) } ?: "--")
            HudCampo.TEMP_DENTRO ->
                    HudCampo.Leitura("Dentro", HudTelemetry.temperaturaInterna()?.let { HudFormato.graus(it) } ?: "--")
            HudCampo.PNEUS -> {
                val pneus = HudTelemetry.avisoDePneu()
                HudCampo.Leitura(
                        "Pneus",
                        when (pneus) {
                            true -> "Verificar"
                            false -> "OK"
                            null -> "--"
                        },
                        alerta = pneus == true
                )
            }
            HudCampo.VELOCIDADE ->
                    HudCampo.Leitura("Velocidade", HudTelemetry.velocidadeKmh()?.toString() ?: "--", "km/h")
            HudCampo.LIMITE ->
                    HudCampo.Leitura("Limite", HudTelemetry.limiteDaViaKmh()?.toString() ?: "--", "km/h")
            HudCampo.HORA -> {
                // O HUD repinta de segundo em segundo (HudOverlayActivity): a hora vira sozinha.
                val agora = Calendar.getInstance()
                HudCampo.Leitura(
                        "Hora",
                        String.format(
                                Locale.ROOT,
                                "%02d:%02d",
                                agora.get(Calendar.HOUR_OF_DAY),
                                agora.get(Calendar.MINUTE)
                        )
                )
            }
        }
