package br.com.opengw.painel

import android.content.Context
import android.content.SharedPreferences
import android.content.res.AssetManager
import android.util.Log
import br.com.opengw.nucleo.OpenGw
import br.com.opengw.ponte.ContratoV1
import br.com.opengw.ponte.PonteDosTemas
import java.io.File
import java.io.InputStream
import java.util.Collections

/**
 * Os temas do painel guardados em `<filesDir>/themes/<pasta>/` (`projetores-temas.md`).
 *
 * Semeia os temas do APK, poda os aposentados, lê `theme.xml`, diz se um tema é suportado, dá os
 * arquivos do tema com caminho seguro, volta ao embutido e apaga. Quem decide QUAL tema aplicar é
 * código nosso fora daqui (`TemaDoPainel`).
 *
 * Erro de leitura (pasta gravada pelo instalador sem dono/contexto certos, XML quebrado) vira "não
 * existe"; nada aqui lança.
 */
object TemasInstalados {

    private const val ETIQUETA = "OpenGW.Temas"

    @Volatile
    private var catalogo: CatalogoDeTemas? = null

    private val trava = Any()
    private var iniciado = false

    @Volatile
    private var semeado = false
    private val aoTerminar = ArrayList<Runnable>()

    /**
     * Uma vez, no início do app (antes do projetor): poda as pastas aposentadas e semeia as do APK.
     * Volta na hora: o trabalho (cópia de alguns MB na primeira vez, SHA-256 de todos os temas em
     * toda abertura) roda numa thread própria (Decisão 1). O que depende dele vai em [quandoPronto].
     *
     * [garantirTemaDoPainelInteiro] é chamada no fim de [sanearTemaAtivo] e de [apagar] (a sala suja
     * passa `{ TemaDoPainel.garantirTemaDoPainelInteiro(contexto, prefs) }`).
     */
    @JvmStatic
    fun iniciar(contexto: Context, garantirTemaDoPainelInteiro: () -> Unit) {
        val novo: CatalogoDeTemas
        synchronized(trava) {
            if (iniciado) return
            iniciado = true
            novo = criar(contexto, garantirTemaDoPainelInteiro)
            catalogo = novo
        }
        Thread({
            try {
                novo.semearAgora()
            } catch (e: Throwable) {
                Log.e(ETIQUETA, "semear os temas falhou", e)
            }
            val tarefas: List<Runnable>
            synchronized(trava) {
                semeado = true
                tarefas = aoTerminar.toList()
                aoTerminar.clear()
            }
            for (tarefa in tarefas) rodar(tarefa)
        }, "OpenGW-Temas").apply { isDaemon = true }.start()
    }

    /**
     * Roda [tarefa] quando a poda e a semeadura terminarem (na thread delas), ou já (na thread de quem
     * chamou) se já terminaram. Ordem do início do app: `iniciar`, depois, aqui dentro,
     * `TemaDoPainel.encerrarRascunhoEsquecido`, `CalibracaoDoPainel.conferirNaSubida`, [sanearTemaAtivo].
     */
    @JvmStatic
    fun quandoPronto(tarefa: Runnable) {
        synchronized(trava) {
            if (!semeado) {
                aoTerminar.add(tarefa)
                return
            }
        }
        rodar(tarefa)
    }

    /** A semeadura desta abertura já terminou. */
    @JvmStatic
    val pronto: Boolean
        get() = semeado

    /** As pastas que foram (re)semeadas nesta abertura do app. */
    internal val semeadasNestaAbertura: Set<String>
        get() = catalogo?.semeadas ?: emptySet()

    @JvmStatic
    val pastaDosTemas: File
        get() = atual().pastaDosTemas

    @JvmStatic
    val pastasDoApk: Set<String>
        get() = atual().pastasDoApk

    /** Só os suportados; ordem livre (quem chama ordena). */
    @JvmStatic
    fun listar(): List<TemaInstalado> = atual().listar()

    @JvmStatic
    fun ler(pasta: String): TemaInstalado? = atual().ler(pasta)

    /** A página do painel original. */
    @JvmStatic
    fun embutido(): TemaInstalado = CatalogoDeTemas.EMBUTIDO

