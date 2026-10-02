package br.com.opengw.luz

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal enum class FonteDaMusica(val rotulo: String) {
    NENHUMA("Nenhum"),
    CENTRAL("Central"),
    DIGITAL("Digital"),
    MICROFONE("Microfone"),
    ALBUM("Álbum"),
}

/** O que a tela mostra do efeito com música. [ultimoEventoEm] = hora (ms de parede) da última batida/passo. */
internal data class DiagnosticoDaMusica(
    val ativa: Boolean = false,
    val fonte: FonteDaMusica = FonteDaMusica.NENHUMA,
    val nivel: Double = 0.0,
    val ultimoEventoEm: Long = 0L,
    val aviso: String? = null,
    val erro: String? = null,
)

/** Estado compartilhado entre o serviço e a tela (mesmo processo). */
internal object DiagnosticoDaLuz {

    private val _mensagem = MutableStateFlow<String?>(null)
    val mensagem: StateFlow<String?> = _mensagem.asStateFlow()

    private val _musica = MutableStateFlow(DiagnosticoDaMusica())
    val musica: StateFlow<DiagnosticoDaMusica> = _musica.asStateFlow()

    fun mensagem(texto: String?) {
        _mensagem.value = texto
    }

    fun musica(mudanca: (DiagnosticoDaMusica) -> DiagnosticoDaMusica) = _musica.update(mudanca)
}
