package br.com.deepgwt.tela3d.viagens

/**
 * O gravador sem Android: junta o [MotorDeViagem], o [ArmazenamentoDeViagens], a memória dos pontos
 * ainda não gravados, o checkpoint e o texto do resumo ao vivo. Quem o usa (o [GravadorDeViagens]) chama
 * tudo da MESMA thread de trabalho; só [textoAoVivo] e [pontosEmMemoria] são para outras threads.
 */
class NucleoDoGravador(
    private val registro: Registro,
    /** Pede uma varredura de mapas/lugares daqui a tantos ms. */
    private val agendarVarreduraDeMapas: (atrasoMs: Long) -> Unit = {},
) : OuvinteDoMotor {

    companion object {
        const val INTERVALO_DO_CHECKPOINT_MS = 15_000L
        const val PONTOS_POR_LOTE = 30
        const val ATRASO_DA_VARREDURA_APOS_VIAGEM_MS = 5_000L
        private const val LIMITE_DA_MEMORIA_SEM_BANCO = 600
    }

    /** O banco; `null` enquanto não abriu (ou se falhou): então nada é gravado. */
    @Volatile
    var armazenamento: ArmazenamentoDeViagens? = null

    private val motor = MotorDeViagem(this)
    private val trava = Any()
    private val memoria = ArrayList<PontoDeTrilha>()
    private var inicioDaMemoria = 0L
    private var ultimoCheckpointMs = 0L
    private var checkpointPedido = false

    /** O resumo da viagem aberta como JSON, recalculado a cada tick; `""` sem viagem aberta. */
    @Volatile
    var textoAoVivo: String = ""
        private set

    /** Há viagem aberta (decide se o GPS fica ligado). */
    @Volatile
    var viagemAberta: Boolean = false
        private set

    /** O motor, para quem precisa consultar o estado (testes). Não mexer em outra thread. */
    val motorDeViagem: MotorDeViagem get() = motor

    /** Os pontos ainda só na memória, se [inicioMs] é a viagem a que eles pertencem. */
    fun pontosEmMemoria(inicioMs: Long): List<PontoDeTrilha> = synchronized(trava) {
        if (inicioMs == inicioDaMemoria && inicioDaMemoria != 0L) memoria.toList() else emptyList()
    }

    // --- Entradas (sempre da thread do gravador) ---

    /** Início: retoma o checkpoint e aprende o "nível parado" da última viagem gravada (C-26). */
    fun iniciar(agora: Long) {
        val a = armazenamento
        if (a != null) {
            try {
                val estado = CodecDoCheckpoint.decodificar(a.lerCheckpoint())
                if (estado.aberta) {
                    motor.restaurar(estado, agora)
                    if (motor.viagemAberta) {
                        synchronized(trava) {
                            memoria.clear()
                            inicioDaMemoria = motor.inicioDaViagemMs
                        }
                        registro("viagem ${motor.inicioDaViagemMs} retomada do checkpoint")
                    }
                }
            } catch (e: Exception) {
                registro("falha ao ler o checkpoint: ${e.message}")
            }
            try {
                a.fimDaUltimaViagem()?.let { motor.alimentarNivelParado(it.tanque, it.bateria, it.lat, it.lon, it.fimMs) }
            } catch (e: Exception) {
                registro("falha ao ler a última viagem: ${e.message}")
            }
        }
        depoisDoMotor(agora, porTempo = false, atualizarTexto = true)
    }

    fun aoSinal(chave: String, valor: String?, agora: Long) {
        motor.aoSinal(chave, valor, agora)
        if (chave == ChavesDeSinais.PRONTO) checkpointPedido = true
        depoisDoMotor(agora, porTempo = false, atualizarTexto = false)
    }

    fun aoPosicao(lat: Double, lon: Double, alt: Double, precisao: Double, agora: Long) {
        motor.aoPosicao(lat, lon, alt, precisao, agora)
        depoisDoMotor(agora, porTempo = false, atualizarTexto = false)
    }

    /** A chamada de 1 s: é aqui que o texto do resumo ao vivo é recalculado. */
    fun tick(agora: Long, intervaloMonotonicoMs: Long) {
        motor.tick(agora, intervaloMonotonicoMs)
        depoisDoMotor(agora, porTempo = true, atualizarTexto = true)
    }

    /** FINALIZAR VIAGEM (C-9). */
    fun finalizarViagem(agora: Long) {
        motor.finalizarAgora(agora)
        checkpointPedido = true
        depoisDoMotor(agora, porTempo = false, atualizarTexto = true)
    }

    /** Pedido de checkpoint "logo" (a Activity pausou). */
    fun salvarLogo(agora: Long) {
        if (motor.viagemAberta) gravarCheckpoint(agora)
    }

    private fun depoisDoMotor(agora: Long, porTempo: Boolean, atualizarTexto: Boolean) {
        viagemAberta = motor.viagemAberta
        if (atualizarTexto) textoAoVivo = motor.resumoAoVivo(agora)?.let { JsonDeViagens.aoVivo(it) } ?: ""
        if (!motor.viagemAberta) {
            checkpointPedido = false
            return
        }
        val devido = porTempo && (agora - ultimoCheckpointMs >= INTERVALO_DO_CHECKPOINT_MS || agora < ultimoCheckpointMs)
        if (checkpointPedido || devido) gravarCheckpoint(agora)
    }

    // --- Eventos do motor ---

    override fun aoEvento(evento: EventoDoMotor) {
        when (evento) {
            is EventoDoMotor.Abriu -> {
                synchronized(trava) {
                    memoria.clear()
                    inicioDaMemoria = evento.inicioMs
                }
                checkpointPedido = true
                registro("viagem ${evento.inicioMs} aberta")
            }
            is EventoDoMotor.Ponto -> guardarPonto(evento.ponto)
            is EventoDoMotor.Parada -> gravarParada(evento)
            is EventoDoMotor.Fechou -> fecharGravando(evento.resumo)
            is EventoDoMotor.Descartou -> descartar(evento.resumo)
        }
    }

    private fun guardarPonto(ponto: PontoDeTrilha) {
        val cheio = synchronized(trava) {
            memoria.add(ponto)
            if (armazenamento == null && memoria.size > LIMITE_DA_MEMORIA_SEM_BANCO) memoria.removeAt(0)
            memoria.size >= PONTOS_POR_LOTE
        }
        if (cheio) descarregarPontos()
    }

    /** Grava no banco os pontos pendentes (em lote) e tira da memória os que foram. */
    private fun descarregarPontos() {
        val a = armazenamento ?: return
        val inicio: Long
        val lote: List<PontoDeTrilha>
        synchronized(trava) {
            if (memoria.isEmpty()) return
            inicio = inicioDaMemoria
            lote = memoria.toList()
        }
        try {
            a.gravarPontos(inicio, lote)
            synchronized(trava) {
                // Só chegaram pontos novos no fim: os do lote são os primeiros.
                repeat(lote.size) { if (memoria.isNotEmpty()) memoria.removeAt(0) }
            }
        } catch (e: Exception) {
            registro("falha ao gravar pontos: ${e.message}")
        }
    }

    private fun gravarCheckpoint(agora: Long) {
        val a = armazenamento ?: return
        descarregarPontos()
        try {
            a.gravarCheckpoint(CodecDoCheckpoint.codificar(motor.estado), agora)
            ultimoCheckpointMs = agora
            checkpointPedido = false
        } catch (e: Exception) {
            registro("falha ao gravar o checkpoint: ${e.message}")
        }
    }

    private fun gravarParada(evento: EventoDoMotor.Parada) {
        val p = evento.parada
        registro("parada ${p.tipo.texto}: ${p.antes} -> ${p.depois}")
        try {
            armazenamento?.gravarParada(evento.inicioMs, p)
        } catch (e: Exception) {
            registro("falha ao gravar a parada: ${e.message}")
        }
    }

    private fun fecharGravando(resumo: ResumoDaViagem) {
        descarregarPontos()
        val a = armazenamento
        if (a != null) {
            var gravou = false
            try {
                a.gravarViagem(resumo)
                gravou = true
            } catch (e: Exception) {
                registro("falha ao gravar a viagem ${resumo.inicioMs}: ${e.message}")
            }
            if (gravou) {
                try {
                    a.gravarRota(resumo.inicioMs)
                } catch (e: Exception) {
                    registro("falha ao gravar a rota: ${e.message}")
                }
                try {
                    a.apagarCheckpoint()
                } catch (e: Exception) {
                    registro("falha ao apagar o checkpoint: ${e.message}")
                }
            }
        }
        limparMemoria()
        registro("viagem ${resumo.inicioMs} gravada: ${resumo.km} km")
        agendarVarreduraDeMapas(ATRASO_DA_VARREDURA_APOS_VIAGEM_MS)
    }

    private fun descartar(resumo: ResumoDaViagem) {
        limparMemoria()
        try {
            armazenamento?.apagarPontos(resumo.inicioMs)
            armazenamento?.apagarCheckpoint()
        } catch (e: Exception) {
            registro("falha ao descartar a viagem: ${e.message}")
        }
        registro("viagem ${resumo.inicioMs} descartada (${resumo.km} km)")
    }

    private fun limparMemoria() {
        synchronized(trava) {
            memoria.clear()
            inicioDaMemoria = 0L
        }
    }
}
