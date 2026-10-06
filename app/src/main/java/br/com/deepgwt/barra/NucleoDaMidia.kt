// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.barra

import android.graphics.Bitmap
import br.com.deepgwt.barra.RegrasDaMidia.ANDROID_AUTO
import br.com.deepgwt.barra.RegrasDaMidia.CARPLAY
import br.com.deepgwt.barra.RegrasDaMidia.USB
import br.com.deepgwt.barra.RegrasDaMidia.eAndroidAuto
import br.com.deepgwt.barra.RegrasDaMidia.eCarPlay
import br.com.deepgwt.barra.RegrasDaMidia.eProjecao

/** Uma sessão de mídia escolhida para publicar (caso 10.3), já extraída. */
internal data class CandidatoDeSessao(
    val pacote: String,
    val titulo: String?,
    val artista: String?,
    val album: String?,
    val capa: Bitmap?,
    val tocando: Boolean,
    val duracaoMs: Long,
    val decorridoMs: Long,
    /** Hora da última posição se > 0, senão agora. */
    val atualizadoEmMs: Long,
    val podeBuscar: Boolean,
    val temMetadados: Boolean,
)

/** Uma faixa do USB pronta para publicar (caso 37). */
internal data class FaixaUsb(
    val titulo: String,
    val artista: String?,
    val album: String?,
    val capa: Bitmap?,
    val caminho: String?,
    val duracaoMs: Long,
    val decorridoMs: Long,
    val tocando: Boolean,
)

/**
 * O núcleo da mídia: as regras que escrevem o retrato (casos 10–13, 18–20, 25–33, 39, 41–46, 56,
 * 58). Sem Android direto (o [Ambiente] dá relógio, retrato e lançador), para ser testável.
 *
 * Os métodos marcados "principal" escrevem o retrato e só rodam na thread principal; os marcados
 * "fundo" podem ir ao lançador (shell) e nunca rodam na principal.
 */
internal class NucleoDaMidia(private val ambiente: Ambiente) {

    interface Ambiente {
        val lancador: LancadorParaABarra
        fun agoraMs(): Long
        fun projecaoNoPainel(): String
        fun lerRetrato(): MidiaAgora
        fun escreverRetrato(m: MidiaAgora)
        /** Enfileira na principal. */
        fun naPrincipal(bloco: () -> Unit)
        fun registrar(categoria: String, campos: Map<String, Any?>)
        fun log(mensagem: String)
        /** Caso 34 (já guardado por 3 s): pronto, não pronto, ou null se não deu para ler. */
        fun usbDaProjecaoPronto(): Boolean?
    }

    // ── Estado do núcleo ────────────────────────────────────────────────────────────────

    /** Fonte e fonte de áudio da central (escritas pelo laço de 1,5 s). */
    @Volatile
    var central: CentralDeMidiaLida = CentralDeMidiaLida.NENHUMA

    /** Se o AA está tocando quando nada melhor se sabe. */
    @Volatile
    var palpiteAA: Boolean = true
        private set

    private var assinaturaCarPlay: String? = null
    private var assinaturaAA: String? = null
    private var assinaturaUsb: String? = null

    /** Fim da janela de 4 s em que a regressão de progresso do AA vale (0 = fechada). */
    @Volatile
    private var janelaDeTrocaAte = 0L

    /** Prazo da proteção do rádio (0 = inativa). */
    @Volatile
    private var protecaoAte = 0L

    /** Horário do último comando AA aceito (0 = nenhum). Um só para todos os nomes (caso 57). */
    @Volatile
    var ultimoComandoAA = 0L

    /** Último "transporte AA pronto" calculado em segundo plano (a principal lê daqui). */
    @Volatile
    var transporteAAProntoGuardado = false
        private set

    @Volatile
    private var sessaoAAProntaGuardada: Pair<Long, Boolean>? = null

    /** Conta escritas no retrato (limpeza condicionada, M-D6). */
    @Volatile
    var geracao = 0L
        private set

    private val alvo = AlvoSegurado()

    private val retrato: MidiaAgora get() = ambiente.lerRetrato()
    private val dono: String? get() = retrato.pacote

    private fun escrever(m: MidiaAgora) {
        geracao++
        ambiente.escreverRetrato(m)
    }

    private fun vazioParaNulo(s: String?): String? = s?.takeIf { it.isNotEmpty() }

