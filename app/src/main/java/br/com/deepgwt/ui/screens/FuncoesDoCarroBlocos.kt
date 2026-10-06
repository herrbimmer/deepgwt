// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui.screens

import br.com.deepgwt.telas.CampoDeApp
import br.com.deepgwt.ui.telas.SeletorDeApp
import br.com.deepgwt.ui.CoresClassicas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.deepgwt.carro.AcaoDoCoringa
import br.com.deepgwt.carro.ConectorDeRecarga
import br.com.deepgwt.carro.CoringasDoVolante
import br.com.deepgwt.carro.EscolhaDoCoringa
import br.com.deepgwt.carro.MemoriaDosAssistentes
import br.com.deepgwt.licenca.Portao
import br.com.deepgwt.licenca.Recurso
import br.com.deepgwt.macros.MacroStore
import br.com.deepgwt.ui.components.TravaDoNivel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/*
 * Blocos da tela Configurações para as funções do pacote `carro` (Alpha 8.5). Ficam fora do
 * AbaDeConfiguracoes porque aquele arquivo já passa de mil linhas; o visual é o mesmo
 * ([SettingSwitchRow] e o vidro de 12 dp).
 */

@Composable
private fun TextoMiudo(texto: String, cor: Color = CoresClassicas.textoSecundario) {
    Text(text = texto, color = cor, fontSize = 12.sp, lineHeight = 16.sp)
}

/** "Manter como deixei": memória dos assistentes de faixa e do aviso de velocidade. */
@Composable
fun BlocoMemoriaDosAssistentes() {
    var ligada by remember { mutableStateOf(MemoriaDosAssistentes.ativa()) }
    var resumo by remember { mutableStateOf(MemoriaDosAssistentes.resumo()) }
    LaunchedEffect(ligada) {
        // A fotografia dos valores é feita na fila do carro; o resumo chega um pouco depois.
        repeat(3) {
            delay(1_200)
            resumo = MemoriaDosAssistentes.resumo()
        }
    }
    Column(Modifier.fillMaxWidth()) {
        SettingSwitchRow(
            title = "Manter assistentes de faixa e aviso de velocidade como deixei",
            subtitle = "O carro religa aviso de saída de faixa, manutenção de faixa e aviso de velocidade a cada " +
                "partida. Com isto ligado, o DeepGWT lembra o que você deixou (no menu do carro ou na tela ADAS) e " +
                "devolve logo depois de ligar. Frenagem automática nunca entra. Mudou algo no menu do carro no " +
                "primeiro minuto depois de ligar? O DeepGWT desfaz — mude depois desse minuto.",
            checked = ligada
        ) {
            ligada = it
            MemoriaDosAssistentes.ligar(it)
            resumo = MemoriaDosAssistentes.resumo()
        }
        Row(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 12.dp)) { TextoMiudo(resumo) }
    }
}

/** Botão "Liberar o conector de recarga" (PHEV). Some num HEV confirmado sem conector. */
@Composable
fun BlocoConectorDeRecarga(comDivisor: Boolean = false) {
    var disponivel by remember { mutableStateOf(true) }
    var mensagem by remember { mutableStateOf<String?>(null) }
    var trabalhando by remember { mutableStateOf(false) }
    var mostrarRegistro by remember { mutableStateOf(false) }
    var registro by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        disponivel = withContext(Dispatchers.IO) { runCatching { ConectorDeRecarga.disponivel() }.getOrDefault(true) }
        if (ConectorDeRecarga.esperando()) mensagem = "Liberado: puxe o conector. A recarga volta ao modo imediato quando ele sair."
    }
    if (!disponivel) return
    // Dentro de um grupo da tela, o fio que separa esta linha da anterior some junto com ela.
    if (comDivisor) DivisorDeLinha()
    Column(Modifier.fillMaxWidth().cartaoDeAjuste()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Liberar o conector de recarga (PHEV)", color = CoresClassicas.texto, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    "Conector preso na tomada do carro? Faz o contorno dos donos num toque: troca a recarga para " +
                        "programada (o carro para e solta o conector por alguns segundos) e volta ao modo imediato " +
                        "quando o conector sair. Só com o carro parado.",
                    color = CoresClassicas.textoSecundario, fontSize = 12.sp, lineHeight = 16.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = {
                    trabalhando = true
                    mensagem = "Liberando…"
                    ConectorDeRecarga.liberar { r ->
                        trabalhando = false
                        mensagem = r.mensagem
                        registro = ConectorDeRecarga.registro()
                    }
                },
                enabled = !trabalhando,
                colors = ButtonDefaults.buttonColors(containerColor = CoresClassicas.destaque)
            ) { Text("Liberar") }
        }
        mensagem?.let {
            Spacer(Modifier.height(8.dp))
            TextoMiudo(it, CoresClassicas.texto)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (mostrarRegistro) "Esconder detalhes" else "Detalhes para o suporte",
            color = CoresClassicas.destaque, fontSize = 12.sp,
            modifier = Modifier.clickable {
                mostrarRegistro = !mostrarRegistro
                registro = ConectorDeRecarga.registro()
            }
        )
        if (mostrarRegistro) {
            Spacer(Modifier.height(4.dp))
            TextoMiudo(
                "Os valores do modo de recarga ainda não foram confirmados em todos os H6 PHEV. " +
                    "Última tentativa: " + (registro ?: "nenhuma.")
            )
        }
    }
}

