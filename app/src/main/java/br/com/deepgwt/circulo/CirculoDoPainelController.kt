// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.circulo

import br.com.deepgwt.nucleo.DeepGwt
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.edit
import br.com.deepgwt.hud.HudOverlayController
import br.com.deepgwt.hud.HudPaginas
import br.com.deepgwt.managers.ToqueDoVolante
import br.com.deepgwt.nucleo.Preferencias
import br.com.deepgwt.projection.DestinoDaProjecao
import br.com.deepgwt.projection.ScreenProjectionController
import br.com.deepgwt.ui.screens.PainelEscolhido

/**
 * Liga o [CirculoDoPainel] ao volante, às preferências e à [JanelaDoCirculo].
 *
 * ## ONDE ENTRA NO CAMINHO DAS TECLAS
 *
 * `ServiceManager.dispatchKeyEvent` consulta [handleKeyEvent] logo depois da mídia do Android Auto
 * e ANTES do HUD, do hands-free e do menu do painel. Só fica com a tecla quando
 * [CirculoDoPainel.ehDono] diz que ela é do círculo; o resto segue como antes. Isso tira ↑/↓ do
 * HUD nos cards 1 e 3 — e, com o círculo ligado, o [br.com.deepgwt.hud.HudWheelInput]
 * também cede em qualquer card ([cuidaDoHud]): o HUD passa a obedecer ao volante só pela página HUD.
 * ←/→ (7.7, página Relógio) e BACK são só OUVIDOS ([CirculoDoPainel.observa]): o círculo age e a
 * tecla segue adiante, porque é com ←/→ que o `ServiceManager` acompanha a troca de card do carro.
 *
 * ## THREADS
 *
 * As teclas chegam no binder do `inputservice`; card, aviso e projeção chegam na thread principal
 * pelo projetor do painel. O estado puro é guardado por `synchronized(circulo)`; tela, HUD e
 * preferências só são mexidos na principal.
 */
object CirculoDoPainelController {

    private const val TAG = "CirculoDoPainel"

    // Keycodes do volante, os mesmos do bloco do menu no ServiceManager.
    private const val VOLANTE_CIMA = 1024
    private const val VOLANTE_BAIXO = 1025
    private const val VOLANTE_ESQUERDA = 1026
    private const val VOLANTE_DIREITA = 1027
    private const val VOLANTE_OK = 1028
    private const val VOLANTE_VOLTAR = 1030
    private const val VOLANTE_OK_LONGO = 1037

    /** O que muda o contexto do círculo em Telas (e num perfil de motorista trocado). */
    private val CHAVES_DO_CONTEXTO =
            setOf(
                    Preferencias.ENABLE_INSTRUMENT_PROJECTOR.chave,
                    Preferencias.ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION.chave,
                    Preferencias.ENABLE_CUSTOM_MENU.chave,
                    Preferencias.ENABLE_VIRTUAL_CLUSTER.chave,
                    Preferencias.ORIGINAL_PANEL_CIRCLE_PROJECTION.chave,
                    Preferencias.HUD_OVERLAY_ENABLED.chave,
                    Preferencias.HUD_PAGE_ORDER.chave,
                    Preferencias.HUD_PAGES_ENABLED.chave
            )

    /**
     * Segurou OK na Central e a projeção não subiu em tanto tempo: a autorização de captura deve
     * estar esperando um toque na central (o appop pelo Shizuku não valeu). O disco diz isso.
     */
    private const val ESPERA_DA_AUTORIZACAO_MS = 6_000L

    /** Quanto tempo o disco da Central mostra que a projeção pedida pelo volante falhou. */
    private const val FALHA_NA_TELA_MS = 6_000L

    private val principal by lazy { Handler(Looper.getMainLooper()) }
    private val toque = ToqueDoVolante()

    private var paginaGravada: PaginaDoCirculo? = null
    private var relogioGravado: String? = null

    private val circulo: CirculoDoPainel by lazy {
        val guardada = PaginaDoCirculo.deNome(
                prefs().getString(Preferencias.ORIGINAL_PANEL_CIRCLE_PAGE.chave, null)
        )
        paginaGravada = guardada
        relogioGravado = prefs().getString(Preferencias.ORIGINAL_PANEL_CIRCLE_CLOCK.chave, null)
        CirculoDoPainel(guardada, RelogiosDoCirculo.indiceDe(relogioGravado))
    }

    // ── Só na thread principal ──────────────────────────────────────────────────────────────
    private var janela: JanelaDoCirculo? = null
    private var card = 0
    private var aviso = false
    private var projecaoAberta = false

