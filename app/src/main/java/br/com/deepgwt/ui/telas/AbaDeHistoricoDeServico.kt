package br.com.deepgwt.ui.telas

import android.app.DatePickerDialog
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.deepgwt.nucleo.DeepGwt
import br.com.deepgwt.nucleo.Preferencias
import br.com.deepgwt.ui.AppTypeScale
import br.com.deepgwt.ui.CoresClassicas
import br.com.deepgwt.ui.Cores
import br.com.deepgwt.veiculo.comandos.LeiturasDoCarro
import java.text.NumberFormat
import java.util.Calendar

private val AMBAR = Color(0xFFFFB74D)
private val VERMELHO = Color(0xFFFF5252)
private const val UM_DIA_MS = 24L * 60 * 60 * 1000

/**
 * O menu "Histórico de Serviço": o caderno de manutenção do carro, no estilo do BMW. Mostra
 * quando vence a próxima revisão (por distância e por prazo) e a lista de tudo o que foi feito, do
 * mais recente para o mais antigo. Registra aqui ou pelo instalador no computador.
 */
@Composable
fun AbaDeHistoricoDeServico() {
    val prefs = remember {
        DeepGwt.contextoProtegido.getSharedPreferences("deepgwt_prefs", Context.MODE_PRIVATE)
    }
    var registros by remember { mutableStateOf(HistoricoDeServico.ordenar(HistoricoDeServico.ler(prefs))) }
    var registrando by remember { mutableStateOf(false) }
    var apagando by remember { mutableStateOf<RegistroDeServico?>(null) }
    val kmAtual = remember { LeiturasDoCarro.quilometragemTotal() }

    // O instalador grava pela rede: a lista acompanha sem precisar sair e voltar da aba.
    DisposableEffect(prefs) {
        val ouvinte = SharedPreferences.OnSharedPreferenceChangeListener { _, chave ->
            if (chave == Preferencias.SERVICE_HISTORY.chave) {
                registros = HistoricoDeServico.ordenar(HistoricoDeServico.ler(prefs))
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(ouvinte)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(ouvinte) }
    }

    Row(
        modifier = Modifier.fillMaxSize().background(CoresClassicas.fundo).padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Esquerda: título, a situação da próxima revisão e o botão de registrar.
        Column(
            modifier = Modifier.weight(0.42f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val papel = AppTypeScale.ScreenTitle
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "HISTÓRICO DE SERVIÇO",
                    fontFamily = papel.fontFamily,
                    fontSize = papel.fontSize,
                    letterSpacing = papel.letterSpacing,
                    color = CoresClassicas.texto,
                )
                Text(
                    "Revisões e serviços do carro, como no caderno do concessionário.",
                    fontSize = 16.sp,
                    color = CoresClassicas.textoSecundario,
                )
            }
            CartaoDaProximaRevisao(HistoricoDeServico.proxima(registros), kmAtual, registros.isEmpty())
            Button(
                onClick = { registrando = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Cores.destaque, contentColor = Cores.sobreODestaque),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Registrar serviço", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                "Também dá para registrar pelo computador: no instalador do DeepGWT, em Histórico de serviço, " +
                    "conectado ao Wi-Fi do carro.",
                fontSize = 12.sp,
                color = CoresClassicas.textoSecundario,
            )
        }

        // Direita: a lista, do mais recente ao mais antigo.
        Column(
            modifier = Modifier.weight(0.58f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "${registros.size} ${if (registros.size == 1) "registro" else "registros"}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = Cores.destaque,
            )
            if (registros.isEmpty()) {
                Text(
                    "Nenhum serviço registrado. Comece pela entrega do veículo (0 km) ou pela última revisão: " +
                        "a próxima é calculada sozinha, 12.000 km ou um ano depois.",
                    fontSize = 14.sp,
                    color = CoresClassicas.textoSecundario,
                )
            }
            registros.forEach { registro -> LinhaDoRegistro(registro) { apagando = registro } }
        }
    }

    if (registrando) {
        DialogoDeServico(
            kmInicial = kmAtual.takeIf { it > 0 }?.toString().orEmpty(),
            aoConfirmar = { novo ->
                registros = HistoricoDeServico.ordenar(HistoricoDeServico.adicionar(prefs, novo))
                registrando = false
            },
            aoCancelar = { registrando = false },
        )
    }
    apagando?.let { alvo ->
        AlertDialog(
            onDismissRequest = { apagando = null },
            containerColor = Cores.conteiner,
            title = { Text("Apagar registro?", color = Color.White) },
            text = {
                Text(
                    "${alvo.tipo.rotulo} de ${dataCurta(alvo.data)} (${NumberFormat.getIntegerInstance().format(alvo.km)} km) " +
                        "sai do histórico.",
                    color = Cores.textoSecundario,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    registros = HistoricoDeServico.ordenar(HistoricoDeServico.apagar(prefs, alvo.id))
                    apagando = null
                }) { Text("Apagar", color = VERMELHO) }
            },
            dismissButton = { TextButton(onClick = { apagando = null }) { Text("Cancelar", color = Cores.textoSecundario) } },
        )
    }
}

