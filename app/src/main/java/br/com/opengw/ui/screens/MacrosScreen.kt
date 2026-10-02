package br.com.opengw.ui.screens

import br.com.opengw.telas.CampoDeApp
import br.com.opengw.ui.telas.SeletorDeApp
import br.com.opengw.ui.Fontes
import br.com.opengw.ui.Cores
import br.com.opengw.veiculo.Veiculo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.opengw.macros.CarSignal
import br.com.opengw.macros.CarSignalCatalog
import br.com.opengw.macros.GestureStore
import br.com.opengw.macros.Macro
import br.com.opengw.macros.MacroAction
import br.com.opengw.macros.MacroActionCatalog
import br.com.opengw.macros.MacroActionParam
import br.com.opengw.macros.MacroActionSpec
import br.com.opengw.macros.MacroComparator
import br.com.opengw.macros.MacroEngine
import br.com.opengw.macros.MacroStore
import br.com.opengw.macros.MacroTrigger
import br.com.opengw.macros.SignalCondition
import br.com.opengw.ui.liquidGlass

/**
 * Macros: "quando o carro fizer X, faça Y, Y e Y".
 *
 * ## O desenho da tela segue o desenho da frase
 *
 * Uma macro é uma frase com três partes — gatilho, condições, ações — e o editor as empilha nessa
 * ordem, uma por cartão. Um formulário com todos os campos soltos obrigaria o motorista a montar a
 * frase de cabeça antes de preencher; empilhado, ele lê o que está construindo enquanto constrói.
 *
 * ## O que esta tela DELIBERADAMENTE não faz
 *
 * Não valida se a combinação faz sentido no carro. "Abrir o teto solar quando a velocidade passar
 * de 120" é gravável. Isso é escolha, não descuido: não existe tabela confiável do que cada
 * atuador aceita em cada estado — o próprio carro recusa em silêncio o que não pode fazer (ver
 * `ServiceManager.openSkylightWithShade`), e um aviso inventado aqui seria palpite com cara de
 * regra. O que a tela faz é mostrar o valor ATUAL de cada sinal ao lado do campo, para a condição
 * ser escrita olhando para o número de verdade.
 */
