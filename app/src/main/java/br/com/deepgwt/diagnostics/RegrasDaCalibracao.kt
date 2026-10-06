// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.diagnostics

import br.com.deepgwt.veiculo.TravaDeMovimento

/**
 * O que a calibração do painel (Alpha 8.8) decide sem precisar do Android: os passos, quais
 * chaves entram na linha do tempo, o que mudou entre duas leituras, como ler o formato cru do
 * `screencap` e quando a sessão acaba sozinha. Separado de [CalibracaoDoPainel] para ser testado.
 */
object RegrasDaCalibracao {

    /**
     * A sessão acaba sozinha depois disto. Com a grade no ar o painel fica sem velocímetro; o
     * limite cobre quem esquece a calibração ligada e desliga o carro.
     */
    const val LIMITE_MS = 30L * 60 * 1000

    /** Intervalo da leitura das chaves da linha do tempo. */
    const val INTERVALO_MS = 500L

    /**
     * Ids de tela do `screencap -d` (os da SurfaceFlinger, não os do DisplayManager). Nesta
     * central o 4 foi o painel físico 1920×720 e o 5 o HUD (docs/architecture/display-system.md,
     * carplay-visual-capture-agent.md); o resto vai junto porque o mapa pode mudar por versão. O 0
     * é a tela central, que durante a captura mostra o próprio Diagnóstico: fica de fora.
     */
    val TELAS = listOf(1, 2, 3, 4, 5, 6)

    data class Passo(val id: String, val rotulo: String, val comoFazer: String)

    val PASSOS = listOf(
        Passo("painel", "Sem aviso", "Carro ligado, portas fechadas, cinto afivelado, luzes apagadas."),
        Passo("ready", "READY", "Pise no freio e dê a partida até o READY acender."),
        Passo("porta", "Porta aberta", "Abra a porta do motorista e deixe aberta."),
        Passo("portamalas", "Porta-malas", "Abra o porta-malas."),
        Passo("cinto", "Cinto solto", "Solte o cinto do motorista."),
        Passo("freio", "Freio de mão", "Acione o freio de estacionamento (ou solte, se já estava)."),
        Passo("farol", "Farol baixo", "Ligue o farol baixo."),
        Passo("alto", "Farol alto", "Ligue o farol alto."),
        Passo("neblina", "Neblina", "Ligue a luz de neblina (dianteira e traseira, se houver)."),
        Passo("setaesq", "Seta esquerda", "Ligue a seta para a esquerda."),
        Passo("setadir", "Seta direita", "Ligue a seta para a direita."),
        Passo("alerta", "Pisca-alerta", "Ligue o pisca-alerta."),
        Passo("faixa", "Assist. de faixa", "Ligue o assistente de faixa pelo botão do carro."),
        Passo("esp", "ESP desligado", "Desligue o ESP (controle de estabilidade)."),
        Passo("popup", "Mensagem", "Qualquer mensagem que o painel mostrar em caixa (pop-up)."),
        Passo("outro", "Outro", "Qualquer outra luz ou aviso aceso agora."),
    )

    fun passo(id: String): Passo = PASSOS.firstOrNull { it.id == id } ?: PASSOS.last()

    /**
     * Chaves que entram na linha do tempo: luzes do quadro, avisos, portas, cinto, freio, READY,
     * setas e faróis. O resto (velocidade, giro, corrente da bateria) muda o tempo todo e só
     * afogaria o que interessa; ele vai inteiro nas capturas.
     */
    private val RELEVANTE = Regex(
        "ipk_light|ipk_info|ipk_setting\\.alarm|alarm|warning|warn|indicator|light_status|lamp|" +
            "door_status|door_lock|seat_belt|driving_ready|engine_state|epb|auto_hold|hdc_enable|" +
            "esp_enable|tpms|hazard|turn_|gear_status|fatigue|\\.tsr\\.|drive_mode"
    )

    fun relevante(chave: String): Boolean = RELEVANTE.containsMatchIn(chave)

    /** Sinais VHAL de lâmpada, aviso e falha, lidos um a um em cada captura. */
    private val SINAL_DE_LAMPADA = Regex("LMP|LAMP|WARN|FAILSTS|TELLTALE|ALARM|INDCR|IND_")

    fun sinalDeLampada(nome: String): Boolean = SINAL_DE_LAMPADA.containsMatchIn(nome)

    data class Mudanca(val chave: String, val antes: String?, val depois: String)

