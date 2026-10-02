package br.com.opengw.barra

/** A grandeza de uma caixinha de ajuste ou do cartão aberto. */
enum class FuncaoDoAjuste { TEMPERATURA_MOTORISTA, TEMPERATURA_PASSAGEIRO, VENTILACAO, VOLUME }

/**
 * Ajuste rápido em curso: o dedo segura uma caixinha da barra e arrasta. Escrito e apagado só pela
 * própria caixinha; a barra expandida desenha a partir dele.
 */
data class AjusteRapido(
    val funcao: FuncaoDoAjuste,
    /** O valor pedido pelo dedo, já no degrau. */
    val valor: Float,
    val minimo: Float,
    val maximo: Float,
    val passo: Float,
    /** Deslocamento elástico no fim de curso, com sinal (px). */
    val empurraoPx: Float,
    /** Centro e largura da caixinha, em px da janela da barra. */
    val centroDaCaixinhaPx: Float,
    val larguraDaCaixinhaPx: Float,
) {
    /** Onde o valor está na faixa, 0..1; 0 quando a faixa é vazia. */
    val fracao: Float
        get() = if (maximo > minimo) ((valor - minimo) / (maximo - minimo)).coerceIn(0f, 1f) else 0f
}

/** A coluna de modos de condução aberta pela pressão longa na caixinha "Modo". */
data class EscolhaDeModo(
    val centroDaCaixinhaPx: Float,
    /** −1 = o dedo ainda está dentro da barra (nada escolhido). */
    val indice: Int,
    /** Valor cru de `car.drive_setting.drive_mode` no começo/durante o gesto. */
    val modoAtual: String,
)

/** Quantas temperaturas a barra mostra. [valorGravado] é o texto de `bottomBarTempControl`. */
enum class ControleDeTemperatura(val valorGravado: String) {
    DESLIGADO("off"),
    UMA_ZONA("single"),
    DUAS_ZONAS("dual"),
}

/** O que o deslize para cima da barra faz. [valorGravado] é o texto de `bottomBarSwipeUpAction`. */
enum class GestoParaCima(val valorGravado: String, val rotulo: String) {
    NADA("none", "Nada (gesto desativado)"),
    GAVETA("drawer", "Abrir a gaveta de atalhos"),
    PAINEL("dashboard", "Abrir Painel (OPEN GW)"),
    HOME_DE_FABRICA("haval_home", "Ir para a Home Haval"),
    LISTA_DE_APPS("app_launcher", "Ir para a lista de apps Haval"),
    APP_ESPECIFICO("custom_app", "Abrir um app específico"),
}

/** Uma fatia da barra (estado caso 9). */
enum class Fatia { TEMPERATURA_MOTORISTA, MODO, VOLUME, TEMPERATURA_PASSAGEIRO }

/** As fatias dos dois lados do trio central, da esquerda para a direita. */
data class FatiasDaBarra(val esquerda: List<Fatia>, val direita: List<Fatia>)

/** Um modo de condução: código cru de `car.drive_setting.drive_mode` e rótulo. */
data class ModoDeConducao(val codigo: String, val rotulo: String)

/** Recuo gravado à mão para um app (`bottomBarOverrides`). */
data class RecuoManual(val overscanDp: Int, val deslocamentoDp: Int)
