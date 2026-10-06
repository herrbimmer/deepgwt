// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.services

import br.com.deepgwt.rede.ControleDeDadosMoveis
import android.content.ComponentName
import android.content.Context
import android.util.Log
import br.com.deepgwt.painel.Diario
import br.com.deepgwt.painel.PainelDeInstrumentos
import br.com.deepgwt.telas.Espelhamento
import br.com.deepgwt.telas.LancadorDeApps
import br.com.deepgwt.telas.MidiaDoAndroidAuto
import br.com.deepgwt.veiculo.ProjecaoNasTelas
import br.com.deepgwt.veiculo.Veiculo
import br.com.deepgwt.veiculo.automacoes.GanchosDePartida
import br.com.deepgwt.veiculo.painel.EventoDoPainel
import br.com.deepgwt.veiculo.painel.EventosDoPainel
import br.com.deepgwt.veiculo.painel.MenuAntigoDoPainel
import br.com.deepgwt.veiculo.painel.ServicoDoPainel
import br.com.deepgwt.veiculo.sistema.LigacoesDoMotor as LigacoesNovas
import br.com.deepgwt.veiculo.sistema.MotorCompleto
import br.com.deepgwt.veiculo.sistema.PainelEVolante
import br.com.deepgwt.veiculo.sistema.PortaoDeLicenca
import br.com.deepgwt.veiculo.volante.SaidaDasMaosLivres
import br.com.deepgwt.veiculo.volante.TratadorDeTecla
import br.com.deepgwt.veiculo.volante.Volante
import br.com.deepgwt.PrincipalActivity
import br.com.deepgwt.carro.CoringasDoVolante
import br.com.deepgwt.carro.FuncoesDoCarro
import br.com.deepgwt.circulo.CirculoDoPainelController
import br.com.deepgwt.hud.HudWheelInput
import br.com.deepgwt.licenca.Portao
import br.com.deepgwt.licenca.Recurso
import br.com.deepgwt.managers.DriverProfileManager
import br.com.deepgwt.managers.GuardaDeDadosMoveis
import br.com.deepgwt.managers.PowerFlowMapper
import br.com.deepgwt.barra.EstadoDaBarra

/**
 * Liga o motor novo (br.com.deepgwt.veiculo: comandos, automações, painel e volante, sistema; 3.3b–e)
 * aos módulos que ainda são antigos ou nossos fora do pacote: o lançador (Android Auto/CarPlay), o
 * painel projetado, os coringas, o HUD, o círculo, a barra (mãos livres), a licença, a guarda de dados
 * móveis, o perfil do motorista, a consistência de viagem e o diário. Cada adaptador some quando o
 * módulo dele falar com o motor direto.
 */
object LigacoesDoMotor {

    private const val TAG = "LigacoesDoMotor"

    /** Na criação do serviço principal (antes de qualquer partida). Repetir só troca as ligações. */
    @JvmStatic
    fun instalar(contexto: Context) {
        val app = contexto.applicationContext ?: contexto
        ServicoDoPainel.instalar(Projecao, MenuAntigoDoPainel.VAZIO)
        Volante.instalar(
            coringas = TratadorDeTecla { codigo, acao, _ -> CoringasDoVolante.aoReceberTecla(codigo, acao) },
            hud = TratadorDeTecla { _, _, evento -> HudWheelInput.handleKeyEvent(evento) },
            circulo = TratadorDeTecla { _, _, evento -> CirculoDoPainelController.handleKeyEvent(evento) },
            maosLivres = MaosLivres,
            projecao = Projecao,
            menuAntigo = MenuAntigoDoPainel.VAZIO,
        )
        EventosDoPainel.observar(AoPainel)
        ControleDeDadosMoveis.aoMudarControle { MotorCompleto.aoMudarControleDeDadosMoveis(it) }
        MotorCompleto.instalar(
            LigacoesNovas(
                projecao = Projecao,
                ganchos = Ganchos,
                painelEVolante = PainelEVolanteDaFabricante,
                avisoDoArNoMaximo = { ativo -> EventosDoPainel.publicar(EventoDoPainel.ArNoMaximoMudou(ativo)) },
                portao = PortaoDeLicenca { Portao.permite(Recurso.SISTEMA) },
                dadosMoveis = GuardaDeDadosMoveis,
                appNaFrenteDoDisplay0 = { LancadorDeApps.pacoteNoTopo(0) },
                diario = { evento, detalhes -> Diario.registrar(evento, detalhes) },
                componenteDaAcessibilidade =
                    ComponentName(app, ServicoDeAcessibilidade::class.java).flattenToShortString(),
                ouvintesFixos = listOf(MapeadorDeFluxo),
            )
        )
    }

    /** O mapeador de fluxo de potência ouve o carro a vida toda do processo. */
    private val MapeadorDeFluxo = Veiculo.Ouvinte { chave, valor -> PowerFlowMapper.onSignal(chave, valor) }

