package br.com.opengw.telas

import br.com.opengw.nucleo.Preferencia
import br.com.opengw.nucleo.Preferencias
import java.util.concurrent.ConcurrentHashMap

/** Parte 2: configurações gravadas, abrir/mover/fechar, o que está nas telas, faixa da barra. */
internal class MotorDasTelas(private val n: NucleoDoLancador) {

    private val travaDasConfigs = Any()

    /** Memória de limites (2-C): pacote → display de origem → limites. Só em memória (D6). */
    private val limitesGuardados = ConcurrentHashMap<String, ConcurrentHashMap<Int, IntArray>>()

    /** Último conserto do modo dividido por pacote (2-G1.4). */
    private val consertos = ConcurrentHashMap<String, Long>()

    private val log get() = n.amb.registro
    private val prefs get() = n.amb.prefs

    // ── A. configurações gravadas ───────────────────────────────────────────────────────────────

    /** A5: sempre do disco, na ordem gravada. */
    fun configuracoes(): List<ConfigDoApp> =
        JsonDasConfigs.ler(prefs.texto(Preferencias.DISPLAY_APP_CONFIGS)) { log.aviso(it) }

    fun configuracao(pacote: String): ConfigDoApp? = configuracoes().firstOrNull { it.pacote == pacote }

    private fun gravar(lista: List<ConfigDoApp>, extras: Map<Preferencia, Any> = emptyMap()) {
        prefs.gravar(mapOf<Preferencia, Any>(Preferencias.DISPLAY_APP_CONFIGS to JsonDasConfigs.escrever(lista)) + extras)
    }

    fun salvar(config: ConfigDoApp) = synchronized(travaDasConfigs) {
        val lista = configuracoes().toMutableList()
        val i = lista.indexOfFirst { it.pacote == config.pacote }
        if (i >= 0) lista[i] = config else lista += config
        gravar(lista)
    }

    fun salvarTodas(configs: List<ConfigDoApp>) = synchronized(travaDasConfigs) { gravar(configs.toList()) }

    fun apagar(pacote: String) = synchronized(travaDasConfigs) {
        gravar(configuracoes().filterNot { it.pacote == pacote })
    }

    fun subir(pacote: String) = synchronized(travaDasConfigs) {
        val lista = configuracoes().toMutableList()
        val i = lista.indexOfFirst { it.pacote == pacote }
        if (i > 0) {
            val c = lista.removeAt(i)
            lista.add(i - 1, c)
            gravar(lista)
        }
    }

    fun descer(pacote: String) = synchronized(travaDasConfigs) {
        val lista = configuracoes().toMutableList()
        val i = lista.indexOfFirst { it.pacote == pacote }
        if (i >= 0 && i < lista.size - 1) {
            val c = lista.removeAt(i)
            lista.add(i + 1, c)
            gravar(lista)
        }
    }

    /** A6. [atividadeDeAbertura] é o `PackageManager` (a classe da intent de abertura). */
    fun configuracaoOuPadrao(
        pacote: String,
        gravarNova: Boolean,
        atividadeDeAbertura: (String) -> String? = n.amb.sistema::atividadeDeAbertura,
    ): ConfigDoApp? {
        configuracao(pacote)?.let { return it }
        PacotesDaCentral.predefinida(pacote)?.let {
            if (gravarNova) salvar(it)
            return it
        }
        val atividade = try {
            atividadeDeAbertura(pacote)
        } catch (e: Exception) {
            null
        } ?: return null
        val nova = ConfigDoApp(
            pacote, atividade, 3, 0, 0, 1920, 720,
            iconeSubstituto = if (pacote.startsWith("com.beantech")) "gwm" else null,
        )
        if (gravarNova) salvar(nova)
        return nova
    }

    /** A7: semeia o atalho das Configurações uma vez por instalação. */
    fun semearAtalhosPadrao() {
        if (prefs.booleano(Preferencias.ANDROID_SETTINGS_SHORTCUT_SEEDED, false)) return
        synchronized(travaDasConfigs) {
            val lista = configuracoes()
            val marca = mapOf<Preferencia, Any>(Preferencias.ANDROID_SETTINGS_SHORTCUT_SEEDED to true)
            if (lista.none { it.pacote == PacotesDaCentral.CONFIGURACOES }) {
                gravar(lista + PacotesDaCentral.predefinida(PacotesDaCentral.CONFIGURACOES)!!, marca)
            } else {
                prefs.gravar(marca)
            }
        }
    }

