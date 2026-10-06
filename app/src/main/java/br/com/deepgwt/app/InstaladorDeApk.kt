// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import br.com.deepgwt.nucleo.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Download e instalação de APK (`telas-e-servico-diversos.md` casos U4–U8). */
object InstaladorDeApk {

    private const val TIPO_APK = "application/vnd.android.package-archive"
    private const val BLOCO = 64 * 1024

    /** X-D2: valores nossos (a especificação só pede que existam). */
    private const val TEMPO_DE_CONEXAO_MS = 15_000
    private const val TEMPO_DE_LEITURA_MS = 30_000

    /** U4. Baixa para `<pasta externa do app>/update.apk`; progresso 0..1 na thread principal. */
    suspend fun baixar(
        contexto: Context,
        endereco: String,
        aoProgresso: suspend (Float) -> Unit = {},
    ): File {
        val destino = File(contexto.getExternalFilesDir(null) ?: contexto.filesDir, "update.apk")
        withContext(Dispatchers.IO) {
            val conexao = URL(endereco).openConnection() as HttpURLConnection
            try {
                conexao.connectTimeout = TEMPO_DE_CONEXAO_MS
                conexao.readTimeout = TEMPO_DE_LEITURA_MS
                val status = conexao.responseCode
                if (status != HttpURLConnection.HTTP_OK) throw IOException("download recusado: HTTP $status")
                val total = conexao.contentLengthLong
                val progresso = ProgressoInteiro(total)
                conexao.inputStream.use { entrada ->
                    destino.outputStream().use { saida ->
                        val buffer = ByteArray(BLOCO)
                        while (true) {
                            val lidos = entrada.read(buffer)
                            if (lidos < 0) break
                            saida.write(buffer, 0, lidos)
                            val novo = progresso.somar(lidos.toLong())
                            if (novo != null) withContext(Dispatchers.Main) { aoProgresso(novo / 100f) }
                        }
                    }
                }
            } finally {
                conexao.disconnect()
            }
        }
        return destino
    }

    /** U5. Instala pelo Shell (sem diálogo). true = o pm disse sucesso. Fora da principal. */
    suspend fun instalarPeloShell(arquivo: File): Boolean = withContext(Dispatchers.IO) {
        val saida = Shell.executar(arrayOf("sh", "-c", RegrasDaInstalacao.comandoDeInstalacao(arquivo.absolutePath)))
        saida.contains("success", ignoreCase = true)
    }

    /** U8. */
    @JvmStatic
    fun podePedirInstalacao(contexto: Context): Boolean = try {
        contexto.packageManager.canRequestPackageInstalls()
    } catch (e: Exception) {
        false
    }

    /** U6. */
    @JvmStatic
    fun intentDeInstalacao(contexto: Context, arquivo: File): Intent {
        val uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.provider", arquivo)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, TIPO_APK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** U7. */
    @JvmStatic
    fun intentDeFontesDesconhecidas(contexto: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${contexto.packageName}"))
}

/** Regras puras da instalação (testadas na JVM). */
internal object RegrasDaInstalacao {
    const val COPIA_TEMPORARIA = "/data/local/tmp/deepgwt-update.apk"

    /** Aspas simples de shell, com as aspas internas escapadas (X-D3). */
    fun aspas(texto: String): String = "'" + texto.replace("'", "'\\''") + "'"

    /** U5: copia, permissão, instala e apaga a cópia (mesmo se a instalação falhar). */
    fun comandoDeInstalacao(caminho: String): String {
        val tmp = COPIA_TEMPORARIA
        return "cp ${aspas(caminho)} $tmp && chmod 644 $tmp && pm install -r -d $tmp; rm -f $tmp"
    }
}

/** U4: o inteiro 0..100 do progresso; devolve o novo valor só quando ele muda. Sem tamanho → nunca. */
internal class ProgressoInteiro(private val total: Long) {
    private var lidos = 0L
    private var ultimo = -1

    fun somar(quantos: Long): Int? {
        if (total <= 0) return null
        lidos += quantos
        val p = ((lidos * 100) / total).coerceIn(0, 100).toInt()
        if (p == ultimo) return null
        ultimo = p
        return p
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
