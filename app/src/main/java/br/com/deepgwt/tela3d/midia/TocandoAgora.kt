package br.com.deepgwt.tela3d.midia

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.SystemClock
import android.view.KeyEvent

/** O que a página (e o cartão) podem pedir ao publicador. Sempre na thread principal. */
interface ControleDeMidia {
    fun anterior()
    fun tocarOuPausar()
    fun proxima()

    /** Inicia (idempotente). */
    fun iniciar()

    /** Força um novo payload mesmo que igual ao anterior. */
    fun reenviarAgora()
}

/**
 * O publicador do "tocando agora": descobre o que toca (sessões de mídia do Android, central da
 * fabricante e CarPlay), escolhe o vencedor, publica um objeto de estado completo ao destino e a
 * posição a cada 250 ms, e executa os comandos de transporte na fonte certa.
 *
 * Todo o estado vive na thread principal; as fontes de fundo só postam o resultado para ela. Ciclo
 * de vida: [anexar] ao criar a tela, [iniciar] a cada retomada, [parar] ao destruir.
 */
class TocandoAgora(contexto: Context, private val principal: Handler) : FaixasDeProjecao, ControleDeMidia {
    private val aplicacao: Context = contexto.applicationContext

    private val publicador = PublicadorDePayload()
    private val capas = GerenteDeCapas(principal, aoPrecisarRepublicar = { reenviarEstado() })
    private val informacoes = InformacoesDosApps(aplicacao)
    private val central = FonteDaCentral(aplicacao, principal) { faixa -> aoFaixaDaCentral(faixa) }
    private val carPlay = FonteDoCarPlay(aplicacao, principal, aoMudar = { faixa -> aoFaixaDoCarPlay(faixa) })

    private var anexado = false
    private var iniciado = false

    private var faixaDeSessao: FaixaLida? = null

    @Volatile
    private var faixaDaCentral: FaixaLida? = null

    @Volatile
    private var faixaDoCarPlay: FaixaLida? = null

    private var vencedor: FaixaLida? = null
    private var fonteDoUltimoVencedor: FonteDaFaixa? = null

    private var observador: ObservadorDeSessoes? = null
    private var ouvinteObservado: OuvinteDeMidiaDaTelaInicial? = null
    private var acessoNegado = false
    private var destino: DestinoDoTocandoAgora? = null

    override val temFaixaDoAndroidAuto: Boolean
        get() = faixaDaCentral?.let { it.rotulo == RotulosDeMidia.ANDROID_AUTO && it.temFaixa } ?: false

    override val temFaixaDoCarPlay: Boolean
        get() = faixaDoCarPlay?.temFaixa ?: false

    // --- ciclo de vida (A) ---------------------------------------------------------------------

    /** A1: guarda o destino, abre as threads das capas e se registra para o ouvinte de notificações. */
    fun anexar(destino: DestinoDoTocandoAgora) {
        this.destino = destino
        publicador.destino = destino
        anexado = true
        capas.abrir()
        RegistroDoOuvinte.registrar(this)
    }

    /** A2: idempotente e barato quando repetido. */
    override fun iniciar() {
        if (!anexado) return
        iniciado = true
        principal.removeCallbacks(lacoDeEspera)
        central.iniciar()
        carPlay.iniciar()
        iniciarRelogio()
        val ouvinte = RegistroDoOuvinte.ouvinte
        if (ouvinte != null) {
            iniciarObservacao(ouvinte)
        } else {
            limparSessao()
            ligarLacoDeEspera()
        }
    }

    /** "Reenviar agora": o próximo envio sai mesmo que idêntico ao anterior. */
    override fun reenviarAgora() {
        publicador.esquecerAssinatura()
        reenviarEstado()
    }

    /** A4: depois de parar nada mais é entregue ao destino anterior. */
    fun parar() {
        iniciado = false
        anexado = false
        principal.removeCallbacks(lacoDeEspera)
        principal.removeCallbacks(relogio)
        central.parar()
        carPlay.parar()
        observador?.parar()
        observador = null
        ouvinteObservado = null
        capas.fechar()
        RegistroDoOuvinte.desregistrar(this)
        destino = null
        publicador.destino = null
        publicador.esquecerAssinatura()
        faixaDeSessao = null
        faixaDaCentral = null
        faixaDoCarPlay = null
        vencedor = null
        fonteDoUltimoVencedor = null
        acessoNegado = false
    }

    // --- comandos (C) --------------------------------------------------------------------------

    override fun anterior() = transportar(ComandoDeMidia.ANTERIOR)

