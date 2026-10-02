package br.com.opengw.rede

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** V9 e caso 33 da especificação: firewall liberado e vigia. */
class FirewallDoCarroTest {

    private val registro = RegistroFalso()

    /** `iptables -S` falso por cadeia; `iptables -I` responde com marcador conforme [insercaoFunciona]. */
    private class IptablesFalso(
        val regras: MutableMap<String, String>,
        val insercaoFunciona: MutableMap<String, Boolean> = mutableMapOf("INPUT" to true, "OUTPUT" to true),
    ) : ExecutorDeShell {
        val comandos = ArrayList<String>()

        override fun executar(comando: Array<String>): String {
            val texto = comando.joinToString(" ")
            comandos += texto
            if (comando.size == 3 && comando[0] == "iptables" && comando[1] == "-S") return regras[comando[2]].orEmpty()
            val cadeia = Regex("iptables -I (\\w+) 1 -j ACCEPT").find(texto)?.groupValues?.get(1)
            if (cadeia != null) return if (insercaoFunciona[cadeia] == true) MARCADOR_OK else ""
            return ""
        }

        fun insercoes() = comandos.filter { "iptables -I" in it }
    }

    private fun garantidor(shell: ExecutorDeShell) = GarantidorDoFirewall(shell, registro)

    private val comAcceptNoInicio = "-P INPUT ACCEPT\n-A INPUT -j ACCEPT\n-A INPUT -p tcp --dport 22 -j DROP\n"

    @Test
    fun `v9 ACCEPT ja em primeiro nao insere nada`() {
        val f = IptablesFalso(mutableMapOf("INPUT" to comAcceptNoInicio, "OUTPUT" to comAcceptNoInicio.replace("INPUT", "OUTPUT")))
        assertTrue(garantidor(f).liberarEntradaESaida())
        assertTrue(f.insercoes().isEmpty())
    }

    @Test
    fun `v9 ACCEPT so em terceiro insere um I na posicao 1`() {
        val entrada = "-P INPUT DROP\n-A INPUT -s 10.0.0.1/32 -j DROP\n-A INPUT -p tcp -j DROP\n-A INPUT -j ACCEPT\n"
        val f = IptablesFalso(mutableMapOf("INPUT" to entrada, "OUTPUT" to "-P OUTPUT ACCEPT\n-A OUTPUT -j ACCEPT\n"))
        assertTrue(garantidor(f).liberarEntradaESaida())
        val insercoes = f.insercoes()
        assertEquals(1, insercoes.size)
        assertTrue("iptables -I INPUT 1 -j ACCEPT" in insercoes.single())
    }

    @Test
    fun `v9 sem ACCEPT insere um I`() {
        val f = IptablesFalso(mutableMapOf("INPUT" to "-P INPUT ACCEPT\n", "OUTPUT" to "-P OUTPUT ACCEPT\n-A OUTPUT -j ACCEPT\n"))
        assertTrue(garantidor(f).liberarEntradaESaida())
        assertEquals(1, f.insercoes().size)
    }

    @Test
    fun `v9 OUTPUT falha e INPUT ainda e tentada, retorno false`() {
        val f = IptablesFalso(
            mutableMapOf("INPUT" to "-P INPUT ACCEPT\n", "OUTPUT" to "-P OUTPUT DROP\n"),
            mutableMapOf("INPUT" to true, "OUTPUT" to false),
        )
        assertFalse(garantidor(f).liberarEntradaESaida())
        assertEquals(2, f.insercoes().size)
        assertTrue(registro.erros.any { "OUTPUT" in it })
        assertTrue(registro.avisos.any { "INPUT" in it })
    }

    @Test
    fun `v9 entrada falha e saida ainda e tentada`() {
        val f = IptablesFalso(
            mutableMapOf("INPUT" to "", "OUTPUT" to "-P OUTPUT ACCEPT\n"),
            mutableMapOf("INPUT" to false, "OUTPUT" to true),
        )
        assertFalse(garantidor(f).liberarEntradaESaida())
        assertEquals(2, f.insercoes().size)
    }

    @Test
    fun `v9 ambas certas depois de inserir`() {
        val f = IptablesFalso(mutableMapOf("INPUT" to "-P INPUT DROP\n", "OUTPUT" to "-P OUTPUT DROP\n"))
        assertTrue(garantidor(f).liberarEntradaESaida())
        assertEquals(2, f.insercoes().size)
    }

    @Test
    fun `excecao do shell nunca lanca`() {
        val g = garantidor(ExecutorDeShell { throw IllegalStateException("shell") })
        assertFalse(g.liberarEntradaESaida())
        assertEquals(2, registro.erros.size)
    }

    // ---- o verificador de posição ----

    @Test
    fun `D8 um ACCEPT depois de um DROP nao vale`() {
        assertFalse(RegrasDoFirewall.primeiraRegraLiberaTudo("-P INPUT ACCEPT\n-A INPUT -j DROP\n-A INPUT -j ACCEPT\n", "INPUT"))
    }

