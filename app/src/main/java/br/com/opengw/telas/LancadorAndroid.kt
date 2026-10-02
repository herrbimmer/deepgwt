package br.com.opengw.telas

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.hardware.display.DisplayManager
import android.hardware.usb.UsbDevice
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.Parcelable
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import br.com.opengw.nucleo.OpenGw
import br.com.opengw.nucleo.Preferencia
import br.com.opengw.nucleo.Shell
import br.com.opengw.veiculo.Veiculo
import br.com.opengw.BuildConfig
import br.com.opengw.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/** A instância única do lançador no app, montada com as costuras reais na primeira chamada. */
internal object Lancador {
    val nucleo: NucleoDoLancador by lazy { NucleoDoLancador(ambienteAndroid()) }

    private fun ambienteAndroid(): AmbienteDoLancador {
        val registro = RegistroNoLogcat
        val fundo = FundoEmCorrotinas(registro)
        return AmbienteDoLancador(
            shell = ShellPeloShizuku,
            relogio = RelogioDoAndroid,
            prefs = PreferenciasDoApp,
            sistema = SistemaAndroid,
            carro = CarroPeloVeiculo,
            canalAa = CanalDoAndroidAutoAndroid(RelogioDoAndroid, registro, fundo),
            canalCp = CanalDoCarPlayAndroid(RelogioDoAndroid, registro),
            fundo = fundo,
            registro = registro,
        )
    }
}

/** Nome e ícone de um app (2-B), com os recursos fixos do app. */
internal object InfoDosApps {
    fun infoDoApp(contexto: Context, pacote: String, nome: String?): InfoDoApp {
        val pm = contexto.packageManager
        val rotulo = RotulosDosApps.rotulo(pacote, nome) { p ->
            try {
                pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString()
            } catch (e: Exception) {
                null
            }
        }
        val recurso = when (RotulosDosApps.iconeFixo(pacote)) {
            IconeFixo.ANDROID_AUTO -> R.drawable.ic_android_auto_default
            IconeFixo.CARPLAY -> R.drawable.ic_carplay_default
            IconeFixo.YOUTUBE -> R.drawable.ic_youtube_default
            IconeFixo.YOUTUBE_MUSIC -> R.drawable.ic_youtube_music_default
            IconeFixo.GWM -> R.drawable.ic_opengw_marca
            null -> null
        }
        val icone = try {
            if (recurso != null) ContextCompat.getDrawable(contexto, recurso) else pm.getApplicationIcon(pacote)
        } catch (e: Exception) {
            null
        }
        return InfoDoApp(rotulo, icone)
    }

    /** A classe da atividade de abertura do pacote, pelo `PackageManager` do contexto dado. */
    fun atividadeDeAbertura(contexto: Context, pacote: String): String? =
        try {
            contexto.packageManager.getLaunchIntentForPackage(pacote)?.component?.className
        } catch (e: Exception) {
            null
        }
}

private object ShellPeloShizuku : ShellDoLancador {
    override fun rodar(comando: String): String = Shell.executar(arrayOf("sh", "-c", "$comando 2>&1"))
    override fun disponivel(): Boolean = Shell.disponivel()
}

private object RelogioDoAndroid : RelogioDoLancador {
    override fun monotonico(): Long = SystemClock.elapsedRealtime()
    override fun parede(): Long = System.currentTimeMillis()
    override suspend fun esperar(ms: Long) = delay(ms)
}

private object PreferenciasDoApp : PreferenciasDoLancador {
    private const val ARQUIVO = "opengw_prefs"

    private val prefs get() = OpenGw.contextoProtegido.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)

    override fun texto(chave: Preferencia): String? =
        try {
            prefs.getString(chave.chave, null)
        } catch (e: ClassCastException) {
            null
        }

    override fun inteiro(chave: Preferencia): Int? =
        try {
            val p = prefs
            if (p.contains(chave.chave)) p.getInt(chave.chave, 0) else null
        } catch (e: ClassCastException) {
            null
        }

    override fun booleano(chave: Preferencia, padrao: Boolean): Boolean =
        try {
            prefs.getBoolean(chave.chave, padrao)
        } catch (e: ClassCastException) {
            padrao
        }

    override fun gravar(valores: Map<Preferencia, Any>) {
        val editor = prefs.edit()
        for ((chave, valor) in valores) {
            when (valor) {
                is Int -> editor.putInt(chave.chave, valor)
                is Boolean -> editor.putBoolean(chave.chave, valor)
                is Long -> editor.putLong(chave.chave, valor)
                is String -> editor.putString(chave.chave, valor)
                else -> editor.putString(chave.chave, valor.toString())
            }
        }
        editor.apply()
    }
}

