package br.com.deepgwt.tela3d.viagens

import br.com.deepgwt.tela3d.viagens.JsonSaida.ConstrutorDeObjeto
import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/** O dia (`yyyy-MM-dd`) de um instante, no fuso do aparelho naquele momento. */
object ChaveDoDia {
    fun de(ms: Long, fuso: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(ms).atZone(fuso).toLocalDate().toString()
}

/** Uma viagem gravada que ainda precisa de mapa e/ou nomes de lugar. */
data class ViagemPendente(
    val inicioMs: Long,
    val latInicial: Double,
    val lonInicial: Double,
    val latFinal: Double,
    val lonFinal: Double,
    /** `null` = mapa pendente; `""` = sem mapa. */
    val caminhoDoMapa: String?,
    /** `null` = lugares pendentes. */
    val lugarInicial: String?,
)

/** O fim da última viagem gravada: de onde se aprende o "nível parado" depois de um reinício. */
data class FimDaUltimaViagem(
    val tanque: Double,
    val bateria: Double,
    val lat: Double,
    val lon: Double,
    val fimMs: Long,
)

/**
 * Tudo que o módulo guarda e consulta no `trips.db`. Cada método devolve JSON pronto ou valores
 * simples; falhas de SQL sobem como exceção (quem chama decide o valor de falha).
 */
class ArmazenamentoDeViagens(private val banco: BancoSql) {

    companion object {
        const val VIAGENS_COM_DETALHE = 30
        const val PONTOS_DA_ROTA = 300
        const val TENTATIVAS_DE_MAPA = 5
        const val CAPACIDADE_MINIMA_DE_VIAGENS = 3
        const val CAPACIDADE_MINIMA_DE_SOC = 10.0
        const val LITROS_MINIMOS_PARA_CONSUMO = 0.05
        const val KM_MINIMOS_PARA_RAZAO = 0.1

        private const val COLUNAS_DA_VIAGEM =
            "start_ms, end_ms, day, km, km_integrated, odo_start, odo_end, ev_km, kwh_out, kwh_in, fuel_l, " +
                "max_kmh, climb_m, start_lat, start_lon, end_lat, end_lon, fuel_pct_start, fuel_pct_end, " +
                "planned_km, guided_km, soc_start, soc_end, drive_ms, idle_ms, ev_ms, stops, harsh_accel, " +
                "harsh_brake, detail_kept, guided, arrived, planned_s, guided_s, start_place, end_place, " +
                "snapshot_path, snapshot_view"

        private const val COLUNAS_DO_PONTO = "t, lat, lon, alt, kmh, kw, fuel_mode, fuel_rate, ice, km"

        private fun finito(d: Double): Double? = if (d.isNaN() || d.isInfinite()) null else d

        private fun somaFinita(d: Double): Double = if (d.isNaN() || d.isInfinite()) 0.0 else d

        private fun arredondar(valor: Double, casas: Int): Double {
            val f = Math.pow(10.0, casas.toDouble())
            return Math.round(valor * f) / f
        }
    }

    // --- Gravação ---

