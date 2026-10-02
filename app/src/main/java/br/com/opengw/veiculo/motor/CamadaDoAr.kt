package br.com.opengw.veiculo.motor

/**
 * Suspensão do app de clima da fabricante em volta das escritas do ar (D.6 i e iv).
 *
 * Antes de cada escrita do ar, o app de clima é desligado (se ainda não está e não é o app na frente
 * do display 0); 300 ms depois da última escrita do ar, ele volta. As escritas do ar ficam em série
 * entre si (a pausa de 150 ms acontece com a trava presa, na thread de quem escreve).
 */
internal class CamadaDoAr(
    private val agenda: Agenda,
    private val shell: (Array<String>) -> String,
    private val appNaFrente: () -> String?,
    private val diario: (evento: String, detalhes: Map<String, Any?>) -> Unit,
    private val registro: RegistroLimitado,
) {
    private val trava = Any()
    private var suspenso = false
    private var retomada: Cancelavel? = null

    /** Muda a cada escrita: uma retomada agendada antes de uma escrita nova não vale mais. */
    private var geracao = 0L

    fun ehDoAr(chave: String): Boolean = chave in ChavesDoMotor.DO_AR

    /** D.6 i. Na thread de quem escreve. */
    fun antesDeEscrever() {
        synchronized(trava) {
            geracao++
            retomada?.cancelar()
            retomada = null
            if (suspenso) return
            if (appDeClimaNaFrente()) return
            shell(arrayOf("pm", "disable-user", "--user", "0", PACOTE))
            shell(arrayOf("am", "force-stop", PACOTE))
            suspenso = true
            diario("ar.suspenso", mapOf("pacote" to PACOTE))
            agenda.pausar(PAUSA_DEPOIS_DE_SUSPENDER_MS)
        }
    }

    /**
     * Na partida do motor: se o app morreu entre o `disable` e o `enable`, o app de clima da fábrica
     * ficaria desligado para sempre. `pm enable` é idempotente; só roda se esta camada não está
     * com ele suspenso de propósito.
     */
    fun religarSeSobrou() {
        synchronized(trava) {
            if (suspenso) return
            val saida = shell(arrayOf("pm", "enable", PACOTE))
            diario("ar.religado-na-partida", mapOf("pacote" to PACOTE, "saida" to saida))
        }
    }

    /** D.6 iv: com sucesso ou não, empurra a retomada para daqui a 300 ms. */
    fun depoisDeEscrever() {
        synchronized(trava) {
            if (!suspenso) return
            val minha = ++geracao
            retomada?.cancelar()
            retomada = agenda.agendar(RETOMADA_MS) { retomar(minha, tentativa = 1) }
        }
    }

    private fun retomar(daGeracao: Long, tentativa: Int) {
        synchronized(trava) {
            if (daGeracao != geracao || !suspenso) return
            retomada = null
            val saida = shell(arrayOf("pm", "enable", PACOTE))
            if (saida.isBlank() && tentativa < TENTATIVAS_DE_RETOMADA) {
                // Saída vazia = o comando nem rodou (Shizuku fora). Deixar o app de clima da
                // fábrica desligado é pior que tentar de novo.
                registro.aviso("ar.retomada", "não deu para religar $PACOTE; tentando de novo")
                retomada = agenda.agendar(INTERVALO_DE_NOVA_RETOMADA_MS) { retomar(daGeracao, tentativa + 1) }
                return
            }
            suspenso = false
            diario("ar.retomado", mapOf("pacote" to PACOTE, "tentativas" to tentativa, "saida" to saida))
        }
    }

    private fun appDeClimaNaFrente(): Boolean =
        try {
            appNaFrente() == PACOTE
        } catch (e: Exception) {
            // "Sem resposta = não está."
            false
        }

    val estaSuspenso: Boolean get() = synchronized(trava) { suspenso }

    companion object {
        const val PACOTE = "com.beantechs.hvac"
        const val PAUSA_DEPOIS_DE_SUSPENDER_MS = 150L
        const val RETOMADA_MS = 300L
        private const val TENTATIVAS_DE_RETOMADA = 5
        private const val INTERVALO_DE_NOVA_RETOMADA_MS = 1_000L
    }
}
