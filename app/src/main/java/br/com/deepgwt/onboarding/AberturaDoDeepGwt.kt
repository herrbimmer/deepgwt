// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.onboarding

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import br.com.deepgwt.BuildConfig
import br.com.deepgwt.nucleo.Preferencias

/**
 * Estado do que está por cima do app: assistente, novidades ou nada (Alpha 7.9). O tour tem o
 * próprio estado em [TourDoDeepGwt].
 *
 * Mora num objeto, e não na Activity, de propósito: trocar o tamanho da interface recria o
 * `PrincipalActivity` e o assistente tem de continuar aberto no mesmo passo.
 */
object AberturaDoDeepGwt {
    private const val TAG = "AberturaDoDeepGwt"

    var tela by mutableStateOf(TelaDeAbertura.NADA)
        private set

    /** As novidades que a tela de novidades mostra agora. */
    var novidades by mutableStateOf<List<Novidade>>(emptyList())
        private set

    private fun prefs(context: Context): SharedPreferences =
        context.createDeviceProtectedStorageContext().getSharedPreferences("deepgwt_prefs", Context.MODE_PRIVATE)

    private val versaoAtual: Int
        get() = BuildConfig.VERSION_CODE

    /** Chamado uma vez por abertura do app (não em recriação da Activity). */
    fun decidirAoAbrir(context: Context) {
        if (tela != TelaDeAbertura.NADA || TourDoDeepGwt.ativo) return
        val p = prefs(context)
        val instalacaoNova =
            runCatching {
                @Suppress("DEPRECATION")
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                info.firstInstallTime == info.lastUpdateTime
            }.getOrDefault(false)
        val vistasAte = p.getInt(Preferencias.NOVIDADES_VISTAS_ATE.chave, 0)
        val decisao =
            PrimeiraAbertura.decidir(
                instalacaoNova = instalacaoNova,
                assistenteConcluidoEm = p.getInt(Preferencias.ASSISTENTE_CONCLUIDO_VERSAO.chave, 0),
                novidadesVistasAte = vistasAte,
                versaoAtual = versaoAtual
            )
        Log.i(TAG, "abertura: instalação nova=$instalacaoNova, novidades vistas até $vistasAte, versão $versaoAtual → $decisao")
        if (decisao == TelaDeAbertura.NOVIDADES) novidades = Novidades.entre(vistasAte, versaoAtual)
        tela = decisao
    }

    /** "Refazer configuração inicial", ou "Configurar agora" nas novidades. */
    fun abrirAssistente() {
        TourDoDeepGwt.encerrar(gravar = false)
        tela = TelaDeAbertura.ASSISTENTE
    }

    /** Fim do assistente, por "Concluir" ou "Pular". As novidades desta versão já foram vistas nele. */
    fun concluirAssistente(context: Context, fazerTour: Boolean) {
        prefs(context).edit()
            .putInt(Preferencias.ASSISTENTE_CONCLUIDO_VERSAO.chave, versaoAtual)
            .putInt(
                Preferencias.NOVIDADES_VISTAS_ATE.chave,
                maxOf(versaoAtual, prefs(context).getInt(Preferencias.NOVIDADES_VISTAS_ATE.chave, 0))
            )
            .apply()
        tela = TelaDeAbertura.NADA
        if (fazerTour) TourDoDeepGwt.iniciar()
    }

    enum class SaidaDasNovidades { OK, MOSTRAR_ONDE_FICA, CONFIGURAR }

    fun fecharNovidades(context: Context, saida: SaidaDasNovidades) {
        val vistas = novidades
        prefs(context).edit().putInt(Preferencias.NOVIDADES_VISTAS_ATE.chave, versaoAtual).apply()
        tela = TelaDeAbertura.NADA
        novidades = emptyList()
        when (saida) {
            SaidaDasNovidades.OK -> Unit
            SaidaDasNovidades.MOSTRAR_ONDE_FICA ->
                TourDoDeepGwt.iniciar(PassosDoTour.porIds(vistas.flatMap { it.passosDoTour }))
            SaidaDasNovidades.CONFIGURAR -> abrirAssistente()
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
