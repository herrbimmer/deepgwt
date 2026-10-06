// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.monitores

import android.graphics.Bitmap
import java.util.concurrent.atomic.AtomicInteger

/** O serviço do CarPlay da central, do ponto de vista do monitor: as quatro transações. */
internal interface ServicoDoCarPlay {
    /** O binder ainda está de pé. */
    val vivo: Boolean

    /** Transação 9: registra o nosso callback. Verdadeiro = saiu. */
    fun iniciarAvisos(): Boolean

    /** Transação 10: cancela o nosso callback. Verdadeiro = saiu. */
    fun pararAvisos(): Boolean

    /** Transação 25: tecla HID do iAP (1 tocar, 2 pausar, 4 próxima, 8 anterior; ação 0 descer, 1 soltar). */
    fun enviarTecla(tecla: Int, acao: Int): Boolean

    /** Transação 31: posição em ms. */
    fun definirPosicao(posicaoMs: Long): Boolean
}

/** O pedido de ligação ao serviço (`bindService` / `unbindService`). */
internal interface LigacaoComOServico {
    /** Pede a ligação (B3: componente explícito, depois ação + pacote). Verdadeiro = pedida. */
    fun ligar(): Boolean

    /** Desfaz a ligação; ignora erro. */
    fun desfazer()
}

/**
 * O miolo do monitor do CarPlay (B1 a B16): sem Android. O relógio, a principal, a ligação, o
 * serviço e a decodificação da capa entram por parâmetro, para o teste rodar com tudo falso.
 *
 * Estado compartilhado entre a thread do binder, a principal e as de fundo: protegido por [trava]
 * (DB6); nenhuma chamada para fora (binder, aviso à barra, ligação) acontece com a trava segurada.
 */
