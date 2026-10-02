package br.com.opengw.licenca

import br.com.opengw.nucleo.OpenGw
import android.content.pm.PackageManager
import android.util.Log
import br.com.opengw.BuildConfig
import java.security.MessageDigest

/**
 * Este APK é o que foi assinado aqui? (Alpha 8.3)
 *
 * Quem edita o APK (tira a cobrança, troca uma verificação) precisa assiná-lo de novo com uma chave
 * própria — não tem a nossa. O Gradle grava no build a impressão digital (SHA-256) do certificado
 * que vai assinar o APK (`BuildConfig.LICENCA_CERTIFICADO`); aqui o app compara com o certificado
 * com que foi instalado. Diferente = APK alterado: com a cobrança ligada, nenhuma licença vale
 * nele ([Portao.Origem.ALTERADO]).
 *
 * O valor no BuildConfig vai MASCARADO (XOR com um hash fixo): procurar a impressão digital do
 * certificado dentro do APK para trocá-la não acha nada.
 *
 * Na dúvida, não acusa: sem referência no build ou sem conseguir ler o certificado, o estado é
 * [Estado.SEM_REFERENCIA]/[Estado.ILEGIVEL] e nada muda. Só [Estado.DIFERENTE] pesa.
 *
 * Não segura quem editar também esta conferência — segura quem só reassinar o APK ou trocar uma
 * das outras verificações. O `sincronizar-apk.sh` confere, antes de embutir, que o APK saiu assinado
 * com o certificado gravado nele.
 */
object Integridade {
    private const val TAG = "Integridade"

    enum class Estado(val id: String) {
        CONFERE("confere"),
        DIFERENTE("diferente"),
        /** Build sem a impressão digital (keystore não achada na hora do build). */
        SEM_REFERENCIA("sem-referencia"),
        ILEGIVEL("ilegivel")
    }

    fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun mascara(pacote: String): ByteArray = sha256("OPENGW-CERT|$pacote".toByteArray(Charsets.UTF_8))

    /** A impressão digital (32 bytes) → texto do BuildConfig. A mesma conta está no `build.gradle.kts`. */
    fun mascarar(impressao: ByteArray, pacote: String): String {
        val m = mascara(pacote)
        return impressao.mapIndexed { i, b -> (b.toInt() xor m[i].toInt()) and 0xff }.joinToString("") { "%02x".format(it) }
    }

    /** O contrário de [mascarar]; null se vazio ou malformado. */
    fun revelar(texto: String, pacote: String): ByteArray? {
        val t = texto.trim()
        if (t.length != 64 || t.any { Character.digit(it, 16) < 0 }) return null
        val m = mascara(pacote)
        return ByteArray(32) { i -> ((t.substring(2 * i, 2 * i + 2).toInt(16) xor m[i].toInt()) and 0xff).toByte() }
    }

    /** Pura. [certificados] = DER de cada certificado com que o APK foi instalado. */
    fun conferir(certificados: List<ByteArray>?, esperado: ByteArray?): Estado = when {
        esperado == null -> Estado.SEM_REFERENCIA
        certificados.isNullOrEmpty() -> Estado.ILEGIVEL
        certificados.any { MessageDigest.isEqual(sha256(it), esperado) } -> Estado.CONFERE
        else -> Estado.DIFERENTE
    }

    @Suppress("DEPRECATION")
    private fun certificadosInstalados(): List<ByteArray>? = runCatching {
        val contexto = OpenGw.contexto
        val info = contexto.packageManager.getPackageInfo(contexto.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val assinatura = info.signingInfo ?: return@runCatching null
        // Com rotação de chave, o histórico traz a original: vale qualquer uma delas.
        val lista = if (assinatura.hasMultipleSigners()) assinatura.apkContentsSigners else assinatura.signingCertificateHistory
        lista?.map { it.toByteArray() }
    }.getOrNull()

    /** Calculado uma vez: o certificado não muda com o app rodando. */
    val estado: Estado by lazy {
        val esperado = revelar(BuildConfig.LICENCA_CERTIFICADO, BuildConfig.APPLICATION_ID)
        conferir(if (esperado == null) null else certificadosInstalados(), esperado).also {
            if (it == Estado.CONFERE) Log.i(TAG, "certificado ${it.id}") else Log.w(TAG, "certificado ${it.id}")
        }
    }

    val alterado: Boolean get() = estado == Estado.DIFERENTE
}
