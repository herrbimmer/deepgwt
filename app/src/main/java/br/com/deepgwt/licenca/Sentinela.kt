// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.licenca

import br.com.deepgwt.nucleo.DeepGwt
import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import br.com.deepgwt.BuildConfig
import java.math.BigInteger
import java.security.MessageDigest

/**
 * A segunda opinião sobre o nível (Alpha 8.3).
 *
 * O [Portao] decide o nível a partir das [Licencas], que verificam cada código com o
 * [VerificadorDeLicenca] e as chaves de [ChavesPublicas]. Quem edita o APK troca UMA dessas peças
 * ("a cobrança está desligada", "a assinatura confere") e o nível sobe. A sentinela refaz a conta
 * por outro caminho — lê a carteira direto do disco, lê as chaves direto do BuildConfig e confere
 * a assinatura com a PRÓPRIA conta (a duplicação é de propósito) — e compara com o que o Portão
 * diz. Nível acima do que o disco sustenta = alguém mexeu.
 *
 * SEM ALARME FALSO. A carteira em memória anda à frente do disco (uso gravado a cada minuto, cópia
 * externa mesclada depois do boot), mas sempre para MENOS saldo — o disco nunca sustenta menos que
 * a memória. Mesmo assim, a primeira diferença é só SUSPEITA: grava o pendente, pede ao Portão
 * para recalcular e só confirma se a diferença continuar depois de [CONFIRMACAO_MS].
 *
 * CONFIRMADO: o Portão passa a dar Drive ([Portao.Origem.ALTERADO]) — pela regra de sempre, só com
 * o carro desligado — e, com o carro desligado, o [Portao.permite] também passa a recusar tudo o
 * que não é Drive por conta própria ([bloqueando]), mesmo que a decisão do Portão tenha sido
 * editada. Vale até o app reiniciar: nada é gravado (um erro nosso não prende ninguém no Drive).
 *
 * Roda na thread das licenças a cada [Licencas.TIQUES_DA_SENTINELA] tiques. Não pesa: meia dúzia de
 * verificações de assinatura a cada 5 minutos.
 */
object Sentinela {
    private const val TAG = "Sentinela"
    const val CONFIRMACAO_MS = 3 * 60_000L

    enum class Situacao(val id: String) { OK("ok"), SUSPEITA("suspeita"), VIOLADO("violado") }

    @Volatile var situacao: Situacao = Situacao.OK
        private set
    @Volatile private var suspeitaDesdeMs: Long? = null

    /** Violação confirmada e o carro já esteve desligado: [Portao.permite] recusa o que não é Drive. */
    @Volatile var bloqueando = false
        private set

    val violado: Boolean get() = situacao == Situacao.VIOLADO

    /**
     * Pura: o maior nível que o disco justifica agora. [emTeste] = nível de teste num build de
     * teste (vale qualquer um); [segurado] = o nível seguro até desligar, já conferido pelo selo.
     */
    fun teto(cobrancaEsperada: Boolean, emTeste: Boolean, apkAlterado: Boolean, sustentado: Nivel?, segurado: Nivel?): Nivel {
        if (emTeste || !cobrancaEsperada) return Nivel.SIGNATURE
        val daLicenca = if (apkAlterado) Nivel.DRIVE else sustentado ?: Nivel.DRIVE
        return maxOf(daLicenca, segurado ?: Nivel.DRIVE)
    }

    // ── a conta por fora ───────────────────────────────────────────────────────────────────────

    /** As chaves de produção lidas do BuildConfig aqui mesmo (não pergunta a [ChavesPublicas.temProducao]). */
    fun chavesDoBuild(texto: String): Map<Int, CurvaDaLicenca.Ponto> =
        ChavesPublicas.lerLista(texto).mapNotNull { (id, b64) -> ponto(b64)?.let { id to it } }.toMap()

