package br.com.deepgwt.tela3d.janelas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** I1: o `dumpsys` (V4) e a cadeia de leituras de visibilidade. */
class LeituraDeVisibilidadeTest {

    private val enchimento = "x".repeat(50)

    // --- V4 ---

    @Test fun pacoteComVisibleTrueEUm() {
        val texto = "...\n    taskId=12: com.waze/com.waze.FreeMapAppActivity bounds=[0,52][1920,660] userId=10 visible=true topActivity=ComponentInfo{com.waze/com.waze.FreeMapAppActivity}"
        assertEquals(Visibilidade.VISIVEL, LeitorDeDumpsys.visibilidade(texto, "com.waze"))
    }

    @Test fun semVisibleTrueNaJanelaEZero() {
        val texto = "$enchimento\n    taskId=12: com.waze/com.waze.FreeMapAppActivity bounds=[0,52][1920,660] userId=10 visible=false " + "y".repeat(700) + " visible=true"
        assertEquals(Visibilidade.SUMIU, LeitorDeDumpsys.visibilidade(texto, "com.waze"))
    }

    @Test fun textoCurtoEDesconhecido() {
        assertEquals(Visibilidade.DESCONHECIDA, LeitorDeDumpsys.visibilidade("a".repeat(30), "com.waze"))
    }

    @Test fun negacaoEDesconhecido() {
        val texto = "Permission Denial: can't dump ActivityManager from pid=1, uid=10 com.waze visible=true $enchimento"
        assertEquals(Visibilidade.DESCONHECIDA, LeitorDeDumpsys.visibilidade(texto, "com.waze"))
        assertTrue(LeitorDeDumpsys.foiNegado(texto))
    }

    @Test fun pacoteQueNaoApareceEDesconhecido() {
        assertEquals(Visibilidade.DESCONHECIDA, LeitorDeDumpsys.visibilidade("$enchimento com.outro/Main visible=true", "com.waze"))
    }

    @Test fun duasOcorrenciasSoASegundaPertoDeVisibleTrue() {
        val primeira = "taskId=1: com.waze/.Main visible=false"
        val longe = "z".repeat(1500)
        val segunda = "taskId=2: com.waze/.Outra x visible=true"
        assertEquals(Visibilidade.VISIVEL, LeitorDeDumpsys.visibilidade("$enchimento $primeira $longe $segunda", "com.waze"))
    }

    @Test fun pacoteVazioESumiu() {
        assertEquals(Visibilidade.SUMIU, LeitorDeDumpsys.visibilidade("a".repeat(100), ""))
    }

    @Test fun pacoteMaisLongoNaoCasaComOPrefixo() {
        val texto = "$enchimento taskId=3: com.foo.bar/.Main visible=true"
        assertEquals(Visibilidade.DESCONHECIDA, LeitorDeDumpsys.visibilidade(texto, "com.foo"))
        assertEquals(Visibilidade.VISIVEL, LeitorDeDumpsys.visibilidade(texto, "com.foo.bar"))
    }

    @Test fun pacoteMaisCurtoNaoCasaPorTerminarIgual() {
        val texto = "$enchimento taskId=3: xcom.foo/.Main visible=true"
        assertEquals(Visibilidade.DESCONHECIDA, LeitorDeDumpsys.visibilidade(texto, "com.foo"))
    }

    @Test fun pontoFinalDeFraseNaoContinuaOPacote() {
        val texto = "$enchimento abriu com.foo. visible=true"
        assertEquals(Visibilidade.VISIVEL, LeitorDeDumpsys.visibilidade(texto, "com.foo"))
    }

    @Test fun chaveEEspacoLimitamOPacote() {
        val texto = "$enchimento topActivity=ComponentInfo{com.foo/com.foo.Main} visible=true"
        assertEquals(Visibilidade.VISIVEL, LeitorDeDumpsys.visibilidade(texto, "com.foo"))
    }

    // --- memória de negação ---

    @Test fun memoriaDeNegacaoPulaPorCincoMinutos() {
        val tempo = TempoFalso()
        val m = MemoriaDeNegacao(tempo)
        assertTrue(m.deveTentar())
        m.registrarNegado()
        assertFalse(m.deveTentar())
        tempo.avancar(MemoriaDeNegacao.DURACAO_PADRAO_MS - 1)
        assertFalse(m.deveTentar())
        tempo.avancar(1)
        assertTrue(m.deveTentar())
    }

    // --- a cadeia I1 ---

