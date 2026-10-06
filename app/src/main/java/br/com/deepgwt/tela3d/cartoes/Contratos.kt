package br.com.deepgwt.tela3d.cartoes

import android.content.Context
import android.graphics.drawable.Drawable
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import br.com.deepgwt.tela3d.faixa.TemaDoCromo
import br.com.deepgwt.tela3d.ponte.IndicadoresDoDock

// Fronteiras do módulo da linha de cartões rápidos: o que a ponte e a faixa usam dela e o que ela
// precisa do anfitrião. Tudo na thread principal.

/** O que a PONTE usa. */
interface CartoesParaAPonte {
    /** `TIME_TICK`, `TIME_SET`, `TIMEZONE_CHANGED` e `LOCALE_CHANGED`: recalcula os cartões de relógio. */
    fun atualizarRelogios()

    /** Mesmo JSON do "tocando agora"; guarda o último e o reaplica a cada reconstrução da linha. */
    fun atualizarCartaoDeMidia(payloadJson: String)
}

/** O que a FAIXA usa. A faixa é dona do contêiner rolável e do tema; os cartões nunca falam com a WebView. */
interface CartoesParaAFaixa {
    /**
     * A linha (filho único do contêiner rolável). Os cartões só mexem no preenchimento superior, direito e
     * inferior dela; o esquerdo é da faixa. Os filhos que a faixa inserir (o "Espaço de trabalho") ficam
     * onde estão: a linha só remove e recoloca os próprios cartões, sempre depois dos da faixa.
     */
    fun criarLinha(): LinearLayout

    /** O contêiner rolável que envolve a linha; usado na edição (arrasto, rolagem automática, foco). */
    fun definirContainer(container: HorizontalScrollView)

    /**
     * Fim do tratamento do pacote da página: o tema da faixa JÁ está atualizado. Usa só os cartões, o limite,
     * `editandoTrilho`, `focoDoTrilho` e os resumos de clima e de consumo.
     */
    fun aplicarIndicadores(ind: IndicadoresDoDock)

    /** Reestiliza tudo, fecha o menu rápido e NÃO reconstrói. */
    fun aoMudarTema()

    /** Idem: as cores de acento são lidas no desenho, então não há o que reconstruir. */
    fun aoMudarAcento()

    fun reconstruirLinha()

    /** A Activity pausou (ou a faixa fechou tudo). */
    fun fecharMenus()

    /** O ícone de um pacote pode ter mudado: refaz o chip de origem dos cartões de mídia e de navegação. */
    fun redesenharChipDeOrigemDoApp(pacote: String)

    /** `railEdit` em vigor. */
    val editando: Boolean
}

/** O que os cartões PRECISAM do anfitrião (a Activity ou a cola implementa e delega aos módulos donos). */
interface AnfitriaoDosCartoes {
    val contexto: Context
    val tema: TemaDoCromo

    /** `window.__app.dockCommand(...)`; o escape do texto é da ponte. */
    fun enviarComandoAoDock(comando: String)

    /** A faixa reavalia qual superfície mostrar (os cartões durante a edição). */
    fun aoMudarEdicao(editando: Boolean)

    /** Abre o app pelo caminho do dock (para pacote de projeção, abre a projeção). */
    fun abrirApp(pacote: String, rotulo: String)

    /** Ícone do chip de origem (ícone do usuário, projeção, ícone padrão da projeção); nulo se não há. */
    fun iconeDeOrigem(pacote: String?, rotulo: String?): Drawable?

    val midia: ControlesDeMidiaDosCartoes
}

/** Controles que o cartão de mídia chama no módulo de mídia. */
interface ControlesDeMidiaDosCartoes {
    fun anterior()
    fun tocarOuPausar()
    fun proxima()
    fun aoMudarTocando(tocando: Boolean)
}
