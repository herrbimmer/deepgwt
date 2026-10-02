package br.com.opengw.seguranca

import br.com.opengw.veiculo.Sinais
import br.com.opengw.managers.FluxoLimitado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

/** Correções da auditoria da 8.4 (Alpha 8.5): B1, B3, B4, B5, B6, B7, B8. */
class SegurancaTest {

    // ── Origem do pedido (B4) ──

    private fun d(origin: String?, host: String? = "192.168.43.1:8766", site: String? = null, metodo: String = "POST", caminho: String = "/set") =
        OrigemDoPedido.decidir(metodo, caminho, origin, host, site)

    @Test
    fun `quem nao e navegador entra sem CORS`() {
        assertEquals(OrigemDoPedido.Decisao.Aceitar(null), d(null))
        assertEquals(OrigemDoPedido.Decisao.Aceitar(null), d("", site = "none"))
    }

    @Test
    fun `launcher 3D e paginas locais entram com CORS`() {
        assertEquals(OrigemDoPedido.Decisao.Aceitar(OrigemDoPedido.LAUNCHER_3D), d(OrigemDoPedido.LAUNCHER_3D, host = "127.0.0.1:8766"))
        assertEquals(OrigemDoPedido.Decisao.Aceitar("http://localhost:1420"), d("http://localhost:1420"))
        assertEquals(OrigemDoPedido.Decisao.Aceitar("http://127.0.0.1:5173"), d("http://127.0.0.1:5173"))
        assertEquals(OrigemDoPedido.Decisao.Aceitar("tauri://localhost"), d("tauri://localhost"))
    }

    @Test
    fun `pagina do celular servida pelo carro e mesma origem`() {
        assertEquals(OrigemDoPedido.Decisao.Aceitar(null), d("http://192.168.43.1:8766", caminho = "/licenca/ativar"))
    }

    @Test
    fun `site qualquer e DNS rebinding param`() {
        assertEquals(OrigemDoPedido.Decisao.Recusar, d("https://site-malicioso.com"))
        assertEquals(OrigemDoPedido.Decisao.Recusar, d("http://192.168.43.5:8080"))
        assertEquals("página em sandbox", OrigemDoPedido.Decisao.Recusar, d("null"))
        assertEquals(
            "Host com nome de domínio = rebinding",
            OrigemDoPedido.Decisao.Recusar,
            d("http://evil.example:8766", host = "evil.example:8766")
        )
        assertEquals(OrigemDoPedido.Decisao.Recusar, d("https://appassets.androidplatform.net.evil.com"))
        assertEquals(OrigemDoPedido.Decisao.Recusar, d("http://user@localhost"))
    }

    @Test
    fun `img e link de outro site param, menos a pagina de ativar`() {
        assertEquals(OrigemDoPedido.Decisao.Recusar, d(null, site = "cross-site", metodo = "GET", caminho = "/files/delete"))
        assertEquals(OrigemDoPedido.Decisao.Aceitar(null), d(null, site = "cross-site", metodo = "GET", caminho = "/ativar"))
        assertEquals(OrigemDoPedido.Decisao.Aceitar(null), d(null, site = "same-origin", metodo = "GET", caminho = "/licenca"))
    }

    // ── Corpo em fluxo (B1) ──

    @Test
    fun `fluxo limitado para no Content-Length e conta`() {
        val dados = ByteArray(300) { it.toByte() }
        val f = FluxoLimitado(ByteArrayInputStream(dados), 200)
        val saida = f.readBytes()
        assertEquals(200, saida.size)
        assertEquals(200L, f.lidos)
        assertEquals(-1, f.read())
        val curto = FluxoLimitado(ByteArrayInputStream(ByteArray(50)), 200)
        curto.readBytes()
        assertEquals("cliente caiu no meio", 50L, curto.lidos)
    }

    // ── Manifesto e código (B3, B7, B8, B6) ──

    private val manifesto = File("src/main/AndroidManifest.xml").readText()

    private fun blocoDoComponente(nome: String): String {
        val i = manifesto.indexOf("android:name=\"$nome\"")
        assertTrue("$nome no manifesto", i >= 0)
        return manifesto.substring(i, manifesto.indexOf('>', i))
    }

    @Test
    fun `componentes exportados fechados`() {
        assertTrue("B3", blocoDoComponente(".broadcastReceivers.ViewerBridgeReceiver").contains("android:exported=\"false\""))
        assertTrue("B7", blocoDoComponente("br.com.opengw.telas.ReceptorDoRecuo").contains("android.permission.WRITE_SECURE_SETTINGS"))
        // B8 (serviço do Frida aberto a qualquer app) sumiu junto com o Frida no IndependentGW.
        assertFalse("B8", File("src/main/java/br/com/opengw/services/FridaTakeActionService.java").exists())
    }

    // B6 (fechar ao desligar não dispara pela câmera do motorista com o carro ligado) saiu daqui junto
    // com o motor herdado; o caso está em br.com.opengw.veiculo.automacoes.AutomacoesPorEventoTest (B.4).
}
