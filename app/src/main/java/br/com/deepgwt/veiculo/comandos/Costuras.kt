// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.comandos

import br.com.deepgwt.veiculo.motor.Agenda
import br.com.deepgwt.veiculo.motor.Cancelavel

/*
 * As costuras do resto do motor (módulos 3.3b, 3.3c e 3.3e): tudo o que a lógica usa do mundo de
 * fora chega por aqui. No app são `Veiculo`, as alças da voz do núcleo, as preferências e o Shizuku
 * (`LigacoesAndroid.kt`); nos testes de JVM, falsos escritos à mão.
 *
 * Especificação: `docs/independencia/specs/motor-resto.md` (índice) e as três partes.
 */

/** O carro como o resto do motor o vê. No app: `Veiculo` e o cache do núcleo (`MotorDoVeiculo`). */
internal interface CarroDoMotor {
    /** O canal com o serviço de controle está vivo agora (`Veiculo.controleVivo`). Barato. */
    val controleVivo: Boolean

    /** Build `simulator` (sem central; escrever volta como evento). */
    val simulador: Boolean

    /** Cache; na falta, pergunta ao carro uma vez (`Veiculo.ler`). */
    fun ler(chave: String): String?

    /** Pergunta ao carro agora (`Veiculo.lerDoCarro`). */
    fun lerDoCarro(chave: String): String?

    /** Só o cache; nunca vai ao carro (`MotorDoVeiculo.espiar`). */
    fun espiar(chave: String): String?

    /** `Veiculo.escrever` (com a camada do ar do núcleo para as chaves `car.hvac.*` dela). */
    fun escrever(chave: String, valor: String)
}

/**
 * A alça `IVehicle` do adaptador de voz da fabricante, só com o que os comandos usam
 * (motor-comandos.md §2.1). Cada método lança o que o binder lançar.
 */
internal interface VozDoVeiculo {
    /** `getWindowsStatus(0)`: 4 posições (dianteira esq., dianteira dir., traseira esq., traseira dir.). */
    fun estadoDasJanelas(): IntArray?

    /** `setWindowStatus(janela, estado)`. */
    fun pedirJanela(janela: Int, estado: Int)

    /** `getSkylightLevel(0)`. */
    fun nivelDoTeto(): Int

    /** `setSkylightLevel(nivel)`. */
    fun pedirTeto(nivel: Int)

    /** `getShadeScreensLevel(0)`. */
    fun nivelDaCortina(): Int

    /** `setShadeScreensLevel(nivel)`. */
    fun pedirCortina(nivel: Int)

    /** `setDoorOpen(posicao, aberto)`. */
    fun pedirPorta(posicao: Int, aberto: Int)
}

/** A alça `IDvr` do adaptador de voz: só `setAVM` (1 abre a câmera 360, 0 fecha). */
internal fun interface VozDaCamera {
    fun pedirAvm(valor: Int)
}

/** As alças da voz que o núcleo mantém. Null = indisponível agora. */
internal interface AlcasDaVoz {
    fun veiculo(): VozDoVeiculo?

    fun camera(): VozDaCamera?
}

/**
 * O arquivo de preferências do app ("deepgwt_prefs", armazenamento protegido). Os tipos importam
 * (é o formato gravado nos carros); a implementação do app tolera um tipo trocado sem lançar.
 */
internal interface PreferenciasDoMotor {
    /** A chave já foi gravada alguma vez (há casos que dependem de "ausente"). */
    fun contem(chave: String): Boolean

    fun booleano(chave: String, padrao: Boolean): Boolean

    fun inteiro(chave: String, padrao: Int): Int

    fun decimal(chave: String, padrao: Float): Float

    fun texto(chave: String): String?

    fun gravarBooleano(chave: String, valor: Boolean)

    fun gravarInteiro(chave: String, valor: Int)

    fun gravarTexto(chave: String, valor: String)

    fun apagar(vararg chaves: String)
}

/** Comandos de sistema pelo Shizuku (`br.com.deepgwt.nucleo.Shell`). Nunca na thread principal. */
internal interface ShellDoMotor {
    /** O Shizuku responde agora? */
    fun disponivel(): Boolean

    /** argv exato; devolve a saída padrão aparada (vazio se não rodou). */
    fun executar(comando: Array<String>): String
}

/** O diário de diagnóstico do motor (`MotorDoVeiculo.diario`). */
internal typealias DiarioDoResto = (evento: String, detalhes: Map<String, Any?>) -> Unit

/**
 * Aviso de "o ar no máximo ligou/desligou" (motor-comandos.md J.2.4 e J.5). A sala suja liga ao
 * barramento do painel (3.3d: `EventosDoPainel.publicar(ArNoMaximoMudou(ativo))`). Vazio: nada.
 */
fun interface AvisoDoArNoMaximo {
    fun mudou(ativo: Boolean)
}

/**
 * Decisões do dono que a especificação deixa abertas (motor-comandos.md §7). A especificação não
 * trouxe "decisões da sala suja" para esta parte; os padrões são as hipóteses descritas em cada
 * campo.
 */
data class DecisoesDosComandos(
    /**
     * D-C4: os comandos de §H também escrevem no simulador (onde o canal parece morto). Padrão
     * falso: A.2 manda fazer como hoje (nada) até o dono decidir.
     */
    val escreverNoSimulador: Boolean = false,
    /** E.5: câmera 360 sem estado no cache conta como fechada (abre). Padrão: o que E.5 manda. */
    val cameraSemEstadoAbre: Boolean = true,
)

/**
 * Tarefas agendadas que uma re-partida cancela de uma vez (motor-sistema.md G.8). Cada nome guarda
 * um agendamento só (agendar de novo troca o anterior). Depois de [cancelarTodos], uma tarefa que já
 * estava na fila não roda mais, mesmo que o cancelamento chegue tarde.
 */
internal class Agendados(private val agenda: Agenda) {

    private val trava = Any()
    private var geracao = 0
    private val pendentes = HashMap<String, Cancelavel>()

    fun agendar(nome: String, atrasoMs: Long, tarefa: () -> Unit) {
        synchronized(trava) {
            val minha = geracao
            pendentes.remove(nome)?.cancelar()
            pendentes[nome] = agenda.agendar(atrasoMs) { if (vale(minha)) tarefa() }
        }
    }

    fun repetir(nome: String, primeiraMs: Long, periodoMs: Long, tarefa: () -> Unit) {
        synchronized(trava) {
            val minha = geracao
            pendentes.remove(nome)?.cancelar()
            pendentes[nome] = agenda.repetir(primeiraMs, periodoMs) { if (vale(minha)) tarefa() }
        }
    }

    fun cancelar(nome: String) {
        synchronized(trava) { pendentes.remove(nome)?.cancelar() }
    }

    fun cancelarTodos() {
        synchronized(trava) {
            geracao++
            for (pendente in pendentes.values) pendente.cancelar()
            pendentes.clear()
        }
    }

    private fun vale(geracaoDaTarefa: Int): Boolean = synchronized(trava) { geracaoDaTarefa == geracao }
}

/** Texto numérico do carro ("23.0"; aceita vírgula) como número; null se ilegível. */
internal fun numeroDoCarro(texto: String?): Double? =
    texto?.trim()?.replace(',', '.')?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()?.takeIf { !it.isNaN() }
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
