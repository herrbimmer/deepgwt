package br.com.deepgwt.tela3d.viagens

/**
 * O esquema do `trips.db` (versão 4) e a atualização das versões antigas. É dado do usuário já gravado
 * nos carros: nomes, tipos e semântica não mudam.
 */
object EsquemaDeViagens {
    const val NOME_DO_ARQUIVO = "trips.db"
    const val VERSAO = 4

    private val CRIAR_VIAGENS = """
        CREATE TABLE trips (
            start_ms INTEGER PRIMARY KEY, end_ms INTEGER NOT NULL, day TEXT NOT NULL,
            km REAL, km_integrated REAL, odo_start REAL, odo_end REAL,
            drive_ms INTEGER, idle_ms INTEGER, ev_km REAL, ev_ms INTEGER,
            kwh_out REAL, kwh_in REAL, fuel_l REAL, max_kmh REAL, stops INTEGER,
            harsh_accel INTEGER, harsh_brake INTEGER, climb_m REAL,
            start_lat REAL, start_lon REAL, end_lat REAL, end_lon REAL,
            fuel_pct_start REAL, fuel_pct_end REAL, start_place TEXT, end_place TEXT,
            snapshot_path TEXT, raw_json TEXT,
            detail_kept INTEGER NOT NULL DEFAULT 1, map_attempts INTEGER NOT NULL DEFAULT 0,
            guided INTEGER NOT NULL DEFAULT 0, arrived INTEGER NOT NULL DEFAULT 0,
            planned_km REAL, planned_s INTEGER, guided_km REAL, guided_s INTEGER,
            route TEXT, snapshot_view TEXT, soc_start REAL, soc_end REAL
        )
    """.trimIndent()

    private val CRIAR_PARADAS = """
        CREATE TABLE IF NOT EXISTS trip_stops (
            id INTEGER PRIMARY KEY AUTOINCREMENT, start_ms INTEGER NOT NULL, t INTEGER NOT NULL,
            kind TEXT NOT NULL, lat REAL, lon REAL, level_before REAL, level_after REAL, amount REAL
        )
    """.trimIndent()

    private const val CRIAR_INDICE_DAS_PARADAS =
        "CREATE INDEX IF NOT EXISTS trip_stops_trip ON trip_stops(start_ms)"

    fun criar(banco: BancoSql) {
        banco.executar(CRIAR_VIAGENS)
        banco.executar("CREATE INDEX trips_day ON trips(day)")
        banco.executar(
            """
            CREATE TABLE trip_points (
                start_ms INTEGER NOT NULL, t INTEGER NOT NULL, lat REAL, lon REAL, alt REAL,
                kmh REAL, kw REAL, fuel_mode INTEGER, fuel_rate REAL, ice INTEGER, km REAL
            )
            """.trimIndent()
        )
        banco.executar("CREATE INDEX trip_points_trip ON trip_points(start_ms, t)")
        banco.executar(
            """
            CREATE TABLE days (
                day TEXT PRIMARY KEY, trips INTEGER NOT NULL DEFAULT 0, km REAL NOT NULL DEFAULT 0,
                drive_ms INTEGER NOT NULL DEFAULT 0, fuel_l REAL NOT NULL DEFAULT 0,
                kwh_out REAL NOT NULL DEFAULT 0, kwh_in REAL NOT NULL DEFAULT 0, ev_km REAL NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        banco.executar(
            """
            CREATE TABLE open_trip (
                id INTEGER PRIMARY KEY CHECK (id = 1), state TEXT NOT NULL, updated_ms INTEGER NOT NULL
            )
            """.trimIndent()
        )
        banco.executar(CRIAR_PARADAS)
        banco.executar(CRIAR_INDICE_DAS_PARADAS)
    }

    /** Atualização em cascata a partir da versão encontrada [de]. Não existe volta (downgrade). */
    fun atualizar(banco: BancoSql, de: Int) {
        if (de < 2) {
            banco.executar("ALTER TABLE trips ADD COLUMN map_attempts INTEGER NOT NULL DEFAULT 0")
        }
        if (de < 3) {
            banco.executar("ALTER TABLE trips ADD COLUMN guided INTEGER NOT NULL DEFAULT 0")
            banco.executar("ALTER TABLE trips ADD COLUMN arrived INTEGER NOT NULL DEFAULT 0")
            banco.executar("ALTER TABLE trips ADD COLUMN planned_km REAL")
            banco.executar("ALTER TABLE trips ADD COLUMN planned_s INTEGER")
            banco.executar("ALTER TABLE trips ADD COLUMN guided_km REAL")
            banco.executar("ALTER TABLE trips ADD COLUMN guided_s INTEGER")
        }
        if (de < 4) {
            banco.executar("ALTER TABLE trips ADD COLUMN route TEXT")
            banco.executar("ALTER TABLE trips ADD COLUMN snapshot_view TEXT")
            banco.executar("ALTER TABLE trips ADD COLUMN soc_start REAL")
            banco.executar("ALTER TABLE trips ADD COLUMN soc_end REAL")
            banco.executar(CRIAR_PARADAS)
            banco.executar(CRIAR_INDICE_DAS_PARADAS)
            // As imagens já feitas são refeitas, agora com o `snapshot_view`.
            banco.executar(
                "UPDATE trips SET snapshot_path = NULL, map_attempts = 0 " +
                    "WHERE snapshot_path IS NOT NULL AND snapshot_path != ''"
            )
        }
    }
}
