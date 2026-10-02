package br.com.opengw.monitores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** B1 a B16 e as sequências B-S1 a B-S6, com relógio, principal, segundo plano e central falsos. */
class NucleoDoMonitorDoCarPlayTest {

    private class Banco(escopoAtivo: () -> Boolean = { true }) {
        val agenda = AgendaFalsa(10_000)
        val ligacao = LigacaoFalsa()
        val segundoPlano = SegundoPlanoFalso()
        val servico = ServicoFalso()
        val avisos: MutableList<TocandoNoCarPlay> = java.util.Collections.synchronizedList(ArrayList())
        val nucleo = NucleoDoMonitorDoCarPlay(
            agenda = agenda,
            ligacao = ligacao,
            capaDoQuadro = { null },
            emSegundoPlano = segundoPlano::postar,
            registro = RegistroMudo,
            aoAtualizar = { avisos += it },
            escopoAtivo = escopoAtivo,
        )

        /** Inicia, roda o primeiro passo do vigia e conecta o serviço falso. */
        fun iniciarEConectar() {
            nucleo.iniciar()
            agenda.rodarAgora()
            nucleo.conectou(servico)
        }

        fun valido(titulo: String = "Faixa", estado: Int = 1) = QuadroDoCarPlay(
            titulo, "Banda", 200_000, null, estado, 5_000, 0, 0, null,
        )

        fun branco() = QuadroDoCarPlay(null, null, -1, null, 0, -1, -1, -1, null)

        fun limpezas() = avisos.count { it.limpar }
    }

    // ───────── B1, B2, B3: iniciar, vigia, ligar ─────────

    @Test
    fun `iniciar pede a ligacao uma vez e e idempotente`() {
        val b = Banco()
        b.nucleo.iniciar()
        b.nucleo.iniciar()
        b.agenda.rodarAgora()
        assertEquals(1, b.ligacao.ligacoes)
        assertEquals(0, b.ligacao.desfeitas)
    }

    @Test
    fun `o vigia repete a cada 5 s mas a ligacao so se renova 5 s depois da anterior`() {
        val b = Banco()
        b.nucleo.iniciar()
        b.agenda.rodarAgora()
        b.agenda.avancarPara(14_999)
        assertEquals(1, b.ligacao.ligacoes)
        b.agenda.avancarPara(15_000)
        // sem binder: desfaz a ligação pedida e pede outra
        assertEquals(2, b.ligacao.ligacoes)
        assertEquals(1, b.ligacao.desfeitas)
        b.agenda.avancarPara(20_000)
        assertEquals(3, b.ligacao.ligacoes)
    }

    @Test
    fun `ligacao recusada nao e desfeita na proxima tentativa`() {
        val b = Banco()
        b.ligacao.resultado = false
        b.nucleo.iniciar()
        b.agenda.avancarPara(15_000)
        assertEquals(2, b.ligacao.ligacoes)
        assertEquals(0, b.ligacao.desfeitas)
    }

    @Test
    fun `com binder vivo o vigia nao pede ligacao`() {
        val b = Banco()
        b.iniciarEConectar()
        b.agenda.avancarPara(60_000)
        assertEquals(1, b.ligacao.ligacoes)
    }

    @Test
    fun `binder morto sem aviso do sistema faz o vigia desfazer e ligar de novo`() {
        val b = Banco()
        b.iniciarEConectar()
        b.servico.vivo = false
        b.agenda.avancarPara(15_000)
        assertEquals(2, b.ligacao.ligacoes)
        assertEquals(1, b.ligacao.desfeitas)
    }

    // ───────── B4, B6, B7, B8: assinatura ─────────

    @Test
    fun `conectou assina`() {
        val b = Banco()
        b.iniciarEConectar()
        assertEquals(listOf("assinar"), b.servico.transacoes)
    }

