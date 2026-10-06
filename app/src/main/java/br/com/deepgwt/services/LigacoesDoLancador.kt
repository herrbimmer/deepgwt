// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.services

import android.content.Context
import android.os.Handler
import android.os.Looper
import br.com.deepgwt.barra.Barra as BarraNova
import br.com.deepgwt.barra.EstadoDaBarra
import br.com.deepgwt.barra.MidiaDaBarra
import br.com.deepgwt.telas.AreaDoTemaGravada
import br.com.deepgwt.telas.AvisosAoPainel
import br.com.deepgwt.telas.BarraParaTelas
import br.com.deepgwt.telas.DependenciasDasTelas
import br.com.deepgwt.telas.DiarioDasTelas
import br.com.deepgwt.telas.EstadoDoDcm
import br.com.deepgwt.telas.GanchoDoLauncher3d
import br.com.deepgwt.telas.LancadorDeApps
import br.com.deepgwt.telas.LeituraDoDcm
import br.com.deepgwt.monitores.ConexoesDaCentral
import br.com.deepgwt.nucleo.DeepGwt
import br.com.deepgwt.nucleo.Preferencias
import br.com.deepgwt.painel.Diario
import br.com.deepgwt.painel.PainelDeInstrumentos
import br.com.deepgwt.painel.TemasInstalados
import br.com.deepgwt.managers.GuardaDoLauncher3D

/**
 * Liga o lançador novo (br.com.deepgwt.telas, 3.8) aos outros módulos: a barra (já nova, 3.7), o
 * painel projetado, os temas instalados, o DCM, o diário e a guarda do launcher 3D. Cada
 * adaptador some quando o módulo dele for reescrito e passar a falar com o lançador direto.
 */
object LigacoesDoLancador {

    private val principal = Handler(Looper.getMainLooper())

    @JvmStatic
    fun ligar(contexto: Context) {
        val app = contexto.applicationContext ?: contexto
        LancadorDeApps.instalar(
            DependenciasDasTelas(
                barra = Barra,
                painel = Painel,
                // A área sai da PASTA do tema ativo, não do nome de exibição que o lançador passa
                // (projetores-temas.md, D1): dois temas com o mesmo nome, ou um nome trocado, não
                // confundem mais a posição dos apps.
                tema = AreaDoTemaGravada { _ ->
                    val pasta = DeepGwt.contextoProtegido.getSharedPreferences("deepgwt_prefs", Context.MODE_PRIVATE)
                        .getString(Preferencias.ACTIVE_CUSTOM_THEME.chave, "").orEmpty().trim()
                    if (pasta.isEmpty()) return@AreaDoTemaGravada null
                    val a = TemasInstalados.ler(pasta)?.areaDosApps ?: return@AreaDoTemaGravada null
                    intArrayOf(a.x, a.y, a.largura, a.altura)
                },
                dcm = EstadoDoDcm {
                    val aparelhos = ConexoesDaCentral.lerAparelhos(app)
                    LeituraDoDcm(
                        androidAutoAtivo = aparelhos.any { it.temProjecaoAndroidAutoAtiva() },
                        resumo = aparelhos.joinToString(prefix = "[", postfix = "]")
                    )
                },
                diario = DiarioDasTelas { evento, detalhes -> Diario.registrar(evento, detalhes) },
                launcher3d = GanchoDoLauncher3d { pacote -> GuardaDoLauncher3D.aoAbrirPacote(pacote) },
            )
        )
    }

    /** O lançador lendo e escrevendo a barra nova (br.com.deepgwt.barra, 3.7). */
    private object Barra : BarraParaTelas {
        override val alturaDp: Int get() = EstadoDaBarra.alturaDp
        override val visivel: Boolean get() = EstadoDaBarra.visivel
        override val pacoteDaMidia: String? get() = MidiaDaBarra.agora.pacote
        override val tocando: Boolean get() = MidiaDaBarra.agora.tocando
        override val tituloDaMidia: String? get() = MidiaDaBarra.agora.titulo
        override val artistaDaMidia: String? get() = MidiaDaBarra.agora.artista
        override val albumDaMidia: String? get() = MidiaDaBarra.agora.album
        override val duracaoDaMidiaMs: Long get() = MidiaDaBarra.agora.duracaoMs
        override val espelhamentoNoPainel: String get() = EstadoDaBarra.projecaoNoPainel

        /** O lançador só manda com o pacote do Android Auto; a barra quer a dica na principal. */
        override fun dicaDeReproducao(pacote: String, tocando: Boolean) {
            principal.post { MidiaDaBarra.dicaDoVolanteAndroidAuto(tocando) }
        }

        override fun marcarRestaurado(pacote: String) = EstadoDaBarra.appsRestaurados.adicionar(pacote)

        override fun desmarcarRestaurado(pacote: String) = EstadoDaBarra.appsRestaurados.remover(pacote)

        override fun rotaNativaDoAndroidAuto(): Boolean = MidiaDaBarra.rotaNativaDoAndroidAutoAtiva()

        override fun rotaNativaTocando(): Boolean? = MidiaDaBarra.androidAutoTocandoPelaCentral()

        override suspend fun alvoPelaRotaNativa(tocar: Boolean, motivo: String): Boolean =
            MidiaDaBarra.androidAutoReproducaoPelaCentral(tocar, motivo)

        override fun alternarMudoDoAndroidAuto(): Boolean = MidiaDaBarra.alternarMudoDoAndroidAuto()

        override fun zerarProgressoAposTrocaDeFaixa(motivo: String): Boolean =
            MidiaDaBarra.androidAutoTrocouDeFaixa(motivo)

        override fun voltarAposFocoExterno(pacote: String?, motivo: String) =
            BarraNova.aoFocoExterno(pacote, motivo)

        override fun reabrirAposEspelhamentoNoPainel(motivo: String) =
            BarraNova.aoProjecaoIrParaOPainel(motivo)
    }

    private object Painel : AvisosAoPainel {
        override fun appNoDisplay(display: Int, ativo: Boolean) {
            if (display != 1 && display != 3) return
            PainelDeInstrumentos.appsNoDisplayMudaram(display, ativo)
        }

        override fun geometriaMudou() = PainelDeInstrumentos.geometriaDosAppsMudou()

        override fun prepararVaoNoDisplay3(limites: IntArray) {
            if (limites.size < 4) return
            PainelDeInstrumentos.prepararVaoNoDisplay3(limites[0], limites[1], limites[2], limites[3])
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
