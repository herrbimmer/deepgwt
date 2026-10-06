// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.luz

import br.com.deepgwt.nucleo.Preferencias
import br.com.deepgwt.veiculo.HeadlightPolicy
import br.com.deepgwt.veiculo.Sinais
import java.util.concurrent.ConcurrentHashMap

/** As chaves de preferência do módulo (arquivo "deepgwt_prefs"). O texto é o formato já gravado nos carros. */
internal object ChavesDaLuz {
    val LIGADA = Preferencias.AMBIENT_LIGHT_BLE_ENABLED.chave
    const val ENDERECO = "ambientLightBleDeviceMac"
    const val NOME = "ambientLightBleDeviceName"
    val DINAMICA = Preferencias.AMBIENT_LIGHT_SYNC_DRIVE_MODE.chave
    val ANIMACOES = Preferencias.AMBIENT_LIGHT_ANIMATIONS_ENABLED.chave
    val MUSICA = Preferencias.AMBIENT_LIGHT_MUSIC_ANIMATION_ENABLED.chave
    val MODO_MUSICAL = Preferencias.AMBIENT_LIGHT_MUSIC_MODE.chave
    const val EFEITO_DO_ALBUM = "ambientLightAlbumEffect"
    const val VELOCIDADE_DO_ALBUM = "ambientLightAlbumEffectSpeed"
    const val SAIDA_DO_ALBUM = "ambientLightAlbumEffectOutput"
    const val MODO_BLE_DO_ALBUM = "ambientLightAlbumBleMode"
    const val MODO_DMX_DO_ALBUM = "ambientLightAlbumDmxMode"
    const val ORDEM_DMX = "ambientLightColorOrder"
    const val ORDEM_BLE = "ambientLightBleColorOrder"
    val BRILHO = Preferencias.AMBIENT_LIGHT_BRIGHTNESS_PERCENT.chave
    val SAIDA = Preferencias.AMBIENT_LIGHT_OUTPUT.chave
    val RECONECTAR = Preferencias.AMBIENT_LIGHT_AUTO_RECONNECT.chave
    val MATIZ = Preferencias.AMBIENT_LIGHT_MANUAL_HUE.chave
    val FONTE_DO_FAROL = Preferencias.NIGHT_MODE_TRIGGER.chave
    val SORRATEIRO = Preferencias.STEALTH_MODE_ACTIVE.chave
}

/** Leitura tipada das preferências. null = ausente ou de um tipo que não serve. */
internal interface FonteDeAjustes {
    fun booleano(chave: String): Boolean?
    fun inteiro(chave: String): Int?
    fun real(chave: String): Float?
    fun texto(chave: String): String?
}

/** Fonte sobre um mapa (testes, ou um retrato das preferências). Números de qualquer tipo servem. */
internal class FonteDeMapa(private val mapa: Map<String, Any?>) : FonteDeAjustes {
    override fun booleano(chave: String): Boolean? = mapa[chave] as? Boolean
    override fun inteiro(chave: String): Int? = (mapa[chave] as? Number)?.toInt()
    override fun real(chave: String): Float? = when (val v = mapa[chave]) {
        is Number -> v.toFloat()
        is String -> v.trim().replace(',', '.').toFloatOrNull()
        else -> null
    }
    override fun texto(chave: String): String? = mapa[chave] as? String
}

/**
 * Um retrato das preferências do módulo, relido a cada comando (e a cada quadro das animações):
 * mudar uma preferência vale no próximo quadro enviado. Valores estranhos caem no padrão; números
 * são limitados.
 */
