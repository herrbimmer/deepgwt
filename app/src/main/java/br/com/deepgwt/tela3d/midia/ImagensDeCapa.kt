package br.com.deepgwt.tela3d.midia

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.Locale

/** E2 sobre um bitmap: existe, não foi reciclado e passa no filtro de capa utilizável. */
internal fun Bitmap?.ehCapaUtilizavel(): Boolean =
    this != null && !isRecycled && RegrasDeCapa.ehUtilizavel(width, height)

internal fun Bitmap.dimensoes(): Dimensoes = Dimensoes(width, height)

/** Decodificação de imagens com redução de amostragem (defeito 14: sem estourar a memória). */
internal object DecodificadorDeImagem {
    fun deBytes(bytes: ByteArray): Bitmap? = try {
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, limites)
        val opcoes = BitmapFactory.Options().apply {
            inSampleSize = RegrasDeCapa.fatorDeAmostragem(limites.outWidth, limites.outHeight)
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opcoes)
    } catch (e: Exception) {
        Diario.aviso("imagem ilegível: ${e.message}")
        null
    } catch (e: OutOfMemoryError) {
        Diario.aviso("imagem grande demais para a memória")
        null
    }

    fun deArquivo(caminho: String): Bitmap? = try {
        val arquivo = File(caminho)
        if (!arquivo.isFile) {
            null
        } else {
            val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(caminho, limites)
            val opcoes = BitmapFactory.Options().apply {
                inSampleSize = RegrasDeCapa.fatorDeAmostragem(limites.outWidth, limites.outHeight)
            }
            BitmapFactory.decodeFile(caminho, opcoes)
        }
    } catch (e: Exception) {
        Diario.aviso("arquivo de capa ilegível: ${e.message}")
        null
    } catch (e: OutOfMemoryError) {
        Diario.aviso("arquivo de capa grande demais para a memória")
        null
    }

    /** Abre pelo resolvedor de conteúdo (esquemas `content:`, `file:`, `android.resource:`). */
    fun deUri(contexto: Context, uri: String): Bitmap? = try {
        val u = Uri.parse(uri)
        val resolvedor = contexto.contentResolver
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolvedor.openInputStream(u).usarOuNulo { BitmapFactory.decodeStream(it, null, limites) }
        val opcoes = BitmapFactory.Options().apply {
            inSampleSize = RegrasDeCapa.fatorDeAmostragem(limites.outWidth, limites.outHeight)
        }
        resolvedor.openInputStream(u).usarOuNulo { BitmapFactory.decodeStream(it, null, opcoes) }
    } catch (e: Exception) {
        Diario.aviso("URI de capa ilegível: ${e.message}")
        null
    } catch (e: OutOfMemoryError) {
        Diario.aviso("URI de capa grande demais para a memória")
        null
    }

    /** Como obter a imagem de uma URL/caminho vindo da central ou do CarPlay (caso H1). */
    fun deLocal(contexto: Context, url: String): Bitmap? = when (RegrasDeCapa.localDaImagem(url)) {
        RegrasDeCapa.LocalDaImagem.NENHUM -> null
        RegrasDeCapa.LocalDaImagem.ARQUIVO -> {
            val caminho = if (url.trim().lowercase(Locale.ROOT).startsWith("file:")) {
                Uri.parse(url.trim()).path.orEmpty()
            } else {
                url.trim()
            }
            deArquivo(caminho)
        }
        RegrasDeCapa.LocalDaImagem.CONTEUDO -> deUri(contexto, url.trim())
    }

    private inline fun <R> InputStream?.usarOuNulo(bloco: (InputStream) -> R): R? = this?.use(bloco)
}

/** Codificação da capa para o payload (caso E5). */
internal object CodificadorDeCapa {
    private const val QUALIDADE_DO_JPEG = 88
    private const val PREFIXO_JPEG = "data:image/jpeg;base64,"

    /** JPEG q88, lado máximo 720, base64 sem quebras. Nulo se a codificação falha. Rodar fora da principal. */
    fun paraDataUrl(original: Bitmap): String? {
        var copia: Bitmap? = null
        var escalado: Bitmap? = null
        return try {
            if (original.isRecycled) return null
            var base = original
            if (original.config == Bitmap.Config.HARDWARE) {
                base = original.copy(Bitmap.Config.ARGB_8888, false) ?: return null
                copia = base
            }
            val alvo = RegrasDeCapa.dimensoesCodificadas(base.width, base.height)
            if (alvo.largura != base.width || alvo.altura != base.height) {
                base = Bitmap.createScaledBitmap(base, alvo.largura, alvo.altura, true)
                escalado = base
            }
            val saida = ByteArrayOutputStream(64 * 1024)
            if (!base.compress(Bitmap.CompressFormat.JPEG, QUALIDADE_DO_JPEG, saida)) return null
            PREFIXO_JPEG + Base64.encodeToString(saida.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            Diario.aviso("falha ao codificar a capa: ${e.message}")
            null
        } catch (e: OutOfMemoryError) {
            Diario.aviso("sem memória para codificar a capa")
            null
        } finally {
            if (escalado != null && escalado !== original) escalado.recycle()
            if (copia != null && copia !== escalado) copia.recycle()
        }
    }
}

/** Amostra barata de pixels para a chave de capa por conteúdo (caso E1.8). */
internal object AmostradorDeCapa {
    private const val GRADE = 6

    /**
     * Chave estável de uma imagem: dimensões e um hash de uma grade de pixels. Bitmaps de hardware
     * não deixam ler pixel: nesses vale só o [reserva] (identidade da faixa).
     */
    fun chaveDe(bitmap: Bitmap, reserva: String): String {
        val l = bitmap.width
        val a = bitmap.height
        if (l <= 0 || a <= 0) return ChaveDeCapa.deAmostra(l, a, IntArray(0))
        if (bitmap.config == Bitmap.Config.HARDWARE || bitmap.isRecycled) {
            return "${l}x$a#$reserva"
        }
        return try {
            val amostra = IntArray(GRADE * GRADE)
            var i = 0
            for (gy in 0 until GRADE) {
                for (gx in 0 until GRADE) {
                    val x = ((gx + 0.5) / GRADE * l).toInt().coerceIn(0, l - 1)
                    val y = ((gy + 0.5) / GRADE * a).toInt().coerceIn(0, a - 1)
                    amostra[i++] = bitmap.getPixel(x, y)
                }
            }
            ChaveDeCapa.deAmostra(l, a, amostra)
        } catch (_: RuntimeException) {
            "${l}x$a#$reserva"
        }
    }
}

/** Converte um drawable em bitmap: se não é bitmap, desenha no tamanho intrínseco (ou 256 se ≤ 0). */
internal fun Drawable.paraBitmap(): Bitmap? {
    if (this is BitmapDrawable && bitmap != null) return bitmap
    val l = if (intrinsicWidth > 0) intrinsicWidth else 256
    val a = if (intrinsicHeight > 0) intrinsicHeight else 256
    return try {
        val bitmap = Bitmap.createBitmap(l, a, Bitmap.Config.ARGB_8888)
        val tela = Canvas(bitmap)
        setBounds(0, 0, l, a)
        draw(tela)
        bitmap
    } catch (_: Exception) {
        null
    } catch (_: OutOfMemoryError) {
        null
    }
}
