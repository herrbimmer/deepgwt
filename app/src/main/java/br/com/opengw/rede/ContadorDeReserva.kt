package br.com.opengw.rede

/**
 * Contador de reserva do uso de dados móveis, para quando o contador do sistema
 * (`NetworkStatsManager`) não responde: acumula a diferença das leituras de `TrafficStats` entre
 * chamadas, e o estado vive nas três preferências `mobileDataTraffic*`.
 *
 * Não é thread-safe: quem chama serializa.
 */
internal class ContadorDeReserva(private val armazem: ArmazemDeChaves) {

    /**
     * Uma chamada. [recebidos] e [enviados] são os contadores móveis do sistema; negativo = não
     * suportado (a leitura vira "inválida" e nada muda além de regravar). Devolve o acumulado.
     */
    fun atualizar(etiquetaDoCiclo: String, recebidos: Long, enviados: Long): Long {
        var acumulado = armazem.lerLong(ChavesDaRede.RESERVA_ACUMULADO, 0L)
        var ultima = armazem.lerLong(ChavesDaRede.RESERVA_ULTIMA_LEITURA, SEM_LEITURA)
        val etiquetaGravada = armazem.lerString(ChavesDaRede.RESERVA_ETIQUETA, "")

        if (etiquetaGravada != etiquetaDoCiclo) {
            // Ciclo novo: zera e só estabelece a base na próxima leitura válida.
            acumulado = 0L
            ultima = SEM_LEITURA
        }

        if (recebidos >= 0 && enviados >= 0) {
            val atual = recebidos + enviados
            if (ultima in 0..atual) {
                acumulado += atual - ultima
            } else if (ultima > atual) {
                // O contador do sistema zerou (reinício do aparelho).
                acumulado += atual
            }
            ultima = atual
        }

        armazem.gravarVarios(
            mapOf(
                ChavesDaRede.RESERVA_ACUMULADO to acumulado,
                ChavesDaRede.RESERVA_ULTIMA_LEITURA to ultima,
                ChavesDaRede.RESERVA_ETIQUETA to etiquetaDoCiclo,
            ),
        )
        return acumulado
    }

    private companion object {
        const val SEM_LEITURA = -1L
    }
}
