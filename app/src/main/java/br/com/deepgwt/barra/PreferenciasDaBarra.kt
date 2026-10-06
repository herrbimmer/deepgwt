// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.barra

import android.content.Context
import android.content.SharedPreferences
import br.com.deepgwt.nucleo.DeepGwt
import br.com.deepgwt.nucleo.Preferencias

/**
 * Leitura das preferências da barra (arquivo "deepgwt_prefs", contexto protegido — formato gravado
 * nos carros). Valor de tipo errado ou ausente vale o padrão: nunca lança.
 */
internal interface FonteDePreferencias {
    fun contem(chave: String): Boolean
    fun booleano(chave: String, padrao: Boolean): Boolean
    fun inteiro(chave: String, padrao: Int): Int
    fun texto(chave: String): String?
}

/** As preferências de [SharedPreferences], tolerando tipo errado. */
internal class FonteDoSharedPreferences(private val prefs: SharedPreferences) : FonteDePreferencias {
    override fun contem(chave: String): Boolean = runCatching { prefs.contains(chave) }.getOrDefault(false)

    override fun booleano(chave: String, padrao: Boolean): Boolean =
        runCatching { prefs.getBoolean(chave, padrao) }.getOrDefault(padrao)

    override fun inteiro(chave: String, padrao: Int): Int =
        runCatching { prefs.getInt(chave, padrao) }.getOrElse {
            // Gravado como texto ou longo à mão: aproveita o número se houver.
            runCatching { prefs.all[chave]?.toString()?.trim()?.toDoubleOrNull()?.toInt() }.getOrNull() ?: padrao
        }

    override fun texto(chave: String): String? = runCatching { prefs.getString(chave, null) }.getOrNull()
}

/** O que `recarregarPreferencias` copia para o estado (estado §B.4). */
internal data class EspelhoDasPreferencias(
    val esconderSozinha: Boolean,
    val gestoParaCima: GestoParaCima,
    val pacoteDoGesto: String,
    val alturaDp: Int,
    val temperatura: ControleDeTemperatura,
    val mostrarVolume: Boolean,
    val mostrarModo: Boolean,
    val logoDoVolume: String,
    val textoDoLogo: String,
    val cartaoPeloCarro: Boolean,
    val indicadorLigado: Boolean,
    val indicadorFonteSp: Int,
    val indicadorCanto: Int,
    val indicadorXDp: Int,
    val indicadorYDp: Int,
)

internal object PreferenciasDaBarra {
    const val ARQUIVO = "deepgwt_prefs"

    /** O arquivo de preferências do app (contexto protegido), ou null se ainda não há contexto. */
    fun abrir(): SharedPreferences? =
        runCatching { DeepGwt.contextoProtegido.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE) }.getOrNull()

    fun fonte(): FonteDePreferencias? = abrir()?.let { FonteDoSharedPreferences(it) }

    /** Estado §E, caso 21. [licencaLiberada] é a outra metade do "indicador ligado". */
    fun lerEspelho(p: FonteDePreferencias, licencaLiberada: Boolean): EspelhoDasPreferencias =
        EspelhoDasPreferencias(
            esconderSozinha = p.booleano(Preferencias.BOTTOM_BAR_AUTO_HIDE.chave, false),
            gestoParaCima = ContasDaBarra.gestoParaCima(p.texto(Preferencias.BOTTOM_BAR_SWIPE_UP_ACTION.chave)),
            pacoteDoGesto = p.texto(Preferencias.BOTTOM_BAR_SWIPE_UP_PACKAGE.chave).orEmpty().trim(),
            alturaDp = ContasDaBarra.alturaValida(
                p.inteiro(Preferencias.BOTTOM_BAR_HEIGHT_DP.chave, Barra.ALTURA_PADRAO_DP)
            ),
            temperatura = ContasDaBarra.controleDeTemperatura(
                p.texto(Preferencias.BOTTOM_BAR_TEMP_CONTROL.chave),
                p.booleano(Preferencias.BOTTOM_BAR_SHOW_AC.chave, true),
                p.booleano(Preferencias.BOTTOM_BAR_SHOW_AC_DUAL_ZONE.chave, false),
            ),
            mostrarVolume = p.booleano(Preferencias.BOTTOM_BAR_SHOW_VOLUME.chave, false),
            mostrarModo = p.booleano(Preferencias.BOTTOM_BAR_SHOW_SPORT_PLUS.chave, true),
            logoDoVolume = p.texto(Preferencias.VOLUME_OVERLAY_LOGO_TYPE.chave) ?: "gwm",
            textoDoLogo = p.texto(Preferencias.VOLUME_OVERLAY_LOGO_CUSTOM_TEXT.chave).orEmpty(),
            cartaoPeloCarro = p.booleano(Preferencias.VOLUME_OVERLAY_ON_CAR_CHANGE.chave, true),
            indicadorLigado = p.booleano(Preferencias.ENABLE_RESOURCE_OVERLAY.chave, false) && licencaLiberada,
            indicadorFonteSp = p.inteiro(Preferencias.RESOURCE_OVERLAY_FONT_SP.chave, 14),
            indicadorCanto = p.inteiro(Preferencias.RESOURCE_OVERLAY_CORNER.chave, 3),
            indicadorXDp = p.inteiro(Preferencias.RESOURCE_OVERLAY_X.chave, 12),
            indicadorYDp = p.inteiro(Preferencias.RESOURCE_OVERLAY_Y.chave, 90),
        )

    /** Escreve no estado só os campos do espelho (não mexe em `visivel` nem nos menus). */
    fun aplicar(e: EspelhoDasPreferencias) {
        val s = EstadoDaBarra
        s.esconderSozinha = e.esconderSozinha
        s.gestoParaCima = e.gestoParaCima
        s.pacoteDoGesto = e.pacoteDoGesto
        s.alturaDp = e.alturaDp
        s.temperatura = e.temperatura
        s.mostrarVolume = e.mostrarVolume
        s.mostrarModo = e.mostrarModo
        s.logoDoVolume = e.logoDoVolume
        s.textoDoLogo = e.textoDoLogo
        s.cartaoPeloCarro = e.cartaoPeloCarro
        s.indicadorLigado = e.indicadorLigado
        s.indicadorFonteSp = e.indicadorFonteSp
        s.indicadorCanto = e.indicadorCanto
        s.indicadorXDp = e.indicadorXDp
        s.indicadorYDp = e.indicadorYDp
    }

    /** O overscan global explícito: só vale se a CHAVE existe. */
    fun overscanExplicito(p: FonteDePreferencias): Int? {
        val chave = Preferencias.PERSISTENT_BOTTOM_BAR_OVERSCAN.chave
        return if (p.contem(chave)) p.inteiro(chave, 0) else null
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
