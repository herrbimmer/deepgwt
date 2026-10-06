package br.com.deepgwt.tela3d.catalogo

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log

// API do catálogo para a faixa, as janelas e a mídia. Funções de extensão sobre [CatalogoDeApps];
// a lógica está nas classes internas (planejador, repositório, fábrica de ícones).

private const val ETIQUETA = "DeepGWT/Catalogo"

// --- Consultas do módulo de janelas ------------------------------------------------------------

/** O app prefere abrir em janela (H5). */
fun CatalogoDeApps.prefereJanela(pacote: String?): Boolean =
    pacote != null && pacote in repositorio.estado.janela

/** App GWM/fabricante (D2). */
fun CatalogoDeApps.ehAppGwm(pacote: String?): Boolean = regras.ehAppGwm(pacote)

/** Um dos 4 apps fixados no hub (D1). */
fun CatalogoDeApps.ehFixado(pacote: String?): Boolean = regras.ehFixado(pacote)

/** Stub do emulador (F): só existe no emulador e não está instalado de verdade. */
fun CatalogoDeApps.ehStubDeEmulador(pacote: String?): Boolean =
    planejador.ehStub(pacote, fonte.atividadesDeLancador())

/**
 * Registra a abertura de um app nos recentes (H2). Seguro de qualquer thread: muda o estado sob
 * trava e avisa a faixa na thread principal. Ignora projeção, stub, o próprio app, ignorados,
 * fixados e ocultos.
 */
fun CatalogoDeApps.registrarAbertura(pacote: String?) {
    if (pacote.isNullOrEmpty() || regras.ehProjecao(pacote) || ehStubDeEmulador(pacote)) return
    if (repositorio.registrarAbertura(pacote)) {
        notificar(MotivoDeMudanca(TipoDeMudanca.RECENTES, pacote))
    }
}

// --- Listas da faixa ---------------------------------------------------------------------------

/** Linha de apps (A6): ordem do sistema, um por pacote, com os ocultos marcados e os stubs no fim. */
fun CatalogoDeApps.linhaDeApps(): List<ItemDoCatalogo> {
    val estado = repositorio.estado
    return planejador.linha(fonte.atividadesDeLancador(), estado).map { paraItem(it, estado) }
}

/** Até 3 atalhos de recentes: instalados e permitidos. */
fun CatalogoDeApps.recentes(): List<ItemDoCatalogo> {
    val estado = repositorio.estado
    return planejador.recentes(fonte.atividadesDeLancador(), estado).map { paraItem(it, estado) }
}

/** O chip "GWM"; null se nenhum fixado está presente e visível (D3). */
fun CatalogoDeApps.hubGwm(): ItemDoHub? {
    val estado = repositorio.estado
    val fixados = planejador.fixadosDoHub(fonte.atividadesDeLancador(), estado)
    if (fixados.isEmpty()) return null
    val personalizacao = estado.personalizacoes.ler(FatosDoCarro.CHAVE_DO_HUB)
    return ItemDoHub(
        rotulo = planejador.rotuloDoHub(estado),
        icone = seguro { fabrica.iconeDoHub(personalizacao) },
        placa = Placas.paraAFaixa(planejador.placaDoHub(estado)),
        fixados = fixados.map { paraItem(it, estado) },
    )
}

/** Fixados do menu do hub (D5): ordem de D1, presentes e não ocultos. */
fun CatalogoDeApps.fixadosDoHub(): List<ItemDoCatalogo> {
    val estado = repositorio.estado
    return planejador.fixadosDoHub(fonte.atividadesDeLancador(), estado).map { paraItem(it, estado) }
}

/** Ocultos, ordenados por pacote (H4). Rótulo B4; ícone sem placa. */
fun CatalogoDeApps.ocultos(): List<ItemOculto> {
    val estado = repositorio.estado
    val instalados = planejador.primeiraPorPacote(fonte.atividadesDeLancador())
    return planejador.ocultosOrdenados(estado).map { pacote ->
        val atividade = instalados[pacote]
        ItemOculto(
            pacote = pacote,
            rotulo = planejador.rotuloDeOculto(pacote, atividade),
            icone = seguro { fabrica.iconeDeOculto(pacote, atividade) },
            instalado = atividade != null,
        )
    }
}