    @Test
    fun `um quadro prova a assinatura e a renovacao so vem com o quadro velho`() {
        val b = Banco()
        b.iniciarEConectar() // assina em 10 000
        b.agenda.avancarPara(12_000)
        b.nucleo.receberQuadro(b.valido())
        b.segundoPlano.rodarTodas()
        b.servico.transacoes.clear()
        b.agenda.avancarPara(30_000) // quadro de 18 s: ainda não
        assertTrue(b.servico.transacoes.isEmpty())
        b.agenda.avancarPara(32_000) // 20 s do último quadro, mas o vigia só olha a cada 5 s
        assertTrue(b.servico.transacoes.isEmpty())
        b.agenda.avancarPara(35_000)
        assertEquals(listOf("cancelar", "assinar"), b.servico.transacoes)
    }

    @Test
    fun `sem quadro nenhum a assinatura e renovada a cada 15 s (cancelar e assinar)`() {
        val b = Banco()
        b.iniciarEConectar() // assina em 10 000
        b.servico.transacoes.clear()
        b.agenda.avancarPara(24_999)
        assertTrue(b.servico.transacoes.isEmpty())
        b.agenda.avancarPara(25_000)
        assertEquals(listOf("cancelar", "assinar"), b.servico.transacoes)
    }

    @Test
    fun `assinatura que falhou e tentada de novo depois de 15 s`() {
        val b = Banco()
        b.servico.resultado = false
        b.iniciarEConectar()
        b.servico.transacoes.clear()
        b.agenda.avancarPara(25_000)
        assertEquals(listOf("cancelar", "assinar"), b.servico.transacoes)
    }

    // ───────── B5: desconectou ─────────

    @Test
    fun `B-S4 desconectar limpa uma vez, quadro valido entrega e desconectar limpa de novo`() {
        val b = Banco()
        b.iniciarEConectar()
        b.nucleo.desconectou()
        assertEquals(1, b.limpezas())
        b.nucleo.desconectou()
        assertEquals(1, b.limpezas())
        b.nucleo.receberQuadro(b.valido("Nova"))
        b.segundoPlano.rodarTodas()
        assertEquals("Nova", b.avisos.last().titulo)
        b.nucleo.desconectou()
        assertEquals(2, b.limpezas())
    }

    @Test
    fun `desconectou esquece o binder mas mantem a ligacao pedida`() {
        val b = Banco()
        b.iniciarEConectar()
        b.nucleo.desconectou()
        assertEquals(0, b.ligacao.desfeitas)
        b.servico.transacoes.clear()
        assertFalse(b.nucleo.proxima()) // sem binder
        assertTrue(b.servico.transacoes.isEmpty())
        // o sistema volta a conectar quando o serviço volta
        b.nucleo.conectou(b.servico)
        assertEquals(listOf("assinar"), b.servico.transacoes)
    }

    @Test
    fun `DB3 ligacao morta limpa e desfaz a ligacao para o vigia pedir outra`() {
        val b = Banco()
        b.iniciarEConectar()
        b.nucleo.ligacaoMorreu()
        assertEquals(1, b.limpezas())
        assertEquals(1, b.ligacao.desfeitas)
        // o vigia pede uma nova ligação (sem desfazer de novo, a pedida já foi desfeita)
        b.agenda.avancarPara(15_000)
        assertEquals(2, b.ligacao.ligacoes)
        assertEquals(1, b.ligacao.desfeitas)
    }

    @Test
    fun `DB3 binder nulo e tratado como ligacao morta`() {
        val b = Banco()
        b.nucleo.iniciar()
        b.agenda.rodarAgora()
        b.nucleo.conectou(null)
        assertEquals(1, b.limpezas())
        assertEquals(1, b.ligacao.desfeitas)
    }

    // ───────── B-S1 a B-S3 ─────────

