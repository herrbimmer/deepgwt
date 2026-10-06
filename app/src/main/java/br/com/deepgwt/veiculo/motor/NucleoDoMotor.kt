// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.motor

import br.com.deepgwt.veiculo.Veiculo
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * O motor do veículo sem Android: ligação com a central (partida, vigia, recuperação), cache,
 * ouvintes, leitura, escrita, chaves acompanhadas, publicação e modo simulado.
 *
 * Tudo o que é de fora chega pelas costuras (`Costuras.kt`). Especificação:
 * `docs/independencia/specs/motor-nucleo.md` — as referências "A.3", "C.1" etc. são dela.
 */
internal class NucleoDoMotor(
    private val central: Central,
    private val agenda: Agenda,
    private val difusor: Difusor,
    registro: RegistroDoMotor,
    shell: (Array<String>) -> String,
    lerChavesDoUsuario: () -> Set<String>,
    private val catalogo: () -> List<String>,
    private val ligarFonteSimulada: LigarFonteSimulada,
) {
    private enum class Modo { NENHUM, REAL, SIMULADO }

    private val registro = RegistroLimitado(registro, agenda::agora)
    private val cache = CacheDeValores()
    private val ouvintes = ListaDeOuvintes(this.registro)
    private val chaves = ChavesAcompanhadas(ChavesDoMotor.ACOMPANHADAS_SEMPRE, lerChavesDoUsuario, this.registro)
    private val camadaDoAr = CamadaDoAr(agenda, shell, { appNaFrente?.invoke() }, ::anotar, this.registro)

    // ── Ganchos do código herdado ──

    /** Gancho único dos eventos crus do carro (F.1 viii). */
    @Volatile var ganchoDoCarro: Veiculo.Ouvinte? = null

    /** Pacote do app na frente do display 0 (camada do ar). */
    @Volatile var appNaFrente: (() -> String?)? = null

    /** Diário persistente de diagnóstico. */
    @Volatile var diario: ((evento: String, detalhes: Map<String, Any?>) -> Unit)? = null

    // ── Estado da ligação ──

    @Volatile private var modo = Modo.NENHUM
    @Volatile private var pacote: String? = null

    /** A alça atual do serviço de controle. Trocada só com [travaDoCanal] presa. */
    @Volatile private var canal: CanalDeControle? = null

    /** Junta "trocar a alça" e "ler o conjunto de chaves": nenhuma chave nova se perde (E.2/E.3). */
    private val travaDoCanal = Any()

    /** Uma partida ou recuperação por vez (A.2, I.4 i). */
    private val travaDaPartida = ReentrantLock(true)
    @Volatile private var partidaEmAndamento = false
    @Volatile private var recuperando = false
    private var recuperacoes = 0
    private var tentativasDeRecuperacao = 0

    private val travaDoVigia = Any()
    private var vigia: Cancelavel? = null

    private val travaDaVoz = Any()
    private var ultimaTentativaDeVoz = Long.MIN_VALUE / 2
    private val renovacaoDeVozAgendada = AtomicBoolean(false)

    private var fonteSimulada: Cancelavel? = null

    // ── Pronto ──

    private val travaDoPronto = Any()
    @Volatile private var prontoInterno = false
    private val tarefasGuardadas = ArrayList<Runnable>()

    // ── Instantes (uptime, ms; 0 = ainda não) ──

    private val boot = AtomicLong(0)
    @Volatile var inicioDaPartida = 0L
        private set
    @Volatile var fimDaPartida = 0L
        private set

    val instanteDoBoot: Long get() = boot.get()

    /** Só grava se ainda for 0. */
    fun marcarBoot(uptimeMs: Long) {
        boot.compareAndSet(0, uptimeMs)
    }

    // ═════════════════════════════ Partida ═════════════════════════════

    /** A. Bloqueante; uma por vez. [pacoteDoApp] é o nome de registro na central. */
    fun partir(pacoteDoApp: String): Boolean = travaDaPartida.withLock {
        val inicio = agenda.agora()
        boot.compareAndSet(0, inicio) // A.13
        modo = Modo.REAL
        pacote = pacoteDoApp
        partidaEmAndamento = true
        var novo: CanalDeControle? = null
        var etapa = "re-partida"
        var deuCerto = false
        try {
            // A.3
            pararVigia()
            val antigo = synchronized(travaDoCanal) { canal.also { canal = null } }
            if (antigo != null) soltar(antigo, pacoteDoApp)

            // A.4
            etapa = "Shizuku"
            if (!esperarShizuku()) return falhouAPartida(etapa, "o Shizuku não respondeu", null)
            camadaDoAr.religarSeSobrou()

            // A.5
            etapa = "serviço de controle"
            novo = central.abrirControle()
                ?: return falhouAPartida(etapa, "o serviço de controle não está registrado", null)
            if (!novo.responde()) return falhouAPartida(etapa, "o serviço de controle não responde", null)

            // A.6
            etapa = "aviso de morte"
            ligarAvisoDeMorte(novo)

            // A.7 (opcional)
            renovarVoz("partida")

            // A.8
            etapa = "registro"
            novo.registrarOuvinte(pacoteDoApp)
            chaves.recarregarDoUsuario()
            val uniao = synchronized(travaDoCanal) {
                canal = novo
                chaves.uniao()
            }
            novo.somarChaves(pacoteDoApp, uniao.toTypedArray())

            // A.9
            ligarVigia()

            // A.10
            etapa = "retrato"
            retrato(novo)

            // A.12
            inicioDaPartida = inicio
            fimDaPartida = agenda.agora()
            deuCerto = true
            anotar(
                "motor.partida",
                mapOf("resultado" to "ok", "chaves" to uniao.size, "ms" to fimDaPartida - inicio),
            )
            concluirPronto()
            true
        } catch (e: Exception) {
            falhouAPartida(etapa, e.message ?: e.javaClass.simpleName, e)
        } finally {
            if (!deuCerto) desfazer(novo, pararOVigia = true)
            partidaEmAndamento = false
        }
    }

    private fun falhouAPartida(etapa: String, motivo: String, causa: Throwable?): Boolean {
        registro.erroSempre("a partida falhou ($etapa): $motivo", causa)
        anotar("motor.partida", mapOf("resultado" to "falha", "etapa" to etapa, "motivo" to motivo))
        return false
    }

    /** A.4: confere e, faltando, até 3 novas tentativas com 500 ms entre elas. */
    private fun esperarShizuku(): Boolean {
        if (central.shizukuResponde()) return true
        repeat(NOVAS_TENTATIVAS_DO_SHIZUKU) {
            agenda.pausar(INTERVALO_DO_SHIZUKU_MS)
            if (central.shizukuResponde()) return true
        }
        return false
    }

    /** B. Sem Shizuku nem central. */
    fun partirSimulado() {
        travaDaPartida.withLock {
            val inicio = agenda.agora()
            boot.compareAndSet(0, inicio)
            modo = Modo.SIMULADO
            // Os valores iniciais entram antes das tarefas guardadas: há quem desenhe com eles.
            if (fonteSimulada == null) {
                fonteSimulada = try {
                    ligarFonteSimulada.ligar(::injetar)
                } catch (e: Exception) {
                    registro.erro("simulador", "a fonte simulada não ligou", e)
                    null
                }
            }
            inicioDaPartida = inicio
            fimDaPartida = agenda.agora()
            anotar("motor.partida", mapOf("resultado" to "ok", "modo" to "simulado"))
            concluirPronto()
        }
    }

    /** J. Para o vigia e desfaz o aviso de morte; não desregistra o ouvinte. */
    fun encerrar() {
        pararVigia()
        canal?.desligarAvisoDeMorte()
    }

    /** Desfaz o aviso de morte e, se a alça ainda responde, desregistra o ouvinte nela. */
    private fun soltar(antigo: CanalDeControle, pacoteDoApp: String) {
        antigo.desligarAvisoDeMorte()
        if (!antigo.responde()) return
        try {
            antigo.desregistrarOuvinte(pacoteDoApp)
        } catch (e: Exception) {
            registro.aviso("desregistrar", "não deu para desregistrar na alça antiga", e)
        }
    }

    /** Depois de uma falha: a alça nova não fica (o canal fica morto até a próxima tentativa). */
    private fun desfazer(novo: CanalDeControle?, pararOVigia: Boolean) {
        if (pararOVigia) pararVigia()
        if (novo == null) return
        synchronized(travaDoCanal) { if (canal === novo) canal = null }
        novo.desligarAvisoDeMorte()
    }

    private fun ligarAvisoDeMorte(alca: CanalDeControle) {
        alca.ligarAvisoDeMorte {
            // I.1: aviso de uma alça já trocada é ignorado.
            if (canal === alca) {
                anotar("motor.canal_morreu", emptyMap())
                agenda.executar { recuperar("morte") }
            }
        }
    }

    private fun concluirPronto() {
        val tarefas = synchronized(travaDoPronto) {
            prontoInterno = true
            tarefasGuardadas.toList().also { tarefasGuardadas.clear() }
        }
        if (tarefas.isNotEmpty()) agenda.executar { tarefas.forEach(::rodarTarefa) }
    }

    // ═════════════════════════ Vigia e recuperação ═════════════════════════

    private fun ligarVigia() {
        synchronized(travaDoVigia) {
            vigia?.cancelar()
            vigia = agenda.repetir(VIGIA_MS, VIGIA_MS, ::vigiar)
        }
    }

    private fun pararVigia() {
        synchronized(travaDoVigia) {
            vigia?.cancelar()
            vigia = null
        }
    }

    /** I.3, mais as alças da voz (Decisões da sala suja, 8). */
    private fun vigiar() {
        if (modo != Modo.REAL) return
        if (!recuperando && !controleVivo) recuperar("vigia")
        if (!central.vozCompleta() && central.shizukuResponde()) renovarVoz("vigia")
    }

    /** I.4. Uma por vez; se há outra (ou uma partida) em andamento, não faz nada. */
    fun recuperar(motivo: String) {
        if (modo != Modo.REAL) return
        if (!travaDaPartida.tryLock()) return
        try {
            if (recuperando) return
            recuperando = true
            val tentativa = ++tentativasDeRecuperacao
            val inicio = agenda.agora()
            anotar("motor.recuperacao_inicio", mapOf("motivo" to motivo, "tentativa" to tentativa))
            var novo: CanalDeControle? = null
            try {
                val pacoteDoApp = pacote ?: throw IllegalStateException("sem partida antes")
                if (!central.shizukuResponde()) throw IllegalStateException("o Shizuku não responde")
                val antigo = synchronized(travaDoCanal) { canal.also { canal = null } }
                if (antigo != null) soltar(antigo, pacoteDoApp)
                novo = central.abrirControle()
                    ?: throw IllegalStateException("o serviço de controle não está registrado")
                if (!novo.responde()) throw IllegalStateException("o serviço de controle não responde")
                ligarAvisoDeMorte(novo)
                novo.registrarOuvinte(pacoteDoApp)
                chaves.recarregarDoUsuario()
                val uniao = synchronized(travaDoCanal) {
                    canal = novo
                    chaves.uniao()
                }
                novo.somarChaves(pacoteDoApp, uniao.toTypedArray())
                retrato(novo)
                recuperacoes++
                anotar(
                    "motor.recuperacao_ok",
                    mapOf("motivo" to motivo, "recuperacoes" to recuperacoes, "ms" to agenda.agora() - inicio),
                )
            } catch (e: Exception) {
                desfazer(novo, pararOVigia = false)
                val mensagem = e.message ?: e.javaClass.simpleName
                registro.aviso("recuperacao", "a recuperação ($motivo) falhou: $mensagem")
                anotar("motor.recuperacao_falha", mapOf("motivo" to motivo, "mensagem" to mensagem))
            } finally {
                recuperando = false
            }
        } finally {
            travaDaPartida.unlock()
        }
    }

    // ═════════════════════════════ Voz ═════════════════════════════

    private fun renovarVoz(motivo: String) {
        synchronized(travaDaVoz) {
            ultimaTentativaDeVoz = agenda.agora()
            val completa = try {
                central.renovarVoz(::aoMorrerAVoz)
            } catch (e: Exception) {
                registro.erro("voz", "falhou pegando as alças da voz", e)
                false
            }
            if (!completa) registro.aviso("voz", "alças da voz incompletas ($motivo)")
            // O vigia tenta a cada 10 s enquanto faltar alça: no diário, só as mudanças.
            if (completa != vozCompletaNaUltima) {
                vozCompletaNaUltima = completa
                anotar("motor.voz", mapOf("motivo" to motivo, "completa" to completa))
            }
        }
    }

    private var vozCompletaNaUltima: Boolean? = null

    private fun aoMorrerAVoz() {
        if (!renovacaoDeVozAgendada.compareAndSet(false, true)) return
        // O adaptador de voz é persistente: volta sozinho, mas leva um tempo para se registrar.
        agenda.agendar(ESPERA_DA_VOZ_MS) {
            renovacaoDeVozAgendada.set(false)
            if (modo == Modo.REAL && central.shizukuResponde()) renovarVoz("morte")
        }
    }

    /** Antes de entregar uma alça da voz: se alguma morreu, tenta pegar de novo (no máximo a cada 2 s). */
    fun garantirVoz() {
        if (modo != Modo.REAL || central.vozCompleta()) return
        synchronized(travaDaVoz) {
            if (central.vozCompleta()) return
            if (agenda.agora() - ultimaTentativaDeVoz < INTERVALO_MINIMO_DA_VOZ_MS) return
            if (!central.shizukuResponde()) return
            renovarVoz("pedido")
        }
    }

    // ═════════════════════════════ Estado ═════════════════════════════

    /** H.1. Não volta a falso. */
    val pronto: Boolean get() = prontoInterno

    /** H.3. Barato; não lança; nunca verdadeiro no simulador. */
    val controleVivo: Boolean get() = modo != Modo.SIMULADO && canalVivo() != null

    /** Ligado à central de verdade (não simulado, não parado): o cache só vale com o canal vivo. */
    val modoReal: Boolean get() = modo == Modo.REAL

    private fun canalVivo(): CanalDeControle? {
        val atual = canal ?: return null
        return if (central.shizukuResponde() && atual.vivo()) atual else null
    }

    /** H.2. Sem perda na corrida entre "não está pronto" e "guardar". */
    fun quandoPronto(tarefa: Runnable) {
        val agora = synchronized(travaDoPronto) {
            if (prontoInterno) true else {
                tarefasGuardadas.add(tarefa)
                false
            }
        }
        if (agora) rodarTarefa(tarefa)
    }

    private fun rodarTarefa(tarefa: Runnable) {
        try {
            tarefa.run()
        } catch (e: Exception) {
            registro.erroSempre("uma tarefa de quandoPronto lançou exceção", e)
        }
    }

    // ═════════════════════════════ Leitura ═════════════════════════════

    /** C.1. Cache primeiro; na falta, vai ao carro uma vez. */
    fun ler(chave: String): String? {
        val lembranca = cache.lembrar(chave)
        if (lembranca.existe) return lembranca.valor
        if (modo == Modo.SIMULADO) return null
        val alca = canalVivo() ?: return semCanal("ler $chave", null)
        return try {
            // Se um evento gravou a chave enquanto a pergunta ia e voltava, ele é mais novo.
            cache.guardarSeAusente(chave, alca.buscar(chave)).valor
        } catch (e: Exception) {
            registro.erro("ler", "falhou lendo $chave", e)
            null
        }
    }

    /** C.6. Só cache. */
    fun espiar(chave: String): String? = cache.lembrar(chave).valor

    /** C.2. */
    fun lerDoCarro(chave: String): String? {
        if (modo == Modo.SIMULADO) return cache.lembrar(chave).valor
        val alca = canalVivo() ?: return semCanal("lerDoCarro $chave", null)
        return try {
            alca.buscar(chave).also { cache.guardar(chave, it) }
        } catch (e: Exception) {
            registro.erro("lerDoCarro", "falhou lendo $chave do carro", e)
            null
        }
    }

    /** C.3. Uma chamada; não mexe no cache; `""` para "sem valor". */
    fun lerDoCarro(chavesPedidas: Array<String>): Map<String, String> {
        val unicas = LinkedHashSet<String>(chavesPedidas.asList()).toList()
        if (unicas.isEmpty()) return emptyMap()
        if (modo == Modo.SIMULADO) return doCache(unicas)
        val alca = canalVivo() ?: return semCanal("lerDoCarro (lote)", emptyMap())
        val valores = try {
            alca.buscarVarias(unicas.toTypedArray())
        } catch (e: Exception) {
            registro.erro("lote", "falhou o lote de ${unicas.size} chaves", e)
            return emptyMap()
        }
        if (valores == null || valores.size != unicas.size) {
            registro.erro("lote", "o lote de ${unicas.size} chaves voltou com ${valores?.size} posições")
            return emptyMap()
        }
        val resultado = LinkedHashMap<String, String>(unicas.size * 2)
        unicas.forEachIndexed { i, chave -> resultado[chave] = valores[i] ?: "" }
        return resultado
    }

    /** C.4. O catálogo inteiro; escreve no cache sem avisar ninguém. */
    fun lerTudoDoCarro(): Map<String, String> {
        val todas = LinkedHashSet(catalogo()).toList()
        if (modo == Modo.SIMULADO) return doCache(todas)
        val alca = canalVivo() ?: return semCanal("lerTudoDoCarro", emptyMap())
        val lidos = lerEmLote(alca, todas)
        val resultado = LinkedHashMap<String, String>(lidos.size * 2)
        for ((chave, valor) in lidos) {
            cache.guardar(chave, valor)
            resultado[chave] = valor ?: ""
        }
        return resultado
    }

    /**
     * Um lote só; se ele falhar inteiro, pedaços de [PEDACO_DO_LOTE] e junta o que vier, na ordem
     * pedida (Decisões da sala suja, 6). Nunca lança.
     */
    private fun lerEmLote(alca: CanalDeControle, chavesPedidas: List<String>): Map<String, String?> {
        val resultado = LinkedHashMap<String, String?>(chavesPedidas.size * 2)
        if (pedirLote(alca, chavesPedidas, resultado)) return resultado
        var falhas = 0
        for (pedaco in chavesPedidas.chunked(PEDACO_DO_LOTE)) {
            if (!pedirLote(alca, pedaco, resultado)) falhas++
        }
        registro.aviso(
            "lote",
            "o lote de ${chavesPedidas.size} chaves falhou inteiro; em pedaços: $falhas pedaço(s) sem resposta",
        )
        return resultado
    }

    private fun pedirLote(alca: CanalDeControle, chavesPedidas: List<String>, destino: MutableMap<String, String?>): Boolean {
        val valores = try {
            alca.buscarVarias(chavesPedidas.toTypedArray())
        } catch (e: Exception) {
            return false
        }
        if (valores == null || valores.size != chavesPedidas.size) return false
        chavesPedidas.forEachIndexed { i, chave -> destino[chave] = valores[i] }
        return true
    }

    private fun doCache(chavesPedidas: List<String>): Map<String, String> {
        val resultado = LinkedHashMap<String, String>(chavesPedidas.size * 2)
        for (chave in chavesPedidas) resultado[chave] = cache.lembrar(chave).valor ?: ""
        return resultado
    }

    /** C.5. Inclui as lembradas "sem valor", com null. */
    fun valoresConhecidos(): Map<String, String?> = cache.copia()

    /** C.8. Relê do carro o conjunto acompanhado e publica os não vazios, sem o gancho. */
    fun reenviarTudo() {
        if (modo == Modo.SIMULADO) {
            for (chave in chaves.uniao()) {
                val valor = cache.lembrar(chave).valor
                if (!valor.isNullOrEmpty()) publicarValor(chave, valor, doCarro = false)
            }
            return
        }
        val alca = canalVivo() ?: return
        try {
            retrato(alca)
        } catch (e: Exception) {
            registro.erro("retrato", "falhou o retrato", e)
        }
    }

    /** O retrato: um lote com a união e publicação de cada valor não vazio. Lança se o lote falhar. */
    private fun retrato(alca: CanalDeControle) {
        val uniao = chaves.uniao()
        val valores = alca.buscarVarias(uniao.toTypedArray())
            ?: throw IllegalStateException("o lote do retrato voltou nulo")
        val ate = minOf(uniao.size, valores.size)
        for (i in 0 until ate) {
            val valor = valores[i]
            if (!valor.isNullOrEmpty()) publicarValor(uniao[i], valor, doCarro = false)
        }
    }

    // ═════════════════════════════ Escrita ═════════════════════════════

    /** D.2. True = a central aceitou o pedido (não quer dizer que o carro mudou). */
    fun escreverConferindo(chave: String, valor: String): Boolean {
        if (modo == Modo.SIMULADO) {
            injetar(chave, valor) // D.7
            return true
        }
        val doAr = camadaDoAr.ehDoAr(chave)
        val alca = canalVivo()
        if (alca == null) {
            if (doAr) anotar("ar.pulada", mapOf("chave" to chave, "valor" to valor, "motivo" to "canal morto"))
            return semCanal("escrever $chave", false)
        }
        if (!doAr) return pedirEscrita(alca, chave, valor)

        // D.6
        camadaDoAr.antesDeEscrever()
        anotar("ar.pedido", mapOf("chave" to chave, "valor" to valor))
        val aceito = pedirEscrita(alca, chave, valor)
        if (aceito) {
            val anterior = cache.trocar(chave, valor)
            if (!anterior.existe || anterior.valor != valor) {
                anotar("ar.otimista", mapOf("chave" to chave, "anterior" to anterior.valor, "novo" to valor))
                ouvintes.avisar(chave, valor)
            }
        }
        camadaDoAr.depoisDeEscrever()
        return aceito
    }

    /** D.3. */
    fun escrever(chave: String, valor: String) {
        try {
            escreverConferindo(chave, valor)
        } catch (e: Exception) {
            registro.erro("escrever", "falhou escrevendo $chave", e)
        }
    }

    private fun pedirEscrita(alca: CanalDeControle, chave: String, valor: String): Boolean =
        try {
            alca.pedir(ACAO_DE_ESCRITA, chave, valor)
            true
        } catch (e: Exception) {
            registro.erro("escrever", "a central recusou $chave=$valor", e)
            if (camadaDoAr.ehDoAr(chave)) {
                anotar(
                    "ar.falha",
                    mapOf("chave" to chave, "valor" to valor, "erro" to e.javaClass.simpleName, "mensagem" to e.message),
                )
            }
            false
        }

    // ═════════════════════════ Chaves acompanhadas ═════════════════════════

    /** E.2/E.3. */
    fun acompanhar(chavesPedidas: Collection<String>) {
        val (novas, alca) = synchronized(travaDoCanal) { chaves.somarNovas(chavesPedidas) to canal }
        if (novas.isEmpty() || modo == Modo.SIMULADO) return
        val pacoteDoApp = pacote
        if (alca == null || pacoteDoApp == null || !central.shizukuResponde() || !alca.vivo()) {
            registro.info("${novas.size} chave(s) guardada(s) para a próxima ligação: $novas")
            return
        }
        val arranjo = novas.toTypedArray()
        try {
            alca.somarChaves(pacoteDoApp, arranjo)
        } catch (e: Exception) {
            // Ficam no conjunto: a próxima recuperação pede de novo.
            registro.erro("acompanhar", "falhou pedindo $novas", e)
            return
        }
        try {
            val valores = alca.buscarVarias(arranjo) ?: return
            if (valores.size != arranjo.size) return
            arranjo.forEachIndexed { i, chave -> cache.guardar(chave, valores[i]) }
        } catch (e: Exception) {
            registro.erro("acompanhar", "falhou lendo o valor inicial de $novas", e)
        }
    }

    /** E.5. Via [quandoPronto]. */
    fun recarregarChavesDoUsuario() {
        quandoPronto {
            chaves.recarregarDoUsuario()
            if (modo == Modo.SIMULADO) {
                reenviarTudo()
                return@quandoPronto
            }
            val alca = canalVivo()
            val pacoteDoApp = pacote
            if (alca == null || pacoteDoApp == null) {
                registro.aviso("recarregar", "canal morto: as chaves da pessoa vão na próxima ligação")
                return@quandoPronto
            }
            try {
                alca.somarChaves(pacoteDoApp, chaves.uniao().toTypedArray())
            } catch (e: Exception) {
                registro.erro("recarregar", "falhou pedindo as chaves de novo", e)
                return@quandoPronto
            }
            cache.limpar()
            reenviarTudo()
        }
    }

    val conjuntoAcompanhado: List<String> get() = chaves.uniao()

    // ═════════════════════════ Ouvintes e publicação ═════════════════════════

    fun observar(ouvinte: Veiculo.Ouvinte) = ouvintes.acrescentar(ouvinte)

    fun pararDeObservar(ouvinte: Veiculo.Ouvinte) = ouvintes.tirar(ouvinte)

    /** Evento do carro (thread de binder). */
    fun chegouDoCarro(chave: String?, valor: String?) = publicarValor(chave, valor, doCarro = true)

    /** B.6: entra como evento do carro. */
    fun injetar(chave: String, valor: String) = publicarValor(chave, valor, doCarro = true)

    /** F.4: chave que só o app conhece; não vai ao carro. */
    fun publicar(chave: String, valor: String) = publicarValor(chave, valor, doCarro = false)

    /** F.1, na ordem exata. Ouvinte ANTES do cache (G.4). */
    private fun publicarValor(chave: String?, valor: String?, doCarro: Boolean) {
        if (chave == null || valor == null) return
        difundir("$PREFIXO_DO_BROADCAST$chave", chave, valor, soDoApp = true)
        difundir("$PREFIXO_DO_BROADCAST${chave}_$valor", null, null, soDoApp = true)
        difundir(BROADCAST_GERAL, chave, valor, soDoApp = false)
        ouvintes.avisar(chave, valor)
        cache.guardar(chave, valor)
        if (doCarro && prontoInterno && !partidaEmAndamento) {
            val gancho = ganchoDoCarro ?: return
            try {
                gancho.mudou(chave, valor)
            } catch (e: Exception) {
                registro.erro("gancho", "o gancho do carro lançou exceção com $chave", e)
            }
        }
    }

    private fun difundir(acao: String, chave: String?, valor: String?, soDoApp: Boolean) {
        try {
            difusor.enviar(acao, chave, valor, soDoApp)
        } catch (e: Exception) {
            registro.erro("broadcast", "falhou o broadcast $acao", e)
        }
    }

    // ═════════════════════════════ Auxiliares ═════════════════════════════

    private fun <T> semCanal(oQue: String, resposta: T): T {
        if (modo == Modo.REAL || modo == Modo.NENHUM) registro.aviso("canal-morto", "canal morto: $oQue")
        return resposta
    }

    private fun anotar(evento: String, detalhes: Map<String, Any?>) {
        registro.info("$evento $detalhes")
        val destino = diario ?: return
        try {
            destino(evento, detalhes)
        } catch (e: Exception) {
            registro.erro("diario", "o diário falhou", e)
        }
    }

    companion object {
        const val ACAO_DE_ESCRITA = "cmd.common.request.set"
        const val PREFIXO_DO_BROADCAST = "android.intent.haval."
        const val BROADCAST_GERAL = "com.haval.vehicle.EVENT_CHANGED"
        const val NOVAS_TENTATIVAS_DO_SHIZUKU = 3
        const val INTERVALO_DO_SHIZUKU_MS = 500L
        const val VIGIA_MS = 10_000L
        const val PEDACO_DO_LOTE = 100
        const val ESPERA_DA_VOZ_MS = 3_000L
        const val INTERVALO_MINIMO_DA_VOZ_MS = 2_000L
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
