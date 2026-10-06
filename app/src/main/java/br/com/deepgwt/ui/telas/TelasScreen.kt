// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui.telas

import androidx.compose.foundation.combinedClickable
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import br.com.deepgwt.ui.Slider
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import br.com.deepgwt.nucleo.DeepGwt
import br.com.deepgwt.nucleo.Preferencias
import br.com.deepgwt.painel.PainelDeInstrumentos
import br.com.deepgwt.painel.TemasInstalados
import br.com.deepgwt.telas.OriginalTempScale
import br.com.deepgwt.ui.CartaoClassico
import br.com.deepgwt.ui.Cores
import br.com.deepgwt.hud.HudOverlayController
import br.com.deepgwt.hud.HudSettings
import br.com.deepgwt.licenca.Recurso
import br.com.deepgwt.managers.CastaDoTema
import br.com.deepgwt.managers.TemaDoPainel
import br.com.deepgwt.projection.ScreenProjectionController
import br.com.deepgwt.ui.components.SeloDoNivel
import br.com.deepgwt.ui.components.TravaDoNivel
import br.com.deepgwt.ui.components.liberado
import br.com.deepgwt.ui.screens.ModoDoPainel
import br.com.deepgwt.ui.screens.PainelEscolhido
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TelasTab() {
    val context = LocalContext.current
    val prefs = DeepGwt.contextoProtegido.getSharedPreferences("deepgwt_prefs", Context.MODE_PRIVATE)
    val scope = rememberCoroutineScope()

    // O que o DeepGWT faz no painel de instrumentos: interruptores independentes (ver
    // PainelEscolhido) — tema customizado, informações extras e projeção no círculo sobre o painel
    // de fábrica. O modo que sai deles grava as mesmas quatro chaves que o resto do app lê; os dois
    // nomes abaixo só existem para o corpo desta tela continuar legível.
    var painel by remember { mutableStateOf(PainelEscolhido.ler(prefs)) }
    val modoDoPainel = painel.modo
    val allClusterFunctionsEnabled = modoDoPainel.usaCamadaDoPainel
    val enableMask = modoDoPainel.desenhaOPainelInteiro
    // Lidos direto do controlador, sem `remember`: o estado da projeção muda por fora desta tela
    // (o gesto de 4 dedos, o botão flutuante, a própria central encerrando a captura), e uma cópia
    // guardada mostraria "Escolher área" com a projeção no ar.
    val projecaoAtiva = ScreenProjectionController.estado.value == ScreenProjectionController.Estado.PROJETANDO
    val projecaoErro =
            if (ScreenProjectionController.estado.value == ScreenProjectionController.Estado.FALHA) {
                ScreenProjectionController.ultimoErro.value
            } else {
                null
            }

    // Camada do HUD. O estado mostrado é o que está DESENHANDO agora, não o que a preferência diz:
    // a Activity do HUD pode ter sido encerrada por fora (troca de usuário, `am` manual), e um
    // botão ligado com o HUD apagado seria mentira.
    var hudOverlayEnabled by remember {
        mutableStateOf(HudOverlayController.isShowing || HudOverlayController.isEnabled())
    }
    var hudOverlayStatus by remember { mutableStateOf("") }
    // Os ajustes do HUD moram em HudSettings (preferências + espelho observável), porque o
    // desenho do HUD também os lê. Aqui ficam só os estados desta tela.
    // Na composição, não num efeito: `hudCorHex` abaixo lê a cor UMA vez, e um efeito só roda depois
    // da primeira composição, então o campo nascia com o branco de fábrica e a edição de um dígito
    // gravava a cor errada. `load` é idempotente.
    remember { HudSettings.load(); true }
    var hudCorHex by remember {
        mutableStateOf(String.format("#%06X", HudSettings.speedColor and 0xFFFFFF))
    }
    // Seletor de cor livre da velocidade. O HUD é um display de verdade desenhando agora, então
    // ele É a prévia: o `onPreview` do seletor empurra a cor para lá enquanto o dedo arrasta, e
    // cancelar devolve a cor de antes — o mesmo contrato que o seletor já cumpre com o painel.
    var hudSeletorDeCorAberto by remember { mutableStateOf(false) }

    // O fundo do cluster e a tela de projeção saíram desta tela; quem tinha mudado um deles volta
    // para o automático em vez de ficar preso numa escolha que não tem mais onde desfazer.
    LaunchedEffect(Unit) {
        if (ModoDoPainel.normalizarOpcoesRemovidas(prefs)) {
            PainelDeInstrumentos.recriar()
        }
    }

    var pastaAtiva by remember { mutableStateOf(TemaDoPainel.ativo(prefs)) }
    var showVirtualClusterWarningDialog by remember { mutableStateOf(false) }
    // A lista lê o `theme.xml` de cada tema (~275): fora da thread principal, senão a aba trava ao abrir.
    var localThemes by remember { mutableStateOf<List<br.com.deepgwt.painel.TemaInstalado>>(emptyList()) }
    var isRefreshingThemes by remember { mutableStateOf(false) }

    // Ao abrir: a próxima revisão volta a ser publicada para o painel (em qualquer modo), e a lista
    // de temas é relida depois de sanear o tema ativo.
    LaunchedEffect(Unit) {
        HistoricoDeServico.atualizarProxima(prefs)
        val lista = withContext(Dispatchers.IO) {
            TemasInstalados.sanearTemaAtivo()
            TemasInstalados.listar()
        }
        pastaAtiva = TemaDoPainel.ativo(prefs)
        localThemes = lista
    }

    // Mudar um interruptor do painel. O tema customizado passa antes pelo aviso de risco, como o
    // antigo interruptor do Painel Virtual; os outros valem na hora.
    val aplicarPainel: (PainelEscolhido) -> Unit = { novo ->
        val anterior = painel
        PainelEscolhido.aplicar(context, prefs, anterior, novo)
        painel = novo
        // Desligar devolve ao Default; ligar sem tema escolhido cai no tema padrão (PainelEscolhido.aplicar).
        pastaAtiva = TemaDoPainel.ativo(prefs)
        // O lugar da projeção pode ter mudado (tema ligado ou desligado, círculo desligado): uma
        // projeção no ar que perdeu o lugar dela é encerrada.
        ScreenProjectionController.aoMudarAsEscolhasDoPainel()
        try {
            br.com.deepgwt.veiculo.Comandos.garantirAppsDoSistema()
            if (novo.modo.usaCamadaDoPainel) {
                br.com.deepgwt.veiculo.Comandos.iniciarPulsoDoPainel()
            }
        } catch (e: Exception) {
            Log.e("TelasTab", "Erro ao trocar o modo do painel: ${e.message}", e)
        }
    }
    val alternarTemaCustomizado: (Boolean) -> Unit = { ligar ->
        if (ligar) {
            showVirtualClusterWarningDialog = true
        } else {
            aplicarPainel(painel.copy(temaCustomizado = false))
        }
    }
    // O mesmo interruptor do HUD mora no alto (junto das outras escolhas) e no cartão do HUD, com
    // os ajustes. Os dois leem e escrevem o mesmo estado.
    val alternarHud: (Boolean) -> Unit = { checked ->
        hudOverlayEnabled = checked
        hudOverlayStatus = if (checked) "Abrindo no HUD..." else "Removendo..."
        // O callback chega da thread do shell; `scope` é o escopo de composição (Main), então a
        // volta para a UI acontece aqui.
        HudOverlayController.setEnabled(checked) { resultado ->
            scope.launch {
                hudOverlayStatus = resultado
                // A camada pode ter sido recusada: o interruptor volta para o estado REAL em vez de
                // mentir.
                hudOverlayEnabled = HudOverlayController.isShowing || HudOverlayController.isEnabled()
            }
        }
    }

    Column(
            modifier =
                    Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── PAINEL DE INSTRUMENTOS ──
        CartaoClassico(modifier = Modifier.padding(horizontal = 8.dp)) {
            Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column {
                    Text(
                            "Painel de instrumentos",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                    )
                    Text(
                            "O que o DeepGWT mostra atrás do volante e no para-brisa. Cada item " +
                                    "liga e desliga sozinho; tudo desligado deixa só o painel de fábrica.",
                            color = Cores.textoSecundario,
                            fontSize = 14.sp
                    )
                }
                // "Informações extras" e "Círculo do painel" são do painel de FÁBRICA: com o tema
                // customizado ligado ele desenha o painel inteiro, e as duas ficam guardadas,
                // apagadas, até ele ser desligado. O interruptor apagado mostra o que está GUARDADO
                // — é o que volta a valer —, não um "desligado" que depois se acenderia sozinho.
                val sobreOPainelDeFabrica = !painel.temaCustomizado
                Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Alpha 8.0: o painel inteiro com tema é do Sport; as informações e o círculo, do Drive.
                    TravaDoNivel(Recurso.TEMAS_EMBUTIDOS, Modifier.weight(1f).fillMaxHeight()) {
                        InterruptorDoPainel(
                                icone = Icons.Default.Palette,
                                titulo = "Tema customizado",
                                ajusteId = "painel.tema_customizado",
                                descricao = "O DeepGWT desenha o painel inteiro com o tema que você escolher.",
                                ligado = painel.temaCustomizado,
                                modifier = Modifier.fillMaxSize(),
                                onChange = alternarTemaCustomizado
                        )
                    }
                    InterruptorDoPainel(
                            icone = Icons.Default.Thermostat,
                            titulo = "Informações extras",
                            ajusteId = "painel.informacoes",
                            descricao =
                                    if (sobreOPainelDeFabrica) {
                                        "Temperatura externa no alto e placa de limite da via, " +
                                                "sobre o painel de fábrica."
                                    } else {
                                        "Só no painel de fábrica. Volta ao desligar o tema customizado."
                                    },
                            ligado = painel.informacoes,
                            habilitado = sobreOPainelDeFabrica,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onChange = { aplicarPainel(painel.copy(informacoes = it)) }
                    )
                    InterruptorDoPainel(
                            icone = Icons.AutoMirrored.Filled.ScreenShare,
                            titulo = "Círculo do painel",
                            ajusteId = "painel.circulo",
                            descricao =
                                    if (sobreOPainelDeFabrica) {
                                        "O círculo da direita ganha páginas no volante: card do " +
                                                "carro, projeção da central, relógio e HUD."
                                    } else {
                                        "Só no painel de fábrica. Com o tema, a projeção vai para o " +
                                                "vão entre os mostradores."
                                    },
                            ligado = painel.circulo,
                            habilitado = sobreOPainelDeFabrica,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onChange = { aplicarPainel(painel.copy(circulo = it)) }
                    )
                    TravaDoNivel(Recurso.HUD, Modifier.weight(1f).fillMaxHeight()) {
                        InterruptorDoPainel(
                                icone = Icons.Default.Speed,
                                titulo = "Head-up display",
                                ajusteId = "hudOverlayEnabled",
                                descricao = "Velocidade, páginas e alertas no para-brisa. Ajustes mais abaixo.",
                                ligado = hudOverlayEnabled,
                                modifier = Modifier.fillMaxSize(),
                                onChange = alternarHud
                        )
                    }
                }

                // Tamanho da temperatura das informações extras (Alpha 6.7). Só faz sentido com
                // elas na tela — sobre o painel de fábrica e ligadas —, então só aparece assim.
                // Aplica na hora, como o tamanho do texto do tema (--orig-temp-scale).
                if (sobreOPainelDeFabrica && painel.informacoes) {
                    var escalaDaTemperatura by remember {
                        mutableStateOf(
                                OriginalTempScale.normalizar(
                                        prefs.getFloat(
                                                Preferencias.ORIGINAL_PANEL_TEMP_SCALE.chave,
                                                OriginalTempScale.PADRAO
                                        )
                                )
                        )
                    }
                    Column {
                        Text(
                                "Tamanho da temperatura  ${OriginalTempScale.emPx(escalaDaTemperatura)} px",
                                color = Cores.textoSecundario,
                                fontSize = 12.sp
                        )
                        Text(
                                "A temperatura externa no alto do painel de fábrica. O padrão é 44 px.",
                                color = Cores.textoApagado,
                                fontSize = 11.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Slider(
                                value = escalaDaTemperatura,
                                onValueChange = { nova ->
                                    escalaDaTemperatura = OriginalTempScale.normalizar(nova)
                                    prefs.edit {
                                        putFloat(
                                                Preferencias.ORIGINAL_PANEL_TEMP_SCALE.chave,
                                                escalaDaTemperatura
                                        )
                                    }
                                    br.com.deepgwt.telas.Painel.aplicarEscalaDaTemperatura()
                                },
                                valueRange = OriginalTempScale.MINIMO..OriginalTempScale.MAXIMO
                        )
                    }
                }
            }
        }

        // ── TEMA DO PAINEL ── só existe no modo em que o DeepGWT desenha o painel inteiro.
        if (enableMask) {
        CartaoClassico(modifier = Modifier.padding(horizontal = 8.dp)) {
            Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                    Text(
                            "Tema do painel",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                    )

                    Column {
                        CabecalhoDosTemas(
                                atualizando = isRefreshingThemes,
                                aoRecarregar = {
                                    isRefreshingThemes = true
                                    scope.launch {
                                        localThemes = withContext(Dispatchers.IO) { TemasInstalados.listar() }
                                        isRefreshingThemes = false
                                    }
                                }
                        )
                        Spacer(Modifier.height(8.dp))
                        // Fora da lista: o Default embutido (saiu na 6.8 — com o tema customizado ligado o
                        // painel usa um tema da pasta, ver TemaDoPainel.PASTA_PADRAO), o rascunho do criador
                        // de temas (some sozinho quando o computador sai do "Ver no carro") e a grade da
                        // calibração do painel (8.8, entra pelo Diagnóstico).
                        //
                        // Duas castas: Legacy (as que vêm no APK) e XTRA (tudo que chegou depois — o
                        // catálogo do instalador, HTML importado, criador de temas). LazyRow porque o
                        // XTRA pode ter centenas: só os cartões visíveis carregam a miniatura.
                        val porCasta = remember(localThemes) {
                            localThemes
                                    .filter {
                                        it.nome != "Default" && it.pasta != "Default" &&
                                                it.pasta != TemaDoPainel.PASTA_DO_RASCUNHO &&
                                                it.pasta != TemaDoPainel.PASTA_DA_CALIBRACAO &&
                                                TemasInstalados.suportado(it)
                                    }
                                    .groupBy { CastaDoTema.de(it.pasta, TemasInstalados.pastasDoApk) }
                                    .mapValues { (_, lista) -> lista.sortedBy { it.nome.lowercase() } }
                        }
                        CastaDoTema.values().forEach { casta ->
                        val temasDaCasta = porCasta[casta].orEmpty()
                        Row(
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                    casta.titulo.uppercase(),
                                    color = Cores.destaque,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                            )
                            Text("${temasDaCasta.size}", color = Cores.textoApagado, fontSize = 12.sp)
                            val recursoDaCasta = TemaDoPainel.recursoDa(casta)
                            if (!liberado(recursoDaCasta)) SeloDoNivel(recursoDaCasta.nivel)
                        }
                        if (temasDaCasta.isEmpty()) {
                            Text(
                                    if (casta == CastaDoTema.XTRA)
                                        "Nenhum tema XTRA no carro. Envie pelo DeepGWT no computador: " +
                                                "Temas do painel → Catálogo XTRA."
                                    else "Nenhum tema Legacy encontrado.",
                                    color = Cores.textoApagado,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else
                        TravaDoNivel(TemaDoPainel.recursoDa(casta), Modifier.fillMaxWidth()) {
                        LazyRow(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(temasDaCasta, key = { it.pasta.ifBlank { it.nome } }) { tema ->
                                // O escolhido é o da PASTA ativa, não o do nome de exibição: dois temas
                                // com o mesmo nome não aparecem os dois marcados.
                                CartaoDeTema(
                                        tema = tema,
                                        selecionado = pastaAtiva == tema.pasta,
                                        podeApagar = true,
                                        aoTocar = {
                                            if (allClusterFunctionsEnabled) {
                                                // O fundo do cluster é sempre o que o próprio tema declara;
                                                // a gravação mora em TemaDoPainel desde a 6.5 (a mesma do
                                                // instalador e do "Ver no carro").
                                                TemaDoPainel.aplicar(prefs, tema)
                                                pastaAtiva = TemaDoPainel.ativo(prefs)
                                            }
                                        },
                                        aoApagar = {
                                            if (allClusterFunctionsEnabled && TemasInstalados.apagar(tema.pasta)) {
                                                // Apagar o tema ativo já trocou o tema (para o padrão, com o
                                                // tema customizado ligado).
                                                pastaAtiva = TemaDoPainel.ativo(prefs)
                                                localThemes = TemasInstalados.listar()
                                            }
                                        }
                                )
                            }
                        }
                        }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    HorizontalDivider(color = Cores.trilhoDesligado)
                    Text(
                            "APARÊNCIA",
                            color = Cores.destaque,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                    )

                    // ── TAMANHO DOS NÚMEROS ──
                    // GAUGE_NUMBER_SCALE é injetada direto como CSS var (--gauge-num-scale) no
                    // WebView do Painel Virtual (PainelDeInstrumentos.aplicarEscalaDosNumeros) --
                    // os mostradores principais dos 4 temas customizados já leem essa var no
                    // font-size. Aplica na hora, sem precisar recarregar o tema.
                    Column {
                        var gaugeScale by remember {
                            mutableStateOf(
                                    prefs.getFloat(
                                            Preferencias.GAUGE_NUMBER_SCALE.chave,
                                            1.0f
                                    )
                            )
                        }
                        Text(
                                "Tamanho do texto do painel  ${"%.0f".format(gaugeScale * 100)}%",
                                color = Cores.textoSecundario,
                                fontSize = 12.sp
                        )
                        Text(
                                "Vale para o tema inteiro, seja qual for — a escala é aplicada " +
                                        "sobre os tamanhos que o próprio tema define. Em 100% nada muda.",
                                color = Cores.textoApagado,
                                fontSize = 11.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Slider(
                                value = gaugeScale,
                                onValueChange = { newScale ->
                                    gaugeScale = newScale
                                    prefs.edit { putFloat(Preferencias.GAUGE_NUMBER_SCALE.chave, newScale) }
                                    br.com.deepgwt.telas.Painel.aplicarEscalaDosNumeros()
                                },
                                valueRange = 0.7f..1.4f,
                                enabled = allClusterFunctionsEnabled
                        )
                    }

                    HorizontalDivider(color = Cores.trilhoDesligado)

                    // ── MODO NOTURNO DO PAINEL: ligado/desligado (Alpha 9.8) ──
                    // Desligado, o tema recebe o farol sempre apagado (PonteDosTemas.farolAgora) e a
                    // cor dos letreiros não muda ao acender o farol. A cor e a fonte abaixo só aparecem ligado.
                    var noturnoDoPainel by remember {
                        mutableStateOf(prefs.getBoolean(Preferencias.NIGHT_MODE_ENABLED.chave, true))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Modo noturno do painel", color = Cores.textoSecundario, fontSize = 12.sp)
                            Text(
                                    "Com o farol aceso, os letreiros do painel mudam de cor. Desligado, o painel " +
                                            "mantém as cores do tema mesmo com o farol aceso.",
                                    color = Cores.textoApagado,
                                    fontSize = 11.sp
                            )
                        }
                        Switch(
                                checked = noturnoDoPainel,
                                enabled = allClusterFunctionsEnabled,
                                onCheckedChange = {
                                    noturnoDoPainel = it
                                    prefs.edit { putBoolean(Preferencias.NIGHT_MODE_ENABLED.chave, it) }
                                    br.com.deepgwt.telas.Painel.republicarFarol()
                                },
                                colors =
                                        SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = Cores.destaque
                                        )
                        )
                    }

                    if (noturnoDoPainel) {
                    HorizontalDivider(color = Cores.trilhoDesligado)

                    // ── MODO NOTURNO (cor ao ligar o farol) ──
                    // NIGHT_MODE_COLOR é injetada direto como CSS var (--night-tint/--night-glow)
                    // no WebView (PainelDeInstrumentos.aplicarCorDoModoNoturno) -- o tema troca pra
                    // essa cor sozinho quando car.basic.head_light_status liga (classe
                    // .headlights-on no body). Igual à escala de número, aplica na hora.
                    Column {
                        var nightColor by remember {
                            mutableStateOf(
                                    prefs.getString(
                                            Preferencias.NIGHT_MODE_COLOR.chave,
                                            "amber"
                                    ) ?: "amber"
                            )
                        }
                        Text(
                                "Cor do modo noturno (ao ligar o farol)",
                                color = Cores.textoSecundario,
                                fontSize = 12.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val options = listOf(
                                    Triple("amber", "Âmbar", Color(0xFFFF9500)),
                                    Triple("red", "Vermelho", Color(0xFFFF2200)),
                                    Triple("mixed", "Meio-termo", Color(0xFFFF5C1A))
                            )
                            options.forEach { (value, label, swatch) ->
                                val selected = nightColor == value
                                Row(
                                        modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .border(
                                                        width = if (selected) 2.dp else 1.dp,
                                                        color = if (selected) swatch else Cores.trilhoDesligado,
                                                        shape = RoundedCornerShape(10.dp)
                                                )
                                                .clickable(enabled = allClusterFunctionsEnabled) {
                                                    nightColor = value
                                                    prefs.edit {
                                                        putString(
                                                                Preferencias.NIGHT_MODE_COLOR.chave,
                                                                value
                                                        )
                                                    }
                                                    br.com.deepgwt.telas.Painel.aplicarCorDoModoNoturno()
                                                }
                                                .padding(vertical = 10.dp, horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                            modifier = Modifier
                                                    .size(12.dp)
                                                    .clip(CircleShape)
                                                    .background(swatch)
                                    )
                                    Text(
                                            label,
                                            color = if (selected) Cores.textoPrincipal else Cores.textoSecundario,
                                            fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Cores.trilhoDesligado)

                    // ── QUANDO ACENDER O MODO NOTURNO ──
                    //
                    // O contrato dos temas tem uma chave só para o farol -- car.basic.head_light_status
                    // -- e NESTE CARRO ela chega vazia: está no barramento, é monitorada, e o veículo
                    // nunca publica valor nenhum nela. Era por isso que a cor escolhida logo acima não
                    // fazia efeito nenhum: os temas liam "" e entendiam farol apagado, sempre.
                    //
                    // HeadlightPolicy resolve o estado a partir das luzes que o carro DE FATO publica.
                    // Qual delas acompanha o farol de verdade é a única coisa que não dá para decidir
                    // de fora do carro, então a fonte é escolhível aqui e os valores crus aparecem
                    // logo abaixo: girar o botão das luzes mostra qual chave se mexe.
                    Column {
                        var nightTrigger by remember {
                            mutableStateOf(
                                    prefs.getString(
                                            Preferencias.NIGHT_MODE_TRIGGER.chave,
                                            br.com.deepgwt.veiculo.HeadlightPolicy.FONTE_PADRAO
                                    ) ?: br.com.deepgwt.veiculo.HeadlightPolicy.FONTE_PADRAO
                            )
                        }
                        var luzesCruas by remember {
                            mutableStateOf(br.com.deepgwt.veiculo.HeadlightPolicy.Sinais())
                        }
                        // Uma leitura por segundo enquanto esta tela está aberta. É o que transforma
                        // "qual chave é o farol?" numa pergunta que o carro responde sozinho.
                        LaunchedEffect(Unit) {
                            while (true) {
                                luzesCruas = br.com.deepgwt.veiculo.HeadlightPolicy
                                        .sinaisDe { chave -> runCatching { br.com.deepgwt.veiculo.Veiculo.ler(chave) }.getOrNull() }
                                delay(1000)
                            }
                        }
                        Text(
                                "Quando acender o modo noturno",
                                color = Cores.textoSecundario,
                                fontSize = 12.sp
                        )
                        Text(
                                "Neste carro a chave do farol (car.basic.head_light_status) chega " +
                                        "vazia, então o automático usa a luz baixa/alta no lugar dela. " +
                                        "Se acender na hora errada, troque a fonte aqui.",
                                color = Cores.textoApagado,
                                fontSize = 11.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        val fontes = listOf(
                                br.com.deepgwt.veiculo.HeadlightPolicy.FONTE_AUTO,
                                br.com.deepgwt.veiculo.HeadlightPolicy.FONTE_LUZ_BAIXA,
                                br.com.deepgwt.veiculo.HeadlightPolicy.FONTE_POSICAO,
                                br.com.deepgwt.veiculo.HeadlightPolicy.FONTE_MESTRE,
                                br.com.deepgwt.veiculo.HeadlightPolicy.FONTE_HORARIO,
                                br.com.deepgwt.veiculo.HeadlightPolicy.FONTE_NUNCA
                        )
                        FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            fontes.forEach { fonte ->
                                val selected = nightTrigger == fonte
                                Text(
                                        br.com.deepgwt.veiculo.HeadlightPolicy
                                                .rotuloDaFonte(fonte),
                                        color = if (selected) Cores.textoPrincipal else Cores.textoSecundario,
                                        fontSize = 12.sp,
                                        modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .border(
                                                        width = if (selected) 2.dp else 1.dp,
                                                        color = if (selected) Color(0xFFFF9500) else Cores.trilhoDesligado,
                                                        shape = RoundedCornerShape(10.dp)
                                                )
                                                .clickable(enabled = allClusterFunctionsEnabled) {
                                                    nightTrigger = fonte
                                                    prefs.edit {
                                                        putString(
                                                                Preferencias.NIGHT_MODE_TRIGGER.chave,
                                                                fonte
                                                        )
                                                    }
                                                    br.com.deepgwt.telas.Painel.republicarFarol()
                                                }
                                                .padding(vertical = 10.dp, horizontal = 12.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        val agoraAceso = br.com.deepgwt.veiculo.HeadlightPolicy
                                .resolver(
                                        nightTrigger,
                                        luzesCruas,
                                        br.com.deepgwt.veiculo.HeadlightPolicy.horaLocal()
                                )
                        Text(
                                "Agora: " + (if (agoraAceso) "modo noturno LIGADO" else "modo noturno desligado"),
                                color = if (agoraAceso) Color(0xFFFF9500) else Cores.textoSecundario,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                        )
                        Text(
                                "farol: '" + (luzesCruas.mestre ?: "") + "'   " +
                                        "luz baixa: '" + (luzesCruas.luzBaixa ?: "") + "'   " +
                                        "luz alta: '" + (luzesCruas.luzAlta ?: "") + "'   " +
                                        "posição: '" + (luzesCruas.posicao ?: "") + "'",
                                color = Cores.textoApagado,
                                fontSize = 11.sp
                        )
                    }
                    }

                    HorizontalDivider(color = Cores.trilhoDesligado)
                    Text(
                            "INFORMAÇÕES NO PAINEL",
                            color = Cores.destaque,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                    )
                    SecaoDeInformacoesNoPainel(prefs, habilitada = allClusterFunctionsEnabled)

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = Cores.trilhoDesligado)
                    Spacer(Modifier.height(16.dp))

                    SecaoDeOdometroERevisao(prefs, habilitada = allClusterFunctionsEnabled)

                    HorizontalDivider(color = Cores.trilhoDesligado)
                    Text(
                            "PROJETAR A CENTRAL NO PAINEL",
                            color = Cores.destaque,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                    )
                    // ── Projetar captura da central ───────────────────────────────────────────
                    //
                    // Mora aqui porque, com o tema customizado, o destino é o vão entre os
                    // mostradores DO TEMA. No painel de fábrica o mesmo controle aparece no cartão
                    // dele, e o destino é o círculo da direita.
                    ControlesDaProjecao(
                            titulo = "Projetar captura da central",
                            descricaoParada =
                                    "Abre um quadro já no formato do vão entre os mostradores do painel: arraste até enquadrar " +
                                            "o que quer projetar e toque em CONFIRMAR. A imagem preenche o vão inteiro e some " +
                                            "suavemente nas bordas, com os mostradores por cima. Atalho: toque duplo com 4 dedos " +
                                            "em qualquer tela.",
                            projecaoAtiva = projecaoAtiva,
                            projecaoErro = projecaoErro
                    )
                }
            }
        }


        // ── PAINEL DE FÁBRICA ── o que aparece por cima dele, dito sem rodeio, e a projeção no
        // círculo quando ela está ligada.
        if (modoDoPainel == ModoDoPainel.ORIGINAL_COM_INFORMACOES) {
            CartaoClassico(modifier = Modifier.padding(horizontal = 8.dp)) {
                Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                            "No painel de fábrica",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                    )
                    Text(
                            buildString {
                                if (painel.informacoes) {
                                    append("Temperatura externa no alto, à esquerda, e placa de limite da via embaixo. ")
                                }
                                if (painel.circulo) {
                                    append(
                                            "Nos cards 1 e 3, ↑/↓ do volante trocam a página do círculo da " +
                                                    "direita: Carro (o card do próprio carro), Central (a projeção), " +
                                                    "Relógio e HUD. Na Central, segure OK para projetar a última " +
                                                    "área (segure de novo para encerrar). No Relógio, ← e → trocam " +
                                                    "entre os catorze relógios — o carro troca de card por baixo, e por " +
                                                    "isso o relógio fica em qualquer card, inclusive o 0. Na página " +
                                                    "HUD, segure OK, escolha a página do para-brisa com ↑/↓ e " +
                                                    "confirme com OK. Fora do relógio o card 0 fica com o carro, a " +
                                                    "última página volta ao ligar, e um aviso do carro sempre passa " +
                                                    "na frente. "
                                    )
                                }
                                append("Os mostradores, avisos e luzes continuam sendo os de fábrica.")
                            },
                            color = Cores.textoSecundario,
                            fontSize = 14.sp
                    )
                    if (painel.circulo) {
                        HorizontalDivider(color = Cores.trilhoDesligado)
                        ControlesDaProjecao(
                                titulo = "Projetar no círculo da direita",
                                descricaoParada =
                                        "Abre um círculo na central: arraste até enquadrar o que quer ver — mapa, " +
                                                "música, câmera — e toque em CONFIRMAR. A imagem aparece na página " +
                                                "Central do círculo da direita do painel, e o círculo vai para ela " +
                                                "sozinho. Depois, segurar OK do volante na página Central projeta " +
                                                "essa mesma área sem tocar na tela. Atalho: toque duplo com 4 dedos " +
                                                "em qualquer tela.",
                                projecaoAtiva = projecaoAtiva,
                                projecaoErro = projecaoErro
                        )
                        HorizontalDivider(color = Cores.trilhoDesligado)
                        EscolhaDoRelogioDoCirculo(prefs)
                    }
                }
            }
        }

        // ── APPS NO PAINEL ── some com o painel desligado, como já ficava apagado antes.
        if (allClusterFunctionsEnabled) {
        CartaoClassico(modifier = Modifier.padding(horizontal = 8.dp)) {
            Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column {
                    Text(
                            "Apps no painel",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                    )
                    Text(
                            "Atalhos para abrir apps no painel de instrumentos.",
                            color = Cores.textoSecundario,
                            fontSize = 14.sp
                    )
                }

                HorizontalDivider(color = Cores.trilhoDesligado)

                SecaoDeAppsNoPainel(prefs)
            }
        }
        }

        // ── HEAD-UP DISPLAY ──
        // Fica fora do painel de instrumentos de propósito: o HUD é outro display, com outro
        // caminho (shell do Shizuku, porque ele é FLAG_PRIVATE) e outro risco. Nenhum dos dois
        // liga ou exige o outro.
        TravaDoNivel(Recurso.HUD) {
        CartaoClassico(modifier = Modifier.padding(horizontal = 8.dp)) {
            Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                                "Head-up display (HUD)",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                        )
                        Text(
                                "Desenha no head-up display " +
                                        "(${HudOverlayController.HUD_WIDTH_PX}x${HudOverlayController.HUD_HEIGHT_PX}): " +
                                        "fundo preto, cinco páginas navegadas pelos direcionais do " +
                                        "volante e alertas do veículo por cima de tudo.",
                                color = Cores.textoSecundario,
                                fontSize = 14.sp
                        )
                    }
                    Switch(
                            checked = hudOverlayEnabled,
                            onCheckedChange = alternarHud,
                            colors =
                                    SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = Cores.destaque
                                    )
                    )
                }

                if (hudOverlayStatus.isNotEmpty()) {
                    Text(hudOverlayStatus, color = Cores.textoSecundario, fontSize = 12.sp)
                }

                // ── Tema do HUD (feito no instalador) ──
                // Os controles abaixo continuam valendo com um tema aplicado: ele grava nas mesmas
                // preferências, então mexer aqui ajusta o tema. "Voltar ao padrão" tira tudo.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                                "Tema do HUD — ${HudSettings.nomeDoTema ?: "padrão"}",
                                color = Color.White,
                                fontSize = 14.sp
                        )
                        Text(
                                "Crie e aplique temas no instalador do computador: Criação de " +
                                        "temas → Heads-Up Display. Cores, fonte, o que cada página " +
                                        "mostra e a ordem delas.",
                                color = Cores.textoSecundario,
                                fontSize = 11.sp
                        )
                    }
                    if (HudSettings.nomeDoTema != null) {
                        TextButton(onClick = { HudSettings.restaurarPadrao() }) {
                            Text("Voltar ao padrão", color = Cores.destaque, fontSize = 12.sp)
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // ── Velocidade ──
                Text(
                        "Velocidade — tamanho ${HudSettings.speedSizePct}% da altura",
                        color = Color.White,
                        fontSize = 14.sp
                )
                Slider(
                        value = HudSettings.speedSizePct.toFloat(),
                        onValueChange = { HudSettings.ajustarTamanhoVelocidade(it.toInt()) },
                        valueRange =
                                HudSettings.SPEED_SIZE_MIN.toFloat()..
                                        HudSettings.SPEED_SIZE_MAX.toFloat(),
                        colors =
                                SliderDefaults.colors(
                                        thumbColor = Cores.destaque,
                                        activeTrackColor = Cores.destaque,
                                        // Sem isto a trilha inativa sai no rosa padrão do Material.
                                        inactiveTrackColor = Cores.trilhoDesligado
                                )
                )

                Text(
                        "Cor da velocidade — ${HudSettings.descreverCor()}",
                        color = Color.White,
                        fontSize = 14.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Os três atalhos que o relatório pediu. O campo ao lado aceita qualquer RGB.
                    listOf(
                                    "Vermelho" to HudSettings.PRESET_VERMELHO,
                                    "Branco" to HudSettings.PRESET_BRANCO,
                                    "Azul" to HudSettings.PRESET_AZUL
                            )
                            .forEach { (nome, valor) ->
                                val selecionado = HudSettings.speedColor == valor
                                Box(
                                        modifier =
                                                Modifier.size(40.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(valor))
                                                        .border(
                                                                width = if (selecionado) 3.dp else 1.dp,
                                                                color =
                                                                        if (selecionado) Cores.destaque
                                                                        else Color.White.copy(alpha = 0.25f),
                                                                shape = CircleShape
                                                        )
                                                        .clickable { HudSettings.ajustarCorVelocidade(valor) }
                                )
                                Unit
                            }
                    // Cor livre. Os três atalhos acima resolvem o caso comum; este abre o mesmo
                    // seletor HSV que o painel usa, para quem quer um tom que não está na lista.
                    BotaoDeCorLivreDoHud(
                            corAtual = HudSettings.speedColor,
                            tamanho = 40.dp,
                            onClick = { hudSeletorDeCorAberto = true }
                    )
                    OutlinedTextField(
                            value = hudCorHex,
                            onValueChange = { texto ->
                                hudCorHex = texto.take(7)
                                // Só aplica quando o texto já é um RGB completo; enquanto digita,
                                // nada muda no HUD.
                                val limpo = hudCorHex.removePrefix("#")
                                if (limpo.length == 6) {
                                    limpo.toIntOrNull(16)?.let { HudSettings.ajustarCorVelocidade(it) }
                                }
                            },
                            singleLine = true,
                            label = { Text("#RRGGBB", fontSize = 11.sp) },
                            modifier = Modifier.width(140.dp)
                    )
                }

                if (hudSeletorDeCorAberto) {
                    // A cor de ANTES, congelada na abertura do seletor.
                    //
                    // `HudSettings.speedColor` não serve aqui: o `onPreview` abaixo escreve nele
                    // enquanto o dedo arrasta, e ler o valor vivo faria `initialHex` perseguir a
                    // prévia — "Cancelar" restauraria a última cor prevista, que é justamente a
                    // que o usuário está desistindo de aplicar. Este `remember` mora dentro do
                    // `if`, então ele nasce ao abrir o seletor e morre ao fechá-lo.
                    val corAntesDaPrevia = remember { HudSettings.speedColor }
                    SeletorDeCorLivre(
                            corInicial = hexDeArgb(corAntesDaPrevia),
                            paleta = CORES_DE_PAGINA_DO_HUD.map { hexDeArgb(it.second) },
                            aoFechar = { hudSeletorDeCorAberto = false },
                            aoEscolher = { hex ->
                                argbDeHex(hex)?.let { HudSettings.ajustarCorVelocidade(it) }
                                hudCorHex = hex
                                hudSeletorDeCorAberto = false
                            },
                            // O HUD está desenhando agora: empurrar a cor enquanto o dedo arrasta
                            // faz dele a prévia. Cancelar chama isto de volta com a cor original.
                            aoPrever = { hex ->
                                argbDeHex(hex)?.let { HudSettings.ajustarCorVelocidade(it) }
                            }
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // ── Espessura da fonte ──
                //
                // O desenho do HUD usava `Typeface.BOLD` fixo (peso 700) em tudo. No para-brisa,
                // com o sol a favor, isso engorda os traços e o número da velocidade vira um
                // borrão claro. Este controle deixa escolher; 700 continua o padrão, então quem
                // não mexer aqui não vê diferença nenhuma depois de atualizar.
                Text(
                        "Espessura da fonte — ${HudSettings.descreverPeso()} (${HudSettings.pesoDaFonte})",
                        color = Color.White,
                        fontSize = 14.sp
                )
                Slider(
                        value = HudSettings.pesoDaFonte.toFloat(),
                        onValueChange = { HudSettings.ajustarPesoDaFonte(it.toInt()) },
                        valueRange = HudSettings.PESO_MIN.toFloat()..HudSettings.PESO_MAX.toFloat(),
                        // Oito paradas (200..900 de cem em cem) = seis intermediárias. Contínuo
                        // não serviria: a família instalada só existe nesses pesos, e 643 desenha
                        // igual a 700 — o rótulo estaria anunciando algo que a fonte ignorou.
                        steps = (HudSettings.PESO_MAX - HudSettings.PESO_MIN) / HudSettings.PESO_PASSO - 1,
                        colors =
                                SliderDefaults.colors(
                                        thumbColor = Cores.destaque,
                                        activeTrackColor = Cores.destaque,
                                        inactiveTrackColor = Cores.trilhoDesligado
                                )
                )
                Text(
                        "Vale para tudo o que o HUD escreve, em todas as páginas. A cortina de " +
                                "alerta é a exceção e fica sempre grossa: ela é o único conteúdo " +
                                "que o motorista não escolheu ver, e um aviso fino some no vidro.",
                        color = Cores.textoSecundario,
                        fontSize = 11.sp
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // ── SLI ──
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Limite da via (SLI)", color = Color.White, fontSize = 14.sp)
                        Text(
                                "Placa com o limite lido pela câmera/navegação. Some sozinha " +
                                        "quando o carro perde a placa — em vez de deixar o último " +
                                        "número velho na tela.",
                                color = Cores.textoSecundario,
                                fontSize = 11.sp
                        )
                    }
                    Switch(
                            checked = HudSettings.showSli,
                            onCheckedChange = { HudSettings.ajustarMostrarSli(it) },
                            colors =
                                    SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = Cores.destaque
                                    )
                    )
                }
                if (HudSettings.showSli) {
                    Text(
                            "Tamanho da placa — ${HudSettings.sliSizePct}% da altura",
                            color = Cores.textoSecundario,
                            fontSize = 12.sp
                    )
                    Slider(
                            value = HudSettings.sliSizePct.toFloat(),
                            onValueChange = { HudSettings.ajustarTamanhoSli(it.toInt()) },
                            valueRange =
                                    HudSettings.SLI_SIZE_MIN.toFloat()..
                                            HudSettings.SLI_SIZE_MAX.toFloat(),
                            colors =
                                    SliderDefaults.colors(
                                            thumbColor = Cores.destaque,
                                            activeTrackColor = Cores.destaque,
                                            // Sem isto a trilha inativa sai no rosa padrão do Material.
                                            inactiveTrackColor = Cores.trilhoDesligado
                                    )
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Alertas do veículo", color = Color.White, fontSize = 14.sp)
                        Text(
                                "Faixa vermelha embaixo quando houver porta aberta (dizendo QUAL), " +
                                        "cinto, pressão de pneu, temperatura, óleo, freio, airbag " +
                                        "ou combustível baixo.",
                                color = Cores.textoSecundario,
                                fontSize = 11.sp
                        )
                    }
                    Switch(
                            checked = HudSettings.showAlerts,
                            onCheckedChange = { HudSettings.ajustarMostrarAlertas(it) },
                            colors =
                                    SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = Cores.destaque
                                    )
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // ── Modo noturno (Alpha 9.5) ──
                Text("Modo noturno", color = Color.White, fontSize = 14.sp)
                Text(
                        "Escurece o HUD inteiro à noite, para não ofuscar. \"Com o farol\" segue a " +
                                "mesma regra do modo noturno dos temas do painel.",
                        color = Cores.textoSecundario,
                        fontSize = 11.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    br.com.deepgwt.hud.HudModoNoturno.entries.forEach { modo ->
                        val escolhido = HudSettings.modoNoturno == modo
                        OutlinedButton(
                                onClick = { HudSettings.ajustarModoNoturno(modo) },
                                border = BorderStroke(1.dp, if (escolhido) Cores.destaque else Color.White.copy(alpha = 0.16f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (escolhido) Cores.destaque.copy(alpha = 0.18f) else Color.Transparent,
                                        contentColor = Color.White
                                )
                        ) { Text(modo.rotulo, fontSize = 13.sp) }
                    }
                }
                if (HudSettings.modoNoturno != br.com.deepgwt.hud.HudModoNoturno.DESLIGADO) {
                    Text(
                            "Brilho à noite — ${HudSettings.brilhoNoturno}% do de dia",
                            color = Cores.textoSecundario,
                            fontSize = 12.sp
                    )
                    Slider(
                            value = HudSettings.brilhoNoturno.toFloat(),
                            onValueChange = { HudSettings.ajustarBrilhoNoturno(it.toInt()) },
                            valueRange =
                                    HudSettings.BRILHO_NOTURNO_MIN.toFloat()..
                                            HudSettings.BRILHO_NOTURNO_MAX.toFloat(),
                            colors =
                                    SliderDefaults.colors(
                                            thumbColor = Cores.destaque,
                                            activeTrackColor = Cores.destaque,
                                            inactiveTrackColor = Cores.trilhoDesligado
                                    )
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Tons âmbar", color = Color.White, fontSize = 14.sp)
                            Text(
                                    "Puxa o branco para o âmbar, que cansa menos o olho no escuro.",
                                    color = Cores.textoSecundario,
                                    fontSize = 11.sp
                            )
                        }
                        Switch(
                                checked = HudSettings.noturnoAmbar,
                                onCheckedChange = { HudSettings.ajustarNoturnoAmbar(it) },
                                colors =
                                        SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = Cores.destaque
                                        )
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // ── Navegação pelo volante ──
                // Painel de fábrica com o círculo: o HUD só muda pela página HUD dele
                // (circulo/CirculoDoPainelController.cuidaDoHud).
                // Alpha 9.5: segurar OK entra no "Controle do HUD" (hud/HudWheelInput.kt) em qualquer
                // painel; fora dele as setas são do painel, como de fábrica.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Controles pelo volante", color = Color.White, fontSize = 14.sp)
                        Text(
                                "Segure OK no volante: o painel é coberto com \"Controle do HUD " +
                                        "ativado\" e ↑/↓ trocam as páginas do HUD. Segure OK de novo " +
                                        "para as setas voltarem ao painel. Desligado, o HUD fica " +
                                        "na página escolhida aqui embaixo.",
                                color = Cores.textoSecundario,
                                fontSize = 11.sp
                        )
                    }
                    Switch(
                            checked = HudSettings.wheelControlEnabled,
                            onCheckedChange = { HudSettings.ajustarControleVolante(it) },
                            colors =
                                    SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = Cores.destaque
                                    )
                    )
                }
                if (HudSettings.wheelControlEnabled) {
                    Text(
                            "Com o HUD no ar, segurar OK passa a ser deste controle (o modo " +
                                    "mãos-livres segue no OK segurado com o HUD desligado). No " +
                                    "Círculo do painel, a página Central continua projetando com " +
                                    "o OK segurado. O controle sai sozinho depois de 20 s sem " +
                                    "tecla ou quando o carro mostrar um aviso.",
                            color = Color(0xFFFFB300),
                            fontSize = 11.sp
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                HudPaginasSection()

                // O que NÃO está verificado. O HUD é ótico e não sai em `screencap`, então não há
                // como este app confirmar sozinho o que apareceu lá.
                Text(
                        "Desenhar no HUD já foi confirmado no carro. O que não está verificado é se " +
                                "esta camada compõe SOBRE a projeção nativa do HUD ou se toma o " +
                                "lugar dela enquanto está ligada. Ligue com o carro parado, olhe o " +
                                "HUD e desligue aqui mesmo se ela cobrir o que o carro projeta.",
                        color = Cores.textoSecundario,
                        fontSize = 11.sp
                )
            }
        }
        }
    }

    if (showVirtualClusterWarningDialog) {
        AvisoDoTemaCustomizado(
                aoAceitar = {
                    aplicarPainel(painel.copy(temaCustomizado = true))
                    showVirtualClusterWarningDialog = false
                },
                aoRecusar = { showVirtualClusterWarningDialog = false }
        )
    }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

/**
 * Um dos interruptores do painel de instrumentos. Independentes entre si — o interruptor no canto
 * diz isso sem texto — e o cartão inteiro é o alvo do toque, do tamanho de um dedo com o carro
 * andando. [habilitado] falso apaga o cartão sem perder o estado guardado (ver PainelEscolhido).
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun InterruptorDoPainel(
        icone: ImageVector,
        titulo: String,
        descricao: String,
        ligado: Boolean,
        modifier: Modifier = Modifier,
        habilitado: Boolean = true,
        /** Id do ajuste no catálogo: apertar e segurar oferece o atalho no launcher. */
        ajusteId: String? = null,
        onChange: (Boolean) -> Unit
) {
    val forma = RoundedCornerShape(12.dp)
    var dialogoDoAtalho by remember { mutableStateOf(false) }
    if (dialogoDoAtalho && ajusteId != null) br.com.deepgwt.ui.DialogoDoAtalho(ajusteId) { dialogoDoAtalho = false }
    val comAtalho = br.com.deepgwt.ajustes.AtalhosDeAjuste.podeVirarAtalho(ajusteId)
    Column(
            modifier =
                    modifier.clip(forma)
                            .alpha(if (habilitado) 1f else 0.45f)
                            .background(
                                    if (ligado) Cores.destaque.copy(alpha = 0.16f)
                                    else Cores.trilhoDesligado
                            )
                            .border(
                                    width = if (ligado) 2.dp else 1.dp,
                                    color =
                                            if (ligado) Cores.destaque
                                            else Color.White.copy(alpha = 0.08f),
                                    shape = forma
                            )
                            .combinedClickable(
                                    enabled = habilitado,
                                    onClick = { onChange(!ligado) },
                                    onLongClick = if (comAtalho) ({ dialogoDoAtalho = true }) else null
                            )
                            .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                    imageVector = icone,
                    contentDescription = null,
                    tint = if (ligado) Cores.destaque else Cores.textoSecundario,
                    modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.weight(1f))
            Switch(
                    checked = ligado,
                    onCheckedChange = null,
                    enabled = habilitado,
                    colors =
                            SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Cores.destaque
                            )
            )
        }
        Text(
                titulo,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
        )
        Text(descricao, color = Cores.textoSecundario, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

/**
 * Escolher a área da central e projetá-la no painel — o mesmo controle nos dois lugares em que a
 * projeção tem destino: o vão do tema customizado e o círculo do painel de fábrica. Para onde ela
 * vai quem decide é o ScreenProjectionController, pelo que está ligado; aqui só muda o texto.
 *
 * O estado vem de fora, lido direto do controlador pelo chamador: a projeção muda por fora desta
 * tela (o gesto de 4 dedos, o botão flutuante, a própria central encerrando a captura).
 */
@Composable
private fun ControlesDaProjecao(
        titulo: String,
        descricaoParada: String,
        projecaoAtiva: Boolean,
        projecaoErro: String?
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
                titulo,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
        )
        Text(
                if (projecaoAtiva) {
                    "Projetando. O botão flutuante na central encerra — ou repita o toque duplo com 4 dedos."
                } else {
                    descricaoParada
                },
                color = Cores.textoSecundario,
                fontSize = 13.sp
        )
        projecaoErro?.let { erro ->
            Text(erro, color = Color(0xFFFFC107), fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                    onClick = {
                        if (projecaoAtiva) {
                            ScreenProjectionController.parar("pedido em Telas")
                        } else {
                            ScreenProjectionController.abrirSelecao()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                            containerColor = if (projecaoAtiva) Color(0xFF8E2C2C) else Cores.destaque
                    )
            ) {
                Text(if (projecaoAtiva) "Encerrar projeção" else "Escolher área")
            }
            if (!projecaoAtiva && ScreenProjectionController.ultimaRegiao() != null) {
                OutlinedButton(onClick = { ScreenProjectionController.reprojetarUltimaArea() }) {
                    Text("Repetir a última área")
                }
            }
        }
    }
}

