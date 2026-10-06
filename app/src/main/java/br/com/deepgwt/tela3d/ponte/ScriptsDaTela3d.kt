package br.com.deepgwt.tela3d.ponte

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Os scripts que a ponte manda à página (Java → JS). Kotlin puro.
 *
 * O WebView da central é um Chromium antigo: só JavaScript ES5 aqui. Todo texto que vem de fora entra
 * no script como literal de string JSON-escapado ([textoJs]), nunca concatenado cru, e todo script é
 * embrulhado em `try/catch` para uma página ainda não carregada (ou sem a função) ser inofensiva.
 */
object ScriptsDaTela3d {

    /**
     * Literal de string JS/JSON: aspas, barra, controles e também U+2028/U+2029, que o JSON aceita
     * mas um WebView antigo trata como quebra de linha dentro do script.
     */
    fun textoJs(texto: String): String {
        val saida = StringBuilder(texto.length + 2)
        saida.append('"')
        for (c in texto) {
            when {
                c == '"' -> saida.append("\\\"")
                c == '\\' -> saida.append("\\\\")
                c == '\n' -> saida.append("\\n")
                c == '\r' -> saida.append("\\r")
                c == '\t' -> saida.append("\\t")
                c == '\b' -> saida.append("\\b")
                c == '\u000C' -> saida.append("\\f")
                c < ' ' || c == ' ' || c == ' ' -> {
                    saida.append("\\u")
                    saida.append(Integer.toHexString(c.code).padStart(4, '0'))
                }
                else -> saida.append(c)
            }
        }
        saida.append('"')
        return saida.toString()
    }

    /**
     * Número como literal decimal com ponto, sem notação científica e sem depender do `Locale`
     * (em pt-BR um `toString` com formato local sairia com vírgula). NaN e infinito viram `0`.
     */
    fun numeroJs(valor: Double): String {
        if (valor.isNaN() || valor.isInfinite()) return "0"
        val decimal = BigDecimal(valor).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros()
        return decimal.toPlainString()
    }

    fun numeroJs(valor: Float): String = numeroJs(valor.toDouble())

    fun booleanoJs(valor: Boolean): String = if (valor) "true" else "false"

    /** `window.onCarDataUpdate(chave, valor)`. */
    fun onCarDataUpdate(chave: String, valor: String): String =
        "try{if(window.onCarDataUpdate){window.onCarDataUpdate(${textoJs(chave)},${textoJs(valor)});}}catch(e){}"

    /** `window.onMediaNowPlaying(<objeto>)`: o texto JSON do motor entra como literal e é lido pela página. */
    fun onMediaNowPlaying(payloadJson: String): String =
        "try{if(window.onMediaNowPlaying){window.onMediaNowPlaying(JSON.parse(${textoJs(payloadJson)}));}}catch(e){}"

    fun onMediaPosition(posicaoMs: Long): String =
        "try{if(window.onMediaPosition){window.onMediaPosition($posicaoMs);}}catch(e){}"

    /** `window.__app.dockCommand(cmd)`: o comando chega intacto, qualquer que seja o texto. */
    fun comandoDoDock(comando: String): String =
        "try{if(window.__app&&typeof window.__app.dockCommand==='function'){window.__app.dockCommand(" +
            textoJs(comando) + ");}}catch(e){}"

    /** `window.__app.setDockPlate({s,l,t,w,h,v})`. */
    fun placaDoDock(estilo: String, visivel: Boolean, esquerdaCss: Float, topoCss: Float, larguraCss: Float, alturaCss: Float): String =
        "try{if(window.__app&&typeof window.__app.setDockPlate==='function'){window.__app.setDockPlate({s:" +
            textoJs(estilo) + ",v:" + booleanoJs(visivel) + ",l:" + numeroJs(esquerdaCss) + ",t:" + numeroJs(topoCss) +
            ",w:" + numeroJs(larguraCss) + ",h:" + numeroJs(alturaCss) + "});}}catch(e){}"

    fun invalidarCatalogoDeApps(): String =
        "try{if(window.__app&&typeof window.__app._invalidateAppsCatalog==='function'){" +
            "window.__app._invalidateAppsCatalog();}}catch(e){}"

    fun pularSplash(): String =
        "(function(){try{if(window.HavalSplash&&window.HavalSplash.skip)window.HavalSplash.skip();}catch(e){}})()"

    fun aliviarMemoria(nivel: Int): String =
        "try{if(window.__deepGwtAliviarMemoria)window.__deepGwtAliviarMemoria($nivel);}catch(e){}"

    fun ambienteDoRelogio(): String =
        "try{window.dispatchEvent(new Event('h6-clock-environment'));}catch(e){}"

    fun miniaturaDaArea(token: String, dataUrl: String): String =
        "try{window.__app&&window.__app.onDesktopSnapshot(${textoJs(token)},${textoJs(dataUrl)});}catch(e){}"

    /** Sondagem de diagnóstico do fim de carga: só registra, sem efeito. */
    fun diagnosticoDaPagina(): String =
        "(function(){try{return JSON.stringify({ready:document.readyState,title:document.title," +
            "body:((document.body&&document.body.innerText)||'').substring(0,160)," +
            "scripts:document.scripts.length,three:typeof THREE});}catch(e){return 'erro';}})()"
}
