package br.com.opengw

import br.com.opengw.ui.CoresClassicas
import br.com.opengw.ui.Cores
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import br.com.opengw.ui.InterfaceDoOpenGw
import br.com.opengw.ui.LocalPaletaDaInterface
import br.com.opengw.ui.paletaDe
import kotlinx.coroutines.delay

/**
 * Splash do OPEN GW: o contorno de "OPEN GW" se desenha (mesma técnica de comprimento-de-traço
 * progressivo usada nos mostradores do app, portada pra Canvas nativo via PathMeasure) e depois
 * ganha um preenchimento sólido. Substitui o antigo fade+zoom sobre a foto do carro.
 */
private const val STROKE_DURATION_MS = 1100
private const val FILL_DELAY_MS = 950
private const val FILL_DURATION_MS = 450
private const val SPLASH_DURATION_MS = 1700L

class AberturaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        setContent {
            // A abertura já sai na cor do estilo escolhido em "Interface do OPEN GW".
            CompositionLocalProvider(LocalPaletaDaInterface provides paletaDe(InterfaceDoOpenGw.estilo)) {
                TelaDeAbertura(onFinished = ::abrirTelaPrincipal)
            }
        }
    }

    private fun abrirTelaPrincipal() {
        startActivity(
            Intent(this, PrincipalActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
        finish()
    }
}

@Composable
private fun TelaDeAbertura(onFinished: () -> Unit) {
    var revealed by remember { mutableStateOf(false) }
    val strokeProgress by
        animateFloatAsState(
            targetValue = if (revealed) 1f else 0f,
            animationSpec = tween(durationMillis = STROKE_DURATION_MS, easing = FastOutSlowInEasing),
            label = "splash_stroke"
        )
    val fillAlpha by
        animateFloatAsState(
            targetValue = if (revealed) 1f else 0f,
            animationSpec =
                tween(durationMillis = FILL_DURATION_MS, delayMillis = FILL_DELAY_MS, easing = EaseOutCubic),
            label = "splash_fill"
        )

    LaunchedEffect(Unit) {
        revealed = true
        delay(SPLASH_DURATION_MS)
        onFinished()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = CoresClassicas.fundo) {
        Box(
            modifier = Modifier.fillMaxSize().background(CoresClassicas.fundo),
            contentAlignment = Alignment.Center
        ) {
            WordmarkStrokeReveal(strokeProgress = strokeProgress, fillAlpha = fillAlpha)
        }
    }
}

@Composable
private fun WordmarkStrokeReveal(strokeProgress: Float, fillAlpha: Float) {
    val context = LocalContext.current
    val typeface = remember { ResourcesCompat.getFont(context, R.font.michroma) }
    val acento = Cores.destaque

    Canvas(modifier = Modifier.fillMaxWidth(0.7f).height(140.dp)) {
        val text = "OPEN GW"
        val paint =
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                this.textSize = size.height * 0.5f
                this.letterSpacing = 0.06f
            }
        val nativePath = android.graphics.Path()
        paint.getTextPath(text, 0, text.length, 0f, 0f, nativePath)
        val fullPath = nativePath.asComposePath()
        val bounds = fullPath.getBounds()

        translate(
            left = (size.width - bounds.width) / 2f - bounds.left,
            top = (size.height - bounds.height) / 2f - bounds.top
        ) {
            val measure = PathMeasure()
            measure.setPath(fullPath, false)
            val strokePath = Path()
            measure.getSegment(0f, measure.length * strokeProgress, strokePath, true)

            drawPath(
                path = strokePath,
                color = acento,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            if (fillAlpha > 0f) {
                drawPath(path = fullPath, color = acento.copy(alpha = fillAlpha))
            }
        }
    }
}