    // ── F. geometria ────────────────────────────────────────────────────────────────────────────

    fun limitesEfetivos(config: ConfigDoApp): IntArray = GeometriaDoLancador.limitesEfetivos(config, n.ajustes())

    fun limitesDeTelaCheiaNoDisplay0(pacote: String): IntArray = GeometriaDoLancador.telaCheiaNoDisplay0(pacote, n.ajustes())

    /** F5 sobre a altura do display 0. */
    private fun fundoDoDisplay0(pacote: String): Int {
        val a = n.ajustes()
        return GeometriaDoLancador.aplicarRecuo(a.resolucao(0).second, pacote, a)
    }

    // ── E. o que está nas telas ─────────────────────────────────────────────────────────────────

    /** E4: a lista; senão o `dumpsys` do display. */
    fun pacoteNoTopo(display: Int): String? = n.semFalhar("pacoteNoTopo($display)", null) {
        n.lista().pacoteNoTopo(display) ?: RegrasDoEspelhamento.pacoteDoDumpsys(
            n.rodar(
                "dumpsys activity activities | sed -n '/Display #$display/,/Display #/p' | " +
                    "grep -E 'mResumedActivity|mCurrentFocus|mFocusedActivity'",
            ),
        )
    }

    fun temAppNoDisplay(display: Int): Boolean = pacoteNoTopo(display) != null

    fun tarefaDoPacote(pacote: String): TarefaNaTela? =
        n.semFalhar("tarefaDoPacote($pacote)", null) { n.lista().tarefaDoPacote(pacote) }

    /** E10. */
    fun primeirasTarefas(pacotes: Collection<String>): Map<String, TarefaNaTela> =
        n.semFalhar("primeirasTarefas", emptyMap()) {
            val distintos = pacotes.filter { it.isNotEmpty() }.distinct()
            if (distintos.isEmpty()) return@semFalhar emptyMap()
            val lista = n.lista()
            val saida = LinkedHashMap<String, TarefaNaTela>()
            for (p in distintos) lista.tarefasDoPacote(p).firstOrNull()?.let { saida[p] = it }
            saida
        }

    // ── C. abrir, mover, fechar ─────────────────────────────────────────────────────────────────

    /** Guarda (em memória) os limites que a tarefa tinha antes de sair do display de origem. */
    fun guardarLimites(pacote: String, display: Int, limites: IntArray?) {
        if (limites == null || limites.size != 4) return
        limitesGuardados.getOrPut(pacote) { ConcurrentHashMap() }[display] = limites.copyOf()
    }

    private fun limitesDeVoltaAo0(pacote: String): IntArray =
        GeometriaDoLancador.limitesDeVoltaAo0(pacote, limitesGuardados[pacote]?.get(0), n.ajustes())

    fun redimensionarPilha(pilha: Int, l: IntArray): String =
        n.rodar("am stack resize $pilha ${l[0]} ${l[1]} ${l[2]} ${l[3]}")

    fun moverPilha(pilha: Int, display: Int): String = n.rodar("am display move-stack $pilha $display")

    fun comandoDeAbrir(pacote: String, atividade: String, display: Int, modo: Int, flags: String? = null): String =
        "am start -n $pacote/${n.escapar(atividade)} --display $display --windowingMode $modo" +
            (flags?.let { " -f $it" } ?: "")

    /** A atividade de uma config; vazia (D14) → a da intent de abertura. */
    private fun atividadeDe(c: ConfigDoApp): String? =
        c.atividade.ifEmpty {
            try {
                n.amb.sistema.atividadeDeAbertura(c.pacote)
            } catch (e: Exception) {
                null
            } ?: ""
        }.ifEmpty { null }

    /** C9: abre o vão no display 3 antes de mover/abrir. */
    suspend fun vaoNoPainel(display: Int, limites: IntArray) {
        if (display != 3 || limites.size != 4) return
        try {
            n.painel.prepararVaoNoDisplay3(limites.copyOf())
            n.esperar(48)
        } catch (e: Exception) {
            log.aviso("vão no display 3 falhou: ${e.message}")
        }
    }

