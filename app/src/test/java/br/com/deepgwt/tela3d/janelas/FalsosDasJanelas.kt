package br.com.deepgwt.tela3d.janelas

import java.util.PriorityQueue

/** Relógio e agendador falsos: o tempo só anda quando o teste manda. Ações no mesmo instante saem na ordem de agendamento. */
internal class TempoFalso : Agendador, Relogio {
    var agora = 0L
        private set
    private var sequencia = 0L

    private class Tarefa(val quando: Long, val ordem: Long, val acao: Runnable) : Comparable<Tarefa> {
        var cancelada = false
        override fun compareTo(other: Tarefa): Int =
            if (quando != other.quando) quando.compareTo(other.quando) else ordem.compareTo(other.ordem)
    }

    private val fila = PriorityQueue<Tarefa>()

    override fun agoraMs(): Long = agora

    override fun em(atrasoMs: Long, acao: Runnable): Cancelavel {
        val t = Tarefa(agora + atrasoMs, sequencia++, acao)
        fila.add(t)
        return Cancelavel { t.cancelada = true }
    }

    /** Anda [ms] milissegundos, executando o que vence no caminho (inclusive o que as ações agendarem). */
    fun avancar(ms: Long) {
        val fim = agora + ms
        while (true) {
            val proxima = fila.peek() ?: break
            if (proxima.quando > fim) break
            fila.poll()
            if (proxima.cancelada) continue
            agora = maxOf(agora, proxima.quando)
            proxima.acao.run()
        }
        agora = fim
    }

    fun pendentes(): Int = fila.count { !it.cancelada }
}

internal class ArmazemEmMemoria : ArmazemDeChaves {
    val textos = LinkedHashMap<String, String>()
    val booleanos = LinkedHashMap<String, Boolean>()
    val gravacoes = ArrayList<Set<String>>()

    override fun texto(chave: String): String? = textos[chave]

    override fun booleano(chave: String): Boolean? = booleanos[chave]

    override fun gravar(textos: Map<String, String>, booleanos: Map<String, Boolean>) {
        this.textos.putAll(textos)
        this.booleanos.putAll(booleanos)
        gravacoes += textos.keys + booleanos.keys
    }
}

/** Registro das chamadas ao sistema; as respostas assíncronas voltam na hora. */
internal class SistemaFalso : PortaDoSistema {
    val chamadas = ArrayList<String>()
    val componentes = HashMap<String, String>()
    var visibilidade = HashMap<String, Int>()
    var visibilidadePadrao = Visibilidade.VISIVEL
    var tarefaAchada = HashMap<String, Int>()
    var levantarFunciona = true
    var abrirLanca = false
    var ouvinte: Runnable? = null
    var ouvinteFunciona = true
    val aberturas = ArrayList<Abertura>()

    class Abertura(val pacote: String, val limites: Retangulo, val reabertura: Boolean, val tipo: String)

    init {
        for (p in listOf("com.waze", "com.spotify", "com.maps", "com.chat")) componentes[p] = "$p/$p.Main"
        componentes["com.android.settings"] = "com.android.settings/com.android.settings.Settings"
    }

    fun limparChamadas() = chamadas.clear()

    override fun componenteDeEntrada(pacote: String): String? = componentes[pacote]

    override fun abrirTelaCheia(pacote: String, componente: String, limites: Retangulo, raizDasConfiguracoes: Boolean) {
        if (abrirLanca) throw IllegalStateException("falha")
        chamadas += "telaCheia:$pacote:$limites:${if (raizDasConfiguracoes) "raiz" else "normal"}"
        aberturas += Abertura(pacote, limites, false, if (raizDasConfiguracoes) "configuracoes" else "telaCheia")
    }

    override fun abrirJanelaDeLado(pacote: String, componente: String, limites: Retangulo, reabertura: Boolean) {
        if (abrirLanca) throw IllegalStateException("falha")
        chamadas += "janela:$pacote:$limites:${if (reabertura) "reabertura" else "primeira"}"
        aberturas += Abertura(pacote, limites, reabertura, "janela")
    }

    override fun abrirProjecao(componente: String, limites: Retangulo) {
        if (abrirLanca) throw IllegalStateException("falha")
        chamadas += "projecao:$componente:$limites"
    }

    var reservaDeProjecao: List<String> = listOf("com.ts.androidauto.app/Aap")

    override fun componentesDeReserva(tipo: TipoDeProjecao): List<String> = reservaDeProjecao

    override fun ligarJanelaLivre() {
        chamadas += "janelaLivre"
    }

