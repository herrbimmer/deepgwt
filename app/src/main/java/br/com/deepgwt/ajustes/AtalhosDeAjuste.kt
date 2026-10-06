package br.com.deepgwt.ajustes

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import br.com.deepgwt.nucleo.DeepGwt
import br.com.deepgwt.nucleo.Preferencias

/**
 * Atalhos de ajuste (Alpha 10.0): apertar e segurar um interruptor do app oferece "Adicionar atalho",
 * e o atalho vira um ícone na grade de apps do launcher do DeepGWT. Tocar no ícone liga ou desliga o
 * ajuste, sem abrir o app.
 *
 * Só entra ajuste que é interruptor do catálogo e tem tela no carro: o atalho liga e desliga pelo
 * MESMO caminho do instalador e da tela de Configurações ([AjustesRemotos.aplicar]), então o efeito
 * (serviço, painel, barramento) é o de sempre.
 */
object AtalhosDeAjuste {

    private const val TAG = "AtalhosDeAjuste"

    /** O launcher se inscreve para redesenhar a grade quando a lista muda. */
    @Volatile
    var aoMudar: (() -> Unit)? = null

    private fun prefs() =
        DeepGwt.contextoProtegido.getSharedPreferences("deepgwt_prefs", Context.MODE_PRIVATE)

    /** O ajuste existe, é um interruptor e não é só do computador. */
    fun podeVirarAtalho(id: String?): Boolean {
        val a = id?.let { CatalogoDeAjustes.ajuste(it) } ?: return false
        return a.tipo == TipoDeAjuste.INTERRUPTOR && !a.soNoComputador
    }

    /** Os atalhos de hoje: só os que o catálogo ainda conhece (um ajuste removido numa versão some). */
    fun listar(): List<String> =
        RegrasDosAtalhos.ler(prefs().getString(Preferencias.LAUNCHER_SETTING_SHORTCUTS.chave, null))
            .filter { podeVirarAtalho(it) }

    fun contem(id: String): Boolean = id in listar()

    fun rotulo(id: String): String = CatalogoDeAjustes.ajuste(id)?.rotulo ?: id

    /** Devolve falso se o ajuste não serve ou a lista está cheia. */
    fun adicionar(id: String): Boolean {
        if (!podeVirarAtalho(id)) return false
        val atual = listar()
        val nova = RegrasDosAtalhos.adicionar(atual, id)
        if (nova == atual && id !in atual) return false
        gravar(nova)
        return true
    }

    fun remover(id: String) {
        val atual = listar()
        val nova = RegrasDosAtalhos.remover(atual, id)
        if (nova != atual) gravar(nova)
    }

    private fun gravar(ids: List<String>) {
        prefs().edit().putString(Preferencias.LAUNCHER_SETTING_SHORTCUTS.chave, RegrasDosAtalhos.escrever(ids)).apply()
        runCatching { aoMudar?.invoke() }.onFailure { Log.w(TAG, "aviso ao launcher falhou", it) }
    }

    /**
     * Liga se estava desligado e desliga se estava ligado. Bloqueia (barramento, serviços): chame
     * fora da thread principal. O texto devolvido é o que aparece para o motorista.
     */
    fun alternar(id: String): String {
        val ajuste = CatalogoDeAjustes.ajuste(id) ?: return "Ajuste não encontrado"
        val atual = runCatching { AjustesRemotos.lerJson(false).optJSONObject("valores")?.opt(id) as? Boolean }
            .getOrNull() ?: return "${ajuste.rotulo}: não deu para ler o estado"
        val r = runCatching { AjustesRemotos.aplicar(mapOf(id to !atual)) }.getOrElse {
            Log.w(TAG, "alternar $id falhou", it)
            return "${ajuste.rotulo}: não deu para aplicar"
        }
        val recusa = r.optJSONArray("recusados")?.optJSONObject(0)?.optString("motivo")?.takeIf { it.isNotBlank() }
        if (recusa != null) return "${ajuste.rotulo}: $recusa"
        val novo = r.optJSONObject("valores")?.opt(id) as? Boolean ?: !atual
        return "${ajuste.rotulo}: ${if (novo) "ligado" else "desligado"}"
    }

    /** Roda [alternar] numa thread própria e mostra o resultado num aviso curto. */
    fun alternarEAvisar(id: String) {
        Thread({
            val texto = alternar(id)
            Handler(Looper.getMainLooper()).post {
                runCatching { Toast.makeText(DeepGwt.contexto, texto, Toast.LENGTH_SHORT).show() }
            }
        }, "atalho-de-ajuste").start()
    }
}
