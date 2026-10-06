// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.managers

import java.io.InputStream

/**
 * Deixa ler só os [limite] primeiros bytes de [origem] e conta quantos saíram.
 *
 * É o corpo de um `POST /files/put` (Alpha 8.5): o socket continua aberto depois do corpo (a
 * resposta vai por ele), então quem copia até o "fim" precisa de um fim — o `Content-Length`.
 * [lidos] menor que [limite] no final = o cliente caiu no meio do envio.
 */
internal class FluxoLimitado(private val origem: InputStream, private val limite: Long) : InputStream() {

    var lidos: Long = 0L
        private set

    override fun read(): Int {
        if (lidos >= limite) return -1
        val b = origem.read()
        if (b >= 0) lidos++
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        val resta = limite - lidos
        if (resta <= 0) return -1
        val n = origem.read(b, off, minOf(len.toLong(), resta).toInt())
        if (n > 0) lidos += n
        return n
    }

    override fun available(): Int = minOf(origem.available().toLong(), limite - lidos).toInt().coerceAtLeast(0)

    /** Não fecha o socket: a resposta ainda vai sair por ele. */
    override fun close() {}
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
