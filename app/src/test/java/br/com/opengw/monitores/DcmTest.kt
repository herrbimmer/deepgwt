package br.com.opengw.monitores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ts.car.dcm.common.data.ConnDevice

/** Parte D de `extras-monitores.md`: log (D-V 1 a 6), `ConnDevice` (D.3), leitura (D.2) e regras do aparelho. */
class DcmTest {

    // ───────── D.5a: linhas do log ─────────

    private fun linha(corpo: String) = "E/DcController( 1234): mergeDevices, devId:Device{$corpo}"

    private val linha1 = linha(
        "mId='abc-123', mFriendlyName='Pixel 7', mDeviceConnectedState=1, mConnectedTypeState={1=3, 2=3}, " +
            "mUsbSerialNumber='R5CT123', mBtAddr='AA:BB:CC:DD:EE:FF', mWifiAddr='null', mAvailableCapability=1111, " +
            "mLastAvailableCapability=1111, mActiveCapability=128, mLastActivateCapability=0, mActiveCapaState={128=4}",
    )

    @Test
    fun `D-V1 aparelho completo por USB`() {
        val a = ParserDeLogDoDcm.analisar(linha1).single()
        assertEquals("AA:BB:CC:DD:EE:FF", a.chave)
        assertEquals("abc-123", a.id)
        assertEquals("Pixel 7", a.nome)
        assertEquals("R5CT123", a.serialUsb)
        assertEquals("AA:BB:CC:DD:EE:FF", a.enderecoBt)
        assertEquals(1111, a.capacidadesDisponiveis)
        assertEquals(128, a.capacidadeAtiva)
        assertEquals(1, a.estadoDoAparelho)
        assertEquals("1=3, 2=3", a.estadoPorTipo)
        assertEquals(4, a.estadoAndroidAutoUsb)
        assertNull(a.estadoAndroidAutoSemFio)
        assertTrue(a.temProjecaoAndroidAutoAtiva())
    }

    @Test
    fun `D-V2 sem BT a chave e o id e o AA sem fio conta como ativo`() {
        val a = ParserDeLogDoDcm.analisar(
            linha(
                "mId='usb-777', mFriendlyName='Galaxy', mUsbSerialNumber='SER9', mBtAddr='null', " +
                    "mAvailableCapability=128, mActiveCapability=0, mActiveCapaState={128=1, 1024=4}",
            ),
        ).single()
        assertEquals("usb-777", a.chave)
        assertNull(a.enderecoBt)
        assertEquals(1, a.estadoAndroidAutoUsb)
        assertEquals(4, a.estadoAndroidAutoSemFio)
        assertTrue(a.temProjecaoAndroidAutoAtiva())
    }

    @Test
    fun `D-V3 apostrofo corta o nome, serial vazio vale ausente, mapa vazio vale nulo`() {
        val a = ParserDeLogDoDcm.analisar(
            linha(
                "mId='zzz', mFriendlyName='Miguel's iPhone', mUsbSerialNumber='', mBtAddr='11:22:33:44:55:66', " +
                    "mAvailableCapability=576, mActiveCapaState={}",
            ),
        ).single()
        assertEquals("11:22:33:44:55:66", a.chave)
        assertEquals("Miguel", a.nome)
        assertNull(a.serialUsb)
        assertNull(a.estadoAndroidAutoUsb)
        assertNull(a.estadoAndroidAutoSemFio)
        assertFalse(a.temProjecaoAndroidAutoAtiva())
    }

    @Test
    fun `D-V4 linha sem mId e sem mBtAddr e ignorada`() {
        assertTrue(ParserDeLogDoDcm.analisar(linha("mFriendlyName='sem id', mUsbSerialNumber='X'")).isEmpty())
    }

    @Test
    fun `D-V5 linha sem mergeDevices ou sem Device abre-chave e ignorada`() {
        assertTrue(ParserDeLogDoDcm.analisar("E/DcController( 1): Device{mId='x', mBtAddr='AA'}").isEmpty())
        assertTrue(ParserDeLogDoDcm.analisar("E/DcController( 1): mergeDevices, devId:null, key:foo, keyType:1").isEmpty())
    }

