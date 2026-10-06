// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui.screens

import br.com.deepgwt.ui.Cores
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.deepgwt.licenca.Avaliacao
import br.com.deepgwt.licenca.AvisoDaLicenca
import br.com.deepgwt.licenca.Carteira
import br.com.deepgwt.licenca.ChavesPublicas
import br.com.deepgwt.licenca.CodigoDeLicenca
import br.com.deepgwt.licenca.ComoObterLicenca
import br.com.deepgwt.licenca.Licenca
import br.com.deepgwt.licenca.LicencaRemota
import br.com.deepgwt.licenca.Licencas
import br.com.deepgwt.licenca.Nivel
import br.com.deepgwt.licenca.Portao
import br.com.deepgwt.licenca.Recurso
import br.com.deepgwt.licenca.Saldo
import br.com.deepgwt.managers.GwLinkServer
import br.com.deepgwt.onboarding.BotaoPrincipal
import br.com.deepgwt.onboarding.BotaoSecundario
import br.com.deepgwt.ui.GlossyCard
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Informações → Nível e licença (Alpha 8.0; licença na 8.1).
 *
 * O nível agora e de onde ele vem; com a cobrança ligada, o VIN mascarado, o saldo de horas de
 * cada nível, as licenças aplicadas e as três portas de entrada de um código: digitar aqui, pelo
 * celular (QR para a página que o próprio carro serve) ou pelo instalador. A tabela do que cada
 * nível libera fica recolhida. Em build de teste, o seletor de nível e a cobrança de teste.
 * Desde a 8.4, o teste grátis do Max ([CartaoDoTesteGratis]).
 */
@Composable
internal fun NivelDoDeepGwtCard() {
    val estado by Portao.estado.collectAsState()
    val resumo by Licencas.resumo.collectAsState()
    var inserir by remember { mutableStateOf(false) }
    var celular by remember { mutableStateOf(false) }
    var comoObter by remember { mutableStateOf(false) }
    var tabela by remember { mutableStateOf(false) }
    val cobranca = Portao.cobrancaAtiva

    GlossyCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Nível e licença", color = Cores.textoPrincipal, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(estado.origem.descricao, color = Cores.textoSecundario, fontSize = 14.sp)
                }
                SeloGrande(estado.nivel)
            }

            if (estado.origem == Portao.Origem.ALTERADO) {
                Faixa(
                    "Este DeepGWT não é o que o instalador envia (o app foi modificado): as licenças não valem nele" +
                        (if (estado.caiPara != null) " e ele passa para o ${Nivel.DRIVE.titulo} ao desligar o carro" else "") +
                        ". Reinstale pelo instalador no computador: Instalar e manter → Atualizar."
                )
            } else if (estado.origem == Portao.Origem.AVALIACAO && estado.caiPara != null) {
                Faixa("O teste do Max acabou. Na primeira parada o DeepGWT volta ao ${estado.caiPara?.titulo}.")
            } else estado.caiPara?.let { para ->
                Faixa("As horas acabaram. Ao desligar o carro o DeepGWT passa para o ${para.titulo}.")
            }
            // No teste grátis as horas das licenças estão paradas: o aviso delas espera.
            resumo.aviso?.takeIf { cobranca && estado.origem != Portao.Origem.AVALIACAO }?.let { Faixa(it.texto) }
            if (cobranca) CartaoDoTesteGratis(estado, resumo)

            if (cobranca) {
                Text(
                    resumo.vin?.let { "VIN " + Licenca.vinMascarado(it) + if (resumo.vinLidoAgora) "" else "  (da última leitura)" }
                        ?: "VIN ainda não lido — as licenças conferem quando o carro responder.",
                    color = Cores.textoSecundario,
                    fontSize = 14.sp
                )
                val saldos = resumo.carteira.saldos().filter { s -> resumo.carteira.aplicadas.any { it.licenca.nivel == s.nivel } }
                if (saldos.isEmpty()) {
                    Text(
                        if (estado.origem == Portao.Origem.SEM_LICENCA) "Nenhuma licença neste carro: o DeepGWT está no ${Nivel.DRIVE.titulo}, o nível grátis."
                        else "Nenhuma licença neste carro.",
                        color = Cores.textoPrincipal,
                        fontSize = 15.sp
                    )
                } else {
                    // Com o nível seguro até desligar, "em uso" é o que está valendo, não o próximo.
                    // Num app alterado nenhuma licença está em uso; no teste grátis, estão paradas.
                    val emUso = when {
                        estado.origem == Portao.Origem.ALTERADO -> null
                        estado.origem == Portao.Origem.AVALIACAO -> null
                        estado.caiPara != null -> estado.nivel
                        else -> resumo.nivelAtivo
                    }
                    saldos.forEach { LinhaDeSaldo(it, ativo = it.nivel == emUso, ateDesligar = estado.caiPara != null && it.nivel == estado.nivel) }
                }
                if (resumo.deOutroCarro > 0) {
                    Text(
                        "${resumo.deOutroCarro} licença(s) guardada(s) não conferem com o VIN deste carro e foram ignoradas.",
                        color = Cores.textoApagado,
                        fontSize = 13.sp
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    BotaoPrincipal("Inserir licença") { inserir = true }
                    BotaoSecundario("Pelo celular") { celular = true }
                    BotaoSecundario("Como obter", semBorda = true) { comoObter = !comoObter }
                }
                Text(
                    "Mais fácil: no computador, abra o DeepGWT conectado ao carro e clique em Ativar licença — é só colar a mensagem que você recebeu.",
                    color = Cores.textoApagado,
                    fontSize = 13.sp
                )
                if (comoObter) Text(ComoObterLicenca.TEXTO, color = Cores.textoSecundario, fontSize = 14.sp)
                if (resumo.carteira.aplicadas.isNotEmpty()) ListaDeLicencas(resumo.carteira)
            } else {
                Text(
                    "Nesta versão todos os recursos estão liberados: nenhuma licença é necessária.",
                    color = Cores.textoSecundario,
                    fontSize = 14.sp
                )
            }

            Text(
                if (tabela) "Esconder o que cada nível libera" else "Ver o que cada nível libera",
                color = Cores.destaque,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { tabela = !tabela }.padding(vertical = 4.dp)
            )
            if (tabela) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Nivel.entries.forEach { nivel ->
                        ColunaDoNivel(nivel, atual = nivel == estado.nivel, liberado = estado.nivel.alcanca(nivel), modifier = Modifier.weight(1f))
                    }
                }
            }

            if (Portao.testeLiberado) SeletorDeTeste()
        }
    }

    if (inserir) InserirLicencaDialogo { inserir = false }
    if (celular) PeloCelularDialogo { celular = false }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

