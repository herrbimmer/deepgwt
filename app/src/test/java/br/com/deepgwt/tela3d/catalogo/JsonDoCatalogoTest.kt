package br.com.deepgwt.tela3d.catalogo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonDoCatalogoTest {
    @Test
    fun `lista vazia e colchetes`() {
        assertEquals("[]", JsonDoCatalogo.montar(emptyList()))
        assertEquals(JsonDoCatalogo.VAZIO, JsonDoCatalogo.montar(emptyList()))
    }

    @Test
    fun `texto compacto com as tres chaves na ordem`() {
        val json = JsonDoCatalogo.montar(
            listOf(EntradaDoCatalogo("com.a", "A", "data:image/png;base64,AAAA")),
        )
        assertEquals("""[{"packageName":"com.a","label":"A","icon":"data:image/png;base64,AAAA"}]""", json)
    }

    @Test
    fun `chave icon e omitida quando nao ha icone`() {
        val json = JsonDoCatalogo.montar(listOf(EntradaDoCatalogo("com.a", "", null)))
        assertEquals("""[{"packageName":"com.a","label":""}]""", json)
        val obj = (Json.parseToJsonElement(json) as JsonArray)[0] as JsonObject
        assertFalse("icon" in obj)
        assertEquals("", obj.getValue("label").jsonPrimitive.content)
    }

    @Test
    fun `rotulos com aspas e acentos sobrevivem a ida e volta`() {
        val rotulo = "Navegação \"GWM\" \\ barra"
        val json = JsonDoCatalogo.montar(listOf(EntradaDoCatalogo("com.a", rotulo, null)))
        val obj = (Json.parseToJsonElement(json) as JsonArray)[0] as JsonObject
        assertEquals(rotulo, obj.getValue("label").jsonPrimitive.content)
    }

    @Test
    fun `ordem do array e a das entradas`() {
        val json = JsonDoCatalogo.montar(listOf("c", "a", "b").map { EntradaDoCatalogo(it, it, null) })
        val pacotes = (Json.parseToJsonElement(json) as JsonArray).map { (it as JsonObject).getValue("packageName").jsonPrimitive.content }
        assertEquals(listOf("c", "a", "b"), pacotes)
    }

    @Test
    fun `data url de png usa base64 sem quebras`() {
        val bytes = ByteArray(300) { (it * 7).toByte() }
        val url = JsonDoCatalogo.dataUrlDePng(bytes)
        assertTrue(url.startsWith("data:image/png;base64,"))
        assertFalse(url.contains('\n'))
        assertFalse(url.contains('\r'))
        val volta = java.util.Base64.getDecoder().decode(url.removePrefix("data:image/png;base64,"))
        assertTrue(bytes.contentEquals(volta))
    }
}
