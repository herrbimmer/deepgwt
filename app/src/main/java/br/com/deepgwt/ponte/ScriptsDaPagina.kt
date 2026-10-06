// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ponte

import br.com.deepgwt.ponte.ContratoV1.textoJs

/**
 * Os scripts que a ponte manda à página do tema (seção 5). Kotlin puro.
 *
 * Tudo aqui roda no WebView do carro, que é um Chromium antigo (o catálogo XTRA trata como 84):
 * só ES5 — sem `?.`, `??`, `replaceAll`, `let`/`const`, arrow function. E nada escreve no console
 * por evento (cada linha de console vai para o registro em disco do app): erros das funções do tema
 * são engolidos em silêncio. A única linha de console é a da reescrita de fonte, uma vez por
 * documento.
 */
object ScriptsDaPagina {

    /** Resposta do `onCardChanged` quando a página não tem a função. */
    const val CARTAO_FALTANDO = "faltando"

    /** Resposta do `onCardChanged` quando a função rodou sem erro. */
    const val CARTAO_CONTRATO = "contrato"

    /** Marca no `<html>` de que a reescrita de `font-size` já foi feita neste documento. */
    const val MARCA_DA_FONTE = "data-gw-font-scale-patched"

    /** `window.onDataChanged(chave, valor)`, só se existir. Chave e valor sempre string JS. */
    fun onDataChanged(chave: String, valor: String): String =
        "try{if(typeof window.onDataChanged==='function'){window.onDataChanged(" +
            textoJs(chave) + "," + textoJs(valor) + ");}}catch(e){}"

    /**
     * `window.onCardChanged(n)` protegido: devolve [CARTAO_FALTANDO] se a função não existe, o erro
     * (`"erro: …"`) se ela lançou, e [CARTAO_CONTRATO] se rodou.
     */
    fun onCardChanged(cartao: Int): String =
        "(function(){try{if(typeof window.onCardChanged!=='function'){return '" + CARTAO_FALTANDO +
            "';}window.onCardChanged(" + cartao + ");return '" + CARTAO_CONTRATO +
            "';}catch(e){return 'erro: '+((e&&e.message)?e.message:String(e));}})()"

    /** `window.onKeyEvent(nome)`, só se existir. */
    fun onKeyEvent(nome: String): String =
        "try{if(typeof window.onKeyEvent==='function'){window.onKeyEvent(" + textoJs(nome) +
            ");}}catch(e){}"

    /**
     * Um `control(chave, valor)` avulso. O tipo do valor vem do Kotlin: texto sai como string JS
     * (mesmo `"57"`), booleano e número saem crus, null sai `null`.
     */
    fun control(chave: String, valor: Any?): String = chamadaControl(chave, literalTipado(valor))

    /**
     * Vários `control` num só script (o lote da sincronização), na ordem dada. Aqui vale a regra do
     * lote: texto `true`/`false`/`null` ou numérico sai cru ([ContratoV1.literalJs]).
     */
    fun lote(valores: Iterable<Pair<String, Any?>>): String {
        val saida = StringBuilder()
        for ((chave, valor) in valores) saida.append(chamadaControl(chave, ContratoV1.literalJs(valor)))
        return saida.toString()
    }

    private fun chamadaControl(chave: String, literal: String): String =
        "try{control(" + textoJs(chave) + "," + literal + ");}catch(e){}"

    /** Literal pelo tipo do Kotlin (o formato do `control` avulso). */
    fun literalTipado(valor: Any?): String =
        if (valor is String) textoJs(valor) else ContratoV1.literalJs(valor)

    /** Escreve uma variável CSS no `document.documentElement`. */
    fun variavelCss(nome: String, valor: String): String =
        "try{document.documentElement.style.setProperty(" + textoJs(nome) + "," + textoJs(valor) +
            ");}catch(e){}"