    override fun registrarOuvinteDePilha(aoMudar: Runnable): Boolean {
        ouvinte = aoMudar
        return ouvinteFunciona
    }

    override fun removerOuvinteDePilha() {
        chamadas += "removerOuvinte"
        ouvinte = null
    }

    override fun encerrarApp(pacote: String) {
        chamadas += "encerrar:$pacote"
    }

    override fun removerTarefasDoPacote(pacote: String, aoTerminar: () -> Unit) {
        chamadas += "removerTodas:$pacote"
        aoTerminar()
    }

    override fun fecharTarefa(pacote: String, guardado: Int) {
        chamadas += "fechar:$pacote:$guardado"
    }

    override fun fixarTarefa(pacote: String, guardado: Int, limites: Retangulo, aoTerminar: (Int) -> Unit) {
        chamadas += "fixar:$pacote:$limites"
        aoTerminar(tarefaAchada[pacote] ?: -1)
    }

    override fun levantarTarefa(pacote: String, guardado: Int, aoTerminar: (ResultadoDoLevantar) -> Unit) {
        chamadas += "levantar:$pacote:$guardado"
        val id = tarefaAchada[pacote] ?: -1
        aoTerminar(ResultadoDoLevantar(id, id >= 0 && levantarFunciona))
    }

    override fun redimensionarDoPacote(pacote: String, guardado: Int, limites: Retangulo, aoTerminar: (Int) -> Unit) {
        chamadas += "redimensionar:$pacote:$guardado:$limites"
        aoTerminar(tarefaAchada[pacote] ?: -1)
    }

    override fun fixarNoTopo(pacote: String, guardado: Int, aoTerminar: (Int) -> Unit) {
        chamadas += "noTopo:$pacote"
        aoTerminar(tarefaAchada[pacote] ?: -1)
    }

    override fun levantarPorPacote(pacote: String, aoTerminar: (ResultadoDoLevantar) -> Unit) {
        chamadas += "levantarPorPacote:$pacote"
        val id = tarefaAchada[pacote] ?: -1
        aoTerminar(ResultadoDoLevantar(id, id >= 0 && levantarFunciona))
    }

    override fun levantarPorNumero(tarefa: Int, aoTerminar: (Boolean) -> Unit) {
        chamadas += "levantarNumero:$tarefa"
        aoTerminar(levantarFunciona)
    }

    override fun lerVisibilidade(pacote: String, guardado: Int, aoTerminar: (Int) -> Unit) {
        chamadas += "visibilidade:$pacote"
        aoTerminar(visibilidade[pacote] ?: visibilidadePadrao)
    }

    override fun esquecerUltimoLevantar() {
        chamadas += "esquecerUltimo"
    }

    fun contar(prefixo: String): Int = chamadas.count { it.startsWith(prefixo) }
}

/** A tela: devolve a geometria de V1 (densidade 1,0; 1920×720) e guarda o que recebeu. */
internal class TelaFalsa : PortaDaTela {
    var medida = true
    val layouts = ArrayList<EstadoDasJanelas>()
    val avisos = ArrayList<String>()
    val imersivos = ArrayList<ModoDaInterface>()
    var naoFocavel = false
    var noLimite = false
    var recalculos = 0
    var limiteTelaCheia = Retangulo(0, 0, 1920, 660)

    private val entradas = EntradasDeGeometria(
        larguraReal = 1920, alturaReal = 720, larguraDasMetricas = 1920, alturaDasMetricas = 720,
        densidade = 1f, alturaDaBarraDeStatus = 24, larguraDoTrilhoDoSistema = 0,
        maiorInsetEsquerdo = 0, maiorInsetSuperior = 0, insetDireito = 0, insetInferior = 0,
        temInsets = true, modoDaInterface = ModoDaInterface.FLUTUANTE,
        raizNaTela = Retangulo(0, 0, 1920, 720), topoDaFaixaNaTela = null,
    )

    override fun raizMedida(): Boolean = medida

    override fun enviarLayout(estado: EstadoDasJanelas) {
        layouts += estado
    }

    override fun limitesDoLado(lado: Lado, modo: ModoDeTela, proporcao: ProporcaoDaDivisao): Retangulo =
        CalculoDeGeometria.limitesDoLado(entradas, lado, modo, proporcao)

    override fun recalcularAncoras(modo: ModoDeTela, proporcao: ProporcaoDaDivisao) {
        recalculos++
    }