/**
 * Os relógios da página Relógio do círculo (Alpha 7.7; 14 desde a 7.8), desenhados de verdade — a mesma
 * [br.com.deepgwt.circulo.RelogioAnalogicoView] do painel. Tocar escolhe; no
 * carro, ← e → do volante na página Relógio fazem o mesmo, e gravam a mesma preferência — por isso
 * a escolha aqui acompanha o volante.
 */
@Composable
private fun EscolhaDoRelogioDoCirculo(prefs: SharedPreferences) {
    val chave = Preferencias.ORIGINAL_PANEL_CIRCLE_CLOCK.chave
    val relogios = br.com.deepgwt.circulo.RelogiosDoCirculo
    var escolhido by remember { mutableStateOf(relogios.indiceDe(prefs.getString(chave, null))) }
    DisposableEffect(prefs) {
        val ouvinte = SharedPreferences.OnSharedPreferenceChangeListener { p, k ->
            if (k == chave) escolhido = relogios.indiceDe(p.getString(k, null))
        }
        prefs.registerOnSharedPreferenceChangeListener(ouvinte)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(ouvinte) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Relógio do círculo", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(
                "Vá com ↑/↓ até a página Relógio; ← e → do volante trocam entre os dez. Tocar " +
                        "aqui escolhe direto.",
                color = Cores.textoSecundario,
                fontSize = 13.sp
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(relogios.TODOS, key = { _, r -> r.id }) { i, relogio ->
                val selecionado = i == escolhido
                Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier =
                                Modifier.width(116.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                                if (selecionado) Cores.destaque.copy(alpha = 0.16f)
                                                else Cores.conteiner
                                        )
                                        .border(
                                                width = 2.dp,
                                                color = if (selecionado) Cores.destaque else Color.Transparent,
                                                shape = RoundedCornerShape(16.dp)
                                        )
                                        .clickable {
                                            escolhido = i
                                            prefs.edit { putString(chave, relogio.id) }
                                        }
                                        .padding(10.dp)
                ) {
                    AndroidView(
                            modifier = Modifier.size(96.dp),
                            factory = { ctx ->
                                br.com.deepgwt.circulo.RelogioAnalogicoView(ctx, margemPx = 1f).apply {
                                    definirTema(i, anunciar = false)
                                    rodando = true
                                }
                            }
                    )
                    Text(
                            relogio.nome,
                            color = if (selecionado) Color.White else Cores.textoSecundario,
                            fontSize = 13.sp,
                            fontWeight = if (selecionado) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                    )
                }
            }
        }
    }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

