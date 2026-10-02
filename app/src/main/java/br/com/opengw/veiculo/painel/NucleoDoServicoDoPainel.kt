package br.com.opengw.veiculo.painel

import br.com.opengw.nucleo.Preferencias
import br.com.opengw.painel.ArbitroDoCartao
import br.com.opengw.painel.TeclaDoVolante
import br.com.opengw.veiculo.ProjecaoNasTelas
import java.util.concurrent.RejectedExecutionException

/**
 * O que o motor guarda sobre o cartão do painel (A.5), numa foto. Instantes em ms de uptime;
 * [NUNCA] = não houve.
 */
internal data class FotoDoCartao(
    val cartao: Int = ArbitroDoCartao.CARTAO_DO_CARRO,
    /** A última tecla do painel recebida (B.4 passo 1). */
    val ultimaTecla: TeclaDoVolante? = null,
    val instanteDaTecla: Long = NUNCA,
    /** A última navegação sintética (A.6): alvo (−1 = nenhuma) e instante. */
    val alvoSintetico: Int = -1,
    val instanteSintetico: Long = NUNCA,
    /** O app abriu o cartão do ar sinteticamente e ainda é dono dele (A.6/A.7). */
    val arDoApp: Boolean = false,
) {
    /** A.5: ignorar a 133 com [novo], [agora]? Passa ao árbitro os tempos desde cada evento. */
    fun ignorar(novo: Int, agora: Long, protegerComProjecao: Boolean): Boolean =
        ArbitroDoCartao.ignorarCartaoDoCarro(
            anterior = cartao,
            novo = novo,
            desdeTeclaMs = desde(instanteDaTecla, agora),
            ultimaTecla = if (instanteDaTecla == NUNCA) null else ultimaTecla,
            desdeSinteticoMs = desde(instanteSintetico, agora),
            alvoSintetico = if (instanteSintetico == NUNCA) -1 else alvoSintetico,
            protegerSaidaDoArComProjecao = protegerComProjecao,
            protegerSaidaDoArSintetica = arDoApp,
        )

    companion object {
        /** ms desde [instante] (−1 = nunca). */
        fun desde(instante: Long, agora: Long): Long =
            if (instante == NUNCA) -1L else (agora - instante).coerceAtLeast(0L)
    }
}

/** Regras pequenas comuns ao painel e ao volante. */
internal object RegrasDoMotorDoPainel {
    /** B.6: o tema esportivo antigo está ativo (`activeCustomTheme` = SportRed/SportRedLite, sem caixa). */
    fun temaEsportivoAntigo(temaAtivo: String?): Boolean =
        temaAtivo != null &&
            (temaAtivo.equals("SportRed", ignoreCase = true) || temaAtivo.equals("SportRedLite", ignoreCase = true))

    fun temaEsportivoAntigo(preferencias: LeitorDePreferencias): Boolean =
        temaEsportivoAntigo(preferencias.texto(Preferencias.ACTIVE_CUSTOM_THEME, ""))
}

/**
 * O serviço do painel da fabricante sem Android (§A): cartão atual, árbitro, navegação sintética,
 * pulso de vida e as mensagens 75/133/134/135. Seguro entre threads: as mensagens chegam numa
 * thread de binder do serviço do painel, as teclas noutra do serviço de teclas, o pulso na linha do
 * painel. Nada aqui chama código de fora com a trava presa.
 */
