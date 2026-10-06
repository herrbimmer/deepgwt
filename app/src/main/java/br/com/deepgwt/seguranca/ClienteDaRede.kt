// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.seguranca

import java.net.InetAddress

/**
 * Quem pode falar com o servidor local (porta 8766). O instalador do computador só alcança o carro
 * pelo Wi-Fi do próprio carro (192.168.33.x) e o app e a página do celular falam por esse mesmo
 * Wi-Fi ou pelo próprio aparelho. Com o firewall liberado, qualquer outro endereço (4G, Wi-Fi
 * externo do roteador do hotspot, USB) alcançaria `/set` e `/files/put` sem nenhuma outra checagem.
 */
object ClienteDaRede {

    private const val PREFIXO_DO_HOTSPOT = "192.168.33."

    /** `true` para o próprio aparelho (127.x, ::1) e para o hotspot do carro (192.168.33.0/24). */
    fun permitido(endereco: InetAddress?): Boolean {
        if (endereco == null) return false
        if (endereco.isLoopbackAddress) return true
        return permitido(endereco.hostAddress)
    }

    /** Versão por texto, testável sem rede. */
    fun permitido(texto: String?): Boolean {
        val ip = texto?.substringBefore('%')?.trim() ?: return false
        if (ip == "::1" || ip.startsWith("127.")) return true
        // Endereço IPv4 mapeado em IPv6 (::ffff:192.168.33.5).
        val v4 = ip.removePrefix("::ffff:")
        if (!v4.startsWith(PREFIXO_DO_HOTSPOT)) return false
        val ultimo = v4.removePrefix(PREFIXO_DO_HOTSPOT).toIntOrNull() ?: return false
        return ultimo in 0..255 && v4.count { it == '.' } == 3
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
