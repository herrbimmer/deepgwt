// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.painel

import android.content.Context
import android.content.pm.ApplicationInfo
import android.hardware.display.DisplayManager
import android.view.Display
import android.widget.Toast
import br.com.deepgwt.nucleo.DeepGwt
import br.com.deepgwt.ponte.Cancelavel
import br.com.deepgwt.ponte.PonteDosTemas
import br.com.deepgwt.BuildConfig

/**
 * O painel de instrumentos projetado pelo app (`projetores-painel.md`): a camada do display do painel
 * ([ProjetorDoPainel]) e a do display 1 ([CamadaDoDisplay1]).
 *
 * Todas as entradas podem ser chamadas de qualquer thread e voltam na hora; o trabalho é postado na
 * principal. Sem [iniciar], as outras não fazem nada.
 */
object PainelDeInstrumentos {

    private val tempo: TempoDoPainel = TempoDoApp
    private val registro: RegistroDoPainel = RegistroDoApp

    // ── Só na principal ──
    private var ligacoes: LigacoesDoPainel? = null
    private var ativo = false
    private var projetor: ProjetorDoPainel? = null
    private var camada1: CamadaDoDisplay1? = null
    private var idDoPainel = EscolhaDaTela.PAINEL_PADRAO
    private var ouvinteDeTelas: DisplayManager.DisplayListener? = null
    private var observacaoDaTela: Cancelavel? = null
    private var telaLigada = false
    private var esperandoSemeadura = false

    @Volatile
    private var arranqueFeito = false

    /** Cria as duas camadas (idempotente: chamar de novo com elas no ar não faz nada). Volta na hora. */
    @JvmStatic
    fun iniciar(ligacoes: LigacoesDoPainel) {
        tempo.naPrincipal {
            if (ativo) return@naPrincipal
            this.ligacoes = ligacoes
            ativo = true
            try {
                ouvirATelaPrincipal()
                criarCamadas()
                ouvirTelas()
                esperarSemeadura()
            } catch (e: Throwable) {
                registro.erro("iniciar o painel de instrumentos falhou", e)
            }
        }
    }

    /** Derruba e cria de novo as duas camadas, detectando a tela do painel outra vez. */
    @JvmStatic
    fun recriar() {
        tempo.naPrincipal {
            if (!ativo) return@naPrincipal
            fecharCamadas()
            try {
                criarCamadas()
            } catch (e: Throwable) {
                registro.erro("recriar o painel de instrumentos falhou", e)
            }
        }
    }

    /** Derruba tudo e para de ouvir o carro (fim do serviço). */
    @JvmStatic
    fun encerrar() {
        tempo.naPrincipal {
            if (!ativo) return@naPrincipal
            ativo = false
            fecharCamadas()
            pararDeOuvirTelas()
            observacaoDaTela?.cancelar()
            observacaoDaTela = null
        }
    }

    // ── Ajustes visuais (a fachada telas.Painel delega para cá) ──

    @JvmStatic
    fun aplicarEscalaDosNumeros() = noNucleo { it.aplicarEscalaDosNumeros() }

    @JvmStatic
    fun aplicarCorDoModoNoturno() = noNucleo { it.aplicarCorDoModoNoturno() }

    @JvmStatic
    fun aplicarEscalaDaTemperatura() = noNucleo { it.aplicarEscalaDaTemperatura() }

    @JvmStatic
    fun republicarFarol() = noNucleo { it.republicarFarol() }

    // ── Avisos de fora (motor do volante/painel — 3.3d; lançador — 3.8) ──

    /** O cartão aceito pelo motor (`projetores-cartao.md`). */
    @JvmStatic
    fun cartaoMudou(cartao: Int) = noNucleo { it.cartaoMudou(cartao) }

    /** A tecla que sobrou para o painel. */
    @JvmStatic
    fun teclaDoVolante(tecla: TeclaDoVolante) = noNucleo { it.teclaDoVolante(tecla) }

    /** BACK do volante. */
    @JvmStatic
    fun dispensarAvisos() = noNucleo { it.dispensarAvisos() }

    @JvmStatic
    fun maosLivres(ativo: Boolean) {
        tempo.naPrincipal {
            projetor?.nucleo?.maosLivres(ativo)
            camada1?.maosLivres(ativo)
        }
    }

    @JvmStatic
    fun arNoMaximo(ativo: Boolean) = noNucleo { it.arNoMaximo(ativo) }

    /** App entrou/saiu do display 1 ou 3. */
    @JvmStatic
    fun appsNoDisplayMudaram(display: Int, algumApp: Boolean) {
        tempo.naPrincipal {
            projetor?.nucleo?.appsNoDisplayMudaram(display, algumApp)
            if (display == RegrasDosApps.DISPLAY_DE_TRAS) camada1?.appNoDisplay(algumApp)
        }
    }

