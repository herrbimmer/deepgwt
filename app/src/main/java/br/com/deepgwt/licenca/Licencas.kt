// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.licenca

import br.com.deepgwt.nucleo.Shell
import br.com.deepgwt.nucleo.DeepGwt
import br.com.deepgwt.veiculo.Veiculo
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import br.com.deepgwt.veiculo.Sinal
import br.com.deepgwt.veiculo.Sinais
import br.com.deepgwt.perfis.BoasVindas
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * As licenças deste carro, vivas (Alpha 8.1): carregar, ativar, contar as horas e avisar.
 *
 * O NÍVEL que vale continua sendo decisão do [Portao]; daqui ele só pergunta o nível que a
 * carteira sustenta ([nivelDaLicenca]) e se o carro está ligado ([readyAgora]).
 *
 * VIN. Toda licença é reverificada contra o VIN a cada carga — o lido agora ou, antes de o
 * barramento responder no boot, o guardado na última leitura (só no `licenca.xml` do app; as
 * cópias que outros apps leem levam só o hash). Ativar uma licença nova exige o VIN lido na hora.
 * VIN diferente do das licenças guardadas = nenhuma vale (Drive).
 *
 * TRÊS CÓPIAS. `licenca.xml` (device-protected, fora do `deepgwt_prefs`: não vai para `/ajustes`,
 * perfis nem backup), `Settings.Global deepgwt_lic` e `/data/local/tmp/.deepgwt_lic` (as duas
 * pelo Shizuku; sobrevivem a limpar os dados e a desinstalar). Carga = união das séries + maior
 * uso de cada uma ([Carteira.fundir]).
 *
 * CONTAGEM. Tique a cada [INTERVALO_MS] numa thread própria: conta com o carro em READY, os
 * serviços do DeepGWT de pé e o nível vindo de licença ([ContadorDeUso]). Grava no app a cada
 * minuto; nas cópias externas a cada 5 minutos, ao esgotar e ao desligar o carro.
 *
 * TESTE GRÁTIS ([Avaliacao], refeito na 10.1). Sem relógio: vale até o carro desligar (READY
 * ligado → desligado) ou a central reiniciar ([idDoBoot]); as licenças ficam paradas nesse tempo.
 * O contador de testes mora só no `licenca.xml` ([CHAVE_AVALIACAO]): reinstalar zera. Acabou com
 * o carro andando (a pessoa tocou em "voltar ao meu plano"): o Portão segura o Max só até a
 * primeira parada — o tique confere a velocidade a cada [INTERVALO_DA_PARADA_MS] enquanto isso.
 */
object Licencas {
    private const val TAG = "Licencas"
    const val INTERVALO_MS = 30_000L
    private const val TIQUES_POR_GRAVACAO = 2
    private const val TIQUES_POR_COPIA = 10
    /** A ronda da [Sentinela]: a cada 5 minutos. */
    const val TIQUES_DA_SENTINELA = 10L
    private const val VELOCIDADE_PARADO = 5f
    private const val ATRASO_DO_AVISO_MS = 6_000L
    private const val DURACAO_DO_AVISO_MS = 9_000L

    /** A [Sentinela] lê estas duas direto do disco. */
    const val CHAVE_CARTEIRA = "carteira"
    const val CHAVE_VIN = "vin"
    private const val CHAVE_QUEDA = "quedaParaAnunciar"
    /** O teste acabou e o Max está seguro até a primeira parada. Sai ao cair ou ao desligar. */
    private const val CHAVE_FIM_DA_AVALIACAO = "avaliacaoAcabou"
    /** O [Avaliacao.Registro] (contador + teste correndo). Só aqui: reinstalar zera, de propósito. */
    const val CHAVE_AVALIACAO = "avaliacao"
    /** Só no build de teste: finge outro boot (para ver o teste acabar sem reiniciar a central). */
    private const val CHAVE_BOOT_DE_TESTE = "bootDeTeste"
    private const val INTERVALO_DA_PARADA_MS = 3_000L