internal class NucleoDoMonitorDoCarPlay(
    private val agenda: AgendaDoMonitor,
    private val ligacao: LigacaoComOServico,
    /** Decodifica a capa do quadro (bytes, senão caminho). Roda em segundo plano. */
    private val capaDoQuadro: (QuadroDoCarPlay) -> Bitmap?,
    /** Roda em segundo plano (dispatcher de E/S no app). */
    private val emSegundoPlano: (() -> Unit) -> Unit,
    private val registro: RegistroDosMonitores,
    private val aoAtualizar: (TocandoNoCarPlay) -> Unit,
    /** O escopo das corrotinas do monitor ainda vale? Se foi cancelado, o vigia se desliga sozinho. */
    private val escopoAtivo: () -> Boolean = { true },
) {

    companion object {
        /** Tempo entre descer e soltar uma tecla. */
        const val SOLTAR_DEPOIS_DE_MS = 90L
        const val INTERVALO_DO_VIGIA_MS = 5_000L

        const val TECLA_TOCAR = 1
        const val TECLA_PAUSAR = 2
        const val TECLA_PROXIMA = 4
        const val TECLA_ANTERIOR = 8
        const val ACAO_DESCER = 0
        const val ACAO_SOLTAR = 1
    }

    private val trava = Any()

    // ── estado (sob a trava) ──
    private var iniciado = false
    private var servico: ServicoDoCarPlay? = null
    private var ligacaoPedida = false
    private var assinado = false
    private var ultimaTentativaDeLigacao = 0L
    private var ultimaTentativaDeAssinatura = 0L
    private var ultimoQuadro = 0L
    private var ultimoQuadroValido = 0L
    private var jaLimpei = false
    private var limpezaAgendada = false
    private var vigiaAtivo = false
    private var numeroDoVigia = 0
    private var passoAgendadoDoVigia: Runnable? = null

    /** Sobe a cada quadro válido, a cada limpeza e em `parar()`: invalida o que estiver em andamento. */
    private val geracao = AtomicInteger(0)

    /** Sobe a cada `parar()`: o `limpar` já postado para a principal não sai depois de parar. */
    private val epoca = AtomicInteger(0)

    private val tarefaDeLimpeza = Runnable { aoDispararALimpeza() }

    // ───────── B1 / B15: iniciar e parar ─────────

    /** B1. Idempotente: pode vir antes de o serviço existir. */
    fun iniciar() {
        var criarVigia = false
        var numero = 0
        synchronized(trava) {
            iniciado = true
            if (!vigiaAtivo) {
                vigiaAtivo = true
                criarVigia = true
                numero = ++numeroDoVigia
            }
        }
        if (criarVigia) agendarPassoDoVigia(numero, 0L)
        pedirLigacao()
        garantirAssinatura()
    }

    /** B15. Depois disto nenhum aviso sai; não emite `limpar`. Pode iniciar de novo. */
    fun parar() {
        val parado: ServicoDoCarPlay?
        val desfazer: Boolean
        synchronized(trava) {
            iniciado = false
            vigiaAtivo = false
            numeroDoVigia++
            passoAgendadoDoVigia?.let { agenda.cancelar(it) }
            passoAgendadoDoVigia = null
            cancelarLimpezaAgendada()
            geracao.incrementAndGet()
            epoca.incrementAndGet()
            parado = servico
            desfazer = ligacaoPedida
        }
        if (parado != null) {
            tentar(registro, "cancelar a assinatura ao parar") { parado.pararAvisos() }
        }
        if (desfazer) desfazerLigacao()
        synchronized(trava) {
            servico = null
            ligacaoPedida = false
            assinado = false
            ultimaTentativaDeLigacao = 0L
            ultimaTentativaDeAssinatura = 0L
            ultimoQuadro = 0L
            ultimoQuadroValido = 0L
            jaLimpei = false
        }
    }

    // ───────── B2: vigia ─────────

    private fun agendarPassoDoVigia(numero: Int, atrasoMs: Long) {
        val passo = object : Runnable {
            override fun run() {
                passoDoVigia(numero, this)
            }
        }
        synchronized(trava) {
            if (!vigiaAtivo || numero != numeroDoVigia) return
            passoAgendadoDoVigia = passo
        }
        if (atrasoMs <= 0L) agenda.postar(passo) else agenda.postarComAtraso(atrasoMs, passo)
    }

    private fun passoDoVigia(numero: Int, passo: Runnable) {
        synchronized(trava) {
            if (!vigiaAtivo || numero != numeroDoVigia) return
            if (passoAgendadoDoVigia === passo) passoAgendadoDoVigia = null
        }
        if (!escopoAtivo()) {
            registro.aviso("escopo cancelado: monitor do CarPlay parando")
            parar()
            return
        }
        tentar(registro, "passo do vigia") {
            pedirLigacao()
            garantirAssinatura()
        }
        agendarPassoDoVigia(numero, INTERVALO_DO_VIGIA_MS)
    }

    // ───────── B3: ligar ao serviço ─────────

    /** Devolve se há binder vivo. */
    fun pedirLigacao(): Boolean {
        val agora = agenda.agoraMs()
        var desfazer = false
        synchronized(trava) {
            if (!iniciado) return false
            val vivo = servico?.vivo == true
            if (!RegrasDoMonitorDoCarPlay.devePedirLigacao(agora, ultimaTentativaDeLigacao, vivo)) return vivo
            if (ligacaoPedida) {
                desfazer = true
                ligacaoPedida = false
                servico = null
                assinado = false
            }
            ultimaTentativaDeLigacao = agora
        }
        if (desfazer) desfazerLigacao()
        val pedida = tentar(registro, "pedir a ligação ao serviço") { ligacao.ligar() } ?: false
        var largar = false
        synchronized(trava) {
            if (iniciado) ligacaoPedida = pedida else largar = pedida
        }
        if (largar) desfazerLigacao() // parou no meio do pedido
        return false
    }

    private fun desfazerLigacao() {
        tentar(registro, "desfazer a ligação") { ligacao.desfazer() }
    }

    // ───────── B4 / B5: conectou, desconectou ─────────

    /** B4. Um binder nulo é tratado como ligação morta (B5). */
    fun conectou(novo: ServicoDoCarPlay?) {
        if (novo == null) {
            ligacaoMorreu()
            return
        }
        synchronized(trava) {
            if (!iniciado) return
            servico = novo
            assinado = false
        }
        assinar()
    }

    /** B5: a central reiniciou ou morreu. A ligação continua pedida; o sistema chama `conectou` de novo. */
    fun desconectou() {
        synchronized(trava) {
            if (!iniciado) return
            servico = null
            assinado = false
        }
        limpar("serviço desconectou")
    }

    /** B5 (exigido, DB3): binding morto ou binder nulo = desconectou + desfaz a ligação, para o vigia pedir outra. */
    fun ligacaoMorreu() {
        synchronized(trava) {
            if (!iniciado) return
            servico = null
            assinado = false
            ligacaoPedida = false
        }
        desfazerLigacao()
        limpar("ligação morta")
    }

    // ───────── B6 / B7 / B8: assinatura ─────────

    /** B6. */
    fun garantirAssinatura() {
        val renovar: Boolean
        synchronized(trava) {
            val atual = servico
            val vivo = atual?.vivo == true
            if (!vivo) {
                assinado = false
                return
            }
            renovar = RegrasDoMonitorDoCarPlay.deveRenovarAssinatura(
                agoraMs = agenda.agoraMs(),
                ultimaTentativaMs = ultimaTentativaDeAssinatura,
                ultimoQuadroMs = ultimoQuadro,
                assinado = assinado,
                binderVivo = true,
            )
        }
        if (renovar) {
            // Cancelar e assinar de novo é o que faz a central empurrar o estado atual de novo.
            cancelarAssinatura()
            assinar()
        }
    }

    private fun assinar() {
        val alvo: ServicoDoCarPlay
        synchronized(trava) {
            alvo = servico ?: return
            ultimaTentativaDeAssinatura = agenda.agoraMs()
        }
        val saiu = tentar(registro, "assinar o tocando agora") { alvo.iniciarAvisos() } ?: false
        synchronized(trava) {
            if (servico === alvo) assinado = saiu
        }
    }

    private fun cancelarAssinatura() {
        val alvo = synchronized(trava) { servico } ?: return
        val saiu = tentar(registro, "cancelar a assinatura") { alvo.pararAvisos() } ?: false
        if (saiu) {
            synchronized(trava) {
                if (servico === alvo) assinado = false
            }
        }
    }

    // ───────── B10 a B14: quadros ─────────

    /**
     * B10. Chamado na thread do binder já com o quadro lido ([quadro] nulo = presença 0, "sem objeto").
     * Um quadro, mesmo em branco, prova que a central nos tem.
     */
    fun receberQuadro(quadro: QuadroDoCarPlay?) {
        val agora = agenda.agoraMs()
        val valido = quadro != null && quadro.temConteudo()
        var minhaGeracao = 0
        synchronized(trava) {
            if (!iniciado) return
            ultimoQuadro = agora
            assinado = true
            if (valido) {
                // B11
                jaLimpei = false
                ultimoQuadroValido = agora
                cancelarLimpezaAgendada()
                minhaGeracao = geracao.incrementAndGet()
            }
        }
        if (valido) {
            val g = minhaGeracao
            try {
                emSegundoPlano { publicar(quadro, g) }
            } catch (e: Exception) {
                registro.erro("agendar a publicação", e)
            }
        } else {
            agendarLimpeza() // B12
        }
    }

    /** B13, em segundo plano. Só o quadro mais recente é entregue. */
    private fun publicar(quadro: QuadroDoCarPlay, g: Int) {
        if (geracao.get() != g) return // nem vale decodificar
        val capa = tentar(registro, "decodificar a capa") { capaDoQuadro(quadro) }
        val titulo = quadro.titulo?.takeIf { it.isNotEmpty() }
        val artista = quadro.artista?.takeIf { it.isNotEmpty() }
        val tocando = quadro.estado == QuadroDoCarPlay.ESTADO_TOCANDO
        if (geracao.get() != g) return // chegou quadro mais novo ou limpou
        if (titulo == null && artista == null && capa == null && !tocando) {
            agendarLimpeza() // na prática um quadro em branco
            return
        }
        val aviso = TocandoNoCarPlay(
            titulo = titulo,
            artista = artista,
            capa = capa,
            caminhoDaCapa = quadro.caminhoDaCapa?.takeIf { it.isNotEmpty() },
            duracaoMs = if (quadro.duracaoMs > 0L) quadro.duracaoMs else 0L,
            decorridoMs = if (quadro.posicaoMs >= 0L) quadro.posicaoMs else 0L,
            tocando = tocando,
            limpar = false,
        )
        naPrincipal {
            if (geracao.get() == g) aoAtualizar(aviso)
        }
    }

    /** B14. Vários brancos seguidos não adiam a limpeza: a espera conta do último quadro VÁLIDO. */
    private fun agendarLimpeza() {
        val limparJa: Boolean
        synchronized(trava) {
            if (!iniciado) return
            val espera = RegrasDoMonitorDoCarPlay.esperaAteLimpar(agenda.agoraMs(), ultimoQuadroValido)
            if (espera <= 0L) {
                limparJa = true
            } else {
                limparJa = false
                cancelarLimpezaAgendada()
                limpezaAgendada = true
                agenda.postarComAtraso(espera, tarefaDeLimpeza)
            }
        }
        if (limparJa) limpar("quadro em branco")
    }

    private fun aoDispararALimpeza() {
        val limparJa: Boolean
        synchronized(trava) {
            limpezaAgendada = false
            if (!iniciado) return
            val espera = RegrasDoMonitorDoCarPlay.esperaAteLimpar(agenda.agoraMs(), ultimoQuadroValido)
            if (espera <= 0L) {
                limparJa = true
            } else {
                // O relógio ainda não chegou: espera o resto (só limpa quando a espera for <= 0).
                limparJa = false
                limpezaAgendada = true
                agenda.postarComAtraso(espera, tarefaDeLimpeza)
            }
        }
        if (limparJa) limpar("quadro em branco")
    }

    /** Sob a trava. */
    private fun cancelarLimpezaAgendada() {
        if (limpezaAgendada) {
            agenda.cancelar(tarefaDeLimpeza)
            limpezaAgendada = false
        }
    }

    /** B9: só uma vez por "sessão vazia". [motivo] vai só para o registro. */
    private fun limpar(motivo: String) {
        val minhaEpoca: Int
        synchronized(trava) {
            if (!iniciado || jaLimpei) return
            jaLimpei = true
            geracao.incrementAndGet()
            ultimoQuadroValido = 0L
            cancelarLimpezaAgendada()
            minhaEpoca = epoca.get()
        }
        registro.aviso("limpando o CarPlay: $motivo")
        naPrincipal {
            if (epoca.get() == minhaEpoca) aoAtualizar(TocandoNoCarPlay(limpar = true))
        }
    }

    private fun naPrincipal(tarefa: () -> Unit) {
        if (agenda.estaNaPrincipal()) tarefa() else agenda.postar(Runnable(tarefa))
    }

    // ───────── B16: comandos ─────────

    /** [posicaoMs] negativa vira 0. Verdadeiro = pedido enviado. */
    fun buscar(posicaoMs: Long): Boolean {
        val alvo = synchronized(trava) { servico } ?: return false
        return tentar(registro, "buscar") { alvo.definirPosicao(maxOf(0L, posicaoMs)) } ?: false
    }

    fun proxima(): Boolean = apertar(TECLA_PROXIMA)

    fun anterior(): Boolean = apertar(TECLA_ANTERIOR)

    fun tocarOuPausar(estaTocando: Boolean): Boolean = apertar(if (estaTocando) TECLA_PAUSAR else TECLA_TOCAR)

    /**
     * B16a: desce a tecla; enviou → verdadeiro na hora e, 90 ms depois, solta (mesmo que o escopo seja
     * cancelado ou o monitor pare nesse meio tempo: tecla presa é pior). Falhou → falso e não solta.
     */
    private fun apertar(tecla: Int): Boolean {
        val alvo = synchronized(trava) { servico } ?: return false
        val desceu = tentar(registro, "descer a tecla $tecla") { alvo.enviarTecla(tecla, ACAO_DESCER) } ?: false
        if (!desceu) return false
        try {
            agenda.postarComAtraso(
                SOLTAR_DEPOIS_DE_MS,
                Runnable { tentar(registro, "soltar a tecla $tecla") { alvo.enviarTecla(tecla, ACAO_SOLTAR) } },
            )
        } catch (e: Exception) {
            registro.erro("agendar o soltar da tecla $tecla", e)
            tentar(registro, "soltar a tecla $tecla") { alvo.enviarTecla(tecla, ACAO_SOLTAR) }
        }
        return true
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
