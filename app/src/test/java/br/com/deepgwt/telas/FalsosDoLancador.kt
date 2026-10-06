// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.telas

import br.com.deepgwt.nucleo.Preferencia
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

/**
 * Uma central de mentira: guarda as pilhas e responde aos comandos do lançador como o Android 9 da
 * central (o bastante para conferir a sequência de comandos).
 */
internal class CentralFalsa : ShellDoLancador {

    class TarefaF(val id: Int, val pacote: String, val atividade: String)

    class PilhaF(val id: Int, var display: Int, var limites: IntArray, var modo: String, val tarefas: MutableList<TarefaF>)

    val pilhas = mutableListOf<PilhaF>()
    val comandos = mutableListOf<String>()
    private var proximaPilha = 100
    private var proximaTarefa = 500

    var moverFalha = false
    var usb = ""
    val pids = mutableMapOf<String, String>()
    var surfaceFlinger = ""
    var logcat = ""
    var audio = ""
    var bootId = "4a3f0c2e-1b2d-4e5f-9a8b-7c6d5e4f3a2b"
    var shizuku = true

    /** Pacotes cujo `am start` não cria tarefa (e a resposta a dar). */
    val naoAbre = mutableMapOf<String, String>()

    fun pilha(
        display: Int,
        pacote: String,
        atividade: String = ".Main",
        limites: IntArray = intArrayOf(0, 0, 1920, 720),
        modo: String = "freeform",
        outras: List<String> = emptyList(),
    ): PilhaF {
        val tarefas = mutableListOf(TarefaF(proximaTarefa++, pacote, atividade))
        for (o in outras) tarefas += TarefaF(proximaTarefa++, o, ".Main")
        val p = PilhaF(proximaPilha++, display, limites, modo, tarefas)
        pilhas += p
        return p
    }

    fun pilhaDe(pacote: String): PilhaF? = pilhas.firstOrNull { p -> p.tarefas.any { it.pacote == pacote } }

    /** Os comandos que mudam algo (sem as leituras). */
    fun acoes(): List<String> = comandos.filterNot {
        it == "am stack list" || it.startsWith("dumpsys") || it.startsWith("pidof") || it.startsWith("cat ") ||
            it.startsWith("logcat")
    }

    override fun disponivel() = shizuku

    override fun rodar(comando: String): String {
        comandos += comando
        val partes = comando.split(" ")
        return when {
            comando == "am stack list" -> listar()
            comando.startsWith("am stack resize ") -> {
                val p = pilhaPorId(partes[3].toInt())
                p?.limites = intArrayOf(partes[4].toInt(), partes[5].toInt(), partes[6].toInt(), partes[7].toInt())
                ""
            }
            comando.startsWith("am display move-stack ") -> {
                if (moverFalha) return "Error: java.lang.IllegalArgumentException: falhou"
                pilhaPorId(partes[3].toInt())?.display = partes[4].toInt()
                ""
            }
            comando.startsWith("am stack remove ") -> {
                pilhas.removeAll { it.id == partes[3].toInt() }
                ""
            }
            comando.startsWith("am stack set-windowing-mode ") -> {
                pilhaPorId(partes[3].toInt())?.modo = if (partes[4] == "1") "fullscreen" else "freeform"
                ""
            }
            comando.startsWith("am force-stop ") -> {
                val pacote = partes[2]
                for (p in pilhas) p.tarefas.removeAll { it.pacote == pacote }
                pilhas.removeAll { it.tarefas.isEmpty() }
                ""
            }
            comando.startsWith("am start ") -> iniciar(comando)
            comando.startsWith("cat /sys/class/android_usb") -> usb
            comando.startsWith("cat /proc/sys/kernel/random/boot_id") -> bootId
            comando.startsWith("pidof ") -> pids[partes[1]] ?: ""
            comando.startsWith("dumpsys SurfaceFlinger") -> surfaceFlinger
            comando.startsWith("dumpsys audio") -> audio
            comando.startsWith("logcat") -> logcat
            else -> ""
        }
    }

    private fun pilhaPorId(id: Int) = pilhas.firstOrNull { it.id == id }

    private fun iniciar(comando: String): String {
        val alvo = Regex("""-n (\S+)/(\S+)""").find(comando) ?: return "Error: sem componente"
        val pacote = alvo.groupValues[1]
        val atividade = alvo.groupValues[2]
        val display = Regex("""--display (\d+)""").find(comando)?.groupValues?.get(1)?.toInt() ?: 0
        naoAbre[pacote]?.let { return it }
        if (pilhas.any { p -> p.display == display && p.tarefas.any { it.pacote == pacote } }) {
            return "Warning: Activity not started, its current task has been brought to the front"
        }
        val modo = if ("--windowingMode 5" in comando) "freeform" else "fullscreen"
        val p = PilhaF(proximaPilha++, display, intArrayOf(0, 0, 1920, 720), modo, mutableListOf(TarefaF(proximaTarefa++, pacote, atividade)))
        pilhas.add(0, p)
        return "Starting: Intent { cmp=$pacote/$atividade }"
    }

