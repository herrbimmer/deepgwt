// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.projection

import br.com.deepgwt.telas.ProjectionGeometry
import br.com.deepgwt.nucleo.Shell
import br.com.deepgwt.nucleo.DeepGwt
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import androidx.compose.runtime.mutableStateOf
import br.com.deepgwt.nucleo.Preferencias
import br.com.deepgwt.ui.screens.PainelEscolhido
/**
 * Dono único da projeção de um pedaço da central no painel.
 *
 * ## O CAMINHO
 *
 * gesto (4 dedos, toque duplo) ou Ajustes
 *   -> [abrirSelecao]: folha de seleção na central, a pessoa arrasta a área
 *   -> [aoEscolherArea]: guarda a área e pede a autorização de captura
 *   -> [ScreenProjectionConsentActivity]: devolve o token do MediaProjection
 *   -> [JanelaDoPainel]: o projetor do cluster abre a janela e entrega a Surface — no vão do
 *      tema customizado ou no círculo da direita do painel de fábrica ([DestinoDaProjecao])
 *   -> VirtualDisplay espelha a central inteira nessa Surface; o RECORTE é feito na
 *      transformação da TextureView, não em software.
 *
 * ## POR QUE O RECORTE NÃO É FEITO QUADRO A QUADRO
 *
 * O caminho óbvio seria ler os quadros com um ImageReader, cortar a região e desenhar. Isso é uma
 * cópia de 1920x720x4 bytes por quadro no CPU, para mostrar uma janelinha — a 30 Hz são 160 MB/s
 * de memória numa central que também está tocando música e desenhando dois painéis. Espelhar a
 * tela inteira e deixar a TextureView escalar e deslocar joga o trabalho todo no compositor, que é
 * quem tem hardware para isso: custo por quadro praticamente zero, e a proporção é preservada pela
 * própria matriz ([ProjectionGeometry.recorteParaCaixa]).
 *
 * ## O QUE É INFERÊNCIA AQUI
 *
 * FATO: `MediaProjection` existe nesta API (28) e espelha o display padrão, que é a central.
 * INFERÊNCIA NÃO VERIFICADA: que `appops set <pacote> PROJECT_MEDIA allow` pelo Shizuku dispense o
 * diálogo de consentimento nesta build AAOS. É uma técnica conhecida nesta faixa de Android, mas
 * não foi confirmada neste carro. Se não funcionar, o diálogo do sistema aparece e a pessoa toca
 * uma vez em "Iniciar agora" — o recurso continua funcionando, só deixa de ser silencioso. E se
 * nem o diálogo existir nesta build, a projeção falha com um aviso claro em vez de sumir.
 */
object ScreenProjectionController {
    private const val TAG = "ScreenProjection"

    enum class Estado { PARADA, SELECIONANDO, AUTORIZANDO, PROJETANDO, FALHA }

    /** A janela que o projetor do cluster oferece para receber o vídeo. */
    interface JanelaDoPainel {
        /**
         * Abre a janela de projeção no painel.
         *
         * [aoFicarPronta] recebe a Surface já dimensionada para [origemLargura]x[origemAltura] e a
         * caixa livre encontrada entre os mostradores. [aoFalhar] recebe o motivo quando não há
         * espaço utilizável ou a janela não pôde ser criada.
         */
        fun abrirJanelaDeProjecao(
            origemLargura: Int,
            origemAltura: Int,
            regiao: ProjectionGeometry.Caixa,
            destino: DestinoDaProjecao,
            aoFicarPronta: (Surface, ProjectionGeometry.Caixa) -> Unit,
            aoFalhar: (String) -> Unit
        )

        /** Fecha a janela e devolve o painel ao tema, sem reiniciar nada. */
        fun fecharJanelaDeProjecao()
    }

    val estado = mutableStateOf(Estado.PARADA)
    val ultimoErro = mutableStateOf<String?>(null)

    /**
     * Avisado (na thread principal) quando uma projeção não sobe. É do círculo do painel: quem
     * segura OK no volante está olhando para o painel, não para o aviso na central.
     */
    @Volatile var aoFalhar: ((String) -> Unit)? = null

