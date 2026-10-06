// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.onboarding

import br.com.deepgwt.ui.Fontes
import br.com.deepgwt.ui.Cores
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.ViewDay
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.deepgwt.BuildConfig
import br.com.deepgwt.ajustes.AjustesRemotos
import br.com.deepgwt.licenca.Avaliacao
import br.com.deepgwt.licenca.Licencas
import br.com.deepgwt.licenca.Nivel
import br.com.deepgwt.licenca.Portao
import br.com.deepgwt.licenca.Recurso
import br.com.deepgwt.ui.components.TravaDoNivel
import br.com.deepgwt.ui.screens.InserirLicencaDialogo
import br.com.deepgwt.nucleo.Preferencias
import br.com.deepgwt.barra.Barra
import br.com.deepgwt.ui.screens.CartaoDeEstilo
import br.com.deepgwt.ui.screens.ModoDoPainel
import br.com.deepgwt.ui.screens.PainelEscolhido
import br.com.deepgwt.ui.EscalaDaInterface
import br.com.deepgwt.ui.EstiloDaInterface
import br.com.deepgwt.ui.InterfaceDoDeepGwt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * O assistente de primeira abertura (Alpha 7.9): tamanho da interface, tamanho das letras,
 * estilo, painel e barra, um por tela, e no fim o convite para o tour.
 *
 * Cada escolha vale na hora e fica gravada — o assistente não tem um "salvar" no fim para
 * alguém perder. Tamanho e letras recriam a Activity (ver `EscalaDaInterface`); o passo em que
 * se está sobrevive porque é `rememberSaveable` e o assistente mora em [AberturaDoDeepGwt].
 * Painel e barra mexem em serviços, então só são aplicados ao avançar do passo deles, pelo mesmo
 * caminho que a tela de Configurações e o instalador usam.
 */
private enum class PassoDoAssistente(val titulo: String, val descricao: String) {
    BOAS_VINDAS(
        "Bem-vindo ao DeepGWT",
        "Vamos deixar o app do seu jeito: tamanho, letras, estilo, painel e barra. Leva um minuto, e tudo pode ser mudado depois."
    ),
    INTERFACE(
        "Tamanho da interface",
        "O tamanho de tudo nas telas do app: botões, cartões e ícones. Toque numa opção para ver na hora."
    ),
    LETRAS(
        "Tamanho das letras",
        "Só os textos. Letras maiores são mais fáceis de ler de relance."
    ),
    ESTILO(
        "Estilo",
        "O visual das telas do app. Não muda a barra inferior nem o painel de instrumentos."
    ),
    PAINEL(
        "Painel de instrumentos",
        "O que o DeepGWT mostra atrás do volante. O tema e os detalhes ficam em Telas."
    ),
    BARRA(
        "Barra inferior",
        "A barra do DeepGWT fica por cima das telas do carro, com ar-condicionado, volume e atalhos. A altura fica em Configurações."
    ),
    CONFIGURACAO(
        "Funções do carro",
        "Escolha como começar: com a predefinição recomendada ou do zero. Esta escolha é obrigatória, e dá para mudar qualquer função depois."
    ),
    LICENCA(
        "Nível e licença",
        "O DeepGWT tem quatro níveis: Essencial, Plus, Pro e Max. Sem licença ele fica no Essencial, o nível grátis; uma licença libera o nível dela pelas horas de uso compradas. Dá para testar o Max grátis e para inserir a licença depois, em Informações."
    ),
    PRONTO(
        "Tudo pronto",
        "Suas escolhas estão salvas e valem nas próximas aberturas. Para mudar depois: Interface do DeepGWT, Telas e Configurações."
    )
}

/** As três escolhas do painel que o assistente oferece; os detalhes continuam em Telas. */
private enum class EscolhaDoPainel(val titulo: String, val descricao: String, val icone: ImageVector) {
    FABRICA("Painel de fábrica", "O painel como veio do carro. Nada do DeepGWT atrás do volante.", Icons.Outlined.Speed),
    EXTRAS(
        "Fábrica + informações",
        "O painel de fábrica com temperatura externa e placa de limite, e o círculo da direita com relógio.",
        Icons.Outlined.Dashboard
    ),
    TEMA("Tema completo", "O DeepGWT desenha o painel inteiro com um tema. Escolha o tema depois em Telas.", Icons.Outlined.Palette);