    private fun listar(): String = buildString {
        for (p in pilhas) {
            val l = p.limites
            append("Stack id=${p.id} bounds=[${l[0]},${l[1]}][${l[2]},${l[3]}] displayId=${p.display} userId=0\n")
            append(" configuration={1.0 winConfig={ mWindowingMode=${p.modo} mActivityType=standard} s.12}\n")
            for (t in p.tarefas) {
                append("  taskId=${t.id}: ${t.pacote}/${t.atividade} bounds=[${l[0]},${l[1]}][${l[2]},${l[3]}] userId=0 visible=true\n")
            }
        }
    }
}

/** Relógio de mentira: esperar só anda o tempo. */
internal class RelogioFalso(var mono: Long = 1_000_000L, var paredeMs: Long = 1_700_000_000_000L) : RelogioDoLancador {
    val esperas = mutableListOf<Long>()
    override fun monotonico() = mono
    override fun parede() = paredeMs
    override suspend fun esperar(ms: Long) {
        esperas += ms
        andar(ms)
        yield()
    }

    fun andar(ms: Long) {
        mono += ms
        paredeMs += ms
    }
}

internal class PreferenciasFalsas : PreferenciasDoLancador {
    val valores = mutableMapOf<String, Any>()
    var escritas = 0

    operator fun set(chave: Preferencia, valor: Any) {
        valores[chave.chave] = valor
    }

    override fun texto(chave: Preferencia) = valores[chave.chave] as? String
    override fun inteiro(chave: Preferencia) = valores[chave.chave] as? Int
    override fun booleano(chave: Preferencia, padrao: Boolean) = valores[chave.chave] as? Boolean ?: padrao
    override fun gravar(valores: Map<Preferencia, Any>) {
        escritas++
        for ((k, v) in valores) this.valores[k.chave] = v
    }
}

internal class SistemaFalso : SistemaDoLancador {
    override var densidade = 1f
    override val pacoteDoApp = "br.com.deepgwt"
    override var depuracao = true
    val atividades = mutableMapOf<String, String>()
    val aberturasPelaIntent = mutableListOf<Pair<String, Int>>()
    var reposicionamentos = 0

    override fun resolucao(display: Int): Pair<Int, Int>? = if (display in setOf(0, 1, 2, 3)) 1920 to 720 else null
    override fun atividadeDeAbertura(pacote: String) = atividades[pacote]
    override fun abrirPelaIntent(pacote: String, flags: Int): Boolean {
        if (pacote !in atividades) return false
        aberturasPelaIntent += pacote to flags
        return true
    }

    override fun reposicionarBarra() {
        reposicionamentos++
    }

    override fun lerArquivo(caminho: String): String? = null
    override fun naThreadPrincipal(bloco: () -> Unit) = bloco()
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

internal class CarroFalso : CarroDoLancador {
    val valores = mutableMapOf<String, String>()
    override fun lerDoCarro(chave: String) = valores[chave]
    override fun ler(chave: String) = valores[chave]
}

/** Segundo plano de mentira: guarda os trabalhos; o teste roda quando quiser. */
internal class FundoFalso : FundoDoLancador {
    val fila = ArrayDeque<Pair<String, suspend () -> Unit>>()
    val nomes = mutableListOf<String>()

    override fun lancar(nome: String, bloco: suspend () -> Unit) {
        nomes += nome
        fila.addLast(nome to bloco)
    }

    /** Roda tudo (inclusive o que for lançado no caminho), menos o que [pular] escolher. */
    fun rodarTudo(pular: (String) -> Boolean = { false }) = runBlocking {
        var voltas = 0
        while (fila.isNotEmpty() && voltas++ < 500) {
            val (nome, bloco) = fila.removeFirst()
            if (pular(nome)) continue
            bloco()
        }
    }

    fun descartar() = fila.clear()
}

internal class RegistroFalso : RegistroDoLancador {
    val linhas = mutableListOf<String>()
    override fun depuracao(texto: String) {
        linhas += "D $texto"
    }

    override fun info(texto: String) {
        linhas += "I $texto"
    }

    override fun aviso(texto: String) {
        linhas += "W $texto"
    }

    override fun erro(texto: String, causa: Throwable?) {
        linhas += "E $texto ${causa ?: ""}"
    }

    fun tem(trecho: String) = linhas.any { trecho in it }
}

internal class CanalAaFalso : CanalDoAndroidAuto {
    var binderVivo = false
    var statusAtual: Int? = null
    var musicaAtual: Int? = null
    val teclas = mutableListOf<Pair<Int, Int>>()
    val transacoes = mutableListOf<String>()
    var pedidos = 0
    override fun vivo() = binderVivo
    override fun ligacaoPedida() = pedidos > 0
    override fun garantirLigado(): Boolean {
        pedidos++
        return binderVivo
    }