    /** C8: em segundo plano, até três leituras (0; 0,5; 1,5 s); para na primeira "ativo". */
    fun avisarDisplay(display: Int) {
        n.emSegundoPlano("avisar display $display") {
            for (espera in longArrayOf(0L, 500L, 1000L)) {
                if (espera > 0) n.esperar(espera)
                val ativo = temAppNoDisplay(display)
                if (display == 1 || display == 3) n.painel.appNoDisplay(display, ativo)
                if (ativo) break
            }
        }
    }

    /** C1. */
    suspend fun abrir(config: ConfigDoApp) = n.semFalhar("abrir ${config.pacote}", Unit) {
        val p = config.pacote
        val d = config.display
        if (p == PacotesDaCentral.CARPLAY) {
            vaoNoPainel(d, limitesEfetivos(config))
            n.espelhamento.pedidoDeAbrirCarPlay(config, "abrir")
            return@semFalhar
        }
        if (p == PacotesDaCentral.ANDROID_AUTO) {
            vaoNoPainel(d, limitesEfetivos(config))
            n.espelhamento.abrirAndroidAuto(config, "abrir")
            return@semFalhar
        }
        n.barra.desmarcarRestaurado(p)
        val limites = limitesEfetivos(config)
        vaoNoPainel(d, limites)
        if (d != 0) despejar(d, p)

        n.lista().pilhaDoPacoteNoDisplay(p, d)?.let {
            redimensionarPilha(it.id, limites)
            avisarDisplay(d)
            return@semFalhar
        }

        if (p != n.pacoteDoApp) {
            val atividade = atividadeDe(config)
            if (atividade == null) {
                log.aviso("abrir $p: sem atividade")
            } else {
                n.rodar("am force-stop $p")
                n.esperar(200)
                n.rodar(comandoDeAbrir(p, atividade, d, 5))
            }
        } else {
            log.info("abrir: o próprio app não é parado nem iniciado")
        }

        n.esperar(300)
        val pilha = n.lista().pilhaDoPacoteNoDisplay(p, d)
        if (pilha != null) redimensionarPilha(pilha.id, limites) else log.aviso("abrir $p: pilha não apareceu no display $d")
        avisarDisplay(d)
    }

    /** C2: preserva o estado do app quando dá. */
    suspend fun enviarParaDisplay(config: ConfigDoApp) = n.semFalhar("enviar ${config.pacote}", Unit) {
        val p = config.pacote
        val d = config.display
        if (p == PacotesDaCentral.CARPLAY || p == PacotesDaCentral.ANDROID_AUTO) {
            abrir(config)
            return@semFalhar
        }
        val limites = limitesEfetivos(config)
        vaoNoPainel(d, limites)

        n.lista().pilhaDoPacoteNoDisplay(p, d)?.let {
            redimensionarPilha(it.id, limites)
            return@semFalhar
        }

        if (d != 0) despejar(d, p)

        val tarefa = n.lista().tarefaDoPacote(p)
        if (tarefa == null) {
            abrir(config)
            return@semFalhar
        }
        if (tarefa.display == d) {
            redimensionarPilha(tarefa.pilha, limites)
            return@semFalhar
        }
        guardarLimites(p, tarefa.display, tarefa.limites)
        if (n.lista().tarefasNaPilha(tarefa.pilha) > 1) {
            abrir(config)
            return@semFalhar
        }
        if (n.falhou(moverPilha(tarefa.pilha, d))) {
            abrir(config)
            return@semFalhar
        }
        n.barra.desmarcarRestaurado(p)
        n.esperar(200)
        n.lista().pilhaDoPacoteNoDisplay(p, d)?.let { redimensionarPilha(it.id, limites) }
        avisarDisplay(d)
    }

