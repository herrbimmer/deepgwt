// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.comandos

import br.com.deepgwt.nucleo.Preferencias
import br.com.deepgwt.veiculo.ProjecaoNasTelas
import br.com.deepgwt.veiculo.TravaDeMovimento
import br.com.deepgwt.veiculo.motor.Agenda
import br.com.deepgwt.veiculo.motor.RegistroDoMotor

/**
 * Os comandos do carro (módulo 3.3c, `docs/independencia/specs/motor-comandos.md`): vidros, teto,
 * cortina, porta-malas, câmera 360, escritas simples e o alvo de carga do híbrido. Sem Android: tudo
 * o que vem de fora chega pelas costuras. O ar no máximo mora em [ControleDoArNoMaximo].
 *
 * Nenhuma função lança (regra 1.3 do índice): falha é registrada e vira o valor de falha descrito.
 */
internal class ComandosDoCarro(
    private val carro: CarroDoMotor,
    private val voz: AlcasDaVoz,
    private val prefs: PreferenciasDoMotor,
    private val agenda: Agenda,
    private val trava: (exigirP: Boolean, exigirLeitura: Boolean) -> TravaDeMovimento.Motivo?,
    private val registro: RegistroDoMotor,
    private val diario: DiarioDoResto,
    private val decisoes: DecisoesDosComandos = DecisoesDosComandos(),
) {

    /** Guardas de projeção da câmera (E.4). Vazia até a sala suja ligar o lançador. */
    @Volatile var projecao: ProjecaoNasTelas = ProjecaoNasTelas.VAZIA

    /** E.3: o app abriu a câmera 360 e ela ainda não fechou. */
    @Volatile var cameraAbertaPeloApp: Boolean = false

    // ── B. Vidros ──

    /** B.1: estado dos quatro vidros; null sem alça ou com falha. */
    fun estadoDasJanelas(): IntArray? {
        val v = alcaDoVeiculo("estado dos vidros") ?: return null
        return tentar("ler o estado dos vidros", null) { v.estadoDasJanelas() }
    }

    /** B.2. */
    fun janela(indice: Int, estado: Int): Boolean {
        val v = alcaDoVeiculo("vidro $indice") ?: return false
        return tentar("vidro $indice → $estado", false) {
            v.pedirJanela(indice, estado)
            true
        }
    }

    /**
     * B.3: pede fechar cada posição diferente de 1 (inclusive -1 e 0, D-C5). Tenta todas, mesmo que
     * uma falhe; devolve true se a leitura e todos os pedidos voltaram.
     */
    fun fecharTodasAsJanelas(): Boolean {
        val v = alcaDoVeiculo("fechar os vidros") ?: return false
        val estados = tentar("ler o estado dos vidros", null) { v.estadoDasJanelas() }
        if (estados == null) {
            registro.aviso("fechar os vidros: estado dos vidros não veio")
            return false
        }
        var tudoCerto = true
        for ((posicao, estado) in estados.withIndex()) {
            if (estado == VIDRO_FECHADO) continue
            val pediu = tentar("fechar o vidro $posicao", false) {
                v.pedirJanela(posicao, VIDRO_FECHADO)
                true
            }
            if (!pediu) tudoCerto = false
        }
        return tudoCerto
    }

    // ── C. Teto e cortina ──

    /** C.1: nível do teto; null sem alça ou com falha. */
    fun aberturaDoTeto(): Int? {
        val v = alcaDoVeiculo("nível do teto") ?: return null
        return tentar("ler o teto", null) { v.nivelDoTeto() }
    }

    /** C.1. */
    fun teto(nivel: Int): Boolean {
        val v = alcaDoVeiculo("teto") ?: return false
        return tentar("teto → $nivel", false) {
            v.pedirTeto(nivel)
            true
        }
    }

    /** C.2. */
    fun aberturaDaCortina(): Int? {
        val v = alcaDoVeiculo("nível da cortina") ?: return null
        return tentar("ler a cortina", null) { v.nivelDaCortina() }
    }

    /** C.2. */
    fun cortina(nivel: Int): Boolean {
        val v = alcaDoVeiculo("cortina") ?: return false
        return tentar("cortina → $nivel", false) {
            v.pedirCortina(nivel)
            true
        }
    }

    /**
     * C.3: com a cortina mais fechada que o teto pedido, o carro ignora o teto em silêncio. Então
     * abre a cortina e manda o teto 1500 ms depois; nos demais casos, o teto na hora.
     */
    fun abrirTetoComCortina(nivel: Int): Boolean {
        val cortinaAgora = aberturaDaCortina()
        if (cortinaAgora != null && cortinaAgora < nivel) {
            if (cortina(CORTINA_ABERTA)) {
                agenda.agendar(TETO_DEPOIS_DA_CORTINA_MS) { teto(nivel) }
                return true
            }
            registro.aviso("teto com cortina: a cortina não abriu; mando o teto mesmo assim")
        }
        return teto(nivel)
    }

    /**
     * C.4: fecha o teto se não está fechado; com [fecharCortinaJunto] e a preferência, a cortina 5 s
     * depois (C.6). O agendamento da cortina não depende de o teto ter respondido.
     */
    fun fecharTeto(fecharCortinaJunto: Boolean) {
        val v = alcaDoVeiculo("fechar o teto")
        if (v != null) {
            tentar("fechar o teto", Unit) {
                if (v.nivelDoTeto() != FECHADO) v.pedirTeto(FECHADO)
            }
        }
        val cortinaJunto = prefs.booleano(Preferencias.CLOSE_SUNROOF_SUN_SHADE_ON_CLOSE_SUN_ROOF.chave, false)
        if (fecharCortinaJunto && cortinaJunto) {
            agenda.agendar(CORTINA_DEPOIS_DO_TETO_MS) { fecharCortina() }
        }
    }

    /** C.5: abre a cortina (100) se ainda não está aberta; tudo vai ao diário. */
    fun abrirCortina() {
        val v = voz.veiculo()
        if (v == null) {
            anotar("cortina: falhou", mapOf("erro" to "sem a alça da voz"))
            return
        }
        try {
            val antes = v.nivelDaCortina()
            if (antes != CORTINA_ABERTA) {
                v.pedirCortina(CORTINA_ABERTA)
                anotar("cortina aberta", mapOf("nivelAnterior" to antes))
            } else {
                anotar("cortina já aberta", mapOf("nivel" to antes))
            }
        } catch (e: Exception) {
            anotar("cortina: falhou", mapOf("erro" to e.javaClass.simpleName, "mensagem" to e.message))
        } catch (e: LinkageError) {
            anotar("cortina: falhou", mapOf("erro" to e.javaClass.simpleName, "mensagem" to e.message))
        }
    }

    /** C.6: fecha a cortina (0) se não está fechada. */
    fun fecharCortina() {
        val v = alcaDoVeiculo("fechar a cortina") ?: return
        tentar("fechar a cortina", Unit) {
            if (v.nivelDaCortina() != FECHADO) v.pedirCortina(FECHADO)
        }
    }

    // ── D. Porta-malas ──

    /** D.2: só o cache de `car.basic.door_status`, posição 5. */
    fun portaMalasAberto(): Boolean? =
        LeiturasDoCarro.posicaoLigada(carro.espiar(CHAVE_PORTAS), POSICAO_DO_PORTA_MALAS)

    /** D.3: passa pela trava de movimento (abrir exige P; sempre exige leitura) antes de pedir. */
    fun portaMalas(aberto: Boolean): Boolean {
        val recusa = try {
            trava(aberto, true)
        } catch (e: Exception) {
            TravaDeMovimento.Motivo.SEM_LEITURA
        }
        if (recusa != null) {
            registro.aviso("porta-malas ${if (aberto) "abrir" else "fechar"} recusado: ${recusa.texto}")
            return false
        }
        val v = alcaDoVeiculo("porta-malas") ?: return false
        return tentar("porta-malas → $aberto", false) {
            v.pedirPorta(POSICAO_DO_PORTA_MALAS, if (aberto) 1 else 0)
            true
        }
    }

    // ── E. Câmera 360 ──

    /** E.1: alterna. Cache "0" abre; outro valor fecha; sem cache segue a decisão de E.5. */
    fun alternarCamera360() {
        val estado = carro.espiar(CHAVE_ESTADO_DA_CAMERA)?.trim()
        val abrir = if (estado == null) {
            if (!decisoes.cameraSemEstadoAbre) {
                registro.aviso("câmera 360: estado desconhecido; nada feito")
                return
            }
            true
        } else {
            estado == CAMERA_FECHADA
        }
        val camera = voz.camera()
        if (camera == null) {
            registro.erro("câmera 360: sem a alça da voz (dvr); nada feito")
            return
        }
        if (abrir) {
            val pediu = tentar("abrir a câmera 360", false) {
                camera.pedirAvm(1)
                true
            }
            if (!pediu) return
            cameraAbertaPeloApp = true
            guardasDaCamera(abrindo = true, motivo = "camera 360 aberta pelo app")
        } else {
            val pediu = tentar("fechar a câmera 360", false) {
                camera.pedirAvm(0)
                true
            }
            if (!pediu) return
            cameraAbertaPeloApp = false
            guardasDaCamera(abrindo = false, motivo = "camera 360 fechada pelo app")
        }
    }

    /** E.2: fecha a câmera (automações). */
    fun fecharCamera360() {
        val camera = voz.camera()
        if (camera == null) {
            registro.erro("fechar a câmera 360: sem a alça da voz (dvr)")
            return
        }
        tentar("fechar a câmera 360", Unit) { camera.pedirAvm(0) }
    }

    /** E.4: AA no display 3 → só o pulso de foco; senão as guardas do CarPlay e do AA no painel. */
    private fun guardasDaCamera(abrindo: Boolean, motivo: String) {
        tentar("guardas de projeção da câmera", Unit) {
            val p = projecao
            if (p.androidAutoNoDisplay(DISPLAY_DO_PAINEL)) {
                if (abrindo) p.pulsoDoAndroidAutoDuranteJanelaNativa(motivo) else p.pulsoDoAndroidAutoAoSairDaJanelaNativa(motivo)
            } else {
                p.guardarCarPlayNoPainel(motivo)
                p.guardarAndroidAutoNoPainelAposJanelaNativa(motivo)
            }
        }
    }

    // ── H. Escritas simples ──

    fun modoDeConducao(modo: String) {
        if (podeEscrever("modo de condução")) escrever("car.drive_setting.drive_mode", modo)
    }

    fun esp(ligado: Boolean) {
        if (podeEscrever("ESP")) escrever("car.drive_setting.esp_enable", umOuZero(ligado))
    }

    /** O modo do carro e depois a descarga do motor (D-C9: nunca misturar com o alvo do híbrido). */
    fun forcarMotorACombustao(ligado: Boolean) {
        if (!podeEscrever("motor a combustão")) return
        val modoOk = escrever("car.ev_setting.power_model_config", if (ligado) "0" else "1")
        if (!modoOk) {
            registro.erro("motor a combustão: o modo não foi escrito; a descarga não é mandada")
            return
        }
        escrever("car.ev.setting.engine_discharge_enable", if (ligado) "1" else "0")
    }

    fun modoTravessia(ligado: Boolean) {
        if (podeEscrever("modo travessia")) escrever("car.ev.setting.wade_mode_enable", umOuZero(ligado))
    }

    fun somParaPedestres(ligado: Boolean) {
        if (podeEscrever("som para pedestres")) escrever("car.ev.setting.avas_enable", umOuZero(ligado))
    }

    /** Distração e fadiga, cada escrita numa tentativa própria. */
    fun monitoramento(ligado: Boolean) {
        if (!podeEscrever("monitoramento")) return
        escrever("car.frs_setting.distraction_detection_enable", umOuZero(ligado))
        escrever("car.frs_setting.fatigue_monitoring_enable", umOuZero(ligado))
    }

    fun fecharJanelasAoTrancar(ligado: Boolean) {
        if (podeEscrever("fechar vidros ao trancar")) escrever("car.basic.window_close_by_lock_vehicle", umOuZero(ligado))
    }

    /** Valores 1..3 nunca confirmados no carro (D-C10). */
    fun gravarMemoriaDoBanco(posicao: Int) {
        if (posicao !in POSICOES_DO_BANCO) {
            registro.aviso("gravar memória do banco: posição $posicao fora de 1..3; nada feito")
            return
        }
        if (podeEscrever("gravar memória do banco")) escrever(CHAVE_MEMORIA_DO_BANCO, posicao.toString())
    }

    /** Valores 11..13 nunca confirmados no carro (D-C10). */
    fun chamarMemoriaDoBanco(posicao: Int) {
        // Sem esta checagem, "gravar 11" seria "chamar 1": o mesmo campo carrega as duas ações.
        if (posicao !in POSICOES_DO_BANCO) {
            registro.aviso("chamar memória do banco: posição $posicao fora de 1..3; nada feito")
            return
        }
        if (podeEscrever("chamar memória do banco")) escrever(CHAVE_MEMORIA_DO_BANCO, (posicao + 10).toString())
    }

    // ── I. Alvo de carga do híbrido ──

    /** I.1 na linha do motor (três leituras remotas); quem chama volta na hora. */
    fun aplicarAlvoDeCargaHev(motivo: String) {
        try {
            agenda.executar { aplicarAlvoNaLinha(motivo) }
        } catch (e: Exception) {
            registro.erro("alvo do híbrido ($motivo): não deu para enfileirar", e)
        }
    }

    /** I.1: só reaplica se o carro já está em HEV Prioritário; nunca escreve o modo nem o sub-modo. */
    internal fun aplicarAlvoNaLinha(motivo: String) {
        if (!prefs.booleano(Preferencias.ENABLE_PERSIST_HEV_SOC_TARGET.chave, false)) return
        if (carro.lerDoCarro(CHAVE_MODO_DE_POTENCIA)?.trim() != MODO_HEV) return
        if (carro.lerDoCarro(CHAVE_RESERVA)?.trim() != RESERVA_PRIORITARIO) return
        val atual = carro.lerDoCarro(CHAVE_ALVO_DE_CARGA)
        if (atual == null) {
            registro.info("alvo do híbrido ($motivo): sem leitura do alvo atual; nada escrito")
            return
        }
        val desejado = prefs.inteiro(Preferencias.HEV_SOC_TARGET_VALUE.chave, ALVO_PADRAO)
        if (atual.trim().toIntOrNull() == desejado) return
        if (escrever(CHAVE_ALVO_DE_CARGA, desejado.toString())) {
            registro.info("alvo do híbrido ($motivo): $atual → $desejado")
        }
    }

    /** I.3: limita a 20..80, grava a preferência e escreve, sem conferir modo nem canal. */
    fun definirAlvoDeCargaHev(valor: Int) {
        val alvo = valor.coerceIn(ALVO_MINIMO, ALVO_MAXIMO)
        try {
            prefs.gravarInteiro(Preferencias.HEV_SOC_TARGET_VALUE.chave, alvo)
        } catch (e: Exception) {
            registro.erro("alvo do híbrido: não deu para gravar a preferência", e)
        }
        if (escrever(CHAVE_ALVO_DE_CARGA, alvo.toString())) {
            registro.info("alvo do híbrido definido: $alvo (pedido $valor)")
        }
    }

    // ── Interno ──

    /** A.1 (com a decisão D-C4 para o simulador). */
    private fun podeEscrever(comando: String): Boolean {
        val vivo = try {
            carro.controleVivo
        } catch (e: Exception) {
            false
        }
        if (vivo) return true
        if (decisoes.escreverNoSimulador && carro.simulador) return true
        registro.erro("$comando: canal com a central morto; nada escrito")
        return false
    }

    private fun escrever(chave: String, valor: String): Boolean =
        tentar("escrever $chave=$valor", false) {
            carro.escrever(chave, valor)
            true
        }

    private fun alcaDoVeiculo(oQue: String): VozDoVeiculo? {
        val v = try {
            voz.veiculo()
        } catch (e: Exception) {
            null
        }
        if (v == null) registro.erro("$oQue: sem a alça da voz (veículo)")
        return v
    }

    private fun anotar(evento: String, detalhes: Map<String, Any?>) {
        registro.info("$evento $detalhes")
        try {
            diario(evento, detalhes)
        } catch (e: Exception) {
            registro.aviso("diário falhou em '$evento'", e)
        }
    }

    private inline fun <T> tentar(oQue: String, falha: T, bloco: () -> T): T =
        try {
            bloco()
        } catch (e: Exception) {
            registro.erro("$oQue falhou", e)
            falha
        } catch (e: LinkageError) {
            registro.erro("$oQue falhou", e)
            falha
        }

    private fun umOuZero(ligado: Boolean) = if (ligado) "1" else "0"

    companion object {
        const val VIDRO_FECHADO = 1
        const val FECHADO = 0
        const val CORTINA_ABERTA = 100
        const val TETO_DEPOIS_DA_CORTINA_MS = 1_500L
        const val CORTINA_DEPOIS_DO_TETO_MS = 5_000L
        const val POSICAO_DO_PORTA_MALAS = 5
        const val DISPLAY_DO_PAINEL = 3
        const val CAMERA_FECHADA = "0"

        const val CHAVE_PORTAS = "car.basic.door_status"
        const val CHAVE_ESTADO_DA_CAMERA = "sys.avm.preview_status"
        const val CHAVE_MEMORIA_DO_BANCO = "car.comfort_setting.chair_mem_pos_set_action"
        /** As três memórias do banco do motorista. */
        val POSICOES_DO_BANCO = 1..3
        const val CHAVE_MODO_DE_POTENCIA = "car.ev_setting.power_model_config"
        const val CHAVE_RESERVA = "car.ev_setting.power_reserve_config"
        const val CHAVE_ALVO_DE_CARGA = "car.ev_setting.charge_soc_target_config"

        const val MODO_HEV = "0"
        const val RESERVA_PRIORITARIO = "2"
        const val ALVO_PADRAO = 50
        const val ALVO_MINIMO = 20
        const val ALVO_MAXIMO = 80
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
