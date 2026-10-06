// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.monitores

/**
 * Leitura dos aparelhos pelo log da central (D.5a): rede de segurança para quando o binder falha.
 * É um retrato do instante em que a central fundiu os aparelhos, não o estado de agora.
 */
internal object ParserDeLogDoDcm {

    private const val MARCA_DA_LINHA = "mergeDevices"
    private const val MARCA_DO_APARELHO = "Device{"

    private fun texto(campo: String) = Regex("(?<![A-Za-z0-9_])$campo='([^']*)'")
    private fun inteiro(campo: String) = Regex("(?<![A-Za-z0-9_])$campo=(-?\\d+)")
    private fun chaves(campo: String) = Regex("(?<![A-Za-z0-9_])$campo=\\{([^}]*)\\}")

    private val id = texto("mId")
    private val enderecoBt = texto("mBtAddr")
    private val nome = texto("mFriendlyName")
    private val serialUsb = texto("mUsbSerialNumber")
    private val capacidadesDisponiveis = inteiro("mAvailableCapability")
    private val capacidadeAtiva = inteiro("mActiveCapability")
    private val estadoDoAparelho = inteiro("mDeviceConnectedState")
    private val estadoPorTipo = chaves("mConnectedTypeState")
    private val estadosPorCapacidade = chaves("mActiveCapaState")
    private val par = Regex("^(-?\\d+)=(-?\\d+)$")

    /** Os aparelhos do texto do log, na ordem da primeira aparição; o último estado visto de cada um vale. */
    fun analisar(saida: String): List<AparelhoDoDcm> {
        val aparelhos = LinkedHashMap<String, AparelhoDoDcm>()
        for (linha in saida.lineSequence()) {
            val aparelho = analisarLinha(linha) ?: continue
            aparelhos[aparelho.chave] = aparelho
        }
        return aparelhos.values.toList()
    }

    fun analisarLinha(linha: String): AparelhoDoDcm? {
        if (!linha.contains(MARCA_DA_LINHA) || !linha.contains(MARCA_DO_APARELHO)) return null
        val idDoAparelho = texto(id, linha)
        val bt = texto(enderecoBt, linha)
        val chave = bt ?: idDoAparelho ?: return null
        val porCapacidade = estadosPorCapacidade.find(linha)?.groupValues?.get(1)?.let(::lerPares) ?: emptyMap()
        return AparelhoDoDcm(
            chave = chave,
            id = idDoAparelho,
            nome = texto(nome, linha),
            serialUsb = texto(serialUsb, linha),
            enderecoBt = bt,
            capacidadesDisponiveis = numero(capacidadesDisponiveis, linha),
            capacidadeAtiva = numero(capacidadeAtiva, linha),
            estadoDoAparelho = numero(estadoDoAparelho, linha),
            estadoPorTipo = estadoPorTipo.find(linha)?.groupValues?.get(1),
            estadoAndroidAutoUsb = porCapacidade[AparelhoDoDcm.CAPACIDADE_ANDROID_AUTO_USB],
            estadoAndroidAutoSemFio = porCapacidade[AparelhoDoDcm.CAPACIDADE_ANDROID_AUTO_SEM_FIO],
        )
    }

    /** Texto entre aspas simples; vazio ou `null` vale "ausente". */
    private fun texto(regra: Regex, linha: String): String? {
        val valor = regra.find(linha)?.groupValues?.get(1) ?: return null
        return if (valor.isEmpty() || valor == "null") null else valor
    }

    private fun numero(regra: Regex, linha: String): Int? = regra.find(linha)?.groupValues?.get(1)?.toIntOrNull()

    /** Pares `capacidade=estado`; os malformados são ignorados. */
    private fun lerPares(conteudo: String): Map<Int, Int> {
        val mapa = LinkedHashMap<Int, Int>()
        for (pedaco in conteudo.split(',')) {
            val achado = par.find(pedaco.trim()) ?: continue
            val k = achado.groupValues[1].toIntOrNull() ?: continue
            val v = achado.groupValues[2].toIntOrNull() ?: continue
            mapa[k] = v
        }
        return mapa
    }
}

/**
 * Roda o `logcat` (só as linhas de erro da etiqueta do gerenciador) e guarda por 3 s o resultado
 * não vazio. Resultado vazio não entra no cache.
 */
internal class LeituraDeAparelhosPorLog(
    /** `Shell.executar` no app: devolve a saída, vazia sem Shizuku ou com erro. */
    private val executar: (Array<String>) -> String,
    private val relogioMs: () -> Long,
    private val registro: RegistroDosMonitores = RegistroMudo,
) {
    companion object {
        const val VALIDADE_DO_CACHE_MS = 3_000L
        val COMANDO = arrayOf("logcat", "-d", "DcController:E", "*:S")
    }

    private var cache: List<AparelhoDoDcm> = emptyList()
    private var cacheEmMs = 0L

    /** Nunca lança. Serializa: duas chamadas juntas rodam o `logcat` uma vez. */
    @Synchronized
    fun ler(): List<AparelhoDoDcm> {
        val agora = relogioMs()
        if (cache.isNotEmpty() && agora - cacheEmMs in 0 until VALIDADE_DO_CACHE_MS) return cache
        val saida = try {
            executar(COMANDO)
        } catch (e: Exception) {
            registro.erro("logcat do DCM", e)
            ""
        }
        // Vazio ou erro: devolve o que estava em cache (pode ser vazio).
        if (saida.isBlank()) return cache
        val lista = try {
            ParserDeLogDoDcm.analisar(saida)
        } catch (e: Exception) {
            registro.erro("análise do log do DCM", e)
            return cache
        }
        if (lista.isNotEmpty()) {
            cache = lista
            cacheEmMs = agora
        }
        return lista
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
