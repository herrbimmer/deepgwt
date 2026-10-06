// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.macros

import br.com.deepgwt.veiculo.Veiculo
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import br.com.deepgwt.barra.EstadoDaBarra
import br.com.deepgwt.barra.RetornoDeGesto

/** O cartão de confirmação, publicado na thread principal. Ver [RetornoDeGesto]. */
object MacroFeedback {
    fun mostrar(texto: String) {
        Handler(Looper.getMainLooper()).post {
            EstadoDaBarra.retornoDeGesto = RetornoDeGesto(
                tipo = RetornoDeGesto.Tipo.MACRO,
                valor = 0f,
                minimo = 0f,
                maximo = 1f,
                degraus = 0,
                noLimite = false,
                carimbo = SystemClock.elapsedRealtime(),
                texto = texto
            )
        }
    }
}

/**
 * Quem avalia os gatilhos e roda a fila de ações.
 *
 * ## Dispara na BORDA, não no nível
 *
 * "Quando a velocidade passar de 80" tem de acontecer no instante em que ela cruza os 80, uma vez.
 * Se o motor avaliasse o nível ("está acima de 80?"), a macro rodaria de novo a cada leitura do
 * velocímetro enquanto o carro estivesse em rodovia — dezenas de vezes por minuto, cada uma
 * escrevendo no barramento. Por isso cada macro guarda o resultado anterior da própria condição em
 * [estadoAnterior], e só executa na transição falso -> verdadeiro.
 *
 * ## Por que o estado é semeado no start()
 *
 * Ao subir com o carro já a 100 km/h, a condição "acima de 80" já nasce verdadeira. Sem semear,
 * a primeira leitura pareceria uma borda e toda macro de rodovia dispararia ao ligar o carro.
 * [semear] resolve isso lendo o valor atual antes de escutar: o motor começa sabendo onde o carro
 * está, e espera uma mudança de verdade.
 *
 * ## Por que a execução tem thread própria
 *
 * Escrever no ControlService é chamada de binder e a fila pode ter uma ação "Esperar" de segundos.
 * Rodar isso na thread que entrega [Veiculo.Ouvinte] travaria a entrega de TODOS os outros sinais do
 * carro pelo tempo da macro — inclusive os que a barra inferior e o painel desenham.
 *
 * Uma thread só, e não uma por macro: duas macros escrevendo no clima ao mesmo tempo produziriam
 * uma ordem que depende do escalonador. Enfileiradas, a segunda vê o carro como a primeira o
 * deixou.
 */
object MacroEngine {

    private const val TAG = "MacroEngine"

    /** Teto de espera de uma ação "Esperar", para uma macro não segurar a fila indefinidamente. */
    private const val ESPERA_MAX_MS = 30_000L

    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    @Volatile private var rodando = false

    // Os tres mapas sao escritos pela thread que entrega [Veiculo.Ouvinte] e lidos/escritos pela
    // thread de execucao e pela da tela (o botao "Executar"). ConcurrentHashMap em vez de HashMap
    // porque um redimensionamento concorrente de HashMap nao apenas perde escrita: ele pode entrar
    // em laco dentro do proprio mapa.
    /** Último resultado da condição de gatilho de cada macro. Ver a nota sobre borda. */
    private val estadoAnterior = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /** Último valor visto por chave, para o comparador "mudou". */
    private val ultimoValor = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Quando cada macro rodou pela última vez (relógio monotônico). */
    private val ultimaExecucao = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private val ouvinte = Veiculo.Ouvinte { key, value -> aoMudarSinal(key, value ?: "") }

    /** Guardado numa `val` para o start/stop repetido não empilhar ouvintes iguais na loja. */
    private val aoMudarAsMacros: () -> Unit = { reconciliar("macros alteradas") }

    @Synchronized
    fun start() {
        if (rodando) return
        rodando = true
        thread = HandlerThread("MacroEngine").also {
            it.start()
            handler = Handler(it.looper)
        }
        MacroStore.observar(aoMudarAsMacros)
        reconciliar("start")
        runCatching { Veiculo.observar(ouvinte) }
            .onFailure { Log.e(TAG, "Falha ao escutar o carro", it) }
        Log.w(TAG, "Motor de macros de pé")
    }

    @Synchronized
    fun stop() {
        if (!rodando) return
        rodando = false
        runCatching { Veiculo.pararDeObservar(ouvinte) }
        handler?.removeCallbacksAndMessages(null)
        thread?.quitSafely()
        thread = null
        handler = null
        Log.w(TAG, "Motor de macros parado")
    }

