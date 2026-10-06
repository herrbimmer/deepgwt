// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.barra

import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** As regras puras do que se vê e se toca (interface casos 3–5, 9, 16–19, 23–24, 28–29). */
internal object RegrasDaInterface {

    // ── Deslizes (casos 3–4; I-D3: px, não dp) ─────────────────────────────────────────

    const val LIMIAR_PARA_CIMA_PX = -45f
    const val LIMIAR_PARA_BAIXO_PX = 20f
    const val LIMIAR_DO_GATILHO_PX = -30f

    enum class Deslize { NADA, PARA_CIMA, PARA_BAIXO }

    /** Caso 3: a soma vertical desde a descida decide (negativo = para cima). */
    fun deslizeDaBarra(soma: Float): Deslize = when {
        soma < LIMIAR_PARA_CIMA_PX -> Deslize.PARA_CIMA
        soma > LIMIAR_PARA_BAIXO_PX -> Deslize.PARA_BAIXO
        else -> Deslize.NADA
    }

    /** Caso 4: na faixa de gatilho, mais de 30 px para cima traz a barra. */
    fun gatilhoTrazABarra(soma: Float): Boolean = soma < LIMIAR_DO_GATILHO_PX

    /** Caso 5: o pacote do gesto para cima (null = abrir o painel). */
    fun pacoteDoGesto(gesto: GestoParaCima, pacoteDoGesto: String): String? = when (gesto) {
        GestoParaCima.HOME_DE_FABRICA -> "com.beantechs.mediacenter"
        GestoParaCima.LISTA_DE_APPS -> "com.beantechs.applist"
        GestoParaCima.APP_ESPECIFICO -> pacoteDoGesto.takeIf { it.isNotBlank() }
        else -> null
    }

    // ── Distribuição das fatias (casos 2, 7–12) ────────────────────────────────────────

    const val LARGURA_MINIMA_DA_TEMPERATURA_DP = 100
    const val LARGURA_MINIMA_DO_VOLUME_DP = 88
    const val LARGURA_MINIMA_DO_MODO_DP = 112
    /** Alvo de toque das setas (o piso do kit, `TouchTarget.Minimum`). */
    const val ALVO_DA_SETA_DP = 44
    const val ESPACO_ENTRE_FATIAS_DP = 12
    const val LARGURA_DO_ATALHO_DP = 64
    const val ESPACO_DO_TRIO_DP = 4
    const val MARGEM_INICIAL_DP = 8
    const val MARGEM_FINAL_DP = 12

    /** O trio central: quatro atalhos de 64 dp com 4 dp entre eles. */
    const val LARGURA_DO_TRIO_DP = 4 * LARGURA_DO_ATALHO_DP + 3 * ESPACO_DO_TRIO_DP

    /** A largura mínima de uma fatia (temperatura = ▼ + caixinha + ▲). */
    fun larguraMinimaDaFatiaDp(f: Fatia): Int = when (f) {
        Fatia.TEMPERATURA_MOTORISTA, Fatia.TEMPERATURA_PASSAGEIRO -> 2 * ALVO_DA_SETA_DP + LARGURA_MINIMA_DA_TEMPERATURA_DP
        Fatia.VOLUME -> LARGURA_MINIMA_DO_VOLUME_DP
        Fatia.MODO -> LARGURA_MINIMA_DO_MODO_DP
    }

    /** A largura mínima de um lado (fatias + 12 dp entre elas). */
    fun larguraMinimaDoLadoDp(fatias: List<Fatia>): Int =
        fatias.sumOf { larguraMinimaDaFatiaDp(it) } + ESPACO_ENTRE_FATIAS_DP * max(fatias.size - 1, 0)

    /**
     * A largura de cada coluna lateral (as duas têm o mesmo peso; o trio fica no centro): a faixa
     * menos o buraco do AA, a margem inicial (`max(recuo − buraco, 0) + 8 dp`), a final (12 dp), o
     * trio e os 12 dp de cada lado do trio.
     */
    fun larguraDoLadoDp(larguraDaJanelaDp: Float, recuoEsquerdoDp: Float, buracoDp: Float): Float {
        val faixa = larguraDaJanelaDp - buracoDp
        val margens = max(recuoEsquerdoDp - buracoDp, 0f) + MARGEM_INICIAL_DP + MARGEM_FINAL_DP
        return (faixa - margens - LARGURA_DO_TRIO_DP) / 2f
    }

    /** As fatias dos dois lados cabem sem cortar? */
    fun fatiasCabem(fatias: FatiasDaBarra, larguraDaJanelaDp: Float, recuoEsquerdoDp: Float, buracoDp: Float): Boolean {
        val lado = larguraDoLadoDp(larguraDaJanelaDp, recuoEsquerdoDp, buracoDp)
        return larguraMinimaDoLadoDp(fatias.esquerda) <= lado && larguraMinimaDoLadoDp(fatias.direita) <= lado
    }

    // ── Textos e ícones das fatias ─────────────────────────────────────────────────────

    /** O número da temperatura na caixinha: `"%.1f°"`, ou `--` inválida. */
    fun textoDaTemperatura(valor: Float?): String = valor?.let { String.format(Locale.US, "%.1f°", it) } ?: "--"

