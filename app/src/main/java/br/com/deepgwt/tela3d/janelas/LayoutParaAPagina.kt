package br.com.deepgwt.tela3d.janelas

/**
 * O texto do objeto que a página recebe a cada mudança das janelas (contrato externo §3) e o JSON de
 * `getShellLayout`. Sem Android: as chaves e os valores são exatamente os que a página lê.
 */
internal object LayoutParaAPagina {

    /** Acréscimo fixo de `safeTop`, em px CSS (J13). */
    const val ACRESCIMO_DO_TOPO_SEGURO = 22f

    /**
     * Tudo que o objeto leva além do [EstadoDasJanelas]: os retângulos dos dois lados e as margens,
     * em pixels de tela, mais a origem da página e a densidade para a conversão em px CSS.
     */
    class Medidas(
        val esquerda: Retangulo,
        val direita: Retangulo,
        val origem: Retangulo,
        val margens: MargensDaPagina,
        val densidade: Float,
    )

    /** O lado de "próximo a abrir": `left`/`right` (padrão `left`). */
    fun proximoLado(estado: EstadoDasJanelas): String =
        if (estado.ladoPreferido == Lado.ESQUERDA.texto || estado.ladoPreferido == Lado.DIREITA.texto) {
            estado.ladoPreferido
        } else if (estado.proximoEhEsquerda) {
            Lado.ESQUERDA.texto
        } else {
            Lado.DIREITA.texto
        }

    /** O lado da última abertura; enquanto não houve, o mesmo valor de [proximoLado]. */
    fun ultimoLado(estado: EstadoDasJanelas): String =
        estado.ultimoLado.ifEmpty { proximoLado(estado) }

    fun montar(estado: EstadoDasJanelas, m: Medidas): String {
        val d = if (m.densidade <= 0f) 1f else m.densidade
        val sb = StringBuilder(640)
        sb.append('{')
        campoBooleano(sb, "left", estado.pacoteEsquerda.isNotEmpty()).append(',')
        campoTexto(sb, "right", if (estado.pacoteDireita.isNotEmpty()) "app" else "idle").append(',')
        campoTexto(sb, "mode", estado.modo.texto).append(',')
        campoTexto(sb, "launchSide", estado.ladoPreferido).append(',')
        campoTexto(sb, "nextSide", proximoLado(estado)).append(',')
        campoTexto(sb, "lastSide", ultimoLado(estado)).append(',')
        campoTexto(sb, "leftUse", estado.usoEsquerda.texto).append(',')
        campoTexto(sb, "rightUse", estado.usoDireita.texto).append(',')
        limites(sb, "leftBounds", m.esquerda, m.origem, d).append(',')
        limites(sb, "rightBounds", m.direita, m.origem, d).append(',')
        campoNumero(sb, "safeLeft", m.margens.lateralEsquerda / d).append(',')
        campoNumero(sb, "safeRight", m.margens.lateralDireita / d).append(',')
        campoNumero(sb, "safeTop", m.margens.superior / d + ACRESCIMO_DO_TOPO_SEGURO).append(',')
        campoNumero(sb, "safeBottom", m.margens.reservaDoDockCompensada / d).append(',')
        campoNumero(sb, "launcherBottom", m.margens.folgaDoChromeCompensada / d).append(',')
        campoNumero(sb, "popupTop", m.margens.topoDosPopups / d).append(',')
        campoNumero(sb, "popupBottom", m.margens.fundoDosPopups / d).append(',')
        campoTexto(sb, "uiMode", estado.modoDaInterface.texto).append(',')
        campoTexto(sb, "splitRatio", estado.proporcao.texto)
        sb.append('}')
        return sb.toString()
    }

    /** O JSON de `getShellLayout`: sempre os três campos, com os valores atuais. */
    fun textoDeGetShellLayout(estado: EstadoDasJanelas): String =
        "{\"mode\":${aspas(estado.modo.texto)},\"splitRatio\":${aspas(estado.proporcao.texto)}," +
            "\"launchSide\":${aspas(estado.ladoPreferido)}}"

    /** Um número JS válido: nunca `NaN` nem infinito (a página testa com `Number.isFinite`). */
    fun numero(valor: Float): String = if (valor.isNaN() || valor.isInfinite()) "0.0" else valor.toString()

    private fun limites(sb: StringBuilder, nome: String, r: Retangulo, origem: Retangulo, d: Float): StringBuilder {
        sb.append('"').append(nome).append("\":{")
        campoNumero(sb, "l", (r.esquerda - origem.esquerda) / d).append(',')
        campoNumero(sb, "t", (r.topo - origem.topo) / d).append(',')
        campoNumero(sb, "r", (r.direita - origem.esquerda) / d).append(',')
        campoNumero(sb, "b", (r.fundo - origem.topo) / d)
        return sb.append('}')
    }

    private fun campoNumero(sb: StringBuilder, nome: String, valor: Float): StringBuilder =
        sb.append('"').append(nome).append("\":").append(numero(valor))

    private fun campoBooleano(sb: StringBuilder, nome: String, valor: Boolean): StringBuilder =
        sb.append('"').append(nome).append("\":").append(valor)

    private fun campoTexto(sb: StringBuilder, nome: String, valor: String): StringBuilder =
        sb.append('"').append(nome).append("\":").append(aspas(valor))

    private fun aspas(texto: String): String {
        val sb = StringBuilder(texto.length + 2).append('"')
        for (c in texto) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        return sb.append('"').toString()
    }
}
