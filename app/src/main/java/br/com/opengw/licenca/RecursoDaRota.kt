package br.com.opengw.licenca

/**
 * O recurso que cada rota do GwLink (porta 8766) exige (Alpha 8.0). `null` = livre.
 *
 * É a trava da porta dos fundos: o instalador já mostra o cadeado antes de chamar, mas qualquer
 * cliente na rede do carro fala com esta porta. Kotlin puro, testado em `RecursoTest`.
 *
 * O QUE FICA LIVRE, DE PROPÓSITO
 *  - Telemetria (`/`), `/diag`, `/identity`, `/storage`, `/displays`: leitura do Drive (monitor e
 *    identidade) e do relatório de problema.
 *  - `/files/delete`: só apaga tema — é desfazer.
 *  - `/ajustes` e `/temas/ativar`: cada ajuste e cada tema é conferido lá dentro, com o motivo certo.
 *  - `/set`: escreve uma chave do carro. Quem desfaz uma mudança ("Alterações e restauração") usa a
 *    mesma rota, e desfazer é sempre grátis; o instalador trava as telas que escrevem por ela.
 *  - `/launcher/set`: voltar à tela de fábrica é desfazer; escolher o 3D é conferido no próprio
 *    tratamento (o pacote vem no corpo).
 */
object RecursoDaRota {
    /** Trecho do caminho da pasta de temas no carro (`files/themes`). */
    const val PASTA_DE_TEMAS = "/files/themes"

    fun de(metodo: String, caminho: String, parametros: Map<String, String>): Recurso? {
        val post = metodo.equals("POST", ignoreCase = true)
        return when {
            caminho == "/mirror" || caminho == "/screenshot" || caminho.startsWith("/input/") -> Recurso.ESPELHAR
            caminho == "/files/delete" -> null
            caminho.startsWith("/files/") -> {
                val dosTemas = daPastaDeTemas(parametros["path"].orEmpty())
                when {
                    !dosTemas -> Recurso.ARQUIVOS
                    caminho == "/files/put" -> Recurso.TEMAS_XTRA
                    else -> Recurso.TEMAS_EMBUTIDOS
                }
            }
            caminho.startsWith("/backup/") || caminho == "/apps/list" -> Recurso.BACKUP
            caminho == "/dvr/find" -> Recurso.DVR
            caminho == "/profiles" || caminho.startsWith("/profiles/") -> Recurso.PERFIS
            post && caminho == "/hud/tema" -> Recurso.EDITOR_DE_TEMAS
            post && caminho == "/quick/asd" -> Recurso.ASD
            post && caminho == "/quick/sportsdisplay" -> Recurso.PAINEL_ESPORTIVO
            else -> null
        }
    }

    /**
     * O caminho está DENTRO da pasta de temas? (Alpha 8.3) Até a 8.2 bastava conter
     * [PASTA_DE_TEMAS]: `…/files/themes/../../shared_prefs/…` passava por tema e lia/gravava fora
     * dela com o nível de temas. Agora um `..` (ou `.`) no caminho tira a rota da pasta de temas —
     * ela passa a pedir o nível de Arquivos.
     */
    fun daPastaDeTemas(caminho: String): Boolean {
        val partes = caminho.replace('\\', '/').split('/')
        if (partes.any { it == ".." || it == "." }) return false
        return caminho.contains(PASTA_DE_TEMAS)
    }
}
