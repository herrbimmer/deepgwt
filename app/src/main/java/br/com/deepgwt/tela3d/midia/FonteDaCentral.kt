package br.com.deepgwt.tela3d.midia

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.IBinder
import android.os.SystemClock

/**
 * Fonte 2: a central de mídia da fabricante (USB e Android Auto nativo), caso H. Um laço de fundo
 * lê a central a cada 1,5 s e entrega o resultado (uma faixa ou nada) na thread principal.
 */
internal class FonteDaCentral(
    private val contexto: Context,
    private val principal: Handler,
    private val aoLer: (FaixaLida?) -> Unit,
) {
    private val laco = LacoDeFundo("deepgwt-central")
    private val ligacao = LigacaoComServico(
        contexto = contexto,
        nome = "central de mídia",
        intencoes = listOf(
            LigacaoComServico.intencaoDeComponente(ProtocoloDaCentral.PACOTE, ProtocoloDaCentral.CLASSE_DO_SERVICO),
        ),
        aoDesconectar = { esquecerReproducao() },
    )

    @Volatile
    private var reproducao: IBinder? = null

    /** Fonte ativa lida no último ciclo (402, 2 ou 0 = nenhuma): usada pelo comando oneway. */
    @Volatile
    private var fonteAtiva = 0

    @Volatile
    private var iniciada = false

    // Estado só tocado pela thread do laço.
    private var chaveDaImagem = ""
    private var imagemDecodificada: Bitmap? = null
    private var ultimoResumo = ""

    /** Liga e inicia o laço (idempotente). */
    @Synchronized
    fun iniciar() {
        if (iniciada) return
        iniciada = true
        ligacao.garantir()
        laco.iniciar(PERIODO_MS) { ciclo() }
    }

    /** Para o laço, encerra a thread e solta a ligação. */
    @Synchronized
    fun parar() {
        iniciada = false
        laco.parar()
        ligacao.soltar()
        esquecerReproducao()
        fonteAtiva = 0
        imagemDecodificada = null
        chaveDaImagem = ""
        ultimoResumo = ""
    }

    private fun esquecerReproducao() {
        reproducao = null
    }

    /** H2: tocar/pausar da central (oneway). Verdadeiro se o `transact` aceitou. */
    fun tocarOuPausar(estaTocando: Boolean): Boolean {
        val binder = reproducao ?: return false
        val fonte = fonteAtiva
        if (fonte != DecodificadorDaCentral.FONTE_ANDROID_AUTO && fonte != DecodificadorDaCentral.FONTE_USB) return false
        val codigo = if (estaTocando) ProtocoloDaCentral.TRANSACAO_PAUSAR else ProtocoloDaCentral.TRANSACAO_RETOMAR
        return try {
            TransacoesDeBinder.semResposta(binder, ProtocoloDaCentral.DESCRITOR_DE_REPRODUCAO, codigo) { it.writeInt(fonte) }
        } catch (e: Exception) {
            Diario.aviso("comando da central falhou", e)
            esquecerReproducao()
            false
        }
    }

    // --- ciclo de leitura (thread de fundo) -------------------------------------------------------

    private fun ciclo() {
        val faixa = try {
            lerFaixa()
        } catch (e: Exception) {
            Diario.aviso("leitura da central falhou", e)
            esquecerReproducao()
            null
        }
        val resumo = faixa?.let { "${it.rotulo}|${it.titulo}|${it.artista}|${it.tocando}" }.orEmpty()
        if (resumo != ultimoResumo) {
            ultimoResumo = resumo
            Diario.info("central: ${if (resumo.isEmpty()) "sem faixa" else resumo}")
        }
        if (!iniciada) return
        principal.post { if (iniciada) aoLer(faixa) }
    }

    private fun lerFaixa(): FaixaLida? {
        val play = binderDeReproducao()
        if (play == null) {
            fonteAtiva = 0
            return null
        }
        val d = ProtocoloDaCentral.DESCRITOR_DE_REPRODUCAO
        val atual = TransacoesDeBinder.inteira(play, d, ProtocoloDaCentral.TRANSACAO_FONTE_ATUAL)
        val audio = TransacoesDeBinder.inteira(play, d, ProtocoloDaCentral.TRANSACAO_FONTE_DE_AUDIO)
        val fonte = DecodificadorDaCentral.escolherFonte(atual, audio)
        if (fonte == null) {
            fonteAtiva = 0
            return null
        }
        val info = TransacoesDeBinder.sincrona(
            play, d, ProtocoloDaCentral.TRANSACAO_INFORMACAO,
            escrever = { it.writeInt(fonte) },
        ) { DecodificadorDaCentral.informacao(it, fonte) }
        val estado = TransacoesDeBinder.sincrona(
            play, d, ProtocoloDaCentral.TRANSACAO_ESTADO,
            escrever = { it.writeInt(fonte) },
        ) { DecodificadorDaCentral.estado(it, fonte) }
        fonteAtiva = fonte
        val dados = DecodificadorDaCentral.montarFaixa(fonte, info, estado) ?: return null
        val instante = SystemClock.elapsedRealtime()
        val bitmapDoPacote = (info?.bitmap as? Bitmap)?.takeIf { !it.isRecycled }
        val capa = resolverCapa(dados, bitmapDoPacote)
        val origem = OrigemDaCapa(
            bitmap = capa,
            urlRemota = if (capa == null) dados.urlRemota else "",
            consulta = "",
            chave = if (capa != null) {
                DecodificadorDaCentral.chaveDaCapa(dados, capa.dimensoes())
            } else {
                DecodificadorDaCentral.chaveDaCapa(dados, null)
            },
        )
        val faixa = FaixaLida(
            fonte = FonteDaFaixa.CENTRAL,
            pacote = dados.pacote,
            rotulo = dados.rotulo,
            titulo = dados.titulo,
            artista = dados.artista,
            album = dados.album,
            duracaoMs = dados.duracaoMs,
            tocando = dados.tocando,
            posicaoInformadaMs = dados.posicaoMs,
            instanteDaPosicaoMs = instante,
            origemDaCapa = origem,
        )
        return if (faixa.temFaixa) faixa else null
    }

    /** Pede o binder de reprodução à central (transação 1) se ainda não há. */
    private fun binderDeReproducao(): IBinder? {
        reproducao?.takeIf { it.isBinderAlive }?.let { return it }
        reproducao = null
        val servico = ligacao.garantir() ?: return null
        val binder = TransacoesDeBinder.sincrona(
            servico, ProtocoloDaCentral.DESCRITOR_DO_SERVICO, ProtocoloDaCentral.TRANSACAO_PEDIR_REPRODUCAO,
            escrever = { it.writeInt(ProtocoloDaCentral.ARGUMENTO_PEDIR_REPRODUCAO) },
        ) { it.lerBinder() }
        reproducao = binder
        return binder
    }

    /**
     * A capa vale a de maior área entre o bitmap do pacote e a imagem decodificada da URL (esta só
     * vence se estritamente maior). A decodificada é guardada por chave para não decodificar a cada 1,5 s.
     */
    private fun resolverCapa(dados: DadosDaFaixaDaCentral, bitmapDoPacote: Bitmap?): Bitmap? {
        if (dados.imagemLocal.isEmpty()) return bitmapDoPacote
        val chave = DecodificadorDaCentral.chaveDaCapa(dados, bitmapDoPacote?.dimensoes())
        if (chave != chaveDaImagem) {
            chaveDaImagem = chave
            imagemDecodificada = DecodificadorDeImagem.deLocal(contexto, dados.imagemLocal)
        }
        val decodificada = imagemDecodificada?.takeIf { !it.isRecycled }
        if (decodificada == null) return bitmapDoPacote
        if (bitmapDoPacote == null) return decodificada
        return if (RegrasDeCapa.decodificadoVence(bitmapDoPacote.dimensoes().area, decodificada.dimensoes().area)) {
            decodificada
        } else {
            bitmapDoPacote
        }
    }

    private companion object {
        const val PERIODO_MS = 1_500L
    }
}
