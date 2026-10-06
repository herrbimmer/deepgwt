// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.carro

import br.com.deepgwt.nucleo.DeepGwt
import br.com.deepgwt.veiculo.Veiculo
import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import br.com.deepgwt.licenca.Portao
import br.com.deepgwt.licenca.Recurso
import br.com.deepgwt.veiculo.Sinal
import br.com.deepgwt.veiculo.Sinais
import br.com.deepgwt.nucleo.Preferencias
import org.json.JSONObject

/**
 * O que fazer quando um assistente lembrado muda de valor. Puro: testável na JVM.
 *
 * A PERGUNTA É "QUEM MUDOU?"
 *  - Logo depois da partida ([JANELA_DA_PARTIDA_MS]) quem muda é o CARRO: é o reset de fábrica
 *    que religa os avisos a cada partida (queixa nº 1 dos donos do H6). O app devolve o valor
 *    lembrado — no máximo [MAX_REAPLICAR] vezes por partida, para não brigar com um carro que não
 *    aceita o valor.
 *  - Fora dessa janela quem muda é a PESSOA, no menu do carro: o app passa a lembrar o valor novo.
 *    É isso que faz a memória ser "como eu deixei", e não "como eu deixei no dia em que liguei a
 *    função".
 */
object RegraDaMemoria {
    const val JANELA_DA_PARTIDA_MS = 60_000L
    const val MAX_REAPLICAR = 3

    enum class Acao { NADA, GUARDAR, REAPLICAR, DESISTIR }

    /**
     * @param desdeAPartidaMs quanto tempo desde a partida; null = o carro não ligou nesta sessão.
     * @param tentativas quantas vezes esta chave já foi devolvida nesta partida.
     */
    fun aoMudar(valor: String?, salvo: String?, desdeAPartidaMs: Long?, tentativas: Int): Acao {
        val v = valor?.trim().orEmpty()
        if (v.isEmpty()) return Acao.NADA
        if (salvo == null) return Acao.GUARDAR
        if (v == salvo) return Acao.NADA
        if (desdeAPartidaMs != null && desdeAPartidaMs in 0..JANELA_DA_PARTIDA_MS) {
            return if (tentativas < MAX_REAPLICAR) Acao.REAPLICAR else Acao.DESISTIR
        }
        return Acao.GUARDAR
    }

    /** O READY deste barramento: vazio, "-1" e "0" são desligado (mesma regra do ServiceManager). */
    fun readyLigado(valor: String?): Boolean {
        val v = valor?.trim().orEmpty()
        return v.isNotEmpty() && v != "-1" && v != "0"
    }
}

/**
 * "Manter como deixei" para os assistentes de faixa e o aviso de velocidade (Alpha 8.5).
 *
 * A queixa mais repetida dos donos do H6 2023–2025: o carro religa aviso de saída de faixa,
 * manutenção de faixa e aviso de velocidade a cada partida, e é preciso desligar tudo de novo no
 * menu antes de sair. Aqui o app lembra o que a pessoa deixou (no menu do carro ou na tela ADAS do
 * DeepGWT) e devolve depois da partida.
 *
 * O QUE ENTRA E O QUE NÃO ENTRA
 *  - Entram só os avisos e assistentes de FAIXA e de VELOCIDADE ([CHAVES]).
 *  - Frenagem automática (PCS/AEB), pedestre (PPS), frenagem de manobra (MEB), colisão traseira e
 *    abertura de porta ficam de fora por regra: a memória nunca mexe em frenagem.
 *  - A memória não desliga nada sozinha: ela devolve o valor que a PESSOA escolheu. Quem deixar
 *    tudo ligado continua com tudo ligado.
 *
 * Os valores de cada chave (0/1, sensibilidade) são os do próprio carro, guardados crus.
 */
object MemoriaDosAssistentes {
    private const val TAG = "MemoriaDosAssistentes"

