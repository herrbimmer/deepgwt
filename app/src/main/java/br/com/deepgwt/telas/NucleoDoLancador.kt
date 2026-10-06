// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.telas

import br.com.deepgwt.nucleo.Preferencias
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException

/**
 * O lançador montado sobre um [AmbienteDoLancador]: as três partes (telas, espelhamento, mídia) e o
 * que elas dividem (comandos, lista de pilhas, geometria). Os objetos públicos ([LancadorDeApps],
 * [Espelhamento], [MidiaDoAndroidAuto]) delegam a uma instância só; os testes montam a sua.
 */
internal class NucleoDoLancador(val amb: AmbienteDoLancador) {

    @Volatile
    var dependencias: DependenciasDasTelas = DependenciasDasTelas()

    /** 2-F2: área de apps declarada pelo tema em tempo de execução (só em memória). */
    @Volatile
    var areaDoTema: IntArray? = null

    val pilhas = LeitorDePilhas(this)
    val telas = MotorDasTelas(this)
    val espelhamento = MotorDoEspelhamento(this)
    val midia = MotorDaMidia(this)

    val barra: BarraParaTelas get() = dependencias.barra
    val painel: AvisosAoPainel get() = dependencias.painel
    val pacoteDoApp: String get() = amb.sistema.pacoteDoApp

    // ── comandos ────────────────────────────────────────────────────────────────────────────────

    /**
     * Roda um comando pelo shell do módulo e registra (em nível de depuração: decisão 9, D19). Todo
     * comando, menos a própria leitura da lista, invalida a lista de pilhas guardada (2-E1).
     */
    fun rodar(comando: String): String {
        val saida = try {
            amb.shell.rodar(comando)
        } catch (e: Exception) {
            ""
        }
        amb.registro.depuracao("$ $comando -> ${saida.take(MAXIMO_NO_LOG)}")
        if (comando != COMANDO_DA_LISTA) pilhas.invalidar()
        return saida
    }

    /** "Falhou" = a saída contém `Exception` ou `Error`. */
    fun falhou(saida: String): Boolean = "Exception" in saida || "Error" in saida

    fun lista(): ListaDePilhas = pilhas.ler()

    suspend fun esperar(ms: Long) = amb.relogio.esperar(ms)

    fun agora(): Long = amb.relogio.monotonico()

    // ── telas e geometria ───────────────────────────────────────────────────────────────────────

    /** 2-F9: largura e altura físicas; (1920, 720) se o display não existe. */
    fun resolucao(display: Int): Pair<Int, Int> =
        try {
            amb.sistema.resolucao(display)
        } catch (e: Exception) {
            null
        } ?: (1920 to 720)

    fun telaCheia(display: Int): IntArray {
        val (l, a) = resolucao(display)
        return intArrayOf(0, 0, l, a)
    }

    fun ajustes(): AjustesDaGeometria {
        val p = amb.prefs
        val deps = dependencias
        return AjustesDaGeometria(
            densidade = amb.sistema.densidade,
            alturaDaBarraDp = try {
                deps.barra.alturaDp
            } catch (e: Exception) {
                60
            },
            barraPersistente = p.booleano(Preferencias.ENABLE_PERSISTENT_BOTTOM_BAR, false),
            painelLigado = p.booleano(Preferencias.ENABLE_VIRTUAL_CLUSTER, true),
            nomeDoTema = p.texto(Preferencias.VIRTUAL_CLUSTER_THEME) ?: "Básico",
            areaDoTema = areaDoTema,
            recuosPorApp = p.texto(Preferencias.BOTTOM_BAR_OVERRIDES),
            recuosMedidos = p.texto(Preferencias.BOTTOM_BAR_AUTO_OVERSCAN),
            recuoGlobal = p.inteiro(Preferencias.PERSISTENT_BOTTOM_BAR_OVERSCAN),
            mostradorDoPainel = p.texto(Preferencias.CURRENT_CLUSTER_DISPLAY),
            deslocamentoDoAaLigado = p.booleano(Preferencias.ENABLE_AA_CLUSTER_OFFSET, false),
            deslocamentoDoAa = p.inteiro(Preferencias.AA_CLUSTER_LEFT_OFFSET) ?: 135,
            resolucao = ::resolucao,
            areaGravada = { nome ->
                try {
                    deps.tema.area(nome)
                } catch (e: Exception) {
                    null
                }
            },
            registrar = { amb.registro.aviso(it) },
        )
    }

    /** Escapa `$` (o comando passa por `sh -c`). */
    fun escapar(texto: String): String = texto.replace("$", "\\$")

    // ── avisos para fora ────────────────────────────────────────────────────────────────────────

    fun diario(evento: String, detalhes: Map<String, Any?>) {
        try {
            dependencias.diario.registrar(evento, detalhes)
        } catch (e: Exception) {
            amb.registro.aviso("diário recusou $evento: ${e.message}")
        }
    }

    fun reposicionarBarra() {
        try {
            amb.sistema.reposicionarBarra()
        } catch (e: Exception) {
            amb.registro.aviso("broadcast da barra falhou: ${e.message}")
        }
    }

    /** Roda no escopo do módulo; uma falha só vai ao log. */
    fun emSegundoPlano(nome: String, bloco: suspend () -> Unit) {
        amb.fundo.lancar(nome) {
            try {
                bloco()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                amb.registro.erro("falha em segundo plano ($nome)", e)
            }
        }
    }

    /** Roda [bloco]; exceção vira log e [padrao] (nenhuma função pública lança). */
    inline fun <T> semFalhar(oQue: String, padrao: T, bloco: () -> T): T =
        try {
            bloco()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            amb.registro.erro("falhou: $oQue", e)
            padrao
        }

    companion object {
        const val COMANDO_DA_LISTA = "am stack list"
        private const val MAXIMO_NO_LOG = 600
    }
}

/**
 * A lista de pilhas guardada por 250 ms (2-E1). Leitores simultâneos dividem uma leitura (trava);
 * qualquer comando do módulo invalida.
 */
internal class LeitorDePilhas(private val n: NucleoDoLancador) {
    private val trava = Any()
    private val geracao = AtomicLong()

    @Volatile
    private var ultima: ListaDePilhas? = null

    @Volatile
    private var lidaEm = 0L

    fun ler(): ListaDePilhas = synchronized(trava) {
        val agora = n.agora()
        val guardada = ultima
        if (guardada != null && agora >= lidaEm && agora - lidaEm < VALIDADE_MS) return guardada
        val antes = geracao.get()
        val texto = n.rodar(NucleoDoLancador.COMANDO_DA_LISTA)
        val lida = ListaDePilhas.ler(texto, n::telaCheia)
        if (geracao.get() == antes) {
            ultima = lida
            lidaEm = agora
        }
        lida
    }

    fun invalidar() {
        geracao.incrementAndGet()
        ultima = null
    }

    companion object {
        const val VALIDADE_MS = 250L
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
