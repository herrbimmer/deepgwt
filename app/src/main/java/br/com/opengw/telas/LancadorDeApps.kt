package br.com.opengw.telas

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * O lançador de apps nos displays (módulo 3.8, parte 2): a API inteira que a fachada [Telas] vai
 * expor. Implementação de sala limpa (`docs/independencia/specs/lancador*.md`).
 *
 * Ninguém chama isto ainda: na troca, a sala suja faz `Telas` delegar para cá (ou renomeia).
 * Funções `suspend` rodam no despacho de E/S; as consultas rodam o shell na thread de quem chamou
 * (D1), com a lista de pilhas guardada por 250 ms. Nenhuma função lança.
 */
object LancadorDeApps {

    private val nucleo: NucleoDoLancador get() = Lancador.nucleo

    // --- já existentes em Telas: mesmas assinaturas ---

    /** Redimensiona os apps gerenciados do display 0 depois que a faixa da barra mudou. Volta na hora. */
    @JvmStatic
    fun reaplicarLimitesDoDisplay0() {
        nucleo.telas.reaplicarLimitesDoDisplay0()
    }

    /** [0, 0, largura, fundo] de "tela cheia" no display 0 com a faixa da barra descontada (2-F6). */
    @JvmStatic
    fun limitesDeTelaCheiaNoDisplay0(pacote: String): IntArray =
        nucleo.semFalhar("limitesDeTelaCheiaNoDisplay0", intArrayOf(0, 0, 1920, 720)) {
            nucleo.telas.limitesDeTelaCheiaNoDisplay0(pacote)
        }

    // --- ligação com os outros módulos ---

    /** Chamado uma vez na partida do app. Sem isto, tudo funciona com dependências vazias. */
    @JvmStatic
    fun instalar(dependencias: DependenciasDasTelas) {
        nucleo.dependencias = dependencias
    }

    // --- configurações gravadas (2-A) ---

    /** Android Auto, CarPlay, Configurações (A4). */
    @JvmField
    val APPS_PREDEFINIDOS: List<ConfigDoApp> = PacotesDaCentral.PREDEFINIDOS

    /** Na ordem gravada. */
    @JvmStatic
    fun configuracoes(): List<ConfigDoApp> = nucleo.semFalhar("configuracoes", emptyList()) { nucleo.telas.configuracoes() }

    @JvmStatic
    fun configuracao(pacote: String): ConfigDoApp? = nucleo.semFalhar("configuracao", null) { nucleo.telas.configuracao(pacote) }

    /** Substitui pelo pacote ou acrescenta no fim. */
    @JvmStatic
    fun salvar(config: ConfigDoApp) = nucleo.semFalhar("salvar", Unit) { nucleo.telas.salvar(config) }

    /** Regrava a lista inteira, nesta ordem. */
    @JvmStatic
    fun salvarTodas(configs: List<ConfigDoApp>) = nucleo.semFalhar("salvarTodas", Unit) { nucleo.telas.salvarTodas(configs) }

    @JvmStatic
    fun apagar(pacote: String) = nucleo.semFalhar("apagar", Unit) { nucleo.telas.apagar(pacote) }

    /** Troca com a anterior. */
    @JvmStatic
    fun subir(pacote: String) = nucleo.semFalhar("subir", Unit) { nucleo.telas.subir(pacote) }

    /** Troca com a seguinte. */
    @JvmStatic
    fun descer(pacote: String) = nucleo.semFalhar("descer", Unit) { nucleo.telas.descer(pacote) }

    /** A gravada; senão a predefinida; senão uma padrão a partir do app instalado; null se não há como abrir (A6). */
    @JvmStatic
    @JvmOverloads
    fun configuracaoOuPadrao(contexto: Context, pacote: String, gravar: Boolean = true): ConfigDoApp? =
        nucleo.semFalhar("configuracaoOuPadrao", null) {
            nucleo.telas.configuracaoOuPadrao(pacote, gravar) { InfoDosApps.atividadeDeAbertura(contexto, it) }
        }

    /** Semeia o atalho das Configurações do Android uma vez por instalação (A7). */
    @JvmStatic
    fun semearAtalhosPadrao() = nucleo.semFalhar("semearAtalhosPadrao", Unit) { nucleo.telas.semearAtalhosPadrao() }

    // --- nome e ícone ---

