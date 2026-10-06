// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.barra

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Fonte e fonte de áudio lidas da central de mídia da fabricante (mídia caso 24). */
data class CentralDeMidiaLida(val fonte: Int?, val fonteDeAudio: Int?) {
    /** Alguma das duas = 402. */
    val androidAutoNativo: Boolean get() = fonte == FONTE_AA || fonteDeAudio == FONTE_AA

    /** Alguma das duas em 10..14 (rádio, AM, FM, DRM, DAB). */
    val radioLocal: Boolean get() = (fonte != null && fonte in 10..14) || (fonteDeAudio != null && fonteDeAudio in 10..14)

    /** Alguma das duas = 2 (USB áudio). */
    val usb: Boolean get() = fonte == FONTE_USB || fonteDeAudio == FONTE_USB

    /** AA nativo ativo e não rádio local. */
    val ignorarProtecaoPorAA: Boolean get() = androidAutoNativo && !radioLocal

    val vazia: Boolean get() = fonte == null && fonteDeAudio == null

    companion object {
        const val FONTE_USB = 2
        const val FONTE_AA = 402
        val NENHUMA = CentralDeMidiaLida(null, null)
    }
}

/** O que o laço de desconexão USB limpa (caso 35). */
enum class LimpezaPorDesconexao { CARPLAY, ANDROID_AUTO, NADA }

/**
 * As regras puras da mídia (casos 11–13, 28, 34, 35, 38, 41–44, 52, 53, 58, 60, 61). Sem Android.
 */
object RegrasDaMidia {

    const val CARPLAY = "com.ts.carplay"
    const val CARPLAY_APP = "com.ts.carplay.app"
    const val ANDROID_AUTO = "com.ts.androidauto"
    const val ANDROID_AUTO_APP = "com.ts.androidauto.app"
    const val ANDROID_AUTO_PROJECAO = "com.ts.androidauto.projectionservice"
    const val USB = "com.beantechs.mediacenter.usb"
    const val MEDIACENTER = "com.beantechs.mediacenter"
    const val MEDIACENTER_H5 = "com.beantechs.mediacenter.h5.core"
    const val BLUETOOTH = "com.android.bluetooth"
    const val ONECAR = "com.onecar.onlinemusic"
    const val TELECOM = "com.android.server.telecom"

    @JvmField val PACOTES_CARPLAY: Set<String> = setOf(CARPLAY, CARPLAY_APP)
    @JvmField val PACOTES_AA: Set<String> = setOf(ANDROID_AUTO, ANDROID_AUTO_APP, ANDROID_AUTO_PROJECAO)
    @JvmField val PACOTES_DE_PROJECAO: Set<String> = PACOTES_CARPLAY + PACOTES_AA
    @JvmField val DO_CELULAR: Set<String> = setOf(BLUETOOTH, ONECAR)
    @JvmField val DA_CENTRAL: Set<String> = setOf(MEDIACENTER, MEDIACENTER_H5)
    @JvmField val SESSOES_DA_CENTRAL_USB: Set<String> = DA_CENTRAL + USB

    /** Notificação de quem NÃO é projeção e deve ceder a um dono projeção (caso 16, M-D10). */
    @JvmField val CEDEM_A_PROJECAO_NA_NOTIFICACAO: Set<String> = setOf(BLUETOOTH, MEDIACENTER, MEDIACENTER_H5, ONECAR)

    fun eCarPlay(p: String?) = p != null && p in PACOTES_CARPLAY
    fun eAndroidAuto(p: String?) = p != null && p in PACOTES_AA
    fun eProjecao(p: String?) = p != null && p in PACOTES_DE_PROJECAO
    fun doCelular(p: String?) = p != null && p in DO_CELULAR
    fun daCentral(p: String?) = p != null && p in DA_CENTRAL

    // ── Arbitragem das sessões (casos 11–13) ───────────────────────────────────────────