    /** Temperatura escrita no carro: uma casa e ponto. */
    fun temperaturaParaOCarro(valor: Float): String = String.format(Locale.US, "%.1f", valor)

    enum class IconeDoVolume { MUDO, BAIXO, ALTO }

    /** Caso 9: mudo ≤ 0, baixo ≤ 13, alto acima. */
    fun iconeDaFatia(volume: Int): IconeDoVolume = when {
        volume <= 0 -> IconeDoVolume.MUDO
        volume <= 13 -> IconeDoVolume.BAIXO
        else -> IconeDoVolume.ALTO
    }

    /** Caso 28: mudo no início da faixa, baixo com fração < 0,5, alto. */
    fun iconeDoCartao(volume: Int, faixa: IntRange): IconeDoVolume = when {
        volume <= faixa.first -> IconeDoVolume.MUDO
        fracaoDoVolume(volume, faixa) < 0.5f -> IconeDoVolume.BAIXO
        else -> IconeDoVolume.ALTO
    }

    fun fracaoDoVolume(volume: Int, faixa: IntRange): Float {
        val total = faixa.last - faixa.first
        if (total <= 0) return 0f
        return ((volume - faixa.first).toFloat() / total).coerceIn(0f, 1f)
    }

    /** Caso 29: o valor sob o dedo na trilha do cartão. */
    fun valorNoCartao(xPx: Float, larguraPx: Float, faixa: IntRange): Int {
        if (larguraPx <= 0f) return faixa.first
        val fracao = (xPx / larguraPx).coerceIn(0f, 1f)
        return (faixa.first + (fracao * (faixa.last - faixa.first)).roundToInt()).coerceIn(faixa.first, max(faixa.first, faixa.last))
    }

    /** A faixa da fatia de volume (I-D2). */
    fun faixaDaFatiaDeVolume(publicada: String?): IntRange =
        if (DecisoesDaBarra.FATIA_DE_VOLUME_NA_FAIXA_DO_CARRO) ContasDaBarra.faixaDoVolume(publicada, 0..40) else 0..40

    // ── Ventilação do banco (caso 23) ───────────────────────────────────────────────────

    /** Máximo: o primeiro inteiro > 0 limitado a 5 (I-D12: `{3,3}` → 3); senão 3. */
    fun maximoDaVentilacao(texto: String?): Int =
        ContasDaBarra.inteirosDoTexto(texto).firstOrNull()?.takeIf { it > 0 }?.coerceAtMost(5)?.toInt() ?: 3

    fun nivelDaVentilacao(texto: String?, maximo: Int): Int =
        texto?.trim()?.toIntOrNull()?.coerceIn(0, maximo) ?: 0

    /** Com o máximo limitado a 3: nível ≥ máximo → 0, senão +1. */
    fun proximoNivelDaVentilacao(nivelTexto: String?, maximoTexto: String?): Int {
        val maximo = maximoDaVentilacao(maximoTexto)
        val nivel = nivelDaVentilacao(nivelTexto, maximo)
        val teto = maximo.coerceAtMost(3)
        return if (nivel >= teto) 0 else nivel + 1
    }

    /** Quantidade de barrinhas: o máximo limitado a 1..3. */
    fun barrinhasDaVentilacao(maximoTexto: String?): Int = maximoDaVentilacao(maximoTexto).coerceIn(1, 3)

    // ── Passo de temperatura do Clima e do painel (caso 24) ────────────────────────────

    /** Base = valor publicado (número) ou 22,0; novo = base + delta em 16..32 (I-D9: no degrau). */
    fun passoDeTemperatura(publicado: String?, delta: Float, encaixar: Boolean = DecisoesDaBarra.PASSO_DO_CLIMA_ENCAIXA_NO_DEGRAU): Float {
        val base = publicado?.trim()?.toFloatOrNull()?.takeIf { it.isFinite() } ?: 22.0f
        val bruto = (base + delta).coerceIn(ContasDaBarra.TEMPERATURA_MINIMA, ContasDaBarra.TEMPERATURA_MAXIMA)
        return if (encaixar) {
            ContasDaBarra.encaixarNoDegrau(bruto, ContasDaBarra.TEMPERATURA_MINIMA, ContasDaBarra.TEMPERATURA_MAXIMA, ContasDaBarra.PASSO_DA_TEMPERATURA)
        } else {
            bruto
        }
    }

    // ── Menu Apps (casos 16–19) ─────────────────────────────────────────────────────────

    const val PREDEFINIDO_CARPLAY = "com.ts.carplay.app"
    const val PREDEFINIDO_AA = "com.ts.androidauto.app"

    /** Caso 16: os configurados e, no fim, CarPlay e AA predefinidos que faltam (ordem dos predefinidos). */
    fun listaDeApps(configurados: List<AppDaBarra>, predefinidos: List<AppDaBarra>): List<AppDaBarra> {
        val presentes = configurados.map { it.pacote }.toHashSet()
        val extras = predefinidos.filter {
            (it.pacote == PREDEFINIDO_CARPLAY || it.pacote == PREDEFINIDO_AA) && it.pacote !in presentes
        }.distinctBy { it.pacote }
        return configurados + extras
    }

