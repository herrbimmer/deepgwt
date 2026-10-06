// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.rede

import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/** Armazém de chaves em memória. */
internal class ArmazemFalso(inicial: Map<String, Any> = emptyMap()) : ArmazemDeChaves {
    val valores = java.util.concurrent.ConcurrentHashMap<String, Any>(inicial)
    val leituras = CopyOnWriteArrayList<String>()

    override fun lerBoolean(chave: String, padrao: Boolean): Boolean {
        leituras += chave
        return valores[chave] as? Boolean ?: padrao
    }

    override fun lerInt(chave: String, padrao: Int): Int {
        leituras += chave
        return valores[chave] as? Int ?: padrao
    }

    override fun lerLong(chave: String, padrao: Long): Long {
        leituras += chave
        return valores[chave] as? Long ?: padrao
    }

    override fun lerString(chave: String, padrao: String): String {
        leituras += chave
        return valores[chave] as? String ?: padrao
    }

    override fun gravarBoolean(chave: String, valor: Boolean) {
        valores[chave] = valor
    }

    override fun gravarVarios(valores: Map<String, Any>) {
        this.valores.putAll(valores)
    }
}

internal class RegistroFalso : RegistroDaRede {
    val infos = CopyOnWriteArrayList<String>()
    val avisos = CopyOnWriteArrayList<String>()
    val erros = CopyOnWriteArrayList<String>()

    override fun info(mensagem: String) {
        infos += mensagem
    }

    override fun aviso(mensagem: String) {
        avisos += mensagem
    }

    override fun erro(mensagem: String, causa: Throwable?) {
        erros += mensagem
    }
}

internal class RelogioFalso(var agoraMs: Long = 0, var monotonicoMs: Long = 0) : RelogioDaRede {
    override fun agoraEmMs() = agoraMs

    override fun monotonicoEmMs() = monotonicoMs
}

/** Executor de shell que grava cada comando e responde pela função [resposta]. */
internal class ShellFalso(var resposta: (String) -> String = { "" }) : ExecutorDeShell {
    val comandos = CopyOnWriteArrayList<String>()

    override fun executar(comando: Array<String>): String {
        val texto = comando.joinToString(" ")
        comandos += texto
        return resposta(texto)
    }
}

/** Agendador de teste: nada roda sozinho; [rodarFila] roda o que está pendente, na ordem. */
internal class AgendadorFalso : AgendadorDeFundo {
    val fila = ArrayList<Runnable>()
    val agendadas = ArrayList<Pair<Long, Runnable>>()

    override fun executar(tarefa: Runnable) {
        fila += tarefa
    }

    override fun depois(atrasoMs: Long, tarefa: Runnable) {
        agendadas += atrasoMs to tarefa
    }

    override fun cancelar(tarefa: Runnable) {
        agendadas.removeAll { it.second === tarefa }
        fila.removeAll { it === tarefa }
    }

    fun rodarFila() {
        while (fila.isNotEmpty()) fila.removeAt(0).run()
    }

    /** Dispara a primeira tarefa agendada (sem esperar o tempo). */
    fun dispararAgendada(): Boolean {
        if (agendadas.isEmpty()) return false
        agendadas.removeAt(0).second.run()
        return true
    }
}

/** Roda comandos de verdade com `sh`, com um diretório de comandos falsos no início do PATH. */
internal class ExecutorReal(private val bin: File?, private val extra: Map<String, String> = emptyMap()) : ExecutorDeShell {
    val comandos = CopyOnWriteArrayList<String>()

    override fun executar(comando: Array<String>): String {
        comandos += comando.joinToString(" ")
        val construtor = ProcessBuilder(*comando).redirectErrorStream(false)
        construtor.environment()["PATH"] = listOfNotNull(bin?.absolutePath, "/usr/bin", "/bin", "/usr/sbin", "/sbin")
            .joinToString(":")
        construtor.environment().putAll(extra)
        construtor.redirectError(ProcessBuilder.Redirect.to(File("/dev/null")))
        val processo = construtor.start()
        processo.outputStream.close()
        val saida = processo.inputStream.bufferedReader().readText()
        processo.waitFor(30, TimeUnit.SECONDS)
        return saida.trim()
    }
}

/** Cria um comando falso executável. */
internal fun comandoFalso(bin: File, nome: String, corpo: String) {
    val arquivo = File(bin, nome)
    arquivo.writeText("#!/bin/sh\n$corpo\n")
    arquivo.setExecutable(true)
}

internal fun pastaTemporaria(prefixo: String): File {
    val base = File(System.getProperty("java.io.tmpdir"), "deepgwt-rede-teste")
    base.mkdirs()
    return java.nio.file.Files.createTempDirectory(base.toPath(), prefixo).toFile()
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
