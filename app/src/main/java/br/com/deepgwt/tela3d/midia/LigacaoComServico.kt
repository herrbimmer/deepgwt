package br.com.deepgwt.tela3d.midia

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.SystemClock

/**
 * Ligação (BIND_AUTO_CREATE) a um serviço de outro app. Respeita a tentativa pendente e recua depois
 * de uma recusa do sistema (defeito 5): quem chama pode pedir [garantir] a cada ciclo sem derrubar a
 * conexão recém-criada. Pode ser chamada de qualquer thread.
 */
internal class LigacaoComServico(
    private val contexto: Context,
    private val nome: String,
    /** Intenções tentadas em ordem até o sistema aceitar uma. */
    private val intencoes: List<Intent>,
    private val aoConectar: (IBinder) -> Unit = {},
    private val aoDesconectar: () -> Unit = {},
    private val agora: () -> Long = SystemClock::elapsedRealtime,
) {
    private val politica = PoliticaDeReligacao()
    private val trava = Any()
    private var ligada = false

    @Volatile
    private var binderAtual: IBinder? = null

    /** O binder do serviço, se está vivo; senão nulo. */
    val binder: IBinder?
        get() {
            val b = binderAtual ?: return null
            return if (b.isBinderAlive) b else null
        }

    private val conexao = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, servico: IBinder?) {
            synchronized(trava) { politica.aoConectar() }
            binderAtual = servico
            if (servico != null) aoConectar(servico)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            esquecerBinder()
        }

        override fun onBindingDied(name: ComponentName?) {
            esquecerBinder()
            desfazer()
        }

        override fun onNullBinding(name: ComponentName?) {
            esquecerBinder()
        }
    }

    private fun esquecerBinder() {
        val tinha = binderAtual != null
        binderAtual = null
        synchronized(trava) { politica.aoDesconectar() }
        if (tinha) aoDesconectar()
    }

    /**
     * Garante uma ligação viva ou pendente. Devolve o binder se já está conectado.
     */
    fun garantir(): IBinder? {
        binder?.let { return it }
        synchronized(trava) {
            if (binderAtual != null) { // binder morto: desfazer para ligar de novo
                binderAtual = null
                desfazerSemTrava()
            }
            if (!politica.deveLigar(agora(), ligado = false)) return null
            var aceito = false
            for (intencao in intencoes) {
                aceito = try {
                    contexto.bindService(intencao, conexao, Context.BIND_AUTO_CREATE)
                } catch (e: SecurityException) {
                    Diario.aviso("ligação a $nome negada", e)
                    false
                }
                ligada = true // mesmo recusada, a conexão precisa de um unbindService
                if (aceito) break
                desfazerSemTrava()
            }
            politica.aoTentar(agora(), aceito)
            if (!aceito) Diario.info("sistema recusou a ligação a $nome; nova tentativa em alguns segundos")
        }
        return null
    }

    /** Solta a ligação (ao parar). */
    fun soltar() {
        binderAtual = null
        desfazer()
        synchronized(trava) { politica.aoDesconectar() }
    }

    private fun desfazer() = synchronized(trava) { desfazerSemTrava() }

    private fun desfazerSemTrava() {
        if (!ligada) return
        ligada = false
        try {
            contexto.unbindService(conexao)
        } catch (_: IllegalArgumentException) {
        }
    }

    companion object {
        fun intencaoDeComponente(pacote: String, classe: String): Intent =
            Intent().setComponent(ComponentName(pacote, classe))
    }
}
