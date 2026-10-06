// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.managers

import br.com.deepgwt.nucleo.DeepGwt
import br.com.deepgwt.veiculo.Comandos
import br.com.deepgwt.veiculo.Veiculo
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import br.com.deepgwt.models.DriverProfile
import br.com.deepgwt.nucleo.Preferencias
import br.com.deepgwt.perfis.BoasVindas
import br.com.deepgwt.perfis.EscopoDoPerfil
import br.com.deepgwt.perfis.ReconciliadorDeAjustes
import br.com.deepgwt.perfis.RelatorioDeAplicacao
import br.com.deepgwt.perfis.ValorDePreferencia
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Perfis de motorista: o carro se adapta a quem senta, não o contrário.
 *
 * O QUE UM PERFIL GUARDA
 *
 * Tudo o que a pessoa configurou no DeepGWT — por varredura do `deepgwt_prefs`, não por lista (ver
 * [EscopoDoPerfil]) — mais as configurações do próprio veículo que o barramento expõe como
 * configuração, mais a memória de banco do carro. Como a captura é varredura, um ajuste criado
 * no app depois desta linha ser escrita já nasce dentro dos perfis, sem ninguém vir aqui.
 *
 * ONDE FICA GRAVADO
 *
 * No mesmo `deepgwt_prefs` de todo o resto, um JSON por perfil, com um índice de nomes. Nada de
 * banco de dados novo: o app inteiro é plano assim, e um perfil precisa estar legível no mesmo
 * lugar (e com a mesma criptografia de dispositivo) que as preferências que ele restaura.
 *
 * O QUE ELE NÃO FAZ
 *
 * Não move banco, não dobra retrovisor e não mexe em nada que atue numa peça — [EscopoDoPerfil]
 * derruba essas chaves antes de chegarem aqui. A posição do banco viaja pela memória do próprio
 * carro, que só executa quando o carro julga seguro.
 */
object DriverProfileManager {
    private const val TAG = "DriverProfileManager"
    private const val PROFILE_KEY_PREFIX = "driverProfile_"
    private const val INDEX_KEY = "driverProfileNames"

    /** Encoder tolerante: um perfil gravado por uma versão mais nova não pode derrubar esta. */
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * O último relatório de aplicação, para a tela conseguir mostrar o que falhou depois que o
     * aviso na tela já sumiu. Só memória — não sobrevive a um reinício, e não precisa.
     */
    @Volatile
    var ultimoRelatorio: RelatorioDeAplicacao? = null
        private set

    private fun prefs() =
        DeepGwt.contextoProtegido.getSharedPreferences("deepgwt_prefs", Context.MODE_PRIVATE)

    private fun keyFor(name: String) = "$PROFILE_KEY_PREFIX$name"

    fun listProfiles(): List<DriverProfile> =
        prefs().getStringSet(INDEX_KEY, emptySet()).orEmpty()
            .mapNotNull { loadProfile(it) }
            .sortedBy { it.name.lowercase() }

    fun loadProfile(name: String): DriverProfile? {
        val raw = prefs().getString(keyFor(name), null) ?: return null
        return try {
            json.decodeFromString<DriverProfile>(raw)
        } catch (e: Exception) {
            Log.e(TAG, "Perfil \"$name\" ilegível", e)
            null
        }
    }

    fun deleteProfile(name: String) {
        val names = prefs().getStringSet(INDEX_KEY, emptySet()).orEmpty().toMutableSet()
        names.remove(name)
        prefs().edit {
            remove(keyFor(name))
            putStringSet(INDEX_KEY, names)
        }
        if (getActiveProfileName() == name) setActiveProfile(null)
    }

    // ══════════════════════════════════════════════════════════════════════════════════════
    // Capturar
    // ══════════════════════════════════════════════════════════════════════════════════════

