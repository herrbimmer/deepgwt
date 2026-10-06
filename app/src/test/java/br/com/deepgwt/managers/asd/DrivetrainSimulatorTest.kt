// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.managers.asd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O câmbio simulado do ASD.
 *
 * A queixa que originou esta revisão foi "a troca de marcha acontece de maneira excessivamente
 * instantânea". O que estes testes travam não é um número de duração — é o FORMATO da troca: que
 * ela tenha três fases, que a carga caia no meio (é o corte de torque que o ouvido lê como
 * "trocou"), que a redução passe ACIMA da rotação de destino antes de assentar, e que Sport e Eco
 * não troquem no mesmo tempo. Um refactor que volte a deslizar a rotação sem nada mais quebra
 * aqui, que é o ponto.
 */
class DrivetrainSimulatorTest {

    private val DT = 0.02 // 50 Hz (o AsdManager roda a 100 Hz desde a 6.3; os testes medem formato, não tick)

    private fun simulador(cadencia: DrivetrainSimulator.Cadencia = DrivetrainSimulator.Cadencia.NORMAL) =
        DrivetrainSimulator().apply {
            start()
            this.cadencia = cadencia
        }

    /**
     * Um carro de teste que acelera sozinho, para os testes não precisarem inventar velocidade.
     *
     * Teleportar o carro (acelerar até 46 km/h e depois alimentar 90 km/h fixo) parecia inofensivo
     * e não é: a relação engatada joga a rotação contra o corte, o limitador assume, e o limitador
     * tem prioridade sobre a fase da troca no cálculo da carga — o teste passaria a medir o
     * limitador achando que media o reengate.
     */
    private class Pista(
        val d: DrivetrainSimulator,
        val aceleracaoMs2: Double = 2.4,
        val desaceleracaoMs2: Double = 1.8
    ) {
        var velocidadeKmh = 0.0
            private set

        /** Um tick. Devolve a carga ANTES do tick, que é o patamar de onde um corte parte. */
        fun passo(pedal: Double, freio: Double = 0.0, dt: Double = 0.02): Double {
            val antes = d.load
            val delta = if (pedal > 0.02) aceleracaoMs2 else -desaceleracaoMs2
            velocidadeKmh = (velocidadeKmh + delta * dt * 3.6).coerceIn(0.0, 240.0)
            d.update(dt, pedal, freio, velocidadeKmh, true)
            return antes
        }

        /**
         * Um tick a velocidade CONSTANTE. É o estado em que um câmbio sobe até a marcha mais longa
         * e deixa a rotação assentar lá embaixo — condição necessária para um kickdown existir, e
         * impossível de alcançar com o carro sempre acelerando.
         */
        fun cruzeiro(pedal: Double, freio: Double = 0.0, dt: Double = 0.02): Double {
            val antes = d.load
            d.update(dt, pedal, freio, velocidadeKmh, true)
            return antes
        }
    }

    /**
     * Acelera do zero até a primeira troca começar.
     *
     * Devolve a carga do tick ANTERIOR ao início da troca: no tick em que a troca começa o corte de
     * torque já está aplicado (é o mesmo `update`), então ler `load` depois disso mediria o buraco,
     * não o patamar de onde ele parte.
     */
    private fun ateComecarATrocar(pista: Pista, pedal: Double = 1.0, maxPassos: Int = 4000): Double {
        var passos = 0
        var cargaAntes = 0.0
        while (passos < maxPassos && !pista.d.emTroca) {
            cargaAntes = pista.passo(pedal)
            passos++
        }
        return cargaAntes
    }

    @Test
    fun `a troca passa pelas tres fases, nessa ordem`() {
        val pista = Pista(simulador())
        ateComecarATrocar(pista)
        assertEquals(DrivetrainSimulator.Fase.TORQUE, pista.d.fase)

        val vistas = mutableListOf(pista.d.fase)
        var passos = 0
        while (pista.d.emTroca && passos < 500) {
            pista.passo(1.0)
            if (vistas.last() != pista.d.fase) vistas.add(pista.d.fase)
            passos++
        }
        assertEquals(
            listOf(
                DrivetrainSimulator.Fase.TORQUE,
                DrivetrainSimulator.Fase.INERCIA,
                DrivetrainSimulator.Fase.REENGATE,
                DrivetrainSimulator.Fase.NENHUMA
            ),
            vistas
        )
    }