    /**
     * Registra no carro as chaves que os gatilhos precisam e re-semeia o estado.
     *
     * Chamado no start e sempre que as macros mudam: uma macro nova com gatilho numa chave que
     * ninguém monitorava nunca receberia [Veiculo.Ouvinte] sem isto.
     */
    fun reconciliar(motivo: String) {
        // Registrar chaves e semear leem o carro por binder. Chamado da tela (salvar uma macro),
        // isso aconteceria na thread principal e travaria o toque pelo tempo das leituras -- que
        // sao uma por gatilho. A fila do motor ja existe para isso.
        val h = handler
        if (h != null) {
            if (Looper.myLooper() == h.looper) reconciliarAgora(motivo)
            else h.post { reconciliarAgora(motivo) }
            return
        }
        // Sem motor de pé (tela aberta com o serviço principal parado) ainda não pode rodar na
        // principal: as leituras são as mesmas e travariam o toque do mesmo jeito.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Thread({ reconciliarAgora(motivo) }, "MacroEngine-reconciliar").start()
        } else {
            reconciliarAgora(motivo)
        }
    }

    private fun reconciliarAgora(motivo: String) {
        val chaves = LinkedHashSet<String>()
        MacroStore.macros.filter { it.enabled }.forEach { macro ->
            (macro.trigger as? MacroTrigger.Signal)?.let { chaves.add(it.condition.key) }
            macro.conditions.forEach { chaves.add(it.key) }
        }
        if (chaves.isNotEmpty()) {
            runCatching { Veiculo.acompanhar(chaves) }
                .onFailure { Log.e(TAG, "Falha ao registrar chaves de macro", it) }
        }
        semear()
        Log.w(TAG, "Macros reconciliadas ($motivo): ${chaves.size} chaves de gatilho")
    }

    private fun semear() {
        estadoAnterior.clear()
        MacroStore.macros.forEach { macro ->
            val gatilho = macro.trigger as? MacroTrigger.Signal ?: return@forEach
            val atual = runCatching { Veiculo.ler(gatilho.condition.key) }.getOrNull()
            if (atual != null) ultimoValor[gatilho.condition.key] = atual
            // `?: false`: sinal ainda sem leitura conta como "condição não atendida". A primeira
            // leitura de verdade vira borda, que é o comportamento certo -- o carro acabou de dizer
            // algo que a gente não sabia.
            estadoAnterior[macro.id] = gatilho.condition.comparator
                .matches(atual, gatilho.condition.value) ?: false
        }
    }

    private fun aoMudarSinal(key: String, value: String) {
        if (!rodando || !MacroStore.isEnabled()) return
        val anteriorDaChave = ultimoValor.put(key, value)
        MacroStore.macros.forEach { macro ->
            if (!macro.enabled) return@forEach
            val gatilho = macro.trigger as? MacroTrigger.Signal ?: return@forEach
            if (gatilho.condition.key != key) return@forEach

            val atende = if (gatilho.condition.comparator == MacroComparator.CHANGED) {
                anteriorDaChave != null && anteriorDaChave != value
            } else {
                gatilho.condition.comparator.matches(value, gatilho.condition.value) ?: false
            }

            val antes = estadoAnterior[macro.id] ?: false
            estadoAnterior[macro.id] = atende
            // "Mudou" não tem nível: toda mudança é uma borda. Os demais só disparam na subida.
            val borda = if (gatilho.condition.comparator == MacroComparator.CHANGED) atende
            else atende && !antes
            if (!borda) return@forEach
            if (!condicoesExtrasAtendidas(macro)) {
                Log.w(TAG, "'${macro.name}' teve o gatilho, mas uma condição extra barrou")
                return@forEach
            }
            executar(macro, "sinal $key=$value")
        }
    }

    private fun condicoesExtrasAtendidas(macro: Macro): Boolean {
        if (macro.conditions.isEmpty()) return true
        return macro.conditions.all { c ->
            val atual = runCatching { Veiculo.ler(c.key) }.getOrNull()
            // Condição sobre sinal que o carro não respondeu NÃO vale como atendida: uma macro que
            // tranca vidros "só se o carro estiver parado" não pode rodar porque a velocidade veio
            // nula.
            c.comparator.matches(atual, c.value) ?: false
        }
    }

    /** Roda um gesto reconhecido: todas as macros armadas nele. */
    fun executarPorGesto(gestureId: String) {
        if (!MacroStore.isEnabled()) return
        val alvos = MacroStore.porGesto(gestureId)
        if (alvos.isEmpty()) {
            Log.w(TAG, "Gesto $gestureId reconhecido, mas nenhuma macro está armada nele")
            return
        }
        alvos.forEach { macro ->
            if (condicoesExtrasAtendidas(macro)) executar(macro, "gesto")
            else Log.w(TAG, "'${macro.name}': gesto reconhecido, condição extra barrou")
        }
    }

    /**
     * Enfileira a macro.
     *
     * [ignorarJanela] existe para o botão "Executar" da tela: quem tocou o botão acabou de pedir,
     * e a janela morta que protege contra oscilação do barramento não tem nada a ver com um toque
     * deliberado.
     */
    fun executar(macro: Macro, motivo: String, ignorarJanela: Boolean = false) {
        val agora = SystemClock.elapsedRealtime()
        if (!ignorarJanela) {
            val ultima = ultimaExecucao[macro.id]
            val janelaMs = macro.cooldownSec.coerceAtLeast(0) * 1000L
            if (ultima != null && agora - ultima < janelaMs) {
                Log.w(TAG, "'${macro.name}' ignorada: dentro da janela de ${macro.cooldownSec}s")
                return
            }
        }
        ultimaExecucao[macro.id] = agora
        val h = handler
        if (h == null) {
            // Sem motor de pé (tela aberta com o serviço parado, por exemplo) a execução não pode
            // sumir em silêncio: roda numa thread solta, sem fila e sem espera encadeada.
            Thread({ rodarFila(macro, motivo) }, "MacroEngine-avulso").start()
            return
        }
        h.post { rodarFila(macro, motivo) }
    }

    private fun rodarFila(macro: Macro, motivo: String) {
        Log.w(TAG, "Executando '${macro.name}' ($motivo): ${macro.actions.size} ações")
        if (macro.showFeedback) MacroFeedback.mostrar(macro.name)
        macro.actions.forEach { acao ->
            if (acao.type == MacroActionCatalog.ACAO_ESPERAR) {
                val ms = acao.paramInt("ms", 1000).toLong().coerceIn(0L, ESPERA_MAX_MS)
                runCatching { Thread.sleep(ms) }
                return@forEach
            }
            val spec = MacroActionCatalog.porId(acao.type)
            if (spec == null) {
                Log.w(TAG, "Ação desconhecida '${acao.type}' em '${macro.name}'; pulando")
                return@forEach
            }
            runCatching { spec.run(acao) }
                .onFailure { Log.e(TAG, "Ação '${acao.type}' falhou em '${macro.name}'", it) }
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
