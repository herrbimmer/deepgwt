package br.com.opengw.ui.telas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import android.util.Log
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.opengw.nucleo.Preferencias
import br.com.opengw.rede.ControleDeDadosMoveis
import br.com.opengw.rede.ModoDoRoteador
import br.com.opengw.rede.RoteadorDoHotspot
import br.com.opengw.ui.ColunaDaTela
import br.com.opengw.ui.CartaoDeSecao
import br.com.opengw.ui.Cores
import br.com.opengw.ui.LinhaComInterruptor
import br.com.opengw.ui.LinhaDeControleDeslizante
import br.com.opengw.veiculo.sistema.Conectividade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** O que a aba mostra e que só se descobre rodando shell ou consultando o sistema (fora da principal). */
private data class LeituraDaRede(
    val texto4g: String = "",
    val textoDoUso: String = "",
    val textoDoRoteador: String = "",
    val textoDoHotspot: String = "",
)

/**
 * A aba "Rede": controle dos dados móveis (4G) da central e roteador do hotspot. Cada interruptor
 * grava na hora, pelo módulo `br.com.opengw.rede`; os textos de estado se atualizam a cada 5 s
 * enquanto a aba está aberta.
 */
@Composable
fun AbaDeRede() {
    val contexto = LocalContext.current

    var controle by remember { mutableStateOf(ControleDeDadosMoveis.controleLigado()) }
    var manual by remember { mutableStateOf(ControleDeDadosMoveis.corteManual()) }
    var consumo by remember { mutableStateOf(ControleDeDadosMoveis.corteSobConsumo()) }
    var noWifi by remember { mutableStateOf(ControleDeDadosMoveis.corteNoWifi()) }
    var naProjecao by remember { mutableStateOf(ControleDeDadosMoveis.corteNaProjecao()) }
    var telemetria by remember { mutableStateOf(ControleDeDadosMoveis.telemetriaBloqueada()) }
    var dia by remember { mutableFloatStateOf(ControleDeDadosMoveis.diaDoCiclo().toFloat()) }
    var teto by remember { mutableFloatStateOf(ControleDeDadosMoveis.tetoDeConsumoMb().toFloat()) }
    var roteador by remember { mutableStateOf(preferenciasDoApp().getBoolean(Preferencias.ENABLE_HOT_ROUTER.chave, false)) }
    var leitura by remember { mutableStateOf(LeituraDaRede()) }

    // A cada 5 s: o motivo do corte e o uso consultam o sistema; o roteador roda shell. Tudo fora da principal.
    LaunchedEffect(controle, roteador) {
        while (true) {
            // Uma exceção aqui (shell, consulta de uso) derrubaria o app inteiro: mantém a leitura anterior.
            val nova = try {
                withContext(Dispatchers.IO) {
                val agora = System.currentTimeMillis()
                LeituraDaRede(
                    texto4g = RegrasDaRede.textoDoCorte(
                        controleLigado = ControleDeDadosMoveis.controleLigado(),
                        motivo = ControleDeDadosMoveis.motivoDoCorte(contexto),
                    ),
                    textoDoUso = RegrasDaRede.textoDoUso(
                        usoMb = ControleDeDadosMoveis.usoNoCicloMb(contexto, agora),
                        tetoMb = ControleDeDadosMoveis.tetoDeConsumoMb(),
                        etiquetaDoCiclo = ControleDeDadosMoveis.etiquetaDoCiclo(agora),
                    ),
                    textoDoRoteador = RegrasDaRede.textoDoRoteador(
                        if (roteador) RoteadorDoHotspot.lerSituacao().modo else ModoDoRoteador.DESLIGADO
                    ),
                    textoDoHotspot = RegrasDaRede.textoDoHotspot(Conectividade.hotspotNoAr),
                )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("OpenGW.Rede", "leitura da aba Rede falhou: ${e.message}")
                null
            }
            if (nova != null) leitura = nova
            delay(5_000)
        }
    }

    ColunaDaTela(respiro = 12.dp, espaco = 16.dp) {
        CartaoDeSecao(
            titulo = "Dados móveis do carro",
            subtitulo = "Corta o 4G da central quando ele não é preciso, por exemplo com o Wi-Fi do celular ou da Starlink.",
        ) {
            LinhaComInterruptor(
                titulo = "Controle dos dados móveis",
                descricao = "Liga as regras abaixo. Desligado, o 4G fica como a fábrica deixou.",
                ligado = controle,
                aoMudar = { controle = it; ControleDeDadosMoveis.definirControleLigado(it) },
            )
            LinhaComInterruptor(
                titulo = "Cortar sempre",
                descricao = "Mantém o 4G da central desligado.",
                ligado = manual,
                aoMudar = { manual = it; ControleDeDadosMoveis.definirCorteManual(it) },
                habilitado = controle,
            )
            LinhaComInterruptor(
                titulo = "Cortar ao passar do teto",
                descricao = "Desliga o 4G quando o uso do ciclo chega ao teto abaixo.",
                ligado = consumo,
                aoMudar = { consumo = it; ControleDeDadosMoveis.definirCorteSobConsumo(it) },
                habilitado = controle,
            )
            LinhaComInterruptor(
                titulo = "Cortar no Wi-Fi",
                descricao = "Desliga o 4G enquanto a central estiver conectada a um Wi-Fi.",
                ligado = noWifi,
                aoMudar = { noWifi = it; ControleDeDadosMoveis.definirCorteNoWifi(it) },
                habilitado = controle,
            )
            LinhaComInterruptor(
                titulo = "Cortar no Android Auto e CarPlay",
                descricao = "Desliga o 4G enquanto um deles estiver projetando (o celular fornece a internet).",
                ligado = naProjecao,
                aoMudar = { naProjecao = it; ControleDeDadosMoveis.definirCorteNaProjecao(it) },
                habilitado = controle,
            )
            HorizontalDivider(color = Cores.linhaFina)
            LinhaDeControleDeslizante(
                rotulo = "Dia de renovação do pacote",
                valor = dia,
                aoMudar = { dia = it.roundToInt().toFloat() },
                faixa = 1f..31f,
                passos = 29,
                habilitado = controle,
                textoDoValor = { "dia ${it.roundToInt()}" },
            )
            LinhaDeControleDeslizante(
                rotulo = "Teto de consumo no ciclo",
                valor = teto,
                aoMudar = { teto = RegrasDaRede.teto(it).toFloat() },
                faixa = 0f..RegrasDaRede.TETO_MAXIMO_MB.toFloat(),
                passos = RegrasDaRede.PASSOS_DO_TETO,
                habilitado = controle,
                textoDoValor = { RegrasDaRede.formatarMb(it.roundToInt().toLong()) },
            )
            // Grava só depois de o dedo parar (0,6 s): a cada passo do arrasto reavaliaria o corte. Na
            // abertura da aba o valor é o gravado e nada é regravado.
            // Sair da aba antes dos 0,6 s cancelaria os dois efeitos abaixo e perderia o ajuste: ao
            // sair, o que ficou pendente é gravado.
            val diaAtual by rememberUpdatedState(dia)
            val tetoAtual by rememberUpdatedState(teto)
            DisposableEffect(Unit) {
                onDispose {
                    val novoDia = diaAtual.roundToInt()
                    if (novoDia != ControleDeDadosMoveis.diaDoCiclo()) ControleDeDadosMoveis.definirDiaDoCiclo(novoDia)
                    val novoTeto = tetoAtual.roundToInt()
                    if (novoTeto != ControleDeDadosMoveis.tetoDeConsumoMb()) ControleDeDadosMoveis.definirTetoDeConsumoMb(novoTeto)
                }
            }
            LaunchedEffect(dia) {
                delay(600)
                val novo = dia.roundToInt()
                if (novo != ControleDeDadosMoveis.diaDoCiclo()) ControleDeDadosMoveis.definirDiaDoCiclo(novo)
            }
            LaunchedEffect(teto) {
                delay(600)
                val novo = teto.roundToInt()
                if (novo != ControleDeDadosMoveis.tetoDeConsumoMb()) ControleDeDadosMoveis.definirTetoDeConsumoMb(novo)
            }
            HorizontalDivider(color = Cores.linhaFina)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(leitura.texto4g, fontSize = 14.sp, color = Cores.textoPrincipal, modifier = Modifier.fillMaxWidth())
                Text(leitura.textoDoUso, fontSize = 13.sp, color = Cores.textoSecundario, modifier = Modifier.fillMaxWidth())
            }
            HorizontalDivider(color = Cores.linhaFina)
            LinhaComInterruptor(
                titulo = "Bloquear a telemetria da fabricante",
                descricao = "Congela o serviço da central que envia dados do carro à nuvem da fabricante. Independe do controle acima.",
                ligado = telemetria,
                aoMudar = { telemetria = it; ControleDeDadosMoveis.definirTelemetriaBloqueada(it) },
            )
        }

        CartaoDeSecao(
            titulo = "Roteador do hotspot",
            subtitulo = "Os aparelhos ligados ao hotspot do carro navegam pelo Wi-Fi externo enquanto ele tiver internet e voltam sozinhos para o 4G quando não tiver.",
        ) {
            LinhaComInterruptor(
                titulo = "Rotear o hotspot pelo Wi-Fi externo",
                descricao = "Precisa do Wi-Fi do carro conectado a uma rede com internet (Starlink, celular).",
                ligado = roteador,
                aoMudar = { roteador = it; RoteadorDoHotspot.definirLigado(it) },
            )
            HorizontalDivider(color = Cores.linhaFina)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(leitura.textoDoRoteador, fontSize = 14.sp, color = Cores.textoPrincipal, modifier = Modifier.fillMaxWidth())
                Text(leitura.textoDoHotspot, fontSize = 13.sp, color = Cores.textoSecundario, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
