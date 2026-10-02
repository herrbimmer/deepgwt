package br.com.opengw.rede

enum class ModoDoRoteador { DESLIGADO, INICIANDO, WLAN, QUATRO_G, ERRO }

/** [epocaEmSegundos] = quando o daemon gravou o modo (0 = desconhecido). */
data class SituacaoDoRoteador(val modo: ModoDoRoteador, val epocaEmSegundos: Long)

/** Lê o que o daemon deixa no aparelho (saída do `status` e arquivo de estado). Sem Android. */
internal object InterpretadorDoRoteador {

    /** Depois deste tempo sem o daemon reescrever o estado (ele reescreve a cada ~5 s), é ERRO. */
    const val LIMITE_DO_ESTADO_VELHO_S = 30L

    /** Tempo de graça, depois de ligar, em que um daemon ainda ausente é "iniciando". */
    const val GRACA_DE_INICIO_MS = 20_000L

    /** Vivo = a saída aparada é exatamente `ALIVE|<pid>` com pid inteiro maior que 1. */
    fun daemonVivo(saidaDoStatus: String): Boolean {
        val partes = saidaDoStatus.trim().split('|')
        if (partes.size != 2 || partes[0] != "ALIVE") return false
        val pid = partes[1]
        if (pid.isEmpty() || !pid.all { it in '0'..'9' }) return false
        val valor = pid.toLongOrNull() ?: return false
        return valor > 1
    }

    /** O estado de um daemon vivo: `MODO|época`. */
    fun situacaoDoDaemonVivo(conteudoDoEstado: String, agoraEmSegundos: Long): SituacaoDoRoteador {
        val texto = conteudoDoEstado.trim()
        if (texto.isEmpty()) return SituacaoDoRoteador(ModoDoRoteador.INICIANDO, 0)
        val partes = texto.split('|')
        val epoca = partes.getOrNull(1)?.trim()?.toLongOrNull()?.coerceAtLeast(0) ?: 0L
        return when (partes[0]) {
            "WLAN" -> noAr(ModoDoRoteador.WLAN, epoca, agoraEmSegundos)
            "4G" -> noAr(ModoDoRoteador.QUATRO_G, epoca, agoraEmSegundos)
            else -> SituacaoDoRoteador(ModoDoRoteador.INICIANDO, epoca)
        }
    }

    private fun noAr(modo: ModoDoRoteador, epoca: Long, agora: Long): SituacaoDoRoteador =
        if (epoca > 0 && agora - epoca > LIMITE_DO_ESTADO_VELHO_S) {
            SituacaoDoRoteador(ModoDoRoteador.ERRO, epoca)
        } else {
            SituacaoDoRoteador(modo, epoca)
        }

    /** O daemon não está vivo: dentro do tempo de graça ainda é "iniciando". */
    fun situacaoDoDaemonMorto(msDesdeAGraca: Long?): SituacaoDoRoteador =
        if (msDesdeAGraca != null && msDesdeAGraca < GRACA_DE_INICIO_MS) {
            SituacaoDoRoteador(ModoDoRoteador.INICIANDO, 0)
        } else {
            SituacaoDoRoteador(ModoDoRoteador.ERRO, 0)
        }
}
