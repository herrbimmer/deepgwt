package br.com.opengw.ui.screens

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Quantas colunas a aba Configurações usa.
 *
 * A tela era um Card de 560dp centralizado numa central de 1920 px: uma tira estreita no meio com o
 * resto vazio. A conta que substituiu isso sai da largura REAL medida, então é ela que precisa estar
 * travada — é o que garante que a tela do carro ganhe várias colunas sem que uma janela estreita
 * fique com colunas espremidas.
 */
class BasicSettingsLayoutTest {

    @Test
    fun `tela larga do carro ganha varias colunas`() {
        // 1920 px com densidade 1 = 1920dp; com densidade 1,5 = 1280dp. Os dois casos têm de dar
        // mais de uma coluna, porque os dois sobram espaço de verdade.
        assertEquals(3, colunasParaLargura(1920.dp))
        assertEquals(2, colunasParaLargura(1280.dp))
    }

    @Test
    fun `janela estreita continua com uma coluna so`() {
        // Abaixo do mínimo por coluna, dividir só espremeria título e descrição.
        assertEquals(1, colunasParaLargura(430.dp))
        assertEquals(1, colunasParaLargura(0.dp))
    }

    @Test
    fun `o numero de colunas tem teto`() {
        // Sem teto, uma tela muito larga viraria uma planilha de blocos minúsculos.
        assertEquals(3, colunasParaLargura(4000.dp))
    }

    @Test
    fun `a conta e exatamente quantos minimos cabem`() {
        assertEquals(1, colunasParaLargura(100.dp, minimoPorColuna = 100.dp, maximo = 9))
        assertEquals(2, colunasParaLargura(250.dp, minimoPorColuna = 100.dp, maximo = 9))
        assertEquals(5, colunasParaLargura(500.dp, minimoPorColuna = 100.dp, maximo = 9))
        // Um mínimo inválido não pode virar divisão por zero.
        assertEquals(1, colunasParaLargura(500.dp, minimoPorColuna = 0.dp, maximo = 9))
    }
}
