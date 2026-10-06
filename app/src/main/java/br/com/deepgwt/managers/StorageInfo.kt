// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.managers

import android.os.Environment
import android.os.StatFs
import android.system.Os
import android.util.Log
import java.io.File

/**
 * Quanto do armazenamento da central está ocupado e quanto sobra.
 *
 * Deliberadamente NÃO usa Shizuku: `StatFs` é uma chamada normal de app, então esta leitura
 * funciona na primeira abertura, antes de qualquer permissão ter sido concedida — que é
 * justamente quando alguém quer saber se ainda cabe um tema, um APK ou uma gravação do DVR.
 *
 * O mesmo resultado alimenta o card de "Informações" no carro e a rota `/storage` do
 * [GwLinkServer] (consumida pelo instalador no computador), para que os dois lados nunca mostrem
 * números diferentes do mesmo carro.
 */
object StorageInfo {
    private const val TAG = "StorageInfo"

    /**
     * @param freeBytes espaço que um app conseguiria de fato gravar (`availableBytes`), não o
     *   total livre do filesystem — parte do que o `df` chama de livre é reserva do sistema e o
     *   usuário nunca vê. Mostrar a reserva como disponível seria prometer espaço que não existe.
     */
    data class Volume(
        val label: String,
        val path: String,
        val totalBytes: Long,
        val freeBytes: Long,
        val removable: Boolean
    ) {
        val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0L)
    }

    // Os dois volumes que sempre existem numa central Android. Costumam ser a MESMA partição
    // física (a área compartilhada é uma visão emulada de /data), e é por isso que existe a
    // deduplicação em [collect] — listar o mesmo espaço duas vezes daria a impressão de ter o
    // dobro de armazenamento.
    private val FIXED_CANDIDATES: List<Pair<String, String>> =
            listOf(
                    "/data" to "Sistema e aplicativos",
                    Environment.getExternalStorageDirectory().absolutePath to
                            "Armazenamento interno"
            )

    private val REMOVABLE_FSTYPES =
            setOf("vfat", "exfat", "ntfs", "fuseblk", "ext4", "f2fs", "sdcardfs", "fuse")

    fun volumes(): List<Volume> = collect()

    /** Soma de todos os volumes distintos, para o resumo de uma linha só. */
    fun totals(volumes: List<Volume> = volumes()): Pair<Long, Long> {
        var total = 0L
        var free = 0L
        volumes.forEach {
            total += it.totalBytes
            free += it.freeBytes
        }
        return total to free
    }

    private fun collect(): List<Volume> {
        val result = mutableListOf<Volume>()
        FIXED_CANDIDATES.forEach { (path, label) -> addIfNew(result, path, label, false) }
        discoverRemovable().forEach { (path, label) -> addIfNew(result, path, label, true) }
        return result
    }

    /**
     * Acrescenta o volume se ele já não estiver na lista sob outro caminho.
     *
     * Dois testes, porque um só não cobre: `st_dev` separa partições de verdade, mas a área
     * compartilhada é montada por sdcardfs/fuse e ganha um `st_dev` próprio apesar de ser o mesmo
     * espaço de /data. Nesse caso o que denuncia é o `statfs`: mesmo tamanho total e o mesmo
     * livre (a menos de ruído de gravação entre as duas leituras).
     */
    private fun addIfNew(
            into: MutableList<Volume>,
            path: String,
            label: String,
            removable: Boolean
    ) {
        val volume = read(path, label, removable) ?: return
        if (volume.totalBytes <= 0L) return
        val deviceId = deviceIdOf(path)
        val duplicate =
                into.any { existing ->
                    val sameDevice = deviceId != null && deviceIdOf(existing.path) == deviceId
                    val sameFilesystem =
                            existing.totalBytes == volume.totalBytes &&
                                    Math.abs(existing.freeBytes - volume.freeBytes) < DEDUPE_SLACK
                    sameDevice || sameFilesystem
                }
        if (!duplicate) into.add(volume)
    }

    private fun read(path: String, label: String, removable: Boolean): Volume? =
            runCatching {
                        val stat = StatFs(path)
                        Volume(
                                label = label,
                                path = path,
                                totalBytes = stat.totalBytes,
                                freeBytes = stat.availableBytes,
                                removable = removable
                        )
                    }
                    .onFailure { Log.d(TAG, "Sem statfs para $path: ${it.message}") }
                    .getOrNull()

    private fun deviceIdOf(path: String): Long? =
            runCatching { Os.stat(path).st_dev }.getOrNull()

    /**
     * Procura cartão/pendrive lendo `/proc/mounts` em vez de chutar caminhos: o ponto de montagem
     * de mídia removível recebe o UUID do volume no nome, então nenhuma lista fixa acertaria.
     * `/proc/mounts` é legível por qualquer app — de novo, nada aqui depende de Shizuku.
     */
    private fun discoverRemovable(): List<Pair<String, String>> {
        val mounts =
                runCatching { File("/proc/mounts").readLines() }
                        .onFailure { Log.d(TAG, "Não foi possível ler /proc/mounts: ${it.message}") }
                        .getOrNull()
                        ?: return emptyList()

        val found = mutableListOf<Pair<String, String>>()
        for (line in mounts) {
            val parts = line.split(" ")
            if (parts.size < 3) continue
            val mountPoint = parts[1].replace("\\040", " ")
            val fsType = parts[2]
            if (fsType !in REMOVABLE_FSTYPES) continue
            if (!isRemovableMountPoint(mountPoint)) continue
            found.add(mountPoint to labelForRemovable(mountPoint))
        }
        return found
    }

    private fun isRemovableMountPoint(mountPoint: String): Boolean {
        val underStorage = mountPoint.startsWith("/storage/")
        val underMediaRw = mountPoint.startsWith("/mnt/media_rw/")
        if (!underStorage && !underMediaRw) return false
        val name = mountPoint.substringAfterLast('/')
        // "emulated" e "self" são a área compartilhada, já coberta pelos candidatos fixos.
        return name.isNotBlank() && name != "emulated" && name != "self" && name != "container"
    }

    private fun labelForRemovable(mountPoint: String): String {
        val name = mountPoint.substringAfterLast('/')
        // Ponto de montagem de mídia removível costuma ser o UUID do volume (ex.: "1A2B-3C4D").
        // Esse nome não diz nada para quem está lendo, então ele vira detalhe entre parênteses.
        return "Cartão ou pendrive ($name)"
    }

    /** 8 MiB de folga: o carro continua gravando log entre uma leitura de `statfs` e a outra. */
    private const val DEDUPE_SLACK = 8L * 1024 * 1024

    /** Formatação curta em GB/MB, a mesma usada no card do carro. */
    fun format(bytes: Long): String {
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            bytes >= gb -> String.format("%.1f GB", bytes / gb)
            bytes >= mb -> String.format("%.0f MB", bytes / mb)
            bytes >= kb -> String.format("%.0f KB", bytes / kb)
            else -> "$bytes B"
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
