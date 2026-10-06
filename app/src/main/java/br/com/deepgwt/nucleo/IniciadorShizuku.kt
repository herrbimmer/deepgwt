// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.nucleo

import android.os.ParcelFileDescriptor
import android.os.RemoteException
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.Closeable
import java.io.InputStream

/**
 * Cria processos como usuário `shell` no servidor do Shizuku.
 *
 * Nesta versão da biblioteca a criação de processo da classe `Shizuku` não é pública; o caminho
 * é a interface do servidor ([IShizukuService]) sobre o binder que a biblioteca recebeu.
 */
internal object IniciadorShizuku : IniciadorDeProcesso {

    /** O binder existe e responde agora. Barato: há quem chame em laço esperando o Shizuku subir. */
    fun respondendo(): Boolean =
        try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }

    override fun iniciar(comando: Array<String>): ProcessoRemoto? {
        // Lido de novo a cada chamada: depois que o servidor reinicia, o binder antigo está morto.
        val binder = Shizuku.getBinder() ?: return null
        val remoto = try {
            IShizukuService.Stub.asInterface(binder).newProcess(comando, null, null)
        } catch (e: RemoteException) {
            // Inclui DeadObjectException. "Permission Denial" chega como SecurityException e não
            // passa sozinha, então segue como falha definitiva.
            throw FalhaPassageira(e)
        } ?: throw IllegalStateException("o Shizuku não devolveu o processo")
        return ProcessoShizuku(remoto)
    }
}

/**
 * As pontas do processo chegam como [ParcelFileDescriptor]. Guardamos a referência ao
 * [IRemoteProcess] até [liberar]: sem ela, o lado do servidor pode ser recolhido com o processo
 * ainda rodando.
 */
private class ProcessoShizuku(private val remoto: IRemoteProcess) : ProcessoRemoto {

    private val abertos = mutableListOf<Closeable>()

    override fun fecharEntrada() {
        // Sem isto o servidor do Shizuku fica com o cano da entrada aberto e vaza descritores.
        remoto.outputStream?.close()
    }

    override fun saida(): InputStream = abrir(remoto.inputStream, "saída padrão")

    override fun erro(): InputStream = abrir(remoto.errorStream, "saída de erro")

    override fun esperar(): Int = remoto.waitFor()

    override fun liberar() {
        val fechar = synchronized(abertos) { abertos.toList().also { abertos.clear() } }
        fechar.forEach { runCatching { it.close() } }
        // Depois que o processo saiu, destroy() não faz mal; antes, garante que ele não fica órfão.
        runCatching { remoto.destroy() }
    }

    private fun abrir(ponta: ParcelFileDescriptor?, nome: String): InputStream {
        val entrada = ParcelFileDescriptor.AutoCloseInputStream(
            ponta ?: throw IllegalStateException("o Shizuku não devolveu a $nome")
        )
        synchronized(abertos) { abertos += entrada }
        return entrada
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
