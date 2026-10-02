package br.com.opengw.licenca

import java.math.BigInteger
import java.security.MessageDigest
import java.util.Base64

/**
 * Uma licença v2 (Alpha 8.2): 285 bits = 60 caracteres no código.
 *
 *   45 bits de dado: versão (3) · chave (4) · nível (2: 1 Sport, 2 Premium, 3 Signature) ·
 *                    vitalícia (1) · série (20) · horas (13; 0 na vitalícia) · reservado (2, zero)
 *   80 bits  e — o desafio da assinatura Schnorr
 *  160 bits  s — a resposta
 *
 * A assinatura cobre "OPENGW-LIC2" ‖ x(R) ‖ chave pública ‖ dado ‖ VIN. O VIN não viaja no código:
 * a licença de um carro simplesmente não fecha a conta em outro. A v1 (8.1, 122 caracteres) nunca
 * chegou a ninguém e não é mais lida.
 */
data class Licenca(
    val chave: Int,
    val nivel: Nivel,
    val vitalicia: Boolean,
    val serie: Int,
    val horas: Int
) {
    val segundos: Long get() = horas * 3600L

    companion object {
        const val VERSAO = 2
        const val BITS_DO_DESAFIO = 80
        const val BITS_DA_RESPOSTA = 160
        const val PREFIXO = "OPENGW-LIC2"

        private val NIVEL_DO_CODIGO = mapOf(1 to Nivel.SPORT, 2 to Nivel.PREMIUM, 3 to Nivel.SIGNATURE)

        /** Os 45 bits → licença, ou a mensagem de por que não. */
        fun lerDado(dado: BigInteger): Result<Licenca> {
            fun campo(desloc: Int, bits: Int) = dado.shiftRight(desloc).toInt() and ((1 shl bits) - 1)
            if (campo(42, 3) != VERSAO || campo(0, 2) != 0) {
                return Result.failure(IllegalArgumentException("Licença de um formato mais novo. Atualize o OPEN GW."))
            }
            val nivel = NIVEL_DO_CODIGO[campo(36, 2)]
                ?: return Result.failure(IllegalArgumentException("Nível inválido no código."))
            val licenca = Licenca(
                chave = campo(38, 4),
                nivel = nivel,
                vitalicia = campo(35, 1) == 1,
                serie = campo(15, 20),
                horas = campo(2, 13)
            )
            if (licenca.serie == 0) return Result.failure(IllegalArgumentException("Série inválida no código."))
            if (licenca.vitalicia != (licenca.horas == 0)) {
                return Result.failure(IllegalArgumentException("Horas inválidas no código."))
            }
            return Result.success(licenca)
        }

        /** O código inteiro (número) → dado, e, s. */
        fun separar(n: BigInteger): Triple<BigInteger, BigInteger, BigInteger> {
            val desafio = BigInteger.ONE.shiftLeft(BITS_DO_DESAFIO).subtract(BigInteger.ONE)
            val resposta = BigInteger.ONE.shiftLeft(BITS_DA_RESPOSTA).subtract(BigInteger.ONE)
            return Triple(
                n.shiftRight(BITS_DO_DESAFIO + BITS_DA_RESPOSTA),
                n.shiftRight(BITS_DA_RESPOSTA).and(desafio),
                n.and(resposta)
            )
        }

        /** O dado como a assinatura o cobre: 45 bits alinhados à esquerda em 6 bytes. */
        fun bytesDoDado(dado: BigInteger): ByteArray = CurvaDaLicenca.numeroParaBytes(dado.shiftLeft(3), 6)

        /** Só lê o código (sem conferir a assinatura) — para as cópias guardadas. */
        fun doCodigo(codigo: String): Licenca? {
            val lido = CodigoDeLicenca.ler(codigo) as? CodigoDeLicenca.Leitura.Ok ?: return null
            return lerDado(separar(lido.numero).first).getOrNull()
        }

        private val VIN_VALIDO = Regex("^[A-HJ-NPR-Z0-9]{17}$")

        /** 17 caracteres maiúsculos sem I/O/Q, ou null. */
        fun normalizarVin(vin: String?): String? =
            vin?.replace(Regex("\\s"), "")?.uppercase()?.takeIf { VIN_VALIDO.matches(it) }

        /** "LGW•••••••••28363". */
        fun vinMascarado(vin: String): String =
            if (vin.length < 8) vin else vin.take(3) + "•".repeat(vin.length - 8) + vin.takeLast(5)

        /** Hash do VIN para as cópias que outros apps podem ler (o VIN em si fica só no app). */
        fun hashDoVin(vin: String): String =
            MessageDigest.getInstance("SHA-256").digest("OPENGW-VIN|$vin".toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

/**
 * Verifica um código (Alpha 8.2): formato, conferência das linhas, chave conhecida e assinatura
 * Schnorr contra o VIN. Kotlin puro, testado nos vetores do licenciador.
 */
object VerificadorDeLicenca {
    sealed class Resultado {
        /** [codigo] na forma canônica — é o que se guarda. */
        data class Valida(val licenca: Licenca, val codigo: String) : Resultado()
        data class Recusada(val motivo: MotivoDaRecusa, val mensagem: String, val linha: Int? = null) : Resultado()
    }

    /** Chave pública em base64 (SEC1 comprimida, 21 bytes) → ponto; erro se não for da curva. */
    fun chavePublica(base64: String): CurvaDaLicenca.Ponto =
        CurvaDaLicenca.descomprimir(Base64.getDecoder().decode(base64.trim()))
            ?: throw IllegalArgumentException("chave pública inválida")

    /**
     * [chaves] = id → pública. A chave de TESTE ([ChavesPublicas.ID_DE_TESTE]) só vale com
     * [aceitarChaveDeTeste] (build de teste); fora dele a licença é recusada com motivo próprio.
     */
    fun verificar(codigo: String, vin: String?, chaves: Map<Int, CurvaDaLicenca.Ponto>, aceitarChaveDeTeste: Boolean): Resultado {
        val leitura = CodigoDeLicenca.ler(codigo)
        if (leitura is CodigoDeLicenca.Leitura.Erro) return Resultado.Recusada(leitura.motivo, leitura.mensagem, leitura.linha)
        val numero = (leitura as CodigoDeLicenca.Leitura.Ok).numero
        val (dado, e, s) = Licenca.separar(numero)
        val licenca = Licenca.lerDado(dado).getOrElse {
            return Resultado.Recusada(MotivoDaRecusa.FORMATO, it.message ?: "Código inválido.")
        }
        if (licenca.chave == ChavesPublicas.ID_DE_TESTE && !aceitarChaveDeTeste) {
            return Resultado.Recusada(MotivoDaRecusa.CHAVE_DE_TESTE, "Licença de teste: só vale num build de teste do OPEN GW.")
        }
        val publica = chaves[licenca.chave]
            ?: return Resultado.Recusada(MotivoDaRecusa.CHAVE, "Esta licença foi assinada por uma chave que este OPEN GW não conhece. Atualize o OPEN GW.")
        val v = Licenca.normalizarVin(vin)
            ?: return Resultado.Recusada(MotivoDaRecusa.VIN, "Não deu para ler o VIN do carro agora. Com o carro ligado, tente de novo.")
        if (!assinaturaConfere(dado, e, s, v, publica)) {
            return Resultado.Recusada(
                MotivoDaRecusa.ASSINATURA,
                "Esta licença não é deste carro (VIN ${Licenca.vinMascarado(v)}) ou foi alterada."
            )
        }
        return Resultado.Valida(licenca, CodigoDeLicenca.formatar(numero))
    }

    /** R' = s·G + e·P; vale se o desafio recalculado com x(R') é o mesmo [e]. */
    fun assinaturaConfere(dado: BigInteger, e: BigInteger, s: BigInteger, vin: String, publica: CurvaDaLicenca.Ponto): Boolean {
        if (s >= CurvaDaLicenca.N || !CurvaDaLicenca.naCurva(publica)) return false
        val r = CurvaDaLicenca.somar(
            CurvaDaLicenca.multiplicar(s, CurvaDaLicenca.G),
            CurvaDaLicenca.multiplicar(e, publica)
        ) ?: return false
        return desafio(r, publica, Licenca.bytesDoDado(dado), vin) == e
    }

    private fun desafio(r: CurvaDaLicenca.Ponto, publica: CurvaDaLicenca.Ponto, dado: ByteArray, vin: String): BigInteger {
        val h = MessageDigest.getInstance("SHA-256").run {
            update(Licenca.PREFIXO.toByteArray(Charsets.US_ASCII))
            update(CurvaDaLicenca.numeroParaBytes(r.x))
            update(CurvaDaLicenca.comprimir(publica))
            update(dado)
            update(vin.toByteArray(Charsets.US_ASCII))
            digest()
        }
        return BigInteger(1, h.copyOfRange(0, Licenca.BITS_DO_DESAFIO / 8))
    }
}
