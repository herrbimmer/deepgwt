package br.com.deepgwt.tela3d.catalogo

import java.lang.ref.WeakReference

/** Retângulo do seletor de desktops já em pixels de tela, e se a janela de sobreposição é desejada. */
internal data class AreaDoSeletor(
    val x: Int,
    val y: Int,
    val largura: Int,
    val altura: Int,
    val querer: Boolean,
)

/** Conversão do retângulo em pixels CSS (vindo da página) para pixels de tela (I2). Pura. */
internal object ConversaoDoSeletor {
    /**
     * `x_tela = origemX + round(x × escala)`, idem y; largura/altura = `max(0, round(v × escala))`;
     * escala ≤ 0 vale 1. Fica "desejada" se [querer] e largura > 1 e altura > 1.
     */
    fun converter(
        x: Float,
        y: Float,
        largura: Float,
        altura: Float,
        querer: Boolean,
        escala: Float,
        origemX: Int,
        origemY: Int,
    ): AreaDoSeletor {
        val e = if (escala > 0f) escala else 1f
        val l = maxOf(0, Math.round(largura * e))
        val a = maxOf(0, Math.round(altura * e))
        return AreaDoSeletor(
            x = origemX + Math.round(x * e),
            y = origemY + Math.round(y * e),
            largura = l,
            altura = a,
            querer = querer && l > 1 && a > 1,
        )
    }
}

/**
 * A janela de sobreposição em si (lado Android). Chamada só na thread principal pela
 * [MaquinaDoSeletor]; quem implementa cuida da view e dos parâmetros de janela.
 */
internal interface JanelaDoSeletor {
    /** Anexa a janela nos pixels de tela dados. @return false se falhou (a próxima sincronização tenta de novo). */
    fun anexar(x: Int, y: Int, largura: Int, altura: Int): Boolean

    /** Atualiza posição/tamanho de uma janela já anexada. @return false se falhou. */
    fun atualizar(x: Int, y: Int, largura: Int, altura: Int): Boolean

    /** Remove a janela; erros são ignorados. */
    fun remover()
}

/**
 * Estado do seletor de desktops, estático (sobrevive à instância do serviço de acessibilidade): o
 * host (fraco), a última área, se a página a quer e se a janela está anexada.
 *
 * `aplicar` pode vir de qualquer thread; a sincronização da janela roda na thread principal, por
 * meio do `postar` que o serviço entrega ao conectar. Sem serviço conectado, o estado fica
 * guardado e é aplicado na conexão.
 */
internal class MaquinaDoSeletor {
    private val trava = Any()
    private var anfitriao: WeakReference<AnfitriaoDoToque>? = null
    private var area = AreaDoSeletor(0, 0, 0, 0, false)

    /** A tela inicial está fora do primeiro plano (parada): a janela fica removida. */
    private var suspenso = false
    private var janela: JanelaDoSeletor? = null
    private var postar: ((Runnable) -> Unit)? = null
    private var anexada = false

    /** O host atual, se ainda existe. */
    fun anfitriao(): AnfitriaoDoToque? = synchronized(trava) { anfitriao?.get() }

    /** A última área informada pela página (com o desejo dela). */
    fun ultimaArea(): AreaDoSeletor = synchronized(trava) { area }

    /** Há janela anexada neste momento? (para testes e diagnóstico) */
    fun estaAnexada(): Boolean = synchronized(trava) { anexada }

    /** Guarda o estado e, se o serviço está conectado, agenda a sincronização. */
    fun aplicar(host: AnfitriaoDoToque?, x: Int, y: Int, largura: Int, altura: Int, querer: Boolean) {
        synchronized(trava) {
            anfitriao = host?.let { WeakReference(it) }
            area = AreaDoSeletor(x, y, largura, altura, querer)
        }
        agendar()
    }

    /** A tela inicial parou: remove a janela, mas lembra o que a página quer. */
    fun suspender() {
        synchronized(trava) { suspenso = true }
        agendar()
    }

