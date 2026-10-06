// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.painel

import android.content.Context
import android.content.SharedPreferences
import br.com.deepgwt.ponte.AppDoLauncher
import br.com.deepgwt.ponte.Cancelavel
import br.com.deepgwt.ponte.Fontes
import br.com.deepgwt.ponte.MidiaDoApp
import br.com.deepgwt.ponte.ModoDeExibicao
import br.com.deepgwt.veiculo.Veiculo
import java.io.File

/**
 * A [Fontes] da ponte (`projetores-painel.md`, seção 2): tema ativo, fundo e modos de exibição
 * saem do próprio módulo (preferência e [TemasInstalados]); o resto delega a [FontesDeFora].
 */
class FontesDoPainel(preferencias: SharedPreferences, private val deFora: FontesDeFora) : Fontes {
    private val p = PreferenciasDoPainel(preferencias)

    /** `activeCustomTheme` (`Default` → ""). */
    override fun temaAtivo(): String = p.pastaAtiva()

    override fun midiaDoApp(): MidiaDoApp = deFora.midiaDoApp()

    override fun appNoTopo(display: Int): String? = deFora.appNoTopo(display)

    override fun rotuloDoApp(pacote: String): String = deFora.rotuloDoApp(pacote)

    override fun iconeDoApp(pacote: String): String = deFora.iconeDoApp(pacote)

    override fun appsDoLauncher(): List<AppDoLauncher> = deFora.appsDoLauncher()

    override fun arquivoDeFundoDoTema(dica: String?): String? = TemasInstalados.arquivoDeFundoDoTemaAtivo(dica)?.absolutePath

    /** Os do tema ativo; sem tema ativo ou não encontrado, os do embutido (lista vazia). */
    override fun modosDeExibicaoDoTema(): List<ModoDeExibicao> {
        val pasta = temaAtivo()
        if (pasta.isNotEmpty()) TemasInstalados.ler(pasta)?.let { return it.modosDeExibicao }
        return TemasInstalados.embutido().modosDeExibicao
    }
}

/** O carro de verdade: a fachada [Veiculo]. */
internal object CarroDoApp : CarroDoPainel {
    override fun ler(chave: String): String? = try {
        Veiculo.ler(chave)
    } catch (e: Exception) {
        null
    }

    override fun observar(ouvinte: (String, String?) -> Unit): Cancelavel {
        val o = Veiculo.Ouvinte { chave, valor -> ouvinte(chave, valor) }
        Veiculo.observar(o)
        return Cancelavel { Veiculo.pararDeObservar(o) }
    }
}

/** O que o papel de parede e as máscaras precisam dos temas instalados. */
internal object TemasDoApp : PapelDeParede.Temas {
    override fun fundoDoTemaAtivo(dica: String?): String? = TemasInstalados.arquivoDeFundoDoTemaAtivo(dica)?.absolutePath

    override fun arquivoNoTema(pasta: String, nome: String): String? = TemasInstalados.arquivo(pasta, nome)?.absolutePath

    override fun arquivoExiste(caminho: String): Boolean = try {
        File(caminho).isFile
    } catch (e: Exception) {
        false
    }
}

/**
 * C.1–C.3 no app: o tema da pasta (contrato v1.0, ponte aceita, arquivo principal existe) ou a
 * página do painel original do APK; o HTML de depuração em build depurável (Decisão 8).
 */
internal class PaginasDoApp(private val contexto: Context, private val depuravel: Boolean) : PaginasDoPainel {

    companion object {
        const val PASTA_DA_ORIGINAL = "PainelOriginal"
        const val ARQUIVO_DA_ORIGINAL = "PainelOriginal/index.html"
        const val BASE_DA_ORIGINAL = "file:///android_asset/PainelOriginal/"
        const val HTML_DE_DEPURACAO = "/data/local/tmp/app.html"
        private const val PAGINA_VAZIA = "<!doctype html><html><head><meta charset=\"utf-8\"></head><body></body></html>"
    }

    private fun original() = PaginaDoPainel(null, BASE_DA_ORIGINAL, null, ARQUIVO_DA_ORIGINAL)

    override fun escolher(pastaAtiva: String): PaginaDoPainel {
        try {
            if (pastaAtiva.isNotEmpty()) {
                val tema = TemasInstalados.ler(pastaAtiva)
                if (tema != null && TemasInstalados.suportado(tema)) {
                    val principal = tema.arquivoPrincipal.ifEmpty { LeitorDoTema.ARQUIVO_PRINCIPAL_PADRAO }
                    val arquivo = TemasInstalados.arquivo(pastaAtiva, principal)
                    if (arquivo != null) {
                        val base = "file://" + File(TemasInstalados.pastaDosTemas, pastaAtiva).absolutePath + "/"
                        return PaginaDoPainel(tema, base, tema.versaoMinimaDaPonte, arquivo.absolutePath)
                    }
                }
            }
        } catch (e: Exception) {
            // Qualquer erro de leitura: a página original.
        }
        return original()
    }

    override fun ler(pagina: PaginaDoPainel): Pair<PaginaDoPainel, String> {
        if (depuravel) lerDepuracao()?.let { return pagina to it }
        return try {
            pagina to if (pagina.tema == null) lerAsset(pagina.origem) else File(pagina.origem).readText()
        } catch (e: Exception) {
            val reserva = original()
            try {
                reserva to lerAsset(reserva.origem)
            } catch (e2: Exception) {
                reserva to PAGINA_VAZIA
            }
        }
    }

    private fun lerAsset(caminho: String): String = contexto.assets.open(caminho).use { it.readBytes().toString(Charsets.UTF_8) }

    /** C.2: existe, legível, não em branco e com `<html` ou `<!doctype html`. */
    private fun lerDepuracao(): String? = try {
        val arquivo = File(HTML_DE_DEPURACAO)
        if (!arquivo.isFile || !arquivo.canRead()) {
            null
        } else {
            val texto = arquivo.readText()
            val minusculo = texto.lowercase()
            if (texto.isBlank() || !(minusculo.contains("<html") || minusculo.contains("<!doctype html"))) null else texto
        }
    } catch (e: Exception) {
        null
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
