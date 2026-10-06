// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui.screens

import br.com.deepgwt.ui.Cores
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.deepgwt.diagnostics.CalibracaoDoPainel
import br.com.deepgwt.diagnostics.RegrasDaCalibracao
import br.com.deepgwt.ui.GlassTier
import br.com.deepgwt.ui.liquidGlass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Cartão da calibração do painel no Diagnóstico (Alpha 8.8). A lógica toda mora em
 * [CalibracaoDoPainel]; aqui só se liga, escolhe o passo, captura e encerra. A gravação continua
 * com a tela fechada: o cartão lê o estado de lá quando volta.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalibracaoDoPainelCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val estado by CalibracaoDoPainel.estado.collectAsState()
    var ocupado by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var passo by remember { mutableStateOf(RegrasDaCalibracao.PASSOS.first().id) }

    // Minutos que faltam: redesenha a cada 15 s enquanto a calibração está ligada.
    var agora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(estado.ativa) {
        while (estado.ativa) {
            agora = System.currentTimeMillis()
            delay(15_000)
        }
    }

    fun rodar(acao: () -> CalibracaoDoPainel.Resultado) {
        if (ocupado) return
        ocupado = true
        aviso = null
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching(acao).getOrElse { CalibracaoDoPainel.Resultado.Recusado(it.message ?: "erro") } }
            // O "feito" fica no estado da calibração (sobrevive ao cartão sair da tela na lista);
            // aqui só a recusa, que vale para este toque.
            aviso = (r as? CalibracaoDoPainel.Resultado.Recusado)?.motivo
            ocupado = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(tier = GlassTier.Popover, shape = RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "CALIBRAÇÃO DO PAINEL",
            color = Cores.textoSecundario,
            fontSize = 11.sp,
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.SemiBold
        )

        if (!estado.ativa) {
            Text(
                "Põe uma grade de coordenadas no painel e grava as luzes e avisos do carro, para mapear " +
                    "onde o painel de fábrica desenha por cima dos temas. Só com o carro parado; volta " +
                    "sozinho ao seu tema se o carro andar ou depois de 30 minutos.",
                color = Cores.textoPrincipal,
                fontSize = 14.sp
            )
            BotaoDaCalibracao(
                texto = if (ocupado) "Ligando…" else "Começar calibração",
                habilitado = !ocupado,
                destaque = true
            ) { rodar { CalibracaoDoPainel.iniciar(context) } }
            estado.pacote?.let { pacote ->
                Text(
                    "Último pacote: $pacote\nNo instalador do computador: Arquivos, abra essa pasta e baixe o .zip.",
                    color = Cores.textoSecundario,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            val desde = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(estado.desdeMs))
            val restante = ((RegrasDaCalibracao.LIMITE_MS - (agora - estado.desdeMs)) / 60_000L).coerceAtLeast(0)
            Text(
                "Grade no painel, gravando desde $desde. Volta ao seu tema em $restante min, se o carro andar, ou em Encerrar.",
                color = Cores.textoPrincipal,
                fontSize = 14.sp
            )

            Text("Fundo da grade", color = Cores.textoSecundario, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pilula("Preto (como um tema)", !estado.fundoTransparente) {
                    scope.launch(Dispatchers.IO) { CalibracaoDoPainel.definirFundo(false) }
                }
                Pilula("Transparente (painel de fábrica por baixo)", estado.fundoTransparente) {
                    scope.launch(Dispatchers.IO) { CalibracaoDoPainel.definirFundo(true) }
                }
            }

            Text("O que está aceso agora", color = Cores.textoSecundario, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val feitos = estado.capturas.map { it.passo }.toSet()
                RegrasDaCalibracao.PASSOS.forEach { p ->
                    val marca = if (p.id in feitos) " ✓" else ""
                    Pilula(p.rotulo + marca, p.id == passo) { passo = p.id }
                }
            }
            Text(RegrasDaCalibracao.passo(passo).comoFazer, color = Cores.textoApagado, fontSize = 12.sp)

            val capturando = estado.capturando
            Row(verticalAlignment = Alignment.CenterVertically) {
                BotaoDaCalibracao(
                    texto = "Capturar: ${RegrasDaCalibracao.passo(passo).rotulo}",
                    habilitado = capturando == null && !ocupado,
                    destaque = true
                ) {
                    val id = passo
                    scope.launch { withContext(Dispatchers.IO) { CalibracaoDoPainel.capturar(id) } }
                }
                if (capturando != null) {
                    Spacer(Modifier.width(12.dp))
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Cores.destaque)
                    Spacer(Modifier.width(8.dp))
                    Text(capturando, color = Cores.textoSecundario, fontSize = 12.sp)
                }
            }

            estado.capturas.sortedByDescending { it.numero }.take(6).forEach { c ->
                val telas = c.telas.filter { it.contains("salva") }.map { it.substringBefore(':') }
                val mudou = c.mudancas.take(4).joinToString("  ") {
                    "${it.chave.removePrefix("car.")} ${it.antes ?: "—"}→${it.depois}"
                }
                Text(
                    buildString {
                        append("%02d · ".format(c.numero)).append(CalibracaoDoPainel.rotuloDe(c.passo))
                        if (c.hora.isNotEmpty()) append(" · ").append(c.hora)
                        if (c.telas.isNotEmpty()) {
                            append(" · telas salvas: ").append(if (telas.isEmpty()) "nenhuma" else telas.joinToString(", "))
                        }
                        if (mudou.isNotEmpty()) append("\n   ").append(mudou)
                        if (c.mudancas.size > 4) append("  (+${c.mudancas.size - 4})")
                    },
                    color = Cores.textoSecundario,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            BotaoDaCalibracao(
                texto = if (ocupado) "Encerrando…" else "Encerrar e voltar ao meu tema",
                habilitado = !ocupado && capturando == null,
                destaque = false
            ) { rodar { CalibracaoDoPainel.encerrar(context) } }
            estado.sessao?.let {
                Text("Pasta: $it", color = Cores.textoApagado, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }

        (aviso ?: estado.mensagem)?.let {
            Text(it, color = Cores.atencao, fontSize = 13.sp)
        }
    }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

@Composable
private fun BotaoDaCalibracao(texto: String, habilitado: Boolean, destaque: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (destaque && habilitado) Modifier.background(Brush.horizontalGradient(Cores.gradienteDoDestaque))
                else Modifier.background(Cores.conteiner)
            )
            .clickable(enabled = habilitado, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            texto,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = when {
                !habilitado -> Cores.textoApagado
                destaque -> Cores.sobreODestaque
                else -> Cores.textoPrincipal
            }
        )
    }
}

@Composable
private fun Pilula(texto: String, selecionada: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(if (selecionada) Cores.destaque else Cores.conteiner)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            texto,
            fontSize = 13.sp,
            fontWeight = if (selecionada) FontWeight.Bold else FontWeight.Medium,
            color = if (selecionada) Cores.sobreODestaque else Cores.textoPrincipal
        )
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
