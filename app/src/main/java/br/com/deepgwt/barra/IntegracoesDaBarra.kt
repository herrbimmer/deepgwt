// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.barra

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable

/**
 * Tudo o que a barra usa de OUTROS módulos. A sala suja implementa os adaptadores e entrega por
 * [Barra.configurar]. Sem configuração vale [VAZIAS]: nada quebra, só falta o que aquela fonte dá.
 */
interface IntegracoesDaBarra {
    /** Lançador de apps (3.8). */
    val lancador: LancadorParaABarra
    /** Monitor do CarPlay (extras, 3.10). */
    val carPlay: FonteDoCarPlay
    /** Fundo do álbum (extras, 3.10). */
    val coresDaCapa: CoresDaCapaParaABarra
    /** Projetar a central no painel de instrumentos. */
    val projecaoNoPainel: ProjecaoDaCentralNoPainel
    /** Guarda do launcher 3D. */
    val telaInicial: TelaInicialEscolhida
    /** Controlador do SPORT+. */
    val sportPlus: SportPlusParaABarra
    /** Perfis de motorista. */
    val perfis: PerfisParaABarra
    /** Nível de licença. */
    val licenca: LicencaParaABarra
    /** Print da tela principal. */
    val capturaDaTela: CapturaDaTelaPrincipal
    /** Registro persistente de eventos (diagnóstico). */
    val registro: RegistroDeEventosDaBarra
    /** Componente do serviço de acessibilidade do app, já achatado `pacote/classe` ("" = nenhum). */
    val componenteDaAcessibilidade: String

    companion object {
        /** Todas as dependências vazias e seguras. */
        @JvmField
        val VAZIAS: IntegracoesDaBarra = object : IntegracoesDaBarra {
            override val lancador = LancadorParaABarra.VAZIO
            override val carPlay = FonteDoCarPlay.VAZIA
            override val coresDaCapa = CoresDaCapaParaABarra { _, _ -> QuatroCores.RESERVA }
            override val projecaoNoPainel = ProjecaoDaCentralNoPainel.VAZIA
            override val telaInicial = TelaInicialEscolhida.VAZIA
            override val sportPlus = SportPlusParaABarra.VAZIO
            override val perfis = PerfisParaABarra.VAZIO
            override val licenca = LicencaParaABarra { false }
            override val capturaDaTela = CapturaDaTelaPrincipal { null }
            override val registro = RegistroDeEventosDaBarra { _, _ -> }
            override val componenteDaAcessibilidade = ""
        }
    }
}

/** Apps e displays. Tudo pode ir ao shell: nunca chamar na thread principal, salvo [projecaoReconhecida]. */
interface LancadorParaABarra {
    fun pacoteNoTopo(display: Int): String?

    /** "com.ts.carplay.app", "com.ts.androidauto.app" ou null. Rápido, sem shell; pode na principal. */
    fun projecaoReconhecida(pacote: String?): String?
    fun projecaoAtivaNoDisplay(display: Int): String?
    suspend fun corrigirTarjaPreta(pacote: String)

    /** Volta na hora; refaz os limites dos apps do display 0 em segundo plano. */
    fun reaplicarLimitesDoDisplay0()
    fun limparJanelasDoAndroidAutoSeDesconectado(motivo: String)

    // Apps da barra (menu Apps). A ordem e a lista são do lançador (preferência dele).
    fun appsConfigurados(): List<AppDaBarra>
    fun appsPredefinidos(): List<AppDaBarra>

    /** A configuração do app, criando a padrão se faltar; null se o app não abre. */
    suspend fun garantirApp(pacote: String): AppDaBarra?
    fun salvarOrdem(pacotes: List<String>)
    fun removerApp(pacote: String)

    /** Abre o app no display 0. Volta na hora (o lançamento sobrevive ao menu que fechou). */
    fun abrir(pacote: String)
    fun rotuloEIcone(pacote: String, nomePersonalizado: String?): RotuloEIcone

