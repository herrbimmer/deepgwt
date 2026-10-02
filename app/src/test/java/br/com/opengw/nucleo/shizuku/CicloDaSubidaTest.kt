package br.com.opengw.nucleo.shizuku

import br.com.opengw.app.PartidaDoShizuku.Estado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Casos A–F de `docs/independencia/specs/subida-do-shizuku.md`, com relógio e Shizuku falsos. */
class CicloDaSubidaTest {

    private val agenda = AgendaFalsa()
    private val shizuku = ShizukuFalso(agenda)
    private val iniciador = IniciadorFalso()
    private val instalacao = InstalacaoFalsa()
    private val registro = RegistroFalso()
    private val ouvinte = OuvinteFalso()
    private val ciclo = CicloDaSubida(agenda, shizuku, instalacao, registro, { iniciador })

    /** Cada start entrega o binder ~0,7 s depois (V3). */
    private fun startEntregaOBinder() {
        iniciador.aoIniciar = {
            shizuku.binderChegaEm(700)
            Disparo(disparado = true, apk = IniciadorFalso.APK)
        }
    }

    private fun partirEAceitar() {
        startEntregaOBinder()
        ciclo.iniciar(ouvinte)
        agenda.avancar(2_000)
        assertEquals(Estado.ACEITO, ciclo.estado)
    }

    // ── A ──

    @Test
    fun `partida fria - sobe, espera o binder e aceita uma vez`() {
        startEntregaOBinder()

        ciclo.iniciar(ouvinte)
        assertEquals(Estado.SUBINDO, ciclo.estado)
        agenda.avancar(20_000)

        assertEquals(Estado.ACEITO, ciclo.estado)
        assertEquals(listOf("aceito"), ouvinte.avisos)
        assertEquals(1, iniciador.inicios)
        assertEquals(listOf(instalacao.pacote), iniciador.pacotes)
        assertEquals(listOf(false), iniciador.pelaRede)
        assertEquals(
            listOf("tentativa", "desfecho:BINDER_DE_PE:${IniciadorFalso.APK}:null", "binderRecebido"),
            registro.eventos,
        )
        // Sessão fechada depois do desfecho; ADB por TCP depois do aceite (§G).
        assertEquals(listOf("iniciar", "terminar", "adb"), iniciador.chamadas)
        assertFalse(shizuku.ouvindoRecebido)
        assertTrue(shizuku.ouvindoMorte)
    }

    @Test
    fun `binder ja de pe pula a subida (B1)`() {
        shizuku.binderDesde = 0

        ciclo.iniciar(ouvinte)
        agenda.avancar(0)

        assertEquals(Estado.ACEITO, ciclo.estado)
        assertEquals(listOf("aceito"), ouvinte.avisos)
        assertEquals(0, iniciador.inicios)
        assertEquals(0, registro.contar("tentativa"))
    }

    @Test
    fun `segundo iniciar no mesmo ciclo e ignorado (A1)`() {
        startEntregaOBinder()
        val outro = OuvinteFalso()

        ciclo.iniciar(ouvinte)
        ciclo.iniciar(outro)
        agenda.avancar(20_000)
        ciclo.iniciar(outro)
        agenda.avancar(20_000)

        assertEquals(1, iniciador.inicios)
        assertEquals(listOf("aceito"), ouvinte.avisos)
        assertTrue(outro.avisos.isEmpty())
    }

    @Test
    fun `UID acima do teto sem bypass desiste, avisa e nao sobe nada (A2, D1)`() {
        instalacao.uidDoApp = 1_010_123
        instalacao.integridade = true

        ciclo.iniciar(ouvinte)
        agenda.avancar(60_000)

        assertEquals(listOf("desistir:uid"), ouvinte.avisos)
        assertEquals(Estado.PARADO, ciclo.estado)
        assertEquals(listOf(false), instalacao.gravacoes)
        assertEquals(1, instalacao.avisos)
        assertEquals(RegrasDaSubida.EVENTO_UID_BLOQUEADO, registro.diario.single().first)
        assertEquals(0, iniciador.inicios)
        assertEquals(0, agenda.pendentes)
        assertFalse(shizuku.ouvindoRecebido)
    }