    /** C3. [atividade] = a pedida (ícones da barra e da tela Telas). */
    suspend fun abrirNaPrincipal(pacote: String, atividade: String?) = n.semFalhar("abrir na principal $pacote", Unit) {
        try {
            n.dependencias.launcher3d.aoAbrirNaPrincipal(pacote)
        } catch (e: Exception) {
            log.aviso("guarda do launcher 3D falhou: ${e.message}")
        }
        if (pacote == PacotesDaCentral.CARPLAY) {
            n.espelhamento.carPlayNaPrincipal(null, "abrir na principal", gravarDestino = true)
            return@semFalhar
        }
        if (pacote == PacotesDaCentral.ANDROID_AUTO) {
            n.espelhamento.abrirAndroidAuto(n.espelhamento.configDoAndroidAutoPara(0, null), "abrir na principal")
            return@semFalhar
        }

        val tarefa = n.lista().tarefaDoPacote(pacote)
        if (tarefa != null && tarefa.display != 0) {
            guardarLimites(pacote, tarefa.display, tarefa.limites)
            val saida = moverPilha(tarefa.pilha, 0)
            avisarDisplay(tarefa.display)
            n.rodar("am stack move-task-to-front ${tarefa.tarefa}")
            val agora = n.lista().tarefaDoPacote(pacote)
            if (!n.falhou(saida) && agora != null && agora.display == 0) {
                n.barra.marcarRestaurado(pacote)
                n.rodar("am stack set-windowing-mode ${agora.pilha} 1")
                redimensionarPilha(agora.pilha, limitesDeVoltaAo0(pacote))
                val escolhida = atividade?.ifEmpty { null } ?: PacotesDaCentral.predefinida(pacote)?.atividade
                if (escolhida != null) n.rodar(comandoDeAbrir(pacote, escolhida, 0, 1, "0x14000000"))
                n.reposicionarBarra()
                guardasDoEspelhamento("abriu na principal depois de mover $pacote")
                return@semFalhar
            }
        }

        if (!n.amb.sistema.abrirPelaIntent(pacote, FLAG_NEW_TASK or FLAG_CLEAR_TOP or FLAG_REORDER_TO_FRONT)) {
            val escolhida = atividade?.ifEmpty { null }
                ?: PacotesDaCentral.predefinida(pacote)?.atividade
                ?: configuracao(pacote)?.atividade?.ifEmpty { null }
            if (escolhida != null) {
                n.rodar(comandoDeAbrir(pacote, escolhida, 0, 1, "0x14000000"))
            } else {
                log.erro("abrir na principal $pacote: sem intent de abertura nem atividade")
            }
        }
        guardasDoEspelhamento("abriu na principal $pacote")
    }

    private fun guardasDoEspelhamento(motivo: String) {
        n.espelhamento.guardarCarPlayNoPainel(motivo)
        n.espelhamento.guardarAndroidAutoNoPainel(motivo)
    }

    /** C4: devolve os pacotes movidos (vazio = nada movido). */
    suspend fun trazerTodosParaPrincipal(): List<String> = n.semFalhar("trazer todos", emptyList()) {
        val lista = n.lista()
        val movidos = ArrayList<String>()
        val (l0, a0) = n.resolucao(0)
        for (pilha in lista.pilhas.filter { it.display == 1 || it.display == 3 }) {
            val p = lista.pacoteDaPilha(pilha.id)
            if (p == PacotesDaCentral.CARPLAY) {
                movidos += p
                n.semFalhar("CarPlay na principal", Unit) {
                    n.espelhamento.carPlayNaPrincipal(null, "trazer todos", gravarDestino = true)
                }
                continue
            }
            if (p == PacotesDaCentral.ANDROID_AUTO) {
                movidos += p
                n.espelhamento.abrirAndroidAuto(n.espelhamento.configDoAndroidAutoPara(0, null), "trazer todos")
                continue
            }
            if (n.falhou(moverPilha(pilha.id, 0))) continue
            if (p != null) {
                movidos += p
                val predefinida = PacotesDaCentral.predefinida(p)?.atividade
                if (predefinida != null) {
                    n.rodar(comandoDeAbrir(p, predefinida, 0, 1, "0x14000000"))
                } else {
                    n.amb.sistema.abrirPelaIntent(p, FLAG_NEW_TASK or FLAG_REORDER_TO_FRONT)
                }
                n.amb.sistema.naThreadPrincipal { n.barra.marcarRestaurado(p) }
            }
            redimensionarPilha(pilha.id, intArrayOf(0, 0, l0, if (p != null) fundoDoDisplay0(p) else a0))
        }
        avisarDisplay(1)
        avisarDisplay(3)
        n.reposicionarBarra()
        movidos
    }