/**
 * O teste grátis do Max (10.1): vale até desligar o carro; [Avaliacao.LIMITE_GRATIS] por instalação.
 * Além do limite o botão continua (sem trava), avisando que vem com o aviso na tela. Correndo:
 * "Voltar ao meu plano". Some quando não faz sentido (já tem o Max, app alterado).
 */
@Composable
private fun CartaoDoTesteGratis(estado: Portao.Estado, resumo: Licencas.Resumo) {
    val escopo = rememberCoroutineScope()
    var mensagem by remember { mutableStateOf<String?>(null) }
    var deuCerto by remember { mutableStateOf(false) }
    var ocupado by remember { mutableStateOf(false) }
    val registro by Licencas.avaliacao.collectAsState()
    val avaliacao = remember(estado, resumo, registro) { Licencas.estadoDaAvaliacao() }
    val max = Avaliacao.NIVEL.titulo
    val depois = (resumo.nivelAtivo ?: Nivel.DRIVE).titulo

    fun executar(acao: () -> Result<Avaliacao.Estado>, falha: String) {
        if (ocupado) return
        ocupado = true
        escopo.launch {
            val r = withContext(Dispatchers.IO) { acao() }
            ocupado = false
            r.onSuccess { mensagem = null; deuCerto = true }
                .onFailure { mensagem = it.message ?: falha; deuCerto = false }
        }
    }

    if (avaliacao.situacao == Avaliacao.Situacao.INDISPONIVEL) return
    val ativa = avaliacao.situacao == Avaliacao.Situacao.ATIVA
    val esgotada = avaliacao.situacao == Avaliacao.Situacao.ESGOTADA
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, if (esgotada || avaliacao.comAviso) Cores.perigo else Cores.destaque, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (ativa) "Testando o $max" else "Teste grátis",
                color = Cores.textoPrincipal, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
            )
            Text(
                when {
                    ativa && avaliacao.comAviso -> Avaliacao.TEXTO_DO_AVISO + " O aviso fica em todas as telas até desligar o carro."
                    ativa -> "O $max vale até desligar o carro (ou a central reiniciar). Depois o DeepGWT volta ao $depois. As horas das licenças não contam durante o teste."
                    esgotada -> "Você já usou os ${avaliacao.limite} testes grátis desta instalação. Dá para testar de novo, mas com um aviso de pagamento em todas as telas."
                    else -> "Experimente o $max, a versão completa, até desligar o carro. Depois o DeepGWT volta ao $depois. " + Avaliacao.contagem(avaliacao)
                },
                color = Cores.textoSecundario,
                fontSize = 14.sp
            )
            mensagem?.let { Text(it, color = if (deuCerto) Cores.destaque else Cores.perigo, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        }
        if (ativa) {
            BotaoSecundario(if (ocupado) "Voltando…" else "Voltar ao meu plano") {
                executar({ Licencas.encerrarAvaliacao("voltar ao meu plano (tela)") }, "Não deu para encerrar o teste.")
            }
        } else {
            BotaoPrincipal(if (ocupado) "Começando…" else if (esgotada) "Testar o $max mesmo assim" else "Testar o $max grátis") {
                executar({ Licencas.iniciarAvaliacao() }, "Não deu para começar o teste.")
            }
        }
    }
}

