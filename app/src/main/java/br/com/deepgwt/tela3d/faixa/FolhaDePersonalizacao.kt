package br.com.deepgwt.tela3d.faixa

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * A folha "PERSONALIZAR" (A10): nome, ícone, cor do ícone e fundo do item. Fica acima do item, com a base
 * sempre 8 acima dele; se o conteúdo cresce ao escolher um ícone, ela cresce para cima e rola por dentro.
 */
class FolhaDePersonalizacao(
    private val atividade: Activity,
    private val tema: TemaDoCromoPadrao,
    private val menus: MenusDaFaixa,
    private val sobreposicoes: SobreposicoesDoDock,
    private val raiz: () -> View?,
) {
    private val d = Densidade(atividade)

    /** Estado da folha aberta; vive só enquanto ela está na tela. */
    private inner class Sessao(
        val chave: String,
        val ancora: View,
        val larguraPx: Int,
        val colunas: Int,
        val aoTerminar: () -> Unit,
    ) {
        var icone: String? = sobreposicoes.icone(chave)
        var cor: String = sobreposicoes.cor(chave) ?: SobreposicoesDoDock.COR_PADRAO
        var fundo: String = sobreposicoes.fundo(chave) ?: SobreposicoesDoDock.FUNDO_PADRAO
        val celulas = LinkedHashMap<String?, View>()
        val bolinhasDeCor = ArrayList<Pair<String, View>>()
        val bolinhasDeFundo = ArrayList<Pair<String, View>>()
        lateinit var campoDeNome: EditText
        lateinit var secaoDeCores: View
        lateinit var rolagem: ScrollView
        lateinit var painel: LinearLayout
        lateinit var popup: PopupWindow
    }

    /**
     * Mostra a folha para [chave] (pacote ou `__gwm_hub`). [nomeOriginal] é a dica do campo de nome;
     * [aoTerminar] roda depois de SAVE ou RESET, para a faixa refazer o item.
     */
    fun mostrar(ancora: View, chave: String, nomeOriginal: String, nomeAtual: String?, aoTerminar: () -> Unit) {
        val larguraDaTelaPx = atividade.resources.displayMetrics.widthPixels
        val colunas = GeometriaDaFolha.colunas((larguraDaTelaPx / d.valor).toInt())
        val larguraPx = d.px(GeometriaDaFolha.larguraDp(colunas))
        val sessao = Sessao(chave, ancora, larguraPx, colunas, aoTerminar)

        val painel = LinearLayout(atividade)
        painel.orientation = LinearLayout.VERTICAL
        painel.background = tema.painelDePopup()
        painel.elevation = d.px(12).toFloat()
        Estilos.arredondar(painel, d.pxF(14f), recortar = true)
        val p = d.px(GeometriaDaFolha.PREENCHIMENTO_DP)
        painel.setPadding(p, p, p, p)
        sessao.painel = painel

        painel.addView(kicker("PERSONALIZAR"))
        painel.addView(campoDeNome(sessao, nomeOriginal, nomeAtual), larguraTotal())
        painel.addView(kicker("ÍCONE", topo = 12))
        painel.addView(grade(sessao))
        sessao.secaoDeCores = secaoDeCores(sessao)
        painel.addView(sessao.secaoDeCores, larguraTotal().apply { topMargin = d.px(8) })
        painel.addView(linhaDeAcoes(sessao), larguraTotal())
        sessao.secaoDeCores.visibility = if (sessao.icone != null) View.VISIBLE else View.GONE
        atualizarCelulas(sessao)

        val rolagem = ScrollView(atividade)
        rolagem.overScrollMode = View.OVER_SCROLL_NEVER
        rolagem.addView(painel, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        sessao.rolagem = rolagem

        val popup = menus.criarPopup(rolagem, largura = larguraPx)
        popup.inputMethodMode = PopupWindow.INPUT_METHOD_NEEDED
        @Suppress("DEPRECATION")
        popup.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        sessao.popup = popup

        val posicao = calcularPosicao(sessao)
        popup.height = posicao.altura
        menus.exibir(TipoDePopup.FOLHA, popup) { janela ->
            val pai = raiz() ?: return@exibir
            janela.showAtLocation(pai, Gravity.TOP or Gravity.START, posicao.x, posicao.y)
        }
    }

    /** Fecha a folha, se aberta. */
    fun fechar() = menus.fechar(TipoDePopup.FOLHA)

    // --- posição ---

    private fun calcularPosicao(sessao: Sessao): PosicaoDaFolha {
        sessao.painel.measure(
            View.MeasureSpec.makeMeasureSpec(sessao.larguraPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val local = IntArray(2)
        sessao.ancora.getLocationInWindow(local)
        return GeometriaDaFolha.calcular(
            larguraDaTela = atividade.resources.displayMetrics.widthPixels,
            itemEsq = local[0],
            itemTopo = local[1],
            larguraPx = sessao.larguraPx,
            alturaDoConteudoPx = sessao.painel.measuredHeight,
            alturaMaximaPx = d.px(GeometriaDaFolha.ALTURA_MAXIMA_DP),
            folgaLateralPx = d.px(GeometriaDaFolha.FOLGA_LATERAL_DP),
            folgaAcimaPx = d.px(GeometriaDaFolha.FOLGA_ACIMA_DO_ITEM_DP),
        )
    }

    /** O conteúdo mudou de altura: mantém a base 8 acima do item e deixa o resto rolar. */
    private fun reposicionar(sessao: Sessao) {
        val posicao = calcularPosicao(sessao)
        try {
            sessao.popup.update(posicao.x, posicao.y, posicao.largura, posicao.altura)
        } catch (e: Exception) {
            // a janela já fechou
        }
    }

    // --- partes ---

    private fun larguraTotal() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun kicker(texto: String, topo: Int = 0): TextView = Estilos.texto(atividade, 11f, tema.corDeRotuloMuted, medio = true).apply {
        text = texto
        letterSpacing = 0.08f
        setPadding(0, d.px(topo), 0, d.px(6))
    }

    private fun campoDeNome(sessao: Sessao, nomeOriginal: String, nomeAtual: String?): EditText {
        val campo = EditText(atividade)
        campo.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
        campo.typeface = Estilos.NORMAL
        campo.setSingleLine(true)
        campo.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        campo.imeOptions = EditorInfo.IME_ACTION_DONE
        campo.hint = nomeOriginal
        campo.setHintTextColor(tema.corDeRotuloMuted)
        campo.setTextColor(tema.corDeRotulo)
        campo.setText(nomeAtual.orEmpty())
        campo.background = tema.fundoDeCampo()
        campo.setPadding(d.px(12), d.px(10), d.px(12), d.px(10))
        campo.ellipsize = TextUtils.TruncateAt.END
        sessao.campoDeNome = campo
        return campo
    }

    private fun grade(sessao: Sessao): View {
        val coluna = LinearLayout(atividade)
        coluna.orientation = LinearLayout.VERTICAL
        val chaves: List<String?> = listOf<String?>(null) + sobreposicoes.slugs.map { it.first }
        var linha: LinearLayout? = null
        for ((i, slug) in chaves.withIndex()) {
            if (i % sessao.colunas == 0) {
                linha = LinearLayout(atividade).also { coluna.addView(it) }
            }
            val celula = celula(sessao, slug)
            sessao.celulas[slug] = celula
            linha?.addView(
                celula,
                LinearLayout.LayoutParams(d.px(CELULA_DP), d.px(CELULA_DP)).apply {
                    rightMargin = d.px(6)
                    bottomMargin = d.px(6)
                },
            )
        }
        return coluna
    }

    private fun celula(sessao: Sessao, slug: String?): View {
        val vista: View = if (slug == null) {
            Estilos.texto(atividade, 8f, tema.corDeRotulo).apply {
                text = "Padrão"
                gravity = Gravity.CENTER
                contentDescription = "Padrão"
            }
        } else {
            ImageView(atividade).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(d.px(7), d.px(7), d.px(7), d.px(7))
                contentDescription = sobreposicoes.slugs.firstOrNull { it.first == slug }?.second
            }
        }
        vista.isClickable = true
        vista.isFocusable = true
        vista.setOnClickListener { escolherIcone(sessao, slug) }
        return vista
    }

    private fun escolherIcone(sessao: Sessao, slug: String?) {
        val tinhaCores = sessao.icone != null
        sessao.icone = slug
        sessao.secaoDeCores.visibility = if (slug != null) View.VISIBLE else View.GONE
        atualizarCelulas(sessao)
        if (tinhaCores != (slug != null)) {
            sessao.rolagem.post { reposicionar(sessao) }
        }
    }

    /** Refaz fundo, contorno e ícone de todas as células com a escolha atual. */
    private fun atualizarCelulas(sessao: Sessao) {
        val corDoIcone = sessao.cor
        val fundo = CoresDoCromo.lerCorOuPadrao(sessao.fundo, FUNDO_PADRAO_ARGB)
        for ((slug, vista) in sessao.celulas) {
            val escolhida = slug == sessao.icone
            val traco = if (escolhida) maxOf(1, d.pxF(2.5f).roundToInt()) else d.contorno()
            val corDoTraco = if (escolhida) tema.acento else tema.corDoContornoDeCelula
            val caixa = GradientDrawable()
            caixa.cornerRadius = d.pxF(10f)
            caixa.setColor(if (slug == null) corDeCampo() else fundo)
            caixa.setStroke(traco, corDoTraco)
            vista.background = caixa
            if (slug != null && vista is ImageView) {
                vista.setImageDrawable(sobreposicoes.drawableDoSlug(slug, corDoIcone))
            }
        }
        atualizarBolinhas(sessao)
    }

    private fun corDeCampo(): Int = if (tema.claro) 0x0F25303B else 0x14FFFFFF

    private fun secaoDeCores(sessao: Sessao): View {
        val ladoDaBolinha = d.px(GeometriaDaFolha.ladoDaBolinhaDp((sessao.larguraPx / d.valor).toInt()))
        val linha = LinearLayout(atividade)
        linha.orientation = LinearLayout.HORIZONTAL
        val esquerda = colunaDeCores(sessao, "COR", ladoDaBolinha, sessao.bolinhasDeCor) { sessao.cor = it }
        val direita = colunaDeCores(sessao, "FUNDO", ladoDaBolinha, sessao.bolinhasDeFundo) { sessao.fundo = it }
        linha.addView(
            esquerda,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = d.px(GeometriaDaFolha.VAO_ENTRE_COLUNAS_DP) },
        )
        linha.addView(direita, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return linha
    }

    private fun colunaDeCores(
        sessao: Sessao,
        titulo: String,
        lado: Int,
        destino: MutableList<Pair<String, View>>,
        aoEscolher: (String) -> Unit,
    ): View {
        val coluna = LinearLayout(atividade)
        coluna.orientation = LinearLayout.VERTICAL
        coluna.addView(kicker(titulo))
        val fileira = LinearLayout(atividade)
        fileira.orientation = LinearLayout.HORIZONTAL
        for ((i, cor) in sobreposicoes.cores.withIndex()) {
            val bolinha = View(atividade)
            bolinha.isClickable = true
            bolinha.isFocusable = true
            bolinha.contentDescription = cor
            bolinha.setOnClickListener {
                aoEscolher(cor)
                atualizarCelulas(sessao)
            }
            destino.add(cor to bolinha)
            fileira.addView(
                bolinha,
                LinearLayout.LayoutParams(lado, lado).apply {
                    if (i < sobreposicoes.cores.size - 1) rightMargin = d.px(GeometriaDaFolha.VAO_DAS_BOLINHAS_DP)
                },
            )
        }
        coluna.addView(fileira)
        return coluna
    }

    private fun atualizarBolinhas(sessao: Sessao) {
        pintarBolinhas(sessao.bolinhasDeCor, sessao.cor)
        pintarBolinhas(sessao.bolinhasDeFundo, sessao.fundo)
    }

    private fun pintarBolinhas(bolinhas: List<Pair<String, View>>, escolhida: String) {
        for ((cor, vista) in bolinhas) {
            val disco = GradientDrawable()
            disco.shape = GradientDrawable.OVAL
            disco.setColor(CoresDoCromo.lerCorOuPadrao(cor, 0xFFFFFFFF.toInt()))
            disco.setStroke(d.contorno(), 0x38FFFFFF)
            val marcada = cor.equals(escolhida, ignoreCase = true)
            if (marcada) {
                val pelicula = GradientDrawable()
                pelicula.shape = GradientDrawable.OVAL
                pelicula.setColor(0x66FFFFFF)
                vista.background = LayerDrawable(arrayOf(disco, pelicula))
            } else {
                vista.background = disco
            }
            vista.alpha = if (marcada) 1f else 0.5f
        }
    }

    private fun linhaDeAcoes(sessao: Sessao): View {
        val linha = LinearLayout(atividade)
        linha.orientation = LinearLayout.HORIZONTAL
        linha.setPadding(0, d.px(14), 0, 0)
        val restaurar = botao("RESET", tema.corDeRotulo, selecionado = false, separado = true) {
            sobreposicoes.limpar(sessao.chave)
            fechar()
            sessao.aoTerminar()
        }
        val salvar = botao("SAVE", tema.acento, selecionado = true, separado = false) {
            val nome = sessao.campoDeNome.text?.toString().orEmpty().trim()
            val icone = sessao.icone
            sobreposicoes.salvar(
                sessao.chave,
                nome.ifEmpty { null },
                icone,
                if (icone != null) sessao.cor else null,
                if (icone != null) sessao.fundo else null,
            )
            fechar()
            sessao.aoTerminar()
        }
        linha.addView(restaurar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = d.px(4); rightMargin = d.px(4)
        })
        linha.addView(salvar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = d.px(4); rightMargin = d.px(4)
        })
        return linha
    }

    private fun botao(texto: String, cor: Int, selecionado: Boolean, separado: Boolean, aoTocar: () -> Unit): TextView =
        Estilos.texto(atividade, 14f, cor).apply {
            this.text = texto
            gravity = Gravity.CENTER
            setPadding(d.px(16), d.px(12), d.px(16), d.px(12))
            background = tema.fundoDeItemDeMenu(selecionado, separado)
            isClickable = true
            isFocusable = true
            setOnClickListener { aoTocar() }
        }

    companion object {
        const val CELULA_DP = 48
        private val FUNDO_PADRAO_ARGB: Int = 0xFF3D4650.toInt()
    }
}
