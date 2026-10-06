// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.barra

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** O retrato do "tocando agora". Imutável: cada mudança é um objeto novo (M-D6). */
data class MidiaAgora(
    /**
     * Origem; pode ser sintético: "com.ts.carplay", "com.ts.androidauto",
     * "com.beantechs.mediacenter.usb" (os leitores conhecem esses textos).
     */
    val pacote: String?,
    val titulo: String?,
    val artista: String?,
    val album: String?,
    /** Maior lado ≤ 720 px; objeto novo a cada capa nova. */
    val capa: Bitmap?,
    val tocando: Boolean,
    val mudo: Boolean,
    /** 0 = desconhecida. */
    val duracaoMs: Long,
    val decorridoMs: Long,
    /** Relógio monotônico (`elapsedRealtime`); 0 = nenhuma posição. */
    val progressoAtualizadoEmMs: Long,
    val podeBuscar: Boolean,
) {
    /** O retrato sem mídia nenhuma, mantendo o mudo (limpezas, caso 45). */
    internal fun zerado(): MidiaAgora = VAZIA.copy(mudo = mudo)

    companion object {
        @JvmField
        val VAZIA: MidiaAgora = MidiaAgora(
            pacote = null, titulo = null, artista = null, album = null, capa = null,
            tocando = false, mudo = false, duracaoMs = 0L, decorridoMs = 0L,
            progressoAtualizadoEmMs = 0L, podeBuscar = false,
        )
    }
}

/**
 * O "tocando agora" da barra e os comandos de mídia. O retrato é lido de qualquer thread; os
 * comandos voltam na hora e trabalham em segundo plano. Sem o serviço da barra de pé, os comandos
 * não fazem nada (e as consultas do Android Auto devolvem false/null).
 */
object MidiaDaBarra {

    private var retrato by mutableStateOf(MidiaAgora.VAZIA)

    /** Retrato atual. Observável pelo Compose; legível de qualquer thread (objeto imutável). */
    @JvmStatic
    val agora: MidiaAgora
        get() = retrato

    /** Só a mídia escreve, e sempre na principal. */
    internal fun publicar(novo: MidiaAgora) {
        retrato = novo
    }

    /** O motor de pé (enquanto o serviço da barra vive). */
    @Volatile
    internal var motor: MotorDaMidia? = null

    /** Posição estimada agora (caso 44). */
    @JvmStatic
    @JvmOverloads
    fun decorridoEstimado(agoraMs: Long = ThreadPrincipal.agoraMs()): Long {
        val m = agora
        return RegrasDaMidia.estimativa(m.decorridoMs, m.duracaoMs, m.progressoAtualizadoEmMs, agoraMs, m.tocando)
    }

    // ── Comandos (painel, depuração e lançador) ────────────────────────────────────────

    @JvmStatic
    fun tocarOuPausar() {
        motor?.tocarOuPausar()
    }

    @JvmStatic
    fun proxima() {
        motor?.pular(paraFrente = true)
    }

    @JvmStatic
    fun anterior() {
        motor?.pular(paraFrente = false)
    }

    @JvmStatic
    fun buscar(posicaoMs: Long) {
        motor?.buscar(posicaoMs)
    }

    // ── Para o lançador (Android Auto) ─────────────────────────────────────────────────

    @JvmStatic
    fun rotaNativaDoAndroidAutoAtiva(): Boolean = motor?.rotaNativaDoAndroidAutoAtiva() ?: false

    /** Lê a central (Binder): não chamar na principal. */
    @JvmStatic
    fun androidAutoTocandoPelaCentral(): Boolean? = motor?.androidAutoTocandoPelaCentral()

    /** Hoje sem chamador vivo (M-7); é o caso 50. */
    @JvmStatic
    suspend fun androidAutoProxima(): Boolean = motor?.comandoDePuloAA(true, "lancador_proxima") ?: false

    @JvmStatic
    suspend fun androidAutoAnterior(): Boolean = motor?.comandoDePuloAA(false, "lancador_anterior") ?: false

    @JvmStatic
    suspend fun androidAutoReproducaoPelaCentral(tocar: Boolean, motivo: String): Boolean =
        motor?.reproducaoAAPelaCentral(tocar, motivo) ?: false

    @JvmStatic
    fun androidAutoTrocouDeFaixa(motivo: String): Boolean = motor?.trocouDeFaixa(motivo) ?: false

    @JvmStatic
    fun alternarMudoDoAndroidAuto(): Boolean = motor?.alternarMudoDoAndroidAuto() ?: false

    /** O volante pediu tocar/pausar no Android Auto: refletir já. Na principal. */
    @JvmStatic
    fun dicaDoVolanteAndroidAuto(tocando: Boolean) {
        motor?.dicaDoVolante(tocando)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