    private fun ponto(b64: String): CurvaDaLicenca.Ponto? =
        runCatching { java.util.Base64.getDecoder().decode(b64.trim()) }.getOrNull()?.let { CurvaDaLicenca.descomprimir(it) }

    /**
     * A assinatura conferida sem passar pelo [VerificadorDeLicenca]. Devolve a licença ou null.
     * Mesma conta (Schnorr, desafio de 80 bits): ver `licenciador/src/nucleo/assinatura.ts`.
     */
    fun confere(codigo: String, vin: String, chaves: Map<Int, CurvaDaLicenca.Ponto>, aceitarTeste: Boolean): Licenca? {
        val lido = CodigoDeLicenca.ler(codigo) as? CodigoDeLicenca.Leitura.Ok ?: return null
        val n = lido.numero
        val s = n.and(BigInteger.ONE.shiftLeft(160).subtract(BigInteger.ONE))
        val e = n.shiftRight(160).and(BigInteger.ONE.shiftLeft(80).subtract(BigInteger.ONE))
        val dado = n.shiftRight(240)
        val licenca = Licenca.lerDado(dado).getOrNull() ?: return null
        if (licenca.chave == ChavesPublicas.ID_DE_TESTE && !aceitarTeste) return null
        val p = chaves[licenca.chave] ?: return null
        if (s >= CurvaDaLicenca.N || e.signum() == 0) return null
        val r = CurvaDaLicenca.somar(CurvaDaLicenca.multiplicar(s, CurvaDaLicenca.G), CurvaDaLicenca.multiplicar(e, p)) ?: return null
        val h = MessageDigest.getInstance("SHA-256")
        h.update(Licenca.PREFIXO.toByteArray(Charsets.US_ASCII))
        h.update(CurvaDaLicenca.numeroParaBytes(r.x))
        h.update(CurvaDaLicenca.comprimir(p))
        h.update(CurvaDaLicenca.numeroParaBytes(dado.shiftLeft(3), 6))
        h.update(vin.toByteArray(Charsets.US_ASCII))
        val desafio = BigInteger(1, h.digest().copyOfRange(0, 10))
        return licenca.takeIf { desafio == e }
    }

    /** Pura: o nível que [carteira] sustenta para [vin], conferindo cada código aqui. */
    fun sustentado(carteira: Carteira, vin: String?, chaves: Map<Int, CurvaDaLicenca.Ponto>, aceitarTeste: Boolean): Nivel? {
        if (vin == null) return null
        val hash = Licenca.hashDoVin(vin)
        if (carteira.vinHash != null && carteira.vinHash != hash) return null
        val boas = carteira.aplicadas.filter { confere(it.codigo, vin, chaves, aceitarTeste)?.serie == it.licenca.serie }
        return carteira.copy(aplicadas = boas).nivelAtivo()
    }

    private fun prefs(): SharedPreferences? =
        runCatching { DeepGwt.contextoProtegido.getSharedPreferences(Portao.ARQUIVO, Context.MODE_PRIVATE) }.getOrNull()

