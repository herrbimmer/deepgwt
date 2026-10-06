// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui.screens

import br.com.deepgwt.ui.Fontes
import br.com.deepgwt.ui.Cores
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.deepgwt.managers.DriverProfileManager
import br.com.deepgwt.models.DriverProfile
import br.com.deepgwt.perfis.RelatorioDeAplicacao
import br.com.deepgwt.ui.liquidGlass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DriverProfilesScreen(onBackToFeatures: () -> Unit) {
    var profiles by remember { mutableStateOf(DriverProfileManager.listProfiles()) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<DriverProfile?>(null) }
    var autoRecall by remember { mutableStateOf(DriverProfileManager.isAutoRecallEnabled()) }
    var aplicarAoLigar by remember { mutableStateOf(DriverProfileManager.aplicaPerfilAoLigar()) }
    var activeName by remember { mutableStateOf(DriverProfileManager.getActiveProfileName()) }
    var relatorio by remember { mutableStateOf(DriverProfileManager.ultimoRelatorio) }
    var aplicando by remember { mutableStateOf(false) }
    val escopo = rememberCoroutineScope()

    fun refresh() {
        profiles = DriverProfileManager.listProfiles()
        activeName = DriverProfileManager.getActiveProfileName()
        relatorio = DriverProfileManager.ultimoRelatorio
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "PERFIS DE MOTORISTA",
                    fontFamily = Fontes.titulo,
                    fontSize = 20.sp,
                    letterSpacing = 1.sp,
                    color = Cores.textoPrincipal
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Guarda TUDO o que você configurou no DeepGWT — tema do painel, HUD, ASD, barra, " +
                        "launcher, atalhos — mais as configurações do próprio carro que o barramento " +
                        "expõe, e a memória de posição do banco. Trocar de perfil devolve o carro inteiro.",
                    fontFamily = Fontes.texto,
                    fontSize = 13.sp,
                    color = Cores.textoSecundario
                )
            }
            Button(
                onClick = { showSaveDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Cores.destaque),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = Cores.sobreODestaque)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Novo perfil", color = Cores.sobreODestaque, fontWeight = FontWeight.SemiBold)
            }
        }

        relatorio?.let { CartaoDeRelatorio(it) }

        InterruptorDePerfil(
            titulo = "Aplicar memória de banco ao ligar",
            descricao = (if (activeName != null)
                "Ao ligar o carro, chama a memória de posição do banco do perfil \"$activeName\"."
            else
                "Aplique um perfil para defini-lo como ativo.") +
                " Quem move o banco é o carro, pela memória dele — o DeepGWT só pede.",
            ligado = autoRecall,
            aoMudar = {
                autoRecall = it
                DriverProfileManager.setAutoRecallEnabled(it)
            }
        )

        InterruptorDePerfil(
            titulo = "Reaplicar o perfil inteiro ao ligar",
            descricao = "Desnecessário na maioria dos casos: o perfil ativo continua aplicado " +
                "depois de desligar o carro. Ligue se alguém mexe nas configurações pelo menu da " +
                "Haval e você quer o carro de volta ao seu jeito a cada partida.",
            ligado = aplicarAoLigar,
            aoMudar = {
                aplicarAoLigar = it
                DriverProfileManager.setAplicaPerfilAoLigar(it)
            }
        )

        if (profiles.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().liquidGlass().padding(24.dp)) {
                Text(
                    "Nenhum perfil ainda. Deixe o carro e o DeepGWT do jeito que você gosta e toque " +
                        "em \"Novo perfil\" — o que estiver valendo neste instante vira o seu perfil.",
                    fontFamily = Fontes.texto,
                    fontSize = 14.sp,
                    color = Cores.textoSecundario
                )
            }
        } else {
            profiles.forEach { profile ->
                Row(
                    modifier = Modifier.fillMaxWidth().liquidGlass().padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.DirectionsCar, contentDescription = null, tint = Cores.destaque)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            profile.name,
                            fontFamily = Fontes.texto,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = Cores.textoPrincipal
                        )
                        Text(
                            descreverPerfil(profile, profile.name == activeName),
                            fontFamily = Fontes.texto,
                            fontSize = 12.sp,
                            color = if (profile.name == activeName) Cores.destaque else Cores.textoSecundario
                        )
                        if (profile.ehLegado) {
                            Text(
                                "Perfil antigo: guarda só configurações do carro. Toque no ícone de " +
                                    "atualizar para ele passar a guardar o DeepGWT também.",
                                fontFamily = Fontes.texto,
                                fontSize = 11.sp,
                                color = Cores.textoApagado
                            )
                        }
                    }
                    TextButton(
                        enabled = !aplicando,
                        onClick = {
                            // Fora da thread de interface: aplicar lê as ~800 chaves do carro e
                            // escreve as que mudaram. Na main isso é ANR, não lentidão.
                            aplicando = true
                            escopo.launch {
                                val r = withContext(Dispatchers.IO) {
                                    DriverProfileManager.aplicar(profile.name)
                                }
                                relatorio = r
                                activeName = profile.name
                                aplicando = false
                            }
                        }
                    ) {
                        Text(if (aplicando) "Aplicando…" else "Aplicar", color = Cores.destaque, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(enabled = !aplicando, onClick = {
                        // Regrava o perfil com o estado ATUAL, mantendo nome e slot de banco.
                        aplicando = true
                        escopo.launch {
                            withContext(Dispatchers.IO) {
                                DriverProfileManager.saveCurrentAsProfile(profile.name, profile.seatMemorySlot)
                            }
                            refresh()
                            aplicando = false
                        }
                    }) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = "Atualizar com o estado atual",
                            tint = Cores.textoSecundario
                        )
                    }
                    IconButton(onClick = { pendingDelete = profile }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Excluir", tint = Cores.perigo)
                    }
                }
            }
        }

        Text(
            "O que NÃO viaja no perfil: o modo sorrateiro, a identidade do " +
                "carro, os ids de tela desta central, o aparelho de luz ambiente pareado e a " +
                "quilometragem de revisão. São coisas do carro ou da instalação, não de quem dirige.",
            fontFamily = Fontes.texto,
            fontSize = 12.sp,
            color = Cores.textoApagado
        )
    }

    if (showSaveDialog) {
        SaveProfileDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { name, slot ->
                showSaveDialog = false
                aplicando = true
                escopo.launch {
                    // Tirar o retrato lê o barramento inteiro: fora da thread de interface.
                    withContext(Dispatchers.IO) { DriverProfileManager.saveCurrentAsProfile(name, slot) }
                    refresh()
                    aplicando = false
                }
            }
        )
    }

    pendingDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Excluir \"${profile.name}\"?") },
            text = { Text("Essa ação não pode ser desfeita. O carro continua como está agora — excluir um perfil não desfaz nada.") },
            confirmButton = {
                TextButton(onClick = {
                    DriverProfileManager.deleteProfile(profile.name)
                    pendingDelete = null
                    refresh()
                }) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") }
            }
        )
    }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

