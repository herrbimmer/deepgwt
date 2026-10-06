// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ajustes

import br.com.deepgwt.luz.ModoMusical
import br.com.deepgwt.luz.SaidaDaLuz
import br.com.deepgwt.licenca.Recurso
import br.com.deepgwt.managers.OemAudioDsp
import br.com.deepgwt.managers.asd.AsdAtivacao
import br.com.deepgwt.managers.asd.AsdCadenciaEscolhida
import br.com.deepgwt.managers.asd.AsdFonteDeRotacao
import br.com.deepgwt.managers.asd.AsdPerfilDeSom
import br.com.deepgwt.veiculo.Sinal
import br.com.deepgwt.veiculo.Sinais
import br.com.deepgwt.nucleo.Preferencia as ChaveDePreferencia
import br.com.deepgwt.nucleo.Preferencias as K
import br.com.deepgwt.veiculo.HeadlightPolicy
import br.com.deepgwt.ui.EscalaDaInterface
import br.com.deepgwt.ui.EstiloDaInterface

/**
 * Todos os ajustes do DeepGWT que o instalador pode ler e mudar pela rede (`/ajustes`).
 *
 * DE ONDE SAÍRAM OS TEXTOS, FAIXAS E PADRÕES
 *
 * Das telas do próprio app — rótulo, explicação, faixa do controle deslizante e o padrão que a
 * tela usa quando a chave nunca foi gravada. Um padrão diferente do da tela faria o instalador
 * mostrar "alterado" num carro que ninguém mexeu. O teste [CatalogoDeAjustesTest] trava as
 * regras que dá para travar sem Android (chaves únicas, padrão dentro da faixa, dependências
 * que existem).
 *
 * O QUE FICOU DE FORA, DE PROPÓSITO
 *
 *  - O que [br.com.deepgwt.perfis.EscopoDoPerfil] exclui por perigo: modo
 *    sorrateiro. Ligá-lo pela rede esconderia o app sem aviso.
 *  - Estado passageiro e identidade da central (ids de tela, MAC pareado, carimbos).
 *  - O que precisa de um seletor que só existe no carro: tema do painel (o carro procura a
 *    imagem de fundo do tema na hora de aplicar), app de arranque, atalhos do Stream Deck,
 *    macros gravadas, fita de LED a parear.
 *  - Controle de dados móveis e roteador de hotspot: mexem na rede pela qual o próprio
 *    instalador está falando com o carro. Desligar a conexão por ela mesma é um jeito certo de
 *    perder o carro no meio da mudança.
 *
 * ACRESCENTAR UM AJUSTE
 *
 * Uma linha aqui. Se a tela do carro chama alguma coisa depois de gravar, o ajuste precisa do
 * [Efeito] correspondente — senão o valor muda no disco e o carro só obedece depois de reiniciar.
 */
object CatalogoDeAjustes {

    val todos: List<Ajuste> by lazy {
        montar().map { a -> RECURSO_POR_AJUSTE[a.id]?.let { a.copy(recurso = it) } ?: a }
    }

    /**
     * Ajustes que pedem outro nível que o da seção (Alpha 8.0). O resto herda o da seção
     * ([SecaoDeAjustes.recurso]). Numa seção de tema (Sport) moram as informações e o círculo do
     * painel original, que são Drive; numa de gestos (Premium) moram a barra lateral, o gesto que
     * leva ao círculo e o microfone do "Olá Haval", que não são.
     */
    private val RECURSO_POR_AJUSTE: Map<String, Recurso> =
            mapOf(
                    ID_DAS_INFORMACOES to Recurso.PAINEL_ORIGINAL,
                    K.ORIGINAL_PANEL_TEMP_SCALE.chave to Recurso.PAINEL_ORIGINAL,
                    ID_DO_CIRCULO to Recurso.CIRCULO,
                    K.PROJECTION_GESTURE_ENABLED.chave to Recurso.CIRCULO,
                    K.HIDE_LEFT_NAV_PANE.chave to Recurso.AJUSTES_RAPIDOS,
                    K.DISABLE_VOICE_WAKE_UP.chave to Recurso.SISTEMA,
                    K.MACROS_ENABLED.chave to Recurso.MACROS,
                    K.ADAS_MEMORIA_ATIVA.chave to Recurso.MEMORIA_ADAS
            )

    private val porId: Map<String, Ajuste> by lazy { todos.associateBy { it.id } }

    fun ajuste(id: String): Ajuste? = porId[id]

    /**
     * As chaves do `deepgwt_prefs` que, se mudarem, mudam algum valor que o instalador mostra.
     * É o filtro do contador de revisão: sem ele, os contadores de tráfego (gravados a cada
     * poucos segundos) acordariam o instalador o tempo todo para nada.
     */
    val chavesObservadas: Set<String> by lazy {
        buildSet {
            todos.forEach { a ->
                when (val g = a.guardado) {
                    is Guardado.Preferencia -> add(g.chave)
                    is Guardado.Painel -> addAll(CHAVES_DO_MODO_DO_PAINEL)
                    is Guardado.TemaAtivo -> add(K.ACTIVE_CUSTOM_THEME.chave)
                    is Guardado.Veiculo -> Unit
                }
                a.espelhos.forEach { add(it.chave) }
            }
        }
    }

    /** O ajuste cujas opções são os temas instalados. */
    const val ID_DO_TEMA_DO_PAINEL = "painel.tema"
    const val ID_DAS_INFORMACOES = "painel.informacoes"
    const val ID_DO_CIRCULO = "painel.circulo"

    val chavesDoVeiculo: Set<String> by lazy {
        todos.mapNotNull { (it.guardado as? Guardado.Veiculo)?.chave }.toSet()
    }

    /** As seis chaves que `PainelEscolhido` grava. */
    val CHAVES_DO_MODO_DO_PAINEL: Set<String> =
            setOf(
                    K.ENABLE_INSTRUMENT_PROJECTOR.chave,
                    K.ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION.chave,
                    K.ENABLE_CUSTOM_MENU.chave,
                    K.ENABLE_VIRTUAL_CLUSTER.chave,
                    K.ORIGINAL_PANEL_INFO.chave,
                    K.ORIGINAL_PANEL_CIRCLE_PROJECTION.chave
            )

    // ── Construtores curtos ────────────────────────────────────────────────────────────────

    private fun interruptor(
            secao: SecaoDeAjustes,
            chave: ChaveDePreferencia,
            rotulo: String,
            padrao: Boolean,
            ajuda: String? = null,
            efeito: Efeito = Efeito.NENHUM,
            visivelSe: Condicao? = null,
            espelhos: List<Espelho> = emptyList(),
            lerDe: ((LeitorDePreferencias) -> Any?)? = null,
            avancado: Boolean = false,
            aviso: String? = null,
            soNoComputador: Boolean = false
    ) =
            Ajuste(
                    id = chave.chave,
                    secao = secao,
                    rotulo = rotulo,
                    tipo = TipoDeAjuste.INTERRUPTOR,
                    guardado = Guardado.Preferencia(chave.chave, Formato.BOOLEANO),
                    padrao = padrao,
                    ajuda = ajuda,
                    efeito = efeito,
                    visivelSe = visivelSe,
                    espelhos = espelhos,
                    lerDe = lerDe,
                    avancado = avancado,
                    aviso = aviso,
                    soNoComputador = soNoComputador
            )

