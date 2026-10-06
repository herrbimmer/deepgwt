package br.com.deepgwt.tela3d.viagens

/** Os JSONs que a página lê (nomes de chave em inglês: são o contrato com a página 3D). */
object JsonDeViagens {

    /** `getLiveTrip`: o resumo da viagem aberta. */
    fun aoVivo(r: ResumoDaViagem): String = JsonSaida.objeto {
        booleano("open", true)
        inteiro("startMs", r.inicioMs)
        inteiro("endMs", r.fimMs)
        real("km", r.km)
        real("kmIntegrated", r.kmIntegrado)
        inteiro("driveMs", r.andandoMs)
        inteiro("idleMs", r.paradoMs)
        real("evKm", r.eletricoKm)
        inteiro("evMs", r.eletricoMs)
        real("kwhOut", r.kwhGasto)
        real("kwhIn", r.kwhRecuperado)
        real("fuelL", r.litros)
        real("maxKmh", r.velocidadeMaxima)
        inteiro("stops", r.paradas)
        inteiro("harshAccel", r.aceleracoesBruscas)
        inteiro("harshBrake", r.frenagensBruscas)
        real("climbM", r.subidaM)
        inteiro("guided", if (r.guiada) 1 else 0)
        inteiro("arrived", if (r.chegou) 1 else 0)
        real("plannedKm", if (r.guiada) r.kmPlanejado else null)
        inteiro("plannedS", r.segundosPlanejados)
        real("guidedKm", if (r.guiada) r.kmGuiados else null)
        inteiro("guidedS", r.segundosGuiados)
        real("kmPerL", r.kmPorLitro)
        real("kwhPer100Km", r.kwhPor100Km)
        real("evShare", r.fracaoEletrica)
        real("avgKmh", r.velocidadeMedia)
        real("fuelPctStart", r.tanqueInicial)
        real("fuelPctEnd", r.tanqueFinal)
        real("socStart", r.bateriaInicial)
        real("socEnd", r.bateriaFinal)
    }

    /** O texto da coluna `raw_json`: `{"first":{...},"last":{...}}`. */
    fun crus(primeiros: Map<String, String>, ultimos: Map<String, String>): String = JsonSaida.objeto {
        bruto("first", mapaDeTextos(primeiros))
        bruto("last", mapaDeTextos(ultimos))
    }

    private fun mapaDeTextos(mapa: Map<String, String>): String = JsonSaida.objeto {
        for ((k, v) in mapa) texto(k, v)
    }

    /** Um ponto como `[t, lat, lon, alt, kmh, kw, fuelMode, fuelRate, ice, km]`. */
    fun ponto(p: PontoDeTrilha): String = pontoDeColunas(
        p.t, p.lat, p.lon, p.alt, p.kmh, p.kw, p.modoDeCombustivel.toLong(), p.taxaDeCombustivel, p.combustao.toLong(), p.km,
    )

    fun pontoDeColunas(
        t: Long, lat: Double?, lon: Double?, alt: Double?, kmh: Double?, kw: Double?,
        modoDeCombustivel: Long, taxaDeCombustivel: Double?, combustao: Long, km: Double?,
    ): String = JsonSaida.lista(
        listOf(
            JsonSaida.inteiro(t), JsonSaida.real(lat), JsonSaida.real(lon), JsonSaida.real(alt),
            JsonSaida.real(kmh), JsonSaida.real(kw), JsonSaida.inteiro(modoDeCombustivel),
            JsonSaida.real(taxaDeCombustivel), JsonSaida.inteiro(combustao), JsonSaida.real(km),
        )
    )

    /** Os nomes das posições de cada ponto, na ordem. */
    val CAMPOS_DO_PONTO: List<String> =
        listOf("t", "lat", "lon", "alt", "kmh", "kw", "fuelMode", "fuelRate", "ice", "km")

    fun parada(p: ParadaDeViagem): String = JsonSaida.objeto {
        texto("kind", p.tipo.texto)
        inteiro("t", p.t)
        real("lat", p.lat)
        real("lon", p.lon)
        real("before", p.antes)
        real("after", p.depois)
        real("amount", p.quantidade)
    }
}
