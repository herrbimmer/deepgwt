// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.hud

import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * O tema do HUD que o instalador monta e o carro aplica (Alpha 5.9).
 *
 * `exemplo.json` e `exemplo.normalizado.json` (test/resources/hud-tema) são lidos TAMBÉM pelo teste
 * do instalador (`instalador/scripts/test-hud-theme.mjs`): o carro e o editor têm de chegar ao mesmo
 * tema a partir do mesmo arquivo, senão a prévia do computador promete o que o HUD não desenha.
 */
class HudTemaTest {

    @After
    fun limpar() {
        HudSettings.restaurarPadrao()
    }

    private fun recurso(nome: String): String =
            javaClass.classLoader!!.getResource("hud-tema/$nome")!!.readText()

    @Test
    fun `o exemplo compartilhado com o instalador normaliza para o mesmo tema dos dois lados`() {
        val tema = HudTema.deJson(recurso("exemplo.json"))
        assertEquals(
                Json.parseToJsonElement(recurso("exemplo.normalizado.json")),
                Json.parseToJsonElement(tema.paraJson())
        )
    }

    @Test
    fun `o exemplo corrige em vez de recusar`() {
        val tema = HudTema.deJson(recurso("exemplo.json"))
        assertEquals("Aviação de teste", tema.nome)
        assertEquals(HudFonte.CONDENSADA, tema.fonte)
        // 450 cai no degrau de cem mais próximo, como o controle deslizante do carro.
        assertEquals(500, tema.peso)
        // 95% passa do teto do número da velocidade.
        assertEquals(HudSettings.SPEED_SIZE_MAX, tema.tamanhoVelocidade)
        assertTrue(tema.placaAEsquerda)
        assertEquals(26, tema.tamanhoPlaca)
        // Ordem do arquivo; página desconhecida e repetida somem; a que faltou entra no fim, ativa.
        assertEquals(
                listOf(
                        HudPaginas.Pagina.VELOCIDADE,
                        HudPaginas.Pagina.POWERTRAIN,
                        HudPaginas.Pagina.CONSUMO,
                        HudPaginas.Pagina.MIDIA,
                        HudPaginas.Pagina.VEICULO
                ),
                tema.paginas.map { it.pagina }
        )
        val velocidade = tema.paginas.first { it.pagina == HudPaginas.Pagina.VELOCIDADE }
        // A velocidade tem cor e tamanho próprios: os da página seriam uma segunda fonte da verdade.
        assertNull(velocidade.cor)
        assertNull(velocidade.escala)
        val motor = tema.paginas.first { it.pagina == HudPaginas.Pagina.POWERTRAIN }
        assertEquals("Motor", motor.titulo)
        assertEquals(listOf(HudCampo.POTENCIA, HudCampo.ROTACAO), motor.layout!!.linhaA)
        assertEquals(listOf(HudCampo.TORQUE, HudCampo.MARCHA, HudCampo.TRACAO), motor.layout!!.linhaB)
        // Igual ao de fábrica não é personalização: fica nulo e a preferência não guarda nada.
        val consumo = tema.paginas.first { it.pagina == HudPaginas.Pagina.CONSUMO }
        assertNull(consumo.titulo)
        assertNull(consumo.layout)
    }

