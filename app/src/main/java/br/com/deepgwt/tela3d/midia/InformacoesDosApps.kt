package br.com.deepgwt.tela3d.midia

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Base64
import java.io.ByteArrayOutputStream

/** O que o payload mostra do app de origem (casos D4 e D5). */
internal class InformacaoDoApp(
    val rotulo: String,
    /** `data:image/png;base64,…` 96×96, ou vazio. */
    val icone: String,
    val canLaunch: Boolean,
)

/**
 * Rótulo, ícone e "pode abrir" de um pacote, com cache por pacote (no máximo 8; ao tentar guardar o
 * 9º o cache é esvaziado). Falha ao obter o ícone vira vazio e também fica no cache. Usar na thread
 * principal.
 */
internal class InformacoesDosApps(private val contexto: Context) {
    private val cache = CacheQueEsvazia<String, InformacaoDoApp>(MAXIMO_DE_PACOTES)

    fun obter(pacote: String): InformacaoDoApp {
        if (pacote.isEmpty()) return InformacaoDoApp("", "", false)
        cache[pacote]?.let { return it }
        val nova = InformacaoDoApp(
            rotulo = rotuloDe(pacote),
            icone = iconeDe(pacote),
            canLaunch = podeAbrir(pacote),
        )
        cache.put(pacote, nova)
        return nova
    }

    private fun rotuloDe(pacote: String): String = try {
        val pm = contexto.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pacote, 0)).toString()
    } catch (_: Exception) {
        pacote
    }

    private fun podeAbrir(pacote: String): Boolean {
        if (PacotesDeProjecao.ehDeProjecao(pacote)) return true // a tela inicial sabe abrir AA/CarPlay
        return try {
            contexto.packageManager.getLaunchIntentForPackage(pacote) != null
        } catch (_: Exception) {
            false
        }
    }

    private fun iconeDe(pacote: String): String {
        var bitmap: Bitmap? = null
        return try {
            val desenho = contexto.packageManager.getApplicationIcon(pacote)
            bitmap = Bitmap.createBitmap(LADO_DO_ICONE, LADO_DO_ICONE, Bitmap.Config.ARGB_8888)
            desenho.setBounds(0, 0, LADO_DO_ICONE, LADO_DO_ICONE)
            desenho.draw(Canvas(bitmap))
            val saida = ByteArrayOutputStream(16 * 1024)
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, saida)) {
                ""
            } else {
                PREFIXO_PNG + Base64.encodeToString(saida.toByteArray(), Base64.NO_WRAP)
            }
        } catch (_: Exception) {
            ""
        } finally {
            bitmap?.recycle()
        }
    }

    companion object {
        const val MAXIMO_DE_PACOTES = 8
        const val LADO_DO_ICONE = 96
        private const val PREFIXO_PNG = "data:image/png;base64,"
    }
}
