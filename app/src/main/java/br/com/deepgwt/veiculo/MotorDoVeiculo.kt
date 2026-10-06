// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo

import android.content.Context
import android.util.Log
import br.com.deepgwt.nucleo.DeepGwt
import br.com.deepgwt.nucleo.Shell
import br.com.deepgwt.veiculo.motor.AgendaEmThread
import br.com.deepgwt.veiculo.motor.CentralPeloShizuku
import br.com.deepgwt.veiculo.motor.ChavesDoMotor
import br.com.deepgwt.veiculo.motor.DifusorAndroid
import br.com.deepgwt.veiculo.motor.FonteSimulada
import br.com.deepgwt.veiculo.motor.LigarFonteSimulada
import br.com.deepgwt.veiculo.motor.NucleoDoMotor
import br.com.deepgwt.veiculo.motor.RegistroNoLogcat
import com.beantechs.intelligentvehiclecontrol.sdk.IListener
import com.beantechs.voice.adapter.IDvr
import com.beantechs.voice.adapter.IVehicle
import com.beantechs.voice.adapter.IVehicleModel

/**
 * O motor próprio do veículo (módulo 3.3a): a ligação de dados com o serviço de controle da central.
 *
 * Escrito em sala limpa a partir de `docs/independencia/specs/motor-nucleo.md`. A lógica fica em
 * [NucleoDoMotor] (sem Android, testada na JVM); aqui só se liga ela ao Android, ao Shizuku e à
 * central.
 *
 * Ligado: é o único registrado na central (ela guarda um ouvinte por nome de pacote); o ciclo de vida
 * que o chama é o do serviço principal ([br.com.deepgwt.veiculo.sistema.MotorCompleto]).
 */
object MotorDoVeiculo {

    private const val TAG = "DeepGWT.Motor"
    private const val ARQUIVO_DE_PREFERENCIAS = "deepgwt_prefs"
    private const val CHAVES_DA_PESSOA = "carMonitorProperties"

    /** Posição do porta-malas em `car.basic.door_status` (§4). */
    const val POSICAO_DO_PORTA_MALAS: Int = 5

    @Volatile private var contextoDoApp: Context? = null

    /** Um objeto ouvinte só, para a vida toda do processo (A.8). */
    private val ouvinteDaCentral: IListener by lazy { OuvinteDaCentral() }

    private val central = CentralPeloShizuku({ ouvinteDaCentral }, RegistroNoLogcat)

    private val nucleo: NucleoDoMotor by lazy {
        NucleoDoMotor(
            central = central,
            agenda = AgendaEmThread("DeepGWT-Motor", RegistroNoLogcat),
            difusor = DifusorAndroid(::contexto),
            registro = RegistroNoLogcat,
            shell = { comando -> Shell.executar(comando) },
            lerChavesDoUsuario = ::chavesDaPessoa,
            catalogo = { Sinais.TODOS.map { it.chave } },
            ligarFonteSimulada = LigarFonteSimulada { injetar ->
                FonteSimulada(AgendaEmThread("DeepGWT-Simulador", RegistroNoLogcat), injetar).ligar()
            },
        )
    }

    // ── Partida e fim ──

    /**
     * Liga o canal com a central (A). Bloqueante (até ~1,5 s esperando o Shizuku, mais as chamadas):
     * chamar fora da thread principal. Pode ser chamada de novo (re-partida).
     */
    @JvmStatic
    fun partir(contexto: Context): Boolean {
        contextoDoApp = contexto.applicationContext ?: contexto
        return nucleo.partir(contexto.packageName)
    }

    /** Partida do build `simulator` (B): sem Shizuku nem central. */
    @JvmStatic
    fun partirSimulado(contexto: Context) {
        contextoDoApp = contexto.applicationContext ?: contexto
        nucleo.partirSimulado()
    }

    /** Para o vigia e desfaz o aviso de morte (J). Não desregistra o ouvinte na central. */
    @JvmStatic
    fun encerrar() = nucleo.encerrar()

    // ── Ganchos para o código herdado ──

    /** Gancho único dos eventos crus do carro, chamado depois dos ouvintes e do cache; null retira. */
    @JvmStatic
    fun aoChegarDoCarro(gancho: Veiculo.Ouvinte?) {
        nucleo.ganchoDoCarro = gancho
    }

    /** Diz o pacote do app na frente do display 0 (camada do ar, D.6). */
    @JvmStatic
    var appNaFrenteDoDisplay0: (() -> String?)?
        get() = nucleo.appNaFrente
        set(valor) {
            nucleo.appNaFrente = valor
        }

    /** Diário persistente de diagnóstico: eventos do canal e da camada do ar. */
    @JvmStatic
    var diario: ((evento: String, detalhes: Map<String, Any?>) -> Unit)?
        get() = nucleo.diario
        set(valor) {
            nucleo.diario = valor
        }

    // ── Alças da voz (null se indisponíveis). Se uma morreu, tenta pegar de novo antes. ──

    @JvmStatic
    val vozVeiculo: IVehicle?
        get() {
            nucleo.garantirVoz()
            return central.vozVeiculo
        }

