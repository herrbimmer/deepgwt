// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.carro

import br.com.deepgwt.veiculo.Comandos
import br.com.deepgwt.veiculo.Veiculo
import br.com.deepgwt.nucleo.DeepGwt
import android.content.Intent
import android.util.Log
import br.com.deepgwt.licenca.Portao
import br.com.deepgwt.licenca.Recurso
import br.com.deepgwt.macros.MacroAction
import br.com.deepgwt.macros.MacroActionCatalog
import br.com.deepgwt.macros.MacroEngine
import br.com.deepgwt.macros.MacroStore
import br.com.deepgwt.veiculo.Sinais
import org.json.JSONObject

/**
 * O que um botão coringa pode fazer (Alpha 8.5).
 *
 * Ações de UM toque, pensadas para o volante: alternar, e não "definir para 22 °C". Onde já existe
 * a ação de macro equivalente ([MacroActionCatalog]), é ela que roda — o mesmo caminho testado da
 * macro, que já sabe suspender o clima antes de escrever. Para qualquer outra coisa há "Rodar uma
 * macro" (Pro), que cobre as 35 ações e as sequências.
 *
 * O [id] fica gravado na preferência e não muda; [titulo] é o que a tela mostra.
 */
enum class AcaoDoCoringa(val id: String, val titulo: String, val parametro: Parametro = Parametro.NENHUM) {
    CARRO("carro", "Função do carro (a do menu do carro)"),
    CAMERA_360("camera360", "Abrir/fechar a câmera 360°"),
    RECIRCULACAO("recirculacao", "Recirculação do ar (liga/desliga)"),
    DESEMBACADOR("desembacador", "Desembaçador do para-brisa (liga/desliga)"),
    DESEMBACADOR_TRASEIRO("desembacador_traseiro", "Desembaçador traseiro (liga/desliga)"),
    AR("ar", "Ar-condicionado (liga/desliga)"),
    IONIZADOR("ionizador", "Ionizador (liga/desliga)"),
    VENTILACAO_BANCO("ventilacao_banco", "Ventilação do banco do motorista (máximo/desliga)"),
    AQUECIMENTO_BANCO("aquecimento_banco", "Aquecimento do banco do motorista (máximo/desliga)"),
    MUDO("mudo", "Mudo da mídia (liga/desliga)"),
    FECHAR_VIDROS("fechar_vidros", "Fechar todos os vidros"),
    REGENERACAO("regeneracao", "Próximo nível de regeneração"),
    MODO_ELETRICO("modo_eletrico", "Próximo modo HEV / EV / Prioridade EV (PHEV)"),
    UM_PEDAL("um_pedal", "Condução com um pedal (liga/desliga)"),
    CONECTOR("conector", "Liberar o conector de recarga (PHEV)"),
    ABRIR_APP("abrir_app", "Abrir um app", Parametro.APP),
    MACRO("macro", "Rodar uma macro", Parametro.MACRO);

    enum class Parametro { NENHUM, APP, MACRO }

    companion object {
        // "alexa" (9.0) e "voz" (9.1) eram assistentes que saíram na 9.2: sem ação com esse id, o
        // botão volta para a função do carro (ver EscolhaDoCoringa.deJson).
        fun deId(id: String?): AcaoDoCoringa? = entries.firstOrNull { it.id == id }
    }
}

/** A escolha gravada de um botão: a ação e, quando ela pede, o app ou a macro. */
data class EscolhaDoCoringa(val acao: AcaoDoCoringa, val parametro: String = "") {
    fun toJson(): String = JSONObject().put("id", acao.id).put("param", parametro).toString()

    /** O que a tela mostra embaixo do botão. */
    fun descricao(): String = when (acao.parametro) {
        AcaoDoCoringa.Parametro.NENHUM -> acao.titulo
        AcaoDoCoringa.Parametro.APP -> if (parametro.isBlank()) "Abrir um app (nenhum escolhido)" else "Abrir ${nomeDoApp(parametro)}"
        AcaoDoCoringa.Parametro.MACRO ->
            MacroStore.porId(parametro)?.let { "Macro \"${it.name}\"" } ?: "Rodar uma macro (nenhuma escolhida)"
    }

    companion object {
        val DO_CARRO = EscolhaDoCoringa(AcaoDoCoringa.CARRO)

        /** Texto gravado → escolha. Ausente, ilegível ou de versão futura = função do carro. */
        fun deJson(texto: String?): EscolhaDoCoringa {
            if (texto.isNullOrBlank()) return DO_CARRO
            return runCatching {
                val o = JSONObject(texto)
                val acao = AcaoDoCoringa.deId(o.optString("id")) ?: return DO_CARRO
                EscolhaDoCoringa(acao, o.optString("param"))
            }.getOrDefault(DO_CARRO)
        }

        fun nomeDoApp(pacote: String): String = runCatching {
            val pm = DeepGwt.contexto.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pacote, 0)).toString()
        }.getOrDefault(pacote)
    }
}

