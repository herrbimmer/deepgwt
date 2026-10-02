package br.com.opengw.licenca

import br.com.opengw.nucleo.OpenGw
import android.provider.Settings
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * O nível guardado no `licenca.xml` com selo (Alpha 8.3): "premium.<HMAC>".
 *
 * O [Portao] guarda o nível a que os serviços foram ajustados e o nível SEGURO até desligar (as
 * horas acabaram com o carro ligado). Até a 8.2 era o id puro: escrever "signature" ali dava o
 * Signature durante uma viagem inteira a quem conseguisse editar o arquivo. Com o selo, um valor
 * editado (ou copiado de outro aparelho) não vale — é lido como "nada guardado".
 *
 * A chave do HMAC mistura o ANDROID_ID, que no Android 8+ é próprio de cada chave de assinatura do
 * APK: um APK reassinado não lê nem produz um selo que o original aceite. Como toda chave dentro do
 * APK, segura edição casual, não quem desmontar o app.
 */
object SeloDoNivel {
    fun chave(androidId: String?): ByteArray =
        MessageDigest.getInstance("SHA-256").digest("OPENGW-NIVEL|8.3|${androidId.orEmpty()}".toByteArray(Charsets.UTF_8))

    private fun hmac(texto: String, chave: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(chave, "HmacSHA256"))
        return mac.doFinal(texto.toByteArray(Charsets.UTF_8)).take(12).joinToString("") { "%02x".format(it) }
    }

    fun selar(nivel: Nivel, chave: ByteArray): String = "${nivel.id}.${hmac("nivel|${nivel.id}", chave)}"

    /** Null se vazio, sem selo (8.2) ou com o selo errado. */
    fun ler(texto: String?, chave: ByteArray): Nivel? {
        val t = texto?.trim() ?: return null
        val nivel = Nivel.deId(t.substringBefore('.', "")) ?: return null
        return nivel.takeIf { MessageDigest.isEqual(selar(it, chave).toByteArray(), t.toByteArray()) }
    }

    /** A chave deste aparelho. */
    val chaveDoAparelho: ByteArray by lazy {
        chave(runCatching { Settings.Secure.getString(OpenGw.contexto.contentResolver, Settings.Secure.ANDROID_ID) }.getOrNull())
    }
}