@Composable
fun MacrosScreen(onBackToFeatures: () -> Unit) {
    var emEdicao by remember { mutableStateOf<Macro?>(null) }
    var mostrandoGestos by remember { mutableStateOf(false) }

    when {
        mostrandoGestos -> GestureRecorderScreen(
            onBack = { mostrandoGestos = false },
            onCriarMacro = { gesto ->
                mostrandoGestos = false
                emEdicao = Macro(
                    name = gesto.name,
                    trigger = MacroTrigger.Gesture(gesto.id)
                )
            }
        )
        emEdicao != null -> EditorDeMacro(
            inicial = emEdicao!!,
            onCancelar = { emEdicao = null },
            onSalvar = {
                MacroStore.salvar(it)
                MacroEngine.reconciliar("salvou '${it.name}'")
                emEdicao = null
            },
            onAbrirGestos = { mostrandoGestos = true }
        )
        else -> ListaDeMacros(
            onBackToFeatures = onBackToFeatures,
            onNova = { emEdicao = Macro() },
            onEditar = { emEdicao = it },
            onAbrirGestos = { mostrandoGestos = true }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────────────────────
// Lista
// ─────────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun ListaDeMacros(
    onBackToFeatures: () -> Unit,
    onNova: () -> Unit,
    onEditar: (Macro) -> Unit,
    onAbrirGestos: () -> Unit
) {
    val macros = MacroStore.macros
    var armadas by remember { mutableStateOf(MacroStore.isEnabled()) }
    var pendenteDeExclusao by remember { mutableStateOf<Macro?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackToFeatures) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Cores.textoPrincipal)
            }
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    "MACROS",
                    fontFamily = Fontes.titulo,
                    fontSize = 20.sp,
                    letterSpacing = 1.sp,
                    color = Cores.textoPrincipal
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Quando o carro fizer alguma coisa — ou quando você desenhar um gesto na tela — " +
                        "o OPEN GW executa a fila de ações que você montar.",
                    fontFamily = Fontes.texto,
                    fontSize = 13.sp,
                    color = Cores.textoSecundario
                )
            }
            Button(
                onClick = onAbrirGestos,
                colors = ButtonDefaults.buttonColors(containerColor = Cores.conteiner),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.Gesture, contentDescription = null, tint = Cores.destaque)
                Spacer(Modifier.width(6.dp))
                Text("Gestos", color = Cores.textoPrincipal, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onNova,
                colors = ButtonDefaults.buttonColors(containerColor = Cores.destaque),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = Cores.sobreODestaque)
                Spacer(Modifier.width(6.dp))
                Text("Nova macro", color = Cores.sobreODestaque, fontWeight = FontWeight.SemiBold)
            }
        }

        MacroInterruptor(
            titulo = "Macros armadas",
            descricao = "Desligado, nada dispara sozinho e nenhum gesto executa — as macros ficam " +
                "gravadas, apenas paradas. O botão \"Executar\" de cada uma continua funcionando.",
            ligado = armadas,
            aoMudar = {
                armadas = it
                MacroStore.setEnabled(it)
                MacroEngine.reconciliar(if (it) "macros armadas" else "macros desarmadas")
            }
        )

        if (macros.isEmpty()) {
            CartaoVazio(
                titulo = "Nenhuma macro ainda",
                texto = "Toque em \"Nova macro\". Um exemplo para começar: gatilho \"Velocidade " +
                    "passar de 20 km/h\", ação \"Fechar todos os vidros\"."
            )
        } else {
            macros.forEach { macro ->
                CartaoDeMacro(
                    macro = macro,
                    onEditar = { onEditar(macro) },
                    onApagar = { pendenteDeExclusao = macro },
                    onAlternar = { ligada ->
                        MacroStore.salvar(macro.copy(enabled = ligada))
                        MacroEngine.reconciliar("alternou '${macro.name}'")
                    },
                    onExecutar = { MacroEngine.executar(macro, "botão", ignorarJanela = true) }
                )
            }
        }
    }

    pendenteDeExclusao?.let { alvo ->
        AlertDialog(
            onDismissRequest = { pendenteDeExclusao = null },
            title = { Text("Apagar \"${alvo.name}\"?") },
            text = { Text("A macro e a fila de ações dela somem. O gesto, se houver, continua gravado.") },
            confirmButton = {
                TextButton(onClick = {
                    MacroStore.apagar(alvo.id)
                    MacroEngine.reconciliar("apagou '${alvo.name}'")
                    pendenteDeExclusao = null
                }) { Text("Apagar", color = Cores.perigo) }
            },
            dismissButton = { TextButton(onClick = { pendenteDeExclusao = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun CartaoDeMacro(
    macro: Macro,
    onEditar: () -> Unit,
    onApagar: () -> Unit,
    onAlternar: (Boolean) -> Unit,
    onExecutar: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().liquidGlass().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (macro.trigger is MacroTrigger.Gesture) Icons.Outlined.Gesture else Icons.Outlined.Bolt,
                contentDescription = null,
                tint = if (macro.enabled) Cores.destaque else Cores.textoApagado,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    macro.name,
                    fontFamily = Fontes.texto,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (macro.enabled) Cores.textoPrincipal else Cores.textoApagado
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    descreverGatilho(macro),
                    fontFamily = Fontes.texto,
                    fontSize = 13.5.sp,
                    color = Cores.textoSecundario
                )
            }
            IconButton(onClick = onExecutar) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = "Executar agora", tint = Cores.destaque)
            }
            TextButton(onClick = onEditar) { Text("Editar", color = Cores.destaque) }
            IconButton(onClick = onApagar) {
                Icon(Icons.Outlined.Delete, contentDescription = "Apagar", tint = Cores.perigo)
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = macro.enabled,
                onCheckedChange = onAlternar,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Cores.sobreODestaque,
                    checkedTrackColor = Cores.destaque,
                    uncheckedThumbColor = Cores.bolinhaDesligada,
                    uncheckedTrackColor = Cores.trilhoDesligado
                )
            )
        }
        if (macro.actions.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Cores.linhaFina)
            Spacer(Modifier.height(12.dp))
            macro.actions.take(4).forEach { acao ->
                Text(
                    "· ${MacroActionCatalog.descrever(acao)}",
                    fontFamily = Fontes.texto,
                    fontSize = 13.sp,
                    color = Cores.textoSecundario
                )
            }
            if (macro.actions.size > 4) {
                Text(
                    "· e mais ${macro.actions.size - 4}",
                    fontFamily = Fontes.texto,
                    fontSize = 13.sp,
                    color = Cores.textoApagado
                )
            }
        }
    }
}