    // Android Auto (usado só pela mídia)
    fun sessaoAndroidAutoPronta(motivo: String): Boolean
    fun linkAndroidAutoAtivoSeJaLigado(motivo: String): Boolean
    fun temJanelaDoAndroidAuto(): Boolean
    fun audioDoAndroidAutoAtivo(motivo: String, forcarLeitura: Boolean = false): Boolean
    fun deixarParaAMidiaNativa(motivo: String): Boolean
    suspend fun prepararComandoAndroidAuto(motivo: String, pedirFocoDeVideo: Boolean = true): Boolean
    fun preferirRotaAap(motivo: String): Boolean
    fun pedirFocoDeVideoParaAlvo(tocar: Boolean): Boolean
    suspend fun pularPeloAap(paraFrente: Boolean): Boolean
    suspend fun reproducaoDireta(estaTocando: Boolean): Boolean
    suspend fun reproducaoPeloAap(estaTocando: Boolean): Boolean

    companion object {
        /** Sem lançador: nenhum app, nenhuma projeção, nenhum comando do Android Auto. */
        @JvmField
        val VAZIO: LancadorParaABarra = object : LancadorParaABarra {
            override fun pacoteNoTopo(display: Int): String? = null
            override fun projecaoReconhecida(pacote: String?): String? = null
            override fun projecaoAtivaNoDisplay(display: Int): String? = null
            override suspend fun corrigirTarjaPreta(pacote: String) = Unit
            override fun reaplicarLimitesDoDisplay0() = Unit
            override fun limparJanelasDoAndroidAutoSeDesconectado(motivo: String) = Unit
            override fun appsConfigurados(): List<AppDaBarra> = emptyList()
            override fun appsPredefinidos(): List<AppDaBarra> = emptyList()
            override suspend fun garantirApp(pacote: String): AppDaBarra? = null
            override fun salvarOrdem(pacotes: List<String>) = Unit
            override fun removerApp(pacote: String) = Unit
            override fun abrir(pacote: String) = Unit
            override fun rotuloEIcone(pacote: String, nomePersonalizado: String?) =
                RotuloEIcone(nomePersonalizado?.takeIf { it.isNotBlank() } ?: pacote, null)
            override fun sessaoAndroidAutoPronta(motivo: String) = false
            override fun linkAndroidAutoAtivoSeJaLigado(motivo: String) = false
            override fun temJanelaDoAndroidAuto() = false
            override fun audioDoAndroidAutoAtivo(motivo: String, forcarLeitura: Boolean) = false
            override fun deixarParaAMidiaNativa(motivo: String) = false
            override suspend fun prepararComandoAndroidAuto(motivo: String, pedirFocoDeVideo: Boolean) = false
            override fun preferirRotaAap(motivo: String) = false
            override fun pedirFocoDeVideoParaAlvo(tocar: Boolean) = false
            override suspend fun pularPeloAap(paraFrente: Boolean) = false
            override suspend fun reproducaoDireta(estaTocando: Boolean) = false
            override suspend fun reproducaoPeloAap(estaTocando: Boolean) = false
        }
    }
}

data class AppDaBarra(
    val pacote: String,
    val nomePersonalizado: String?,
    /**
     * Chave de ícone substituto ("nav", "music", "video", "settings", "haval", "game", "tv",
     * "phone", "chat", "map_alt", "youtube", "youtube_music", "gwm") ou null.
     */
    val iconeSubstituto: String?,
    /** Cor do ícone substituto, "#RRGGBB"/"#AARRGGBB", ou null. */
    val corDoIcone: String?,
)

data class RotuloEIcone(val rotulo: String, val icone: Drawable?)

/** CarPlay "tocando agora". Avisos e respostas na thread principal. */
interface FonteDoCarPlay {
    fun iniciar(aoAtualizar: (AtualizacaoDoCarPlay) -> Unit)
    fun parar()
    fun buscar(posicaoMs: Long): Boolean
    fun proxima(): Boolean
    fun anterior(): Boolean
    fun tocarOuPausar(estaTocando: Boolean): Boolean

    companion object {
        @JvmField
        val VAZIA: FonteDoCarPlay = object : FonteDoCarPlay {
            override fun iniciar(aoAtualizar: (AtualizacaoDoCarPlay) -> Unit) = Unit
            override fun parar() = Unit
            override fun buscar(posicaoMs: Long) = false
            override fun proxima() = false
            override fun anterior() = false
            override fun tocarOuPausar(estaTocando: Boolean) = false
        }
    }
}

