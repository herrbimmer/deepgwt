package br.com.deepgwt.tela3d.viagens

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/** O que o motor avisa a quem o usa (quem grava no banco, guarda o ponto na memória etc.). */
sealed class EventoDoMotor {
    data class Abriu(val inicioMs: Long) : EventoDoMotor()
    data class Ponto(val inicioMs: Long, val ponto: PontoDeTrilha) : EventoDoMotor()
    data class Parada(val inicioMs: Long, val parada: ParadaDeViagem) : EventoDoMotor()

    /** A viagem fechou e vale ser gravada ([ResumoDaViagem.vale]). */
    data class Fechou(val resumo: ResumoDaViagem) : EventoDoMotor()

    /** A viagem fechou curta demais: nada vai para as tabelas. */
    data class Descartou(val resumo: ResumoDaViagem) : EventoDoMotor()
}

fun interface OuvinteDoMotor {
    fun aoEvento(evento: EventoDoMotor)
}

/** Última posição aceita do GPS. */
data class PosicaoConhecida(val lat: Double, val lon: Double, val alt: Double, val precisao: Double, val t: Long)

/**
 * O motor da viagem: abre e fecha viagens, integra distância, tempo, combustível e energia, detecta
 * abastecimento e recarga, e decide quando registrar um ponto da trilha. É puro: recebe os sinais em
 * texto e o relógio (`agora`, em ms) por parâmetro, e devolve tudo por [OuvinteDoMotor]. Não é
 * thread-safe: quem o usa chama sempre da mesma thread.
 */
class MotorDeViagem(private val ouvinte: OuvinteDoMotor) {

    companion object {
        const val PASSO_MAXIMO_DE_INTEGRACAO_MS = 10_000L
        const val DESLIGADO_PARA_FECHAR_MS = 600_000L
        const val SUSPENSAO_MS = 600_000L
        const val JANELA_DE_ACOMODACAO_MS = 180_000L
        const val IDADE_MAXIMA_DA_POSICAO_NA_PARTIDA_MS = 30_000L
        const val IDADE_MAXIMA_DA_POSICAO_NO_PONTO_MS = 5_000L
        const val GUIAGEM_SEM_NOTICIA_MS = 3_600_000L
        const val DISTANCIA_DE_CHEGADA_M = 150.0
        const val DISTANCIA_DE_CHEGADA_NO_DESTINO_M = 300.0
        const val INTERVALO_DO_PONTO_ANDANDO_MS = 1_000L
        const val INTERVALO_DO_PONTO_PARADO_MS = 10_000L
        const val VELOCIDADE_ANDANDO_KMH = 1.0
        const val VELOCIDADE_PARA_ARMAR_PARADA_KMH = 5.0
        const val PRECISAO_MAXIMA_DA_POSICAO_M = 50.0
        const val PRECISAO_PADRAO_M = 30.0
        const val PRECISAO_MAXIMA_PARA_ALTITUDE_M = 25.0
        const val HISTERESE_DE_ALTITUDE_M = 3.0
        const val KM_MINIMO_PARA_PARTIR_COM_HODOMETRO = 0.05
        const val KM_MINIMO_DO_HODOMETRO_NO_FIM = 0.1
        const val VELOCIDADE_MAXIMA_DO_HODOMETRO_KMH = 250.0
        const val FOLGA_DO_HODOMETRO_KM = 0.3
        const val TANQUE_DE_REFERENCIA_L = 55.0
        const val SUBIDA_MINIMA_DO_TANQUE = 4.0
        const val SUBIDA_MINIMA_DA_BATERIA = 8.0
        const val INTERVALO_MINIMO_DA_BRUSCA_MS = 400L
        const val INTERVALO_MAXIMO_DA_BRUSCA_MS = 3_000L
        const val ACELERACAO_BRUSCA = 3.0
        const val ACELERACAO_REARMA = 1.5
        const val FRENAGEM_BRUSCA = -3.5
        const val FRENAGEM_REARMA = -1.75
        const val KM_MAXIMO_DE_CORRECAO_DO_HODOMETRO = 0.1
        private const val MS_POR_HORA = 3_600_000.0
    }

