// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.carro

import br.com.deepgwt.nucleo.Shell
import br.com.deepgwt.nucleo.DeepGwt
import android.content.Context
import android.content.SharedPreferences
import android.database.ContentObserver
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import br.com.deepgwt.licenca.Portao
import br.com.deepgwt.licenca.Recurso
import br.com.deepgwt.macros.MacroFeedback
import br.com.deepgwt.managers.ToqueDoVolante
import br.com.deepgwt.nucleo.Preferencia
import br.com.deepgwt.nucleo.Preferencias
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Reprogramar os dois botões coringa do volante (Alpha 8.5).
 *
 * COMO FUNCIONA
 *  - Botão com uma ação do DeepGWT: o app guarda a função que a pessoa escolheu no menu do carro e
 *    grava 99 no `bean_sw_custom_keyN_config` ([PlanoDoCoringa]). Sem função de fábrica, o
 *    `inputservice` entrega a tecla do botão ao app ([aoReceberTecla]), que roda a ação.
 *  - Desligar a reprogramação (ou voltar o botão para "função do carro", ou o nível deixar de
 *    liberar) devolve exatamente a função guardada.
 *  - Reprogramação desligada e botão nunca tomado: o app não lê nem grava nada, e a tecla passa
 *    reto por [aoReceberTecla] (devolve false na primeira linha).
 *
 * AS TECLAS
 * 517 (botão 1) e 1031 (botão 2) são as medidas no H6 GT. Outra central pode
 * entregar outras: a tela tem "Aprender", que grava a próxima tecla desconhecida que chegar com o
 * botão já tomado. Na Alpha 6.x o HUD tentou aprender o coringa COM a função de fábrica ativa e não
 * chegou tecla nenhuma — por isso aprender aqui só vale com o botão tomado (99).
 *
 * REMOVER O APP
 * Enquanto um botão está tomado, a função guardada também fica em [ARQUIVO_DO_INSTALADOR] (via
 * Shizuku). O "Remover" do instalador lê esse arquivo e devolve as funções antes do
 * `pm uninstall`; sem isso o botão ficaria sem função até a pessoa escolher de novo no menu.
 */
object CoringasDoVolante {
    private const val TAG = "CoringasDoVolante"

    const val TECLA_PADRAO_1 = 517
    const val TECLA_PADRAO_2 = 1031

    /** Lido pelo script de remoção do instalador (root). Uma linha `chave=valor` por botão. */
    const val ARQUIVO_DO_INSTALADOR = "/data/local/tmp/.deepgwt_coringas"

    /** Teclas que já têm dono (direcionais, OK, voltar, casa, segurar) — nunca viram coringa. */
    private val TECLAS_CONHECIDAS = setOf(1024, 1025, 1026, 1027, 1028, 1029, 1030, 1033, 1034, 1037, 1039)

    private const val TEMPO_PARA_APRENDER_MS = 20_000L
    private const val TENTAR_DE_NOVO_MS = 15_000L
    private const val MAX_TENTATIVAS = 20

    /** Um botão: a chave do carro e as preferências dele. */
    private class Botao(
        val numero: Int,
        val chaveDoCarro: String,
        val acao: Preferencia,
        val tecla: Preferencia,
        val teclaPadrao: Int,
        val tomado: Preferencia,
        val original: Preferencia
    )

    private val BOTOES = listOf(
        Botao(
            1, "bean_sw_custom_key1_config", Preferencias.CORINGA1_ACAO, Preferencias.CORINGA1_TECLA,
            TECLA_PADRAO_1, Preferencias.CORINGA1_TOMADO, Preferencias.CORINGA1_ORIGINAL
        ),
        Botao(
            2, "bean_sw_custom_key2_config", Preferencias.CORINGA2_ACAO, Preferencias.CORINGA2_TECLA,
            TECLA_PADRAO_2, Preferencias.CORINGA2_TOMADO, Preferencias.CORINGA2_ORIGINAL
        )
    )

    private val PREFERENCIAS_QUE_MEXEM: Set<String> =
        (BOTOES.map { it.acao.chave } + Preferencias.CORINGAS_ATIVOS.chave).toSet()