// ─────────────────────────────────────────────────────────────────────────────────────────────
// PÁGINAS DO HUD: QUAIS, EM QUE ORDEM E COM QUE CARA
//
// POR QUE ESTA SEÇÃO EXISTE
//
// O volante só sabe "anterior" e "próxima". Quem decide o que ENTRA nesse rodízio, em que ordem e
// como cada página se parece é esta tela — e ela é o único caminho quando os controles pelo
// volante estão desligados, porque aí o HUD fica na página que for escolhida aqui.
//
// O QUE É POR PÁGINA E O QUE É GLOBAL
//
// Por página: entra ou não no rodízio, a posição na ordem, a cor do valor principal e a escala do
// conteúdo. Global (e com controle próprio mais acima nesta mesma tela): o tamanho e a cor da
// VELOCIDADE, o tamanho da placa de limite, os alertas. Velocidade não ganha escala nem cor aqui
// justamente para não haver dois controles disputando a mesma coisa.
// ─────────────────────────────────────────────────────────────────────────────────────────────
@Composable
private fun HudPaginasSection() {
    // A leitura das preferências acontece AQUI, na composição, e não num `LaunchedEffect`: este
    // bloco decide a ordem das linhas, e um efeito só roda depois que a primeira composição já
    // desenhou. Com a leitura atrasada a lista aparecia por um quadro na ordem de fábrica e só
    // então na ordem gravada. `load` é guardada contra releitura, então isto custa uma leitura de
    // arquivo na primeira vez que qualquer ajuste do HUD é tocado no processo, e nada depois.
    remember { HudSettings.load(); true }

    // A página no ar é `@Volatile`, não estado do Compose: o volante pode trocá-la enquanto esta
    // tela está aberta e o Compose não saberia. O espelho local é atualizado quando esta tela age
    // e a cada entrada na seção, que é o suficiente — ninguém mexe no volante olhando a central.
    var paginaNoAr by remember { mutableStateOf(br.com.deepgwt.hud.HudPaginas.pagina) }
    var expandida by remember {
        mutableStateOf<br.com.deepgwt.hud.HudPaginas.Pagina?>(null)
    }
    // Qual página está com o seletor de cor livre aberto. Um estado só para as cinco: só existe
    // uma linha expandida por vez, então dois seletores nunca disputam a tela.
    var seletorDeCor by remember {
        mutableStateOf<br.com.deepgwt.hud.HudPaginas.Pagina?>(null)
    }
    LaunchedEffect(Unit) { paginaNoAr = br.com.deepgwt.hud.HudPaginas.pagina }

    // AS MINIATURAS PRECISAM DA TELEMETRIA LIGADA
    //
    // Elas são o mesmo desenho do HUD, então leem os mesmos números do carro. Com a camada do HUD
    // desligada ninguém tinha pedido essas chaves ao ControlService, e as prévias sairiam com
    // traços em tudo. `start`/`stop` contam interessados (ver HudTelemetry), então ligar aqui não
    // atrapalha o HUD que estiver no ar, e sair desta tela não apaga a telemetria dele.
    DisposableEffect(Unit) {
        br.com.deepgwt.hud.HudTelemetry.start()
        onDispose { br.com.deepgwt.hud.HudTelemetry.stop() }
    }

    // O mesmo pulso de 1 s da Activity do HUD, pela mesma razão: o desenho é orientado a evento,
    // mas um `AndroidView` não recebe os eventos de telemetria. Um contador que anda de segundo em
    // segundo entra na assinatura das miniaturas e as repinta com o número novo.
    var pulso by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            pulso++
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Páginas do HUD", color = Color.White, fontSize = 14.sp)
            Text(
                    "Cada miniatura é o desenho de verdade daquela página, com os números do " +
                            "carro agora. Toque nela para mandá-la ao HUD. As setas mudam a ordem " +
                            "em que o volante anda pelas páginas, e o interruptor tira a página do " +
                            "rodízio — a última ativa não pode ser desligada.",
                    color = Cores.textoSecundario,
                    fontSize = 11.sp
            )
        }
        TextButton(onClick = {
            HudSettings.restaurarPaginas()
            paginaNoAr = br.com.deepgwt.hud.HudPaginas.pagina
            expandida = null
            seletorDeCor = null
        }) { Text("Restaurar", color = Cores.destaque, fontSize = 12.sp) }
    }

    val ordem = HudSettings.ordemPaginas
    ordem.forEachIndexed { indice, pagina ->
        // `key(pagina)`: a identidade de uma linha é a PÁGINA dela, não a posição na lista. Sem
        // isto, reordenar faz o Compose reaproveitar o slot — e a miniatura, que é uma view do
        // Android criada uma vez só, fica desenhando a página do ocupante anterior ao lado do nome
        // do novo. Foi exatamente o que aconteceu: "Veículo" com a prévia de Powertrain e
        // vice-versa, porque a ordem gravada só chega depois da primeira composição.
        key(pagina) {
        LinhaDePaginaDoHud(
                pagina = pagina,
                indice = indice,
                ultimo = indice == ordem.lastIndex,
                noAr = paginaNoAr == pagina,
                expandida = expandida == pagina,
                pulso = pulso,
                onMostrar = {
                    br.com.deepgwt.hud.HudPaginas.irPara(pagina)
                    paginaNoAr = pagina
                },
                onExpandir = { expandida = if (expandida == pagina) null else pagina },
                onMover = { passo ->
                    HudSettings.moverPagina(pagina, passo)
                    paginaNoAr = br.com.deepgwt.hud.HudPaginas.pagina
                },
                onAtiva = { ativa ->
                    HudSettings.ajustarPaginaAtiva(pagina, ativa)
                    paginaNoAr = br.com.deepgwt.hud.HudPaginas.pagina
                },
                onCorLivre = { seletorDeCor = pagina }
        )
        }
    }

    seletorDeCor?.let { alvo ->
        // A cor de ANTES, congelada na abertura. Ler `corDaPagina` viva faria `initialHex`
        // perseguir a prévia que o `onPreview` acabou de gravar, e "Cancelar" restauraria a cor
        // que o usuário está desistindo de aplicar. A chave é a página: trocar de linha recaptura.
        val corAntesDaPrevia = remember(alvo) { HudSettings.corDaPagina(alvo) }
        SeletorDeCorLivre(
                corInicial = hexDeArgb(corAntesDaPrevia),
                paleta = CORES_DE_PAGINA_DO_HUD.map { hexDeArgb(it.second) },
                aoFechar = { seletorDeCor = null },
                aoEscolher = { hex ->
                    argbDeHex(hex)?.let { HudSettings.ajustarCorDaPagina(alvo, it) }
                    seletorDeCor = null
                },
                // A miniatura desta linha está na tela e o HUD pode estar no ar: os dois seguem a
                // cor enquanto o dedo arrasta. Cancelar chama isto de volta com a cor original.
                aoPrever = { hex -> argbDeHex(hex)?.let { HudSettings.ajustarCorDaPagina(alvo, it) } }
        )
    }
}

