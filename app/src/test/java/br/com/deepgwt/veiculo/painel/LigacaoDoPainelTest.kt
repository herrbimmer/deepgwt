// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.painel

import br.com.deepgwt.nucleo.Preferencias
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** Conector falso: guarda os avisos de cada ligação para o teste disparar. */
internal class ConectorFalso<S>(private val eventos: MutableList<String>) : ConectorDeServico<S> {
    var aceita = true
    val conexoes = CopyOnWriteArrayList<Pair<(S) -> Unit, () -> Unit>>()
    private var ligado = false

    override fun ligar(aoConectar: (S) -> Unit, aoDesconectar: () -> Unit): Boolean {
        eventos += "bind"
        conexoes += aoConectar to aoDesconectar
        ligado = true
        return aceita
    }

    /** Como o do app: sem ligação guardada, não faz nada. */
    override fun desligar() {
        if (!ligado) return
        ligado = false
        eventos += "unbind"
    }

    fun conectar(servico: S, qual: Int = conexoes.size - 1) = conexoes[qual].first(servico)

    fun desconectar(qual: Int = conexoes.size - 1) = conexoes[qual].second()
}

/** A.1, A.8 e A.10 de `motor-painel-e-volante.md`. */
class LigacaoDoPainelTest {

    private class RemotoFalso(private val nome: String, private val eventos: MutableList<String>) : ServicoRemotoDoPainel {
        var falharRegistro = false
        var falharDesregistro = false

        override fun enviar(id: Int, valor: Int) {
            eventos += "$nome:$id=$valor"
        }

        override fun registrarRetorno() {
            eventos += "$nome:registrar"
            if (falharRegistro) throw IllegalStateException("recusou")
        }

        override fun desregistrarRetorno() {
            eventos += "$nome:desregistrar"
            if (falharDesregistro) throw IllegalStateException("recusou")
        }
    }

    private val b = BancadaDoPainel()
    private val eventos = b.chamadas
    private val conector = ConectorFalso<ServicoRemotoDoPainel>(eventos)
    private val linhas = CopyOnWriteArrayList<LinhaFalsa>()
    private val ligacao = LigacaoDoPainel(
        nucleo = b.nucleo,
        conector = conector,
        novaLinha = { LinhaFalsa(b.relogio, "linha${linhas.size + 1}").also { linhas += it } },
        preferencias = b.preferencias,
        registro = b.registro,
    )

    @Test
    fun a1_conectou_registra_o_retorno_e_inicia_o_pulso() {
        b.preferencias[Preferencias.ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION] = true
        ligacao.ligar()
        assertEquals(listOf("bind"), eventos)
        assertNotNull(b.nucleo.linha)
        val remoto = RemotoFalso("p", eventos)
        conector.conectar(remoto)
        assertTrue(ligacao.conectado)
        assertEquals("o registro sai na linha do painel", listOf("bind"), eventos)
        linhas.single().rodarPendentes()
        assertEquals(listOf("bind", "p:registrar", "p:75=1"), eventos)
        linhas.single().avancar(1_000)
        assertEquals(listOf("bind", "p:registrar", "p:75=1", "p:134=0"), eventos)
    }

    @Test
    fun a1_sem_integracao_nao_ha_pulso() {
        ligacao.ligar()
        conector.conectar(RemotoFalso("p", eventos))
        linhas.single().avancar(3_000)
        assertEquals(listOf("bind", "p:registrar"), eventos)
        assertFalse(b.nucleo.pulsoRodando)
    }

    @Test
    fun a1_falha_no_registro_so_registra() {
        b.preferencias[Preferencias.ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION] = true
        ligacao.ligar()
        val remoto = RemotoFalso("p", eventos).apply { falharRegistro = true }
        conector.conectar(remoto)
        linhas.single().rodarPendentes()
        assertEquals(listOf("bind", "p:registrar", "p:75=1"), eventos)
        assertEquals(1, b.registro.erros.size)
    }

    @Test
    fun a8_desligar_desregistra_desfaz_e_esquece() {
        b.preferencias[Preferencias.ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION] = true
        ligacao.ligar()
        conector.conectar(RemotoFalso("p", eventos))
        linhas.single().avancar(1_000)
        ligacao.desligar()
        assertEquals(listOf("bind", "p:registrar", "p:75=1", "p:134=0", "p:desregistrar", "unbind"), eventos)
        assertFalse(ligacao.conectado)
        assertNull(b.nucleo.canal)
        assertNull(b.nucleo.linha)
        assertFalse(b.nucleo.pulsoRodando)
        assertTrue(linhas.single().encerrada)
    }

    @Test
    fun a8_falha_ao_desregistrar_segue() {
        ligacao.ligar()
        conector.conectar(RemotoFalso("p", eventos).apply { falharDesregistro = true })
        linhas.single().rodarPendentes()
        ligacao.desligar()
        assertEquals(listOf("bind", "p:registrar", "p:desregistrar", "unbind"), eventos)
        assertEquals(1, b.registro.erros.size)
    }

    @Test
    fun a1_religar_desfaz_a_anterior_antes() {
        ligacao.ligar()
        conector.conectar(RemotoFalso("velho", eventos), qual = 0)
        linhas[0].rodarPendentes()
        ligacao.ligar()
        assertEquals(listOf("bind", "velho:registrar", "velho:desregistrar", "unbind", "bind"), eventos)
        assertTrue(linhas[0].encerrada)
        // Aviso atrasado da ligação antiga: ignorado.
        conector.conectar(RemotoFalso("atrasado", eventos), qual = 0)
        assertFalse(ligacao.conectado)
        conector.conectar(RemotoFalso("novo", eventos), qual = 1)
        linhas[1].rodarPendentes()
        assertEquals(listOf("bind", "velho:registrar", "velho:desregistrar", "unbind", "bind", "novo:registrar"), eventos)
    }

    @Test
    fun a1_desconectou_esquece_a_interface() {
        b.preferencias[Preferencias.ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION] = true
        ligacao.ligar()
        conector.conectar(RemotoFalso("p", eventos))
        linhas.single().rodarPendentes()
        conector.desconectar()
        assertFalse(ligacao.conectado)
        linhas.single().avancar(1_000)
        assertEquals("o pulso é descartado sem interface", listOf("bind", "p:registrar", "p:75=1"), eventos)
        // Desconexão antiga depois de religar não apaga a nova.
        ligacao.ligar()
        conector.conectar(RemotoFalso("q", eventos), qual = 1)
        conector.desconectar(qual = 0)
        assertTrue(ligacao.conectado)
    }

    @Test
    fun a10_simulador_falha_em_silencio() {
        conector.aceita = false
        ligacao.ligar()
        assertFalse(ligacao.conectado)
        assertEquals(0, b.nucleo.cartaoAtual)
        assertEquals(1, b.registro.avisos.size)
        assertTrue(b.registro.erros.isEmpty())
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
