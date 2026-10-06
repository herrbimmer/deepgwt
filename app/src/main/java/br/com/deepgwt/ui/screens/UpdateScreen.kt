// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui.screens

import br.com.deepgwt.ui.Cores
import br.com.deepgwt.nucleo.DeepGwt
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import br.com.deepgwt.BuildConfig
import br.com.deepgwt.managers.PermissionBootstrap
import br.com.deepgwt.app.InstaladorDeApk
import br.com.deepgwt.app.VerificadorDeVersoes
import br.com.deepgwt.app.Versao
import br.com.deepgwt.nucleo.Preferencias
import br.com.deepgwt.ui.GlassTier
import br.com.deepgwt.ui.Motion
import br.com.deepgwt.ui.liquidGlass
import br.com.deepgwt.ui.motionSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tela cheia de atualização, desenhada para ser lida de dentro do carro: um estado por vez, texto
 * grande, e nenhuma ação obrigatória do usuário no caminho feliz.
 *
 * O caso que dita o formato é a instalação nova. O app pode subir antes do servidor do Shizuku
 * (que não é daemon do sistema e é levantado logo depois, ver br.com.deepgwt.nucleo.shizuku.SubidaDoShizuku),
 * e sem Shizuku não existe shell privilegiado — logo, não existe como conceder permissão nenhuma.
 * Em vez de mostrar erro, a tela assume a verdade: a instalação ainda não acabou. Ela espera,
 * mostra que está esperando, e no instante em que o Shizuku responde concede tudo de uma vez.
 */
private const val SHIZUKU_POLL_MS = 1_500L

private sealed interface Phase {
    object WaitingShizuku : Phase
    object AwaitingShizukuConsent : Phase
    object Granting : Phase
    object Checking : Phase
    data class UpToDate(val version: String) : Phase
    object ViaInstaller : Phase
    data class Available(val release: Versao) : Phase
    data class Downloading(val fraction: Float) : Phase
    object Installing : Phase
    data class Done(val failed: List<String>) : Phase
    data class Failed(val message: String) : Phase
}

private data class StepResult(val label: String, val granted: Boolean)

@Composable
fun UpdateScreen(postInstall: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    var phase by remember { mutableStateOf<Phase>(Phase.Checking) }
    var waitedSeconds by remember { mutableStateOf(0) }
    var runId by remember { mutableStateOf(0) }
    val stepLog = remember { mutableStateListOf<StepResult>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(runId) {
        try {
            // pingBinder/checkSelfPermission são IPC; num laço de espera eles não têm o que fazer
            // na main thread, ainda mais numa central que já engasga no arranque do app.
            if (!withContext(Dispatchers.IO) { PermissionBootstrap.isShizukuReady() }) {
                val startedAt = System.currentTimeMillis()
                waitedSeconds = 0
                var consentRequested = false
                while (true) {
                    val running = withContext(Dispatchers.IO) { PermissionBootstrap.isShizukuRunning() }
                    val ready = running && withContext(Dispatchers.IO) { PermissionBootstrap.hasShizukuAccess() }
                    if (ready) break

                    phase = if (running) Phase.AwaitingShizukuConsent else Phase.WaitingShizuku
                    if (running && !consentRequested) {
                        consentRequested = true
                        PermissionBootstrap.requestShizukuAccess()
                    }
                    delay(SHIZUKU_POLL_MS)
                    waitedSeconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
                }
            }

            if (postInstall || !withContext(Dispatchers.IO) { PermissionBootstrap.hasRunForCurrentVersion() }) {
                phase = Phase.Granting
                val failed = grantAllPermissions(context, stepLog)
                PermissionBootstrap.recordOutcome(failed)
                if (postInstall) {
                    setInstallFlag(false)
                    phase = Phase.Done(failed)
                    return@LaunchedEffect
                }
            }

            phase = Phase.Checking
            phase = lookForUpdate()
        } catch (e: Exception) {
            phase = Phase.Failed(e.message ?: "Falha inesperada")
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Cores.fundo).padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally)
        ) {
            Column(modifier = Modifier.widthIn(max = 560.dp)) {
                StatusBlock(
                    phase = phase,
                    waitedSeconds = waitedSeconds,
                    onUpdate = {
                        (phase as? Phase.Available)?.let { available ->
                            scope.launch {
                                downloadAndInstall(context, available.release) { phase = it }
                            }
                        }
                    },
                    onRetry = { stepLog.clear(); runId += 1 },
                    onClose = onClose
                )
            }

            AnimatedVisibility(visible = stepLog.isNotEmpty()) {
                StepChecklist(stepLog, modifier = Modifier.width(320.dp))
            }
        }
    }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

