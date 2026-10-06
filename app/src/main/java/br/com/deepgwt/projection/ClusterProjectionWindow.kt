// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.projection

import br.com.deepgwt.telas.ProjectionGeometry
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout

/**
 * A janelinha que recebe, no painel, o pedaço da central que está sendo projetado.
 *
 * ## DUAS ALTURAS, E QUEM DECIDE QUAL
 *
 * A janela entra no mesmo FrameLayout raiz do projetor, e o lugar dela na pilha depende de o tema
 * saber ou não que existe projeção:
 *
 *  • **POR BAIXO da WebView** quando o tema DECLAROU a caixa (ponte `setProjectionBox`). Declarar
 *    é o tema dizendo "eu sei o que é isso e eu abro o vão": ele recebe
 *    `control('projectionWindow', 'x,y,l,a')`, apaga o próprio fundo naquele retângulo com uma
 *    borda suave, e os mostradores — que moram acima do fundo — continuam desenhando NORMALMENTE
 *    por cima do vídeo. É o pedido: a projeção preenche o vão central e os gauges ficam por cima.
 *
 *  • **POR CIMA da WebView** quando a caixa saiu da varredura automática. Um tema que nunca ouviu
 *    falar de projeção não vai abrir vão nenhum; posto embaixo, o vídeo simplesmente não
 *    apareceria. Por cima ele aparece em QUALQUER tema, e o esmaecimento das bordas
 *    ([MolduraComFade]) é o que evita o recorte retangular duro em cima do desenho do painel.
 *
 * Nos dois casos nada do tema é editado em tempo de execução, e o painel volta ao normal quando a
 * janela sai — sem reiniciar o projetor.
 *
 * ## O CÍRCULO DO PAINEL DE FÁBRICA (Alpha 6.0)
 *
 * Com o painel de fábrica na tela ([DestinoDaProjecao.CIRCULO_DIREITO]) não há vão a procurar: o
 * lugar é o círculo da direita, onde ficam os cards do carro, e a posição é fixa
 * ([ProjectionGeometry.caixaDoCirculo]). A janela vai POR CIMA da WebView, recortada em círculo por
 * [MolduraCircular] — um disco preto que tapa o card do carro, com o vídeo esmaecendo para dentro
 * dele e uma pequena sombra para fora. O pop-up de um aviso de fábrica aparece nesse mesmo círculo;
 * enquanto ele estiver na tela a janela some ([definirAvisoDeFabrica]) e volta sozinha depois. Quem
 * decide quando é o projetor, por `circulo.AvisoNoCirculo` (Alpha 6.6): o pop-up, não o aviso — um
 * cinto solto com o carro parado não tira mais o círculo para sempre.
 *
 * ## COMO O ESPAÇO É ENCONTRADO
 *
 * Em três camadas, da mais exata para a mais genérica:
 *
 *  1. O tema declarou uma caixa (ponte `setProjectionBox`) — é ele quem melhor sabe onde tem
 *     espaço, e quem quiser encaixe perfeito declara.
 *  2. Varredura do DOM: mede o que o tema realmente desenha e procura o maior vão que atravessa a
 *     linha central ([ProjectionGeometry.maiorCaixaLivreNoCentro]). Vale em qualquer tema e em
 *     qualquer resolução, sem o tema declarar nada.
 *  3. Uma caixa central de tamanho fixo em fração da tela, quando as duas primeiras não derem
 *     resposta utilizável. Antes uma caixa razoável no meio do que nenhuma projeção.
 */
