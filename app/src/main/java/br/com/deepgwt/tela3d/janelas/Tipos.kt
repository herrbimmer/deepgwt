package br.com.deepgwt.tela3d.janelas

/** Modo de tela: um app à esquerda com o carro 3D ao fundo, ou dois apps dividindo a tela. */
enum class ModoDeTela(val texto: String) {
    APP_E_CARRO("appCar"),
    DOIS_APPS("appsOnly"),
    ;

    companion object {
        /** `appsOnly` é o Side by Side; qualquer outro texto (inclusive o antigo `triple`) é `appCar`. */
        fun doTexto(texto: String?): ModoDeTela = if (texto == DOIS_APPS.texto) DOIS_APPS else APP_E_CARRO
    }
}

enum class Lado(val texto: String) {
    ESQUERDA("left"),
    DIREITA("right"),
    ;

    val oposto: Lado get() = if (this == ESQUERDA) DIREITA else ESQUERDA

    companion object {
        /** `right` é a direita; qualquer outro texto (a página só manda `left`/`right`) é a esquerda. */
        fun doTextoDaPagina(texto: String?): Lado = if (texto == DIREITA.texto) DIREITA else ESQUERDA
    }
}

enum class ProporcaoDaDivisao(val texto: String, val fracaoDaEsquerda: Float) {
    UM_PARA_UM("1:1", 0.5f),
    DOIS_PARA_UM("2:1", 0.6666667f),
    UM_PARA_DOIS("1:2", 0.33333334f),
    ;

    companion object {
        /** Null quando o texto não é uma das três proporções. */
        fun doTexto(texto: String?): ProporcaoDaDivisao? = entries.firstOrNull { it.texto == texto }
    }
}

enum class UsoDoLado(val texto: String) {
    APP("app"),
    WIDGETS("widgets"),
    ;

    companion object {
        /** `widgets` ou (qualquer outro texto, nulo incluído) `app`. */
        fun doTexto(texto: String?): UsoDoLado = if (texto == WIDGETS.texto) WIDGETS else APP
    }
}

enum class ModoDaInterface(val texto: String) {
    EMBUTIDA("inset"),
    FLUTUANTE("floating"),
    TELA_CHEIA("full"),
    ;

    companion object {
        /** Null quando o texto não é um dos três modos. */
        fun doTexto(texto: String?): ModoDaInterface? = entries.firstOrNull { it.texto == texto }
    }
}

enum class TipoDeProjecao { NENHUM, ANDROID_AUTO, CARPLAY }

/** Retângulo em pixels, sem Android (a geometria e a lógica de janelas são puras e rodam em teste de JVM). */
data class Retangulo(val esquerda: Int, val topo: Int, val direita: Int, val fundo: Int) {
    val largura: Int get() = direita - esquerda
    val altura: Int get() = fundo - topo

    override fun toString(): String = "[$esquerda,$topo][$direita,$fundo]"
}

/** O que o módulo sabe das janelas num instante; a faixa observa pelo `StateFlow` da fachada. */
data class EstadoDasJanelas(
    val modo: ModoDeTela,
    val proporcao: ProporcaoDaDivisao,
    /** Sempre `auto`: a preferência de lado fixo foi removida (a página não usa). */
    val ladoPreferido: String,
    val proximoEhEsquerda: Boolean,
    /** `left`/`right`, ou vazio se ainda não houve abertura. */
    val ultimoLado: String,
    val pacoteEsquerda: String,
    val pacoteDireita: String,
    val usoEsquerda: UsoDoLado,
    val usoDireita: UsoDoLado,
    val modoDaInterface: ModoDaInterface,
    val chromePorCima: Boolean,
) {
    val temJanelaDeLado: Boolean get() = pacoteEsquerda.isNotEmpty() || pacoteDireita.isNotEmpty()
}

/** Algo agendado que pode ser desfeito. */
fun interface Cancelavel {
    fun cancelar()
}

/** Agenda uma ação na thread principal depois de [atrasoMs] (0 = o quanto antes). */
internal fun interface Agendador {
    fun em(atrasoMs: Long, acao: Runnable): Cancelavel
}

/** Relógio monotônico em milissegundos. */
internal fun interface Relogio {
    fun agoraMs(): Long
}

/** Linhas de registro da tela 3D: uma por etapa. */
internal interface Diario {
    fun info(mensagem: String)
    fun aviso(mensagem: String)
    fun erro(mensagem: String, causa: Throwable? = null)

    object Mudo : Diario {
        override fun info(mensagem: String) {}
        override fun aviso(mensagem: String) {}
        override fun erro(mensagem: String, causa: Throwable?) {}
    }
}
