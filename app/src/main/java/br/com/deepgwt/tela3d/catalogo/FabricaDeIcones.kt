package br.com.deepgwt.tela3d.catalogo

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.annotation.DrawableRes
import br.com.deepgwt.R

/** O que o catálogo pergunta ao sistema sobre os apps instalados. */
internal interface FonteDeApps {
    /** Atividades de lançador, na ordem em que o sistema as devolve. */
    fun atividadesDeLancador(): List<AtividadeDeLancador>

    /** Ícone que o sistema dá para a atividade de lançador; null se não há. */
    fun iconeDaAtividade(atividade: AtividadeDeLancador): Drawable?

    /** Ícone do aplicativo; lança se o pacote não existe. */
    fun iconeDoAplicativo(pacote: String): Drawable

    /** Rótulo do aplicativo no gerenciador de pacotes; null se o pacote não existe. */
    fun rotuloDoAplicativo(pacote: String): String?

    /** É app de sistema (`FLAG_SYSTEM`)? Erro ao ler: false. */
    fun ehAppDeSistema(pacote: String): Boolean
}

/** [FonteDeApps] sobre o [PackageManager] do usuário atual. */
internal class FonteDeAppsDoSistema(private val pm: PackageManager) : FonteDeApps {
    override fun atividadesDeLancador(): List<AtividadeDeLancador> {
        val intencao = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val achados = pm.queryIntentActivities(intencao, 0)
        val saida = ArrayList<AtividadeDeLancador>(achados.size)
        for (r in achados) {
            val info = r.activityInfo ?: continue
            val pacote = info.packageName ?: continue
            val rotulo = try {
                r.loadLabel(pm)?.toString().orEmpty()
            } catch (e: Exception) {
                ""
            }
            saida.add(AtividadeDeLancador(pacote, info.name.orEmpty(), rotulo))
        }
        return saida
    }

    override fun iconeDaAtividade(atividade: AtividadeDeLancador): Drawable? = try {
        pm.getActivityIcon(ComponentName(atividade.pacote, atividade.classe))
    } catch (e: Exception) {
        null
    }

    override fun iconeDoAplicativo(pacote: String): Drawable = pm.getApplicationIcon(pacote)

    override fun rotuloDoAplicativo(pacote: String): String? = try {
        pm.getApplicationLabel(pm.getApplicationInfo(pacote, 0)).toString()
    } catch (e: Exception) {
        null
    }

    override fun ehAppDeSistema(pacote: String): Boolean = try {
        (pm.getApplicationInfo(pacote, 0).flags and ApplicationInfo.FLAG_SYSTEM) != 0
    } catch (e: Exception) {
        false
    }
}

/**
 * Escolhe e desenha o ícone de cada app (C1): personalização, emblema GWM, ícones dos fixados,
 * ícone do sistema. Devolve drawables que o chamador pode desenhar à vontade (cópias mutáveis dos
 * recursos); o ícone do sistema vem como o sistema o entrega e só é desenhado por cópia.
 */
