// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ponte

import android.webkit.JavascriptInterface

/**
 * O `window.Android` da página do tema (seção 4 da especificação).
 *
 * Nomes, quantidade e TIPOS Java dos parâmetros são contrato: o WebView converte o valor do JS
 * conforme o tipo do parâmetro (`int`, `boolean` primitivos; `String`). Parâmetro `String` aceita
 * `null` sem lançar (vira ""), e nenhum método deixa exceção chegar à página. Rodam na thread do
 * WebView, com o JS da página parado até voltarem.
 */
class ObjetoAndroid internal constructor(private val ponte: PonteDosTemas) {

    /** 1. A página está viva. */
    @JavascriptInterface
    fun heartbeat() {
        seguro { ponte.batida() }
    }

    /** 2. Não faz nada: o selo de aviso é decidido pelo app. */
    @JavascriptInterface
    fun setWarningActive(isActive: Boolean) {
        // Temas antigos mandam isto; não pode mudar nada.
    }

    /** 3. Área dos apps abertos no painel. */
    @JavascriptInterface
    fun setAppDefaultDimensions(x: Int, y: Int, width: Int, height: Int) {
        seguro { ponte.areaDosApps(x, y, width, height) }
    }

    /** 4. Liga/desliga uma das cinco máscaras nativas. */
    @JavascriptInterface
    fun setNativeMaskState(maskName: String?, visible: Boolean) {
        seguro { ponte.mascaraNativa(maskName, visible) }
    }

    /** 5. Configuração das máscaras. */
    @JavascriptInterface
    fun setNativeMasksConfig(jsonConfig: String?) {
        seguro { ponte.configuracaoDasMascaras(jsonConfig) }
    }

    /** 6. Só `display`/`currentClusterDisplay`: o modo de exibição. */
    @JavascriptInterface
    fun saveSetting(key: String?, value: String?) {
        seguro { ponte.gravarAjuste(key, value) }
    }

    /** 7. Escreve no carro (só as graváveis) ou o modo de exibição (`display`). */
    @JavascriptInterface
    fun updateCarData(key: String?, value: String?) {
        seguro { ponte.escreverNoCarro(key, value) }
    }

    /** 8. Ação do sistema sem payload (= payload ""). */
    @JavascriptInterface
    fun triggerSystemAction(action: String?) {
        seguro { ponte.acaoDoSistema(action, "") }
    }

    /** 9. Ação do sistema. */
    @JavascriptInterface
    fun triggerSystemAction(action: String?, payload: String?) {
        seguro { ponte.acaoDoSistema(action, payload) }
    }

    /** 10. O vão da projeção da central declarado pelo tema. */
    @JavascriptInterface
    fun setProjectionBox(x: Int, y: Int, width: Int, height: Int) {
        seguro { ponte.declararCaixaDaProjecao(x, y, width, height) }
    }

    /** 11. Grava uma preferência do tema (escopada). */
    @JavascriptInterface
    fun savePreference(key: String?, value: String?) {
        seguro { ponte.gravarPreferencia(key, value) }
    }

    /** 12. Papel de parede do display 1 (só `THEME`). */
    @JavascriptInterface
    fun setClusterBackground(type: String?, value: String?) {
        seguro { ponte.definirFundo(type, value) }
    }

    /** 13. O papel de parede do próprio tema, se o modo atual é `THEME`. */
    @JavascriptInterface
    fun setThemeBackground(relativePath: String?) {
        seguro { ponte.definirFundoDoTema(relativePath) }
    }

    /** 14. De onde vem o papel de parede do display 1 (JSON). */
    @JavascriptInterface
    fun getClusterBackdropSource(): String = lendo(FundoDoPainel.VAZIO) { ponte.origemDoFundo() }

    /** 15. Preferência do tema (escopada, com a global de reserva). */
    @JavascriptInterface
    fun getPreference(key: String?, defaultValue: String?): String =
        lendo(defaultValue.orEmpty()) { ponte.lerPreferencia(key, defaultValue) }

    /** 16. Os `<DisplayMode>` do tema ativo (JSON). */
    @JavascriptInterface
    fun getThemeDisplayModes(): String = lendo("[]") { ponte.modosDeExibicao() }

    /** 17. Assina chaves (array JSON). */
    @JavascriptInterface
    fun subscribe(keysJson: String?) {
        seguro { ponte.assinar(keysJson) }
    }

    /** 18. Cancela assinaturas (array JSON), exatamente como vieram. */
    @JavascriptInterface
    fun unsubscribe(keysJson: String?) {
        seguro { ponte.cancelarAssinatura(keysJson) }
    }

    /** 19. Leitura pontual de uma chave legível. */
    @JavascriptInterface
    fun getCarData(key: String?): String = lendo("") { ponte.lerParaPagina(key) }

    /** 20. Sempre recusado. */
    @JavascriptInterface
    fun launchApp(packageName: String?, displayId: Int) {
        seguro { ponte.recusarApp("launchApp", packageName) }
    }

    /** 21. Sempre recusado. */
    @JavascriptInterface
    fun killApp(packageName: String?) {
        seguro { ponte.recusarApp("killApp", packageName) }
    }

    /** 22. A lista 6.1 (array JSON). */
    @JavascriptInterface
    fun getAvailableKeys(): String = ponte.chavesDisponiveis()

    /** Sem efeito; a página do painel original o chama durante a carga. */
    @JavascriptInterface
    fun setUseDecentralizedScreens(enabled: Boolean) {
        // Sem efeito (decisão 6 da sala suja).
    }

    private inline fun seguro(bloco: () -> Unit) {
        try {
            bloco()
        } catch (e: Exception) {
            // Exceção aqui viraria erro no JS do tema; o registro de cada método já cobre as recusas.
        }
    }

    private inline fun lendo(padrao: String, bloco: () -> String): String = try {
        bloco()
    } catch (e: Exception) {
        padrao
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
