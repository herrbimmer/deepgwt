// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.painel

import br.com.deepgwt.nucleo.Preferencia
import br.com.deepgwt.veiculo.ProjecaoNasTelas
import java.util.PriorityQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.RejectedExecutionException

/** Relógio que só anda quando o teste manda. */
internal class RelogioFalso(var instante: Long = 10_000L) : Relogio {
    override fun agora(): Long = instante

    fun andar(ms: Long) {
        instante += ms
    }
}

/** Linha do painel com o relógio virtual: nada roda até o teste [avancar]. */
internal class LinhaFalsa(private val relogio: RelogioFalso, val nome: String = "linha") : LinhaDoPainel {

    private class Item(val quando: Long, val ordem: Long, val tarefa: () -> Unit)

    private val fila = PriorityQueue(compareBy<Item>({ it.quando }, { it.ordem }))
    private var contador = 0L

    var encerrada = false
        private set

    override fun executar(tarefa: () -> Unit) = agendar(0, tarefa)

    override fun agendar(atrasoMs: Long, tarefa: () -> Unit) {
        if (encerrada) throw RejectedExecutionException("$nome encerrada")
        fila.add(Item(relogio.instante + atrasoMs, contador++, tarefa))
    }

    override fun encerrar() {
        encerrada = true
        fila.clear()
    }

    /** Avança o relógio, rodando em ordem tudo o que vence até lá (inclusive o que for agendado no caminho). */
    fun avancar(ms: Long) {
        val alvo = relogio.instante + ms
        while (true) {
            val proximo = fila.peek() ?: break
            if (proximo.quando > alvo) break
            fila.poll()
            if (proximo.quando > relogio.instante) relogio.instante = proximo.quando
            proximo.tarefa()
        }
        if (relogio.instante < alvo) relogio.instante = alvo
    }

    fun rodarPendentes() = avancar(0)

    val pendentes: Int get() = fila.size
}

internal class RegistroFalso : RegistroDoPainel {
    val infos = CopyOnWriteArrayList<String>()
    val avisos = CopyOnWriteArrayList<String>()
    val erros = CopyOnWriteArrayList<String>()

    override fun info(mensagem: String) {
        infos += mensagem
    }

    override fun aviso(mensagem: String, causa: Throwable?) {
        avisos += mensagem
    }

    override fun erro(mensagem: String, causa: Throwable?) {
        erros += mensagem
    }

    val todas: List<String> get() = infos + avisos + erros
}

/** Preferências em memória; ausente = padrão. */
internal class PreferenciasFalsas : LeitorDePreferencias {
    val booleanos = HashMap<String, Boolean>()
    val textos = HashMap<String, String>()

    override fun booleano(chave: Preferencia, padrao: Boolean): Boolean = booleanos[chave.chave] ?: padrao

    override fun texto(chave: Preferencia, padrao: String): String = textos[chave.chave] ?: padrao

    operator fun set(chave: Preferencia, valor: Boolean) {
        booleanos[chave.chave] = valor
    }

    operator fun set(chave: Preferencia, valor: String) {
        textos[chave.chave] = valor
    }
}

/** Canal do painel que anota cada envio como "id=valor" em [eventos]. */
internal class CanalFalso(private val eventos: MutableList<String>) : CanalDoPainel {
    @Volatile var falhar = false

    override fun enviar(id: Int, valor: Int) {
        if (falhar) throw IllegalStateException("painel recusou")
        eventos += "$id=$valor"
    }
}

/** Projeção configurável; cada chamada que importa vira um evento em [eventos]. */
internal class ProjecaoFalsa(private val eventos: MutableList<String>) : ProjecaoNasTelas {
    @Volatile var proteger = false
    @Volatile var trataMidia = false
    @Volatile var trataTecla = false
    @Volatile var lancar = false
    val teclasComDiagnostico = HashSet<Int>()
    val mensagensComDiagnostico = HashSet<Int>()

    override fun androidAutoNoDisplay(display: Int) = false
    override fun carPlayNoDisplay(display: Int) = false
    override fun guardarCarPlayNoPainel(motivo: String) {}
    override fun guardarAndroidAutoNoPainelAposJanelaNativa(motivo: String) {}
    override fun pulsoDoAndroidAutoDuranteJanelaNativa(motivo: String) {}
    override fun pulsoDoAndroidAutoAoSairDaJanelaNativa(motivo: String) {}

    override fun protegerCartaoDoArNaTransicao(): Boolean {
        if (lancar) throw IllegalStateException("projeção quebrada")
        return proteger
    }

    override fun teclaDoVolante(tecla: Int, acao: Int): Boolean {
        eventos += "aa:$tecla/$acao"
        if (lancar) throw IllegalStateException("projeção quebrada")
        return trataTecla
    }

    override fun comandoDeMidiaDoPainel(comando: Int): Boolean {
        eventos += "aa-midia:$comando"
        if (lancar) throw IllegalStateException("projeção quebrada")
        return trataMidia
    }

    override fun teclaMereceDiagnostico(tecla: Int): Boolean {
        eventos += "diag:$tecla"
        return tecla in teclasComDiagnostico
    }

    override fun mensagemDoPainelMereceDiagnostico(mensagem: Int): Boolean = mensagem in mensagensComDiagnostico
}

internal class MenuAntigoFalso : MenuAntigoDoPainel {
    @Volatile var cartao = -1
    val teclas = CopyOnWriteArrayList<String>()

    override fun cartaoAtual(): Int = cartao

    override fun tecla(nome: String) {
        teclas += nome
    }
}

/**
 * O núcleo do painel montado com falsos: barramento de verdade (linha = a do núcleo), um ouvinte que
 * guarda tudo o que chega e um diário em memória.
 */
internal class BancadaDoPainel {
    val relogio = RelogioFalso()
    val registro = RegistroFalso()
    val preferencias = PreferenciasFalsas()
    val chamadas = CopyOnWriteArrayList<String>()
    val canal = CanalFalso(chamadas)
    val projecao = ProjecaoFalsa(chamadas)
    val menuAntigo = MenuAntigoFalso()
    val diario = CopyOnWriteArrayList<Pair<String, Map<String, Any?>>>()
    val linha = LinhaFalsa(relogio)

    lateinit var nucleo: NucleoDoServicoDoPainel
    val barramento = BarramentoDoPainel(registro, relogio) { nucleo.linha }

    /** Eventos que chegaram ao ouvinte do barramento, na ordem. */
    val recebidos = CopyOnWriteArrayList<EventoDoPainel>()

    init {
        nucleo = NucleoDoServicoDoPainel(relogio, registro, { e, d -> diario += e to d }, preferencias, barramento)
        nucleo.projecao = projecao
        nucleo.menuAntigo = menuAntigo
        barramento.observar { recebidos += it }
    }

    fun comLinha(): BancadaDoPainel = apply { nucleo.trocarLinha(linha) }

    fun comCanal(): BancadaDoPainel = apply { nucleo.canal = canal }

    fun eventosDoDiario(): List<String> = diario.map { it.first }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
