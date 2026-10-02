package br.com.opengw.rede

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * V8 da especificação: o script do roteador rodando de verdade com `sh` (e `dash`, se houver),
 * com `ip`, `iptables`, `ping` e `getprop` falsos no início do PATH. Os falsos guardam estado em
 * arquivos e registram cada chamada em `calls.log`.
 */
class ScriptDoRoteadorTest {

    private val asset = File("src/main/assets/roteador_do_hotspot.sh")
    private val interpretadores = listOfNotNull("sh", if (File("/bin/dash").exists()) "dash" else null)

    private lateinit var raiz: File
    private lateinit var bin: File
    private lateinit var base: File
    private lateinit var proc: File
    private lateinit var sys: File
    private lateinit var fake: File
    private lateinit var ipfwd: File
    private lateinit var script: File
    private val processos = mutableListOf<Process>()

    @Before
    fun montar() {
        assumeTrue("script não encontrado em ${asset.absolutePath}", asset.exists())
        raiz = pastaTemporaria("roteador")
        bin = File(raiz, "bin").apply { mkdirs() }
        base = File(raiz, "base").apply { mkdirs() }
        proc = File(raiz, "proc").apply { mkdirs() }
        sys = File(raiz, "sys").apply { mkdirs() }
        fake = File(raiz, "fake").apply { mkdirs() }
        ipfwd = File(raiz, "ip_forward").apply { writeText("0\n") }
        script = File(base, "hotrouter.sh")
        asset.copyTo(script)

        val d = fake.absolutePath
        val s = '§'
        comandoFalso(
            bin, "ip",
            """
            echo "ip §*" >> '$d/calls.log'
            case "§1" in
              link)
                for m in §FAKE_LINKS_MISSING; do [ "§m" = "§3" ] && exit 1; done
                exit 0 ;;
              route)
                [ "§2" = flush ] && exit 0
                if [ "§FAKE_NO_DEFAULT" != 1 ] && [ "§4" = wlan0 ]; then echo "default via 192.168.100.1 dev wlan0"; fi
                exit 0 ;;
              rule)
                case "§2" in
                  show) cat '$d/rules' 2>/dev/null; exit 0 ;;
                  add|del)
                    op=§2; shift 2
                    iif=""; tab=""; prio=""
                    while [ §# -gt 0 ]; do
                      case "§1" in iif) iif=§2; shift ;; lookup) tab=§2; shift ;; priority) prio=§2; shift ;; esac
                      shift
                    done
                    linha=§(printf '%s:\tfrom all iif %s lookup %s' "§prio" "§iif" "§tab")
                    if [ "§op" = add ]; then
                      [ "§FAKE_RULE_ADD_FAIL" = 1 ] && exit 2
                      echo "§linha" >> '$d/rules'; exit 0
                    fi
                    [ "§FAKE_RULE_DEL_FAIL" = 1 ] && exit 2
                    awk -v l="§linha" '!feito && §0 == l { feito = 1; next } { print }' '$d/rules' > '$d/rules.novo' && mv '$d/rules.novo' '$d/rules'
                    exit 0 ;;
                esac ;;
            esac
            exit 0
            """.trimIndent().replace(s, '$'),
        )
        comandoFalso(
            bin, "iptables",
            """
            echo "iptables §*" >> '$d/calls.log'
            tabela=filter
            if [ "§1" = -t ]; then tabela=§2; shift 2; fi
            op=§1; cadeia=§2; shift 2
            case "§op" in
              -S) for m in §FAKE_CHAINS_MISSING; do [ "§m" = "§cadeia" ] && exit 1; done; exit 0 ;;
              -C) grep -qxF "§tabela|§cadeia|§*" '$d/iptables' 2>/dev/null ;;
              -I)
                [ "§FAKE_IPT_INSERT_FAIL" = 1 ] && exit 1
                shift
                echo "§tabela|§cadeia|§*" >> '$d/iptables' ;;
              -D)
                [ "§FAKE_IPT_DEL_FAIL" = 1 ] && exit 1
                grep -qxF "§tabela|§cadeia|§*" '$d/iptables' 2>/dev/null || exit 1
                awk -v l="§tabela|§cadeia|§*" '!feito && §0 == l { feito = 1; next } { print }' '$d/iptables' > '$d/iptables.novo' && mv '$d/iptables.novo' '$d/iptables' ;;
            esac
            """.trimIndent().replace(s, '$'),
        )
        comandoFalso(
            bin, "ping",
            """
            echo "ping §*" >> '$d/calls.log'
            for ultimo; do :; done
            for h in §FAKE_PING_OK; do [ "§h" = "§ultimo" ] && exit 0; done
            exit 1
            """.trimIndent().replace(s, '$'),
        )
        comandoFalso(bin, "getprop", "echo \"\$FAKE_SOFTAP\"")
        File(fake, "rules").writeText("")
        File(fake, "iptables").writeText("")
        File(fake, "calls.log").writeText("")
    }

