package br.com.opengw.perfis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O tipo tem de sobreviver à viagem.
 *
 * O que se trava aqui é a única coisa que pode corromper o app inteiro a partir de uma troca de
 * perfil: uma preferência booleana voltar como texto. A partir daí todo `getBoolean` naquela
 * chave lança ClassCastException, e não só na tela de perfis — na tela que usa o ajuste, dias
 * depois, sem ligação aparente com perfil nenhum.
 */
class ValorDePreferenciaTest {

    private fun idaEVolta(valor: Any?): Any? =
        ValorDePreferencia.codificar(valor)?.let { ValorDePreferencia.decodificar(it) }

    @Test
    fun `cada tipo volta como ele mesmo`() {
        assertEquals(true, idaEVolta(true))
        assertEquals(false, idaEVolta(false))
        assertEquals(42, idaEVolta(42))
        assertEquals(-7, idaEVolta(-7))
        assertEquals(1234567890123L, idaEVolta(1234567890123L))
        assertEquals(1.5f, idaEVolta(1.5f))
        assertEquals("texto", idaEVolta("texto"))
    }

    @Test
    fun `o numero inteiro nao vira texto nem o contrario`() {
        // O caso que quebra o app: "true" gravado como String e lido com getBoolean.
        assertTrue(idaEVolta(true) is Boolean)
        assertTrue(idaEVolta("true") is String)
        assertTrue(idaEVolta(1) is Int)
        assertTrue(idaEVolta("1") is String)
        assertTrue(idaEVolta(1L) is Long)
        assertTrue(idaEVolta(1f) is Float)
    }

    @Test
    fun `texto com dois pontos e quebra de linha atravessa inteiro`() {
        val original = "s: isto: não é prefixo\nsegunda linha"
        assertEquals(original, idaEVolta(original))
    }

    @Test
    fun `conjunto de textos volta como conjunto`() {
        val original = setOf("com.ts.androidauto", "com.beantechs.mediacenter", "a,b;c")
        val voltou = idaEVolta(original)
        assertEquals(original, voltou)
    }

    @Test
    fun `conjunto vazio nao vira nulo`() {
        // Diferença que importa: "nenhum app escolhido" e "a preferência nunca existiu" levam a
        // telas diferentes.
        assertEquals(emptySet<String>(), idaEVolta(emptySet<String>()))
    }

    @Test
    fun `tipo que o SharedPreferences nao guarda e recusado na entrada`() {
        assertNull(ValorDePreferencia.codificar(null))
        assertNull(ValorDePreferencia.codificar(Any()))
        assertNull(ValorDePreferencia.codificar(listOf(1, 2)))
        assertNull(ValorDePreferencia.codificar(3.5))
    }

    @Test
    fun `linha estragada devolve nulo em vez de explodir`() {
        // Um perfil com uma linha ruim tem de aplicar todo o resto. Ver o cabeçalho da classe.
        assertNull(ValorDePreferencia.decodificar(""))
        assertNull(ValorDePreferencia.decodificar("b"))
        assertNull(ValorDePreferencia.decodificar("sem prefixo"))
        assertNull(ValorDePreferencia.decodificar("b:talvez"))
        assertNull(ValorDePreferencia.decodificar("i:abc"))
        assertNull(ValorDePreferencia.decodificar("i:9999999999999"))
        assertNull(ValorDePreferencia.decodificar("z:novo tipo do futuro"))
    }

    @Test
    fun `booleano so aceita true ou false, nao 1 e 0`() {
        // toBooleanStrictOrNull: "TRUE" e "1" não são booleanos gravados por este codificador,
        // e aceitá-los mascararia um perfil escrito por outra coisa.
        assertNull(ValorDePreferencia.decodificar("b:1"))
        assertNull(ValorDePreferencia.decodificar("b:TRUE"))
        assertEquals(true, ValorDePreferencia.decodificar("b:true"))
    }
}
