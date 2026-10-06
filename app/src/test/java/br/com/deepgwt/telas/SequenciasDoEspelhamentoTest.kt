// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.telas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parte 5, 4.1: sequências da parte 3 (Android Auto, CarPlay, guardas, vigia e partida). */
class SequenciasDoEspelhamentoTest {

    private val aaAtividade = "com.ts.androidauto.app.display.AapActivity"
    private val cpAtividade = "com.ts.carplay.app.ui.display.view.CarPlayDisplayActivity"
    private val abrirCpNo3 =
        "am start --display 3 --windowingMode 5 --activity-multiple-task -f 0x18000000 -n com.ts.carplay.app/$cpAtividade"
    private val servicosDoAa = listOf(
        "am startservice --user 0 -a com.ts.androidauto.action.AndroidAutoService -n com.ts.androidauto.projectionservice/.AndroidAutoService",
        "am startservice --user 0 -a com.ts.androidauto.action.AndroidAutoRemoteUiService -n com.ts.androidauto.app/.AndroidAutoRemoteUiService",
    )
    private val servicosDoCp = listOf(
        "setprop persist.haval.carplay.video.height 720",
        "am startservice -n com.ts.carplay/.CarPlayService",
        "am startservice -n com.ts.carplay.app/.service.CarPlayRemoteService",
    )

    private fun focoDoAa(d: Int) = listOf(
        "am broadcast -a ts.car.androidauto.view_state --es state foreground --ei displayId $d",
        "am broadcast -a com.ts.androidauto.action.AndroidAutoService --es \"command\" \"requestVideoFocus\" --ei \"displayId\" $d",
    )

    private fun destinoDoCp(b: Bancada) = b.prefs.valores[ChavesDoLancador.DESTINO_DO_CARPLAY.chave]

