package br.com.opengw.ui.screens

import br.com.opengw.ui.Fontes
import br.com.opengw.ui.AjustesAgrupados
import br.com.opengw.ui.ItemDeAjuste
import br.com.opengw.ui.Cores
import br.com.opengw.nucleo.OpenGw
import br.com.opengw.veiculo.Veiculo
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import br.com.opengw.veiculo.Sinal
import br.com.opengw.veiculo.Sinais
import br.com.opengw.nucleo.Preferencias
import br.com.opengw.ui.liquidGlass

/**
 * Tela de ajustes ADAS. Os valores por trás de boa parte destas chaves nunca foram
 * confirmados na prática (só o padrão "0"/"1" foi validado para AVAS/DMS/ESP) — cada
 * toggle é best-effort e precisa ser validado no carro real antes de confiar nele.
 */
private data class AdasToggle(
    val key: Sinal,
    val title: String,
    val description: String,
    val group: String
)

private object AdasGroups {
    const val FAIXA = "Assistente de faixa"
    const val COLISAO = "Prevenção de colisão"
    const val CRUZEIRO = "Piloto automático & rodovia"
    const val ESTACIONAMENTO = "Estacionamento assistido"
    const val OUTROS = "Outros avisos"
    val ORDER = listOf(FAIXA, COLISAO, CRUZEIRO, ESTACIONAMENTO, OUTROS)
}

