// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.painel

import br.com.deepgwt.ponte.ContratoV1
import br.com.deepgwt.telas.PresetBackgrounds

/**
 * A forma do painel de fábrica (`projetores-painel.md`, L.2): onde a máscara global aparece. Gerada
 * pelos quatro retângulos (Decisão 6; sem imagem). Coordenadas inclusivas na especificação; aqui
 * [Retangulo] é meio-aberto.
 */
object FormaDoPainelDeFabrica {
    const val LARGURA = 1920
    const val ALTURA = 720

    @JvmField
    val RETANGULOS: List<Retangulo> = listOf(
        Retangulo(0, 0, 601, 720), // x 0–600 × y 0–719
        Retangulo(710, 0, 1211, 68), // x 710–1210 × y 0–67
        Retangulo(0, 653, 731, 720), // x 0–730 × y 653–719
        Retangulo(1190, 653, 1920, 720), // x 1190–1919 × y 653–719
    )

    /** O pixel (x, y) está dentro da forma? */
    @JvmStatic
    fun contem(x: Int, y: Int): Boolean =
        RETANGULOS.any { x >= it.esquerda && x < it.direita && y >= it.topo && y < it.base }
}

/** De onde vem uma imagem: um arquivo em disco ou um asset do APK. */
sealed class ImagemDoPainel {
    data class Arquivo(val caminho: String) : ImagemDoPainel()
    data class Asset(val caminho: String) : ImagemDoPainel()
}

/** O papel de parede do painel (L.7) e o `clusterBackground` (L.8), a partir das preferências. */
object PapelDeParede {
    const val THEME = "THEME"
    const val PRESET = "PRESET"
    const val FILE = "FILE"
    const val IMAGE_URL = "IMAGE_URL"
    const val COLOR = "COLOR"
    const val WEB_URL = "WEB_URL"

    /** O fundo de reserva dentro da pasta do tema em uso. */
    const val FUNDO_DE_RESERVA = "car-bg.png"

    /** O que está gravado: ligado (padrão true), tipo normalizado (padrão THEME), valor sem espaços nas pontas. */
    data class Escolha(val ligado: Boolean, val tipo: String, val valor: String) {
        companion object {
            @JvmStatic
            fun de(p: PreferenciasDoPainel): Escolha = Escolha(
                ligado = p.booleano(PreferenciasDoPainel.FUNDO_LIGADO, true),
                tipo = ContratoV1.tipoDeFundo(p.texto(PreferenciasDoPainel.FUNDO_TIPO, THEME)),
                valor = p.texto(PreferenciasDoPainel.FUNDO_VALOR, "").trim(),
            )
        }
    }

    /** O que o papel de parede precisa saber dos temas instalados. */
    interface Temas {
        /** `TemasInstalados.arquivoDeFundoDoTemaAtivo(dica)?.absolutePath`. */
        fun fundoDoTemaAtivo(dica: String?): String?

        /** `TemasInstalados.arquivo(pasta, nome)?.absolutePath`. */
        fun arquivoNoTema(pasta: String, nome: String): String?

        fun arquivoExiste(caminho: String): Boolean
    }

    /**
     * L.7: a imagem do papel de parede para o display do painel, ou null. [temaEmUso] = a pasta da
     * página carregada ("" = página do painel original: sem fundo de tema).
     */
    @JvmStatic
    fun imagem(escolha: Escolha, temaEmUso: String, temas: Temas): ImagemDoPainel? = try {
        if (!escolha.ligado) {
            null
        } else {
            when (escolha.tipo) {
                THEME -> if (temaEmUso.isEmpty()) {
                    null
                } else {
                    (temas.fundoDoTemaAtivo(escolha.valor.ifEmpty { null }) ?: temas.arquivoNoTema(temaEmUso, FUNDO_DE_RESERVA))
                        ?.let { ImagemDoPainel.Arquivo(it) }
                }
                FILE -> escolha.valor.takeIf { it.isNotEmpty() && temas.arquivoExiste(it) }?.let { ImagemDoPainel.Arquivo(it) }
                PRESET -> escolha.valor.takeIf { it.isNotEmpty() }?.let { ImagemDoPainel.Asset("backgrounds/" + PresetBackgrounds.resolve(it)) }
                else -> null
            }
        }
    } catch (e: Exception) {
        null
    }

