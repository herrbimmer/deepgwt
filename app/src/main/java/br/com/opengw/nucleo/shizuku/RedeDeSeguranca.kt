package br.com.opengw.nucleo.shizuku

import br.com.opengw.nucleo.shizuku.RegrasDaSubida.AcaoDaRede
import java.util.concurrent.locks.ReentrantLock

/**
 * A rede de segurança da partida fria (§H), só a parte de decisão: quando chamar o
 * [IniciadorDoServidor]. Sem Android; [rodar] bloqueia a thread de quem chama (a facade abre uma
 * thread própria).
 *
 * Antes de cada tentativa confere o binder (D9: sem isso derrubaria o servidor que o caminho
 * principal acabou de subir) e pula a vez se o caminho principal estiver no meio de uma subida
 * ([travaDaSubida] segurada, D5).
 */
class RedeDeSeguranca(
    private val dormir: (Long) -> Unit,
    private val binderRespondendo: () -> Boolean,
    private val iniciador: () -> IniciadorDoServidor,
    private val pacote: () -> String,
    private val travaDaSubida: ReentrantLock,
    private val log: (String) -> Unit,
) {

    enum class Fim { BINDER_DE_PE, SUBIU, ESGOTOU }

    fun rodar(): Fim {
        dormir(RegrasDaSubida.REDE_ESPERA_INICIAL_MS)
        var tentativa = 1
        while (true) {
            val binder = pingSeguro()
            when (RegrasDaSubida.decidirRede(tentativa, binder, travaDaSubida.isLocked)) {
                AcaoDaRede.ENCERRAR -> return if (binder) Fim.BINDER_DE_PE else Fim.ESGOTOU
                AcaoDaRede.PULAR -> log("rede de segurança: subida em andamento, tentativa $tentativa pulada")
                AcaoDaRede.AGIR -> if (tentarUmaVez(tentativa)) return Fim.SUBIU
            }
            if (tentativa >= RegrasDaSubida.REDE_TENTATIVAS) return Fim.ESGOTOU
            tentativa++
            dormir(RegrasDaSubida.REDE_INTERVALO_MS)
        }
    }

    /** true = o servidor está de pé (binder respondendo ou pid vivo ~3 s depois do start). */
    private fun tentarUmaVez(tentativa: Int): Boolean {
        if (!travaDaSubida.tryLock()) {
            log("rede de segurança: subida em andamento, tentativa $tentativa pulada")
            return false
        }
        try {
            if (pingSeguro()) return true
            val ini = iniciador()
            try {
                val disparo = try {
                    ini.iniciar(pacote(), pelaRedeDeSeguranca = true)
                } catch (e: Exception) {
                    Disparo(disparado = false, erro = e.message ?: e.javaClass.simpleName)
                }
                if (!disparo.disparado) {
                    log("rede de segurança: tentativa $tentativa não disparou (${disparo.erro})")
                    return false
                }
                dormir(RegrasDaSubida.REDE_ESPERA_DEPOIS_DO_START_MS)
                val vivo = pingSeguro() || (runCatching { ini.servidorVivo() }.getOrNull() == true)
                log("rede de segurança: tentativa $tentativa ${if (vivo) "subiu o servidor" else "sem servidor"}")
                return vivo
            } finally {
                runCatching { ini.terminar() }
            }
        } finally {
            travaDaSubida.unlock()
        }
    }

    private fun pingSeguro(): Boolean = runCatching { binderRespondendo() }.getOrDefault(false)
}