    /** Só num build de teste ([Portao.testeLiberado]). */
    const val CHAVE_VIN_DE_TESTE = "vinDeTeste"
    const val CHAVE_READY_DE_TESTE = "readyDeTeste"
    const val CHAVE_COBRANCA_DE_TESTE = "cobrancaDeTeste"

    const val GLOBAL = "deepgwt_lic"
    const val ARQUIVO_EXTERNO = "/data/local/tmp/.deepgwt_lic"

    /** O que a tela e o instalador mostram. Só as licenças que conferem com o VIN. */
    data class Resumo(
        val vin: String? = null,
        val vinLidoAgora: Boolean = false,
        val carteira: Carteira = Carteira(),
        /** Licenças guardadas que NÃO conferem com o VIN (de outro carro). */
        val deOutroCarro: Int = 0,
        val nivelAtivo: Nivel? = null,
        val saldoAtivo: Saldo? = null,
        val aviso: AvisoDaLicenca.Aviso? = null
    )

    class Recusa(val motivo: MotivoDaRecusa, mensagem: String, val linha: Int? = null) : Exception(mensagem)

    private val resumoFlow = MutableStateFlow(Resumo())
    private val trava = Any()
    @Volatile private var carregada = false
    private var carteira = Carteira()
    private var validas: Set<Int> = emptySet()
    @Volatile private var vinAoVivo: String? = null

    private var handler: Handler? = null
    private val contador = ContadorDeUso(INTERVALO_MS)
    private var tiques = 0L
    private var ultimoReady: Boolean? = null
    private var avisadoNestaPartida = true
    @Volatile private var sujaNoApp = false
    @Volatile private var sujaNasCopias = false

    val resumo: StateFlow<Resumo>
        get() {
            garantirCarregada()
            return resumoFlow.asStateFlow()
        }

    private fun prefs(): SharedPreferences? =
        runCatching { DeepGwt.contextoProtegido.getSharedPreferences(Portao.ARQUIVO, Context.MODE_PRIVATE) }.getOrNull()

    // ── carga e validação ──────────────────────────────────────────────────────────────────────

    private fun garantirCarregada() {
        if (carregada) return
        synchronized(trava) {
            if (carregada) return
            val doApp = CopiasDaCarteira.desserializar(prefs()?.getString(CHAVE_CARTEIRA, null))
            carteira = CopiasDaCarteira.fundirTodas(listOf(doApp, lerGlobal()))
            revalidar()
            carregarAvaliacao()
            carregada = true
            Log.i(TAG, "carteira: ${carteira.aplicadas.size} licença(s), ${validas.size} deste carro, ativo=${resumoFlow.value.nivelAtivo?.id}")
        }
    }

    private fun vinDeTeste(): String? =
        if (Portao.testeLiberado) Licenca.normalizarVin(prefs()?.getString(CHAVE_VIN_DE_TESTE, null)) else null

    /** O VIN que vale para verificar: teste > lido agora > guardado. */
    private fun vinParaVerificar(): String? =
        vinDeTeste() ?: vinAoVivo ?: Licenca.normalizarVin(prefs()?.getString(CHAVE_VIN, null))

    @Volatile private var mesmoCarro = false

    /** Chamar com [trava]. */
    private fun revalidar() {
        val vin = vinParaVerificar()
        mesmoCarro = vin != null && (carteira.vinHash == null || carteira.vinHash == Licenca.hashDoVin(vin))
        validas = if (!mesmoCarro) emptySet() else carteira.aplicadas.filter {
            VerificadorDeLicenca.verificar(it.codigo, vin, ChavesPublicas.todas(), Portao.testeLiberado) is VerificadorDeLicenca.Resultado.Valida
        }.mapTo(HashSet()) { it.licenca.serie }
        publicarResumo(vin)
    }

    /** Chamar com [trava]. */
    private fun publicarResumo(vin: String? = vinParaVerificar()) {
        val minhas = carteira.somente(validas)
        val ativo = minhas.nivelAtivo()
        val saldo = ativo?.let { minhas.saldo(it) }
        resumoFlow.value = Resumo(
            vin = vin,
            vinLidoAgora = vinAoVivo != null || vinDeTeste() != null,
            carteira = minhas,
            deOutroCarro = carteira.aplicadas.size - minhas.aplicadas.size,
            nivelAtivo = ativo,
            saldoAtivo = saldo,
            aviso = AvisoDaLicenca.doSaldo(saldo)
        )
    }

