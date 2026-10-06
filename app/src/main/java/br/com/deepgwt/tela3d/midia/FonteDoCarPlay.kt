package br.com.deepgwt.tela3d.midia

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock

/**
 * Fonte 3: o serviço do CarPlay da fabricante (caso I). Registra um retorno de "tocando agora",
 * publica a faixa e envia os comandos como toques HID. A faixa é entregue na thread principal.
 */
internal class FonteDoCarPlay(
    private val contexto: Context,
    private val principal: Handler,
    private val aoMudar: (FaixaLida?) -> Unit,
    private val agora: () -> Long = SystemClock::elapsedRealtime,
) {
    private val fundo = LacoDeFundo("deepgwt-carplay")
    private val ligacao = LigacaoComServico(
        contexto = contexto,
        nome = "CarPlay",
        intencoes = listOf(
            LigacaoComServico.intencaoDeComponente(ProtocoloDoCarPlay.PACOTE, ProtocoloDoCarPlay.CLASSE_DO_SERVICO),
            Intent(ProtocoloDoCarPlay.ACAO_DO_SERVICO).setPackage(ProtocoloDoCarPlay.PACOTE),
        ),
        aoConectar = { retornoRegistrado = false; registrarRetorno(it) },
        aoDesconectar = { aoPerderServico() },
    )

    private val retorno = RetornoDoCarPlay()
    private val trava = Any()
    private val tolerancia = ToleranciaDeAtualizacaoVazia()

    @Volatile
    private var iniciada = false

    @Volatile
    private var retornoRegistrado = false

    // Cache da capa (protegido por [trava]; o retorno chega em threads de binder).
    private var chaveDaCapa = ""
    private var capaEmCache: Bitmap? = null

    /** Liga e inicia o cão de guarda de 5 s (idempotente). */
    @Synchronized
    fun iniciar() {
        if (iniciada) return
        iniciada = true
        fundo.iniciar(PERIODO_DO_CAO_MS) { guardar() }
    }

    /** Cancela o cão de guarda, desregistra o retorno, solta a ligação e encerra a thread. */
    @Synchronized
    fun parar() {
        iniciada = false
        fundo.parar()
        ligacao.binder?.let { cancelarRetorno(it) }
        ligacao.soltar()
        retornoRegistrado = false
        synchronized(trava) {
            tolerancia.esquecer()
            capaEmCache = null
            chaveDaCapa = ""
        }
    }

    /**
     * I5: toque HID (pressionar agora e soltar 90 ms depois na thread de fundo). Verdadeiro se o
     * PRESSIONAR foi aceito; sem binder devolve falso.
     */
    fun enviarToque(uso: Int): Boolean {
        val binder = ligacao.binder ?: return false
        val aceito = try {
            toqueHid(binder, uso, ProtocoloDoCarPlay.HID_PRESSIONAR)
        } catch (e: Exception) {
            Diario.aviso("toque HID do CarPlay falhou", e)
            return false
        }
        fundo.agendar(ProtocoloDoCarPlay.PRESSAO_HID_MS) {
            try {
                toqueHid(binder, uso, ProtocoloDoCarPlay.HID_SOLTAR)
            } catch (e: Exception) {
                Diario.aviso("soltura HID do CarPlay falhou", e)
            }
        }
        return aceito
    }

    private fun toqueHid(binder: IBinder, uso: Int, acao: Int): Boolean =
        TransacoesDeBinder.semResposta(binder, ProtocoloDoCarPlay.DESCRITOR_DO_SERVICO, ProtocoloDoCarPlay.TRANSACAO_TOQUE_HID) {
            it.writeInt(uso)
            it.writeInt(acao)
        }

    // --- ligação e registro do retorno -------------------------------------------------------

    private fun guardar() {
        val binder = ligacao.garantir() ?: return
        if (!retornoRegistrado) registrarRetorno(binder)
    }

    private fun registrarRetorno(binder: IBinder) {
        try {
            TransacoesDeBinder.semResposta(
                binder, ProtocoloDoCarPlay.DESCRITOR_DO_SERVICO, ProtocoloDoCarPlay.TRANSACAO_REGISTRAR_RETORNO,
            ) { it.writeStrongBinder(retorno) }
        } catch (e: Exception) {
            Diario.aviso("registro do retorno do CarPlay falhou", e)
        }
    }

    private fun cancelarRetorno(binder: IBinder) {
        try {
            TransacoesDeBinder.semResposta(
                binder, ProtocoloDoCarPlay.DESCRITOR_DO_SERVICO, ProtocoloDoCarPlay.TRANSACAO_CANCELAR_RETORNO,
            ) { it.writeStrongBinder(retorno) }
        } catch (_: Exception) {
        }
    }

    private fun aoPerderServico() {
        retornoRegistrado = false
        entregar(null)
    }

    // --- atualizações recebidas (threads de binder) ---------------------------------------------

    private fun aoReceber(dados: Parcel) {
        retornoRegistrado = true
        val atualizacao = try {
            DecodificadorDoCarPlay.ler(LeitorDeParcel(dados))
        } catch (e: Exception) {
            Diario.aviso("atualização do CarPlay ilegível", e)
            return
        }
        if (atualizacao == null || !atualizacao.valida) {
            aoReceberVazia()
            return
        }
        val instante = agora()
        val faixa = synchronized(trava) {
            tolerancia.aoReceberValida(instante)
            montar(atualizacao, instante)
        }
        entregar(faixa)
    }

    private fun montar(a: AtualizacaoDoCarPlay, instante: Long): FaixaLida {
        val chave = a.chaveDaCapa
        var capa = capaEmCache?.takeIf { chave == chaveDaCapa && !it.isRecycled }
        if (capa == null) {
            capa = a.imagem?.takeIf { it.isNotEmpty() }?.let { DecodificadorDeImagem.deBytes(it) }
                ?: a.caminhoDaArte.trim().takeIf { it.isNotEmpty() }?.let { DecodificadorDeImagem.deLocal(contexto, it) }
            capaEmCache = capa
            chaveDaCapa = chave
        }
        return FaixaLida(
            fonte = FonteDaFaixa.CARPLAY,
            pacote = ProtocoloDoCarPlay.PACOTE,
            rotulo = RotulosDeMidia.CARPLAY,
            titulo = a.titulo.trim(),
            artista = a.artista.trim(),
            album = "",
            duracaoMs = a.duracaoMs,
            tocando = a.tocando,
            posicaoInformadaMs = a.decorridoMs,
            instanteDaPosicaoMs = instante,
            origemDaCapa = OrigemDaCapa(capa, "", "", chave),
        )
    }

    /** I4: a vazia não limpa dentro da tolerância; ao fim dela, limpa se nenhuma válida chegou. */
    private fun aoReceberVazia() {
        val decisao = synchronized(trava) { tolerancia.aoReceberVazia(agora()) }
        when (decisao) {
            DecisaoDaVazia.LimparAgora -> limpar()
            is DecisaoDaVazia.Aguardar -> fundo.agendar(decisao.restanteMs + 1) {
                val deveLimpar = synchronized(trava) { tolerancia.deveLimparAoFimDaEspera(agora()) }
                if (deveLimpar) limpar()
            }
        }
    }

    private fun limpar() {
        synchronized(trava) {
            capaEmCache = null
            chaveDaCapa = ""
        }
        entregar(null)
    }

    private fun entregar(faixa: FaixaLida?) {
        if (!iniciada) return
        principal.post { if (iniciada) aoMudar(faixa) }
    }

    /** O binder do app que o CarPlay chama de volta (transação 1). */
    private inner class RetornoDoCarPlay : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == IBinder.INTERFACE_TRANSACTION) {
                reply?.writeString(ProtocoloDoCarPlay.DESCRITOR_DO_RETORNO)
                return true
            }
            if (code == ProtocoloDoCarPlay.TRANSACAO_DO_RETORNO) {
                consumirTokenDaInterface(data)
                aoReceber(data)
                return true
            }
            return try {
                super.onTransact(code, data, reply, flags)
            } catch (_: RuntimeException) {
                true
            }
        }
    }

    /**
     * Se o chamador escreveu o token da interface, consome-o; se não escreveu, a leitura volta ao
     * início para o formato da atualização (o primeiro inteiro é o "presente").
     */
    private fun consumirTokenDaInterface(data: Parcel) {
        val inicio = data.dataPosition()
        try {
            data.enforceInterface(ProtocoloDoCarPlay.DESCRITOR_DO_RETORNO)
        } catch (_: RuntimeException) {
            data.setDataPosition(inicio)
        }
    }

    private companion object {
        const val PERIODO_DO_CAO_MS = 5_000L
    }
}