// --- Ações -------------------------------------------------------------------------------------

/** Oculta o app (menu "Hide"): sai dos recentes, a página relê o catálogo. */
fun CatalogoDeApps.ocultar(pacote: String) {
    if (repositorio.ocultar(pacote)) {
        notificar(MotivoDeMudanca(TipoDeMudanca.OCULTOS, pacote))
        invalidarPagina()
    }
}

/** Oculta o hub: os 4 fixados entram nos ocultos (instalados ou não). */
fun CatalogoDeApps.ocultarHubGwm() {
    if (repositorio.ocultarVarios(FatosDoCarro.FIXADOS)) {
        notificar(MotivoDeMudanca(TipoDeMudanca.OCULTOS))
        invalidarPagina()
    }
}

/** Restaura um app oculto; só age se ele estava oculto. */
fun CatalogoDeApps.restaurar(pacote: String) {
    if (repositorio.restaurar(pacote)) {
        Log.i(ETIQUETA, "App restaurado: $pacote")
        notificar(MotivoDeMudanca(TipoDeMudanca.OCULTOS, pacote))
        invalidarPagina()
    }
}

/** Pode alternar "abrir como janela" (H5): não é o próprio app, fixado, GWM nem projeção. */
fun CatalogoDeApps.podeAlternarJanela(pacote: String): Boolean = regras.podeAlternarJanela(pacote)

/** Alterna "abrir como janela" e devolve o novo valor (o valor atual se o app não pode alternar). */
fun CatalogoDeApps.alternarAbrirComoJanela(pacote: String): Boolean {
    val novo = repositorio.alternarJanela(pacote) ?: return prefereJanela(pacote)
    notificar(MotivoDeMudanca(TipoDeMudanca.JANELA, pacote))
    return novo
}

/** Pode desinstalar (H7): não é o próprio app, fixado, GWM nem app de sistema. */
fun CatalogoDeApps.podeDesinstalar(pacote: String): Boolean =
    regras.podeDesinstalarSemSistema(pacote) && !fonte.ehAppDeSistema(pacote)

