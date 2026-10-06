// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.hud

/**
 * As páginas do HUD e a posição atual.
 *
 * NÃO EXISTE MAIS "ABRIR O MENU"
 *
 * A versão anterior tinha um menu modal: era preciso segurar um botão coringa por 3 s para entrar
 * nele, e o botão precisava ser APRENDIDO porque o coringa não tem keycode fixo. Isso não funcionou
 * no carro. Agora não há estado de "dentro" e "fora": o HUD sempre mostra uma das páginas, começando
 * pela de velocidade, e os direcionais do volante andam entre elas. Não há como ficar preso num modo
 * nem como não conseguir entrar nele.
 *
 * CINCO PÁGINAS, NA ORDEM DO RELATÓRIO
 *
 * A primeira é a que o motorista vê 99% do tempo (velocidade + limite da via). As outras quatro são
 * um assunto cada — 480x240 físicos não comportam dois assuntos ao mesmo tempo de forma legível.
 *
 * NÃO VOLTA SOZINHA PARA A PRIMEIRA
 *
 * Trocar de página é um ato deliberado do motorista no volante. Um tempo limite que devolvesse a
 * velocidade sozinho tornaria impossível deixar o HUD na página de mídia por mais de alguns segundos
 * — e quem decide o que fica no para-brisa é quem dirige. O que interrompe qualquer página é ALERTA,
 * e só ele (ver [HudTelemetry.alertas]).
 */
object HudPaginas {

    enum class Pagina(val titulo: String) {
        /** Padrão: velocidade atual + SLI. */
        VELOCIDADE("Velocidade"),
        /** Rádio, música, capa e informações de mídia. */
        MIDIA("Mídia"),
        /** Consumo, autonomia, combustível e bateria. */
        CONSUMO("Consumo"),
        /** Potência, torque, rotação e tração. */
        POWERTRAIN("Powertrain"),
        /** Odômetro, temperaturas, marcha e pneus. */
        VEICULO("Veículo"),
    }

    /**
     * As páginas que o volante alcança agora, na ordem escolhida em Telas.
     *
     * NÃO É MAIS `Pagina.entries`
     *
     * Ordem e quais páginas entram são do usuário ([HudSettings.ordemPaginas],
     * [HudSettings.paginasAtivas]). O enum continua definindo o que EXISTE; esta lista define o que
     * está no rodízio hoje. Ela nunca volta vazia: [HudSettings.ajustarPaginaAtiva] recusa desligar
     * a última, e a leitura da preferência trata "vazio" como "todas".
     */
    val visiveis: List<Pagina>
        get() {
            HudSettings.load()
            val lista = HudSettings.ordemPaginas.filter { it in HudSettings.paginasAtivas }
            return lista.ifEmpty { listOf(Pagina.VELOCIDADE) }
        }

    @Volatile
    var pagina: Pagina = Pagina.VELOCIDADE
        private set

    /** Posição da página atual entre as visíveis, ou 0 quando ela acabou de sair do rodízio. */
    val indice: Int
        get() = visiveis.indexOf(pagina).coerceAtLeast(0)

    val total: Int
        get() = visiveis.size

    /**
     * Posição de [pagina] no rodízio, ou -1 quando ela está desligada.
     *
     * É o que a MINIATURA da tela de Telas usa para acender o pontinho certo: ela desenha uma
     * página escolhida, e não a que está no ar, então [indice] — que responde sempre sobre a
     * página no ar — daria o ponto errado. O -1 é informação, não falha: uma página fora do
     * rodízio desenha a fileira de pontos com nenhum aceso, que é exatamente o que ela é.
     */
    fun indiceDe(pagina: Pagina): Int = visiveis.indexOf(pagina)

    fun proxima() = mover(1)

    fun anterior() = mover(-1)

    /** Volta para a primeira página visível. Usada quando a camada sobe do zero. */
    fun reiniciar() {
        pagina = visiveis.first()
    }

    /**
     * Vai direto para [destino], sem passar pelas do meio. É por aqui que a tela da central
     * escolhe o que o HUD mostra — inclusive com o controle pelo volante desligado, que é
     * justamente o caso em que ela é o único jeito de trocar.
     *
     * Escolher uma página que não está no rodízio a mostra mesmo assim: o pedido foi explícito, e
     * recusar calado seria pior do que mostrar. O próximo passo do volante volta para as visíveis.
     */
    fun irPara(destino: Pagina) {
        if (pagina == destino) return
        pagina = destino
        HudOverlayController.requestRedraw()
    }

    /**
     * Chamada quando a ordem ou o conjunto de páginas ativas muda.
     *
     * Se a página que está no ar saiu do rodízio, o HUD ficaria mostrando algo que o volante não
     * alcança mais e sem indicação de onde está. Nesse caso cai para a primeira visível; se ela
     * continua visível, não mexe — trocar a página debaixo do motorista porque outra foi
     * reordenada seria movimento sem pedido.
     */
    fun reconciliar() {
        val lista = visiveis
        if (pagina !in lista) {
            pagina = lista.first()
        }
        HudOverlayController.requestRedraw()
    }

    /**
     * Anda [passo] páginas VISÍVEIS, dando a volta nas duas pontas.
     *
     * O resto de Kotlin herda o sinal do dividendo, então `(0 - 1) % 5` é `-1` e não `4`; o `+ n`
     * antes do segundo resto é o que faz a volta funcionar para trás.
     */
    fun mover(passo: Int) {
        val lista = visiveis
        val n = lista.size
        if (n == 0) return
        val novo = ((indice + passo) % n + n) % n
        if (lista[novo] == pagina) return
        pagina = lista[novo]
        HudOverlayController.requestRedraw()
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
