package br.com.deepgwt.tela3d.integracao

import android.content.Context
import android.graphics.drawable.Drawable
import br.com.deepgwt.R
import br.com.deepgwt.tela3d.cartoes.AnfitriaoDosCartoes
import br.com.deepgwt.tela3d.cartoes.ControlesDeMidiaDosCartoes
import br.com.deepgwt.tela3d.catalogo.CatalogoDeApps
import br.com.deepgwt.tela3d.catalogo.ProjecaoParaCatalogo
import br.com.deepgwt.tela3d.catalogo.iconeSubstitutoDoPacote
import br.com.deepgwt.tela3d.faixa.PresencaDeProjecaoParaFaixa
import br.com.deepgwt.tela3d.faixa.TemaDoCromo
import br.com.deepgwt.tela3d.janelas.Janelas
import br.com.deepgwt.tela3d.midia.ControleDeMidia
import br.com.deepgwt.tela3d.midia.DestinoDoTocandoAgora
import br.com.deepgwt.tela3d.midia.PacotesDeProjecao
import br.com.deepgwt.tela3d.midia.PresencaDeProjecao
import br.com.deepgwt.tela3d.midia.TocandoAgora
import br.com.deepgwt.tela3d.ponte.MidiaParaAPonte
import br.com.deepgwt.tela3d.ponte.PaginaDaTela3d
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList
import br.com.deepgwt.tela3d.catalogo.TipoDeProjecao as ProjecaoDoCatalogo
import br.com.deepgwt.tela3d.faixa.TipoDeProjecao as ProjecaoDaFaixa
import br.com.deepgwt.tela3d.midia.TipoDeProjecao as ProjecaoDaMidia

/*
 * Os adaptadores do módulo de mídia: o publicador do "tocando agora" para a ponte, a presença de
 * projeção para janelas, faixa e catálogo, e o que os cartões chamam da mídia.
 */

/**
 * A presença de projeção é uma só (a mídia a mede), mas três vizinhos querem ser avisados quando ela
 * muda e a mídia guarda um único ouvinte: esta peça distribui.
 */
internal class CentralDeProjecao(private val presenca: PresencaDeProjecao) {
    private val ouvintes = CopyOnWriteArrayList<(ProjecaoDaMidia) -> Unit>()

    val atual: ProjecaoDaMidia get() = presenca.atual

    fun aoMudar(ouvinte: (ProjecaoDaMidia) -> Unit) {
        ouvintes.add(ouvinte)
    }

    /** Idempotente: a mídia só liga o laço uma vez. */
    fun iniciar() = presenca.iniciar { tipo -> for (o in ouvintes) o(tipo) }

    fun parar() = presenca.parar()
}

/**
 * O publicador do "tocando agora" para a ponte. O visualizador de áudio NUNCA é criado (as barras
 * foram descartadas): `aoRetomar` e `aoDestruir` não têm o que fazer.
 */
internal class MidiaDaPonte(
    private val tocando: TocandoAgora,
    private val central: CentralDeProjecao,
) : MidiaParaAPonte {

    override fun anexar(aoAtualizar: (payloadJson: String) -> Unit, aoPosicao: (posicaoMs: Long) -> Unit) {
        tocando.anexar(object : DestinoDoTocandoAgora {
            override fun aoMudarFaixa(payload: JSONObject) = aoAtualizar(payload.toString())

            override fun aoMudarPosicao(posicaoMs: Long) = aoPosicao(posicaoMs)
        })
    }

    /** A cada retomada, na carga da página e no plano B de 20 s: tudo idempotente. */
    override fun iniciar() {
        tocando.iniciar()
        central.iniciar()
    }

    override fun empurrarAgora() = tocando.reenviarAgora()

    override fun parar() {
        tocando.parar()
        central.parar()
    }

    override fun proxima() = tocando.proxima()

    override fun anterior() = tocando.anterior()

    override fun tocarOuPausar() = tocando.tocarOuPausar()
}

/** A projeção para o catálogo: a regra de pacote é da mídia, e os ícones também. */
internal class ProjecaoDoCatalogoDaTela3d(private val presenca: PresencaDeProjecao) : ProjecaoParaCatalogo {
    override fun ehPacoteDeProjecao(pacote: String?): Boolean = PacotesDeProjecao.ehDeProjecao(pacote)