    @Test
    fun `D-V6 pares malformados do mapa por capacidade sao ignorados`() {
        val a = ParserDeLogDoDcm.analisar(
            linha("mId='k', mBtAddr='AA', mActiveCapaState={128=4, 1024=1, x=2, 5=y, 7}"),
        ).single()
        assertEquals(4, a.estadoAndroidAutoUsb)
        assertEquals(1, a.estadoAndroidAutoSemFio)
    }

    @Test
    fun `linhas com a mesma chave substituem as anteriores e a ordem da primeira aparicao fica`() {
        val saida = listOf(
            linha("mId='a', mBtAddr='AA', mActiveCapaState={128=1}"),
            linha("mId='b', mBtAddr='BB', mActiveCapaState={128=1}"),
            "E/DcController( 1): outra coisa",
            linha("mId='a', mBtAddr='AA', mActiveCapaState={128=4}"),
        ).joinToString("\n")
        val lista = ParserDeLogDoDcm.analisar(saida)
        assertEquals(listOf("AA", "BB"), lista.map { it.chave })
        assertEquals(4, lista[0].estadoAndroidAutoUsb)
        assertEquals(1, lista[1].estadoAndroidAutoUsb)
    }

    @Test
    fun `campos com prefixo parecido nao confundem o parser`() {
        val a = ParserDeLogDoDcm.analisar(
            linha("mDevId='errado', mId='certo', mLastAvailableCapability=7, mAvailableCapability=128, mLastActivateCapability=9, mActiveCapability=64"),
        ).single()
        assertEquals("certo", a.id)
        assertEquals(128, a.capacidadesDisponiveis)
        assertEquals(64, a.capacidadeAtiva)
    }

    // ───────── D.5a: cache e comando ─────────

    private class ShellFalso(var saida: String = "", var lanca: Boolean = false) {
        val comandos = ArrayList<List<String>>()
        fun executar(c: Array<String>): String {
            comandos += c.toList()
            if (lanca) throw IllegalStateException("sem shell")
            return saida
        }
    }

    @Test
    fun `o logcat pede so as linhas de erro da etiqueta do gerenciador`() {
        val shell = ShellFalso(linha1)
        LeituraDeAparelhosPorLog(shell::executar, { 10_000L }).ler()
        assertEquals(listOf(listOf("logcat", "-d", "DcController:E", "*:S")), shell.comandos)
    }

    @Test
    fun `resultado nao vazio fica em cache por 3 s`() {
        val shell = ShellFalso(linha1)
        var agora = 10_000L
        val leitura = LeituraDeAparelhosPorLog(shell::executar, { agora })
        assertEquals(1, leitura.ler().size)
        agora = 12_999
        assertEquals(1, leitura.ler().size)
        assertEquals(1, shell.comandos.size)
        agora = 13_000
        leitura.ler()
        assertEquals(2, shell.comandos.size)
    }

    @Test
    fun `resultado vazio nao cacheia`() {
        val shell = ShellFalso("")
        val leitura = LeituraDeAparelhosPorLog(shell::executar, { 10_000L })
        assertTrue(leitura.ler().isEmpty())
        assertTrue(leitura.ler().isEmpty())
        assertEquals(2, shell.comandos.size)
    }

    @Test
    fun `saida sem aparelhos nao cacheia`() {
        val shell = ShellFalso("E/DcController( 1): nada de mais")
        val leitura = LeituraDeAparelhosPorLog(shell::executar, { 10_000L })
        assertTrue(leitura.ler().isEmpty())
        assertTrue(leitura.ler().isEmpty())
        assertEquals(2, shell.comandos.size)
    }

    @Test
    fun `erro ou saida vazia devolve o que estava em cache`() {
        val shell = ShellFalso(linha1)
        var agora = 10_000L
        val leitura = LeituraDeAparelhosPorLog(shell::executar, { agora })
        assertEquals(1, leitura.ler().size)
        agora = 20_000
        shell.saida = ""
        assertEquals(1, leitura.ler().size)
        shell.lanca = true
        assertEquals(1, leitura.ler().size)
    }

    @Test
    fun `relogio para tras expira o cache`() {
        val shell = ShellFalso(linha1)
        var agora = 10_000L
        val leitura = LeituraDeAparelhosPorLog(shell::executar, { agora })
        leitura.ler()
        agora = 9_000
        leitura.ler()
        assertEquals(2, shell.comandos.size)
    }

