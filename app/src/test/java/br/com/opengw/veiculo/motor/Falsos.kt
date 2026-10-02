package br.com.opengw.veiculo.motor

import br.com.opengw.veiculo.Veiculo
import java.util.Collections
import java.util.PriorityQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Linha do motor com relógio virtual: nada roda até o teste [avancar]. Segura entre threads. */
internal class AgendaFalsa(private val eventos: MutableList<String>) : Agenda {

    private class Item(val quando: Long, val ordem: Long, val periodo: Long?, val cancelado: AtomicBoolean, val tarefa: () -> Unit)

    private val trava = Any()
    private var instante = 1_000L
    private var contador = 0L
    private val fila = PriorityQueue<Item>(compareBy<Item>({ it.quando }, { it.ordem }))
    val pausas = CopyOnWriteArrayList<Long>()

    override fun agora(): Long = synchronized(trava) { instante }

    override fun executar(tarefa: () -> Unit) {
        agendar(0, tarefa)
    }

    override fun agendar(atrasoMs: Long, tarefa: () -> Unit): Cancelavel = enfileirar(atrasoMs, null, tarefa)

    override fun repetir(primeiraMs: Long, periodoMs: Long, tarefa: () -> Unit): Cancelavel = enfileirar(primeiraMs, periodoMs, tarefa)

    override fun pausar(ms: Long) {
        pausas += ms
        eventos += "pausa:$ms"
        synchronized(trava) { instante += ms }
    }

    private fun enfileirar(atraso: Long, periodo: Long?, tarefa: () -> Unit): Cancelavel {
        val cancelado = AtomicBoolean(false)
        synchronized(trava) { fila.add(Item(instante + atraso, contador++, periodo, cancelado, tarefa)) }
        return Cancelavel { cancelado.set(true) }
    }

    /** Avança o relógio, rodando em ordem tudo o que vence até lá. */
    fun avancar(ms: Long) {
        val alvo = synchronized(trava) { instante + ms }
        while (true) {
            val item = synchronized(trava) {
                val proximo = fila.peek()
                if (proximo == null || proximo.quando > alvo) {
                    null
                } else {
                    fila.poll()
                    if (proximo.quando > instante) instante = proximo.quando
                    proximo
                }
            } ?: break
            if (item.cancelado.get()) continue
            item.tarefa()
            if (item.periodo != null && !item.cancelado.get()) {
                synchronized(trava) {
                    fila.add(Item(instante + item.periodo, contador++, item.periodo, item.cancelado, item.tarefa))
                }
            }
        }
        synchronized(trava) { if (instante < alvo) instante = alvo }
    }

    fun rodarPendentes() = avancar(0)

    fun pendentes(): Int = synchronized(trava) { fila.count { !it.cancelado.get() } }
}

/** Uma alça falsa do serviço de controle. Cada chamada vira um evento "<nome>:<operação>". */
internal class CanalFalso(val nome: String, private val eventos: MutableList<String>) : CanalDeControle {

    /** O que "a central" responde (ausente = sem valor). */
    val valores = ConcurrentHashMap<String, String>()

    @Volatile var respondePing = true
    @Volatile var vivoAgora = true

    /** Operações que lançam: pedir, buscar, buscarVarias, somarChaves, registrar, desregistrar, aviso. */
    val falharEm: MutableSet<String> = Collections.synchronizedSet(HashSet())

    /** Lotes maiores que isto lançam (null = nenhum). */
    @Volatile var maiorLoteAceito: Int? = null

    /** Lotes que lançam, pela ordem da chamada (1 = o primeiro). */
    val lotesQueFalham: MutableSet<Int> = Collections.synchronizedSet(HashSet())

    @Volatile var aoRegistrar: (() -> Unit)? = null
    @Volatile var aoMorrer: (() -> Unit)? = null

