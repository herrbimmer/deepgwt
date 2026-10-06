// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.licenca

import java.math.BigInteger

/**
 * O código de licença como texto (Alpha 8.2): Base32 de Crockford em 3 linhas de 19 símbolos de
 * dado + 1 de conferência — quatro grupos de cinco por linha, 60 caracteres:
 *
 *   8400Z-A0CGT-NEWSD-GQHE5
 *   BX9XF-MS4HP-KZ7F9-DDFW9
 *   BT4K9-96G0J-HPH2Z-VAY7W
 *
 * A referência é `licenciador/src/nucleo/codigo.ts` (fora do repositório, com o gerador); o
 * instalador tem a mesma conta em `instalador/src/tiers/codigoDeLicenca.ts`. Os vetores de
 * `instalador/src/mock/licencaVetores.json` travam as três (ver `LicencaTest`).
 *
 * A conferência existe para a tela dizer ONDE está o erro ("a linha 2 tem um caractere errado").
 * Soma com pesos ímpares mais 7 × número da linha, módulo 32 — peso ímpar é inversível módulo 32,
 * então trocar um símbolo sozinho sempre muda a conta.
 *
 * A leitura ACHA o código dentro de um texto maior: dá para colar a mensagem inteira do WhatsApp.
 */
object CodigoDeLicenca {
    const val ALFABETO = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    const val LINHAS = 3
    const val DADOS_POR_LINHA = 19
    const val SIMBOLOS_POR_LINHA = DADOS_POR_LINHA + 1
    const val SIMBOLOS_DE_DADO = LINHAS * DADOS_POR_LINHA
    const val TAMANHO = LINHAS * SIMBOLOS_POR_LINHA

    /** 0–31, ou -1 fora do alfabeto. O vale 0; I e L valem 1; minúscula vale maiúscula. */
    fun valorDoSimbolo(c: Char): Int {
        val n = when (val u = c.uppercaseChar()) {
            'O' -> '0'
            'I', 'L' -> '1'
            else -> u
        }
        return ALFABETO.indexOf(n)
    }

    fun conferencia(valores: List<Int>, linha: Int): Int {
        var s = 7 * (linha + 1)
        valores.forEachIndexed { i, v -> s += (2 * i + 1) * v }
        return s % 32
    }

    /** Número de 285 bits → 57 valores de 5 bits (do mais alto para o mais baixo). */
    fun numeroParaValores(n: BigInteger): List<Int> =
        (SIMBOLOS_DE_DADO - 1 downTo 0).map { n.shiftRight(5 * it).toInt() and 31 }

    fun valoresParaNumero(valores: List<Int>): BigInteger =
        valores.fold(BigInteger.ZERO) { acc, v -> acc.shiftLeft(5).or(BigInteger.valueOf(v.toLong())) }

    /** Número → texto canônico: linhas separadas por `\n`, grupos de cinco por `-`. */
    fun formatar(n: BigInteger): String =
        numeroParaValores(n).chunked(DADOS_POR_LINHA).mapIndexed { l, parte ->
            (parte + conferencia(parte, l)).joinToString("") { ALFABETO[it].toString() }.chunked(5).joinToString("-")
        }.joinToString("\n")

    sealed class Leitura {
        class Ok(val numero: BigInteger) : Leitura()
        data class Erro(val motivo: MotivoDaRecusa, val mensagem: String, val linha: Int? = null) : Leitura()
    }

    // O `\s` do Java não pega o espaço fino/não separável que o WhatsApp às vezes põe; vai à mão.
    private const val SEP = "\\s\\u00A0\\u2007\\u202F\\-_.·–—"
    private val SEPARADORES = Regex("[$SEP]")
    private val FORA_DO_CODIGO = Regex("[^0-9A-Za-z$SEP]+")
    private val OUTRAS_COISAS = Regex("[^0-9A-Za-z$SEP]")

    private fun primeiraLinhaErrada(valores: List<Int>): Int {
        valores.chunked(SIMBOLOS_POR_LINHA).forEachIndexed { l, linha ->
            if (linha.size < 2) return l + 1
            if (conferencia(linha.dropLast(1), l) != linha.last()) return l + 1
        }
        return 0
    }

