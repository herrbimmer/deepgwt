// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.managers

import br.com.deepgwt.nucleo.DeepGwt
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.ParcelFileDescriptor
import android.util.Log
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Primitivas de "Opções de Desenvolvedor" (mirror de tela, injeção de toque, arquivos e backup)
 * usadas pelo GwLinkServer. Tudo passa por processos root via Shizuku (o processo do próprio
 * app não tem permissão pra ler /data/data de outros apps ou capturar telas secundárias), então
 * cada operação aqui é, na prática, um `newProcess` do Shizuku com stdin/stdout/stderr crus —
 * diferente dos helpers de texto do [br.com.deepgwt.nucleo.Shell], que
 * assumem saída em linhas (inadequado pra PNG/JPEG/tar binário).
 */
object RemoteControlOps {
    private const val TAG = "RemoteControlOps"

    data class DisplayInfo(val id: Int, val name: String, val width: Int, val height: Int)

    data class FileEntry(val name: String, val isDir: Boolean, val size: Long)

    fun listDisplays(): List<DisplayInfo> {
        val dm = DeepGwt.contexto.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return dm.displays.map { d ->
            val point = Point()
            @Suppress("DEPRECATION")
            d.getRealSize(point)
            DisplayInfo(d.displayId, d.name ?: "Display ${d.displayId}", point.x, point.y)
        }
    }

    private fun shizukuService(): IShizukuService? {
        val binder = Shizuku.getBinder() ?: return null
        return IShizukuService.Stub.asInterface(binder)
    }

