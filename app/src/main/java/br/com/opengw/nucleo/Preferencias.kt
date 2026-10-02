package br.com.opengw.nucleo

/** Uma chave do arquivo de preferências do app ("opengw_prefs", no armazenamento protegido). */
data class Preferencia(@JvmField val chave: String) {
    override fun toString() = chave
}

/**
 * As chaves de preferência que o código do OPEN GW usa. Os textos são o formato de dados já gravado
 * nos carros: mudar um texto aqui é perder o valor que a pessoa escolheu. O nome de cada constante
 * sai do próprio texto da chave.
 */
object Preferencias {
    @JvmField val AA_CLUSTER_LEFT_OFFSET = Preferencia("aaClusterLeftOffset")
    @JvmField val ACTIVE_CUSTOM_THEME = Preferencia("activeCustomTheme")
    @JvmField val ACTIVE_DRIVER_PROFILE = Preferencia("activeDriverProfile")
    @JvmField val ADAS_DISCLAIMER_ACKNOWLEDGED = Preferencia("adasDisclaimerAcknowledged")
    @JvmField val ADAS_MEMORIA_ATIVA = Preferencia("adasMemoriaAtiva")
    @JvmField val ADAS_MEMORIA_VALORES = Preferencia("adasMemoriaValores")
    @JvmField val ADVANCE_USE = Preferencia("advanceUse")
    @JvmField val AMBIENT_LIGHT_ALBUM_BLE_MODE = Preferencia("ambientLightAlbumBleMode")
    @JvmField val AMBIENT_LIGHT_ALBUM_DMX_MODE = Preferencia("ambientLightAlbumDmxMode")
    @JvmField val AMBIENT_LIGHT_ALBUM_EFFECT = Preferencia("ambientLightAlbumEffect")
    @JvmField val AMBIENT_LIGHT_ALBUM_EFFECT_OUTPUT = Preferencia("ambientLightAlbumEffectOutput")
    @JvmField val AMBIENT_LIGHT_ALBUM_EFFECT_SPEED = Preferencia("ambientLightAlbumEffectSpeed")
    @JvmField val AMBIENT_LIGHT_ANIMATIONS_ENABLED = Preferencia("ambientLightAnimationsEnabled")
    @JvmField val AMBIENT_LIGHT_AUTO_RECONNECT = Preferencia("ambientLightAutoReconnect")
    @JvmField val AMBIENT_LIGHT_BLE_COLOR_ORDER = Preferencia("ambientLightBleColorOrder")
    @JvmField val AMBIENT_LIGHT_BLE_DEVICE_MAC = Preferencia("ambientLightBleDeviceMac")
    @JvmField val AMBIENT_LIGHT_BLE_DEVICE_NAME = Preferencia("ambientLightBleDeviceName")
    @JvmField val AMBIENT_LIGHT_BLE_ENABLED = Preferencia("ambientLightBleEnabled")
    @JvmField val AMBIENT_LIGHT_BRIGHTNESS_PERCENT = Preferencia("ambientLightBrightnessPercent")
    @JvmField val AMBIENT_LIGHT_COLOR_ORDER = Preferencia("ambientLightColorOrder")
    @JvmField val AMBIENT_LIGHT_DOOR_DEVICE_MAC_ = Preferencia("ambientLightDoorDeviceMac_")
    @JvmField val AMBIENT_LIGHT_DOOR_DEVICE_NAME_ = Preferencia("ambientLightDoorDeviceName_")
    @JvmField val AMBIENT_LIGHT_MANUAL_HUE = Preferencia("ambientLightManualHue")
    @JvmField val AMBIENT_LIGHT_MUSIC_ANIMATION_ENABLED = Preferencia("ambientLightMusicAnimationEnabled")
    @JvmField val AMBIENT_LIGHT_MUSIC_MODE = Preferencia("ambientLightMusicMode")
    @JvmField val AMBIENT_LIGHT_OUTPUT = Preferencia("ambientLightOutput")
    @JvmField val AMBIENT_LIGHT_REACT_BLIND_SPOT = Preferencia("ambientLightReactBlindSpot")
    @JvmField val AMBIENT_LIGHT_REACT_CLIMATE = Preferencia("ambientLightReactClimate")
    @JvmField val AMBIENT_LIGHT_REACT_DRIVE_MODE = Preferencia("ambientLightReactDriveMode")
    @JvmField val AMBIENT_LIGHT_REACT_HAZARD = Preferencia("ambientLightReactHazard")
    @JvmField val AMBIENT_LIGHT_REACT_HEADLIGHT = Preferencia("ambientLightReactHeadlight")
    @JvmField val AMBIENT_LIGHT_REACT_TURN_SIGNAL = Preferencia("ambientLightReactTurnSignal")
    @JvmField val AMBIENT_LIGHT_SYNC_DRIVE_MODE = Preferencia("ambientLightSyncDriveMode")
    @JvmField val ANDROID_SETTINGS_SHORTCUT_SEEDED = Preferencia("androidSettingsShortcutSeeded")
    @JvmField val APLICAR_PERFIL_AO_LIGAR = Preferencia("aplicarPerfilAoLigar")
    @JvmField val ASD_ACTIVATION = Preferencia("asdActivation")
    @JvmField val ASD_CADENCE = Preferencia("asdCadence")
    @JvmField val ASD_LEARNED_BUFFER_FRAMES = Preferencia("asdLearnedBufferFrames")
    @JvmField val ASD_MASTER_ENABLED = Preferencia("asdMasterEnabled")
    @JvmField val ASD_MUFFLE_AMOUNT = Preferencia("asdMuffleAmount")
    @JvmField val ASD_MUFFLE_ENABLED = Preferencia("asdMuffleEnabled")
    @JvmField val ASD_MUTE_IN_PARK = Preferencia("asdMuteInPark")
    @JvmField val ASD_PEDAL_PROPERTY_KEY = Preferencia("asdPedalPropertyKey")
    @JvmField val ASD_PITCH = Preferencia("asdPitch")
    @JvmField val ASD_POSITION_X = Preferencia("asdPositionX")
    @JvmField val ASD_POSITION_Y = Preferencia("asdPositionY")
    @JvmField val ASD_RPM_SOURCE = Preferencia("asdRpmSource")
    @JvmField val ASD_SOUND_PROFILE = Preferencia("asdSoundProfile")
    @JvmField val ASD_VOLUME = Preferencia("asdVolume")
    @JvmField val ASD_VOLUME_MODE = Preferencia("asdVolumeMode")
    @JvmField val ASD_VOLUME_RPM_HIGH = Preferencia("asdVolumeRpmHigh")
    @JvmField val ASD_VOLUME_RPM_LOW = Preferencia("asdVolumeRpmLow")
    @JvmField val ASSISTENTE_CONCLUIDO_VERSAO = Preferencia("assistenteConcluidoVersao")
    @JvmField val AUDIO_EQ_BAND_GAINS = Preferencia("audioEqBandGains")
    @JvmField val AUDIO_EQ_ENABLED = Preferencia("audioEqEnabled")
    @JvmField val AUDIO_REVERB_ENABLED = Preferencia("audioReverbEnabled")
    @JvmField val AUDIO_REVERB_ROOM_SIZE = Preferencia("audioReverbRoomSize")
    @JvmField val AUDIO_REVERB_WETNESS = Preferencia("audioReverbWetness")
    @JvmField val AUTO_BRIGHTNESS_CITY = Preferencia("autoBrightnessCity")
    @JvmField val AUTO_BRIGHTNESS_LAST_LAT = Preferencia("autoBrightnessLastLat")
    @JvmField val AUTO_BRIGHTNESS_LAST_LON = Preferencia("autoBrightnessLastLon")
    @JvmField val AUTO_BRIGHTNESS_LEVEL_DAY = Preferencia("autoBrightnessLevelDay")
    @JvmField val AUTO_BRIGHTNESS_LEVEL_NIGHT = Preferencia("autoBrightnessLevelNight")
    @JvmField val AUTO_BRIGHTNESS_STEP_MIN = Preferencia("autoBrightnessStepMin")
    @JvmField val AUTO_BRIGHTNESS_USE_SUN = Preferencia("autoBrightnessUseSun")
    @JvmField val AUTO_MOVE_PROJECTION_TO_CLUSTER = Preferencia("autoMoveProjectionToCluster")
    @JvmField val AUTO_RECALL_SEAT_MEMORY = Preferencia("autoRecallSeatMemory")
    @JvmField val BLOCK_CAR_MOBILE_DATA = Preferencia("blockCarMobileData")
    @JvmField val BLOCK_DATATRACK_TELEMETRY = Preferencia("blockDatatrackTelemetry")
    @JvmField val BLUETOOTH_STATE_ON_POWER_OFF = Preferencia("bluetoothStateOnPowerOff")
    @JvmField val BOTTOM_BAR_AUTO_HIDE = Preferencia("bottomBarAutoHide")
    @JvmField val BOTTOM_BAR_AUTO_OVERSCAN = Preferencia("bottomBarAutoOverscan")
    @JvmField val BOTTOM_BAR_HEIGHT_DP = Preferencia("bottomBarHeightDp")
    @JvmField val BOTTOM_BAR_OVERRIDES = Preferencia("bottomBarOverrides")
    @JvmField val BOTTOM_BAR_SHOW_AC = Preferencia("bottomBarShowAc")
    @JvmField val BOTTOM_BAR_SHOW_AC_DUAL_ZONE = Preferencia("bottomBarShowAcDualZone")
    @JvmField val BOTTOM_BAR_SHOW_SPORTS_DISPLAY = Preferencia("bottomBarShowSportsDisplay")
    @JvmField val BOTTOM_BAR_SHOW_SPORT_PLUS = Preferencia("bottomBarShowSportPlus")
    @JvmField val BOTTOM_BAR_SHOW_VOLUME = Preferencia("bottomBarShowVolume")
    @JvmField val BOTTOM_BAR_SWIPE_UP_ACTION = Preferencia("bottomBarSwipeUpAction")
    @JvmField val BOTTOM_BAR_SWIPE_UP_PACKAGE = Preferencia("bottomBarSwipeUpPackage")
    @JvmField val BOTTOM_BAR_TEMP_CONTROL = Preferencia("bottomBarTempControl")
    @JvmField val BYPASS_SELF_INSTALLATION_INTEGRITY_CHECK = Preferencia("bypassSelfInstallationIntegrityCheck")
    @JvmField val CAR_MONITOR_PROPERTIES = Preferencia("carMonitorProperties")
    @JvmField val CLOSE_SUNROOF_ON_FOLD_MIRROR = Preferencia("closeSunroofOnFoldMirror")
    @JvmField val CLOSE_SUNROOF_ON_POWER_OFF = Preferencia("closeSunroofOnPowerOff")
    @JvmField val CLOSE_SUNROOF_ON_SPEED = Preferencia("closeSunroofOnSpeed")
    @JvmField val CLOSE_SUNROOF_SUN_SHADE_ON_CLOSE_SUN_ROOF = Preferencia("closeSunroofSunShadeOnCloseSunRoof")
    @JvmField val CLOSE_WINDOWS_ON_SPEED = Preferencia("closeWindowsOnSpeed")
    @JvmField val CLOSE_WINDOW_ON_FOLD_MIRROR = Preferencia("closeWindowOnFoldMirror")
    @JvmField val CLOSE_WINDOW_ON_LOCK_VEHICLE = Preferencia("closeWindowOnLockVehicle")
    @JvmField val CLOSE_WINDOW_ON_POWER_OFF = Preferencia("closeWindowOnPowerOff")
    @JvmField val CLUSTER_FUEL_DISPLAY_UNIT = Preferencia("clusterFuelDisplayUnit")
    @JvmField val CLUSTER_HIDE_SPEEDOMETER_ON_MAPS = Preferencia("clusterHideSpeedometerOnMaps")
    @JvmField val CLUSTER_PROJECTION_OPENS_DASHBOARD = Preferencia("clusterProjectionOpensDashboard")
    @JvmField val CLUSTER_V2_TRIP_INFO = Preferencia("clusterV2TripInfo")
    @JvmField val CONECTOR_MODO_PARA_VOLTAR = Preferencia("conectorModoParaVoltar")
    @JvmField val CONECTOR_REGISTRO = Preferencia("conectorRegistro")
    @JvmField val CORINGA1_ACAO = Preferencia("coringa1Acao")
    @JvmField val CORINGA1_ORIGINAL = Preferencia("coringa1Original")
    @JvmField val CORINGA1_TECLA = Preferencia("coringa1Tecla")
    @JvmField val CORINGA1_TOMADO = Preferencia("coringa1Tomado")
    @JvmField val CORINGA2_ACAO = Preferencia("coringa2Acao")
    @JvmField val CORINGA2_ORIGINAL = Preferencia("coringa2Original")
    @JvmField val CORINGA2_TECLA = Preferencia("coringa2Tecla")
    @JvmField val CORINGA2_TOMADO = Preferencia("coringa2Tomado")
    @JvmField val CORINGAS_ATIVOS = Preferencia("coringasAtivos")
    @JvmField val CURRENT_CLUSTER_COLOR = Preferencia("currentClusterColor")
    @JvmField val CURRENT_CLUSTER_DISPLAY = Preferencia("currentClusterDisplay")
    @JvmField val CURRENT_CLUSTER_TEMPLATE = Preferencia("currentClusterTemplate")
    @JvmField val CURRENT_USER = Preferencia("currentUser")
    @JvmField val CUSTOM_BACKGROUND_TYPE_D1 = Preferencia("customBackgroundTypeD1")
    @JvmField val CUSTOM_BACKGROUND_VALUE_D1 = Preferencia("customBackgroundValueD1")
    @JvmField val DASHBOARD_CARD_ORDER = Preferencia("dashboardCardOrder")
    @JvmField val DATATRACK_DISABLED_BY_APP = Preferencia("datatrackDisabledByApp")
    @JvmField val DEFAULT_DISPLAY_APP_PACKAGE = Preferencia("defaultDisplayAppPackage")
    @JvmField val DIAGNOSTIC_ADVANCED_MODE = Preferencia("diagnosticAdvancedMode")
    @JvmField val DISABLE_AVAS = Preferencia("disableAvas")
    @JvmField val DISABLE_AVM_CAR_STOPPED = Preferencia("disableAvmCarStopped")
    @JvmField val DISABLE_BLUETOOTH_ON_FOLD_MIRROR = Preferencia("disableBluetoothOnFoldMirror")
    @JvmField val DISABLE_BLUETOOTH_ON_POWER_OFF = Preferencia("disableBluetoothOnPowerOff")
    @JvmField val DISABLE_HOTSPOT_ON_FOLD_MIRROR = Preferencia("disableHotspotOnFoldMirror")
    @JvmField val DISABLE_HOTSPOT_ON_POWER_OFF = Preferencia("disableHotspotOnPowerOff")
    @JvmField val DISABLE_MONITORING = Preferencia("disableMonitoring")
    @JvmField val DISABLE_NATIVE_NAVIGATION = Preferencia("disableNativeNavigation")
    @JvmField val DISABLE_NATIVE_VOICE = Preferencia("disableNativeVoice")
    @JvmField val DISABLE_NATIVE_WEATHER = Preferencia("disableNativeWeather")
    @JvmField val DISABLE_VOICE_WAKE_UP = Preferencia("disableVoiceWakeUp")
    @JvmField val DISPLAY_APP_CONFIGS = Preferencia("displayAppConfigs")
    @JvmField val DVR_SAVE_LOCATION_URI = Preferencia("dvrSaveLocationUri")
    @JvmField val DVR_STORAGE_PATH = Preferencia("dvrStoragePath")
    @JvmField val ENABLE_AA_CLUSTER_OFFSET = Preferencia("enableAaClusterOffset")
    @JvmField val ENABLE_AUTO_BRIGHTNESS = Preferencia("enableAutoBrightness")
    @JvmField val ENABLE_CUSTOM_BACKGROUND_D1 = Preferencia("enableCustomBackgroundD1")
    @JvmField val ENABLE_CUSTOM_MENU = Preferencia("enableCustomMenu")
    @JvmField val ENABLE_HOT_ROUTER = Preferencia("enableHotRouter")
    @JvmField val ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION = Preferencia("enableInstrumentCustomMediaIntegration")
    @JvmField val ENABLE_INSTRUMENT_EV_BATTERY_PERCENTAGE = Preferencia("enableInstrumentEvBatteryPercentage")
    @JvmField val ENABLE_INSTRUMENT_ODOMETER = Preferencia("enableInstrumentOdometer")
    @JvmField val ENABLE_INSTRUMENT_PROJECTOR = Preferencia("enableInstrumentProjector")
    @JvmField val ENABLE_INSTRUMENT_REVISION_WARNING = Preferencia("enableInstrumentRevisionWarning")
    @JvmField val ENABLE_MAX_AC_ON_UNLOCK = Preferencia("enableMaxAcOnUnlock")
    @JvmField val ENABLE_ODOMETER_AND_REVISION = Preferencia("enableOdometerAndRevision")
    @JvmField val ENABLE_OPEN_SUNROOF_CURTAIN_ON_START = Preferencia("enableOpenSunroofCurtainOnStart")
    @JvmField val ENABLE_PERSISTENT_BOTTOM_BAR = Preferencia("enable_persistent_bottom_bar")
    @JvmField val ENABLE_PERSIST_HEV_SOC_TARGET = Preferencia("enablePersistHevSocTarget")
    @JvmField val ENABLE_RESOURCE_OVERLAY = Preferencia("enableResourceOverlay")
    @JvmField val ENABLE_SEAT_VENTILATION_ON_AC_ON = Preferencia("enableSeatVentilationOnAcOn")
    @JvmField val ENABLE_SPEED_ADJUSTMENT = Preferencia("enableSpeedAdjustment")
    @JvmField val ENABLE_STEERING_WHEEL_CUSTOM_BUTTONS = Preferencia("enableSteeringWheelCustomButtons")
    @JvmField val ENABLE_STEERING_WHEEL_MEDIA_CONTROL = Preferencia("enableSteeringWheelMediaControl")
    @JvmField val ENABLE_VIRTUAL_CLUSTER = Preferencia("enableVirtualCluster")
    @JvmField val GAUGE_NUMBER_SCALE = Preferencia("gaugeNumberScale")
    @JvmField val HEV_SOC_TARGET_VALUE = Preferencia("hevSocTargetValue")
    @JvmField val HIDE_BAR_ON_ANDROID_AUTO = Preferencia("hideBarOnAndroidAuto")
    @JvmField val HIDE_LEFT_NAV_PANE = Preferencia("hideLeftNavPane")
    @JvmField val HOME_LAUNCHER3D_FIXO = Preferencia("homeLauncher3dFixo")
    @JvmField val HOTSPOT_STATE_ON_POWER_OFF = Preferencia("hotspotStateOnPowerOff")
    @JvmField val HUD_FONT_FAMILY = Preferencia("hudFontFamily")
    @JvmField val HUD_FONT_WEIGHT = Preferencia("hudFontWeight")
    @JvmField val HUD_LABEL_COLOR = Preferencia("hudLabelColor")
    @JvmField val HUD_NIGHT_AMBER = Preferencia("hudNightAmber")
    @JvmField val HUD_NIGHT_BRIGHTNESS = Preferencia("hudNightBrightness")
    @JvmField val HUD_NIGHT_MODE = Preferencia("hudNightMode")
    @JvmField val HUD_OVERLAY_DISPLAY_ID = Preferencia("hudOverlayDisplayId")
    @JvmField val HUD_OVERLAY_ENABLED = Preferencia("hudOverlayEnabled")
    @JvmField val HUD_PAGES_ENABLED = Preferencia("hudPagesEnabled")
    @JvmField val HUD_PAGE_COLORS = Preferencia("hudPageColors")
    @JvmField val HUD_PAGE_LAYOUTS = Preferencia("hudPageLayouts")
    @JvmField val HUD_PAGE_ORDER = Preferencia("hudPageOrder")
    @JvmField val HUD_PAGE_SCALES = Preferencia("hudPageScales")
    @JvmField val HUD_PAGE_TITLES = Preferencia("hudPageTitles")
    @JvmField val HUD_SHOW_ALERTS = Preferencia("hudShowAlerts")
    @JvmField val HUD_SHOW_DOTS = Preferencia("hudShowDots")
    @JvmField val HUD_SHOW_HEADER = Preferencia("hudShowHeader")
    @JvmField val HUD_SHOW_SLI = Preferencia("hudShowSli")
    @JvmField val HUD_SHOW_SPEED_UNIT = Preferencia("hudShowSpeedUnit")
    @JvmField val HUD_SLI_SIDE = Preferencia("hudSliSide")
    @JvmField val HUD_SLI_SIZE_PCT = Preferencia("hudSliSizePct")
    @JvmField val HUD_SPEED_COLOR = Preferencia("hudSpeedColor")
    @JvmField val HUD_SPEED_SIZE_PCT = Preferencia("hudSpeedSizePct")
    @JvmField val HUD_THEME_NAME = Preferencia("hudThemeName")
    @JvmField val HUD_WHEEL_CONTROL = Preferencia("hudWheelControl")
    @JvmField val INSTRUMENT_MASK_DISPLAY_ID = Preferencia("instrumentMaskDisplayId")
    @JvmField val INSTRUMENT_REVISION_HISTORY = Preferencia("instrumentRevisionHistory")
    @JvmField val INSTRUMENT_REVISION_KM = Preferencia("instrumentRevisionKm")
    @JvmField val INSTRUMENT_REVISION_NEXT_DATE = Preferencia("instrumentRevisionNextDate")
    @JvmField val INTERFACE_ESCALA = Preferencia("interfaceEscala")
    @JvmField val INTERFACE_ESCALA_TEXTO = Preferencia("interfaceEscalaTexto")
    @JvmField val INTERFACE_STYLE = Preferencia("interfaceStyle")
    @JvmField val LAST_CLUSTER_AC_CONFIG = Preferencia("lastClusterAcConfig")
    @JvmField val LAST_CLUSTER_MENU_ITEM = Preferencia("lastClusterMenuItem")
    @JvmField val LAST_CLUSTER_SCREEN = Preferencia("lastClusterScreen")
    @JvmField val MACROS_ENABLED = Preferencia("macrosEnabled")
    @JvmField val MACROS_JSON = Preferencia("macrosJson")
    @JvmField val MACRO_GESTURES_ENABLED = Preferencia("macroGesturesEnabled")
    @JvmField val MACRO_GESTURES_JSON = Preferencia("macroGesturesJson")
    @JvmField val MAX_AC_ON_UNLOCK_THRESHOLD = Preferencia("maxAcOnUnlockThreshold")
    @JvmField val MAX_AC_TARGET_TEMP = Preferencia("maxAcTargetTemp")
    @JvmField val MAX_AC_TIMEOUT = Preferencia("maxAcTimeout")
    @JvmField val MOBILE_DATA_AUTOBLOCK = Preferencia("mobileDataAutoblock")
    @JvmField val MOBILE_DATA_AUTOBLOCK_CAP_MB = Preferencia("mobileDataAutoblockCapMb")
    @JvmField val MOBILE_DATA_BLOCK_ON_PROJECTION = Preferencia("mobileDataBlockOnProjection")
    @JvmField val MOBILE_DATA_BLOCK_ON_WIFI = Preferencia("mobileDataBlockOnWifi")
    @JvmField val MOBILE_DATA_CONTROL_ENABLED = Preferencia("mobileDataControlEnabled")
    @JvmField val MOBILE_DATA_CYCLE_DAY = Preferencia("mobileDataCycleDay")
    @JvmField val MOBILE_DATA_DISABLED_BY_APP = Preferencia("mobileDataDisabledByApp")
    @JvmField val MOBILE_DATA_TRAFFIC_ACCUM_BYTES = Preferencia("mobileDataTrafficAccumBytes")
    @JvmField val MOBILE_DATA_TRAFFIC_CYCLE_TAG = Preferencia("mobileDataTrafficCycleTag")
    @JvmField val MOBILE_DATA_TRAFFIC_LAST_READING = Preferencia("mobileDataTrafficLastReading")
    @JvmField val NIGHT_END_HOUR = Preferencia("nightEndHour")
    @JvmField val NIGHT_END_MINUTE = Preferencia("nightEndMinute")
    @JvmField val NIGHT_MODE_COLOR = Preferencia("nightModeColor")
    @JvmField val NIGHT_MODE_TRIGGER = Preferencia("nightModeTrigger")
    @JvmField val NIGHT_START_HOUR = Preferencia("nightStartHour")
    @JvmField val NIGHT_START_MINUTE = Preferencia("nightStartMinute")
    @JvmField val NOTIFICATION_LISTENERS_BACKUP = Preferencia("notificationListenersBackup")
    @JvmField val NOVIDADES_VISTAS_ATE = Preferencia("novidadesVistasAte")
    @JvmField val OPEN_SUNROOF_CURTAIN_END_HOUR = Preferencia("openSunroofCurtainEndHour")
    @JvmField val OPEN_SUNROOF_CURTAIN_END_MINUTE = Preferencia("openSunroofCurtainEndMinute")
    @JvmField val OPEN_SUNROOF_CURTAIN_MAX_TEMP = Preferencia("openSunroofCurtainMaxTemp")
    @JvmField val OPEN_SUNROOF_CURTAIN_START_HOUR = Preferencia("openSunroofCurtainStartHour")
    @JvmField val OPEN_SUNROOF_CURTAIN_START_MINUTE = Preferencia("openSunroofCurtainStartMinute")
    @JvmField val ORIGINAL_PANEL_CIRCLE_CLOCK = Preferencia("originalPanelCircleClock")
    @JvmField val ORIGINAL_PANEL_CIRCLE_PAGE = Preferencia("originalPanelCirclePage")
    @JvmField val ORIGINAL_PANEL_CIRCLE_PROJECTION = Preferencia("originalPanelCircleProjection")
    @JvmField val ORIGINAL_PANEL_INFO = Preferencia("originalPanelInfo")
    @JvmField val ORIGINAL_PANEL_TEMP_SCALE = Preferencia("originalPanelTempScale")
    @JvmField val PENDING_RESET_TARGET_VERSION = Preferencia("pendingResetTargetVersion")
    @JvmField val PERMISSIONS_BOOTSTRAP_VERSION = Preferencia("permissionsBootstrapVersion")
    @JvmField val PERSISTENT_BOTTOM_BAR_OVERSCAN = Preferencia("persistentBottomBarOverscan")
    @JvmField val PROJECTION_GESTURE_ENABLED = Preferencia("projectionGestureEnabled")
    @JvmField val PROJECTION_LAST_PANEL_BOX = Preferencia("projectionLastPanelBox")
    @JvmField val PROJECTION_LAST_REGION = Preferencia("projectionLastRegion")
    @JvmField val PROJECTION_LAST_REGION_CIRCLE = Preferencia("projectionLastRegionCircle")
    @JvmField val QUICK_GESTURES_ENABLED = Preferencia("quickGesturesEnabled")
    @JvmField val RESOURCE_OVERLAY_CORNER = Preferencia("resourceOverlayCorner")
    @JvmField val RESOURCE_OVERLAY_FONT_SP = Preferencia("resourceOverlayFontSp")
    @JvmField val RESOURCE_OVERLAY_X = Preferencia("resourceOverlayX")
    @JvmField val RESOURCE_OVERLAY_Y = Preferencia("resourceOverlayY")
    @JvmField val SELF_INSTALLATION_INTEGRITY_CHECK = Preferencia("selfInstallationIntegrityCheck")
    @JvmField val SET_STARTUP_VOLUME = Preferencia("setStartupVolume")
    @JvmField val SHOW_BETA_UPDATES = Preferencia("showBetaUpdates")
    @JvmField val SPEED_ADJUSTMENT_OFFSET = Preferencia("speedAdjustmentOffset")
    @JvmField val SPEED_THRESHOLD = Preferencia("speedThreshold")
    @JvmField val SPORTS_DISPLAY_POWER_UNIT = Preferencia("sportsDisplayPowerUnit")
    @JvmField val SPORT_PLUS_ENABLED = Preferencia("sportPlusEnabled")
    @JvmField val STARTUP_VOLUME = Preferencia("startupVolume")
    @JvmField val STEALTH_MODE_ACTIVE = Preferencia("stealthModeActive")
    @JvmField val STEERING_WHEEL_CUSTOM_BUTON1_ACTION_ORIGINAL = Preferencia("steeringWheelCustomButon1ActionOriginal")
    @JvmField val STEERING_WHEEL_CUSTOM_BUTON2_ACTION_ORIGINAL = Preferencia("steeringWheelCustomButon2ActionOriginal")
    @JvmField val STREAMDECK_ENABLED = Preferencia("streamdeckEnabled")
    @JvmField val STREAMDECK_KEY_ACTION_ = Preferencia("streamdeckKeyAction_")
    @JvmField val SUNROOF_SPEED_THRESHOLD = Preferencia("sunroofSpeedThreshold")
    @JvmField val THEME_RELOAD_NONCE = Preferencia("themeReloadNonce")
    @JvmField val TOUR_CONCLUIDO = Preferencia("tourConcluido")
    @JvmField val TRIP_CONSISTENCY_CLUSTER_ACTIVE = Preferencia("tripConsistencyClusterActive")
    @JvmField val TRIP_CONSISTENCY_CLUSTER_SCORE = Preferencia("tripConsistencyClusterScore")
    @JvmField val UPDATE_INSTALL_IN_PROGRESS = Preferencia("updateInstallInProgress")
    @JvmField val VIEWER_COMMANDS_ENABLED = Preferencia("viewerCommandsEnabled")
    @JvmField val VIRTUAL_CLUSTER_NIGHT_MODE = Preferencia("virtualClusterNightMode")
    @JvmField val VIRTUAL_CLUSTER_THEME = Preferencia("virtualClusterTheme")
    @JvmField val VOLUME_OVERLAY_LOGO_CUSTOM_TEXT = Preferencia("volumeOverlayLogoCustomText")
    @JvmField val VOLUME_OVERLAY_LOGO_TYPE = Preferencia("volumeOverlayLogoType")
    @JvmField val VOLUME_OVERLAY_ON_CAR_CHANGE = Preferencia("volumeOverlayOnCarChange")

