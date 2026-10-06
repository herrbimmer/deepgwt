package br.com.deepgwt.tela3d.catalogo

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap

/**
 * Catálogo de apps instalados e estado do usuário sobre eles (ocultos, recentes, janela,
 * personalização). Alimenta a página 3D (`getInstalledApps`, `getAppIcon`) e a faixa nativa.
 *
 * Os três métodos da classe são os que a ponte encaminha; o resto da API (listas da faixa, ações,
 * consultas do módulo de janelas) está em funções de extensão, em `CatalogoDeAppsApi.kt`.
 *
 * Threads: `appsInstaladosComoJson` e `iconeComoDataUrl` chegam na thread de ponte da WebView; o
 * estado do usuário é um retrato imutável trocado sob trava, então a leitura é segura de qualquer
 * thread. Todo aviso a ouvintes (faixa) é entregue na thread principal.
 *
 * @param contexto o Context da tela inicial (só o `applicationContext` é guardado)
 * @param prefs preferências `deepgwt_lancador`
 * @param principal Handler da thread principal
 */
class CatalogoDeApps(
    contexto: Context,
    prefs: SharedPreferences,
    internal val projecao: ProjecaoParaCatalogo,
    internal val tema: TemaDaFaixa,
    private val invalidador: InvalidadorDaPagina,
    private val principal: Handler,
    internal val emulador: Boolean = detectarEmulador(),
) {
    internal val contexto: Context = contexto.applicationContext ?: contexto

    internal val regras = RegrasDoCatalogo(this.contexto.packageName) { projecao.ehPacoteDeProjecao(it) }
    internal val fonte: FonteDeApps = FonteDeAppsDoSistema(this.contexto.packageManager)
    internal val planejador = PlanejadorDeApps(regras, emulador)
    internal val fabrica = FabricaDeIcones(this.contexto, fonte, regras)
    internal val repositorio = RepositorioDoUsuario(ArmazemDeSharedPreferences(prefs), regras) { Log.w(ETIQUETA, it) }

    private val ouvintes = CopyOnWriteArrayList<OuvinteDoCatalogo>()

    init {
        // Criar ou tirar um atalho de ajuste (nas telas do app) refaz a grade do launcher.
        br.com.deepgwt.ajustes.AtalhosDeAjuste.aoMudar = { principal.post { invalidarPagina() } }
    }

    // Cache do `getAppIcon`: data URL pronto por pacote e o conjunto de pacotes "sem ícone".
    private val iconesPorPacote = ConcurrentHashMap<String, String>()
    private val pacotesSemIcone: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** `getInstalledApps`: texto JSON do catálogo da página. Nunca lança; em erro devolve o que já montou. */
    fun appsInstaladosComoJson(): String {
        val entradas = ArrayList<EntradaDoCatalogo>()
        try {
            val estado = repositorio.estado
            val temaClaro = tema.claro
            val atividades = fonte.atividadesDeLancador()
            for (item in planejador.catalogo(atividades, estado)) {
                val personalizacao = estado.personalizacoes.ler(item.pacote)
                entradas.add(EntradaDoCatalogo(item.pacote, item.rotulo, fabrica.dataUrlDoCatalogo(item, personalizacao, temaClaro)))
            }
        } catch (e: Exception) {
            Log.w(ETIQUETA, "Falha ao montar o catálogo de apps", e)
        } catch (e: OutOfMemoryError) {
            Log.w(ETIQUETA, "Sem memória ao montar o catálogo de apps", e)
        }
        // Os atalhos de ajuste vão depois dos apps (Alpha 10.0).
        entradas.addAll(AtalhosNoCatalogo.entradas())
        return JsonDoCatalogo.montar(entradas)
    }

    /**
     * `getAppIcon`: o ícone verdadeiro do pacote (sem personalização, placa nem emblema) como data
     * URL PNG 96×96; `""` se não houver.
     */
    fun iconeComoDataUrl(pacote: String?): String {
        if (pacote.isNullOrEmpty()) return ""
        iconesPorPacote[pacote]?.let { return it }
        if (pacote in pacotesSemIcone) return ""
        val ehProjecao = projecao.ehPacoteDeProjecao(pacote)
        try {
            val icone = if (ehProjecao) {
                iconeDeProjecao(pacote)
            } else {
                NormalizacaoDeIcone.normalizar(contexto.resources, fonte.iconeDoAplicativo(pacote), CodificadorDeIcone.LADO)
            }
            val dataUrl = CodificadorDeIcone.paraDataUrl(icone, null)
            if (dataUrl != null) {
                iconesPorPacote[pacote] = dataUrl
                return dataUrl
            }
        } catch (e: Exception) {
            Log.w(ETIQUETA, "Sem ícone para $pacote", e)
        } catch (e: OutOfMemoryError) {
            Log.w(ETIQUETA, "Sem memória no ícone de $pacote", e)
            return ""
        }
        // Falhou: projeção que não é CarPlay cai no ícone do Android Auto; o resto vai para o cache negativo.
        if (ehProjecao && !pacote.lowercase(Locale.ROOT).contains("carplay")) {
            val reserva = try {
                CodificadorDeIcone.paraDataUrl(fabrica.recurso(br.com.deepgwt.R.drawable.ic_android_auto), null)
            } catch (e: Exception) {
                null
            }
            if (reserva != null) {
                iconesPorPacote[pacote] = reserva
                return reserva
            }
        }
        pacotesSemIcone.add(pacote)
        return ""
    }

    /**
     * Receptor de remoção de pacote. Remoção com [substituindo] verdadeiro é atualização do app:
     * ignorada (não perde ocultos, janela nem recentes). Remoção de verdade limpa o estado do
     * pacote (menos a personalização) e avisa a faixa para tirar o chip.
     */
    fun aoPacoteRemovido(pacote: String, substituindo: Boolean) {
        if (pacote.isEmpty()) return
        esquecerIcone(pacote)
        if (substituindo) return
        repositorio.aoDesinstalar(pacote)
        notificar(MotivoDeMudanca(TipoDeMudanca.PACOTE_REMOVIDO, pacote))
        invalidarPagina()
    }

    /** Pacote instalado ou atualizado (`PACKAGE_ADDED`/`PACKAGE_REPLACED`): refaz ícone, linha e catálogo. */
    fun aoPacoteInstalado(pacote: String) {
        if (pacote.isEmpty()) return
        esquecerIcone(pacote)
        notificar(MotivoDeMudanca(TipoDeMudanca.PACOTE_INSTALADO, pacote))
        invalidarPagina()
    }

    /** A faixa mudou entre tema claro e escuro: as placas do catálogo mudam de cor. */
    fun aoMudarTema() {
        notificar(MotivoDeMudanca(TipoDeMudanca.TEMA))
        invalidarPagina()
    }

    internal fun registrarOuvinteInterno(ouvinte: OuvinteDoCatalogo) {
        ouvintes.addIfAbsent(ouvinte)
    }

    /** Avisa os ouvintes na thread principal (executa na hora se já está nela). */
    internal fun notificar(motivo: MotivoDeMudanca) {
        if (ouvintes.isEmpty()) return
        val acao = Runnable {
            for (o in ouvintes) {
                try {
                    o.aoMudar(motivo)
                } catch (e: Exception) {
                    Log.w(ETIQUETA, "Ouvinte do catálogo falhou", e)
                }
            }
        }
        if (Looper.myLooper() === principal.looper) acao.run() else principal.post(acao)
    }

    /** Pede à página que releia o catálogo. */
    internal fun invalidarPagina() {
        try {
            invalidador.invalidarCatalogo()
        } catch (e: Exception) {
            Log.w(ETIQUETA, "Falha ao invalidar o catálogo da página", e)
        }
    }

    private fun esquecerIcone(pacote: String) {
        iconesPorPacote.remove(pacote)
        pacotesSemIcone.remove(pacote)
    }

    /** Ícone de projeção (C4.3): CarPlay se o pacote contém "carplay", senão Android Auto; reserva `ic_*_default`. */
    private fun iconeDeProjecao(pacote: String): android.graphics.drawable.Drawable? {
        return if (pacote.lowercase(Locale.ROOT).contains("carplay")) {
            projecao.iconeDaProjecao(TipoDeProjecao.CARPLAY) ?: fabrica.recurso(br.com.deepgwt.R.drawable.ic_carplay_default)
        } else {
            projecao.iconeDaProjecao(TipoDeProjecao.ANDROID_AUTO) ?: fabrica.recurso(br.com.deepgwt.R.drawable.ic_android_auto_default)
        }
    }

    internal companion object {
        const val ETIQUETA = "DeepGWT/Catalogo"
    }
}

/** Emulador: pelas propriedades de [Build]. */
fun detectarEmulador(): Boolean =
    detectarEmulador(Build.FINGERPRINT, Build.MODEL, Build.PRODUCT, Build.HARDWARE)

/** Regra pura de emulador (F1), com as propriedades por parâmetro. */
fun detectarEmulador(fingerprint: String?, modelo: String?, produto: String?, hardware: String?): Boolean {
    val f = fingerprint.orEmpty()
    val m = modelo.orEmpty()
    val p = produto.orEmpty()
    val h = hardware.orEmpty()
    return f.startsWith("generic") || f.contains("emulator") ||
        m.contains("sdk_gphone") || m.contains("Android SDK built for") ||
        p.contains("sdk") || p.contains("emulator") ||
        h.contains("goldfish") || h.contains("ranchu")
}
