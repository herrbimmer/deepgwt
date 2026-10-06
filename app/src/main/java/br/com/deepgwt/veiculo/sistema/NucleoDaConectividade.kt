// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.sistema

import br.com.deepgwt.nucleo.Preferencias
import br.com.deepgwt.veiculo.comandos.Agendados
import br.com.deepgwt.veiculo.comandos.DiarioDoResto
import br.com.deepgwt.veiculo.comandos.PreferenciasDoMotor
import br.com.deepgwt.veiculo.comandos.ShellDoMotor
import br.com.deepgwt.veiculo.motor.Agenda
import br.com.deepgwt.veiculo.motor.RegistroDoMotor

/**
 * Bluetooth e hotspot (motor-sistema.md §A): desligar ao desligar o carro ou recolher os retrovisores
 * lembrando que estavam ligados, e religar na partida seguinte. Sem Android: as funções daqui rodam
 * na thread de quem chama (a linha do motor; nunca a principal, porque há shell).
 */
internal class NucleoDaConectividade(
    private val bluetooth: BluetoothDoCarro,
    private val hotspot: HotspotDoCarro,
    private val shell: ShellDoMotor,
    private val prefs: PreferenciasDoMotor,
    agenda: Agenda,
    private val carroDesligado: () -> Boolean,
    private val registro: RegistroDoMotor,
    private val diario: DiarioDoResto,
    private val decisoes: DecisoesDoSistema = DecisoesDoSistema(),
) {

    private val agendados = Agendados(agenda)

    /** O último estado que o receptor do hotspot viu (A.10); semeado depois do "pronto" (A.12). */
    @Volatile var ultimoEstadoDoHotspot: Boolean = false
        private set

    // ── Bluetooth ──

    /** A.1. */
    val bluetoothLigado: Boolean
        get() = try {
            bluetooth.ligado()
        } catch (e: Exception) {
            false
        }

    /** A.2. */
    fun ligarBluetooth(motivo: String) = comandoDoBluetooth("enable", motivo)

    /** A.2. */
    fun desligarBluetooth(motivo: String) = comandoDoBluetooth("disable", motivo)

    private fun comandoDoBluetooth(acao: String, motivo: String) {
        val comando = arrayOf("svc", "bluetooth", acao)
        try {
            val antes = bluetoothLigado
            val saida = shell.executar(comando)
            anotar(
                "bluetooth: $acao",
                mapOf("comando" to comando.joinToString(" "), "motivo" to motivo, "antes" to antes, "depois" to bluetoothLigado, "saida" to saida),
            )
        } catch (e: Exception) {
            anotar("bluetooth: $acao falhou", mapOf("motivo" to motivo, "erro" to e.javaClass.simpleName, "mensagem" to e.message))
        }
    }

    /** A.3: receptor do Bluetooth. Ligou com o carro desligado e a preferência → desliga, sem marca. */
    fun aoMudarBluetooth(acao: String?, estado: Int, estadoDaConexao: Int) {
        val desligado = carroDesligado()
        anotar(
            "bluetooth: receptor",
            mapOf("acao" to acao, "estado" to estado, "conexao" to estadoDaConexao, "carroDesligado" to desligado),
        )
        if (estado == BLUETOOTH_LIGADO && desligado &&
            prefs.booleano(Preferencias.DISABLE_BLUETOOTH_ON_POWER_OFF.chave, false)
        ) {
            desligarBluetooth("ligou com o carro desligado")
        }
    }

    /** A.4: desligado não apaga um "estava ligado" anterior (retrovisor e READY no mesmo ciclo). */
    fun desligarBluetoothGuardando(motivo: String) {
        val ligado = bluetoothLigado
        anotar("bluetooth: desligar guardando", mapOf("motivo" to motivo, "ligado" to ligado, "carroDesligado" to carroDesligado()))
        if (!ligado) return
        prefs.gravarBooleano(MARCA_DO_BLUETOOTH, true)
        desligarBluetooth(motivo)
    }

    /** A.5: 8 tentativas a cada 4 s; a primeira que der certo encerra. */
    fun religarBluetoothSeFomosNos(motivo: String) {
        val marca = prefs.booleano(MARCA_DO_BLUETOOTH, false)
        anotar("bluetooth: religar se fomos nós", mapOf("motivo" to motivo, "marca" to marca))
        if (!marca) return
        tentarReligar(BLUETOOTH, motivo, 0)
    }

    // ── Hotspot ──

    /** A.6: 13 = ligado; sem inteiro → o último estado do receptor. Nesta central dá -1 (falso). */
    val hotspotLigado: Boolean
        get() {
            val estado = try {
                hotspot.estadoPorReflexao()
            } catch (e: Exception) {
                null
            }
            return if (estado == null) ultimoEstadoDoHotspot else estado == HOTSPOT_LIGADO
        }

    /** A.9: o carrier do sysfs; ilegível → [hotspotLigado]. Shell: fora da principal. */
    val hotspotNoAr: Boolean
        get() = when (carrierDoHotspot()) {
            "1" -> true
            "0" -> false
            else -> hotspotLigado
        }

    /** A.11. */
    fun ligarHotspot() {
        val pediu = try {
            hotspot.ligar()
        } catch (e: Exception) {
            registro.erro("hotspot: ligar falhou", e)
            return
        }
        if (!pediu) registro.erro("hotspot: sem a alça do connectivity; nada feito")
    }

    /** A.11. */
    fun desligarHotspot() {
        val pediu = try {
            hotspot.desligar()
        } catch (e: Exception) {
            registro.erro("hotspot: desligar falhou", e)
            return
        }
        if (!pediu) registro.erro("hotspot: sem a alça do connectivity; nada feito")
    }

    /** A.7. */
    fun desligarHotspotGuardando() {
        val ligado = estadoParaGuardar()
        registro.info("hotspot: desligar guardando (ligado=$ligado, carroDesligado=${carroDesligado()})")
        if (!ligado) return
        prefs.gravarBooleano(MARCA_DO_HOTSPOT, true)
        desligarHotspot()
    }

    /** A.8: igual a A.5, sem diário. */
    fun religarHotspotSeFomosNos() {
        if (!prefs.booleano(MARCA_DO_HOTSPOT, false)) return
        tentarReligar(HOTSPOT, "partida", 0)
    }

    /** A.10: receptor do hotspot. */
    fun aoMudarHotspot(estado: Int) {
        when (estado) {
            HOTSPOT_LIGADO -> {
                ultimoEstadoDoHotspot = true
                if (carroDesligado() && prefs.booleano(Preferencias.DISABLE_HOTSPOT_ON_POWER_OFF.chave, false)) {
                    registro.info("hotspot: ligou com o carro desligado; desligo (sem marca)")
                    desligarHotspot()
                }
            }
            HOTSPOT_DESLIGADO -> ultimoEstadoDoHotspot = false
        }
    }

    /** A.12: depois do "pronto", o receptor só vê mudanças. */
    fun semearEstadoDoHotspot() {
        ultimoEstadoDoHotspot = estadoParaGuardar()
    }

    /** Re-partida (G.8): as voltas pendentes se perdem; as marcas ficam. */
    fun cancelarRetentativas() {
        agendados.cancelarTodos()
    }

    // ── Interno ──

    private class Restauracao(val nome: String, val marca: String, val comDiario: Boolean)

    private val BLUETOOTH = Restauracao("bluetooth", MARCA_DO_BLUETOOTH, comDiario = true)
    private val HOTSPOT = Restauracao("hotspot", MARCA_DO_HOTSPOT, comDiario = false)

    private fun estaLigado(r: Restauracao): Boolean = if (r === BLUETOOTH) bluetoothLigado else estadoParaGuardar()

    private fun ligar(r: Restauracao, motivo: String) {
        if (r === BLUETOOTH) ligarBluetooth(motivo) else ligarHotspot()
    }

    /** A.5 passo a passo (A.8 igual). Uma volta por vez: começar de novo troca a anterior. */
    private fun tentarReligar(r: Restauracao, motivo: String, n: Int) {
        if (!prefs.booleano(r.marca, false)) {
            contar(r, "${r.nome}: religar abortado (a marca sumiu)", mapOf("tentativa" to n))
            return
        }
        if (carroDesligado()) {
            contar(r, "${r.nome}: religar parado; o carro desligou (a marca fica)", mapOf("tentativa" to n))
            return
        }
        if (estaLigado(r)) {
            prefs.gravarBooleano(r.marca, false)
            contar(r, "${r.nome}: religado", mapOf("tentativa" to n, "motivo" to motivo))
            return
        }
        contar(r, "${r.nome}: tentativa $n", mapOf("motivo" to motivo))
        ligar(r, motivo)
        if (n + 1 < TENTATIVAS) {
            agendados.agendar(r.nome, ESPERA_ENTRE_TENTATIVAS_MS) { tentarReligar(r, motivo, n + 1) }
        } else {
            prefs.gravarBooleano(r.marca, false)
            contar(r, "${r.nome}: religar desistiu depois de $TENTATIVAS tentativas", mapOf("motivo" to motivo))
        }
    }

    /** D-S2: o estado que decide "guardar" e "já ligado" no hotspot. */
    private fun estadoParaGuardar(): Boolean = if (decisoes.hotspotPeloSysfs) hotspotNoAr else hotspotLigado

    private fun carrierDoHotspot(): String? =
        try {
            val interfaceDoHotspot = shell.executar(arrayOf("getprop", "sys.wifi.softap_interface_name")).trim()
            if (!NOME_DE_INTERFACE.matches(interfaceDoHotspot)) {
                null
            } else {
                shell.executar(arrayOf("cat", "/sys/class/net/$interfaceDoHotspot/carrier")).trim()
            }
        } catch (e: Exception) {
            registro.aviso("hotspot: carrier ilegível", e)
            null
        }

    private fun contar(r: Restauracao, evento: String, detalhes: Map<String, Any?>) {
        if (r.comDiario) anotar(evento, detalhes) else registro.info("$evento $detalhes")
    }

    private fun anotar(evento: String, detalhes: Map<String, Any?>) {
        registro.info("$evento $detalhes")
        try {
            diario(evento, detalhes)
        } catch (e: Exception) {
            registro.aviso("diário falhou em '$evento'", e)
        }
    }

    companion object {
        /** `BluetoothAdapter.STATE_ON`. */
        const val BLUETOOTH_LIGADO = 12
        const val HOTSPOT_LIGADO = 13
        const val HOTSPOT_DESLIGADO = 11
        const val TENTATIVAS = 8
        const val ESPERA_ENTRE_TENTATIVAS_MS = 4_000L

        val MARCA_DO_BLUETOOTH = Preferencias.BLUETOOTH_STATE_ON_POWER_OFF.chave
        val MARCA_DO_HOTSPOT = Preferencias.HOTSPOT_STATE_ON_POWER_OFF.chave

        private val NOME_DE_INTERFACE = Regex("[A-Za-z0-9_.-]+")
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
