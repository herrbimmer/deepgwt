package br.com.opengw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * O "-1%" de bateria que aparecia no Painel Esportivo com o carro cheio.
 *
 * `soc_of_battery` devolve -1.0 quando a central não tem a leitura. A tela tentava cair para
 * `cur_battery_power_percentage` com `?:`, mas `?:` testa NULO, e "-1.0" é um Float válido — a
 * alternativa nunca era consultada. Na mesma captura do carro as duas chaves estavam lado a lado:
 * `soc_of_battery = -1.0` e `cur_battery_power_percentage = 38`.
 */
class SportsDisplayBatteryTest {

    @Test
    fun `o -1 da captura cai para a chave que respondeu`() {
        assertEquals(38f, resolveBatteryPercent(socOfBattery = -1f, curBatteryPercentage = 38f)!!, 0f)
    }

    @Test
    fun `a leitura boa continua ganhando`() {
        assertEquals(74f, resolveBatteryPercent(socOfBattery = 74f, curBatteryPercentage = 38f)!!, 0f)
    }

    @Test
    fun `nulo tambem cai para a alternativa`() {
        assertEquals(38f, resolveBatteryPercent(socOfBattery = null, curBatteryPercentage = 38f)!!, 0f)
    }

    @Test
    fun `acima de cem por cento nao e percentual`() {
        assertEquals(38f, resolveBatteryPercent(socOfBattery = 6553.5f, curBatteryPercentage = 38f)!!, 0f)
    }

    @Test
    fun `sem nenhuma leitura boa o painel mostra um traco`() {
        assertNull(resolveBatteryPercent(socOfBattery = -1f, curBatteryPercentage = -1f))
        assertNull(resolveBatteryPercent(socOfBattery = null, curBatteryPercentage = null))
    }

    @Test
    fun `zero por cento e uma leitura valida, nao ausencia`() {
        assertEquals(0f, resolveBatteryPercent(socOfBattery = 0f, curBatteryPercentage = 55f)!!, 0f)
    }
}
