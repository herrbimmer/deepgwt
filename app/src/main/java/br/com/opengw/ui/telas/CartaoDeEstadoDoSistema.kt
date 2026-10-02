package br.com.opengw.ui.telas

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.opengw.app.AplicacaoOpenGw
import br.com.opengw.app.MotorDoApp
import br.com.opengw.nucleo.Preferencias
import br.com.opengw.ui.Cores
import br.com.opengw.ui.CoresClassicas
import br.com.opengw.ui.Fontes
import br.com.opengw.ui.GlossyCard
import br.com.opengw.ui.MedidasClassicas
import kotlinx.coroutines.delay

private val VERDE = Color(0xFF4ADE80)
private val VERMELHO = Color(0xFFEF4444)

/** Cartão "estado do sistema" da aba Informações (`telas-e-servico-diversos.md` §3, I1–I7). */
@Composable
fun CartaoDeEstadoDoSistema(
    motor: MotorDoApp = AplicacaoOpenGw.ligacoes.motor,
) {
    val contexto = LocalContext.current
    val prefs = remember { preferenciasDoApp() }

    // I2: lidas uma vez ao abrir.
    val ocultarIntegridade = remember { prefs.getBoolean(Preferencias.BYPASS_SELF_INSTALLATION_INTEGRITY_CHECK.chave, false) }
    val integridade = remember { prefs.getBoolean(Preferencias.SELF_INSTALLATION_INTEGRITY_CHECK.chave, false) }

    // I5: a cada 1 s até estar pronto com os três instantes gravados.
    var pronto by remember { mutableStateOf(motor.pronto) }
    var boot by remember { mutableLongStateOf(motor.instanteDoBoot) }
    var inicio by remember { mutableLongStateOf(motor.inicioDaPartida) }
    var fim by remember { mutableLongStateOf(motor.fimDaPartida) }
    LaunchedEffect(motor) {
        while (true) {
            pronto = motor.pronto
            boot = motor.instanteDoBoot
            inicio = motor.inicioDaPartida
            fim = motor.fimDaPartida
            if (pronto && boot > 0 && inicio > 0 && fim > 0) break
            delay(1_000)
        }
    }

    val versao = remember { versaoDoPacote(contexto) }
    var toques by remember { mutableIntStateOf(0) }
    var confirmarAvancado by remember { mutableStateOf(false) }

    GlossyCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Status do sistema", fontFamily = Fontes.titulo, fontSize = 17.sp, color = Color.White)
            HorizontalDivider(color = Cores.linhaFina)

            if (!ocultarIntegridade) {
                LinhaDeEstado("Instalado corretamente:", if (integridade) "Sim" else "Não", if (integridade) VERDE else VERMELHO)
            }
            LinhaDeEstado("Estado:", if (pronto) "Ativo" else "Inativo", if (pronto) VERDE else VERMELHO)

            if (pronto) {
                val naoIniciado = "não inicializado"
                LinhaDeInstante("Boot do sistema:", FormatoDoInstante.formatar(boot, pronto) ?: naoIniciado)
                LinhaDeInstante("Início da partida:", FormatoDoInstante.formatar(inicio, pronto) ?: naoIniciado)
                LinhaDeInstante("Fim da partida:", FormatoDoInstante.formatar(fim, pronto) ?: naoIniciado)
            }

            HorizontalDivider(color = Cores.linhaFina)
            Column(
                modifier = Modifier.clickable {
                    toques++
                    if (toques >= 5) {
                        toques = 0
                        confirmarAvancado = true
                    }
                },
            ) {
                Text("Versão", fontSize = 14.sp, color = Cores.textoSecundario)
                Text(versao, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color.White)
            }

            HorizontalDivider(color = Cores.linhaFina)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = { abrirConfiguracoesDoAndroid(contexto) },
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(MedidasClassicas.cantoDoBotao),
                    colors = ButtonDefaults.buttonColors(containerColor = CoresClassicas.destaque, contentColor = Color.White),
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Abrir Configurações do Android")
                }
            }
        }
    }

    if (confirmarAvancado) {
        AlertDialog(
            onDismissRequest = { confirmarAvancado = false },
            containerColor = Cores.conteiner,
            title = { Text("Modo avançado", color = Color.White) },
            text = {
                Text(
                    "O modo avançado mostra ajustes que podem deixar o sistema instável. Ative só se souber " +
                        "o que está fazendo; o uso é por sua conta.",
                    color = Cores.textoSecundario,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.edit().putBoolean(Preferencias.ADVANCE_USE.chave, true).apply()
                    confirmarAvancado = false
                }) { Text("Ativar", color = Cores.destaque) }
            },
            dismissButton = {
                TextButton(onClick = { confirmarAvancado = false }) { Text("Cancelar", color = Cores.textoSecundario) }
            },
        )
    }
}

@Composable
private fun LinhaDeEstado(rotulo: String, valor: String, cor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(rotulo, color = Cores.textoSecundario)
        Spacer(Modifier.width(8.dp))
        Text(valor, color = cor, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun LinhaDeInstante(rotulo: String, valor: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(rotulo, fontSize = 14.sp, color = Cores.textoSecundario)
        Spacer(Modifier.width(8.dp))
        Text(valor, fontSize = 14.sp, color = Color.White)
    }
}

private fun versaoDoPacote(contexto: Context): String = try {
    contexto.packageManager.getPackageInfo(contexto.packageName, 0).versionName ?: "Desconhecida"
} catch (e: Exception) {
    "Erro"
}

/** I7 (X-D7: falha vira mensagem). */
private fun abrirConfiguracoesDoAndroid(contexto: Context) {
    try {
        val intent = Intent(Intent.ACTION_MAIN)
            .setComponent(ComponentName("com.android.settings", "com.android.settings.Settings"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        contexto.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(contexto, "Não deu para abrir as Configurações do Android", Toast.LENGTH_SHORT).show()
    }
}