    /**
     * `--gauge-num-scale` e, na primeira vez em cada documento, a reescrita de `font-size` de toda
     * regra das folhas da página para `calc(<original> * var(--gauge-num-scale, 1))`, mantendo a
     * prioridade (`!important`). Pula regra que já cita a variável, valor sem número (palavras como
     * `inherit`/`larger`, que dentro de `calc` deixariam de valer) e número sem unidade; pula folha
     * de outra origem (ilegível). Entra em `@media`/`@supports` e em folhas importadas. Com escala 1
     * nada muda na tela.
     */
    fun escalaDosNumeros(escala: String): String =
        "(function(){var raiz=document.documentElement;if(!raiz){return;}" +
            "try{raiz.style.setProperty('--gauge-num-scale'," + textoJs(escala) + ");}catch(e){}" +
            "if(raiz.getAttribute('" + MARCA_DA_FONTE + "')==='1'){return;}" +
            "raiz.setAttribute('" + MARCA_DA_FONTE + "','1');" +
            "var mudadas=0;" +
            "function regras(lista){if(!lista){return;}for(var i=0;i<lista.length;i++){var r=lista[i];" +
            "if(!r){continue;}" +
            "if(r.styleSheet){var importadas=null;try{importadas=r.styleSheet.cssRules;}catch(e){importadas=null;}regras(importadas);}" +
            "if(r.style){var v=r.style.getPropertyValue('font-size');" +
            "if(v){v=String(v).replace(/^\\s+|\\s+$/g,'');" +
            "if(v.indexOf('--gauge-num-scale')<0&&/[0-9]/.test(v)&&!/^[+-]?[0-9]*\\.?[0-9]+$/.test(v)){" +
            "r.style.setProperty('font-size','calc('+v+' * var(--gauge-num-scale, 1))',r.style.getPropertyPriority('font-size'));" +
            "mudadas++;}}}" +
            "if(r.cssRules){var internas=null;try{internas=r.cssRules;}catch(e){internas=null;}regras(internas);}" +
            "}}" +
            "var folhas=document.styleSheets;for(var f=0;f<folhas.length;f++){var lista=null;" +
            "try{lista=folhas[f].cssRules;}catch(e){lista=null;}regras(lista);}" +
            "try{console.log('[DeepGWT] escala dos números: '+mudadas+' regras de font-size');}catch(e){}" +
            "})()"

    /**
     * Garantias de compatibilidade do fim de cada carga (seção 10.1): `control` e `updateWarning`
     * existem (só se cria o que falta; os substitutos não fazem nada nem escrevem no console) e, se
     * `window.Android` existe, nele existem `setUseDecentralizedScreens`, `setNativeMaskState`,
     * `setNativeMasksConfig` e `getAvailableKeys`.
     */
    val compatibilidade: String =
        "(function(){var w=window;" +
            "if(typeof control!=='function'){w.control=function(){};}" +
            "if(typeof updateWarning!=='function'){w.updateWarning=function(){};}" +
            "var a=w.Android;if(!a){return;}" +
            "try{if(typeof a.setUseDecentralizedScreens!=='function'){a.setUseDecentralizedScreens=function(){};}}catch(e){}" +
            "try{if(typeof a.setNativeMaskState!=='function'){a.setNativeMaskState=function(){};}}catch(e){}" +
            "try{if(typeof a.setNativeMasksConfig!=='function'){a.setNativeMasksConfig=function(){};}}catch(e){}" +
            "try{if(typeof a.getAvailableKeys!=='function'){a.getAvailableKeys=function(){return " +
            textoJs(listaJson(ContratoV1.CHAVES_DO_SUBSTITUTO)) + ";};}}catch(e){}" +
            "})()"

    /**
     * A batida própria (seção 5.8): um intervalo de 2 s que chama `Android.heartbeat()`, trocando o
     * anterior guardado em `window.__havalHeartbeatTimer`.
     */
    val batida: String =
        "(function(){try{if(window.__havalHeartbeatTimer){clearInterval(window.__havalHeartbeatTimer);}}catch(e){}" +
            "window.__havalHeartbeatTimer=setInterval(function(){try{if(window.Android&&" +
            "typeof window.Android.heartbeat==='function'){window.Android.heartbeat();}}catch(e){}},2000);})()"