    /**
     * Caixa que o tema declarou para receber a projeção (ponte `setProjectionBox`), em pixels do
     * painel, ou `null` se o tema não declarou nada.
     *
     * Mora aqui e não na janela do painel porque quem escreve é a ponte do tema, que roda antes de
     * a janela existir — e porque a declaração tem de sobreviver a um reinício do projetor.
     */
    @Volatile var caixaDeclaradaPeloTema: ProjectionGeometry.Caixa? = null

    private val principal = Handler(Looper.getMainLooper())

    /**
     * A janela do painel, como ESTADO DE COMPOSE e não como campo comum.
     *
     * Era `@Volatile private var`. O botão de projeção da barra inferior nasce apagado
     * (`enabled = projetando || painelPronto`) e o projetor do cluster só se registra depois —
     * mas uma leitura de campo comum não é assinatura: o Compose não tinha como saber que
     * [painelPronto] tinha mudado, não recompunha, e o botão ficava apagado para sempre. Quem
     * usava o carro via um botão que simplesmente não aceitava toque.
     *
     * Um `mutableStateOf` avisa. `@Volatile` sai porque o estado do Compose já publica a escrita
     * para as outras threads pelo próprio sistema de snapshots.
     */
    private val janelaState = mutableStateOf<JanelaDoPainel?>(null)
    private var janela: JanelaDoPainel?
        get() = janelaState.value
        set(valor) {
            janelaState.value = valor
        }
    private var projecao: MediaProjection? = null
    private var displayVirtual: VirtualDisplay? = null
    private var superficie: Surface? = null
    private var regiaoPendente: ProjectionGeometry.Caixa? = null
    private var destinoPendente: DestinoDaProjecao? = null

    /** Para onde a projeção NO AR foi aberta; ver [aoMudarAsEscolhasDoPainel]. */
    private var destinoEmUso: DestinoDaProjecao? = null

    private fun prefs() =
        DeepGwt.contextoProtegido.getSharedPreferences("deepgwt_prefs", Context.MODE_PRIVATE)

    /**
     * O projetor do cluster se registra ao subir e se desregistra ao cair.
     *
     * Se a projeção estava rodando quando o painel caiu (troca de tema, reinício do projetor), ela
     * é encerrada: um VirtualDisplay apontando para uma Surface morta não dá erro, só desenha em
     * lugar nenhum — e ficaria assim para sempre, gastando GPU calada.
     */
    fun registrarJanela(nova: JanelaDoPainel?) {
        val anterior = janela
        janela = nova
        if (nova == null && anterior != null && estado.value == Estado.PROJETANDO) {
            parar("o painel foi reiniciado")
        }
    }

    fun estaProjetando(): Boolean = estado.value == Estado.PROJETANDO

    /**
     * Existe um painel virtual pronto para receber a projeção.
     *
     * Sem ele [abrirSelecao] falha com aviso na tela — o que está certo para um gesto, que não tem
     * como avisar antes. Um BOTÃO tem: com isto o atalho da barra inferior aparece apagado em vez
     * de convidar a um toque que só resulta em erro.
     *
     * Observável: lê [janelaState], então quem compõe este valor recompõe quando o projetor do
     * cluster sobe ou cai. Ver a nota em [janelaState] para o defeito que isso corrigiu.
     */
    val painelPronto: Boolean
        get() = janela != null

    /**
     * Para onde a projeção iria agora, pelo que está ligado em Telas; `null` quando não há destino
     * (painel desligado, ou painel de fábrica sem o círculo ligado).
     */
    fun destinoAtual(): DestinoDaProjecao? = PainelEscolhido.ler(prefs()).destinoDaProjecao

    /** Cada destino guarda a própria última área: a do círculo é quadrada, a do vão é larga. */
    private fun chaveDaRegiao(destino: DestinoDaProjecao): String =
        when (destino) {
            DestinoDaProjecao.VAO_CENTRAL -> Preferencias.PROJECTION_LAST_REGION.chave
            DestinoDaProjecao.CIRCULO_DIREITO -> Preferencias.PROJECTION_LAST_REGION_CIRCLE.chave
        }

