package br.com.deepgwt.tela3d.ponte

import java.util.concurrent.ConcurrentHashMap

/*
 * Peças pequenas e sem Android que as pontes JS usam. Cada uma tem uma implementação Android em
 * SuportesAndroid.kt; os testes usam falsos.
 */

/** O arquivo de preferências `deepgwt_lancador` (mesmo nome e mesmas chaves dos carros já instalados). */
interface PreferenciasDoLancador {
    fun texto(chave: String, padrao: String): String
    fun inteiro(chave: String, padrao: Int): Int
    fun gravarTexto(chave: String, valor: String)
    fun gravarInteiro(chave: String, valor: Int)

    companion object {
        const val ARQUIVO = "deepgwt_lancador"
        const val CHAVE_FUNDO_DO_SHELL = "shellBg"
        const val CHAVE_WIDGETS = "widgets"
        const val CHAVE_OCUPACAO = "uiMode"
        const val CHAVE_MODO_DO_DOCK = "dockSurfaceMode"
    }
}

/** O registro (log) da ponte. Em produção vai para o `Log` do Android; nos testes é um falso. */
interface DiarioDaPonte {
    fun info(mensagem: String)
    fun aviso(mensagem: String)
    fun erro(mensagem: String, causa: Throwable? = null)

    companion object {
        val SILENCIOSO = object : DiarioDaPonte {
            override fun info(mensagem: String) {}
            override fun aviso(mensagem: String) {}
            override fun erro(mensagem: String, causa: Throwable?) {}
        }
    }
}

/** Posta trabalho na thread principal (a thread de ponte do WebView não é a principal). */
interface ExecutorDaUi {
    fun postar(tarefa: () -> Unit)
}

/** Cache de telemetria: escrita na principal, leitura na thread de ponte. Cresce só até o número de chaves do carro. */
class CacheDeTelemetria {
    private val valores = ConcurrentHashMap<String, String>()

    fun guardar(chave: String, valor: String) {
        valores[chave] = valor
    }

    /** O último valor, ou `""` se a chave nunca chegou (nunca nulo). */
    fun valorDe(chave: String?): String = if (chave == null) "" else valores[chave] ?: ""
}

/** Os broadcasts que a ponte manda ao carro (`setPackage` do próprio app, destino `ViewerBridgeReceiver`). */
interface EnviadorDeComandos {
    fun pedirRetratoDeTelemetria()
    fun enviarComando(comando: String, valor: String)
    fun escreverChave(chave: String, valor: String)
}

/** O ambiente de relógio do sistema, cada parte podendo falhar sozinha. */
interface AmbienteDoRelogio {
    /** Tag BCP-47 do idioma do sistema. */
    fun etiquetaDoIdioma(): String?

    /** Id do fuso horário. */
    fun idDoFuso(): String?

    /** O sistema usa 24 h? */
    fun sistemaEm24h(): Boolean
}

object JsonDoRelogio {
    /** `{"locale":…,"timeZone":…,"system24":…}`; a parte que falha usa o padrão (`en-US`, `""`, `false`). */
    fun montar(ambiente: AmbienteDoRelogio): String {
        val idioma = try { ambiente.etiquetaDoIdioma() } catch (e: Exception) { null }
        val fuso = try { ambiente.idDoFuso() } catch (e: Exception) { null }
        val em24h = try { ambiente.sistemaEm24h() } catch (e: Exception) { false }
        return "{\"locale\":" + ScriptsDaTela3d.textoJs(idioma?.takeIf { it.isNotBlank() && it != "und" } ?: "en-US") +
            ",\"timeZone\":" + ScriptsDaTela3d.textoJs(fuso ?: "") +
            ",\"system24\":" + ScriptsDaTela3d.booleanoJs(em24h) + "}"
    }
}

/** `AudioManager.isMusicActive`, escondido atrás de uma função. */
fun interface MusicaAtiva {
    fun ativa(): Boolean
}

/** O GET HTTPS de `fetchJson`. */
fun interface BuscadorDeJson {
    /** Devolve o corpo ou `""`; nunca lança. */
    fun buscar(url: String?): String
}
