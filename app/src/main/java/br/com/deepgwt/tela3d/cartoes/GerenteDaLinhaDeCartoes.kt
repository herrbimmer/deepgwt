package br.com.deepgwt.tela3d.cartoes

import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import br.com.deepgwt.tela3d.faixa.Densidade
import br.com.deepgwt.tela3d.midia.PayloadDeMidia
import br.com.deepgwt.tela3d.ponte.CartaoInferior
import br.com.deepgwt.tela3d.ponte.ConfigDosCartoes
import br.com.deepgwt.tela3d.ponte.IndicadoresDoDock
import br.com.deepgwt.tela3d.ponte.LinhaDeMenu
import org.json.JSONObject

/**
 * A linha de cartões rápidos: monta, reconstrói e atualiza os cartões a partir do que a página manda, cuida
 * do menu rápido, da edição da linha e do payload de mídia. Tudo na thread principal.
 *
 * A linha só mexe nos próprios cartões: os filhos que a faixa inseriu (o "Espaço de trabalho") ficam onde
 * estão, e o preenchimento esquerdo continua sendo da faixa.
 */
class GerenteDaLinhaDeCartoes(private val anfitriao: AnfitriaoDosCartoes) : CartoesParaAFaixa, CartoesParaAPonte {
    private val d = Densidade(anfitriao.contexto)
    private val linha = LinearLayout(anfitriao.contexto)
    private var container: HorizontalScrollView? = null
    private val meusCartoes = ArrayList<CartaoDaLinha>()
    private var config: ConfigDosCartoes? = null
    private var emEdicao = false
    private var ultimoFoco = ""
    private var ultimoPayload: PayloadDeMidia? = null
    private var versaoDoTema = anfitriao.tema.versao

    private val menu = MenuDeCartoes(anfitriao, d)

    private val servicos = object : ServicosDoCartao {
        override fun enviar(comando: String) = anfitriao.enviarComandoAoDock(comando)
        override fun abrirMenuRapido(cartao: CartaoDaLinha, linhas: List<LinhaDeMenu>) {
            menu.abrir(cartao, linhas, separarUltima = true) { anfitriao.enviarComandoAoDock(it) }
        }
    }

    private val edicao = EdicaoDaLinha(
        d = d,
        cartoes = { meusCartoes },
        container = { container },
        enviar = { anfitriao.enviarComandoAoDock(it) },
        abrirMenuDeEdicao = { cartao ->
            // lê o descritor ATUAL do cartão
            val linhas = cartao.descritor?.menuDeEdicao.orEmpty()
            if (linhas.isNotEmpty()) menu.abrir(cartao, linhas, separarUltima = false) { anfitriao.enviarComandoAoDock(it) }
        },
    )

    init {
        linha.orientation = LinearLayout.HORIZONTAL
        linha.gravity = Gravity.TOP or Gravity.START
        linha.clipChildren = false
        linha.clipToPadding = false
        linha.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
        linha.setPadding(
            linha.paddingLeft, d.px(MedidasDaLinha.PREENCHIMENTO_SUPERIOR_DP),
            d.px(MedidasDaLinha.PREENCHIMENTO_DIREITO_DP), d.px(MedidasDaLinha.PREENCHIMENTO_INFERIOR_DP),
        )
        construirConjuntoDeReserva()
    }

    // --- CartoesParaAFaixa ---

    override fun criarLinha(): LinearLayout = linha

    override fun definirContainer(container: HorizontalScrollView) {
        this.container = container
    }

    override val editando: Boolean get() = emEdicao

    override fun aplicarIndicadores(ind: IndicadoresDoDock) {
        // 1. tema e acento: a faixa já atualizou; reestiliza uma vez se mudou (nunca reconstrói)
        if (anfitriao.tema.versao != versaoDoTema) reestilizar()
        // 2. cartões
        val nova = ind.cartoes
        if (nova != null) {
            aplicarConfiguracao(nova)
        } else if (config != null) {
            config = null
            reconstruir()
        }
        // 3. edição
        ind.editandoTrilho?.let { definirEdicao(it) }
        // 4. foco
        ind.focoDoTrilho?.let { foco ->
            if (foco != ultimoFoco) {
                ultimoFoco = foco
                if (foco.isNotEmpty()) focar(foco)
            }
        }
        // 5. resumos do conjunto de reserva
        ind.resumoDoClima?.let { r -> cartaoDeTextoDaReserva(CHAVE_CLIMA)?.definirResumo(r.texto, r.descricao) }
        ind.resumoDoConsumo?.let { r -> cartaoDeTextoDaReserva(CHAVE_ENERGIA)?.definirResumo(r.texto, r.descricao) }
    }

    override fun aoMudarTema() = reestilizar()

    override fun aoMudarAcento() = reestilizar()

    override fun reconstruirLinha() = reconstruir()

    override fun fecharMenus() = menu.fechar()

    override fun redesenharChipDeOrigemDoApp(pacote: String) {
        for (c in meusCartoes) {
            when (c) {
                is CartaoDeMidia -> if (c.usaOPacote(pacote)) c.redesenharChip()
                is CartaoVisual -> if (c.usaOPacote(pacote)) c.redesenharIconeDeOrigem()
                else -> Unit
            }
        }
    }

    // --- CartoesParaAPonte ---