private object SistemaAndroid : SistemaDoLancador {
    private const val ACAO_REPOSICIONAR_BARRA = "br.com.opengw.UPDATE_BAR_POSITION"

    private val contexto: Context get() = OpenGw.contexto

    override val densidade: Float get() = contexto.resources.displayMetrics.density
    override val pacoteDoApp: String get() = contexto.packageName
    override val depuracao: Boolean get() = BuildConfig.DEBUG

    override fun resolucao(display: Int): Pair<Int, Int>? {
        val dm = contexto.getSystemService(DisplayManager::class.java) ?: return null
        val tela = dm.getDisplay(display) ?: return null
        val modo = tela.mode
        return modo.physicalWidth to modo.physicalHeight
    }

    override fun atividadeDeAbertura(pacote: String): String? = InfoDosApps.atividadeDeAbertura(contexto, pacote)

    override fun abrirPelaIntent(pacote: String, flags: Int): Boolean {
        val intent = contexto.packageManager.getLaunchIntentForPackage(pacote) ?: return false
        intent.addFlags(flags)
        try {
            contexto.startActivity(intent)
        } catch (e: Exception) {
            RegistroNoLogcat.erro("startActivity de $pacote falhou", e)
        }
        return true
    }

    override fun reposicionarBarra() {
        contexto.sendBroadcast(Intent(ACAO_REPOSICIONAR_BARRA).setPackage(contexto.packageName))
    }

    override fun lerArquivo(caminho: String): String? =
        try {
            File(caminho).readText()
        } catch (e: Exception) {
            null
        }

    override fun naThreadPrincipal(bloco: () -> Unit) {
        Handler(Looper.getMainLooper()).post(bloco)
    }
}

private object CarroPeloVeiculo : CarroDoLancador {
    override fun lerDoCarro(chave: String): String? = Veiculo.lerDoCarro(chave)
    override fun ler(chave: String): String? = Veiculo.ler(chave)
}

private class FundoEmCorrotinas(private val registro: RegistroDoLancador) : FundoDoLancador {
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun lancar(nome: String, bloco: suspend () -> Unit) {
        escopo.launch {
            try {
                bloco()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                registro.erro("falha em segundo plano ($nome)", e)
            }
        }
    }
}

private object RegistroNoLogcat : RegistroDoLancador {
    private const val TAG = "OpenGW.Telas"
    override fun depuracao(texto: String) {
        Log.d(TAG, texto)
    }

    override fun info(texto: String) {
        Log.i(TAG, texto)
    }

    override fun aviso(texto: String) {
        Log.w(TAG, texto)
    }

    override fun erro(texto: String, causa: Throwable?) {
        Log.e(TAG, texto, causa)
    }
}

/**
 * Uma ligação a um serviço da central com prazo de 2,5 s: pedida e sem binder depois do prazo, é
 * desfeita e pedida de novo (4-A1; no CarPlay, a correção de D7).
 */
