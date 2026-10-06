// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.perfis

import br.com.deepgwt.veiculo.Sinais
/**
 * O que entra num perfil de motorista — e, principalmente, o que NÃO entra.
 *
 * A REGRA É AO CONTRÁRIO DO ÓBVIO
 *
 * Não existe lista do que o perfil guarda. Existe lista do que ele IGNORA, e tudo o mais entra
 * sozinho. É o único jeito de a promessa "toda configuração nova é incorporada automaticamente"
 * ser verdade sem alguém lembrar de vir aqui: uma preferência criada amanhã, em qualquer tela do
 * DeepGWT, cai no `deepgwt_prefs` como todas as outras e passa por esta função com resposta `true`.
 * Uma lista de inclusão faria o contrário — cada recurso novo nasceria fora dos perfis e ninguém
 * notaria, porque nada quebra: o valor simplesmente não viaja.
 *
 * O preço dessa escolha é que o que fica de fora precisa ser justificado um a um. São quatro
 * motivos, e nenhum deles é "achei melhor não":
 *
 *   1. É a máquina dos próprios perfis (guardar o perfil dentro do perfil).
 *   2. É do CARRO ou da INSTALAÇÃO, não de quem dirige — id de tela, MAC de aparelho pareado,
 *      versão de bootstrap, histórico de revisão. Restaurar isso num perfil de outro motorista
 *      não muda a experiência dele: corrompe o estado do aparelho.
 *   3. É estado passageiro, não preferência — última cidade lida do GPS, bytes acumulados no
 *      mês, nonce de recarga do tema. Gravar não faz mal; RESTAURAR faz, porque planta um valor
 *      velho em cima do atual.
 *   4. Vira o carro de um jeito que ninguém pediu ao trocar de perfil. Só dois casos, e os dois
 *      estão comentados abaixo com o motivo.
 *
 * Tudo isto é `String`, não `Preferencia`, de propósito: metade das chaves do app é
 * dinâmica (`theme_config_<tema>_<variável>`, `streamdeckKeyAction_<tecla>`) e não existe no
 * enum. O snapshot é feito por `prefs.all`, que enxerga as duas famílias igual.
 */
object EscopoDoPerfil {

    // ── 1. A máquina dos próprios perfis ────────────────────────────────────────────────────
    private const val PREFIXO_PERFIL = "driverProfile_"

    // ── 2. Do carro / da instalação ─────────────────────────────────────────────────────────
    private val PREFIXOS_DO_APARELHO = listOf(
        // Qual carroceria este carro é, assinada pelo que o barramento respondeu. Ver VehicleIdentity.
        "vehicleIdentityChoice_",
        // Endereço e nome de fita de LED pareada por Bluetooth: é o hardware instalado NESTE carro.
        "ambientLightDoorDeviceMac_",
        "ambientLightDoorDeviceName_"
    )

    private val CHAVES_DO_APARELHO = setOf(
        "driverProfileNames",
        "activeDriverProfile",
        "autoRecallSeatMemory",
        "aplicarPerfilAoLigar",
        "vehicleIdentityLastSignature",
        "ambientLightBleDeviceMac",
        "ambientLightBleDeviceName",
        // Ids de tela descobertos nesta central. Trocar de motorista não muda onde o painel está.
        "instrumentMaskDisplayId",
        "hudOverlayDisplayId",
        // Estado da instalação, do instalador e do aceite legal — não pertence a ninguém em
        // particular e restaurar um valor antigo aqui é como reverter uma atualização pela metade.
        "permissionsBootstrapVersion",
        "setupPresetChoice",
        "selfInstallationIntegrityCheck",
        "bypassSelfInstallationIntegrityCheck",
        "androidSettingsShortcutSeeded",
        "pendingResetTargetVersion",
        "updateInstallInProgress",
        "notificationListenersBackup",
        "adasDisclaimerAcknowledged",
        // Primeira abertura (Alpha 7.9): o assistente e as novidades já vistos NESTA central.
        // Trocar de motorista não pode reabrir o assistente nem esconder novidades de ninguém.
        "assistenteConcluidoVersao",
        "tourConcluido",
        "novidadesVistasAte",
        // Folga de áudio que o ASD mediu NESTA central (underruns do hardware). Restaurar o valor
        // de outro momento só faria o som picotar ou atrasar à toa.
        "asdLearnedBufferFrames",
        // Onde o DVR do carro grava e a pasta que o usuário autorizou pelo seletor do Android.
        // A segunda é uma PERMISSÃO concedida, não um texto: restaurar uma URI velha devolve
        // um acesso que o Android já revogou, e o app passa a falhar ao salvar sem dizer por quê.
        "dvrStoragePath",
        "dvrSaveLocationUri",
        // Quilometragem e histórico de revisão são do carro. Dois motoristas dividem o mesmo óleo.
        "instrumentRevisionKm",
        "instrumentRevisionNextDate",
        "instrumentRevisionHistory",
        "serviceHistory",
        // Qual tela inicial o carro abre é do aparelho (e trocar de perfil não pode trocar o
        // launcher por baixo do motorista). O recuo medido por app é do app instalado NESTA central.
        "homeLauncher3dFixo",
        "bottomBarAutoOverscan",
        // Alpha 8.5: o que o app guardou do CARRO para devolver os coringas, e as teclas que ESTE
        // volante entrega. Trocar de perfil não pode apagar a função de fábrica guardada.
        "coringa1Tomado",
        "coringa2Tomado",
        "coringa1Original",
        "coringa2Original",
        "coringa1Tecla",
        "coringa2Tecla"
    )

