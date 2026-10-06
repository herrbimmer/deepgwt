// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui

import br.com.deepgwt.nucleo.DeepGwt
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import br.com.deepgwt.nucleo.Preferencias

/**
 * "Interface do DeepGWT": o estilo das telas do app na central (Alpha 7.2).
 *
 * O estilo muda duas coisas:
 *  - a tela inicial do app: [EstiloDaInterface.GRADE] é a grade de blocos de sempre;
 *    [EstiloDaInterface.BAVARO] é o menu em lista com barra vermelha e medalhão;
 *    [EstiloDaInterface.ESTUGARDA] (Alpha 7.3) é o carrossel de três itens sobre azul profundo;
 *    [EstiloDaInterface.CUPERTINO] (Alpha 9.5) é a tela de início de vidro no estilo da Apple;
 *  - a paleta das seções, entregue por [LocalPaletaDaInterface]. As cores do kit de interface
 *    leem dela, então todas as telas trocam de cor sem mudar uma linha.
 *
 * O QUE NÃO MUDA
 *
 * Só o `PrincipalActivity` (e a abertura do app) fornece a paleta. A barra inferior, o painel de
 * esportes, o HUD de gestos e o painel de instrumentos não fornecem, então continuam com o padrão
 * [PALETA_GRADE] — que tem exatamente as cores de antes desta rodada. Nada no contrato de temas
 * nem na ponte com o painel é tocado.
 */
enum class EstiloDaInterface(val id: String, val nome: String, val descricao: String) {
    GRADE(
        "grade",
        "Grade",
        "Blocos com ícone e acento azul. O visual de sempre do DeepGWT."
    ),
    BAVARO(
        "bavaro",
        "Bávaro",
        "Menu em lista com barra vermelha, medalhão do item e acabamento prateado, no estilo das centrais alemãs."
    ),
    ESTUGARDA(
        "estugarda",
        "Estugarda",
        "Carrossel de três itens grandes com ícone cromado, fundo azul profundo e pontos de página, no estilo das centrais de Stuttgart."
    ),
    CUPERTINO(
        "cupertino",
        "Cupertino",
        "Fundo grafite sóbrio, ícones escuros com o glifo em cor, anel de foco azul e um Dock de vidro."
    );

    companion object {
        /** Valor ausente ou desconhecido (um estilo de uma versão mais nova) volta para a grade. */
        fun deId(id: String?): EstiloDaInterface = entries.firstOrNull { it.id == id } ?: GRADE
    }
}

/** Cores de um estilo: os papéis de cor das telas, mais o fundo/cartão/borda clássicos e o `GlossyCard`. */
data class PaletaDaInterface(
    val ground: Color,
    val container: Color,
    val accent: Color,
    val onAccent: Color,
    val expandTint: Color,
    val hairline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val trackOff: Color,
    val thumbOff: Color,
    val attention: Color,
    val danger: Color,
    val indexActiveTint: Color,
    val sheenHighlight: Color,
    val accentGradient: List<Color>,
    /** Gradiente diagonal do `GlossyCard`, do canto de cima para o de baixo. */
    val cardGradient: List<Color>,
    val appBackground: Color,
    val appCardBackground: Color,
    val appBorder: Color
)

/** As cores de antes da 7.2, valor por valor. Mexer aqui muda a barra inferior e o painel de esportes. */
val PALETA_GRADE = PaletaDaInterface(
    ground = Color(0xFF0A0A0C),
    container = Color(0xFF12141A),
    accent = Color(0xFF4A9EFF),
    onAccent = Color(0xFF0A0A0C),
    expandTint = Color(0x0F4A9EFF),
    hairline = Color(0x0DFFFFFF),
    textPrimary = Color(0xFFF5F5F5),
    textSecondary = Color(0xFF8A93A3),
    textMuted = Color(0xFF4B5563),
    trackOff = Color(0xFF262A33),
    thumbOff = Color(0xFF6B7280),
    attention = Color(0xFFFBBF24),
    danger = Color(0xFFEF4444),
    indexActiveTint = Color(0x244A9EFF),
    sheenHighlight = Color(0x33FFFFFF),
    accentGradient = listOf(Color(0xFF2C6FE0), Color(0xFF4A9EFF)),
    cardGradient = listOf(Color(0xFF1B1F26), Color(0xFF0D0F13)),
    appBackground = Color(0xFF0A0A0A),
    appCardBackground = Color(0xFF13151A),
    appBorder = Color(0xFF1D2430)
)

/** Preto azulado de fundo, vermelho de destaque, texto em branco e cinza prateado. */
val PALETA_BAVARO = PaletaDaInterface(
    ground = Color(0xFF07080D),
    container = Color(0xFF15161D),
    accent = Color(0xFFE3262B),
    // Texto sobre o vermelho é branco; o escuro da grade quase some nele.
    onAccent = Color(0xFFFFFFFF),
    expandTint = Color(0x10E3262B),
    hairline = Color(0x16C8CCD4),
    textPrimary = Color(0xFFF2F3F6),
    textSecondary = Color(0xFF9A9EA8),
    textMuted = Color(0xFF585C66),
    trackOff = Color(0xFF2A2B33),
    thumbOff = Color(0xFF7A7E88),
    attention = Color(0xFFFBBF24),
    danger = Color(0xFFEF4444),
    indexActiveTint = Color(0x26E3262B),
    sheenHighlight = Color(0x38E6E8EE),
    accentGradient = listOf(Color(0xFF9E1117), Color(0xFFF03A3A)),
    cardGradient = listOf(Color(0xFF1D1E26), Color(0xFF0B0B10)),
    appBackground = Color(0xFF07080D),
    appCardBackground = Color(0xFF15161D),
    appBorder = Color(0xFF2A2B34)
)