private val ADAS_TOGGLES = listOf(
    AdasToggle(
        Sinais.CAR_INTELLIGENT_DRIVING_SETTING_LAS_ENABLE,
        "Sistemas de assistência de faixa (LAS)",
        "Chave geral dos recursos de faixa abaixo",
        AdasGroups.FAIXA
    ),
    AdasToggle(
        Sinais.CAR_INTELLIGENT_DRIVING_SETTING_LAS_LKA_STATE,
        "Manutenção de faixa (LKA)",
        "Corrige a direção para manter o carro centralizado na faixa",
        AdasGroups.FAIXA
    ),
    AdasToggle(
        Sinais.CAR_INTELLIGENT_DRIVING_SETTING_LAS_LDW_STATE,
        "Aviso de saída de faixa (LDW)",
        "Alerta sonoro/visual ao sair da faixa sem sinalizar",
        AdasGroups.FAIXA
    ),
    AdasToggle(
        Sinais.CAR_INTELLIGENT_DRIVING_SETTING_LAS_LCA_STATE,
        "Assistente de troca de faixa (LCA)",
        "Ajuda a manobrar o carro ao trocar de faixa",
        AdasGroups.FAIXA
    ),
    AdasToggle(
        Sinais.CAR_INTELLIGENT_DRIVING_SETTING_LAS_ELK_STATE,
        "Manutenção de faixa de emergência (ELK)",
        "Correção mais brusca perto de barreiras ou faixa contínua",
        AdasGroups.FAIXA
    ),
    AdasToggle(
        Sinais.CAR_INTELLIGENT_DRIVING_SETTING_LAS_TSI_STATE,
        "Reconhecimento de placas (TSI)",
        "Exibe/alerta o limite de velocidade lido nas placas",
        AdasGroups.FAIXA
    ),
    AdasToggle(
        Sinais.CAR_INTELLIGENT_DRIVING_SETTING_EAS_CHANGE_LANE_ASSIST_STATE,
        "Assistente de mudança de faixa (EAS)",
        "Auxílio ativo ao mudar de faixa em rodovia",
        AdasGroups.FAIXA
    ),
    AdasToggle(
        Sinais.CAR_INTELLIGENT_DRIVING_SETTING_FAS_PCS_STATE,
        "Frenagem de pré-colisão (PCS)",
        "Freia automaticamente ao detectar risco de colisão frontal",
        AdasGroups.COLISAO
    ),
    AdasToggle(
        Sinais.CAR_INTELLIGENT_DRIVING_SETTING_FAS_PPS_STATE,
        "Proteção de pedestres (PPS)",
        "Detecção e frenagem para pedestres e ciclistas",
        AdasGroups.COLISAO
    ),
    AdasToggle(
        Sinais.CAR_INTELLIGENT_DRIVING_SETTING_FAS_AUTO_EMERGENCY_TURN,
        "Desvio de emergência automático",
        "Manobra evasiva automática em risco iminente de colisão",
        AdasGroups.COLISAO
    ),
    AdasToggle(
        Sinais.CAR_INTELLIGENT_DRIVING_SETTING_FAS_FRONT_CROSS_LATERAL_BRAKE,
        "Frenagem de cruzamento frontal",
        "Freia para tráfego cruzando à frente em cruzamentos",
        AdasGroups.COLISAO
    ),
    AdasToggle(
        Sinais.CAR_INTELLIGENT_DRIVING_SETTING_FAS_FRONT_CROSS_LATERAL_WRANING,
        "Aviso de cruzamento frontal",
        "Alerta de tráfego cruzando à frente em cruzamentos",
        AdasGroups.COLISAO
    ),
    AdasToggle(
        Sinais.CAR_INTELLIGENT_DRIVING_SETTING_FAS_INTERSECTION_ASSIST_STATE,
        "Assistente de interseção",
        "Auxílio em cruzamentos e conversões",
        AdasGroups.COLISAO
    ),
    AdasToggle(
        Sinais.CAR_INTELLIGENT_DRIVING_SETTING_SRAS_RCW_STATE,
        "Aviso de colisão traseira (RCW)",
        "Alerta de risco de colisão traseira",
        AdasGroups.COLISAO
    ),
    AdasToggle(
        Sinais.CAR_INTELLIGENT_DRIVING_SETTING_SRAS_RSA_STATE,
        "Assistente de saída traseira (RSA)",
        "Auxílio ao sair de ré de vagas/garagens",
        AdasGroups.COLISAO
    ),
    AdasToggle(
        Sinais.CAR_INTELLIGENT_DRIVING_SETTING_SRAS_RSA_RSB_STATE,
        "Frenagem de saída traseira (RSA/RSB)",
        "Frenagem automática ao sair de ré com risco de colisão",
        AdasGroups.COLISAO
    ),
    AdasToggle(
        Sinais.CAR_INTELLIGENT_DRIVING_SETTING_SRAS_RSA_RSB_WARNING_STATE,
        "Aviso de saída traseira (RSA/RSB)",
        "Alerta ao sair de ré com risco de colisão",
        AdasGroups.COLISAO
    ),
    AdasToggle(
        Sinais.CAR_INTELLIGENT_DRIVING_SETTING_SRAS_ALA_STATE,
        "Alerta de área lateral (ALA)",
        "Detecção de objetos/veículos nas laterais",
        AdasGroups.COLISAO
    ),
    AdasToggle(
        Sinais.CAR_INTELLIGENT_DRIVING_SETTING_SRAS_DOOR_OPEN_WARNING,
        "Aviso ao abrir a porta",
        "Alerta de tráfego se aproximando ao abrir a porta",
        AdasGroups.COLISAO
    ),
    AdasToggle(
        Sinais.CAR_INTELLIGENT_DRIVING_SETTING_SMART_DODGE_STATE,
        "Desvio inteligente",
        "Assistência de desvio inteligente de obstáculos",
        AdasGroups.COLISAO
    ),
    AdasToggle(
        Sinais.CAR_INTELLIGENT_DRIVING_SETTING_OVER_SPEED_WARNING_STATE,
        "Aviso de excesso de velocidade",
        "Alerta ao ultrapassar o limite configurado",
        AdasGroups.CRUZEIRO
    ),
    AdasToggle(
        Sinais.CAR_INTELLIGENT_DRIVING_SETTING_NOH_STATUS,
        "Piloto em rodovia (NOH)",
        "Navegação assistida em rodovia conectada ao GPS",
        AdasGroups.CRUZEIRO
    ),
    AdasToggle(
        Sinais.CAR_INTELLIGENT_DRIVING_SETTING_NOH_FOLLOW_NAVIGATION,
        "NOH segue a navegação",
        "O piloto de rodovia segue a rota traçada no GPS",
        AdasGroups.CRUZEIRO
    ),
    AdasToggle(
        Sinais.CAR_INTELLIGENT_DRIVING_SETTING_NOH_AUXILIARY_NAVIGATION,
        "NOH com navegação auxiliar",
        "Usa dados de navegação auxiliares no piloto de rodovia",
        AdasGroups.CRUZEIRO
    ),
    AdasToggle(
        Sinais.CAR_INTELLIGENT_DRIVING_SETTING_VEHICLE_TO_EVERYTHING_ENABLE,
        "Comunicação V2X",
        "Comunicação do veículo com infraestrutura e outros veículos",
        AdasGroups.CRUZEIRO
    ),
    AdasToggle(
        Sinais.CAR_INTELLIGENT_DRIVING_SETTING_WISDOM_CORNERING_ENABLE,
        "Curva inteligente",
        "Ajuste automático de velocidade/assistência em curvas",
        AdasGroups.CRUZEIRO
    ),
    AdasToggle(
        Sinais.CAR_PARKING_SETTING_AUTO_PARK_ENABLE,
        "Estacionamento automático",
        "Habilita a manobra automática para estacionar",
        AdasGroups.ESTACIONAMENTO
    ),
    AdasToggle(
        Sinais.CAR_PARKING_SETTING_AUTO_STEERING_ENABLE,
        "Direção automática ao estacionar",
        "Controle automático do volante durante a manobra",
        AdasGroups.ESTACIONAMENTO
    ),
    AdasToggle(
        Sinais.CAR_PARKING_SETTING_APA_FLANKING_PROTECTION,
        "Proteção lateral (APA)",
        "Proteção contra colisão lateral durante manobras",
        AdasGroups.ESTACIONAMENTO
    ),
    AdasToggle(
        Sinais.CAR_PARKING_SETTING_FPAS_ENABLE,
        "Assistência de estacionamento frontal (FPAS)",
        "Assistência de manobra usando os sensores frontais",
        AdasGroups.ESTACIONAMENTO
    ),
    AdasToggle(
        Sinais.CAR_PARKING_SETTING_RADAR_ALARM_SOUND_ENABLE,
        "Som dos sensores de estacionamento",
        "Bipe sonoro dos sensores de proximidade",
        AdasGroups.ESTACIONAMENTO
    ),
    AdasToggle(
        Sinais.CAR_PARKING_SETTING_RADAR_SETTING_MEB_STATE,
        "Frenagem automática ao manobrar (MEB)",
        "Freia automaticamente ao detectar obstáculo durante a manobra",
        AdasGroups.ESTACIONAMENTO
    ),
    AdasToggle(
        Sinais.CAR_PARKING_SETTING_AVM_SETTING_AVM_AUTO_ACTIVE,
        "Câmera 360° automática",
        "Abre a câmera 360° automaticamente ao manobrar",
        AdasGroups.ESTACIONAMENTO
    ),
    AdasToggle(
        Sinais.CAR_INTELLIGENT_DRIVING_SETTING_EAS_WARNING_WAY,
        "Forma de aviso do EAS",
        "Alterna o tipo de alerta do assistente de faixa (valor a confirmar no carro)",
        AdasGroups.OUTROS
    )
)

