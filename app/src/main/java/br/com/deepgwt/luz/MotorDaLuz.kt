// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.luz

import br.com.deepgwt.veiculo.HeadlightPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlin.math.max

/** O carro, do jeito que a luz precisa dele (o Android liga em `Veiculo`; os testes, num falso). */
internal interface CarroDaLuz {
    fun ler(chave: String): String?
    fun observar(ouvinte: OuvinteDoCarro)
    fun pararDeObservar(ouvinte: OuvinteDoCarro)
    fun acompanhar(chaves: Collection<String>)
    fun escrever(chave: String, valor: String)
    fun horaLocal(): Int
}

internal fun interface OuvinteDoCarro {
    fun mudou(chave: String, valor: String?)
}

/** Os efeitos com música (graves e onda do álbum). */
internal interface ControleDaMusica {
    /** Liga o efeito escolhido e para o outro. */
    fun ligar(modo: ModoMusical)

    /** Para os dois, restaurando o que mudou na central. */
    fun parar()

    companion object {
        val NENHUM: ControleDaMusica = object : ControleDaMusica {
            override fun ligar(modo: ModoMusical) = Unit
            override fun parar() = Unit
        }
    }
}

/**
 * O cérebro da luz ambiente: comandos do serviço (casos 9–17), reações ao carro (33–38), cores
 * de base e animações (39–53). Sem Android: o serviço injeta preferências, fita, carro e relógio.
 *
 * Animações, pulsos, o âmbar fixo do farol e a piscada do clima rodam um de cada vez: começar um
 * cancela o anterior (e espera ele parar, para os quadros não se misturarem).
 */
