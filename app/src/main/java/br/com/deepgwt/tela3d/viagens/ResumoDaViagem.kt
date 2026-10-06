package br.com.deepgwt.tela3d.viagens

import kotlin.math.min

/** As razões do resumo (consumo, eficiência, fração elétrica, velocidade média). Puras. */
object CalculosDoResumo {
    private const val LITROS_MINIMOS_PARA_CONSUMO = 0.05
    private const val KM_MINIMOS_PARA_RAZAO = 0.1
    private const val MS_POR_HORA = 3_600_000.0

    /** km/L; NaN se abasteceu menos de 0,05 L. */
    fun kmPorLitro(km: Double, litros: Double): Double =
        if (litros >= LITROS_MINIMOS_PARA_CONSUMO) km / litros else Double.NaN

    /** kWh/100 km líquidos (gastos - recuperados); NaN se andou menos de 0,1 km. */
    fun kwhPor100Km(kwhGasto: Double, kwhRecuperado: Double, km: Double): Double =
        if (km >= KM_MINIMOS_PARA_RAZAO) 100.0 * (kwhGasto - kwhRecuperado) / km else Double.NaN

    /** Quilômetros em modo elétrico: a fração `ev/(ev+ice)` (no máximo 1) aplicada ao km. */
    fun kmEletrico(eletricoKm: Double, combustaoKm: Double, km: Double): Double {
        val soma = eletricoKm + combustaoKm
        return if (soma > 0.0) min(1.0, eletricoKm / soma) * km else 0.0
    }

    /** Parte da viagem rodada em modo elétrico; NaN se andou menos de 0,1 km. */
    fun fracaoEletrica(eletricoKm: Double, km: Double): Double =
        if (km >= KM_MINIMOS_PARA_RAZAO) min(1.0, eletricoKm / km) else Double.NaN

    /** Distância integrada / tempo andando; NaN sem tempo andando. */
    fun velocidadeMedia(kmIntegrado: Double, andandoMs: Long): Double =
        if (andandoMs > 0L) kmIntegrado / (andandoMs / MS_POR_HORA) else Double.NaN
}

/** O resumo de uma viagem (aberta ou fechada). Reais sem valor são NaN. */
data class ResumoDaViagem(
    val inicioMs: Long,
    /** O fim da viagem; na viagem aberta é "agora". */
    val fimMs: Long,
    val aberta: Boolean,
    val km: Double,
    val kmIntegrado: Double,
    val odometroInicial: Double,
    val odometroFinal: Double,
    val odometroSaltos: Int,
    val andandoMs: Long,
    val paradoMs: Long,
    val eletricoKm: Double,
    val eletricoMs: Long,
    val kwhGasto: Double,
    val kwhRecuperado: Double,
    val litros: Double,
    val velocidadeMaxima: Double,
    val paradas: Int,
    val aceleracoesBruscas: Int,
    val frenagensBruscas: Int,
    val subidaM: Double,
    val latInicial: Double,
    val lonInicial: Double,
    val latFinal: Double,
    val lonFinal: Double,
    val tanqueInicial: Double,
    val tanqueFinal: Double,
    val bateriaInicial: Double,
    val bateriaFinal: Double,
    val guiada: Boolean,
    val chegou: Boolean,
    /** NaN se a viagem não foi guiada. */
    val kmPlanejado: Double,
    val segundosPlanejados: Int,
    /** NaN se a viagem não foi guiada. */
    val kmGuiados: Double,
    val segundosGuiados: Int,
    val crusPrimeiros: Map<String, String>,
    val crusUltimos: Map<String, String>,
) {
    val kmPorLitro: Double get() = CalculosDoResumo.kmPorLitro(km, litros)
    val kwhPor100Km: Double get() = CalculosDoResumo.kwhPor100Km(kwhGasto, kwhRecuperado, km)
    val fracaoEletrica: Double get() = CalculosDoResumo.fracaoEletrica(eletricoKm, km)
    val velocidadeMedia: Double get() = CalculosDoResumo.velocidadeMedia(kmIntegrado, andandoMs)

    /** Regra de C-8: uma viagem fechada só é gravada com 0,2 km ou 60 s andando. */
    val vale: Boolean get() = km >= KM_MINIMOS_PARA_GRAVAR || andandoMs >= ANDANDO_MINIMO_PARA_GRAVAR_MS

    companion object {
        const val KM_MINIMOS_PARA_GRAVAR = 0.2
        const val ANDANDO_MINIMO_PARA_GRAVAR_MS = 60_000L
    }
}
