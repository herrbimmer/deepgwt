package br.com.opengw.barra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vetores da especificação do estado (§H). */
class ContasDaBarraTest {

    private val c = ContasDaBarra

    @Test
    fun `overscan da barra caso 5`() {
        assertEquals(60, c.overscanDaBarraDp(60, null))
        assertEquals(140, c.overscanDaBarraDp(140, null))
        assertEquals(40, c.overscanDaBarraDp(40, null))
        assertEquals(0, c.overscanDaBarraDp(60, 0))
        assertEquals(30, c.overscanDaBarraDp(60, 30))
        assertEquals(60, c.overscanDaBarraDp(60, 90))
        assertEquals(60, c.overscanDaBarraDp(60, 180))
        assertEquals(0, c.overscanDaBarraDp(60, -20))
        for (a in 40..140 step 4) assertTrue(c.overscanDaBarraDp(a, null) >= a)
        // A fachada usa as mesmas contas.
        assertEquals(30, Barra.overscanDaBarraDp(60, 30))
    }

    @Test
    fun `limite do overscan caso 6`() {
        assertEquals(60, c.limitarOverscanDp(120, 60))
        assertEquals(45, c.limitarOverscanDp(45, 60))
        assertEquals(80, c.limitarOverscanDp(120, 60, 20))
        assertEquals(60, c.limitarOverscanDp(120, 60, -20))
        assertEquals(0, c.limitarOverscanDp(-10, 60))
        for (a in 40..140) assertEquals(a, c.limitarOverscanDp(a, a))
        assertEquals(0, c.limitarOverscanDp(10, -50))
    }

    @Test
    fun `overscan em px caso 7`() {
        for (d in listOf(1f, 1.5f, 2f)) {
            for (a in 40..140) assertEquals((a * d).toInt(), c.overscanEmPx(c.overscanDaBarraDp(a, null), d))
        }
        assertEquals(0, c.overscanEmPx(-20, 2f))
    }

    @Test
    fun `deslocamento escondido caso 8`() {
        assertEquals(-20, c.deslocamentoEscondidoPx(60, 40))
        assertEquals(-100, c.deslocamentoEscondidoPx(140, 40))
        assertEquals(0, c.deslocamentoEscondidoPx(40, 40))
        assertEquals(0, c.deslocamentoEscondidoPx(40, 80))
        for (a in 40..140) {
            val alturaPx = a * 2
            assertEquals(80, alturaPx + c.deslocamentoEscondidoPx(alturaPx, 80))
        }
    }

    @Test
    fun `fatias caso 9`() {
        assertEquals(
            FatiasDaBarra(listOf(Fatia.TEMPERATURA_MOTORISTA, Fatia.MODO), emptyList()),
            c.fatias(ControleDeTemperatura.UMA_ZONA, mostrarVolume = false, mostrarModo = true),
        )
        assertEquals(
            FatiasDaBarra(
                listOf(Fatia.TEMPERATURA_MOTORISTA, Fatia.MODO),
                listOf(Fatia.VOLUME, Fatia.TEMPERATURA_PASSAGEIRO),
            ),
            c.fatias(ControleDeTemperatura.DUAS_ZONAS, mostrarVolume = true, mostrarModo = true),
        )
        assertEquals(
            FatiasDaBarra(emptyList(), emptyList()),
            c.fatias(ControleDeTemperatura.DESLIGADO, mostrarVolume = false, mostrarModo = false),
        )
    }

    @Test
    fun `proxima temperatura caso 10`() {
        assertEquals(22.5f, c.proximaTemperatura(22.0f, 0.5f), 0.0001f)
        assertEquals(21.5f, c.proximaTemperatura(22.0f, -0.5f), 0.0001f)
        assertEquals(32.0f, c.proximaTemperatura(32.0f, 0.5f), 0.0001f)
        assertEquals(16.0f, c.proximaTemperatura(16.0f, -0.5f), 0.0001f)
        assertEquals(23.0f, c.proximaTemperatura(22.3f, 0.5f), 0.0001f)
    }

