package br.com.opengw.midia

/**
 * O tamanho da capa do álbum que vai para o tema do painel.
 *
 * O estado da barra inferior guarda a capa com até 720 px de lado,
 * que é o que a barra inferior e a luz ambiente usam. Mandada assim para o tema, ela virava um PNG
 * de centenas de KB em base64, recodificado NA THREAD PRINCIPAL a cada segundo pelo laço de mídia
 * do painel — só para o diff descobrir que nada tinha mudado.
 *
 * O maior lugar que um tema reserva para a capa é o card do Cupertino Ultra (34% de 760 = 258 px);
 * a linha do Stuttgart NTG 5.5 usa 56. [LADO_MAX] cobre os dois sem ampliar nada.
 *
 * Kotlin puro para a conta poder ser provada em teste de unidade.
 */
object CapaParaTema {

    const val LADO_MAX = 320

    /**
     * Largura e altura reduzidas mantendo a proporção.
     *
     * Devolve as dimensões originais quando elas já cabem — capa pequena não é ampliada — e quando
     * são inválidas, para quem chama decidir o que fazer com um Bitmap vazio.
     */
    fun dimensoes(largura: Int, altura: Int, ladoMax: Int = LADO_MAX): Pair<Int, Int> {
        if (largura <= 0 || altura <= 0) return largura to altura
        val maior = maxOf(largura, altura)
        if (maior <= ladoMax) return largura to altura
        val escala = ladoMax.toDouble() / maior
        return maxOf(1, Math.round(largura * escala).toInt()) to
                maxOf(1, Math.round(altura * escala).toInt())
    }
}
