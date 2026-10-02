package br.com.opengw.ajustes

import br.com.opengw.nucleo.Preferencias
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O catálogo é o contrato com o instalador. O que se trava aqui é o que, errado, só apareceria
 * no computador de alguém: um ajuste "alterado" num carro que ninguém mexeu (padrão diferente da
 * tela), uma dependência que aponta para o nada (ajuste que nunca aparece), ou uma chave perigosa
 * exposta à rede.
 */
class CatalogoDeAjustesTest {

    private val todos = CatalogoDeAjustes.todos
    private val porId = todos.associateBy { it.id }

    @Test
    fun `ids sao unicos`() {
        val repetidos = todos.groupBy { it.id }.filterValues { it.size > 1 }.keys
        assertTrue("ids repetidos: $repetidos", repetidos.isEmpty())
    }

    @Test
    fun `toda secao tem ajuste`() {
        val vazias = SecaoDeAjustes.entries.filter { s -> todos.none { it.secao == s } }
        assertTrue("seções vazias: $vazias", vazias.isEmpty())
    }

    @Test
    fun `preferencia usa chave que existe no enum`() {
        val conhecidas = Preferencias.TODAS.map { it.chave }.toSet()
        todos.forEach { a ->
            val g = a.guardado
            if (g is Guardado.Preferencia) {
                assertTrue("${a.id}: chave fora do enum", g.chave in conhecidas)
                assertEquals("id de preferência é a própria chave", g.chave, a.id)
            }
            a.espelhos.forEach { assertTrue("${a.id}: espelho ${it.chave} fora do enum", it.chave in conhecidas) }
        }
    }

    @Test
    fun `nada perigoso ou da instalacao fica exposto`() {
        // As mesmas que o perfil de motorista recusa por perigo, mais o que é do aparelho.
        val proibidas =
                setOf(
                        Preferencias.STEALTH_MODE_ACTIVE.chave,
                        Preferencias.BYPASS_SELF_INSTALLATION_INTEGRITY_CHECK.chave,
                        Preferencias.PERMISSIONS_BOOTSTRAP_VERSION.chave,
                        Preferencias.ACTIVE_DRIVER_PROFILE.chave,
                        Preferencias.INSTRUMENT_MASK_DISPLAY_ID.chave,
                        Preferencias.HUD_OVERLAY_DISPLAY_ID.chave,
                        Preferencias.ADAS_DISCLAIMER_ACKNOWLEDGED.chave
                )
        val expostas = CatalogoDeAjustes.chavesObservadas intersect proibidas
        assertTrue("expostas: $expostas", expostas.isEmpty())
        assertTrue(todos.none { it.id.startsWith("driverProfile") })
    }

    @Test
    fun `padrao tem o tipo do controle e cabe na faixa`() {
        todos.forEach { a ->
            val p = a.padrao
            when (a.tipo) {
                TipoDeAjuste.INTERRUPTOR ->
                        assertTrue("${a.id}: padrão ${p}", p == null || p is Boolean)
                TipoDeAjuste.NUMERO -> {
                    assertTrue("${a.id}: padrão não numérico", p is Number)
                    val n = (p as Number).toDouble()
                    assertTrue("${a.id}: $n abaixo de ${a.min}", a.min == null || n >= a.min!!)
                    assertTrue("${a.id}: $n acima de ${a.max}", a.max == null || n <= a.max!!)
                    if (a.inteiro) assertTrue("${a.id}: inteiro com padrão decimal", p is Int)
                    // O controle deslizante encaixa o valor na grade min + k·passo. Um padrão fora
                    // dela pula para o vizinho no primeiro toque.
                    if (a.passo != null && a.min != null) {
                        val k = (n - a.min!!) / a.passo!!
                        assertTrue("${a.id}: padrão $n fora da grade de ${a.passo}", Math.abs(k - Math.round(k)) < 1e-6)
                    }
                }
                TipoDeAjuste.OPCOES -> {
                    assertTrue("${a.id}: sem opções", a.opcoes.isNotEmpty())
                    assertTrue("${a.id}: padrão \"$p\" fora das opções", a.opcoes.any { it.id == p })
                    assertEquals("${a.id}: opções repetidas", a.opcoes.size, a.opcoes.map { it.id }.toSet().size)
                }
                TipoDeAjuste.TEXTO -> assertTrue("${a.id}: padrão não é texto", p is String)
                TipoDeAjuste.COR ->
                        assertEquals("${a.id}: padrão não é #RRGGBB", p, ConversaoDeAjuste.corNormalizada(p as String))
                TipoDeAjuste.HORA -> {
                    assertEquals("${a.id}: padrão não é HH:MM", p, ConversaoDeAjuste.horaNormalizada(p as String))
                    assertTrue("${a.id}: horário sem a chave do minuto", a.espelhos.size == 1 && a.lerDe != null)
                }
            }
        }
    }