private fun descreverGatilho(macro: Macro): String {
    val base = when (val t = macro.trigger) {
        is MacroTrigger.Signal -> {
            val sinal = CarSignalCatalog.porChave(t.condition.key)
            val alvo = sinal.values.firstOrNull { it.first == t.condition.value }?.second
                ?: t.condition.value
            if (t.condition.comparator == MacroComparator.CHANGED) {
                "Quando ${sinal.label} mudar"
            } else {
                "Quando ${sinal.label} ${t.condition.comparator.label} $alvo ${sinal.unit}".trim()
            }
        }
        is MacroTrigger.Gesture ->
            "Gesto \"${GestureStore.porId(t.gestureId)?.name ?: "apagado"}\" na tela"
        MacroTrigger.Manual -> "Só quando você tocar em Executar"
    }
    val extras = if (macro.conditions.isEmpty()) "" else " · ${macro.conditions.size} condição(ões) extra(s)"
    return base + extras
}

// ─────────────────────────────────────────────────────────────────────────────────────────────
// Editor
// ─────────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun EditorDeMacro(
    inicial: Macro,
    onCancelar: () -> Unit,
    onSalvar: (Macro) -> Unit,
    onAbrirGestos: () -> Unit
) {
    var nome by remember { mutableStateOf(inicial.name) }
    var gatilho by remember { mutableStateOf(inicial.trigger) }
    var condicoes by remember { mutableStateOf(inicial.conditions) }
    var acoes by remember { mutableStateOf(inicial.actions) }
    var janela by remember { mutableStateOf(inicial.cooldownSec) }
    var aviso by remember { mutableStateOf(inicial.showFeedback) }

    var escolhendoSinalDoGatilho by remember { mutableStateOf(false) }
    var escolhendoSinalDeCondicao by remember { mutableStateOf(false) }
    var escolhendoAcao by remember { mutableStateOf(false) }
    var acaoExpandida by remember { mutableStateOf(-1) }

    fun montar(): Macro = inicial.copy(
        name = nome.trim().ifBlank { "Macro" },
        trigger = gatilho,
        conditions = condicoes,
        actions = acoes,
        cooldownSec = janela,
        showFeedback = aviso
    )

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancelar) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Cores.textoPrincipal)
            }
            Text(
                "EDITAR MACRO",
                fontFamily = Fontes.titulo,
                fontSize = 20.sp,
                letterSpacing = 1.sp,
                color = Cores.textoPrincipal,
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
            TextButton(onClick = { MacroEngine.executar(montar(), "teste no editor", ignorarJanela = true) }) {
                Text("Executar agora", color = Cores.destaque)
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { onSalvar(montar()) },
                colors = ButtonDefaults.buttonColors(containerColor = Cores.destaque),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Salvar", color = Cores.sobreODestaque, fontWeight = FontWeight.SemiBold) }
        }

        CampoDeTexto(valor = nome, rotulo = "Nome da macro", aoMudar = { nome = it })

        // ── Gatilho ─────────────────────────────────────────────────────────────────────────
        BlocoDoEditor("QUANDO", "O que faz a macro rodar.") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Cada chip so TROCA de tipo; tocar no que ja esta ativo nao faz nada. Sem essa
                // guarda, um toque de confirmacao apagaria a condicao que o motorista acabou de
                // montar e a devolveria ao padrao.
                Chip("Sinal do carro", gatilho is MacroTrigger.Signal) {
                    if (gatilho !is MacroTrigger.Signal) {
                        gatilho = MacroTrigger.Signal(
                            SignalCondition(
                                CarSignalCatalog.curados.first().key, MacroComparator.GTE, "0"
                            )
                        )
                    }
                }
                Chip("Gesto na tela", gatilho is MacroTrigger.Gesture) {
                    if (gatilho !is MacroTrigger.Gesture) {
                        gatilho = MacroTrigger.Gesture(GestureStore.gestos.firstOrNull()?.id.orEmpty())
                    }
                }
                Chip("Só manual", gatilho is MacroTrigger.Manual) { gatilho = MacroTrigger.Manual }
            }
            Spacer(Modifier.height(14.dp))
            when (val t = gatilho) {
                is MacroTrigger.Signal -> EditorDeCondicao(
                    condicao = t.condition,
                    aoMudar = { gatilho = MacroTrigger.Signal(it) },
                    aoEscolherSinal = { escolhendoSinalDoGatilho = true },
                    permitirMudou = true
                )
                is MacroTrigger.Gesture -> {
                    val gestos = GestureStore.gestos
                    if (gestos.isEmpty()) {
                        Text(
                            "Nenhum gesto gravado ainda.",
                            fontFamily = Fontes.texto,
                            fontSize = 14.sp,
                            color = Cores.textoSecundario
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = onAbrirGestos,
                            colors = ButtonDefaults.buttonColors(containerColor = Cores.conteiner),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Gravar um gesto", color = Cores.textoPrincipal) }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SeletorSuspenso(
                                rotulo = "Gesto",
                                valor = t.gestureId,
                                opcoes = gestos.map { it.id to "${it.name} (${it.dedos} dedo(s))" },
                                aoMudar = { gatilho = MacroTrigger.Gesture(it) },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(14.dp))
                            GestureStore.porId(t.gestureId)?.let {
                                MiniaturaDeGesto(it.caminho, modifier = Modifier.size(72.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            TextButton(onClick = onAbrirGestos) { Text("Gerenciar", color = Cores.destaque) }
                        }
                    }
                }
                MacroTrigger.Manual -> Text(
                    "A macro só roda pelo botão Executar. Bom para montar e testar antes de armar.",
                    fontFamily = Fontes.texto,
                    fontSize = 13.5.sp,
                    color = Cores.textoSecundario
                )
            }
        }

        // ── Condições extras ────────────────────────────────────────────────────────────────
        BlocoDoEditor(
            "E SÓ SE",
            "Guardas opcionais. Todas precisam valer no instante do gatilho — um sinal sem leitura " +
                "conta como NÃO atendido."
        ) {
            condicoes.forEachIndexed { i, c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        EditorDeCondicao(
                            condicao = c,
                            aoMudar = { nova ->
                                condicoes = condicoes.toMutableList().also { it[i] = nova }
                            },
                            aoEscolherSinal = {
                                escolhendoSinalDeCondicao = true
                                indiceDeCondicaoEmEdicao = i
                            },
                            permitirMudou = false
                        )
                    }
                    IconButton(onClick = {
                        condicoes = condicoes.toMutableList().also { it.removeAt(i) }
                    }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Remover", tint = Cores.perigo)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            TextButton(onClick = {
                condicoes = condicoes + SignalCondition(
                    CarSignalCatalog.curados.first().key, MacroComparator.EQ, ""
                )
            }) { Text("+ Adicionar condição", color = Cores.destaque) }
        }

        // ── Ações ───────────────────────────────────────────────────────────────────────────
        BlocoDoEditor("FAÇA", "Na ordem. Use \"Esperar\" entre movimentos do mesmo atuador.") {
            if (acoes.isEmpty()) {
                Text(
                    "Nenhuma ação ainda.",
                    fontFamily = Fontes.texto,
                    fontSize = 14.sp,
                    color = Cores.textoApagado
                )
                Spacer(Modifier.height(10.dp))
            }
            acoes.forEachIndexed { i, acao ->
                LinhaDeAcao(
                    acao = acao,
                    expandida = acaoExpandida == i,
                    podeSubir = i > 0,
                    podeDescer = i < acoes.lastIndex,
                    aoAlternar = { acaoExpandida = if (acaoExpandida == i) -1 else i },
                    aoMudar = { nova -> acoes = acoes.toMutableList().also { it[i] = nova } },
                    aoSubir = {
                        acoes = acoes.toMutableList().also { java.util.Collections.swap(it, i, i - 1) }
                        acaoExpandida = -1
                    },
                    aoDescer = {
                        acoes = acoes.toMutableList().also { java.util.Collections.swap(it, i, i + 1) }
                        acaoExpandida = -1
                    },
                    aoRemover = {
                        acoes = acoes.toMutableList().also { it.removeAt(i) }
                        acaoExpandida = -1
                    }
                )
                Spacer(Modifier.height(10.dp))
            }
            Button(
                onClick = { escolhendoAcao = true },
                colors = ButtonDefaults.buttonColors(containerColor = Cores.destaque),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = Cores.sobreODestaque)
                Spacer(Modifier.width(6.dp))
                Text("Adicionar ação", color = Cores.sobreODestaque, fontWeight = FontWeight.SemiBold)
            }
        }

        // ── Ajustes ─────────────────────────────────────────────────────────────────────────
        BlocoDoEditor("AJUSTES", "") {
            Text(
                "Janela morta: $janela s",
                fontFamily = Fontes.texto,
                fontSize = 14.sp,
                color = Cores.textoPrincipal
            )
            Text(
                "Tempo mínimo entre duas execuções automáticas. O carro oscila em volta do valor " +
                    "escolhido, e sem essa folga a macro dispararia a cada leitura.",
                fontFamily = Fontes.texto,
                fontSize = 12.5.sp,
                color = Cores.textoApagado
            )
            Slider(
                value = janela.toFloat(),
                onValueChange = { janela = it.toInt() },
                valueRange = 0f..120f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Cores.destaque,
                    inactiveTrackColor = Cores.trilhoDesligado
                )
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Avisar na tela quando rodar",
                        fontFamily = Fontes.texto,
                        fontSize = 15.sp,
                        color = Cores.textoPrincipal
                    )
                    Text(
                        "Um cartão de dois segundos com o nome da macro. Não rouba toque de app nenhum.",
                        fontFamily = Fontes.texto,
                        fontSize = 12.5.sp,
                        color = Cores.textoApagado
                    )
                }
                Switch(
                    checked = aviso,
                    onCheckedChange = { aviso = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Cores.sobreODestaque,
                        checkedTrackColor = Cores.destaque,
                        uncheckedThumbColor = Cores.bolinhaDesligada,
                        uncheckedTrackColor = Cores.trilhoDesligado
                    )
                )
            }
        }
    }

    if (escolhendoSinalDoGatilho) {
        SeletorDeSinal(
            onEscolher = { sinal ->
                val atual = (gatilho as? MacroTrigger.Signal)?.condition
                gatilho = MacroTrigger.Signal(
                    SignalCondition(
                        key = sinal.key,
                        comparator = atual?.comparator ?: MacroComparator.EQ,
                        value = sinal.values.firstOrNull()?.first ?: ""
                    )
                )
                escolhendoSinalDoGatilho = false
            },
            onFechar = { escolhendoSinalDoGatilho = false }
        )
    }

    if (escolhendoSinalDeCondicao) {
        SeletorDeSinal(
            onEscolher = { sinal ->
                val i = indiceDeCondicaoEmEdicao
                if (i in condicoes.indices) {
                    condicoes = condicoes.toMutableList().also {
                        it[i] = it[i].copy(
                            key = sinal.key,
                            value = sinal.values.firstOrNull()?.first ?: ""
                        )
                    }
                }
                escolhendoSinalDeCondicao = false
            },
            onFechar = { escolhendoSinalDeCondicao = false }
        )
    }

    if (escolhendoAcao) {
        SeletorDeAcao(
            onEscolher = { spec ->
                acoes = acoes + MacroActionCatalog.instanciar(spec)
                acaoExpandida = acoes.lastIndex
                escolhendoAcao = false
            },
            onFechar = { escolhendoAcao = false }
        )
    }
}

