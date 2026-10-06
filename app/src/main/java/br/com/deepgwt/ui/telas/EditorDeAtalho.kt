// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui.telas

import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import br.com.deepgwt.ui.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.deepgwt.telas.ConfigDoApp
import br.com.deepgwt.telas.LancadorDeApps
import br.com.deepgwt.ui.Cores
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** Decisão 11: espera de calma antes de mandar a prévia ao vivo. */
private const val CALMA_DA_PREVIA_MS = 200L

private val VERMELHO_DO_ABORTAR = Color(0xFFFF4B4B)

/** O app escolhido no editor (rótulo e ícone para mostrar). */
private data class AppDoEditor(val pacote: String, val atividade: String, val rotulo: String, val icone: Drawable?)

/**
 * Editor de atalho (`telas-e-servico-telas.md` §5.2, D6–D12). [atalho] null = novo. Salvar grava pelo
 * lançador e chama [aoSalvar].
 */
@Composable
internal fun EditorDeAtalho(
    atalho: ConfigDoApp?,
    aoFechar: () -> Unit,
    aoSalvar: () -> Unit,
) {
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    val configuracao = LocalConfiguration.current

    // D7: estado inicial.
    var app by remember {
        mutableStateOf(
            atalho?.let {
                val info = LancadorDeApps.infoDoApp(contexto, it.pacote, it.nome)
                AppDoEditor(it.pacote, it.atividade, info.rotulo, info.icone)
            },
        )
    }
    var display by remember { mutableIntStateOf(RegrasDoEditor.displayInicial(atalho)) }
    var x by remember { mutableIntStateOf(atalho?.x ?: 0) }
    var y by remember { mutableIntStateOf(atalho?.y ?: 0) }
    var largura by remember { mutableIntStateOf(atalho?.largura ?: 1920) }
    var altura by remember { mutableIntStateOf(atalho?.altura ?: 720) }
    var nome by remember { mutableStateOf(atalho?.nome.orEmpty()) }
    var ignorarArea by remember { mutableStateOf(atalho?.ignorarAreaDoTema ?: false) }
    var substituto by remember { mutableStateOf(atalho?.iconeSubstituto) }
    var cor by remember { mutableStateOf(atalho?.corDoIcone ?: "#FFFFFF") }
    val jaConfigurados = remember { LancadorDeApps.configuracoes().map { it.pacote }.toSet() }
    val comDisplay0 = atalho?.display == 0

    var previa by remember { mutableStateOf(false) }
    var escolhendoApp by remember { mutableStateOf(false) }
    var editandoNome by remember { mutableStateOf(false) }
    var avisoDeCompatibilidade by remember { mutableStateOf<AppEscolhido?>(null) }

    fun montar(): ConfigDoApp? {
        val atual = app ?: return null
        return RegrasDoEditor.montar(
            pacote = atual.pacote, atividade = atual.atividade, display = display, x = x, y = y,
            largura = largura, altura = altura, nome = nome, ignorarAreaDoTema = ignorarArea,
            iconeSubstituto = substituto, corDoIcone = cor, original = atalho,
        )
    }

    fun trocarDisplay(novo: Int) {
        display = novo
        val l = RegrasDoEditor.limitar(x, y, largura, altura, novo)
        x = l[0]; y = l[1]; largura = l[2]; altura = l[3]
    }

    // D11: com a prévia ligada, cada mudança abre de novo, agrupada (decisão 11).
    LaunchedEffect(x, y, largura, altura, ignorarArea, substituto, cor) {
        if (!previa) return@LaunchedEffect
        delay(CALMA_DA_PREVIA_MS)
        montar()?.let { LancadorDeApps.abrir(it) }
    }

    fun aplicarEscolha(escolhido: AppEscolhido) {
        app = AppDoEditor(escolhido.pacote, escolhido.atividade, escolhido.rotulo, escolhido.icone)
        escolhendoApp = false
        previa = true
        val inicial = ConfigDoApp(escolhido.pacote, escolhido.atividade, display, x, y, largura, altura)
        escopo.launch { LancadorDeApps.abrir(inicial) }
    }

    Dialog(onDismissRequest = aoFechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .width((configuracao.screenWidthDp * 0.5f).dp)
                .height((configuracao.screenHeightDp * 0.8f).dp)
                .padding(16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Cores.conteiner)
                .border(1.dp, Cores.trilhoDesligado, RoundedCornerShape(12.dp))
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (atalho != null) "Editar atalho" else "Novo atalho",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = aoFechar) { Icon(Icons.Filled.Close, contentDescription = "Fechar", tint = Color.White) }
            }
            HorizontalDivider(color = Cores.trilhoDesligado)
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 1. Aplicativo.
                Rotulo("Aplicativo")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Cores.trilhoDesligado)
                            .clickable { escolhendoApp = true }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val atual = app
                        if (atual != null) {
                            IconeDoAtalho(pacote = atual.pacote, iconeDoApp = atual.icone, tamanho = 24.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(atual.rotulo, color = Color.White, maxLines = 1)
                        } else {
                            Text("Toque para escolher um app", color = Cores.textoApagado)
                        }
                    }
                    if (app != null) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Cores.trilhoDesligado)
                                .clickable { editandoNome = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (nome.isEmpty()) {
                                Icon(Icons.Filled.Edit, contentDescription = "Dar um nome", tint = Color.White)
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Label, contentDescription = "Nome: $nome", tint = Cores.destaque)
                            }
                        }
                    }
                }

                // 2. Tela de destino.
                Rotulo("Tela de destino")
                MenuDeDisplay(display = display, comDisplay0 = comDisplay0, aoEscolher = ::trocarDisplay)

                // 3. Dimensões.
                Rotulo("Dimensões")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ignorar a área do tema", color = Color.White, modifier = Modifier.weight(1f))
                    Switch(
                        checked = ignorarArea,
                        onCheckedChange = { ignorarArea = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = Cores.destaque),
                    )
                }

                // 4. Posição e tamanho.
                if (RegrasDoEditor.mostraDimensoes(ignorarArea, display)) {
                    val (l, a) = RegrasDoEditor.resolucao(display)
                    Text("Resolução: $l x $a | Pos: $x,$y", fontSize = 11.sp, color = Cores.textoSecundario)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ControleDeNumero("Posição X", x, 0..l) { x = it }
                        ControleDeNumero("Posição Y", y, 0..a, encaixe = 135) { y = it }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ControleDeNumero("Largura", largura, 100..l) { largura = it }
                        ControleDeNumero("Altura", altura, 100..a) { altura = it }
                    }
                }

                // 5. Ícone substituto.
                Rotulo("Ícone substituto")
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuadradoDeIcone(escolhido = substituto == null, aoTocar = { substituto = null }) {
                        Text("Padrão", fontSize = 9.sp, color = Color.White)
                    }
                    for (s in RegrasDoEditor.SUBSTITUTOS) {
                        QuadradoDeIcone(escolhido = substituto == s, aoTocar = { substituto = s }) {
                            IconeDoAtalho(pacote = "", iconeDoApp = null, tamanho = 26.dp, substituto = s, noEditor = true)
                        }
                    }
                }

                // 6. Cor do ícone.
                Rotulo("Cor do ícone")
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in RegrasDoEditor.CORES_DO_ICONE) {
                        val escolhida = c.equals(cor, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(corDoAtalho(c))
                                .border(if (escolhida) 2.dp else 1.dp, Color.White, CircleShape)
                                .clickable { cor = c },
                        )
                    }
                }

                // 7. Aviso da prévia.
                if (previa) {
                    Text(
                        "Prévia ao vivo: os controles mexem no app aberto no painel.",
                        fontSize = 12.sp,
                        color = Cores.destaque,
                    )
                }

                // 8. Salvar.
                Button(
                    onClick = {
                        montar()?.let {
                            LancadorDeApps.salvar(it)
                            aoSalvar()
                        }
                    },
                    enabled = app != null,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Cores.destaque, contentColor = Cores.sobreODestaque),
                ) { Text("Salvar") }
            }
        }
    }

    if (escolhendoApp) {
        SeletorDeApp(
            jaConfigurados = jaConfigurados,
            aoFechar = { escolhendoApp = false },
            aoEscolher = { escolhido ->
                if (escolhido.pacote == RegrasDoEditor.PACOTE_ANDROID_AUTO || escolhido.pacote == RegrasDoEditor.PACOTE_CARPLAY) {
                    escolhendoApp = false
                    avisoDeCompatibilidade = escolhido
                } else {
                    aplicarEscolha(escolhido)
                }
            },
        )
    }

    avisoDeCompatibilidade?.let { escolhido ->
        AvisoDeCompatibilidade(
            androidAuto = escolhido.pacote == RegrasDoEditor.PACOTE_ANDROID_AUTO,
            aoProsseguir = {
                avisoDeCompatibilidade = null
                aplicarEscolha(escolhido)
            },
            aoAbortar = { avisoDeCompatibilidade = null },
        )
    }

    if (editandoNome) {
        DialogoDeNome(
            nomeAtual = nome,
            dica = app?.rotulo.orEmpty(),
            aoAplicar = {
                nome = it
                editandoNome = false
            },
            aoCancelar = { editandoNome = false },
        )
    }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