@Composable
private fun StatusBlock(
    phase: Phase,
    waitedSeconds: Int,
    onUpdate: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    val headline: String
    val detail: String
    when (phase) {
        Phase.WaitingShizuku -> {
            headline = "Finalizando a instalação"
            detail =
                "Ainda faltam alguns instantes. O serviço que dá as permissões está subindo — " +
                    "assim que ele responder, tudo é liberado de uma vez, sem você tocar em nada.\n" +
                    "Mantenha a central ligada. Esperando há ${waitedSeconds}s."
        }
        Phase.AwaitingShizukuConsent -> {
            headline = "Autorize o acesso"
            detail =
                "O serviço de permissões subiu, mas ainda não liberou o DeepGWT. Toque em " +
                    "\"Permitir\" na janela do Shizuku que aparece na tela — é a única " +
                    "confirmação necessária, e só nesta primeira vez.\n" +
                    "Esperando há ${waitedSeconds}s."
        }
        Phase.Granting -> {
            headline = "Liberando permissões"
            detail = "Sobreposição, instalação de apps, barra inferior e acessibilidade."
        }
        Phase.Checking -> {
            headline = "Procurando atualizações"
            detail = "Versão instalada: ${BuildConfig.VERSION_NAME}"
        }
        is Phase.UpToDate -> {
            headline = "Tudo em dia"
            detail = "A versão ${phase.version} é a mais recente disponível."
        }
        Phase.ViaInstaller -> {
            headline = "Atualização pelo instalador"
            detail =
                "Versão instalada: ${BuildConfig.VERSION_NAME}.\nO DeepGWT é atualizado pelo " +
                    "instalador, no computador: conecte-o ao Wi-Fi do carro e use \"Instalar e " +
                    "manter\". As suas configurações são mantidas."
        }
        is Phase.Available -> {
            headline = "Versão ${phase.release.etiqueta} disponível"
            detail =
                "Você está na ${BuildConfig.VERSION_NAME}. A central reinicia o app sozinha ao " +
                    "final; a tela pisca por alguns segundos."
        }
        is Phase.Downloading -> {
            headline = "Baixando atualização"
            detail = "${(phase.fraction * 100).toInt()}%"
        }
        Phase.Installing -> {
            headline = "Instalando"
            detail = "Não desligue a central. O app fecha e reabre sozinho em instantes."
        }
        is Phase.Done -> {
            headline = if (phase.failed.isEmpty()) "Tudo pronto" else "Quase tudo pronto"
            detail =
                if (phase.failed.isEmpty()) {
                    "DeepGWT ${BuildConfig.VERSION_NAME} instalado, com todas as permissões ativas."
                } else {
                    "Instalado, mas ${phase.failed.size} permissão(ões) não foram concedidas: " +
                        phase.failed.joinToString(", ") + "."
                }
        }
        is Phase.Failed -> {
            headline = "Não deu certo"
            detail = phase.message
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PhaseIcon(phase)
        Text(headline, color = Cores.textoPrincipal, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
        Text(
            detail,
            color = Cores.textoSecundario,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            modifier = Modifier.widthIn(max = 560.dp)
        )

        ProgressBar(phase)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when (phase) {
                is Phase.Available -> PrimaryButton("Atualizar agora", onUpdate)
                is Phase.UpToDate -> {
                    PrimaryButton("Procurar de novo", onRetry)
                    TextButton(onClick = onClose) { Text("Fechar", color = Cores.textoSecundario) }
                }
                Phase.ViaInstaller -> PrimaryButton("Fechar", onClose)
                is Phase.Done -> PrimaryButton("Fechar", onClose)
                is Phase.Failed -> {
                    PrimaryButton("Tentar de novo", onRetry)
                    TextButton(onClick = onClose) { Text("Fechar", color = Cores.textoSecundario) }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun PhaseIcon(phase: Phase) {
    val (icon, tint) = when (phase) {
        Phase.WaitingShizuku, Phase.AwaitingShizukuConsent ->
            Icons.Outlined.HourglassTop to Cores.atencao
        Phase.Granting -> Icons.Outlined.Lock to Cores.destaque
        is Phase.Done -> Icons.Outlined.CheckCircle to
            (if (phase.failed.isEmpty()) Color(0xFF34D399) else Cores.atencao)
        is Phase.Failed -> Icons.Outlined.ErrorOutline to Cores.perigo
        else -> Icons.Outlined.CloudDownload to Cores.destaque
    }
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(44.dp))
}

@Composable
private fun ProgressBar(phase: Phase) {
    val fraction = when (phase) {
        is Phase.Downloading -> phase.fraction
        else -> null
    }
    val indeterminate = phase is Phase.WaitingShizuku ||
        phase is Phase.AwaitingShizukuConsent ||
        phase is Phase.Granting ||
        phase is Phase.Checking ||
        phase is Phase.Installing

    when {
        fraction != null -> {
            // Anima o alvo em vez de saltar a cada 1% recebido: numa central lenta os blocos
            // chegam em rajadas e a barra crua fica aos trancos.
            val animated by animateFloatAsState(
                targetValue = fraction,
                animationSpec = motionSpec(Motion.reposition()),
                label = "download_progress"
            )
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier.fillMaxWidth(0.7f).height(8.dp).clip(RoundedCornerShape(6.dp)),
                color = Cores.destaque,
                trackColor = Cores.trilhoDesligado
            )
        }
        indeterminate -> CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            color = Cores.destaque,
            strokeWidth = 2.dp
        )
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Cores.destaque,
            contentColor = Cores.sobreODestaque
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

@Composable
private fun StepChecklist(steps: SnapshotStateList<StepResult>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .liquidGlass(GlassTier.Popover, RoundedCornerShape(20.dp))
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Permissões", color = Cores.textoSecundario, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        steps.forEach { step ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (step.granted) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = if (step.granted) Color(0xFF34D399) else Cores.perigo,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(step.label, color = Cores.textoPrincipal, fontSize = 13.sp)
            }
        }
    }
}

/**
 * Roda os passos um a um a partir da corrotina da tela (e não pelo [PermissionBootstrap.runAll]),
 * para que cada item apareça na lista no momento em que é concedido. Só o shell vai pra IO.
 */
private suspend fun grantAllPermissions(
    context: Context,
    log: SnapshotStateList<StepResult>
): List<String> {
    log.clear()
    val failed = mutableListOf<String>()
    PermissionBootstrap.steps().forEach { step ->
        val granted = withContext(Dispatchers.IO) {
            runCatching { step.apply(context) }.getOrDefault(false)
        }
        if (!granted) failed += step.label
        log.add(StepResult(step.label, granted))
    }
    return failed
}

private suspend fun lookForUpdate(): Phase {
    if (!VerificadorDeVersoes.configurado) return Phase.ViaInstaller
    val current = BuildConfig.VERSION_NAME
    val result = VerificadorDeVersoes.consultar()
    // Quem já está numa preview continua recebendo preview; quem está numa estável só vê estável,
    // senão uma atualização "normal" empurraria o carro pra um canal de teste sem avisar.
    val candidate =
        if (current.contains("preview", ignoreCase = true)) {
            result.previa ?: result.estavel
        } else {
            result.estavel
        }
    return when {
        candidate == null -> Phase.Failed("Não foi possível falar com o servidor de versões. Verifique a internet da central.")
        VerificadorDeVersoes.maisNova(candidate.etiqueta, current) -> Phase.Available(candidate)
        else -> Phase.UpToDate(current)
    }
}

/**
 * Baixa e instala. A instalação silenciosa troca o pacote e o Android mata este processo no meio —
 * por isso a marca em disco antes de chamar: quem mostra o resultado é a reabertura da tela pelo
 * MY_PACKAGE_REPLACED. Chegar na linha seguinte ao install significa que a troca NÃO aconteceu.
 */
private suspend fun downloadAndInstall(
    context: Context,
    release: Versao,
    setPhase: (Phase) -> Unit
) {
    try {
        setPhase(Phase.Downloading(0f))
        val file = InstaladorDeApk.baixar(context, release.endereco) { fraction ->
            setPhase(Phase.Downloading(fraction))
        }
        setPhase(Phase.Installing)
        setInstallFlag(true)
        if (InstaladorDeApk.instalarPeloShell(file)) return

        setInstallFlag(false)
        context.startActivity(InstaladorDeApk.intentDeInstalacao(context, file))
        setPhase(
            Phase.Failed(
                "A instalação automática não foi aceita. Abri o instalador do Android — " +
                    "toque em Instalar para concluir."
            )
        )
    } catch (e: Exception) {
        setInstallFlag(false)
        setPhase(Phase.Failed(e.message ?: "Falha ao baixar a atualização"))
    }
}

private fun setInstallFlag(value: Boolean) {
    DeepGwt.contextoProtegido
        .getSharedPreferences("deepgwt_prefs", Context.MODE_PRIVATE)
        .edit { putBoolean(Preferencias.UPDATE_INSTALL_IN_PROGRESS.chave, value) }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
