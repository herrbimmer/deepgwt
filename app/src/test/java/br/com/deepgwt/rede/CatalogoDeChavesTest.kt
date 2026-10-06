// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.rede

import br.com.deepgwt.nucleo.Preferencias
import org.junit.Assert.assertEquals
import org.junit.Test

/** As chaves do módulo são as do catálogo do app (formato já gravado nos carros). */
class CatalogoDeChavesTest {

    @Test
    fun `chaves do modulo batem com o catalogo de preferencias`() {
        assertEquals(Preferencias.MOBILE_DATA_CONTROL_ENABLED.chave, ChavesDaRede.CONTROLE_LIGADO)
        assertEquals(Preferencias.BLOCK_CAR_MOBILE_DATA.chave, ChavesDaRede.CORTE_MANUAL)
        assertEquals(Preferencias.MOBILE_DATA_AUTOBLOCK.chave, ChavesDaRede.CORTE_SOB_CONSUMO)
        assertEquals(Preferencias.MOBILE_DATA_BLOCK_ON_WIFI.chave, ChavesDaRede.CORTE_NO_WIFI)
        assertEquals(Preferencias.MOBILE_DATA_BLOCK_ON_PROJECTION.chave, ChavesDaRede.CORTE_NA_PROJECAO)
        assertEquals(Preferencias.MOBILE_DATA_CYCLE_DAY.chave, ChavesDaRede.DIA_DO_CICLO)
        assertEquals(Preferencias.MOBILE_DATA_AUTOBLOCK_CAP_MB.chave, ChavesDaRede.TETO_DE_CONSUMO_MB)
        assertEquals(Preferencias.MOBILE_DATA_DISABLED_BY_APP.chave, ChavesDaRede.CORTADO_PELO_APP)
        assertEquals(Preferencias.MOBILE_DATA_TRAFFIC_ACCUM_BYTES.chave, ChavesDaRede.RESERVA_ACUMULADO)
        assertEquals(Preferencias.MOBILE_DATA_TRAFFIC_LAST_READING.chave, ChavesDaRede.RESERVA_ULTIMA_LEITURA)
        assertEquals(Preferencias.MOBILE_DATA_TRAFFIC_CYCLE_TAG.chave, ChavesDaRede.RESERVA_ETIQUETA)
        assertEquals(Preferencias.BLOCK_DATATRACK_TELEMETRY.chave, ChavesDaRede.TELEMETRIA_BLOQUEADA)
        assertEquals(Preferencias.DATATRACK_DISABLED_BY_APP.chave, ChavesDaRede.TELEMETRIA_CONGELADA_PELO_APP)
        assertEquals(Preferencias.ENABLE_HOT_ROUTER.chave, ChavesDaRede.ROTEADOR_LIGADO)
    }

    @Test
    fun `padroes da tabela`() {
        assertEquals(1, ChavesDaRede.DIA_DO_CICLO_PADRAO)
        assertEquals(2048, ChavesDaRede.TETO_PADRAO_MB)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
