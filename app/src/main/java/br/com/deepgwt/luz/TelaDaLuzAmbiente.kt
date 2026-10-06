// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package br.com.deepgwt.luz

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import br.com.deepgwt.ui.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.deepgwt.nucleo.Licenciamento
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

/**
 * A tela de ajustes (Recursos XTRA → Ambient Light). `modoExpert` desenha a tela de portas (código nosso,
 * ligado pela sala suja na troca); null = sem o botão "Modo Expert".
 */
@Composable
fun TelaDaLuzAmbiente(
    aoVoltar: () -> Unit,
    modoExpert: (@Composable (aoVoltar: () -> Unit) -> Unit)? = null,
) {
    var noModoExpert by rememberSaveable { mutableStateOf(false) }
    if (noModoExpert && modoExpert != null) {
        BackHandler { noModoExpert = false }
        modoExpert { noModoExpert = false }
    } else {
        AjustesDaLuzAmbiente(
            aoVoltar = aoVoltar,
            abrirModoExpert = if (modoExpert != null) ({ noModoExpert = true }) else null,
        )
    }
}

private const val HEX_DE_TESTE = "7B0007FF000000FFBF"

private val VERDE_OK = Color(0xFF34C759)
private val VERMELHO_ERRO = Color(0xFFFF453A)
private val AMARELO_ESPERA = Color(0xFFFFCC00)
private val CINZA_PARADO = Color(0xFF8E8E93)

