// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.rede

/**
 * Firewall liberado (especificação, seção D): a central vem com regras que barram tráfego; o app
 * garante ACCEPT incondicional como PRIMEIRA regra de INPUT e de OUTPUT (IPv4), para o instalador
 * do computador alcançar o servidor do app na porta 8766 e para os apps do usuário saírem.
 * Precisa de Shizuku como root.
 */
object FirewallDoCarro {

    private val garantidor by lazy { GarantidorDoFirewall(ExecutorDoModulo, RegistroDaRedeNoLogcat) }
    private val vigia by lazy { VigiaDoFirewall(ExecutorDoModulo, RegistroDaRedeNoLogcat) }

    /** Garante ACCEPT incondicional como PRIMEIRA regra de INPUT e de OUTPUT. Bloqueia. true = os dois. */
    @JvmStatic
    fun liberarEntradaESaida(): Boolean = garantidor.liberarEntradaESaida()

    /**
     * Vigia nativo: um script no aparelho que repete a garantia a cada 5 s (quem reinicia as regras
     * da central o faz de vez em quando). Idempotente. Bloqueia. true = de pé.
     */
    @JvmStatic
    fun iniciarVigia(): Boolean = vigia.iniciar()
}

internal object RegrasDoFirewall {

    /**
     * A primeira linha `-A <cadeia>` da saída de `iptables -S <cadeia>` é exatamente
     * `-A <cadeia> -j ACCEPT`? Um ACCEPT em outra posição, ou com condições, não vale.
     */
    fun primeiraRegraLiberaTudo(saidaDoIptablesS: String, cadeia: String): Boolean {
        val prefixo = "-A $cadeia"
        val primeira = saidaDoIptablesS.lineSequence()
            .map { it.trim() }
            .firstOrNull { it == prefixo || it.startsWith("$prefixo ") }
            ?: return false
        return primeira == "-A $cadeia -j ACCEPT"
    }
}

internal class GarantidorDoFirewall(
    private val shell: ExecutorDeShell,
    private val registro: RegistroDaRede,
) {

    /** As duas cadeias são sempre tentadas, mesmo que a primeira falhe. Nunca lança. */
    fun liberarEntradaESaida(): Boolean {
        val entrada = garantir("INPUT")
        val saida = garantir("OUTPUT")
        return entrada && saida
    }

    private fun garantir(cadeia: String): Boolean =
        try {
            val regras = shell.executar(arrayOf("iptables", "-S", cadeia))
            if (RegrasDoFirewall.primeiraRegraLiberaTudo(regras, cadeia)) {
                true
            } else if (temMarcador(shell.roteiro("iptables -I $cadeia 1 -j ACCEPT && echo $MARCADOR_OK"))) {
                registro.aviso("firewall: ACCEPT posto na primeira posição de $cadeia")
                true
            } else {
                registro.erro("firewall: não consegui pôr ACCEPT na primeira posição de $cadeia")
                false
            }
        } catch (e: Exception) {
            registro.erro("firewall: falhou em $cadeia", e)
            false
        }
}

/** O vigia nativo do firewall (especificação, caso 33). */
internal class VigiaDoFirewall(
    private val shell: ExecutorDeShell,
    private val registro: RegistroDaRede,
) {
    private val instalador = InstaladorDeArquivo(shell, registro)

    fun iniciar(): Boolean =
        try {
            val bytes = SCRIPT_DO_VIGIA.toByteArray(Charsets.UTF_8)
            if (!instalador.instalar(CAMINHO_DO_SCRIPT, bytes)) {
                false
            } else {
                // O próprio script termina os vigias antigos ao partir. O caminho é montado no
                // shell: a linha de comando deste `sh -c` não deve parecer um vigia.
                shell.roteiro(
                    "d=/data/local/tmp; setsid sh \$d/iptables_watchdog.sh > /dev/null 2>&1 < /dev/null &",
                )
                val dePe = estaDePe()
                if (dePe) registro.aviso("firewall: vigia de 5 s de pé") else registro.erro("firewall: o vigia não subiu")
                dePe
            }
        } catch (e: Exception) {
            registro.erro("firewall: falhou ao iniciar o vigia", e)
            false
        }

    /** Procura um processo cujo argv tenha o caminho do script. */
    private fun estaDePe(): Boolean {
        val roteiro = "sleep 1; b=/data/local; n=iptables_watchdog.sh; " +
            "for d in /proc/[0-9]*; do p=\${d##*/}; [ \"\$p\" = \"\$\$\" ] && continue; " +
            "if tr '\\0' ' ' 2>/dev/null < \"\$d/cmdline\" | grep -qF -- \" \$b/tmp/\$n \"; then echo $MARCADOR_OK; break; fi; done"
        return temMarcador(shell.roteiro(roteiro))
    }

    internal companion object {
        const val CAMINHO_DO_SCRIPT = "/data/local/tmp/iptables_watchdog.sh"

        /** `§` vira `$`: assim o texto do script não precisa de escapes no Kotlin. */
        val SCRIPT_DO_VIGIA: String = """
            #!/system/bin/sh
            # Vigia do firewall do DeepGWT: a cada 5 s garante ACCEPT incondicional como primeira
            # regra de INPUT e de OUTPUT (IPv4). Instalado pelo app em $CAMINHO_DO_SCRIPT.
            SELF=$CAMINHO_DO_SCRIPT

            # Termina os vigias antigos: processos com o caminho deste script na linha de comando.
            for d in /proc/[0-9]*; do
              p=§{d##*/}
              [ "§p" = "§§" ] && continue
              if tr '\0' ' ' 2>/dev/null < "§d/cmdline" | grep -qF -- " §SELF "; then
                kill "§p" 2>/dev/null
              fi
            done

            garantir() {
              primeira=§(iptables -S "§1" 2>/dev/null | awk -v c="§1" 'index(§0, "-A " c " ") == 1 { print; exit }')
              [ "§primeira" = "-A §1 -j ACCEPT" ] || iptables -I "§1" 1 -j ACCEPT 2>/dev/null
            }

            while :; do
              garantir INPUT
              garantir OUTPUT
              sleep 5
            done
        """.trimIndent().replace('§', '$') + "\n"
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