    private fun numero(
            secao: SecaoDeAjustes,
            chave: ChaveDePreferencia,
            rotulo: String,
            padrao: Number,
            min: Number,
            max: Number,
            passo: Number = 1,
            unidade: String? = null,
            formato: Formato = Formato.INTEIRO,
            ajuda: String? = null,
            efeito: Efeito = Efeito.NENHUM,
            visivelSe: Condicao? = null,
            avancado: Boolean = false,
            soNoComputador: Boolean = false
    ) =
            Ajuste(
                    id = chave.chave,
                    secao = secao,
                    rotulo = rotulo,
                    tipo = TipoDeAjuste.NUMERO,
                    guardado = Guardado.Preferencia(chave.chave, formato),
                    padrao = if (formato == Formato.INTEIRO) padrao.toInt() else padrao.toDouble(),
                    ajuda = ajuda,
                    min = min.toDouble(),
                    max = max.toDouble(),
                    passo = passo.toDouble(),
                    unidade = unidade,
                    efeito = efeito,
                    visivelSe = visivelSe,
                    avancado = avancado,
                    soNoComputador = soNoComputador
            )

    private fun opcoes(
            secao: SecaoDeAjustes,
            chave: ChaveDePreferencia,
            rotulo: String,
            padrao: String,
            opcoes: List<Opcao>,
            ajuda: String? = null,
            efeito: Efeito = Efeito.NENHUM,
            visivelSe: Condicao? = null,
            formato: Formato = Formato.TEXTO,
            espelhos: List<Espelho> = emptyList(),
            lerDe: ((LeitorDePreferencias) -> Any?)? = null,
            avancado: Boolean = false,
            soNoComputador: Boolean = false
    ) =
            Ajuste(
                    id = chave.chave,
                    secao = secao,
                    rotulo = rotulo,
                    tipo = TipoDeAjuste.OPCOES,
                    guardado = Guardado.Preferencia(chave.chave, formato),
                    padrao = padrao,
                    ajuda = ajuda,
                    opcoes = opcoes,
                    efeito = efeito,
                    visivelSe = visivelSe,
                    espelhos = espelhos,
                    lerDe = lerDe,
                    avancado = avancado,
                    soNoComputador = soNoComputador
            )

    private fun cor(
            secao: SecaoDeAjustes,
            chave: ChaveDePreferencia,
            rotulo: String,
            padrao: String,
            efeito: Efeito,
            ajuda: String? = null,
            visivelSe: Condicao? = null
    ) =
            Ajuste(
                    id = chave.chave,
                    secao = secao,
                    rotulo = rotulo,
                    tipo = TipoDeAjuste.COR,
                    guardado = Guardado.Preferencia(chave.chave, Formato.COR_ARGB),
                    padrao = padrao,
                    ajuda = ajuda,
                    efeito = efeito,
                    visivelSe = visivelSe
            )

    /**
     * Um horário que a tela antiga guardava em duas chaves inteiras. Na conversa vira um `HH:MM`
     * só — dois controles deslizantes (hora e minuto) para escolher "20:30" é o tipo de coisa que
     * faz alguém desistir do ajuste.
     */
    private fun horario(
            secao: SecaoDeAjustes,
            hora: ChaveDePreferencia,
            minuto: ChaveDePreferencia,
            rotulo: String,
            padraoHora: Int,
            padraoMinuto: Int,
            ajuda: String? = null,
            efeito: Efeito = Efeito.NENHUM,
            visivelSe: Condicao? = null,
            soNoComputador: Boolean = false
    ) =
            Ajuste(
                    id = hora.chave,
                    secao = secao,
                    rotulo = rotulo,
                    tipo = TipoDeAjuste.HORA,
                    guardado = Guardado.Preferencia(hora.chave, Formato.HORA),
                    padrao = ConversaoDeAjuste.horaTexto(padraoHora, padraoMinuto),
                    ajuda = ajuda,
                    efeito = efeito,
                    visivelSe = visivelSe,
                    espelhos = listOf(Espelho(minuto.chave, Formato.INTEIRO) { ConversaoDeAjuste.minutoDe(it as String) }),
                    lerDe = { p ->
                        ConversaoDeAjuste.horaTexto(
                                p.inteiro(hora.chave, padraoHora).coerceIn(0, 23),
                                p.inteiro(minuto.chave, padraoMinuto).coerceIn(0, 59)
                        )
                    },
                    soNoComputador = soNoComputador
            )

    private fun se(chave: ChaveDePreferencia, vararg valores: String) = Condicao(chave.chave, valores.toSet())

    private fun ligado(chave: ChaveDePreferencia) = se(chave, "true")

    // ── O catálogo ─────────────────────────────────────────────────────────────────────────

    private fun montar(): List<Ajuste> = buildList {
        barra()
        carro()
        gestos()
        asd()
        audio()
        painel()
        hud()
        luz()
        brilho()
        automacoes()
        sistema()
        launcher()
        interfaceDoApp()
    }