    @Test
    fun `B-S1 primeiro branco limpa uma vez mesmo sem nunca ter tocado`() {
        val b = Banco()
        b.iniciarEConectar()
        b.nucleo.receberQuadro(b.branco()) // 10 000
        assertEquals(1, b.limpezas())
        assertTrue(b.avisos.single().limpar)

        b.agenda.avancarPara(10_500)
        b.nucleo.receberQuadro(b.branco())
        b.agenda.avancarPara(11_000)
        b.nucleo.receberQuadro(null) // "sem objeto" é igual a branco
        assertEquals(1, b.avisos.size)

        b.agenda.avancarPara(12_000)
        b.nucleo.receberQuadro(b.valido("Música"))
        b.segundoPlano.rodarTodas()
        assertEquals(2, b.avisos.size)
        assertEquals("Música", b.avisos.last().titulo)

        b.agenda.avancarPara(12_001)
        b.nucleo.receberQuadro(b.branco()) // agenda para 16 000 (espera 3 999)
        b.agenda.avancarPara(15_999)
        assertEquals(2, b.avisos.size)
        b.agenda.avancarPara(16_000)
        assertEquals(3, b.avisos.size)
        assertTrue(b.avisos.last().limpar)
    }

    @Test
    fun `B-S2 varios brancos nao adiam a limpeza, valido cancela, branco velho limpa na hora`() {
        val b = Banco()
        b.iniciarEConectar()
        b.nucleo.receberQuadro(b.valido()) // 10 000
        b.segundoPlano.rodarTodas()
        assertEquals(1, b.avisos.size)

        b.agenda.avancarPara(11_000)
        b.nucleo.receberQuadro(b.branco()) // agenda 14 000
        b.agenda.avancarPara(12_000)
        b.nucleo.receberQuadro(b.branco()) // reagenda para o mesmo 14 000
        b.agenda.avancarPara(13_000)
        b.nucleo.receberQuadro(b.valido("Outra")) // cancela e entrega
        b.segundoPlano.rodarTodas()
        assertEquals(2, b.avisos.size)

        b.agenda.avancarPara(14_000) // a limpeza cancelada não dispara
        assertEquals(2, b.avisos.size)

        b.agenda.avancarPara(20_000)
        b.nucleo.receberQuadro(b.branco()) // espera 0: limpa já
        assertEquals(3, b.avisos.size)
        assertTrue(b.avisos.last().limpar)
        b.agenda.avancarPara(20_500)
        b.nucleo.receberQuadro(b.branco())
        assertEquals(3, b.avisos.size)
    }

    @Test
    fun `brancos seguidos limpam 4 s depois do ultimo valido`() {
        val b = Banco()
        b.iniciarEConectar()
        b.nucleo.receberQuadro(b.valido()) // 10 000
        b.segundoPlano.rodarTodas()
        b.agenda.avancarPara(11_000)
        b.nucleo.receberQuadro(b.branco())
        b.agenda.avancarPara(12_000)
        b.nucleo.receberQuadro(b.branco())
        b.agenda.avancarPara(13_999)
        assertEquals(0, b.limpezas())
        b.agenda.avancarPara(14_000)
        assertEquals(1, b.limpezas())
    }

    @Test
    fun `B-S3 decodificacao velha termina depois e e descartada`() {
        val b = Banco()
        b.iniciarEConectar()
        b.nucleo.receberQuadro(b.valido("f1"))
        b.nucleo.receberQuadro(b.valido("f2"))
        assertEquals(2, b.segundoPlano.quantas())
        b.segundoPlano.rodar(1) // f2 termina primeiro
        b.segundoPlano.rodar(0) // f1 termina depois: descartado
        assertEquals(listOf("f2"), b.avisos.map { it.titulo })
    }

    @Test
    fun `B-S3 limpar sobe a geracao e descarta a decodificacao em andamento`() {
        val b = Banco()
        b.iniciarEConectar()
        b.nucleo.receberQuadro(b.valido("f1"))
        b.nucleo.desconectou() // limpar
        b.segundoPlano.rodarTodas()
        assertEquals(1, b.avisos.size)
        assertTrue(b.avisos.single().limpar)
    }

    // ───────── B13: o aviso ─────────

