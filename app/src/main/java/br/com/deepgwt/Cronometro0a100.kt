package br.com.deepgwt

/**
 * O cronômetro de 0 a 100 km/h do painel esportivo.
 *
 * Uma corrida só começa depois de o carro ter estado em repouso (menos de 1 km/h) e saído dele;
 * termina ao cruzar 100 km/h, e depois disso a tela só volta a cronometrar quando o carro parar de
 * novo. Sem isso, a amostra seguinte a 100 km/h abria uma corrida nova e a conclusão dela gravava
 * por cima um tempo de décimos de segundo, e abrir a tela já em movimento "cronometrava" um 0-100
 * que nunca existiu.
 *
 * Uma freada até quase zero no meio da corrida cancela, sem gravar tempo falso.
 */
class Cronometro0a100 {

    /** O tempo da última corrida concluída, em milissegundos; `null` até a primeira. */
    var tempoMs: Long? = null
        private set

    /** Início da corrida em curso (relógio monotônico); `null` sem corrida. */
    var inicioMs: Long? = null
        private set

    private var armado = false
    private var ultimoKmh: Float? = null

    /** Uma leitura de velocidade, com o relógio monotônico do momento. */
    fun amostra(kmh: Float, agoraMs: Long) {
        ultimoKmh = kmh
        val inicio = inicioMs
        when {
            kmh < REPOUSO_KMH -> {
                inicioMs = null
                armado = true
            }
            inicio == null && armado -> {
                inicioMs = agoraMs
                armado = false
            }
            inicio != null && kmh >= META_KMH -> {
                tempoMs = agoraMs - inicio
                inicioMs = null
            }
        }
    }

    /**
     * O botão "Zerar": esquece o tempo e a corrida. Com o carro parado a próxima partida já conta
     * (a velocidade não muda, então nenhuma amostra nova viria armar); andando, exige um repouso.
     */
    fun zerar() {
        tempoMs = null
        inicioMs = null
        armado = (ultimoKmh ?: Float.MAX_VALUE) < REPOUSO_KMH
    }

    private companion object {
        const val REPOUSO_KMH = 1f
        const val META_KMH = 100f
    }
}
