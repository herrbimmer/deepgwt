// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.services

import android.app.Application
import android.app.Service
import android.content.Context
import android.util.Log
import br.com.deepgwt.app.ExtrasDoServico
import br.com.deepgwt.app.LigacoesDoApp
import br.com.deepgwt.app.LigacoesDoServico
import br.com.deepgwt.app.PartidaDoShizuku
import br.com.deepgwt.barra.Barra
import br.com.deepgwt.luz.LuzAmbiente
import br.com.deepgwt.nucleo.DeepGwt
import br.com.deepgwt.nucleo.Preferencias
import br.com.deepgwt.nucleo.shizuku.SubidaDoShizuku
import br.com.deepgwt.painel.TemasInstalados
import br.com.deepgwt.rede.FirewallDoCarro
import br.com.deepgwt.rede.RoteadorDoHotspot
import br.com.deepgwt.telas.Espelhamento
import br.com.deepgwt.telas.LancadorDeApps
import br.com.deepgwt.BuildConfig
import br.com.deepgwt.UpdateActivity
import br.com.deepgwt.diagnostics.CalibracaoDoPainel
import br.com.deepgwt.hud.HudOverlayController
import br.com.deepgwt.macros.MacroEngine
import br.com.deepgwt.managers.AudioEffectsManager
import br.com.deepgwt.managers.GwLinkServer
import br.com.deepgwt.managers.LimpezaDoAssistenteDeVoz
import br.com.deepgwt.managers.PermissionBootstrap
import br.com.deepgwt.managers.QuickGesturesManager
import br.com.deepgwt.managers.StealthModeController
import br.com.deepgwt.managers.TemaDoPainel
import br.com.deepgwt.managers.asd.AsdManager
import br.com.deepgwt.projection.ScreenProjectionController

/**
 * Liga o esqueleto novo (br.com.deepgwt.app, 3.9b) aos módulos do app: o que a Application e o
 * serviço principal herdados chamavam, na mesma ordem. Cada gancho chama módulos prontos; nada de
 * lógica aqui.
 */
object LigacoesDoCarro : LigacoesDoApp {

    private const val TAG = "LigacoesDoCarro"

    private fun prefs() = DeepGwt.contextoProtegido.getSharedPreferences("deepgwt_prefs", Context.MODE_PRIVATE)

    override fun aoCriarOApp(app: Application) {
        passo("lançador") { LigacoesDoLancador.ligar(app) }
        // Barra inferior (br.com.deepgwt.barra, 3.7): integrações e nomes dos componentes (D-1),
        // antes de qualquer componente da barra subir.
        passo("barra") { LigacoesDaBarra.ligar() }
        passo("atalhos padrão") { LancadorDeApps.semearAtalhosPadrao() }
        // Temas do painel: poda e semeia os do APK numa thread própria. Quando terminar (na mesma
        // thread), o rascunho esquecido e a calibração vencida voltam ao tema de antes e um tema
        // ativo fora do contrato volta ao embutido — o projetor recarrega pela preferência.
        passo("temas do painel") {
            val prefsDoPainel = prefs()
            val contexto = app.applicationContext
            TemasInstalados.iniciar(contexto) {
                TemaDoPainel.garantirTemaDoPainelInteiro(contexto, prefsDoPainel)
            }
            TemasInstalados.quandoPronto {
                passo("rascunho esquecido") { TemaDoPainel.encerrarRascunhoEsquecido(contexto, prefsDoPainel) }
                passo("calibração do painel") { CalibracaoDoPainel.conferirNaSubida(contexto, prefsDoPainel) }
                TemasInstalados.sanearTemaAtivo()
            }
        }
        // Alpha 9.2: o assistente de voz saiu; a chave do Gemini não fica no carro.
        passo("limpeza do assistente") { LimpezaDoAssistenteDeVoz.executar() }
        // Luz ambiente (br.com.deepgwt.luz): mídia tocando (barra nova) e cores da capa (fundo do álbum).
        passo("luz") { LigacoesDaLuz.ligar() }
        // O motor do veículo (3.3b–e) recebe os adaptadores antes de qualquer partida (antes era na
        // criação do serviço; uma vez por processo basta).
        passo("motor") { LigacoesDoMotor.instalar(app) }
        // Subida do Shizuku (3.13): quem pede o servidor à central, antes de o serviço partir.
        SubidaDoShizuku.iniciador = LigacoesDoShizuku
    }

    override fun aoReceberPartida(contexto: Context, acao: String?) {
        // O servidor do Shizuku não sobrevive a um reboot da central: rede de segurança da partida fria.
        SubidaDoShizuku.redeDeSegurancaNaPartida(contexto)
    }

    override fun reabrirTelaDeAtualizacao(contexto: Context) {
        contexto.startActivity(UpdateActivity.postInstallIntent(contexto))
    }

    override val servico: LigacoesDoServico get() = Servico