@Composable
private fun AjustesDaLuzAmbiente(aoVoltar: () -> Unit, abrirModoExpert: (() -> Unit)?) {
    val contexto = LocalContext.current
    var ajustes by remember { mutableStateOf(PreferenciasDaLuz.ler()) }
    val conexao = remember { LuzAmbiente.conexaoBle(LuzAmbiente.CONEXAO_PRINCIPAL) }
    val estado by conexao.estado.collectAsState()
    val diagnostico by conexao.diagnostico.collectAsState()
    val musica by DiagnosticoDaLuz.musica.collectAsState()
    val mensagem by DiagnosticoDaLuz.mensagem.collectAsState()
    val busca = remember { BuscaDeFitas(contexto) }
    var buscando by remember { mutableStateOf(false) }
    val achados = remember { mutableStateListOf<FitaEncontrada>() }
    val agora by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(500)
            value = System.currentTimeMillis()
        }
    }

    DisposableEffect(busca) { onDispose { busca.parar() } }

    fun reler() {
        ajustes = PreferenciasDaLuz.ler()
    }

    fun gravar(chave: String, valor: Any?) {
        PreferenciasDaLuz.gravar(chave, valor)
        reler()
    }

    fun avisar(texto: String?) = DiagnosticoDaLuz.mensagem(texto)

    fun reaplicar() = LuzAmbiente.iniciarSeLigada(contexto)

    fun iniciarBusca() {
        achados.clear()
        buscando = true
        var falhou = false
        avisar("Procurando fitas…")
        busca.iniciar(
            aoAchar = { f -> if (achados.none { it.endereco.equals(f.endereco, ignoreCase = true) }) achados.add(f) },
            aoFalhar = {
                falhou = true
                avisar(it)
            },
            aoTerminar = {
                buscando = false
                if (!falhou) avisar(if (achados.isEmpty()) "Nenhum aparelho encontrado" else "${achados.size} aparelho(s) encontrado(s)")
            },
        )
    }

    val pedirPermissoesDaBusca = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (BuscaDeFitas.temPermissao(contexto)) iniciarBusca() else avisar("Sem permissão para procurar fitas")
    }

    val pedirAudio = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedida ->
        if (concedida) {
            PreferenciasDaLuz.gravar(ChavesDaLuz.MUSICA, true)
            PreferenciasDaLuz.gravar(ChavesDaLuz.MODO_MUSICAL, ModoMusical.GRAVES.valorGravado)
            reler()
            reaplicar()
        } else {
            gravar(ChavesDaLuz.MUSICA, false)
            avisar("Sem permissão de áudio os graves não funcionam")
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = aoVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
            Text("Luz ambiente", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ------------------------------------------------------------------ módulo
            item {
                Secao("Módulo") {
                    LinhaComChave("Luz ambiente", "Fita de LED BLE/DMX instalada no carro", ajustes.ligado) { ligar ->
                        gravar(ChavesDaLuz.LIGADA, ligar)
                        if (ligar) {
                            if (!Licenciamento.luzAmbiente()) avisar("O nível de licença deste carro não inclui a luz ambiente")
                            reaplicar()
                        } else {
                            LuzAmbiente.parar(contexto)
                            aoVoltar()
                        }
                    }
                    LinhaDeEstado(estado)
                    mensagem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }

            // ------------------------------------------------------------------ fita
            item {
                Secao("Fita") {
                    Text(
                        if (ajustes.temFita) "${ajustes.nome ?: "Fita sem nome"} · ${ajustes.endereco}" else "Nenhuma fita salva",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (BuscaDeFitas.temPermissao(contexto)) iniciarBusca() else pedirPermissoesDaBusca.launch(BuscaDeFitas.permissoesNecessarias())
                            },
                            enabled = !buscando,
                        ) { Text(if (buscando) "Procurando…" else "Adicionar fita") }
                        OutlinedButton(onClick = {
                            LuzAmbiente.conectar(contexto, null, null)
                            avisar("Testando a conexão…")
                        }, enabled = ajustes.temFita) { Text("Testar conexão") }
                        OutlinedButton(onClick = {
                            LuzAmbiente.desconectar(contexto)
                            avisar("Desconectada")
                        }, enabled = ajustes.temFita) { Text("Desconectar") }
                        TextButton(onClick = {
                            PreferenciasDaLuz.esquecerFita()
                            reler()
                            LuzAmbiente.desconectar(contexto)
                            avisar("Fita esquecida")
                        }, enabled = ajustes.temFita) { Text("Esquecer") }
                    }
                    val lista = AnuncioDaFita.paraTela(achados)
                    if (lista.isNotEmpty()) HorizontalDivider()
                    lista.forEach { f ->
                        LinhaDaFita(f) {
                            busca.parar()
                            buscando = false
                            PreferenciasDaLuz.gravarFita(f.endereco, f.nome)
                            reler()
                            LuzAmbiente.conectar(contexto, f.endereco, f.nome)
                            avisar("Conectando a ${f.nome ?: f.endereco}…")
                        }
                    }
                }
            }

            // ------------------------------------------------------------------ controlador
            item {
                Secao("Controlador") {
                    Escolha("Ordem das cores (DMX)", OrdemDeCor.entries, ajustes.ordemDmx, { it.name }) {
                        gravar(ChavesDaLuz.ORDEM_DMX, it.valorGravado)
                    }
                    Escolha("Ordem das cores (BLE)", OrdemDeCor.entries, ajustes.ordemBle, { it.name }) {
                        gravar(ChavesDaLuz.ORDEM_BLE, it.valorGravado)
                    }
                    Escolha("Saída", SaidaDaLuz.entries, ajustes.saida, { it.rotulo }) {
                        gravar(ChavesDaLuz.SAIDA, it.valorGravado)
                    }
                    var brilho by remember(ajustes.brilho) { mutableFloatStateOf(ajustes.brilho.toFloat()) }
                    Text("Brilho: ${brilho.roundToInt()}%", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = brilho,
                        onValueChange = { brilho = it },
                        valueRange = 0f..100f,
                        steps = 99,
                        onValueChangeFinished = {
                            val p = AjustesDaLuz.limitarBrilho(brilho.roundToInt())
                            gravar(ChavesDaLuz.BRILHO, p)
                            LuzAmbiente.brilho(contexto, p)
                        },
                    )
                }
            }

            // ------------------------------------------------------------------ cor manual
            if (!ajustes.dinamica) {
                item {
                    Secao("Cor manual") {
                        var matiz by remember(ajustes.matiz) { mutableFloatStateOf(ajustes.matiz) }
                        val cor = CorLed.daMatiz(matiz)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Bolinha(cor, 28)
                            Spacer(Modifier.width(12.dp))
                            Text("${matiz.roundToInt()}°  ·  RGB ${cor.r}, ${cor.g}, ${cor.b}", style = MaterialTheme.typography.bodyMedium)
                        }
                        Slider(
                            value = matiz,
                            onValueChange = { matiz = it },
                            valueRange = 0f..360f,
                            enabled = ajustes.temFita,
                            onValueChangeFinished = {
                                val h = AjustesDaLuz.limitarMatiz(matiz)
                                gravar(ChavesDaLuz.MATIZ, h)
                                val c = CorLed.daMatiz(h)
                                LuzAmbiente.testarCor(contexto, c)
                                avisar("Quadros: ${Hex.deQuadros(Quadros.cor(c, ajustes.saida, ajustes.ordemDmx, ajustes.ordemBle))}")
                            },
                        )
                        if (!ajustes.temFita) Text("Adicione uma fita para escolher a cor.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // ------------------------------------------------------------------ teste de cores
            item {
                Secao("Teste de cores") {
                    val testes = listOf(
                        "Vermelho" to Cores.VERMELHO,
                        "Verde" to Cores.VERDE,
                        "Azul" to Cores.AZUL,
                        "Branco" to Cores.BRANCO,
                        "Amarelo" to Cores.AMARELO,
                        "Roxo" to Cores.ROXO,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        testes.forEach { (nome, c) ->
                            OutlinedButton(onClick = {
                                LuzAmbiente.testarCor(contexto, c)
                                avisar("Teste: $nome")
                            }, enabled = ajustes.temFita) {
                                Bolinha(c, 14)
                                Spacer(Modifier.width(8.dp))
                                Text(nome)
                            }
                        }
                        OutlinedButton(onClick = {
                            LuzAmbiente.enviarHex(contexto, HEX_DE_TESTE)
                            avisar("HEX de teste: $HEX_DE_TESTE")
                        }, enabled = ajustes.temFita) { Text("Enviar HEX de teste") }
                    }
                }
            }

            // ------------------------------------------------------------------ automação
            item {
                Secao("Automação") {
                    LinhaComChave("Reconectar sozinho", "Tenta de novo quando a fita cai", ajustes.reconectar) { ligar ->
                        gravar(ChavesDaLuz.RECONECTAR, ligar)
                        if (ligar) reaplicar()
                    }
                    LinhaComChave("Iluminação dinâmica", "A luz reage ao carro: modo de condução, alertas e farol", ajustes.dinamica) {
                        gravar(ChavesDaLuz.DINAMICA, it)
                        reaplicar()
                    }
                    if (ajustes.dinamica) {
                        ReacaoDaLuz.entries.forEach { r ->
                            LinhaComChave(r.titulo, r.descricao, r in ajustes.reacoes, recuo = true) {
                                gravar(r.chave.chave, it)
                                reaplicar()
                            }
                        }
                    }
                    LinhaComChave("Animações", "Boas-vindas, transições e efeitos do modo", ajustes.animacoes) {
                        gravar(ChavesDaLuz.ANIMACOES, it)
                        reaplicar()
                    }
                    LinhaComChave("Efeito com música", "Com música ligada a luz não reage ao carro", ajustes.musica) { ligar ->
                        if (ligar && ajustes.modoMusical == ModoMusical.GRAVES && !PermissoesDaLuz.podeGravarAudio(contexto)) {
                            pedirAudio.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            gravar(ChavesDaLuz.MUSICA, ligar)
                            reaplicar()
                        }
                    }
                    Escolha("Modo da animação com música", ModoMusical.entries, ajustes.modoMusical, { it.rotulo }) { m ->
                        gravar(ChavesDaLuz.MODO_MUSICAL, m.valorGravado)
                        if (m == ModoMusical.GRAVES && ajustes.musica && !PermissoesDaLuz.podeGravarAudio(contexto)) {
                            gravar(ChavesDaLuz.MUSICA, false)
                            pedirAudio.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            reaplicar()
                        }
                    }
                    if (ajustes.modoMusical == ModoMusical.ONDA_DO_ALBUM) {
                        Escolha("Efeito", EfeitoDoAlbum.entries, ajustes.efeitoDoAlbum, { it.curto }) {
                            gravar(ChavesDaLuz.EFEITO_DO_ALBUM, it.valorGravado)
                        }
                        Escolha("Aplicar em", SaidaDaLuz.entries, ajustes.saidaDoAlbum, { it.rotulo }) {
                            gravar(ChavesDaLuz.SAIDA_DO_ALBUM, it.valorGravado)
                        }
                        Escolha("BLE", ModoDoAlbum.entries, ajustes.modoBleDoAlbum, { it.rotulo }) {
                            gravar(ChavesDaLuz.MODO_BLE_DO_ALBUM, it.valorGravado)
                        }
                        Escolha("DMX", ModoDoAlbum.entries, ajustes.modoDmxDoAlbum, { it.rotulo }) {
                            gravar(ChavesDaLuz.MODO_DMX_DO_ALBUM, it.valorGravado)
                        }
                        var velocidade by remember(ajustes.velocidadeDoAlbum) { mutableFloatStateOf(ajustes.velocidadeDoAlbum.toFloat()) }
                        Text("Velocidade: ${velocidade.roundToInt()}", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = velocidade,
                            onValueChange = { velocidade = it },
                            valueRange = 1f..100f,
                            steps = 98,
                            onValueChangeFinished = {
                                gravar(ChavesDaLuz.VELOCIDADE_DO_ALBUM, AjustesDaLuz.limitarVelocidade(velocidade.roundToInt()))
                            },
                        )
                    }
                }
            }

            // ------------------------------------------------------------------ diagnóstico
            item {
                Secao("Diagnóstico") {
                    Info("Estado", estado.rotulo)
                    Info("RSSI", diagnostico.rssi?.let { "$it dBm" } ?: "—")
                    Info("Serviço", diagnostico.servico ?: "—")
                    Info(
                        "Característica",
                        diagnostico.caracteristica?.let {
                            it + when (diagnostico.semResposta) {
                                true -> " (sem resposta)"
                                false -> " (com resposta)"
                                null -> ""
                            }
                        } ?: "—",
                    )
                    Info("Último quadro", diagnostico.ultimoQuadro ?: "—", mono = true)
                    Info("Último erro", diagnostico.ultimoErro ?: "—")
                    HorizontalDivider()
                    Info("Música", if (musica.ativa) "ativa" else "inativa")
                    Info("Modo", ajustes.modoMusical.rotulo)
                    if (ajustes.modoMusical == ModoMusical.ONDA_DO_ALBUM) {
                        Info(
                            "Álbum",
                            "${ajustes.efeitoDoAlbum.curto} · ${ajustes.saidaDoAlbum.rotulo} · BLE ${ajustes.modoBleDoAlbum.rotulo} · " +
                                "DMX ${ajustes.modoDmxDoAlbum.rotulo} · velocidade ${ajustes.velocidadeDoAlbum}",
                        )
                    }
                    Info("Fonte", musica.fonte.rotulo)
                    Info("Nível", "${(musica.nivel * 100).roundToInt()}%")
                    Info(
                        "Última batida/passo",
                        if (musica.ultimoEventoEm > 0) {
                            String.format(PORTUGUES, "há %.1f s", ((agora - musica.ultimoEventoEm).coerceAtLeast(0L)) / 1000.0)
                        } else {
                            "—"
                        },
                    )
                    musica.aviso?.let { Info("Aviso", it) }
                    Info("Erro da música", musica.erro ?: "—")
                }
            }

            // ------------------------------------------------------------------ modo expert
            if (abrirModoExpert != null) {
                item {
                    Secao("Modo Expert") {
                        Text("Teste por porta: uma fita e uma conexão para cada porta.", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = abrirModoExpert) { Text("Abrir Modo Expert") }
                    }
                }
            }
        }
    }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

private val PORTUGUES: Locale = Locale.forLanguageTag("pt-BR")

@Composable
private fun Secao(titulo: String, conteudo: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            conteudo()
        }
    }
}

