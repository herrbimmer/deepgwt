package br.com.deepgwt.botoesextra

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.deepgwt.ajustes.CatalogoDeAjustes
import br.com.deepgwt.ajustes.TipoDeAjuste
import br.com.deepgwt.botoesextra.RegrasDasTeclasExternas.AcaoDaTecla
import br.com.deepgwt.streamdeck.StreamDeckService
import br.com.deepgwt.streamdeck.StreamDeckSettings
import br.com.deepgwt.streamdeck.StreamDeckSettingsScreen
import br.com.deepgwt.ui.CartaoClassico
import br.com.deepgwt.ui.CoresClassicas

private const val TAG = "BotoesExtra"

/**
 * "Botões extra" (Alpha 10.0, antes "Stream Deck"): botões físicos que fazem coisas no carro. Duas
 * fontes, uma por vez: o Stream Deck (o único modelo compatível é o MiraBox StreamDock 293S, que
 * também sai como RiseMode RM MC 01 W) ou um dispositivo externo, como um teclado USB, cujas teclas
 * o usuário ensina apertando.
 */
@Composable
fun BotoesExtraScreen(onBackToFeatures: () -> Unit) {
    val context = LocalContext.current
    var fonte by remember { mutableStateOf(BotoesExtraSettings.fonte()) }

    fun escolher(nova: FonteDeBotoes) {
        if (nova == fonte) return
        BotoesExtraSettings.definirFonte(nova)
        fonte = nova
        when (nova) {
            FonteDeBotoes.STREAM_DECK -> {
                LeitorDeTeclasExternas.startIfEnabled()
                if (StreamDeckSettings.isEnabled()) StreamDeckService.start(context)
            }
            FonteDeBotoes.DISPOSITIVO_EXTERNO -> {
                StreamDeckService.stop(context)
                LeitorDeTeclasExternas.startIfEnabled()
            }
        }
    }

    val cabecalho: @Composable () -> Unit = {
        CabecalhoDosBotoesExtra(fonte, onBackToFeatures, ::escolher)
    }
    when (fonte) {
        FonteDeBotoes.STREAM_DECK -> StreamDeckSettingsScreen(onBackToFeatures, cabecalho)
        FonteDeBotoes.DISPOSITIVO_EXTERNO -> TeclasExternasScreen(cabecalho)
    }
}

@Composable
private fun CabecalhoDosBotoesExtra(
    fonte: FonteDeBotoes,
    aoVoltar: () -> Unit,
    aoEscolher: (FonteDeBotoes) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = aoVoltar) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = CoresClassicas.texto)
            }
            Column {
                Text("Botões extra", color = CoresClassicas.texto, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Botões físicos que fazem coisas no carro. Escolha o que você vai usar.",
                    color = CoresClassicas.textoSecundario,
                    fontSize = 16.sp
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
            OpcaoDeFonte(
                titulo = "Stream Deck",
                descricao = "Deck USB com teclas que mostram o estado do carro. Compatível: MiraBox StreamDock 293S " +
                    "(RiseMode RM MC 01 W, USB 0A00:1001). Outros modelos, como os da Elgato, não funcionam.",
                escolhida = fonte == FonteDeBotoes.STREAM_DECK,
                modifier = Modifier.weight(1f)
            ) { aoEscolher(FonteDeBotoes.STREAM_DECK) }
            OpcaoDeFonte(
                titulo = "Dispositivo externo",
                descricao = "Um teclado ou qualquer dispositivo USB com botões. Você aperta a tecla, o DeepGWT guarda o " +
                    "sinal e liga a uma ação do carro.",
                escolhida = fonte == FonteDeBotoes.DISPOSITIVO_EXTERNO,
                modifier = Modifier.weight(1f)
            ) { aoEscolher(FonteDeBotoes.DISPOSITIVO_EXTERNO) }
        }
    }
}

@Composable
private fun OpcaoDeFonte(
    titulo: String,
    descricao: String,
    escolhida: Boolean,
    modifier: Modifier,
    aoTocar: () -> Unit
) {
    val forma = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(forma)
            .background(CoresClassicas.cartao)
            .border(
                if (escolhida) BorderStroke(2.dp, CoresClassicas.destaque) else BorderStroke(1.dp, CoresClassicas.borda),
                forma
            )
            .clickable(onClick = aoTocar)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            titulo + if (escolhida) "  ✓" else "",
            color = if (escolhida) CoresClassicas.destaque else CoresClassicas.texto,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(descricao, color = CoresClassicas.textoSecundario, fontSize = 14.sp)
    }
}