    @Test
    fun `o aviso leva titulo, artista, caminho, duracao, posicao e tocando`() {
        val b = Banco()
        b.iniciarEConectar()
        b.nucleo.receberQuadro(QuadroDoCarPlay("T", "A", 180_000, "/x/capa.jpg", 1, 42_000, 0, 0, null))
        b.segundoPlano.rodarTodas()
        val aviso = b.avisos.single()
        assertEquals("T", aviso.titulo)
        assertEquals("A", aviso.artista)
        assertEquals("/x/capa.jpg", aviso.caminhoDaCapa)
        assertEquals(180_000L, aviso.duracaoMs)
        assertEquals(42_000L, aviso.decorridoMs)
        assertTrue(aviso.tocando)
        assertFalse(aviso.limpar)
        assertNull(aviso.capa)
    }

    @Test
    fun `duracao e posicao desconhecidas viram 0 e vazio vira nulo sem aparar espacos`() {
        val b = Banco()
        b.iniciarEConectar()
        b.nucleo.receberQuadro(QuadroDoCarPlay(" ", "", -1, "", 2, -1, -1, -1, null))
        b.segundoPlano.rodarTodas()
        val aviso = b.avisos.single()
        assertEquals(" ", aviso.titulo)
        assertNull(aviso.artista)
        assertNull(aviso.caminhoDaCapa)
        assertEquals(0L, aviso.duracaoMs)
        assertEquals(0L, aviso.decorridoMs)
        assertFalse(aviso.tocando)
    }

    @Test
    fun `estados 3 e 4 contam como nao tocando (DB9 mantido)`() {
        val b = Banco()
        b.iniciarEConectar()
        for (estado in intArrayOf(0, 2, 3, 4)) {
            b.nucleo.receberQuadro(b.valido(estado = estado))
        }
        b.segundoPlano.rodarTodas()
        // só o último quadro (geração mais recente) é entregue
        assertEquals(1, b.avisos.size)
        assertFalse(b.avisos.single().tocando)
    }

    @Test
    fun `estado 1 sem texto nem capa e um quadro valido tocando`() {
        val b = Banco()
        b.iniciarEConectar()
        b.nucleo.receberQuadro(QuadroDoCarPlay(null, null, -1, null, 1, -1, -1, -1, null))
        b.segundoPlano.rodarTodas()
        val aviso = b.avisos.single()
        assertTrue(aviso.tocando)
        assertNull(aviso.titulo)
    }

    @Test
    fun `quadro com conteudo que nao rende titulo, artista nem capa e tratado como branco`() {
        val b = Banco()
        b.iniciarEConectar()
        // só o caminho da capa (que não decodifica) e estado parado
        b.nucleo.receberQuadro(QuadroDoCarPlay(null, null, -1, "/x/capa.jpg", 0, -1, -1, -1, null))
        b.segundoPlano.rodarTodas()
        assertTrue(b.avisos.isEmpty()) // nada publicado; a limpeza vem 4 s depois do "último válido"
        b.agenda.avancarPara(13_999)
        assertEquals(0, b.limpezas())
        b.agenda.avancarPara(14_000)
        assertEquals(1, b.limpezas())
    }

    @Test
    fun `a decodificacao da capa que lanca vira quadro sem capa`() {
        val b = Banco()
        val avisos: MutableList<TocandoNoCarPlay> = java.util.Collections.synchronizedList(ArrayList())
        val nucleo = NucleoDoMonitorDoCarPlay(
            agenda = b.agenda, ligacao = b.ligacao, capaDoQuadro = { throw IllegalStateException("x") },
            emSegundoPlano = b.segundoPlano::postar, registro = RegistroMudo, aoAtualizar = { avisos += it },
        )
        nucleo.iniciar()
        nucleo.conectou(b.servico)
        nucleo.receberQuadro(b.valido("T"))
        b.segundoPlano.rodarTodas()
        assertEquals("T", avisos.single().titulo)
    }

    @Test
    fun `avisos fora da principal sao postados, nunca entregues na thread do binder`() {
        val b = Banco()
        b.agenda.naPrincipal = false
        b.iniciarEConectar()
        b.nucleo.receberQuadro(b.valido("T"))
        b.segundoPlano.rodarTodas()
        assertTrue("só depois de a principal rodar", b.avisos.isEmpty())
        b.agenda.rodarAgora()
        assertEquals("T", b.avisos.single().titulo)
    }

