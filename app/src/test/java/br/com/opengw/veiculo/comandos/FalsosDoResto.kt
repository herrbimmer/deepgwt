package br.com.opengw.veiculo.comandos

import br.com.opengw.veiculo.ProjecaoNasTelas
import br.com.opengw.veiculo.motor.AgendaFalsa
import br.com.opengw.veiculo.motor.RegistroFalsoDoMotor
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/*
 * Falsos do resto do motor (3.3b, 3.3c, 3.3e), escritos à mão. Cada um anota o que recebeu num
 * registro de eventos comum, na ordem.
 */

/** O carro: cache (ler/espiar), respostas de `lerDoCarro` e escritas anotadas. */
internal class CarroFalso(private val eventos: MutableList<String>) : CarroDoMotor {
    @Volatile override var controleVivo: Boolean = true
    @Volatile override var simulador: Boolean = false

    val cache = ConcurrentHashMap<String, String>()
    val doCarro = ConcurrentHashMap<String, String>()

    /** Respostas em sequência de `lerDoCarro` (antes de cair em [doCarro]); "null" textual = null. */
    val sequencias = ConcurrentHashMap<String, MutableList<String?>>()

    val escritas = CopyOnWriteArrayList<Pair<String, String>>()
    val leituras = CopyOnWriteArrayList<String>()
    val falharAoEscrever: MutableSet<String> = Collections.synchronizedSet(HashSet())

    fun responder(chave: String, vararg respostas: String?) {
        sequencias[chave] = Collections.synchronizedList(respostas.toMutableList())
    }

    override fun ler(chave: String): String? = cache[chave]

    override fun lerDoCarro(chave: String): String? {
        leituras += chave
        val fila = sequencias[chave]
        if (fila != null && fila.isNotEmpty()) return fila.removeAt(0)
        return doCarro[chave]
    }

    override fun espiar(chave: String): String? = cache[chave]

    override fun escrever(chave: String, valor: String) {
        eventos += "escrever:$chave=$valor"
        if (chave in falharAoEscrever) throw IllegalStateException("escrita recusada")
        escritas += chave to valor
    }

    fun escritasDe(chave: String): List<String> = escritas.filter { it.first == chave }.map { it.second }
}

/** As alças da voz. */
internal class VozFalsa(private val eventos: MutableList<String>) : AlcasDaVoz, VozDoVeiculo, VozDaCamera {
    @Volatile var semVeiculo = false
    @Volatile var semCamera = false

    @Volatile var janelas: IntArray? = intArrayOf(1, 1, 1, 1)
    @Volatile var nivelTeto = 0
    @Volatile var nivelCortina = 0

    /** Métodos que lançam: estadoDasJanelas, pedirJanela, nivelDoTeto, pedirTeto, nivelDaCortina, pedirCortina, pedirPorta, pedirAvm. */
    val falharEm: MutableSet<String> = Collections.synchronizedSet(HashSet())
    val pedidos = CopyOnWriteArrayList<String>()

    override fun veiculo(): VozDoVeiculo? = if (semVeiculo) null else this

    override fun camera(): VozDaCamera? = if (semCamera) null else this

    private fun talvezFalhar(metodo: String) {
        if (metodo in falharEm) throw IllegalStateException("$metodo morreu")
    }

    override fun estadoDasJanelas(): IntArray? {
        talvezFalhar("estadoDasJanelas")
        return janelas
    }

    override fun pedirJanela(janela: Int, estado: Int) {
        eventos += "janela:$janela=$estado"
        talvezFalhar("pedirJanela")
        pedidos += "janela:$janela=$estado"
    }

    override fun nivelDoTeto(): Int {
        talvezFalhar("nivelDoTeto")
        return nivelTeto
    }

    override fun pedirTeto(nivel: Int) {
        eventos += "teto:$nivel"
        talvezFalhar("pedirTeto")
        pedidos += "teto:$nivel"
    }

    override fun nivelDaCortina(): Int {
        talvezFalhar("nivelDaCortina")
        return nivelCortina
    }

    override fun pedirCortina(nivel: Int) {
        eventos += "cortina:$nivel"
        talvezFalhar("pedirCortina")
        pedidos += "cortina:$nivel"
    }

    override fun pedirPorta(posicao: Int, aberto: Int) {
        eventos += "porta:$posicao=$aberto"
        talvezFalhar("pedirPorta")
        pedidos += "porta:$posicao=$aberto"
    }

    override fun pedirAvm(valor: Int) {
        eventos += "avm:$valor"
        talvezFalhar("pedirAvm")
        pedidos += "avm:$valor"
    }
}

/** Preferências em memória, com os tipos de verdade (ler com o tipo errado lança, como no Android). */
internal class PreferenciasEmMemoria : PreferenciasDoMotor {
    val valores = ConcurrentHashMap<String, Any>()

    override fun contem(chave: String): Boolean = valores.containsKey(chave)

