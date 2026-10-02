package br.com.opengw.broadcastReceivers

import br.com.opengw.nucleo.OpenGw
import br.com.opengw.veiculo.Comandos
import br.com.opengw.veiculo.Veiculo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import br.com.opengw.managers.PowerFlowMapper
import br.com.opengw.nucleo.Preferencias

/**
 * A ponta do OPEN GW que atende o launcher 3D (`com.havalh6.viewer`).
 *
 * O launcher já foi escrito esperando estas três mensagens — elas estão no código dele, com estes
 * nomes exatos — e até agora ninguém do lado do OPEN GW respondia. O resultado é que a telemetria
 * chegava (o OPEN GW já emite `com.haval.vehicle.EVENT_CHANGED` a cada mudança) mas o launcher
 * abria sem NENHUM valor: ele pede um retrato completo no boot, e esse pedido caía no vazio até
 * o primeiro sinal mudar sozinho. Num carro parado isso é quase nunca.
 *
 *   1. `com.haval.vehicle.REQUEST_SNAPSHOT` — "me manda tudo o que você já sabe". Leitura.
 *   2. `ACTION_VEHICLE_COMMAND`  — abrir/fechar vidro, teto, cortina. ESCRITA no veículo.
 *   3. `ACTION_UPDATE_CAR_DATA`  — escrever uma chave de configuração. ESCRITA no veículo.
 *
 * ESCRITA VEM LIGADA desde 2026-09-16, e a chave "Comandos do launcher" (tela Tela Inicial)
 * continua lá para desligar. Ela nasceu desligada por cautela e o efeito prático foi outro: quem
 * instalava o launcher 3D apertava os botões de vidro e de porta-malas do modelo e o carro não
 * fazia nada, sem nenhum aviso na tela — os pedidos eram recusados aqui, só no log. Um botão que
 * o produto desenha, no modelo do próprio carro, tem que funcionar por padrão; quem não quiser
 * desliga. Com a chave desligada, o pedido continua sendo registrado e recusado, não some calado.
 */
class ViewerBridgeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REQUEST_SNAPSHOT -> handleSnapshot(intent)
            ACTION_VEHICLE_COMMAND -> handleVehicleCommand(context, intent)
            ACTION_UPDATE_CAR_DATA -> handleUpdateCarData(context, intent)
        }
    }

    /**
     * Reemite todo o cache. `dispatchAllData()` já publica cada chave pelo mesmo
     * `EVENT_CHANGED` que o launcher escuta, então não há formato novo para combinar.
     */
    private fun handleSnapshot(intent: Intent) {
        val requester = intent.getStringExtra("requester") ?: "desconhecido"
        Log.w(TAG, "Retrato de telemetria pedido por $requester")
        Veiculo.quandoPronto{
            runCatching { Veiculo.reenviarTudo() }
                .onFailure { Log.e(TAG, "Falha ao reemitir telemetria: ${it.message}") }
            // `dispatchAllData` reemite as chaves CRUAS do carro. `haval.power.flow` e derivada e
            // nao esta nessa lista, entao sem esta linha o raio-X do conjunto abriria vazio e so
            // ganharia vida quando algum sinal se mexesse sozinho -- num carro parado, nunca.
            runCatching { PowerFlowMapper.republish() }
                .onFailure { Log.e(TAG, "Falha ao reemitir o fluxo de potência: ${it.message}") }
        }
    }

    private fun writesAllowed(context: Context): Boolean =
        OpenGw.contextoProtegido
            .getSharedPreferences("opengw_prefs", Context.MODE_PRIVATE)
            .getBoolean(Preferencias.VIEWER_COMMANDS_ENABLED.chave, true)

    private fun handleVehicleCommand(context: Context, intent: Intent) {
        val command = intent.getStringExtra("command")?.trim().orEmpty()
        val value = intent.getStringExtra("value")?.trim().orEmpty()
        if (command.isEmpty()) return

        if (!writesAllowed(context)) {
            Log.w(TAG, "Comando \"$command\" recusado: \"Comandos do launcher\" está desligado no OPEN GW.")
            return
        }

        Veiculo.quandoPronto{
            val handled = runCatching { applyCommand(command, value) }
                .onFailure { Log.e(TAG, "Comando \"$command\" falhou: ${it.message}") }
                .getOrDefault(false)
            if (!handled) {
                Log.w(TAG, "Comando \"$command\" ignorado: sem atuador nesta central.")
            }
        }
    }

    /**
     * Vocabulário de vidro, medido no carro e documentado pelo autor do launcher:
     * 0 = em movimento (transitório), 1 = fechado, 2 = totalmente aberto, 3 = parcial.
     *
     * Escrever 0 para abrir — que é o que este projeto fazia — não faz nada. Abrir é 2.
     * A ordem dos índices é a mesma de `door_status`: 0=dianteiro esquerdo, 1=dianteiro direito,
     * 2=traseiro esquerdo, 3=traseiro direito.
     */
    private fun applyCommand(command: String, value: String): Boolean {
        val level = value.toIntOrNull()?.coerceIn(0, 100)
        return when (command) {
            "close_windows" -> { Comandos.fecharTodasAsJanelas(); true }
            "open_windows" -> setAllWindows(WINDOW_OPEN)
            "toggle_windows" -> {
                val anyOpen = Comandos.estadoDasJanelas()?.any { it != WINDOW_CLOSED } ?: false
                if (anyOpen) Comandos.fecharTodasAsJanelas() else setAllWindows(WINDOW_OPEN)
                true
            }
            "toggle_window_fl" -> toggleWindow(0)
            "toggle_window_fr" -> toggleWindow(1)
            "toggle_window_rl" -> toggleWindow(2)
            "toggle_window_rr" -> toggleWindow(3)

            "close_sunroof" -> { Comandos.fecharTeto(false); true }
            "open_sunroof" -> Comandos.abrirTetoComCortina(100)
            "toggle_sunroof" -> {
                val open = (Comandos.aberturaDoTeto() ?: 0) > 0
                if (open) { Comandos.fecharTeto(false); true } else Comandos.abrirTetoComCortina(100)
            }
            "set_sunroof_level" -> level?.let {
                if (it > 0) Comandos.abrirTetoComCortina(it) else Comandos.teto(0)
            } ?: false

            "close_curtain" -> { Comandos.fecharCortina(); true }
            "open_curtain" -> { Comandos.abrirCortina(); true }
            "toggle_curtain" -> {
                val open = (Comandos.aberturaDaCortina() ?: 0) > 0
                if (open) Comandos.fecharCortina() else Comandos.abrirCortina()
                true
            }
            "set_curtain_level" -> level?.let { Comandos.cortina(it) } ?: false

            // Porta-malas. O comentário que estava aqui dizia que a central não expõe atuador
            // nenhum para ele — e isso estava errado: `IVehicle.setDoorOpen(índice, estado)` existe
            // na AIDL do carro, e o índice do porta-malas é o mesmo slot 5 do vetor `door_status`
            // que o próprio launcher já usa para desenhar a tampa. Enquanto ninguém ligou os dois,
            // o botão "ABRIR" do modelo 3D caía neste `else` e o carro não fazia nada.
            "toggle_trunk" -> {
                val aberto = Comandos.portaMalasAberto() ?: false
                Comandos.portaMalas(!aberto)
            }
            "open_trunk" -> Comandos.portaMalas(true)
            "close_trunk" -> Comandos.portaMalas(false)

            // Portas laterais continuam de fora: mexer numa porta com alguém encostado nela é
            // outra conversa, e o launcher não manda esse comando.
            else -> false
        }
    }

    private fun setAllWindows(state: Int): Boolean {
        val status = Comandos.estadoDasJanelas() ?: return false
        for (i in status.indices) Comandos.janela(i, state)
        return true
    }

    private fun toggleWindow(index: Int): Boolean {
        val status = Comandos.estadoDasJanelas() ?: return false
        if (index >= status.size) return false
        val target = if (status[index] == WINDOW_CLOSED) WINDOW_OPEN else WINDOW_CLOSED
        return Comandos.janela(index, target)
    }

    private fun handleUpdateCarData(context: Context, intent: Intent) {
        val key = intent.getStringExtra("key")?.trim().orEmpty()
        val value = intent.getStringExtra("value")?.trim().orEmpty()
        if (key.isEmpty()) return
        if (!writesAllowed(context)) {
            Log.w(TAG, "Escrita de \"$key\" recusada: \"Comandos do launcher\" está desligado no OPEN GW.")
            return
        }
        Veiculo.quandoPronto{
            val ok = runCatching { Veiculo.escreverConferindo(key, value) }
                .getOrDefault(false)
            Log.w(TAG, "Launcher escreveu $key=$value -> ${if (ok) "ok" else "falhou"}")
        }
    }

    companion object {
        private const val TAG = "ViewerBridgeReceiver"

        const val ACTION_REQUEST_SNAPSHOT = "com.haval.vehicle.REQUEST_SNAPSHOT"
        const val ACTION_VEHICLE_COMMAND = "br.com.opengw.ACTION_VEHICLE_COMMAND"
        const val ACTION_UPDATE_CAR_DATA = "br.com.opengw.ACTION_UPDATE_CAR_DATA"

        private const val WINDOW_CLOSED = 1
        private const val WINDOW_OPEN = 2

    }
}
