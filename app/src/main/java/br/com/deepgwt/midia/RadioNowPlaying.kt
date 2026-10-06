// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.midia

import br.com.deepgwt.veiculo.Sinais
/**
 * O rádio do carro traduzido para o mesmo "now playing" que o resto do app já sabe mostrar.
 *
 * ## POR QUE ISTO EXISTE
 *
 * O indicador de mídia do painel e a página de mídia do HUD leem `app.media.*`, que vem do
 * `MediaSession` do Android — ou seja, de aplicativos. **O rádio do carro não é um aplicativo
 * Android:** ele é do próprio veículo e só aparece no barramento, em `sys.radio.*`. Com o rádio
 * tocando, o `MediaSession` não tem nada a dizer, o título chegava vazio e o painel mostrava o
 * indicador apagado — "não mostra qual rádio está tocando".
 *
 * O HUD tinha o defeito na outra ponta: ele já lia `sys.radio.cur_channel_info` e desenhava o
 * valor CRU na tela, isto é, literalmente `{103900,0,1,1}`.
 *
 * ## O QUE É FATO E O QUE É INFERÊNCIA
 *
 * FATO, da captura completa em `knowledge/session_2026-09-15/raw_http/telemetry.json`, com o rádio
 * tocando em FM:
 *
 * ```
 * "sys.radio.cur_channel_info":"{103900,0,1,1}"
 * "sys.radio.play_state":"1"
 * "sys.radio.fm_valid_station_list":"{92900,80100,100900,105700,...,103900,...}"
 * "sys.radio.rds_cur_channel_info":""
 * "sys.radio.am_valid_station_list":"{}"
 * ```
 *
 * O primeiro campo é a frequência em kHz: 103900 está na lista de estações FM válidas e 103900 kHz
 * é 103,9 MHz. Isso está confirmado.
 *
 * INFERÊNCIA: o segundo campo (`0`) parece ser a banda, mas com AM e DAB vazios neste carro não há
 * como confirmar a codificação. Por isso a banda é decidida pela FAIXA da frequência, que é
 * verificável sozinha, e o campo de banda é ignorado — ver [faixaDe].
 *
 * INFERÊNCIA: o formato de `rds_cur_channel_info` é desconhecido (vem vazio aqui). O nome de
 * estação só é aceito quando o texto tem letra, que é o que separa um nome de mais um vetor de
 * números — e, sem isso, o pior caso é continuar mostrando a frequência, que é o certo.
 */
object RadioNowPlaying {

    val CHAVE_CANAL: String = Sinais.SYS_RADIO_CUR_CHANNEL_INFO.chave
    val CHAVE_CANAL_RDS: String = Sinais.SYS_RADIO_RDS_CUR_CHANNEL_INFO.chave
    val CHAVE_ESTADO: String = Sinais.SYS_RADIO_PLAY_STATE.chave

    /** As chaves que mudam o que este objeto devolve. */
    val CHAVES: Set<String> = setOf(CHAVE_CANAL, CHAVE_CANAL_RDS, CHAVE_ESTADO)

    /** Rótulo da linha de baixo quando a de cima já é a frequência. */
    const val RODAPE_PADRAO = "Rádio"

    /** Faixa de uma estação: a banda, o número já formatado e a unidade. */
    data class Estacao(val banda: String, val numero: String, val unidade: String) {
        /** `"FM 103,9 MHz"` — o texto completo, para quem tem uma linha só. */
        val rotulo: String
            get() = listOf(banda, numero, unidade).filter { it.isNotEmpty() }.joinToString(" ")

        /** `"FM 103,9"` — sem a unidade, para caber na linha estreita do painel. */
        val curto: String
            get() = listOf(banda, numero).filter { it.isNotEmpty() }.joinToString(" ")
    }

    /** O rádio está tocando agora? Mesma régua que a barra inferior já usa para `play_state`. */
    fun tocando(estado: String?): Boolean {
        val v = estado?.trim()?.lowercase() ?: return false
        return v == "1" || v == "playing" || v == "play"
    }

    /**
     * A banda e a unidade a partir da frequência em kHz.
     *
     * A faixa decide, não o campo de banda do vetor: 87,5–108 MHz é FM em todo lugar do mundo e
     * 522–1710 kHz é AM em todo lugar do mundo, e isso não depende de descobrir a codificação do
     * fabricante. Fora das duas faixas conhecidas o número é mostrado como veio, sob o rótulo
     * genérico — inventar uma banda seria pior do que não afirmar nenhuma.
     */
    internal fun faixaDe(kHz: Long): Estacao = when {
        kHz in 64_000..108_000 -> Estacao("FM", megahertz(kHz), "MHz")
        kHz in 144..1_710 -> Estacao("AM", kHz.toString(), "kHz")
        else -> Estacao(RODAPE_PADRAO, kHz.toString(), "")
    }

