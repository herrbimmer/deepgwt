// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.rede

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Casos 14 a 22 da especificação: o gestor do roteador (lado do app). */
class GestorDoRoteadorTest {

    private val script = "#!/system/bin/sh\necho roteador\n".toByteArray()
    private val sha = sha256Hex(script)

    private val armazem = ArmazemFalso()
    private val registro = RegistroFalso()
    private val relogio = RelogioFalso(agoraMs = 10_000_000, monotonicoMs = 500_000)
    private val agendador = AgendadorFalso()

    // O mundo do aparelho, como o shell falso o enxerga.
    private var statusDoDaemon = "DEAD"
    private var versaoGravada = ""
    private var scriptExiste = true
    private var instalacaoConfere = true
    private var scriptLegivel: ByteArray? = script
    private var saidaDoStop = "STOPPED"

    private val shell = ShellFalso { comando ->
        when {
            "base64 -d" in comando -> if (instalacaoConfere) "$sha  /data/local/tmp/hotrouter.sh.tmp" else "0000  x"
            "mv -f" in comando -> if (instalacaoConfere) "$sha  /data/local/tmp/hotrouter.sh" else "0000  x"
            "[ -f /data/local/tmp/hotrouter.sh ]" in comando -> if (scriptExiste) MARCADOR_OK else ""
            "hotrouter.sh status" in comando -> statusDoDaemon
            "hotrouter.version" in comando && comando.contains("cat ") -> versaoGravada
            comando.endsWith("hotrouter.sh stop") -> saidaDoStop
            else -> ""
        }
    }

    private val gestor = GestorDoRoteador(armazem, shell, registro, relogio, agendador) { scriptLegivel }

    private fun lancamentos() = shell.comandos.filter { "setsid" in it }
    private fun instalacoes() = shell.comandos.filter { "base64 -d" in it }

    // ---- ligar (caso 16) e instalar (caso 14) ----

    @Test
    fun `ligar grava a preferencia na hora, e o trabalho vai para o fundo`() {
        gestor.definirLigado(true)
        assertEquals(true, armazem.valores[ChavesDaRede.ROTEADOR_LIGADO])
        assertTrue(shell.comandos.isEmpty())
        agendador.rodarFila()
        assertEquals(1, instalacoes().size)
        assertEquals(1, lancamentos().size)
    }

    @Test
    fun `instalacao por temporario, base64, sha256, chmod e depois mover e conferir`() {
        gestor.definirLigado(true)
        agendador.rodarFila()
        val gravacao = shell.comandos[0]
        assertTrue("hotrouter.sh.tmp" in gravacao)
        assertTrue("base64 -d > /data/local/tmp/hotrouter.sh.tmp" in gravacao)
        assertTrue("chmod 755 /data/local/tmp/hotrouter.sh.tmp" in gravacao)
        assertTrue("sha256sum /data/local/tmp/hotrouter.sh.tmp" in gravacao)
        val b64 = java.util.Base64.getEncoder().encodeToString(script)
        assertTrue("'$b64'" in gravacao)
        val mover = shell.comandos[1]
        assertTrue("mv -f /data/local/tmp/hotrouter.sh.tmp /data/local/tmp/hotrouter.sh" in mover)
        assertTrue("sha256sum /data/local/tmp/hotrouter.sh" in mover)
    }

    @Test
    fun `divergencia na instalacao apaga o temporario, registra erro e nao inicia nada`() {
        instalacaoConfere = false
        gestor.definirLigado(true)
        agendador.rodarFila()
        assertTrue(lancamentos().isEmpty())
        assertTrue(shell.comandos.any { it == "rm -f /data/local/tmp/hotrouter.sh.tmp" })
        assertTrue(registro.erros.isNotEmpty())
        // o vigia continua armado para tentar de novo
        assertEquals(1, agendador.agendadas.size)
    }

    @Test
    fun `script grande demais nao e instalado`() {
        scriptLegivel = ByteArray(InstaladorDeArquivo.LIMITE_EM_BYTES + 1)
        gestor.definirLigado(true)
        agendador.rodarFila()
        assertTrue(shell.comandos.isEmpty())
        assertTrue(registro.erros.isNotEmpty())
    }

    @Test
    fun `asset ilegivel registra erro`() {
        scriptLegivel = null
        gestor.definirLigado(true)
        agendador.rodarFila()
        assertTrue(shell.comandos.isEmpty())
        assertTrue(registro.erros.isNotEmpty())
    }

