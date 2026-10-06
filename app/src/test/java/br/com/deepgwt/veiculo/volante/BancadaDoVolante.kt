// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.volante

import br.com.deepgwt.nucleo.Preferencias
import br.com.deepgwt.veiculo.painel.BancadaDoPainel
import br.com.deepgwt.veiculo.painel.EventoDoPainel
import java.util.concurrent.CopyOnWriteArrayList

/** Saída das mãos livres que anota tudo. */
internal class SaidaFalsa(private val eventos: MutableList<String>) : SaidaDasMaosLivres {
    override fun mudou(ativo: Boolean) {
        eventos += "maosLivres:$ativo"
    }

    override fun teclaDeDirecao(codigoAndroid: Int) {
        eventos += "dpad:$codigoAndroid"
    }
}

/**
 * O núcleo do volante montado sobre a bancada do painel (o estado do cartão é o de verdade). O evento
 * de tecla é um texto: na JVM não há `KeyEvent`.
 */
internal class BancadaDoVolante {
    val painel = BancadaDoPainel()
    val relogio get() = painel.relogio
    val registro get() = painel.registro
    val preferencias get() = painel.preferencias

    /** Chamadas aos elos, à projeção e à saída, na ordem. */
    val chamadas: MutableList<String> get() = painel.chamadas

    /** Quem consome: nome do elo → consome? */
    val consome = HashMap<String, Boolean>()
    val lanca = HashSet<String>()

    /** Tempo gasto por elo (o relógio anda). */
    val custo = HashMap<String, Long>()

    private fun elo(nome: String) = EloDaCadeia<String> { codigo, acao, evento ->
        chamadas += "$nome:$codigo/$acao/$evento"
        custo[nome]?.let { relogio.andar(it) }
        if (nome in lanca) throw IllegalStateException("$nome quebrado")
        consome[nome] ?: false
    }

    val nucleo = NucleoDoVolante<String>(
        relogio = painel.relogio,
        registro = painel.registro,
        diario = { e, d -> painel.diario += e to d },
        preferencias = painel.preferencias,
        publicador = painel.barramento,
        cartao = painel.nucleo,
    ).also {
        it.elos = ElosDoVolante(elo("coringas"), elo("hud"), elo("circulo"))
        it.maosLivres = SaidaFalsa(painel.chamadas)
        it.projecao = painel.projecao
        it.menuAntigo = painel.menuAntigo
    }

    /** Eventos do barramento. */
    val recebidos: List<EventoDoPainel> get() = painel.recebidos

    fun comMenu(): BancadaDoVolante = apply { preferencias[Preferencias.ENABLE_CUSTOM_MENU] = true }

    fun tecla(codigo: Int, acao: Int, doServico: Boolean = true): Boolean = nucleo.tratar(codigo, acao, "ev$codigo", doServico)

    fun soltou(codigo: Int) = tecla(codigo, NucleoDoVolante.ACAO_SOLTOU)

    fun apertou(codigo: Int) = tecla(codigo, NucleoDoVolante.ACAO_APERTOU)
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