    /** kHz inteiro para MHz com uma casa e vírgula decimal: `103900` -> `"103,9"`. */
    private fun megahertz(kHz: Long): String {
        val decimos = Math.round(kHz / 100.0)
        return "${decimos / 10},${decimos % 10}"
    }

    /**
     * A estação sintonizada, ou `null` quando o vetor não traz frequência utilizável.
     *
     * `{}`, `{0,...}` e vazio devolvem `null`: é o cache do canal antes de o rádio sintonizar
     * qualquer coisa, e mostrar "FM 0,0" seria pior do que não mostrar nada.
     */
    fun estacao(canal: String?): Estacao? {
        val kHz = primeiroNumero(canal) ?: return null
        if (kHz <= 0) return null
        return faixaDe(kHz)
    }

    /** O nome que o RDS deu à estação, ou `null`. Só vale se tiver letra — ver o cabeçalho. */
    fun nomeRds(rds: String?): String? {
        val bruto = rds?.trim().orEmpty()
        if (bruto.isEmpty() || bruto == "{}" || bruto == "[]") return null
        val texto = bruto.trim('{', '}', '[', ']')
                .split(',')
                .map { it.trim().trim('"') }
                .firstOrNull { pedaco -> pedaco.any { it.isLetter() } }
                ?: return null
        return texto.takeIf { it.isNotEmpty() }
    }

    /**
     * A linha de cima do indicador: o nome da estação quando o RDS dá um, senão a frequência.
     *
     * Devolve `""` quando não há rádio para anunciar — quem chama trata isso como "nada tocando" e
     * não como um título em branco.
     */
    fun titulo(canal: String?, rds: String? = null): String {
        val estacao = estacao(canal) ?: return ""
        return nomeRds(rds) ?: estacao.curto
    }

    /**
     * A linha de baixo: a frequência quando o nome ocupou a de cima, senão o rótulo genérico.
     *
     * Nunca repete o que já está na linha de cima — duas linhas iguais gastam a única área de texto
     * que o painel tem para dizer alguma coisa.
     */
    fun subtitulo(canal: String?, rds: String? = null): String {
        val estacao = estacao(canal) ?: return ""
        return if (nomeRds(rds) != null) estacao.rotulo else RODAPE_PADRAO
    }

    /** Uma linha só, para o HUD: `"FM 103,9 MHz"`, ou com o nome do RDS na frente quando existir. */
    fun linhaUnica(canal: String?, rds: String? = null): String {
        val estacao = estacao(canal) ?: return ""
        val nome = nomeRds(rds) ?: return estacao.rotulo
        return "$nome · ${estacao.rotulo}"
    }

    /** Lê as três chaves de um leitor qualquer — o mesmo padrão testável da política do farol. */
    fun de(ler: (String) -> String?): Leitura =
            Leitura(
                    canal = ler(CHAVE_CANAL),
                    rds = ler(CHAVE_CANAL_RDS),
                    estado = ler(CHAVE_ESTADO)
            )

    /** As três leituras cruas, já emparelhadas. */
    data class Leitura(val canal: String?, val rds: String?, val estado: String?) {
        val estaTocando: Boolean
            get() = tocando(estado)
        val titulo: String
            get() = titulo(canal, rds)
        val subtitulo: String
            get() = subtitulo(canal, rds)
        val linhaUnica: String
            get() = linhaUnica(canal, rds)
    }

    /** O primeiro inteiro do vetor, que é onde a frequência está. */
    private fun primeiroNumero(bruto: String?): Long? {
        val texto = bruto?.trim() ?: return null
        if (texto.isEmpty()) return null
        var i = 0
        while (i < texto.length && !texto[i].isDigit()) {
            // Um sinal de menos antes do primeiro dígito só aparece em lista de favoritos vazia
            // ({-1,-1,...}); ali não há frequência para ler, e parar aqui devolve null.
            if (texto[i] == '-') return null
            i++
        }
        if (i >= texto.length) return null
        var fim = i
        while (fim < texto.length && texto[fim].isDigit()) fim++
        return texto.substring(i, fim).toLongOrNull()
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