    @Test
    fun `valor pelo dedo caso 11`() {
        assertEquals(32f, c.valorPeloDedo(16f, 1600f, 1600f, 16f, 32f), 0.0001f)
        assertEquals(24f, c.valorPeloDedo(16f, 800f, 1600f, 16f, 32f), 0.0001f)
        assertEquals(20f, c.valorPeloDedo(24f, -400f, 1600f, 16f, 32f), 0.0001f)
        assertEquals(22f, c.valorPeloDedo(22f, 500f, 0f, 16f, 32f), 0.0001f)
        assertEquals(22f, c.valorPeloDedo(22f, 500f, 1600f, 32f, 16f), 0.0001f)
    }

    @Test
    fun `degrau caso 12`() {
        assertEquals(23.5f, c.encaixarNoDegrau(23.4713f, 16f, 32f, 0.5f), 0.0001f)
        assertEquals(23.5f, c.encaixarNoDegrau(23.6f, 16f, 32f, 0.5f), 0.0001f)
        assertEquals(24.0f, c.encaixarNoDegrau(23.9f, 16f, 32f, 0.5f), 0.0001f)
        assertEquals(32f, c.encaixarNoDegrau(99f, 16f, 32f, 0.5f), 0.0001f)
        assertEquals(16f, c.encaixarNoDegrau(-99f, 16f, 32f, 0.5f), 0.0001f)
        assertEquals(17f, c.encaixarNoDegrau(17.4f, 0f, 40f, 1f), 0.0001f)
        assertEquals(18f, c.encaixarNoDegrau(17.6f, 0f, 40f, 1f), 0.0001f)
        assertEquals(17.4f, c.encaixarNoDegrau(17.4f, 0f, 40f, 0f), 0.0001f)
    }

    @Test
    fun `cada degrau custa 50 px de dedo a partir da segunda troca`() {
        val trocas = ArrayList<Int>()
        var ultimo = c.encaixarNoDegrau(16f, 16f, 32f, 0.5f)
        for (px in 0..1600) {
            val v = c.encaixarNoDegrau(c.valorPeloDedo(16f, px.toFloat(), 1600f, 16f, 32f), 16f, 32f, 0.5f)
            if (v != ultimo) {
                trocas += px
                ultimo = v
            }
        }
        assertTrue(trocas.size > 3)
        for (i in 2 until trocas.size) {
            val passo = trocas[i] - trocas[i - 1]
            assertTrue("passo $passo", passo in 48..52)
        }
    }

    @Test
    fun `empurrao elastico caso 13`() {
        fun e(bruto: Float, teto: Float = 21f) = c.empurraoElastico(bruto, 16f, 32f, 1600f, teto)
        assertEquals(0f, e(24f), 0.01f)
        assertEquals(0f, e(32f), 0.01f)
        assertEquals(15.197f, e(33f), 0.01f)
        assertEquals(17.634f, e(34f), 0.01f)
        assertEquals(19.170f, e(36f), 0.01f)
        assertEquals(19.744f, e(38f), 0.01f)
        assertEquals(20.043f, e(40f), 0.01f)
        assertEquals(20.834f, e(80f), 0.01f)
        assertEquals(-15.197f, e(15f), 0.01f)
        assertEquals(-19.170f, e(12f), 0.01f)
        assertEquals(-20.511f, e(0f), 0.01f)
        assertEquals(0f, e(40f, teto = 0f), 0.0001f)
        assertEquals(0f, c.empurraoElastico(40f, 16f, 32f, 0f, 21f), 0.0001f)
        assertTrue(e(1_000_000f) < 21f)
    }

    @Test
    fun `temperatura valida caso 14`() {
        listOf("16.0", "23.5", "32.0").forEach { assertTrue(it, c.temperaturaValida(it)) }
        listOf(null, "--", "-1", "-1.0", "255", "85", "", "NaN", "Infinity").forEach {
            assertFalse(it.toString(), c.temperaturaValida(it))
        }
        assertEquals(23.5f, c.temperaturaOuNulo("23.5")!!, 0.0001f)
        assertNull(c.temperaturaOuNulo("--"))
    }