    private fun MutableList<Ajuste>.barra() {
        val s = SecaoDeAjustes.BARRA
        val barraLigada = ligado(K.ENABLE_PERSISTENT_BOTTOM_BAR)
        add(
                interruptor(
                        s, K.ENABLE_PERSISTENT_BOTTOM_BAR, "Barra inferior", false,
                        ajuda = "Overlay persistente com controles rápidos do veículo.",
                        efeito = Efeito.BARRA_SERVICO
                )
        )
        add(
                numero(
                        // Passo 4 e não 8 (o +/- da tela do carro): a grade do controle começa em
                        // 40, e com 8 o padrão 60 ficaria fora dela — o controle deslizante do
                        // instalador pularia para 56 ou 64 no primeiro toque.
                        s, K.BOTTOM_BAR_HEIGHT_DP, "Altura", 60, 40, 140, passo = 4, unidade = "dp",
                        efeito = Efeito.BARRA, visivelSe = barraLigada
                )
        )
        add(
                opcoes(
                        s, K.BOTTOM_BAR_TEMP_CONTROL, "Controle de temperatura", "single",
                        listOf(Opcao("off", "Desligado"), Opcao("single", "Uma zona"), Opcao("dual", "Duas zonas")),
                        efeito = Efeito.BARRA, visivelSe = barraLigada,
                        // Os dois interruptores antigos continuam coerentes: um downgrade do app
                        // encontra a barra do jeito que a pessoa deixou (igual à tela do carro).
                        espelhos =
                                listOf(
                                        Espelho(K.BOTTOM_BAR_SHOW_AC.chave, Formato.BOOLEANO) { it == "single" },
                                        Espelho(K.BOTTOM_BAR_SHOW_AC_DUAL_ZONE.chave, Formato.BOOLEANO) { it == "dual" }
                                ),
                        // Sem a chave nova, vale a leitura dos dois antigos (ContasDaBarra.controleDeTemperatura, caso 19 do estado da barra).
                        lerDe = { p ->
                            p.texto(K.BOTTOM_BAR_TEMP_CONTROL.chave)?.takeIf { it in setOf("off", "single", "dual") }
                                    ?: when {
                                        p.booleano(K.BOTTOM_BAR_SHOW_AC_DUAL_ZONE.chave, false) -> "dual"
                                        p.booleano(K.BOTTOM_BAR_SHOW_AC.chave, true) -> "single"
                                        else -> "off"
                                    }
                        }
                )
        )
        add(
                interruptor(
                        s, K.BOTTOM_BAR_SHOW_VOLUME, "Controle rápido de volume", false,
                        efeito = Efeito.BARRA, visivelSe = barraLigada
                )
        )
        add(
                interruptor(
                        s, K.BOTTOM_BAR_SHOW_SPORT_PLUS, "Modo de condução e SPORT+", true,
                        ajuda = "Botão que mostra o modo atual. Toque abre o menu Condução (o SPORT+ pede confirmação); segurar e arrastar para cima troca o modo.",
                        efeito = Efeito.BARRA, visivelSe = barraLigada
                )
        )
        add(
                interruptor(
                        s, K.VOLUME_OVERLAY_ON_CAR_CHANGE, "Cartão de volume ao mudar o volume", true,
                        ajuda = "Mostra o cartão também quando o volume muda pelo volante ou pelo botão físico, e não só pela barra.",
                        efeito = Efeito.BARRA
                )
        )
        add(
                opcoes(
                        s, K.VOLUME_OVERLAY_LOGO_TYPE, "Logo no cartão de volume", "gwm",
                        listOf(Opcao("gwm", "GWM"), Opcao("jbl", "JBL"), Opcao("hertz", "Hertz"), Opcao("custom", "Texto")),
                        efeito = Efeito.BARRA
                )
        )
        add(
                Ajuste(
                        id = K.VOLUME_OVERLAY_LOGO_CUSTOM_TEXT.chave,
                        secao = s,
                        rotulo = "Texto do logo",
                        tipo = TipoDeAjuste.TEXTO,
                        guardado = Guardado.Preferencia(K.VOLUME_OVERLAY_LOGO_CUSTOM_TEXT.chave, Formato.TEXTO),
                        padrao = "",
                        maxCaracteres = 12,
                        visivelSe = se(K.VOLUME_OVERLAY_LOGO_TYPE, "custom"),
                        efeito = Efeito.BARRA
                )
        )
        add(
                interruptor(
                        s, K.HIDE_BAR_ON_ANDROID_AUTO, "Ocultar barra no Android Auto e CarPlay", true,
                        ajuda = "A barra sai sozinha ao entrar na projeção e volta ao sair. Um deslize de baixo para cima traz a barra de volta sem sair da projeção."
                )
        )
        add(
                interruptor(
                        s, K.BOTTOM_BAR_AUTO_HIDE, "Esconder a barra sozinha", false,
                        ajuda = "A barra some depois de 30 segundos sem uso.",
                        efeito = Efeito.BARRA, visivelSe = barraLigada, soNoComputador = true
                )
        )
        add(
                opcoes(
                        s, K.BOTTOM_BAR_SWIPE_UP_ACTION, "Ao deslizar a barra para cima", "drawer",
                        listOf(
                                Opcao("drawer", "Abrir a gaveta de atalhos"),
                                Opcao("dashboard", "Abrir o painel do DeepGWT"),
                                Opcao("haval_home", "Ir para a Home Haval"),
                                Opcao("app_launcher", "Ir para a lista de apps Haval"),
                                Opcao("none", "Nada (gesto desativado)")
                        ),
                        efeito = Efeito.BARRA, visivelSe = barraLigada, soNoComputador = true
                )
        )
    }

    private fun MutableList<Ajuste>.carro() {
        val s = SecaoDeAjustes.CARRO
        add(
                interruptor(
                        s, K.DISABLE_AVAS, "Desativar AVAS", false,
                        ajuda = "Sistema de alerta de veículo silencioso.",
                        efeito = Efeito.AVAS
                )
        )
        add(
                interruptor(
                        s, K.DISABLE_MONITORING, "Desativar monitoramento de distração e fadiga", false,
                        ajuda = "Impede a câmera interna (DMS) de monitorar distração e fadiga do motorista.",
                        efeito = Efeito.MONITORAMENTO
                )
        )
        add(
                interruptor(
                        s, K.ADAS_MEMORIA_ATIVA, "Manter assistentes de faixa e aviso de velocidade como deixei", false,
                        ajuda = "O carro religa os avisos de faixa e de velocidade a cada partida; o DeepGWT devolve o que você deixou. Frenagem automática fica de fora."
                )
        )
        add(
                interruptor(
                        s, K.CLOSE_WINDOW_ON_LOCK_VEHICLE, "Fechar vidros ao trancar", false,
                        ajuda = "Recurso nativo do carro — valide o comportamento no seu veículo.",
                        efeito = Efeito.VIDRO_AO_TRANCAR
                )
        )
        add(
                interruptor(
                        s, K.ENABLE_STEERING_WHEEL_MEDIA_CONTROL, "Controle de mídia pelo volante", true,
                        ajuda = "Próxima, anterior e play/pause do volante controlam a mídia (Android Auto)."
                )
        )
        add(
                interruptor(
                        s, K.DISABLE_HOTSPOT_ON_POWER_OFF, "Desativar conectividade ao desligar", false,
                        ajuda = "Desliga o hotspot Wi-Fi e o Bluetooth ao desligar o veículo, e religa os dois quando o carro liga de novo.",
                        espelhos = listOf(Espelho(K.DISABLE_BLUETOOTH_ON_POWER_OFF.chave, Formato.BOOLEANO) { it }),
                        // A tela do carro mostra ligado se QUALQUER uma das duas estiver ligada.
                        lerDe = { p ->
                            p.booleano(K.DISABLE_HOTSPOT_ON_POWER_OFF.chave, false) ||
                                    p.booleano(K.DISABLE_BLUETOOTH_ON_POWER_OFF.chave, false)
                        }
                )
        )
        add(
                interruptor(
                        s, K.SPORT_PLUS_ENABLED, "SPORT+", false,
                        ajuda = "Ao entrar no modo Sport, oferece ativar o motor a combustão forçado e desativar o ESP (tração/estabilidade)."
                )
        )
        add(
                interruptor(
                        s, K.DISABLE_AVM_CAR_STOPPED, "Desligar a câmera 360° com o carro parado", false,
                        ajuda = "Fecha a câmera 360° quando o carro para fora da ré."
                )
        )
        add(
                interruptor(
                        s, K.AVM_MANUAL_WHEN_STOPPED, "Permitir abrir pelo botão da câmera 360°", false,
                        ajuda = "Com o carro parado, a câmera abre e fica aberta se você apertar o botão da câmera 360°. " +
                                "Só vale para o botão: a que abre sozinha continua sendo fechada.",
                        visivelSe = ligado(K.DISABLE_AVM_CAR_STOPPED)
                )
        )
    }

