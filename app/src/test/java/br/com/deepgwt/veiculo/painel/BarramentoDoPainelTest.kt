// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.painel

import br.com.deepgwt.painel.TeclaDoVolante
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** §C de `motor-painel-e-volante.md`: o barramento de eventos do painel. */
class BarramentoDoPainelTest {

    private val relogio = RelogioFalso()
    private val registro = RegistroFalso()
    private var linha: LinhaDoPainel? = null
    private val barramento = BarramentoDoPainel(registro, relogio) { linha }
    private val ordem = CopyOnWriteArrayList<String>()

    private fun ouvinte(nome: String) = EventosDoPainel.Ouvinte { ordem += "$nome:$it" }

    @Test
    fun c2_ordem_de_registro_na_thread_de_quem_publicou() {
        val threads = CopyOnWriteArrayList<Thread>()
        barramento.observar(ouvinte("a"))
        barramento.observar(ouvinte("b"))
        barramento.observar { threads += Thread.currentThread() }
        barramento.publicar(EventoDoPainel.GeometriaMudou)
        assertEquals(listOf("a:GeometriaMudou", "b:GeometriaMudou"), ordem)
        assertEquals(listOf(Thread.currentThread()), threads)
    }

    @Test
    fun c1_repetir_nao_duplica_e_avisa() {
        val a = ouvinte("a")
        barramento.observar(a)
        barramento.observar(a)
        assertEquals(1, barramento.quantos)
        assertEquals(1, registro.avisos.size)
        barramento.publicar(EventoDoPainel.MaosLivresMudou(true))
        assertEquals(listOf("a:MaosLivresMudou(ativo=true)"), ordem)
    }

    @Test
    fun c1_parar_de_observar() {
        val a = ouvinte("a")
        val b = ouvinte("b")
        barramento.observar(a)
        barramento.observar(b)
        barramento.pararDeObservar(a)
        barramento.pararDeObservar(a) // já saiu: aviso, nada
        assertEquals(1, registro.avisos.size)
        barramento.publicar(EventoDoPainel.DispensarAviso)
        assertEquals(listOf("b:DispensarAviso"), ordem)
    }

    @Test
    fun c2_foto_da_lista() {
        val tardio = ouvinte("tardio")
        barramento.observar {
            ordem += "primeiro:$it"
            barramento.observar(tardio)
        }
        barramento.publicar(EventoDoPainel.ArNoMaximoMudou(true))
        assertEquals(listOf("primeiro:ArNoMaximoMudou(ativo=true)"), ordem)
        barramento.publicar(EventoDoPainel.ArNoMaximoMudou(false))
        assertEquals(
            listOf(
                "primeiro:ArNoMaximoMudou(ativo=true)",
                "primeiro:ArNoMaximoMudou(ativo=false)",
                "tardio:ArNoMaximoMudou(ativo=false)",
            ),
            ordem,
        )
    }

    @Test
    fun c2_excecao_isolada() {
        barramento.observar { throw IllegalStateException("quebrado") }
        barramento.observar(ouvinte("b"))
        barramento.publicar(EventoDoPainel.AppNoDisplay(3, true))
        assertEquals(listOf("b:AppNoDisplay(display=3, ativo=true)"), ordem)
        assertEquals(1, registro.erros.size)
    }

    @Test
    fun c3_fora_da_thread_na_linha_do_painel() {
        val falsa = LinhaFalsa(relogio)
        linha = falsa
        barramento.observar(ouvinte("a"))
        barramento.publicarForaDaThread(EventoDoPainel.CartaoMudou(1))
        assertTrue(ordem.isEmpty())
        falsa.rodarPendentes()
        assertEquals(listOf("a:CartaoMudou(cartao=1)"), ordem)
    }

    @Test
    fun c3_sem_linha_entrega_na_hora() {
        barramento.observar(ouvinte("a"))
        barramento.publicarForaDaThread(EventoDoPainel.CartaoMudou(3))
        assertEquals(listOf("a:CartaoMudou(cartao=3)"), ordem)
    }

    @Test
    fun c3_linha_parada_entrega_na_hora() {
        val falsa = LinhaFalsa(relogio)
        falsa.encerrar()
        linha = falsa
        barramento.observar(ouvinte("a"))
        barramento.publicarForaDaThread(EventoDoPainel.CartaoMudou(0))
        assertEquals(listOf("a:CartaoMudou(cartao=0)"), ordem)
    }

    @Test
    fun c3_na_thread_da_linha_de_verdade() {
        val deVerdade = LinhaEmThread("DeepGWT-Painel-teste", registro)
        try {
            linha = deVerdade
            val nome = CopyOnWriteArrayList<String>()
            val chegou = CountDownLatch(2)
            barramento.observar {
                nome += Thread.currentThread().name
                chegou.countDown()
            }
            barramento.publicarForaDaThread(EventoDoPainel.CartaoMudou(1))
            barramento.publicarForaDaThread(EventoDoPainel.CartaoMudou(3))
            assertTrue(chegou.await(5, TimeUnit.SECONDS))
            assertEquals(listOf("DeepGWT-Painel-teste", "DeepGWT-Painel-teste"), nome)
        } finally {
            deVerdade.encerrar()
        }
    }

    @Test
    fun c3_repasse_lento_vira_aviso() {
        val falsa = LinhaFalsa(relogio)
        linha = falsa
        var custo = 50L
        barramento.observar { relogio.andar(custo) }
        barramento.publicarForaDaThread(EventoDoPainel.CartaoMudou(1))
        falsa.rodarPendentes()
        assertTrue(registro.avisos.isEmpty())
        custo = 51L
        barramento.publicarForaDaThread(EventoDoPainel.CartaoMudou(3))
        falsa.rodarPendentes()
        assertEquals(1, registro.avisos.size)
        assertTrue(registro.avisos.single().contains("CartaoMudou(cartao=3)"))
    }

    @Test
    fun dp4_teclas_nao_entram_no_log() {
        barramento.publicar(EventoDoPainel.TeclaRecebida(TeclaDoVolante.ENTER, 1028, 1))
        barramento.publicar(EventoDoPainel.TeclaParaOTema(TeclaDoVolante.ENTER))
        assertTrue(registro.infos.isEmpty())
        barramento.publicar(EventoDoPainel.CartaoMudou(1))
        barramento.publicar(EventoDoPainel.Livre("tela", arrayOf<Any?>("x", 2)))
        barramento.publicar(EventoDoPainel.PrepararVaoNoDisplay3(intArrayOf(1, 2, 3, 4)))
        assertEquals(
            listOf("barramento: CartaoMudou(cartao=1)", "barramento: Livre(tela, [x, 2])", "barramento: PrepararVaoNoDisplay3([1, 2, 3, 4])"),
            registro.infos,
        )
    }

    @Test
    fun c5_quem_chega_depois_nao_recebe_os_anteriores() {
        barramento.publicar(EventoDoPainel.CartaoMudou(1))
        barramento.observar(ouvinte("a"))
        assertTrue(ordem.isEmpty())
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
