// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.monitores

import java.util.PriorityQueue

/** Relógio e principal falsos: o tempo só anda quando o teste manda. */
internal class AgendaFalsa(inicioMs: Long = 10_000L) : AgendaDoMonitor {

    private class Tarefa(val quando: Long, val ordem: Long, val tarefa: Runnable)

    private var agora = inicioMs
    private var contador = 0L
    private val fila = PriorityQueue<Tarefa>(compareBy<Tarefa>({ it.quando }, { it.ordem }))

    var naPrincipal = true

    @Synchronized
    override fun agoraMs(): Long = agora

    override fun estaNaPrincipal(): Boolean = naPrincipal

    @Synchronized
    override fun postar(tarefa: Runnable) {
        fila.add(Tarefa(agora, contador++, tarefa))
    }

    @Synchronized
    override fun postarComAtraso(atrasoMs: Long, tarefa: Runnable) {
        fila.add(Tarefa(agora + atrasoMs, contador++, tarefa))
    }

    @Synchronized
    override fun cancelar(tarefa: Runnable) {
        fila.removeAll { it.tarefa === tarefa }
    }

    /** Quantas tarefas esperam (qualquer horário). */
    fun pendentes(): Int = fila.size

    /** Anda o relógio até [alvoMs], rodando na ordem o que vence no caminho (e em [alvoMs]). */
    fun avancarPara(alvoMs: Long) {
        while (true) {
            val proxima = fila.peek() ?: break
            if (proxima.quando > alvoMs) break
            fila.poll()
            if (proxima.quando > agora) agora = proxima.quando
            proxima.tarefa.run()
        }
        if (alvoMs > agora) agora = alvoMs
    }

    /** Roda o que já venceu, sem mexer no relógio. */
    fun rodarAgora() = avancarPara(agora)

    /** Relógio andando para trás (só muda o valor; não roda nada). */
    fun recuarPara(alvoMs: Long) {
        agora = alvoMs
    }
}

/** "Segundo plano" falso: as tarefas esperam o teste rodá-las, na ordem que ele quiser. */
internal class SegundoPlanoFalso {
    private val tarefas = ArrayList<() -> Unit>()

    fun postar(tarefa: () -> Unit) {
        tarefas += tarefa
    }

    fun quantas(): Int = tarefas.size

    /** Roda a tarefa [indice] (a ordem de chegada) e a tira da lista. */
    fun rodar(indice: Int) {
        tarefas.removeAt(indice)()
    }

    fun rodarTodas() {
        while (tarefas.isNotEmpty()) tarefas.removeAt(0)()
    }
}

internal class ServicoFalso : ServicoDoCarPlay {
    override var vivo: Boolean = true
    val transacoes: MutableList<String> = java.util.Collections.synchronizedList(ArrayList())
    var resultado: Boolean = true

    override fun iniciarAvisos(): Boolean {
        transacoes += "assinar"
        return resultado
    }

    override fun pararAvisos(): Boolean {
        transacoes += "cancelar"
        return resultado
    }

    override fun enviarTecla(tecla: Int, acao: Int): Boolean {
        transacoes += "tecla $tecla $acao"
        return resultado
    }

    override fun definirPosicao(posicaoMs: Long): Boolean {
        transacoes += "posicao $posicaoMs"
        return resultado
    }
}

internal class LigacaoFalsa : LigacaoComOServico {
    var ligacoes = 0
    var desfeitas = 0
    var resultado = true

    override fun ligar(): Boolean {
        ligacoes++
        return resultado
    }

    override fun desfazer() {
        desfeitas++
    }
}

/** Um `Parcel` falso: escreve e lê itens tipados, e confere o tipo na leitura (a ordem importa). */
internal class ParcelFalso : LeitorDeParcel, EscritorDeParcel {

    data class Item(val tipo: Char, val valor: Any?)

    val itens = ArrayDeque<Item>()

    fun restantes(): Int = itens.size

    private fun ler(tipo: Char): Any? {
        val item = itens.removeFirstOrNull() ?: throw AssertionError("leitura além do que foi escrito (esperava $tipo)")
        if (item.tipo != tipo) throw AssertionError("esperava $tipo, veio ${item.tipo} (${item.valor})")
        return item.valor
    }

    override fun escreverString(valor: String?) {
        itens += Item('S', valor)
    }

    override fun escreverInt(valor: Int) {
        itens += Item('I', valor)
    }

    override fun escreverLong(valor: Long) {
        itens += Item('L', valor)
    }

    override fun escreverByte(valor: Byte) {
        itens += Item('B', valor)
    }

    override fun escreverMapa(valor: Map<Int, Int>) {
        itens += Item('M', LinkedHashMap(valor))
    }

    fun escreverBytes(valor: ByteArray) {
        itens += Item('Y', valor.copyOf())
    }

    override fun lerString(): String? = ler('S') as String?

    override fun lerInt(): Int = ler('I') as Int

    override fun lerLong(): Long = ler('L') as Long

    override fun lerByte(): Byte = ler('B') as Byte

    @Suppress("UNCHECKED_CAST")
    override fun lerMapa(): Map<Int, Int> = ler('M') as Map<Int, Int>

    override fun lerBytes(tamanho: Int): ByteArray? {
        val valor = ler('Y') as ByteArray
        if (valor.size != tamanho) throw AssertionError("tamanho do vetor: ${valor.size} != $tamanho")
        return valor
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