/** Abre o diálogo de desinstalação do sistema. A remoção em si chega pelo receptor de pacotes. */
fun CatalogoDeApps.desinstalar(pacote: String) {
    if (!podeDesinstalar(pacote)) return
    try {
        val intencao = Intent(Intent.ACTION_DELETE, Uri.parse("package:$pacote"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        contexto.startActivity(intencao)
    } catch (e: Exception) {
        Log.w(ETIQUETA, "Falha ao pedir a desinstalação de $pacote", e)
    }
}

// --- Personalização ----------------------------------------------------------------------------

/** Personalização do app (nome, ícone, cor, fundo), ou null se não há. A chave do hub é `__gwm_hub`. */
fun CatalogoDeApps.personalizacao(pacote: String): Personalizacao? =
    repositorio.estado.personalizacoes.ler(pacote)

/** Grava a personalização (G3): sem nome nem ícone, remove. A faixa refaz o chip; a página relê o catálogo. */
fun CatalogoDeApps.salvarPersonalizacao(pacote: String, nome: String, icone: String?, cor: String?, fundo: String?) {
    if (pacote.isEmpty()) return
    repositorio.salvarPersonalizacao(pacote, nome, icone, cor, fundo)
    notificar(MotivoDeMudanca(TipoDeMudanca.PERSONALIZACAO, pacote))
    invalidarPagina()
}

/** Remove a personalização (G4). */
fun CatalogoDeApps.limparPersonalizacao(pacote: String) {
    if (pacote.isEmpty()) return
    repositorio.limparPersonalizacao(pacote)
    notificar(MotivoDeMudanca(TipoDeMudanca.PERSONALIZACAO, pacote))
    invalidarPagina()
}

/** Rótulo "de fábrica" (dica do campo de nome): o que o chip mostraria sem nome personalizado. */
fun CatalogoDeApps.rotuloPadrao(pacote: String): String {
    if (pacote == FatosDoCarro.CHAVE_DO_HUB) return FatosDoCarro.ROTULO_DO_HUB
    val doSistema = fonte.rotuloDoAplicativo(pacote) ?: return pacote
    return if (regras.ehFixado(pacote)) regras.rotuloDeFixado(pacote, doSistema) else regras.rotuloBase(pacote, doSistema)
}

/** Ícone substituto de um slug com a cor (C5); null se o slug não existe. Para a pré-visualização da folha. */
fun CatalogoDeApps.iconeSubstituto(slug: String, corHex: String?): Drawable? =
    fabrica.iconeSubstituto(slug, corHex)

/** Ícone substituto da personalização de quem toca (chip de origem do cartão); null se não há. */
fun CatalogoDeApps.iconeSubstitutoDoPacote(pacote: String?): Drawable? {
    if (pacote.isNullOrEmpty()) return null
    return seguro { fabrica.iconeSubstituto(repositorio.estado.personalizacoes.ler(pacote)) }
}

/** Normaliza ícone adaptativo para um quadrado [lado]×[lado] sem máscara (C2); outros ícones voltam iguais. */
fun CatalogoDeApps.normalizarIconeAdaptativo(icone: Drawable?, lado: Int): Drawable? =
    NormalizacaoDeIcone.normalizar(contexto.resources, icone, lado)

/** A faixa se registra aqui para redesenhar quando algo muda. */
fun CatalogoDeApps.registrarOuvinte(ouvinte: OuvinteDoCatalogo) = registrarOuvinteInterno(ouvinte)

// --- Recentes: semente -------------------------------------------------------------------------

/**
 * Na criação da tela inicial, se não há recentes, tenta semear com as tarefas recentes do sistema
 * (até 3, instalados e permitidos). Melhor esforço: sem permissão ou sem reflexão, não semeia.
 * Devolve true se semeou.
 */
@Suppress("DEPRECATION")
fun CatalogoDeApps.semearRecentesSeVazio(): Boolean {
    if (repositorio.estado.recentes.isNotEmpty()) return false
    return try {
        val gerente = contexto.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val tarefas = gerente.getRecentTasks(16, FLAG_RECENT_WITH_EXCLUDED) ?: return false
        val candidatos = ArrayList<String>(tarefas.size)
        for (tarefa in tarefas) {
            val pacote = try {
                (ActivityManager.RecentTaskInfo::class.java.getField("realActivity").get(tarefa) as? ComponentName)?.packageName
            } catch (e: Exception) {
                null
            }
            if (!pacote.isNullOrEmpty()) candidatos.add(pacote)
        }
        val instalados = planejador.primeiraPorPacote(fonte.atividadesDeLancador()).keys
        val semeou = repositorio.semearSeVazio(candidatos, instalados)
        if (semeou) notificar(MotivoDeMudanca(TipoDeMudanca.RECENTES))
        semeou
    } catch (e: Exception) {
        false
    }
}

private const val FLAG_RECENT_WITH_EXCLUDED = 1

// --- Ajudantes ---------------------------------------------------------------------------------

private fun CatalogoDeApps.paraItem(item: ItemPlanejado, estado: EstadoDoUsuario): ItemDoCatalogo {
    val personalizacao = estado.personalizacoes.ler(item.pacote)
    return ItemDoCatalogo(
        pacote = item.pacote,
        rotulo = item.rotulo,
        icone = seguro { fabrica.iconeDoItem(item, personalizacao) },
        placa = Placas.paraAFaixa(item.placa),
        oculto = item.oculto,
    )
}

private inline fun <T> seguro(bloco: () -> T?): T? = try {
    bloco()
} catch (e: Exception) {
    Log.w(ETIQUETA, "Falha ao obter ícone", e)
    null
}