    /** Caso 16: mostrar "Adicionar" para o app da frente? */
    fun mostrarAdicionar(pacoteNoDisplay0: String, lista: List<AppDaBarra>, proprio: String): Boolean =
        pacoteNoDisplay0.isNotBlank() && pacoteNoDisplay0 != proprio && lista.none { it.pacote == pacoteNoDisplay0 }

    /** Caso 18: posição (linha a partir de CIMA, coluna) do índice, preenchendo de baixo para cima. */
    fun posicaoNaGrade(indice: Int, total: Int, colunas: Int = 3): Pair<Int, Int> {
        val linhas = max((total + colunas - 1) / colunas, 1)
        return (linhas - 1 - indice / colunas) to (indice % colunas)
    }

    /**
     * Caso 19: o item cuja zona central (60% interno) contém o centro do arrastado; null se nenhum
     * (ou se é ele mesmo).
     */
    fun alvoDaTroca(centroX: Float, centroY: Float, itens: List<Retangulo>, arrastado: Int): Int? {
        itens.forEachIndexed { i, r ->
            if (i == arrastado || r.vazio) return@forEachIndexed
            val margemX = (r.direita - r.esquerda) * 0.2f
            val margemY = (r.base - r.topo) * 0.2f
            if (centroX >= r.esquerda + margemX && centroX <= r.direita - margemX &&
                centroY >= r.topo + margemY && centroY <= r.base - margemY
            ) return i
        }
        return null
    }

    /** Troca dois de lugar (para salvar a ordem). */
    fun <T> trocar(lista: List<T>, de: Int, para: Int): List<T> {
        if (de !in lista.indices || para !in lista.indices || de == para) return lista
        return lista.toMutableList().apply {
            val x = this[de]
            this[de] = this[para]
            this[para] = x
        }
    }

    /** A cor do ícone substituto (`#RRGGBB`/`#AARRGGBB`) como ARGB; inválida → null. */
    fun corDoIcone(texto: String?): Int? {
        val t = texto?.trim()?.removePrefix("#") ?: return null
        val v = t.toLongOrNull(16) ?: return null
        return when (t.length) {
            6 -> (0xFF000000 or v).toInt()
            8 -> v.toInt()
            else -> null
        }
    }

    // ── Condução (casos 20–21) ──────────────────────────────────────────────────────────

    @JvmField
    val ENERGIA: List<ModoDeConducao> = listOf(ModoDeConducao("0", "HEV"), ModoDeConducao("1", "EV Prioritário"), ModoDeConducao("3", "EV"))

    @JvmField
    val REGENERACAO: List<ModoDeConducao> = listOf(ModoDeConducao("2", "Baixo"), ModoDeConducao("0", "Normal"), ModoDeConducao("1", "Alto"))

    @JvmField
    val DIRECAO: List<ModoDeConducao> = listOf(ModoDeConducao("2", "Conforto"), ModoDeConducao("0", "Normal"), ModoDeConducao("1", "Esportiva"))

    /** O texto do cartão do SPORT+ (caso 21). */
    fun linhaDoSportPlus(ligado: Boolean, confirmando: Boolean, espCru: String?): String = when {
        confirmando -> "Vai desligar o ESP, forçar o motor a combustão e ligar o modo travessia."
        ligado -> "ESP desligado e motor a combustão forçado."
        espCru?.trim() != "0" -> "Modo Sport com ESP desligado. ESP agora: ligado."
        else -> "Modo Sport com ESP desligado. ESP agora: desligado."
    }

    const val CONFIRMACAO_DO_SPORT_PLUS_MS = 6_000L

    // ── Clima (caso 22) ─────────────────────────────────────────────────────────────────

    @JvmField
    val DIRECOES_DO_AR: List<ModoDeConducao> = listOf(
        ModoDeConducao("2", "Pés"), ModoDeConducao("1", "Rosto/Pés"), ModoDeConducao("0", "Rosto"), ModoDeConducao("3", "Vidro/Pés"),
    )

    /** O texto do mostrador: `"%.1f°C"` ou `--` desligado ou inválido. */
    fun textoDoMostrador(publicado: String?, ligado: Boolean): String {
        if (!ligado) return "--"
        return ContasDaBarra.temperaturaOuNulo(publicado)?.let { String.format(Locale.US, "%.1f°C", it) } ?: "--"
    }

    // ── Cartão de volume (caso 26) ─────────────────────────────────────────────────────

    /** Fecha sozinho em 3,0 s (3,5 s se aberto pelo carro). */
    fun tempoDoCartaoMs(abertoPeloCarro: Boolean): Long = if (abertoPeloCarro) 3_500L else 3_000L

    /** O texto do logo do cartão (caso 28); null = a imagem da GWM. */
    fun textoDoLogo(tipo: String, personalizado: String): String? = when (tipo) {
        "jbl" -> "JBL"
        "hertz" -> "Hertz"
        "custom" -> personalizado.ifBlank { "GWM" }
        else -> null
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
