package br.com.deepgwt.tela3d.janelas

import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.graphics.Rect
import android.os.Binder
import android.os.Build
import android.os.Parcel
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [PrimitivasDeTarefa] sobre o Android: APIs públicas do `ActivityManager`, APIs ocultas por
 * reflexão (`resizeTask`, `removeTask`, serviço de tarefas, `setTaskAlwaysOnTop`) e, como última
 * reserva, o `am stack` do shell privilegiado ([PilhasDoSistema]). Roda fora da thread principal.
 */
internal class PrimitivasAndroid(
    private val contexto: Context,
    private val pacoteProprio: String,
    private val pilhas: PilhasDoSistema,
    private val guarda: GuardaDeLevantar,
    private val noTopo: SuporteAoNoTopo,
    private val relogio: Relogio,
    private val diario: Diario,
) : PrimitivasDeTarefa {

    private val gerente: ActivityManager? =
        contexto.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

    /** I3: o serviço de sistema de tarefas; uma falha o marca "indisponível" para sempre nesta instância. */
    @Volatile private var servicoResolvido = false
    @Volatile private var servicoDeTarefas: Any? = null
    private val falhaDaListaJaRegistrada = AtomicBoolean(false)

    private val memoriaDeNegacao = MemoriaDeNegacao(relogio)
    private var dumpsysGuardado: String? = null
    private var dumpsysGuardadoEm = Long.MIN_VALUE

    private val leitorDeVisibilidade = LeitorDeVisibilidade(
        pacoteProprio = pacoteProprio,
        dumpsys = ::visibilidadePeloDumpsys,
        listaDoServico = ::tarefasDoServico,
        processos = ::processosEmExecucao,
        tarefasPublicas = ::tarefasEmExecucao,
        diario = diario,
    )

    /** I1. */
    fun visibilidade(pacote: String, tarefaGuardada: Int): Int = leitorDeVisibilidade.ler(pacote, tarefaGuardada)

    // --- I3: o serviço de tarefas e a lista dele ---

    @Synchronized
    private fun servico(): Any? {
        if (servicoResolvido) return servicoDeTarefas
        servicoResolvido = true
        ApisOcultas.liberar()
        servicoDeTarefas = try {
            val classe = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Class.forName("android.app.ActivityTaskManager")
            } else {
                ActivityManager::class.java
            }
            classe.getMethod("getService").invoke(null)
        } catch (e: Throwable) {
            diario.aviso("serviço de tarefas indisponível: ${e.javaClass.simpleName}")
            null
        }
        return servicoDeTarefas
    }

    private fun tarefasDoServico(): List<RegistroDeTarefa>? {
        val servico = servico() ?: return null
        return try {
            val metodo = servico.javaClass.methods.firstOrNull { it.name == "getTasks" && it.parameterTypes.isNotEmpty() }
                ?: return null
            val tipos = metodo.parameterTypes
            val resultado = when {
                tipos.size == 1 -> metodo.invoke(servico, MAXIMO_DE_TAREFAS)
                tipos.size == 2 && tipos[1] == java.lang.Boolean.TYPE -> metodo.invoke(servico, MAXIMO_DE_TAREFAS, false)
                else -> metodo.invoke(servico, MAXIMO_DE_TAREFAS, false, false)
            }
            (resultado as? List<*>)?.mapNotNull { (it as? ActivityManager.RunningTaskInfo)?.let(::registroDe) }
        } catch (e: Throwable) {
            if (falhaDaListaJaRegistrada.compareAndSet(false, true)) {
                diario.aviso("lista de tarefas do serviço indisponível: ${e.javaClass.simpleName}")
            }
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun tarefasEmExecucao(): List<RegistroDeTarefa>? =
        try {
            gerente?.getRunningTasks(MAXIMO_DE_TAREFAS)?.map(::registroDe)
        } catch (e: Exception) {
            diario.aviso("getRunningTasks falhou: ${e.javaClass.simpleName}")
            null
        }

    @Suppress("DEPRECATION")
    private fun registroDe(t: ActivityManager.RunningTaskInfo): RegistroDeTarefa =
        RegistroDeTarefa(
            id = t.id,
            pacoteDoTopo = t.topActivity?.packageName,
            pacoteDaBase = t.baseActivity?.packageName,
            visivel = campoBooleano(t, "isVisible", padrao = true),
        )

    private fun campoBooleano(alvo: Any, nome: String, padrao: Boolean): Boolean = try {
        alvo.javaClass.getField(nome).getBoolean(alvo)
    } catch (e: Exception) {
        padrao
    }

    private fun processosEmExecucao(): List<RegistroDeProcesso>? =
        gerente?.runningAppProcesses?.map { RegistroDeProcesso(it.pkgList?.toList().orEmpty(), it.importance) }

    // --- I1, passo 1: dumpsys como processo filho, com prazo real e memória de negação ---

    private fun visibilidadePeloDumpsys(pacote: String): Int {
        if (!memoriaDeNegacao.deveTentar()) return Visibilidade.DESCONHECIDA
        val texto = textoDoDumpsys() ?: return Visibilidade.DESCONHECIDA
        if (LeitorDeDumpsys.foiNegado(texto)) {
            memoriaDeNegacao.registrarNegado()
            diario.info("dumpsys negado; pulando este passo por alguns minutos")
            return Visibilidade.DESCONHECIDA
        }
        return LeitorDeDumpsys.visibilidade(texto, pacote)
    }

    /** Os dois lados são lidos em sequência pelo mesmo vigia: o texto vale por [VALIDADE_DO_DUMPSYS_MS]. */
    @Synchronized
    private fun textoDoDumpsys(): String? {
        val agora = relogio.agoraMs()
        dumpsysGuardado?.let { if (agora - dumpsysGuardadoEm < VALIDADE_DO_DUMPSYS_MS) return it }
        val texto = LeitorDeProcesso.ler(COMANDO_DO_DUMPSYS, PRAZO_DO_DUMPSYS_MS, LIMITE_DO_DUMPSYS) ?: return null
        dumpsysGuardado = texto
        dumpsysGuardadoEm = relogio.agoraMs()
        return texto
    }

    // --- C35: achar a tarefa de um pacote ---

    override fun acharTarefa(pacote: String): Int {
        if (pacote.isEmpty()) return -1
        val publicas = runCatching { achar(pacote, tarefasEmExecucao()) }.getOrDefault(-1)
        if (publicas >= 0) return publicas
        val recentes = runCatching { acharNasRecentes(pacote) }.getOrDefault(-1)
        if (recentes >= 0) return recentes
        val doServico = runCatching { achar(pacote, tarefasDoServico()) }.getOrDefault(-1)
        if (doServico >= 0) return doServico
        return runCatching { pilhas.acharTarefa(pacote) }.getOrDefault(-1)
    }

    private fun achar(pacote: String, lista: List<RegistroDeTarefa>?): Int =
        lista?.firstOrNull { it.pacoteDoTopo == pacote || it.pacoteDaBase == pacote }?.id ?: -1

    /** I2: recentes — pacote de `origActivity`, de `realActivity` (campo oculto) ou do componente de `baseIntent`. */
    @Suppress("DEPRECATION")
    private fun acharNasRecentes(pacote: String): Int {
        val recentes = gerente?.getRecentTasks(MAXIMO_DE_TAREFAS, FLAG_RECENTES_COM_EXCLUIDAS) ?: return -1
        for (r in recentes) {
            val daReal = (campoOculto(r, "realActivity") as? ComponentName)?.packageName
            val pacotes = listOf(r.origActivity?.packageName, daReal, r.baseIntent?.component?.packageName)
            if (pacote in pacotes) return if (r.persistentId > 0) r.persistentId else r.id
        }
        return -1
    }

    private fun campoOculto(alvo: Any, nome: String): Any? = try {
        alvo.javaClass.getField(nome).get(alvo)
    } catch (e: Exception) {
        null
    }

    // --- C36: redimensionar ---

    override fun redimensionar(tarefa: Int, limites: Retangulo): Boolean {
        if (tarefa < 0) return false
        ApisOcultas.liberar()
        val retangulo = Rect(limites.esquerda, limites.topo, limites.direita, limites.fundo)
        val inteiro = Integer.TYPE
        val gerente = gerente
        if (gerente != null) {
            try {
                gerente.javaClass.getMethod("resizeTask", inteiro, Rect::class.java).invoke(gerente, tarefa, retangulo)
                diario.info("tarefa $tarefa fixada via AM.resizeTask(2)")
                return true
            } catch (e: Throwable) {
                // segue para a próxima rota
            }
            try {
                gerente.javaClass.getMethod("resizeTask", inteiro, Rect::class.java, inteiro).invoke(gerente, tarefa, retangulo, 0)
                diario.info("tarefa $tarefa fixada via AM.resizeTask(3)")
                return true
            } catch (e: Throwable) {
                // segue para a próxima rota
            }
        }
        try {
            val servico = servico()
            val metodo = servico?.javaClass?.methods?.firstOrNull {
                it.name == "resizeTask" && it.parameterTypes.size >= 2 &&
                    it.parameterTypes[0] == inteiro && it.parameterTypes[1] == Rect::class.java
            }
            if (servico != null && metodo != null) {
                if (metodo.parameterTypes.size == 2) {
                    metodo.invoke(servico, tarefa, retangulo)
                } else if (metodo.parameterTypes.size == 3 && metodo.parameterTypes[2] == inteiro) {
                    metodo.invoke(servico, tarefa, retangulo, 0)
                } else {
                    throw NoSuchMethodException("assinatura de resizeTask desconhecida")
                }
                diario.info("tarefa $tarefa fixada via ATM")
                return true
            }
        } catch (e: Throwable) {
            // segue para a reserva do shell
        }
        return try {
            pilhas.redimensionar(tarefa, limites)
        } catch (e: Exception) {
            false
        }
    }

    // --- C37: levantar ---

    override fun levantar(tarefa: Int): Boolean {
        if (tarefa < 0) return false
        if (guarda.repetida(tarefa)) return true
        val gerente = gerente
        if (gerente != null) {
            try {
                val animacao = ActivityOptions.makeCustomAnimation(contexto, 0, 0).toBundle()
                gerente.moveTaskToFront(tarefa, ActivityManager.MOVE_TASK_NO_USER_ACTION, animacao)
                guarda.registrarSucesso(tarefa)
                diario.info("levantar ok: tarefa $tarefa")
                return true
            } catch (e: Throwable) {
                diario.info("levantar recusado: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
        val pelaReserva = try {
            pilhas.levantar(tarefa)
        } catch (e: Exception) {
            false
        }
        if (pelaReserva) {
            guarda.registrarSucesso(tarefa)
            diario.info("levantar ok pelo shell: tarefa $tarefa")
        }
        return pelaReserva
    }

    // --- C38: sempre no topo ---

    override fun marcarNoTopo(tarefa: Int): Boolean {
        if (tarefa < 0 || !noTopo.suportado) return false
        val servico = servico()
        if (servico == null) {
            noTopo.marcarNaoSuportado()
            return false
        }
        return try {
            val metodo = servico.javaClass.methods.firstOrNull {
                it.name == "setTaskAlwaysOnTop" && it.parameterTypes.size == 2 &&
                    it.parameterTypes[0] == Integer.TYPE && it.parameterTypes[1] == java.lang.Boolean.TYPE
            }
            if (metodo == null) {
                noTopo.marcarNaoSuportado()
                diario.info("setTaskAlwaysOnTop indisponível; usando moveTaskToFront")
                return false
            }
            metodo.invoke(servico, tarefa, true)
            diario.info("tarefa $tarefa marcada sempre no topo")
            true
        } catch (e: Throwable) {
            noTopo.marcarNaoSuportado()
            diario.info("setTaskAlwaysOnTop falhou (${e.javaClass.simpleName}); usando moveTaskToFront")
            false
        }
    }

    // --- C53: remover ---

    override fun remover(tarefa: Int) {
        if (tarefa < 0) return
        ApisOcultas.liberar()
        val gerente = gerente
        if (gerente != null) {
            try {
                gerente.javaClass.getMethod("removeTask", Integer.TYPE).invoke(gerente, tarefa)
                diario.info("tarefa removida: $tarefa")
                return
            } catch (e: Throwable) {
                diario.info("removeTask($tarefa) recusado: ${causa(e)}")
            }
        }
        try {
            val servico = servico() ?: return
            servico.javaClass.getMethod("removeTask", Integer.TYPE).invoke(servico, tarefa)
            diario.info("tarefa $tarefa removida via serviço do sistema")
        } catch (e: Throwable) {
            diario.info("remoção da tarefa $tarefa falhou: ${causa(e)}")
        }
    }

    private fun causa(e: Throwable): String = (e.cause ?: e).javaClass.simpleName

    // --- C51: ouvinte de pilha de tarefas ---

    private var ouvinte: Any? = null

    /** Registra um ouvinte de `ITaskStackListener`; verdadeiro se alguma rota aceitou. */
    fun registrarOuvinte(aoMudar: Runnable): Boolean {
        ApisOcultas.liberar()
        return try {
            val interfaceDoOuvinte = Class.forName("android.app.ITaskStackListener")
            val binder = object : Binder() {
                // Os avisos da pilha são todos eventos sem retorno: qualquer transação é "a pilha mudou".
                override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                    aoMudar.run()
                    return true
                }
            }
            val procurador = Proxy.newProxyInstance(
                PrimitivasAndroid::class.java.classLoader,
                arrayOf(interfaceDoOuvinte),
                ManipuladorDoOuvinte(binder, aoMudar),
            )
            val registrado = try {
                val gerente = checkNotNull(gerente)
                gerente.javaClass.getMethod("registerTaskStackListener", interfaceDoOuvinte).invoke(gerente, procurador)
                true
            } catch (e: Throwable) {
                val servico = servico() ?: throw e
                servico.javaClass.getMethod("registerTaskStackListener", interfaceDoOuvinte).invoke(servico, procurador)
                true
            }
            if (registrado) ouvinte = procurador
            registrado
        } catch (e: Throwable) {
            diario.aviso("ouvinte de pilha indisponível: ${causa(e)}")
            false
        }
    }

    fun removerOuvinte() {
        val registrado = ouvinte ?: return
        ouvinte = null
        try {
            val interfaceDoOuvinte = Class.forName("android.app.ITaskStackListener")
            try {
                val gerente = checkNotNull(gerente)
                gerente.javaClass.getMethod("unregisterTaskStackListener", interfaceDoOuvinte).invoke(gerente, registrado)
            } catch (e: Throwable) {
                val servico = servico() ?: return
                servico.javaClass.getMethod("unregisterTaskStackListener", interfaceDoOuvinte).invoke(servico, registrado)
            }
        } catch (e: Throwable) {
            // remoção simétrica: falhas engolidas
        }
    }

    /**
     * Procurador da interface oculta: `asBinder` devolve o binder local; os métodos de aviso (os mesmos
     * da lista da especificação) disparam [aoMudar]; os demais devolvem `false`/`0`/`0L`/`null` conforme
     * o tipo de retorno.
     */
    private class ManipuladorDoOuvinte(
        private val binder: Binder,
        private val aoMudar: Runnable,
    ) : InvocationHandler {
        override fun invoke(proxy: Any, metodo: Method, args: Array<out Any?>?): Any? {
            return when (metodo.name) {
                "asBinder" -> binder
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "OuvinteDePilha"
                in AVISOS_DA_PILHA -> {
                    aoMudar.run()
                    valorPadrao(metodo.returnType)
                }
                else -> valorPadrao(metodo.returnType)
            }
        }

        private fun valorPadrao(tipo: Class<*>): Any? = when (tipo) {
            java.lang.Boolean.TYPE -> false
            Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            Character.TYPE -> 0.toChar()
            else -> null
        }
    }

    companion object {
        const val MAXIMO_DE_TAREFAS = 32

        /** `ActivityManager.RECENT_WITH_EXCLUDED`. */
        const val FLAG_RECENTES_COM_EXCLUIDAS = 1

        private val COMANDO_DO_DUMPSYS = listOf("/system/bin/dumpsys", "activity", "activities")
        private const val PRAZO_DO_DUMPSYS_MS = 900L
        private const val LIMITE_DO_DUMPSYS = 350_000
        private const val VALIDADE_DO_DUMPSYS_MS = 300L

        private val AVISOS_DA_PILHA = setOf(
            "onTaskStackChanged",
            "onTaskRemoved",
            "onTaskRemovalStarted",
            "onRecentTaskListUpdated",
            "onTaskMovedToFront",
            "onActivityUnpinned",
        )
    }
}
