package br.com.deepgwt.tela3d.ponte

/*
 * O que a página manda para a UI nativa (faixa e cartões) pelo pacote JSON `updateDockIndicators`,
 * já sanitizado pelo [DecodificadorDeIndicadores]. Campo nulo = a chave não veio (não altera aquele
 * estado), exceto `cartoes`: nulo quer dizer "limpar a configuração" (ver a especificação).
 */

/** Texto de um resumo (clima ou consumo) e o aviso de que veio vazio mas a chave existia. */
data class ResumoDeTexto(
    /** O texto a mostrar (o padrão `—` ou `— °C  ·  Fan —  ·  AUTO —` quando veio vazio). */
    val texto: String,
    /** A chave existia, mas o conteúdo era vazio: o [texto] é o padrão. */
    val vazioMasPresente: Boolean,
    /** A descrição de acessibilidade (`Climate summary: …` / `Consumption summary: …`). */
    val descricao: String,
)

data class ConfigDosCartoes(val cartoes: List<CartaoInferior>, val limite: Int)

data class LinhaDeMenu(val rotulo: String, val comando: String, val selecionada: Boolean)

data class OpcoesDoRelogio(
    val face: String,
    val formatoDeHora: String,
    val marcas: String,
    val placas: String,
    val formatoDaLombada: String,
    val palavras: String,
) {
    companion object {
        val PADRAO = OpcoesDoRelogio("panorama", "system", "index", "frost", "month-name", "short")
    }
}

data class CartaoInferior(
    val id: String,
    val titulo: String,
    val valor: String,
    val acao: String,
    val primario: String,
    val secundario: String,
    val metricaA: String,
    val metricaB: String,
    val progresso: Int,
    val demo: Boolean,
    val acaoDoIcone: String,
    val acaoLonga: String,
    val glifo: String,
    val textoDoGlifo: String,
    val relogio: OpcoesDoRelogio,
    val menu: List<LinhaDeMenu>,
    val menuDeEdicao: List<LinhaDeMenu>,
    val estado: String,
    val estadoDasRodas: List<String>, // sempre 4 itens
    val variantePotencia: String,
    val sabeSoc: Boolean,
    val soc: Double,
    val pressoes: String,
    val temperaturas: String,
    val unidadeDePressao: String,
    val pacoteDoApp: String,
    val navRestante: String,
    val navDuracao: String,
    val navChegada: String,
    val navCidadeOciosa: String,
    val navAcaoOciosa: String,
    val barrasDeEnergia: List<Int>,
)

data class IndicadoresDoDock(
    val modoDoDock: String?,                 // "launcher" | "cards"
    val nomeDaAreaAtiva: String?,
    val quantidadeDeAreas: Int?,
    val indiceDaAreaAtiva: Int?,
    val estudioAberto: Boolean?,
    val telaDoEstudio: String?,              // "" quando fora do conjunto
    val corDeDestaque: Int?,                 // ARGB opaco
    val cartoes: ConfigDosCartoes?,          // null => limpar a configuração
    val editandoTrilho: Boolean?,
    val focoDoTrilho: String?,
    val resumoDoClima: ResumoDeTexto?,
    val resumoDoConsumo: ResumoDeTexto?,
    val modelo: String?,
    val desempenho: String?,
    val tinta: String?,
    val tempo: String?,
    val fps: Boolean?,
    val raioX: Boolean?,
    val modoDoTemaDosWidgets: String?,
    val temaDosWidgets: String?,
    val temaClaro: Boolean?,
    /** `dockStyle`: `none`, `flat`, `lion` ou `bar`; nulo = a página não mandou. */
    val estiloDoDock: String? = null,
)

/** `updateCenterFill`: modo normalizado (`car`/`wallpaper`/`mixed`) e visibilidade do chip. */
data class PreenchimentoDoCentro(val modo: String?, val visivel: Boolean?)