    @Test
    fun `a marcha so troca no fim da fase de inercia, nao no comeco da troca`() {
        val pista = Pista(simulador())
        ateComecarATrocar(pista)
        val marchaAntes = pista.d.gear
        assertEquals(DrivetrainSimulator.Fase.TORQUE, pista.d.fase)

        // Toda a fase de torque acontece com a marcha VELHA ainda engatada: é a embreagem que sai
        // aliviando, não a que entra.
        while (pista.d.fase == DrivetrainSimulator.Fase.TORQUE) {
            pista.passo(1.0)
            assertEquals(marchaAntes, pista.d.gear)
        }
        while (pista.d.fase == DrivetrainSimulator.Fase.INERCIA) {
            pista.passo(1.0)
        }
        assertEquals(marchaAntes + 1, pista.d.gear)
    }

    @Test
    fun `a troca nao e instantanea`() {
        val pista = Pista(simulador())
        ateComecarATrocar(pista)
        var decorrido = 0.0
        var passos = 0
        while (pista.d.emTroca && passos < 500) {
            pista.passo(1.0)
            decorrido += DT
            passos++
        }
        // Pé no fundo em NORMAL é o caso MAIS RÁPIDO desta cadência, e mesmo ele leva tempo de
        // sobra para ser ouvido. O teto protege contra o outro erro: uma troca tão longa que soa
        // como o câmbio patinando.
        assertTrue("troca durou $decorrido s", decorrido > 0.25)
        assertTrue("troca durou $decorrido s", decorrido < 0.9)
    }

    @Test
    fun `a carga cai abaixo do pedido durante o corte de torque`() {
        val pista = Pista(simulador())
        val cargaAntes = ateComecarATrocar(pista)
        // Antes da troca, com o pé no fundo, a carga estava perto de 1.
        assertTrue("carga antes = $cargaAntes", cargaAntes > 0.7)

        var menorCarga = pista.d.load
        while (pista.d.fase == DrivetrainSimulator.Fase.TORQUE ||
            pista.d.fase == DrivetrainSimulator.Fase.INERCIA
        ) {
            pista.passo(1.0)
            if (pista.d.load < menorCarga) menorCarga = pista.d.load
        }
        // É ESTE buraco que se ouve como troca de marcha. Sem ele a troca vira um glissando de tom.
        assertTrue("menor carga na troca = $menorCarga", menorCarga < 0.0)
    }

    @Test
    fun `o reengate devolve a carga ao topo antes de assentar`() {
        val pista = Pista(simulador())
        ateComecarATrocar(pista)
        while (pista.d.fase != DrivetrainSimulator.Fase.REENGATE && pista.d.emTroca) {
            pista.passo(1.0)
        }
        assertEquals(DrivetrainSimulator.Fase.REENGATE, pista.d.fase)
        var maiorCarga = pista.d.load
        while (pista.d.fase == DrivetrainSimulator.Fase.REENGATE) {
            pista.passo(1.0)
            if (pista.d.load > maiorCarga) maiorCarga = pista.d.load
        }
        // Com pedido 1.0 a carga satura em 1.0; o que se afirma é que ela VOLTA ao topo dentro da
        // própria fase, e não depois — senão o reengate soaria como um degrau atrasado.
        assertTrue("maior carga no reengate = $maiorCarga", maiorCarga > 0.8)
    }