    @After
    fun limpar() {
        processos.forEach { it.destroyForcibly() }
        if (::raiz.isInitialized) raiz.deleteRecursively()
    }

    private class Resultado(val codigo: Int, val saida: String)

    private fun rodar(
        interpretador: String,
        vararg args: String,
        ambiente: Map<String, String> = emptyMap(),
    ): Resultado {
        val construtor = ProcessBuilder(interpretador, script.absolutePath, *args).redirectErrorStream(true)
        preparar(construtor, ambiente)
        val processo = construtor.start()
        processo.outputStream.close()
        val saida = processo.inputStream.bufferedReader().readText()
        assertTrue("o script travou", processo.waitFor(60, TimeUnit.SECONDS))
        return Resultado(processo.exitValue(), saida.trim())
    }

    private fun preparar(construtor: ProcessBuilder, ambiente: Map<String, String>) {
        val env = construtor.environment()
        env["PATH"] = "${bin.absolutePath}:/usr/bin:/bin:/usr/sbin:/sbin"
        env["HOTROUTER_BASE"] = base.absolutePath
        env["HOTROUTER_IPFORWARD"] = ipfwd.absolutePath
        env["HOTROUTER_PROC"] = proc.absolutePath
        env["HOTROUTER_SYSNET"] = sys.absolutePath
        env["HOTROUTER_INTERVAL"] = "1"
        env["FAKE_DIR"] = fake.absolutePath
        env["FAKE_PING_OK"] = "8.8.8.8 1.1.1.1"
        env.putAll(ambiente)
    }

    private fun umaIteracao(interpretador: String, ambiente: Map<String, String> = emptyMap()) =
        rodar(interpretador, "start", ambiente = ambiente + ("HOTROUTER_ONCE" to "1"))

    private fun chamadas() = File(fake, "calls.log").readLines()
    private fun regrasDeRoteamento() = File(fake, "rules").readLines().filter { it.isNotBlank() }
    private fun regrasDoIptables() = File(fake, "iptables").readLines().filter { it.isNotBlank() }
    private fun estado() = File(base, "hotrouter.state").readText().trim()
    private fun log() = File(base, "hotrouter.log").takeIf { it.exists() }?.readText().orEmpty()

    private val seisRegras = listOf(
        "nat|tetherctrl_nat_POSTROUTING|-o wlan0 -j MASQUERADE",
        "filter|tetherctrl_FORWARD|-i wlan0 -o wlan2 -m state --state RELATED,ESTABLISHED -g tetherctrl_counters",
        "filter|tetherctrl_FORWARD|-i wlan2 -o wlan0 -m state --state INVALID -j DROP",
        "filter|tetherctrl_FORWARD|-i wlan2 -o wlan0 -g tetherctrl_counters",
        "filter|tetherctrl_counters|-i wlan2 -o wlan0 -j RETURN",
        "filter|tetherctrl_counters|-i wlan0 -o wlan2 -j RETURN",
    )

    private fun todosOsInterpretadores(corpo: (String) -> Unit) {
        for (i in interpretadores) {
            // Cada interpretador parte de um mundo limpo.
            if (i != interpretadores.first()) {
                limpar(); montar()
            }
            try {
                corpo(i)
            } catch (e: Throwable) {
                throw AssertionError("com o interpretador '$i': ${e.message}", e)
            }
        }
    }

    // ---- sintaxe ----

    @Test
    fun `sintaxe valida nos interpretadores disponiveis`() {
        for (i in interpretadores) {
            val p = ProcessBuilder(i, "-n", asset.absolutePath).redirectErrorStream(true).start()
            val saida = p.inputStream.bufferedReader().readText()
            assertEquals("$i -n: $saida", 0, p.waitFor())
        }
        val texto = asset.readText()
        assertTrue(texto.startsWith("#!/system/bin/sh\n"))
        assertFalse("sem bash", Regex("\\[\\[|(^|\\s)local\\s+\\w|<<<|(^|\\s)function\\s+\\w").containsMatchIn(texto))
        assertTrue("o script cabe num argumento de linha de comando", asset.length() < 64 * 1024)
    }