    override fun statusSeJaLigado() = if (binderVivo) statusAtual else null
    override fun status() = if (binderVivo) statusAtual else null
    override fun musica() = if (binderVivo) musicaAtual else null
    override fun progresso() = if (binderVivo) 10 else null
    override fun tecla(ordinal: Int, acao: Int): Boolean {
        teclas += ordinal to acao
        return binderVivo
    }

    override fun proxima() = binderVivo.also { transacoes += "next" }
    override fun anterior() = binderVivo.also { transacoes += "previous" }
    override fun tocar() = binderVivo.also { transacoes += "play" }
    override fun pausar() = binderVivo.also { transacoes += "pause" }
    override fun aparelhos(): List<AparelhoDoAa>? = if (binderVivo) emptyList() else null
    override fun conectar(aparelho: AparelhoDoAa) = binderVivo
    override fun ouvirSemFio(aparelho: AparelhoDoAa) = binderVivo
}

internal class CanalCpFalso : CanalDoCarPlay {
    var binderVivo = true
    var statusAtual: Int? = 2
    var modosNormais = 0
    var pedidos = 0
    override fun vivo() = binderVivo
    override fun pedirLigacao() {
        pedidos++
    }

    override fun status() = statusAtual
    override fun modoNormal(): Boolean {
        modosNormais++
        return true
    }
}

internal class BarraFalsa : BarraParaTelas {
    override var alturaDp = 60
    override var visivel = true
    override var pacoteDaMidia: String? = null
    override var tocando = false
    override var tituloDaMidia: String? = null
    override var artistaDaMidia: String? = null
    override var albumDaMidia: String? = null
    override var duracaoDaMidiaMs = 0L
    override var espelhamentoNoPainel = ""
    var rotaNativa = false
    var tocandoNativo: Boolean? = null
    val dicas = mutableListOf<Pair<String, Boolean>>()
    val restaurados = mutableListOf<String>()
    val alvosPelaRota = mutableListOf<Boolean>()
    var reaberturas = 0
    var progressoZerado = 0

    override fun dicaDeReproducao(pacote: String, tocando: Boolean) {
        dicas += pacote to tocando
    }

    override fun marcarRestaurado(pacote: String) {
        restaurados += "+$pacote"
    }

    override fun desmarcarRestaurado(pacote: String) {
        restaurados += "-$pacote"
    }

    override fun rotaNativaDoAndroidAuto() = rotaNativa
    override fun rotaNativaTocando() = tocandoNativo
    var rotaEnvia = true

    override suspend fun alvoPelaRotaNativa(tocar: Boolean, motivo: String): Boolean {
        alvosPelaRota += tocar
        return rotaEnvia
    }

    override fun alternarMudoDoAndroidAuto() = true
    override fun zerarProgressoAposTrocaDeFaixa(motivo: String): Boolean {
        progressoZerado++
        return true
    }

    override fun voltarAposFocoExterno(pacote: String?, motivo: String) = Unit
    override fun reabrirAposEspelhamentoNoPainel(motivo: String) {
        reaberturas++
    }
}

internal class PainelFalso : AvisosAoPainel {
    val vaos = mutableListOf<List<Int>>()
    var geometrias = 0
    val apps = mutableListOf<Pair<Int, Boolean>>()
    override fun appNoDisplay(display: Int, ativo: Boolean) {
        apps += display to ativo
    }

    override fun geometriaMudou() {
        geometrias++
    }

    override fun prepararVaoNoDisplay3(limites: IntArray) {
        vaos += limites.toList()
    }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

/** Tudo montado. */
internal class Bancada {
    val central = CentralFalsa()
    val relogio = RelogioFalso()
    val prefs = PreferenciasFalsas()
    val sistema = SistemaFalso()
    val carro = CarroFalso()
    val canalAa = CanalAaFalso()
    val canalCp = CanalCpFalso()
    val fundo = FundoFalso()
    val registro = RegistroFalso()
    val barra = BarraFalsa()
    val painel = PainelFalso()
    var dcm = LeituraDoDcm(false, "")
    val diario = mutableListOf<String>()
    val launcher3d = mutableListOf<String>()

    val nucleo = NucleoDoLancador(
        AmbienteDoLancador(central, relogio, prefs, sistema, carro, canalAa, canalCp, fundo, registro),
    ).also {
        it.dependencias = DependenciasDasTelas(
            barra = barra,
            painel = painel,
            dcm = EstadoDoDcm { dcm },
            diario = DiarioDasTelas { evento, _ -> diario += evento },
            launcher3d = GanchoDoLauncher3d { p -> launcher3d += p },
        )
    }

    val telas get() = nucleo.telas
    val esp get() = nucleo.espelhamento
    val midia get() = nucleo.midia

    fun <T> rodar(bloco: suspend () -> T): T = runBlocking { bloco() }

    /** Limpa os registros (comandos, esperas, fundo) para conferir só o que vem depois. */
    fun zerarRegistros() {
        central.comandos.clear()
        relogio.esperas.clear()
        fundo.descartar()
        fundo.nomes.clear()
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