    private fun MutableList<Ajuste>.gestos() {
        val s = SecaoDeAjustes.GESTOS
        add(
                interruptor(
                        s, K.HIDE_LEFT_NAV_PANE, "Ocultar barra lateral do carro", false,
                        ajuda = "Esconde a barra nativa (ventilação, home, apps) em todas as telas. Ela volta com um deslize da borda e se esconde de novo sozinha.",
                        efeito = Efeito.PAINEL_LATERAL
                )
        )
        add(
                interruptor(
                        s, K.QUICK_GESTURES_ENABLED, "Gestos rápidos com 3 dedos", false,
                        ajuda = "Para cima/baixo muda a temperatura; para os lados muda a ventilação.",
                        efeito = Efeito.GESTOS
                )
        )
        add(
                interruptor(
                        s, K.PROJECTION_GESTURE_ENABLED, "Toque duplo com 4 dedos projeta no painel", true,
                        ajuda = "Abre a escolha da área da central que aparece entre os mostradores. Repetir o gesto encerra.",
                        efeito = Efeito.GESTOS
                )
        )
        add(
                interruptor(
                        s, K.DISABLE_VOICE_WAKE_UP, "Desativar escuta e \"Olá Haval\"", false,
                        ajuda = "Nega o microfone aos pacotes de voz da Haval. Nada é desinstalado: desligue e o \"Olá Haval\" volta.",
                        efeito = Efeito.ESCUTA_DE_VOZ
                )
        )
        add(
                interruptor(
                        s, K.MACROS_ENABLED, "Macros armadas", true,
                        ajuda = "Com isto desligado, nenhuma macro gravada dispara. As macros continuam guardadas.",
                        efeito = Efeito.MACROS
                )
        )
    }

    private fun MutableList<Ajuste>.asd() {
        val s = SecaoDeAjustes.ASD
        val ligado = ligado(K.ASD_MASTER_ENABLED)
        add(
                interruptor(
                        s, K.ASD_MASTER_ENABLED, "Som do motor (ASD)", false,
                        ajuda = "Som de motor pelos alto-falantes, por cima do que o carro toca.",
                        efeito = Efeito.ASD
                )
        )
        add(
                opcoes(
                        s, K.ASD_SOUND_PROFILE, "Tipo de som", AsdPerfilDeSom.COMBUSTAO.name,
                        AsdPerfilDeSom.entries.map { Opcao(it.name, it.rotulo) },
                        efeito = Efeito.ASD, visivelSe = ligado
                )
        )
        add(
                opcoes(
                        s, K.ASD_ACTIVATION, "Quando tocar", AsdAtivacao.READY.name,
                        AsdAtivacao.entries.map { Opcao(it.name, it.rotulo) },
                        efeito = Efeito.ASD, visivelSe = ligado
                )
        )
        add(
                opcoes(
                        s, K.ASD_RPM_SOURCE, "Rotação do som", AsdFonteDeRotacao.SIMULADA.name,
                        AsdFonteDeRotacao.entries.map { Opcao(it.name, it.rotulo) },
                        efeito = Efeito.ASD, visivelSe = se(K.ASD_SOUND_PROFILE, AsdPerfilDeSom.COMBUSTAO.name)
                )
        )
        add(
                opcoes(
                        s, K.ASD_CADENCE, "Câmbio simulado", AsdCadenciaEscolhida.AUTOMATICA.name,
                        AsdCadenciaEscolhida.entries.map { Opcao(it.name, it.rotulo) },
                        efeito = Efeito.ASD, visivelSe = se(K.ASD_RPM_SOURCE, AsdFonteDeRotacao.SIMULADA.name)
                )
        )
        add(
                numero(
                        s, K.ASD_PITCH, "Tom do motor", 0, -20, 20, unidade = "%",
                        ajuda = "Negativo = mais grave; positivo = mais agudo.",
                        efeito = Efeito.ASD, visivelSe = ligado
                )
        )
        add(
                interruptor(
                        s, K.ASD_MUTE_IN_PARK, "Silenciar em P", false,
                        ajuda = "Sem som com a alavanca em P e o carro parado.",
                        efeito = Efeito.ASD, visivelSe = ligado
                )
        )
        add(
                opcoes(
                        s, K.ASD_VOLUME_MODE, "Volume", "FIXED",
                        listOf(Opcao("FIXED", "Volume fixo"), Opcao("RPM", "Volume por RPM")),
                        efeito = Efeito.ASD, visivelSe = ligado
                )
        )
        add(
                numero(
                        s, K.ASD_VOLUME, "Volume do ASD", 50, 0, 100, unidade = "%",
                        ajuda = "Independente do volume da mídia.",
                        efeito = Efeito.ASD, visivelSe = se(K.ASD_VOLUME_MODE, "FIXED")
                )
        )
        add(
                numero(
                        s, K.ASD_VOLUME_RPM_LOW, "Volume em RPM baixas", 30, 0, 100, unidade = "%",
                        efeito = Efeito.ASD, visivelSe = se(K.ASD_VOLUME_MODE, "RPM")
                )
        )
        add(
                numero(
                        s, K.ASD_VOLUME_RPM_HIGH, "Volume em RPM altas", 80, 0, 100, unidade = "%",
                        efeito = Efeito.ASD, visivelSe = se(K.ASD_VOLUME_MODE, "RPM")
                )
        )
        add(
                numero(
                        s, K.ASD_POSITION_X, "Posição: esquerda ↔ direita", 0, -100, 100,
                        efeito = Efeito.ASD, visivelSe = ligado, avancado = true
                )
        )
        add(
                numero(
                        s, K.ASD_POSITION_Y, "Posição: trás ↔ frente", 0, -100, 100,
                        efeito = Efeito.ASD, visivelSe = ligado, avancado = true
                )
        )
    }

    private fun MutableList<Ajuste>.audio() {
        val s = SecaoDeAjustes.AUDIO
        val faixaDoEq = Sinais.SYS_SETTINGS_AUDIO_EQ_VALUE_RANGE.chave
        fun banda(chave: Sinal, rotulo: String) =
                Ajuste(
                        id = chave.chave,
                        secao = s,
                        rotulo = rotulo,
                        tipo = TipoDeAjuste.NUMERO,
                        guardado = Guardado.Veiculo(chave.chave, booleano = false, chaveDaFaixa = faixaDoEq),
                        padrao = 0,
                        ajuda = "Equalizador do amplificador: vale para mídia, Android Auto, Bluetooth e telefone.",
                        min = -7.0,
                        max = 7.0,
                        passo = 1.0,
                        efeito = Efeito.ACUSTICA
                )
        add(banda(Sinais.SYS_SETTINGS_AUDIO_EQ_BASS_VALUE, "Grave"))
        add(banda(Sinais.SYS_SETTINGS_AUDIO_EQ_ALTO_VALUE, "Médio"))
        add(banda(Sinais.SYS_SETTINGS_AUDIO_EQ_TREBLE_VALUE, "Agudo"))
        add(
                Ajuste(
                        id = Sinais.SYS_SETTINGS_AUDIO_SOUND_FIELD_VALUE.chave,
                        secao = s,
                        rotulo = "Campo sonoro",
                        tipo = TipoDeAjuste.NUMERO,
                        guardado =
                                Guardado.Veiculo(
                                        Sinais.SYS_SETTINGS_AUDIO_SOUND_FIELD_VALUE.chave,
                                        booleano = false,
                                        chaveDaFaixa = Sinais.SYS_SETTINGS_AUDIO_SOUND_FIELD_VALUE_RANGE.chave
                                ),
                        padrao = 0,
                        min = 0.0,
                        max = 100.0,
                        passo = 1.0,
                        efeito = Efeito.ACUSTICA
                )
        )
        OemAudioDsp.Toggle.entries.forEach { t ->
            add(
                    Ajuste(
                            id = t.key.chave,
                            secao = s,
                            rotulo = t.label,
                            tipo = TipoDeAjuste.INTERRUPTOR,
                            guardado = Guardado.Veiculo(t.key.chave, booleano = true),
                            // Não há padrão do DeepGWT: quem decide é o amplificador. `null` faz o
                            // instalador não marcar "alterado" em nenhum dos dois estados.
                            padrao = null,
                            ajuda = t.description,
                            efeito = Efeito.ACUSTICA,
                            // Desligar o amplificador externo muda o som inteiro do carro.
                            avancado = t == OemAudioDsp.Toggle.EXTERNAL_AMP
                    )
            )
        }
        val reverb = ligado(K.AUDIO_REVERB_ENABLED)
        add(
                interruptor(
                        s, K.AUDIO_REVERB_ENABLED, "Concerto (reverb de software)", false,
                        ajuda = "Reverb aplicado por app que toca som. Não passa pelo amplificador.",
                        efeito = Efeito.AUDIO_SOFTWARE, avancado = true
                )
        )
        add(
                numero(
                        s, K.AUDIO_REVERB_ROOM_SIZE, "Tamanho da sala", 40, 0, 100, unidade = "%",
                        efeito = Efeito.AUDIO_SOFTWARE, visivelSe = reverb, avancado = true
                )
        )
        add(
                numero(
                        s, K.AUDIO_REVERB_WETNESS, "Intensidade do reverb", 30, 0, 100, unidade = "%",
                        efeito = Efeito.AUDIO_SOFTWARE, visivelSe = reverb, avancado = true
                )
        )
    }

