package br.com.opengw.services

import android.accessibilityservice.AccessibilityService as ServicoBaseDoAndroid
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import br.com.opengw.barra.CamadaDeAcessibilidade
import br.com.opengw.managers.GuardaDoLauncher3D
import br.com.opengw.telas.LancadorDeApps

/**
 * O serviço de acessibilidade do OPEN GW. Ele não lê o conteúdo das janelas; serve a três coisas:
 *
 *  - avisar quem precisa saber qual app veio para a frente e quando alguém tocou na barra do sistema;
 *  - ver a tecla Início (o filtro de teclas está ligado na configuração do serviço) sem nunca
 *    consumi-la, para a guarda do launcher 3D saber que houve um pedido de início;
 *  - oferecer a única camada de janela acima das do sistema, entregue a [CamadaDeAcessibilidade]
 *    enquanto o serviço estiver conectado.
 */
class ServicoDeAcessibilidade : ServicoBaseDoAndroid() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        CamadaDeAcessibilidade.conectou(this)
        GuardaDoLauncher3D.aoIniciar()
    }

    override fun onAccessibilityEvent(evento: AccessibilityEvent?) {
        if (evento == null) return
        val pacote = evento.packageName?.toString()
        when (evento.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (pacote == null) return
                semDerrubarOServico("mudança de janela") {
                    LancadorDeApps.aoMudarJanela(pacote)
                    GuardaDoLauncher3D.aoMudarJanela(pacote)
                }
            }
            // Um toque na barra do sistema (o painel lateral com a casinha) é um pedido de início.
            AccessibilityEvent.TYPE_VIEW_CLICKED ->
                semDerrubarOServico("toque") { GuardaDoLauncher3D.aoTocar(pacote) }
        }
    }

    override fun onInterrupt() {
        // Nada a interromper: o serviço não mantém feedback em andamento.
    }

    /**
     * Só vigia: o Início solto avisa a guarda, e o evento segue o caminho que o Android der a ele
     * (devolve sempre falso). Sem log por tecla.
     */
    override fun onKeyEvent(evento: KeyEvent?): Boolean {
        if (evento != null && evento.keyCode == KeyEvent.KEYCODE_HOME && evento.action == KeyEvent.ACTION_UP) {
            runCatching { GuardaDoLauncher3D.aoApertarInicio() }
        }
        return false
    }

    override fun onUnbind(intent: Intent?): Boolean {
        CamadaDeAcessibilidade.desconectou(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        // Pode vir depois do onUnbind: desconectar duas vezes não faz mal.
        CamadaDeAcessibilidade.desconectou(this)
        super.onDestroy()
    }

    /** Um erro de quem recebe o aviso não pode derrubar o serviço (e, com ele, o app). */
    private inline fun semDerrubarOServico(situacao: String, acao: () -> Unit) {
        try {
            acao()
        } catch (erro: Exception) {
            Log.w(TAG, "falha ao tratar $situacao: ${erro.message}")
        }
    }

    private companion object {
        const val TAG = "ServicoDeAcessibilidade"
    }
}
