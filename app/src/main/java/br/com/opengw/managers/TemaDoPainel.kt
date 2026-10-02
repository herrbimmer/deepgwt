package br.com.opengw.managers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import br.com.opengw.licenca.Portao
import br.com.opengw.licenca.Recurso
import br.com.opengw.nucleo.Preferencias
import br.com.opengw.painel.TemaInstalado
import br.com.opengw.painel.TemasInstalados
import br.com.opengw.ui.screens.ModoDoPainel
import org.json.JSONObject

/**
 * Trocar o tema do painel — o que a tela Telas faz ao tocar num tema, fora da tela.
 *
 * O trecho morava dentro do `TelasScreen` (procurava a imagem de fundo do tema na hora e gravava
 * seis preferências numa transação). Extraído para cá, serve a três chamadores com a MESMA
 * gravação: a própria tela Telas, os Ajustes do OPEN GW pelo computador (`painel.tema`) e o
 * "Ver no carro" do criador de temas, que troca o painel para um rascunho enquanto se edita.
 *
 * Nada aqui toca o WebView: o painel (`br.com.opengw.painel`) já recarrega sozinho quando
 * [Preferencias.ACTIVE_CUSTOM_THEME] ou [Preferencias.THEME_RELOAD_NONCE] muda.
 */
object TemaDoPainel {
    private const val TAG = "TemaDoPainel"

    /** Pasta onde o instalador grava o tema que está sendo editado no computador. */
    const val PASTA_DO_RASCUNHO = "opengw-rascunho"

    /**
     * A grade da calibração do painel (Alpha 8.8, `diagnostics/CalibracaoDoPainel`). Não é tema de
     * escolher: entra e sai pelo Diagnóstico, e fica fora da lista como o rascunho.
     */
    const val PASTA_DA_CALIBRACAO = "opengw-calibracao"

    /**
     * O tema que estava no painel antes do rascunho, para voltar a ele. Estado passageiro:
     * fica fora dos perfis (ver `EscopoDoPerfil`).
     */
    const val CHAVE_ANTES_DO_RASCUNHO = "temaAntesDoRascunho"

    /** O tema embutido no APK não tem pasta; na preferência ele é a string vazia. */
    const val PASTA_EMBUTIDA = ""

    /**
     * O tema do painel inteiro quando ninguém escolheu outro: o NTG 5.5 clássico.
     *
     * Desde a Alpha 6.8 o embutido (Default) não é mais um tema de escolher. Ele continua no APK
     * porque é quem desenha o painel de fábrica com as informações extras e o círculo; com o tema
     * customizado ligado, porém, o painel nunca fica nele — cai aqui ([garantirTemaDoPainelInteiro]).
     */
    const val PASTA_PADRAO = "coffe3"

    data class Tema(
        val pasta: String,
        val nome: String,
        val versao: String,
        val descricao: String,
        val embutido: Boolean,
        val casta: CastaDoTema = CastaDoTema.XTRA,
    )

    sealed class Resultado {
        data class Feito(val pasta: String, val nome: String) : Resultado()
        data class Recusado(val motivo: String) : Resultado()
    }

    /**
     * Os temas que a tela Telas oferece: os da pasta, sem o rascunho e sem a grade da calibração,
     * Legacy antes de XTRA. O
     * embutido saiu na 6.8 (ver [PASTA_PADRAO]); o campo [Tema.embutido] fica, sempre falso, para o
     * instalador que já o lê.
     */
    @Suppress("UNUSED_PARAMETER")
    fun listar(context: Context): List<Tema> {
        return TemasInstalados.listar()
            .filter { it.pasta.isNotBlank() && !it.pasta.equals("Default", ignoreCase = true) }
            .filter { it.pasta != PASTA_DO_RASCUNHO && it.pasta != PASTA_DA_CALIBRACAO }
            .filter { TemasInstalados.suportado(it) }
            .map { Tema(it.pasta, it.nome, it.versao, it.descricao, embutido = false, casta = castaDe(it.pasta)) }
            .sortedWith(compareBy({ it.casta.ordinal }, { it.nome.lowercase() }))
    }

    /** Legacy (veio no APK) ou XTRA (chegou depois), pela pasta. */
    fun castaDe(pasta: String): CastaDoTema = CastaDoTema.de(pasta, TemasInstalados.pastasDoApk)

    /** O tema que o painel inteiro usa no lugar do embutido: o [PASTA_PADRAO]; sem ele, o primeiro da lista. */
    private fun temaPadrao(context: Context): TemaInstalado? {
        TemasInstalados.ler(PASTA_PADRAO)?.takeIf { TemasInstalados.suportado(it) }?.let { return it }
        return listar(context).firstOrNull()?.let { TemasInstalados.ler(it.pasta) }
    }