    /**
     * Retrato do estado atual: preferências do DeepGWT + configurações do veículo.
     *
     * A leitura do carro é UMA chamada em lote ([ServiceManager.fetchAllCarConstants]) e não uma
     * por chave: são ~250 chaves de configuração e, uma a uma, o binder leva segundos com o
     * carro ligado. Se o serviço de controle não estiver no ar, a parte do veículo sai vazia e o
     * perfil ainda vale — o lado DeepGWT não depende do carro.
     */
    fun capturar(nome: String, seatMemorySlot: Int?): DriverProfile {
        val agora = System.currentTimeMillis()
        val anterior = loadProfile(nome)

        val doApp = LinkedHashMap<String, String>()
        for ((chave, valor) in prefs().all) {
            if (!EscopoDoPerfil.preferenciaPertence(chave)) continue
            ValorDePreferencia.codificar(valor)?.let { doApp[chave] = it }
        }

        val doCarro = LinkedHashMap<String, String>()
        val lidoDoCarro = runCatching { Veiculo.lerTudoDoCarro() }
            .onFailure { Log.w(TAG, "Sem barramento para capturar o veículo: ${it.message}") }
            .getOrNull() ?: emptyMap()
        for ((chave, valor) in lidoDoCarro) {
            if (!EscopoDoPerfil.chaveDoVeiculoPertence(chave)) continue
            val limpo = valor?.trim().orEmpty()
            if (limpo.isEmpty() || limpo == "null") continue
            doCarro[chave] = limpo
        }

        return DriverProfile(
            name = nome,
            seatMemorySlot = seatMemorySlot,
            settings = emptyMap(),
            deepGwt = doApp,
            veiculo = doCarro,
            criadoEm = anterior?.criadoEm?.takeIf { it > 0 } ?: agora,
            atualizadoEm = agora
        )
    }

    /** Captura e grava. O slot de banco, quando informado, guarda a posição ATUAL no carro. */
    fun saveCurrentAsProfile(name: String, seatMemorySlot: Int?) {
        seatMemorySlot?.let {
            runCatching { Comandos.gravarMemoriaDoBanco(it) }
                .onFailure { e -> Log.w(TAG, "Memória de banco não gravada: ${e.message}") }
        }
        val perfil = capturar(name, seatMemorySlot)
        val names = prefs().getStringSet(INDEX_KEY, emptySet()).orEmpty().toMutableSet()
        names.add(name)
        prefs().edit {
            putString(keyFor(name), json.encodeToString(perfil))
            putStringSet(INDEX_KEY, names)
        }
        Log.w(
            TAG,
            "Perfil \"$name\" gravado: ${perfil.deepGwt.size} ajustes do DeepGWT, " +
                "${perfil.veiculo.size} do veículo"
        )
    }

    // ══════════════════════════════════════════════════════════════════════════════════════
    // Aplicar
    // ══════════════════════════════════════════════════════════════════════════════════════

