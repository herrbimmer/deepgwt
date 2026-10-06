package br.com.deepgwt.tela3d.cartoes

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import br.com.deepgwt.R
import br.com.deepgwt.tela3d.faixa.CoresDoCromo
import br.com.deepgwt.tela3d.faixa.TemaDoCromo
import br.com.deepgwt.tela3d.ponte.CartaoInferior
import kotlin.math.max
import kotlin.math.min

/**
 * Desenha o gráfico de cada tipo de cartão (AP3) numa área W×H em px. Guarda as canetas e os caches
 * (glifo vetorial, vetores de manobra) para não alocar a cada quadro. Só na thread principal.
 */
internal class DesenhoDosGraficos(private val contexto: Context, private val densidade: Float) {
    private val d = densidade
    private val tinta = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tintaBitmap = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val rf = RectF()
    private val caminho = Path()
    private val matriz = Matrix()
    private val origem = android.graphics.Rect()
    private val destino = RectF()

    private val medio = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val medioNegrito = Typeface.create("sans-serif-medium", Typeface.BOLD)
    private val normal = Typeface.create("sans-serif", Typeface.NORMAL)
    private val negrito = Typeface.create("sans-serif", Typeface.BOLD)

    private var glifoEmTexto: String? = null
    private var glifoComandos: List<ComandoDeCaminho> = emptyList()
    private val vetoresDeManobra = HashMap<String, Drawable?>()

    /** Dados do instante da pulsação do fluxo de energia (relógio de sistema em segundos e se pode pulsar). */
    class Pulso(val segundos: Double, val ativa: Boolean)

    /** Devolve se o gráfico precisa de repintura contínua (pulsação da bateria). */
    fun desenhar(canvas: Canvas, w: Float, h: Float, c: CartaoInferior, tema: TemaDoCromo, pulso: Pulso): Boolean {
        when (c.id) {
            "climate" -> ventoinha(canvas, w, h, tema)
            "consumption" -> colunas(canvas, w, h, c, tema)
            "range" -> { anel(canvas, w, h, c, tema); raio(canvas, w, h, tema) }
            "desktops" -> grade(canvas, w, h, tema)
            "driveMode" -> { modoDeCondutor(canvas, w, h, c, tema); pontinhos(canvas, w, h, c, tema) }
            "powerMode" -> { modoDePotencia(canvas, w, h, c, tema); pontinhos(canvas, w, h, c, tema) }
            "regen" -> { regeneracao(canvas, w, h, c, tema); pontinhos(canvas, w, h, c, tema) }
            "navigation" -> navegacao(canvas, w, h, c, tema)
            "power" -> return fluxoDeEnergia(canvas, w, h, c, tema, pulso)
            else -> anel(canvas, w, h, c, tema)
        }
        return false
    }

    // --- canetas ---

    private fun preencher(cor: Int) {
        tinta.reset(); tinta.isAntiAlias = true
        tinta.style = Paint.Style.FILL
        tinta.color = cor
    }

    private fun traco(cor: Int, espessura: Float, redondo: Boolean = true) {
        tinta.reset(); tinta.isAntiAlias = true
        tinta.style = Paint.Style.STROKE
        tinta.color = cor
        tinta.strokeWidth = espessura
        if (redondo) {
            tinta.strokeCap = Paint.Cap.ROUND
            tinta.strokeJoin = Paint.Join.ROUND
        }
    }

    private fun texto(cor: Int, tamanho: Float, fonte: Typeface, alinhamento: Paint.Align = Paint.Align.LEFT) {
        tinta.reset(); tinta.isAntiAlias = true
        tinta.style = Paint.Style.FILL
        tinta.color = cor
        tinta.textSize = tamanho
        tinta.typeface = fonte
        tinta.textAlign = alinhamento
    }

    private fun largura(texto: String, tamanho: Float, fonte: Typeface): Float {
        tinta.reset(); tinta.isAntiAlias = true
        tinta.textSize = tamanho
        tinta.typeface = fonte
        return tinta.measureText(texto)
    }

    // --- clima: ventoinha ---

