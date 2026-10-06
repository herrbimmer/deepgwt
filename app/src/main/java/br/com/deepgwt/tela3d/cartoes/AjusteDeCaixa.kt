package br.com.deepgwt.tela3d.cartoes

import java.util.Locale

/**
 * Ajuste de caixa dos textos que a página manda em MAIÚSCULAS (detalhes dos cartões): cada linha é tratada
 * à parte; a que não tem nenhuma minúscula e tem pelo menos 4 letras vira "frase" (siglas conhecidas e
 * palavras sem letras ficam como estão; a primeira palavra que não é sigla ganha inicial maiúscula).
 */
object AjusteDeCaixa {
    private const val MINIMO_DE_LETRAS = 4

    val SIGLAS: Set<String> = setOf(
        "SOC", "EV", "HEV", "PHEV", "AWD", "ADAS", "GPS", "ECO", "GT", "TPMS", "DC", "AC", "ETA",
        "RL", "RR", "FL", "FR", "PSI", "BAR",
    )

    fun ajustar(texto: String?, idioma: Locale = Locale.getDefault()): String {
        if (texto.isNullOrEmpty()) return ""
        if ('\n' !in texto) return linha(texto, idioma)
        return texto.split('\n').joinToString("\n") { linha(it, idioma) }
    }

    private fun linha(linha: String, idioma: Locale): String {
        if (linha.any { Character.isLowerCase(it) }) return linha
        if (linha.count { Character.isLetter(it) } < MINIMO_DE_LETRAS) return linha
        var jaTeveNaoSigla = false
        return linha.split(' ').joinToString(" ") { palavra ->
            val letras = palavra.filter { it in 'A'..'Z' || it in 'a'..'z' || it in 'À'..'ÿ' }
            if (letras.isEmpty() || letras.uppercase(Locale.ROOT) in SIGLAS) {
                palavra
            } else if (!jaTeveNaoSigla) {
                jaTeveNaoSigla = true
                inicialMaiuscula(palavra, idioma)
            } else {
                palavra.lowercase(idioma)
            }
        }
    }

    private fun inicialMaiuscula(palavra: String, idioma: Locale): String {
        val minuscula = palavra.lowercase(idioma)
        if (minuscula.isEmpty()) return minuscula
        return minuscula.substring(0, 1).uppercase(idioma) + minuscula.substring(1)
    }
}

/** Corte de texto com reticências à largura disponível, caractere a caractere. */
object CorteDeTexto {
    const val RETICENCIAS = "…"

    /**
     * Devolve [texto] se couber em [largura]; senão o maior prefixo que, seguido de `…`, cabe. Se nem a
     * reticência cabe devolve vazio. [medir] mede a largura de um texto (na fonte e tamanho do desenho).
     */
    fun cortar(texto: String, largura: Float, medir: (String) -> Float): String {
        if (texto.isEmpty()) return texto
        if (medir(texto) <= largura) return texto
        var fim = texto.length - 1
        while (fim > 0) {
            val candidato = texto.substring(0, fim).trimEnd() + RETICENCIAS
            if (medir(candidato) <= largura) return candidato
            fim--
        }
        return if (medir(RETICENCIAS) <= largura) RETICENCIAS else ""
    }

    /**
     * "Ajustar" do relógio: o maior tamanho (a partir de [maximo], decrescendo de 1 em 1, nunca menos que
     * [minimo]) em que a largura medida não passa de [largura].
     */
    fun tamanhoQueCabe(maximo: Float, largura: Float, minimo: Float = 10f, medir: (Float) -> Float): Float {
        var tamanho = maximo
        while (tamanho > minimo && medir(tamanho) > largura) tamanho -= 1f
        return if (tamanho < minimo) minimo else tamanho
    }
}
