package com.autolink.cluster

import android.os.Parcel
import android.os.Parcelable
import br.com.opengw.painel.MensagemDoCluster

/**
 * O parcelable das mensagens do serviço do painel da fabricante (`IClusterService.setMsg`,
 * `getMsgData`, `IClusterCallback.callbackMsg`). O nome e o pacote são os que a interface AIDL da
 * central exige; o formato no `Parcel` é o dela, byte a byte (`projetores-cartao.md`, seção 5).
 *
 * Toda a lógica está em [MensagemDoCluster] (Kotlin puro, com testes); aqui só o `Parcel`.
 */
class ClusterMsgData() : Parcelable {

    private var mensagem = MensagemDoCluster()

    constructor(origem: Parcel) : this() {
        mensagem = MensagemDoCluster.ler(CanalDoParcel(origem))
    }

    /** `flag |= 1` e guarda o valor. */
    fun setIntValue(valor: Int) {
        mensagem.definirValor(valor)
    }

    /** `flag |= 2` e guarda o vetor. */
    fun setIntArrayValue(vetor: IntArray) {
        mensagem.definirVetor(vetor)
    }

    fun getIntValue(): Int = mensagem.valor

    @Suppress("FunctionName")
    fun getIntArray_value(): IntArray = mensagem.vetor

    /** Para parâmetros `out`/`inout` (a interface atual só usa `in` e retorno). */
    fun readFromParcel(origem: Parcel) {
        mensagem = MensagemDoCluster.ler(CanalDoParcel(origem))
    }

    override fun writeToParcel(destino: Parcel, flags: Int) {
        mensagem.escrever(CanalDoParcel(destino))
    }

    override fun describeContents(): Int = 0

    override fun toString(): String = mensagem.texto()

    private class CanalDoParcel(private val parcel: Parcel) : MensagemDoCluster.CanalDeInteiros {
        override fun escreverInt(valor: Int) {
            parcel.writeInt(valor)
        }

        override fun escreverVetor(vetor: IntArray) {
            parcel.writeIntArray(vetor)
        }

        override fun lerInt(): Int = parcel.readInt()

        override fun lerVetor(destino: IntArray) {
            parcel.readIntArray(destino)
        }
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ClusterMsgData> = object : Parcelable.Creator<ClusterMsgData> {
            override fun createFromParcel(origem: Parcel): ClusterMsgData = ClusterMsgData(origem)

            override fun newArray(tamanho: Int): Array<ClusterMsgData?> = arrayOfNulls(tamanho)
        }
    }
}
