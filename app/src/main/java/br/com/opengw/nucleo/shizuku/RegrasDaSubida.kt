package br.com.opengw.nucleo.shizuku

/**
 * As regras puras da subida do Shizuku (`docs/independencia/specs/subida-do-shizuku.md`). Kotlin
 * puro, sem Android: tudo aqui é testado na JVM. Quem age (telnet, binder, preferências) fica em
 * [CicloDaSubida] e em [SubidaDoShizuku].
 */
object RegrasDaSubida {

    // ── Tempos e limites (spec, "Tempos e limites") ──

    /** Janela de espera do binder (§C). */
    const val JANELA_DO_BINDER_MS = 15_000L

    /** Prazo para conferir o desfecho do start (§B8). */
    const val PRAZO_DO_DESFECHO_MS = 12_000L

    /** Intervalo entre `pingBinder` durante o §B8. */
    const val INTERVALO_DO_PING_MS = 250L

    /** Intervalo entre `pidof shizuku_server` durante o §B8. */
    const val INTERVALO_DO_PID_MS = 2_000L

    /** Re-tentativa depois de falha do procedimento (§B9). */
    const val RETENTATIVA_MS = 1_000L

    /** No 3º timeout de binder o serviço é reiniciado (§C2). */
    const val TIMEOUTS_ATE_REINICIAR = 3

    /** Teto real de UID de app no Android (10000–19999), §A2. */
    const val TETO_DE_UID = 19_999

    /** Rede de segurança (§H): espera inicial, folga entre tentativas, limite e espera pós-start. */
    const val REDE_ESPERA_INICIAL_MS = 45_000L
    const val REDE_INTERVALO_MS = 30_000L
    const val REDE_TENTATIVAS = 20
    const val REDE_ESPERA_DEPOIS_DO_START_MS = 3_000L

    // ── Textos estáveis (contrato com o serviço, o diário e o /diag) ──

    const val MOTIVO_UID = "uid"
    const val MOTIVO_TIMEOUT = "shizuku_binder_timeout:3"
    const val MOTIVO_BINDER_MORTO = "shizuku_binder_dead"

    const val EVENTO_UID_BLOQUEADO = "shizuku_bootstrap_uid_blocked"
    const val EVENTO_BINDER_MORTO_NA_PARTIDA_MORNA = "shizuku_stale_binder_on_already_running_start"

    /** Preferência crua com o caminho da `libshizuku.so` em cache (§B4); só deste módulo. */
    const val CHAVE_LOCAL_DA_LIB = "shizuku_lib_location"

    /**
     * Pacote do fork renomeado do Shizuku no carro (só no `pm path` do §B5 e na rede do §H).
     * Id próprio desde a Fase 4 (build "OPEN GW Acesso", `third_party/open-gw-acesso/`).
     */
    const val PACOTE_DO_FORK_DO_SHIZUKU = "br.com.opengw.shizuku"

    // ── §B5 / V4: o base.apk deduzido do caminho da lib ──

    private val formaDaLib = Regex("^(/.+)/lib/[^/]+/libshizuku\\.so$")

    /**
     * `<dir do pacote>/lib/<abi>/libshizuku.so` → `<dir do pacote>/base.apk`. Nulo quando o caminho
     * não tem essa forma (quem chama cai no `pm path` e, em último caso, sobe sem `--apk=`).
     */
    @JvmStatic
    fun deduzirBaseApk(caminhoDaLib: String?): String? {
        val caminho = caminhoDaLib?.trim() ?: return null
        val achado = formaDaLib.matchEntire(caminho) ?: return null
        return achado.groupValues[1] + "/base.apk"
    }

    // ── §B8: o desfecho do start ──

    /** Os três desfechos; [codigo] é o inteiro que `BootDiagnostics.onBootstrapStartOutcome` recebe. */
    enum class Desfecho(val codigo: Int) {
        BINDER_DE_PE(0),
        VIVO_SEM_BINDER(1),
        SERVIDOR_MORTO(2),
    }