    override fun limitesDeTelaCheia(pacote: String): Retangulo = limiteTelaCheia

    override fun aplicarPoliticaDeFoco(naoFocavel: Boolean, noLimite: Boolean): Boolean {
        val mudou = naoFocavel != this.naoFocavel || noLimite != this.noLimite
        this.naoFocavel = naoFocavel
        this.noLimite = noLimite
        return mudou
    }

    override fun aplicarImersivo(modo: ModoDaInterface) {
        imersivos += modo
    }

    override fun mostrarAviso(texto: String) {
        avisos += texto
    }
}

internal class ChromeFalso : ChromeParaJanelas {
    val modos = ArrayList<ModoDeTela>()
    val proporcoes = ArrayList<ProporcaoDaDivisao>()
    var mudancasDeSlot = 0
    val projecoes = ArrayList<TipoDeProjecao>()

    override fun aoMudarOModoDeTela(modo: ModoDeTela) {
        modos += modo
    }

    override fun aoMudarAProporcao(proporcao: ProporcaoDaDivisao) {
        proporcoes += proporcao
    }

    override fun aoMudarSlots() {
        mudancasDeSlot++
    }

    override fun aoMudarAProjecao(tipo: TipoDeProjecao) {
        projecoes += tipo
    }

    override fun topoDaFaixaNaTela(): Int? = null

    override fun alturaDaFaixaEmDp(): Int = 148
}

internal class CatalogoFalso : CatalogoParaJanelas {
    val recentes = ArrayList<String>()
    var mentira = emptySet<String>()
    var saiuDaFrente = 0

    override fun ehAppDeMentiraDoEmulador(pacote: String): Boolean = pacote in mentira

    override fun registrarAppRecente(pacote: String) {
        recentes += pacote
    }

    override fun ehAppDaGwm(pacote: String): Boolean = RegrasDeOrigemDoApp.ehAppDaGwm(pacote, "br.com.deepgwt")

    override fun ehAppFixoDoDock(pacote: String): Boolean = RegrasDeOrigemDoApp.ehAppFixoDoDock(pacote)

    override fun aoLauncherSairDaFrente() {
        saiuDaFrente++
    }
}

internal class ProjecaoFalsa : ProjecaoParaJanelas {
    override var atual: TipoDeProjecao = TipoDeProjecao.NENHUM
    val pedidos = ArrayList<TipoDeProjecao>()
    var ouvinte: ((TipoDeProjecao) -> Unit)? = null

    override fun aoMudar(ouvinte: (TipoDeProjecao) -> Unit) {
        this.ouvinte = ouvinte
    }

    override fun pedirExibicao(tipo: TipoDeProjecao) {
        pedidos += tipo
    }
}

/** Monta o núcleo com todos os falsos para os testes de comportamento. */
internal class CenarioDasJanelas(
    prefs: Map<String, String> = emptyMap(),
    booleanos: Map<String, Boolean> = emptyMap(),
) {
    val tempo = TempoFalso()
    val sistema = SistemaFalso()
    val tela = TelaFalsa()
    val chrome = ChromeFalso()
    val catalogo = CatalogoFalso()
    val projecao = ProjecaoFalsa()
    val armazem = ArmazemEmMemoria().also {
        it.textos.putAll(prefs)
        it.booleanos.putAll(booleanos)
    }
    val diario = DiarioDeTeste()
    val nucleo = NucleoDasJanelas(
        pacoteProprio = "br.com.deepgwt",
        sistema = sistema,
        tela = tela,
        agendador = tempo,
        relogio = tempo,
        armazem = PreferenciasDasJanelas(armazem),
        chrome = chrome,
        catalogo = catalogo,
        projecao = projecao,
        diario = diario,
    )

    init {
        nucleo.aoCriar(ExtrasDeEntrada.NENHUM)
        sistema.limparChamadas()
    }

    val estado: EstadoDasJanelas get() = nucleo.estado.value

    /** Deixa passar o tempo (a abertura em curso dura 2 s) e a fila "adiada" rodar. */
    fun assentar(ms: Long = 5_000L) = tempo.avancar(ms)
}

internal class DiarioDeTeste : Diario {
    val linhas = ArrayList<String>()

    override fun info(mensagem: String) {
        linhas += mensagem
    }

    override fun aviso(mensagem: String) {
        linhas += "aviso: $mensagem"
    }

    override fun erro(mensagem: String, causa: Throwable?) {
        linhas += "erro: $mensagem"
    }

    fun contem(trecho: String): Boolean = linhas.any { trecho in it }
}
