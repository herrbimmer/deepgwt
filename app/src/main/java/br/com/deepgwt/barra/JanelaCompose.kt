// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.barra

import android.content.Context
import android.graphics.Region
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import br.com.deepgwt.ui.TemaDeepGwt
import java.lang.reflect.Proxy

/**
 * Dono de ciclo de vida, de estado salvo e de ViewModel de UMA janela (serviço caso 8): criado do
 * zero e levado a RESUMIDO na hora. Criar o controlador de estado salvo depois de o dono ter
 * passado da inicialização derruba o processo, por isso não usar o ciclo do serviço.
 */
internal class DonoDaJanela : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registro = LifecycleRegistry(this)
    private val controlador = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()
    override val lifecycle: Lifecycle get() = registro
    override val savedStateRegistry: SavedStateRegistry get() = controlador.savedStateRegistry

    init {
        controlador.performAttach()
        controlador.performRestore(null)
        registro.currentState = Lifecycle.State.RESUMED
    }

    fun ligar(vista: View) {
        vista.setViewTreeLifecycleOwner(this)
        vista.setViewTreeSavedStateRegistryOwner(this)
        vista.setViewTreeViewModelStoreOwner(this)
    }

    fun destruir() {
        registro.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}

/**
 * Região tocável por janela pela API oculta do Android (serviço caso 9): ouvinte de "insets
 * internos" do `ViewTreeObserver`, modo região (3). Falha → registrar e seguir com a janela
 * inteira tocável.
 */
internal object RegiaoTocavel {
    private const val TAG = "DeepGWT.Barra.Regiao"
    private const val MODO_REGIAO = 3

    private val classeDoOuvinte: Class<*>? by lazy {
        runCatching { Class.forName("android.view.ViewTreeObserver\$OnComputeInternalInsetsListener") }.getOrNull()
    }

    private fun metodo(nome: String): java.lang.reflect.Method? {
        val classe = classeDoOuvinte ?: return null
        return runCatching { ViewTreeObserver::class.java.getMethod(nome, classe) }.getOrNull()
            ?: runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("Landroid/view/ViewTreeObserver")
                }
                ViewTreeObserver::class.java.getMethod(nome, classe)
            }.getOrNull()
    }

    /** Instala o cálculo; devolve o ouvinte (para tirar) ou null se a API oculta não respondeu. */
    fun instalar(vista: View, calcular: () -> Retangulo): Any? {
        val classe = classeDoOuvinte
        val adicionar = metodo("addOnComputeInternalInsetsListener")
        if (classe == null || adicionar == null) {
            Log.w(TAG, "insets internos indisponíveis: janela inteira tocável")
            return null
        }
        var ouvinte: Any? = null
        ouvinte = Proxy.newProxyInstance(classe.classLoader, arrayOf(classe)) { _, m, args ->
            when (m.name) {
                "onComputeInternalInsets" -> {
                    runCatching { aplicar(args!![0], calcular()) }
                        .onFailure { Log.w(TAG, "região: ${it.message}") }
                    null
                }
                "equals" -> args?.getOrNull(0) === ouvinte
                "hashCode" -> System.identityHashCode(ouvinte)
                "toString" -> "RegiaoTocavel@${System.identityHashCode(ouvinte)}"
                else -> null
            }
        }
        return runCatching {
            adicionar.invoke(vista.viewTreeObserver, ouvinte)
            ouvinte
        }.onFailure { Log.w(TAG, "não instalou a região: ${it.message}") }.getOrNull()
    }

    fun remover(vista: View, ouvinte: Any?) {
        if (ouvinte == null) return
        runCatching { metodo("removeOnComputeInternalInsetsListener")?.invoke(vista.viewTreeObserver, ouvinte) }
    }

    private fun aplicar(info: Any, r: Retangulo) {
        val c = info.javaClass
        c.getMethod("setTouchableInsets", Int::class.javaPrimitiveType).invoke(info, MODO_REGIAO)
        val regiao = c.getField("touchableRegion").get(info) as Region
        if (r.vazio) regiao.setEmpty() else regiao.set(r.esquerda, r.topo, r.direita, r.base)
    }
}

/**
 * Uma janela de sobreposição com conteúdo Compose, dona do próprio ciclo de vida (caso 8). Toda
 * chamada na thread principal. Erros do WindowManager nunca lançam.
 */