    /** C5: despeja os outros apps do display [display] (≠ 0), exceto [exceto]. */
    suspend fun despejar(display: Int, exceto: String) {
        if (display == 0) return
        val lista = n.lista()
        val (l0, a0) = n.resolucao(0)
        for (pilha in lista.pilhasDoDisplay(display)) {
            val q = lista.pacoteDaPilha(pilha) ?: continue
            if (q == exceto || q == PacotesDaCentral.SYSTEMUI) continue
            when (q) {
                PacotesDaCentral.CARPLAY -> n.rodar("am stack remove $pilha")
                PacotesDaCentral.ANDROID_AUTO -> {
                    n.espelhamento.gravarDestinoDoAndroidAuto(0)
                    guardarLimites(q, display, tarefaNaPilha(lista, q, pilha)?.limites)
                    if (n.falhou(moverPilha(pilha, 0))) continue
                    val noZero = n.lista().tarefaDoPacoteNoDisplay(q, 0)
                    if (noZero != null) {
                        val a = n.ajustes()
                        val limites = intArrayOf(0, 0, l0, GeometriaDoLancador.aplicarRecuo(a0, q, a))
                        n.espelhamento.redimensionarEFocarAndroidAuto(noZero, 0, limites)
                    }
                    n.barra.marcarRestaurado(q)
                }
                else -> {
                    val tarefa = tarefaNaPilha(lista, q, pilha)
                    guardarLimites(q, display, tarefa?.limites)
                    if (n.falhou(moverPilha(pilha, 0))) continue
                    if (tarefa != null) {
                        val predefinida = PacotesDaCentral.predefinida(q)?.atividade
                        if (predefinida != null) {
                            n.rodar(comandoDeAbrir(q, predefinida, 0, 1))
                        } else {
                            n.amb.sistema.abrirPelaIntent(q, FLAG_NEW_TASK)
                        }
                        n.barra.marcarRestaurado(q)
                        redimensionarPilha(pilha, limitesDeVoltaAo0(q))
                    }
                }
            }
        }
        avisarDisplay(display)
        n.reposicionarBarra()
    }

    /** A tarefa de [pacote] naquela pilha, com os limites da linha (senão os da pilha). */
    private fun tarefaNaPilha(lista: ListaDePilhas, pacote: String, pilha: Int): TarefaNaTela? {
        val p = lista.pilhas.firstOrNull { it.id == pilha } ?: return null
        val t = p.tarefas.firstOrNull { it.pacote == pacote } ?: return null
        return TarefaNaTela(t.id, p.id, p.display, (t.limites ?: p.limites).copyOf())
    }

    /** C6: os números crus da config (sem tema, sem barra). */
    suspend fun redimensionar(config: ConfigDoApp) {
        try {
            val limites = when (config.pacote) {
                PacotesDaCentral.CARPLAY -> n.telaCheia(config.display)
                PacotesDaCentral.ANDROID_AUTO -> GeometriaDoLancador.limitesDoAndroidAuto(config.display, n.ajustes())
                else -> intArrayOf(config.x, config.y, config.x + config.largura, config.y + config.altura)
            }
            val pilha = n.lista().pilhaDoPacoteNoDisplay(config.pacote, config.display) ?: return
            redimensionarPilha(pilha.id, limites)
            n.painel.geometriaMudou()
        } catch (e: Exception) {
            log.depuracao("redimensionar ${config.pacote} falhou: ${e.message}")
        }
    }

    /** C7. */
    suspend fun fechar(pacote: String) = n.semFalhar("fechar $pacote", Unit) {
        when (pacote) {
            PacotesDaCentral.CARPLAY -> n.espelhamento.fecharCarPlay()
            PacotesDaCentral.ANDROID_AUTO -> n.espelhamento.fecharAndroidAuto()
            else -> {
                n.rodar("am force-stop $pacote")
                avisarDisplay(3)
            }
        }
    }