    private fun ventoinha(canvas: Canvas, w: Float, h: Float, tema: TemaDoCromo) {
        val v = GeometriaDosGraficos.ventoinha(w, h)
        preencher(CoresDosCartoes.discoDaVentoinha(tema.claro))
        canvas.drawCircle(v.cx, v.cy, v.raioDoDisco, tinta)
        preencher(tema.acento)
        for (angulo in v.angulosDasPas) {
            canvas.save()
            canvas.translate(v.cx, v.cy)
            canvas.rotate(angulo)
            rf.set(v.esqDaPa, v.topoDaPa, v.dirDaPa, v.baseDaPa)
            canvas.drawRoundRect(rf, v.raioDaPa, v.raioDaPa, tinta)
            canvas.restore()
        }
        preencher(CoresDosCartoes.cuboDaVentoinha(tema.claro))
        canvas.drawCircle(v.cx, v.cy, v.raioDoCubo, tinta)
    }

    // --- consumo: colunas ---

    private fun colunas(canvas: Canvas, w: Float, h: Float, c: CartaoInferior, tema: TemaDoCromo) {
        for (col in GeometriaDosGraficos.colunasDeEnergia(c.barrasDeEnergia, w, h, d)) {
            val cor = when (col.cor) {
                CorDaColuna.ACENTO -> tema.acento
                CorDaColuna.COM_VALOR -> CoresDosCartoes.colunaComValor(tema.claro)
                CorDaColuna.FRACA -> CoresDosCartoes.fraco(tema.claro)
            }
            preencher(cor)
            rf.set(col.retangulo.esq, col.retangulo.topo, col.retangulo.dir, col.retangulo.base)
            canvas.drawRoundRect(rf, col.raio, col.raio, tinta)
        }
    }

    // --- autonomia / estado / desconhecido: anel ---

    private fun anel(canvas: Canvas, w: Float, h: Float, c: CartaoInferior, tema: TemaDoCromo) {
        val a = GeometriaDosGraficos.anel(w, h, d)
        traco(CoresDosCartoes.fraco(tema.claro), a.espessura)
        canvas.drawCircle(a.cx, a.cy, a.raioDoTraco, tinta)
        if (c.progresso > 0) {
            traco(tema.acento, a.espessura)
            val r = a.raioDoTraco
            rf.set(a.cx - r, a.cy - r, a.cx + r, a.cy + r)
            canvas.drawArc(rf, -90f, GeometriaDosGraficos.varreduraDoProgresso(c.progresso), false, tinta)
        }
    }

    private fun raio(canvas: Canvas, w: Float, h: Float, tema: TemaDoCromo) {
        val a = GeometriaDosGraficos.anel(w, h, d)
        val vertices = GeometriaDosGraficos.raio(a.diametro)
        caminho.reset()
        vertices.forEachIndexed { i, p ->
            if (i == 0) caminho.moveTo(a.cx + p.x, a.cy + p.y) else caminho.lineTo(a.cx + p.x, a.cy + p.y)
        }
        caminho.close()
        preencher(tema.acento)
        canvas.drawPath(caminho, tinta)
    }

    // --- páginas: grade 2×2 ---

    private fun grade(canvas: Canvas, w: Float, h: Float, tema: TemaDoCromo) {
        val g = GeometriaDosGraficos.grade(w, h)
        g.celulas.forEachIndexed { i, r ->
            preencher(if (i == 0) tema.acento else CoresDosCartoes.celulaDaGrade(tema.claro))
            rf.set(r.esq, r.topo, r.dir, r.base)
            canvas.drawRoundRect(rf, g.raioDosCantos, g.raioDosCantos, tinta)
        }
    }

    // --- modos de condução ---

