// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.sistema

import br.com.deepgwt.veiculo.motor.RegistroDoMotor

/**
 * Partida e encerramento do conjunto em volta do núcleo (motor-sistema.md §G). Sem Android: o
 * contexto é um tipo qualquer [C] e cada passo de fora chega por [PortasDoCiclo].
 *
 * Só o núcleo decide a partida (G.2.5); os outros passos são isolados (uma falha é registrada e a
 * partida segue).
 */
internal class CicloDeVida<C>(
    private val portas: PortasDoCiclo<C>,
    private val relogio: () -> Long,
    private val registro: RegistroDoMotor,
) {

    private val trava = Any()

    /** G.5: verdadeiro depois da primeira partida completa; não volta a falso. */
    @Volatile var pronto: Boolean = false
        private set

    @Volatile var instanteDoBoot: Long = 0L
        private set

    @Volatile var inicioDaPartida: Long = 0L
        private set

    @Volatile var fimDaPartida: Long = 0L
        private set

    /** G.6: só grava a primeira vez; repassa ao núcleo. */
    fun marcarBoot(uptimeMs: Long) {
        synchronized(trava) {
            if (instanteDoBoot == 0L) instanteDoBoot = uptimeMs
        }
        passo("marcar o boot no núcleo") { portas.marcarBootNoNucleo(uptimeMs) }
    }

    /** G.2: bloqueante, uma por vez. */
    fun partir(contexto: C): Boolean = synchronized(trava) {
        if (instanteDoBoot == 0L) marcarBoot(relogio())
        desfazer(contexto)
        inicioDaPartida = relogio()
        passo("gancho do carro") { portas.ligarGanchoDoCarro() }
        val nucleoSubiu = try {
            portas.partirNucleo(contexto)
        } catch (e: Exception) {
            registro.erro("partida: o núcleo lançou exceção", e)
            false
        }
        if (!nucleoSubiu) {
            registro.erro("partida: o núcleo não subiu; painel, teclas e automações ficam desligados")
            return false
        }
        passo("painel e volante") { portas.ligarPainelEVolante(contexto) }
        passo("ajustes do Android") { portas.ajustesDoAndroidNaPartida(contexto) }
        passo("dados móveis") { portas.iniciarDadosMoveis() }
        passo("conectividade") { portas.prepararConectividade(contexto) }
        passo("automações de partida") { portas.automacoesDePartida(true) }
        pronto = true
        fimDaPartida = relogio()
        passo("estado do hotspot") { portas.semearHotspot() }
        passo("automações depois do pronto") { portas.automacoesDepoisDoPronto() }
        registro.info("partida completa em ${fimDaPartida - inicioDaPartida} ms")
        true
    }

    /** G.3: sem painel, teclas, receptores, acessibilidade nem dados móveis. */
    fun partirSimulado(contexto: C) {
        synchronized(trava) {
            inicioDaPartida = relogio()
            passo("gancho do carro") { portas.ligarGanchoDoCarro() }
            passo("núcleo simulado") { portas.partirNucleoSimulado(contexto) }
            pronto = true
            fimDaPartida = relogio()
            passo("automações de partida (simulador)") { portas.automacoesDePartida(false) }
        }
    }

    /** G.4: destruição do serviço principal. Cada passo isolado. */
    fun encerrar(contexto: C) {
        synchronized(trava) {
            passo("dados móveis") { portas.pararDadosMoveis() }
            passo("núcleo") { portas.encerrarNucleo() }
            passo("painel e volante") { portas.desligarPainelEVolante(contexto) }
            passo("automações") { portas.encerrarAutomacoes() }
        }
    }

    /** G.2.2: desfaz a partida anterior (o volante desregistra antes: senão teclas em dobro). */
    private fun desfazer(contexto: C) {
        passo("parar dados móveis") { portas.pararDadosMoveis() }
        passo("desligar painel e volante") { portas.desligarPainelEVolante(contexto) }
        passo("encerrar automações") { portas.encerrarAutomacoes() }
    }

    private inline fun passo(nome: String, bloco: () -> Unit) {
        try {
            bloco()
        } catch (e: Exception) {
            registro.erro("ciclo de vida: '$nome' falhou", e)
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