@Composable
private fun LinhaComChave(
    titulo: String,
    descricao: String?,
    ligado: Boolean,
    recuo: Boolean = false,
    aoMudar: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = if (recuo) 16.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge)
            if (!descricao.isNullOrBlank()) {
                Text(descricao, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = ligado, onCheckedChange = aoMudar)
    }
}

@Composable
private fun <T> Escolha(rotulo: String, opcoes: List<T>, atual: T, nome: (T) -> String, aoEscolher: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(rotulo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            opcoes.forEach { o ->
                FilterChip(selected = o == atual, onClick = { if (o != atual) aoEscolher(o) }, label = { Text(nome(o)) })
            }
        }
    }
}

@Composable
private fun LinhaDeEstado(estado: EstadoDaConexao) {
    val cor = when (estado) {
        is EstadoDaConexao.Conectada -> VERDE_OK
        is EstadoDaConexao.Erro -> VERMELHO_ERRO
        is EstadoDaConexao.Conectando, is EstadoDaConexao.Reconectando -> AMARELO_ESPERA
        EstadoDaConexao.Parada -> CINZA_PARADO
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(cor))
        Spacer(Modifier.width(8.dp))
        Text(estado.rotulo, style = MaterialTheme.typography.bodyMedium, color = cor)
    }
}

@Composable
private fun LinhaDaFita(f: FitaEncontrada, aoTocar: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = aoTocar).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(f.nome ?: "Dispositivo BLE", style = MaterialTheme.typography.bodyLarge, fontWeight = if (f.pareceFita) FontWeight.SemiBold else FontWeight.Normal)
            Text("${f.endereco} · ${f.rssi} dBm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(AnuncioDaFita.tipo(f), style = MaterialTheme.typography.labelMedium, color = if (f.pareceFita) VERDE_OK else CINZA_PARADO)
    }
}

@Composable
private fun Info(rotulo: String, valor: String, mono: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(rotulo, Modifier.width(160.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.bodySmall, fontFamily = if (mono) FontFamily.Monospace else null)
    }
}

@Composable
private fun Bolinha(cor: CorLed, tamanhoDp: Int) {
    val c = ContasDeCor.limitar(cor)
    Box(Modifier.size(tamanhoDp.dp).clip(CircleShape).background(Color(c.r, c.g, c.b)))
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
