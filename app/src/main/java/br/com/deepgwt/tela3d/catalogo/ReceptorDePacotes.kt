package br.com.deepgwt.tela3d.catalogo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log

/** O que um broadcast de pacote significa para o catálogo. */
internal sealed interface EventoDePacote {
    /** Desinstalação de verdade (não é atualização). */
    data class Removido(val pacote: String) : EventoDePacote

    /** Instalação ou atualização concluída. */
    data class Instalado(val pacote: String) : EventoDePacote
}

/** Interpretação pura dos broadcasts de pacote. */
internal object InterpretadorDePacotes {
    const val ACAO_ADICIONADO = "android.intent.action.PACKAGE_ADDED"
    const val ACAO_SUBSTITUIDO = "android.intent.action.PACKAGE_REPLACED"
    const val ACAO_REMOVIDO = "android.intent.action.PACKAGE_REMOVED"
    const val ACAO_REMOVIDO_POR_COMPLETO = "android.intent.action.PACKAGE_FULLY_REMOVED"

    val ACOES = listOf(ACAO_ADICIONADO, ACAO_SUBSTITUIDO, ACAO_REMOVIDO, ACAO_REMOVIDO_POR_COMPLETO)

    /**
     * @param acao ação do broadcast
     * @param pacote o pacote dos dados (`package:<pacote>`)
     * @param substituindo o extra `EXTRA_REPLACING`
     * @return o evento, ou null se deve ser ignorado (ação alheia, pacote vazio, ou remoção que é
     *   só uma atualização do app)
     */
    fun interpretar(acao: String?, pacote: String?, substituindo: Boolean): EventoDePacote? {
        if (pacote.isNullOrEmpty()) return null
        return when (acao) {
            ACAO_REMOVIDO -> if (substituindo) null else EventoDePacote.Removido(pacote)
            ACAO_REMOVIDO_POR_COMPLETO -> EventoDePacote.Removido(pacote)
            ACAO_ADICIONADO, ACAO_SUBSTITUIDO -> EventoDePacote.Instalado(pacote)
            else -> null
        }
    }
}

/**
 * Receptor registrado em código (não no manifesto) enquanto a tela inicial existe: escuta
 * instalação, atualização e remoção de pacotes e avisa o [CatalogoDeApps]. O sistema entrega na
 * thread principal.
 *
 * Remoção que faz parte de uma atualização (`EXTRA_REPLACING`) é ignorada, e a instalação que
 * vem a seguir (`PACKAGE_REPLACED`/`PACKAGE_ADDED`) refaz o chip.
 */
class ReceptorDePacotes(private val catalogo: CatalogoDeApps) : BroadcastReceiver() {
    private var registrado = false

    /** Registra o receptor; chamar de novo é inofensivo. */
    fun registrar(contexto: Context) {
        if (registrado) return
        val filtro = IntentFilter()
        for (acao in InterpretadorDePacotes.ACOES) filtro.addAction(acao)
        filtro.addDataScheme("package")
        try {
            contexto.registerReceiver(this, filtro)
            registrado = true
        } catch (e: Exception) {
            Log.w(ETIQUETA, "Falha ao registrar o receptor de pacotes", e)
        }
    }

    /** Desregistra o receptor (ao destruir a tela inicial). */
    fun desregistrar(contexto: Context) {
        if (!registrado) return
        registrado = false
        try {
            contexto.unregisterReceiver(this)
        } catch (e: IllegalArgumentException) {
            // já não estava registrado
        }
    }

    override fun onReceive(contexto: Context?, intencao: Intent?) {
        if (intencao == null) return
        val pacote = intencao.data?.schemeSpecificPart
        val substituindo = intencao.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        when (val evento = InterpretadorDePacotes.interpretar(intencao.action, pacote, substituindo)) {
            is EventoDePacote.Removido -> catalogo.aoPacoteRemovido(evento.pacote, false)
            is EventoDePacote.Instalado -> catalogo.aoPacoteInstalado(evento.pacote)
            null -> if (intencao.action == InterpretadorDePacotes.ACAO_REMOVIDO && substituindo && !pacote.isNullOrEmpty()) {
                // Atualização: só solta o ícone em cache; o estado do usuário fica como está.
                catalogo.aoPacoteRemovido(pacote, true)
            }
        }
    }

    private companion object {
        const val ETIQUETA = "DeepGWT/Catalogo"
    }
}