    private fun MutableList<Ajuste>.painel() {
        val s = SecaoDeAjustes.PAINEL
        fun parte(parte: ParteDoPainel, rotulo: String, ajuda: String, aviso: String? = null) =
                Ajuste(
                        id = "painel.${parte.name.lowercase()}",
                        secao = s,
                        rotulo = rotulo,
                        tipo = TipoDeAjuste.INTERRUPTOR,
                        guardado = Guardado.Painel(parte),
                        padrao = false,
                        ajuda = ajuda,
                        efeito = Efeito.PAINEL,
                        aviso = aviso
                )
        add(
                parte(
                        ParteDoPainel.TEMA_CUSTOMIZADO, "Tema customizado",
                        "O DeepGWT desenha o painel inteiro com o tema escolhido no carro.",
                        // O mesmo aviso que a tela do carro mostra antes de ligar.
                        aviso =
                                "Este painel virtual é renderizado pela multimídia, ficando sujeito a gargalos de " +
                                        "processamento que podem causar discrepâncias ou atrasos entre as informações " +
                                        "reais e as mostradas. A velocidade informada pode ter uma pequena variação. " +
                                        "Confirme para aceitar os riscos e condições."
                )
        )
        add(
                Ajuste(
                        id = ID_DO_TEMA_DO_PAINEL,
                        secao = s,
                        rotulo = "Tema do painel",
                        tipo = TipoDeAjuste.OPCOES,
                        guardado = Guardado.TemaAtivo,
                        padrao = br.com.deepgwt.managers.TemaDoPainel.PASTA_PADRAO,
                        // A lista de verdade é montada na leitura, com os temas instalados agora
                        // (AjustesRemotos.comOpcoesDoMomento). Esta é a que vale sem carro: o tema
                        // padrão, que vem no APK. O embutido (Default) saiu da lista na 6.8.
                        opcoes = listOf(
                                Opcao(br.com.deepgwt.managers.TemaDoPainel.PASTA_PADRAO, "Stuttgart Clássico")
                        ),
                        ajuda = "Os temas instalados no carro. Um tema enviado pelo criador de temas aparece aqui.",
                        visivelSe = Condicao("painel.tema_customizado", setOf("true")),
                        efeito = Efeito.NENHUM
                )
        )
        add(
                parte(
                        ParteDoPainel.INFORMACOES, "Informações extras",
                        "Temperatura externa e placa de limite da via sobre o painel de fábrica. Com o tema customizado ligado fica guardado e volta quando ele for desligado."
                )
        )
        add(
                numero(
                        s, K.ORIGINAL_PANEL_TEMP_SCALE, "Tamanho da temperatura",
                        br.com.deepgwt.telas.OriginalTempScale.PADRAO,
                        br.com.deepgwt.telas.OriginalTempScale.MINIMO,
                        br.com.deepgwt.telas.OriginalTempScale.MAXIMO,
                        passo = 0.05, unidade = "×", formato = Formato.DECIMAL,
                        ajuda = "A temperatura externa das informações extras. 1× são 44 px, o padrão.",
                        efeito = Efeito.PAINEL_TEMPERATURA,
                        visivelSe = Condicao("painel.informacoes", setOf("true"))
                )
        )
        add(
                parte(
                        ParteDoPainel.CIRCULO, "Círculo do painel",
                        "O círculo da direita do painel de fábrica ganha páginas no volante: carro, projeção da central e HUD."
                )
        )
        add(
                interruptor(
                        s, K.ENABLE_ODOMETER_AND_REVISION, "Odômetro e aviso de revisão", true
                )
        )
        add(
                opcoes(
                        s, K.CLUSTER_FUEL_DISPLAY_UNIT, "Combustível mostrado em", "liters",
                        listOf(Opcao("liters", "Litros"), Opcao("percent", "Porcentagem"))
                )
        )
        add(
                interruptor(
                        s, K.CLUSTER_HIDE_SPEEDOMETER_ON_MAPS, "Ocultar velocímetro nos mapas", false,
                        ajuda = "Vale para os modos de mapa dos temas Sport."
                )
        )
        add(
                numero(
                        s, K.GAUGE_NUMBER_SCALE, "Tamanho do texto do painel", 1.0, 0.7, 1.4,
                        passo = 0.05, unidade = "×", formato = Formato.DECIMAL,
                        ajuda = "Escala sobre os tamanhos que o próprio tema define. Em 1× nada muda.",
                        efeito = Efeito.PAINEL_ESCALA
                )
        )
        add(
                interruptor(
                        s, K.NIGHT_MODE_ENABLED, "Modo noturno do painel", true,
                        ajuda = "Com o farol aceso, os letreiros do painel mudam de cor. Desligado, o painel mantém " +
                                "as cores do tema mesmo com o farol aceso.",
                        efeito = Efeito.PAINEL_FONTE_NOTURNA
                )
        )
        add(
                opcoes(
                        s, K.NIGHT_MODE_COLOR, "Cor do modo noturno", "amber",
                        listOf(Opcao("amber", "Âmbar"), Opcao("red", "Vermelho"), Opcao("mixed", "Meio-termo")),
                        ajuda = "A cor que o painel assume com o farol aceso.",
                        efeito = Efeito.PAINEL_COR_NOTURNA,
                        visivelSe = ligado(K.NIGHT_MODE_ENABLED)
                )
        )
        add(
                opcoes(
                        s, K.NIGHT_MODE_TRIGGER, "Quando acender o modo noturno", HeadlightPolicy.FONTE_PADRAO,
                        listOf(
                                        HeadlightPolicy.FONTE_AUTO,
                                        HeadlightPolicy.FONTE_LUZ_BAIXA,
                                        HeadlightPolicy.FONTE_POSICAO,
                                        HeadlightPolicy.FONTE_MESTRE,
                                        HeadlightPolicy.FONTE_HORARIO,
                                        HeadlightPolicy.FONTE_NUNCA
                                )
                                .map { Opcao(it, HeadlightPolicy.rotuloDaFonte(it)) },
                        ajuda = "Neste carro a chave do farol chega vazia; o automático usa a luz baixa/alta no lugar dela.",
                        efeito = Efeito.PAINEL_FONTE_NOTURNA, avancado = true,
                        visivelSe = ligado(K.NIGHT_MODE_ENABLED)
                )
        )
    }

