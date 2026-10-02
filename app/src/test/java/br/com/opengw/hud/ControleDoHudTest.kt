package br.com.opengw.hud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Alpha 9.5: segurar OK entrega as setas ao HUD; segurar de novo devolve ao painel. */
class ControleDoHudTest {

    private val G = EstadoDoControleDoHud.GUARDA_DO_OK_LONGO_MS

    @Test
    fun `fora do controle as setas sao do painel`() {
        val e = EstadoDoControleDoHud()
        val (efeito, consumida) = e.aoApertar(TeclaDoControle.CIMA, 0, podeEntrar = true)
        assertEquals(EfeitoDoControle.Nenhum, efeito)
        assertFalse(consumida)
        assertFalse(e.aoApertar(TeclaDoControle.OK, 10, podeEntrar = true).second)
    }

    @Test
    fun `segurar OK entra e as setas passam a trocar a pagina do HUD`() {
        val e = EstadoDoControleDoHud()
        assertEquals(EfeitoDoControle.Entrou to true, e.aoApertar(TeclaDoControle.OK_LONGO, 0, true))
        assertTrue(e.ativo)
        assertEquals(EfeitoDoControle.Andou(-1) to true, e.aoApertar(TeclaDoControle.CIMA, 100, true))
        assertEquals(EfeitoDoControle.Andou(1) to true, e.aoApertar(TeclaDoControle.BAIXO, 200, true))
        // OK curto sob a cortina é engolido: apertaria algo no painel sem ninguém ver.
        assertEquals(EfeitoDoControle.Nenhum to true, e.aoApertar(TeclaDoControle.OK, 300, true))
    }

    @Test
    fun `segurar OK de novo sai, mas a repeticao do mesmo gesto nao`() {
        val e = EstadoDoControleDoHud()
        e.aoApertar(TeclaDoControle.OK_LONGO, 0, true)
        // O inputservice pode repetir o keycode com a tecla segura.
        assertEquals(EfeitoDoControle.Nenhum to true, e.aoApertar(TeclaDoControle.OK_LONGO, G - 1, true))
        assertTrue(e.ativo)
        val (efeito, consumida) = e.aoApertar(TeclaDoControle.OK_LONGO, G + 10, true)
        assertTrue(efeito is EfeitoDoControle.Saiu)
        assertTrue(consumida)
        assertFalse(e.ativo)
        // O "soltar" logo depois não aperta OK no painel, e a repetição não religa nem vira mãos-livres.
        assertTrue(e.aoApertar(TeclaDoControle.OK, G + 200, true).second)
        assertEquals(EfeitoDoControle.Nenhum to true, e.aoApertar(TeclaDoControle.OK_LONGO, G + 300, true))
        assertFalse(e.ativo)
        // Passada a guarda, o OK curto volta a ser do painel.
        assertFalse(e.aoApertar(TeclaDoControle.OK, G + 10 + EstadoDoControleDoHud.GUARDA_DO_OK_MS + 1, true).second)
    }

    @Test
    fun `sem poder entrar o OK segurado segue para o resto do app`() {
        // HUD fora do ar, controle desligado em Telas ou página Central do círculo.
        val e = EstadoDoControleDoHud()
        assertEquals(EfeitoDoControle.Nenhum to false, e.aoApertar(TeclaDoControle.OK_LONGO, 0, podeEntrar = false))
        assertFalse(e.ativo)
    }

    @Test
    fun `voltar sai e continua ate o carro`() {
        val e = EstadoDoControleDoHud()
        e.aoApertar(TeclaDoControle.OK_LONGO, 0, true)
        val (efeito, consumida) = e.aoApertar(TeclaDoControle.VOLTAR, 50, true)
        assertTrue(efeito is EfeitoDoControle.Saiu)
        assertFalse(consumida)
    }

    @Test
    fun `sem tecla por vinte segundos o painel volta`() {
        val e = EstadoDoControleDoHud()
        e.aoApertar(TeclaDoControle.OK_LONGO, 0, true)
        e.aoApertar(TeclaDoControle.BAIXO, 5_000, true)
        assertEquals(EfeitoDoControle.Nenhum, e.expirar(5_000 + EstadoDoControleDoHud.TEMPO_SEM_TECLA_MS - 1))
        assertTrue(e.expirar(5_000 + EstadoDoControleDoHud.TEMPO_SEM_TECLA_MS) is EfeitoDoControle.Saiu)
        assertFalse(e.ativo)
    }

    @Test
    fun `aviso do carro encerra por fora`() {
        val e = EstadoDoControleDoHud()
        assertEquals(EfeitoDoControle.Nenhum, e.encerrar("aviso do carro", 0))
        e.aoApertar(TeclaDoControle.OK_LONGO, 0, true)
        assertEquals(EfeitoDoControle.Saiu("aviso do carro"), e.encerrar("aviso do carro", 10))
    }

    @Test
    fun `teclas do volante viram teclas do controle`() {
        assertEquals(TeclaDoControle.OK_LONGO, HudWheelInput.teclaDoControle(1037))
        assertEquals(TeclaDoControle.OK, HudWheelInput.teclaDoControle(1028))
        assertEquals(TeclaDoControle.VOLTAR, HudWheelInput.teclaDoControle(1030))
        assertEquals(TeclaDoControle.CIMA, HudWheelInput.teclaDoControle(1024))
        assertEquals(TeclaDoControle.BAIXO, HudWheelInput.teclaDoControle(1025))
        // ←/→ são do carro, que troca de card com elas.
        assertEquals(TeclaDoControle.OUTRA, HudWheelInput.teclaDoControle(1026))
        assertEquals(TeclaDoControle.OUTRA, HudWheelInput.teclaDoControle(1027))
    }
}
