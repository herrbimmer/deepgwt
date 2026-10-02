package br.com.opengw.ponte

import android.content.SharedPreferences
import br.com.opengw.telas.ProjectionGeometry

/** Painel em memória: roda tudo "na principal" na hora e guarda cada script mandado à página. */
class PainelFalso : Painel {
    val scripts = mutableListOf<String>()

    /** O que `executar` devolve a quem pediu resposta (o JSON que o WebView daria). */
    var responder: (String) -> String? = { js ->
        if (js.contains("onCardChanged")) "\"contrato\"" else null
    }

    override var avisoAtivo = false
    override var avisoDispensado = false
    var avisosDispensados = 0
    var batidas = 0
    val areas = mutableListOf<List<Int>>()
    val mascaras = mutableListOf<Pair<String, Boolean>>()
    val configuracoes = mutableListOf<String>()
    val modos = mutableListOf<String>()

    override fun naPrincipal(bloco: () -> Unit) = bloco()

    override fun executar(js: String, resposta: ((String?) -> Unit)?) {
        scripts.add(js)
        resposta?.invoke(responder(js))
    }

    override fun dispensarAvisos() {
        avisosDispensados++
    }

    override fun batimento() {
        batidas++
    }

    override fun areaDosApps(x: Int, y: Int, largura: Int, altura: Int) {
        areas.add(listOf(x, y, largura, altura))
    }

    override fun mascaraNativa(nome: String, visivel: Boolean) {
        mascaras.add(nome to visivel)
    }

    override fun configuracaoDasMascaras(json: String) {
        configuracoes.add(json)
    }

    override fun salvarModoDeExibicao(valor: String) {
        modos.add(valor)
    }

    /** Os `onDataChanged(chave, valor)` mandados, na ordem. */
    fun entregas(): List<Pair<String, String>> = scripts.mapNotNull { LeitorDeScript.onDataChanged(it) }

    fun limpar() = scripts.clear()
}

class ProjecaoFalsa : Projecao {
    val declaracoes = mutableListOf<ProjectionGeometry.Caixa?>()
    override var caixaDeclaradaPeloTema: ProjectionGeometry.Caixa? = null
        set(valor) {
            declaracoes.add(valor)
            field = valor
        }
    val paradas = mutableListOf<String>()
    var temUltimaArea = true
    var reprojecoes = 0
    var selecoes = 0

    override fun parar(motivo: String) {
        paradas.add(motivo)
    }

    override fun reprojetarUltimaArea(): Boolean {
        reprojecoes++
        return temUltimaArea
    }

    override fun abrirSelecao() {
        selecoes++
    }
}

class FontesFalsas : Fontes {
    var tema = ""
    var midia = MidiaDoApp.NENHUMA
    val topo = mutableMapOf<Int, String?>()
    var consultasDoTopo = 0
    var launcher: List<AppDoLauncher> = emptyList()
    var fundoDoTema: (String?) -> String? = { null }
    var modos: List<ModoDeExibicao> = emptyList()
    var leiturasDosModos = 0

    override fun temaAtivo() = tema
    override fun midiaDoApp() = midia
    override fun appNoTopo(display: Int): String? {
        consultasDoTopo++
        return topo[display]
    }

    override fun rotuloDoApp(pacote: String) = "Rótulo de $pacote"
    override fun iconeDoApp(pacote: String) = "data:image/png;base64,$pacote"
    override fun appsDoLauncher() = launcher
    override fun arquivoDeFundoDoTema(dica: String?) = fundoDoTema(dica)
    override fun modosDeExibicaoDoTema(): List<ModoDeExibicao> {
        leiturasDosModos++
        return modos
    }
}

class CarroFalso : Carro {
    val valores = mutableMapOf<String, String>()
    val diretos = mutableMapOf<String, String>()
    val acompanhados = mutableListOf<List<String>>()
    val lidasDoCarro = mutableListOf<List<String>>()
    val escritas = mutableListOf<Pair<String, String>>()
    var arNoMaximo = 0
    var camera = 0

    /** O que o motor grava no cache ao começar a acompanhar (sem avisar ninguém). */
    val aoAcompanhar = mutableMapOf<String, String>()

    override fun ler(chave: String): String? = valores[chave]
    override fun lerDoCarro(chaves: List<String>): Map<String, String> {
        lidasDoCarro.add(chaves)
        return chaves.mapNotNull { k -> diretos[k]?.let { k to it } }.toMap()
    }

    override fun acompanhar(chaves: Collection<String>) {
        acompanhados.add(chaves.toList())
        for (k in chaves) aoAcompanhar[k]?.let { valores[k] = it }
    }

    override fun escrever(chave: String, valor: String) {
        escritas.add(chave to valor)
    }

    override fun sairDoArNoMaximo() {
        arNoMaximo++
    }

    override fun abrirCamera360() {
        camera++
    }
}

/** Agendador manual: o fundo espera [rodarFundo] (ou roda na hora com [imediato]); os laços, [passar]. */
class AgendadorFalso : Agendador {
    var imediato = true
    val fundo = ArrayDeque<() -> Unit>()
    val repetidas = mutableListOf<Pair<Long, () -> Unit>>()
    var canceladas = 0

    override fun repetir(intervaloMs: Long, tarefa: () -> Unit): Cancelavel {
        val item = intervaloMs to tarefa
        repetidas.add(item)
        return Cancelavel {
            repetidas.remove(item)
            canceladas++
        }
    }

    override fun emSegundoPlano(tarefa: () -> Unit) {
        if (imediato) tarefa() else fundo.addLast(tarefa)
    }

    fun rodarFundo() {
        while (fundo.isNotEmpty()) fundo.removeFirst().invoke()
    }

