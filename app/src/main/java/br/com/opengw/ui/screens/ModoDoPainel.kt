package br.com.opengw.ui.screens

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import br.com.opengw.managers.TemaDoPainel
import br.com.opengw.ui.telas.TemaEmbutido
import br.com.opengw.nucleo.Preferencia
import br.com.opengw.nucleo.Preferencias
import br.com.opengw.projection.DestinoDaProjecao

/**
 * O que o OPEN GW faz no painel de instrumentos, do ponto de vista do resto do app.
 *
 * Desde a Alpha 6.0 Telas não mostra mais este modo: mostra os interruptores de [PainelEscolhido],
 * e o modo sai deles. Ele continua aqui porque é o que as quatro chaves antigas descrevem.
 *
 * ## POR QUE EXISTE
 *
 * Telas tinha "Habilitar Funções do Cluster" e, embaixo, "Painel Virtual", que só destravava com
 * o primeiro ligado. Com o de cima ligado e o de baixo desligado o painel de fábrica continuava
 * na tela, mas o tema Default rodava transparente por cima dele — e era ali que apareciam as
 * temperaturas nos cantos e o WARN. Era um terceiro modo que ninguém tinha escolhido por nome.
 *
 * As três combinações que fazem sentido viraram os três modos. As quatro preferências continuam
 * sendo as mesmas que o resto do app lê (projetor, integração de mídia, menu do volante e Painel
 * Virtual); esta classe só decide como elas andam juntas.
 */
enum class ModoDoPainel {
    /** Painel de fábrica puro: nada do OPEN GW desenhado nem interceptado no cluster. */
    DESLIGADO,

    /**
     * Painel de fábrica com a camada transparente por cima: temperatura externa, menu e cards de
     * mídia do OPEN GW. É o estado antigo "funções ligadas, Painel Virtual desligado".
     */
    ORIGINAL_COM_INFORMACOES,

    /** O OPEN GW desenha o painel inteiro com o tema escolhido (o antigo Painel Virtual). */
    TEMA_CUSTOMIZADO;

    /** Liga o projetor, a integração de mídia e o menu do volante. */
    val usaCamadaDoPainel: Boolean
        get() = this != DESLIGADO

    /** O `ENABLE_VIRTUAL_CLUSTER` — o tema desenha mostradores e máscaras. */
    val desenhaOPainelInteiro: Boolean
        get() = this == TEMA_CUSTOMIZADO

