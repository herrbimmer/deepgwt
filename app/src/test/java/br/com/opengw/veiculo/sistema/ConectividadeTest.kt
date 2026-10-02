package br.com.opengw.veiculo.sistema

import br.com.opengw.veiculo.comandos.DiarioFalso
import br.com.opengw.veiculo.comandos.PreferenciasEmMemoria
import br.com.opengw.veiculo.comandos.ShellFalso
import br.com.opengw.veiculo.motor.AgendaFalsa
import br.com.opengw.veiculo.motor.RegistroFalsoDoMotor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList

/** motor-sistema.md §A (Bluetooth e hotspot). */
class ConectividadeTest {

    private class Bancada(decisoes: DecisoesDoSistema = DecisoesDoSistema()) {
        val eventos: MutableList<String> = Collections.synchronizedList(ArrayList())
        val agenda = AgendaFalsa(eventos)
        val shell = ShellFalso(eventos)
        val prefs = PreferenciasEmMemoria()
        val registro = RegistroFalsoDoMotor()
        val diario = DiarioFalso(eventos)

        @Volatile var bluetooth = false
        @Volatile var carroDesligado = false
        @Volatile var reflexao: Int? = -1
        @Volatile var semAlca = false
        val pedidosDoHotspot = CopyOnWriteArrayList<String>()

        /** Em que tentativa o "svc bluetooth enable" passa a valer (null = nunca). */
        @Volatile var bluetoothLigaNaVez: Int? = null
        private var ligacoes = 0

        val hotspot = object : HotspotDoCarro {
            override fun estadoPorReflexao(): Int? = reflexao
            override fun ligar(): Boolean {
                if (semAlca) return false
                pedidosDoHotspot += "ligar"
                return true
            }
            override fun desligar(): Boolean {
                if (semAlca) return false
                pedidosDoHotspot += "desligar"
                return true
            }
        }

        val nucleo = NucleoDaConectividade(
            bluetooth = BluetoothDoCarro { bluetooth },
            hotspot = hotspot,
            shell = shell,
            prefs = prefs,
            agenda = agenda,
            carroDesligado = { carroDesligado },
            registro = registro,
            diario = diario,
            decisoes = decisoes,
        )

        init {
            shell.responder = { comando ->
                when (comando) {
                    "svc bluetooth enable" -> {
                        ligacoes++
                        if (bluetoothLigaNaVez != null && ligacoes >= bluetoothLigaNaVez!!) bluetooth = true
                        ""
                    }
                    "svc bluetooth disable" -> {
                        bluetooth = false
                        ""
                    }
                    else -> null
                }
            }
        }

        fun carrier(valor: String, interfaceDoHotspot: String = "wlan2") {
            shell.saidas["getprop sys.wifi.softap_interface_name"] = interfaceDoHotspot
            shell.saidas["cat /sys/class/net/$interfaceDoHotspot/carrier"] = valor
        }

        fun ligacoesDoBluetooth() = shell.comandos.count { it == "svc bluetooth enable" }

        val marcaBt get() = prefs.valores["bluetoothStateOnPowerOff"]
        val marcaHotspot get() = prefs.valores["hotspotStateOnPowerOff"]
    }

    // ── Bluetooth ──

    @Test
    fun `A1 A2 - estado e comandos com diario`() {
        val b = Bancada()
        b.bluetooth = true
        assertTrue(b.nucleo.bluetoothLigado)
        b.nucleo.desligarBluetooth("teste")
        assertEquals(listOf("svc bluetooth disable"), b.shell.comandos)
        val entrada = b.diario.entradas.single()
        assertEquals("teste", entrada.second["motivo"])
        assertEquals(true, entrada.second["antes"])
        assertEquals(false, entrada.second["depois"])

        val quebrado = NucleoDaConectividade(
            BluetoothDoCarro { throw IllegalStateException("x") }, b.hotspot, b.shell, b.prefs, b.agenda, { false }, b.registro, b.diario,
        )
        assertFalse(quebrado.bluetoothLigado)
    }

    @Test
    fun `A4 - ligado grava a marca e desliga - desligado nao apaga uma marca anterior`() {
        val b = Bancada()
        b.bluetooth = true
        b.nucleo.desligarBluetoothGuardando("desligou")
        assertEquals(true, b.marcaBt)
        assertEquals(listOf("svc bluetooth disable"), b.shell.comandos)

        b.nucleo.desligarBluetoothGuardando("retrovisor")
        assertEquals("a marca fica", true, b.marcaBt)
        assertEquals(1, b.shell.comandos.size)
    }