class ClusterProjectionWindow(
    private val ctx: Context,
    private val raiz: FrameLayout,
    private val obterWebView: () -> WebView?,
    private val naThreadDaUi: (() -> Unit) -> Unit
) : ScreenProjectionController.JanelaDoPainel {

    companion object {
        private const val TAG = "ClusterProjection"

        /**
         * Elementos que cobrem mais que isto da tela são fundo, não conteúdo.
         *
         * Sem esta regra a varredura nunca acha espaço nenhum: quase todo tema tem um `body` ou um
         * `#app` opaco do tamanho da tela, e ele sozinho marcaria a tela inteira como ocupada.
         */
        private const val FRACAO_DE_FUNDO = 0.55

        /** Caixa de último recurso, em fração da tela do painel. */
        private const val PADRAO_LARGURA = 0.30f
        private const val PADRAO_ALTURA = 0.34f

        /** Moldura em volta da imagem, para ela não parecer um erro de desenho do tema. */
        private const val COR_DA_MOLDURA = 0x66FFFFFF.toInt()
        private const val ESPESSURA_DA_MOLDURA = 2

        /**
         * Quanto da borda esmaece, em fração do lado, quando a janela vai POR CIMA do tema.
         *
         * Só vale no modo por cima: quando o tema abre o vão, é a máscara DELE que faz a
         * transição, e esmaecer também aqui descobriria a camada nativa por baixo do vídeo
         * justamente na faixa onde o tema já saiu da frente.
         */
        private const val FRACAO_DO_FADE = 0.16f

        /** Teto do esmaecimento em pixels, para uma caixa grande não virar só névoa. */
        private const val FADE_MAXIMO_PX = 72

        /**
         * Sombra do disco para fora do círculo, em pixels. É o `box-shadow` de 20 px da máscara que
         * o tema Default punha ali: tapa a borda do card de fábrica sem um corte seco.
         */
        internal const val HALO_DO_CIRCULO_PX = 20

        /** Quanto do vídeo esmaece para dentro do disco, antes da borda do círculo. */
        internal const val FADE_DO_CIRCULO_PX = 26

        /** Tempo para a janela do círculo sair e voltar quando um aviso de fábrica vai e vem. */
        private const val DURACAO_DO_AVISO_MS = 180L

        /**
         * Converte a resposta do JS numa lista de caixas, descartando o que for fundo.
         *
         * O JS devolve texto puro no formato `x,y,l,a;x,y,l,a;…` em vez de JSON. Não é economia de
         * bytes: é para que ESTA função seja Kotlin puro e possa ser testada — `org.json` não
         * existe em teste de unidade, e a alternativa seria deixar sem teste justo a parte que lê
         * dado vindo de fora.
         *
         * Devolve `null` quando a resposta não veio ou não deu para ler, que é diferente de "o tema
         * não desenha nada": no primeiro caso cai-se na caixa padrão; no segundo, a tela toda está
         * livre e a varredura responde isso corretamente com uma lista vazia.
         */
        internal fun interpretarOcupados(
            resultado: String?,
            largura: Int,
            altura: Int
        ): List<ProjectionGeometry.Caixa>? {
            var cru = resultado?.trim() ?: return null
            if (cru.isEmpty() || cru == "null" || cru == "\"null\"") return null
            // evaluateJavascript devolve a string ENTRE ASPAS e com escapes de JSON.
            if (cru.length >= 2 && cru.startsWith("\"") && cru.endsWith("\"")) {
                cru = cru.substring(1, cru.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
            }
            if (cru == "ERRO") return null
            if (cru.isEmpty()) return emptyList()

            val areaDaTela = largura.toLong() * altura.toLong()
            val saida = ArrayList<ProjectionGeometry.Caixa>()
            for (pedaco in cru.split(';')) {
                if (pedaco.isBlank()) continue
                val n = pedaco.split(',')
                if (n.size < 4) continue
                val x = n[0].trim().toIntOrNull() ?: continue
                val y = n[1].trim().toIntOrNull() ?: continue
                val w = n[2].trim().toIntOrNull() ?: continue
                val h = n[3].trim().toIntOrNull() ?: continue
                if (w <= 0 || h <= 0) continue
                // Quem cobre mais que [FRACAO_DE_FUNDO] da tela é fundo, não conteúdo — e fundo não
                // ocupa espaço para este fim.
                if (w.toLong() * h.toLong() > areaDaTela * FRACAO_DE_FUNDO) continue
                saida.add(ProjectionGeometry.Caixa(x, y, w, h))
            }
            return saida
        }
    }

    /** Onde a janela entra na pilha e com que forma. Ver a nota da classe. */
    private enum class Encaixe { POR_BAIXO, POR_CIMA, CIRCULO }

    private var container: FrameLayout? = null
    private var textura: TextureView? = null
    private var superficie: Surface? = null
    private var encaixeAtual: Encaixe? = null

    /** Aviso do carro na tela agora — o círculo cede o lugar enquanto isto for verdade. */
    private var avisoDeFabricaAtivo = false

    /**
     * A página do círculo deixa a projeção aparecer (Alpha 6.1): só na página Central dos cards 1
     * e 3. Fora dela a projeção continua rodando, invisível — voltar à Central é instantâneo.
     */
    private var visivelNoCirculo = true

    /**
     * Avisado na thread da UI quando a janela abre (true) ou fecha (false) NO CÍRCULO. É o que leva
     * o círculo à página Central quando uma projeção começa. Ver circulo/CirculoDoPainelController.
     */
    var aoMudarProjecaoNoCirculo: ((Boolean) -> Unit)? = null

    /** Caixa avisada ao tema, para poder desfazer o aviso exatamente uma vez ao fechar. */
    private var caixaAvisadaAoTema: ProjectionGeometry.Caixa? = null

    override fun abrirJanelaDeProjecao(
        origemLargura: Int,
        origemAltura: Int,
        regiao: ProjectionGeometry.Caixa,
        destino: DestinoDaProjecao,
        aoFicarPronta: (Surface, ProjectionGeometry.Caixa) -> Unit,
        aoFalhar: (String) -> Unit
    ) {
        naThreadDaUi {
            descobrirCaixa(destino) { caixa, encaixe ->
                if (caixa == null) {
                    aoFalhar("Não há espaço livre entre os mostradores deste tema para a projeção.")
                    return@descobrirCaixa
                }
                montar(
                    origemLargura,
                    origemAltura,
                    regiao,
                    caixa,
                    encaixe,
                    aoFicarPronta,
                    aoFalhar
                )
            }
        }
    }

    /**
     * O carro mostrou (ou tirou) um aviso. Os avisos de fábrica aparecem no círculo da direita,
     * então a projeção que mora ali sai da frente enquanto ele durar — tapar um aviso do carro com
     * um mapa não é uma escolha que a pessoa fez. No vão do tema customizado nada muda: ali quem
     * desenha o aviso é o próprio tema, por cima de tudo.
     */
    fun definirAvisoDeFabrica(ativo: Boolean) {
        naThreadDaUi {
            if (avisoDeFabricaAtivo == ativo) return@naThreadDaUi
            avisoDeFabricaAtivo = ativo
            if (atualizarAlfaDoCirculo()) {
                Log.w(TAG, "Projeção no círculo ${if (ativo) "cede o lugar ao" else "volta depois do"} aviso do carro")
            }
        }
    }

    /** Página do círculo: a projeção só aparece na Central dos cards 1 e 3. */
    fun definirVisivelNoCirculo(visivel: Boolean) {
        naThreadDaUi {
            if (visivelNoCirculo == visivel) return@naThreadDaUi
            visivelNoCirculo = visivel
            atualizarAlfaDoCirculo()
        }
    }

    /** Leva a janela do círculo ao alfa certo. false quando não há janela no círculo. */
    private fun atualizarAlfaDoCirculo(): Boolean {
        val c = container ?: return false
        if (encaixeAtual != Encaixe.CIRCULO) return false
        val alvo = if (avisoDeFabricaAtivo || !visivelNoCirculo) 0f else 1f
        c.animate().cancel()
        c.animate().alpha(alvo).setDuration(DURACAO_DO_AVISO_MS).start()
        return true
    }

    override fun fecharJanelaDeProjecao() {
        naThreadDaUi {
            avisarTema(null)
            val c = container
            val estavaNoCirculo = encaixeAtual == Encaixe.CIRCULO
            container = null
            textura = null
            encaixeAtual = null
            if (estavaNoCirculo) aoMudarProjecaoNoCirculo?.invoke(false)
            runCatching { superficie?.release() }
            superficie = null
            if (c != null) runCatching { raiz.removeView(c) }
        }
    }

    /**
     * Conta ao tema onde está o vão da projeção, para ele abrir (ou fechar) o buraco no fundo.
     *
     * Vai por `control()`, que todo tema implementa e que ignora chave desconhecida sem erro — um
     * tema antigo recebe isto e não faz nada, que é exatamente o comportamento desejado, porque um
     * tema que não abre o vão também não é posto por baixo.
     */
    private fun avisarTema(caixa: ProjectionGeometry.Caixa?) {
        if (caixa == null && caixaAvisadaAoTema == null) return
        caixaAvisadaAoTema = caixa
        val wv = obterWebView() ?: return
        val valor = caixa?.let { "${it.x},${it.y},${it.largura},${it.altura}" } ?: ""
        runCatching { wv.evaluateJavascript("control('projectionWindow', '$valor');", null) }
            .onFailure { Log.w(TAG, "Não foi possível avisar o tema sobre o vão da projeção", it) }
    }

    /**
     * Reenvia ao tema o aviso do vão, depois de a página ter recarregado.
     *
     * O buraco no fundo é estado da PÁGINA, não do app: uma recarga da WebView (troca de tema,
     * watchdog do projetor, a própria página se redesenhando) devolve o fundo inteiro e o vídeo —
     * que continua lá embaixo, projetando — some atrás dele, sem erro nenhum no log. O aviso é
     * idempotente do lado do tema, então repeti-lo é seguro; não repetir é que não é.
     */
    fun reavisarTema() {
        val caixa = caixaAvisadaAoTema ?: return
        naThreadDaUi { avisarTema(caixa) }
    }

    // ── Montagem ──────────────────────────────────────────────────────────────────────────────

    private fun montar(
        origemLargura: Int,
        origemAltura: Int,
        regiao: ProjectionGeometry.Caixa,
        caixa: ProjectionGeometry.Caixa,
        encaixe: Encaixe,
        aoFicarPronta: (Surface, ProjectionGeometry.Caixa) -> Unit,
        aoFalhar: (String) -> Unit
    ) {
        fecharJanelaDeProjecaoSincrono()

        val recorte = ProjectionGeometry.recorteParaCaixa(
            origemLargura = origemLargura,
            origemAltura = origemAltura,
            caixaLargura = caixa.largura,
            caixaAltura = caixa.altura,
            recorte = regiao,
            // PREENCHER, não caber: o vão central do painel tem de ficar cheio. A sobra da imagem
            // é aparada pelo `clipChildren` da moldura. Ver ProjectionGeometry.recorteParaCaixa.
            preencher = true
        )
        if (recorte == null) {
            aoFalhar("A área escolhida não cabe no espaço do painel.")
            return
        }

        val porBaixo = encaixe == Encaixe.POR_BAIXO
        val circular = encaixe == Encaixe.CIRCULO
        val pena = if (porBaixo || circular) 0 else calcularPena(caixa)
        // No círculo a moldura é maior que o vídeo: a sobra em volta é onde a sombra do disco
        // esmaece para fora, tapando a borda do card de fábrica.
        val halo = if (circular) HALO_DO_CIRCULO_PX else 0

        val moldura = when {
            circular -> MolduraCircular(ctx, halo, FADE_DO_CIRCULO_PX)
            pena > 0 -> MolduraComFade(ctx, pena)
            else -> FrameLayout(ctx)
        }.apply {
            layoutParams = FrameLayout.LayoutParams(caixa.largura + 2 * halo, caixa.altura + 2 * halo).apply {
                leftMargin = caixa.x - halo
                topMargin = caixa.y - halo
            }
            // Preto sólido no modo POR BAIXO. Não é decoração: o tema abre o vão assim que é
            // avisado, e o primeiro quadro do espelho só chega alguns décimos depois — nesse
            // intervalo o buraco mostraria o painel de fábrica piscando. Como a imagem PREENCHE a
            // caixa, este preto some no primeiro quadro e não volta.
            //
            // No modo por cima ele continua translúcido, ali como separação do desenho do tema.
            // No círculo o fundo não pode ser da view — um fundo quadrado apareceria nos cantos
            // do disco; o preto é desenhado pela própria MolduraCircular, só dentro do círculo.
            setBackgroundColor(
                when {
                    circular -> Color.TRANSPARENT
                    porBaixo -> Color.BLACK
                    else -> Color.argb(160, 0, 0, 0)
                }
            )
            clipChildren = true
            isClickable = false
            isFocusable = false
        }

        val tv = TextureView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(caixa.largura, caixa.altura).apply {
                leftMargin = halo
                topMargin = halo
            }
            isOpaque = false
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                    // O tamanho do buffer é o da TELA DE ORIGEM, não o da caixa: é a tela inteira
                    // que é espelhada, e é a matriz que recorta. Deixar o buffer no tamanho da
                    // caixa faria o espelho ser reduzido ANTES do recorte, e a área escolhida
                    // chegaria borrada.
                    st.setDefaultBufferSize(origemLargura, origemAltura)
                    val s = Surface(st)
                    superficie = s
                    aplicarRecorte(this@apply, recorte)
                    aoFicarPronta(s, caixa)
                }

                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                    st.setDefaultBufferSize(origemLargura, origemAltura)
                    aplicarRecorte(this@apply, recorte)
                }

                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                    runCatching { superficie?.release() }
                    superficie = null
                    // `true` = a TextureView pode liberar a SurfaceTexture. Devolver `false` aqui
                    // significaria assumir a posse dela, e nada neste caminho a liberaria depois.
                    return true
                }

                override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
            }
        }

        moldura.addView(tv)

        // A moldura desenhada só existe no modo POR CIMA, e nem ali junto do esmaecimento: uma
        // borda nítida em volta de uma imagem que desaparece nas pontas é contraditória -- a linha
        // reta denuncia exatamente o retângulo que o fade existe para esconder.
        if (!porBaixo && !circular && pena <= 0) {
            val borda = View(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(caixa.largura, caixa.altura)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    setStroke(ESPESSURA_DA_MOLDURA, COR_DA_MOLDURA)
                }
                isClickable = false
            }
            moldura.addView(borda)
        }

        if (porBaixo) {
            // Logo abaixo da WebView do tema e acima da máscara nativa: é essa posição que põe os
            // mostradores por cima do vídeo sem tirar a máscara que tapa o painel de fábrica.
            val alvo = obterWebView()
            val indice = if (alvo != null) raiz.indexOfChild(alvo).coerceAtLeast(0) else 0
            raiz.addView(moldura, indice)
            avisarTema(caixa)
        } else {
            raiz.addView(moldura)
        }
        container = moldura
        textura = tv
        encaixeAtual = encaixe
        // Aberta com um aviso do carro já na tela, ou fora da página Central: nasce escondida.
        if (circular && (avisoDeFabricaAtivo || !visivelNoCirculo)) moldura.alpha = 0f
        if (circular) aoMudarProjecaoNoCirculo?.invoke(true)
        Log.w(
            TAG,
            "Janela de projeção aberta no painel: $caixa (origem ${origemLargura}x$origemAltura) " +
                "encaixe=$encaixe fade=${pena}px"
        )
    }

    /**
     * Largura do esmaecimento para esta caixa.
     *
     * Proporcional ao lado, com teto: numa caixa pequena um fade fixo de 72 px comeria a imagem
     * inteira, e numa caixa larga um fade proporcional viraria uma faixa enorme de névoa. Zero
     * quando a caixa é pequena demais para sobrar conteúdo depois de esmaecer os dois lados.
     */
    private fun calcularPena(caixa: ProjectionGeometry.Caixa): Int {
        val menorLado = kotlin.math.min(caixa.largura, caixa.altura)
        val pena = kotlin.math.min((menorLado * FRACAO_DO_FADE).toInt(), FADE_MAXIMO_PX)
        return if (menorLado - 2 * pena < 40) 0 else pena
    }

    private fun fecharJanelaDeProjecaoSincrono() {
        avisarTema(null)
        val c = container ?: return
        if (encaixeAtual == Encaixe.CIRCULO) aoMudarProjecaoNoCirculo?.invoke(false)
        container = null
        textura = null
        encaixeAtual = null
        runCatching { superficie?.release() }
        superficie = null
        runCatching { raiz.removeView(c) }
    }

    private fun aplicarRecorte(tv: TextureView, r: ProjectionGeometry.Recorte) {
        val m = Matrix()
        m.setScale(r.escalaX, r.escalaY)
        m.postTranslate(r.deslocaX, r.deslocaY)
        tv.setTransform(m)
    }

    // ── Descoberta do espaço livre ────────────────────────────────────────────────────────────

    /**
     * Acha a caixa e diz COMO ela entra na pilha.
     *
     * O segundo parâmetro não é informação extra: é ele que decide a altura da janela na pilha
     * (ver a nota da classe). Um tema que declarou a caixa é um tema que abre o vão no próprio
     * fundo, e só nesse caso o vídeo pode ir por baixo dos mostradores. No painel de fábrica não
     * há o que procurar: é o círculo da direita, sempre.
     */
    private fun descobrirCaixa(
        destino: DestinoDaProjecao,
        pronto: (ProjectionGeometry.Caixa?, Encaixe) -> Unit
    ) {
        val largura = raiz.width.takeIf { it > 0 } ?: 1920
        val altura = raiz.height.takeIf { it > 0 } ?: 720
        if (destino == DestinoDaProjecao.CIRCULO_DIREITO) {
            val circulo = ProjectionGeometry.caixaDoCirculo(largura, altura)
            Log.w(TAG, "Usando o círculo da direita do painel de fábrica: $circulo")
            pronto(circulo, Encaixe.CIRCULO)
            return
        }

        val declarada = ScreenProjectionController.caixaDeclaradaPeloTema
        if (declarada != null && declarada.largura >= ProjectionGeometry.LARGURA_MINIMA &&
            declarada.altura >= ProjectionGeometry.ALTURA_MINIMA
        ) {
            Log.w(TAG, "Usando a caixa declarada pelo tema: $declarada")
            pronto(declarada, Encaixe.POR_BAIXO)
            return
        }

        val wv = obterWebView()
        if (wv == null) {
            pronto(caixaPadrao(largura, altura), Encaixe.POR_CIMA)
            return
        }
        runCatching {
            wv.evaluateJavascript(JS_MEDIR_OCUPADOS) { resultado ->
                val ocupados = interpretarOcupados(resultado, largura, altura)
                val achada = if (ocupados == null) {
                    null
                } else {
                    ProjectionGeometry.maiorCaixaLivreNoCentro(largura, altura, ocupados)
                }
                val padrao = caixaPadrao(largura, altura)
                if (achada != null && cabeMaisQue(achada, padrao)) {
                    Log.w(TAG, "Vão encontrado na varredura do tema: $achada")
                    pronto(achada, Encaixe.POR_CIMA)
                } else {
                    // A varredura devolve o MAIOR vão livre que cruza a linha do meio, e num painel
                    // cheio isso pode ser um filete: medido num tema real, 240x332 encostado no
                    // topo, que no carro aparece como um retanguinho de vídeo perdido ao lado do
                    // velocímetro -- que é como este defeito foi relatado. Tecnicamente é a resposta
                    // certa da pergunta errada: "o maior espaço vazio" não é "um lugar onde a imagem
                    // se lê". Abaixo da caixa central de último recurso, ela é que vale.
                    Log.w(
                        TAG,
                        "Varredura devolveu ${achada ?: "nada"}, pequeno demais perto de $padrao; " +
                            "usando a caixa central padrão"
                    )
                    pronto(padrao, Encaixe.POR_CIMA)
                }
            }
        }.onFailure {
            Log.w(TAG, "evaluateJavascript falhou ao medir o tema", it)
            pronto(caixaPadrao(largura, altura), Encaixe.POR_CIMA)
        }
    }

    /**
     * Moldura que apaga as próprias bordas, para a projeção sumir aos poucos em vez de terminar
     * num corte reto.
     *
     * ## POR QUE `saveLayer` E NÃO `setLayerType(LAYER_TYPE_HARDWARE)`
     *
     * Os dois criam a camada fora da tela que o [PorterDuff.Mode.DST_OUT] precisa para apagar
     * ALFA em vez de pintar de preto. A diferença é a TextureView: uma camada de view permanente
     * em cima dela é justamente o arranjo que alguns drivers de automotivo deixam de atualizar —
     * o vídeo congela no primeiro quadro e nada no log diz por quê. O `saveLayer` vive só durante
     * este `dispatchDraw`, é o caminho que o próprio framework usa para `fadingEdge`, e não muda
     * como a textura é composta.
     *
     * As quatro bordas são apagadas com gradientes independentes. Nos cantos o apagamento acontece
     * duas vezes e o canto fica um pouco mais aberto que os lados — que é o que se quer: um canto
     * é a parte da imagem que mais denuncia o retângulo.
     */
    @SuppressLint("ViewConstructor")
    private class MolduraComFade(ctx: Context, private val pena: Int) : FrameLayout(ctx) {

        private val apagador = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            isAntiAlias = false
        }

        // Os gradientes dependem do tamanho, que só existe depois do layout. Recriados em
        // onSizeChanged e não a cada quadro: alocar quatro LinearGradient por frame numa tela de
        // carro é desperdício visível no perfil.
        private var esquerda: LinearGradient? = null
        private var direita: LinearGradient? = null
        private var topo: LinearGradient? = null
        private var base: LinearGradient? = null

        init {
            setWillNotDraw(false)
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            val opaco = Color.BLACK
            val vazio = Color.TRANSPARENT
            val p = pena.toFloat()
            esquerda = LinearGradient(0f, 0f, p, 0f, opaco, vazio, Shader.TileMode.CLAMP)
            direita = LinearGradient(w - p, 0f, w.toFloat(), 0f, vazio, opaco, Shader.TileMode.CLAMP)
            topo = LinearGradient(0f, 0f, 0f, p, opaco, vazio, Shader.TileMode.CLAMP)
            base = LinearGradient(0f, h - p, 0f, h.toFloat(), vazio, opaco, Shader.TileMode.CLAMP)
        }

        override fun dispatchDraw(canvas: Canvas) {
            if (pena <= 0 || width <= 0 || height <= 0) {
                super.dispatchDraw(canvas)
                return
            }
            val camada = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
            super.dispatchDraw(canvas)
            val l = width.toFloat()
            val a = height.toFloat()
            val p = pena.toFloat()
            esquerda?.let { apagador.shader = it; canvas.drawRect(0f, 0f, p, a, apagador) }
            direita?.let { apagador.shader = it; canvas.drawRect(l - p, 0f, l, a, apagador) }
            topo?.let { apagador.shader = it; canvas.drawRect(0f, 0f, l, p, apagador) }
            base?.let { apagador.shader = it; canvas.drawRect(0f, a - p, l, a, apagador) }
            apagador.shader = null
            canvas.restoreToCount(camada)
        }
    }

    /**
     * O disco do círculo da direita: vídeo recortado em círculo sobre um fundo preto que tapa o
     * card do carro, com sombra suave para fora.
     *
     * Mesma técnica da [MolduraComFade] e pelo mesmo motivo — `saveLayer` só durante o
     * `dispatchDraw`, nunca uma camada de view permanente em cima da TextureView. Dentro da camada,
     * em três passos:
     *
     *  1. os filhos desenham (o vídeo, no quadrado do círculo, com [halo] de folga em volta);
     *  2. DST_OUT com um gradiente radial apaga tudo fora do círculo e esmaece a última faixa de
     *     [fade] px do vídeo — é o recorte redondo, sem serrilhado;
     *  3. DST_OVER pinta o disco preto POR TRÁS do que sobrou: opaco até a borda do círculo, e daí
     *     esmaecendo até sumir na borda da moldura. Na faixa em que o vídeo esmaece, é este preto
     *     que aparece — o vídeo afunda no disco em vez de mostrar o card do carro por baixo.
     *
     * Gradientes recriados só em [onSizeChanged]; o desenho de cada quadro é dois retângulos.
     */
    @SuppressLint("ViewConstructor")
    internal class MolduraCircular(ctx: Context, private val halo: Int, private val fade: Int) :
        FrameLayout(ctx) {

        private val apagador = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            isAntiAlias = true
        }
        private val disco = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER)
            isAntiAlias = true
        }

        init {
            setWillNotDraw(false)
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            val externo = kotlin.math.min(w, h) / 2f
            if (externo <= 0f) return
            val raio = (externo - halo).coerceAtLeast(1f)
            val inicioDoFade = (raio - fade).coerceAtLeast(0f)
            val cx = w / 2f
            val cy = h / 2f
            // Posições em fração do raio EXTERNO (o do gradiente); além dele, CLAMP repete a última
            // cor — opaca no apagador, então tudo fora da moldura redonda sai.
            apagador.shader = RadialGradient(
                cx, cy, externo,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.BLACK, Color.BLACK),
                floatArrayOf(0f, inicioDoFade / externo, raio / externo, 1f),
                Shader.TileMode.CLAMP
            )
            disco.shader = RadialGradient(
                cx, cy, externo,
                intArrayOf(Color.BLACK, Color.BLACK, Color.argb(150, 0, 0, 0), Color.TRANSPARENT),
                floatArrayOf(0f, raio / externo, (raio + halo * 0.35f) / externo, 1f),
                Shader.TileMode.CLAMP
            )
        }

        override fun dispatchDraw(canvas: Canvas) {
            if (width <= 0 || height <= 0 || apagador.shader == null) {
                super.dispatchDraw(canvas)
                return
            }
            val l = width.toFloat()
            val a = height.toFloat()
            val camada = canvas.saveLayer(0f, 0f, l, a, null)
            super.dispatchDraw(canvas)
            canvas.drawRect(0f, 0f, l, a, apagador)
            canvas.drawRect(0f, 0f, l, a, disco)
            canvas.restoreToCount(camada)
        }
    }

    /**
     * A caixa varrida vale mais que a de último recurso?
     *
     * Os dois lados são comparados separadamente, e não pela área: uma faixa de 1900x110 tem área
     * maior que 576x244 e não serve para nada -- imagem nenhuma se lê num filete.
     */
    private fun cabeMaisQue(
        candidata: ProjectionGeometry.Caixa,
        referencia: ProjectionGeometry.Caixa
    ): Boolean = candidata.largura >= referencia.largura && candidata.altura >= referencia.altura

    private fun caixaPadrao(largura: Int, altura: Int): ProjectionGeometry.Caixa {
        val w = (largura * PADRAO_LARGURA).toInt()
        val h = (altura * PADRAO_ALTURA).toInt()
        return ProjectionGeometry.Caixa((largura - w) / 2, (altura - h) / 2, w, h)
    }

}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

