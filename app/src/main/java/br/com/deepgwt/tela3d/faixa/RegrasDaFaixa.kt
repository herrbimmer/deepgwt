package br.com.deepgwt.tela3d.faixa

import kotlin.math.max
import kotlin.math.min

// Regras puras da faixa: visibilidade, composição da fileira de apps, revelação e alinhamento.

/** Como a faixa fica na hierarquia de views. */
enum class EstadoDaFaixa { INVISIVEL, GONE, VISIVEL }

object RegrasDaFaixa {
    /**
     * F4: a faixa só aparece se já foi revelada, a página não pediu para escondê-la e a casca não é
     * lado a lado. Antes da revelação (e fora do lado a lado) ela ocupa lugar mas fica invisível.
     */
    fun estadoDaFaixa(revelada: Boolean, escondidaPelaPagina: Boolean, modo: ModoDaCasca): EstadoDaFaixa = when {
        modo == ModoDaCasca.LADO_A_LADO -> EstadoDaFaixa.GONE
        !revelada -> EstadoDaFaixa.INVISIVEL
        escondidaPelaPagina -> EstadoDaFaixa.GONE
        else -> EstadoDaFaixa.VISIVEL
    }

    /** F13: a banda de reserva só depende da revelação e de ter altura. */
    fun bandaVisivel(revelada: Boolean, alturaDaBandaPx: Int): Boolean = revelada && alturaDaBandaPx > 0

    /** F35: preenchimento esquerdo da linha de cartões, alinhando o primeiro cartão com a área de widgets. */
    fun preenchimentoEsquerdoDaLinhaDeCartoes(
        areaEsquerdaEsq: Int,
        paginaEsq: Int,
        preenchimentoEsquerdoDaFaixa: Int,
        margemEsquerdaDoConteiner: Int,
    ): Int = max(0, (areaEsquerdaEsq - paginaEsq) - preenchimentoEsquerdoDaFaixa - margemEsquerdaDoConteiner)

    /** Margem esquerda do contêiner de conteúdo, em dp: 12 no lançador, 0 nos cartões. */
    fun margemDoConteinerDp(superficie: SuperficieDoDock): Int = if (superficie == SuperficieDoDock.LANCADOR) 12 else 0

    /** A fileira de apps aparece no lançador, a menos que a edição dos cartões esteja ativa. */
    fun mostrarFileiraDeApps(superficie: SuperficieDoDock, editandoCartoes: Boolean): Boolean =
        superficie == SuperficieDoDock.LANCADOR && !editandoCartoes
}

/** Quem é cada item da fileira. */
enum class TipoDeItem { PROJECAO, HUB, LADO_A_LADO, APP }

/** Um item da fileira: [chave] é o pacote nos apps e um texto fixo nos três itens especiais. */
data class ItemPlanejado(val tipo: TipoDeItem, val chave: String, val recente: Boolean = false)

/** Um app da linha geral do catálogo: [oculto] = o item existe mas fica fora da fileira. */
data class AppDaLinha(val pacote: String, val oculto: Boolean)

/** Composição da fileira de apps (F11 a F16). */
object PlanoDaFileira {
    const val CHAVE_DA_PROJECAO = "__projecao"
    const val CHAVE_DO_HUB = "__gwm_hub"
    const val CHAVE_DO_LADO_A_LADO = "__lado_a_lado"
    const val SLOTS_DE_RECENTES = 3