private class LigacaoComPrazo(
    private val acao: String,
    private val componente: ComponentName,
    private val relogio: RelogioDoLancador,
    private val registro: RegistroDoLancador,
    private val aoLigar: (IBinder) -> Unit = {},
) {
    private val trava = Any()

    @Volatile
    var binder: IBinder? = null
        private set
    private var conexao: ServiceConnection? = null
    private var pedidaEm = 0L

    fun vivo(): Boolean = binder?.isBinderAlive == true

    fun pedida(): Boolean = synchronized(trava) { conexao != null }

    fun esquecer() {
        binder = null
    }

    /** Devolve se o binder já está vivo. */
    fun garantir(): Boolean = synchronized(trava) {
        val b = binder
        if (b != null) {
            if (b.isBinderAlive) return true
            binder = null
        }
        val agora = relogio.monotonico()
        if (conexao != null && agora - pedidaEm in 0 until PRAZO_MS) return false
        val contexto = OpenGw.contexto
        conexao?.let {
            try {
                contexto.unbindService(it)
            } catch (e: Exception) {
            }
        }
        conexao = null
        val nova = object : ServiceConnection {
            override fun onServiceConnected(nome: ComponentName?, servico: IBinder?) {
                binder = servico
                if (servico != null) aoLigar(servico)
            }

            override fun onServiceDisconnected(nome: ComponentName?) {
                binder = null
            }

            override fun onBindingDied(nome: ComponentName?) {
                binder = null
            }
        }
        try {
            val intent = Intent(acao).setComponent(componente)
            if (!contexto.bindService(intent, nova, Context.BIND_AUTO_CREATE)) {
                registro.aviso("bindService recusado: ${componente.flattenToShortString()}")
            }
        } catch (e: Exception) {
            registro.aviso("bindService falhou: ${componente.flattenToShortString()}: ${e.message}")
        }
        conexao = nova
        pedidaEm = agora
        return binder?.isBinderAlive == true
    }

    /** Transação síncrona com leitura de exceção; null se falhou (e o binder é esquecido). */
    fun <T> sincrona(token: String, codigo: Int, escrever: (Parcel) -> Unit = {}, ler: (Parcel) -> T): T? {
        val b = binder?.takeIf { it.isBinderAlive } ?: run {
            garantir()
            return null
        }
        val dados = Parcel.obtain()
        val resposta = Parcel.obtain()
        return try {
            dados.writeInterfaceToken(token)
            escrever(dados)
            if (!b.transact(codigo, dados, resposta, 0)) throw IllegalStateException("transação $codigo recusada")
            resposta.readException()
            ler(resposta)
        } catch (e: Exception) {
            registro.aviso("transação $codigo falhou: ${e.message}")
            esquecer()
            null
        } finally {
            dados.recycle()
            resposta.recycle()
        }
    }

    /** Transação unidirecional. */
    fun unidirecional(token: String, codigo: Int, escrever: (Parcel) -> Unit): Boolean {
        val b = binder?.takeIf { it.isBinderAlive } ?: run {
            garantir()
            return false
        }
        val dados = Parcel.obtain()
        return try {
            dados.writeInterfaceToken(token)
            escrever(dados)
            b.transact(codigo, dados, null, IBinder.FLAG_ONEWAY)
        } catch (e: Exception) {
            registro.aviso("transação $codigo falhou: ${e.message}")
            esquecer()
            false
        } finally {
            dados.recycle()
        }
    }

    companion object {
        const val PRAZO_MS = 2_500L
    }
}

/**
 * Os campos do aparelho (`IfDeviceMirrorDevice`), na ordem do parcelable da central. Hipótese (a
 * confirmar): os parcelables aninhados vão por `writeParcelable`/`readParcelable`.
 */
private class DadosDoAparelho(
    val modoAtual: Int, val status: Int, val usb: Parcelable?, val enlace: Int, val bluetooth: Parcelable?,
    val id: String?, val modosSuportados: Int, val modoDeConexao: Int, val erro: Int, val bateria: Int,
    val porta: Int, val capacidade: Int, val serialUsb: String?, val modoUsb: Int, val enderecoBt: String?,
    val enderecoWifi: String?, val noHid: String?, val noNcm: String?, val tipo: Int,
) {
    fun escrever(p: Parcel) {
        p.writeInt(modoAtual); p.writeInt(status); p.writeParcelable(usb, 0); p.writeInt(enlace)
        p.writeParcelable(bluetooth, 0); p.writeString(id); p.writeInt(modosSuportados); p.writeInt(modoDeConexao)
        p.writeInt(erro); p.writeInt(bateria); p.writeInt(porta); p.writeInt(capacidade); p.writeString(serialUsb)
        p.writeInt(modoUsb); p.writeString(enderecoBt); p.writeString(enderecoWifi); p.writeString(noHid)
        p.writeString(noNcm); p.writeInt(tipo)
    }

    companion object {
        @Suppress("DEPRECATION")
        fun ler(p: Parcel): DadosDoAparelho {
            val modoAtual = p.readInt()
            val status = p.readInt()
            val usb = p.readParcelable<UsbDevice>(UsbDevice::class.java.classLoader)
            val enlace = p.readInt()
            val bt = p.readParcelable<BluetoothDevice>(BluetoothDevice::class.java.classLoader)
            return DadosDoAparelho(
                modoAtual, status, usb, enlace, bt, p.readString(), p.readInt(), p.readInt(), p.readInt(),
                p.readInt(), p.readInt(), p.readInt(), p.readString(), p.readInt(), p.readString(), p.readString(),
                p.readString(), p.readString(), p.readInt(),
            )
        }
    }
}