    /** A página do HUD escolhida no círculo enquanto o volante está com ele; null fora disso. */
    private var candidataDoHud: HudPaginas.Pagina? = null

    /** Segurou OK na Central: desde quando a projeção está subindo (0 = não está). */
    private var abrindoDesde = 0L

    /** Até quando o disco da Central diz que a projeção pedida falhou. */
    private var falhouAte = 0L

    private val reavaliarDica = Runnable { desenhar(piscar = false) }

    /** Lido no binder pelo HudWheelInput. */
    @Volatile private var circuloLigado = false

    private val aoMudarPreferencia =
            SharedPreferences.OnSharedPreferenceChangeListener { p, chave ->
                when (chave) {
                    in CHAVES_DO_CONTEXTO -> principal.post { recalcular("preferência $chave") }
                    // Perfil de motorista trocado: a página dele vem junto.
                    Preferencias.ORIGINAL_PANEL_CIRCLE_PAGE.chave -> {
                        val nova = PaginaDoCirculo.deNome(p.getString(chave, null)) ?: return@OnSharedPreferenceChangeListener
                        principal.post {
                            if (nova == paginaGravada) return@post
                            paginaGravada = nova
                            aplicar(synchronized(circulo) { circulo.irPara(nova) })
                        }
                    }
                    // Relógio escolhido em Telas, ou trazido por um perfil de motorista.
                    Preferencias.ORIGINAL_PANEL_CIRCLE_CLOCK.chave -> {
                        val id = p.getString(chave, null)
                        principal.post {
                            if (id == relogioGravado) return@post
                            relogioGravado = id
                            aplicar(synchronized(circulo) { circulo.escolherRelogio(RelogiosDoCirculo.indiceDe(id)) })
                        }
                    }
                }
            }

    private val expirarControle =
            Runnable {
                val (efeito, ainda) = synchronized(circulo) {
                    circulo.expirar(SystemClock.uptimeMillis()) to circulo.controlandoHud
                }
                // Uma tecla engolida pela guarda do OK renovou o prazo sem reagendar: confere de novo.
                if (ainda) agendarExpiracao() else aplicar(efeito)
            }

    private fun prefs(): SharedPreferences =
            DeepGwt.contextoProtegido.getSharedPreferences("deepgwt_prefs", Context.MODE_PRIVATE)

    // ── Entradas do projetor do painel (thread principal) ─────────────────────────────────────

    /** O projetor subiu (com a janela) ou caiu (null). */
    fun registrarJanela(nova: JanelaDoCirculo?, cardAtual: Int, avisoAtual: Boolean) {
        val antiga = janela
        if (antiga === nova) return
        antiga?.liberar()
        janela = nova
        if (antiga == null && nova != null) {
            prefs().registerOnSharedPreferenceChangeListener(aoMudarPreferencia)
            ScreenProjectionController.aoFalhar = { motivo -> aoFalharProjecao(motivo) }
        }
        if (antiga != null && nova == null) {
            prefs().unregisterOnSharedPreferenceChangeListener(aoMudarPreferencia)
            ScreenProjectionController.aoFalhar = null
            abrindoDesde = 0L
            falhouAte = 0L
            principal.removeCallbacks(reavaliarDica)
        }
        card = cardAtual
        aviso = avisoAtual
        if (nova == null) projecaoAberta = false
        recalcular(if (nova == null) "projetor fechado" else "projetor aberto")
    }

    fun aoMudarCard(novo: Int) {
        if (card == novo) return
        card = novo
        recalcular("card $novo")
    }

    fun aoMudarAviso(ativo: Boolean) {
        if (aviso == ativo) return
        aviso = ativo
        recalcular(if (ativo) "aviso do carro" else "aviso saiu")
    }

    /** A janela da projeção abriu ou fechou no círculo. Abrir leva à página Central. */
    fun aoMudarProjecao(aberta: Boolean) {
        if (projecaoAberta == aberta) return
        projecaoAberta = aberta
        if (aberta) {
            abrindoDesde = 0L
            falhouAte = 0L
            aplicar(synchronized(circulo) { circulo.irPara(PaginaDoCirculo.CENTRAL) }, piscar = true)
        } else {
            desenhar(piscar = false)
        }
    }