    @Test
    fun `UID acima do teto com bypass registra e segue`() {
        instalacao.uidDoApp = 20_000
        instalacao.bypass = true
        startEntregaOBinder()

        ciclo.iniciar(ouvinte)
        agenda.avancar(20_000)

        assertEquals(listOf(false), instalacao.gravacoes)
        assertEquals(RegrasDaSubida.EVENTO_UID_BLOQUEADO, registro.diario.single().first)
        assertEquals(0, instalacao.avisos)
        assertEquals(listOf("aceito"), ouvinte.avisos)
    }

    @Test
    fun `UID na faixa grava integridade verdadeira so uma vez`() {
        startEntregaOBinder()
        ciclo.iniciar(ouvinte)
        agenda.avancar(20_000)
        ciclo.encerrar()
        ciclo.iniciar(ouvinte)
        agenda.avancar(20_000)

        assertEquals(listOf(true), instalacao.gravacoes)
        assertTrue(registro.diario.isEmpty())
    }

    // ── B ──

    @Test
    fun `servidor que nunca aparece e servidor morto - re-tenta em 1 s (B8, B9)`() {
        iniciador.pid = { false }

        ciclo.iniciar(ouvinte)
        agenda.avancar(12_500)
        assertEquals(1, iniciador.inicios)
        assertTrue(registro.eventos.contains("desfecho:SERVIDOR_MORTO:${IniciadorFalso.APK}:fatal: can't get path of manager"))
        assertTrue(registro.eventos.any { it.startsWith("erro:") })

        agenda.avancar(600) // 13,1 s: a re-tentativa de ~1 s já foi
        assertEquals(2, iniciador.inicios)
    }

    @Test
    fun `pid que aparece e some e servidor morto antes do prazo`() {
        iniciador.pid = { n -> n == 1 } // vivo aos 2 s, sumido aos 4 s

        ciclo.iniciar(ouvinte)
        agenda.avancar(4_500)
        assertTrue(registro.eventos.any { it.startsWith("desfecho:SERVIDOR_MORTO") })
        agenda.avancar(600)

        assertEquals(2, iniciador.inicios)
    }

    @Test
    fun `servidor vivo sem binder nao e derrubado - so a escada re-sobe (D5)`() {
        iniciador.pid = { true }

        ciclo.iniciar(ouvinte)
        agenda.avancar(26_000) // desfecho aos 12 s; janela nova de 15 s até 27 s

        assertEquals(1, iniciador.inicios)
        assertTrue(registro.eventos.contains("desfecho:VIVO_SEM_BINDER:${IniciadorFalso.APK}:fatal: can't get path of manager"))
        assertEquals(0, registro.contar("resubida"))

        agenda.avancar(2_000)
        assertEquals(listOf("timeouts:1", "resubida:1"), registro.eventos.filter { it.startsWith("timeouts") || it.startsWith("resubida") })
        assertEquals(2, iniciador.inicios)
    }

    @Test
    fun `exceção do iniciador e falha do procedimento e a sessão é fechada`() {
        iniciador.aoIniciar = { n ->
            if (n == 1) throw IllegalStateException("telnet caiu")
            shizuku.binderChegaEm(700)
            Disparo(disparado = true, apk = IniciadorFalso.APK)
        }

        ciclo.iniciar(ouvinte)
        agenda.avancar(5_000)

        assertTrue(registro.eventos.contains("erro:exceção na subida: telnet caiu"))
        assertEquals(listOf("iniciar", "terminar", "iniciar", "terminar", "adb"), iniciador.chamadas)
        assertEquals(listOf("aceito"), ouvinte.avisos)
    }

    @Test
    fun `conexão de telnet é registrada`() {
        iniciador.aoIniciar = {
            shizuku.binderChegaEm(700)
            Disparo(true, IniciadorFalso.APK, telnet = ConexaoDoTelnet("127.0.0.1", listOf("127.0.0.1"), 12))
        }

        ciclo.iniciar(ouvinte)
        agenda.avancar(2_000)

        assertEquals(listOf("tentativa", "telnet:127.0.0.1"), registro.eventos.take(2))
    }

    // ── C ──

