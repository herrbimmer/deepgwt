package br.com.opengw.barra

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import br.com.opengw.nucleo.Preferencias
import br.com.opengw.nucleo.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * O encaixe pela tela (serviço §F): confere se o app da frente deixou uma faixa lisa acima da barra
 * e, se for a assinatura do defeito, diminui o recuo daquele app. As regras puras são de
 * [EncaixeDaBarra]. Só Android ≤ 10 (`wm overscan` não existe a partir do 11).
 */
internal class EncaixeNaTela(
    private val contexto: Context,
    private val escopo: CoroutineScope,
    private val acesso: Acesso,
) {
    interface Acesso {
        /** O último px mandado ao `wm overscan` (−1 = nenhum). */
        fun aplicadoPx(): Int
        fun ultimoTratado(): String?
        /** Barra visível, nada pedindo toque, sem cartão, ajuste, escolha nem painel. Principal. */
        fun barraLivre(): Boolean
        /** Recalcular e aplicar o recuo do app (principal). */
        fun reaplicarRecuo(pacote: String)
    }

    private var rodando: Job? = null
    private val trava = Any()
    private val encaixados = HashSet<String>()
    private val pontos = HashMap<String, EncaixeDaBarra.Ponto>()

    /** Uma conferência por vez. */
    fun conferir(pacote: String) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) return
        if (rodando?.isActive == true) return
        rodando = escopo.launch(Dispatchers.IO) {
            runCatching { conferirAgora(pacote) }.onFailure { log("falhou: ${it.message}") }
        }
    }

    /** Caso 24: a altura mudou. */
    fun esquecer() {
        synchronized(trava) {
            encaixados.clear()
            pontos.clear()
        }
        PreferenciasDaBarra.abrir()?.edit()?.remove(Preferencias.BOTTOM_BAR_AUTO_OVERSCAN.chave)?.apply()
    }

    private fun log(m: String) {
        Log.i(TAG, "[ENCAIXE] $m")
    }

    private suspend fun livre() = withContext(Dispatchers.Main) { acesso.barraLivre() }

    private suspend fun conferirAgora(pacote: String) {
        if (!livre()) return
        if (pacote == contexto.packageName) return
        if (Barra.integracoes.lancador.projecaoReconhecida(pacote) != null) return
        val aplicado = acesso.aplicadoPx()
        if (aplicado <= 0) return

        // Passo 1 — o sistema.
        val lido = EncaixeDaBarra.overscanInferiorDoDisplay0(Shell.executar(arrayOf("dumpsys", "display")))
        if (lido != null && abs(lido - aplicado) > 2) {
            log("sistema com $lido px, aplicado $aplicado px: mandando de novo")
            Shell.executar(arrayOf("wm", "overscan", "0,0,0,$aplicado"))
            return
        }

        // Passo 2 — a tela.
        if (!appDoSistema(pacote)) return
        val manuais = PreferenciasDaBarra.abrir()?.getString(Preferencias.BOTTOM_BAR_OVERRIDES.chave, null).orEmpty()
        if (manuais.contains("\"$pacote\"")) return
        if (synchronized(trava) { pacote in encaixados }) return
        val primeira = medir()
        if (primeira == null) {
            synchronized(trava) { encaixados += pacote }
            log("$pacote sem faixa com $aplicado px")
            return
        }
        delay(900)
        if (acesso.ultimoTratado() != pacote || !livre() || acesso.aplicadoPx() != aplicado) return
        val segunda = medir() ?: return
        if (abs(primeira.altura - segunda.altura) > 2 || primeira.cor != segunda.cor) {
            log("$pacote: medidas não concordam (${primeira.altura}/${segunda.altura})")
            return
        }
        val proximo = synchronized(trava) {
            val anterior = pontos[pacote]
            pontos[pacote] = EncaixeDaBarra.Ponto(aplicado, segunda.altura)
            EncaixeDaBarra.proximoRecuoPx(aplicado, segunda.altura, anterior)
        }
        if (proximo == null) {
            synchronized(trava) { encaixados += pacote }
            log("$pacote: faixa ${segunda.altura} px com $aplicado px; sem próximo recuo")
            return
        }
        val densidade = contexto.resources.displayMetrics.density
        val dp = (proximo / densidade).roundToInt()
        val prefs = PreferenciasDaBarra.abrir() ?: return
        val mapa = ContasDaBarra.lerRecuosMedidos(prefs.getString(Preferencias.BOTTOM_BAR_AUTO_OVERSCAN.chave, null)).toMutableMap()
        mapa[pacote] = dp
        prefs.edit().putString(Preferencias.BOTTOM_BAR_AUTO_OVERSCAN.chave, ContasDaBarra.gravarRecuosMedidos(mapa)).apply()
        log("$pacote: faixa ${segunda.altura} px (#${Integer.toHexString(segunda.cor)}) com $aplicado px → $proximo px ($dp dp)")
        if (acesso.ultimoTratado() == pacote) withContext(Dispatchers.Main) { acesso.reaplicarRecuo(pacote) }
        Barra.integracoes.lancador.reaplicarLimitesDoDisplay0()
    }

    private fun appDoSistema(pacote: String): Boolean = runCatching {
        val flags = contexto.packageManager.getApplicationInfo(pacote, 0).flags
        (flags and ApplicationInfo.FLAG_SYSTEM) != 0 || (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }.getOrDefault(false)

    /** Caso 23. */
    @Suppress("DEPRECATION")
    private fun medir(): EncaixeDaBarra.Faixa? {
        val png = runCatching { Barra.integracoes.capturaDaTela.png() }.getOrNull() ?: return null
        val m = DisplayMetrics()
        (contexto.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(m)
        val barraPx = (EstadoDaBarra.alturaDp * m.density).roundToInt()
        val topo = m.heightPixels - barraPx
        val alturaDaRegiao = minOf(2 * barraPx + 8, topo)
        if (alturaDaRegiao <= 1 || m.widthPixels <= 0) return null
        val decodificador = runCatching { BitmapRegionDecoder.newInstance(png, 0, png.size, false) }.getOrNull() ?: return null
        return try {
            val regiao = decodificador.decodeRegion(Rect(0, topo - alturaDaRegiao, m.widthPixels, topo), null) ?: return null
            val l = regiao.width
            val a = regiao.height
            val px = IntArray(l * a)
            regiao.getPixels(px, 0, l, 0, 0, l, a)
            regiao.recycle()
            val recuo = EstadoDaBarra.recuoEsquerdoPx
            EncaixeDaBarra.faixaAcimaDaBarra(px, l, a, recuo + 16, l - 16)
        } catch (e: Exception) {
            null
        } finally {
            decodificador.recycle()
        }
    }

    private companion object {
        const val TAG = "OpenGW.Barra"
    }
}
