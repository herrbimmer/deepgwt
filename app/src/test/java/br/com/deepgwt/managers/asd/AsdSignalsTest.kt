// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.managers.asd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A cascata de fontes do ASD.
 *
 * O que importa aqui é a ORDEM e o descarte. A chave do pedal é um palpite não confirmado neste
 * carro; se ela responder, ganha de tudo — mas se ela devolver lixo (fora de 0..100), tem de sair
 * do caminho em vez de calar as fontes melhores. E a fonte vencedora precisa ser dita, porque é o
 * que a tela do ASD mostra para a inferência poder ser conferida no veículo.
 */
class AsdSignalsTest {

    @Test
    fun `o pedal ganha de todas as outras fontes`() {
        val r = AsdSignals.resolverAcelerador(
            pedalBruto = 42.0,
            saidaDeEnergiaBruta = 90.0,
            potenciaKw = 120.0,
            aceleracaoMs2 = 3.5
        )
        assertEquals(AsdSignals.Fonte.PEDAL, r.fonte)
        assertEquals(0.42, r.valor, 1e-9)
    }

    @Test
    fun `pedal fora de escala e descartado e a proxima fonte assume`() {
        // -1 e 1e9 são o que uma chave inexistente costuma devolver. Aceitar qualquer um dos dois
        // como "pedal" congelaria o som em 0% ou em 100% para sempre.
        listOf(-1.0, 1e9, Double.NaN).forEach { lixo ->
            val r = AsdSignals.resolverAcelerador(lixo, 60.0, null, null)
            assertEquals("lixo=$lixo", AsdSignals.Fonte.SAIDA_DE_ENERGIA, r.fonte)
            assertEquals(0.6, r.valor, 1e-9)
        }
    }

    @Test
    fun `sem pedal e sem saida de energia, a potencia assume`() {
        val r = AsdSignals.resolverAcelerador(null, null, AsdSignals.POTENCIA_DE_REFERENCIA_KW / 2, null)
        assertEquals(AsdSignals.Fonte.POTENCIA, r.fonte)
        assertEquals(0.5, r.valor, 1e-9)
    }

    @Test
    fun `potencia acima da referencia satura em 1`() {
        val r = AsdSignals.resolverAcelerador(null, null, 400.0, null)
        assertEquals(AsdSignals.Fonte.POTENCIA, r.fonte)
        assertEquals(1.0, r.valor, 1e-9)
    }

    @Test
    fun `a aceleracao e o ultimo recurso`() {
        val r = AsdSignals.resolverAcelerador(null, null, null, AsdSignals.ACELERACAO_DE_PE_NO_FUNDO)
        assertEquals(AsdSignals.Fonte.ACELERACAO, r.fonte)
        assertEquals(1.0, r.valor, 1e-9)
    }

    @Test
    fun `sem nenhuma fonte o acelerador e zero e diz que nao sabe`() {
        val r = AsdSignals.resolverAcelerador(null, null, null, null)
        assertEquals(AsdSignals.Fonte.NENHUMA, r.fonte)
        assertEquals(0.0, r.valor, 0.0)
    }

    @Test
    fun `saida de energia negativa nao vira pedal`() {
        // Regeneração aparece como saída negativa. Isso é freio, não acelerador — quem trata é
        // resolverFreio. Aqui só não pode virar pedal negativo nem pedal fantasma.
        val r = AsdSignals.resolverAcelerador(null, -40.0, null, null)
        assertEquals(AsdSignals.Fonte.SAIDA_DE_ENERGIA, r.fonte)
        assertEquals(0.0, r.valor, 1e-9)
    }

    @Test
    fun `aceleracao negativa nao vira pedal negativo`() {
        val r = AsdSignals.resolverAcelerador(null, null, null, -2.0)
        assertEquals(0.0, r.valor, 1e-9)
    }

    @Test
    fun `o freio sai da regeneracao quando ela responde`() {
        val r = AsdSignals.resolverFreio(regeneracaoBruta = 70.0, potenciaKw = 30.0, freioDeMao = false)
        assertEquals(AsdSignals.Fonte.REGENERACAO, r.fonte)
        assertEquals(0.7, r.valor, 1e-9)
    }

    @Test
    fun `sem regeneracao, potencia negativa vira freio`() {
        val r = AsdSignals.resolverFreio(
            regeneracaoBruta = null,
            potenciaKw = -AsdSignals.POTENCIA_DE_REFERENCIA_KW / 4,
            freioDeMao = false
        )
        assertEquals(AsdSignals.Fonte.REGENERACAO, r.fonte)
        assertEquals(0.25, r.valor, 1e-9)
    }

    @Test
    fun `potencia positiva nunca vira freio`() {
        val r = AsdSignals.resolverFreio(null, 80.0, false)
        assertEquals(AsdSignals.Fonte.NENHUMA, r.fonte)
        assertEquals(0.0, r.valor, 0.0)
    }

    @Test
    fun `freio de estacionamento e o ultimo recurso`() {
        val r = AsdSignals.resolverFreio(null, null, true)
        assertEquals(AsdSignals.Fonte.FREIO_DE_MAO, r.fonte)
        assertEquals(1.0, r.valor, 0.0)
    }

    @Test
    fun `regeneracao zerada nao conta como freio`() {
        // 0 é "não está regenerando", não "está freando com força 0" — a diferença importa porque
        // a fonte seguinte precisa ter a chance de responder.
        val r = AsdSignals.resolverFreio(0.0, -50.0, false)
        assertEquals(AsdSignals.Fonte.REGENERACAO, r.fonte)
        assertTrue("valor = ${r.valor}", r.valor > 0.0)
    }

    @Test
    fun `o modo de conducao vira cadencia do cambio`() {
        assertEquals(DrivetrainSimulator.Cadencia.NORMAL, AsdSignals.cadenciaParaModo("0"))
        assertEquals(DrivetrainSimulator.Cadencia.SPORT, AsdSignals.cadenciaParaModo("1"))
        assertEquals(DrivetrainSimulator.Cadencia.ECO, AsdSignals.cadenciaParaModo("2"))
        // Neve, areia e lama: modos de tração em que um câmbio de verdade sobe cedo e evita tranco.
        assertEquals(DrivetrainSimulator.Cadencia.ECO, AsdSignals.cadenciaParaModo("3"))
        assertEquals(DrivetrainSimulator.Cadencia.ECO, AsdSignals.cadenciaParaModo("4"))
        assertEquals(DrivetrainSimulator.Cadencia.ECO, AsdSignals.cadenciaParaModo("5"))
        assertEquals(DrivetrainSimulator.Cadencia.NORMAL, AsdSignals.cadenciaParaModo("11"))
    }

    @Test
    fun `modo desconhecido ou ausente cai em normal`() {
        assertEquals(DrivetrainSimulator.Cadencia.NORMAL, AsdSignals.cadenciaParaModo(null))
        assertEquals(DrivetrainSimulator.Cadencia.NORMAL, AsdSignals.cadenciaParaModo(""))
        assertEquals(DrivetrainSimulator.Cadencia.NORMAL, AsdSignals.cadenciaParaModo("banana"))
        assertEquals(DrivetrainSimulator.Cadencia.NORMAL, AsdSignals.cadenciaParaModo("99"))
    }

    @Test
    fun `o barramento manda float e a cadencia entende`() {
        // O gateway real entrega "1.0", não "1".
        assertEquals(DrivetrainSimulator.Cadencia.SPORT, AsdSignals.cadenciaParaModo("1.0"))
        assertEquals(DrivetrainSimulator.Cadencia.ECO, AsdSignals.cadenciaParaModo(" 2.0 "))
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
