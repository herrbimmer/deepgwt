// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.nucleo.shizuku

import android.util.Log

/**
 * Quem pede à central para (re)iniciar o servidor do Shizuku (§B3–B7 da spec, pela sessão de
 * telnet de root) e quem liga o ADB por TCP (§G). A decisão de quando chamar, a espera do binder, a
 * classificação do desfecho e todos os registros (`BootDiagnostics`, diário) são deste módulo; o
 * adaptador só executa e **não** grava em `BootDiagnostics` (senão as contagens dobram).
 *
 * Todos os métodos são bloqueantes e chamados fora da thread principal: [iniciar], [servidorVivo],
 * [logDoServidor] e [terminar] na thread do módulo (ou na da rede de segurança), em ordem e nunca
 * ao mesmo tempo; [ligarAdbPorTcp] na thread do módulo, depois do aceite. Podem lançar: o módulo
 * trata exceção de [iniciar] como falha do procedimento (§B9) e engole as dos outros.
 *
 * A sessão aberta por [iniciar] pode ficar aberta até [terminar] (é por ela que [servidorVivo] e
 * [logDoServidor] leem); [terminar] é chamado sempre depois de [iniciar], dê no que der.
 */
interface IniciadorDoServidor {

    /**
     * Conecta à sessão de root, acha a lib, resolve o `--apk=`, mata processos antigos, faz o
     * `pm grant` de [pacoteDoApp] e sobe o servidor em segundo plano (§B3–B7). Volta logo depois do
     * `setsid ... &`: **não** espera o binder.
     *
     * @param pelaRedeDeSeguranca chamado da rede do §H: o apk vem de `pm path` em vez de deduzido.
     */
    fun iniciar(pacoteDoApp: String, pelaRedeDeSeguranca: Boolean): Disparo

    /** `pidof shizuku_server` pela sessão aberta: true = há pid, false = vazio, null = não deu para saber. */
    fun servidorVivo(): Boolean? = null

    /** Conteúdo de `/data/local/tmp/shizuku_auto.log` (só pedido quando o start não deu binder). */
    fun logDoServidor(): String? = null

    /** Fecha a sessão aberta por [iniciar]. */
    fun terminar() {}

    /** §G: garante o ADB por TCP na porta 5555, pela fachada `Shell`. Erros são do adaptador. */
    fun ligarAdbPorTcp()

    companion object {
        /** Padrão até a sala suja ligar o adaptador: não faz nada, só registra no logcat. */
        @JvmField
        val NENHUM: IniciadorDoServidor = Nenhum
    }
}

/**
 * O que [IniciadorDoServidor.iniciar] conseguiu.
 *
 * @property disparado o `setsid ... &` foi enviado; false = falha do procedimento (sem host de
 *   telnet, lib não achada...), com o motivo em [erro].
 * @property apk o `--apk=` usado; nulo se subiu sem ele.
 * @property telnet como foi a conexão (§B3); nulo se o adaptador não sabe dizer.
 */
data class Disparo(
    val disparado: Boolean,
    val apk: String? = null,
    val erro: String? = null,
    val telnet: ConexaoDoTelnet? = null,
)

/** §B3: [host] que aceitou (nulo = nenhum), a lista tentada em ordem e o tempo até conectar. */
data class ConexaoDoTelnet(val host: String?, val tentados: List<String>, val ms: Long)

private object Nenhum : IniciadorDoServidor {
    private const val ETIQUETA = "DeepGWT.Shizuku"

    override fun iniciar(pacoteDoApp: String, pelaRedeDeSeguranca: Boolean): Disparo {
        Log.w(ETIQUETA, "sem iniciador do servidor ligado; nada a subir")
        return Disparo(disparado = false, erro = "sem iniciador do servidor")
    }

    override fun ligarAdbPorTcp() {
        Log.i(ETIQUETA, "sem iniciador do servidor ligado; ADB por TCP não mexido")
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