    @Test
    fun `A5 - sem a marca nao faz nada`() {
        val b = Bancada()
        b.nucleo.religarBluetoothSeFomosNos("ligou")
        assertTrue(b.shell.comandos.isEmpty())
        assertEquals(0, b.agenda.pendentes())
    }

    @Test
    fun `A5 - 8 tentativas a cada 4 s e desiste apagando a marca`() {
        val b = Bancada()
        b.prefs["bluetoothStateOnPowerOff"] = true
        b.nucleo.religarBluetoothSeFomosNos("ligou")
        assertEquals(1, b.ligacoesDoBluetooth())
        b.agenda.avancar(3_999)
        assertEquals(1, b.ligacoesDoBluetooth())
        b.agenda.avancar(1)
        assertEquals(2, b.ligacoesDoBluetooth())
        b.agenda.avancar(60_000)
        assertEquals(8, b.ligacoesDoBluetooth())
        assertEquals(false, b.marcaBt)
        assertEquals(0, b.agenda.pendentes())
    }

    @Test
    fun `A5 - o primeiro que der certo encerra e apaga a marca`() {
        val b = Bancada()
        b.prefs["bluetoothStateOnPowerOff"] = true
        b.bluetoothLigaNaVez = 2
        b.nucleo.religarBluetoothSeFomosNos("ligou")
        b.agenda.avancar(60_000)
        assertEquals(2, b.ligacoesDoBluetooth())
        assertEquals(false, b.marcaBt)
        assertTrue(b.diario.nomes().contains("bluetooth: religado"))
    }

    @Test
    fun `A5 - ja ligado apaga a marca sem ligar`() {
        val b = Bancada()
        b.prefs["bluetoothStateOnPowerOff"] = true
        b.bluetooth = true
        b.nucleo.religarBluetoothSeFomosNos("ligou")
        assertEquals(0, b.ligacoesDoBluetooth())
        assertEquals(false, b.marcaBt)
    }

    @Test
    fun `A5 - carro desligou no meio - para mantendo a marca`() {
        val b = Bancada()
        b.prefs["bluetoothStateOnPowerOff"] = true
        b.nucleo.religarBluetoothSeFomosNos("ligou")
        b.carroDesligado = true
        b.agenda.avancar(60_000)
        assertEquals(1, b.ligacoesDoBluetooth())
        assertEquals(true, b.marcaBt)
    }

    @Test
    fun `A5 - marca apagada por outra restauracao - aborta`() {
        val b = Bancada()
        b.prefs["bluetoothStateOnPowerOff"] = true
        b.nucleo.religarBluetoothSeFomosNos("ligou")
        b.prefs["bluetoothStateOnPowerOff"] = false
        b.agenda.avancar(60_000)
        assertEquals(1, b.ligacoesDoBluetooth())
        assertTrue(b.diario.nomes().any { it.contains("abortado") })
    }

    @Test
    fun `A5 - re-partida perde a volta pendente, a marca fica`() {
        val b = Bancada()
        b.prefs["bluetoothStateOnPowerOff"] = true
        b.nucleo.religarBluetoothSeFomosNos("ligou")
        b.nucleo.cancelarRetentativas()
        b.agenda.avancar(60_000)
        assertEquals(1, b.ligacoesDoBluetooth())
        assertEquals(true, b.marcaBt)
    }

    @Test
    fun `A3 - receptor re-desliga sem marca so com carro desligado e a preferencia`() {
        val b = Bancada()
        b.prefs["disableBluetoothOnPowerOff"] = true
        b.nucleo.aoMudarBluetooth("STATE_CHANGED", 12, 0)
        assertTrue("carro ligado: nada", b.shell.comandos.isEmpty())

        b.carroDesligado = true
        b.nucleo.aoMudarBluetooth("STATE_CHANGED", 11, 0)
        assertTrue("não é STATE_ON: nada", b.shell.comandos.isEmpty())

        b.nucleo.aoMudarBluetooth("STATE_CHANGED", 12, 0)
        assertEquals(listOf("svc bluetooth disable"), b.shell.comandos)
        assertEquals("sem marca", null, b.marcaBt)

        val semPreferencia = Bancada()
        semPreferencia.carroDesligado = true
        semPreferencia.nucleo.aoMudarBluetooth("STATE_CHANGED", 12, 0)
        assertTrue(semPreferencia.shell.comandos.isEmpty())
        assertTrue(semPreferencia.diario.nomes().contains("bluetooth: receptor"))
    }

    // ── Hotspot ──

