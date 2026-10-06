// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.perfis

import br.com.deepgwt.nucleo.Preferencias
import br.com.deepgwt.veiculo.Sinais
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A fronteira do perfil.
 *
 * O teste mais importante deste arquivo é o primeiro: uma chave inventada agora, que não existe
 * em lugar nenhum do app, TEM de entrar no perfil. É essa propriedade que sustenta a promessa de
 * "toda configuração nova é incorporada automaticamente" — e é ela que uma refatoração
 * distraída quebraria primeiro, trocando a lista de exclusão por uma de inclusão sem que nada
 * mais falhasse.
 */
class EscopoDoPerfilTest {

    // ── DeepGWT ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `chave que ninguem viu antes entra no perfil`() {
        assertTrue(EscopoDoPerfil.preferenciaPertence("umAjusteQueAindaNaoExiste"))
        assertTrue(EscopoDoPerfil.preferenciaPertence("recursoDe2027Habilitado"))
        // Chaves dinâmicas (tema, stream deck) também: elas nem estão no enum.
        assertTrue(EscopoDoPerfil.preferenciaPertence("theme_config_minimalist_accentColor"))
        assertTrue(EscopoDoPerfil.preferenciaPertence("streamdeckKeyAction_7"))
    }

    @Test
    fun `as preferencias que o pedido cita nome a nome estao dentro`() {
        val citadas = listOf(
            Preferencias.ACTIVE_CUSTOM_THEME,       // tema do cluster
            Preferencias.VIRTUAL_CLUSTER_THEME,     // painel
            Preferencias.HUD_OVERLAY_ENABLED,       // head-up display
            Preferencias.HUD_SPEED_SIZE_PCT,        // layout do HUD
            Preferencias.ASD_MASTER_ENABLED,        // ASD
            Preferencias.ASD_VOLUME,
            Preferencias.BOTTOM_BAR_HEIGHT_DP,      // barra inferior
            Preferencias.BOTTOM_BAR_SWIPE_UP_ACTION,
            Preferencias.VIEWER_COMMANDS_ENABLED,   // launcher 3D
            Preferencias.HIDE_LEFT_NAV_PANE,        // navegação
            Preferencias.QUICK_GESTURES_ENABLED,    // atalhos
            Preferencias.VOLUME_OVERLAY_LOGO_TYPE,  // mídia
            Preferencias.CLUSTER_FUEL_DISPLAY_UNIT  // visualização
        )
        for (chave in citadas) {
            assertTrue(chave.chave, EscopoDoPerfil.preferenciaPertence(chave.chave))
        }
    }

    @Test
    fun `o perfil nao guarda o proprio sistema de perfis`() {
        assertFalse(EscopoDoPerfil.preferenciaPertence("driverProfile_Miguel"))
        assertFalse(EscopoDoPerfil.preferenciaPertence("driverProfileNames"))
        assertFalse(EscopoDoPerfil.preferenciaPertence(Preferencias.ACTIVE_DRIVER_PROFILE.chave))
        assertFalse(EscopoDoPerfil.preferenciaPertence(Preferencias.APLICAR_PERFIL_AO_LIGAR.chave))
        assertFalse(EscopoDoPerfil.preferenciaPertence(Preferencias.AUTO_RECALL_SEAT_MEMORY.chave))
    }

    @Test
    fun `o que e do carro ou da instalacao fica de fora`() {
        assertFalse(EscopoDoPerfil.preferenciaPertence("vehicleIdentityChoice_h6|phev"))
        assertFalse(EscopoDoPerfil.preferenciaPertence("vehicleIdentityLastSignature"))
        assertFalse(EscopoDoPerfil.preferenciaPertence("ambientLightBleDeviceMac"))
        assertFalse(EscopoDoPerfil.preferenciaPertence("ambientLightDoorDeviceMac_2"))
        assertFalse(EscopoDoPerfil.preferenciaPertence("instrumentMaskDisplayId"))
        assertFalse(EscopoDoPerfil.preferenciaPertence("hudOverlayDisplayId"))
        assertFalse(EscopoDoPerfil.preferenciaPertence("permissionsBootstrapVersion"))
        assertFalse(EscopoDoPerfil.preferenciaPertence("adasDisclaimerAcknowledged"))
        assertFalse(EscopoDoPerfil.preferenciaPertence("dvrSaveLocationUri"))
        assertFalse(EscopoDoPerfil.preferenciaPertence("instrumentRevisionKm"))
    }

    @Test
    fun `estado passageiro nao e preferencia`() {
        assertFalse(EscopoDoPerfil.preferenciaPertence("autoBrightnessLastLat"))
        assertFalse(EscopoDoPerfil.preferenciaPertence("autoBrightnessCity"))
        assertFalse(EscopoDoPerfil.preferenciaPertence("mobileDataTrafficAccumBytes"))
        assertFalse(EscopoDoPerfil.preferenciaPertence("bluetoothStateOnPowerOff"))
        assertFalse(EscopoDoPerfil.preferenciaPertence("themeReloadNonce"))
        assertFalse(EscopoDoPerfil.preferenciaPertence("tripConsistencyClusterScore"))
        // Mas a preferência de MOSTRAR a pontuação continua no perfil.
        assertTrue(EscopoDoPerfil.preferenciaPertence("tripConsistencyClusterActive"))
    }

    @Test
    fun `o modo sorrateiro nunca viaja num perfil`() {
        // Só volta pelo instalador no computador: um perfil que o ligasse esconderia o app de
        // quem trocou de perfil, sem caminho de volta dentro do carro.
        assertFalse(EscopoDoPerfil.preferenciaPertence("stealthModeActive"))
    }

    @Test
    fun `ficar fora do escopo tambem protege do apagamento`() {
        // Aplicar um perfil REMOVE as chaves do escopo que aquele perfil não tem — é assim que um
        // ajuste ligado pelo motorista anterior volta ao padrão em vez de ficar valendo para todo
        // mundo (ver DriverProfileManager.aplicar).
        //
        // O efeito colateral disso é que esta função é, ao mesmo tempo, a lista do que o perfil
        // guarda e a lista do que ele NÃO apaga. Se os próprios perfis entrassem no escopo, a
        // primeira troca apagaria todos os perfis do carro — e o único jeito de perceber seria
        // perdendo os perfis.
        val nuncaPodeSerApagado = listOf(
            "driverProfile_Miguel",
            "driverProfileNames",
            Preferencias.ACTIVE_DRIVER_PROFILE.chave,
            "vehicleIdentityChoice_h6|phev",
            "instrumentMaskDisplayId",
            "instrumentRevisionHistory",
            "dvrSaveLocationUri",
            "permissionsBootstrapVersion"
        )
        for (chave in nuncaPodeSerApagado) {
            assertFalse(chave, EscopoDoPerfil.preferenciaPertence(chave))
        }
    }

    @Test
    fun `chave vazia nao entra`() {
        assertFalse(EscopoDoPerfil.preferenciaPertence(""))
        assertFalse(EscopoDoPerfil.preferenciaPertence("   "))
    }

    // ── Veículo ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `as configuracoes do veiculo que o pedido cita sao reconhecidas`() {
        val citadas = listOf(
            Sinais.CAR_DRIVE_SETTING_DRIVE_MODE,                      // modo de condução
            Sinais.SYS_SETTINGS_AUDIO_MEDIA_VOLUME,                   // mídia
            Sinais.SYS_SETTINGS_AUDIO_EQ_BASS_VALUE,                  // equalização
            Sinais.SYS_SETTINGS_DISPLAY_BRIGHTNESS_LEVEL,             // interface
            Sinais.CAR_COMFORT_SETTING_DRIVER_SEAT_VENTILATION_LEVEL, // conforto
            Sinais.CAR_INTELLIGENT_DRIVING_SETTING_LAS_ENABLE,        // assistência
            Sinais.CAR_DOOR_LOCK_SETTING_LOCKED_BY_SPEED,             // preferências do carro
            Sinais.CAR_EV_SETTING_AVAS_ENABLE
        )
        for (chave in citadas) {
            assertTrue(chave.chave, EscopoDoPerfil.chaveDoVeiculoPertence(chave.chave))
        }
    }

    @Test
    fun `leitura nao e configuracao`() {
        val leituras = listOf(
            Sinais.CAR_BASIC_VEHICLE_SPEED,
            Sinais.CAR_BASIC_ENGINE_SPEED,
            Sinais.CAR_BASIC_DOOR_STATUS,
            Sinais.CAR_BASIC_ACCUMULATED_ODOMETER,
            Sinais.CAR_BASIC_COOLANT_TEMP
        )
        for (chave in leituras) {
            assertFalse(chave.chave, EscopoDoPerfil.chaveDoVeiculoPertence(chave.chave))
        }
    }

    @Test
    fun `comando nunca entra num perfil, mesmo dentro do grupo setting`() {
        // O caso que justifica a regra inteira: esta chave está no grupo "setting" e restaura o
        // carro para o estado de fábrica.
        assertFalse(EscopoDoPerfil.chaveDoVeiculoPertence("car.genernal.setting.factory_reset_action"))
        assertFalse(EscopoDoPerfil.chaveDoVeiculoPertence("sys.settings.general.reset_all_action"))
        assertFalse(EscopoDoPerfil.chaveDoVeiculoPertence("car.comfort_setting.seat_heating_action"))
        assertFalse(EscopoDoPerfil.chaveDoVeiculoPertence("car.comfort_setting.chair_mem_pos_set_action"))
        assertFalse(EscopoDoPerfil.chaveDoVeiculoPertence("car.comfort_setting.chair_mem_pos_set_feedback"))
    }

    @Test
    fun `descricao de hardware nao e escolha de ninguem`() {
        // Faixa aceita pelo módulo e presença de equipamento: escrever ali não guarda preferência.
        assertFalse(EscopoDoPerfil.chaveDoVeiculoPertence("sys.settings.audio.media_volume_range"))
        assertFalse(EscopoDoPerfil.chaveDoVeiculoPertence("sys.settings.audio.eq_value_range"))
        assertFalse(EscopoDoPerfil.chaveDoVeiculoPertence("sys.avm.device_support"))
        // O mudo é passageiro: restaurar "mudo" de um perfil silenciaria o carro na partida.
        assertFalse(EscopoDoPerfil.chaveDoVeiculoPertence("sys.settings.audio.media_mute_state"))
    }

    @Test
    fun `nada que mexa em peca fisica entra`() {
        // Pelo enum, não por texto: a grafia da chave é a parte que já falhou neste projeto
        // (ver o comentário em EscopoDoPerfil.CHAVES_QUE_ATUAM). Escrita à mão, uma trava de
        // segurança com nome errado passa no teste e não protege nada.
        val atuadores = listOf(
            Sinais.CAR_DRIVE_SETTING_OUTSIDE_VIEW_MIRROR_FOLD_STATE,
            Sinais.CAR_DRIVE_SETTING_TRAILER_HOOK_STATE,
            Sinais.CAR_COMFORT_SETTING_DRIVE_SEAT_DIRECTION_CONFIG,
            Sinais.CAR_COMFORT_SETTING_SEAT_WAIST_DIRECTION_CONFIG,
            Sinais.CAR_COMFORT_SETTING_DRIVER_SEAT_SUPPORT_STATE,
            Sinais.CAR_BASIC_CONFIGURE_CODE
        )
        for (chave in atuadores) {
            assertFalse(chave.chave, EscopoDoPerfil.chaveDoVeiculoPertence(chave.chave))
        }
    }

    @Test
    fun `a varredura do catalogo real fica num tamanho plausivel`() {
        // Guarda contra os dois jeitos de a regra quebrar em silêncio: parar de reconhecer
        // configuração (o perfil viraria só preferências do app) ou passar a aceitar o
        // barramento inteiro (o perfil escreveria em leitura). O catálogo (Sinais) tem 1014 chaves.
        val total = Sinais.TODOS.size
        val aceitas = Sinais.TODOS.count { EscopoDoPerfil.chaveDoVeiculoPertence(it.chave) }
        assertTrue("aceitas=$aceitas de $total", aceitas in 150..400)
    }

    @Test
    fun `nenhuma chave aceita termina em action`() {
        val comandos = Sinais.TODOS
            .map { it.chave }
            .filter { it.endsWith("_action") && EscopoDoPerfil.chaveDoVeiculoPertence(it) }
        assertTrue("comandos aceitos: $comandos", comandos.isEmpty())
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
