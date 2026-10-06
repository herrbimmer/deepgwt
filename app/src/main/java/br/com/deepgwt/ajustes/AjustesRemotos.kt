// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ajustes

import br.com.deepgwt.nucleo.DeepGwt
import br.com.deepgwt.veiculo.Comandos
import br.com.deepgwt.veiculo.Veiculo
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import br.com.deepgwt.BuildConfig
import br.com.deepgwt.licenca.Portao
import br.com.deepgwt.managers.TemaDoPainel
import br.com.deepgwt.nucleo.Preferencias
import br.com.deepgwt.ui.screens.PainelEscolhido
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Os ajustes do DeepGWT vistos de fora: o que o instalador lê e muda em `/ajustes`.
 *
 * TRÊS PROMESSAS
 *
 *  1. **Mudou no computador, vale no carro na hora.** Gravar no `deepgwt_prefs` não basta — parte
 *     do app guarda um espelho em memória (barra, HUD, ASD). Cada ajuste declara o [Efeito] que a
 *     tela do carro dispara, e ele roda aqui depois da gravação.
 *  2. **Mudou no carro, o computador fica sabendo.** Um contador de [revisao] sobe a cada
 *     mudança numa chave do catálogo (ouvinte do SharedPreferences e do barramento). O instalador
 *     espera por ele em `/ajustes/espera` — segura a conexão até mudar ou até o tempo acabar —,
 *     então a atualização chega em menos de um segundo sem ficar perguntando em laço.
 *  3. **A tela do carro aberta não fica mentindo.** As telas leem as preferências uma vez, ao
 *     abrir (`remember { prefs.get… }`). Depois de uma mudança vinda de fora, [geracaoDaTela] sobe
 *     e o `TelaPrincipal` recria a seção aberta, que relê tudo.
 *
 * A gravação é UMA transação na thread principal. Uma só porque a barra e o painel leem chaves
 * relacionadas e duas levas deixariam uma janela com metade do estado novo. Na principal porque
 * lá o Android avisa os ouvintes na hora, dentro do `commit` — assim a revisão que volta na
 * resposta já conta a mudança, e o instalador não acorda de novo para reler o que acabou de gravar.
 */
object AjustesRemotos {
    private const val TAG = "AjustesRemotos"

    /** Formato da conversa. Sobe só se um cliente antigo deixar de entender a resposta. */
    const val VERSAO_DO_FORMATO = 1

    /** Recria a seção aberta no app do carro. Ver `TelaPrincipal`. */
    val geracaoDaTela = mutableIntStateOf(0)

    private fun prefs(): SharedPreferences =
            DeepGwt.contextoProtegido.getSharedPreferences("deepgwt_prefs", Context.MODE_PRIVATE)

    // ── Revisão ────────────────────────────────────────────────────────────────────────────

    private val trava = Object()

    /**
     * Começa no relógio, não em 1: se o app reiniciar, a revisão que o instalador guardou não
     * pode coincidir com a nova por acaso — ele esperaria uma mudança que já aconteceu.
     */
    @Volatile
    var revisao: Long = System.currentTimeMillis()
        private set

    /**
     * Conta uma mudança: quem espera em `/ajustes/espera` acorda e relê. Também chamado quando o
     * nível muda (Alpha 8.0) — os cadeados do instalador mudam sem nenhuma preferência mudar.
     */
    fun tocar() {
        synchronized(trava) {
            revisao += 1
            trava.notifyAll()
        }
    }

    /** Segura até a revisão ser diferente de [desde] ou [ms] passar. Devolve a revisão atual. */
    fun esperar(desde: Long, ms: Long): Long {
        val limite = System.currentTimeMillis() + ms
        synchronized(trava) {
            while (revisao == desde) {
                val resta = limite - System.currentTimeMillis()
                if (resta <= 0) break
                trava.wait(resta)
            }
            return revisao
        }
    }