    fun passar(intervaloMs: Long) {
        for ((intervalo, tarefa) in repetidas.toList()) if (intervalo == intervaloMs) tarefa()
    }
}

class RegistroFalso : RegistroDaPonte {
    val avisos = mutableListOf<String>()
    val infos = mutableListOf<String>()
    val erros = mutableListOf<String>()
    override fun info(mensagem: String) {
        infos.add(mensagem)
    }

    override fun aviso(mensagem: String) {
        avisos.add(mensagem)
    }

    override fun erro(mensagem: String, causa: Throwable?) {
        erros.add(mensagem)
    }
}

/** `SharedPreferences` em memória que lança `ClassCastException` com o tipo errado, como o de verdade. */
class PreferenciasEmMemoria : SharedPreferences {
    val mapa = LinkedHashMap<String, Any>()
    private val ouvintes = LinkedHashSet<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): MutableMap<String, *> = LinkedHashMap(mapa)
    override fun getString(key: String, defValue: String?): String? {
        val valor = mapa[key] ?: return defValue
        return valor as String
    }

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? {
        val valor = mapa[key] ?: return defValues
        return valor as MutableSet<String>
    }

    override fun getInt(key: String, defValue: Int): Int {
        val valor = mapa[key] ?: return defValue
        return valor as Int
    }

    override fun getLong(key: String, defValue: Long): Long {
        val valor = mapa[key] ?: return defValue
        return valor as Long
    }

    override fun getFloat(key: String, defValue: Float): Float {
        val valor = mapa[key] ?: return defValue
        return valor as Float
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        val valor = mapa[key] ?: return defValue
        return valor as Boolean
    }

    override fun contains(key: String): Boolean = mapa.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        if (listener != null) ouvintes.add(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        ouvintes.remove(listener)
    }

    val quantosOuvintes: Int get() = ouvintes.size

    /** Grava direto (como a tela de ajustes) e avisa os ouvintes. */
    fun gravar(chave: String, valor: Any?) {
        if (valor == null) mapa.remove(chave) else mapa[chave] = valor
        for (o in ouvintes.toList()) o.onSharedPreferenceChanged(this, chave)
    }

    private inner class Editor : SharedPreferences.Editor {
        private val mudancas = LinkedHashMap<String, Any?>()
        private var limpar = false

        private fun pondo(key: String, value: Any?): SharedPreferences.Editor {
            mudancas[key] = value
            return this
        }

        override fun putString(key: String, value: String?) = pondo(key, value)
        override fun putStringSet(key: String, values: MutableSet<String>?) = pondo(key, values)
        override fun putInt(key: String, value: Int) = pondo(key, value)
        override fun putLong(key: String, value: Long) = pondo(key, value)
        override fun putFloat(key: String, value: Float) = pondo(key, value)
        override fun putBoolean(key: String, value: Boolean) = pondo(key, value)
        override fun remove(key: String) = pondo(key, null)
        override fun clear(): SharedPreferences.Editor {
            limpar = true
            return this
        }
        override fun commit(): Boolean {
            aplicar()
            return true
        }

        override fun apply() {
            aplicar()
        }

        private fun aplicar() {
            if (limpar) mapa.clear()
            for ((k, v) in mudancas) gravar(k, v)
        }
    }
}

/** Lê de volta as chamadas que a ponte montou (literais de string JS). */
object LeitorDeScript {

    fun onDataChanged(js: String): Pair<String, String>? {
        val marca = "window.onDataChanged("
        val i = js.indexOf(marca)
        if (i < 0) return null
        val (chave, fim) = literal(js, i + marca.length) ?: return null
        if (js[fim] != ',') return null
        val (valor, _) = literal(js, fim + 1) ?: return null
        return chave to valor
    }

    fun onKeyEvent(js: String): String? {
        val marca = "window.onKeyEvent("
        val i = js.indexOf(marca)
        if (i < 0) return null
        return literal(js, i + marca.length)?.first
    }

    fun onCardChanged(js: String): Int? =
        Regex("window\\.onCardChanged\\((-?\\d+)\\)").find(js)?.groupValues?.get(1)?.toInt()

    /** Os `control(chave, literal)` de um script, com o literal cru. */
    fun controls(js: String): List<Pair<String, String>> {
        val saida = mutableListOf<Pair<String, String>>()
        val marca = "control("
        var i = js.indexOf(marca)
        while (i >= 0) {
            val antes = if (i > 0) js[i - 1] else ' '
            if (antes == '{' || antes == ';') {
                val lido = literal(js, i + marca.length)
                if (lido != null) {
                    val (chave, fim) = lido
                    val fecha = js.indexOf(");}catch", fim)
                    saida.add(chave to js.substring(fim + 1, fecha))
                }
            }
            i = js.indexOf(marca, i + 1)
        }
        return saida
    }

    /** Decodifica um literal de string JS entre aspas duplas começando em [inicio]; devolve (texto, índice depois). */
    fun literal(js: String, inicio: Int): Pair<String, Int>? {
        if (inicio >= js.length || js[inicio] != '"') return null
        val saida = StringBuilder()
        var i = inicio + 1
        while (i < js.length) {
            val c = js[i]
            when (c) {
                '"' -> return saida.toString() to i + 1
                '\\' -> {
                    val e = js[i + 1]
                    when (e) {
                        'n' -> saida.append('\n')
                        'r' -> saida.append('\r')
                        't' -> saida.append('\t')
                        'u' -> {
                            saida.append(js.substring(i + 2, i + 6).toInt(16).toChar())
                            i += 4
                        }
                        else -> saida.append(e)
                    }
                    i += 2
                    continue
                }
                '\n', '\r', ' ', ' ' -> return null
                else -> saida.append(c)
            }
            i++
        }
        return null
    }
}