    @Test
    fun `subcomando desconhecido imprime o uso e sai com 1`() = todosOsInterpretadores { i ->
        val r = rodar(i, "outra-coisa")
        assertEquals(1, r.codigo)
        assertTrue(r.saida.startsWith("uso:"))
    }

    // ---- V8: com internet ----

    @Test
    fun `v8 com internet acrescenta uma regra de roteamento, as seis do iptables e liga o ip_forward`() = todosOsInterpretadores { i ->
        val r = umaIteracao(i)
        assertEquals(r.saida, 0, r.codigo)
        assertEquals(1, regrasDeRoteamento().size)
        assertEquals("17999:\tfrom all iif wlan2 lookup wlan0", regrasDeRoteamento().single())
        assertEquals(1, chamadas().count { it.startsWith("ip rule add") })
        assertTrue("ip rule add from all iif wlan2 lookup wlan0 priority 17999" in chamadas())

        // As seis regras, nas posições certas.
        val insercoes = chamadas().filter { it.startsWith("iptables") && " -I " in it }
        assertEquals(
            listOf(
                "iptables -t nat -I tetherctrl_nat_POSTROUTING 1 -o wlan0 -j MASQUERADE",
                "iptables -I tetherctrl_FORWARD 1 -i wlan0 -o wlan2 -m state --state RELATED,ESTABLISHED -g tetherctrl_counters",
                "iptables -I tetherctrl_FORWARD 2 -i wlan2 -o wlan0 -m state --state INVALID -j DROP",
                "iptables -I tetherctrl_FORWARD 3 -i wlan2 -o wlan0 -g tetherctrl_counters",
                "iptables -I tetherctrl_counters 1 -i wlan2 -o wlan0 -j RETURN",
                "iptables -I tetherctrl_counters 2 -i wlan0 -o wlan2 -j RETURN",
            ),
            insercoes,
        )
        assertEquals(seisRegras, regrasDoIptables())
        assertEquals("1", ipfwd.readText().trim())
        assertTrue(estado().matches(Regex("WLAN\\|\\d+")))
        assertTrue("ip route flush cache" in chamadas())
        // O ping foi pela wlan0, com os parâmetros do caso 28, só no primeiro host.
        assertEquals(listOf("ping -I wlan0 -c 1 -W 2 8.8.8.8"), chamadas().filter { it.startsWith("ping") })
        // O valor original do ip_forward foi guardado.
        assertEquals("0", File(base, "hotrouter.ip_forward.original").readText().trim())
        assertFalse("o modo de teste não deixa o pid", File(base, "hotrouter.pid").exists())
    }

    @Test
    fun `v8 de novo com tudo presente nao faz nenhuma alteracao (D4)`() = todosOsInterpretadores { i ->
        umaIteracao(i)
        val estadoAntes = estado()
        File(fake, "calls.log").writeText("")
        Thread.sleep(1100) // a época do estado muda
        val r = umaIteracao(i)
        assertEquals(r.saida, 0, r.codigo)
        val c = chamadas()
        assertTrue("ip rule del" !in c.joinToString("\n"))
        assertTrue(c.none { it.startsWith("ip rule add") || it.startsWith("ip rule del") })
        assertTrue(c.none { it.startsWith("iptables") && (" -I " in it || " -A " in it || " -D " in it) })
        assertTrue(c.none { it.startsWith("ip route flush") })
        assertEquals(1, regrasDeRoteamento().size)
        assertEquals(seisRegras, regrasDoIptables())
        assertTrue(estado().matches(Regex("WLAN\\|\\d+")))
        assertTrue(estado() != estadoAntes)
    }

    @Test
    fun `duplicatas da regra de roteamento sao removidas, deixando exatamente uma`() = todosOsInterpretadores { i ->
        val linha = "17999:\tfrom all iif wlan2 lookup wlan0"
        File(fake, "rules").writeText("$linha\n$linha\n$linha\n")
        umaIteracao(i)
        assertEquals(1, regrasDeRoteamento().size)
        assertEquals(2, chamadas().count { it.startsWith("ip rule del") })
        assertTrue(chamadas().none { it.startsWith("ip rule add") })
    }