    /**
     * O nível que as licenças válidas sustentam, ou null (= Drive). Rápido: memória. Num APK
     * reassinado ([Integridade]) nenhuma vale — a mesma conferência também está no [Portao].
     */
    fun nivelDaLicenca(): Nivel? {
        garantirCarregada()
        if (Integridade.alterado) return null
        nivelFingido?.let { if (Portao.testeLiberado) return it }
        return resumoFlow.value.nivelAtivo
    }

    // ── teste grátis ───────────────────────────────────────────────────────────────────────────

    private val avaliacaoFlow = MutableStateFlow(Avaliacao.Registro())
    private val avisoNaTelaFlow = MutableStateFlow(false)

    /** O registro do teste (contador + o que está correndo). */
    val avaliacao: StateFlow<Avaliacao.Registro>
        get() {
            garantirCarregada()
            return avaliacaoFlow.asStateFlow()
        }

    /**
     * O aviso "pague pelo Max ou volte ao seu plano" deve estar em todas as telas agora: teste além
     * do limite correndo. Observado pelo [AvisoDoTeste] (central), pelo painel e pelo HUD.
     */
    val avisoNaTela: StateFlow<Boolean>
        get() {
            garantirCarregada()
            return avisoNaTelaFlow.asStateFlow()
        }

    /**
     * Id deste boot da central: `Settings.Global.BOOT_COUNT`; sem ele, o `boot_id` do kernel; sem os
     * dois, a hora do boot arredondada ao minuto. No build de teste, o fingido vem antes.
     */
    fun idDoBoot(): String {
        if (Portao.testeLiberado) prefs()?.getString(CHAVE_BOOT_DE_TESTE, null)?.let { return "t:$it" }
        bootDoProcesso?.let { return it }
        return lerIdDoBoot().also { bootDoProcesso = it }
    }

    /** O boot não muda com o processo vivo: lido uma vez. */
    @Volatile private var bootDoProcesso: String? = null

    private fun lerIdDoBoot(): String {
        runCatching { Settings.Global.getInt(DeepGwt.contexto.contentResolver, Settings.Global.BOOT_COUNT) }
            .getOrNull()?.let { return "c:$it" }
        runCatching { java.io.File("/proc/sys/kernel/random/boot_id").readText().trim() }
            .getOrNull()?.takeIf { it.isNotEmpty() }?.let { return "k:$it" }
        return "h:${(System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 60_000}"
    }

    /** Chamar com [trava]. Um teste de outro boot acaba aqui (a central reiniciou). */
    private fun carregarAvaliacao() {
        val lido = Avaliacao.Registro.ler(prefs()?.getString(CHAVE_AVALIACAO, null))
        val conferido = lido.conferir(idDoBoot(), ready = null)
        avaliacaoFlow.value = conferido
        if (conferido != lido) {
            if (lido.boot != null && conferido.boot == null) {
                Log.w(TAG, "teste grátis acabou: a central reiniciou")
                prefs()?.edit()?.putBoolean(CHAVE_FIM_DA_AVALIACAO, true)?.apply()
            }
            gravarAvaliacao(conferido)
        }
        publicarAvisoNaTela()
    }

    private fun gravarAvaliacao(r: Avaliacao.Registro) {
        avaliacaoFlow.value = r
        prefs()?.edit()?.putString(CHAVE_AVALIACAO, r.serializar())?.commit()
        publicarAvisoNaTela()
    }

    private fun publicarAvisoNaTela() {
        val r = avaliacaoFlow.value
        avisoNaTelaFlow.value = r.comAviso && r.ativoNo(idDoBoot()) && Portao.cobrancaAtiva && !Integridade.alterado
    }

    /** O teste grátis está correndo (neste boot). Num APK reassinado, não. */
    fun avaliacaoAtiva(): Boolean {
        garantirCarregada()
        return !Integridade.alterado && avaliacaoFlow.value.ativoNo(idDoBoot())
    }