    // ── Android Auto e CarPlay (lançador, 3.8) ──

    private object Projecao : ProjecaoNasTelas {
        override fun androidAutoNoDisplay(display: Int) = Espelhamento.androidAutoNoDisplay(display)

        override fun carPlayNoDisplay(display: Int) = Espelhamento.carPlayNoDisplay(display)

        override fun guardarCarPlayNoPainel(motivo: String) = Espelhamento.guardarCarPlayNoPainel(motivo)

        override fun guardarAndroidAutoNoPainelAposJanelaNativa(motivo: String) =
            Espelhamento.guardarAndroidAutoNoPainelAposJanelaNativa(motivo)

        override fun pulsoDoAndroidAutoDuranteJanelaNativa(motivo: String) =
            Espelhamento.pulsoDoAndroidAutoDuranteJanelaNativa(motivo)

        override fun pulsoDoAndroidAutoAoSairDaJanelaNativa(motivo: String) =
            Espelhamento.pulsoDoAndroidAutoAoSairDaJanelaNativa(motivo)

        override fun protegerCartaoDoArNaTransicao() = Espelhamento.protegerCartaoDoArNaTransicao()

        override fun teclaDoVolante(tecla: Int, acao: Int) = MidiaDoAndroidAuto.teclaDoVolante(tecla, acao)

        override fun comandoDeMidiaDoPainel(comando: Int) = MidiaDoAndroidAuto.comandoDeMidiaDoPainel(comando)

        override fun teclaMereceDiagnostico(tecla: Int) = MidiaDoAndroidAuto.teclaMereceDiagnostico(tecla)

        override fun mensagemDoPainelMereceDiagnostico(mensagem: Int) =
            MidiaDoAndroidAuto.mensagemDoPainelMereceDiagnostico(mensagem)
    }

    // ── O que a partida chama de fora do motor ──

    private object Ganchos : GanchosDePartida {
        override fun funcoesDoCarro() = FuncoesDoCarro.iniciar()

        override fun perfilAoLigarOCarro() = DriverProfileManager.aoLigarOCarro()

        /** Na principal (o motor garante): o painel de instrumentos novo (3.6). */
        override fun iniciarProjetores() = LigacoesDoPainel.iniciar()

        override fun iniciarConsistenciaDeViagem() = Unit
    }

    // ── Serviço do painel e serviço de teclas da fabricante ──

    /** Desligar: o volante desregistra antes do painel (senão cada tecla é tratada duas vezes). */
    private object PainelEVolanteDaFabricante : PainelEVolante {
        override fun ligar(contexto: Context) {
            ServicoDoPainel.ligar(contexto)
            Volante.ligar(contexto)
        }

        override fun desligar(contexto: Context) {
            Volante.desligar(contexto)
            ServicoDoPainel.desligar(contexto)
        }
    }

    // ── Mãos livres: estado para a barra (3.7) e setas para a atividade principal ──

    private object MaosLivres : SaidaDasMaosLivres {
        override fun mudou(ativo: Boolean) {
            EstadoDaBarra.maosLivresAtivo = ativo
        }

        override fun teclaDeDirecao(codigoAndroid: Int) = PrincipalActivity.dispatchHandsFreeKey(codigoAndroid)
    }

    // ── Barramento do painel → painel de instrumentos novo (3.6) ──

    private object AoPainel : EventosDoPainel.Ouvinte {
        override fun aoEvento(evento: EventoDoPainel) {
            when (evento) {
                is EventoDoPainel.CartaoMudou -> PainelDeInstrumentos.cartaoMudou(evento.cartao)
                is EventoDoPainel.TeclaParaOTema -> PainelDeInstrumentos.teclaDoVolante(evento.tecla)
                EventoDoPainel.DispensarAviso -> PainelDeInstrumentos.dispensarAvisos()
                is EventoDoPainel.MaosLivresMudou -> PainelDeInstrumentos.maosLivres(evento.ativo)
                is EventoDoPainel.ArNoMaximoMudou -> PainelDeInstrumentos.arNoMaximo(evento.ativo)
                is EventoDoPainel.AppNoDisplay -> PainelDeInstrumentos.appsNoDisplayMudaram(evento.display, evento.ativo)
                EventoDoPainel.GeometriaMudou -> PainelDeInstrumentos.geometriaDosAppsMudou()
                is EventoDoPainel.PrepararVaoNoDisplay3 -> {
                    val l = evento.limites
                    if (l.size >= 4) {
                        PainelDeInstrumentos.prepararVaoNoDisplay3(l[0], l[1], l[2], l[3])
                    } else {
                        Log.w(TAG, "vão no display 3 sem 4 limites: ${l.contentToString()}")
                    }
                }
                // Ninguém do lado de cá usa: o painel novo recebe a tecla já decidida (TeclaParaOTema).
                is EventoDoPainel.TeclaRecebida, is EventoDoPainel.Livre -> Unit
            }
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
