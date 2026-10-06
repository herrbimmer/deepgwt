package br.com.deepgwt.tela3d.ponte

import android.webkit.JavascriptInterface

/**
 * O objeto `MediaBridge` da página (5 métodos): transporte da mídia, fechar o slot da direita e o aviso
 * de "a página terminou de carregar o modelo".
 */
class PonteDaMidia(
    private val midia: MidiaParaAPonte,
    private val janelas: JanelasParaAPonte,
    private val acoes: AcoesDaTela,
    private val executor: ExecutorDaUi,
    private val diario: DiarioDaPonte,
) {
    @JavascriptInterface
    fun dismissSlot() = naUi("dismissSlot") {
        if (janelas.estadoDoShell().direitoOcupado) janelas.fecharSlotDaDireita()
    }

    @JavascriptInterface
    fun next() = naUi("next") {
        diario.info("mídia: próxima")
        midia.proxima()
    }

    @JavascriptInterface
    fun prev() = naUi("prev") {
        diario.info("mídia: anterior")
        midia.anterior()
    }

    @JavascriptInterface
    fun playPause() = naUi("playPause") {
        diario.info("mídia: tocar/pausar")
        midia.tocarOuPausar()
    }

    /** A página terminou de carregar o modelo: liga o motor de mídia e depois pede a localização. */
    @JavascriptInterface
    fun onViewerReady() = naUi("onViewerReady") {
        try {
            midia.iniciar()
            midia.empurrarAgora()
        } catch (e: Exception) {
            diario.erro("onViewerReady: motor de mídia falhou", e)
        }
        acoes.pedirPermissaoDeLocalizacao()
    }

    private inline fun naUi(nome: String, crossinline acao: () -> Unit) {
        try {
            executor.postar {
                try {
                    acao()
                } catch (e: Exception) {
                    diario.erro("$nome falhou", e)
                }
            }
        } catch (e: Exception) {
            diario.erro("$nome falhou", e)
        }
    }

    companion object {
        const val NOME_NA_PAGINA = "MediaBridge"
    }
}

/**
 * O objeto `TelemetryBridge` da página (4 métodos). Os dois que escrevem no carro só deixam passar o que
 * está nas listas fechadas do [ValidadorDeComandos]; recusa é registrada e a página não é avisada.
 */
class PonteDaTelemetria(
    private val cache: CacheDeTelemetria,
    private val enviador: EnviadorDeComandos,
    private val executor: ExecutorDaUi,
    private val diario: DiarioDaPonte,
    /** Só no build debug o `reportPerf` registra; em release não faz nada. */
    private val depuracao: Boolean,
) {
    @JavascriptInterface
    fun getCarData(chave: String?): String = try {
        cache.valorDe(chave)
    } catch (e: Exception) {
        ""
    }

    @JavascriptInterface
    fun invokeVehicleCommand(comando: String?, valor: String?) {
        try {
            when (val resultado = ValidadorDeComandos.comandoDoVeiculo(comando, valor)) {
                is Validacao.Recusada -> diario.aviso("comando recusado: ${resultado.motivo}")
                is Validacao.Aceita -> executor.postar {
                    try {
                        enviador.enviarComando(resultado.nome, resultado.valor)
                    } catch (e: Exception) {
                        diario.erro("envio do comando falhou", e)
                    }
                }
            }
        } catch (e: Exception) {
            diario.erro("invokeVehicleCommand falhou", e)
        }
    }

    @JavascriptInterface
    fun setCarData(chave: String?, valor: String?) {
        try {
            when (val resultado = ValidadorDeComandos.escritaDeChave(chave, valor)) {
                is Validacao.Recusada -> diario.aviso("escrita recusada: ${resultado.motivo}")
                is Validacao.Aceita -> executor.postar {
                    try {
                        enviador.escreverChave(resultado.nome, resultado.valor)
                    } catch (e: Exception) {
                        diario.erro("envio da escrita falhou", e)
                    }
                }
            }
        } catch (e: Exception) {
            diario.erro("setCarData falhou", e)
        }
    }

    @JavascriptInterface
    fun reportPerf(json: String?) {
        if (!depuracao) return
        try {
            diario.info("perf $json")
        } catch (e: Exception) {
            // diagnóstico: nada a fazer
        }
    }

    companion object {
        const val NOME_NA_PAGINA = "TelemetryBridge"
    }
}

/**
 * O objeto `TripBridge` da página (10 métodos): só encaminha ao gravador de viagens, na thread da
 * ponte, com os limites da especificação. Chave de viagem que não é número devolve o valor neutro
 * (`"null"`, `"{}"` ou `""`), nunca uma exceção.
 */
class PonteDeViagens(
    private val viagens: ViagensParaAPonte,
    private val diario: DiarioDaPonte = DiarioDaPonte.SILENCIOSO,
) {
    @JavascriptInterface
    fun finishTrip() = segura("finishTrip", Unit) { viagens.encerrarViagem() }

    @JavascriptInterface
    fun getDays(de: String?, ate: String?): String =
        segura("getDays", "[]") { viagens.dias(de.orEmpty(), ate.orEmpty()) }

    @JavascriptInterface
    fun getLiveTrip(): String = segura("getLiveTrip", "{}") { viagens.aoVivo() }

    @JavascriptInterface
    fun getMonths(): String = segura("getMonths", "[]") { viagens.meses() }

    @JavascriptInterface
    fun getStopTotals(deMs: String?): String {
        val de = Normalizadores.chaveDeViagem(deMs) ?: return "{}"
        return segura("getStopTotals", "{}") { viagens.totaisDeParadas(de) }
    }

    @JavascriptInterface
    fun getTotals(): String = segura("getTotals", "{}") { viagens.totais() }

    @JavascriptInterface
    fun getTrip(idMs: String?, maxPontos: Int): String {
        val id = Normalizadores.chaveDeViagem(idMs) ?: return "null"
        val limite = Normalizadores.limiteDePontos(maxPontos)
        return segura("getTrip", "null") { viagens.viagem(id, limite) }
    }

    @JavascriptInterface
    fun getTripMap(idMs: String?): String {
        val id = Normalizadores.chaveDeViagem(idMs) ?: return ""
        return segura("getTripMap", "") { viagens.mapaDaViagem(id) }
    }

    @JavascriptInterface
    fun getTripPointsSince(inicioMs: String?, tMs: String?, maxPontos: Int): String {
        val inicio = Normalizadores.chaveDeViagem(inicioMs) ?: return "null"
        val t = Normalizadores.chaveDeViagem(tMs) ?: return "null"
        val limite = Normalizadores.limiteDePontos(maxPontos)
        return segura("getTripPointsSince", "null") { viagens.pontosDesde(inicio, t, limite) }
    }

    @JavascriptInterface
    fun getTrips(offset: Int, limite: Int): String = segura("getTrips", "[]") { viagens.lista(offset, limite) }

    private inline fun <T> segura(nome: String, padrao: T, acao: () -> T): T = try {
        acao()
    } catch (e: Exception) {
        diario.erro("$nome falhou", e)
        padrao
    }

    companion object {
        const val NOME_NA_PAGINA = "TripBridge"
    }
}
