package br.com.opengw.rede

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** V7 da especificação: o layout exato das duas transações de tethering. */
class TetheringDoSistemaTest {

    private val registro = RegistroFalso()
    private val pacote = "br.com.opengw"

    /** O `Parcel` visto como lista de itens, na ordem de escrita. */
    private class CanalFalso(val falha: Exception? = null) : CanalDoServico {
        var codigo: Int? = null
        var comReceptor = false
        var respostaDoReceptor: ((Int) -> Unit)? = null
        val itens = ArrayList<String>()
        var chamadas = 0

        override fun transacionar(codigo: Int, aoResponder: ((Int) -> Unit)?, montar: (EscritorDePacote) -> Unit) {
            chamadas++
            this.codigo = codigo
            comReceptor = aoResponder != null
            respostaDoReceptor = aoResponder
            montar(
                object : EscritorDePacote {
                    override fun token(descritor: String) {
                        itens += "token:$descritor"
                    }

                    override fun inteiro(valor: Int) {
                        itens += "int:$valor"
                    }

                    override fun texto(valor: String?) {
                        itens += "string:$valor"
                    }

                    override fun receptor() {
                        itens += "receptor"
                    }
                },
            )
            if (falha != null) throw falha
        }
    }

    @Test
    fun `v7 startTethering e a transacao 24 com o layout exato`() {
        val canal = CanalFalso()
        val respostas = mutableListOf<Int>()
        assertTrue(TetheringPorTransacao(canal, pacote, registro).ligarWifi { respostas += it })
        assertEquals(24, canal.codigo)
        assertEquals(
            listOf(
                "token:android.net.IConnectivityManager",
                "int:0",
                "int:1",
                "receptor",
                "int:0",
                "string:$pacote",
            ),
            canal.itens,
        )
        assertTrue(canal.comReceptor)
        canal.respostaDoReceptor!!.invoke(0)
        assertEquals(listOf(0), respostas)
    }

    @Test
    fun `v7 stopTethering e a transacao 25 sem receptor`() {
        val canal = CanalFalso()
        assertTrue(TetheringPorTransacao(canal, pacote, registro).desligarWifi())
        assertEquals(25, canal.codigo)
        assertEquals(listOf("token:android.net.IConnectivityManager", "int:0", "string:$pacote"), canal.itens)
        assertFalse(canal.comReceptor)
    }

    @Test
    fun `os codigos sao a primeira transacao mais o indice 23 e 24`() {
        assertEquals(1 + 23, TetheringPorTransacao.TRANSACAO_START_TETHERING)
        assertEquals(1 + 24, TetheringPorTransacao.TRANSACAO_STOP_TETHERING)
        assertEquals("android.net.IConnectivityManager", TetheringPorTransacao.DESCRITOR)
        assertEquals(0, TetheringPorTransacao.TIPO_WIFI)
    }

    @Test
    fun `sem servico as duas devolvem false`() {
        val t = TetheringPorTransacao(null, pacote, registro)
        assertFalse(t.ligarWifi { })
        assertFalse(t.desligarWifi())
    }

    @Test
    fun `falha do binder ou da leitura registra o erro e devolve false`() {
        for (falha in listOf<Exception>(java.io.IOException("binder morreu"), SecurityException("sem permissão"), IllegalStateException("ruim"))) {
            registro.erros.clear()
            val t = TetheringPorTransacao(CanalFalso(falha), pacote, registro)
            assertFalse(t.ligarWifi { })
            assertFalse(t.desligarWifi())
            assertEquals(2, registro.erros.size)
        }
    }

    @Test
    fun `a fachada usa o canal trocado e sem canal devolve false`() {
        val canal = CanalFalso()
        TetheringDoSistema.usarCanal(canal, pacote)
        assertTrue(TetheringDoSistema.desligarWifi())
        assertEquals(25, canal.codigo)
        TetheringDoSistema.usarCanal(null, pacote)
        assertFalse(TetheringDoSistema.desligarWifi())
        assertFalse(TetheringDoSistema.ligarWifi { })
    }
}
