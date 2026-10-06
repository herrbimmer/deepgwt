package br.com.deepgwt.botoesextra

/**
 * As regras puras dos "Botões extra" com dispositivo externo (Alpha 10.0): teclado ou qualquer
 * dispositivo USB que o Linux enxergue como teclas. Sem Android, testado na JVM.
 *
 * O leitor é o `getevent` do shell privilegiado (mesma técnica dos gestos): vê a tecla em qualquer
 * tela, sem o app precisar estar na frente. O que se guarda é o NOME da tecla do Linux (`KEY_F13`),
 * e não o código numérico, porque o nome é o que o `getevent -l` imprime e o que o usuário entende.
 */
object RegrasDasTeclasExternas {

    enum class Fase { APERTOU, SOLTOU, REPETIU }

    data class EventoDeTecla(val tecla: String, val fase: Fase)

    /** Um nó de entrada que parece teclado: o caminho e o nome que o sistema dá. */
    data class DispositivoDeTeclas(val caminho: String, val nome: String)

    /** Teto de nós de teclas escutados ao mesmo tempo. */
    const val MAX_DISPOSITIVOS = 6

    private val EVENTO = Regex("\\bEV_KEY\\s+(\\S+)\\s+(\\S+)\\s*$")
    private val CABECALHO_DE_EVENTOS = Regex("^\\s+[A-Z_]+ \\([0-9a-fA-F]+\\):")

    /**
     * Uma linha do `getevent -l <nó>`: `EV_KEY       KEY_F13              DOWN`. Aceita o prefixo do nó
     * (`/dev/input/event3: EV_KEY ...`). Linhas de outros tipos (EV_SYN, EV_MSC) viram nulo.
     */
    fun lerEvento(linha: String): EventoDeTecla? {
        val m = EVENTO.find(linha.trim()) ?: return null
        val fase = when (m.groupValues[2]) {
            "DOWN" -> Fase.APERTOU
            "UP" -> Fase.SOLTOU
            "REPEAT" -> Fase.REPETIU
            else -> return null
        }
        return EventoDeTecla(m.groupValues[1], fase)
    }

    /**
     * Os nós de teclas no texto do `getevent -pl`: os que anunciam alguma `KEY_*` e não são
     * digitalizador de toque. Na ordem do sistema, no máximo [MAX_DISPOSITIVOS].
     */
    fun acharDispositivos(dump: String): List<DispositivoDeTeclas> {
        val achados = ArrayList<DispositivoDeTeclas>()
        var caminho: String? = null
        var nome = ""
        var temTecla = false
        var temToque = false
        var nasTeclas = false

        fun fechar() {
            val c = caminho
            if (c != null && temTecla && !temToque) achados += DispositivoDeTeclas(c, nome)
        }

        for (bruta in dump.lineSequence()) {
            val linha = bruta.trimEnd()
            val t = linha.trim()
            if (t.startsWith("add device")) {
                fechar()
                caminho = t.substringAfter(":").trim().takeIf { it.startsWith("/dev/input/") }
                nome = ""
                temTecla = false
                temToque = false
                nasTeclas = false
            } else if (t.startsWith("name:")) {
                nome = t.substringAfter(":").trim().trim('"')
            } else if (CABECALHO_DE_EVENTOS.containsMatchIn(linha)) {
                nasTeclas = t.startsWith("KEY (0001):")
                if (nasTeclas && t.substringAfter(":").split(Regex("\\s+")).any { it.startsWith("KEY_") }) temTecla = true
            } else if (nasTeclas && t.split(Regex("\\s+")).any { it.startsWith("KEY_") }) {
                temTecla = true
            }
            // Digitalizador de toque tem ABS_MT_POSITION_X: não é teclado, mesmo que anuncie KEY.
            if (t.contains("ABS_MT_POSITION_X")) temToque = true
        }
        fechar()
        return achados.take(MAX_DISPOSITIVOS)
    }

    /** `KEY_F13` → `F13`; `KEY_VOLUMEUP` → `Volumeup`; `BTN_TRIGGER` → `Trigger`. Só para exibir. */
    fun rotuloDaTecla(tecla: String): String {
        val sem = tecla.removePrefix("KEY_").removePrefix("BTN_")
        val limpo = sem.replace('_', ' ').trim()
        if (limpo.isEmpty()) return tecla
        return if (limpo.length <= 3) limpo.uppercase() else limpo.lowercase().replaceFirstChar { it.uppercase() }
    }

    // ── A ação ligada a uma tecla: texto que viaja na preferência ──

    const val PREFIXO_DECK = "deck:"
    const val PREFIXO_AJUSTE = "ajuste:"

    sealed interface AcaoDaTecla {
        data class FuncaoDoDeck(val nome: String) : AcaoDaTecla
        data class LigaDesligaAjuste(val id: String) : AcaoDaTecla
    }

    fun codificar(acao: AcaoDaTecla): String = when (acao) {
        is AcaoDaTecla.FuncaoDoDeck -> PREFIXO_DECK + acao.nome
        is AcaoDaTecla.LigaDesligaAjuste -> PREFIXO_AJUSTE + acao.id
    }

    fun decodificar(texto: String?): AcaoDaTecla? = when {
        texto == null -> null
        texto.startsWith(PREFIXO_DECK) && texto.length > PREFIXO_DECK.length ->
            AcaoDaTecla.FuncaoDoDeck(texto.removePrefix(PREFIXO_DECK))
        texto.startsWith(PREFIXO_AJUSTE) && texto.length > PREFIXO_AJUSTE.length ->
            AcaoDaTecla.LigaDesligaAjuste(texto.removePrefix(PREFIXO_AJUSTE))
        else -> null
    }
}
