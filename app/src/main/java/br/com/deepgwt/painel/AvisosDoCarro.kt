// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.painel

import br.com.deepgwt.ponte.Cancelavel
import java.util.Locale

/**
 * As chaves de aviso do carro que o painel vigia e a regra de "valor ativo"
 * (`projetores-painel.md`, I.1, I.2 e V6). Kotlin puro.
 */
object AvisosDoCarro {

    /** As 21 chaves vigiadas (o motor já acompanha todas desde a partida). */
    @JvmField
    val VIGIADAS: List<String> = listOf(
        "car.basic.coolant_temp_warning",
        "car.basic.engine_oil_low_pressure_warning",
        "car.basic.fatigue_warning",
        "car.basic.maintenance_warning",
        "car.basic.oil_low_warning",
        "car.basic.seat_belt_warning",
        "car.basic.tirepress_warning",
        "car.basic.tiretemp_warning",
        "car.basic.tpms_warning",
        "car.ipk_info.bsd_lca_warning_reqleft",
        "car.ipk_info.bsd_lca_warning_reqright",
        "car.ipk_info.dow_warning_reqleft",
        "car.ipk_info.dow_warning_reqright",
        "car.ipk_info.fcta_warning",
        "car.ipk_info.fcw_warning",
        "car.ipk_info.warning_tts_notify",
        "car.ipk_light.door_warning",
        "car.ipk_light.engine_oil_low_pressure_warning",
        "car.ipk_light.seat_belt_warning_indicator",
        "car.ipk_light.tpms_warning",
        "car.ipk_light.fuel_low",
    )

    private val CONJUNTO_VIGIADO: Set<String> = VIGIADAS.toHashSet()

    /** Momentâneas: nunca ressemeadas do cache. */
    @JvmField
    val MOMENTANEAS: Set<String> = setOf(
        "car.ipk_info.bsd_lca_warning_reqleft",
        "car.ipk_info.bsd_lca_warning_reqright",
        "car.ipk_info.warning_tts_notify",
        "car.ipk_info.dow_warning_reqleft",
        "car.ipk_info.dow_warning_reqright",
        "car.ipk_info.fcta_warning",
        "car.ipk_info.fcw_warning",
    )

    /** Vigiadas que não acendem o selo nem abrem pop-up. */
    @JvmField
    val SEM_SELO: Set<String> = MOMENTANEAS + setOf(
        "car.ipk_light.seat_belt_warning_indicator",
        "car.basic.tirepress_warning",
        "car.basic.tiretemp_warning",
        "car.basic.maintenance_warning",
    )

    /** As 10 que acendem o selo. */
    @JvmField
    val COM_SELO: Set<String> = VIGIADAS.filterNot { it in SEM_SELO }.toCollection(LinkedHashSet())

    private val INATIVOS = setOf("0", "{0,0,0,0}", "{0,0,0,0,0}", "", "false", "null", "undefined", "unknown", "--", "nan")

    fun vigiada(chave: String): Boolean = chave in CONJUNTO_VIGIADO

    /** O valor indica aviso ligado? Sem espaços nas pontas e em minúsculas; null → não. */
    @JvmStatic
    fun ativo(valor: String?): Boolean {
        if (valor == null) return false
        return valor.trim().lowercase(Locale.ROOT) !in INATIVOS
    }
}

/**
 * O selo de aviso do carro (`projetores-painel.md`, I.3–I.8 e V7): quando acende, quando apaga,
 * dispensa pelo BACK. Kotlin puro; o relógio e a espera vêm de fora.
 *
 * Só na thread principal, exceto [avisoAtivo]/[avisoDispensado], que a ponte lê da thread do
 * WebView.
 */
