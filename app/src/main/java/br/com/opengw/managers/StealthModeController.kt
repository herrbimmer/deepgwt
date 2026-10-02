package br.com.opengw.managers

import br.com.opengw.nucleo.Shell
import br.com.opengw.nucleo.OpenGw
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.edit
import br.com.opengw.luz.LuzAmbiente
import br.com.opengw.nucleo.Preferencias
import br.com.opengw.barra.Barra
import br.com.opengw.ui.telas.TemaEmbutido
/**
 * Modo sorrateiro: oculta barra inferior, efeitos de ambient light, painel virtual e o ícone do
 * app na tela inicial, deixando a multimídia como se nunca tivesse sido modificada.
 *
 * Não existe (nem deve existir) nenhuma forma de sair pelo carro. A versão anterior tinha uma
 * sequência física no volante (farol alto 2x + seta D-E-D-E em menos de 8s) como saída, mas essa
 * sequência já deixou usuários travados fora do próprio app — a janela de 8s é apertada demais e o
 * listener do carro podia nem estar registrado ainda no momento em que o modo foi ativado. A
 * única saída agora é o botão "Sair do Modo Sorrateiro" do instalador Haval Install, que reescreve
 * o estado direto no arquivo de SharedPreferences via shell (ver `reset_stealth_mode` no
 * src-tauri/src/lib.rs do instalador) — muito mais confiável que depender de sinais do carro.
 *
 * O estado ativo/inativo fica em SharedPreferences (device-protected, sobrevive a reboot), e a
 * visibilidade do ícone é um component-enabled-state do PackageManager, que também sobrevive a
 * reboot por conta própria.
 */
object StealthModeController {
    private const val TAG = "StealthModeController"

    // Alias exportado como LAUNCHER (ver AndroidManifest.xml); desativar o alias esconde o ícone
    // sem impedir `am start -n .../.AberturaActivity` (usado pelo instalador como saída de
    // emergência).
    private val LAUNCHER_ALIAS_CLASSES =
            listOf(
                    "br.com.opengw.LauncherAlias",
                    // "Atualizar OPEN GW" é um segundo ícone; deixá-lo de fora entregaria que o
                    // app está instalado justamente quando o usuário pediu para escondê-lo.
                    "br.com.opengw.UpdaterAlias"
            )

    /** Quem desenha a tela inicial nesta central — precisa ser avisado para remontar a grade. */
    private val LAUNCHERS_TO_REFRESH =
            listOf(HomeLauncherController.FACTORY_PACKAGE, HomeLauncherController.VIEWER_PACKAGE)

    private fun prefs(context: Context) =
            OpenGw.contextoProtegido
                    .getSharedPreferences("opengw_prefs", Context.MODE_PRIVATE)

    fun isActive(context: Context): Boolean =
            prefs(context).getBoolean(Preferencias.STEALTH_MODE_ACTIVE.chave, false)

    fun activate(context: Context) {
        val appContext = context.applicationContext
        prefs(appContext).edit { putBoolean(Preferencias.STEALTH_MODE_ACTIVE.chave, true) }
        applyHiddenState(appContext)
    }

    private fun applyHiddenState(context: Context) {
        setLauncherIconVisible(context, false)
        Barra.parar(context)
        LuzAmbiente.parar(context)

        val p = prefs(context)
        if (p.getBoolean(Preferencias.ENABLE_VIRTUAL_CLUSTER.chave, false)) {
            // Mesmo mecanismo do switch manual "Painel Virtual" em Telas: volta o tema padrão e
            // desliga o painel. Ao sair do stealth (pelo instalador) o usuário precisa reativar
            // manualmente (mesmo fluxo/aviso de quem liga o Painel Virtual pela primeira vez).
            TemaEmbutido.restaurar(p)
            p.edit { putBoolean(Preferencias.ENABLE_VIRTUAL_CLUSTER.chave, false) }
        }
        Log.w(TAG, "Modo sorrateiro ativado: barra, efeitos, painel e ícone ocultados.")
    }

    /**
     * Esconde (ou devolve) os ícones.
     *
     * POR QUE NÃO BASTA `setComponentEnabledSetting`
     *
     * Ele grava o estado, sim — mas com [PackageManager.DONT_KILL_APP] o sistema NÃO avisa quem
     * desenha a tela inicial. O launcher de fábrica desta central (`com.beantechs.mediacenter`) e
     * o launcher 3D montam a grade uma vez e ficam com ela em memória: o ícone continuava lá até
     * alguém reiniciar o carro. Era esse o defeito relatado — "o Modo Sorrateiro não esconde os
     * ícones". O estado estava certo; a tela é que não tinha sido reconstruída.
     *
     * Três camadas, da mais forte para a mais fraca, todas toleradas se falharem:
     *   1. `pm disable`/`enable` via Shizuku — escrita de sistema, e o próprio `pm` notifica o
     *      PackageManager, que dispara a mudança para todos os observadores de pacote;
     *   2. `setComponentEnabledSetting` sem `DONT_KILL_APP` — o caminho que sempre existiu, agora
     *      pedindo ao sistema que derrube o processo, o que também conta como mudança observável;
     *   3. um `PACKAGE_CHANGED` explícito para os dois launchers conhecidos, para o caso de um
     *      deles só recarregar a grade quando recebe o broadcast.
     */
    private fun setLauncherIconVisible(context: Context, visible: Boolean) {
        val pm = context.packageManager
        val state =
                if (visible) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        // A visibilidade de componente é POR USUÁRIO. Em Android Automotive o app roda como
        // usuário 10 e um `pm disable` sem `--user` escreve no usuário 0: responde sucesso e o
        // ícone continua na tela do motorista.
        val userId = (android.os.Process.myUid() / 100000).toString()
        val shizuku = runCatching { Shell.disponivel() }.getOrDefault(false)

        LAUNCHER_ALIAS_CLASSES.forEach { aliasClass ->
            val component = "${context.packageName}/$aliasClass"
            if (shizuku) {
                runCatching {
                    val verb = if (visible) "enable" else "disable"
                    val out = Shell.executar(
                            arrayOf("pm", verb, "--user", userId, component)
                    )
                    Log.w(TAG, "pm $verb $component -> ${out?.trim().orEmpty().ifBlank { "(sem saída)" }}")
                }.onFailure { Log.w(TAG, "pm via Shizuku falhou para $component: ${it.message}") }
            }
            try {
                val alias = ComponentName(context.packageName, aliasClass)
                // Sem DONT_KILL_APP de propósito: ver o comentário acima.
                pm.setComponentEnabledSetting(alias, state, 0)
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao alterar visibilidade do ícone $aliasClass", e)
            }
        }
        notifyLaunchersOfIconChange(context)
    }

    /** Cutuca os launchers conhecidos para que remontem a grade de ícones agora, não no próximo boot. */
    private fun notifyLaunchersOfIconChange(context: Context) {
        val data = android.net.Uri.parse("package:${context.packageName}")
        for (launcher in LAUNCHERS_TO_REFRESH) {
            runCatching {
                val intent = Intent(Intent.ACTION_PACKAGE_CHANGED, data)
                        .setPackage(launcher)
                        .putExtra(Intent.EXTRA_UID, android.os.Process.myUid())
                        .putExtra(Intent.EXTRA_CHANGED_COMPONENT_NAME_LIST, LAUNCHER_ALIAS_CLASSES.toTypedArray())
                context.sendBroadcast(intent)
            }.onFailure { Log.w(TAG, "Não deu para avisar $launcher: ${it.message}") }
        }
    }
}
