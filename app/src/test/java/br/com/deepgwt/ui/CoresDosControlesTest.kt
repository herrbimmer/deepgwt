// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Cores clássicas fixas (T2), medidas clássicas (T3) e a escolha de cores de cada estilo de
 * interruptor (caso 52) e de controle deslizante (caso 53) a partir das 4 paletas. "Padrão" =
 * [Color.Unspecified] = o Material decide.
 */
class CoresDosControlesTest {

    private val paletas = listOf(PALETA_GRADE, PALETA_BAVARO, PALETA_ESTUGARDA, PALETA_CUPERTINO)
    private val padrao = Color.Unspecified
    private val branco = Color(0xFFFFFFFF)

    @Test
    fun `T2 cores classicas fixas`() {
        assertEquals(Color(0xFFFFFFFF), CoresClassicas.texto)
        assertEquals(Color(0xFFB0B8C4), CoresClassicas.textoSecundario)
        assertEquals(Color(0xFF808080), CoresClassicas.textoDesabilitado)
        assertEquals(Color(0xFF2A2F37), CoresClassicas.superficie)
        assertEquals(Color(0xFF3A3F47), CoresClassicas.botaoSecundario)
    }

    @Test
    fun `T3 medidas classicas`() {
        assertEquals(20.dp, MedidasClassicas.respiroDoCartao)
        assertEquals(8.dp, MedidasClassicas.margemDoCartao)
        assertEquals(1.dp, MedidasClassicas.espessuraDaBorda)
        assertEquals(12.dp, MedidasClassicas.cantoDoCartao)
        assertEquals(8.dp, MedidasClassicas.cantoDaBorda)
        assertEquals(8.dp, MedidasClassicas.cantoDoBotao)
        assertEquals(16.dp, MedidasClassicas.respiroDoConteudo)
    }

    @Test
    fun `interruptor AJUSTE`() = paletas.forEach { p ->
        assertEquals(
            EscolhaDoInterruptor(
                bolinhaLigado = p.onAccent,
                trilhoLigado = p.accent,
                bordaLigado = Color.Transparent,
                bolinhaDesligado = p.thumbOff,
                trilhoDesligado = p.trackOff,
                bordaDesligado = Color.Transparent,
                bolinhaLigadoDesabilitado = p.onAccent,
                trilhoLigadoDesabilitado = p.accent.copy(alpha = 0.5f),
                bolinhaDesligadoDesabilitado = p.thumbOff,
                trilhoDesligadoDesabilitado = p.trackOff.copy(alpha = 0.5f),
            ),
            EscolhaDeCores.doInterruptor(EstiloDoInterruptor.AJUSTE, p),
        )
    }

    @Test
    fun `alfa 0,5 substitui o alfa, nao multiplica`() {
        // O contêiner do Cupertino já é translúcido (F2): substituir dá 0,5, multiplicar daria ~0,47.
        val c = EscolhaDeCores.comMeioAlfa(PALETA_CUPERTINO.container)
        assertEquals(0.5f, c.alpha, 0.002f)
        assertEquals(PALETA_CUPERTINO.container.red, c.red, 0.0001f)
        assertEquals(0.5f, EscolhaDeCores.comMeioAlfa(PALETA_GRADE.accent).alpha, 0.002f)
    }

    @Test
    fun `interruptor QUATRO_CORES`() = paletas.forEach { p ->
        val e = EscolhaDeCores.doInterruptor(EstiloDoInterruptor.QUATRO_CORES, p)
        assertEquals(
            EscolhaDoInterruptor(
                bolinhaLigado = p.onAccent,
                trilhoLigado = p.accent,
                bolinhaDesligado = p.thumbOff,
                trilhoDesligado = p.trackOff,
            ),
            e,
        )
        // Borda e desabilitado ficam os do Material (D1).
        assertEquals(padrao, e.bordaLigado)
        assertEquals(padrao, e.bordaDesligado)
        assertEquals(padrao, e.trilhoLigadoDesabilitado)
    }

    @Test
    fun `interruptor CLARO`() = paletas.forEach { p ->
        assertEquals(
            EscolhaDoInterruptor(
                bolinhaLigado = p.textPrimary,
                trilhoLigado = p.accent,
                bordaLigado = Color.Transparent,
                bolinhaDesligado = p.textSecondary,
                trilhoDesligado = p.trackOff,
                bordaDesligado = Color.Transparent,
            ),
            EscolhaDeCores.doInterruptor(EstiloDoInterruptor.CLARO, p),
        )
    }

    @Test
    fun `interruptor SO_LIGADO`() = paletas.forEach { p ->
        assertEquals(
            EscolhaDoInterruptor(bolinhaLigado = p.onAccent, trilhoLigado = p.accent),
            EscolhaDeCores.doInterruptor(EstiloDoInterruptor.SO_LIGADO, p),
        )
    }

