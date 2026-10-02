package br.com.opengw.hud

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import br.com.opengw.midia.RadioNowPlaying
import java.util.Locale

/**
 * O desenho do HUD.
 *
 * TUDO EM CÓDIGO, NADA DE LAYOUT
 *
 * O HUD é um display privado de 480x240 com densidade própria: qualquer `sp`/`dp` herdado da
 * central sai com tamanho errado lá. Então nenhum recurso do app entra aqui — todo tamanho é
 * derivado da altura REAL da view, e os ajustes do usuário ([HudSettings]) são percentuais dessa
 * altura. O mesmo arquivo desenha certo num HUD de outra resolução sem tocar em nada.
 *
 * FUNDO PRETO, E POR QUÊ
 *
 * O HUD projeta luz: preto é ausência de luz, ou seja, o fundo some no para-brisa e só o que for
 * claro aparece. É o oposto de uma tela — por isso o fundo é preto chapado e o conteúdo é pouco,
 * grande e claro.
 *
 * CINCO PÁGINAS E UMA SOBREPOSIÇÃO
 *
 * [HudPaginas] diz qual página desenhar; os direcionais do volante andam entre elas. Por cima de
 * QUALQUER página, quando o carro tem o que avisar, entra a sobreposição de alerta — ela cobre o
 * HUD inteiro, aparece e sai em transição suave ([DURACAO_FADE_MS]) e tem prioridade absoluta: nada
 * do conteúdo normal aparece enquanto ela está no ar.
 */
/**
 * @param paginaFixa quando diferente de `null`, esta view é uma MINIATURA: desenha sempre esta
 * página, venha o volante trocar o que vier, e nunca levanta a cortina de alerta. É o que a tela de
 * Telas coloca ao lado de cada linha da lista de páginas, e é a mesma classe do HUD de verdade de
 * propósito — uma segunda implementação "só para a prévia" seria uma segunda verdade sobre como
 * cada página se parece, e as duas divergiriam no primeiro ajuste que alguém esquecesse de copiar.
 */