    val buscas = AtomicInteger()
    val lotes = AtomicInteger()
    val pedidos = CopyOnWriteArrayList<Triple<String, String, String>>()
    val chavesSomadas = CopyOnWriteArrayList<List<String>>()
    val lotesPedidos = CopyOnWriteArrayList<List<String>>()

    override fun responde(): Boolean = respondePing

    override fun vivo(): Boolean = vivoAgora

    override fun ligarAvisoDeMorte(aoMorrer: () -> Unit) {
        eventos += "$nome:aviso"
        if ("aviso" in falharEm) throw IllegalStateException("alça morta")
        this.aoMorrer = aoMorrer
    }

    override fun desligarAvisoDeMorte() {
        if (aoMorrer != null) eventos += "$nome:semAviso"
        aoMorrer = null
    }

    override fun pedir(acao: String, chave: String, valor: String) {
        eventos += "$nome:pedir:$chave=$valor"
        if ("pedir" in falharEm) throw SecurityException("sem permissão")
        pedidos += Triple(acao, chave, valor)
    }

    override fun buscar(chave: String): String? {
        buscas.incrementAndGet()
        eventos += "$nome:buscar:$chave"
        if ("buscar" in falharEm) throw IllegalStateException("morreu")
        return valores[chave]
    }

    override fun buscarVarias(chaves: Array<String>): Array<out String?>? {
        val numero = lotes.incrementAndGet()
        lotesPedidos += chaves.toList()
        eventos += "$nome:lote:${chaves.size}"
        if ("buscarVarias" in falharEm || numero in lotesQueFalham) throw IllegalStateException("lote falhou")
        maiorLoteAceito?.let { if (chaves.size > it) throw IllegalStateException("lote grande demais") }
        return Array(chaves.size) { valores[chaves[it]] }
    }

    override fun somarChaves(pacote: String, chaves: Array<String>) {
        eventos += "$nome:somar:${chaves.size}"
        if ("somarChaves" in falharEm) throw IllegalStateException("morreu")
        chavesSomadas += chaves.toList()
    }

    override fun registrarOuvinte(pacote: String) {
        eventos += "$nome:registrar:$pacote"
        if ("registrar" in falharEm) throw IllegalStateException("morreu")
        aoRegistrar?.invoke()
    }

    override fun desregistrarOuvinte(pacote: String) {
        eventos += "$nome:desregistrar:$pacote"
        if ("desregistrar" in falharEm) throw IllegalStateException("morreu")
    }

    /** A central morre: a alça para de responder e o aviso dispara. */
    fun morrer() {
        vivoAgora = false
        respondePing = false
        aoMorrer?.invoke()
    }
}

internal class CentralFalsa(private val eventos: MutableList<String>) : Central {

    @Volatile var shizuku = true

    /** Respostas do Shizuku em sequência, antes de cair em [shizuku]. */
    val respostasDoShizuku = java.util.concurrent.ConcurrentLinkedQueue<Boolean>()
    val pingsDoShizuku = AtomicInteger()

    @Volatile var servicoAusente = false
    val abertos = CopyOnWriteArrayList<CanalFalso>()

    /** Chamado com cada alça nova antes de devolvê-la (para o teste preparar). */
    @Volatile var aoAbrir: ((CanalFalso) -> Unit)? = null

    @Volatile var vozDisponivel = true
    @Volatile var vozViva = false
    val renovacoesDeVoz = AtomicInteger()
    @Volatile var aoMorrerAVoz: (() -> Unit)? = null

    /** Valores "da central" que cada alça nova já começa tendo. */
    val valores = ConcurrentHashMap<String, String>()

    override fun shizukuResponde(): Boolean {
        pingsDoShizuku.incrementAndGet()
        return respostasDoShizuku.poll() ?: shizuku
    }

    override fun abrirControle(): CanalDeControle? {
        eventos += "abrirControle"
        if (servicoAusente) return null
        val canal = CanalFalso("c${abertos.size + 1}", eventos)
        canal.valores.putAll(valores)
        aoAbrir?.invoke(canal)
        abertos += canal
        return canal
    }

