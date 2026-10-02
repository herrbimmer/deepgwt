package br.com.opengw.ponte

/**
 * O que a ponte usa por fora do contrato, atrás de interfaces para a ponte inteira rodar na JVM dos
 * testes. As implementações do app estão em `DependenciasAndroid.kt`.
 */

/** O carro: as fachadas `Veiculo` e `Comandos`. */
interface Carro {
    /** Último valor conhecido (sem ir ao carro); null se nunca chegou. */
    fun ler(chave: String): String?

    /** Pergunta ao carro agora (lento; fora da principal). */
    fun lerDoCarro(chaves: List<String>): Map<String, String>

    fun acompanhar(chaves: Collection<String>)

    /** Manda o valor ao carro sem esperar resposta. */
    fun escrever(chave: String, valor: String)

    fun sairDoArNoMaximo()

    fun abrirCamera360()
}

/** Algo que se cancela. */
fun interface Cancelavel {
    fun cancelar()
}

/** Relógios e threads da ponte. */
interface Agendador {
    /** Roda [tarefa] na thread principal a cada [intervaloMs] (a primeira depois de um intervalo). */
    fun repetir(intervaloMs: Long, tarefa: () -> Unit): Cancelavel

    /** Roda [tarefa] numa thread de fundo; as tarefas rodam uma de cada vez, na ordem. */
    fun emSegundoPlano(tarefa: () -> Unit)
}

/** Registro da ponte (logcat no app). Nunca o console da página. */
interface RegistroDaPonte {
    fun info(mensagem: String)
    fun aviso(mensagem: String)
    fun erro(mensagem: String, causa: Throwable? = null)
}

/** Transforma a capa do álbum (o objeto de [MidiaDoApp.capa]) num data URL PNG de até 320 px. */
fun interface CodificadorDeCapa {
    fun dataUrl(capa: Any): String
}