    @JvmStatic
    fun geometriaDosAppsMudou() = noNucleo { it.geometriaDosAppsMudou() }

    /** O vão antes de o lançador mover um app para o display 3 (a espera de 48 ms continua no lançador). */
    @JvmStatic
    fun prepararVaoNoDisplay3(esquerda: Int, topo: Int, direita: Int, base: Int) =
        noNucleo { it.prepararVaoNoDisplay3(esquerda, topo, direita, base) }

    // ═══════════════════════════ Por dentro ═══════════════════════════

    private fun noNucleo(acao: (NucleoDoPainel) -> Unit) {
        tempo.naPrincipal { projetor?.nucleo?.let(acao) }
    }

    private val simulador: Boolean get() = BuildConfig.SIMULATOR_MODE

    private fun contexto(): Context = DeepGwt.contexto

    private fun gerenciador(): DisplayManager = contexto().getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    private fun preferencias() = PonteDosTemas.preferenciasDoApp()

    private fun depuravel(): Boolean = (contexto().applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private val primeiraCarga: () -> Boolean = {
        synchronized(this) {
            if (arranqueFeito) false else true.also { arranqueFeito = true }
        }
    }

    /** A.7: a tela principal, por `engine_state`, só nas transições (Decisão 7). */
    private fun ouvirATelaPrincipal() {
        telaLigada = NucleoDoPainel.telaPrincipalLigada(CarroDoApp.ler(EstadoParaPagina.ESTADO_DO_MOTOR))
        observacaoDaTela?.cancelar()
        observacaoDaTela = CarroDoApp.observar { chave, valor ->
            if (chave == EstadoParaPagina.ESTADO_DO_MOTOR) tempo.naPrincipal { estadoDoMotor(valor) }
        }
    }

    private fun estadoDoMotor(valor: String?) {
        if (!ativo) return
        val ligada = NucleoDoPainel.telaPrincipalLigada(valor)
        if (ligada == telaLigada) return
        telaLigada = ligada
        projetor?.nucleo?.telaPrincipal(ligada)
        camada1?.telaPrincipal(ligada)
        if (!ligada) {
            val apps = ligacoes?.apps ?: return
            tempo.emFundo {
                try {
                    apps.fecharAppsDoPainel()
                } catch (e: Exception) {
                    registro.erro("fechar os apps do painel falhou", e)
                }
            }
        }
    }

    /** A.2–A.5. */
    private fun criarCamadas() {
        val dm = gerenciador()
        val telas = telasConhecidas(dm)
        idDoPainel = if (simulador) {
            EscolhaDaTela.escolherNoSimulador(telas)
        } else {
            val p = PreferenciasDoPainel(preferencias())
            val resultado = EscolhaDaTela.escolher(p.inteiro(PreferenciasDoPainel.TELA_DO_PAINEL, -1), telas)
            if (resultado.apagarPreferencia) preferencias().edit().remove(PreferenciasDoPainel.TELA_DO_PAINEL).apply()
            resultado.id
        }
        criarProjetor(dm)
        if (!simulador) criarDisplay1(dm)
        val faltando = ArrayList<Int>()
        if (projetor == null) faltando.add(idDoPainel)
        if (!simulador && camada1 == null) faltando.add(EscolhaDaTela.TELA_DE_TRAS)
        if (faltando.isNotEmpty()) {
            Diario.registrar("projector_display_missing", mapOf("mask" to idDoPainel, "pending" to faltando.joinToString(",")))
            if (projetor == null) {
                try {
                    Toast.makeText(
                        contexto(),
                        "Painel Virtual: a tela do cluster (display $idDoPainel) não existe agora, então nada foi projetado.",
                        Toast.LENGTH_LONG,
                    ).show()
                } catch (e: Exception) {
                    registro.erro("aviso de tela ausente falhou", e)
                }
            }
        }
    }

    private fun criarProjetor(dm: DisplayManager) {
        if (projetor != null) return
        val display = dm.getDisplay(idDoPainel) ?: return
        val l = ligacoes ?: return
        try {
            // Atribui ANTES de mostrar: se `mostrar()` lançar (o WebView não subiu), o catch precisa
            // enxergar o projetor para encerrá-lo; com a atribuição depois do `.also` ele via null,
            // e a janela, os laços de tempo e a ponte ficavam vivos, e o próximo `criarProjetor`
            // montava um segundo projetor por cima.
            val novo = ProjetorDoPainel(
                contexto = contexto(),
                display = display,
                ligacoes = l,
                preferencias = preferencias(),
                tempo = tempo,
                registro = registro,
                telaPrincipalLigada = telaLigada,
                depuravel = depuravel(),
                primeiraCargaDoProcesso = primeiraCarga,
            )
            projetor = novo
            novo.mostrar()
            registro.info("projetor do painel criado no display $idDoPainel")
        } catch (e: Throwable) {
            registro.erro("criar o projetor no display $idDoPainel falhou", e)
            try {
                projetor?.encerrar()
            } catch (e2: Throwable) {
                registro.erro("desfazer o projetor falhou", e2)
            }
            projetor = null
        }
    }

    private fun criarDisplay1(dm: DisplayManager) {
        if (camada1 != null) return
        val display = dm.getDisplay(EscolhaDaTela.TELA_DE_TRAS) ?: return
        val l = ligacoes ?: return
        try {
            val nova = CamadaDoDisplay1(contexto(), display, preferencias(), tempo, l.apps, registro, telaLigada)
            camada1 = nova
            nova.mostrar()
        } catch (e: Throwable) {
            registro.erro("criar a camada do display 1 falhou", e)
            try {
                camada1?.encerrar()
            } catch (e2: Throwable) {
                registro.erro("desfazer a camada do display 1 falhou", e2)
            }
            camada1 = null
        }
    }

    private fun fecharCamadas() {
        try {
            projetor?.encerrar()
        } catch (e: Throwable) {
            registro.erro("fechar o projetor falhou", e)
        }
        projetor = null
        try {
            camada1?.encerrar()
        } catch (e: Throwable) {
            registro.erro("fechar a camada do display 1 falhou", e)
        }
        camada1 = null
    }

    /** A.5(c) e D5 (Decisão 12): ouvir sempre; tela que some fecha a camada, que volta recria. */
    private fun ouvirTelas() {
        if (ouvinteDeTelas != null) return
        val ouvinte = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {
                if (!ativo) return
                val dm = gerenciador()
                if (projetor == null) {
                    if (displayId != idDoPainel) {
                        val telas = telasConhecidas(dm)
                        val tela = telas.firstOrNull { it.id == displayId }
                        if (tela != null && EscolhaDaTela.ehPainel(tela, telas)) idDoPainel = displayId
                    }
                    if (displayId == idDoPainel) criarProjetor(dm)
                }
                if (!simulador && camada1 == null && displayId == EscolhaDaTela.TELA_DE_TRAS) criarDisplay1(dm)
            }

            override fun onDisplayRemoved(displayId: Int) {
                if (projetor?.idDoDisplay == displayId) {
                    registro.info("a tela do painel ($displayId) sumiu")
                    try {
                        projetor?.encerrar()
                    } catch (e: Throwable) {
                        registro.erro("fechar o projetor falhou", e)
                    }
                    projetor = null
                }
                if (camada1?.idDoDisplay == displayId) {
                    try {
                        camada1?.encerrar()
                    } catch (e: Throwable) {
                        registro.erro("fechar a camada do display 1 falhou", e)
                    }
                    camada1 = null
                }
            }

            override fun onDisplayChanged(displayId: Int) = Unit
        }
        gerenciador().registerDisplayListener(ouvinte, null)
        ouvinteDeTelas = ouvinte
    }

