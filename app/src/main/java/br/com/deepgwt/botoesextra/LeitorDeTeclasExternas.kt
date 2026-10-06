package br.com.deepgwt.botoesextra

import android.util.Log
import br.com.deepgwt.botoesextra.RegrasDasTeclasExternas.DispositivoDeTeclas
import br.com.deepgwt.botoesextra.RegrasDasTeclasExternas.Fase
import br.com.deepgwt.nucleo.Shell
import java.util.concurrent.Executors

/**
 * Lê as teclas de um teclado ou dispositivo USB externo (Alpha 10.0), em qualquer tela, pelo
 * `getevent` do shell privilegiado — a mesma técnica dos gestos (`QuickGesturesManager`). Nada é
 * consumido: a tecla segue para o app da frente como sempre.
 *
 * Dois usos, um leitor só:
 *  - **disparar**: tecla ligada a uma ação, com "Botões extra" em dispositivo externo ligado;
 *  - **aprender**: a tela de configuração pede "a próxima tecla apertada" ([aprender]); nesse modo
 *    a tecla NÃO dispara nada, só é entregue a quem pediu.
 *
 * Dispositivos plugados depois são pegos pela releitura periódica da lista de nós.
 */
object LeitorDeTeclasExternas {
    private const val TAG = "TeclasExternas"
    private const val RELEITURA_MS = 10_000L

    @Volatile private var running = false
    @Volatile private var aprendiz: ((String, String) -> Unit)? = null
    private val emEscuta = java.util.Collections.synchronizedSet(HashSet<String>())
    private val acoes = Executors.newSingleThreadExecutor { r -> Thread(r, "TeclasExternas-acao") }

    /** Sobe o leitor se algo pedir (botões extra externos ligados ou uma tela aprendendo). Idempotente. */
    fun startIfEnabled() {
        if (BotoesExtraSettings.externoLigado() || aprendiz != null) start() else stop()
    }

    /**
     * Entrega a PRÓXIMA tecla apertada: [aoReceber] recebe o nome da tecla (`KEY_F13`) e o nome do
     * dispositivo. Uma vez só. Chame [cancelarAprender] ao fechar a tela.
     */
    fun aprender(aoReceber: (tecla: String, dispositivo: String) -> Unit) {
        aprendiz = aoReceber
        start()
    }

    fun cancelarAprender() {
        aprendiz = null
        startIfEnabled()
    }

    @Synchronized
    private fun start() {
        if (running) return
        running = true
        Thread({ supervisor() }, "TeclasExternas-supervisor").start()
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        val nos = synchronized(emEscuta) { emEscuta.toList() }
        emEscuta.clear()
        nos.forEach { no ->
            runCatching { Shell.executar(arrayOf("sh", "-c", "pkill -f 'getevent -l $no' || true")) }
        }
        Log.w(TAG, "Leitor de teclas externas parado")
    }

    private fun supervisor() {
        var avisou = false
        while (running) {
            if (!Shell.disponivel()) {
                if (!avisou) {
                    Log.w(TAG, "Shizuku ainda não está de pé; o leitor de teclas espera")
                    avisou = true
                }
                dormir(5_000)
                continue
            }
            avisou = false
            val dump = runCatching { Shell.executar(arrayOf("sh", "-c", "getevent -pl 2>/dev/null")) }.getOrDefault("")
            RegrasDasTeclasExternas.acharDispositivos(dump).forEach { escutar(it) }
            dormir(RELEITURA_MS)
        }
    }

    private fun escutar(d: DispositivoDeTeclas) {
        if (!emEscuta.add(d.caminho)) return
        Log.w(TAG, "Escutando teclas em ${d.caminho} (${d.nome})")
        Shell.emSegundoPlano(
            arrayOf("sh", "-c", "getevent -l ${d.caminho}"),
            object : Shell.Ouvinte {
                override fun linha(texto: String) {
                    if (!running) return
                    runCatching { aoLinha(d, texto) }.onFailure { Log.w(TAG, "linha '$texto'", it) }
                }

                override fun terminou(codigo: Int) {
                    emEscuta.remove(d.caminho)
                }
            }
        )
    }

    private fun aoLinha(d: DispositivoDeTeclas, texto: String) {
        val evento = RegrasDasTeclasExternas.lerEvento(texto) ?: return
        if (evento.fase != Fase.APERTOU) return
        val quemAprende = aprendiz
        if (quemAprende != null) {
            aprendiz = null
            quemAprende(evento.tecla, d.nome)
            return
        }
        if (!BotoesExtraSettings.externoLigado()) return
        val acao = BotoesExtraSettings.acaoDaTecla(evento.tecla) ?: return
        acoes.execute {
            runCatching { AcaoDeBotao.executar(acao) }.onFailure { Log.w(TAG, "ação de ${evento.tecla} falhou", it) }
        }
    }

    private fun dormir(ms: Long) {
        runCatching { Thread.sleep(ms) }
    }
}