    /** O que `AjustesRemotos.aplicar` recebe (ids `painel.*` do catálogo). */
    val ajustes: Map<String, Any?>
        get() =
            when (this) {
                FABRICA -> mapOf("painel.tema_customizado" to false, "painel.informacoes" to false, "painel.circulo" to false)
                EXTRAS -> mapOf("painel.tema_customizado" to false, "painel.informacoes" to true, "painel.circulo" to true)
                TEMA -> mapOf("painel.tema_customizado" to true)
            }

    companion object {
        fun atual(prefs: SharedPreferences): EscolhaDoPainel {
            val p = PainelEscolhido.ler(prefs)
            return when {
                p.temaCustomizado -> TEMA
                p.modo == ModoDoPainel.DESLIGADO -> FABRICA
                else -> EXTRAS
            }
        }
    }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

/** A escolha obrigatória do passo "Funções do carro" (gravada em [Preferencias.SETUP_PRESET_CHOICE]). */
private enum class EscolhaDeConfiguracao(val id: String) {
    RECOMENDADA("recomendada"),
    DO_ZERO("do_zero");

    companion object {
        fun ler(prefs: SharedPreferences): EscolhaDeConfiguracao? {
            val gravado = prefs.getString(Preferencias.SETUP_PRESET_CHOICE.chave, null)
            return entries.firstOrNull { it.id == gravado }
        }
    }
}

private const val TAG = "AssistenteInicial"

@Composable
fun AssistenteInicial() {
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    val prefs = remember { context.createDeviceProtectedStorageContext().getSharedPreferences("deepgwt_prefs", Context.MODE_PRIVATE) }
    // O passo da licença só existe quando há cobrança (Alpha 8.1).
    val passos = remember { PassoDoAssistente.entries.filter { it != PassoDoAssistente.LICENCA || Portao.cobrancaAtiva } }
    var inserirLicenca by remember { mutableStateOf(false) }
    /** A recusa do teste grátis (VIN não lido, já usado…), no lugar da descrição do cartão. */
    var testeGratisMsg by remember { mutableStateOf<String?>(null) }
    var indice by rememberSaveable { mutableIntStateOf(0) }
    val passo = passos[indice.coerceIn(0, passos.lastIndex)]

    // Lidos de novo a cada recriação da Activity: é assim que a escolha aparece marcada.
    var interfacePct by remember { mutableIntStateOf(EscalaDaInterface.lerInterface(prefs)) }
    var letrasPct by remember { mutableIntStateOf(EscalaDaInterface.lerLetras(prefs)) }

    var painelAplicado by rememberSaveable { mutableStateOf(EscolhaDoPainel.atual(prefs)) }
    var painel by rememberSaveable { mutableStateOf(painelAplicado) }
    var barraAplicada by rememberSaveable {
        mutableStateOf(prefs.getBoolean(Preferencias.ENABLE_PERSISTENT_BOTTOM_BAR.chave, false))
    }
    var barra by rememberSaveable { mutableStateOf(barraAplicada) }
    // A escolha obrigatória das funções do carro. Lida do disco: sobrevive à Activity recriada.
    var configuracao by rememberSaveable { mutableStateOf(EscolhaDeConfiguracao.ler(prefs)) }
    var resumoDaConfiguracao by remember { mutableStateOf<String?>(null) }

    fun escolherConfiguracao(escolha: EscolhaDeConfiguracao) {
        configuracao = escolha
        prefs.edit().putString(Preferencias.SETUP_PRESET_CHOICE.chave, escolha.id).apply()
        val lote = if (escolha == EscolhaDeConfiguracao.RECOMENDADA) PredefinicaoRecomendada.ajustes else PredefinicaoRecomendada.padroes()
        resumoDaConfiguracao = "Aplicando…"
        escopo.launch(Dispatchers.IO) {
            val r = runCatching { AjustesRemotos.aplicar(lote) }
            r.onFailure { Log.w(TAG, "configuração → $escolha falhou", it) }
            val recusados = r.getOrNull()?.optJSONArray("recusados")?.length() ?: 0
            withContext(Dispatchers.Main) {
                // A predefinição mexe no painel: o passo do painel e o resumo têm de ler o que ficou.
                painelAplicado = EscolhaDoPainel.atual(prefs)
                painel = painelAplicado
                resumoDaConfiguracao = when {
                    r.isFailure -> "Não deu para aplicar agora. Os ajustes ficam em Configurações."
                    recusados > 0 -> "$recusados função(ões) exigem um nível maior e ficaram de fora. O resto foi aplicado."
                    else -> if (escolha == EscolhaDeConfiguracao.RECOMENDADA) "Predefinição aplicada." else "Tudo no padrão: você configura depois."
                }
            }
        }
    }

    fun aplicarAoSair(de: PassoDoAssistente) {
        when (de) {
            PassoDoAssistente.PAINEL ->
                if (painel != painelAplicado) {
                    val escolha = painel
                    painelAplicado = escolha
                    escopo.launch(Dispatchers.IO) {
                        runCatching { AjustesRemotos.aplicar(escolha.ajustes) }
                            .onSuccess {
                                Log.i(TAG, "painel → $escolha: tudo certo=${it.optBoolean("tudoCerto")}, falhas=${it.optJSONArray("falhas")}")
                            }
                            .onFailure { Log.w(TAG, "painel → $escolha falhou", it) }
                    }
                }
            PassoDoAssistente.BARRA ->
                if (barra != barraAplicada) {
                    barraAplicada = barra
                    aplicarBarra(context, prefs, barra)
                }
            else -> Unit
        }
    }

    fun avancar() {
        aplicarAoSair(passo)
        if (indice < passos.lastIndex) indice++
    }

    fun concluir(fazerTour: Boolean) {
        aplicarAoSair(passo)
        AberturaDoDeepGwt.concluirAssistente(context, fazerTour)
    }

    BackHandler(enabled = indice > 0) { indice-- }

    if (inserirLicenca) InserirLicencaDialogo { inserirLicenca = false }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Cores.fundo, Cores.conteiner)))
            .bloquearToques()
    ) {
        // Tela baixa (interface grande, letras grandes, ou a central com barras do sistema): tudo
        // encolhe um pouco para o passo caber sem rolar.
        val compacto = maxHeight < 520.dp
        CompositionLocalProvider(LocalCompacto provides compacto) {
        Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = if (compacto) 10.dp else 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("DeepGWT", fontFamily = Fontes.titulo, fontSize = 18.sp, color = Cores.textoPrincipal, letterSpacing = 1.sp)
                Spacer(Modifier.width(24.dp))
                Pontos(atual = indice, total = passos.size)
                Spacer(Modifier.weight(1f))
                // A escolha das funções é obrigatória (10.0): pular sem ela leva até o passo dela.
                if (passo != PassoDoAssistente.PRONTO && passo != PassoDoAssistente.CONFIGURACAO) {
                    BotaoSecundario("Pular", semBorda = true) {
                        if (configuracao == null) indice = passos.indexOf(PassoDoAssistente.CONFIGURACAO) else concluir(false)
                    }
                }
            }

            Row(
                Modifier.weight(1f).fillMaxWidth().padding(vertical = if (compacto) 6.dp else 14.dp),
                horizontalArrangement = Arrangement.spacedBy(40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    Modifier.weight(0.36f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(if (compacto) 8.dp else 12.dp)
                ) {
                    // Os pontos lá em cima já dizem o passo; sem espaço, o rótulo sai.
                    if (!compacto) {
                        Text(
                            "PASSO ${indice + 1} DE ${passos.size}",
                            color = Cores.destaque,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp
                        )
                    }
                    Text(
                        passo.titulo,
                        color = Cores.textoPrincipal,
                        fontSize = if (compacto) 26.sp else 34.sp,
                        lineHeight = if (compacto) 31.sp else 40.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        passo.descricao,
                        color = Cores.textoSecundario,
                        fontSize = if (compacto) 16.sp else 18.sp,
                        lineHeight = if (compacto) 22.sp else 26.sp
                    )
                }
                Box(Modifier.weight(0.64f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(if (compacto) 10.dp else 16.dp)
                    ) {
                        when (passo) {
                            PassoDoAssistente.BOAS_VINDAS -> BoasVindas()
                            PassoDoAssistente.INTERFACE ->
                                EscolhaDeTamanho(EscalaDaInterface.TAMANHOS_DA_INTERFACE, interfacePct, deLetras = false) {
                                    interfacePct = it
                                    EscalaDaInterface.gravar(context, interfacePct = it)
                                }
                            PassoDoAssistente.LETRAS -> {
                                EscolhaDeTamanho(EscalaDaInterface.TAMANHOS_DAS_LETRAS, letrasPct, deLetras = true) {
                                    letrasPct = it
                                    EscalaDaInterface.gravar(context, letrasPct = it)
                                }
                                Text(
                                    "Temperatura externa 24 °C · Volume 12 · Próxima saída em 800 m",
                                    color = Cores.textoSecundario,
                                    fontSize = 18.sp
                                )
                            }
                            PassoDoAssistente.ESTILO -> {
                                val atual = InterfaceDoDeepGwt.estilo
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                                    EstiloDaInterface.entries.forEach { estilo ->
                                        CartaoDeEstilo(
                                            estilo = estilo,
                                            emUso = estilo == atual,
                                            modifier = Modifier.weight(1f),
                                            onEscolher = { InterfaceDoDeepGwt.escolher(estilo) }
                                        )
                                    }
                                }
                            }
                            PassoDoAssistente.PAINEL ->
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                                    EscolhaDoPainel.entries.forEach { e ->
                                        // Tema completo é do Sport (Alpha 8.1): no Drive aparece com cadeado.
                                        if (e == EscolhaDoPainel.TEMA) {
                                            TravaDoNivel(Recurso.TEMAS_EMBUTIDOS, Modifier.weight(1f)) {
                                                OpcaoComIcone(e.icone, e.titulo, e.descricao, escolhido = painel == e, modifier = Modifier.fillMaxWidth()) {
                                                    painel = e
                                                }
                                            }
                                        } else {
                                            OpcaoComIcone(e.icone, e.titulo, e.descricao, escolhido = painel == e, modifier = Modifier.weight(1f)) {
                                                painel = e
                                            }
                                        }
                                    }
                                }
                            PassoDoAssistente.BARRA ->
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                                    OpcaoComIcone(
                                        Icons.Outlined.ViewDay,
                                        "Com a barra",
                                        "Ar-condicionado, volume e atalhos sempre à mão, por cima de todas as telas.",
                                        escolhido = barra,
                                        modifier = Modifier.weight(1f)
                                    ) { barra = true }
                                    OpcaoComIcone(
                                        Icons.Outlined.VisibilityOff,
                                        "Sem a barra",
                                        "As telas do carro como vieram de fábrica, sem nada por cima.",
                                        escolhido = !barra,
                                        modifier = Modifier.weight(1f)
                                    ) { barra = false }
                                }
                            PassoDoAssistente.CONFIGURACAO -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                                    OpcaoComIcone(
                                        Icons.Outlined.AutoAwesome,
                                        "Predefinição recomendada",
                                        PredefinicaoRecomendada.RESUMO.joinToString("\n") { "• $it" } +
                                            "\nPode ser editada depois.",
                                        escolhido = configuracao == EscolhaDeConfiguracao.RECOMENDADA,
                                        modifier = Modifier.weight(1f)
                                    ) { escolherConfiguracao(EscolhaDeConfiguracao.RECOMENDADA) }
                                    OpcaoComIcone(
                                        Icons.Outlined.Tune,
                                        "Customizar do zero",
                                        "Nada muda sozinho: o carro fica como está e você liga cada função do seu jeito em Configurações.",
                                        escolhido = configuracao == EscolhaDeConfiguracao.DO_ZERO,
                                        modifier = Modifier.weight(1f)
                                    ) { escolherConfiguracao(EscolhaDeConfiguracao.DO_ZERO) }
                                }
                                resumoDaConfiguracao?.let { Text(it, color = Cores.textoSecundario, fontSize = 16.sp) }
                            }
                            PassoDoAssistente.LICENCA -> {
                                    val estado by Portao.estado.collectAsState()
                                    val resumoDaLicenca by Licencas.resumo.collectAsState()
                                    val registroDoTeste by Licencas.avaliacao.collectAsState()
                                    val avaliacao = remember(estado, resumoDaLicenca, registroDoTeste) { Licencas.estadoDaAvaliacao() }
                                    val max = Avaliacao.NIVEL.titulo
                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                                        OpcaoComIcone(
                                            Icons.Outlined.Key,
                                            "Tenho uma licença",
                                            "Cole ou digite o código que você recebeu. Também dá pelo celular ou pelo instalador.",
                                            escolhido = false,
                                            modifier = Modifier.weight(1f)
                                        ) { inserirLicenca = true }
                                        // Teste grátis (10.1: até desligar o carro): só os limpos aparecem aqui, na
                                        // primeira configuração; além do limite fica em Informações → Nível.
                                        if (avaliacao.situacao == Avaliacao.Situacao.DISPONIVEL || avaliacao.situacao == Avaliacao.Situacao.ATIVA || testeGratisMsg != null) {
                                            OpcaoComIcone(
                                                Icons.Outlined.Timer,
                                                if (avaliacao.situacao == Avaliacao.Situacao.ATIVA) "Testando o $max" else "Testar o $max grátis",
                                                testeGratisMsg ?: if (avaliacao.situacao == Avaliacao.Situacao.ATIVA)
                                                    "Vale até desligar o carro. Depois volta ao nível de antes."
                                                else "A versão completa até desligar o carro. ${Avaliacao.contagem(avaliacao)}",
                                                escolhido = avaliacao.situacao == Avaliacao.Situacao.ATIVA,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                if (avaliacao.situacao != Avaliacao.Situacao.DISPONIVEL) return@OpcaoComIcone
                                                escopo.launch {
                                                    val r = withContext(Dispatchers.IO) { Licencas.iniciarAvaliacao() }
                                                    testeGratisMsg = r.exceptionOrNull()?.message
                                                }
                                            }
                                        }
                                        OpcaoComIcone(
                                            Icons.Outlined.CheckCircle,
                                            "Continuar no ${estado.nivel.titulo}",
                                            when {
                                                estado.nivel == Nivel.DRIVE -> "Barra, painel original com informações, círculo, brilho, automações e o estilo do app — de graça."
                                                estado.origem == Portao.Origem.AVALIACAO -> "Teste do $max até desligar o carro; depois volta ao seu plano."
                                                else -> "Licença ativa. As horas só contam com o carro ligado."
                                            },
                                            escolhido = avaliacao.situacao != Avaliacao.Situacao.ATIVA,
                                            modifier = Modifier.weight(1f)
                                        ) { avancar() }
                                    }
                                }
                            PassoDoAssistente.PRONTO ->
                                Resumo(
                                    listOf(
                                        "Interface" to EscalaDaInterface.nomeDe(EscalaDaInterface.TAMANHOS_DA_INTERFACE, interfacePct),
                                        "Letras" to EscalaDaInterface.nomeDe(EscalaDaInterface.TAMANHOS_DAS_LETRAS, letrasPct),
                                        "Estilo" to InterfaceDoDeepGwt.estilo.nome,
                                        "Painel" to painel.titulo,
                                        "Barra inferior" to if (barra) "Ligada" else "Desligada",
                                        "Funções do carro" to when (configuracao) {
                                            EscolhaDeConfiguracao.RECOMENDADA -> "Predefinição recomendada"
                                            EscolhaDeConfiguracao.DO_ZERO -> "Do zero"
                                            null -> "—"
                                        },
                                        "Nível" to Portao.nivel().titulo
                                    )
                                )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (indice > 0) BotaoSecundario("Voltar") { indice-- }
                Spacer(Modifier.weight(1f))
                if (passo == PassoDoAssistente.PRONTO) {
                    BotaoSecundario("Concluir") { concluir(false) }
                    BotaoPrincipal("Fazer o tour") { concluir(true) }
                } else {
                    BotaoPrincipal(
                        if (indice == 0) "Começar" else "Próximo",
                        habilitado = passo != PassoDoAssistente.CONFIGURACAO || configuracao != null
                    ) { avancar() }
                }
            }
        }
        }
    }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