    // --- Estado da viagem (aberta ou não) ---
    private var s = EstadoDaViagem()

    // --- O que o carro disse por último (vale com a viagem aberta ou fechada) ---
    private var pronto: Boolean? = null
    private var velocidade = Double.NaN
    private var hodometro = Double.NaN
    private var tensao = Double.NaN
    private var corrente = Double.NaN
    private var consumo = Consumo.SEM_CONSUMO
    private var combustao: Boolean? = null
    private var nivelDoTanque = Double.NaN
    private var nivelDaBateria = Double.NaN
    private var ultimaPosicao: PosicaoConhecida? = null
    private val crusUltimos = LinkedHashMap<String, String>()

    // --- Abastecimento e recarga ---
    private var janelaDesde = 0L
    private var tanqueParado = Double.NaN
    private var bateriaParada = Double.NaN
    private var latParado = Double.NaN
    private var lonParado = Double.NaN
    private var instanteParado = 0L
    private var candidataDoTanque: ParadaDeViagem? = null
    private var candidataDaBateria: ParadaDeViagem? = null
    private val paradasPendentes = ArrayList<ParadaDeViagem>()

    // --- Bruscas (não vão para o checkpoint) ---
    private var amostraConhecida = false
    private var amostraVelocidade = 0.0
    private var amostraT = 0L
    private var emAceleracao = false
    private var emFrenagem = false

    // --- Consulta ---

    /** O estado atual da viagem (para o checkpoint). Só vale ler da mesma thread que alimenta o motor. */
    val estado: EstadoDaViagem get() = s
    val viagemAberta: Boolean get() = s.aberta
    val inicioDaViagemMs: Long get() = s.inicioMs

    /** `true` ligado, `false` desligado, `null` desconhecido. */
    val prontoAtual: Boolean? get() = pronto

    /** Paradas detectadas com a viagem fechada, à espera da próxima abertura. */
    val paradasEmEspera: List<ParadaDeViagem> get() = paradasPendentes.toList()

    /** A guiagem mantém a viagem aberta (C-23). */
    fun guiagemAtiva(agora: Long): Boolean =
        s.aberta && s.guiada && s.instanteDoFimDaGuiagem == 0L &&
            !s.ultimaDistanciaRestanteM.isNaN() && s.ultimaDistanciaRestanteM > DISTANCIA_DE_CHEGADA_NO_DESTINO_M &&
            agora - s.ultimaNavegacaoT < GUIAGEM_SEM_NOTICIA_MS

    // --- Entradas ---

    /** Um sinal do carro, em texto, no instante [agora]. */
    fun aoSinal(chave: String, valor: String?, agora: Long) {
        if (valor == null || !ChavesDeSinais.interessa(chave)) return
        if (ChavesDeSinais.ehCrua(chave)) registrarCru(chave, valor)
        integrar(agora)
        when (chave) {
            ChavesDeSinais.PRONTO -> LeitoresDeSinais.prontoDe(valor)?.let { aoPronto(it, agora) }
            ChavesDeSinais.VELOCIDADE -> aoVelocidade(LeitoresDeSinais.numeroDe(valor), agora)
            ChavesDeSinais.HODOMETRO -> aoHodometro(LeitoresDeSinais.numeroDe(valor), agora)
            ChavesDeSinais.TENSAO_DO_PACOTE -> {
                val v = LeitoresDeSinais.numeroDe(valor)
                tensao = if (LeitoresDeSinais.dentroDaFaixa(v, FaixasDeSinais.TENSAO_MIN, FaixasDeSinais.TENSAO_MAX)) v else Double.NaN
            }
            ChavesDeSinais.CORRENTE_DO_PACOTE -> {
                val v = LeitoresDeSinais.numeroDe(valor)
                corrente = if (LeitoresDeSinais.dentroDaFaixa(v, FaixasDeSinais.CORRENTE_MIN, FaixasDeSinais.CORRENTE_MAX)) v else Double.NaN
            }
            ChavesDeSinais.CONSUMO_INSTANTANEO -> consumo = LeitoresDeSinais.consumoDe(valor)
            ChavesDeSinais.COMBUSTAO -> LeitoresDeSinais.combustaoDe(valor)?.let { combustao = it }
            ChavesDeSinais.FLUXO_DE_POTENCIA -> LeitoresDeSinais.fluxoDe(valor)?.let { combustao = it }
            ChavesDeSinais.NIVEL_DO_TANQUE -> aoNivel(TipoDeParada.ABASTECIMENTO, LeitoresDeSinais.numeroDe(valor), agora)
            ChavesDeSinais.NIVEL_DA_BATERIA -> aoNivel(TipoDeParada.RECARGA, LeitoresDeSinais.numeroDe(valor), agora)
            ChavesDeSinais.NAVEGACAO -> aoNavegacaoEmTexto(valor, agora)
        }
    }

