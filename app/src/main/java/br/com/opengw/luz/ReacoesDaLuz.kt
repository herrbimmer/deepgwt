package br.com.opengw.luz

import br.com.opengw.nucleo.Preferencia
import br.com.opengw.nucleo.Preferencias

/**
 * O que a iluminação dinâmica segue (Alpha 9.5).
 *
 * ## O PEDIDO
 *
 * "Dou seta, paro de dar seta, mas a luz ambiente não para de piscar. Ela não deve piscar ao dar
 * seta, apenas ao ligar o alerta, e deve ter uma opção de quais ações geram reação da luz."
 *
 * ## POR QUE A LUZ FICAVA PISCANDO
 *
 * O motor do veículo avisa os ouvintes ANTES de gravar o valor novo no cache. O serviço da luz
 * relia a chave do cache dentro do ouvinte e recebia o valor ANTERIOR: a seta
 * desligava ("0"), a luz lia "1" e continuava no pulso até outra chave vigiada mudar. O mesmo valia
 * para o pisca-alerta e o ponto cego. Agora o serviço guarda o valor que chega no próprio aviso
 * e só lê o cache para chave que ainda não chegou.
 *
 * ## AS REAÇÕES
 *
 * Cada uma é um interruptor em Recursos → Ambient Light e no instalador. A seta vem DESLIGADA:
 * piscar a cabine a cada conversão era o incômodo relatado.
 */
enum class ReacaoDaLuz(
    val chave: Preferencia,
    val titulo: String,
    val descricao: String,
    val padrao: Boolean
) {
    PISCA_ALERTA(
        Preferencias.AMBIENT_LIGHT_REACT_HAZARD,
        "Pisca-alerta",
        "Pulsa vermelho enquanto o pisca-alerta estiver ligado.",
        true
    ),
    PONTO_CEGO(
        Preferencias.AMBIENT_LIGHT_REACT_BLIND_SPOT,
        "Alerta de ponto cego",
        "Pulsa laranja enquanto o carro avisar de um veículo no ponto cego.",
        true
    ),
    SETA(
        Preferencias.AMBIENT_LIGHT_REACT_TURN_SIGNAL,
        "Seta",
        "Pulsa âmbar enquanto a seta estiver ligada. Vem desligada.",
        false
    ),
    FAROL(
        Preferencias.AMBIENT_LIGHT_REACT_HEADLIGHT,
        "Farol aceso",
        "Âmbar fixo com o farol aceso, quando nenhum alerta estiver ativo.",
        true
    ),
    MODO_DE_CONDUCAO(
        Preferencias.AMBIENT_LIGHT_REACT_DRIVE_MODE,
        "Modo de condução",
        "Cor de cada modo (Sport vermelho, Eco verde...). Desligado, a base é a cor manual.",
        true
    ),
    CLIMA(
        Preferencias.AMBIENT_LIGHT_REACT_CLIMATE,
        "Temperatura do ar",
        "Uma piscada vermelha ao subir a temperatura pela barra.",
        true
    );

    companion object {
        /** Lê as reações ligadas. Sem nada gravado, cada uma fica no [padrao]. */
        fun ligadas(ler: (chave: String, padrao: Boolean) -> Boolean): Set<ReacaoDaLuz> =
            entries.filterTo(mutableSetOf()) { ler(it.chave.chave, it.padrao) }
    }
}

/** O que está por cima da cor base agora. Ordem de prioridade: de cima para baixo. */
enum class CamadaDaLuz { PISCA_ALERTA, PONTO_CEGO, SETA, FAROL, NENHUMA }

/** Os sinais do carro que a luz acompanha, já decididos em ligado/desligado. */
data class SinaisDaLuz(
    val piscaAlerta: Boolean,
    val pontoCego: Boolean,
    val seta: Boolean,
    val farol: Boolean,
    val modoSport: Boolean
)

object DecisaoDaLuz {
    /**
     * A camada que vence. Só entra o que a pessoa deixou ligado em [reacoes].
     *
     * O pisca-alerta acende as duas lanternas, então as chaves de seta também leem "ligado" com
     * ele: por isso ele vem antes da seta. No Sport o farol não pinta âmbar por cima do vermelho
     * do modo (regra de antes) — mas só quando o modo de condução está pintando a base.
     */
    fun camada(sinais: SinaisDaLuz, reacoes: Set<ReacaoDaLuz>): CamadaDaLuz {
        val sportPintando = sinais.modoSport && ReacaoDaLuz.MODO_DE_CONDUCAO in reacoes
        return when {
            sinais.piscaAlerta && ReacaoDaLuz.PISCA_ALERTA in reacoes -> CamadaDaLuz.PISCA_ALERTA
            // Pisca-alerta ligado com a reação desligada: as setas acendem junto e não podem
            // pulsar no lugar dele.
            sinais.piscaAlerta -> if (sinais.farol && !sportPintando && ReacaoDaLuz.FAROL in reacoes) CamadaDaLuz.FAROL else CamadaDaLuz.NENHUMA
            sinais.pontoCego && ReacaoDaLuz.PONTO_CEGO in reacoes -> CamadaDaLuz.PONTO_CEGO
            sinais.seta && ReacaoDaLuz.SETA in reacoes -> CamadaDaLuz.SETA
            sinais.farol && !sportPintando && ReacaoDaLuz.FAROL in reacoes -> CamadaDaLuz.FAROL
            else -> CamadaDaLuz.NENHUMA
        }
    }
}