    @Test
    fun `a interface do hotspot vem da propriedade do sistema`() = todosOsInterpretadores { i ->
        File(fake, "rules").writeText("")
        umaIteracao(i, mapOf("FAKE_SOFTAP" to "ap0"))
        assertEquals("17999:\tfrom all iif ap0 lookup wlan0", regrasDeRoteamento().single())
        assertTrue(regrasDoIptables().any { "-i ap0 -o wlan0" in it })
    }

    // ---- V8: sem internet ----

    @Test
    fun `v8 sem internet remove a regra de roteamento e nao mexe no iptables`() = todosOsInterpretadores { i ->
        umaIteracao(i)
        File(fake, "calls.log").writeText("")
        val r = umaIteracao(i, mapOf("FAKE_PING_OK" to ""))
        assertEquals(r.saida, 0, r.codigo)
        assertTrue(regrasDeRoteamento().isEmpty())
        assertTrue(chamadas().any { it.startsWith("ip rule del") })
        assertTrue(chamadas().none { it.startsWith("iptables") && (" -I " in it || " -A " in it || " -D " in it) })
        assertEquals(seisRegras, regrasDoIptables()) // ficam: é o tethering normal do Android
        assertEquals("1", ipfwd.readText().trim())
        assertTrue(estado().matches(Regex("4G\\|\\d+")))
        // Tentou os dois hosts.
        assertEquals(
            listOf("ping -I wlan0 -c 1 -W 2 8.8.8.8", "ping -I wlan0 -c 1 -W 2 1.1.1.1"),
            chamadas().filter { it.startsWith("ping") },
        )
    }

    @Test
    fun `o segundo host salva quando o primeiro falha`() = todosOsInterpretadores { i ->
        umaIteracao(i, mapOf("FAKE_PING_OK" to "1.1.1.1"))
        assertTrue(estado().matches(Regex("WLAN\\|\\d+")))
    }

    @Test
    fun `sem rota padrao na tabela wlan0 ou sem interface e 4G`() = todosOsInterpretadores { i ->
        umaIteracao(i, mapOf("FAKE_NO_DEFAULT" to "1"))
        assertTrue(estado().startsWith("4G|"))
        assertTrue(chamadas().none { it.startsWith("ping") })
        umaIteracao(i, mapOf("FAKE_LINKS_MISSING" to "wlan2"))
        assertTrue(estado().startsWith("4G|"))
        assertTrue(regrasDeRoteamento().isEmpty())
    }

    @Test
    fun `v8 cadeia do netd ausente remove a regra, registra erro e reporta 4G`() = todosOsInterpretadores { i ->
        val r = umaIteracao(i, mapOf("FAKE_CHAINS_MISSING" to "tetherctrl_FORWARD"))
        assertEquals(r.saida, 0, r.codigo)
        assertTrue(regrasDeRoteamento().isEmpty())
        assertTrue(estado().startsWith("4G|"))
        assertTrue(log().lines().any { " ERRO " in it })
        assertTrue(regrasDoIptables().isEmpty())
    }

    @Test
    fun `insercao do iptables que falha tambem e sem consertar`() = todosOsInterpretadores { i ->
        umaIteracao(i, mapOf("FAKE_IPT_INSERT_FAIL" to "1"))
        assertTrue(regrasDeRoteamento().isEmpty())
        assertTrue(estado().startsWith("4G|"))
        assertTrue(log().lines().any { " ERRO " in it })
    }

    @Test
    fun `regra de roteamento que nao entra tambem e 4G`() = todosOsInterpretadores { i ->
        umaIteracao(i, mapOf("FAKE_RULE_ADD_FAIL" to "1"))
        assertTrue(estado().startsWith("4G|"))
    }

    // ---- ip_forward ----

    @Test
    fun `ip_forward ilegivel nao inicia`() = todosOsInterpretadores { i ->
        ipfwd.writeText("talvez\n")
        val r = umaIteracao(i)
        assertEquals(1, r.codigo)
        assertFalse(File(base, "hotrouter.pid").exists())
        assertFalse(File(base, "hotrouter.ip_forward.original").exists())
    }

    @Test
    fun `o valor original so e guardado uma vez`() = todosOsInterpretadores { i ->
        umaIteracao(i) // guarda 0 e põe 1
        umaIteracao(i) // o arquivo existe: não regrava com 1
        assertEquals("0", File(base, "hotrouter.ip_forward.original").readText().trim())
    }

    // ---- stop ----

