package br.com.opengw.telas

import android.graphics.drawable.Drawable

// Tipos públicos do lançador de apps (módulo 3.8). Especificação: docs/independencia/specs/lancador*.md
// (parte 1, seção 2). Implementação de sala limpa.

/**
 * Uma entrada de `displayAppConfigs`. Os textos entre aspas são as chaves JSON gravadas nos carros
 * (contrato; parte 2, seção A). Ler: chave ausente → null/false/0. Escrever: nunca escrever chave
 * com valor nulo.
 */
data class ConfigDoApp(
    val pacote: String,                     // "packageName"
    val atividade: String,                  // "activityName"
    val display: Int,                       // "displayId"
    val x: Int,                             // "x"
    val y: Int,                             // "y"
    val largura: Int,                       // "width"
    val altura: Int,                        // "height"
    val iconeSubstituto: String? = null,    // "substituteIcon"
    val corDoIcone: String? = null,         // "iconColor"
    val nome: String? = null,               // "customName"
    val forcarFoco: Boolean = false,        // "forceFocus"
    val ignorarAreaDoTema: Boolean = false, // "overrideThemeDimensions"
)

/** Nome e ícone para mostrar um app (parte 2, seção B). */
data class InfoDoApp(val rotulo: String, val icone: Drawable?)

/**
 * Uma tarefa achada na lista de pilhas (`am stack list`). `limites` = [esquerda, topo, direita,
 * fundo] em px do display, ou null se não deu para saber.
 */
data class TarefaNaTela(val tarefa: Int, val pilha: Int, val display: Int, val limites: IntArray?) {
    override fun equals(other: Any?): Boolean =
        other is TarefaNaTela && tarefa == other.tarefa && pilha == other.pilha &&
            display == other.display && limites.contentEquals(other.limites)

    override fun hashCode(): Int =
        ((tarefa * 31 + pilha) * 31 + display) * 31 + (limites?.contentHashCode() ?: 0)

    override fun toString(): String =
        "TarefaNaTela(tarefa=$tarefa, pilha=$pilha, display=$display, limites=${limites?.contentToString()})"
}

/** As fontes de fora do pacote. Tudo tem padrão vazio e seguro. */
class DependenciasDasTelas(
    val barra: BarraParaTelas = BarraParaTelas.VAZIA,
    val painel: AvisosAoPainel = AvisosAoPainel.VAZIO,
    val tema: AreaDoTemaGravada = AreaDoTemaGravada.VAZIA,
    val dcm: EstadoDoDcm = EstadoDoDcm.VAZIO,
    val diario: DiarioDasTelas = DiarioDasTelas.VAZIO,
    val launcher3d: GanchoDoLauncher3d = GanchoDoLauncher3d.VAZIO,
)

/** A barra inferior (módulo 3.7). Ler de qualquer thread; os valores são do estado global da barra. */
interface BarraParaTelas {
    /** Altura da barra em dp (padrão 60). */
    val alturaDp: Int

    /** A barra está na tela agora. */
    val visivel: Boolean

    /** Quem está tocando, segundo a barra. */
    val pacoteDaMidia: String?
    val tocando: Boolean
    val tituloDaMidia: String?
    val artistaDaMidia: String?
    val albumDaMidia: String?
    val duracaoDaMidiaMs: Long

    /** Pacote de espelhamento que a barra vê no painel ("" = nenhum). */
    val espelhamentoNoPainel: String

    /** Dica otimista depois de uma tecla de tocar/pausar do volante (parte 4, E4). */
    fun dicaDeReproducao(pacote: String, tocando: Boolean)

    /** Lista de "apps restaurados" da barra (sem efeito hoje; decisão 7). */
    fun marcarRestaurado(pacote: String)
    fun desmarcarRestaurado(pacote: String)

    /** A rota nativa do Media Center para o Android Auto está ativa? */
    fun rotaNativaDoAndroidAuto(): Boolean

    /** Tocando segundo a rota nativa; null se não sabe. */
    fun rotaNativaTocando(): Boolean?
    suspend fun alvoPelaRotaNativa(tocar: Boolean, motivo: String): Boolean
    fun alternarMudoDoAndroidAuto(): Boolean
    fun zerarProgressoAposTrocaDeFaixa(motivo: String): Boolean

