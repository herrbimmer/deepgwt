package br.com.opengw

import br.com.opengw.ui.TemaOpenGw
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import br.com.opengw.ui.screens.UpdateScreen

/**
 * "Atualizar OPEN GW" — ícone próprio na tela inicial da central (ver `.UpdaterAlias` no
 * AndroidManifest). É uma Activity separada da principal de propósito: numa instalação nova ela
 * precisa abrir e ser útil enquanto o resto do app ainda não tem permissão de nada.
 */
class UpdateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        // Um download nesta central pode passar do tempo de tela; apagar no meio esconde o
        // progresso e faz parecer que travou.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val postInstall = intent?.getBooleanExtra(EXTRA_POST_INSTALL, false) == true
        setContent {
            TemaOpenGw { UpdateScreen(postInstall = postInstall, onClose = ::finish) }
        }
    }

    companion object {
        const val EXTRA_POST_INSTALL = "postInstall"

        /**
         * Reabre a tela logo após o pacote ter sido trocado, já no modo "concluir": concede as
         * permissões da versão nova e mostra o resultado.
         */
        @JvmStatic
        fun postInstallIntent(context: Context): Intent =
                Intent(context, UpdateActivity::class.java).apply {
                    putExtra(EXTRA_POST_INSTALL, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
    }
}
