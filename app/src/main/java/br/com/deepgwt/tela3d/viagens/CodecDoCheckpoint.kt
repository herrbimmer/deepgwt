package br.com.deepgwt.tela3d.viagens

/**
 * O texto do checkpoint da viagem aberta (`open_trip.state`): uma linha `nome=valor` por campo, nesta
 * ordem. Dado do usuário já gravado nos carros: nomes, ordem e formato não mudam.
 *
 * Leitura tolerante: linha sem `=` (ou com `=` na posição 0) é ignorada; chave desconhecida, também;
 * valor numérico ilegível ignora só aquela linha (o campo fica no padrão do [EstadoDaViagem]).
 */
object CodecDoCheckpoint {
    private const val PREFIXO_PRIMEIRO = "rf."
    private const val PREFIXO_ULTIMO = "rl."

    private class Campo(
        val nome: String,
        val escrever: (EstadoDaViagem) -> String,
        val ler: (EstadoDaViagem, String) -> Boolean,
    )

    private fun booleano(nome: String, obter: (EstadoDaViagem) -> Boolean, definir: (EstadoDaViagem, Boolean) -> Unit) =
        Campo(nome, { if (obter(it)) "1" else "0" }, { e, v -> definir(e, v == "1"); true })

    private fun longo(nome: String, obter: (EstadoDaViagem) -> Long, definir: (EstadoDaViagem, Long) -> Unit) =
        Campo(nome, { obter(it).toString() }, { e, v ->
            val n = v.trim().toLongOrNull()
            if (n != null) definir(e, n)
            n != null
        })

    private fun inteiro(nome: String, obter: (EstadoDaViagem) -> Int, definir: (EstadoDaViagem, Int) -> Unit) =
        Campo(nome, { obter(it).toString() }, { e, v ->
            val n = v.trim().toIntOrNull()
            if (n != null) definir(e, n)
            n != null
        })

    private fun real(nome: String, obter: (EstadoDaViagem) -> Double, definir: (EstadoDaViagem, Double) -> Unit) =
        Campo(nome, { obter(it).toString() }, { e, v ->
            val n = v.trim().toDoubleOrNull()
            if (n != null) definir(e, n)
            n != null
        })