    private fun modoDeCondutor(canvas: Canvas, w: Float, h: Float, c: CartaoInferior, tema: TemaDoCromo) {
        val conhecido = GeometriaDosGraficos.estadoConhecido(c.estado)
        val cor = if (conhecido) tema.acento else CoresDosCartoes.tracoApagado(tema.claro)
        if (c.textoDoGlifo.isEmpty()) {
            val lado = GeometriaDosGraficos.ladoDoGlifo(w, h)
            glifo(canvas, c.glifo, lado, 0.5f * w, 0.44f * h, cor, GeometriaDosGraficos.espessuraDoGlifo(lado, d))
        } else {
            etiqueta(canvas, w, h, 0.5f, 0.44f, 0.6f, 0.4f, c.textoDoGlifo, cor, conhecido)
        }
    }

    private fun modoDePotencia(canvas: Canvas, w: Float, h: Float, c: CartaoInferior, tema: TemaDoCromo) {
        val rotulo = GeometriaDosGraficos.textoDaPotencia(c.estado)
        if (rotulo != null) {
            etiqueta(canvas, w, h, 0.5f, 0.46f, 0.74f, 0.46f, rotulo, tema.acento, true)
        } else {
            etiqueta(canvas, w, h, 0.5f, 0.46f, 0.74f, 0.46f, "--", CoresDosCartoes.tracoApagado(tema.claro), false)
        }
    }

    private fun etiqueta(
        canvas: Canvas, w: Float, h: Float, cx: Float, cy: Float, lw: Float, lh: Float,
        textoDaEtiqueta: String, cor: Int, conhecido: Boolean,
    ) {
        val e = GeometriaDosGraficos.etiqueta(w, h, d, cx, cy, lw, lh)
        preencher(CoresDoCromo.comAlfa(cor, GeometriaDosGraficos.alfaDaEtiqueta(conhecido)))
        rf.set(e.retangulo.esq, e.retangulo.topo, e.retangulo.dir, e.retangulo.base)
        canvas.drawRoundRect(rf, e.raioDosCantos, e.raioDosCantos, tinta)
        traco(cor, e.espessuraDoContorno, redondo = false)
        canvas.drawRoundRect(rf, e.raioDosCantos, e.raioDosCantos, tinta)

        tinta.reset(); tinta.isAntiAlias = true
        tinta.typeface = medio
        tinta.letterSpacing = 0.06f
        tinta.textSize = e.tamanhoDoTexto
        val medida = tinta.measureText(textoDaEtiqueta)
        tinta.textSize = GeometriaDosGraficos.tamanhoDoTextoDaEtiqueta(e.tamanhoDoTexto, medida, e.retangulo.largura)
        tinta.color = cor
        tinta.style = Paint.Style.FILL
        tinta.textAlign = Paint.Align.CENTER
        val metricas = tinta.fontMetrics
        // Linha de base no centro vertical: o texto fica centrado pela altura das letras.
        val base = e.retangulo.centroY - (metricas.ascent + metricas.descent) / 2f
        canvas.drawText(textoDaEtiqueta, e.retangulo.centroX, base, tinta)
    }

    private fun glifo(canvas: Canvas, texto: String, lado: Float, cx: Float, cy: Float, cor: Int, espessura: Float) {
        if (texto.isEmpty()) return
        if (texto != glifoEmTexto) {
            glifoEmTexto = texto
            glifoComandos = CaminhoVetorial.ler(texto)
        }
        if (glifoComandos.isEmpty()) return
        caminho.reset()
        for (k in glifoComandos) {
            when (k) {
                is ComandoDeCaminho.MoverPara -> caminho.moveTo(k.x, k.y)
                is ComandoDeCaminho.LinhaPara -> caminho.lineTo(k.x, k.y)
                is ComandoDeCaminho.Curva -> caminho.cubicTo(k.x1, k.y1, k.x2, k.y2, k.x, k.y)
                ComandoDeCaminho.Fechar -> caminho.close()
            }
        }
        val escala = lado / 24f
        matriz.reset()
        matriz.postScale(escala, escala)
        matriz.postTranslate(cx - lado / 2f, cy - lado / 2f)
        caminho.transform(matriz)
        traco(cor, espessura)
        canvas.drawPath(caminho, tinta)
    }

    // --- regeneração ---