    /** O que mostrar sobre o teste grátis (tela, instalador, celular). */
    fun estadoDaAvaliacao(): Avaliacao.Estado {
        garantirCarregada()
        return Avaliacao.estado(Portao.cobrancaAtiva, Portao.estado.value.origem, nivelDaLicenca(), avaliacaoFlow.value, idDoBoot())
    }

    /** Só no build de teste: finge uma [Licencas] editada, para ver a [Sentinela] agir. */
    @Volatile private var nivelFingido: Nivel? = null

    // ── o carro ────────────────────────────────────────────────────────────────────────────────

    private fun dado(chave: Sinal): String? =
        runCatching { Veiculo.ler(chave.chave) }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

    private fun readyDeTeste(): Boolean? =
        if (Portao.testeLiberado) prefs()?.getString(CHAVE_READY_DE_TESTE, null)?.let { it == "on" } else null

    /** true/false = o carro disse; null = ainda não se sabe (barramento fora, processo recém-subido). */
    fun readyAgora(): Boolean? {
        readyDeTeste()?.let { return it }
        val v = dado(Sinais.CAR_BASIC_DRIVING_READY_STATE) ?: return null
        return v != "0" && v != "-1"
    }

    private fun servicosAtivos(): Boolean =
        readyDeTeste() != null || runCatching { Veiculo.pronto }.getOrDefault(false)

    /** Só no build de teste: velocidade fingida (km/h), para ver o fim do teste esperar a parada. */
    @Volatile private var velocidadeDeTeste: Float? = null

    private fun parado(): Boolean {
        if (Portao.testeLiberado) velocidadeDeTeste?.let { return it <= VELOCIDADE_PARADO }
        val v = dado(Sinais.CAR_BASIC_VEHICLE_SPEED)?.toFloatOrNull() ?: return readyDeTeste() != null
        return v <= VELOCIDADE_PARADO
    }

    /** O carro está parado agora (velocidade de parado). Sem leitura: não. */
    fun paradoAgora(): Boolean = runCatching { parado() }.getOrDefault(false)

    /** Lê o VIN do barramento agora (ou o de teste). */
    fun lerVinAgora(): String? {
        vinDeTeste()?.let { return it }
        val lido = Licenca.normalizarVin(
            runCatching { Veiculo.lerDoCarro(Sinais.CAR_BASIC_VIN_CODE.chave) }.getOrNull()
                ?: dado(Sinais.CAR_BASIC_VIN_CODE)
        )
        if (lido != null && lido != vinAoVivo) {
            vinAoVivo = lido
            synchronized(trava) {
                // Guarda o VIN só se ele é o das licenças (ou não há licença): um VIN estranho não
                // apaga o que o carro sabia.
                if (carteira.vinHash == null || carteira.vinHash == Licenca.hashDoVin(lido)) {
                    prefs()?.edit()?.putString(CHAVE_VIN, lido)?.apply()
                }
                revalidar()
            }
            Portao.recalcular()
        }
        return lido
    }

    // ── ativar ─────────────────────────────────────────────────────────────────────────────────