    /**
     * Com o tema customizado ligado, troca o embutido pelo [PASTA_PADRAO]. Qualquer caminho que
     * devolvia o painel ao embutido (ligar o tema customizado pela primeira vez, apagar o tema
     * ativo, um tema fora do contrato, o fim de um rascunho) passa por aqui depois.
     *
     * Sem nenhum tema na pasta o embutido fica: painel com o Default é melhor que painel vazio.
     * Devolve true quando trocou.
     */
    fun garantirTemaDoPainelInteiro(context: Context, prefs: SharedPreferences): Boolean {
        if (!ModoDoPainel.ler(prefs).desenhaOPainelInteiro) return false
        val pasta = ativo(prefs)
        if (pasta.isNotBlank() && !pasta.equals("Default", ignoreCase = true)) return false
        val meta = temaPadrao(context) ?: return false
        aplicar(prefs, meta)
        Log.i(TAG, "Painel inteiro estava no embutido; aplicado '${meta.pasta}'.")
        return true
    }

    /** A pasta do tema no painel agora. Vazia = o embutido. */
    fun ativo(prefs: SharedPreferences): String =
        prefs.getString(Preferencias.ACTIVE_CUSTOM_THEME.chave, PASTA_EMBUTIDA) ?: PASTA_EMBUTIDA

    /**
     * Aplica um tema já lido. É a gravação que estava no `TelasScreen`, sem mudança nenhuma: o fundo
     * do cluster é sempre o que o próprio tema declara, e um tema sem imagem grava a dica vazia para
     * o projetor ficar transparente em vez de reaproveitar a imagem do tema anterior.
     */
    fun aplicar(prefs: SharedPreferences, tema: TemaInstalado) {
        val embutido = tema.pasta == "Default" || tema.nome == "Default"
        val pasta = if (embutido) PASTA_EMBUTIDA else tema.pasta
        val fundoRelativo = tema.fundo.ifBlank {
            if (tema.fundoAbsoluto.isNotBlank()) java.io.File(tema.fundoAbsoluto).name else ""
        }
        prefs.edit()
            .putString(Preferencias.VIRTUAL_CLUSTER_THEME.chave, tema.nome)
            .putString(Preferencias.ACTIVE_CUSTOM_THEME.chave, pasta)
            .putLong(Preferencias.THEME_RELOAD_NONCE.chave, System.currentTimeMillis())
            .putBoolean(Preferencias.ENABLE_CUSTOM_BACKGROUND_D1.chave, true)
            .putString(Preferencias.CUSTOM_BACKGROUND_TYPE_D1.chave, "THEME")
            .putString(Preferencias.CUSTOM_BACKGROUND_VALUE_D1.chave, fundoRelativo)
            .apply()
    }

    /** Aplica pela pasta. Pasta vazia ou "Default" é o tema embutido. */
    fun aplicarPorPasta(context: Context, prefs: SharedPreferences, pasta: String): Resultado {
        val meta: TemaInstalado = if (pasta.isBlank() || pasta.equals("Default", ignoreCase = true)) {
            TemasInstalados.embutido()
        } else {
            TemasInstalados.ler(pasta) ?: return Resultado.Recusado("não há tema na pasta \"$pasta\"")
        }
        if (!TemasInstalados.suportado(meta)) {
            return Resultado.Recusado("o tema \"${meta.nome}\" não segue o contrato v1.0 desta versão do app")
        }
        val embutido = meta.pasta == "Default" || meta.nome == "Default"
        if (!embutido) motivoParaNaoTrocar(prefs)?.let { return Resultado.Recusado(it) }
        if (!embutido) motivoDoNivel(castaDe(meta.pasta))?.let { return Resultado.Recusado(it) }
        aplicar(prefs, meta)
        if (embutido && garantirTemaDoPainelInteiro(context, prefs)) {
            return Resultado.Feito(ativo(prefs), prefs.getString(Preferencias.VIRTUAL_CLUSTER_THEME.chave, "") ?: "")
        }
        return Resultado.Feito(if (embutido) PASTA_EMBUTIDA else meta.pasta, meta.nome)
    }

    /**
     * Com o tema customizado do painel desligado, o painel é o de fábrica e o tema escolhido é
     * sempre o embutido — é a invariante de [ModoDoPainel.voltaAoTemaDeFabrica]. Gravar outro tema
     * por fora dela faria o painel acordar num tema que a tela Telas diz não estar escolhido.
     */
    private fun motivoParaNaoTrocar(prefs: SharedPreferences): String? =
        if (ModoDoPainel.ler(prefs).desenhaOPainelInteiro) null
        else "o tema customizado do painel está desligado no carro — ligue em Ajustes do OPEN GW, Painel de instrumentos, Tema customizado"