    /**
     * Uma posição do GPS. [alt] e [precisao] NaN = ausentes (a precisão ausente conta 30 m). Posição
     * sem lat/lon ou com precisão pior que 50 m é descartada.
     */
    fun aoPosicao(lat: Double, lon: Double, alt: Double, precisao: Double, agora: Long) {
        integrar(agora)
        val p = if (precisao.isNaN()) PRECISAO_PADRAO_M else precisao
        if (!lat.isFinite() || !lon.isFinite() || p > PRECISAO_MAXIMA_DA_POSICAO_M) return
        ultimaPosicao = PosicaoConhecida(lat, lon, alt, p, agora)
        if (!s.aberta) return
        if (s.latInicial.isNaN() || s.lonInicial.isNaN()) {
            s.latInicial = lat
            s.lonInicial = lon
        }
        s.latFinal = lat
        s.lonFinal = lon
        if (!alt.isNaN() && p <= PRECISAO_MAXIMA_PARA_ALTITUDE_M) {
            val ref = s.referenciaDeAltitude
            when {
                ref.isNaN() -> s.referenciaDeAltitude = alt
                alt > ref + HISTERESE_DE_ALTITUDE_M -> {
                    s.subidaM += alt - ref
                    s.referenciaDeAltitude = alt
                }
                alt < ref - HISTERESE_DE_ALTITUDE_M -> s.referenciaDeAltitude = alt
            }
        }
    }

    /**
     * A chamada de 1 s. [intervaloMonotonicoMs] é o tempo desde o tick anterior medido por um relógio
     * que conta o sono do aparelho: 10 min ou mais quer dizer que o sistema dormiu.
     */
    fun tick(agora: Long, intervaloMonotonicoMs: Long = 1_000L) {
        if (intervaloMonotonicoMs >= SUSPENSAO_MS) acordou(agora)
        integrar(agora)
        if (pronto == true && janelaDesde != 0L && agora - janelaDesde >= JANELA_DE_ACOMODACAO_MS) {
            emitirCandidatas()
            tanqueParado = Double.NaN
            bateriaParada = Double.NaN
            janelaDesde = 0L
        }
        if (s.aberta && pronto != true && s.desligadoEm != 0L &&
            agora - s.desligadoEm >= DESLIGADO_PARA_FECHAR_MS && !guiagemAtiva(agora)
        ) {
            fechar(s.desligadoEm)
        }
        registrarPonto(agora)
    }

    /** FINALIZAR VIAGEM: fecha a aberta e, com READY ligado, abre outra a partir de agora. */
    fun finalizarAgora(agora: Long) {
        integrar(agora)
        if (!s.aberta) return
        fechar(if (s.desligadoEm != 0L) s.desligadoEm else agora)
        if (pronto == true) abrir(agora)
    }

    /** Retoma uma viagem a partir do checkpoint (C-29). Só vale se o estado está aberto. */
    fun restaurar(estado: EstadoDaViagem, agora: Long) {
        if (!estado.aberta) return
        s = estado
        if (s.ultimoT == 0L) s.ultimoT = s.inicioMs
        pronto = null
        if (s.desligadoEm == 0L) s.desligadoEm = s.ultimoT
        for ((k, v) in s.crusUltimos) crusUltimos.putIfAbsent(k, v)
        if (hodometro.isNaN() && !s.odometroUltimo.isNaN()) hodometro = s.odometroUltimo
        if (nivelDoTanque.isNaN() && !s.tanqueFinal.isNaN()) nivelDoTanque = s.tanqueFinal
        if (nivelDaBateria.isNaN() && !s.bateriaFinal.isNaN()) nivelDaBateria = s.bateriaFinal
        if (agora - s.desligadoEm >= DESLIGADO_PARA_FECHAR_MS && !guiagemAtiva(agora)) fechar(s.desligadoEm)
    }