    /**
     * Grava a viagem fechada, acumula o dia e aplica a retenção dos pontos por segundo (30 viagens), tudo
     * numa transação. Regravar o mesmo `start_ms` troca a linha e corrige o dia (não soma de novo).
     */
    fun gravarViagem(r: ResumoDaViagem) {
        banco.emTransacao {
            val dia = ChaveDoDia.de(r.inicioMs)
            val antiga = banco.consultar(
                "SELECT day, km, drive_ms, fuel_l, kwh_out, kwh_in, ev_km FROM trips WHERE start_ms = ?",
                r.inicioMs,
            ).firstOrNull()
            if (antiga != null) tirarDoDia(antiga)

            banco.executar(
                "INSERT OR REPLACE INTO trips (start_ms, end_ms, day, km, km_integrated, odo_start, odo_end, " +
                    "drive_ms, idle_ms, ev_km, ev_ms, kwh_out, kwh_in, fuel_l, max_kmh, stops, harsh_accel, " +
                    "harsh_brake, climb_m, start_lat, start_lon, end_lat, end_lon, fuel_pct_start, fuel_pct_end, " +
                    "raw_json, guided, arrived, planned_km, planned_s, guided_km, guided_s, soc_start, soc_end) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, " +
                    "?, ?, ?, ?, ?)",
                r.inicioMs, r.fimMs, dia, finito(r.km), finito(r.kmIntegrado),
                finito(r.odometroInicial), finito(r.odometroFinal),
                r.andandoMs, r.paradoMs, finito(r.eletricoKm), r.eletricoMs,
                finito(r.kwhGasto), finito(r.kwhRecuperado), finito(r.litros), finito(r.velocidadeMaxima),
                r.paradas, r.aceleracoesBruscas, r.frenagensBruscas, finito(r.subidaM),
                finito(r.latInicial), finito(r.lonInicial), finito(r.latFinal), finito(r.lonFinal),
                finito(r.tanqueInicial), finito(r.tanqueFinal),
                JsonDeViagens.crus(r.crusPrimeiros, r.crusUltimos),
                if (r.guiada) 1 else 0, if (r.chegou) 1 else 0,
                if (r.guiada) finito(r.kmPlanejado) else null,
                if (r.guiada) r.segundosPlanejados else null,
                if (r.guiada) finito(r.kmGuiados) else null,
                if (r.guiada) r.segundosGuiados else null,
                finito(r.bateriaInicial), finito(r.bateriaFinal),
            )

            banco.executar("INSERT OR IGNORE INTO days (day) VALUES (?)", dia)
            banco.executar(
                "UPDATE days SET trips = trips + 1, km = km + ?, drive_ms = drive_ms + ?, fuel_l = fuel_l + ?, " +
                    "kwh_out = kwh_out + ?, kwh_in = kwh_in + ?, ev_km = ev_km + ? WHERE day = ?",
                somaFinita(r.km), r.andandoMs, somaFinita(r.litros), somaFinita(r.kwhGasto),
                somaFinita(r.kwhRecuperado), somaFinita(r.eletricoKm), dia,
            )

            aplicarRetencao()
        }
    }

    private fun tirarDoDia(antiga: LinhaSql) {
        val dia = antiga.texto("day") ?: return
        banco.executar(
            "UPDATE days SET trips = MAX(0, trips - 1), km = km - ?, drive_ms = drive_ms - ?, fuel_l = fuel_l - ?, " +
                "kwh_out = kwh_out - ?, kwh_in = kwh_in - ?, ev_km = ev_km - ? WHERE day = ?",
            antiga.real("km") ?: 0.0, antiga.longo("drive_ms") ?: 0L, antiga.real("fuel_l") ?: 0.0,
            antiga.real("kwh_out") ?: 0.0, antiga.real("kwh_in") ?: 0.0, antiga.real("ev_km") ?: 0.0, dia,
        )
        banco.executar("DELETE FROM days WHERE day = ? AND trips <= 0", dia)
    }

    /**
     * Só as 30 viagens gravadas mais recentes (maior `start_ms`) guardam os pontos por segundo. Antes de
     * soltá-los, garante a rota gravada. Pontos da viagem aberta (ainda não gravada) não são tocados.
     */
    private fun aplicarRetencao() {
        val antigas = "SELECT start_ms FROM trips ORDER BY start_ms DESC LIMIT -1 OFFSET $VIAGENS_COM_DETALHE"
        val semRota = banco.consultar(
            "SELECT start_ms FROM trips WHERE route IS NULL AND detail_kept = 1 AND start_ms IN ($antigas)"
        )
        for (linha in semRota) gravarRota(linha.longo("start_ms") ?: continue)
        banco.executar("DELETE FROM trip_points WHERE start_ms IN ($antigas)")
        banco.executar("UPDATE trips SET detail_kept = 0 WHERE detail_kept = 1 AND start_ms IN ($antigas)")
    }

