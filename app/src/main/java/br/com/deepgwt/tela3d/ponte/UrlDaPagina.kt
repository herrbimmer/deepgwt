package br.com.deepgwt.tela3d.ponte

/** O endereço da página 3D e a regra de para onde o quadro principal pode navegar. */
object UrlDaPagina {

    /** Quebra-cache dos arquivos de `www`: quem mudar o `www` incrementa esta revisão. */
    const val REVISAO_DOS_ASSETS = "vehicle-console-v47-aa-tbt-gmaps"

    /**
     * `android` (modo "dentro do carro" da página), `assets=<revisão>` (só quebra-cache) e `demo=0`
     * (a página ignora tudo que não seja `force`; o emulador não é detectado aqui). Com
     * [semSplash] acrescenta `&nosplash` (a vinheta já tocou nesta partida).
     */
    fun montar(semSplash: Boolean): String {
        val base = "https://${ArquivosEmpacotados.HOST}/assets/www/index.html" +
            "?android&assets=$REVISAO_DOS_ASSETS&demo=0"
        return if (semSplash) "$base&nosplash" else base
    }

    /**
     * O quadro principal só pode ir para `https://appassets.androidplatform.net` (porta padrão):
     * outra origem carregaria uma página estranha com as quatro pontes JS expostas, e com elas os
     * comandos do carro.
     */
    fun navegacaoPermitida(esquema: String?, host: String?, porta: Int): Boolean {
        if (esquema == null || host == null) return false
        if (!esquema.equals("https", ignoreCase = true)) return false
        if (!host.equals(ArquivosEmpacotados.HOST, ignoreCase = true)) return false
        return porta == -1 || porta == 443
    }
}
