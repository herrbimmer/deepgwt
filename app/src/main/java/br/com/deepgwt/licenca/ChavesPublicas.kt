// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.licenca

import br.com.deepgwt.BuildConfig

/**
 * As chaves que podem assinar licenças (Alpha 8.1; chave curta na 8.2).
 *
 * TESTE (id 0): pública fixa aqui, privada no licenciador (não é segredo). Só vale num build de
 * teste ([Portao.testeLiberado]); o APK que o instalador leva ao carro recusa.
 *
 * PRODUÇÃO (id 1 em diante): vêm de `app/licenca/chaves-publicas.txt` (uma linha "id base64", a
 * chave em SEC1 comprimida na curva de [CurvaDaLicenca] — 28 caracteres), que
 * o licenciador grava ao criar a chave; o Gradle copia para `BuildConfig.LICENCA_CHAVES_PUBLICAS`.
 * Um build SEM chave de produção não cobra nada ([Portao.cobrancaAtiva]) — ninguém conseguiria
 * emitir uma licença que ele aceitasse. Mais de uma linha = rotação: as antigas continuam valendo.
 */
object ChavesPublicas {
    const val ID_DE_TESTE = 0

    const val TESTE = "AuDI3dqJexjcjV3flo/pfTWlBOXS"

    /** "1 AuDI…;2 A3x…" → id → base64. Ignora o id 0 (reservado ao teste) e linhas malformadas. */
    fun lerLista(texto: String): Map<Int, String> =
        texto.split(';', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { linha ->
                val partes = linha.split(Regex("\\s+"), limit = 2)
                val id = partes.getOrNull(0)?.toIntOrNull()?.takeIf { it in 1..15 } ?: return@mapNotNull null
                val chave = partes.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                id to chave
            }
            .toMap()

    val producao: Map<Int, String> by lazy { lerLista(BuildConfig.LICENCA_CHAVES_PUBLICAS) }

    /** Só conta chave que é ponto da curva: uma linha estragada no arquivo não liga a cobrança. */
    val temProducao: Boolean get() = carregadas.keys.any { it != ID_DE_TESTE }

    private val carregadas: Map<Int, CurvaDaLicenca.Ponto> by lazy {
        (producao + (ID_DE_TESTE to TESTE)).mapNotNull { (id, base64) ->
            runCatching { id to VerificadorDeLicenca.chavePublica(base64) }.getOrNull()
        }.toMap()
    }

    /** As que valem neste build (a de teste entra sempre; o verificador recusa fora do teste). */
    fun todas(): Map<Int, CurvaDaLicenca.Ponto> = carregadas
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