    private fun MutableList<Ajuste>.hud() {
        val s = SecaoDeAjustes.HUD
        val ligado = ligado(K.HUD_OVERLAY_ENABLED)
        add(
                interruptor(
                        s, K.HUD_OVERLAY_ENABLED, "Head-up display", false,
                        ajuda = "Desenha a camada do DeepGWT no HUD do para-brisa.",
                        efeito = Efeito.HUD
                )
        )
        add(
                numero(
                        s, K.HUD_SPEED_SIZE_PCT, "Tamanho da velocidade", 30, 20, 80, unidade = "%",
                        efeito = Efeito.HUD, visivelSe = ligado
                )
        )
        add(cor(s, K.HUD_SPEED_COLOR, "Cor da velocidade", "#FFFFFF", Efeito.HUD, visivelSe = ligado))
        add(
                interruptor(
                        s, K.HUD_SHOW_SPEED_UNIT, "Mostrar \"km/h\"", true,
                        efeito = Efeito.HUD, visivelSe = ligado
                )
        )
        add(
                interruptor(
                        s, K.HUD_SHOW_SLI, "Limite da via (SLI)", true,
                        efeito = Efeito.HUD, visivelSe = ligado
                )
        )
        add(
                numero(
                        s, K.HUD_SLI_SIZE_PCT, "Tamanho da placa de limite", 28, 10, 45, unidade = "%",
                        efeito = Efeito.HUD, visivelSe = ligado(K.HUD_SHOW_SLI)
                )
        )
        add(
                opcoes(
                        s, K.HUD_SLI_SIDE, "Lado da placa", "direita",
                        listOf(Opcao("direita", "Direita"), Opcao("esquerda", "Esquerda")),
                        efeito = Efeito.HUD, visivelSe = ligado(K.HUD_SHOW_SLI)
                )
        )
        add(
                interruptor(
                        s, K.HUD_SHOW_ALERTS, "Alertas do veículo", true,
                        ajuda = "Portas, cinto, pressão dos pneus e outros avisos.",
                        efeito = Efeito.HUD, visivelSe = ligado
                )
        )
        add(
                interruptor(
                        s, K.HUD_WHEEL_CONTROL, "Controles pelo volante", true,
                        ajuda = "Segure OK no volante: o painel é coberto e ↑/↓ trocam as páginas do HUD. Segure OK de novo para devolver as setas ao painel.",
                        efeito = Efeito.HUD, visivelSe = ligado
                )
        )
        // Alpha 9.5: modo noturno do HUD.
        add(
                opcoes(
                        s, K.HUD_NIGHT_MODE, "Modo noturno", br.com.deepgwt.hud.HudModoNoturno.DESLIGADO.id,
                        br.com.deepgwt.hud.HudModoNoturno.entries.map { Opcao(it.id, it.rotulo) },
                        ajuda = "Escurece o HUD à noite. \"Com o farol\" segue a regra do modo noturno dos temas do painel.",
                        efeito = Efeito.HUD, visivelSe = ligado
                )
        )
        val noturno = se(K.HUD_NIGHT_MODE, "automatico", "sempre")
        add(
                numero(
                        s, K.HUD_NIGHT_BRIGHTNESS, "Brilho à noite", 55, 20, 90, unidade = "%",
                        efeito = Efeito.HUD, visivelSe = noturno
                )
        )
        add(
                interruptor(
                        s, K.HUD_NIGHT_AMBER, "Tons âmbar à noite", false,
                        efeito = Efeito.HUD, visivelSe = noturno
                )
        )
        add(
                interruptor(
                        s, K.HUD_SHOW_HEADER, "Nome da página no alto", true,
                        efeito = Efeito.HUD, visivelSe = ligado
                )
        )
        add(
                interruptor(
                        s, K.HUD_SHOW_DOTS, "Pontinhos de página", true,
                        efeito = Efeito.HUD, visivelSe = ligado
                )
        )
        add(
                numero(
                        s, K.HUD_FONT_WEIGHT, "Espessura da fonte", 700, 200, 900, passo = 100,
                        efeito = Efeito.HUD, visivelSe = ligado, avancado = true
                )
        )
        add(
                opcoes(
                        s, K.HUD_FONT_FAMILY, "Fonte", "padrao",
                        listOf(Opcao("padrao", "Padrão"), Opcao("condensada", "Condensada")),
                        efeito = Efeito.HUD, visivelSe = ligado, avancado = true
                )
        )
        add(
                cor(
                        s, K.HUD_LABEL_COLOR, "Cor dos rótulos", "#AAB4BE", Efeito.HUD,
                        visivelSe = ligado
                )
        )
    }

    private fun MutableList<Ajuste>.luz() {
        val s = SecaoDeAjustes.LUZ
        val ligada = ligado(K.AMBIENT_LIGHT_BLE_ENABLED)
        add(
                interruptor(
                        s, K.AMBIENT_LIGHT_BLE_ENABLED, "Luz ambiente", false,
                        ajuda = "A fita precisa ter sido pareada uma vez pela tela do carro (Recursos XTRA → Ambient Light BLE).",
                        efeito = Efeito.LUZ_AMBIENTE
                )
        )
        add(
                opcoes(
                        s, K.AMBIENT_LIGHT_OUTPUT, "Saída", SaidaDaLuz.PADRAO.valorGravado,
                        SaidaDaLuz.entries.map { Opcao(it.valorGravado, it.rotulo) },
                        efeito = Efeito.LUZ_AMBIENTE, visivelSe = ligada, avancado = true
                )
        )
        add(
                numero(
                        s, K.AMBIENT_LIGHT_BRIGHTNESS_PERCENT, "Brilho", 100, 0, 100, unidade = "%",
                        efeito = Efeito.LUZ_AMBIENTE, visivelSe = ligada
                )
        )
        add(
                numero(
                        s, K.AMBIENT_LIGHT_MANUAL_HUE, "Cor manual (matiz)", 0.0, 0, 360, unidade = "°",
                        formato = Formato.DECIMAL,
                        ajuda = "Usada quando a iluminação dinâmica está desligada. 0° vermelho, 120° verde, 240° azul.",
                        efeito = Efeito.LUZ_AMBIENTE, visivelSe = ligada
                )
        )
        add(
                interruptor(
                        s, K.AMBIENT_LIGHT_SYNC_DRIVE_MODE, "Iluminação dinâmica", false,
                        ajuda = "A luz reage ao carro. As opções abaixo dizem a quê.",
                        efeito = Efeito.LUZ_AMBIENTE, visivelSe = ligada
                )
        )
        // Alpha 9.5: o que a iluminação dinâmica segue. A seta vem desligada.
        val dinamica = ligado(K.AMBIENT_LIGHT_SYNC_DRIVE_MODE)
        br.com.deepgwt.luz.ReacaoDaLuz.entries.forEach { r ->
            add(
                    interruptor(
                            s, r.chave, "Reage a: ${r.titulo.replaceFirstChar { it.lowercase() }}", r.padrao,
                            ajuda = r.descricao,
                            efeito = Efeito.LUZ_AMBIENTE, visivelSe = dinamica
                    )
            )
        }
        add(
                interruptor(
                        s, K.AMBIENT_LIGHT_ANIMATIONS_ENABLED, "Animações", false,
                        efeito = Efeito.LUZ_AMBIENTE, visivelSe = ligada
                )
        )
        add(
                interruptor(
                        s, K.AMBIENT_LIGHT_MUSIC_ANIMATION_ENABLED, "Animação com a música", false,
                        efeito = Efeito.LUZ_AMBIENTE, visivelSe = ligada
                )
        )
        add(
                opcoes(
                        s, K.AMBIENT_LIGHT_MUSIC_MODE, "Modo da animação com música", ModoMusical.PADRAO.valorGravado,
                        ModoMusical.entries.map { Opcao(it.valorGravado, it.rotulo) },
                        efeito = Efeito.LUZ_AMBIENTE, visivelSe = ligado(K.AMBIENT_LIGHT_MUSIC_ANIMATION_ENABLED)
                )
        )
        add(
                interruptor(
                        s, K.AMBIENT_LIGHT_AUTO_RECONNECT, "Reconectar sozinha", true,
                        efeito = Efeito.LUZ_AMBIENTE, visivelSe = ligada, avancado = true
                )
        )
    }