    private fun regeneracao(canvas: Canvas, w: Float, h: Float, c: CartaoInferior, tema: TemaDoCromo) {
        val g = GeometriaDosGraficos.barrasDeRegeneracao(w, h, d)
        val nivel = GeometriaDosGraficos.nivelDaRegeneracao(c.estado)
        g.barras.forEachIndexed { i, r ->
            preencher(if (i < nivel) tema.acento else CoresDosCartoes.tracoApagado(tema.claro))
            rf.set(r.esq, r.topo, r.dir, r.base)
            canvas.drawRoundRect(rf, g.raioDosCantos, g.raioDosCantos, tinta)
        }
        if (c.estado == "onepedal") {
            traco(tema.acento, g.espessuraDaLinha)
            canvas.drawLine(g.linhaUmPedal[0], g.linhaUmPedal[1], g.linhaUmPedal[2], g.linhaUmPedal[3], tinta)
        }
    }

    private fun pontinhos(canvas: Canvas, w: Float, h: Float, c: CartaoInferior, tema: TemaDoCromo) {
        val p = GeometriaDosGraficos.pontinhos(w, h, d)
        val destacado = PontosDePasso.indiceDestacado(c.progresso)
        for (i in 0 until PontosDePasso.QUANTIDADE) {
            if (i == destacado) {
                preencher(tema.acento)
                canvas.drawCircle(p.cx[i], p.cy, p.raioDestacado, tinta)
            } else {
                preencher(CoresDosCartoes.tracoApagado(tema.claro))
                canvas.drawCircle(p.cx[i], p.cy, p.raio, tinta)
            }
        }
    }

    // --- navegação ---

    private fun navegacao(canvas: Canvas, w: Float, h: Float, c: CartaoInferior, tema: TemaDoCromo) {
        val claro = tema.claro
        val forte = CoresDosCartoes.textoForte(claro)
        val apagado = CoresDosCartoes.tracoApagado(claro)
        val k = min(0.3f * w, 0.95f * h)
        val x0 = k + 0.035f * w
        val larguraDoTexto = w - x0 - 0.03f * w
        val mostraManobra = RegrasDaNavegacao.mostraManobra(c.estado, c.glifo)

        if (mostraManobra) {
            val lado = min(0.78f * k, 0.58f * h)
            val cx = 0.5f * k
            val cy = 0.36f * h
            if (RegrasDaNavegacao.temVetor(c.estado)) {
                vetorDeManobra(c.estado)?.let { v ->
                    v.setTint(tema.acento)
                    v.setBounds(
                        Math.round(cx - lado / 2f), Math.round(cy - lado / 2f),
                        Math.round(cx + lado / 2f), Math.round(cy + lado / 2f),
                    )
                    v.draw(canvas)
                }
            } else {
                glifo(canvas, c.glifo, lado, cx, cy, tema.acento, max(2.4f * d, 0.08f * lado))
            }
            if (c.metricaA.isNotEmpty()) {
                texto(forte, max(13f * d, 0.2f * h), medio, Paint.Align.CENTER)
                canvas.drawText(c.metricaA, 0.5f * k, 0.88f * h, tinta)
            }
            if (larguraDoTexto > 8f * d) {
                val tamanho = max(16f * d, 0.26f * h)
                val rua = CorteDeTexto.cortar(c.primario, larguraDoTexto) { largura(it, tamanho, medio) }
                texto(forte, tamanho, medio)
                canvas.drawText(rua, x0, 0.32f * h, tinta)
                val linha = MetricasDaNavegacao.calcular(
                    c.navRestante, c.navDuracao, c.navChegada, larguraDoTexto, h, d,
                ) { t, tam -> largura(t, tam, medio) }
                for (item in linha.itens) {
                    texto(if (item.destaque) tema.acento else forte, linha.tamanhoDoValor, medio)
                    canvas.drawText(item.valor, x0 + item.x, 0.68f * h, tinta)
                    texto(apagado, linha.tamanhoDoRotulo, medio)
                    canvas.drawText(item.rotulo, x0 + item.x, 0.86f * h, tinta)
                }
            }
        } else {
            val f3 = 0.92f * k
            val f4 = 0.78f * h
            val ox = 0.04f * k
            val oy = 0.12f * h
            fun px(a: Float) = ox + a * f3
            fun py(b: Float) = oy + b * f4
            caminho.reset()
            caminho.moveTo(px(0.12f), py(0.88f))
            caminho.cubicTo(px(0.18f), py(0.48f), px(0.48f), py(0.66f), px(0.52f), py(0.32f))
            caminho.cubicTo(px(0.55f), py(0.12f), px(0.78f), py(0.14f), px(0.92f), py(0.14f))
            val corFina = if (c.estado == "unavailable") apagado else tema.acento
            traco(apagado, max(3.5f * d, 0.018f * w))
            canvas.drawPath(caminho, tinta)
            traco(corFina, max(1.8f * d, 0.01f * w))
            canvas.drawPath(caminho, tinta)
            preencher(corFina)
            canvas.drawCircle(px(0.12f), py(0.88f), max(3f * d, 0.012f * w), tinta)

            if (larguraDoTexto > 8f * d) {
                fun linha(textoDaLinha: String, tamanho: Float, cor: Int, fonte: Typeface, base: Float) {
                    if (textoDaLinha.isEmpty()) return
                    val cortado = CorteDeTexto.cortar(textoDaLinha, larguraDoTexto) { largura(it, tamanho, fonte) }
                    texto(cor, tamanho, fonte)
                    canvas.drawText(cortado, x0, base, tinta)
                }
                linha(c.primario, max(16f * d, 0.26f * h), forte, medio, 0.32f * h)
                linha(c.navCidadeOciosa, max(12f * d, 0.17f * h), apagado, medio, 0.54f * h)
                linha(c.navAcaoOciosa, max(10f * d, 0.13f * h), apagado, normal, 0.76f * h)
            }
        }
    }

