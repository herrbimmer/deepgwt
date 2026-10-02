package br.com.opengw.diagnostics

import br.com.opengw.nucleo.OpenGw
import br.com.opengw.veiculo.Veiculo
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.util.Log
import br.com.opengw.BuildConfig
import br.com.opengw.managers.RemoteControlOps
import br.com.opengw.managers.TemaDoPainel
import br.com.opengw.painel.TemasInstalados
import br.com.opengw.veiculo.vhal.CatalogoVhal
import br.com.opengw.veiculo.vhal.Vhal
import br.com.opengw.veiculo.Sinais
import br.com.opengw.nucleo.Preferencias
import br.com.opengw.ui.screens.ModoDoPainel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Calibração do painel (Alpha 8.8): descobrir onde o carro desenha luzes e avisos POR CIMA do tema.
 *
 * ## POR QUE EXISTE
 *
 * O painel de fábrica compõe READY, placa da via, luzes e mensagens acima da WebView do tema, e
 * nenhum tema consegue escondê-los. Só três posições eram conhecidas, medidas no desenho do tema
 * Default e nunca no carro; o resto estava "A confirmar" no THEME_GUIDE. Sem as posições, os temas
 * põem ponteiro, número e placa em cima dos avisos originais.
 *
 * ## O QUE FAZ
 *
 * - Troca o painel para uma grade de coordenadas 1920×720 (assets/CalibracaoDoPainel), guardando o
 *   tema de antes. Fotografada, a grade dá a posição de cada luz em coordenadas do tema.
 * - Grava uma linha do tempo das chaves de luz e aviso (a cada meio segundo) com o relógio da
 *   central, o mesmo que a grade mostra: foto e vídeo casam com os valores.
 * - Cada "Capturar" guarda todas as chaves do carro, os sinais VHAL de lâmpada, o `screencap` de
 *   cada tela e a lista de camadas da SurfaceFlinger. Se a luz sair no `screencap`, ela é uma
 *   camada Android; se só a foto a mostrar, é desenhada fora do Android (QNX).
 * - Ao encerrar, volta ao tema de antes e fecha tudo num .zip em /sdcard/OpenGW/calibracao, que o
 *   instalador baixa em Arquivos.
 *
 * ## SEGURANÇA
 *
 * Com a grade no ar o painel fica sem velocímetro. Por isso só começa com o carro parado, volta ao
 * tema de antes assim que a velocidade passa de 1 km/h e acaba sozinha em 30 minutos, inclusive
 * se a central reiniciar no meio (ver [conferirNaSubida]). A grade também avisa na tela se o carro
 * andar. Nada aqui escreve no carro: é leitura de chaves, de sinais e de telas.
 */
object CalibracaoDoPainel {
    private const val TAG = "CalibracaoDoPainel"

    /** Onde os arquivos do tema estão no APK. */
    private const val ASSET = "CalibracaoDoPainel"

    /** O tema de antes, para voltar. Estado passageiro: fora dos perfis (`EscopoDoPerfil`). */
    const val CHAVE_ANTES = "temaAntesDaCalibracao"
    const val CHAVE_DESDE = "calibracaoDesde"
    const val CHAVE_SESSAO = "calibracaoSessao"

    /** A preferência que o tema lê (`Android.getPreference` da ponte: theme_config_<pasta>_<variável>). */
    const val CHAVE_FUNDO = "theme_config_${TemaDoPainel.PASTA_DA_CALIBRACAO}_fundo"

    /** Tempo para a grade subir no painel antes da primeira captura automática. */
    private const val ESPERA_DA_GRADE_MS = 5_000L

    private val AUTOMATICOS = mapOf(
        "inicio" to "Início (automática)",
        "subida" to "Depois de religar (automática)",
    )

    fun rotuloDe(passoId: String): String =
        AUTOMATICOS[passoId] ?: RegrasDaCalibracao.PASSOS.firstOrNull { it.id == passoId }?.rotulo ?: passoId