/**
 * Qual condição extra está com o seletor de sinal aberto.
 *
 * Mora fora do composable de propósito: é um detalhe de uma caixa de diálogo que vive um toque, e
 * subi-lo para um `remember` na função obrigaria a passá-lo por três níveis de composable só para
 * o diálogo saber em que linha escrever.
 */
private var indiceDeCondicaoEmEdicao = -1

// ─────────────────────────────────────────────────────────────────────────────────────────────
// Peças do editor
// ─────────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun BlocoDoEditor(titulo: String, descricao: String, conteudo: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().liquidGlass().padding(22.dp)) {
        Text(
            titulo,
            fontFamily = Fontes.texto,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            letterSpacing = 1.sp,
            color = Cores.destaque
        )
        if (descricao.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(descricao, fontFamily = Fontes.texto, fontSize = 12.5.sp, color = Cores.textoApagado)
        }
        Spacer(Modifier.height(16.dp))
        conteudo()
    }
}

@Composable
private fun Chip(texto: String, ativo: Boolean, aoTocar: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (ativo) Cores.destaque else Cores.conteiner)
            .clickable(onClick = aoTocar)
            .heightIn(min = 46.dp)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            texto,
            fontFamily = Fontes.texto,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (ativo) Cores.sobreODestaque else Cores.textoPrincipal
        )
    }
}

