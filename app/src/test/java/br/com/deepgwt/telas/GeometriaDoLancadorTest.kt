// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.telas

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Parte 2, seção F, e parte 3, C2: vetores V5 (limites) e V6 (recuo). */
class GeometriaDoLancadorTest {

    /** Central: densidade 1, display 0 = 1920×720, faixa máxima 150, barra 60 dp ligada, sem recuos gravados. */
    private fun central(
        barraPersistente: Boolean = true,
        densidade: Float = 1f,
        alturaDaBarra: Int = 60,
        painelLigado: Boolean = true,
        nomeDoTema: String = "Básico",
        areaDoTema: IntArray? = null,
        areaGravada: (String) -> IntArray? = { null },
        recuosPorApp: String? = null,
        recuosMedidos: String? = null,
        recuoGlobal: Int? = null,
        mostrador: String? = null,
        deslocamentoLigado: Boolean = false,
        deslocamento: Int = 135,
    ) = AjustesDaGeometria(
        densidade = densidade, alturaDaBarraDp = alturaDaBarra, barraPersistente = barraPersistente,
        painelLigado = painelLigado, nomeDoTema = nomeDoTema, areaDoTema = areaDoTema, recuosPorApp = recuosPorApp,
        recuosMedidos = recuosMedidos, recuoGlobal = recuoGlobal, mostradorDoPainel = mostrador,
        deslocamentoDoAaLigado = deslocamentoLigado, deslocamentoDoAa = deslocamento, areaGravada = areaGravada,
    )

    private fun spotify(display: Int = 0, x: Int = 0, y: Int = 0, l: Int = 1920, a: Int = 720, ignorar: Boolean = false) =
        ConfigDoApp("com.spotify.music", "com.spotify.music.MainActivity", display, x, y, l, a, ignorarAreaDoTema = ignorar)

    private fun lim(c: ConfigDoApp, a: AjustesDaGeometria = central()) = GeometriaDoLancador.limitesEfetivos(c, a)

    @Test
    fun `V5 - display 0 com a barra`() {
        assertArrayEquals(intArrayOf(0, 0, 1920, 660), lim(spotify()))
        assertArrayEquals(intArrayOf(0, 0, 1920, 660), lim(spotify(a = 660)))
        assertArrayEquals(intArrayOf(0, 0, 1920, 660), lim(spotify(a = 571)))
        assertArrayEquals("D3", intArrayOf(0, 0, 1920, 509), lim(spotify(a = 569)))
        assertArrayEquals(intArrayOf(0, 100, 1920, 660), lim(spotify(y = 100, a = 620)))
        assertArrayEquals(intArrayOf(0, 0, 1920, 720), lim(spotify(), central(barraPersistente = false)))
        assertArrayEquals(intArrayOf(0, 0, 1920, 720), lim(spotify(ignorar = true)))
    }

    @Test
    fun `V5 - displays do painel e o tema`() {
        for (tema in listOf("Básico", "Default", "Light")) {
            assertArrayEquals(tema, intArrayOf(0, 62, 1920, 658), lim(spotify(display = 3), central(nomeDoTema = tema)))
        }
        assertArrayEquals(intArrayOf(100, 50, 900, 450), lim(spotify(display = 1), central(areaDoTema = intArrayOf(100, 50, 800, 400))))
        val gravada = { nome: String -> if (nome == "Meu Tema") intArrayOf(10, 20, 300, 200) else null }
        assertArrayEquals(intArrayOf(10, 20, 310, 220), lim(spotify(display = 3), central(nomeDoTema = "Meu Tema", areaGravada = gravada)))
        assertArrayEquals(intArrayOf(0, 0, 1920, 720), lim(spotify(display = 3), central(nomeDoTema = "Outro", areaGravada = gravada)))
        assertArrayEquals(intArrayOf(0, 0, 1920, 720), lim(spotify(display = 3), central(painelLigado = false)))
    }

    @Test
    fun `V5 - Android Auto e CarPlay`() {
        val aa = { d: Int -> ConfigDoApp("com.ts.androidauto.app", "x", d, 0, 0, 1920, 720) }
        assertArrayEquals(intArrayOf(135, 0, 1920, 720), lim(aa(3), central(mostrador = "Mapa")))
        assertArrayEquals(intArrayOf(135, 0, 1920, 720), lim(aa(3), central(mostrador = "Mapa Limpo")))
        assertArrayEquals(intArrayOf(0, 0, 1920, 720), lim(aa(3), central(mostrador = "Normal")))
        assertArrayEquals(intArrayOf(1720, 0, 1920, 720), lim(aa(3), central(deslocamentoLigado = true, deslocamento = 5000)))
        assertArrayEquals(intArrayOf(0, 0, 1920, 720), lim(aa(3), central(deslocamentoLigado = true, deslocamento = -5)))
        assertArrayEquals(intArrayOf(0, 0, 1920, 720), lim(aa(0), central(mostrador = "Mapa")))
        assertArrayEquals(intArrayOf(0, 0, 1920, 720), lim(ConfigDoApp("com.ts.carplay.app", "x", 3, 5, 5, 10, 10)))
    }