    @Test
    fun `ACCEPT com condicao nao vale`() {
        assertFalse(RegrasDoFirewall.primeiraRegraLiberaTudo("-A INPUT -i lo -j ACCEPT\n", "INPUT"))
        assertFalse(RegrasDoFirewall.primeiraRegraLiberaTudo("-A INPUT -j ACCEPT -m comment --comment x\n", "INPUT"))
    }

    @Test
    fun `ignora a politica e linhas de outra cadeia`() {
        assertTrue(RegrasDoFirewall.primeiraRegraLiberaTudo("-P INPUT DROP\n-N meu\n  -A INPUT -j ACCEPT  \n", "INPUT"))
        assertFalse(RegrasDoFirewall.primeiraRegraLiberaTudo("-A OUTPUT -j ACCEPT\n", "INPUT"))
        assertFalse(RegrasDoFirewall.primeiraRegraLiberaTudo("", "INPUT"))
        assertFalse(RegrasDoFirewall.primeiraRegraLiberaTudo("-A INPUTX -j ACCEPT\n", "INPUT"))
    }

    // ---- vigia (caso 33) ----

    @Test
    fun `o script do vigia tem sintaxe valida`() {
        val arquivo = File.createTempFile("vigia", ".sh")
        arquivo.writeText(VigiaDoFirewall.SCRIPT_DO_VIGIA)
        val codigo = ProcessBuilder("sh", "-n", arquivo.absolutePath).start().waitFor()
        assertEquals(0, codigo)
        assertTrue(VigiaDoFirewall.SCRIPT_DO_VIGIA.startsWith("#!/system/bin/sh\n"))
        assertTrue("\$1" in VigiaDoFirewall.SCRIPT_DO_VIGIA)
        assertFalse('§' in VigiaDoFirewall.SCRIPT_DO_VIGIA)
        assertTrue("-I \"\$1\" 1 -j ACCEPT" in VigiaDoFirewall.SCRIPT_DO_VIGIA)
    }

    @Test
    fun `o vigia instala o script pelo mesmo nome do atual e lanca com setsid`() {
        val shell = ShellFalso { comando ->
            val sha = sha256Hex(VigiaDoFirewall.SCRIPT_DO_VIGIA.toByteArray())
            when {
                "base64 -d" in comando -> "$sha  x"
                "mv -f" in comando -> "$sha  x"
                "sleep 1" in comando -> MARCADOR_OK
                else -> ""
            }
        }
        assertTrue(VigiaDoFirewall(shell, registro).iniciar())
        assertTrue(shell.comandos.any { "mv -f /data/local/tmp/iptables_watchdog.sh.tmp /data/local/tmp/iptables_watchdog.sh" in it })
        val lancamento = shell.comandos.single { "setsid" in it }
        assertTrue("setsid sh \$d/iptables_watchdog.sh > /dev/null 2>&1 < /dev/null &" in lancamento)
        assertFalse("/data/local/tmp/iptables_watchdog.sh" in lancamento)
    }

    @Test
    fun `vigia que nao sobe devolve false`() {
        val shell = ShellFalso { comando ->
            val sha = sha256Hex(VigiaDoFirewall.SCRIPT_DO_VIGIA.toByteArray())
            if ("base64 -d" in comando || "mv -f" in comando) "$sha  x" else ""
        }
        assertFalse(VigiaDoFirewall(shell, registro).iniciar())
    }

    @Test
    fun `vigia com a instalacao divergente nao lanca`() {
        val shell = ShellFalso { "0000  x" }
        assertFalse(VigiaDoFirewall(shell, registro).iniciar())
        assertTrue(shell.comandos.none { "setsid" in it })
    }

    @Test
    fun `sh de verdade - o vigia garante ACCEPT na primeira posicao a cada volta`() {
        val bin = pastaTemporaria("vigia")
        val d = bin.absolutePath
        // iptables falso: -S mostra um DROP antes do ACCEPT; -I registra.
        comandoFalso(
            bin, "iptables",
            """
            echo "iptables §*" >> '$d/calls'
            if [ "§1" = -S ]; then echo "-P §2 ACCEPT"; echo "-A §2 -j DROP"; echo "-A §2 -j ACCEPT"; fi
            """.trimIndent().replace('§', '$'),
        )
        // sleep falso: encerra o vigia (o pai) na primeira volta, para o teste não ficar preso.
        comandoFalso(bin, "sleep", "kill \$PPID")
        val script = File(bin, "vigia.sh")
        script.writeText(VigiaDoFirewall.SCRIPT_DO_VIGIA)
        assumeTrue(File("/bin/sh").exists())
        val construtor = ProcessBuilder("sh", script.absolutePath)
        construtor.environment()["PATH"] = "$d:/usr/bin:/bin"
        construtor.redirectErrorStream(true)
        val processo = construtor.start()
        processo.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)
        val chamadas = File(bin, "calls").readLines()
        assertEquals(1, chamadas.count { it == "iptables -I INPUT 1 -j ACCEPT" })
        assertEquals(1, chamadas.count { it == "iptables -I OUTPUT 1 -j ACCEPT" })
    }
}
