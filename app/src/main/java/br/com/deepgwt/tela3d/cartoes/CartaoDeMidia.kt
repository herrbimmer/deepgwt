package br.com.deepgwt.tela3d.cartoes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import br.com.deepgwt.tela3d.faixa.Densidade
import br.com.deepgwt.tela3d.midia.EspacoReservadoDaCapa
import br.com.deepgwt.tela3d.midia.PayloadDeMidia
import br.com.deepgwt.tela3d.midia.RegrasDoCartaoDeMidia
import br.com.deepgwt.tela3d.ponte.CartaoInferior

/** Os quatro ícones dos botões do cartão de mídia (polígonos na grade 24×24). */
internal enum class IconeDeMidia(val poligonos: List<FloatArray>) {
    ANTERIOR(listOf(floatArrayOf(6f, 6f, 8f, 6f, 8f, 18f, 6f, 18f), floatArrayOf(18f, 6f, 18f, 18f, 9.5f, 12f))),
    PROXIMA(listOf(floatArrayOf(16f, 6f, 18f, 6f, 18f, 18f, 16f, 18f), floatArrayOf(6f, 6f, 6f, 18f, 14.5f, 12f))),
    TOCAR(listOf(floatArrayOf(8f, 5f, 8f, 19f, 19f, 12f))),
    PAUSAR(listOf(floatArrayOf(6f, 5f, 10f, 5f, 10f, 19f, 6f, 19f), floatArrayOf(14f, 5f, 18f, 5f, 18f, 19f, 14f, 19f))),
    ;

    fun desenhar(lado: Int, cor: Int): Bitmap {
        val tamanho = maxOf(1, lado)
        val bitmap = Bitmap.createBitmap(tamanho, tamanho, Bitmap.Config.ARGB_8888)
        val tela = Canvas(bitmap)
        val tinta = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cor; style = Paint.Style.FILL }
        val escala = tamanho / 24f
        val caminho = Path()
        for (p in poligonos) {
            for (k in 0 until p.size / 2) {
                if (k == 0) caminho.moveTo(p[0] * escala, p[1] * escala) else caminho.lineTo(p[2 * k] * escala, p[2 * k + 1] * escala)
            }
            caminho.close()
        }
        tela.drawPath(caminho, tinta)
        return bitmap
    }
}