    /** Última área escolhida, para a UI poder reoferecer "projetar de novo" sem pedir o arrasto. */
    fun ultimaRegiao(destino: DestinoDaProjecao? = destinoAtual()): ProjectionGeometry.Caixa? {
        if (destino == null) return null
        return lerCaixa(chaveDaRegiao(destino))
    }

    private fun guardarRegiao(c: ProjectionGeometry.Caixa, destino: DestinoDaProjecao) {
        prefs().edit()
            .putString(chaveDaRegiao(destino), "${c.x},${c.y},${c.largura},${c.altura}")
            .apply()
    }

    private fun motivoSemDestino(): String =
        "Ligue \"Círculo do painel\" (painel de fábrica) ou o tema customizado em Telas para " +
            "projetar no painel."

    private fun lerCaixa(chave: String): ProjectionGeometry.Caixa? {
        val cru = prefs().getString(chave, null) ?: return null
        val p = cru.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (p.size != 4) return null
        if (p[2] <= 0 || p[3] <= 0) return null
        return ProjectionGeometry.Caixa(p[0], p[1], p[2], p[3])
    }

    private fun guardarCaixaDoPainel(c: ProjectionGeometry.Caixa) {
        prefs().edit()
            .putString(
                Preferencias.PROJECTION_LAST_PANEL_BOX.chave,
                "${c.x},${c.y},${c.largura},${c.altura}"
            )
            .apply()
    }

    /**
     * Largura/altura do vão do painel, para a folha de seleção nascer com a proporção do destino.
     *
     * Na ordem: o que o tema declarou agora (exato), o vão da última projeção (o tema costuma ser
     * o mesmo) e, só então, a proporção da caixa central de último recurso. O terceiro caso só
     * acontece na primeiríssima projeção de um tema que não declara nada — e mesmo aí o retângulo
     * tem a forma certa para a caixa que o app vai acabar usando.
     */
    fun proporcaoDaCaixaDoPainel(destino: DestinoDaProjecao? = destinoAtual()): Float {
        // O círculo é redondo: a seleção na central é o quadrado que o circunscreve.
        if (destino == DestinoDaProjecao.CIRCULO_DIREITO) return 1f
        val caixa = caixaDeclaradaPeloTema
            ?: lerCaixa(Preferencias.PROJECTION_LAST_PANEL_BOX.chave)
        if (caixa != null && caixa.largura > 0 && caixa.altura > 0) {
            return caixa.largura.toFloat() / caixa.altura.toFloat()
        }
        // Mesmas frações da caixa padrão de ClusterProjectionWindow (0,30 x 0,34 da tela).
        return (1920f * 0.30f) / (720f * 0.34f)
    }

    /**
     * Abre a folha de seleção. Se já estiver projetando, o gesto vira "encerrar": repetir o mesmo
     * gesto para desfazer é o que a mão espera, e evita ter de achar o botão.
     */
    fun abrirSelecao() {
        principal.post {
            if (estado.value == Estado.PROJETANDO) {
                parar("o gesto foi repetido")
                return@post
            }
            if (estado.value == Estado.SELECIONANDO || estado.value == Estado.AUTORIZANDO) return@post
            if (janela == null) {
                falhar("O painel virtual precisa estar ligado para receber a projeção.")
                return@post
            }
            val destino = destinoAtual()
            if (destino == null) {
                falhar(motivoSemDestino())
                return@post
            }
            estado.value = Estado.SELECIONANDO
            ProjectionOverlays.mostrarSelecao(
                proporcaoDoPainel = proporcaoDaCaixaDoPainel(destino),
                inicial = ultimaRegiao(destino),
                circular = destino == DestinoDaProjecao.CIRCULO_DIREITO,
                aoEscolher = { area ->
                    ProjectionOverlays.esconderSelecao()
                    aoEscolherArea(area, destino)
                },
                aoCancelar = {
                    if (estado.value == Estado.SELECIONANDO) estado.value = Estado.PARADA
                }
            )
        }
    }

