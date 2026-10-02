package br.com.opengw.licenca

/**
 * O teste grátis do Max (Alpha 8.4): o que mostrar e se dá para começar. Kotlin puro.
 *
 * 10 minutos de uso ([Carteira.AVALIACAO_SEG]), contados como as horas das licenças (carro ligado e
 * OPEN GW ativo), uma vez por carro (VIN). Enquanto corre, as horas das licenças ficam paradas; no
 * fim o OPEN GW volta ao nível de antes na primeira parada ([Portao.segurarQueda]).
 *
 * O estado mora na carteira ([Carteira.avaliacaoUsadaSeg]), nas mesmas três cópias das licenças:
 * limpar os dados do app não devolve o teste. Apagar as três devolve — o mesmo risco aceito do
 * reset de fábrica da central.
 */
object Avaliacao {
    enum class Situacao(val id: String) {
        /** Dá para começar. */
        DISPONIVEL("disponivel"),
        /** Correndo agora. */
        ATIVA("ativa"),
        /** Já usado neste carro. */
        USADA("usada"),
        /** Não faz sentido agora (sem cobrança, já tem o Max, app alterado, nível de teste). */
        INDISPONIVEL("indisponivel")
    }

    data class Estado(val situacao: Situacao, val restanteSeg: Long, val motivo: String? = null) {
        val totalSeg: Long get() = Carteira.AVALIACAO_SEG
    }

    /**
     * [carteiraDoCarro] = a carteira, se é deste carro (VIN confere) ou ainda não tem carro; null
     * se é de outro carro (o teste deste ainda não começou). [nivelDaLicenca] = o que as licenças
     * sustentam sem o teste.
     */
    fun estado(cobranca: Boolean, origem: Portao.Origem, nivelDaLicenca: Nivel?, carteiraDoCarro: Carteira?): Estado {
        val c = carteiraDoCarro
        if (c != null && c.avaliacaoAtiva && origem == Portao.Origem.AVALIACAO) return Estado(Situacao.ATIVA, c.avaliacaoRestanteSeg)
        if (c != null && c.avaliacaoComecou && !c.avaliacaoAtiva) return Estado(Situacao.USADA, 0, "O teste grátis já foi usado neste carro.")
        motivoDeIndisponivel(cobranca, origem, nivelDaLicenca)?.let { return Estado(Situacao.INDISPONIVEL, 0, it) }
        // Começou mas o Portão não está nele (ex.: VIN ainda não lido): o que resta continua valendo.
        if (c != null && c.avaliacaoAtiva) return Estado(Situacao.ATIVA, c.avaliacaoRestanteSeg)
        return Estado(Situacao.DISPONIVEL, Carteira.AVALIACAO_SEG)
    }

    /** Null = dá para começar (falta só conferir o VIN e se já foi usado). */
    fun motivoDeIndisponivel(cobranca: Boolean, origem: Portao.Origem, nivelDaLicenca: Nivel?): String? = when {
        !cobranca -> "Nesta versão todos os recursos já estão liberados."
        origem == Portao.Origem.ALTERADO -> "Este OPEN GW foi modificado: reinstale pelo instalador no computador."
        origem == Portao.Origem.TESTE -> "O nível de teste está ligado (build de teste)."
        nivelDaLicenca == Carteira.NIVEL_DA_AVALIACAO -> "Você já tem o ${Carteira.NIVEL_DA_AVALIACAO.titulo}."
        else -> null
    }

    fun mensagemDeIniciada(de: Nivel): String =
        "Teste grátis do ${Carteira.NIVEL_DA_AVALIACAO.titulo} começou: ${AvisoDaLicenca.duracao(Carteira.AVALIACAO_SEG)} de uso. " +
            "Depois o OPEN GW volta ao ${de.titulo}."

    fun mensagemDeFim(para: Nivel): String =
        "O teste grátis do ${Carteira.NIVEL_DA_AVALIACAO.titulo} acabou. O OPEN GW voltou ao ${para.titulo}" +
            (if (para == Nivel.DRIVE) ", o nível grátis." else ".")
}
