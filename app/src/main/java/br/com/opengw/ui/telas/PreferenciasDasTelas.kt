package br.com.opengw.ui.telas

import android.content.Context
import android.content.SharedPreferences
import br.com.opengw.nucleo.OpenGw

/** O arquivo de preferências do app ("opengw_prefs"), no contexto protegido. */
internal fun preferenciasDoApp(): SharedPreferences =
    OpenGw.contextoProtegido.getSharedPreferences("opengw_prefs", Context.MODE_PRIVATE)