/** O assistente está numa tela baixa: amostras e textos menores (ver [AssistenteInicial]). */
private val LocalCompacto = staticCompositionLocalOf { false }

/** Mesmo caminho do interruptor de Configurações (`AbaDeConfiguracoes`). */
private fun aplicarBarra(context: Context, prefs: SharedPreferences, ligada: Boolean) {
    prefs.edit().putBoolean(Preferencias.ENABLE_PERSISTENT_BOTTOM_BAR.chave, ligada).apply()
    runCatching {
        if (!ligada) {
            Barra.parar(context)
        } else if (Settings.canDrawOverlays(context)) {
            Barra.mostrar(context)
        } else {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }.onFailure { Log.w(TAG, "barra → $ligada falhou", it) }
}

@Composable
private fun Pontos(atual: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { i ->
            Box(
                Modifier
                    .size(width = if (i == atual) 26.dp else 10.dp, height = 10.dp)
                    .clip(CircleShape)
                    .background(if (i <= atual) Cores.destaque else Cores.linhaFina)
            )
        }
    }
}

@Composable
private fun BoasVindas() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("DeepGWT", fontFamily = Fontes.titulo, fontSize = 64.sp, color = Cores.textoPrincipal, letterSpacing = 4.sp)
        Box(
            Modifier
                .width(220.dp)
                .height(3.dp)
                .background(Brush.horizontalGradient(listOf(Cores.destaque, Cores.destaque.copy(alpha = 0f))))
        )
        Text(BuildConfig.VERSION_NAME, color = Cores.textoApagado, fontSize = 16.sp)
    }
}