    fun gravarPontos(inicioMs: Long, pontos: List<PontoDeTrilha>) {
        if (pontos.isEmpty()) return
        banco.emTransacao {
            for (p in pontos) {
                banco.executar(
                    "INSERT INTO trip_points (start_ms, t, lat, lon, alt, kmh, kw, fuel_mode, fuel_rate, ice, km) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    inicioMs, p.t, finito(p.lat), finito(p.lon), finito(p.alt), finito(p.kmh), finito(p.kw),
                    p.modoDeCombustivel, finito(p.taxaDeCombustivel), p.combustao, finito(p.km),
                )
            }
        }
    }

    fun apagarPontos(inicioMs: Long) {
        banco.executar("DELETE FROM trip_points WHERE start_ms = ?", inicioMs)
    }

    fun gravarParada(inicioMs: Long, p: ParadaDeViagem) {
        banco.executar(
            "INSERT INTO trip_stops (start_ms, t, kind, lat, lon, level_before, level_after, amount) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            inicioMs, p.t, p.tipo.texto, finito(p.lat), finito(p.lon), finito(p.antes), finito(p.depois),
            finito(p.quantidade),
        )
    }

    /**
     * Grava a rota da viagem: os pontos com posição, afinados a 300, como `[lat, lon, kmh, kw, fuelMode,
     * fuelRate]`. Sem nenhum ponto com posição grava `[]` (a viagem sai da lista de pendentes).
     */
    fun gravarRota(inicioMs: Long) {
        val linhas = banco.consultar(
            "SELECT lat, lon, kmh, kw, fuel_mode, fuel_rate FROM trip_points " +
                "WHERE start_ms = ? AND lat IS NOT NULL AND lon IS NOT NULL ORDER BY t",
            inicioMs,
        )
        val itens = Afinamento.afinar(linhas, PONTOS_DA_ROTA).map { l ->
            JsonSaida.lista(
                listOf(
                    JsonSaida.real(l.real("lat")?.let { arredondar(it, 5) }),
                    JsonSaida.real(l.real("lon")?.let { arredondar(it, 5) }),
                    l.real("kmh")?.let { JsonSaida.inteiro(Math.round(it)) } ?: "null",
                    JsonSaida.real(l.real("kw")?.let { arredondar(it, 1) }),
                    JsonSaida.inteiro(l.inteiroOuZero("fuel_mode")),
                    JsonSaida.real(l.real("fuel_rate")?.let { arredondar(it, 1) }),
                )
            )
        }
        banco.executar("UPDATE trips SET route = ? WHERE start_ms = ?", JsonSaida.lista(itens), inicioMs)
    }

    // --- Checkpoint ---

    fun gravarCheckpoint(texto: String, agoraMs: Long) {
        banco.executar("INSERT OR REPLACE INTO open_trip (id, state, updated_ms) VALUES (1, ?, ?)", texto, agoraMs)
    }

    fun lerCheckpoint(): String? =
        banco.consultar("SELECT state FROM open_trip WHERE id = 1").firstOrNull()?.texto("state")

    fun apagarCheckpoint() {
        banco.executar("DELETE FROM open_trip")
    }

    fun fimDaUltimaViagem(): FimDaUltimaViagem? {
        val l = banco.consultar(
            "SELECT fuel_pct_end, soc_end, end_lat, end_lon, end_ms FROM trips ORDER BY start_ms DESC LIMIT 1"
        ).firstOrNull() ?: return null
        return FimDaUltimaViagem(
            tanque = l.real("fuel_pct_end") ?: Double.NaN,
            bateria = l.real("soc_end") ?: Double.NaN,
            lat = l.real("end_lat") ?: Double.NaN,
            lon = l.real("end_lon") ?: Double.NaN,
            fimMs = l.longo("end_ms") ?: 0L,
        )
    }

    // --- Mapas e lugares ---

    fun viagensSemRota(limite: Int): List<Long> =
        banco.consultar(
            "SELECT start_ms FROM trips WHERE route IS NULL AND detail_kept = 1 ORDER BY start_ms DESC LIMIT ?",
            limite,
        ).mapNotNull { it.longo("start_ms") }

