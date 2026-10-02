package br.com.opengw.managers.asd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Opções do ASD da Alpha 6.3: quando ele toca, de onde sai a rotação, e a aritmética nova do
 * motor de áudio (crossfade de potência constante, interpolação cúbica e zumbido elétrico).
 */
class AsdOpcoesTest {

    // ── Motor a combustão ligado? ───────────────────────────────────────────────────────────

    @Test
    fun `o detector liga acima de 400 e so desliga abaixo de 150`() {
        val d = DetectorDeMotorACombustao()
        assertFalse(d.atualizar("0"))
        assertFalse(d.atualizar("380"))
        assertTrue(d.atualizar("820"))
        // Entre os dois limiares nada muda: é o que impede o som de piscar numa partida.
        assertTrue(d.atualizar("300"))
        assertTrue(d.atualizar("150"))
        assertFalse(d.atualizar("120"))
        assertFalse(d.atualizar("300"))
    }

    @Test
    fun `leitura ausente ou lixo mantem o estado anterior`() {
        val d = DetectorDeMotorACombustao()
        d.atualizar("1500")
        listOf(null, "", "abc", "-1", "NaN").forEach { lixo ->
            assertTrue("lixo=$lixo", d.atualizar(lixo))
        }
        d.zerar()
        assertFalse(d.girando)
    }

    // ── Deve soar? ──────────────────────────────────────────────────────────────────────────

    private fun soa(
        ativacao: AsdAtivacao = AsdAtivacao.READY,
        ignicao: Boolean = true,
        combustao: Boolean = false,
        silenciarEmP: Boolean = false,
        emP: Boolean = false,
        kmh: Double = 0.0
    ) = AsdDecisao.deveSoar(ativacao, ignicao, combustao, silenciarEmP, emP, kmh)

    @Test
    fun `em READY toca com o carro ligado mesmo com o motor a combustao parado`() {
        assertTrue(soa(combustao = false))
        assertFalse(soa(ignicao = false))
    }

    @Test
    fun `no modo motor a combustao so toca com ele girando`() {
        assertFalse(soa(ativacao = AsdAtivacao.MOTOR_A_COMBUSTAO, combustao = false))
        assertTrue(soa(ativacao = AsdAtivacao.MOTOR_A_COMBUSTAO, combustao = true))
        assertFalse(soa(ativacao = AsdAtivacao.MOTOR_A_COMBUSTAO, combustao = true, ignicao = false))
    }

    @Test
    fun `silenciar em P so vale parado`() {
        assertFalse(soa(silenciarEmP = true, emP = true, kmh = 0.0))
        assertTrue(soa(silenciarEmP = true, emP = true, kmh = 8.0))
        assertTrue(soa(silenciarEmP = true, emP = false, kmh = 0.0))
        assertTrue(soa(silenciarEmP = false, emP = true, kmh = 0.0))
    }

    @Test
    fun `preferencia ausente ou desconhecida cai no comportamento de sempre`() {
        assertEquals(AsdAtivacao.READY, AsdAtivacao.fromKey(null))
        assertEquals(AsdAtivacao.READY, AsdAtivacao.fromKey("XYZ"))
        assertEquals(AsdPerfilDeSom.COMBUSTAO, AsdPerfilDeSom.fromKey(null))
        assertEquals(AsdFonteDeRotacao.SIMULADA, AsdFonteDeRotacao.fromKey(""))
        assertEquals(AsdCadenciaEscolhida.AUTOMATICA, AsdCadenciaEscolhida.fromKey(null))
        assertNull(AsdCadenciaEscolhida.AUTOMATICA.fixa)
    }

    // ── Rotação real ────────────────────────────────────────────────────────────────────────

