package br.com.opengw.ponte

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import br.com.opengw.midia.CapaParaTema
import br.com.opengw.veiculo.Comandos
import br.com.opengw.veiculo.Veiculo
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** O carro de verdade, pelas fachadas [Veiculo] e [Comandos]. */
object CarroDoVeiculo : Carro {
    override fun ler(chave: String): String? = Veiculo.ler(chave)

    override fun lerDoCarro(chaves: List<String>): Map<String, String> =
        Veiculo.lerDoCarro(chaves.toTypedArray())

    override fun acompanhar(chaves: Collection<String>) {
        Veiculo.acompanhar(chaves)
    }

    override fun escrever(chave: String, valor: String) {
        Veiculo.escrever(chave, valor)
    }

    override fun sairDoArNoMaximo() {
        Comandos.sairDoArNoMaximo()
    }

    override fun abrirCamera360() {
        Comandos.abrirCamera360()
    }
}

/** Registro no logcat, etiqueta `OpenGW.Ponte`. */
object RegistroNoLogcat : RegistroDaPonte {
    private const val ETIQUETA = "OpenGW.Ponte"

    override fun info(mensagem: String) {
        Log.i(ETIQUETA, mensagem)
    }

    override fun aviso(mensagem: String) {
        Log.w(ETIQUETA, mensagem)
    }

    override fun erro(mensagem: String, causa: Throwable?) {
        Log.e(ETIQUETA, mensagem, causa)
    }
}

/**
 * Principal pelo `Handler` do looper principal; fundo numa thread só, que morre depois de 30 s
 * parada (cada painel recriado tem a sua ponte e não pode deixar thread para trás).
 */
class AgendadorDoApp(private val registro: RegistroDaPonte = RegistroNoLogcat) : Agendador {

    private val principal = Handler(Looper.getMainLooper())

    private val fundo = ThreadPoolExecutor(0, 1, 30L, TimeUnit.SECONDS, LinkedBlockingQueue()) { r ->
        Thread(r, "OpenGW-Ponte").apply { isDaemon = true }
    }

    override fun repetir(intervaloMs: Long, tarefa: () -> Unit): Cancelavel {
        val repeticao = object : Runnable {
            @Volatile
            var ativa = true

            override fun run() {
                if (!ativa) return
                try {
                    tarefa()
                } catch (e: Throwable) {
                    registro.erro("tarefa repetida da ponte falhou", e)
                }
                if (ativa) principal.postDelayed(this, intervaloMs)
            }
        }
        principal.postDelayed(repeticao, intervaloMs)
        return Cancelavel {
            repeticao.ativa = false
            principal.removeCallbacks(repeticao)
        }
    }

    override fun emSegundoPlano(tarefa: () -> Unit) {
        fundo.execute {
            try {
                tarefa()
            } catch (e: Throwable) {
                registro.erro("tarefa de fundo da ponte falhou", e)
            }
        }
    }
}

/** A capa (um [Bitmap]) reduzida a no máximo 320 px no maior lado, sem ampliar, em PNG base64. */
object CodificadorDeCapaPng : CodificadorDeCapa {
    override fun dataUrl(capa: Any): String {
        val imagem = capa as? Bitmap ?: return ""
        return try {
            if (imagem.isRecycled) return ""
            val (largura, altura) = CapaParaTema.dimensoes(imagem.width, imagem.height)
            if (largura <= 0 || altura <= 0) return ""
            val reduzida = if (largura == imagem.width && altura == imagem.height) {
                imagem
            } else {
                Bitmap.createScaledBitmap(imagem, largura, altura, true)
            }
            val saida = ByteArrayOutputStream()
            reduzida.compress(Bitmap.CompressFormat.PNG, 100, saida)
            if (reduzida !== imagem) reduzida.recycle()
            "data:image/png;base64," + Base64.getEncoder().encodeToString(saida.toByteArray())
        } catch (e: Exception) {
            ""
        }
    }
}
