package br.com.opengw.monitores

import android.graphics.Bitmap

/**
 * Cores do "fundo do álbum" (`extras-monitores.md`, parte A).
 *
 * A conta fica em [ContaDasCores] (Kotlin puro, testada na JVM); o que é Android aqui é só o acesso
 * aos pixels da capa e o cache.
 */
object ExtratorDeCores {

    private const val CAPACIDADE_DO_CACHE = 24

    private val cache = CacheLru<CoresDoFundo>(CAPACIDADE_DO_CACHE)

    /**
     * Cores da capa. Qualquer thread (CPU; nunca na principal se a capa for grande). Usa o cache.
     * [chave] é só uma dica para o cache (faixa/pacote); pode ser nula ou em branco.
     */
    @JvmStatic
    fun extrair(capa: Bitmap?, chave: String?): CoresDoFundo {
        if (capa == null) return CoresDoFundo.RESERVA
        val largura: Int
        val altura: Int
        val geracao: Int
        try {
            // Reciclada: nem a geração dela se pode ler com segurança.
            if (capa.isRecycled) return CoresDoFundo.RESERVA
            largura = capa.width
            altura = capa.height
            if (largura <= 0 || altura <= 0) return CoresDoFundo.RESERVA
            geracao = capa.generationId
        } catch (e: Exception) {
            return CoresDoFundo.RESERVA
        }
        return extrairComCache(largura, altura, geracao, chave) { abrirPixels(capa) }
    }

    /**
     * O mesmo cálculo sobre pixels ARGB já lidos ([pixels] tem [largura]*[altura] ints, linha a
     * linha), sem cache e sem Android: é o que os testes JVM chamam.
     */
    @JvmStatic
    fun extrairDePixels(pixels: IntArray, largura: Int, altura: Int): CoresDoFundo {
        if (largura <= 0 || altura <= 0) return CoresDoFundo.RESERVA
        if (pixels.size.toLong() < largura.toLong() * altura.toLong()) return CoresDoFundo.RESERVA
        return ContaDasCores.calcular(largura, altura) { x, y -> pixels[y * largura + x] }
    }

    /** Esvazia o cache (testes). */
    @JvmStatic
    fun limparCache() {
        cache.limpar()
    }

    /** Acesso aos pixels de uma capa, com a liberação do que for preciso (cópia de software). */
    internal class AcessoAPixels(val ler: (Int, Int) -> Int, val liberar: () -> Unit = {})

    /**
     * Cache + cálculo, sem Bitmap (testes). A chave do cache é a dica, a largura, a altura e a
     * geração, unidas por `|`. Falha ao abrir ou ler os pixels vira [CoresDoFundo.RESERVA] e entra no
     * cache (defeito DA3, mantido).
     */
    internal fun extrairComCache(
        largura: Int,
        altura: Int,
        geracao: Int,
        chave: String?,
        abrir: () -> AcessoAPixels,
    ): CoresDoFundo {
        val dica = if (chave == null || chave.isBlank()) "" else chave
        val chaveDoCache = "$dica|$largura|$altura|$geracao"
        cache.obter(chaveDoCache)?.let { return it }

        val resultado = try {
            val acesso = abrir()
            try {
                ContaDasCores.calcular(largura, altura, acesso.ler)
            } finally {
                try {
                    acesso.liberar()
                } catch (_: Exception) {
                }
            }
        } catch (e: Exception) {
            CoresDoFundo.RESERVA
        }
        cache.guardar(chaveDoCache, resultado)
        return resultado
    }

    private fun abrirPixels(capa: Bitmap): AcessoAPixels {
        // Bitmap de hardware não deixa ler pixel: copia para um de software (e solta a cópia depois).
        if (capa.config == Bitmap.Config.HARDWARE) {
            val copia = capa.copy(Bitmap.Config.ARGB_8888, false)
                ?: throw IllegalStateException("sem cópia de software")
            return AcessoAPixels(ler = { x, y -> copia.getPixel(x, y) }, liberar = { copia.recycle() })
        }
        return AcessoAPixels(ler = { x, y -> capa.getPixel(x, y) })
    }
}

/** Cache LRU seguro entre threads: leitura conta como uso. */
internal class CacheLru<V : Any>(private val capacidade: Int) {

    private val mapa = object : LinkedHashMap<String, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>?): Boolean = size > capacidade
    }

    fun obter(chave: String): V? = synchronized(mapa) { mapa[chave] }

    fun guardar(chave: String, valor: V) {
        synchronized(mapa) { mapa[chave] = valor }
    }

    fun limpar() {
        synchronized(mapa) { mapa.clear() }
    }

    fun tamanho(): Int = synchronized(mapa) { mapa.size }

    fun contem(chave: String): Boolean = synchronized(mapa) { mapa.containsKey(chave) }
}

/**
 * A conta das quatro cores (A.3), toda em `Float` de 32 bits. Pontuações HSL por tipo de candidato;
 * as constantes são as que dão às capas as cores que o dono vê hoje.
 */
internal object ContaDasCores {

    private const val ALFA_MINIMO = 180
    private const val LADO_DE_AMOSTRA = 96f

    private class Hsl(val h: Float, val s: Float, val l: Float)

