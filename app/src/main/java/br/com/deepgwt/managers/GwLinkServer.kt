// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.managers

import br.com.deepgwt.nucleo.DeepGwt
import br.com.deepgwt.veiculo.Veiculo
import android.os.SystemClock
import android.util.Log
import br.com.deepgwt.app.BootDiagnostics
import br.com.deepgwt.painel.Diario
import br.com.deepgwt.painel.TemasInstalados
import br.com.deepgwt.ajustes.AjustesRemotos
import br.com.deepgwt.licenca.LicencaRemota
import br.com.deepgwt.licenca.Licencas
import br.com.deepgwt.licenca.Portao
import br.com.deepgwt.licenca.Recurso
import br.com.deepgwt.licenca.RecursoDaRota
import br.com.deepgwt.managers.asd.AsdManager
import br.com.deepgwt.nucleo.Preferencias
import br.com.deepgwt.seguranca.OrigemDoPedido
import br.com.deepgwt.veiculo.TravaDeMovimento
import org.json.JSONArray
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/**
 * Servidor HTTP mínimo (JSON, sem framework) embarcado no DeepGWT para a função "GWLink" do
 * instalador: expõe TODAS as [br.com.deepgwt.veiculo.Sinais] que o
 * DeepGWT já sabe ler, pra quem só quer ver telemetria em tempo real sem passar pelo fluxo de
 * "Instalar Apps" de novo. Roda na mesma rede do telnetd (porta fixa, sem autenticação — mesma
 * postura de segurança do resto do projeto: quem está no Wi-Fi do carro já tem acesso root via
 * telnet de qualquer forma).
 *
 * Além da telemetria original, também serve as rotas do menu escondido "Opções de Desenvolvedor"
 * do instalador: listagem de displays, mirror de tela, injeção de toque, arquivos e backup — ver
 * [RemoteControlOps]. Essas rotas nunca são expostas na UI do próprio app no carro (só existem
 * no instalador do laptop), mas o servidor em si não distingue "quem" está perguntando — a
 * discrição é só de interface, não de rede.
 */
object GwLinkServer {
    private const val TAG = "GwLinkServer"
    const val PORT = 8766

    @Volatile private var started = false

    @Synchronized
    fun start() {
        if (started) return
        started = true
        // O contador de revisão dos ajustes precisa estar ouvindo desde já: uma mudança feita na
        // tela do carro antes de o instalador abrir também tem de acordar quem já está esperando.
        AjustesRemotos.iniciar()
        // Alpha 8.0: o nível pode ter mudado com o app fechado — os serviços se ajustam uma vez.
        runCatching { Portao.conferirNaPartida() }.onFailure { Log.w(TAG, "Portão na partida: ${it.message}") }
        // Alpha 8.1: as horas da licença contam daqui em diante.
        runCatching { Licencas.iniciar() }.onFailure { Log.w(TAG, "Licenças: ${it.message}") }
        // Alpha 10.1: o aviso de pagamento do teste além do limite, em todas as telas.
        runCatching { br.com.deepgwt.licenca.AvisoDoTeste.iniciar() }.onFailure { Log.w(TAG, "Aviso do teste: ${it.message}") }
        // Alpha 8.5 (auditoria B2): até a 8.4 um erro em `accept()` saía do laço e a thread morria;
        // uma porta presa num reinício rápido do app só ia para o log. Nos dois casos o instalador,
        // a página do celular e o launcher 3D perdiam o carro até o app reiniciar. Agora o servidor
        // fecha, espera (1 s, dobrando até 30 s) e abre de novo, para sempre.
        Thread({
            var esperaMs = ESPERA_INICIAL_MS
            while (true) {
                try {
                    ServerSocket().use { server ->
                        server.reuseAddress = true
                        server.bind(InetSocketAddress(PORT))
                        Log.i(TAG, "GWLink server ouvindo na porta $PORT")
                        esperaMs = ESPERA_INICIAL_MS
                        while (true) {
                            val socket = server.accept()
                            if (!br.com.deepgwt.seguranca.ClienteDaRede.permitido(socket.inetAddress)) {
                                Log.w(TAG, "GWLink: cliente fora do hotspot do carro recusado (${socket.inetAddress?.hostAddress})")
                                runCatching { socket.close() }
                                continue
                            }
                            try {
                                Thread({ handleClient(socket) }, "gwlink-client").start()
                            } catch (t: Throwable) {
                                // Sem memória ou sem thread para mais um cliente: fecha só este.
                                Log.e(TAG, "GWLink sem recurso para atender: ${t.message}")
                                runCatching { socket.close() }
                            }
                        }
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "GWLink parou (${t.javaClass.simpleName}: ${t.message}); de novo em ${esperaMs / 1000} s")
                }
                try { Thread.sleep(esperaMs) } catch (_: InterruptedException) {}
                esperaMs = (esperaMs * 2).coerceAtMost(ESPERA_MAXIMA_MS)
            }
        }, "gwlink-server").start()
    }

    private const val ESPERA_INICIAL_MS = 1_000L
    private const val ESPERA_MAXIMA_MS = 30_000L

    /**
     * Teto do corpo lido para a memória (Alpha 8.5, auditoria B1). O maior corpo legítimo é o
     * rascunho de um tema do criador, com imagens embutidas — alguns MB. Acima disto: 413.
     * `/files/put` não passa por aqui: o arquivo vai do socket direto para o disco.
     */
    const val MAX_CORPO_EM_MEMORIA = 16L * 1024 * 1024

    private class Request(
        val method: String,
        val path: String,
        val query: Map<String, String>,
        val headers: Map<String, String>,
        val contentLength: Long,
        val input: InputStream
    ) {
        /** Preenchido por [lerCorpo] depois das travas; `/files/put` nunca preenche. */
        var body: ByteArray? = null
    }

    /**
     * Lê uma linha byte a byte (sem `BufferedReader`) porque a requisição pode ter um corpo
     * binário (upload de arquivo) logo em seguida dos headers — um `BufferedReader` bufferiza
     * bytes além do fim da linha, e esses bytes (já parte do corpo) seriam perdidos ao trocar
     * pra leitura crua depois. Lento (byte a byte), mas os headers são pequenos e isso só roda
     * numa rede local de baixíssimo volume.
     */
    private fun readLine(input: InputStream): String? {
        val acumulado = ByteArrayOutputStream()
        val primeiro = input.read()
        if (primeiro == -1) return null
        var atual = primeiro
        while (atual != -1 && atual != '\n'.code) {
            // Uma linha sem fim não pode encher a memória: acima do teto, a requisição é abandonada.
            if (acumulado.size() >= MAX_LINHA_DE_CABECALHO) return null
            acumulado.write(atual)
            atual = input.read()
        }
        // Fim de linha CRLF: o CR que sobrou antes do LF não faz parte do texto.
        val cru = acumulado.toByteArray()
        val tamanho = if (cru.isNotEmpty() && cru[cru.size - 1] == '\r'.code.toByte()) cru.size - 1 else cru.size
        return String(cru, 0, tamanho, Charsets.UTF_8)
    }

    private const val MAX_LINHA_DE_CABECALHO = 16 * 1024
    private const val MAX_CABECALHOS = 100