    /** Verifica e aplica [codigo]. Bloqueia (Shizuku, disco): chamar fora da thread principal. */
    fun ativar(codigo: String): Result<LicencaAplicada> {
        garantirCarregada()
        // Num app alterado a licença não valeria: melhor dizer agora do que "ativada" e continuar no
        // Drive. O código não é gasto — o app oficial, reinstalado, aceita.
        if (Portao.estado.value.origem == Portao.Origem.ALTERADO) {
            return Result.failure(
                Recusa(
                    MotivoDaRecusa.ALTERADO,
                    "Este DeepGWT foi modificado e não aceita licenças. Reinstale pelo instalador no computador (Instalar e manter → Atualizar) e ative de novo."
                )
            )
        }
        val vin = lerVinAgora()
            ?: return Result.failure(Recusa(MotivoDaRecusa.VIN, "Não deu para ler o VIN do carro agora. Com o carro ligado, tente de novo."))
        val r = VerificadorDeLicenca.verificar(codigo, vin, ChavesPublicas.todas(), Portao.testeLiberado)
        if (r is VerificadorDeLicenca.Resultado.Recusada) return Result.failure(Recusa(r.motivo, r.mensagem, r.linha))
        r as VerificadorDeLicenca.Resultado.Valida

        mesclarCopiasExternas(recalcular = false)
        val aplicada = LicencaAplicada(r.licenca, r.codigo, System.currentTimeMillis())
        synchronized(trava) {
            val hash = Licenca.hashDoVin(vin)
            val base = if (carteira.vinHash != null && carteira.vinHash != hash) Carteira(vinHash = hash) else carteira.copy(vinHash = hash)
            val nova = base.aplicar(aplicada)
                ?: return Result.failure(Recusa(MotivoDaRecusa.REPETIDA, "Esta licença já foi usada neste carro."))
            carteira = nova.copy(geracao = nova.geracao + 1)
            prefs()?.edit()?.putString(CHAVE_VIN, vin)?.apply()
            revalidar()
        }
        Log.w(TAG, "licença ${r.licenca.serie} aplicada: ${r.licenca.nivel.id} ${if (r.licenca.vitalicia) "vitalícia" else "${r.licenca.horas} h"}")
        gravarNoApp()
        gravarCopiasExternas()
        Portao.recalcular()
        return Result.success(aplicada)
    }

    /**
     * Começa o teste grátis do Max. Sem VIN e sem trava: além do [Avaliacao.LIMITE_GRATIS] ele
     * começa igual, com o aviso em todas as telas. Bloqueia (disco): fora da thread principal.
     */
    fun iniciarAvaliacao(): Result<Avaliacao.Estado> {
        garantirCarregada()
        Avaliacao.motivoDeIndisponivel(Portao.cobrancaAtiva, Portao.estado.value.origem, nivelDaLicenca())?.let {
            return Result.failure(Recusa(if (Portao.estado.value.origem == Portao.Origem.ALTERADO) MotivoDaRecusa.ALTERADO else MotivoDaRecusa.AVALIACAO, it))
        }
        val de = Portao.nivel()
        synchronized(trava) {
            val nova = avaliacaoFlow.value.iniciar(idDoBoot(), System.currentTimeMillis(), readyAgora())
                ?: return Result.failure(Recusa(MotivoDaRecusa.AVALIACAO, "O teste do ${Avaliacao.NIVEL.titulo} já está correndo: vale até desligar o carro."))
            prefs()?.edit()?.remove(CHAVE_FIM_DA_AVALIACAO)?.apply()
            gravarAvaliacao(nova)
            Log.w(TAG, "teste grátis nº ${nova.usos} começou${if (nova.comAviso) " (além do limite: com aviso)" else ""}, vindo do ${de.id}")
        }
        Portao.recalcular()
        return Result.success(estadoDaAvaliacao())
    }

    /**
     * Encerra o teste agora ("voltar ao meu plano", ou o power cycle visto pelo tique). Parado ou
     * desligado, o nível cai já; andando, o Portão segura o Max até a primeira parada.
     */
    fun encerrarAvaliacao(motivo: String): Result<Avaliacao.Estado> {
        garantirCarregada()
        synchronized(trava) {
            val r = avaliacaoFlow.value
            if (r.boot == null) return Result.success(estadoDaAvaliacao())
            prefs()?.edit()?.putBoolean(CHAVE_FIM_DA_AVALIACAO, true)?.commit()
            gravarAvaliacao(r.encerrar())
        }
        Log.w(TAG, "teste grátis acabou: $motivo")
        Portao.recalcular()
        handler?.post { runCatching { conferirParada() } }
        return Result.success(estadoDaAvaliacao())
    }

    /** O teste acabou nesta partida e o Max está seguro até parar ([Portao.segurarQueda]). */
    fun avaliacaoAcabouNestaPartida(): Boolean = prefs()?.getBoolean(CHAVE_FIM_DA_AVALIACAO, false) == true