internal class NucleoDoServicoDoPainel(
    private val relogio: Relogio,
    private val registro: RegistroDoPainel,
    private val diario: DiarioDoPainel,
    private val preferencias: LeitorDePreferencias,
    private val publicador: PublicadorDoPainel,
) : CartaoDoPainel {

    companion object {
        const val MSG_ANDROID_PRONTO = 75
        const val MSG_CARTAO = 133
        const val MSG_PULSO = 134
        const val MSG_MIDIA = 135

        /** 134 vindo do painel com este valor = "mande o pulso" (A.4.5). */
        const val PEDIDO_DE_PULSO = 2
        const val INTERVALO_DO_PULSO_MS = 1_000L
        const val CONTADOR_MAXIMO = 32_767

        /** O pulso entra no log a cada tantos envios (A.4.3). */
        const val LOG_A_CADA_PULSOS = 10

        /** As únicas mensagens do painel que entram no log (o registro despeja ~100). */
        private val MENSAGENS_NO_LOG = setOf(MSG_ANDROID_PRONTO, MSG_CARTAO, MSG_PULSO, MSG_MIDIA)
    }

    /** Ligadas por `instalar`; vazias até lá. */
    @Volatile var projecao: ProjecaoNasTelas = ProjecaoNasTelas.VAZIA
    @Volatile var menuAntigo: MenuAntigoDoPainel = MenuAntigoDoPainel.VAZIO

    /** A interface do painel enquanto conectado (A.1); null = os envios são descartados com log. */
    @Volatile var canal: CanalDoPainel? = null

    private val trava = Any()

    // ── Estado do cartão (sob [trava]) ──
    private var foto = FotoDoCartao()
    private var instanteDoUltimo133 = NUNCA

    // ── Pulso (sob [trava]) ──
    private var linhaAtual: LinhaDoPainel? = null
    private var geracao = 0
    private var rodando = false
    private var contador = 0
    private var pulsosEnviados = 0L

    /** A linha do painel atual (entre ligar e desligar), ou null. */
    val linha: LinhaDoPainel? get() = synchronized(trava) { linhaAtual }

    override val cartaoAtual: Int get() = synchronized(trava) { foto.cartao }

    val pulsoRodando: Boolean get() = synchronized(trava) { rodando }

    /** Só diagnóstico: ms de uptime da última 133 recebida ([NUNCA] = nenhuma). */
    val instanteDaUltima133: Long get() = synchronized(trava) { instanteDoUltimo133 }

    internal fun fotoAtual(): FotoDoCartao = synchronized(trava) { foto }

    /** Só para os testes: põe o estado do cartão como ficaria depois de uma sequência de eventos. */
    internal fun restaurar(nova: FotoDoCartao) {
        synchronized(trava) { foto = nova }
    }

    /**
     * Troca a linha do painel (ligar: uma nova; desligar: null). O pulso para: o laço antigo morre com
     * a linha antiga (A.4.6). A linha antiga é encerrada.
     */
    fun trocarLinha(nova: LinhaDoPainel?) {
        val antiga = synchronized(trava) {
            val antiga = linhaAtual
            linhaAtual = nova
            geracao++
            rodando = false
            antiga
        }
        if (antiga != null && antiga !== nova) protegido(registro, "encerrar a linha do painel", Unit) { antiga.encerrar() }
    }

    // ── A.2: mensagem recebida (thread de binder do serviço do painel) ──

    /** [valor] = o inteiro da mensagem; null se ela veio sem dados. */
    fun mensagemRecebida(id: Int, valor: Int?) {
        val projecao = this.projecao
        if (id in MENSAGENS_NO_LOG) {
            registro.info("painel → app: $id = $valor")
        } else if (protegido(registro, "mensagemDoPainelMereceDiagnostico", false) { projecao.mensagemDoPainelMereceDiagnostico(id) }) {
            registro.info("painel → app (diagnóstico): $id = $valor")
        }
        if (id != MSG_CARTAO && id != MSG_PULSO && id != MSG_MIDIA) return
        if (valor == null) {
            registro.aviso("painel → app: $id sem valor legível; ignorada")
            return
        }
        when (id) {
            MSG_CARTAO -> cartaoDoCarro(valor)
            MSG_PULSO -> pedidoDePulso(valor)
            MSG_MIDIA -> comandoDeMidia(valor)
        }
    }

    // ── A.3: 133, cartão informado pelo carro ──

    private fun cartaoDoCarro(novo: Int) {
        val protegerComProjecao = protegido(registro, "protegerCartaoDoArNaTransicao", false) {
            projecao.protegerCartaoDoArNaTransicao()
        }
        val agora = relogio.agora()
        val antes: FotoDoCartao
        val ignorar: Boolean
        synchronized(trava) {
            instanteDoUltimo133 = agora
            antes = foto
            ignorar = antes.ignorar(novo, agora, protegerComProjecao)
            if (!ignorar) {
                foto = foto.copy(cartao = novo)
                if (antes.cartao == ArbitroDoCartao.CARTAO_DO_AR && novo != ArbitroDoCartao.CARTAO_DO_AR) {
                    foto = soltarPosse(foto)
                }
            }
        }
        val detalhes = linkedMapOf<String, Any?>(
            "de" to antes.cartao,
            "para" to novo,
            "ultimaTecla" to antes.ultimaTecla?.name,
            "msDesdeTecla" to FotoDoCartao.desde(antes.instanteDaTecla, agora),
            "alvoSintetico" to antes.alvoSintetico,
            "msDesdeSintetico" to FotoDoCartao.desde(antes.instanteSintetico, agora),
            "protecaoDaProjecao" to protegerComProjecao,
            "protecaoSintetica" to antes.arDoApp,
        )
        if (ignorar) {
            registro.info("cartão do carro ignorado: ${antes.cartao} → $novo $detalhes")
            anotar("cartao_do_carro_ignorado", detalhes)
            return
        }
        publicador.publicarForaDaThread(EventoDoPainel.CartaoMudou(novo))
        registro.info("cartão do carro aceito: ${antes.cartao} → $novo")
        anotar("cartao_do_carro_aceito", detalhes)
    }

    // ── A.6 e A.7 (chamados pelo volante, B.4) ──

    override fun teclaDoPainelRecebida(tecla: TeclaDoVolante) {
        val agora = relogio.agora()
        synchronized(trava) { foto = foto.copy(ultimaTecla = tecla, instanteDaTecla = agora) }
    }

    override fun navegar(tecla: TeclaDoVolante) {
        var atual = cartaoAtual
        val noAnel = atual == ArbitroDoCartao.CARTAO_DO_CARRO || atual == ArbitroDoCartao.CARTAO_DO_MENU ||
            atual == ArbitroDoCartao.CARTAO_DO_AR
        if (!noAnel && RegrasDoMotorDoPainel.temaEsportivoAntigo(preferencias)) {
            val menu = menuAntigo
            atual = protegido(registro, "menuAntigo.cartaoAtual", atual) { menu.cartaoAtual() }
        }
        val proximo = ArbitroDoCartao.proximoCartao(atual, tecla)
        val agora = relogio.agora()
        synchronized(trava) {
            foto = foto.copy(
                cartao = proximo,
                alvoSintetico = proximo,
                instanteSintetico = agora,
                arDoApp = proximo == ArbitroDoCartao.CARTAO_DO_AR,
            )
        }
        registro.info("navegação sintética: ${tecla.name} $atual → $proximo")
        publicador.publicarForaDaThread(EventoDoPainel.CartaoMudou(proximo))
    }

    override fun soltarPosseDoAr() {
        synchronized(trava) { foto = soltarPosse(foto) }
    }

    /** A.7: o app deixa de ser dono do ar; navegação sintética = nunca. */
    private fun soltarPosse(f: FotoDoCartao) = f.copy(arDoApp = false, alvoSintetico = -1, instanteSintetico = NUNCA)

    // ── A.4: pulso de vida ──

    /** A.4.1. Volta na hora; o 75 e os passos saem na linha do painel. */
    fun iniciarPulso() {
        val linha: LinhaDoPainel
        val minha: Int
        synchronized(trava) {
            if (rodando) {
                registro.info("pulso do painel já está rodando")
                return
            }
            linha = linhaAtual ?: run {
                registro.erro("pulso do painel pedido sem a linha do painel; nada feito")
                return
            }
            rodando = true
            minha = geracao
        }
        registro.info("pulso do painel: iniciando (75 = 1 e depois 134 a cada ${INTERVALO_DO_PULSO_MS} ms)")
        try {
            linha.executar { if (vale(minha)) enviar(MSG_ANDROID_PRONTO, 1, logar = true) }
            linha.agendar(INTERVALO_DO_PULSO_MS) { passo(minha) }
        } catch (e: RejectedExecutionException) {
            synchronized(trava) { if (geracao == minha) rodando = false }
            registro.erro("pulso do painel: a linha já estava parada", e)
        }
    }

    /** A.4.2. */
    private fun passo(minha: Int) {
        if (!vale(minha)) return
        if (!integracaoLigada()) {
            synchronized(trava) { if (geracao == minha) rodando = false }
            registro.info("pulso do painel parou: integração com o painel desligada")
            return
        }
        pulsar()
        val linha = synchronized(trava) { if (geracao == minha && rodando) linhaAtual else null } ?: return
        try {
            linha.agendar(INTERVALO_DO_PULSO_MS) { passo(minha) }
        } catch (e: RejectedExecutionException) {
            registro.aviso("pulso do painel: a linha parou entre dois passos")
        }
    }

    private fun vale(minha: Int): Boolean = synchronized(trava) { geracao == minha && rodando }

    /** A.4.3/A.4.4: manda 134 = contador e anda o contador (mesmo sem interface). */
    private fun pulsar() {
        val valor: Int
        val numero: Long
        synchronized(trava) {
            valor = contador
            contador = if (valor >= CONTADOR_MAXIMO) 0 else valor + 1
            numero = ++pulsosEnviados
        }
        val logar = numero % LOG_A_CADA_PULSOS == 1L
        enviar(MSG_PULSO, valor, logar)
    }

    /** A.4.5: 134 vindo do painel. */
    private fun pedidoDePulso(valor: Int) {
        if (valor != PEDIDO_DE_PULSO) return
        if (!integracaoLigada()) {
            registro.info("painel pediu o pulso, mas a integração está desligada")
            return
        }
        val linha = linha ?: run {
            registro.erro("painel pediu o pulso sem a linha do painel; nada feito")
            return
        }
        try {
            linha.executar {
                pulsar()
                iniciarPulso()
            }
        } catch (e: RejectedExecutionException) {
            registro.aviso("painel pediu o pulso com a linha parada")
        }
    }

    // ── A.9: 135, comando de mídia do painel ──

    private fun comandoDeMidia(valor: Int) {
        val integracao = integracaoLigada()
        anotar("midia_do_painel", linkedMapOf("valor" to valor, "integracao" to integracao))
        if (integracao && (valor == 1 || valor == 2)) enviar(MSG_MIDIA, valor, logar = true)
        val projecao = this.projecao
        val tratou = protegido(registro, "comandoDeMidiaDoPainel", false) { projecao.comandoDeMidiaDoPainel(valor) }
        if (tratou) registro.info("mídia do painel $valor tratada pela projeção")
    }

    // ── Envio (A.4.4, D-P5) ──

    /** Manda [id] = [valor]; sem interface, descarta com log. Devolve se foi. */
    private fun enviar(id: Int, valor: Int, logar: Boolean): Boolean {
        val atual = canal
        if (atual == null) {
            if (logar) registro.aviso("app → painel: $id = $valor descartada (painel não conectado)")
            return false
        }
        return try {
            atual.enviar(id, valor)
            if (logar) registro.info("app → painel: $id = $valor")
            true
        } catch (e: Exception) {
            registro.erro("app → painel: $id = $valor falhou", e)
            false
        }
    }

    private fun integracaoLigada(): Boolean =
        preferencias.booleano(Preferencias.ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION, false)

    private fun anotar(evento: String, detalhes: Map<String, Any?>) {
        protegido(registro, "diário ($evento)", Unit) { diario(evento, detalhes) }
    }
}
