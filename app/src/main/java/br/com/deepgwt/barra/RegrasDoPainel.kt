// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.barra

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** As regras puras do painel expandido (painel casos 10–15, 20, 24, 32–34). Sem Android. */
internal object RegrasDoPainel {

    // ── Dispensar puxando (caso 10) ────────────────────────────────────────────────────

    const val CONSUMIR_A_PARTIR_DE_PX = 20f
    const val LIMIAR_DA_DISPENSA_PX = 80f
    const val DOMINIO = 1.2f

    /** "Vertical domina": vertical > 0 e ≥ 1,2 × |horizontal|. */
    fun verticalDomina(vertical: Float, horizontal: Float): Boolean = vertical > 0f && vertical >= DOMINIO * abs(horizontal)

    /** Projeção do lançamento (px) pela velocidade (px/s), decaimento 0,998. */
    fun projecao(velocidade: Float): Float = (velocidade / 1000f) * 0.998f / 0.002f

    /** Ao soltar: dispensa se domina e vertical + projeção > 80 px. */
    fun dispensar(vertical: Float, horizontal: Float, velocidade: Float): Boolean =
        verticalDomina(vertical, horizontal) && vertical + projecao(velocidade) > LIMIAR_DA_DISPENSA_PX

    // ── Cabeçalho (caso 11) ─────────────────────────────────────────────────────────────

    fun rotuloDaProjecao(projecao: String): String = when (projecao) {
        RegrasDaMidia.CARPLAY_APP -> "Apple CarPlay ativo no cluster"
        RegrasDaMidia.ANDROID_AUTO_APP -> "Android Auto ativo no cluster"
        "" -> "Dashboard do display 0"
        else -> "Projeção ativa"
    }

    /** O rótulo curto da projeção (título de reserva e marca d'água); null sem projeção. */
    fun rotuloCurtoDaProjecao(projecao: String): String? = when {
        projecao.isEmpty() -> null
        RegrasDaMidia.eCarPlay(projecao) -> "Apple CarPlay"
        RegrasDaMidia.eAndroidAuto(projecao) -> "Android Auto"
        else -> if (DecisoesDaBarra.PAINEL_TEXTOS_CORRIGIDOS) "Projeção" else "Projecao"
    }

    /** Caso 13: o app de referência (nome e ícone sem metadados). */
    fun appDeReferencia(projecao: String, escolhido: String, primeiroConfigurado: String?): String? = when {
        projecao.isNotEmpty() -> projecao
        escolhido.isNotEmpty() -> escolhido
        else -> primeiroConfigurado
    }

    // ── Valores do carro (caso 15) ──────────────────────────────────────────────────────

    private fun numeros(valores: List<String?>): List<Double> =
        valores.mapNotNull { it?.trim()?.takeIf { t -> t.isNotEmpty() }?.toDoubleOrNull()?.takeIf { d -> d.isFinite() } }

    /** Bateria: o primeiro número em 0..100 e > 0; senão o primeiro em 0..100; senão null. */
    fun bateria(valores: List<String?>): Double? {
        val n = numeros(valores)
        return n.firstOrNull { it in 0.0..100.0 && it > 0.0 } ?: n.firstOrNull { it in 0.0..100.0 }
    }

    /** Autonomia: o primeiro > 0; senão o primeiro ≥ 0; senão null. */
    fun autonomia(valores: List<String?>): Double? {
        val n = numeros(valores)
        return n.firstOrNull { it > 0.0 } ?: n.firstOrNull { it >= 0.0 }
    }

    /** Número sem casas desnecessárias (`38.0` → `38`). */
    fun textoDoNumero(v: Double?): String = when {
        v == null -> "--"
        v == v.toLong().toDouble() -> v.toLong().toString()
        else -> v.toString()
    }

    // ── Formatação (casos 32–34) ────────────────────────────────────────────────────────

    /** Caso 34: não número, ≤ −40, ≥ 85, = −1 ou = 255 → `--`; senão `"%.1f°C"`. */
    fun temperatura(texto: String?): String {
        val v = texto?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return "--"
        if (v <= -40.0 || v >= 85.0 || v == -1.0 || v == 255.0) return "--"
        return String.format(Locale.US, "%.1f°C", v)
    }

    /** Percentual: número → arredondado, limitado a 0..100, + "%"; senão `--`. */
    fun percentual(texto: String?): String {
        val v = texto?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return "--"
        return "${v.roundToInt().coerceIn(0, 100)}%"
    }

    /** Fração: número / 100 limitado a 0..1 (não número → 0). */
    fun fracao(texto: String?): Float =
        ((texto?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)

    /** Tempo de mídia `m:ss`; com [desconhecidoQuandoZero] e ≤ 0 → `--:--`. */
    fun tempo(ms: Long, desconhecidoQuandoZero: Boolean = false): String {
        if (desconhecidoQuandoZero && ms <= 0) return "--:--"
        val s = (ms.coerceAtLeast(0L) / 1000)
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
    }

    /** Caso 33: o valor do controle Energia. */
    fun energia(modelo: String?, reserva: String?, alvo: String?): String = when (modelo?.trim()) {
        "1" -> "EV Prior."
        "3" -> "EV"
        else -> if (reserva?.trim() == "2") {
            val n = alvo?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() }?.toInt()?.coerceIn(20, 80) ?: 50
            "HEV Prior. $n%"
        } else {
            "HEV Intel."
        }
    }