    override fun tocarOuPausar() = transportar(ComandoDeMidia.TOCAR_OU_PAUSAR)

    override fun proxima() = transportar(ComandoDeMidia.PROXIMA)

    private fun transportar(comando: ComandoDeMidia) {
        val atual = vencedor
        val conectado = RegistroDoOuvinte.ouvinte != null
        val tocando = atual?.tocando ?: false
        for (rota in RoteamentoDeComandos.rotas(comando, atual?.fonte, conectado)) {
            when (rota) {
                RotaDoComando.HID_DO_CARPLAY ->
                    if (carPlay.enviarToque(UsosHidDoCarPlay.doComando(comando, tocando))) return
                RotaDoComando.CENTRAL ->
                    if (central.tocarOuPausar(tocando)) return
                RotaDoComando.TECLA_GLOBAL ->
                    enviarTeclaGlobal(TeclasDeMidia.doComando(comando))
                RotaDoComando.OUVINTE -> {
                    val sessoes = observador
                    val teclaDeFalha = if (sessoes != null) {
                        sessoes.executar(comando)
                    } else {
                        ComandoDoOuvinte.planejar(comando, false, null, 0L).teclaDeFalha
                    }
                    if (teclaDeFalha != null) enviarTeclaGlobal(teclaDeFalha)
                }
            }
        }
    }

    /** Injeta DOWN e UP com o mesmo carimbo, pelo `AudioManager`. */
    private fun enviarTeclaGlobal(codigo: Int) {
        try {
            val audio = aplicacao.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val instante = SystemClock.uptimeMillis()
            audio.dispatchMediaKeyEvent(KeyEvent(instante, instante, KeyEvent.ACTION_DOWN, codigo, 0))
            audio.dispatchMediaKeyEvent(KeyEvent(instante, instante, KeyEvent.ACTION_UP, codigo, 0))
        } catch (e: Exception) {
            Diario.aviso("tecla de mídia $codigo falhou", e)
        }
    }

    // --- eventos das fontes --------------------------------------------------------------------

    private fun aoFaixaDaCentral(faixa: FaixaLida?) {
        if (!anexado) return
        faixaDaCentral = faixa
        publicar()
    }

    private fun aoFaixaDoCarPlay(faixa: FaixaLida?) {
        if (!anexado) return
        faixaDoCarPlay = faixa
        publicar()
    }

    private fun aoFaixaDeSessao(faixa: FaixaLida?) {
        if (!anexado) return
        faixaDeSessao = faixa
        publicar()
    }

    /** B4: o sistema ligou o serviço do ouvinte. Chamado de qualquer thread. */
    internal fun aoOuvinteConectar(ouvinte: OuvinteDeMidiaDaTelaInicial) {
        principal.post {
            if (!anexado || !iniciado) return@post
            principal.removeCallbacks(lacoDeEspera)
            iniciarObservacao(ouvinte)
        }
    }

    /** B4: o serviço do ouvinte caiu. Chamado de qualquer thread. */
    internal fun aoOuvinteDesconectar() {
        principal.post {
            if (!anexado) return@post
            pararObservacao()
            limparSessao()
            if (iniciado) ligarLacoDeEspera()
        }
    }

    /** B6: chegou uma notificação; se é do app da sessão escolhida e falta capa, relê a sessão. */
    internal fun aoNotificacaoDeApp(pacote: String) {
        principal.post {
            if (!anexado) return@post
            val atual = observador ?: return@post
            if (pacote == atual.pacoteEscolhido && precisaDeCapa()) atual.relerAgora()
        }
    }

    /** E7. */
    private fun precisaDeCapa(): Boolean {
        val v = vencedor
        if (v != null && v.fonte != FonteDaFaixa.SESSAO) return false
        return capas.precisaDeCapa()
    }

    // --- observação das sessões ---------------------------------------------------------------

    private fun iniciarObservacao(ouvinte: OuvinteDeMidiaDaTelaInicial) {
        var atual = observador
        if (atual == null || ouvinteObservado !== ouvinte) {
            atual?.parar()
            atual = ObservadorDeSessoes(aplicacao, principal, ouvinte, capas) { faixa -> aoFaixaDeSessao(faixa) }
            observador = atual
            ouvinteObservado = ouvinte
        }
        val aceito = atual.iniciar()
        if (aceito) {
            acessoNegado = false
        } else {
            // B5 (defeito 8): serviço conectado sem acesso às sessões: pedir a liberação ao usuário.
            acessoNegado = true
            faixaDeSessao = null
            publicar()
            ligarLacoDeEspera() // se o acesso for liberado depois, a próxima tentativa funciona
        }
    }