internal class MotorDaLuz(
    private val ajustes: () -> AjustesDaLuz,
    private val gravarFita: (endereco: String, nome: String?) -> Unit,
    private val fita: FitaDoMotor,
    private val carro: CarroDaLuz,
    escopo: CoroutineScope,
    private val relogio: Relogio = RelogioDoSistema,
    private val registro: Registro = Registro.MUDO,
    private val esperaDaConexaoMs: Long = ESPERA_DA_CONEXAO_MS,
) {
    @Volatile
    var musica: ControleDaMusica = ControleDaMusica.NENHUM

    private val trabalhos = SupervisorJob(escopo.coroutineContext[Job])
    private val meuEscopo = CoroutineScope(escopo.coroutineContext + trabalhos)

    private val trava = Any()
    private val memoria = MemoriaDosSinais()
    private var ouvintesLigados = false
    private var animacao: Job? = null

    @Volatile
    var modoAtual: ModoDeConducao = ModoDeConducao.DESCONHECIDO
        private set

    @Volatile
    var camada: CamadaDaLuz = CamadaDaLuz.NENHUMA
        private set

    /** Começa branca; só muda ao fim de uma transição ou de um quadro fixo de cor-base. */
    @Volatile
    var ultimaCor: CorLed = Cores.BRANCO
        private set

    private val ouvinteDeAlerta = OuvinteDoCarro { chave, valor ->
        if (chave in ChavesDoCarro.ALERTA) {
            memoria.receber(chave, valor)
            lancar("aviso $chave") { recalcularCamada(forcado = false) }
        }
    }

    private val ouvinteDoModo = OuvinteDoCarro { chave, valor ->
        if (chave == ChavesDoCarro.MODO_DE_CONDUCAO) lancar("modo de condução") { aoMudarModo(valor) }
    }

    // ================================================================== comandos

    /** Caso 9–12. */
    fun iniciar() {
        val a = ajustes()
        if (a.dinamica) {
            ligarOuvintes()
        } else {
            soltarOuvintes()
            synchronized(trava) { camada = CamadaDaLuz.NENHUMA }
            memoria.limpar()
        }
        if (a.dinamica && a.temFita) modoAtual = ModoDeConducao.doValor(carro.ler(ChavesDoCarro.MODO_DE_CONDUCAO))
        if (!a.temFita) {
            musica.parar()
            fita.desconectar()
            return
        }
        lancar("iniciar") { prepararEConectar(a, aplicarBase = true) }
    }

    /** Caso 13. Não mexe nos ouvintes. */
    fun conectar(endereco: String?, nome: String?) {
        if (!endereco.isNullOrBlank()) gravarFita(endereco.trim(), nome)
        val a = ajustes()
        if (!a.temFita) {
            musica.parar()
            fita.desconectar()
            return
        }
        lancar("conectar") { prepararEConectar(a, aplicarBase = false) }
    }

    /** Caso 14. */
    fun desconectar() {
        musica.parar()
        cancelarAnimacao()
        fita.desconectar()
    }

    /** Caso 15. */
    fun brilho(percentual: Int) {
        val a = ajustes()
        val endereco = a.endereco ?: return
        lancar("brilho") {
            if (fita.conectarEEsperar(endereco, a.reconectar, esperaDaConexaoMs)) {
                fita.enviar(Quadros.brilho(percentual, ajustes().saida))
            }
        }
    }

    /** Caso 16. Os efeitos de música só voltam no próximo iniciar. */
    fun testarCor(cor: CorLed) {
        val a = ajustes()
        val endereco = a.endereco ?: return
        musica.parar()
        cancelarAnimacao()
        lancar("testar cor") {
            if (fita.conectarEEsperar(endereco, a.reconectar, esperaDaConexaoMs)) enviarCor(cor)
        }
    }

    /** Caso 17. Texto inválido vira só "último erro" (não derruba a conexão). */
    fun enviarHex(texto: String?) {
        val a = ajustes()
        val endereco = a.endereco ?: return
        if (texto.isNullOrBlank()) return
        musica.parar()
        cancelarAnimacao()
        lancar("enviar hex") {
            if (!fita.conectarEEsperar(endereco, a.reconectar, esperaDaConexaoMs)) return@lancar
            when (val r = Hex.paraBytes(texto)) {
                is ResultadoDoHex.Bytes -> fita.enviar(listOf(r.bytes))
                is ResultadoDoHex.Erro -> fita.registrarErro(r.mensagem)
            }
        }
    }

    /** Casos 51–53: duas piscadas vermelhas, nunca por cima de um alerta. */
    fun piscarPeloClima() {
        val a = ajustes()
        if (!a.ligado || !a.temFita || a.musica || ReacaoDaLuz.CLIMA !in a.reacoes || camada != CamadaDaLuz.NENHUMA) return
        executarAnimacao("clima") {
            repeat(2) {
                enviarCor(Cores.VERMELHO)
                relogio.esperar(130)
                enviarCor(Cores.APAGADO)
                relogio.esperar(90)
            }
            if (camada == CamadaDaLuz.NENHUMA) {
                val b = ajustes()
                if (b.dinamica) rodarCorBase() else fixarBase(b.corManual)
            }
        }
    }

    /** Caso 6: solta ouvintes, encerra efeitos e animação, desconecta. */
    fun parar() {
        soltarOuvintes()
        try {
            musica.parar()
        } catch (e: Exception) {
            registro.anotar("ao parar a música: $e")
        }
        cancelarAnimacao()
        trabalhos.cancelChildren()
        fita.desconectar()
    }

    /** Fim do motor (serviço destruído / fim de um teste). */
    fun encerrar() {
        parar()
        trabalhos.cancel()
    }

    // ================================================================== iniciar / conectar

    private suspend fun prepararEConectar(a: AjustesDaLuz, aplicarBase: Boolean) {
        val endereco = a.endereco ?: return
        if (a.musica) {
            cancelarAnimacao()
            musica.ligar(a.modoMusical)
        } else {
            musica.parar()
        }
        val jaConectado = fita.estado.value.conectadaA(endereco)
        if (!fita.conectarEEsperar(endereco, a.reconectar, esperaDaConexaoMs)) {
            registro.anotar("a fita $endereco não conectou a tempo")
            return
        }
        fita.enviar(Quadros.brilho(a.brilho, a.saida))
        if (a.musica) return
        when {
            !jaConectado && a.animacoes -> executarAnimacao("boas-vindas") { boasVindas() }
            !a.dinamica -> fixarBase(a.corManual)
            aplicarBase -> {
                aplicarCorBase()
                recalcularCamada(forcado = true)
            }
        }
    }

    /** Caso 44: azul suave, branco e transição do branco para a cor-base certa. */
    private suspend fun boasVindas() {
        val a = ajustes()
        if (!a.ligado || !a.temFita) return
        enviarCor(Cores.AZUL_SUAVE)
        relogio.esperar(300)
        enviarCor(Cores.BRANCO)
        relogio.esperar(300)
        transicao(Cores.BRANCO, ajustes().corBase(modoAtual))
    }

    // ================================================================== reações ao carro

    private fun ligarOuvintes() {
        synchronized(trava) {
            if (ouvintesLigados) return
            ouvintesLigados = true
        }
        try {
            carro.acompanhar(ChavesDoCarro.ALERTA + ChavesDoCarro.MODO_DE_CONDUCAO)
        } catch (e: Exception) {
            registro.anotar("acompanhar chaves: $e")
        }
        carro.observar(ouvinteDoModo)
        carro.observar(ouvinteDeAlerta)
    }

    private fun soltarOuvintes() {
        synchronized(trava) {
            if (!ouvintesLigados) return
            ouvintesLigados = false
        }
        carro.pararDeObservar(ouvinteDeAlerta)
        carro.pararDeObservar(ouvinteDoModo)
    }

    /** Caso 34: último aviso vence o cache; a camada sai da regra de [DecisaoDaLuz]. */
    private fun camadaAgora(a: AjustesDaLuz): CamadaDaLuz {
        fun valor(chave: String) = memoria.valor(chave) { carro.ler(it) }
        fun ativo(chave: String) = HeadlightPolicy.ligada(valor(chave))
        val sinais = SinaisDaLuz(
            piscaAlerta = ativo(ChavesDoCarro.PISCA_ALERTA),
            pontoCego = ativo(ChavesDoCarro.PONTO_CEGO_ESQUERDO) || ativo(ChavesDoCarro.PONTO_CEGO_DIREITO),
            seta = ativo(ChavesDoCarro.SETA_ESQUERDA) || ativo(ChavesDoCarro.SETA_DIREITA),
            farol = HeadlightPolicy.resolver(a.fonteDoFarol, HeadlightPolicy.sinaisDe { valor(it) }, carro.horaLocal()),
            modoSport = modoAtual == ModoDeConducao.SPORT,
        )
        return DecisaoDaLuz.camada(sinais, a.reacoes)
    }

    /** Caso 35. */
    fun recalcularCamada(forcado: Boolean) {
        val a = ajustes()
        if (!a.ligado || !a.dinamica || !a.temFita || a.musica) return
        val nova = synchronized(trava) {
            val n = camadaAgora(a)
            if (n == camada && !forcado) return
            camada = n
            n
        }
        when (nova) {
            CamadaDaLuz.PISCA_ALERTA -> pulsar(nova, Cores.VERMELHO, 600)
            CamadaDaLuz.PONTO_CEGO -> pulsar(nova, Cores.LARANJA, 400)
            CamadaDaLuz.SETA -> pulsar(nova, Cores.AMBAR, 700)
            CamadaDaLuz.FAROL -> executarAnimacao("farol") { enviarCor(Cores.AMBAR) }
            CamadaDaLuz.NENHUMA -> aplicarCorBase()
        }
    }

    /** Caso 37: acende, meio período, apaga, meio período — enquanto a camada continuar a mesma. */
    private fun pulsar(esperada: CamadaDaLuz, cor: CorLed, periodoMs: Long) {
        val meio = max(periodoMs / 2, 80L)
        executarAnimacao("pulso ${esperada.name}") {
            while (true) {
                if (!continuaNa(esperada)) break
                enviarCor(cor)
                relogio.esperar(meio)
                if (!continuaNa(esperada)) break
                enviarCor(Cores.APAGADO)
                relogio.esperar(meio)
            }
        }
    }

    /** Relê os sinais: um aviso de "desligou" que se perdeu não deixa o pulso preso. */
    private fun continuaNa(esperada: CamadaDaLuz): Boolean {
        if (camada != esperada) return false
        val agora = camadaAgora(ajustes())
        if (agora == esperada) return true
        lancar("recálculo pelo pulso") { recalcularCamada(forcado = false) }
        return false
    }

    /** Caso 38. */
    fun aoMudarModo(cru: String?) {
        val a = ajustes()
        if (!a.ligado || !a.dinamica || !a.temFita) return
        val novo = ModoDeConducao.doValor(cru)
        synchronized(trava) {
            if (novo == modoAtual) return
            modoAtual = novo
        }
        if (a.musica) {
            registro.anotar("modo de condução: $novo (efeito com música ligado)")
            return
        }
        if (camada == CamadaDaLuz.NENHUMA) aplicarCorBase()
        recalcularCamada(forcado = false)
    }

    // ================================================================== cor-base e animações

    /** Caso 36, no "um de cada vez". */
    private fun aplicarCorBase() {
        executarAnimacao("cor-base") { rodarCorBase() }
    }

    private suspend fun rodarCorBase() {
        val a = ajustes()
        if (!a.temFita) return
        if (a.dinamica && ReacaoDaLuz.MODO_DE_CONDUCAO in a.reacoes) {
            rodarCorDoModo(modoAtual, a)
        } else if (a.animacoes) {
            transicao(ultimaCor, a.corManual)
        } else {
            fixarBase(a.corManual)
        }
    }

    /** Casos 45–49. */
    private suspend fun rodarCorDoModo(modo: ModoDeConducao, a: AjustesDaLuz) {
        if (!a.animacoes) {
            fixarBase(modo.cor)
            return
        }
        when (modo) {
            ModoDeConducao.SPORT -> {
                val inicio = relogio.agora()
                do {
                    transicao(Cores.VERMELHO_FRACO, Cores.VERMELHO)
                    transicao(Cores.VERMELHO, Cores.VERMELHO_FRACO)
                } while (relogio.agora() - inicio < DURACAO_DO_SPORT_MS)
                fixarBase(Cores.VERMELHO)
            }
            ModoDeConducao.ECO -> {
                transicao(ultimaCor, Cores.VERDE)
                while (true) {
                    transicao(Cores.VERDE_FRACO, Cores.VERDE)
                    relogio.esperar(250)
                    transicao(Cores.VERDE, Cores.VERDE_FRACO)
                    relogio.esperar(250)
                }
            }
            else -> transicao(ultimaCor, modo.cor)
        }
    }

    /** Caso 45: 20 passos truncados, 30 ms depois de cada quadro. */
    private suspend fun transicao(de: CorLed, para: CorLed) {
        for (k in 1..Transicao.PASSOS) {
            enviarCor(Transicao.passo(de, para, k))
            relogio.esperar(Transicao.PAUSA_MS)
        }
        ultimaCor = para
    }

    /** Um quadro de cor-base. */
    private suspend fun fixarBase(cor: CorLed) {
        enviarCor(cor)
        ultimaCor = cor
    }

    /** Na saída geral, com as duas ordens salvas (relidas agora). */
    private suspend fun enviarCor(cor: CorLed): Boolean {
        val a = ajustes()
        return fita.enviar(Quadros.cor(cor, a.saida, a.ordemDmx, a.ordemBle))
    }

    private fun executarAnimacao(nome: String, bloco: suspend () -> Unit) {
        synchronized(trava) {
            val anterior = animacao
            val nova = meuEscopo.launch(start = CoroutineStart.LAZY) {
                anterior?.cancelAndJoin()
                try {
                    bloco()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    registro.anotar("animação $nome falhou: $e")
                }
            }
            animacao = nova
            nova.start()
        }
    }

    private fun cancelarAnimacao() {
        synchronized(trava) {
            animacao?.cancel()
            animacao = null
        }
    }

    private fun lancar(nome: String, bloco: suspend () -> Unit) {
        meuEscopo.launch {
            try {
                bloco()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                registro.anotar("$nome falhou: $e")
            }
        }
    }

    companion object {
        const val DURACAO_DO_SPORT_MS = 3_000L
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
