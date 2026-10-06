// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.hud

import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import br.com.deepgwt.circulo.CirculoDoPainelController
import br.com.deepgwt.managers.ToqueDoVolante

/**
 * O volante navegando o HUD.
 *
 * ## ALPHA 9.5 — SEGURAR OK, E SÓ ENTÃO AS SETAS
 *
 * Até a 9.4, com o HUD no ar, ↑/↓ trocavam a página do para-brisa direto — e deixavam de chegar
 * ao painel. Pedido do dono: "o controle das telas do HUD não deve ser feito pelas setas
 * puramente". Agora:
 *
 *  • fora do controle, as setas são do painel, como de fábrica — este objeto não pega nada;
 *  • SEGURAR OK entra no Controle do HUD ([ControleDoHud]): o painel é coberto por uma cortina
 *    "Controle do HUD ativado" e ↑/↓ trocam as páginas;
 *  • SEGURAR OK de novo sai, e as setas voltam ao painel.
 *
 * Vale em qualquer painel (tema customizado, fábrica, fábrica com o Círculo do painel). Por isso
 * este objeto é consultado ANTES do círculo no `ServiceManager`. Duas exceções de quem já usava o
 * OK segurado: a página Central do Círculo do painel (projeta a última área, Alpha 7.7) continua
 * com ele, e com o HUD fora do ar ele segue sendo o liga/desliga do mãos-livres.
 *
 * ## O QUE VEIO DAS VERSÕES ANTERIORES
 *
 *  • 6.1: o `inputservice` desta central entrega o volante como SOLTOU (às vezes DOWN + UP); vale o
 *    primeiro sinal de cada aperto ([ToqueDoVolante]).
 *  • ←/→ nunca são do HUD: o carro troca de card com elas por conta própria, e é com elas que o
 *    `ServiceManager` acompanha o card do carro.
 *  • "Controles pelo volante" desligado em Telas ([HudSettings.wheelControlEnabled]): o OK
 *    segurado não entra no controle e o HUD fica na página escolhida na central.
 */
object HudWheelInput {

    private const val TAG = "HudWheelInput"

    // Keycodes do bloco de navegação do volante, os mesmos que o ServiceManager já trata.
    internal const val VOLANTE_CIMA = 1024
    internal const val VOLANTE_BAIXO = 1025
    internal const val VOLANTE_ESQUERDA = 1026
    internal const val VOLANTE_DIREITA = 1027

    /**
     * Quantas páginas andar com este keycode, ou null quando a tecla não é do HUD.
     *
     * Separado do [handleKeyEvent] para ser testável sem `KeyEvent`, que é classe de plataforma.
     */
    internal fun passoDaTecla(codigo: Int): Int? =
            when (codigo) {
                VOLANTE_CIMA -> -1
                VOLANTE_BAIXO -> 1
                else -> null
            }

    private val toque = ToqueDoVolante()

    internal const val VOLANTE_OK = 1028
    internal const val VOLANTE_VOLTAR = 1030
    internal const val VOLANTE_OK_LONGO = 1037

    /** A tecla, para o [ControleDoHud]. Separado para ser testável sem `KeyEvent`. */
    internal fun teclaDoControle(codigo: Int): TeclaDoControle =
            when (codigo) {
                VOLANTE_CIMA -> TeclaDoControle.CIMA
                VOLANTE_BAIXO -> TeclaDoControle.BAIXO
                VOLANTE_OK -> TeclaDoControle.OK
                VOLANTE_OK_LONGO -> TeclaDoControle.OK_LONGO
                VOLANTE_VOLTAR -> TeclaDoControle.VOLTAR
                else -> TeclaDoControle.OUTRA
            }

    /**
     * Ponto de entrada, chamado do `dispatchKeyEvent` do ServiceManager, antes do círculo.
     *
     * @return true quando o evento foi consumido pelo HUD e não deve seguir para o resto do
     * tratamento (círculo, menu do cluster, hands-free, mídia).
     */
    fun handleKeyEvent(event: KeyEvent): Boolean {
        HudSettings.load()
        val noAr = HudOverlayController.isShowing
        // O HUD saiu do ar ou o controle foi desligado em Telas com a cortina no ar: encerra, e a
        // tecla segue o caminho normal.
        if (ControleDoHud.ativo && (!noAr || !HudSettings.wheelControlEnabled)) {
            ControleDoHud.encerrar(if (!noAr) "HUD fora do ar" else "controle desligado em Telas")
        }
        val tecla = teclaDoControle(event.keyCode)
        if (tecla == TeclaDoControle.OUTRA && !ControleDoHud.ativo) return false
        // Durante o controle, as versões longas de ↑/↓ (1033/1034) também ficam com o HUD: sob a
        // cortina elas mexeriam no painel sem ninguém ver.
        if (tecla == TeclaDoControle.OUTRA) return event.keyCode == 1033 || event.keyCode == 1034
        if (!toque.ehNovoToque(event.keyCode, event.action, SystemClock.uptimeMillis())) {
            // O outro meio de um aperto já tratado (o SOLTOU depois do APERTOU): fica com quem
            // ficou com o primeiro. Sem isto, o SOLTOU do OK segurado que ENCERROU o controle
            // chegaria ao menu do tema, que age no SOLTOU, como um ENTER_LONG.
            // Repetição do APERTOU com a tecla segura também fica — só o SOLTOU libera.
            return synchronized(apertosDoHud) {
                if (event.action == KeyEvent.ACTION_UP) apertosDoHud.remove(event.keyCode)
                else apertosDoHud.contains(event.keyCode)
            }
        }
        val podeEntrar = noAr && HudSettings.wheelControlEnabled &&
                !CirculoDoPainelController.querOkLongoParaProjecao()
        val consumida = ControleDoHud.aoApertar(tecla, podeEntrar)
        if (consumida && event.action == KeyEvent.ACTION_DOWN) {
            synchronized(apertosDoHud) { apertosDoHud.add(event.keyCode) }
        }
        if (consumida) Log.w(TAG, "Controle do HUD ficou com a tecla ${event.keyCode} (ativo=${ControleDoHud.ativo})")
        return consumida
    }

    /** Teclas cujo APERTOU ficou com o HUD: o SOLTOU delas também fica. */
    private val apertosDoHud = HashSet<Int>()
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