    /** O [Portao] chama quando o nível caiu no fim do teste: o carro está parado, avisa já. */
    fun anunciarFimDaAvaliacao(para: Nivel) {
        prefs()?.edit()?.remove(CHAVE_FIM_DA_AVALIACAO)?.apply()
        val texto = Avaliacao.mensagemDeFim(para)
        Log.w(TAG, "fim do teste grátis: ${para.id}")
        handler?.post { BoasVindas.mostrar(texto, DURACAO_DO_AVISO_MS) }
    }

    // ── cópias ─────────────────────────────────────────────────────────────────────────────────

    private fun lerGlobal(): Carteira? =
        CopiasDaCarteira.desserializar(runCatching { Settings.Global.getString(DeepGwt.contexto.contentResolver, GLOBAL) }.getOrNull())

    private fun lerArquivoExterno(): Carteira? {
        if (!runCatching { Shell.disponivel() }.getOrDefault(false)) return null
        return CopiasDaCarteira.desserializar(runCatching { Shell.executar(arrayOf("cat", ARQUIVO_EXTERNO)) }.getOrNull())
    }

    /** Junta Settings.Global e /data/local/tmp à carteira do app (ex.: depois de limpar os dados). */
    fun mesclarCopiasExternas(recalcular: Boolean = true) {
        garantirCarregada()
        val externas = listOf(lerGlobal(), lerArquivoExterno())
        val mudou = synchronized(trava) {
            val antes = carteira
            carteira = CopiasDaCarteira.fundirTodas(listOf(carteira) + externas)
            if (carteira != antes) {
                revalidar()
                sujaNoApp = true
                true
            } else false
        }
        if (mudou) {
            gravarNoApp()
            if (recalcular) Portao.recalcular()
        }
    }

    /** Grava o que está só na memória (a [Sentinela] chama antes de desconfiar do disco). */
    fun gravarPendencias() {
        if (sujaNoApp) gravarNoApp()
    }

    private fun gravarNoApp() {
        val texto = synchronized(trava) {
            carteira = carteira.copy(geracao = carteira.geracao + 1)
            CopiasDaCarteira.serializar(carteira)
        }
        prefs()?.edit()?.putString(CHAVE_CARTEIRA, texto)?.commit()
        sujaNoApp = false
    }

    private fun gravarCopiasExternas() {
        if (!runCatching { Shell.disponivel() }.getOrDefault(false)) return
        val texto = synchronized(trava) { CopiasDaCarteira.serializar(carteira) }
        runCatching { Shell.executar(arrayOf("settings", "put", "global", GLOBAL, texto)) }
            .onFailure { Log.w(TAG, "cópia no Settings.Global: ${it.message}") }
        runCatching {
            Shell.executar(arrayOf("sh", "-c", "printf %s \"\$1\" > $ARQUIVO_EXTERNO", "deepgwt", texto))
        }.onFailure { Log.w(TAG, "cópia em $ARQUIVO_EXTERNO: ${it.message}") }
        sujaNasCopias = false
    }

    // ── contagem ───────────────────────────────────────────────────────────────────────────────

    @Synchronized
    fun iniciar() {
        if (handler != null) return
        garantirCarregada()
        val thread = HandlerThread("licenca-uso").apply { start() }
        handler = Handler(thread.looper).apply {
            postDelayed({ tique() }, 5_000)
            // O Shizuku costuma subir depois do app no boot.
            postDelayed({ runCatching { mesclarCopiasExternas() } }, 20_000)
        }
    }

    /** Enquanto o Max do teste estiver seguro esperando parar, olha a velocidade a cada 3 s. */
    private fun conferirParada() {
        val e = Portao.estado.value
        if (e.origem != Portao.Origem.AVALIACAO || e.caiPara == null) return
        if (parado() || readyAgora() == false) {
            Portao.recalcular()
            if (Portao.estado.value.caiPara == null) return
        }
        handler?.postDelayed({ runCatching { conferirParada() } }, INTERVALO_DA_PARADA_MS)
    }