    @Test
    fun `entrega postada para a principal e descartada se chegou um quadro mais novo`() {
        val b = Banco()
        b.agenda.naPrincipal = false
        b.iniciarEConectar()
        b.nucleo.receberQuadro(b.valido("f1"))
        b.segundoPlano.rodarTodas() // f1 decodificado, entrega postada
        b.nucleo.receberQuadro(b.valido("f2")) // geração sobe antes de a principal entregar f1
        b.agenda.rodarAgora()
        b.segundoPlano.rodarTodas()
        b.agenda.rodarAgora()
        assertEquals(listOf("f2"), b.avisos.map { it.titulo })
    }

    // ───────── B15: parar ─────────

    @Test
    fun `B-S5 parar com decodificacao em andamento nao deixa sair aviso nenhum`() {
        val b = Banco()
        b.iniciarEConectar()
        b.nucleo.receberQuadro(b.valido("f1"))
        b.nucleo.parar()
        b.segundoPlano.rodarTodas()
        assertTrue(b.avisos.isEmpty())
    }

    @Test
    fun `parar cancela a assinatura, desfaz a ligacao e para o vigia sem emitir limpar`() {
        val b = Banco()
        b.iniciarEConectar()
        b.servico.transacoes.clear()
        b.nucleo.parar()
        assertEquals(listOf("cancelar"), b.servico.transacoes)
        assertEquals(1, b.ligacao.desfeitas)
        b.agenda.avancarPara(100_000)
        assertEquals(1, b.ligacao.ligacoes)
        assertTrue(b.avisos.isEmpty())
        assertEquals(0, b.agenda.pendentes())
    }

    @Test
    fun `depois de parar quadros, desconexoes e limpezas agendadas nao produzem aviso`() {
        val b = Banco()
        b.iniciarEConectar()
        b.nucleo.receberQuadro(b.valido())
        b.segundoPlano.rodarTodas()
        b.agenda.avancarPara(11_000)
        b.nucleo.receberQuadro(b.branco()) // agenda limpeza
        b.avisos.clear()
        b.nucleo.parar()
        b.nucleo.receberQuadro(b.valido("tarde"))
        b.nucleo.desconectou()
        b.agenda.avancarPara(60_000)
        b.segundoPlano.rodarTodas()
        assertTrue(b.avisos.isEmpty())
    }

    @Test
    fun `limpar ja postado para a principal nao sai depois de parar`() {
        val b = Banco()
        b.agenda.naPrincipal = false
        b.iniciarEConectar()
        b.nucleo.desconectou() // limpar postado
        b.nucleo.parar()
        b.agenda.rodarAgora()
        assertTrue(b.avisos.isEmpty())
    }

    @Test
    fun `parar e iniciar de novo funciona e o primeiro branco limpa de novo`() {
        val b = Banco()
        b.iniciarEConectar()
        b.nucleo.receberQuadro(b.branco())
        assertEquals(1, b.limpezas())
        b.nucleo.parar()
        b.iniciarEConectar()
        assertEquals(2, b.ligacao.ligacoes)
        b.nucleo.receberQuadro(b.branco())
        assertEquals(2, b.limpezas())
    }

    @Test
    fun `escopo cancelado desliga o vigia`() {
        var ativo = true
        val b = Banco(escopoAtivo = { ativo })
        b.nucleo.iniciar()
        b.agenda.rodarAgora()
        ativo = false
        b.agenda.avancarPara(15_000)
        assertEquals(1, b.ligacao.ligacoes)
        assertEquals(0, b.agenda.pendentes())
    }

    // ───────── B16: comandos ─────────

    @Test
    fun `B-S6 sem binder os comandos devolvem falso e nao enviam nada`() {
        val b = Banco()
        b.nucleo.iniciar()
        assertFalse(b.nucleo.proxima())
        assertFalse(b.nucleo.anterior())
        assertFalse(b.nucleo.tocarOuPausar(true))
        assertFalse(b.nucleo.buscar(1_000))
        assertTrue(b.servico.transacoes.isEmpty())
    }