    /** Os dois lados conhecidos e diferentes. */
    private fun mudouDeValorConhecido(antes: String?, depois: String?) =
        antes != null && depois != null && antes != depois

    // ── Limpezas (caso 45) e preservação (caso 46) ─────────────────────────────────────

    /** Caso 46 (principal). Devolve "preservou". */
    fun preservarAANativo(): Boolean {
        if (!central.androidAutoNativo || !transporteAAProntoGuardado || eCarPlay(dono)) return false
        val r = retrato
        val eraAA = eAndroidAuto(r.pacote)
        escrever(r.copy(pacote = ANDROID_AUTO, tocando = if (eraAA) r.tocando else palpiteAA))
        return true
    }

    /**
     * Limpeza geral pedida de qualquer thread: enfileirada na principal e só aplicada se nada
     * escreveu o retrato desde o pedido (M-D6).
     */
    fun pedirLimpezaGeral(preservarProjecao: Boolean) {
        val pedidoNa = geracao
        ambiente.naPrincipal {
            if (geracao == pedidoNa) limparGeral(preservarProjecao)
        }
    }

    /** Caso 45, geral (principal). */
    fun limparGeral(preservarProjecao: Boolean) {
        if (preservarAANativo()) return
        if (preservarProjecao && eProjecao(dono)) return
        if (!preservarProjecao) {
            assinaturaCarPlay = null
            assinaturaAA = null
        }
        escrever(retrato.zerado())
    }

    /** Ao parar a mídia: zera sem exceção nenhuma (principal). */
    fun limparTudo() {
        assinaturaCarPlay = null
        assinaturaAA = null
        assinaturaUsb = null
        alvo.esquecer()
        janelaDeTrocaAte = 0L
        protecaoAte = 0L
        escrever(retrato.zerado())
    }

    /** Caso 45, CarPlay (principal). */
    fun limparCarPlay() {
        assinaturaCarPlay = null
        if (!eCarPlay(dono)) return
        escrever(retrato.zerado())
    }

    /** Caso 45, AA (principal). */
    fun limparAA() {
        assinaturaAA = null
        ultimoComandoAA = 0L
        if (preservarAANativo()) return
        if (!eAndroidAuto(dono)) return
        escrever(retrato.zerado())
    }

    /** Caso 39, limpar USB (principal). Devolve "limpou". */
    fun limparUsb(): Boolean {
        if (dono != USB) return false
        assinaturaUsb = null
        escrever(retrato.zerado())
        ambiente.registrar("usb_media", mapOf("etapa" to "cleared"))
        return true
    }

    // ── Progresso (casos 41, 56) ────────────────────────────────────────────────────────

    /** A janela de troca de faixa está aberta? Consultar uma vencida a fecha. */
    fun janelaDeTrocaAberta(): Boolean {
        val ate = janelaDeTrocaAte
        if (ate == 0L) return false
        if (ambiente.agoraMs() <= ate) return true
        janelaDeTrocaAte = 0L
        return false
    }

    /** Caso 41 sobre o retrato [novo] (já com pacote e tocando novos). */
    private fun comProgresso(
        base: MidiaAgora,
        novo: MidiaAgora,
        duracao: Long,
        decorrido: Long,
        agora: Long,
        podeBuscar: Boolean,
        regressaoPermitida: Boolean,
    ): MidiaAgora {
        val permitida = regressaoPermitida ||
            ((eAndroidAuto(novo.pacote) || central.androidAutoNativo) && janelaDeTrocaAberta())
        return RegrasDaMidia.comProgresso(base, novo, duracao, decorrido, agora, podeBuscar, permitida)
    }

    /** Caso 56 (principal). */
    fun trocouDeFaixa(motivo: String): Boolean {
        val agora = ambiente.agoraMs()
        janelaDeTrocaAte = agora + JANELA_DE_TROCA_MS
        val r = retrato
        if (eAndroidAuto(r.pacote) || central.androidAutoNativo) {
            escrever(comProgresso(r, r, r.duracaoMs, 0L, agora, podeBuscar = false, regressaoPermitida = true))
        } else {
            ambiente.log("trocou de faixa ($motivo) sem AA: só a janela")
        }
        return true
    }

    // ── Alvo segurado (caso 58) ─────────────────────────────────────────────────────────

