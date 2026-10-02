package br.com.opengw.hud

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.FrameLayout

/**
 * "Controle do HUD": segurar OK no volante entrega as setas ao HUD (Alpha 9.5).
 *
 * ## O PEDIDO
 *
 * "O controle das telas do HUD não deve ser feito pelas setas puramente. A pessoa deve apertar e
 * segurar OK no volante; aí o painel de instrumentos fica borrado por completo, com um aviso
 * 'Controle do HUD ativado', e só então as setas controlam o HUD. Para parar, é só segurar o OK de
 * novo, e as setas voltam a controlar o painel normal."
 *
 * ## AS TECLAS
 *
 * Fora do controle nada muda para o painel: ↑/↓ são dele, como de fábrica. Com o controle ativo:
 *  • ↑/↓ trocam a página do HUD na hora ([HudWheelInput.passoDaTecla]);
 *  • OK (curto) é engolido — sob a cortina ele apertaria algo no painel sem ninguém ver;
 *  • OK segurado sai. Um segundo OK segurado dentro de [GUARDA_DO_OK_LONGO_MS] é ignorado: o
 *    `inputservice` pode repetir o keycode enquanto a tecla continua segura, e entrar e sair no
 *    mesmo gesto seria pior que nada. Pelo mesmo motivo, o OK curto que chegar logo depois de sair
 *    ([GUARDA_DO_OK_MS]) também é engolido: seria o "soltar" do gesto;
 *  • BACK sai e segue para o carro (é ele que dispensa o aviso de fábrica);
 *  • ←/→ seguem para o carro, que troca de card com elas por conta própria.
 *
 * ## ONDE O CONTROLE SAI SOZINHO
 *
 * A cortina tapa o painel inteiro — velocidade, luzes, tudo. Por isso ele não fica preso:
 * [TEMPO_SEM_TECLA_MS] sem tecla, um aviso NOVO do carro no painel, o HUD saindo do ar ou o
 * interruptor "Controles pelo volante" desligado encerram o controle.
 *
 * Kotlin puro em [EstadoDoControleDoHud] (testável); aqui fica o relógio, a cortina e o HUD.
 */
sealed interface EfeitoDoControle {
    data object Nenhum : EfeitoDoControle
    data object Entrou : EfeitoDoControle
    data class Saiu(val motivo: String) : EfeitoDoControle
    data class Andou(val passo: Int) : EfeitoDoControle
}

enum class TeclaDoControle { CIMA, BAIXO, OK, OK_LONGO, VOLTAR, OUTRA }

class EstadoDoControleDoHud {

    companion object {
        /** Depois de entrar ou sair, outro OK segurado antes disto é o mesmo gesto repetido. */
        const val GUARDA_DO_OK_LONGO_MS = 2_500L

        /** Depois de sair, um OK curto antes disto é o "soltar" do OK segurado. */
        const val GUARDA_DO_OK_MS = 1_500L

        /** Sem tecla por este tempo, o painel volta: a cortina nunca fica esquecida. */
        const val TEMPO_SEM_TECLA_MS = 20_000L
    }

    var ativo = false
        private set

    private var alternouEm: Long? = null
    private var ultimaTeclaEm = 0L

    /**
     * Um aperto físico. [podeEntrar] = HUD no ar, controle ligado em Telas e o OK segurado não é
     * de outra função agora (a página Central do Círculo do painel projeta com ele).
     *
     * @return o efeito e se a tecla fica com o HUD (true) ou segue para o resto do app.
     */
    fun aoApertar(tecla: TeclaDoControle, agoraMs: Long, podeEntrar: Boolean): Pair<EfeitoDoControle, Boolean> {
        val ultima = alternouEm
        val dentroDaGuarda = ultima != null && agoraMs - ultima < GUARDA_DO_OK_LONGO_MS
        if (!ativo) {
            return when (tecla) {
                TeclaDoControle.OK_LONGO ->
                    when {
                        // A repetição do OK que acabou de sair: engole, senão viraria mãos-livres.
                        dentroDaGuarda -> EfeitoDoControle.Nenhum to true
                        podeEntrar -> {
                            ativo = true
                            alternouEm = agoraMs
                            ultimaTeclaEm = agoraMs
                            EfeitoDoControle.Entrou to true
                        }
                        else -> EfeitoDoControle.Nenhum to false
                    }
                // O "soltar" do OK que encerrou o controle não aperta nada no painel.
                TeclaDoControle.OK ->
                    EfeitoDoControle.Nenhum to (ultima != null && agoraMs - ultima < GUARDA_DO_OK_MS)
                else -> EfeitoDoControle.Nenhum to false
            }
        }
        ultimaTeclaEm = agoraMs
        return when (tecla) {
            TeclaDoControle.CIMA -> EfeitoDoControle.Andou(-1) to true
            TeclaDoControle.BAIXO -> EfeitoDoControle.Andou(1) to true
            TeclaDoControle.OK -> EfeitoDoControle.Nenhum to true
            TeclaDoControle.OK_LONGO ->
                if (dentroDaGuarda) {
                    EfeitoDoControle.Nenhum to true
                } else {
                    ativo = false
                    alternouEm = agoraMs
                    EfeitoDoControle.Saiu("segurou OK") to true
                }
            // BACK sai, mas continua até o carro: é ele que dispensa o aviso de fábrica.
            TeclaDoControle.VOLTAR -> {
                ativo = false
                alternouEm = agoraMs
                EfeitoDoControle.Saiu("voltar") to false
            }
            TeclaDoControle.OUTRA -> EfeitoDoControle.Nenhum to false
        }
    }