    /** Os campos, na ordem de escrita. */
    private val CAMPOS: List<Campo> = listOf(
        booleano("open", { it.aberta }, { e, v -> e.aberta = v }),
        longo("startMs", { it.inicioMs }, { e, v -> e.inicioMs = v }),
        longo("lastT", { it.ultimoT }, { e, v -> e.ultimoT = v }),
        longo("readyOffAt", { it.desligadoEm }, { e, v -> e.desligadoEm = v }),
        longo("lastPointT", { it.ultimoPontoT }, { e, v -> e.ultimoPontoT = v }),
        real("odoStart", { it.odometroInicial }, { e, v -> e.odometroInicial = v }),
        real("odoLast", { it.odometroUltimo }, { e, v -> e.odometroUltimo = v }),
        longo("lastOdoT", { it.odometroUltimoT }, { e, v -> e.odometroUltimoT = v }),
        real("odoKm", { it.odometroKm }, { e, v -> e.odometroKm = v }),
        inteiro("odoJumps", { it.odometroSaltos }, { e, v -> e.odometroSaltos = v }),
        booleano("odoFromOpen", { it.partiuComHodometro }, { e, v -> e.partiuComHodometro = v }),
        real("distInt", { it.distanciaIntegrada }, { e, v -> e.distanciaIntegrada = v }),
        real("distIntAtOdo", { it.distanciaIntegradaNoHodometro }, { e, v -> e.distanciaIntegradaNoHodometro = v }),
        longo("driveMs", { it.andandoMs }, { e, v -> e.andandoMs = v }),
        longo("idleMs", { it.paradoMs }, { e, v -> e.paradoMs = v }),
        real("evKm", { it.eletricoKm }, { e, v -> e.eletricoKm = v }),
        real("iceKm", { it.combustaoKm }, { e, v -> e.combustaoKm = v }),
        longo("evMs", { it.eletricoMs }, { e, v -> e.eletricoMs = v }),
        real("kwhOut", { it.kwhGasto }, { e, v -> e.kwhGasto = v }),
        real("kwhIn", { it.kwhRecuperado }, { e, v -> e.kwhRecuperado = v }),
        real("fuelL", { it.litros }, { e, v -> e.litros = v }),
        real("maxKmh", { it.velocidadeMaxima }, { e, v -> e.velocidadeMaxima = v }),
        inteiro("stops", { it.paradas }, { e, v -> e.paradas = v }),
        booleano("wasMoving", { it.armouParada }, { e, v -> e.armouParada = v }),
        inteiro("harshAccel", { it.aceleracoesBruscas }, { e, v -> e.aceleracoesBruscas = v }),
        inteiro("harshBrake", { it.frenagensBruscas }, { e, v -> e.frenagensBruscas = v }),
        real("climbM", { it.subidaM }, { e, v -> e.subidaM = v }),
        real("altRef", { it.referenciaDeAltitude }, { e, v -> e.referenciaDeAltitude = v }),
        real("startLat", { it.latInicial }, { e, v -> e.latInicial = v }),
        real("startLon", { it.lonInicial }, { e, v -> e.lonInicial = v }),
        real("endLat", { it.latFinal }, { e, v -> e.latFinal = v }),
        real("endLon", { it.lonFinal }, { e, v -> e.lonFinal = v }),
        real("fuelPctStart", { it.tanqueInicial }, { e, v -> e.tanqueInicial = v }),
        real("fuelPctEnd", { it.tanqueFinal }, { e, v -> e.tanqueFinal = v }),
        booleano("guided", { it.guiada }, { e, v -> e.guiada = v }),
        real("plannedKm", { it.kmPlanejado }, { e, v -> e.kmPlanejado = v }),
        inteiro("plannedS", { it.segundosPlanejados }, { e, v -> e.segundosPlanejados = v }),
        real("guideStartKm", { it.kmNoInicioDaGuiagem }, { e, v -> e.kmNoInicioDaGuiagem = v }),
        longo("guideStartT", { it.instanteDoInicioDaGuiagem }, { e, v -> e.instanteDoInicioDaGuiagem = v }),
        real("guideEndKm", { it.kmNoFimDaGuiagem }, { e, v -> e.kmNoFimDaGuiagem = v }),
        longo("guideEndT", { it.instanteDoFimDaGuiagem }, { e, v -> e.instanteDoFimDaGuiagem = v }),
        booleano("arrived", { it.chegou }, { e, v -> e.chegou = v }),
        real("lastRemainingM", { it.ultimaDistanciaRestanteM }, { e, v -> e.ultimaDistanciaRestanteM = v }),
        longo("lastNavT", { it.ultimaNavegacaoT }, { e, v -> e.ultimaNavegacaoT = v }),
        real("socStart", { it.bateriaInicial }, { e, v -> e.bateriaInicial = v }),
        real("socEnd", { it.bateriaFinal }, { e, v -> e.bateriaFinal = v }),
    )

    private val POR_NOME: Map<String, Campo> = CAMPOS.associateBy { it.nome }

    /** Valores nunca levam quebra de linha: ela vira espaço. */
    private fun semQuebra(valor: String): String = valor.replace('\n', ' ')

    fun codificar(estado: EstadoDaViagem): String {
        val s = StringBuilder(1024)
        for (campo in CAMPOS) s.append(campo.nome).append('=').append(semQuebra(campo.escrever(estado))).append('\n')
        for ((chave, valor) in estado.crusPrimeiros) {
            s.append(PREFIXO_PRIMEIRO).append(semQuebra(chave)).append('=').append(semQuebra(valor)).append('\n')
        }
        for ((chave, valor) in estado.crusUltimos) {
            s.append(PREFIXO_ULTIMO).append(semQuebra(chave)).append('=').append(semQuebra(valor)).append('\n')
        }
        return s.toString()
    }

    /** Entrada nula = estado vazio (não aberto). */
    fun decodificar(texto: String?): EstadoDaViagem {
        val estado = EstadoDaViagem()
        if (texto == null) return estado
        for (linha in texto.split('\n')) {
            val igual = linha.indexOf('=')
            if (igual <= 0) continue
            val nome = linha.substring(0, igual)
            val valor = linha.substring(igual + 1)
            when {
                nome.startsWith(PREFIXO_PRIMEIRO) -> estado.crusPrimeiros[nome.substring(PREFIXO_PRIMEIRO.length)] = valor
                nome.startsWith(PREFIXO_ULTIMO) -> estado.crusUltimos[nome.substring(PREFIXO_ULTIMO.length)] = valor
                else -> POR_NOME[nome]?.ler?.invoke(estado, valor)
            }
        }
        return estado
    }
}
