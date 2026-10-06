// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.licenca

import br.com.deepgwt.nucleo.DeepGwt
import br.com.deepgwt.veiculo.Comandos
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import br.com.deepgwt.ajustes.AjustesRemotos
import br.com.deepgwt.managers.CastaDoTema
import br.com.deepgwt.managers.TemaDoPainel
import br.com.deepgwt.ui.screens.PainelEscolhido

/**
 * O que acontece quando o nível muda (Alpha 8.0): cada serviço que perdeu a vez desliga e devolve o
 * estado de fábrica; cada um que ganhou volta a obedecer às preferências.
 *
 * AS PREFERÊNCIAS NÃO MUDAM. O ASD, o HUD, a luz, o Stream Deck, gestos, reverb e o indicador de CPU
 * perguntam ao [Portao] na hora de decidir se sobem — basta pedir que se reavaliem. O painel e a
 * tela inicial são diferentes: o que está no ar É a preferência (o tema ativo, o papel de tela
 * inicial do Android). Esses dois guardam o que tiraram em `licenca.xml` ([GUARDADO_TEMA_CUSTOMIZADO],
 * [GUARDADO_TEMA]) e repõem quando o nível voltar — uma licença nova devolve o carro como estava.
 *
 * Só se mexe no que mudou de lado entre os dois níveis: cair de Signature para Premium não reinicia
 * o ASD de ninguém.
 */
object MudancaDeNivel {
    private const val TAG = "MudancaDeNivel"

    const val GUARDADO_TEMA_CUSTOMIZADO = "guardado.painelTemaCustomizado"
    const val GUARDADO_TEMA = "guardado.temaDoPainel"

    /** Recursos que mudaram de lado entre [anterior] e [novo]. Pura (testada). */
    fun afetados(anterior: Nivel, novo: Nivel): Set<Recurso> =
        Recurso.entries.filter { anterior.alcanca(it.nivel) != novo.alcanca(it.nivel) }.toSet()

    fun aplicarEmSegundoPlano(anterior: Nivel, novo: Nivel, atrasoMs: Long = 0) {
        Thread({
            if (atrasoMs > 0) Thread.sleep(atrasoMs)
            runCatching { aplicar(anterior, novo) }.onFailure { Log.e(TAG, "Falha ao ajustar ao nível ${novo.id}", it) }
        }, "mudanca-de-nivel").start()
    }

    private fun aplicar(anterior: Nivel, novo: Nivel) {
        val afetados = afetados(anterior, novo)
        // O instalador que espera mudança em `/ajustes` relê os cadeados.
        runCatching { AjustesRemotos.tocar(); naPrincipal { AjustesRemotos.geracaoDaTela.intValue += 1 } }
        if (afetados.isEmpty()) return
        val contexto = DeepGwt.contexto
        val prefs = DeepGwt.contextoProtegido.getSharedPreferences("deepgwt_prefs", Context.MODE_PRIVATE)
        Log.w(TAG, "${anterior.id} → ${novo.id}: ${afetados.joinToString { it.id }}")

        // Tela, painel e camadas: na principal, como a tela do carro faz.
        naPrincipal {
            if (afetados.any { it == Recurso.TEMAS_EMBUTIDOS || it == Recurso.TEMAS_XTRA || it == Recurso.EDITOR_DE_TEMAS }) {
                passo("painel") { ajustarPainel(contexto, prefs) }
            }
            if (Recurso.HUD in afetados) passo("HUD") {
                val hud = br.com.deepgwt.hud.HudOverlayController
                if (hud.isEnabled()) hud.restoreIfEnabled() else hud.hide()
            }
            if (Recurso.SISTEMA in afetados) passo("indicador de CPU") {
                br.com.deepgwt.barra.Barra.recarregarPreferencias()
            }
            if (Recurso.AUDIO in afetados) passo("reverb") {
                br.com.deepgwt.managers.AudioEffectsManager.applyFromPrefs()
            }
        }

        if (Recurso.ASD in afetados) passo("ASD") {
            br.com.deepgwt.managers.asd.AsdManager.reconciliarComAsPreferencias()
        }
        if (Recurso.LUZ_AMBIENTE in afetados) passo("luz ambiente") {
            br.com.deepgwt.luz.LuzAmbiente.reaplicar(contexto)
        }
        if (Recurso.STREAM_DECK in afetados) passo("Stream Deck") {
            val servico = br.com.deepgwt.streamdeck.StreamDeckService
            if (br.com.deepgwt.streamdeck.StreamDeckSettings.isEnabled()) servico.start(contexto)
            else servico.stop(contexto)
            br.com.deepgwt.botoesextra.LeitorDeTeclasExternas.startIfEnabled()
        }
        if (Recurso.GESTOS in afetados || Recurso.MACROS in afetados) passo("gestos") {
            br.com.deepgwt.managers.QuickGesturesManager.startIfEnabled()
        }
        if (Recurso.LAUNCHER_3D in afetados) passo("tela inicial") { ajustarTelaInicial(contexto) }
        // Sem o nível, os coringas voltam à função do carro; com ele de volta, retomam.
        if (Recurso.CORINGAS in afetados) passo("coringas do volante") {
            br.com.deepgwt.carro.CoringasDoVolante.reconciliar("nível")
        }
        // Apps de sistema e o microfone do "Olá Haval": shell, demora — por último.
        if (Recurso.SISTEMA in afetados) passo("apps do sistema") { Comandos.garantirAppsDesativados() }
    }