    // ── Estado que a tela observa ──────────────────────────────────────────────────────────────
    /** Última tecla que o `inputservice` entregou (qualquer uma): diagnóstico na tela. */
    private val _ultimaTecla = MutableStateFlow<Int?>(null)
    val ultimaTecla: StateFlow<Int?> = _ultimaTecla

    /** Botão aprendendo a tecla agora (1 ou 2), ou null. */
    private val _aprendendo = MutableStateFlow<Int?>(null)
    val aprendendo: StateFlow<Int?> = _aprendendo

    /** Uma linha por botão com o que o app fez com a função do carro. */
    private val _situacao = MutableStateFlow("")
    val situacao: StateFlow<String> = _situacao

    private var aprenderAte = 0L
    /** A tecla recém-aprendida e quando: o SOLTOU do mesmo aperto não pode virar um toque. */
    @Volatile private var teclaAprendida = -1
    @Volatile private var aprendidaEm = 0L
    private val toque = ToqueDoVolante()
    @Volatile private var iniciada = false
    private var tentativas = 0

    private fun prefs(): SharedPreferences =
        DeepGwt.contextoProtegido.getSharedPreferences("deepgwt_prefs", Context.MODE_PRIVATE)

    /** Guardado numa `val`: o SharedPreferences segura ouvintes por referência fraca. */
    private val aoMudarPreferencia = SharedPreferences.OnSharedPreferenceChangeListener { _, chave ->
        if (chave in PREFERENCIAS_QUE_MEXEM) reconciliar("preferência $chave")
    }

    private val observador = object : ContentObserver(FilaDoCarro.handler) {
        override fun onChange(selfChange: Boolean) {
            // O menu do carro (ou o reset dele) mexeu na função: só interessa com o botão tomado.
            if (BOTOES.any { prefs().getBoolean(it.tomado.chave, false) }) reconciliar("menu do carro")
        }
    }

    fun ativos(): Boolean = prefs().getBoolean(Preferencias.CORINGAS_ATIVOS.chave, false)

    fun escolha(numero: Int): EscolhaDoCoringa =
        EscolhaDoCoringa.deJson(prefs().getString(BOTOES[numero - 1].acao.chave, null))

    fun tecla(numero: Int): Int = BOTOES[numero - 1].let { prefs().getInt(it.tecla.chave, it.teclaPadrao) }

    fun tomado(numero: Int): Boolean = prefs().getBoolean(BOTOES[numero - 1].tomado.chave, false)

    private fun desejado(b: Botao): Boolean =
        ativos() && Portao.permite(Recurso.CORINGAS) && escolha(b.numero).acao != AcaoDoCoringa.CARRO

    /** Chamado uma vez, com os serviços do carro de pé. */
    fun iniciar() {
        if (iniciada) return
        iniciada = true
        prefs().registerOnSharedPreferenceChangeListener(aoMudarPreferencia)
        BOTOES.forEach { b ->
            runCatching {
                DeepGwt.contexto.contentResolver.registerContentObserver(
                    Settings.System.getUriFor(b.chaveDoCarro), false, observador
                )
            }.onFailure { Log.w(TAG, "Sem observar ${b.chaveDoCarro}: ${it.message}") }
        }
        reconciliar("início")
    }

    fun ligar(ligados: Boolean) {
        prefs().edit().putBoolean(Preferencias.CORINGAS_ATIVOS.chave, ligados).apply()
    }

    fun escolher(numero: Int, escolha: EscolhaDoCoringa) {
        prefs().edit().putString(BOTOES[numero - 1].acao.chave, escolha.toJson()).apply()
    }

    /** Põe a função do carro de volta onde precisar e tira onde precisar. Idempotente. */
    fun reconciliar(motivo: String) {
        FilaDoCarro.rodar { reconciliarAgora(motivo) }
    }

