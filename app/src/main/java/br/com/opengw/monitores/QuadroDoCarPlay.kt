package br.com.opengw.monitores

/**
 * O "tocando agora" que o serviço do CarPlay da central manda (B.2). Quadros acumulados: cada um
 * carrega tudo o que já se sabe da faixa.
 */
internal class QuadroDoCarPlay(
    val titulo: String?,
    val artista: String?,
    /** Em ms; −1 = desconhecida. */
    val duracaoMs: Long,
    val caminhoDaCapa: String?,
    /** 0 parado, 1 tocando, 2 pausado, 3 avançando, 4 voltando. */
    val estado: Int,
    /** Em ms; −1 = desconhecida. */
    val posicaoMs: Long,
    val aleatorio: Int,
    val repetir: Int,
    /** Bytes da capa; nulo se o quadro não trouxe. */
    val bytesDaCapa: ByteArray?,
) {

    /** Título, artista ou caminho da capa não vazios, bytes da capa, ou estado 1. Senão é um quadro em branco. */
    fun temConteudo(): Boolean =
        !titulo.isNullOrEmpty() ||
            !artista.isNullOrEmpty() ||
            !caminhoDaCapa.isNullOrEmpty() ||
            (bytesDaCapa != null && bytesDaCapa.isNotEmpty()) ||
            estado == ESTADO_TOCANDO

    companion object {
        /** O app só trata 1 como "tocando" (DB9, mantido: estados 3 e 4 contam como não tocando). */
        const val ESTADO_TOCANDO = 1

        /** O número da transação do aviso de "tocando agora" no callback. */
        const val CODIGO_DO_AVISO = 1

        /**
         * Lê o int de presença e, se for diferente de 0, o objeto, na ordem da central:
         * título, artista, duração, caminho da capa, estado, posição, aleatório, repetir, tamanho
         * da capa e, só se o tamanho for > 0, os bytes. Presença 0 = "sem objeto" (nulo).
         */
        fun lerComPresenca(leitor: LeitorDeParcel): QuadroDoCarPlay? {
            if (leitor.lerInt() == 0) return null
            return lerObjeto(leitor)
        }

        fun lerObjeto(leitor: LeitorDeParcel): QuadroDoCarPlay {
            val titulo = leitor.lerString()
            val artista = leitor.lerString()
            val duracao = leitor.lerLong()
            val caminho = leitor.lerString()
            val estado = leitor.lerInt()
            val posicao = leitor.lerLong()
            val aleatorio = leitor.lerInt()
            val repetir = leitor.lerInt()
            val tamanhoDaCapa = leitor.lerInt()
            // Último campo: tamanho <= 0 não lê mais nada (o emissor ainda escreve um int a mais).
            val bytes = if (tamanhoDaCapa > 0) leitor.lerBytes(tamanhoDaCapa) else null
            return QuadroDoCarPlay(titulo, artista, duracao, caminho, estado, posicao, aleatorio, repetir, bytes)
        }
    }
}

/**
 * Um aviso do monitor do CarPlay. Sempre entregue na thread principal. [limpar] = verdadeiro: apagar o
 * que se mostrava do CarPlay (os demais campos ficam nos valores padrão).
 */
data class TocandoNoCarPlay(
    val titulo: String? = null,
    val artista: String? = null,
    val capa: android.graphics.Bitmap? = null,
    val caminhoDaCapa: String? = null,
    val duracaoMs: Long = 0L,
    val decorridoMs: Long = 0L,
    val tocando: Boolean = false,
    val limpar: Boolean = false,
)

/** As decisões puras do monitor (B.6); relógio sempre monotônico, em ms. */
object RegrasDoMonitorDoCarPlay {
    const val GRACA_DO_QUADRO_VAZIO_MS = 4_000L
    const val INTERVALO_ENTRE_LIGACOES_MS = 5_000L
    const val INTERVALO_ENTRE_ASSINATURAS_MS = 15_000L
    const val QUADRO_VELHO_MS = 20_000L

    /**
     * Quanto falta para limpar o cartão: 0 se nunca houve quadro válido; senão o resto da graça de 4 s
     * contada do último quadro válido (o relógio andar para trás não limpa antes: devolve até 4000).
     */
    @JvmStatic
    fun esperaAteLimpar(agoraMs: Long, ultimoQuadroValidoMs: Long): Long {
        if (ultimoQuadroValidoMs <= 0L) return 0L
        return maxOf(0L, GRACA_DO_QUADRO_VAZIO_MS - maxOf(0L, agoraMs - ultimoQuadroValidoMs))
    }

    /** Pede a ligação ao serviço? Nunca com binder vivo; no mínimo 5 s depois da tentativa anterior. */
    @JvmStatic
    fun devePedirLigacao(agoraMs: Long, ultimaTentativaMs: Long, binderVivo: Boolean): Boolean {
        if (binderVivo) return false
        if (ultimaTentativaMs <= 0L) return true
        return !dentroDaJanela(agoraMs - ultimaTentativaMs, INTERVALO_ENTRE_LIGACOES_MS)
    }

    /**
     * Cancela e assina de novo? Só com binder vivo, no mínimo 15 s depois da tentativa anterior e se
     * não estamos assinados, nunca houve quadro ou o último tem 20 s ou mais.
     */
    @JvmStatic
    fun deveRenovarAssinatura(
        agoraMs: Long,
        ultimaTentativaMs: Long,
        ultimoQuadroMs: Long,
        assinado: Boolean,
        binderVivo: Boolean,
    ): Boolean {
        if (!binderVivo) return false
        if (ultimaTentativaMs > 0L && dentroDaJanela(agoraMs - ultimaTentativaMs, INTERVALO_ENTRE_ASSINATURAS_MS)) {
            return false
        }
        if (!assinado) return true
        if (ultimoQuadroMs <= 0L) return true
        return agoraMs - ultimoQuadroMs >= QUADRO_VELHO_MS
    }

    /** `0 <= diferença < janela`: o relógio andar para trás fica fora da janela. */
    private fun dentroDaJanela(diferencaMs: Long, janelaMs: Long): Boolean = diferencaMs in 0 until janelaMs
}
