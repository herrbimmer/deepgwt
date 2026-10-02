package br.com.opengw.rede

import java.time.ZoneId
import java.util.concurrent.Executor

/** O que mede o tráfego móvel: o contador do sistema e, de reserva, o `TrafficStats`. */
internal interface MedidorDeUso {
    /**
     * Soma recebido+enviado do tráfego móvel entre [inicioMs] e [fimMs] segundo o
     * `NetworkStatsManager`. Null = não deu (serviço ausente, resumo nulo). Pode lançar.
     */
    fun resumoDoSistema(inicioMs: Long, fimMs: Long): Long?

    /** `TrafficStats.getMobileRxBytes()`; negativo = não suportado. */
    fun bytesRecebidos(): Long

    /** `TrafficStats.getMobileTxBytes()`; negativo = não suportado. */
    fun bytesEnviados(): Long
}

/**
 * Controle dos dados móveis (4G) do carro e bloqueio da telemetria da fabricante, sem Android
 * (especificação, seções A e B). [fundo] tem de ser uma thread única: as avaliações de corte e as
 * da telemetria rodam nela, uma por vez.
 */
internal class NucleoDosDadosMoveis(
    private val armazem: ArmazemDeChaves,
    private val shell: ExecutorDeShell,
    private val registro: RegistroDaRede,
    private val relogio: RelogioDaRede,
    private val fuso: () -> ZoneId,
    private val medidor: MedidorDeUso,
    private val wifiConectadoAgora: () -> Boolean,
    private val projecaoAtivaAgora: () -> Boolean,
    private val pacoteDoApp: () -> String,
    private val fundo: Executor,
) {

    @Volatile
    private var ouvinteDoMestre: ((Boolean) -> Unit)? = null

    // ---- interruptores ----

    fun controleLigado(): Boolean = armazem.lerBoolean(ChavesDaRede.CONTROLE_LIGADO, false)

    fun definirControleLigado(ligado: Boolean) {
        armazem.gravarBoolean(ChavesDaRede.CONTROLE_LIGADO, ligado)
        try {
            ouvinteDoMestre?.invoke(ligado)
        } catch (e: Exception) {
            registro.erro("o ouvinte do controle de dados móveis lançou exceção", e)
        }
        garantirPermissaoDeUso()
        reavaliar()
    }

    fun corteManual(): Boolean = armazem.lerBoolean(ChavesDaRede.CORTE_MANUAL, false)

    fun definirCorteManual(ligado: Boolean) = definirRegra(ChavesDaRede.CORTE_MANUAL, ligado)

    fun corteSobConsumo(): Boolean = armazem.lerBoolean(ChavesDaRede.CORTE_SOB_CONSUMO, false)

    fun definirCorteSobConsumo(ligado: Boolean) = definirRegra(ChavesDaRede.CORTE_SOB_CONSUMO, ligado)

    fun corteNoWifi(): Boolean = armazem.lerBoolean(ChavesDaRede.CORTE_NO_WIFI, false)

    fun definirCorteNoWifi(ligado: Boolean) = definirRegra(ChavesDaRede.CORTE_NO_WIFI, ligado)

    fun corteNaProjecao(): Boolean = armazem.lerBoolean(ChavesDaRede.CORTE_NA_PROJECAO, false)

    fun definirCorteNaProjecao(ligado: Boolean) = definirRegra(ChavesDaRede.CORTE_NA_PROJECAO, ligado)

    private fun definirRegra(chave: String, ligado: Boolean) {
        armazem.gravarBoolean(chave, ligado)
        garantirPermissaoDeUso()
        reavaliar()
    }

    /** Quem quiser saber do mestre. Chamado por [definirControleLigado]. */
    fun aoMudarControle(ouvinte: ((Boolean) -> Unit)?) {
        ouvinteDoMestre = ouvinte
    }

    // ---- ciclo e teto ----

    fun diaDoCiclo(): Int =
        armazem.lerInt(ChavesDaRede.DIA_DO_CICLO, ChavesDaRede.DIA_DO_CICLO_PADRAO).coerceIn(1, 31)

    fun tetoDeConsumoMb(): Int =
        armazem.lerInt(ChavesDaRede.TETO_DE_CONSUMO_MB, ChavesDaRede.TETO_PADRAO_MB).coerceAtLeast(0)

    /** Grava o dia de renovação (limitado a 1..31) e reavalia: o uso do ciclo muda de base. */
    fun definirDiaDoCiclo(dia: Int) {
        armazem.gravarVarios(mapOf(ChavesDaRede.DIA_DO_CICLO to dia.coerceIn(1, 31)))
        reavaliar()
    }

    /** Grava o teto de consumo em MB (mínimo 0) e reavalia. */
    fun definirTetoDeConsumoMb(mb: Int) {
        armazem.gravarVarios(mapOf(ChavesDaRede.TETO_DE_CONSUMO_MB to mb.coerceAtLeast(0)))
        reavaliar()
    }

    fun inicioDoCiclo(agoraMs: Long): Long = CicloDeCobranca.inicioEmMs(agoraMs, diaDoCiclo(), fuso())

    fun etiquetaDoCiclo(agoraMs: Long): String = CicloDeCobranca.etiqueta(agoraMs, diaDoCiclo(), fuso())

    // ---- estado de execução ----

    fun wifiConectado(): Boolean =
        try {
            wifiConectadoAgora()
        } catch (e: Exception) {
            false
        }

    fun projecaoAtiva(): Boolean =
        try {
            projecaoAtivaAgora()
        } catch (e: Exception) {
            false
        }

    /** Null = não está cortando. */
    fun motivoDoCorte(): MotivoDoCorte? = RegrasDeCorte.decidirMotivo(entradas)

    /** As entradas das regras: cada uma lê só o que precisa, quando precisa. */
    private val entradas = object : EntradasDoCorte {
        override fun mestre() = controleLigado()
        override fun manual() = corteManual()
        override fun consumoLigado() = corteSobConsumo()
        override fun usoNoCicloMb() = usoNoCicloMb(relogio.agoraEmMs())
        override fun tetoDeConsumoMb() = this@NucleoDosDadosMoveis.tetoDeConsumoMb()
        override fun wifiLigado() = corteNoWifi()
        override fun wifiConectado() = this@NucleoDosDadosMoveis.wifiConectado()
        override fun projecaoLigada() = corteNaProjecao()
        override fun projecaoAtiva() = this@NucleoDosDadosMoveis.projecaoAtiva()
    }

    // ---- uso ----

    private val travaDoUso = Any()
    private val reserva = ContadorDeReserva(armazem)

    /** Primeiro o contador do sistema; sem resposta válida, o de reserva. Uma chamada por vez. */
    fun usoNoCicloBytes(agoraMs: Long): Long = synchronized(travaDoUso) {
        val dia = diaDoCiclo()
        val zona = fuso()
        val doSistema = try {
            medidor.resumoDoSistema(CicloDeCobranca.inicioEmMs(agoraMs, dia, zona), agoraMs)
        } catch (e: Exception) {
            registro.aviso("NetworkStats indisponível: ${e.message}")
            null
        }
        if (doSistema != null && doSistema >= 0) return doSistema
        reserva.atualizar(CicloDeCobranca.etiqueta(agoraMs, dia, zona), medidor.bytesRecebidos(), medidor.bytesEnviados())
    }

    fun usoNoCicloMb(agoraMs: Long): Long = usoNoCicloBytes(agoraMs) / BYTES_POR_MB

    // ---- avaliação (thread de fundo, fundida) ----

    private val trava = Any()
    private var agendada = false
    private var emCurso = false
    private var repetir = false

    // Os dois últimos aplicados que este processo conhece.
    private var ultimoDoCorte = UltimoAplicado.NENHUM
    private var ultimoDaTelemetria = UltimoAplicado.NENHUM

    /**
     * Volta na hora. Pedidos feitos enquanto uma avaliação roda ou está agendada são fundidos:
     * termina a em curso e roda mais uma, nunca duas ao mesmo tempo.
     */
    fun reavaliar() {
        synchronized(trava) {
            when {
                agendada -> return
                emCurso -> {
                    repetir = true
                    return
                }
                else -> agendada = true
            }
        }
        fundo.execute { laco() }
    }

    private fun laco() {
        while (true) {
            synchronized(trava) {
                agendada = false
                emCurso = true
                repetir = false
            }
            try {
                avaliarCorte()
            } catch (e: Throwable) {
                registro.erro("a avaliação do corte de dados móveis falhou", e)
            }
            synchronized(trava) {
                emCurso = false
                if (!repetir) return
                agendada = true
            }
        }
    }

    private fun avaliarCorte() {
        val motivo = motivoDoCorte()
        val cortadoAntes = armazem.lerBoolean(ChavesDaRede.CORTADO_PELO_APP, false)
        val acao = RegrasDeCorte.decidirAcao(
            desejaCortar = motivo != null,
            reafirmar = true,
            cortadoPeloApp = cortadoAntes,
            ultimoAplicado = ultimoDoCorte,
        )
        if (acao == AcaoDeCorte.NADA) return
        val cortar = acao == AcaoDeCorte.DESATIVAR
        val deu = synchronized(travaDeAplicacao) { rodarCorte(cortar) }
        if (!deu) {
            registro.erro("dados móveis: não consegui ${if (cortar) "cortar" else "religar"} o 4G; tento na próxima avaliação")
            return
        }
        val novo = if (cortar) UltimoAplicado.CORTADO else UltimoAplicado.LIBERADO
        val mudou = ultimoDoCorte != novo || cortadoAntes != cortar
        armazem.gravarBoolean(ChavesDaRede.CORTADO_PELO_APP, cortar)
        ultimoDoCorte = novo
        if (mudou) {
            registro.aviso(
                if (cortar) "dados móveis cortados (${motivo?.rotulo})" else "dados móveis liberados",
            )
        }
    }

    /** Serializa a aplicação do corte e a da telemetria entre si. */
    private val travaDeAplicacao = Any()

    /**
     * Um único shell: o comando do `svc`; se falhar, falha na hora; senão confere
     * `settings get global mobile_data` até 5 vezes, 1 s entre as leituras.
     */
    private fun rodarCorte(cortar: Boolean): Boolean {
        val alvo = if (cortar) "0" else "1"
        val comando = if (cortar) "svc data disable" else "svc data enable"
        val roteiro = "$comando || exit 1; " +
            "i=1; while [ \"\$i\" -le $LEITURAS ]; do " +
            "v=\$(settings get global mobile_data); " +
            "if [ \"\$v\" = \"$alvo\" ]; then echo $MARCADOR_OK; exit 0; fi; " +
            "if [ \"\$i\" -lt $LEITURAS ]; then sleep 1; fi; " +
            "i=\$((i + 1)); done; exit 1"
        return temMarcador(shell.roteiro(roteiro))
    }

    // ---- permissão de uso ----

    /**
     * Só age com o mestre e a regra de consumo ligados. Em segundo plano (na mesma fila das
     * avaliações, antes delas), três comandos independentes; falha só vai para o log.
     */
    fun garantirPermissaoDeUso() {
        if (!controleLigado() || !corteSobConsumo()) return
        val pacote = pacoteDoApp()
        fundo.execute {
            try {
                concederPermissoes(pacote)
            } catch (e: Throwable) {
                registro.erro("não deu para conceder a permissão de uso de rede", e)
            }
        }
    }

    private fun concederPermissoes(pacote: String) {
        val comandos = listOf(
            "appops set $pacote GET_USAGE_STATS allow",
            "pm grant $pacote android.permission.PACKAGE_USAGE_STATS",
            "pm grant $pacote android.permission.READ_NETWORK_USAGE_HISTORY",
        )
        for (comando in comandos) {
            if (!temMarcador(shell.roteiro("$comando && echo $MARCADOR_OK"))) {
                registro.aviso("permissão de uso: falhou `$comando`")
            }
        }
    }

    // ---- telemetria da fabricante ----

    fun telemetriaBloqueada(): Boolean = armazem.lerBoolean(ChavesDaRede.TELEMETRIA_BLOQUEADA, false)

    fun definirTelemetriaBloqueada(bloquear: Boolean) {
        armazem.gravarBoolean(ChavesDaRede.TELEMETRIA_BLOQUEADA, bloquear)
        reaplicarTelemetria()
    }

    /** Só aplica, em segundo plano, na mesma thread das avaliações de corte. */
    fun reaplicarTelemetria() {
        fundo.execute {
            try {
                avaliarTelemetria()
            } catch (e: Throwable) {
                registro.erro("a aplicação do bloqueio da telemetria falhou", e)
            }
        }
    }

    private fun avaliarTelemetria() {
        val deseja = telemetriaBloqueada()
        val congeladoAntes = armazem.lerBoolean(ChavesDaRede.TELEMETRIA_CONGELADA_PELO_APP, false)
        val acao = RegrasDeCorte.decidirAcao(
            desejaCortar = deseja,
            reafirmar = false,
            cortadoPeloApp = congeladoAntes,
            ultimoAplicado = ultimoDaTelemetria,
        )
        if (acao == AcaoDeCorte.NADA) return
        val congelar = acao == AcaoDeCorte.DESATIVAR
        val deu = synchronized(travaDeAplicacao) { rodarTelemetria(congelar) }
        if (!deu) {
            registro.erro("telemetria: não consegui ${if (congelar) "congelar" else "descongelar"} $PACOTE_DA_TELEMETRIA")
            return
        }
        val novo = if (congelar) UltimoAplicado.CORTADO else UltimoAplicado.LIBERADO
        val mudou = ultimoDaTelemetria != novo || congeladoAntes != congelar
        armazem.gravarBoolean(ChavesDaRede.TELEMETRIA_CONGELADA_PELO_APP, congelar)
        ultimoDaTelemetria = novo
        if (mudou) {
            registro.aviso(if (congelar) "telemetria da fabricante bloqueada" else "telemetria da fabricante liberada")
        }
    }

    /**
     * Um único shell. Desativar: `pm disable-user`, `am force-stop`, e confere que a linha exata
     * do pacote está em `pm list packages -d`. Ativar: `pm enable` e confere que não está.
     */
    private fun rodarTelemetria(congelar: Boolean): Boolean {
        val linha = "package:$PACOTE_DA_TELEMETRIA"
        val confere = "l=\$(pm list packages -d) || exit 1; " +
            "if printf '%s\\n' \"\$l\" | grep -Fxq '$linha'; then achou=1; else achou=0; fi; "
        val roteiro = if (congelar) {
            "pm disable-user --user 0 $PACOTE_DA_TELEMETRIA; am force-stop $PACOTE_DA_TELEMETRIA; " +
                confere + "[ \"\$achou\" = 1 ] && echo $MARCADOR_OK"
        } else {
            "pm enable $PACOTE_DA_TELEMETRIA; " + confere + "[ \"\$achou\" = 0 ] && echo $MARCADOR_OK"
        }
        return temMarcador(shell.roteiro(roteiro))
    }

    internal companion object {
        const val LEITURAS = 5
        const val BYTES_POR_MB = 1_048_576L
        const val PACOTE_DA_TELEMETRIA = "com.beantechs.datatrackservice"
    }
}
