package br.com.deepgwt.tela3d.viagens

import br.com.deepgwt.tela3d.viagens.ChavesDeSinais as K
import kotlin.math.floor
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotorDeViagemTest {

    private fun consumoEPotencia(b: Bancada, t: Long, ice: String = "0") {
        b.sinal(K.CONSUMO_INSTANTANEO, "{1,8.0}", t)
        b.sinal(K.COMBUSTAO, ice, t)
        b.sinal(K.TENSAO_DO_PACOTE, "400", t)
        b.sinal(K.CORRENTE_DO_PACOTE, "50", t)
    }

    /** O cenário A da especificação: 120 s acelerando até 60 km/h, depois READY desliga. */
    private fun cenarioA(): Bancada {
        val b = Bancada()
        b.hodometro(12345.6, T0 - 5000)
        b.tanque(50.0, T0 - 5000)
        b.bateria(80.0, T0 - 5000)
        b.pronto("1", T0)
        consumoEPotencia(b, T0)
        var acc = 0.0
        var ultimaLeitura = floor(12345.6 * 10) / 10
        for (s in 1..120) {
            val t = T0 + s * 1000L
            val v = min(60.0, 6.0 * s)
            b.velocidade(v, t)
            acc += v / 3600.0
            val leitura = floor((12345.6 + acc) * 10) / 10
            if (leitura != ultimaLeitura) {
                b.hodometro(leitura, t)
                ultimaLeitura = leitura
            }
            if (s == 60) {
                b.sinal(K.COMBUSTAO, "1", t)
                b.sinal(K.CORRENTE_DO_PACOTE, "-20", t)
            }
            if (s == 100) b.tanque(49.0, t)
            b.tick(t)
        }
        return b
    }

    // --- A. viagem simples ---

    @Test
    fun `A - pontos da trilha`() {
        val b = cenarioA()
        assertEquals(120, b.pontos.size)
        val p0 = b.pontos.first()
        assertEquals(T0 + 1000, p0.t)
        assertEquals(6.0, p0.kmh, 0.0)
        assertPerto(20.0, p0.kw)
        assertEquals(1, p0.modoDeCombustivel)
        assertEquals(8.0, p0.taxaDeCombustivel, 0.0)
        assertEquals(0, p0.combustao)
        assertPerto(0.0, p0.km)
        assertTrue(p0.lat.isNaN())
        val p119 = b.pontos.last()
        assertEquals(60.0, p119.kmh, 0.0)
        assertPerto(-8.0, p119.kw)
        assertEquals(1, p119.combustao)
        assertPerto(1.916667, p119.km.let { Math.round(it * 1e6) / 1e6 })
    }

    @Test
    fun `A - resumo ao vivo`() {
        val b = cenarioA()
        val r = b.motor.resumoAoVivo(T0 + 120_000)!!
        assertPerto(1.916667, r.km)
        assertPerto(1.908333, r.kmIntegrado)
        assertEquals(119_000L, r.andandoMs)
        assertPerto(0.9123, r.eletricoKm)
        assertEquals(59_000L, r.eletricoMs)
        assertPerto(0.333333, r.kwhGasto)
        assertPerto(0.133333, r.kwhRecuperado)
        assertPerto(0.152667, r.litros)
        assertPerto(12.554585, r.kmPorLitro)
        assertPerto(10.434783, r.kwhPor100Km)
        assertPerto(0.475983, r.fracaoEletrica)
    }

    @Test
    fun `A - fechamento 10 min depois de desligar`() {
        val b = cenarioA()
        val off = T0 + 121_000
        b.pronto("0", off)
        b.tick(off + 599_000)
        assertTrue(b.motor.viagemAberta)
        assertTrue(b.fechamentos.isEmpty())
        b.tick(off + 600_000)
        assertFalse(b.motor.viagemAberta)
        assertEquals(listOf(T0), b.aberturas.map { it.inicioMs })
        val r = b.fechamentos.single().resumo
        assertEquals(T0, r.inicioMs)
        assertEquals(off, r.fimMs)
        assertPerto(1.9, r.km)
        assertPerto(1.925, r.kmIntegrado)
        assertPerto(12345.6, r.odometroInicial)
        assertPerto(12347.5, r.odometroFinal)
        assertEquals(0, r.odometroSaltos)
        assertEquals(120_000L, r.andandoMs)
        assertEquals(0L, r.paradoMs)
        assertPerto(0.896537, r.eletricoKm)
        assertEquals(59_000L, r.eletricoMs)
        assertPerto(0.333333, r.kwhGasto)
        assertPerto(0.135556, r.kwhRecuperado)
        assertPerto(0.154, r.litros)
        assertPerto(60.0, r.velocidadeMaxima)
        assertPerto(50.0, r.tanqueInicial)
        assertPerto(49.0, r.tanqueFinal)
        assertPerto(80.0, r.bateriaInicial)
        assertPerto(80.0, r.bateriaFinal)
        assertPerto(12.337662, r.kmPorLitro)
        assertPerto(10.409357, r.kwhPor100Km)
        assertPerto(0.471861, r.fracaoEletrica)
        assertPerto(57.75, r.velocidadeMedia)
        assertEquals("12345.6", r.crusPrimeiros[K.HODOMETRO])
        assertEquals("50.0", r.crusPrimeiros[K.NIVEL_DO_TANQUE])
        assertEquals("12347.5", r.crusUltimos[K.HODOMETRO])
        assertEquals("49.0", r.crusUltimos[K.NIVEL_DO_TANQUE])
    }

    // --- B. hodômetro ---

    @Test
    fun `B - leituras do hodometro`() {
        val b = Bancada()
        b.hodometro(1000.0, T0 - 1000)
        b.pronto("1", T0)
        b.velocidade(60.0, T0)
        b.hodometro(1000.1, T0 + 1000)
        b.hodometro(1000.1, T0 + 2000)
        b.hodometro(1000.0, T0 + 3000)
        b.hodometro(1000.5, T0 + 4000)
        b.hodometro(1000.8, T0 + 5000)
        b.hodometro(1001.0, T0 + 105_000)
        val r = b.motor.resumoAoVivo(T0 + 105_000)!!
        assertPerto(0.6, r.km)
        assertEquals(2, r.odometroSaltos)
        assertPerto(0.083333, r.kmIntegrado)
    }

    @Test
    fun `B - hodometro com READY desligado e salto`() {
        val b = Bancada()
        b.hodometro(1000.0, T0 - 1000)
        b.pronto("1", T0)
        b.pronto("0", T0 + 1000)
        b.hodometro(1000.1, T0 + 2000)
        val r = b.motor.resumoAoVivo(T0 + 2000)!!
        assertEquals(1, r.odometroSaltos)
        assertPerto(0.0, r.km)
    }

    // --- C. religar ---

    private fun viagemC(b: Bancada) {
        b.pronto("1", T0)
        b.velocidade(36.0, T0)
        b.ticks(T0, T0 + 120_000)
        b.pronto("0", T0 + 120_000)
    }

    @Test
    fun `C - religar antes de 10 min continua a viagem`() {
        val b = Bancada()
        viagemC(b)
        b.pronto("1", T0 + 120_000 + 599_999)
        assertEquals(listOf(T0), b.aberturas.map { it.inicioMs })
        assertTrue(b.fechamentos.isEmpty())
        assertTrue(b.motor.viagemAberta)
    }

    @Test
    fun `C - religar depois de 10 min fecha no desligamento e abre outra`() {
        val b = Bancada()
        viagemC(b)
        // o desligamento em T0+800 s: refaz com READY desligando mais tarde
        val c = Bancada()
        c.pronto("1", T0)
        c.velocidade(36.0, T0)
        c.ticks(T0, T0 + 120_000)
        c.pronto("0", T0 + 800_000)
        c.pronto("1", T0 + 800_000 + 600_000)
        assertEquals(listOf(T0, T0 + 1_400_000), c.aberturas.map { it.inicioMs })
        val f = c.fechamentos.single().resumo
        assertEquals(T0, f.inicioMs)
        assertEquals(T0 + 800_000, f.fimMs)
        assertPerto(1.2, f.km)
        assertEquals(120_000L, f.andandoMs)
        assertPerto(36.0, f.velocidadeMaxima)
    }

    // --- D. descartar x gravar ---

    private fun viagemCurta(v: Double, segundos: Int): Bancada {
        val b = Bancada()
        b.pronto("1", T0)
        b.velocidade(v, T0)
        b.ticks(T0, T0 + segundos * 1000L)
        b.pronto("0", T0 + segundos * 1000L)
        b.tick(T0 + segundos * 1000L + 600_000)
        return b
    }

    @Test
    fun `D - viagem curta e descartada`() {
        val b = viagemCurta(10.0, 59)
        assertEquals(1, b.descartes.size)
        assertTrue(b.fechamentos.isEmpty())
    }

    @Test
    fun `D - 60 s andando grava mesmo com pouca distancia`() {
        val b = viagemCurta(1.0, 60)
        assertEquals(1, b.fechamentos.size)
        assertEquals(60_000L, b.fechamentos.single().resumo.andandoMs)
    }

    @Test
    fun `D - 0,2 km grava`() {
        val b = viagemCurta(12.0, 60)
        assertEquals(1, b.fechamentos.size)
    }

    // --- E. paradas ---

    private fun cenarioE(): Pair<Bancada, Long> {
        val b = Bancada()
        b.tanque(30.0, T0)
        b.bateria(20.0, T0)
        b.motor.aoPosicao(-22.9, -43.2, Double.NaN, 5.0, T0)
        b.pronto("1", T0)
        b.velocidade(50.0, T0)
        b.ticks(T0, T0 + 120_000)
        val off = T0 + 120_000
        b.pronto("0", off)
        b.tanque(33.0, off + 60_000)
        assertTrue(b.paradas.isEmpty())
        b.tanque(34.0, off + 120_000)
        b.tanque(40.0, off + 180_000)
        b.tanque(39.0, off + 240_000)
        b.bateria(27.0, off + 300_000)
        b.bateria(28.0, off + 360_000)
        b.bateria(90.0, off + 420_000)
        b.pronto("1", off + 900_000)
        return b to off
    }

    @Test
    fun `E - abastecimento e recarga entram na viagem que fecha`() {
        val (b, off) = cenarioE()
        val tipos = b.eventos.filter { it !is EventoDoMotor.Ponto }.map { e ->
            when (e) {
                is EventoDoMotor.Abriu -> "abre ${e.inicioMs}"
                is EventoDoMotor.Parada -> "${e.parada.tipo.texto} ${e.inicioMs}"
                is EventoDoMotor.Fechou -> "fecha ${e.resumo.inicioMs} ${e.resumo.fimMs}"
                else -> "outro"
            }
        }
        assertEquals(
            listOf("abre $T0", "refuel $T0", "charge $T0", "fecha $T0 $off", "abre ${off + 900_000}"),
            tipos,
        )
        val refuel = b.paradas[0].parada
        assertEquals(off, refuel.t)
        assertPerto(30.0, refuel.antes)
        assertPerto(40.0, refuel.depois)
        assertPerto(5.5, refuel.quantidade)
        assertPerto(-22.9, refuel.lat)
        assertPerto(-43.2, refuel.lon)
        val charge = b.paradas[1].parada
        assertPerto(20.0, charge.antes)
        assertPerto(90.0, charge.depois)
        assertPerto(70.0, charge.quantidade)
    }

    @Test
    fun `E - defeito 3 - nao repete a parada na viagem nova`() {
        val (b, off) = cenarioE()
        b.tanque(45.0, off + 1_000_000)
        assertEquals(2, b.paradas.size)
    }

    @Test
    fun `E - parada detectada sem viagem aberta espera a proxima abertura`() {
        val b = Bancada()
        b.tanque(30.0, T0)
        b.pronto("1", T0)
        b.velocidade(50.0, T0)
        b.ticks(T0, T0 + 120_000)
        val off = T0 + 120_000
        b.pronto("0", off)
        b.tick(off + 600_000)
        assertEquals(1, b.fechamentos.size)
        // com a viagem fechada o tanque sobe: candidata
        b.tanque(36.0, off + 700_000)
        // o sistema dorme e acorda; o retrato diz que o carro está desligado: estaciona sem viagem aberta
        b.motor.tick(off + 701_000, 900_000)
        b.pronto("0", off + 800_000)
        assertTrue(b.paradas.isEmpty())
        assertEquals(1, b.motor.paradasEmEspera.size)
        b.pronto("1", off + 900_000)
        val parada = b.paradas.single()
        assertEquals(off + 900_000, parada.inicioMs)
        assertEquals(off, parada.parada.t)
        assertPerto(30.0, parada.parada.antes)
        assertPerto(36.0, parada.parada.depois)
        assertPerto(3.3, parada.parada.quantidade)
        assertTrue(b.motor.paradasEmEspera.isEmpty())
    }

    // --- F. guiagem ---

    private fun nav(b: Bancada, ativo: Boolean, m: Double, s: Double, destino: Boolean, t: Long) {
        val turn = if (destino) "DESTINATION" else "TURN"
        b.sinal(K.NAVEGACAO, """{"active":$ativo,"remaining_m":$m,"remaining_s":$s,"turn":"$turn"}""", t)
    }

    @Test
    fun `F - chegada ao destino`() {
        val b = Bancada()
        b.pronto("1", T0)
        b.velocidade(72.0, T0)
        nav(b, true, 12000.0, 900.4, false, T0 + 1000)
        b.ticks(T0 + 1000, T0 + 600_000)
        nav(b, true, 250.0, 20.0, true, T0 + 600_000)
        b.tick(T0 + 601_000)
        val r = b.motor.resumoAoVivo(T0 + 601_000)!!
        assertTrue(r.guiada)
        assertTrue(r.chegou)
        assertPerto(12.0, r.kmPlanejado)
        assertEquals(900, r.segundosPlanejados)
        assertPerto(11.98, r.kmGuiados)
        assertEquals(599, r.segundosGuiados)
        assertPerto(12.02, r.km)
        nav(b, false, 0.0, 0.0, false, T0 + 602_000)
        b.pronto("0", T0 + 603_000)
        b.tick(T0 + 603_000 + 600_000)
        val f = b.fechamentos.single().resumo
        assertTrue(f.chegou)
        assertPerto(12.06, f.km)
    }

    @Test
    fun `F - guiagem cancelada longe`() {
        val b = Bancada()
        b.pronto("1", T0)
        b.velocidade(72.0, T0)
        nav(b, true, 12000.0, 900.0, false, T0 + 1000)
        b.ticks(T0 + 1000, T0 + 100_000)
        nav(b, true, 11000.0, 800.0, false, T0 + 100_000)
        b.tick(T0 + 101_000)
        nav(b, false, 0.0, 0.0, false, T0 + 101_000)
        val r = b.motor.resumoAoVivo(T0 + 101_000)!!
        assertFalse(r.chegou)
        assertPerto(2.0, r.kmGuiados)
        assertEquals(100, r.segundosGuiados)
        assertPerto(2.02, r.km)
    }

    @Test
    fun `F - guiagem ativa segura a viagem por uma hora`() {
        val b = Bancada()
        b.pronto("1", T0)
        b.velocidade(72.0, T0)
        nav(b, true, 12000.0, 900.0, false, T0 + 1000)
        b.ticks(T0 + 1000, T0 + 100_000)
        nav(b, true, 11000.0, 800.0, false, T0 + 100_000)
        b.pronto("0", T0 + 101_000)
        b.tick(T0 + 101_000 + 3_000_000)
        assertTrue(b.motor.viagemAberta)
        b.tick(T0 + 100_000 + 3_599_999)
        assertTrue(b.motor.viagemAberta)
        b.tick(T0 + 100_000 + 3_600_001)
        assertFalse(b.motor.viagemAberta)
        assertEquals(T0 + 101_000, b.eventos.filterIsInstance<EventoDoMotor.Fechou>().single().resumo.fimMs)
    }

    @Test
    fun `F - defeito 7 - sem distancia restante nao vira guiada`() {
        val b = Bancada()
        b.pronto("1", T0)
        b.sinal(K.NAVEGACAO, """{"active":true,"turn":"TURN"}""", T0 + 1000)
        b.sinal(K.NAVEGACAO, """{"active":true,"remaining_m":null,"turn":"TURN"}""", T0 + 2000)
        assertFalse(b.motor.resumoAoVivo(T0 + 3000)!!.guiada)
        b.sinal(K.NAVEGACAO, """{"active":true,"remaining_m":0,"turn":"TURN"}""", T0 + 3000)
        assertFalse(b.motor.resumoAoVivo(T0 + 3000)!!.guiada)
    }

    @Test
    fun `F - guiagem so comeca uma vez por viagem`() {
        val b = Bancada()
        b.pronto("1", T0)
        nav(b, true, 12000.0, 900.0, false, T0 + 1000)
        nav(b, true, 100.0, 5.0, false, T0 + 2000)
        assertTrue(b.motor.resumoAoVivo(T0 + 2000)!!.chegou)
        nav(b, true, 9000.0, 700.0, false, T0 + 3000)
        val r = b.motor.resumoAoVivo(T0 + 3000)!!
        assertPerto(12.0, r.kmPlanejado)
        assertTrue(r.chegou)
    }

    @Test
    fun `F - JSON de navegacao ilegivel e ignorado`() {
        val b = Bancada()
        b.pronto("1", T0)
        b.sinal(K.NAVEGACAO, "isso nao e json", T0 + 1000)
        assertFalse(b.motor.resumoAoVivo(T0 + 1000)!!.guiada)
    }

    // --- G. posição e subida ---

    @Test
    fun `G - posicao de partida respeita 30 s de idade`() {
        val novo = Bancada()
        novo.motor.aoPosicao(-22.9, -43.2, 10.0, 5.0, T0 - 1000)
        novo.pronto("1", T0)
        assertPerto(-22.9, novo.motor.estado.latInicial)

        val velho = Bancada()
        velho.motor.aoPosicao(-22.9, -43.2, 10.0, 5.0, T0 - 30_001)
        velho.pronto("1", T0)
        assertTrue(velho.motor.estado.latInicial.isNaN())
        velho.motor.aoPosicao(-22.8, -43.1, Double.NaN, 5.0, T0 + 1000)
        assertPerto(-22.8, velho.motor.estado.latInicial)
        assertPerto(-22.8, velho.motor.estado.latFinal)

        val limite = Bancada()
        limite.motor.aoPosicao(-22.9, -43.2, 10.0, 5.0, T0 - 30_000)
        limite.pronto("1", T0)
        assertPerto(-22.9, limite.motor.estado.latInicial)
    }

    @Test
    fun `G - subida acumulada com histerese de 3 m`() {
        val b = Bancada()
        b.pronto("1", T0)
        var t = T0
        for (alt in listOf(10.0, 12.0, 13.5, 14.0, 20.0, 17.0, 16.5, 14.9, 14.0)) {
            t += 1000
            b.motor.aoPosicao(-22.9, -43.2, alt, 5.0, t)
        }
        assertPerto(10.0, b.motor.estado.subidaM)
        b.motor.aoPosicao(-22.9, -43.2, 18.0, 26.0, t + 1000)
        b.motor.aoPosicao(-22.9, -43.2, 19.0, 26.0, t + 2000)
        b.motor.aoPosicao(-22.9, -43.2, 25.0, 30.0, t + 3000)
        assertPerto(10.0, b.motor.estado.subidaM)
    }

    @Test
    fun `G - precisao pior que 50 m e rejeitada`() {
        val b = Bancada()
        b.pronto("1", T0)
        b.motor.aoPosicao(-22.9, -43.2, Double.NaN, 50.01, T0 + 1000)
        assertTrue(b.motor.estado.latInicial.isNaN())
        b.motor.aoPosicao(-22.9, -43.2, Double.NaN, 50.0, T0 + 2000)
        assertPerto(-22.9, b.motor.estado.latInicial)
        b.motor.aoPosicao(Double.NaN, -43.2, Double.NaN, 5.0, T0 + 3000)
        assertPerto(-22.9, b.motor.estado.latFinal)
    }

    @Test
    fun `G - ponto leva a posicao com ate 5000 ms de idade`() {
        val a = Bancada()
        a.pronto("1", T0)
        a.motor.aoPosicao(-22.9, -43.2, 12.0, 5.0, T0)
        a.tick(T0 + 5000)
        assertPerto(-22.9, a.pontos.single().lat)
        assertPerto(12.0, a.pontos.single().alt)
        val b = Bancada()
        b.pronto("1", T0)
        b.motor.aoPosicao(-22.9, -43.2, 12.0, 5.0, T0)
        b.tick(T0 + 5500)
        assertTrue(b.pontos.single().lat.isNaN())
    }

    // --- H. bruscas ---

    private fun bruscas(amostras: List<Double>, dts: List<Long>): Pair<Int, Int> {
        val b = Bancada()
        b.pronto("1", T0)
        var t = T0
        amostras.forEachIndexed { i, v ->
            t += dts[i]
            b.velocidade(v, t)
        }
        return b.motor.estado.aceleracoesBruscas to b.motor.estado.frenagensBruscas
    }

    private fun mil(n: Int) = List(n) { 1000L }

    @Test
    fun `H - aceleracao e frenagem bruscas`() {
        assertEquals(1 to 0, bruscas(listOf(0.0, 11.0), listOf(0L, 1000)))
        assertEquals(0 to 0, bruscas(listOf(0.0, 10.0), listOf(0L, 1000)))
        assertEquals(0 to 1, bruscas(listOf(60.0, 47.0), listOf(0L, 1000)))
        assertEquals(0 to 0, bruscas(listOf(60.0, 48.0), listOf(0L, 1000)))
        assertEquals(0 to 1, bruscas(listOf(60.0, 47.4), listOf(0L, 1000)))
        assertEquals(0 to 0, bruscas(listOf(60.0, 47.5), listOf(0L, 1000)))
    }

    @Test
    fun `H - episodios contam uma vez`() {
        assertEquals(2 to 0, bruscas(listOf(0.0, 11.0, 18.0, 30.0, 31.0, 43.0), listOf(0L) + mil(5)))
        assertEquals(0 to 2, bruscas(listOf(60.0, 47.0, 41.0, 29.0, 28.0, 15.0), listOf(0L) + mil(5)))
    }

    @Test
    fun `H - intervalos fora da janela`() {
        assertEquals(1 to 0, bruscas(listOf(0.0, 50.0, 11.0), listOf(0L, 399, 601)))
        assertEquals(0 to 0, bruscas(listOf(0.0, 3.0), listOf(0L, 400)))
        assertEquals(1 to 0, bruscas(listOf(0.0, 50.0), listOf(0L, 3000)))
        assertEquals(0 to 0, bruscas(listOf(0.0, 50.0), listOf(0L, 3001)))
    }

    private fun paradas(amostras: List<Double>, pronto: Boolean = true): Int {
        val b = Bancada()
        if (pronto) b.pronto("1", T0)
        var t = T0
        for (v in amostras) {
            t += 1000
            b.velocidade(v, t)
        }
        return b.motor.estado.paradas
    }

    @Test
    fun `H - paradas`() {
        assertEquals(2, paradas(listOf(0.0, 6.0, 0.9, 6.0, 1.0, 0.0)))
        assertEquals(0, paradas(listOf(5.0, 0.0)))
        assertEquals(0, paradas(listOf(0.0, 6.0, 0.9, 6.0, 1.0, 0.0), pronto = false))
    }

    // --- I. outras integrações ---

    @Test
    fun `I - marcha lenta soma litros por hora e pontos a cada 10 s`() {
        val b = Bancada()
        b.pronto("1", T0)
        b.velocidade(0.0, T0)
        b.sinal(K.CONSUMO_INSTANTANEO, "{4,1.2}", T0)
        b.ticks(T0, T0 + 360_000)
        val r = b.motor.resumoAoVivo(T0 + 360_000)!!
        assertPerto(0.12, r.litros)
        assertEquals(360_000L, r.paradoMs)
        assertEquals(36, b.pontos.size)
        assertEquals(listOf(T0 + 1000, T0 + 11_000, T0 + 21_000), b.pontos.take(3).map { it.t })
    }

    @Test
    fun `I - passo de 10 s integra e de 10,001 s nao`() {
        val a = Bancada()
        a.pronto("1", T0)
        a.velocidade(36.0, T0)
        a.tick(T0 + 10_000)
        assertPerto(0.1, a.motor.estado.distanciaIntegrada)
        val b = Bancada()
        b.pronto("1", T0)
        b.velocidade(36.0, T0)
        b.tick(T0 + 10_001)
        assertPerto(0.0, b.motor.estado.distanciaIntegrada)
    }

    @Test
    fun `I - hodometro tardio usa o maior no final`() {
        val b = Bancada()
        b.pronto("1", T0)
        b.velocidade(36.0, T0)
        b.ticks(T0, T0 + 60_000)
        b.hodometro(500.0, T0 + 60_000)
        assertPerto(0.6, b.motor.resumoAoVivo(T0 + 60_000)!!.km)
        b.ticks(T0 + 60_000, T0 + 71_000)
        b.hodometro(500.1, T0 + 71_000)
        assertPerto(0.71, b.motor.resumoAoVivo(T0 + 71_000)!!.km.let { Math.round(it * 100) / 100.0 })
        b.pronto("0", T0 + 71_000)
        b.tick(T0 + 71_000 + 600_000)
        assertPerto(0.71, b.fechamentos.single().resumo.km)
    }

    @Test
    fun `I - fracao eletrica aplicada ao km`() {
        val b = Bancada()
        b.pronto("1", T0)
        b.velocidade(36.0, T0)
        b.sinal(K.FLUXO_DE_POTENCIA, "v1|1|0|0|0", T0)
        b.ticks(T0, T0 + 30_000)
        b.sinal(K.FLUXO_DE_POTENCIA, "v1|1|1|0|0", T0 + 30_000)
        b.ticks(T0 + 30_000, T0 + 90_000)
        val r = b.motor.resumoAoVivo(T0 + 90_000)!!
        assertPerto(0.9, r.km)
        assertPerto(0.3, r.eletricoKm)
        assertPerto(0.333333, r.fracaoEletrica)
    }

    @Test
    fun `I - ultimo sinal de combustao define o estado`() {
        val b = Bancada()
        b.pronto("1", T0)
        b.sinal(K.COMBUSTAO, "1", T0)
        b.sinal(K.FLUXO_DE_POTENCIA, "v1|1|0|0|0", T0)
        b.tick(T0 + 1000)
        assertEquals(0, b.pontos.single().combustao)
    }

    @Test
    fun `I - combustao desconhecida vira menos um no ponto`() {
        val b = Bancada()
        b.pronto("1", T0)
        b.tick(T0 + 1000)
        assertEquals(-1, b.pontos.single().combustao)
    }

    @Test
    fun `I - tensao ou corrente fora da faixa param a energia`() {
        val b = Bancada()
        b.pronto("1", T0)
        b.sinal(K.TENSAO_DO_PACOTE, "400", T0)
        b.sinal(K.CORRENTE_DO_PACOTE, "50", T0)
        b.ticks(T0, T0 + 10_000)
        val antes = b.motor.estado.kwhGasto
        b.sinal(K.CORRENTE_DO_PACOTE, "65535", T0 + 10_000)
        b.ticks(T0 + 10_000, T0 + 20_000)
        assertPerto(antes, b.motor.estado.kwhGasto)
        assertTrue(b.pontos.last().kw.isNaN())
    }

    @Test
    fun `sinal de velocidade fora da faixa e descartado`() {
        val b = Bancada()
        b.pronto("1", T0)
        b.velocidade(50.0, T0)
        b.sinal(K.VELOCIDADE, "301", T0 + 1000)
        b.sinal(K.VELOCIDADE, "65535", T0 + 2000)
        b.tick(T0 + 3000)
        assertEquals(50.0, b.pontos.single().kmh, 0.0)
    }

    // --- J. finalizar ---

    @Test
    fun `J - finalizar com READY ligado fecha e abre outra`() {
        val b = Bancada()
        b.pronto("1", T0)
        b.velocidade(60.0, T0)
        b.ticks(T0, T0 + 100_000)
        b.motor.finalizarAgora(T0 + 100_000)
        assertEquals(listOf(T0, T0 + 100_000), b.aberturas.map { it.inicioMs })
        assertEquals(T0 + 100_000, b.fechamentos.single().resumo.fimMs)
        assertTrue(b.motor.viagemAberta)
    }

    @Test
    fun `J - finalizar com READY desligado nao abre outra`() {
        val b = Bancada()
        b.pronto("1", T0)
        b.velocidade(60.0, T0)
        b.ticks(T0, T0 + 100_000)
        b.pronto("0", T0 + 100_000)
        b.motor.finalizarAgora(T0 + 200_000)
        assertFalse(b.motor.viagemAberta)
        assertEquals(T0 + 100_000, b.fechamentos.single().resumo.fimMs)
    }

    @Test
    fun `J - finalizar sem viagem aberta nao faz nada`() {
        val b = Bancada()
        b.motor.finalizarAgora(T0)
        assertTrue(b.eventos.isEmpty())
    }

    // --- K. restauração e suspensão ---

    private fun comCheckpoint(): EstadoDaViagem {
        val b = Bancada()
        b.pronto("1", T0)
        b.velocidade(60.0, T0)
        b.ticks(T0, T0 + 100_000)
        return CodecDoCheckpoint.decodificar(CodecDoCheckpoint.codificar(b.motor.estado))
    }

    @Test
    fun `K - restaurar antes de 10 min mantem aberta`() {
        val estado = comCheckpoint()
        assertEquals(T0 + 100_000, estado.ultimoT)
        val b = Bancada()
        b.motor.restaurar(estado, T0 + 100_000 + 599_999)
        assertTrue(b.motor.viagemAberta)
        assertTrue(b.eventos.isEmpty())
    }

    @Test
    fun `K - restaurar com 10 min fecha no ultimo instante visto`() {
        val estado = comCheckpoint()
        val b = Bancada()
        b.motor.restaurar(estado, T0 + 100_000 + 600_000)
        assertFalse(b.motor.viagemAberta)
        assertEquals(T0 + 100_000, b.fechamentos.single().resumo.fimMs)
    }

    @Test
    fun `K - viagem restaurada continua se READY voltar a tempo`() {
        val estado = comCheckpoint()
        val b = Bancada()
        b.motor.restaurar(estado, T0 + 110_000)
        b.pronto("1", T0 + 115_000)
        assertTrue(b.motor.viagemAberta)
        assertTrue(b.aberturas.isEmpty())
        assertEquals(estado.inicioMs, b.motor.inicioDaViagemMs)
    }

    @Test
    fun `K - acordar de suspensao fecha a viagem`() {
        val b = Bancada()
        b.pronto("1", T0)
        b.velocidade(60.0, T0)
        b.ticks(T0, T0 + 100_000)
        b.motor.tick(T0 + 100_000 + 3_600_000, 3_600_000)
        assertFalse(b.motor.viagemAberta)
        val r = b.fechamentos.single().resumo
        assertEquals(T0 + 100_000, r.fimMs)
        assertPerto(1.666667, r.km)
        assertNull(b.motor.prontoAtual)
    }

    @Test
    fun `K - acordar com guiagem ativa mantem a viagem`() {
        val b = Bancada()
        b.pronto("1", T0)
        b.velocidade(60.0, T0)
        nav(b, true, 12000.0, 900.0, false, T0 + 1000)
        b.ticks(T0 + 1000, T0 + 100_000)
        b.motor.tick(T0 + 100_000 + 700_000, 700_000)
        assertTrue(b.motor.viagemAberta)
        assertEquals(T0 + 100_000, b.motor.estado.desligadoEm)
    }

    // --- Casos numerados ---

    @Test
    fun `C-4 abertura herda o que o carro disse antes`() {
        val b = Bancada()
        b.hodometro(100.0, T0 - 1000)
        b.tanque(40.0, T0 - 1000)
        b.bateria(70.0, T0 - 1000)
        b.velocidade(30.0, T0 - 1000)
        b.sinal("car.basic.accumulated_odometer", "123", T0 - 1000)
        b.pronto("1", T0)
        val e = b.motor.estado
        assertTrue(e.aberta)
        assertEquals(T0, e.inicioMs)
        assertPerto(100.0, e.odometroInicial)
        assertTrue(e.partiuComHodometro)
        assertPerto(40.0, e.tanqueInicial)
        assertPerto(70.0, e.bateriaInicial)
        assertTrue(e.armouParada)
        assertEquals("123", e.crusPrimeiros["car.basic.accumulated_odometer"])
        assertEquals("100.0", e.crusPrimeiros[K.HODOMETRO])
    }

    @Test
    fun `C-5 repetir READY desligado nao muda o instante do desligamento`() {
        val b = Bancada()
        b.pronto("1", T0)
        b.pronto("0", T0 + 5000)
        b.pronto("0", T0 + 9000)
        assertEquals(T0 + 5000, b.motor.estado.desligadoEm)
    }

    @Test
    fun `C-15 sem READY o maximo nao e atualizado`() {
        val b = Bancada()
        b.pronto("1", T0)
        b.velocidade(40.0, T0)
        b.pronto("0", T0 + 1000)
        b.velocidade(90.0, T0 + 2000)
        assertPerto(40.0, b.motor.estado.velocidadeMaxima)
    }

    @Test
    fun `C-18 a janela de acomodacao acaba em 180 s e apaga os niveis de referencia`() {
        val b = Bancada()
        b.tanque(30.0, T0)
        b.pronto("1", T0)
        b.pronto("0", T0 + 10_000)
        b.pronto("1", T0 + 20_000)
        // dentro da janela de 180 s o tanque subindo vira candidata...
        b.tanque(36.0, T0 + 30_000)
        b.ticks(T0 + 20_000, T0 + 20_000 + 180_000)
        // ...que sai quando a janela acaba
        assertEquals(1, b.paradas.size)
        b.tanque(60.0, T0 + 400_000)
        assertEquals(1, b.paradas.size)
    }

    @Test
    fun `nivel parado alimentado pela ultima viagem vale para a deteccao`() {
        val b = Bancada()
        b.motor.alimentarNivelParado(30.0, 20.0, -22.9, -43.2, T0 - 1000)
        b.pronto("1", T0)
        b.pronto("0", T0 + 1000)
        b.tanque(36.0, T0 + 2000)
        b.pronto("1", T0 + 700_000)
        val p = b.paradas.single().parada
        assertEquals(TipoDeParada.ABASTECIMENTO, p.tipo)
        assertEquals(T0 + 1000, p.t)
        assertPerto(30.0, p.antes)
        assertPerto(-22.9, p.lat)
    }

    @Test
    fun `nivel parado alimentado nao sobrescreve o que o carro ja disse`() {
        val b = Bancada()
        b.tanque(50.0, T0)
        b.motor.alimentarNivelParado(30.0, Double.NaN, Double.NaN, Double.NaN, T0)
        b.pronto("1", T0)
        b.pronto("0", T0 + 1000)
        b.tanque(54.0, T0 + 2000)
        b.pronto("1", T0 + 700_000)
        assertPerto(50.0, b.paradas.single().parada.antes)
    }
}
