package br.com.opengw.carro

import br.com.opengw.nucleo.OpenGw
import br.com.opengw.veiculo.Veiculo
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import br.com.opengw.licenca.Portao
import br.com.opengw.licenca.Recurso
import br.com.opengw.managers.VehicleIdentity
import br.com.opengw.veiculo.Sinais
import br.com.opengw.nucleo.Preferencias
import br.com.opengw.veiculo.TravaDeMovimento
import org.json.JSONObject

/**
 * A decisão do "Liberar conector", pura (testável na JVM).
 *
 * O CONTORNO QUE OS DONOS USAM
 * No H6 PHEV o conector de recarga fica preso na tomada do carro (campanha "Cordinha Não",
 * Procon-DF). A GWM não pôs um "parar recarga" nem na central nem no app. O que solta, segundo os
 * donos: trocar a recarga de IMEDIATA para PROGRAMADA no menu da central — o carro para de
 * carregar e destrava o conector por alguns segundos. O app faz essa troca num toque e devolve o
 * modo imediato quando o conector sai (senão a próxima recarga não começaria sozinha).
 *
 * A CONFIRMAR NO CARRO: os valores de `car.ev_setting.charge_mode`. Aqui 0 = imediata e
 * 1 = programada (a convenção do resto do barramento). Qualquer outro valor: o app não mexe e
 * mostra o valor lido, para ajustar a regra. Cada tentativa fica registrada com os valores antes e
 * depois (ver [ConectorDeRecarga.registro]).
 */
object PlanoDoConector {
    const val MODO_IMEDIATO = "0"
    const val MODO_PROGRAMADO = "1"

    sealed class Decisao {
        data class Recusar(val motivo: String) : Decisao()
        data class Trocar(val de: String, val para: String) : Decisao()
    }

    /** true/false; null = o carro não informa o conector (HEV, ou barramento mudo). */
    fun conectado(gun: String?, gunAc: String?): Boolean? {
        val leituras = listOf(gun, gunAc).map { it?.trim().orEmpty() }.filter { it.isNotEmpty() }
        if (leituras.isEmpty()) return null
        return leituras.any { v -> v.toFloatOrNull()?.let { it != 0f } ?: false }
    }

    fun decidir(gun: String?, gunAc: String?, modo: String?, trava: String?, jaEsperando: Boolean): Decisao {
        if (jaEsperando) return Decisao.Recusar("Já liberado: puxe o conector. A recarga volta ao modo imediato quando ele sair.")
        if (trava != null) return Decisao.Recusar("Não liberado: $trava.")
        when (conectado(gun, gunAc)) {
            null -> return Decisao.Recusar("Este carro não informa o conector de recarga (a função é do H6 PHEV).")
            false -> return Decisao.Recusar("Nenhum conector de recarga encaixado.")
            true -> Unit
        }
        return when (val m = modo?.trim().orEmpty()) {
            MODO_IMEDIATO -> Decisao.Trocar(MODO_IMEDIATO, MODO_PROGRAMADO)
            MODO_PROGRAMADO -> Decisao.Recusar(
                "A recarga já está programada, e nesse modo o carro já deveria soltar o conector. " +
                    "Destrave as portas pela chave e puxe de novo."
            )
            "" -> Decisao.Recusar("O modo de recarga não pôde ser lido agora. Tente de novo em alguns segundos.")
            else -> Decisao.Recusar("Modo de recarga desconhecido (valor \"$m\"): nada foi mudado. Anote o valor para o suporte.")
        }
    }
}

/**
 * "Liberar conector de recarga" (Alpha 8.5): faz sozinho o contorno do [PlanoDoConector].
 *
 * Sequência: confere (conector encaixado, carro parado, modo imediato) → guarda o modo a devolver
 * → troca para programada → confere em 2 s se o carro aceitou → espera o conector sair (até 3 min)
 * → devolve o modo imediato. O modo a devolver fica gravado: se o app reiniciar no meio, ele
 * devolve na volta. Nunca deixa o carro preso no modo programado sem avisar.
 */
object ConectorDeRecarga {
    private const val TAG = "ConectorDeRecarga"

