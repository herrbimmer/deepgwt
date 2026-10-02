package br.com.opengw.telas

import java.util.Locale

/** Teclas Android e da central usadas pela mídia do Android Auto (parte 4). */
internal object TeclasDaMidia {
    const val NEXT = 87
    const val PREVIOUS = 88
    const val PLAY_PAUSE = 85
    const val PLAY = 126
    const val PAUSE = 127
    const val MUTE = 91
    const val VOLUME_MUTE = 164
    const val DOWN = 0
    const val UP = 1
    const val CENTRAL_ANTERIOR = 1002
    const val CENTRAL_PROXIMA = 1003
    const val CENTRAL_TOCAR_PAUSAR = 1004

    /** E1: as teclas do volante que o módulo olha. */
    val DO_VOLANTE = setOf(NEXT, PREVIOUS, PLAY_PAUSE, PLAY, PAUSE, MUTE, VOLUME_MUTE, CENTRAL_TOCAR_PAUSAR)
    val DE_TOCAR = setOf(PLAY_PAUSE, PLAY, PAUSE, CENTRAL_TOCAR_PAUSAR)
}

/** Uma tecla aceita pelo volante (para a regra da duplicata). */
internal data class TeclaAceita(val tecla: Int, val acao: Int, val em: Long)

/** Um aparelho da lista do canal do AA (4-A1, transação 13), com o que a escolha de 4-B usa. */
internal class AparelhoDoAa(
    val modoAtual: Int,
    val status: Int,
    val enlace: Int,
    val temBluetooth: Boolean,
    val temUsb: Boolean,
    /** Os campos crus, para mandar de volta ao canal. */
    val dados: Any? = null,
) {
    val semFio: Boolean get() = enlace == 2
}

/** As regras puras da parte 4. */
internal object RegrasDaMidia {

    /** A2: "canal ativo" = 3 (ativado), 7 (mostrando vídeo) ou 8 (primeira configuração). */
    fun canalAtivo(status: Int?): Boolean = status == 3 || status == 7 || status == 8

    private val NOMES_DO_ENLACE = mapOf(
        -1 to "sem aparelho", 0 to "iniciando", 1 to "disponível", 2 to "ativando", 3 to "ativado",
        4 to "desativando", 5 to "desativado", 6 to "falha de conexão", 7 to "mostrando vídeo",
        8 to "primeira configuração", 9 to "troca de usuário", 10 to "erro de conexão",
    )
    private val NOMES_DA_MUSICA = mapOf(0 to "não começou", 1 to "tocando", 2 to "pausada")

    fun nomeDoEnlace(status: Int?): String = status?.let { NOMES_DO_ENLACE[it] } ?: "desconhecido"
    fun nomeDaMusica(musica: Int?): String = musica?.let { NOMES_DA_MUSICA[it] } ?: "desconhecido"

    /** A3 (V16): ceder ao rádio nativo? */
    fun cedeAoRadio(rotaNativaAtiva: Boolean, estadoDoRadio: String?, fonteDoAudio: String?): Boolean {
        if (rotaNativaAtiva) return false
        if (estadoDoRadio?.trim() == "1") return true
        val fonte = fonteDoAudio?.trim()?.lowercase(Locale.ROOT) ?: return false
        if (fonte.isEmpty()) return false
        if (RegrasDoEspelhamento.pacoteDeEspelhamento(fonte) != null) return false
        return "radio" in fonte
    }

    private val ESTADO_INICIADO = Regex("""\bstate\s*[:=]\s*started\b""", RegexOption.IGNORE_CASE)

    /** A4 (V17): alguma linha de `USAGE_AAUTO_MEDIA` está em `state:started`. */
    fun audioDoAaTocando(dump: String?): Boolean {
        if (dump.isNullOrEmpty()) return false
        return dump.lines().any { it.contains("USAGE_AAUTO_MEDIA", ignoreCase = true) && ESTADO_INICIADO.containsMatchIn(it) }
    }

    /** A5: a evidência do DCM vale até 180 s. */
    fun evidenciaDoDcmValida(marcadaEm: Long, agora: Long, validadeMs: Long = 180_000L): Boolean =
        marcadaEm > 0 && agora - marcadaEm in 0..validadeMs

    /** A9, passo 3: a barra já diz que a mídia (ou o painel) é do AA. */
    fun aaPelaBarra(pacoteDaMidia: String?, espelhamentoNoPainel: String?): Boolean =
        RegrasDoEspelhamento.ehAndroidAuto(pacoteDaMidia) || RegrasDoEspelhamento.ehAndroidAuto(espelhamentoNoPainel)

