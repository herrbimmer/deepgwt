package br.com.deepgwt.tela3d.viagens

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import java.io.File

/**
 * O gravador de viagens: um por processo (criado por quem o pede, a Activity da tela 3D, e vive até o
 * processo morrer). Escuta os sinais do carro por broadcast, grava as viagens no `trips.db`, liga o GPS
 * enquanto há viagem aberta e cuida dos mapas e nomes de lugar. Tudo roda na thread de trabalho dele;
 * as consultas da ponte (`TripBridge`) rodam na thread do WebView e podem bloquear brevemente.
 */
class GravadorDeViagens private constructor(contexto: Context) : FachadaDeViagens {

    companion object {
        private const val ETIQUETA = "DeepGWT.Viagens"
        private const val INTERVALO_DO_TICK_MS = 1_000L
        private const val INTERVALO_DO_GPS_MS = 1_000L
        private const val PASTA_DOS_MAPAS = "trip-maps"
        private const val PASTA_DOS_TILES = "osm-tiles"

        @Volatile
        private var instancia: GravadorDeViagens? = null

        /** O gravador do processo; cria e inicia na primeira chamada. */
        fun obter(contexto: Context): GravadorDeViagens =
            instancia ?: synchronized(this) {
                instancia ?: GravadorDeViagens(contexto.applicationContext).also { instancia = it }
            }
    }

    private val contexto: Context = contexto.applicationContext
    private val fio = HandlerThread("deepgwt-viagens").apply { start() }
    private val manipulador = Handler(fio.looper)

    @Volatile
    private var servicoDeMapas: ServicoDeMapas? = null

    @Volatile
    private var armazenamento: ArmazenamentoDeViagens? = null

    private val nucleo = NucleoDoGravador(::registrar) { atraso -> servicoDeMapas?.agendar(atraso) }

    private val consultas = ConsultasDaPonte(
        armazenamento = { armazenamento },
        textoAoVivo = { nucleo.textoAoVivo },
        pontosEmMemoria = { inicio -> nucleo.pontosEmMemoria(inicio) },
        pedirFinalizacao = ::pedirFinalizacao,
        pastaDosMapas = { File(this.contexto.filesDir, PASTA_DOS_MAPAS) },
        registro = ::registrar,
    )

    private var ouvinteDoGps: LocationListener? = null
    private var ultimoTickMonotonicoMs = 0L

    private fun registrar(mensagem: String) {
        Log.i(ETIQUETA, mensagem)
    }

    private fun iniciarNaThreadDoGravador() {
        try {
            val banco = BancoSqliteAndroid.abrir(contexto)
            armazenamento = ArmazenamentoDeViagens(banco).also { nucleo.armazenamento = it }
        } catch (e: Exception) {
            registrar("não abriu o trips.db: ${e.message}")
        }
        nucleo.iniciar(System.currentTimeMillis())
        armazenamento?.let { iniciarServicoDeMapas(it) }
        try {
            val filtro = IntentFilter().apply {
                addAction(ChavesDeSinais.ACAO_EVENTO_DO_VEICULO)
                addAction(ChavesDeSinais.ACAO_DADOS_DO_CARRO)
            }
            contexto.registerReceiver(receptor, filtro, null, manipulador)
        } catch (e: Exception) {
            registrar("não registrou o receptor de sinais: ${e.message}")
        }
        ultimoTickMonotonicoMs = SystemClock.elapsedRealtime()
        manipulador.postDelayed(tick, INTERVALO_DO_TICK_MS)
    }

    private fun iniciarServicoDeMapas(a: ArmazenamentoDeViagens) {
        val tiles = TilesOsm(File(contexto.cacheDir, PASTA_DOS_TILES), registro = ::registrar)
        val gerador = GeradorDeMapaAndroid(File(contexto.filesDir, PASTA_DOS_MAPAS), tiles)
        val varredura = VarreduraDeMapas(
            a, gerador, ConsultaDeLugar { lat, lon -> GeocodificadorReverso.consultar(lat, lon) }, ::registrar,
        )
        val servico = ServicoDeMapas(varredura::executar, ::registrar)
        servicoDeMapas = servico
        servico.iniciar()
    }

    // --- Sinais do carro ---

