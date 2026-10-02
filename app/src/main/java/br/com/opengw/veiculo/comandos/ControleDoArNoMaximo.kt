package br.com.opengw.veiculo.comandos

import br.com.opengw.nucleo.Preferencias
import br.com.opengw.veiculo.motor.Agenda
import br.com.opengw.veiculo.motor.RegistroDoMotor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Ar-condicionado no máximo (motor-comandos.md §J): liga com a cabine quente quando o READY liga,
 * suaviza perto do alvo e volta ao estado de antes. Toda a lógica roda na linha do motor; as entradas
 * públicas só enfileiram e voltam na hora (J.1, J.6).
 */
internal class ControleDoArNoMaximo(
    private val carro: CarroDoMotor,
    private val prefs: PreferenciasDoMotor,
    private val agenda: Agenda,
    private val registro: RegistroDoMotor,
    private val diario: DiarioDoResto,
) {

    /** Publicação de "ligou/desligou" (barramento do painel, 3.3d). */
    @Volatile var aviso: AvisoDoArNoMaximo = AvisoDoArNoMaximo { }

    /** J.1: lido de qualquer thread. */
    @Volatile var ativo: Boolean = false
        private set

    /** Estado de antes do máximo (chave → texto, inclusive nulos). Só mexido na linha. */
    private val anterior = LinkedHashMap<String, String?>()

    private val agendados = Agendados(agenda)

    /** J.2: o READY ligou (as automações já conferiram a preferência). */
    fun ligarSeQuente() {
        enfileirar("ligar") { tentarLigar(0) }
    }

    /** J.4: chegou um evento de `car.basic.inside_temp`. */
    fun aoMudarTemperaturaInterna() {
        enfileirar("suavizar") { suavizarNaLinha() }
    }

    /** J.5/J.6: volta ao estado de antes; qualquer thread, volta na hora. */
    fun sair() {
        enfileirar("sair") { sairNaLinha("pedido") }
    }

    /** Re-partida (motor-sistema.md G.8): perde as novas tentativas e o tempo-limite. */
    fun cancelarAgendados() {
        agendados.cancelarTodos()
    }

    // ── J.2 ──

    private fun tentarLigar(tentativa: Int) {
        val crua = carro.lerDoCarro(TEMPERATURA_INTERNA) ?: return
        val t = numeroDoCarro(crua)
        if (t == null) {
            registro.aviso("ar no máximo: temperatura interna ilegível '$crua'")
            return
        }
        if (t >= LEITURA_INVALIDA_ACIMA || t <= LEITURA_INVALIDA_ABAIXO) {
            if (tentativa < NOVAS_TENTATIVAS) {
                agendados.agendar("tentativa", ESPERA_ENTRE_TENTATIVAS_MS) { tentarLigar(tentativa + 1) }
            } else {
                registro.aviso("ar no máximo: temperatura inválida ($t) depois de $NOVAS_TENTATIVAS novas tentativas; desisto")
            }
            return
        }
        val limiar = prefs.decimal(Preferencias.MAX_AC_ON_UNLOCK_THRESHOLD.chave, LIMIAR_PADRAO)
        if (t < limiar || ativo) return
        ligar(t)
    }

    private fun ligar(temperatura: Double) {
        recuperarEstadoSalvo()
        if (anterior.isEmpty()) {
            for (chave in CHAVES_DE_ANTES) anterior[chave] = carro.lerDoCarro(chave)
            salvarEstado()
        }
        for ((chave, valor) in ESCRITAS_AO_LIGAR) escrever(chave, valor)
        ativo = true
        avisar(true)
        anotar("ar no máximo ligado", mapOf("temperatura" to temperatura, "anterior" to HashMap(anterior)))
        val minutos = prefs.inteiro(Preferencias.MAX_AC_TIMEOUT.chave, 0)
        if (minutos > 0) {
            agendados.agendar("tempoLimite", minutos * 60_000L) { sairNaLinha("tempo-limite de $minutos min") }
        } else {
            agendados.cancelar("tempoLimite")
        }
    }

    /** J.2.3.1: o app morreu no meio de um máximo; o "antes" verdadeiro é o salvo. */
    private fun recuperarEstadoSalvo() {
        if (!prefs.booleano(SALVO_ATIVO, false)) return
        val texto = prefs.texto(SALVO_ANTERIOR) ?: return
        val mapa = try {
            lerJson(texto)
        } catch (e: Exception) {
            registro.aviso("ar no máximo: estado salvo corrompido; apagado", e)
            prefs.apagar(SALVO_ATIVO, SALVO_ANTERIOR)
            return
        }
        anterior.clear()
        anterior.putAll(mapa)
        ativo = true
        registro.info("ar no máximo: retomado o estado salvo $mapa")
    }

    private fun salvarEstado() {
        try {
            val json = JsonObject(anterior.filterValues { it != null }.mapValues { JsonPrimitive(it.value) })
            prefs.gravarTexto(SALVO_ANTERIOR, json.toString())
            prefs.gravarBooleano(SALVO_ATIVO, true)
        } catch (e: Exception) {
            registro.aviso("ar no máximo: não deu para salvar o estado de antes", e)
        }
    }

    // ── J.4 ──

    private fun suavizarNaLinha() {
        if (!ativo) return
        val t = numeroDoCarro(carro.lerDoCarro(TEMPERATURA_INTERNA) ?: return) ?: return
        val alvo = prefs.decimal(Preferencias.MAX_AC_TARGET_TEMP.chave, ALVO_PADRAO).toDouble()
        if (t <= alvo) {
            registro.info("ar no máximo: interna $t ≤ alvo $alvo; volta ao de antes")
            sairNaLinha("alvo alcançado")
            return
        }
        if (t >= alvo + FAIXA_DE_SUAVIZACAO) return
        val f = ((t - alvo) / FAIXA_DE_SUAVIZACAO).coerceIn(0.0, 1.0)

        passo("ventilador") {
            val antes = anterior[VENTILADOR]?.let { it.trim().toIntOrNull() ?: error("ventilador anterior ilegível '$it'") }
                ?: VENTILADOR_PADRAO
            escrever(VENTILADOR, ventiladorSuavizado(antes, f).toString())
        }
        for (chave in listOf(TEMPERATURA_DO_MOTORISTA, TEMPERATURA_DO_PASSAGEIRO)) {
            passo(chave) {
                val antes = anterior[chave]?.let { numeroDoCarro(it) ?: error("temperatura anterior ilegível '$it'") }
                    ?: TEMPERATURA_PADRAO
                escrever(chave, comUmaCasa(temperaturaSuavizada(antes, f)))
            }
        }
        escrever(LIGADO, "1")
        escrever(AR, "1")
        passo("recirculação") {
            val interna = numeroDoCarro(carro.lerDoCarro(TEMPERATURA_INTERNA))
            val externa = numeroDoCarro(carro.lerDoCarro(TEMPERATURA_EXTERNA))
            if (interna != null && externa != null) escrever(RECIRCULACAO, if (interna < externa) "1" else "0")
        }
    }

    // ── J.5 ──

    private fun sairNaLinha(motivo: String) {
        if (!ativo) return
        ativo = false
        agendados.cancelar("tempoLimite")
        for (chave in CHAVES_DE_ANTES) {
            if (chave == LIGADO || chave == AR) continue
            val valor = anterior[chave] ?: continue
            escrever(chave, valor)
        }
        escrever(AR, anterior[AR] ?: "1")
        escrever(LIGADO, anterior[LIGADO] ?: "1")
        anterior.clear()
        try {
            prefs.apagar(SALVO_ATIVO, SALVO_ANTERIOR)
        } catch (e: Exception) {
            registro.aviso("ar no máximo: não deu para apagar o estado salvo", e)
        }
        avisar(false)
        anotar("ar no máximo desligado", mapOf("motivo" to motivo))
    }

    // ── Interno ──

    private fun enfileirar(oQue: String, tarefa: () -> Unit) {
        try {
            agenda.executar(tarefa)
        } catch (e: Exception) {
            registro.erro("ar no máximo ($oQue): não deu para enfileirar", e)
        }
    }

    private fun escrever(chave: String, valor: String) {
        try {
            carro.escrever(chave, valor)
        } catch (e: Exception) {
            registro.erro("ar no máximo: escrever $chave=$valor falhou", e)
        }
    }

    private inline fun passo(nome: String, bloco: () -> Unit) {
        try {
            bloco()
        } catch (e: Exception) {
            registro.aviso("ar no máximo: suavização do $nome falhou neste passo", e)
        }
    }

    private fun avisar(ligado: Boolean) {
        try {
            aviso.mudou(ligado)
        } catch (e: Exception) {
            registro.aviso("ar no máximo: aviso ao painel falhou", e)
        }
    }

    private fun anotar(evento: String, detalhes: Map<String, Any?>) {
        registro.info("$evento $detalhes")
        try {
            diario(evento, detalhes)
        } catch (e: Exception) {
            registro.aviso("diário falhou em '$evento'", e)
        }
    }

    companion object {
        const val TEMPERATURA_INTERNA = "car.basic.inside_temp"
        const val TEMPERATURA_EXTERNA = "car.basic.outside_temp"
        const val LIGADO = "car.hvac.power_mode"
        const val AR = "car.hvac.ac_enable"
        const val VENTILADOR = "car.hvac.fan_speed"
        const val TEMPERATURA_DO_MOTORISTA = "car.hvac.driver_temperature"
        const val TEMPERATURA_DO_PASSAGEIRO = "car.hvac.pass_temperature"
        const val RECIRCULACAO = "car.hvac.cycle_mode"

        /** As 11 chaves de antes do máximo, na ordem em que voltam (J.5, fora `power_mode`/`ac_enable`). */
        val CHAVES_DE_ANTES = listOf(
            LIGADO,
            AR,
            VENTILADOR,
            TEMPERATURA_DO_MOTORISTA,
            TEMPERATURA_DO_PASSAGEIRO,
            "car.hvac.auto_enable",
            "car.hvac.anion_enable",
            "car.hvac.aqs_enable",
            "car.hvac.sync_enable",
            "car.hvac.setting.comfort_curve",
            RECIRCULACAO,
        )

        /** J.2.3: as 7 escritas, nesta ordem. */
        val ESCRITAS_AO_LIGAR = listOf(
            LIGADO to "1",
            "car.hvac.auto_enable" to "0",
            VENTILADOR to "7",
            TEMPERATURA_DO_MOTORISTA to "16.0",
            TEMPERATURA_DO_PASSAGEIRO to "16.0",
            "car.hvac.sync_enable" to "1",
            "car.hvac.setting.comfort_curve" to "2",
        )

        /** Estado salvo (formato gravado nos carros). */
        const val SALVO_ATIVO = "MAX_AC_ACTIVE_PERSISTED"
        const val SALVO_ANTERIOR = "MAX_AC_PREVIOUS_STATE"

        const val LIMIAR_PADRAO = 35.0f
        const val ALVO_PADRAO = 28.0f
        const val FAIXA_DE_SUAVIZACAO = 2.0
        const val LEITURA_INVALIDA_ACIMA = 85.0
        const val LEITURA_INVALIDA_ABAIXO = -40.0
        const val NOVAS_TENTATIVAS = 5
        const val ESPERA_ENTRE_TENTATIVAS_MS = 1_000L

        const val VENTILADOR_MAXIMO = 7
        const val VENTILADOR_PADRAO = 3
        const val VENTILADOR_MINIMO = 3
        const val TEMPERATURA_MINIMA = 16.0
        const val TEMPERATURA_PADRAO = 22.0
        const val TEMPERATURA_TETO_NA_SUAVIZACAO = 20.0

        /** J.4: `antes + round((7 − antes) × f)`, no mínimo 3 (x,5 arredonda para cima). */
        fun ventiladorSuavizado(antes: Int, f: Double): Int =
            (antes + ((VENTILADOR_MAXIMO - antes) * f).roundToInt()).coerceAtLeast(VENTILADOR_MINIMO)

        /** J.4: `antes − (antes − 16) × f`, no máximo 20,0. */
        fun temperaturaSuavizada(antes: Double, f: Double): Double =
            (antes - (antes - TEMPERATURA_MINIMA) * f).coerceAtMost(TEMPERATURA_TETO_NA_SUAVIZACAO)

        /** Uma casa, ponto decimal (o formato das temperaturas do ar). */
        fun comUmaCasa(valor: Double): String = String.format(Locale.US, "%.1f", valor)

        /** O JSON salvo (objeto chave → texto). Lança se não for um objeto. */
        fun lerJson(texto: String): Map<String, String?> {
            val objeto = Json.parseToJsonElement(texto).jsonObject
            val mapa = LinkedHashMap<String, String?>()
            for ((chave, valor) in objeto) {
                mapa[chave] = when (valor) {
                    is JsonNull -> null
                    is JsonPrimitive -> valor.content
                    else -> valor.toString()
                }
            }
            return mapa
        }
    }
}