    // ───────── D.3: ConnDevice ─────────

    /** Escreve os 31 campos na ordem da central (tabela D.3), com valores reconhecíveis. */
    private fun escreverAparelho(
        p: ParcelFalso,
        uuid: String? = "uuid-1",
        capacidades: Int = 1111,
        ativa: Int = 128,
        porCapacidade: Map<Int, Int> = mapOf(128 to 4),
    ) {
        p.escreverString(uuid) // 1
        p.escreverString("Pixel") // 2
        p.escreverInt(1) // 3 tipo
        p.escreverInt(1) // 4 aceito
        p.escreverInt(1) // 5 estado
        p.escreverMapa(mapOf(1 to 3)) // 6
        p.escreverString("SERIAL") // 7
        p.escreverString("AA:BB:CC:DD:EE:FF") // 8
        p.escreverString("wifi-mac") // 9
        p.escreverString("10.0.0.2") // 10
        p.escreverString("123456") // 11
        p.escreverInt(5) // 12
        p.escreverByte(1) // 13
        p.escreverByte(0) // 14
        p.escreverInt(64) // 15
        p.escreverInt(capacidades) // 16
        p.escreverInt(ativa) // 17
        p.escreverMapa(porCapacidade) // 18
        p.escreverInt(32) // 19
        p.escreverMapa(mapOf(1 to 2, 4 to 2)) // 20
        p.escreverByte(1) // 21
        p.escreverString("/dev/hid0") // 22
        p.escreverString("/dev/iap0") // 23
        p.escreverString("ncm0") // 24
        p.escreverByte(1) // 25
        p.escreverLong(111L) // 26
        p.escreverLong(222L) // 27
        p.escreverLong(333L) // 28
        p.escreverByte(0) // 29
        p.escreverLong(444L) // 30
        p.escreverByte(1) // 31
    }

    @Test
    fun `ConnDevice le os 31 campos na ordem da central`() {
        val p = ParcelFalso()
        escreverAparelho(p)

        val d = ConnDevice.ler(p)

        assertEquals(0, p.restantes())
        assertEquals("uuid-1", d.uuid)
        assertEquals("Pixel", d.nomeAmigavel)
        assertEquals(1, d.tipoDoAparelho)
        assertEquals(1, d.aceito)
        assertEquals(1, d.estadoDoAparelho)
        assertEquals(mapOf(1 to 3), d.estadoPorTipoDeConexao)
        assertEquals("SERIAL", d.serialUsb)
        assertEquals("AA:BB:CC:DD:EE:FF", d.enderecoBt)
        assertEquals("wifi-mac", d.enderecoWifi)
        assertEquals("10.0.0.2", d.ipWifi)
        assertEquals("123456", d.ultimoUptime)
        assertEquals(5, d.ultimoServico)
        assertTrue(d.eoUltimo)
        assertFalse(d.favorito)
        assertEquals(64, d.capacidade)
        assertEquals(1111, d.capacidadesDisponiveis)
        assertEquals(128, d.capacidadeAtiva)
        assertEquals(mapOf(128 to 4), d.estadoPorCapacidadeAtiva)
        assertEquals(32, d.capacidadeSempre)
        assertEquals(mapOf(1 to 2, 4 to 2), d.estadoPorPerfilBt)
        assertTrue(d.emModoDispositivoUsb)
        assertEquals("/dev/hid0", d.noHidUsb)
        assertEquals("/dev/iap0", d.noIap)
        assertEquals("ncm0", d.noNcmUsb)
        assertTrue(d.pareado)
        assertEquals(111L, d.horaDoPareamentoBt)
        assertEquals(222L, d.horaDaUltimaConexaoCarPlaySemFio)
        assertEquals(333L, d.horaDaUltimaConexaoCarPlayUsb)
        assertFalse(d.portaUsbOtg)
        assertEquals(444L, d.mascaraDeMudancas)
        assertTrue(d.suportaCarPlayPorUuid)
    }

    @Test
    fun `ConnDevice tem ida e volta com a mesma ordem`() {
        val entrada = ParcelFalso()
        escreverAparelho(entrada)
        val esperado = entrada.itens.toList()

        val saida = ParcelFalso()
        ConnDevice.ler(entrada).escrever(saida)

        assertEquals(31, saida.itens.size)
        assertEquals(esperado, saida.itens.toList())
    }

