// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ponte

import br.com.deepgwt.telas.ProjectionGeometry

/**
 * O lado do painel que a ponte usa. O projetor do painel implementa.
 *
 * Threads: [naPrincipal] e [executar] seguem o que dizem. [batimento], [avisoAtivo] e
 * [avisoDispensado] são usados direto da thread do WebView (onde rodam os métodos do
 * `window.Android`): guarde só o horário/o estado em campo volátil. Os outros métodos a ponte sempre
 * chama de dentro de [naPrincipal].
 */
interface Painel {
    /** Roda já se estiver na thread principal; senão posta para ela, na ordem dos pedidos. */
    fun naPrincipal(bloco: () -> Unit)

    /** `evaluateJavascript` na página do tema. Só na principal. [resposta] recebe o JSON do resultado. */
    fun executar(js: String, resposta: ((String?) -> Unit)? = null)

    /** O selo de aviso do carro está aceso. */
    val avisoAtivo: Boolean

    /** Os avisos à mostra já foram dispensados. */
    val avisoDispensado: Boolean

    /** Reconhece todos os avisos à mostra (exceto se o mais novo surgiu há menos de 2,5 s). */
    fun dispensarAvisos()

    /** A página está viva (o vigia recarrega depois de 15 s sem batida com o painel visível). */
    fun batimento()

    /** Onde um app aberto no painel deve ficar (já limitado a 1920×720); vale até trocar de tema. */
    fun areaDosApps(x: Int, y: Int, largura: Int, altura: Int)

    /** Liga/desliga uma máscara nativa; [nome] já em minúsculas e entre as cinco aceitas. */
    fun mascaraNativa(nome: String, visivel: Boolean)

    /** Configuração avançada das máscaras (hoje o painel só redesenha). */
    fun configuracaoDasMascaras(json: String)

    /** Grava o modo de exibição; o painel normaliza (Normal/Esportivo/Reduzido/Clean/Mapa). */
    fun salvarModoDeExibicao(valor: String)
}

/** O controle da projeção de tela da central no painel. Código nosso fora deste pacote (a sala suja liga). */
interface Projecao {
    /** O vão que o tema declarou por `setProjectionBox`; null = busca automática. Só na principal. */
    var caixaDeclaradaPeloTema: ProjectionGeometry.Caixa?

    fun parar(motivo: String)

    /** Projeta de novo a última área escolhida; false se não há área guardada. */
    fun reprojetarUltimaArea(): Boolean

    fun abrirSelecao()
}

/**
 * Dados que não são do carro. A sala suja liga às fontes atuais por adaptador.
 *
 * Threads: [temaAtivo], [midiaDoApp], [arquivoDeFundoDoTema] e [modosDeExibicaoDoTema] podem ser
 * chamados da thread do WebView e da principal — devem ser rápidos (sem shell; o `theme.xml` pode ser
 * lido, a ponte guarda a resposta). [appNoTopo], [rotuloDoApp], [iconeDoApp] e [appsDoLauncher]
 * são chamados fora da principal (podem ir ao shell).
 */
interface Fontes {
    /** A pasta do tema customizado ativo; "" = nenhum (painel original). */
    fun temaAtivo(): String

    /** A mídia de app Android vista pelo ouvinte de notificações (seção 7.4). */
    fun midiaDoApp(): MidiaDoApp

    /** O pacote no topo do display (1 ou 3), ou null. Pode ir ao shell: lento. */
    fun appNoTopo(display: Int): String?

    fun rotuloDoApp(pacote: String): String

    /** Ícone do app em data URL PNG; "" se não houver. */
    fun iconeDoApp(pacote: String): String

    /** Os apps configurados no lançador, na ordem da lista (seção 7.3). */
    fun appsDoLauncher(): List<AppDoLauncher>

    /**
     * Caminho absoluto do papel de parede do tema ativo, ou null. [dica] é o caminho relativo
     * gravado; sem dica, o `<background>` do `theme.xml`; sem tema customizado ativo, null.
     */
    fun arquivoDeFundoDoTema(dica: String?): String?

    /** Os `<DisplayMode>` do `theme.xml` do tema ativo (sem tema: os do painel original). */
    fun modosDeExibicaoDoTema(): List<ModoDeExibicao>
}

/**
 * A mídia de um app Android tocando agora. [capa] é a imagem do álbum (um `android.graphics.Bitmap`
 * no app); a fonte troca o objeto a cada capa nova, e a ponte codifica cada objeto uma vez só.
 */
class MidiaDoApp(
    val titulo: String?,
    val artista: String?,
    val album: String?,
    val tocando: Boolean,
    val capa: Any? = null,
) {
    companion object {
        @JvmField
        val NENHUMA = MidiaDoApp(null, null, null, false, null)
    }
}

/** Um app do lançador. [nomeDeExibicao] = nome customizado, senão o rótulo; [icone] em data URL PNG. */
data class AppDoLauncher(
    val pacote: String,
    val atividade: String?,
    val nomeDeExibicao: String,
    val icone: String,
    val display: Int,
)

/** Um `<DisplayMode name x y width height/>` do `theme.xml`. */
data class ModoDeExibicao(
    val nome: String,
    val x: Int,
    val y: Int,
    val largura: Int,
    val altura: Int,
)
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
