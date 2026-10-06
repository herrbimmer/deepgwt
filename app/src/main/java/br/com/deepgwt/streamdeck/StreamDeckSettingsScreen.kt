// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.streamdeck

import br.com.deepgwt.ui.CartaoClassico
import br.com.deepgwt.ui.MedidasClassicas
import br.com.deepgwt.ui.CoresClassicas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Physical layout of the deck: 3 rows x 5 keys, plus a 3-cell vertical strip (the side screen) on the right. */
private val MAIN_GRID_ROWS = listOf(
    listOf(1, 2, 3, 4, 5),
    listOf(6, 7, 8, 9, 10),
    listOf(11, 12, 13, 14, 15)
)
private val SIDE_STRIP_KEYS = listOf(16, 17, 18)

@Composable
fun StreamDeckSettingsScreen(
    onBackToFeatures: () -> Unit,
    /** Quando a tela mora dentro de "Botões extra", o cabeçalho (voltar, título e escolha da fonte) vem de fora. */
    cabecalho: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(StreamDeckSettings.isEnabled()) }
    var bindings by remember { mutableStateOf(StreamDeckSettings.allBindings()) }
    var editingSlot by remember { mutableIntStateOf(-1) }
    var confirmingReset by remember { mutableStateOf(false) }

    // Drag-and-drop: palette chips report their live position in root coordinates as `dragPosition`
    // while being dragged; grid cells report their own bounds via onGloballyPositioned so a drop
    // can be resolved by simple hit-testing, no shared drag-and-drop API needed between them.
    var draggedAction by remember { mutableStateOf<StreamDeckKeyAction?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    val cellBounds = remember { mutableStateMapOf<Int, Rect>() }

    fun refresh() {
        bindings = StreamDeckSettings.allBindings()
    }

    fun dropDraggedActionHere(action: StreamDeckKeyAction) {
        val slot = cellBounds.entries.firstOrNull { (_, rect) -> rect.contains(dragPosition) }?.key
        if (slot != null) {
            StreamDeckSettings.setActionForKey(slot, action)
            refresh()
            StreamDeckService.refreshIcons(context)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CoresClassicas.fundo)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (cabecalho != null) {
            cabecalho()
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackToFeatures) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = CoresClassicas.texto)
                }
                Column {
                    Text("Stream Deck", color = CoresClassicas.texto, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Deck físico USB (RiseMode / MiraBox StreamDock 293S) mapeado para funções do carro.",
                        color = CoresClassicas.textoSecundario,
                        fontSize = 16.sp
                    )
                }
            }
        }

        CartaoClassico {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Recurso experimental", color = CoresClassicas.texto, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Protocolo USB reconstruído por engenharia reversa, sem spec oficial. Se não funcionar, desative aqui.",
                            color = CoresClassicas.textoSecundario,
                            fontSize = 14.sp
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { checked ->
                            StreamDeckSettings.setEnabled(checked)
                            enabled = checked
                            if (checked) {
                                StreamDeckService.start(context)
                            } else {
                                StreamDeckService.stop(context)
                            }
                        }
                    )
                }
                if (enabled) {
                    OutlinedButton(onClick = { StreamDeckService.refreshIcons(context) }) {
                        Text("Reenviar ícones para o deck")
                    }
                }
            }
        }

        if (enabled) {
            CartaoClassico {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Layout do dock", color = CoresClassicas.texto, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Toque numa tecla para escolher a função. A grade reproduz o desenho físico: 3x5 teclas + tela lateral (3 células) à direita.",
                                color = CoresClassicas.textoSecundario,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MAIN_GRID_ROWS.forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { slot ->
                                        KeyGridCell(
                                            modifier = Modifier
                                                .weight(1f)
                                                .onGloballyPositioned { coords -> cellBounds[slot] = coords.boundsInRoot() },
                                            slot = slot,
                                            action = bindings[slot],
                                            highlighted = draggedAction != null && cellBounds[slot]?.contains(dragPosition) == true,
                                            onClick = { editingSlot = slot }
                                        )
                                    }
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Tela lateral", color = CoresClassicas.textoSecundario, fontSize = 12.sp)
                            SIDE_STRIP_KEYS.forEach { slot ->
                                KeyGridCell(
                                    modifier = Modifier
                                        .width(76.dp)
                                        .onGloballyPositioned { coords -> cellBounds[slot] = coords.boundsInRoot() },
                                    slot = slot,
                                    action = bindings[slot],
                                    highlighted = draggedAction != null && cellBounds[slot]?.contains(dragPosition) == true,
                                    onClick = { editingSlot = slot }
                                )
                            }
                        }
                    }

                    OutlinedButton(onClick = { confirmingReset = true }) {
                        Text("Restaurar layout padrão")
                    }
                }
            }

            CartaoClassico {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Funções disponíveis", color = CoresClassicas.texto, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Arraste uma função e solte em cima de uma tecla para atribuir — a tecla de destino acende enquanto você arrasta.",
                        color = CoresClassicas.textoSecundario,
                        fontSize = 14.sp
                    )
                    PaletteFlow(
                        actions = StreamDeckKeyAction.entries,
                        onDragStart = { action, rootOffset ->
                            draggedAction = action
                            dragPosition = rootOffset
                        },
                        onDrag = { delta -> dragPosition += delta },
                        onDragEnd = { action ->
                            dropDraggedActionHere(action)
                            draggedAction = null
                        }
                    )
                }
            }
        }
    }

    draggedAction?.let { action ->
        Box(
            modifier = Modifier
                .offset { IntOffset((dragPosition.x - 48).roundToInt(), (dragPosition.y - 22).roundToInt()) }
                .width(96.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(MedidasClassicas.cantoDaBorda))
                .background(CoresClassicas.superficie)
                .border(MedidasClassicas.espessuraDaBorda, CoresClassicas.borda, RoundedCornerShape(MedidasClassicas.cantoDaBorda))
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                action.label,
                color = CoresClassicas.texto,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    }

    if (editingSlot in 1..StreamDeckProtocol.KEY_COUNT) {
        ActionPickerDialog(
            currentAction = bindings[editingSlot],
            onDismiss = { editingSlot = -1 },
            onSelect = { action ->
                StreamDeckSettings.setActionForKey(editingSlot, action)
                refresh()
                editingSlot = -1
                StreamDeckService.refreshIcons(context)
            }
        )
    }

    if (confirmingReset) {
        AlertDialog(
            onDismissRequest = { confirmingReset = false },
            title = { Text("Restaurar layout padrão?") },
            text = {
                Text(
                    "Isso substitui todas as teclas configuradas: A/C motorista +/- com a " +
                        "temperatura interna entre elas, A/C passageiro +/-, volume +/-, SPORT+ e " +
                        "liga/desliga A/C na grade principal. A tela lateral e o restante da " +
                        "terceira linha ficam livres para você escolher."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    StreamDeckSettings.applyDefaultLayout()
                    refresh()
                    confirmingReset = false
                    StreamDeckService.refreshIcons(context)
                }) { Text("Restaurar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingReset = false }) { Text("Cancelar") }
            }
        )
    }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

@Composable
private fun KeyGridCell(
    slot: Int,
    action: StreamDeckKeyAction?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false
) {
    val assigned = action != null
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(MedidasClassicas.cantoDaBorda))
            .background(if (assigned) CoresClassicas.superficie else CoresClassicas.cartao)
            .border(
                width = if (highlighted) MedidasClassicas.espessuraDaBorda * 2 else MedidasClassicas.espessuraDaBorda,
                color = if (highlighted) CoresClassicas.texto else CoresClassicas.borda,
                shape = RoundedCornerShape(MedidasClassicas.cantoDaBorda)
            )
            .clickable(onClick = onClick)
            .padding(6.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(slot.toString(), color = CoresClassicas.textoSecundario, fontSize = 11.sp)
        Text(
            action?.label ?: "—",
            color = if (assigned) CoresClassicas.texto else CoresClassicas.textoDesabilitado,
            fontSize = 11.sp,
            fontWeight = if (assigned) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PaletteFlow(
    actions: List<StreamDeckKeyAction>,
    onDragStart: (StreamDeckKeyAction, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: (StreamDeckKeyAction) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.chunked(3).forEach { rowActions ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowActions.forEach { action ->
                    PaletteChip(
                        action = action,
                        onDragStart = { offset -> onDragStart(action, offset) },
                        onDrag = onDrag,
                        onDragEnd = { onDragEnd(action) }
                    )
                }
            }
        }
    }
}

/** A draggable chip for one function; reports its drag position in root coordinates so the caller can hit-test it against grid cells without any shared drag-and-drop API between the two. */
@Composable
private fun PaletteChip(
    action: StreamDeckKeyAction,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    var rootTopLeft by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = Modifier
            .width(104.dp)
            .onGloballyPositioned { coords -> rootTopLeft = coords.boundsInRoot().topLeft }
            .clip(RoundedCornerShape(MedidasClassicas.cantoDaBorda))
            .background(CoresClassicas.cartao)
            .border(MedidasClassicas.espessuraDaBorda, CoresClassicas.borda, RoundedCornerShape(MedidasClassicas.cantoDaBorda))
            .pointerInput(action) {
                detectDragGestures(
                    onDragStart = { local -> onDragStart(rootTopLeft + local) },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                )
            }
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            action.label,
            color = CoresClassicas.texto,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ActionPickerDialog(
    currentAction: StreamDeckKeyAction?,
    onDismiss: () -> Unit,
    onSelect: (StreamDeckKeyAction?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fechar") }
        },
        title = { Text("Escolher função da tecla") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                item {
                    ActionOptionRow(label = "Sem função", selected = currentAction == null) { onSelect(null) }
                }
                items(StreamDeckKeyAction.entries) { action ->
                    ActionOptionRow(label = action.label, selected = action == currentAction) { onSelect(action) }
                }
            }
        }
    )
}

@Composable
private fun ActionOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Text("✓")
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
