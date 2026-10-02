package br.com.opengw.licenca

import java.math.BigInteger

/**
 * A curva da assinatura curta (Alpha 8.2): brainpoolP160r1 (RFC 5639), cofator 1. Kotlin puro.
 *
 * A assinatura Schnorr nesta curva, com desafio de 80 bits, ocupa 30 bytes — é o que deixou o
 * código com 60 caracteres (a ECDSA P-256 da 8.1 ocupava 64 bytes e o código tinha 122).
 * Segurança ~80 bits: forjar exige da ordem de 2^80 operações de curva.
 *
 * O Android não tem esta curva pronta para Schnorr, então a conta é feita aqui, afim, com
 * BigInteger. Só CONFERE (nada secreto passa por aqui). A referência é
 * `licenciador/src/nucleo/curva.ts`, conferida contra o OpenSSL; os vetores travam as duas.
 */
object CurvaDaLicenca {
    val P = BigInteger("e95e4a5f737059dc60dfc7ad95b3d8139515620f", 16)
    val A = BigInteger("340e7be2a280eb74e2be61bada745d97e8f7c300", 16)
    val B = BigInteger("1e589a8595423412134faa2dbdec95c8d8675e58", 16)
    /** Ordem do grupo (160 bits). */
    val N = BigInteger("e95e4a5f737059dc60df5991d45029409e60fc09", 16)
    val G = Ponto(
        BigInteger("bed5af16ea3f6a4f62938c4631eb5af7bdbcdbc3", 16),
        BigInteger("1667cb477a1a8ec338f94741669c976316da6321", 16)
    )
    const val BYTES_DO_NUMERO = 20

    private val DOIS = BigInteger.valueOf(2)
    private val TRES = BigInteger.valueOf(3)

    /** Ponto afim; `null` é o ponto no infinito. */
    data class Ponto(val x: BigInteger, val y: BigInteger)

    fun naCurva(p: Ponto?): Boolean {
        if (p == null) return false
        if (p.x.signum() < 0 || p.x >= P || p.y.signum() < 0 || p.y >= P) return false
        return p.y.multiply(p.y).subtract(p.x.pow(3).add(A.multiply(p.x)).add(B)).mod(P).signum() == 0
    }

    fun somar(p: Ponto?, q: Ponto?): Ponto? {
        if (p == null) return q
        if (q == null) return p
        val l = if (p.x == q.x) {
            if (p.y.add(q.y).mod(P).signum() == 0) return null
            TRES.multiply(p.x).multiply(p.x).add(A).multiply(DOIS.multiply(p.y).modInverse(P)).mod(P)
        } else {
            q.y.subtract(p.y).multiply(q.x.subtract(p.x).modInverse(P)).mod(P)
        }
        val x = l.multiply(l).subtract(p.x).subtract(q.x).mod(P)
        return Ponto(x, l.multiply(p.x.subtract(x)).subtract(p.y).mod(P))
    }

    /** k·p (dobra e soma). */
    fun multiplicar(k: BigInteger, p: Ponto?): Ponto? {
        val e = k.mod(N)
        var r: Ponto? = null
        var base = p
        for (i in 0 until e.bitLength()) {
            if (e.testBit(i)) r = somar(r, base)
            base = somar(base, base)
        }
        return r
    }

    /** [n] em [tamanho] bytes, sem sinal, do mais alto para o mais baixo. */
    fun numeroParaBytes(n: BigInteger, tamanho: Int = BYTES_DO_NUMERO): ByteArray {
        val cru = n.toByteArray()
        val semSinal = if (cru.size > 1 && cru[0] == 0.toByte()) cru.copyOfRange(1, cru.size) else cru
        require(semSinal.size <= tamanho) { "número grande demais" }
        return ByteArray(tamanho - semSinal.size) + semSinal
    }

    /** SEC1 comprimido: 02/03 (paridade de y) ‖ x = 21 bytes. */
    fun comprimir(p: Ponto): ByteArray =
        byteArrayOf(if (p.y.testBit(0)) 3 else 2) + numeroParaBytes(p.x)

    /** O contrário de [comprimir], ou null se não é um ponto da curva. P ≡ 3 (mod 4). */
    fun descomprimir(b: ByteArray): Ponto? {
        if (b.size != 1 + BYTES_DO_NUMERO || (b[0].toInt() != 2 && b[0].toInt() != 3)) return null
        val x = BigInteger(1, b.copyOfRange(1, b.size))
        if (x >= P) return null
        val v = x.pow(3).add(A.multiply(x)).add(B).mod(P)
        var y = v.modPow(P.add(BigInteger.ONE).shiftRight(2), P)
        if (y.multiply(y).subtract(v).mod(P).signum() != 0) return null
        if (y.testBit(0) != (b[0].toInt() == 3)) y = P.subtract(y)
        return Ponto(x, y).takeIf { naCurva(it) }
    }
}
