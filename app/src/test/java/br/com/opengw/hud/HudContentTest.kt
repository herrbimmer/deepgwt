package br.com.opengw.hud

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O conteúdo do HUD: alertas, páginas e as teclas do volante.
 *
 * Nada aqui toca no aparelho. O que dá para verificar sem o carro é exatamente o que costuma sair
 * errado nesta parte: qual porta é qual dentro do vetor do carro, a diferença entre "tudo fechado" e
 * "o carro ainda não respondeu", que direcional anda para que lado, e que as cinco páginas pedidas
 * existem.
 */
class HudContentTest {

    @After
    fun voltarParaAPrimeiraPagina() {
        // Ordem, páginas ativas, cores e escalas são estado de objeto, não de instância: sem esta
        // volta ao padrão um teste que reordena decidiria o resultado do próximo a rodar.
        HudSettings.restaurarPaginas()
        HudSettings.ajustarControleVolante(true)
        // `restaurarPaginas` não mexe no peso de propósito (ele é global, como o SLI e os
        // alertas). Aqui a volta tem de ser explícita, senão um teste que afina a fonte e falha no
        // meio deixa o padrão errado para o próximo a rodar.
        HudSettings.ajustarPesoDaFonte(HudSettings.PESO_PADRAO)
        HudPaginas.reiniciar()
    }

