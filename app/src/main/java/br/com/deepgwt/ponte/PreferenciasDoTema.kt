// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ponte

import br.com.deepgwt.telas.PresetBackgrounds

/** Leitura do arquivo de preferências do jeito que a ponte precisa. Kotlin puro (o app liga ao `SharedPreferences`). */
interface LeitorDePreferencias {
    fun contem(chave: String): Boolean

    /** O valor como texto, seja qual for o tipo gravado (`true`, `1.0`…); null se não existe. */
    fun texto(chave: String): String?

    /** O valor só se ele foi gravado como texto; null se não existe ou é de outro tipo. */
    fun somenteTexto(chave: String): String?

    /** Booleano gravado (ou texto `true`/`false`); null se não existe ou é outra coisa. */
    fun booleano(chave: String): Boolean?

    /** Número gravado (ou texto numérico); null se não existe ou é outra coisa. */
    fun numero(chave: String): Float?
}

/**
 * Escopo das preferências por tema (seção 9). Kotlin puro.
 */
object PreferenciasDoTema {

    /** A PASTA do tema ativo (não o nome de exibição); vazia = `Default` no escopo. */
    const val TEMA_ATIVO = "activeCustomTheme"

    /** Fonte do farol sintetizado (seção 7.6); mudou = reavaliar forçado. */
    const val FONTE_DO_FAROL = "nightModeTrigger"

    /**
     * Liga/desliga o modo noturno do painel (Alpha 9.8). Ausente = ligado. Desligado, o tema recebe
     * sempre o farol apagado e a cor dos letreiros não muda quando o farol acende.
     */
    const val NOTURNO_DO_PAINEL = "nightModeEnabled"

    fun temaAtivo(leitor: LeitorDePreferencias): String = leitor.somenteTexto(TEMA_ATIVO).orEmpty()

    /**
     * `getPreference(key, default)`: a chave escopada do tema ativo, se existe (o valor como texto,
     * seja qual for o tipo); senão a preferência global [chave] se ela for texto; senão [padrao].
     * Não valida a chave.
     */
    fun ler(leitor: LeitorDePreferencias, chave: String, padrao: String): String {
        val escopada = ContratoV1.chaveEscopada(temaAtivo(leitor), chave)
        if (leitor.contem(escopada)) return leitor.texto(escopada).orEmpty()
        return leitor.somenteTexto(chave) ?: padrao
    }

    /**
     * `savePreference(key, value)`: a chave sem espaços nas pontas tem de casar
     * `[A-Za-z0-9_.-]{1,80}`. Devolve (chave escopada, valor até 4096 caracteres), ou null para
     * recusar.
     */
    fun gravacao(temaAtivo: String, chave: String, valor: String): Pair<String, String>? {
        val limpa = chave.trim()
        if (!ContratoV1.chaveDePreferenciaValida(limpa)) return null
        return ContratoV1.chaveEscopada(temaAtivo, limpa) to valor.take(ContratoV1.LIMITE_DO_VALOR_DA_PREFERENCIA)
    }

    /**
     * Mudou a preferência [chaveMudada] (seção 9.3, item 1): com que nome `app.preferences.*` ela vai
     * à página, ou null. Primeiro a escopada do tema ativo agora (`theme_config_<tema>_<resto>` ->
     * `app.preferences.<resto>`), se assinada; senão a própria chave (`app.preferences.<K>`), se
     * assinada.
     */
    fun nomeDaEntrega(chaveMudada: String, temaAtivo: String, assinada: (String) -> Boolean): String? {
        val prefixo = ContratoV1.chaveEscopada(temaAtivo, "")
        if (chaveMudada.startsWith(prefixo)) {
            val nome = ContratoV1.PREFIXO_PREFERENCIAS + chaveMudada.substring(prefixo.length)
            if (assinada(nome)) return nome
        }
        val global = ContratoV1.PREFIXO_PREFERENCIAS + chaveMudada
        return if (assinada(global)) global else null
    }
}

/**
 * O papel de parede do display 1 visto pelo tema (seção 4, itens 12–14). Kotlin puro.
 */
object FundoDoPainel {

    const val LIGADO = "enableCustomBackgroundD1"
    const val TIPO = "customBackgroundTypeD1"
    const val VALOR = "customBackgroundValueD1"

    const val TIPO_TEMA = "THEME"
    const val VAZIO = "{}"

    private const val PASTA_DOS_PRONTOS = "file:///android_asset/backgrounds/"
    private val COR = Regex("#[0-9A-Fa-f]{6}")

    /** O que está gravado: ligado (ausente = true), tipo normalizado (ausente = THEME), valor sem espaços nas pontas. */
    data class Estado(val ligado: Boolean, val tipo: String, val valor: String)

    fun estado(leitor: LeitorDePreferencias): Estado = Estado(
        ligado = leitor.booleano(LIGADO) ?: true,
        tipo = ContratoV1.tipoDeFundo(leitor.somenteTexto(TIPO)),
        valor = leitor.somenteTexto(VALOR)?.trim().orEmpty(),
    )

    /**
     * `getClusterBackdropSource()`: `{"kind":"IMAGE","url":…}`, `{"kind":"COLOR",…}` ou `{}`.
     * [arquivoDoTema] resolve o fundo do tema ativo pela dica (null = o `<background>`);
     * [arquivoExiste] confere um caminho absoluto.
     */
    fun origem(
        estado: Estado,
        arquivoDoTema: (String?) -> String?,
        arquivoExiste: (String) -> Boolean,
    ): String = try {
        if (!estado.ligado) {
            VAZIO
        } else {
            val valor = estado.valor
            when (estado.tipo) {
                TIPO_TEMA -> arquivoDoTema(valor.ifEmpty { null })
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { JsonDaPonte.fundoImagem("file://$it") }
                    ?: VAZIO
                "FILE" -> if (valor.isNotEmpty() && arquivoExiste(valor)) JsonDaPonte.fundoImagem("file://$valor") else VAZIO
                "PRESET" -> if (valor.isEmpty()) VAZIO else JsonDaPonte.fundoImagem(PASTA_DOS_PRONTOS + PresetBackgrounds.resolve(valor))
                "IMAGE_URL" -> if (valor.isEmpty()) VAZIO else JsonDaPonte.fundoImagem(valor)
                "COLOR" -> cor(valor) ?: VAZIO
                else -> VAZIO
            }
        }
    } catch (e: Exception) {
        VAZIO
    }

    /**
     * `#RRGGBB|V<n>` -> `{"kind":"COLOR","color":"#RRGGBB","vignette":n}`, hex em maiúsculas,
     * vinheta 0..100 (ausente ou ilegível = 0; fora da faixa = presa na faixa). Hex inválido: null.
     */
    fun cor(valor: String): String? {
        val partes = valor.split('|')
        val hex = partes[0].trim()
        if (!COR.matches(hex)) return null
        var vinheta = 0
        if (partes.size > 1) {
            val texto = partes[1].trim()
            if (texto.startsWith("V") || texto.startsWith("v")) {
                texto.substring(1).trim().toIntOrNull()?.let { vinheta = it.coerceIn(0, 100) }
            }
        }
        return JsonDaPonte.fundoCor(hex.uppercase(), vinheta)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
