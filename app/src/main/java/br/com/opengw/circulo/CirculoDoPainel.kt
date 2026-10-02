package br.com.opengw.circulo

import br.com.opengw.telas.AvisoNoCirculo

/**
 * As páginas do círculo da direita sobre o painel de fábrica (Alpha 6.1).
 *
 * ## O PEDIDO
 *
 * "Mantemos o card de projeção, porém com páginas verticais, como existiam antes: a primeira mostra
 * o card padrão do carro (mídia), a segunda a projeção da multimídia, e numa terceira você aperta e
 * segura o OK e passa a controlar o HUD — passa pelas telas do HUD e clica OK de novo para
 * selecionar e parar de controlar, e volta a mexer no cluster." Card 0 sem páginas; ao ligar o
 * carro, a última página usada.
 *
 * ## ONDE VALE
 *
 * Só nos cards 1 e 3 — os que o carro deixa para o Android e onde o menu do tema Default usou
 * ↑/↓ por anos sem conflito com o carro. O card 0 é do carro (THEME_GUIDE): lá o círculo não
 * aparece e não pega tecla nenhuma. Aviso de fábrica na tela: idem, até ele sair.
 *
 * ## AS TECLAS
 *
 *  • ↑/↓ trocam de página (com volta nas pontas).
 *  • Na página HUD, SEGURAR OK entra no controle do HUD. Aí ↑/↓ escolhem a página do HUD — só no
 *    círculo; o para-brisa não muda enquanto se escolhe — e OK confirma: o HUD vai para a escolhida
 *    e o volante volta ao painel. BACK, trocar de card, um aviso ou [TEMPO_DO_CONTROLE_MS] sem
 *    tecla cancelam sem mudar o HUD.
 *  • ←/→ nunca são consumidas: quem troca de card é o carro (a página Relógio só as ouve, 7.7).
 *  • BACK nunca é consumido: é ele que dispensa o aviso de fábrica.
 *
 * ## ALPHA 7.7 — SEGURAR OK NA CENTRAL E A PÁGINA RELÓGIO
 *
 * "Se eu navegar para o círculo de projeção, eu possa apertar e segurar OK, que automaticamente
 * começará a projetar a última área que selecionei no círculo, sem eu precisar tocar na tela.
 * Além disso, uma página que mostre um relógio analógico, com 10 temas trocados com os botões
 * para a esquerda e direita do volante."
 *
 *  • Página Central: SEGURAR OK projeta a última área do círculo; com a projeção no ar, segurar
 *    de novo encerra ([EfeitoDoCirculo.AlternouProjecao]). Um segundo OK segurado dentro de
 *    [GUARDA_DA_PROJECAO_MS] é ignorado: o `inputservice` pode repetir o keycode de segurar, e
 *    ligar e desligar no mesmo gesto seria pior que nada. Nessa página o hands-free cede o OK
 *    segurado, como já cedia na página HUD.
 *  • Página Relógio (entre Central e HUD): ←/→ trocam o relógio ([RelogiosDoCirculo]).
 *
 * ←/→ SÃO DO CARRO — O RELÓGIO SÓ OUVE
 *
 * O carro troca de card com ←/→ por conta própria; consumir a tecla aqui nunca impediu isso, e
 * consumi-la tiraria o ←/→ do `ServiceManager`, que é quem mantém o card do app igual ao do carro
 * ([br.com.opengw.managers.ClusterCardSyncPolicy] descarta troca de card sem
 * ←/→ recente). Então o relógio age em ←/→ SEM consumir ([observa]) — e, para não sumir a cada
 * troca, a página Relógio aparece em QUALQUER card do carro, inclusive o 0
 * ([ContextoDoCirculo.mostra]): o carro troca de card por baixo do disco e quem olha só vê o
 * relógio mudar. As outras páginas continuam só nos cards 1 e 3, como na 6.1.
 *
 * Kotlin puro, com o relógio passado de fora: é aqui que mora toda regra testável. Quem liga isto
 * ao volante, às preferências e à tela é o [CirculoDoPainelController].
 */
enum class PaginaDoCirculo(val titulo: String) {
    CARRO("Carro"),
    CENTRAL("Central"),
    RELOGIO("Relógio"),
    HUD("HUD");

