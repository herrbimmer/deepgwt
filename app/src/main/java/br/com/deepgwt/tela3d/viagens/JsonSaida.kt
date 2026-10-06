package br.com.deepgwt.tela3d.viagens

/**
 * Monta JSON à mão (o `org.json` do Android não roda nos testes da JVM). Reais NaN/infinitos viram
 * `null`; texto é escapado.
 */
object JsonSaida {
    fun texto(valor: String?): String {
        if (valor == null) return "null"
        val s = StringBuilder(valor.length + 2)
        s.append('"')
        for (c in valor) {
            when {
                c == '"' -> s.append("\\\"")
                c == '\\' -> s.append("\\\\")
                c == '\n' -> s.append("\\n")
                c == '\r' -> s.append("\\r")
                c == '\t' -> s.append("\\t")
                c < ' ' || c == ' ' || c == ' ' -> s.append(String.format("\\u%04x", c.code))
                else -> s.append(c)
            }
        }
        s.append('"')
        return s.toString()
    }

    fun real(valor: Double?): String =
        if (valor == null || valor.isNaN() || valor.isInfinite()) "null" else valor.toString()

    fun inteiro(valor: Long?): String = valor?.toString() ?: "null"

    fun booleano(valor: Boolean): String = if (valor) "true" else "false"

    /** `[a,b,c]` com itens já em JSON. */
    fun lista(itens: List<String>): String = itens.joinToString(separator = ",", prefix = "[", postfix = "]")

    fun objeto(bloco: ConstrutorDeObjeto.() -> Unit): String = ConstrutorDeObjeto().apply(bloco).fechar()

    class ConstrutorDeObjeto {
        private val s = StringBuilder("{")
        private var primeiro = true

        private fun chave(nome: String) {
            if (!primeiro) s.append(',')
            primeiro = false
            s.append(JsonSaida.texto(nome)).append(':')
        }

        fun texto(nome: String, valor: String?) {
            chave(nome)
            s.append(JsonSaida.texto(valor))
        }

        fun real(nome: String, valor: Double?) {
            chave(nome)
            s.append(JsonSaida.real(valor))
        }

        fun inteiro(nome: String, valor: Long?) {
            chave(nome)
            s.append(JsonSaida.inteiro(valor))
        }

        fun inteiro(nome: String, valor: Int) = inteiro(nome, valor.toLong())

        fun booleano(nome: String, valor: Boolean) {
            chave(nome)
            s.append(JsonSaida.booleano(valor))
        }

        /** Valor já em JSON (objeto, lista...). */
        fun bruto(nome: String, json: String) {
            chave(nome)
            s.append(json)
        }

        internal fun fechar(): String = s.append('}').toString()
    }
}
