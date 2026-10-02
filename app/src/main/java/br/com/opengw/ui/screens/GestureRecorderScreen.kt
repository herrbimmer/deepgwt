package br.com.opengw.ui.screens

import br.com.opengw.ui.Fontes
import br.com.opengw.ui.Cores
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.opengw.macros.GestureRecognizer
import br.com.opengw.macros.GestureStore
import br.com.opengw.macros.GestureTemplate
import br.com.opengw.macros.MacroGestureRouter
import br.com.opengw.macros.MacroStore
import br.com.opengw.ui.liquidGlass

/**
 * A tela dos gestos desenhados: a lista do que já foi gravado e o quadro onde se grava mais.
 *
 * ## Por que se desenha com UM dedo mesmo para um gesto de dois
 *
 * O que fica gravado é a FORMA, não quantos dedos a fizeram. Pedir que o motorista desenhe um
 * círculo com dois dedos dentro de um quadro pequeno só produziria um traço pior, e os dois dedos
 * traçam a mesma forma de qualquer jeito (é o que o leitor faz no carro: pega o dedo com mais
 * amostras). O número de dedos é um campo à parte porque ele decide QUANDO o gesto vale, não como
 * ele é.
 */
@Composable
fun GestureRecorderScreen(
    onBack: () -> Unit,
    /**
     * Fecha o ciclo: gravar um gesto e não ter onde apontá-lo é meio caminho. O cartão de um gesto
     * sem macro oferece o atalho que leva ao editor com o gatilho já preenchido.
     */
    onCriarMacro: (GestureTemplate) -> Unit = {}
) {
    val gestos = GestureStore.gestos
    var habilitado by remember { mutableStateOf(GestureStore.isEnabled()) }
    var emGravacao by remember { mutableStateOf<GestureTemplate?>(null) }
    var pendenteDeExclusao by remember { mutableStateOf<GestureTemplate?>(null) }

    if (emGravacao != null) {
        GravadorDeGesto(
            inicial = emGravacao!!,
            onCancelar = { emGravacao = null },
            onSalvar = { novo ->
                GestureStore.salvar(novo)
                if (!GestureStore.isEnabled()) {
                    // Gravar um gesto É o pedido para que ele funcione. Deixar o interruptor
                    // desligado depois disso faria o gesto novo não fazer nada e pareceria defeito.
                    GestureStore.setEnabled(true)
                    habilitado = true
                } else {
                    GestureStore.setEnabled(true)
                }
                emGravacao = null
            }
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Cores.textoPrincipal)
            }
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    "GESTOS DESENHADOS",
                    fontFamily = Fontes.titulo,
                    fontSize = 20.sp,
                    letterSpacing = 1.sp,
                    color = Cores.textoPrincipal
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Desenhe uma forma, dê um nome e aponte uma macro para ela. O gesto vale em " +
                        "qualquer tela do carro — o leitor enxerga o toque sem tomá-lo do app da frente.",
                    fontFamily = Fontes.texto,
                    fontSize = 13.sp,
                    color = Cores.textoSecundario
                )
            }
            Button(
                onClick = { emGravacao = GestureTemplate(name = "") },
                colors = ButtonDefaults.buttonColors(containerColor = Cores.destaque),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = Cores.sobreODestaque)
                Spacer(Modifier.width(6.dp))
                Text("Gravar gesto", color = Cores.sobreODestaque, fontWeight = FontWeight.SemiBold)
            }
        }

        MacroInterruptor(
            titulo = "Reconhecer gestos desenhados",
            descricao = "Desligado, os desenhos continuam gravados mas nenhum deles dispara macro. " +
                "Precisa do Shizuku de pé: o leitor de toque é o mesmo dos gestos de 3 dedos.",
            ligado = habilitado,
            aoMudar = {
                habilitado = it
                GestureStore.setEnabled(it)
            }
        )

        if (gestos.isEmpty()) {
            CartaoVazio(
                titulo = "Nenhum gesto gravado",
                texto = "Toque em \"Gravar gesto\", desenhe uma forma no quadro e salve. Depois é " +
                    "só criar uma macro com o gatilho \"Gesto na tela\"."
            )
        } else {
            gestos.forEach { gesto ->
                CartaoDeGesto(
                    gesto = gesto,
                    aoEditar = { emGravacao = gesto },
                    aoApagar = { pendenteDeExclusao = gesto },
                    aoCriarMacro = { onCriarMacro(gesto) }
                )
            }
        }
    }

    pendenteDeExclusao?.let { alvo ->
        val presas = MacroStore.porGesto(alvo.id)
        AlertDialog(
            onDismissRequest = { pendenteDeExclusao = null },
            title = { Text("Apagar \"${alvo.name}\"?") },
            text = {
                Text(
                    if (presas.isEmpty()) "O desenho será apagado."
                    else "O desenho será apagado. ${presas.size} macro(s) ficam sem gatilho e param " +
                        "de rodar sozinhas: ${presas.joinToString { it.name }}."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    GestureStore.apagar(alvo.id)
                    pendenteDeExclusao = null
                }) { Text("Apagar", color = Cores.perigo) }
            },
            dismissButton = {
                TextButton(onClick = { pendenteDeExclusao = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun CartaoDeGesto(
    gesto: GestureTemplate,
    aoEditar: () -> Unit,
    aoApagar: () -> Unit,
    aoCriarMacro: () -> Unit
) {
    val presas = MacroStore.porGesto(gesto.id)
    Row(
        modifier = Modifier.fillMaxWidth().liquidGlass().padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiniaturaDeGesto(gesto.caminho, modifier = Modifier.size(84.dp))
        Spacer(Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                gesto.name,
                fontFamily = Fontes.texto,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                color = Cores.textoPrincipal
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${gesto.dedos} dedo(s) · " + if (presas.isEmpty()) "nenhuma macro usa este gesto"
                else presas.joinToString { it.name },
                fontFamily = Fontes.texto,
                fontSize = 13.sp,
                color = if (presas.isEmpty()) Cores.textoApagado else Cores.textoSecundario
            )
        }
        if (presas.isEmpty()) {
            Button(
                onClick = aoCriarMacro,
                colors = ButtonDefaults.buttonColors(containerColor = Cores.destaque),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Criar macro", color = Cores.sobreODestaque, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(8.dp))
        }
        TextButton(onClick = aoEditar) { Text("Regravar", color = Cores.destaque) }
        IconButton(onClick = aoApagar) {
            Icon(Icons.Outlined.Delete, contentDescription = "Apagar", tint = Cores.perigo)
        }
    }
}

/**
 * O quadro de gravação.
 *
 * O traço aparece sob o dedo enquanto ele anda — não no fim. Sem isso o motorista desenha às cegas
 * e só descobre no resultado se a mão tremeu, o que transforma gravar um gesto em tentativa e erro.
 */
@Composable
private fun GravadorDeGesto(
    inicial: GestureTemplate,
    onCancelar: () -> Unit,
    onSalvar: (GestureTemplate) -> Unit
) {
    var nome by remember { mutableStateOf(inicial.name) }
    var dedos by remember { mutableStateOf(inicial.dedos) }
    val pontos = remember { mutableStateListOf<Offset>() }
    var caminhoSalvo by remember { mutableStateOf(inicial.caminho) }
    var aviso by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancelar) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Cores.textoPrincipal)
            }
            Text(
                if (inicial.caminho.isEmpty()) "GRAVAR GESTO" else "REGRAVAR GESTO",
                fontFamily = Fontes.titulo,
                fontSize = 20.sp,
                letterSpacing = 1.sp,
                color = Cores.textoPrincipal,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // A caixa tem a PROPORÇÃO DA TELA do carro de propósito.
            //
            // O traço é normalizado por eixo aqui e por eixo no leitor do `/dev/input` (lá, pelas
            // faixas do digitalizador). Se as duas caixas tiverem proporções diferentes, o mesmo
            // desenho chega aos dois lados esticado de formas diferentes — e num gesto quase reto,
            // onde a escala não pode esticar a caixa até o quadrado, essa diferença é real
            // (ver `GestureRecognizer.suavizar`). Copiar a proporção da tela zera o problema na
            // origem; a suavização cobre o que sobrar de folga no digitalizador.
            val configuracao = LocalConfiguration.current
            val proporcaoDaTela = remember(configuracao.screenWidthDp, configuracao.screenHeightDp) {
                val l = configuracao.screenWidthDp.toFloat()
                val a = configuracao.screenHeightDp.toFloat()
                if (l > 0f && a > 0f) (l / a).coerceIn(0.5f, 3.5f) else 1.6f
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(proporcaoDaTela)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF0E1015))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { inicio ->
                                pontos.clear()
                                pontos.add(inicio)
                            },
                            onDrag = { mudanca, _ ->
                                mudanca.consume()
                                pontos.add(mudanca.position)
                            },
                            onDragEnd = {
                                val novo = run {
                                    if (pontos.size < 4 || size.width <= 0 || size.height <= 0) null
                                    else {
                                        val cru = FloatArray(pontos.size * 2)
                                        pontos.forEachIndexed { i, p ->
                                            // Normaliza por EIXO, igual ao leitor do carro, que
                                            // divide pelas faixas do digitalizador. É o que faz o
                                            // gesto gravado aqui casar com o mesmo gesto feito
                                            // depois na tela inteira, de outra proporção.
                                            cru[i * 2] = p.x / size.width
                                            cru[i * 2 + 1] = p.y / size.height
                                        }
                                        GestureRecognizer.normalizarParaDesenho(cru)
                                    }
                                }
                                if (novo != null && novo.isNotEmpty()) {
                                    caminhoSalvo = novo
                                    val parecido = MacroGestureRouter
                                        .melhorCasamento(novo, dedos, ignorarId = inicial.id)
                                    aviso = if (parecido != null &&
                                        parecido.nota >= MacroGestureRouter.LIMIAR_PARECIDO
                                    ) {
                                        "Muito parecido com \"${parecido.gesto.name}\" " +
                                            "(${(parecido.nota * 100).toInt()}%). Os dois juntos " +
                                            "ficam ambíguos e nenhum vai disparar — desenhe algo " +
                                            "mais distinto."
                                    } else null
                                } else {
                                    aviso = "Traço curto demais. Desenhe uma forma maior."
                                }
                            }
                        )
                    }
            ) {
                val acento = Cores.destaque
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (pontos.size > 1) {
                        desenharCaminhoDePontos(pontos, acento)
                    } else if (caminhoSalvo.isNotEmpty()) {
                        desenharCaminhoNormalizado(caminhoSalvo, acento.copy(alpha = 0.45f))
                    }
                }
                if (pontos.isEmpty() && caminhoSalvo.isEmpty()) {
                    Text(
                        "Desenhe aqui com um dedo",
                        fontFamily = Fontes.texto,
                        fontSize = 16.sp,
                        color = Cores.textoApagado,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Nome do gesto") },
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

                Text(
                    "Quantos dedos para executar",
                    fontFamily = Fontes.texto,
                    fontSize = 14.sp,
                    color = Cores.textoSecundario
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(2 to "Dois dedos", 1 to "Um dedo").forEach { (n, rotulo) ->
                        Button(
                            onClick = { dedos = n },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (dedos == n) Cores.destaque else Cores.conteiner
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                rotulo,
                                color = if (dedos == n) Cores.sobreODestaque else Cores.textoPrincipal,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Text(
                    if (dedos == 1)
                        "Com um dedo o gesto compete com tudo que se arrasta na tela — rolar lista, " +
                            "mover mapa. A exigência de semelhança sobe para compensar, e ainda " +
                            "assim é a opção mais sujeita a engano."
                    else
                        "Dois dedos é o recomendado: o sistema quase não usa essa faixa, e a pinça " +
                            "(zoom) é descartada antes de chegar ao reconhecedor.",
                    fontFamily = Fontes.texto,
                    fontSize = 12.5.sp,
                    color = Cores.textoApagado
                )

                aviso?.let {
                    Text(it, fontFamily = Fontes.texto, fontSize = 13.sp, color = Cores.perigo)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            pontos.clear()
                            caminhoSalvo = FloatArray(0)
                            aviso = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Cores.conteiner),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Limpar", color = Cores.textoPrincipal) }

                    Button(
                        onClick = {
                            val caminho = caminhoSalvo
                            if (caminho.isEmpty()) {
                                aviso = "Desenhe o gesto antes de salvar."
                                return@Button
                            }
                            onSalvar(
                                inicial.copy(
                                    name = nome.trim().ifBlank { "Gesto" },
                                    dedos = dedos,
                                    caminho = caminho
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Cores.destaque),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Salvar gesto", color = Cores.sobreODestaque, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/** A forma gravada, desenhada dentro de uma caixa. Usada na lista e no seletor de gatilho. */
@Composable
fun MiniaturaDeGesto(caminho: FloatArray, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0E1015))
    ) {
        val acento = Cores.destaque
        Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            if (caminho.size >= 4) desenharCaminhoNormalizado(caminho, acento)
        }
    }
}

private fun DrawScope.desenharCaminhoNormalizado(caminho: FloatArray, cor: Color) {
    // O caminho vem com o lado maior valendo 1 e o menor valendo menos — a proporção é do desenho,
    // não da caixa. Multiplicar pelo MENOR lado da caixa é o que mantém a forma inteira dentro dela.
    val escala = minOf(size.width, size.height)
    val desvioX = (size.width - escala) / 2f
    val desvioY = (size.height - escala) / 2f
    val path = Path()
    var i = 0
    while (i < caminho.size - 1) {
        val x = desvioX + caminho[i] * escala
        val y = desvioY + caminho[i + 1] * escala
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        i += 2
    }
    drawPath(path, cor, style = Stroke(width = 5f))
}

private fun DrawScope.desenharCaminhoDePontos(pontos: List<Offset>, cor: Color) {
    val path = Path()
    pontos.forEachIndexed { i, p ->
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    drawPath(path, cor, style = Stroke(width = 7f))
}