    // ── Portas ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `cada indice do vetor de portas tem o nome certo`() {
        // Ordem medida neste projeto (a mesma que o launcher 3D usa para desenhar o carro aberto):
        // 0=DE, 1=DD, 2=TE, 3=TD, 4 não usado, 5=porta-malas.
        assertEquals(listOf("Dianteira esquerda"), HudTelemetry.portasAbertasEm("1,0,0,0,0,0"))
        assertEquals(listOf("Dianteira direita"), HudTelemetry.portasAbertasEm("0,1,0,0,0,0"))
        assertEquals(listOf("Traseira esquerda"), HudTelemetry.portasAbertasEm("0,0,1,0,0,0"))
        assertEquals(listOf("Traseira direita"), HudTelemetry.portasAbertasEm("0,0,0,1,0,0"))
        assertEquals(listOf("Porta-malas"), HudTelemetry.portasAbertasEm("0,0,0,0,0,1"))
    }

    @Test
    fun `o slot 4 nao vira alerta nenhum`() {
        // O vetor tem um slot a mais que as portas. Se ele virasse "porta aberta", o HUD avisaria
        // de uma porta que não existe.
        assertEquals(emptyList<String>(), HudTelemetry.portasAbertasEm("0,0,0,0,1,0"))
    }

    @Test
    fun `sem leitura do carro e diferente de tudo fechado`() {
        // null = o carro ainda não respondeu; lista vazia = respondeu e está tudo fechado. O HUD
        // não pode anunciar "tudo certo" com base em silêncio.
        assertNull(HudTelemetry.portasAbertasEm(""))
        assertNull(HudTelemetry.portasAbertasEm("   "))
        assertEquals(emptyList<String>(), HudTelemetry.portasAbertasEm("0,0,0,0,0,0"))
    }

    @Test
    fun `o vetor tambem pode vir separado por espaco ou ponto e virgula`() {
        assertEquals(listOf("Dianteira direita"), HudTelemetry.portasAbertasEm("0 1 0 0 0 0"))
        assertEquals(listOf("Porta-malas"), HudTelemetry.portasAbertasEm("0;0;0;0;0;2"))
    }

    // ── Alertas ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `uma porta aberta diz qual porta no detalhe`() {
        // O relatório pede identificar QUAL porta; o título é o aviso e o detalhe é o lugar.
        val alertas = HudTelemetry.montarAlertas(listOf("Traseira esquerda"), emptyList())
        assertEquals(1, alertas.size)
        assertEquals(HudTelemetry.Icone.PORTA, alertas[0].icone)
        assertEquals("Porta aberta", alertas[0].titulo)
        assertEquals("Traseira esquerda", alertas[0].detalhe)
    }

    @Test
    fun `o porta-malas sozinho tem desenho e texto proprios`() {
        // "Porta aberta" mandaria o motorista olhar as quatro portas por engano.
        val alertas = HudTelemetry.montarAlertas(listOf("Porta-malas"), emptyList())
        assertEquals(HudTelemetry.Icone.PORTA_MALAS, alertas[0].icone)
        assertEquals("Porta-malas aberto", alertas[0].titulo)
    }

    @Test
    fun `varias portas abertas viram um alerta so`() {
        // 480 px não comportam três avisos; a contagem no título deixa óbvio que é mais de uma sem
        // precisar ler a lista inteira.
        val alertas =
                HudTelemetry.montarAlertas(listOf("Dianteira esquerda", "Porta-malas"), emptyList())
        assertEquals(1, alertas.size)
        assertEquals("2 portas abertas", alertas[0].titulo)
        assertTrue(alertas[0].detalhe.contains("Porta-malas"))
        assertTrue(alertas[0].detalhe.contains("Dianteira esquerda"))
    }

    @Test
    fun `porta aberta vem antes das lampadas`() {
        val alertas =
                HudTelemetry.montarAlertas(
                        listOf("Dianteira esquerda"),
                        listOf(
                                HudTelemetry.Alerta(HudTelemetry.Icone.CINTO, "Cinto de segurança"),
                                HudTelemetry.Alerta(
                                        HudTelemetry.Icone.COMBUSTIVEL,
                                        "Combustível baixo"
                                )
                        )
                )
        assertEquals("Porta aberta", alertas[0].titulo)
        assertEquals(3, alertas.size)
    }

    @Test
    fun `carro fechado e sem lampada nao gera alerta`() {
        assertEquals(
                emptyList<HudTelemetry.Alerta>(),
                HudTelemetry.montarAlertas(emptyList(), emptyList())
        )
        // Sem leitura de porta, as lâmpadas ainda valem: uma coisa não depende da outra.
        val airbag = HudTelemetry.Alerta(HudTelemetry.Icone.AVISO, "Airbag")
        assertEquals(listOf(airbag), HudTelemetry.montarAlertas(null, listOf(airbag)))
    }

    // Alpha 9.4: portas são aviso (âmbar), não alerta grave.
    @Test
    fun `porta aberta e aviso, nao alerta grave`() {
        val alertas = HudTelemetry.montarAlertas(listOf("Traseira esquerda"), emptyList())
        assertFalse(alertas[0].grave)
        assertFalse(HudTelemetry.montarAlertas(listOf("Porta-malas"), emptyList())[0].grave)
    }

    // O vetor lido no carro com o motorista sem cinto (30/09): {1,0,0,0,0}.
    @Test
    fun `cinto diz qual lugar esta solto`() {
        assertEquals(listOf("Motorista"), HudTelemetry.lugaresSemCinto("{1,0,0,0,0}"))
        assertEquals(listOf("Motorista", "Passageiro"), HudTelemetry.lugaresSemCinto("{1,1,0,0,0}"))
        assertEquals(emptyList<String>(), HudTelemetry.lugaresSemCinto("{0,0,0,0,0}"))
        assertNull(HudTelemetry.lugaresSemCinto(""))

        val um = HudTelemetry.alertaDoCinto(listOf("Motorista"))
        assertEquals("Afivele o cinto", um.titulo)
        assertEquals("Motorista", um.detalhe)
        assertTrue(um.grave)
        assertEquals(HudTelemetry.Icone.CINTO, um.icone)

        val tres = HudTelemetry.alertaDoCinto(listOf("Motorista", "Passageiro", "Traseiro central"))
        assertEquals("Afivele os cintos", tres.titulo)
        assertEquals("Motorista · Passageiro · Tras. centro", tres.detalhe)

        // Lâmpada acesa sem o vetor: o aviso continua, só sem o lugar.
        assertEquals("", HudTelemetry.alertaDoCinto(null).detalhe)
    }

    @Test
    fun `cada desenho tem categoria`() {
        for (icone in HudTelemetry.Icone.entries) {
            assertTrue(HudTelemetry.categoriaDe(icone).isNotBlank())
        }
        assertEquals("Segurança", HudTelemetry.categoriaDe(HudTelemetry.Icone.CINTO))
    }

    // ── Teclas do volante ──────────────────────────────────────────────────────────────────

    @Test
    fun `cima volta uma pagina e baixo avanca`() {
        assertEquals(-1, HudWheelInput.passoDaTecla(HudWheelInput.VOLANTE_CIMA))
        assertEquals(1, HudWheelInput.passoDaTecla(HudWheelInput.VOLANTE_BAIXO))
    }

    @Test
    fun `esquerda e direita sao do carro, que troca de card com elas`() {
        // Alpha 6.1: consumir ←/→ nunca impediu o carro de trocar de card; com o HUD andando de
        // verdade, as duas coisas aconteceriam juntas a cada toque.
        assertNull(HudWheelInput.passoDaTecla(HudWheelInput.VOLANTE_ESQUERDA))
        assertNull(HudWheelInput.passoDaTecla(HudWheelInput.VOLANTE_DIREITA))
    }

    @Test
    fun `as outras teclas do volante nao sao do HUD`() {
        // ENTER, HOME, VOLTAR e as versões longas continuam inteiras para o cluster e o hands-free.
        // Consumi-las aqui tiraria do motorista funções que não têm nada a ver com o para-brisa.
        for (codigo in listOf(1028, 1029, 1030, 1033, 1034, 1037, 1039)) {
            assertNull("keycode $codigo não deveria mexer no HUD", HudWheelInput.passoDaTecla(codigo))
        }
    }

    // ── Páginas ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `sao cinco paginas e a primeira e a da velocidade`() {
        // Padrão, mídia, consumo, powertrain e mais uma útil — os cinco menus do relatório.
        assertEquals(5, HudPaginas.total)
        assertEquals(HudPaginas.Pagina.VELOCIDADE, HudPaginas.Pagina.entries.first())
        assertEquals(HudPaginas.Pagina.VELOCIDADE, HudPaginas.pagina)
    }

    @Test
    fun `as paginas dao a volta nas duas direcoes`() {
        HudPaginas.reiniciar()
        assertEquals(0, HudPaginas.indice)

        HudPaginas.anterior()
        assertEquals("da primeira para trás tem de cair na última", HudPaginas.total - 1, HudPaginas.indice)
        HudPaginas.proxima()
        assertEquals("e da última para frente, de volta à primeira", 0, HudPaginas.indice)
    }

    @Test
    fun `andar para frente e para tras devolve a mesma pagina`() {
        HudPaginas.reiniciar()
        HudPaginas.proxima()
        HudPaginas.proxima()
        val meio = HudPaginas.pagina
        assertNotEquals(HudPaginas.Pagina.VELOCIDADE, meio)
        HudPaginas.anterior()
        HudPaginas.anterior()
        assertEquals(HudPaginas.Pagina.VELOCIDADE, HudPaginas.pagina)
    }

    // ── Ordem, quais páginas entram e personalização ──────────────────────────────────────

    @Test
    fun `a ordem escolhida e a ordem que o volante anda`() {
        HudSettings.moverPagina(HudPaginas.Pagina.VEICULO, -4)
        assertEquals(
                "Veículo subiu quatro casas, então é a primeira",
                HudPaginas.Pagina.VEICULO,
                HudPaginas.visiveis.first()
        )
        HudPaginas.reiniciar()
        HudPaginas.proxima()
        assertEquals(HudPaginas.Pagina.VELOCIDADE, HudPaginas.pagina)
    }

    @Test
    fun `mover para fora da lista nao faz nada`() {
        val antes = HudPaginas.visiveis
        HudSettings.moverPagina(HudPaginas.Pagina.VELOCIDADE, -1)
        assertEquals("a primeira não sobe mais", antes, HudPaginas.visiveis)
        HudSettings.moverPagina(HudPaginas.Pagina.VEICULO, 1)
        assertEquals("nem a última desce", antes, HudPaginas.visiveis)
    }

    @Test
    fun `pagina desligada sai do rodizio do volante`() {
        HudSettings.ajustarPaginaAtiva(HudPaginas.Pagina.MIDIA, false)
        assertEquals(4, HudPaginas.total)
        assertFalse(HudPaginas.Pagina.MIDIA in HudPaginas.visiveis)

        HudPaginas.reiniciar()
        HudPaginas.proxima()
        assertEquals(
                "depois de Velocidade vem Consumo, porque Mídia saiu",
                HudPaginas.Pagina.CONSUMO,
                HudPaginas.pagina
        )
    }

    @Test
    fun `desligar a pagina que esta no ar cai para a primeira visivel`() {
        HudPaginas.irPara(HudPaginas.Pagina.CONSUMO)
        HudSettings.ajustarPaginaAtiva(HudPaginas.Pagina.CONSUMO, false)
        assertEquals(
                "o HUD não pode ficar mostrando uma página que o volante não alcança mais",
                HudPaginas.Pagina.VELOCIDADE,
                HudPaginas.pagina
        )
    }

    @Test
    fun `a ultima pagina ativa nao pode ser desligada`() {
        // Um HUD sem página nenhuma não teria o que desenhar nem como voltar pelo volante.
        HudPaginas.Pagina.entries.forEach { HudSettings.ajustarPaginaAtiva(it, false) }
        assertEquals(1, HudPaginas.total)
        assertFalse(HudSettings.podeDesativar(HudPaginas.visiveis.first()))
    }

    @Test
    fun `escolher pela central mostra ate uma pagina fora do rodizio`() {
        // O pedido foi explícito: recusar calado seria pior do que mostrar.
        HudSettings.ajustarPaginaAtiva(HudPaginas.Pagina.POWERTRAIN, false)
        HudPaginas.irPara(HudPaginas.Pagina.POWERTRAIN)
        assertEquals(HudPaginas.Pagina.POWERTRAIN, HudPaginas.pagina)
        // E o próximo passo do volante devolve o motorista para as visíveis.
        HudPaginas.proxima()
        assertTrue(HudPaginas.pagina in HudPaginas.visiveis)
    }

    @Test
    fun `a escala da pagina fica dentro dos limites e a velocidade nao tem uma`() {
        HudSettings.ajustarEscalaDaPagina(HudPaginas.Pagina.CONSUMO, 999)
        assertEquals(HudSettings.ESCALA_MAX, HudSettings.escalaDaPaginaPct(HudPaginas.Pagina.CONSUMO))
        HudSettings.ajustarEscalaDaPagina(HudPaginas.Pagina.CONSUMO, 0)
        assertEquals(HudSettings.ESCALA_MIN, HudSettings.escalaDaPaginaPct(HudPaginas.Pagina.CONSUMO))
        assertEquals(
                "sem ajuste, a página sai no tamanho do desenho original",
                HudSettings.ESCALA_PADRAO,
                HudSettings.escalaDaPaginaPct(HudPaginas.Pagina.VEICULO)
        )
    }

    @Test
    fun `a cor da velocidade e a cor da pagina de velocidade sao a mesma coisa`() {
        // Duas fontes para a mesma cor seria uma contradizendo a outra na tela.
        HudSettings.ajustarCorDaPagina(HudPaginas.Pagina.VELOCIDADE, HudSettings.PRESET_AZUL)
        assertEquals(HudSettings.PRESET_AZUL, HudSettings.speedColor)
        assertEquals(
                HudSettings.PRESET_AZUL,
                HudSettings.corDaPagina(HudPaginas.Pagina.VELOCIDADE)
        )
        HudSettings.ajustarCorVelocidade(HudSettings.PRESET_BRANCO)
    }

    @Test
    fun `a cor guardada por pagina sai sempre opaca`() {
        // O HUD é ótico: um valor translúcido simplesmente some no para-brisa.
        HudSettings.ajustarCorDaPagina(HudPaginas.Pagina.MIDIA, 0x00408040)
        val cor = HudSettings.corDaPagina(HudPaginas.Pagina.MIDIA)
        assertEquals(0xFF, (cor ushr 24) and 0xFF)
        assertEquals(0x408040, cor and 0xFFFFFF)
    }

    // ── Espessura da fonte ─────────────────────────────────────────────────────────────────

    @Test
    fun `o peso padrao e o negrito que o HUD ja desenhava`() {
        // O desenho pedia `Typeface.BOLD` fixo antes deste ajuste existir, e BOLD é peso 700.
        // Qualquer outro padrão mudaria o HUD de quem estava satisfeito ao atualizar o app.
        assertEquals(700, HudSettings.PESO_PADRAO)
        assertEquals(700, HudSettings.pesoDaFonte)
    }

    @Test
    fun `o peso e arredondado para o multiplo de cem mais proximo`() {
        // O controle deslizante entrega um Float contínuo, mas a família instalada só existe em
        // alguns pesos: 643 e 700 desenham igual, e um rótulo dizendo "643" mentiria.
        assertEquals(600, HudSettings.arredondarPeso(643))
        assertEquals(700, HudSettings.arredondarPeso(651))
        assertEquals(400, HudSettings.arredondarPeso(400))
    }

    @Test
    fun `o peso nunca sai da faixa que a fonte tem`() {
        // 200 no piso porque o HUD é ótico: um traço de peso 100 não chega ao olho com sol de
        // frente. 900 no teto porque é o mais grosso que existe na escala.
        assertEquals(HudSettings.PESO_MIN, HudSettings.arredondarPeso(0))
        assertEquals(HudSettings.PESO_MIN, HudSettings.arredondarPeso(-500))
        assertEquals(HudSettings.PESO_MAX, HudSettings.arredondarPeso(4000))
    }

    @Test
    fun `ajustar o peso guarda o valor arredondado e nao o cru`() {
        HudSettings.ajustarPesoDaFonte(337)
        assertEquals(300, HudSettings.pesoDaFonte)
        assertEquals("Leve", HudSettings.descreverPeso())
        HudSettings.ajustarPesoDaFonte(HudSettings.PESO_PADRAO)
        assertEquals("Negrito", HudSettings.descreverPeso())
    }

    @Test
    fun `a cortina de alerta e mais grossa do que o mais fino que da para escolher`() {
        // O alerta é o único conteúdo que o motorista não escolheu ver. Se ele afinasse junto com
        // o resto, daria para configurar o carro para sussurrar "porta aberta".
        assertTrue(HudSettings.PESO_DO_ALERTA > HudSettings.PESO_MIN)
        assertTrue(HudSettings.PESO_DO_ALERTA >= HudSettings.PESO_PADRAO)
    }

    // ── Miniaturas das páginas ─────────────────────────────────────────────────────────────

    @Test
    fun `a miniatura acende o ponto da propria pagina e nao o da que esta no ar`() {
        // A prévia desenha uma página escolhida; `indice` responde sempre sobre a que está no ar,
        // então usá-lo numa miniatura acenderia o ponto errado nas cinco.
        HudPaginas.reiniciar()
        assertEquals(0, HudPaginas.indiceDe(HudPaginas.Pagina.VELOCIDADE))
        assertEquals(2, HudPaginas.indiceDe(HudPaginas.Pagina.CONSUMO))
        assertEquals(HudPaginas.Pagina.VELOCIDADE, HudPaginas.pagina)
        assertEquals(0, HudPaginas.indice)
    }

    @Test
    fun `a miniatura de uma pagina desligada nao acende ponto nenhum`() {
        // -1 é informação, não falha: a página está fora do rodízio, e a fileira de pontos tem de
        // dizer isso em vez de acender um ponto qualquer.
        HudSettings.ajustarPaginaAtiva(HudPaginas.Pagina.VEICULO, false)
        assertEquals(-1, HudPaginas.indiceDe(HudPaginas.Pagina.VEICULO))
        assertTrue(HudPaginas.indiceDe(HudPaginas.Pagina.MIDIA) >= 0)
    }

    @Test
    fun `reordenar move o ponto da miniatura junto`() {
        // Os pontos desenham o rodízio, então a prévia de uma página tem de segui-lo quando a
        // ordem muda — senão a miniatura contradiz as setas que o usuário acabou de apertar.
        HudSettings.moverPagina(HudPaginas.Pagina.MIDIA, -1)
        assertEquals(0, HudPaginas.indiceDe(HudPaginas.Pagina.MIDIA))
        assertEquals(1, HudPaginas.indiceDe(HudPaginas.Pagina.VELOCIDADE))
    }

    @Test
    fun `reiniciar volta para a velocidade de qualquer pagina`() {
        // A camada sobe sempre na velocidade: acender o HUD mostrando mídia seria uma surpresa
        // ruim para quem está dirigindo.
        HudPaginas.proxima()
        HudPaginas.proxima()
        HudPaginas.reiniciar()
        assertEquals(HudPaginas.Pagina.VELOCIDADE, HudPaginas.pagina)
    }
}
