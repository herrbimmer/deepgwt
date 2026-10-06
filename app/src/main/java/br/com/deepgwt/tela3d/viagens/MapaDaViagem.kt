package br.com.deepgwt.tela3d.viagens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.floor

/** O que a geração de um mapa pode devolver. Falha inesperada é exceção; falta de rede é [SemRede]. */
sealed interface ResultadoDoMapa {
    /** A imagem foi gravada em `files/trip-maps/<startMs>.jpg`. */
    data class Gerado(val nomeDoArquivo: String) : ResultadoDoMapa

    /** Algum tile não pôde ser obtido (sem internet, servidor fora): tenta de novo mais tarde. */
    data object SemRede : ResultadoDoMapa
}

/** Quem desenha e grava a imagem do mapa de uma viagem. */
fun interface GeradorDeMapa {
    fun gerar(inicioMs: Long, quadro: Enquadramento): ResultadoDoMapa
}

/** Os tiles do OpenStreetMap com cache em disco (`cache/osm-tiles/<z>/<x>/<y>.png`, válido por 30 dias). */
class TilesOsm(
    private val pastaDoCache: File,
    private val agoraMs: () -> Long = System::currentTimeMillis,
    private val baixar: (String) -> ByteArray? = { url -> HttpSimples.obterBytes(url, AgenteDeUsuario.MAPA) },
    private val registro: Registro = {},
) {
    companion object {
        const val VALIDADE_DO_CACHE_MS = 2_592_000_000L

        fun urlDoTile(z: Int, x: Int, y: Int): String = "https://tile.openstreetmap.org/$z/$x/$y.png"
    }

    /** O tile (colunas já dentro de `0 until 2^z`), ou `null` se não foi possível obtê-lo. */
    fun obter(z: Int, x: Int, y: Int): Bitmap? {
        val arquivo = File(pastaDoCache, "$z/$x/$y.png")
        if (arquivo.isFile && agoraMs() - arquivo.lastModified() < VALIDADE_DO_CACHE_MS) {
            BitmapFactory.decodeFile(arquivo.path)?.let { return it }
        }
        val bytes = baixar(urlDoTile(z, x, y))
        if (bytes == null) {
            registro("tile $z/$x/$y não baixou")
            return null
        }
        val imagem = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        if (imagem == null) {
            registro("tile $z/$x/$y veio ilegível")
            return null
        }
        try {
            arquivo.parentFile?.mkdirs()
            arquivo.writeBytes(bytes)
        } catch (e: IOException) {
            // O cache é descartável: falhar em guardar não atrapalha o mapa.
        }
        return imagem
    }
}

/** Desenha o mapa de 640 x 400 com os tiles e grava o JPEG (qualidade 82) em `files/trip-maps`. */
class GeradorDeMapaAndroid(
    private val pastaDosMapas: File,
    private val tiles: TilesOsm,
) : GeradorDeMapa {

    companion object {
        const val QUALIDADE_DO_JPEG = 82
        private const val COR_DE_FUNDO = 0xFFE8E6E1.toInt()
        private const val COR_DA_CAIXA = 0xCCFFFFFF.toInt()
        private const val COR_DO_TEXTO = 0xFF333333.toInt()
        private const val ATRIBUICAO = "© OpenStreetMap contributors"
        private const val TAMANHO_DO_TEXTO_PX = 12f
        private const val TOPO_DA_CAIXA = 382f
        private const val BASE_DO_TEXTO = 395f
    }

    override fun gerar(inicioMs: Long, quadro: Enquadramento): ResultadoDoMapa {
        val largura = ProjecaoDoMapa.LARGURA
        val altura = ProjecaoDoMapa.ALTURA
        val lado = ProjecaoDoMapa.LADO_DO_TILE
        val imagem = Bitmap.createBitmap(largura, altura, Bitmap.Config.ARGB_8888)
        try {
            val tela = Canvas(imagem)
            tela.drawColor(COR_DE_FUNDO)
            val pincelDosTiles = Paint(Paint.FILTER_BITMAP_FLAG)
            val z = quadro.zoom
            val tilesPorLado = 1 shl z
            val primeiraColuna = floor(quadro.ox / lado).toInt()
            val ultimaColuna = floor((quadro.ox + largura - 1) / lado).toInt()
            val primeiraLinha = floor(quadro.oy / lado).toInt()
            val ultimaLinha = floor((quadro.oy + altura - 1) / lado).toInt()
            for (linha in primeiraLinha..ultimaLinha) {
                if (linha < 0 || linha >= tilesPorLado) continue
                for (coluna in primeiraColuna..ultimaColuna) {
                    val tile = tiles.obter(z, Math.floorMod(coluna, tilesPorLado), linha) ?: return ResultadoDoMapa.SemRede
                    try {
                        tela.drawBitmap(
                            tile,
                            (coluna * lado - quadro.ox).toFloat(),
                            (linha * lado - quadro.oy).toFloat(),
                            pincelDosTiles,
                        )
                    } finally {
                        tile.recycle()
                    }
                }
            }
            desenharAtribuicao(tela, largura.toFloat(), altura.toFloat())
            return ResultadoDoMapa.Gerado(gravar(inicioMs, imagem))
        } finally {
            imagem.recycle()
        }
    }

    private fun desenharAtribuicao(tela: Canvas, largura: Float, altura: Float) {
        val texto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COR_DO_TEXTO
            textSize = TAMANHO_DO_TEXTO_PX
        }
        val larguraDoTexto = texto.measureText(ATRIBUICAO)
        val caixa = Paint().apply {
            color = COR_DA_CAIXA
            style = Paint.Style.FILL
        }
        tela.drawRect(largura - larguraDoTexto - 10f, TOPO_DA_CAIXA, largura, altura, caixa)
        tela.drawText(ATRIBUICAO, largura - larguraDoTexto - 5f, BASE_DO_TEXTO, texto)
    }

    private fun gravar(inicioMs: Long, imagem: Bitmap): String {
        pastaDosMapas.mkdirs()
        val nome = "$inicioMs.jpg"
        val temporario = File(pastaDosMapas, "$nome.tmp")
        val destino = File(pastaDosMapas, nome)
        try {
            val ok = FileOutputStream(temporario).use { imagem.compress(Bitmap.CompressFormat.JPEG, QUALIDADE_DO_JPEG, it) }
            if (!ok) throw IOException("não deu para comprimir o mapa")
            if (!temporario.renameTo(destino)) throw IOException("não deu para gravar $nome")
        } finally {
            if (temporario.exists()) temporario.delete()
        }
        return nome
    }
}