    // Referências fortes: o SharedPreferences guarda ouvintes em WeakHashMap, e um lambda solto
    // seria recolhido pelo coletor na primeira oportunidade — a revisão pararia de subir sem erro.
    private val ouvinteDasPreferencias =
            SharedPreferences.OnSharedPreferenceChangeListener { _, chave ->
                if (chave == null || chave in CatalogoDeAjustes.chavesObservadas) tocar()
            }

    private val ouvinteDoVeiculo = Veiculo.Ouvinte { chave, _ ->
        if (chave in CatalogoDeAjustes.chavesDoVeiculo) tocar()
    }

    @Volatile private var iniciado = false
    @Volatile private var veiculoMonitorado = false

    @Synchronized
    fun iniciar() {
        if (iniciado) return
        iniciado = true
        runCatching { prefs().registerOnSharedPreferenceChangeListener(ouvinteDasPreferencias) }
                .onFailure { Log.e(TAG, "Sem ouvinte das preferências", it) }
        runCatching { Veiculo.observar(ouvinteDoVeiculo) }
                .onFailure { Log.e(TAG, "Sem ouvinte do barramento", it) }
    }

    /**
     * Pede ao ControlService para avisar mudanças nas chaves de áudio. Sem isso, um ajuste do
     * equalizador feito na tela do carro não chegaria ao ouvinte, e o computador não saberia.
     * Só dá certo com o serviço de pé, então é tentado a cada leitura até pegar.
     */
    private fun garantirMonitoramentoDoVeiculo() {
        if (veiculoMonitorado) return
        if (runCatching { Veiculo.controleVivo }.getOrDefault(false)) {
            Veiculo.acompanhar(CatalogoDeAjustes.chavesDoVeiculo)
            veiculoMonitorado = true
        }
    }

    // ── Leitura ────────────────────────────────────────────────────────────────────────────

    private class LeitorDoMapa(private val mapa: Map<String, *>) : LeitorDePreferencias {
        override fun contem(chave: String) = mapa.containsKey(chave)
        override fun booleano(chave: String, padrao: Boolean) = mapa[chave] as? Boolean ?: padrao
        override fun inteiro(chave: String, padrao: Int) = mapa[chave] as? Int ?: padrao
        override fun texto(chave: String) = mapa[chave] as? String
    }

    private class Estado(
            val valores: JSONObject,
            val indisponiveis: JSONArray,
            val faixas: JSONObject
    )

    private fun lerEstado(): Estado {
        val prefs = prefs()
        val mapa = prefs.all
        val leitor = LeitorDoMapa(mapa)
        val painel = PainelEscolhido.ler(prefs)
        runCatching { garantirMonitoramentoDoVeiculo() }

        val valores = JSONObject()
        val indisponiveis = JSONArray()
        val faixas = JSONObject()
        for (a in CatalogoDeAjustes.todos) {
            val valor: Any? =
                    when (val g = a.guardado) {
                        is Guardado.Preferencia ->
                                a.lerDe?.invoke(leitor)
                                        ?: ConversaoDeAjuste.doDisco(a, g.formato, mapa[g.chave])
                                        ?: a.padrao
                        is Guardado.Painel ->
                                when (g.parte) {
                                    ParteDoPainel.TEMA_CUSTOMIZADO -> painel.temaCustomizado
                                    ParteDoPainel.INFORMACOES -> painel.informacoes
                                    ParteDoPainel.CIRCULO -> painel.circulo
                                }
                        is Guardado.TemaAtivo -> TemaDoPainel.ativo(prefs)
                        is Guardado.Veiculo -> {
                            val lido = runCatching { Veiculo.ler(g.chave) }.getOrNull()
                            val convertido = ConversaoDeAjuste.doVeiculo(g, lido)
                            if (convertido == null) indisponiveis.put(a.id)
                            g.chaveDaFaixa?.let { chave ->
                                val faixa = ConversaoDeAjuste.lerFaixa(runCatching { Veiculo.ler(chave) }.getOrNull())
                                if (faixa != null) faixas.put(a.id, JSONArray().put(faixa.start).put(faixa.endInclusive))
                            }
                            convertido
                        }
                    }
            valores.put(a.id, valor ?: JSONObject.NULL)
        }
        return Estado(valores, indisponiveis, faixas)
    }

