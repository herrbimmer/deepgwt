// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.barra

/**
 * O mudo do Android Auto (casos 59–61): o lançador intercepta a tecla de mudo e a barra escreve no
 * carro, confere e reafirma. Sem Android direto (carro e agenda injetados), para o teste andar com
 * relógio falso.
 */
internal class MudoDoAndroidAuto(
    private val carro: Carro,
    /** Roda [tarefa] em segundo plano depois de [atrasoMs]. */
    private val agendar: (atrasoMs: Long, tarefa: () -> Unit) -> Unit,
    /** Publica o mudo no retrato (na principal, por quem implementa). */
    private val publicar: (Boolean) -> Unit,
    /** O mudo do retrato agora. */
    private val mudoVisual: () -> Boolean,
) {
    interface Carro {
        fun lerDoCarro(chave: String): String?
        fun lerDoCache(chave: String): String?
        fun escrever(chave: String, valor: String)
    }

    @Volatile
    private var geracao = 0L

    @Volatile
    private var volumeGuardado: Int? = null

    private fun ler(chave: String): String? = carro.lerDoCarro(chave) ?: carro.lerDoCache(chave)

    /** Caso 60. */
    fun lerMudo(): Boolean? = RegrasDaMidia.combinarMudo(
        listOf(
            carro.lerDoCarro(MUTE_STATE), carro.lerDoCache(MUTE_STATE),
            carro.lerDoCarro(MEDIA_MUTE_STATE), carro.lerDoCache(MEDIA_MUTE_STATE),
        )
    )

    private fun volume(): Int? = ler(MEDIA_VOLUME)?.trim()?.toDoubleOrNull()?.toInt()

    /** Caso 61. */
    fun lerMudoComVolume(alvo: Boolean?): Boolean? = RegrasDaMidia.mudoComVolume(lerMudo(), volume(), alvo)

    /** O teto do volume restaurado (M-6: a faixa do carro; senão 30). */
    private fun tetoDoVolume(): Int =
        if (DecisoesDaBarra.MUDO_RESTAURA_NA_FAIXA_DO_CARRO) ContasDaBarra.faixaDoVolume(ler(MEDIA_VOLUME_RANGE)).last
        else ContasDaBarra.FAIXA_DE_RESERVA_DO_VOLUME.last

    private fun escreverMudo(alvo: Boolean, restaurar: Int) {
        val v = if (alvo) "1" else "0"
        carro.escrever(MEDIA_MUTE_STATE, v)
        carro.escrever(MUTE_STATE, v)
        carro.escrever(MEDIA_VOLUME, if (alvo) "0" else restaurar.coerceIn(0, tetoDoVolume().coerceAtLeast(0)).toString())
    }

    /** Caso 59, depois das guardas de quem chama. Devolve true. */
    fun alternar(): Boolean {
        val alvo = !mudoVisual()
        val minha = ++geracao
        agendar(0L) {
            var restaurar = VOLUME_PADRAO_AO_DESMUTAR
            if (alvo) {
                volume()?.takeIf { it > 0 }?.let { volumeGuardado = it }
            } else {
                restaurar = volumeGuardado ?: VOLUME_PADRAO_AO_DESMUTAR
                volumeGuardado = null
            }
            escreverMudo(alvo, restaurar)
            agendar(CONFERIR_MS) {
                if (lerMudoComVolume(alvo) != alvo) carro.escrever(MUTE_ADJUST_ACTION, "2")
                publicar(alvo)
            }
            for (quando in REAFIRMAR_MS) {
                agendar(quando) {
                    if (geracao != minha) return@agendar
                    val lido = lerMudoComVolume(alvo) ?: mudoVisual()
                    publicar(lido)
                    if (lido != alvo) {
                        escreverMudo(alvo, restaurar)
                        publicar(alvo)
                    }
                }
            }
        }
        return true
    }

    companion object {
        const val MUTE_STATE = "sys.settings.audio.mute_state"
        const val MEDIA_MUTE_STATE = "sys.settings.audio.media_mute_state"
        const val MEDIA_VOLUME = "sys.settings.audio.media_volume"
        const val MEDIA_VOLUME_RANGE = "sys.settings.audio.media_volume_range"
        const val MUTE_ADJUST_ACTION = "sys.settings.audio.mute_adjust_action"
        const val VOLUME_PADRAO_AO_DESMUTAR = 8
        const val CONFERIR_MS = 450L

        /** Contados a partir do pedido; a agenda já andou 0 ms quando eles são marcados. */
        val REAFIRMAR_MS = longArrayOf(3_800L, 7_200L)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