    @JvmStatic
    fun suportado(tema: TemaInstalado): Boolean = CatalogoDeTemas.suportado(tema)

    @JvmStatic
    fun contratoAceito(versao: String?): Boolean = ContratoV1.contratoAceito(versao)

    @JvmStatic
    fun arquivo(pasta: String, nome: String): File? = atual().arquivo(pasta, nome)

    @JvmStatic
    fun arquivoDeFundoDoTemaAtivo(dica: String?): File? = atual().arquivoDeFundoDoTemaAtivo(dica)

    @JvmStatic
    fun caminhoDoFundo(pasta: String): String = atual().caminhoDoFundo(pasta)

    @JvmStatic
    fun pastaInvalida(pasta: String?): Boolean = atual().pastaInvalida(pasta)

    @JvmStatic
    @JvmOverloads
    fun voltarAoEmbutido(carimbar: Boolean = true) {
        atual().voltarAoEmbutido(carimbar)
    }

    @JvmStatic
    fun sanearTemaAtivo() {
        atual().sanearTemaAtivo()
    }

    @JvmStatic
    fun apagar(pasta: String): Boolean = atual().apagar(pasta)

    /** Testes: troca o catálogo (null = volta ao do app). */
    internal fun usarParaTeste(outro: CatalogoDeTemas?) {
        synchronized(trava) { catalogo = outro }
    }

    private fun atual(): CatalogoDeTemas {
        catalogo?.let { return it }
        synchronized(trava) {
            catalogo?.let { return it }
            val novo = criar(OpenGw.contexto, {})
            catalogo = novo
            return novo
        }
    }

    private fun criar(contexto: Context, garantir: () -> Unit): CatalogoDeTemas {
        val app = contexto.applicationContext ?: contexto
        return CatalogoDeTemas(
            pastaDosTemas = File(app.filesDir, CatalogoDeTemas.PASTA),
            preferencias = { PonteDosTemas.preferenciasDoApp() },
            pacote = PacoteNosAssets(app.assets),
            garantir = garantir,
            registro = RegistroNoLogcat,
        )
    }

    private fun rodar(tarefa: Runnable) {
        try {
            tarefa.run()
        } catch (e: Throwable) {
            Log.e(ETIQUETA, "tarefa depois da semeadura falhou", e)
        }
    }

    private object RegistroNoLogcat : CatalogoDeTemas.Registro {
        override fun info(mensagem: String) {
            Log.i(ETIQUETA, mensagem)
        }

        override fun erro(mensagem: String, causa: Throwable?) {
            Log.e(ETIQUETA, mensagem, causa)
        }
    }
}

/** Os temas que vêm no APK. */
interface PacoteDeTemas {
    fun pastas(): List<String>
    fun arquivos(pasta: String): List<String>
    fun abrir(pasta: String, arquivo: String): InputStream
}

/** `assets/PreinstalledThemes/<pasta>/<arquivo>` (só arquivos; as pastas do APK são planas). */
internal class PacoteNosAssets(private val assets: AssetManager) : PacoteDeTemas {
    override fun pastas(): List<String> = assets.list(RAIZ)?.toList().orEmpty()

    override fun arquivos(pasta: String): List<String> =
        assets.list("$RAIZ/$pasta")?.filter { nome -> assets.list("$RAIZ/$pasta/$nome").isNullOrEmpty() }.orEmpty()

    override fun abrir(pasta: String, arquivo: String): InputStream = assets.open("$RAIZ/$pasta/$arquivo")

    companion object {
        const val RAIZ = "PreinstalledThemes"
    }
}

/**
 * O trabalho de [TemasInstalados] sobre uma pasta, umas preferências e um pacote — sem `Context`,
 * para rodar nos testes de unidade.
 */