    @Test
    fun `B-S6 tecla desce agora e solta 90 ms depois, devolvendo verdadeiro antes do soltar`() {
        val b = Banco()
        b.iniciarEConectar()
        b.servico.transacoes.clear()
        assertTrue(b.nucleo.proxima())
        assertEquals(listOf("tecla 4 0"), b.servico.transacoes)
        b.agenda.avancarPara(10_089)
        assertEquals(listOf("tecla 4 0"), b.servico.transacoes)
        b.agenda.avancarPara(10_090)
        assertEquals(listOf("tecla 4 0", "tecla 4 1"), b.servico.transacoes)
    }

    @Test
    fun `B-S6 anterior, tocar e pausar usam as teclas 8, 1 e 2`() {
        val b = Banco()
        b.iniciarEConectar()
        b.servico.transacoes.clear()
        assertTrue(b.nucleo.anterior())
        assertTrue(b.nucleo.tocarOuPausar(true))
        assertTrue(b.nucleo.tocarOuPausar(false))
        b.agenda.avancarPara(10_090)
        assertEquals(
            listOf("tecla 8 0", "tecla 2 0", "tecla 1 0", "tecla 8 1", "tecla 2 1", "tecla 1 1"),
            b.servico.transacoes,
        )
    }

    @Test
    fun `descer que falha devolve falso e nao envia soltar`() {
        val b = Banco()
        b.iniciarEConectar()
        b.servico.resultado = false
        b.servico.transacoes.clear()
        assertFalse(b.nucleo.proxima())
        b.agenda.avancarPara(11_000)
        assertEquals(listOf("tecla 4 0"), b.servico.transacoes)
    }

    @Test
    fun `DB1 o soltar sai mesmo se o monitor parar entre descer e soltar`() {
        val b = Banco()
        b.iniciarEConectar()
        b.servico.transacoes.clear()
        assertTrue(b.nucleo.proxima())
        b.nucleo.parar()
        b.agenda.avancarPara(10_090)
        assertTrue(b.servico.transacoes.contains("tecla 4 1"))
    }

    @Test
    fun `buscar manda a posicao e posicao negativa vira 0`() {
        val b = Banco()
        b.iniciarEConectar()
        b.servico.transacoes.clear()
        assertTrue(b.nucleo.buscar(75_000))
        assertTrue(b.nucleo.buscar(-5))
        assertEquals(listOf("posicao 75000", "posicao 0"), b.servico.transacoes)
        b.servico.resultado = false
        assertFalse(b.nucleo.buscar(1))
    }

    @Test
    fun `excecao do servico vira falso`() {
        val b = Banco()
        val estoura = object : ServicoDoCarPlay {
            override val vivo = true
            override fun iniciarAvisos(): Boolean = true
            override fun pararAvisos(): Boolean = true
            override fun enviarTecla(tecla: Int, acao: Int): Boolean = throw IllegalStateException("morto")
            override fun definirPosicao(posicaoMs: Long): Boolean = throw IllegalStateException("morto")
        }
        b.nucleo.iniciar()
        b.nucleo.conectou(estoura)
        assertFalse(b.nucleo.proxima())
        assertFalse(b.nucleo.buscar(1))
    }

    // ───────── DB6: threads ─────────

    @Test
    fun `quadros de varias threads juntos nao quebram nem entregam fora de ordem`() {
        val b = Banco()
        b.iniciarEConectar()
        val comecar = CountDownLatch(1)
        val threads = (0 until 4).map { t ->
            thread {
                comecar.await()
                repeat(200) { i ->
                    b.nucleo.receberQuadro(if (i % 3 == 0) b.branco() else b.valido("t$t-$i"))
                    b.nucleo.proxima()
                }
            }
        }
        comecar.countDown()
        threads.forEach { it.join(10_000) }
        assertTrue(threads.none { it.isAlive })
        b.segundoPlano.rodarTodas()
        // Sem corrida: nenhuma thread travou nem lançou; só o aviso mais recente de cada geração sai.
        assertTrue(b.avisos.count { !it.limpar } <= 1)
    }
}