/** O cartão de mídia (392×124): capa, "Mídia", chip do app, título, subtítulo e os três botões. */
internal class CartaoDeMidia(
    contexto: Context,
    anfitriao: AnfitriaoDosCartoes,
    d: Densidade,
    private val servicos: ServicosDoCartao,
    inicial: CartaoInferior?,
) : CartaoDaLinha(contexto, anfitriao, d) {
    private var atual: CartaoInferior? = inicial
    override val descritor: CartaoInferior? get() = atual
    override val larguraEmDp: Int = MedidasDaLinha.LARGURA_MIDIA_DP

    private val capa = ImageView(contexto)
    private val quadroDaCapa = FrameLayout(contexto)
    private val rotulo = Fontes.texto(contexto, 12.5f, 0, true)
    private val chip = ImageView(contexto)
    private val titulo = Fontes.texto(contexto, 17f, 0, true)
    private val subtitulo = Fontes.texto(contexto, 12.5f, 0, false)
    private val anterior = ImageView(contexto)
    private val tocar = ImageView(contexto)
    private val proxima = ImageView(contexto)

    private var payload: PayloadDeMidia? = null
    private var urlDaCapa: String? = null
    private var bitmapDaCapa: Bitmap? = null
    private var temCapa = false

    init {
        val raiz = LinearLayout(contexto).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            setPadding(d.px(14), d.px(14), d.px(14), d.px(14))
        }
        addView(raiz, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // capa
        val raioDaCapa = d.pxF(18f)
        quadroDaCapa.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(vista: View, contorno: Outline) {
                contorno.setRoundRect(0, 0, vista.width, vista.height, raioDaCapa)
            }
        }
        quadroDaCapa.clipToOutline = true
        capa.scaleType = ImageView.ScaleType.CENTER_CROP
        capa.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        quadroDaCapa.addView(capa, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        raiz.addView(quadroDaCapa, LinearLayout.LayoutParams(d.px(96), LinearLayout.LayoutParams.MATCH_PARENT))

        // coluna
        val coluna = LinearLayout(contexto).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d.px(16), d.px(2), d.px(2), d.px(2))
        }
        raiz.addView(coluna, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))

        val linhaDoRotulo = LinearLayout(contexto).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        rotulo.text = "Mídia"
        rotulo.isSingleLine = true
        linhaDoRotulo.addView(rotulo, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        chip.scaleType = ImageView.ScaleType.FIT_CENTER
        chip.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        chip.visibility = View.GONE
        linhaDoRotulo.addView(chip, LinearLayout.LayoutParams(d.px(30), d.px(30)))
        coluna.addView(linhaDoRotulo, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        titulo.isSingleLine = true
        titulo.ellipsize = TextUtils.TruncateAt.END
        coluna.addView(titulo, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        subtitulo.isSingleLine = true
        subtitulo.ellipsize = TextUtils.TruncateAt.END
        coluna.addView(subtitulo, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // botões: ocupam o espaço que sobra na coluna, centrados na vertical
        val botoes = LinearLayout(contexto).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
        }
        botoes.addView(anterior, LinearLayout.LayoutParams(d.px(40), d.px(40)).apply { marginEnd = d.px(10) })
        botoes.addView(tocar, LinearLayout.LayoutParams(d.px(48), d.px(48)).apply { marginEnd = d.px(10) })
        botoes.addView(proxima, LinearLayout.LayoutParams(d.px(40), d.px(40)).apply { marginEnd = d.px(10) })
        coluna.addView(botoes, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        for (b in listOf(anterior, tocar, proxima)) {
            b.scaleType = ImageView.ScaleType.CENTER
            b.isClickable = true
            b.isFocusable = true
        }
        anterior.setOnClickListener { anfitriao.midia.anterior() }
        tocar.setOnClickListener { anfitriao.midia.tocarOuPausar() }
        proxima.setOnClickListener { anfitriao.midia.proxima() }
        setOnClickListener { aoTocar() }
        setOnLongClickListener {
            val c = atual
            if (c != null) {
                servicos.enviar(PoliticaDeToque.aoTocarLongo(c))
                true
            } else {
                false
            }
        }

        contentDescription = inicial?.let { TextosDosCartoes.descricao(it) } ?: TextosDosCartoes.DESCRICAO_DO_CARTAO_DE_MIDIA
        aplicarPayload(null, avisar = false)
        reaplicarTema()
    }

    /** Guarda o descritor (a ação do toque). A descrição de acessibilidade do cartão de mídia não se atualiza. */
    fun definirDescritor(c: CartaoInferior?) {
        atual = c
    }

    // --- payload ---

    fun aplicarPayload(p: PayloadDeMidia?, avisar: Boolean = true) {
        payload = p
        val temFaixa = p?.hasTrack == true
        val tituloTexto = if (p == null) RegrasDoCartaoDeMidia.TEXTO_SEM_FAIXA else RegrasDoCartaoDeMidia.titulo(p)
        val subtituloTexto = if (p == null) RegrasDoCartaoDeMidia.SUBTITULO_SEM_FAIXA else RegrasDoCartaoDeMidia.subtitulo(p)
        // O título da faixa NÃO passa pelo ajuste de caixa: "ABBA" continua "ABBA".
        titulo.text = tituloTexto
        subtitulo.text = subtituloTexto
        subtitulo.visibility = if (RegrasDoCartaoDeMidia.subtituloVisivel(subtituloTexto)) View.VISIBLE else View.INVISIBLE

        val opacidade = if (temFaixa) RegrasDoCartaoDeMidia.OPACIDADE_HABILITADO else RegrasDoCartaoDeMidia.OPACIDADE_DESABILITADO
        for (b in listOf(anterior, tocar, proxima)) {
            b.isEnabled = temFaixa
            b.alpha = opacidade
        }
        anterior.contentDescription = "Previous track"
        proxima.contentDescription = "Next track"
        tocar.contentDescription = if (p?.playing == true) "Pause" else "Play"
        desenharBotoes()

        atualizarCapa(p?.artDataUrl.orEmpty())
        atualizarChip(p)
        if (avisar) anfitriao.midia.aoMudarTocando(p?.playing == true)
    }

    /** Ícone-fonte de novo (o do usuário pode ter mudado). */
    fun redesenharChip() = atualizarChip(payload)

    fun usaOPacote(pacote: String): Boolean = payload?.packageName == pacote

    private fun atualizarChip(p: PayloadDeMidia?) {
        val pacote = p?.packageName.orEmpty().ifEmpty { null }
        val icone = if (p == null) null else anfitriao.iconeDeOrigem(pacote, p.appLabel.ifEmpty { null })
        chip.setImageDrawable(icone)
        chip.visibility = if (icone == null) View.GONE else View.VISIBLE
    }

    private fun atualizarCapa(url: String) {
        if (url != urlDaCapa) {
            urlDaCapa = url
            bitmapDaCapa = if (RegrasDoCartaoDeMidia.ehDataUrl(url)) decodificar(url) else null
        }
        val b = bitmapDaCapa
        if (b != null && !b.isRecycled) {
            temCapa = true
            capa.setPadding(0, 0, 0, 0)
            capa.scaleType = ImageView.ScaleType.CENTER_CROP
            capa.setImageBitmap(b)
        } else {
            temCapa = false
            val m = d.px(RegrasDoCartaoDeMidia.MARGEM_DO_ESPACO_RESERVADO_DP)
            capa.setPadding(m, m, m, m)
            capa.scaleType = ImageView.ScaleType.FIT_CENTER
            capa.setImageBitmap(EspacoReservadoDaCapa.criar(d.valor, tema.corDeRotulo))
        }
    }

    private fun decodificar(url: String): Bitmap? {
        val base64 = RegrasDoCartaoDeMidia.base64DaDataUrl(url) ?: return null
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, limites)
            val alvo = d.px(96) * 2
            var amostra = 1
            while (limites.outWidth / (amostra * 2) >= alvo && limites.outHeight / (amostra * 2) >= alvo) amostra *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = amostra })
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    // --- botões e tema ---

    private fun desenharBotoes() {
        val claro = tema.claro
        val icones = listOf(
            Triple(anterior, IconeDeMidia.ANTERIOR, tema.corDeRotulo),
            Triple(proxima, IconeDeMidia.PROXIMA, tema.corDeRotulo),
            Triple(tocar, if (payload?.playing == true) IconeDeMidia.PAUSAR else IconeDeMidia.TOCAR, CoresDosCartoes.iconeDoBotaoCentral(claro)),
        )
        for ((vista, icone, cor) in icones) {
            val lado = (0.46f * (if (vista === tocar) 48 else 40) * d.valor).toInt()
            vista.setImageBitmap(icone.desenhar(lado, cor))
        }
        anterior.background = fundoLateral(claro)
        proxima.background = fundoLateral(claro)
        tocar.background = fundoCentral(claro)
    }

    private fun fundoLateral(claro: Boolean): StateListDrawable {
        val pressionado = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(CoresDosCartoes.botaoLateralPressionado(claro)) }
        val repouso = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0) }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressionado)
            addState(intArrayOf(), repouso)
        }
    }

    private fun fundoCentral(claro: Boolean): StateListDrawable {
        val cor = CoresDosCartoes.discoDoBotaoCentral(claro)
        val pressionado = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor((cor and 0x00FFFFFF) or (200 shl 24)) }
        val repouso = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(cor) }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressionado)
            addState(intArrayOf(), repouso)
        }
    }

    override fun reaplicarTema() {
        aplicarFundo(0)
        quadroDaCapa.background = tema.materialPlaca(false)
        rotulo.setTextColor(tema.corDeRotuloMuted)
        titulo.setTextColor(tema.corDeRotulo)
        subtitulo.setTextColor(tema.corDeRotuloMuted)
        desenharBotoes()
        if (!temCapa) atualizarCapa(urlDaCapa.orEmpty())
    }

    private fun aoTocar() {
        val p = payload
        val acao = atual?.acao.orEmpty()
        val decisao = PoliticaDeToque.aoTocarNaMidia(
            podeAbrir = p != null && RegrasDoCartaoDeMidia.toqueAbreOAplicativo(p),
            pacote = p?.packageName.orEmpty(),
            acaoDoDescritor = acao,
        )
        when (decisao) {
            is AcaoDoToque.AbrirApp -> anfitriao.abrirApp(decisao.pacote, RegrasDoCartaoDeMidia.titulo(p ?: PayloadDeMidia()))
            is AcaoDoToque.Comando -> servicos.enviar(decisao.comando)
            else -> Unit
        }
    }
}
