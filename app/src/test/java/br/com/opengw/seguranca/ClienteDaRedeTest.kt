package br.com.opengw.seguranca

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClienteDaRedeTest {

    @Test fun oHotspotDoCarroEOProprioAparelhoPodem() {
        assertTrue(ClienteDaRede.permitido("192.168.33.177"))
        assertTrue(ClienteDaRede.permitido("192.168.33.1"))
        assertTrue(ClienteDaRede.permitido("127.0.0.1"))
        assertTrue(ClienteDaRede.permitido("::1"))
        assertTrue(ClienteDaRede.permitido("::ffff:192.168.33.5"))
    }

    @Test fun qualquerOutraRedeNaoPode() {
        assertFalse(ClienteDaRede.permitido("192.168.100.35"))
        assertFalse(ClienteDaRede.permitido("192.168.3.1"))
        assertFalse(ClienteDaRede.permitido("10.0.0.5"))
        assertFalse(ClienteDaRede.permitido("192.168.33.999"))
        assertFalse(ClienteDaRede.permitido("192.168.33.1.9"))
        assertFalse(ClienteDaRede.permitido("192.168.330.1"))
        assertFalse(ClienteDaRede.permitido("2001:db8::1"))
        assertFalse(ClienteDaRede.permitido(""))
        assertFalse(ClienteDaRede.permitido(null as String?))
    }
}
