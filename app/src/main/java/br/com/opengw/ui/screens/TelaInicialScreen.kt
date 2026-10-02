package br.com.opengw.ui.screens

import br.com.opengw.ui.Cores
import br.com.opengw.nucleo.OpenGw
import br.com.opengw.licenca.Recurso
import br.com.opengw.ui.components.TravaDoNivel
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import br.com.opengw.managers.HomeLauncherController
import br.com.opengw.managers.VehicleIdentity
import br.com.opengw.nucleo.Preferencias
import br.com.opengw.ui.GlossyCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Tela Inicial": qual launcher aparece quando o carro liga.
 *
 * Existe porque o launcher 3D (com.havalh6.viewer) substitui a tela de fábrica inteira. Quem
 * instala precisa de um lugar óbvio para voltar atrás sem depender do computador — e o único
 * lugar que continua acessível com o launcher novo no ar é o próprio OPEN GW.
 *
 * A troca em si é `cmd package set-home-activity` via Shizuku (ver [HomeLauncherController]).
 */
@Composable
fun TelaInicialTab() {
    val context = OpenGw.contexto
    val scope = rememberCoroutineScope()

    var candidates by remember { mutableStateOf<List<HomeLauncherController.HomeCandidate>>(emptyList()) }
    var fixo by remember { mutableStateOf(false) }
    var carregando by remember { mutableStateOf(true) }
    var erro by remember { mutableStateOf<String?>(null) }
    var sucesso by remember { mutableStateOf<String?>(null) }
    /** Pacote cuja troca está em andamento; `null` quando não há nenhuma. */
    var trocando by remember { mutableStateOf<String?>(null) }
    var identidade by remember { mutableStateOf<VehicleIdentity.Resolution?>(null) }

    val prefs = remember {
        OpenGw.contextoProtegido
            .getSharedPreferences("opengw_prefs", android.content.Context.MODE_PRIVATE)
    }
    var comandosLiberados by remember {
        mutableStateOf(prefs.getBoolean(Preferencias.VIEWER_COMMANDS_ENABLED.chave, true))
    }

    suspend fun recarregar() {
        val lidos = withContext(Dispatchers.IO) { HomeLauncherController.candidates(context) }
        fixo = withContext(Dispatchers.IO) { HomeLauncherController.launcher3DFixo() }
        val id = withContext(Dispatchers.IO) { runCatching { VehicleIdentity.resolve(context) }.getOrNull() }
        candidates = lidos
        identidade = id
        carregando = false
    }

    LaunchedEffect(Unit) { recarregar() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        GlossyCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Tela inicial do carro",
                    color = Cores.textoPrincipal,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Escolhe qual aplicativo aparece ao ligar o veículo. Nada é desinstalado: o " +
                        "launcher de fábrica continua no carro e volta a qualquer momento por esta mesma tela.",
                    color = Cores.textoSecundario,
                    fontSize = 12.sp
                )

                when {
                    carregando -> Text("Lendo…", color = Cores.textoApagado, fontSize = 12.sp)
                    candidates.isEmpty() -> Text(
                        "Nenhum aplicativo se oferece como tela inicial nesta central.",
                        color = Cores.atencao,
                        fontSize = 12.sp
                    )
                    else -> candidates.forEach { original ->
                        // Com o launcher 3D escolhido, ele é o atual mesmo que a preferência do
                        // Android diga outra coisa: quem o mantém na frente é a guarda (7.8).
                        val candidato = if (fixo) {
                            original.copy(isCurrent = original.packageName == HomeLauncherController.VIEWER_PACKAGE)
                        } else original
                        // Alpha 8.0: escolher o 3D é do Sport; voltar à de fábrica é sempre livre.
                        val recursoDaLinha =
                            if (candidato.packageName == HomeLauncherController.VIEWER_PACKAGE) Recurso.LAUNCHER_3D
                            else Recurso.ESSENCIAL
                        TravaDoNivel(recursoDaLinha, Modifier.fillMaxWidth()) {
                        LauncherLinha(
                            candidato = candidato,
                            ocupado = trocando != null,
                            trocandoAgora = trocando == candidato.packageName,
                            onSelecionar = {
                                scope.launch {
                                    erro = null
                                    sucesso = null
                                    trocando = candidato.packageName
                                    val resultado = withContext(Dispatchers.IO) {
                                        HomeLauncherController.setHome(candidato.packageName, context)
                                    }
                                    resultado
                                        .onFailure { erro = it.message }
                                        .onSuccess { detalhe ->
                                            sucesso = if (candidato.packageName == HomeLauncherController.VIEWER_PACKAGE) {
                                                "Tela inicial agora é o launcher 3D: $detalhe."
                                            } else {
                                                "Tela inicial agora é ${candidato.label}."
                                            }
                                        }
                                    trocando = null
                                    recarregar()
                                }
                            }
                        )
                        }
                    }
                }

                // Antes desta tela não dizer nada era o defeito: clicar num launcher que o sistema
                // recusava não produzia nenhum sinal na tela, e o usuário lia isso como "o botão
                // não faz nada". Agora tanto o sucesso quanto a recusa aparecem, com a saída crua
                // do comando na recusa — é a única pista de por que o sistema não aceitou.
                sucesso?.let {
                    Text(it, color = Cores.destaque, fontSize = 12.sp)
                }
                erro?.let {
                    Text("Não deu para trocar: $it", color = Cores.perigo, fontSize = 12.sp)
                }
            }
        }

        // ── Como o launcher 3D fica na frente, e como desfazer (Alpha 7.8) ─────────────────
        if (fixo) {
            GlossyCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Launcher 3D mantido pelo OPEN GW",
                        color = Cores.textoPrincipal,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "A central abre a tela de fábrica por caminhos que ignoram a escolha do Android. " +
                            "Quando ela aparece como tela inicial — carro ligando, tela acordando, tecla ou " +
                            "casinha do painel lateral —, o OPEN GW traz o launcher 3D de volta. Rádio e mídia " +
                            "abertos por você continuam abrindo normalmente.",
                        color = Cores.textoSecundario,
                        fontSize = 12.sp
                    )
                    Text(
                        "Voltar à tela de fábrica",
                        color = Cores.destaque,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = trocando == null) {
                                scope.launch {
                                    erro = null
                                    sucesso = null
                                    trocando = HomeLauncherController.FACTORY_PACKAGE
                                    withContext(Dispatchers.IO) { HomeLauncherController.voltarParaFabrica(context) }
                                        .onFailure { erro = it.message }
                                        .onSuccess { sucesso = "Tela de fábrica de volta. O launcher 3D continua instalado." }
                                    trocando = null
                                    recarregar()
                                }
                            }
                            .padding(vertical = 8.dp)
                    )
                    Text(
                        "Outros jeitos de desfazer: no painel do próprio launcher 3D (Voltar para a tela de " +
                            "fábrica), no instalador pelo computador (Ajustes → Launcher 3D → Launcher 3D como " +
                            "tela inicial) ou, com o cabo, " +
                            "adb shell am broadcast -a br.com.opengw.VOLTAR_TELA_DE_FABRICA " +
                            "-p br.com.opengw",
                        color = Cores.textoApagado,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // ── Modelo do carro, usado pelo launcher 3D ─────────────────────────────────────────
        GlossyCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Modelo para o launcher 3D",
                    color = Cores.textoPrincipal,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                val id = identidade
                if (id == null) {
                    Text("Sem leitura de identidade.", color = Cores.textoApagado, fontSize = 12.sp)
                } else {
                    val rotulo = id.trim?.uppercase() ?: "ainda não definido"
                    Text(
                        "Carroceria: $rotulo" + when (id.source) {
                            "user" -> " (escolhido por você)"
                            "reported" -> " (lido do carro)"
                            else -> ""
                        },
                        color = if (id.trim == null) Cores.atencao else Cores.textoSecundario,
                        fontSize = 12.sp
                    )
                    Text(id.reason, color = Cores.textoApagado, fontSize = 11.sp)
                    if (id.trim != null && id.source == "user") {
                        Text(
                            "Esquecer escolha e perguntar de novo",
                            color = Cores.destaque,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable {
                                scope.launch {
                                    withContext(Dispatchers.IO) { VehicleIdentity.clearSelection(context) }
                                    recarregar()
                                }
                            }
                        )
                    }
                }
            }
        }

        // ── Escrita no veículo vinda do launcher ────────────────────────────────────────────
        GlossyCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.padding(end = 12.dp)) {
                        Text(
                            "Comandos do launcher",
                            color = Cores.textoPrincipal,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Switch(
                            checked = comandosLiberados,
                            onCheckedChange = { ligado ->
                                comandosLiberados = ligado
                                prefs.edit {
                                    putBoolean(Preferencias.VIEWER_COMMANDS_ENABLED.chave, ligado)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Cores.sobreODestaque,
                                checkedTrackColor = Cores.destaque,
                                uncheckedThumbColor = Cores.bolinhaDesligada,
                                uncheckedTrackColor = Cores.trilhoDesligado
                            )
                        )
                    }
                }
                Text(
                    "Deixa o launcher 3D abrir e fechar vidros, teto solar, cortina e porta-malas " +
                        "pelos botões do modelo na tela. Desligado, o carro só é lido — nada é " +
                        "movido. As portas laterais continuam de fora de propósito.",
                    color = Cores.textoSecundario,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun LauncherLinha(
    candidato: HomeLauncherController.HomeCandidate,
    ocupado: Boolean,
    trocandoAgora: Boolean,
    onSelecionar: () -> Unit
) {
    val destaque = when (candidato.packageName) {
        HomeLauncherController.VIEWER_PACKAGE -> "Launcher 3D"
        HomeLauncherController.FACTORY_PACKAGE -> "Tela de fábrica"
        else -> null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (candidato.isCurrent) Cores.realceDoItemAtivo else Color.Transparent)
            .clickable(enabled = !candidato.isCurrent && !ocupado) { onSelecionar() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (candidato.isCurrent) Icons.Outlined.CheckCircle else Icons.Outlined.Home,
            contentDescription = null,
            tint = if (candidato.isCurrent) Cores.destaque else Cores.textoApagado,
            modifier = Modifier.size(20.dp)
        )
        Column(Modifier.padding(start = 12.dp)) {
            Text(
                candidato.label,
                color = Cores.textoPrincipal,
                fontSize = 14.sp,
                fontWeight = if (candidato.isCurrent) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(
                listOfNotNull(
                    if (trocandoAgora) "trocando…" else null,
                    destaque,
                    candidato.packageName
                ).joinToString(" · "),
                color = if (trocandoAgora) Cores.destaque else Cores.textoApagado,
                fontSize = 11.sp
            )
        }
    }
}