    /** Caso 11: uma sessão do celular (ou da central, com AA nativo) entra como o "tocando" do AA. */
    fun reservaDoAndroidAuto(
        candidato: String?,
        temMetadados: Boolean,
        dono: String?,
        projecaoNoPainel: String?,
        central: CentralDeMidiaLida,
        sessaoAAPronta: () -> Boolean,
    ): Boolean {
        if (!temMetadados) return false
        if (eCarPlay(dono) || eCarPlay(projecaoNoPainel)) return false
        if (eProjecao(candidato)) return false
        val celular = doCelular(candidato)
        if (celular && !sessaoAAPronta()) return false
        if (!celular && !(central.androidAutoNativo && daCentral(candidato))) return false
        return eAndroidAuto(dono) || eAndroidAuto(projecaoNoPainel) || central.androidAutoNativo
    }

    /** Caso 12: o dono é projeção e esta sessão não deve tomar o lugar dele. */
    fun manterProjecao(dono: String?, candidato: String?, temMetadados: Boolean, sessaoAAPronta: Boolean): Boolean {
        if (!eProjecao(dono) || eProjecao(candidato)) return false
        if (eAndroidAuto(dono) && temMetadados && doCelular(candidato) && !sessaoAAPronta) return false
        return if (!temMetadados) true else doCelular(candidato)
    }

    /** Caso 13: o USB é dono e a sessão candidata não o tira. */
    fun preservarDonoUsb(dono: String?, central: CentralDeMidiaLida, candidato: String?, candidatoTocando: Boolean): Boolean {
        if (dono != USB || !central.usb || eProjecao(candidato)) return false
        if (candidato != null && candidato in SESSOES_DA_CENTRAL_USB) return true
        return !candidatoTocando
    }

    // ── Rádio (caso 28) ─────────────────────────────────────────────────────────────────

    const val SINAL_PLAY_STATE = "sys.radio.play_state"
    const val SINAL_CANAL = "sys.radio.cur_channel_info"
    const val SINAL_CANAL_RDS = "sys.radio.rds_cur_channel_info"
    const val SINAL_FONTE_DE_AUDIO = "sys.basic.audio.source_app"

    /** Caso 28: este sinal diz que o rádio da central está tocando? */
    fun sinalDoRadioProtege(chave: String, valor: String?, inicial: Boolean): Boolean {
        val v = valor?.trim().orEmpty()
        if (v.isEmpty()) return false
        return when (chave) {
            SINAL_PLAY_STATE -> v.lowercase() in setOf("1", "playing", "play")
            SINAL_FONTE_DE_AUDIO -> v.lowercase().contains("radio")
            SINAL_CANAL, SINAL_CANAL_RDS ->
                !inicial && v != "{}" && v != "[]" && v != "0" && ContasDaBarra.inteirosDoTexto(v).any { it > 0 }
            else -> false
        }
    }

    // ── Projeção USB (caso 34) ──────────────────────────────────────────────────────────

    /** Caso 34: alguma linha é CONFIGURED ou CONNECTED. */
    fun usbDaProjecaoPronto(texto: String?): Boolean =
        texto.orEmpty().lineSequence().any {
            val l = it.trim().uppercase()
            l == "CONFIGURED" || l == "CONNECTED"
        }

    /** Caso 35: com o USB da projeção lido e não pronto, o que limpar. */
    fun limpezaPorDesconexao(dono: String?, sessaoAAPronta: Boolean): LimpezaPorDesconexao = when {
        eCarPlay(dono) -> LimpezaPorDesconexao.CARPLAY
        eAndroidAuto(dono) && !sessaoAAPronta -> LimpezaPorDesconexao.ANDROID_AUTO
        else -> LimpezaPorDesconexao.NADA
    }

    // ── USB pela central (caso 38) ──────────────────────────────────────────────────────

    /** Caso 38: publicar a faixa do USB da central? */
    fun publicarUsb(
        central: CentralDeMidiaLida,
        dono: String?,
        projecaoNoPainel: String?,
        concorrenteTocando: String?,
        transporteAAProntoOuChegou: Boolean,
        usbTocando: Boolean,
    ): Boolean {
        if (!central.usb || central.radioLocal) return false
        if (eCarPlay(dono) || eCarPlay(projecaoNoPainel)) return false
        if (concorrenteTocando != null) return false
        if (transporteAAProntoOuChegou && (eAndroidAuto(dono) || eAndroidAuto(projecaoNoPainel))) return false
        return dono == USB || usbTocando
    }