/**
 * Mede, dentro da WebView do tema, tudo que é realmente PINTADO.
 *
 * "Pintado" é a parte que importa: um tema é cheio de `div`s de agrupamento, transparentes e do
 * tamanho de meia tela. Contá-las marcaria como ocupado um espaço que na tela está vazio, e a
 * projeção nunca acharia onde caber. Então só conta quem tem fundo, borda, imagem, desenho SVG ou
 * texto próprio.
 */
private const val JS_MEDIR_OCUPADOS = """
(function () {
  try {
    var SVG = 'http://www.w3.org/2000/svg';
    var saida = [];
    var todos = document.querySelectorAll('body *');
    for (var i = 0; i < todos.length; i++) {
      var el = todos[i];
      var cs = window.getComputedStyle(el);
      if (!cs) continue;
      if (cs.display === 'none' || cs.visibility === 'hidden') continue;
      if (parseFloat(cs.opacity || '1') < 0.05) continue;
      var r = el.getBoundingClientRect();
      if (!r || r.width < 8 || r.height < 8) continue;

      var pinta = false;
      var tag = (el.tagName || '').toUpperCase();
      if (tag === 'IMG' || tag === 'CANVAS' || tag === 'VIDEO') {
        pinta = true;
      } else if (el.namespaceURI === SVG && tag !== 'SVG' && tag !== 'G' && tag !== 'DEFS') {
        pinta = true;
      } else {
        var bg = cs.backgroundColor || '';
        if (bg && bg !== 'transparent' && bg.indexOf('rgba(0, 0, 0, 0)') !== 0) pinta = true;
        if (!pinta && cs.backgroundImage && cs.backgroundImage !== 'none') pinta = true;
        if (!pinta && parseFloat(cs.borderTopWidth || '0') > 0) pinta = true;
        if (!pinta && parseFloat(cs.borderLeftWidth || '0') > 0) pinta = true;
        if (!pinta) {
          for (var n = el.firstChild; n; n = n.nextSibling) {
            if (n.nodeType === 3 && n.nodeValue && n.nodeValue.trim()) { pinta = true; break; }
          }
        }
      }
      if (!pinta) continue;
      saida.push(
        Math.round(r.left) + ',' + Math.round(r.top) + ',' +
        Math.round(r.width) + ',' + Math.round(r.height)
      );
      if (saida.length > 600) break;
    }
    return saida.join(';');
  } catch (e) {
    return 'ERRO';
  }
})()
"""
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
