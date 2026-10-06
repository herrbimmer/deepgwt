package br.com.deepgwt.tela3d.catalogo

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * Serviço de acessibilidade do seletor de desktops. Existe só para ter o tipo de janela de
 * sobreposição de acessibilidade: põe uma janela transparente por cima do seletor de desktops da
 * página (que fica sob a barra de status do Android, que engole toques) e repassa os toques à
 * WebView, por meio do [AnfitriaoDoToque] guardado em [MaquinaDoSeletor].
 *
 * Quem o habilita é o instalador ou o ajuste manual (`enabled_accessibility_services`); o app não
 * o liga sozinho. A configuração é `res/xml/desktop_switcher_hit.xml`.
 */
open class ServicoDoSeletorDeAreas : AccessibilityService() {
    private val principal = Handler(Looper.getMainLooper())
    private var janela: JanelaDeSobreposicao? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val nova = JanelaDeSobreposicao(this, MaquinaDoSeletor.compartilhada)
        janela = nova
        MaquinaDoSeletor.compartilhada.conectar(nova) { acao -> principal.post(acao) }
        Log.w(ETIQUETA, "Desktop switcher hit service connected")
    }

    override fun onDestroy() {
        principal.removeCallbacksAndMessages(null)
        janela?.let { MaquinaDoSeletor.compartilhada.desconectar(it) }
        janela = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(evento: AccessibilityEvent?) {
        // Nada: o serviço existe só pelo tipo de janela.
    }

    override fun onInterrupt() {
        // Nada.
    }

    private companion object {
        const val ETIQUETA = "DeepGWT/SeletorDeAreas"
    }
}

/**
 * A janela de sobreposição (tipo 2032, `TYPE_ACCESSIBILITY_OVERLAY`) transparente. A view e os
 * parâmetros são criados uma vez; a cada sincronização só mudam posição e tamanho. O toque vai
 * para o [AnfitriaoDoToque] atual; sem host, não é consumido.
 */
internal class JanelaDeSobreposicao(
    private val contexto: Context,
    private val maquina: MaquinaDoSeletor,
) : JanelaDoSeletor {
    private var view: View? = null
    private var parametros: WindowManager.LayoutParams? = null
    private var anexada = false

    private val gerente: WindowManager?
        get() = contexto.getSystemService(Context.WINDOW_SERVICE) as? WindowManager

    override fun anexar(x: Int, y: Int, largura: Int, altura: Int): Boolean {
        val wm = gerente ?: return false
        val v = view ?: criarView().also { view = it }
        val p = parametros ?: criarParametros().also { parametros = it }
        posicionar(p, x, y, largura, altura)
        return try {
            wm.addView(v, p)
            anexada = true
            Log.w(ETIQUETA, "Hit proxy overlay $x,$y ${largura}x$altura")
            true
        } catch (e: Exception) {
            Log.w(ETIQUETA, "Hit proxy overlay failed", e)
            anexada = false
            false
        }
    }

    override fun atualizar(x: Int, y: Int, largura: Int, altura: Int): Boolean {
        val wm = gerente ?: return false
        val v = view ?: return false
        val p = parametros ?: return false
        posicionar(p, x, y, largura, altura)
        return try {
            wm.updateViewLayout(v, p)
            true
        } catch (e: Exception) {
            Log.w(ETIQUETA, "Hit proxy overlay failed", e)
            anexada = false
            false
        }
    }

    override fun remover() {
        val v = view
        if (anexada && v != null) {
            try {
                gerente?.removeViewImmediate(v)
            } catch (e: Exception) {
                // erros ao remover são ignorados
            }
        }
        anexada = false
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun criarView(): View = View(contexto).apply {
        setBackgroundColor(0)
        setOnTouchListener { _, evento: MotionEvent -> maquina.anfitriao()?.repassarToque(evento) ?: false }
    }

    private fun criarParametros(): WindowManager.LayoutParams = WindowManager.LayoutParams().apply {
        type = TIPO_SOBREPOSICAO_DE_ACESSIBILIDADE
        flags = FLAGS_DA_JANELA
        format = FORMATO_TRANSLUCIDO
        gravity = Gravity.START or Gravity.TOP
    }

    private fun posicionar(p: WindowManager.LayoutParams, x: Int, y: Int, largura: Int, altura: Int) {
        p.x = x
        p.y = y
        p.width = largura
        p.height = altura
    }

    internal companion object {
        const val ETIQUETA = "DeepGWT/SeletorDeAreas"

        /** `TYPE_ACCESSIBILITY_OVERLAY`. */
        const val TIPO_SOBREPOSICAO_DE_ACESSIBILIDADE = 2032

        /**
         * `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_NO_LIMITS |
         * FLAG_HARDWARE_ACCELERATED` = 0x01000328.
         */
        const val FLAGS_DA_JANELA = 0x01000328

        /** `PixelFormat.TRANSLUCENT`. */
        const val FORMATO_TRANSLUCIDO = -3
    }
}
