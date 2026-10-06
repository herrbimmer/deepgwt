// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.rede

/*
 * Costuras do módulo de rede (3.10c, `docs/independencia/specs/extras-rede.md`): tudo o que a
 * lógica precisa de fora (preferências, relógio, shell, log) chega por interface, para rodar em
 * teste de JVM sem Android. As ligações reais ficam em `AdaptadoresAndroid.kt`.
 */

/**
 * Quem roda comandos de sistema para o módulo. Em produção é o [br.com.deepgwt.nucleo.Shell]
 * (Shizuku); a sala suja pode trocar por outro adaptador em [LigacoesDaRede.executor].
 *
 * Devolve só a saída padrão, aparada; não devolve o código de saída. Por isso todo comando que
 * precisa de confirmação imprime um marcador ([MARCADOR_OK]). Vazio = falhou ou Shizuku ausente.
 * Bloqueia; nunca na thread principal.
 */
fun interface ExecutorDeShell {
    fun executar(comando: Array<String>): String
}

/** Ponto único de troca das ligações do módulo com o resto do app. */
object LigacoesDaRede {
    /** O executor de comandos. O padrão é o Shell nosso (Shizuku). */
    @Volatile
    @JvmStatic
    var executor: ExecutorDeShell = ExecutorDeShell { br.com.deepgwt.nucleo.Shell.executar(it) }
}

/** Impresso pelos comandos de shell do módulo quando deram certo. */
internal const val MARCADOR_OK = "DEEPGWT_REDE_OK"

/** O comando imprimiu o marcador de sucesso em alguma linha? */
internal fun temMarcador(saida: String): Boolean = saida.lineSequence().any { it.trim() == MARCADOR_OK }

/** Roda `sh -c <roteiro>`. */
internal fun ExecutorDeShell.roteiro(texto: String): String = executar(arrayOf("sh", "-c", texto))

/** O arquivo de preferências do app ("deepgwt_prefs"). Leituras nunca bloqueiam. */
internal interface ArmazemDeChaves {
    fun lerBoolean(chave: String, padrao: Boolean): Boolean
    fun lerInt(chave: String, padrao: Int): Int
    fun lerLong(chave: String, padrao: Long): Long
    fun lerString(chave: String, padrao: String): String

    fun gravarBoolean(chave: String, valor: Boolean)

    /** Grava de uma vez; valores Boolean, Int, Long ou String. */
    fun gravarVarios(valores: Map<String, Any>)
}

/** Relógio injetável. */
internal interface RelogioDaRede {
    /** Relógio de parede, ms desde 1970. */
    fun agoraEmMs(): Long

    /** Relógio monotônico do aparelho, ms (não salta com ajuste de hora). */
    fun monotonicoEmMs(): Long
}

/** Para onde vão as mensagens de diagnóstico (o logcat, no app). */
internal interface RegistroDaRede {
    fun info(mensagem: String)
    fun aviso(mensagem: String)
    fun erro(mensagem: String, causa: Throwable? = null)
}

/** As chaves de "deepgwt_prefs" que o módulo usa. O texto é o formato já gravado nos carros. */
internal object ChavesDaRede {
    const val CONTROLE_LIGADO = "mobileDataControlEnabled"
    const val CORTE_MANUAL = "blockCarMobileData"
    const val CORTE_SOB_CONSUMO = "mobileDataAutoblock"
    const val CORTE_NO_WIFI = "mobileDataBlockOnWifi"
    const val CORTE_NA_PROJECAO = "mobileDataBlockOnProjection"
    const val DIA_DO_CICLO = "mobileDataCycleDay"
    const val TETO_DE_CONSUMO_MB = "mobileDataAutoblockCapMb"
    const val CORTADO_PELO_APP = "mobileDataDisabledByApp"
    const val RESERVA_ACUMULADO = "mobileDataTrafficAccumBytes"
    const val RESERVA_ULTIMA_LEITURA = "mobileDataTrafficLastReading"
    const val RESERVA_ETIQUETA = "mobileDataTrafficCycleTag"
    const val TELEMETRIA_BLOQUEADA = "blockDatatrackTelemetry"
    const val TELEMETRIA_CONGELADA_PELO_APP = "datatrackDisabledByApp"
    const val ROTEADOR_LIGADO = "enableHotRouter"

    const val DIA_DO_CICLO_PADRAO = 1
    const val TETO_PADRAO_MB = 2048
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
