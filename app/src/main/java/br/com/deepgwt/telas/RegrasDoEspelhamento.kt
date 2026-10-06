// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.telas

import java.util.Locale

/** Ícones fixos de 2-B2 (o recurso é escolhido pelo lado Android). */
internal enum class IconeFixo { ANDROID_AUTO, CARPLAY, YOUTUBE, YOUTUBE_MUSIC, GWM }

/** Nome e ícone para mostrar um app (parte 2, seção B). Puro; o `PackageManager` entra por função. */
internal object RotulosDosApps {
    private const val YOUTUBE = "com.google.android.youtube"
    private const val YOUTUBE_MUSIC = "com.google.android.apps.youtube.music"

    private fun ehAndroidAuto(minusculo: String) = "androidauto" in minusculo || "gearhead" in minusculo
    private fun ehCarPlay(minusculo: String) = "carplay" in minusculo || "carlink" in minusculo || "zlink" in minusculo

    /** B1: rótulo; [doSistema] devolve null quando o `PackageManager` falha. */
    fun rotulo(pacote: String, nome: String?, doSistema: (String) -> String?): String {
        if (!nome.isNullOrEmpty()) return nome
        val m = pacote.lowercase(Locale.ROOT)
        if (ehAndroidAuto(m)) return "Android Auto"
        if (ehCarPlay(m)) return "Apple CarPlay"
        if (pacote.equals(YOUTUBE, ignoreCase = true)) return "YouTube"
        if (pacote.equals(YOUTUBE_MUSIC, ignoreCase = true)) return "YouTube Music"
        val doPm = try {
            doSistema(pacote)
        } catch (e: Exception) {
            null
        }
        return doPm ?: pacote
    }

    /** B2: o ícone fixo, ou null (vale o do `PackageManager`). O nome não influi. */
    fun iconeFixo(pacote: String): IconeFixo? {
        val m = pacote.lowercase(Locale.ROOT)
        return when {
            ehAndroidAuto(m) -> IconeFixo.ANDROID_AUTO
            ehCarPlay(m) -> IconeFixo.CARPLAY
            pacote.equals(YOUTUBE, ignoreCase = true) -> IconeFixo.YOUTUBE
            pacote.equals(YOUTUBE_MUSIC, ignoreCase = true) -> IconeFixo.YOUTUBE_MUSIC
            m.startsWith("com.beantech") -> IconeFixo.GWM
            else -> null
        }
    }
}

/** O resultado da conferência de saúde do CarPlay no logcat (3-K6). */
internal data class SaudeDoCarPlay(val linhasDeCodec: Int, val linhasDeSuperficie: Int, val sessaoCaiu: Boolean) {
    val problemaDeCodec: Boolean get() = linhasDeCodec >= 6
    val problemaDeSuperficie: Boolean get() = linhasDeSuperficie >= 4

    /** Só codec ou superfície pedem recriar a janela; "sessão caiu" sozinha é estado do USB. */
    val pedeRecriar: Boolean get() = problemaDeCodec || problemaDeSuperficie
}

/** Ação de uma guarda do Android Auto (3-G1). */
internal enum class AcaoDaGuardaDoAa { CONFERIR, FOCO_DE_VIDEO }

/** Estados do orquestrador do CarPlay (3-K1). */
internal enum class EstadoDoCarPlay { DESCONECTADO, NA_PRINCIPAL, PREPARANDO_O_PAINEL, NO_PAINEL, VOLTANDO_A_PRINCIPAL, EM_RECUPERACAO }

/**
 * As regras puras da parte 3 (e as leituras de texto que ela usa): classificação, USB, superfície,
 * reuso, pid, velocidade, saúde do CarPlay, guardas e vigias. Tempo sempre por parâmetro.
 */
internal object RegrasDoEspelhamento {

