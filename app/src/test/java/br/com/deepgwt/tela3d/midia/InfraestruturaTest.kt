package br.com.deepgwt.tela3d.midia

import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.net.ServerSocket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Caches (D5, E5), recuo (defeitos 16 e 5), HTTP e geometria do espaço reservado. */
class InfraestruturaTest {

    @Test
    fun `cache fifo de 3 remove a mais antiga ao inserir a quarta`() {
        val c = CacheFifo<String, String>(3)
        c.put("a", "1"); c.put("b", "2"); c.put("c", "3")
        c.put("d", "4")
        assertNull(c["a"])
        assertEquals("2", c["b"])
        assertEquals("4", c["d"])
        assertEquals(3, c.tamanho)
    }

    @Test
    fun `cache fifo atualizar chave existente nao despeja nem muda a ordem`() {
        val c = CacheFifo<String, String>(3)
        c.put("a", "1"); c.put("b", "2"); c.put("c", "3")
        c.put("a", "novo")
        assertEquals(3, c.tamanho)
        c.put("d", "4") // sai a "a", a mais antiga
        assertNull(c["a"])
        assertEquals("2", c["b"])
    }

    @Test
    fun `cache de icones e esvaziado por inteiro ao tentar inserir o nono`() {
        val c = CacheQueEsvazia<String, String>(InformacoesDosApps.MAXIMO_DE_PACOTES)
        for (i in 1..8) c.put("p$i", "v$i")
        assertEquals(8, c.tamanho)
        c.put("p1", "atualiza") // existente: não esvazia
        assertEquals(8, c.tamanho)
        c.put("p9", "v9")
        assertEquals(1, c.tamanho)
        assertNull(c["p1"])
        assertEquals("v9", c["p9"])
    }

    @Test
    fun `recuo impede repetir a mesma chave por 20 s`() {
        var t = 0L
        val r = RecuoPorChave(20_000, { t })
        assertTrue(r.podeTentar("url"))
        r.registrarFalha("url")
        assertFalse(r.podeTentar("url"))
        assertTrue(r.podeTentar("outra"))
        t = 19_999
        assertFalse(r.podeTentar("url"))
        t = 20_000
        assertTrue(r.podeTentar("url"))
        assertTrue(r.podeTentar("url")) // expirou e foi esquecida
    }

    @Test
    fun `recuo esquecer libera a chave e o limite de chaves nao cresce sem fim`() {
        var t = 0L
        val r = RecuoPorChave(20_000, { t }, limiteDeChaves = 4)
        r.registrarFalha("a")
        r.esquecer("a")
        assertTrue(r.podeTentar("a"))
        for (i in 0 until 10) r.registrarFalha("k$i")
        assertFalse(r.podeTentar("k9"))
    }

    // --- religação (defeito 5) --------------------------------------------------------------------

    @Test
    fun `religacao espera a tentativa pendente e recua depois de recusa`() {
        val p = PoliticaDeReligacao(esperaPendenteMs = 30_000, intervaloAposRecusaMs = 10_000)
        assertTrue(p.deveLigar(0, ligado = false))
        assertFalse(p.deveLigar(0, ligado = true))
        p.aoTentar(0, aceito = true)
        assertFalse(p.deveLigar(1_500, false)) // pendente: não derruba a conexão que está subindo
        assertFalse(p.deveLigar(29_999, false))
        assertTrue(p.deveLigar(30_000, false)) // a tentativa expirou
        p.aoTentar(30_000, aceito = false)
        assertFalse(p.deveLigar(39_999, false))
        assertTrue(p.deveLigar(40_000, false)) // recusa: tenta de novo só depois de 10 s
    }

    @Test
    fun `religacao conectou libera e desconectou permite ligar de novo`() {
        val p = PoliticaDeReligacao()
        p.aoTentar(0, true)
        p.aoConectar()
        assertTrue(p.deveLigar(1, ligado = false))
        p.aoTentar(5, true)
        p.aoDesconectar()
        assertTrue(p.deveLigar(6, false))
    }

    // --- HTTP -----------------------------------------------------------------------------------

    /** Servidor HTTP mínimo sobre sockets: responde por caminho e fecha a conexão a cada pedido. */
    private class ServidorFalso : AutoCloseable {
        private val socket = ServerSocket(0, 10, InetAddress.getLoopbackAddress())
        val porta: Int get() = socket.localPort
        val agentes = ArrayList<String>()

        private val fio = Thread {
            while (!socket.isClosed) {
                try {
                    socket.accept().use { atender(it) }
                } catch (_: Exception) {
                }
            }
        }.apply { isDaemon = true; start() }