    /**
     * Aplica um perfil inteiro e conta o que não foi.
     *
     * A ORDEM IMPORTA
     *
     *   1. Todas as preferências numa ÚNICA transação. Não é preciosismo: o painel virtual e a
     *      barra leem várias chaves relacionadas entre si, e gravar em duas levas deixaria uma
     *      janela em que o tema novo já está escolhido mas a cor dele ainda é a do perfil
     *      anterior — o "estado parcialmente aplicado" que o pedido manda evitar. O Android
     *      aplica o lote inteiro na memória antes de avisar qualquer ouvinte, então ninguém
     *      enxerga o meio do caminho.
     *   2. O carimbo de recarga do tema vai na mesma transação, e é ele que faz o painel
     *      recarregar sozinho (o painel, br.com.deepgwt.painel, escuta essa chave).
     *   3. Só então os subsistemas releem ([ReconciliadorDeAjustes]) — já com tudo no lugar.
     *   4. O veículo por último, porque é a parte lenta e a única que pode ser recusada: com ela
     *      no fim, uma recusa do carro não atrasa a parte visível.
     */
    fun aplicar(nome: String): RelatorioDeAplicacao {
        val perfil = loadProfile(nome)
            ?: return RelatorioDeAplicacao(nome, erroFatal = "perfil não encontrado")
                .also { ultimoRelatorio = it }

        val ilegiveis = mutableListOf<String>()
        val avisos = mutableListOf<String>()
        var aplicadas = 0

        if (perfil.deepGwt.isEmpty()) {
            avisos.add(
                "Este perfil é do formato antigo: ele guarda só configurações do carro, nenhum " +
                    "ajuste do DeepGWT. Use \"Atualizar com o estado atual\" para ele passar a " +
                    "guardar tudo."
            )
        } else {
            val editor = prefs().edit()
            for ((chave, texto) in perfil.deepGwt) {
                if (!EscopoDoPerfil.preferenciaPertence(chave)) continue
                if (ValorDePreferencia.gravar(editor, chave, texto)) aplicadas++ else ilegiveis.add(chave)
            }

            // Ajuste que existe HOJE no carro e não existe no perfil volta ao padrão.
            //
            // Sem isto o perfil não representaria o motorista, e sim a soma dele com quem usou o
            // carro antes: quem deixou uma opção ligada a deixaria ligada para todo mundo, porque
            // uma opção no padrão costuma nem estar gravada (o app lê `getBoolean(chave, false)`),
            // e portanto não estaria no perfil de ninguém para desligá-la de volta.
            //
            // Remover devolve ao padrão — nunca a um valor errado. O efeito colateral aceito é
            // que um ajuste criado DEPOIS de o perfil ser salvo volta ao padrão ao trocar de
            // perfil; é o preço de "o perfil é a configuração completa", e o relatório conta
            // quantos foram para o motorista não descobrir sozinho.
            var voltaramAoPadrao = 0
            for (chave in prefs().all.keys) {
                if (!EscopoDoPerfil.preferenciaPertence(chave)) continue
                if (perfil.deepGwt.containsKey(chave)) continue
                editor.remove(chave)
                voltaramAoPadrao++
            }
            if (voltaramAoPadrao > 0) {
                avisos.add(
                    "$voltaramAoPadrao ${if (voltaramAoPadrao == 1) "ajuste voltou" else "ajustes voltaram"} " +
                        "ao padrão de fábrica do DeepGWT: não existiam quando este perfil foi salvo."
                )
            }

            // Carimbo de recarga do painel, na mesma transação. Ver br.com.deepgwt.painel.
            editor.putLong(Preferencias.THEME_RELOAD_NONCE.chave, System.currentTimeMillis())
            editor.apply()
        }

        val falhas = ReconciliadorDeAjustes.reconciliarTudo()

        // ── Veículo ────────────────────────────────────────────────────────────────────────
        var jaCerto = 0
        var mudou = 0
        val recusadas = mutableListOf<String>()
        val alvo = perfil.chavesDoVeiculo()
        if (alvo.isNotEmpty()) {
            val atual = runCatching { Veiculo.lerTudoDoCarro() }
                .onFailure { Log.w(TAG, "Sem barramento para aplicar o veículo: ${it.message}") }
                .getOrNull()
            if (atual == null) {
                avisos.add("O carro não respondeu: as ${alvo.size} configurações do veículo ficaram como estavam.")
            } else {
                for ((chave, valor) in alvo) {
                    if (!EscopoDoPerfil.chaveDoVeiculoPertence(chave)) continue
                    if (valor.isEmpty()) continue
                    // Escrever só o que mudou. A diferença costuma ser de meia dúzia de chaves, e
                    // reenviar 250 valores idênticos ao barramento é trabalho para o carro
                    // rejeitar ou, pior, para ele aceitar duas vezes.
                    if (atual[chave]?.trim() == valor) { jaCerto++; continue }
                    val ok = runCatching {
                        Veiculo.escreverConferindo(chave, valor)
                    }.getOrDefault(false)
                    if (ok) mudou++ else recusadas.add(chave)
                }
            }
        }

        perfil.seatMemorySlot?.let { slot ->
            val ok = runCatching { Comandos.chamarMemoriaDoBanco(slot); true }
                .getOrDefault(false)
            if (!ok) avisos.add("A memória de banco $slot não foi chamada: o carro não aceitou agora.")
        }

        setActiveProfile(nome)

        val relatorio = RelatorioDeAplicacao(
            perfil = nome,
            preferenciasAplicadas = aplicadas,
            preferenciasIlegiveis = ilegiveis,
            veiculoJaEstavaCerto = jaCerto,
            veiculoAplicado = mudou,
            veiculoRecusado = recusadas,
            reconciliacoesFalhas = falhas,
            avisos = avisos
        )
        ultimoRelatorio = relatorio
        Log.w(TAG, relatorio.resumo())
        return relatorio
    }

