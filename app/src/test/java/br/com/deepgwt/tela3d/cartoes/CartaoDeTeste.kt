package br.com.deepgwt.tela3d.cartoes

import br.com.deepgwt.tela3d.ponte.CartaoInferior
import br.com.deepgwt.tela3d.ponte.LinhaDeMenu
import br.com.deepgwt.tela3d.ponte.OpcoesDoRelogio

/** Monta um [CartaoInferior] só com o que o teste quer dizer; o resto fica vazio. */
internal fun cartao(
    id: String,
    titulo: String = "",
    valor: String = "",
    acao: String = "",
    primario: String = "",
    secundario: String = "",
    metricaA: String = "",
    metricaB: String = "",
    progresso: Int = 0,
    acaoDoIcone: String = "",
    acaoLonga: String = "",
    glifo: String = "",
    textoDoGlifo: String = "",
    relogio: OpcoesDoRelogio = OpcoesDoRelogio.PADRAO,
    menu: List<LinhaDeMenu> = emptyList(),
    menuDeEdicao: List<LinhaDeMenu> = emptyList(),
    estado: String = "",
    rodas: List<String> = List(4) { "normal" },
    pressoes: String = "",
    navCidadeOciosa: String = "",
    navAcaoOciosa: String = "",
    barras: List<Int> = emptyList(),
) = CartaoInferior(
    id = id, titulo = titulo, valor = valor, acao = acao, primario = primario, secundario = secundario,
    metricaA = metricaA, metricaB = metricaB, progresso = progresso, demo = false,
    acaoDoIcone = acaoDoIcone, acaoLonga = acaoLonga, glifo = glifo, textoDoGlifo = textoDoGlifo,
    relogio = relogio, menu = menu, menuDeEdicao = menuDeEdicao, estado = estado, estadoDasRodas = rodas,
    variantePotencia = "phev19", sabeSoc = false, soc = 0.0, pressoes = pressoes, temperaturas = "",
    unidadeDePressao = "", pacoteDoApp = "", navRestante = "", navDuracao = "", navChegada = "",
    navCidadeOciosa = navCidadeOciosa, navAcaoOciosa = navAcaoOciosa, barrasDeEnergia = barras,
)