    private fun consumir(seg: Long) {
        val esgotou = synchronized(trava) {
            val c = carteira.somente(validas).consumir(seg)
            if (c.consumidoSeg == 0L) return
            carteira = carteira.copy(usoSeg = c.carteira.usoSeg)
            sujaNoApp = true
            sujaNasCopias = true
            publicarResumo()
            c.esgotou
        }
        if (esgotou) {
            Log.w(TAG, "horas do ${resumoFlow.value.carteira.saldos().firstOrNull { !it.temSaldo }?.nivel?.id} acabaram")
            gravarNoApp()
            gravarCopiasExternas()
            // Com o carro ligado o Portão segura o nível até desligar.
            Portao.recalcular()
        }
    }

    private fun conferirAvaliacao(ready: Boolean?) {
        val antes = avaliacaoFlow.value
        if (antes.boot == null) return
        val depois = antes.conferir(idDoBoot(), ready)
        if (depois == antes) return
        if (depois.boot == null) {
            encerrarAvaliacao(if (ready == false) "o carro desligou" else "a central reiniciou")
        } else synchronized(trava) { gravarAvaliacao(depois) }
    }

    private fun tique() {
        executarTique()
        handler?.postDelayed({ tique() }, INTERVALO_MS)
    }

    private fun executarTique() {
        try {
            val ready = readyAgora()
            val estado = Portao.estado.value
            // Com o nível seguro até desligar (horas acabaram andando), nada mais é cobrado: o nível
            // de baixo só começa a contar quando passar a valer. No teste grátis nada conta.
            val contando = ready == true && servicosAtivos() && estado.caiPara == null &&
                estado.origem == Portao.Origem.LICENCA
            val seg = contador.tique(SystemClock.elapsedRealtime(), contando)
            if (seg > 0) consumir(seg)

            // Teste grátis: acaba no power cycle (carro desligou depois de ligado, ou outro boot).
            conferirAvaliacao(ready)

            if (vinAoVivo == null) lerVinAgora()

            if (ready != ultimoReady) {
                if (ready == true) avisadoNestaPartida = false
                if (ready == false) {
                    if (sujaNoApp) gravarNoApp()
                    if (sujaNasCopias) gravarCopiasExternas()
                }
                if (ready == false && avaliacaoAcabouNestaPartida() && Portao.estado.value.origem != Portao.Origem.AVALIACAO) {
                    prefs()?.edit()?.remove(CHAVE_FIM_DA_AVALIACAO)?.apply()
                }
                ultimoReady = ready
            }
            // Queda pendente (horas acabaram com o carro ligado): aplica com ele desligado.
            if (ready != true && Portao.estado.value.caiPara != null) Portao.recalcular()
            // Fim do teste esperando parar (o app pode ter reiniciado no meio): volta a olhar.
            if (Portao.estado.value.origem == Portao.Origem.AVALIACAO && Portao.estado.value.caiPara != null) conferirParada()

            if (ready == true && !avisadoNestaPartida && parado()) {
                avisadoNestaPartida = true
                avisarNaPartida()
            }

            if (tiques % TIQUES_DA_SENTINELA == 0L || (Sentinela.violado && !Sentinela.bloqueando && ready != true)) {
                Sentinela.conferir()
            }

            tiques++
            if (sujaNoApp && tiques % TIQUES_POR_GRAVACAO == 0L) gravarNoApp()
            if (sujaNasCopias && tiques % TIQUES_POR_COPIA == 0L) gravarCopiasExternas()
        } catch (e: Exception) {
            Log.e(TAG, "tique", e)
        }
    }

    // ── avisos ─────────────────────────────────────────────────────────────────────────────────

    /** O [Portao] avisa aqui quando o nível CAIU por falta de horas; a partida seguinte conta. */
    fun registrarQueda(de: Nivel, para: Nivel) {
        prefs()?.edit()?.putString(CHAVE_QUEDA, "${de.id}>${para.id}")?.apply()
    }

    private fun quedaParaAnunciar(): Pair<Nivel, Nivel>? {
        val v = prefs()?.getString(CHAVE_QUEDA, null) ?: return null
        val de = Nivel.deId(v.substringBefore('>')) ?: return null
        val para = Nivel.deId(v.substringAfter('>')) ?: return null
        return de to para
    }