    @Test
    fun `dependencia aponta para ajuste que existe e para valor possivel`() {
        todos.forEach { a ->
            val c = a.visivelSe ?: return@forEach
            val alvo = porId[c.id]
            assertTrue("${a.id} depende de ${c.id}, que não existe", alvo != null)
            val possiveis =
                    when (alvo!!.tipo) {
                        TipoDeAjuste.INTERRUPTOR -> setOf("true", "false")
                        TipoDeAjuste.OPCOES -> alvo.opcoes.map { it.id }.toSet()
                        else -> null
                    }
            assertTrue("${a.id}: dependência de controle sem valores fixos", possiveis != null)
            assertTrue("${a.id}: valores ${c.valores} impossíveis para ${c.id}", possiveis!!.containsAll(c.valores))
        }
    }

    @Test
    fun `dependencias nao formam ciclo`() {
        todos.forEach { a ->
            val vistos = mutableSetOf(a.id)
            var atual = a.visivelSe?.id
            while (atual != null) {
                assertTrue("ciclo passando por ${a.id}", vistos.add(atual))
                atual = porId[atual]?.visivelSe?.id
            }
        }
    }

    @Test
    fun `quem pede confirmacao e interruptor`() {
        todos.filter { it.aviso != null }.forEach {
            assertEquals("${it.id}: aviso só faz sentido ao ligar", TipoDeAjuste.INTERRUPTOR, it.tipo)
        }
    }

    @Test
    fun `painel e espelhos entram no filtro da revisao`() {
        val observadas = CatalogoDeAjustes.chavesObservadas
        assertTrue(observadas.containsAll(CatalogoDeAjustes.CHAVES_DO_MODO_DO_PAINEL))
        assertTrue(Preferencias.DISABLE_BLUETOOTH_ON_POWER_OFF.chave in observadas)
        assertTrue(Preferencias.BOTTOM_BAR_SHOW_AC_DUAL_ZONE.chave in observadas)
        // Os contadores de tráfego mudam a cada poucos segundos e não podem acordar o instalador.
        assertFalse(observadas.any { it.startsWith("mobileDataTraffic") })
    }

    @Test
    fun `espelhos derivam o tipo que declaram`() {
        todos.forEach { a ->
            a.espelhos.forEach { e ->
                val exemplos: List<Any> =
                        when (a.tipo) {
                            TipoDeAjuste.INTERRUPTOR -> listOf(true, false)
                            TipoDeAjuste.OPCOES -> a.opcoes.map { it.id }
                            TipoDeAjuste.HORA -> listOf("07:05", "23:59")
                            else -> emptyList()
                        }
                exemplos.forEach { v ->
                    val derivado = e.derivar(v)
                    when (e.formato) {
                        Formato.BOOLEANO -> assertTrue("${a.id}→${e.chave}", derivado is Boolean)
                        Formato.TEXTO -> assertTrue("${a.id}→${e.chave}", derivado is String)
                        Formato.INTEIRO -> assertTrue("${a.id}→${e.chave}", derivado is Int)
                        else -> Unit
                    }
                }
            }
        }
    }

    private class Mapa(val m: Map<String, Any>) : LeitorDePreferencias {
        override fun contem(chave: String) = m.containsKey(chave)
        override fun booleano(chave: String, padrao: Boolean) = m[chave] as? Boolean ?: padrao
        override fun inteiro(chave: String, padrao: Int) = m[chave] as? Int ?: padrao
        override fun texto(chave: String) = m[chave] as? String
    }