    /**
     * A fileira visível, na ordem: projeção (se houver), hub (se visível), Side by Side, até 3 recentes e
     * a linha geral sem os recentes e sem os ocultos. Cada pacote aparece uma vez.
     */
    fun montar(
        projecaoPresente: Boolean,
        hubVisivel: Boolean,
        recentes: List<String>,
        linha: List<AppDaLinha>,
    ): List<ItemPlanejado> {
        val saida = ArrayList<ItemPlanejado>()
        if (projecaoPresente) saida.add(ItemPlanejado(TipoDeItem.PROJECAO, CHAVE_DA_PROJECAO))
        if (hubVisivel) saida.add(ItemPlanejado(TipoDeItem.HUB, CHAVE_DO_HUB))
        saida.add(ItemPlanejado(TipoDeItem.LADO_A_LADO, CHAVE_DO_LADO_A_LADO))
        val naLinha = linha.associateBy { it.pacote }
        val mostrados = LinkedHashSet<String>()
        for (pacote in recentes) {
            if (mostrados.size >= SLOTS_DE_RECENTES) break
            val app = naLinha[pacote]
            if (app != null && app.oculto) continue
            if (mostrados.add(pacote)) saida.add(ItemPlanejado(TipoDeItem.APP, pacote, recente = true))
        }
        for (app in linha) {
            if (app.oculto || app.pacote in mostrados) continue
            if (!mostrados.add(app.pacote)) continue
            saida.add(ItemPlanejado(TipoDeItem.APP, app.pacote))
        }
        return saida
    }
}

/** Parâmetros da primeira revelação (F3 e A8). */
object RevelacaoDaFileira {
    const val DURACAO_MS = 520L
    const val FATOR_DA_DESACELERACAO = 1.8f
    const val PASSO_ENTRE_ITENS_MS = 200L
    const val DESLOCAMENTO_DP = 120f
    const val RASTRO_DP = 90f
    const val PLANO_B_MS = 60_000L

    /** O último item da fila começa primeiro; o primeiro, por último. */
    fun atrasoMs(quantidade: Int, indice: Int): Long = max(0, quantidade - 1 - indice) * PASSO_ENTRE_ITENS_MS

    /** Deslocamento horizontal no progresso [t] (0 a 1), em dp (negativo: vem da esquerda). */
    fun deslocamentoDp(t: Float): Float = -DESLOCAMENTO_DP * (1f - t)

    fun alfa(t: Float): Float = min(1f, 1.6f * t)

    fun rastroDp(t: Float): Float = RASTRO_DP * (1f - t)

    /** Alfas (0 a 255) das cópias fantasma do rastro, de dentro para fora; as menores que 3 são puladas. */
    fun alfaDaCopiaDoRastro(k: Int): Int = ((COPIAS_DO_RASTRO - k) * 150) / COPIAS_DO_RASTRO

    /** Fração do deslocamento do rastro da cópia [k] (de 1 a 6). */
    fun fracaoDaCopia(k: Int): Float = k / 6f

    const val COPIAS_DO_RASTRO = 6
    const val ALFA_MINIMO_DA_COPIA = 3
    const val LIMIAR_DO_RASTRO = 0.5f
}

/** O que o menu de edição (toque longo num item) oferece (F17). */
enum class LinhaDoMenuDeEdicao(val texto: String) {
    PERSONALIZAR("Customize…"),
    OCULTAR("Hide"),
    ABRIR_COMO_JANELA("Open as window"),
    ABRIR_COMO_JANELA_MARCADO("✓ Open as window"),
    REEXIBIR("Unhide selected…"),
    DESINSTALAR("Uninstall"),
}

object RegrasDoMenuDeEdicao {
    /** As linhas do menu, na ordem; só as que se aplicam entram. */
    fun linhas(
        podeAbrirComoJanela: Boolean,
        abrirComoJanelaMarcado: Boolean,
        haOcultos: Boolean,
        podeDesinstalar: Boolean,
    ): List<LinhaDoMenuDeEdicao> {
        val saida = ArrayList<LinhaDoMenuDeEdicao>()
        saida.add(LinhaDoMenuDeEdicao.PERSONALIZAR)
        saida.add(LinhaDoMenuDeEdicao.OCULTAR)
        if (podeAbrirComoJanela) {
            saida.add(
                if (abrirComoJanelaMarcado) LinhaDoMenuDeEdicao.ABRIR_COMO_JANELA_MARCADO
                else LinhaDoMenuDeEdicao.ABRIR_COMO_JANELA,
            )
        }
        if (haOcultos) saida.add(LinhaDoMenuDeEdicao.REEXIBIR)
        if (podeDesinstalar) saida.add(LinhaDoMenuDeEdicao.DESINSTALAR)
        return saida
    }
}
