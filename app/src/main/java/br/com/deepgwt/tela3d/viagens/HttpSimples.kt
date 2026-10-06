package br.com.deepgwt.tela3d.viagens

import java.net.HttpURLConnection
import java.net.URL

/** O mínimo de HTTPS que o módulo precisa (tiles e Nominatim): GET com limites de 8 s, só vale 200. */
object HttpSimples {
    const val LIMITE_DE_CONEXAO_MS = 8_000
    const val LIMITE_DE_LEITURA_MS = 8_000

    /** Os bytes da resposta 200; `null` em qualquer outro código ou exceção. */
    fun obterBytes(url: String, agenteDeUsuario: String, aceita: String? = null): ByteArray? {
        var conexao: HttpURLConnection? = null
        return try {
            conexao = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = LIMITE_DE_CONEXAO_MS
                readTimeout = LIMITE_DE_LEITURA_MS
                requestMethod = "GET"
                setRequestProperty("User-Agent", agenteDeUsuario)
                if (aceita != null) setRequestProperty("Accept", aceita)
            }
            if (conexao.responseCode != HttpURLConnection.HTTP_OK) null else conexao.inputStream.use { it.readBytes() }
        } catch (e: Exception) {
            null
        } finally {
            conexao?.disconnect()
        }
    }

    /** O corpo (UTF-8) da resposta 200, ou `null`. */
    fun obterTexto(url: String, agenteDeUsuario: String, aceita: String? = null): String? =
        obterBytes(url, agenteDeUsuario, aceita)?.toString(Charsets.UTF_8)
}