        private fun atender(s: java.net.Socket) {
            val entrada = s.getInputStream().bufferedReader(Charsets.ISO_8859_1)
            val linha = entrada.readLine() ?: return
            val caminho = linha.split(" ")[1]
            var agente = ""
            while (true) {
                val cabecalho = entrada.readLine() ?: break
                if (cabecalho.isEmpty()) break
                if (cabecalho.startsWith("User-Agent:", ignoreCase = true)) agente = cabecalho.substringAfter(":").trim()
            }
            synchronized(agentes) { agentes += agente }
            val saida = s.getOutputStream()
            fun responder(codigo: Int, extra: String = "", corpo: ByteArray = ByteArray(0)) {
                val cab = "HTTP/1.1 $codigo X\r\n${extra}Content-Length: ${corpo.size}\r\nConnection: close\r\n\r\n"
                saida.write(cab.toByteArray(Charsets.ISO_8859_1))
                saida.write(corpo)
                saida.flush()
            }
            when (caminho) {
                "/ok" -> responder(200, corpo = "imagem".toByteArray())
                "/grande" -> responder(200, corpo = ByteArray(5_000))
                "/redireciona" -> responder(302, extra = "Location: /ok\r\n")
                "/laco" -> responder(302, extra = "Location: /laco\r\n")
                "/agente" -> responder(200, corpo = agente.toByteArray())
                else -> responder(404)
            }
        }

        override fun close() {
            socket.close()
        }
    }

    private fun comServidor(bloco: (String) -> Unit) {
        ServidorFalso().use { bloco("http://127.0.0.1:${it.porta}") }
    }

    @Test
    fun `http baixa o corpo e aceita so 2xx`() = comServidor { base ->
        assertEquals("imagem", String(requireNotNull(ClienteHttp.baixarBytes("$base/ok", 2_000, 2_000))))
        assertNull(ClienteHttp.baixarBytes("$base/erro", 2_000, 2_000))
        assertNull(ClienteHttp.baixarBytes("$base/nao-existe", 2_000, 2_000))
    }

    @Test
    fun `http segue redirecionamento mas desiste de laco`() = comServidor { base ->
        assertEquals("imagem", String(requireNotNull(ClienteHttp.baixarBytes("$base/redireciona", 2_000, 2_000))))
        assertNull(ClienteHttp.baixarBytes("$base/laco", 2_000, 2_000))
    }

    @Test
    fun `http respeita o limite de tamanho do corpo`() = comServidor { base ->
        assertNull(ClienteHttp.baixarBytes("$base/grande", 2_000, 2_000, limite = 4_999))
        assertEquals(5_000, requireNotNull(ClienteHttp.baixarBytes("$base/grande", 2_000, 2_000, limite = 5_000)).size)
    }

    @Test
    fun `http se identifica como o app e nunca como navegador`() = comServidor { base ->
        val agente = String(requireNotNull(ClienteHttp.baixarBytes("$base/agente", 2_000, 2_000)))
        assertEquals("DeepGWT/1.0", agente)
        assertFalse(agente.contains("Mozilla"))
        assertFalse(agente.contains("Chrome"))
    }

    @Test
    fun `http endereco invalido ou protocolo estranho devolve nulo`() {
        assertNull(ClienteHttp.baixarBytes("isto nao e url", 500, 500))
        assertNull(ClienteHttp.baixarBytes("ftp://127.0.0.1/a", 500, 500))
        assertNull(ClienteHttp.baixarBytes("http://127.0.0.1:1/a", 500, 500)) // porta fechada
    }

    @Test
    fun `leitura com limite so passa se couber`() {
        assertEquals(3, requireNotNull(ClienteHttp.lerComLimite(ByteArrayInputStream(ByteArray(3)), 3)).size)
        assertNull(ClienteHttp.lerComLimite(ByteArrayInputStream(ByteArray(4)), 3))
    }

    // --- espaço reservado da capa -------------------------------------------------------------------

    @Test
    fun `espaco reservado medidas`() {
        val g = GeometriaDoEspacoReservado
        assertEquals(48, g.lado(1f))
        assertEquals(96, g.lado(2f))
        assertEquals(16, g.lado(0.1f)) // mínimo de 16 px
        assertEquals(18f, g.raioDoAnel(48), 1e-4f) // 0,375 x lado
        assertEquals(4.8f, g.raioDoPonto(48), 1e-4f)
        assertEquals(2.976f, g.espessuraDoAnel(48), 1e-4f) // 0,062 x lado
        assertEquals(1.5f, g.espessuraDoAnel(16), 1e-4f) // piso de 1,5 px
        assertEquals(102, g.ALFA)
    }
}
