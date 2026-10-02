package br.com.opengw.veiculo

import br.com.opengw.veiculo.comandos.MotorDeComandos
import br.com.opengw.veiculo.painel.ServicoDoPainel
import br.com.opengw.veiculo.sistema.AppsDoSistema
import br.com.opengw.veiculo.sistema.EscutaDeVoz

/**
 * Ações no carro e na central que vão além de escrever uma chave.
 *
 * Fachada da independência (`docs/independencia/PLANO.md`, 2.3). Desde a troca do 3.3b–e usa o
 * motor próprio: [MotorDeComandos] (vidros, teto, condução, conforto), [AppsDoSistema] e
 * [EscutaDeVoz] (central) e [ServicoDoPainel] (pulso do painel).
 */
object Comandos {

    // ── Janelas, teto e porta-malas ──

    /** Fecha as quatro janelas. True se o pedido foi aceito. */
    @JvmStatic fun fecharTodasAsJanelas(): Boolean = MotorDeComandos.fecharTodasAsJanelas()

    /** Abertura de cada janela, na ordem do carro; null se ainda não chegou. */
    @JvmStatic fun estadoDasJanelas(): IntArray? = MotorDeComandos.estadoDasJanelas()

    @JvmStatic fun janela(indice: Int, estado: Int): Boolean = MotorDeComandos.janela(indice, estado)

    @JvmStatic fun portaMalasAberto(): Boolean? = MotorDeComandos.portaMalasAberto()

    @JvmStatic fun portaMalas(aberto: Boolean): Boolean = MotorDeComandos.portaMalas(aberto)

    @JvmStatic fun aberturaDoTeto(): Int? = MotorDeComandos.aberturaDoTeto()

    @JvmStatic fun teto(nivel: Int): Boolean = MotorDeComandos.teto(nivel)

    @JvmStatic fun abrirTetoComCortina(nivel: Int): Boolean = MotorDeComandos.abrirTetoComCortina(nivel)

    @JvmStatic fun fecharTeto(fecharCortinaJunto: Boolean) = MotorDeComandos.fecharTeto(fecharCortinaJunto)

    @JvmStatic fun aberturaDaCortina(): Int? = MotorDeComandos.aberturaDaCortina()

    @JvmStatic fun cortina(nivel: Int): Boolean = MotorDeComandos.cortina(nivel)

    @JvmStatic fun abrirCortina() = MotorDeComandos.abrirCortina()

    @JvmStatic fun fecharCortina() = MotorDeComandos.fecharCortina()

    // ── Condução ──

    @JvmStatic fun modoDeConducao(modo: String) = MotorDeComandos.modoDeConducao(modo)

    @JvmStatic fun esp(ligado: Boolean) = MotorDeComandos.esp(ligado)

    @JvmStatic fun forcarMotorACombustao(ligado: Boolean) = MotorDeComandos.forcarMotorACombustao(ligado)

    @JvmStatic fun modoTravessia(ligado: Boolean) = MotorDeComandos.modoTravessia(ligado)

    /** Aplica o alvo de carga da bateria do híbrido, se o modo que o usa estiver ativo. */
    @JvmStatic fun aplicarAlvoDeCargaHev(motivo: String) = MotorDeComandos.aplicarAlvoDeCargaHev(motivo)

    /** Grava o alvo de carga do híbrido (20..80) e manda ao carro, sem conferir o modo. */
    @JvmStatic fun definirAlvoDeCargaHev(valor: Int) = MotorDeComandos.definirAlvoDeCargaHev(valor)

    // ── Conforto ──

    /** Sai do modo de ar-condicionado no máximo. */
    @JvmStatic fun sairDoArNoMaximo() = MotorDeComandos.sairDoArNoMaximo()

    /** O ar-condicionado no máximo está ligado agora. */
    @JvmStatic val arNoMaximoAtivo: Boolean get() = MotorDeComandos.arNoMaximoAtivo

    /** Abre a câmera 360° uma vez (como o botão físico); fecha se já estiver aberta. */
    @JvmStatic fun abrirCamera360() = MotorDeComandos.abrirCamera360()

    @JvmStatic fun gravarMemoriaDoBanco(posicao: Int) = MotorDeComandos.gravarMemoriaDoBanco(posicao)

    @JvmStatic fun chamarMemoriaDoBanco(posicao: Int) = MotorDeComandos.chamarMemoriaDoBanco(posicao)

    @JvmStatic fun fecharJanelasAoTrancar(ligado: Boolean) = MotorDeComandos.fecharJanelasAoTrancar(ligado)

    /** Som externo de alerta a pedestres (AVAS). */
    @JvmStatic fun somParaPedestres(ligado: Boolean) = MotorDeComandos.somParaPedestres(ligado)

    @JvmStatic fun monitoramento(ligado: Boolean) = MotorDeComandos.monitoramento(ligado)

    /** Aplica a preferência de despertar por voz e devolve o que foi feito (síncrono: fora da principal). */
    @JvmStatic fun aplicarDespertarPorVoz(): String = EscutaDeVoz.aplicar()

    // ── Central ──

    @JvmStatic fun garantirAppsDoSistema() = AppsDoSistema.garantirAppsDoSistema()

    @JvmStatic fun garantirAppsDesativados() = AppsDoSistema.garantirAppsDesativados()

    @JvmStatic fun iniciarPulsoDoPainel() = ServicoDoPainel.iniciarPulso()
}