    /** Assinatura antiga, mantida para quem já chamava. */
    fun applyProfile(name: String) {
        aplicar(name)
    }

    // ══════════════════════════════════════════════════════════════════════════════════════
    // Perfil ativo e partida do carro
    // ══════════════════════════════════════════════════════════════════════════════════════

    fun getActiveProfileName(): String? =
        prefs().getString(Preferencias.ACTIVE_DRIVER_PROFILE.chave, null)

    fun setActiveProfile(name: String?) {
        prefs().edit {
            if (name == null) remove(Preferencias.ACTIVE_DRIVER_PROFILE.chave)
            else putString(Preferencias.ACTIVE_DRIVER_PROFILE.chave, name)
        }
    }

    fun isAutoRecallEnabled(): Boolean =
        prefs().getBoolean(Preferencias.AUTO_RECALL_SEAT_MEMORY.chave, false)

    fun setAutoRecallEnabled(enabled: Boolean) {
        prefs().edit { putBoolean(Preferencias.AUTO_RECALL_SEAT_MEMORY.chave, enabled) }
    }

    /**
     * Reaplicar o perfil ativo INTEIRO ao ligar o carro.
     *
     * Vem desligado. O perfil ativo já está aplicado — ele foi aplicado quando o motorista o
     * escolheu, e o SharedPreferences sobrevive ao desligamento —, então ligar isto é para quem
     * divide o carro com alguém que mexe nos ajustes do próprio carro pelo menu da Haval: aí sim
     * o barramento volta diferente do que o perfil diz, e reaplicar conserta.
     */
    fun aplicaPerfilAoLigar(): Boolean =
        prefs().getBoolean(Preferencias.APLICAR_PERFIL_AO_LIGAR.chave, false)

    fun setAplicaPerfilAoLigar(ligado: Boolean) {
        prefs().edit { putBoolean(Preferencias.APLICAR_PERFIL_AO_LIGAR.chave, ligado) }
    }

    /**
     * Chamado pelo ServiceManager quando o carro liga (parado, em P).
     *
     * Três coisas, nesta ordem e cada uma opcional por conta própria: a saudação, a memória de
     * banco e — só se pedido — o perfil inteiro. Nenhuma delas pode derrubar o evento de partida,
     * que é o mesmo que religa Bluetooth e ar-condicionado.
     */
    fun aoLigarOCarro() {
        val nome = getActiveProfileName()

        if (nome != null) {
            runCatching { BoasVindas.saudar(nome) }
                .onFailure { Log.w(TAG, "Saudação não exibida: ${it.message}") }
        }

        if (nome != null && aplicaPerfilAoLigar()) {
            // Numa thread própria: este método roda no despachante de eventos do barramento, o
            // mesmo que religa Bluetooth e ar-condicionado na partida. Aplicar um perfil lê as
            // ~800 chaves do carro e escreve as que mudaram — segurar esse despachante por
            // segundos atrasaria todo o resto da partida.
            Thread({
                runCatching { aplicar(nome) }
                    .onFailure { Log.e(TAG, "Perfil não reaplicado na partida", it) }
            }, "PerfilNaPartida").start()
            return // aplicar() já chama a memória de banco.
        }

        autoApplySeatMemoryOnPowerOn()
    }

    /**
     * Só a POSIÇÃO DO BANCO do perfil ativo, quando o interruptor correspondente está ligado.
     * Espelha exatamente o "Aplicar" manual; no-op silencioso se desligado / sem perfil / sem slot.
     */
    fun autoApplySeatMemoryOnPowerOn() {
        if (!isAutoRecallEnabled()) return
        val name = getActiveProfileName() ?: return
        val slot = loadProfile(name)?.seatMemorySlot ?: return
        runCatching { Comandos.chamarMemoriaDoBanco(slot) }
            .onFailure { Log.w(TAG, "Memória de banco não chamada na partida: ${it.message}") }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