    @Test
    fun `a reducao passa acima da rotacao de destino antes de assentar`() {
        val pista = Pista(simulador())
        // Acelera com pé leve até uma velocidade de estrada...
        var passos = 0
        while (pista.velocidadeKmh < 80.0 && passos < 4000) {
            pista.passo(0.3)
            passos++
        }
        // ...e mantém cruzeiro, para o câmbio subir e a rotação assentar embaixo. Sem esta parte
        // não existe kickdown para testar: acelerando sem parar a rotação nunca cai o suficiente.
        repeat(800) { pista.cruzeiro(0.12) }
        val marchaAlta = pista.d.gear
        assertTrue("precisava ter subido de marcha, ficou em $marchaAlta", marchaAlta >= 4)
        assertTrue("rotação de cruzeiro = ${pista.d.engineRpm}", pista.d.engineRpm < 3600)

        // Pé no fundo em cruzeiro: é o kickdown.
        passos = 0
        while (!pista.d.emTroca && passos < 300) {
            pista.cruzeiro(1.0)
            passos++
        }
        assertEquals(DrivetrainSimulator.Troca.REDUCAO, pista.d.troca)

        var maiorRpm = 0.0
        var rpmNoFim = 0.0
        while (pista.d.fase == DrivetrainSimulator.Fase.TORQUE ||
            pista.d.fase == DrivetrainSimulator.Fase.INERCIA
        ) {
            pista.cruzeiro(1.0)
            if (pista.d.engineRpm > maiorRpm) maiorRpm = pista.d.engineRpm
            rpmNoFim = pista.d.engineRpm
            passos++
        }
        // O blip: o motor é levado acima de onde a marcha nova o colocaria, e volta. Sem essa
        // ultrapassagem a redução soa como uma subida de tom qualquer.
        assertTrue("pico $maiorRpm vs fim $rpmNoFim", maiorRpm > rpmNoFim + 200)
    }

    @Test
    fun `sport troca mais rapido que eco`() {
        fun duracaoDaTroca(cadencia: DrivetrainSimulator.Cadencia): Double {
            val pista = Pista(simulador(cadencia))
            ateComecarATrocar(pista)
            var decorrido = 0.0
            var passos = 0
            while (pista.d.emTroca && passos < 800) {
                pista.passo(1.0)
                decorrido += DT
                passos++
            }
            return decorrido
        }
        val sport = duracaoDaTroca(DrivetrainSimulator.Cadencia.SPORT)
        val eco = duracaoDaTroca(DrivetrainSimulator.Cadencia.ECO)
        assertTrue("sport=$sport eco=$eco", sport < eco)
    }

    @Test
    fun `sport segura a marcha ate rotacao mais alta que eco`() {
        val sport = simulador(DrivetrainSimulator.Cadencia.SPORT)
        val eco = simulador(DrivetrainSimulator.Cadencia.ECO)
        assertTrue(sport.rpmDeSubida(1.0) > eco.rpmDeSubida(1.0))
        assertTrue(sport.rpmDeSubida(0.3) > eco.rpmDeSubida(0.3))
        // E nenhuma das duas manda trocar em cima do corte: senão o limitador bateria antes.
        assertTrue(sport.rpmDeSubida(1.0) < DrivetrainSimulator.REDLINE_RPM)
        assertTrue(eco.rpmDeSubida(1.0) < DrivetrainSimulator.REDLINE_RPM)
    }

    @Test
    fun `o conversor patina na arrancada em vez de colar na roda`() {
        // Parado, em D, pé no fundo: o motor sobe até a rotação de calço e para ali. É o que um
        // automático faz preso no freio — nem fica na marcha lenta, nem sobe até o corte.
        val segurandoNoFreio = simulador()
        repeat(200) { segurandoNoFreio.update(DT, 1.0, 0.0, 0.0, true) }
        assertTrue("rpm parado com pé no fundo = ${segurandoNoFreio.engineRpm}", segurandoNoFreio.engineRpm > 1800)
        assertTrue("rpm parado com pé no fundo = ${segurandoNoFreio.engineRpm}", segurandoNoFreio.engineRpm < 3200)
        // E não sai trocando de marcha com o carro imóvel.
        assertEquals(1, segurandoNoFreio.gear)

        // A mesma situação sem pedal fica na marcha lenta: a patinagem é pedida pelo pé, não é
        // um piso constante.
        val parado = simulador()
        repeat(400) { parado.update(DT, 0.0, 0.0, 0.0, true) }
        assertTrue("rpm parado sem pé = ${parado.engineRpm}", parado.engineRpm < 1100)
    }

    @Test
    fun `a patinagem fecha conforme a velocidade sobe`() {
        val d = simulador()
        val paradoComPe = d.rpmComPatinagem(rpmAcoplada = 0.0, speedMs = 0.0, demanda = 1.0)
        val rodandoComPe = d.rpmComPatinagem(rpmAcoplada = 2000.0, speedMs = 20.0, demanda = 1.0)
        assertTrue("parado = $paradoComPe", paradoComPe > 2000)
        // Acima da velocidade de travamento o conversor não acrescenta nada: rotação = relação.
        assertEquals(2000.0, rodandoComPe, 0.001)
    }

