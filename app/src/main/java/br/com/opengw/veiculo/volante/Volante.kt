package br.com.opengw.veiculo.volante

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import br.com.opengw.veiculo.painel.ConectorAndroid
import br.com.opengw.veiculo.painel.DiarioDoMotor
import br.com.opengw.veiculo.painel.EventosDoPainel
import br.com.opengw.veiculo.painel.MenuAntigoDoPainel
import br.com.opengw.veiculo.painel.PreferenciasDoApp
import br.com.opengw.veiculo.ProjecaoNasTelas
import br.com.opengw.veiculo.painel.RegistroNoLogcat
import br.com.opengw.veiculo.painel.RelogioDoAparelho
import br.com.opengw.veiculo.painel.ServicoDoPainel
import com.beantechs.inputservice.IInputListener
import com.beantechs.inputservice.IInputService

/**
 * As teclas do volante pelo serviço de teclas da fabricante (`com.beantechs.inputservice`), repassadas
 * numa ordem fixa a quem as quer (`motor-painel-e-volante.md` §B).
 *
 * Tipo de tecla: `br.com.opengw.painel.TeclaDoVolante`. Os NOMES (UP, DOWN, LEFT, RIGHT, ENTER, HOME,
 * BACK, UP_LONG, DOWN_LONG, ENTER_LONG, BACK_LONG) são contrato: chegam aos temas como
 * `window.onKeyEvent('<NOME>')`.
 *
 * Escrito em sala limpa. A lógica fica em [NucleoDoVolante] e [LigacaoDoVolante] (sem Android,
 * testadas na JVM); aqui só se liga ela ao `bindService` e às classes da fabricante.
 *
 * Ligado: o ciclo de vida do motor chama [ligar]; só há um tratador de teclas registrado no serviço.
 */
object Volante {

    private const val ACAO_DO_SERVICO = "com.beantechs.inputservice.service_init"
    private const val PACOTE_DO_SERVICO = "com.beantechs.inputservice"

    /** Todas as teclas (código −1 no serviço de teclas). */
    private val TODAS_AS_TECLAS = intArrayOf(-1)

    private val registro = RegistroNoLogcat("OpenGW.Volante")

    private val nucleo: NucleoDoVolante<KeyEvent> by lazy {
        NucleoDoVolante(
            relogio = RelogioDoAparelho,
            registro = registro,
            diario = DiarioDoMotor,
            preferencias = PreferenciasDoApp,
            publicador = EventosDoPainel.barramento,
            cartao = ServicoDoPainel.nucleo,
        )
    }

    /** O ouvinte de teclas: um objeto só para a vida toda do processo (B.1). */
    private val ouvinte: IInputListener.Stub by lazy { OuvinteDeTeclas() }

    private val conector = ConectorAndroid<ServicoRemotoDeTeclas>(
        nome = "volante",
        intencao = { Intent(ACAO_DO_SERVICO).setPackage(PACOTE_DO_SERVICO) },
        converter = { binder -> InterfaceDeTeclas(IInputService.Stub.asInterface(binder)) },
        registro = registro,
    )

    private val ligacao: LigacaoDoVolante by lazy {
        LigacaoDoVolante(
            conector = conector,
            emOutraThread = { tarefa -> Thread({ tarefa() }, "OpenGW-Volante").apply { isDaemon = true }.start() },
            registro = registro,
        )
    }

    /** Liga as dependências de fora (ciclo de vida, 3.3e §G.1). Sem isto, valem as vazias. */
    @JvmStatic
    fun instalar(
        coringas: TratadorDeTecla,
        hud: TratadorDeTecla,
        circulo: TratadorDeTecla,
        maosLivres: SaidaDasMaosLivres,
        projecao: ProjecaoNasTelas,
        menuAntigo: MenuAntigoDoPainel,
    ) {
        nucleo.elos = ElosDoVolante(
            coringas = EloDaCadeia { c, a, e -> coringas.tratar(c, a, e) },
            hud = EloDaCadeia { c, a, e -> hud.tratar(c, a, e) },
            circulo = EloDaCadeia { c, a, e -> circulo.tratar(c, a, e) },
        )
        nucleo.maosLivres = maosLivres
        nucleo.projecao = projecao
        nucleo.menuAntigo = menuAntigo
    }

    /** B.1: desfaz uma ligação anterior antes (desregistra o ouvinte primeiro). Volta na hora. */
    @JvmStatic
    fun ligar(contexto: Context) {
        conector.contexto = contexto.applicationContext ?: contexto
        ligacao.ligar()
    }

    /** B.8. */
    @JvmStatic
    @Suppress("UNUSED_PARAMETER")
    fun desligar(contexto: Context) {
        // A ligação é desfeita no mesmo contexto em que foi feita (guardado pelo conector).
        ligacao.desligar()
    }

    /** O modo mãos livres está ligado (B.3)? */
    @JvmStatic
    val maosLivresAtivo: Boolean
        get() = nucleo.maosLivresAtivo

    /** Entra na cadeia inteira como se viesse do serviço de teclas, na thread de quem chamou (B.9). */
    @JvmStatic
    fun injetar(codigo: Int, acao: Int): Boolean {
        val evento = try {
            KeyEvent(acao, codigo)
        } catch (e: Exception) {
            registro.erro("injetar: não deu para montar a tecla $codigo", e)
            return false
        }
        return nucleo.tratar(codigo, acao, evento, doServico = false)
    }

    /** As teclas chegam aqui numa thread de binder do nosso processo, uma de cada vez. */
    private class OuvinteDeTeclas : IInputListener.Stub() {
        override fun dispatchKeyEvent(event: KeyEvent?) {
            if (event == null) return
            try {
                nucleo.tratar(event.keyCode, event.action, event, doServico = true)
            } catch (e: Exception) {
                registro.erro("falhou tratando a tecla do volante", e)
            }
        }
    }

    private class InterfaceDeTeclas(private val servico: IInputService) : ServicoRemotoDeTeclas {
        override fun registrarOuvinte() = servico.registerKeyEventListener(TODAS_AS_TECLAS, ouvinte)

        override fun desregistrarOuvinte() = servico.unregisterKeyEventListener(TODAS_AS_TECLAS, ouvinte)
    }
}
