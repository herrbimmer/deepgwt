package br.com.opengw.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** `telas-e-servico-servico.md` casos B2–B4. */
class NucleoDoReceptorTest {

    private class Portas(val emCurso: Boolean = false, val lancarNoGancho: Boolean = false) : PortasDoReceptor {
        val chamadas = mutableListOf<String>()
        override fun marcarBootTravado() { chamadas += "diag.travado" }
        override fun marcarBootNoMotor() { chamadas += "motor.marcarBoot" }
        override fun marcarBootNaLinhaDoTempo(acao: String?) { chamadas += "diag.boot($acao)" }
        override fun pedirOServico() { chamadas += "iniciar" }
        override fun aoReceberPartida(acao: String?) {
            chamadas += "aoReceberPartida($acao)"
            if (lancarNoGancho) throw RuntimeException("x")
        }
        override fun atualizacaoEmCurso(): Boolean = emCurso
        override fun reabrirTelaDeAtualizacao() { chamadas += "reabrir" }
        override fun info(mensagem: String) {}
        override fun erro(mensagem: String, causa: Throwable?) { chamadas += "erro" }
    }

    @Test
    fun `boot travado so marca e para`() {
        val p = Portas()
        NucleoDoReceptor(p).aoReceber("android.intent.action.LOCKED_BOOT_COMPLETED")
        assertEquals(listOf("diag.travado"), p.chamadas)
    }

    @Test
    fun `boot completo marca, pede o servico e chama a ligacao`() {
        val p = Portas()
        NucleoDoReceptor(p).aoReceber("android.intent.action.BOOT_COMPLETED")
        assertEquals(
            listOf(
                "motor.marcarBoot", "diag.boot(android.intent.action.BOOT_COMPLETED)", "iniciar",
                "aoReceberPartida(android.intent.action.BOOT_COMPLETED)",
            ),
            p.chamadas,
        )
    }

    @Test
    fun `intent nulo segue como partida`() {
        val p = Portas()
        NucleoDoReceptor(p).aoReceber(null)
        assertEquals(listOf("motor.marcarBoot", "diag.boot(null)", "iniciar", "aoReceberPartida(null)"), p.chamadas)
    }

    @Test
    fun `pacote trocado com atualizacao em curso reabre a tela`() {
        val acao = "android.intent.action.MY_PACKAGE_REPLACED"
        val com = Portas(emCurso = true, lancarNoGancho = true)
        NucleoDoReceptor(com).aoReceber(acao)
        assertEquals("reabrir", com.chamadas.last())
        val sem = Portas(emCurso = false)
        NucleoDoReceptor(sem).aoReceber(acao)
        assertEquals("aoReceberPartida($acao)", sem.chamadas.last())
        val boot = Portas(emCurso = true)
        NucleoDoReceptor(boot).aoReceber("android.intent.action.BOOT_COMPLETED")
        assertEquals(false, boot.chamadas.contains("reabrir"))
    }
}