    /** Marca o alvo de um tocar/pausar do AA (principal). */
    fun marcarAlvo(tocar: Boolean) {
        val agora = ambiente.agoraMs()
        palpiteAA = tocar
        val r = retrato
        alvo.marcar(tocar, agora, r.decorridoMs)
        val novo = r.copy(pacote = ANDROID_AUTO, tocando = tocar)
        escrever(comProgresso(r, novo, r.duracaoMs, r.decorridoMs, agora, podeBuscar = false, regressaoPermitida = false))
    }

    fun resolverPeloAlvo(recebido: Boolean?, decorridoRecebido: Long?): Boolean? =
        alvo.resolver(recebido, decorridoRecebido, ambiente.agoraMs())

    // ── Proteção do rádio (casos 29–31) ─────────────────────────────────────────────────

    /** Caso 29 (principal). */
    fun ativarProtecaoDoRadio(motivo: String) {
        if (central.ignorarProtecaoPorAA) {
            protecaoAte = 0L
            ambiente.log("proteção do rádio ignorada por AA ($motivo)")
            return
        }
        val agora = ambiente.agoraMs()
        if (protecaoAte <= agora) ambiente.log("proteção do rádio ativa ($motivo)")
        protecaoAte = agora + PROTECAO_DO_RADIO_MS
        limparAA()
    }

    /** Caso 30. */
    fun anularProtecaoPorAA() {
        if (central.ignorarProtecaoPorAA) protecaoAte = 0L
    }

    /** Caso 31. Com [consultar], pode perguntar ao lançador (fundo). */
    fun protecaoAtiva(consultar: Boolean): Boolean {
        val agora = ambiente.agoraMs()
        if (protecaoAte > agora) {
            if (central.ignorarProtecaoPorAA) {
                protecaoAte = 0L
                return false
            }
            return true
        }
        val c = central
        if (c.radioLocal) {
            ambiente.naPrincipal { ativarProtecaoDoRadio("central em rádio") }
            return true
        }
        if (c.androidAutoNativo) return false
        if (!consultar) return false
        if (ambiente.lancador.deixarParaAMidiaNativa("protecao_do_radio")) {
            ambiente.naPrincipal { ativarProtecaoDoRadio("lançador deixou para a nativa") }
            return true
        }
        return false
    }

    // ── Prontidão do Android Auto (casos 32–33), fundo ─────────────────────────────────

    /** Caso 32 (fundo). Guarda o resultado para a principal. */
    fun transporteAAPronto(): Boolean {
        val l = ambiente.lancador
        val pronto = when {
            protecaoAtiva(consultar = true) -> false
            ambiente.usbDaProjecaoPronto() == true -> true
            l.linkAndroidAutoAtivoSeJaLigado("transporte") -> true
            l.audioDoAndroidAutoAtivo("transporte") -> true
            else -> central.androidAutoNativo
        }
        transporteAAProntoGuardado = pronto
        return pronto
    }

    /** Caso 33 (fundo), guardado por 1,5 s (inclusive o "não"). */
    fun sessaoAAPronta(): Boolean {
        val agora = ambiente.agoraMs()
        sessaoAAProntaGuardada?.let { (quando, valor) -> if (agora - quando in 0 until SESSAO_AA_GUARDADA_MS) return valor }
        val l = ambiente.lancador
        val pronta = if (protecaoAtiva(consultar = true)) {
            false
        } else {
            val usb = ambiente.usbDaProjecaoPronto()
            if (usb != null) {
                usb || l.linkAndroidAutoAtivoSeJaLigado("sessao") || l.temJanelaDoAndroidAuto() ||
                    l.audioDoAndroidAutoAtivo("sessao")
            } else {
                l.temJanelaDoAndroidAuto() || l.audioDoAndroidAutoAtivo("sessao") || l.sessaoAndroidAutoPronta("sessao")
            }
        }
        sessaoAAProntaGuardada = agora to pronta
        return pronta
    }

    /** Caso 48 (fundo). */
    fun rotaAAParaOPainel(): Boolean {
        val d = dono
        return when {
            eCarPlay(d) -> false
            eAndroidAuto(d) -> transporteAAPronto()
            central.androidAutoNativo -> transporteAAPronto()
            d != null -> false
            protecaoAtiva(consultar = false) -> false
            else -> transporteAAPronto()
        }
    }

