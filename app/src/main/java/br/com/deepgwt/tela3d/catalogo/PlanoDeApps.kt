package br.com.deepgwt.tela3d.catalogo

/** Uma atividade de lançador do sistema (ação MAIN + categoria LAUNCHER). Dado puro. */
internal data class AtividadeDeLancador(
    val pacote: String,
    val classe: String,
    /** Rótulo que o sistema devolve para a atividade (vazio se não há). */
    val rotulo: String,
)

/**
 * Um app decidido pelo planejador, ainda sem ícone desenhado: o que entra, com que rótulo e que
 * placa. Quem desenha o ícone é a camada Android.
 */
internal data class ItemPlanejado(
    val pacote: String,
    val rotulo: String,
    /** A primeira atividade do pacote; null para os apps falsos do emulador. */
    val atividade: AtividadeDeLancador?,
    val stub: Boolean,
    val fixado: Boolean,
    val oculto: Boolean,
    val placa: DecisaoDePlaca,
)

/**
 * As regras de quem aparece em cada lista (catálogo da página, linha da faixa, recentes, hub,
 * ocultos). Puro: recebe as atividades de lançador e o [EstadoDoUsuario] e devolve decisões.
 *
 * A ordem do sistema é mantida; um item por pacote (o da primeira atividade).
 */