/** Faltam quantos km e dias; a cor fica âmbar perto do vencimento e vermelha depois dele. */
@Composable
private fun CartaoDaProximaRevisao(proxima: ProximaRevisao, kmAtual: Int, semHistorico: Boolean) {
    val agora = System.currentTimeMillis()
    val kmRestantes = if (kmAtual > 0 && proxima.km > 0) proxima.km - kmAtual else null
    val diasRestantes = if (proxima.data > 0) Math.floorDiv(proxima.data - agora, UM_DIA_MS).toInt() else null
    val vencida = (kmRestantes != null && kmRestantes <= 0) || (diasRestantes != null && diasRestantes <= 0)
    val perto = (kmRestantes != null && kmRestantes <= 1_000) || (diasRestantes != null && diasRestantes <= 30)
    val cor = when {
        vencida -> VERMELHO
        perto -> AMBAR
        else -> Cores.destaque
    }
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Cores.conteiner).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("PRÓXIMA REVISÃO", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = Cores.textoSecundario)
        if (semHistorico || (proxima.km == 0 && proxima.data == 0L)) {
            Text("N/D", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = AMBAR)
            Text("Registre a entrega do veículo ou uma revisão.", fontSize = 13.sp, color = Cores.textoSecundario)
            return@Column
        }
        Text(
            if (vencida) "Vencida" else "Em dia",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = cor,
        )
        Text(
            "${NumberFormat.getIntegerInstance().format(proxima.km)} km ou ${dataCurta(proxima.data)}, o que vier primeiro",
            fontSize = 14.sp,
            color = Color.White,
        )
        if (kmRestantes != null) {
            BarraDeProgresso(
                rotulo = if (kmRestantes > 0) "Faltam ${NumberFormat.getIntegerInstance().format(kmRestantes)} km" else
                    "Passou ${NumberFormat.getIntegerInstance().format(-kmRestantes)} km",
                usado = 1f - kmRestantes / Revisoes.INTERVALO_KM.toFloat(),
                cor = cor,
            )
        }
        if (diasRestantes != null) {
            BarraDeProgresso(
                rotulo = if (diasRestantes > 0) "Faltam $diasRestantes dias" else "Passou ${-diasRestantes} dias",
                usado = 1f - diasRestantes / 365f,
                cor = cor,
            )
        }
    }
}

@Composable
private fun BarraDeProgresso(rotulo: String, usado: Float, cor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(rotulo, fontSize = 12.sp, color = Cores.textoSecundario)
        Box(modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Cores.trilhoDesligado)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(usado.coerceIn(0.02f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(cor),
            )
        }
    }
}

@Composable
private fun LinhaDoRegistro(registro: RegistroDeServico, aoApagar: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Cores.conteiner)
            .padding(start = 18.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(registro.tipo.rotulo, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                "${dataCurta(registro.data)}  ·  ${NumberFormat.getIntegerInstance().format(registro.km)} km",
                fontSize = 13.sp,
                color = Cores.textoSecundario,
            )
            if (registro.descricao.isNotBlank()) Text(registro.descricao, fontSize = 13.sp, color = Color.White)
            val rodape = listOfNotNull(
                registro.oficina.takeIf { it.isNotBlank() },
                registro.custoCentavos.takeIf { it > 0 }?.let { HistoricoDeServico.reais(it) },
            ).joinToString("  ·  ")
            if (rodape.isNotEmpty()) Text(rodape, fontSize = 12.sp, color = Cores.textoSecundario)
        }
        IconButton(onClick = aoApagar) {
            Icon(Icons.Filled.Delete, contentDescription = "Apagar", tint = Cores.textoSecundario)
        }
    }
}