    // ── Sessões (caso 10.2 e 10.4), principal ──────────────────────────────────────────

    /**
     * Publica a sessão escolhida, ou a falta dela. [sessaoAAPronta] já foi calculada em segundo
     * plano (caso 33).
     */
    fun publicarSessao(c: CandidatoDeSessao?, sessaoAAPronta: Boolean) {
        val r = retrato
        val donoAtual = r.pacote
        if (c == null) {
            if (preservarAANativo()) return
            if (RegrasDaMidia.preservarDonoUsb(donoAtual, central, null, false)) return
            limparGeral(preservarProjecao = true)
            return
        }
        // (a) o USB continua dono
        if (RegrasDaMidia.preservarDonoUsb(donoAtual, central, c.pacote, c.tocando)) return
        val titulo = vazioParaNulo(c.titulo)
        val artista = vazioParaNulo(c.artista)
        val album = vazioParaNulo(c.album)
        // (b) reserva do AA
        if (RegrasDaMidia.reservaDoAndroidAuto(
                c.pacote, c.temMetadados, donoAtual, ambiente.projecaoNoPainel(), central
            ) { sessaoAAPronta }
        ) {
            val eraAA = eAndroidAuto(donoAtual)
            val tocando = if (!eraAA || c.tocando) c.tocando else r.tocando
            val permitida = !eraAA || mudouDeValorConhecido(r.titulo, titulo) ||
                mudouDeValorConhecido(r.artista, artista) || mudouDeValorConhecido(r.album, album)
            val novo = r.copy(
                titulo = titulo, artista = artista, album = album, capa = c.capa,
                pacote = ANDROID_AUTO, tocando = tocando,
            )
            escrever(comProgresso(r, novo, c.duracaoMs, c.decorridoMs, c.atualizadoEmMs, false, permitida))
            return
        }
        // (c) manter a projeção
        if (RegrasDaMidia.manterProjecao(donoAtual, c.pacote, c.temMetadados, sessaoAAPronta)) return
        // (d) sessão sem metadados não tira uma projeção
        if (!c.temMetadados && eProjecao(donoAtual)) return
        // (e) a sessão vira o retrato
        val novo = r.copy(
            titulo = titulo, artista = artista, album = album, capa = c.capa,
            pacote = c.pacote, tocando = c.tocando,
        )
        escrever(comProgresso(r, novo, c.duracaoMs, c.decorridoMs, c.atualizadoEmMs, c.podeBuscar, false))
    }

    // ── Notificações (casos 16–18), principal ──────────────────────────────────────────

    /** O dono é projeção e esta notificação é de quem cede a ela (caso 16). */
    fun notificacaoCedeAProjecao(pacote: String): Boolean =
        eProjecao(dono) && !eProjecao(pacote) && pacote in RegrasDaMidia.CEDEM_A_PROJECAO_NA_NOTIFICACAO

    /** Caso 18. */
    fun publicarNotificacao(pacote: String, titulo: String?, artista: String?, album: String?, capa: Bitmap?) {
        if (notificacaoCedeAProjecao(pacote)) return
        val r = retrato
        val t = vazioParaNulo(titulo)
        val a = vazioParaNulo(artista)
        val faixaMudou = pacote != r.pacote || t != r.titulo || a != r.artista
        escrever(
            r.copy(
                titulo = t,
                artista = a,
                album = vazioParaNulo(album),
                capa = if (capa != null || faixaMudou) capa else r.capa,
                pacote = pacote,
                tocando = true,
                duracaoMs = 0L,
                decorridoMs = 0L,
                progressoAtualizadoEmMs = 0L,
                podeBuscar = false,
            )
        )
    }

    // ── CarPlay (casos 19–20), principal ────────────────────────────────────────────────