    private val receptor = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            val chave = intent?.getStringExtra(ChavesDeSinais.EXTRA_CHAVE) ?: return
            val valor = intent.getStringExtra(ChavesDeSinais.EXTRA_VALOR) ?: return
            try {
                nucleo.aoSinal(chave, valor, System.currentTimeMillis())
            } catch (e: Exception) {
                registrar("falha ao tratar $chave: ${e.message}")
            }
        }
    }

    // --- Tick de 1 s ---

    private val tick = object : Runnable {
        override fun run() {
            val monotonico = SystemClock.elapsedRealtime()
            val intervalo = monotonico - ultimoTickMonotonicoMs
            ultimoTickMonotonicoMs = monotonico
            try {
                nucleo.tick(System.currentTimeMillis(), intervalo)
                ajustarGps()
                if (intervalo >= MotorDeViagem.SUSPENSAO_MS) pedirRetratoDaTelemetria()
            } catch (e: Exception) {
                registrar("falha no tick: ${e.message}")
            }
            manipulador.postDelayed(this, INTERVALO_DO_TICK_MS)
        }
    }

    /** Depois de acordar o gravador não sabe mais o estado do carro: pede o retrato da telemetria. */
    private fun pedirRetratoDaTelemetria() {
        try {
            val pedido = Intent(ChavesDeSinais.ACAO_PEDIR_RETRATO)
                .setPackage(contexto.packageName)
                .putExtra(ChavesDeSinais.EXTRA_SOLICITANTE, contexto.packageName)
            contexto.sendBroadcast(pedido)
        } catch (e: Exception) {
            registrar("não pediu o retrato da telemetria: ${e.message}")
        }
    }

    // --- GPS ---

    private fun ajustarGps() {
        if (nucleo.viagemAberta) ligarGps() else desligarGps()
    }

    private fun ligarGps() {
        if (ouvinteDoGps != null) return
        if (contexto.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val gerente = contexto.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        val ouvinte = object : LocationListener {
            override fun onLocationChanged(posicao: Location) {
                try {
                    nucleo.aoPosicao(
                        posicao.latitude,
                        posicao.longitude,
                        if (posicao.hasAltitude()) posicao.altitude else Double.NaN,
                        if (posicao.hasAccuracy()) posicao.accuracy.toDouble() else Double.NaN,
                        System.currentTimeMillis(),
                    )
                } catch (e: Exception) {
                    registrar("falha ao tratar a posição: ${e.message}")
                }
            }

            @Deprecated("Chamado só em Androids antigos")
            override fun onStatusChanged(provedor: String?, estado: Int, extras: Bundle?) = Unit

            override fun onProviderEnabled(provedor: String) = Unit

            override fun onProviderDisabled(provedor: String) = Unit
        }
        try {
            @Suppress("MissingPermission")
            gerente.requestLocationUpdates(LocationManager.GPS_PROVIDER, INTERVALO_DO_GPS_MS, 0f, ouvinte, fio.looper)
            ouvinteDoGps = ouvinte
        } catch (e: Exception) {
            registrar("não ligou o GPS: ${e.message}")
        }
    }

    private fun desligarGps() {
        val ouvinte = ouvinteDoGps ?: return
        ouvinteDoGps = null
        try {
            (contexto.getSystemService(Context.LOCATION_SERVICE) as? LocationManager)?.removeUpdates(ouvinte)
        } catch (e: Exception) {
            registrar("não desligou o GPS: ${e.message}")
        }
    }

    // --- Chamadas de fora ---

    /** Pedido de checkpoint "logo" (a Activity pausou). */
    fun salvarLogo() {
        manipulador.post {
            try {
                nucleo.salvarLogo(System.currentTimeMillis())
            } catch (e: Exception) {
                registrar("falha ao salvar o checkpoint: ${e.message}")
            }
        }
    }

    /** A página pediu FINALIZAR VIAGEM: roda na thread do gravador, não na da ponte. */
    private fun pedirFinalizacao() {
        manipulador.post {
            try {
                nucleo.finalizarViagem(System.currentTimeMillis())
            } catch (e: Exception) {
                registrar("falha ao finalizar a viagem: ${e.message}")
            }
        }
    }

    override fun finalizarViagem() = consultas.finalizarViagem()
    override fun diasEmJson(de: String?, ate: String?): String = consultas.diasEmJson(de, ate)
    override fun viagemAoVivoEmJson(): String = consultas.viagemAoVivoEmJson()
    override fun mesesEmJson(): String = consultas.mesesEmJson()
    override fun totaisDeParadasEmJson(desdeMs: String?): String = consultas.totaisDeParadasEmJson(desdeMs)
    override fun totaisEmJson(): String = consultas.totaisEmJson()
    override fun viagemEmJson(inicioMs: String?, maxPontos: Int): String = consultas.viagemEmJson(inicioMs, maxPontos)
    override fun mapaDaViagem(inicioMs: String?): String = consultas.mapaDaViagem(inicioMs)
    override fun pontosDesdeEmJson(inicioMs: String?, ultimoT: String?, maxPontos: Int): String =
        consultas.pontosDesdeEmJson(inicioMs, ultimoT, maxPontos)
    override fun viagensEmJson(deslocamento: Int, limite: Int): String = consultas.viagensEmJson(deslocamento, limite)

    // Por último: a thread do gravador só começa depois que todos os campos acima existem.
    init {
        manipulador.post { iniciarNaThreadDoGravador() }
    }
}
