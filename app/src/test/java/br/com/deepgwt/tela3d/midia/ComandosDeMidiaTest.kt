package br.com.deepgwt.tela3d.midia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Casos C1–C5 (rotas dos comandos de transporte) e a lógica de MediaBridge (caso M). */
class ComandosDeMidiaTest {
    private val anterior = ComandoDeMidia.ANTERIOR
    private val tocar = ComandoDeMidia.TOCAR_OU_PAUSAR
    private val proxima = ComandoDeMidia.PROXIMA

    private fun rotas(c: ComandoDeMidia, v: FonteDaFaixa?, conectado: Boolean) = RoteamentoDeComandos.rotas(c, v, conectado)

    @Test
    fun `c1 anterior com carplay tenta o hid e depois a tecla global`() {
        assertEquals(listOf(RotaDoComando.HID_DO_CARPLAY, RotaDoComando.TECLA_GLOBAL), rotas(anterior, FonteDaFaixa.CARPLAY, true))
        assertEquals(listOf(RotaDoComando.HID_DO_CARPLAY, RotaDoComando.TECLA_GLOBAL), rotas(proxima, FonteDaFaixa.CARPLAY, false))
    }

    @Test
    fun `c1 sem ouvinte conectado vai a tecla global`() {
        assertEquals(listOf(RotaDoComando.TECLA_GLOBAL), rotas(anterior, null, false))
        assertEquals(listOf(RotaDoComando.TECLA_GLOBAL), rotas(anterior, FonteDaFaixa.SESSAO, false))
    }

    @Test
    fun `c1 vencedor que nao e de sessao vai a tecla global mesmo com ouvinte`() {
        assertEquals(listOf(RotaDoComando.TECLA_GLOBAL), rotas(anterior, FonteDaFaixa.CENTRAL, true))
        assertEquals(listOf(RotaDoComando.TECLA_GLOBAL), rotas(proxima, FonteDaFaixa.CENTRAL, true))
    }

    @Test
    fun `c1 ouvinte conectado com sessao ou sem vencedor usa o ouvinte`() {
        assertEquals(listOf(RotaDoComando.OUVINTE), rotas(anterior, FonteDaFaixa.SESSAO, true))
        assertEquals(listOf(RotaDoComando.OUVINTE), rotas(proxima, null, true))
    }

    @Test
    fun `c2 tocar ou pausar com a central tenta o comando oneway antes`() {
        assertEquals(listOf(RotaDoComando.CENTRAL, RotaDoComando.TECLA_GLOBAL), rotas(tocar, FonteDaFaixa.CENTRAL, true))
        assertEquals(listOf(RotaDoComando.HID_DO_CARPLAY, RotaDoComando.TECLA_GLOBAL), rotas(tocar, FonteDaFaixa.CARPLAY, true))
        assertEquals(listOf(RotaDoComando.OUVINTE), rotas(tocar, FonteDaFaixa.SESSAO, true))
    }

    @Test
    fun `teclas de midia`() {
        assertEquals(87, TeclasDeMidia.doComando(proxima))
        assertEquals(88, TeclasDeMidia.doComando(anterior))
        assertEquals(85, TeclasDeMidia.doComando(tocar))
        assertEquals(127, TeclasDeMidia.PAUSAR)
        assertEquals(126, TeclasDeMidia.TOCAR)
    }

    @Test
    fun `usos hid do carplay`() {
        assertEquals(8, UsosHidDoCarPlay.doComando(anterior, true))
        assertEquals(4, UsosHidDoCarPlay.doComando(proxima, false))
        assertEquals(2, UsosHidDoCarPlay.doComando(tocar, true)) // tocando: pausar
        assertEquals(1, UsosHidDoCarPlay.doComando(tocar, false)) // pausado: tocar
    }

    // --- C4 / C5 --------------------------------------------------------------------------------

    @Test
    fun `c4 proxima e anterior respeitam as acoes suportadas`() {
        val todas = 16L or 32L
        assertEquals(AcaoDeSessao.PROXIMA, ComandoDoOuvinte.planejar(proxima, true, 3, todas).acao)
        assertEquals(AcaoDeSessao.ANTERIOR, ComandoDoOuvinte.planejar(anterior, true, 2, todas).acao)
        val semProxima = ComandoDoOuvinte.planejar(proxima, true, 3, 16L)
        assertNull(semProxima.acao)
        assertEquals(87, semProxima.teclaDeFalha)
        val semAnterior = ComandoDoOuvinte.planejar(anterior, true, 3, 32L)
        assertNull(semAnterior.acao)
        assertEquals(88, semAnterior.teclaDeFalha)
    }

