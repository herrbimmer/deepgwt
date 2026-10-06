// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.licenca

/**
 * O que cada nível libera — a FONTE ÚNICA da tabela de níveis (Alpha 8.0).
 *
 * Mudar um recurso de nível é mudar uma linha aqui. Todo o resto lê desta tabela: o menu e as telas
 * do app (cadeado), os serviços (o [Portao] decide se sobem), o catálogo de ajustes (`/ajustes`
 * devolve `bloqueado`), as rotas do GwLink e o instalador — que recebe a tabela pronta em
 * `GET /licenca` e tem uma cópia em `instalador/src/mock/licencaFixture.json`, travada contra esta
 * pelo teste `RecursoTest` (mesmo padrão do `ajustesFixture.json`).
 *
 * PRINCÍPIOS
 *  - Desfazer é sempre grátis: instalar, atualizar, reparar, sair do modo sorrateiro, remover,
 *    voltar à tela de fábrica, pontos de restauração e relatar problema ficam em [ESSENCIAL]. Nunca
 *    prender o carro num estado modificado.
 *  - Acessibilidade nunca é paga: estilo, tamanho da interface e das letras são [INTERFACE] (Drive).
 *  - Bloqueado aparece com cadeado e o nome do nível; não some.
 *
 * O [id] viaja para o instalador e não muda. [titulo] é o que o cadeado diz ("Disponível no Premium:
 * Macros").
 */
enum class Recurso(val id: String, val titulo: String, val nivel: Nivel) {
    // ── Drive (grátis) ────────────────────────────────────────────────────────────────────
    ESSENCIAL("essencial", "Instalar, reparar, desfazer e informações", Nivel.DRIVE),
    BARRA("barra", "Barra inferior e cartão de volume", Nivel.DRIVE),
    PAINEL_ORIGINAL("painel.original", "Informações no painel original", Nivel.DRIVE),
    CIRCULO("painel.circulo", "Círculo e relógios no painel original", Nivel.DRIVE),
    AJUSTES_RAPIDOS("carro.rapidos", "AVAS, distração, vidros e ajustes rápidos", Nivel.DRIVE),
    BRILHO("brilho", "Brilho automático", Nivel.DRIVE),
    AUTOMACOES("automacoes", "Automações do veículo", Nivel.DRIVE),
    INTERFACE("interface", "Estilo, tamanho e letras do app", Nivel.DRIVE),
    MONITOR("monitor", "Monitor e identidade do veículo", Nivel.DRIVE),
    // Alpha 8.5: consertam defeitos do carro que os donos mais reclamam — ficam no grátis.
    MEMORIA_ADAS("adas.memoria", "Memória dos assistentes de faixa e velocidade", Nivel.DRIVE),
    CONECTOR("conector", "Liberar o conector de recarga (PHEV)", Nivel.DRIVE),

    // ── Sport ─────────────────────────────────────────────────────────────────────────────
    TEMAS_EMBUTIDOS("temas.embutidos", "Temas do painel que vêm no app", Nivel.SPORT),
    HUD("hud", "Head-up display", Nivel.SPORT),
    ASD("asd", "Som do motor (ASD)", Nivel.SPORT),
    AUDIO("audio", "Equalizador e reverb", Nivel.SPORT),
    LAUNCHER_3D("launcher3d", "Tela inicial 3D", Nivel.SPORT),
    LUZ_AMBIENTE("luz", "Luz ambiente", Nivel.SPORT),
    PERFIS("perfis", "Perfis de motorista", Nivel.SPORT),
    DVR("dvr", "Gravação (DVR)", Nivel.SPORT),
    SISTEMA("sistema", "Sistema e desempenho", Nivel.SPORT),
    ESPELHAR("espelhar", "Espelhar e controlar a tela", Nivel.SPORT),
    CORINGAS("volante.coringas", "Reprogramar os botões coringa do volante", Nivel.SPORT),

    // ── Premium ───────────────────────────────────────────────────────────────────────────
    TEMAS_XTRA("temas.xtra", "Catálogo XTRA (275 temas)", Nivel.PREMIUM),
    EDITOR_DE_TEMAS("temas.editor", "Editor de temas e de HUD", Nivel.PREMIUM),
    MACROS("macros", "Macros", Nivel.PREMIUM),
    GESTOS("gestos", "Gestos na tela", Nivel.PREMIUM),
    STREAM_DECK("streamdeck", "Botões extra", Nivel.PREMIUM),
    PAINEL_ESPORTIVO("painel.esportivo", "Painel esportivo", Nivel.PREMIUM),
    FUNCOES_DE_FABRICA("coding.guiado", "Funções de fábrica guiadas", Nivel.PREMIUM),
    ADAS("adas", "Assistentes de direção (ADAS)", Nivel.PREMIUM),
    INSTALAR_APPS("apps.instalar", "Instalar apps", Nivel.PREMIUM),
    ARQUIVOS("arquivos", "Arquivos da central", Nivel.PREMIUM),
    BACKUP("backup", "Backup da central", Nivel.PREMIUM),
    DIAGNOSTICO("diagnostico", "Diagnóstico", Nivel.PREMIUM),

    // ── Signature ─────────────────────────────────────────────────────────────────────────
    CRIADOR_DE_TEMAS("temas.criador", "Criador de temas e importar HTML", Nivel.SIGNATURE),
    CODING("coding", "Coding completo", Nivel.SIGNATURE),
    OFICINA("oficina", "Oficina", Nivel.SIGNATURE),
    ENGENHARIA("engenharia", "Engenharia", Nivel.SIGNATURE),
    TELEMETRIA_CRUA("gwlink", "Telemetria crua (GWLink)", Nivel.SIGNATURE),
    VALORES_ATUAIS("valores", "Valores atuais", Nivel.SIGNATURE),
    APPS_DO_SISTEMA("apps.sistema", "Desinstalar apps do sistema", Nivel.SIGNATURE);

    companion object {
        fun deId(id: String?): Recurso? = entries.firstOrNull { it.id == id }

        /** Os recursos de cada nível, na ordem da tabela — é o que a tela "Níveis" mostra. */
        fun doNivel(nivel: Nivel): List<Recurso> = entries.filter { it.nivel == nivel }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
