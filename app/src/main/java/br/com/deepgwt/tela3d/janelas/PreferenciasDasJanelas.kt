package br.com.deepgwt.tela3d.janelas

import br.com.deepgwt.tela3d.catalogo.ListaDePacotes

/**
 * Onde as chaves moram (o arquivo `deepgwt_lancador`, modo privado). Só mexe nas chaves pedidas: as do
 * resto do arquivo (`widgets`, `shellBg`, `hidden_apps`, `recent_apps`…) são de outros módulos.
 */
internal interface ArmazemDeChaves {
    fun texto(chave: String): String?
    fun booleano(chave: String): Boolean?

    /** Grava só as chaves dadas, sem apagar as demais (`apply()` assíncrono no Android). */
    fun gravar(textos: Map<String, String>, booleanos: Map<String, Boolean> = emptyMap())
}

/** O que se lê das preferências ao iniciar a Activity (C47). */
internal data class DadosSalvos(
    val modo: ModoDeTela,
    val proporcao: ProporcaoDaDivisao,
    val proximoEhEsquerda: Boolean,
    val modoDaInterface: ModoDaInterface,
    /** Apps do Side by Side salvos por SAVE, para restaurar na revelação do launcher (C27). */
    val appEsquerda: String,
    val appDireita: String,
    /** Pacotes marcados "abrir como janela", sem repetição. */
    val abrirComoJanela: Set<String>,
)

/**
 * Leitura e gravação das preferências deste módulo.
 *
 * Decisões do dono: o padrão do Side by Side sobrevive às gravações comuns (só SAVE grava `appsOnly`
 * em `mode`); a preferência de lado fixo (`launchSide`) foi removida e as chaves `slot_*` nunca são
 * lidas (só gravadas, como sempre).
 */
internal class PreferenciasDasJanelas(private val armazem: ArmazemDeChaves) {

    fun carregar(): DadosSalvos = DadosSalvos(
        modo = ModoDeTela.doTexto(armazem.texto(MODO)),
        proporcao = ProporcaoDaDivisao.doTexto(armazem.texto(PROPORCAO)) ?: ProporcaoDaDivisao.UM_PARA_UM,
        proximoEhEsquerda = armazem.booleano(PROXIMO_ESQUERDA) ?: true,
        modoDaInterface = ModoDaInterface.doTexto(armazem.texto(MODO_DA_INTERFACE)) ?: ModoDaInterface.FLUTUANTE,
        appEsquerda = armazem.texto(APP_ESQUERDA).orEmpty(),
        appDireita = armazem.texto(APP_DIREITA).orEmpty(),
        abrirComoJanela = ListaDePacotes.ler(armazem.texto(APPS_EM_JANELA)).toCollection(LinkedHashSet()),
    )

    /**
     * Gravação comum (lado escolhido, proporção, interface…). O `mode` só é regravado quando o modo
     * atual NÃO é Side by Side: em `appsOnly` o padrão salvo por SAVE fica como está (D8).
     */
    fun gravarComum(modo: ModoDeTela, proporcao: ProporcaoDaDivisao, proximoEhEsquerda: Boolean, modoDaInterface: ModoDaInterface) {
        val textos = LinkedHashMap<String, String>()
        if (modo == ModoDeTela.APP_E_CARRO) textos[MODO] = ModoDeTela.APP_E_CARRO.texto
        textos[PROPORCAO] = proporcao.texto
        textos[MODO_DA_INTERFACE] = modoDaInterface.texto
        armazem.gravar(textos, mapOf(PROXIMO_ESQUERDA to proximoEhEsquerda))
    }

    /** SAVE: grava o Side by Side atual como padrão da partida. */
    fun gravarPadraoDoSideBySide(
        proporcao: ProporcaoDaDivisao,
        appEsquerda: String,
        appDireita: String,
        proximoEhEsquerda: Boolean,
        modoDaInterface: ModoDaInterface,
    ) {
        armazem.gravar(
            mapOf(
                MODO to ModoDeTela.DOIS_APPS.texto,
                ULTIMO_MODO_FORA_DO_SIDE_BY_SIDE to ModoDeTela.APP_E_CARRO.texto,
                PROPORCAO to proporcao.texto,
                APP_ESQUERDA to appEsquerda,
                APP_DIREITA to appDireita,
                MODO_DA_INTERFACE to modoDaInterface.texto,
            ),
            mapOf(PROXIMO_ESQUERDA to proximoEhEsquerda),
        )
    }

    /** `slot_<modo>_<lado>` (escrita-apenas, como sempre). */
    fun gravarUsoDoLado(modo: ModoDeTela, lado: Lado, uso: UsoDoLado) {
        armazem.gravar(mapOf("slot_${modo.texto}_${lado.texto}" to uso.texto))
    }

    fun gravarAbrirComoJanela(pacotes: Collection<String>) {
        armazem.gravar(mapOf(APPS_EM_JANELA to ListaDePacotes.escreverOrdenado(pacotes)))
    }

    companion object {
        const val MODO = "mode"
        const val ULTIMO_MODO_FORA_DO_SIDE_BY_SIDE = "lastNonAppsMode"
        const val PROPORCAO = "splitRatio"
        const val PROXIMO_ESQUERDA = "nextLeft"
        const val MODO_DA_INTERFACE = "uiMode"
        const val APP_ESQUERDA = "leftApp"
        const val APP_DIREITA = "rightApp"
        const val APPS_EM_JANELA = "window_apps"
    }
}

/**
 * C54: o conjunto de pacotes marcados "abrir como janela". Mudar o conjunto não mexe em janelas já
 * abertas; só grava.
 */
internal class AbrirComoJanela(
    inicial: Collection<String>,
    private val pacoteProprio: String,
    private val podeAlternar: (String) -> Boolean,
    private val gravar: (Set<String>) -> Unit,
) {
    private val pacotes = LinkedHashSet(inicial)

    fun contem(pacote: String): Boolean = pacote in pacotes

    fun alternar(pacote: String) {
        if (pacote == pacoteProprio || !podeAlternar(pacote)) return
        if (!pacotes.remove(pacote)) pacotes.add(pacote)
        gravar(pacotes.toSet())
    }

    /** Pacote desinstalado: sai do conjunto e grava, se estava lá. */
    fun esquecer(pacote: String) {
        if (pacotes.remove(pacote)) gravar(pacotes.toSet())
    }

    fun todos(): Set<String> = pacotes.toSet()
}
