package br.com.opengw.perfis

import br.com.opengw.models.DriverProfile
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Perfis já gravados em carros que rodam o OPEN GW não podem virar lixo numa atualização.
 *
 * O formato mudou nesta rodada: o perfil deixou de guardar 16 chaves do veículo escolhidas a dedo
 * e passou a guardar o app inteiro. Quem já tinha perfil salvo tem o JSON antigo no
 * `opengw_prefs` — e a atualização chega pelo instalador sem aviso nenhum. Se a leitura do formato
 * antigo quebrar, a pessoa abre a tela de perfis e não encontra mais nada, sem explicação e sem
 * como recuperar.
 */
class FormatoDoPerfilTest {

    /** Mesma configuração do DriverProfileManager. */
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `perfil do formato antigo continua abrindo`() {
        val antigo = """
            {"name":"Miguel","seatMemorySlot":1,
             "settings":{"car.ev_setting.avas_enable":"1","car.drive.setting.drive_mode":"2"}}
        """.trimIndent()

        val perfil = json.decodeFromString<DriverProfile>(antigo)

        assertEquals("Miguel", perfil.name)
        assertEquals(1, perfil.seatMemorySlot)
        assertEquals(2, perfil.chavesDoVeiculo().size)
        assertEquals("2", perfil.chavesDoVeiculo()["car.drive.setting.drive_mode"])
        assertTrue(perfil.ehLegado)
        assertTrue(perfil.openGw.isEmpty())
    }

    @Test
    fun `perfil do formato novo vai e volta inteiro`() {
        val original = DriverProfile(
            name = "Ana",
            seatMemorySlot = 2,
            openGw = mapOf(
                "asdMasterEnabled" to "b:true",
                "bottomBarHeightDp" to "i:72",
                "activeCustomTheme" to "s:minimalist"
            ),
            veiculo = mapOf("sys.settings.audio.media_volume" to "18"),
            criadoEm = 1_700_000_000_000L,
            atualizadoEm = 1_700_000_500_000L
        )

        val voltou = json.decodeFromString<DriverProfile>(json.encodeToString(original))

        assertEquals(original, voltou)
        assertFalse(voltou.ehLegado)
        assertEquals(1, voltou.chavesDoVeiculo().size)
    }

    @Test
    fun `campo desconhecido de uma versao futura nao derruba a leitura`() {
        val doFuturo = """
            {"name":"Futuro","openGw":{"x":"b:true"},"veiculo":{},
             "algoQueAindaNaoInventamos":{"a":1},"outro":"texto"}
        """.trimIndent()

        val perfil = json.decodeFromString<DriverProfile>(doFuturo)

        assertEquals("Futuro", perfil.name)
        assertEquals(1, perfil.openGw.size)
    }

    @Test
    fun `o formato novo prefere veiculo a settings quando os dois existem`() {
        // Só acontece num perfil regravado por cima de um antigo; a resposta certa é a nova.
        val misto = """
            {"name":"Misto","settings":{"velho":"1"},"veiculo":{"novo":"2"}}
        """.trimIndent()

        val perfil = json.decodeFromString<DriverProfile>(misto)

        assertEquals(mapOf("novo" to "2"), perfil.chavesDoVeiculo())
    }

    @Test
    fun `nome com acento, espaco e aspas sobrevive ao JSON`() {
        val original = DriverProfile(name = "João \"Jota\" D'Ávila", openGw = mapOf("a" to "s:b"))
        val voltou = json.decodeFromString<DriverProfile>(json.encodeToString(original))
        assertEquals(original.name, voltou.name)
    }
}
