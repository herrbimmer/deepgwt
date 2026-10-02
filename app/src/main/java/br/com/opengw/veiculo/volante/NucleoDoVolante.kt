package br.com.opengw.veiculo.volante

import br.com.opengw.nucleo.Preferencias
import br.com.opengw.painel.ArbitroDoCartao
import br.com.opengw.painel.TeclaDoVolante
import br.com.opengw.veiculo.painel.CartaoDoPainel
import br.com.opengw.veiculo.painel.DiarioDoPainel
import br.com.opengw.veiculo.painel.EventoDoPainel
import br.com.opengw.veiculo.painel.LeitorDePreferencias
import br.com.opengw.veiculo.painel.MenuAntigoDoPainel
import br.com.opengw.veiculo.painel.NUNCA
import br.com.opengw.veiculo.ProjecaoNasTelas
import br.com.opengw.veiculo.painel.PublicadorDoPainel
import br.com.opengw.veiculo.painel.RegistroDoPainel
import br.com.opengw.veiculo.painel.RegrasDoMotorDoPainel
import br.com.opengw.veiculo.painel.Relogio
import br.com.opengw.veiculo.painel.protegido

/** Um elo da cadeia sem Android: [E] é o `KeyEvent` no app e qualquer coisa nos testes. */
internal fun interface EloDaCadeia<E> {
    fun tratar(codigo: Int, acao: Int, evento: E): Boolean
}

/** Os três elos de fora, na ordem em que entram na cadeia (B.2 passos 1, 4 e 5). */
internal class ElosDoVolante<E>(
    val coringas: EloDaCadeia<E>,
    val hud: EloDaCadeia<E>,
    val circulo: EloDaCadeia<E>,
) {
    companion object {
        fun <E> nenhum(): ElosDoVolante<E> {
            val nada = EloDaCadeia<E> { _, _, _ -> false }
            return ElosDoVolante(nada, nada, nada)
        }
    }
}

/**
 * As teclas do volante sem Android (§B): a cadeia de tratadores, o modo mãos livres e o menu do
 * painel. Roda na thread de quem entregou a tecla (binder do serviço de teclas, ou quem chamou
 * `injetar`) e decide na hora. Nada aqui lê o carro nem roda shell.
 */
