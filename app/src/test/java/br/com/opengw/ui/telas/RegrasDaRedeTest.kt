package br.com.opengw.ui.telas

import br.com.opengw.rede.ModoDoRoteador
import br.com.opengw.rede.MotivoDoCorte
import org.junit.Assert.assertEquals
import org.junit.Test

class RegrasDaRedeTest {

    @Test fun corteComControleDesligadoNaoCita_motivo() {
        assertEquals(
            "Controle desligado: o 4G fica como a fábrica deixou.",
            RegrasDaRede.textoDoCorte(controleLigado = false, motivo = MotivoDoCorte.MANUAL),
        )
    }

    @Test fun corteLiberadoEMotivos() {
        assertEquals("4G liberado agora.", RegrasDaRede.textoDoCorte(true, null))
        assertEquals("4G cortado agora (manual).", RegrasDaRede.textoDoCorte(true, MotivoDoCorte.MANUAL))
        assertEquals("4G cortado agora (WiFi).", RegrasDaRede.textoDoCorte(true, MotivoDoCorte.WIFI))
        assertEquals("4G cortado agora (AA/CarPlay).", RegrasDaRede.textoDoCorte(true, MotivoDoCorte.PROJECAO))
        assertEquals("4G cortado agora (consumo).", RegrasDaRede.textoDoCorte(true, MotivoDoCorte.CONSUMO))
    }

    @Test fun formatarMb() {
        assertEquals("0 MB", RegrasDaRede.formatarMb(0))
        assertEquals("1023 MB", RegrasDaRede.formatarMb(1023))
        assertEquals("1,0 GB", RegrasDaRede.formatarMb(1024))
        assertEquals("2,0 GB", RegrasDaRede.formatarMb(2048))
        assertEquals("20,0 GB", RegrasDaRede.formatarMb(20480))
    }

    @Test fun textoDoUso() {
        assertEquals(
            "Uso no ciclo 2026-9: 512 MB de 2,0 GB.",
            RegrasDaRede.textoDoUso(512, 2048, "2026-9"),
        )
    }

    @Test fun tetoArredondaParaOPassoELimita() {
        assertEquals(0, RegrasDaRede.teto(0f))
        assertEquals(0, RegrasDaRede.teto(100f))
        assertEquals(256, RegrasDaRede.teto(130f))
        assertEquals(2048, RegrasDaRede.teto(2050f))
        assertEquals(20480, RegrasDaRede.teto(99999f))
        assertEquals(0, RegrasDaRede.teto(-5f))
    }

    @Test fun passosDoTetoCobremAFaixa() {
        assertEquals(79, RegrasDaRede.PASSOS_DO_TETO)
    }

    @Test fun textosDoRoteadorCobremTodosOsModos() {
        ModoDoRoteador.values().forEach { assert(RegrasDaRede.textoDoRoteador(it).isNotBlank()) }
        assertEquals("Desligado.", RegrasDaRede.textoDoRoteador(ModoDoRoteador.DESLIGADO))
    }

    @Test fun textoDoHotspot() {
        assertEquals("Hotspot do carro: no ar.", RegrasDaRede.textoDoHotspot(true))
        assertEquals("Hotspot do carro: desligado.", RegrasDaRede.textoDoHotspot(false))
    }
}
