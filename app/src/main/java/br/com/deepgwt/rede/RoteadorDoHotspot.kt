// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.rede

import android.os.Handler
import android.os.HandlerThread
import br.com.deepgwt.nucleo.DeepGwt

/**
 * Roteador do hotspot: os aparelhos ligados ao hotspot do carro navegam pelo Wi-Fi externo
 * enquanto ele tiver internet, e voltam sozinhos para o 4G da fabricante quando não tiver.
 * Especificação: `docs/independencia/specs/extras-rede.md`, seção C. O daemon é o asset
 * `roteador_do_hotspot.sh`; a lógica do lado do app está em [GestorDoRoteador].
 */
object RoteadorDoHotspot {

    private const val ASSET_DO_SCRIPT = "roteador_do_hotspot.sh"

    private val gestor: GestorDoRoteador by lazy {
        GestorDoRoteador(
            armazem = ArmazemDePreferencias(),
            shell = ExecutorDoModulo,
            registro = RegistroDaRedeNoLogcat,
            relogio = RelogioDoSistema,
            agendador = AgendadorNoHandler("DeepGWT-Rede-Roteador"),
            lerScript = ::lerAsset,
        )
    }

    /** Liga ou desliga. Grava a preferência `enableHotRouter` e volta na hora; o trabalho é no fundo. */
    @JvmStatic
    fun definirLigado(ligado: Boolean) = gestor.definirLigado(ligado)

    /** Serviço pronto (Shizuku e motor de pé): se a preferência está ligada, garante o daemon. */
    @JvmStatic
    fun aoFicarPronto() = gestor.aoFicarPronto()

    /**
     * Lê o estado agora. **Bloqueia** (roda shell): nunca na thread principal. O modo é do uplink,
     * não do hotspot: a tela que mostra "ativo" deve consultar também `Conectividade.hotspotNoAr`.
     */
    @JvmStatic
    fun lerSituacao(): SituacaoDoRoteador = gestor.lerSituacao()

    private fun lerAsset(): ByteArray? =
        try {
            DeepGwt.contexto.assets.open(ASSET_DO_SCRIPT).use { it.readBytes() }
        } catch (e: Exception) {
            RegistroDaRedeNoLogcat.erro("não consegui ler o asset $ASSET_DO_SCRIPT", e)
            null
        }

    /** Uma `HandlerThread` própria, criada no primeiro uso. */
    private class AgendadorNoHandler(nome: String) : AgendadorDeFundo {
        private val handler: Handler by lazy {
            val linha = HandlerThread(nome).apply { isDaemon = true; start() }
            Handler(linha.looper)
        }

        override fun executar(tarefa: Runnable) {
            handler.post(tarefa)
        }

        override fun depois(atrasoMs: Long, tarefa: Runnable) {
            handler.postDelayed(tarefa, atrasoMs)
        }

        override fun cancelar(tarefa: Runnable) {
            handler.removeCallbacks(tarefa)
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
