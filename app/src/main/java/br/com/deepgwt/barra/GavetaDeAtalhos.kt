// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.barra

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.deepgwt.ui.Cores
import br.com.deepgwt.ui.Fontes
import br.com.deepgwt.ui.GlassTier
import br.com.deepgwt.ui.liquidGlass
import br.com.deepgwt.ui.materialize
import br.com.deepgwt.ui.rememberMenuEntrance
import br.com.deepgwt.ui.touchTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Gaveta de atalhos (interface caso 25). */
@Composable
internal fun GavetaDeAtalhos(modifier: Modifier) {
    val entrada = rememberMenuEntrance()
    Column(
        modifier
            .widthIn(max = 760.dp)
            .fillMaxWidth()
            .materialize(entrada, TransformOrigin(0.5f, 1f))
            .liquidGlass(GlassTier.Painel, RoundedCornerShape(12.dp))
            .consomeToques()
            .padding(12.dp),
    ) {
        AlcaDaGaveta()
        Column(
            Modifier
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CabecalhoDeMenu("Atalhos do DeepGWT")
            SecaoDoCondutor()
            SecaoDaProjecao()
        }
    }
}

/** A alça: arrastar para baixo mais de 80 px, com o vertical dominando, fecha a gaveta. */
@Composable
private fun AlcaDaGaveta() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val descida = awaitFirstDown(requireUnconsumed = false)
                    var dx = 0f
                    var dy = 0f
                    while (true) {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull { it.id == descida.id } ?: break
                        if (!c.pressed) break
                        dx += c.position.x - c.previousPosition.x
                        dy += c.position.y - c.previousPosition.y
                        c.consume()
                        if (dy > 80f && dy >= 1.2f * abs(dx)) {
                            EstadoDaBarra.gavetaAberta = false
                            break
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(64.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.32f)),
        )
    }
}

@Composable
private fun TituloDaSecao(icone: ImageVector, titulo: String, dica: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icone, contentDescription = null, tint = Cores.destaque, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(titulo, fontFamily = Fontes.texto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Cores.textoPrincipal)
        Spacer(Modifier.width(10.dp))
        Text(dica, fontFamily = Fontes.texto, fontSize = 12.sp, color = Cores.textoSecundario, maxLines = 1)
    }
}

/** Condutor: os perfis (lidos uma vez por abertura) e o relatório da aplicação. */
@Composable
private fun SecaoDoCondutor() {
    val perfis = Barra.integracoes.perfis
    val escopo = rememberCoroutineScope()
    var nomes by remember { mutableStateOf<List<String>?>(null) }
    var ativo by remember { mutableStateOf<String?>(null) }
    var aplicando by remember { mutableStateOf<String?>(null) }
    var relatorio by remember { mutableStateOf<RelatorioDoPerfil?>(null) }
    LaunchedEffect(Unit) {
        val (n, a) = withContext(Dispatchers.IO) {
            runCatching { perfis.nomes() to perfis.ativo() }.getOrDefault(emptyList<String>() to null)
        }
        nomes = n
        ativo = a
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TituloDaSecao(Icons.Filled.Person, "Condutor", ativo?.let { "Dirigindo agora: $it" } ?: "Nenhum perfil ativo")
        val lista = nomes ?: return@Column
        if (lista.isEmpty()) {
            Text(
                "Nenhum perfil salvo ainda. Crie o primeiro no DeepGWT, em Perfis de Motorista: ele guarda o carro " +
                    "do jeito que você deixou e devolve tudo com um toque.",
                fontFamily = Fontes.texto,
                fontSize = 13.sp,
                color = Cores.textoSecundario,
            )
            return@Column
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (nome in lista) {
                val eAtivo = nome == ativo
                val forma = RoundedCornerShape(8.dp)
                val destaque = Cores.destaque
                Row(
                    Modifier
                        .touchTarget(minWidth = 0.dp)
                        .alpha(if (aplicando != null) 0.35f else 1f)
                        .clip(forma)
                        .background(if (eAtivo) destaque.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f))
                        .border(1.dp, if (eAtivo) destaque.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.10f), forma)
                        .clickable(enabled = aplicando == null) {
                            aplicando = nome
                            relatorio = null
                            escopo.launch {
                                val r = withContext(Dispatchers.IO) {
                                    runCatching { perfis.aplicar(nome) }
                                        .getOrElse { RelatorioDoPerfil(false, "Falha ao aplicar $nome.", listOfNotNull(it.message)) }
                                }
                                ativo = nome
                                relatorio = r
                                aplicando = null
                            }
                        }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (eAtivo) Icons.Filled.DirectionsCar else Icons.Filled.Person,
                        contentDescription = null,
                        tint = if (eAtivo) destaque else Color.White.copy(alpha = 0.82f),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (aplicando == nome) "Aplicando $nome…" else nome,
                        fontFamily = Fontes.texto,
                        fontSize = 14.sp,
                        color = if (eAtivo) destaque else Color.White.copy(alpha = 0.88f),
                        maxLines = 1,
                    )
                }
            }
        }
        relatorio?.let { r ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.055f))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    r.resumo,
                    fontFamily = Fontes.texto,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (r.tudoCerto) Cores.destaque else Cores.atencao,
                )
                for (linha in r.detalhes) {
                    Text(linha, fontFamily = Fontes.texto, fontSize = 11.sp, color = Cores.textoSecundario)
                }
            }
        }
    }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

/** Projeção do celular: Android Auto, CarPlay e (com o SPORT+ ligado) o painel esportivo. */
@Composable
private fun SecaoDaProjecao() {
    val contexto = LocalContext.current
    var instalados by remember { mutableStateOf<Pair<Boolean, Boolean>?>(null) }
    LaunchedEffect(Unit) {
        instalados = withContext(Dispatchers.IO) {
            fun instalado(p: String) = runCatching { contexto.packageManager.getPackageInfo(p, 0); true }.getOrDefault(false)
            instalado(RegrasDaInterface.PREDEFINIDO_AA) to instalado(RegrasDaInterface.PREDEFINIDO_CARPLAY)
        }
    }
    val (aa, cp) = instalados ?: (false to false)
    val lancador = Barra.integracoes.lancador
    fun abrir(pacote: String) {
        EstadoDaBarra.gavetaAberta = false
        CarroDaBarra.emSegundoPlano { lancador.abrir(pacote) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TituloDaSecao(Icons.Filled.PhoneAndroid, "Projeção do celular", "Abre na central")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InterruptorDoAr(Icons.Filled.DirectionsCar, "Android Auto", ativo = false, habilitado = aa, modifier = Modifier.weight(1f)) {
                abrir(RegrasDaInterface.PREDEFINIDO_AA)
            }
            InterruptorDoAr(Icons.Filled.PhoneIphone, "CarPlay", ativo = false, habilitado = cp, modifier = Modifier.weight(1f)) {
                abrir(RegrasDaInterface.PREDEFINIDO_CARPLAY)
            }
            if (EstadoDaBarra.sportPlusLigado) {
                InterruptorDoAr(Icons.Filled.Speed, "Painel Esportivo", ativo = true, habilitado = true, modifier = Modifier.weight(1f)) {
                    EstadoDaBarra.gavetaAberta = false
                    runCatching { Barra.integracoes.sportPlus.abrirSoOPainelEsportivo() }
                }
            }
        }
        if (instalados != null && (!aa || !cp)) {
            Text(
                "O que está apagado não está instalado nesta central.",
                fontFamily = Fontes.texto,
                fontSize = 11.sp,
                color = Cores.textoApagado,
            )
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