    private fun pararObservacao() {
        observador?.parar()
        observador = null
        ouvinteObservado = null
    }

    /** B2: sem ouvinte, não há faixa de sessão: republica (vazio com pedido de acesso, se mudou). */
    private fun limparSessao() {
        faixaDeSessao = null
        publicar()
    }

    /**
     * B3: laço de espera. Enquanto não há ouvinte conectado (ou o acesso às sessões foi negado), a cada
     * 2,5 s: se já conectou, inicia a observação; senão reaplica a limpeza (sem payload novo).
     */
    private val lacoDeEspera = object : Runnable {
        override fun run() {
            if (!iniciado || !anexado) return
            val ouvinte = RegistroDoOuvinte.ouvinte
            if (ouvinte != null) {
                iniciarObservacao(ouvinte)
                if (!acessoNegado) return // observando: o laço termina
            } else {
                limparSessao()
            }
            ligarLacoDeEspera()
        }
    }

    private fun ligarLacoDeEspera() {
        principal.removeCallbacks(lacoDeEspera)
        principal.postDelayed(lacoDeEspera, INTERVALO_DO_LACO_MS)
    }

    /** Pede o estado atual: pela observação das sessões se há, senão publica direto. */
    private fun reenviarEstado() {
        val atual = observador
        if (anexado && atual != null) atual.relerAgora() else publicar()
    }

    // --- relógio de posição (F1) ---------------------------------------------------------------

    private val relogio = object : Runnable {
        override fun run() {
            if (!iniciado || !anexado) return
            val v = vencedor
            val d = destino
            if (v != null && v.tocando && d != null) d.aoMudarPosicao(v.posicaoAoVivo(SystemClock.elapsedRealtime()))
            principal.postDelayed(this, PERIODO_DO_RELOGIO_MS)
        }
    }

    private fun iniciarRelogio() {
        principal.removeCallbacks(relogio)
        principal.postDelayed(relogio, PERIODO_DO_RELOGIO_MS)
    }

    // --- publicação (D) ------------------------------------------------------------------------

    /** O ouvinte conectado (e, com isso, o acesso concedido) dispensa o pedido de acesso (D2). */
    private fun precisaDeAcesso(): Boolean = RegistroDoOuvinte.ouvinte == null || acessoNegado

    private fun publicar() {
        if (!anexado) return
        val sessao = faixaDeSessao
        val doCentral = faixaDaCentral
        val doCarPlay = faixaDoCarPlay
        val fonte = Arbitragem.escolher(sessao?.candidata(), doCentral?.candidata(), doCarPlay?.candidata())
        val faixa = when (fonte) {
            FonteDaFaixa.SESSAO -> sessao
            FonteDaFaixa.CENTRAL -> doCentral
            FonteDaFaixa.CARPLAY -> doCarPlay
            null -> null
        }
        vencedor = faixa
        if (faixa == null) {
            // D2: sem vencedor zera a memória da capa e a fonte do último vencedor.
            capas.esquecerAtual()
            fonteDoUltimoVencedor = null
            publicador.publicarVazio(precisaDeAcesso())
            return
        }
        if (faixa.fonte != fonteDoUltimoVencedor) {
            fonteDoUltimoVencedor = faixa.fonte
            Diario.info("vencedor: ${faixa.fonte} (${faixa.rotulo.ifEmpty { faixa.pacote }})")
        }
        val capa = capas.paraPublicar(faixa.fonte, faixa.origemDaCapa)
        val app = informacoes.obter(faixa.pacote)
        val rotulo = if (faixa.fonte == FonteDaFaixa.SESSAO) app.rotulo else faixa.rotulo
        publicador.publicar(
            EntradaDoPayload(
                fonte = faixa.fonte,
                titulo = faixa.titulo,
                artista = faixa.artista,
                album = faixa.album,
                duracaoMs = faixa.duracaoMs,
                posicaoMs = faixa.posicaoAoVivo(SystemClock.elapsedRealtime()),
                tocando = faixa.tocando,
                rotulo = rotulo,
                pacote = faixa.pacote,
                chaveDaCapa = capa.chave,
                artDataUrl = capa.artDataUrl,
                appIcon = app.icone,
                canLaunch = app.canLaunch,
            ),
        )
    }

    private companion object {
        const val INTERVALO_DO_LACO_MS = 2_500L
        const val PERIODO_DO_RELOGIO_MS = 250L
    }
}