    /**
     * Alimenta o "nível parado" com o fim da última viagem gravada, só onde ainda está vazio (C-26).
     * Valores NaN não alimentam nada.
     */
    fun alimentarNivelParado(tanque: Double, bateria: Double, lat: Double, lon: Double, instante: Long) {
        if (tanqueParado.isNaN() && !tanque.isNaN()) tanqueParado = tanque
        if (bateriaParada.isNaN() && !bateria.isNaN()) bateriaParada = bateria
        if (latParado.isNaN() && !lat.isNaN() && !lon.isNaN()) {
            latParado = lat
            lonParado = lon
        }
        if (instanteParado == 0L) instanteParado = instante
    }

    /** O resumo da viagem aberta agora (km ao vivo); `null` sem viagem aberta. */
    fun resumoAoVivo(agora: Long): ResumoDaViagem? = if (s.aberta) montarResumo(agora, final = false) else null

    // --- Sinais ---

    private fun registrarCru(chave: String, valor: String) {
        crusUltimos[chave] = valor
        if (s.aberta) {
            s.crusPrimeiros.putIfAbsent(chave, valor)
            s.crusUltimos[chave] = valor
        }
    }

    private fun aoPronto(ligado: Boolean, agora: Long) {
        val anterior = pronto
        pronto = ligado
        if (ligado) {
            if (!s.aberta) {
                abrir(agora)
            } else if (s.desligadoEm != 0L) {
                if (agora - s.desligadoEm < DESLIGADO_PARA_FECHAR_MS || guiagemAtiva(agora)) {
                    s.desligadoEm = 0L
                } else {
                    fechar(s.desligadoEm)
                    abrir(agora)
                }
            }
            if (anterior != true) janelaDesde = agora
        } else {
            if (s.aberta && s.desligadoEm == 0L) s.desligadoEm = agora
            if (anterior != false) estacionar(agora)
        }
    }

    private fun aoVelocidade(v: Double, agora: Long) {
        if (!LeitoresDeSinais.dentroDaFaixa(v, FaixasDeSinais.VELOCIDADE_MIN, FaixasDeSinais.VELOCIDADE_MAX)) return
        velocidade = v
        if (!s.aberta || pronto != true) return
        if (v > s.velocidadeMaxima) s.velocidadeMaxima = v
        if (v > VELOCIDADE_PARA_ARMAR_PARADA_KMH) s.armouParada = true
        if (v < VELOCIDADE_ANDANDO_KMH && s.armouParada) {
            s.paradas++
            s.armouParada = false
        }
        registrarBrusca(v, agora)
    }

    private fun registrarBrusca(v: Double, agora: Long) {
        val dt = agora - amostraT
        if (!amostraConhecida || dt < 0L || dt > INTERVALO_MAXIMO_DA_BRUSCA_MS) {
            amostraConhecida = true
            amostraVelocidade = v
            amostraT = agora
            return
        }
        if (dt < INTERVALO_MINIMO_DA_BRUSCA_MS) return
        val a = (v - amostraVelocidade) / 3.6 / (dt / 1000.0)
        if (a >= ACELERACAO_BRUSCA) {
            if (!emAceleracao) {
                s.aceleracoesBruscas++
                emAceleracao = true
            }
        } else if (a < ACELERACAO_REARMA) {
            emAceleracao = false
        }
        if (a <= FRENAGEM_BRUSCA) {
            if (!emFrenagem) {
                s.frenagensBruscas++
                emFrenagem = true
            }
        } else if (a > FRENAGEM_REARMA) {
            emFrenagem = false
        }
        amostraVelocidade = v
        amostraT = agora
    }