private fun descreverPerfil(profile: DriverProfile, ativo: Boolean): String {
    val partes = mutableListOf<String>()
    if (profile.deepGwt.isNotEmpty()) partes.add("${profile.deepGwt.size} ajustes do DeepGWT")
    val carro = profile.chavesDoVeiculo().size
    if (carro > 0) partes.add("$carro do carro")
    profile.seatMemorySlot?.let { partes.add("banco $it") }
    if (partes.isEmpty()) partes.add("vazio")
    if (ativo) partes.add("ativo")
    return partes.joinToString(" · ")
}

@Composable
private fun InterruptorDePerfil(
    titulo: String,
    descricao: String,
    ligado: Boolean,
    aoMudar: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().liquidGlass().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                titulo,
                fontFamily = Fontes.texto,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = Cores.textoPrincipal
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(descricao, fontFamily = Fontes.texto, fontSize = 12.sp, color = Cores.textoSecundario)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = ligado,
            onCheckedChange = aoMudar,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Cores.sobreODestaque,
                checkedTrackColor = Cores.destaque
            )
        )
    }
}

/**
 * O resultado da última aplicação.
 *
 * Fica na tela mesmo depois do aviso sumir, porque o pedido é explícito: configuração que não
 * pôde ser aplicada tem de ser informada, não engolida. Quando tudo deu certo continua aparecendo
 * — em verde e numa linha — para a troca de perfil ter confirmação visível.
 */
@Composable
private fun CartaoDeRelatorio(relatorio: RelatorioDeAplicacao) {
    val detalhes = relatorio.detalhes()
    Column(
        modifier = Modifier.fillMaxWidth().liquidGlass().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            relatorio.resumo(),
            fontFamily = Fontes.texto,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = if (relatorio.tudoCerto) Cores.destaque else Cores.perigo
        )
        detalhes.forEach { linha ->
            Text(linha, fontFamily = Fontes.texto, fontSize = 12.sp, color = Cores.textoSecundario)
        }
    }
}

@Composable
private fun SaveProfileDialog(onDismiss: () -> Unit, onSave: (String, Int?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var slotText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo perfil com o estado atual") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do motorista") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors()
                )
                OutlinedTextField(
                    value = slotText,
                    onValueChange = { slotText = it.filter { c -> c.isDigit() } },
                    label = { Text("Slot de memória do banco (opcional, 1-3)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors()
                )
                Text(
                    "O nome aparece no launcher 3D e na saudação ao ligar o carro. Informar o slot " +
                        "grava a posição do banco de agora na memória do próprio veículo — a " +
                        "posição não viaja como dado; quem a guarda e quem a devolve é o carro.",
                    fontFamily = Fontes.texto,
                    fontSize = 12.sp,
                    color = Cores.textoApagado
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(name.trim(), slotText.toIntOrNull()) }
            ) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