internal class NucleoDoVolante<E>(
    private val relogio: Relogio,
    private val registro: RegistroDoPainel,
    private val diario: DiarioDoPainel,
    private val preferencias: LeitorDePreferencias,
    private val publicador: PublicadorDoPainel,
    private val cartao: CartaoDoPainel,
) {
    companion object {
        /** `KeyEvent.ACTION_DOWN` / `ACTION_UP`. */
        const val ACAO_APERTOU = 0
        const val ACAO_SOLTOU = 1

        /** Segurar OK (código longo do OK do painel). */
        val CODIGO_MAOS_LIVRES = TeclaDoVolante.ENTER_LONG.codigo

        /**
         * D-V1 (a confirmar no carro): as mãos livres reagem a "apertou". Se o carro só entregar
         * "soltou", o dono decide trocar para [ACAO_SOLTOU]; até lá fica como hoje.
         */
        const val ACAO_DAS_MAOS_LIVRES = ACAO_APERTOU

        const val INTERVALO_DAS_MAOS_LIVRES_MS = 800L
        const val JANELA_DE_DUPLICADA_MS = 220L

        /** Tecla que segurou a thread do serviço mais que isto vira erro no log (B.7). */
        const val ENGASGO_MS = 15L

        /** `KeyEvent.KEYCODE_DPAD_*` para cada tecla do painel, com as mãos livres ativas (B.3.2). */
        val DIRECOES: Map<Int, Int> = mapOf(
            TeclaDoVolante.UP.codigo to 19,
            TeclaDoVolante.DOWN.codigo to 20,
            TeclaDoVolante.LEFT.codigo to 21,
            TeclaDoVolante.RIGHT.codigo to 22,
            TeclaDoVolante.ENTER.codigo to 23,
        )
    }

    /** Ligados por `instalar`; vazios até lá. */
    @Volatile var elos: ElosDoVolante<E> = ElosDoVolante.nenhum()
    @Volatile var maosLivres: SaidaDasMaosLivres = SaidaDasMaosLivres.VAZIA
    @Volatile var projecao: ProjecaoNasTelas = ProjecaoNasTelas.VAZIA
    @Volatile var menuAntigo: MenuAntigoDoPainel = MenuAntigoDoPainel.VAZIO

    private val trava = Any()
    private var ativo = false
    private var ultimaTroca = NUNCA
    private var ultimaTratadaCodigo = 0
    private var ultimaTratadaInstante = NUNCA

    val maosLivresAtivo: Boolean get() = synchronized(trava) { ativo }

    /**
     * B.2: a cadeia inteira, na ordem; a primeira que consumir encerra. [doServico] = a tecla veio do
     * serviço de teclas (mede o engasgo, B.7); falso para `injetar`.
     */
    fun tratar(codigo: Int, acao: Int, evento: E, doServico: Boolean): Boolean {
        val inicio = relogio.agora()
        val consumida = protegido(registro, "cadeia de teclas ($codigo)", false) { cadeia(codigo, acao, evento) }
        if (doServico) {
            val gasto = relogio.agora() - inicio
            if (gasto > ENGASGO_MS) registro.erro("tecla $codigo (ação $acao) segurou o serviço de teclas por $gasto ms")
        }
        return consumida
    }

    private fun cadeia(codigo: Int, acao: Int, evento: E): Boolean {
        val elos = this.elos
        val projecao = this.projecao
        // 1. Coringas.
        if (perguntar("coringas") { elos.coringas.tratar(codigo, acao, evento) }) return true
        // 2. Só diagnóstico.
        if (protegido(registro, "teclaMereceDiagnostico", false) { projecao.teclaMereceDiagnostico(codigo) }) {
            registro.info("tecla do volante (diagnóstico): $codigo ação $acao")
        }
        // 3. Mídia do Android Auto.
        if (preferencias.booleano(Preferencias.ENABLE_STEERING_WHEEL_MEDIA_CONTROL, true) &&
            perguntar("mídia do Android Auto") { projecao.teclaDoVolante(codigo, acao) }
        ) {
            registro.info("tecla $codigo tratada pela mídia do Android Auto")
            return true
        }
        // 4. Controle do HUD (antes do círculo e das mãos livres: regra do dono, 9.5).
        if (perguntar("controle do HUD") { elos.hud.tratar(codigo, acao, evento) }) return true
        // 5. Círculo do painel.
        if (perguntar("círculo do painel") { elos.circulo.tratar(codigo, acao, evento) }) return true
        // 6. Mãos livres.
        if (maosLivres(codigo, acao)) return true
        // 7. Menu do painel.
        if (preferencias.booleano(Preferencias.ENABLE_CUSTOM_MENU, false)) return menuDoPainel(codigo, acao)
        return false
    }

    /** Uma exceção num tratador é registrada e a tecla segue (B.2). */
    private inline fun perguntar(quem: String, bloco: () -> Boolean): Boolean = protegido(registro, quem, false, bloco)

    // ── B.3: mãos livres ──

    private fun maosLivres(codigo: Int, acao: Int): Boolean {
        if (codigo == CODIGO_MAOS_LIVRES && acao == ACAO_DAS_MAOS_LIVRES) {
            val agora = relogio.agora()
            val novo: Boolean? = synchronized(trava) {
                if (ultimaTroca == NUNCA || agora - ultimaTroca > INTERVALO_DAS_MAOS_LIVRES_MS) {
                    ativo = !ativo
                    ultimaTroca = agora
                    ativo
                } else {
                    null
                }
            }
            if (novo == null) {
                registro.info("mãos livres: segurar OK repetido em menos de $INTERVALO_DAS_MAOS_LIVRES_MS ms; ignorado")
            } else {
                registro.info("mãos livres: ${if (novo) "ligado" else "desligado"}")
                val saida = maosLivres
                protegido(registro, "maosLivres.mudou", Unit) { saida.mudou(novo) }
                publicador.publicar(EventoDoPainel.MaosLivresMudou(novo))
            }
            return true
        }
        if (acao == ACAO_APERTOU && maosLivresAtivo) {
            val direcao = DIRECOES[codigo] ?: return false
            val saida = maosLivres
            protegido(registro, "maosLivres.teclaDeDirecao", Unit) { saida.teclaDeDirecao(direcao) }
            return true
        }
        return false
    }

    // ── B.4: menu do painel ──

    private fun menuDoPainel(codigo: Int, acao: Int): Boolean {
        val tecla = TeclaDoVolante.doCodigo(codigo) ?: return false
        val agora = relogio.agora()
        // 1. A última tecla do painel recebida (o árbitro do cartão usa, A.5).
        cartao.teclaDoPainelRecebida(tecla)
        // 2. Registro.
        val cartaoAgora = cartao.cartaoAtual
        registro.info("tecla do painel: ${tecla.name} ($codigo) ação $acao, cartão $cartaoAgora")
        anotar("tecla_do_painel", linkedMapOf("tecla" to tecla.name, "codigo" to codigo, "acao" to acao, "cartao" to cartaoAgora))
        // 3. Para quem quiser ver todas.
        publicador.publicar(EventoDoPainel.TeclaRecebida(tecla, codigo, acao))
        // 7. "Apertou" fica nisso.
        if (!ArbitroDoCartao.tratarAcao(acao)) return true
        // 4 e 6. Duplicada: mesmo código da última tratada em até 220 ms.
        val duplicada = synchronized(trava) {
            val repetida = ultimaTratadaInstante != NUNCA && ultimaTratadaCodigo == codigo &&
                agora - ultimaTratadaInstante <= JANELA_DE_DUPLICADA_MS
            if (!repetida) {
                ultimaTratadaCodigo = codigo
                ultimaTratadaInstante = agora
            }
            repetida
        }
        if (duplicada) {
            registro.info("tecla do painel duplicada: ${tecla.name} ($codigo); ignorada")
            anotar("tecla_do_painel_duplicada", linkedMapOf("tecla" to tecla.name, "codigo" to codigo))
            return true
        }
        // 5. Tratada.
        if (tecla.trocaCartao) {
            cartao.navegar(tecla)
            return true
        }
        if (ArbitroDoCartao.soltaPosseDoAr(tecla)) cartao.soltarPosseDoAr()
        if (tecla == TeclaDoVolante.BACK) publicador.publicar(EventoDoPainel.DispensarAviso)
        if (RegrasDoMotorDoPainel.temaEsportivoAntigo(preferencias)) {
            val menu = menuAntigo
            protegido(registro, "menuAntigo.tecla", Unit) { menu.tecla(tecla.name) }
        } else {
            publicador.publicar(EventoDoPainel.TeclaParaOTema(tecla))
        }
        return true
    }

    private fun anotar(evento: String, detalhes: Map<String, Any?>) {
        protegido(registro, "diário ($evento)", Unit) { diario(evento, detalhes) }
    }
}