    /** Quanto tempo esperar o conector sair antes de devolver o modo mesmo assim. */
    const val ESPERA_MAXIMA_MS = 180_000L
    /** Depois que o conector sai, um respiro antes de devolver o modo. */
    private const val RESPIRO_MS = 3_000L
    private const val CONFERIR_TROCA_MS = 2_000L

    private val GUN = Sinais.CAR_EV_INFO_CHARGING_GUN_CONN_STATE.chave
    private val GUN_AC = Sinais.CAR_EV_INFO_CHARGING_GUN_AC_CONN_STATE.chave
    private val MODO = Sinais.CAR_EV_SETTING_CHARGE_MODE.chave

    /** O que vai para o registro, antes e depois (para ajustar a regra com dado do carro real). */
    private val CHAVES_DO_REGISTRO = listOf(
        MODO,
        Sinais.CAR_EV_SETTING_APPOINT_CHARGE_SET.chave,
        Sinais.CAR_EV_SETTING_CHARGE_ACTION.chave,
        Sinais.CAR_EV_INFO_CHARGING_STATE.chave,
        GUN,
        GUN_AC
    )

    data class Resultado(val ok: Boolean, val mensagem: String)

    @Volatile private var iniciada = false
    private var prazoMs: Long = 0L

    private fun prefs(): SharedPreferences =
        OpenGw.contextoProtegido.getSharedPreferences("opengw_prefs", Context.MODE_PRIVATE)

    private fun ler(chave: String): String? =
        runCatching {
            // Leitura fresca do barramento; sem ControlService (simulador, ou caído), o cache.
            (Veiculo.lerDoCarro(chave) ?: Veiculo.ler(chave))
        }.getOrNull()?.trim()

    private val ouvinte = Veiculo.Ouvinte { key, _ ->
        if (key == GUN || key == GUN_AC) FilaDoCarro.rodar { conferirEspera() }
    }

    fun iniciar() {
        if (iniciada) return
        iniciada = true
        runCatching { Veiculo.acompanhar(listOf(GUN, GUN_AC, MODO)) }
        Veiculo.observar(ouvinte)
        // Ficou um modo a devolver de antes (app reiniciado no meio): o prazo recomeça agora.
        FilaDoCarro.rodar {
            if (modoParaVoltar() != null) {
                prazoMs = SystemClock.elapsedRealtime() + ESPERA_MAXIMA_MS
                conferirEspera()
                FilaDoCarro.rodarDepois(ESPERA_MAXIMA_MS + 500) { conferirEspera() }
            }
        }
    }

    /** Mostrar o botão? Esconde só num HEV confirmado e sem conector encaixado. */
    fun disponivel(): Boolean {
        val trim = runCatching { VehicleIdentity.resolve().trim }.getOrNull()
        if (trim != "hev") return true
        return PlanoDoConector.conectado(ler(GUN), ler(GUN_AC)) == true
    }

    fun esperando(): Boolean = modoParaVoltar() != null

    private fun modoParaVoltar(): String? =
        prefs().getString(Preferencias.CONECTOR_MODO_PARA_VOLTAR.chave, null)

    /** O último registro, em texto, para a tela ("Detalhes para o suporte"). */
    fun registro(): String? = prefs().getString(Preferencias.CONECTOR_REGISTRO.chave, null)

    /**
     * Liberar. Roda na [FilaDoCarro] e entrega o resultado na thread principal. Pode ser chamado
     * da tela, de um coringa do volante ou de uma macro.
     */
    fun liberar(aoTerminar: ((Resultado) -> Unit)? = null) {
        FilaDoCarro.rodar {
            val r = if (!Portao.permite(Recurso.CONECTOR)) {
                Resultado(false, "Liberar o conector não está disponível neste nível.")
            } else {
                liberarAgora()
            }
            Log.w(TAG, "Liberar conector: ${r.mensagem}")
            if (aoTerminar != null) Handler(Looper.getMainLooper()).post { aoTerminar(r) }
        }
    }

    private fun lerTudo(): JSONObject {
        val o = JSONObject()
        CHAVES_DO_REGISTRO.forEach { k -> o.put(k, ler(k) ?: JSONObject.NULL) }
        return o
    }