    private fun MutableList<Ajuste>.brilho() {
        val s = SecaoDeAjustes.BRILHO
        val ligado = ligado(K.ENABLE_AUTO_BRIGHTNESS)
        val porHorario = se(K.AUTO_BRIGHTNESS_USE_SUN, "false")
        add(
                interruptor(
                        s, K.ENABLE_AUTO_BRIGHTNESS, "Brilho automático", false,
                        efeito = Efeito.BRILHO, soNoComputador = true
                )
        )
        add(
                interruptor(
                        s, K.AUTO_BRIGHTNESS_USE_SUN, "Seguir o nascer e o pôr do sol", false,
                        ajuda = "Transição suave pelo GPS. Sem localização, volta para o horário fixo.",
                        efeito = Efeito.BRILHO, visivelSe = ligado, soNoComputador = true
                )
        )
        add(
                numero(
                        s, K.AUTO_BRIGHTNESS_LEVEL_DAY, "Brilho de dia", 10, 1, 10,
                        efeito = Efeito.BRILHO, visivelSe = ligado, soNoComputador = true
                )
        )
        add(
                numero(
                        s, K.AUTO_BRIGHTNESS_LEVEL_NIGHT, "Brilho à noite", 1, 1, 10,
                        efeito = Efeito.BRILHO, visivelSe = ligado, soNoComputador = true
                )
        )
        add(
                horario(
                        s, K.NIGHT_START_HOUR, K.NIGHT_START_MINUTE, "A noite começa às", 20, 0,
                        efeito = Efeito.BRILHO, visivelSe = porHorario, soNoComputador = true
                )
        )
        add(
                horario(
                        s, K.NIGHT_END_HOUR, K.NIGHT_END_MINUTE, "A noite termina às", 6, 0,
                        efeito = Efeito.BRILHO, visivelSe = porHorario, soNoComputador = true
                )
        )
        add(
                numero(
                        s, K.AUTO_BRIGHTNESS_STEP_MIN, "Transição: minutos por nível", 5, 1, 30, unidade = "min",
                        efeito = Efeito.BRILHO, visivelSe = se(K.AUTO_BRIGHTNESS_USE_SUN, "true"),
                        avancado = true, soNoComputador = true
                )
        )
    }

    private fun MutableList<Ajuste>.automacoes() {
        val s = SecaoDeAjustes.AUTOMACOES
        fun liga(chave: ChaveDePreferencia, rotulo: String, ajuda: String? = null, efeito: Efeito = Efeito.NENHUM) =
                interruptor(s, chave, rotulo, false, ajuda = ajuda, efeito = efeito, soNoComputador = true)
        add(liga(K.CLOSE_WINDOW_ON_POWER_OFF, "Fechar vidros ao desligar o carro"))
        add(liga(K.CLOSE_WINDOW_ON_FOLD_MIRROR, "Fechar vidros ao recolher os retrovisores"))
        add(liga(K.CLOSE_SUNROOF_ON_POWER_OFF, "Fechar teto solar ao desligar o carro"))
        add(liga(K.CLOSE_SUNROOF_ON_FOLD_MIRROR, "Fechar teto solar ao recolher os retrovisores"))
        add(liga(K.CLOSE_SUNROOF_SUN_SHADE_ON_CLOSE_SUN_ROOF, "Fechar a cortina junto com o teto solar"))
        add(liga(K.CLOSE_WINDOWS_ON_SPEED, "Fechar vidros ao passar de uma velocidade"))
        add(
                numero(
                        s, K.SPEED_THRESHOLD, "Velocidade para fechar os vidros", 15, 10, 120, unidade = "km/h",
                        formato = Formato.DECIMAL, visivelSe = ligado(K.CLOSE_WINDOWS_ON_SPEED), soNoComputador = true
                )
        )
        add(liga(K.CLOSE_SUNROOF_ON_SPEED, "Fechar teto solar ao passar de uma velocidade"))
        add(
                numero(
                        s, K.SUNROOF_SPEED_THRESHOLD, "Velocidade para fechar o teto", 15, 10, 120, unidade = "km/h",
                        formato = Formato.DECIMAL, visivelSe = ligado(K.CLOSE_SUNROOF_ON_SPEED), soNoComputador = true
                )
        )
        add(liga(K.SET_STARTUP_VOLUME, "Volume fixo ao ligar o carro", "Vale a partir da próxima partida."))
        add(
                numero(
                        s, K.STARTUP_VOLUME, "Volume ao ligar", 1, 0, 40,
                        visivelSe = ligado(K.SET_STARTUP_VOLUME), soNoComputador = true
                )
        )
        add(liga(K.ENABLE_MAX_AC_ON_UNLOCK, "A/C no máximo ao ligar com a cabine quente"))
        add(
                numero(
                        s, K.MAX_AC_ON_UNLOCK_THRESHOLD, "Temperatura de disparo", 35, 20, 38, unidade = "°C",
                        formato = Formato.DECIMAL, visivelSe = ligado(K.ENABLE_MAX_AC_ON_UNLOCK), soNoComputador = true
                )
        )
        add(
                numero(
                        s, K.MAX_AC_TARGET_TEMP, "Temperatura alvo", 28, 18, 34, unidade = "°C",
                        formato = Formato.DECIMAL, visivelSe = ligado(K.ENABLE_MAX_AC_ON_UNLOCK), soNoComputador = true
                )
        )
        add(
                opcoes(
                        s, K.MAX_AC_TIMEOUT, "Desligar o máximo depois de", "0",
                        listOf(Opcao("0", "Sem limite"), Opcao("1", "1 minuto"), Opcao("3", "3 minutos"), Opcao("5", "5 minutos")),
                        formato = Formato.INTEIRO, visivelSe = ligado(K.ENABLE_MAX_AC_ON_UNLOCK), soNoComputador = true
                )
        )
        add(liga(K.ENABLE_SEAT_VENTILATION_ON_AC_ON, "Ventilar o banco do motorista ao ligar o A/C"))
        add(liga(K.ENABLE_OPEN_SUNROOF_CURTAIN_ON_START, "Abrir a cortina do teto ao ligar o carro"))
        val cortina = ligado(K.ENABLE_OPEN_SUNROOF_CURTAIN_ON_START)
        add(
                horario(
                        s, K.OPEN_SUNROOF_CURTAIN_START_HOUR, K.OPEN_SUNROOF_CURTAIN_START_MINUTE,
                        "Cortina: abrir a partir de", 18, 0, visivelSe = cortina, soNoComputador = true
                )
        )
        add(
                horario(
                        s, K.OPEN_SUNROOF_CURTAIN_END_HOUR, K.OPEN_SUNROOF_CURTAIN_END_MINUTE,
                        "Cortina: abrir até", 9, 0,
                        ajuda = "A janela pode passar da meia-noite (18:00 até 09:00).",
                        visivelSe = cortina, soNoComputador = true
                )
        )
        add(
                numero(
                        s, K.OPEN_SUNROOF_CURTAIN_MAX_TEMP, "Cortina: temperatura externa máxima", -1, -1, 45, unidade = "°C",
                        formato = Formato.DECIMAL, ajuda = "-1 desliga a checagem de temperatura: só o horário manda.",
                        visivelSe = cortina, soNoComputador = true
                )
        )
        add(
                liga(
                        K.ENABLE_PERSIST_HEV_SOC_TARGET, "Manter a bateria no HEV Prioritário",
                        efeito = Efeito.HEV
                )
        )
        add(
                numero(
                        s, K.HEV_SOC_TARGET_VALUE, "Bateria a manter", 50, 20, 80, passo = 5, unidade = "%",
                        efeito = Efeito.HEV, visivelSe = ligado(K.ENABLE_PERSIST_HEV_SOC_TARGET), soNoComputador = true
                )
        )
        add(liga(K.DISABLE_BLUETOOTH_ON_FOLD_MIRROR, "Desligar o Bluetooth ao recolher os retrovisores"))
        add(liga(K.DISABLE_HOTSPOT_ON_FOLD_MIRROR, "Desligar o hotspot ao recolher os retrovisores"))
    }

