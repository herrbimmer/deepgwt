package br.com.opengw.barra

import android.content.Context
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * As três janelas auxiliares do serviço (caso 25–28): o aviso do SPORT+, a confirmação de gesto e o
 * indicador de CPU/RAM. Tudo na principal, salvo a amostragem.
 */
internal class JanelasAuxiliares(private val contexto: Context) {

    private var aviso: JanelaCompose? = null
    private var retorno: JanelaCompose? = null
    private var indicador: JanelaCompose? = null
    private val apagarRetorno = Runnable { EstadoDaBarra.retornoDeGesto = null }

    // ── Aviso do SPORT+ (caso 25) ───────────────────────────────────────────────────────

    fun aviso(mostrar: Boolean) {
        if (!mostrar) {
            aviso?.desmontar()
            aviso = null
            return
        }
        if (aviso != null) return
        val p = JanelaCompose.parametros(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL,
        ).apply { y = 24 } // S-D13: px, não dp (mantido)
        val j = JanelaCompose(contexto, p, "aviso SPORT+") {
            AvisoDoSportPlus(
                aoDesfazer = {
                    runCatching { Barra.integracoes.sportPlus.desligar() }
                    EstadoDaBarra.pedirAvisoDoSportPlus = false
                },
                aoOk = { EstadoDaBarra.pedirAvisoDoSportPlus = false },
            )
        }
        if (j.montar()) aviso = j
    }

    // ── Retorno de gesto (caso 26) ─────────────────────────────────────────────────────

    fun retorno(valor: RetornoDeGesto?) {
        ThreadPrincipal.cancelar(apagarRetorno)
        if (valor == null) {
            retorno?.desmontar()
            retorno = null
            return
        }
        if (retorno == null) {
            val p = JanelaCompose.parametros(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                Gravity.CENTER,
            )
            val j = JanelaCompose(contexto, p, "retorno de gesto") {
                // Lê o valor vivo: a janela é reaproveitada entre gestos.
                EstadoDaBarra.retornoDeGesto?.let { CartaoDoRetornoDeGesto(it) }
            }
            if (j.montar()) retorno = j
        }
        ThreadPrincipal.postarComAtraso(RETORNO_MS, apagarRetorno)
    }

    // ── Indicador de CPU/RAM (casos 27–28) ─────────────────────────────────────────────

    private val amostrador = AmostradorDeRecursos()
    private var textoDoIndicador by mutableStateOf(RegrasDoServico.textoDoIndicador(null, null))
    private var amostragem: Thread? = null

    /** Monta (ou tira, com [mostrar] falso). Uma mudança de fonte/canto/x/y remonta: quem chama tira antes. */
    fun indicador(mostrar: Boolean, fonteSp: Int, canto: Int, xDp: Int, yDp: Int) {
        if (indicador != null) {
            indicador?.desmontar()
            indicador = null
            pararAmostragem()
            if (!mostrar) registrar("escondido")
        }
        if (!mostrar) return
        val d = contexto.resources.displayMetrics.density
        val gravidade = when (RegrasDoServico.canto(canto)) {
            RegrasDoServico.Canto.TOPO_INICIO -> Gravity.TOP or Gravity.START
            RegrasDoServico.Canto.TOPO_FIM -> Gravity.TOP or Gravity.END
            RegrasDoServico.Canto.BAIXO_INICIO -> Gravity.BOTTOM or Gravity.START
            RegrasDoServico.Canto.BAIXO_FIM -> Gravity.BOTTOM or Gravity.END
        }
        val p = JanelaCompose.parametros(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            gravidade,
        ).apply {
            x = (xDp * d).toInt()
            y = (yDp * d).toInt()
        }
        textoDoIndicador = RegrasDoServico.textoDoIndicador(null, null)
        val j = JanelaCompose(contexto, p, "indicador") { IndicadorDeRecursos(textoDoIndicador, fonteSp) }
        if (!j.montar()) {
            registrar("falha ao montar")
            return
        }
        indicador = j
        registrar("exibido canto=$canto x=$xDp y=$yDp fonte=${fonteSp}sp")
        iniciarAmostragem()
    }

