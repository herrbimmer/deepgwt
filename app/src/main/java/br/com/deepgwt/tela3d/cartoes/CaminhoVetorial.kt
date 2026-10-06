package br.com.deepgwt.tela3d.cartoes

/** Um comando do caminho vetorial dos glifos (grade 24×24): `M`, `L`, `C` ou `Z`. */
sealed interface ComandoDeCaminho {
    data class MoverPara(val x: Float, val y: Float) : ComandoDeCaminho
    data class LinhaPara(val x: Float, val y: Float) : ComandoDeCaminho
    data class Curva(
        val x1: Float, val y1: Float,
        val x2: Float, val y2: Float,
        val x: Float, val y: Float,
    ) : ComandoDeCaminho
    data object Fechar : ComandoDeCaminho
}

/** Leitor tolerante do texto `M x y L x y C x1 y1 x2 y2 x y Z` (a ponte já filtrou os caracteres). */
object CaminhoVetorial {
    fun ler(texto: String): List<ComandoDeCaminho> {
        val fichas = separar(texto)
        val r = ArrayList<ComandoDeCaminho>()
        var i = 0
        var comando = ' '
        fun numeros(n: Int): FloatArray? {
            if (i + n > fichas.size) return null
            val v = FloatArray(n)
            for (k in 0 until n) v[k] = fichas[i + k].toFloatOrNull() ?: return null
            i += n
            return v
        }
        while (i < fichas.size) {
            val f = fichas[i]
            if (f.length == 1 && f[0].isLetter()) {
                comando = f[0]
                i++
                if (comando == 'Z') {
                    r += ComandoDeCaminho.Fechar
                    continue
                }
            } else if (comando == ' ' || comando == 'Z') {
                return r
            }
            // Números sem letra repetem o último comando (L depois de M, como no SVG).
            when (comando) {
                'M' -> { val v = numeros(2) ?: return r; r += ComandoDeCaminho.MoverPara(v[0], v[1]); comando = 'L' }
                'L' -> { val v = numeros(2) ?: return r; r += ComandoDeCaminho.LinhaPara(v[0], v[1]) }
                'C' -> {
                    val v = numeros(6) ?: return r
                    r += ComandoDeCaminho.Curva(v[0], v[1], v[2], v[3], v[4], v[5])
                }
                else -> return r
            }
        }
        return r
    }

    /** Separa letras e números (as letras podem vir coladas aos números). */
    internal fun separar(texto: String): List<String> {
        val r = ArrayList<String>()
        val atual = StringBuilder()
        fun fecha() {
            if (atual.isNotEmpty()) {
                r += atual.toString()
                atual.setLength(0)
            }
        }
        for (c in texto) {
            when {
                c.isLetter() -> { fecha(); r += c.toString() }
                c == ' ' || c == ',' -> fecha()
                c == '-' -> { fecha(); atual.append(c) }
                else -> atual.append(c)
            }
        }
        fecha()
        return r
    }
}
