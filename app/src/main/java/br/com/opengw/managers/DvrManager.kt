package br.com.opengw.managers

import br.com.opengw.nucleo.OpenGw
import br.com.opengw.veiculo.Veiculo
import android.content.Context
import androidx.core.content.edit
import br.com.opengw.veiculo.Sinais
import br.com.opengw.nucleo.Preferencias
import java.io.OutputStream

/**
 * Puxa as gravações do DVR nativo do carro (subsistema sys.dvr.*) usando os mesmos
 * primitivos de arquivo do canal Shizuku já usados pelo GwLinkServer ([RemoteControlOps]).
 *
 * O caminho de armazenamento não vem de nenhuma chave do dicionário de ~800 chaves, então em vez
 * de cravar um palpite no código (que exigiria uma nova build pra corrigir se estiver errado),
 * [detectStoragePath] testa uma lista de candidatos comuns via Shizuku e persiste o primeiro que
 * realmente existir e tiver arquivo dentro. Uma vez confirmado, fica salvo em SharedPreferences.
 */
object DvrManager {
    private val CANDIDATE_PATHS = listOf(
        "/sdcard/DVR",
        "/sdcard/dvr",
        "/sdcard/Movies/DVR",
        "/storage/emulated/0/DVR",
        "/storage/emulated/0/DCIM/DVR",
        "/storage/emulated/0/Video/dvr",
        "/storage/emulated/0/dvr",
        "/data/dvr",
    )

    private val prefs by lazy {
        OpenGw.contextoProtegido.getSharedPreferences("opengw_prefs", Context.MODE_PRIVATE)
    }

    var storagePath: String? = null
        get() = field ?: prefs.getString(Preferencias.DVR_STORAGE_PATH.chave, null)
        set(value) {
            field = value
            prefs.edit { putString(Preferencias.DVR_STORAGE_PATH.chave, value) }
        }

    /**
     * Testa os caminhos candidatos e adota o primeiro que existir e contiver ao menos um
     * arquivo. Retorna o caminho escolhido, ou null se nenhum candidato bateu (nesse caso a
     * pessoa ainda pode digitar o caminho manualmente na tela de DVR).
     */
    fun detectStoragePath(): String? {
        for (candidate in CANDIDATE_PATHS) {
            val entries = RemoteControlOps.listDir(candidate)
            if (!entries.isNullOrEmpty() && entries.any { !it.isDir }) {
                storagePath = candidate
                return candidate
            }
        }
        return null
    }

    /**
     * Fallback para quando nenhum [CANDIDATE_PATHS] bate: varre o armazenamento de verdade por
     * arquivos de vídeo ([RemoteControlOps.findVideoFiles]) e adota a pasta que contém mais
     * arquivos encontrados. Mais lento que [detectStoragePath] (find recursivo em vez de um
     * `ls` por candidato), por isso fica como segunda tentativa, não a primeira.
     */
    fun detectStoragePathDeep(): String? {
        val files = RemoteControlOps.findVideoFiles() ?: return null
        val byDir = files.mapNotNull { path ->
            val idx = path.lastIndexOf('/')
            if (idx <= 0) null else path.substring(0, idx)
        }.groupingBy { it }.eachCount()
        val best = byDir.maxByOrNull { it.value }?.key ?: return null
        storagePath = best
        return best
    }

    fun listRecordings(): List<RemoteControlOps.FileEntry>? {
        val path = storagePath ?: return null
        return RemoteControlOps.listDir(path)?.filter { !it.isDir }
    }

    fun pullRecording(fileName: String, out: OutputStream): Boolean {
        val path = storagePath ?: return false
        return RemoteControlOps.readFileToOutput("$path/$fileName", out)
    }

    fun setRemoteRecordingEnabled(enabled: Boolean) {
        Veiculo.escrever(
            Sinais.SYS_DVR_REMOTE_SWITCH.chave,
            if (enabled) "1" else "0"
        )
    }
}