    @Test
    fun `os campos 30 e 31 sao lidos sempre, entao um segundo aparelho na lista nao desalinha (DD4)`() {
        val p = ParcelFalso()
        escreverAparelho(p, uuid = "primeiro", ativa = 128)
        escreverAparelho(p, uuid = "segundo", ativa = 1024, porCapacidade = mapOf(1024 to 4))

        val a = ConnDevice.ler(p)
        val b = ConnDevice.ler(p)

        assertEquals("primeiro", a.uuid)
        assertEquals("segundo", b.uuid)
        assertEquals(1024, b.capacidadeAtiva)
        assertTrue(b.suportaCarPlayPorUuid)
        assertEquals(0, p.restantes())
    }

    @Test
    fun `estado de uma capacidade ausente e 0`() {
        val p = ParcelFalso()
        escreverAparelho(p, porCapacidade = mapOf(128 to 4))
        val d = ConnDevice.ler(p)
        assertEquals(4, d.estadoDaCapacidade(128))
        assertEquals(0, d.estadoDaCapacidade(1024))
    }

    @Test
    fun `ConnDevice tem o nome combinado e o campo CREATOR estatico`() {
        assertEquals("ts.car.dcm.common.data.ConnDevice", ConnDevice::class.java.name)
        val campo = ConnDevice::class.java.getField("CREATOR")
        assertTrue(java.lang.reflect.Modifier.isStatic(campo.modifiers))
        assertTrue(java.lang.reflect.Modifier.isPublic(campo.modifiers))
        assertTrue(android.os.Parcelable::class.java.isAssignableFrom(ConnDevice::class.java))
    }

    // ───────── AparelhoDoDcm ─────────

    private fun aparelho(
        capacidades: Int? = null,
        usb: Int? = null,
        semFio: Int? = null,
    ) = AparelhoDoDcm("k", "id", "n", null, null, capacidades, null, null, null, usb, semFio)

    @Test
    fun `temAndroidAutoUsb olha o bit 128 das capacidades disponiveis`() {
        assertTrue(aparelho(capacidades = 128).temAndroidAutoUsb())
        assertTrue(aparelho(capacidades = 1 or 128 or 1024).temAndroidAutoUsb())
        assertFalse(aparelho(capacidades = 1024 or 64).temAndroidAutoUsb())
        assertFalse(aparelho(capacidades = null).temAndroidAutoUsb())
    }

    @Test
    fun `projecao ativa so com estado 4 em USB ou sem fio (DD10)`() {
        assertTrue(aparelho(usb = 4).temProjecaoAndroidAutoAtiva())
        assertTrue(aparelho(semFio = 4).temProjecaoAndroidAutoAtiva())
        assertTrue(aparelho(usb = 1, semFio = 4).temProjecaoAndroidAutoAtiva())
        assertFalse(aparelho(usb = 3, semFio = 3).temProjecaoAndroidAutoAtiva())
        assertFalse(aparelho(usb = 0, semFio = 0).temProjecaoAndroidAutoAtiva())
        assertFalse(aparelho().temProjecaoAndroidAutoAtiva())
    }

    @Test
    fun `do ConnDevice a chave e o do Bundle e os estados AA vem do mapa por capacidade (0 se ausente)`() {
        val p = ParcelFalso()
        escreverAparelho(p, porCapacidade = mapOf(128 to 4))
        val a = AparelhoDoDcm.doConnDevice("chave-do-bundle", ConnDevice.ler(p))
        assertEquals("chave-do-bundle", a.chave)
        assertEquals("uuid-1", a.id)
        assertEquals("Pixel", a.nome)
        assertEquals("SERIAL", a.serialUsb)
        assertEquals("AA:BB:CC:DD:EE:FF", a.enderecoBt)
        assertEquals(1111, a.capacidadesDisponiveis)
        assertEquals(128, a.capacidadeAtiva)
        assertEquals(1, a.estadoDoAparelho)
        assertEquals("{1=3}", a.estadoPorTipo)
        assertEquals(4, a.estadoAndroidAutoUsb)
        assertEquals(0, a.estadoAndroidAutoSemFio)
        assertTrue(a.temProjecaoAndroidAutoAtiva())
    }