    override fun booleano(chave: String, padrao: Boolean): Boolean = valores[chave]?.let { it as Boolean } ?: padrao

    override fun inteiro(chave: String, padrao: Int): Int = valores[chave]?.let { it as Int } ?: padrao

    override fun decimal(chave: String, padrao: Float): Float = valores[chave]?.let { it as Float } ?: padrao

    override fun texto(chave: String): String? = valores[chave] as String?

    override fun gravarBooleano(chave: String, valor: Boolean) {
        valores[chave] = valor
    }

    override fun gravarInteiro(chave: String, valor: Int) {
        valores[chave] = valor
    }

    override fun gravarTexto(chave: String, valor: String) {
        valores[chave] = valor
    }

    override fun apagar(vararg chaves: String) {
        for (chave in chaves) valores.remove(chave)
    }

    operator fun set(chave: String, valor: Any) {
        valores[chave] = valor
    }
}

/** Shell: cada comando (argv unido por espaço) é anotado; a saída vem de [saidas] ou de [responder]. */
internal class ShellFalso(private val eventos: MutableList<String>) : ShellDoMotor {
    @Volatile var disponivelAgora = true
    val comandos = CopyOnWriteArrayList<String>()
    val saidas = ConcurrentHashMap<String, String>()
    @Volatile var responder: (String) -> String? = { null }

    override fun disponivel(): Boolean = disponivelAgora

    override fun executar(comando: Array<String>): String {
        val texto = comando.joinToString(" ")
        eventos += "shell:$texto"
        comandos += texto
        return responder(texto) ?: saidas[texto] ?: ""
    }
}

/** O lançador: anota as guardas e os pulsos. */
internal class ProjecaoFalsa(private val eventos: MutableList<String>) : ProjecaoNasTelas {
    @Volatile var androidAutoNoPainel = false

    override fun androidAutoNoDisplay(display: Int) = display == 3 && androidAutoNoPainel
    override fun carPlayNoDisplay(display: Int) = false
    override fun guardarCarPlayNoPainel(motivo: String) {
        eventos += "guardaCarPlay:$motivo"
    }
    override fun guardarAndroidAutoNoPainelAposJanelaNativa(motivo: String) {
        eventos += "guardaAA:$motivo"
    }
    override fun pulsoDoAndroidAutoDuranteJanelaNativa(motivo: String) {
        eventos += "pulsoDurante:$motivo"
    }
    override fun pulsoDoAndroidAutoAoSairDaJanelaNativa(motivo: String) {
        eventos += "pulsoAoSair:$motivo"
    }
    override fun protegerCartaoDoArNaTransicao() = false
    override fun teclaDoVolante(tecla: Int, acao: Int) = false
    override fun comandoDeMidiaDoPainel(comando: Int) = false
    override fun teclaMereceDiagnostico(tecla: Int) = false
    override fun mensagemDoPainelMereceDiagnostico(mensagem: Int) = false
}

/** Diário que anota "evento" e os detalhes. */
internal class DiarioFalso(private val eventos: MutableList<String>) : (String, Map<String, Any?>) -> Unit {
    val entradas = CopyOnWriteArrayList<Pair<String, Map<String, Any?>>>()

    override fun invoke(evento: String, detalhes: Map<String, Any?>) {
        eventos += "diario:$evento"
        entradas += evento to detalhes
    }

    fun nomes(): List<String> = entradas.map { it.first }
}

/** Tudo montado para os comandos (3.3c). */
internal class BancadaDosComandos(decisoes: DecisoesDosComandos = DecisoesDosComandos()) {
    val eventos: MutableList<String> = Collections.synchronizedList(ArrayList())
    val agenda = AgendaFalsa(eventos)
    val carro = CarroFalso(eventos)
    val voz = VozFalsa(eventos)
    val prefs = PreferenciasEmMemoria()
    val registro = RegistroFalsoDoMotor()
    val diario = DiarioFalso(eventos)
    val projecao = ProjecaoFalsa(eventos)

    /** O que a trava responde (null = pode); anota os parâmetros. */
    @Volatile var recusa: ((exigirP: Boolean, exigirLeitura: Boolean) -> br.com.opengw.veiculo.TravaDeMovimento.Motivo?) = { _, _ -> null }
    val pedidosATrava = CopyOnWriteArrayList<Pair<Boolean, Boolean>>()

    val comandos = ComandosDoCarro(
        carro = carro,
        voz = voz,
        prefs = prefs,
        agenda = agenda,
        trava = { p, l ->
            pedidosATrava += p to l
            recusa(p, l)
        },
        registro = registro,
        diario = diario,
        decisoes = decisoes,
    ).also { it.projecao = projecao }

    val avisosDoAr = CopyOnWriteArrayList<Boolean>()

    val ar = ControleDoArNoMaximo(carro, prefs, agenda, registro, diario).also { controle ->
        controle.aviso = AvisoDoArNoMaximo { ativo ->
            eventos += "aviso:$ativo"
            avisosDoAr += ativo
        }
    }
}