@Composable
fun AdasSettingsScreen() {
    val prefs = remember {
        OpenGw.contextoProtegido.getSharedPreferences("opengw_prefs", Context.MODE_PRIVATE)
    }
    var acknowledged by remember {
        mutableStateOf(prefs.getBoolean(Preferencias.ADAS_DISCLAIMER_ACKNOWLEDGED.chave, false))
    }

    if (!acknowledged) {
        AdasDisclaimer(onAccept = {
            prefs.edit { putBoolean(Preferencias.ADAS_DISCLAIMER_ACKNOWLEDGED.chave, true) }
            acknowledged = true
        })
        return
    }

    val dataMap = remember {
        mutableStateMapOf<String, String>().apply {
            Veiculo.valoresConhecidos().forEach { (k, v) -> put(k, v ?: "") }
        }
    }
    DisposableEffect(Unit) {
        val listener = Veiculo.Ouvinte { key, value -> dataMap[key] = value ?: "" }
        Veiculo.observar(listener)
        onDispose { Veiculo.pararDeObservar(listener) }
    }

    val items = ADAS_TOGGLES.map { toggle ->
        ItemDeAjuste(
            titulo = toggle.title,
            descricao = toggle.description,
            ligado = dataMap[toggle.key.chave] == "1",
            aoMudar = { checked ->
                val valor = if (checked) "1" else "0"
                // Escolha feita aqui é da pessoa: a memória dos assistentes (Alpha 8.5) passa a
                // lembrar este valor, mesmo logo depois da partida.
                br.com.opengw.carro.MemoriaDosAssistentes.aoEscolherNoApp(toggle.key.chave, valor)
                Veiculo.escrever(toggle.key.chave, valor)
            },
            grupo = toggle.group
        )
    }

    AjustesAgrupados(itens = items, ordemDosGrupos = AdasGroups.ORDER, titulo = "ADAS")
}

@Composable
private fun AdasDisclaimer(onAccept: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .liquidGlass()
                .padding(28.dp)
        ) {
            Text(
                text = "Antes de continuar",
                fontFamily = Fontes.texto,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                color = Cores.textoPrincipal
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Estes ajustes controlam sistemas de segurança ativa do veículo (assistente de " +
                    "faixa, frenagem de emergência, alertas de colisão, estacionamento assistido, etc). " +
                    "Desativar algum deles pode reduzir a segurança do carro e, dependendo da sua " +
                    "legislação local, ter implicações legais.\n\n" +
                    "O valor exato que cada ajuste espera não foi confirmado por documentação oficial — " +
                    "só o padrão \"ligado/desligado\" já validado em outras funções deste app. Teste cada " +
                    "opção com cuidado no seu carro antes de confiar nela.",
                fontFamily = Fontes.texto,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = Cores.textoSecundario
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(containerColor = Cores.destaque),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Entendi, continuar", color = Cores.sobreODestaque, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