internal class PlanejadorDeApps(
    private val regras: RegrasDoCatalogo,
    private val emulador: Boolean,
) {
    /** Primeira atividade de cada pacote, na ordem do sistema. */
    fun primeiraPorPacote(atividades: List<AtividadeDeLancador>): LinkedHashMap<String, AtividadeDeLancador> {
        val mapa = LinkedHashMap<String, AtividadeDeLancador>()
        for (a in atividades) {
            if (a.pacote.isNotEmpty() && !mapa.containsKey(a.pacote)) mapa[a.pacote] = a
        }
        return mapa
    }

    /** O pacote é um stub do emulador: só existe no emulador e não está instalado de verdade. */
    fun ehStub(pacote: String?, atividades: List<AtividadeDeLancador>): Boolean {
        if (!emulador || pacote.isNullOrEmpty()) return false
        if (atividades.any { it.pacote == pacote }) return false
        return regras.ehFixado(pacote) || FatosDoCarro.STUBS_EXTRAS.any { it.first == pacote }
    }

    /** Catálogo da página (A3): ordem do sistema, um por pacote, fixados entram, stubs extras no fim. */
    fun catalogo(atividades: List<AtividadeDeLancador>, estado: EstadoDoUsuario): List<ItemPlanejado> {
        val saida = ArrayList<ItemPlanejado>()
        val instalados = primeiraPorPacote(atividades)
        for ((pacote, atividade) in instalados) {
            if (!regras.entraNoCatalogo(pacote, estado.ocultos)) continue
            val fixado = regras.ehFixado(pacote)
            val rotuloDoApp = if (fixado) regras.rotuloDeFixado(pacote, atividade.rotulo) else regras.rotuloBase(pacote, atividade.rotulo)
            saida.add(item(pacote, rotuloDoApp, atividade, stub = false, fixado = fixado, oculto = false, estado = estado))
        }
        if (emulador) {
            for ((pacote, rotulo) in FatosDoCarro.STUBS_EXTRAS) {
                if (instalados.containsKey(pacote)) continue
                if (!regras.entraNoCatalogo(pacote, estado.ocultos)) continue
                saida.add(item(pacote, rotulo, null, stub = true, fixado = false, oculto = false, estado = estado))
            }
        }
        return saida
    }

    /**
     * Linha de apps da faixa (A6): ordem do sistema, um por pacote, sem fixados nem projeção,
     * COM os ocultos (marcados) e os stubs extras no fim.
     */
    fun linha(atividades: List<AtividadeDeLancador>, estado: EstadoDoUsuario): List<ItemPlanejado> {
        val saida = ArrayList<ItemPlanejado>()
        val instalados = primeiraPorPacote(atividades)
        for ((pacote, atividade) in instalados) {
            if (!regras.entraNaLinha(pacote)) continue
            saida.add(
                item(
                    pacote, regras.rotuloBase(pacote, atividade.rotulo), atividade,
                    stub = false, fixado = false, oculto = pacote in estado.ocultos, estado = estado,
                ),
            )
        }
        if (emulador) {
            for ((pacote, rotulo) in FatosDoCarro.STUBS_EXTRAS) {
                if (instalados.containsKey(pacote)) continue
                if (!regras.entraNaLinha(pacote)) continue
                saida.add(
                    item(pacote, rotulo, null, stub = true, fixado = false, oculto = pacote in estado.ocultos, estado = estado),
                )
            }
        }
        return saida
    }

    /** Até 3 atalhos de recentes: instalados, permitidos (A7). */
    fun recentes(atividades: List<AtividadeDeLancador>, estado: EstadoDoUsuario): List<ItemPlanejado> {
        val instalados = primeiraPorPacote(atividades)
        val pacotes = RecentesMru.atalhos(estado.recentes, instalados.keys) { regras.podeSerRecente(it, estado.ocultos) }
        return pacotes.mapNotNull { pacote ->
            val atividade = instalados[pacote] ?: return@mapNotNull null
            item(pacote, regras.rotuloBase(pacote, atividade.rotulo), atividade, stub = false, fixado = false, oculto = false, estado = estado)
        }
    }

    /** Fixados do hub (D5): ordem de D1, "ligados" (instalados; no emulador, sempre) e não ocultos. */
    fun fixadosDoHub(atividades: List<AtividadeDeLancador>, estado: EstadoDoUsuario): List<ItemPlanejado> {
        val instalados = primeiraPorPacote(atividades)
        val saida = ArrayList<ItemPlanejado>()
        for (pacote in FatosDoCarro.FIXADOS) {
            val atividade = instalados[pacote]
            val ligado = atividade != null || emulador
            if (!ligado || pacote in estado.ocultos) continue
            saida.add(
                item(
                    pacote, regras.rotuloDeFixado(pacote, atividade?.rotulo), atividade,
                    stub = atividade == null, fixado = true, oculto = false, estado = estado,
                ),
            )
        }
        return saida
    }

    /** Ocultos para a lista da UI: ordenados por nome de pacote. */
    fun ocultosOrdenados(estado: EstadoDoUsuario): List<String> = estado.ocultos.sorted()

    /** Rótulo na lista de ocultos: instalado = fixado/base; senão o próprio pacote. */
    fun rotuloDeOculto(pacote: String, atividade: AtividadeDeLancador?): String =
        if (atividade == null) pacote else regras.rotuloDeFixado(pacote, atividade.rotulo)

    /** Placa do chip do hub (`__gwm_hub`). */
    fun placaDoHub(estado: EstadoDoUsuario): DecisaoDePlaca =
        Placas.decidir(true, estado.personalizacoes.ler(FatosDoCarro.CHAVE_DO_HUB), true)

    /** Rótulo do chip do hub: o nome personalizado ou "GWM". */
    fun rotuloDoHub(estado: EstadoDoUsuario): String =
        estado.personalizacoes.ler(FatosDoCarro.CHAVE_DO_HUB)?.nome ?: FatosDoCarro.ROTULO_DO_HUB

    private fun item(
        pacote: String,
        rotuloPadrao: String,
        atividade: AtividadeDeLancador?,
        stub: Boolean,
        fixado: Boolean,
        oculto: Boolean,
        estado: EstadoDoUsuario,
    ): ItemPlanejado {
        val personalizacao = estado.personalizacoes.ler(pacote)
        return ItemPlanejado(
            pacote = pacote,
            rotulo = personalizacao?.nome ?: rotuloPadrao,
            atividade = atividade,
            stub = stub,
            fixado = fixado,
            oculto = oculto,
            placa = Placas.decidir(false, personalizacao, regras.usaEmblemaGwm(pacote) || regras.ehEnergyAssistant(pacote)),
        )
    }
}
