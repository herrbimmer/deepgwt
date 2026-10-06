package br.com.deepgwt.tela3d.cartoes

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.widget.LinearLayout
import br.com.deepgwt.tela3d.faixa.Densidade

/** Os dois cartões de texto do conjunto de reserva (clima e energia): título pequeno e um valor de 26 sp. */
internal class CartaoDeTexto(
    contexto: Context,
    anfitriao: AnfitriaoDosCartoes,
    d: Densidade,
    tituloFonte: String,
    textoInicial: String,
    descricaoInicial: String,
    private val comando: String,
    override val larguraEmDp: Int,
    private val servicos: ServicosDoCartao,
) : CartaoDaLinha(contexto, anfitriao, d) {
    private val titulo = Fontes.texto(contexto, 12.5f, 0, true)
    private val valor = Fontes.texto(contexto, 26f, 0, true)

    init {
        val coluna = LinearLayout(contexto).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(d.px(16), d.px(12), d.px(16), d.px(12))
        }
        titulo.text = AjusteDeCaixa.ajustar(tituloFonte)
        titulo.isSingleLine = true
        titulo.ellipsize = TextUtils.TruncateAt.END
        valor.isSingleLine = true
        valor.ellipsize = TextUtils.TruncateAt.END
        valor.text = textoInicial
        coluna.addView(titulo, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        coluna.addView(valor, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = d.px(4)
        })
        addView(coluna, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        contentDescription = descricaoInicial
        setOnClickListener { servicos.enviar(comando) }
        reaplicarTema()
    }

    fun definirResumo(texto: String, descricao: String) {
        if (valor.text.toString() != texto) valor.text = texto
        contentDescription = descricao
    }

    override fun reaplicarTema() {
        aplicarFundo(0)
        titulo.setTextColor(tema.corDeRotuloMuted)
        valor.setTextColor(tema.corDeRotulo)
    }
}