    private fun lerEstrito(texto: String): Leitura {
        val simbolos = texto.replace(SEPARADORES, "")
        if (simbolos.isEmpty()) return Leitura.Erro(MotivoDaRecusa.VAZIO, "Cole ou digite o código da licença.")
        val valores = ArrayList<Int>(simbolos.length)
        for (c in simbolos) {
            val v = valorDoSimbolo(c)
            if (v < 0) return Leitura.Erro(MotivoDaRecusa.CARACTERE, "\"$c\" não faz parte de um código de licença.")
            valores += v
        }
        if (valores.size != TAMANHO) {
            val diferenca = valores.size - TAMANHO
            // Uma linha "4" (o que sobrou depois da 3ª) não existe na tela: aí não aponta linha.
            val linha = primeiraLinhaErrada(valores).takeIf { it in 1..LINHAS }
            val quanto = when {
                diferenca == -1 -> "Falta 1 caractere"
                diferenca < 0 -> "Faltam ${-diferenca} caracteres"
                diferenca == 1 -> "Sobra 1 caractere"
                else -> "Sobram $diferenca caracteres"
            }
            return Leitura.Erro(MotivoDaRecusa.TAMANHO, if (linha != null) "$quanto — confira a linha $linha." else "$quanto no código.", linha)
        }
        val linha = primeiraLinhaErrada(valores)
        if (linha > 0) return Leitura.Erro(MotivoDaRecusa.CONFERENCIA, "A linha $linha tem um caractere errado.", linha)
        return Leitura.Ok(valoresParaNumero(valores.chunked(SIMBOLOS_POR_LINHA).flatMap { it.dropLast(1) }))
    }

    /** Palavras seguidas (só separadores entre elas), todas do alfabeto, somando 60 símbolos. */
    private fun candidatos(texto: String): List<String> {
        val achados = ArrayList<String>()
        for (pedaco in texto.split(FORA_DO_CODIGO)) {
            val palavras = pedaco.split(SEPARADORES).filter { it.isNotEmpty() }
            for (i in palavras.indices) {
                var total = 0
                for (j in i until palavras.size) {
                    if (palavras[j].any { valorDoSimbolo(it) < 0 }) break
                    total += palavras[j].length
                    if (total == TAMANHO) achados += palavras.subList(i, j + 1).joinToString("")
                    if (total >= TAMANHO) break
                }
            }
        }
        return achados
    }

    /**
     * Texto → número de 285 bits. Aceita espaços, quebras de linha, hífens e pontos em qualquer
     * lugar, minúsculas, O no lugar de 0, I/L no lugar de 1 — e o código no meio de uma mensagem.
     */
    fun ler(texto: String): Leitura {
        val direto = lerEstrito(texto)
        if (direto is Leitura.Ok || (direto as Leitura.Erro).motivo == MotivoDaRecusa.VAZIO) return direto
        val achados = candidatos(texto)
        achados.forEach { c -> (lerEstrito(c) as? Leitura.Ok)?.let { return it } }
        if (achados.isNotEmpty() && (direto.motivo == MotivoDaRecusa.CARACTERE || direto.motivo == MotivoDaRecusa.TAMANHO)) {
            return lerEstrito(achados.first())
        }
        if (OUTRAS_COISAS.containsMatchIn(texto)) {
            return Leitura.Erro(MotivoDaRecusa.CARACTERE, "Não achei o código no texto colado. Ele tem 60 caracteres, em 3 linhas.")
        }
        return direto
    }

    /** Quantos símbolos do código o texto já tem (para o contador enquanto se digita). */
    fun simbolosDigitados(texto: String): Int = texto.count { valorDoSimbolo(it) >= 0 }
}

/**
 * Por que um código foi recusado. Os ids são os mesmos do licenciador e do instalador; [ALTERADO]
 * (8.3) só existe no carro.
 */
enum class MotivoDaRecusa(val id: String) {
    VAZIO("vazio"),
    CARACTERE("caractere"),
    TAMANHO("tamanho"),
    CONFERENCIA("conferencia"),
    FORMATO("formato"),
    CHAVE("chave"),
    CHAVE_DE_TESTE("chave-de-teste"),
    VIN("vin"),
    ASSINATURA("assinatura"),
    REPETIDA("repetida"),
    ALTERADO("alterado"),
    /** Teste grátis recusado: já usado neste carro, já tem o Max ou nada a testar (8.4). */
    AVALIACAO("avaliacao")
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
