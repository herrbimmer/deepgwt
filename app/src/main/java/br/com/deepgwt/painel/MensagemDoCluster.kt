// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.painel

/**
 * O conteúdo do parcelable `com.autolink.cluster.ClusterMsgData` da fabricante
 * (`projetores-cartao.md`, seção 5). Kotlin puro: a classe com o nome da fabricante só repassa o
 * `Parcel` para [escrever]/[ler] por um [CanalDeInteiros].
 *
 * Formato no `Parcel` (tem de ser byte a byte o da central): `flag`; se `flag & 1`, o valor; se
 * `flag & 2`, o tamanho do vetor e depois o vetor como `writeIntArray` (que escreve o tamanho de
 * novo e os elementos).
 */
class MensagemDoCluster {

    /** O pedaço do `android.os.Parcel` que a mensagem usa. */
    interface CanalDeInteiros {
        fun escreverInt(valor: Int)

        /** Como `Parcel.writeIntArray`: o tamanho e os elementos. */
        fun escreverVetor(vetor: IntArray)

        fun lerInt(): Int

        /** Como `Parcel.readIntArray(destino)`: lê o tamanho, confere com o do destino e preenche. */
        fun lerVetor(destino: IntArray)
    }

    companion object {
        const val TEM_VALOR = 1
        const val TEM_VETOR = 2

        /** Lê na ordem da escrita. */
        @JvmStatic
        fun ler(canal: CanalDeInteiros): MensagemDoCluster {
            val mensagem = MensagemDoCluster()
            mensagem.flag = canal.lerInt()
            if (mensagem.flag and TEM_VALOR != 0) mensagem.valor = canal.lerInt()
            if (mensagem.flag and TEM_VETOR != 0) {
                val tamanho = canal.lerInt()
                val vetor = IntArray(tamanho.coerceAtLeast(0))
                canal.lerVetor(vetor)
                mensagem.vetor = vetor
            }
            return mensagem
        }
    }

    var flag: Int = 0
        private set

    var valor: Int = 0
        private set

    var vetor: IntArray = IntArray(0)
        private set

    fun definirValor(novo: Int) {
        flag = flag or TEM_VALOR
        valor = novo
    }

    fun definirVetor(novo: IntArray) {
        flag = flag or TEM_VETOR
        vetor = novo
    }

    fun escrever(canal: CanalDeInteiros) {
        canal.escreverInt(flag)
        if (flag and TEM_VALOR != 0) canal.escreverInt(valor)
        if (flag and TEM_VETOR != 0) {
            canal.escreverInt(vetor.size)
            canal.escreverVetor(vetor)
        }
    }

    /** `ClusterMsgData{int_value=<v>, intArray_value=[a, b]}`. */
    fun texto(): String = "ClusterMsgData{int_value=$valor, intArray_value=${vetor.contentToString()}}"
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
