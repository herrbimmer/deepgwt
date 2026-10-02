package br.com.opengw.barra

import androidx.compose.runtime.Composable

/** Conteúdo da janela da barra (interface §A). */
@Composable
internal fun ConteudoDaJanelaDaBarra() = ConteudoDaBarra()

/** Conteúdo da janela de menus (interface §B). O painel expandido NÃO é composto aqui (S-D11). */
@Composable
internal fun ConteudoDaJanelaDeMenus() = ConteudoDosMenus()
