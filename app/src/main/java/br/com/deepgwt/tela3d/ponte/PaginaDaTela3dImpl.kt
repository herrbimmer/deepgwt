package br.com.deepgwt.tela3d.ponte

/**
 * O que os vizinhos mandam para a página. Qualquer thread: o cache é concorrente e todo script passa
 * pela fila da UI da WebView ([executarJs] posta). Sem Android: testável na JVM.
 */
class PaginaDaTela3dImpl(
    private val cache: CacheDeTelemetria,
    /** Executa um script na página (o `post` da WebView; não faz nada sem WebView). */
    private val executarJs: (String) -> Unit,
    /** Pede o aviso de layout (a Activity posta na principal e junta as tentativas). */
    private val pedirAvisoDeLayout: () -> Unit,
) : PaginaDaTela3d {

    override fun publicarTelemetria(chave: String, valor: String) {
        cache.guardar(chave, valor)
        executarJs(ScriptsDaTela3d.onCarDataUpdate(chave, valor))
    }

    override fun valorDeTelemetria(chave: String): String = cache.valorDe(chave)

    override fun enviarMidia(payloadJson: String) = executarJs(ScriptsDaTela3d.onMediaNowPlaying(payloadJson))

    override fun enviarPosicaoDaMidia(posicaoMs: Long) = executarJs(ScriptsDaTela3d.onMediaPosition(posicaoMs))

    override fun enviarComandoDoDock(comando: String) = executarJs(ScriptsDaTela3d.comandoDoDock(comando))

    override fun invalidarCatalogoDeApps() = executarJs(ScriptsDaTela3d.invalidarCatalogoDeApps())

    override fun avisarLayoutDoShell() = pedirAvisoDeLayout()

    override fun enviarPlacaDoDock(estilo: String, visivel: Boolean, esquerdaCss: Float, topoCss: Float, larguraCss: Float, alturaCss: Float) =
        executarJs(ScriptsDaTela3d.placaDoDock(estilo, visivel, esquerdaCss, topoCss, larguraCss, alturaCss))
}
