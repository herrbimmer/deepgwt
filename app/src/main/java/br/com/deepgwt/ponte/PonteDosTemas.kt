// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ponte

import android.content.Context
import android.content.SharedPreferences
import br.com.deepgwt.midia.RadioNowPlaying
import br.com.deepgwt.nucleo.DeepGwt
import br.com.deepgwt.telas.OriginalTempScale
import br.com.deepgwt.veiculo.HeadlightPolicy
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * A ponte entre a página do tema (o WebView do painel) e o app — contrato v1.0
 * (`docs/independencia/specs/ponte-dos-temas.md`).
 *
 * Não abre WebView nem projetor: fala com o painel só por [Painel]. Quem decide QUANDO mandar o quê
 * é o projetor; a ponte é o transporte e as regras do contrato.
 *
 * Threads:
 * - os métodos do `window.Android` ([objetoJs]) rodam na thread do WebView e a página fica parada
 *   até eles voltarem — os getters só leem memória;
 * - toda chamada à página vai pela principal ([Painel.naPrincipal]);
 * - [valorDoCarro] chega das threads do motor; shell, ícones e o carro vão para [Agendador.emSegundoPlano].
 */
class PonteDosTemas internal constructor(
    private val painel: Painel,
    private val fontes: Fontes,
    private val projecao: Projecao,
    private val preferencias: SharedPreferences,
    private val carro: Carro,
    private val agendador: Agendador,
    private val registro: RegistroDaPonte,
    codificadorDeCapa: CodificadorDeCapa,
    private val horaLocal: () -> Int,
    private val arquivoExiste: (String) -> Boolean,
) {

    constructor(
        painel: Painel,
        fontes: Fontes,
        projecao: Projecao,
        preferencias: SharedPreferences,
    ) : this(
        painel,
        fontes,
        projecao,
        preferencias,
        CarroDoVeiculo,
        AgendadorDoApp(),
        RegistroNoLogcat,
        CodificadorDeCapaPng,
        { HeadlightPolicy.horaLocal() },
        { caminho -> File(caminho).exists() },
    )

    companion object {
        /** O arquivo de preferências do app (contexto protegido). Formato gravado nos carros. */
        const val ARQUIVO_DE_PREFERENCIAS = "deepgwt_prefs"

        /** O `SharedPreferences` que a ponte deve receber no app. */
        @JvmStatic
        fun preferenciasDoApp(): SharedPreferences =
            DeepGwt.contextoProtegido.getSharedPreferences(ARQUIVO_DE_PREFERENCIAS, Context.MODE_PRIVATE)

        private const val CHAVE_DA_JANELA_DA_PROJECAO = "projectionWindow"
        private const val INTERVALO_DA_MIDIA_MS = 1_000L
        private const val INTERVALO_DO_FAROL_MS = 30_000L
        private const val LIMITE_DE_RECUSAS_REGISTRADAS = 200
    }

    /** O `window.Android`. Registrar como [ContratoV1.NOME_DO_OBJETO] ANTES da primeira carga. */
    val objetoJs: ObjetoAndroid = ObjetoAndroid(this)

    private val leitor = LeitorDoSharedPreferences(preferencias)
    private val assinaturas = Assinaturas()
    private val cacheDeCapa = CacheDeCapa(codificadorDeCapa)
    private val chavesDisponiveis = JsonDaPonte.listaDeTextos(ContratoV1.CHAVES_LEGIVEIS)

    /** Muda a cada carga pedida: entregas de uma página velha que ainda estavam a caminho são descartadas. */
    private val geracao = AtomicInteger(0)

    @Volatile
    private var encerrada = false

    // ── Só na principal ──
    private val fila = FilaDaPagina()
    private var iniciada = false
    private var lacos: List<Cancelavel> = emptyList()
    private var farolEntregue: String? = null
    private val midiaEntregue = HashMap<String, String>()

    // ── Carro ──
    /** O último valor que o projetor repassou de cada chave (o motor avisa antes de gravar o cache). */
    private val recebidos = ConcurrentHashMap<String, String>()

    /** Deduplicação de [valorDoCarro]; zerada no fim de cada carga. */
    private val ultimoNaCarga = ConcurrentHashMap<String, String>()

    /** Ordem entre a memória do carro e o envio das entregas. */
    private val trava = Any()

    /** Chaves que a ponte já pediu ao carro para acompanhar. */
    private val pedidasAoCarro = HashSet<String>()

    // ── Caches dos getters ──
    private val apps = ConcurrentHashMap<Int, Pair<Int, InfoDoDisplay>>()
    private val versaoDosApps = ConcurrentHashMap<Int, AtomicInteger>()

    @Volatile
    private var cacheDoFundo: Pair<Pair<FundoDoPainel.Estado, String>, String>? = null

    @Volatile
    private var cacheDosModos: Pair<String, String>? = null

    private val recusasRegistradas: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val ouvinteDePreferencias =
        SharedPreferences.OnSharedPreferenceChangeListener { _, chave -> preferenciaMudou(chave) }

    /** A página atual terminou de carregar (ler na principal). */
    val paginaCarregada: Boolean get() = fila.carregada

    // ═══════════════════════════ Ciclo de vida ═══════════════════════════

    /** Liga o ouvinte de preferências, o laço de mídia (1 s) e a reavaliação do farol (30 s). */
    fun iniciar() {
        painel.naPrincipal {
            if (iniciada || encerrada) return@naPrincipal
            iniciada = true
            preferencias.registerOnSharedPreferenceChangeListener(ouvinteDePreferencias)
            lacos = listOf(
                agendador.repetir(INTERVALO_DA_MIDIA_MS) { lacoDaMidia() },
                agendador.repetir(INTERVALO_DO_FAROL_MS) { reavaliarFarol(false) },
            )
        }
    }

    /** Para tudo; chama `window.cleanup()` (se existir) e encerra a batida injetada. */
    fun encerrar() {
        encerrada = true
        geracao.incrementAndGet()
        assinaturas.limpar()
        painel.naPrincipal {
            for (laco in lacos) laco.cancelar()
            lacos = emptyList()
            if (iniciada) preferencias.unregisterOnSharedPreferenceChangeListener(ouvinteDePreferencias)
            iniciada = false
            fila.antesDeCarregar()
            painel.executar(ScriptsDaPagina.encerramento)
        }
    }

    /**
     * A cada carga PEDIDA, na principal, ANTES do pedido (seção 11.1): esquece a caixa da projeção
     * declarada, limpa as assinaturas, marca "não carregada" e descarta a fila.
     */
    fun antesDeCarregar() {
        geracao.incrementAndGet()
        assinaturas.limpar()
        recusasRegistradas.clear()
        cacheDosModos = null
        painel.naPrincipal {
            projecao.caixaDeclaradaPeloTema = null
            fila.antesDeCarregar()
        }
    }

    /**
     * No fim da carga (seção 11.2, itens 1, 2, 6 e 10): marca "carregada", instala as garantias de
     * compatibilidade, zera a deduplicação do carro, manda a fila pendente na ordem e instala a
     * batida própria. O projetor faz o resto (variáveis CSS, farol forçado, `projectionWindow`, lote
     * de estado, cartão, avisos, projeção).
     */
    fun aoTerminarDeCarregar(minBridgeVersion: String?) {
        painel.naPrincipal {
            if (encerrada) return@naPrincipal
            val pendentes = fila.aoTerminarDeCarregar()
            ultimoNaCarga.clear()
            for (mensagem in SequenciaDeCarga.fimDaCarga(minBridgeVersion, pendentes)) {
                painel.executar(mensagem.js, mensagem.resposta)
            }
        }
    }

    // ═══════════════════════════ Do app para a página ═══════════════════════════

    /**
     * Valor novo de uma chave do carro (seção 7.1). Não repete o mesmo valor seguido; as quatro
     * chaves do farol reavaliam o farol e `head_light_status` cru nunca vai à página; entrega pelo
     * apelido, se a página assinou o apelido, senão pela canônica.
     */
    fun valorDoCarro(chave: String, valor: String) {
        synchronized(trava) {
            recebidos[chave] = valor
            if (ultimoNaCarga.put(chave, valor) == valor) return
            if (chave in HeadlightPolicy.CHAVES_DE_ORIGEM) reavaliarFarol(false)
            if (encerrada || chave == ContratoV1.CHAVE_FAROL || !ContratoV1.doCarro(chave)) return
            val nome = assinaturas.nomeParaEntrega(chave) ?: return
            entregar(nome, valor, geracao.get())
        }
    }

    /**
     * Recalcula o farol sintetizado (seção 7.6) e entrega `"1"`/`"0"` se a página assinou e (forçado
     * ou diferente do último entregue).
     */
    fun reavaliarFarol(forcar: Boolean) {
        painel.naPrincipal {
            if (encerrada) return@naPrincipal
            val nome = assinaturas.nomeParaEntrega(ContratoV1.CHAVE_FAROL) ?: return@naPrincipal
            val valor = farolAgora()
            if (!forcar && valor == farolEntregue) return@naPrincipal
            farolEntregue = valor
            painel.executar(ScriptsDaPagina.onDataChanged(nome, valor))
        }
    }

    /** O estado de apps do display 1 ou 3 mudou: entrega as `app.display.<n>.*` assinadas (sem deduplicar). */
    fun appDoDisplayMudou(display: Int) {
        if (display !in ChavesVirtuais.DISPLAYS) return
        versaoDosApps.getOrPut(display) { AtomicInteger(0) }.incrementAndGet()
        apps.remove(display)
        if (ChavesVirtuais.chavesDoDisplay(display).none { assinaturas.contem(it) }) return
        val g = geracao.get()
        agendador.emSegundoPlano {
            val info = infoDoDisplay(display)
            for (campo in ChavesVirtuais.CampoDoDisplay.entries) {
                val nome = ChavesVirtuais.chaveDoDisplay(display, campo)
                if (assinaturas.contem(nome)) entregar(nome, info.valor(campo), g)
            }
        }
    }

    /** `control(chave, valor)` avulso (seção 5.4): fila; `projectionWindow` vai direto. */
    fun controle(chave: String, valor: Any?) {
        val js = ScriptsDaPagina.control(chave, valor)
        if (chave == CHAVE_DA_JANELA_DA_PROJECAO) direto(js) else naFila(js)
    }

    /** Vários `control` num só script (o lote), na ordem do mapa. Fila. */
    fun controles(valores: Map<String, Any?>) {
        if (valores.isEmpty()) return
        naFila(ScriptsDaPagina.lote(valores.toList()))
    }

    /**
     * `onCardChanged(id)` (seção 5.2), fila. [resultado] recebe `faltando`, `contrato` ou o erro, só
     * para registro. Sem reserva para `control('cardId')` (legado Sport, decisão 3).
     */
    fun cartao(id: Int, resultado: ((String) -> Unit)? = null) {
        val resposta: ((String?) -> Unit)? = resultado?.let { r ->
            { json: String? -> r(JsonDaPonte.respostaComoTexto(json).orEmpty()) }
        }
        naFila(ScriptsDaPagina.onCardChanged(id), resposta)
    }

    /** `onKeyEvent(nome)` (seção 5.3), fila. LEFT/RIGHT e nomes fora do contrato não vão. */
    fun tecla(nome: String) {
        if (nome !in ContratoV1.TECLAS) {
            recusar("tecla fora do contrato: $nome")
            return
        }
        naFila(ScriptsDaPagina.onKeyEvent(nome))
    }

    /** Variável CSS no `<html>` (seção 5.7): direto se carregada, senão fila. */
    fun variavelCss(nome: String, valor: String) {
        naFila(ScriptsDaPagina.variavelCss(nome, valor))
    }

    /** `--gauge-num-scale` e, uma vez por documento, a reescrita de `font-size` (seção 5.7). */
    fun escalaDosNumeros(escala: Float) {
        naFila(ScriptsDaPagina.escalaDosNumeros(AjustesVisuais.escalaComoTexto(escala)))
    }

    /**
     * As três variáveis CSS a partir das preferências atuais (`gaugeNumberScale`, `nightModeColor`,
     * `originalPanelTempScale`) — o item 3 da seção 11.2 e o que fazer quando um desses ajustes muda.
     */
    fun aplicarAjustesVisuais() {
        escalaDosNumeros(leitor.numero(AjustesVisuais.PREF_ESCALA_DOS_NUMEROS) ?: 1f)
        val (tinta, brilho) = AjustesVisuais.corNoturna(leitor.somenteTexto(AjustesVisuais.PREF_COR_NOTURNA))
        variavelCss(AjustesVisuais.TINTA_NOTURNA, tinta)
        variavelCss(AjustesVisuais.BRILHO_NOTURNO, brilho)
        val temperatura = OriginalTempScale.normalizar(
            leitor.numero(AjustesVisuais.PREF_ESCALA_DA_TEMPERATURA) ?: OriginalTempScale.PADRAO
        )
        variavelCss(AjustesVisuais.ESCALA_DA_TEMPERATURA, temperatura.toString())
    }

    /**
     * Cortina de abertura presa (seção 5.8). O projetor chama 450 ms depois de o painel voltar a ficar
     * visível. [resultado] recebe o JSON `{presas, app, corpo, oculto}`, ou null sem página carregada.
     */
    fun liberarCortina(resultado: (String?) -> Unit) {
        painel.naPrincipal {
            if (encerrada || !fila.carregada) {
                resultado(null)
                return@naPrincipal
            }
            painel.executar(ScriptsDaPagina.cortina) { resultado(it) }
        }
    }

    /** O retorno de `window.__havalProjectionDebug()` (opcional, só diagnóstico; seção 5.6). */
    fun depuracaoDaProjecao(resultado: (String?) -> Unit) {
        painel.naPrincipal {
            if (encerrada || !fila.carregada) {
                resultado(null)
                return@naPrincipal
            }
            painel.executar(ScriptsDaPagina.depuracaoDaProjecao) { resultado(JsonDaPonte.respostaComoTexto(it)) }
        }
    }

    /** A página recebe [chave] (por ela ou pelo apelido)? */
    fun assinada(chave: String): Boolean = assinaturas.contem(chave) || assinaturas.recebe(chave)

    // ═══════════════════════════ Da página para o app (window.Android) ═══════════════════════════

    internal fun batida() {
        painel.batimento()
    }

    internal fun areaDosApps(x: Int, y: Int, largura: Int, altura: Int) {
        val area = ContratoV1.limitarAreaDosApps(x, y, largura, altura)
        painel.naPrincipal { painel.areaDosApps(area.x, area.y, area.largura, area.altura) }
    }

    internal fun mascaraNativa(nome: String?, visivel: Boolean) {
        val aceito = ContratoV1.mascaraAceita(nome.orEmpty())
        if (aceito == null) {
            recusar("setNativeMaskState recusado: $nome")
            return
        }
        painel.naPrincipal { painel.mascaraNativa(aceito, visivel) }
    }

    internal fun configuracaoDasMascaras(json: String?) {
        val texto = json.orEmpty()
        painel.naPrincipal { painel.configuracaoDasMascaras(texto) }
    }

    internal fun gravarAjuste(chave: String?, valor: String?) {
        if (chave == null || chave !in ContratoV1.CHAVES_DO_MODO_DE_EXIBICAO) {
            recusar("saveSetting recusado: $chave")
            return
        }
        val texto = valor.orEmpty()
        painel.naPrincipal { painel.salvarModoDeExibicao(texto) }
    }

    internal fun escreverNoCarro(chave: String?, valor: String?) {
        val pedida = chave.orEmpty()
        if (pedida == "display") {
            gravarAjuste(pedida, valor)
            return
        }
        val canonica = ContratoV1.canonica(pedida)
        if (canonica !in ContratoV1.CHAVES_GRAVAVEIS) {
            recusar("updateCarData recusado: $pedida")
            return
        }
        val texto = valor.orEmpty().take(ContratoV1.LIMITE_DO_VALOR_NO_CARRO)
        agendador.emSegundoPlano { carro.escrever(canonica, texto) }
    }

    internal fun acaoDoSistema(acao: String?, payload: String?) {
        val nome = acao.orEmpty()
        val dado = payload.orEmpty()
        when (ContratoV1.acaoDoSistema(nome, dado)) {
            AcaoDoSistema.SAIR_DO_AR_NO_MAXIMO -> agendador.emSegundoPlano { carro.sairDoArNoMaximo() }
            AcaoDoSistema.ABRIR_CAMERA_360 -> agendador.emSegundoPlano { carro.abrirCamera360() }
            AcaoDoSistema.DISPENSAR_AVISOS -> painel.naPrincipal { painel.dispensarAvisos() }
            AcaoDoSistema.SO_REGISTRAR -> registro.info("triggerSystemAction $nome: ${dado.take(200)}")
            AcaoDoSistema.PARAR_PROJECAO -> painel.naPrincipal { projecao.parar("pedido pelo tema") }
            AcaoDoSistema.REPROJETAR -> painel.naPrincipal {
                if (!projecao.reprojetarUltimaArea()) projecao.abrirSelecao()
            }
            AcaoDoSistema.ABRIR_SELECAO -> painel.naPrincipal { projecao.abrirSelecao() }
            AcaoDoSistema.RECUSADA -> recusar("triggerSystemAction recusado: $nome")
        }
    }

    internal fun declararCaixaDaProjecao(x: Int, y: Int, largura: Int, altura: Int) {
        val nova = ContratoV1.limitarCaixaDaProjecao(x, y, largura, altura)
        painel.naPrincipal {
            if (projecao.caixaDeclaradaPeloTema == nova) return@naPrincipal
            projecao.caixaDeclaradaPeloTema = nova
            registro.info(if (nova == null) "setProjectionBox: declaração apagada" else "setProjectionBox: $nova")
        }
    }

    internal fun gravarPreferencia(chave: String?, valor: String?) {
        val gravacao = PreferenciasDoTema.gravacao(PreferenciasDoTema.temaAtivo(leitor), chave.orEmpty(), valor.orEmpty())
        if (gravacao == null) {
            recusar("savePreference recusado: chave inválida")
            return
        }
        preferencias.edit().putString(gravacao.first, gravacao.second).apply()
    }

    internal fun definirFundo(tipo: String?, valor: String?) {
        val normalizado = ContratoV1.tipoDeFundo(tipo)
        val caminho = valor.orEmpty().trim()
        if (normalizado != FundoDoPainel.TIPO_TEMA || !ContratoV1.caminhoSeguroNoTema(caminho)) {
            recusar("setClusterBackground recusado: $normalizado")
            return
        }
        gravarFundoDoTema(caminho)
    }

    internal fun definirFundoDoTema(caminho: String?) {
        val limpo = caminho.orEmpty().trim()
        if (!ContratoV1.caminhoSeguroNoTema(limpo)) {
            recusar("setThemeBackground recusado: caminho inseguro")
            return
        }
        if (ContratoV1.tipoDeFundo(leitor.somenteTexto(FundoDoPainel.TIPO)) != FundoDoPainel.TIPO_TEMA) return
        gravarFundoDoTema(limpo)
    }

    private fun gravarFundoDoTema(caminho: String) {
        preferencias.edit()
            .putBoolean(FundoDoPainel.LIGADO, true)
            .putString(FundoDoPainel.TIPO, FundoDoPainel.TIPO_TEMA)
            .putString(FundoDoPainel.VALOR, caminho)
            .apply()
    }

    internal fun origemDoFundo(): String {
        val estado = FundoDoPainel.estado(leitor)
        val tema = try {
            fontes.temaAtivo()
        } catch (e: Exception) {
            ""
        }
        val chave = estado to tema
        cacheDoFundo?.let { (guardada, json) -> if (guardada == chave) return json }
        val json = FundoDoPainel.origem(estado, { dica -> fontes.arquivoDeFundoDoTema(dica) }, arquivoExiste)
        cacheDoFundo = chave to json
        return json
    }

    internal fun lerPreferencia(chave: String?, padrao: String?): String =
        PreferenciasDoTema.ler(leitor, chave.orEmpty(), padrao.orEmpty())

    internal fun modosDeExibicao(): String {
        val tema = try {
            fontes.temaAtivo()
        } catch (e: Exception) {
            ""
        }
        cacheDosModos?.let { (guardado, json) -> if (guardado == tema) return json }
        val json = try {
            val modos = fontes.modosDeExibicaoDoTema()
            if (modos.isEmpty()) "[]" else JsonDaPonte.modosDeExibicao(modos)
        } catch (e: Exception) {
            "[]"
        }
        cacheDosModos = tema to json
        return json
    }

    /** `subscribe(keysJson)` (seção 7.1). */
    internal fun assinar(keysJson: String?) {
        val chaves = JsonDaPonte.lerListaDeChaves(keysJson)
        if (chaves == null) {
            recusar("subscribe: JSON inválido, ignorado")
            return
        }
        val aceitas = ArrayList<Pair<String, String>>(chaves.size)
        for (chave in chaves) {
            val canonica = ContratoV1.canonica(chave)
            if (!ContratoV1.legivel(canonica)) {
                recusar("assinatura recusada: $chave")
                continue
            }
            assinaturas.adicionar(chave)
            aceitas.add(chave to canonica)
        }
        if (aceitas.isEmpty()) return
        val g = geracao.get()
        val lentas = aceitas.map { it.second }.filter { ChavesVirtuais.lenta(it) }.distinct()
        if (lentas.isEmpty()) {
            painel.naPrincipal { entregarIniciais(aceitas, emptyMap(), g) }
        } else {
            agendador.emSegundoPlano {
                val calculados = lentas.associateWith { valorVirtual(it) }
                painel.naPrincipal { entregarIniciais(aceitas, calculados, g) }
            }
        }
        acompanharNoCarro(aceitas.map { it.second }, g)
    }

    /** `unsubscribe(keysJson)`: tira cada chave exatamente como veio. */
    internal fun cancelarAssinatura(keysJson: String?) {
        val chaves = JsonDaPonte.lerListaDeChaves(keysJson)
        if (chaves == null) {
            recusar("unsubscribe: JSON inválido, ignorado")
            return
        }
        for (chave in chaves) assinaturas.remover(chave)
    }

    /** `getCarData(key)` (seção 4, item 19). */
    internal fun lerParaPagina(chave: String?): String {
        val pedida = chave.orEmpty()
        val canonica = ContratoV1.canonica(pedida)
        if (!ContratoV1.legivel(canonica)) {
            recusar("getCarData recusado: $pedida")
            return ""
        }
        return try {
            when {
                canonica == ContratoV1.CHAVE_AVISO_ATIVO -> painel.avisoAtivo.toString()
                canonica == ContratoV1.CHAVE_AVISO_DISPENSADO -> painel.avisoDispensado.toString()
                canonica == ContratoV1.CHAVE_FAROL -> farolAgora()
                canonica.startsWith(ContratoV1.PREFIXO_PREFERENCIAS) -> ""
                canonica.startsWith("app.") -> valorVirtual(canonica)
                else -> lerCarro(canonica).orEmpty()
            }
        } catch (e: Exception) {
            ""
        }
    }

    internal fun chavesDisponiveis(): String = chavesDisponiveis

    internal fun recusarApp(metodo: String, pacote: String?) {
        recusar("$metodo recusado: ${pacote.orEmpty()}")
    }

    // ═══════════════════════════ Por dentro ═══════════════════════════

    private fun naFila(js: String, resposta: ((String?) -> Unit)? = null) {
        painel.naPrincipal {
            if (encerrada) {
                resposta?.invoke(null)
                return@naPrincipal
            }
            if (fila.enviarOuGuardar(MensagemParaPagina(js, resposta))) painel.executar(js, resposta)
        }
    }

    private fun direto(js: String) {
        painel.naPrincipal { if (!encerrada) painel.executar(js) }
    }

    /** `onDataChanged` pela principal; descartada se a página trocou ou a chave deixou de ser assinada. */
    private fun entregar(nome: String, valor: String, g: Int) {
        painel.naPrincipal {
            if (!encerrada && geracao.get() == g && assinaturas.contem(nome)) {
                painel.executar(ScriptsDaPagina.onDataChanged(nome, valor))
            }
        }
    }

    /** Os valores iniciais da assinatura (seção 7.1, passo 3), na ordem das chaves. Na principal. */
    private fun entregarIniciais(aceitas: List<Pair<String, String>>, calculados: Map<String, String>, g: Int) {
        if (encerrada || geracao.get() != g) return
        for ((chave, canonica) in aceitas) {
            if (!assinaturas.contem(chave)) continue
            val valor = calculados[canonica] ?: valorInicial(canonica) ?: continue
            if (canonica == ContratoV1.CHAVE_FAROL) farolEntregue = valor
            if (canonica in ChavesVirtuais.MIDIA_DO_LACO) midiaEntregue[canonica] = valor
            painel.executar(ScriptsDaPagina.onDataChanged(chave, valor))
        }
    }

    /** null = não entregar nada. */
    private fun valorInicial(canonica: String): String? = try {
        when {
            canonica == ContratoV1.CHAVE_AVISO_ATIVO -> painel.avisoAtivo.toString()
            canonica == ContratoV1.CHAVE_AVISO_DISPENSADO -> painel.avisoDispensado.toString()
            canonica.startsWith(ContratoV1.PREFIXO_PREFERENCIAS) -> null
            canonica.startsWith("app.") -> valorVirtual(canonica)
            canonica == ContratoV1.CHAVE_FAROL -> farolAgora()
            else -> lerCarro(canonica)
        }
    } catch (e: Exception) {
        registro.erro("valor inicial de $canonica falhou", e)
        null
    }

    /**
     * Passo 4 da assinatura: pede ao carro para acompanhar as `car.*`/`sys.*` novas (com o farol, as
     * quatro chaves de origem dele) e entrega o primeiro valor das que ainda não tinham valor.
     */
    private fun acompanharNoCarro(canonicas: List<String>, g: Int) {
        val doCarro = canonicas.filter { ContratoV1.doCarro(it) }.distinct()
        if (doCarro.isEmpty()) return
        val novas = LinkedHashSet<String>()
        synchronized(pedidasAoCarro) {
            for (chave in doCarro) if (pedidasAoCarro.add(chave)) novas.add(chave)
            if (ContratoV1.CHAVE_FAROL in doCarro) {
                for (chave in HeadlightPolicy.CHAVES_DE_ORIGEM) if (pedidasAoCarro.add(chave)) novas.add(chave)
            }
        }
        val semValor = doCarro.filter { it != ContratoV1.CHAVE_FAROL && lerCarro(it) == null }
        if (novas.isEmpty() && semValor.isEmpty()) return
        agendador.emSegundoPlano {
            if (novas.isNotEmpty()) {
                try {
                    carro.acompanhar(novas.toList())
                } catch (e: Exception) {
                    registro.erro("acompanhar ${novas.size} chaves falhou", e)
                }
            }
            if (semValor.isNotEmpty()) buscarPrimeirosValores(semValor, novas, g)
        }
    }

    /**
     * Defeito 14.3: o primeiro valor de uma chave que acabou de passar a ser acompanhada. Primeiro o
     * cache; para as que acabaram de ser pedidas e seguem sem valor, uma leitura direta do carro. Só
     * vale valor não vazio, e só se o carro não mandou outro valor dela no meio tempo.
     */
    private fun buscarPrimeirosValores(chaves: List<String>, novas: Set<String>, g: Int) {
        val achados = LinkedHashMap<String, String>()
        val faltando = ArrayList<String>()
        for (chave in chaves) {
            val valor = lerCarro(chave)
            if (!valor.isNullOrEmpty()) achados[chave] = valor else if (chave in novas) faltando.add(chave)
        }
        if (faltando.isNotEmpty()) {
            try {
                for ((chave, valor) in carro.lerDoCarro(faltando)) {
                    if (chave in faltando && valor.isNotEmpty()) achados[chave] = valor
                }
            } catch (e: Exception) {
                registro.erro("leitura direta de ${faltando.size} chaves falhou", e)
            }
        }
        for ((chave, valor) in achados) primeiroValor(chave, valor, g)
    }

    private fun primeiroValor(chave: String, valor: String, g: Int) {
        synchronized(trava) {
            if (recebidos.putIfAbsent(chave, valor) != null) return
            ultimoNaCarga.putIfAbsent(chave, valor)
            if (encerrada || geracao.get() != g) return
            val nome = assinaturas.nomeParaEntrega(chave) ?: return
            entregar(nome, valor, g)
        }
    }

    private fun lerCarro(chave: String): String? = recebidos[chave] ?: try {
        carro.ler(chave)
    } catch (e: Exception) {
        null
    }

    private fun farolAgora(): String {
        // Modo noturno do painel desligado: o tema nunca vê o farol aceso (nenhum letreiro troca de cor).
        if (leitor.booleano(PreferenciasDoTema.NOTURNO_DO_PAINEL) == false) return "0"
        return HeadlightPolicy.resolverComoTexto(
            leitor.somenteTexto(PreferenciasDoTema.FONTE_DO_FAROL) ?: HeadlightPolicy.FONTE_PADRAO,
            HeadlightPolicy.sinaisDe { lerCarro(it) },
            horaLocal(),
        )
    }

    private fun midiaAgora(incluirCapa: Boolean): ValoresDaMidia {
        val app = try {
            fontes.midiaDoApp()
        } catch (e: Exception) {
            MidiaDoApp.NENHUMA
        }
        val radio = RadioNowPlaying.de { lerCarro(it) }
        return RegraDaMidia.calcular(app, radio, incluirCapa) { cacheDeCapa.dataUrl(it) }
    }

    /** Valor de uma `app.*` (seção 7). As `app.preferences.*` não passam por aqui. */
    private fun valorVirtual(canonica: String): String {
        ChavesVirtuais.displayDaChave(canonica)?.let { (display, campo) -> return infoDoDisplay(display).valor(campo) }
        return when {
            canonica == ChavesVirtuais.LAUNCHER -> appsDoLauncher()
            canonica == ChavesVirtuais.DIRECOES -> ChavesVirtuais.VALOR_DAS_DIRECOES
            canonica in ChavesVirtuais.SEMPRE_VAZIAS -> ""
            canonica.startsWith("app.media.") ->
                midiaAgora(canonica == ChavesVirtuais.MIDIA_CAPA).valor(canonica).orEmpty()
            else -> ""
        }
    }

    private fun appsDoLauncher(): String = try {
        JsonDaPonte.appsDoLauncher(fontes.appsDoLauncher())
    } catch (e: Exception) {
        "[]"
    }

    /** O app no topo do display; guardado até [appDoDisplayMudou] avisar que mudou. Pode ir ao shell. */
    private fun infoDoDisplay(display: Int): InfoDoDisplay {
        val versao = versaoDosApps.getOrPut(display) { AtomicInteger(0) }.get()
        apps[display]?.let { (guardada, info) -> if (guardada == versao) return info }
        val info = try {
            val pacote = fontes.appNoTopo(display).orEmpty().trim()
            if (pacote.isEmpty()) InfoDoDisplay.VAZIO else InfoDoDisplay(pacote, fontes.rotuloDoApp(pacote), fontes.iconeDoApp(pacote))
        } catch (e: Exception) {
            registro.erro("app no topo do display $display falhou", e)
            InfoDoDisplay.VAZIO
        }
        if (versaoDosApps[display]?.get() == versao) apps[display] = versao to info
        return info
    }

    /** Laço de 1 s (principal): entrega as `app.media.*` assinadas que mudaram desde a última entrega. */
    private fun lacoDaMidia() {
        if (encerrada) return
        val assinadas = ChavesVirtuais.MIDIA_DO_LACO.filter { assinaturas.contem(it) }
        if (assinadas.isEmpty()) return
        val valores = midiaAgora(ChavesVirtuais.MIDIA_CAPA in assinadas)
        for (nome in assinadas) {
            val valor = valores.valor(nome) ?: continue
            if (midiaEntregue[nome] == valor) continue
            midiaEntregue[nome] = valor
            painel.executar(ScriptsDaPagina.onDataChanged(nome, valor))
        }
    }

    /** Ouvinte de preferências (principal), seção 9.3. */
    private fun preferenciaMudou(chave: String?) {
        if (chave == null || encerrada) return
        if (chave == PreferenciasDoTema.FONTE_DO_FAROL || chave == PreferenciasDoTema.NOTURNO_DO_PAINEL) {
            reavaliarFarol(true)
        }
        val nome = PreferenciasDoTema.nomeDaEntrega(chave, PreferenciasDoTema.temaAtivo(leitor)) {
            assinaturas.contem(it)
        } ?: return
        entregar(nome, leitor.texto(chave).orEmpty(), geracao.get())
    }

    /** Registra uma recusa uma vez por carga (temas repetem a mesma chamada a cada 2 s). */
    private fun recusar(mensagem: String) {
        if (recusasRegistradas.size >= LIMITE_DE_RECUSAS_REGISTRADAS) return
        if (recusasRegistradas.add(mensagem)) registro.aviso(mensagem)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
