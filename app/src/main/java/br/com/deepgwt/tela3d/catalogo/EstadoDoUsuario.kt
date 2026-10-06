package br.com.deepgwt.tela3d.catalogo

import android.content.SharedPreferences

/** Arquivo e chaves das preferências do catálogo. Estão nos carros: NÃO renomear. */
internal object ChavesDoCatalogo {
    const val ARQUIVO = "deepgwt_lancador"
    const val RECENTES = "recent_apps"
    const val OCULTOS = "hidden_apps"
    const val JANELA = "window_apps"
    const val PERSONALIZACOES = "dock_app_overrides"

    /** Cópia do texto de personalização que não deu para ler, antes de ele ser regravado. */
    const val PERSONALIZACOES_INVALIDAS = "dock_app_overrides_invalido"
}

/** Onde o estado do usuário é guardado: texto por chave. */
internal interface ArmazemDePreferencias {
    fun ler(chave: String): String?
    fun gravar(chave: String, valor: String)
}

/** Armazém sobre [SharedPreferences]; grava com `apply()`. */
internal class ArmazemDeSharedPreferences(private val prefs: SharedPreferences) : ArmazemDePreferencias {
    override fun ler(chave: String): String? = prefs.getString(chave, null)

    override fun gravar(chave: String, valor: String) {
        prefs.edit().putString(chave, valor).apply()
    }
}

/** Retrato imutável do que o usuário decidiu sobre os apps. Leitura segura de qualquer thread. */
internal class EstadoDoUsuario(
    /** Mais recente primeiro, sem repetição, até 12. */
    val recentes: List<String>,
    val ocultos: Set<String>,
    val janela: Set<String>,
    val personalizacoes: MapaDePersonalizacoes,
) {
    companion object {
        val VAZIO = EstadoDoUsuario(emptyList(), emptySet(), emptySet(), MapaDePersonalizacoes.VAZIO)
    }
}

/**
 * Guarda e altera o [EstadoDoUsuario]. As mudanças acontecem sob trava (de qualquer thread) e cada
 * uma troca o retrato inteiro, então quem lê [estado] nunca vê um conjunto sendo mexido.
 * Persiste na hora, com `apply()`.
 */