    /** Uma janela de outro app chegou à frente: a barra decide se volta. */
    fun voltarAposFocoExterno(pacote: String?, motivo: String)

    /** O espelhamento acabou de ir para o painel (display 3): a barra reabre o que precisar. */
    fun reabrirAposEspelhamentoNoPainel(motivo: String)

    companion object {
        /** Altura 60, visível, nada tocando, ações sem efeito, false/null. */
        val VAZIA: BarraParaTelas = object : BarraParaTelas {
            override val alturaDp: Int get() = 60
            override val visivel: Boolean get() = true
            override val pacoteDaMidia: String? get() = null
            override val tocando: Boolean get() = false
            override val tituloDaMidia: String? get() = null
            override val artistaDaMidia: String? get() = null
            override val albumDaMidia: String? get() = null
            override val duracaoDaMidiaMs: Long get() = 0L
            override val espelhamentoNoPainel: String get() = ""
            override fun dicaDeReproducao(pacote: String, tocando: Boolean) = Unit
            override fun marcarRestaurado(pacote: String) = Unit
            override fun desmarcarRestaurado(pacote: String) = Unit
            override fun rotaNativaDoAndroidAuto(): Boolean = false
            override fun rotaNativaTocando(): Boolean? = null
            override suspend fun alvoPelaRotaNativa(tocar: Boolean, motivo: String): Boolean = false
            override fun alternarMudoDoAndroidAuto(): Boolean = false
            override fun zerarProgressoAposTrocaDeFaixa(motivo: String): Boolean = false
            override fun voltarAposFocoExterno(pacote: String?, motivo: String) = Unit
            override fun reabrirAposEspelhamentoNoPainel(motivo: String) = Unit
        }
    }
}

/** O painel projetado (módulos 3.6 e camada do display 1). Chamadas podem vir de qualquer thread. */
interface AvisosAoPainel {
    /** Displays 1 e 3: há (ou não) app de terceiros naquele display. */
    fun appNoDisplay(display: Int, ativo: Boolean)

    /** Algum app mudou de tamanho/posição: o painel recalcula os vãos. */
    fun geometriaMudou()

    /** Um app vai entrar no display 3 com estes limites [e, t, d, f]: abrir o vão ANTES. */
    fun prepararVaoNoDisplay3(limites: IntArray)

    companion object {
        val VAZIO: AvisosAoPainel = object : AvisosAoPainel {
            override fun appNoDisplay(display: Int, ativo: Boolean) = Unit
            override fun geometriaMudou() = Unit
            override fun prepararVaoNoDisplay3(limites: IntArray) = Unit
        }
    }
}

/** A área para apps declarada no `theme.xml` de um tema (`<AppDefaultPosition>`: x, y, width, height). */
fun interface AreaDoTemaGravada {
    /** [x, y, largura, altura] ou null. */
    fun area(nomeDoTema: String): IntArray?

    companion object {
        val VAZIA: AreaDoTemaGravada = AreaDoTemaGravada { null }
    }
}

/** O gerenciador de conexões da central (DCM), lido pelo módulo dos monitores de AA/CarPlay. */
fun interface EstadoDoDcm {
    /**
     * Lê os aparelhos agora (pode levar até ~2,5 s). `androidAutoAtivo` = alguma projeção AA (USB ou
     * sem fio) ativada.
     */
    suspend fun ler(): LeituraDoDcm

    companion object {
        /** Sempre `LeituraDoDcm(false, "")`. */
        val VAZIO: EstadoDoDcm = EstadoDoDcm { LeituraDoDcm(false, "") }
    }
}

data class LeituraDoDcm(val androidAutoAtivo: Boolean, val resumo: String)

/** O registro persistente de diagnóstico do app. Nomes de evento livres. */
fun interface DiarioDasTelas {
    fun registrar(evento: String, detalhes: Map<String, Any?>)

    companion object {
        val VAZIO: DiarioDasTelas = DiarioDasTelas { _, _ -> }
    }
}

/** A guarda do launcher 3D (código nosso fora do pacote). */
fun interface GanchoDoLauncher3d {
    /** Chamado no começo de toda abertura na principal (C3), com o pacote pedido. */
    fun aoAbrirNaPrincipal(pacote: String)

    companion object {
        val VAZIO: GanchoDoLauncher3d = GanchoDoLauncher3d { }
    }
}
