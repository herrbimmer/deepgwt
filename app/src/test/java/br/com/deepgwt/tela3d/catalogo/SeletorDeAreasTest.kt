package br.com.deepgwt.tela3d.catalogo

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SeletorDeAreasTest {

    // --- Conversão I2 -------------------------------------------------------------------------

    @Test
    fun `conversao aplica escala e origem`() {
        val a = ConversaoDoSeletor.converter(100f, 20f, 140f, 56f, true, 2f, 10, 30)
        assertEquals(AreaDoSeletor(210, 70, 280, 112, true), a)
    }

    @Test
    fun `conversao arredonda meio para cima`() {
        val a = ConversaoDoSeletor.converter(0.25f, 0.25f, 2.25f, 2.25f, true, 2f, 0, 0)
        // 0.5 -> 1 ; 4.5 -> 5
        assertEquals(AreaDoSeletor(1, 1, 5, 5, true), a)
    }

    @Test
    fun `escala menor ou igual a zero vale 1`() {
        assertEquals(AreaDoSeletor(5, 6, 30, 20, true), ConversaoDoSeletor.converter(5f, 6f, 30f, 20f, true, 0f, 0, 0))
        assertEquals(AreaDoSeletor(5, 6, 30, 20, true), ConversaoDoSeletor.converter(5f, 6f, 30f, 20f, true, -2f, 0, 0))
    }

    @Test
    fun `tamanho menor ou igual a 1 nao e desejado`() {
        assertFalse(ConversaoDoSeletor.converter(0f, 0f, 1f, 50f, true, 1f, 0, 0).querer)
        assertFalse(ConversaoDoSeletor.converter(0f, 0f, 50f, 1f, true, 1f, 0, 0).querer)
        assertTrue(ConversaoDoSeletor.converter(0f, 0f, 2f, 2f, true, 1f, 0, 0).querer)
    }

    @Test
    fun `querer falso da zeros e nao desejado`() {
        val a = ConversaoDoSeletor.converter(0f, 0f, 0f, 0f, false, 1f, 100, 200)
        assertEquals(AreaDoSeletor(100, 200, 0, 0, false), a)
    }

    @Test
    fun `largura negativa vira zero`() {
        val a = ConversaoDoSeletor.converter(0f, 0f, -5f, -5f, true, 1f, 0, 0)
        assertEquals(0, a.largura)
        assertEquals(0, a.altura)
        assertFalse(a.querer)
    }

    // --- Máquina de estados -------------------------------------------------------------------

    private class JanelaFalsa : JanelaDoSeletor {
        val eventos = ArrayList<String>()
        var falharAoAnexar = false
        override fun anexar(x: Int, y: Int, largura: Int, altura: Int): Boolean {
            eventos.add("anexar $x,$y ${largura}x$altura")
            return !falharAoAnexar
        }

        override fun atualizar(x: Int, y: Int, largura: Int, altura: Int): Boolean {
            eventos.add("atualizar $x,$y ${largura}x$altura")
            return true
        }

        override fun remover() {
            eventos.add("remover")
        }
    }

    private class HostFalso : AnfitriaoDoToque {
        override fun repassarToque(evento: MotionEvent): Boolean = true
    }

    /** Fila que faz o papel da thread principal. */
    private class Fila {
        val pendentes = ArrayList<Runnable>()
        val postar: (Runnable) -> Unit = { pendentes.add(it) }
        fun rodar() {
            val copia = ArrayList(pendentes)
            pendentes.clear()
            copia.forEach { it.run() }
        }
    }

    @Test
    fun `sem servico conectado o estado fica guardado e a conexao aplica`() {
        val m = MaquinaDoSeletor()
        val host = HostFalso()
        val janela = JanelaFalsa()
        val fila = Fila()
        m.aplicar(host, 10, 20, 300, 80, true)
        assertTrue(janela.eventos.isEmpty())
        assertEquals(AreaDoSeletor(10, 20, 300, 80, true), m.ultimaArea())
        assertSame(host, m.anfitriao())

        m.conectar(janela, fila.postar)
        assertTrue("a conexão só agenda", janela.eventos.isEmpty())
        fila.rodar()
        assertEquals(listOf("anexar 10,20 300x80"), janela.eventos)
        assertTrue(m.estaAnexada())
    }

    @Test
    fun `querer falso remove a janela anexada e nao remove duas vezes`() {
        val m = MaquinaDoSeletor()
        val janela = JanelaFalsa()
        val fila = Fila()
        m.conectar(janela, fila.postar)
        fila.rodar()
        m.aplicar(null, 1, 2, 30, 40, true)
        fila.rodar()
        m.aplicar(null, 0, 0, 0, 0, false)
        fila.rodar()
        m.aplicar(null, 0, 0, 0, 0, false)
        fila.rodar()
        assertEquals(listOf("anexar 1,2 30x40", "remover"), janela.eventos)
        assertFalse(m.estaAnexada())
    }

    @Test
    fun `ja anexada so atualiza o layout`() {
        val m = MaquinaDoSeletor()
        val janela = JanelaFalsa()
        val fila = Fila()
        m.conectar(janela, fila.postar)
        fila.rodar()
        m.aplicar(null, 1, 2, 30, 40, true)
        fila.rodar()
        m.aplicar(null, 5, 6, 70, 80, true)
        fila.rodar()
        assertEquals(listOf("anexar 1,2 30x40", "atualizar 5,6 70x80"), janela.eventos)
    }

    @Test
    fun `falha ao anexar marca nao anexada e a proxima sincronizacao tenta de novo`() {
        val m = MaquinaDoSeletor()
        val janela = JanelaFalsa().also { it.falharAoAnexar = true }
        val fila = Fila()
        m.conectar(janela, fila.postar)
        fila.rodar()
        m.aplicar(null, 1, 2, 30, 40, true)
        fila.rodar()
        assertFalse(m.estaAnexada())
        janela.falharAoAnexar = false
        m.aplicar(null, 1, 2, 30, 40, true)
        fila.rodar()
        assertTrue(m.estaAnexada())
        assertEquals(listOf("anexar 1,2 30x40", "anexar 1,2 30x40"), janela.eventos)
    }

    @Test
    fun `parar remove e retomar reaplica a ultima area desejada`() {
        val m = MaquinaDoSeletor()
        val janela = JanelaFalsa()
        val fila = Fila()
        m.conectar(janela, fila.postar)
        fila.rodar()
        m.aplicar(null, 1, 2, 30, 40, true)
        fila.rodar()
        m.suspender()
        fila.rodar()
        assertFalse(m.estaAnexada())
        m.retomar()
        fila.rodar()
        assertEquals(listOf("anexar 1,2 30x40", "remover", "anexar 1,2 30x40"), janela.eventos)
    }

    @Test
    fun `retomar sem desejo da pagina nao anexa`() {
        val m = MaquinaDoSeletor()
        val janela = JanelaFalsa()
        val fila = Fila()
        m.conectar(janela, fila.postar)
        m.aplicar(null, 0, 0, 0, 0, false)
        m.suspender()
        m.retomar()
        fila.rodar()
        assertTrue(janela.eventos.isEmpty())
    }

    @Test
    fun `pagina que reporta com a tela parada nao anexa ate retomar`() {
        val m = MaquinaDoSeletor()
        val janela = JanelaFalsa()
        val fila = Fila()
        m.conectar(janela, fila.postar)
        m.suspender()
        m.aplicar(null, 1, 2, 30, 40, true)
        fila.rodar()
        assertTrue(janela.eventos.isEmpty())
        m.retomar()
        fila.rodar()
        assertEquals(listOf("anexar 1,2 30x40"), janela.eventos)
    }

    @Test
    fun `desconectar remove a janela corrente e ignora a antiga`() {
        val m = MaquinaDoSeletor()
        val velha = JanelaFalsa()
        val nova = JanelaFalsa()
        val fila = Fila()
        m.conectar(velha, fila.postar)
        fila.rodar()
        m.aplicar(null, 1, 2, 30, 40, true)
        fila.rodar()
        m.conectar(nova, fila.postar)
        m.desconectar(velha)
        assertEquals("a antiga remove a própria janela", listOf("anexar 1,2 30x40", "remover"), velha.eventos)
        fila.rodar()
        assertEquals(listOf("anexar 1,2 30x40"), nova.eventos)
        m.desconectar(nova)
        assertEquals(listOf("anexar 1,2 30x40", "remover"), nova.eventos)
        m.aplicar(null, 9, 9, 99, 99, true)
        fila.rodar()
        assertEquals(2, nova.eventos.size)
    }

    @Test
    fun `host e guardado por referencia fraca e nulo some`() {
        val m = MaquinaDoSeletor()
        m.aplicar(HostFalso(), 0, 0, 0, 0, false)
        m.aplicar(null, 0, 0, 0, 0, false)
        assertNull(m.anfitriao())
    }

    // --- Fachada da ponte ---------------------------------------------------------------------

    @Test
    fun `aplicarEmCss converte e aplica`() {
        val m = MaquinaDoSeletor()
        val janela = JanelaFalsa()
        val fila = Fila()
        m.conectar(janela, fila.postar)
        fila.rodar()
        val seletor = SeletorDeAreas(m)
        seletor.aplicarEmCss(null, 100f, 20f, 140f, 56f, true, 2f, 10, 30)
        fila.rodar()
        assertEquals(listOf("anexar 210,70 280x112"), janela.eventos)
        seletor.aoParar()
        fila.rodar()
        seletor.aoRetomar()
        fila.rodar()
        seletor.aoDestruir(null)
        fila.rodar()
        assertEquals(
            listOf("anexar 210,70 280x112", "remover", "anexar 210,70 280x112", "remover"),
            janela.eventos,
        )
        // depois de destruir a área foi esquecida: retomar não reaplica
        seletor.aoRetomar()
        fila.rodar()
        assertEquals(4, janela.eventos.size)
    }

    @Test
    fun `parametros da janela de sobreposicao`() {
        assertEquals(2032, JanelaDeSobreposicao.TIPO_SOBREPOSICAO_DE_ACESSIBILIDADE)
        assertEquals(0x01000328, JanelaDeSobreposicao.FLAGS_DA_JANELA)
        assertEquals(0x08 or 0x20 or 0x100 or 0x200 or 0x01000000, JanelaDeSobreposicao.FLAGS_DA_JANELA)
        assertEquals(-3, JanelaDeSobreposicao.FORMATO_TRANSLUCIDO)
    }
}