/**
 * Uma comparação editável: sinal, comparador, valor.
 *
 * O valor ATUAL do sinal aparece embaixo do botão. É o detalhe que faz a condição ser escrita
 * olhando para o carro em vez de para a memória — sem ele, "gear_status = 4" é um palpite; com
 * ele, o motorista vê "agora: P (estacionado)" e escolhe o código certo na lista.
 */
@Composable
private fun EditorDeCondicao(
    condicao: SignalCondition,
    aoMudar: (SignalCondition) -> Unit,
    aoEscolherSinal: () -> Unit,
    permitirMudou: Boolean
) {
    val sinal = CarSignalCatalog.porChave(condicao.key)
    val atual = remember(condicao.key) {
        runCatching { Veiculo.ler(condicao.key) }.getOrNull()
    }
    val comparadores = remember(permitirMudou) {
        MacroComparator.entries.filter { permitirMudou || it != MacroComparator.CHANGED }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .weight(1.4f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Cores.conteiner)
                    .clickable(onClick = aoEscolherSinal)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    sinal.label,
                    fontFamily = Fontes.texto,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Cores.textoPrincipal,
                    maxLines = 1
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "agora: ${sinal.format(atual)}",
                    fontFamily = Fontes.texto,
                    fontSize = 12.sp,
                    color = Cores.textoApagado
                )
            }
            Spacer(Modifier.width(12.dp))
            SeletorSuspenso(
                rotulo = "Comparação",
                valor = condicao.comparator.id,
                opcoes = comparadores.map { it.id to it.label },
                aoMudar = { aoMudar(condicao.copy(comparator = MacroComparator.fromId(it))) },
                modifier = Modifier.weight(1f)
            )
            if (condicao.comparator != MacroComparator.CHANGED) {
                Spacer(Modifier.width(12.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (sinal.values.isNotEmpty()) {
                        SeletorSuspenso(
                            rotulo = "Valor",
                            valor = condicao.value,
                            opcoes = sinal.values,
                            aoMudar = { aoMudar(condicao.copy(value = it)) }
                        )
                    } else {
                        CampoDeTexto(
                            valor = condicao.value,
                            rotulo = if (sinal.unit.isBlank()) "Valor" else "Valor (${sinal.unit})",
                            aoMudar = { aoMudar(condicao.copy(value = it)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LinhaDeAcao(
    acao: MacroAction,
    expandida: Boolean,
    podeSubir: Boolean,
    podeDescer: Boolean,
    aoAlternar: () -> Unit,
    aoMudar: (MacroAction) -> Unit,
    aoSubir: () -> Unit,
    aoDescer: () -> Unit,
    aoRemover: () -> Unit
) {
    val spec = MacroActionCatalog.porId(acao.type)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Cores.conteiner)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = spec?.params?.isNotEmpty() == true, onClick = aoAlternar)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    MacroActionCatalog.descrever(acao),
                    fontFamily = Fontes.texto,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (spec == null) Cores.perigo else Cores.textoPrincipal
                )
                if (spec != null && spec.description.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        spec.description,
                        fontFamily = Fontes.texto,
                        fontSize = 12.5.sp,
                        color = Cores.textoApagado
                    )
                }
            }
            IconButton(onClick = aoSubir, enabled = podeSubir) {
                Icon(
                    Icons.Outlined.KeyboardArrowUp,
                    contentDescription = "Subir",
                    tint = if (podeSubir) Cores.textoSecundario else Cores.textoApagado
                )
            }
            IconButton(onClick = aoDescer, enabled = podeDescer) {
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "Descer",
                    tint = if (podeDescer) Cores.textoSecundario else Cores.textoApagado
                )
            }
            IconButton(onClick = aoRemover) {
                Icon(Icons.Outlined.Delete, contentDescription = "Remover", tint = Cores.perigo)
            }
        }
        if (expandida && spec != null && spec.params.isNotEmpty()) {
            HorizontalDivider(color = Cores.linhaFina)
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                spec.params.forEach { p ->
                    EditorDeParametro(
                        param = p,
                        valor = acao.params[p.name] ?: p.default,
                        aoMudar = { novo ->
                            aoMudar(acao.copy(params = acao.params + (p.name to novo)))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorDeParametro(param: MacroActionParam, valor: String, aoMudar: (String) -> Unit) {
    var escolhendoChave by remember { mutableStateOf(false) }
    when (param.kind) {
        MacroActionParam.Kind.ENUM -> SeletorSuspenso(
            rotulo = param.label,
            valor = valor,
            opcoes = param.options,
            aoMudar = aoMudar
        )

        MacroActionParam.Kind.NUMBER -> Column(modifier = Modifier.fillMaxWidth()) {
            val faixa = param.range
            Text(
                "${param.label}: $valor ${param.unit}".trim(),
                fontFamily = Fontes.texto,
                fontSize = 14.sp,
                color = Cores.textoPrincipal
            )
            if (faixa != null) {
                val numero = valor.toFloatOrNull() ?: param.default.toFloatOrNull() ?: faixa.start
                Slider(
                    value = numero.coerceIn(faixa),
                    onValueChange = { bruto ->
                        // Arredonda para o passo do próprio campo: uma temperatura de 21,73 °C
                        // seria escrita no carro como está, e o carro trabalha em meio grau.
                        val passo = param.step.takeIf { it > 0f } ?: 1f
                        val ajustado = Math.round(bruto / passo) * passo
                        aoMudar(
                            if (passo >= 1f) ajustado.toInt().toString()
                            else String.format(java.util.Locale.US, "%.1f", ajustado)
                        )
                    },
                    valueRange = faixa,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Cores.destaque,
                        inactiveTrackColor = Cores.trilhoDesligado
                    )
                )
            } else {
                CampoDeTexto(valor = valor, rotulo = param.label, aoMudar = aoMudar)
            }
        }

        MacroActionParam.Kind.PACKAGE -> CampoDeApp(
            pacote = valor,
            aoEscolher = aoMudar,
            rotulo = param.label,
            seletor = { fechar, escolher -> SeletorDeApp(aoFechar = fechar, aoEscolher = { escolher(it.pacote) }) }
        )

        MacroActionParam.Kind.SIGNAL_KEY -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(param.label, fontFamily = Fontes.texto, fontSize = 13.sp, color = Cores.textoSecundario)
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Cores.fundo)
                        .clickable { escolhendoChave = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text(
                        valor.ifBlank { "Escolher chave…" },
                        fontFamily = Fontes.texto,
                        fontSize = 15.sp,
                        color = if (valor.isBlank()) Cores.textoApagado else Cores.textoPrincipal
                    )
                }
            }
            if (escolhendoChave) {
                SeletorDeSinal(
                    onEscolher = {
                        aoMudar(it.key)
                        escolhendoChave = false
                    },
                    onFechar = { escolhendoChave = false }
                )
            }
        }

        MacroActionParam.Kind.TEXT -> CampoDeTexto(valor = valor, rotulo = param.label, aoMudar = aoMudar)
    }
}

// ─────────────────────────────────────────────────────────────────────────────────────────────
// Seletores
// ─────────────────────────────────────────────────────────────────────────────────────────────

/**
 * A lista dos sinais do carro, com busca.
 *
 * Os curados vêm primeiro porque são os que alguém procura; as outras ~770 chaves ficam no fim,
 * atrás do mesmo campo de busca. Ninguém rola até lá sem saber o que está procurando — e quem sabe
 * digita o nome da chave.
 */
@Composable
private fun SeletorDeSinal(onEscolher: (CarSignal) -> Unit, onFechar: () -> Unit) {
    var busca by remember { mutableStateOf("") }
    val resultados = remember(busca) { CarSignalCatalog.buscar(busca) }

    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text("Sinal do carro", fontFamily = Fontes.texto) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                CampoDeTexto(valor = busca, rotulo = "Buscar (nome ou chave)", aoMudar = { busca = it })
                Spacer(Modifier.height(12.dp))
                Text(
                    "${resultados.size} sinais",
                    fontFamily = Fontes.texto,
                    fontSize = 12.sp,
                    color = Cores.textoApagado
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(resultados, key = { it.key }) { sinal ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEscolher(sinal) }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                sinal.label,
                                fontFamily = Fontes.texto,
                                fontSize = 15.sp,
                                color = if (sinal.isCurated) Cores.textoPrincipal else Cores.textoSecundario
                            )
                            Text(
                                if (sinal.isCurated) "${sinal.group} · ${sinal.key}" else sinal.key,
                                fontFamily = Fontes.texto,
                                fontSize = 11.5.sp,
                                color = Cores.textoApagado
                            )
                        }
                        HorizontalDivider(color = Cores.linhaFina)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onFechar) { Text("Fechar") } }
    )
}

