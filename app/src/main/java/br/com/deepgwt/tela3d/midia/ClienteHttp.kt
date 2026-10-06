package br.com.deepgwt.tela3d.midia

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Cliente HTTP mínimo para capas e para a busca de vídeo (casos E6 e E8). */
internal object ClienteHttp {
    /** Identificação honesta do app: nunca simula navegador. */
    const val AGENTE_DE_USUARIO = "DeepGWT/1.0"

    const val LIMITE_DO_CORPO_DA_CAPA = 8 * 1024 * 1024
    private const val MAXIMO_DE_REDIRECIONAMENTOS = 5

    /**
     * GET que segue redirecionamentos (nunca de https para http), aceita só 2xx e entrega o corpo a
     * [consumir]. Devolve nulo em qualquer falha.
     */
    fun <T> obter(
        endereco: String,
        conectarMs: Int,
        lerMs: Int,
        cabecalhos: Map<String, String> = emptyMap(),
        consumir: (InputStream) -> T?,
    ): T? {
        var atual = endereco
        repeat(MAXIMO_DE_REDIRECIONAMENTOS + 1) {
            var conexao: HttpURLConnection? = null
            try {
                val url = URL(atual)
                if (url.protocol != "http" && url.protocol != "https") return null
                val aberta = url.openConnection() as? HttpURLConnection ?: return null
                conexao = aberta
                aberta.connectTimeout = conectarMs
                aberta.readTimeout = lerMs
                aberta.instanceFollowRedirects = false
                aberta.requestMethod = "GET"
                aberta.setRequestProperty("User-Agent", AGENTE_DE_USUARIO)
                for ((nome, valor) in cabecalhos) aberta.setRequestProperty(nome, valor)
                val codigo = aberta.responseCode
                if (codigo in 300..399) {
                    val destino = aberta.getHeaderField("Location") ?: return null
                    val proximo = URL(url, destino)
                    if (url.protocol == "https" && proximo.protocol != "https") return null
                    atual = proximo.toString()
                    return@repeat
                }
                if (codigo !in 200..299) return null
                return aberta.inputStream.use(consumir)
            } catch (_: IOException) {
                return null
            } catch (_: RuntimeException) {
                return null
            } finally {
                conexao?.disconnect()
            }
        }
        return null
    }

    /** Corpo inteiro, com limite de tamanho: passou do limite, falha (sem estourar a memória). */
    fun baixarBytes(endereco: String, conectarMs: Int, lerMs: Int, limite: Int = LIMITE_DO_CORPO_DA_CAPA): ByteArray? =
        obter(endereco, conectarMs, lerMs) { entrada -> lerComLimite(entrada, limite) }

    internal fun lerComLimite(entrada: InputStream, limite: Int): ByteArray? {
        val saida = ByteArrayOutputStream()
        val bloco = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = entrada.read(bloco)
            if (n < 0) break
            total += n
            if (total > limite) return null
            saida.write(bloco, 0, n)
        }
        return saida.toByteArray()
    }
}