    @JvmStatic
    @JvmOverloads
    fun infoDoApp(contexto: Context, pacote: String, nome: String? = null): InfoDoApp =
        try {
            InfoDosApps.infoDoApp(contexto, pacote, nome)
        } catch (e: Exception) {
            InfoDoApp(nome?.ifEmpty { null } ?: pacote, null)
        }

    // --- abrir, mover, fechar (2-C) ---

    /** C1. */
    suspend fun abrir(config: ConfigDoApp) = withContext(Dispatchers.IO) { nucleo.telas.abrir(config) }

    /** C2. */
    suspend fun enviarParaDisplay(config: ConfigDoApp) = withContext(Dispatchers.IO) { nucleo.telas.enviarParaDisplay(config) }

    /** C3. O contexto é aceito por compatibilidade; a abertura usa o contexto da aplicação. */
    suspend fun abrirNaPrincipal(contexto: Context, pacote: String, atividade: String? = null) =
        withContext(Dispatchers.IO) { nucleo.telas.abrirNaPrincipal(pacote, atividade) }

    /** O mesmo que [abrirNaPrincipal], no escopo do próprio módulo: sobrevive ao fechamento de quem chamou. */
    @JvmStatic
    @JvmOverloads
    fun abrirNaPrincipalSemEsperar(contexto: Context, pacote: String, atividade: String? = null) {
        nucleo.emSegundoPlano("abrir na principal $pacote") { nucleo.telas.abrirNaPrincipal(pacote, atividade) }
    }

    /** Traz tudo dos displays 1 e 3 para o 0. Devolve os pacotes movidos (vazio = nada movido). C4 */
    suspend fun trazerTodosParaPrincipal(): List<String> = withContext(Dispatchers.IO) { nucleo.telas.trazerTodosParaPrincipal() }

    /** Redimensiona um app que já está no display da config, com os números crus dela. C6 */
    suspend fun redimensionar(config: ConfigDoApp) = withContext(Dispatchers.IO) { nucleo.telas.redimensionar(config) }

    /** C7. */
    suspend fun fechar(pacote: String) = withContext(Dispatchers.IO) { nucleo.telas.fechar(pacote) }

    @JvmStatic
    fun fecharSemEsperar(pacote: String) {
        nucleo.emSegundoPlano("fechar $pacote") { nucleo.telas.fechar(pacote) }
    }

    /** Confere e corrige a "tarja preta" do app da frente do display 0 (F7). */
    suspend fun corrigirTarjaPreta(pacote: String) = withContext(Dispatchers.IO) { nucleo.telas.corrigirTarjaPreta(pacote) }

    // --- geometria (2-F) ---

    /** [esquerda, topo, direita, fundo] que o app da config deve ocupar. F1 */
    @JvmStatic
    fun limitesEfetivos(config: ConfigDoApp): IntArray =
        nucleo.semFalhar("limitesEfetivos", intArrayOf(config.x, config.y, config.x + config.largura, config.y + config.altura)) {
            nucleo.telas.limitesEfetivos(config)
        }

    /** Largura e altura físicas do display; (1920, 720) se o display não existe. F9 */
    @JvmStatic
    fun resolucao(display: Int): Pair<Int, Int> = nucleo.resolucao(display)

    /** Área para apps declarada pelo tema ativo em tempo de execução: [x, y, largura, altura] ou null. F2 */
    @JvmStatic
    var areaDoTema: IntArray?
        get() = nucleo.areaDoTema?.copyOf()
        set(valor) {
            nucleo.areaDoTema = valor?.copyOf()
        }

    // --- o que está nas telas (2-E) — consultas ---

    @JvmStatic
    fun pacoteNoTopo(display: Int): String? = nucleo.telas.pacoteNoTopo(display)

    @JvmStatic
    fun temAppNoDisplay(display: Int): Boolean = nucleo.semFalhar("temAppNoDisplay", false) { nucleo.telas.temAppNoDisplay(display) }

    @JvmStatic
    fun tarefaDoPacote(pacote: String): TarefaNaTela? = nucleo.telas.tarefaDoPacote(pacote)

    @JvmStatic
    fun primeirasTarefas(pacotes: Collection<String>): Map<String, TarefaNaTela> = nucleo.telas.primeirasTarefas(pacotes)

    // --- eventos ---

    /** Uma janela de app chegou à frente (serviço de acessibilidade). Volta na hora. G1 */
    @JvmStatic
    fun aoMudarJanela(pacote: String) = nucleo.semFalhar("aoMudarJanela", Unit) { nucleo.telas.aoMudarJanela(pacote) }
}