    /** L.8: há fundo opaco atrás do painel? */
    @JvmStatic
    fun haFundo(escolha: Escolha, temas: Temas): Boolean = try {
        if (!escolha.ligado) {
            false
        } else {
            when (escolha.tipo) {
                THEME -> temas.fundoDoTemaAtivo(escolha.valor.ifEmpty { null }) != null
                PRESET, FILE, IMAGE_URL, COLOR -> true
                WEB_URL -> escolha.valor.isNotEmpty()
                else -> false
            }
        }
    } catch (e: Exception) {
        false
    }
}

/** Uma máscara por elemento a desenhar (L.3). */
data class ElementoDaMascara(
    val nome: String,
    val area: Retangulo,
    val opacidade: Float,
    /** A imagem do tema; null = sem imagem (recorte do papel, se houver; senão [transparenteSemImagem] ou preta). */
    val imagem: ImagemDoPainel?,
) {
    /** Sem imagem e sem papel de parede: transparente se ≥ 500 × 500, senão preta. */
    val transparenteSemImagem: Boolean get() = area.largura >= 500 && area.altura >= 500
}

/**
 * O que o contêiner das máscaras nativas deve mostrar (L.1–L.6). [papel] não nulo = máscara
 * global (se a imagem não decodificar, cai para [elementos] sem papel); senão [elementos]; os dois
 * vazios = nenhuma. [vao] é apagado de tudo.
 */
data class PlanoDasMascaras(
    val tema: String,
    val papel: ImagemDoPainel?,
    val elementos: List<ElementoDaMascara>,
    val vao: Retangulo?,
    val cartao: Int,
) {
    val nenhuma: Boolean get() = papel == null && elementos.isEmpty()

    /** O resumo do diário `native_mask_state` (L.10). */
    fun resumo(): String {
        val resultado = when {
            papel != null -> "global"
            elementos.isNotEmpty() -> "elementos:" + elementos.joinToString("+") { it.nome }
            else -> "nenhuma"
        }
        return "tema=${tema.ifEmpty { "original" }} resultado=$resultado cartao=$cartao vao=${vao ?: "nenhum"}"
    }

    companion object {
        /** Os nomes das cinco máscaras, como a ponte manda (minúsculas). */
        @JvmField
        val NOMES: List<String> = listOf("fuel", "battery", "speed", "info", "topcenter")

        /**
         * Monta o plano. [mascaras] = as do `theme.xml` do tema em uso (null = nenhuma);
         * [sobreposicoes] = o que a página ligou/desligou (L.6).
         */
        @JvmStatic
        fun planejar(
            tema: String,
            papel: ImagemDoPainel?,
            mascaras: MascarasNativas?,
            sobreposicoes: Map<String, Boolean>,
            cartao: Int,
            vao: Retangulo?,
            pastaDoTema: (String) -> ImagemDoPainel?,
        ): PlanoDasMascaras {
            // As por elemento são calculadas sempre: são a reserva se o papel não decodificar.
            val elementos = ArrayList<ElementoDaMascara>()
            if (mascaras != null) {
                for (nome in NOMES) {
                    val m = mascaras.porNome(nome) ?: continue
                    if (!m.ligada) continue
                    if (sobreposicoes[nome] == false) continue
                    if (nome == "info" && cartao == ArbitroDoCartao.CARTAO_DO_CARRO) continue
                    val area = Retangulo(m.x, m.y, m.x + m.largura, m.y + m.altura)
                    val imagem = m.imagem.takeIf { it.isNotEmpty() }?.let { pastaDoTema(it) }
                    elementos.add(ElementoDaMascara(nome, area, m.opacidade, imagem))
                }
            }
            return PlanoDasMascaras(tema, papel, elementos, vao, cartao)
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