    /** Faixa e velocidade. Nenhuma de frenagem — ver o cabeçalho. */
    val CHAVES: List<Sinal> = listOf(
        Sinais.CAR_INTELLIGENT_DRIVING_SETTING_LAS_ENABLE,
        Sinais.CAR_INTELLIGENT_DRIVING_SETTING_LAS_LDW_STATE,
        Sinais.CAR_INTELLIGENT_DRIVING_SETTING_LAS_LKA_STATE,
        Sinais.CAR_INTELLIGENT_DRIVING_SETTING_LAS_ELK_STATE,
        Sinais.CAR_INTELLIGENT_DRIVING_SETTING_LAS_EARLY_WARNING_SENSITIVITY,
        Sinais.CAR_INTELLIGENT_DRIVING_SETTING_EAS_WARNING_WAY,
        Sinais.CAR_INTELLIGENT_DRIVING_SETTING_OVER_SPEED_WARNING_STATE,
        Sinais.CAR_INTELLIGENT_DRIVING_SETTING_OVER_SPEED_ALARM_SENSITIVITY,
        Sinais.CAR_INTELLIGENT_DRIVING_SETTING_LAS_TSI_STATE
    )

    private val CHAVES_TEXTO: Set<String> = CHAVES.map { it.chave }.toSet()

    /** Conferências depois da partida: o módulo ADAS pode acordar e resetar alguns segundos depois. */
    private val CONFERIR_EM_MS = longArrayOf(3_000L, 10_000L, 25_000L, 45_000L)

    private val READY = Sinais.CAR_BASIC_DRIVING_READY_STATE.chave

    // Tudo abaixo só é tocado na [FilaDoCarro].
    private var partidaEmMs: Long? = null
    private var readyAnterior: Boolean? = null
    private val tentativas = HashMap<String, Int>()
    @Volatile private var iniciada = false

    private fun prefs(): SharedPreferences =
        DeepGwt.contextoProtegido.getSharedPreferences("deepgwt_prefs", Context.MODE_PRIVATE)

    private val ouvinte = Veiculo.Ouvinte { key, value ->
        if (key == READY || key in CHAVES_TEXTO) FilaDoCarro.rodar { aoMudar(key, value) }
    }

    /** Guardado numa `val`: o SharedPreferences segura ouvintes por referência fraca. */
    private val aoMudarPreferencia = SharedPreferences.OnSharedPreferenceChangeListener { _, chave ->
        if (chave == Preferencias.ADAS_MEMORIA_ATIVA.chave) FilaDoCarro.rodar { aoLigarOuDesligar() }
    }

    fun ativa(): Boolean =
        prefs().getBoolean(Preferencias.ADAS_MEMORIA_ATIVA.chave, false) && Portao.permite(Recurso.MEMORIA_ADAS)

    /** Chamado uma vez, com os serviços do carro de pé. */
    fun iniciar() {
        if (iniciada) return
        iniciada = true
        runCatching { Veiculo.acompanhar(CHAVES_TEXTO + READY) }
        Veiculo.observar(ouvinte)
        prefs().registerOnSharedPreferenceChangeListener(aoMudarPreferencia)
        FilaDoCarro.rodar {
            val ready = RegraDaMemoria.readyLigado(ler(READY))
            readyAnterior = ready
            if (!ativa()) return@rodar
            if (salvos().isEmpty()) fotografar("início")
            // O app pode ter subido depois da partida (e do reset do carro): trata como partida.
            if (ready) aoLigarOCarro("app subiu com o carro ligado")
        }
    }

    /** A tela e o `/ajustes` ligam por aqui ou pela preferência; os dois caem em [aoLigarOuDesligar]. */
    fun ligar(ligada: Boolean) {
        prefs().edit().putBoolean(Preferencias.ADAS_MEMORIA_ATIVA.chave, ligada).apply()
    }

    private fun aoLigarOuDesligar() {
        if (ativa()) {
            // Ligar = "lembre como está AGORA". Desligar esquece tudo (abaixo), então aqui só há
            // valores guardados se vieram junto de um perfil de motorista — esses valem.
            if (salvos().isEmpty()) fotografar("ligada")
        } else {
            partidaEmMs = null
            prefs().edit().remove(Preferencias.ADAS_MEMORIA_VALORES.chave).apply()
            Log.w(TAG, "Memória dos assistentes desligada e esquecida: o carro volta a decidir sozinho")
        }
    }

    /** A tela ADAS do DeepGWT escreveu: é escolha da pessoa, mesmo dentro da janela da partida. */
    fun aoEscolherNoApp(chave: String, valor: String) {
        if (chave !in CHAVES_TEXTO) return
        FilaDoCarro.rodar { if (ativa()) guardar(chave, valor) }
    }

