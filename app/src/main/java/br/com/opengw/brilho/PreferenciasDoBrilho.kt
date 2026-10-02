package br.com.opengw.brilho

import android.content.Context
import android.content.SharedPreferences
import java.math.BigDecimal

/**
 * As preferências do brilho automático, no formato que já está gravado nos carros (arquivo
 * `opengw_prefs`, contexto protegido). Um valor gravado com tipo inesperado vale como o padrão.
 */
internal class PreferenciasDoBrilho(contextoProtegido: Context) {

    private val prefs: SharedPreferences =
        contextoProtegido.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)

    val ligado: Boolean
        get() = booleano(LIGADO, false)

    fun ajustes() = Ajustes(
        usarSol = booleano(USAR_SOL, false),
        nivelDia = inteiro(NIVEL_DIA, 10),
        nivelNoite = inteiro(NIVEL_NOITE, 1),
        passoMin = inteiro(PASSO_MIN, 5),
        inicioDaNoiteHora = inteiro(INICIO_HORA, 20),
        inicioDaNoiteMinuto = inteiro(INICIO_MINUTO, 0),
        fimDaNoiteHora = inteiro(FIM_HORA, 6),
        fimDaNoiteMinuto = inteiro(FIM_MINUTO, 0),
    )

    /** A última posição gravada, se houver uma válida. */
    fun posicaoGravada(): Posicao? {
        val latitude = numero(LATITUDE) ?: return null
        val longitude = numero(LONGITUDE) ?: return null
        return if (Sol.coordenadaValida(latitude, longitude)) Posicao(latitude, longitude) else null
    }

    fun gravarPosicao(posicao: Posicao) {
        prefs.edit()
            .putString(LATITUDE, texto(posicao.latitude))
            .putString(LONGITUDE, texto(posicao.longitude))
            .apply()
    }

    fun gravarCidade(nome: String) {
        prefs.edit().putString(CIDADE, nome).apply()
    }

    private fun booleano(chave: String, padrao: Boolean): Boolean =
        try {
            prefs.getBoolean(chave, padrao)
        } catch (e: ClassCastException) {
            padrao
        }

    private fun inteiro(chave: String, padrao: Int): Int =
        try {
            prefs.getInt(chave, padrao)
        } catch (e: ClassCastException) {
            padrao
        }

    private fun numero(chave: String): Double? =
        try {
            prefs.getString(chave, null)?.trim()?.replace(',', '.')?.toDoubleOrNull()
        } catch (e: ClassCastException) {
            null
        }

    /** Número em texto simples, com ponto e sem notação científica (ex. `-16.686`). */
    private fun texto(grau: Double): String = BigDecimal.valueOf(grau).toPlainString()

    private companion object {
        const val ARQUIVO = "opengw_prefs"
        const val LIGADO = "enableAutoBrightness"
        const val USAR_SOL = "autoBrightnessUseSun"
        const val NIVEL_DIA = "autoBrightnessLevelDay"
        const val NIVEL_NOITE = "autoBrightnessLevelNight"
        const val PASSO_MIN = "autoBrightnessStepMin"
        const val INICIO_HORA = "nightStartHour"
        const val INICIO_MINUTO = "nightStartMinute"
        const val FIM_HORA = "nightEndHour"
        const val FIM_MINUTO = "nightEndMinute"
        const val LATITUDE = "autoBrightnessLastLat"
        const val LONGITUDE = "autoBrightnessLastLon"
        const val CIDADE = "autoBrightnessCity"
    }
}
