package br.com.opengw.perfis

import br.com.opengw.nucleo.OpenGw
import br.com.opengw.veiculo.Comandos
import android.util.Log
/**
 * Faz o que já está rodando obedecer às preferências recém-gravadas.
 *
 * POR QUE ISTO PRECISA EXISTIR
 *
 * Gravar no SharedPreferences não muda nada por si. Parte do OPEN GW lê a preferência na hora de
 * usar — essa obedece sozinha —, mas parte mantém um ESPELHO em memória, carregado uma vez quando
 * o serviço sobe: a barra inferior copia tudo para `EstadoDaBarra` no `onCreate`, o HUD guarda os
 * tamanhos em `HudSettings`, o motor de som já recebeu volume e curva. Para esses, uma troca de
 * perfil sem este passo seria a pior espécie de bug: os valores certos gravados no disco, a tela
 * mostrando os valores certos na tela de ajustes, e o carro se comportando como o motorista
 * anterior até alguém reiniciar a central.
 *
 * COMO CADA UM É CHAMADO
 *
 * Sempre pelo caminho que o próprio subsistema já usa quando o usuário mexe naquele ajuste —
 * nenhum caminho novo foi inventado aqui. Se o botão da tela de ajustes funciona, isto funciona,
 * porque é o mesmo código.
 *
 * O painel virtual não aparece na lista: ele escuta o SharedPreferences por conta própria
 * (o ouvinte de preferências do painel, br.com.opengw.painel) e recarrega quando o carimbo de recarga muda — que o
 * DriverProfileManager grava na mesma transação das outras chaves.
 *
 * Cada item é isolado: um subsistema que exploda não pode levar os outros junto, então o nome
 * dele entra na lista devolvida e o laço segue. Quem chama transforma essa lista no que o
 * motorista lê.
 */
object ReconciliadorDeAjustes {
    private const val TAG = "ReconciliadorDeAjustes"

    /** Devolve os nomes dos subsistemas que falharam. Lista vazia = tudo releu. */
    fun reconciliarTudo(): List<String> {
        val falhas = mutableListOf<String>()

        tentar(falhas, "barra inferior") {
            br.com.opengw.barra.Barra.recarregarPreferencias()
        }

        tentar(falhas, "som do motor (ASD)") {
            br.com.opengw.managers.asd.AsdManager.reconciliarComAsPreferencias()
        }

        tentar(falhas, "camada do HUD") {
            // `recarregar`, e não `load`: o perfil acabou de reescrever as preferências, e `load`
            // é guardada contra releitura (ver HudSettings.recarregar).
            br.com.opengw.hud.HudSettings.recarregar()
            val hud = br.com.opengw.hud.HudOverlayController
            if (hud.isEnabled()) hud.restoreIfEnabled() else hud.hide()
            hud.requestRedraw()
        }

        tentar(falhas, "luz ambiente") {
            br.com.opengw.luz.LuzAmbiente.reaplicar(OpenGw.contexto)
        }

        tentar(falhas, "gestos rápidos") {
            br.com.opengw.managers.QuickGesturesManager.startIfEnabled()
        }

        tentar(falhas, "dados móveis") {
            br.com.opengw.rede.ControleDeDadosMoveis.reavaliar(OpenGw.contexto)
            br.com.opengw.rede.ControleDeDadosMoveis.reaplicarTelemetria()
        }

        tentar(falhas, "roteador do hotspot") {
            val ligado = OpenGw.contextoProtegido.getSharedPreferences("opengw_prefs", android.content.Context.MODE_PRIVATE)
                .getBoolean(br.com.opengw.nucleo.Preferencias.ENABLE_HOT_ROUTER.chave, false)
            br.com.opengw.rede.RoteadorDoHotspot.definirLigado(ligado)
        }

        tentar(falhas, "painel lateral") {
            br.com.opengw.barra.PainelLateral.reapply()
        }

        tentar(falhas, "brilho automático") {
            br.com.opengw.brilho.BrilhoAutomatico.atualizar()
        }

        tentar(falhas, "apps do sistema desativados") {
            br.com.opengw.veiculo.Comandos.garantirAppsDesativados()
        }

        tentar(falhas, "tema do painel") {
            // O perfil pode trazer um tema que não existe mais no carro (ex.: o Stuttgart NTG 5.5
            // do motor, aposentado na 7.6). Sem isto o painel caía no embutido até a próxima
            // subida; a checagem da subida é a mesma e manda para o tema padrão (NTG clássico).
            br.com.opengw.painel.TemasInstalados.sanearTemaAtivo()
        }

        tentar(falhas, "margem da tela (overscan)") {
            br.com.opengw.telas.Telas.reaplicarLimitesDoDisplay0()
        }

        return falhas
    }

    private inline fun tentar(falhas: MutableList<String>, nome: String, bloco: () -> Unit) {
        try {
            bloco()
        } catch (e: Throwable) {
            // Throwable e não Exception: um subsistema pode não existir nesta central (classe
            // ausente, serviço morto) e isso chega como Error, não como Exception. Continuar com
            // os outros vale mais do que insistir neste.
            Log.e(TAG, "Falha ao reconciliar \"$nome\"", e)
            falhas.add(nome)
        }
    }
}