    // ── Progresso (casos 41–44) ─────────────────────────────────────────────────────────

    /** Limita a 0..duração (≥ 0 sem duração). */
    fun limitarPosicao(pos: Long, duracao: Long): Long =
        if (duracao > 0) pos.coerceIn(0L, duracao) else max(pos, 0L)

    /** Caso 44: a posição estimada agora. */
    fun estimativa(base: Long, duracao: Long, atualizadoEm: Long, agora: Long, tocando: Boolean): Long {
        var p = limitarPosicao(base, duracao)
        if (tocando && duracao > 0 && atualizadoEm > 0) p += max(agora - atualizadoEm, 0L)
        return limitarPosicao(p, duracao)
    }

    /** Caso 42: segurar uma regressão transitória da central no AA. */
    fun segurarRegressaoAA(
        dono: String?,
        anterior: Long,
        recebido: Long,
        duracaoAnterior: Long,
        duracao: Long,
        tocando: Boolean,
        permitida: Boolean,
    ): Boolean {
        if (permitida || !eAndroidAuto(dono)) return false
        if (!tocando && recebido > 1_000) return false
        if (anterior <= 3_000) return false
        if (duracao > 0 && anterior >= duracao - 2_000) return false
        if (duracaoAnterior > 0 && duracao > 0 && abs(duracaoAnterior - duracao) > 2_000) return false
        if (recebido <= 1_000) return true
        return recebido + 1_500 < anterior
    }

    /** Caso 43: a posição vinda da central (fonte 402), ou a estimativa quando não serve. */
    fun progressoNativoResolvido(
        anterior: Long,
        duracao: Long,
        atualizadoEm: Long,
        agora: Long,
        tocando: Boolean,
        faixaMudou: Boolean,
        nativo: Long?,
    ): Long {
        if (nativo != null) {
            val n = limitarPosicao(nativo, duracao)
            return if (segurarRegressaoAA(ANDROID_AUTO, anterior, n, duracao, duracao, tocando, faixaMudou)) {
                estimativa(anterior, duracao, atualizadoEm, agora, tocando)
            } else {
                n
            }
        }
        return if (faixaMudou) 0L else estimativa(anterior, duracao, atualizadoEm, agora, tocando)
    }

    /**
     * Caso 41 aplicado a um retrato: [base] é o retrato de antes (posição e duração anteriores);
     * [novo] já traz o resto do retrato novo (pacote, tocando...). A janela de troca de faixa
     * entra em [regressaoPermitida] por quem chama.
     */
    fun comProgresso(
        base: MidiaAgora,
        novo: MidiaAgora,
        duracao: Long,
        decorrido: Long,
        agora: Long,
        podeBuscar: Boolean,
        regressaoPermitida: Boolean,
    ): MidiaAgora {
        val d = max(duracao, 0L)
        val pos = limitarPosicao(decorrido, d)
        val segurar = segurarRegressaoAA(
            novo.pacote, base.decorridoMs, pos, base.duracaoMs, d, novo.tocando, regressaoPermitida
        )
        return if (segurar) {
            novo.copy(
                duracaoMs = if (d > 0) d else base.duracaoMs,
                decorridoMs = base.decorridoMs,
                progressoAtualizadoEmMs = base.progressoAtualizadoEmMs,
                podeBuscar = podeBuscar && d > 0,
            )
        } else {
            novo.copy(
                duracaoMs = d,
                decorridoMs = pos,
                progressoAtualizadoEmMs = if (d > 0 || pos > 0) agora else 0L,
                podeBuscar = podeBuscar && d > 0,
            )
        }
    }

    // ── Comandos do Android Auto (casos 50–53, 57) ─────────────────────────────────────

    const val INTERVALO_PROXIMA_MS = 650L
    const val INTERVALO_TOCAR_PAUSAR_MS = 2_000L

