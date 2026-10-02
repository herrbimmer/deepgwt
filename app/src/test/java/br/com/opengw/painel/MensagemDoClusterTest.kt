package br.com.opengw.painel

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** V3 de `projetores-cartao.md`: os inteiros no `Parcel`, com um `Parcel` falso. */
class MensagemDoClusterTest {

    /** Imita `android.os.Parcel` (escrita/leitura de int e de int[] com o tamanho na frente). */
    private class ParcelFalso : MensagemDoCluster.CanalDeInteiros {
        val inteiros = ArrayList<Int>()
        private var posicao = 0

        override fun escreverInt(valor: Int) {
            inteiros.add(valor)
        }

        override fun escreverVetor(vetor: IntArray) {
            inteiros.add(vetor.size)
            for (v in vetor) inteiros.add(v)
        }

        override fun lerInt(): Int = inteiros[posicao++]

        override fun lerVetor(destino: IntArray) {
            val n = lerInt()
            if (n != destino.size) throw RuntimeException("bad array lengths")
            for (i in destino.indices) destino[i] = lerInt()
        }
    }

    private fun confere(montar: MensagemDoCluster.() -> Unit, vararg esperado: Int) {
        val m = MensagemDoCluster().apply(montar)
        val p = ParcelFalso()
        m.escrever(p)
        assertEquals(esperado.toList(), p.inteiros)
        val lida = MensagemDoCluster.ler(p)
        assertEquals(m.flag, lida.flag)
        assertEquals(m.valor, lida.valor)
        assertArrayEquals(m.vetor, lida.vetor)
    }

    @Test
    fun vetoresDaFabricante() {
        confere({}, 0)
        confere({ definirValor(133) }, 1, 133)
        confere({ definirValor(1) }, 1, 1)
        confere({ definirVetor(intArrayOf(7, 8)) }, 2, 2, 2, 7, 8)
        confere({ definirValor(5); definirVetor(intArrayOf()) }, 3, 5, 0, 0)
        confere({ definirValor(5); definirVetor(intArrayOf(9)) }, 3, 5, 1, 1, 9)
    }

    @Test
    fun texto() {
        assertEquals("ClusterMsgData{int_value=0, intArray_value=[]}", MensagemDoCluster().texto())
        val m = MensagemDoCluster().apply {
            definirValor(3)
            definirVetor(intArrayOf(1, 2))
        }
        assertEquals("ClusterMsgData{int_value=3, intArray_value=[1, 2]}", m.texto())
    }
}
