package br.com.opengw.app

import java.util.concurrent.atomic.AtomicBoolean

/**
 * O trabalho do serviço principal (`telas-e-servico-servico.md` §3, casos C–F), sem Android: o
 * [ServicoPrincipal] só traduz os eventos do sistema para cá. Testado na JVM.
 *
 * Uma instância por vida do serviço (onCreate → onDestroy). O que vale para o processo inteiro (o
 * serviço "de pé", o aviso da central já registrado) fica em [EstadoDoProcesso].
 */
internal class NucleoDoServico(
    private val estado: EstadoDoProcesso,
    private val portas: PortasDoServico,
    private val ganchos: GanchosDoServico,
    private val linha: LinhaDeFundo,
) {

    /** O que devolver ao `onStartCommand`. */
    enum class Resposta { CONTINUAR, NAO_CONTINUAR }

    /** D1: a sequência de §E é postada uma vez por vida do serviço. */
    private val partidaPostada = AtomicBoolean(false)

    private val ouvinte = object : PartidaDoShizuku.Ouvinte {
        override fun aceito() = aoAceitar()
        override fun pedirReinicio(motivo: String) = aoPedirReinicio(motivo)
        override fun desistir(motivo: String) = aoDesistir(motivo)
    }

    /** C2 (a parte que não é do Android). */
    fun aoCriar() {
        portas.diario("foreground_service_on_create", "created=true")
    }

    /** C4, C5, C6 e C7. Thread principal. */
    fun aoPedirInicio(flags: Int, startId: Int): Resposta {
        if (portas.simulador) return partidaSimulada()

        if (!estado.dePe.compareAndSet(false, true)) {
            // C4: pedido repetido (partida morna, receptor de partida depois da Application...).
            portas.diario("foreground_service_already_running", "flags=$flags startId=$startId")
            protegido("aoIniciarDeNovo") { ganchos.aoIniciarDeNovo() }
            protegido("conferir do Shizuku") { ganchos.conferirShizuku() }
            return Resposta.CONTINUAR
        }

        estado.nucleoAtual = this
        return try {
            portas.diario("foreground_service_started", "flags=$flags startId=$startId")
            linha.esvaziar()
            portas.entrarEmPrimeiroPlano(simulado = false)
            protegido("aoIniciar") { ganchos.aoIniciar() }
            protegido("reset pendente") { resetPendente() }
            portas.marcarInicioNaLinhaDoTempo()
            ganchos.iniciarShizuku(ouvinte)
            Resposta.CONTINUAR
        } catch (e: Throwable) {
            // C6.
            portas.erro("primeira partida do serviço falhou", e)
            estado.dePe.set(false)
            portas.pararOServico()
            Resposta.NAO_CONTINUAR
        }
    }

    /** C7: simulador. Sem Shizuku, sem ganchos de primeira partida, nada no pedido repetido. */
    private fun partidaSimulada(): Resposta {
        if (!estado.dePe.compareAndSet(false, true)) return Resposta.CONTINUAR
        estado.nucleoAtual = this
        try {
            portas.entrarEmPrimeiroPlano(simulado = true)
        } catch (e: Throwable) {
            portas.erro("primeiro plano do simulador falhou", e)
            estado.dePe.set(false)
            portas.pararOServico()
            return Resposta.NAO_CONTINUAR
        }
        protegido("partida simulada") { ganchos.partirSimulado() }
        protegido("depoisDaPartidaSimulada") { ganchos.depoisDaPartidaSimulada() }
        return Resposta.CONTINUAR
    }

    /** C9, comportamento recomendado (decisão 3): apaga só a chave; nunca limpa as preferências. */
    private fun resetPendente() {
        val versao = portas.lerResetPendente()
        if (versao.isEmpty()) return
        portas.apagarResetPendente()
        portas.info("reset pendente encontrado (versão '$versao'); só a marca foi apagada")
    }

    /** O ouvinte vale só enquanto esta vida do serviço está de pé. */
    private fun vivo(): Boolean = estado.dePe.get() && estado.nucleoAtual === this

    /** D1. */
    private fun aoAceitar() {
        if (!vivo()) {
            portas.info("Shizuku aceito com o serviço parado: ignorado")
            return
        }
        if (!partidaPostada.compareAndSet(false, true)) {
            portas.info("Shizuku aceito de novo na mesma vida do serviço: ignorado")
            return
        }
        linha.postar { sequenciaDaPartida() }
    }

    /** D2. */
    private fun aoPedirReinicio(motivo: String) {
        if (!vivo()) {
            portas.info("pedido de reinício com o serviço parado ($motivo): ignorado")
            return
        }
        reiniciar(motivo)
    }

    /** D3. */
    private fun aoDesistir(motivo: String) {
        if (!vivo()) return
        portas.erro("o Shizuku não sobe nesta instalação: serviço parado sem reinício ($motivo)", null)
        portas.diario("foreground_service_gave_up", motivo)
        estado.dePe.set(false)
        protegido("sair do primeiro plano") { portas.sairDoPrimeiroPlano() }
        portas.pararOServico()
    }

    /** §E, na linha de fundo. */
    private fun sequenciaDaPartida() {
        protegido("comShizuku") { ganchos.comShizuku() }
        protegido("extras.comShizuku") { ganchos.extrasComShizuku() }
        protegido("antesDaPartida") { ganchos.antesDaPartida() }
        val partiu = try {
            ganchos.partirMotor()
        } catch (e: Throwable) {
            portas.erro("partida do motor lançou", e)
            false
        }
        if (!partiu) {
            portas.erro("partida do motor falhou", null)
            reiniciar(MOTIVO_PARTIDA_FALHOU)
            return
        }
        protegido("depoisDaPartida") { ganchos.depoisDaPartida() }
        protegido("extras.depoisDaPartida") { ganchos.extrasDepoisDaPartida() }
        registrarAvisoDaCentralUmaVez()
    }

    /** E7: uma vez por processo. */
    private fun registrarAvisoDaCentralUmaVez() {
        if (!estado.avisoDaCentralRegistrado.compareAndSet(false, true)) return
        try {
            portas.registrarAvisoDaCentral()
        } catch (e: Throwable) {
            estado.avisoDaCentralRegistrado.set(false)
            portas.erro("não deu para registrar o aviso de que a central reiniciou", e)
        }
    }

    fun registrarAvisoIgnorado() {
        portas.info("aviso da central repetido em menos de um minuto: ignorado")
    }

    /** E7, aviso recebido com este serviço de pé. */
    private fun aoAvisoDaCentral() {
        portas.diario("init_completed_restart", "a central avisou que reiniciou")
        reiniciar(MOTIVO_AVISO_DA_CENTRAL)
    }

    /** F2. */
    private fun reiniciar(motivo: String) {
        portas.info("reinício do serviço: $motivo")
        protegido("linha do tempo") { portas.marcarReinicioNaLinhaDoTempo(motivo) }
        estado.dePe.set(false)
        protegido("encerrar do Shizuku") { ganchos.encerrarShizuku() }
        protegido("agendar reinício") { portas.agendarReinicio() }
        portas.pararOServico()
    }

    /** F1. Thread principal. A ordem é obrigatória: o gancho antes do motor. */
    fun aoDestruir() {
        protegido("aoDestruir") { ganchos.aoDestruir() }
        protegido("encerrar o motor") { ganchos.encerrarMotor() }
        if (!portas.simulador) protegido("encerrar do Shizuku") { ganchos.encerrarShizuku() }
        protegido("encerrar a linha de fundo") { linha.encerrar() }
        if (estado.nucleoAtual === this) {
            estado.dePe.set(false)
            estado.nucleoAtual = null
        }
    }

    /** C8: exceção de um passo é registrada e não impede o seguinte. */
    private inline fun protegido(passo: String, bloco: () -> Unit) {
        try {
            bloco()
        } catch (e: Throwable) {
            portas.erro("serviço: '$passo' falhou", e)
        }
    }

    companion object {
        const val MOTIVO_PARTIDA_FALHOU = "partida_do_motor_falhou"
        const val MOTIVO_AVISO_DA_CENTRAL = "init_completed"

        /**
         * E7, ao receber o aviso (o receptor é do processo, não de uma vida do serviço): de pé →
         * reinício; parado → pedir o serviço.
         */
        fun tratarAvisoDaCentral(
            estado: EstadoDoProcesso,
            agora: Long = System.nanoTime() / 1_000_000L,
            pedirOServico: () -> Unit,
        ) {
            val atual = estado.nucleoAtual
            if (estado.dePe.get() && atual != null) {
                // O receptor é exportado: qualquer app poderia mandar o aviso em laço e reiniciar o
                // serviço sem parar. Um reinício por aviso a cada [INTERVALO_MINIMO_ENTRE_AVISOS_MS].
                if (!estado.aceitaAviso(agora)) {
                    atual.registrarAvisoIgnorado()
                    return
                }
                atual.aoAvisoDaCentral()
            } else {
                pedirOServico()
            }
        }

        const val INTERVALO_MINIMO_ENTRE_AVISOS_MS = 60_000L
    }
}

