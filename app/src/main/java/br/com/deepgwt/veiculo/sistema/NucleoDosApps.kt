// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.sistema

import br.com.deepgwt.nucleo.Preferencias
import br.com.deepgwt.veiculo.comandos.PreferenciasDoMotor
import br.com.deepgwt.veiculo.comandos.ShellDoMotor
import br.com.deepgwt.veiculo.motor.RegistroDoMotor

/**
 * Apps do sistema, debloat e escuta de voz (motor-sistema.md §B e §C). Síncrono (vários comandos de
 * shell): roda na thread de quem chama, que nunca é a principal (as fachadas enfileiram).
 */
internal class NucleoDosApps(
    private val shell: ShellDoMotor,
    private val prefs: PreferenciasDoMotor,
    private val portao: () -> PortaoDeLicenca,
    private val registro: RegistroDoMotor,
) {

    /** B.1: reativa o dono das telas. Nunca desativá-lo (fatos §3). */
    fun garantirAppsDoSistema() {
        reativar(MULTIDISPLAY)
    }

    /**
     * B.2: semeia as preferências nunca gravadas a partir do estado real, aplica cada interruptor e,
     * por fim, a escuta de voz. Sem Shizuku não faz nada: a leitura vazia semearia "desativado" à toa.
     */
    fun garantirAppsDesativados() {
        if (!shellDisponivel()) {
            registro.aviso("debloat: Shizuku indisponível; nada semeado nem aplicado")
            return
        }
        val aSemear = INTERRUPTORES.filter { !prefs.contem(it.chave) }
        if (aSemear.isNotEmpty()) {
            // "Desativado" só se o pacote existe na lista completa e não está na dos habilitados.
            // Uma consulta que volta vazia é o shell falhando, não "tudo desativado": semear assim
            // desligaria a navegação, a voz e o clima da fábrica sem a pessoa ter pedido.
            val instalados = pacotesInstalados()
            if (instalados == null) {
                registro.aviso("debloat: a lista de pacotes veio vazia; nada semeado")
            } else {
                for (interruptor in aSemear) {
                    val desativado = interruptor.sinalizador in instalados && !habilitado(interruptor.sinalizador)
                    prefs.gravarBooleano(interruptor.chave, desativado)
                    registro.info("debloat: ${interruptor.chave} semeado = $desativado")
                }
            }
        }
        val permite = permiteSistema()
        for (interruptor in INTERRUPTORES) {
            val desativar = prefs.booleano(interruptor.chave, false) && permite
            for (pacote in interruptor.pacotes) {
                try {
                    if (desativar) desativar(pacote) else reativar(pacote)
                } catch (e: Exception) {
                    registro.erro("debloat: $pacote falhou", e)
                }
            }
        }
        try {
            aplicarEscuta()
        } catch (e: Exception) {
            registro.erro("debloat: escuta de voz falhou", e)
        }
    }

    /** Os pacotes da lista completa (habilitados ou não); `null` se a consulta não devolveu nada. */
    private fun pacotesInstalados(): Set<String>? {
        val saida = try {
            shell.executar(arrayOf("pm", "list", "packages"))
        } catch (e: Exception) {
            return null
        }
        val pacotes = saida.lineSequence().map { it.trim() }.filter { it.startsWith("package:") }
            .map { it.removePrefix("package:") }.toSet()
        return pacotes.takeIf { it.isNotEmpty() }
    }

    /** B.5: uma linha exatamente `package:<pkg>`; exceção → habilitado (conservador). */
    fun habilitado(pacote: String): Boolean {
        val saida = try {
            shell.executar(arrayOf("pm", "list", "packages", "-e", pacote))
        } catch (e: Exception) {
            return true
        }
        return saida.lineSequence().any { it.trim() == "package:$pacote" }
    }

    /** B.3. */
    private fun desativar(pacote: String) {
        registro.info("debloat: desativando $pacote")
        shell.executar(arrayOf("pm", "disable-user", "--user", "0", pacote))
        shell.executar(arrayOf("pkill", "-9", "-f", pacote))
    }

    /** B.4: cobre os dois jeitos de ter sido desligado. */
    private fun reativar(pacote: String) {
        shell.executar(arrayOf("pm", "install-existing", pacote))
        shell.executar(arrayOf("pm", "enable", pacote))
    }

    /** §C: devolve o relatório (C.4). */
    fun aplicarEscuta(): String {
        if (!prefs.contem(Preferencias.DISABLE_VOICE_WAKE_UP.chave)) return NUNCA_TOCADO
        val silenciar = prefs.booleano(Preferencias.DISABLE_VOICE_WAKE_UP.chave, false) && permiteSistema()
        val modo = if (silenciar) "ignore" else "allow"
        val linhas = ArrayList<String>()
        var obedeceram = 0
        for (pacote in PACOTES_DE_VOZ) {
            val curto = pacote.substringAfterLast('.')
            try {
                rodar("cmd appops set $pacote RECORD_AUDIO $modo")
                rodar("pm ${if (silenciar) "revoke" else "grant"} $pacote android.permission.RECORD_AUDIO")
                if (silenciar) rodar("am force-stop $pacote")
                val resposta = rodar("cmd appops get $pacote RECORD_AUDIO").trim()
                val negado = resposta.contains("ignore") || resposta.contains("deny")
                val obedeceu = if (silenciar) negado else !negado
                if (obedeceu) obedeceram++
                val situacao = resposta.ifEmpty { "sem resposta do appops" }
                linhas += "$curto: $situacao" + if (obedeceu) "" else "  <- NÃO obedeceu"
            } catch (e: Exception) {
                linhas += "$curto: falhou (${e.message})"
            }
        }
        val resumo = "Escuta ${if (silenciar) "desligada" else "religada"} em $obedeceram de ${PACOTES_DE_VOZ.size} pacotes."
        registro.info(resumo)
        return (resumo + "\n" + linhas.joinToString("\n")).trimEnd()
    }

    /** C.3: `sh -c "<comando> 2>&1"`, para o erro voltar junto. */
    private fun rodar(comando: String): String {
        val saida = shell.executar(arrayOf("sh", "-c", "$comando 2>&1"))
        registro.info("escuta: $comando → $saida")
        return saida
    }

    private fun permiteSistema(): Boolean =
        try {
            portao().permiteSistema()
        } catch (e: Exception) {
            registro.aviso("portão de licença falhou; vale como negado", e)
            false
        }

    private fun shellDisponivel(): Boolean =
        try {
            shell.disponivel()
        } catch (e: Exception) {
            false
        }

    /** Um interruptor do debloat: a preferência, o pacote que semeia e os pacotes que mexe. */
    private class Interruptor(val chave: String, val sinalizador: String, val pacotes: List<String>)

    companion object {
        const val MULTIDISPLAY = "com.beantechs.multidisplay"
        const val NAVEGACAO = "com.neusoft.na.navigation"
        const val VOZ_TELA = "com.iflytek.cutefly.speechclient.hmi"
        const val VOZ_CLIENTE = "com.beantechs.voiceclient"
        const val VOZ_ADAPTADOR = "com.beantechs.voice.adapter"
        const val VOZ_IMPRESSAO = "com.beantechs.voiceprintservice"
        const val CLIMA = "com.beantechs.weatherservice"

        /** Os quatro que seguram o microfone, na ordem do relatório. */
        val PACOTES_DE_VOZ = listOf(VOZ_TELA, VOZ_CLIENTE, VOZ_ADAPTADOR, VOZ_IMPRESSAO)

        const val NUNCA_TOCADO = "Interruptor nunca tocado — nada foi alterado."

        private val INTERRUPTORES = listOf(
            Interruptor(Preferencias.DISABLE_NATIVE_NAVIGATION.chave, NAVEGACAO, listOf(NAVEGACAO)),
            Interruptor(Preferencias.DISABLE_NATIVE_VOICE.chave, VOZ_TELA, listOf(VOZ_TELA, VOZ_CLIENTE)),
            Interruptor(Preferencias.DISABLE_NATIVE_WEATHER.chave, CLIMA, listOf(CLIMA)),
        )
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