    @Test
    fun `item da coluna caso 15`() {
        fun i(acima: Float, item: Float = 69f, total: Int = 6) = c.itemDaColunaSobODedo(acima, item, total)
        assertEquals(-1, i(0f))
        assertEquals(-1, i(-1f))
        assertEquals(-1, i(-40f))
        assertEquals(0, i(1f))
        assertEquals(0, i(68f))
        assertEquals(1, i(69f))
        assertEquals(1, i(137f))
        assertEquals(2, i(138f))
        for (k in 0 until 6) assertEquals(k, i(k * 69f + 34.5f))
        assertEquals(5, i(415f))
        assertEquals(5, i(3450f))
        assertEquals(5, i(100000f))
        assertEquals(-1, i(120f, item = 0f))
        assertEquals(-1, i(120f, item = -5f))
        assertEquals(-1, i(120f, total = 0))
    }

    @Test
    fun `modos de conducao caso 16`() {
        assertEquals(listOf("2", "0", "1", "3", "4", "5"), c.MODOS_DE_CONDUCAO.map { it.codigo })
        assertEquals(listOf("Eco", "Normal", "Sport", "Neve", "Areia", "Lama"), c.MODOS_DE_CONDUCAO.map { it.rotulo })
        assertEquals("Eco", c.rotuloDoModo("2"))
        assertEquals("Sport", c.rotuloDoModo("1"))
        assertEquals("Neve", c.rotuloDoModo("3"))
        assertEquals("Areia", c.rotuloDoModo("4"))
        assertEquals("Lama", c.rotuloDoModo("5"))
        assertEquals("Normal", c.rotuloDoModo("0"))
        assertEquals("Normal", c.rotuloDoModo(""))
        assertEquals("Normal", c.rotuloDoModo(null))
        assertEquals("Normal", c.rotuloDoModo("9"))
    }

    @Test
    fun `faixa do volume caso 17`() {
        assertEquals(0..39, c.faixaDoVolume("{0,39}"))
        assertEquals(0..39, c.faixaDoVolume("0,39"))
        assertEquals(0..39, c.faixaDoVolume("0-39"))
        assertEquals(5..39, c.faixaDoVolume("{5,39}"))
        assertEquals(0..39, c.faixaDoVolume("39"))
        assertEquals(0..39, c.faixaDoVolume("{39,0}"))
        listOf(null, "", "   ", "{}", "0").forEach { assertEquals(0..30, c.faixaDoVolume(it)) }
        assertEquals(-5..10, c.faixaDoVolume("{-5,10}"))
    }

    @Test
    fun `gesto para cima caso 18`() {
        assertEquals(GestoParaCima.GAVETA, c.gestoParaCima(null))
        assertEquals(GestoParaCima.NADA, c.gestoParaCima("none"))
        listOf("", "gaveta", "Drawer", "painel", "{}").forEach { assertEquals(GestoParaCima.GAVETA, c.gestoParaCima(it)) }
        assertEquals(GestoParaCima.PAINEL, c.gestoParaCima("dashboard"))
        assertEquals(GestoParaCima.HOME_DE_FABRICA, c.gestoParaCima("haval_home"))
        assertEquals(GestoParaCima.LISTA_DE_APPS, c.gestoParaCima("app_launcher"))
        assertEquals(GestoParaCima.APP_ESPECIFICO, c.gestoParaCima("custom_app"))
    }

    @Test
    fun `temperatura gravada caso 19`() {
        assertEquals(ControleDeTemperatura.DESLIGADO, c.controleDeTemperatura("off", true, true))
        assertEquals(ControleDeTemperatura.UMA_ZONA, c.controleDeTemperatura("single", false, true))
        assertEquals(ControleDeTemperatura.DUAS_ZONAS, c.controleDeTemperatura("dual", false, false))
        for (g in listOf(null, "", "Dual", "duas zonas")) {
            assertEquals(ControleDeTemperatura.UMA_ZONA, c.controleDeTemperatura(g, true, false))
            assertEquals(ControleDeTemperatura.DUAS_ZONAS, c.controleDeTemperatura(g, false, true))
            assertEquals(ControleDeTemperatura.DESLIGADO, c.controleDeTemperatura(g, false, false))
            assertEquals(ControleDeTemperatura.DUAS_ZONAS, c.controleDeTemperatura(g, true, true))
        }
    }

