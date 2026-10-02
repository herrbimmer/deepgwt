package br.com.opengw.ui.telas

import android.app.DatePickerDialog
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.opengw.nucleo.Preferencias
import br.com.opengw.ui.Cores
import br.com.opengw.veiculo.comandos.LeiturasDoCarro
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val AMBAR = Color(0xFFFFB74D)
private val VERMELHO_DA_LIXEIRA = Color(0xFFFF5252)

private fun dataCurta(ms: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(ms))

/** Odômetro e aviso de revisão (`telas-e-servico-telas.md` §3, B4–B6). Aparece dentro do cartão do tema. */
@Composable
fun SecaoDeOdometroERevisao(
    prefs: SharedPreferences,
    habilitada: Boolean = true,
    quilometragemAtual: () -> Int = { LeiturasDoCarro.quilometragemTotal() },
) {
    var ligado by remember { mutableStateOf(prefs.getBoolean(Preferencias.ENABLE_ODOMETER_AND_REVISION.chave, true)) }
    var historico by remember { mutableStateOf(Revisoes.ler(prefs)) }
    val proxima = Revisoes.proxima(historico)
    var dialogo by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var aberto by remember { mutableStateOf(false) }

    fun gravar(nova: List<Revisao>) {
        historico = nova
        Revisoes.gravar(prefs, nova)
        Revisoes.publicar(prefs, Revisoes.proxima(nova))
    }

    val ativa = ligado && habilitada
    val abrirDialogo = { dialogo = quilometragemAtual().toString() to System.currentTimeMillis() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().height(104.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Esquerda (40%): o interruptor (só exibição; a caixa inteira é o alvo).
            Row(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Cores.trilhoDesligado)
                    .clickable(enabled = habilitada) {
                        ligado = !ligado
                        prefs.edit().putBoolean(Preferencias.ENABLE_ODOMETER_AND_REVISION.chave, ligado).apply()
                    }
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Odômetro e aviso de revisão", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        "Mostra no painel o total rodado e quando vence a próxima revisão",
                        fontSize = 12.sp,
                        color = Cores.textoSecundario,
                    )
                }
                Switch(
                    checked = ligado,
                    onCheckedChange = null,
                    enabled = habilitada,
                    colors = SwitchDefaults.colors(checkedTrackColor = Cores.destaque),
                )
            }
            // Direita (60%): registrar e a próxima.
            Row(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (ativa) Cores.trilhoDesligado else Cores.conteiner)
                    .clickable(enabled = ativa, onClick = abrirDialogo)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = abrirDialogo,
                    enabled = ativa,
                    modifier = Modifier.width(210.dp).height(38.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Cores.destaque, contentColor = Cores.sobreODestaque),
                ) {
                    Text(if (historico.isEmpty()) "Registrar compra ou revisão" else "Registrar revisão", fontSize = 12.sp)
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    val corDoTexto = if (ativa) Cores.textoSecundario else Cores.bolinhaDesligada
                    Text("Próxima revisão", fontSize = 12.sp, color = corDoTexto)
                    if (proxima.km == 0 && proxima.data == 0L) {
                        Text(
                            "N/D — registre a compra ou uma revisão",
                            fontSize = 13.sp,
                            color = if (ativa) AMBAR else Cores.bolinhaDesligada,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        Text(
                            "${NumberFormat.getIntegerInstance().format(proxima.km)} km ou ${dataCurta(proxima.data)}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (ativa) Color.White else Cores.bolinhaDesligada,
                        )
                    }
                }
                Spacer(Modifier.width(210.dp))
            }
        }

        // B5: histórico recolhível.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Cores.trilhoDesligado)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { aberto = !aberto },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Histórico de revisões (${historico.size})", color = Color.White, modifier = Modifier.weight(1f))
                Icon(
                    if (aberto) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (aberto) "Fechar" else "Abrir",
                    tint = Cores.textoSecundario,
                )
            }
            if (aberto) {
                Spacer(Modifier.height(8.dp))
                if (historico.isEmpty()) {
                    Text("Nenhuma revisão registrada.", fontSize = 13.sp, color = Cores.textoSecundario)
                } else {
                    for (revisao in historico.sortedByDescending { it.km }) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { gravar(historico.filterNot { it == revisao }) },
                                enabled = habilitada,
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Apagar", tint = VERMELHO_DA_LIXEIRA)
                            }
                            Text(
                                if (revisao.km == 0) "Data de compra" else "${NumberFormat.getIntegerInstance().format(revisao.km)} km",
                                color = Color.White,
                                modifier = Modifier.weight(1f),
                            )
                            Text(dataCurta(revisao.data), color = Cores.textoSecundario)
                        }
                    }
                }
            }
        }
    }

    dialogo?.let { (kmInicial, dataInicial) ->
        DialogoDeRegistro(
            historicoVazio = historico.isEmpty(),
            kmInicial = kmInicial,
            dataInicial = dataInicial,
            aoConfirmar = { km, data ->
                gravar((historico + Revisao(km, data)).sortedBy { it.km })
                dialogo = null
            },
            aoCancelar = { dialogo = null },
        )
    }
}

/** B6. */
@Composable
private fun DialogoDeRegistro(
    historicoVazio: Boolean,
    kmInicial: String,
    dataInicial: Long?,
    aoConfirmar: (km: Int, data: Long) -> Unit,
    aoCancelar: () -> Unit,
) {
    val contexto = LocalContext.current
    var km by remember { mutableStateOf(kmInicial) }
    var data by remember { mutableStateOf(dataInicial) }
    LaunchedEffect(kmInicial, dataInicial) {
        km = kmInicial
        data = dataInicial
    }

    fun escolherData() {
        val calendario = Calendar.getInstance().apply { data?.let { timeInMillis = it } }
        DatePickerDialog(
            contexto,
            { _, ano, mes, dia ->
                // A data escolhida com a hora atual do dia.
                val agora = Calendar.getInstance()
                agora.set(ano, mes, dia)
                data = agora.timeInMillis
            },
            calendario.get(Calendar.YEAR),
            calendario.get(Calendar.MONTH),
            calendario.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    val kmValido = km.toIntOrNull()
    AlertDialog(
        onDismissRequest = aoCancelar,
        containerColor = Cores.conteiner,
        title = {
            Text(if (historicoVazio) "Registrar compra ou revisão" else "Registrar revisão", color = Color.White)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "A próxima revisão é calculada sozinha: 12.000 km ou um ano depois da última." +
                        if (historicoVazio) " Para registrar a data da compra, use 0 km." else "",
                    color = Cores.textoSecundario,
                    fontSize = 13.sp,
                )
                OutlinedTextField(
                    value = km,
                    onValueChange = { novo -> if (novo.isEmpty() || novo.all { it in '0'..'9' }) km = novo },
                    label = { Text("Quilometragem atual") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Cores.destaque,
                        focusedLabelColor = Cores.destaque,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    ),
                )
                Text(if (km == "0") "Data da compra" else "Data da revisão", fontSize = 12.sp, color = Cores.textoSecundario)
                OutlinedButton(onClick = { escolherData() }) {
                    Text(data?.let { dataCurta(it) } ?: "Escolher a data", color = Cores.destaque)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { aoConfirmar(kmValido ?: 0, data ?: 0L) },
                enabled = kmValido != null && data != null,
            ) { Text("Confirmar", color = if (kmValido != null && data != null) Cores.destaque else Cores.textoApagado) }
        },
        dismissButton = { TextButton(onClick = aoCancelar) { Text("Cancelar", color = Cores.textoSecundario) } },
    )
}
