package br.com.deepgwt.tela3d.viagens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.os.Handler
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A última posição conhecida de algum provedor do aparelho. */
data class PosicaoDoAparelho(val lat: Double, val lon: Double, val instanteMs: Long, val provedor: String?)

/** De onde o lugar atual tira a posição (no app, o `LocationManager`). */
interface FonteDePosicoes {
    /** Há permissão de localização (fina ou grossa). */
    fun temPermissao(): Boolean

    /** A mais recente entre `gps`, `network` e `passive`, sem limite de idade. Pode lançar `SecurityException`. */
    fun maisRecente(): PosicaoDoAparelho?
}

/** Uma fonte de nomes de lugar (no app, o `Geocoder` do Android). */
interface FonteDeGeocodificacao {
    /** O aparelho tem essa fonte (o carro, sem Google, provavelmente não tem). */
    fun presente(): Boolean

    /** Pode lançar exceção; `null` ou vazio = sem resposta. */
    fun consultar(lat: Double, lon: Double): GeocodificadorReverso.Lugar?
}

/**
 * Tenta primeiro a fonte do Android; se ela não existe ou lança exceção, marca-a como morta pelo resto da
 * vida do objeto e usa só o Nominatim. Resposta vazia (sem erro) vale o Nominatim só naquela consulta.
 */
class GeocodificadorDoLugar(
    private val android: FonteDeGeocodificacao?,
    private val nominatim: ConsultaDeLugar,
    private val registro: Registro = {},
) : ConsultaDeLugar {
    private var androidMorto = android == null

    override fun consultar(lat: Double, lon: Double): GeocodificadorReverso.Lugar? {
        val fonte = android
        if (fonte != null && !androidMorto) {
            try {
                if (!fonte.presente()) {
                    androidMorto = true
                    registro("o Geocoder do Android não existe neste aparelho")
                } else {
                    val lugar = fonte.consultar(lat, lon)
                    if (lugar != null && !lugar.vazio) return lugar
                }
            } catch (e: Exception) {
                androidMorto = true
                registro("o Geocoder do Android falhou: ${e.message}")
            }
        }
        return nominatim.consultar(lat, lon)
    }
}

/** Distância entre duas posições (graus), em metros. */
object DistanciaGeografica {
    private const val RAIO_DA_TERRA_M = 6_371_000.0