    /** A projeção não subiu (qualquer caminho). Se foi o volante que pediu, o disco avisa. */
    private fun aoFalharProjecao(motivo: String) {
        if (abrindoDesde == 0L) return
        Log.w(TAG, "Projeção pedida pelo volante falhou: $motivo")
        abrindoDesde = 0L
        falhouAte = SystemClock.uptimeMillis() + FALHA_NA_TELA_MS
        principal.removeCallbacks(reavaliarDica)
        principal.postDelayed(reavaliarDica, FALHA_NA_TELA_MS + 50)
        desenhar(piscar = false)
    }

    /** Painel de fábrica com o círculo: o HUD só obedece ao volante pela página HUD. */
    fun cuidaDoHud(): Boolean = circuloLigado

    /**
     * O OK segurado é da página Central agora (projetar a última área, Alpha 7.7)? Lido no binder
     * pelo [br.com.deepgwt.hud.HudWheelInput] antes de entrar no Controle do HUD
     * (Alpha 9.5), que fora daqui fica com o OK segurado.
     */
    fun querOkLongoParaProjecao(): Boolean =
            circuloLigado && synchronized(circulo) { circulo.ativo && circulo.pagina == PaginaDoCirculo.CENTRAL }

    // ── Volante (binder do inputservice) ──────────────────────────────────────────────────────

    /** @return true quando a tecla é do círculo e não deve seguir adiante. */
    fun handleKeyEvent(event: KeyEvent): Boolean {
        val tecla = teclaDe(event.keyCode) ?: return false
        val agora = SystemClock.uptimeMillis()
        var efeito: EfeitoDoCirculo = EfeitoDoCirculo.Nenhum
        var dono: Boolean
        synchronized(circulo) {
            dono = circulo.ehDono(tecla)
            // BACK nunca é consumido — ele dispensa o aviso do carro —, mas cancela o controle do
            // HUD; ←/→ trocam o card no carro e, na página Relógio, também o relógio.
            if (!dono && !circulo.observa(tecla)) return false
            if (toque.ehNovoToque(event.keyCode, event.action, agora)) {
                efeito = circulo.aoApertar(tecla, agora)
            }
        }
        if (efeito != EfeitoDoCirculo.Nenhum) principal.post { aplicar(efeito) }
        return dono
    }

    private fun teclaDe(codigo: Int): TeclaDoCirculo? =
            when (codigo) {
                VOLANTE_CIMA -> TeclaDoCirculo.CIMA
                VOLANTE_BAIXO -> TeclaDoCirculo.BAIXO
                VOLANTE_ESQUERDA -> TeclaDoCirculo.ESQUERDA
                VOLANTE_DIREITA -> TeclaDoCirculo.DIREITA
                VOLANTE_OK -> TeclaDoCirculo.OK
                VOLANTE_OK_LONGO -> TeclaDoCirculo.OK_LONGO
                VOLANTE_VOLTAR -> TeclaDoCirculo.VOLTAR
                else -> null
            }

    // ── Thread principal ──────────────────────────────────────────────────────────────────────

    private fun recalcular(motivo: String) {
        val painel = PainelEscolhido.ler(prefs())
        val contexto =
                ContextoDoCirculo(
                        ligado = janela != null &&
                                painel.destinoDaProjecao == DestinoDaProjecao.CIRCULO_DIREITO,
                        card = card,
                        hudLigado = HudOverlayController.isEnabled(),
                        avisoDeFabrica = aviso
                )
        circuloLigado = contexto.ligado
        val efeito = synchronized(circulo) { circulo.aoMudarContexto(contexto) }
        Log.w(TAG, "Contexto ($motivo): $contexto")
        aplicar(efeito, piscar = false)
    }

    private fun aplicar(efeito: EfeitoDoCirculo, piscar: Boolean = efeito is EfeitoDoCirculo.TrocouDePagina) {
        when (efeito) {
            EfeitoDoCirculo.Nenhum -> Unit
            EfeitoDoCirculo.AlternouProjecao -> alternarProjecao()
            is EfeitoDoCirculo.TrocouDeRelogio ->
                Log.w(TAG, "Relógio ${RelogiosDoCirculo.doIndice(efeito.indice).nome}")
            is EfeitoDoCirculo.TrocouDePagina -> Log.w(TAG, "Página ${efeito.pagina}")
            EfeitoDoCirculo.EntrouNoHud -> {
                candidataDoHud = HudPaginas.pagina
                agendarExpiracao()
                Log.w(TAG, "Volante controla o HUD (página ${HudPaginas.pagina})")
            }
            is EfeitoDoCirculo.AndouNoHud -> {
                candidataDoHud = CirculoDoPainel.andar(
                        HudPaginas.visiveis,
                        candidataDoHud ?: HudPaginas.pagina,
                        efeito.passo
                )
                agendarExpiracao()
            }
            EfeitoDoCirculo.ConfirmouHud -> {
                candidataDoHud?.let { HudPaginas.irPara(it) }
                Log.w(TAG, "HUD -> ${candidataDoHud ?: HudPaginas.pagina}; volante devolvido ao painel")
                candidataDoHud = null
                principal.removeCallbacks(expirarControle)
            }
            EfeitoDoCirculo.CancelouHud -> {
                Log.w(TAG, "Controle do HUD cancelado; HUD continua em ${HudPaginas.pagina}")
                candidataDoHud = null
                principal.removeCallbacks(expirarControle)
            }
        }
        gravarPagina()
        gravarRelogio()
        desenhar(piscar, anunciarRelogio = efeito is EfeitoDoCirculo.TrocouDeRelogio)
    }