    private fun MutableList<Ajuste>.sistema() {
        val s = SecaoDeAjustes.SISTEMA
        add(
                interruptor(
                        s, K.DISABLE_NATIVE_NAVIGATION, "Desativar o navegador GPS nativo (Neusoft)", false,
                        efeito = Efeito.APPS_DO_SISTEMA, avancado = true
                )
        )
        add(
                interruptor(
                        s, K.DISABLE_NATIVE_VOICE, "Desativar o assistente de voz nativo (iFlyTek)", false,
                        efeito = Efeito.APPS_DO_SISTEMA, avancado = true
                )
        )
        add(
                interruptor(
                        s, K.DISABLE_NATIVE_WEATHER, "Desativar a previsão do tempo (OEM)", false,
                        efeito = Efeito.APPS_DO_SISTEMA, avancado = true
                )
        )
        val indicador = ligado(K.ENABLE_RESOURCE_OVERLAY)
        add(
                interruptor(
                        s, K.ENABLE_RESOURCE_OVERLAY, "Indicador de CPU e RAM", false,
                        ajuda = "Quadradinho por cima de qualquer app. Enquanto ligado, o app faz uma leitura a cada 2,5 s.",
                        efeito = Efeito.BARRA, avancado = true
                )
        )
        add(
                opcoes(
                        s, K.RESOURCE_OVERLAY_CORNER, "Canto do indicador", "3",
                        listOf(
                                Opcao("0", "Superior esquerdo"),
                                Opcao("1", "Superior direito"),
                                Opcao("2", "Inferior esquerdo"),
                                Opcao("3", "Inferior direito")
                        ),
                        formato = Formato.INTEIRO, efeito = Efeito.BARRA, visivelSe = indicador, avancado = true
                )
        )
        add(
                numero(
                        s, K.RESOURCE_OVERLAY_FONT_SP, "Tamanho do texto do indicador", 14, 10, 30, unidade = "sp",
                        efeito = Efeito.BARRA, visivelSe = indicador, avancado = true
                )
        )
        add(
                numero(
                        s, K.RESOURCE_OVERLAY_X, "Distância horizontal do canto", 12, 0, 400, unidade = "dp",
                        efeito = Efeito.BARRA, visivelSe = indicador, avancado = true
                )
        )
        add(
                numero(
                        s, K.RESOURCE_OVERLAY_Y, "Distância vertical do canto", 90, 0, 300, unidade = "dp",
                        efeito = Efeito.BARRA, visivelSe = indicador, avancado = true
                )
        )
    }

    private fun MutableList<Ajuste>.interfaceDoApp() {
        add(
                opcoes(
                        SecaoDeAjustes.INTERFACE, K.INTERFACE_STYLE, "Estilo", EstiloDaInterface.GRADE.id,
                        EstiloDaInterface.entries.map { Opcao(it.id, it.nome) },
                        // Sem Efeito: o app escuta a própria chave (InterfaceDoDeepGwt) e redesenha
                        // na hora, venha a troca da tela do carro, do instalador ou de um perfil.
                        ajuda = "Grade: blocos com ícone, o visual de sempre. Bávaro: menu em lista com " +
                                "barra vermelha e medalhão. Estugarda: carrossel de três itens sobre azul " +
                                "profundo. Cupertino: fundo grafite sóbrio, ícones escuros arredondados e Dock de vidro. " +
                                "Muda só as telas do app, não a barra nem o painel."
                )
        )
        // Alpha 7.9. Sem Efeito: o PrincipalActivity escuta as duas chaves e se recria no tamanho novo.
        add(
                numero(
                        SecaoDeAjustes.INTERFACE, K.INTERFACE_ESCALA, "Tamanho da interface",
                        EscalaDaInterface.PADRAO, EscalaDaInterface.MINIMO, EscalaDaInterface.MAXIMO_DA_INTERFACE,
                        passo = 5, unidade = "%",
                        ajuda = "O tamanho de botões, cartões e ícones nas telas do app. No carro: 90 (Compacto), " +
                                "100 (Padrão), 115 (Grande) e 125 (Extra). Não muda a barra, o painel nem o HUD."
                )
        )
        add(
                numero(
                        SecaoDeAjustes.INTERFACE, K.INTERFACE_ESCALA_TEXTO, "Tamanho das letras",
                        EscalaDaInterface.PADRAO, EscalaDaInterface.MINIMO, EscalaDaInterface.MAXIMO_DAS_LETRAS,
                        passo = 5, unidade = "%",
                        ajuda = "Só os textos das telas do app. No carro: 90, 100, 115 e 130."
                )
        )
    }

    private fun MutableList<Ajuste>.launcher() {
        add(
                interruptor(
                        SecaoDeAjustes.LAUNCHER, K.HOME_LAUNCHER3D_FIXO,
                        "Launcher 3D como tela inicial", false,
                        ajuda = "Ligado, o carro abre no launcher 3D e o DeepGWT o devolve quando a tela de fábrica " +
                                "abrir como início. Desligado, volta a tela de fábrica.",
                        efeito = Efeito.TELA_INICIAL
                )
        )
        add(
                interruptor(
                        SecaoDeAjustes.LAUNCHER, K.VIEWER_COMMANDS_ENABLED,
                        "Launcher 3D abre e fecha vidros, teto e cortina", true,
                        ajuda = "Com isto desligado, os botões do carro 3D só mostram o estado."
                )
        )
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