    /** Intervalo mínimo entre comandos: aceita se nunca houve ou se passou MAIS que o intervalo. */
    fun aceitaComando(agora: Long, ultimo: Long, intervalo: Long): Boolean =
        ultimo == 0L || agora - ultimo > intervalo

    /** Caso 52.1: o estado de reprodução efetivo do AA. [status] é o do monitor (desligado → null). */
    fun reproducaoEfetiva(
        visualTocando: Boolean,
        audioAtivo: Boolean?,
        status: Int?,
        nativo: Boolean?,
        rotaNativaAtiva: Boolean,
        palpite: Boolean,
    ): Boolean = when {
        audioAtivo == true -> true
        status == 1 -> true
        status == 2 -> false
        nativo == true -> true
        rotaNativaAtiva && !visualTocando && audioAtivo == false -> palpite
        else -> visualTocando
    }

    /** Caso 53: o comando pegou? */
    fun verificar(alvoTocar: Boolean, audioAtivo: Boolean, status: Int?, pararVale: Boolean): Boolean =
        if (alvoTocar) audioAtivo || status == 1
        else !audioAtivo && (status == 2 || (pararVale && status != 1))

    /** Caso 25: o estado lido da central vale para esta info? */
    fun estadoAceito(fonteDoEstado: Int, fonteDaInfo: Int?): Boolean =
        fonteDoEstado == CentralDeMidiaLida.FONTE_AA || fonteDoEstado == 0 ||
            (fonteDaInfo != null && fonteDoEstado == fonteDaInfo)

    // ── Mudo (casos 60–61) ──────────────────────────────────────────────────────────────

    /** Caso 60: um valor de mudo; desconhecido → null. */
    fun interpretarMudo(valor: String?): Boolean? = when (valor?.trim()?.lowercase()) {
        "1", "true", "on", "muted", "mute" -> true
        "0", "false", "off", "unmuted", "unmute" -> false
        else -> null
    }

    /** Caso 60: carro e cache de `mute_state` e `media_mute_state`; algum sim → sim. */
    fun combinarMudo(valores: List<String?>): Boolean? {
        val lidos = valores.mapNotNull { interpretarMudo(it) }
        if (lidos.isEmpty()) return null
        return lidos.any { it }
    }

    /** Caso 61: o volume decide antes do mudo lido. */
    fun mudoComVolume(mudoLido: Boolean?, volume: Int?, alvoMudo: Boolean?): Boolean? = when {
        volume == 0 -> true
        alvoMudo == false && volume != null && volume > 0 -> false
        else -> mudoLido
    }

    // ── Capa (casos 14–15) ──────────────────────────────────────────────────────────────

    const val LADO_MAXIMO_DA_CAPA = 720

    /** Caso 15: o tamanho da capa com o maior lado ≤ 720 (cada lado ≥ 1). */
    fun tamanhoNormalizado(largura: Int, altura: Int, maximo: Int = LADO_MAXIMO_DA_CAPA): Pair<Int, Int> {
        val maior = max(largura, altura)
        if (maior <= maximo || maior <= 0) return largura to altura
        val escala = maximo.toDouble() / maior
        return max((largura * escala).roundToInt(), 1) to max((altura * escala).roundToInt(), 1)
    }

    /** Caso 14: amostragem por potências de 2 enquanto metade/amostra ≥ 720 nos dois lados. */
    fun amostraParaCapa(largura: Int, altura: Int, alvo: Int = LADO_MAXIMO_DA_CAPA): Int {
        var amostra = 1
        if (largura <= 0 || altura <= 0) return amostra
        while ((largura / 2) / amostra >= alvo && (altura / 2) / amostra >= alvo) amostra *= 2
        return amostra
    }

    /** Caso 40: amostragem por potências de 2 até os dois lados ≤ [limite] (imagem das tags). */
    fun amostraAteCaber(largura: Int, altura: Int, limite: Int = 1_440): Int {
        var amostra = 1
        if (largura <= 0 || altura <= 0) return amostra
        while (largura / amostra > limite || altura / amostra > limite) amostra *= 2
        return amostra
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