    data class Captura(
        val numero: Int,
        val passo: String,
        val hora: String,
        /** Uma linha por tela: "4: 1920×720 salva", "3: toda #000000"... */
        val telas: List<String> = emptyList(),
        val chaves: Int = 0,
        /** Só as mudanças de luz e aviso desde a captura anterior (o arquivo tem todas). */
        val mudancas: List<RegrasDaCalibracao.Mudanca> = emptyList(),
    )

    data class Estado(
        val ativa: Boolean = false,
        val desdeMs: Long = 0L,
        val sessao: String? = null,
        val capturas: List<Captura> = emptyList(),
        /** Texto do progresso enquanto uma captura roda; null parado. */
        val capturando: String? = null,
        val fundoTransparente: Boolean = false,
        /** O .zip da última sessão encerrada. */
        val pacote: String? = null,
        val mensagem: String? = null,
    )

    private val _estado = MutableStateFlow(Estado())
    val estado: StateFlow<Estado> = _estado

    @Volatile private var gravador: Thread? = null
    @Volatile private var rodando = false
    @Volatile private var sessao: File? = null
    @Volatile private var desde: Long = 0L
    private val travaDaCaptura = Any()
    private val travaDoArquivo = Any()
    private var ultimaLeituraCompleta: Map<String, String> = emptyMap()

    sealed class Resultado {
        data class Feito(val mensagem: String) : Resultado()
        data class Recusado(val motivo: String) : Resultado()
    }

    private fun prefs(): SharedPreferences =
        OpenGw.contextoProtegido.getSharedPreferences("opengw_prefs", Context.MODE_PRIVATE)

    fun ativa(prefs: SharedPreferences = prefs()): Boolean =
        TemaDoPainel.ativo(prefs) == TemaDoPainel.PASTA_DA_CALIBRACAO

    /** Pasta onde as sessões ficam; é a que o instalador abre em Arquivos. */
    fun raiz(context: Context): File {
        val publica = File(Environment.getExternalStorageDirectory(), "OpenGW/calibracao")
        if (publica.isDirectory || publica.mkdirs()) {
            if (publica.canWrite()) return publica
        }
        val doApp = context.getExternalFilesDir("calibracao")
        if (doApp != null && (doApp.isDirectory || doApp.mkdirs())) return doApp
        return File(context.filesDir, "calibracao").also { it.mkdirs() }
    }

    // ── Começar e encerrar ───────────────────────────────────────────────────────────────────

    /** Liga a grade e começa a gravar. Chamar fora da thread principal (copia arquivos). */
    fun iniciar(context: Context): Resultado {
        val prefs = prefs()
        if (ativa(prefs) && rodando) return Resultado.Feito("A calibração já está ligada.")
        val velocidade = runCatching {
            Veiculo.ler(Sinais.CAR_BASIC_VEHICLE_SPEED.chave)
        }.getOrNull()
        if (RegrasDaCalibracao.fim(System.currentTimeMillis(), 0L, velocidade) == RegrasDaCalibracao.Fim.ANDOU) {
            return Resultado.Recusado("O carro está andando: a calibração é feita com o carro parado.")
        }
        if (!ModoDoPainel.ler(prefs).desenhaOPainelInteiro) {
            return Resultado.Recusado(
                "A grade é um tema do painel inteiro: ligue o Tema customizado em Telas e tente de novo."
            )
        }
        if (!instalarTema(context)) return Resultado.Recusado("Não foi possível copiar a grade para a pasta de temas.")
        val meta = TemasInstalados.ler(TemaDoPainel.PASTA_DA_CALIBRACAO)
            ?.takeIf { TemasInstalados.suportado(it) }
            ?: return Resultado.Recusado("A grade não abriu como tema (theme.xml ilegível).")

        if (!ativa(prefs)) {
            val antes = JSONObject()
                .put("pasta", TemaDoPainel.ativo(prefs))
                .put("nome", prefs.getString(Preferencias.VIRTUAL_CLUSTER_THEME.chave, "Default") ?: "Default")
            prefs.edit().putString(CHAVE_ANTES, antes.toString()).apply()
        }
        val agora = System.currentTimeMillis()
        val dir = File(raiz(context), SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(agora)))
        dir.mkdirs()
        prefs.edit()
            .putLong(CHAVE_DESDE, agora)
            .putString(CHAVE_SESSAO, dir.absolutePath)
            .apply()
        TemaDoPainel.aplicar(prefs, meta)