    /**
     * Cortina de abertura presa (seção 5.8): `.boot`, `#boot`, `.boot-overlay`, `#bootOverlay` com
     * opacidade computada > 0,01 ganham `done`/`hide`, `opacity: 0` e `pointer-events: none` inline.
     * Devolve `{presas, app, corpo, oculto}` para registro.
     */
    val cortina: String =
        "(function(){var seletores=['.boot','#boot','.boot-overlay','#bootOverlay'];var vistos=[];var presas=0;" +
            "for(var s=0;s<seletores.length;s++){var lista=document.querySelectorAll(seletores[s]);" +
            "for(var i=0;i<lista.length;i++){var el=lista[i];if(vistos.indexOf(el)>=0){continue;}vistos.push(el);" +
            "var op=parseFloat(window.getComputedStyle(el).opacity);if(isNaN(op)){op=1;}" +
            "if(op>0.01){if(el.classList){el.classList.add('done');el.classList.add('hide');}" +
            "el.style.opacity='0';el.style.pointerEvents='none';presas++;}}}" +
            "var app=document.getElementById('app');" +
            "return {presas:presas,app:app?app.children.length:null," +
            "corpo:document.body?document.body.children.length:null,oculto:document.visibilityState};})()"

    /** Fim do painel (seção 5.6): para a batida injetada e chama `window.cleanup()` se existir. */
    val encerramento: String =
        "(function(){try{if(window.__havalHeartbeatTimer){clearInterval(window.__havalHeartbeatTimer);" +
            "window.__havalHeartbeatTimer=null;}}catch(e){}" +
            "try{if(typeof window.cleanup==='function'){window.cleanup();}}catch(e){}})()"

    /** Diagnóstico opcional da projeção (seção 5.6): o retorno de `window.__havalProjectionDebug()`. */
    val depuracaoDaProjecao: String =
        "(function(){try{if(typeof window.__havalProjectionDebug!=='function'){return null;}" +
            "var r=window.__havalProjectionDebug();return (typeof r==='string')?r:JSON.stringify(r);}" +
            "catch(e){return null;}})()"

    /** Array JSON de strings (sem org.json). */
    fun listaJson(itens: List<String>): String = JsonDaPonte.listaDeTextos(itens)
}

/**
 * As variáveis CSS que o app escreve na página (seção 5.7) a partir das preferências. Kotlin puro.
 */
object AjustesVisuais {

    const val ESCALA_DOS_NUMEROS = "--gauge-num-scale"
    const val TINTA_NOTURNA = "--night-tint"
    const val BRILHO_NOTURNO = "--night-glow"
    const val ESCALA_DA_TEMPERATURA = "--orig-temp-scale"

    const val PREF_ESCALA_DOS_NUMEROS = "gaugeNumberScale"
    const val PREF_COR_NOTURNA = "nightModeColor"
    const val PREF_ESCALA_DA_TEMPERATURA = "originalPanelTempScale"

    /** `nightModeColor` -> (tinta, brilho). `amber` é o padrão (e o que vale para valor desconhecido). */
    fun corNoturna(valor: String?): Pair<String, String> = when (valor?.trim()) {
        "red" -> "#ff2200" to "rgba(255,34,0,0.4)"
        "mixed" -> "#ff5c1a" to "rgba(255,92,26,0.42)"
        else -> "#ff9500" to "rgba(255,149,0,0.45)"
    }

    /** A escala dos números como texto (`1.0`, `1.15`); inválida ou ausente = `1.0`. */
    fun escalaComoTexto(escala: Float?): String =
        if (escala == null || escala.isNaN() || escala.isInfinite() || escala <= 0f) "1.0" else escala.toString()
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