    @Test
    fun `A6 - reflexao 13, -1, e sem inteiro cai no receptor`() {
        val b = Bancada()
        b.reflexao = 13
        assertTrue(b.nucleo.hotspotLigado)
        b.reflexao = -1
        assertFalse(b.nucleo.hotspotLigado)
        b.reflexao = null
        assertFalse(b.nucleo.hotspotLigado)
        b.nucleo.aoMudarHotspot(13)
        assertTrue(b.nucleo.hotspotLigado)
    }

    @Test
    fun `A9 - carrier 1, 0 e ilegivel`() {
        val b = Bancada()
        b.carrier("1")
        assertTrue(b.nucleo.hotspotNoAr)
        b.carrier("0")
        assertFalse(b.nucleo.hotspotNoAr)
        b.carrier("")
        b.reflexao = 13
        assertTrue("vazio cai em hotspotLigado", b.nucleo.hotspotNoAr)
        b.reflexao = -1
        assertFalse(b.nucleo.hotspotNoAr)
    }

    @Test
    fun `A9 - interface vazia ou estranha nao vira caminho`() {
        val b = Bancada()
        b.shell.saidas["getprop sys.wifi.softap_interface_name"] = ""
        b.reflexao = 13
        assertTrue(b.nucleo.hotspotNoAr)
        b.shell.saidas["getprop sys.wifi.softap_interface_name"] = "../../x"
        assertTrue(b.nucleo.hotspotNoAr)
        assertTrue(b.shell.comandos.none { it.startsWith("cat") })
    }

    @Test
    fun `A7 D-S2 - guarda pelo sysfs`() {
        val b = Bancada()
        b.carrier("1")
        b.nucleo.desligarHotspotGuardando()
        assertEquals(true, b.marcaHotspot)
        assertEquals(listOf("desligar"), b.pedidosDoHotspot)

        val forDoAr = Bancada()
        forDoAr.carrier("0")
        forDoAr.nucleo.desligarHotspotGuardando()
        assertEquals(null, forDoAr.marcaHotspot)
        assertTrue(forDoAr.pedidosDoHotspot.isEmpty())
    }

    @Test
    fun `A7 - sem a decisao D-S2, como hoje (reflexao -1 nunca guarda)`() {
        val b = Bancada(DecisoesDoSistema(hotspotPeloSysfs = false))
        b.carrier("1")
        b.nucleo.desligarHotspotGuardando()
        assertEquals(null, b.marcaHotspot)
        assertTrue(b.pedidosDoHotspot.isEmpty())
    }

    @Test
    fun `A8 - religar hotspot, 8 x 4 s, para quando esta no ar`() {
        val b = Bancada()
        b.prefs["hotspotStateOnPowerOff"] = true
        b.carrier("0")
        b.nucleo.religarHotspotSeFomosNos()
        b.agenda.avancar(8_000)
        assertEquals(3, b.pedidosDoHotspot.size)
        b.carrier("1")
        b.agenda.avancar(60_000)
        assertEquals(3, b.pedidosDoHotspot.size)
        assertEquals(false, b.marcaHotspot)
        assertTrue("sem diário", b.diario.entradas.isEmpty())
    }

    @Test
    fun `A8 - desiste depois de 8 e apaga a marca`() {
        val b = Bancada()
        b.prefs["hotspotStateOnPowerOff"] = true
        b.carrier("0")
        b.nucleo.religarHotspotSeFomosNos()
        b.agenda.avancar(60_000)
        assertEquals(8, b.pedidosDoHotspot.size)
        assertEquals(false, b.marcaHotspot)
    }

    @Test
    fun `A10 - receptor guarda o estado e re-desliga sem marca`() {
        val b = Bancada()
        b.prefs["disableHotspotOnPowerOff"] = true
        b.nucleo.aoMudarHotspot(13)
        assertTrue(b.nucleo.ultimoEstadoDoHotspot)
        assertTrue("carro ligado: nada", b.pedidosDoHotspot.isEmpty())
        b.carroDesligado = true
        b.nucleo.aoMudarHotspot(13)
        assertEquals(listOf("desligar"), b.pedidosDoHotspot)
        assertEquals(null, b.marcaHotspot)
        b.nucleo.aoMudarHotspot(11)
        assertFalse(b.nucleo.ultimoEstadoDoHotspot)
    }

    @Test
    fun `A11 - sem alca do connectivity registra e nada`() {
        val b = Bancada()
        b.semAlca = true
        b.nucleo.ligarHotspot()
        b.nucleo.desligarHotspot()
        assertEquals(2, b.registro.erros.size)
    }

    @Test
    fun `A12 - semear o estado depois do pronto`() {
        val b = Bancada()
        b.carrier("1")
        b.nucleo.semearEstadoDoHotspot()
        assertTrue(b.nucleo.ultimoEstadoDoHotspot)
    }
}
