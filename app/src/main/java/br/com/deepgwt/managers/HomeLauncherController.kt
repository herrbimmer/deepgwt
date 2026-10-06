// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.managers

import br.com.deepgwt.nucleo.Shell
import br.com.deepgwt.nucleo.DeepGwt
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import br.com.deepgwt.nucleo.Preferencias
import org.json.JSONArray
import org.json.JSONObject

/**
 * Qual tela aparece quando o carro liga.
 *
 * O H6 tem o launcher de fábrica (`com.beantechs.mediacenter`) e a tela inicial 3D do próprio
 * DeepGWT (a Activity `br.com.deepgwt.tela3d.ponte.TelaInicial3dActivity`, dentro deste APK desde a fusão dos dois
 * apps). Os dois declaram `category.HOME`; quem decide é a preferência de HOME do Android, que só o `cmd package set-home-activity` muda sem diálogo.
 *
 * Por que não abrir o seletor do sistema: na central o diálogo "Usar por padrão" aparece atrás da
 * barra do OEM e em vários casos nem é tocável. Trocar por comando é o único caminho que funciona
 * de dentro do carro.
 *
 * Nada aqui desinstala nem desativa o launcher de fábrica. A volta é sempre o mesmo comando com o
 * pacote do OEM — é por isso que "Voltar ao launcher de fábrica" pode ser um botão e não um aviso
 * assustador.
 *
 * Desde a Alpha 7.8 escolher o launcher 3D também liga a [GuardaDoLauncher3D], que o devolve
 * quando a GWM abre a tela de fábrica por conta própria (ver [setHome]).
 */
object HomeLauncherController {
    private const val TAG = "HomeLauncherController"

    const val FACTORY_PACKAGE = "com.beantechs.mediacenter"

    /**
     * A tela inicial 3D deixou de ser um app separado: foi incorporada a este APK e virou uma
     * Activity daqui (`TelaInicial3dActivity`, com `category.HOME`; as versões anteriores a
     * registravam como `com.havalh6.viewer.MainActivity`). O pacote, portanto, é o nosso — e `viewerInstalled` passa a ser sempre
     * verdadeiro, porque a tela inicial vem junto com o DeepGWT e não dá mais para ter um sem o
     * outro. O nome da constante fica como está: é assim que a interface e a rota do GWLink já
     * chamam esta opção.
     */
    const val VIEWER_PACKAGE = br.com.deepgwt.BuildConfig.APPLICATION_ID

    data class HomeCandidate(
        val packageName: String,
        val activityName: String,
        val label: String,
        val isCurrent: Boolean
    )