    /** Viagens com mapa ou lugar pendente (e menos de 5 tentativas), da mais nova para a mais velha. */
    fun viagensPendentes(limite: Int): List<ViagemPendente> =
        banco.consultar(
            "SELECT start_ms, start_lat, start_lon, end_lat, end_lon, snapshot_path, start_place FROM trips " +
                "WHERE (snapshot_path IS NULL OR start_place IS NULL) AND map_attempts < ? " +
                "ORDER BY start_ms DESC LIMIT ?",
            TENTATIVAS_DE_MAPA, limite,
        ).mapNotNull { l ->
            ViagemPendente(
                inicioMs = l.longo("start_ms") ?: return@mapNotNull null,
                latInicial = l.real("start_lat") ?: Double.NaN,
                lonInicial = l.real("start_lon") ?: Double.NaN,
                latFinal = l.real("end_lat") ?: Double.NaN,
                lonFinal = l.real("end_lon") ?: Double.NaN,
                caminhoDoMapa = l.texto("snapshot_path"),
                lugarInicial = l.texto("start_place"),
            )
        }

    /**
     * As posições da viagem para o mapa (no máximo [limite], afinadas). Sem os pontos por segundo (já
     * apagados) usa a rota gravada.
     */
    fun posicoesParaMapa(inicioMs: Long, limite: Int): List<Fixo> {
        val linhas = banco.consultar(
            "SELECT lat, lon FROM trip_points WHERE start_ms = ? AND lat IS NOT NULL AND lon IS NOT NULL ORDER BY t",
            inicioMs,
        )
        if (linhas.size >= 2) {
            return Afinamento.afinar(linhas, limite).mapNotNull { l ->
                val lat = l.real("lat") ?: return@mapNotNull null
                val lon = l.real("lon") ?: return@mapNotNull null
                Fixo(lat, lon)
            }
        }
        val rota = banco.consultar("SELECT route FROM trips WHERE start_ms = ?", inicioMs).firstOrNull()?.texto("route")
        return Afinamento.afinar(posicoesDaRota(rota), limite)
    }