    /** Encerra por fora (aviso, HUD fora do ar, interruptor). */
    fun encerrar(motivo: String, agoraMs: Long): EfeitoDoControle {
        if (!ativo) return EfeitoDoControle.Nenhum
        ativo = false
        alternouEm = agoraMs
        return EfeitoDoControle.Saiu(motivo)
    }

    fun expirar(agoraMs: Long): EfeitoDoControle =
        if (ativo && agoraMs - ultimaTeclaEm >= TEMPO_SEM_TECLA_MS) encerrar("sem tecla", agoraMs)
        else EfeitoDoControle.Nenhum
}

/**
 * Liga o [EstadoDoControleDoHud] ao volante, ao HUD e à cortina no painel.
 *
 * Teclas chegam no binder do `inputservice`; a cortina e o HUD são mexidos na thread principal.
 */
object ControleDoHud {

    private const val TAG = "ControleDoHud"

    private val principal by lazy { Handler(Looper.getMainLooper()) }
    private val estado = EstadoDoControleDoHud()

    /** Lido pelo desenho do HUD (selo "controle") e por quem precisa saber. */
    @Volatile var ativo = false
        private set

    // ── Só na thread principal ──────────────────────────────────────────────────────────────
    private var cortina: CortinaDoControleDoHud? = null

    private val expirar = Runnable {
        val efeito = synchronized(estado) { estado.expirar(SystemClock.uptimeMillis()) }
        if (efeito is EfeitoDoControle.Saiu) aplicar(efeito) else if (ativo) agendarExpiracao()
    }

    /**
     * O projetor do painel subiu (com a raiz dele) ou caiu (null). Sem projetor no ar — painel de
     * fábrica sem nenhuma camada do OPEN GW — o controle funciona igual, só sem a cortina: o selo
     * no próprio HUD mostra que as setas estão com ele.
     */
    fun registrarRaiz(raiz: FrameLayout) {
        if (raizAtual === raiz) return
        cortina?.liberar()
        raizAtual = raiz
        cortina = CortinaDoControleDoHud(raiz.context, raiz).also { it.mostrar(ativo, animar = false) }
    }

    /** O projetor dono de [raiz] caiu. Um projetor novo que já registrou a dele não é afetado. */
    fun liberarRaiz(raiz: FrameLayout) {
        if (raizAtual !== raiz) return
        cortina?.liberar()
        cortina = null
        raizAtual = null
    }

    private var raizAtual: FrameLayout? = null

    /** @return true quando a tecla fica com o HUD. */
    fun aoApertar(tecla: TeclaDoControle, podeEntrar: Boolean): Boolean {
        val (efeito, consumida) = synchronized(estado) {
            estado.aoApertar(tecla, SystemClock.uptimeMillis(), podeEntrar)
        }
        ativo = estado.ativo
        if (efeito != EfeitoDoControle.Nenhum) principal.post { aplicar(efeito) }
        return consumida
    }

    /** Encerra por fora. Qualquer thread. */
    fun encerrar(motivo: String) {
        val efeito = synchronized(estado) { estado.encerrar(motivo, SystemClock.uptimeMillis()) }
        ativo = estado.ativo
        if (efeito != EfeitoDoControle.Nenhum) principal.post { aplicar(efeito) }
    }

    /** O carro abriu um aviso NOVO no painel: a cortina sai para ele ser visto. */
    fun aoAvisoNovoDoCarro() {
        if (ativo) encerrar("aviso do carro")
    }

    private fun aplicar(efeito: EfeitoDoControle) {
        when (efeito) {
            EfeitoDoControle.Nenhum -> Unit
            EfeitoDoControle.Entrou -> {
                Log.w(TAG, "Controle do HUD ativado (página ${HudPaginas.pagina})")
                cortina?.mostrar(true)
                agendarExpiracao()
            }
            is EfeitoDoControle.Saiu -> {
                Log.w(TAG, "Controle do HUD desativado: ${efeito.motivo}")
                principal.removeCallbacks(expirar)
                cortina?.mostrar(false)
            }
            is EfeitoDoControle.Andou -> {
                HudPaginas.mover(efeito.passo)
                Log.w(TAG, "HUD -> página ${HudPaginas.pagina.name}")
                cortina?.atualizar()
                agendarExpiracao()
            }
        }
        HudOverlayController.requestRedraw()
    }

    private fun agendarExpiracao() {
        principal.removeCallbacks(expirar)
        principal.postDelayed(expirar, EstadoDoControleDoHud.TEMPO_SEM_TECLA_MS + 50)
    }
}
