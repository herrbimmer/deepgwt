package br.com.opengw.telas

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Mídia do Android Auto (parte 4 da especificação do lançador). Nenhuma função lança. */
object MidiaDoAndroidAuto {

    private val motor: MotorDaMidia get() = Lancador.nucleo.midia

    /** A3. */
    @JvmStatic
    fun cederAoRadioNativo(motivo: String): Boolean = motor.cederAoRadioNativo(motivo)

    /** A4. */
    @JvmStatic
    @JvmOverloads
    fun audioTocando(motivo: String, forcarLeitura: Boolean = false): Boolean = motor.audioTocando(motivo, forcarLeitura)

    /** A6. */
    @JvmStatic
    fun sessaoPronta(motivo: String): Boolean = motor.sessaoPronta(motivo)

    /** A7. */
    @JvmStatic
    fun canalAtivoSeJaLigado(motivo: String): Boolean = motor.canalAtivoSeJaLigado(motivo)

    /** A8. */
    @JvmStatic
    fun temJanelaDoAndroidAuto(): Boolean = motor.temJanelaDoAndroidAuto()

    /** C3. */
    @JvmStatic
    fun preferirTeclaAap(motivo: String): Boolean = Lancador.nucleo.semFalhar("preferirTeclaAap", false) { motor.preferirTeclaAap(motivo) }

    /** C4: sempre verdadeiro. */
    @JvmStatic
    fun pedirFocoDeVideo(tocar: Boolean): Boolean = true

    /** C1. */
    suspend fun prepararAlvo(motivo: String, display: Int? = null, pedirFocoDeVideo: Boolean = true): Boolean =
        withContext(Dispatchers.IO) { motor.prepararAlvo(motivo, display, pedirFocoDeVideo) }

    /** M2. */
    suspend fun pularFaixa(paraFrente: Boolean, jaPreparado: Boolean = false): Boolean =
        withContext(Dispatchers.IO) { motor.pularFaixa(paraFrente, jaPreparado) }

    /** M3. */
    suspend fun tocarOuPausarPorTeclaAap(tocandoAgora: Boolean): Boolean =
        withContext(Dispatchers.IO) { motor.tocarOuPausarPorTeclaAap(tocandoAgora) }

    /** M4. */
    suspend fun alternarPorTeclaAap(): Boolean = withContext(Dispatchers.IO) { motor.alternarPorTeclaAap() }

    /** M5. */
    suspend fun tocarOuPausarDireto(tocandoAgora: Boolean): Boolean =
        withContext(Dispatchers.IO) { motor.tocarOuPausarDireto(tocandoAgora) }

    /** H1. */
    @JvmStatic
    fun estadoParaDepuracao(motivo: String): String = motor.estadoParaDepuracao(motivo)

    // --- entradas do carro ---

    /** E1: true = tratada. */
    @JvmStatic
    fun teclaDoVolante(tecla: Int, acao: Int): Boolean = motor.teclaDoVolante(tecla, acao)

    /** F1. */
    @JvmStatic
    fun comandoDeMidiaDoPainel(comando: Int): Boolean =
        Lancador.nucleo.semFalhar("comandoDeMidiaDoPainel", false) { motor.comandoDeMidiaDoPainel(comando) }

    /** F2. */
    @JvmStatic
    fun teclaMereceDiagnostico(tecla: Int): Boolean = tecla in TeclasDaMidia.DO_VOLANTE

    /** F2 (consulta). */
    @JvmStatic
    fun mensagemDoPainelMereceDiagnostico(mensagem: Int): Boolean =
        Lancador.nucleo.semFalhar("mensagemDoPainelMereceDiagnostico", false) { motor.mensagemDoPainelMereceDiagnostico(mensagem) }

    /** F3: sempre falso (decisão 3). */
    @JvmStatic
    fun consumirTeclaDaAcessibilidade(tecla: Int, acao: Int): Boolean = false
}