@Composable
private fun LinhaDePaginaDoHud(
        pagina: br.com.deepgwt.hud.HudPaginas.Pagina,
        indice: Int,
        ultimo: Boolean,
        noAr: Boolean,
        expandida: Boolean,
        pulso: Int,
        onMostrar: () -> Unit,
        onExpandir: () -> Unit,
        onMover: (Int) -> Unit,
        onAtiva: (Boolean) -> Unit,
        onCorLivre: () -> Unit
) {
    val velocidade = pagina == br.com.deepgwt.hud.HudPaginas.Pagina.VELOCIDADE
    val ativa = pagina in HudSettings.paginasAtivas
    Column(
            modifier =
                    Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                    if (noAr) Cores.destaque.copy(alpha = 0.12f)
                                    else Color.White.copy(alpha = 0.04f)
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            // Reordenar. Nas pontas o botão fica cinza em vez de sumir: um botão que desaparece
            // move os outros de lugar entre um toque e o seguinte.
            Column {
                IconButton(onClick = { onMover(-1) }, enabled = indice > 0, modifier = Modifier.size(26.dp)) {
                    Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = "Subir ${pagina.titulo}",
                            tint = if (indice > 0) Color.White else Cores.textoSecundario,
                            modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = { onMover(1) }, enabled = !ultimo, modifier = Modifier.size(26.dp)) {
                    Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Descer ${pagina.titulo}",
                            tint = if (!ultimo) Color.White else Cores.textoSecundario,
                            modifier = Modifier.size(18.dp)
                    )
                }
            }

            // A prévia. Tocar nela manda a página para o HUD — é o mesmo gesto do nome ao lado,
            // mas com o alvo que o usuário está olhando enquanto decide.
            PreviaDaPaginaDoHud(
                    pagina = pagina,
                    pulso = pulso,
                    modifier =
                            Modifier.padding(start = 6.dp)
                                    .width(112.dp)
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(
                                            width = if (noAr) 2.dp else 1.dp,
                                            color =
                                                    if (noAr) Cores.destaque
                                                    else Color.White.copy(alpha = 0.18f),
                                            shape = RoundedCornerShape(6.dp)
                                    )
                                    .alpha(if (ativa) 1f else 0.45f)
                                    .clickable { onMostrar() }
            )

            Column(
                    modifier =
                            Modifier.weight(1f)
                                    .padding(start = 10.dp)
                                    .clickable { onMostrar() }
            ) {
                Text(
                        HudSettings.tituloDaPagina(pagina),
                        color = if (ativa) Color.White else Cores.textoSecundario,
                        fontSize = 14.sp,
                        fontWeight = if (noAr) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                        when {
                            noAr && !ativa -> "no HUD agora · fora do rodízio do volante"
                            noAr -> "no HUD agora"
                            !ativa -> "fora do rodízio do volante"
                            else -> "toque para mostrar no HUD"
                        },
                        color = if (noAr) Cores.destaque else Cores.textoSecundario,
                        fontSize = 10.sp
                )
            }
            IconButton(onClick = onExpandir, modifier = Modifier.size(30.dp)) {
                Icon(
                        if (expandida) Icons.Default.ExpandLess else Icons.Default.Tune,
                        contentDescription = "Personalizar ${pagina.titulo}",
                        tint = Cores.textoSecundario,
                        modifier = Modifier.size(18.dp)
                )
            }
            Switch(
                    checked = ativa,
                    // Desligar a última ativa deixaria o HUD sem nada para desenhar e sem caminho
                    // de volta pelo volante. O interruptor fica travado em vez de recusar calado.
                    enabled = !ativa || HudSettings.podeDesativar(pagina),
                    onCheckedChange = onAtiva,
                    colors =
                            SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Cores.destaque
                            )
            )
        }

        if (expandida) {
            HorizontalDivider(
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.padding(vertical = 6.dp)
            )
            if (velocidade) {
                Text(
                        "A cor e o tamanho da velocidade têm controles próprios acima nesta tela " +
                                "(número, placa de limite e espessura da fonte). Não há um segundo " +
                                "par aqui para não existirem dois ajustes disputando a mesma coisa " +
                                "— a miniatura ao lado já responde aos de lá.",
                        color = Cores.textoSecundario,
                        fontSize = 11.sp
                )
            } else {
                // A prévia grande. A pequena da linha mostra qual página é; esta mostra o que a
                // cor e o tamanho fizeram com ela, que é a pergunta de quem abriu este painel.
                PreviaDaPaginaDoHud(
                        pagina = pagina,
                        pulso = pulso,
                        modifier =
                                Modifier.fillMaxWidth()
                                        .aspectRatio(2f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                                1.dp,
                                                Color.White.copy(alpha = 0.12f),
                                                RoundedCornerShape(8.dp)
                                        )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                        "Cor do valor principal — ${HudSettings.descreverCor(HudSettings.corDaPagina(pagina))}",
                        color = Cores.textoSecundario,
                        fontSize = 12.sp
                )
                Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    CORES_DE_PAGINA_DO_HUD.forEach { (nome, valor) ->
                        val selecionado = HudSettings.corDaPagina(pagina) == valor
                        Box(
                                modifier =
                                        Modifier.size(30.dp)
                                                .clip(CircleShape)
                                                .background(Color(valor))
                                                .border(
                                                        width = if (selecionado) 3.dp else 1.dp,
                                                        color =
                                                                if (selecionado) Cores.destaque
                                                                else Color.White.copy(alpha = 0.25f),
                                                        shape = CircleShape
                                                )
                                                .clickable { HudSettings.ajustarCorDaPagina(pagina, valor) },
                                contentAlignment = Alignment.Center
                        ) {
                            if (selecionado) {
                                Icon(
                                        Icons.Default.Check,
                                        contentDescription = nome,
                                        tint = Color.Black,
                                        modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                    // Qualquer outro tom. A paleta acima é o caminho rápido, não o limite.
                    BotaoDeCorLivreDoHud(
                            corAtual = HudSettings.corDaPagina(pagina),
                            tamanho = 30.dp,
                            onClick = onCorLivre
                    )
                }
                Text(
                        "Tamanho do conteúdo — ${HudSettings.escalaDaPaginaPct(pagina)}%",
                        color = Cores.textoSecundario,
                        fontSize = 12.sp
                )
                Slider(
                        value = HudSettings.escalaDaPaginaPct(pagina).toFloat(),
                        onValueChange = { HudSettings.ajustarEscalaDaPagina(pagina, it.toInt()) },
                        valueRange = HudSettings.ESCALA_MIN.toFloat()..HudSettings.ESCALA_MAX.toFloat(),
                        colors =
                                SliderDefaults.colors(
                                        thumbColor = Cores.destaque,
                                        activeTrackColor = Cores.destaque,
                                        // Sem isto a trilha inativa sai no rosa padrão do Material.
                                        inactiveTrackColor = Cores.trilhoDesligado
                                )
                )
                Text(
                        "Aumentar não faz texto vazar para fora do HUD: ele cresce até bater na " +
                                "largura da coluna e para ali.",
                        color = Cores.textoSecundario,
                        fontSize = 10.sp
                )
            }
        }
    }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

/**
 * A miniatura de uma página do HUD.
 *
 * É O DESENHO DE VERDADE, NÃO UM MOCKUP
 *
 * Dentro do `AndroidView` está a mesma [br.com.deepgwt.hud.HudOverlayView] que
 * desenha no HUD físico, só que amarrada a uma página ([paginaFixa] do construtor). O tamanho não
 * importa: todo corpo de letra lá dentro é derivado da altura REAL da view, então o desenho de
 * 480x240 encolhe para 112x56 sem nenhum ajuste. Uma segunda implementação "só para a prévia"
 * seria uma segunda verdade sobre como a página se parece, e as duas divergiriam no primeiro
 * ajuste que alguém esquecesse de copiar.
 *
 * COMO ELA SABE QUE PRECISA REPINTAR
 *
 * Um `AndroidView` não é recomposto pelo Compose — ele é uma view do Android dentro da árvore. O
 * bloco `update` roda de novo quando um estado LIDO DENTRO DELE muda, então a lista `ajustes` não
 * é decoração: é a inscrição desta miniatura em cada ajuste que muda o desenho. Comparar a lista
 * com a anterior evita repintar as cinco prévias a cada recomposição por outro motivo qualquer.
 */
@Composable
private fun PreviaDaPaginaDoHud(
        pagina: br.com.deepgwt.hud.HudPaginas.Pagina,
        pulso: Int,
        modifier: Modifier = Modifier
) {
    // A página entra no construtor da view e o `factory` roda UMA vez por slot de composição. Um
    // `key` aqui é o que garante que trocar a página de um slot construa uma view nova em vez de
    // pintar a página antiga com o rótulo novo — a proteção fica junto do perigo, então a prévia
    // continua correta em qualquer lugar onde alguém venha a usá-la.
    key(pagina) {
    AndroidView(
            modifier = modifier,
            factory = { ctx ->
                br.com.deepgwt.hud.HudOverlayView(ctx, pagina)
            },
            update = { view ->
                val ajustes =
                        listOf(
                                pulso,
                                HudSettings.pesoDaFonte,
                                HudSettings.corDaPagina(pagina),
                                HudSettings.escalaDaPaginaPct(pagina),
                                HudSettings.speedSizePct,
                                HudSettings.sliSizePct,
                                if (HudSettings.showSli) 1 else 0,
                                // A fileira de pontinhos desenha o rodízio: tirar uma página dele
                                // ou reordenar muda o que TODAS as miniaturas mostram embaixo.
                                HudSettings.paginasAtivas.size,
                                HudSettings.ordemPaginas.indexOf(pagina),
                                // O que só o tema do HUD muda (Alpha 5.9).
                                HudSettings.corRotulo,
                                HudSettings.fonte.ordinal,
                                if (HudSettings.mostrarCabecalho) 1 else 0,
                                if (HudSettings.mostrarPontos) 1 else 0,
                                if (HudSettings.unidadeVelocidade) 1 else 0,
                                if (HudSettings.placaAEsquerda) 1 else 0,
                                HudSettings.layoutDaPagina(pagina).hashCode(),
                                HudSettings.tituloDaPagina(pagina).hashCode(),
                                // Modo noturno (Alpha 9.5): as miniaturas escurecem junto.
                                HudSettings.modoNoturno.ordinal,
                                HudSettings.brilhoNoturno,
                                if (HudSettings.noturnoAmbar) 1 else 0
                        )
                if (view.ajustesDesenhados != ajustes) {
                    view.ajustesDesenhados = ajustes
                    view.invalidate()
                }
            }
    )
    }
}

/**
 * O botão que abre o seletor de cor livre, desenhado com a cor que está valendo.
 *
 * Fica ao lado dos atalhos de cor e tem a mesma forma deles de propósito: é mais um tom da mesma
 * fileira, e não um controle de outra categoria. O conta-gotas por cima diz que este não é um tom
 * fixo — é o caminho para qualquer um.
 */
@Composable
private fun BotaoDeCorLivreDoHud(corAtual: Int, tamanho: Dp, onClick: () -> Unit) {
    Box(
            modifier =
                    Modifier.size(tamanho)
                            .clip(CircleShape)
                            .background(Color(corAtual))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                            .clickable { onClick() },
            contentAlignment = Alignment.Center
    ) {
        Icon(
                Icons.Default.Colorize,
                contentDescription = "Cor personalizada",
                // O conta-gotas tem de aparecer sobre branco e sobre azul-escuro: a luminância da
                // cor de baixo decide se ele sai preto ou branco.
                tint = CoresDosAjustes.corLegivelSobre(corAtual),
                modifier = Modifier.size(tamanho * 0.5f)
        )
    }
}

/** `#RRGGBB` de um ARGB, que é o formato que o [SeletorDeCorLivre] fala. */
private fun hexDeArgb(argb: Int): String = String.format("#%06X", argb and 0xFFFFFF)

/**
 * ARGB opaco de um `#RRGGBB`, ou `null` quando o texto não é uma cor.
 *
 * `parseColor` ESTOURA em vez de devolver nulo, e o seletor devolve o texto que ele mesmo montou —
 * mas um `runCatching` aqui é o que garante que uma cor malformada vinda de qualquer caminho
 * futuro deixe o ajuste como estava em vez de derrubar a tela de Telas inteira.
 */
private fun argbDeHex(hex: String): Int? =
        runCatching { android.graphics.Color.parseColor(hex) or (0xFF shl 24) }.getOrNull()

/**
 * Paleta de cor por página.
 *
 * Só tons claros e frios, mais o âmbar: o HUD é ótico e projeta luz num para-brisa — um tom escuro
 * simplesmente não aparece, e o vermelho fica reservado para a cortina de alerta, que é o único
 * conteúdo que o motorista não escolheu ver. Quem quiser sair daqui tem o seletor livre ao lado
 * ([BotaoDeCorLivreDoHud]); esta lista é o caminho rápido, não o limite.
 */
private val CORES_DE_PAGINA_DO_HUD: List<Pair<String, Int>> =
        listOf(
                "Branco" to HudSettings.PRESET_BRANCO,
                "Azul" to HudSettings.PRESET_AZUL,
                "Ciano" to android.graphics.Color.rgb(64, 224, 208),
                "Verde" to android.graphics.Color.rgb(126, 217, 87),
                "Âmbar" to android.graphics.Color.rgb(255, 179, 0)
        )
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
