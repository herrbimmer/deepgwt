package br.com.deepgwt.tela3d.cartoes

import org.junit.Assert.assertEquals
import org.junit.Test

class CorteDeTextoTest {
    /** Cada caractere mede 10. */
    private val medir: (String) -> Float = { it.length * 10f }

    @Test fun `cabendo devolve igual`() {
        assertEquals("Paulista", CorteDeTexto.cortar("Paulista", 80f, medir))
    }

    @Test fun `nao cabendo corta caractere a caractere com reticencias`() {
        // largura 50 = 5 caracteres: 4 letras + "…"
        assertEquals("Aven…", CorteDeTexto.cortar("Avenida Paulista", 50f, medir))
        assertEquals("Avenida Paulis…", CorteDeTexto.cortar("Avenida Paulista", 150f, medir))
    }

    @Test fun `espaco antes das reticencias e removido`() {
        // 5 caracteres cabem: "Av. P…" teria 6; "Av. …" é cortado para "Av.…"
        assertEquals("Av.…", CorteDeTexto.cortar("Av. Paulista", 50f, medir))
    }

    @Test fun `largura minima so cabe a reticencia`() {
        assertEquals("…", CorteDeTexto.cortar("Paulista", 10f, medir))
        assertEquals("", CorteDeTexto.cortar("Paulista", 5f, medir))
    }

    @Test fun `texto vazio continua vazio`() {
        assertEquals("", CorteDeTexto.cortar("", 0f, medir))
    }

    @Test fun `tamanho que cabe decresce de 1 em 1 e para no minimo`() {
        // largura medida = 2 × tamanho; limite 70 → 35
        assertEquals(35f, CorteDeTexto.tamanhoQueCabe(62f, 70f) { it * 2 }, 0f)
        assertEquals(62f, CorteDeTexto.tamanhoQueCabe(62f, 1000f) { it * 2 }, 0f)
        assertEquals(10f, CorteDeTexto.tamanhoQueCabe(62f, 5f) { it * 2 }, 0f)
    }
}