    /**
     * Segurou OK na página Central: a última área do círculo, sem a folha de seleção — ou, com a
     * projeção no ar, encerra. Sem área guardada o disco já diz para escolher uma na central.
     */
    private fun alternarProjecao() {
        when (ScreenProjectionController.estado.value) {
            ScreenProjectionController.Estado.PROJETANDO -> {
                Log.w(TAG, "Segurou OK na Central: encerrando a projeção")
                ScreenProjectionController.parar("segurou OK no círculo do painel")
            }
            ScreenProjectionController.Estado.SELECIONANDO,
            ScreenProjectionController.Estado.AUTORIZANDO ->
                Log.w(TAG, "Segurou OK na Central: a projeção já está subindo")
            else ->
                if (ScreenProjectionController.reprojetarUltimaArea()) {
                    Log.w(TAG, "Segurou OK na Central: projetando a última área do círculo")
                    abrindoDesde = SystemClock.uptimeMillis()
                    falhouAte = 0L
                    principal.removeCallbacks(reavaliarDica)
                    principal.postDelayed(reavaliarDica, ESPERA_DA_AUTORIZACAO_MS + 50)
                } else {
                    Log.w(TAG, "Segurou OK na Central: nenhuma área do círculo guardada")
                }
        }
    }

    private fun dicaDaCentral(): DicaDaCentral {
        val agora = SystemClock.uptimeMillis()
        return when {
            abrindoDesde != 0L && agora - abrindoDesde < ESPERA_DA_AUTORIZACAO_MS -> DicaDaCentral.ABRINDO
            abrindoDesde != 0L -> DicaDaCentral.CONFIRME_NA_CENTRAL
            agora < falhouAte -> DicaDaCentral.FALHOU
            ScreenProjectionController.ultimaRegiao(DestinoDaProjecao.CIRCULO_DIREITO) != null -> DicaDaCentral.SEGURE_OK
            else -> DicaDaCentral.SEM_AREA
        }
    }

    private fun agendarExpiracao() {
        principal.removeCallbacks(expirarControle)
        principal.postDelayed(expirarControle, CirculoDoPainel.TEMPO_DO_CONTROLE_MS + 50)
    }

    private fun gravarPagina() {
        val escolhida = synchronized(circulo) { circulo.escolhida }
        if (escolhida == paginaGravada) return
        paginaGravada = escolhida
        prefs().edit { putString(Preferencias.ORIGINAL_PANEL_CIRCLE_PAGE.chave, escolhida.name) }
    }

    private fun gravarRelogio() {
        val id = RelogiosDoCirculo.doIndice(synchronized(circulo) { circulo.relogio }).id
        if (id == relogioGravado) return
        relogioGravado = id
        prefs().edit { putString(Preferencias.ORIGINAL_PANEL_CIRCLE_CLOCK.chave, id) }
    }

    private fun desenhar(piscar: Boolean, anunciarRelogio: Boolean = false) {
        val j = janela ?: return
        val dica = dicaDaCentral()
        val exibicao = synchronized(circulo) {
            ExibicaoDoCirculo(
                    ativo = circulo.ativo,
                    pagina = circulo.pagina,
                    paginas = circulo.paginas,
                    disco = circulo.discoVisivel(projecaoAberta),
                    projecao = circulo.projecaoVisivel,
                    controlandoHud = circulo.controlandoHud,
                    paginaDoHud = candidataDoHud,
                    relogio = circulo.relogio,
                    dicaDaCentral = dica
            )
        }
        j.mostrar(exibicao, piscar, anunciarRelogio)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