    fun regeneracao(v: String?): String = when (v?.trim()) {
        "2" -> "Baixo"
        "1" -> "Alto"
        else -> "Normal"
    }

    fun direcao(v: String?): String = when (v?.trim()) {
        "2" -> "Conforto"
        "1" -> "Sport"
        else -> "Normal"
    }

    /** Pronto: `1` ou `true` (sem maiúsculas). */
    fun pronto(v: String?): Boolean = v?.trim()?.lowercase(Locale.ROOT).let { it == "1" || it == "true" }

    // ── Controles de ciclo (caso 24) ────────────────────────────────────────────────────

    val CICLO_DA_CONDUCAO = listOf("2", "0", "1")
    val CICLO_DA_ENERGIA = listOf("0", "1", "3")
    val CICLO_DA_REGENERACAO = listOf("2", "0", "1")
    val CICLO_DA_DIRECAO = listOf("2", "0", "1")

    /** O item depois do atual; fora da lista → o primeiro. */
    fun proximoNoCiclo(atual: String?, ciclo: List<String>): String {
        val i = ciclo.indexOf(atual?.trim())
        return if (i < 0) ciclo.first() else ciclo[(i + 1) % ciclo.size]
    }

    // ── Volume do painel (caso 20) ──────────────────────────────────────────────────────

    const val PENDENTE_MS = 1_800L

    /** Um toque ±1: base = pendente, senão o do carro (limitado), senão o mostrado. */
    fun proximoVolume(pendente: Int?, doCarro: Int?, mostrado: Int, delta: Int, faixa: IntRange): Int {
        val base = pendente ?: doCarro?.coerceIn(faixa.first, faixa.last) ?: mostrado
        return (base + delta).coerceIn(faixa.first, faixa.last)
    }

    /** O valor do carro mudou: adota-o se não há pendente, se bate com o pendente, ou se o pendente é velho. */
    fun adotarDoCarro(pendente: Int?, pendenteEm: Long, doCarro: Int, agora: Long): Boolean =
        pendente == null || doCarro == pendente || agora - pendenteEm > PENDENTE_MS

    /** A faixa do volume do painel (P-D4). */
    fun faixaDoVolume(publicada: String?): IntRange =
        if (DecisoesDaBarra.PAINEL_VOLUME_NA_FAIXA_DO_CARRO) ContasDaBarra.faixaDoVolume(publicada) else 0..30

    // ── Mídia (casos 16–17) ─────────────────────────────────────────────────────────────

    /** "artista • álbum" com os não vazios, sem repetir; null se nenhum. */
    fun subtitulo(artista: String?, album: String?): String? =
        listOfNotNull(artista?.trim()?.takeIf { it.isNotEmpty() }, album?.trim()?.takeIf { it.isNotEmpty() })
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" • ")

    /** O selo do cartão Mídia (P-D2: com acento). */
    fun selo(tocando: Boolean, temTitulo: Boolean): String = when {
        tocando -> "PLAY"
        temTitulo -> if (DecisoesDaBarra.PAINEL_TEXTOS_CORRIGIDOS) "MÍDIA" else "MIDIA"
        else -> if (DecisoesDaBarra.PAINEL_TEXTOS_CORRIGIDOS) "ÁUDIO" else "AUDIO"
    }

    /** Subtítulo de reserva sem artista/álbum. */
    fun subtituloDeReserva(projecao: String, pacoteDaMidia: String?): String {
        val c = DecisoesDaBarra.PAINEL_TEXTOS_CORRIGIDOS
        return when {
            projecao.isNotEmpty() -> if (c) "Projeção ativa no cluster" else "Projecao ativa no cluster"
            !pacoteDaMidia.isNullOrEmpty() -> if (c) "Mídia do sistema" else "Midia do sistema"
            else -> if (c) "Sistema de áudio" else "Sistema de audio"
        }
    }

    /** A chave do fundo do álbum (caso 30). */
    fun chaveDoFundo(pacote: String?, titulo: String?, artista: String?, album: String?, largura: Int?, altura: Int?): String =
        listOfNotNull(pacote, titulo, artista, album, largura?.toString(), altura?.toString()).joinToString("|")

    /** Caso 26: ventilação do painel ±1 em 0..7 e o que fazer com o ar. */
    data class PassoDaVentilacao(val velocidade: Int, val ligarAr: Boolean?, )

    fun passoDaVentilacao(atual: String?, delta: Int, ligado: Boolean): PassoDaVentilacao {
        val v = ((atual?.trim()?.toIntOrNull() ?: 0) + delta).coerceIn(0, 7)
        val ar = when {
            v == 0 && ligado -> false
            v > 0 && !ligado -> true
            else -> null
        }
        return PassoDaVentilacao(v, ar)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