internal class FabricaDeIcones(
    private val contexto: Context,
    private val fonte: FonteDeApps,
    private val regras: RegrasDoCatalogo,
) {
    /**
     * Ícone-fonte de um item planejado (antes da normalização): a personalização válida vence;
     * depois os fixados (C1.3); depois energy/emblema/ícone do sistema (C1.4).
     */
    fun iconeDoItem(item: ItemPlanejado, personalizacao: Personalizacao?): Drawable? {
        iconeSubstituto(personalizacao)?.let { return it }
        if (item.fixado) return iconeDeFixado(item.pacote)
        return iconeBase(item.pacote, item.atividade)
    }

    /** Ícone do chip do hub: personalização ou o emblema `ic_gwm_viewer`. */
    fun iconeDoHub(personalizacao: Personalizacao?): Drawable? =
        iconeSubstituto(personalizacao) ?: recurso(R.drawable.ic_gwm_viewer)

    /** Ícone de um pacote fixado (C1.3), sem personalização. */
    fun iconeDeFixado(pacote: String): Drawable? = when (pacote) {
        FatosDoCarro.ENERGY_ASSISTANT -> recurso(R.drawable.ic_energy_assistant)
        FatosDoCarro.SETTINGS_GWM -> recurso(R.drawable.ic_sub_settings)?.also {
            it.setColorFilter(BRANCO, PorterDuff.Mode.SRC_IN)
        }
        else -> recurso(R.drawable.ic_gwm_emblem) ?: recurso(R.drawable.ic_gwm_viewer)
    }

    /** C1.4: energy → seu ícone; app GWM com emblema → `ic_gwm_viewer`; senão o ícone do sistema. */
    fun iconeBase(pacote: String, atividade: AtividadeDeLancador?): Drawable? {
        if (regras.ehEnergyAssistant(pacote)) return recurso(R.drawable.ic_energy_assistant)
        if (regras.usaEmblemaGwm(pacote)) return recurso(R.drawable.ic_gwm_viewer)
        return if (atividade != null) fonte.iconeDaAtividade(atividade) else semExcecao { fonte.iconeDoAplicativo(pacote) }
    }

    /** Ícone para a lista de ocultos (C1.4, sem placa e sem personalização). */
    fun iconeDeOculto(pacote: String, atividade: AtividadeDeLancador?): Drawable? =
        if (atividade == null && !regras.usaEmblemaGwm(pacote) && !regras.ehEnergyAssistant(pacote)) null
        else iconeBase(pacote, atividade)

    /** Ícone substituto da personalização, se ela tem um slug conhecido (C5). */
    fun iconeSubstituto(personalizacao: Personalizacao?): Drawable? =
        iconeSubstituto(personalizacao?.icone, personalizacao?.cor)

    /**
     * C5: o recurso do slug (cópia mutável). Slugs de marca mantêm as cores; os outros levam filtro
     * SRC_IN com a cor (vazia/inválida: branco). Slug desconhecido ou erro: null.
     */
    fun iconeSubstituto(slug: String?, corHex: String?): Drawable? {
        val id = SlugsDeIcone.recurso(slug) ?: return null
        return try {
            val drawable = recurso(id) ?: return null
            if (!SlugsDeIcone.ehDeMarca(slug)) {
                drawable.setColorFilter(CoresDaPersonalizacao.corDoGlifo(corHex), PorterDuff.Mode.SRC_IN)
            }
            drawable
        } catch (e: Exception) {
            Log.w(ETIQUETA, "Falha ao montar o ícone substituto", e)
            null
        }
    }

    /** Ícone de um item do catálogo da página como data URL 96×96 (null = chave `icon` omitida). */
    fun dataUrlDoCatalogo(item: ItemPlanejado, personalizacao: Personalizacao?, temaClaro: Boolean): String? = try {
        val fonteDoIcone = iconeDoItem(item, personalizacao)
        val normalizado = NormalizacaoDeIcone.normalizar(contexto.resources, fonteDoIcone, CodificadorDeIcone.LADO)
        CodificadorDeIcone.paraDataUrl(normalizado, Placas.corDoCatalogo(item.placa, temaClaro))
    } catch (e: Exception) {
        Log.w(ETIQUETA, "Falha ao desenhar o ícone de ${item.pacote}", e)
        null
    }

    /** Recurso de drawable como cópia mutável; null se não existe. */
    fun recurso(@DrawableRes id: Int): Drawable? = try {
        contexto.getDrawable(id)?.let { DrawablesSeguros.copiaMutavel(it) }
    } catch (e: Exception) {
        null
    }

    private inline fun <T> semExcecao(bloco: () -> T): T? = try {
        bloco()
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val ETIQUETA = "DeepGWT/Catalogo"
        const val BRANCO = 0xFFFFFFFF.toInt()
    }
}