    private fun leitor(
        dumpsys: Int = Visibilidade.DESCONHECIDA,
        servico: List<RegistroDeTarefa>? = null,
        processos: List<RegistroDeProcesso>? = null,
        publicas: List<RegistroDeTarefa>? = null,
        passos: MutableList<String> = ArrayList(),
        falhaNoPasso: Int = 0,
    ) = LeitorDeVisibilidade(
        pacoteProprio = "br.com.deepgwt",
        dumpsys = { passos += "dumpsys"; if (falhaNoPasso == 1) error("x"); dumpsys },
        listaDoServico = { passos += "servico"; if (falhaNoPasso == 2) error("x"); servico },
        processos = { passos += "processos"; if (falhaNoPasso == 3) error("x"); processos },
        tarefasPublicas = { passos += "publicas"; publicas },
    )

    @Test fun pacoteVazioESumiuSemConsultar() {
        val passos = ArrayList<String>()
        assertEquals(Visibilidade.SUMIU, leitor(passos = passos).ler("", -1))
        assertTrue(passos.isEmpty())
    }

    @Test fun oDumpsysVenceQuandoSabe() {
        val passos = ArrayList<String>()
        assertEquals(Visibilidade.VISIVEL, leitor(dumpsys = 1, passos = passos).ler("com.waze", -1))
        assertEquals(listOf("dumpsys"), passos)
        assertEquals(Visibilidade.SUMIU, leitor(dumpsys = 0).ler("com.waze", -1))
    }

    @Test fun listaDoServicoUsaAVisibilidadeDaTarefa() {
        val servico = listOf(RegistroDeTarefa(5, "com.waze", null, visivel = false))
        assertEquals(Visibilidade.SUMIU, leitor(servico = servico).ler("com.waze", -1))
        val visivel = listOf(RegistroDeTarefa(5, null, "com.waze", visivel = true))
        assertEquals(Visibilidade.VISIVEL, leitor(servico = visivel).ler("com.waze", -1))
    }

    @Test fun listaSemOPacoteMasComOutroAppESumiu() {
        val servico = listOf(RegistroDeTarefa(5, "com.outro", null))
        assertEquals(Visibilidade.SUMIU, leitor(servico = servico).ler("com.waze", -1))
    }

    @Test fun listaSoComOProprioAppNaoDizNada() {
        val servico = listOf(RegistroDeTarefa(5, "br.com.deepgwt", null))
        val passos = ArrayList<String>()
        assertEquals(Visibilidade.DESCONHECIDA, leitor(servico = servico, passos = passos).ler("com.waze", -1))
        assertEquals(listOf("dumpsys", "servico", "processos", "publicas"), passos)
    }

    @Test fun processoComImportanciaAte200EVisivel() {
        val vivo = listOf(RegistroDeProcesso(listOf("com.waze"), 200))
        assertEquals(Visibilidade.VISIVEL, leitor(processos = vivo).ler("com.waze", -1))
        val fundo = listOf(RegistroDeProcesso(listOf("com.waze"), 300))
        assertEquals(Visibilidade.SUMIU, leitor(processos = fundo).ler("com.waze", -1))
    }

    @Test fun processosSemOPacoteMasComOutroAppESumiu() {
        val lista = listOf(RegistroDeProcesso(listOf("com.outro"), 100))
        assertEquals(Visibilidade.SUMIU, leitor(processos = lista).ler("com.waze", -1))
    }

    @Test fun tarefasPublicasCasamTambemPeloNumeroGuardado() {
        val lista = listOf(RegistroDeTarefa(9, "com.diferente", null, visivel = true))
        assertEquals(Visibilidade.VISIVEL, leitor(publicas = lista).ler("com.waze", 9))
        assertEquals(Visibilidade.SUMIU, leitor(publicas = lista).ler("com.waze", 4))
    }

    @Test fun excecaoNumPassoSegueAoProximo() {
        val vivo = listOf(RegistroDeProcesso(listOf("com.waze"), 100))
        assertEquals(Visibilidade.VISIVEL, leitor(processos = vivo, falhaNoPasso = 1).ler("com.waze", -1))
        assertEquals(Visibilidade.VISIVEL, leitor(processos = vivo, falhaNoPasso = 2).ler("com.waze", -1))
    }

    @Test fun nadaLegivelEDesconhecido() {
        assertEquals(Visibilidade.DESCONHECIDA, leitor().ler("com.waze", -1))
    }
}