    private fun parseQuery(raw: String?): Map<String, String> {
        if (raw.isNullOrEmpty()) return emptyMap()
        return raw.split("&").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx == -1) return@mapNotNull null
            val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
            val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
            key to value
        }.toMap()
    }

    private fun readRequest(input: InputStream): Request? {
        val requestLine = readLine(input) ?: return null
        val requestParts = requestLine.split(" ")
        if (requestParts.size < 2) return null
        val method = requestParts[0]
        val fullPath = requestParts[1]
        val path = fullPath.substringBefore('?')
        val query = parseQuery(fullPath.substringAfter('?', ""))

        val headers = mutableMapOf<String, String>()
        var contados = 0
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            if (++contados > MAX_CABECALHOS) return null
            val idx = line.indexOf(':')
            if (idx == -1) continue
            headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
        }

        // Sem cabeçalho = sem corpo; ilegível ou negativo vira -1 e é recusado com 400.
        val bruto = headers["content-length"]
        val contentLength = if (bruto == null) 0L else bruto.toLongOrNull()?.takeIf { it >= 0 } ?: -1L

        return Request(method, path, query, headers, contentLength, input)
    }

    /** Lê o corpo para a memória. Só depois de [MAX_CORPO_EM_MEMORIA] ter sido conferido. */
    private fun lerCorpo(request: Request) {
        val tamanho = request.contentLength.toInt()
        if (tamanho <= 0) return
        val buffer = ByteArray(tamanho)
        var read = 0
        while (read < tamanho) {
            val n = request.input.read(buffer, read, tamanho - read)
            if (n == -1) break
            read += n
        }
        request.body = buffer
    }

    private fun writeStatusHeaders(out: OutputStream, status: String, contentType: String, extra: String = "") {
        val header = "HTTP/1.1 $status\r\n" +
            "Content-Type: $contentType\r\n" +
            "Connection: close\r\n" +
            corsHeaders() +
            extra +
            "\r\n"
        out.write(header.toByteArray(Charsets.UTF_8))
    }

    /**
     * O launcher 3D roda dentro de um WebView cuja página vem de
     * `https://appassets.androidplatform.net`. Uma chamada dali para `http://127.0.0.1:8766` é,
     * para o navegador, uma origem pedindo dado de outra — sem estes cabeçalhos a resposta chega
     * e é descartada antes do JavaScript vê-la.
     *
     * Até a 8.4 era `*` para todo mundo. Desde a 8.5 (auditoria B4) o cabeçalho só devolve a
     * origem que [OrigemDoPedido] aceitou, e uma origem recusada nem chega à rota. Cada cliente é
     * atendido na própria thread, por isso a origem aceita mora num [ThreadLocal].
     */
    private val origemCors = ThreadLocal<String?>()

    private fun corsHeaders(): String {
        val origem = origemCors.get() ?: return ""
        return "Access-Control-Allow-Origin: $origem\r\n" +
            "Vary: Origin\r\n" +
            "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
            "Access-Control-Allow-Headers: Content-Type\r\n" +
            "Access-Control-Allow-Private-Network: true\r\n"
    }

    private fun writeJson(out: OutputStream, json: Any, status: String = "200 OK") {
        val body = json.toString().toByteArray(Charsets.UTF_8)
        writeStatusHeaders(out, status, "application/json; charset=utf-8", "Content-Length: ${body.size}\r\n")
        out.write(body)
        out.flush()
    }

    private fun handleClient(socket: Socket) {
        socket.use {
            var saida: OutputStream? = null
            try {
                socket.soTimeout = 30000
                val input = socket.getInputStream()
                val output = socket.getOutputStream()
                saida = output
                origemCors.set(null)
                val request = readRequest(input) ?: return

                // Alpha 8.5 (auditoria B4): origem de navegador que não é do carro nem do app para
                // aqui, antes de qualquer rota — inclusive do pré-voo, que fica sem CORS.
                when (val decisao = OrigemDoPedido.decidir(
                    request.method, request.path, request.headers["origin"], request.headers["host"],
                    request.headers["sec-fetch-site"]
                )) {
                    OrigemDoPedido.Decisao.Recusar -> {
                        Log.w(TAG, "Pedido recusado: ${request.method} ${request.path} de origem ${request.headers["origin"] ?: request.headers["sec-fetch-site"]}")
                        writeJson(
                            output,
                            JSONObject().put("ok", false).put("motivo", "origem")
                                .put("error", "Esta página não pode comandar o carro."),
                            "403 Forbidden"
                        )
                        return
                    }
                    is OrigemDoPedido.Decisao.Aceitar -> origemCors.set(decisao.origemCors)
                }

                // Alpha 8.0: a rota de um recurso que o nível não libera responde 403 com o motivo.
                // O instalador já mostra o cadeado antes; isto é a trava para qualquer outro cliente.
                if (request.method != "OPTIONS") {
                    RecursoDaRota.de(request.method, request.path, request.query)
                        ?.takeUnless { Portao.permite(it) }
                        ?.let { recurso ->
                            writeJson(output, LicencaRemota.recusa(recurso), "403 Forbidden")
                            return
                        }
                }

                // Alpha 8.5 (auditoria B1): o corpo não é mais lido inteiro às cegas. Um arquivo
                // grande pelo Explorador (ou um `Content-Length` de 2 GB de qualquer um na rede)
                // virava OutOfMemoryError, que não é Exception: escapava do catch e derrubava o
                // app inteiro — barra, painel, HUD e ASD juntos.
                if (request.contentLength < 0) {
                    writeJson(output, JSONObject().put("ok", false).put("error", "Content-Length inválido"), "400 Bad Request")
                    return
                }
                val corpoEmFluxo = request.method == "POST" && request.path == "/files/put"
                if (!corpoEmFluxo) {
                    if (request.contentLength > MAX_CORPO_EM_MEMORIA) {
                        writeJson(
                            output,
                            JSONObject().put("ok", false).put("error", "Corpo grande demais (${request.contentLength} bytes)"),
                            "413 Payload Too Large"
                        )
                        return
                    }
                    lerCorpo(request)
                }

                when {
                    // Pré-voo do navegador. O launcher 3D usa POST com corpo JSON, e o WebView
                    // manda um OPTIONS antes; sem esta linha ele nunca chega ao POST de verdade.
                    request.method == "OPTIONS" -> writeJson(output, JSONObject().put("ok", true))
                    request.path == "/diag" -> writeJson(output, buildDiagnosticsJson())
                    // Nível e licença (Alpha 8.0). Carro mais antigo cai no `else`: telemetria sem
                    // `ok` — o instalador entende como "sem níveis" e não tranca nada.
                    request.method == "POST" && request.path == "/licenca/nivel-de-teste" -> handleNivelDeTeste(request, output)
                    // Licença (Alpha 8.1): ativar um código (instalador e página do celular) e os
                    // ganchos do build de teste. A rede é aberta, mas só entra código assinado.
                    request.method == "POST" && request.path == "/licenca/ativar" -> handleLicencaAtivar(request, output)
                    request.method == "POST" && request.path == "/licenca/teste" -> handleLicencaTeste(request, output)
                    request.method == "POST" && request.path == "/licenca/avaliacao" -> handleLicencaAvaliacao(output)
                    request.method == "POST" && request.path == "/licenca/avaliacao/encerrar" -> handleLicencaAvaliacaoEncerrar(output)
                    request.path == "/licenca" -> writeJson(output, LicencaRemota.json())
                    // A página que o celular abre pelo QR de Informações → Licença.
                    request.path == "/ativar" -> handlePaginaDeAtivar(output)
                    request.path == "/displays" -> handleDisplays(output)
                    request.path == "/mirror" -> handleMirror(request, output)
                    request.path == "/screenshot" -> handleScreenshot(request, output)
                    request.method == "POST" && request.path == "/input/tap" -> handleInputTap(request, output)
                    request.method == "POST" && request.path == "/input/swipe" -> handleInputSwipe(request, output)
                    request.method == "POST" && request.path == "/input/key" -> handleInputKey(request, output)
                    request.path == "/files/list" -> handleFilesList(request, output)
                    request.path == "/files/get" -> handleFilesGet(request, output)
                    request.method == "POST" && request.path == "/files/put" -> handleFilesPut(request, output)
                    request.path == "/files/delete" -> handleThemeDelete(request, output)
                    request.path == "/backup/apps" -> handleBackup(output, apps = true)
                    request.path == "/backup/full" -> handleBackup(output, apps = false)
                    request.path == "/apps/list" -> handleAppsList(output)
                    request.path == "/backup/size" -> handleBackupSize(request, output)
                    request.path == "/dvr/find" -> handleDvrFind(output)
                    request.path == "/storage" -> handleStorage(output)
                    // Rotas do launcher 3D (br.com.deepgwt.tela3d). Ver ViewerBridgeReceiver.
                    request.path == "/identity" -> handleIdentity(output)
                    request.method == "POST" && request.path == "/identity/select" -> handleIdentitySelect(request, output)
                    request.path == "/launcher/state" -> writeJson(output, HomeLauncherController.toJson())
                    request.method == "POST" && request.path == "/launcher/set" -> handleLauncherSet(request, output)
                    request.path == "/profiles" -> handleProfiles(output)
                    request.method == "POST" && request.path == "/profiles/select" -> handleProfileSelect(request, output)
                    request.method == "POST" && request.path == "/profiles/save" -> handleProfileSave(request, output)
                    request.method == "POST" && request.path == "/profiles/delete" -> handleProfileDelete(request, output)
                    // Histórico de serviço (revisões e manutenção) registrado pelo instalador. Carro
                    // mais antigo cai no `else` e devolve telemetria sem `ok`.
                    request.method == "POST" && request.path == "/servico/adicionar" -> handleServicoAdicionar(request, output)
                    request.method == "POST" && request.path == "/servico/apagar" -> handleServicoApagar(request, output)
                    request.path == "/servico" -> handleServico(output)
                    // Tema do HUD feito no instalador (Alpha 5.9). Um carro mais antigo cai no
                    // `else` e devolve telemetria sem `ok`: é assim que o instalador sabe que
                    // precisa atualizar o app antes de aplicar.
                    request.method == "POST" && request.path == "/hud/tema" -> handleHudTemaAplicar(request, output)
                    request.path == "/hud/tema" -> handleHudTemaLer(output)
                    // Ajustes do DeepGWT pelo instalador (Alpha 6.4): catálogo, valores, mudança em
                    // lote e espera por mudança feita no carro. Mesmo aviso de versão do HUD: um
                    // carro mais antigo cai no `else` e devolve telemetria sem `ok`.
                    request.path == "/ajustes/espera" -> handleAjustesEspera(request, output)
                    request.method == "POST" && request.path == "/ajustes" -> handleAjustesAplicar(request, output)
                    request.path == "/ajustes" ->
                            writeJson(output, AjustesRemotos.lerJson(comCatalogo = request.query["catalogo"] != "0"))
                    // Tema do painel pelo instalador (Alpha 6.5): listar, ativar e o rascunho do
                    // "Ver no carro" do criador de temas. Mesmo aviso de versão: carro mais antigo
                    // cai no `else` e devolve telemetria sem `ok`.
                    request.method == "POST" && request.path == "/temas/ativar" -> handleTemasAtivar(request, output)
                    request.method == "POST" && request.path == "/temas/rascunho" -> handleTemasRascunho(request, output)
                    request.path == "/temas" -> handleTemasListar(output)
                    request.path == "/quick/state" -> handleQuickState(output)
                    request.method == "POST" && request.path == "/quick/asd" -> handleQuickAsd(request, output)
                    request.method == "POST" && request.path == "/quick/stealth" -> handleQuickStealth(output)
                    request.method == "POST" && request.path == "/quick/sportsdisplay" ->
                            handleQuickSportsDisplay(output)
                    request.path == "/backup/app" -> handleBackupApp(request, output)
                    request.method == "POST" && request.path == "/set" -> handleSetValue(request, output)
                    request.method == "POST" && request.path == "/system/restart" -> handleSystemRestart(output)
                    else -> writeJson(output, buildTelemetryJson())
                }
            } catch (e: Exception) {
                Log.w(TAG, "Erro atendendo cliente GWLink: ${e.message}")
                // Corpo que não é JSON, campo obrigatório ausente etc.: o cliente recebe o motivo
                // em vez de uma conexão que fecha vazia.
                val status = if (e is org.json.JSONException) "400 Bad Request" else "500 Internal Server Error"
                runCatching {
                    saida?.let { writeJson(it, JSONObject().put("ok", false).put("error", e.message ?: e.javaClass.simpleName), status) }
                }
            } catch (e: OutOfMemoryError) {
                // Um pedido não pode derrubar o app inteiro. Este cliente fica sem resposta.
                Log.e(TAG, "Sem memória atendendo cliente GWLink: ${e.message}")
            } finally {
                origemCors.remove()
            }
        }
    }

    private fun handleDisplays(output: OutputStream) {
        val array = JSONArray()
        for (display in RemoteControlOps.listDisplays()) {
            array.put(
                JSONObject()
                    .put("id", display.id)
                    .put("name", display.name)
                    .put("width", display.width)
                    .put("height", display.height)
            )
        }
        writeJson(output, array)
    }

    /**
     * Protocolo propositalmente simples (não é MJPEG/multipart de verdade) já que o único
     * cliente é o nosso próprio código Rust no instalador: depois dos headers, cada frame é um
     * inteiro big-endian de 4 bytes com o tamanho, seguido dos bytes JPEG. Repete até o cliente
     * desconectar (erro de escrita no socket) ou o display parar de responder.
     */
    private fun handleMirror(request: Request, output: OutputStream) {
        val displayId = request.query["display"]?.toIntOrNull() ?: 0
        val fps = (request.query["fps"]?.toIntOrNull() ?: 8).coerceIn(1, 20)
        val quality = (request.query["quality"]?.toIntOrNull() ?: 40).coerceIn(10, 90)
        val maxWidth = (request.query["maxWidth"]?.toIntOrNull() ?: 720).coerceIn(240, 1920)
        val frameIntervalMs = (1000L / fps)

        writeStatusHeaders(output, "200 OK", "application/octet-stream")

        var consecutiveFailures = 0
        while (consecutiveFailures < 10) {
            val start = SystemClock.elapsedRealtime()
            val jpeg = RemoteControlOps.captureDisplayJpeg(displayId, quality, maxWidth)
            if (jpeg == null) {
                consecutiveFailures++
                Thread.sleep(200)
                continue
            }
            consecutiveFailures = 0
            val lengthPrefix = byteArrayOf(
                (jpeg.size ushr 24).toByte(),
                (jpeg.size ushr 16).toByte(),
                (jpeg.size ushr 8).toByte(),
                jpeg.size.toByte()
            )
            output.write(lengthPrefix)
            output.write(jpeg)
            output.flush()

            val elapsed = SystemClock.elapsedRealtime() - start
            val sleepMs = frameIntervalMs - elapsed
            if (sleepMs > 0) Thread.sleep(sleepMs)
        }
    }

    /**
     * A print da tela em resolução cheia, PNG cru do `screencap` (Alpha 7.6: "salvar uma print"
     * no espelhamento do instalador). O `/mirror` manda JPEG reduzido para caber no Wi-Fi a vários
     * quadros por segundo; a print é um quadro só, então vai sem perda e sem reduzir.
     */
    private fun handleScreenshot(request: Request, output: OutputStream) {
        val displayId = request.query["display"]?.toIntOrNull() ?: 0
        val png = RemoteControlOps.captureDisplayPng(displayId)
        if (png == null) {
            writeJson(
                output,
                JSONObject().put("ok", false).put("error", "screencap não devolveu imagem da tela $displayId"),
                "500 Internal Server Error"
            )
            return
        }
        writeStatusHeaders(output, "200 OK", "image/png", "Content-Length: ${png.size}\r\n")
        output.write(png)
        output.flush()
    }

    private fun handleInputTap(request: Request, output: OutputStream) {
        val json = request.body?.let { JSONObject(String(it, Charsets.UTF_8)) }
        if (json == null) {
            writeJson(output, JSONObject().put("ok", false).put("error", "corpo ausente"), "400 Bad Request")
            return
        }
        val result = RemoteControlOps.injectTap(json.optInt("display", 0), json.getInt("x"), json.getInt("y"))
        writeJson(output, JSONObject().put("ok", result.exitCode == 0).put("exitCode", result.exitCode).put("output", result.output))
    }

    private fun handleInputSwipe(request: Request, output: OutputStream) {
        val json = request.body?.let { JSONObject(String(it, Charsets.UTF_8)) }
        if (json == null) {
            writeJson(output, JSONObject().put("ok", false).put("error", "corpo ausente"), "400 Bad Request")
            return
        }
        val result = RemoteControlOps.injectSwipe(
            json.optInt("display", 0),
            json.getInt("x1"), json.getInt("y1"),
            json.getInt("x2"), json.getInt("y2"),
            json.optInt("durationMs", 300)
        )
        writeJson(output, JSONObject().put("ok", result.exitCode == 0).put("exitCode", result.exitCode).put("output", result.output))
    }

    private fun handleInputKey(request: Request, output: OutputStream) {
        val json = request.body?.let { JSONObject(String(it, Charsets.UTF_8)) }
        if (json == null) {
            writeJson(output, JSONObject().put("ok", false).put("error", "corpo ausente"), "400 Bad Request")
            return
        }
        val result = RemoteControlOps.injectKey(json.optInt("display", 0), json.getInt("keycode"))
        writeJson(output, JSONObject().put("ok", result.exitCode == 0).put("exitCode", result.exitCode).put("output", result.output))
    }

    /**
     * Reinicia a central a pedido do PC (botão "Restart" do instalador). Responde ANTES de a
     * central cair: [RemoteControlOps.rebootHeadUnit] agenda o `reboot` com atraso, então o
     * `writeJson`/flush abaixo chega ao PC e só depois o sistema reinicia. Sem corpo de request —
     * é uma ação sem parâmetros.
     */
    private fun handleSystemRestart(output: OutputStream) {
        // Alpha 8.5 (auditoria B5): pela REDE, só parado. A central reiniciando leva junto o
        // painel projetado, o CarPlay e as câmeras. O botão "Reiniciar central" da própria tela do
        // carro continua valendo em movimento (decisão de produto, com confirmação): lá quem pede
        // está dentro do carro. Sem leitura de velocidade (ControlService caído — justamente
        // quando reiniciar é o remédio) não recusa.
        TravaDeMovimento.agora(exigirP = false, exigirLeitura = false)?.let { motivo ->
            writeJson(
                output,
                JSONObject().put("ok", false).put("scheduled", false).put("motivo", "andando")
                    .put("error", "Não reiniciado: ${motivo.texto}. Pare o carro e tente de novo."),
                "409 Conflict"
            )
            return
        }
        val scheduled = RemoteControlOps.rebootHeadUnit(delayMs = 800)
        writeJson(
            output,
            JSONObject()
                .put("ok", scheduled)
                .put("scheduled", scheduled)
                .put("error", if (scheduled) JSONObject.NULL else "Shizuku indisponível no carro")
        )
    }

    private fun handleFilesList(request: Request, output: OutputStream) {
        val path = request.query["path"] ?: "/sdcard"
        val entries = RemoteControlOps.listDir(path)
        if (entries == null) {
            writeJson(output, JSONObject().put("ok", false).put("error", "falha ao listar $path"), "500 Internal Server Error")
            return
        }
        val array = JSONArray()
        for (entry in entries) {
            array.put(JSONObject().put("name", entry.name).put("isDir", entry.isDir).put("size", entry.size))
        }
        writeJson(output, array)
    }

    private fun handleFilesGet(request: Request, output: OutputStream) {
        val path = request.query["path"]
        if (path.isNullOrBlank()) {
            writeJson(output, JSONObject().put("ok", false).put("error", "path ausente"), "400 Bad Request")
            return
        }
        writeStatusHeaders(output, "200 OK", "application/octet-stream")
        RemoteControlOps.readFileToOutput(path, output)
        output.flush()
    }

    private fun handleFilesPut(request: Request, output: OutputStream) {
        val path = request.query["path"]
        if (path.isNullOrBlank() || request.contentLength <= 0) {
            writeJson(output, JSONObject().put("ok", false).put("error", "path ou corpo ausente"), "400 Bad Request")
            return
        }
        // Do socket direto para o disco, em blocos de 64 KB (Alpha 8.5, auditoria B1): o tamanho
        // do arquivo não pesa mais na memória do app.
        val corpo = FluxoLimitado(request.input, request.contentLength)
        val gravou = RemoteControlOps.writeFileFromInput(path, corpo)
        if (gravou && corpo.lidos < request.contentLength) {
            writeJson(
                output,
                JSONObject().put("ok", false)
                    .put("error", "Envio incompleto (${corpo.lidos} de ${request.contentLength} bytes); o arquivo no carro ficou cortado"),
                "500 Internal Server Error"
            )
            return
        }
        val ok = gravou
        if (ok) {
            writeJson(output, JSONObject().put("ok", true))
        } else {
            writeJson(output, JSONObject().put("ok", false).put("error", "Falha ao gravar arquivo (Shizuku indisponível?)"), "500 Internal Server Error")
        }
    }

    /**
     * Apaga UMA pasta de tema em files/themes. Deliberadamente não é um "apague este caminho".
     *
     * As outras rotas de arquivo recebem caminho livre porque servem ao explorador do menu de
     * desenvolvedor, onde o operador escolhe o que abrir. Um apagar-por-caminho na mesma rede sem
     * autenticação seria outra coisa: uma chamada com "/data/data/..." derrubaria o app inteiro, e
     * o servidor não tem como distinguir quem perguntou. Então esta rota aceita só um NOME de
     * pasta, e quem valida é [TemasInstalados.apagar] — que resolve dentro da raiz de temas e
     * recusa qualquer nome que escape dela.
     *
     * Aceita GET e POST: o cliente do instalador manda POST, mas um GET equivalente evita um
     * "405" confuso para quem estiver testando a rota na mão.
     */
    private fun handleThemeDelete(request: Request, output: OutputStream) {
        val folder = request.query["theme"]
        if (folder.isNullOrBlank()) {
            writeJson(
                output,
                JSONObject().put("ok", false).put("error", "parâmetro theme ausente"),
                "400 Bad Request"
            )
            return
        }
        val removed = try {
            TemasInstalados.apagar(folder)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao apagar tema $folder: ${e.message}")
            false
        }
        if (removed) {
            writeJson(output, JSONObject().put("ok", true).put("theme", folder))
        } else {
            writeJson(
                output,
                JSONObject().put("ok", false).put("error", "tema não encontrado ou nome inválido: $folder"),
                "404 Not Found"
            )
        }
    }

    private fun handleBackup(output: OutputStream, apps: Boolean) {
        val fileName = if (apps) "openhw_apps_backup.tar" else "openhw_full_backup.tar"
        writeStatusHeaders(
            output, "200 OK", "application/x-tar",
            "Content-Disposition: attachment; filename=\"$fileName\"\r\n"
        )
        if (apps) {
            RemoteControlOps.streamAppsBackup(output)
        } else {
            RemoteControlOps.streamFullBackup(output)
        }
        output.flush()
    }

    private fun handleAppsList(output: OutputStream) {
        val apps = RemoteControlOps.listInstalledApps()
        if (apps == null) {
            writeJson(output, JSONObject().put("ok", false).put("error", "falha ao listar apps"), "500 Internal Server Error")
            return
        }
        val array = JSONArray()
        for (app in apps) array.put(JSONObject().put("package", app.packageName).put("isSystem", app.isSystem))
        writeJson(output, array)
    }

    /**
     * Estimativa de tamanho (bytes) pra alimentar a barra de progresso do Mac antes de iniciar
     * qualquer um dos três backups — sem isso não dá pra mostrar porcentagem, só bytes recebidos,
     * já que a resposta do backup em si é "close-delimited" (sem Content-Length).
     */
    private fun handleBackupSize(request: Request, output: OutputStream) {
        val kind = request.query["kind"]
        val paths = when (kind) {
            "apps" -> RemoteControlOps.estimateAppsBackupPaths()
            "full" -> RemoteControlOps.resolveFullBackupPaths()
            "app" -> {
                val pkg = request.query["package"]
                if (pkg.isNullOrBlank()) {
                    writeJson(output, JSONObject().put("ok", false).put("error", "package ausente"), "400 Bad Request")
                    return
                }
                RemoteControlOps.estimateAppBackupPaths(pkg)
            }
            else -> null
        }
        if (paths.isNullOrEmpty()) {
            writeJson(output, JSONObject().put("ok", false).put("error", "kind inválido ou nada para estimar"), "400 Bad Request")
            return
        }
        val bytes = RemoteControlOps.estimateSizeBytes(paths)
        if (bytes == null) {
            writeJson(output, JSONObject().put("ok", false).put("error", "falha ao estimar tamanho"), "500 Internal Server Error")
            return
        }
        writeJson(output, JSONObject().put("ok", true).put("bytes", bytes))
    }

    private fun handleDvrFind(output: OutputStream) {
        val path = DvrManager.detectStoragePathDeep()
        if (path == null) {
            writeJson(output, JSONObject().put("ok", false).put("error", "Nenhum arquivo de vídeo encontrado no armazenamento"), "404 Not Found")
            return
        }
        writeJson(output, JSONObject().put("ok", true).put("path", path))
    }

    /**
     * Espaço ocupado e livre no armazenamento da central.
     *
     * Serve os mesmos números que o card "Armazenamento" mostra na tela do carro — mesma fonte
     * ([StorageInfo]), para que instalador e carro nunca discordem. Não depende de Shizuku, então
     * responde mesmo num carro recém-instalado onde nada foi autorizado ainda.
     */
    private fun handleStorage(output: OutputStream) {
        val volumes = StorageInfo.volumes()
        val (total, free) = StorageInfo.totals(volumes)
        val array = JSONArray()
        for (volume in volumes) {
            array.put(
                JSONObject()
                    .put("label", volume.label)
                    .put("path", volume.path)
                    .put("totalBytes", volume.totalBytes)
                    .put("freeBytes", volume.freeBytes)
                    .put("usedBytes", volume.usedBytes)
                    .put("removable", volume.removable)
            )
        }
        writeJson(
            output,
            JSONObject()
                .put("ok", volumes.isNotEmpty())
                .put("totalBytes", total)
                .put("freeBytes", free)
                .put("usedBytes", (total - free).coerceAtLeast(0L))
                .put("volumes", array)
        )
    }

    private fun handleBackupApp(request: Request, output: OutputStream) {
        val pkg = request.query["package"]
        if (pkg.isNullOrBlank()) {
            writeJson(output, JSONObject().put("ok", false).put("error", "package ausente"), "400 Bad Request")
            return
        }
        val safeName = pkg.replace(Regex("[^A-Za-z0-9._-]"), "_")
        writeStatusHeaders(
            output, "200 OK", "application/x-tar",
            "Content-Disposition: attachment; filename=\"$safeName.tar\"\r\n"
        )
        RemoteControlOps.streamSingleAppBackup(pkg, output)
        output.flush()
    }

    /**
     * Escrita genérica no carro para o instalador desktop (item "espelhar tudo" do redesign):
     * um único endpoint reaproveitado por todas as telas novas (ADAS, perfis, DVR, ajustes),
     * em vez de uma rota por feature — mesmo mecanismo que [ServiceManager.updateData] já usa
     * internamente pra AVAS/DMS/ESP/etc. Sem autenticação própria, mesma postura do resto deste
     * servidor: local à rede Wi-Fi do carro, nunca deve ser exposto além dela.
     */
    // ── Rotas do launcher 3D ───────────────────────────────────────────────────────────────
    //
    // A tela inicial 3D é uma página dentro de um WebView; ela não tem Shizuku nem acesso direto
    // ao ControlService. Tudo que ela precisa do carro passa por aqui, pelo mesmo servidor que o
    // instalador já usa. Desde a fusão dos dois apps a Activity que hospeda esse WebView é deste
    // pacote, e o código dela está em `br.com.deepgwt.tela3d` — mas as rotas continuam sendo a
    // fronteira: quem fala com o carro é este lado, que tem Shizuku, e não o JavaScript.

    private fun handleIdentity(output: OutputStream) {
        val resolution = VehicleIdentity.resolve()
        writeJson(output, VehicleIdentity.toJson(resolution))
    }

    private fun handleIdentitySelect(request: Request, output: OutputStream) {
        val json = request.body?.let { runCatching { JSONObject(String(it, Charsets.UTF_8)) }.getOrNull() }
        val trim = json?.optString("trim")?.takeIf { it.isNotBlank() }
        val colorHex = json?.optString("colorHex")?.takeIf { it.isNotBlank() }
        if (trim == null) {
            writeJson(output, JSONObject().put("ok", false).put("error", "trim ausente"), "400 Bad Request")
            return
        }
        val ok = VehicleIdentity.select(DeepGwt.contexto, trim, colorHex)
        if (ok) {
            writeJson(output, VehicleIdentity.toJson(VehicleIdentity.resolve()))
        } else {
            writeJson(
                output,
                JSONObject().put("ok", false)
                    .put("error", "modelo fora da lista: ${VehicleIdentity.KNOWN_TRIMS.joinToString(", ")}"),
                "400 Bad Request"
            )
        }
    }

    private fun handleNivelDeTeste(request: Request, output: OutputStream) {
        val corpo = request.body?.let { runCatching { JSONObject(String(it, Charsets.UTF_8)) }.getOrNull() }
        LicencaRemota.definirNivelDeTeste(corpo)
            .onSuccess { writeJson(output, it) }
            .onFailure {
                writeJson(output, JSONObject().put("ok", false).put("error", it.message ?: "recusado"), "403 Forbidden")
            }
    }

    private fun handleLicencaAtivar(request: Request, output: OutputStream) {
        val corpo = request.body?.let { runCatching { JSONObject(String(it, Charsets.UTF_8)) }.getOrNull() }
        LicencaRemota.ativar(corpo)
            .onSuccess { writeJson(output, it) }
            .onFailure { writeJson(output, LicencaRemota.recusaDeAtivacao(it), "400 Bad Request") }
    }

    /** Teste grátis do Max (8.4): instalador e página do celular. */
    private fun handleLicencaAvaliacao(output: OutputStream) {
        LicencaRemota.iniciarAvaliacao()
            .onSuccess { writeJson(output, it) }
            .onFailure { writeJson(output, LicencaRemota.recusaDeAtivacao(it), "400 Bad Request") }
    }

    /** "Voltar ao meu plano" (10.1): encerra o teste grátis. */
    private fun handleLicencaAvaliacaoEncerrar(output: OutputStream) {
        LicencaRemota.encerrarAvaliacao()
            .onSuccess { writeJson(output, it) }
            .onFailure { writeJson(output, LicencaRemota.recusaDeAtivacao(it), "400 Bad Request") }
    }

    private fun handleLicencaTeste(request: Request, output: OutputStream) {
        val corpo = request.body?.let { runCatching { JSONObject(String(it, Charsets.UTF_8)) }.getOrNull() }
        LicencaRemota.teste(corpo)
            .onSuccess { writeJson(output, it) }
            .onFailure {
                writeJson(output, JSONObject().put("ok", false).put("error", it.message ?: "recusado"), "403 Forbidden")
            }
    }

    private fun handlePaginaDeAtivar(output: OutputStream) {
        val pagina = runCatching { DeepGwt.contexto.assets.open("licenca/ativar.html").use { it.readBytes() } }.getOrNull()
        if (pagina == null) {
            writeJson(output, JSONObject().put("ok", false).put("error", "página ausente"), "404 Not Found")
            return
        }
        writeStatusHeaders(output, "200 OK", "text/html; charset=utf-8", "Content-Length: ${pagina.size}\r\nCache-Control: no-store\r\n")
        output.write(pagina)
        output.flush()
    }

    private fun handleLauncherSet(request: Request, output: OutputStream) {
        val json = request.body?.let { runCatching { JSONObject(String(it, Charsets.UTF_8)) }.getOrNull() }
        val target = json?.optString("package")?.takeIf { it.isNotBlank() }
            ?: request.query["package"]
        if (target == null) {
            writeJson(output, JSONObject().put("ok", false).put("error", "package ausente"), "400 Bad Request")
            return
        }
        // Voltar à tela de fábrica é sempre livre; escolher o 3D pede o nível dele.
        if (target == HomeLauncherController.VIEWER_PACKAGE && !Portao.permite(Recurso.LAUNCHER_3D)) {
            writeJson(output, LicencaRemota.recusa(Recurso.LAUNCHER_3D), "403 Forbidden")
            return
        }
        HomeLauncherController.setHome(target)
            .onSuccess { writeJson(output, HomeLauncherController.toJson()) }
            .onFailure {
                writeJson(
                    output,
                    JSONObject().put("ok", false).put("error", it.message ?: "falha ao trocar a tela inicial"),
                    "500 Internal Server Error"
                )
            }
    }

    /** Tudo que os atalhos rápidos do launcher precisam mostrar, numa ida só. */
    private fun handleQuickState(output: OutputStream) {
        val context = DeepGwt.contexto
        val asd = runCatching {
            JSONObject()
                .put("enabled", AsdManager.isMasterEnabled())
                .put("volume", AsdManager.getVolume())
                .put("muffle", AsdManager.isMuffleEnabled())
        }.getOrElse { JSONObject().put("error", it.message ?: "ASD indisponível") }

        writeJson(
            output,
            JSONObject()
                .put("ok", true)
                .put("asd", asd)
                .put("stealthActive", StealthModeController.isActive(context))
                // Estado inicial do atalho do Painel Esportivo no launcher 3D. Depois disso ele
                // acompanha a chave empurrada (SportPlusController.KEY_ACTIVE) e não pergunta
                // mais nada — esta resposta é só para quem acabou de abrir a tela.
                .put("sportPlusActive", SportPlusController.isActive)
                .put("commandsEnabled", viewerCommandsEnabled(context))
                .put("launcher", HomeLauncherController.toJson())
        )
    }

    private fun viewerCommandsEnabled(context: android.content.Context): Boolean =
        DeepGwt.contextoProtegido
            .getSharedPreferences("deepgwt_prefs", android.content.Context.MODE_PRIVATE)
            .getBoolean(Preferencias.VIEWER_COMMANDS_ENABLED.chave, true)

    private fun handleQuickAsd(request: Request, output: OutputStream) {
        val json = request.body?.let { runCatching { JSONObject(String(it, Charsets.UTF_8)) }.getOrNull() }
        if (json == null) {
            writeJson(output, JSONObject().put("ok", false).put("error", "corpo ausente"), "400 Bad Request")
            return
        }
        runCatching {
            if (json.has("enabled")) AsdManager.setMasterEnabled(json.getBoolean("enabled"))
            if (json.has("volume")) AsdManager.setVolume(json.getInt("volume").coerceIn(0, 100))
            if (json.has("muffle")) {
                AsdManager.setMuffle(json.getBoolean("muffle"), AsdManager.getMuffleAmount())
            }
        }.onFailure {
            writeJson(
                output,
                JSONObject().put("ok", false).put("error", it.message ?: "falha no ASD"),
                "500 Internal Server Error"
            )
            return
        }
        handleQuickState(output)
    }

    /**
     * Modo sorrateiro é de mão única por projeto: ele esconde o ícone do DeepGWT, e a saída é o
     * botão "Sair do Modo Sorrateiro" do instalador no computador. Por isso não existe rota para
     * desligar — quem liga daqui precisa ter lido o aviso que o launcher mostra antes.
     */
    private fun handleQuickStealth(output: OutputStream) {
        val context = DeepGwt.contexto
        if (StealthModeController.isActive(context)) {
            writeJson(output, JSONObject().put("ok", true).put("stealthActive", true).put("alreadyActive", true))
            return
        }
        runCatching { StealthModeController.activate(context) }
            .onSuccess { writeJson(output, JSONObject().put("ok", true).put("stealthActive", true)) }
            .onFailure {
                writeJson(
                    output,
                    JSONObject().put("ok", false).put("error", it.message ?: "falha ao ativar"),
                    "500 Internal Server Error"
                )
            }
    }

    /**
     * Abre o Painel Esportivo (só a telemetria), sem os efeitos do SPORT+ no veículo.
     *
     * É a mesma porta que o botão que existia na barra inferior usava
     * ([SportPlusController.openDisplayOnly]): motor forçado, ESP e modo alagamento continuam
     * como estão. O botão saiu da barra e virou este atalho do launcher — a ação não mudou, só o
     * lugar de onde ela é chamada.
     */
    private fun handleQuickSportsDisplay(output: OutputStream) {
        runCatching { SportPlusController.openDisplayOnly() }
            .onSuccess { writeJson(output, JSONObject().put("ok", true)) }
            .onFailure {
                writeJson(
                    output,
                    JSONObject().put("ok", false)
                        .put("error", it.message ?: "falha ao abrir o Painel Esportivo"),
                    "500 Internal Server Error"
                )
            }
    }

    /**
     * Perfis de motorista para o launcher 3D (e para o instalador).
     *
     * Devolve também o relatório da última aplicação: quem trocou de perfil pelo launcher precisa
     * conseguir ver o que o carro recusou sem abrir o app — senão o aviso passa e a pendência
     * fica invisível, que é justamente o que o recurso promete não fazer.
     */
    private fun handleProfiles(output: OutputStream) {
        val lista = JSONArray()
        for (perfil in DriverProfileManager.listProfiles()) {
            lista.put(
                JSONObject()
                    .put("name", perfil.name)
                    .put("ajustes", perfil.deepGwt.size)
                    .put("veiculo", perfil.chavesDoVeiculo().size)
                    .put("banco", perfil.seatMemorySlot ?: JSONObject.NULL)
                    .put("legado", perfil.ehLegado)
                    .put("atualizadoEm", perfil.atualizadoEm)
            )
        }
        val resposta = JSONObject()
            .put("ok", true)
            .put("active", DriverProfileManager.getActiveProfileName() ?: JSONObject.NULL)
            .put("profiles", lista)
        DriverProfileManager.ultimoRelatorio?.let { resposta.put("ultimo", relatorioParaJson(it)) }
        writeJson(output, resposta)
    }

    private fun handleProfileSelect(request: Request, output: OutputStream) {
        val json = request.body?.let { runCatching { JSONObject(String(it, Charsets.UTF_8)) }.getOrNull() }
        val nome = json?.optString("name")?.takeIf { it.isNotBlank() }
        if (nome == null) {
            writeJson(output, JSONObject().put("ok", false).put("error", "name ausente"), "400 Bad Request")
            return
        }
        if (DriverProfileManager.loadProfile(nome) == null) {
            writeJson(output, JSONObject().put("ok", false).put("error", "perfil \"$nome\" não existe"), "404 Not Found")
            return
        }
        val relatorio = DriverProfileManager.aplicar(nome)
        writeJson(
            output,
            JSONObject()
                .put("ok", relatorio.erroFatal == null)
                .put("active", DriverProfileManager.getActiveProfileName() ?: JSONObject.NULL)
                .put("relatorio", relatorioParaJson(relatorio))
        )
    }

    /**
     * Cria (ou regrava) um perfil com o estado ATUAL do carro.
     *
     * O retrato é sempre tirado aqui, no carro — o instalador não manda uma lista de valores.
     * Tem de ser assim: o que entra no perfil é a varredura do `deepgwt_prefs`, que só existe deste
     * lado, e uma lista montada no computador voltaria a ser a lista fixa que este trabalho veio
     * justamente eliminar.
     */
    private fun handleProfileSave(request: Request, output: OutputStream) {
        val json = request.body?.let { runCatching { JSONObject(String(it, Charsets.UTF_8)) }.getOrNull() }
        val nome = json?.optString("name")?.trim()?.takeIf { it.isNotBlank() }
        if (nome == null) {
            writeJson(output, JSONObject().put("ok", false).put("error", "name ausente"), "400 Bad Request")
            return
        }
        val slot = json.opt("seatMemorySlot")?.let { (it as? Number)?.toInt() }
            ?: DriverProfileManager.loadProfile(nome)?.seatMemorySlot
        runCatching { DriverProfileManager.saveCurrentAsProfile(nome, slot) }
            .onSuccess { handleProfiles(output) }
            .onFailure {
                writeJson(
                    output,
                    JSONObject().put("ok", false).put("error", it.message ?: "falha ao salvar"),
                    "500 Internal Server Error"
                )
            }
    }

    /** O HUD como está agora, no formato do tema — para o instalador partir do que o carro tem. */
    private fun handleHudTemaLer(output: OutputStream) {
        val tema = br.com.deepgwt.hud.HudSettings.temaAtual()
        writeJson(output, JSONObject().put("ok", true).put("tema", JSONObject(tema.paraJson())))
    }

    /**
     * Aplica um tema de HUD vindo do instalador.
     *
     * O corpo é o próprio arquivo do tema (ver [br.com.deepgwt.hud.HudTema]).
     * A aplicação roda na thread principal e esta espera por ela: os ajustes do HUD são estado do
     * Compose que a tela de Telas pode estar desenhando agora, e a resposta só diz "ok" depois que
     * o tema está de fato gravado.
     */
    private fun handleHudTemaAplicar(request: Request, output: OutputStream) {
        val texto = request.body?.let { String(it, Charsets.UTF_8) }
        if (texto.isNullOrBlank()) {
            writeJson(output, JSONObject().put("ok", false).put("error", "corpo ausente"), "400 Bad Request")
            return
        }
        val tema =
                try {
                    br.com.deepgwt.hud.HudTema.deJson(texto)
                } catch (e: IllegalArgumentException) {
                    writeJson(output, JSONObject().put("ok", false).put("error", e.message ?: "tema inválido"), "400 Bad Request")
                    return
                }
        val feito = java.util.concurrent.CountDownLatch(1)
        var falha: Throwable? = null
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                br.com.deepgwt.hud.HudSettings.aplicarTema(tema)
            } catch (t: Throwable) {
                falha = t
            } finally {
                feito.countDown()
            }
        }
        if (!feito.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
            writeJson(output, JSONObject().put("ok", false).put("error", "o app não respondeu a tempo"), "503 Service Unavailable")
            return
        }
        falha?.let {
            Log.w(TAG, "Falha ao aplicar tema do HUD: ${it.message}")
            writeJson(output, JSONObject().put("ok", false).put("error", it.message ?: "falha ao aplicar"), "500 Internal Server Error")
            return
        }
        writeJson(output, JSONObject().put("ok", true).put("nome", tema.nome))
    }

    /**
     * Segura a resposta até algum ajuste mudar (no carro ou por outro cliente) ou o tempo acabar.
     *
     * É a metade "carro → computador" da sincronia. Perguntar em laço a cada segundo custaria uma
     * leitura inteira de ~115 ajustes por segundo com nada mudando; aqui a conexão fica parada, sem
     * custo, e volta em milissegundos quando alguém mexe. Cada cliente já tem thread própria.
     */
    private fun handleAjustesEspera(request: Request, output: OutputStream) {
        val desde = request.query["desde"]?.toLongOrNull()
        if (desde == null) {
            writeJson(output, JSONObject().put("ok", false).put("error", "desde ausente"), "400 Bad Request")
            return
        }
        val ms = (request.query["ms"]?.toLongOrNull() ?: 20_000L).coerceIn(0L, 25_000L)
        val atual = AjustesRemotos.esperar(desde, ms)
        writeJson(output, JSONObject().put("ok", true).put("revisao", atual).put("mudou", atual != desde))
    }

    private fun handleAjustesAplicar(request: Request, output: OutputStream) {
        val json = request.body?.let { runCatching { JSONObject(String(it, Charsets.UTF_8)) }.getOrNull() }
        val valores = json?.optJSONObject("valores")
        if (valores == null) {
            writeJson(output, JSONObject().put("ok", false).put("error", "valores ausentes"), "400 Bad Request")
            return
        }
        val entrada = linkedMapOf<String, Any?>()
        valores.keys().forEach { chave ->
            entrada[chave] = valores.opt(chave).takeUnless { it == JSONObject.NULL }
        }
        val resposta = AjustesRemotos.aplicar(entrada)
        writeJson(output, resposta, if (resposta.optBoolean("ok")) "200 OK" else "503 Service Unavailable")
    }

    // --- tema do painel (Alpha 6.5) -------------------------------------------------------------

    private fun prefsDoApp() = DeepGwt.contextoProtegido
        .getSharedPreferences("deepgwt_prefs", android.content.Context.MODE_PRIVATE)

    private fun estadoDosTemas(): JSONObject {
        val contexto = DeepGwt.contexto
        val prefs = prefsDoApp()
        val lista = JSONArray()
        for (t in TemaDoPainel.listar(contexto)) {
            lista.put(
                JSONObject()
                    .put("pasta", t.pasta)
                    .put("nome", t.nome)
                    .put("versao", t.versao)
                    .put("descricao", t.descricao)
                    .put("embutido", t.embutido)
                    .put("casta", t.casta.id)
            )
        }
        return JSONObject()
            .put("ok", true)
            .put("ativo", TemaDoPainel.ativo(prefs))
            .put("rascunho", TemaDoPainel.rascunhoAtivo(prefs))
            .put("temaCustomizado", br.com.deepgwt.ui.screens.ModoDoPainel.ler(prefs).desenhaOPainelInteiro)
            .put("temas", lista)
    }

    private fun handleTemasListar(output: OutputStream) {
        val estado = runCatching { estadoDosTemas() }.getOrElse {
            writeJson(output, JSONObject().put("ok", false).put("error", it.message ?: "falha ao listar"), "500 Internal Server Error")
            return
        }
        writeJson(output, estado)
    }

    private fun respostaDeTema(r: TemaDoPainel.Resultado, output: OutputStream) {
        when (r) {
            is TemaDoPainel.Resultado.Feito ->
                writeJson(output, estadoDosTemas().put("aplicado", JSONObject().put("pasta", r.pasta).put("nome", r.nome)))
            is TemaDoPainel.Resultado.Recusado ->
                writeJson(output, JSONObject().put("ok", false).put("error", r.motivo), "409 Conflict")
        }
    }

    private fun handleTemasAtivar(request: Request, output: OutputStream) {
        val json = request.body?.let { runCatching { JSONObject(String(it, Charsets.UTF_8)) }.getOrNull() }
        if (json == null || !json.has("pasta")) {
            writeJson(output, JSONObject().put("ok", false).put("error", "pasta ausente"), "400 Bad Request")
            return
        }
        val pasta = json.optString("pasta", "")
        if (pasta == TemaDoPainel.PASTA_DO_RASCUNHO) {
            writeJson(output, JSONObject().put("ok", false).put("error", "o rascunho entra pelo Ver no carro, não por aqui"), "400 Bad Request")
            return
        }
        if (pasta == TemaDoPainel.PASTA_DA_CALIBRACAO) {
            writeJson(output, JSONObject().put("ok", false).put("error", "a grade de calibração entra pelo Diagnóstico do carro, não por aqui"), "400 Bad Request")
            return
        }
        respostaDeTema(TemaDoPainel.aplicarPorPasta(DeepGwt.contexto, prefsDoApp(), pasta), output)
    }

    /**
     * `{"ativar": true}` troca o painel para o rascunho (ou só recarrega, se já estiver nele) —
     * o instalador chama a cada envio. `{"ativar": false}` volta ao tema de antes e apaga a pasta.
     */
    private fun handleTemasRascunho(request: Request, output: OutputStream) {
        val json = request.body?.let { runCatching { JSONObject(String(it, Charsets.UTF_8)) }.getOrNull() }
        if (json == null || !json.has("ativar")) {
            writeJson(output, JSONObject().put("ok", false).put("error", "ativar ausente"), "400 Bad Request")
            return
        }
        val contexto = DeepGwt.contexto
        val prefs = prefsDoApp()
        val r = if (json.optBoolean("ativar")) TemaDoPainel.iniciarRascunho(contexto, prefs)
                else TemaDoPainel.encerrarRascunho(contexto, prefs)
        respostaDeTema(r, output)
    }

    /** O histórico de serviço do carro, o odômetro e a próxima revisão. */
    private fun handleServico(output: OutputStream) {
        val registros = br.com.deepgwt.ui.telas.HistoricoDeServico.ordenar(
            br.com.deepgwt.ui.telas.HistoricoDeServico.ler(prefsDoApp())
        )
        writeJson(output, servicoParaJson(registros))
    }

    private fun servicoParaJson(registros: List<br.com.deepgwt.ui.telas.RegistroDeServico>): JSONObject {
        val historico = br.com.deepgwt.ui.telas.HistoricoDeServico
        val proxima = historico.proxima(registros)
        val lista = JSONArray(historico.paraJson(historico.ordenar(registros)))
        val tipos = JSONArray()
        br.com.deepgwt.ui.telas.TipoDeServico.entries.forEach {
            tipos.put(JSONObject().put("id", it.id).put("rotulo", it.rotulo))
        }
        return JSONObject()
            .put("ok", true)
            .put("registros", lista)
            .put("tipos", tipos)
            .put("odometro", br.com.deepgwt.veiculo.comandos.LeiturasDoCarro.quilometragemTotal())
            .put("proximaKm", proxima.km)
            .put("proximaData", proxima.data)
    }

    private fun handleServicoAdicionar(request: Request, output: OutputStream) {
        val json = request.body?.let { runCatching { JSONObject(String(it, Charsets.UTF_8)) }.getOrNull() }
        val historico = br.com.deepgwt.ui.telas.HistoricoDeServico
        val km = json?.optInt("km", -1) ?: -1
        val data = json?.optLong("data", 0L) ?: 0L
        if (json == null || km < 0 || data <= 0L) {
            writeJson(output, JSONObject().put("ok", false).put("error", "km e data são obrigatórios"), "400 Bad Request")
            return
        }
        val novo = br.com.deepgwt.ui.telas.RegistroDeServico(
            id = 0,
            tipo = br.com.deepgwt.ui.telas.TipoDeServico.deId(json.optString("tipo")),
            km = km,
            data = data,
            descricao = json.optString("descricao").trim().take(historico.LIMITE_DE_TEXTO),
            oficina = json.optString("oficina").trim().take(historico.LIMITE_DE_TEXTO),
            custoCentavos = json.optLong("custoCentavos", 0L).coerceAtLeast(0L),
        )
        writeJson(output, servicoParaJson(historico.adicionar(prefsDoApp(), novo)))
    }

    private fun handleServicoApagar(request: Request, output: OutputStream) {
        val json = request.body?.let { runCatching { JSONObject(String(it, Charsets.UTF_8)) }.getOrNull() }
        if (json == null || !json.has("id")) {
            writeJson(output, JSONObject().put("ok", false).put("error", "id ausente"), "400 Bad Request")
            return
        }
        writeJson(
            output,
            servicoParaJson(br.com.deepgwt.ui.telas.HistoricoDeServico.apagar(prefsDoApp(), json.optLong("id")))
        )
    }

    private fun handleProfileDelete(request: Request, output: OutputStream) {
        val json = request.body?.let { runCatching { JSONObject(String(it, Charsets.UTF_8)) }.getOrNull() }
        val nome = json?.optString("name")?.takeIf { it.isNotBlank() }
        if (nome == null) {
            writeJson(output, JSONObject().put("ok", false).put("error", "name ausente"), "400 Bad Request")
            return
        }
        DriverProfileManager.deleteProfile(nome)
        handleProfiles(output)
    }

    private fun relatorioParaJson(
        relatorio: br.com.deepgwt.perfis.RelatorioDeAplicacao
    ): JSONObject {
        val detalhes = JSONArray()
        relatorio.detalhes().forEach { detalhes.put(it) }
        return JSONObject()
            .put("perfil", relatorio.perfil)
            .put("tudoCerto", relatorio.tudoCerto)
            .put("resumo", relatorio.resumo())
            .put("detalhes", detalhes)
            .put("ajustes", relatorio.preferenciasAplicadas)
            .put("veiculo", relatorio.veiculoAplicado)
    }

    private fun handleSetValue(request: Request, output: OutputStream) {
        val json = request.body?.let { JSONObject(String(it, Charsets.UTF_8)) }
        val key = json?.optString("key")?.takeIf { it.isNotBlank() }
        // `optString` devolve "" quando a chave falta: isso gravaria texto vazio no carro.
        val value = json?.opt("value")?.takeIf { it != JSONObject.NULL }?.toString()
        if (key == null || value == null) {
            writeJson(output, JSONObject().put("ok", false).put("error", "key ou value ausente"), "400 Bad Request")
            return
        }
        val success = Veiculo.escreverConferindo(key, value)
        if (success) {
            writeJson(output, JSONObject().put("ok", true))
        } else {
            writeJson(output, JSONObject().put("ok", false).put("error", "ControlService indisponível ou falhou ao aplicar"), "500 Internal Server Error")
        }
    }

    private fun buildTelemetryJson(): JSONObject {
        val data = Veiculo.lerTudoDoCarro()
        val json = JSONObject()
        for ((key, value) in data) json.put(key, value)
        return json
    }

    // Snapshot dos pontos-chave do bootstrap automático do Shizuku, pra poder diagnosticar
    // remotamente (pelo instalador no laptop) por que o restart automático não funcionou depois
    // de um reboot da MMI, sem precisar de logcat físico no carro.
    private fun buildDiagnosticsJson(): JSONObject {
        val nowElapsed = SystemClock.elapsedRealtime()
        fun agoOrNull(atElapsedMs: Long): Long? = if (atElapsedMs < 0) null else nowElapsed - atElapsedMs

        val json = JSONObject()
        json.put("uptimeMs", nowElapsed)
        json.put("shizukuPingBinderNow", runCatching { Shizuku.pingBinder() }.getOrDefault(false))
        json.put("bootReceiverAgoMs", agoOrNull(BootDiagnostics.bootReceiverAtElapsedMs))
        json.put("bootReceiverAction", BootDiagnostics.bootReceiverAction)
        json.put("onStartCommandAgoMs", agoOrNull(BootDiagnostics.onStartCommandAtElapsedMs))
        json.put("bootstrapAttemptCount", BootDiagnostics.bootstrapAttemptCount)
        json.put("lastBootstrapAttemptAgoMs", agoOrNull(BootDiagnostics.lastBootstrapAttemptAtElapsedMs))
        json.put("lastBootstrapError", BootDiagnostics.lastBootstrapError)
        json.put("shizukuBinderReceivedAgoMs", agoOrNull(BootDiagnostics.shizukuBinderReceivedAtElapsedMs))
        json.put("shizukuBinderTimeoutCount", BootDiagnostics.shizukuBinderTimeoutCount)
        json.put("lastRestartAgoMs", agoOrNull(BootDiagnostics.lastRestartAtElapsedMs))
        json.put("lastRestartReason", BootDiagnostics.lastRestartReason)
        json.put("lastBootstrapTelnetHost", BootDiagnostics.lastBootstrapTelnetHost)
        json.put("lastBootstrapTelnetHostsTried", BootDiagnostics.lastBootstrapTelnetHostsTried)
        json.put("lastBootstrapTelnetConnectMs", BootDiagnostics.lastBootstrapTelnetConnectMs)
        json.put("lockedBootReceiverAgoMs", agoOrNull(BootDiagnostics.lockedBootReceiverAtElapsedMs))
        json.put("firstBootstrapAttemptAgoMs", agoOrNull(BootDiagnostics.firstBootstrapAttemptAtElapsedMs))
        json.put("lastBootstrapStartOutcome", BootDiagnostics.lastBootstrapStartOutcome)
        json.put("lastBootstrapApkArg", BootDiagnostics.lastBootstrapApkArg)
        json.put("lastBootstrapServerLog", BootDiagnostics.lastBootstrapServerLog)
        json.put("rebootstrapAfterBinderTimeoutCount", BootDiagnostics.rebootstrapAfterBinderTimeoutCount)
        json.put("lastRebootstrapAgoMs", agoOrNull(BootDiagnostics.lastRebootstrapAtElapsedMs))
        json.put("lastRebootstrapTimeoutCount", BootDiagnostics.lastRebootstrapTimeoutCount)
        json.put("staleBinderOnAlreadyRunningCount", BootDiagnostics.staleBinderOnAlreadyRunningCount)
        json.put(
            "lastStaleBinderOnAlreadyRunningAgoMs",
            agoOrNull(BootDiagnostics.lastStaleBinderOnAlreadyRunningAtElapsedMs)
        )

        // A MESMA linha do tempo, só que contada a partir do boot em vez de "há quanto tempo" —
        // que é o formato em que a pergunta "quanto o Shizuku demorou para subir nesta partida?"
        // tem resposta direta, sem subtrair do uptime à mão. Todos os valores saem do mesmo
        // relógio (SystemClock.elapsedRealtime), então a diferença entre dois deles é o intervalo.
        // Chave nova, aninhada: nada do que já estava aqui muda de nome ou de sentido.
        val desdeOBoot = JSONObject()
        fun desdeOBootOuNulo(atElapsedMs: Long): Long? = if (atElapsedMs < 0) null else atElapsedMs
        desdeOBoot.put("lockedBootReceiver", desdeOBootOuNulo(BootDiagnostics.lockedBootReceiverAtElapsedMs))
        desdeOBoot.put("bootReceiver", desdeOBootOuNulo(BootDiagnostics.bootReceiverAtElapsedMs))
        desdeOBoot.put("onStartCommand", desdeOBootOuNulo(BootDiagnostics.onStartCommandAtElapsedMs))
        desdeOBoot.put("firstBootstrapAttempt", desdeOBootOuNulo(BootDiagnostics.firstBootstrapAttemptAtElapsedMs))
        desdeOBoot.put("lastBootstrapAttempt", desdeOBootOuNulo(BootDiagnostics.lastBootstrapAttemptAtElapsedMs))
        desdeOBoot.put("lastRebootstrap", desdeOBootOuNulo(BootDiagnostics.lastRebootstrapAtElapsedMs))
        desdeOBoot.put("lastRestart", desdeOBootOuNulo(BootDiagnostics.lastRestartAtElapsedMs))
        desdeOBoot.put("shizukuBinderReceived", desdeOBootOuNulo(BootDiagnostics.shizukuBinderReceivedAtElapsedMs))
        desdeOBoot.put(
            "lastStaleBinderOnAlreadyRunningStart",
            desdeOBootOuNulo(BootDiagnostics.lastStaleBinderOnAlreadyRunningAtElapsedMs)
        )
        json.put("bootTimelineSinceBootMs", desdeOBoot)
        json.put("recentEventLog", Diario.ultimasLinhas(200))

        val sportPlus = JSONObject()
        sportPlus.put("monitoringStartedAgoMs", agoOrNull(SportPlusController.monitoringStartedAtElapsedMs))
        sportPlus.put("isActive", SportPlusController.isActive)
        sportPlus.put("lastRawValueSeen", SportPlusController.lastRawValueSeen)
        sportPlus.put("lastMappedModeSeen", SportPlusController.lastMappedModeSeen)
        sportPlus.put("lastEventAgoMs", agoOrNull(SportPlusController.lastEventAtElapsedMs))
        sportPlus.put("enteredSportCount", SportPlusController.enteredSportCount)
        sportPlus.put("leftSportCount", SportPlusController.leftSportCount)
        sportPlus.put("activateCallCount", SportPlusController.activateCallCount)
        sportPlus.put("deactivateCallCount", SportPlusController.deactivateCallCount)
        sportPlus.put("lastSkippedReason", SportPlusController.lastSkippedReason)
        json.put("sportPlus", sportPlus)

        return json
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
