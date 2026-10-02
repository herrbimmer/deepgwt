package br.com.opengw.painel

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import br.com.opengw.nucleo.OpenGw
import br.com.opengw.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/**
 * O diário persistente de diagnóstico do app inteiro (`projetores-painel.md`, Q.1).
 *
 * Ligado só no build de depuração ou de diagnóstico do painel (o que o instalador leva ao carro e o
 * `simulator`) e se a captura não foi desligada (`capture_enabled` em
 * `cluster_persistent_event_logger`). Um arquivo por dia em `<externos>/cluster-diagnostics`,
 * guardados 3 dias. A escrita é numa thread de fundo, em ordem, sem bloquear quem chamou; erro de
 * disco só vai ao logcat. O instalador lê as linhas (`GET /diag`): o formato é contrato.
 */
object Diario {

    private const val ETIQUETA = "OpenGW.Diario"
    private const val ARQUIVO_DE_PREFERENCIAS = "cluster_persistent_event_logger"
    private const val CAPTURA_LIGADA = "capture_enabled"
    const val PASTA = "cluster-diagnostics"

    @Volatile
    private var preferencias: SharedPreferences? = null

    private val escritor: EscritorDoDiario by lazy {
        val fila = Executors.newSingleThreadExecutor { r -> Thread(r, "OpenGW-Diario").apply { isDaemon = true } }
        EscritorDoDiario(
            pasta = { pastaDoDiario() },
            fila = fila,
            epochMs = { System.currentTimeMillis() },
            desdeOBootMs = { SystemClock.elapsedRealtime() },
            zona = { TimeZone.getDefault() },
            erro = { mensagem, causa -> Log.w(ETIQUETA, mensagem, causa) },
        )
    }

    /** Build de depuração ou de diagnóstico do painel, e a captura não desligada. */
    @JvmStatic
    val ligado: Boolean
        get() {
            if (!(BuildConfig.DEBUG || BuildConfig.DIAGNOSTICO_DO_PAINEL)) return false
            return try {
                val sp = preferencias ?: OpenGw.contextoProtegido
                    .getSharedPreferences(ARQUIVO_DE_PREFERENCIAS, Context.MODE_PRIVATE)
                    .also { preferencias = it }
                sp.getBoolean(CAPTURA_LIGADA, true)
            } catch (e: Exception) {
                false
            }
        }

    @JvmStatic
    @JvmOverloads
    fun registrar(evento: String, detalhes: Map<String, Any?> = emptyMap()) {
        if (!ligado) return
        escritor.registrar(evento, detalhes)
    }

    /** = [registrar] com os detalhes `{message}`. */
    @JvmStatic
    fun registrarTexto(evento: String, mensagem: String) {
        registrar(evento, mapOf("message" to mensagem))
    }

    /** As últimas [maximo] linhas do arquivo de hoje (inclui o que ainda estava na fila). */
    @JvmStatic
    @JvmOverloads
    fun ultimasLinhas(maximo: Int = 200): String = try {
        escritor.ultimasLinhas(maximo)
    } catch (e: Exception) {
        ""
    }

    private fun pastaDoDiario(): File? = try {
        val contexto = OpenGw.contextoProtegido
        File(contexto.getExternalFilesDir(null) ?: contexto.filesDir, PASTA)
    } catch (e: Exception) {
        null
    }
}

/** O formato das linhas e dos arquivos do diário (Q.1). Kotlin puro. */
object FormatoDoDiario {
    const val LIMITE_DO_VALOR = 600
    const val DIAS_GUARDADOS = 3

    private val NOME_DO_ARQUIVO = Regex("cluster-events-(\\d{8})\\.log")
    private val FORA_DO_NOME = Regex("[^A-Za-z0-9_.:-]")
    private val ESPACOS = Regex("\\s+")

    /** `cluster-events-AAAAMMDD.log` (data local). */
    @JvmStatic
    fun nomeDoArquivo(epochMs: Long, zona: TimeZone): String = "cluster-events-" + data(epochMs, zona) + ".log"