        escreverLeiaMe(dir)
        escreverSessao(context, dir, agora, prefs)
        comecar(dir, agora, retomada = false)
        registrar("inicio", "", "", "grade no painel; tema de antes: ${lerAntes(prefs).optString("nome")}")
        val texto = "Grade no painel. Gravando em ${dir.absolutePath}"
        _estado.update { it.copy(mensagem = texto) }
        return Resultado.Feito(texto)
    }

    /** Volta ao tema de antes, para de gravar e fecha o pacote. Chamar fora da thread principal. */
    fun encerrar(context: Context, motivo: String = "pelo botão Encerrar"): Resultado {
        parar()
        val prefs = prefs()
        if (ativa(prefs)) voltarAoTemaDeAntes(context, prefs)
        val pacote = finalizar(context, motivo)
        return Resultado.Feito(
            if (pacote != null) "Calibração encerrada. Pacote: ${pacote.absolutePath}" else "Calibração encerrada."
        )
    }

    /**
     * Na subida do app, antes do projetor: a central pode ter reiniciado no meio da calibração
     * (desligar e religar o carro faz parte dela, para ver as luzes de teste da partida). Dentro
     * dos 30 minutos a sessão continua na mesma pasta; depois disso, ou se outro tema foi posto no
     * lugar, o painel volta ao tema de antes JÁ, e o pacote é fechado em segundo plano.
     */
    fun conferirNaSubida(context: Context, prefs: SharedPreferences) {
        val desdeSalvo = prefs.getLong(CHAVE_DESDE, 0L)
        val estaAtiva = ativa(prefs)
        if (desdeSalvo == 0L && !estaAtiva) return
        val expirou = desdeSalvo == 0L ||
            RegrasDaCalibracao.fim(System.currentTimeMillis(), desdeSalvo, null) != null
        val caminho = prefs.getString(CHAVE_SESSAO, null)
        if (!estaAtiva || expirou) {
            if (estaAtiva) voltarAoTemaDeAntes(context, prefs)
            if (caminho != null) sessao = File(caminho)
            val motivo = if (expirou) RegrasDaCalibracao.Fim.TEMPO.texto else "outro tema foi escolhido no painel"
            Thread({ finalizar(context, motivo) }, "calibracao-fim").start()
            return
        }
        val dir = caminho?.let { File(it) }?.takeIf { it.isDirectory || it.mkdirs() }
            ?: File(raiz(context), SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())).also { it.mkdirs() }
        Log.i(TAG, "Calibração retomada na subida do app: ${dir.absolutePath}")
        if (!File(dir, "LEIA-ME.txt").exists()) escreverLeiaMe(dir)
        if (!File(dir, "sessao.txt").exists()) escreverSessao(context, dir, desdeSalvo, prefs)
        comecar(dir, desdeSalvo, retomada = true)
        registrar("subida do app", "", "", "a central ou o app reiniciou durante a calibração")
        _estado.update { it.copy(mensagem = "Calibração retomada depois de religar: a grade continua no painel.") }
    }

    fun definirFundo(transparente: Boolean) {
        prefs().edit().putString(CHAVE_FUNDO, if (transparente) "Transparente" else "Preto").apply()
        _estado.update { it.copy(fundoTransparente = transparente) }
        registrar("fundo", "", "", if (transparente) "transparente" else "preto")
    }

    private fun comecar(dir: File, desdeMs: Long, retomada: Boolean) {
        parar()
        sessao = dir
        desde = desdeMs
        ultimaLeituraCompleta = emptyMap()
        val existentes = capturasNaPasta(dir)
        _estado.value = Estado(
            ativa = true,
            desdeMs = desdeMs,
            sessao = dir.absolutePath,
            capturas = existentes,
            fundoTransparente = (prefs().getString(CHAVE_FUNDO, "Preto") ?: "Preto").startsWith("Transp"),
        )
        rodando = true
        val t = Thread({ laco(dir, desdeMs, retomada) }, "calibracao-painel")
        t.isDaemon = true
        gravador = t
        t.start()
    }

    private fun parar() {
        rodando = false
        val t = gravador
        gravador = null
        if (t != null && t !== Thread.currentThread()) t.interrupt()
    }

    private fun instalarTema(context: Context): Boolean = runCatching {
        val destino = File(File(context.filesDir, "themes"), TemaDoPainel.PASTA_DA_CALIBRACAO)
        destino.mkdirs()
        val arquivos = context.assets.list(ASSET).orEmpty()
        arquivos.forEach { nome ->
            context.assets.open("$ASSET/$nome").use { entrada ->
                File(destino, nome).outputStream().use { saida -> entrada.copyTo(saida) }
            }
        }
        File(destino, "theme.xml").isFile
    }.onFailure { Log.e(TAG, "Falha ao instalar a grade", it) }.getOrDefault(false)

    private fun lerAntes(prefs: SharedPreferences): JSONObject =
        runCatching { JSONObject(prefs.getString(CHAVE_ANTES, null) ?: "{}") }.getOrDefault(JSONObject())

    /**
     * O tema de antes volta sem passar pela régua do nível: é o que já estava no painel, não uma
     * escolha nova (e na subida do app o nível ainda nem foi conferido). Sem ele, o embutido, que
     * com o tema customizado ligado vira o tema padrão.
     */
    private fun voltarAoTemaDeAntes(context: Context, prefs: SharedPreferences) {
        val pasta = lerAntes(prefs).optString("pasta", TemaDoPainel.PASTA_EMBUTIDA)
        val meta = pasta.takeIf { it.isNotBlank() && it != TemaDoPainel.PASTA_DA_CALIBRACAO }
            ?.let { TemasInstalados.ler(it) }
            ?.takeIf { TemasInstalados.suportado(it) }
        if (meta != null) {
            TemaDoPainel.aplicar(prefs, meta)
        } else {
            TemaDoPainel.aplicarPorPasta(context, prefs, TemaDoPainel.PASTA_EMBUTIDA)
        }
        Log.i(TAG, "Painel de volta ao tema de antes da calibração: '${TemaDoPainel.ativo(prefs)}'")
    }

    /**
     * Fecha a sessão: linha final, pacote, preferências e a pasta da grade. Devolve o .zip. Espera
     * a captura em andamento terminar, para o pacote não sair sem ela.
     */
    private fun finalizar(context: Context, motivo: String): File? = synchronized(travaDaCaptura) {
        val dir = sessao
        registrar("fim", "", "", motivo)
        val pacote = dir?.takeIf { it.isDirectory }?.let { empacotar(it) }
        val prefs = prefs()
        prefs.edit().remove(CHAVE_ANTES).remove(CHAVE_DESDE).remove(CHAVE_SESSAO).apply()
        if (!ativa(prefs)) {
            runCatching { TemasInstalados.apagar(TemaDoPainel.PASTA_DA_CALIBRACAO) }
                .onFailure { Log.w(TAG, "Não apagou a pasta da grade: ${it.message}") }
        }
        sessao = null
        _estado.value = Estado(
            ativa = false,
            pacote = pacote?.absolutePath,
            mensagem = "Calibração encerrada ($motivo).",
        )
        Log.i(TAG, "Calibração encerrada ($motivo); pacote=${pacote?.absolutePath}")
        return pacote
    }

    private fun empacotar(dir: File): File? = runCatching {
        val zip = File(dir.parentFile, "calibracao-${dir.name}.zip")
        ZipOutputStream(zip.outputStream().buffered()).use { saida ->
            dir.walkTopDown().filter { it.isFile }.forEach { arquivo ->
                saida.putNextEntry(ZipEntry("${dir.name}/${arquivo.relativeTo(dir).path}"))
                arquivo.inputStream().use { it.copyTo(saida) }
                saida.closeEntry()
            }
        }
        zip
    }.onFailure { Log.e(TAG, "Falha ao empacotar ${dir.absolutePath}", it) }.getOrNull()

    // ── Gravação ─────────────────────────────────────────────────────────────────────────────

    private fun chavesDaLinhaDoTempo(): Array<String> =
        (Sinais.TODOS.map { it.chave }.filter { RegrasDaCalibracao.relevante(it) } +
            Sinais.CAR_BASIC_VEHICLE_SPEED.chave).distinct().toTypedArray()

    private fun laco(dir: File, desdeMs: Long, retomada: Boolean) {
        val chaves = chavesDaLinhaDoTempo()
        val velocidadeKey = Sinais.CAR_BASIC_VEHICLE_SPEED.chave
        val inicio = System.currentTimeMillis()
        var anteriores: Map<String, String>? = null
        var automaticaFeita = false
        while (rodando) {
            val prefs = prefs()
            if (!ativa(prefs)) {
                // Alguém pôs outro tema no painel (Telas, instalador, perfil): a calibração acabou,
                // e o tema escolhido fica.
                rodando = false
                finalizar(OpenGw.contexto, "outro tema foi escolhido no painel")
                return
            }
            val leitura = runCatching { Veiculo.lerDoCarro(chaves) }
                .getOrDefault(emptyMap())
            val velocidade = leitura[velocidadeKey]?.takeIf { it.isNotBlank() }
                ?: runCatching { Veiculo.ler(velocidadeKey) }.getOrNull()
            val fim = RegrasDaCalibracao.fim(System.currentTimeMillis(), desdeMs, velocidade)
            if (fim != null) {
                rodando = false
                Log.w(TAG, "Calibração encerrada sozinha: ${fim.texto}")
                encerrar(OpenGw.contexto, fim.texto)
                return
            }
            if (leitura.isNotEmpty()) {
                val relevantes = leitura.filterKeys { RegrasDaCalibracao.relevante(it) }
                val antes = anteriores
                if (antes == null) {
                    registrarVarios(relevantes.map { (k, v) -> Linha("valor inicial", k, "", v, "") })
                } else {
                    val mudou = RegrasDaCalibracao.mudancas(antes, relevantes)
                    if (mudou.isNotEmpty()) registrarVarios(mudou.map { Linha("mudou", it.chave, it.antes ?: "", it.depois, "") })
                }
                anteriores = relevantes
            }
            if (!automaticaFeita && System.currentTimeMillis() - inicio >= ESPERA_DA_GRADE_MS) {
                automaticaFeita = true
                capturar(if (retomada) "subida" else "inicio")
            }
            try {
                Thread.sleep(RegrasDaCalibracao.INTERVALO_MS)
            } catch (_: InterruptedException) {
                // parar() interrompe o sono; o while confere `rodando`.
            }
        }
        Log.i(TAG, "Gravação parada (${dir.name})")
    }

    private data class Linha(val evento: String, val chave: String, val antes: String, val depois: String, val nota: String)

    private fun registrar(evento: String, chave: String, antes: String, nota: String) =
        registrarVarios(listOf(Linha(evento, chave, antes, "", nota)))

    private fun registrarVarios(linhas: List<Linha>) {
        val dir = sessao ?: return
        if (linhas.isEmpty()) return
        val agora = System.currentTimeMillis()
        val hora = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(agora))
        synchronized(travaDoArquivo) {
            runCatching {
                val arquivo = File(dir, "linha-do-tempo.tsv")
                val novo = !arquivo.exists()
                val texto = StringBuilder()
                if (novo) texto.append("hora\tms\tevento\tchave\tantes\tdepois\tnota\n")
                linhas.forEach { l ->
                    texto.append(hora).append('\t').append(agora).append('\t').append(l.evento).append('\t')
                        .append(l.chave).append('\t').append(limpo(l.antes)).append('\t').append(limpo(l.depois))
                        .append('\t').append(limpo(l.nota)).append('\n')
                }
                arquivo.appendText(texto.toString())
            }.onFailure { Log.w(TAG, "Linha do tempo: ${it.message}") }
        }
    }

    private fun limpo(s: String) = s.replace('\t', ' ').replace('\n', ' ')

    // ── Captura ──────────────────────────────────────────────────────────────────────────────

    /**
     * Guarda o estado de agora com o nome do passo. Bloqueia por alguns segundos (telas em PNG):
     * chamar fora da thread principal. Duas capturas ao mesmo tempo ficam em fila.
     */
    fun capturar(passoId: String): Captura? = synchronized(travaDaCaptura) {
        val dir = sessao ?: return null
        val numero = (capturasNaPasta(dir).maxOfOrNull { it.numero } ?: -1) + 1
        val pasta = File(dir, RegrasDaCalibracao.nomeDaCaptura(numero, passoId)).also { it.mkdirs() }
        val hora = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        registrar("captura", "", "", "${pasta.name} (${rotuloDe(passoId)})")
        try {
            progresso("Lendo as chaves do carro…")
            val todas = runCatching { Veiculo.lerTudoDoCarro() }.getOrDefault(emptyMap())
            File(pasta, "chaves.tsv").writeText(
                todas.toSortedMap().entries.joinToString("") { "${it.key}\t${limpo(it.value)}\n" }
            )
            val anteriores = ultimaLeituraCompleta
            val mudancas = if (anteriores.isEmpty()) emptyList() else RegrasDaCalibracao.mudancas(anteriores, todas)
            File(pasta, "mudancas.tsv").writeText(
                "chave\tantes\tdepois\n" + mudancas.joinToString("") { "${it.chave}\t${limpo(it.antes ?: "")}\t${limpo(it.depois)}\n" }
            )
            if (todas.isNotEmpty()) ultimaLeituraCompleta = todas

            progresso("Lendo os sinais das lâmpadas…")
            val vhal = CatalogoVhal.todos().filter { it.legivel && RegrasDaCalibracao.sinalDeLampada(it.nome) }
            File(pasta, "vhal.tsv").writeText(
                vhal.joinToString("") { "${it.nome}\t${limpo(Vhal.ler(it.id) ?: "")}\n" }
            )

            val telas = mutableListOf<String>()
            RegrasDaCalibracao.TELAS.forEach { id ->
                progresso("Capturando a tela $id…")
                telas += capturarTela(id, pasta)
            }
            progresso("Lendo as camadas das telas…")
            val camadas = RemoteControlOps.execCaptureBytes(arrayOf("dumpsys", "SurfaceFlinger", "--list"))
            File(pasta, "camadas.txt").writeBytes(camadas ?: "sem resposta (Shizuku indisponível?)\n".toByteArray())

            File(pasta, "captura.txt").writeText(
                buildString {
                    appendLine("Passo: ${rotuloDe(passoId)} ($passoId)")
                    appendLine("Hora da central: $hora")
                    appendLine("Chaves lidas: ${todas.size}")
                    appendLine("Telas (screencap -d N):")
                    telas.forEach { appendLine("  $it") }
                    appendLine("Mudanças desde a captura anterior: ${mudancas.size} (mudancas.tsv)")
                }
            )
            val captura = Captura(
                numero = numero,
                passo = passoId,
                hora = hora.substring(0, 8),
                telas = telas,
                chaves = todas.size,
                mudancas = mudancas.filter { RegrasDaCalibracao.relevante(it.chave) },
            )
            _estado.update { e ->
                e.copy(capturas = (e.capturas.filter { it.numero != numero } + captura).sortedBy { it.numero })
            }
            captura
        } catch (t: Throwable) {
            Log.e(TAG, "Captura ${pasta.name} falhou", t)
            registrar("erro", "", "", "captura ${pasta.name}: ${t.message}")
            null
        } finally {
            progresso(null)
        }
    }

    private fun progresso(texto: String?) = _estado.update { it.copy(capturando = texto) }

    /** Uma tela pelo formato cru do `screencap` (o `-p` já saiu truncado no painel desta central). */
    private fun capturarTela(id: Int, pasta: File): String {
        val bytes = RemoteControlOps.execCaptureBytes(arrayOf("screencap", "-d", id.toString()))
            ?: return "$id: sem resposta (Shizuku indisponível?)"
        val raw = RegrasDaCalibracao.lerRaw(bytes) ?: return "$id: não é imagem (${bytes.size} bytes)"
        val argb = RegrasDaCalibracao.paraArgb(bytes, raw)
        RegrasDaCalibracao.corUnica(argb)?.let { cor ->
            return "$id: ${raw.largura}×${raw.altura}, toda #%06x".format(cor and 0xffffff)
        }
        val bitmap = Bitmap.createBitmap(argb, raw.largura, raw.altura, Bitmap.Config.ARGB_8888)
        return try {
            File(pasta, "tela-$id.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            "$id: ${raw.largura}×${raw.altura}, salva (tela-$id.png)"
        } finally {
            bitmap.recycle()
        }
    }

    private fun capturasNaPasta(dir: File): List<Captura> =
        dir.listFiles().orEmpty()
            .filter { it.isDirectory }
            .mapNotNull { pasta ->
                val m = Regex("^(\\d{2,})-(.+)$").find(pasta.name) ?: return@mapNotNull null
                Captura(numero = m.groupValues[1].toInt(), passo = m.groupValues[2], hora = "")
            }
            .sortedBy { it.numero }

    // ── Arquivos da sessão ───────────────────────────────────────────────────────────────────

    private fun escreverSessao(context: Context, dir: File, agora: Long, prefs: SharedPreferences) {
        runCatching {
            val telas = runCatching { RemoteControlOps.listDisplays() }.getOrDefault(emptyList())
            File(dir, "sessao.txt").writeText(
                buildString {
                    appendLine("OPEN GW ${BuildConfig.VERSION_NAME} (versionCode ${BuildConfig.VERSION_CODE})")
                    appendLine("Início: ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS", Locale.US).format(Date(agora))} ($agora ms)")
                    appendLine("Central: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.DISPLAY}")
                    appendLine("Tema de antes: ${lerAntes(prefs)}")
                    appendLine("Modo do painel: ${ModoDoPainel.ler(prefs)}")
                    appendLine("Telas do DisplayManager (id do Android, não o do screencap):")
                    telas.forEach { appendLine("  ${it.id}: ${it.name} ${it.width}×${it.height}") }
                    appendLine("Chaves na linha do tempo: ${chavesDaLinhaDoTempo().size}")
                }
            )
        }.onFailure { Log.w(TAG, "sessao.txt: ${it.message}") }
        // Os dumps demoram (SurfaceFlinger inteiro) e dependem do Shizuku: em segundo plano.
        Thread({
            listOf(
                "surfaceflinger.txt" to arrayOf("dumpsys", "SurfaceFlinger"),
                "display.txt" to arrayOf("dumpsys", "display"),
                "janelas.txt" to arrayOf("dumpsys", "window", "displays"),
            ).forEach { (nome, comando) ->
                val saida = runCatching { RemoteControlOps.execCaptureBytes(comando) }.getOrNull()
                runCatching { File(dir, nome).writeBytes(saida ?: "sem resposta (Shizuku indisponível?)\n".toByteArray()) }
            }
        }, "calibracao-dumps").start()
    }

    private fun escreverLeiaMe(dir: File) {
        runCatching {
            File(dir, "LEIA-ME.txt").writeText(
                """
                Calibração do painel — OPEN GW ${BuildConfig.VERSION_NAME}

                linha-do-tempo.tsv  Luzes e avisos do carro lidos a cada meio segundo, com a hora da
                                    central (a mesma do relógio da grade no painel). "valor inicial" é a
                                    primeira leitura; depois, só o que mudou.
                NN-passo/           Uma pasta por captura:
                  chaves.tsv        Todas as chaves do carro naquele instante.
                  mudancas.tsv      O que mudou desde a captura anterior.
                  vhal.tsv          Sinais crus das lâmpadas (VHAL).
                  tela-N.png        screencap -d N (ids da SurfaceFlinger; 4 foi o painel nesta central).
                  camadas.txt       dumpsys SurfaceFlinger --list.
                  captura.txt       Resumo.
                sessao.txt          Versão, central e telas.
                surfaceflinger.txt, display.txt, janelas.txt  Estado das telas no início.

                A grade tem linha a cada 20 px e número a cada 100 px, em coordenadas do tema (1920×720).
                """.trimIndent() + "\n"
            )
        }
    }
}