    private fun posicoesDaRota(rota: String?): List<Fixo> {
        if (rota.isNullOrBlank()) return emptyList()
        return try {
            val itens = Json.parseToJsonElement(rota) as? JsonArray ?: return emptyList()
            itens.mapNotNull { item ->
                val par = item as? JsonArray ?: return@mapNotNull null
                val lat = (par.getOrNull(0) as? JsonPrimitive)?.doubleOrNull
                val lon = (par.getOrNull(1) as? JsonPrimitive)?.doubleOrNull
                if (lat == null || lon == null) null else Fixo(lat, lon)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun gravarMapa(inicioMs: Long, caminho: String, quadro: String?) {
        banco.executar("UPDATE trips SET snapshot_path = ?, snapshot_view = ? WHERE start_ms = ?", caminho, quadro, inicioMs)
    }

    /** Sem posições suficientes: grava `snapshot_path = ""` e não tenta mais. */
    fun marcarSemMapa(inicioMs: Long) = gravarMapa(inicioMs, "", null)

    fun somarTentativaDeMapa(inicioMs: Long) {
        banco.executar("UPDATE trips SET map_attempts = map_attempts + 1 WHERE start_ms = ?", inicioMs)
    }

    fun gravarLugares(inicioMs: Long, partida: String, chegada: String) {
        banco.executar("UPDATE trips SET start_place = ?, end_place = ? WHERE start_ms = ?", partida, chegada, inicioMs)
    }

    // --- Consultas da página ---

    fun viagensEmJson(deslocamento: Int, limite: Int): String {
        val linhas = banco.consultar(
            "SELECT $COLUNAS_DA_VIAGEM FROM trips ORDER BY start_ms DESC LIMIT ? OFFSET ?",
            limite.coerceIn(1, 200), maxOf(0, deslocamento),
        )
        return JsonSaida.lista(linhas.map { l -> JsonSaida.objeto { camposDaViagem(l) } })
    }

    /** `null` se a viagem não existe. */
    fun viagemEmJson(inicioMs: Long, maxPontos: Int): String? {
        val l = banco.consultar("SELECT $COLUNAS_DA_VIAGEM, route FROM trips WHERE start_ms = ?", inicioMs)
            .firstOrNull() ?: return null
        val rota = l.texto("route")?.takeIf { rotaValida(it) }
        val pontos = Afinamento.afinar(pontosDaViagem(inicioMs), maxPontos).map { pontoDeLinha(it) }
        return JsonSaida.objeto {
            camposDaViagem(l)
            if (rota != null) bruto("route", rota)
            bruto("pointFields", JsonSaida.lista(JsonDeViagens.CAMPOS_DO_PONTO.map { JsonSaida.texto(it) }))
            bruto("points", JsonSaida.lista(pontos))
            bruto("stops", paradasEmJson(inicioMs))
        }
    }

    /** Os pontos com `t > ultimoT` (afinados ao vivo) mais os da memória, e as paradas. */
    fun pontosDesdeEmJson(inicioMs: Long, ultimoT: Long, maxPontos: Int, naMemoria: List<PontoDeTrilha>): String {
        val linhas = banco.consultar(
            "SELECT $COLUNAS_DO_PONTO FROM trip_points WHERE start_ms = ? AND t > ? ORDER BY t",
            inicioMs, ultimoT,
        )
        val itens = Afinamento.afinarAoVivo(linhas, maxPontos).map { pontoDeLinha(it) }.toMutableList()
        val ultimoIncluido = linhas.lastOrNull()?.longo("t") ?: ultimoT
        for (p in naMemoria) if (p.t > ultimoIncluido) itens.add(JsonDeViagens.ponto(p))
        return JsonSaida.objeto {
            bruto("points", JsonSaida.lista(itens))
            bruto("stops", paradasEmJson(inicioMs))
        }
    }

    fun diasEmJson(de: String?, ate: String?): String {
        val linhas = banco.consultar(
            "SELECT day, trips, km, drive_ms, fuel_l, kwh_out, kwh_in, ev_km FROM days " +
                "WHERE day >= ? AND day <= ? ORDER BY day",
            de ?: "", ate ?: "9999",
        )
        return JsonSaida.lista(linhas.map { periodoEmJson(it, "day") })
    }

    fun mesesEmJson(): String {
        val linhas = banco.consultar(
            "SELECT substr(day, 1, 7) AS period, SUM(trips) AS trips, SUM(km) AS km, SUM(drive_ms) AS drive_ms, " +
                "SUM(fuel_l) AS fuel_l, SUM(kwh_out) AS kwh_out, SUM(kwh_in) AS kwh_in, SUM(ev_km) AS ev_km " +
                "FROM days GROUP BY substr(day, 1, 7) ORDER BY period"
        )
        return JsonSaida.lista(linhas.map { periodoEmJson(it, "period") })
    }

    private fun periodoEmJson(l: LinhaSql, colunaDoPeriodo: String): String {
        val km = l.real("km") ?: 0.0
        val litros = l.real("fuel_l") ?: 0.0
        val out = l.real("kwh_out") ?: 0.0
        val entrada = l.real("kwh_in") ?: 0.0
        return JsonSaida.objeto {
            texto("period", l.texto(colunaDoPeriodo))
            inteiro("trips", l.inteiroOuZero("trips"))
            real("km", km)
            inteiro("driveMs", l.inteiroOuZero("drive_ms"))
            real("fuelL", litros)
            real("kwhOut", out)
            real("kwhIn", entrada)
            real("evKm", l.real("ev_km") ?: 0.0)
            real("kmPerL", CalculosDoResumo.kmPorLitro(km, litros).takeIf { !it.isNaN() })
            real("kwhPer100Km", CalculosDoResumo.kwhPor100Km(out, entrada, km).takeIf { !it.isNaN() })
        }
    }

    fun totaisEmJson(): String {
        val d = banco.consultar(
            "SELECT COALESCE(SUM(trips), 0) AS trips, COALESCE(SUM(km), 0) AS km, " +
                "COALESCE(SUM(drive_ms), 0) AS drive_ms, COALESCE(SUM(fuel_l), 0) AS fuel_l, " +
                "COALESCE(SUM(kwh_out), 0) AS kwh_out, COALESCE(SUM(kwh_in), 0) AS kwh_in, " +
                "COALESCE(SUM(ev_km), 0) AS ev_km, MIN(day) AS first_day FROM days"
        ).first()
        val abastecimentos = banco.consultar(
            "SELECT COUNT(*) AS n, COALESCE(SUM(amount), 0) AS q FROM trip_stops WHERE kind = 'refuel'"
        ).first()
        val recargas = banco.consultar("SELECT COUNT(*) AS n FROM trip_stops WHERE kind = 'charge'").first()
        val capacidade = banco.consultar(
            "SELECT COUNT(*) AS n, SUM(soc_start - soc_end) AS soc, SUM(kwh_out - kwh_in) AS kwh FROM trips " +
                "WHERE soc_start IS NOT NULL AND soc_end IS NOT NULL AND ABS(soc_start - soc_end) >= ? " +
                "AND (soc_start - soc_end) * (kwh_out - kwh_in) > 0",
            CAPACIDADE_MINIMA_DE_SOC,
        ).first()
        val viagensDaCapacidade = capacidade.inteiroOuZero("n")
        val somaDoSoc = capacidade.real("soc") ?: 0.0
        val somaDaEnergia = capacidade.real("kwh") ?: 0.0
        return JsonSaida.objeto {
            inteiro("trips", d.inteiroOuZero("trips"))
            real("km", d.real("km") ?: 0.0)
            inteiro("driveMs", d.inteiroOuZero("drive_ms"))
            real("fuelL", d.real("fuel_l") ?: 0.0)
            real("kwhOut", d.real("kwh_out") ?: 0.0)
            real("kwhIn", d.real("kwh_in") ?: 0.0)
            real("evKm", d.real("ev_km") ?: 0.0)
            texto("firstDay", d.texto("first_day"))
            inteiro("refuels", abastecimentos.inteiroOuZero("n"))
            real("refuelL", abastecimentos.real("q") ?: 0.0)
            inteiro("charges", recargas.inteiroOuZero("n"))
            if (viagensDaCapacidade >= CAPACIDADE_MINIMA_DE_VIAGENS && somaDoSoc != 0.0) {
                real("capacityKwh", somaDaEnergia / somaDoSoc * 100.0)
                inteiro("capacityTrips", viagensDaCapacidade)
            }
        }
    }

    fun totaisDeParadasEmJson(desdeMs: Long): String {
        val linhas = banco.consultar(
            "SELECT kind, COUNT(*) AS n, COALESCE(SUM(level_after - level_before), 0) AS pct " +
                "FROM trip_stops WHERE t >= ? GROUP BY kind",
            desdeMs,
        )
        var abastecimentos = 0L
        var pontosDoTanque = 0.0
        var recargas = 0L
        var pontosDaBateria = 0.0
        for (l in linhas) {
            when (l.texto("kind")) {
                TipoDeParada.ABASTECIMENTO.texto -> {
                    abastecimentos = l.inteiroOuZero("n")
                    pontosDoTanque = l.real("pct") ?: 0.0
                }
                TipoDeParada.RECARGA.texto -> {
                    recargas = l.inteiroOuZero("n")
                    pontosDaBateria = l.real("pct") ?: 0.0
                }
            }
        }
        return JsonSaida.objeto {
            inteiro("refuels", abastecimentos)
            real("refuelPct", pontosDoTanque)
            inteiro("charges", recargas)
            real("chargePct", pontosDaBateria)
        }
    }

    // --- Peças ---

    private fun ConstrutorDeObjeto.camposDaViagem(l: LinhaSql) {
        val km = l.real("km")
        val litros = l.real("fuel_l")
        val out = l.real("kwh_out")
        val entrada = l.real("kwh_in")
        val eletricoKm = l.real("ev_km")
        inteiro("startMs", l.longo("start_ms"))
        inteiro("endMs", l.longo("end_ms"))
        texto("day", l.texto("day"))
        real("km", km)
        real("kmIntegrated", l.real("km_integrated"))
        real("odoStart", l.real("odo_start"))
        real("odoEnd", l.real("odo_end"))
        real("evKm", eletricoKm)
        real("kwhOut", out)
        real("kwhIn", entrada)
        real("fuelL", litros)
        real("maxKmh", l.real("max_kmh"))
        real("climbM", l.real("climb_m"))
        real("startLat", l.real("start_lat"))
        real("startLon", l.real("start_lon"))
        real("endLat", l.real("end_lat"))
        real("endLon", l.real("end_lon"))
        real("fuelPctStart", l.real("fuel_pct_start"))
        real("fuelPctEnd", l.real("fuel_pct_end"))
        real("plannedKm", l.real("planned_km"))
        real("guidedKm", l.real("guided_km"))
        real("socStart", l.real("soc_start"))
        real("socEnd", l.real("soc_end"))
        inteiro("driveMs", l.inteiroOuZero("drive_ms"))
        inteiro("idleMs", l.inteiroOuZero("idle_ms"))
        inteiro("evMs", l.inteiroOuZero("ev_ms"))
        inteiro("stops", l.inteiroOuZero("stops"))
        inteiro("harshAccel", l.inteiroOuZero("harsh_accel"))
        inteiro("harshBrake", l.inteiroOuZero("harsh_brake"))
        inteiro("detailKept", l.inteiroOuZero("detail_kept"))
        inteiro("guided", l.inteiroOuZero("guided"))
        inteiro("arrived", l.inteiroOuZero("arrived"))
        inteiro("plannedS", l.inteiroOuZero("planned_s"))
        inteiro("guidedS", l.inteiroOuZero("guided_s"))
        texto("startPlace", l.texto("start_place"))
        texto("endPlace", l.texto("end_place"))
        texto("snapshotPath", l.texto("snapshot_path"))
        texto("snapshotView", l.texto("snapshot_view"))
        real(
            "kmPerL",
            if (km != null && litros != null && litros >= LITROS_MINIMOS_PARA_CONSUMO) km / litros else null,
        )
        real(
            "kwhPer100Km",
            if (km != null && km >= KM_MINIMOS_PARA_RAZAO && out != null && entrada != null) {
                100.0 * (out - entrada) / km
            } else {
                null
            },
        )
        real(
            "evShare",
            if (km != null && km >= KM_MINIMOS_PARA_RAZAO && eletricoKm != null) minOf(1.0, eletricoKm / km) else null,
        )
    }

    private fun pontosDaViagem(inicioMs: Long): List<LinhaSql> =
        banco.consultar("SELECT $COLUNAS_DO_PONTO FROM trip_points WHERE start_ms = ? ORDER BY t", inicioMs)

    private fun pontoDeLinha(l: LinhaSql): String = JsonDeViagens.pontoDeColunas(
        t = l.longo("t") ?: 0L,
        lat = l.real("lat"), lon = l.real("lon"), alt = l.real("alt"),
        kmh = l.real("kmh"), kw = l.real("kw"),
        modoDeCombustivel = l.inteiroOuZero("fuel_mode"),
        taxaDeCombustivel = l.real("fuel_rate"),
        combustao = l.inteiroOuZero("ice"),
        km = l.real("km"),
    )

    private fun paradasEmJson(inicioMs: Long): String {
        val linhas = banco.consultar(
            "SELECT kind, t, lat, lon, level_before, level_after, amount FROM trip_stops " +
                "WHERE start_ms = ? ORDER BY t, id",
            inicioMs,
        )
        return JsonSaida.lista(
            linhas.map { l ->
                JsonSaida.objeto {
                    texto("kind", l.texto("kind"))
                    inteiro("t", l.longo("t"))
                    real("lat", l.real("lat"))
                    real("lon", l.real("lon"))
                    real("before", l.real("level_before"))
                    real("after", l.real("level_after"))
                    real("amount", l.real("amount"))
                }
            }
        )
    }

    private fun rotaValida(texto: String): Boolean = try {
        Json.parseToJsonElement(texto) is JsonArray
    } catch (e: Exception) {
        false
    }
}