    @Test
    fun `escada - timeouts 1 e 2 re-sobem, o 3 pede reinicio uma vez (C2, V5)`() {
        iniciador.pid = { true }

        ciclo.iniciar(ouvinte)
        agenda.avancar(300_000)

        assertEquals(listOf("reinicio:${RegrasDaSubida.MOTIVO_TIMEOUT}"), ouvinte.avisos)
        assertEquals(3, iniciador.inicios)
        assertEquals(
            listOf("timeouts:1", "resubida:1", "timeouts:2", "resubida:2", "timeouts:3"),
            registro.eventos.filter { it.startsWith("timeouts") || it.startsWith("resubida") },
        )
        assertFalse(shizuku.ouvindoRecebido)
    }

    @Test
    fun `falhas seguidas re-tentam a cada segundo e a escada ainda decide`() {
        iniciador.aoIniciar = { Disparo(disparado = false, erro = "nenhum host de telnet") }

        ciclo.iniciar(ouvinte)
        agenda.avancar(10_500)
        assertEquals(11, iniciador.inicios) // 0 s, 1 s, ... 10 s
        assertTrue(ouvinte.avisos.isEmpty())

        agenda.avancar(200_000)
        assertEquals(listOf("reinicio:${RegrasDaSubida.MOTIVO_TIMEOUT}"), ouvinte.avisos)
        val depois = iniciador.inicios
        agenda.avancar(100_000)
        assertEquals(depois, iniciador.inicios)
        assertTrue(depois in 44..48) // ~45 s de re-tentativas até o 3º timeout
    }

    @Test
    fun `binder entregue pelo aviso durante a espera aceita (C1, D1)`() {
        iniciador.pid = { true }
        ciclo.iniciar(ouvinte)
        agenda.avancar(13_000)
        assertTrue(ouvinte.avisos.isEmpty())

        shizuku.binderChegaEm(0)
        agenda.avancar(0)

        assertEquals(listOf("aceito"), ouvinte.avisos)
        agenda.avancar(100_000)
        assertEquals(1, iniciador.inicios)
        assertEquals(0, registro.contar("timeouts"))
    }

    // ── D ──

    @Test
    fun `segundo binder recebido na mesma partida e ignorado (D2)`() {
        iniciador.pid = { true }
        ciclo.iniciar(ouvinte)
        agenda.avancar(13_000)
        val aviso = shizuku.aoReceber!!
        shizuku.binderDesde = agenda.agora

        aviso()
        aviso()

        assertEquals(listOf("aceito"), ouvinte.avisos)
        assertEquals(1, registro.contar("binderRecebido"))
    }

    @Test
    fun `permissao ausente e pedida - concedida aceita`() {
        shizuku.permissao = false
        startEntregaOBinder()
        ciclo.iniciar(ouvinte)
        agenda.avancar(2_000)

        assertEquals(1, shizuku.pedidosDePermissao)
        assertEquals(Estado.SUBINDO, ciclo.estado)
        assertTrue(ouvinte.avisos.isEmpty())

        shizuku.responderPermissao(true)
        agenda.avancar(0)
        assertEquals(Estado.ACEITO, ciclo.estado)
        assertEquals(listOf("aceito"), ouvinte.avisos)
    }

    @Test
    fun `permissao negada registra e nao aceita nem reinicia (D6)`() {
        shizuku.permissao = false
        startEntregaOBinder()
        ciclo.iniciar(ouvinte)
        agenda.avancar(2_000)

        shizuku.responderPermissao(false)
        agenda.avancar(100_000)

        assertTrue(ouvinte.avisos.isEmpty())
        assertEquals(Estado.SUBINDO, ciclo.estado)
        assertTrue(registro.eventos.contains("erro:permissão do Shizuku negada"))
        assertEquals(1, iniciador.inicios)
    }

    // ── E ──

    @Test
    fun `partida morna com binder morto re-sobe sem novo aceito (E1, D10)`() {
        partirEAceitar()
        shizuku.binderDesde = null // o servidor morreu no ciclo de energia; o aviso não chegou

        ciclo.conferir()
        assertEquals(Estado.SUBINDO, ciclo.estado)
        assertFalse(shizuku.ouvindoMorte)
        assertEquals(RegrasDaSubida.EVENTO_BINDER_MORTO_NA_PARTIDA_MORNA, registro.diario.single().first)
        assertEquals(1, registro.contar("binderMortoNaPartidaMorna"))

        agenda.avancar(2_000)
        assertEquals(2, iniciador.inicios)
        assertEquals(Estado.ACEITO, ciclo.estado)
        assertEquals(listOf("aceito"), ouvinte.avisos)
        assertTrue(shizuku.ouvindoMorte)
    }