    @JvmStatic
    val vozDvr: IDvr?
        get() {
            nucleo.garantirVoz()
            return central.vozDvr
        }

    @JvmStatic
    val vozModelo: IVehicleModel?
        get() {
            nucleo.garantirVoz()
            return central.vozModelo
        }

    // ── Cache e chaves ──

    /** Só o cache; nunca vai ao carro (C.6). */
    @JvmStatic
    fun espiar(chave: String): String? = nucleo.espiar(chave)

    /** Chaves acompanhadas sempre (Apêndice B). */
    @JvmStatic
    val chavesPadrao: List<String> get() = ChavesDoMotor.ACOMPANHADAS_SEMPRE

    /** Pede de novo a lista da pessoa, apaga o cache e refaz o retrato (E.5). */
    @JvmStatic
    fun recarregarChavesDoUsuario() = nucleo.recarregarChavesDoUsuario()

    /** Entra exatamente como um evento vindo do carro (B.6). */
    @JvmStatic
    fun injetar(chave: String, valor: String) = nucleo.injetar(chave, valor)

    // ── Instantes (uptime, ms; 0 = ainda não) ──

    /** Só grava se ainda for 0. */
    @JvmStatic
    fun marcarBoot(uptimeMs: Long) = nucleo.marcarBoot(uptimeMs)

    @JvmStatic
    val instanteDoBoot: Long get() = nucleo.instanteDoBoot

    @JvmStatic
    val inicioDaPartida: Long get() = nucleo.inicioDaPartida

    @JvmStatic
    val fimDaPartida: Long get() = nucleo.fimDaPartida

    // ── O que `Veiculo` precisa (mesmo comportamento; a troca do corpo é da sala suja) ──

    /** C.1: cache; na falta e com canal vivo, pergunta ao carro uma vez e guarda. */
    @JvmStatic
    fun ler(chave: String): String? = nucleo.ler(chave)

    /** C.2. */
    @JvmStatic
    fun lerDoCarro(chave: String): String? = nucleo.lerDoCarro(chave)

    /** C.3. */
    @JvmStatic
    fun lerDoCarro(chaves: Array<String>): Map<String, String> = nucleo.lerDoCarro(chaves)

    /** C.4. */
    @JvmStatic
    fun lerTudoDoCarro(): Map<String, String> = nucleo.lerTudoDoCarro()

    /** C.5: inclui as chaves lembradas "sem valor", com null. */
    @JvmStatic
    fun valoresConhecidos(): Map<String, String?> = nucleo.valoresConhecidos()

    /** D.3. */
    @JvmStatic
    fun escrever(chave: String, valor: String) = nucleo.escrever(chave, valor)

    /** D.2. */
    @JvmStatic
    fun escreverConferindo(chave: String, valor: String): Boolean = nucleo.escreverConferindo(chave, valor)

    /** E.2/E.3. */
    @JvmStatic
    fun acompanhar(chaves: Collection<String>) = nucleo.acompanhar(chaves)

    @JvmStatic
    fun observar(ouvinte: Veiculo.Ouvinte) = nucleo.observar(ouvinte)

    @JvmStatic
    fun pararDeObservar(ouvinte: Veiculo.Ouvinte) = nucleo.pararDeObservar(ouvinte)

    /** H.3. */
    @JvmStatic
    val controleVivo: Boolean get() = nucleo.controleVivo

    /** O motor está ligado à central de verdade (não simulado). */
    @JvmStatic
    val modoReal: Boolean get() = nucleo.modoReal

    /** H.1. */
    @JvmStatic
    val pronto: Boolean get() = nucleo.pronto

    /** H.2. */
    @JvmStatic
    fun quandoPronto(tarefa: Runnable) = nucleo.quandoPronto(tarefa)

    /** C.8. */
    @JvmStatic
    fun reenviarTudo() = nucleo.reenviarTudo()

    /** F.4. */
    @JvmStatic
    fun publicar(chave: String, valor: String) = nucleo.publicar(chave, valor)

    // ── Interno ──

    private fun contexto(): Context? =
        contextoDoApp ?: try {
            DeepGwt.contexto
        } catch (e: Exception) {
            null
        }

    /** (b) de A.8: `carMonitorProperties` em `deepgwt_prefs`, no armazenamento protegido. */
    private fun chavesDaPessoa(): Set<String> {
        val protegido = try {
            DeepGwt.contextoProtegido
        } catch (e: Exception) {
            contexto()?.createDeviceProtectedStorageContext()
        } ?: return emptySet()
        return protegido.getSharedPreferences(ARQUIVO_DE_PREFERENCIAS, Context.MODE_PRIVATE)
            .getStringSet(CHAVES_DA_PESSOA, null)
            ?.toSet()
            ?: emptySet()
    }

    /** Os eventos chegam aqui numa thread de binder, um de cada vez (G.5). */
    private class OuvinteDaCentral : IListener.Stub() {
        override fun onDataChanged(key: String?, value: String?) {
            try {
                nucleo.chegouDoCarro(key, value)
            } catch (e: Exception) {
                Log.e(TAG, "falhou tratando o evento $key", e)
            }
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