    @Test
    fun `interruptor LIGADO_BRANCO`() = paletas.forEach { p ->
        assertEquals(
            EscolhaDoInterruptor(bolinhaLigado = branco, trilhoLigado = p.accent),
            EscolhaDeCores.doInterruptor(EstiloDoInterruptor.LIGADO_BRANCO, p),
        )
    }

    @Test
    fun `interruptor CLASSICO`() = paletas.forEach { p ->
        assertEquals(
            EscolhaDoInterruptor(
                bolinhaLigado = Color(0xFFFFFFFF),
                trilhoLigado = p.accent,
                bordaLigado = Color.Transparent,
                bolinhaDesligado = Color(0xFFB0B8C4),
                trilhoDesligado = Color(0xFF3A3F47),
                bordaDesligado = Color.Transparent,
            ),
            EscolhaDeCores.doInterruptor(EstiloDoInterruptor.CLASSICO, p),
        )
    }

    @Test
    fun `a grade da as cores de sempre no estilo QUATRO_CORES`() {
        val e = EscolhaDeCores.doInterruptor(EstiloDoInterruptor.QUATRO_CORES, PALETA_GRADE)
        assertEquals(Color(0xFF0A0A0C), e.bolinhaLigado)
        assertEquals(Color(0xFF4A9EFF), e.trilhoLigado)
        assertEquals(Color(0xFF6B7280), e.bolinhaDesligado)
        assertEquals(Color(0xFF262A33), e.trilhoDesligado)
    }

    @Test
    fun `controle DESTAQUE`() = paletas.forEach { p ->
        assertEquals(
            EscolhaDoControleDeslizante(bolinha = p.accent, trilhoAtivo = p.accent, trilhoInativo = p.trackOff),
            EscolhaDeCores.doControleDeslizante(EstiloDoControleDeslizante.DESTAQUE, p),
        )
    }

    @Test
    fun `controle DESTAQUE_APAGADO`() = paletas.forEach { p ->
        assertEquals(
            EscolhaDoControleDeslizante(
                bolinha = p.accent,
                trilhoAtivo = p.accent,
                trilhoInativo = p.trackOff,
                bolinhaDesabilitada = p.trackOff,
                trilhoAtivoDesabilitado = p.trackOff,
                trilhoInativoDesabilitado = p.trackOff,
            ),
            EscolhaDeCores.doControleDeslizante(EstiloDoControleDeslizante.DESTAQUE_APAGADO, p),
        )
    }

    @Test
    fun `controle BOLINHA_BRANCA`() = paletas.forEach { p ->
        assertEquals(
            EscolhaDoControleDeslizante(bolinha = branco, trilhoAtivo = p.accent, trilhoInativo = p.trackOff),
            EscolhaDeCores.doControleDeslizante(EstiloDoControleDeslizante.BOLINHA_BRANCA, p),
        )
    }

    @Test
    fun `controle AJUSTE`() = paletas.forEach { p ->
        val e = EscolhaDeCores.doControleDeslizante(EstiloDoControleDeslizante.AJUSTE, p)
        assertEquals(
            EscolhaDoControleDeslizante(
                bolinha = branco,
                trilhoAtivo = p.accent,
                trilhoInativo = p.trackOff,
                marcaAtiva = Color.Transparent,
                marcaInativa = Color.Transparent,
                bolinhaDesabilitada = branco,
                trilhoAtivoDesabilitado = p.accent,
                trilhoInativoDesabilitado = p.trackOff,
            ),
            e,
        )
    }

    @Test
    fun `medidas da linha com interruptor`() {
        assertEquals(
            MedidasDaLinha(true, 20.dp, 16.dp, true, 15.sp, 2.dp, true, 12.sp, 12.dp),
            MedidasDaLinha.de(TamanhoDaLinha.COMPACTA),
        )
        assertEquals(
            MedidasDaLinha(true, 20.dp, 16.dp, true, 16.sp, 0.dp, true, 13.sp, 0.dp),
            MedidasDaLinha.de(TamanhoDaLinha.MEDIA),
        )
        assertEquals(
            MedidasDaLinha(true, 22.dp, 22.dp, true, 18.sp, 4.dp, true, 13.sp, 18.dp),
            MedidasDaLinha.de(TamanhoDaLinha.GRANDE),
        )
        assertEquals(
            MedidasDaLinha(false, 0.dp, 0.dp, false, 14.sp, 0.dp, false, 12.sp, 12.dp),
            MedidasDaLinha.de(TamanhoDaLinha.SIMPLES),
        )
    }

    @Test
    fun `caixa de aviso`() {
        assertEquals(Color(0xFF2A1414), corDoAviso(TipoDeAviso.ALERTA))
        assertEquals(Color(0xFF14202A), corDoAviso(TipoDeAviso.INFORMACAO))
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