    /** O que mudou de [antes] para [depois]. Chave que sumiu da leitura nova não conta. */
    fun mudancas(antes: Map<String, String>, depois: Map<String, String>): List<Mudanca> =
        depois.entries
            .filter { (k, v) -> antes[k] != v }
            .map { Mudanca(it.key, antes[it.key], it.value) }
            .sortedBy { it.chave }

    enum class Fim(val texto: String) {
        ANDOU("o carro andou"),
        TEMPO("passaram 30 minutos"),
    }

    /** Por que a sessão acaba agora, ou null. Sem leitura de velocidade, só o tempo encerra. */
    fun fim(agoraMs: Long, desdeMs: Long, velocidade: String?): Fim? {
        if (TravaDeMovimento.avaliar(velocidade, null, exigirP = false, exigirLeitura = false) ==
            TravaDeMovimento.Motivo.ANDANDO
        ) return Fim.ANDOU
        if (desdeMs > 0 && agoraMs - desdeMs > LIMITE_MS) return Fim.TEMPO
        return null
    }

    /**
     * Cabeçalho do formato cru do `screencap` (sem `-p`): largura, altura e formato em 32 bits
     * little-endian. O Android 9 para aí (12 bytes); do 10 em diante vem mais o espaço de cor (16).
     * O resto são as linhas, sem sobra de stride. `null` = não é uma imagem que se saiba ler.
     *
     * Formatos (PixelFormat): 1 RGBA_8888, 2 RGBX_8888, 3 RGB_888, 4 RGB_565, 5 BGRA_8888.
     */
    data class Raw(val largura: Int, val altura: Int, val formato: Int, val cabecalho: Int, val bytesPorPixel: Int)

    fun lerRaw(bytes: ByteArray): Raw? {
        if (bytes.size < 12) return null
        val w = le32(bytes, 0)
        val h = le32(bytes, 4)
        val f = le32(bytes, 8)
        if (w <= 0 || h <= 0 || w > 8192 || h > 8192) return null
        val bpp = when (f) {
            1, 2, 5 -> 4
            3 -> 3
            4 -> 2
            else -> return null
        }
        val cabecalho = bytes.size.toLong() - w.toLong() * h * bpp
        if (cabecalho != 12L && cabecalho != 16L) return null
        return Raw(w, h, f, cabecalho.toInt(), bpp)
    }

    /** Os pixels em ARGB opaco (uma captura de tela não tem transparência que valha guardar). */
    fun paraArgb(bytes: ByteArray, raw: Raw): IntArray {
        val n = raw.largura * raw.altura
        val saida = IntArray(n)
        var o = raw.cabecalho
        for (i in 0 until n) {
            val c = when (raw.formato) {
                1, 2 -> rgb(bytes[o], bytes[o + 1], bytes[o + 2])
                5 -> rgb(bytes[o + 2], bytes[o + 1], bytes[o])
                3 -> rgb(bytes[o], bytes[o + 1], bytes[o + 2])
                else -> {
                    val v = (bytes[o].toInt() and 0xff) or ((bytes[o + 1].toInt() and 0xff) shl 8)
                    val r = (v shr 11) and 0x1f
                    val g = (v shr 5) and 0x3f
                    val b = v and 0x1f
                    (0xff shl 24) or (((r shl 3) or (r shr 2)) shl 16) or (((g shl 2) or (g shr 4)) shl 8) or
                        ((b shl 3) or (b shr 2))
                }
            }
            saida[i] = c
            o += raw.bytesPorPixel
        }
        return saida
    }

    /** A cor da tela inteira quando ela é de uma cor só (preta, em geral), ou null. */
    fun corUnica(argb: IntArray): Int? {
        if (argb.isEmpty()) return null
        val primeira = argb[0]
        for (c in argb) if (c != primeira) return null
        return primeira
    }

    /** Nome de arquivo seguro para o passo (já é, mas o "outro" pode ganhar rótulo livre um dia). */
    fun nomeDaCaptura(numero: Int, passoId: String): String =
        "%02d-%s".format(numero, passoId.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "passo" })

    private fun le32(b: ByteArray, i: Int): Int =
        (b[i].toInt() and 0xff) or ((b[i + 1].toInt() and 0xff) shl 8) or
            ((b[i + 2].toInt() and 0xff) shl 16) or ((b[i + 3].toInt() and 0xff) shl 24)

    private fun rgb(r: Byte, g: Byte, b: Byte): Int =
        (0xff shl 24) or ((r.toInt() and 0xff) shl 16) or ((g.toInt() and 0xff) shl 8) or (b.toInt() and 0xff)
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
