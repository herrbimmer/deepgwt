package br.com.opengw.licenca

/**
 * Os quatro níveis do OPEN GW (Alpha 8.0). Cumulativos: cada um contém tudo o que o anterior libera.
 *
 * A ORDEM DAS CONSTANTES É A ORDEM DOS NÍVEIS — [alcanca] compara pelo `ordinal`. Acrescentar um
 * nível no meio muda o que cada licença libera; o teste `RecursoTest` trava a ordem.
 *
 * O [id] é o que viaja na conversa com o instalador (`GET /licenca`) e, na 8.1, dentro do código
 * da licença. Não muda nunca.
 *
 * NOMES (Alpha 8.4). O [titulo] é o nome que a pessoa vê: Essencial · Plus · Pro · Max. As constantes
 * e os ids continuam com os nomes da 8.0 (drive, sport, premium, signature) — mudá-los invalidaria os
 * níveis guardados com selo, as cópias da carteira e o instalador antigo, sem ganho nenhum.
 */
enum class Nivel(val id: String, val titulo: String, val resumo: String) {
    DRIVE(
        "drive",
        "Essencial",
        "O dia a dia: barra inferior, informações e relógios no painel original, " +
            "AVAS, vidros, brilho e automações. Grátis."
    ),
    SPORT(
        "sport",
        "Plus",
        "Visual e som prontos: temas do painel que vêm no app, HUD, som do motor, áudio, " +
            "tela inicial 3D, luz ambiente, perfis e gravação."
    ),
    PREMIUM(
        "premium",
        "Pro",
        "Personalização total: os 275 temas XTRA, editores de tema e de HUD, macros, gestos, " +
            "Stream Deck, painel esportivo, funções de fábrica e ADAS."
    ),
    SIGNATURE(
        "signature",
        "Max",
        "Criar e programar: criador de temas, Coding completo, Oficina, Engenharia e as " +
            "ferramentas que escrevem direto no carro."
    );

    /** `true` quando este nível libera o que [exigido] pede. */
    fun alcanca(exigido: Nivel): Boolean = ordinal >= exigido.ordinal

    companion object {
        fun deId(id: String?): Nivel? = entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) }
    }
}