    @Test
    fun `V5 - tela cheia no display 0 e densidade do emulador`() {
        assertArrayEquals(intArrayOf(0, 0, 1920, 660), GeometriaDoLancador.telaCheiaNoDisplay0("com.spotify.music", central()))
        assertArrayEquals(intArrayOf(0, 0, 1920, 720), GeometriaDoLancador.telaCheiaNoDisplay0("com.ts.carplay.app:remote", central()))
        assertEquals(220, GeometriaDoLancador.faixaMaxima(1.5f))
        assertEquals(150, GeometriaDoLancador.faixaMaxima(1f))
        assertEquals(90, GeometriaDoLancador.recuoPx("com.spotify.music", central(densidade = 1.5f)))
        assertArrayEquals(intArrayOf(0, 0, 1920, 630), lim(spotify(), central(densidade = 1.5f)))
    }

    private fun recuo(pacote: String = "p", a: AjustesDaGeometria = central()) = GeometriaDoLancador.recuoPx(pacote, a)

    @Test
    fun `V6 - recuo do app`() {
        assertEquals(60, recuo())
        assertEquals(30, recuo(a = central(recuoGlobal = 30)))
        assertEquals(60, recuo(a = central(recuoGlobal = 90)))
        assertEquals(0, recuo(a = central(recuoGlobal = 0)))
        assertEquals(60, recuo(a = central(recuosPorApp = """{"p":{"overscan":120,"yOffset":0}}""")))
        assertEquals(40, recuo(a = central(recuosPorApp = """{"p":{"overscan":40}}""")))
        assertEquals(90, recuo(a = central(recuosPorApp = """{"p":{"overscan":120,"yOffset":30}}""")))
        assertEquals(0, recuo(a = central(recuosPorApp = """{"p":{"overscan":-5}}""")))
        assertEquals("D5", 60, recuo(a = central(recuosPorApp = """{"p":{"overscan":120,"offset":30}}""")))
        assertEquals(13, recuo(a = central(recuosMedidos = """{"p":12.6}""")))
        assertEquals(60, recuo(a = central(recuosMedidos = """{"p":80}""")))
        assertEquals(30, recuo(a = central(recuosMedidos = """{"p":80}""", recuoGlobal = 30)))
        assertEquals(60, recuo(a = central(recuosMedidos = """{"p":-1}""")))
        assertEquals(60, recuo("com.google.android.apps.messaging"))
        assertEquals(40, recuo("com.google.android.apps.messaging", central(alturaDaBarra = 40)))
        assertEquals("D4", 60, recuo("deezer.android.app", central(alturaDaBarra = 80)))
        assertEquals(30, recuo("com.ts.androidauto.app"))
        assertEquals(
            40,
            recuo(
                "com.ts.androidauto.app",
                central(recuosPorApp = """{"com.ts.androidauto.app":{"overscan":40}}""", recuosMedidos = """{"com.ts.androidauto.app":10}"""),
            ),
        )
    }

    @Test
    fun `D5 - correcao pronta para quando o dono decidir`() {
        val a = central(recuosPorApp = """{"p":{"overscan":120,"offset":30}}""")
        assertEquals(90, GeometriaDoLancador.recuoDp("p", a, aceitarOffset = true))
    }

    @Test
    fun `JSON invalido nos recuos segue a prioridade`() {
        val registros = mutableListOf<String>()
        val a = AjustesDaGeometria(recuosPorApp = "{lixo", recuosMedidos = """{"p":20}""", registrar = { registros += it })
        assertEquals(20, GeometriaDoLancador.recuoDp("p", a))
        assertEquals(1, registros.size)
        assertEquals(60, GeometriaDoLancador.recuoDp("p", AjustesDaGeometria(recuosMedidos = "[1,2")))
    }

    @Test
    fun `F5 - recuo aplicado a um fundo`() {
        assertEquals(660, GeometriaDoLancador.aplicarRecuo(720, "p", central()))
        assertEquals(720, GeometriaDoLancador.aplicarRecuo(720, "p", central(barraPersistente = false)))
        assertEquals(720, GeometriaDoLancador.aplicarRecuo(720, "p", central(recuoGlobal = 0)))
        assertEquals(50, GeometriaDoLancador.aplicarRecuo(50, "p", central()))
    }

    @Test
    fun `volta ao 0 desconta a barra uma vez so`() {
        assertArrayEquals(intArrayOf(0, 0, 1920, 660), GeometriaDoLancador.limitesDeVoltaAo0("p", intArrayOf(0, 0, 1920, 660), central()))
        assertArrayEquals(intArrayOf(10, 0, 900, 440), GeometriaDoLancador.limitesDeVoltaAo0("p", intArrayOf(10, 0, 900, 500), central()))
        assertArrayEquals(intArrayOf(0, 0, 1920, 660), GeometriaDoLancador.limitesDeVoltaAo0("p", null, central()))
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
