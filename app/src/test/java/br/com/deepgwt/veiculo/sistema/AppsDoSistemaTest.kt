// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.sistema

import br.com.deepgwt.veiculo.comandos.PreferenciasEmMemoria
import br.com.deepgwt.veiculo.comandos.ShellFalso
import br.com.deepgwt.veiculo.motor.RegistroFalsoDoMotor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

/** motor-sistema.md §B (apps do sistema e debloat) e §C (escuta de voz). */
class AppsDoSistemaTest {

    private class Bancada {
        val eventos: MutableList<String> = Collections.synchronizedList(ArrayList())
        val shell = ShellFalso(eventos)
        val prefs = PreferenciasEmMemoria()
        val registro = RegistroFalsoDoMotor()
        @Volatile var permite = true
        val apps = NucleoDosApps(shell, prefs, { PortaoDeLicenca { permite } }, registro)

        /** Pacotes que `pm list packages -e` mostra como habilitados (todos os três estão instalados). */
        fun habilitados(vararg pacotes: String) {
            shell.saidas["pm list packages"] =
                "package:${NucleoDosApps.NAVEGACAO}\npackage:${NucleoDosApps.VOZ_TELA}\npackage:${NucleoDosApps.CLIMA}"
            for (p in listOf(
                NucleoDosApps.NAVEGACAO,
                NucleoDosApps.VOZ_TELA,
                NucleoDosApps.CLIMA,
            )) {
                shell.saidas["pm list packages -e $p"] = if (p in pacotes) "package:$p" else ""
            }
        }

        /** Só os comandos de pm/pkill que mexem em pacotes (sem as consultas). */
        fun mexidas(): List<String> = shell.comandos.filter {
            it.startsWith("pm disable-user") || it.startsWith("pkill") || it.startsWith("pm install-existing") || it.startsWith("pm enable")
        }
    }

    @Test
    fun `B1 - reativa o dono das telas, nunca desativa`() {
        val b = Bancada()
        b.apps.garantirAppsDoSistema()
        assertEquals(
            listOf("pm install-existing com.beantechs.multidisplay", "pm enable com.beantechs.multidisplay"),
            b.shell.comandos,
        )
    }

    @Test
    fun `B5 - linha exata - prefixo parecido nao conta`() {
        val b = Bancada()
        b.shell.saidas["pm list packages -e com.beantechs.weatherservice"] = "package:com.beantechs.weatherservice2"
        assertFalse(b.apps.habilitado("com.beantechs.weatherservice"))
        b.shell.saidas["pm list packages -e com.beantechs.weatherservice"] =
            "package:com.beantechs.weatherservice2\npackage:com.beantechs.weatherservice"
        assertTrue(b.apps.habilitado("com.beantechs.weatherservice"))
        b.shell.responder = { throw IllegalStateException("x") }
        assertTrue("exceção = habilitado", b.apps.habilitado("com.beantechs.weatherservice"))
    }

    @Test
    fun `B2 - semeia so as ausentes, a partir do estado real`() {
        val b = Bancada()
        b.habilitados(NucleoDosApps.NAVEGACAO)
        b.prefs["disableNativeWeather"] = false
        b.apps.garantirAppsDesativados()
        assertEquals(false, b.prefs.valores["disableNativeNavigation"])
        assertEquals(true, b.prefs.valores["disableNativeVoice"])
        assertEquals("já gravada: a preferência manda", false, b.prefs.valores["disableNativeWeather"])
        assertTrue(b.shell.comandos.none { it == "pm list packages -e com.beantechs.weatherservice" })
    }

    @Test
    fun `B2 - lista de pacotes vazia nao semeia nada`() {
        val b = Bancada()
        b.apps.garantirAppsDesativados()
        assertTrue(b.prefs.valores.isEmpty())
        assertTrue(b.mexidas().none { it.startsWith("pm disable-user") || it.startsWith("pkill") })
    }

    @Test
    fun `B2 - pacote que nao esta instalado semeia como ligado`() {
        val b = Bancada()
        b.shell.saidas["pm list packages"] = "package:${NucleoDosApps.NAVEGACAO}"
        b.shell.saidas["pm list packages -e ${NucleoDosApps.NAVEGACAO}"] = ""
        b.apps.garantirAppsDesativados()
        assertEquals("instalado e desligado", true, b.prefs.valores["disableNativeNavigation"])
        assertEquals("não instalado", false, b.prefs.valores["disableNativeVoice"])
    }

    @Test
    fun `B2 - aplica - desativar = disable-user e pkill - reativar = install-existing e enable - voz = dois pacotes`() {
        val b = Bancada()
        b.prefs["disableNativeNavigation"] = true
        b.prefs["disableNativeVoice"] = true
        b.prefs["disableNativeWeather"] = false
        b.apps.garantirAppsDesativados()
        assertEquals(
            listOf(
                "pm disable-user --user 0 com.neusoft.na.navigation",
                "pkill -9 -f com.neusoft.na.navigation",
                "pm disable-user --user 0 com.iflytek.cutefly.speechclient.hmi",
                "pkill -9 -f com.iflytek.cutefly.speechclient.hmi",
                "pm disable-user --user 0 com.beantechs.voiceclient",
                "pkill -9 -f com.beantechs.voiceclient",
                "pm install-existing com.beantechs.weatherservice",
                "pm enable com.beantechs.weatherservice",
            ),
            b.mexidas(),
        )
        assertTrue(b.shell.comandos.none { it.contains("disable-user") && it.contains("multidisplay") })
    }