@Composable
private fun Rotulo(texto: String) {
    Text(texto, fontSize = 12.sp, color = Cores.textoSecundario)
}

/** D8.2. */
@Composable
private fun MenuDeDisplay(display: Int, comDisplay0: Boolean, aoEscolher: (Int) -> Unit) {
    val largura = (LocalConfiguration.current.screenWidthDp * 0.5f).dp
    var aberto by remember { mutableStateOf(false) }
    val opcoes = buildList {
        if (comDisplay0) add(0 to "Central (display 0)")
        add(1 to "Painel, atrás do ADAS e das informações (display 1)")
        add(3 to "Painel, por cima de tudo (display 3)")
        add(RegrasDoEditor.DISPLAY_HUD to "HUD (display 4)")
    }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Cores.trilhoDesligado)
                .clickable { aberto = true }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                opcoes.firstOrNull { it.first == display }?.second ?: "Display $display",
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = Color.White)
        }
        DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }, modifier = Modifier.width(largura)) {
            for ((id, texto) in opcoes) {
                DropdownMenuItem(text = { Text(texto) }, onClick = {
                    aberto = false
                    aoEscolher(id)
                })
            }
        }
    }
}

/** D8.4: rótulo e valor em cima; o valor passa por [RegrasDoEditor.ajustar]. */
@Composable
private fun RowScope.ControleDeNumero(
    rotulo: String,
    valor: Int,
    faixa: IntRange,
    encaixe: Int? = null,
    aoMudar: (Int) -> Unit,
) {
    Column(modifier = Modifier.weight(1f)) {
        Row {
            Text(rotulo, fontSize = 12.sp, color = Cores.textoSecundario, modifier = Modifier.weight(1f))
            Text(valor.toString(), fontSize = 12.sp, color = Color.White)
        }
        Slider(
            value = valor.toFloat().coerceIn(faixa.first.toFloat(), faixa.last.toFloat()),
            onValueChange = { aoMudar(RegrasDoEditor.ajustar(it, faixa, 1, encaixe)) },
            valueRange = faixa.first.toFloat()..faixa.last.toFloat(),
            colors = SliderDefaults.colors(thumbColor = Cores.destaque, activeTrackColor = Cores.destaque),
        )
    }
}