    private fun vetorDeManobra(estado: String): Drawable? = vetoresDeManobra.getOrPut(estado) {
        val recurso = when (estado) {
            "turn_right" -> R.drawable.ic_tbt_turn_right
            "turn_left" -> R.drawable.ic_tbt_turn_left
            "straight" -> R.drawable.ic_tbt_straight
            "uturn" -> R.drawable.ic_tbt_uturn
            "roundabout" -> R.drawable.ic_tbt_roundabout
            "fork" -> R.drawable.ic_tbt_fork
            "merge" -> R.drawable.ic_tbt_merge
            else -> 0
        }
        if (recurso == 0) null else contexto.getDrawable(recurso)?.mutate()
    }

    // --- fluxo de energia ---

    private fun fluxoDeEnergia(canvas: Canvas, w: Float, h: Float, c: CartaoInferior, tema: TemaDoCromo, pulso: Pulso): Boolean {
        val variante = VarianteDoFluxo.deId(c.variantePotencia)
        val e = GeometriaDoFluxo.estado(c.estado, variante, c.textoDoGlifo, c.sabeSoc, c.soc)
        val pulsando = pulso.ativa && e.pulsa

        canvas.save()
        rf.set(0f, 0f, w, h)
        caminho.reset()
        caminho.addRoundRect(rf, 0.12f * h, 0.12f * h, Path.Direction.CW)
        canvas.clipPath(caminho)
        canvas.translate(w / 2f, h / 2f)
        val escala = GeometriaDoFluxo.escala(w, h)
        canvas.scale(escala, escala)
        canvas.rotate(90f)
        canvas.translate(-175f, -385f)

        // chassi
        val atlas = ImagensDosCartoes.atlas
        if (atlas != null && !atlas.isRecycled) {
            origem.set(
                variante.atlasX, VarianteDoFluxo.ORIGEM_Y_NO_ATLAS,
                variante.atlasX + VarianteDoFluxo.LARGURA_DO_RETRATO,
                VarianteDoFluxo.ORIGEM_Y_NO_ATLAS + VarianteDoFluxo.ALTURA_DO_RETRATO,
            )
            destino.set(0f, 0f, VarianteDoFluxo.LARGURA_DO_RETRATO.toFloat(), VarianteDoFluxo.ALTURA_DO_RETRATO.toFloat())
            tintaBitmap.alpha = 255
            canvas.drawBitmap(atlas, origem, destino, tintaBitmap)
        }

        // caixa da bateria
        val b = variante.bateria
        rf.set(b.x, b.y, b.x + b.largura, b.y + b.altura)
        preencher(0xFF626B71.toInt())
        canvas.drawRoundRect(rf, 4f, 4f, tinta)
        traco(0xFF9AA1A5.toInt(), 1f, redondo = false)
        canvas.drawRoundRect(rf, 4f, 4f, tinta)

        // células
        val reg = variante.celulas
        val larguraDaCelula = (reg.largura - (GeometriaDoFluxo.CELULAS - 1) * GeometriaDoFluxo.VAO_DAS_CELULAS) / GeometriaDoFluxo.CELULAS
        val agora = pulso.segundos
        for (i in 0 until GeometriaDoFluxo.CELULAS) {
            val esq = reg.x + i * (larguraDaCelula + GeometriaDoFluxo.VAO_DAS_CELULAS)
            rf.set(esq, reg.y, esq + larguraDaCelula, reg.y + reg.altura)
            preencher(0xFF141D22.toInt())
            canvas.drawRoundRect(rf, 4f, 4f, tinta)
            traco(0xFF929A9F.toInt(), 1.3f, redondo = false)
            canvas.drawRoundRect(rf, 4f, 4f, tinta)
            val opacidade = GeometriaDoFluxo.opacidadeDaCelula(e, i, agora, pulsando)
            if (opacidade > 0f) {
                rf.set(esq + 1f, reg.y + 1.5f, esq + larguraDaCelula - 1f, reg.y + reg.altura - 1.5f)
                preencher(CoresDoCromo.comAlfa(e.corDaPilha, Math.round(255f * opacidade)))
                canvas.drawRoundRect(rf, 4f, 4f, tinta)
            }
        }

        // rotas (a da frente e os ramos das rodas dianteiras; depois as da traseira)
        // rotas: a da frente e os ramos das rodas dianteiras; depois (só no PHEV34) as da traseira
        GeometriaDoFluxo.rotas(variante, e).forEach { rota(canvas, it) }

        if (e.motor) {
            val m = GeometriaDoFluxo.MOTOR
            rf.set(m[0], m[1], m[2], m[3])
            preencher(GeometriaDoFluxo.DESTAQUE_DO_MOTOR)
            canvas.drawRoundRect(rf, 4f, 4f, tinta)
        }

        // textos de carga
        val centroX = reg.x + reg.largura / 2f
        texto(0xFFF2F5F7.toInt(), variante.tamanhoDoTextoDeCarga, negrito, Paint.Align.CENTER)
        canvas.drawText(GeometriaDoFluxo.textoDaCarga(e), centroX, reg.y - 9f, tinta)
        texto(0xFFF2F5F7.toInt(), 9f, normal, Paint.Align.CENTER)
        canvas.drawText("HIGH VOLTAGE", centroX, reg.y + reg.altura + 15f, tinta)
        canvas.drawText("BATTERY", centroX, reg.y + reg.altura + 27f, tinta)

        canvas.restore()
        return pulsando
    }

    private fun rota(canvas: Canvas, r: RotaDoFluxo) {
        if (r.pontos.size < 2) return
        caminho.reset()
        r.pontos.forEachIndexed { i, p -> if (i == 0) caminho.moveTo(p.x, p.y) else caminho.lineTo(p.x, p.y) }
        traco(CoresDoCromo.comAlfa(r.cor, 40), 48f)
        canvas.drawPath(caminho, tinta)
        traco(CoresDoCromo.comAlfa(r.cor, 120), 34f)
        canvas.drawPath(caminho, tinta)
        traco(r.cor, 7f)
        canvas.drawPath(caminho, tinta)
        for (s in GeometriaDoFluxo.setas(r.pontos)) {
            canvas.drawLine(s.ponta.x, s.ponta.y, s.bracoA.x, s.bracoA.y, tinta)
            canvas.drawLine(s.ponta.x, s.ponta.y, s.bracoB.x, s.bracoB.y, tinta)
        }
    }
}