    private fun aoHodometro(v: Double, agora: Long) {
        if (!LeitoresDeSinais.dentroDaFaixa(v, FaixasDeSinais.HODOMETRO_MIN, FaixasDeSinais.HODOMETRO_MAX)) return
        hodometro = v
        if (!s.aberta) return
        if (s.odometroUltimo.isNaN()) {
            s.odometroInicial = v
            s.odometroUltimo = v
            s.odometroUltimoT = agora
            s.partiuComHodometro = s.distanciaIntegrada < KM_MINIMO_PARA_PARTIR_COM_HODOMETRO
            s.distanciaIntegradaNoHodometro = s.distanciaIntegrada
            return
        }
        if (v == s.odometroUltimo) return
        val d = v - s.odometroUltimo
        val segundos = maxOf(0L, agora - s.odometroUltimoT) / 1000.0
        val limite = segundos * VELOCIDADE_MAXIMA_DO_HODOMETRO_KMH / 3600.0 + FOLGA_DO_HODOMETRO_KM
        if (d > 0.0 && d <= limite && pronto != false) s.odometroKm += d else s.odometroSaltos++
        s.odometroUltimo = v
        s.odometroUltimoT = agora
        s.distanciaIntegradaNoHodometro = s.distanciaIntegrada
    }

    private fun aoNivel(tipo: TipoDeParada, v: Double, agora: Long) {
        if (!LeitoresDeSinais.dentroDaFaixa(v, FaixasDeSinais.NIVEL_MIN, FaixasDeSinais.NIVEL_MAX)) return
        if (tipo == TipoDeParada.ABASTECIMENTO) {
            nivelDoTanque = v
            if (s.aberta) {
                if (s.tanqueInicial.isNaN()) s.tanqueInicial = v
                s.tanqueFinal = v
            }
        } else {
            nivelDaBateria = v
            if (s.aberta) {
                if (s.bateriaInicial.isNaN()) s.bateriaInicial = v
                s.bateriaFinal = v
            }
        }
        detectar(tipo, v, agora)
    }

    // --- Navegação (dormente: ninguém publica a chave hoje, mas o barramento a prevê) ---

    private fun aoNavegacaoEmTexto(texto: String, agora: Long) {
        val objeto = try {
            Json.parseToJsonElement(texto) as? JsonObject
        } catch (e: Exception) {
            null
        } ?: return
        val ativo = (objeto["active"] as? JsonPrimitive)?.booleanOrNull ?: false
        val restanteM = numeroOuNaN(objeto["remaining_m"])
        val restanteS = numeroOuNaN(objeto["remaining_s"])
        val destino = (objeto["turn"] as? JsonPrimitive)?.takeIf { it.isString }?.content == "DESTINATION"
        aoNavegacao(ativo, restanteM, restanteS, destino, agora)
    }

    private fun numeroOuNaN(elemento: kotlinx.serialization.json.JsonElement?): Double {
        val p = elemento as? JsonPrimitive ?: return Double.NaN
        if (p is JsonNull) return Double.NaN
        val d = p.doubleOrNull ?: return Double.NaN
        return if (d.isFinite()) d else Double.NaN
    }

    /** O sinal de navegação já interpretado. Só vale com viagem aberta. */
    fun aoNavegacao(ativo: Boolean, restanteM: Double, restanteS: Double, destino: Boolean, agora: Long) {
        integrar(agora)
        if (!s.aberta) return
        val emCurso = s.guiada && s.instanteDoFimDaGuiagem == 0L
        if (!ativo) {
            if (emCurso) {
                terminarGuiagem(agora, kmAoVivo(), chegou = chegouPelaUltimaDistancia())
            }
            return
        }
        if (!s.guiada) {
            if (restanteM.isNaN() || restanteM <= 0.0) return
            s.guiada = true
            s.kmPlanejado = restanteM / 1000.0
            s.segundosPlanejados = if (!restanteS.isNaN() && restanteS > 0.0) Math.round(restanteS).toInt() else 0
            s.kmNoInicioDaGuiagem = kmAoVivo()
            s.instanteDoInicioDaGuiagem = agora
        }
        s.ultimaNavegacaoT = agora
        if (s.instanteDoFimDaGuiagem == 0L && !restanteM.isNaN()) {
            s.ultimaDistanciaRestanteM = restanteM
            if (restanteM <= DISTANCIA_DE_CHEGADA_M || (destino && restanteM <= DISTANCIA_DE_CHEGADA_NO_DESTINO_M)) {
                terminarGuiagem(agora, kmAoVivo(), chegou = true)
            }
        }
    }

