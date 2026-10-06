package br.com.deepgwt.tela3d.integracao

import br.com.deepgwt.tela3d.faixa.ModoDaCasca
import br.com.deepgwt.tela3d.faixa.Proporcao
import br.com.deepgwt.tela3d.faixa.ReservasDaFaixa
import br.com.deepgwt.tela3d.faixa.MedidasDaTela
import br.com.deepgwt.tela3d.faixa.MedidasDaFaixa
import br.com.deepgwt.tela3d.faixa.limitesDosPopupsCss
import br.com.deepgwt.tela3d.janelas.EstadoDasJanelas
import br.com.deepgwt.tela3d.ponte.EstadoDoShell
import br.com.deepgwt.tela3d.ponte.IndicadoresDoDock
import br.com.deepgwt.tela3d.ponte.MargensDaPagina
import br.com.deepgwt.tela3d.ponte.ModoDeOcupacao
import br.com.deepgwt.tela3d.ponte.RetanguloEmPx
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import br.com.deepgwt.tela3d.catalogo.TipoDeProjecao as ProjecaoDoCatalogo
import br.com.deepgwt.tela3d.faixa.TipoDeProjecao as ProjecaoDaFaixa
import br.com.deepgwt.tela3d.janelas.ModoDeTela as ModoDasJanelas
import br.com.deepgwt.tela3d.janelas.ProporcaoDaDivisao as ProporcaoDasJanelas
import br.com.deepgwt.tela3d.janelas.TipoDeProjecao as ProjecaoDasJanelas
import br.com.deepgwt.tela3d.midia.TipoDeProjecao as ProjecaoDaMidia

/*
 * Conversões puras entre os tipos que cada módulo da tela 3D definiu para si (há quatro
 * `TipoDeProjecao`, dois retângulos, três "modos de tela"...). Cada módulo continua dono do seu tipo;
 * a integração converte aqui, sem Android, para os testes rodarem na JVM.
 */
internal object Conversoes {

    // --- projeção ---

    fun projecaoDaMidiaParaAsJanelas(tipo: ProjecaoDaMidia): ProjecaoDasJanelas = when (tipo) {
        ProjecaoDaMidia.NENHUMA -> ProjecaoDasJanelas.NENHUM
        ProjecaoDaMidia.ANDROID_AUTO -> ProjecaoDasJanelas.ANDROID_AUTO
        ProjecaoDaMidia.CARPLAY -> ProjecaoDasJanelas.CARPLAY
    }

    fun projecaoDaMidiaParaAFaixa(tipo: ProjecaoDaMidia): ProjecaoDaFaixa = when (tipo) {
        ProjecaoDaMidia.NENHUMA -> ProjecaoDaFaixa.NENHUMA
        ProjecaoDaMidia.ANDROID_AUTO -> ProjecaoDaFaixa.ANDROID_AUTO
        ProjecaoDaMidia.CARPLAY -> ProjecaoDaFaixa.CARPLAY
    }

    fun projecaoDasJanelasParaAFaixa(tipo: ProjecaoDasJanelas): ProjecaoDaFaixa = when (tipo) {
        ProjecaoDasJanelas.NENHUM -> ProjecaoDaFaixa.NENHUMA
        ProjecaoDasJanelas.ANDROID_AUTO -> ProjecaoDaFaixa.ANDROID_AUTO
        ProjecaoDasJanelas.CARPLAY -> ProjecaoDaFaixa.CARPLAY
    }

    fun projecaoDaFaixaParaAsJanelas(tipo: ProjecaoDaFaixa): ProjecaoDasJanelas = when (tipo) {
        ProjecaoDaFaixa.NENHUMA -> ProjecaoDasJanelas.NENHUM
        ProjecaoDaFaixa.ANDROID_AUTO -> ProjecaoDasJanelas.ANDROID_AUTO
        ProjecaoDaFaixa.CARPLAY -> ProjecaoDasJanelas.CARPLAY
    }

