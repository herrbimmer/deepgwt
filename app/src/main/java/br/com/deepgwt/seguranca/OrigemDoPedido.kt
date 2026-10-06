// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.seguranca

/**
 * Quem pode falar com o GwLink (porta 8766) a partir de um NAVEGADOR (Alpha 8.5, auditoria B4).
 *
 * O servidor não tem senha, e o argumento de sempre ("quem está no Wi-Fi do carro já tem telnet
 * root") vale para uma PESSOA na rede. Não vale para uma PÁGINA: um celular no hotspot do carro
 * que abre um site qualquer deixa esse site mandar `fetch("http://192.168.x.1:8766/set")` — o
 * navegador não fala telnet, mas fala HTTP. Até a 8.4 o servidor respondia
 * `Access-Control-Allow-Origin: *` para todo mundo.
 *
 * O QUE CONTINUA ENTRANDO
 *  - Sem cabeçalho `Origin`: o instalador (Rust, reqwest), `curl`, testes — nenhum navegador.
 *  - O launcher 3D: WebView com a página em `https://appassets.androidplatform.net`.
 *  - A página do celular servida pelo próprio GwLink (`/ativar`): mesma origem, com o `Host`
 *    sendo um IP ou `localhost` (um nome de domínio no `Host` é o sinal de DNS rebinding).
 *  - Páginas do próprio aparelho (`localhost`/`127.0.0.1`) e a janela do instalador (Tauri).
 *
 * O QUE PARA
 *  - Qualquer outra origem, e `Origin: null` (página em sandbox ou `file://`).
 *  - Pedido sem `Origin` mas marcado pelo navegador como `Sec-Fetch-Site: cross-site` — é um
 *    `<img src>` ou um link de outro site. Só a página `/ativar` aceita chegar assim (abrir o link
 *    não faz nada sozinho).
 *
 * Puro, sem Android: testável na JVM.
 */
object OrigemDoPedido {

    const val LAUNCHER_3D = "https://appassets.androidplatform.net"

    private val ORIGENS_FIXAS = setOf(
        LAUNCHER_3D,
        "tauri://localhost",
        "http://tauri.localhost",
        "https://tauri.localhost"
    )

    private val HOSTS_LOCAIS = setOf("localhost", "127.0.0.1", "[::1]")

    sealed class Decisao {
        /** [origemCors] volta em `Access-Control-Allow-Origin`; null = não precisa de CORS. */
        data class Aceitar(val origemCors: String?) : Decisao()
        object Recusar : Decisao()
    }

    fun decidir(metodo: String, caminho: String, origin: String?, host: String?, secFetchSite: String?): Decisao {
        val o = origin?.trim().orEmpty()
        if (o.isEmpty()) {
            val cruzado = secFetchSite?.trim().equals("cross-site", ignoreCase = true)
            if (cruzado && !(metodo == "GET" && caminho == "/ativar")) return Decisao.Recusar
            return Decisao.Aceitar(null)
        }
        if (o.equals("null", ignoreCase = true)) return Decisao.Recusar
        val normal = o.lowercase().trimEnd('/')
        if (normal in ORIGENS_FIXAS) return Decisao.Aceitar(o)
        val (esquema, hostDaOrigem) = partes(normal) ?: return Decisao.Recusar
        if (esquema != "http" && esquema != "https") return Decisao.Recusar
        if (semPorta(hostDaOrigem) in HOSTS_LOCAIS) return Decisao.Aceitar(o)
        // Mesma origem: a página que o próprio GwLink serviu (o celular abrindo /ativar pelo QR).
        val h = host?.trim()?.lowercase().orEmpty()
        if (h.isNotEmpty() && hostDaOrigem == h && esquema == "http" && hostSemNome(semPorta(h))) {
            return Decisao.Aceitar(null)
        }
        return Decisao.Recusar
    }

    /** "http://1.2.3.4:8766" → ("http", "1.2.3.4:8766"). */
    private fun partes(origem: String): Pair<String, String>? {
        val i = origem.indexOf("://")
        if (i <= 0) return null
        val host = origem.substring(i + 3)
        if (host.isEmpty() || host.contains('/') || host.contains('@')) return null
        return origem.substring(0, i) to host
    }

    private fun semPorta(host: String): String =
        if (host.startsWith("[")) host.substringBefore("]") + "]" else host.substringBefore(':')

    /** IP literal ou localhost: um nome de domínio aqui pode ter sido "religado" para o carro. */
    private fun hostSemNome(host: String): Boolean {
        if (host in HOSTS_LOCAIS) return true
        val partes = host.split('.')
        return partes.size == 4 && partes.all { p -> p.toIntOrNull()?.let { it in 0..255 } == true }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