    /** `GET /ajustes`. Sem [comCatalogo], só os valores — é o que o instalador relê a cada mudança. */
    fun lerJson(comCatalogo: Boolean): JSONObject {
        val estado = lerEstado()
        val json =
                JSONObject()
                        .put("ok", true)
                        .put("formato", VERSAO_DO_FORMATO)
                        .put("app", BuildConfig.VERSION_NAME)
                        .put("revisao", revisao)
                        .put("valores", estado.valores)
                        .put("indisponiveis", estado.indisponiveis)
                        .put("faixas", estado.faixas)
                        // Alpha 8.0: o nível do carro agora. Cada ajuste diz o que pede e se está
                        // bloqueado — o instalador desenha o cadeado sem ter a tabela.
                        .put("nivel", Portao.nivel().id)
        if (comCatalogo) {
            val secoes = JSONArray()
            SecaoDeAjustes.entries.forEach {
                // A seção só aparece trancada quando TODOS os ajustes dela estão: "Gestos, voz e
                // macros" tem a barra lateral e o gesto do círculo, que são Drive. O nível exigido é
                // o menor que abre alguma coisa dela.
                val daSecao = CatalogoDeAjustes.todos.filter { a -> a.secao == it }
                val menor = daSecao.map { a -> a.recursoEfetivo.nivel }.minOrNull() ?: it.recurso.nivel
                secoes.put(
                        JSONObject()
                                .put("id", it.id)
                                .put("titulo", it.titulo)
                                .put("descricao", it.descricao)
                                .put("recurso", it.recurso.id)
                                .put("nivelExigido", menor.id)
                                .put("bloqueado", daSecao.isNotEmpty() && daSecao.none { a -> Portao.permite(a.recursoEfetivo) })
                )
            }
            val ajustes = JSONArray()
            CatalogoDeAjustes.todos.forEach { ajustes.put(descrever(comOpcoesDoMomento(it))) }
            json.put("secoes", secoes).put("ajustes", ajustes)
        }
        return json
    }

    private fun descrever(a: Ajuste): JSONObject {
        val o =
                JSONObject()
                        .put("id", a.id)
                        .put("secao", a.secao.id)
                        .put("rotulo", a.rotulo)
                        .put("tipo", a.tipo.id)
                        .put("padrao", a.padrao ?: JSONObject.NULL)
                        .put("efeito", a.efeito.nome)
                        .put(
                                "origem",
                                when (a.guardado) {
                                    is Guardado.Preferencia -> "preferencia"
                                    is Guardado.Veiculo -> "veiculo"
                                    is Guardado.Painel -> "painel"
                                    is Guardado.TemaAtivo -> "tema"
                                }
                        )
        a.ajuda?.let { o.put("ajuda", it) }
        a.min?.let { o.put("min", it) }
        a.max?.let { o.put("max", it) }
        a.passo?.let { o.put("passo", it) }
        a.unidade?.let { o.put("unidade", it) }
        a.maxCaracteres?.let { o.put("maxCaracteres", it) }
        a.aviso?.let { o.put("aviso", it) }
        if (a.inteiro) o.put("inteiro", true)
        if (a.avancado) o.put("avancado", true)
        if (a.soNoComputador) o.put("soNoComputador", true)
        o.put("recurso", a.recursoEfetivo.id)
        o.put("nivelExigido", a.recursoEfetivo.nivel.id)
        if (!Portao.permite(a.recursoEfetivo)) o.put("bloqueado", true)
        if (a.opcoes.isNotEmpty()) {
            val opcoes = JSONArray()
            a.opcoes.forEach { opcoes.put(JSONObject().put("id", it.id).put("rotulo", it.rotulo)) }
            o.put("opcoes", opcoes)
        }
        a.visivelSe?.let { c ->
            o.put("visivelSe", JSONObject().put("id", c.id).put("valores", JSONArray(c.valores.toList())))
        }
        return o
    }