/** 4-A1: `com.ts.androidauto.sdk.aidl.LinkCommand`. */
private class CanalDoAndroidAutoAndroid(
    relogio: RelogioDoLancador,
    private val registro: RegistroDoLancador,
    private val fundo: FundoDoLancador,
) : CanalDoAndroidAuto {
    private val ligacao: LigacaoComPrazo = LigacaoComPrazo(
        "com.ts.androidauto.action.AndroidAutoService",
        ComponentName("com.ts.androidauto.projectionservice", "com.ts.androidauto.projectionservice.AndroidAutoService"),
        relogio, registro,
    ) { _ -> fundo.lancar("assinar teclas da HMI") { assinarTeclas() } }

    private fun assinarTeclas() {
        ligacao.sincrona(TOKEN, ASSINAR_TECLAS) { }
    }

    override fun vivo() = ligacao.vivo()
    override fun ligacaoPedida() = ligacao.pedida()
    override fun garantirLigado() = ligacao.garantir()

    override fun statusSeJaLigado(): Int? = if (ligacao.vivo()) status() else null
    override fun status(): Int? = ligacao.sincrona(TOKEN, STATUS) { it.readInt() }
    override fun musica(): Int? = ligacao.sincrona(TOKEN, MUSICA) { it.readInt() }
    override fun progresso(): Int? = ligacao.sincrona(TOKEN, PROGRESSO) { it.readInt() }

    override fun tecla(ordinal: Int, acao: Int): Boolean = ligacao.unidirecional(TOKEN, TECLA) {
        it.writeInt(ordinal)
        it.writeInt(acao)
    }

    override fun proxima() = ligacao.sincrona(TOKEN, PROXIMA) { } != null
    override fun anterior() = ligacao.sincrona(TOKEN, ANTERIOR) { } != null
    override fun tocar() = ligacao.sincrona(TOKEN, TOCAR) { } != null
    override fun pausar() = ligacao.sincrona(TOKEN, PAUSAR) { } != null

    override fun aparelhos(): List<AparelhoDoAa>? = ligacao.sincrona(TOKEN, APARELHOS) { p ->
        val tamanho = p.readInt()
        val lista = ArrayList<AparelhoDoAa>()
        for (i in 0 until maxOf(tamanho, 0)) {
            if (p.readInt() == 0) continue
            val d = DadosDoAparelho.ler(p)
            lista += AparelhoDoAa(
                d.modoAtual, d.status, d.enlace,
                temBluetooth = d.bluetooth != null || !d.enderecoBt.isNullOrEmpty(),
                temUsb = d.usb != null,
                dados = d,
            )
        }
        lista
    }

    private fun escreverAparelho(p: Parcel, aparelho: AparelhoDoAa) {
        val d = aparelho.dados as DadosDoAparelho
        p.writeInt(1)
        d.escrever(p)
        p.writeInt(0)
    }

    override fun conectar(aparelho: AparelhoDoAa): Boolean {
        if (aparelho.dados !is DadosDoAparelho) return false
        return ligacao.sincrona(TOKEN, CONECTAR, { escreverAparelho(it, aparelho) }) { it.readInt() } != null
    }

    override fun ouvirSemFio(aparelho: AparelhoDoAa): Boolean {
        if (aparelho.dados !is DadosDoAparelho) return false
        return ligacao.sincrona(TOKEN, OUVIR_SEM_FIO, { escreverAparelho(it, aparelho) }) { } != null
    }

    companion object {
        const val TOKEN = "com.ts.androidauto.sdk.aidl.LinkCommand"
        const val CONECTAR = 7
        const val TECLA = 10
        const val APARELHOS = 13
        const val OUVIR_SEM_FIO = 15
        const val STATUS = 21
        const val PROXIMA = 24
        const val ANTERIOR = 25
        const val PROGRESSO = 26
        const val MUSICA = 27
        const val TOCAR = 28
        const val PAUSAR = 29
        const val ASSINAR_TECLAS = 49
    }
}

/** 3-K9: `com.ts.carplay.common.aidl.ICarPlayService`. */
private class CanalDoCarPlayAndroid(relogio: RelogioDoLancador, registro: RegistroDoLancador) : CanalDoCarPlay {
    private val ligacao = LigacaoComPrazo(
        "com.ts.carplay.action.CarPlayService",
        ComponentName("com.ts.carplay", "com.ts.carplay.CarPlayService"),
        relogio, registro,
    )

    override fun vivo() = ligacao.vivo()
    override fun pedirLigacao() {
        ligacao.garantir()
    }

    override fun status(): Int? = ligacao.sincrona(TOKEN, STATUS) { it.readInt() }
    override fun modoNormal(): Boolean = ligacao.sincrona(TOKEN, MODO, { it.writeInt(0) }) { } != null

    companion object {
        const val TOKEN = "com.ts.carplay.common.aidl.ICarPlayService"
        const val STATUS = 29
        const val MODO = 20
    }
}

/** 4-G1, passo 4: o serviço e o broadcast ordenado do app de AA modificado. */
