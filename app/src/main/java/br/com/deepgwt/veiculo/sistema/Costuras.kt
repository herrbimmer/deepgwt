// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.sistema

import android.content.Context
import br.com.deepgwt.veiculo.Veiculo
import br.com.deepgwt.veiculo.automacoes.GanchosDePartida
import br.com.deepgwt.veiculo.comandos.AvisoDoArNoMaximo
import br.com.deepgwt.veiculo.ProjecaoNasTelas

/*
 * O que o motor (módulo 3.3e, `docs/independencia/specs/motor-sistema.md`) recebe de fora. Tudo tem
 * um valor vazio, usado até a sala suja ligar o adaptador.
 */

/** "O nível atual permite mexer no sistema?" (código nosso em `licenca/`). */
fun interface PortaoDeLicenca {
    fun permiteSistema(): Boolean
}

/** A guarda de dados móveis (extra herdado, fora desta parte). */
interface DadosMoveis {
    /** Na partida real. */
    fun iniciar()

    /** No encerramento e antes de cada re-partida. */
    fun parar()

    fun aoMudarControle(ligado: Boolean)

    companion object {
        @JvmField
        val VAZIO: DadosMoveis = object : DadosMoveis {
            override fun iniciar() {}
            override fun parar() {}
            override fun aoMudarControle(ligado: Boolean) {}
        }
    }
}

/**
 * O serviço do painel da fabricante e o serviço de teclas do volante (parte 3.3d, ainda não escrita).
 * O ciclo de vida só liga e desliga os dois, nesta ordem; quem implementar o 3.3d (ou a sala suja)
 * entrega um objeto que chama `ServicoDoPainel.ligar/desligar` e `Volante.ligar/desligar`.
 * `desligar` tem de desregistrar o ouvinte de teclas e o retorno do painel (senão teclas em dobro).
 */
interface PainelEVolante {
    fun ligar(contexto: Context)

    fun desligar(contexto: Context)

    companion object {
        @JvmField
        val VAZIO: PainelEVolante = object : PainelEVolante {
            override fun ligar(contexto: Context) {}
            override fun desligar(contexto: Context) {}
        }
    }
}

/**
 * O que vem de fora do motor (a sala suja liga; tudo tem valor vazio). Difere da proposta da
 * especificação só no que é do 3.3d: em vez de coringas, HUD, círculo, mãos livres e menu antigo
 * (tipos que o 3.3d define), vem [painelEVolante] já montado; e há [avisoDoArNoMaximo] para o
 * barramento do painel.
 */
class LigacoesDoMotor(
    val projecao: ProjecaoNasTelas = ProjecaoNasTelas.VAZIA,
    val ganchos: GanchosDePartida = GanchosDePartida.VAZIO,
    val painelEVolante: PainelEVolante = PainelEVolante.VAZIO,
    val avisoDoArNoMaximo: AvisoDoArNoMaximo = AvisoDoArNoMaximo { },
    val portao: PortaoDeLicenca = PortaoDeLicenca { true },
    val dadosMoveis: DadosMoveis = DadosMoveis.VAZIO,
    val appNaFrenteDoDisplay0: () -> String? = { null },
    val diario: (String, Map<String, Any?>) -> Unit = { _, _ -> },
    /** "pacote/.Classe" do serviço de acessibilidade do app (3.3b §I.1). Null: a lista não é mexida. */
    val componenteDaAcessibilidade: String? = null,
    /** Ouvintes comuns registrados uma vez por processo (ex.: o mapeador de fluxo de potência). */
    val ouvintesFixos: List<Veiculo.Ouvinte> = emptyList(),
)

/**
 * Decisões do dono que a especificação deixa abertas (motor-sistema.md §8). A especificação não
 * trouxe "decisões da sala suja" para esta parte; os padrões seguem a recomendação de cada uma.
 */
data class DecisoesDoSistema(
    /**
     * D-S2: "desligar hotspot guardando" e a volta usam o hotspot no ar de verdade (sysfs), porque
     * `getWifiApState()` devolve -1 nesta central. Falso = como hoje (nunca guarda nada).
     */
    val hotspotPeloSysfs: Boolean = true,
)

/** O adaptador Bluetooth da central. */
internal fun interface BluetoothDoCarro {
    /** `BluetoothManager.getAdapter().isEnabled()`; pode lançar. */
    fun ligado(): Boolean
}

/** O tethering Wi-Fi da central (serviço `connectivity` pelo Shizuku). */
internal interface HotspotDoCarro {
    /** `WifiManager.getWifiApState()` por reflexão; null se não devolveu inteiro ou lançou. */
    fun estadoPorReflexao(): Int?

    /** `startTethering(0, …)`. False = sem a alça do `connectivity`. */
    fun ligar(): Boolean

    /** `stopTethering(0, …)` (ou a forma antiga). False = sem a alça do `connectivity`. */
    fun desligar(): Boolean
}

/** Os passos do ciclo de vida que dependem de fora (núcleo, 3.3b, 3.3d, conectividade). */
internal interface PortasDoCiclo<C> {
    fun partirNucleo(contexto: C): Boolean
    fun partirNucleoSimulado(contexto: C)
    fun encerrarNucleo()
    fun ligarGanchoDoCarro()
    fun marcarBootNoNucleo(uptimeMs: Long)

    fun ligarPainelEVolante(contexto: C)
    fun desligarPainelEVolante(contexto: C)

    fun iniciarDadosMoveis()
    fun pararDadosMoveis()

    fun ajustesDoAndroidNaPartida(contexto: C)
    fun automacoesDePartida(real: Boolean)
    fun automacoesDepoisDoPronto()
    fun encerrarAutomacoes()

    fun prepararConectividade(contexto: C)
    fun semearHotspot()
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