class HudOverlayView(
        context: Context,
        paginaFixa: HudPaginas.Pagina? = null,
        /**
         * Levanta a cortina de alerta. Só vale sem [paginaFixa]. O espelho do HUD no círculo do
         * painel (circulo/JanelaDoCirculo) desliga: ali quem avisa é o próprio carro.
         */
        private val comAlertas: Boolean = true
) : View(context) {

    /**
     * A página desenhada, ou `null` para seguir [HudPaginas.pagina]. Mutável para o círculo do
     * painel mostrar a página que o volante está ESCOLHENDO sem trocar o HUD antes do OK.
     */
    var paginaFixa: HudPaginas.Pagina? = paginaFixa
        set(valor) {
            if (field == valor) return
            field = valor
            invalidate()
        }

    /**
     * Assinatura dos ajustes já desenhados, para a miniatura da tela de Telas.
     *
     * A miniatura vive dentro de um `AndroidView`, que não sabe repintar sozinho quando um
     * controle deslizante do Compose se move. Quem compara esta assinatura é a tela; aqui ela é só
     * um lugar para guardá-la junto da view a que pertence. O HUD de verdade não usa este campo:
     * ele repinta por evento de telemetria e pelo pulso de 1 s da Activity.
     */
    var ajustesDesenhados: List<Int>? = null

    private val fundo = Color.BLACK

    private val paintTexto =
            Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    /**
     * Peso já aplicado ao [paintTexto], para não recriar o `Typeface` a cada quadro.
     *
     * `Typeface.create` consulta a família instalada e monta um objeto novo; chamá-lo uma vez por
     * quadro, em 480x240, seria trabalho repetido para chegar sempre ao mesmo desenho. -1 nunca é
     * um peso válido, então o primeiro quadro sempre aplica.
     */
    private var pesoAplicado = -1

    /** Família já aplicada, pela mesma razão do [pesoAplicado]. */
    private var fonteAplicada: HudFonte? = null

    /** Valores das páginas de números e o texto da mídia: alinhado pela esquerda, medido à mão. */
    private val paintEsq = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT }

    /**
     * Rótulos (caixa-alta, espaçados), unidades e o artista: um peso abaixo do valor.
     *
     * Com o mesmo peso em tudo, "km" e "AUTONOMIA" disputavam atenção com o número. A hierarquia
     * vem do peso e do tamanho juntos — o valor é o que o olho acha primeiro.
     */
    private val paintRotulo =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                letterSpacing = 0.12f
            }
    private val paintUnidade = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT }

    /** A capa, com filtro: sem ele a arte de 300 px reduzida para 130 sai serrilhada. */
    private val paintCapa = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val paintForma = Paint(Paint.ANTI_ALIAS_FLAG)
    private val caixa = Rect()
    private val retangulo = RectF()
    private val traco = Path()

    /** Vermelho de alerta: reservado para o que exige ação imediata (e para o pneu a verificar). */
    private val corAlerta = Color.rgb(255, 64, 48)

    /** Âmbar dos avisos que não são de segurança imediata: portas, pneus, combustível (9.4). */
    private val corAviso = Color.rgb(255, 176, 32)
    /** O cinza da cortina de alerta, que o tema não alcança (ver [corRotulo]). */
    private val corSecundaria = Color.rgb(170, 180, 190)
    private val corApagada = Color.rgb(90, 96, 104)

    // ---- Transição da sobreposição de alerta -------------------------------------------------
    /** Quanto da sobreposição está desenhada agora: 0 = nada, 1 = cobrindo tudo. */
    private var alfaAlerta = 0f
    private var ultimoQuadroMs = 0L

    /** O que a cortina mostrava por último, para ela sair com a transição. */
    private var ultimosAlertas: List<HudTelemetry.Alerta> = emptyList()

    // ---- Estilo da página que está sendo desenhada --------------------------------------------
    //
    // Resolvidos uma vez por quadro em [desenharPagina] e lidos pelos desenhos daquela página. São
    // campos, e não parâmetros, porque a alternativa seria acrescentar dois argumentos a cada um
    // dos doze auxiliares de desenho para dizer a mesma coisa.
    //
    // O que NÃO responde a eles, de propósito: a placa de limite (é uma placa de trânsito, tem de
    // parecer uma), os pontinhos de página (navegação, não conteúdo) e a sobreposição de alerta
    // (vermelho e branco são o contrato dela).

    /** Cor do valor principal da página atual ([HudSettings.corDaPagina]). */
    private var corPrincipal = Color.WHITE

    /** Multiplicador do corpo de letra da página atual ([HudSettings.escalaDaPagina]). */
    private var escalaPagina = 1f

    /**
     * Rótulos, unidades e cabeçalho das páginas ([HudSettings.corRotulo], do tema). Sem tema é o
     * mesmo cinza de [corSecundaria].
     */
    private var corRotulo = HudSettings.COR_ROTULO_PADRAO

    init {
        HudSettings.load()
        usarPeso(HudSettings.pesoDaFonte, HudSettings.fonte)
        setBackgroundColor(fundo)
    }

    /**
     * Troca a espessura de tudo o que vier a ser escrito.
     *
     * Precisa ser aplicada ANTES de qualquer desenho de texto do quadro, e não só antes do
     * `drawText`: [linhasDoTitulo] mede o texto com o `Paint` para decidir se ele cabe na coluna, e uma
     * medição feita com o peso errado devolveria um corpo de letra errado.
     */
    private fun usarPeso(peso: Int, fonte: HudFonte) {
        if (peso == pesoAplicado && fonte == fonteAplicada) return
        pesoAplicado = peso
        fonteAplicada = fonte
        // A padrão continua sendo `Typeface.SANS_SERIF` — o mesmo objeto da 5.8, e não uma busca
        // por nome que poderia devolver outra instância da mesma família.
        val familia =
                when (fonte) {
                    HudFonte.PADRAO -> Typeface.SANS_SERIF
                    HudFonte.CONDENSADA -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
                }
        // A sobrecarga com peso numérico é da API 28, que é o mínimo deste app (minSdk = 28).
        val principal = Typeface.create(familia, peso, false)
        paintTexto.typeface = principal
        paintEsq.typeface = principal
        val leve = Typeface.create(familia, pesoDoRotulo(peso), false)
        paintRotulo.typeface = leve
        paintUnidade.typeface = leve
    }

    /**
     * Dois degraus abaixo do valor, sem passar de 400 para baixo — e nunca mais grosso que o
     * próprio valor, para quem escolheu uma fonte fina (300 fica 300 em tudo).
     */
    private fun pesoDoRotulo(peso: Int): Int = (peso - 200).coerceIn(minOf(peso, 400), peso)

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(fundo)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Modo noturno (Alpha 9.5): o quadro inteiro passa por um filtro que multiplica as cores.
        // No HUD preto é "sem luz", então escurecer é multiplicar — o fundo continua preto e só o
        // que acende perde brilho. Camada de 480x240, uma por quadro: custo desprezível.
        val noturno = HudSettings.noturnoAtivo { HudTelemetry.farolAceso() }
        if (noturno) {
            canvas.saveLayer(0f, 0f, w, h, paintNoturno(HudSettings.brilhoNoturno, HudSettings.noturnoAmbar))
        }
        desenharQuadro(canvas, w, h)
        if (noturno) canvas.restore()
    }

    private val paintDoNoturno = Paint()
    private var filtroNoturnoDe = -1

    /** Filtro de multiplicação para [brilho] % (e o puxão para o âmbar). Refeito só quando muda. */
    private fun paintNoturno(brilho: Int, ambar: Boolean): Paint {
        val chave = brilho * 2 + if (ambar) 1 else 0
        if (chave != filtroNoturnoDe) {
            filtroNoturnoDe = chave
            val k = brilho.coerceIn(0, 100) / 100f
            val mul = if (ambar) Color.rgb((255 * k).toInt(), (190 * k).toInt(), (90 * k).toInt())
                      else Color.rgb((255 * k).toInt(), (255 * k).toInt(), (255 * k).toInt())
            paintDoNoturno.colorFilter = android.graphics.LightingColorFilter(mul, 0)
        }
        return paintDoNoturno
    }

    private fun desenharQuadro(canvas: Canvas, w: Float, h: Float) {

        usarPeso(HudSettings.pesoDaFonte, HudSettings.fonte)
        corRotulo = HudSettings.corRotulo

        // A miniatura nunca levanta a cortina: ela existe para mostrar como UMA página se parece,
        // e uma porta aberta no pátio taparia as cinco prévias da tela de Telas com o mesmo aviso
        // vermelho — justamente enquanto o usuário tenta comparar as páginas entre si.
        val alertas =
                if (comAlertas && paginaFixa == null && HudSettings.showAlerts) HudTelemetry.alertas()
                else emptyList()
        avancarFade(alertas.isNotEmpty())
        // A saída da cortina precisa do que ela mostrava: sem isto, no quadro em que o alerta
        // acabava a lista vinha vazia e a cortina sumia seca, sem a transição de saída.
        if (alertas.isNotEmpty()) ultimosAlertas = alertas
        val naCortina = alertas.ifEmpty { ultimosAlertas }

        // A página só é desenhada quando a sobreposição ainda deixa ver alguma coisa. Desenhar por
        // baixo de uma cortina opaca seria trabalho jogado fora a cada quadro.
        if (alfaAlerta < 1f) desenharPagina(canvas, w, h)
        if (alfaAlerta > 0f && naCortina.isNotEmpty()) {
            // A cortina de alerta usa os mesmos auxiliares de texto das páginas, então a escala da
            // página escorreria para dentro dela. O alerta tem tamanho fixo por decisão: ele é o
            // único conteúdo que o motorista NÃO escolheu ver, e encolher um aviso porque a página
            // de consumo foi configurada menor seria deixar o usuário reduzir o que o carro grita.
            escalaPagina = 1f
            // A família também é fixa na cortina: o tema escolhe a letra das páginas, não a do aviso.
            usarPeso(HudSettings.PESO_DO_ALERTA, HudFonte.PADRAO)
            desenharSobreposicaoDeAlerta(canvas, w, h, naCortina)
        }
    }

    /**
     * Anda um passo da transição e pede o próximo quadro enquanto ela não terminar.
     *
     * A camada repinta uma vez por segundo ([HudOverlayActivity]); isso basta para número mudando,
     * mas não para uma transição. Em vez de um segundo temporizador espalhado pelo código, a
     * própria transição pede o quadro seguinte enquanto está em curso e para de pedir ao chegar no
     * fim — nenhum trabalho acontece com o HUD parado.
     */
    private fun avancarFade(temAlerta: Boolean) {
        val agora = SystemClock.uptimeMillis()
        val decorrido = if (ultimoQuadroMs == 0L) 0L else (agora - ultimoQuadroMs)
        ultimoQuadroMs = agora
        val alvo = if (temAlerta) 1f else 0f
        if (alfaAlerta == alvo) return
        val passo = (decorrido.coerceAtMost(DURACAO_FADE_MS)).toFloat() / DURACAO_FADE_MS
        alfaAlerta =
                if (alvo > alfaAlerta) (alfaAlerta + passo).coerceAtMost(1f)
                else (alfaAlerta - passo).coerceAtLeast(0f)
        if (alfaAlerta != alvo) postInvalidateOnAnimation()
    }

    // ---------------------------------------------------------------- páginas

    private fun desenharPagina(canvas: Canvas, w: Float, h: Float) {
        val atual = paginaFixa ?: HudPaginas.pagina
        corPrincipal = HudSettings.corDaPagina(atual)
        // A página de velocidade já tem dois controles de tamanho próprios (o número e a placa);
        // uma terceira escala por cima deles brigaria com os dois.
        escalaPagina =
                if (atual == HudPaginas.Pagina.VELOCIDADE) 1f else HudSettings.escalaDaPagina(atual)
        if (atual != HudPaginas.Pagina.VELOCIDADE && HudSettings.mostrarCabecalho) {
            desenharCabecalho(canvas, w, h, atual)
        }
        // `atual`, e não `HudPaginas.pagina`: numa miniatura as duas são páginas diferentes, e ler
        // a global aqui desenharia o cabeçalho de uma com o conteúdo de outra.
        when (atual) {
            HudPaginas.Pagina.VELOCIDADE -> paginaVelocidade(canvas, w, h)
            HudPaginas.Pagina.MIDIA -> paginaMidia(canvas, w, h)
            HudPaginas.Pagina.CONSUMO,
            HudPaginas.Pagina.POWERTRAIN,
            HudPaginas.Pagina.VEICULO ->
                    HudSettings.layoutDaPagina(atual)?.let { paginaDeNumeros(canvas, w, h, it) }
        }
        // Os pontos ficam visíveis inclusive na página de velocidade: é o único sinal de que
        // existem outras páginas e de que o volante as alcança. Só um tema os tira.
        // Com o Controle do HUD ativo (Alpha 9.5) os pontinhos aparecem sempre, e a pílula da página
        // no ar fica azul: é o que diz, no próprio para-brisa, que as setas estão com o HUD.
        if (HudSettings.mostrarPontos || controlando()) desenharPontos(canvas, w, h - h * 0.045f)
    }

    /**
     * O nome da página, pequeno e espaçado no alto.
     *
     * Era um título de 24 px no peso dos números, ocupando quase um quinto do HUD para dizer uma
     * palavra que o motorista só precisa ler na primeira vez. Agora é um rótulo, e a altura volta
     * para o conteúdo. Não obedece à escala da página: é moldura, não conteúdo.
     */
    private fun desenharCabecalho(canvas: Canvas, w: Float, h: Float, pagina: HudPaginas.Pagina) {
        paintRotulo.color = corRotulo
        paintRotulo.textAlign = Paint.Align.CENTER
        paintRotulo.textSize = h * TAM_CABECALHO
        val texto = HudSettings.tituloDaPagina(pagina).uppercase(PT_BR)
        val largura = w * (1f - 2 * MARGEM)
        val medida = paintRotulo.measureText(texto)
        if (medida > largura) paintRotulo.textSize *= largura / medida
        canvas.drawText(texto, w / 2f, h * LINHA_CABECALHO, paintRotulo)
    }

    /** O HUD de verdade (não miniatura nem espelho) com o Controle do HUD ativo. */
    private fun controlando(): Boolean = comAlertas && paginaFixa == null && ControleDoHud.ativo

    private fun desenharPontos(canvas: Canvas, w: Float, y: Float) {
        // `total` e `indice` filtram a lista de páginas visíveis a cada leitura. Lidos uma vez,
        // fora do laço: dentro dele seriam cinco filtragens e cinco buscas por quadro para
        // responder sempre a mesma coisa — a mesma regra que [HudTelemetry] segue para não
        // consultar o carro durante o desenho.
        val total = HudPaginas.total
        // Numa miniatura o ponto aceso é o da página DESENHADA, não o da que está no ar; -1 quando
        // ela está fora do rodízio, e aí nenhum ponto acende (ver HudPaginas.indiceDe).
        val fixa = paginaFixa
        val atual = if (fixa != null) HudPaginas.indiceDe(fixa) else HudPaginas.indice
        val r = w * 0.006f
        val passo = r * 5f
        val inicio = w / 2f - (total - 1) * passo / 2f
        paintForma.style = Paint.Style.FILL
        for (i in 0 until total) {
            val px = inicio + i * passo
            if (i == atual) {
                // 9.4: a página no ar é uma pílula, não só um ponto mais claro — lê-se de relance
                // qual das cinco está na tela sem contar pontos.
                paintForma.color = if (controlando()) COR_DO_CONTROLE else Color.WHITE
                retangulo.set(px - r * 2f, y - r, px + r * 2f, y + r)
                canvas.drawRoundRect(retangulo, r, r, paintForma)
            } else {
                paintForma.color = corApagada
                canvas.drawCircle(px, y, r, paintForma)
            }
        }
    }

    // ---- 1. velocidade + SLI -----------------------------------------------------------------

    private fun paginaVelocidade(canvas: Canvas, w: Float, h: Float) {
        // Desconta a faixa dos pontos para o número não encostar neles.
        val alturaUtil = h * 0.90f
        desenharVelocidade(canvas, w, alturaUtil)
        if (HudSettings.showSli) desenharPlacaDeLimite(canvas, w, alturaUtil)
    }

    private fun desenharVelocidade(canvas: Canvas, w: Float, alturaUtil: Float) {
        val velocidade = HudTelemetry.velocidadeKmh()
        val texto = velocidade?.toString() ?: "--"

        paintTexto.shader = null
        paintTexto.color = HudSettings.speedColor
        paintTexto.textSize = alturaUtil * (HudSettings.speedSizePct / 100f)
        paintTexto.getTextBounds(texto, 0, texto.length, caixa)

        // O número fica centralizado na largura útil, não na tela inteira: a placa de limite ocupa
        // um dos lados (a direita, ou a esquerda por tema) e um número grande encostaria nela.
        val larguraUtil = if (HudSettings.showSli) w * 0.72f else w
        val cx =
                if (HudSettings.showSli && HudSettings.placaAEsquerda) w - larguraUtil / 2f
                else larguraUtil / 2f
        val cy = alturaUtil / 2f + caixa.height() / 2f
        canvas.drawText(texto, cx, cy, paintTexto)

        // "km/h" logo abaixo, discreto: no HUD ele existe para quem olha a primeira vez — e um
        // tema pode tirá-lo para quem já sabe.
        if (!HudSettings.unidadeVelocidade) return
        // No peso dos rótulos (9.4): no peso cheio do número, "km/h" disputava atenção com ele.
        paintUnidade.color = corRotulo
        paintUnidade.textSize = alturaUtil * 0.11f
        centralizado(canvas, "km/h", cx, cy + alturaUtil * 0.15f, paintUnidade)
    }

    /**
     * A placa de limite, desenhada como placa: círculo branco com aro vermelho e o número dentro.
     * É a forma que o motorista já reconhece de relance — um número solto ao lado da velocidade
     * seria confundido com a própria velocidade.
     */
    private fun desenharPlacaDeLimite(canvas: Canvas, w: Float, alturaUtil: Float) {
        val limite = HudTelemetry.limiteDaViaKmh()
        val lado = alturaUtil * (HudSettings.sliSizePct / 100f)
        val raio = lado / 2f
        val cx = if (HudSettings.placaAEsquerda) raio + lado * 0.25f else w - raio - lado * 0.25f
        val cy = alturaUtil / 2f

        if (limite == null) {
            // Sem placa válida: um aro apagado mostra onde ela apareceria, sem afirmar limite nenhum.
            paintForma.style = Paint.Style.STROKE
            paintForma.strokeWidth = raio * 0.16f
            paintForma.color = Color.rgb(70, 76, 84)
            canvas.drawCircle(cx, cy, raio * 0.92f, paintForma)
            return
        }

        paintForma.style = Paint.Style.FILL
        paintForma.color = Color.WHITE
        canvas.drawCircle(cx, cy, raio, paintForma)

        paintForma.style = Paint.Style.STROKE
        paintForma.strokeWidth = raio * 0.20f
        paintForma.color = corAlerta
        canvas.drawCircle(cx, cy, raio - paintForma.strokeWidth / 2f, paintForma)

        val texto = limite.toString()
        paintTexto.shader = null
        paintTexto.color = Color.BLACK
        paintTexto.textSize = raio * 1.0f
        paintTexto.getTextBounds(texto, 0, texto.length, caixa)
        canvas.drawText(texto, cx, cy + caixa.height() / 2f, paintTexto)
    }

    // ---- 2. mídia ----------------------------------------------------------------------------

    /**
     * Rádio, música, capa e informações de mídia numa página só, como o relatório pediu.
     *
     * TRÊS CARAS, CADA UMA COM O SEU DADO
     *
     * Música: capa à esquerda, texto alinhado à esquerda do lado — quem tocou (Spotify,
     * Bluetooth…), o título em até duas linhas e o artista. Sem capa o mesmo bloco vem centrado.
     * Rádio: a frequência grande, a banda no rótulo e o nome RDS embaixo quando existir. Nada
     * tocando: uma linha discreta.
     *
     * O QUE ESTAVA ERRADO
     *
     * A primeira linha repetia "Mídia" logo abaixo do cabeçalho "Mídia"; no rádio a frequência
     * saía duas vezes (rótulo e título); um título longo encolhia até sumir numa linha só; e "nada
     * tocando" ocupava a largura inteira do HUD. Texto que não cabe agora quebra em duas linhas e
     * termina em reticências — nunca encolhe abaixo de um corpo legível.
     */
    private fun paginaMidia(canvas: Canvas, w: Float, h: Float) {
        val titulo = HudTelemetry.midiaTitulo()
        val radio = if (titulo.isEmpty()) HudTelemetry.radioTocando() else null
        when {
            titulo.isNotEmpty() -> midiaMusica(canvas, w, h, titulo)
            radio != null -> midiaRadio(canvas, w, h, radio)
            else -> {
                paintEsq.color = corRotulo
                paintEsq.textSize = h * 0.10f * escalaPagina
                centralizado(canvas, "Nada tocando", w / 2f, h * 0.57f, paintEsq)
            }
        }
    }

    private fun midiaMusica(canvas: Canvas, w: Float, h: Float, titulo: String) {
        val capa = HudTelemetry.midiaCapa()?.takeIf { !it.isRecycled }
        val artista = HudTelemetry.midiaArtista()
        val app = HudTelemetry.midiaApp()
        val margem = w * MARGEM
        val centroY = (TOPO_CONTEUDO + BASE_CONTEUDO) / 2f * h

        var esquerda = margem
        if (capa != null) {
            val lado = h * 0.56f
            val topo = centroY - lado / 2f
            retangulo.set(esquerda, topo, esquerda + lado, topo + lado)
            canvas.save()
            traco.reset()
            traco.addRoundRect(retangulo, lado * 0.09f, lado * 0.09f, Path.Direction.CW)
            canvas.clipPath(traco)
            canvas.drawBitmap(capa, null, retangulo, paintCapa)
            canvas.restore()
            esquerda += lado + w * 0.05f
        }
        val largura = w - margem - esquerda
        // Com capa o texto encosta nela (alinhado à esquerda); sem capa, centrado no HUD.
        val xDe = { texto: String, paint: Paint ->
            if (capa != null) esquerda else w / 2f - paint.measureText(texto) / 2f
        }

        val tamRotulo = h * TAM_ROTULO
        var tamTitulo = h * 0.125f * escalaPagina
        val tamArtista = h * 0.085f * escalaPagina
        paintEsq.textSize = tamTitulo
        // Passou da linha por pouco: encolhe um pouco e fica numa linha só, em vez de deixar uma
        // palavra solta na segunda ("Evidências (Ao / Vivo)"). Passou por muito: duas linhas.
        val larguraTitulo = paintEsq.measureText(titulo)
        val linhas =
                if (larguraTitulo > largura && larguraTitulo <= largura * 1.2f) {
                    tamTitulo *= largura / larguraTitulo
                    paintEsq.textSize = tamTitulo
                    listOf(titulo)
                } else {
                    quebrarEmLinhas(titulo, paintEsq, largura, 2)
                }

        // Altura do bloco, para centrá-lo na vertical ao lado da capa.
        val alturaRotulo = if (app.isNotEmpty()) tamRotulo * 1.9f else 0f
        val alturaTitulo = tamTitulo * (0.78f + (linhas.size - 1) * 1.14f)
        val alturaArtista = if (artista.isNotEmpty()) tamArtista * 1.55f else 0f
        var y = centroY - (alturaRotulo + alturaTitulo + alturaArtista) / 2f

        if (app.isNotEmpty()) {
            val texto = app.uppercase(PT_BR)
            paintRotulo.color = corRotulo
            paintRotulo.textAlign = Paint.Align.LEFT
            paintRotulo.textSize = tamRotulo
            y += tamRotulo * 0.78f
            canvas.drawText(texto, xDe(texto, paintRotulo), y, paintRotulo)
            y += tamRotulo * 1.12f
        }
        paintEsq.color = corPrincipal
        paintEsq.textSize = tamTitulo
        y += tamTitulo * 0.78f
        for ((i, linha) in linhas.withIndex()) {
            if (i > 0) y += tamTitulo * 1.14f
            canvas.drawText(linha, xDe(linha, paintEsq), y, paintEsq)
        }
        if (artista.isNotEmpty()) {
            paintUnidade.color = corRotulo
            paintUnidade.textSize = tamArtista
            val texto = reticencias(artista, paintUnidade, largura)
            y += tamArtista * 1.55f
            canvas.drawText(texto, xDe(texto, paintUnidade), y, paintUnidade)
        }
    }

    private fun midiaRadio(canvas: Canvas, w: Float, h: Float, radio: RadioNowPlaying.Leitura) {
        val estacao = RadioNowPlaying.estacao(radio.canal) ?: return
        val banda = estacao.banda.takeIf { it != RadioNowPlaying.RODAPE_PADRAO }
        bloco(
                canvas,
                w / 2f,
                w * (1f - 2 * MARGEM),
                h * 0.30f,
                h * 0.64f,
                if (banda != null) "Rádio $banda" else "Rádio",
                estacao.numero,
                estacao.unidade,
                h * 0.27f
        )
        val nome = RadioNowPlaying.nomeRds(radio.rds)
        if (nome != null) {
            paintUnidade.color = corRotulo
            paintUnidade.textSize = h * 0.085f * escalaPagina
            val texto = reticencias(nome, paintUnidade, w * (1f - 2 * MARGEM))
            centralizado(canvas, texto, w / 2f, h * 0.82f, paintUnidade)
        }
    }

    // ---- 3 a 5. páginas de números: consumo, powertrain, veículo ------------------------------

    /**
     * Uma página de números: a linha A com um ou dois valores grandes e a linha B com até três
     * menores, cada um com o seu rótulo — os campos vêm de [HudSettings.layoutDaPagina].
     *
     * AS TRÊS PÁGINAS DA 5.8 SÃO ESTA FUNÇÃO COM OS CAMPOS DE FÁBRICA
     *
     * Consumo (consumo; autonomia, tanque, bateria), Powertrain (potência, torque; rotação, modo,
     * tração) e Veículo (odômetro, marcha; fora, dentro, pneus) eram três funções com as mesmas
     * contas. Sem tema, [HudLayout.padrao] devolve essas listas e o desenho sai pixel a pixel igual:
     * a coluna de um campo opcional sem dado some (bateria, tração AWD), e a linha se redivide.
     *
     * Só a linha A, sem linha B: os valores descem para o meio e crescem — um tema de "relógio e
     * autonomia" não pode deixar a metade de baixo do HUD vazia.
     */
    private fun paginaDeNumeros(canvas: Canvas, w: Float, h: Float, layout: HudLayout) {
        val util = w * (1f - 2 * MARGEM)
        val linhaA = layout.linhaA.mapNotNull { lerCampo(it) }
        val linhaB = layout.linhaB.mapNotNull { lerCampo(it) }
        val soA = linhaB.isEmpty()

        if (linhaA.isNotEmpty()) {
            val largura = util / linhaA.size
            for ((i, campo) in linhaA.withIndex()) {
                // Um valor só fica no centro exato do HUD, como na página de consumo da 5.8.
                val cx = if (linhaA.size == 1) w / 2f else w * MARGEM + largura * (i + 0.5f)
                bloco(
                        canvas, cx, largura,
                        h * (if (soA) LINHA_SO_A_ROTULO else LINHA_A_ROTULO),
                        h * (if (soA) LINHA_SO_A_VALOR else LINHA_A_VALOR),
                        campo.rotulo, campo.valor, campo.unidade,
                        h * (if (soA) TAM_SO_A else TAM_A),
                        if (campo.alerta) corAlerta else corPrincipal
                )
            }
        }

        if (linhaB.isEmpty()) return
        val larguraColuna = util / linhaB.size
        for ((i, campo) in linhaB.withIndex()) {
            val cx = w * MARGEM + larguraColuna * (i + 0.5f)
            linhaB(canvas, cx, larguraColuna, h, campo.rotulo, campo.valor, campo.unidade,
                    if (campo.alerta) corAlerta else corPrincipal)
            campo.barra?.let { barraFina(canvas, cx, larguraColuna * 0.56f, h, it) }
        }
    }

    // ---------------------------------------------------------------- sobreposição de alerta

    /**
     * A cortina de alerta: um disco com o desenho à esquerda e, ao lado, de que sistema é, o que
     * houve e onde.
     *
     * O QUE MUDOU NA 9.4
     *
     * Era um desenho de traço fino solto no preto, o título inteiro em vermelho e o detalhe em
     * branco, centrados numa coluna — o cinto, em especial, saía como três riscos que não lembravam
     * a lâmpada do painel. Agora cada aviso tem a cara de uma lâmpada: desenho sólido dentro de um
     * disco com aro e halo da cor, o sistema em rótulo pequeno ("SEGURANÇA"), o título em branco
     * (o que se lê primeiro) e o detalhe em cinza. A cor diz a gravidade: vermelho com o aro
     * pulsando para segurança imediata, âmbar para aviso ([HudTelemetry.Alerta.grave]).
     *
     * Entra e sai com [alfaAlerta]. O fundo preto usa a mesma opacidade — num HUD ótico preto é
     * APAGAR, então a cortina apaga a página em vez de misturar as duas. O resto vai numa camada
     * opaca que entra junto: os recortes pretos dos desenhos precisam ser pretos de verdade.
     */
    private fun desenharSobreposicaoDeAlerta(
            canvas: Canvas,
            w: Float,
            h: Float,
            alertas: List<HudTelemetry.Alerta>
    ) {
        val a = alfaAlerta.coerceIn(0f, 1f)
        paintForma.style = Paint.Style.FILL
        paintForma.color = Color.argb((255 * a).toInt(), 0, 0, 0)
        canvas.drawRect(0f, 0f, w, h, paintForma)

        // Um alerta por vez, alternando a cada 2 s: dois avisos lado a lado em 480 px não se leem.
        val indice =
                if (alertas.size <= 1) 0
                else ((SystemClock.uptimeMillis() / RODIZIO_MS) % alertas.size).toInt()
        val alerta = alertas[indice]
        val cor = if (alerta.grave) corAlerta else corAviso

        val camada = if (a < 1f) canvas.saveLayerAlpha(0f, 0f, w, h, (255 * a).toInt()) else -1

        // ---- o disco
        val pulso = if (alerta.grave) pulso() else 1f
        val raio = h * 0.31f
        val cx = w * MARGEM + raio
        val cy = h * 0.5f
        paintForma.style = Paint.Style.FILL
        paintForma.color = corComAlfa(cor, 0.10f + 0.10f * pulso)
        canvas.drawCircle(cx, cy, raio, paintForma)
        paintForma.style = Paint.Style.STROKE
        paintForma.strokeWidth = raio * 0.085f
        paintForma.color = corComAlfa(cor, 0.40f + 0.60f * pulso)
        canvas.drawCircle(cx, cy, raio - paintForma.strokeWidth / 2f, paintForma)
        desenharIcone(canvas, alerta.icone, cx, cy, raio * 1.2f, cor)

        // ---- o texto, num bloco centrado na altura do disco
        val esquerda = cx + raio + w * 0.055f
        val largura = w * (1f - MARGEM) - esquerda
        val categoria = HudTelemetry.categoriaDe(alerta.icone).uppercase(PT_BR)
        val tamRotulo = h * 0.062f

        val titulo = linhasDoTitulo(alerta.titulo, h * 0.175f, h * 0.12f, largura)
        val tamTitulo = paintEsq.textSize
        val detalhe = linhasDoDetalhe(alerta.detalhe, h * 0.10f, h * 0.075f, largura)
        val tamDetalhe = paintEsq.textSize

        val alturaRotulo = tamRotulo * 0.74f + tamTitulo * 0.42f
        val alturaTitulo = tamTitulo * (0.74f + (titulo.size - 1) * 1.08f)
        val alturaDetalhe =
                if (detalhe.isEmpty()) 0f else tamTitulo * 0.40f + tamDetalhe * (0.74f + (detalhe.size - 1) * 1.2f)
        var y = cy - (alturaRotulo + alturaTitulo + alturaDetalhe) / 2f

        paintRotulo.color = cor
        paintRotulo.textAlign = Paint.Align.LEFT
        paintRotulo.textSize = tamRotulo
        y += tamRotulo * 0.74f
        canvas.drawText(categoria, esquerda, y, paintRotulo)
        y += tamTitulo * 0.42f

        paintEsq.color = Color.WHITE
        paintEsq.textSize = tamTitulo
        for ((i, linha) in titulo.withIndex()) {
            y += if (i == 0) tamTitulo * 0.74f else tamTitulo * 1.08f
            canvas.drawText(linha, esquerda, y, paintEsq)
        }

        if (detalhe.isNotEmpty()) {
            paintEsq.color = corSecundaria
            paintEsq.textSize = tamDetalhe
            y += tamTitulo * 0.40f
            for ((i, linha) in detalhe.withIndex()) {
                y += if (i == 0) tamDetalhe * 0.74f else tamDetalhe * 1.2f
                canvas.drawText(linha, esquerda, y, paintEsq)
            }
        }

        // ---- mais de um alerta: pontinhos na cor do que está no ar, sob o texto
        if (alertas.size > 1) {
            val r = w * 0.0065f
            val passo = r * 4.2f
            val yPontos = h * 0.90f
            paintForma.style = Paint.Style.FILL
            for (i in alertas.indices) {
                paintForma.color = if (i == indice) cor else corApagada
                canvas.drawCircle(esquerda + r + i * passo, yPontos, r, paintForma)
            }
        }

        if (camada >= 0) canvas.restoreToCount(camada)
        // O pulso precisa de quadros próprios (a camada só repinta a cada 1 s). ~30 quadros/s,
        // só com um alerta grave no ar; parado o HUD volta a não pedir nada.
        if (alerta.grave) postInvalidateDelayed(QUADRO_DO_PULSO_MS)
    }

    /** 0..1..0 a cada [PERIODO_DO_PULSO_MS]: o aro e o halo dos alertas graves respiram. */
    private fun pulso(): Float {
        val fase = (SystemClock.uptimeMillis() % PERIODO_DO_PULSO_MS).toFloat() / PERIODO_DO_PULSO_MS
        return 0.5f + 0.5f * kotlin.math.cos(2.0 * Math.PI * fase).toFloat()
    }

    /**
     * O título em uma linha, encolhendo até [minimo]; não cabendo, duas linhas entre palavras.
     * Deixa [paintEsq] no corpo escolhido.
     */
    private fun linhasDoTitulo(texto: String, maximo: Float, minimo: Float, largura: Float): List<String> {
        paintEsq.textSize = maximo
        val inteira = paintEsq.measureText(texto)
        if (inteira <= largura) return listOf(texto)
        val umaLinha = maximo * largura / inteira
        if (umaLinha >= minimo) {
            paintEsq.textSize = umaLinha
            return listOf(texto)
        }
        var tam = maximo * 0.86f
        while (true) {
            paintEsq.textSize = tam
            val linhas = quebrarEmLinhas(texto, paintEsq, largura, 2)
            val coube = !linhas.last().endsWith("…") || texto.endsWith("…")
            if (coube || tam <= minimo) return linhas
            tam = maxOf(minimo, tam * 0.92f)
        }
    }

    /**
     * O detalhe do alerta em uma ou duas linhas. Deixa [paintEsq] no corpo escolhido.
     *
     * Uma linha enquanto couber sem encolher demais (até 3/4 de [maximo]) — "Dianteira esquerda"
     * não merece ser partida. Não cabendo, duas linhas: uma lista de portas quebra no separador
     * " · ", equilibrando as duas, para nenhum nome ficar cortado ao meio; o resto quebra entre
     * palavras. O corpo desce até [minimo] para o texto caber inteiro.
     */
    private fun linhasDoDetalhe(
            texto: String,
            maximo: Float,
            minimo: Float,
            largura: Float
    ): List<String> {
        if (texto.isEmpty()) return emptyList()
        paintEsq.textSize = maximo
        val inteira = paintEsq.measureText(texto)
        if (inteira <= largura / 0.75f) {
            paintEsq.textSize = if (inteira <= largura) maximo else maximo * largura / inteira
            return listOf(texto)
        }
        val partes = texto.split(SEPARADOR)
        val linhas =
                if (partes.size >= 2) {
                    (1 until partes.size)
                            .map { k ->
                                listOf(
                                        partes.subList(0, k).joinToString(SEPARADOR),
                                        partes.subList(k, partes.size).joinToString(SEPARADOR)
                                )
                            }
                            .minByOrNull { par -> par.maxOf { paintEsq.measureText(it) } }!!
                } else {
                    paintEsq.textSize = minimo
                    quebrarEmLinhas(texto, paintEsq, largura, 2).also { paintEsq.textSize = maximo }
                }
        val maisLarga = linhas.maxOf { paintEsq.measureText(it) }
        paintEsq.textSize =
                if (maisLarga <= largura) maximo
                else (maximo * largura / maisLarga).coerceAtLeast(minimo)
        return linhas.map { reticencias(it, paintEsq, largura) }
    }

    /**
     * Os desenhos dos alertas, no estilo das lâmpadas do painel: silhuetas sólidas, poucos
     * detalhes, recortes em preto.
     *
     * Cada desenho é escrito numa caixa de 100 x 100 ([lado] px, centrada em [cx],[cy]); `x()`/`y()`
     * convertem. Sólido e não traço fino de propósito: no para-brisa, a esta distância, um contorno
     * de 3 px vira um borrão, e uma área cheia continua sendo reconhecida pela silhueta. O mesmo
     * desenho, número a número, está em `instalador/src/hud-editor/render.ts` (prévia do instalador).
     */
    private fun desenharIcone(
            canvas: Canvas,
            icone: HudTelemetry.Icone,
            cx: Float,
            cy: Float,
            lado: Float,
            cor: Int
    ) {
        val k = lado / 100f
        val x0 = cx - lado / 2f
        val y0 = cy - lado / 2f
        fun x(v: Float) = x0 + v * k
        fun y(v: Float) = y0 + v * k
        val p = paintForma
        fun cheio(c: Int = cor) {
            p.style = Paint.Style.FILL
            p.color = c
        }
        fun contorno(largura: Float, c: Int = cor) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = largura * k
            p.color = c
        }
        fun caixa(l: Float, t: Float, r: Float, b: Float, raio: Float) {
            retangulo.set(x(l), y(t), x(r), y(b))
            canvas.drawRoundRect(retangulo, raio * k, raio * k, p)
        }
        fun circulo(ccx: Float, ccy: Float, r: Float) = canvas.drawCircle(x(ccx), y(ccy), r * k, p)
        fun linha(vararg pts: Float, fechar: Boolean = false) {
            traco.reset()
            traco.moveTo(x(pts[0]), y(pts[1]))
            var i = 2
            while (i < pts.size) {
                traco.lineTo(x(pts[i]), y(pts[i + 1]))
                i += 2
            }
            if (fechar) traco.close()
            canvas.drawPath(traco, p)
        }
        fun arco(ccx: Float, ccy: Float, r: Float, inicio: Float, varredura: Float) {
            retangulo.set(x(ccx - r), y(ccy - r), x(ccx + r), y(ccy + r))
            canvas.drawArc(retangulo, inicio, varredura, false, p)
        }
        // Exclamação recortada/desenhada: barra de [topo] a [base] e o ponto em [ponto].
        fun exclamacao(topo: Float, base: Float, ponto: Float, largura: Float, c: Int) {
            contorno(largura, c)
            linha(50f, topo, 50f, base)
            cheio(c)
            circulo(50f, ponto, largura * 0.62f)
        }
        val preto = Color.BLACK
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND

        when (icone) {
            // Pessoa sentada de frente com a faixa do cinto atravessada do ombro ao quadril e a
            // fivela — a lâmpada do cinto. A faixa é separada do corpo por um recorte preto.
            HudTelemetry.Icone.CINTO -> {
                cheio()
                circulo(50f, 18f, 14f)
                traco.reset()
                traco.moveTo(x(18f), y(96f))
                traco.lineTo(x(18f), y(55f))
                traco.cubicTo(x(18f), y(43f), x(28f), y(37f), x(40f), y(37f))
                traco.lineTo(x(60f), y(37f))
                traco.cubicTo(x(72f), y(37f), x(82f), y(43f), x(82f), y(55f))
                traco.lineTo(x(82f), y(96f))
                traco.close()
                canvas.drawPath(traco, p)
                contorno(18f, preto)
                linha(28f, 40f, 70f, 86f)
                contorno(9f)
                linha(28f, 40f, 70f, 86f)
                cheio(preto)
                caixa(58f, 76f, 88f, 97f, 5f)
                cheio()
                caixa(62f, 79f, 85f, 94f, 3f)
            }
            // Carro visto de cima com a porta aberta.
            HudTelemetry.Icone.PORTA -> {
                cheio()
                caixa(30f, 6f, 62f, 94f, 14f)
                cheio(preto)
                caixa(35f, 24f, 57f, 38f, 4f)
                caixa(35f, 66f, 57f, 76f, 4f)
                contorno(9f)
                linha(62f, 34f, 88f, 54f)
            }
            // Carro de lado com a tampa traseira levantada.
            HudTelemetry.Icone.PORTA_MALAS -> {
                cheio()
                caixa(6f, 50f, 90f, 74f, 8f)
                linha(18f, 51f, 30f, 32f, 66f, 32f, 72f, 51f, fechar = true)
                cheio(preto)
                linha(26f, 48f, 34f, 37f, 46f, 37f, 46f, 48f, fechar = true)
                linha(51f, 48f, 51f, 37f, 62f, 37f, 66f, 48f, fechar = true)
                circulo(26f, 74f, 12f)
                circulo(72f, 74f, 12f)
                cheio()
                circulo(26f, 74f, 7f)
                circulo(72f, 74f, 7f)
                contorno(8f)
                linha(70f, 30f, 92f, 14f)
            }
            // Bloco do motor, a silhueta da lâmpada de injeção: bloco, tampa com o gargalo em
            // cima, flange à esquerda e o escalonado da direita.
            HudTelemetry.Icone.MOTOR -> {
                cheio()
                caixa(24f, 36f, 74f, 76f, 4f)
                caixa(36f, 28f, 62f, 37f, 2f)
                caixa(45f, 19f, 53f, 29f, 1f)
                caixa(37f, 13f, 61f, 20f, 2f)
                caixa(12f, 48f, 25f, 64f, 2f)
                caixa(6f, 42f, 13f, 70f, 2f)
                caixa(73f, 44f, 84f, 70f, 2f)
                caixa(83f, 51f, 93f, 63f, 2f)
            }
            // Termômetro mergulhado: a lâmpada de temperatura do líquido de arrefecimento.
            HudTelemetry.Icone.TEMPERATURA -> {
                cheio()
                caixa(43f, 6f, 57f, 64f, 7f)
                circulo(50f, 68f, 14f)
                contorno(6f)
                linha(60f, 18f, 74f, 18f)
                linha(60f, 32f, 70f, 32f)
                linha(60f, 46f, 74f, 46f)
                contorno(6f)
                traco.reset()
                traco.moveTo(x(10f), y(90f))
                traco.cubicTo(x(22f), y(82f), x(28f), y(98f), x(40f), y(90f))
                traco.cubicTo(x(52f), y(82f), x(58f), y(98f), x(70f), y(90f))
                traco.cubicTo(x(78f), y(85f), x(84f), y(92f), x(90f), y(90f))
                canvas.drawPath(traco, p)
            }
            // Almotolia com a gota: a lâmpada de pressão do óleo.
            HudTelemetry.Icone.OLEO -> {
                cheio()
                caixa(14f, 44f, 62f, 76f, 5f)
                caixa(30f, 34f, 44f, 45f, 2f)
                linha(60f, 50f, 90f, 36f, 92f, 42f, 64f, 66f, fechar = true)
                contorno(6f)
                arco(14f, 56f, 10f, 90f, 180f)
                cheio()
                traco.reset()
                traco.moveTo(x(88f), y(52f))
                traco.cubicTo(x(94f), y(62f), x(96f), y(66f), x(92f), y(70f))
                traco.cubicTo(x(88f), y(74f), x(82f), y(70f), x(84f), y(64f))
                traco.close()
                canvas.drawPath(traco, p)
            }
            // Corte do pneu com a exclamação: a lâmpada do TPMS.
            HudTelemetry.Icone.PNEU -> {
                contorno(9f)
                traco.reset()
                traco.moveTo(x(26f), y(84f))
                traco.cubicTo(x(8f), y(66f), x(10f), y(28f), x(30f), y(16f))
                canvas.drawPath(traco, p)
                traco.reset()
                traco.moveTo(x(74f), y(84f))
                traco.cubicTo(x(92f), y(66f), x(90f), y(28f), x(70f), y(16f))
                canvas.drawPath(traco, p)
                linha(18f, 86f, 82f, 86f)
                contorno(6f)
                for (xi in floatArrayOf(28f, 42f, 58f, 72f)) linha(xi, 88f, xi, 96f)
                exclamacao(30f, 56f, 70f, 9f, cor)
            }
            // Bomba de combustível com a mangueira.
            HudTelemetry.Icone.COMBUSTIVEL -> {
                cheio()
                caixa(16f, 10f, 58f, 90f, 6f)
                caixa(10f, 84f, 64f, 94f, 3f)
                cheio(preto)
                caixa(23f, 18f, 51f, 40f, 3f)
                contorno(6f)
                traco.reset()
                traco.moveTo(x(58f), y(52f))
                traco.lineTo(x(68f), y(52f))
                traco.cubicTo(x(74f), y(52f), x(76f), y(56f), x(76f), y(62f))
                traco.lineTo(x(76f), y(74f))
                traco.cubicTo(x(76f), y(82f), x(86f), y(82f), x(86f), y(74f))
                traco.lineTo(x(86f), y(34f))
                traco.lineTo(x(76f), y(22f))
                canvas.drawPath(traco, p)
            }
            // (!) — a lâmpada do sistema de freio.
            HudTelemetry.Icone.FREIO -> {
                contorno(8f)
                circulo(50f, 50f, 27f)
                arco(50f, 50f, 42f, 135f, 90f)
                arco(50f, 50f, 42f, -45f, 90f)
                exclamacao(36f, 56f, 66f, 8f, cor)
            }
            // (P) — a lâmpada do freio de estacionamento.
            HudTelemetry.Icone.ESTACIONAMENTO -> {
                contorno(8f)
                circulo(50f, 50f, 27f)
                arco(50f, 50f, 42f, 135f, 90f)
                arco(50f, 50f, 42f, -45f, 90f)
                contorno(8f)
                traco.reset()
                traco.moveTo(x(42f), y(66f))
                traco.lineTo(x(42f), y(34f))
                traco.lineTo(x(52f), y(34f))
                traco.cubicTo(x(64f), y(34f), x(64f), y(52f), x(52f), y(52f))
                traco.lineTo(x(42f), y(52f))
                canvas.drawPath(traco, p)
            }
            // Triângulo cheio com a exclamação recortada, para o que não tem desenho próprio.
            HudTelemetry.Icone.AVISO -> {
                cheio()
                p.strokeWidth = 10f * k
                p.style = Paint.Style.FILL_AND_STROKE
                linha(50f, 12f, 91f, 86f, 9f, 86f, fechar = true)
                exclamacao(38f, 62f, 74f, 9f, preto)
            }
        }
        p.strokeCap = Paint.Cap.BUTT
        p.strokeJoin = Paint.Join.MITER
    }

    // ---------------------------------------------------------------- peças comuns

    /**
     * Um valor com nome: rótulo pequeno em caixa-alta em cima, valor e unidade na mesma linha de
     * base embaixo. É a peça de todas as páginas de números — a mesma altura de rótulo, o mesmo
     * respiro e a mesma proporção de unidade em todas, para as páginas parecerem uma família.
     */
    private fun bloco(
            canvas: Canvas,
            cx: Float,
            largura: Float,
            yRotulo: Float,
            yValor: Float,
            rotulo: String,
            valor: String,
            unidade: String,
            tamanho: Float,
            corValor: Int = corPrincipal
    ) {
        val larguraUtil = largura * 0.92f
        if (rotulo.isNotEmpty()) {
            // O rótulo NÃO segue a escala da página: é moldura, como o cabeçalho. Crescendo junto
            // com o valor, em 140% ele encostava no número logo abaixo.
            val texto = rotulo.uppercase(PT_BR)
            paintRotulo.color = corRotulo
            paintRotulo.textAlign = Paint.Align.CENTER
            paintRotulo.textSize = height * TAM_ROTULO
            val larguraRotulo = paintRotulo.measureText(texto)
            if (larguraRotulo > larguraUtil) paintRotulo.textSize *= larguraUtil / larguraRotulo
            canvas.drawText(texto, cx, yRotulo, paintRotulo)
        }
        valorEUnidade(canvas, cx, yValor, valor, unidade, tamanho, larguraUtil, corValor)
    }

    /** A linha de baixo das páginas de números: mesmo [bloco], nas alturas da linha B. */
    private fun linhaB(
            canvas: Canvas,
            cx: Float,
            largura: Float,
            h: Float,
            rotulo: String,
            valor: String,
            unidade: String,
            corValor: Int = corPrincipal
    ) = bloco(canvas, cx, largura, h * LINHA_B_ROTULO, h * LINHA_B_VALOR, rotulo, valor, unidade,
            h * TAM_B, corValor)

    /**
     * Valor e unidade lado a lado, centrados juntos em [cx], na mesma linha de base.
     *
     * Antes a unidade ia numa linha própria embaixo do número, e com duas linhas de valores por
     * página as unidades da linha de cima encostavam nos números da de baixo. Na mesma linha a
     * página ganha a altura de uma linha inteira — e "272 km" se lê de uma vez.
     */
    private fun valorEUnidade(
            canvas: Canvas,
            cx: Float,
            base: Float,
            valor: String,
            unidade: String,
            tamanho: Float,
            larguraMax: Float,
            cor: Int
    ) {
        var tamValor = tamanho * escalaPagina
        // Piso para a unidade: na linha de baixo 42% do valor dava 13 px, e letra desse tamanho
        // some no para-brisa.
        var tamUnidade = maxOf(tamValor * PROPORCAO_UNIDADE, height * TAM_UNIDADE_MIN * escalaPagina)
        paintEsq.textSize = tamValor
        var lValor = paintEsq.measureText(valor)
        paintUnidade.textSize = tamUnidade
        var lUnidade = if (unidade.isEmpty()) 0f else paintUnidade.measureText(unidade)
        var vao = if (unidade.isEmpty()) 0f else tamValor * 0.12f
        val total = lValor + vao + lUnidade
        if (total > larguraMax && total > 0f) {
            val k = larguraMax / total
            tamValor *= k
            tamUnidade *= k
            lValor *= k
            lUnidade *= k
            vao *= k
        }
        var x = cx - (lValor + vao + lUnidade) / 2f
        paintEsq.color = cor
        paintEsq.textSize = tamValor
        canvas.drawText(valor, x, base, paintEsq)
        if (unidade.isNotEmpty()) {
            x += lValor + vao
            paintUnidade.color = corRotulo
            paintUnidade.textSize = tamUnidade
            canvas.drawText(unidade, x, base, paintUnidade)
        }
    }

    /** Barra de nível fina embaixo de um valor da linha B; vermelha abaixo de 12%. */
    private fun barraFina(canvas: Canvas, cx: Float, largura: Float, h: Float, fracao: Float) {
        val altura = h * 0.022f
        val y = h * LINHA_B_BARRA
        retangulo.set(cx - largura / 2f, y, cx + largura / 2f, y + altura)
        paintForma.style = Paint.Style.FILL
        paintForma.color = Color.rgb(50, 56, 64)
        canvas.drawRoundRect(retangulo, altura / 2f, altura / 2f, paintForma)
        val f = fracao.coerceIn(0f, 1f)
        if (f <= 0f) return
        retangulo.right = retangulo.left + largura * f
        paintForma.color = if (f < 0.12f) corAlerta else corPrincipal
        canvas.drawRoundRect(retangulo, altura / 2f, altura / 2f, paintForma)
    }

    private fun centralizado(canvas: Canvas, texto: String, cx: Float, base: Float, paint: Paint) {
        val alinhamento = paint.textAlign
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(texto, cx - paint.measureText(texto) / 2f, base, paint)
        paint.textAlign = alinhamento
    }

    /** [texto] cortado com "…" para caber em [largura], no corpo que [paint] já tem. */
    private fun reticencias(texto: String, paint: TextPaint, largura: Float): String =
            TextUtils.ellipsize(texto, paint, largura, TextUtils.TruncateAt.END).toString()

    /**
     * Até [maxLinhas] linhas, quebrando entre palavras; a última termina em "…" se sobrar texto.
     * Uma palavra maior do que a linha inteira é partida onde couber.
     */
    private fun quebrarEmLinhas(
            texto: String,
            paint: TextPaint,
            largura: Float,
            maxLinhas: Int
    ): List<String> {
        val linhas = mutableListOf<String>()
        var resto = texto.trim()
        while (resto.isNotEmpty() && linhas.size < maxLinhas) {
            if (linhas.size == maxLinhas - 1 || paint.measureText(resto) <= largura) {
                linhas += reticencias(resto, paint, largura)
                break
            }
            var corte = paint.breakText(resto, true, largura, null).coerceAtLeast(1)
            val espaco = resto.lastIndexOf(' ', corte)
            if (espaco > 0) corte = espaco
            linhas += resto.substring(0, corte).trim()
            resto = resto.substring(corte).trim()
        }
        return linhas
    }

    private fun corComAlfa(cor: Int, alfa: Float): Int =
            Color.argb(
                    (255 * alfa.coerceIn(0f, 1f)).toInt(),
                    Color.red(cor),
                    Color.green(cor),
                    Color.blue(cor)
            )

    companion object {
        /** Azul do Controle do HUD (Alpha 9.5), o mesmo do disco do Círculo do painel. */
        internal val COR_DO_CONTROLE = Color.rgb(79, 195, 247)

        /** Tempo da entrada e da saída da cortina de alerta. */
        internal const val DURACAO_FADE_MS = 320L

        /** De quanto em quanto tempo o alerta em exibição troca, quando há mais de um. */
        internal const val RODIZIO_MS = 2000L

        /** Um ciclo do aro pulsando dos alertas graves. */
        private const val PERIODO_DO_PULSO_MS = 1200L

        /** Intervalo entre quadros do pulso (~30 por segundo). */
        private const val QUADRO_DO_PULSO_MS = 33L

        private val PT_BR = Locale("pt", "BR")

        /** Entre os itens de uma lista no detalhe do alerta (as portas abertas). */
        private const val SEPARADOR = " · "

        // ---- A grade das páginas de números, em frações do HUD ------------------------------
        //
        // Uma grade só para consumo, powertrain e veículo: cabeçalho discreto no alto, linha A
        // (o valor grande), linha B (três ou dois valores menores) e a faixa dos pontinhos no
        // pé, que nada invade. A margem lateral deixa todo conteúdo longe da borda da ótica.

        /** Margem lateral, em fração da largura. */
        private const val MARGEM = 0.06f

        /** Faixa vertical do conteúdo, entre o cabeçalho e os pontinhos. */
        private const val TOPO_CONTEUDO = 0.17f
        private const val BASE_CONTEUDO = 0.89f

        private const val TAM_CABECALHO = 0.066f
        private const val LINHA_CABECALHO = 0.115f

        private const val TAM_ROTULO = 0.056f
        private const val LINHA_A_ROTULO = 0.265f
        private const val LINHA_A_VALOR = 0.535f
        private const val TAM_A = 0.23f
        private const val LINHA_B_ROTULO = 0.67f
        private const val LINHA_B_VALOR = 0.835f
        private const val LINHA_B_BARRA = 0.87f
        private const val TAM_B = 0.135f

        /** Linha A sozinha (tema sem linha B): mais baixa e maior, no meio do conteúdo. */
        private const val LINHA_SO_A_ROTULO = 0.34f
        private const val LINHA_SO_A_VALOR = 0.70f
        private const val TAM_SO_A = 0.28f

        /** A unidade ao lado do valor tem esta fração do corpo dele. */
        private const val PROPORCAO_UNIDADE = 0.42f

        /** Menor corpo que uma unidade pode ter, em fração da altura. */
        private const val TAM_UNIDADE_MIN = 0.068f
    }
}
