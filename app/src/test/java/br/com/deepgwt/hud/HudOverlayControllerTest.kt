// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.hud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A camada do HUD: achar o display certo e não mentir sobre o resultado.
 *
 * O HUD é um display privado que não aparece para o app e que `screencap` não captura. Então as
 * duas únicas coisas que dá para verificar sem o carro são estas: que o id sai do despejo do
 * sistema em vez de ser chutado, e que a mensagem mostrada na tela distingue "o comando foi aceito"
 * de "a camada está no ar".
 */
class HudOverlayControllerTest {

    // ── Achar o display ────────────────────────────────────────────────────────────────────

    @Test
    fun `acha o HUD pelo uniqueId conhecido`() {
        val dump =
                """
                Logical Displays: size=2
                  Display 0:
                    mDisplayId=0
                    mPrimaryDisplayDeviceInfo=DisplayDeviceInfo{"Built-in Screen": uniqueId="local:0", 1920 x 720}
                  Display 1:
                    mDisplayId=4096
                    mPrimaryDisplayDeviceInfo=DisplayDeviceInfo{"HUD": uniqueId="local:5", 480 x 240}
                """.trimIndent()
        assertEquals(4096, HudOverlayController.parseHudDisplayId(dump))
    }

    @Test
    fun `acha o HUD pela resolucao quando o uniqueId muda`() {
        val dump =
                """
                  mDisplayId=0
                    real 1920 x 720
                  mDisplayId=7
                    real 480 x 240
                """.trimIndent()
        assertEquals(7, HudOverlayController.parseHudDisplayId(dump))
    }

    @Test
    fun `sem o HUD no despejo, nao chuta nenhum display`() {
        // Devolver "o primeiro que apareceu" abriria uma Activity em cima do cluster ou da tela
        // principal. Null faz o chamador cair no id conhecido desta central, que é uma escolha
        // declarada, e não um palpite.
        val dump =
                """
                  mDisplayId=0
                    real 1920 x 720
                  mDisplayId=3
                    real 1920 x 720
                """.trimIndent()
        assertNull(HudOverlayController.parseHudDisplayId(dump))
        assertNull(HudOverlayController.parseHudDisplayId(""))
    }

    @Test
    fun `resolucao antes do id no mesmo bloco nao vira id errado`() {
        // Se o formato inverter a ordem, não há id anterior para associar — e associar ao display
        // do bloco de cima seria pior do que não achar.
        val dump =
                """
                  real 480 x 240
                  mDisplayId=0
                """.trimIndent()
        assertNull(HudOverlayController.parseHudDisplayId(dump))
    }

    // ── O que a tela diz ao usuário ────────────────────────────────────────────────────────

    @Test
    fun `erro do am start aparece como erro`() {
        val texto =
                HudOverlayController.describeLaunch(
                        4096,
                        "Error: Activity not started, unable to resolve Intent"
                )
        assertTrue(texto.contains("recusou"))
        assertTrue(texto.contains("4096"))
    }

    @Test
    fun `display inexistente aparece como erro`() {
        assertTrue(
                HudOverlayController.describeLaunch(9, "java.lang.SecurityException: ...")
                        .contains("recusou")
        )
        assertTrue(
                HudOverlayController.describeLaunch(9, "Display 9 does not exist")
                        .contains("recusou")
        )
    }

    @Test
    fun `sucesso do comando nao e declarado como camada no ar`() {
        // `am start` responde "Starting:" mesmo quando nada aparece. A mensagem tem de pedir a
        // confirmação visual em vez de afirmar o que não foi observado.
        val texto =
                HudOverlayController.describeLaunch(
                        4096,
                        "Starting: Intent { cmp=br.com.deepgwt/.hud.HudOverlayActivity }"
                )
        assertTrue(texto.contains("Confira o HUD"))
        assertTrue(!texto.contains("no ar"))
    }

    @Test
    fun `saida vazia nao vira sucesso`() {
        val texto = HudOverlayController.describeLaunch(4096, "")
        assertTrue(texto.contains("sem resposta"))
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
