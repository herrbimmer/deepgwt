package br.com.opengw.monitores

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Monitor do "tocando agora" do CarPlay da central (`extras-monitores.md`, parte B).
 *
 * Liga-se ao serviço `com.ts.carplay`, assina os quadros do iPhone, entrega à barra (sempre na
 * thread principal) e manda próxima/anterior/tocar/pausar/buscar ao iPhone. A lógica fica em
 * [NucleoDoMonitorDoCarPlay]; aqui só o que é Android (ligação, binder, decodificação).
 */
class MonitorDoCarPlay(
    contexto: Context,
    /** Corrotinas do monitor (decodificação da capa); quem chama cancela ao parar. */
    private val escopo: CoroutineScope,
    aoAtualizar: (TocandoNoCarPlay) -> Unit,
) {

    private val registro = RegistroNoLogcat(ETIQUETA)
    private val aplicacao: Context = contexto.applicationContext ?: contexto

    private val callback = CallbackDoCarPlay(registro) { quadro -> nucleo.receberQuadro(quadro) }

    private val ligacao = LigacaoAndroidDoCarPlay(
        contexto = aplicacao,
        registro = registro,
        aoConectar = { binder ->
            nucleo.conectou(binder?.let { ServicoDoCarPlayPorBinder(it, callback, registro) })
        },
        aoDesconectar = { nucleo.desconectou() },
        aoMorrer = { nucleo.ligacaoMorreu() },
    )

    private val produtorDeCapa = ProdutorDeCapaDoCarPlay(aplicacao, registro)

    private val nucleo: NucleoDoMonitorDoCarPlay = NucleoDoMonitorDoCarPlay(
        agenda = AgendaDoAndroid,
        ligacao = ligacao,
        capaDoQuadro = produtorDeCapa::obter,
        emSegundoPlano = { tarefa -> escopo.launch(Dispatchers.IO) { tarefa() } },
        registro = registro,
        aoAtualizar = aoAtualizar,
        escopoAtivo = { escopo.isActive },
    )

    /** Liga ao serviço do CarPlay, assina e começa a vigiar. Pode ser chamado de novo (sem efeito). */
    fun iniciar() = nucleo.iniciar()

    /** Cancela tudo, desassina e desliga. Depois disto nenhum aviso sai. Pode iniciar de novo. */
    fun parar() = nucleo.parar()

    /** Pede ao iPhone para ir a [posicaoMs]. Verdadeiro = pedido enviado. */
    fun buscar(posicaoMs: Long): Boolean = nucleo.buscar(posicaoMs)

    fun proxima(): Boolean = nucleo.proxima()

    fun anterior(): Boolean = nucleo.anterior()

    /** [estaTocando] é o que a interface acredita agora: tocando → pausa; senão → toca. */
    fun tocarOuPausar(estaTocando: Boolean): Boolean = nucleo.tocarOuPausar(estaTocando)

    internal companion object {
        const val ETIQUETA = "OpenGW.CarPlay"

        // Fatos da central (B.1).
        const val PACOTE = "com.ts.carplay"
        const val CLASSE_DO_SERVICO = "com.ts.carplay.CarPlayService"
        const val ACAO_DO_SERVICO = "com.ts.carplay.action.CarPlayService"
        const val DESCRITOR_DO_SERVICO = "com.ts.carplay.common.aidl.ICarPlayService"
        const val DESCRITOR_DO_CALLBACK = "com.ts.carplay.common.aidl.INowPlayingUpdateCallback"

        const val TRANSACAO_INICIAR_AVISOS = 9
        const val TRANSACAO_PARAR_AVISOS = 10
        const val TRANSACAO_TECLA_HID = 25
        const val TRANSACAO_POSICAO = 31

        const val CALLBACK_AVISO_DO_TOCANDO = 1
        const val CALLBACK_LISTA = 2
    }
}

/**
 * Tenta nesta ordem: [primeira] (componente explícito) e, só se ela devolveu falso ou lançou,
 * [segunda] (ação + pacote). Verdadeiro = alguma pediu a ligação.
 */
internal fun ligarPelaOrdem(primeira: () -> Boolean, segunda: () -> Boolean): Boolean {
    val deuCerto = try {
        primeira()
    } catch (e: Exception) {
        false
    }
    if (deuCerto) return true
    return try {
        segunda()
    } catch (e: Exception) {
        false
    }
}