    /** B1: o pacote canônico de espelhamento para um nome observado. CarPlay vence Android Auto. */
    fun pacoteDeEspelhamento(pacote: String?): String? {
        if (pacote == null) return null
        val m = pacote.lowercase(Locale.ROOT)
        if (m == "com.ts.carplay.app" || m == "com.ts.carplay" || "carplay" in m || "carlink" in m || "zlink" in m) {
            return PacotesDaCentral.CARPLAY
        }
        if (m == "com.ts.androidauto.app" || m == "com.ts.androidauto.projectionservice" ||
            "androidauto" in m || "gearhead" in m
        ) {
            return PacotesDaCentral.ANDROID_AUTO
        }
        return null
    }

    fun ehCarPlay(pacote: String?) = pacoteDeEspelhamento(pacote) == PacotesDaCentral.CARPLAY
    fun ehAndroidAuto(pacote: String?) = pacoteDeEspelhamento(pacote) == PacotesDaCentral.ANDROID_AUTO

    /** A6: alguma linha (aparada, em maiúsculas) é `CONFIGURED` ou `CONNECTED`. */
    fun usbConfigurado(texto: String?): Boolean {
        if (texto == null) return false
        return texto.lines().any {
            val l = it.trim().uppercase(Locale.ROOT)
            l == "CONFIGURED" || l == "CONNECTED"
        }
    }

    private val BUFFER = Regex("""activeBuffer=\[\s*(\d+)\s*x\s*(\d+)""")

    /** A7: largura × altura de um `activeBuffer=[<l>x<a>…`; null se não há. */
    fun lerBuffer(texto: String): Pair<Int, Int>? {
        val m = BUFFER.find(texto) ?: return null
        val l = m.groupValues[1].toIntOrNull() ?: return null
        val a = m.groupValues[2].toIntOrNull() ?: return null
        return l to a
    }

    /** A7: o tamanho do buffer no bloco da camada; null = desconhecido. */
    fun bufferDaCamada(dump: String, camada: String): Pair<Int, Int>? {
        val linhas = dump.lines()
        val inicio = linhas.indexOfFirst { "+ BufferLayer ($camada" in it }
        if (inicio < 0) return null
        val bloco = StringBuilder(linhas[inicio]).append('\n')
        for (i in inicio + 1 until linhas.size) {
            if (linhas[i].startsWith("+ ")) break
            bloco.append(linhas[i]).append('\n')
        }
        return lerBuffer(bloco.toString())
    }

    /** A7: "velha" = largura ≤ 1 ou altura ≤ 1; desconhecido não é velha. */
    fun superficieVelha(buffer: Pair<Int, Int>?): Boolean =
        buffer != null && (buffer.first <= 1 || buffer.second <= 1)

    fun superficieVelha(dump: String, camada: String): Boolean = superficieVelha(bufferDaCamada(dump, camada))

    /** A8: a saída de `am start` diz que a instância existente foi reaproveitada. */
    fun reusoDaInstancia(saida: String?): Boolean {
        if (saida == null) return false
        val m = saida.lowercase(Locale.ROOT)
        return "activity not started" in m && (
            "currently running top-most instance" in m ||
                "current task has been brought to the front" in m ||
                "brought to the front" in m
            )
    }

    /** A4: algum item da saída de `pidof`, separada por espaços, é número > 0. */
    fun pidVivo(saida: String?): Boolean {
        if (saida.isNullOrBlank()) return false
        return saida.trim().split(Regex("""\s+""")).any { (it.toLongOrNull() ?: 0L) > 0L }
    }

    private val NUMERO = Regex("""[-+]?\d+(?:[.,]\d+)?""")

    /** V24: o primeiro número (sinal opcional, decimal por `.` ou `,`) do texto da velocidade. */
    fun velocidade(texto: String?): Double? {
        if (texto == null) return null
        val m = NUMERO.find(texto) ?: return null
        return m.value.replace(',', '.').toDoubleOrNull()
    }

    private val BOOT_ID = Regex("""[0-9a-fA-F-]{16,}""")

    /** H3: a primeira sequência de 16+ caracteres `[0-9a-fA-F-]`; null se não há. */
    fun tokenDoBoot(texto: String?): String? = texto?.let { BOOT_ID.find(it)?.value }

    private val PACOTE_E_CLASSE = Regex("""([A-Za-z0-9._]+)/""")

