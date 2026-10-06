// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.motor

/*
 * As costuras do motor do veículo: tudo o que o [NucleoDoMotor] usa do mundo de fora chega por
 * aqui. No app são Android, Shizuku e a central (`LigacaoAndroid.kt`); nos testes de JVM, falsos
 * escritos à mão. Especificação: `docs/independencia/specs/motor-nucleo.md`.
 */

/** Algo agendado que ainda pode ser desmarcado. Desmarcar duas vezes não faz mal. */
internal fun interface Cancelavel {
    fun cancelar()
}

/**
 * A "linha do motor": relógio e fila de tarefas em série. No app, uma thread própria e o uptime do
 * aparelho; nos testes, um relógio virtual.
 */
internal interface Agenda {
    /** Instante atual, em ms de uptime. */
    fun agora(): Long

    /** Põe [tarefa] no fim da fila da linha. */
    fun executar(tarefa: () -> Unit)

    /** Roda [tarefa] na linha daqui a [atrasoMs]. */
    fun agendar(atrasoMs: Long, tarefa: () -> Unit): Cancelavel

    /** Roda [tarefa] na linha depois de [primeiraMs] e, daí em diante, a cada [periodoMs]. */
    fun repetir(primeiraMs: Long, periodoMs: Long, tarefa: () -> Unit): Cancelavel

    /** Para a thread de QUEM CHAMOU por [ms] (não é a linha). */
    fun pausar(ms: Long)
}

/** Como chegar à central: Shizuku, serviço de controle e alças da voz. */
internal interface Central {
    /** O Shizuku responde agora? Barato; nunca lança. */
    fun shizukuResponde(): Boolean

    /**
     * Procura o serviço de controle pelo nome no registro de serviços e o embrulha no Shizuku.
     * Null quando o nome não está registrado. Não faz chamada ao serviço.
     */
    fun abrirControle(): CanalDeControle?

    /**
     * Pega de novo as alças da voz (códigos 6, 8 e 13). [aoMorrer] é chamado, em qualquer thread,
     * quando uma das alças novas morre. Devolve true se as três estão de pé. Nunca lança.
     */
    fun renovarVoz(aoMorrer: () -> Unit): Boolean

    /** As três alças da voz existem e estão vivas. Barato, sem chamada remota. */
    fun vozCompleta(): Boolean
}

/**
 * Uma alça do serviço de controle da central (as seis operações de §2, mais ping, vida e aviso de
 * morte). As operações remotas lançam o que a central ou o binder lançarem.
 */
internal interface CanalDeControle {
    /** Ping direto na alça (não passa pelo Shizuku). Nunca lança. */
    fun responde(): Boolean

    /** A alça ainda está viva, sem chamada remota. Nunca lança. */
    fun vivo(): Boolean

    /** Liga o aviso de morte (troca um anterior desta alça). Lança se a alça já morreu. */
    fun ligarAvisoDeMorte(aoMorrer: () -> Unit)

    /** Desfaz o aviso de morte, se houver. Nunca lança. */
    fun desligarAvisoDeMorte()

    /** §2 nº 1: `request(acao, chave, valor)`. */
    fun pedir(acao: String, chave: String, valor: String)

    /** §2 nº 2: `fetchData`. Null = sem valor. */
    fun buscar(chave: String): String?

    /** §2 nº 3: `fetchDatas`. Mesmo tamanho e ordem do pedido; null nas posições sem valor. */
    fun buscarVarias(chaves: Array<String>): Array<out String?>?

    /** §2 nº 4: `addListenerKey` (soma; nunca substitui). */
    fun somarChaves(pacote: String, chaves: Array<String>)

    /** §2 nº 5: registra o ouvinte único do processo com o nome [pacote]. */
    fun registrarOuvinte(pacote: String)

    /** §2 nº 6: apaga o ouvinte e todas as chaves de [pacote]. */
    fun desregistrarOuvinte(pacote: String)
}

/** Manda os broadcasts da publicação (F.1, itens 2–4). */
internal interface Difusor {
    /**
     * Envia [acao]; com [chave] não nula, leva os extras `key` e `value`. [soDoApp] restringe ao
     * próprio pacote.
     */
    fun enviar(acao: String, chave: String?, valor: String?, soDoApp: Boolean)
}

/** Para onde vão as mensagens de diagnóstico (o logcat, no app). */
internal interface RegistroDoMotor {
    fun info(mensagem: String)

    fun aviso(mensagem: String, causa: Throwable? = null)

    fun erro(mensagem: String, causa: Throwable? = null)
}

/** Liga a fonte simulada, que injeta valores por [injetar]; devolve como desligá-la. */
internal fun interface LigarFonteSimulada {
    fun ligar(injetar: (chave: String, valor: String) -> Unit): Cancelavel
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