    companion object {
        /**
         * Lê o modo de preferências gravadas por qualquer versão anterior.
         *
         * O antigo interruptor mestre gravava as três primeiras chaves juntas, mas uma delas
         * ligada basta para contar como "ligado" — é a mesma regra que a tela antiga usava para
         * mostrar o interruptor aceso. Painel Virtual gravado sem a camada não é um modo: sem
         * projetor não há tema nenhum na tela, então vale DESLIGADO.
         */
        fun de(
                projetor: Boolean,
                integracaoDeMidia: Boolean,
                menuDoVolante: Boolean,
                painelVirtual: Boolean
        ): ModoDoPainel {
            val camada = projetor || integracaoDeMidia || menuDoVolante
            return when {
                !camada -> DESLIGADO
                painelVirtual -> TEMA_CUSTOMIZADO
                else -> ORIGINAL_COM_INFORMACOES
            }
        }

        fun ler(prefs: SharedPreferences): ModoDoPainel =
                de(
                        projetor =
                                prefs.getBoolean(
                                        Preferencias.ENABLE_INSTRUMENT_PROJECTOR.chave,
                                        false
                                ),
                        integracaoDeMidia =
                                prefs.getBoolean(
                                        Preferencias
                                                .ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION
                                                .chave,
                                        false
                                ),
                        menuDoVolante =
                                prefs.getBoolean(Preferencias.ENABLE_CUSTOM_MENU.chave, false),
                        painelVirtual =
                                prefs.getBoolean(Preferencias.ENABLE_VIRTUAL_CLUSTER.chave, false)
                )

        /** As preferências que cada modo grava. Separado de [aplicar] para ser testável. */
        fun preferenciasDe(modo: ModoDoPainel): Map<String, Boolean> =
                mapOf(
                        Preferencias.ENABLE_INSTRUMENT_PROJECTOR.chave to modo.usaCamadaDoPainel,
                        Preferencias.ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION.chave to
                                modo.usaCamadaDoPainel,
                        Preferencias.ENABLE_CUSTOM_MENU.chave to modo.usaCamadaDoPainel,
                        Preferencias.ENABLE_VIRTUAL_CLUSTER.chave to modo.desenhaOPainelInteiro
                )

        /**
         * Quando trocar de [anterior] para [novo] devolve o tema ao Default de fábrica.
         *
         * É a invariante que os dois interruptores antigos mantinham — religar o painel depois
         * não pode ressuscitar um tema customizado com o Painel Virtual desligado — e só ela: ir
         * de Desligado para o painel original não mexe nas cores que alguém deu ao Default.
         */
        fun voltaAoTemaDeFabrica(anterior: ModoDoPainel, novo: ModoDoPainel): Boolean =
                novo == DESLIGADO || (anterior.desenhaOPainelInteiro && !novo.desenhaOPainelInteiro)

        /**
         * O fundo do cluster e a tela de projeção saíram de Telas. Quem tinha escolhido outro
         * valor ficaria preso nele sem ter onde desfazer — e, no modo com o painel original, um
         * fundo opaco cobriria justamente o painel de fábrica. Volta para o automático: o fundo
         * que o próprio tema declara (transparente no Default) e a tela de cluster detectada.
         *
         * Devolve true quando a tela de projeção mudou, para quem chama recriar o projetor.
         */
        fun normalizarOpcoesRemovidas(prefs: SharedPreferences): Boolean {
            val fundoLigado =
                    prefs.getBoolean(Preferencias.ENABLE_CUSTOM_BACKGROUND_D1.chave, true)
            val tipoDoFundo =
                    prefs.getString(Preferencias.CUSTOM_BACKGROUND_TYPE_D1.chave, "THEME")
            val fundoForaDoTema = !fundoLigado || !"THEME".equals(tipoDoFundo, ignoreCase = true)
            val telaEscolhida = prefs.contains(Preferencias.INSTRUMENT_MASK_DISPLAY_ID.chave)
            if (!fundoForaDoTema && !telaEscolhida &&
                            prefs.contains(Preferencias.CUSTOM_BACKGROUND_TYPE_D1.chave)
            ) {
                return false
            }
            prefs.edit {
                if (fundoForaDoTema || !prefs.contains(Preferencias.CUSTOM_BACKGROUND_TYPE_D1.chave)) {
                    putBoolean(Preferencias.ENABLE_CUSTOM_BACKGROUND_D1.chave, true)
                    putString(Preferencias.CUSTOM_BACKGROUND_TYPE_D1.chave, "THEME")
                    putString(Preferencias.CUSTOM_BACKGROUND_VALUE_D1.chave, "")
                }
                if (telaEscolhida) remove(Preferencias.INSTRUMENT_MASK_DISPLAY_ID.chave)
            }
            return telaEscolhida
        }
    }
}

/**
 * As escolhas do painel de instrumentos como Telas as mostra desde a Alpha 6.0: interruptores
 * independentes em vez de um modo só.
 *
 *  • [temaCustomizado] — o OPEN GW desenha o painel inteiro (o antigo Painel Virtual).
 *  • [informacoes] — sobre o painel de fábrica: temperatura externa e placa de limite.
 *  • [circulo] — sobre o painel de fábrica: o círculo da direita recebe a projeção da central, no
 *    lugar do menu e do ar-condicionado que ficavam ali.
 *
 * As duas últimas só valem com o tema customizado desligado: com ele o tema desenha o painel
 * inteiro e a projeção vai para o vão entre os mostradores dele. Ficam guardadas enquanto isso, e
 * voltam a valer quando o tema for desligado.
 *
 * O [modo] que sai delas é o que as quatro chaves antigas gravam, então o projetor, o menu do volante
 * e a integração de mídia continuam lendo exatamente o que liam.
 */