    @Test
    fun `giro real so vale com a opcao ligada, o motor girando e leitura plausivel`() {
        assertNull(AsdDecisao.rpmReal(AsdFonteDeRotacao.SIMULADA, true, "2350"))
        assertNull(AsdDecisao.rpmReal(AsdFonteDeRotacao.REAL, false, "2350"))
        assertEquals(2350.0, AsdDecisao.rpmReal(AsdFonteDeRotacao.REAL, true, "2350")!!, 0.0)
        assertEquals(2350.5, AsdDecisao.rpmReal(AsdFonteDeRotacao.REAL, true, " 2350.5 ")!!, 0.0)
        listOf(null, "", "0", "-5", "9000", "abc").forEach { lixo ->
            assertNull("lixo=$lixo", AsdDecisao.rpmReal(AsdFonteDeRotacao.REAL, true, lixo))
        }
    }

    @Test
    fun `carga sem simulador segue a convencao do simulador`() {
        assertEquals(0.5, AsdDecisao.cargaSemSimulador(0.5, 0.0, 60.0), 1e-9)
        assertEquals(-0.35, AsdDecisao.cargaSemSimulador(0.0, 0.0, 60.0), 1e-9)
        assertEquals(-0.8, AsdDecisao.cargaSemSimulador(0.0, 0.8, 60.0), 1e-9)
        assertEquals(-0.05, AsdDecisao.cargaSemSimulador(0.0, 0.0, 0.0), 1e-9)
    }

    // ── Motor de áudio ──────────────────────────────────────────────────────────────────────

    @Test
    fun `o crossfade soma um em potencia em todo o caminho`() {
        for (i in 0..100) {
            val mix = i / 100.0
            val baixo = AsdDsp.ganhoBaixo(mix)
            val alto = AsdDsp.ganhoAlto(mix)
            assertEquals("mix=$mix", 1.0, baixo * baixo + alto * alto, 1e-12)
        }
        assertEquals(1.0, AsdDsp.ganhoBaixo(0.0), 1e-12)
        assertEquals(0.0, AsdDsp.ganhoAlto(0.0), 1e-12)
        assertEquals(0.0, AsdDsp.ganhoBaixo(1.0), 1e-12)
        assertEquals(1.0, AsdDsp.ganhoAlto(1.0), 1e-12)
    }

    @Test
    fun `a interpolacao cubica passa pelas amostras e reproduz uma rampa`() {
        assertEquals(10.0, AsdDsp.hermite(3.0, 10.0, -4.0, 7.0, 0.0), 1e-12)
        assertEquals(-4.0, AsdDsp.hermite(3.0, 10.0, -4.0, 7.0, 1.0), 1e-12)
        // Numa reta a cúbica tem de ser exata — senão ela inventaria ondulação em sinal liso.
        for (i in 0..10) {
            val f = i / 10.0
            assertEquals(2.0 + f * 3.0, AsdDsp.hermite(-1.0, 2.0, 5.0, 8.0, f), 1e-12)
        }
    }

    @Test
    fun `o zumbido sobe com a velocidade e comeca no tom de parado`() {
        assertEquals(AsdDsp.ZUMBIDO_HZ_PARADO, AsdDsp.frequenciaDoZumbido(AsdDsp.rotacaoEletricaVirtual(0.0)), 1e-9)
        var anterior = 0.0
        for (kmh in 0..200 step 10) {
            val f = AsdDsp.frequenciaDoZumbido(AsdDsp.rotacaoEletricaVirtual(kmh.toDouble()))
            assertTrue("kmh=$kmh f=$f", f > anterior)
            anterior = f
        }
        // Em rodovia o zumbido é grave-médio, não apito.
        val a120 = AsdDsp.frequenciaDoZumbido(AsdDsp.rotacaoEletricaVirtual(120.0))
        assertTrue("120 km/h = $a120 Hz", a120 in 250.0..AsdDsp.ZUMBIDO_HZ_MAXIMO)
    }

    @Test
    fun `a escala de rpm equivalente vai e volta sem perda`() {
        for (hz in listOf(AsdDsp.ZUMBIDO_HZ_PARADO, 100.0, 250.0, AsdDsp.ZUMBIDO_HZ_MAXIMO)) {
            val rpm = AsdDsp.rpmEquivalente(hz)
            assertTrue(rpm in 800.0..7000.0)
            assertEquals(hz, AsdDsp.frequenciaDoRpmEquivalente(rpm), 1e-9)
        }
    }
}