    private fun chegouPelaUltimaDistancia(): Boolean =
        !s.ultimaDistanciaRestanteM.isNaN() && s.ultimaDistanciaRestanteM <= DISTANCIA_DE_CHEGADA_NO_DESTINO_M

    private fun terminarGuiagem(agora: Long, km: Double, chegou: Boolean) {
        s.kmNoFimDaGuiagem = km
        s.instanteDoFimDaGuiagem = agora
        s.chegou = chegou
    }

    // --- Abrir, fechar, estacionar ---

    private fun abrir(agora: Long) {
        val novo = EstadoDaViagem()
        novo.aberta = true
        novo.inicioMs = agora
        novo.ultimoT = agora
        novo.odometroInicial = hodometro
        novo.odometroUltimo = hodometro
        novo.odometroUltimoT = agora
        novo.partiuComHodometro = !hodometro.isNaN()
        novo.tanqueInicial = nivelDoTanque
        novo.tanqueFinal = nivelDoTanque
        novo.bateriaInicial = nivelDaBateria
        novo.bateriaFinal = nivelDaBateria
        novo.crusPrimeiros.putAll(crusUltimos)
        novo.crusUltimos.putAll(crusUltimos)
        val pos = ultimaPosicao
        if (pos != null && agora - pos.t <= IDADE_MAXIMA_DA_POSICAO_NA_PARTIDA_MS) {
            novo.latInicial = pos.lat
            novo.lonInicial = pos.lon
            novo.latFinal = pos.lat
            novo.lonFinal = pos.lon
        }
        novo.armouParada = !velocidade.isNaN() && velocidade > VELOCIDADE_PARA_ARMAR_PARADA_KMH
        s = novo
        amostraConhecida = !velocidade.isNaN()
        amostraVelocidade = if (amostraConhecida) velocidade else 0.0
        amostraT = agora
        emAceleracao = false
        emFrenagem = false
        ouvinte.aoEvento(EventoDoMotor.Abriu(agora))
        if (paradasPendentes.isNotEmpty()) {
            val pendentes = paradasPendentes.toList()
            paradasPendentes.clear()
            for (p in pendentes) ouvinte.aoEvento(EventoDoMotor.Parada(agora, p))
        }
    }

    private fun fechar(fim: Long) {
        emitirCandidatas()
        val km = kmFinal()
        if (s.guiada && s.instanteDoFimDaGuiagem == 0L) {
            terminarGuiagem(fim, km, chegou = chegouPelaUltimaDistancia())
        }
        val resumo = montarResumo(fim, final = true)
        s.aberta = false
        ouvinte.aoEvento(if (resumo.vale) EventoDoMotor.Fechou(resumo) else EventoDoMotor.Descartou(resumo))
    }

    private fun acordou(agora: Long) {
        pronto = null
        if (!s.aberta) return
        if (guiagemAtiva(agora)) {
            if (s.desligadoEm == 0L) s.desligadoEm = s.ultimoT
        } else {
            fechar(if (s.desligadoEm != 0L) s.desligadoEm else s.ultimoT)
        }
    }

    private fun estacionar(agora: Long) {
        emitirCandidatas()
        if (!nivelDoTanque.isNaN()) tanqueParado = nivelDoTanque
        if (!nivelDaBateria.isNaN()) bateriaParada = nivelDaBateria
        val pos = ultimaPosicao
        if (pos != null) {
            latParado = pos.lat
            lonParado = pos.lon
        }
        instanteParado = agora
        janelaDesde = 0L
    }

    // --- Abastecimento e recarga ---