/** Executa uma escolha. Sempre fora da thread principal (na [FilaDoCarro]). */
internal object ExecutorDoCoringa {
    private const val TAG = "CoringasDoVolante"


    private fun macro(tipo: String, vararg params: Pair<String, String>) {
        val spec = MacroActionCatalog.porId(tipo) ?: return
        spec.run(MacroAction(tipo, mapOf(*params)))
    }

    /** "Máximo ou desliga": nível > 0 vira 0; 0 vira [maximo]. */
    private fun alternarNivel(chave: String, maximo: Int) {
        val atual = Veiculo.ler(chave)?.trim()?.toIntOrNull() ?: 0
        Veiculo.escreverConferindo(chave, if (atual > 0) "0" else maximo.toString())
    }

    /** Próximo valor de [ordem], a partir do atual (fora da ordem = o primeiro). */
    private fun ciclar(chave: String, ordem: List<String>) {
        val atual = Veiculo.lerDoCarro(chave)?.trim()
        val i = ordem.indexOf(atual)
        Veiculo.escreverConferindo(chave, ordem[(i + 1) % ordem.size])
    }

    /** @return o texto do cartão de confirmação, ou null quando não há o que mostrar. */
    fun executar(escolha: EscolhaDoCoringa): String? {
        when (escolha.acao) {
            AcaoDoCoringa.CARRO -> return null
            AcaoDoCoringa.CAMERA_360 -> Comandos.abrirCamera360()
            AcaoDoCoringa.RECIRCULACAO -> macro("hvac_recirc", "state" to "t")
            AcaoDoCoringa.DESEMBACADOR -> macro("hvac_defrost_front", "state" to "t")
            AcaoDoCoringa.DESEMBACADOR_TRASEIRO -> macro("hvac_defrost_rear", "state" to "t")
            AcaoDoCoringa.AR -> macro("hvac_power", "state" to "t")
            AcaoDoCoringa.IONIZADOR -> macro("hvac_anion", "state" to "t")
            AcaoDoCoringa.VENTILACAO_BANCO ->
                alternarNivel(Sinais.CAR_COMFORT_SETTING_DRIVER_SEAT_VENTILATION_LEVEL.chave, 3)
            AcaoDoCoringa.AQUECIMENTO_BANCO ->
                alternarNivel(Sinais.CAR_COMFORT_SETTING_DRIVER_SEAT_HEATING_LEVEL.chave, 3)
            AcaoDoCoringa.MUDO -> macro("media_mute", "state" to "t")
            AcaoDoCoringa.FECHAR_VIDROS -> Comandos.fecharTodasAsJanelas()
            // Ordens medidas no H6 GT: regeneração normal(0) → alta(1) → baixa(2);
            // modo HEV(0) → Prioridade EV(1) → EV(3).
            AcaoDoCoringa.REGENERACAO -> ciclar(Sinais.CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL.chave, listOf("0", "1", "2"))
            AcaoDoCoringa.MODO_ELETRICO -> ciclar(Sinais.CAR_EV_SETTING_POWER_MODEL_CONFIG.chave, listOf("0", "1", "3"))
            AcaoDoCoringa.UM_PEDAL -> {
                val chave = Sinais.CAR_EV_SETTING_PEDAL_CONTROL_ENABLE.chave
                val ligado = Veiculo.lerDoCarro(chave)?.trim() == "1"
                Veiculo.escreverConferindo(chave, if (ligado) "0" else "1")
            }
            AcaoDoCoringa.CONECTOR -> {
                ConectorDeRecarga.liberar { r -> br.com.deepgwt.macros.MacroFeedback.mostrar(r.mensagem) }
                return null
            }
            AcaoDoCoringa.ABRIR_APP -> {
                val pacote = escolha.parametro.trim()
                if (pacote.isEmpty()) return "Coringa sem app escolhido"
                val intent = DeepGwt.contexto.packageManager.getLaunchIntentForPackage(pacote)
                    ?: return "App não encontrado"
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                DeepGwt.contexto.startActivity(intent)
            }
            AcaoDoCoringa.MACRO -> {
                if (!Portao.permite(Recurso.MACROS)) return "Macros: disponível no ${Recurso.MACROS.nivel.titulo}"
                val m = MacroStore.porId(escolha.parametro) ?: return "Macro não encontrada"
                // A macro mostra o próprio cartão (se pedir); não duplicar.
                MacroEngine.executar(m, "coringa do volante", ignorarJanela = true)
                return null
            }
        }
        Log.w(TAG, "Coringa executou: ${escolha.acao.id}")
        return escolha.descricao()
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