    /** O melhor candidato de um tipo: maior pontuação; empate fica com o primeiro. */
    private class Melhor {
        var cor = 0
        var pontos = 0f
        var tem = false

        fun oferecer(corNova: Int, pontosNovos: Float) {
            if (!tem || pontosNovos > pontos) {
                cor = corNova
                pontos = pontosNovos
                tem = true
            }
        }
    }

    fun calcular(largura: Int, altura: Int, lerPixel: (Int, Int) -> Int): CoresDoFundo {
        if (largura <= 0 || altura <= 0) return CoresDoFundo.RESERVA

        val maiorLado = maxOf(largura, altura)
        val passo = maxOf(1, Math.round(maiorLado / LADO_DE_AMOSTRA))

        val vibrante = Melhor()
        val vibranteEscuro = Melhor()
        val suave = Melhor()
        val suaveEscuro = Melhor()
        var somaR = 0
        var somaG = 0
        var somaB = 0
        var contagem = 0

        var y = 0
        while (y < altura) {
            var x = 0
            while (x < largura) {
                val argb = lerPixel(x, y)
                x += passo
                if ((argb ushr 24) < ALFA_MINIMO) continue
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF
                val cor = 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
                val hsl = paraHsl(r, g, b)
                val s = hsl.s
                val l = hsl.l

                if (l >= 0.06f && l <= 0.94f) {
                    somaR += r
                    somaG += g
                    somaB += b
                    contagem++
                }
                val base = s * (1f - Math.abs(l - 0.52f))
                if (s >= 0.44f && l >= 0.24f && l <= 0.78f) vibrante.oferecer(cor, base)
                if (s >= 0.42f && l >= 0.08f && l <= 0.48f) vibranteEscuro.oferecer(cor, base + 0.16f)
                if (s >= 0.12f && s <= 0.5f && l >= 0.26f && l <= 0.74f) {
                    suave.oferecer(cor, (1f - s) * (1f - Math.abs(l - 0.5f)))
                }
                if (s >= 0.08f && s <= 0.48f && l >= 0.08f && l <= 0.46f) {
                    suaveEscuro.oferecer(cor, (1f - s) * maxOf(0f, 0.62f - l))
                }
            }
            y += passo
        }
        if (contagem == 0) return CoresDoFundo.RESERVA

        val media = 0xFF000000.toInt() or
            ((somaR / contagem).coerceIn(0, 255) shl 16) or
            ((somaG / contagem).coerceIn(0, 255) shl 8) or
            (somaB / contagem).coerceIn(0, 255)

        fun cor(m: Melhor?): Int? = if (m != null && m.tem) m.cor else null

        val principal = legivel(cor(vibrante) ?: media)
        val secundaria = legivel(cor(vibranteEscuro) ?: cor(suave) ?: media)
        val destaque = legivel(cor(suave) ?: cor(vibrante) ?: media)
        val escura = escurecer(cor(suaveEscuro) ?: cor(vibranteEscuro) ?: media)
        return CoresDoFundo(principal, secundaria, destaque, escura)
    }

    /** `s` em [0,28; 0,82] e `l` em [0,26; 0,66]. */
    fun legivel(cor: Int): Int {
        val hsl = paraHsl((cor shr 16) and 0xFF, (cor shr 8) and 0xFF, cor and 0xFF)
        return paraRgb(hsl.h, hsl.s.coerceIn(0.28f, 0.82f), hsl.l.coerceIn(0.26f, 0.66f))
    }

    /** `s` no mínimo 0,16 e `l` multiplicada por 0,34, entre 0,04 e 0,2. */
    fun escurecer(cor: Int): Int {
        val hsl = paraHsl((cor shr 16) and 0xFF, (cor shr 8) and 0xFF, cor and 0xFF)
        return paraRgb(hsl.h, maxOf(hsl.s, 0.16f), (hsl.l * 0.34f).coerceIn(0.04f, 0.2f))
    }

    private fun paraHsl(r: Int, g: Int, b: Int): Hsl {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val maximo = maxOf(rf, gf, bf)
        val minimo = minOf(rf, gf, bf)
        val l = (maximo + minimo) / 2f
        if (maximo == minimo) return Hsl(0f, 0f, l)
        val d = maximo - minimo
        val s = (d / (1f - Math.abs(2f * l - 1f))).coerceIn(0f, 1f)
        val sextante = when (maximo) {
            rf -> (gf - bf) / d + (if (gf < bf) 6f else 0f)
            gf -> (bf - rf) / d + 2f
            else -> (rf - gf) / d + 4f
        }
        return Hsl(sextante / 6f, s, l)
    }

    private fun paraRgb(h: Float, s: Float, l: Float): Int {
        val r: Int
        val g: Int
        val b: Int
        if (s == 0f) {
            val v = canal(l)
            r = v
            g = v
            b = v
        } else {
            val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
            val p = 2f * l - q
            r = canal(terco(p, q, h + 1f / 3f))
            g = canal(terco(p, q, h))
            b = canal(terco(p, q, h - 1f / 3f))
        }
        return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
    }

    private fun terco(p: Float, q: Float, tBruto: Float): Float {
        var t = tBruto
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 0.5f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }

    private fun canal(valor: Float): Int = Math.round(valor * 255f).coerceIn(0, 255)
}
