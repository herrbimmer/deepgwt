package br.com.deepgwt.tela3d.midia

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Handler
import br.com.deepgwt.R

/**
 * Qual projeção (Android Auto ou CarPlay) está ligada (caso J). Um laço de fundo de 2 s consulta o
 * estado dos enlaces e as faixas do publicador e avisa mudanças na thread principal.
 */
class PresencaDeProjecao(
    contexto: Context,
    private val principal: Handler,
    private val faixas: FaixasDeProjecao,
) {
    private val aplicacao: Context = contexto.applicationContext

    private val laco = LacoDeFundo("deepgwt-presenca")

    private val ligacaoAndroidAuto = LigacaoComServico(
        contexto = aplicacao,
        nome = "projeção do Android Auto",
        intencoes = listOf(
            Intent(ACAO_DO_ANDROID_AUTO)
                .setClassName(PACOTE_DO_SERVICO_ANDROID_AUTO, CLASSE_DO_SERVICO_ANDROID_AUTO),
        ),
    )

    private val ligacaoCarPlay = LigacaoComServico(
        contexto = aplicacao,
        nome = "CarPlay (presença)",
        intencoes = listOf(
            LigacaoComServico.intencaoDeComponente(ProtocoloDoCarPlay.PACOTE, ProtocoloDoCarPlay.CLASSE_DO_SERVICO),
            Intent(ProtocoloDoCarPlay.ACAO_DO_SERVICO).setPackage(ProtocoloDoCarPlay.PACOTE),
        ),
    )

    @Volatile
    var atual: TipoDeProjecao = TipoDeProjecao.NENHUMA
        private set

    @Volatile
    private var aoMudar: ((TipoDeProjecao) -> Unit)? = null

    @Volatile
    private var ativa = false

    /** Inicia o laço (idempotente); [aoMudar] é chamado na thread principal. */
    @Synchronized
    fun iniciar(aoMudar: (TipoDeProjecao) -> Unit) {
        this.aoMudar = aoMudar
        if (ativa) return
        ativa = true
        laco.iniciar(PERIODO_MS) { ciclo() }
    }

    /** Para o laço, encerra a thread e solta as ligações. */
    @Synchronized
    fun parar() {
        ativa = false
        aoMudar = null
        laco.parar()
        ligacaoAndroidAuto.soltar()
        ligacaoCarPlay.soltar()
    }

    fun rotulo(tipo: TipoDeProjecao): String = when (tipo) {
        TipoDeProjecao.ANDROID_AUTO -> "Android Auto"
        TipoDeProjecao.CARPLAY -> "CarPlay"
        TipoDeProjecao.NENHUMA -> ""
    }

    fun icone(tipo: TipoDeProjecao): Drawable? {
        val recurso = when (tipo) {
            TipoDeProjecao.ANDROID_AUTO -> R.drawable.ic_android_auto
            TipoDeProjecao.CARPLAY -> R.drawable.ic_carplay
            TipoDeProjecao.NENHUMA -> return null
        }
        return try {
            aplicacao.getDrawable(recurso)
        } catch (_: Exception) {
            null
        }
    }

    /** Costura para janelas: transação oneway, sem argumentos, no serviço de projeção do Android Auto (J8). */
    fun enviarAoAndroidAuto(codigo: Int) {
        val binder = ligacaoAndroidAuto.garantir()
        if (binder == null) {
            Diario.info("serviço do Android Auto ainda não está ligado; comando $codigo descartado")
            return
        }
        try {
            TransacoesDeBinder.semResposta(binder, DESCRITOR_DO_ANDROID_AUTO, codigo)
        } catch (e: Exception) {
            Diario.aviso("comando $codigo ao Android Auto falhou", e)
        }
    }

    private fun ciclo() {
        val enlaceCarPlay = ResolucaoDePresenca.enlaceDoCarPlayAtivo(lerCarPlay())
        val enlaceAndroidAuto = ResolucaoDePresenca.enlaceDoAndroidAutoAtivo(lerAndroidAuto())
        val novo = ResolucaoDePresenca.resolver(
            enlaceCarPlay = enlaceCarPlay,
            enlaceAndroidAuto = enlaceAndroidAuto,
            faixaCarPlay = faixas.temFaixaDoCarPlay,
            faixaAndroidAuto = faixas.temFaixaDoAndroidAuto,
        )
        if (novo == atual) return
        Diario.info("projeção: $atual -> $novo")
        atual = novo
        principal.post { if (ativa) aoMudar?.invoke(novo) }
    }

    private fun lerAndroidAuto(): Int? = lerEstado(ligacaoAndroidAuto, DESCRITOR_DO_ANDROID_AUTO, TRANSACAO_ESTADO_DO_ANDROID_AUTO)

    private fun lerCarPlay(): Int? = lerEstado(
        ligacaoCarPlay, ProtocoloDoCarPlay.DESCRITOR_DO_SERVICO, ProtocoloDoCarPlay.TRANSACAO_ESTADO_DO_ENLACE,
    )

    /** Transação síncrona do enlace; o cabeçalho de exceção é lido e qualquer exceção dele ignorada. */
    private fun lerEstado(ligacao: LigacaoComServico, descritor: String, codigo: Int): Int? {
        val binder = ligacao.garantir() ?: return null
        return try {
            TransacoesDeBinder.inteira(binder, descritor, codigo, ignorarExcecaoDoCabecalho = true)
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val PERIODO_MS = 2_000L

        const val PACOTE_DO_SERVICO_ANDROID_AUTO = "com.ts.androidauto.projectionservice"
        const val CLASSE_DO_SERVICO_ANDROID_AUTO = "com.ts.androidauto.projectionservice.AndroidAutoService"
        const val ACAO_DO_ANDROID_AUTO = "com.ts.androidauto.action.AndroidAutoService"
        const val DESCRITOR_DO_ANDROID_AUTO = "com.ts.androidauto.sdk.aidl.LinkCommand"
        const val TRANSACAO_ESTADO_DO_ANDROID_AUTO = 21
    }
}
