package br.com.deepgwt.botoesextra

import br.com.deepgwt.botoesextra.RegrasDasTeclasExternas.AcaoDaTecla
import br.com.deepgwt.botoesextra.RegrasDasTeclasExternas.Fase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RegrasDasTeclasExternasTest {

    @Test
    fun `evento de tecla apertada e solta`() {
        val d = RegrasDasTeclasExternas.lerEvento("EV_KEY       KEY_F13              DOWN")
        assertEquals("KEY_F13", d!!.tecla)
        assertEquals(Fase.APERTOU, d.fase)
        assertEquals(Fase.SOLTOU, RegrasDasTeclasExternas.lerEvento("EV_KEY       KEY_F13              UP")!!.fase)
        assertEquals(Fase.REPETIU, RegrasDasTeclasExternas.lerEvento("EV_KEY       KEY_A                REPEAT")!!.fase)
    }

    @Test
    fun `aceita o prefixo do no e ignora o que nao e tecla`() {
        assertEquals("KEY_A", RegrasDasTeclasExternas.lerEvento("/dev/input/event3: EV_KEY       KEY_A                DOWN")!!.tecla)
        assertNull(RegrasDasTeclasExternas.lerEvento("EV_SYN       SYN_REPORT           00000000"))
        assertNull(RegrasDasTeclasExternas.lerEvento("EV_MSC       MSC_SCAN             00070004"))
        assertNull(RegrasDasTeclasExternas.lerEvento("EV_KEY       KEY_A                00000002"))
        assertNull(RegrasDasTeclasExternas.lerEvento(""))
    }

    private val dump = """
        add device 1: /dev/input/event0
          name:     "gpio-keys"
          events:
            KEY (0001): KEY_POWER
        add device 2: /dev/input/event1
          name:     "Goodix Touchscreen"
          events:
            KEY (0001): BTN_TOUCH
            ABS (0003): ABS_MT_SLOT           : value 0, min 0, max 9, fuzz 0, flat 0, resolution 0
                        ABS_MT_POSITION_X     : value 0, min 0, max 1919, fuzz 0, flat 0, resolution 0
                        ABS_MT_POSITION_Y     : value 0, min 0, max 719, fuzz 0, flat 0, resolution 0
        add device 3: /dev/input/event2
          name:     "Logitech USB Keyboard"
          events:
            KEY (0001): KEY_ESC               KEY_1                 KEY_2
                        KEY_F12               KEY_F13               KEY_F14
            MSC (0004): MSC_SCAN
            LED (0011): LED_NUML              LED_CAPSL
        add device 4: /dev/input/event3
          name:     "Mouse"
          events:
            REL (0002): REL_X                 REL_Y
    """.trimIndent()

    @Test
    fun `acha teclados e nao digitalizador de toque nem mouse`() {
        val achados = RegrasDasTeclasExternas.acharDispositivos(dump)
        assertEquals(listOf("/dev/input/event0", "/dev/input/event2"), achados.map { it.caminho })
        assertEquals("Logitech USB Keyboard", achados.last().nome)
    }

    @Test
    fun `teclas que continuam na linha de baixo contam`() {
        val so = """
            add device 1: /dev/input/event5
              name:     "Macro Pad"
              events:
                KEY (0001): BTN_MISC
                            KEY_F20
        """.trimIndent()
        assertEquals(1, RegrasDasTeclasExternas.acharDispositivos(so).size)
    }

    @Test
    fun `respeita o teto de dispositivos`() {
        val muitos = (1..10).joinToString("\n") {
            "add device $it: /dev/input/event$it\n  name:     \"k$it\"\n  events:\n    KEY (0001): KEY_A"
        }
        assertEquals(RegrasDasTeclasExternas.MAX_DISPOSITIVOS, RegrasDasTeclasExternas.acharDispositivos(muitos).size)
    }

    @Test
    fun `rotulo legivel da tecla`() {
        assertEquals("F13", RegrasDasTeclasExternas.rotuloDaTecla("KEY_F13"))
        assertEquals("A", RegrasDasTeclasExternas.rotuloDaTecla("KEY_A"))
        assertEquals("Volumeup", RegrasDasTeclasExternas.rotuloDaTecla("KEY_VOLUMEUP"))
        assertEquals("Page up", RegrasDasTeclasExternas.rotuloDaTecla("KEY_PAGE_UP"))
    }

    @Test
    fun `a acao de uma tecla vai e volta como texto`() {
        val a = AcaoDaTecla.FuncaoDoDeck("VOLUME_UP")
        val b = AcaoDaTecla.LigaDesligaAjuste("disableAvas")
        assertEquals(a, RegrasDasTeclasExternas.decodificar(RegrasDasTeclasExternas.codificar(a)))
        assertEquals(b, RegrasDasTeclasExternas.decodificar(RegrasDasTeclasExternas.codificar(b)))
        assertNull(RegrasDasTeclasExternas.decodificar(null))
        assertNull(RegrasDasTeclasExternas.decodificar("deck:"))
        assertNull(RegrasDasTeclasExternas.decodificar("lixo"))
        assertNotNull(RegrasDasTeclasExternas.decodificar("ajuste:x"))
    }
}
