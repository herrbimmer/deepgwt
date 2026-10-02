package br.com.opengw.licenca

/**
 * As licenças aplicadas neste carro e o uso de cada uma (Alpha 8.1). Kotlin puro.
 *
 * SALDO POR NÍVEL. Cada nível pago tem saldo = Σ horas das licenças dele − uso; o nível ativo é o
 * MAIOR com saldo (vitalícia = sem fim). O uso consome o nível ativo, licença por licença na
 * ordem da série (a mais antiga primeiro). Exemplo: 100 h de Premium + 20 h de Sport → usa o
 * Premium até zerar, depois cai para o Sport, depois para o Drive. Uma licença nova soma ao saldo
 * do nível dela: renovar antes do fim não perde hora nenhuma.
 *
 * ANTI-REPETIÇÃO. A série de cada licença aplicada fica guardada; o mesmo código de novo é
 * recusado. O estado vive em três cópias (ver [CopiasDaCarteira]); juntar duas é a UNIÃO das
 * séries com o MAIOR uso de cada uma — apagar uma cópia nunca devolve hora, e dois processos
 * contando ao mesmo tempo não somam em dobro.
 *
 * TESTE GRÁTIS (Alpha 8.4). [avaliacaoUsadaSeg] = quanto do teste do Max já foi usado; null = nunca
 * começou. Um por carro: mora na mesma carteira (mesmo VIN, mesmas três cópias) e junta pelo
 * MAIOR, como o uso das licenças — apagar uma cópia não devolve o teste.
 */
data class LicencaAplicada(
    val licenca: Licenca,
    /** O código na forma canônica — reverificado a cada carga contra o VIN. */
    val codigo: String,
    val aplicadaEmMs: Long
)

data class Saldo(
    val nivel: Nivel,
    val totalSeg: Long,
    val usadoSeg: Long,
    val vitalicia: Boolean
) {
    val restanteSeg: Long get() = if (vitalicia) Long.MAX_VALUE else (totalSeg - usadoSeg).coerceAtLeast(0)
    val temSaldo: Boolean get() = vitalicia || restanteSeg > 0

    /** 0.0–1.0 do que resta (1.0 na vitalícia). */
    val fracaoRestante: Double get() = if (vitalicia || totalSeg <= 0) 1.0 else restanteSeg.toDouble() / totalSeg
}

