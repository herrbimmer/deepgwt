package br.com.opengw.luz

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import br.com.opengw.nucleo.OpenGw
import br.com.opengw.veiculo.HeadlightPolicy
import br.com.opengw.veiculo.Veiculo
import java.util.concurrent.ConcurrentHashMap

/** As preferências do módulo no arquivo "opengw_prefs" do armazenamento protegido. */
internal object PreferenciasDaLuz {

    private const val ARQUIVO = "opengw_prefs"

    private fun prefs(): SharedPreferences =
        OpenGw.contextoProtegido.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)

    fun ler(): AjustesDaLuz = AjustesDaLuz.ler(FonteDasPreferencias(prefs()))

    /** Grava em segundo plano (`apply`). Tipos aceitos: Boolean, Int, Float, String; null apaga. */
    fun gravar(chave: String, valor: Any?) {
        val e = prefs().edit()
        when (valor) {
            null -> e.remove(chave)
            is Boolean -> e.putBoolean(chave, valor)
            is Int -> e.putInt(chave, valor)
            is Float -> e.putFloat(chave, valor)
            is String -> e.putString(chave, valor)
            else -> e.putString(chave, valor.toString())
        }
        e.apply()
    }

    /** Endereço e nome da fita principal: gravação síncrona (`commit`). */
    fun gravarFita(endereco: String, nome: String?) {
        prefs().edit()
            .putString(ChavesDaLuz.ENDERECO, endereco)
            .apply { if (nome.isNullOrBlank()) remove(ChavesDaLuz.NOME) else putString(ChavesDaLuz.NOME, nome) }
            .commit()
    }

    fun esquecerFita() {
        prefs().edit().remove(ChavesDaLuz.ENDERECO).remove(ChavesDaLuz.NOME).commit()
    }
}

/** Leitura tolerante: tipo inesperado vale como ausente; números trocados de tipo são convertidos. */
private class FonteDasPreferencias(private val p: SharedPreferences) : FonteDeAjustes {

    override fun booleano(chave: String): Boolean? {
        if (!p.contains(chave)) return null
        return try {
            p.getBoolean(chave, false)
        } catch (e: ClassCastException) {
            null
        }
    }

    override fun inteiro(chave: String): Int? {
        if (!p.contains(chave)) return null
        return tentar { p.getInt(chave, 0) }
            ?: tentar { p.getLong(chave, 0L).toInt() }
            ?: tentar { p.getFloat(chave, 0f).toInt() }
            ?: tentar { p.getString(chave, null)?.trim()?.toIntOrNull() }
    }

    override fun real(chave: String): Float? {
        if (!p.contains(chave)) return null
        return tentar { p.getFloat(chave, 0f) }
            ?: tentar { p.getInt(chave, 0).toFloat() }
            ?: tentar { p.getLong(chave, 0L).toFloat() }
            ?: tentar { p.getString(chave, null)?.trim()?.replace(',', '.')?.toFloatOrNull() }
    }

    override fun texto(chave: String): String? = tentar { p.getString(chave, null) }

    private inline fun <T> tentar(bloco: () -> T?): T? = try {
        bloco()
    } catch (e: ClassCastException) {
        null
    }
}

/** O carro de verdade, pela fachada [Veiculo]. */
internal object CarroDoVeiculo : CarroDaLuz {

    private val adaptadores = ConcurrentHashMap<OuvinteDoCarro, Veiculo.Ouvinte>()

    override fun ler(chave: String): String? = Veiculo.ler(chave)

    override fun observar(ouvinte: OuvinteDoCarro) {
        val adaptador = adaptadores.computeIfAbsent(ouvinte) { o -> Veiculo.Ouvinte { chave, valor -> o.mudou(chave, valor) } }
        Veiculo.observar(adaptador)
    }

    override fun pararDeObservar(ouvinte: OuvinteDoCarro) {
        adaptadores.remove(ouvinte)?.let { Veiculo.pararDeObservar(it) }
    }

    override fun acompanhar(chaves: Collection<String>) {
        Veiculo.acompanhar(chaves)
    }

    override fun escrever(chave: String, valor: String) {
        Veiculo.escrever(chave, valor)
    }

    override fun horaLocal(): Int = HeadlightPolicy.horaLocal()
}

/** Log no logcat: `OpenGW.Luz` para o módulo, `OpenGW.LuzQuadro` para cada quadro enviado. */
internal object RegistroAndroid : Registro {
    private const val TAG = "OpenGW.Luz"
    private const val TAG_DOS_QUADROS = "OpenGW.LuzQuadro"

    override fun anotar(texto: String) {
        Log.i(TAG, texto)
    }

    override fun quadro(conexao: String, hex: String) {
        Log.i(TAG_DOS_QUADROS, "[$conexao] $hex")
    }
}