    /** Aparece também no card 0 (Alpha 7.7): ←/→ trocam o card do carro sem tirar ela da tela. */
    val emQualquerCard: Boolean
        get() = this == RELOGIO

    companion object {
        fun deNome(nome: String?): PaginaDoCirculo? = entries.firstOrNull { it.name == nome }
    }
}

enum class TeclaDoCirculo { CIMA, BAIXO, ESQUERDA, DIREITA, OK, OK_LONGO, VOLTAR }

/** O que está em volta do círculo agora. Quem monta é o controlador, a partir do projetor. */
data class ContextoDoCirculo(
    /** Painel de fábrica, "Círculo do painel" ligado e o projetor do painel no ar. */
    val ligado: Boolean,
    val card: Int,
    /** Camada do OPEN GW no HUD ligada em Telas. Sem ela a página HUD não existe. */
    val hudLigado: Boolean,
    /** O aviso do carro ocupa o círculo agora — pop-up recente ou carro andando; ver [AvisoNoCirculo]. */
    val avisoDeFabrica: Boolean
) {
    val cardComPaginas: Boolean
        get() = card == 1 || card == 3

    /** Um dos cards que o carro tem (0, 1 e 3). */
    val cardDoCarro: Boolean
        get() = card == 0 || cardComPaginas

    /** [pagina] aparece e responde ao volante agora. */
    fun mostra(pagina: PaginaDoCirculo): Boolean =
        ligado && !avisoDeFabrica && (cardComPaginas || (pagina.emQualquerCard && cardDoCarro))

    companion object {
        val DESLIGADO = ContextoDoCirculo(ligado = false, card = 0, hudLigado = false, avisoDeFabrica = false)
    }
}

sealed interface EfeitoDoCirculo {
    data object Nenhum : EfeitoDoCirculo
    data class TrocouDePagina(val pagina: PaginaDoCirculo) : EfeitoDoCirculo
    data object EntrouNoHud : EfeitoDoCirculo
    data class AndouNoHud(val passo: Int) : EfeitoDoCirculo
    data object ConfirmouHud : EfeitoDoCirculo
    data object CancelouHud : EfeitoDoCirculo

    /** Segurou OK na Central: projetar a última área, ou encerrar a que está no ar. */
    data object AlternouProjecao : EfeitoDoCirculo

    /** O relógio da página Relógio agora é o [indice] de [RelogiosDoCirculo.TODOS]. */
    data class TrocouDeRelogio(val indice: Int) : EfeitoDoCirculo
}