    /** Todas as chaves acima, em ordem alfabética do nome. */
    @JvmField val TODAS: List<Preferencia> = listOf(
        AA_CLUSTER_LEFT_OFFSET,
        ACTIVE_CUSTOM_THEME,
        ACTIVE_DRIVER_PROFILE,
        ADAS_DISCLAIMER_ACKNOWLEDGED,
        ADAS_MEMORIA_ATIVA,
        ADAS_MEMORIA_VALORES,
        ADVANCE_USE,
        AMBIENT_LIGHT_ALBUM_BLE_MODE,
        AMBIENT_LIGHT_ALBUM_DMX_MODE,
        AMBIENT_LIGHT_ALBUM_EFFECT,
        AMBIENT_LIGHT_ALBUM_EFFECT_OUTPUT,
        AMBIENT_LIGHT_ALBUM_EFFECT_SPEED,
        AMBIENT_LIGHT_ANIMATIONS_ENABLED,
        AMBIENT_LIGHT_AUTO_RECONNECT,
        AMBIENT_LIGHT_BLE_COLOR_ORDER,
        AMBIENT_LIGHT_BLE_DEVICE_MAC,
        AMBIENT_LIGHT_BLE_DEVICE_NAME,
        AMBIENT_LIGHT_BLE_ENABLED,
        AMBIENT_LIGHT_BRIGHTNESS_PERCENT,
        AMBIENT_LIGHT_COLOR_ORDER,
        AMBIENT_LIGHT_DOOR_DEVICE_MAC_,
        AMBIENT_LIGHT_DOOR_DEVICE_NAME_,
        AMBIENT_LIGHT_MANUAL_HUE,
        AMBIENT_LIGHT_MUSIC_ANIMATION_ENABLED,
        AMBIENT_LIGHT_MUSIC_MODE,
        AMBIENT_LIGHT_OUTPUT,
        AMBIENT_LIGHT_REACT_BLIND_SPOT,
        AMBIENT_LIGHT_REACT_CLIMATE,
        AMBIENT_LIGHT_REACT_DRIVE_MODE,
        AMBIENT_LIGHT_REACT_HAZARD,
        AMBIENT_LIGHT_REACT_HEADLIGHT,
        AMBIENT_LIGHT_REACT_TURN_SIGNAL,
        AMBIENT_LIGHT_SYNC_DRIVE_MODE,
        ANDROID_SETTINGS_SHORTCUT_SEEDED,
        APLICAR_PERFIL_AO_LIGAR,
        ASD_ACTIVATION,
        ASD_CADENCE,
        ASD_LEARNED_BUFFER_FRAMES,
        ASD_MASTER_ENABLED,
        ASD_MUFFLE_AMOUNT,
        ASD_MUFFLE_ENABLED,
        ASD_MUTE_IN_PARK,
        ASD_PEDAL_PROPERTY_KEY,
        ASD_PITCH,
        ASD_POSITION_X,
        ASD_POSITION_Y,
        ASD_RPM_SOURCE,
        ASD_SOUND_PROFILE,
        ASD_VOLUME,
        ASD_VOLUME_MODE,
        ASD_VOLUME_RPM_HIGH,
        ASD_VOLUME_RPM_LOW,
        ASSISTENTE_CONCLUIDO_VERSAO,
        AUDIO_EQ_BAND_GAINS,
        AUDIO_EQ_ENABLED,
        AUDIO_REVERB_ENABLED,
        AUDIO_REVERB_ROOM_SIZE,
        AUDIO_REVERB_WETNESS,
        AUTO_BRIGHTNESS_CITY,
        AUTO_BRIGHTNESS_LAST_LAT,
        AUTO_BRIGHTNESS_LAST_LON,
        AUTO_BRIGHTNESS_LEVEL_DAY,
        AUTO_BRIGHTNESS_LEVEL_NIGHT,
        AUTO_BRIGHTNESS_STEP_MIN,
        AUTO_BRIGHTNESS_USE_SUN,
        AUTO_MOVE_PROJECTION_TO_CLUSTER,
        AUTO_RECALL_SEAT_MEMORY,
        BLOCK_CAR_MOBILE_DATA,
        BLOCK_DATATRACK_TELEMETRY,
        BLUETOOTH_STATE_ON_POWER_OFF,
        BOTTOM_BAR_AUTO_HIDE,
        BOTTOM_BAR_AUTO_OVERSCAN,
        BOTTOM_BAR_HEIGHT_DP,
        BOTTOM_BAR_OVERRIDES,
        BOTTOM_BAR_SHOW_AC,
        BOTTOM_BAR_SHOW_AC_DUAL_ZONE,
        BOTTOM_BAR_SHOW_SPORTS_DISPLAY,
        BOTTOM_BAR_SHOW_SPORT_PLUS,
        BOTTOM_BAR_SHOW_VOLUME,
        BOTTOM_BAR_SWIPE_UP_ACTION,
        BOTTOM_BAR_SWIPE_UP_PACKAGE,
        BOTTOM_BAR_TEMP_CONTROL,
        BYPASS_SELF_INSTALLATION_INTEGRITY_CHECK,
        CAR_MONITOR_PROPERTIES,
        CLOSE_SUNROOF_ON_FOLD_MIRROR,
        CLOSE_SUNROOF_ON_POWER_OFF,
        CLOSE_SUNROOF_ON_SPEED,
        CLOSE_SUNROOF_SUN_SHADE_ON_CLOSE_SUN_ROOF,
        CLOSE_WINDOWS_ON_SPEED,
        CLOSE_WINDOW_ON_FOLD_MIRROR,
        CLOSE_WINDOW_ON_LOCK_VEHICLE,
        CLOSE_WINDOW_ON_POWER_OFF,
        CLUSTER_FUEL_DISPLAY_UNIT,
        CLUSTER_HIDE_SPEEDOMETER_ON_MAPS,
        CLUSTER_PROJECTION_OPENS_DASHBOARD,
        CLUSTER_V2_TRIP_INFO,
        CONECTOR_MODO_PARA_VOLTAR,
        CONECTOR_REGISTRO,
        CORINGA1_ACAO,
        CORINGA1_ORIGINAL,
        CORINGA1_TECLA,
        CORINGA1_TOMADO,
        CORINGA2_ACAO,
        CORINGA2_ORIGINAL,
        CORINGA2_TECLA,
        CORINGA2_TOMADO,
        CORINGAS_ATIVOS,
        CURRENT_CLUSTER_COLOR,
        CURRENT_CLUSTER_DISPLAY,
        CURRENT_CLUSTER_TEMPLATE,
        CURRENT_USER,
        CUSTOM_BACKGROUND_TYPE_D1,
        CUSTOM_BACKGROUND_VALUE_D1,
        DASHBOARD_CARD_ORDER,
        DATATRACK_DISABLED_BY_APP,
        DEFAULT_DISPLAY_APP_PACKAGE,
        DIAGNOSTIC_ADVANCED_MODE,
        DISABLE_AVAS,
        DISABLE_AVM_CAR_STOPPED,
        DISABLE_BLUETOOTH_ON_FOLD_MIRROR,
        DISABLE_BLUETOOTH_ON_POWER_OFF,
        DISABLE_HOTSPOT_ON_FOLD_MIRROR,
        DISABLE_HOTSPOT_ON_POWER_OFF,
        DISABLE_MONITORING,
        DISABLE_NATIVE_NAVIGATION,
        DISABLE_NATIVE_VOICE,
        DISABLE_NATIVE_WEATHER,
        DISABLE_VOICE_WAKE_UP,
        DISPLAY_APP_CONFIGS,
        DVR_SAVE_LOCATION_URI,
        DVR_STORAGE_PATH,
        ENABLE_AA_CLUSTER_OFFSET,
        ENABLE_AUTO_BRIGHTNESS,
        ENABLE_CUSTOM_BACKGROUND_D1,
        ENABLE_CUSTOM_MENU,
        ENABLE_HOT_ROUTER,
        ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION,
        ENABLE_INSTRUMENT_EV_BATTERY_PERCENTAGE,
        ENABLE_INSTRUMENT_ODOMETER,
        ENABLE_INSTRUMENT_PROJECTOR,
        ENABLE_INSTRUMENT_REVISION_WARNING,
        ENABLE_MAX_AC_ON_UNLOCK,
        ENABLE_ODOMETER_AND_REVISION,
        ENABLE_OPEN_SUNROOF_CURTAIN_ON_START,
        ENABLE_PERSISTENT_BOTTOM_BAR,
        ENABLE_PERSIST_HEV_SOC_TARGET,
        ENABLE_RESOURCE_OVERLAY,
        ENABLE_SEAT_VENTILATION_ON_AC_ON,
        ENABLE_SPEED_ADJUSTMENT,
        ENABLE_STEERING_WHEEL_CUSTOM_BUTTONS,
        ENABLE_STEERING_WHEEL_MEDIA_CONTROL,
        ENABLE_VIRTUAL_CLUSTER,
        GAUGE_NUMBER_SCALE,
        HEV_SOC_TARGET_VALUE,
        HIDE_BAR_ON_ANDROID_AUTO,
        HIDE_LEFT_NAV_PANE,
        HOME_LAUNCHER3D_FIXO,
        HOTSPOT_STATE_ON_POWER_OFF,
        HUD_FONT_FAMILY,
        HUD_FONT_WEIGHT,
        HUD_LABEL_COLOR,
        HUD_NIGHT_AMBER,
        HUD_NIGHT_BRIGHTNESS,
        HUD_NIGHT_MODE,
        HUD_OVERLAY_DISPLAY_ID,
        HUD_OVERLAY_ENABLED,
        HUD_PAGES_ENABLED,
        HUD_PAGE_COLORS,
        HUD_PAGE_LAYOUTS,
        HUD_PAGE_ORDER,
        HUD_PAGE_SCALES,
        HUD_PAGE_TITLES,
        HUD_SHOW_ALERTS,
        HUD_SHOW_DOTS,
        HUD_SHOW_HEADER,
        HUD_SHOW_SLI,
        HUD_SHOW_SPEED_UNIT,
        HUD_SLI_SIDE,
        HUD_SLI_SIZE_PCT,
        HUD_SPEED_COLOR,
        HUD_SPEED_SIZE_PCT,
        HUD_THEME_NAME,
        HUD_WHEEL_CONTROL,
        INSTRUMENT_MASK_DISPLAY_ID,
        INSTRUMENT_REVISION_HISTORY,
        INSTRUMENT_REVISION_KM,
        INSTRUMENT_REVISION_NEXT_DATE,
        INTERFACE_ESCALA,
        INTERFACE_ESCALA_TEXTO,
        INTERFACE_STYLE,
        LAST_CLUSTER_AC_CONFIG,
        LAST_CLUSTER_MENU_ITEM,
        LAST_CLUSTER_SCREEN,
        MACROS_ENABLED,
        MACROS_JSON,
        MACRO_GESTURES_ENABLED,
        MACRO_GESTURES_JSON,
        MAX_AC_ON_UNLOCK_THRESHOLD,
        MAX_AC_TARGET_TEMP,
        MAX_AC_TIMEOUT,
        MOBILE_DATA_AUTOBLOCK,
        MOBILE_DATA_AUTOBLOCK_CAP_MB,
        MOBILE_DATA_BLOCK_ON_PROJECTION,
        MOBILE_DATA_BLOCK_ON_WIFI,
        MOBILE_DATA_CONTROL_ENABLED,
        MOBILE_DATA_CYCLE_DAY,
        MOBILE_DATA_DISABLED_BY_APP,
        MOBILE_DATA_TRAFFIC_ACCUM_BYTES,
        MOBILE_DATA_TRAFFIC_CYCLE_TAG,
        MOBILE_DATA_TRAFFIC_LAST_READING,
        NIGHT_END_HOUR,
        NIGHT_END_MINUTE,
        NIGHT_MODE_COLOR,
        NIGHT_MODE_TRIGGER,
        NIGHT_START_HOUR,
        NIGHT_START_MINUTE,
        NOTIFICATION_LISTENERS_BACKUP,
        NOVIDADES_VISTAS_ATE,
        OPEN_SUNROOF_CURTAIN_END_HOUR,
        OPEN_SUNROOF_CURTAIN_END_MINUTE,
        OPEN_SUNROOF_CURTAIN_MAX_TEMP,
        OPEN_SUNROOF_CURTAIN_START_HOUR,
        OPEN_SUNROOF_CURTAIN_START_MINUTE,
        ORIGINAL_PANEL_CIRCLE_CLOCK,
        ORIGINAL_PANEL_CIRCLE_PAGE,
        ORIGINAL_PANEL_CIRCLE_PROJECTION,
        ORIGINAL_PANEL_INFO,
        ORIGINAL_PANEL_TEMP_SCALE,
        PENDING_RESET_TARGET_VERSION,
        PERMISSIONS_BOOTSTRAP_VERSION,
        PERSISTENT_BOTTOM_BAR_OVERSCAN,
        PROJECTION_GESTURE_ENABLED,
        PROJECTION_LAST_PANEL_BOX,
        PROJECTION_LAST_REGION,
        PROJECTION_LAST_REGION_CIRCLE,
        QUICK_GESTURES_ENABLED,
        RESOURCE_OVERLAY_CORNER,
        RESOURCE_OVERLAY_FONT_SP,
        RESOURCE_OVERLAY_X,
        RESOURCE_OVERLAY_Y,
        SELF_INSTALLATION_INTEGRITY_CHECK,
        SET_STARTUP_VOLUME,
        SHOW_BETA_UPDATES,
        SPEED_ADJUSTMENT_OFFSET,
        SPEED_THRESHOLD,
        SPORTS_DISPLAY_POWER_UNIT,
        SPORT_PLUS_ENABLED,
        STARTUP_VOLUME,
        STEALTH_MODE_ACTIVE,
        STEERING_WHEEL_CUSTOM_BUTON1_ACTION_ORIGINAL,
        STEERING_WHEEL_CUSTOM_BUTON2_ACTION_ORIGINAL,
        STREAMDECK_ENABLED,
        STREAMDECK_KEY_ACTION_,
        SUNROOF_SPEED_THRESHOLD,
        THEME_RELOAD_NONCE,
        TOUR_CONCLUIDO,
        TRIP_CONSISTENCY_CLUSTER_ACTIVE,
        TRIP_CONSISTENCY_CLUSTER_SCORE,
        UPDATE_INSTALL_IN_PROGRESS,
        VIEWER_COMMANDS_ENABLED,
        VIRTUAL_CLUSTER_NIGHT_MODE,
        VIRTUAL_CLUSTER_THEME,
        VOLUME_OVERLAY_LOGO_CUSTOM_TEXT,
        VOLUME_OVERLAY_LOGO_TYPE,
        VOLUME_OVERLAY_ON_CAR_CHANGE,
    )
}