    /** A tela inicial voltou ao primeiro plano: reaplica a última área se a página a queria. */
    fun retomar() {
        synchronized(trava) { suspenso = false }
        agendar()
    }

    /** O serviço conectou: guarda a janela e o jeito de postar na thread principal, e sincroniza. */
    fun conectar(nova: JanelaDoSeletor, postarNaPrincipal: (Runnable) -> Unit) {
        synchronized(trava) {
            janela = nova
            postar = postarNaPrincipal
            anexada = false
        }
        agendar()
    }

    /**
     * O serviço foi destruído: remove a janela dele (sempre) e, se ele ainda é o corrente, solta o
     * estado de conexão.
     */
    fun desconectar(antiga: JanelaDoSeletor) {
        synchronized(trava) {
            if (janela === antiga) {
                janela = null
                postar = null
                anexada = false
            }
        }
        antiga.remover()
    }

    private fun agendar() {
        val p = synchronized(trava) { postar } ?: return
        p(Runnable { sincronizar() })
    }

    /** Põe a janela de acordo com o estado. Roda na thread principal. */
    fun sincronizar() {
        val alvo: JanelaDoSeletor
        val a: AreaDoSeletor
        val desejada: Boolean
        synchronized(trava) {
            alvo = janela ?: return
            a = area
            desejada = a.querer && !suspenso
        }
        if (!desejada) {
            val estava = synchronized(trava) { anexada.also { anexada = false } }
            if (estava) alvo.remover()
            return
        }
        val jaAnexada = synchronized(trava) { anexada }
        val ok = if (jaAnexada) alvo.atualizar(a.x, a.y, a.largura, a.altura) else alvo.anexar(a.x, a.y, a.largura, a.altura)
        synchronized(trava) { if (janela === alvo) anexada = ok }
    }

    companion object {
        /** A instância única do app (o serviço de acessibilidade é criado pelo sistema, então o estado é estático). */
        val compartilhada = MaquinaDoSeletor()
    }
}

/**
 * Lado da ponte: informa ao serviço de acessibilidade onde está o seletor de desktops da página.
 * Todas as instâncias compartilham o mesmo estado estático.
 */
class SeletorDeAreas internal constructor(private val maquina: MaquinaDoSeletor) {
    constructor() : this(MaquinaDoSeletor.compartilhada)

    /**
     * Aplica a área em pixels de TELA. Se o serviço está conectado, a janela é sincronizada na
     * thread principal; senão o estado fica guardado até a conexão.
     */
    fun aplicar(anfitriao: AnfitriaoDoToque?, x: Int, y: Int, largura: Int, altura: Int, querer: Boolean) {
        maquina.aplicar(anfitriao, x, y, largura, altura, querer)
    }

    /**
     * Converte o retângulo em pixels CSS da página (com a folga de 20 %) para pixels de tela e
     * aplica (I2): [escala] é a escala atual da WebView (≤ 0 vale 1) e a origem é onde a WebView
     * está na tela.
     */
    fun aplicarEmCss(
        anfitriao: AnfitriaoDoToque?,
        x: Float,
        y: Float,
        largura: Float,
        altura: Float,
        querer: Boolean,
        escala: Float,
        origemNaTelaX: Int,
        origemNaTelaY: Int,
    ) {
        val a = ConversaoDoSeletor.converter(x, y, largura, altura, querer, escala, origemNaTelaX, origemNaTelaY)
        maquina.aplicar(anfitriao, a.x, a.y, a.largura, a.altura, a.querer)
    }

    /** A tela inicial foi destruída: remove a janela e esquece a área (a página reenvia ao voltar). */
    fun aoDestruir(anfitriao: AnfitriaoDoToque?) {
        maquina.aplicar(anfitriao, 0, 0, 0, 0, false)
    }

    /** A tela inicial parou de verdade (não é mudança de configuração nem abertura de sobreposição): remove a janela. */
    fun aoParar() {
        maquina.suspender()
    }

    /** A tela inicial voltou ao primeiro plano: reaplica a última área, se a página a queria. */
    fun aoRetomar() {
        maquina.retomar()
    }
}
