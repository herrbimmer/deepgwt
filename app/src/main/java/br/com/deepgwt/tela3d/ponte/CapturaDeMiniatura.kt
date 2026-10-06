package br.com.deepgwt.tela3d.ponte

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.util.Base64
import android.view.PixelCopy
import android.view.View
import android.view.Window
import java.io.ByteArrayOutputStream

/**
 * `captureDesktopSnapshot`: copia a janela inteira da Activity (inclui WebGL, widgets e a UI nativa
 * dela, não os apps de outras janelas) para um bitmap pequeno e o devolve à página como JPEG em
 * `data:` URL. A cópia roda no Handler principal; a compressão, numa thread curta própria. O bitmap é
 * sempre liberado. Qualquer falha só é registrada: a página desiste sozinha depois de 400 ms.
 */
class CapturaDeMiniatura(
    private val janela: Window,
    private val handler: Handler,
    private val executarJs: (String) -> Unit,
    private val diario: DiarioDaPonte,
) {
    fun capturar(raiz: View, token: String, larguraPedida: Int, alturaPedida: Int) {
        val largura = raiz.width
        val altura = raiz.height
        if (largura <= 0 || altura <= 0) return
        val destino = Bitmap.createBitmap(
            MiniaturaDaArea.largura(larguraPedida), MiniaturaDaArea.altura(alturaPedida), Bitmap.Config.ARGB_8888,
        )
        try {
            PixelCopy.request(janela, Rect(0, 0, largura, altura), destino, { resultado ->
                if (resultado == PixelCopy.SUCCESS) {
                    comprimir(destino, token)
                } else {
                    destino.recycle()
                    diario.aviso("miniatura: a cópia de pixels falhou (código $resultado)")
                }
            }, handler)
        } catch (e: Exception) {
            destino.recycle()
            diario.erro("miniatura falhou", e)
        }
    }

    private fun comprimir(bitmap: Bitmap, token: String) {
        Thread({
            try {
                val saida = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, MiniaturaDaArea.QUALIDADE_DO_JPEG, saida)
                val url = MiniaturaDaArea.PREFIXO_DA_URL + Base64.encodeToString(saida.toByteArray(), Base64.NO_WRAP)
                executarJs(ScriptsDaTela3d.miniaturaDaArea(token, url))
            } catch (e: Exception) {
                diario.erro("miniatura: compressão falhou", e)
            } finally {
                bitmap.recycle()
            }
        }, "deepgwt-tela3d-miniatura").start()
    }
}
