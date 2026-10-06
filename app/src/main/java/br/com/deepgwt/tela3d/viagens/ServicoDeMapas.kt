package br.com.deepgwt.tela3d.viagens

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** Quem consulta o nome do lugar. `null` = falha de rede/erro. */
fun interface ConsultaDeLugar {
    fun consultar(lat: Double, lon: Double): GeocodificadorReverso.Lugar?
}

/**
 * Uma varredura de mapas e lugares (C-35): grava as rotas que faltam, depois trata até 5 viagens
 * pendentes (da mais nova para a mais velha). Falha de rede interrompe a varredura sem gastar tentativa;
 * falha inesperada ao desenhar o mapa soma uma tentativa (no máximo 5 por viagem).
 */
class VarreduraDeMapas(
    private val armazenamento: ArmazenamentoDeViagens,
    private val gerador: GeradorDeMapa,
    private val lugares: ConsultaDeLugar,
    private val registro: Registro = {},
) {
    companion object {
        const val ROTAS_POR_VARREDURA = 20
        const val VIAGENS_POR_VARREDURA = 5
        const val POSICOES_DO_MAPA = 800
    }

    fun executar() {
        gravarRotasQueFaltam()
        val pendentes = armazenamento.viagensPendentes(VIAGENS_POR_VARREDURA)
        for (viagem in pendentes) {
            if (viagem.caminhoDoMapa == null && !tratarMapa(viagem)) return
            if (viagem.lugarInicial == null && !tratarLugares(viagem)) return
        }
    }

    private fun gravarRotasQueFaltam() {
        for (inicio in armazenamento.viagensSemRota(ROTAS_POR_VARREDURA)) {
            try {
                armazenamento.gravarRota(inicio)
            } catch (e: Exception) {
                registro("rota de $inicio falhou: ${e.message}")
            }
        }
    }

    /** `false` = a varredura deve parar (sem rede). */
    private fun tratarMapa(viagem: ViagemPendente): Boolean {
        val posicoes = armazenamento.posicoesParaMapa(viagem.inicioMs, POSICOES_DO_MAPA)
        val quadro = if (posicoes.size < 2) null else ProjecaoDoMapa.enquadrar(posicoes)
        if (quadro == null) {
            armazenamento.marcarSemMapa(viagem.inicioMs)
            return true
        }
        return try {
            when (val resultado = gerador.gerar(viagem.inicioMs, quadro)) {
                is ResultadoDoMapa.Gerado -> {
                    armazenamento.gravarMapa(viagem.inicioMs, resultado.nomeDoArquivo, quadro.texto)
                    true
                }
                ResultadoDoMapa.SemRede -> {
                    registro("sem rede para o mapa de ${viagem.inicioMs}")
                    false
                }
            }
        } catch (e: Exception) {
            registro("mapa de ${viagem.inicioMs} falhou: ${e.message}")
            armazenamento.somarTentativaDeMapa(viagem.inicioMs)
            true
        }
    }

    /** `false` = a varredura deve parar (consulta falhou). */
    private fun tratarLugares(viagem: ViagemPendente): Boolean {
        val semCoordenadas = !viagem.latInicial.isFinite() || !viagem.lonInicial.isFinite() ||
            !viagem.latFinal.isFinite() || !viagem.lonFinal.isFinite()
        if (semCoordenadas) {
            armazenamento.gravarLugares(viagem.inicioMs, "", "")
            return true
        }
        val partida = lugares.consultar(viagem.latInicial, viagem.lonInicial)
        if (partida == null) {
            registro("sem resposta do geocodificador para a partida de ${viagem.inicioMs}")
            return false
        }
        val chegada = lugares.consultar(viagem.latFinal, viagem.lonFinal)
        if (chegada == null) {
            registro("sem resposta do geocodificador para a chegada de ${viagem.inicioMs}")
            return false
        }
        armazenamento.gravarLugares(viagem.inicioMs, partida.rotulo, chegada.rotulo)
        return true
    }
}

/**
 * O serviço de mapas: roda a [VarreduraDeMapas] numa thread própria, a primeira 20 s depois do início,
 * depois a cada 10 min e 5 s depois de cada viagem gravada. Nunca duas ao mesmo tempo; um novo pedido
 * só vale se for mais cedo que o já marcado.
 */
class ServicoDeMapas(
    private val varredura: () -> Unit,
    private val registro: Registro = {},
    private val relogioMs: () -> Long = System::currentTimeMillis,
) {
    companion object {
        const val PRIMEIRA_VARREDURA_MS = 20_000L
        const val INTERVALO_ENTRE_VARREDURAS_MS = 600_000L
    }

    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { corpo ->
        Thread(corpo, "deepgwt-mapas").apply { isDaemon = true }
    }
    private var marcada: ScheduledFuture<*>? = null
    private var devidoEmMs = Long.MAX_VALUE

    fun iniciar() = agendar(PRIMEIRA_VARREDURA_MS)

    /** Pede uma varredura daqui a [atrasoMs] (vale se for mais cedo que a já marcada). */
    @Synchronized
    fun agendar(atrasoMs: Long) {
        val devido = relogioMs() + atrasoMs
        val atual = marcada
        if (atual != null && !atual.isDone && devidoEmMs <= devido) return
        atual?.cancel(false)
        devidoEmMs = devido
        marcada = try {
            executor.schedule({ rodar() }, atrasoMs, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            null
        }
    }

    private fun rodar() {
        synchronized(this) { devidoEmMs = Long.MAX_VALUE }
        try {
            varredura()
        } catch (e: Exception) {
            registro("a varredura de mapas falhou: ${e.message}")
        } finally {
            agendar(INTERVALO_ENTRE_VARREDURAS_MS)
        }
    }

    @Synchronized
    fun encerrar() {
        marcada?.cancel(false)
        marcada = null
        executor.shutdownNow()
    }
}