    /**
     * O nível libera esta casta? Os temas que vêm no app são Sport; os XTRA (catálogo, HTML
     * importado, criador) são Premium (Alpha 8.0). `null` = libera.
     */
    fun motivoDoNivel(casta: CastaDoTema): String? {
        val recurso = recursoDa(casta)
        return if (Portao.permite(recurso)) null
        else "${casta.titulo} são do ${recurso.nivel.titulo}"
    }

    fun recursoDa(casta: CastaDoTema): Recurso =
        if (casta == CastaDoTema.LEGACY) Recurso.TEMAS_EMBUTIDOS else Recurso.TEMAS_XTRA

    /** Pede ao painel para reler o tema ativo — o rascunho acabou de ganhar arquivos novos. */
    fun recarregar(prefs: SharedPreferences) {
        prefs.edit().putLong(Preferencias.THEME_RELOAD_NONCE.chave, System.currentTimeMillis()).apply()
    }

    fun rascunhoAtivo(prefs: SharedPreferences): Boolean = ativo(prefs) == PASTA_DO_RASCUNHO

    /**
     * Troca o painel para o rascunho do computador, guardando o tema de antes. Chamar de novo com o
     * rascunho já ativo só recarrega: não sobrescreve o "antes" com o próprio rascunho.
     */
    @Suppress("UNUSED_PARAMETER")
    fun iniciarRascunho(context: Context, prefs: SharedPreferences): Resultado {
        val meta = TemasInstalados.ler(PASTA_DO_RASCUNHO)
            ?: return Resultado.Recusado("o rascunho ainda não chegou ao carro (falta o theme.xml em $PASTA_DO_RASCUNHO)")
        if (!TemasInstalados.suportado(meta)) return Resultado.Recusado("o rascunho não segue o contrato v1.0")
        motivoParaNaoTrocar(prefs)?.let { return Resultado.Recusado(it) }
        if (!Portao.permite(Recurso.EDITOR_DE_TEMAS)) {
            return Resultado.Recusado("o editor de temas é do ${Recurso.EDITOR_DE_TEMAS.nivel.titulo}")
        }
        if (!rascunhoAtivo(prefs)) {
            val antes = JSONObject()
                .put("pasta", ativo(prefs))
                .put("nome", prefs.getString(Preferencias.VIRTUAL_CLUSTER_THEME.chave, "Default") ?: "Default")
            prefs.edit().putString(CHAVE_ANTES_DO_RASCUNHO, antes.toString()).apply()
        }
        aplicar(prefs, meta)
        return Resultado.Feito(PASTA_DO_RASCUNHO, meta.nome)
    }

    /** Volta ao tema de antes do rascunho e apaga a pasta dele. */
    fun encerrarRascunho(context: Context, prefs: SharedPreferences): Resultado {
        val antes = prefs.getString(CHAVE_ANTES_DO_RASCUNHO, null)
        val pastaAntes = runCatching { JSONObject(antes ?: "{}").optString("pasta", PASTA_EMBUTIDA) }
            .getOrDefault(PASTA_EMBUTIDA)
        prefs.edit().remove(CHAVE_ANTES_DO_RASCUNHO).apply()
        val resultado = if (rascunhoAtivo(prefs)) {
            aplicarPorPasta(context, prefs, pastaAntes).let {
                // O tema de antes pode ter sido apagado no meio da sessão: o embutido é a volta segura.
                if (it is Resultado.Recusado) aplicarPorPasta(context, prefs, PASTA_EMBUTIDA) else it
            }
        } else {
            Resultado.Feito(ativo(prefs), prefs.getString(Preferencias.VIRTUAL_CLUSTER_THEME.chave, "Default") ?: "Default")
        }
        runCatching { TemasInstalados.apagar(PASTA_DO_RASCUNHO) }
            .onFailure { Log.w(TAG, "Não apagou a pasta do rascunho: ${it.message}") }
        return resultado
    }

    /**
     * Na subida do app: um rascunho que ficou ativo (o computador desligou no meio do "Ver no
     * carro") volta ao tema de antes. Sem isto, o painel ficaria para sempre no rascunho de uma
     * edição que ninguém está mais fazendo.
     */
    fun encerrarRascunhoEsquecido(context: Context, prefs: SharedPreferences) {
        if (!rascunhoAtivo(prefs) && prefs.getString(CHAVE_ANTES_DO_RASCUNHO, null) == null) return
        Log.i(TAG, "Rascunho do computador ainda ativo na subida; voltando ao tema de antes.")
        encerrarRascunho(context, prefs)
    }
}