/**
 * Quatro tamanhos lado a lado. A amostra de cada um é desenhada no tamanho que ela TERÁ: com a
 * escala atual já aplicada à tela inteira, a conta desfaz a atual e aplica a da opção.
 */
@Composable
private fun EscolhaDeTamanho(
    opcoes: List<EscalaDaInterface.Opcao>,
    atual: Int,
    deLetras: Boolean,
    aoEscolher: (Int) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        opcoes.forEach { opcao ->
            val fator = opcao.pct.toFloat() / atual.coerceAtLeast(1)
            val compacto = LocalCompacto.current
            val base = if (compacto) 0.7f else 1f
            CartaoDeOpcao(escolhido = opcao.pct == atual, modifier = Modifier.weight(1f), aoTocar = { aoEscolher(opcao.pct) }) {
                Box(Modifier.height(if (compacto) 44.dp else 64.dp), contentAlignment = Alignment.BottomStart) {
                    if (deLetras) {
                        Text("Aa", color = Cores.textoPrincipal, fontSize = (30 * fator * base).sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Icon(
                            Icons.Outlined.GridView,
                            contentDescription = null,
                            tint = Cores.destaque,
                            modifier = Modifier.size((40 * fator * base).dp)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(opcao.nome, color = Cores.textoPrincipal, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    if (opcao.pct == atual) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Cores.destaque, modifier = Modifier.size(20.dp))
                    }
                }
                Text("${opcao.pct}%", color = Cores.textoSecundario, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun OpcaoComIcone(
    icone: ImageVector,
    titulo: String,
    descricao: String,
    escolhido: Boolean,
    modifier: Modifier,
    aoTocar: () -> Unit
) {
    CartaoDeOpcao(escolhido = escolhido, modifier = modifier, aoTocar = aoTocar) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icone, contentDescription = null, tint = Cores.destaque, modifier = Modifier.size(30.dp))
            Spacer(Modifier.weight(1f))
            if (escolhido) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Cores.destaque, modifier = Modifier.size(22.dp))
        }
        Text(titulo, color = Cores.textoPrincipal, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(descricao, color = Cores.textoSecundario, fontSize = 15.sp, lineHeight = 21.sp)
    }
}

@Composable
private fun Resumo(linhas: List<Pair<String, String>>) {
    val formato = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(formato)
            .background(Cores.conteiner)
            .padding(horizontal = 22.dp, vertical = 12.dp)
    ) {
        val folga = if (LocalCompacto.current) 6.dp else 10.dp
        linhas.forEachIndexed { i, (rotulo, valor) ->
            Row(Modifier.fillMaxWidth().padding(vertical = folga), verticalAlignment = Alignment.CenterVertically) {
                Text(rotulo, color = Cores.textoSecundario, fontSize = 18.sp, modifier = Modifier.weight(1f))
                Text(valor, color = Cores.textoPrincipal, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
            if (i < linhas.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Cores.linhaFina))
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