    // ───────── D.2: binder primeiro, log só em falha ─────────

    private class SessaoFalsa(private val resultado: () -> Map<String, ConnDevice>) : SessaoDoDcm {
        var fechada = 0
        override fun lerAparelhos(): Map<String, ConnDevice> = resultado()
        override fun close() {
            fechada++
        }
    }

    private class AcessoFalso(private val sessao: () -> SessaoDoDcm?) : AcessoAoDcm {
        var limites = ArrayList<Long>()
        override fun abrir(limiteMs: Long): SessaoDoDcm? {
            limites += limiteMs
            return sessao()
        }
    }

    private fun conn(uuid: String, porCapacidade: Map<Int, Int>): ConnDevice {
        val p = ParcelFalso()
        escreverAparelho(p, uuid = uuid, porCapacidade = porCapacidade)
        return ConnDevice.ler(p)
    }

    private fun leitor(acesso: AcessoAoDcm, shell: ShellFalso) =
        LeitorDeAparelhosDoDcm(acesso, LeituraDeAparelhosPorLog(shell::executar, { 10_000L }))

    @Test
    fun `sucesso pelo binder devolve os aparelhos, fecha a ligacao e nao roda logcat`() {
        val sessao = SessaoFalsa { linkedMapOf("u1" to conn("u1", mapOf(128 to 4)), "u2" to conn("u2", emptyMap())) }
        val shell = ShellFalso(linha1)
        val acesso = AcessoFalso { sessao }

        val lista = leitor(acesso, shell).ler()

        assertEquals(listOf("u1", "u2"), lista.map { it.chave })
        assertTrue(lista[0].temProjecaoAndroidAutoAtiva())
        assertFalse(lista[1].temProjecaoAndroidAutoAtiva())
        assertEquals(1, sessao.fechada)
        assertTrue(shell.comandos.isEmpty())
        assertEquals(listOf(2_500L), acesso.limites)
    }

    @Test
    fun `lista vazia legitima devolve vazio sem tentar o log (DD2)`() {
        val sessao = SessaoFalsa { emptyMap() }
        val shell = ShellFalso(linha1)

        assertTrue(leitor(AcessoFalso { sessao }, shell).ler().isEmpty())

        assertTrue(shell.comandos.isEmpty())
        assertEquals(1, sessao.fechada)
    }

    @Test
    fun `ligacao recusada ou sem conectar cai no log`() {
        val shell = ShellFalso(linha1)

        val lista = leitor(AcessoFalso { null }, shell).ler()

        assertEquals(listOf("AA:BB:CC:DD:EE:FF"), lista.map { it.chave })
        assertEquals(1, shell.comandos.size)
    }

    @Test
    fun `excecao ao abrir cai no log`() {
        val shell = ShellFalso(linha1)
        val lista = leitor(AcessoFalso { throw SecurityException("x") }, shell).ler()
        assertEquals(1, lista.size)
        assertEquals(1, shell.comandos.size)
    }

    @Test
    fun `excecao na transacao ou na leitura do Bundle cai no log e ainda fecha a ligacao`() {
        val sessao = SessaoFalsa { throw IllegalStateException("classe ausente") }
        val shell = ShellFalso(linha1)

        val lista = leitor(AcessoFalso { sessao }, shell).ler()

        assertEquals(1, lista.size)
        assertEquals(1, shell.comandos.size)
        assertEquals(1, sessao.fechada)
    }

    @Test
    fun `falha do binder e log tambem vazio devolve lista vazia sem lancar`() {
        val shell = ShellFalso("")
        assertTrue(leitor(AcessoFalso { null }, shell).ler().isEmpty())
    }

    @Test
    fun `fechar que lanca nao derruba a leitura`() {
        val sessao = object : SessaoDoDcm {
            override fun lerAparelhos(): Map<String, ConnDevice> = mapOf("u" to conn("u", mapOf(128 to 4)))
            override fun close() = throw IllegalStateException("já solta")
        }
        val lista = leitor(AcessoFalso { sessao }, ShellFalso()).ler()
        assertEquals(1, lista.size)
    }
}