data class Carteira(
    /** Hash do VIN do carro em que as licenças foram aplicadas ([Licenca.hashDoVin]). */
    val vinHash: String? = null,
    val aplicadas: List<LicencaAplicada> = emptyList(),
    /** Série → segundos usados. */
    val usoSeg: Map<Int, Long> = emptyMap(),
    /** Sobe a cada gravação; só para saber qual cópia é a mais nova ao depurar. */
    val geracao: Long = 0,
    /** Segundos usados do teste grátis; null = o teste nunca começou neste carro. */
    val avaliacaoUsadaSeg: Long? = null,
    /** Quando o teste começou (relógio da central; só para mostrar). */
    val avaliacaoIniciadaEmMs: Long? = null
) {
    val avaliacaoComecou: Boolean get() = avaliacaoUsadaSeg != null

    /** O teste começou e ainda tem tempo. */
    val avaliacaoAtiva: Boolean get() = avaliacaoUsadaSeg.let { it != null && it < AVALIACAO_SEG }

    val avaliacaoRestanteSeg: Long get() = (AVALIACAO_SEG - (avaliacaoUsadaSeg ?: 0)).coerceIn(0, AVALIACAO_SEG)

    /** Começa o teste. Null se ele já começou alguma vez neste carro. */
    fun iniciarAvaliacao(agoraMs: Long): Carteira? =
        if (avaliacaoComecou) null else copy(avaliacaoUsadaSeg = 0, avaliacaoIniciadaEmMs = agoraMs)

    /** Desconta do teste (nunca além do que ele tem). [Consumo.esgotou] = o teste acabou agora. */
    fun consumirAvaliacao(segundos: Long): Consumo {
        val usado = avaliacaoUsadaSeg
        if (segundos <= 0 || usado == null || usado >= AVALIACAO_SEG) return Consumo(this, 0, null, false)
        val agora = minOf(segundos, AVALIACAO_SEG - usado)
        val nova = copy(avaliacaoUsadaSeg = usado + agora)
        return Consumo(nova, agora, NIVEL_DA_AVALIACAO, !nova.avaliacaoAtiva)
    }

    val series: Set<Int> get() = aplicadas.mapTo(HashSet()) { it.licenca.serie }

    fun usoDe(serie: Int): Long = usoSeg[serie] ?: 0

    fun saldo(nivel: Nivel): Saldo {
        val doNivel = aplicadas.filter { it.licenca.nivel == nivel }
        return Saldo(
            nivel = nivel,
            totalSeg = doNivel.filterNot { it.licenca.vitalicia }.sumOf { it.licenca.segundos },
            usadoSeg = doNivel.filterNot { it.licenca.vitalicia }.sumOf { usoDe(it.licenca.serie).coerceAtMost(it.licenca.segundos) },
            vitalicia = doNivel.any { it.licenca.vitalicia }
        )
    }

    /** Sport, Premium e Signature, nessa ordem. */
    fun saldos(): List<Saldo> = NIVEIS_PAGOS.map { saldo(it) }

    /** O maior nível com saldo, ou null (= Drive). */
    fun nivelAtivo(): Nivel? = NIVEIS_PAGOS.lastOrNull { saldo(it).temSaldo }

    data class Consumo(val carteira: Carteira, val consumidoSeg: Long, val nivel: Nivel?, val esgotou: Boolean)

    /**
     * Desconta [segundos] do nível ativo. Nunca além do saldo dele: o que sobra não passa para o
     * nível de baixo (quem decide quando o nível cai é o [Portao], com o carro desligado).
     */
    fun consumir(segundos: Long): Consumo {
        val nivel = nivelAtivo()
        if (segundos <= 0 || nivel == null || saldo(nivel).vitalicia) return Consumo(this, 0, nivel, false)
        var falta = segundos
        val uso = usoSeg.toMutableMap()
        aplicadas
            .filter { it.licenca.nivel == nivel && !it.licenca.vitalicia }
            .sortedBy { it.licenca.serie }
            .forEach { a ->
                if (falta <= 0) return@forEach
                val usado = uso[a.licenca.serie] ?: 0
                val livre = (a.licenca.segundos - usado).coerceAtLeast(0)
                val agora = minOf(livre, falta)
                if (agora > 0) {
                    uso[a.licenca.serie] = usado + agora
                    falta -= agora
                }
            }
        val nova = copy(usoSeg = uso)
        return Consumo(nova, segundos - falta, nivel, !nova.saldo(nivel).temSaldo)
    }

    /** Junta [a] (já verificada) à carteira. Null se a série já estava aqui. */
    fun aplicar(a: LicencaAplicada): Carteira? =
        if (a.licenca.serie in series) null else copy(aplicadas = aplicadas + a)

    /** Só as licenças das [series] (as que conferem com o VIN deste carro). */
    fun somente(series: Set<Int>): Carteira = copy(aplicadas = aplicadas.filter { it.licenca.serie in series })

    companion object {
        val NIVEIS_PAGOS = listOf(Nivel.SPORT, Nivel.PREMIUM, Nivel.SIGNATURE)

        /** O teste grátis: 10 minutos de uso (carro ligado e OPEN GW ativo), uma vez por carro. */
        const val AVALIACAO_SEG = 10 * 60L
        /** O teste é da versão completa. */
        val NIVEL_DA_AVALIACAO = Nivel.SIGNATURE

        /**
         * União das séries, maior uso de cada uma, maior geração. O VIN que vale é o do lado que
         * tiver algum; dois VINs diferentes não se misturam — fica o de [a] (a cópia do app).
         */
        fun fundir(a: Carteira, b: Carteira): Carteira {
            if (a.vinHash != null && b.vinHash != null && a.vinHash != b.vinHash) return a
            val porSerie = LinkedHashMap<Int, LicencaAplicada>()
            (a.aplicadas + b.aplicadas).forEach { porSerie.putIfAbsent(it.licenca.serie, it) }
            val uso = HashMap(a.usoSeg)
            b.usoSeg.forEach { (s, v) -> uso[s] = maxOf(uso[s] ?: 0, v) }
            return Carteira(
                vinHash = a.vinHash ?: b.vinHash,
                aplicadas = porSerie.values.sortedBy { it.aplicadaEmMs },
                usoSeg = uso,
                geracao = maxOf(a.geracao, b.geracao),
                avaliacaoUsadaSeg = listOfNotNull(a.avaliacaoUsadaSeg, b.avaliacaoUsadaSeg).maxOrNull(),
                avaliacaoIniciadaEmMs = listOfNotNull(a.avaliacaoIniciadaEmMs, b.avaliacaoIniciadaEmMs).minOrNull()
            )
        }
    }
}

/**
 * Quanto tempo contar a cada tique (Alpha 8.1). Kotlin puro.
 *
 * Conta só entre dois tiques em que o carro estava ligado (READY) e o OPEN GW ativo: a partida e o
 * desligamento caem entre tiques e ficam de fora — a dúvida é a favor de quem usa. O intervalo é
 * medido com o relógio desde o boot (mudar a data/hora não dá nem tira tempo) e limitado a 2× o
 * normal: uma central suspensa ou um processo congelado não viram horas cobradas.
 */
class ContadorDeUso(private val intervaloMs: Long) {
    private var ultimoMs: Long? = null
    private var contavaNoUltimo = false
    private var restoMs = 0L

    /** Segundos inteiros a cobrar agora (o resto em ms fica para o próximo). */
    fun tique(agoraMs: Long, contando: Boolean): Long {
        val ultimo = ultimoMs
        val contava = contavaNoUltimo
        ultimoMs = agoraMs
        contavaNoUltimo = contando
        if (ultimo == null || !contando || !contava) return 0
        restoMs += (agoraMs - ultimo).coerceIn(0, 2 * intervaloMs)
        val seg = restoMs / 1000
        restoMs -= seg * 1000
        return seg
    }
}