/** Reprogramar os dois botões coringa do volante. */
@Composable
fun BlocoCoringasDoVolante() {
    TravaDoNivel(Recurso.CORINGAS, Modifier.fillMaxWidth()) {
        var ativos by remember { mutableStateOf(CoringasDoVolante.ativos()) }
        var versao by remember { mutableIntStateOf(0) }
        var escolhendo by remember { mutableStateOf<Int?>(null) }
        val ultimaTecla by CoringasDoVolante.ultimaTecla.collectAsState()
        val aprendendo by CoringasDoVolante.aprendendo.collectAsState()
        val situacao by CoringasDoVolante.situacao.collectAsState()
        Column(Modifier.fillMaxWidth()) {
            SettingSwitchRow(
                title = "Reprogramar os botões coringa do volante",
                subtitle = "Dá aos dois botões configuráveis do volante uma função do DeepGWT. A função escolhida no " +
                    "menu do carro fica guardada e volta sozinha quando você desligar isto (ou deixar o botão em " +
                    "\"Função do carro\"). Desligado, o DeepGWT não toca nos botões.",
                checked = ativos
            ) {
                ativos = it
                CoringasDoVolante.ligar(it)
            }
            if (ativos) {
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier.fillMaxWidth().cartaoDeAjuste(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf(1, 2).forEach { n ->
                        // `versao` força reler a escolha depois de trocar.
                        val escolha = remember(versao, n) { CoringasDoVolante.escolha(n) }
                        val tecla = remember(versao, n, aprendendo) { CoringasDoVolante.tecla(n) }
                        // `situacao` muda quando a fila do carro termina de tomar/devolver o botão.
                        val tomado = remember(versao, n, situacao) { CoringasDoVolante.tomado(n) }
                        LinhaDoCoringa(
                            numero = n,
                            escolha = escolha,
                            tecla = tecla,
                            tomado = tomado,
                            aprendendoEste = aprendendo == n,
                            onTrocar = { escolhendo = n },
                            onAprender = { CoringasDoVolante.aprender(n) },
                            onTeclaPadrao = { CoringasDoVolante.voltarTeclaPadrao(n); versao++ }
                        )
                    }
                    TextoMiudo(
                        "Última tecla do volante recebida: " + (ultimaTecla?.toString() ?: "nenhuma ainda") +
                            ". Se o botão não responder, toque em \"Aprender\" e aperte o coringa."
                    )
                    if (situacao.isNotBlank()) TextoMiudo(situacao)
                }
            }
        }
        escolhendo?.let { n ->
            DialogoDoCoringa(
                numero = n,
                atual = CoringasDoVolante.escolha(n),
                onEscolher = { e ->
                    CoringasDoVolante.escolher(n, e)
                    versao++
                    escolhendo = null
                },
                onCancelar = { escolhendo = null }
            )
        }
    }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

@Composable
private fun LinhaDoCoringa(
    numero: Int,
    escolha: EscolhaDoCoringa,
    tecla: Int,
    tomado: Boolean,
    aprendendoEste: Boolean,
    onTrocar: () -> Unit,
    onAprender: () -> Unit,
    onTeclaPadrao: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Botão coringa $numero", color = CoresClassicas.texto, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(escolha.descricao(), color = CoresClassicas.textoSecundario, fontSize = 13.sp)
            }
            Button(onClick = onTrocar, colors = ButtonDefaults.buttonColors(containerColor = CoresClassicas.destaque)) { Text("Trocar") }
        }
        if (escolha.acao != AcaoDoCoringa.CARRO) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextoMiudo(
                    if (aprendendoEste) "Aperte o botão coringa $numero agora…"
                    else "Tecla $tecla" + if (tomado) " · função do carro guardada" else "",
                    if (aprendendoEste) CoresClassicas.texto else CoresClassicas.textoSecundario
                )
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = onAprender,
                    enabled = !aprendendoEste,
                    border = BorderStroke(1.dp, CoresClassicas.destaque),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CoresClassicas.destaque)
                ) { Text("Aprender", fontSize = 12.sp) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onTeclaPadrao) { Text("Padrão", fontSize = 12.sp, color = CoresClassicas.textoSecundario) }
            }
        }
    }
}

