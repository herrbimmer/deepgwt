package br.com.opengw.macros

import android.util.Log

/**
 * Do traço cru ao gesto gravado — e daí à macro.
 *
 * ## Por que dois dedos é o padrão
 *
 * O leitor de `/dev/input` NÃO consome o toque (ver `QuickGesturesManager`): ele enxerga o mesmo
 * dedo que o app da frente está usando. Com um dedo, todo arrasto da tela — rolar uma lista,
 * arrastar um mapa, deslizar a barra — passaria por aqui como candidato a gesto. O reconhecedor
 * rejeitaria a maioria, mas "a maioria" não é bom o bastante para algo que fecha vidro e liga
 * modo Sport: basta um mapa arrastado num arco para a nota subir.
 *
 * Com dois dedos o espaço fica praticamente livre. As duas coisas que o sistema faz com dois dedos
 * são pinça (zoom) e rolagem de mapa; a pinça é descartada explicitamente em
 * [pareceUmaPinca], e a rolagem só casaria se tivesse a forma exata de um gesto
 * gravado, o que um arrasto reto não tem.
 *
 * Um dedo continua disponível — quem quer, escolhe — mas com nota mínima mais alta
 * ([NOTA_MINIMA_1_DEDO]) e com o aviso na tela de gravação.
 *
 * ## Por que a margem sobre o segundo colocado
 *
 * Duas macros com gestos parecidos ("C" e "(") produzem notas próximas. Executar a mais bem
 * colocada por 0,002 seria escolher no ruído: o traço que o motorista fez não distingue as duas.
 * Sem a [MARGEM], nada roda — e o motorista repete o gesto com mais capricho, que é o
 * comportamento correto. A tela de gravação avisa quando um gesto novo nasce perto demais de um
 * que já existe, justamente para essa situação ser rara.
 */
object MacroGestureRouter {

    private const val TAG = "MacroGestures"

    private const val NOTA_MINIMA_2_DEDOS = 0.80f
    private const val NOTA_MINIMA_1_DEDO = 0.88f

    /** Vantagem mínima sobre o segundo colocado para o primeiro valer. */
    private const val MARGEM = 0.04f

    /**
     * Comprimento mínimo do traço, em frações de tela somadas.
     *
     * Um toque e um arrasto de 2 cm não são desenhos. Sem este piso, o reconhecedor receberia
     * traços de três pontos, onde qualquer coisa casa com qualquer coisa.
     */
    private const val COMPRIMENTO_MINIMO = 0.35f

    /** Quanto a distância entre dois dedos pode variar sem o gesto virar pinça. */
    private const val VARIACAO_PINCA = 0.35f

    data class Casamento(val gesto: GestureTemplate, val nota: Float)

    /**
     * Avalia um traço já normalizado para 0..1 em cada eixo (ver `QuickGesturesManager`).
     * Devolve o gesto reconhecido, ou `null` — e o `null` é a resposta esperada na maioria dos
     * traços que chegam aqui.
     */
    fun reconhecer(caminho: FloatArray, dedos: Int): GestureTemplate? {
        if (!GestureStore.temGestosAtivos()) return null
        if (GestureRecognizer.comprimento(caminho) < COMPRIMENTO_MINIMO) return null
        val modelo = GestureRecognizer.preparar(caminho) ?: return null

        val candidatos = GestureStore.gestos
            .filter { it.dedos == dedos }
            .mapNotNull { g -> g.modelo?.let { Casamento(g, GestureRecognizer.comparar(modelo, it)) } }
            .sortedByDescending { it.nota }
        if (candidatos.isEmpty()) return null

        val melhor = candidatos.first()
        val piso = if (dedos <= 1) NOTA_MINIMA_1_DEDO else NOTA_MINIMA_2_DEDOS
        if (melhor.nota < piso) {
            Log.w(TAG, "Traço descartado: melhor foi '${melhor.gesto.name}' com ${"%.2f".format(melhor.nota)} (piso $piso)")
            return null
        }
        val segundo = candidatos.getOrNull(1)
        if (segundo != null && melhor.nota - segundo.nota < MARGEM) {
            Log.w(TAG, "Traço ambíguo entre '${melhor.gesto.name}' e '${segundo.gesto.name}'; nada executado")
            return null
        }
        Log.w(TAG, "Gesto '${melhor.gesto.name}' reconhecido com ${"%.2f".format(melhor.nota)}")
        return melhor.gesto
    }

    /** Ponto de entrada do leitor de toque: reconhece e manda executar. */
    fun aoTracar(caminho: FloatArray, dedos: Int) {
        val gesto = reconhecer(caminho, dedos) ?: return
        MacroEngine.executarPorGesto(gesto.id)
    }

    /**
     * Dois dedos afastando ou aproximando = pinça, não desenho.
     *
     * Recebe as distâncias entre os dois dedos no começo e no fim do gesto, na mesma unidade.
     */
    fun pareceUmaPinca(distanciaInicial: Float, distanciaFinal: Float): Boolean {
        if (distanciaInicial <= 0.0001f) return false
        val variacao = kotlin.math.abs(distanciaFinal - distanciaInicial) / distanciaInicial
        return variacao > VARIACAO_PINCA
    }

    /**
     * O gesto gravado mais parecido com este traço, seja qual for a nota.
     *
     * Usado pela tela de gravação em dois momentos: para mostrar ao motorista o quanto o traço dele
     * bate com o que ele acabou de gravar (o teste), e para avisar, na hora de salvar, que o gesto
     * novo é parecido demais com um que já existe.
     */
    fun melhorCasamento(caminho: FloatArray, dedos: Int, ignorarId: String? = null): Casamento? {
        val modelo = GestureRecognizer.preparar(caminho) ?: return null
        return GestureStore.gestos
            .filter { it.dedos == dedos && it.id != ignorarId }
            .mapNotNull { g -> g.modelo?.let { Casamento(g, GestureRecognizer.comparar(modelo, it)) } }
            .maxByOrNull { it.nota }
    }

    /** Acima disso, dois gestos são parecidos demais para conviverem. */
    const val LIMIAR_PARECIDO = 0.82f

    fun notaMinima(dedos: Int): Float = if (dedos <= 1) NOTA_MINIMA_1_DEDO else NOTA_MINIMA_2_DEDOS
}