class SeloDeAvisos(
    private val agora: () -> Long,
    private val esperar: (Long, () -> Unit) -> Cancelavel,
    private val lerDoCache: (String) -> String?,
    private val saida: Saida,
) {
    /** O que o selo pede ao projetor. */
    interface Saida {
        /** Um aviso com selo começou agora (o carro abre o pop-up). */
        fun popUp()

        /** O selo ou a dispensa mudou (antes do envio). */
        fun mudou(ativo: Boolean, dispensado: Boolean, motivo: String)

        /** Sempre depois de aplicar: `warningActive`/`warningDismissed` e o aviso no círculo. */
        fun enviar(ativo: Boolean, dispensado: Boolean)
    }

    companion object {
        const val ESPERA_PARA_APAGAR_MS = 2_000L
        const val DISPENSA_BLOQUEADA_MS = 2_500L
    }

    @Volatile
    var avisoAtivo: Boolean = false
        private set

    @Volatile
    var avisoDispensado: Boolean = false
        private set

    /** Instante em que o selo acendeu pela última vez. */
    var ligouEm: Long = -1L
        private set

    private val ultimoVisto = HashMap<String, String>()
    private val inicio = HashMap<String, Long>()
    private val dispensados = HashMap<String, String>()
    private var esperaDeApagar: Cancelavel? = null

    /** I.3: semeia as vigiadas não momentâneas (último visto, senão o cache, senão `0`). */
    fun semear() {
        for (chave in AvisosDoCarro.VIGIADAS) {
            if (chave in AvisosDoCarro.MOMENTANEAS) continue
            val valor = ultimoVisto[chave] ?: lerSeguro(chave) ?: "0"
            if (registrarInicio(chave, valor) && chave in AvisosDoCarro.COM_SELO) saida.popUp()
        }
    }

    /** I.5: um valor novo (já deduplicado) de uma chave vigiada. */
    fun valorNovo(chave: String, valor: String) {
        if (!AvisosDoCarro.vigiada(chave)) return
        val comecou = registrarInicio(chave, valor)
        if (comecou && chave in AvisosDoCarro.COM_SELO) {
            saida.popUp()
            dispensados.clear()
        }
        val dispensado = dispensados[chave]
        if (dispensado != null && dispensado != valor) dispensados.remove(chave)
        recalcular("valor")
    }

    /** I.6: recalcula e aplica (acender na hora; apagar com espera, salvo [semEspera]). */
    fun recalcular(motivo: String, semEspera: Boolean = false) {
        if (aMostra().isNotEmpty()) {
            cancelarEspera()
            aplicar(true, motivo)
            return
        }
        if (avisoAtivo && !semEspera) {
            cancelarEspera()
            esperaDeApagar = esperar(ESPERA_PARA_APAGAR_MS) {
                esperaDeApagar = null
                if (aMostra().isEmpty()) aplicar(false, "$motivo-espera") else aplicar(true, motivo)
            }
            return
        }
        cancelarEspera()
        aplicar(false, motivo)
    }

    /** I.8: o BACK (ou `DISMISS_WARNINGS`). */
    fun dispensar() {
        val mostra = aMostra()
        if (mostra.isEmpty()) return
        val maisNovo = inicio[mostra.first()]
        if (maisNovo != null && agora() - maisNovo < DISPENSA_BLOQUEADA_MS) return
        for (chave in mostra) dispensados[chave] = valorAtual(chave) ?: continue
        recalcular("dispensa", semEspera = true)
    }

    /** G.5: ao entrar nos cartões 1/3 a dispensa volta a falso (sem envio; D14). */
    fun esquecerDispensadoAoEntrarNoCartao() {
        avisoDispensado = false
    }

    /** Fim do projetor. */
    fun encerrar() {
        cancelarEspera()
    }

    /** As chaves à mostra, do aviso mais novo para o mais velho. */
    fun aMostra(): List<String> {
        val lista = ArrayList<String>()
        for (chave in AvisosDoCarro.COM_SELO) {
            val valor = valorAtual(chave) ?: continue
            if (!AvisosDoCarro.ativo(valor)) continue
            if (dispensados[chave] == valor) continue
            lista.add(chave)
        }
        lista.sortByDescending { inicio[it] ?: Long.MIN_VALUE }
        return lista
    }

    /** I.4: guarda o valor; diz se é o início de um aviso. */
    private fun registrarInicio(chave: String, valor: String): Boolean {
        val anterior = ultimoVisto.put(chave, valor)
        if (!AvisosDoCarro.ativo(valor)) {
            inicio.remove(chave)
            return false
        }
        if (valor != anterior || chave !in inicio) {
            inicio[chave] = agora()
            return true
        }
        return false
    }

    /** I.7. */
    private fun aplicar(ativo: Boolean, motivo: String) {
        val dispensado = !ativo && dispensados.isNotEmpty()
        val mudou = ativo != avisoAtivo || dispensado != avisoDispensado
        if (ativo && !avisoAtivo) ligouEm = agora()
        avisoAtivo = ativo
        avisoDispensado = dispensado
        if (mudou) saida.mudou(ativo, dispensado, motivo)
        saida.enviar(ativo, dispensado)
    }

    private fun valorAtual(chave: String): String? = ultimoVisto[chave] ?: lerSeguro(chave)

    private fun lerSeguro(chave: String): String? = try {
        lerDoCache(chave)
    } catch (e: Exception) {
        null
    }

    private fun cancelarEspera() {
        esperaDeApagar?.cancelar()
        esperaDeApagar = null
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
