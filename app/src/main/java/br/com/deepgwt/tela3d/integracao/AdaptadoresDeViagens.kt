package br.com.deepgwt.tela3d.integracao

import br.com.deepgwt.tela3d.ponte.LugarAtualParaAPonte
import br.com.deepgwt.tela3d.ponte.ViagensParaAPonte
import br.com.deepgwt.tela3d.viagens.FachadaDeViagens
import br.com.deepgwt.tela3d.viagens.LugarAtual

/*
 * Os adaptadores do módulo de viagens: a ponte já normalizou os números das chaves de viagem, e a
 * fachada do módulo os lê como texto.
 */

/** O gravador de viagens como a ponte o usa. [salvarLogo] é o pedido de checkpoint "logo" do gravador. */
internal class ViagensDaPonte(
    private val fachada: FachadaDeViagens,
    private val salvarLogo: () -> Unit,
) : ViagensParaAPonte {

    override fun encerrarViagem() = fachada.finalizarViagem()

    override fun dias(de: String, ate: String): String = fachada.diasEmJson(de, ate)

    override fun aoVivo(): String = fachada.viagemAoVivoEmJson()

    override fun meses(): String = fachada.mesesEmJson()

    override fun totaisDeParadas(deMs: Long): String = fachada.totaisDeParadasEmJson(deMs.toString())

    override fun totais(): String = fachada.totaisEmJson()

    override fun viagem(idMs: Long, maxPontos: Int): String = fachada.viagemEmJson(idMs.toString(), maxPontos)

    override fun mapaDaViagem(idMs: Long): String = fachada.mapaDaViagem(idMs.toString())

    override fun pontosDesde(inicioMs: Long, tMs: Long, maxPontos: Int): String =
        fachada.pontosDesdeEmJson(inicioMs.toString(), tMs.toString(), maxPontos)

    override fun lista(offset: Int, limite: Int): String = fachada.viagensEmJson(offset, limite)

    /** Chamado quando a Activity pausa ou é destruída: o gravador salva o que está em andamento. */
    override fun gravarEmBreve() = salvarLogo()
}

/** O que o adaptador do lugar atual precisa do serviço (o `LugarAtual` do módulo, ou um falso nos testes). */
internal interface ServicoDeLugar {
    fun iniciar()
    fun parar()
    fun cutucarAgora()
}

/** O [LugarAtual] do módulo de viagens como serviço. */
internal class LugarAtualComoServico(private val lugar: LugarAtual) : ServicoDeLugar {
    override fun iniciar() = lugar.iniciar()

    override fun parar() = lugar.parar()

    override fun cutucarAgora() = lugar.cutucarAgora()
}

/**
 * O lugar atual como a ponte o usa: `iniciar(aoMudar)` é chamado na criação e a cada retomada, então a
 * função de retorno é trocada pela mais recente (a do serviço é uma só) e o ciclo do serviço é idempotente.
 */
internal class LugarDaPonte(private val servico: ServicoDeLugar) : LugarAtualParaAPonte {
    @Volatile
    private var aoMudar: ((String) -> Unit)? = null

    /** Entrega o JSON do lugar à função mais recente; chamado pela thread principal (o serviço posta). */
    fun entregar(json: String) {
        aoMudar?.invoke(json)
    }

    override fun iniciar(aoMudar: (texto: String) -> Unit) {
        this.aoMudar = aoMudar
        servico.iniciar()
    }

    override fun parar() {
        aoMudar = null
        servico.parar()
    }

    override fun consultarJa() = servico.cutucarAgora()
}
