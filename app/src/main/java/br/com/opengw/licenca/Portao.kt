package br.com.opengw.licenca

import br.com.opengw.nucleo.OpenGw
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import br.com.opengw.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Quem decide se um [Recurso] vale agora (Alpha 8.0; licença na 8.1).
 *
 * É consultado pelos SERVIÇOS, não só pelas telas: o ASD, o HUD, a luz ambiente, macros, gestos,
 * Stream Deck, launcher 3D, o painel e os apps de sistema perguntam aqui antes de subir. Uma trava
 * só na tela voltaria pela porta dos fundos — um perfil de motorista, uma macro, o instalador pela
 * rede ou o próprio boot ligariam o que a tela esconde.
 *
 * COBRANÇA (8.1). Com [cobrancaAtiva], o nível é o que as licenças deste carro sustentam
 * ([Licencas]); sem licença, Drive. A cobrança liga sozinha quando o build tem uma chave de
 * PRODUÇÃO ([ChavesPublicas.temProducao]) — um build sem ela não teria como aceitar licença
 * nenhuma, então continua como a 8.0 (todo mundo em Signature). Para testar os cadeados existe o
 * nível de teste e a cobrança de teste, aceitos só num build pedido para isso ou no do simulador
 * ([testeLiberado]) — o APK que o instalador envia ignora as duas chaves.
 *
 * NUNCA CAI COM O CARRO LIGADO. Quando as horas acabam em movimento, o nível fica SEGURO até o
 * READY desligar ([Estado.caiPara] diz para onde vai cair); o mesmo vale se o app reiniciar no
 * meio do caminho, porque o nível segurado fica gravado ([CHAVE_NIVEL_SEGURADO]).
 *
 * O estado mora em `licenca.xml` (device-protected), FORA do `opengw_prefs`: não entra em
 * `/ajustes`, nos perfis de motorista nem no backup do app. Os níveis guardados ali levam selo
 * ([SeloDoNivel], 8.3): editados à mão, não valem.
 *
 * APK ALTERADO (8.3). Com a cobrança ligada, um APK reassinado ([Integridade]) ou uma diferença
 * confirmada pela [Sentinela] dão Drive ([Origem.ALTERADO]) — pela mesma regra de não cair com o
 * carro ligado.
 *
 * TESTE GRÁTIS (8.4). A pessoa toca em "Testar grátis" e ganha o Max por 10 minutos de uso
 * ([Origem.AVALIACAO]); as horas das licenças ficam paradas nesse tempo. Acabou: volta ao nível
 * de antes na PRIMEIRA PARADA (velocidade de parado), sem esperar desligar — o teste dura o que
 * promete. Em movimento, espera parar ([segurarQueda]).
 *
 * MUDANÇA DE NÍVEL. Quando o nível cai, cada serviço que perdeu a vez desliga e devolve o estado de
 * fábrica (ver [MudancaDeNivel]); as preferências da pessoa ficam como estavam, e o nível voltando
 * restaura tudo.
 */
object Portao {
    private const val TAG = "Portao"

    /** Com chave de produção no build, ou a cobrança de teste num build de teste. */
    val cobrancaAtiva: Boolean
        get() = ChavesPublicas.temProducao || Licencas.cobrancaDeTeste()

    const val ARQUIVO = "licenca"
    const val CHAVE_NIVEL_DE_TESTE = "nivelDeTeste"
    /** O nível a que os serviços foram ajustados por último. Ver [conferirNaPartida]. */
    const val CHAVE_NIVEL_APLICADO = "nivelAplicado"
    /** O nível mantido até o carro desligar, depois que as horas acabaram com ele ligado. */
    const val CHAVE_NIVEL_SEGURADO = "nivelSeguradoAteDesligar"

    /** De onde veio o nível que vale agora. Vai para o instalador e para a tela Informações. */
    enum class Origem(val id: String, val descricao: String) {
        SEM_COBRANCA("sem-cobranca", "Todos os recursos liberados nesta versão"),
        TESTE("teste", "Nível de teste (build de teste)"),
        LICENCA("licenca", "Licença"),
        AVALIACAO("avaliacao", "Teste grátis do Max"),
        SEM_LICENCA("sem-licenca", "Sem licença: nível grátis"),
        ALTERADO("alterado", "Este OPEN GW foi modificado: as licenças não valem nele")
    }

    /** [caiPara]: as horas acabaram com o carro ligado; o nível desce para ele ao desligar. */
    data class Estado(val nivel: Nivel, val origem: Origem, val caiPara: Nivel? = null)