    // ── Aplicação ──────────────────────────────────────────────────────────────────────────

    private class Pedido(val ajuste: Ajuste, val valor: Any)

    /**
     * O catálogo é fixo, mas a lista de temas não: ela é a pasta `files/themes` agora. Tanto a
     * descrição que o instalador recebe quanto a validação do que ele manda usam esta lista, para
     * nunca aceitar uma pasta que não existe nem recusar um tema que acabou de chegar.
     */
    private fun comOpcoesDoMomento(a: Ajuste): Ajuste {
        if (a.guardado !is Guardado.TemaAtivo) return a
        val temas = runCatching { TemaDoPainel.listar(DeepGwt.contexto) }.getOrNull() ?: return a
        return a.copy(opcoes = temas.map {
            val casta = if (it.casta == br.com.deepgwt.managers.CastaDoTema.LEGACY) "Legacy" else "XTRA"
            Opcao(it.pasta, if (it.embutido) "${it.nome} (embutido)" else "${it.nome} ($casta)")
        })
    }

    /**
     * `POST /ajustes`. [entrada] é `{id: valor}`; o que não passar na validação volta em
     * `recusados` e o resto é aplicado — uma opção ruim não derruba o lote inteiro.
     */
    fun aplicar(entrada: Map<String, Any?>): JSONObject {
        val recusados = JSONArray()
        val avisos = JSONArray()
        val falhas = mutableListOf<String>()
        val aceitos = mutableListOf<Pedido>()


        for ((id, bruto) in entrada) {
            val ajuste = CatalogoDeAjustes.ajuste(id)
            if (ajuste == null) {
                recusados.put(recusa(id, "ajuste desconhecido por esta versão do app"))
                continue
            }
            if (!Portao.permite(ajuste.recursoEfetivo)) {
                recusados.put(recusa(id, motivoDoNivel(ajuste.recursoEfetivo)))
                continue
            }
            val faixa =
                    (ajuste.guardado as? Guardado.Veiculo)?.chaveDaFaixa?.let { chave ->
                        ConversaoDeAjuste.lerFaixa(runCatching { Veiculo.ler(chave) }.getOrNull())
                    }
            when (val v = ConversaoDeAjuste.validar(comOpcoesDoMomento(ajuste), bruto, faixa)) {
                is Validacao.Valido -> aceitos += Pedido(ajuste, v.valor)
                is Validacao.Recusado -> recusados.put(recusa(id, v.motivo))
            }
        }

        val preferencias = aceitos.filter { it.ajuste.guardado is Guardado.Preferencia }
        val doPainel = aceitos.filter { it.ajuste.guardado is Guardado.Painel }
        val doVeiculo = aceitos.filter { it.ajuste.guardado is Guardado.Veiculo }
        val doTema = aceitos.filter { it.ajuste.guardado is Guardado.TemaAtivo }
        val efeitos = aceitos.map { it.ajuste.efeito }.toSet()

        // 1. Preferências, painel, tema e efeitos que mexem em estado de tela: thread principal.
        if (preferencias.isNotEmpty() || doPainel.isNotEmpty() || doTema.isNotEmpty()) {
            val erro = naThreadPrincipal {
                val prefs = prefs()
                val editor = prefs.edit()
                preferencias.forEach { p ->
                    val g = p.ajuste.guardado as Guardado.Preferencia
                    gravar(editor, g.chave, ConversaoDeAjuste.paraDisco(g.formato, p.valor))
                    p.ajuste.espelhos.forEach { e ->
                        gravar(editor, e.chave, ConversaoDeAjuste.paraDisco(e.formato, e.derivar(p.valor)))
                    }
                }
                editor.commit()
                if (doPainel.isNotEmpty()) aplicarPainel(prefs, doPainel, falhas)
                // Depois do painel: um lote que liga o tema customizado E escolhe o tema tem de
                // encontrar o tema customizado já ligado.
                doTema.forEach { p ->
                    val r = TemaDoPainel.aplicarPorPasta(DeepGwt.contexto, prefs, p.valor as String)
                    if (r is TemaDoPainel.Resultado.Recusado) recusados.put(recusa(p.ajuste.id, r.motivo))
                }
                efeitos.filter { it in EFEITOS_DA_THREAD_PRINCIPAL }.forEach { executar(it, falhas, avisos) }
                geracaoDaTela.intValue += 1
            }
            if (erro != null) {
                return JSONObject()
                        .put("ok", false)
                        .put("error", erro)
                        .put("revisao", revisao)
            }
        }

        // 2. Barramento: chamadas de binder, fora da principal.
        var escreveuNoVeiculo = false
        doVeiculo.forEach { p ->
            val g = p.ajuste.guardado as Guardado.Veiculo
            val ok =
                    runCatching { Veiculo.escreverConferindo(g.chave, ConversaoDeAjuste.paraVeiculo(g, p.valor)) }
                                    .getOrDefault(false)
            if (ok) escreveuNoVeiculo = true
            else recusados.put(recusa(p.ajuste.id, "o carro não aceitou (serviço do veículo indisponível)"))
        }

        // 3. O resto dos efeitos. Os demorados (shell) vão para uma thread própria: a resposta
        //    não espera por eles, e o resultado aparece na próxima leitura.
        efeitos.filterNot { it in EFEITOS_DA_THREAD_PRINCIPAL || it in EFEITOS_DEMORADOS }
                .filterNot { it == Efeito.ACUSTICA && !escreveuNoVeiculo }
                .forEach { executar(it, falhas, avisos) }
        val demorados = efeitos.filter { it in EFEITOS_DEMORADOS }
        if (demorados.isNotEmpty()) {
            Thread({ demorados.forEach { executar(it, mutableListOf(), JSONArray()) } }, "ajustes-remotos").start()
            demorados.forEach { avisos.put("\"${it.nome}\" está sendo aplicado no carro; pode levar alguns segundos.") }
        }

        val estado = lerEstado()
        val aplicados = JSONArray()
        aceitos.map { it.ajuste.id }
                .filterNot { id -> (0 until recusados.length()).any { recusados.getJSONObject(it).optString("id") == id } }
                .forEach { aplicados.put(it) }
        val falhasJson = JSONArray()
        falhas.forEach { falhasJson.put(it) }
        return JSONObject()
                .put("ok", true)
                .put("tudoCerto", recusados.length() == 0 && falhas.isEmpty())
                .put("aplicados", aplicados)
                .put("recusados", recusados)
                .put("falhas", falhasJson)
                .put("avisos", avisos)
                .put("revisao", revisao)
                .put("valores", estado.valores)
                .put("indisponiveis", estado.indisponiveis)
                .put("faixas", estado.faixas)
    }