    private fun detectar(tipo: TipoDeParada, nivel: Double, agora: Long) {
        val naJanela = janelaDesde != 0L && agora - janelaDesde < JANELA_DE_ACOMODACAO_MS
        if (pronto == true && !naJanela) return
        val parado = if (tipo == TipoDeParada.ABASTECIMENTO) tanqueParado else bateriaParada
        if (parado.isNaN()) return
        val atual = if (tipo == TipoDeParada.ABASTECIMENTO) candidataDoTanque else candidataDaBateria
        val nova = if (atual != null) {
            if (nivel <= atual.depois) return
            atual.copy(depois = nivel, quantidade = quantidadeDaParada(tipo, atual.antes, nivel))
        } else {
            val limiar = if (tipo == TipoDeParada.ABASTECIMENTO) SUBIDA_MINIMA_DO_TANQUE else SUBIDA_MINIMA_DA_BATERIA
            if (nivel - parado < limiar) return
            ParadaDeViagem(
                tipo = tipo,
                t = if (instanteParado != 0L) instanteParado else agora,
                lat = latParado,
                lon = lonParado,
                antes = parado,
                depois = nivel,
                quantidade = quantidadeDaParada(tipo, parado, nivel),
            )
        }
        if (tipo == TipoDeParada.ABASTECIMENTO) candidataDoTanque = nova else candidataDaBateria = nova
    }

    private fun quantidadeDaParada(tipo: TipoDeParada, antes: Double, depois: Double): Double =
        if (tipo == TipoDeParada.ABASTECIMENTO) (depois - antes) * TANQUE_DE_REFERENCIA_L / 100.0 else depois - antes

    /**
     * Entrega as candidatas (primeiro o tanque, depois a bateria). Depois de entregue, o nível de
     * referência daquele tipo some: não se detecta mais nada ali até estacionar de novo, o que evita
     * uma segunda parada para o mesmo abastecimento quando o carro religa depois de 10 min.
     */
    private fun emitirCandidatas() {
        candidataDoTanque?.let {
            entregarParada(it)
            candidataDoTanque = null
            tanqueParado = Double.NaN
        }
        candidataDaBateria?.let {
            entregarParada(it)
            candidataDaBateria = null
            bateriaParada = Double.NaN
        }
    }

    private fun entregarParada(parada: ParadaDeViagem) {
        if (s.aberta) ouvinte.aoEvento(EventoDoMotor.Parada(s.inicioMs, parada)) else paradasPendentes.add(parada)
    }

    // --- Integração ---

    private fun potenciaKw(): Double =
        if (!tensao.isNaN() && !corrente.isNaN()) tensao * corrente / 1000.0 else Double.NaN

    /** Avança o relógio da viagem até [agora] somando o que o estado atual produziu no intervalo. */
    private fun integrar(agora: Long) {
        if (!s.aberta) return
        val dt = agora - s.ultimoT
        if (dt < 0L) {
            s.ultimoT = agora
            return
        }
        if (dt == 0L) return
        s.ultimoT = agora
        if (dt > PASSO_MAXIMO_DE_INTEGRACAO_MS || pronto != true) return
        var passoKm = 0.0
        if (!velocidade.isNaN()) {
            passoKm = velocidade * dt / MS_POR_HORA
            s.distanciaIntegrada += passoKm
            if (velocidade >= VELOCIDADE_ANDANDO_KMH) {
                s.andandoMs += dt
                when (combustao) {
                    false -> {
                        s.eletricoKm += passoKm
                        s.eletricoMs += dt
                    }
                    true -> s.combustaoKm += passoKm
                    null -> Unit
                }
            } else {
                s.paradoMs += dt
            }
        }
        if (consumo.valor > 0.0) {
            when (consumo.modo) {
                1 -> if (!velocidade.isNaN()) s.litros += consumo.valor / 100.0 * passoKm
                4 -> s.litros += consumo.valor * dt / MS_POR_HORA
            }
        }
        val kw = potenciaKw()
        if (!kw.isNaN()) {
            if (kw >= 0.0) s.kwhGasto += kw * dt / MS_POR_HORA else s.kwhRecuperado += -kw * dt / MS_POR_HORA
        }
    }

    // --- Distância ---

    /** O km da viagem aberta (C-10). */
    private fun kmAoVivo(): Double {
        if (s.odometroUltimo.isNaN()) return s.distanciaIntegrada
        val correcao = (s.distanciaIntegrada - s.distanciaIntegradaNoHodometro)
            .coerceIn(0.0, KM_MAXIMO_DE_CORRECAO_DO_HODOMETRO)
        val porHodometro = s.odometroKm + correcao
        return if (s.partiuComHodometro) porHodometro else maxOf(porHodometro, s.distanciaIntegrada)
    }