@Composable
private fun DialogoDeServico(
    kmInicial: String,
    aoConfirmar: (RegistroDeServico) -> Unit,
    aoCancelar: () -> Unit,
) {
    val contexto = LocalContext.current
    var tipo by remember { mutableStateOf(TipoDeServico.REVISAO) }
    var km by remember { mutableStateOf(kmInicial) }
    var data by remember { mutableStateOf<Long?>(System.currentTimeMillis()) }
    var descricao by remember { mutableStateOf("") }
    var oficina by remember { mutableStateOf("") }
    var custo by remember { mutableStateOf("") }

    fun escolherData() {
        val calendario = Calendar.getInstance().apply { data?.let { timeInMillis = it } }
        DatePickerDialog(
            contexto,
            { _, ano, mes, dia ->
                val c = Calendar.getInstance()
                c.set(ano, mes, dia)
                data = c.timeInMillis
            },
            calendario.get(Calendar.YEAR),
            calendario.get(Calendar.MONTH),
            calendario.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    val kmValido = km.toIntOrNull()
    val custoValido = HistoricoDeServico.centavosDe(custo)
    val pode = kmValido != null && data != null && custoValido != null
    val cores = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Cores.destaque,
        focusedLabelColor = Cores.destaque,
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
    )

    AlertDialog(
        onDismissRequest = aoCancelar,
        containerColor = Cores.conteiner,
        title = { Text("Registrar serviço", color = Color.White) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("O que foi feito", fontSize = 12.sp, color = Cores.textoSecundario)
                TipoDeServico.entries.chunked(4).forEach { linha ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        linha.forEach { opcao ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (opcao == tipo) Cores.destaque else Cores.trilhoDesligado)
                                    .clickable { tipo = opcao },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    opcao.rotulo,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (opcao == tipo) Cores.sobreODestaque else Color.White,
                                    maxLines = 2,
                                )
                            }
                        }
                        // A última linha tem menos opções: o vão mantém as caixas do mesmo tamanho.
                        repeat(4 - linha.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = km,
                        onValueChange = { novo -> if (novo.length <= 7 && novo.all { it in '0'..'9' }) km = novo },
                        label = { Text("Quilometragem") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = cores,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = { escolherData() }, modifier = Modifier.weight(1f).height(56.dp)) {
                        Text(data?.let { dataCurta(it) } ?: "Escolher a data", color = Cores.destaque)
                    }
                }
                OutlinedTextField(
                    value = descricao,
                    onValueChange = { if (it.length <= HistoricoDeServico.LIMITE_DE_TEXTO) descricao = it },
                    label = { Text("O que foi feito (opcional)") },
                    maxLines = 2,
                    colors = cores,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = oficina,
                        onValueChange = { if (it.length <= HistoricoDeServico.LIMITE_DE_TEXTO) oficina = it },
                        label = { Text("Oficina (opcional)") },
                        singleLine = true,
                        colors = cores,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = custo,
                        onValueChange = { custo = it.take(12) },
                        label = { Text("Custo em R$ (opcional)") },
                        singleLine = true,
                        isError = custoValido == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = cores,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = pode,
                onClick = {
                    aoConfirmar(
                        RegistroDeServico(
                            id = 0,
                            tipo = tipo,
                            km = kmValido ?: 0,
                            data = data ?: 0L,
                            descricao = descricao.trim(),
                            oficina = oficina.trim(),
                            custoCentavos = custoValido ?: 0L,
                        ),
                    )
                },
            ) { Text("Registrar", color = if (pode) Cores.destaque else Cores.textoApagado) }
        },
        dismissButton = { TextButton(onClick = aoCancelar) { Text("Cancelar", color = Cores.textoSecundario) } },
    )
}