internal class RepositorioDoUsuario(
    private val armazem: ArmazemDePreferencias,
    private val regras: RegrasDoCatalogo,
    private val aviso: (String) -> Unit = {},
) {
    private val trava = Any()

    @Volatile
    var estado: EstadoDoUsuario = EstadoDoUsuario.VAZIO
        private set

    init {
        val leitura = MapaDePersonalizacoes.lerTexto(armazem.ler(ChavesDoCatalogo.PERSONALIZACOES))
        if (leitura.textoInvalido != null) {
            aviso("Bad dock_app_overrides JSON")
            armazem.gravar(ChavesDoCatalogo.PERSONALIZACOES_INVALIDAS, leitura.textoInvalido)
        }
        estado = EstadoDoUsuario(
            recentes = ListaDePacotes.ler(armazem.ler(ChavesDoCatalogo.RECENTES)),
            ocultos = LinkedHashSet(ListaDePacotes.ler(armazem.ler(ChavesDoCatalogo.OCULTOS))),
            janela = LinkedHashSet(ListaDePacotes.ler(armazem.ler(ChavesDoCatalogo.JANELA))),
            personalizacoes = leitura.mapa,
        )
    }

    /** Registra a abertura de um app: ignora o que não pode ser recente. @return true se a lista mudou. */
    fun registrarAbertura(pacote: String?): Boolean {
        if (pacote.isNullOrEmpty()) return false
        synchronized(trava) {
            val atual = estado
            if (!regras.podeSerRecente(pacote, atual.ocultos)) return false
            val nova = RecentesMru.inserir(atual.recentes, pacote)
            if (nova == atual.recentes) return false
            trocarRecentes(nova)
            return true
        }
    }

    /** Semeia os recentes se a lista está vazia. @return true se semeou algo. */
    fun semearSeVazio(candidatos: List<String>, instalados: Set<String>): Boolean {
        synchronized(trava) {
            val atual = estado
            if (atual.recentes.isNotEmpty()) return false
            val semente = RecentesMru.semear(candidatos, instalados) { regras.podeSerRecente(it, atual.ocultos) }
            if (semente.isEmpty()) return false
            trocarRecentes(semente)
            return true
        }
    }

    /** Oculta o pacote e o tira dos recentes. @return true se mudou algo. */
    fun ocultar(pacote: String?): Boolean = ocultarVarios(listOfNotNull(pacote))

    /** Oculta vários pacotes de uma vez (o hub oculta os 4 fixados). */
    fun ocultarVarios(pacotes: Collection<String>): Boolean {
        val validos = pacotes.filter { it.isNotEmpty() }
        if (validos.isEmpty()) return false
        synchronized(trava) {
            val atual = estado
            val ocultos = LinkedHashSet(atual.ocultos)
            val mudouOcultos = ocultos.addAll(validos)
            val recentes = atual.recentes.filter { it !in validos }
            val mudouRecentes = recentes.size != atual.recentes.size
            if (!mudouOcultos && !mudouRecentes) return false
            if (mudouOcultos) {
                armazem.gravar(ChavesDoCatalogo.OCULTOS, ListaDePacotes.escreverOrdenado(ocultos))
            }
            if (mudouRecentes) {
                armazem.gravar(ChavesDoCatalogo.RECENTES, ListaDePacotes.escrever(recentes))
            }
            estado = EstadoDoUsuario(recentes, ocultos, atual.janela, atual.personalizacoes)
            return true
        }
    }

    /** Tira o pacote dos ocultos. @return true só se ele estava oculto. */
    fun restaurar(pacote: String?): Boolean {
        if (pacote.isNullOrEmpty()) return false
        synchronized(trava) {
            val atual = estado
            if (pacote !in atual.ocultos) return false
            val ocultos = LinkedHashSet(atual.ocultos)
            ocultos.remove(pacote)
            armazem.gravar(ChavesDoCatalogo.OCULTOS, ListaDePacotes.escreverOrdenado(ocultos))
            estado = EstadoDoUsuario(atual.recentes, ocultos, atual.janela, atual.personalizacoes)
            return true
        }
    }

    /**
     * Alterna "abrir como janela". @return o novo valor, ou null se o pacote não pode alternar.
     */
    fun alternarJanela(pacote: String?): Boolean? {
        if (pacote.isNullOrEmpty() || !regras.podeAlternarJanela(pacote)) return null
        synchronized(trava) {
            val atual = estado
            val janela = LinkedHashSet(atual.janela)
            val agora = if (pacote in janela) {
                janela.remove(pacote)
                false
            } else {
                janela.add(pacote)
                true
            }
            armazem.gravar(ChavesDoCatalogo.JANELA, ListaDePacotes.escreverOrdenado(janela))
            estado = EstadoDoUsuario(atual.recentes, atual.ocultos, janela, atual.personalizacoes)
            return agora
        }
    }

    fun salvarPersonalizacao(pacote: String?, nome: String?, icone: String?, cor: String?, fundo: String?) {
        if (pacote.isNullOrEmpty()) return
        synchronized(trava) {
            val atual = estado
            trocarPersonalizacoes(atual, atual.personalizacoes.gravar(pacote, nome, icone, cor, fundo))
        }
    }

    fun limparPersonalizacao(pacote: String?) {
        if (pacote.isNullOrEmpty()) return
        synchronized(trava) {
            val atual = estado
            trocarPersonalizacoes(atual, atual.personalizacoes.remover(pacote))
        }
    }

    /**
     * O pacote foi desinstalado: sai dos recentes, dos ocultos e do "abrir como janela". A
     * personalização NÃO é apagada (fica órfã e volta se o app for reinstalado).
     * @return true se algum estado mudou.
     */
    fun aoDesinstalar(pacote: String?): Boolean {
        if (pacote.isNullOrEmpty()) return false
        synchronized(trava) {
            val atual = estado
            var recentes = atual.recentes
            var ocultos = atual.ocultos
            var janela = atual.janela
            var mudou = false
            if (pacote in recentes) {
                recentes = recentes.filter { it != pacote }
                armazem.gravar(ChavesDoCatalogo.RECENTES, ListaDePacotes.escrever(recentes))
                mudou = true
            }
            if (pacote in ocultos) {
                ocultos = LinkedHashSet(ocultos).also { it.remove(pacote) }
                armazem.gravar(ChavesDoCatalogo.OCULTOS, ListaDePacotes.escreverOrdenado(ocultos))
                mudou = true
            }
            if (pacote in janela) {
                janela = LinkedHashSet(janela).also { it.remove(pacote) }
                armazem.gravar(ChavesDoCatalogo.JANELA, ListaDePacotes.escreverOrdenado(janela))
                mudou = true
            }
            if (mudou) estado = EstadoDoUsuario(recentes, ocultos, janela, atual.personalizacoes)
            return mudou
        }
    }

    private fun trocarRecentes(novos: List<String>) {
        val atual = estado
        armazem.gravar(ChavesDoCatalogo.RECENTES, ListaDePacotes.escrever(novos))
        estado = EstadoDoUsuario(novos, atual.ocultos, atual.janela, atual.personalizacoes)
    }

    private fun trocarPersonalizacoes(atual: EstadoDoUsuario, nova: MapaDePersonalizacoes) {
        armazem.gravar(ChavesDoCatalogo.PERSONALIZACOES, nova.paraTexto())
        estado = EstadoDoUsuario(atual.recentes, atual.ocultos, atual.janela, nova)
    }
}
