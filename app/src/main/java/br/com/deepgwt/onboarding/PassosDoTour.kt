// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.onboarding

/**
 * Um passo do tour guiado (Alpha 7.9).
 *
 * [secao] é o título de um item do menu (`ItemDoMenu`): o tour abre aquela seção antes de
 * mostrar o balão; null volta para a tela inicial do app. [alvo] é o que fica em destaque — um id
 * marcado com `Modifier.alvoDoTour` —; null é um cartão no meio da tela, sem destaque.
 */
data class PassoDoTour(
    val id: String,
    val titulo: String,
    val texto: String,
    val secao: String? = null,
    val alvo: String? = null
)

/**
 * O roteiro do tour. Só seções que aparecem sempre no menu — nunca as que dependem de uma
 * chave (ADAS só aparece com o uso avançado). O teste `PassosDoTourTest` confere os
 * títulos contra o `ItensDoMenu.kt`.
 */
object PassosDoTour {
    /** A tela inicial do app, nos três estilos. */
    const val ALVO_MENU = "menu"

    /** A seção aberta, inteira. Um destaque que cobre quase a tela vira um véu leve. */
    const val ALVO_SECAO = "secao"

    /** O cartão de tamanho em Interface do DeepGWT. */
    const val ALVO_TAMANHO = "interface.tamanho"

    val TODOS: List<PassoDoTour> =
        listOf(
            PassoDoTour(
                "inicio",
                "Bem-vindo ao tour",
                "Vamos passar pelas partes principais do DeepGWT. Leva um minuto, e dá para sair quando quiser."
            ),
            PassoDoTour(
                "menu",
                "A tela inicial do app",
                "Cada item abre uma parte do DeepGWT. A seta no alto de cada tela volta para cá.",
                alvo = ALVO_MENU
            ),
            PassoDoTour(
                "configuracoes",
                "Configurações",
                "Segurança e avisos (como o som de alerta a pedestres), a barra inferior, gestos e o que o " +
                    "DeepGWT faz sozinho ao ligar, desligar ou trancar o carro.",
                secao = "Configurações",
                alvo = ALVO_SECAO
            ),
            PassoDoTour(
                "telas",
                "Telas",
                "O que aparece atrás do volante: informações extras e o círculo no painel de fábrica, com " +
                    "os relógios, os temas que desenham o painel inteiro e o head-up display.",
                secao = "Telas",
                alvo = ALVO_SECAO
            ),
            PassoDoTour(
                "tela-inicial",
                "Tela Inicial",
                "Escolha se o carro abre na tela de fábrica ou no launcher 3D. Voltar para a de fábrica é um toque.",
                secao = "Tela Inicial",
                alvo = ALVO_SECAO
            ),
            PassoDoTour(
                "asd",
                "Som do motor (ASD)",
                "O som de motor tocado pelos alto-falantes, que acompanha a rotação, o acelerador e as marchas.",
                secao = "ASD",
                alvo = ALVO_SECAO
            ),
            PassoDoTour(
                "audio",
                "Áudio",
                "O equalizador e o processamento do amplificador do carro, e efeitos como o reverb.",
                secao = "Áudio",
                alvo = ALVO_SECAO
            ),
            PassoDoTour(
                "recursos",
                "Recursos XTRA",
                "Perfis de motorista, macros, DVR, luz ambiente e botões extra (Stream Deck ou teclado).",
                secao = "Recursos XTRA",
                alvo = ALVO_SECAO
            ),
            PassoDoTour(
                "interface",
                "Tamanho e estilo",
                "Aqui você muda o tamanho da interface e das letras e o estilo do app. É também onde se " +
                    "refaz a configuração inicial ou este tour.",
                secao = "Interface do DeepGWT",
                alvo = ALVO_TAMANHO
            ),
            PassoDoTour(
                "informacoes",
                "Informações",
                "A versão instalada, o nível e a licença do DeepGWT (horas, inserir código, o que cada nível traz), o estado do app e o espaço livre na central.",
                secao = "Informações",
                alvo = ALVO_SECAO
            ),
            PassoDoTour(
                "barra",
                "Barra inferior",
                "A barra do DeepGWT fica por cima de todas as telas do carro, com ar-condicionado, volume " +
                    "e atalhos. Ela liga, desliga e muda de altura em Configurações."
            ),
            PassoDoTour(
                "instalador",
                "No computador",
                "O instalador do DeepGWT conecta pelo Wi-Fi do carro: muda os ajustes, envia os temas XTRA, " +
                    "espelha a tela e atualiza o app."
            ),
            PassoDoTour(
                "fim",
                "Pronto",
                "Para ver este tour de novo: Interface do DeepGWT → Ver o tour."
            )
        )

    /** Os passos com estes ids, na ordem do roteiro. Ids desconhecidos são ignorados. */
    fun porIds(ids: Collection<String>): List<PassoDoTour> = TODOS.filter { it.id in ids }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