    /**
     * Classifica o start a partir do que se viu até agora.
     *
     * @param binder `pingBinder` respondeu.
     * @param pids as leituras de `pidof`, em ordem (`null` = não deu para saber).
     * @param noPrazo o prazo de [PRAZO_DO_DESFECHO_MS] acabou.
     * @return o desfecho, ou nulo se ainda não dá para decidir (continuar olhando).
     *
     * Sem nenhuma leitura de pid conclusiva no prazo, o servidor é tratado como vivo: na dúvida não
     * se derruba nada (D5) e a escada do §C decide.
     */
    @JvmStatic
    fun classificarDesfecho(binder: Boolean, pids: List<Boolean?>, noPrazo: Boolean): Desfecho? {
        if (binder) return Desfecho.BINDER_DE_PE
        val conhecidos = pids.filterNotNull()
        val apareceuESumiu = conhecidos.indexOf(true).let { i -> i >= 0 && conhecidos.drop(i + 1).contains(false) }
        if (apareceuESumiu) return Desfecho.SERVIDOR_MORTO
        if (!noPrazo) return null
        return when (conhecidos.lastOrNull()) {
            false -> Desfecho.SERVIDOR_MORTO
            else -> Desfecho.VIVO_SEM_BINDER
        }
    }

    /** O que fazer depois do desfecho: só o servidor morto é re-tentado já (D4/D5). */
    enum class DepoisDoDesfecho { ESPERAR_BINDER, TENTAR_DE_NOVO }

    @JvmStatic
    fun depoisDoDesfecho(desfecho: Desfecho): DepoisDoDesfecho = when (desfecho) {
        Desfecho.BINDER_DE_PE, Desfecho.VIVO_SEM_BINDER -> DepoisDoDesfecho.ESPERAR_BINDER
        Desfecho.SERVIDOR_MORTO -> DepoisDoDesfecho.TENTAR_DE_NOVO
    }

    // ── §C2 / V5: a escada de timeouts ──

    enum class AcaoNoTimeout { NADA, RESUBIR, REINICIAR }

    /** [contador] já incrementado: 1 e 2 re-sobem, 3 (ou mais) pede reinício, 0 nada. */
    @JvmStatic
    fun decidirNoTimeout(contador: Int): AcaoNoTimeout = when {
        contador <= 0 -> AcaoNoTimeout.NADA
        contador < TIMEOUTS_ATE_REINICIAR -> AcaoNoTimeout.RESUBIR
        else -> AcaoNoTimeout.REINICIAR
    }

    // ── §E / V5: binder morto na partida morna ──

    /**
     * `conferir()` só age quando o estado em memória mente: aceito, binder morto e a subida
     * disponível (um ciclo iniciado, não encerrado, sem reinício já pedido).
     */
    @JvmStatic
    fun deveReSubirNaPartidaMorna(aceitoEmMemoria: Boolean, binderVivo: Boolean, subidaDisponivel: Boolean): Boolean =
        aceitoEmMemoria && !binderVivo && subidaDisponivel

    // ── §A2: conferência de integridade da instalação ──

    /**
     * @property gravarIntegridade valor a gravar em `selfInstallationIntegrityCheck`; nulo = não gravar.
     * @property bloqueado UID acima do teto (registra `shizuku_bootstrap_uid_blocked`).
     * @property desistir parar aqui com `desistir("uid")` (bloqueado e sem bypass).
     */
    data class ConferenciaDaInstalacao(
        val gravarIntegridade: Boolean?,
        val bloqueado: Boolean,
        val desistir: Boolean,
        val mensagem: String?,
    )

    @JvmStatic
    fun conferirInstalacao(uid: Int, bypass: Boolean, integridadeGravada: Boolean): ConferenciaDaInstalacao {
        if (uid > TETO_DE_UID) {
            return ConferenciaDaInstalacao(
                gravarIntegridade = false,
                bloqueado = true,
                desistir = !bypass,
                mensagem = "UID do app $uid acima do teto $TETO_DE_UID; bypass=${if (bypass) "ligado" else "desligado"}",
            )
        }
        return ConferenciaDaInstalacao(
            gravarIntegridade = if (integridadeGravada) null else true,
            bloqueado = false,
            desistir = false,
            mensagem = null,
        )
    }

    // ── §H: rede de segurança na partida (só a decisão) ──

    enum class AcaoDaRede { ENCERRAR, PULAR, AGIR }

    /**
     * Antes de cada tentativa da rede: binder respondendo → encerrar (D9, obrigatório); tentativas
     * esgotadas → encerrar; uma subida já em andamento (do caminho principal) → pular esta vez (D5);
     * senão agir.
     *
     * @param tentativa 1 para a primeira.
     */
    @JvmStatic
    fun decidirRede(tentativa: Int, binderRespondendo: Boolean, subidaEmAndamento: Boolean): AcaoDaRede = when {
        binderRespondendo -> AcaoDaRede.ENCERRAR
        tentativa > REDE_TENTATIVAS -> AcaoDaRede.ENCERRAR
        subidaEmAndamento -> AcaoDaRede.PULAR
        else -> AcaoDaRede.AGIR
    }
}
