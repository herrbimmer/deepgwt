// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.volante

import br.com.deepgwt.veiculo.painel.ConectorFalso
import br.com.deepgwt.veiculo.painel.RegistroFalso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** B.1, B.8 e B.10 de `motor-painel-e-volante.md`. */
class LigacaoDoVolanteTest {

    private class TeclasFalsas(private val nome: String, private val eventos: MutableList<String>) : ServicoRemotoDeTeclas {
        var falhasNoRegistro = 0
        var falharDesregistro = false

        override fun registrarOuvinte() {
            eventos += "$nome:registrar"
            if (falhasNoRegistro > 0) {
                falhasNoRegistro--
                throw IllegalStateException("recusou")
            }
        }

        override fun desregistrarOuvinte() {
            eventos += "$nome:desregistrar"
            if (falharDesregistro) throw IllegalStateException("recusou")
        }
    }

    private val eventos = CopyOnWriteArrayList<String>()
    private val registro = RegistroFalso()
    private val conector = ConectorFalso<ServicoRemotoDeTeclas>(eventos)

    /** A "thread própria": guardada até o teste rodar. */
    private val outraThread = ArrayList<() -> Unit>()
    private val ligacao = LigacaoDoVolante(conector, { outraThread += it }, registro)

    private fun rodarOutraThread() {
        val tarefas = outraThread.toList()
        outraThread.clear()
        tarefas.forEach { it() }
    }

    @Test
    fun b1_conectou_registra_numa_thread_propria() {
        ligacao.ligar()
        conector.conectar(TeclasFalsas("t", eventos))
        assertTrue(ligacao.conectado)
        assertEquals(listOf("bind"), eventos)
        rodarOutraThread()
        assertEquals(listOf("bind", "t:registrar"), eventos)
    }

    @Test
    fun b1_falhou_tenta_mais_uma_vez() {
        ligacao.ligar()
        conector.conectar(TeclasFalsas("t", eventos).apply { falhasNoRegistro = 1 })
        rodarOutraThread()
        assertEquals(listOf("bind", "t:registrar", "t:registrar"), eventos)
        assertEquals(1, registro.erros.size)
    }

    @Test
    fun b1_falhou_duas_vezes_desiste_registrando() {
        ligacao.ligar()
        conector.conectar(TeclasFalsas("t", eventos).apply { falhasNoRegistro = 5 })
        rodarOutraThread()
        assertEquals(listOf("bind", "t:registrar", "t:registrar"), eventos)
        assertEquals(2, registro.erros.size)
    }

    @Test
    fun b8_desligar_desregistra_e_depois_desfaz() {
        ligacao.ligar()
        conector.conectar(TeclasFalsas("t", eventos))
        rodarOutraThread()
        ligacao.desligar()
        assertEquals(listOf("bind", "t:registrar", "t:desregistrar", "unbind"), eventos)
        assertFalse(ligacao.conectado)
    }

    @Test
    fun b8_falha_ao_desregistrar_segue() {
        ligacao.ligar()
        conector.conectar(TeclasFalsas("t", eventos).apply { falharDesregistro = true })
        rodarOutraThread()
        ligacao.desligar()
        assertEquals(listOf("bind", "t:registrar", "t:desregistrar", "unbind"), eventos)
        assertEquals(1, registro.erros.size)
    }

    @Test
    fun b1_religar_desregistra_o_anterior_antes_de_qualquer_coisa() {
        ligacao.ligar()
        conector.conectar(TeclasFalsas("velho", eventos), qual = 0)
        rodarOutraThread()
        ligacao.ligar()
        assertEquals(listOf("bind", "velho:registrar", "velho:desregistrar", "unbind", "bind"), eventos)
        conector.conectar(TeclasFalsas("novo", eventos), qual = 1)
        rodarOutraThread()
        assertEquals(listOf("bind", "velho:registrar", "velho:desregistrar", "unbind", "bind", "novo:registrar"), eventos)
    }

    @Test
    fun registro_atrasado_de_uma_ligacao_desfeita_nao_acontece() {
        ligacao.ligar()
        conector.conectar(TeclasFalsas("t", eventos))
        // Desligou antes de a thread própria rodar: nada de registro solto no serviço.
        ligacao.desligar()
        rodarOutraThread()
        assertEquals(listOf("bind", "t:desregistrar", "unbind"), eventos)
        // Aviso de conexão atrasado de uma ligação antiga: ignorado.
        conector.conectar(TeclasFalsas("atrasado", eventos), qual = 0)
        rodarOutraThread()
        assertFalse(ligacao.conectado)
        assertEquals(listOf("bind", "t:desregistrar", "unbind"), eventos)
    }

    @Test
    fun desconectou_esquece_a_interface() {
        ligacao.ligar()
        conector.conectar(TeclasFalsas("t", eventos))
        rodarOutraThread()
        conector.desconectar()
        assertFalse(ligacao.conectado)
        ligacao.desligar()
        assertEquals("sem interface não há o que desregistrar", listOf("bind", "t:registrar", "unbind"), eventos)
    }

    @Test
    fun b10_simulador_nao_conecta() {
        conector.aceita = false
        ligacao.ligar()
        assertFalse(ligacao.conectado)
        assertEquals(1, registro.avisos.size)
        assertTrue(registro.erros.isEmpty())
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