class CirculoDoPainel(
    paginaGuardada: PaginaDoCirculo?,
    relogioGuardado: Int = 0,
    private val quantosRelogios: Int = RelogiosDoCirculo.QUANTOS
) {

    companion object {
        /**
         * Sem nada guardado, o círculo começa no card do carro: é o que ele mostrava na 6.0 sem
         * projeção. Abrir uma projeção leva à página Central sozinho ([irPara]).
         */
        val PAGINA_PADRAO = PaginaDoCirculo.CARRO

        /**
         * Depois de segurar OK, um OK que chegue antes disto e sem ↑/↓ no meio é o SOLTAR do mesmo
         * gesto, não uma confirmação. Não se sabe se esta central manda um OK comum ao soltar um
         * OK segurado; se mandar, sem esta guarda o controle entraria e sairia no mesmo gesto.
         */
        const val GUARDA_DO_OK_MS = 2_500L

        /** Controle do HUD sem tecla por este tempo é cancelado: o volante nunca fica preso. */
        const val TEMPO_DO_CONTROLE_MS = 10_000L

        /**
         * Depois de ligar ou encerrar a projeção segurando OK, outro OK segurado antes disto é
         * ignorado: o `inputservice` pode repetir o keycode enquanto a tecla continua segura.
         */
        const val GUARDA_DA_PROJECAO_MS = 3_000L

        /** Anda [passo] casas em [lista], dando a volta nas duas pontas. Fora da lista = da 1ª. */
        fun <T> andar(lista: List<T>, atual: T, passo: Int): T {
            require(lista.isNotEmpty())
            val n = lista.size
            val i = lista.indexOf(atual).coerceAtLeast(0)
            return lista[((i + passo) % n + n) % n]
        }
    }

    /** A última escolhida — a que se grava. Pode não existir agora (HUD desligado). */
    var escolhida: PaginaDoCirculo = paginaGuardada ?: PAGINA_PADRAO
        private set

    var contexto: ContextoDoCirculo = ContextoDoCirculo.DESLIGADO
        private set

    var controlandoHud = false
        private set

    /** O relógio da página Relógio, índice em [RelogiosDoCirculo.TODOS]. */
    var relogio: Int = relogioGuardado.coerceIn(0, quantosRelogios - 1)
        private set

    private var okLongoEm = 0L
    private var andouNoHud = false
    private var ultimaTeclaEm = 0L
    private var alternouProjecaoEm: Long? = null

    val paginas: List<PaginaDoCirculo>
        get() =
            if (contexto.hudLigado) PaginaDoCirculo.entries
            else PaginaDoCirculo.entries - PaginaDoCirculo.HUD

    /**
     * A página mostrada: a escolhida, ou Carro quando ela não existe agora. A escolhida não é
     * sobrescrita — religar o HUD devolve a página HUD.
     */
    val pagina: PaginaDoCirculo
        get() = escolhida.takeIf { it in paginas } ?: PaginaDoCirculo.CARRO

    /** A página atual está na tela e o volante fala com ela. */
    val ativo: Boolean
        get() = contexto.mostra(pagina)

    /**
     * O disco preto aparece: HUD, Relógio, ou Central sem projeção (o aviso de como projetar).
     */
    fun discoVisivel(projecaoAberta: Boolean): Boolean =
        ativo &&
            (pagina == PaginaDoCirculo.HUD ||
                pagina == PaginaDoCirculo.RELOGIO ||
                (pagina == PaginaDoCirculo.CENTRAL && !projecaoAberta))

    /**
     * A janela da projeção pode aparecer. O aviso de fábrica NÃO entra aqui: a janela da projeção
     * já sai da frente dele sozinha (`definirAvisoDeFabrica`), com a mesma espera do tema.
     */
    val projecaoVisivel: Boolean
        get() = contexto.ligado && contexto.cardComPaginas && pagina == PaginaDoCirculo.CENTRAL

    /** Esta tecla é do círculo agora — e não segue para o HUD, o hands-free ou o menu? */
    fun ehDono(tecla: TeclaDoCirculo): Boolean {
        if (!ativo) return false
        return when (tecla) {
            TeclaDoCirculo.CIMA, TeclaDoCirculo.BAIXO -> true
            // Do carro: é com elas que ele troca de card. O relógio só ouve ([observa]).
            TeclaDoCirculo.ESQUERDA, TeclaDoCirculo.DIREITA -> false
            TeclaDoCirculo.OK -> controlandoHud
            // Nas páginas Carro e Relógio, segurar OK continua sendo o hands-free.
            TeclaDoCirculo.OK_LONGO ->
                controlandoHud || pagina == PaginaDoCirculo.HUD || pagina == PaginaDoCirculo.CENTRAL
            TeclaDoCirculo.VOLTAR -> false
        }
    }

    /**
     * Tecla que o círculo usa SEM consumir — ela segue também para o carro e o `ServiceManager`:
     * BACK (dispensa o aviso do carro e cancela o controle do HUD) e ←/→ na página Relógio.
     */
    fun observa(tecla: TeclaDoCirculo): Boolean =
        when (tecla) {
            TeclaDoCirculo.VOLTAR -> controlandoHud
            TeclaDoCirculo.ESQUERDA, TeclaDoCirculo.DIREITA ->
                ativo && !controlandoHud && pagina == PaginaDoCirculo.RELOGIO
            else -> false
        }

    /** Um aperto físico ([br.com.opengw.managers.ToqueDoVolante]). */
    fun aoApertar(tecla: TeclaDoCirculo, agoraMs: Long): EfeitoDoCirculo {
        if (!ativo) return EfeitoDoCirculo.Nenhum
        if (controlandoHud) {
            ultimaTeclaEm = agoraMs
            return when (tecla) {
                TeclaDoCirculo.CIMA -> {
                    andouNoHud = true
                    EfeitoDoCirculo.AndouNoHud(-1)
                }
                TeclaDoCirculo.BAIXO -> {
                    andouNoHud = true
                    EfeitoDoCirculo.AndouNoHud(1)
                }
                TeclaDoCirculo.OK, TeclaDoCirculo.OK_LONGO ->
                    if (!andouNoHud && agoraMs - okLongoEm < GUARDA_DO_OK_MS) {
                        EfeitoDoCirculo.Nenhum
                    } else {
                        controlandoHud = false
                        EfeitoDoCirculo.ConfirmouHud
                    }
                TeclaDoCirculo.VOLTAR -> {
                    controlandoHud = false
                    EfeitoDoCirculo.CancelouHud
                }
                // O carro vai trocar de card; aoMudarContexto cancela o controle quando o card chegar.
                TeclaDoCirculo.ESQUERDA, TeclaDoCirculo.DIREITA -> EfeitoDoCirculo.Nenhum
            }
        }
        return when (tecla) {
            TeclaDoCirculo.CIMA -> mover(-1)
            TeclaDoCirculo.BAIXO -> mover(1)
            TeclaDoCirculo.ESQUERDA -> trocarRelogio(-1)
            TeclaDoCirculo.DIREITA -> trocarRelogio(1)
            TeclaDoCirculo.OK_LONGO ->
                when (pagina) {
                    PaginaDoCirculo.HUD -> {
                        controlandoHud = true
                        okLongoEm = agoraMs
                        ultimaTeclaEm = agoraMs
                        andouNoHud = false
                        EfeitoDoCirculo.EntrouNoHud
                    }
                    PaginaDoCirculo.CENTRAL -> {
                        val ultima = alternouProjecaoEm
                        if (ultima != null && agoraMs - ultima < GUARDA_DA_PROJECAO_MS) {
                            EfeitoDoCirculo.Nenhum
                        } else {
                            alternouProjecaoEm = agoraMs
                            EfeitoDoCirculo.AlternouProjecao
                        }
                    }
                    else -> EfeitoDoCirculo.Nenhum
                }
            TeclaDoCirculo.OK, TeclaDoCirculo.VOLTAR -> EfeitoDoCirculo.Nenhum
        }
    }

    private fun trocarRelogio(passo: Int): EfeitoDoCirculo {
        if (pagina != PaginaDoCirculo.RELOGIO) return EfeitoDoCirculo.Nenhum
        relogio = ((relogio + passo) % quantosRelogios + quantosRelogios) % quantosRelogios
        return EfeitoDoCirculo.TrocouDeRelogio(relogio)
    }

    /** O relógio escolhido fora do volante (Telas, perfil de motorista). */
    fun escolherRelogio(indice: Int): EfeitoDoCirculo {
        val novo = indice.coerceIn(0, quantosRelogios - 1)
        if (novo == relogio) return EfeitoDoCirculo.Nenhum
        relogio = novo
        return EfeitoDoCirculo.TrocouDeRelogio(novo)
    }

    private fun mover(passo: Int): EfeitoDoCirculo {
        val nova = andar(paginas, pagina, passo)
        escolhida = nova
        return EfeitoDoCirculo.TrocouDePagina(nova)
    }

    /**
     * Vai para [destino] sem passar pelo volante — hoje, abrir uma projeção no círculo leva à
     * página Central. Cancela o controle do HUD, se houver: a pessoa mudou de assunto.
     */
    fun irPara(destino: PaginaDoCirculo): EfeitoDoCirculo {
        val cancelou = controlandoHud
        controlandoHud = false
        if (escolhida == destino && !cancelou) return EfeitoDoCirculo.Nenhum
        escolhida = destino
        return if (cancelou) EfeitoDoCirculo.CancelouHud else EfeitoDoCirculo.TrocouDePagina(destino)
    }

    /** Card, aviso, preferências. Trocar de card ou perder o círculo cancela o controle do HUD. */
    fun aoMudarContexto(novo: ContextoDoCirculo): EfeitoDoCirculo {
        val anterior = contexto
        contexto = novo
        if (controlandoHud && (!ativo || novo.card != anterior.card || pagina != PaginaDoCirculo.HUD)) {
            controlandoHud = false
            return EfeitoDoCirculo.CancelouHud
        }
        return EfeitoDoCirculo.Nenhum
    }

    fun expirar(agoraMs: Long): EfeitoDoCirculo {
        if (!controlandoHud || agoraMs - ultimaTeclaEm < TEMPO_DO_CONTROLE_MS) return EfeitoDoCirculo.Nenhum
        controlandoHud = false
        return EfeitoDoCirculo.CancelouHud
    }
}