    @Test
    fun `uma reducao que estouraria o motor e recusada`() {
        val d = simulador()
        // 110 km/h: a 2ª pediria mais de 9000 rpm. A conta tem de subir o alvo até caber, e devolver
        // null quando nem a marcha imediatamente abaixo couber.
        val a110 = 110.0 / 3.6
        repeat(3000) { d.update(DT, 0.35, 0.0, (it * DT * 2.4 * 3.6).coerceAtMost(110.0), true) }
        assertTrue("ficou em ${d.gear}ª", d.gear >= 3)
        val alvo = d.marchaDeReducao(d.gear - 2, a110)
        assertTrue("alvo = $alvo em ${d.gear}ª a 110 km/h", alvo != null && alvo < d.gear)
        val rodaRpm = (a110 / (2 * Math.PI * 0.325)) * 60
        val rpmResultante = rodaRpm * doubleArrayOf(4.714, 3.143, 2.106, 1.667, 1.285, 1.0, 0.839, 0.667)[alvo!! - 1] * 3.42
        assertTrue("redução para $alvo daria $rpmResultante rpm", rpmResultante < DrivetrainSimulator.REDLINE_RPM)
    }

    @Test
    fun `da primeira marcha nao existe reducao`() {
        val d = simulador()
        assertNull(d.marchaDeReducao(0, 10.0))
    }

    @Test
    fun `o limitador bate em vez de segurar a rotacao parada`() {
        val d = simulador()
        // 8ª marcha e velocidade absurda: é o único jeito de encostar no corte, já que os pontos
        // de troca ficam todos abaixo dele.
        val amostras = mutableListOf<Double>()
        var velocidade = 0.0
        repeat(4000) {
            velocidade = (velocidade + 3.0 * DT * 3.6).coerceAtMost(400.0)
            d.update(DT, 1.0, 0.0, velocidade, true)
            if (d.gear == 8) amostras.add(d.engineRpm)
        }
        assertEquals("terminou em ${d.gear}ª", 8, d.gear)
        val bateu = amostras.any { it >= DrivetrainSimulator.REDLINE_RPM }
        assertTrue("nunca chegou ao corte (máx ${amostras.maxOrNull()})", bateu)
        // Bater é cair e voltar: se a rotação ficasse presa numa nota só, o som denunciaria
        // simulação. Exige-se variação de pelo menos 100 rpm depois de encostar no corte.
        val depoisDoCorte = amostras.dropWhile { it < DrivetrainSimulator.REDLINE_RPM }
        val amplitude = (depoisDoCorte.maxOrNull() ?: 0.0) - (depoisDoCorte.minOrNull() ?: 0.0)
        assertTrue("amplitude no limitador = $amplitude", amplitude > 100)
    }

    @Test
    fun `sair de D no meio da troca nao deixa a troca pendurada`() {
        val pista = Pista(simulador())
        ateComecarATrocar(pista)
        assertTrue(pista.d.emTroca)
        pista.d.update(DT, 0.0, 0.0, 40.0, false)
        assertEquals(DrivetrainSimulator.Fase.NENHUMA, pista.d.fase)
        assertEquals(DrivetrainSimulator.Troca.NENHUMA, pista.d.troca)
    }

    @Test
    fun `um tick atrasado nao estica a troca`() {
        // dt gordo (a thread do ASD já foi vista perder agendamento). A sobra tem de ser carregada
        // de fase em fase, senão cada fase arredondaria para cima e a troca ficaria mais longa
        // justamente quando o sistema está sobrecarregado.
        val pista = Pista(simulador())
        ateComecarATrocar(pista)
        var passos = 0
        while (pista.d.emTroca && passos < 100) {
            pista.passo(1.0, dt = 0.25)
            passos++
        }
        assertTrue("com dt=0,25s a troca levou $passos passos", passos <= 4)
    }

    @Test
    fun `parado e fora de D o motor responde so ao pedal`() {
        val d = simulador()
        repeat(200) { d.update(DT, 0.0, 0.0, 0.0, false) }
        val lenta = d.engineRpm
        repeat(200) { d.update(DT, 0.8, 0.0, 0.0, false) }
        assertTrue("lenta=$lenta acelerado=${d.engineRpm}", d.engineRpm > lenta + 2000)
        assertEquals(DrivetrainSimulator.Fase.NENHUMA, d.fase)
    }