internal class JanelaCompose(
    private val contexto: Context,
    val params: WindowManager.LayoutParams,
    private val nome: String,
    /** Região tocável recalculada a cada passada de layout; null = sem região (janela inteira). */
    private val regiao: (() -> Retangulo)? = null,
    /** Chamado a cada passada de layout com a vista (prender em x=0, medir). */
    private val aoDispor: ((JanelaCompose) -> Unit)? = null,
    private val conteudo: @Composable () -> Unit,
) {
    private val wm = contexto.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var dono: DonoDaJanela? = null
    private var ouvinteDaRegiao: Any? = null

    var vista: ComposeView? = null
        private set

    /** Entregue ao WindowManager e ainda presa nele. */
    val entregue: Boolean get() = vista?.let { it.isAttachedToWindow || it.parent != null } ?: false

    private val aoLayout = ViewTreeObserver.OnGlobalLayoutListener { aoDispor?.invoke(this) }

    /** Cria e entrega ao WindowManager. Devolve false se ele recusou. */
    fun montar(): Boolean {
        if (vista != null) return true
        val d = DonoDaJanela()
        val v = ComposeView(contexto).apply {
            d.ligar(this)
            setContent { TemaDeepGwt() { conteudo() } }
        }
        return try {
            wm.addView(v, params)
            vista = v
            dono = d
            if (regiao != null) ouvinteDaRegiao = RegiaoTocavel.instalar(v, regiao)
            if (aoDispor != null) v.viewTreeObserver.addOnGlobalLayoutListener(aoLayout)
            true
        } catch (e: Exception) {
            Log.w(TAG, "[$nome] WindowManager recusou a janela: ${e.message}")
            d.destruir()
            false
        }
    }

    /** Aplica [params]; se a janela não estiver anexada, entrega de novo (rede de segurança, caso 7). */
    fun atualizar() {
        val v = vista ?: return
        try {
            wm.updateViewLayout(v, params)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "[$nome] janela não anexada; entregando de novo")
            desmontar()
            montar()
        } catch (e: Exception) {
            Log.w(TAG, "[$nome] falha ao atualizar: ${e.message}")
        }
    }

    fun pedirLayout() {
        vista?.requestLayout()
    }

    fun desmontar() {
        val v = vista ?: return
        vista = null
        RegiaoTocavel.remover(v, ouvinteDaRegiao)
        ouvinteDaRegiao = null
        runCatching { v.viewTreeObserver.removeOnGlobalLayoutListener(aoLayout) }
        runCatching { wm.removeViewImmediate(v) }
        dono?.destruir()
        dono = null
    }

    companion object {
        private const val TAG = "DeepGWT.Barra.Janela"

        /** Os parâmetros comuns de uma sobreposição da barra. */
        fun parametros(largura: Int, altura: Int, flags: Int, gravidade: Int): WindowManager.LayoutParams =
            WindowManager.LayoutParams(
                largura,
                altura,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                flags,
                android.graphics.PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = gravidade
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
    }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

/**
 * Prende uma janela ao x=0 físico (serviço caso 11): mede onde ela caiu e, se a esquerda ≠ 0,
 * soma a diferença ao `x` (com gravidade DIREITA, `x` positivo move para a ESQUERDA). Uma correção
 * por vez, 250 ms entre medidas, no máximo 4 tentativas; posição certa zera a contagem.
 */
internal class PresaAoZero(private val nome: String, private val larguraFisica: () -> Int) {
    private var tentativas = 0
    private var esperando = false
    private var desistiu = false
    private val local = IntArray(2)

    fun aoDispor(janela: JanelaCompose) {
        if (esperando) return
        val v = janela.vista ?: return
        v.getLocationOnScreen(local)
        val esquerda = local[0]
        if (esquerda == 0) {
            tentativas = 0
            desistiu = false
            return
        }
        if (tentativas >= TENTATIVAS) {
            if (!desistiu) {
                desistiu = true
                Log.e("DeepGWT.Barra.Janela", "[$nome] não ficou em x=0 depois de $TENTATIVAS tentativas (esquerda=$esquerda)")
            }
            return
        }
        tentativas++
        esperando = true
        janela.params.x += esquerda
        larguraFisica().takeIf { it > 0 }?.let { janela.params.width = it }
        v.post { janela.atualizar() }
        v.postDelayed({
            esperando = false
            janela.pedirLayout()
        }, ESPERA_MS)
    }

    companion object {
        const val TENTATIVAS = 4
        const val ESPERA_MS = 250L
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