    /** Todo app que se oferece como tela inicial, com o atual marcado. */
    fun candidates(context: Context = DeepGwt.contexto): List<HomeCandidate> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val current = currentHomePackage(context)
        // MATCH_DEFAULT_ONLY some launchers fail: um launcher de terceiro (o caso relatado foi o
        // COFFE OS) pode declarar `category.HOME` sem declarar `category.DEFAULT`, e aí ele nem
        // aparecia nesta lista — o usuário via o app instalado no carro e não via a opção aqui.
        // MATCH_ALL devolve todo mundo que se oferece como HOME; o que interessa para
        // `set-home-activity` é ser HOME, não ser DEFAULT.
        return runCatching {
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                .mapNotNull { info ->
                    val activity = info.activityInfo ?: return@mapNotNull null
                    // O resolvedor do próprio Android aparece na lista quando não há padrão
                    // definido; oferecê-lo como opção só levaria o usuário de volta ao diálogo.
                    if (activity.packageName == "android") return@mapNotNull null
                    // Nem o assistente de configuração nem a "casa de reserva" das Configurações
                    // são tela inicial de verdade: escolhê-los deixaria o carro numa tela vazia.
                    if (activity.packageName == "com.android.provision" ||
                        activity.name.endsWith("FallbackHome")
                    ) return@mapNotNull null
                    HomeCandidate(
                        packageName = activity.packageName,
                        activityName = activity.name,
                        label = runCatching { info.loadLabel(pm).toString() }
                            .getOrDefault(activity.packageName),
                        isCurrent = activity.packageName == current
                    )
                }
        }.onFailure { Log.w(TAG, "Falha ao listar telas iniciais: ${it.message}") }
            .getOrDefault(emptyList())
    }

    /**
     * Pacote da tela inicial atual. `resolveActivity` devolve o resolvedor do sistema quando
     * nenhum padrão foi escolhido ainda — nesse caso não há "atual" de verdade.
     */
    fun currentHomePackage(context: Context = DeepGwt.contexto): String? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = runCatching {
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrNull() ?: return null
        val pkg = resolved.activityInfo?.packageName ?: return null
        return if (pkg == "android") null else pkg
    }

    /**
     * Pacote E classe da tela inicial atual (`null` sem padrão escolhido). Comparar só o pacote não
     * basta: o app instalado antes registrou como preferida a Activity antiga
     * (`com.havalh6.viewer.MainActivity`), que tem o MESMO pacote e já não existe.
     */
    fun currentHomeComponent(context: Context = DeepGwt.contexto): Pair<String, String>? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = runCatching {
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrNull() ?: return null
        val activity = resolved.activityInfo ?: return null
        return if (activity.packageName == "android") null else activity.packageName to activity.name
    }

    /** O HOME atual é exatamente [pacote]/[classe]? Pura, para os testes de JVM. */
    internal fun ehOHomeAtual(atual: Pair<String, String>?, pacote: String, classe: String): Boolean =
        atual != null && atual.first == pacote && atual.second == classe

    /** O HOME atual é a tela inicial 3D de HOJE (nem o pacote só, nem a Activity antiga)? */
    internal fun ehATela3dAtual(atual: Pair<String, String>?): Boolean =
        ehOHomeAtual(atual, VIEWER_PACKAGE, GuardaDoLauncher3D.COMPONENTE_3D)

    fun isInstalled(packageName: String, context: Context = DeepGwt.contexto): Boolean =
        runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess

    /** O launcher 3D está escolhido como tela inicial (e a [GuardaDoLauncher3D] ligada)? */
    fun launcher3DFixo(): Boolean = GuardaDoLauncher3D.ativa()

    private fun gravarLauncher3DFixo(fixo: Boolean) {
        DeepGwt.contextoProtegido
            .getSharedPreferences("deepgwt_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(Preferencias.HOME_LAUNCHER3D_FIXO.chave, fixo)
            .apply()
    }

    @Volatile private var pacotesDeInicio: Set<String>? = null

    /** [pacote] se oferece como tela inicial (e não é o DeepGWT)? Lista lida uma vez. */
    fun ehTelaInicial(pacote: String): Boolean {
        val conhecidos = pacotesDeInicio ?: candidates().map { it.packageName }
            .filter { it != VIEWER_PACKAGE }.toSet().also { pacotesDeInicio = it }
        return pacote in conhecidos
    }

    /**
     * Troca a tela inicial.
     *
     * ## DUAS CAMADAS (Alpha 7.8)
     *
     * 1. A preferência de HOME do Android, por comando (Shizuku): é ela que decide a tecla Início e
     *    o boot padrão.
     * 2. Para o launcher 3D, a [GuardaDoLauncher3D]: a tela de fábrica desta central também é
     *    aberta por caminhos da GWM que não perguntam nada ao Android — era isso o "não aplica de
     *    fato" relatado. A escolha é gravada ANTES do comando, então a guarda vale mesmo quando o
     *    sistema recusa a preferência.
     *
     * Em qualquer escolha a tela escolhida é aberta na hora — antes o motorista escolhia e nada
     * mudava na frente dele até apertar Início.
     *
     * Devolve a saída dos comandos em caso de falha, para a interface poder dizer o que aconteceu.
     *
     * [gravarEscolha] falso (Alpha 8.0) troca a tela sem esquecer a escolha: é o nível que perdeu o
     * launcher 3D devolvendo a de fábrica, e a escolha gravada volta a valer com o nível de volta.
     * [trazerParaFrente] falso troca o papel de tela inicial sem trazer a tela para a frente — a mudança de nível
     * não pode tirar a pessoa do que ela está vendo.
     */
    fun setHome(
        packageName: String,
        context: Context = DeepGwt.contexto,
        gravarEscolha: Boolean = true,
        trazerParaFrente: Boolean = true
    ): Result<String> {
        val candidate = candidates(context).firstOrNull { it.packageName == packageName }
            ?: return Result.failure(
                IllegalStateException("$packageName não está instalado ou não se oferece como tela inicial.")
            )
        val eh3D = packageName == VIEWER_PACKAGE
        if (gravarEscolha) gravarLauncher3DFixo(eh3D)
        val doSistema = aplicarPreferenciaDoSistema(candidate, context)
        if (eh3D) {
            if (trazerParaFrente) GuardaDoLauncher3D.abrirLauncher3D(context)
            return Result.success(
                doSistema.fold(
                    onSuccess = { "o Android aceitou ($it) e o DeepGWT mantém o launcher 3D" },
                    onFailure = { "o Android não aceitou a troca direta, e o DeepGWT mantém o launcher 3D sozinho" }
                )
            )
        }
        GuardaDoLauncher3D.liberarFabrica(if (gravarEscolha) "escolhida em Tela inicial" else "o nível não libera o launcher 3D")
        if (trazerParaFrente) abrir(candidate, context)
        return doSistema
    }

    /**
     * A preferência de HOME do Android — UM comando só não bastava.
     *
     * `cmd package set-home-activity` existe desde o Android 7 e, nesta central (Android 9), o shell
     * do Shizuku tem a permissão que ele pede (`SET_PREFERRED_APPLICATIONS`). No Android 10+ quem
     * decide é o RoleManager, e o `set-home-activity` responde "Success" sem mudar nada — é o
     * emulador automotivo. Por isso a ordem depende da versão, e a verificação é o estado do
     * aparelho depois, não a palavra "Success".
     */
    private fun aplicarPreferenciaDoSistema(candidate: HomeCandidate, context: Context): Result<String> {
        if (!Shell.disponivel()) {
            return Result.failure(IllegalStateException("Shizuku indisponível — trocar a tela inicial é operação de sistema."))
        }
        val component = ComponentName(candidate.packageName, candidate.activityName).flattenToString()
        // A preferência de tela inicial é POR USUÁRIO. No carro só existe o usuário 0; em Android
        // Automotive o app roda como usuário 10 e o comando sem `--user` grava a do usuário 0.
        val userId = (android.os.Process.myUid() / 100000).toString()

        val porPapel = "role" to arrayOf(
            "cmd", "role", "add-role-holder", "--user", userId,
            "android.app.role.HOME", candidate.packageName
        )
        val porPreferencia = "set-home-activity" to arrayOf(
            "cmd", "package", "set-home-activity", "--user", userId, component
        )
        // Sem `--user`: alguns builds do `cmd package` recusam o parâmetro em vez de ignorá-lo.
        val semUsuario = "set-home-activity (sem --user)" to arrayOf(
            "cmd", "package", "set-home-activity", component
        )
        val attempts =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                listOf(porPapel, porPreferencia, semUsuario)
            } else {
                // Android 9: não há RoleManager — `cmd role` só gastaria tempo.
                listOf(porPreferencia, semUsuario)
            }

        val log = StringBuilder()
        for ((label, command) in attempts) {
            val output = runCatching { Shell.executar(command) }
                .getOrNull()?.trim().orEmpty()
            log.append(label).append(": ").append(output.ifBlank { "(sem saída)" }).append('\n')
            if (ehOHomeAtual(currentHomeComponent(context), candidate.packageName, candidate.activityName)) {
                Log.w(TAG, "Tela inicial agora é $component (via $label)")
                return Result.success(label)
            }
        }

        Log.e(TAG, "Falha ao definir tela inicial $component:\n$log")
        return Result.failure(
            IllegalStateException(
                "nenhum dos caminhos do sistema aceitou a troca. Saída:\n${log.toString().trim()}"
            )
        )
    }

    /** Com o launcher 3D escolhido, repõe a preferência do Android se alguém a trocou (boot). */
    fun reafirmarEmSegundoPlano() {
        Thread({
            runCatching {
                val context = DeepGwt.contexto
                if (!launcher3DFixo() || ehATela3dAtual(currentHomeComponent(context))) return@runCatching
                val nosso = candidates(context).firstOrNull { it.packageName == VIEWER_PACKAGE } ?: return@runCatching
                aplicarPreferenciaDoSistema(nosso, context)
                    .onSuccess { Log.w(TAG, "preferência de tela inicial reposta para o launcher 3D ($it)") }
            }
        }, "ReafirmarLauncher3D").start()
    }

    /** Aplica o que está gravado (o instalador mudou a chave pela rede). */
    fun aplicarEscolhaGravada(context: Context = DeepGwt.contexto): Result<String> =
        setHome(if (launcher3DFixo()) VIEWER_PACKAGE else FACTORY_PACKAGE, context)

    /**
     * Volta à tela de fábrica por qualquer caminho de emergência (receiver, instalador, Telas).
     * Em central de outro modelo a tela de fábrica pode ser outro pacote: sem o
     * [FACTORY_PACKAGE], vale a primeira outra tela inicial instalada.
     */
    fun voltarParaFabrica(context: Context = DeepGwt.contexto): Result<String> {
        gravarLauncher3DFixo(false)
        val alvo = telaDeFabrica(context)
            ?: return Result.failure(IllegalStateException("nenhuma outra tela inicial instalada."))
        return setHome(alvo, context)
    }

    /** A tela inicial de fábrica: o [FACTORY_PACKAGE] ou, em outra central, a primeira que não é a nossa. */
    fun telaDeFabrica(context: Context = DeepGwt.contexto): String? {
        val outras = candidates(context).filter { it.packageName != VIEWER_PACKAGE }
        return (outras.firstOrNull { it.packageName == FACTORY_PACKAGE } ?: outras.firstOrNull())?.packageName
    }

    private fun abrir(candidate: HomeCandidate, context: Context) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .setComponent(ComponentName(candidate.packageName, candidate.activityName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Log.w(TAG, "não deu para abrir ${candidate.packageName}: ${it.message}") }
    }

    fun toJson(context: Context = DeepGwt.contexto): JSONObject {
        val list = JSONArray()
        for (candidate in candidates(context)) {
            list.put(
                JSONObject()
                    .put("package", candidate.packageName)
                    .put("activity", candidate.activityName)
                    .put("label", candidate.label)
                    .put("current", candidate.isCurrent)
            )
        }
        return JSONObject()
            .put("ok", true)
            .put("current", currentHomePackage(context) ?: JSONObject.NULL)
            .put("factoryPackage", FACTORY_PACKAGE)
            .put("viewerPackage", VIEWER_PACKAGE)
            .put("factoryInstalled", isInstalled(FACTORY_PACKAGE, context))
            .put("viewerInstalled", isInstalled(VIEWER_PACKAGE, context))
            .put("shizuku", Shell.disponivel())
            .put("launcher3DFixo", launcher3DFixo())
            .put("candidates", list)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