    private fun prepararTudoInstalado() {
        val linha = "17999:\tfrom all iif wlan2 lookup wlan0"
        File(fake, "rules").writeText("$linha\n$linha\n")
        File(fake, "iptables").writeText(seisRegras.joinToString("\n") + "\n" + seisRegras[0] + "\n") // uma cópia a mais
        File(base, "hotrouter.ip_forward.original").writeText("0\n")
        ipfwd.writeText("1\n")
    }

    @Test
    fun `v8 stop remove tudo, inclusive copias, restaura o ip_forward e imprime STOPPED`() = todosOsInterpretadores { i ->
        prepararTudoInstalado()
        val r = rodar(i, "stop")
        assertEquals(0, r.codigo)
        assertEquals("STOPPED", r.saida)
        assertTrue(regrasDeRoteamento().isEmpty())
        assertTrue(regrasDoIptables().isEmpty())
        assertEquals("0", ipfwd.readText().trim())
        assertFalse(File(base, "hotrouter.ip_forward.original").exists())
        assertTrue(estado().startsWith("OFF|"))
    }

    @Test
    fun `v8 stop com uma remocao que falha imprime STOP_FAILED e sai com 1`() = todosOsInterpretadores { i ->
        prepararTudoInstalado()
        val r = rodar(i, "stop", ambiente = mapOf("FAKE_IPT_DEL_FAIL" to "1"))
        assertEquals(1, r.codigo)
        assertEquals("STOP_FAILED", r.saida)
        assertTrue(regrasDoIptables().isNotEmpty())
    }

    @Test
    fun `stop com a regra de roteamento que nao sai tambem falha`() = todosOsInterpretadores { i ->
        prepararTudoInstalado()
        val r = rodar(i, "stop", ambiente = mapOf("FAKE_RULE_DEL_FAIL" to "1"))
        assertEquals(1, r.codigo)
        assertEquals("STOP_FAILED", r.saida)
    }

    @Test
    fun `D5 stop com o hotspot no ar nao baixa o ip_forward para 0`() = todosOsInterpretadores { i ->
        prepararTudoInstalado()
        File(sys, "wlan2").mkdirs()
        File(sys, "wlan2/carrier").writeText("1\n")
        val r = rodar(i, "stop")
        assertEquals("STOPPED", r.saida)
        assertEquals("1", ipfwd.readText().trim())
        assertFalse(File(base, "hotrouter.ip_forward.original").exists())
    }

    @Test
    fun `stop com original 1 restaura 1 e sem nada instalado ainda diz STOPPED`() = todosOsInterpretadores { i ->
        val r = rodar(i, "stop")
        assertEquals("STOPPED", r.saida)
        File(base, "hotrouter.ip_forward.original").writeText("1\n")
        ipfwd.writeText("0\n")
        assertEquals("STOPPED", rodar(i, "stop").saida)
        assertEquals("1", ipfwd.readText().trim())
    }

    // ---- status e reconhecimento do daemon (D1) ----

    /** `Process.pid()` é do Java 9; por reflexão para compilar com qualquer alvo. */
    private fun pidDe(processo: Process): Long = Process::class.java.getMethod("pid").invoke(processo) as Long

    private fun processoFalso(pid: Long, vararg argv: String) {
        val pasta = File(proc, pid.toString()).apply { mkdirs() }
        File(pasta, "cmdline").writeBytes(argv.joinToString("\u0000", postfix = "\u0000").toByteArray())
    }

    @Test
    fun `status ALIVE so quando a linha de comando e este script com start`() = todosOsInterpretadores { i ->
        processoFalso(4242, "sh", script.absolutePath, "start")
        File(base, "hotrouter.pid").writeText("4242\n")
        val vivo = rodar(i, "status")
        assertEquals(0, vivo.codigo)
        assertEquals("ALIVE|4242", vivo.saida)
    }

    @Test
    fun `status DEAD apaga o pid sobrando`() = todosOsInterpretadores { i ->
        File(base, "hotrouter.pid").writeText("4242\n") // sem processo
        val morto = rodar(i, "status")
        assertEquals(1, morto.codigo)
        assertEquals("DEAD", morto.saida)
        assertFalse(File(base, "hotrouter.pid").exists())
    }

    @Test
    fun `D1 am start nao e o daemon`() = todosOsInterpretadores { i ->
        processoFalso(4343, "am", "start", "-n", "x/.y")
        File(base, "hotrouter.pid").writeText("4343\n")
        assertEquals("DEAD", rodar(i, "status").saida)
        // outro script com start também não
        processoFalso(4344, "sh", "/data/local/tmp/outro.sh", "start")
        File(base, "hotrouter.pid").writeText("4344\n")
        assertEquals("DEAD", rodar(i, "status").saida)
        // este script com outro subcomando também não
        processoFalso(4345, "sh", script.absolutePath, "status")
        File(base, "hotrouter.pid").writeText("4345\n")
        assertEquals("DEAD", rodar(i, "status").saida)
    }

