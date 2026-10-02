package br.com.opengw.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Os papéis de cor da paleta do estilo em uso (tabela T1), lidos de [LocalPaletaDaInterface] sem
 * transformação nenhuma. Só dentro de composição.
 *
 * Ler uma cor não assina mudança (o `CompositionLocal` é estático): trocar o estilo redesenha a
 * árvore inteira debaixo de quem fornece a paleta. Fora de uma janela que fornece, valem as cores
 * da Grade.
 *
 * Quem desenha em `Canvas`/`drawBehind`/`drawWithCache` lê a cor aqui, antes, e usa o valor
 * dentro do bloco de desenho.
 */
object Cores {
    val fundo: Color
        @Composable @ReadOnlyComposable get() = LocalPaletaDaInterface.current.ground

    /** Cartões e diálogos. */
    val conteiner: Color
        @Composable @ReadOnlyComposable get() = LocalPaletaDaInterface.current.container

    val destaque: Color
        @Composable @ReadOnlyComposable get() = LocalPaletaDaInterface.current.accent

    /** Texto e ícone sobre o [destaque]. */
    val sobreODestaque: Color
        @Composable @ReadOnlyComposable get() = LocalPaletaDaInterface.current.onAccent

    /** Fundo da área expandida de um ajuste. */
    val tintaDaExpansao: Color
        @Composable @ReadOnlyComposable get() = LocalPaletaDaInterface.current.expandTint

    /** Linha fina e divisórias. */
    val linhaFina: Color
        @Composable @ReadOnlyComposable get() = LocalPaletaDaInterface.current.hairline

    val textoPrincipal: Color
        @Composable @ReadOnlyComposable get() = LocalPaletaDaInterface.current.textPrimary

    val textoSecundario: Color
        @Composable @ReadOnlyComposable get() = LocalPaletaDaInterface.current.textSecondary

    val textoApagado: Color
        @Composable @ReadOnlyComposable get() = LocalPaletaDaInterface.current.textMuted

    /** Trilho do interruptor desligado. */
    val trilhoDesligado: Color
        @Composable @ReadOnlyComposable get() = LocalPaletaDaInterface.current.trackOff

    /** Bolinha do interruptor desligado. */
    val bolinhaDesligada: Color
        @Composable @ReadOnlyComposable get() = LocalPaletaDaInterface.current.thumbOff

    val atencao: Color
        @Composable @ReadOnlyComposable get() = LocalPaletaDaInterface.current.attention

    val perigo: Color
        @Composable @ReadOnlyComposable get() = LocalPaletaDaInterface.current.danger

    /** Fundo do item ativo (índice dos ajustes agrupados). */
    val realceDoItemAtivo: Color
        @Composable @ReadOnlyComposable get() = LocalPaletaDaInterface.current.indexActiveTint

    val reflexo: Color
        @Composable @ReadOnlyComposable get() = LocalPaletaDaInterface.current.sheenHighlight

    val gradienteDoDestaque: List<Color>
        @Composable @ReadOnlyComposable get() = LocalPaletaDaInterface.current.accentGradient

    val gradienteDoCartao: List<Color>
        @Composable @ReadOnlyComposable get() = LocalPaletaDaInterface.current.cardGradient
}

/**
 * A família clássica, das telas mais antigas: quatro cores vêm da paleta (T1, últimas linhas, e o
 * destaque), cinco são fixas (T2) e podem ser lidas fora de composição.
 *
 * Atenção: na Grade o fundo e o cartão clássicos NÃO são iguais a [Cores.fundo] e
 * [Cores.conteiner] (`0A0A0A` × `0A0A0C`; `13151A` × `12141A`).
 */
object CoresClassicas {
    val fundo: Color
        @Composable @ReadOnlyComposable get() = LocalPaletaDaInterface.current.appBackground

    val cartao: Color
        @Composable @ReadOnlyComposable get() = LocalPaletaDaInterface.current.appCardBackground

    val borda: Color
        @Composable @ReadOnlyComposable get() = LocalPaletaDaInterface.current.appBorder

    /** O mesmo de [Cores.destaque]. */
    val destaque: Color
        @Composable @ReadOnlyComposable get() = LocalPaletaDaInterface.current.accent

    val texto: Color = Color(0xFFFFFFFF)
    val textoSecundario: Color = Color(0xFFB0B8C4)
    val textoDesabilitado: Color = Color(0xFF808080)

    /** Fundo do campo clássico e de células. */
    val superficie: Color = Color(0xFF2A2F37)

    /** Botão secundário e trilho desligado clássico. */
    val botaoSecundario: Color = Color(0xFF3A3F47)
}

/** Cantos, bordas e margens das telas antigas (tabela T3). */
object MedidasClassicas {
    val respiroDoCartao: Dp = 20.dp
    val margemDoCartao: Dp = 8.dp
    val espessuraDaBorda: Dp = 1.dp
    val cantoDoCartao: Dp = 12.dp
    val cantoDaBorda: Dp = 8.dp
    val cantoDoBotao: Dp = 8.dp
    val respiroDoConteudo: Dp = 16.dp
}
