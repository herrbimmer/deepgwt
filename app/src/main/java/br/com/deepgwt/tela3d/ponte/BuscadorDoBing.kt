package br.com.deepgwt.tela3d.ponte

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Uma resposta HTTP já aberta (o que o [BuscadorDoBing] precisa dela). */
interface RespostaHttp {
    val codigo: Int
    fun cabecalho(nome: String): String?
    fun corpo(): InputStream
    fun fechar()
}

/**
 * `AppLauncherBridge.fetchJson`: GET HTTPS síncrono, **só** do Bing (a página cai no `fetch` dela para
 * qualquer outro endereço). Diferenças deliberadas em relação ao original, por decisão do dono:
 * corpo com teto de [TETO_DO_CORPO_EM_BYTES] (2 MB) e redirecionamentos seguidos à mão, cada salto
 * conferido contra a mesma regra de host (um redirecionamento do Bing para fora do Bing é recusado).
 *
 * Kotlin sem Android: a conexão vem de [abrir] (o `HttpURLConnection` em produção; falso nos testes).
 */
class BuscadorDoBing(
    private val diario: DiarioDaPonte = DiarioDaPonte.SILENCIOSO,
    private val abrir: (String) -> RespostaHttp = ::abrirComHttpUrlConnection,
) : BuscadorDeJson {

    override fun buscar(url: String?): String {
        var atual = url?.trim() ?: return ""
        if (!enderecoPermitido(atual)) return ""
        return try {
            for (salto in 0..MAXIMO_DE_REDIRECIONAMENTOS) {
                val resposta = abrir(atual)
                try {
                    val codigo = resposta.codigo
                    if (codigo in 300..399) {
                        val destino = resposta.cabecalho("Location")?.trim().orEmpty()
                        if (destino.isEmpty()) return ""
                        val absoluto = URI(atual).resolve(destino).toString()
                        if (!enderecoPermitido(absoluto)) {
                            diario.aviso("fetchJson: redirecionamento para fora do Bing recusado")
                            return ""
                        }
                        atual = absoluto
                        continue
                    }
                    if (codigo != 200) return ""
                    return lerComTeto(resposta.corpo(), TETO_DO_CORPO_EM_BYTES) ?: run {
                        diario.aviso("fetchJson: corpo acima do teto de 2 MB")
                        ""
                    }
                } finally {
                    resposta.fechar()
                }
            }
            diario.aviso("fetchJson: redirecionamentos demais")
            ""
        } catch (e: Exception) {
            diario.erro("fetchJson falhou", e)
            ""
        }
    }

    companion object {
        const val TETO_DO_CORPO_EM_BYTES = 2 * 1024 * 1024
        const val TEMPO_DE_CONEXAO_MS = 12_000
        const val TEMPO_DE_LEITURA_MS = 12_000
        const val MAXIMO_DE_REDIRECIONAMENTOS = 3
        const val USER_AGENT = "Mozilla/5.0 (compatible; HavalH6Viewer/1.0)"

        /** `https://www.bing.com/…` ou `https://bing.com/…` (e nada mais: nem `http`, nem outro host). */
        fun enderecoPermitido(url: String?): Boolean {
            val texto = url?.trim() ?: return false
            return texto.startsWith("https://www.bing.com/") || texto.startsWith("https://bing.com/")
        }

        /** Lê o fluxo inteiro até [teto] bytes; passou do teto devolve null. O fluxo é fechado. */
        fun lerComTeto(fluxo: InputStream, teto: Int): String? {
            fluxo.use { entrada ->
                val saida = ByteArrayOutputStream()
                val bloco = ByteArray(8192)
                while (true) {
                    val lidos = entrada.read(bloco)
                    if (lidos < 0) break
                    if (saida.size() + lidos > teto) return null
                    saida.write(bloco, 0, lidos)
                }
                return saida.toString(Charsets.UTF_8.name())
            }
        }

        private fun abrirComHttpUrlConnection(url: String): RespostaHttp {
            val conexao = URL(url).openConnection() as HttpURLConnection
            conexao.connectTimeout = TEMPO_DE_CONEXAO_MS
            conexao.readTimeout = TEMPO_DE_LEITURA_MS
            conexao.instanceFollowRedirects = false
            conexao.requestMethod = "GET"
            conexao.setRequestProperty("User-Agent", USER_AGENT)
            return object : RespostaHttp {
                override val codigo: Int get() = conexao.responseCode
                override fun cabecalho(nome: String): String? = conexao.getHeaderField(nome)
                override fun corpo(): InputStream = conexao.inputStream
                override fun fechar() = conexao.disconnect()
            }
        }
    }
}
