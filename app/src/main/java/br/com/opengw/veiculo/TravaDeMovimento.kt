package br.com.opengw.veiculo

/**
 * "O carro está parado?" — a pergunta que toda ação que mexe em peça ou corta recurso faz antes
 * (Alpha 8.5, auditoria B3/B5/B6).
 *
 * Antes cada chamador lia a velocidade do seu jeito, e três deles não liam nada: o porta-malas
 * pela ponte do launcher, o reinício da central pela rede e o fechar vidros ao desligar (que
 * disparava pela câmera de distração). A regra agora mora aqui, pura e testável.
 *
 * Marcha: "3" é P neste barramento (é o que o ASD e o recolher retrovisor já usam).
 */
object TravaDeMovimento {

    /** Até aqui ainda é "parado": o velocímetro do barramento oscila em 0,x km/h com o carro em P. */
    const val PARADO_KMH = 1f

    const val MARCHA_P = "3"

    enum class Motivo(val texto: String) {
        ANDANDO("o carro está andando"),
        FORA_DE_P("a alavanca não está em P"),
        SEM_LEITURA("a velocidade do carro não pôde ser lida")
    }

    /**
     * Por que NÃO fazer agora, ou null quando pode.
     *
     * @param exigirP além de parado, a alavanca em P (quando ela for lida).
     * @param exigirLeitura sem velocidade lida, recusa. Falso para o que serve de remédio quando o
     *   próprio carro parou de responder (reiniciar a central): aí só recusa o que se SABE andando.
     */
    fun avaliar(velocidade: String?, marcha: String?, exigirP: Boolean, exigirLeitura: Boolean): Motivo? {
        val v = velocidade?.trim()?.replace(',', '.')?.toFloatOrNull()
        if (v == null) {
            if (exigirLeitura) return Motivo.SEM_LEITURA
        } else if (v > PARADO_KMH) {
            return Motivo.ANDANDO
        }
        if (exigirP) {
            val m = marcha?.trim().orEmpty()
            if (m.isNotEmpty() && m != MARCHA_P && !m.equals("P", ignoreCase = true)) return Motivo.FORA_DE_P
        }
        return null
    }

    /** [avaliar] com a leitura do carro agora. */
    fun agora(exigirP: Boolean, exigirLeitura: Boolean): Motivo? {
        // Com o canal da central caído, o cache guarda a última velocidade (talvez 0 de antes de
        // sair andando): isso não é uma leitura, e "parado" seria um palpite.
        val canalCaido = runCatching { MotorDoVeiculo.modoReal && !MotorDoVeiculo.controleVivo }.getOrDefault(false)
        val velocidade = if (canalCaido) null else runCatching { Veiculo.ler(Sinais.CAR_BASIC_VEHICLE_SPEED.chave) }.getOrNull()
        val marcha = if (canalCaido) null else runCatching { Veiculo.ler(Sinais.CAR_BASIC_GEAR_STATUS.chave) }.getOrNull()
        return avaliar(velocidade, marcha, exigirP, exigirLeitura)
    }
}