    @Test
    fun `pid menor ou igual a 1 ou nao numerico nunca e o daemon`() = todosOsInterpretadores { i ->
        processoFalso(1, "sh", script.absolutePath, "start")
        File(base, "hotrouter.pid").writeText("1\n")
        assertEquals("DEAD", rodar(i, "status").saida)
        File(base, "hotrouter.pid").writeText("abc\n")
        assertEquals("DEAD", rodar(i, "status").saida)
    }

    @Test
    fun `v8 o start termina so o processo que e o daemon (D1)`() = todosOsInterpretadores { i ->
        val intruso = ProcessBuilder("sleep", "300").start().also { processos += it }
        val daemonAntigo = ProcessBuilder("sleep", "300").start().also { processos += it }
        processoFalso(pidDe(intruso), "am", "start", "-n", "x/.y")
        processoFalso(pidDe(daemonAntigo), "sh", script.absolutePath, "start")
        val r = umaIteracao(i)
        assertEquals(r.saida, 0, r.codigo)
        assertTrue("o daemon antigo devia ter terminado", daemonAntigo.waitFor(10, TimeUnit.SECONDS))
        assertTrue("o processo com 'am start' não pode ser terminado", intruso.isAlive)
    }

    @Test
    fun `o stop tambem so termina o daemon`() = todosOsInterpretadores { i ->
        val intruso = ProcessBuilder("sleep", "300").start().also { processos += it }
        val daemonAntigo = ProcessBuilder("sleep", "300").start().also { processos += it }
        processoFalso(pidDe(intruso), "am", "start", "-n", "x/.y")
        processoFalso(pidDe(daemonAntigo), "sh", script.absolutePath, "start")
        File(base, "hotrouter.pid").writeText("${pidDe(daemonAntigo)}\n")
        val r = rodar(i, "stop")
        assertEquals("STOPPED", r.saida)
        assertTrue(daemonAntigo.waitFor(10, TimeUnit.SECONDS))
        assertTrue(intruso.isAlive)
    }

    // ---- log ----

    @Test
    fun `o log guarda no maximo 400 linhas e cada linha tem data, nivel e mensagem`() = todosOsInterpretadores { i ->
        File(base, "hotrouter.log").writeText((1..700).joinToString("\n") { "linha $it" } + "\n")
        umaIteracao(i)
        val linhas = File(base, "hotrouter.log").readLines()
        assertTrue("sobraram ${linhas.size}", linhas.size <= 400 + 5)
        assertTrue(linhas.any { it == "linha 700" })
        assertFalse(linhas.any { it == "linha 1" })
        assertTrue(linhas.last().matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} (INFO|AVISO|ERRO) .+")))
    }

    // ---- o laço de verdade, com sinal ----

    @Test
    fun `TERM no daemon faz a limpeza, apaga o pid e sai com 0`() = todosOsInterpretadores { i ->
        val construtor = ProcessBuilder(i, script.absolutePath, "start").redirectErrorStream(true)
        preparar(construtor, emptyMap())
        val processo = construtor.start().also { processos += it }
        processo.outputStream.close()

        val estadoPronto = File(base, "hotrouter.state")
        val limite = System.currentTimeMillis() + 15_000
        while (!estadoPronto.exists() && System.currentTimeMillis() < limite) Thread.sleep(50)
        assertTrue("o daemon não gravou o estado", estadoPronto.exists())
        assertTrue(File(base, "hotrouter.pid").exists())
        assertEquals(1, regrasDeRoteamento().size)
        assertEquals("1", ipfwd.readText().trim())

        processo.destroy() // SIGTERM
        assertTrue("o daemon não saiu", processo.waitFor(15, TimeUnit.SECONDS))
        assertEquals(0, processo.exitValue())
        assertTrue(regrasDeRoteamento().isEmpty())
        assertTrue(regrasDoIptables().isEmpty())
        assertEquals("0", ipfwd.readText().trim())
        assertFalse(File(base, "hotrouter.ip_forward.original").exists())
        assertFalse(File(base, "hotrouter.pid").exists())
        assertTrue(estado().startsWith("OFF|"))
    }
}
