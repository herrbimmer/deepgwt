// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.deepgwt.nucleo.Preferencias
import kotlin.math.roundToInt

/**
 * Tamanho da interface e das letras das telas do DeepGWT (Alpha 7.9).
 *
 * ## COMO FUNCIONA
 *
 * O `PrincipalActivity` troca a própria densidade (`densityDpi`) e a escala de fonte (`fontScale`)
 * antes de desenhar — [sobreposicao], aplicada em `attachBaseContext`. Tudo o que o app mede em
 * dp e sp cresce junto, inclusive os diálogos (abrem com o contexto da Activity), sem mexer em
 * tela nenhuma. Trocar o valor recria a Activity (ver `PrincipalActivity`).
 *
 * ## O QUE NÃO MUDA
 *
 * Barra inferior, painel de instrumentos, HUD, launcher 3D e a abertura: são outras janelas e
 * Activities, e seguem a densidade do sistema. Com os dois valores no padrão nada é sobreposto —
 * quem atualiza não vê diferença nenhuma.
 *
 * ## CUIDADO AO MEDIR A JANELA
 *
 * `Configuration.screenWidthDp`/`screenHeightDp` continuam medidos na densidade REAL (quem calcula
 * é o sistema). Multiplicar por `LocalDensity` dá o tamanho errado com a escala ligada: use
 * [larguraDaJanelaPx] e [alturaDaJanelaDp].
 */
object EscalaDaInterface {
    data class Opcao(val pct: Int, val nome: String)

    const val PADRAO = 100
    const val MINIMO = 85
    const val MAXIMO_DA_INTERFACE = 130
    const val MAXIMO_DAS_LETRAS = 140

    /** A tela do carro tem 720 px de altura: 125% ainda deixa 576 dp, o bastante para as telas rolarem. */
    val TAMANHOS_DA_INTERFACE =
        listOf(Opcao(90, "Compacto"), Opcao(100, "Padrão"), Opcao(115, "Grande"), Opcao(125, "Extra"))

    val TAMANHOS_DAS_LETRAS =
        listOf(Opcao(90, "Pequenas"), Opcao(100, "Padrão"), Opcao(115, "Grandes"), Opcao(130, "Extra"))

    fun interfaceValida(pct: Int): Int = pct.coerceIn(MINIMO, MAXIMO_DA_INTERFACE)

    fun letrasValidas(pct: Int): Int = pct.coerceIn(MINIMO, MAXIMO_DAS_LETRAS)

    /** O nome da opção com esta porcentagem, ou "95%" quando veio do computador fora das quatro. */
    fun nomeDe(opcoes: List<Opcao>, pct: Int): String =
        opcoes.firstOrNull { it.pct == pct }?.nome ?: "$pct%"

    /** Valores que o `PrincipalActivity` sobrepõe. null em cada campo = não mexer nele. */
    data class Sobreposicao(val densityDpi: Int?, val fontScale: Float?) {
        val vazia: Boolean
            get() = densityDpi == null && fontScale == null
    }

    /** Parte pura de [sobreposicao]: testável sem Android. */
    fun calcular(densityDpiReal: Int, fontScaleReal: Float, interfacePct: Int, letrasPct: Int): Sobreposicao {
        val i = interfaceValida(interfacePct)
        val l = letrasValidas(letrasPct)
        val dpi =
            if (i == PADRAO || densityDpiReal <= 0) null
            else (densityDpiReal * i / 100f).roundToInt()
        val fonte =
            if (l == PADRAO || fontScaleReal <= 0f) null
            else fontScaleReal * l / 100f
        return Sobreposicao(dpi, fonte)
    }

    /**
     * A densidade do sistema, anotada antes da sobreposição. `Resources.getSystem()` não serve: no
     * emulador, com `wm density`, ela fica na densidade física da tela.
     */
    @Volatile
    var densidadeReal: Float = 0f
        private set

    fun prefs(context: Context): SharedPreferences =
        context.createDeviceProtectedStorageContext().getSharedPreferences("deepgwt_prefs", Context.MODE_PRIVATE)

    fun lerInterface(prefs: SharedPreferences): Int =
        interfaceValida(runCatching { prefs.getInt(Preferencias.INTERFACE_ESCALA.chave, PADRAO) }.getOrDefault(PADRAO))

    fun lerLetras(prefs: SharedPreferences): Int =
        letrasValidas(runCatching { prefs.getInt(Preferencias.INTERFACE_ESCALA_TEXTO.chave, PADRAO) }.getOrDefault(PADRAO))

    /**
     * A configuração que o `PrincipalActivity` sobrepõe à do sistema, ou null quando os dois valores
     * estão no padrão. Só os campos preenchidos valem: o resto continua vindo do sistema, inclusive
     * nas mudanças de configuração que a Activity trata sozinha.
     */
    fun sobreposicao(base: Context): Configuration? {
        val p = prefs(base)
        val real = base.resources.configuration
        if (real.densityDpi > 0) densidadeReal = real.densityDpi / 160f
        val s = calcular(real.densityDpi, real.fontScale, lerInterface(p), lerLetras(p))
        if (s.vazia) return null
        return Configuration().apply {
            s.densityDpi?.let { densityDpi = it }
            s.fontScale?.let { fontScale = it }
        }
    }

    /** Grava; o `PrincipalActivity` ouve as duas chaves e se recria com o tamanho novo. */
    fun gravar(context: Context, interfacePct: Int? = null, letrasPct: Int? = null) {
        val editor = prefs(context).edit()
        interfacePct?.let { editor.putInt(Preferencias.INTERFACE_ESCALA.chave, interfaceValida(it)) }
        letrasPct?.let { editor.putInt(Preferencias.INTERFACE_ESCALA_TEXTO.chave, letrasValidas(it)) }
        editor.apply()
    }
}

/** Largura da janela em px de verdade, com ou sem a escala da interface. */
@Composable
fun larguraDaJanelaPx(): Float = LocalConfiguration.current.screenWidthDp * densidadeRealOu(LocalDensity.current.density)

/** Altura da janela em dp da densidade em uso (a escalada, quando a escala está ligada). */
@Composable
fun alturaDaJanelaDp(): Dp {
    val densidade = LocalDensity.current.density
    val px = LocalConfiguration.current.screenHeightDp * densidadeRealOu(densidade)
    return (px / densidade).dp
}

private fun densidadeRealOu(emUso: Float): Float =
    EscalaDaInterface.densidadeReal.takeIf { it > 0f } ?: emUso
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