    /** F7: corrige a "tarja preta" do app do display 0. */
    suspend fun corrigirTarjaPreta(pacote: String) = n.semFalhar("tarja preta $pacote", Unit) {
        if (pacote == n.pacoteDoApp || RegrasDoEspelhamento.pacoteDeEspelhamento(pacote) != null) return@semFalhar
        if (!n.barra.visivel) return@semFalhar
        if (!prefs.booleano(Preferencias.ENABLE_PERSISTENT_BOTTOM_BAR, false)) return@semFalhar
        if (configuracao(pacote)?.ignorarAreaDoTema == true) return@semFalhar
        val tarefa = n.lista().tarefaDoPacoteNoDisplay(pacote, 0) ?: return@semFalhar
        val limites = tarefa.limites ?: return@semFalhar
        val (l0, a0) = n.resolucao(0)
        val esperado = fundoDoDisplay0(pacote)
        val novo = FaixaDaBarra.fundoCorrigidoContraTarjaPreta(limites, l0, a0, esperado)
        if (novo == null) {
            log.depuracao("tarja preta ok: $pacote ${limites.contentToString()}")
            return@semFalhar
        }
        redimensionarPilha(tarefa.pilha, intArrayOf(limites[0], limites[1], limites[2], novo))
        n.painel.geometriaMudou()
    }

    /** F8: volta na hora; o trabalho roda no escopo do módulo. */
    fun reaplicarLimitesDoDisplay0() {
        n.emSegundoPlano("reaplicar limites do display 0") { reaplicarAgora() }
    }

    internal fun reaplicarAgora() = n.semFalhar("reaplicar limites do display 0", Unit) {
        val lista = n.lista()
        for (pilha in lista.pilhasDoDisplay(0)) {
            val p = lista.pacoteDaPilha(pilha) ?: continue
            if (p == PacotesDaCentral.SYSTEMUI || p == n.pacoteDoApp) continue
            val c = configuracaoOuPadrao(p, gravarNova = false) ?: continue
            if (c.display != 0 || c.ignorarAreaDoTema) continue
            redimensionarPilha(pilha, limitesEfetivos(c))
        }
        n.painel.geometriaMudou()
    }

    // ── G. janela mudou ─────────────────────────────────────────────────────────────────────────

    /** G1: volta na hora. */
    fun aoMudarJanela(pacote: String) {
        try {
            n.barra.voltarAposFocoExterno(pacote, "janela mudou para $pacote")
        } catch (e: Exception) {
            log.aviso("barra falhou ao ver a janela $pacote: ${e.message}")
        }
        n.emSegundoPlano("guardas da janela $pacote") {
            n.espelhamento.guardaDoCarPlayPorJanela(pacote)
            n.espelhamento.guardaDoAndroidAutoPorJanela(pacote)
        }
        n.emSegundoPlano("modo dividido $pacote") { consertarModoDividido(pacote) }
    }

    /** G1.4: o app gravado num display secundário abriu em "split-screen-secondary". */
    internal suspend fun consertarModoDividido(pacote: String) {
        if (pacote == n.pacoteDoApp) return
        val c = configuracao(pacote) ?: return
        if (c.display == 0) return
        val ultimo = consertos[pacote]
        if (ultimo != null && n.agora() - ultimo in 0 until CONSERTO_MS) return
        n.esperar(500)
        val pilha = n.lista().pilhaDoPacoteNoDisplay(pacote, c.display) ?: return
        if (pilha.modo != "split-screen-secondary") return
        consertos[pacote] = n.agora()
        val atividade = atividadeDe(c) ?: return
        n.rodar("am force-stop $pacote")
        n.esperar(200)
        n.rodar(comandoDeAbrir(pacote, atividade, c.display, 5))
        n.esperar(300)
        n.lista().pilhaDoPacoteNoDisplay(pacote, c.display)?.let { redimensionarPilha(it.id, limitesEfetivos(c)) }
    }

    companion object {
        const val FLAG_NEW_TASK = 0x10000000
        const val FLAG_CLEAR_TOP = 0x04000000
        const val FLAG_REORDER_TO_FRONT = 0x00020000
        private const val CONSERTO_MS = 10_000L
    }
}
