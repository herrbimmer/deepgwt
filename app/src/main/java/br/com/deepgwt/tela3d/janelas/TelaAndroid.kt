package br.com.deepgwt.tela3d.janelas

import android.widget.Toast

/** [PortaDaTela] sobre a Activity: página, janela, geometria e avisos. Tudo na thread principal. */
internal class TelaAndroid(
    private val anfitriao: AnfitriaoDasJanelas,
    private val geometria: GeometriaAndroid,
    private val ancoras: () -> AncorasInvisiveis?,
    private val limitesDeTelaCheia: LimitesDeTelaCheia,
    private val diario: Diario,
) : PortaDaTela {

    override fun raizMedida(): Boolean = anfitriao.raizMedida() != null

    override fun enviarLayout(estado: EstadoDasJanelas) {
        val entradas = geometria.entradas()
        val medidas = LayoutParaAPagina.Medidas(
            esquerda = CalculoDeGeometria.limitesDoLado(entradas, Lado.ESQUERDA, estado.modo, estado.proporcao),
            direita = CalculoDeGeometria.limitesDoLado(entradas, Lado.DIREITA, estado.modo, estado.proporcao),
            origem = CalculoDeGeometria.origemDaPagina(entradas),
            margens = CalculoDeGeometria.margens(entradas, estado.modo),
            densidade = entradas.densidade,
        )
        anfitriao.enviarLayoutParaAPagina(LayoutParaAPagina.montar(estado, medidas), estado.pacoteEsquerda.isNotEmpty())
    }

    override fun limitesDoLado(lado: Lado, modo: ModoDeTela, proporcao: ProporcaoDaDivisao): Retangulo =
        geometria.limitesDoLado(lado, modo, proporcao)

    override fun recalcularAncoras(modo: ModoDeTela, proporcao: ProporcaoDaDivisao) {
        val vistas = ancoras() ?: return
        val entradas = geometria.entradas()
        vistas.posicionar(
            CalculoDeGeometria.limitesDoLado(entradas, Lado.ESQUERDA, modo, proporcao),
            CalculoDeGeometria.limitesDoLado(entradas, Lado.DIREITA, modo, proporcao),
            CalculoDeGeometria.origemDaPagina(entradas),
        )
    }

    override fun limitesDeTelaCheia(pacote: String): Retangulo =
        LimitesDeTelaCheiaJ10.resolver(limitesDeTelaCheia, pacote, diario)

    override fun aplicarPoliticaDeFoco(naoFocavel: Boolean, noLimite: Boolean): Boolean {
        val janela = anfitriao.activity.window ?: return false
        val atuais = janela.attributes.flags
        val novas = PoliticaDeFoco.novasFlags(atuais, naoFocavel, noLimite)
        if (novas == atuais) return false
        janela.setFlags(novas, PoliticaDeFoco.NAO_FOCAVEL or PoliticaDeFoco.NO_LIMITE)
        return true
    }

    override fun aplicarImersivo(modo: ModoDaInterface) = anfitriao.aplicarImersivo(modo)

    override fun mostrarAviso(texto: String) {
        Toast.makeText(anfitriao.activity, texto, Toast.LENGTH_SHORT).show()
    }
}