    /** O km da viagem que fecha (C-10). */
    private fun kmFinal(): Double {
        if (s.odometroUltimo.isNaN()) return s.distanciaIntegrada
        if (s.partiuComHodometro && s.odometroKm >= KM_MINIMO_DO_HODOMETRO_NO_FIM) return s.odometroKm
        return maxOf(s.odometroKm, s.distanciaIntegrada)
    }

    private fun montarResumo(fim: Long, final: Boolean): ResumoDaViagem {
        val km = if (final) kmFinal() else kmAoVivo()
        val guiada = s.guiada
        val kmFimGuiagem = if (s.instanteDoFimDaGuiagem != 0L) s.kmNoFimDaGuiagem else km
        val instanteFim = if (s.instanteDoFimDaGuiagem != 0L) s.instanteDoFimDaGuiagem else fim
        return ResumoDaViagem(
            inicioMs = s.inicioMs,
            fimMs = fim,
            aberta = !final,
            km = km,
            kmIntegrado = s.distanciaIntegrada,
            odometroInicial = s.odometroInicial,
            odometroFinal = s.odometroUltimo,
            odometroSaltos = s.odometroSaltos,
            andandoMs = s.andandoMs,
            paradoMs = s.paradoMs,
            eletricoKm = CalculosDoResumo.kmEletrico(s.eletricoKm, s.combustaoKm, km),
            eletricoMs = s.eletricoMs,
            kwhGasto = s.kwhGasto,
            kwhRecuperado = s.kwhRecuperado,
            litros = s.litros,
            velocidadeMaxima = s.velocidadeMaxima,
            paradas = s.paradas,
            aceleracoesBruscas = s.aceleracoesBruscas,
            frenagensBruscas = s.frenagensBruscas,
            subidaM = s.subidaM,
            latInicial = s.latInicial,
            lonInicial = s.lonInicial,
            latFinal = s.latFinal,
            lonFinal = s.lonFinal,
            tanqueInicial = s.tanqueInicial,
            tanqueFinal = s.tanqueFinal,
            bateriaInicial = s.bateriaInicial,
            bateriaFinal = s.bateriaFinal,
            guiada = guiada,
            chegou = guiada && s.chegou,
            kmPlanejado = if (guiada) s.kmPlanejado else Double.NaN,
            segundosPlanejados = if (guiada) s.segundosPlanejados else 0,
            kmGuiados = if (guiada) maxOf(0.0, kmFimGuiagem - s.kmNoInicioDaGuiagem) else Double.NaN,
            segundosGuiados = if (guiada) (maxOf(0L, instanteFim - s.instanteDoInicioDaGuiagem) / 1000L).toInt() else 0,
            crusPrimeiros = LinkedHashMap(s.crusPrimeiros),
            crusUltimos = LinkedHashMap(s.crusUltimos),
        )
    }

    // --- Trilha ---

    private fun registrarPonto(agora: Long) {
        if (!s.aberta || pronto != true) return
        val andando = !velocidade.isNaN() && velocidade >= VELOCIDADE_ANDANDO_KMH
        val intervalo = if (andando) INTERVALO_DO_PONTO_ANDANDO_MS else INTERVALO_DO_PONTO_PARADO_MS
        if (agora - s.ultimoPontoT < intervalo && agora >= s.ultimoPontoT) return
        s.ultimoPontoT = agora
        val pos = ultimaPosicao?.takeIf { agora - it.t <= IDADE_MAXIMA_DA_POSICAO_NO_PONTO_MS }
        val ponto = PontoDeTrilha(
            t = agora,
            lat = pos?.lat ?: Double.NaN,
            lon = pos?.lon ?: Double.NaN,
            alt = pos?.alt ?: Double.NaN,
            kmh = velocidade,
            kw = potenciaKw(),
            modoDeCombustivel = consumo.modo,
            taxaDeCombustivel = consumo.valor,
            combustao = when (combustao) {
                true -> 1
                false -> 0
                null -> -1
            },
            km = kmAoVivo(),
        )
        ouvinte.aoEvento(EventoDoMotor.Ponto(s.inicioMs, ponto))
    }
}
