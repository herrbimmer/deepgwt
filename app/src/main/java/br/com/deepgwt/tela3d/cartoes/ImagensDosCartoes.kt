package br.com.deepgwt.tela3d.cartoes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper

/**
 * A imagem dos gráficos (atlas dos chassis do fluxo de energia): decodificada UMA vez por processo, fora da thread principal, e compartilhadas por todos os cartões.
 * Se faltarem, os gráficos usam o desenho de reserva.
 */
internal object ImagensDosCartoes {
    private const val CAMINHO_DO_ATLAS = "www/assets/power/graphics/approved-chassis-atlas.png"

    @Volatile var atlas: Bitmap? = null
        private set

    @Volatile private var estado = Estado.NAO_PEDIDO
    private val principal = Handler(Looper.getMainLooper())
    private val ouvintes = ArrayList<() -> Unit>()

    private enum class Estado { NAO_PEDIDO, CARREGANDO, PRONTO }

    /** Pede as imagens (na primeira vez) e chama [aoCarregar] na thread principal quando chegarem. */
    fun pedir(contexto: Context, aoCarregar: () -> Unit) {
        synchronized(this) {
            when (estado) {
                Estado.PRONTO -> {
                    principal.post(aoCarregar)
                    return
                }
                Estado.CARREGANDO -> {
                    ouvintes += aoCarregar
                    return
                }
                Estado.NAO_PEDIDO -> {
                    estado = Estado.CARREGANDO
                    ouvintes += aoCarregar
                }
            }
        }
        val assets = contexto.applicationContext.assets
        Thread({
            val a = decodificar(assets, CAMINHO_DO_ATLAS)
            principal.post {
                atlas = a
                val avisar: List<() -> Unit>
                synchronized(this) {
                    estado = Estado.PRONTO
                    avisar = ouvintes.toList()
                    ouvintes.clear()
                }
                avisar.forEach { it() }
            }
        }, "cartoes-imagens").start()
    }

    private fun decodificar(assets: android.content.res.AssetManager, caminho: String): Bitmap? = try {
        assets.open(caminho).use { BitmapFactory.decodeStream(it) }
    } catch (_: Exception) {
        null
    } catch (_: OutOfMemoryError) {
        null
    }
}