@Composable
private fun SeletorDeAcao(onEscolher: (MacroActionSpec) -> Unit, onFechar: () -> Unit) {
    var busca by remember { mutableStateOf("") }
    val resultados = remember(busca) {
        val q = busca.trim().lowercase()
        if (q.isBlank()) MacroActionCatalog.acoes
        else MacroActionCatalog.acoes.filter {
            it.label.lowercase().contains(q) || it.group.lowercase().contains(q)
        }
    }

    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text("O que a macro deve fazer", fontFamily = Fontes.texto) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                CampoDeTexto(valor = busca, rotulo = "Buscar ação", aoMudar = { busca = it })
                Spacer(Modifier.height(12.dp))
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    MacroActionCatalog.grupos.forEach { grupo ->
                        val doGrupo = resultados.filter { it.group == grupo }
                        if (doGrupo.isEmpty()) return@forEach
                        item(key = "g-$grupo") {
                            Text(
                                grupo.uppercase(),
                                fontFamily = Fontes.texto,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                letterSpacing = 1.sp,
                                color = Cores.destaque,
                                modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
                            )
                        }
                        items(doGrupo, key = { it.id }) { spec ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onEscolher(spec) }
                                    .padding(vertical = 10.dp)
                            ) {
                                Text(
                                    spec.label,
                                    fontFamily = Fontes.texto,
                                    fontSize = 15.sp,
                                    color = Cores.textoPrincipal
                                )
                                if (spec.description.isNotBlank()) {
                                    Text(
                                        spec.description,
                                        fontFamily = Fontes.texto,
                                        fontSize = 11.5.sp,
                                        color = Cores.textoApagado
                                    )
                                }
                            }
                            HorizontalDivider(color = Cores.linhaFina)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onFechar) { Text("Fechar") } }
    )
}