    // ── 3. Estado passageiro ────────────────────────────────────────────────────────────────
    private val PREFIXOS_DE_ESTADO = listOf("mobileDataTraffic")

    private val CHAVES_DE_ESTADO = setOf(
        // Cache do GPS para a conta de nascer/pôr do sol. Refeito sozinho na primeira leitura.
        "autoBrightnessLastLat",
        "autoBrightnessLastLon",
        "autoBrightnessCity",
        // "Eu desliguei isto e preciso religar depois" — anotação do próprio app, em andamento.
        "mobileDataDisabledByApp",
        "datatrackDisabledByApp",
        "bluetoothStateOnPowerOff",
        "hotspotStateOnPowerOff",
        // Carimbo de tempo que força o painel a recarregar. É emitido na hora da aplicação
        // (ver DriverProfileManager.aplicar) — restaurar um carimbo velho seria pedir para o
        // painel recarregar num instante do passado, que é o mesmo que não pedir nada.
        "themeReloadNonce",
        // O tema de antes do "Ver no carro" do criador de temas. Só vale durante aquela sessão de
        // edição; num perfil, restauraria uma volta para um rascunho que já não existe.
        "temaAntesDoRascunho",
        // A calibração do painel (Alpha 8.8): tema de antes, início e pasta da sessão em andamento.
        "temaAntesDaCalibracao",
        "calibracaoDesde",
        "calibracaoSessao",
        // Pontuação acumulada de condução: pertence à viagem, não ao motorista.
        "tripConsistencyClusterScore",
        // Onde o cursor do menu do volante parou na última vez.
        "lastClusterScreen",
        "lastClusterMenuItem",
        "conectorModoParaVoltar",
        "conectorRegistro"
    )

    // ── 4. Viraria o carro sem ninguém pedir ────────────────────────────────────────────────
    private val CHAVES_PERIGOSAS = setOf(
        // O modo sorrateiro tira o ícone do DeepGWT do carro e a ÚNICA volta é pelo instalador
        // no computador. Um perfil salvo com ele ligado esconderia o app de quem trocasse de
        // perfil sem fazer ideia do que aconteceu — e sem caminho de volta dentro do carro.
        "stealthModeActive"
    )

    /**
     * Esta preferência viaja no perfil?
     *
     * Responde `true` para tudo o que não está listado — inclusive para chaves que ainda não
     * existem. É o comportamento desejado; ver o cabeçalho.
     */
    fun preferenciaPertence(chave: String): Boolean {
        if (chave.isBlank()) return false
        if (chave.startsWith(PREFIXO_PERFIL)) return false
        if (PREFIXOS_DO_APARELHO.any { chave.startsWith(it) }) return false
        if (PREFIXOS_DE_ESTADO.any { chave.startsWith(it) }) return false
        if (chave in CHAVES_DO_APARELHO) return false
        if (chave in CHAVES_DE_ESTADO) return false
        if (chave in CHAVES_PERIGOSAS) return false
        return true
    }