    override fun iconeDaProjecao(tipo: ProjecaoDoCatalogo): Drawable? =
        presenca.icone(Conversoes.projecaoDoCatalogoParaAMidia(tipo))
}

/** A projeção para a faixa. */
internal class ProjecaoDaFaixaDaTela3d(
    private val central: CentralDeProjecao,
    private val presenca: PresencaDeProjecao,
) : PresencaDeProjecaoParaFaixa {

    override fun atual(): ProjecaoDaFaixa = Conversoes.projecaoDaMidiaParaAFaixa(central.atual)

    override fun icone(tipo: ProjecaoDaFaixa): Drawable? = presenca.icone(paraAMidia(tipo))

    override fun rotulo(tipo: ProjecaoDaFaixa): String = presenca.rotulo(paraAMidia(tipo))

    override fun aoMudar(ouvinte: (ProjecaoDaFaixa) -> Unit) {
        central.aoMudar { tipo -> ouvinte(Conversoes.projecaoDaMidiaParaAFaixa(tipo)) }
    }

    private fun paraAMidia(tipo: ProjecaoDaFaixa): ProjecaoDaMidia = when (tipo) {
        ProjecaoDaFaixa.NENHUMA -> ProjecaoDaMidia.NENHUMA
        ProjecaoDaFaixa.ANDROID_AUTO -> ProjecaoDaMidia.ANDROID_AUTO
        ProjecaoDaFaixa.CARPLAY -> ProjecaoDaMidia.CARPLAY
    }
}

/** Os três botões do cartão de mídia. Sem visualizador, "mudou o estado de tocar" não tem destinatário. */
internal class ControlesDeMidiaDosCartoesDaTela3d(private val controle: ControleDeMidia) : ControlesDeMidiaDosCartoes {
    override fun anterior() = controle.anterior()

    override fun tocarOuPausar() = controle.tocarOuPausar()

    override fun proxima() = controle.proxima()

    override fun aoMudarTocando(tocando: Boolean) {}
}

/** O que os cartões precisam do resto do app, reunido numa peça só. */
internal class AnfitriaoDosCartoesDaTela3d(
    override val contexto: Context,
    private val faixaTema: () -> TemaDoCromo,
    private val pagina: PaginaDaTela3d,
    private val janelas: Janelas,
    private val catalogo: CatalogoDeApps,
    private val presenca: PresencaDeProjecao,
    override val midia: ControlesDeMidiaDosCartoes,
) : AnfitriaoDosCartoes {

    override val tema: TemaDoCromo get() = faixaTema()

    override fun enviarComandoAoDock(comando: String) = pagina.enviarComandoDoDock(comando)

    /** A faixa já reavalia a superfície ao tratar o mesmo pacote de indicadores (`railEdit`): nada a fazer. */
    override fun aoMudarEdicao(editando: Boolean) {}

    /** O caminho do dock decide sozinho; para pacote de projeção as janelas abrem a projeção. */
    override fun abrirApp(pacote: String, rotulo: String) = janelas.abrirComoODock(pacote, rotulo)

    /**
     * Ícone do chip de origem: o que o usuário escolheu para o app; senão o da projeção (Android Auto ou
     * CarPlay) se a fonte for uma delas; senão nenhum (o cartão usa o ícone do payload).
     */
    override fun iconeDeOrigem(pacote: String?, rotulo: String?): Drawable? {
        catalogo.iconeSubstitutoDoPacote(pacote)?.let { return it }
        return when {
            PacotesDeProjecao.ehFonteAndroidAuto(pacote, rotulo) ->
                presenca.icone(ProjecaoDaMidia.ANDROID_AUTO) ?: recurso(R.drawable.ic_android_auto_default)
            PacotesDeProjecao.ehFonteCarPlay(pacote, rotulo) ->
                presenca.icone(ProjecaoDaMidia.CARPLAY) ?: recurso(R.drawable.ic_carplay_default)
            else -> null
        }
    }

    private fun recurso(id: Int): Drawable? = try {
        contexto.getDrawable(id)
    } catch (e: Exception) {
        null
    }
}