    /** Recomeça com a última área, sem pedir o arrasto de novo. */
    fun reprojetarUltimaArea(): Boolean {
        val destino = destinoAtual() ?: return false
        val area = ultimaRegiao(destino) ?: return false
        principal.post { aoEscolherArea(area, destino) }
        return true
    }

    private fun aoEscolherArea(area: ProjectionGeometry.Caixa, destino: DestinoDaProjecao) {
        guardarRegiao(area, destino)
        regiaoPendente = area
        destinoPendente = destino
        estado.value = Estado.AUTORIZANDO
        ultimoErro.value = null

        val existente = projecao
        if (existente != null) {
            // Já autorizado nesta sessão: não faz sentido pedir de novo.
            comecarComProjecao(existente, area, destino)
            return
        }
        preAutorizarPeloShizuku()
        ScreenProjectionConsentActivity.pedirToken(DeepGwt.contexto)
    }

    /**
     * Tenta dispensar o diálogo de consentimento pelo Shizuku.
     *
     * Best-effort de propósito: se o appop não existir nesta build, o comando falha em silêncio e o
     * diálogo aparece — que é uma degradação aceitável, não um erro. Falhar ruidosamente aqui só
     * assustaria quem veria a projeção funcionar em seguida.
     */
    private fun preAutorizarPeloShizuku() {
        if (!Shell.disponivel()) return
        runCatching {
            val pacote = DeepGwt.contexto.packageName
            Shell.executar(
                arrayOf("sh", "-c", "appops set $pacote PROJECT_MEDIA allow 2>/dev/null || true")
            )
        }.onFailure { Log.w(TAG, "appops PROJECT_MEDIA não aplicou", it) }
    }

