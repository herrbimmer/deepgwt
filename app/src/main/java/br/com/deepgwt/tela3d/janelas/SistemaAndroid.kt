package br.com.deepgwt.tela3d.janelas

import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.provider.Settings
import android.view.View
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [PortaDoSistema] sobre o Android: monta as intents e as opções de abertura e as entrega à Activity
 * (por onde passa o aviso à guarda do launcher), e roda numa thread de trabalho tudo que consulta ou
 * muda tarefas, devolvendo o resultado à thread principal.
 *
 * A thread de trabalho é uma só: as operações de uma sequência (fechar a tarefa e depois encerrar o
 * app; remover as antigas antes de abrir) acontecem na ordem em que foram pedidas.
 */
internal class SistemaAndroid(
    private val anfitriao: AnfitriaoDasJanelas,
    private val operacoes: OperacoesDeTarefa,
    private val primitivas: PrimitivasAndroid,
    private val guarda: GuardaDeLevantar,
    private val ancoras: () -> AncorasInvisiveis?,
    private val avisoDeAbertura: (Intent) -> Unit,
    private val executarNoShell: (Array<String>) -> String,
    private val executor: ExecutorService,
    private val diario: Diario,
) : PortaDoSistema {

    private val contexto: Context get() = anfitriao.activity
    private val permissaoJaNegada = AtomicBoolean(false)

    // --- componente de entrada ---

    override fun componenteDeEntrada(pacote: String): String? =
        try {
            contexto.packageManager.getLaunchIntentForPackage(pacote)?.component?.flattenToString()
        } catch (e: Exception) {
            null
        }

    // --- aberturas (thread principal) ---

    override fun abrirTelaCheia(pacote: String, componente: String, limites: Retangulo, raizDasConfiguracoes: Boolean) {
        val flags = if (raizDasConfiguracoes) FLAGS_DA_RAIZ_DAS_CONFIGURACOES else FLAGS_DE_TELA_CHEIA
        abrir(pacote, componente, flags, opcoes(MODO_TELA_CHEIA, limites, ancora = null))
    }

    override fun abrirJanelaDeLado(pacote: String, componente: String, limites: Retangulo, reabertura: Boolean) {
        if (reabertura) {
            abrir(pacote, componente, FLAGS_DE_REABERTURA, opcoes(MODO_JANELA_LIVRE, limites, ancora = null))
        } else {
            abrir(pacote, componente, FLAGS_DE_JANELA_DE_LADO, opcoes(MODO_JANELA_LIVRE, limites, ancoras()?.paraRetangulo(limites)))
        }
    }

    override fun abrirProjecao(componente: String, limites: Retangulo) {
        val nome = ComponentName.unflattenFromString(componente)
            ?: throw IllegalArgumentException("componente inválido: $componente")
        abrir(nome.packageName, componente, FLAGS_DE_TELA_CHEIA, opcoes(MODO_TELA_CHEIA, limites, ancora = null))
    }

    private fun abrir(pacote: String, componente: String, flags: Int, opcoes: Bundle) {
        val nome = ComponentName.unflattenFromString(componente)
            ?: throw IllegalArgumentException("componente inválido: $componente")
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(nome)
            .setPackage(pacote)
            .setFlags(flags)
        avisoDeAbertura(intent)
        anfitriao.activity.startActivity(intent, opcoes)
    }

    /**
     * As opções de abertura: modo de janela pelo método oculto E pela chave de bundle, limites pelo
     * método público E pela chave de bundle, e a animação "clip reveal" a partir da âncora do lado
     * (só na primeira abertura de uma janela, se a âncora já foi medida).
     */
    private fun opcoes(modoDeJanela: Int, limites: Retangulo, ancora: View?): Bundle {
        ApisOcultas.liberar()
        val retangulo = Rect(limites.esquerda, limites.topo, limites.direita, limites.fundo)
        val opcoes = if (modoDeJanela == MODO_JANELA_LIVRE && ancora != null && ancora.width > 0 && ancora.height > 0) {
            ActivityOptions.makeClipRevealAnimation(ancora, 0, 0, ancora.width, ancora.height)
        } else {
            ActivityOptions.makeBasic()
        }
        try {
            ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Integer.TYPE).invoke(opcoes, modoDeJanela)
        } catch (e: Throwable) {
            // sem o método oculto (Android 11+): vale a chave de bundle abaixo
        }
        opcoes.setLaunchBounds(Rect(retangulo))
        val pacote = opcoes.toBundle() ?: Bundle()
        pacote.putInt(CHAVE_DO_MODO_DE_JANELA, modoDeJanela)
        pacote.putParcelable(CHAVE_DOS_LIMITES, Rect(retangulo))
        return pacote
    }

    override fun componentesDeReserva(tipo: TipoDeProjecao): List<String> {
        val pm = contexto.packageManager
        val lista = LinkedHashSet<String>()
        PacotesDeProjecao.atividadePreferida(tipo)?.let { texto ->
            val nome = ComponentName.unflattenFromString(texto)
            val exportada = nome != null && try {
                pm.getActivityInfo(nome, 0).exported
            } catch (e: Exception) {
                false
            }
            if (nome != null && exportada) lista += nome.flattenToString()
        }
        for (pacote in PacotesDeProjecao.pacotesDeReserva(tipo)) {
            val entrada = try {
                pm.getLaunchIntentForPackage(pacote)?.component
            } catch (e: Exception) {
                null
            }
            if (entrada != null) lista += entrada.flattenToString()
        }
        return lista.toList()
    }

    // --- C17 ---

    override fun ligarJanelaLivre() {
        val resolvedor = contexto.contentResolver
        try {
            Settings.Global.putInt(resolvedor, "enable_freeform_support", 1)
            Settings.Global.putInt(resolvedor, "force_resizable_activities", 1)
        } catch (e: SecurityException) {
            if (permissaoJaNegada.compareAndSet(false, true)) {
                diario.aviso("janela livre: sem permissão (precisa de shell ou WRITE_SECURE_SETTINGS)")
            }
        }
    }

    // --- ouvinte de pilha ---

    override fun registrarOuvinteDePilha(aoMudar: Runnable): Boolean = primitivas.registrarOuvinte(aoMudar)

    override fun removerOuvinteDePilha() = primitivas.removerOuvinte()

    // --- encerrar app (C34, D2): killBackgroundProcesses + am force-stop pelo Shell, em thread de trabalho ---

    override fun encerrarApp(pacote: String) {
        if (pacote.isEmpty() || pacote == contexto.packageName) return
        emFundo {
            try {
                (contexto.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.killBackgroundProcesses(pacote)
            } catch (e: Exception) {
                diario.aviso("killBackgroundProcesses($pacote) falhou: ${e.javaClass.simpleName}")
            }
            try {
                executarNoShell(arrayOf("am", "force-stop", pacote))
            } catch (e: Exception) {
                diario.aviso("am force-stop $pacote falhou: ${e.javaClass.simpleName}")
            }
        }
    }

    // --- consultas e mudanças de tarefa (thread de trabalho; retorno na principal) ---

    override fun removerTarefasDoPacote(pacote: String, aoTerminar: () -> Unit) {
        emFundoComRetorno(Unit, { operacoes.removerTodasDoPacote(pacote) }) { aoTerminar() }
    }

    override fun fecharTarefa(pacote: String, guardado: Int) {
        emFundo { operacoes.fechar(pacote, guardado) }
    }

    override fun fixarTarefa(pacote: String, guardado: Int, limites: Retangulo, aoTerminar: (Int) -> Unit) {
        emFundoComRetorno(-1, { operacoes.fixar(pacote, guardado, limites) }, aoTerminar)
    }

    override fun levantarTarefa(pacote: String, guardado: Int, aoTerminar: (ResultadoDoLevantar) -> Unit) {
        emFundoComRetorno(ResultadoDoLevantar(-1, false), { operacoes.levantar(pacote, guardado) }, aoTerminar)
    }

    override fun redimensionarDoPacote(pacote: String, guardado: Int, limites: Retangulo, aoTerminar: (Int) -> Unit) {
        emFundoComRetorno(-1, { operacoes.redimensionarDoPacote(pacote, guardado, limites) }, aoTerminar)
    }

    override fun fixarNoTopo(pacote: String, guardado: Int, aoTerminar: (Int) -> Unit) {
        emFundoComRetorno(-1, { operacoes.fixarNoTopo(pacote, guardado) }, aoTerminar)
    }

    override fun levantarPorPacote(pacote: String, aoTerminar: (ResultadoDoLevantar) -> Unit) {
        emFundoComRetorno(ResultadoDoLevantar(-1, false), { operacoes.levantarPorPacote(pacote) }, aoTerminar)
    }

    override fun levantarPorNumero(tarefa: Int, aoTerminar: (Boolean) -> Unit) {
        emFundoComRetorno(false, { primitivas.levantar(tarefa) }, aoTerminar)
    }

    override fun lerVisibilidade(pacote: String, guardado: Int, aoTerminar: (Int) -> Unit) {
        emFundoComRetorno(Visibilidade.DESCONHECIDA, { primitivas.visibilidade(pacote, guardado) }, aoTerminar)
    }

    override fun esquecerUltimoLevantar() = guarda.esquecer()

    private fun emFundo(trabalho: () -> Unit) {
        try {
            executor.execute {
                try {
                    trabalho()
                } catch (e: Throwable) {
                    diario.erro("trabalho de janelas falhou", e)
                }
            }
        } catch (e: RejectedExecutionException) {
            // a Activity foi destruída
        }
    }

    private fun <T> emFundoComRetorno(padrao: T, trabalho: () -> T, aoTerminar: (T) -> Unit) {
        emFundo {
            val resultado = try {
                trabalho()
            } catch (e: Throwable) {
                diario.erro("trabalho de janelas falhou", e)
                padrao
            }
            anfitriao.naPrincipal { aoTerminar(resultado) }
        }
    }

    companion object {
        /** `FLAG_ACTIVITY_NEW_TASK | CLEAR_TOP | RESET_TASK_IF_NEEDED`. */
        const val FLAGS_DE_TELA_CHEIA = 0x14200000

        /** As de tela cheia + `CLEAR_TASK`. */
        const val FLAGS_DA_RAIZ_DAS_CONFIGURACOES = 0x14208000

        /** `NEW_TASK | REORDER_TO_FRONT | TASK_ON_HOME`. */
        const val FLAGS_DE_JANELA_DE_LADO = 0x10024000

        /** `NEW_TASK | REORDER_TO_FRONT | NO_ANIMATION`. */
        const val FLAGS_DE_REABERTURA = 0x10030000

        const val MODO_JANELA_LIVRE = 5
        const val MODO_TELA_CHEIA = 1

        const val CHAVE_DO_MODO_DE_JANELA = "android.activity.windowingMode"
        const val CHAVE_DOS_LIMITES = "android:activity.launchBounds"
    }
}
