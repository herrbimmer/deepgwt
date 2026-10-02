package br.com.opengw.telas

import br.com.opengw.nucleo.Preferencia

// As costuras do lançador: tudo o que é sistema, Android, tempo ou binder entra por aqui. A
// implementação real fica em `LancadorAndroid.kt`; os testes de JVM usam falsos.

/** Comandos de sistema. A real roda `sh -c "<comando> 2>&1"` pelo `Shell` (Shizuku). */
internal interface ShellDoLancador {
    /** A saída (padrão e erro juntas); `""` quando não deu. Nunca lança. */
    fun rodar(comando: String): String
    fun disponivel(): Boolean
}

/** Tempo: monotônico (intervalos), de parede (horários gravados, logcat) e a espera de corrotina. */
internal interface RelogioDoLancador {
    fun monotonico(): Long
    fun parede(): Long
    suspend fun esperar(ms: Long)
}

/** O arquivo "opengw_prefs" do contexto protegido. Valor de outro tipo conta como ausente. */
internal interface PreferenciasDoLancador {
    fun texto(chave: Preferencia): String?

    /** Null quando a chave não existe. */
    fun inteiro(chave: Preferencia): Int?
    fun booleano(chave: Preferencia, padrao: Boolean): Boolean

    /** Uma escrita só (`apply`), com todos os valores (Int, Boolean ou String). */
    fun gravar(valores: Map<Preferencia, Any>)
}

/** O que é do Android e não é comando de shell. */
internal interface SistemaDoLancador {
    val densidade: Float
    val pacoteDoApp: String
    val depuracao: Boolean

    /** Largura e altura físicas do modo atual do display; null se ele não existe. */
    fun resolucao(display: Int): Pair<Int, Int>?

    /** A classe da atividade de abertura do pacote (`PackageManager`); null se não há. */
    fun atividadeDeAbertura(pacote: String): String?

    /** Abre o pacote pela intent de abertura com estas flags; false se não há intent. */
    fun abrirPelaIntent(pacote: String, flags: Int): Boolean

    /** Broadcast "reposicionar a barra" (2, fatos da central). */
    fun reposicionarBarra()

    /** Lê um arquivo direto (sem shell); null se ilegível. */
    fun lerArquivo(caminho: String): String?

    fun naThreadPrincipal(bloco: () -> Unit)
}

/** O carro (`Veiculo`). */
internal interface CarroDoLancador {
    /** Pergunta ao carro agora. */
    fun lerDoCarro(chave: String): String?

    /** Último valor conhecido. */
    fun ler(chave: String): String?
}

/** Trabalho em segundo plano no escopo próprio do módulo (supervisionado). */
internal interface FundoDoLancador {
    fun lancar(nome: String, bloco: suspend () -> Unit)
}

/** Log do módulo. */
internal interface RegistroDoLancador {
    fun depuracao(texto: String)
    fun info(texto: String)
    fun aviso(texto: String)
    fun erro(texto: String, causa: Throwable? = null)
}

/** O canal de comandos do Android Auto (`LinkCommand`, parte 4-A1). */
internal interface CanalDoAndroidAuto {
    fun vivo(): Boolean
    fun ligacaoPedida(): Boolean

    /** "Garantir ligado": devolve se o binder já está vivo (em geral não: ele chega depois). */
    fun garantirLigado(): Boolean

    /** Status (21) só se o binder já está vivo; não pede ligação. */
    fun statusSeJaLigado(): Int?
    fun status(): Int?
    fun musica(): Int?
    fun progresso(): Int?

    /** `sendKeyEvent` (10), unidirecional. */
    fun tecla(ordinal: Int, acao: Int): Boolean
    fun proxima(): Boolean
    fun anterior(): Boolean
    fun tocar(): Boolean
    fun pausar(): Boolean
    fun aparelhos(): List<AparelhoDoAa>?
    fun conectar(aparelho: AparelhoDoAa): Boolean
    fun ouvirSemFio(aparelho: AparelhoDoAa): Boolean
}

/** A interface do serviço do CarPlay (`ICarPlayService`, parte 3-K9). */
internal interface CanalDoCarPlay {
    fun vivo(): Boolean
    fun pedirLigacao()

    /** Transação 29; null se falhou. */
    fun status(): Int?

    /** Transação 20 com `0` (modo normal). */
    fun modoNormal(): Boolean
}

/** Tudo junto. */
internal class AmbienteDoLancador(
    val shell: ShellDoLancador,
    val relogio: RelogioDoLancador,
    val prefs: PreferenciasDoLancador,
    val sistema: SistemaDoLancador,
    val carro: CarroDoLancador,
    val canalAa: CanalDoAndroidAuto,
    val canalCp: CanalDoCarPlay,
    val fundo: FundoDoLancador,
    val registro: RegistroDoLancador,
)

/** "No máximo a cada N ms": [tentar] marca e devolve true quando já passou o intervalo. */
internal class IntervaloMinimo(private val minimoMs: Long) {
    private var ultima: Long? = null

    @Synchronized
    fun tentar(agora: Long): Boolean {
        val u = ultima
        if (u != null && agora >= u && agora - u < minimoMs) return false
        ultima = agora
        return true
    }

    @Synchronized
    fun zerar() {
        ultima = null
    }
}

/** Chaves de preferência que este módulo usa e ainda não estão no catálogo `Preferencias`. */
internal object ChavesDoLancador {
    val DESTINO_DO_CARPLAY = Preferencia("desiredCarPlayDisplayId")
    val DESTINO_DO_ANDROID_AUTO = Preferencia("desiredAndroidAutoDisplayId")
    val TOKEN_DA_PARTIDA_DO_CARPLAY = Preferencia("carPlayBootAutostartBootToken")
}
