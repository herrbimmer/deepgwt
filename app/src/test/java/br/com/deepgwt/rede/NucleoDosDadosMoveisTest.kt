// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.rede

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class NucleoDosDadosMoveisTest {

    private class MedidorFalso : MedidorDeUso {
        var sistema: Long? = null
        var rx = -1L
        var tx = -1L
        var lanca = false
        var consultas = 0

        override fun resumoDoSistema(inicioMs: Long, fimMs: Long): Long? {
            consultas++
            if (lanca) throw IllegalStateException("sem serviço")
            return sistema
        }

        override fun bytesRecebidos() = rx
        override fun bytesEnviados() = tx
    }

    private val armazem = ArmazemFalso()
    private val registro = RegistroFalso()
    private val relogio = RelogioFalso(agoraMs = 1791644400000)
    private val medidor = MedidorFalso()
    private val shell = ShellFalso { MARCADOR_OK }
    private var wifi = false
    private var projecao = false
    private val fundo = Executors.newSingleThreadExecutor { Thread(it, "teste-fundo").apply { isDaemon = true } }

    private fun nucleo(
        shell: ExecutorDeShell = this.shell,
        wifiAgora: () -> Boolean = { wifi },
        projecaoAgora: () -> Boolean = { projecao },
    ) = NucleoDosDadosMoveis(
        armazem, shell, registro, relogio, { ZoneId.of("America/Sao_Paulo") }, medidor,
        wifiAgora, projecaoAgora, { "br.com.deepgwt" }, fundo,
    )

    @After
    fun fechar() {
        fundo.shutdownNow()
    }

    /** Espera tudo o que já foi enfileirado no fundo. */
    private fun esperarFundo() {
        val feito = CountDownLatch(1)
        fundo.execute { feito.countDown() }
        assertTrue("o fundo não terminou", feito.await(10, TimeUnit.SECONDS))
    }

    // ---- preferências: chaves e padrões ----

    @Test
    fun `padroes da tabela de preferencias`() {
        val n = nucleo()
        assertFalse(n.controleLigado())
        assertFalse(n.corteManual())
        assertFalse(n.corteSobConsumo())
        assertFalse(n.corteNoWifi())
        assertFalse(n.corteNaProjecao())
        assertEquals(1, n.diaDoCiclo())
        assertEquals(2048, n.tetoDeConsumoMb())
        assertFalse(n.telemetriaBloqueada())
    }

    @Test
    fun `dia do ciclo e limitado a 1 a 31 e o teto tem minimo zero`() {
        val n = nucleo()
        armazem.valores[ChavesDaRede.DIA_DO_CICLO] = 45
        assertEquals(31, n.diaDoCiclo())
        armazem.valores[ChavesDaRede.DIA_DO_CICLO] = -3
        assertEquals(1, n.diaDoCiclo())
        armazem.valores[ChavesDaRede.TETO_DE_CONSUMO_MB] = -10
        assertEquals(0, n.tetoDeConsumoMb())
        armazem.valores[ChavesDaRede.TETO_DE_CONSUMO_MB] = 50_000
        assertEquals(50_000, n.tetoDeConsumoMb())
    }

    @Test
    fun `as chaves gravadas sao as do formato dos carros`() {
        val n = nucleo()
        n.definirCorteManual(true)
        n.definirCorteSobConsumo(true)
        n.definirCorteNoWifi(true)
        n.definirCorteNaProjecao(true)
        n.definirControleLigado(true)
        n.definirTelemetriaBloqueada(true)
        esperarFundo()
        for (chave in listOf(
            "blockCarMobileData", "mobileDataAutoblock", "mobileDataBlockOnWifi",
            "mobileDataBlockOnProjection", "mobileDataControlEnabled", "blockDatatrackTelemetry",
        )) {
            assertEquals(chave, true, armazem.valores[chave])
        }
    }

    // ---- motivo e uso ----

    @Test
    fun `motivo do corte segue a ordem e o wifi com excecao e falso`() {
        val n = nucleo(wifiAgora = { throw IllegalStateException("sem rede") })
        armazem.valores[ChavesDaRede.CONTROLE_LIGADO] = true
        armazem.valores[ChavesDaRede.CORTE_NO_WIFI] = true
        assertNull(n.motivoDoCorte())
        armazem.valores[ChavesDaRede.CORTE_NA_PROJECAO] = true
        projecao = true
        assertEquals(MotivoDoCorte.PROJECAO, n.motivoDoCorte())
    }

    @Test
    fun `uso em MB trunca a divisao inteira`() {
        val n = nucleo()
        medidor.sistema = 3_221_225_472
        assertEquals(3072L, n.usoNoCicloMb(relogio.agoraMs))
        medidor.sistema = 3_221_225_471
        assertEquals(3071L, n.usoNoCicloMb(relogio.agoraMs))
        assertEquals(3_221_225_471L, n.usoNoCicloBytes(relogio.agoraMs))
    }

    @Test
    fun `resumo nulo, soma negativa ou excecao caem no contador de reserva`() {
        val n = nucleo()
        medidor.rx = 1000
        medidor.tx = 0
        medidor.sistema = null
        assertEquals(0L, n.usoNoCicloBytes(relogio.agoraMs)) // só base
        medidor.rx = 1600
        medidor.sistema = -1
        assertEquals(600L, n.usoNoCicloBytes(relogio.agoraMs))
        medidor.rx = 2000
        medidor.lanca = true
        assertEquals(1000L, n.usoNoCicloBytes(relogio.agoraMs))
        assertEquals("2026-10", armazem.lerString(ChavesDaRede.RESERVA_ETIQUETA, ""))
    }

    @Test
    fun `regra de consumo desligada nao consulta o uso`() {
        val n = nucleo()
        armazem.valores[ChavesDaRede.CONTROLE_LIGADO] = true
        medidor.sistema = 9_999_999_999
        assertNull(n.motivoDoCorte())
        assertEquals(0, medidor.consultas)
    }

    // ---- aplicar corte ----

    @Test
    fun `padrao inerte nao roda shell nenhum`() {
        val n = nucleo()
        n.reavaliar()
        n.reaplicarTelemetria()
        esperarFundo()
        assertTrue(shell.comandos.isEmpty())
    }

    @Test
    fun `corte manual corta, grava o estado do app e reafirma a cada avaliacao`() {
        val n = nucleo()
        n.definirControleLigado(true)
        n.definirCorteManual(true)
        esperarFundo()
        val cortes = shell.comandos.filter { "svc data disable" in it }
        assertTrue(cortes.isNotEmpty())
        assertEquals(true, armazem.valores[ChavesDaRede.CORTADO_PELO_APP])
        assertTrue(registro.avisos.any { "cortados" in it && "manual" in it })

        shell.comandos.clear()
        registro.avisos.clear()
        n.reavaliar()
        esperarFundo()
        assertEquals(1, shell.comandos.count { "svc data disable" in it })
        assertTrue("sem aviso quando nada mudou", registro.avisos.isEmpty())
    }

    @Test
    fun `desligar tudo religa so porque o app tinha cortado`() {
        val n = nucleo()
        armazem.valores[ChavesDaRede.CONTROLE_LIGADO] = true
        armazem.valores[ChavesDaRede.CORTE_MANUAL] = true
        n.reavaliar()
        esperarFundo()
        n.definirCorteManual(false)
        esperarFundo()
        assertTrue(shell.comandos.any { "svc data enable" in it })
        assertEquals(false, armazem.valores[ChavesDaRede.CORTADO_PELO_APP])
        assertTrue(registro.avisos.any { "liberados" in it })
    }

    @Test
    fun `nunca religa um 4G que o usuario desligou por fora`() {
        val n = nucleo()
        n.definirControleLigado(true)
        esperarFundo()
        assertTrue(shell.comandos.none { "svc data" in it })
    }

    @Test
    fun `mestre desligado so libera se o app tinha cortado`() {
        val n = nucleo()
        armazem.valores[ChavesDaRede.CORTADO_PELO_APP] = true
        armazem.valores[ChavesDaRede.CORTE_MANUAL] = true // ignorado: mestre desligado
        n.reavaliar()
        esperarFundo()
        assertEquals(1, shell.comandos.count { "svc data enable" in it })
        assertEquals(false, armazem.valores[ChavesDaRede.CORTADO_PELO_APP])
    }

    @Test
    fun `falha do shell nao grava nada e a proxima avaliacao tenta de novo`() {
        val sem = ShellFalso { "" }
        val n = nucleo(shell = sem)
        armazem.valores[ChavesDaRede.CONTROLE_LIGADO] = true
        armazem.valores[ChavesDaRede.CORTE_MANUAL] = true
        n.reavaliar()
        esperarFundo()
        assertNull(armazem.valores[ChavesDaRede.CORTADO_PELO_APP])
        assertEquals(1, sem.comandos.size)
        assertTrue(registro.erros.isNotEmpty())
        n.reavaliar()
        esperarFundo()
        assertEquals(2, sem.comandos.size)
    }

    @Test
    fun `o comando de corte e um unico shell com svc e ate 5 leituras`() {
        val n = nucleo()
        armazem.valores[ChavesDaRede.CONTROLE_LIGADO] = true
        armazem.valores[ChavesDaRede.CORTE_MANUAL] = true
        n.reavaliar()
        esperarFundo()
        val comando = shell.comandos.single()
        assertTrue(comando.startsWith("sh -c svc data disable || exit 1;"))
        assertTrue("settings get global mobile_data" in comando)
        assertTrue("-le 5" in comando)
        assertTrue("sleep 1" in comando)
        assertTrue(MARCADOR_OK in comando)
    }

    // ---- o comando de corte rodando de verdade (sh), com svc e settings falsos ----

    /** `svc` grava o pedido; `settings get` devolve 2 até a leitura [chegaNaLeitura], e então o valor pedido. */
    private fun comandosDeCorte(bin: File, chegaNaLeitura: Int): ExecutorReal {
        val d = bin.absolutePath
        comandoFalso(bin, "svc", "echo \"§2\" > '$d/pedido'; exit 0".replace('§', '$'))
        comandoFalso(
            bin, "settings",
            """
            n=§(cat '$d/n' 2>/dev/null || echo 0); n=§((n+1)); echo §n > '$d/n'
            if [ "§(cat '$d/pedido' 2>/dev/null)" = disable ]; then valor=0; else valor=1; fi
            if [ "§n" -ge $chegaNaLeitura ]; then echo §valor; else echo 2; fi
            """.trimIndent().replace('§', '$'),
        )
        return ExecutorReal(bin)
    }

    @Test
    fun `sh de verdade - o valor chega na terceira leitura`() {
        val bin = pastaTemporaria("corte")
        val real = comandosDeCorte(bin, chegaNaLeitura = 3)
        val n = nucleo(shell = real)
        armazem.valores[ChavesDaRede.CONTROLE_LIGADO] = true
        armazem.valores[ChavesDaRede.CORTE_MANUAL] = true
        n.reavaliar()
        esperarFundo()
        assertEquals(true, armazem.valores[ChavesDaRede.CORTADO_PELO_APP])
        assertEquals("3", File(bin, "n").readText().trim())
    }

    @Test
    fun `sh de verdade - valor que nao chega em 5 leituras falha sem gravar`() {
        val bin = pastaTemporaria("corte")
        val real = comandosDeCorte(bin, chegaNaLeitura = 999)
        val n = nucleo(shell = real)
        armazem.valores[ChavesDaRede.CONTROLE_LIGADO] = true
        armazem.valores[ChavesDaRede.CORTE_MANUAL] = true
        n.reavaliar()
        esperarFundo()
        assertNull(armazem.valores[ChavesDaRede.CORTADO_PELO_APP])
        assertEquals("5", File(bin, "n").readText().trim())
    }

    @Test
    fun `sh de verdade - svc que falha encerra na hora sem ler settings`() {
        val bin = pastaTemporaria("corte")
        val real = ExecutorReal(bin)
        comandoFalso(bin, "svc", "exit 1")
        comandoFalso(bin, "settings", "echo 0 > '${bin.absolutePath}/leu'; echo 0")
        val n = nucleo(shell = real)
        armazem.valores[ChavesDaRede.CONTROLE_LIGADO] = true
        armazem.valores[ChavesDaRede.CORTE_MANUAL] = true
        n.reavaliar()
        esperarFundo()
        assertNull(armazem.valores[ChavesDaRede.CORTADO_PELO_APP])
        assertFalse(File(bin, "leu").exists())
    }

    // ---- coalescência (caso 7) ----

    @Test
    fun `dez chamadas em rajada durante uma avaliacao lenta dao no maximo duas avaliacoes`() {
        val entrou = CountDownLatch(1)
        val liberar = CountDownLatch(1)
        val rodando = AtomicInteger(0)
        val maximoSimultaneo = AtomicInteger(0)
        val total = AtomicInteger(0)
        val lento = ShellFalso { _ ->
            val agora = rodando.incrementAndGet()
            maximoSimultaneo.updateAndGet { maxOf(it, agora) }
            total.incrementAndGet()
            entrou.countDown()
            liberar.await(10, TimeUnit.SECONDS)
            rodando.decrementAndGet()
            MARCADOR_OK
        }
        val n = nucleo(shell = lento)
        armazem.valores[ChavesDaRede.CONTROLE_LIGADO] = true
        armazem.valores[ChavesDaRede.CORTE_MANUAL] = true

        n.reavaliar()
        assertTrue(entrou.await(10, TimeUnit.SECONDS))
        repeat(9) { n.reavaliar() }
        liberar.countDown()
        esperarFundo()
        assertEquals(2, total.get())
        assertEquals(1, maximoSimultaneo.get())
    }

    @Test
    fun `chamadas antes de a avaliacao comecar tambem sao fundidas`() {
        val travado = CountDownLatch(1)
        fundo.execute { travado.await(10, TimeUnit.SECONDS) } // ocupa a thread
        val n = nucleo()
        armazem.valores[ChavesDaRede.CONTROLE_LIGADO] = true
        armazem.valores[ChavesDaRede.CORTE_MANUAL] = true
        repeat(10) { n.reavaliar() }
        travado.countDown()
        esperarFundo()
        assertEquals(1, shell.comandos.size)
    }

    @Test
    fun `excecao dentro da avaliacao e registrada e a thread continua`() {
        var primeira = true
        val explosivo = ShellFalso { _ ->
            if (primeira) {
                primeira = false
                throw IllegalStateException("estourou")
            }
            MARCADOR_OK
        }
        val n = nucleo(shell = explosivo)
        armazem.valores[ChavesDaRede.CONTROLE_LIGADO] = true
        armazem.valores[ChavesDaRede.CORTE_MANUAL] = true
        n.reavaliar()
        esperarFundo()
        assertTrue(registro.erros.isNotEmpty())
        n.reavaliar()
        esperarFundo()
        assertEquals(true, armazem.valores[ChavesDaRede.CORTADO_PELO_APP])
    }

    // ---- ouvinte do mestre (caso 8) ----

    @Test
    fun `definirControleLigado avisa o ouvinte e as outras definir nao`() {
        val n = nucleo()
        val recebidos = mutableListOf<Boolean>()
        n.aoMudarControle { recebidos += it }
        n.definirControleLigado(true)
        n.definirCorteManual(true)
        n.definirCorteNoWifi(true)
        n.definirControleLigado(false)
        esperarFundo()
        assertEquals(listOf(true, false), recebidos)
    }

    @Test
    fun `ouvinte que lanca nao impede o resto`() {
        val n = nucleo()
        n.aoMudarControle { throw IllegalStateException("ouvinte") }
        n.definirControleLigado(true)
        assertEquals(true, armazem.valores[ChavesDaRede.CONTROLE_LIGADO])
        n.aoMudarControle(null)
        n.definirControleLigado(false)
    }

    // ---- permissão de uso (caso 12) ----

    @Test
    fun `permissao de uso so com mestre e regra de consumo, tres comandos independentes`() {
        val n = nucleo()
        n.garantirPermissaoDeUso()
        esperarFundo()
        assertTrue(shell.comandos.isEmpty())

        armazem.valores[ChavesDaRede.CONTROLE_LIGADO] = true
        n.garantirPermissaoDeUso()
        esperarFundo()
        assertTrue(shell.comandos.isEmpty())

        armazem.valores[ChavesDaRede.CORTE_SOB_CONSUMO] = true
        shell.resposta = { "" } // tudo falha: os três ainda rodam
        n.garantirPermissaoDeUso()
        esperarFundo()
        assertEquals(3, shell.comandos.size)
        assertTrue("appops set br.com.deepgwt GET_USAGE_STATS allow" in shell.comandos[0])
        assertTrue("pm grant br.com.deepgwt android.permission.PACKAGE_USAGE_STATS" in shell.comandos[1])
        assertTrue("pm grant br.com.deepgwt android.permission.READ_NETWORK_USAGE_HISTORY" in shell.comandos[2])
        assertEquals(3, registro.avisos.size)
    }

    @Test
    fun `definir regra de consumo concede a permissao antes de reavaliar`() {
        val n = nucleo()
        armazem.valores[ChavesDaRede.CONTROLE_LIGADO] = true
        n.definirCorteSobConsumo(true)
        esperarFundo()
        assertTrue("appops" in shell.comandos.first())
    }

    // ---- telemetria (seção B) ----

    @Test
    fun `bloquear telemetria congela o pacote e confere a linha exata`() {
        val n = nucleo()
        n.definirTelemetriaBloqueada(true)
        esperarFundo()
        val comando = shell.comandos.single()
        assertTrue("pm disable-user --user 0 com.beantechs.datatrackservice" in comando)
        assertTrue("am force-stop com.beantechs.datatrackservice" in comando)
        assertTrue("pm list packages -d" in comando)
        assertTrue("grep -Fxq 'package:com.beantechs.datatrackservice'" in comando)
        assertTrue(comando.indexOf("disable-user") < comando.indexOf("force-stop"))
        assertTrue(comando.indexOf("force-stop") < comando.indexOf("list packages"))
        assertEquals(true, armazem.valores[ChavesDaRede.TELEMETRIA_CONGELADA_PELO_APP])
    }

    @Test
    fun `reaplicar depois de reinicio roda uma vez e depois nao se repete`() {
        armazem.valores[ChavesDaRede.TELEMETRIA_BLOQUEADA] = true
        armazem.valores[ChavesDaRede.TELEMETRIA_CONGELADA_PELO_APP] = true
        val n = nucleo()
        n.reaplicarTelemetria()
        esperarFundo()
        assertEquals(1, shell.comandos.size) // reaplicado uma vez (idempotente)
        n.reaplicarTelemetria()
        n.reaplicarTelemetria()
        esperarFundo()
        assertEquals(1, shell.comandos.size)
    }

    @Test
    fun `desbloquear telemetria so se o app tinha congelado`() {
        val n = nucleo()
        n.definirTelemetriaBloqueada(false)
        esperarFundo()
        assertTrue(shell.comandos.isEmpty())

        armazem.valores[ChavesDaRede.TELEMETRIA_CONGELADA_PELO_APP] = true
        n.definirTelemetriaBloqueada(false)
        esperarFundo()
        val comando = shell.comandos.single()
        assertTrue("pm enable com.beantechs.datatrackservice" in comando)
        assertEquals(false, armazem.valores[ChavesDaRede.TELEMETRIA_CONGELADA_PELO_APP])
    }

    @Test
    fun `falha da telemetria nao grava nada`() {
        val sem = ShellFalso { "" }
        val n = nucleo(shell = sem)
        n.definirTelemetriaBloqueada(true)
        esperarFundo()
        assertNull(armazem.valores[ChavesDaRede.TELEMETRIA_CONGELADA_PELO_APP])
        assertTrue(registro.erros.isNotEmpty())
    }

    @Test
    fun `corte e telemetria nunca rodam ao mesmo tempo`() {
        val rodando = AtomicInteger(0)
        val maximo = AtomicInteger(0)
        val lento = ShellFalso { _ ->
            maximo.updateAndGet { maxOf(it, rodando.incrementAndGet()) }
            Thread.sleep(30)
            rodando.decrementAndGet()
            MARCADOR_OK
        }
        val n = nucleo(shell = lento)
        armazem.valores[ChavesDaRede.CONTROLE_LIGADO] = true
        armazem.valores[ChavesDaRede.CORTE_MANUAL] = true
        repeat(3) {
            n.reavaliar()
            n.definirTelemetriaBloqueada(it % 2 == 0)
        }
        esperarFundo()
        assertEquals(1, maximo.get())
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