    private fun newProcess(command: Array<String>): IRemoteProcess? {
        val service = shizukuService() ?: run {
            Log.e(TAG, "Shizuku indisponível para: ${command.joinToString(" ")}")
            return null
        }
        return try {
            service.newProcess(command, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao criar processo: ${command.joinToString(" ")}", e)
            null
        }
    }

    private fun drainAndCloseAsync(pfd: ParcelFileDescriptor?) {
        if (pfd == null) return
        Thread({
            try {
                FileInputStream(pfd.fileDescriptor).use { it.readBytes() }
            } catch (_: Exception) {
            } finally {
                try { pfd.close() } catch (_: Exception) {}
            }
        }, "rc-drain").start()
    }

    /** Roda um comando e devolve todo o stdout como bytes. Uso: capturas de tela, saídas pequenas. */
    fun execCaptureBytes(command: Array<String>): ByteArray? {
        val process = newProcess(command) ?: return null
        return try {
            try { process.outputStream?.close() } catch (_: Exception) {}
            drainAndCloseAsync(process.errorStream)
            val outPfd = process.inputStream
            val bytes = outPfd?.let { FileInputStream(it.fileDescriptor).use { s -> s.readBytes() } }
            process.waitFor()
            bytes
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao capturar stdout: ${command.joinToString(" ")}", e)
            null
        } finally {
            try { process.destroy() } catch (_: Exception) {}
        }
    }

    /** Roda um comando e escreve o stdout cru em [out] conforme chega. Uso: streaming de backups grandes. */
    fun execStreamToOutput(command: Array<String>, out: OutputStream, exigirSaidaZero: Boolean = false): Boolean {
        val process = newProcess(command) ?: return false
        return try {
            try { process.outputStream?.close() } catch (_: Exception) {}
            drainAndCloseAsync(process.errorStream)
            val outPfd = process.inputStream
            if (outPfd != null) {
                FileInputStream(outPfd.fileDescriptor).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                    }
                }
            }
            val saida = process.waitFor()
            // `cat` de arquivo que não existe ou sem permissão sai com erro e stdout vazio: isso não
            // é um download feito. (O `tar` dos backups segue como estava: sai com 1 por aviso.)
            !exigirSaidaZero || saida == 0
        } catch (e: Exception) {
            Log.w(TAG, "Stream de comando interrompido: ${command.joinToString(" ")}: ${e.message}")
            false
        } finally {
            try { process.destroy() } catch (_: Exception) {}
        }
    }

    /** Roda um comando alimentando seu stdin com bytes de [input]. Uso: gravar arquivo no carro. */
    fun execWithStdin(command: Array<String>, input: InputStream): Boolean {
        val process = newProcess(command) ?: return false
        return try {
            drainAndCloseAsync(process.errorStream)
            drainAndCloseAsync(process.inputStream)
            val inPfd = process.outputStream
            if (inPfd != null) {
                ParcelFileDescriptor.AutoCloseOutputStream(inPfd).use { stdin ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        stdin.write(buffer, 0, read)
                    }
                    stdin.flush()
                }
            }
            process.waitFor() == 0
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao gravar stdin: ${command.joinToString(" ")}", e)
            false
        } finally {
            try { process.destroy() } catch (_: Exception) {}
        }
    }

    /**
     * Captura o display [displayId] via `screencap` root e reencoda como JPEG (bem menor que o
     * PNG cru do screencap, essencial pra não afogar o Wi-Fi do carro com 3 streams simultâneos).
     * Devolve null se o `screencap -d` não for suportado nesta build do Android — isso só se
     * confirma testando no carro de verdade.
     */
    fun captureDisplayJpeg(displayId: Int, quality: Int = 40, maxWidth: Int = 720): ByteArray? {
        val png = execCaptureBytes(arrayOf("screencap", "-d", displayId.toString(), "-p"))
        if (png == null || png.isEmpty()) return null
        val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size) ?: return null
        val scaled = if (bitmap.width > maxWidth) {
            val ratio = maxWidth.toFloat() / bitmap.width
            Bitmap.createScaledBitmap(bitmap, maxWidth, (bitmap.height * ratio).toInt(), true)
        } else {
            bitmap
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        if (scaled !== bitmap) bitmap.recycle()
        scaled.recycle()
        return out.toByteArray()
    }

    /** A tela [displayId] em PNG, na resolução do display, sem reencodar. Null se o `screencap` falhar. */
    fun captureDisplayPng(displayId: Int): ByteArray? {
        val png = execCaptureBytes(arrayOf("screencap", "-d", displayId.toString(), "-p"))
        // Assinatura PNG: sem ela o que veio é aviso do screencap escrito no stdout, não imagem.
        if (png == null || png.size < 8 || png[0] != 0x89.toByte() || png[1] != 'P'.code.toByte()) return null
        return png
    }

    /**
     * A tela principal em PNG, sem `-d` — o `screencap` sem tela escolhida captura a principal em
     * qualquer versão do Android (no 14 o `-d 0` nem é o id certo). Usada pela barra para medir o
     * encaixe dos apps (EncaixeDaBarra).
     */
    fun captureMainDisplayPng(): ByteArray? {
        val bruto = execCaptureBytes(arrayOf("screencap", "-p")) ?: return null
        // Do Android 12 em diante, com mais de uma tela, o screencap escreve um aviso no stdout
        // ANTES da imagem. O PNG começa na assinatura; o que vem antes é texto.
        val inicio = (0 until minOf(bruto.size - 8, 1024)).firstOrNull { i ->
            bruto[i] == 0x89.toByte() && bruto[i + 1] == 'P'.code.toByte() &&
                bruto[i + 2] == 'N'.code.toByte() && bruto[i + 3] == 'G'.code.toByte()
        } ?: return null
        return if (inicio == 0) bruto else bruto.copyOfRange(inicio, bruto.size)
    }

    data class ShellResult(val exitCode: Int, val output: String)

    /**
     * Como [execCaptureBytes], mas devolve exit code e o texto de stdout+stderr em vez de
     * descartar tudo — os primeiros testes reais de toque no carro mostraram que `input -d`
     * falhava sem nenhum sinal visível na UI, porque o retorno booleano/silencioso anterior não
     * dava pra distinguir "comando rodou e o toque não fez nada" de "comando nem existe nessa
     * build". Lê stdout e stderr em threads separadas pra não travar em pipe cheio.
     */
    private fun execCaptureResult(command: Array<String>): ShellResult {
        val process = newProcess(command) ?: return ShellResult(-1, "Shizuku indisponível")
        return try {
            try { process.outputStream?.close() } catch (_: Exception) {}
            val stdoutBuf = ByteArrayOutputStream()
            val stderrBuf = ByteArrayOutputStream()
            val stdoutThread = Thread({
                try {
                    process.inputStream?.let { FileInputStream(it.fileDescriptor).use { s -> s.copyTo(stdoutBuf) } }
                } catch (_: Exception) {}
            }, "rc-stdout").apply { start() }
            val stderrThread = Thread({
                try {
                    process.errorStream?.let { FileInputStream(it.fileDescriptor).use { s -> s.copyTo(stderrBuf) } }
                } catch (_: Exception) {}
            }, "rc-stderr").apply { start() }
            val exitCode = process.waitFor()
            stdoutThread.join(2000)
            stderrThread.join(2000)
            val combined = (stderrBuf.toString(Charsets.UTF_8.name()) + "\n" + stdoutBuf.toString(Charsets.UTF_8.name())).trim()
            ShellResult(exitCode, combined.take(2000))
        } catch (e: Exception) {
            ShellResult(-1, e.message ?: "erro desconhecido")
        } finally {
            try { process.destroy() } catch (_: Exception) {}
        }
    }

    /**
     * Roda o comando `input` mirando [displayId].
     *
     * O `input` só ganhou a opção `-d DISPLAY_ID` no Android 10 (API 29). **Esta central roda
     * Android 9**, onde `input -d 0 tap ...` não é reconhecido: o comando imprime o usage e sai
     * com erro, sem injetar nada. Era exatamente esse o sintoma do controle remoto — a imagem
     * chegava (o `screencap -d` existe desde muito antes e funciona) mas nenhum toque tinha
     * efeito. O resto do app já sabia disso na prática: a barra inferior manda
     * `input keyevent 4` sem `-d` e sempre funcionou.
     *
     * Abaixo da API 29 o comando vai sem `-d`, o que atinge a tela padrão. Telas secundárias
     * (cluster, HUD) não têm como receber toque via `input` nessa versão do Android — nesse
     * caso devolvemos um erro explicativo em vez de um comando que falharia em silêncio.
     */
    private fun runInput(displayId: Int, vararg args: String): ShellResult {
        val supportsDisplayFlag = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q
        if (!supportsDisplayFlag && displayId != android.view.Display.DEFAULT_DISPLAY) {
            return ShellResult(
                -1,
                "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}) " +
                    "não tem 'input -d': só a tela principal (display 0) aceita toque remoto neste veículo."
            )
        }
        val command =
            if (supportsDisplayFlag) arrayOf("input", "-d", displayId.toString(), *args)
            else arrayOf("input", *args)
        val result = execCaptureResult(command)
        if (result.exitCode != 0) {
            Log.w(TAG, "input falhou (exit ${result.exitCode}): ${command.joinToString(" ")} -> ${result.output}")
        }
        return result
    }

    fun injectTap(displayId: Int, x: Int, y: Int): ShellResult =
        runInput(displayId, "tap", x.toString(), y.toString())

    fun injectSwipe(displayId: Int, x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int): ShellResult =
        runInput(
            displayId, "swipe",
            x1.toString(), y1.toString(), x2.toString(), y2.toString(), durationMs.toString()
        )

    fun injectKey(displayId: Int, keycode: Int): ShellResult =
        runInput(displayId, "keyevent", keycode.toString())

    /**
     * Reinicia a central (reboot do Android do head unit). É seguro em movimento: a multimídia é
     * um sistema separado das ECUs de controle do carro — reiniciar a tela não toca em freio,
     * direção ou motor.
     *
     * NÃO espera o processo terminar: `reboot` derruba o sistema, então o processo Shizuku some
     * junto e um `waitFor` nunca retornaria. O reboot é disparado numa thread com [delayMs] de
     * atraso, para o chamador (o handler HTTP do GwLinkServer) conseguir responder "ok" e dar
     * flush no socket ANTES de a central cair — senão o PC veria só a conexão morrer.
     *
     * `reboot` vem do UID shell do Shizuku, que tem a permissão REBOOT (o mesmo que `adb reboot`
     * usa). Se o Shizuku estiver como root, idem. REQUER_VALIDACAO no carro: só o teste real
     * confirma que esta build do Android 9 aceita `reboot` por esse caminho.
     */
    fun rebootHeadUnit(delayMs: Long = 0): Boolean {
        val available = shizukuService() != null
        if (!available) {
            Log.e(TAG, "Shizuku indisponível — reboot da central não disparado.")
            return false
        }
        Thread({
            if (delayMs > 0) try { Thread.sleep(delayMs) } catch (_: InterruptedException) {}
            try {
                Log.w(TAG, "Reiniciando a central (reboot) a pedido do usuário.")
                // `sync` primeiro achata o cache de disco pendente, para o reboot não perder
                // gravações recentes (ex.: um tema acabado de instalar). Um só `sh -c` encadeia.
                shizukuService()?.newProcess(arrayOf("sh", "-c", "sync; reboot"), null, null)
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao disparar reboot da central", e)
            }
        }, "rc-reboot").start()
        return true
    }

    /**
     * Reinício PROFUNDO da central: desliga de verdade em vez de reiniciar o Android.
     *
     * A DIFERENÇA PARA [rebootHeadUnit]
     *
     * `reboot` reinicia o Android e mais nada: o SoC nunca perde energia, e o que está pendurado
     * nele — amplificador, sintonizador, transceptor CAN, o próprio MCU — continua no estado em
     * que estava. É por isso que um `reboot` não resolve os travamentos que só o botão Power
     * segurado resolve. Segurar o Power corta a energia do SoC e devolve depois; o equivalente
     * alcançável de dentro do Android é o desligamento, e a religada é o MCU que faz, porque com a
     * ignição ligada ele não deixa a central desligada.
     *
     * O QUE ESTÁ VERIFICADO E O QUE NÃO ESTÁ
     *
     * Que `svc power shutdown` desliga é padrão do Android (IPowerManager.shutdown pelo uid shell,
     * que tem REBOOT). Que ESTA central volte sozinha com a ignição ligada é INFERÊNCIA do
     * comportamento do MCU e não foi medido neste veículo — por isso o diálogo que chama isto diz,
     * com essas palavras, que se não voltar em ~1 min é só girar a chave. A ordem tenta `svc`
     * primeiro e `setprop sys.powerctl` depois, que é o mesmo caminho por baixo e serve de reserva
     * se o binário `svc` não existir nesta build.
     *
     * Não espera o processo: o sistema cai no meio.
     */
    fun deepRestartHeadUnit(delayMs: Long = 0): Boolean {
        if (shizukuService() == null) {
            Log.e(TAG, "Shizuku indisponível — reinício profundo não disparado.")
            return false
        }
        Thread({
            if (delayMs > 0) try { Thread.sleep(delayMs) } catch (_: InterruptedException) {}
            try {
                Log.w(TAG, "Reinício profundo da central (desligar + religar pelo MCU) a pedido do usuário.")
                shizukuService()?.newProcess(
                    arrayOf(
                        "sh",
                        "-c",
                        // `sync` achata o cache antes de a energia sair. `svc power shutdown`
                        // primeiro; se ele não existir ou falhar, a propriedade faz o mesmo.
                        "sync; svc power shutdown || setprop sys.powerctl shutdown"
                    ),
                    null,
                    null
                )
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao disparar o reinício profundo da central", e)
            }
        }, "rc-deep-restart").start()
        return true
    }

    private fun shellQuote(path: String): String = "'" + path.replace("'", "'\\''") + "'"

    /** Lista um diretório via `ls -la` root. Retorno best-effort — parsing de `ls` é heurístico. */
    fun listDir(path: String): List<FileEntry>? {
        val raw = execCaptureBytes(arrayOf("sh", "-c", "ls -la ${shellQuote(path)} 2>/dev/null"))
            ?: return null
        val text = String(raw, Charsets.UTF_8)
        val entries = mutableListOf<FileEntry>()
        for (line in text.lineSequence()) {
            if (line.isBlank() || line.startsWith("total ")) continue
            val parts = line.trim().split(Regex("\\s+"), limit = 9)
            if (parts.size < 9) continue
            val perms = parts[0]
            val name = parts[8]
            if (name == "." || name == "..") continue
            val size = parts[4].toLongOrNull() ?: 0L
            entries.add(FileEntry(name, perms.startsWith("d"), size))
        }
        return entries
    }

    fun readFileToOutput(path: String, out: OutputStream): Boolean =
        execStreamToOutput(arrayOf("cat", path), out, exigirSaidaZero = true)

    /**
     * Varre `roots` (recursivo) por arquivos de vídeo — usado quando nenhum dos caminhos
     * candidatos fixos de [DvrManager] bate: em vez de exigir uma nova build toda vez que o
     * caminho real de gravação do DVR nativo for diferente do palpite, procura de verdade no
     * armazenamento. `find` com `-iname` cobre as extensões mais comuns de dashcam automotiva,
     * incluindo stream elementar cru (.h264/.265) que alguns DVRs chineses gravam sem container.
     */
    fun findVideoFiles(roots: List<String> = listOf("/sdcard", "/storage/emulated/0", "/data/dvr")): List<String>? {
        val rootsExpr = roots.joinToString(" ") { shellQuote(it) }
        val nameExpr = listOf("*.mp4", "*.h264", "*.264", "*.h265", "*.265", "*.avi", "*.ts", "*.mkv")
            .joinToString(" -o ") { "-iname ${shellQuote(it)}" }
        val script = "find $rootsExpr -type f \\( $nameExpr \\) 2>/dev/null | head -300"
        val raw = execCaptureBytes(arrayOf("sh", "-c", script)) ?: return null
        return String(raw, Charsets.UTF_8).lineSequence().filter { it.isNotBlank() }.toList()
    }

    // `mkdir -p` do diretório-pai primeiro: usado pelo editor "Criação de Temas" do instalador
    // pra empurrar uma pasta de tema nova (theme.xml/app.html/PNGs) que ainda não existe em
    // themesDir — sem isso, o primeiro `cat >` de uma pasta nova falhava com "No such file or
    // directory" (o antigo formato só era usado para escrever em arquivos com pasta já existente,
    // ex.: /sdcard, nunca para criar uma pasta nova em getFilesDir()).
    //
    // Depois de escrever, reajusta dono (uid:gid) e contexto SELinux de tudo que foi criado pra
    // bater com o primeiro diretório ancestral que já existia antes desta chamada — sem isso, o
    // arquivo fica com dono `shell`/`root` (o processo Shizuku que executou o `cat`), e o próprio
    // app (rodando com seu UID normal) nunca consegue ler de volta o que acabou de gravar dentro
    // de getFilesDir() (ex.: um tema enviado pelo editor do PC: upload reporta sucesso, mas o
    // arquivo fica invisível pro ThemeManager porque `File.exists()`/`listFiles()` falham
    // silenciosamente sob EACCES/negação do SELinux).
    fun writeFileFromInput(path: String, input: InputStream): Boolean {
        val quoted = shellQuote(path)
        val script = "P=$quoted; D=\"\$(dirname \"\$P\")\"; A=\"\$D\"; " +
            "while [ ! -d \"\$A\" ] && [ \"\$A\" != \"/\" ]; do A=\"\$(dirname \"\$A\")\"; done; " +
            "mkdir -p \"\$D\" && cat > \"\$P\"; RC=\$?; " +
            "if [ \"\$A\" = \"\$D\" ]; then TARGET=\"\$P\"; else CUR=\"\$D\"; " +
            "while [ \"\$(dirname \"\$CUR\")\" != \"\$A\" ]; do CUR=\"\$(dirname \"\$CUR\")\"; done; TARGET=\"\$CUR\"; fi; " +
            "OWNER=\"\$(stat -c '%u:%g' \"\$A\" 2>/dev/null)\"; " +
            "[ -n \"\$OWNER\" ] && chown -R \"\$OWNER\" \"\$TARGET\" 2>/dev/null; " +
            "if command -v restorecon >/dev/null 2>&1; then restorecon -R \"\$TARGET\" 2>/dev/null; " +
            "else CTX=\"\$(stat -c '%C' \"\$A\" 2>/dev/null)\"; " +
            "[ -n \"\$CTX\" ] && [ \"\$CTX\" != \"?\" ] && [ \"\$CTX\" != \"%C\" ] && chcon -R \"\$CTX\" \"\$TARGET\" 2>/dev/null; fi; " +
            "exit \$RC"
        return execWithStdin(arrayOf("sh", "-c", script), input)
    }

    data class InstalledApp(val packageName: String, val apkDir: String, val isSystem: Boolean)

    /**
     * Lista TODOS os apps instalados (terceiros + sistema) e o diretório que contém seus APKs.
     * Usa o diretório (não o `base.apk` sozinho) porque apps com split APKs (config de
     * arquitetura/densidade — comum em apps modernos) têm `base.apk` + `split_*.apk` lado a lado
     * na mesma pasta; extrair só o base.apk produz um pacote incompleto que não reinstala direito.
     *
     * Antes rodava só `pm list packages -f -3` (só terceiros), o que deixava a lista de "extrair
     * um app específico" minúscula (geralmente ~4 apps nesta central) enquanto a lista de
     * desinstalar — que roda `-3` E `-s` — mostrava dezenas. Agora combina os dois, igual ao
     * fluxo de desinstalar, e marca `isSystem` pra quem precisar filtrar/avisar na UI.
     */
    fun listInstalledApps(): List<InstalledApp>? {
        fun parse(raw: ByteArray?, isSystem: Boolean): List<InstalledApp> {
            if (raw == null) return emptyList()
            val text = String(raw, Charsets.UTF_8)
            // Cada linha: "package:/data/app/.../base.apk=com.exemplo.pacote"
            return text.lineSequence()
                .mapNotNull { line ->
                    val withoutPrefix = line.removePrefix("package:").trim()
                    val eqIdx = withoutPrefix.lastIndexOf('=')
                    if (eqIdx == -1) return@mapNotNull null
                    val apkPath = withoutPrefix.substring(0, eqIdx)
                    val pkg = withoutPrefix.substring(eqIdx + 1)
                    val dir = apkPath.substringBeforeLast('/', "")
                    if (pkg.isBlank() || dir.isBlank()) null else InstalledApp(pkg, dir, isSystem)
                }
                .toList()
        }

        val userRaw = execCaptureBytes(arrayOf("pm", "list", "packages", "-f", "-3")) ?: return null
        val systemRaw = execCaptureBytes(arrayOf("pm", "list", "packages", "-f", "-s"))
        return (parse(userRaw, false) + parse(systemRaw, true)).sortedBy { it.packageName }
    }

    /**
     * Tar (sem compressão) do diretório completo de todos os APKs de apps de terceiros instalados,
     * direto pro [out] — inclui splits, não só o base.apk (ver [listInstalledApps]). Fica
     * deliberadamente restrito a terceiros (filtra `isSystem`) — é o que a UI ("Extrair todos os
     * APKs... de cada app de terceiros") promete; extrair um app de sistema específico continua
     * possível via [streamSingleAppBackup]. Sem `-z`: o `tar` deste build (toybox/busybox) pode
     * não ter gzip embutido, e nesse caso `-z` falha na hora — como a resposta HTTP já é
     * "close-delimited" (sem Content-Length), essa falha vira só um arquivo minúsculo e ilegível
     * do lado do laptop, sem nenhum erro visível. Tar puro evita essa dependência por completo.
     */
    fun streamAppsBackup(out: OutputStream): Boolean {
        val apps = listInstalledApps()?.filter { !it.isSystem } ?: return false
        if (apps.isEmpty()) return false
        val dirs = apps.map { it.apkDir }.distinct()
        val quotedPaths = dirs.joinToString(" ") { shellQuote(it) }
        return execStreamToOutput(arrayOf("sh", "-c", "tar -cf - $quotedPaths 2>/dev/null"), out)
    }

    /** Diretório de APKs de um app específico — inclui apps de sistema, ao contrário de [streamAppsBackup]. */
    fun estimateAppBackupPaths(packageName: String): List<String>? =
        listInstalledApps()?.firstOrNull { it.packageName == packageName }?.let { listOf(it.apkDir) }

    fun estimateAppsBackupPaths(): List<String>? =
        listInstalledApps()?.filter { !it.isSystem }?.map { it.apkDir }?.distinct()

    /** Como [streamAppsBackup], mas só do diretório de um único pacote — pra extração individual. */
    fun streamSingleAppBackup(packageName: String, out: OutputStream): Boolean {
        val app = listInstalledApps()?.firstOrNull { it.packageName == packageName } ?: return false
        return execStreamToOutput(arrayOf("sh", "-c", "tar -cf - ${shellQuote(app.apkDir)} 2>/dev/null"), out)
    }

    /**
     * Partições reais candidatas para o backup "completo". Evita tarear `/` direto com
     * `--exclude` pra pseudo-filesystems (`/proc`, `/sys`, `/dev`) porque o `tar` deste build
     * (toybox/busybox) tem histórico de flags não suportadas falhando sem aviso (ver comentário
     * de `-z` acima) — listar as partições reais é mais lento de escrever mas não depende de
     * nenhuma flag além de `-c`/`-f`, que já sabemos que funcionam. `/sdcard` cobre o
     * armazenamento do usuário (fotos, DVR, downloads); as demais são as partições do sistema.
     * Cada uma só entra se existir nesta central — evita o `tar` falhar num caminho ausente.
     */
    private val FULL_BACKUP_CANDIDATE_PATHS = listOf(
        "/data", "/sdcard", "/system", "/vendor", "/product", "/odm", "/persist"
    )

    private fun pathExists(path: String): Boolean {
        val raw = execCaptureBytes(arrayOf("sh", "-c", "[ -e ${shellQuote(path)} ] && echo 1")) ?: return false
        return String(raw, Charsets.UTF_8).trim() == "1"
    }

    fun resolveFullBackupPaths(): List<String> = FULL_BACKUP_CANDIDATE_PATHS.filter(::pathExists)

    /**
     * Backup "completo" de verdade — todas as partições reais de [resolveFullBackupPaths], não só
     * `/data/app` + `/data/data`. Pode passar de dezenas de GB e demorar horas pelo Wi-Fi — é
     * literalmente tudo que dá pra copiar sem arriscar quebrar o `tar` em pseudo-filesystems.
     */
    fun streamFullBackup(out: OutputStream): Boolean {
        val paths = resolveFullBackupPaths()
        if (paths.isEmpty()) return false
        val quotedPaths = paths.joinToString(" ") { shellQuote(it) }
        return execStreamToOutput(arrayOf("sh", "-c", "tar -cf - $quotedPaths 2>/dev/null"), out)
    }

    /**
     * Soma o tamanho (bytes) de uma lista de caminhos via `du -sk`, usado só pra alimentar a barra
     * de progresso (total esperado) do lado do Mac antes de iniciar o `tar` — a resposta HTTP do
     * backup em si é "close-delimited" (sem Content-Length), então sem isso não haveria como saber
     * a porcentagem, só os bytes já baixados. `du -sk` (KB) em vez de `-sb` porque nem todo build
     * de `du` toybox/busybox suporta `-b`; a granularidade de KB é sobra suficiente pra uma barra
     * de progresso.
     */
    fun estimateSizeBytes(paths: List<String>): Long? {
        var total = 0L
        var any = false
        for (path in paths) {
            val raw = execCaptureBytes(arrayOf("sh", "-c", "du -sk ${shellQuote(path)} 2>/dev/null | awk '{print \$1}'")) ?: continue
            val kb = String(raw, Charsets.UTF_8).trim().toLongOrNull() ?: continue
            total += kb * 1024
            any = true
        }
        return if (any) total else null
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