    @Test
    fun `recuos medidos caso 20`() {
        val json = """{"com.beantechs.mediacenter":0,"com.beantechs.hvac":30}"""
        val mapa = c.lerRecuosMedidos(json)
        assertEquals(mapOf("com.beantechs.mediacenter" to 0, "com.beantechs.hvac" to 30), mapa)
        assertEquals(mapa, c.lerRecuosMedidos(c.gravarRecuosMedidos(mapa)))
        assertTrue(c.lerRecuosMedidos(null).isEmpty())
        assertTrue(c.lerRecuosMedidos("não é json").isEmpty())
    }

    @Test
    fun `recuo manual aceita offset e yOffset (E-D2)`() {
        assertEquals(RecuoManual(30, 10), c.recuoManual("""{"a":{"overscan":30,"offset":10}}""", "a"))
        assertEquals(RecuoManual(30, -5), c.recuoManual("""{"a":{"overscan":30,"yOffset":-5}}""", "a"))
        assertEquals(RecuoManual(30, 0), c.recuoManual("""{"a":{"overscan":30}}""", "a"))
        assertNull(c.recuoManual("""{"a":{"offset":30}}""", "a"))
        assertNull(c.recuoManual("""{"a":{"overscan":30}}""", "b"))
        assertNull(c.recuoManual("lixo", "a"))
        assertNull(c.recuoManual(null, "a"))
    }

    @Test
    fun `altura lida fica em 40 a 140 (E-D1)`() {
        assertEquals(40, c.alturaValida(10))
        assertEquals(140, c.alturaValida(300))
        assertEquals(60, c.alturaValida(60))
    }

    @Test
    fun `regras do que esta aberto`() {
        // Algo pede toque: o cartão do carro, o ajuste rápido e a coluna não entram.
        assertFalse(c.algoPedeToque(false, false, false, false, false, null, false))
        assertTrue(c.algoPedeToque(true, false, false, false, false, null, false))
        assertTrue(c.algoPedeToque(false, true, false, false, false, null, false))
        assertTrue(c.algoPedeToque(false, false, false, false, true, null, false))
        assertTrue(c.algoPedeToque(false, false, false, false, false, FuncaoDoAjuste.VOLUME, false))
        assertFalse(c.algoPedeToque(false, false, false, false, false, FuncaoDoAjuste.VOLUME, true))
        // Algo aberto: qualquer um, inclusive o cartão do carro.
        val ajuste = AjusteRapido(FuncaoDoAjuste.VOLUME, 1f, 0f, 2f, 1f, 0f, 0f, 0f)
        assertFalse(c.algoAberto(false, false, false, false, false, null, null, null))
        assertTrue(c.algoAberto(false, false, false, false, false, FuncaoDoAjuste.VOLUME, null, null))
        assertTrue(c.algoAberto(false, false, false, false, false, null, ajuste, null))
        assertTrue(c.algoAberto(false, false, false, false, false, null, null, EscolhaDeModo(0f, -1, "0")))
        assertTrue(c.algoAberto(true, false, false, false, false, null, null, null))
        assertEquals(0.5f, ajuste.fracao, 0.0001f)
        assertEquals(0f, ajuste.copy(minimo = 2f).fracao, 0.0001f)
    }

    @Test
    fun `cores de reserva da capa`() {
        val r = QuatroCores.RESERVA
        assertEquals(0xFF1F333A.toInt(), r.principal)
        assertEquals(0xFF41351F.toInt(), r.secundaria)
        assertEquals(0xFF66E3FF.toInt(), r.destaque)
        assertEquals(0xFF05070A.toInt(), r.escura)
    }
}
