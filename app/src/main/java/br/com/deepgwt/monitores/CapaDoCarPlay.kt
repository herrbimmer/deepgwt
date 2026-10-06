// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.monitores

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.security.MessageDigest

/** Regras puras da capa (B.5): fator de amostragem e normalização para no máximo 720 de lado. */
internal object RegrasDaCapa {
    const val LADO_MAXIMO = 720

    /** Dimensões finais de um bitmap. */
    data class Dimensoes(val largura: Int, val altura: Int)

    /**
     * O `inSampleSize` clássico: potência de 2; dobra enquanto metade da altura e metade da largura,
     * divididas pelo fator, ainda forem >= 720.
     */
    fun fatorDeAmostragem(largura: Int, altura: Int): Int {
        var fator = 1
        val metadeDaAltura = altura / 2
        val metadeDaLargura = largura / 2
        while (metadeDaAltura / fator >= LADO_MAXIMO && metadeDaLargura / fator >= LADO_MAXIMO) {
            fator *= 2
        }
        return fator
    }

    /**
     * Se o maior lado passa de 720, reduz proporcionalmente até o maior lado ser 720 (cada lado
     * arredondado, mínimo 1). Senão devolve as mesmas dimensões.
     */
    fun normalizar(largura: Int, altura: Int): Dimensoes {
        val maior = maxOf(largura, altura)
        if (maior <= LADO_MAXIMO) return Dimensoes(largura, altura)
        val escala = LADO_MAXIMO.toDouble() / maior
        return Dimensoes(
            maxOf(1, Math.round(largura * escala).toInt()),
            maxOf(1, Math.round(altura * escala).toInt()),
        )
    }

    /** O esquema do caminho (minúsculo) ou nulo se for caminho de arquivo puro. */
    fun esquemaDoCaminho(caminho: String): String? {
        val dois = caminho.indexOf(':')
        if (dois <= 0) return null
        val candidato = caminho.substring(0, dois)
        if (!candidato[0].isLetter()) return null
        if (!candidato.all { it.isLetterOrDigit() || it == '+' || it == '.' || it == '-' }) return null
        return candidato.lowercase()
    }
}

/**
 * Guarda só a última capa decodificada e reaproveita o mesmo objeto quando os bytes são os mesmos
 * (tamanho e hash do conteúdo), DB10. Seguro entre threads.
 */
internal class CacheDaUltimaCapa<T : Any> {

    private class Entrada<T>(val tamanho: Int, val hash: ByteArray, val valor: T)

    private var ultima: Entrada<T>? = null

    /** O objeto da última decodificação se [bytes] são os mesmos; senão decodifica com [decodificar] e guarda. */
    @Synchronized
    fun obter(bytes: ByteArray, decodificar: (ByteArray) -> T?): T? {
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
        val anterior = ultima
        if (anterior != null && anterior.tamanho == bytes.size && anterior.hash.contentEquals(hash)) {
            return anterior.valor
        }
        val novo = decodificar(bytes) ?: return null
        ultima = Entrada(bytes.size, hash, novo)
        return novo
    }

    @Synchronized
    fun esquecer() {
        ultima = null
    }
}

/** Decodifica a capa de um quadro (bytes, senão caminho) para um bitmap de no máximo 720×720. */
internal class ProdutorDeCapaDoCarPlay(
    contexto: Context,
    private val registro: RegistroDosMonitores,
) {
    private val resolvedor = contexto.applicationContext.contentResolver
    private val ultimaPorBytes = CacheDaUltimaCapa<Bitmap>()

    /** Nunca lança: qualquer falha é "sem capa". */
    fun obter(quadro: QuadroDoCarPlay): Bitmap? {
        val bytes = quadro.bytesDaCapa
        if (bytes != null && bytes.isNotEmpty()) {
            val dosBytes = try {
                ultimaPorBytes.obter(bytes, ::decodificarBytes)
            } catch (e: Exception) {
                registro.erro("capa pelos bytes", e)
                null
            }
            if (dosBytes != null) return dosBytes
        }
        val caminho = quadro.caminhoDaCapa
        if (caminho.isNullOrEmpty()) return null
        return try {
            decodificarCaminho(caminho)
        } catch (e: SecurityException) {
            registro.aviso("capa pelo caminho: sem permissão")
            null
        } catch (e: Exception) {
            registro.erro("capa pelo caminho", e)
            null
        }
    }

    private fun decodificarBytes(bytes: ByteArray): Bitmap? {
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, limites)
        if (limites.outWidth <= 0 || limites.outHeight <= 0) return null
        val opcoes = BitmapFactory.Options().apply {
            inSampleSize = RegrasDaCapa.fatorDeAmostragem(limites.outWidth, limites.outHeight)
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opcoes) ?: return null
        return normalizar(bitmap)
    }

    private fun decodificarCaminho(caminho: String): Bitmap? {
        when (RegrasDaCapa.esquemaDoCaminho(caminho)) {
            "http", "https" -> return null // nunca rede
            null -> return decodificarArquivo(caminho)
            else -> return decodificarPeloResolvedor(Uri.parse(caminho))
        }
    }

    private fun decodificarArquivo(caminho: String): Bitmap? {
        if (!File(caminho).isFile) return null
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(caminho, limites)
        if (limites.outWidth <= 0 || limites.outHeight <= 0) return null
        val opcoes = BitmapFactory.Options().apply {
            inSampleSize = RegrasDaCapa.fatorDeAmostragem(limites.outWidth, limites.outHeight)
        }
        val bitmap = BitmapFactory.decodeFile(caminho, opcoes) ?: return null
        return normalizar(bitmap)
    }

    private fun decodificarPeloResolvedor(uri: Uri): Bitmap? {
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolvedor.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) } ?: return null
        if (limites.outWidth <= 0 || limites.outHeight <= 0) return null
        val opcoes = BitmapFactory.Options().apply {
            inSampleSize = RegrasDaCapa.fatorDeAmostragem(limites.outWidth, limites.outHeight)
        }
        val bitmap = resolvedor.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opcoes) } ?: return null
        return normalizar(bitmap)
    }

    private fun normalizar(bitmap: Bitmap): Bitmap {
        val alvo = RegrasDaCapa.normalizar(bitmap.width, bitmap.height)
        if (alvo.largura == bitmap.width && alvo.altura == bitmap.height) return bitmap
        val reduzido = Bitmap.createScaledBitmap(bitmap, alvo.largura, alvo.altura, true)
        if (reduzido !== bitmap) bitmap.recycle()
        return reduzido
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