    private fun avisarNaPartida() {
        val origem = Portao.estado.value.origem
        // No teste grátis não se fala das horas das licenças (estão paradas).
        if (origem != Portao.Origem.LICENCA && origem != Portao.Origem.SEM_LICENCA) return
        val queda = quedaParaAnunciar()
        val aviso = AvisoDaLicenca.naPartida(resumoFlow.value.saldoAtivo, queda) ?: return
        if (queda != null) prefs()?.edit()?.remove(CHAVE_QUEDA)?.apply()
        Log.w(TAG, "aviso na partida: ${aviso.tipo.id}")
        handler?.postDelayed({ BoasVindas.mostrar(aviso.texto, DURACAO_DO_AVISO_MS) }, ATRASO_DO_AVISO_MS)
    }

    // ── teste ──────────────────────────────────────────────────────────────────────────────────

    fun cobrancaDeTeste(): Boolean =
        Portao.testeLiberado && prefs()?.getBoolean(CHAVE_COBRANCA_DE_TESTE, false) == true

    /**
     * Ganchos do build de teste (`POST /licenca/teste`): VIN, READY e cobrança fingidos, e minutos
     * de uso de uma vez (para ver o aviso de 10% e o fim das horas sem esperar). Recusa fora dele.
     */
    fun definirTeste(
        vin: String?,
        ready: String?,
        cobranca: Boolean?,
        consumirMinutos: Int?,
        fingirNivel: String? = null,
        rondaDaSentinela: Boolean = false,
        velocidade: Float? = null,
        limparVelocidade: Boolean = false,
        zerarAvaliacao: Boolean = false,
        bootDeTeste: String? = null
    ): Result<Unit> {
        if (!Portao.testeLiberado) return Result.failure(IllegalStateException("só num build de teste do app"))
        fingirNivel?.let { nivelFingido = if (it.isBlank()) null else Nivel.deId(it) }
        if (limparVelocidade) velocidadeDeTeste = null
        velocidade?.let { velocidadeDeTeste = it }
        if (zerarAvaliacao) {
            // O mesmo que reinstalar: contador zerado e nenhum teste correndo.
            synchronized(trava) { gravarAvaliacao(Avaliacao.Registro()) }
            prefs()?.edit()?.remove(CHAVE_FIM_DA_AVALIACAO)?.commit()
        }
        bootDeTeste?.let { b ->
            prefs()?.edit()?.apply { if (b.isBlank()) remove(CHAVE_BOOT_DE_TESTE) else putString(CHAVE_BOOT_DE_TESTE, b) }?.commit()
            // Antes do recálculo abaixo: como na carga real, o fim do teste já sabe que foi reboot.
            conferirAvaliacao(readyAgora())
        }
        val p = prefs() ?: return Result.failure(IllegalStateException("armazenamento indisponível"))
        p.edit().apply {
            vin?.let { if (it.isBlank()) remove(CHAVE_VIN_DE_TESTE) else putString(CHAVE_VIN_DE_TESTE, it.uppercase()) }
            ready?.let { if (it.isBlank()) remove(CHAVE_READY_DE_TESTE) else putString(CHAVE_READY_DE_TESTE, it) }
            cobranca?.let { putBoolean(CHAVE_COBRANCA_DE_TESTE, it) }
        }.commit()
        if (vin != null) {
            vinAoVivo = null
            synchronized(trava) { revalidar() }
        }
        if (consumirMinutos != null && consumirMinutos > 0) {
            if (Portao.estado.value.origem != Portao.Origem.AVALIACAO) consumir(consumirMinutos * 60L)
        }
        if (ready != null && ready != "on") {
            // Desligar de mentira: grava e aplica a queda pendente, como o tique faria.
            gravarNoApp()
            gravarCopiasExternas()
        }
        if (ready == "on") avisadoNestaPartida = false
        Portao.recalcular()
        handler?.post {
            executarTique()
            if (rondaDaSentinela) Sentinela.conferir(confirmarJa = true)
        }
        return Result.success(Unit)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