    /**
     * A decisão, pura. [testeLiberado] = build de teste ou do simulador; fora dele o nível de teste
     * gravado é ignorado (um `licenca.xml` copiado de um aparelho de teste não libera nada).
     * [alterado] = APK reassinado ou diferença confirmada pela [Sentinela]: só pesa com cobrança.
     * [avaliacao] = o teste grátis está correndo (8.4): Max, acima de qualquer licença.
     */
    fun decidir(
        cobrancaAtiva: Boolean,
        nivelDaLicenca: Nivel?,
        nivelDeTeste: Nivel?,
        testeLiberado: Boolean,
        alterado: Boolean = false,
        avaliacao: Boolean = false
    ): Estado = when {
        testeLiberado && nivelDeTeste != null -> Estado(nivelDeTeste, Origem.TESTE)
        !cobrancaAtiva -> Estado(Nivel.SIGNATURE, Origem.SEM_COBRANCA)
        alterado -> Estado(Nivel.DRIVE, Origem.ALTERADO)
        avaliacao -> Estado(Carteira.NIVEL_DA_AVALIACAO, Origem.AVALIACAO)
        nivelDaLicenca != null -> Estado(nivelDaLicenca, Origem.LICENCA)
        else -> Estado(Nivel.DRIVE, Origem.SEM_LICENCA)
    }

    /**
     * Não é `BuildConfig.DEBUG`: o APK que vai para o carro é o `debug`. É um campo próprio, ligado
     * só no build do simulador ou com `-Popengw.nivelDeTeste=true` (ver `app/build.gradle.kts`).
     */
    val testeLiberado: Boolean get() = BuildConfig.NIVEL_DE_TESTE

    private val estadoFlow = MutableStateFlow(Estado(Nivel.SIGNATURE, Origem.SEM_COBRANCA))
    @Volatile private var iniciado = false
    private val trava = Any()

    /**
     * Lê o estado gravado na primeira consulta. Preguiçoso de propósito: serviços que sobem antes do
     * `App.onCreate` terminar (acessibilidade, boot) perguntam antes de qualquer `iniciar()` — e
     * precisam da resposta certa, não do valor inicial do campo.
     */
    private fun garantirIniciado() {
        if (iniciado) return
        synchronized(trava) {
            if (iniciado) return
            estadoFlow.value = calcular()
            iniciado = true
            Log.i(TAG, "nível ${estadoFlow.value.nivel.id} (${estadoFlow.value.origem.id})")
        }
    }