    @Test
    fun `frear faz o cambio reduzir`() {
        val pista = Pista(simulador())
        repeat(3000) { pista.passo(0.5) }
        val marchaEmCruzeiro = pista.d.gear
        assertTrue("ficou em $marchaEmCruzeiro", marchaEmCruzeiro >= 3)
        // Pé fora e freio: a rotação cai e o câmbio tem de ir descendo junto.
        repeat(2000) { pista.passo(0.0, freio = 0.8) }
        assertTrue(
            "de $marchaEmCruzeiro para ${pista.d.gear} a ${pista.velocidadeKmh} km/h",
            pista.d.gear < marchaEmCruzeiro
        )
    }

    @Test
    fun `parar zera o estado de troca`() {
        val pista = Pista(simulador())
        ateComecarATrocar(pista)
        assertNotEquals(DrivetrainSimulator.Fase.NENHUMA, pista.d.fase)
        pista.d.stop()
        assertEquals(DrivetrainSimulator.Fase.NENHUMA, pista.d.fase)
        assertEquals(DrivetrainSimulator.Troca.NENHUMA, pista.d.troca)
        assertEquals(0.0, pista.d.engineRpm, 0.0)
        assertEquals("–", pista.d.gearLabel)
    }

    @Test
    fun `a carga responde mais rapido ao pisar do que ao aliviar`() {
        // Fora de D para não haver troca no meio: aqui só interessa o alisamento da carga.
        val d = simulador()
        repeat(100) { d.update(0.01, 0.0, 0.0, 0.0, false) }
        repeat(8) { d.update(0.01, 1.0, 0.0, 0.0, false) }
        val aposPisar = d.load
        assertTrue("80 ms depois de pisar a carga estava em $aposPisar", aposPisar > 0.6)

        repeat(300) { d.update(0.01, 1.0, 0.0, 0.0, false) }
        repeat(8) { d.update(0.01, 0.0, 0.0, 0.0, false) }
        // Parado, soltar leva a carga para -0,05; o quanto já andou em 80 ms é o que se compara.
        val percorridoAoAliviar = (1.0 - d.load) / (1.0 + 0.05)
        assertTrue(
            "ao pisar: $aposPisar; ao aliviar percorreu $percorridoAoAliviar",
            percorridoAoAliviar < aposPisar
        )
    }

    @Test
    fun `partida a quente nao repete a marcha lenta alta da partida a frio`() {
        val frio = DrivetrainSimulator().apply { start() }
        val quente = DrivetrainSimulator().apply { start(quente = true) }
        repeat(150) {
            frio.update(DT, 0.0, 0.0, 0.0, false)
            quente.update(DT, 0.0, 0.0, 0.0, false)
        }
        // 3 s depois: a frio ainda está bem acima da lenta; a quente já assentou.
        assertTrue("frio=${frio.engineRpm} quente=${quente.engineRpm}", frio.engineRpm > quente.engineRpm + 150)
        assertTrue("quente=${quente.engineRpm}", quente.engineRpm < 1000)
        assertTrue(!quente.coldStartActive)
    }

    @Test
    fun `leitura NaN do barramento nao envenena a rotacao`() {
        val d = simulador()
        for (i in 0 until 50) d.update(DT, 0.0, 0.0, 60.0, true)
        val antes = d.engineRpm
        assertTrue(antes.isFinite())

        // Um NaN solto no meio (velocidade, pedal e freio) e leituras boas depois.
        d.update(DT, Double.NaN, Double.NaN, Double.NaN, true)
        for (i in 0 until 200) d.update(DT, 0.0, 0.0, 60.0, true)
        assertTrue("rpm=${d.engineRpm}", d.engineRpm.isFinite())
        assertTrue(d.engineRpm > 0.0)
    }

    @Test
    fun `infinito tambem e descartado`() {
        val d = simulador()
        d.update(DT, 0.0, 0.0, Double.POSITIVE_INFINITY, true)
        d.update(DT, 0.0, 0.0, 40.0, true)
        assertTrue(d.engineRpm.isFinite())
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
