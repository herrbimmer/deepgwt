// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.rede

import android.content.Context
import br.com.deepgwt.nucleo.DeepGwt
import br.com.deepgwt.telas.Espelhamento
import java.time.ZoneId

/**
 * Controle dos dados móveis (4G) do carro e bloqueio da telemetria da fabricante
 * (`docs/independencia/specs/extras-rede.md`, seções A e B). A lógica está em
 * [NucleoDosDadosMoveis]; aqui ficam só as ligações com o Android.
 *
 * O parâmetro `contexto` dos métodos é o do app (há um só); a lógica usa [DeepGwt.contexto].
 */
object ControleDeDadosMoveis {

    private val nucleo: NucleoDosDadosMoveis by lazy {
        NucleoDosDadosMoveis(
            armazem = ArmazemDePreferencias(),
            shell = ExecutorDoModulo,
            registro = RegistroDaRedeNoLogcat,
            relogio = RelogioDoSistema,
            fuso = { ZoneId.systemDefault() },
            medidor = MedidorDeUsoAndroid,
            wifiConectadoAgora = { RedeAndroid.wifiConectado(DeepGwt.contexto) },
            projecaoAtivaAgora = { Espelhamento.noDisplay(0) || Espelhamento.noDisplay(3) },
            pacoteDoApp = { DeepGwt.contexto.packageName },
            fundo = threadUnicaDeFundo("DeepGWT-Rede-DadosMoveis"),
        )
    }

    // Interruptores. Cada definir*: grava, pede a permissão de uso se couber e reavalia.
    @JvmStatic fun controleLigado(): Boolean = nucleo.controleLigado()

    /** Grava, avisa o ouvinte de [aoMudarControle], pede a permissão de uso e reavalia. */
    @JvmStatic fun definirControleLigado(ligado: Boolean) = nucleo.definirControleLigado(ligado)

    @JvmStatic fun corteManual(): Boolean = nucleo.corteManual()

    @JvmStatic fun definirCorteManual(ligado: Boolean) = nucleo.definirCorteManual(ligado)

    @JvmStatic fun corteSobConsumo(): Boolean = nucleo.corteSobConsumo()

    @JvmStatic fun definirCorteSobConsumo(ligado: Boolean) = nucleo.definirCorteSobConsumo(ligado)

    @JvmStatic fun corteNoWifi(): Boolean = nucleo.corteNoWifi()

    @JvmStatic fun definirCorteNoWifi(ligado: Boolean) = nucleo.definirCorteNoWifi(ligado)

    @JvmStatic fun corteNaProjecao(): Boolean = nucleo.corteNaProjecao()

    @JvmStatic fun definirCorteNaProjecao(ligado: Boolean) = nucleo.definirCorteNaProjecao(ligado)

    /** Quem quiser saber do mestre (a ligação ao motor). Chamado por [definirControleLigado]. */
    @JvmStatic fun aoMudarControle(ouvinte: ((Boolean) -> Unit)?) = nucleo.aoMudarControle(ouvinte)

    // Configuração do ciclo e do teto.
    @JvmStatic fun diaDoCiclo(): Int = nucleo.diaDoCiclo()

    @JvmStatic fun tetoDeConsumoMb(): Int = nucleo.tetoDeConsumoMb()

    @JvmStatic fun definirDiaDoCiclo(dia: Int) = nucleo.definirDiaDoCiclo(dia)

    @JvmStatic fun definirTetoDeConsumoMb(mb: Int) = nucleo.definirTetoDeConsumoMb(mb)

    // Estado de execução.
    @JvmStatic fun wifiConectado(contexto: Context): Boolean = RedeAndroid.wifiConectado(contexto)

    @JvmStatic fun projecaoAtiva(): Boolean = nucleo.projecaoAtiva()

    /** Null = não está cortando. Pode consultar o uso (rápido, sem shell). */
    @JvmStatic fun motivoDoCorte(contexto: Context): MotivoDoCorte? = nucleo.motivoDoCorte()

    // Ciclo e uso.
    @JvmStatic fun inicioDoCiclo(agoraMs: Long): Long = nucleo.inicioDoCiclo(agoraMs)

    @JvmStatic fun etiquetaDoCiclo(agoraMs: Long): String = nucleo.etiquetaDoCiclo(agoraMs)

    @JvmStatic fun usoNoCicloBytes(contexto: Context, agoraMs: Long): Long = nucleo.usoNoCicloBytes(agoraMs)

    @JvmStatic fun usoNoCicloMb(contexto: Context, agoraMs: Long): Long = nucleo.usoNoCicloMb(agoraMs)

    /** Reavalia as regras e aplica o corte ou a liberação. Volta na hora, de qualquer thread. */
    @JvmStatic fun reavaliar(contexto: Context) = nucleo.reavaliar()

    /** Concede (best-effort, em segundo plano) a permissão de leitura de uso de rede. */
    @JvmStatic fun garantirPermissaoDeUso(contexto: Context) = nucleo.garantirPermissaoDeUso()

    // Telemetria da fabricante (independente do mestre).
    @JvmStatic fun telemetriaBloqueada(): Boolean = nucleo.telemetriaBloqueada()

    /** Grava e aplica. */
    @JvmStatic fun definirTelemetriaBloqueada(bloquear: Boolean) = nucleo.definirTelemetriaBloqueada(bloquear)

    /** Só aplica (partida da guarda). */
    @JvmStatic fun reaplicarTelemetria() = nucleo.reaplicarTelemetria()
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