@Composable
private fun SeloGrande(nivel: Nivel) {
    Text(
        nivel.titulo.uppercase(),
        color = Cores.sobreODestaque,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Cores.destaque)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

@Composable
private fun Faixa(texto: String) {
    Text(
        texto,
        color = Cores.textoPrincipal,
        fontSize = 15.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Cores.atencao, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    )
}

@Composable
private fun LinhaDeSaldo(s: Saldo, ativo: Boolean, ateDesligar: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                s.nivel.titulo + when {
                    ateDesligar -> "  · até desligar o carro"
                    ativo -> "  · em uso"
                    else -> ""
                },
                color = Cores.textoPrincipal,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                when {
                    s.vitalicia -> "vitalícia"
                    !s.temSaldo -> "acabou (${AvisoDaLicenca.duracao(s.totalSeg)} usadas)"
                    else -> "${AvisoDaLicenca.duracao(s.restanteSeg)} de ${AvisoDaLicenca.duracao(s.totalSeg)}"
                },
                color = Cores.textoSecundario,
                fontSize = 14.sp
            )
        }
        val fracao = s.fracaoRestante.toFloat().coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Cores.linhaFina)) {
            Box(
                Modifier.fillMaxWidth(fracao).height(6.dp)
                    .background(if (fracao < AvisoDaLicenca.FRACAO_POUCO) Cores.atencao else Cores.destaque)
            )
        }
    }
}