    fun metros(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = p2 - p1
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * RAIO_DA_TERRA_M * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
}

/**
 * O lugar atual (bairro, cidade, rua) para o cartão de repouso da página, a partir da última posição
 * conhecida do aparelho. Consulta logo ao iniciar e a cada 45 s numa thread própria; só entrega ao
 * [OuvinteDeLugar] (na thread principal) quando o JSON muda.
 */
class LugarAtual internal constructor(
    private val posicoes: FonteDePosicoes,
    private val geocodificador: ConsultaDeLugar,
    private val entregar: (String) -> Unit,
    private val relogioMs: () -> Long,
    private val registro: Registro,
) {
    fun interface OuvinteDeLugar {
        /** Chamado na thread principal. */
        fun aoMudar(jsonDoLugar: String)
    }

    constructor(contexto: Context, principal: Handler, ouvinte: OuvinteDeLugar) : this(
        posicoes = PosicoesDoAndroid(contexto.applicationContext),
        geocodificador = GeocodificadorDoLugar(
            GeocoderDoAndroid(contexto.applicationContext),
            ConsultaDeLugar { lat, lon -> GeocodificadorReverso.consultar(lat, lon) },
            RegistroDoAndroid::informar,
        ),
        entregar = { json -> principal.post { ouvinte.aoMudar(json) } },
        relogioMs = System::currentTimeMillis,
        registro = RegistroDoAndroid::informar,
    )

    companion object {
        const val INTERVALO_MS = 45_000L
        const val DESLOCAMENTO_MINIMO_M = 120.0
        const val IDADE_MAXIMA_DA_POSICAO_MS = 600_000L
        const val PROVEDOR_PADRAO = "gps"

        /** O JSON entregue à página (`app.location.place`). */
        fun montarJson(lugar: GeocodificadorReverso.Lugar, provedor: String?): String = JsonSaida.objeto {
            texto("neighborhood", lugar.bairro)
            texto("city", lugar.cidade)
            texto("street", lugar.rua)
            texto("provider", provedor ?: PROVEDOR_PADRAO)
        }
    }

    /** Uma execução do ciclo; cada `iniciar()` cria uma nova, assim `parar()` + `iniciar()` não se confundem. */
    private inner class Ciclo : Thread("deepgwt-lugar") {
        private val trava = Object()
        private var encerrado = false
        private var cutucada = false

        init {
            isDaemon = true
        }

        override fun run() {
            while (true) {
                try {
                    consultarUmaVez()
                } catch (e: Exception) {
                    registro("a consulta do lugar atual falhou: ${e.message}")
                }
                synchronized(trava) {
                    if (!encerrado && !cutucada) {
                        try {
                            trava.wait(INTERVALO_MS)
                        } catch (e: InterruptedException) {
                            encerrado = true
                        }
                    }
                    if (encerrado) return
                    cutucada = false
                }
            }
        }

        fun encerrar() {
            synchronized(trava) {
                encerrado = true
                trava.notifyAll()
            }
        }

        fun cutucar() {
            synchronized(trava) {
                cutucada = true
                trava.notifyAll()
            }
        }
    }

    private var ciclo: Ciclo? = null

    // Protegidos por `consultaTrava`: só uma consulta de cada vez, mesmo com `parar()` + `iniciar()` rápidos.
    private val consultaTrava = Any()
    private var ultimoJson: String = ""
    private var ultimaPosicaoUsada: PosicaoDoAparelho? = null

    /** Idempotente; pode ser chamado de novo depois de [parar]. */
    @Synchronized
    fun iniciar() {
        if (ciclo?.isAlive == true) return
        ciclo = Ciclo().also { it.start() }
    }

    @Synchronized
    fun parar() {
        ciclo?.encerrar()
        ciclo = null
    }

    /** Uma consulta extra, fora do ciclo de 45 s. Sem efeito se o serviço não está rodando. */
    @Synchronized
    fun cutucarAgora() {
        ciclo?.cutucar()
    }

    /** Uma consulta (C-43). Bloqueante; roda na thread do ciclo. */
    internal fun consultarUmaVez() {
        synchronized(consultaTrava) { consultar() }
    }

    private fun consultar() {
        if (!posicoes.temPermissao()) return
        val posicao = try {
            posicoes.maisRecente()
        } catch (e: SecurityException) {
            registro("sem permissão para ler a posição: ${e.message}")
            return
        } ?: return
        if (relogioMs() - posicao.instanteMs > IDADE_MAXIMA_DA_POSICAO_MS) return
        val anterior = ultimaPosicaoUsada
        if (ultimoJson.isNotEmpty() && anterior != null &&
            DistanciaGeografica.metros(anterior.lat, anterior.lon, posicao.lat, posicao.lon) < DESLOCAMENTO_MINIMO_M
        ) {
            return
        }
        val lugar = geocodificador.consultar(posicao.lat, posicao.lon) ?: return
        if (lugar.vazio) return
        ultimaPosicaoUsada = posicao
        val json = montarJson(lugar, posicao.provedor)
        if (json != ultimoJson) {
            ultimoJson = json
            entregar(json)
        }
    }
}

private object RegistroDoAndroid {
    private const val ETIQUETA = "DeepGWT.Viagens"

    fun informar(mensagem: String) {
        android.util.Log.i(ETIQUETA, mensagem)
    }
}

/** A posição do `LocationManager`: a mais recente entre `gps`, `network` e `passive`. */
internal class PosicoesDoAndroid(private val contexto: Context) : FonteDePosicoes {
    private val gerente: LocationManager? = contexto.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    override fun temPermissao(): Boolean =
        contexto.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            contexto.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @android.annotation.SuppressLint("MissingPermission")
    override fun maisRecente(): PosicaoDoAparelho? {
        val g = gerente ?: return null
        var melhor: android.location.Location? = null
        for (provedor in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
            val l = try {
                g.getLastKnownLocation(provedor)
            } catch (e: SecurityException) {
                throw e
            } catch (e: Exception) {
                null
            } ?: continue
            if (melhor == null || l.time > melhor.time) melhor = l
        }
        return melhor?.let { PosicaoDoAparelho(it.latitude, it.longitude, it.time, it.provider) }
    }
}

/** O `Geocoder` do Android com localidade pt-BR. */
internal class GeocoderDoAndroid(private val contexto: Context) : FonteDeGeocodificacao {
    private val geocoder: Geocoder? by lazy { if (Geocoder.isPresent()) Geocoder(contexto, Locale("pt", "BR")) else null }

    override fun presente(): Boolean = Geocoder.isPresent()

    @Suppress("DEPRECATION")
    override fun consultar(lat: Double, lon: Double): GeocodificadorReverso.Lugar? {
        val g = geocoder ?: return null
        val endereco = g.getFromLocation(lat, lon, 1)?.firstOrNull() ?: return null
        return GeocodificadorReverso.Lugar(
            bairro = endereco.subLocality?.trim().orEmpty(),
            cidade = (endereco.locality?.takeIf { it.isNotBlank() } ?: endereco.subAdminArea).orEmpty().trim(),
            rua = endereco.thoroughfare?.trim().orEmpty(),
        )
    }
}