    fun salvos(): Map<String, String> {
        val texto = prefs().getString(Preferencias.ADAS_MEMORIA_VALORES.chave, null) ?: return emptyMap()
        return runCatching {
            val json = JSONObject(texto)
            json.keys().asSequence().filter { it in CHAVES_TEXTO }.associateWith { json.getString(it) }
        }.getOrDefault(emptyMap())
    }

    private fun gravarSalvos(mapa: Map<String, String>) {
        val json = JSONObject()
        mapa.forEach { (k, v) -> json.put(k, v) }
        prefs().edit().putString(Preferencias.ADAS_MEMORIA_VALORES.chave, json.toString()).apply()
    }

    private fun guardar(chave: String, valor: String) {
        val v = valor.trim()
        if (v.isEmpty()) return
        val mapa = salvos().toMutableMap()
        if (mapa[chave] == v) return
        mapa[chave] = v
        gravarSalvos(mapa)
        Log.w(TAG, "Lembrando $chave=$v")
    }

    private fun ler(chave: String): String? =
        runCatching {
            // Leitura fresca do barramento; sem ControlService (simulador, ou caído), o cache.
            (Veiculo.lerDoCarro(chave) ?: Veiculo.ler(chave))
        }.getOrNull()?.trim()

    private fun fotografar(motivo: String) {
        val mapa = HashMap<String, String>()
        CHAVES_TEXTO.forEach { k -> ler(k)?.takeIf { it.isNotEmpty() }?.let { mapa[k] = it } }
        gravarSalvos(mapa)
        Log.w(TAG, "Fotografia dos assistentes ($motivo): $mapa")
    }

    private fun aoLigarOCarro(motivo: String) {
        partidaEmMs = SystemClock.elapsedRealtime()
        tentativas.clear()
        Log.w(TAG, "Partida ($motivo): conferindo os assistentes lembrados")
        CONFERIR_EM_MS.forEach { atraso -> FilaDoCarro.rodarDepois(atraso) { conferir() } }
    }

    private fun desdeAPartida(): Long? = partidaEmMs?.let { SystemClock.elapsedRealtime() - it }

    /** Depois da partida: devolve o que o carro trocou sem avisar (sem esperar o evento). */
    private fun conferir() {
        if (!ativa()) return
        val desde = desdeAPartida() ?: return
        if (desde > RegraDaMemoria.JANELA_DA_PARTIDA_MS) return
        salvos().forEach { (chave, salvo) ->
            val atual = ler(chave)
            if (!atual.isNullOrEmpty() && atual != salvo) reaplicar(chave, salvo)
        }
    }

    private fun aoMudar(chave: String, valor: String?) {
        if (chave == READY) {
            val ligado = RegraDaMemoria.readyLigado(valor)
            val antes = readyAnterior
            readyAnterior = ligado
            if (ligado && antes == false && ativa()) aoLigarOCarro("READY")
            if (!ligado) partidaEmMs = null
            return
        }
        if (!ativa()) return
        val salvo = salvos()[chave]
        when (RegraDaMemoria.aoMudar(valor, salvo, desdeAPartida(), tentativas[chave] ?: 0)) {
            RegraDaMemoria.Acao.NADA -> Unit
            RegraDaMemoria.Acao.GUARDAR -> guardar(chave, valor.orEmpty())
            RegraDaMemoria.Acao.REAPLICAR -> reaplicar(chave, salvo ?: return)
            RegraDaMemoria.Acao.DESISTIR ->
                Log.w(TAG, "$chave: o carro recusou $salvo ${RegraDaMemoria.MAX_REAPLICAR} vezes nesta partida; desisto até a próxima")
        }
    }

    private fun reaplicar(chave: String, valor: String) {
        val n = (tentativas[chave] ?: 0) + 1
        if (n > RegraDaMemoria.MAX_REAPLICAR) return
        tentativas[chave] = n
        val ok = runCatching { Veiculo.escreverConferindo(chave, valor) }.getOrDefault(false)
        Log.w(TAG, "Devolvendo $chave=$valor (tentativa $n): ${if (ok) "enviado" else "falhou"}")
    }

    /** Resumo para a tela: quantos assistentes estão lembrados. */
    fun resumo(): String {
        if (!ativa()) return "Desligada: o carro decide a cada partida."
        val n = salvos().size
        return if (n == 0) "Ligada, esperando ler os assistentes do carro." else "Ligada: $n ajustes lembrados."
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