    /** Chamado pela [ScreenProjectionConsentActivity] com o resultado do pedido de captura. */
    fun aoReceberToken(resultCode: Int, data: Intent?) {
        principal.post {
            val area = regiaoPendente
            val destino = destinoPendente
            if (area == null || destino == null) {
                estado.value = Estado.PARADA
                return@post
            }
            if (data == null || resultCode != android.app.Activity.RESULT_OK) {
                falhar("A captura da tela não foi autorizada.")
                return@post
            }
            val gerente = DeepGwt.contexto
                .getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            if (gerente == null) {
                falhar("Esta central não expõe o serviço de captura de tela.")
                return@post
            }
            val nova = runCatching { gerente.getMediaProjection(resultCode, data) }.getOrNull()
            if (nova == null) {
                falhar("Não foi possível abrir a captura da tela.")
                return@post
            }
            projecao = nova
            nova.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    // O sistema pode encerrar a captura por conta própria (outro app pediu, ou a
                    // central revogou). Sem tratar isso, o botão de encerrar ficaria na tela
                    // prometendo desligar algo que já morreu. A autorização morta também sai:
                    // reaproveitá-la ("Repetir a última área", segurar OK no círculo) faria o
                    // createVirtualDisplay falhar em vez de pedir uma nova.
                    principal.post {
                        if (projecao === nova) projecao = null
                        parar("a central encerrou a captura")
                    }
                }
            }, principal)
            comecarComProjecao(nova, area, destino)
        }
    }

    private fun comecarComProjecao(
        mp: MediaProjection,
        area: ProjectionGeometry.Caixa,
        destino: DestinoDaProjecao
    ) {
        val alvo = janela
        if (alvo == null) {
            falhar("O painel virtual precisa estar ligado para receber a projeção.")
            return
        }
        val (larguraTela, alturaTela) = ProjectionOverlays.tamanhoDaTela()
        if (larguraTela <= 0 || alturaTela <= 0) {
            falhar("Não foi possível medir a tela da central.")
            return
        }
        alvo.abrirJanelaDeProjecao(
            origemLargura = larguraTela,
            origemAltura = alturaTela,
            regiao = area,
            destino = destino,
            aoFicarPronta = { surface, caixa ->
                principal.post { ligarDisplayVirtual(mp, surface, larguraTela, alturaTela, caixa, destino) }
            },
            aoFalhar = { motivo -> principal.post { falhar(motivo) } }
        )
    }

    private fun ligarDisplayVirtual(
        mp: MediaProjection,
        surface: Surface,
        larguraTela: Int,
        alturaTela: Int,
        caixa: ProjectionGeometry.Caixa,
        destino: DestinoDaProjecao
    ) {
        descartarDisplayVirtual()
        superficie = surface
        val dpi = DeepGwt.contexto.resources.displayMetrics.densityDpi
        val vd = runCatching {
            mp.createVirtualDisplay(
                "DeepGWT-projecao",
                larguraTela,
                alturaTela,
                dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                principal
            )
        }.getOrElse {
            Log.e(TAG, "createVirtualDisplay falhou", it)
            null
        }
        if (vd == null) {
            falhar("A central recusou criar o espelho da tela.")
            return
        }
        displayVirtual = vd
        // A proporção deste vão é o que faz a PRÓXIMA seleção nascer com a forma certa. Só o vão
        // do tema: o círculo é sempre quadrado e não pode ensinar essa forma ao tema customizado.
        if (destino == DestinoDaProjecao.VAO_CENTRAL) guardarCaixaDoPainel(caixa)
        estado.value = Estado.PROJETANDO
        ultimoErro.value = null
        regiaoPendente = null
        destinoPendente = null
        destinoEmUso = destino
        Log.w(TAG, "Projeção ligada: área $caixa no painel ($destino), origem ${larguraTela}x$alturaTela")
        ProjectionOverlays.mostrarBotaoDeEncerrar { parar("encerrada pelo botão") }
    }

    private fun descartarDisplayVirtual() {
        runCatching { displayVirtual?.release() }
        displayVirtual = null
        superficie = null
    }

    /**
     * Encerra tudo e devolve o painel ao tema.
     *
     * A ordem importa: primeiro a janela do painel sai (o tema volta a ocupar a caixa), depois o
     * display virtual é liberado. Ao contrário, o espelho escreveria por um instante numa Surface
     * já desmontada — o que não estoura, mas deixa um quadro preto piscando no painel.
     */
    fun parar(motivo: String) {
        principal.post {
            if (estado.value == Estado.PARADA && displayVirtual == null) return@post
            Log.w(TAG, "Projeção encerrada: $motivo")
            ProjectionOverlays.esconderBotaoDeEncerrar()
            ProjectionOverlays.esconderSelecao()
            runCatching { janela?.fecharJanelaDeProjecao() }
            descartarDisplayVirtual()
            // A autorização de captura NÃO é derrubada: mantê-la viva é o que faz a próxima
            // projeção subir sem novo diálogo, e ela não custa nada parada.
            regiaoPendente = null
            destinoPendente = null
            destinoEmUso = null
            estado.value = Estado.PARADA
        }
    }

    /**
     * Telas mudou o que está ligado no painel. Se a projeção no ar perdeu o lugar dela — o tema
     * customizado foi ligado com ela no círculo, o círculo foi desligado —, ela é encerrada: a
     * janela aberta no lugar antigo continuaria desenhando por cima de um painel que não tem mais
     * aquele espaço.
     */
    fun aoMudarAsEscolhasDoPainel() {
        principal.post {
            if (estado.value != Estado.PROJETANDO) return@post
            val agora = destinoAtual()
            if (agora != destinoEmUso) parar("o lugar da projeção mudou em Telas ($destinoEmUso -> $agora)")
        }
    }

    /** Derruba também a autorização. Só no encerramento do app/serviço. */
    fun liberarTudo() {
        parar("o app está encerrando")
        principal.post {
            runCatching { projecao?.stop() }
            projecao = null
        }
    }

    private fun falhar(motivo: String) {
        Log.w(TAG, "Projeção não subiu: $motivo")
        ultimoErro.value = motivo
        estado.value = Estado.FALHA
        ProjectionOverlays.esconderSelecao()
        ProjectionOverlays.esconderBotaoDeEncerrar()
        runCatching { janela?.fecharJanelaDeProjecao() }
        descartarDisplayVirtual()
        regiaoPendente = null
        destinoPendente = null
        destinoEmUso = null
        ProjectionOverlays.avisar(motivo)
        runCatching { aoFalhar?.invoke(motivo) }.onFailure { Log.w(TAG, "aoFalhar", it) }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
