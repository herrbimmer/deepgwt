package br.com.deepgwt.onboarding

import br.com.deepgwt.ajustes.AjustesRemotos
import br.com.deepgwt.ajustes.CatalogoDeAjustes
import br.com.deepgwt.nucleo.Preferencias as K

/**
 * A predefinição recomendada do assistente inicial (Alpha 10.0): um pacote de ajustes que o dono
 * escolheu como ponto de partida. Tudo pode ser editado depois, nas telas de sempre.
 *
 * Os valores vão pelo MESMO caminho do instalador e da tela de Configurações
 * ([AjustesRemotos.aplicar], ids do catálogo): um ajuste que o nível da licença não libera volta
 * como recusado e o resto passa, então quem está no Essencial leva só o que o nível abre.
 *
 * O que ela faz:
 *  - desativa o AVAS;
 *  - liga os gestos rápidos de 3 dedos;
 *  - liga o HUD (overlay);
 *  - liga o tema customizado do painel, com o Stuttgart;
 *  - fecha a câmera 360° com o carro parado e só a deixa abrir pelo botão;
 *  - desliga o hotspot e o Bluetooth ao desligar o carro (um ajuste só, o espelho cuida do outro).
 */
object PredefinicaoRecomendada {

    /** Pasta do tema que a predefinição aplica (`assets/PreinstalledThemes`). */
    const val TEMA = "stuttgart-mbux"

    /** Os booleanos: o que a predefinição liga ou desliga. Ordem estável (é a da explicação). */
    val INTERRUPTORES: Map<String, Boolean> = linkedMapOf(
        K.DISABLE_AVAS.chave to true,
        K.QUICK_GESTURES_ENABLED.chave to true,
        K.HUD_OVERLAY_ENABLED.chave to true,
        "painel.tema_customizado" to true,
        K.DISABLE_AVM_CAR_STOPPED.chave to true,
        K.AVM_MANUAL_WHEN_STOPPED.chave to true,
        K.DISABLE_HOTSPOT_ON_POWER_OFF.chave to true,
    )

    /** O lote completo para [AjustesRemotos.aplicar]: o tema entra depois de ligar o tema customizado. */
    val ajustes: Map<String, Any?> = LinkedHashMap<String, Any?>(INTERRUPTORES).apply {
        put(CatalogoDeAjustes.ID_DO_TEMA_DO_PAINEL, TEMA)
    }

    /**
     * "Customizar do zero": os mesmos interruptores de volta ao padrão do catálogo. Serve para quem
     * tocou na recomendada e se arrependeu antes de seguir; numa instalação nova não muda nada.
     */
    fun padroes(): Map<String, Any?> =
        INTERRUPTORES.keys.associateWith { id -> CatalogoDeAjustes.ajuste(id)?.padrao ?: false }

    /** Frases para o cartão do assistente, uma por efeito. */
    val RESUMO: List<String> = listOf(
        "Sem o AVAS",
        "Gestos rápidos ligados",
        "HUD do DeepGWT ligado",
        "Painel com o tema Stuttgart",
        "Câmera 360° só pelo botão",
        "Hotspot e Bluetooth desligam com o carro",
    )
}