    // ---- lançar (caso 15) ----

    @Test
    fun `o lancamento e em segundo plano com saida e entrada redirecionadas e grava a versao`() {
        gestor.definirLigado(true)
        agendador.rodarFila()
        val lancar = lancamentos().single()
        assertTrue(lancar.startsWith("sh -c "))
        assertTrue("setsid sh \$d/hotrouter.sh start > \$d/hotrouter.out 2>&1 < /dev/null &" in lancar)
        assertTrue("d=/data/local/tmp" in lancar)
        assertTrue("printf %s '$sha' > \$d/hotrouter.version" in lancar)
        assertTrue("o sh -c não pode conter o caminho completo seguido de start", "/data/local/tmp/hotrouter.sh start" !in lancar)
    }

    // ---- vigia (caso 19) ----

    @Test
    fun `ligar arma um vigia de 60 segundos`() {
        gestor.definirLigado(true)
        agendador.rodarFila()
        assertEquals(listOf(60_000L), agendador.agendadas.map { it.first })
    }

    @Test
    fun `no maximo um vigia armado`() {
        gestor.definirLigado(true)
        agendador.rodarFila()
        gestor.aoFicarPronto()
        agendador.rodarFila()
        gestor.aoFicarPronto()
        agendador.rodarFila()
        assertEquals(1, agendador.agendadas.size)
    }

    @Test
    fun `vigia com daemon vivo e versao certa nao faz nada alem de reagendar`() {
        armazem.valores[ChavesDaRede.ROTEADOR_LIGADO] = true
        statusDoDaemon = "ALIVE|1234"
        versaoGravada = sha
        gestor.aoFicarPronto()
        agendador.rodarFila()
        shell.comandos.clear()
        assertTrue(agendador.dispararAgendada())
        assertTrue(lancamentos().isEmpty())
        assertTrue(instalacoes().isEmpty())
        assertEquals(1, agendador.agendadas.size)
    }

    @Test
    fun `vigia com daemon morto renova a graca, instala e lanca`() {
        armazem.valores[ChavesDaRede.ROTEADOR_LIGADO] = true
        gestor.aoFicarPronto()
        statusDoDaemon = "DEAD"
        agendador.rodarFila()
        shell.comandos.clear()
        relogio.monotonicoMs += 100_000 // a graça antiga já venceu
        assertEquals(ModoDoRoteador.ERRO, gestor.lerSituacao().modo)
        agendador.dispararAgendada()
        assertEquals(1, instalacoes().size)
        assertEquals(1, lancamentos().size)
        assertEquals(ModoDoRoteador.INICIANDO, gestor.lerSituacao().modo) // graça renovada
        assertEquals(1, agendador.agendadas.size)
    }

    @Test
    fun `vigia com preferencia desligada se encerra e nao reagenda`() {
        armazem.valores[ChavesDaRede.ROTEADOR_LIGADO] = true
        gestor.aoFicarPronto()
        agendador.rodarFila()
        armazem.valores[ChavesDaRede.ROTEADOR_LIGADO] = false
        shell.comandos.clear()
        agendador.dispararAgendada()
        assertTrue(shell.comandos.isEmpty())
        assertTrue(agendador.agendadas.isEmpty())
    }

    @Test
    fun `excecao no vigia e registrada e ele continua`() {
        var explodir = false
        val instavel = ShellFalso { comando ->
            if (explodir) throw IllegalStateException("shell")
            if ("hotrouter.sh status" in comando) "ALIVE|1234" else ""
        }
        val g = GestorDoRoteador(armazem, instavel, registro, relogio, agendador) { script }
        armazem.valores[ChavesDaRede.ROTEADOR_LIGADO] = true
        g.aoFicarPronto()
        agendador.rodarFila()
        explodir = true
        assertTrue(agendador.dispararAgendada())
        assertTrue(registro.erros.any { "vigia" in it })
        assertEquals(1, agendador.agendadas.size)
    }

    // ---- servico pronto (caso 19) e versão (caso 20) ----

    @Test
    fun `servico pronto com preferencia desligada nao faz nada`() {
        gestor.aoFicarPronto()
        assertTrue(agendador.fila.isEmpty())
        assertTrue(agendador.agendadas.isEmpty())
        assertTrue(shell.comandos.isEmpty())
    }