    private fun registrar(etapa: String, texto: String, valores: JSONObject) {
        val atual = runCatching { JSONObject(registro() ?: "{}") }.getOrDefault(JSONObject())
        if (etapa == "pedido") atual.keys().asSequence().toList().forEach { atual.remove(it) }
        atual.put("quando", System.currentTimeMillis())
        atual.put(etapa, JSONObject().put("texto", texto).put("valores", valores))
        prefs().edit().putString(Preferencias.CONECTOR_REGISTRO.chave, atual.toString()).apply()
    }

    private fun liberarAgora(): Resultado {
        val antes = lerTudo()
        val trava = TravaDeMovimento.agora(exigirP = false, exigirLeitura = false)?.texto
        val decisao = PlanoDoConector.decidir(ler(GUN), ler(GUN_AC), ler(MODO), trava, esperando())
        if (decisao is PlanoDoConector.Decisao.Recusar) {
            registrar("pedido", "recusado: ${decisao.motivo}", antes)
            return Resultado(false, decisao.motivo)
        }
        decisao as PlanoDoConector.Decisao.Trocar
        // Grava ANTES de trocar: se o app cair aqui, a volta devolve o modo imediato.
        prefs().edit().putString(Preferencias.CONECTOR_MODO_PARA_VOLTAR.chave, decisao.de).commit()
        registrar("pedido", "troca ${decisao.de} → ${decisao.para}", antes)
        val enviado = runCatching { Veiculo.escreverConferindo(MODO, decisao.para) }.getOrDefault(false)
        if (!enviado) {
            prefs().edit().remove(Preferencias.CONECTOR_MODO_PARA_VOLTAR.chave).apply()
            registrar("resultado", "o carro não recebeu a troca", lerTudo())
            return Resultado(false, "O carro não recebeu o pedido. Tente de novo em alguns segundos.")
        }
        Thread.sleep(CONFERIR_TROCA_MS)
        val depois = lerTudo()
        val modoAgora = depois.optString(MODO)
        if (modoAgora != decisao.para) {
            // O carro não aceitou (ou o valor não é o que achamos): não fica nada pendurado.
            prefs().edit().remove(Preferencias.CONECTOR_MODO_PARA_VOLTAR.chave).apply()
            registrar("resultado", "o carro manteve o modo \"$modoAgora\"", depois)
            return Resultado(
                false,
                "O carro não aceitou a troca do modo de recarga (continua \"$modoAgora\"). Use o menu de recarga da central."
            )
        }
        registrar("troca", "modo programado aceito", depois)
        prazoMs = SystemClock.elapsedRealtime() + ESPERA_MAXIMA_MS
        FilaDoCarro.rodarDepois(ESPERA_MAXIMA_MS + 500) { conferirEspera() }
        return Resultado(
            true,
            "Pronto: puxe o conector agora. Quando ele sair, a recarga volta sozinha ao modo imediato."
        )
    }

    /** Conector saiu, ou o prazo acabou: devolve o modo guardado. */
    private fun conferirEspera() {
        val voltar = modoParaVoltar() ?: return
        val conectado = PlanoDoConector.conectado(ler(GUN), ler(GUN_AC))
        val prazoAcabou = prazoMs != 0L && SystemClock.elapsedRealtime() >= prazoMs
        if (conectado == true && !prazoAcabou) return
        if (conectado != true) Thread.sleep(RESPIRO_MS)
        val ok = runCatching { Veiculo.escreverConferindo(MODO, voltar) }.getOrDefault(false)
        prefs().edit().remove(Preferencias.CONECTOR_MODO_PARA_VOLTAR.chave).apply()
        prazoMs = 0L
        val texto = when {
            conectado != true -> "conector saiu; modo \"$voltar\" devolvido (${if (ok) "enviado" else "falhou"})"
            else -> "o conector não saiu em ${ESPERA_MAXIMA_MS / 60_000} min; modo \"$voltar\" devolvido (${if (ok) "enviado" else "falhou"})"
        }
        registrar("resultado", texto, lerTudo())
        Log.w(TAG, texto)
    }
}
