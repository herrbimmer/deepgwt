package br.com.opengw.rede

import java.security.MessageDigest
import java.util.Base64

internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/**
 * Instala um arquivo no aparelho sem nunca deixar um arquivo pela metade no lugar do destino: grava
 * num temporário (Base64 numa linha de shell), confere o SHA-256, e só então renomeia por cima do
 * destino e confere de novo. O renome é obrigatório: o `sh` lê um script aos poucos, e reescrever
 * em cima de um daemon rodando o corromperia.
 */
internal class InstaladorDeArquivo(
    private val shell: ExecutorDeShell,
    private val registro: RegistroDaRede,
) {

    /** true = o destino ficou com exatamente [conteudo]. Em qualquer divergência apaga o temporário. */
    fun instalar(destino: String, conteudo: ByteArray, sha256: String = sha256Hex(conteudo)): Boolean {
        if (conteudo.size > LIMITE_EM_BYTES) {
            registro.erro("não instalo $destino: ${conteudo.size} bytes passam de $LIMITE_EM_BYTES")
            return false
        }
        val temporario = "$destino.tmp"
        val base64 = Base64.getEncoder().encodeToString(conteudo)

        val gravado = shell.roteiro(
            "printf %s '$base64' | base64 -d > $temporario && chmod 755 $temporario && sha256sum $temporario",
        )
        if (primeiraPalavra(gravado) != sha256) {
            registro.erro("instalação de $destino: o temporário não confere (${primeiraPalavra(gravado)})")
            apagar(temporario)
            return false
        }
        val movido = shell.roteiro("mv -f $temporario $destino && sha256sum $destino")
        if (primeiraPalavra(movido) != sha256) {
            registro.erro("instalação de $destino: o destino não confere depois de mover (${primeiraPalavra(movido)})")
            apagar(temporario)
            return false
        }
        return true
    }

    private fun apagar(caminho: String) {
        shell.executar(arrayOf("rm", "-f", caminho))
    }

    /** `sha256sum` imprime `<hex>  <arquivo>`. */
    private fun primeiraPalavra(saida: String): String =
        saida.trim().lineSequence().firstOrNull()?.trim()?.split(Regex("\\s+"))?.firstOrNull().orEmpty().lowercase()

    companion object {
        /** O conteúdo vai num argumento de linha de comando: abaixo de ~64 KB. */
        const val LIMITE_EM_BYTES = 64 * 1024
    }
}