    private fun tetoAgora(): Nivel {
        val p = prefs()
        val emTesteDeBuild = BuildConfig.NIVEL_DE_TESTE
        val producao = chavesDoBuild(BuildConfig.LICENCA_CHAVES_PUBLICAS).filterKeys { it != ChavesPublicas.ID_DE_TESTE }
        val cobrancaEsperada = Portao.COBRANCA_LIGADA && producao.isNotEmpty() ||
            (Portao.COBRANCA_LIGADA && emTesteDeBuild && p?.getBoolean(Licencas.CHAVE_COBRANCA_DE_TESTE, false) == true)
        val emTeste = emTesteDeBuild && Nivel.deId(p?.getString(Portao.CHAVE_NIVEL_DE_TESTE, null)) != null
        if (emTeste || !cobrancaEsperada) return Nivel.SIGNATURE
        // O teste grátis (10.1) não tem trava: correndo neste boot, o Max está justificado.
        if (!Integridade.alterado && Avaliacao.Registro.ler(p?.getString(Licencas.CHAVE_AVALIACAO, null)).ativoNo(Licencas.idDoBoot())) {
            return Nivel.SIGNATURE
        }

        val chaves = producao + listOfNotNull(ponto(ChavesPublicas.TESTE)?.let { ChavesPublicas.ID_DE_TESTE to it })
        val carteira = CopiasDaCarteira.fundirTodas(
            listOf(
                CopiasDaCarteira.desserializar(p?.getString(Licencas.CHAVE_CARTEIRA, null)),
                CopiasDaCarteira.desserializar(runCatching { Settings.Global.getString(DeepGwt.contexto.contentResolver, Licencas.GLOBAL) }.getOrNull())
            )
        )
        val vinDeTeste = if (emTesteDeBuild) Licenca.normalizarVin(p?.getString(Licencas.CHAVE_VIN_DE_TESTE, null)) else null
        val vin = vinDeTeste ?: Licenca.normalizarVin(p?.getString(Licencas.CHAVE_VIN, null))
        return teto(
            cobrancaEsperada = true,
            emTeste = false,
            apkAlterado = Integridade.alterado,
            sustentado = sustentado(carteira, vin, chaves, aceitarTeste = emTesteDeBuild),
            segurado = SeloDoNivel.ler(p?.getString(Portao.CHAVE_NIVEL_SEGURADO, null), SeloDoNivel.chaveDoAparelho)
        )
    }

    // ── a ronda ────────────────────────────────────────────────────────────────────────────────

    /**
     * Uma ronda. [confirmarJa] pula a espera da confirmação (só o gancho do build de teste usa).
     * Bloqueia (disco, contas): chamar na thread das licenças.
     */
    fun conferir(agoraMs: Long = SystemClock.elapsedRealtime(), confirmarJa: Boolean = false) {
        try {
            if (violado) {
                aplicarBloqueioSeDesligado()
                return
            }
            var acima = acimaDoTeto()
            if (acima == null) {
                limpar()
                return
            }
            val desde = suspeitaDesdeMs
            if (desde == null && !confirmarJa) {
                // Primeira vez: pode ser só o disco atrás da memória. Grava e recalcula.
                Licencas.gravarPendencias()
                Portao.recalcular()
                acima = acimaDoTeto()
                if (acima == null) {
                    limpar()
                    return
                }
                suspeitaDesdeMs = agoraMs
                situacao = Situacao.SUSPEITA
                Log.w(TAG, "suspeita: $acima")
                return
            }
            if (confirmarJa || agoraMs - (desde ?: agoraMs) >= CONFIRMACAO_MS) {
                situacao = Situacao.VIOLADO
                Log.e(TAG, "confirmado: $acima")
                Portao.recalcular()
                aplicarBloqueioSeDesligado()
            }
        } catch (e: Exception) {
            Log.w(TAG, "ronda: ${e.message}")
        }
    }

    /** "signature > drive" quando o Portão está acima do teto; null quando está tudo certo. */
    private fun acimaDoTeto(): String? {
        val nivel = Portao.estado.value.nivel
        val teto = tetoAgora()
        return if (nivel.ordinal > teto.ordinal) "${nivel.id} > ${teto.id}" else null
    }

    private fun limpar() {
        if (situacao != Situacao.OK) Log.i(TAG, "sem diferença")
        situacao = Situacao.OK
        suspeitaDesdeMs = null
    }

    private fun aplicarBloqueioSeDesligado() {
        if (bloqueando || Licencas.readyAgora() == true) return
        bloqueando = true
        Log.e(TAG, "bloqueio aplicado (carro desligado)")
        Portao.recalcular()
    }

    /** Só testes. */
    internal fun reiniciarParaTeste() {
        situacao = Situacao.OK
        suspeitaDesdeMs = null
        bloqueando = false
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