    @Test
    fun `servico pronto instala, le o status e lanca se nao esta vivo`() {
        armazem.valores[ChavesDaRede.ROTEADOR_LIGADO] = true
        statusDoDaemon = "DEAD"
        gestor.aoFicarPronto()
        agendador.rodarFila()
        assertEquals(1, instalacoes().size)
        assertEquals(1, lancamentos().size)
        assertEquals(1, agendador.agendadas.size)
    }

    @Test
    fun `D6 daemon vivo de outra versao e relancado`() {
        armazem.valores[ChavesDaRede.ROTEADOR_LIGADO] = true
        statusDoDaemon = "ALIVE|1234"
        versaoGravada = "sha-de-uma-versao-antiga"
        gestor.aoFicarPronto()
        agendador.rodarFila()
        assertEquals(1, lancamentos().size)
    }

    @Test
    fun `daemon vivo da versao certa nao e relancado`() {
        armazem.valores[ChavesDaRede.ROTEADOR_LIGADO] = true
        statusDoDaemon = "ALIVE|1234"
        versaoGravada = sha
        gestor.aoFicarPronto()
        agendador.rodarFila()
        assertTrue(lancamentos().isEmpty())
    }

    @Test
    fun `D6 no vigia tambem`() {
        armazem.valores[ChavesDaRede.ROTEADOR_LIGADO] = true
        statusDoDaemon = "ALIVE|1234"
        versaoGravada = sha
        gestor.aoFicarPronto()
        agendador.rodarFila()
        versaoGravada = "outra"
        agendador.dispararAgendada()
        assertEquals(1, lancamentos().size)
    }

    // ---- desligar (casos 17 e 18) ----

    @Test
    fun `desligar grava, cancela o vigia e manda parar`() {
        gestor.definirLigado(true)
        agendador.rodarFila()
        shell.comandos.clear()
        gestor.definirLigado(false)
        assertEquals(false, armazem.valores[ChavesDaRede.ROTEADOR_LIGADO])
        assertTrue(agendador.agendadas.isEmpty())
        agendador.rodarFila()
        assertTrue(shell.comandos.any { it == "sh /data/local/tmp/hotrouter.sh stop" })
        assertTrue(registro.erros.isEmpty())
    }

    @Test
    fun `D3 desligar manda parar mesmo com a instalacao quebrada`() {
        instalacaoConfere = false
        gestor.definirLigado(false)
        agendador.rodarFila()
        assertTrue(shell.comandos.any { it.endsWith("hotrouter.sh stop") })
        assertTrue(instalacoes().isEmpty()) // o script já estava lá
    }

    @Test
    fun `parar sem script instala o do asset e roda o stop`() {
        scriptExiste = false
        gestor.definirLigado(false)
        agendador.rodarFila()
        assertEquals(1, instalacoes().size)
        assertTrue(shell.comandos.last().endsWith("hotrouter.sh stop"))
    }

    @Test
    fun `parar sem script e sem conseguir instalar registra erro e nao roda stop`() {
        scriptExiste = false
        instalacaoConfere = false
        gestor.definirLigado(false)
        agendador.rodarFila()
        assertTrue(shell.comandos.none { it.endsWith("hotrouter.sh stop") })
        assertTrue(registro.erros.isNotEmpty())
    }

    @Test
    fun `stop que nao diz STOPPED e erro`() {
        saidaDoStop = "STOP_FAILED"
        gestor.definirLigado(false)
        agendador.rodarFila()
        assertTrue(registro.erros.any { "STOP_FAILED" in it })
    }

    @Test
    fun `ligar e desligar em seguida nao deixa o daemon rodando`() {
        gestor.definirLigado(true)
        gestor.definirLigado(false)
        agendador.rodarFila()
        assertTrue(lancamentos().isEmpty())
        assertTrue(shell.comandos.any { it.endsWith("hotrouter.sh stop") })
        assertTrue(agendador.agendadas.isEmpty())
    }

    @Test
    fun `leituras de preferencia nunca bloqueiam`() {
        assertFalse(armazem.lerBoolean(ChavesDaRede.ROTEADOR_LIGADO, false))
        assertNull(armazem.valores[ChavesDaRede.ROTEADOR_LIGADO])
        assertEquals(ModoDoRoteador.DESLIGADO, gestor.lerSituacao().modo)
        assertTrue(shell.comandos.isEmpty())
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
