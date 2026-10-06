package br.com.deepgwt.tela3d.catalogo

import android.graphics.drawable.Drawable
import android.view.MotionEvent
import androidx.annotation.ColorInt

// Costuras do módulo catálogo com os vizinhos (ponte, faixa, janelas, mídia). Tipos públicos.

/** Tipo de projeção para o ícone (o módulo de mídia tem o seu próprio enum, com NENHUMA). */
enum class TipoDeProjecao { ANDROID_AUTO, CARPLAY }

/** O que o catálogo precisa do módulo de mídia: a regra de pacote de projeção e os ícones. */
interface ProjecaoParaCatalogo {
    /** Regra de "pacote de projeção" (Android Auto / CarPlay). */
    fun ehPacoteDeProjecao(pacote: String?): Boolean

    /** Ícone da projeção, ou null se o módulo de mídia ainda não o tem (vale o `ic_*_default`). */
    fun iconeDaProjecao(tipo: TipoDeProjecao): Drawable?
}

/** Tema claro/escuro da faixa. Lido de qualquer thread: a implementação deve ser `@Volatile`. */
interface TemaDaFaixa {
    val claro: Boolean
}

/** Quem sabe pedir à página que descarte o catálogo (`window.__app._invalidateAppsCatalog()`). */
fun interface InvalidadorDaPagina {
    fun invalidarCatalogo()
}

/** A faixa se registra para redesenhar quando algo muda. Sempre chamado na thread principal. */
fun interface OuvinteDoCatalogo {
    fun aoMudar(motivo: MotivoDeMudanca)
}

enum class TipoDeMudanca {
    /** Ocultou ou restaurou (pacote null = o hub inteiro). */
    OCULTOS,

    /** A lista de recentes mudou. */
    RECENTES,

    /** "Abrir como janela" mudou para o pacote. */
    JANELA,

    /** Salvou ou limpou a personalização do pacote. */
    PERSONALIZACAO,

    /** O pacote foi desinstalado: a faixa deve remover o chip. */
    PACOTE_REMOVIDO,

    /** O pacote foi instalado ou atualizado: a faixa deve criar/refazer o chip. */
    PACOTE_INSTALADO,

    /** O tema claro/escuro da faixa mudou: as placas devem ser redesenhadas. */
    TEMA,
}

/** Por que o catálogo avisa; [pacote] é o app afetado, quando há um. */
data class MotivoDeMudanca(val tipo: TipoDeMudanca, val pacote: String? = null)

/** Placa (fundo) do ícone no dock. */
sealed interface PlacaDoItem {
    data object Escura : PlacaDoItem
    data object Clara : PlacaDoItem
    data class Cor(@ColorInt val argb: Int) : PlacaDoItem
}

/**
 * Um app para a faixa. [icone] é o ícone-fonte (a faixa o normaliza no tamanho do dock com
 * [normalizarIconeAdaptativo]); null = chip sem imagem. [oculto] = o chip existe mas fica invisível.
 */
data class ItemDoCatalogo(
    val pacote: String,
    val rotulo: String,
    val icone: Drawable?,
    val placa: PlacaDoItem,
    val oculto: Boolean,
)

/** O chip "GWM": rótulo, ícone, placa e os fixados presentes no menu. */
data class ItemDoHub(
    val rotulo: String,
    val icone: Drawable?,
    val placa: PlacaDoItem,
    val fixados: List<ItemDoCatalogo>,
)

/** Um app da lista de ocultos. [rotulo] é o próprio pacote se o app não está instalado. */
data class ItemOculto(
    val pacote: String,
    val rotulo: String,
    val icone: Drawable?,
    val instalado: Boolean,
)

/**
 * Quem recebe o toque que a janela de sobreposição do seletor de desktops captura (implementado
 * pela ponte, que conhece a WebView e a origem dela na tela). Devolve se a WebView consumiu o toque.
 */
interface AnfitriaoDoToque {
    fun repassarToque(evento: MotionEvent): Boolean
}