    override fun renovarVoz(aoMorrer: () -> Unit): Boolean {
        renovacoesDeVoz.incrementAndGet()
        eventos += "voz"
        aoMorrerAVoz = aoMorrer
        vozViva = vozDisponivel
        return vozViva
    }

    override fun vozCompleta(): Boolean = vozViva

    val canal: CanalFalso get() = abertos.last()
}

internal class DifusorFalso(private val eventos: MutableList<String>) : Difusor {
    data class Envio(val acao: String, val chave: String?, val valor: String?, val soDoApp: Boolean)

    val envios = CopyOnWriteArrayList<Envio>()
    @Volatile var falhar = false

    override fun enviar(acao: String, chave: String?, valor: String?, soDoApp: Boolean) {
        eventos += "broadcast:$acao"
        if (falhar) throw IllegalStateException("sem contexto")
        envios += Envio(acao, chave, valor, soDoApp)
    }
}

internal class RegistroFalsoDoMotor : RegistroDoMotor {
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
}

/** Ouvinte que anota tudo o que recebe (e, se pedido, o que `ler` devolve dentro dele). */
internal class OuvinteAnotador(
    private val eventos: MutableList<String>,
    private val nome: String = "ouvinte",
    private val dentro: ((String, String?) -> Unit)? = null,
) : Veiculo.Ouvinte {
    val recebidos = CopyOnWriteArrayList<Pair<String, String?>>()

    override fun mudou(chave: String, valor: String?) {
        eventos += "$nome:$chave=$valor"
        recebidos += chave to valor
        dentro?.invoke(chave, valor)
    }
}

/** Tudo montado: o núcleo com falsos em volta e um registro único de eventos, na ordem. */
internal class Bancada {
    val eventos: MutableList<String> = Collections.synchronizedList(ArrayList())
    val agenda = AgendaFalsa(eventos)
    val central = CentralFalsa(eventos)
    val difusor = DifusorFalso(eventos)
    val registro = RegistroFalsoDoMotor()
    val comandos = CopyOnWriteArrayList<String>()
    @Volatile var saidaDoShell: (Array<String>) -> String = { "Package com.beantechs.hvac new state: ok" }
    @Volatile var chavesDaPessoa: Set<String> = emptySet()
    @Volatile var catalogo: List<String> = listOf("car.basic.vehicle_speed", "car.basic.engine_speed")
    val fontesLigadas = AtomicInteger()
    val fontesDesligadas = AtomicInteger()
    val diario = CopyOnWriteArrayList<Pair<String, Map<String, Any?>>>()

    val nucleo = NucleoDoMotor(
        central = central,
        agenda = agenda,
        difusor = difusor,
        registro = registro,
        shell = { comando ->
            val texto = comando.joinToString(" ")
            eventos += "shell:$texto"
            comandos += texto
            saidaDoShell(comando)
        },
        lerChavesDoUsuario = { chavesDaPessoa },
        catalogo = { catalogo },
        ligarFonteSimulada = LigarFonteSimulada {
            fontesLigadas.incrementAndGet()
            Cancelavel { fontesDesligadas.incrementAndGet() }
        },
    ).also { nucleo ->
        nucleo.diario = { evento, detalhes ->
            diario += evento to detalhes
            eventos += "diario:$evento"
        }
    }

    fun partir(): Boolean = nucleo.partir(PACOTE)

    /** Partida bem-sucedida e eventos zerados, para o teste olhar só o que vem depois. */
    fun partirLimpo(): CanalFalso {
        check(partir()) { "a partida devia dar certo" }
        agenda.rodarPendentes()
        eventos.clear()
        diario.clear()
        comandos.clear() // o `pm enable` do clima na partida não é do assunto dos testes
        agenda.pausas.clear()
        difusor.envios.clear()
        return central.canal
    }

    fun eventosDiario(): List<String> = diario.map { it.first }

    companion object {
        const val PACOTE = "br.com.teste.app"
    }
}