data class AtualizacaoDoCarPlay(
    val titulo: String?,
    val artista: String?,
    val capa: Bitmap?,
    val caminhoDaCapa: String?,
    val duracaoMs: Long,
    val decorridoMs: Long,
    val tocando: Boolean,
    val limpar: Boolean,
)

/** Quatro cores da capa (CPU; chamar fora da principal). Sem capa → [QuatroCores.RESERVA]. */
fun interface CoresDaCapaParaABarra {
    fun cores(capa: Bitmap?, chave: String?): QuatroCores
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

/** Cores ARGB (inteiros), opacas. */
data class QuatroCores(val principal: Int, val secundaria: Int, val destaque: Int, val escura: Int) {
    companion object {
        private fun opaca(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

        @JvmField
        val RESERVA: QuatroCores =
            QuatroCores(opaca(31, 51, 58), opaca(65, 53, 31), opaca(102, 227, 255), opaca(5, 7, 10))
    }
}

/** Projetar a CENTRAL no painel de instrumentos. Leituras observáveis pelo Compose. */
interface ProjecaoDaCentralNoPainel {
    val projetando: Boolean
    val painelPronto: Boolean

    /** Começa a escolha da projeção, ou encerra se já está projetando. */
    fun alternar()

    companion object {
        @JvmField
        val VAZIA: ProjecaoDaCentralNoPainel = object : ProjecaoDaCentralNoPainel {
            override val projetando = false
            override val painelPronto = false
            override fun alternar() = Unit
        }
    }
}

/** A tela inicial escolhida (launcher 3D ou a de fábrica). */
interface TelaInicialEscolhida {
    fun launcher3DAtivo(): Boolean
    fun abrirLauncher3D(contexto: Context)
    fun aoIniciarBarra()
    fun aoMudarAppNoDisplay0(pacote: String)

    companion object {
        @JvmField
        val VAZIA: TelaInicialEscolhida = object : TelaInicialEscolhida {
            override fun launcher3DAtivo() = false
            override fun abrirLauncher3D(contexto: Context) = Unit
            override fun aoIniciarBarra() = Unit
            override fun aoMudarAppNoDisplay0(pacote: String) = Unit
        }
    }
}

interface SportPlusParaABarra {
    /** Começa a vigiar o modo de condução; [aoPedirAviso] = mostrar o aviso "SPORT+ ativado". */
    fun iniciarMonitoramento(contexto: Context, aoPedirAviso: () -> Unit)
    fun pararMonitoramento()
    fun ligar()
    fun desligar()
    fun abrirSoOPainelEsportivo()

    companion object {
        @JvmField
        val VAZIO: SportPlusParaABarra = object : SportPlusParaABarra {
            override fun iniciarMonitoramento(contexto: Context, aoPedirAviso: () -> Unit) = Unit
            override fun pararMonitoramento() = Unit
            override fun ligar() = Unit
            override fun desligar() = Unit
            override fun abrirSoOPainelEsportivo() = Unit
        }
    }
}

interface PerfisParaABarra {
    fun nomes(): List<String>
    fun ativo(): String?

    /** Lento (lê ~800 chaves do carro): nunca na principal. */
    fun aplicar(nome: String): RelatorioDoPerfil

    companion object {
        @JvmField
        val VAZIO: PerfisParaABarra = object : PerfisParaABarra {
            override fun nomes(): List<String> = emptyList()
            override fun ativo(): String? = null
            override fun aplicar(nome: String) =
                RelatorioDoPerfil(false, "Perfis indisponíveis nesta versão.", emptyList())
        }
    }
}

data class RelatorioDoPerfil(val tudoCerto: Boolean, val resumo: String, val detalhes: List<String>)

fun interface LicencaParaABarra {
    fun indicadorDeRecursosLiberado(): Boolean
}

/** Lento (shell); fora da principal. */
fun interface CapturaDaTelaPrincipal {
    fun png(): ByteArray?
}

fun interface RegistroDeEventosDaBarra {
    fun registrar(categoria: String, campos: Map<String, Any?>)
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