    /** O catálogo só conhece os dois tipos reais; "nenhuma" não tem ícone. */
    fun projecaoDoCatalogoParaAMidia(tipo: ProjecaoDoCatalogo): ProjecaoDaMidia = when (tipo) {
        ProjecaoDoCatalogo.ANDROID_AUTO -> ProjecaoDaMidia.ANDROID_AUTO
        ProjecaoDoCatalogo.CARPLAY -> ProjecaoDaMidia.CARPLAY
    }

    // --- casca: modo e proporção ---

    fun modoDasJanelasParaAFaixa(modo: ModoDasJanelas): ModoDaCasca = when (modo) {
        ModoDasJanelas.APP_E_CARRO -> ModoDaCasca.APP_E_CARRO
        ModoDasJanelas.DOIS_APPS -> ModoDaCasca.LADO_A_LADO
    }

    fun proporcaoDasJanelasParaAFaixa(p: ProporcaoDasJanelas): Proporcao = when (p) {
        ProporcaoDasJanelas.UM_PARA_UM -> Proporcao.UM_UM
        ProporcaoDasJanelas.DOIS_PARA_UM -> Proporcao.DOIS_UM
        ProporcaoDasJanelas.UM_PARA_DOIS -> Proporcao.UM_DOIS
    }

    fun modoDaFaixaParaAsJanelas(modo: ModoDaCasca): ModoDasJanelas = when (modo) {
        ModoDaCasca.APP_E_CARRO -> ModoDasJanelas.APP_E_CARRO
        ModoDaCasca.LADO_A_LADO -> ModoDasJanelas.DOIS_APPS
    }

    fun proporcaoDaFaixaParaAsJanelas(p: Proporcao): ProporcaoDasJanelas = when (p) {
        Proporcao.UM_UM -> ProporcaoDasJanelas.UM_PARA_UM
        Proporcao.DOIS_UM -> ProporcaoDasJanelas.DOIS_PARA_UM
        Proporcao.UM_DOIS -> ProporcaoDasJanelas.UM_PARA_DOIS
    }

    // --- estado do shell que a ponte publica para a página ---

    /**
     * O `EstadoDoShell` da ponte a partir do estado das janelas e dos retângulos dos dois lados (em
     * pixels de tela).
     */
    fun estadoDoShell(estado: EstadoDasJanelas, esquerdo: RetanguloEmPx, direito: RetanguloEmPx): EstadoDoShell =
        EstadoDoShell(
            modo = estado.modo.texto,
            proporcao = estado.proporcao.texto,
            ladoPreferido = estado.ladoPreferido,
            proximoLado = if (estado.proximoEhEsquerda) "left" else "right",
            ultimoLado = estado.ultimoLado,
            usoEsquerdo = estado.usoEsquerda.texto,
            usoDireito = estado.usoDireita.texto,
            esquerdoOcupado = estado.pacoteEsquerda.isNotEmpty(),
            direitoOcupado = estado.pacoteDireita.isNotEmpty(),
            limitesEsquerdo = esquerdo,
            limitesDireito = direito,
        )

    // --- margens da página (faixa -> ponte) ---

    /**
     * As margens que a ponte põe em `onAndroidShellLayout`. A faixa já soma os 22 px CSS de folga
     * superior em `safeTopCss` e a ponte os soma de novo, então aqui sai o que a ponte espera: sem eles.
     */
    fun margensDaPagina(r: ReservasDaFaixa, medidas: MedidasDaTela, topoDaFaixaNaTelaPx: Int?): MargensDaPagina {
        val (popupTopo, popupBase) = limitesDosPopupsCss(medidas, topoDaFaixaNaTelaPx)
        return MargensDaPagina(
            esquerdaCss = r.safeLeftCss,
            direitaCss = r.safeRightCss,
            topoCss = r.safeTopCss - MedidasDaFaixa.FOLGA_SUPERIOR_DA_PAGINA_DP,
            reservaInferiorCss = r.safeBottomCss,
            folgaInferiorCss = r.launcherBottomCss,
            popupTopoCss = popupTopo,
            popupBaseCss = popupBase,
            origemDaPaginaXEmPx = medidas.paginaEsq,
            origemDaPaginaYEmPx = medidas.paginaTopo,
        )
    }

    // --- medidas da tela (ponte -> faixa) ---