    /**
     * A9, passos 5 a 8: AA ativo pela sessão. Entradas preguiçosas (cada uma pode ir ao shell) e
     * consultadas na ordem da especificação.
     */
    fun aaAtivoPelaSessao(
        ativoNoPainel: () -> String?,
        nucleoDaSessao: () -> Boolean,
        canal: () -> Int?,
        audio: () -> Boolean,
        haTarefaDoAa: () -> Boolean,
    ): Boolean {
        val sessao by lazy(LazyThreadSafetyMode.NONE) { nucleoDaSessao() }
        if (RegrasDoEspelhamento.ehAndroidAuto(ativoNoPainel()) && sessao) return true
        if (canalAtivo(canal())) return true
        if (audio()) return true
        return haTarefaDoAa() && sessao
    }

    /**
     * V18: a regra pura inteira, com todas as entradas na mão (o passo 3 de A9 a usa com
     * sessão = sim).
     */
    fun aaAtivo(
        ativoNoPainel: String?,
        pacoteDaMidia: String?,
        espelhamentoNoPainel: String?,
        canal: Int?,
        haTarefaDoAa: Boolean,
        sessao: Boolean,
        audio: Boolean,
    ): Boolean {
        if (aaPelaBarra(pacoteDaMidia, espelhamentoNoPainel) && sessao) return true
        return aaAtivoPelaSessao({ ativoNoPainel }, { sessao }, { canal }, { audio }, { haTarefaDoAa })
    }

    /** C2 (V19): o display alvo da mídia. */
    fun displayAlvo(aaNoPainel: Boolean, aaNaPrincipal: Boolean, destinoGravado: Int): Int = when {
        aaNoPainel -> 3
        aaNaPrincipal -> 0
        destinoGravado == 0 || destinoGravado == 3 -> destinoGravado
        else -> 0
    }

    /** M1: o ordinal AAP da tecla; null = não é tecla AAP. */
    fun ordinalAap(tecla: Int): Int? = when (tecla) {
        TeclasDaMidia.PLAY_PAUSE, TeclasDaMidia.CENTRAL_TOCAR_PAUSAR -> 6
        TeclasDaMidia.PLAY -> 7
        TeclasDaMidia.PAUSE -> 8
        TeclasDaMidia.PREVIOUS -> 9
        TeclasDaMidia.NEXT -> 10
        else -> null
    }

    /** E5 (V22): o alvo de uma tecla de tocar/pausar: true = tocar, false = pausar, null = nenhum. */
    fun alvoDoVolante(tecla: Int, tocandoNativo: Boolean?, barraTocando: Boolean): Boolean? = when (tecla) {
        TeclasDaMidia.PLAY -> true
        TeclasDaMidia.PAUSE -> false
        TeclasDaMidia.PLAY_PAUSE, TeclasDaMidia.CENTRAL_TOCAR_PAUSAR -> !(tocandoNativo ?: barraTocando)
        else -> null
    }

    /** E5 (V22): a tecla pede a reconciliação em segundo plano? */
    fun reconciliarVolante(tecla: Int, acao: Int, doVolante: Boolean, rotaNativa: Boolean, aaQuerOPainel: Boolean): Boolean =
        tecla in TeclasDaMidia.DE_TOCAR && acao == TeclasDaMidia.UP && doVolante && rotaNativa && aaQuerOPainel

    /** E3 (V25): mesma tecla e mesma ação em ≤ [janelaMs] da última aceita. */
    fun duplicataDoVolante(ultima: TeclaAceita?, tecla: Int, acao: Int, agora: Long, janelaMs: Long = 280L): Boolean =
        ultima != null && ultima.tecla == tecla && ultima.acao == acao && agora - ultima.em in 0..janelaMs

    /**
     * B: o aparelho a reconectar. Candidatos com modo atual = 4; status 1 primeiro, depois sem fio,
     * depois com Bluetooth, depois com USB; o primeiro.
     */
    fun aparelhoParaReconectar(aparelhos: List<AparelhoDoAa>): AparelhoDoAa? =
        aparelhos.filter { it.modoAtual == 4 }
            .sortedWith(
                compareByDescending<AparelhoDoAa> { it.status == 1 }
                    .thenByDescending { it.semFio }
                    .thenByDescending { it.temBluetooth }
                    .thenByDescending { it.temUsb },
            )
            .firstOrNull()
}