    private fun reconciliarAgora(motivo: String) {
        // Primeiro sem Shizuku nenhum: se nenhum botão é desejado nem está tomado, não há nada a
        // ler nem a gravar — é a promessa de não interferir.
        val trabalho = BOTOES.filter { desejado(it) || prefs().getBoolean(it.tomado.chave, false) }
        if (trabalho.isEmpty()) {
            _situacao.value = ""
            return
        }
        if (!Shell.disponivel()) {
            _situacao.value = "Esperando o Shizuku para mexer nos botões do carro."
            tentarDeNovo(motivo)
            return
        }
        var falhou = false
        trabalho.forEach { b ->
            val ok = runCatching { reconciliarBotao(b) }
                .onFailure { Log.e(TAG, "Coringa ${b.numero} ($motivo)", it) }
                .getOrDefault(false)
            if (!ok) falhou = true
        }
        atualizarArquivoDoInstalador()
        _situacao.value = BOTOES.joinToString("\n") { descreverBotao(it) }.trim()
        if (falhou) tentarDeNovo(motivo) else tentativas = 0
    }

    private fun tentarDeNovo(motivo: String) {
        if (tentativas >= MAX_TENTATIVAS) return
        tentativas++
        FilaDoCarro.rodarDepois(TENTAR_DE_NOVO_MS) { reconciliarAgora("de novo: $motivo") }
    }

    private fun descreverBotao(b: Botao): String {
        val tomado = prefs().getBoolean(b.tomado.chave, false)
        if (!tomado) return ""
        val original = prefs().getString(b.original.chave, null)
        return "Botão ${b.numero}: função do carro guardada (${original ?: "nenhuma"}); volta quando desligar."
    }

    private fun lerDoCarro(chave: String): String? {
        val saida = Shell.executar(arrayOf("settings", "get", "system", chave))
        if (saida.isNullOrBlank()) throw IllegalStateException("settings get $chave sem resposta")
        return PlanoDoCoringa.normalizar(saida)
    }

    private fun gravarNoCarro(chave: String, valor: String?) {
        if (valor == null) Shell.executar(arrayOf("settings", "delete", "system", chave))
        else Shell.executar(arrayOf("settings", "put", "system", chave, valor))
    }

    /** @return true quando o carro ficou como devia (conferido lendo de volta). */
    private fun reconciliarBotao(b: Botao): Boolean {
        val tomado = prefs().getBoolean(b.tomado.chave, false)
        val original = PlanoDoCoringa.normalizar(prefs().getString(b.original.chave, null))
        val desejado = desejado(b)
        val atual = if (desejado) lerDoCarro(b.chaveDoCarro) else null
        when (val passo = PlanoDoCoringa.planejar(desejado, PlanoDoCoringa.Estado(tomado, original), atual)) {
            PlanoDoCoringa.Passo.Nada -> return true
            is PlanoDoCoringa.Passo.Tomar -> {
                if (!PlanoDoCoringa.valorSeguro(passo.original)) {
                    Log.w(TAG, "Coringa ${b.numero}: valor do carro inesperado (${passo.original}); não mexo")
                    return true
                }
                // Guarda ANTES de gravar 99 (commit, não apply): se o app cair entre os dois, a
                // função do carro não se perde.
                prefs().edit()
                    .putString(b.original.chave, passo.original ?: PlanoDoCoringa.AUSENTE)
                    .putBoolean(b.tomado.chave, true)
                    .commit()
                gravarNoCarro(b.chaveDoCarro, PlanoDoCoringa.DESATIVADO)
                Log.w(TAG, "Coringa ${b.numero}: função do carro ${passo.original} guardada; botão com o DeepGWT")
            }
            is PlanoDoCoringa.Passo.Retomar -> {
                if (!PlanoDoCoringa.valorSeguro(passo.novoOriginal)) return true
                prefs().edit().putString(b.original.chave, passo.novoOriginal ?: PlanoDoCoringa.AUSENTE).commit()
                gravarNoCarro(b.chaveDoCarro, PlanoDoCoringa.DESATIVADO)
                Log.w(TAG, "Coringa ${b.numero}: função trocada no menu do carro (${passo.novoOriginal}); guardada, botão segue com o DeepGWT")
            }
            is PlanoDoCoringa.Passo.Devolver -> {
                gravarNoCarro(b.chaveDoCarro, passo.valor)
                val lido = lerDoCarro(b.chaveDoCarro)
                if (lido != passo.valor) {
                    Log.w(TAG, "Coringa ${b.numero}: devolvi ${passo.valor} mas o carro tem $lido; tento de novo")
                    return false
                }
                prefs().edit().remove(b.original.chave).putBoolean(b.tomado.chave, false).commit()
                Log.w(TAG, "Coringa ${b.numero}: função do carro ${passo.valor} devolvida")
                return true
            }
        }
        return lerDoCarro(b.chaveDoCarro) == PlanoDoCoringa.DESATIVADO
    }