/** A fonte "dispositivo externo": liga/desliga, a lista de teclas e o "Adicionar tecla". */
@Composable
private fun TeclasExternasScreen(cabecalho: @Composable () -> Unit) {
    var ligado by remember { mutableStateOf(BotoesExtraSettings.externoMarcado()) }
    var teclas by remember { mutableStateOf(BotoesExtraSettings.teclasLigadas()) }
    var aprendendo by remember { mutableStateOf(false) }
    // A tecla que acabou de ser aprendida (ou a que se está editando) e o passo de escolher a ação.
    var teclaEscolhendo by remember { mutableStateOf<String?>(null) }
    var dispositivoVisto by remember { mutableStateOf("") }
    var escolhendoAjuste by remember { mutableStateOf(false) }

    fun atualizar() {
        teclas = BotoesExtraSettings.teclasLigadas()
    }

    // Fechar a tela encerra o modo de aprender (o leitor sobe de novo só se o recurso estiver ligado).
    DisposableEffect(Unit) { onDispose { LeitorDeTeclasExternas.cancelarAprender() } }

    fun comecarAAprender() {
        aprendendo = true
        LeitorDeTeclasExternas.aprender { tecla, dispositivo ->
            // Chega na thread do getevent: o estado do Compose aceita escrita de qualquer thread.
            aprendendo = false
            dispositivoVisto = dispositivo
            teclaEscolhendo = tecla
            // Fora desta thread: reconciliar pode matar o getevent que está entregando esta tecla.
            Thread({ LeitorDeTeclasExternas.startIfEnabled() }, "teclas-externas-reconciliar").start()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CoresClassicas.fundo)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        cabecalho()

        CartaoClassico {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Usar o dispositivo externo", color = CoresClassicas.texto, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Lê as teclas em qualquer tela, pelo acesso avançado (Shizuku). A tecla continua chegando ao app da frente.",
                            color = CoresClassicas.textoSecundario,
                            fontSize = 14.sp
                        )
                    }
                    Switch(checked = ligado, onCheckedChange = {
                        ligado = it
                        BotoesExtraSettings.definirExterno(it)
                        LeitorDeTeclasExternas.startIfEnabled()
                    })
                }
            }
        }

        CartaoClassico {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Teclas", color = CoresClassicas.texto, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                if (teclas.isEmpty()) {
                    Text(
                        "Nenhuma tecla ainda. Toque em \"Adicionar tecla\", aperte a tecla do dispositivo e escolha a ação.",
                        color = CoresClassicas.textoSecundario,
                        fontSize = 14.sp
                    )
                }
                teclas.forEach { (tecla, acao) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                RegrasDasTeclasExternas.rotuloDaTecla(tecla) + "  (" + tecla + ")",
                                color = CoresClassicas.texto,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(AcaoDeBotao.descrever(acao), color = CoresClassicas.textoSecundario, fontSize = 14.sp)
                        }
                        TextButton(onClick = { dispositivoVisto = ""; teclaEscolhendo = tecla }) { Text("Mudar ação") }
                        TextButton(onClick = { BotoesExtraSettings.soltarTecla(tecla); atualizar() }) { Text("Remover") }
                    }
                }
                OutlinedButton(onClick = { comecarAAprender() }) { Text("Adicionar tecla") }
            }
        }
    }

    if (aprendendo) {
        AlertDialog(
            onDismissRequest = { aprendendo = false; LeitorDeTeclasExternas.cancelarAprender() },
            title = { Text("Aperte a tecla") },
            text = {
                Text(
                    "Aperte agora o botão do dispositivo que você quer usar. O DeepGWT guarda o sinal dele. " +
                        "Se nada acontecer, confira se o dispositivo está plugado e se o Shizuku está ativo."
                )
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { aprendendo = false; LeitorDeTeclasExternas.cancelarAprender() }) { Text("Cancelar") }
            }
        )
    }

    val tecla = teclaEscolhendo
    if (tecla != null && !escolhendoAjuste) {
        AlertDialog(
            onDismissRequest = { teclaEscolhendo = null },
            title = { Text("Tecla ${RegrasDasTeclasExternas.rotuloDaTecla(tecla)}") },
            text = {
                Column {
                    if (dispositivoVisto.isNotBlank()) {
                        Text("Dispositivo: $dispositivoVisto", color = CoresClassicas.textoSecundario, fontSize = 13.sp)
                    }
                    Text("O que esta tecla faz?", modifier = Modifier.padding(vertical = 8.dp))
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(AcaoDeBotao.funcoesDoCarro()) { f ->
                            Text(
                                f.label,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        BotoesExtraSettings.ligarTecla(tecla, AcaoDaTecla.FuncaoDoDeck(f.name))
                                        Log.i(TAG, "tecla $tecla -> ${f.name}")
                                        teclaEscolhendo = null
                                        atualizar()
                                    }
                                    .padding(vertical = 12.dp),
                                fontSize = 16.sp
                            )
                        }
                        item {
                            Text(
                                "Ligar/desligar um ajuste…",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { escolhendoAjuste = true }
                                    .padding(vertical = 12.dp),
                                fontSize = 16.sp,
                                color = CoresClassicas.destaque
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { teclaEscolhendo = null }) { Text("Cancelar") } }
        )
    }

    if (tecla != null && escolhendoAjuste) {
        val ajustes = remember {
            CatalogoDeAjustes.todos
                .filter { it.tipo == TipoDeAjuste.INTERRUPTOR && !it.soNoComputador }
                .sortedBy { it.rotulo }
        }
        AlertDialog(
            onDismissRequest = { escolhendoAjuste = false; teclaEscolhendo = null },
            title = { Text("Qual ajuste liga e desliga?") },
            text = {
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(ajustes) { a ->
                        Text(
                            a.rotulo,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    BotoesExtraSettings.ligarTecla(tecla, AcaoDaTecla.LigaDesligaAjuste(a.id))
                                    escolhendoAjuste = false
                                    teclaEscolhendo = null
                                    atualizar()
                                }
                                .padding(vertical = 12.dp),
                            fontSize = 16.sp
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { escolhendoAjuste = false }) { Text("Voltar") } }
        )
    }
}