@Composable
private fun DialogoDoCoringa(
    numero: Int,
    atual: EscolhaDoCoringa,
    onEscolher: (EscolhaDoCoringa) -> Unit,
    onCancelar: () -> Unit
) {
    var acao by remember { mutableStateOf(atual.acao) }
    var parametro by remember { mutableStateOf(atual.parametro) }
    val macrosLiberadas = Portao.permite(Recurso.MACROS)
    AlertDialog(
        onDismissRequest = onCancelar,
        // Fundo escuro como o resto do app: o padrão do Material é claro, e o texto aqui é claro.
        containerColor = CoresClassicas.superficie,
        titleContentColor = CoresClassicas.texto,
        textContentColor = CoresClassicas.texto,
        title = { Text("Botão coringa $numero") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                AcaoDoCoringa.entries.forEach { a ->
                    val liberada = a != AcaoDoCoringa.MACRO || macrosLiberadas
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = liberada) {
                            if (acao != a) parametro = ""
                            acao = a
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = acao == a,
                            onClick = if (liberada) ({ if (acao != a) parametro = ""; acao = a }) else null,
                            colors = RadioButtonDefaults.colors(selectedColor = CoresClassicas.destaque, unselectedColor = CoresClassicas.textoSecundario)
                        )
                        Text(
                            a.titulo + if (liberada) "" else " (${Recurso.MACROS.nivel.titulo})",
                            color = if (liberada) CoresClassicas.texto else CoresClassicas.textoSecundario,
                            fontSize = 14.sp
                        )
                    }
                }
                when (acao.parametro) {
                    AcaoDoCoringa.Parametro.APP -> {
                        Spacer(Modifier.height(8.dp))
                        CampoDeApp(pacote = parametro, aoEscolher = { parametro = it }, seletor = { fechar, escolher -> SeletorDeApp(aoFechar = fechar, aoEscolher = { escolher(it.pacote) }) })
                    }
                    AcaoDoCoringa.Parametro.MACRO -> {
                        Spacer(Modifier.height(8.dp))
                        val macros = remember { MacroStore.macros }
                        if (macros.isEmpty()) TextoMiudo("Nenhuma macro criada ainda (Recursos XTRA → Macros).")
                        macros.forEach { m ->
                            Row(
                                Modifier.fillMaxWidth().clickable { parametro = m.id },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = parametro == m.id,
                                    onClick = { parametro = m.id },
                                    colors = RadioButtonDefaults.colors(selectedColor = CoresClassicas.destaque)
                                )
                                Text(m.name, color = CoresClassicas.texto, fontSize = 14.sp)
                            }
                        }
                    }
                    AcaoDoCoringa.Parametro.NENHUM -> Unit
                }
            }
        },
        confirmButton = {
            val completo = acao.parametro == AcaoDoCoringa.Parametro.NENHUM || parametro.isNotBlank()
            TextButton(onClick = { onEscolher(EscolhaDoCoringa(acao, parametro)) }, enabled = completo) {
                Text("Salvar", color = if (completo) CoresClassicas.destaque else CoresClassicas.textoDesabilitado)
            }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar", color = CoresClassicas.textoSecundario) } }
    )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