@Composable
private fun SeletorSuspenso(
    rotulo: String,
    valor: String,
    opcoes: List<Pair<String, String>>,
    aoMudar: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var aberto by remember { mutableStateOf(false) }
    val rotuloAtual = opcoes.firstOrNull { it.first == valor }?.second ?: valor.ifBlank { "—" }
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Cores.conteiner)
                .clickable { aberto = true }
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(rotulo, fontFamily = Fontes.texto, fontSize = 11.5.sp, color = Cores.textoApagado)
            Spacer(Modifier.height(2.dp))
            Text(
                rotuloAtual,
                fontFamily = Fontes.texto,
                fontSize = 16.sp,
                color = Cores.textoPrincipal,
                maxLines = 1
            )
        }
        DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            opcoes.forEach { (chave, texto) ->
                DropdownMenuItem(
                    text = { Text(texto, fontFamily = Fontes.texto) },
                    onClick = {
                        aoMudar(chave)
                        aberto = false
                    }
                )
            }
        }
    }
}

@Composable
private fun CampoDeTexto(valor: String, rotulo: String, aoMudar: (String) -> Unit) {
    OutlinedTextField(
        value = valor,
        onValueChange = aoMudar,
        label = { Text(rotulo) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Cores.textoPrincipal,
            unfocusedTextColor = Cores.textoPrincipal,
            focusedBorderColor = Cores.destaque,
            unfocusedBorderColor = Cores.linhaFina,
            focusedLabelColor = Cores.destaque,
            unfocusedLabelColor = Cores.textoApagado,
            cursorColor = Cores.destaque
        )
    )
}