    fun publicarCarPlay(a: AtualizacaoDoCarPlay) {
        if (a.limpar) {
            limparCarPlay()
            return
        }
        val t = a.titulo?.trim().orEmpty()
        val ar = a.artista?.trim().orEmpty()
        val cc = a.caminhoDaCapa?.trim().orEmpty()
        val assinatura = if (t.isEmpty() && ar.isEmpty() && cc.isEmpty()) null else "$t|$ar|$cc"
        val r = retrato
        val fonteMudou = r.pacote != CARPLAY
        val anterior = assinaturaCarPlay
        val faixaMudou = anterior != null && assinatura != null && anterior != assinatura
        val novo = r.copy(
            titulo = a.titulo?.takeIf { it.isNotBlank() } ?: if (fonteMudou) null else r.titulo,
            artista = a.artista?.takeIf { it.isNotBlank() } ?: if (fonteMudou) null else r.artista,
            album = if (fonteMudou || faixaMudou) null else r.album,
            capa = if (fonteMudou || faixaMudou || a.capa != null) a.capa else r.capa,
            pacote = CARPLAY,
            tocando = a.tocando,
        )
        escrever(comProgresso(r, novo, a.duracaoMs, a.decorridoMs, ambiente.agoraMs(), true, false))
        if (assinatura != null) assinaturaCarPlay = assinatura
    }

    // ── Android Auto pela central (casos 25–26), principal ─────────────────────────────

    private fun podeAplicarAA(transportePronto: Boolean) =
        central.androidAutoNativo && transportePronto && !eCarPlay(dono)

    /** Caso 25. */
    fun aplicarMetadadosAA(info: InfoNaCentral, estado: EstadoNaCentral?, capa: Bitmap?, transportePronto: Boolean) {
        if (!podeAplicarAA(transportePronto)) return
        val r = retrato
        val agora = ambiente.agoraMs()
        val eraAA = eAndroidAuto(r.pacote)
        val fonteMudou = !eraAA
        val faixaMudouPeloEstado = eraAA && (
            mudouDeValorConhecido(r.titulo, info.titulo) || mudouDeValorConhecido(r.artista, info.artista) ||
                mudouDeValorConhecido(r.album, info.album)
            )
        val dimensoes = capa?.let { runCatching { "${it.width}x${it.height}" }.getOrNull() }.orEmpty()
        val tudoVazio = info.titulo == null && info.artista == null && info.album == null && dimensoes.isEmpty()
        val assinatura = if (tudoVazio && info.duracaoMs <= 0) null else
            "${info.titulo.orEmpty()}|${info.artista.orEmpty()}|${info.album.orEmpty()}|${info.duracaoMs}|$dimensoes"
        val anterior = assinaturaAA
        val faixaMudou = anterior != null && assinatura != null && anterior != assinatura
        val resetar = fonteMudou || faixaMudouPeloEstado || faixaMudou || janelaDeTrocaAberta()
        val aceito = estado?.takeIf { RegrasDaMidia.estadoAceito(it.fonte, info.fonte) }

        val resolvido = resolverPeloAlvo(aceito?.tocando, aceito?.progressoValido)
        val tocando = when {
            resolvido != null -> {
                palpiteAA = resolvido
                resolvido
            }
            !eraAA -> palpiteAA
            else -> r.tocando
        }
        val duracao = aceito?.duracaoMs?.takeIf { it > 0 } ?: info.duracaoMs.takeIf { it > 0 } ?: r.duracaoMs
        val decorrido = RegrasDaMidia.progressoNativoResolvido(
            r.decorridoMs, duracao, r.progressoAtualizadoEmMs, agora, tocando, resetar, aceito?.progressoValido
        )
        val novo = r.copy(
            titulo = info.titulo ?: if (fonteMudou) null else r.titulo,
            artista = info.artista ?: if (fonteMudou) null else r.artista,
            album = info.album ?: if (fonteMudou || faixaMudou) null else r.album,
            capa = capa ?: if (fonteMudou) null else r.capa,
            pacote = ANDROID_AUTO,
            tocando = tocando,
        )
        escrever(comProgresso(r, novo, duracao, decorrido, agora, false, resetar))
        if (assinatura != null) assinaturaAA = assinatura
    }

    /** Caso 26. Devolve "aplicou". */
    fun aplicarEstadoAA(estado: EstadoNaCentral?, transportePronto: Boolean): Boolean {
        if (!podeAplicarAA(transportePronto)) return false
        val aceito = estado?.takeIf { it.fonte == CentralDeMidiaLida.FONTE_AA || it.fonte == 0 } ?: return false
        val resolvido = resolverPeloAlvo(aceito.tocando, aceito.progressoValido) ?: return false
        val r = retrato
        val agora = ambiente.agoraMs()
        palpiteAA = resolvido
        val duracao = aceito.duracaoMs.takeIf { it > 0 } ?: r.duracaoMs
        val janela = janelaDeTrocaAberta()
        val decorrido = RegrasDaMidia.progressoNativoResolvido(
            r.decorridoMs, duracao, r.progressoAtualizadoEmMs, agora, resolvido, janela, aceito.progressoValido
        )
        val novo = r.copy(pacote = ANDROID_AUTO, tocando = resolvido)
        escrever(comProgresso(r, novo, duracao, decorrido, agora, false, janela))
        return true
    }