    @Test
    fun `conferir nao faz nada com binder vivo, subindo ou parado (E2)`() {
        ciclo.conferir() // PARADO
        agenda.avancar(1_000)
        assertEquals(Estado.PARADO, ciclo.estado)

        iniciador.pid = { true }
        ciclo.iniciar(ouvinte)
        agenda.avancar(1_000)
        ciclo.conferir() // SUBINDO
        agenda.avancar(13_000)
        assertEquals(1, iniciador.inicios)

        shizuku.binderChegaEm(0)
        agenda.avancar(0)
        assertEquals(Estado.ACEITO, ciclo.estado)
        ciclo.conferir() // ACEITO com binder vivo
        agenda.avancar(50_000)

        assertEquals(1, iniciador.inicios)
        assertTrue(registro.diario.isEmpty())
        assertEquals(0, registro.contar("binderMortoNaPartidaMorna"))
    }

    // ── F ──

    @Test
    fun `binder morto depois do aceite pede reinicio uma vez (F1)`() {
        partirEAceitar()

        shizuku.binderMorre()
        agenda.avancar(0)
        ciclo.conferir() // o pedido de reinício já foi: não sobe por cima
        agenda.avancar(100_000)

        assertEquals(listOf("aceito", "reinicio:${RegrasDaSubida.MOTIVO_BINDER_MORTO}"), ouvinte.avisos)
        assertFalse(shizuku.ouvindoMorte)
        assertFalse(shizuku.ouvindoRecebido)
        assertEquals(1, iniciador.inicios)
    }

    @Test
    fun `encerrar cancela tudo e nao toca no servidor (F2)`() {
        iniciador.pid = { true }
        ciclo.iniciar(ouvinte)
        agenda.avancar(13_000)
        val chamadasAntes = iniciador.chamadas.toList()

        ciclo.encerrar()

        assertEquals(Estado.PARADO, ciclo.estado)
        assertEquals(0, agenda.pendentes)
        assertFalse(shizuku.ouvindoRecebido)
        agenda.avancar(300_000)
        assertEquals(chamadasAntes, iniciador.chamadas)
        assertTrue(ouvinte.avisos.isEmpty())
    }

    @Test
    fun `encerrar no meio do desfecho para a espera e fecha a sessao`() {
        iniciador.pid = {
            ciclo.encerrar() // o serviço é destruído de outra thread enquanto se espera o binder
            true
        }
        ciclo.iniciar(ouvinte)
        agenda.avancar(300_000)

        assertEquals(1, iniciador.inicios)
        assertEquals("terminar", iniciador.chamadas.last())
        assertEquals(0, registro.contar("desfecho"))
        assertTrue(ouvinte.avisos.isEmpty())
        assertEquals(Estado.PARADO, ciclo.estado)
    }

    @Test
    fun `depois de encerrar um novo iniciar comeca outro ciclo`() {
        partirEAceitar()
        ciclo.encerrar()
        val outro = OuvinteFalso()
        shizuku.binderDesde = 0

        ciclo.iniciar(outro)
        agenda.avancar(0)

        assertEquals(listOf("aceito"), outro.avisos)
        assertEquals(listOf("aceito"), ouvinte.avisos)
    }

    @Test
    fun `binder morto esperando a permissao volta a esperar o binder sem pedir reinicio`() {
        shizuku.permissao = false
        startEntregaOBinder()
        ciclo.iniciar(ouvinte)
        agenda.avancar(2_000)
        shizuku.binderMorre()
        agenda.avancar(0)

        assertTrue(ouvinte.avisos.isEmpty())
        assertFalse(shizuku.esperandoPermissao)
        assertTrue(shizuku.ouvindoRecebido)
        assertEquals(Estado.SUBINDO, ciclo.estado)
    }
}