    private fun licenca(): SharedPreferences =
        DeepGwt.contextoProtegido.getSharedPreferences(Portao.ARQUIVO, Context.MODE_PRIVATE)

    /**
     * O painel guarda o que tirou. Sem Sport: o tema customizado desliga (fica o painel original com
     * as informações e o círculo, que são Drive). Sem Premium: um tema XTRA (ou o rascunho do
     * editor) dá lugar ao tema padrão, que vem no app.
     */
    private fun ajustarPainel(contexto: Context, prefs: SharedPreferences) {
        val guardado = licenca()

        if (TemaDoPainel.rascunhoAtivo(prefs) && !Portao.permite(Recurso.EDITOR_DE_TEMAS)) {
            TemaDoPainel.encerrarRascunho(contexto, prefs)
        }

        val escolhas = PainelEscolhido.ler(prefs)
        if (escolhas.temaCustomizado && !Portao.permite(Recurso.TEMAS_EMBUTIDOS)) {
            guardado.edit()
                .putBoolean(GUARDADO_TEMA_CUSTOMIZADO, true)
                .putString(GUARDADO_TEMA, guardado.getString(GUARDADO_TEMA, null) ?: TemaDoPainel.ativo(prefs))
                .commit()
            AjustesRemotos.trocarEscolhasDoPainel(prefs, escolhas, escolhas.copy(temaCustomizado = false))
            Log.w(TAG, "tema customizado do painel guardado e desligado")
            return
        }
        if (!escolhas.temaCustomizado && Portao.permite(Recurso.TEMAS_EMBUTIDOS) &&
            guardado.getBoolean(GUARDADO_TEMA_CUSTOMIZADO, false)
        ) {
            guardado.edit().remove(GUARDADO_TEMA_CUSTOMIZADO).commit()
            AjustesRemotos.trocarEscolhasDoPainel(prefs, escolhas, escolhas.copy(temaCustomizado = true))
            Log.w(TAG, "tema customizado do painel reposto")
        }
        if (!PainelEscolhido.ler(prefs).temaCustomizado) return

        val ativo = TemaDoPainel.ativo(prefs)
        if (ativo.isNotBlank() && TemaDoPainel.castaDe(ativo) == CastaDoTema.XTRA && !Portao.permite(Recurso.TEMAS_XTRA)) {
            guardado.edit().putString(GUARDADO_TEMA, ativo).commit()
            TemaDoPainel.aplicarPorPasta(contexto, prefs, TemaDoPainel.PASTA_PADRAO)
            Log.w(TAG, "tema XTRA '$ativo' guardado; painel no ${TemaDoPainel.PASTA_PADRAO}")
            return
        }
        val paraRepor = guardado.getString(GUARDADO_TEMA, null) ?: return
        val permitido = TemaDoPainel.castaDe(paraRepor) == CastaDoTema.LEGACY || Portao.permite(Recurso.TEMAS_XTRA)
        if (!permitido) return
        guardado.edit().remove(GUARDADO_TEMA).commit()
        if (paraRepor.isNotBlank() && paraRepor != ativo) {
            val r = TemaDoPainel.aplicarPorPasta(contexto, prefs, paraRepor)
            Log.w(TAG, "tema '$paraRepor' reposto: $r")
        }
    }

    /**
     * A tela inicial é um papel do Android, não uma preferência lida na hora: sem Sport o launcher 3D
     * sai e a de fábrica volta, SEM apagar a escolha gravada — a guarda ([Portao] em
     * `GuardaDoLauncher3D.ativa`) para de reafirmar, e o nível voltando reaplica o 3D.
     */
    private fun ajustarTelaInicial(contexto: Context) {
        val hc = br.com.deepgwt.managers.HomeLauncherController
        if (Portao.permite(Recurso.LAUNCHER_3D)) {
            if (hc.launcher3DFixo()) hc.setHome(hc.VIEWER_PACKAGE, contexto, gravarEscolha = false, trazerParaFrente = false)
            return
        }
        if (hc.currentHomePackage(contexto) == hc.VIEWER_PACKAGE) {
            val fabrica = hc.telaDeFabrica(contexto) ?: return Unit.also { Log.w(TAG, "sem tela de fábrica para voltar") }
            hc.setHome(fabrica, contexto, gravarEscolha = false, trazerParaFrente = false)
                .onFailure { Log.w(TAG, "não voltou à tela de fábrica: ${it.message}") }
        }
    }

    private inline fun passo(nome: String, bloco: () -> Unit) {
        runCatching(bloco).onFailure { Log.e(TAG, "Falha ao ajustar \"$nome\" ao nível", it) }
    }

    private fun naPrincipal(bloco: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) bloco() else Handler(Looper.getMainLooper()).post(bloco)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