    // ── 3-C4 ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `C4 - AA ja no alvo so foca`() {
        val b = Bancada()
        b.central.pilha(3, "com.ts.androidauto.app", aaAtividade)
        b.rodar { b.esp.abrirAndroidAuto(b.esp.configDoAndroidAutoPara(3, null), "teste") }
        assertEquals(servicosDoAa + focoDoAa(3), b.central.acoes())
        assertEquals(3, b.prefs.valores[ChavesDoLancador.DESTINO_DO_ANDROID_AUTO.chave])
        assertTrue(b.canalAa.pedidos > 0)
        assertEquals(1, b.barra.reaberturas)
        assertTrue("confere a superfície no painel", b.central.comandos.any { it.startsWith("dumpsys SurfaceFlinger") })
    }

    @Test
    fun `C4 - AA em outro display move a pilha`() {
        val b = Bancada()
        val p = b.central.pilha(0, "com.ts.androidauto.app", aaAtividade)
        b.rodar { b.esp.abrirAndroidAuto(b.esp.configDoAndroidAutoPara(3, null), "teste") }
        assertEquals(servicosDoAa + "am display move-stack ${p.id} 3" + focoDoAa(3), b.central.acoes())
        assertEquals(listOf(48L, 700L, 160L), b.relogio.esperas)
        b.fundo.rodarTudo { it.startsWith("avisar") }
        assertTrue("focos em segundo plano", b.relogio.esperas.containsAll(listOf(500L, 900L, 1200L)))
    }

    @Test
    fun `C4 - AA em pilha mista traz a outra tarefa e abre a atividade`() {
        val b = Bancada()
        b.central.pilha(0, "com.ts.androidauto.app", aaAtividade, outras = listOf("com.waze"))
        b.rodar { b.esp.abrirAndroidAuto(b.esp.configDoAndroidAutoPara(3, null), "teste") }
        val acoes = b.central.acoes()
        assertEquals("am start -n com.waze/.Main --display 0 -f 0x14000000", acoes[2])
        assertEquals("am start --display 3 --windowingMode 5 --activity-multiple-task -f 0x18000000 -n com.ts.androidauto.app/$aaAtividade", acoes[3])
        assertTrue(b.relogio.esperas.containsAll(listOf(220L, 700L)))
    }

    @Test
    fun `C4 - AA que nao aparece para so o app visual e tenta de novo`() {
        val b = Bancada()
        b.central.naoAbre["com.ts.androidauto.app"] = "Starting: Intent { cmp=com.ts.androidauto.app/x }"
        b.rodar { b.esp.abrirAndroidAuto(b.esp.configDoAndroidAutoPara(3, null), "teste") }
        val acoes = b.central.acoes()
        assertTrue(acoes.contains("am force-stop com.ts.androidauto.app"))
        assertTrue(acoes.none { it == "am force-stop com.ts.androidauto" || it == "am force-stop com.ts.androidauto.projectionservice" })
        assertEquals(2, acoes.count { it.startsWith("am start ") })
        assertTrue(b.relogio.esperas.containsAll(listOf(700L, 650L, 900L)))
        assertTrue(b.registro.tem("AA não apareceu"))
    }

    @Test
    fun `C4 - AA no display 0 em tela cheia`() {
        val b = Bancada()
        b.rodar { b.esp.abrirAndroidAuto(b.esp.configDoAndroidAutoPara(0, null), "teste") }
        val acoes = b.central.acoes()
        assertEquals("am start -n com.ts.androidauto.app/$aaAtividade --display 0 --windowingMode 1 -f 0x14000000", acoes[2])
        assertTrue(acoes[3].startsWith("am stack set-windowing-mode "))
        assertTrue(b.barra.restaurados.contains("+com.ts.androidauto.app"))
    }

    @Test
    fun `C6 - superficie velha do AA reinicia o visual no maximo a cada 5 s`() {
        val b = Bancada()
        val p = b.central.pilha(3, "com.ts.androidauto.app", aaAtividade)
        b.central.surfaceFlinger = "+ BufferLayer (${MotorDoEspelhamento.CAMADA_DO_ANDROID_AUTO}#0)\n  activeBuffer=[   0x   0:   0,Unknown/None]\n"
        val tarefa = TarefaNaTela(p.tarefas[0].id, p.id, 3, p.limites)
        assertTrue(b.rodar { b.esp.superficieVelhaDoAndroidAuto(tarefa) })
        assertTrue(b.central.acoes().contains("am force-stop com.ts.androidauto.app"))
        b.zerarRegistros()
        b.relogio.andar(1_300)
        assertTrue("passou da checagem, mas o reinício espera 5 s", b.rodar { b.esp.superficieVelhaDoAndroidAuto(tarefa) })
        assertTrue(b.central.acoes().isEmpty())
        b.relogio.andar(100)
        assertFalse("sondagem ≥ 1,2 s", b.rodar { b.esp.superficieVelhaDoAndroidAuto(tarefa) })
    }

    // ── 3-C8 ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `C8 - limpeza do AA sem conexao depois de 30 s e uma a cada 10 s`() {
        val b = Bancada()
        val p = b.central.pilha(0, "com.ts.androidauto.app", aaAtividade)
        b.rodar { b.esp.limparAndroidAutoSemConexao("teste") }
        val leituras = b.central.comandos.count { it.startsWith("cat /sys/class/android_usb") }
        b.relogio.andar(5_000)
        b.rodar { b.esp.limparAndroidAutoSemConexao("teste") }
        assertEquals("menos de 10 s: nada", leituras, b.central.comandos.count { it.startsWith("cat /sys/class/android_usb") })
        b.relogio.andar(5_000)
        b.rodar { b.esp.limparAndroidAutoSemConexao("teste") }
        assertTrue(b.central.acoes().none { it.startsWith("am stack remove") })
        b.relogio.andar(20_000)
        b.rodar { b.esp.limparAndroidAutoSemConexao("teste") }
        assertEquals(listOf("am stack remove ${p.id}"), b.central.acoes())
        assertEquals(1, b.sistema.reposicionamentos)
    }

    @Test
    fun `C8 - DCM com AA ativo nao limpa e vira evidencia da sessao`() {
        val b = Bancada()
        b.central.pilha(0, "com.ts.androidauto.app", aaAtividade)
        b.dcm = LeituraDoDcm(true, "aa sem fio")
        b.rodar { b.esp.limparAndroidAutoSemConexao("teste") }
        assertTrue(b.midia.nucleoDaSessao())
        b.relogio.andar(181_000)
        assertFalse(b.midia.nucleoDaSessao())
    }

    // ── 3-K: CarPlay ────────────────────────────────────────────────────────────────────────────

    private val cpNo3 = ConfigDoApp("com.ts.carplay.app", "x", 3, 0, 0, 1920, 720)

    @Test
    fun `K3 e K5 - ida ao painel pela pilha viva do 0`() {
        val b = Bancada()
        val p = b.central.pilha(0, "com.ts.carplay.app", cpAtividade)
        b.rodar { b.esp.pedidoDeAbrirCarPlay(cpNo3, "teste") }
        assertEquals(
            listOf("setprop persist.haval.carplay.desired_display 3") + servicosDoCp + "am display move-stack ${p.id} 3",
            b.central.acoes(),
        )
        assertEquals(listOf(260L, 700L, 650L), b.relogio.esperas)
        assertEquals(EstadoDoCarPlay.NO_PAINEL, b.esp.estadoDoCarPlay)
        assertFalse(b.esp.carPlayPreparandoOPainel())
        assertEquals(3, destinoDoCp(b))
    }

    @Test
    fun `K4 e K5 - pilha mista no 0 nao e movida, abre a atividade no painel`() {
        val b = Bancada()
        b.central.pilha(0, "com.ts.carplay.app", cpAtividade, outras = listOf("com.beantechs.mediacenter"))
        b.rodar { b.esp.pedidoDeAbrirCarPlay(cpNo3, "teste") }
        val acoes = b.central.acoes()
        assertTrue(acoes.contains("am start -n com.beantechs.mediacenter/.Main --display 0 -f 0x14000000"))
        assertTrue(acoes.contains(abrirCpNo3))
        assertTrue(acoes.none { it.startsWith("am display move-stack") || it.startsWith("am stack remove") })
        assertEquals(1, b.canalCp.modosNormais)
        assertEquals(EstadoDoCarPlay.NO_PAINEL, b.esp.estadoDoCarPlay)
    }

    @Test
    fun `K2 e K4 - volta do painel para a principal pela pilha`() {
        val b = Bancada()
        val p = b.central.pilha(3, "com.ts.carplay.app", cpAtividade)
        b.rodar { b.esp.carPlayNaPrincipal(null, "teste", gravarDestino = true) }
        assertEquals(
            listOf("setprop persist.haval.carplay.desired_display 0") + servicosDoCp + "am display move-stack ${p.id} 0",
            b.central.acoes(),
        )
        assertEquals(EstadoDoCarPlay.NA_PRINCIPAL, b.esp.estadoDoCarPlay)
        assertEquals(1, b.sistema.reposicionamentos)

        b.zerarRegistros()
        b.rodar { b.esp.carPlayNaPrincipal(null, "de novo", gravarDestino = true) }
        assertEquals("idempotente", listOf("setprop persist.haval.carplay.desired_display 0"), b.central.acoes())
    }

    @Test
    fun `K4 - reuso da instancia sem saida mantem a pilha e vai para recuperacao`() {
        val b = Bancada()
        b.central.pilha(1, "com.ts.carplay.app", cpAtividade)
        b.central.naoAbre["com.ts.carplay.app"] = "Warning: Activity not started, its current task has been brought to the front"
        b.rodar { b.esp.pedidoDeAbrirCarPlay(cpNo3.copy(display = 1), "teste") }
        assertTrue("D12: display 1 vira 3", b.central.acoes().contains(abrirCpNo3))
        assertTrue(b.central.acoes().none { it.startsWith("am stack remove") })
        assertTrue(b.registro.tem("CarPlay preso no display 1"))
        assertEquals(EstadoDoCarPlay.EM_RECUPERACAO, b.esp.estadoDoCarPlay)
    }

    @Test
    fun `K6 - sem tarefa no display confere a saude e recria so com problema de imagem`() {
        val b = Bancada()
        b.central.naoAbre["com.ts.carplay.app"] = "Starting: Intent { cmp=x }"
        val agora = b.relogio.paredeMs / 1000.0
        val linha = String.format(java.util.Locale.ROOT, "%.3f 1 2 3 W NdkMediaCodec: invalid bufidx-1", agora + 2.0)
        b.central.logcat = List(6) { linha }.joinToString("\n")
        b.rodar { b.esp.carPlayNaPrincipal(null, "teste", gravarDestino = false) }
        val abrirNo0 = "am start -f 0x14000000 -n com.ts.carplay.app/$cpAtividade"
        assertEquals("abre, confere a saúde e recria uma vez", 2, b.central.acoes().count { it == abrirNo0 })
        assertTrue("sem broadcast de refresh render (só havia efeito no CarPlay modificado)", b.central.acoes().none { it.contains("REFRESH_RENDER") })
        assertTrue("nunca reinicia o serviço do CarPlay", b.central.acoes().none { it.startsWith("am force-stop com.ts.carplay") })
    }

    // ── 3-F: guardas do CarPlay ─────────────────────────────────────────────────────────────────

    @Test
    fun `F2 - guarda publica no maximo a cada 3,5 s e so com o destino no painel`() {
        val b = Bancada()
        b.esp.guardarCarPlayNoPainel("x")
        assertTrue("sem destino no painel: nada", b.fundo.nomes.isEmpty())
        b.prefs[ChavesDoLancador.DESTINO_DO_CARPLAY] = 3
        b.esp.guardarCarPlayNoPainel("x")
        b.esp.guardarCarPlayNoPainel("y")
        assertEquals(1, b.fundo.nomes.size)
        b.relogio.andar(3_500)
        b.esp.guardarCarPlayNoPainel("z")
        assertEquals(2, b.fundo.nomes.size)
    }

    @Test
    fun `F3 - guarda por janela com intervalo unico de 8 s (D17)`() {
        val b = Bancada()
        b.prefs[ChavesDoLancador.DESTINO_DO_CARPLAY] = 3
        b.esp.guardaDoCarPlayPorJanela("com.android.systemui")
        assertTrue(b.fundo.nomes.isEmpty())
        b.esp.guardaDoCarPlayPorJanela("com.android.settings")
        b.esp.guardaDoCarPlayPorJanela("com.waze")
        assertEquals(1, b.fundo.nomes.size)
        b.relogio.andar(8_000)
        b.esp.guardaDoCarPlayPorJanela("com.waze")
        assertEquals(2, b.fundo.nomes.size)
    }

    @Test
    fun `F5 - devolve do 0 ao painel abrindo a atividade quando a pilha e mista`() {
        val b = Bancada()
        val p = b.central.pilha(0, "com.ts.carplay.app", cpAtividade, outras = listOf("com.beantechs.mediacenter"))
        val t = TarefaNaTela(p.tarefas[0].id, p.id, 0, p.limites)
        b.rodar { b.esp.devolverDo0ParaOPainel(t) }
        val acoes = b.central.acoes()
        val desfoco = acoes.indexOf("am start -n com.beantechs.mediacenter/.Main --display 0 --windowingMode 1 -f 0x14000000")
        val abertura = acoes.indexOf(abrirCpNo3)
        assertTrue(desfoco >= 0 && abertura > desfoco)
        assertTrue("a pilha mista do 0 é mantida", acoes.none { it.startsWith("am stack remove") })
        assertTrue(b.relogio.esperas.containsAll(listOf(300L, 900L, 650L)))
    }

    // ── 3-G: guardas do AA ──────────────────────────────────────────────────────────────────────

    @Test
    fun `G2 - guardas do AA so elegiveis e no maximo a cada 2,5 s`() {
        val b = Bancada()
        b.central.usb = "CONFIGURED"
        b.central.pilha(3, "com.ts.androidauto.app", aaAtividade)
        b.esp.guardarAndroidAutoNoPainel("x")
        b.fundo.rodarTudo()
        assertTrue("AA não quer o painel: nada", b.relogio.esperas.isEmpty())

        b.prefs[ChavesDoLancador.DESTINO_DO_ANDROID_AUTO] = 3
        b.esp.guardarAndroidAutoNoPainel("x")
        b.esp.guardarAndroidAutoNoPainelAposJanelaNativa("y")
        b.fundo.rodarTudo()
        assertEquals(listOf(450L, 950L), b.relogio.esperas)
        assertTrue(b.registro.tem("guarda do AA ignorada (y)"))
    }

    @Test
    fun `G3 - janela do AA fora do painel volta ao painel`() {
        val b = Bancada()
        b.prefs[ChavesDoLancador.DESTINO_DO_ANDROID_AUTO] = 3
        val p = b.central.pilha(0, "com.ts.androidauto.app", aaAtividade)
        b.rodar { b.esp.guardaDoAndroidAutoPorJanela("com.ts.androidauto.app") }
        assertEquals(250L, b.relogio.esperas.first())
        assertTrue(b.central.acoes().contains("am display move-stack ${p.id} 3"))
    }

    @Test
    fun `G3 - janela de outro app pede foco de video do AA no painel`() {
        val b = Bancada()
        b.prefs[ChavesDoLancador.DESTINO_DO_ANDROID_AUTO] = 3
        b.central.usb = "CONFIGURED"
        b.central.pilha(3, "com.ts.androidauto.app", aaAtividade)
        b.rodar { b.esp.guardaDoAndroidAutoPorJanela("com.beantechs.hvac") }
        assertEquals(listOf(500L, 1200L, 3500L, 4000L), b.relogio.esperas)
        assertEquals(4, b.central.acoes().count { it == focoDoAa(3)[1] })
    }

    // ── 3-H1: vigia ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `H1 - reconcilia o destino pela tarefa real`() {
        val b = Bancada()
        b.prefs[ChavesDoLancador.DESTINO_DO_CARPLAY] = 0
        b.central.pilha(3, "com.ts.carplay.app", cpAtividade)
        b.rodar { b.esp.voltaDaVigia() }
        assertEquals(3, destinoDoCp(b))
        assertTrue(b.central.acoes().contains("setprop persist.haval.carplay.desired_display 3"))
    }

    @Test
    fun `H1 - sem CarPlay, sem tolerancia e sem visto limpa o destino velho`() {
        val b = Bancada()
        b.prefs[ChavesDoLancador.DESTINO_DO_CARPLAY] = 3
        b.rodar { b.esp.voltaDaVigia() }
        assertEquals(0, destinoDoCp(b))
        assertEquals(listOf("setprop persist.haval.carplay.desired_display 0"), b.central.acoes())
    }

    @Test
    fun `H1 - tolerancia de partida segura o destino por 65 s sem USB`() {
        val b = Bancada()
        b.prefs[ChavesDoLancador.DESTINO_DO_CARPLAY] = 3
        b.esp.comecoDaVigia()
        b.rodar { b.esp.voltaDaVigia() }
        assertEquals(3, destinoDoCp(b))
        b.relogio.andar(66_000)
        b.rodar { b.esp.voltaDaVigia() }
        assertEquals(0, destinoDoCp(b))
    }

    @Test
    fun `H1 - visto no painel ha pouco e USB de pe recria a janela no painel`() {
        val b = Bancada()
        b.prefs[ChavesDoLancador.DESTINO_DO_CARPLAY] = 3
        b.central.usb = "CONFIGURED"
        b.central.pilha(3, "com.ts.carplay.app", cpAtividade)
        b.rodar { b.esp.voltaDaVigia() }
        b.central.pilhas.clear()
        b.relogio.andar(5_000)
        b.zerarRegistros()
        b.rodar { b.esp.voltaDaVigia() }
        val acoes = b.central.acoes()
        assertTrue(acoes.contains(abrirCpNo3))
        assertTrue(acoes.any { it.startsWith("am stack resize ") })
        assertTrue(acoes.contains("am broadcast -a ts.car.carplay.view_state --es state foreground --ei displayId 3"))
        assertEquals(3, destinoDoCp(b))
    }

    @Test
    fun `H1 - duplicata no 0 em outra pilha e removida`() {
        val b = Bancada()
        b.prefs[ChavesDoLancador.DESTINO_DO_CARPLAY] = 3
        b.central.pilha(3, "com.ts.carplay.app", cpAtividade)
        val no0 = b.central.pilha(0, "com.ts.carplay.app", cpAtividade)
        b.rodar { b.esp.voltaDaVigia() }
        assertEquals(listOf("am stack remove ${no0.id}"), b.central.acoes())
    }

    @Test
    fun `H1 - so no 0 sem quarentena devolve ao painel`() {
        val b = Bancada()
        b.prefs[ChavesDoLancador.DESTINO_DO_CARPLAY] = 3
        b.central.usb = "CONFIGURED"
        val p = b.central.pilha(0, "com.ts.carplay.app", cpAtividade)
        b.rodar { b.esp.voltaDaVigia() }
        assertTrue(b.central.acoes().contains("am display move-stack ${p.id} 3"))
        b.zerarRegistros()
        b.central.pilhaDe("com.ts.carplay.app")!!.display = 0
        b.relogio.andar(1_000)
        b.rodar { b.esp.voltaDaVigia() }
        assertTrue("restauração no máximo a cada 3,5 s", b.central.acoes().none { it.startsWith("am display move-stack") })
    }

    @Test
    fun `H1 - depois de reconectar o cabo o CarPlay no 0 e respeitado (quarentena)`() {
        val b = Bancada()
        b.prefs[ChavesDoLancador.DESTINO_DO_CARPLAY] = 3
        b.central.usb = "CONFIGURED"
        b.central.pilha(3, "com.ts.carplay.app", cpAtividade)
        b.rodar { b.esp.voltaDaVigia() }

        b.central.pilhas.clear()
        b.central.usb = ""
        b.relogio.andar(2_000)
        b.rodar { b.esp.voltaDaVigia() }
        assertEquals("instável: mantém o destino", 3, destinoDoCp(b))

        b.central.pilha(0, "com.ts.carplay.app", cpAtividade)
        b.central.usb = "CONFIGURED"
        b.relogio.andar(2_000)
        b.zerarRegistros()
        b.rodar { b.esp.voltaDaVigia() }
        assertTrue(b.central.acoes().none { it.startsWith("am display move-stack") })
        assertTrue("F7: protege o cartão do ar", b.esp.protegerCartaoDoArNaTransicao())
        b.relogio.andar(5 * 60_000L + 1)
        assertFalse(b.esp.protegerCartaoDoArNaTransicao())
    }

    // ── 3-H3: partida ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `H3 - token do boot ja gravado nao faz nada`() {
        val b = Bancada()
        b.prefs[ChavesDoLancador.TOKEN_DA_PARTIDA_DO_CARPLAY] = b.central.bootId
        b.rodar { b.esp.partidaDoCarPlay() }
        assertEquals(emptyList<String>(), b.central.acoes())
    }

    @Test
    fun `H3 - AA ativo pelo DCM grava o token e nao mexe no CarPlay`() {
        val b = Bancada()
        b.dcm = LeituraDoDcm(true, "")
        b.rodar { b.esp.partidaDoCarPlay() }
        assertEquals(b.central.bootId, b.prefs.valores[ChavesDoLancador.TOKEN_DA_PARTIDA_DO_CARPLAY.chave])
        assertEquals(emptyList<String>(), b.central.acoes())
    }

    @Test
    fun `H3 - CarPlay ja no 0 grava o destino 0, ou so o setprop se o destino e o painel`() {
        val b = Bancada()
        b.central.pilha(0, "com.ts.carplay.app", cpAtividade)
        b.rodar { b.esp.partidaDoCarPlay() }
        assertEquals(0, destinoDoCp(b))
        assertEquals(b.central.bootId, b.prefs.valores[ChavesDoLancador.TOKEN_DA_PARTIDA_DO_CARPLAY.chave])

        val c = Bancada()
        c.prefs[ChavesDoLancador.DESTINO_DO_CARPLAY] = 3
        c.central.pilha(0, "com.ts.carplay.app", cpAtividade)
        c.rodar { c.esp.partidaDoCarPlay() }
        assertEquals(3, destinoDoCp(c))
        assertEquals(listOf("setprop persist.haval.carplay.desired_display 3"), c.central.acoes())
    }

    @Test
    fun `H3 - com USB e o CarPlay no painel, abre na principal sem gravar o destino`() {
        val b = Bancada()
        b.prefs[ChavesDoLancador.DESTINO_DO_CARPLAY] = 3
        b.central.usb = "CONFIGURED"
        val p = b.central.pilha(3, "com.ts.carplay.app", cpAtividade)
        b.rodar { b.esp.partidaDoCarPlay() }
        assertTrue(b.central.acoes().contains("am display move-stack ${p.id} 0"))
        assertEquals("destino do painel mantido", 3, destinoDoCp(b))
        assertEquals(b.central.bootId, b.prefs.valores[ChavesDoLancador.TOKEN_DA_PARTIDA_DO_CARPLAY.chave])
    }

    @Test
    fun `H3 - sem USB tenta 30 vezes a cada 2 s e nao grava o token`() {
        val b = Bancada()
        b.rodar { b.esp.partidaDoCarPlay() }
        assertEquals(List(30) { 2_000L }, b.relogio.esperas)
        assertFalse(b.prefs.valores.containsKey(ChavesDoLancador.TOKEN_DA_PARTIDA_DO_CARPLAY.chave))
        assertTrue(b.registro.tem("30 tentativas"))
    }

    @Test
    fun `C9 - abertura automatica respeita a opcao de mover a projecao`() {
        val b = Bancada()
        val aa = PacotesDaCentral.predefinida("com.ts.androidauto.app")!!
        assertEquals(3, b.esp.configDeAberturaAutomatica(aa).display)
        b.prefs[br.com.deepgwt.nucleo.Preferencias.AUTO_MOVE_PROJECTION_TO_CLUSTER] = false
        assertEquals(0, b.esp.configDeAberturaAutomatica(aa).display)
        assertEquals("não grava", 0, b.prefs.escritas)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
