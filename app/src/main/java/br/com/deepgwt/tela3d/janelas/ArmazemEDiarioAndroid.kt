package br.com.deepgwt.tela3d.janelas

import android.content.SharedPreferences
import android.util.Log

/** O arquivo de preferências `deepgwt_lancador` como [ArmazemDeChaves]; só toca nas chaves pedidas. */
internal class ArmazemDeSharedPreferences(private val preferencias: SharedPreferences) : ArmazemDeChaves {

    override fun texto(chave: String): String? = try {
        preferencias.getString(chave, null)
    } catch (e: ClassCastException) {
        null
    }

    override fun booleano(chave: String): Boolean? = try {
        if (preferencias.contains(chave)) preferencias.getBoolean(chave, false) else null
    } catch (e: ClassCastException) {
        null
    }

    override fun gravar(textos: Map<String, String>, booleanos: Map<String, Boolean>) {
        val editor = preferencias.edit()
        for ((chave, valor) in textos) editor.putString(chave, valor)
        for ((chave, valor) in booleanos) editor.putBoolean(chave, valor)
        editor.apply()
    }

    companion object {
        const val NOME_DO_ARQUIVO = "deepgwt_lancador"
    }
}

/** Uma linha de logcat por etapa, com a etiqueta da tela 3D. */
internal class DiarioNoLogcat(private val etiqueta: String = ETIQUETA_PADRAO) : Diario {
    override fun info(mensagem: String) {
        Log.i(etiqueta, mensagem)
    }

    override fun aviso(mensagem: String) {
        Log.w(etiqueta, mensagem)
    }

    override fun erro(mensagem: String, causa: Throwable?) {
        Log.e(etiqueta, mensagem, causa)
    }

    companion object {
        const val ETIQUETA_PADRAO = "Tela3D.Janelas"
    }
}