    @Test
    fun `so recusa o que nao e tema de HUD`() {
        for (texto in listOf("", "[]", "isto não é json", """{"formato":"outro"}""", """{"nome":"sem formato"}""")) {
            try {
                HudTema.deJson(texto)
                fail("deveria recusar: $texto")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.isNotBlank())
            }
        }
        // O mínimo aceito: só o formato. O resto vira o HUD de fábrica.
        val minimo = HudTema.deJson("""{"formato":"deepgwt-hud-tema"}""")
        assertEquals(HudTema.padrao().copy(nome = "Tema sem nome"), minimo)
    }

    @Test
    fun `nenhuma pagina ativa vira todas ativas`() {
        val todasDesligadas =
                HudPaginas.Pagina.entries.joinToString(",") { """{"pagina":"${it.name}","ativa":false}""" }
        val tema = HudTema.deJson("""{"formato":"deepgwt-hud-tema","paginas":[$todasDesligadas]}""")
        assertTrue(tema.paginas.all { it.ativa })
        // Página que o arquivo não cita entra ATIVA — então duas desligadas e três ausentes deixam
        // três no rodízio, sem precisar do socorro acima.
        val parcial =
                HudTema.deJson(
                        """{"formato":"deepgwt-hud-tema","paginas":[
                            {"pagina":"VELOCIDADE","ativa":false},{"pagina":"MIDIA","ativa":false}]}"""
                )
        assertEquals(3, parcial.paginas.count { it.ativa })
    }

    @Test
    fun `pagina de numeros sem valor grande volta aos campos de fabrica`() {
        val tema =
                HudTema.deJson(
                        """{"formato":"deepgwt-hud-tema","paginas":[
                            {"pagina":"VEICULO","linhaA":[],"linhaB":["HORA"]},
                            {"pagina":"MIDIA","linhaA":["HORA"]}]}"""
                )
        assertNull(tema.paginas.first { it.pagina == HudPaginas.Pagina.VEICULO }.layout)
        // Mídia não é página de números: campos nela são ignorados.
        assertNull(tema.paginas.first { it.pagina == HudPaginas.Pagina.MIDIA }.layout)
    }

    @Test
    fun `o layout da preferencia ida e volta`() {
        val layout = HudLayout(listOf(HudCampo.HORA, HudCampo.AUTONOMIA), emptyList())
        assertEquals("HORA+AUTONOMIA/", layout.paraTexto())
        assertEquals(layout, HudLayout.deTexto(HudPaginas.Pagina.CONSUMO, layout.paraTexto()))
        // Nome que uma versão futura gravou e esta não conhece some sem derrubar o resto.
        assertEquals(
                HudLayout(listOf(HudCampo.HORA), listOf(HudCampo.TANQUE)),
                HudLayout.deTexto(HudPaginas.Pagina.CONSUMO, "HORA+NOVO/TANQUE")
        )
        assertNull(HudLayout.deTexto(HudPaginas.Pagina.MIDIA, "HORA/"))
    }

    @Test
    fun `os campos de fabrica sao os das paginas da 5_8`() {
        assertEquals(
                HudLayout(listOf(HudCampo.CONSUMO), listOf(HudCampo.AUTONOMIA, HudCampo.TANQUE, HudCampo.BATERIA)),
                HudLayout.padrao(HudPaginas.Pagina.CONSUMO)
        )
        assertEquals(
                HudLayout(listOf(HudCampo.POTENCIA, HudCampo.TORQUE), listOf(HudCampo.ROTACAO, HudCampo.MODO, HudCampo.TRACAO)),
                HudLayout.padrao(HudPaginas.Pagina.POWERTRAIN)
        )
        assertEquals(
                HudLayout(listOf(HudCampo.ODOMETRO, HudCampo.MARCHA), listOf(HudCampo.TEMP_FORA, HudCampo.TEMP_DENTRO, HudCampo.PNEUS)),
                HudLayout.padrao(HudPaginas.Pagina.VEICULO)
        )
        assertNull(HudLayout.padrao(HudPaginas.Pagina.VELOCIDADE))
        assertNull(HudLayout.padrao(HudPaginas.Pagina.MIDIA))
    }

    @Test
    fun `aplicar e ler o tema devolve o mesmo tema`() {
        val tema = HudTema.deJson(recurso("exemplo.json"))
        HudSettings.aplicarTema(tema)
        assertEquals(tema, HudSettings.temaAtual())
        assertEquals("Aviação de teste", HudSettings.nomeDoTema)
        assertEquals("Motor", HudSettings.tituloDaPagina(HudPaginas.Pagina.POWERTRAIN))
        assertEquals("Mídia", HudSettings.tituloDaPagina(HudPaginas.Pagina.MIDIA))
        assertFalse(HudPaginas.Pagina.MIDIA in HudSettings.paginasAtivas)
        // O rodízio segue o tema: mídia desligada não entra.
        assertEquals(
                listOf(
                        HudPaginas.Pagina.VELOCIDADE,
                        HudPaginas.Pagina.POWERTRAIN,
                        HudPaginas.Pagina.CONSUMO,
                        HudPaginas.Pagina.VEICULO
                ),
                HudPaginas.visiveis
        )
    }

    @Test
    fun `voltar ao padrao tira o tema e devolve o desenho de fabrica`() {
        HudSettings.aplicarTema(HudTema.deJson(recurso("exemplo.json")))
        HudSettings.restaurarPadrao()
        assertNull(HudSettings.nomeDoTema)
        assertEquals(HudSettings.COR_ROTULO_PADRAO, HudSettings.corRotulo)
        assertEquals(HudFonte.PADRAO, HudSettings.fonte)
        assertEquals(HudSettings.PESO_PADRAO, HudSettings.pesoDaFonte)
        assertTrue(HudSettings.mostrarCabecalho && HudSettings.mostrarPontos && HudSettings.unidadeVelocidade)
        assertFalse(HudSettings.placaAEsquerda)
        assertEquals(HudLayout.padrao(HudPaginas.Pagina.POWERTRAIN), HudSettings.layoutDaPagina(HudPaginas.Pagina.POWERTRAIN))
        assertEquals(HudPaginas.Pagina.entries.toList(), HudPaginas.visiveis)
        assertEquals(HudTema.padrao(), HudSettings.temaAtual())
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
