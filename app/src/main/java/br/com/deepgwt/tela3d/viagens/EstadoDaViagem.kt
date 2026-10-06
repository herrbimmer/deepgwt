package br.com.deepgwt.tela3d.viagens

/** Um ponto da trilha gravada (uma linha de `trip_points`). Reais ausentes são NaN. */
data class PontoDeTrilha(
    val t: Long,
    val lat: Double,
    val lon: Double,
    val alt: Double,
    val kmh: Double,
    val kw: Double,
    /** 0 = sem dado, 1 = L/100 km andando, 4 = L/h parado. */
    val modoDeCombustivel: Int,
    val taxaDeCombustivel: Double,
    /** 1 = combustão ligada, 0 = desligada, -1 = desconhecido. */
    val combustao: Int,
    val km: Double,
)

enum class TipoDeParada(val texto: String) {
    ABASTECIMENTO("refuel"),
    RECARGA("charge");

    companion object {
        fun doTexto(texto: String?): TipoDeParada? = entries.firstOrNull { it.texto == texto }
    }
}

/**
 * Um abastecimento ou uma recarga detectado com o carro desligado. [quantidade] é em litros para o
 * abastecimento e em pontos percentuais de bateria para a recarga; [antes] e [depois] são os níveis (%).
 */
data class ParadaDeViagem(
    val tipo: TipoDeParada,
    val t: Long,
    val lat: Double,
    val lon: Double,
    val antes: Double,
    val depois: Double,
    val quantidade: Double,
)

/**
 * Tudo que o motor sabe da viagem aberta e que vai para o checkpoint. Os nomes dos campos seguem o
 * texto do checkpoint (ver [CodecDoCheckpoint]); o formato em disco é dado do usuário.
 */
class EstadoDaViagem {
    var aberta: Boolean = false
    var inicioMs: Long = 0L

    /** Último instante integrado. */
    var ultimoT: Long = 0L

    /** Instante em que o carro desligou (0 = ligado). */
    var desligadoEm: Long = 0L
    var ultimoPontoT: Long = 0L

    var odometroInicial: Double = Double.NaN
    var odometroUltimo: Double = Double.NaN
    var odometroUltimoT: Long = 0L

    /** Quilômetros aceitos do hodômetro nesta viagem. */
    var odometroKm: Double = 0.0
    var odometroSaltos: Int = 0

    /** A viagem começou conhecendo o hodômetro. */
    var partiuComHodometro: Boolean = false
    var distanciaIntegrada: Double = 0.0
    var distanciaIntegradaNoHodometro: Double = 0.0

    var andandoMs: Long = 0L
    var paradoMs: Long = 0L
    var eletricoKm: Double = 0.0
    var combustaoKm: Double = 0.0
    var eletricoMs: Long = 0L
    var kwhGasto: Double = 0.0
    var kwhRecuperado: Double = 0.0
    var litros: Double = 0.0
    var velocidadeMaxima: Double = 0.0
    var paradas: Int = 0
    var armouParada: Boolean = false
    var aceleracoesBruscas: Int = 0
    var frenagensBruscas: Int = 0
    var subidaM: Double = 0.0
    var referenciaDeAltitude: Double = Double.NaN

    var latInicial: Double = Double.NaN
    var lonInicial: Double = Double.NaN
    var latFinal: Double = Double.NaN
    var lonFinal: Double = Double.NaN
    var tanqueInicial: Double = Double.NaN
    var tanqueFinal: Double = Double.NaN
    var bateriaInicial: Double = Double.NaN
    var bateriaFinal: Double = Double.NaN

    var guiada: Boolean = false
    var kmPlanejado: Double = Double.NaN
    var segundosPlanejados: Int = 0
    var kmNoInicioDaGuiagem: Double = 0.0
    var instanteDoInicioDaGuiagem: Long = 0L
    var kmNoFimDaGuiagem: Double = 0.0
    var instanteDoFimDaGuiagem: Long = 0L
    var chegou: Boolean = false
    var ultimaDistanciaRestanteM: Double = Double.NaN
    var ultimaNavegacaoT: Long = 0L

    /** Primeiro e último texto bruto de cada chave "crua" recebido desde a abertura. */
    val crusPrimeiros: LinkedHashMap<String, String> = LinkedHashMap()
    val crusUltimos: LinkedHashMap<String, String> = LinkedHashMap()
}