    @Test
    fun `c4 sessao em erro ou sem sessao falha para a tecla`() {
        assertNull(ComandoDoOuvinte.planejar(proxima, true, 7, 32L).acao)
        val semSessao = ComandoDoOuvinte.planejar(anterior, false, null, 0)
        assertNull(semSessao.acao)
        assertEquals(88, semSessao.teclaDeFalha)
    }

    @Test
    fun `c4 sem estado o comando e enviado sem checar acoes`() {
        assertEquals(AcaoDeSessao.PROXIMA, ComandoDoOuvinte.planejar(proxima, true, null, 0L).acao)
        assertEquals(AcaoDeSessao.TOCAR, ComandoDoOuvinte.planejar(tocar, true, null, 0L).acao)
    }

    @Test
    fun `c5 tocando pausa e pausado toca`() {
        val p = ComandoDoOuvinte.planejar(tocar, true, 3, 2L or 4L)
        assertEquals(AcaoDeSessao.PAUSAR, p.acao)
        assertEquals(127, p.teclaDeFalha)
        val q = ComandoDoOuvinte.planejar(tocar, true, 2, 2L or 4L)
        assertEquals(AcaoDeSessao.TOCAR, q.acao)
        assertEquals(126, q.teclaDeFalha)
    }

    @Test
    fun `c5 acao nao suportada cai na tecla de pausar ou tocar`() {
        val p = ComandoDoOuvinte.planejar(tocar, true, 3, 4L) // tocando, mas sem bit de pausar
        assertNull(p.acao)
        assertEquals(127, p.teclaDeFalha)
        val q = ComandoDoOuvinte.planejar(tocar, true, 2, 2L) // pausado, mas sem bit de tocar
        assertNull(q.acao)
        assertEquals(126, q.teclaDeFalha)
    }

    // --- MediaBridge (caso M) -------------------------------------------------------------------

    private class ControleFalso : ControleDeMidia {
        val chamadas = ArrayList<String>()
        override fun anterior() { chamadas += "anterior" }
        override fun tocarOuPausar() { chamadas += "tocarOuPausar" }
        override fun proxima() { chamadas += "proxima" }
        override fun iniciar() { chamadas += "iniciar" }
        override fun reenviarAgora() { chamadas += "reenviarAgora" }
    }

    private class Fila : (Runnable) -> Unit {
        val pendentes = ArrayList<Runnable>()
        override fun invoke(r: Runnable) { pendentes += r }
        fun rodarTudo() {
            val copia = ArrayList(pendentes)
            pendentes.clear()
            copia.forEach { it.run() }
        }
    }

    @Test
    fun `m1 os comandos so rodam na thread principal`() {
        val controle = ControleFalso()
        val fila = Fila()
        val ponte = ComandosDaPaginaDeMidia(controle, {}, {}, fila)
        ponte.anterior()
        ponte.tocarOuPausar()
        ponte.proxima()
        assertTrue(controle.chamadas.isEmpty()) // nada rodou ainda: foi postado
        assertEquals(3, fila.pendentes.size)
        fila.rodarTudo()
        assertEquals(listOf("anterior", "tocarOuPausar", "proxima"), controle.chamadas)
    }

    @Test
    fun `m1 pagina pronta inicia reenvia e so depois pede a localizacao`() {
        val controle = ControleFalso()
        val ordem = ArrayList<String>()
        val fila = Fila()
        val ponte = ComandosDaPaginaDeMidia(controle, {}, { ordem += "localizacao:" + controle.chamadas.joinToString(",") }, fila)
        ponte.paginaPronta()
        fila.rodarTudo()
        assertEquals(listOf("iniciar", "reenviarAgora"), controle.chamadas)
        assertEquals(listOf("localizacao:iniciar,reenviarAgora"), ordem)
    }

    @Test
    fun `m1 falha no pedido de localizacao nao quebra`() {
        val fila = Fila()
        val ponte = ComandosDaPaginaDeMidia(ControleFalso(), {}, { throw IllegalStateException("x") }, fila)
        ponte.paginaPronta()
        fila.rodarTudo() // não lança
    }

    @Test
    fun `m2 dispensar slot fecha o app e engole excecoes`() {
        var fechou = 0
        val fila = Fila()
        val ponte = ComandosDaPaginaDeMidia(ControleFalso(), { fechou++ }, {}, fila)
        ponte.dispensarSlot()
        assertEquals(0, fechou)
        fila.rodarTudo()
        assertEquals(1, fechou)
        val quebra = ComandosDaPaginaDeMidia(ControleFalso(), { throw IllegalStateException("x") }, {}, fila)
        quebra.dispensarSlot()
        fila.rodarTudo() // não lança
        assertFalse(fila.pendentes.isNotEmpty())
    }
}