// ─────────────────────────────────────────────────────────────────────────────────────────────
// Compartilhado com a tela de gestos
// ─────────────────────────────────────────────────────────────────────────────────────────────

@Composable
internal fun MacroInterruptor(
    titulo: String,
    descricao: String,
    ligado: Boolean,
    aoMudar: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().liquidGlass().padding(22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 18.dp)) {
            Text(
                titulo,
                fontFamily = Fontes.texto,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Cores.textoPrincipal
            )
            Spacer(Modifier.height(4.dp))
            Text(descricao, fontFamily = Fontes.texto, fontSize = 13.sp, color = Cores.textoSecundario)
        }
        Switch(
            checked = ligado,
            onCheckedChange = aoMudar,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Cores.sobreODestaque,
                checkedTrackColor = Cores.destaque,
                uncheckedThumbColor = Cores.bolinhaDesligada,
                uncheckedTrackColor = Cores.trilhoDesligado
            )
        )
    }
}

@Composable
internal fun CartaoVazio(titulo: String, texto: String) {
    Column(modifier = Modifier.fillMaxWidth().liquidGlass().padding(28.dp)) {
        Text(
            titulo,
            fontFamily = Fontes.texto,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Cores.textoPrincipal
        )
        Spacer(Modifier.height(6.dp))
        Text(texto, fontFamily = Fontes.texto, fontSize = 14.sp, color = Cores.textoSecundario)
    }
}