/**
 * Azul profundo de fundo, azul-gelo luminoso de destaque e texto branco com cinza azulado. O
 * vermelho do estilo fica só no ponto da página atual do carrossel, como na referência.
 */
val PALETA_ESTUGARDA = PaletaDaInterface(
    ground = Color(0xFF050B18),
    container = Color(0xFF0E1B33),
    accent = Color(0xFF5CB8FF),
    onAccent = Color(0xFF04101F),
    expandTint = Color(0x125CB8FF),
    hairline = Color(0x1A9CC6FF),
    textPrimary = Color(0xFFF3F7FC),
    textSecondary = Color(0xFF9DB0CC),
    textMuted = Color(0xFF52648A),
    trackOff = Color(0xFF192944),
    thumbOff = Color(0xFF7888A4),
    attention = Color(0xFFFBBF24),
    danger = Color(0xFFEF4444),
    indexActiveTint = Color(0x265CB8FF),
    sheenHighlight = Color(0x33CFE6FF),
    accentGradient = listOf(Color(0xFF1B5DC2), Color(0xFF5CB8FF)),
    cardGradient = listOf(Color(0xFF15294B), Color(0xFF081329)),
    appBackground = Color(0xFF050B18),
    appCardBackground = Color(0xFF0E1B33),
    appBorder = Color(0xFF1D3963)
)

/**
 * As cores de sistema do macOS/iOS no modo escuro (Alpha 9.5; fundo neutro na 9.6): azul de
 * sistema de destaque, fundos em cinza-grafite e texto branco com o cinza translúcido de rótulo
 * secundário. O container fica
 * quase opaco (95%): é ele que diálogos e menus usam de fundo, e sem desfoque na central um vidro
 * transparente deixaria ler o que está atrás.
 */
val PALETA_CUPERTINO = PaletaDaInterface(
    ground = Color(0xFF0E0E10),
    container = Color(0xF21C1C1E),
    accent = Color(0xFF0A84FF),
    onAccent = Color(0xFFFFFFFF),
    expandTint = Color(0x140A84FF),
    hairline = Color(0x40545458),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0x99EBEBF5),
    textMuted = Color(0x4DEBEBF5),
    trackOff = Color(0xFF39393D),
    thumbOff = Color(0xFF8E8E93),
    attention = Color(0xFFFF9F0A),
    danger = Color(0xFFFF453A),
    indexActiveTint = Color(0x260A84FF),
    sheenHighlight = Color(0x40FFFFFF),
    accentGradient = listOf(Color(0xFF0A84FF), Color(0xFF5AC8FA)),
    cardGradient = listOf(Color(0xF22C2C2E), Color(0xF21C1C1E)),
    appBackground = Color(0xFF000000),
    appCardBackground = Color(0xF21C1C1E),
    appBorder = Color(0xFF38383A)
)

fun paletaDe(estilo: EstiloDaInterface): PaletaDaInterface = when (estilo) {
    EstiloDaInterface.GRADE -> PALETA_GRADE
    EstiloDaInterface.BAVARO -> PALETA_BAVARO
    EstiloDaInterface.ESTUGARDA -> PALETA_ESTUGARDA
    EstiloDaInterface.CUPERTINO -> PALETA_CUPERTINO
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

/**
 * Estática de propósito: trocar de estilo é raro e redesenha tudo de uma vez, e assim nenhuma
 * leitura de cor paga o custo de ser observada.
 */
val LocalPaletaDaInterface = staticCompositionLocalOf { PALETA_GRADE }

object InterfaceDoDeepGwt {
    private val chave = Preferencias.INTERFACE_STYLE.chave

    private val prefs: SharedPreferences? by lazy {
        runCatching {
            DeepGwt.contextoProtegido.getSharedPreferences("deepgwt_prefs", Context.MODE_PRIVATE)
        }.getOrNull()
    }

    /** O estilo em uso. Estado do Compose: quem lê é redesenhado quando muda. */
    var estilo by mutableStateOf(lerDoDisco())
        private set

    // Referência forte: o SharedPreferences guarda os ouvintes só por referência fraca.
    // Pega a troca feita pelo instalador (`/ajustes`) e pela troca de perfil de motorista.
    private val ouvinte = SharedPreferences.OnSharedPreferenceChangeListener { p, k ->
        if (k == chave) {
            val novo = EstiloDaInterface.deId(p.getString(chave, null))
            Handler(Looper.getMainLooper()).post { estilo = novo }
        }
    }

    init {
        prefs?.registerOnSharedPreferenceChangeListener(ouvinte)
    }

    private fun lerDoDisco(): EstiloDaInterface =
        EstiloDaInterface.deId(runCatching { prefs?.getString(chave, null) }.getOrNull())

    /** Grava e aplica na hora. */
    fun escolher(novo: EstiloDaInterface) {
        estilo = novo
        prefs?.edit()?.putString(chave, novo.id)?.apply()
    }
}

/**
 * Teclas do volante (modo mãos livres) e de um D-pad, antes do Compose. Quem mostra uma tela que
 * trata as setas por conta própria — o menu em lista do Bávaro, o carrossel do Estugarda, a tela de início do Cupertino — se registra aqui enquanto
 * está na tela; `PrincipalActivity.dispatchKeyEvent` pergunta primeiro a ele.
 */
object TeclasDaInterface {
    @Volatile
    var tratador: ((KeyEvent) -> Boolean)? = null
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
