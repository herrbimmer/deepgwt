// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.painel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import br.com.deepgwt.veiculo.ProjecaoNasTelas
import com.autolink.cluster.ClusterMsgData
import com.autolink.clusterservice.IClusterCallback
import com.autolink.clusterservice.IClusterService

/**
 * O serviço do painel de instrumentos da fabricante (`com.autolink.clusterservice`): cartão na tela,
 * "Android pronto", pulso de vida e comandos de mídia do painel (`motor-painel-e-volante.md` §A).
 *
 * Escrito em sala limpa. A lógica fica em [NucleoDoServicoDoPainel] e [LigacaoDoPainel] (sem
 * Android, testadas na JVM); aqui só se liga ela ao `bindService` e às classes da fabricante.
 *
 * Ligado: o ciclo de vida do motor chama [ligar]; só há um tratador registrado no serviço do painel.
 */
object ServicoDoPainel {

    private const val PACOTE = "com.autolink.clusterservice"
    private const val CLASSE = "com.autolink.clusterservice.ClusterService"

    private val registro = RegistroNoLogcat("DeepGWT.Painel")

    internal val nucleo: NucleoDoServicoDoPainel by lazy {
        NucleoDoServicoDoPainel(
            relogio = RelogioDoAparelho,
            registro = registro,
            diario = DiarioDoMotor,
            preferencias = PreferenciasDoApp,
            publicador = EventosDoPainel.barramento,
        )
    }

    /** O retorno do painel: um objeto só para a vida toda do processo (A.1). */
    private val retorno: IClusterCallback.Stub by lazy { RetornoDoPainel() }

    private val conector = ConectorAndroid<ServicoRemotoDoPainel>(
        nome = "painel",
        intencao = { Intent().setComponent(ComponentName(PACOTE, CLASSE)) },
        converter = { binder -> InterfaceDoPainel(IClusterService.Stub.asInterface(binder)) },
        registro = registro,
    )

    private val ligacao: LigacaoDoPainel by lazy {
        LigacaoDoPainel(
            nucleo = nucleo,
            conector = conector,
            novaLinha = { LinhaEmThread("DeepGWT-Painel", registro) },
            preferencias = PreferenciasDoApp,
            registro = registro,
        )
    }

    /** Liga as dependências de fora (ciclo de vida, 3.3e §G.1). Sem isto, valem as vazias. */
    @JvmStatic
    fun instalar(projecao: ProjecaoNasTelas, menuAntigo: MenuAntigoDoPainel) {
        nucleo.projecao = projecao
        nucleo.menuAntigo = menuAntigo
    }

    /** Liga-se ao serviço da fabricante (A.1). Desfaz uma ligação anterior antes. Volta na hora. */
    @JvmStatic
    fun ligar(contexto: Context) {
        conector.contexto = contexto.applicationContext ?: contexto
        ligacao.ligar()
    }

    /** Desregistra o retorno e desfaz a ligação (A.8). */
    @JvmStatic
    @Suppress("UNUSED_PARAMETER")
    fun desligar(contexto: Context) {
        // A ligação é desfeita no mesmo contexto em que foi feita (guardado pelo conector).
        ligacao.desligar()
    }

    /** Cartão que o painel mostra agora (0, 1 ou 3; começa 0). Qualquer thread. */
    @JvmStatic
    val cartaoAtual: Int
        get() = nucleo.cartaoAtual

    /** Pulso de vida (A.4). Volta na hora. (= `Comandos.iniciarPulsoDoPainel`) */
    @JvmStatic
    fun iniciarPulso() = nucleo.iniciarPulso()

    /** As mensagens do painel chegam aqui numa thread de binder, uma de cada vez (A.2). */
    private class RetornoDoPainel : IClusterCallback.Stub() {
        override fun callbackMsg(arg0: Int, data: ClusterMsgData?) {
            try {
                val valor = data?.getIntValue()
                nucleo.mensagemRecebida(arg0, valor)
            } catch (e: Exception) {
                registro.erro("falhou tratando a mensagem $arg0 do painel", e)
            }
        }
    }

    /** A interface do painel conectada; o valor vai num `ClusterMsgData` com `setIntValue`. */
    private class InterfaceDoPainel(private val servico: IClusterService) : ServicoRemotoDoPainel {
        override fun enviar(id: Int, valor: Int) {
            val dados = ClusterMsgData()
            dados.setIntValue(valor)
            servico.setMsg(id, dados)
        }

        override fun registrarRetorno() = servico.registerCallback(retorno)

        override fun desregistrarRetorno() = servico.unregisterCallback(retorno)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