    private fun registrar(mensagem: String) {
        Log.i(TAG, "[RECURSOS] $mensagem")
        runCatching { Barra.integracoes.registro.registrar("resource_overlay", mapOf("evento" to mensagem)) }
    }

    private fun iniciarAmostragem() {
        amostrador.zerar()
        val t = Thread({
            try {
                amostrador.cpu(lerArquivo("/proc/stat")) // aquece a base
                while (!Thread.currentThread().isInterrupted) {
                    Thread.sleep(AMOSTRA_MS)
                    val cpu = amostrador.cpu(lerArquivo("/proc/stat"))
                    val ram = AmostradorDeRecursos.ram(lerArquivo("/proc/meminfo"))
                    val texto = RegrasDoServico.textoDoIndicador(cpu, ram)
                    ThreadPrincipal.postar { textoDoIndicador = texto }
                }
            } catch (_: InterruptedException) {
            }
        }, "OpenGW.Barra.Recursos")
        t.isDaemon = true
        amostragem = t
        t.start()
    }

    private fun pararAmostragem() {
        amostragem?.interrupt()
        amostragem = null
        amostrador.zerar()
    }

    private fun lerArquivo(caminho: String): String? = runCatching { File(caminho).readText() }.getOrNull()

    /** Caso 40: tira as três. */
    fun desmontarTudo() {
        aviso(false)
        ThreadPrincipal.cancelar(apagarRetorno)
        retorno?.desmontar()
        retorno = null
        indicador?.desmontar()
        indicador = null
        pararAmostragem()
    }

    companion object {
        private const val TAG = "OpenGW.Barra"
        const val RETORNO_MS = 1_800L
        const val AMOSTRA_MS = 2_500L
    }
}

/** O aviso do SPORT+ ligado sozinho (caso 25). */
@Composable
internal fun AvisoDoSportPlus(aoDesfazer: () -> Unit, aoOk: () -> Unit) {
    val vermelho = Color(0xFFD32F2F)
    Box(Modifier.padding(top = 12.dp)) {
        Row(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .background(Color(0xFF17181D), RoundedCornerShape(14.dp))
                .border(1.dp, vermelho.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f, fill = false)) {
                Text(
                    "SPORT+ ativado automaticamente",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Motor a combustão forçado, ESP (controle de tração/estabilidade) desativado e modo " +
                        "alagamento liberado. Volta ao normal automaticamente ao sair do Sport.",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.75f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = aoDesfazer) { Text("Desfazer", color = Color.White) }
                Button(
                    onClick = aoOk,
                    colors = ButtonDefaults.buttonColors(containerColor = vermelho, contentColor = Color.White),
                ) { Text("OK") }
            }
        }
    }
}

/** O indicador de CPU/RAM (caso 27). */
@Composable
internal fun IndicadorDeRecursos(texto: String, fonteSp: Int) {
    Text(
        text = texto,
        fontFamily = FontFamily.Monospace,
        fontSize = fonteSp.sp,
        color = Color(0xFFF5F5F5),
        modifier = Modifier
            .background(Color(0xCC12141A), RoundedCornerShape(6.dp))
            .border(1.dp, Color(0x334A9EFF), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/**
 * Esconder sozinha (caso 36): 30 s, rearmado a cada mudança do que está aberto (o serviço observa) e,
 * com S-D1, a cada toque na janela da barra. Só arma com `esconderSozinha && visivel`.
 */
internal object EsconderSozinha {
    const val TEMPO_MS = 30_000L

    private val vencer = Runnable {
        val s = EstadoDaBarra
        if (s.visivel && !s.algoAberto()) s.visivel = false
    }

    /** Na principal. */
    fun rearmar() {
        ThreadPrincipal.cancelar(vencer)
        if (EstadoDaBarra.esconderSozinha && EstadoDaBarra.visivel) ThreadPrincipal.postarComAtraso(TEMPO_MS, vencer)
    }

    /** Um toque na janela da barra (S-D1). */
    fun tocou() {
        if (DecisoesDaBarra.REARMAR_ESCONDER_A_CADA_TOQUE) rearmar()
    }

    fun cancelar() {
        ThreadPrincipal.cancelar(vencer)
    }
}
