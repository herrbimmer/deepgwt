// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.licenca

/**
 * O teste grátis do Max (Alpha 8.4; refeito na 10.1 "como o WinRAR"). Kotlin puro.
 *
 * SEM RELÓGIO. O teste vale até o próximo POWER CYCLE: o carro desligar (READY ligado → desligado)
 * ou a central reiniciar (outro id de boot). Aí o DeepGWT volta ao plano da pessoa. Enquanto nada
 * disso acontece, fica no Max — as horas das licenças não contam nesse tempo.
 *
 * CONTADOR POR INSTALAÇÃO. [Registro.usos] conta quantos testes começaram. Mora só no `licenca.xml`
 * do app: desinstalar e reinstalar (ou limpar os dados) zera. Sem trava pesada de propósito: os
 * [LIMITE_GRATIS] primeiros são limpos; do terceiro em diante o teste começa igual, mas com o aviso
 * "pague pelo Max ou volte ao seu plano" em todas as telas do DeepGWT (central, painel e HUD) —
 * sem impedir o uso ([Registro.comAviso]).
 */
object Avaliacao {
    /** Testes limpos por instalação; do seguinte em diante, com o aviso. */
    const val LIMITE_GRATIS = 2

    /** O teste é da versão completa. */
    val NIVEL: Nivel get() = Carteira.NIVEL_DA_AVALIACAO

    const val TEXTO_DO_AVISO = "Por favor, pague pela versão Max ou retorne ao plano que você possui."

    /**
     * O que fica gravado. [boot] = id do boot em que o teste atual começou (null = nenhum correndo);
     * [viuLigado] = o carro já esteve em READY durante este teste (desligar depois disso encerra).
     */
    data class Registro(
        val usos: Int = 0,
        val boot: String? = null,
        val desdeMs: Long? = null,
        val comAviso: Boolean = false,
        val viuLigado: Boolean = false
    ) {
        fun ativoNo(bootAtual: String?): Boolean = boot != null && boot == bootAtual

        /** Começa um teste. Null se já há um correndo neste boot. */
        fun iniciar(bootAtual: String, agoraMs: Long, readyAgora: Boolean?): Registro? =
            if (ativoNo(bootAtual)) null
            else Registro(
                usos = usos + 1,
                boot = bootAtual,
                desdeMs = agoraMs,
                comAviso = usos >= LIMITE_GRATIS,
                viuLigado = readyAgora == true
            )

        /** Encerra o teste (o contador fica). */
        fun encerrar(): Registro = copy(boot = null, desdeMs = null, comAviso = false, viuLigado = false)

        /**
         * O que um tique (ou a carga) faz com o teste: outro boot → encerra; READY desligado depois
         * de ter estado ligado → encerra; READY ligado → marca [viuLigado]. Devolve o mesmo objeto
         * quando nada muda.
         */
        fun conferir(bootAtual: String?, ready: Boolean?): Registro = when {
            boot == null -> this
            bootAtual != null && boot != bootAtual -> encerrar()
            ready == false && viuLigado -> encerrar()
            ready == true && !viuLigado -> copy(viuLigado = true)
            else -> this
        }

        /** `usos|boot|desde|aviso|viuLigado` — só o app lê. */
        fun serializar(): String =
            listOf(usos.toString(), boot.orEmpty(), desdeMs?.toString().orEmpty(), if (comAviso) "1" else "0", if (viuLigado) "1" else "0")
                .joinToString("|")

        companion object {
            /** Vazio ou ilegível = nunca testou. */
            fun ler(texto: String?): Registro {
                val p = texto?.split('|') ?: return Registro()
                if (p.size < 5) return Registro()
                val usos = p[0].toIntOrNull()?.coerceAtLeast(0) ?: return Registro()
                return Registro(
                    usos = usos,
                    boot = p[1].ifEmpty { null },
                    desdeMs = p[2].toLongOrNull(),
                    comAviso = p[3] == "1",
                    viuLigado = p[4] == "1"
                )
            }
        }
    }

    enum class Situacao(val id: String) {
        /** Dá para começar sem aviso. */
        DISPONIVEL("disponivel"),
        /** Dá para começar, mas os grátis acabaram: vem com o aviso em todas as telas. */
        ESGOTADA("esgotada"),
        /** Correndo agora (ver [Estado.comAviso]). */
        ATIVA("ativa"),
        /** Não faz sentido agora (sem cobrança, já tem o Max, app alterado, nível de teste). */
        INDISPONIVEL("indisponivel")
    }

    data class Estado(
        val situacao: Situacao,
        val usos: Int = 0,
        val comAviso: Boolean = false,
        val motivo: String? = null
    ) {
        val limite: Int get() = LIMITE_GRATIS
        val gratisRestantes: Int get() = (LIMITE_GRATIS - usos).coerceAtLeast(0)
        val podeComecar: Boolean get() = situacao == Situacao.DISPONIVEL || situacao == Situacao.ESGOTADA
    }

    fun estado(cobranca: Boolean, origem: Portao.Origem, nivelDaLicenca: Nivel?, registro: Registro, bootAtual: String?): Estado {
        val r = registro
        if (r.ativoNo(bootAtual) && origem == Portao.Origem.AVALIACAO) return Estado(Situacao.ATIVA, r.usos, r.comAviso)
        motivoDeIndisponivel(cobranca, origem, nivelDaLicenca)?.let { return Estado(Situacao.INDISPONIVEL, r.usos, motivo = it) }
        if (r.ativoNo(bootAtual)) return Estado(Situacao.ATIVA, r.usos, r.comAviso)
        return Estado(if (r.usos >= LIMITE_GRATIS) Situacao.ESGOTADA else Situacao.DISPONIVEL, r.usos)
    }

    /** Null = dá para começar. */
    fun motivoDeIndisponivel(cobranca: Boolean, origem: Portao.Origem, nivelDaLicenca: Nivel?): String? = when {
        !cobranca -> "Nesta versão todos os recursos já estão liberados."
        origem == Portao.Origem.ALTERADO -> "Este DeepGWT foi modificado: reinstale pelo instalador no computador."
        origem == Portao.Origem.TESTE -> "O nível de teste está ligado (build de teste)."
        nivelDaLicenca == NIVEL -> "Você já tem o ${NIVEL.titulo}."
        else -> null
    }

    /** Uma linha sobre quantos testes sobram (tela, instalador, celular). */
    fun contagem(e: Estado): String = when (e.gratisRestantes) {
        0 -> "Os $LIMITE_GRATIS testes grátis desta instalação já foram usados: o próximo vem com aviso na tela."
        1 -> "Resta 1 de $LIMITE_GRATIS testes grátis nesta instalação."
        else -> "Restam ${e.gratisRestantes} de $LIMITE_GRATIS testes grátis nesta instalação."
    }

    fun mensagemDeIniciada(de: Nivel, comAviso: Boolean): String =
        if (comAviso) "${NIVEL.titulo} liberado até desligar o carro, com aviso na tela (os testes grátis acabaram). Depois volta ao ${de.titulo}."
        else "Teste grátis do ${NIVEL.titulo} começou: vale até desligar o carro. Depois o DeepGWT volta ao ${de.titulo}."

    fun mensagemDeFim(para: Nivel): String =
        "O teste do ${NIVEL.titulo} acabou. O DeepGWT voltou ao ${para.titulo}" +
            (if (para == Nivel.DRIVE) ", o nível grátis." else ".")
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