    private fun pararDeOuvirTelas() {
        val ouvinte = ouvinteDeTelas ?: return
        ouvinteDeTelas = null
        try {
            gerenciador().unregisterDisplayListener(ouvinte)
        } catch (e: Exception) {
            registro.erro("parar de ouvir as telas falhou", e)
        }
    }

    /** O tema ativo foi semeado de novo nesta abertura: recarregar quando a semeadura acabar. */
    private fun esperarSemeadura() {
        if (esperandoSemeadura) return
        esperandoSemeadura = true
        TemasInstalados.quandoPronto {
            tempo.naPrincipal {
                val pasta = PreferenciasDoPainel(preferencias()).pastaAtiva()
                if (pasta.isNotEmpty() && pasta in TemasInstalados.semeadasNestaAbertura) projetor?.nucleo?.recarregar("tema semeado")
            }
        }
    }

    /** As telas do sistema, com modo físico, privacidade e `uniqueId` (API escondida, por reflexão). */
    private fun telasConhecidas(dm: DisplayManager): List<TelaConhecida> = dm.displays.map { d ->
        val modo = d.mode
        TelaConhecida(
            id = d.displayId,
            largura = modo.physicalWidth,
            altura = modo.physicalHeight,
            privada = (d.flags and Display.FLAG_PRIVATE) != 0,
            idUnico = idUnico(d),
        )
    }

    private fun idUnico(display: Display): String? = try {
        Display::class.java.getMethod("getUniqueId").invoke(display) as? String
    } catch (e: Throwable) {
        null
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