data class PainelEscolhido(
        val temaCustomizado: Boolean,
        val informacoes: Boolean,
        val circulo: Boolean
) {
    val modo: ModoDoPainel
        get() =
                when {
                    temaCustomizado -> ModoDoPainel.TEMA_CUSTOMIZADO
                    informacoes || circulo -> ModoDoPainel.ORIGINAL_COM_INFORMACOES
                    else -> ModoDoPainel.DESLIGADO
                }

    /** Para onde a projeção da central vai com estas escolhas; `null` = não há onde projetar. */
    val destinoDaProjecao: DestinoDaProjecao?
        get() =
                when {
                    temaCustomizado -> DestinoDaProjecao.VAO_CENTRAL
                    circulo -> DestinoDaProjecao.CIRCULO_DIREITO
                    else -> null
                }

    /** Tudo o que estas escolhas gravam: as quatro chaves do [modo] e as duas do painel de fábrica. */
    fun preferencias(): Map<String, Boolean> =
            ModoDoPainel.preferenciasDe(modo) +
                    mapOf(
                            Preferencias.ORIGINAL_PANEL_INFO.chave to informacoes,
                            Preferencias.ORIGINAL_PANEL_CIRCLE_PROJECTION.chave to circulo
                    )

    companion object {
        /**
         * Monta as escolhas a partir do [modo] gravado e das duas chaves novas, que antes da 6.0
         * não existiam (`null`).
         *
         * Ausentes contam como ligadas: "Original + informações" mostrava a temperatura, a placa e
         * o menu no círculo, então quem estava nele continua vendo as informações e ganha a
         * projeção no lugar do menu. Com o painel desligado nada aparece aceso — nada do OPEN GW
         * estava no painel, e é isso que a tela tem de dizer.
         *
         * Painel de fábrica com as duas gravadas desligadas não sai de Telas (desligar a última
         * desliga a camada junto); se aparecer, vale o de antes da 6.0 em vez de uma camada
         * ligada que a tela mostraria apagada.
         */
        fun de(
                modo: ModoDoPainel,
                informacoesGravadas: Boolean?,
                circuloGravado: Boolean?
        ): PainelEscolhido {
            val informacoes = informacoesGravadas ?: true
            val circulo = circuloGravado ?: true
            return when (modo) {
                ModoDoPainel.DESLIGADO -> PainelEscolhido(false, false, false)
                ModoDoPainel.ORIGINAL_COM_INFORMACOES ->
                        if (informacoes || circulo) PainelEscolhido(false, informacoes, circulo)
                        else PainelEscolhido(false, true, true)
                ModoDoPainel.TEMA_CUSTOMIZADO -> PainelEscolhido(true, informacoes, circulo)
            }
        }

        fun ler(prefs: SharedPreferences): PainelEscolhido {
            fun gravado(chave: Preferencia): Boolean? =
                    if (prefs.contains(chave.chave)) prefs.getBoolean(chave.chave, true) else null
            return de(
                    ModoDoPainel.ler(prefs),
                    gravado(Preferencias.ORIGINAL_PANEL_INFO),
                    gravado(Preferencias.ORIGINAL_PANEL_CIRCLE_PROJECTION)
            )
        }

        fun aplicar(
                context: Context,
                prefs: SharedPreferences,
                anterior: PainelEscolhido,
                novo: PainelEscolhido
        ) {
            prefs.edit {
                novo.preferencias().forEach { (chave, valor) -> putBoolean(chave, valor) }
                if (novo.modo == ModoDoPainel.DESLIGADO) {
                    // O interruptor mestre antigo também desligava isto ao apagar tudo.
                    putBoolean(Preferencias.ENABLE_ODOMETER_AND_REVISION.chave, false)
                }
            }
            if (ModoDoPainel.voltaAoTemaDeFabrica(anterior.modo, novo.modo)) {
                TemaEmbutido.restaurar(prefs)
            }
            // Ligar o tema customizado sem tema escolhido não pode desenhar o painel com o
            // embutido, que saiu da lista na 6.8: vai para o tema padrão.
            TemaDoPainel.garantirTemaDoPainelInteiro(context, prefs)
        }
    }
}