    override fun atualizarRelogios() {
        for (c in meusCartoes) if (c is CartaoDeRelogio) c.atualizarRelogio()
    }

    override fun atualizarCartaoDeMidia(payloadJson: String) {
        val p = try {
            PayloadDeMidia.deJson(JSONObject(payloadJson))
        } catch (_: Exception) {
            return
        }
        ultimoPayload = p
        for (c in meusCartoes) if (c is CartaoDeMidia) c.aplicarPayload(p)
    }

    // --- montagem ---

    private fun cartaoDeTextoDaReserva(chave: String): CartaoDeTexto? =
        meusCartoes.firstOrNull { it.tag == chave } as? CartaoDeTexto

    private fun limparMeusCartoes() {
        menu.fechar()
        edicao.desligar()
        for (c in meusCartoes) linha.removeView(c)
        meusCartoes.clear()
    }

    private fun adicionar(c: CartaoDaLinha) {
        val params = LinearLayout.LayoutParams(d.px(c.larguraEmDp), d.px(MedidasDaLinha.ALTURA_DP)).apply {
            marginEnd = d.px(MedidasDaLinha.MARGEM_DIREITA_DP)
        }
        linha.addView(c, params)
        meusCartoes += c
    }

    /** A1/A2: o conjunto de reserva (clima, energia, mídia sem descritor); não entra na edição. */
    private fun construirConjuntoDeReserva() {
        val clima = CartaoDeTexto(
            anfitriao.contexto, anfitriao, d, "CLIMATE", RESERVA_CLIMA,
            "Climate summary. Opens the climate page when available", "openClimate",
            MedidasDaLinha.LARGURA_RESERVA_CLIMA_DP, servicos,
        )
        clima.tag = CHAVE_CLIMA
        val energia = CartaoDeTexto(
            anfitriao.contexto, anfitriao, d, "ENERGIA", RESERVA_ENERGIA,
            "Resumo de energia. Abre a tela de energia", "openConsumption",
            MedidasDaLinha.LARGURA_RESERVA_ENERGIA_DP, servicos,
        )
        energia.tag = CHAVE_ENERGIA
        adicionar(clima)
        adicionar(energia)
        val midia = CartaoDeMidia(anfitriao.contexto, anfitriao, d, servicos, null)
        adicionar(midia)
        ultimoPayload?.let { midia.aplicarPayload(it) }
    }

    /** A3: reconstrói a linha do zero com os cartões visíveis da configuração (ou o conjunto de reserva). */
    private fun reconstruir() {
        limparMeusCartoes()
        val c = config
        if (c == null) {
            construirConjuntoDeReserva()
        } else {
            for (descritor in RegrasDaLinha.visiveis(c)) criarCartao(descritor)?.let { adicionar(it) }
            ultimoPayload?.let { p -> for (m in meusCartoes) if (m is CartaoDeMidia) m.aplicarPayload(p) }
        }
        if (emEdicao) edicao.ligar()
    }

    private fun criarCartao(c: CartaoInferior): CartaoDaLinha? {
        val contexto = anfitriao.contexto
        val cartao = when (c.id) {
            "clock" -> CartaoDeRelogio(contexto, anfitriao, d, servicos, c)
            "media" -> CartaoDeMidia(contexto, anfitriao, d, servicos, c)
            else -> CartaoVisual(contexto, anfitriao, d, servicos, c)
        }
        cartao.configurado = true
        return cartao
    }

    private fun aplicarConfiguracao(nova: ConfigDosCartoes) {
        val reconstruirAgora = RegrasDaLinha.deveReconstruir(config, nova)
        config = nova
        if (reconstruirAgora) reconstruir() else atualizarValores(nova)
    }

    /** A4: só os valores, sem reconstruir. */
    private fun atualizarValores(nova: ConfigDosCartoes) {
        val visiveis = RegrasDaLinha.visiveis(nova)
        val cartoesDaConfig = meusCartoes.filter { it.configurado }
        for ((i, c) in cartoesDaConfig.withIndex()) {
            val descritor = visiveis.getOrNull(i) ?: continue
            when (c) {
                is CartaoVisual -> c.aplicar(descritor)
                is CartaoDeRelogio -> c.aplicar(descritor)
                is CartaoDeMidia -> c.definirDescritor(descritor)
                else -> Unit
            }
        }
    }

    // --- tema (A5) ---

    private fun reestilizar() {
        versaoDoTema = anfitriao.tema.versao
        menu.fechar()
        for (c in meusCartoes) c.reaplicarTema()
    }

    // --- edição ---

    private fun definirEdicao(ligar: Boolean) {
        if (ligar == emEdicao) return
        emEdicao = ligar
        menu.fechar()
        if (ligar) {
            edicao.ligar()
        } else {
            edicao.desligar()
            reconstruir()
        }
        anfitriao.aoMudarEdicao(ligar)
    }

    private fun focar(id: String) {
        val alvo = meusCartoes.firstOrNull { it.configurado && it.descritor?.id == id } ?: return
        edicao.focar(alvo)
    }

    private companion object {
        const val CHAVE_CLIMA = "reserva-clima"
        const val CHAVE_ENERGIA = "reserva-energia"
        const val RESERVA_CLIMA = "— °C  ·  Fan —  ·  AUTO —"
        const val RESERVA_ENERGIA = "—"
    }
}