    // ── USB (caso 39), principal ────────────────────────────────────────────────────────

    fun aplicarUsb(f: FaixaUsb) {
        val r = retrato
        val eraDono = r.pacote == USB
        val dimensoes = f.capa?.let { runCatching { "${it.width}x${it.height}" }.getOrNull() }.orEmpty()
        val assinatura = "${f.titulo}|${f.artista.orEmpty()}|${f.album.orEmpty()}|${f.caminho.orEmpty()}|${f.duracaoMs}|$dimensoes"
        val anterior = assinaturaUsb
        val faixaMudou = anterior != null && anterior != assinatura
        val novo = r.copy(
            titulo = f.titulo, artista = f.artista, album = f.album, capa = f.capa,
            pacote = USB, tocando = f.tocando,
        )
        escrever(comProgresso(r, novo, f.duracaoMs, f.decorridoMs, ambiente.agoraMs(), false, !eraDono || faixaMudou))
        assinaturaUsb = assinatura
        ambiente.registrar(
            "usb_media",
            mapOf(
                "etapa" to "published",
                "titulo" to f.titulo,
                "artista" to f.artista,
                "capa" to (dimensoes.ifEmpty { "none" }),
                "tocando" to f.tocando,
            ),
        )
    }

    // ── Dica do volante (caso 63) e rota AA, principal ─────────────────────────────────

    fun dicaDoVolante(tocando: Boolean) {
        escrever(retrato.copy(pacote = ANDROID_AUTO, tocando = tocando))
    }

    /** Sucesso de um comando pela rota AA: o dono passa a ser o AA. */
    fun donoPassaAoAA() {
        val r = retrato
        if (r.pacote != ANDROID_AUTO) escrever(r.copy(pacote = ANDROID_AUTO))
    }

    /** Um buscar aceito (caso 47). */
    fun posicaoBuscada(alvoMs: Long) {
        val r = retrato
        escrever(comProgresso(r, r, r.duracaoMs, alvoMs, ambiente.agoraMs(), r.podeBuscar, true))
    }

    /** Publica o mudo (casos 59 e 62). */
    fun publicarMudo(mudo: Boolean) {
        val r = retrato
        if (r.mudo != mudo) escrever(r.copy(mudo = mudo))
    }

    companion object {
        const val JANELA_DE_TROCA_MS = 4_000L
        const val PROTECAO_DO_RADIO_MS = 12_000L
        const val SESSAO_AA_GUARDADA_MS = 1_500L
    }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

/** Caso 58: o alvo de um tocar/pausar enquanto o carro não reflete. */
internal class AlvoSegurado {
    private var alvo: Boolean? = null
    private var marcadoEm = 0L
    private var decorridoNaMarca = 0L

    @Synchronized
    fun marcar(tocar: Boolean, agora: Long, decorrido: Long) {
        alvo = tocar
        marcadoEm = agora
        decorridoNaMarca = decorrido
    }

    @Synchronized
    fun esquecer() {
        alvo = null
    }

    @Synchronized
    fun resolver(recebido: Boolean?, decorridoRecebido: Long?, agora: Long): Boolean? {
        val a = alvo
        val idade = agora - marcadoEm
        if (a == null || idade < 0 || idade > JANELA_MS) {
            alvo = null
            return recebido
        }
        if (recebido == null || recebido == a) return a
        val andou = decorridoRecebido != null && decorridoRecebido - decorridoNaMarca >= AVANCO_MS
        if (idade >= IDADE_DE_ESCAPE_MS && andou) {
            alvo = null
            return recebido
        }
        return a
    }

    companion object {
        const val JANELA_MS = 45_000L
        const val IDADE_DE_ESCAPE_MS = 8_000L
        const val AVANCO_MS = 3_000L
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