@Composable
private fun QuadradoDeIcone(escolhido: Boolean, aoTocar: () -> Unit, conteudo: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (escolhido) Cores.destaque else Cores.trilhoDesligado)
            .clickable(onClick = aoTocar),
        contentAlignment = Alignment.Center,
    ) { conteudo() }
}

/** D10. */
@Composable
private fun DialogoDeNome(nomeAtual: String, dica: String, aoAplicar: (String) -> Unit, aoCancelar: () -> Unit) {
    var texto by remember { mutableStateOf(nomeAtual) }
    AlertDialog(
        onDismissRequest = aoCancelar,
        containerColor = Cores.conteiner,
        title = { Text("Nome do atalho", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("O nome aparece no cartão do atalho. Vazio usa o nome do app.", color = Cores.textoSecundario, fontSize = 13.sp)
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it },
                    placeholder = { Text(dica) },
                    singleLine = true,
                    colors = coresDoCampo(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (texto.isNotEmpty()) {
                    TextButton(onClick = { texto = "" }) { Text("Voltar ao padrão", color = VERMELHO_DO_ABORTAR) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { aoAplicar(texto.trim()) }) { Text("OK", color = Cores.destaque) } },
        dismissButton = { TextButton(onClick = aoCancelar) { Text("Cancelar", color = Cores.textoSecundario) } },
    )
}

/** D12. Idioma do aparelho `en` → inglês; senão português. */
@Composable
private fun AvisoDeCompatibilidade(androidAuto: Boolean, aoProsseguir: () -> Unit, aoAbortar: () -> Unit) {
    val ingles = Locale.getDefault().language == "en"
    val (titulo, texto) = when {
        androidAuto && ingles -> "Android Auto on the cluster" to
            "Support is experimental. The content is not resized, only cropped. Touching outside it on the " +
            "head unit takes the focus away and the screen goes black; the app tries to recover by itself, " +
            "but if that fails, tap the Android Auto icon. Do you want to continue?"
        androidAuto -> "Android Auto no painel" to
            "O suporte é experimental. O conteúdo não muda de tamanho, só é recortado. Tocar fora dele na " +
            "central tira o foco e a tela fica preta; o app tenta recuperar sozinho, mas, se não conseguir, " +
            "toque no ícone do Android Auto. Quer continuar?"
        ingles -> "CarPlay on the cluster" to
            "CarPlay was not tested in this version and there is no guarantee it works. Do you want to continue?"
        else -> "CarPlay no painel" to
            "O CarPlay não foi testado nesta versão e não há garantia de que funcione. Quer continuar?"
    }
    AlertDialog(
        onDismissRequest = aoAbortar,
        containerColor = Cores.conteiner,
        title = { Text(titulo, color = Color.White) },
        text = { Text(texto, color = Cores.textoSecundario) },
        confirmButton = {
            TextButton(onClick = aoProsseguir) { Text(if (ingles) "Proceed" else "Prosseguir", color = Cores.destaque) }
        },
        dismissButton = {
            TextButton(onClick = aoAbortar) { Text(if (ingles) "Abort" else "Abortar", color = VERMELHO_DO_ABORTAR) }
        },
    )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