    /** O arquivo que o "Remover" do instalador usa para devolver as funções. */
    private fun atualizarArquivoDoInstalador() {
        val linhas = BOTOES.filter { prefs().getBoolean(it.tomado.chave, false) }.mapNotNull { b ->
            val original = prefs().getString(b.original.chave, null) ?: return@mapNotNull null
            if (!PlanoDoCoringa.valorSeguro(PlanoDoCoringa.normalizar(original))) return@mapNotNull null
            "${b.chaveDoCarro}=$original"
        }
        runCatching {
            if (linhas.isEmpty()) {
                Shell.executar(arrayOf("rm", "-f", ARQUIVO_DO_INSTALADOR))
            } else {
                val conteudo = linhas.joinToString("\n") + "\n"
                Shell.executar(arrayOf("sh", "-c", "printf '%s' \"\$1\" > $ARQUIVO_DO_INSTALADOR", "coringas", conteudo))
            }
        }.onFailure { Log.w(TAG, "Arquivo do instalador não atualizado: ${it.message}") }
    }

    // ── Volante ───────────────────────────────────────────────────────────────────────────────

    /** Começa a aprender a tecla do botão [numero]: a próxima tecla desconhecida que chegar. */
    fun aprender(numero: Int) {
        aprenderAte = SystemClock.elapsedRealtime() + TEMPO_PARA_APRENDER_MS
        _aprendendo.value = numero
    }

    fun pararDeAprender() {
        _aprendendo.value = null
    }

    fun voltarTeclaPadrao(numero: Int) {
        prefs().edit().remove(BOTOES[numero - 1].tecla.chave).apply()
    }

    /**
     * Chamado pelo ouvinte do `inputservice` (ServiceManager) para TODA tecla, antes de qualquer
     * outro. Devolve true só quando a tecla é de um coringa reprogramado (ou está sendo aprendida).
     */
    fun aoReceberTecla(codigo: Int, acao: Int): Boolean {
        _ultimaTecla.value = codigo
        val aprendendoAgora = _aprendendo.value
        if (aprendendoAgora != null) {
            if (SystemClock.elapsedRealtime() > aprenderAte) {
                _aprendendo.value = null
            } else if (codigo !in TECLAS_CONHECIDAS) {
                val b = BOTOES[aprendendoAgora - 1]
                prefs().edit().putInt(b.tecla.chave, codigo).apply()
                teclaAprendida = codigo
                aprendidaEm = SystemClock.elapsedRealtime()
                _aprendendo.value = null
                Log.w(TAG, "Coringa ${b.numero} aprendido: tecla $codigo")
                MacroFeedback.mostrar("Botão ${b.numero} aprendido")
                return true
            }
        }
        if (codigo == teclaAprendida && SystemClock.elapsedRealtime() - aprendidaEm < 1_500L) return true
        if (!ativos()) return false
        val b = BOTOES.firstOrNull { tecla(it.numero) == codigo } ?: return false
        if (!prefs().getBoolean(b.tomado.chave, false)) return false
        if (!toque.ehNovoToque(codigo, acao, SystemClock.elapsedRealtime())) return true
        val escolha = escolha(b.numero)
        if (escolha.acao == AcaoDoCoringa.CARRO || !Portao.permite(Recurso.CORINGAS)) return false
        FilaDoCarro.rodar {
            val texto = ExecutorDoCoringa.executar(escolha)
            if (texto != null) MacroFeedback.mostrar(texto)
        }
        return true
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
