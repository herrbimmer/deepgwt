// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.brilho

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import android.util.Log
import br.com.deepgwt.nucleo.Shell
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Posição do carro para o modo sol e o nome da cidade para a tela. Nenhum erro daqui sobe: vai
 * para o log e o modo sol segue com o que tiver (ou cai no horário fixo).
 */
internal object Localizacao {

    private const val TAG = "DeepGWT.Brilho"

    /** Provedores consultados, os que existirem no aparelho. */
    private val PROVEDORES = listOf("gps", "passive", "network", "fused")

    private val controleDaCidade = ControleDaCidade()

    /**
     * A última posição conhecida mais recente dos provedores; achou, grava nas preferências (e
     * pede o nome da cidade em segundo plano). Não achou: a gravada. Nenhuma: null.
     */
    fun posicao(contexto: Context, prefs: PreferenciasDoBrilho): Posicao? {
        val achada = ultimaConhecida(contexto)
        if (achada != null) {
            prefs.gravarPosicao(achada)
            pedirCidade(contexto, prefs, achada)
            return achada
        }
        return prefs.posicaoGravada()
    }

    private fun ultimaConhecida(contexto: Context): Posicao? {
        garantirPermissao(contexto)
        val gerenciador = try {
            contexto.getSystemService(LocationManager::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "sem LocationManager", e)
            null
        } ?: return null
        val existentes = try {
            gerenciador.allProviders.toSet()
        } catch (e: Exception) {
            Log.e(TAG, "não deu para listar os provedores de localização", e)
            return null
        }
        var maisRecente: Location? = null
        for (provedor in PROVEDORES) {
            if (provedor !in existentes) continue
            val local = try {
                gerenciador.getLastKnownLocation(provedor)
            } catch (e: Exception) {
                Log.w(TAG, "última posição de '$provedor' indisponível: $e")
                null
            } ?: continue
            if (!Sol.coordenadaValida(local.latitude, local.longitude)) continue
            if (maisRecente == null || local.time > maisRecente.time) maisRecente = local
        }
        return maisRecente?.let { Posicao(it.latitude, it.longitude) }
    }

    /** Sem a permissão de localização precisa, concede pelo Shell (usuário `shell`, Shizuku). */
    private fun garantirPermissao(contexto: Context) {
        try {
            val permissao = Manifest.permission.ACCESS_FINE_LOCATION
            if (contexto.checkSelfPermission(permissao) == PackageManager.PERMISSION_GRANTED) return
            Shell.executar(arrayOf("pm", "grant", contexto.packageName, permissao))
        } catch (e: Exception) {
            Log.e(TAG, "falhou concedendo a permissão de localização", e)
        }
    }

    /** Pergunta ao Geocoder, numa thread própria, e grava o nome da cidade se vier um. */
    private fun pedirCidade(contexto: Context, prefs: PreferenciasDoBrilho, posicao: Posicao) {
        if (!controleDaCidade.comecar(posicao.latitude, posicao.longitude, SystemClock.elapsedRealtime())) return
        try {
            thread(name = "DeepGWT.Brilho.Cidade", isDaemon = true) {
                try {
                    nomeDaCidade(contexto, posicao)?.let(prefs::gravarCidade)
                } catch (e: Throwable) {
                    Log.w(TAG, "Geocoder falhou: $e")
                } finally {
                    controleDaCidade.terminou()
                }
            }
        } catch (e: Throwable) {
            controleDaCidade.terminou()
            Log.e(TAG, "não deu para consultar a cidade", e)
        }
    }

    @Suppress("DEPRECATION") // a versão com callback é da API 33; a central é API 28.
    private fun nomeDaCidade(contexto: Context, posicao: Posicao): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(contexto, Locale.forLanguageTag("pt-BR"))
        val endereco = geocoder.getFromLocation(posicao.latitude, posicao.longitude, 1)?.firstOrNull()
            ?: return null
        return listOf(endereco.subAdminArea, endereco.locality, endereco.adminArea)
            .firstOrNull { !it.isNullOrBlank() }
            ?.trim()
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