    private object Servico : LigacoesDoServico {

        override val shizuku: PartidaDoShizuku
            get() = if (BuildConfig.SIMULATOR_MODE) PartidaDoShizuku.SEMPRE_ACEITO else SubidaDoShizuku

        override fun aoIniciar(servico: Service) {
            passo("barra") { garantirBarra(servico, "service-start") }
            // Gestos de 3 dedos independem da barra estar ligada.
            passo("gestos") { QuickGesturesManager.startIfEnabled() }
            passo("botões extra") { br.com.deepgwt.botoesextra.LeitorDeTeclasExternas.startIfEnabled() }
            // Macros: sobem sempre (registram as chaves de gatilho; o "Executar" precisa da fila).
            passo("macros") { MacroEngine.start() }
            // Camada do HUD: quem ligou espera encontrar ligada depois de reiniciar o carro.
            passo("HUD") { HudOverlayController.restoreIfEnabled() }
            passo("luz") { LuzAmbiente.iniciarSeLigada(servico) }
            passo("efeitos de áudio") { AudioEffectsManager.start() }
            passo("GwLink") { GwLinkServer.start() }
        }

        override fun aoIniciarDeNovo(servico: Service) {
            passo("vigia do ícone do CarPlay") { Espelhamento.iniciarVigiaDoIconeDoCarPlay() }
            passo("barra") { garantirBarra(servico, "already-running") }
            passo("luz") { LuzAmbiente.iniciarSeLigada(servico) }
            passo("efeitos de áudio") { AudioEffectsManager.start() }
            passo("ASD") { AsdManager.start() }
            passo("GwLink") { GwLinkServer.start() }
            passo("macros") { MacroEngine.start() }
        }

        override fun comShizuku(servico: Service) {
            // O leitor de gestos precisa do shell privilegiado: rechamar aqui é o que garante o
            // toque com 4 dedos na primeira partida do dia. Idempotente.
            passo("gestos") { QuickGesturesManager.startIfEnabled() }
            passo("botões extra") { br.com.deepgwt.botoesextra.LeitorDeTeclasExternas.startIfEnabled() }
            // Primeira janela com shell privilegiado: permissões de uma instalação nova.
            passo("permissões") { PermissionBootstrap.runOnceInBackground(servico) }
        }

        override fun antesDaPartida(servico: Service) {
            passo("vigias do espelhamento") {
                Espelhamento.iniciarVigiaDoCarPlayNoPainel()
                Espelhamento.iniciarPartidaDoCarPlay()
            }
        }

        override fun depoisDaPartida(servico: Service) {
            passo("ASD") { AsdManager.start() }
            passo("vigia do ícone do CarPlay") { Espelhamento.iniciarVigiaDoIconeDoCarPlay() }
            passo("foco do volante") { Espelhamento.iniciarFocoDoVolante() }
        }

        override fun depoisDaPartidaSimulada(servico: Service) {
            passo("efeitos de áudio") { AudioEffectsManager.start() }
            passo("ASD") { AsdManager.start() }
            passo("GwLink") { GwLinkServer.start() }
        }

        override fun aoDestruir(servico: Service) {
            // A projeção da central segura uma autorização de captura que não some sozinha.
            passo("projeção") { ScreenProjectionController.liberarTudo() }
        }

        override val extras: ExtrasDoServico get() = Extras

        /** Modo sorrateiro, interruptor da barra e permissão de sobreposição, nessa ordem. */
        private fun garantirBarra(servico: Service, motivo: String) {
            if (StealthModeController.isActive(servico)) {
                Log.d(TAG, "modo sorrateiro: barra escondida ($motivo)")
                return
            }
            if (!prefs().getBoolean(Preferencias.ENABLE_PERSISTENT_BOTTOM_BAR.chave, false)) {
                Log.d(TAG, "barra persistente desligada ($motivo)")
                return
            }
            if (!android.provider.Settings.canDrawOverlays(servico)) {
                Log.e(TAG, "sem permissão de sobreposição: barra não sobe ($motivo)")
                return
            }
            Barra.mostrar(servico)
        }
    }

    /** Extras de rede (3.10c): firewall liberado e roteador do hotspot (br.com.deepgwt.rede). */
    private object Extras : ExtrasDoServico {

        override fun comShizuku() {
            passo("firewall") {
                if (FirewallDoCarro.liberarEntradaESaida()) Log.w(TAG, "Firewall liberado")
                else Log.e(TAG, "Não deu para liberar o firewall")
                FirewallDoCarro.iniciarVigia()
            }
        }

        override fun depoisDaPartida() {
            passo("roteador") { RoteadorDoHotspot.aoFicarPronto() }
        }
    }

    private fun passo(nome: String, bloco: () -> Unit) {
        try {
            bloco()
        } catch (e: Exception) {
            Log.e(TAG, "'$nome' falhou", e)
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