class CatalogoDeTemas(
    val pastaDosTemas: File,
    private val preferencias: () -> SharedPreferences,
    private val pacote: PacoteDeTemas,
    private val garantir: () -> Unit,
    private val registro: Registro,
    private val agora: () -> Long = { System.currentTimeMillis() },
) {
    interface Registro {
        fun info(mensagem: String)
        fun erro(mensagem: String, causa: Throwable? = null)
    }

    companion object {
        const val PASTA = "themes"
        const val ARQUIVO_DO_TEMA = "theme.xml"
        const val MARCA_DA_SEMEADURA = ".opengw-seed"

        /** Pastas aposentadas: apagadas em toda abertura. Nunca `Default`. */
        @JvmField
        val APOSENTADAS: List<String> = listOf(
            "minimalist", "sportred", "sportredlite", "hybriddigital", "horizon", "nordic", "bavaro", "stuttgart-ntg55",
        )

        /** O tema da página do painel original (sem pasta em disco). */
        @JvmField
        val EMBUTIDO = TemaInstalado(
            pasta = PreferenciasDoPainel.TEMA_EMBUTIDO,
            nome = PreferenciasDoPainel.TEMA_EMBUTIDO,
            descricao = "Tema principal com o novo design Sport.",
            versao = "1.1.6",
            miniatura = "",
            arquivoPrincipal = LeitorDoTema.ARQUIVO_PRINCIPAL_PADRAO,
            local = true,
            areaDosApps = AreaDosApps(0, 62, 1920, 596),
            descentralizado = false,
            versaoMinimaDaPonte = "1.0.0",
            versaoDoContrato = ContratoV1.VERSAO_DO_CONTRATO,
            ajustes = emptyList(),
            modosDeExibicao = emptyList(),
            fundo = "",
            fundoAbsoluto = "",
            mascaras = null,
        )

        @JvmStatic
        fun suportado(tema: TemaInstalado): Boolean =
            ContratoV1.contratoAceito(tema.versaoDoContrato) && ContratoV1.versaoDaPonteAceita(tema.versaoMinimaDaPonte)

        /** "" ou `Default` (qualquer caixa) = o embutido. */
        fun ehEmbutido(pasta: String?): Boolean =
            pasta == null || pasta.isBlank() || pasta.trim().equals(PreferenciasDoPainel.TEMA_EMBUTIDO, ignoreCase = true)
    }

    /** As pastas semeadas nesta abertura. */
    val semeadas: MutableSet<String> = Collections.synchronizedSet(LinkedHashSet())

    @Volatile
    private var cacheDasPastasDoApk: Set<String>? = null

    val pastasDoApk: Set<String>
        get() {
            cacheDasPastasDoApk?.let { return it }
            val lidas = try {
                pacote.pastas().toCollection(LinkedHashSet())
            } catch (e: Exception) {
                registro.erro("listar os temas do APK falhou", e)
                emptySet<String>()
            }
            cacheDasPastasDoApk = lidas
            return lidas
        }

    // ═══════════════════════════ A. Início ═══════════════════════════

    /** Poda e semeia agora, na thread de quem chamou. */
    fun semearAgora() {
        pastaDosTemas.mkdirs()
        for (nome in APOSENTADAS) {
            val pasta = File(pastaDosTemas, nome)
            if (pasta.exists()) {
                val apagou = pasta.deleteRecursively()
                registro.info("tema aposentado $nome ${if (apagou) "apagado" else "não pôde ser apagado"}")
            }
        }
        for (pasta in pastasDoApk) {
            try {
                semearUma(pasta)
            } catch (e: Exception) {
                registro.erro("semear $pasta falhou", e)
            }
        }
    }

    private fun semearUma(pasta: String) {
        val arquivos = pacote.arquivos(pasta)
        if (ARQUIVO_DO_TEMA !in arquivos) {
            registro.info("tema do APK $pasta sem theme.xml: pulado")
            return
        }
        val empacotado = pacote.abrir(pasta, ARQUIVO_DO_TEMA).use { LeitorDoTema.ler(it) { m -> registro.info("$pasta: $m") } }
        if (empacotado == null) {
            registro.info("tema do APK $pasta sem <name> ou ilegível: pulado")
            return
        }
        val impressao = VersoesDeTema.impressao(arquivos) { pacote.abrir(pasta, it) }
        val destino = File(pastaDosTemas, pasta)
        val xmlEmDisco = File(destino, ARQUIVO_DO_TEMA)
        val versaoInstalada = if (xmlEmDisco.isFile) lerArquivo(xmlEmDisco, pasta)?.versao else null
        val marca = try {
            File(destino, MARCA_DA_SEMEADURA).takeIf { it.isFile }?.readText()?.trim()
        } catch (e: Exception) {
            null
        }
        val decisao = VersoesDeTema.decidir(marca, versaoInstalada, empacotado.versao, impressao)
        if (decisao == VersoesDeTema.Decisao.MANTER) return
        destino.mkdirs()
        for (nome in arquivos) {
            pacote.abrir(pasta, nome).use { entrada -> File(destino, nome).outputStream().use { entrada.copyTo(it) } }
        }
        semeadas.add(pasta)
        try {
            File(destino, MARCA_DA_SEMEADURA).writeText(impressao)
        } catch (e: Exception) {
            registro.erro("gravar a marca de $pasta falhou", e)
        }
        registro.info("tema $pasta semeado (versão ${empacotado.versao}, instalada $versaoInstalada)")
    }

    // ═══════════════════════════ B. Leitura ═══════════════════════════

    fun listar(): List<TemaInstalado> {
        val pastas = try {
            pastaDosTemas.listFiles()?.filter { it.isDirectory }.orEmpty()
        } catch (e: Exception) {
            emptyList()
        }
        val saida = ArrayList<TemaInstalado>()
        for (pasta in pastas) {
            val nome = pasta.name
            if (nome.startsWith(".") || ehEmbutido(nome)) continue
            val tema = ler(nome) ?: continue
            if (suportado(tema)) saida.add(tema)
        }
        return saida
    }

    fun ler(pasta: String): TemaInstalado? {
        val diretorio = diretorioSeguro(pasta) ?: return null
        val xml = File(diretorio, ARQUIVO_DO_TEMA)
        if (!xml.isFile) return null
        val lido = lerArquivo(xml, pasta) ?: return null
        return montar(pasta, lido)
    }

    private fun lerArquivo(xml: File, pasta: String): TemaLido? = try {
        xml.inputStream().use { LeitorDoTema.ler(it) { m -> registro.info("$pasta: $m") } }
    } catch (e: Exception) {
        registro.erro("ler $pasta/theme.xml falhou", e)
        null
    }

    private fun montar(pasta: String, lido: TemaLido): TemaInstalado {
        val miniatura = when {
            lido.miniatura.isEmpty() -> ""
            lido.miniatura.startsWith("http") -> lido.miniatura
            else -> File(File(pastaDosTemas, pasta), lido.miniatura).absolutePath
        }
        val fundoAbsoluto = lido.fundo.takeIf { it.isNotEmpty() }?.let { arquivo(pasta, it)?.absolutePath }.orEmpty()
        return TemaInstalado(
            pasta = pasta,
            nome = lido.nome,
            descricao = lido.descricao,
            versao = lido.versao.orEmpty(),
            miniatura = miniatura,
            arquivoPrincipal = lido.arquivoPrincipal,
            local = true,
            areaDosApps = lido.areaDosApps,
            descentralizado = lido.descentralizado,
            versaoMinimaDaPonte = lido.versaoMinimaDaPonte,
            versaoDoContrato = lido.versaoDoContrato,
            ajustes = lido.ajustes,
            modosDeExibicao = lido.modosDeExibicao,
            fundo = lido.fundo,
            fundoAbsoluto = fundoAbsoluto,
            mascaras = lido.mascaras,
        )
    }

    // ═══════════════════════════ B.7 / D. Arquivos ═══════════════════════════

    /** A pasta canônica, se a pasta é segura (B.7) e existe; senão null. */
    fun diretorioSeguro(pasta: String?): File? {
        if (pasta.isNullOrEmpty()) return null
        if (pasta.contains('/') || pasta.contains('\\') || File(pasta).name != pasta) return null
        return try {
            val raiz = pastaDosTemas.canonicalFile
            val diretorio = File(raiz, pasta).canonicalFile
            if (diretorio.parentFile != raiz) null else diretorio.takeIf { it.isDirectory }
        } catch (e: Exception) {
            null
        }
    }

    /** O arquivo [nome] dentro da pasta (caminho canônico dentro dela, e é arquivo); senão null. */
    fun arquivo(pasta: String, nome: String): File? {
        if (nome.isEmpty()) return null
        val diretorio = diretorioSeguro(pasta) ?: return null
        return try {
            val alvo = File(diretorio, nome).canonicalFile
            if (alvo.path.startsWith(diretorio.path + File.separator) && alvo.isFile) alvo else null
        } catch (e: Exception) {
            null
        }
    }

    fun arquivoDeFundoDoTemaAtivo(dica: String?): File? {
        val pasta = pastaAtiva()
        if (ehEmbutido(pasta)) return null
        val relativo = dica?.trim()?.takeIf { it.isNotEmpty() } ?: ler(pasta)?.fundo?.takeIf { it.isNotEmpty() } ?: return null
        var normalizado = relativo.replace('\\', '/')
        while (normalizado.startsWith("./")) normalizado = normalizado.substring(2)
        val nomeBase = normalizado.substringAfterLast('/')
        if (nomeBase.isEmpty() || normalizado.contains("..")) return null
        return arquivo(pasta, normalizado) ?: arquivo(pasta, nomeBase)
    }

    fun caminhoDoFundo(pasta: String): String {
        if (ehEmbutido(pasta)) return ""
        return ler(pasta)?.fundoAbsoluto.orEmpty()
    }

    /** C.4: vazia/null/`Default` → não (é o embutido); sem `theme.xml` legível ou não suportada → sim. */
    fun pastaInvalida(pasta: String?): Boolean {
        if (ehEmbutido(pasta)) return false
        val tema = ler(pasta!!.trim()) ?: return true
        return !suportado(tema)
    }

    // ═══════════════════════════ E. Voltar, sanear, apagar ═══════════════════════════

    fun voltarAoEmbutido(carimbar: Boolean) {
        val editor = preferencias().edit()
            .putString(PreferenciasDoPainel.TEMA_ATIVO, "")
            .putString(PreferenciasDoPainel.NOME_DO_TEMA, PreferenciasDoPainel.TEMA_EMBUTIDO)
        if (carimbar) editor.putLong(PreferenciasDoPainel.CARIMBO_DO_TEMA, agora())
        editor.apply()
    }

    fun sanearTemaAtivo() {
        try {
            val p = PreferenciasDoPainel(preferencias())
            val pasta = p.texto(PreferenciasDoPainel.TEMA_ATIVO, "")
            val nome = p.texto(PreferenciasDoPainel.NOME_DO_TEMA, "").trim()
            val pastaRuim = pastaInvalida(pasta)
            val nomeRuim = nome.isNotEmpty() &&
                !nome.equals(PreferenciasDoPainel.TEMA_EMBUTIDO, ignoreCase = true) &&
                listar().none { it.nome.equals(nome, ignoreCase = true) }
            if (pastaRuim || nomeRuim) {
                registro.info("tema ativo inválido (pasta=$pasta inválida=$pastaRuim, nome=$nome inválido=$nomeRuim): volta ao embutido")
                voltarAoEmbutido(true)
            }
        } catch (e: Exception) {
            registro.erro("sanear o tema ativo falhou", e)
        }
        garantirSeguro()
    }

    fun apagar(pasta: String): Boolean {
        if (ehEmbutido(pasta)) return false
        val diretorio = diretorioSeguro(pasta) ?: return false
        val apagou = try {
            diretorio.deleteRecursively()
        } catch (e: Exception) {
            registro.erro("apagar $pasta falhou", e)
            false
        }
        if (apagou && pastaAtiva() == pasta) {
            voltarAoEmbutido(true)
            garantirSeguro()
        }
        return apagou
    }

    private fun pastaAtiva(): String = try {
        PreferenciasDoPainel(preferencias()).texto(PreferenciasDoPainel.TEMA_ATIVO, "").trim()
    } catch (e: Exception) {
        ""
    }

    private fun garantirSeguro() {
        try {
            garantir()
        } catch (e: Exception) {
            registro.erro("garantir o tema do painel inteiro falhou", e)
        }
    }
}
