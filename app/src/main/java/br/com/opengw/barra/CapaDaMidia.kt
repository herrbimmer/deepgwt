package br.com.opengw.barra

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import java.io.File

/** Capas: normalizar (≤ 720 px no maior lado) e decodificar de arquivo ou de URI (casos 14–15). */
internal object CapaDaMidia {

    /** Caso 15: igual se já cabe; senão reduz proporcionalmente, com filtro. */
    fun normalizar(b: Bitmap?): Bitmap? {
        if (b == null) return null
        return runCatching {
            val (l, a) = RegrasDaMidia.tamanhoNormalizado(b.width, b.height)
            if (l == b.width && a == b.height) b else Bitmap.createScaledBitmap(b, l, a, true)
        }.getOrNull()
    }

    /**
     * Caso 14 (2): `http`/`https` → nenhuma (não baixa, M-D12); sem esquema → arquivo local; senão
     * pelo `ContentResolver`, só os limites primeiro e amostrando. Erro ou recusa → nenhuma.
     */
    fun deUri(resolver: ContentResolver, texto: String?): Bitmap? {
        val t = texto?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching {
            val uri = Uri.parse(t)
            when (uri.scheme?.lowercase()) {
                "http", "https" -> null
                null, "" -> deArquivo(t)
                "file" -> uri.path?.let { deArquivo(it) }
                else -> {
                    val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }
                    if (limites.outWidth <= 0 || limites.outHeight <= 0) return@runCatching null
                    val opcoes = BitmapFactory.Options().apply {
                        inSampleSize = RegrasDaMidia.amostraParaCapa(limites.outWidth, limites.outHeight)
                    }
                    normalizar(resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opcoes) })
                }
            }
        }.getOrNull()
    }

    /** Um arquivo local, amostrado como no caso 14. */
    fun deArquivo(caminho: String): Bitmap? = runCatching {
        if (!File(caminho).isFile) return@runCatching null
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(caminho, limites)
        if (limites.outWidth <= 0 || limites.outHeight <= 0) return@runCatching null
        val opcoes = BitmapFactory.Options().apply {
            inSampleSize = RegrasDaMidia.amostraParaCapa(limites.outWidth, limites.outHeight)
        }
        normalizar(BitmapFactory.decodeFile(caminho, opcoes))
    }.getOrNull()

    /** Bytes de imagem embutida (tags do USB, caso 40): ≤ 8 MiB, amostra até 1 440, depois 720. */
    fun deBytes(bytes: ByteArray?): Bitmap? {
        if (bytes == null || bytes.isEmpty() || bytes.size > 8 * 1024 * 1024) return null
        return runCatching {
            val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, limites)
            if (limites.outWidth <= 0 || limites.outHeight <= 0) return@runCatching null
            val opcoes = BitmapFactory.Options().apply {
                inSampleSize = RegrasDaMidia.amostraAteCaber(limites.outWidth, limites.outHeight)
            }
            normalizar(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opcoes))
        }.getOrNull()
    }

    /** Um ícone desenhado num bitmap do tamanho intrínseco (ou 256×256). */
    fun deDrawable(d: Drawable?): Bitmap? {
        if (d == null) return null
        return runCatching {
            if (d is BitmapDrawable && d.bitmap != null) return@runCatching normalizar(d.bitmap)
            val l = d.intrinsicWidth.takeIf { it > 0 } ?: 256
            val a = d.intrinsicHeight.takeIf { it > 0 } ?: 256
            val b = Bitmap.createBitmap(l, a, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            d.setBounds(0, 0, l, a)
            d.draw(c)
            normalizar(b)
        }.getOrNull()
    }
}