@Composable
private fun ListaDeLicencas(carteira: br.com.deepgwt.licenca.Carteira) {
    val data = remember { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Licenças aplicadas", color = Cores.textoApagado, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        carteira.aplicadas.sortedByDescending { it.aplicadaEmMs }.forEach { a ->
            val l = a.licenca
            val quanto = if (l.vitalicia) "vitalícia" else "${l.horas} h · usadas ${AvisoDaLicenca.duracao(carteira.usoDe(l.serie).coerceAtMost(l.segundos))}"
            Text(
                "${l.nivel.titulo} · $quanto · série ${l.serie} · aplicada ${data.format(Date(a.aplicadaEmMs))}" +
                    if (l.chave == ChavesPublicas.ID_DE_TESTE) " · TESTE" else "",
                color = Cores.textoSecundario,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun ColunaDoNivel(nivel: Nivel, atual: Boolean, liberado: Boolean, modifier: Modifier) {
    val formato = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(formato)
            .border(
                if (atual) BorderStroke(2.dp, Cores.destaque) else BorderStroke(1.dp, Cores.linhaFina),
                formato
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            nivel.titulo + if (atual) "  · atual" else "",
            color = if (liberado) Cores.textoPrincipal else Cores.textoApagado,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Recurso.doNivel(nivel).filter { it != Recurso.ESSENCIAL }.forEach {
            Text(
                "• ${it.titulo}",
                color = if (liberado) Cores.textoSecundario else Cores.textoApagado,
                fontSize = 13.sp
            )
        }
    }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

@Composable
private fun Chip(texto: String, escolhido: Boolean, aoTocar: () -> Unit) {
    Text(
        texto,
        color = if (escolhido) Cores.sobreODestaque else Cores.textoPrincipal,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (escolhido) Cores.destaque else Cores.conteiner)
            .clickable(onClick = aoTocar)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

/** Só em build de teste: nível de teste e cobrança de teste (licença de teste, sem licença = Drive). */
@Composable
private fun SeletorDeTeste() {
    val escopo = rememberCoroutineScope()
    // Relido a cada mudança de estado (o collectAsState do cartão recompõe isto).
    val teste = Portao.nivelDeTesteGravado()
    val cobrancaDeTeste = Licencas.cobrancaDeTeste()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Nível de teste:", color = Cores.textoApagado, fontSize = 13.sp)
            (listOf<Nivel?>(null) + Nivel.entries).forEach { opcao ->
                Chip(opcao?.titulo ?: "Normal", opcao == teste) {
                    escopo.launch { withContext(Dispatchers.IO) { Portao.definirNivelDeTeste(opcao) } }
                }
            }
        }
        if (!ChavesPublicas.temProducao) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Cobrança de teste (build sem chave de produção):", color = Cores.textoApagado, fontSize = 13.sp)
                Chip("Desligada", !cobrancaDeTeste) {
                    escopo.launch { withContext(Dispatchers.IO) { Portao.definirCobrancaDeTeste(false) } }
                }
                Chip("Ligada", cobrancaDeTeste) {
                    escopo.launch { withContext(Dispatchers.IO) { Portao.definirCobrancaDeTeste(true) } }
                }
            }
        }
    }
}

// ── Inserir licença ─────────────────────────────────────────────────────────────────────────────

/** O código digitado ou colado. Também usado pelo assistente de primeira abertura. */
@Composable
internal fun InserirLicencaDialogo(aoFechar: () -> Unit) {
    Dialog(onDismissRequest = aoFechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ConteudoDeInserirLicenca(aoFechar)
    }
}

/**
 * Dentro do [Dialog] de propósito: foco e teclado são da janela do diálogo — pegos fora dele, eram
 * os da tela de trás e soltar o foco não fechava teclado nenhum.
 */
@Composable
private fun ConteudoDeInserirLicenca(aoFechar: () -> Unit) {
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    var codigo by remember { mutableStateOf("") }
    var mensagem by remember { mutableStateOf<String?>(null) }
    var deuCerto by remember { mutableStateOf(false) }
    var conferindo by remember { mutableStateOf(false) }
    val simbolos = CodigoDeLicenca.simbolosDigitados(codigo)
    val total = CodigoDeLicenca.TAMANHO
    // Numa tela de 720 px o teclado cobre a metade de baixo: botões e resposta ficam EM CIMA, e o
    // teclado sai de cena (foco fora do campo) quando o código fica completo ou ao ativar.
    val teclado = LocalSoftwareKeyboardController.current
    val foco = LocalFocusManager.current
    fun soltarTeclado() {
        foco.clearFocus()
        teclado?.hide()
    }

    fun ativar() {
        if (conferindo) return
        soltarTeclado()
        // A conferência das linhas roda aqui, sem ir ao disco: o erro de digitação aparece na hora.
        val leitura = CodigoDeLicenca.ler(codigo)
        if (leitura is CodigoDeLicenca.Leitura.Erro) {
            mensagem = leitura.mensagem
            deuCerto = false
            return
        }
        conferindo = true
        val texto = codigo
        escopo.launch {
            val r = withContext(Dispatchers.IO) { Licencas.ativar(texto) }
            conferindo = false
            r.onSuccess { mensagem = LicencaRemota.mensagemDeAtivada(it.licenca); deuCerto = true; codigo = "" }
                .onFailure { mensagem = it.message ?: "Não deu para ativar."; deuCerto = false }
        }
    }

    run {
        Column(
            Modifier
                .widthIn(max = 860.dp)
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(22.dp))
                .background(Cores.fundo)
                .border(1.dp, Cores.linhaFina, RoundedCornerShape(22.dp))
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Inserir licença",
                    color = Cores.textoPrincipal,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                BotaoSecundario("Colar") {
                    val cm = contexto.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val texto = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(contexto)?.toString()
                    if (texto.isNullOrBlank()) {
                        mensagem = "A área de transferência está vazia."
                        deuCerto = false
                    } else {
                        // A mensagem inteira do WhatsApp serve: fica só o código, já arrumado.
                        val lido = CodigoDeLicenca.ler(texto)
                        codigo = if (lido is CodigoDeLicenca.Leitura.Ok) CodigoDeLicenca.formatar(lido.numero) else texto.uppercase()
                        mensagem = (lido as? CodigoDeLicenca.Leitura.Erro)?.mensagem
                        deuCerto = false
                        soltarTeclado()
                    }
                }
                BotaoSecundario(if (deuCerto) "Fechar" else "Cancelar") { aoFechar() }
                if (!deuCerto) BotaoPrincipal(if (conferindo) "Conferindo…" else "Ativar") { ativar() }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val completo = CodigoDeLicenca.ler(codigo) is CodigoDeLicenca.Leitura.Ok
                Text(
                    if (completo) "Código completo" else "$simbolos de $total caracteres",
                    color = if (completo) Cores.destaque else Cores.textoApagado,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                mensagem?.let {
                    Text(it, color = if (deuCerto) Cores.destaque else Cores.perigo, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            OutlinedTextField(
                value = codigo,
                onValueChange = {
                    // Código completo (digitado ou achado numa mensagem colada): fica arrumado em 3 linhas.
                    val lido = CodigoDeLicenca.ler(it)
                    codigo = if (lido is CodigoDeLicenca.Leitura.Ok) CodigoDeLicenca.formatar(lido.numero) else it.uppercase()
                    mensagem = null
                    deuCerto = false
                    // Fora do callback da edição: soltar o foco dentro dele o teclado ignora.
                    if (CodigoDeLicenca.simbolosDigitados(codigo) >= total) {
                        escopo.launch { delay(150); soltarTeclado() }
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                placeholder = { Text("XXXXX-XXXXX-XXXXX-XXXXX", fontFamily = FontFamily.Monospace, fontSize = 22.sp, color = Cores.textoApagado) },
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 22.sp, lineHeight = 32.sp, color = Cores.textoPrincipal),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { soltarTeclado() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Cores.destaque,
                    unfocusedBorderColor = Cores.linhaFina,
                    cursorColor = Cores.destaque
                )
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Cole ou digite o código de 3 linhas (ou cole a mensagem inteira que você recebeu). Se uma letra estiver errada, a linha dela aparece aqui. Mais fácil: ative pelo DeepGWT no computador.",
                    color = Cores.textoSecundario,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                BotaoSecundario("Limpar", semBorda = true) { codigo = ""; mensagem = null }
            }
        }
    }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

// ── Pelo celular ────────────────────────────────────────────────────────────────────────────────

/** Endereços IPv4 do carro nas redes em que ele está (hotspot primeiro). */
private fun enderecosDoCarro(): List<Pair<String, String>> =
    runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { i -> i.inetAddresses.toList().filterIsInstance<Inet4Address>().map { i.name to it.hostAddress.orEmpty() } }
            .filter { it.second.isNotEmpty() }
            .sortedBy { (nome, _) -> if (nome.startsWith("ap") || nome.startsWith("swlan") || nome.contains("softap")) 0 else if (nome.startsWith("wlan")) 1 else 2 }
    }.getOrDefault(emptyList())

@Composable
internal fun PeloCelularDialogo(aoFechar: () -> Unit) {
    val enderecos = remember { enderecosDoCarro() }
    var escolhido by remember { mutableStateOf(0) }
    val url = enderecos.getOrNull(escolhido)?.let { "http://${it.second}:${GwLinkServer.PORT}/ativar" }

    Dialog(onDismissRequest = aoFechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Row(
            Modifier
                .widthIn(max = 900.dp)
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(22.dp))
                .background(Cores.fundo)
                .border(1.dp, Cores.linhaFina, RoundedCornerShape(22.dp))
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Ativar pelo celular", color = Cores.textoPrincipal, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "1. Ligue o hotspot do carro e conecte o celular nele.\n" +
                        "2. Aponte a câmera do celular para o código ao lado (ou digite o endereço no navegador).\n" +
                        "3. Cole o código da licença que você recebeu e toque em Ativar.",
                    color = Cores.textoSecundario,
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                )
                if (url == null) {
                    Text("O carro não está em nenhuma rede agora. Ligue o hotspot e abra de novo.", color = Cores.perigo, fontSize = 15.sp)
                } else {
                    Text(url, color = Cores.destaque, fontSize = 18.sp, fontFamily = FontFamily.Monospace)
                    if (enderecos.size > 1) {
                        Text("Outras redes do carro:", color = Cores.textoApagado, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            enderecos.forEachIndexed { i, (nome, ip) -> Chip("$nome $ip", i == escolhido) { escolhido = i } }
                        }
                    }
                }
                Row { Spacer(Modifier.weight(1f)); BotaoSecundario("Fechar") { aoFechar() } }
            }
            if (url != null) QrCode(url, Modifier.size(260.dp))
        }
    }
}

@Composable
private fun QrCode(texto: String, modifier: Modifier) {
    val matriz: BitMatrix? = remember(texto) {
        runCatching { QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 2)) }.getOrNull()
    }
    if (matriz == null) return
    // Escuro no claro, sempre: câmera de celular lê mal QR invertido.
    Canvas(modifier.clip(RoundedCornerShape(12.dp)).background(Color.White)) {
        val lado = size.minDimension / matriz.width
        for (y in 0 until matriz.height) {
            for (x in 0 until matriz.width) {
                if (matriz.get(x, y)) drawRect(Color.Black, Offset(x * lado, y * lado), Size(lado + 0.5f, lado + 0.5f))
            }
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