    @Test
    fun `temperatura da barra le os interruptores antigos quando a chave nova nao existe`() {
        val a = porId.getValue(Preferencias.BOTTOM_BAR_TEMP_CONTROL.chave)
        val ler = a.lerDe!!
        // Instalação nova: nada gravado -> uma zona (showAc nasce ligado), igual à barra.
        assertEquals("single", ler(Mapa(emptyMap())))
        assertEquals("off", ler(Mapa(mapOf(Preferencias.BOTTOM_BAR_SHOW_AC.chave to false))))
        assertEquals("dual", ler(Mapa(mapOf(Preferencias.BOTTOM_BAR_SHOW_AC_DUAL_ZONE.chave to true))))
        // A chave nova manda.
        assertEquals(
                "off",
                ler(
                        Mapa(
                                mapOf(
                                        Preferencias.BOTTOM_BAR_TEMP_CONTROL.chave to "off",
                                        Preferencias.BOTTOM_BAR_SHOW_AC_DUAL_ZONE.chave to true
                                )
                        )
                )
        )
        // Espelhos coerentes com o que a tela grava.
        val espelhos = a.espelhos.associate { it.chave to it.derivar("dual") }
        assertEquals(false, espelhos[Preferencias.BOTTOM_BAR_SHOW_AC.chave])
        assertEquals(true, espelhos[Preferencias.BOTTOM_BAR_SHOW_AC_DUAL_ZONE.chave])
    }

    @Test
    fun `conectividade ao desligar aparece ligada se qualquer uma estiver`() {
        val a = porId.getValue(Preferencias.DISABLE_HOTSPOT_ON_POWER_OFF.chave)
        assertEquals(true, a.lerDe!!(Mapa(mapOf(Preferencias.DISABLE_BLUETOOTH_ON_POWER_OFF.chave to true))))
        assertEquals(false, a.lerDe!!(Mapa(emptyMap())))
    }

    @Test
    fun `audio do amplificador vem do barramento`() {
        val veiculo = todos.filter { it.guardado is Guardado.Veiculo }
        assertTrue(veiculo.size >= 4)
        assertTrue(veiculo.all { it.secao == SecaoDeAjustes.AUDIO })
        assertEquals(veiculo.map { (it.guardado as Guardado.Veiculo).chave }.toSet(), CatalogoDeAjustes.chavesDoVeiculo)
    }

    @Test
    fun `horario le e grava as duas chaves da tela antiga`() {
        val a = porId.getValue(Preferencias.NIGHT_START_HOUR.chave)
        assertEquals("20:00", a.lerDe!!(Mapa(emptyMap())))
        assertEquals(
                "21:45",
                a.lerDe!!(
                        Mapa(
                                mapOf(
                                        Preferencias.NIGHT_START_HOUR.chave to 21,
                                        Preferencias.NIGHT_START_MINUTE.chave to 45
                                )
                        )
                )
        )
        assertEquals(21, ConversaoDeAjuste.paraDisco(Formato.HORA, "21:45"))
        assertEquals(Preferencias.NIGHT_START_MINUTE.chave, a.espelhos.single().chave)
        assertEquals(45, a.espelhos.single().derivar("21:45"))
        // O minuto não é um ajuste solto: some da lista e só existe como parte do horário.
        assertTrue(Preferencias.NIGHT_START_MINUTE.chave !in porId)
        assertTrue(Preferencias.NIGHT_START_MINUTE.chave in CatalogoDeAjustes.chavesObservadas)
    }

    @Test
    fun `tema do painel e a pasta ativa e depende do tema customizado`() {
        val a = porId.getValue(CatalogoDeAjustes.ID_DO_TEMA_DO_PAINEL)
        assertTrue(a.guardado is Guardado.TemaAtivo)
        assertEquals(TipoDeAjuste.OPCOES, a.tipo)
        // Sem carro, a única opção garantida é o NTG clássico, que vem no APK — e é ela o padrão.
        // O embutido (pasta vazia) saiu da lista na 6.8.
        assertEquals("coffe3", a.padrao)
        assertTrue(a.opcoes.any { it.id == "coffe3" })
        assertFalse(a.opcoes.any { it.id == "" })
        assertEquals("painel.tema_customizado", a.visivelSe?.id)
        assertEquals(setOf("true"), a.visivelSe?.valores)
        // Trocar o tema na tela Telas grava ACTIVE_CUSTOM_THEME: o instalador tem de acordar.
        assertTrue(Preferencias.ACTIVE_CUSTOM_THEME.chave in CatalogoDeAjustes.chavesObservadas)
        assertFalse(a.inteiro)
    }
}