    /** Tudo que se lê do Android para as medidas da faixa; [raizLargura] ou [raizAltura] ≤ 0 = ainda sem tamanho. */
    class LeituraDaTela(
        val densidade: Float,
        val larguraRealPx: Int,
        val alturaRealPx: Int,
        val raizEsquerda: Int,
        val raizTopo: Int,
        val raizLargura: Int,
        val raizAltura: Int,
        val larguraDoTrilhoDimenPx: Int,
        val maiorInsetEsquerdoPx: Int,
        val maiorInsetSuperiorPx: Int,
        val alturaBarraStatusPx: Int,
        val ocupacao: ModoDeOcupacao,
    )

    /**
     * A raiz que ainda não foi medida vale a tela inteira (a faixa nunca vê uma página de largura zero
     * e inventa insets). A barra de status só está visível fora do modo `full`.
     */
    fun medidasDaTela(l: LeituraDaTela, modo: ModoDaCasca): MedidasDaTela {
        val medida = l.raizLargura > 0 && l.raizAltura > 0
        val esq = if (medida) l.raizEsquerda else 0
        val topo = if (medida) l.raizTopo else 0
        val dir = if (medida) l.raizEsquerda + l.raizLargura else l.larguraRealPx
        val base = if (medida) l.raizTopo + l.raizAltura else l.alturaRealPx
        return MedidasDaTela(
            densidade = if (l.densidade > 0f) l.densidade else 1f,
            larguraRealPx = l.larguraRealPx,
            alturaRealPx = l.alturaRealPx,
            paginaEsq = esq,
            paginaTopo = topo,
            paginaDir = dir,
            paginaBase = base,
            larguraDoTrilhoDimenPx = l.larguraDoTrilhoDimenPx,
            maiorInsetEsquerdoPx = l.maiorInsetEsquerdoPx,
            maiorInsetSuperiorPx = l.maiorInsetSuperiorPx,
            alturaBarraStatusPx = l.alturaBarraStatusPx,
            barraStatusVisivel = l.ocupacao != ModoDeOcupacao.TELA_CHEIA,
            modo = modo,
        )
    }

    // --- JSON que a faixa lê (a partir do que a ponte já decodificou) ---

    /**
     * O pacote `updateDockIndicators` reduzido aos seis campos que a FAIXA lê (`dockMode`, `uiTheme`,
     * `accent`, `studioScreen`, `desktopStudioOpen`, `railEdit`, `dockStyle`). Os cartões recebem o objeto
     * decodificado inteiro por outro caminho.
     */
    fun jsonDeIndicadoresParaAFaixa(ind: IndicadoresDoDock): String {
        val campos = LinkedHashMap<String, JsonElement>()
        ind.modoDoDock?.let { campos["dockMode"] = JsonPrimitive(it) }
        ind.temaClaro?.let { campos["uiTheme"] = JsonPrimitive(if (it) "light" else "dark") }
        ind.corDeDestaque?.let { campos["accent"] = JsonPrimitive("#%06X".format(it and 0xFFFFFF)) }
        ind.telaDoEstudio?.let { campos["studioScreen"] = JsonPrimitive(it) }
        ind.estudioAberto?.let { campos["desktopStudioOpen"] = JsonPrimitive(it) }
        ind.editandoTrilho?.let { campos["railEdit"] = JsonPrimitive(it) }
        ind.estiloDoDock?.let { campos["dockStyle"] = JsonPrimitive(it) }
        return JsonObject(campos).toString()
    }

    /** `updateCenterFill` já normalizado, no formato que a faixa lê. */
    fun jsonDePreenchimentoParaAFaixa(modo: String?, visivel: Boolean?): String {
        val campos = LinkedHashMap<String, JsonElement>()
        modo?.let { campos["mode"] = JsonPrimitive(it) }
        visivel?.let { campos["visible"] = JsonPrimitive(it) }
        return JsonObject(campos).toString()
    }

    // --- toque do dedo ---

    /** `aoLevantarODedo(noTopoReservado)` vira a altura do toque que a fachada de janelas compara. */
    fun alturaDoToque(noTopoReservado: Boolean): Float = if (noTopoReservado) 0f else Float.MAX_VALUE
}