    private fun recusa(id: String, motivo: String) = JSONObject().put("id", id).put("motivo", motivo)

    /** O texto da recusa por nível — o mesmo que o cadeado da tela do carro mostra. */
    fun motivoDoNivel(recurso: br.com.deepgwt.licenca.Recurso): String =
            "disponível no ${recurso.nivel.titulo} (${recurso.titulo})"

    private fun gravar(editor: SharedPreferences.Editor, chave: String, valor: Any) {
        when (valor) {
            is Boolean -> editor.putBoolean(chave, valor)
            is Int -> editor.putInt(chave, valor)
            is Float -> editor.putFloat(chave, valor)
            is String -> editor.putString(chave, valor)
            else -> error("tipo sem gravação: ${valor::class.java.simpleName}")
        }
    }

    /** Devolve a mensagem de erro, ou `null` se o bloco rodou até o fim. */
    private fun naThreadPrincipal(bloco: () -> Unit): String? {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return runCatching(bloco).exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName }
        }
        val feito = CountDownLatch(1)
        var falha: Throwable? = null
        Handler(Looper.getMainLooper()).post {
            try {
                bloco()
            } catch (t: Throwable) {
                falha = t
            } finally {
                feito.countDown()
            }
        }
        if (!feito.await(8, TimeUnit.SECONDS)) return "o app do carro não respondeu a tempo"
        return falha?.let {
            Log.e(TAG, "Falha ao gravar ajustes", it)
            it.message ?: it.javaClass.simpleName
        }
    }

    /**
     * As três escolhas do painel viram um modo e quatro chaves antigas — o mesmo caminho do
     * interruptor de Telas (`aplicarPainel`), inclusive o que ele faz depois de gravar.
     */
    private fun aplicarPainel(prefs: SharedPreferences, pedidos: List<Pedido>, falhas: MutableList<String>) {
        val anterior = PainelEscolhido.ler(prefs)
        var novo = anterior
        pedidos.forEach { p ->
            val ligado = p.valor as Boolean
            novo =
                    when ((p.ajuste.guardado as Guardado.Painel).parte) {
                        ParteDoPainel.TEMA_CUSTOMIZADO -> novo.copy(temaCustomizado = ligado)
                        ParteDoPainel.INFORMACOES -> novo.copy(informacoes = ligado)
                        ParteDoPainel.CIRCULO -> novo.copy(circulo = ligado)
                    }
        }
        if (novo == anterior) return
        try {
            trocarEscolhasDoPainel(prefs, anterior, novo)
        } catch (t: Throwable) {
            Log.e(TAG, "Falha ao trocar o modo do painel", t)
            falhas += Efeito.PAINEL.nome
        }
    }

    /**
     * O que o interruptor de Telas faz: grava as escolhas e acorda quem depende delas. Também é o
     * caminho da mudança de nível (`licenca.MudancaDeNivel`). Na thread principal.
     */
    fun trocarEscolhasDoPainel(prefs: SharedPreferences, anterior: PainelEscolhido, novo: PainelEscolhido) {
        if (novo == anterior) return
        PainelEscolhido.aplicar(DeepGwt.contexto, prefs, anterior, novo)
        br.com.deepgwt.projection.ScreenProjectionController.aoMudarAsEscolhasDoPainel()
        Comandos.garantirAppsDoSistema()
        if (novo.modo.usaCamadaDoPainel) Comandos.iniciarPulsoDoPainel()
    }

    private val EFEITOS_DA_THREAD_PRINCIPAL =
            setOf(
                    Efeito.BARRA,
                    Efeito.BARRA_SERVICO,
                    Efeito.HUD,
                    Efeito.PAINEL_ESCALA,
                    Efeito.PAINEL_COR_NOTURNA,
                    Efeito.PAINEL_FONTE_NOTURNA,
                    Efeito.PAINEL_TEMPERATURA,
                    Efeito.MACROS,
                    Efeito.AUDIO_SOFTWARE
            )

    private val EFEITOS_DEMORADOS = setOf(Efeito.APPS_DO_SISTEMA, Efeito.ESCUTA_DE_VOZ)

    /** Cada ramo chama o que a tela do carro chama depois de gravar aquele ajuste. */
    private fun executar(efeito: Efeito, falhas: MutableList<String>, avisos: JSONArray) {
        val prefs = prefs()
        val contexto = DeepGwt.contexto
        try {
            when (efeito) {
                Efeito.NENHUM, Efeito.PAINEL -> Unit
                Efeito.BARRA -> br.com.deepgwt.barra.Barra.recarregarPreferencias()
                Efeito.BARRA_SERVICO -> {
                    val ligada = prefs.getBoolean(Preferencias.ENABLE_PERSISTENT_BOTTOM_BAR.chave, false)
                    if (!ligada) {
                        br.com.deepgwt.barra.Barra.parar(contexto)
                    } else if (Settings.canDrawOverlays(contexto)) {
                        br.com.deepgwt.barra.Barra.mostrar(contexto)
                    } else {
                        // A tela do carro abre o pedido de permissão; daqui não dá para tocar nele.
                        avisos.put(
                                "A barra ficou ligada, mas o app ainda não tem permissão para desenhar sobre " +
                                        "outros apps. Abra Configurações no carro e ligue a barra por lá uma vez."
                        )
                    }
                    br.com.deepgwt.barra.Barra.recarregarPreferencias()
                }
                Efeito.ASD -> br.com.deepgwt.managers.asd.AsdManager.reconciliarComAsPreferencias()
                Efeito.HUD -> {
                    br.com.deepgwt.hud.HudSettings.recarregar()
                    val hud = br.com.deepgwt.hud.HudOverlayController
                    if (hud.isEnabled()) hud.restoreIfEnabled() else hud.hide()
                    hud.requestRedraw()
                }
                Efeito.LUZ_AMBIENTE -> br.com.deepgwt.luz.LuzAmbiente.reaplicar(contexto)
                Efeito.GESTOS -> br.com.deepgwt.managers.QuickGesturesManager.startIfEnabled()
                Efeito.PAINEL_LATERAL ->
                        // setEnabled e não reapply: reapply só ESCONDE; quem desligou pelo computador
                        // precisa ver a barra voltar.
                        br.com.deepgwt.barra.PainelLateral.setEnabled(
                                prefs.getBoolean(Preferencias.HIDE_LEFT_NAV_PANE.chave, false)
                        )
                Efeito.BRILHO ->
                        br.com.deepgwt.brilho.BrilhoAutomatico.atualizar()
                Efeito.APPS_DO_SISTEMA -> Comandos.garantirAppsDesativados()
                Efeito.AVAS ->
                        Comandos.somParaPedestres(!prefs.getBoolean(Preferencias.DISABLE_AVAS.chave, false))
                Efeito.MONITORAMENTO ->
                        Comandos.monitoramento(!prefs.getBoolean(Preferencias.DISABLE_MONITORING.chave, false))
                Efeito.VIDRO_AO_TRANCAR ->
                        Comandos.fecharJanelasAoTrancar(
                                        prefs.getBoolean(Preferencias.CLOSE_WINDOW_ON_LOCK_VEHICLE.chave, false)
                                )
                Efeito.ESCUTA_DE_VOZ -> {
                    val relatorio = Comandos.aplicarDespertarPorVoz()
                    Log.i(TAG, "Escuta de voz: $relatorio")
                }
                Efeito.PAINEL_ESCALA ->
                        br.com.deepgwt.telas.Painel.aplicarEscalaDosNumeros()
                Efeito.PAINEL_COR_NOTURNA ->
                        br.com.deepgwt.telas.Painel.aplicarCorDoModoNoturno()
                Efeito.PAINEL_TEMPERATURA ->
                        br.com.deepgwt.telas.Painel.aplicarEscalaDaTemperatura()
                Efeito.PAINEL_FONTE_NOTURNA ->
                        br.com.deepgwt.telas.Painel.republicarFarol()
                Efeito.AUDIO_SOFTWARE -> br.com.deepgwt.managers.AudioEffectsManager.applyFromPrefs()
                Efeito.ACUSTICA -> br.com.deepgwt.managers.OemAudioDsp.requestApplyAcoustics()
                Efeito.HEV -> Comandos.aplicarAlvoDeCargaHev("INSTALADOR")
                Efeito.MACROS ->
                        br.com.deepgwt.macros.MacroStore.setEnabled(
                                prefs.getBoolean(Preferencias.MACROS_ENABLED.chave, true)
                        )
                // É por aqui que o instalador desfaz o launcher 3D pela rede: a chave desligada
                // devolve a tela de fábrica (preferência do Android incluída) e desliga a guarda.
                Efeito.TELA_INICIAL ->
                        br.com.deepgwt.managers.HomeLauncherController.aplicarEscolhaGravada(contexto)
                                .onFailure { avisos.put("Tela inicial: ${it.message}") }
            }
        } catch (t: Throwable) {
            // Throwable: um subsistema ausente nesta central chega como Error. O resto do lote
            // continua valendo, e o nome entra no relatório.
            Log.e(TAG, "Falha ao aplicar \"${efeito.nome}\"", t)
            falhas += efeito.nome
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