/** O que vale para o processo inteiro, entre uma vida e outra do serviço. */
internal class EstadoDoProcesso {
    val dePe = AtomicBoolean(false)
    val avisoDaCentralRegistrado = AtomicBoolean(false)

    @Volatile
    var nucleoAtual: NucleoDoServico? = null

    private var ultimoAvisoAceito = Long.MIN_VALUE / 2

    /** Um aviso da central só vale se o anterior foi há mais de [NucleoDoServico.INTERVALO_MINIMO_ENTRE_AVISOS_MS]. */
    @Synchronized
    fun aceitaAviso(agora: Long): Boolean {
        if (agora - ultimoAvisoAceito < NucleoDoServico.INTERVALO_MINIMO_ENTRE_AVISOS_MS) return false
        ultimoAvisoAceito = agora
        return true
    }
}

/** Uma thread com fila; tarefas em ordem. */
internal interface LinhaDeFundo {
    fun postar(tarefa: () -> Unit)

    /** Descarta as tarefas ainda na fila. */
    fun esvaziar()

    /** Para a thread; as pendentes são descartadas. */
    fun encerrar()
}

/** O Android e a linha do tempo, vistos pelo núcleo. */
internal interface PortasDoServico {
    val simulador: Boolean

    fun diario(evento: String, mensagem: String)
    fun info(mensagem: String)
    fun erro(mensagem: String, causa: Throwable?)

    fun entrarEmPrimeiroPlano(simulado: Boolean)
    fun sairDoPrimeiroPlano()
    fun pararOServico()
    fun agendarReinicio()

    fun marcarInicioNaLinhaDoTempo()
    fun marcarReinicioNaLinhaDoTempo(motivo: String)

    /** Registra o receptor de `INIT_COMPLETED` no contexto do app. O núcleo garante uma vez por processo. */
    fun registrarAvisoDaCentral()

    /** `pendingResetTargetVersion`, "" se ausente. */
    fun lerResetPendente(): String
    fun apagarResetPendente()
}

/** As ligações do app e o Shizuku, já presos à instância do serviço. */
internal interface GanchosDoServico {
    fun aoIniciar()
    fun aoIniciarDeNovo()
    fun comShizuku()
    fun extrasComShizuku()
    fun antesDaPartida()
    fun partirMotor(): Boolean
    fun depoisDaPartida()
    fun extrasDepoisDaPartida()
    fun partirSimulado()
    fun depoisDaPartidaSimulada()
    fun aoDestruir()
    fun encerrarMotor()

    fun iniciarShizuku(ouvinte: PartidaDoShizuku.Ouvinte)
    fun conferirShizuku()
    fun encerrarShizuku()
}