    /** 2-E4 (reserva): o primeiro `<pacote>/<classe>` da saída do `dumpsys`. */
    fun pacoteDoDumpsys(texto: String?): String? {
        if (texto.isNullOrBlank()) return null
        return PACOTE_E_CLASSE.find(texto)?.groupValues?.get(1)
    }

    // ── saúde do CarPlay (K6, V12) ───────────────────────────────────────────────────────────────

    /** K6: lê o logcat (`-v threadtime,epoch`) e conta só as linhas com horário ≥ desde − 1 s. */
    fun saudeDoCarPlay(logcat: String, desdeSegundos: Double): SaudeDoCarPlay {
        var codec = 0
        var superficie = 0
        var sessao = false
        for (linha in logcat.lines()) {
            val campos = linha.trim().split(Regex("""\s+"""), limit = 2)
            val horario = campos.firstOrNull()?.toDoubleOrNull() ?: continue
            if (horario < desdeSegundos - 1.0) continue
            val m = linha.lowercase(Locale.ROOT)
            val ehCodec = "invalid bufidx" in m || "sf error code: -38" in m || "errcode=-19" in m ||
                (("ndkmediacodec" in m || "mediacodec" in m || "cpscreen" in m) &&
                    ("error" in m || "fail" in m || "invalid" in m))
            if (ehCodec) codec++
            if ("jsurface is NULL" in linha || ("setSurface" in linha && "NULL" in linha)) superficie++
            if ("isCarPlayConnected=false" in linha || "mCarPlayConnected=== false" in linha ||
                "mIsCarPlayConnected === false" in linha || "mDeviceConnectedState=0" in linha
            ) {
                sessao = true
            }
        }
        return SaudeDoCarPlay(codec, superficie, sessao)
    }

    /** K6: desde = max(início + 1,2 s, agora − 2,2 s). */
    fun desdeDaSaude(inicioSegundos: Double, agoraSegundos: Double): Double =
        maxOf(inicioSegundos + 1.2, agoraSegundos - 2.2)

    // ── guardas e vigias (V11, V13, V14) ─────────────────────────────────────────────────────────

    /** C8 (V11): remover as janelas do AA sem conexão? */
    fun removerAaSemConexao(
        usbConfigurado: Boolean,
        canalAtivo: Boolean,
        haTarefas: Boolean,
        aaQuerOPainel: Boolean,
        rotaNativa: Boolean,
        vistoVelhoEm: Long,
        agora: Long,
        toleranciaMs: Long,
    ): Boolean = !usbConfigurado && !canalAtivo && haTarefas && !aaQuerOPainel && !rotaNativa &&
        vistoVelhoEm > 0 && agora - vistoVelhoEm >= toleranciaMs

    private val PASSIVOS_DO_CARPLAY = setOf(
        "com.android.systemui",
        "com.beantechs.mediacenter",
        "com.beantechs.mediacenter.h5.core",
        "com.beantechs.vehiclecenter",
    )

    /** F3 (V13): a janela [pacote] pede conferir o CarPlay no painel? */
    fun guardaDoCarPlayConfere(pacote: String): Boolean {
        if (pacoteDeEspelhamento(pacote) != null) return false
        return pacote.lowercase(Locale.ROOT) !in PASSIVOS_DO_CARPLAY
    }

    /** G3 (V13): a ação da guarda do AA para a janela [pacote]; null = nada. */
    fun acaoDaGuardaDoAa(pacote: String, pacoteDoApp: String): AcaoDaGuardaDoAa? {
        if (pacoteDeEspelhamento(pacote) != null) return null
        if (pacote == pacoteDoApp) return AcaoDaGuardaDoAa.CONFERIR
        val m = pacote.lowercase(Locale.ROOT)
        if ("avm" in m || "camera" in m || "backcamera" in m) return AcaoDaGuardaDoAa.CONFERIR
        return AcaoDaGuardaDoAa.FOCO_DE_VIDEO
    }

