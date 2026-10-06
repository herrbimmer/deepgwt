package br.com.deepgwt.tela3d.viagens

import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/** Uma posição (graus). */
data class Fixo(val lat: Double, val lon: Double)

/** Caixa de latitude/longitude que cobre as posições. */
data class CaixaGeografica(val latMin: Double, val latMax: Double, val lonMin: Double, val lonMax: Double) {
    companion object {
        fun de(fixos: List<Fixo>): CaixaGeografica? {
            if (fixos.isEmpty()) return null
            var latMin = Double.POSITIVE_INFINITY
            var latMax = Double.NEGATIVE_INFINITY
            var lonMin = Double.POSITIVE_INFINITY
            var lonMax = Double.NEGATIVE_INFINITY
            for (f in fixos) {
                if (f.lat < latMin) latMin = f.lat
                if (f.lat > latMax) latMax = f.lat
                if (f.lon < lonMin) lonMin = f.lon
                if (f.lon > lonMax) lonMax = f.lon
            }
            return CaixaGeografica(latMin, latMax, lonMin, lonMax)
        }
    }
}

/**
 * O quadro do mapa: zoom e canto superior esquerdo da imagem em coordenadas de mundo (pixels do mapa
 * inteiro naquele zoom). A página usa o mesmo quadro para desenhar a rota por cima da imagem.
 */
data class Enquadramento(val zoom: Int, val ox: Double, val oy: Double) {
    /** O texto de `snapshot_view`: `"<z>,<ox>,<oy>"`, 3 casas, ponto decimal em qualquer idioma. */
    val texto: String get() = String.format(Locale.US, "%d,%.3f,%.3f", zoom, ox, oy)
}

/** Web Mercator padrão (XYZ, tiles de 256 px) e a conta de enquadramento da imagem do mapa. */
object ProjecaoDoMapa {
    const val LADO_DO_TILE = 256
    const val LARGURA = 640
    const val ALTURA = 400
    const val FOLGA = 36
    const val ZOOM_MINIMO = 3
    const val ZOOM_MAXIMO = 17
    const val LATITUDE_MAXIMA = 85.05112878

    private fun escala(z: Int): Double = LADO_DO_TILE * 2.0.pow(z)

    fun mundoX(lon: Double, z: Int): Double = (180.0 + lon) / 360.0 * escala(z)

    fun mundoY(lat: Double, z: Int): Double {
        val limitada = lat.coerceIn(-LATITUDE_MAXIMA, LATITUDE_MAXIMA)
        val r = Math.toRadians(limitada)
        return (1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0 * escala(z)
    }

    /** O maior zoom em [zoomMin]..[zoomMax] em que a caixa cabe em (largura - 2 folga) x (altura - 2 folga). */
    fun escolherZoom(
        caixa: CaixaGeografica,
        largura: Int = LARGURA,
        altura: Int = ALTURA,
        folga: Int = FOLGA,
        zoomMin: Int = ZOOM_MINIMO,
        zoomMax: Int = ZOOM_MAXIMO,
    ): Int {
        val larguraUtil = largura - 2 * folga
        val alturaUtil = altura - 2 * folga
        for (z in zoomMax downTo zoomMin + 1) {
            val w = mundoX(caixa.lonMax, z) - mundoX(caixa.lonMin, z)
            val h = mundoY(caixa.latMin, z) - mundoY(caixa.latMax, z)
            if (w <= larguraUtil && h <= alturaUtil) return z
        }
        return zoomMin
    }

    fun enquadrar(
        caixa: CaixaGeografica,
        largura: Int = LARGURA,
        altura: Int = ALTURA,
        folga: Int = FOLGA,
        zoomMin: Int = ZOOM_MINIMO,
        zoomMax: Int = ZOOM_MAXIMO,
    ): Enquadramento {
        val z = escolherZoom(caixa, largura, altura, folga, zoomMin, zoomMax)
        val cx = (mundoX(caixa.lonMin, z) + mundoX(caixa.lonMax, z)) / 2.0
        val cy = (mundoY(caixa.latMin, z) + mundoY(caixa.latMax, z)) / 2.0
        return Enquadramento(z, cx - largura / 2.0, cy - altura / 2.0)
    }

    fun enquadrar(fixos: List<Fixo>): Enquadramento? = CaixaGeografica.de(fixos)?.let { enquadrar(it) }
}