/** O `ServiceConnection` do serviço do CarPlay: repassa os eventos ao núcleo. */
internal class LigacaoAndroidDoCarPlay(
    private val contexto: Context,
    private val registro: RegistroDosMonitores,
    private val aoConectar: (IBinder?) -> Unit,
    private val aoDesconectar: () -> Unit,
    private val aoMorrer: () -> Unit,
) : ServiceConnection, LigacaoComOServico {

    override fun ligar(): Boolean = ligarPelaOrdem(
        primeira = {
            pedir(Intent().setComponent(ComponentName(MonitorDoCarPlay.PACOTE, MonitorDoCarPlay.CLASSE_DO_SERVICO)))
        },
        segunda = {
            pedir(Intent(MonitorDoCarPlay.ACAO_DO_SERVICO).setPackage(MonitorDoCarPlay.PACOTE))
        },
    )

    private fun pedir(intencao: Intent): Boolean {
        val pedida = contexto.bindService(intencao, this, Context.BIND_AUTO_CREATE)
        // Uma ligação recusada ainda precisa ser solta, ou o sistema a guarda.
        if (!pedida) desfazer()
        return pedida
    }

    override fun desfazer() {
        try {
            contexto.unbindService(this)
        } catch (e: Exception) {
            // Não estava ligada: nada a fazer.
        }
    }

    override fun onServiceConnected(nome: ComponentName?, binder: IBinder?) {
        aoConectar(binder)
    }

    override fun onServiceDisconnected(nome: ComponentName?) {
        aoDesconectar()
    }

    override fun onBindingDied(nome: ComponentName?) {
        registro.aviso("ligação com o CarPlay morreu")
        aoMorrer()
    }

    override fun onNullBinding(nome: ComponentName?) {
        registro.aviso("o CarPlay devolveu binder nulo")
        aoMorrer()
    }
}

/** As quatro transações da central, sobre um binder de verdade. Todas unidirecionais. */
internal class ServicoDoCarPlayPorBinder(
    private val binder: IBinder,
    private val callback: IBinder,
    private val registro: RegistroDosMonitores,
) : ServicoDoCarPlay {

    override val vivo: Boolean get() = binder.isBinderAlive

    override fun iniciarAvisos(): Boolean =
        enviar(MonitorDoCarPlay.TRANSACAO_INICIAR_AVISOS) { it.writeStrongBinder(callback) }

    override fun pararAvisos(): Boolean =
        enviar(MonitorDoCarPlay.TRANSACAO_PARAR_AVISOS) { it.writeStrongBinder(callback) }

    override fun enviarTecla(tecla: Int, acao: Int): Boolean =
        enviar(MonitorDoCarPlay.TRANSACAO_TECLA_HID) {
            it.writeInt(tecla)
            it.writeInt(acao)
        }

    override fun definirPosicao(posicaoMs: Long): Boolean =
        enviar(MonitorDoCarPlay.TRANSACAO_POSICAO) { it.writeLong(posicaoMs) }

    private fun enviar(codigo: Int, corpo: (Parcel) -> Unit): Boolean {
        val dados = Parcel.obtain()
        return try {
            dados.writeInterfaceToken(MonitorDoCarPlay.DESCRITOR_DO_SERVICO)
            corpo(dados)
            binder.transact(codigo, dados, null, IBinder.FLAG_ONEWAY)
        } catch (e: Exception) {
            registro.erro("transação $codigo do CarPlay", e)
            false
        } finally {
            dados.recycle()
        }
    }
}

/**
 * O nosso callback, um `Binder` com o descritor da central. A central chama (unidirecional): 1 =
 * "tocando agora"; 2 = lista (só aceita); o pedido do descritor é respondido pelo próprio `Binder`.
 */
internal class CallbackDoCarPlay(
    private val registro: RegistroDosMonitores,
    private val aoQuadro: (QuadroDoCarPlay?) -> Unit,
) : Binder() {

    init {
        attachInterface(null, MonitorDoCarPlay.DESCRITOR_DO_CALLBACK)
    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        when (code) {
            MonitorDoCarPlay.CALLBACK_AVISO_DO_TOCANDO -> {
                try {
                    data.enforceInterface(MonitorDoCarPlay.DESCRITOR_DO_CALLBACK)
                    aoQuadro(QuadroDoCarPlay.lerComPresenca(LeitorDeParcelAndroid(data)))
                } catch (e: Exception) {
                    registro.erro("quadro do CarPlay", e)
                }
                return true
            }
            MonitorDoCarPlay.CALLBACK_LISTA -> return true // ignorado: só aceitar
        }
        return super.onTransact(code, data, reply, flags) // inclui o pedido do descritor
    }
}