internal data class AjustesDaLuz(
    val ligado: Boolean = false,
    val endereco: String? = null,
    val nome: String? = null,
    val dinamica: Boolean = false,
    val animacoes: Boolean = false,
    val musica: Boolean = false,
    val modoMusical: ModoMusical = ModoMusical.PADRAO,
    val efeitoDoAlbum: EfeitoDoAlbum = EfeitoDoAlbum.PADRAO,
    val velocidadeDoAlbum: Int = 50,
    val saidaDoAlbum: SaidaDaLuz = SaidaDaLuz.PADRAO,
    val modoBleDoAlbum: ModoDoAlbum = ModoDoAlbum.PADRAO,
    val modoDmxDoAlbum: ModoDoAlbum = ModoDoAlbum.PADRAO,
    val ordemDmx: OrdemDeCor = OrdemDeCor.PADRAO_DMX,
    val ordemBle: OrdemDeCor = OrdemDeCor.PADRAO_BLE,
    val brilho: Int = 100,
    val saida: SaidaDaLuz = SaidaDaLuz.PADRAO,
    val reconectar: Boolean = true,
    val matiz: Float = 0f,
    val reacoes: Set<ReacaoDaLuz> = ReacaoDaLuz.ligadas { _, padrao -> padrao },
    val fonteDoFarol: String = HeadlightPolicy.FONTE_PADRAO,
    val sorrateiro: Boolean = false,
) {
    val temFita: Boolean get() = !endereco.isNullOrBlank()

    val corManual: CorLed get() = CorLed.daMatiz(matiz)

    /** Cor-base estática (caso 36 sem animação): cor do modo com a reação ligada, senão a manual. */
    fun corBase(modo: ModoDeConducao): CorLed =
        if (dinamica && ReacaoDaLuz.MODO_DE_CONDUCAO in reacoes) modo.cor else corManual

    companion object {
        fun limitarVelocidade(v: Int): Int = v.coerceIn(1, 100)
        fun limitarBrilho(p: Int): Int = p.coerceIn(0, 100)
        fun limitarMatiz(h: Float): Float = if (h.isNaN()) 0f else h.coerceIn(0f, 360f)

        fun ler(f: FonteDeAjustes): AjustesDaLuz = AjustesDaLuz(
            ligado = f.booleano(ChavesDaLuz.LIGADA) ?: false,
            endereco = f.texto(ChavesDaLuz.ENDERECO)?.trim()?.takeIf { it.isNotEmpty() },
            nome = f.texto(ChavesDaLuz.NOME)?.takeIf { it.isNotBlank() },
            dinamica = f.booleano(ChavesDaLuz.DINAMICA) ?: false,
            animacoes = f.booleano(ChavesDaLuz.ANIMACOES) ?: false,
            musica = f.booleano(ChavesDaLuz.MUSICA) ?: false,
            modoMusical = ModoMusical.doValor(f.texto(ChavesDaLuz.MODO_MUSICAL)),
            efeitoDoAlbum = EfeitoDoAlbum.doValor(f.texto(ChavesDaLuz.EFEITO_DO_ALBUM)),
            velocidadeDoAlbum = limitarVelocidade(f.inteiro(ChavesDaLuz.VELOCIDADE_DO_ALBUM) ?: 50),
            saidaDoAlbum = SaidaDaLuz.doValor(f.texto(ChavesDaLuz.SAIDA_DO_ALBUM)),
            modoBleDoAlbum = ModoDoAlbum.doValor(f.texto(ChavesDaLuz.MODO_BLE_DO_ALBUM)),
            modoDmxDoAlbum = ModoDoAlbum.doValor(f.texto(ChavesDaLuz.MODO_DMX_DO_ALBUM)),
            ordemDmx = OrdemDeCor.doValor(f.texto(ChavesDaLuz.ORDEM_DMX), OrdemDeCor.PADRAO_DMX),
            ordemBle = OrdemDeCor.doValor(f.texto(ChavesDaLuz.ORDEM_BLE), OrdemDeCor.PADRAO_BLE),
            brilho = limitarBrilho(f.inteiro(ChavesDaLuz.BRILHO) ?: 100),
            saida = SaidaDaLuz.doValor(f.texto(ChavesDaLuz.SAIDA)),
            reconectar = f.booleano(ChavesDaLuz.RECONECTAR) ?: true,
            matiz = limitarMatiz(f.real(ChavesDaLuz.MATIZ) ?: 0f),
            reacoes = ReacaoDaLuz.ligadas { chave, padrao -> f.booleano(chave) ?: padrao },
            fonteDoFarol = f.texto(ChavesDaLuz.FONTE_DO_FAROL) ?: HeadlightPolicy.FONTE_PADRAO,
            sorrateiro = f.booleano(ChavesDaLuz.SORRATEIRO) ?: false,
        )
    }
}

/** As chaves do carro que a luz lê (caso 33) e as da ponte com a luz original (caso 60). */
internal object ChavesDoCarro {
    val PISCA_ALERTA = Sinais.CAR_BASIC_HAZARD_LIGHT_STATUS.chave
    val PONTO_CEGO_ESQUERDO = Sinais.CAR_IPK_INFO_BSD_LCA_WARNING_REQLEFT.chave
    val PONTO_CEGO_DIREITO = Sinais.CAR_IPK_INFO_BSD_LCA_WARNING_REQRIGHT.chave
    val SETA_ESQUERDA = Sinais.CAR_BASIC_LEFT_TURN_LIGHT_STATUS.chave
    val SETA_DIREITA = Sinais.CAR_BASIC_RIGHT_TURN_LIGHT_STATUS.chave
    val FAROL_MESTRE = Sinais.CAR_BASIC_HEAD_LIGHT_STATUS.chave
    val MODO_DE_CONDUCAO = Sinais.CAR_DRIVE_SETTING_DRIVE_MODE.chave
    val RITMO_DA_LUZ_ORIGINAL = Sinais.CAR_LIGHT_SETTING_AMBIENT_LIGHT_RHYTHMIC_SWITCH.chave
    val FREQUENCIA_DA_MUSICA = Sinais.CAR_LIGHT_SETTING_AMBIENT_LIGHT_SYNC_MUSIC_FREQ.chave

    /** O que o ouvinte de alerta acompanha: alertas, setas e as fontes do farol. */
    val ALERTA: Set<String> = linkedSetOf(
        PISCA_ALERTA, PONTO_CEGO_ESQUERDO, PONTO_CEGO_DIREITO, SETA_ESQUERDA, SETA_DIREITA, FAROL_MESTRE,
    ) + HeadlightPolicy.CHAVES_DE_ORIGEM
}

/**
 * "O último aviso vence o cache": o motor do veículo avisa os ouvintes ANTES de gravar o valor no
 * cache, então ler o cache dentro do aviso devolve o valor anterior. Guarda o valor que chegou no
 * próprio aviso; o cache só vale para chave que nunca chegou (aviso nulo não apaga nada).
 */
internal class MemoriaDosSinais {
    private val valores = ConcurrentHashMap<String, String>()

    fun receber(chave: String, valor: String?) {
        if (valor != null) valores[chave] = valor
    }

    fun valor(chave: String, lerDoCache: (String) -> String?): String? = valores[chave] ?: lerDoCache(chave)

    fun limpar() = valores.clear()
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