    /**
     * `yyyy-MM-dd HH:mm:ss.SSS Z epochMs=<ms> elapsedMs=<ms> event=<evento> <k1>=<v1>…`, detalhes em
     * ordem alfabética da chave.
     */
    @JvmStatic
    fun linha(epochMs: Long, desdeOBootMs: Long, zona: TimeZone, evento: String, detalhes: Map<String, Any?>): String {
        val formato = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).apply { timeZone = zona }
        val saida = StringBuilder(128)
        saida.append(formato.format(Date(epochMs)))
            .append(" epochMs=").append(epochMs)
            .append(" elapsedMs=").append(desdeOBootMs)
            .append(" event=").append(nome(evento))
        for (chave in detalhes.keys.sortedWith(compareBy { nome(it) })) {
            saida.append(' ').append(nome(chave)).append('=').append(valor(detalhes[chave]))
        }
        return saida.toString()
    }

    /** Evento e chave: fora de `[A-Za-z0-9_.:-]` vira `_`; vazio vira `unknown`. */
    @JvmStatic
    fun nome(texto: String?): String {
        if (texto.isNullOrEmpty()) return "unknown"
        return FORA_DO_NOME.replace(texto, "_")
    }

    /** Valor: null → `null`; vazio → `empty`; espaços em sequência → `_`; `|` → `/`; até 600 caracteres. */
    @JvmStatic
    fun valor(valor: Any?): String {
        if (valor == null) return "null"
        val texto = valor.toString()
        if (texto.isEmpty()) return "empty"
        return ESPACOS.replace(texto, "_").replace('|', '/').take(LIMITE_DO_VALOR)
    }

    /** Dos [nomes] de uma pasta, os do diário mais velhos que 3 dias (hoje e os 2 anteriores ficam). */
    @JvmStatic
    fun paraApagar(nomes: List<String>, agoraMs: Long, zona: TimeZone): List<String> {
        val calendario = Calendar.getInstance(zona, Locale.US).apply {
            timeInMillis = agoraMs
            add(Calendar.DAY_OF_MONTH, -(DIAS_GUARDADOS - 1))
        }
        val limite = data(calendario.timeInMillis, zona)
        return nomes.filter { nome ->
            val dia = NOME_DO_ARQUIVO.matchEntire(nome)?.groupValues?.get(1) ?: return@filter false
            dia < limite
        }
    }

    private fun data(epochMs: Long, zona: TimeZone): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = zona }.format(Date(epochMs))
}

/** A escrita do diário numa fila de uma thread só. Sem Android (para os testes). */
class EscritorDoDiario(
    private val pasta: () -> File?,
    private val fila: Executor,
    private val epochMs: () -> Long,
    private val desdeOBootMs: () -> Long,
    private val zona: () -> TimeZone,
    private val erro: (String, Throwable?) -> Unit,
) {
    companion object {
        const val INTERVALO_DA_LIMPEZA_MS = 60_000L
    }

    /** Só na thread da fila. */
    private var ultimaLimpeza = Long.MIN_VALUE

    fun registrar(evento: String, detalhes: Map<String, Any?>) {
        val epoch = epochMs()
        val boot = desdeOBootMs()
        val copia = LinkedHashMap(detalhes)
        try {
            fila.execute { escrever(epoch, boot, evento, copia) }
        } catch (e: Exception) {
            erro("diário: fila recusou $evento", e)
        }
    }

    fun ultimasLinhas(maximo: Int): String {
        val tarefa = FutureTask<String> {
            val diretorio = pasta()
            val arquivo = diretorio?.let { File(it, FormatoDoDiario.nomeDoArquivo(epochMs(), zona())) }
            if (arquivo == null || !arquivo.isFile) {
                ""
            } else {
                arquivo.readLines().takeLast(maximo.coerceAtLeast(0)).joinToString("\n")
            }
        }
        fila.execute(tarefa)
        return tarefa.get(2, TimeUnit.SECONDS)
    }

    private fun escrever(epoch: Long, boot: Long, evento: String, detalhes: Map<String, Any?>) {
        try {
            val diretorio = pasta() ?: return
            if (!diretorio.isDirectory) diretorio.mkdirs()
            limparSePreciso(diretorio, epoch, boot)
            val z = zona()
            val linha = FormatoDoDiario.linha(epoch, boot, z, evento, detalhes)
            File(diretorio, FormatoDoDiario.nomeDoArquivo(epoch, z)).appendText(linha + "\n")
        } catch (e: Exception) {
            erro("diário: não gravou $evento", e)
        }
    }

    private fun limparSePreciso(diretorio: File, epoch: Long, boot: Long) {
        if (ultimaLimpeza != Long.MIN_VALUE && boot - ultimaLimpeza < INTERVALO_DA_LIMPEZA_MS) return
        ultimaLimpeza = boot
        val nomes = diretorio.list()?.toList().orEmpty()
        for (nome in FormatoDoDiario.paraApagar(nomes, epoch, zona())) {
            if (!File(diretorio, nome).delete()) erro("diário: não apagou $nome", null)
        }
    }
}