    /** G3 (V13): pular quando a mesma janela com a mesma ação passou há ≤ [intervaloMs]. */
    fun pularGuardaDoAa(
        ultimoPacote: String?,
        ultimaAcao: AcaoDaGuardaDoAa?,
        ultimaEm: Long?,
        pacote: String,
        acao: AcaoDaGuardaDoAa,
        agora: Long,
        intervaloMs: Long,
    ): Boolean = ultimaEm != null && ultimoPacote == pacote && ultimaAcao == acao &&
        agora - ultimaEm in 0..intervaloMs

    /** G3 (V13): o AA saiu do painel e deve voltar? */
    fun aaDeveVoltarAoPainel(pacote: String, aaQuerOPainel: Boolean, aaNoPainel: Boolean, displayDoAa: Int?): Boolean =
        ehAndroidAuto(pacote) && aaQuerOPainel && !aaNoPainel && displayDoAa != null && displayDoAa != 3

    /** G2 (V13): as guardas do AA valem? */
    fun aaElegivelParaGuardas(ativoNoPainel: String?, aaQuerOPainel: Boolean): Boolean =
        ativoNoPainel == PacotesDaCentral.ANDROID_AUTO && aaQuerOPainel

    /** F8 (V14): tolerância de reconexão. */
    fun toleranciaDeReconexao(agora: Long, desconectouEm: Long, configurouEm: Long, prazoMs: Long): Boolean =
        desconectouEm > 0 && configurouEm > desconectouEm && agora - configurouEm in 0..prazoMs

    /** F8 (V14): quarentena do display 0. */
    fun quarentenaDo0(agora: Long, desconectouEm: Long, configurouEm: Long, vistoNo0Em: Long, prazoMs: Long): Boolean =
        desconectouEm > 0 && configurouEm > desconectouEm && vistoNo0Em > 0 && agora - vistoNo0Em in 0..prazoMs

    /** F8 (V14): instabilidade do USB. */
    fun instabilidade(
        querOPainel: Boolean,
        semTarefas: Boolean,
        usbConfigurado: Boolean,
        agora: Long,
        desconectouEm: Long,
        configurouEm: Long,
        prazoMs: Long,
    ): Boolean = querOPainel && semTarefas && (
        (!usbConfigurado && desconectouEm > 0 && agora - desconectouEm in 0..prazoMs) ||
            toleranciaDeReconexao(agora, desconectouEm, configurouEm, prazoMs)
        )

    /** K5 (V14): é seguro mover a pilha viva do CarPlay do 0 para o 3? */
    fun moverPilhaVivaEhSeguro(displayDaTarefa: Int, tarefasNaPilha: Int, haTarefaNoPainel: Boolean): Boolean =
        displayDaTarefa == 0 && tarefasNaPilha == 1 && !haTarefaNoPainel

    /** K1: o estado observado. */
    fun estadoObservado(carPlayNoPainel: Boolean, carPlayNaPrincipal: Boolean): EstadoDoCarPlay = when {
        carPlayNoPainel -> EstadoDoCarPlay.NO_PAINEL
        carPlayNaPrincipal -> EstadoDoCarPlay.NA_PRINCIPAL
        else -> EstadoDoCarPlay.DESCONECTADO
    }

    /** K1: "atualizar" — o estado depois de uma observação. */
    fun estadoAtualizado(atual: EstadoDoCarPlay, observado: EstadoDoCarPlay, reconciliar: Boolean): EstadoDoCarPlay {
        if (observado == atual) return atual
        if (reconciliar) return observado
        if (atual == EstadoDoCarPlay.PREPARANDO_O_PAINEL || atual == EstadoDoCarPlay.VOLTANDO_A_PRINCIPAL) return atual
        return observado
    }

    /** C9: a config que a abertura automática da partida deve usar. */
    fun configDeAberturaAutomatica(c: ConfigDoApp, moverProjecaoAoPainel: Boolean): ConfigDoApp {
        if (c.display != 3) return c
        if (pacoteDeEspelhamento(c.pacote) == null) return c
        if (moverProjecaoAoPainel) return c
        return c.copy(display = 0)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
