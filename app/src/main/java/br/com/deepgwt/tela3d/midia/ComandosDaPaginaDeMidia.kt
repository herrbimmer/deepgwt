package br.com.deepgwt.tela3d.midia

import android.os.Handler
import android.os.Looper

/**
 * A lógica do objeto de ponte `MediaBridge` (o módulo da ponte o registra na WebView com esse nome e
 * só encaminha cada método). Os métodos são chamados numa thread da WebView; todo o trabalho real
 * vai para a thread principal.
 */
class ComandosDaPaginaDeMidia internal constructor(
    private val midia: ControleDeMidia,
    private val fecharAppDoSlotDireito: () -> Unit,
    private val aoPaginaPronta: () -> Unit,
    private val postar: (Runnable) -> Unit,
) {
    /** Costura com janelas e ponte: [fecharAppDoSlotDireito] só age se há app no slot; [aoPaginaPronta] pede a localização. */
    constructor(
        midia: TocandoAgora,
        fecharAppDoSlotDireito: () -> Unit,
        aoPaginaPronta: () -> Unit,
    ) : this(midia as ControleDeMidia, fecharAppDoSlotDireito, aoPaginaPronta, postadorDaPrincipal())

    fun anterior() = postar(Runnable { midia.anterior() })

    fun tocarOuPausar() = postar(Runnable { midia.tocarOuPausar() })

    fun proxima() = postar(Runnable { midia.proxima() })

    /** A página terminou de carregar: inicia o publicador, força o reenvio e só então pede a localização. */
    fun paginaPronta() = postar(
        Runnable {
            midia.iniciar()
            midia.reenviarAgora()
            try {
                aoPaginaPronta()
            } catch (e: Exception) {
                Diario.aviso("pedido pós-carga da página falhou", e)
            }
        },
    )

    /** A página abriu o painel e precisa fechar a coluna da direita: fecha o app do slot, se houver. */
    fun dispensarSlot() = postar(
        Runnable {
            try {
                fecharAppDoSlotDireito()
            } catch (e: Exception) {
                Diario.aviso("fechar o app do slot direito falhou", e)
            }
        },
    )

    private companion object {
        fun postadorDaPrincipal(): (Runnable) -> Unit {
            val principal = Handler(Looper.getMainLooper())
            return { principal.post(it) }
        }
    }
}