    // ══════════════════════════════════════════════════════════════════════════════════════
    // Configurações do próprio veículo
    // ══════════════════════════════════════════════════════════════════════════════════════
    //
    // Aqui a regra é de INCLUSÃO, e por um motivo diferente: as 818 chaves do barramento não são
    // configurações. A esmagadora maioria é leitura — velocidade, rotação, temperatura do
    // líquido de arrefecimento, estado das portas. Escrever nelas não guarda preferência nenhuma;
    // na melhor das hipóteses é recusado, e na pior manda um comando.
    //
    // O que distingue configuração de leitura, neste barramento, é o nome: a GWM agrupa tudo o
    // que o motorista escolhe sob um segmento `*_setting`/`settings` (car.comfort_setting.*,
    // car.drive.setting.*, sys.settings.audio.*) ou com sufixo `_config`. Isso é convenção do
    // fornecedor, não garantia — por isso a aplicação confere o valor atual antes de escrever e
    // conta as recusas em vez de fingir que deu certo.

    private val PREFIXOS_DE_CONFIGURACAO = listOf("sys.settings.")

    private val PEDACOS_DE_CONFIGURACAO = listOf("_setting.", ".setting.", "_settings.", ".settings.")

    private val SUFIXOS_DE_CONFIGURACAO = listOf("_config")

    /**
     * Sufixos que, no vocabulário deste barramento, marcam um COMANDO e não uma preferência.
     *
     * `_action` é o caso literal: `car.genernal.setting.factory_reset_action` está dentro do
     * grupo "setting" e restaura o carro de fábrica. `_feedback` é a resposta do módulo à ação.
     * `_range` e `_support` descrevem o que o hardware aceita (faixa do volume, se o AVM existe)
     * — escrever ali não muda escolha nenhuma.
     */
    private val SUFIXOS_DE_COMANDO = listOf("_action", "_feedback", "_range", "_support")

    private val PEDACOS_PROIBIDOS = listOf("factory_reset", "reset_all", "mute_state")

    /**
     * Chaves que passam pela regra de nome mas MEXEM em peça física.
     *
     * Um perfil devolve preferências; ele não dobra retrovisor nem move banco sozinho enquanto
     * alguém pode estar com a mão ali. A posição do banco tem caminho próprio e seguro — a
     * memória de banco do próprio carro (ver seatMemorySlot), que o carro só executa quando
     * julga seguro.
     *
     * Escritas pelo enum e não por texto de propósito. O dicionário deste carro mistura
     * `car.drive_setting.*` e `car.drive.setting.*` na mesma área, e este projeto já perdeu uma
     * chave inteira por transcrever o NOME do enum em vez do VALOR dele (`wade_mode_enable`, que
     * ficou meses sem ser lida nem gravada, falhando em silêncio). Pelo enum, um erro de grafia é
     * erro de compilação; por texto, é uma trava de segurança que simplesmente não pega.
     */
    private val CHAVES_QUE_ATUAM: Set<String> = setOf(
        Sinais.CAR_DRIVE_SETTING_OUTSIDE_VIEW_MIRROR_FOLD_STATE,
        Sinais.CAR_DRIVE_SETTING_TRAILER_HOOK_STATE,
        Sinais.CAR_COMFORT_SETTING_DRIVER_SEAT_SUPPORT_STATE,
        Sinais.CAR_COMFORT_SETTING_PASSENGER_SEAT_SUPPORT_STATE,
        Sinais.CAR_COMFORT_SETTING_DRIVE_SEAT_DIRECTION_CONFIG,
        Sinais.CAR_COMFORT_SETTING_SEAT_WAIST_DIRECTION_CONFIG,
        // Código de configuração de fábrica deste chassi. Identidade do carro, escrita pela GWM.
        Sinais.CAR_BASIC_CONFIGURE_CODE
    ).map { it.chave }.toSet()

    /** Grupos que são configuração mas não trazem `setting` no nome. */
    private val EXTRAS = listOf("sys.avm." to "_enable")

    fun chaveDoVeiculoPertence(chave: String): Boolean {
        if (chave.isBlank()) return false
        if (chave in CHAVES_QUE_ATUAM) return false
        if (SUFIXOS_DE_COMANDO.any { chave.endsWith(it) }) return false
        if (PEDACOS_PROIBIDOS.any { chave.contains(it) }) return false

        if (PREFIXOS_DE_CONFIGURACAO.any { chave.startsWith(it) }) return true
        if (PEDACOS_DE_CONFIGURACAO.any { chave.contains(it) }) return true
        if (SUFIXOS_DE_CONFIGURACAO.any { chave.endsWith(it) }) return true
        if (EXTRAS.any { (prefixo, sufixo) -> chave.startsWith(prefixo) && chave.endsWith(sufixo) }) return true
        return false
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