    @Test
    fun `B2 - portao negado reativa tudo`() {
        val b = Bancada()
        b.permite = false
        b.prefs["disableNativeNavigation"] = true
        b.prefs["disableNativeVoice"] = true
        b.prefs["disableNativeWeather"] = true
        b.apps.garantirAppsDesativados()
        assertTrue(b.mexidas().none { it.startsWith("pm disable-user") || it.startsWith("pkill") })
        assertEquals(8, b.mexidas().size)
    }

    @Test
    fun `B2 - sem Shizuku nao semeia nem aplica`() {
        val b = Bancada()
        b.shell.disponivelAgora = false
        b.apps.garantirAppsDesativados()
        assertTrue(b.shell.comandos.isEmpty())
        assertTrue(b.prefs.valores.isEmpty())
    }

    @Test
    fun `B2 3 - aplica a escuta de voz no fim`() {
        val b = Bancada()
        b.prefs["disableNativeNavigation"] = false
        b.prefs["disableNativeVoice"] = false
        b.prefs["disableNativeWeather"] = false
        b.prefs["disableVoiceWakeUp"] = true
        b.apps.garantirAppsDesativados()
        val ultimaMexida = b.shell.comandos.indexOfLast { it.startsWith("pm enable") }
        val primeiraEscuta = b.shell.comandos.indexOfFirst { it.contains("appops") }
        assertTrue(primeiraEscuta > ultimaMexida)
    }

    // ── C. Escuta de voz ──

    @Test
    fun `C1 - nunca tocado`() {
        val b = Bancada()
        assertEquals("Interruptor nunca tocado — nada foi alterado.", b.apps.aplicarEscuta())
        assertTrue(b.shell.comandos.isEmpty())
    }

    @Test
    fun `C - vetor exato do relatorio (silenciando)`() {
        val b = Bancada()
        b.prefs["disableVoiceWakeUp"] = true
        b.shell.responder = { comando ->
            when {
                comando.contains("voiceprintservice") -> throw IllegalStateException("x")
                comando == "sh -c cmd appops get com.iflytek.cutefly.speechclient.hmi RECORD_AUDIO 2>&1" -> "RECORD_AUDIO: ignore"
                comando == "sh -c cmd appops get com.beantechs.voiceclient RECORD_AUDIO 2>&1" -> "RECORD_AUDIO: ignore"
                else -> null
            }
        }
        val relatorio = b.apps.aplicarEscuta()
        assertEquals(
            "Escuta desligada em 2 de 4 pacotes.\n" +
                "hmi: RECORD_AUDIO: ignore\n" +
                "voiceclient: RECORD_AUDIO: ignore\n" +
                "adapter: sem resposta do appops  <- NÃO obedeceu\n" +
                "voiceprintservice: falhou (x)",
            relatorio,
        )
    }

    @Test
    fun `C3 - comandos com 2gt1, na ordem, force-stop so ao silenciar`() {
        val b = Bancada()
        b.prefs["disableVoiceWakeUp"] = true
        b.apps.aplicarEscuta()
        val doHmi = b.shell.comandos.filter { it.contains("speechclient.hmi") }
        assertEquals(
            listOf(
                "sh -c cmd appops set com.iflytek.cutefly.speechclient.hmi RECORD_AUDIO ignore 2>&1",
                "sh -c pm revoke com.iflytek.cutefly.speechclient.hmi android.permission.RECORD_AUDIO 2>&1",
                "sh -c am force-stop com.iflytek.cutefly.speechclient.hmi 2>&1",
                "sh -c cmd appops get com.iflytek.cutefly.speechclient.hmi RECORD_AUDIO 2>&1",
            ),
            doHmi,
        )
        assertTrue(b.shell.comandos.all { it.startsWith("sh -c ") && it.endsWith(" 2>&1") })
    }

    @Test
    fun `C2 - portao negado devolve o microfone`() {
        val b = Bancada()
        b.prefs["disableVoiceWakeUp"] = true
        b.permite = false
        b.shell.responder = { comando -> if (comando.contains("appops get")) "RECORD_AUDIO: allow" else null }
        val relatorio = b.apps.aplicarEscuta()
        assertTrue(relatorio.startsWith("Escuta religada em 4 de 4 pacotes."))
        assertTrue(b.shell.comandos.none { it.contains("force-stop") || it.contains("revoke") })
        assertTrue(b.shell.comandos.any { it.contains("pm grant") })
        assertTrue(b.shell.comandos.any { it.contains("RECORD_AUDIO allow") })
    }

    @Test
    fun `C5 - devolver - resposta com ignore ou deny nao obedeceu - sem resposta obedeceu`() {
        val b = Bancada()
        b.prefs["disableVoiceWakeUp"] = false
        b.shell.responder = { comando ->
            when {
                comando.contains("appops get com.iflytek") -> "RECORD_AUDIO: deny"
                comando.contains("appops get com.beantechs.voiceclient") -> "RECORD_AUDIO: allow"
                else -> null
            }
        }
        val linhas = b.apps.aplicarEscuta().lines()
        assertEquals("Escuta religada em 3 de 4 pacotes.", linhas[0])
        assertEquals("hmi: RECORD_AUDIO: deny  <- NÃO obedeceu", linhas[1])
        assertEquals("adapter: sem resposta do appops", linhas[3])
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