    private fun prefs(): SharedPreferences? =
        runCatching { OpenGw.contextoProtegido.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE) }.getOrNull()

    private fun calcular(): Estado {
        val teste = prefs()?.getString(CHAVE_NIVEL_DE_TESTE, null)?.let { Nivel.deId(it) }
        val cobranca = cobrancaAtiva
        val bruto = decidir(
            cobranca,
            nivelDaLicenca = if (cobranca) Licencas.nivelDaLicenca() else null,
            nivelDeTeste = teste,
            testeLiberado = testeLiberado,
            alterado = Integridade.alterado || Sentinela.violado,
            avaliacao = cobranca && Licencas.avaliacaoAtiva()
        )
        return segurar(bruto)
    }

    /**
     * A regra de nunca cair com o carro ligado, pura. [base] = o nível em que os serviços estão
     * (ou o segurado de antes); [ready] = null quando ainda não se sabe (app recém-subido: segura,
     * por via das dúvidas, até o carro dizer que está desligado).
     *
     * [fimDaAvaliacao] = a queda é o fim do teste grátis (8.4): aí segura só enquanto o carro anda
     * — [parado] solta na primeira parada — e a origem continua [Origem.AVALIACAO] até cair.
     */
    fun segurarQueda(
        bruto: Estado,
        base: Nivel?,
        ready: Boolean?,
        fimDaAvaliacao: Boolean = false,
        parado: Boolean = false
    ): Estado {
        if (bruto.origem != Origem.LICENCA && bruto.origem != Origem.SEM_LICENCA && bruto.origem != Origem.ALTERADO) return bruto
        if (base == null || base.ordinal <= bruto.nivel.ordinal || ready == false) return bruto
        val acabouOTeste = fimDaAvaliacao && bruto.origem != Origem.ALTERADO
        if (acabouOTeste && parado) return bruto
        val origem = when {
            bruto.origem == Origem.ALTERADO -> Origem.ALTERADO
            acabouOTeste -> Origem.AVALIACAO
            else -> Origem.LICENCA
        }
        return Estado(base, origem, caiPara = bruto.nivel)
    }

    /** O nível guardado em [chave], se o selo confere ([SeloDoNivel]). */
    private fun lerNivel(p: SharedPreferences?, chave: String): Nivel? =
        SeloDoNivel.ler(p?.getString(chave, null), SeloDoNivel.chaveDoAparelho)

    private fun gravarNivel(e: SharedPreferences.Editor, chave: String, nivel: Nivel): SharedPreferences.Editor =
        e.putString(chave, SeloDoNivel.selar(nivel, SeloDoNivel.chaveDoAparelho))

    private fun segurar(bruto: Estado): Estado {
        val p = prefs()
        val segurado = lerNivel(p, CHAVE_NIVEL_SEGURADO)
        val aplicado = lerNivel(p, CHAVE_NIVEL_APLICADO)
        val base = listOfNotNull(segurado, aplicado).maxByOrNull { it.ordinal }
        // Só pergunta pelo carro quando há queda a decidir (o normal é não haver).
        val haQueda = base != null && base.ordinal > bruto.nivel.ordinal
        val ready = if (haQueda) Licencas.readyAgora() else null
        val fimDaAvaliacao = haQueda && Licencas.avaliacaoAcabouNestaPartida()
        val estado = segurarQueda(bruto, base, ready, fimDaAvaliacao, parado = fimDaAvaliacao && Licencas.paradoAgora())
        if (estado.caiPara != null) {
            if (segurado != estado.nivel) p?.edit()?.let { gravarNivel(it, CHAVE_NIVEL_SEGURADO, estado.nivel).apply() }
        } else if (p?.contains(CHAVE_NIVEL_SEGURADO) == true) {
            p?.edit()?.remove(CHAVE_NIVEL_SEGURADO)?.apply()
        }
        return estado
    }

    /** O estado para a interface (Compose: `Portao.estado.collectAsState()`). */
    val estado: StateFlow<Estado>
        get() {
            garantirIniciado()
            return estadoFlow.asStateFlow()
        }

    @JvmStatic
    fun nivel(): Nivel {
        garantirIniciado()
        return estadoFlow.value.nivel
    }

    /**
     * O que os serviços perguntam. Além do nível, a [Sentinela]: com uma alteração confirmada e o
     * carro já desligado, só o Drive passa — mesmo que a decisão acima tenha sido editada.
     */
    @JvmStatic
    fun permite(recurso: Recurso): Boolean =
        nivel().alcanca(recurso.nivel) && (recurso.nivel == Nivel.DRIVE || !Sentinela.bloqueando)

    /** Nível de teste lido do disco, mesmo quando ignorado (release). */
    fun nivelDeTesteGravado(): Nivel? = prefs()?.getString(CHAVE_NIVEL_DE_TESTE, null)?.let { Nivel.deId(it) }

    /**
     * Grava (ou apaga, com `null`) o nível de teste e aplica. Recusa fora do build de teste.
     * Devolve o estado que ficou valendo.
     */
    fun definirNivelDeTeste(nivel: Nivel?): Result<Estado> {
        if (!testeLiberado) return Result.failure(IllegalStateException("nível de teste só vale num build de teste do app"))
        val p = prefs() ?: return Result.failure(IllegalStateException("armazenamento indisponível"))
        p.edit().apply { if (nivel == null) remove(CHAVE_NIVEL_DE_TESTE) else putString(CHAVE_NIVEL_DE_TESTE, nivel.id) }.commit()
        return Result.success(recalcular())
    }

    /** Liga/desliga a cobrança de teste (licença de teste, sem licença = Drive). Só em build de teste. */
    fun definirCobrancaDeTeste(ligada: Boolean): Result<Estado> =
        Licencas.definirTeste(vin = null, ready = null, cobranca = ligada, consumirMinutos = null).map { estado.value }

    /** Relê o disco; se o nível mudou, os serviços se ajustam (fora da thread de quem chamou). */
    fun recalcular(): Estado {
        garantirIniciado()
        val anterior: Estado
        val novo: Estado
        synchronized(trava) {
            anterior = estadoFlow.value
            novo = calcular()
            estadoFlow.value = novo
        }
        if (anterior.nivel != novo.nivel) {
            Log.w(TAG, "nível ${anterior.nivel.id} → ${novo.nivel.id} (${novo.origem.id})")
            prefs()?.edit()?.let { gravarNivel(it, CHAVE_NIVEL_APLICADO, novo.nivel).apply() }
            val caiu = novo.nivel.ordinal < anterior.nivel.ordinal &&
                (novo.origem == Origem.LICENCA || novo.origem == Origem.SEM_LICENCA)
            if (caiu && anterior.origem == Origem.AVALIACAO) {
                // Fim do teste: o carro está parado agora (é a regra da queda) — avisa já.
                Licencas.anunciarFimDaAvaliacao(novo.nivel)
            } else if (caiu) {
                Licencas.registrarQueda(anterior.nivel, novo.nivel)
            }
            MudancaDeNivel.aplicarEmSegundoPlano(anterior.nivel, novo.nivel)
        } else if (anterior.caiPara != novo.caiPara) {
            val ate = if (novo.origem == Origem.AVALIACAO) "parar (fim do teste grátis)" else "desligar"
            Log.w(TAG, "nível ${novo.nivel.id} seguro até $ate; cai para ${novo.caiPara?.id}")
        }
        return novo
    }

    /**
     * Na subida do app: se o nível mudou com o app fechado (um `licenca.xml` restaurado, na 8.1 uma
     * licença que deixou de valer), os serviços se ajustam uma vez — depois de subirem, por isso o
     * atraso. Sem isto o painel acordaria no tema que o nível não libera mais.
     */
    fun conferirNaPartida() {
        val p = prefs() ?: return
        val atual = nivel()
        val aplicado = lerNivel(p, CHAVE_NIVEL_APLICADO)
        gravarNivel(p.edit(), CHAVE_NIVEL_APLICADO, atual).apply()
        if (aplicado == null || aplicado == atual) return
        Log.w(TAG, "nível mudou com o app fechado: ${aplicado.id} → ${atual.id}")
        MudancaDeNivel.aplicarEmSegundoPlano(aplicado, atual, atrasoMs = 15_000)
    }
}
