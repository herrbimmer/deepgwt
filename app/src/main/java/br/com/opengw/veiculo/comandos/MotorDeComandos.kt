package br.com.opengw.veiculo.comandos

import br.com.opengw.veiculo.ProjecaoNasTelas
import br.com.opengw.veiculo.TravaDeMovimento

/**
 * A implementação nova de `br.com.opengw.veiculo.Comandos` (módulo 3.3c, escrita em sala limpa a
 * partir de `docs/independencia/specs/motor-comandos.md`). Mesmas funções, mesmo comportamento; a
 * lógica fica em [ComandosDoCarro] (testada na JVM).
 *
 * Ligado: os corpos de `Comandos.kt` chamam daqui. As três funções de `Comandos` que não são desta
 * parte moram em `br.com.opengw.veiculo.sistema` (`AppsDoSistema`, `EscutaDeVoz`) e no painel (3.3d).
 */
object MotorDeComandos {

    internal val nucleo: ComandosDoCarro by lazy {
        ComandosDoCarro(
            carro = CarroPeloVeiculo,
            voz = AlcasDoMotor,
            prefs = PreferenciasDoApp,
            agenda = LinhaDoMotor.agenda,
            trava = TravaDeMovimento::agora,
            registro = RegistroNoLogcatDe("OpenGW.Comandos"),
            diario = DiarioDoMotor,
        )
    }

    /** Guardas de projeção da câmera 360 (o ciclo de vida liga ao lançador). */
    @JvmStatic
    var projecao: ProjecaoNasTelas
        get() = nucleo.projecao
        set(valor) {
            nucleo.projecao = valor
        }

    // ── Janelas, teto e porta-malas ──

    @JvmStatic fun fecharTodasAsJanelas(): Boolean = nucleo.fecharTodasAsJanelas()

    @JvmStatic fun estadoDasJanelas(): IntArray? = nucleo.estadoDasJanelas()

    @JvmStatic fun janela(indice: Int, estado: Int): Boolean = nucleo.janela(indice, estado)

    @JvmStatic fun portaMalasAberto(): Boolean? = nucleo.portaMalasAberto()

    @JvmStatic fun portaMalas(aberto: Boolean): Boolean = nucleo.portaMalas(aberto)

    @JvmStatic fun aberturaDoTeto(): Int? = nucleo.aberturaDoTeto()

    @JvmStatic fun teto(nivel: Int): Boolean = nucleo.teto(nivel)

    @JvmStatic fun abrirTetoComCortina(nivel: Int): Boolean = nucleo.abrirTetoComCortina(nivel)

    @JvmStatic fun fecharTeto(fecharCortinaJunto: Boolean) = nucleo.fecharTeto(fecharCortinaJunto)

    @JvmStatic fun aberturaDaCortina(): Int? = nucleo.aberturaDaCortina()

    @JvmStatic fun cortina(nivel: Int): Boolean = nucleo.cortina(nivel)

    @JvmStatic fun abrirCortina() = nucleo.abrirCortina()

    @JvmStatic fun fecharCortina() = nucleo.fecharCortina()

    // ── Condução ──

    @JvmStatic fun modoDeConducao(modo: String) = nucleo.modoDeConducao(modo)

    @JvmStatic fun esp(ligado: Boolean) = nucleo.esp(ligado)

    @JvmStatic fun forcarMotorACombustao(ligado: Boolean) = nucleo.forcarMotorACombustao(ligado)

    @JvmStatic fun modoTravessia(ligado: Boolean) = nucleo.modoTravessia(ligado)

    /** I.1: reaplica o alvo se o carro já está em HEV Prioritário; volta na hora. */
    @JvmStatic fun aplicarAlvoDeCargaHev(motivo: String) = nucleo.aplicarAlvoDeCargaHev(motivo)

    /** I.3: grava o alvo (20..80) e escreve no carro, sem conferir o modo. */
    @JvmStatic fun definirAlvoDeCargaHev(valor: Int) = nucleo.definirAlvoDeCargaHev(valor)

    // ── Conforto ──

    /** J.5; qualquer thread, volta na hora. */
    @JvmStatic fun sairDoArNoMaximo() = ArNoMaximo.sair()

    /** O ar no máximo está ligado agora (§J). Leitura barata, qualquer thread. */
    @JvmStatic val arNoMaximoAtivo: Boolean get() = ArNoMaximo.ativo

    /** E.1: alterna a câmera 360 (abre se fechada). */
    @JvmStatic fun abrirCamera360() = nucleo.alternarCamera360()

    @JvmStatic fun gravarMemoriaDoBanco(posicao: Int) = nucleo.gravarMemoriaDoBanco(posicao)

    @JvmStatic fun chamarMemoriaDoBanco(posicao: Int) = nucleo.chamarMemoriaDoBanco(posicao)

    @JvmStatic fun fecharJanelasAoTrancar(ligado: Boolean) = nucleo.fecharJanelasAoTrancar(ligado)

    @JvmStatic fun somParaPedestres(ligado: Boolean) = nucleo.somParaPedestres(ligado)

    @JvmStatic fun monitoramento(ligado: Boolean) = nucleo.monitoramento(ligado)
}

/** Ar-condicionado no máximo (motor-comandos.md §J). Entradas voltam na hora; o trabalho é na linha do motor. */
object ArNoMaximo {

    internal val controle: ControleDoArNoMaximo by lazy {
        ControleDoArNoMaximo(
            carro = CarroPeloVeiculo,
            prefs = PreferenciasDoApp,
            agenda = LinhaDoMotor.agenda,
            registro = RegistroNoLogcatDe("OpenGW.ArNoMaximo"),
            diario = DiarioDoMotor,
        )
    }

    /** Publicação no barramento do painel (3.3d). O ciclo de vida liga. */
    @JvmStatic
    var aviso: AvisoDoArNoMaximo
        get() = controle.aviso
        set(valor) {
            controle.aviso = valor
        }

    /** J.2 (gatilho: READY ligou). */
    @JvmStatic fun ligarSeQuente() = controle.ligarSeQuente()

    /** J.4. */
    @JvmStatic fun aoMudarTemperaturaInterna() = controle.aoMudarTemperaturaInterna()

    /** J.5 (= `Comandos.sairDoArNoMaximo`). */
    @JvmStatic fun sair() = controle.sair()

    @JvmStatic val ativo: Boolean get() = controle.ativo

    internal fun cancelarAgendados() = controle.cancelarAgendados()
}

/** Câmera 360 para as automações (motor-comandos.md §1.4 e §E). */
object Camera360 {

    /** O app abriu a câmera e ela ainda não fechou (E.3). */
    @JvmStatic
    var abertaPeloApp: Boolean
        get() = MotorDeComandos.nucleo.cameraAbertaPeloApp
        set(valor) {
            MotorDeComandos.nucleo.cameraAbertaPeloApp = valor
        }

    /** E.2. */
    @JvmStatic fun fechar() = MotorDeComandos.nucleo.fecharCamera360()
}
