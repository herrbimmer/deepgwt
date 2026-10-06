// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// GERADO por scripts/independencia/gerar_sinais.py — não editar à mão.
// Fontes: chaves usadas pelos apps da própria central (APKs puxados do carro) e chaves que o
// carro respondeu com valor em leituras ao vivo. Origem de cada uma: docs/independencia/sinais-fontes.tsv.
package br.com.deepgwt.veiculo

/** Uma chave do ControlService da central (ex.: `car.basic.vehicle_speed`). */
data class Sinal(@JvmField val chave: String) {
    override fun toString() = chave
}

@Suppress("unused")
object Sinais {
    @JvmField val CAR_BASIC_ACCELERATOR_PEDAL_POSITION = Sinal("car.basic.accelerator_pedal_position")
    @JvmField val CAR_BASIC_ACCUMULATED_DRIVETIME = Sinal("car.basic.accumulated_drivetime")
    @JvmField val CAR_BASIC_ACCUMULATED_ODOMETER = Sinal("car.basic.accumulated_odometer")
    @JvmField val CAR_BASIC_APP_MODE_FB = Sinal("car.basic.app_mode_fb")
    @JvmField val CAR_BASIC_AVG_FUEL_CONSUMPTION = Sinal("car.basic.avg_fuel_consumption")
    @JvmField val CAR_BASIC_AVG_VEHICLE_SPEED_SINCE_STARTUP = Sinal("car.basic.avg_vehicle_speed_since_startup")
    @JvmField val CAR_BASIC_BAROPRESSURE = Sinal("car.basic.baropressure")
    @JvmField val CAR_BASIC_BARO_PRESSURE_STATE = Sinal("car.basic.baro_pressure_state")
    @JvmField val CAR_BASIC_BATTERY_POWER_LEVEL = Sinal("car.basic.battery_power_level")
    @JvmField val CAR_BASIC_BATTERY_VOLTAGE = Sinal("car.basic.battery_voltage")
    @JvmField val CAR_BASIC_BRAKE_PEDAL = Sinal("car.basic.brake_pedal")
    @JvmField val CAR_BASIC_BRAKE_PEDAL_PRESS = Sinal("car.basic.brake_pedal_press")
    @JvmField val CAR_BASIC_CONFIGURE_CODE = Sinal("car.basic.configure_code")
    @JvmField val CAR_BASIC_COOLANT_TEMP = Sinal("car.basic.coolant_temp")
    @JvmField val CAR_BASIC_COOLANT_TEMP_LEVEL = Sinal("car.basic.coolant_temp_level")
    @JvmField val CAR_BASIC_COOLANT_TEMP_WARNING = Sinal("car.basic.coolant_temp_warning")
    @JvmField val CAR_BASIC_CUR_JOURNEY_AVG_FUEL_CONSUME = Sinal("car.basic.cur_journey_avg_fuel_consume")
    @JvmField val CAR_BASIC_CUR_JOURNEY_DRIVETIME = Sinal("car.basic.cur_journey_drivetime")
    @JvmField val CAR_BASIC_CUR_JOURNEY_ODOMETER = Sinal("car.basic.cur_journey_odometer")
    @JvmField val CAR_BASIC_DOOR_CONTROL_ACTION = Sinal("car.basic.door_control_action")
    @JvmField val CAR_BASIC_DOOR_LOCK_STATUS = Sinal("car.basic.door_lock_status")
    @JvmField val CAR_BASIC_DOOR_STATUS = Sinal("car.basic.door_status")
    @JvmField val CAR_BASIC_DRIVER_SEAT_CONTROL_ACTION = Sinal("car.basic.driver_seat_control_action")
    @JvmField val CAR_BASIC_DRIVING_READY_STATE = Sinal("car.basic.driving_ready_state")
    @JvmField val CAR_BASIC_DTC_PKI_STATE = Sinal("car.basic.dtc_pki_state")
    @JvmField val CAR_BASIC_ENGINE_OIL_LOW_PRESSURE_WARNING = Sinal("car.basic.engine_oil_low_pressure_warning")
    @JvmField val CAR_BASIC_ENGINE_SPEED = Sinal("car.basic.engine_speed")
    @JvmField val CAR_BASIC_ENGINE_STATE = Sinal("car.basic.engine_state")
    @JvmField val CAR_BASIC_EPB_AVAILABLE_STATE = Sinal("car.basic.epb_available_state")
    @JvmField val CAR_BASIC_EPB_ERROR_STATE = Sinal("car.basic.epb_error_state")
    @JvmField val CAR_BASIC_EPB_STATE = Sinal("car.basic.epb_state")
    @JvmField val CAR_BASIC_FATIGUE_WARNING = Sinal("car.basic.fatigue_warning")
    @JvmField val CAR_BASIC_FRAGRANCE_CONCENTRATION = Sinal("car.basic.fragrance_concentration")
    @JvmField val CAR_BASIC_FRAGRANCE_STATUS = Sinal("car.basic.fragrance_status")
    @JvmField val CAR_BASIC_FRAGRANCE_TYPE = Sinal("car.basic.fragrance_type")
    @JvmField val CAR_BASIC_FRONT_FOG_LIGHT_STATUS = Sinal("car.basic.front_fog_light_status")
    @JvmField val CAR_BASIC_FRONT_WIPWER_FREQUENCY = Sinal("car.basic.front_wipwer_frequency")
    @JvmField val CAR_BASIC_FRONT_WIPWER_STATUS = Sinal("car.basic.front_wipwer_status")
    @JvmField val CAR_BASIC_GEAR = Sinal("car.basic.gear")
    @JvmField val CAR_BASIC_GEAR_STATUS = Sinal("car.basic.gear_status")
    @JvmField val CAR_BASIC_HAND_BRAKE_STATUS = Sinal("car.basic.hand_brake_status")
    @JvmField val CAR_BASIC_HAZARD_LIGHT_STATUS = Sinal("car.basic.hazard_light_status")
    @JvmField val CAR_BASIC_HEAD_LIGHT_STATUS = Sinal("car.basic.head_light_status")
    @JvmField val CAR_BASIC_HIGH_BEAM_LIGHT_STATUS = Sinal("car.basic.high_beam_light_status")
    @JvmField val CAR_BASIC_INSIDE_TEMP = Sinal("car.basic.inside_temp")
    @JvmField val CAR_BASIC_INSTANT_FUEL_CONSUMPTION = Sinal("car.basic.instant_fuel_consumption")
    @JvmField val CAR_BASIC_IPK_TYPE = Sinal("car.basic.ipk_type")
    @JvmField val CAR_BASIC_KILOMETER_AVG_ELECT_CONSUMPTION = Sinal("car.basic.kilometer_avg_elect_consumption")
    @JvmField val CAR_BASIC_KILOMETER_AVG_FUEL_CONSUMPTION = Sinal("car.basic.kilometer_avg_fuel_consumption")
    @JvmField val CAR_BASIC_LEFT_TURN_INDICATOR_LIGHT_STATUS = Sinal("car.basic.left_turn_Indicator_light_status")
    @JvmField val CAR_BASIC_LEFT_TURN_LIGHT_STATUS = Sinal("car.basic.left_turn_light_status")
    @JvmField val CAR_BASIC_LEFT_TURN_SWITCH_STATUS = Sinal("car.basic.left_turn_switch_status")
    @JvmField val CAR_BASIC_LOW_BEAM_LIGHT_STATUS = Sinal("car.basic.low_beam_light_status")
    @JvmField val CAR_BASIC_LOW_LIGHT_STATUS = Sinal("car.basic.low_light_status")
    @JvmField val CAR_BASIC_MAINTENANCE_WARNING = Sinal("car.basic.maintenance_warning")
    @JvmField val CAR_BASIC_MAINTENANCE_WARNING_MILEAGE = Sinal("car.basic.maintenance_warning_mileage")
    @JvmField val CAR_BASIC_OIL_LOW_WARNING = Sinal("car.basic.oil_low_warning")
    @JvmField val CAR_BASIC_OTA_UPGRADE_MODE_ACTION = Sinal("car.basic.ota_upgrade_mode_action")
    @JvmField val CAR_BASIC_OTA_UPGRADE_MODE_STATE = Sinal("car.basic.ota_upgrade_mode_state")
    @JvmField val CAR_BASIC_OUTSIDE_TEMP = Sinal("car.basic.outside_temp")
    @JvmField val CAR_BASIC_PASSWORD_FREE_LOGIN_NOTIFY = Sinal("car.basic.password_free_login_notify")
    @JvmField val CAR_BASIC_POWER_MODE = Sinal("car.basic.power_mode")
    @JvmField val CAR_BASIC_POWER_MODE_REPAIR_MODE = Sinal("car.basic.power_mode.repair_mode")
    @JvmField val CAR_BASIC_POWER_SAVING_MODE = Sinal("car.basic.power_saving_mode")
    @JvmField val CAR_BASIC_REAR_FOG_LIGHT_STATUS = Sinal("car.basic.rear_fog_light_status")
    @JvmField val CAR_BASIC_REAR_WIPWER_STATUS = Sinal("car.basic.rear_wipwer_status")
    @JvmField val CAR_BASIC_REMAIN_FUEL_PERCENTAGE = Sinal("car.basic.remain_fuel_percentage")
    @JvmField val CAR_BASIC_REMAIN_ODOMETER = Sinal("car.basic.remain_odometer")
    @JvmField val CAR_BASIC_REMOTE_UPGRADE_STATE_ACTION = Sinal("car.basic.remote_upgrade_state_action")
    @JvmField val CAR_BASIC_RIGHT_TURN_INDICATOR_LIGHT_STATUS = Sinal("car.basic.right_turn_Indicator_light_status")
    @JvmField val CAR_BASIC_RIGHT_TURN_LIGHT_STATUS = Sinal("car.basic.right_turn_light_status")
    @JvmField val CAR_BASIC_RIGHT_TURN_SWITCH_STATUS = Sinal("car.basic.right_turn_switch_status")
    @JvmField val CAR_BASIC_SEATED_STATE = Sinal("car.basic.seated_state")
    @JvmField val CAR_BASIC_SEAT_BELT_WARNING = Sinal("car.basic.seat_belt_warning")
    @JvmField val CAR_BASIC_SPEED = Sinal("car.basic.speed")
    @JvmField val CAR_BASIC_STEERING_RESET_REMIND_ENABLE = Sinal("car.basic.steering_reset_remind_enable")
    @JvmField val CAR_BASIC_STEERING_WHEEL_ANGLE = Sinal("car.basic.steering_wheel_angle")
    @JvmField val CAR_BASIC_STEER_WHEEL_HEATING_ENABLE = Sinal("car.basic.steer_wheel_heating_enable")
    @JvmField val CAR_BASIC_SUNROOF_STATUS = Sinal("car.basic.sunroof_status")
    @JvmField val CAR_BASIC_SUNSHADE_STATUS = Sinal("car.basic.sunshade_status")
    @JvmField val CAR_BASIC_TEMP_INSIDE = Sinal("car.basic.temp_inside")
    @JvmField val CAR_BASIC_TEMP_OUTSIDE = Sinal("car.basic.temp_outside")
    @JvmField val CAR_BASIC_TIREPRESS_WARNING = Sinal("car.basic.tirepress_warning")
    @JvmField val CAR_BASIC_TIRETEMP_WARNING = Sinal("car.basic.tiretemp_warning")
    @JvmField val CAR_BASIC_TOTAL_ODOMETER = Sinal("car.basic.total_odometer")
    @JvmField val CAR_BASIC_TPMS_STATUS = Sinal("car.basic.tpms_status")
    @JvmField val CAR_BASIC_TPMS_UNITS = Sinal("car.basic.tpms_units")
    @JvmField val CAR_BASIC_TPMS_WARNING = Sinal("car.basic.tpms_warning")
    @JvmField val CAR_BASIC_TRANSMISSION_OIL_TEMP = Sinal("car.basic.transmission_oil_temp")
    @JvmField val CAR_BASIC_VEHICLE_MODEL1 = Sinal("car.basic.vehicle_model1")
    @JvmField val CAR_BASIC_VEHICLE_MODEL2 = Sinal("car.basic.vehicle_model2")
    @JvmField val CAR_BASIC_VEHICLE_SPEED = Sinal("car.basic.vehicle_speed")
    @JvmField val CAR_BASIC_VEHICLE_SPEED_SINCE_RESET = Sinal("car.basic.vehicle_speed_since_reset")
    @JvmField val CAR_BASIC_VEHICLE_VERSION = Sinal("car.basic.vehicle_version")
    @JvmField val CAR_BASIC_VIN_CODE = Sinal("car.basic.vin_code")
    @JvmField val CAR_BASIC_WINDOW_CLOSE_BY_DOOR_LOCK = Sinal("car.basic.window_close_by_door_lock")
    @JvmField val CAR_BASIC_WINDOW_CLOSE_BY_LOCK_VEHICLE = Sinal("car.basic.window_close_by_lock_vehicle")
    @JvmField val CAR_BASIC_WINDOW_CLOSE_BY_SPEED = Sinal("car.basic.window_close_by_speed")
    @JvmField val CAR_BASIC_WINDOW_CONTROL_ACTION = Sinal("car.basic.window_control_action")
    @JvmField val CAR_BASIC_WINDOW_CONTROL_ONESHOT = Sinal("car.basic.window_control_oneshot")
    @JvmField val CAR_BASIC_WINDOW_REAR_LOCKED = Sinal("car.basic.window_rear_locked")
    @JvmField val CAR_BASIC_WINDOW_SHORT_UP_DOWN_SET = Sinal("car.basic.window_short_up_down_set")
    @JvmField val CAR_BASIC_WINDOW_STATUS = Sinal("car.basic.window_status")
    @JvmField val CAR_BASIC_WIPER_STATUS = Sinal("car.basic.wiper_status")
    @JvmField val CAR_BEANTECHS_EXCEPTION_REPORT = Sinal("car.beantechs.exception_report")
    @JvmField val CAR_BEANTECHS_LICENSE_STATE = Sinal("car.beantechs.license_state")
    @JvmField val CAR_BEANTECHS_SOTA_START = Sinal("car.beantechs.sota_start")
    @JvmField val CAR_BEANTECHS_SOTA_STATUS = Sinal("car.beantechs.sota_status")
    @JvmField val CAR_BEANTECHS_VERSION = Sinal("car.beantechs.version")
    @JvmField val CAR_CAMERA_STATE = Sinal("car.camera.state")
    @JvmField val CAR_CAPACITY_VOICE_ENGINE_ACTION = Sinal("car.capacity.voice_engine_action")
    @JvmField val CAR_CHILD_MODE_DOOR_WINDOW_LOCK_STATE = Sinal("car.child_mode.door_window_lock_state")
    @JvmField val CAR_CHILD_MODE_LOCK_STATE = Sinal("car.child_mode.lock_state")
    @JvmField val CAR_CHILD_MODE_LOCK_STATE_LAMP = Sinal("car.child_mode.lock_state_lamp")
    @JvmField val CAR_CHILD_MODE_VMDR_ALARM_DELAY_ENABLE = Sinal("car.child_mode.vmdr_alarm_delay_enable")
    @JvmField val CAR_CHILD_MODE_VMDR_TMP_MONITOR_STATE = Sinal("car.child_mode.vmdr_tmp_monitor_state")
    @JvmField val CAR_COMFORT_SETTING_AUTO_OPEN_BACK_DOOR = Sinal("car.comfort_setting.auto_open_back_door")
    @JvmField val CAR_COMFORT_SETTING_CHAIR_MEMORY_AUTO_ENABLE = Sinal("car.comfort_setting.chair_memory.auto_enable")
    @JvmField val CAR_COMFORT_SETTING_CHAIR_MEM_POSN_SET_SWT = Sinal("car.comfort.setting.chair_mem_posn_set_swt")
    @JvmField val CAR_COMFORT_SETTING_CHAIR_MEM_POSN_SET_SWT_FB = Sinal("car.comfort.setting.chair_mem_posn_set_swt_fb")
    @JvmField val CAR_COMFORT_SETTING_CHAIR_MEM_POS_SET_ACTION = Sinal("car.comfort_setting.chair_mem_pos_set_action")
    @JvmField val CAR_COMFORT_SETTING_CHAIR_MEM_POS_SET_FEEDBACK = Sinal("car.comfort_setting.chair_mem_pos_set_feedback")
    @JvmField val CAR_COMFORT_SETTING_CHAIR_REST_SET_SWT = Sinal("car.comfort.setting.chair_rest_set_swt")
    @JvmField val CAR_COMFORT_SETTING_CHAIR_REST_SET_SWT_FB = Sinal("car.comfort.setting.chair_rest_set_swt_fb")
    @JvmField val CAR_COMFORT_SETTING_DRIVER_SEAT_HEATING_LEVEL = Sinal("car.comfort_setting.driver_seat_heating_level")
    @JvmField val CAR_COMFORT_SETTING_DRIVER_SEAT_MASSAGE_ENABLE = Sinal("car.comfort_setting.driver_seat_massage_enable")
    @JvmField val CAR_COMFORT_SETTING_DRIVER_SEAT_MASSAGE_LEVEL = Sinal("car.comfort_setting.driver_seat_massage_level")
    @JvmField val CAR_COMFORT_SETTING_DRIVER_SEAT_MASSAGE_MODE = Sinal("car.comfort_setting.driver_seat_massage_mode")
    @JvmField val CAR_COMFORT_SETTING_DRIVER_SEAT_SUPPORT_STATE = Sinal("car.comfort_setting.driver_seat_support_state")
    @JvmField val CAR_COMFORT_SETTING_DRIVER_SEAT_VENTILATION_LEVEL = Sinal("car.comfort_setting.driver_seat_ventilation_level")
    @JvmField val CAR_COMFORT_SETTING_DRIVE_SEAT_DIRECTION_CONFIG = Sinal("car.comfort_setting.drive_seat_direction_config")
    @JvmField val CAR_COMFORT_SETTING_ELECTRIC_SIDE_PEDAL_ENABLE = Sinal("car.comfort_setting.electric_side_pedal.enable")
    @JvmField val CAR_COMFORT_SETTING_ELECTRIC_SIDE_PEDAL_ROOF_MODE_ENABLE = Sinal("car.comfort_setting.electric_side_pedal.roof_mode_enable")
    @JvmField val CAR_COMFORT_SETTING_ELECTRIC_SIDE_PEDAL_SPECIAL_MODE_ENABLE = Sinal("car.comfort_setting.electric_side_pedal.special_mode_enable")
    @JvmField val CAR_COMFORT_SETTING_FRONT_WIPER_SENSITIVITY = Sinal("car.comfort_setting.front_wiper_sensitivity")
    @JvmField val CAR_COMFORT_SETTING_FRONT_WIPER_WORK_INTERVAL = Sinal("car.comfort_setting.front_wiper_work_interval")
    @JvmField val CAR_COMFORT_SETTING_PASSENGER_CHAIR_MEM_POS_SET = Sinal("car.comfort_setting.passenger_chair.mem_pos_set")
    @JvmField val CAR_COMFORT_SETTING_PASSENGER_SEAT_HEATING_LEVEL = Sinal("car.comfort_setting.passenger_seat_heating_level")
    @JvmField val CAR_COMFORT_SETTING_PASSENGER_SEAT_MASSAGE_ENABLE = Sinal("car.comfort_setting.passenger_seat_massage_enable")
    @JvmField val CAR_COMFORT_SETTING_PASSENGER_SEAT_MASSAGE_LEVEL = Sinal("car.comfort_setting.passenger_seat_massage_level")
    @JvmField val CAR_COMFORT_SETTING_PASSENGER_SEAT_MASSAGE_MODE = Sinal("car.comfort_setting.passenger_seat_massage_mode")
    @JvmField val CAR_COMFORT_SETTING_PASSENGER_SEAT_SUPPORT_STATE = Sinal("car.comfort_setting.passenger_seat_support_state")
    @JvmField val CAR_COMFORT_SETTING_PASSENGER_SEAT_VENTILATION_LEVEL = Sinal("car.comfort_setting.passenger_seat_ventilation_level")
    @JvmField val CAR_COMFORT_SETTING_PASS_SEAT_MASSAGE_LEVEL = Sinal("car.comfort.setting.pass_seat_massage_level")
    @JvmField val CAR_COMFORT_SETTING_REARVIEW_MIRROR_FOLDING_MODE = Sinal("car.comfort_setting.rearview_mirror_folding_mode")
    @JvmField val CAR_COMFORT_SETTING_REAR_LEFT_SEAT_HEATING_LEVEL = Sinal("car.comfort_setting.rear_left_seat_heating_level")
    @JvmField val CAR_COMFORT_SETTING_REAR_MIDDLE_SEAT_HEATING_LEVEL = Sinal("car.comfort_setting.rear_middle_seat_heating_level")
    @JvmField val CAR_COMFORT_SETTING_REAR_RIGHT_SEAT_HEATING_LEVEL = Sinal("car.comfort_setting.rear_right_seat_heating_level")
    @JvmField val CAR_COMFORT_SETTING_REAR_RIGHT_SEAT_VENTILATION_LEVEL_NINE = Sinal("car.comfort_setting.rear_right_seat_ventilation_level_nine")
    @JvmField val CAR_COMFORT_SETTING_REAR_SEAT_HEATING_MAX_LEVEL = Sinal("car.comfort_setting.rear_seat_heating_max_level")
    @JvmField val CAR_COMFORT_SETTING_REDUCE_SEAT_BELT_SLACK = Sinal("car.comfort_setting.reduce_seat_belt_slack")
    @JvmField val CAR_COMFORT_SETTING_REST_MODE_CHAIR_MEM_SET = Sinal("car.comfort_setting.rest_mode_chair_mem_set")
    @JvmField val CAR_COMFORT_SETTING_REST_MODE_ENABLE = Sinal("car.comfort_setting.rest_mode_enable")
    @JvmField val CAR_COMFORT_SETTING_SEAT_BELT_VIBRATION_WARNING = Sinal("car.comfort_setting.seat_belt_vibration_warning")
    @JvmField val CAR_COMFORT_SETTING_SEAT_HEATING_ACTION = Sinal("car.comfort_setting.seat_heating_action")
    @JvmField val CAR_COMFORT_SETTING_SEAT_HEATING_MAX_LEVEL = Sinal("car.comfort_setting.seat_heating_max_level")
    @JvmField val CAR_COMFORT_SETTING_SEAT_MASSAGE_ENABLE = Sinal("car.comfort.setting.seat_massage_enable")
    @JvmField val CAR_COMFORT_SETTING_SEAT_MASSAGE_LEVEL = Sinal("car.comfort.setting.seat_massage_level")
    @JvmField val CAR_COMFORT_SETTING_SEAT_MASSAGE_MODE = Sinal("car.comfort.setting.seat_massage_mode")
    @JvmField val CAR_COMFORT_SETTING_SEAT_VENTILATION_ACTION = Sinal("car.comfort_setting.seat_ventilation_action")
    @JvmField val CAR_COMFORT_SETTING_SEAT_VENTILATION_MAX_LEVEL = Sinal("car.comfort_setting.seat_ventilation_max_level")
    @JvmField val CAR_COMFORT_SETTING_SEAT_WAIST_DIRECTION_CONFIG = Sinal("car.comfort.setting.seat_waist_direction_config")
    @JvmField val CAR_COMFORT_SETTING_SEAT_WELCOME_CONFIG = Sinal("car.comfort_setting.seat_welcome_config")
    @JvmField val CAR_COMMAND_PARKING_SETTING_COME_GO_FREELY = Sinal("car.command_parking_setting.come_go_freely")
    @JvmField val CAR_COMMAND_PARKING_SETTING_OUTSIDE_CONTROL = Sinal("car.command_parking_setting.outside_control")
    @JvmField val CAR_COMMAND_PARKING_SETTING_RAISE_HAND_IGNITE = Sinal("car.command_parking_setting.raise_hand_ignite")
    @JvmField val CAR_CONFIGURE_ACTIVELY_ENTER_SYSTEM = Sinal("car.configure.actively_enter_system")
    @JvmField val CAR_CONFIGURE_ACTIVE_GRILLE = Sinal("car.configure.active_grille")
    @JvmField val CAR_CONFIGURE_ADAPTIVE_FRONT_LIGHTING_SYSTEM = Sinal("car.configure.adaptive_front_lighting_system")
    @JvmField val CAR_CONFIGURE_AEB_INTERSECTION_ASSIST = Sinal("car.configure.aeb_intersection_assist")
    @JvmField val CAR_CONFIGURE_AIR_BAG = Sinal("car.configure.air_bag")
    @JvmField val CAR_CONFIGURE_AIR_CONDITION_SYSTEM = Sinal("car.configure.air_condition_system")
    @JvmField val CAR_CONFIGURE_AIR_CONDITION_TYPE = Sinal("car.configure.air_condition_type")
    @JvmField val CAR_CONFIGURE_AIR_PURIFIER = Sinal("car.configure.air_purifier")
    @JvmField val CAR_CONFIGURE_AIR_QUALITY_SYSTEM = Sinal("car.configure.air_quality_system")
    @JvmField val CAR_CONFIGURE_ALTERNATOR_PROPERTIES = Sinal("car.configure.alternator_properties")
    @JvmField val CAR_CONFIGURE_AMPLIFIER = Sinal("car.configure.amplifier")
    @JvmField val CAR_CONFIGURE_ANC_SYSTEM = Sinal("car.configure.anc_system")
    @JvmField val CAR_CONFIGURE_ANTENNA = Sinal("car.configure.antenna")
    @JvmField val CAR_CONFIGURE_ASSIST_BRAKE = Sinal("car.configure.assist_brake")
    @JvmField val CAR_CONFIGURE_ASSIST_UPHILL_DOWNHILL = Sinal("car.configure.assist_uphill_downhill")
    @JvmField val CAR_CONFIGURE_ASS_MEMORY_SETTING = Sinal("car.configure.ass_memory_setting")
    @JvmField val CAR_CONFIGURE_AUDIO_PARSING_MULTICOLOR_RHYTHM_MOOD_LAMP = Sinal("car.configure.audio_parsing_multicolor_rhythm_mood_lamp")
    @JvmField val CAR_CONFIGURE_AUTO_EMERGENCY_STEERING = Sinal("car.configure.auto_emergency_steering")
    @JvmField val CAR_CONFIGURE_AUTO_HEADLIGHT = Sinal("car.configure.auto_headlight")
    @JvmField val CAR_CONFIGURE_AUTO_PARKING = Sinal("car.configure.auto_parking")
    @JvmField val CAR_CONFIGURE_AUTO_PARKING_SYSTEM = Sinal("car.configure.auto_parking_system")
    @JvmField val CAR_CONFIGURE_BACK_AIR_CONDITION_SYSTEM = Sinal("car.configure.back_air_condition_system")
    @JvmField val CAR_CONFIGURE_BATTERY_SENSOR = Sinal("car.configure.battery_sensor")
    @JvmField val CAR_CONFIGURE_BIDIRECTION_CHARGING = Sinal("car.configure.bidirection_charging")
    @JvmField val CAR_CONFIGURE_BLUETOOTH_CALL = Sinal("car.configure.bluetooth_call")
    @JvmField val CAR_CONFIGURE_BODY_DOMAIN_CONTROLLER = Sinal("car.configure.body_domain_controller")
    @JvmField val CAR_CONFIGURE_BOOSTER_BRAKE_TYPE = Sinal("car.configure.booster_brake_type")
    @JvmField val CAR_CONFIGURE_BRAKING_ENERGY_RECOVERY = Sinal("car.configure.braking_energy_recovery")
    @JvmField val CAR_CONFIGURE_BUTTION_CONTROL = Sinal("car.configure.buttion_control")
    @JvmField val CAR_CONFIGURE_CATAPULT_START = Sinal("car.configure.catapult_start")
    @JvmField val CAR_CONFIGURE_CLUSTER_TIME_DISPLAY = Sinal("car.configure.cluster_time_display")
    @JvmField val CAR_CONFIGURE_CLUSTER_TYPE = Sinal("car.configure.cluster_type")
    @JvmField val CAR_CONFIGURE_COCKPIT_COMBINATION_TYPE = Sinal("car.configure.cockpit_combination_type")
    @JvmField val CAR_CONFIGURE_COMB_FRONT_LIGHT_SRC = Sinal("car.configure.comb_front_light_src")
    @JvmField val CAR_CONFIGURE_CRUISE_CONTROL = Sinal("car.configure.cruise_control")
    @JvmField val CAR_CONFIGURE_DECK = Sinal("car.configure.deck")
    @JvmField val CAR_CONFIGURE_DEFAULT_TEMP_UNIT = Sinal("car.configure.default_temp_unit")
    @JvmField val CAR_CONFIGURE_DIFFERENTIAL_LOCK = Sinal("car.configure.differential_lock")
    @JvmField val CAR_CONFIGURE_DISCHARGE_CONNECTING_DEVICE = Sinal("car.configure.discharge_connecting_device")
    @JvmField val CAR_CONFIGURE_DOOR_OPEN_WARNING = Sinal("car.configure.door_open_warning")
    @JvmField val CAR_CONFIGURE_DOOR_UNLOCK_MODE = Sinal("car.configure.door_unlock_mode")
    @JvmField val CAR_CONFIGURE_DRIVER_SEAT = Sinal("car.configure.driver_seat")
    @JvmField val CAR_CONFIGURE_DRIVER_WAIST_SUPPORTER = Sinal("car.configure.driver_waist_supporter")
    @JvmField val CAR_CONFIGURE_DRIVER_WINDOW = Sinal("car.configure.driver_window")
    @JvmField val CAR_CONFIGURE_DRIVE_HAND_TYPE = Sinal("car.configure.drive_hand_type")
    @JvmField val CAR_CONFIGURE_DRIVE_MODE_MEMORY = Sinal("car.configure.drive_mode_memory")
    @JvmField val CAR_CONFIGURE_DRIVING_MODE2_OPTIONAL = Sinal("car.configure.driving_mode2_optional")
    @JvmField val CAR_CONFIGURE_DRIVING_MODE_OPTIONAL = Sinal("car.configure.driving_mode_optional")
    @JvmField val CAR_CONFIGURE_DRIVING_MODE_SIGNAL = Sinal("car.configure.driving_mode_signal")
    @JvmField val CAR_CONFIGURE_DROWSINESS_WARNING_SYSTEM = Sinal("car.configure.drowsiness_warning_system")
    @JvmField val CAR_CONFIGURE_DYNAMIC_DIRECTION_STAB_ASSIST = Sinal("car.configure.dynamic_direction_stab_assist")
    @JvmField val CAR_CONFIGURE_ELECTRICAL_LANGUAGE = Sinal("car.configure.electrical_language")
    @JvmField val CAR_CONFIGURE_ELECTRIC_SOUND_REMINDER = Sinal("car.configure.electric_sound_reminder")
    @JvmField val CAR_CONFIGURE_ELECTRONIC_GEAR_TYPE = Sinal("car.configure.electronic_gear_type")
    @JvmField val CAR_CONFIGURE_ELEC_ENERGY_MANAGEMENT = Sinal("car.configure.elec_energy_management")
    @JvmField val CAR_CONFIGURE_ELEC_PARKING_GEAR = Sinal("car.configure.elec_parking_gear")
    @JvmField val CAR_CONFIGURE_ELEC_STEERING_COL_LOCK = Sinal("car.configure.elec_steering_col_lock")
    @JvmField val CAR_CONFIGURE_EMERGENCY_STEERING_ASSIST = Sinal("car.configure.emergency_steering_assist")
    @JvmField val CAR_CONFIGURE_EMISSION_STANDARDS = Sinal("car.configure.emission_standards")
    @JvmField val CAR_CONFIGURE_ENERGY_TYPE = Sinal("car.configure.energy_type")
    @JvmField val CAR_CONFIGURE_ENGINE_ANTI_THEFT = Sinal("car.configure.engine_anti_theft")
    @JvmField val CAR_CONFIGURE_ENGINE_CONTROL_UNIT = Sinal("car.configure.engine_control_unit")
    @JvmField val CAR_CONFIGURE_ENGINE_MAIN_WATER_PUMP = Sinal("car.configure.engine_main_water_pump")
    @JvmField val CAR_CONFIGURE_ENHANCED_ASSIST_SYSTEM_VIBRATION_REMINDER = Sinal("car.configure.enhanced_assist_system_vibration_reminder")
    @JvmField val CAR_CONFIGURE_EV_DRIVE_ARCHITECTURE = Sinal("car.configure.ev_drive_architecture")
    @JvmField val CAR_CONFIGURE_E_AXLE = Sinal("car.configure.e_axle")
    @JvmField val CAR_CONFIGURE_FCW_AND_AEB = Sinal("car.configure.fcw_and_aeb")
    @JvmField val CAR_CONFIGURE_FORWARD_CROSS_WARNING_AND_BRAKE_SYSTEM = Sinal("car.configure.forward_cross_warning_and_brake_system")
    @JvmField val CAR_CONFIGURE_FRAGRANCE_SYSTEM = Sinal("car.configure.fragrance_system")
    @JvmField val CAR_CONFIGURE_FRONT_BUMPER = Sinal("car.configure.front_bumper")
    @JvmField val CAR_CONFIGURE_FRONT_DEAD_ANGLE_SYSTEM = Sinal("car.configure.front_dead_angle_system")
    @JvmField val CAR_CONFIGURE_FRONT_RADAR_PROBE = Sinal("car.configure.front_radar_probe")
    @JvmField val CAR_CONFIGURE_FRONT_RADAR_SENSOR = Sinal("car.configure.front_radar_sensor")
    @JvmField val CAR_CONFIGURE_FRONT_SEAT_BELT = Sinal("car.configure.front_seat_belt")
    @JvmField val CAR_CONFIGURE_FRONT_SEAT_COOLING_AND_MASSAGE = Sinal("car.configure.front_seat_cooling_and_massage")
    @JvmField val CAR_CONFIGURE_FRONT_WIPER = Sinal("car.configure.front_wiper")
    @JvmField val CAR_CONFIGURE_FUEL_TANK_SYSTEM = Sinal("car.configure.fuel_tank_system")
    @JvmField val CAR_CONFIGURE_GRILLE = Sinal("car.configure.grille")
    @JvmField val CAR_CONFIGURE_HAPTIC_REMINDER_OF_LANE_SUPPORT_SYSTEM = Sinal("car.configure.haptic_reminder_of_lane_support_system")
    @JvmField val CAR_CONFIGURE_HEADLAMP_CONTROL_FUNC = Sinal("car.configure.headlamp_control_func")
    @JvmField val CAR_CONFIGURE_HEAD_LAMP_ADJUSTMENT = Sinal("car.configure.head_lamp_adjustment")
    @JvmField val CAR_CONFIGURE_HEAD_LAMP_AUTO_OFF = Sinal("car.configure.head_lamp_auto_off")
    @JvmField val CAR_CONFIGURE_HIGHWAY_ASSIST_SYSTEM = Sinal("car.configure.highway_assist_system")
    @JvmField val CAR_CONFIGURE_HUD = Sinal("car.configure.hud")
    @JvmField val CAR_CONFIGURE_HUT_ETHERNET_PORT = Sinal("car.configure.hut_ethernet_port")
    @JvmField val CAR_CONFIGURE_IDLE_STOP_AND_GO = Sinal("car.configure.idle_stop_and_go")
    @JvmField val CAR_CONFIGURE_INDOOR_AUTO_DEFOGGING = Sinal("car.configure.Indoor_auto_defogging")
    @JvmField val CAR_CONFIGURE_INTELLIGENT_FRONT_VIEW_CONTROL = Sinal("car.configure.intelligent_front_view_control")
    @JvmField val CAR_CONFIGURE_INTELLIGENT_NETWORK_SERVICE = Sinal("car.configure.intelligent_network_service")
    @JvmField val CAR_CONFIGURE_LANE_ASSIST_SYSTEM_VIBRATION_REMINDER = Sinal("car.configure.lane_assist_system_vibration_reminder")
    @JvmField val CAR_CONFIGURE_LCA = Sinal("car.configure.lca")
    @JvmField val CAR_CONFIGURE_LDW_AND_LKA = Sinal("car.configure.ldw_and_lka")
    @JvmField val CAR_CONFIGURE_LED_ULTRA_LONG_ASSIST_HIGH_BEAM = Sinal("car.configure.led_ultra_long_assist_high_beam")
    @JvmField val CAR_CONFIGURE_LIGHT_AUTO_SWITCH_SYSTEM = Sinal("car.configure.light_auto_switch_system")
    @JvmField val CAR_CONFIGURE_MOBILE_BLUETOOTH_KEY = Sinal("car.configure.mobile_bluetooth_key")
    @JvmField val CAR_CONFIGURE_MOOD_LAMP = Sinal("car.configure.mood_lamp")
    @JvmField val CAR_CONFIGURE_MOOD_LAMP_PARTITION = Sinal("car.configure.mood_lamp_partition")
    @JvmField val CAR_CONFIGURE_MP5_SCREEN = Sinal("car.configure.mp5_screen")
    @JvmField val CAR_CONFIGURE_NAVIGATION = Sinal("car.configure.navigation")
    @JvmField val CAR_CONFIGURE_NFC = Sinal("car.configure.nfc")
    @JvmField val CAR_CONFIGURE_NIGHT_VISION_SYSTEM = Sinal("car.configure.night_vision_system")
    @JvmField val CAR_CONFIGURE_NOMINAL_TIRE_PRESSURE = Sinal("car.configure.nominal_tire_pressure")
    @JvmField val CAR_CONFIGURE_OFF_ROAD_CRUISE_CONTROL = Sinal("car.configure.off_road_cruise_control")
    @JvmField val CAR_CONFIGURE_OFF_ROAD_EXPERT_MODE = Sinal("car.configure.off_road_expert_mode")
    @JvmField val CAR_CONFIGURE_OFF_ROAD_INFO_DISPLAY = Sinal("car.configure.off_road_info_display")
    @JvmField val CAR_CONFIGURE_OMNIVEW_SYSTEM = Sinal("car.configure.omnivew_system")
    @JvmField val CAR_CONFIGURE_ONEKEY_INNER_CYCLE_MODE = Sinal("car.configure.onekey_inner_cycle_mode")
    @JvmField val CAR_CONFIGURE_OUTSIDE_TEMP_DISPLAY = Sinal("car.configure.outside_temp_display")
    @JvmField val CAR_CONFIGURE_OUTSIDE_VIEW_MIRROR = Sinal("car.configure.outside_view_mirror")
    @JvmField val CAR_CONFIGURE_PARKING_BRAKE = Sinal("car.configure.parking_brake")
    @JvmField val CAR_CONFIGURE_PARKING_LIGHT = Sinal("car.configure.parking_light")
    @JvmField val CAR_CONFIGURE_PASSENGER = Sinal("car.configure.passenger")
    @JvmField val CAR_CONFIGURE_PASS_WAIST_SUPPORTER = Sinal("car.configure.pass_waist_supporter")
    @JvmField val CAR_CONFIGURE_PASS_WINDOW = Sinal("car.configure.pass_window")
    @JvmField val CAR_CONFIGURE_PAS_AND_CTA = Sinal("car.configure.pas_and_cta")
    @JvmField val CAR_CONFIGURE_PEDAL_CONTROL = Sinal("car.configure.pedal_control")
    @JvmField val CAR_CONFIGURE_POWER_TYPE = Sinal("car.configure.power_type")
    @JvmField val CAR_CONFIGURE_PRODUCT_NAME = Sinal("car.configure.product_name")
    @JvmField val CAR_CONFIGURE_PROJECT_CODE = Sinal("car.configure.project_code")
    @JvmField val CAR_CONFIGURE_RAIN_SHELTER = Sinal("car.configure.rain_shelter")
    @JvmField val CAR_CONFIGURE_REAR_BUMPER = Sinal("car.configure.rear_bumper")
    @JvmField val CAR_CONFIGURE_REAR_COLLISION_WARNING = Sinal("car.configure.rear_collision_warning")
    @JvmField val CAR_CONFIGURE_REAR_DOOR_OPENING = Sinal("car.configure.rear_door_opening")
    @JvmField val CAR_CONFIGURE_REAR_RADAR_SENSOR = Sinal("car.configure.rear_radar_sensor")
    @JvmField val CAR_CONFIGURE_REAR_WINDOW = Sinal("car.configure.rear_window")
    @JvmField val CAR_CONFIGURE_RECORDER = Sinal("car.configure.recorder")
    @JvmField val CAR_CONFIGURE_REGIONAL = Sinal("car.configure.regional")
    @JvmField val CAR_CONFIGURE_REMOTE_CONTROL = Sinal("car.configure.remote_control")
    @JvmField val CAR_CONFIGURE_REMOTE_RESERVATION_CHARGING = Sinal("car.configure.remote_reservation_charging")
    @JvmField val CAR_CONFIGURE_REVERSE_VIDEO = Sinal("car.configure.reverse_video")
    @JvmField val CAR_CONFIGURE_ROBOT = Sinal("car.configure.robot")
    @JvmField val CAR_CONFIGURE_ROLLOVER_SENSING_CURTAIN_AIRBAG_PROTECTION = Sinal("car.configure.rollover_sensing_curtain_airbag_protection")
    @JvmField val CAR_CONFIGURE_ROTATING_SPEED_ALARM_THRESHOLD = Sinal("car.configure.rotating_speed_alarm_threshold")
    @JvmField val CAR_CONFIGURE_SALES_AREA = Sinal("car.configure.sales_area")
    @JvmField val CAR_CONFIGURE_SEAT_BELT_WARNING = Sinal("car.configure.seat_belt_warning")
    @JvmField val CAR_CONFIGURE_SEAT_CONTROL_MODULE = Sinal("car.configure.seat_control_module")
    @JvmField val CAR_CONFIGURE_SEAT_HEATING = Sinal("car.configure.seat_heating")
    @JvmField val CAR_CONFIGURE_SECOND_SEAT_COOLING_AND_MASSAGE = Sinal("car.configure.second_seat_cooling_and_massage")
    @JvmField val CAR_CONFIGURE_SHIFT_UNLOCK_KEY = Sinal("car.configure.shift_unlock_key")
    @JvmField val CAR_CONFIGURE_SHOCK_ABSORBER = Sinal("car.configure.shock_absorber")
    @JvmField val CAR_CONFIGURE_SIDE_PEDAL = Sinal("car.configure.side_pedal")
    @JvmField val CAR_CONFIGURE_SKYLIGHT_WINDOW = Sinal("car.configure.skylight_window")
    @JvmField val CAR_CONFIGURE_SMART_FUSE_BOX = Sinal("car.configure.smart_fuse_box")
    @JvmField val CAR_CONFIGURE_SPEAKER = Sinal("car.configure.speaker")
    @JvmField val CAR_CONFIGURE_SPECIAL_EXPORT_COUNTRY = Sinal("car.configure.special_export_country")
    @JvmField val CAR_CONFIGURE_SPEED_METER = Sinal("car.configure.speed_meter")
    @JvmField val CAR_CONFIGURE_STANDARD_UNIT = Sinal("car.configure.standard_unit")
    @JvmField val CAR_CONFIGURE_STARTING_SYSTEM = Sinal("car.configure.starting_system")
    @JvmField val CAR_CONFIGURE_STEERING_ASSIST = Sinal("car.configure.steering_assist")
    @JvmField val CAR_CONFIGURE_STEERING_SYSTEM = Sinal("car.configure.steering_system")
    @JvmField val CAR_CONFIGURE_STEER_WHEEL_HEATING = Sinal("car.configure.steer_wheel_heating")
    @JvmField val CAR_CONFIGURE_SUB_AIRBAG_SWITCH = Sinal("car.configure.sub_airbag_switch")
    @JvmField val CAR_CONFIGURE_SUB_INSTRUMENT_SCREEN = Sinal("car.configure.sub_instrument_screen")
    @JvmField val CAR_CONFIGURE_SUPER_LOCK = Sinal("car.configure.super_lock")
    @JvmField val CAR_CONFIGURE_TELEMATICS = Sinal("car.configure.telematics")
    @JvmField val CAR_CONFIGURE_TPMS = Sinal("car.configure.tpms")
    @JvmField val CAR_CONFIGURE_TRAFFIC_SIGN_WARNING = Sinal("car.configure.traffic_sign_warning")
    @JvmField val CAR_CONFIGURE_TRAILING_HOOK = Sinal("car.configure.trailing_hook")
    @JvmField val CAR_CONFIGURE_TRANSMISSION_ANTI_THEFT = Sinal("car.configure.transmission_anti_theft")
    @JvmField val CAR_CONFIGURE_TRANSMISSION_TYPE = Sinal("car.configure.transmission_type")
    @JvmField val CAR_CONFIGURE_VEHICLE_SPLITTER = Sinal("car.configure.vehicle_splitter")
    @JvmField val CAR_CONFIGURE_VEHICLE_TO_VEHICLE_CHARGE = Sinal("car.configure.vehicle_to_vehicle_charge")
    @JvmField val CAR_CONFIGURE_VOICE_AND_FACIAL_RECOGNITION = Sinal("car.configure.voice_and_facial_recognition")
    @JvmField val CAR_CONFIGURE_VOICE_RECOGNITON = Sinal("car.configure.voice_recogniton")
    @JvmField val CAR_CONFIGURE_WPC = Sinal("car.configure.wpc")
    @JvmField val CAR_DASHBOARD_SETTING_BRIGHTNESS_CONFIG = Sinal("car.dashboard_setting.brightness_config")
    @JvmField val CAR_DMS_CAMERA_SHELTER = Sinal("car.dms.camera_shelter")
    @JvmField val CAR_DMS_FEEDBACK_DISTRACTED_STATE = Sinal("car.dms.feedback.distracted_state")
    @JvmField val CAR_DMS_FEEDBACK_EMOTIONAL_STATE = Sinal("car.dms.feedback.emotional_state")
    @JvmField val CAR_DMS_FEEDBACK_FATIGUE_DRIVING_STATE = Sinal("car.dms.feedback.fatigue_driving_state")
    @JvmField val CAR_DMS_FRS_CONFIG_ENABLE = Sinal("car.dms.frs.config_enable")
    @JvmField val CAR_DMS_FRS_DELETE_ACTION = Sinal("car.dms.frs.delete_action")
    @JvmField val CAR_DMS_FRS_DELE_DMS_FACEDB = Sinal("car.dms.frs.dele_dms_facedb")
    @JvmField val CAR_DMS_FRS_DELE_DMS_FACEDB_RST = Sinal("car.dms.frs.dele_dms_facedb_rst")
    @JvmField val CAR_DMS_FRS_DELE_OMS_FACEDB = Sinal("car.dms.frs.dele_oms_facedb")
    @JvmField val CAR_DMS_FRS_DELE_OMS_FACEDB_RST = Sinal("car.dms.frs.dele_oms_facedb_rst")
    @JvmField val CAR_DMS_FRS_DMS_BEHAVIOR_CALL_DETECT = Sinal("car.dms.frs.dms_behavior_call_detect")
    @JvmField val CAR_DMS_FRS_DMS_BEHAVIOR_DRINK_DETECT = Sinal("car.dms.frs.dms_behavior_drink_detect")
    @JvmField val CAR_DMS_FRS_DMS_BEHAVIOR_SMOKE_DETECT = Sinal("car.dms.frs.dms_behavior_smoke_detect")
    @JvmField val CAR_DMS_FRS_DRIVER_AGE = Sinal("car.dms.frs.driver_age")
    @JvmField val CAR_DMS_FRS_DRIVER_BEHAVIOR_DETECT = Sinal("car.dms.frs.driver_behavior_detect")
    @JvmField val CAR_DMS_FRS_DRIVER_GENDER = Sinal("car.dms.frs.driver_gender")
    @JvmField val CAR_DMS_FRS_DRIVER_SIGHT_TO_MONITOR_ENABLE = Sinal("car.dms.frs.driver_sight_to_monitor_enable")
    @JvmField val CAR_DMS_FRS_DRIVER_SIGHT_TO_MONITOR_RESULT = Sinal("car.dms.frs.driver_sight_to_monitor_result")
    @JvmField val CAR_DMS_FRS_DRIVER_STAT = Sinal("car.dms.frs.driver_stat")
    @JvmField val CAR_DMS_FRS_DRIVER_STAT_STATE = Sinal("car.dms.frs.driver_stat_state")
    @JvmField val CAR_DMS_FRS_ENROLL_ACTION = Sinal("car.dms.frs.enroll_action")
    @JvmField val CAR_DMS_FRS_ENROLL_AGE = Sinal("car.dms.frs.enroll_age")
    @JvmField val CAR_DMS_FRS_ENROLL_GENDER = Sinal("car.dms.frs.enroll_gender")
    @JvmField val CAR_DMS_FRS_ENROLL_NUM = Sinal("car.dms.frs.enroll_num")
    @JvmField val CAR_DMS_FRS_GAZE_AREA = Sinal("car.dms.frs.gaze_area")
    @JvmField val CAR_DMS_FRS_IDENTIFY_ACTION = Sinal("car.dms.frs.identify_action")
    @JvmField val CAR_DMS_FRS_NOTIFY_DELTE_STATE = Sinal("car.dms.frs.notify_delte_state")
    @JvmField val CAR_DMS_FRS_NOTIFY_ENROLL_STATE = Sinal("car.dms.frs.notify_enroll_state")
    @JvmField val CAR_DMS_FRS_NOTIFY_IDENTIFY_RESULT = Sinal("car.dms.frs.notify_identify_result")
    @JvmField val CAR_DMS_FRS_NOTIFY_IDENTIFY_STATE = Sinal("car.dms.frs.notify_identify_state")
    @JvmField val CAR_DMS_INFRAREDBLOCKING_GLASSES_DETECT = Sinal("car.dms.infraredblocking_glasses_detect")
    @JvmField val CAR_DMS_WORK_STATE = Sinal("car.dms.work_state")
    @JvmField val CAR_DOOR_LOCK_SETTING_INITIATIVE_TO_ENTER = Sinal("car.door_lock_setting.initiative_to_enter")
    @JvmField val CAR_DOOR_LOCK_SETTING_INITIATIVE_TO_EXIT = Sinal("car.door_lock_setting.initiative_to_exit")
    @JvmField val CAR_DOOR_LOCK_SETTING_LOCKED_BY_SPEED = Sinal("car.door_lock_setting.locked_by_speed")
    @JvmField val CAR_DOOR_LOCK_SETTING_LOCKED_REMIND = Sinal("car.door_lock_setting.locked_remind")
    @JvmField val CAR_DOOR_LOCK_SETTING_LOCK_PROMPT = Sinal("car.door_lock_setting.lock_prompt")
    @JvmField val CAR_DOOR_LOCK_SETTING_SUPER_LOCK_ENABLE = Sinal("car.door_lock_setting.super_lock_enable")
    @JvmField val CAR_DOOR_LOCK_SETTING_UNLOCK_BY_FLAMEOUT = Sinal("car.door_lock_setting.unlock_by_flameout")
    @JvmField val CAR_DOOR_LOCK_SETTING_UNLOCK_MODE = Sinal("car.door_lock_setting.unlock_mode")
    @JvmField val CAR_DRIVE_ETC_ACTIVATION_STATUS = Sinal("car.drive.etc.activation_status")
    @JvmField val CAR_DRIVE_SETTING_ACCELERATOR_PEDAL_SENSITIVITY = Sinal("car.drive.setting.accelerator_pedal_sensitivity")
    @JvmField val CAR_DRIVE_SETTING_ASS_STATE = Sinal("car.drive_setting.ass_state")
    @JvmField val CAR_DRIVE_SETTING_BRAKE_MODE_SELECT = Sinal("car.drive_setting.brake_mode_select")
    @JvmField val CAR_DRIVE_SETTING_BRAKING_MODE = Sinal("car.drive.setting.braking_mode")
    @JvmField val CAR_DRIVE_SETTING_CREEPING_MODE_ENABLE = Sinal("car.drive.setting.creeping_mode_enable")
    @JvmField val CAR_DRIVE_SETTING_DAMPER_MODE = Sinal("car.drive.setting.damper_mode")
    @JvmField val CAR_DRIVE_SETTING_DAMPING_DRIVE_MODE = Sinal("car.drive_setting.damping_drive_mode")
    @JvmField val CAR_DRIVE_SETTING_DRIVE_MODE = Sinal("car.drive_setting.drive_mode")
    @JvmField val CAR_DRIVE_SETTING_DRIVE_MODE_MEMORY = Sinal("car.drive.setting.drive_mode_memory")
    @JvmField val CAR_DRIVE_SETTING_DRV_ASST_SWT = Sinal("car.drive_setting.drv_asst_swt")
    @JvmField val CAR_DRIVE_SETTING_DST_ENABLE = Sinal("car.drive_setting.dst_enable")
    @JvmField val CAR_DRIVE_SETTING_EPB_ENABLE_AVAILABLE = Sinal("car.drive.setting.epb_enable_available")
    @JvmField val CAR_DRIVE_SETTING_ESP_ENABLE = Sinal("car.drive_setting.esp_enable")
    @JvmField val CAR_DRIVE_SETTING_FATIGUE_MONITOR_STATE = Sinal("car.drive_setting.fatigue_monitor_state")
    @JvmField val CAR_DRIVE_SETTING_HDC_ENABLE = Sinal("car.drive.setting.hdc_enable")
    @JvmField val CAR_DRIVE_SETTING_OUTLINE_LAMPS_STATE = Sinal("car.drive.setting.outline_lamps_state")
    @JvmField val CAR_DRIVE_SETTING_OUTSIDE_VIEW_MIRROR_ASTERN_MODE = Sinal("car.drive.setting.outside_view_mirror_astern_mode")
    @JvmField val CAR_DRIVE_SETTING_OUTSIDE_VIEW_MIRROR_FOLD_STATE = Sinal("car.drive.setting.outside_view_mirror_fold_state")
    @JvmField val CAR_DRIVE_SETTING_SPEED_LIMIT = Sinal("car.drive.setting.speed_limit")
    @JvmField val CAR_DRIVE_SETTING_STEERING_WHEEL_ASSIST_MODE = Sinal("car.drive_setting.steering_wheel_assist_mode")
    @JvmField val CAR_DRIVE_SETTING_STEERING_WHEEL_CORRECTION_REMINDER_ENABLE = Sinal("car.drive_setting.steering_wheel_correction_reminder_enable")
    @JvmField val CAR_DRIVE_SETTING_TRAILER_HOOK_ACTION = Sinal("car.drive.setting.trailer_hook_action")
    @JvmField val CAR_DRIVE_SETTING_TRAILER_HOOK_STATE = Sinal("car.drive.setting.trailer_hook_state")
    @JvmField val CAR_EV_INFO_ATTENUATION_OF_BATTERY = Sinal("car.ev_info.attenuation_of_battery")
    @JvmField val CAR_EV_INFO_AVERAGE_ENERGY_CONSUME_INFO = Sinal("car.ev_info.average_energy_consume_info")
    @JvmField val CAR_EV_INFO_AVG_ENERGY_CONSUME_INFO_SINCE_RESET = Sinal("car.ev_info.avg_energy_consume_info_since_reset")
    @JvmField val CAR_EV_INFO_AVG_ENERGY_CONSUME_INFO_SINCE_STARTUP = Sinal("car.ev_info.avg_energy_consume_info_since_startup")
    @JvmField val CAR_EV_INFO_BATTERY_CHARGE_PERCENTAGE = Sinal("car.ev_info.battery_charge_percentage")
    @JvmField val CAR_EV_INFO_BATT_HEAT_RUNAWAY_NOTIFY = Sinal("car.ev_info.batt_heat_runaway_notify")
    @JvmField val CAR_EV_INFO_BMS_STATE = Sinal("car.ev_info.bms_state")
    @JvmField val CAR_EV_INFO_CHARGE_PILE_ACTION = Sinal("car.ev_info.charge_pile_action")
    @JvmField val CAR_EV_INFO_CHARGE_PILE_ACTION_REPONSE = Sinal("car.ev_info.charge_pile_action_reponse")
    @JvmField val CAR_EV_INFO_CHARGE_PILE_BROADCAST = Sinal("car.ev_info.charge_pile_broadcast")
    @JvmField val CAR_EV_INFO_CHARGE_PILE_CONNECT_STATE = Sinal("car.ev_info.charge_pile_connect_state")
    @JvmField val CAR_EV_INFO_CHARGE_PILE_ID1 = Sinal("car.ev_info.charge_pile_id1")
    @JvmField val CAR_EV_INFO_CHARGE_PILE_ID2 = Sinal("car.ev_info.charge_pile_id2")
    @JvmField val CAR_EV_INFO_CHARGE_REMAINING_TIME = Sinal("car.ev_info.charge_remaining_time")
    @JvmField val CAR_EV_INFO_CHARGING_GUN_AC_CONN_STATE = Sinal("car.ev_info.charging_gun_ac_conn_state")
    @JvmField val CAR_EV_INFO_CHARGING_GUN_CONN_STATE = Sinal("car.ev_info.charging_gun_conn_state")
    @JvmField val CAR_EV_INFO_CHARGING_STATE = Sinal("car.ev_info.charging_state")
    @JvmField val CAR_EV_INFO_CUR_BATTERY_POWER_PERCENTAGE = Sinal("car.ev_info.cur_battery_power_percentage")
    @JvmField val CAR_EV_INFO_CUR_CHARGE_CURRENT = Sinal("car.ev_info.cur_charge_current")
    @JvmField val CAR_EV_INFO_CYCLE_ENERGY_CONSUME_INFO = Sinal("car.ev_info.cycle_energy_consume_info")
    @JvmField val CAR_EV_INFO_CYCLE_FUEL_CONSUME_INFO = Sinal("car.ev_info.cycle_fuel_consume_info")
    @JvmField val CAR_EV_INFO_ECONOMIC_GUIDE_LEVEL = Sinal("car.ev_info.economic_guide_level")
    @JvmField val CAR_EV_INFO_ECONOMIC_GUIDE_RANGE = Sinal("car.ev_info.economic_guide_range")
    @JvmField val CAR_EV_INFO_ELECTRIC_MODE_REMAIN_ODOMETER = Sinal("car.ev_info.electric_mode_remain_odometer")
    @JvmField val CAR_EV_INFO_ENERGY_CONSUME_INFO = Sinal("car.ev_info.energy_consume_info")
    @JvmField val CAR_EV_INFO_ENERGY_DRIVE_STATE = Sinal("car.ev_info.energy_drive_state")
    @JvmField val CAR_EV_INFO_ENERGY_OUTPUT_PERCENTAGE = Sinal("car.ev_info.energy_output_percentage")
    @JvmField val CAR_EV_INFO_ENERGY_OUTPUT_WARNING = Sinal("car.ev_info.energy_output_warning")
    @JvmField val CAR_EV_INFO_ENERGY_RECOVERY_INFO = Sinal("car.ev_info.energy_recovery_info")
    @JvmField val CAR_EV_INFO_FAST_CHARGING_GUN_CONN_STATE = Sinal("car.ev_info.fast_charging_gun_conn_state")
    @JvmField val CAR_EV_INFO_FUEL_CONSUME_INFO = Sinal("car.ev_info.fuel_consume_info")
    @JvmField val CAR_EV_INFO_FUEL_MODE_REMAIN_ODOMETER = Sinal("car.ev_info.fuel_mode_remain_odometer")
    @JvmField val CAR_EV_INFO_HCU_POWER_TRAIN_STATE = Sinal("car.ev_info.hcu_power_train_state")
    @JvmField val CAR_EV_INFO_INSTANT_ENERGY_CONSUMPTION = Sinal("car.ev_info.Instant_energy_consumption")
    @JvmField val CAR_EV_INFO_LAST_CHARGE_TIME_ODOMETER = Sinal("car.ev_info.last_charge.time_odometer")
    @JvmField val CAR_EV_INFO_MOTOR_POWER = Sinal("car.ev_info.motor_power")
    @JvmField val CAR_EV_INFO_MOTOR_SPEED = Sinal("car.ev_info.motor_speed")
    @JvmField val CAR_EV_INFO_PHEV_AHD_VOLTAGE = Sinal("car.ev_info.phev_ahd_voltage")
    @JvmField val CAR_EV_INFO_POWER_BATTERY_CURRENT = Sinal("car.ev_info.power_battery_current")
    @JvmField val CAR_EV_INFO_POWER_BATTERY_VOLTAGE = Sinal("car.ev_info.power_battery_voltage")
    @JvmField val CAR_EV_INFO_POWER_DOWN_COMPLETE = Sinal("car.ev_info.power_down_complete")
    @JvmField val CAR_EV_INFO_POWER_VOLT_STATE = Sinal("car.ev_info.power_volt_state")
    @JvmField val CAR_EV_INFO_REAR_MOTOR_SPEED = Sinal("car.ev_info.rear_motor_speed")
    @JvmField val CAR_EV_INFO_SOC_OF_BATTERY = Sinal("car.ev_info.soc_of_battery")
    @JvmField val CAR_EV_INFO_TOTAL_ODOMETER = Sinal("car.ev_info.total_odometer")
    @JvmField val CAR_EV_INFO_VEHICLE_START_REQ = Sinal("car.ev_info.vehicle_start_req")
    @JvmField val CAR_EV_SETTING_APPOINT_CHARGE_SET = Sinal("car.ev_setting.appoint_charge_set")
    @JvmField val CAR_EV_SETTING_AUTO_CHARGE_CONFIG = Sinal("car.ev_setting.auto_charge_config")
    @JvmField val CAR_EV_SETTING_AVAS_CONFIG = Sinal("car.ev.setting.avas_config")
    @JvmField val CAR_EV_SETTING_AVAS_ENABLE = Sinal("car.ev.setting.avas_enable")
    @JvmField val CAR_EV_SETTING_BATTERY_CHARGING_INSULATION_ENABLE = Sinal("car.ev.setting.battery_charging_insulation_enable")
    @JvmField val CAR_EV_SETTING_BATTERY_CHARGING_INSULATION_TYPE = Sinal("car.ev.setting.battery_charging_insulation_type")
    @JvmField val CAR_EV_SETTING_BATTERY_PACK_AUTO_INSULATION_ENABLE = Sinal("car.ev.setting.battery_pack_auto_insulation_enable")
    @JvmField val CAR_EV_SETTING_CHARGE_ACTION = Sinal("car.ev_setting.charge_action")
    @JvmField val CAR_EV_SETTING_CHARGE_CURRENT_CONFIG = Sinal("car.ev_setting.charge_current_config")
    @JvmField val CAR_EV_SETTING_CHARGE_MODE = Sinal("car.ev_setting.charge_mode")
    @JvmField val CAR_EV_SETTING_CHARGE_SAVE_MODE_LIMIT_CONFIG = Sinal("car.ev_setting.charge_save_mode_limit_config")
    @JvmField val CAR_EV_SETTING_CHARGE_SOC_LIMIT_CONFIG = Sinal("car.ev_setting.charge_soc_limit_config")
    @JvmField val CAR_EV_SETTING_CHARGE_SOC_TARGET_CONFIG = Sinal("car.ev_setting.charge_soc_target_config")
    @JvmField val CAR_EV_SETTING_DIAGNOSE_REPAIR_MODE_ENABLE = Sinal("car.ev_setting.diagnose_repair_mode_enable")
    @JvmField val CAR_EV_SETTING_DRIVE_TIME_CONFIG = Sinal("car.ev_setting.drive_time_config")
    @JvmField val CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL = Sinal("car.ev_setting.energy_recovery_level")
    @JvmField val CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL_VALUE = Sinal("car.ev_setting.energy_recovery_level_value")
    @JvmField val CAR_EV_SETTING_ENGINE_DISCHARGE_ENABLE = Sinal("car.ev.setting.engine_discharge_enable")
    @JvmField val CAR_EV_SETTING_ENGINE_START_SETTINGS = Sinal("car.ev_setting.engine_start_settings")
    @JvmField val CAR_EV_SETTING_GMODE_GW_STATE = Sinal("car.ev.setting.gmode_gw_state")
    @JvmField val CAR_EV_SETTING_GMODE_HUT_SET = Sinal("car.ev.setting.gmode_hut_set")
    @JvmField val CAR_EV_SETTING_GMODE_HUT_STATE = Sinal("car.ev.setting.gmode_hut_state")
    @JvmField val CAR_EV_SETTING_GMODE_NOTIFY = Sinal("car.ev.setting.gmode_notify")
    @JvmField val CAR_EV_SETTING_GMODE_STATE = Sinal("car.ev.setting.gmode_state")
    @JvmField val CAR_EV_SETTING_PEDAL_CONTROL_ENABLE = Sinal("car.ev.setting.pedal_control_enable")
    @JvmField val CAR_EV_SETTING_POWER_MODEL_CONFIG = Sinal("car.ev_setting.power_model_config")
    @JvmField val CAR_EV_SETTING_POWER_RESERVE_CONFIG = Sinal("car.ev_setting.power_reserve_config")
    @JvmField val CAR_EV_SETTING_VEHICLE_TO_LOAD_DISCHARGE_ENABLE = Sinal("car.ev.setting.vehicle_to_load_discharge_enable")
    @JvmField val CAR_EV_SETTING_VEHICLE_TO_VEHICLE_DISCHARGE_ENABLE = Sinal("car.ev.setting.vehicle_to_vehicle_discharge_enable")
    @JvmField val CAR_EV_SETTING_VEHICLE_TO_VEHICLE_DISCHARGE_NOTIFY = Sinal("car.ev.setting.vehicle_to_vehicle_discharge_notify")
    @JvmField val CAR_EV_SETTING_VSG_CONFIG = Sinal("car.ev.setting.vsg_config")
    @JvmField val CAR_EV_SETTING_WADE_MODE_ENABLE = Sinal("car.ev.setting.wade_mode_enable")
    @JvmField val CAR_EV_SETTING_WASH_MODE_ENABLE = Sinal("car.ev.setting.wash_mode_enable")
    @JvmField val CAR_EV_SETTING_WPT_ENABLE = Sinal("car.ev_setting.wpt_enable")
    @JvmField val CAR_EV_SETTING_WPT_LODSTS = Sinal("car.ev_setting.wpt_lodsts")
    @JvmField val CAR_FRAGRANCE_CHANNEL_LONG_TYPE = Sinal("car.fragrance.channel_long_type")
    @JvmField val CAR_FRAGRANCE_CHANNEL_TIMEOUT_ALARM = Sinal("car.fragrance.channel_timeout_alarm")
    @JvmField val CAR_FRAGRANCE_CHANNEL_TYPE = Sinal("car.fragrance.channel_type")
    @JvmField val CAR_FRAGRANCE_CHANNEL_WORKING_STATUS = Sinal("car.fragrance.channel_working_status")
    @JvmField val CAR_FRAGRANCE_CONCENTRATION_STATUS = Sinal("car.fragrance.concentration_status")
    @JvmField val CAR_FRAGRANCE_CUR_CHANNEL_TYPE = Sinal("car.fragrance.cur_channel_type")
    @JvmField val CAR_FRAGRANCE_STATUS = Sinal("car.fragrance.status")
    @JvmField val CAR_FRS_CURRENT_OMS_SEAT_INFO = Sinal("car.frs.current_oms_seat_info")
    @JvmField val CAR_FRS_SETTING_DISTRACTION_DETECTION_ENABLE = Sinal("car.frs_setting.distraction_detection_enable")
    @JvmField val CAR_FRS_SETTING_DMS_ENABLE = Sinal("car.frs_setting.dms_enable")
    @JvmField val CAR_FRS_SETTING_DRIVER_AGE = Sinal("car.frs_setting.driver_age")
    @JvmField val CAR_FRS_SETTING_DRIVER_BEHAVIOR_DETECT = Sinal("car.frs_setting.driver_behavior_detect")
    @JvmField val CAR_FRS_SETTING_DRIVER_GENDER = Sinal("car.frs_setting.driver_gender")
    @JvmField val CAR_FRS_SETTING_EXPRESSION_RECOGNITION_ENABLE = Sinal("car.frs_setting.expression_recognition_enable")
    @JvmField val CAR_FRS_SETTING_FACIAL_DETECTION_ENABLE = Sinal("car.frs_setting.facial_detection_enable")
    @JvmField val CAR_FRS_SETTING_FATIGUE_MONITORING_ENABLE = Sinal("car.frs_setting.fatigue_monitoring_enable")
    @JvmField val CAR_FRS_SETTING_GESTURE_ENABLE = Sinal("car.frs_setting.gesture_enable")
    @JvmField val CAR_FRS_SETTING_OCCUPANT_IDENTIFICATION_SYSTEM = Sinal("car.frs_setting.occupant_identification_system")
    @JvmField val CAR_FRS_SETTING_OMS_AGE = Sinal("car.frs_setting.oms_age")
    @JvmField val CAR_FRS_SETTING_OMS_BEHAVIOR_DETECT_ENABLE = Sinal("car.frs_setting.oms_behavior_detect_enable")
    @JvmField val CAR_FRS_SETTING_OMS_CONFIG_ENABLE = Sinal("car.frs_setting.oms_config_enable")
    @JvmField val CAR_FRS_SETTING_OMS_EXIST = Sinal("car.frs_setting.oms_exist")
    @JvmField val CAR_FRS_SETTING_OMS_EXIST_STATE = Sinal("car.frs_setting.oms_exist_state")
    @JvmField val CAR_FRS_SETTING_OMS_GENDER = Sinal("car.frs_setting.oms_gender")
    @JvmField val CAR_FRS_SETTING_PASSENGER_EXPRESSION = Sinal("car.frs_setting.passenger_expression")
    @JvmField val CAR_FRS_SETTING_SCREEN_CONTROL_ENABLE = Sinal("car.frs_setting.screen_control_enable")
    @JvmField val CAR_GENERNAL_SETTING_FACTORY_RESET_ACTION = Sinal("car.genernal.setting.factory_reset_action")
    @JvmField val CAR_GENERNAL_SETTING_FACTORY_RESET_RESULT = Sinal("car.genernal.setting.factory_reset_result")
    @JvmField val CAR_GENERNAL_SETTING_ULTRASONIC_ANTI_THEFT = Sinal("car.genernal.setting.ultrasonic_anti_theft")
    @JvmField val CAR_HUD_SETTING_ADAS_DISPLAY_ENABLE = Sinal("car.hud_setting.adas_display_enable")
    @JvmField val CAR_HUD_SETTING_AUTO_BRIGHTNESS_ENABLE = Sinal("car.hud_setting.auto_brightness_enable")
    @JvmField val CAR_HUD_SETTING_BRIGHTNESS_CONFIG = Sinal("car.hud_setting.brightness_config")
    @JvmField val CAR_HUD_SETTING_BTPHONE_DISPLAY_ENABLE = Sinal("car.hud_setting.btphone_display_enable")
    @JvmField val CAR_HUD_SETTING_DISPLAY_MODE_STATE = Sinal("car.hud_setting.display_mode_state")
    @JvmField val CAR_HUD_SETTING_ENABLE_STATE = Sinal("car.hud_setting.enable_state")
    @JvmField val CAR_HUD_SETTING_ENGINE_SPEED_DISPLAY_ENABLE = Sinal("car.hud_setting.engine_speed_display_enable")
    @JvmField val CAR_HUD_SETTING_HEIGHT_CONFIG = Sinal("car.hud_setting.height_config")
    @JvmField val CAR_HUD_SETTING_MODE_CONFIG = Sinal("car.hud_setting.mode_config")
    @JvmField val CAR_HUD_SETTING_NAVIGATION_DISPLAY_ENABLE = Sinal("car.hud_setting.navigation_display_enable")
    @JvmField val CAR_HUD_SETTING_REALX_MOMENT_ENABLE = Sinal("car.hud_setting.realx_moment_enable")
    @JvmField val CAR_HUD_SETTING_ROTATION_ANGLE = Sinal("car.hud_setting.rotation_angle")
    @JvmField val CAR_HUD_SETTING_ROTATION_DIRECTION = Sinal("car.hud_setting.rotation_direction")
    @JvmField val CAR_HUD_SETTING_SNOW_MODE_ENABLE = Sinal("car.hud_setting.snow_mode_enable")
    @JvmField val CAR_HUD_SETTING_TOUCH_SCREEN_VIBRATION = Sinal("car.hud_setting.touch_screen_vibration")
    @JvmField val CAR_HUD_SETTING_VIBRATION_CORRN_ENABLE = Sinal("car.hud_setting.vibration_corrn_enable")
    @JvmField val CAR_HVAC_ACMAX_ENABLE = Sinal("car.hvac.acmax_enable")
    @JvmField val CAR_HVAC_AC_ENABLE = Sinal("car.hvac.ac_enable")
    @JvmField val CAR_HVAC_ANION_ENABLE = Sinal("car.hvac.anion_enable")
    @JvmField val CAR_HVAC_AQS_ENABLE = Sinal("car.hvac.aqs_enable")
    @JvmField val CAR_HVAC_AUTO_ENABLE = Sinal("car.hvac.auto_enable")
    @JvmField val CAR_HVAC_AUTO_LIMIT_ENABLE = Sinal("car.hvac.auto_limit_enable")
    @JvmField val CAR_HVAC_BLOWER_MODE = Sinal("car.hvac.blower_mode")
    @JvmField val CAR_HVAC_CONFIG = Sinal("car.hvac.config")
    @JvmField val CAR_HVAC_CONTROL_VIEW_DISPLAY_POSITION = Sinal("car.hvac.control_view.display_position")
    @JvmField val CAR_HVAC_CYCLE_MODE = Sinal("car.hvac.cycle_mode")
    @JvmField val CAR_HVAC_DISPLAY_STATUS = Sinal("car.hvac.display_status")
    @JvmField val CAR_HVAC_DRIVER_ELECTRIC_BLOWER_MODE = Sinal("car.hvac.driver_electric_blower_mode")
    @JvmField val CAR_HVAC_DRIVER_TEMPERATURE = Sinal("car.hvac.driver_temperature")
    @JvmField val CAR_HVAC_DRIVER_TEMP_ACTION = Sinal("car.hvac.driver_temp_action")
    @JvmField val CAR_HVAC_ELECTRIC_BLOWER_CENTER_LEFT_POSITION = Sinal("car.hvac.electric_blower_center_left_position")
    @JvmField val CAR_HVAC_ELECTRIC_BLOWER_LEFT_POSITION = Sinal("car.hvac.electric_blower_left_position")
    @JvmField val CAR_HVAC_ELECTRIC_BLOWER_RIGHT_POSITION = Sinal("car.hvac.electric_blower_right_position")
    @JvmField val CAR_HVAC_EVAPORATOR_SELF_DRYING_ENABLE = Sinal("car.hvac.evaporator_self_drying_enable")
    @JvmField val CAR_HVAC_FAN_SPEED = Sinal("car.hvac.fan_speed")
    @JvmField val CAR_HVAC_FAN_SPEED_ACTION = Sinal("car.hvac.fan_speed_action")
    @JvmField val CAR_HVAC_FAN_SPEED_RANGE = Sinal("car.hvac.fan_speed_range")
    @JvmField val CAR_HVAC_FRONT_DEFROST_ENABLE = Sinal("car.hvac.front_defrost_enable")
    @JvmField val CAR_HVAC_FRONT_TEMPERATURE_RANGE = Sinal("car.hvac.front_temperature_range")
    @JvmField val CAR_HVAC_HEATING_ENABLE = Sinal("car.hvac.heating_enable")
    @JvmField val CAR_HVAC_INTELLIGENT_SWITCH_ENABLE = Sinal("car.hvac.Intelligent_switch_enable")
    @JvmField val CAR_HVAC_INTELLIGENT_TEMPERATURE_RANGE = Sinal("car.hvac.Intelligent_temperature_range")
    @JvmField val CAR_HVAC_NATURAL_WIND_ENABLE = Sinal("car.hvac.natural_wind_enable")
    @JvmField val CAR_HVAC_NATURAL_WIND_MODE = Sinal("car.hvac.natural_wind_mode")
    @JvmField val CAR_HVAC_PANEL_DISPLAY_NOTIFY = Sinal("car.hvac.panel_display_notify")
    @JvmField val CAR_HVAC_PASSENGER_ELECTRIC_BLOWER_MODE = Sinal("car.hvac.passenger_electric_blower_mode")
    @JvmField val CAR_HVAC_PASS_TEMPERATURE = Sinal("car.hvac.pass_temperature")
    @JvmField val CAR_HVAC_PASS_TEMP_ACTION = Sinal("car.hvac.pass_temp_action")
    @JvmField val CAR_HVAC_PM2_5_VALUE = Sinal("car.hvac.pm2.5_value")
    @JvmField val CAR_HVAC_POWER_MODE = Sinal("car.hvac.power_mode")
    @JvmField val CAR_HVAC_REAR_AC_LOCK = Sinal("car.hvac.rear_ac_lock")
    @JvmField val CAR_HVAC_REAR_CONTROL_LOCK = Sinal("car.hvac.rear_control_lock")
    @JvmField val CAR_HVAC_REAR_DEFROST_ENABLE = Sinal("car.hvac.rear_defrost_enable")
    @JvmField val CAR_HVAC_REAR_FAN_SPEED = Sinal("car.hvac.rear_fan_speed")
    @JvmField val CAR_HVAC_REAR_FAN_SPEED_RANGE = Sinal("car.hvac.rear_fan_speed_range")
    @JvmField val CAR_HVAC_REAR_PANEL_DISPLAY_NOTIFY = Sinal("car.hvac.rear_panel_display_notify")
    @JvmField val CAR_HVAC_REAR_TEMPERATURE = Sinal("car.hvac.rear_temperature")
    @JvmField val CAR_HVAC_REAR_TEMPERATURE_RANGE = Sinal("car.hvac.rear_temperature_range")
    @JvmField val CAR_HVAC_REAR_TEMP_ACTION = Sinal("car.hvac.rear_temp_action")
    @JvmField val CAR_HVAC_REQUEST_HVAC_INFO = Sinal("car.hvac.request_hvac_info")
    @JvmField val CAR_HVAC_SELF_DESICCATION_ENABLE = Sinal("car.hvac.self_desiccation_enable")
    @JvmField val CAR_HVAC_SETTING_AUTO_DEFROST_ENABLE = Sinal("car.hvac.setting.auto_defrost_enable")
    @JvmField val CAR_HVAC_SETTING_COMFORT_CURVE = Sinal("car.hvac.setting.comfort_curve")
    @JvmField val CAR_HVAC_SETTING_LEFT_SIDE_OUTLET_MODE = Sinal("car.hvac.setting_left_side_outlet_mode")
    @JvmField val CAR_HVAC_SETTING_LIMIT_ENABLE = Sinal("car.hvac.setting.limit_enable")
    @JvmField val CAR_HVAC_SETTING_RIGHT_SIDE_OUTLET_MODE = Sinal("car.hvac.setting_right_side_outlet_mode")
    @JvmField val CAR_HVAC_SYNC_ENABLE = Sinal("car.hvac.sync_enable")
    @JvmField val CAR_HVAC_ULTRAVIOLET_STERILIZATION_ENABLE = Sinal("car.hvac.ultraviolet_sterilization_enable")
    @JvmField val CAR_HVAC_ZONE_ENABLE = Sinal("car.hvac.zone_enable")
    @JvmField val CAR_HVAC_ZONE_SYNC_ENABLE = Sinal("car.hvac.zone_sync_enable")
    @JvmField val CAR_INTELLIGENT_DRIVING_INFO_ACC_STATE = Sinal("car.intelligent_driving_info.acc_state")
    @JvmField val CAR_INTELLIGENT_DRIVING_INFO_AUTO_HOLD_STATE = Sinal("car.intelligent_driving_info.auto_hold_state")
    @JvmField val CAR_INTELLIGENT_DRIVING_INFO_HAZARD_BRAKE_STATE = Sinal("car.intelligent_driving_info.hazard_brake_state")
    @JvmField val CAR_INTELLIGENT_DRIVING_INFO_TJA_FOLLOW_STATE = Sinal("car.intelligent_driving_info.tja_follow_state")
    @JvmField val CAR_INTELLIGENT_DRIVING_INFO_TJA_HWA_STATE = Sinal("car.intelligent_driving_info.tja_hwa_state")
    @JvmField val CAR_INTELLIGENT_DRIVING_INFO_TJA_ICA_STATE = Sinal("car.intelligent_driving_info.tja_ica_state")
    @JvmField val CAR_INTELLIGENT_DRIVING_INFO_TTS_PLAY_SET = Sinal("car.intelligent_driving_info.tts_play_set")
    @JvmField val CAR_INTELLIGENT_DRIVING_RAIN_MODE = Sinal("car.intelligent_driving.rain_mode")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_CRUISING_SPEED_LIMIT = Sinal("car.intelligent_driving_setting.cruising_speed_limit")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_EAS_ASSIST_SENSITIVITY = Sinal("car.intelligent_driving_setting.eas.assist_sensitivity")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_EAS_CHANGE_LANE_ASSIST_STATE = Sinal("car.intelligent_driving_setting.eas.change_lane_assist_state")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_EAS_ENABLE = Sinal("car.intelligent_driving_setting.eas_enable")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_EAS_HIGHWAY_ASSIST_SYSTEM_STATE = Sinal("car.intelligent_driving_setting.eas.highway_assist_system_state")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_EAS_SETTING = Sinal("car.intelligent_driving_setting.eas_setting")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_EAS_WARNING_WAY = Sinal("car.intelligent_driving_setting.eas.warning_way")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_FAS_AUTO_EMERGENCY_TURN = Sinal("car.intelligent_driving_setting.fas.auto_emergency_turn")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_FAS_EARLY_WARNING_MODE = Sinal("car.intelligent_driving_setting.fas.early_warning_mode")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_FAS_EARLY_WARNING_SENSITIVITY = Sinal("car.intelligent_driving_setting.fas.early_warning_sensitivity")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_FAS_FRONT_CROSS_LATERAL_BRAKE = Sinal("car.intelligent_driving_setting.fas.front_cross_lateral_brake")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_FAS_FRONT_CROSS_LATERAL_WRANING = Sinal("car.intelligent_driving_setting.fas.front_cross_lateral_wraning")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_FAS_INTELLIGENTCURVE_SWITCH = Sinal("car.intelligent_driving_setting.fas.intelligentcurve_switch")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_FAS_INTERSECTION_ASSIST_STATE = Sinal("car.intelligent_driving_setting.fas.intersection_assist_state")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_FAS_PCS_STATE = Sinal("car.intelligent_driving_setting.fas.pcs_state")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_FAS_PPS_STATE = Sinal("car.intelligent_driving_setting.fas.pps_state")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_LAS_EARLY_WARNING_SENSITIVITY = Sinal("car.intelligent_driving_setting.las.early_warning_sensitivity")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_LAS_ELK_STATE = Sinal("car.intelligent_driving_setting.las.elk_state")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_LAS_ENABLE = Sinal("car.intelligent_driving_setting.las.enable")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_LAS_LCA_STATE = Sinal("car.intelligent_driving_setting.las.lca_state")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_LAS_LDW_STATE = Sinal("car.intelligent_driving_setting.las.ldw_state")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_LAS_LKA_STATE = Sinal("car.intelligent_driving_setting.las.lka_state")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_LAS_TSI_STATE = Sinal("car.intelligent_driving_setting.las.tsi_state")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_LC_MODE = Sinal("car.intelligent_driving_setting.lc_mode")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_NAV_DEC_SPEED = Sinal("car.intelligent_driving_setting.nav_dec_speed")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_NOH_AUXILIARY_NAVIGATION = Sinal("car.intelligent_driving_setting.noh_auxiliary_navigation")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_NOH_FOLLOW_NAVIGATION = Sinal("car.intelligent_driving_setting.noh_follow_navigation")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_NOH_STATUS = Sinal("car.intelligent_driving_setting.noh_status")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_OVER_SPEED_ALARM_SENSITIVITY = Sinal("car.intelligent_driving_setting.over_speed_alarm_sensitivity")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_OVER_SPEED_WARNING_STATE = Sinal("car.intelligent_driving_setting.over_speed_warning_state")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_RESET = Sinal("car.intelligent_driving_setting.reset")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_SMART_DODGE_STATE = Sinal("car.intelligent_driving_setting.smart_dodge_state")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_SRAS_ALA_STATE = Sinal("car.intelligent_driving_setting.sras.ala_state")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_SRAS_DOOR_OPEN_WARNING = Sinal("car.intelligent_driving_setting.sras.door_open_warning")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_SRAS_RCW_STATE = Sinal("car.intelligent_driving_setting.sras.rcw_state")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_SRAS_RSA_RSB_STATE = Sinal("car.intelligent_driving_setting.sras.rsa_rsb_state")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_SRAS_RSA_RSB_WARNING_STATE = Sinal("car.intelligent_driving_setting.sras.rsa_rsb_warning_state")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_SRAS_RSA_STATE = Sinal("car.intelligent_driving_setting.sras.rsa_state")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_TRAFFIC_LIGHT_CONTROL_ENABLE = Sinal("car.intelligent_driving_setting.traffic_light_control_enable")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_VEHICLE_TO_EVERYTHING_ENABLE = Sinal("car.intelligent_driving_setting.vehicle_to_everything_enable")
    @JvmField val CAR_INTELLIGENT_DRIVING_SETTING_WISDOM_CORNERING_ENABLE = Sinal("car.intelligent_driving_setting.wisdom_cornering_enable")
    @JvmField val CAR_INTELLIGENT_HAVC_PRE_COOLING = Sinal("car.intelligent.havc.pre_cooling")
    @JvmField val CAR_INTELLIGENT_NAVIGATION_AMBIENT_LIGHTING_CONTROL_ACTION = Sinal("car.intelligent.navigation.ambient_lighting_control_action")
    @JvmField val CAR_INTELLIGENT_PULSATING_AMBIENT_LIGHTING = Sinal("car.intelligent.pulsating_ambient_lighting")
    @JvmField val CAR_INTELLIGENT_VOICE_WINDOW_CONTROL_ACTION = Sinal("car.intelligent.voice.window_control_action")
    @JvmField val CAR_IPK_INFO_AEB_JA_BRK_TRIG = Sinal("car.ipk_info.aeb_ja_brk_trig")
    @JvmField val CAR_IPK_INFO_AEB_JA_TRIG = Sinal("car.ipk_info.aeb_ja_trig")
    @JvmField val CAR_IPK_INFO_AEB_PED_TRIG = Sinal("car.ipk_info.aeb_ped_trig")
    @JvmField val CAR_IPK_INFO_BSD_LCA_WARNING_REQLEFT = Sinal("car.ipk_info.bsd_lca_warning_reqleft")
    @JvmField val CAR_IPK_INFO_BSD_LCA_WARNING_REQRIGHT = Sinal("car.ipk_info.bsd_lca_warning_reqright")
    @JvmField val CAR_IPK_INFO_CLUSTER_ALARM_STATUS = Sinal("car.ipk_info.cluster_alarm_status")
    @JvmField val CAR_IPK_INFO_DOW_WARNING_REQLEFT = Sinal("car.ipk_info.dow_warning_reqleft")
    @JvmField val CAR_IPK_INFO_DOW_WARNING_REQRIGHT = Sinal("car.ipk_info.dow_warning_reqright")
    @JvmField val CAR_IPK_INFO_FCTA_WARNING = Sinal("car.ipk_info.fcta_warning")
    @JvmField val CAR_IPK_INFO_FCTB_TRIG = Sinal("car.ipk_info.fctb_trig")
    @JvmField val CAR_IPK_INFO_FCW_WARNING = Sinal("car.ipk_info.fcw_warning")
    @JvmField val CAR_IPK_INFO_WARNING_TTS_CONTENT = Sinal("car.ipk_info.warning_tts_content")
    @JvmField val CAR_IPK_INFO_WARNING_TTS_NOTIFY = Sinal("car.ipk_info.warning_tts_notify")
    @JvmField val CAR_IPK_LIGHT_ABS_INDICATOR = Sinal("car.ipk_light.abs_indicator")
    @JvmField val CAR_IPK_LIGHT_AEB_VEH_TRIG = Sinal("car.ipk_light.aeb_veh_trig")
    @JvmField val CAR_IPK_LIGHT_AIRBAG = Sinal("car.ipk_light.airbag")
    @JvmField val CAR_IPK_LIGHT_ASS_INDICATOR = Sinal("car.ipk_light.ass_indicator")
    @JvmField val CAR_IPK_LIGHT_AUTO_PARKING_INDICATOR = Sinal("car.ipk_light.auto_parking_indicator")
    @JvmField val CAR_IPK_LIGHT_BATTERY_CHARGE_INDICATOR = Sinal("car.ipk_light.battery_charge_indicator")
    @JvmField val CAR_IPK_LIGHT_BATTERY_CUT_OFF_INDICATOR = Sinal("car.ipk_light.battery_cut_off_indicator")
    @JvmField val CAR_IPK_LIGHT_BRAKE_ENERGE_RECYCLE = Sinal("car.ipk_light.brake_energe_recycle")
    @JvmField val CAR_IPK_LIGHT_BRAKING_SYSTEM_INDICATOR = Sinal("car.ipk_light.braking_system_indicator")
    @JvmField val CAR_IPK_LIGHT_BSD_INDICATOR = Sinal("car.ipk_light.bsd_indicator")
    @JvmField val CAR_IPK_LIGHT_CCO_INDICATOR = Sinal("car.ipk_light.cco_indicator")
    @JvmField val CAR_IPK_LIGHT_CHARGE_SYSTEM_INDICATOR = Sinal("car.ipk_light.charge_system_indicator")
    @JvmField val CAR_IPK_LIGHT_DOOR_WARNING = Sinal("car.ipk_light.door_warning")
    @JvmField val CAR_IPK_LIGHT_ENGINE_MALFUNCTION = Sinal("car.ipk_light.engine_malfunction")
    @JvmField val CAR_IPK_LIGHT_ENGINE_OIL_LOW_PRESSURE_WARNING = Sinal("car.ipk_light.engine_oil_low_pressure_warning")
    @JvmField val CAR_IPK_LIGHT_ENGINE_SERVICE = Sinal("car.ipk_light.engine_service")
    @JvmField val CAR_IPK_LIGHT_ENGINE_WATER_TEMP_HIGH = Sinal("car.ipk_light.engine_water_temp_high")
    @JvmField val CAR_IPK_LIGHT_EPARK_INDICATOR = Sinal("car.ipk_light.epark_indicator")
    @JvmField val CAR_IPK_LIGHT_EPS_ALERT = Sinal("car.ipk_light.eps_alert")
    @JvmField val CAR_IPK_LIGHT_EPS_OFF = Sinal("car.ipk_light.eps_off")
    @JvmField val CAR_IPK_LIGHT_ESP_INDICATOR = Sinal("car.ipk_light.esp_indicator")
    @JvmField val CAR_IPK_LIGHT_ETC_ACTIVE_STATE = Sinal("car.ipk_light.etc_active_state")
    @JvmField val CAR_IPK_LIGHT_ETC_TAMPER_STATE = Sinal("car.ipk_light.etc_tamper_state")
    @JvmField val CAR_IPK_LIGHT_FCW_INDICATOR = Sinal("car.ipk_light.fcw_indicator")
    @JvmField val CAR_IPK_LIGHT_FRONT_DIFFERENTIAL_LOCK_INDICATOR = Sinal("car.ipk_light.front_differential_lock_indicator")
    @JvmField val CAR_IPK_LIGHT_FUEL_LOW = Sinal("car.ipk_light.fuel_low")
    @JvmField val CAR_IPK_LIGHT_FUEL_LOW_LMPSTS = Sinal("car.ipk_light.fuel_low_lmpsts")
    @JvmField val CAR_IPK_LIGHT_GLOBAL_ALARM_INDICATOR = Sinal("car.ipk_light.global_alarm_indicator")
    @JvmField val CAR_IPK_LIGHT_HYBRID_POWER_SYSTEM = Sinal("car.ipk_light.hybrid_power_system")
    @JvmField val CAR_IPK_LIGHT_LDW_INDICATOR = Sinal("car.ipk_light.ldw_indicator")
    @JvmField val CAR_IPK_LIGHT_LIMIT_120 = Sinal("car.ipk_light.limit_120")
    @JvmField val CAR_IPK_LIGHT_LKA_INDICATOR = Sinal("car.ipk_light.lka_indicator")
    @JvmField val CAR_IPK_LIGHT_MOTOR_MALFUNCTION = Sinal("car.ipk_light.motor_malfunction")
    @JvmField val CAR_IPK_LIGHT_PARKING_ALARM_INDICATOR = Sinal("car.ipk_light.parking_alarm_indicator")
    @JvmField val CAR_IPK_LIGHT_POWER_BATTERY_ALARM_INDICATOR = Sinal("car.ipk_light.power_battery_alarm_indicator")
    @JvmField val CAR_IPK_LIGHT_POWER_BATTERY_LOW = Sinal("car.ipk_light.power_battery_low")
    @JvmField val CAR_IPK_LIGHT_REAR_DIFFERENTIAL_LOCK_INDICATOR = Sinal("car.ipk_light.rear_differential_lock_indicator")
    @JvmField val CAR_IPK_LIGHT_REDUCE_POWER_INDICATOR = Sinal("car.ipk_light.reduce_power_indicator")
    @JvmField val CAR_IPK_LIGHT_SEAT_BELT_WARNING_INDICATOR = Sinal("car.ipk_light.seat_belt_warning_indicator")
    @JvmField val CAR_IPK_LIGHT_SOC_LOW_WARN = Sinal("car.ipk_light.soc_low_warn")
    @JvmField val CAR_IPK_LIGHT_SOUND_PROMPT_SYSTEM = Sinal("car.ipk_light.sound_prompt_system")
    @JvmField val CAR_IPK_LIGHT_TAB_INDICATOR = Sinal("car.ipk_light.tab_indicator")
    @JvmField val CAR_IPK_LIGHT_TPMS_WARNING = Sinal("car.ipk_light.tpms_warning")
    @JvmField val CAR_IPK_LIGHT_TRANSMISSION_OVER_HEAT = Sinal("car.ipk_light.transmission_over_heat")
    @JvmField val CAR_IPK_LIGHT_VCU_READY_STATE = Sinal("car.ipk_light.vcu_ready_state")
    @JvmField val CAR_IPK_SETTING_ALARM_HIS_INFO_LIST = Sinal("car.ipk_setting.alarm_his_info_list")
    @JvmField val CAR_IPK_SETTING_BRIGHTNESS_CONFIG = Sinal("car.ipk_setting.brightness_config")
    @JvmField val CAR_IPK_SETTING_BRIGHTNESS_RANGE = Sinal("car.ipk_setting.brightness_range")
    @JvmField val CAR_IPK_SETTING_CAST_SCREEN_READY_STATE = Sinal("car.ipk_setting.cast_screen_ready_state")
    @JvmField val CAR_IPK_SETTING_DRIVE_INFO_RESET = Sinal("car.ipk_setting.drive_info_reset")
    @JvmField val CAR_IPK_SETTING_DRIVE_INFO_RESET_STATE = Sinal("car.ipk_setting.drive_info_reset_state")
    @JvmField val CAR_IPK_SETTING_ENGINE_SPEEDING_LIMIT_ALARM = Sinal("car.ipk.setting.engine_speeding_limit_alarm")
    @JvmField val CAR_IPK_SETTING_ENGINE_SPEED_UI_STATE = Sinal("car.ipk.setting.engine_speed_ui_state")
    @JvmField val CAR_IPK_SETTING_MODE_CONFIG = Sinal("car.ipk_setting.mode_config")
    @JvmField val CAR_IPK_SETTING_THEME_CONFIG = Sinal("car.ipk_setting.theme_config")
    @JvmField val CAR_LIGHT_SETTING_ABS_LIGHT_ENABLE = Sinal("car.light_setting.abs_light_enable")
    @JvmField val CAR_LIGHT_SETTING_AMBIENT_LIGHT_AEB_FOLLOW = Sinal("car.light_setting.ambient_light.aeb_follow")
    @JvmField val CAR_LIGHT_SETTING_AMBIENT_LIGHT_AREA_DRIVER_DOOR_SWITCH = Sinal("car.light_setting.ambient_light.area_driver_door_switch")
    @JvmField val CAR_LIGHT_SETTING_AMBIENT_LIGHT_AREA_FOOT_SWITCH = Sinal("car.light_setting.ambient_light.area_foot_switch")
    @JvmField val CAR_LIGHT_SETTING_AMBIENT_LIGHT_AREA_LEFT_PANEL_SWITCH = Sinal("car.light_setting.ambient_light.area_left_panel_switch")
    @JvmField val CAR_LIGHT_SETTING_AMBIENT_LIGHT_AREA_PASS_DOOR_SWITCH = Sinal("car.light_setting.ambient_light.area_pass_door_switch")
    @JvmField val CAR_LIGHT_SETTING_AMBIENT_LIGHT_AREA_RIGHT_PANEL_SWITCH = Sinal("car.light_setting.ambient_light.area_right_panel_switch")
    @JvmField val CAR_LIGHT_SETTING_AMBIENT_LIGHT_AREA_RIGHT_REAR_DOOR_SWITCH = Sinal("car.light_setting.ambient_light.area_right_rear_door_switch")
    @JvmField val CAR_LIGHT_SETTING_AMBIENT_LIGHT_AREA_ROOF_SWITCH = Sinal("car.light_setting.ambient_light.area_roof_switch")
    @JvmField val CAR_LIGHT_SETTING_AMBIENT_LIGHT_BREATHING_MODE_SWITCH = Sinal("car.light_setting.ambient_light.breathing_mode_switch")
    @JvmField val CAR_LIGHT_SETTING_AMBIENT_LIGHT_BRIGHTNESS = Sinal("car.light_setting.ambient_light.brightness")
    @JvmField val CAR_LIGHT_SETTING_AMBIENT_LIGHT_DOW_SYNC = Sinal("car.light_setting.ambient_light.dow_sync")
    @JvmField val CAR_LIGHT_SETTING_AMBIENT_LIGHT_DYNAMIC_MODE = Sinal("car.light_setting.ambient_light.dynamic_mode")
    @JvmField val CAR_LIGHT_SETTING_AMBIENT_LIGHT_ENABLE = Sinal("car.light_setting.ambient_light.enable")
    @JvmField val CAR_LIGHT_SETTING_AMBIENT_LIGHT_FCTA_FCTB_SYNC = Sinal("car.light_setting.ambient_light.fcta_fctb_sync")
    @JvmField val CAR_LIGHT_SETTING_AMBIENT_LIGHT_FCW_SYNC = Sinal("car.light_setting.ambient_light.fcw_sync")
    @JvmField val CAR_LIGHT_SETTING_AMBIENT_LIGHT_FLOW_WATER_SWITCH = Sinal("car.light_setting.ambient_light.flow_water_switch")
    @JvmField val CAR_LIGHT_SETTING_AMBIENT_LIGHT_INTELLIGENT_LINKAGE = Sinal("car.light_setting.ambient_light.Intelligent_linkage")
    @JvmField val CAR_LIGHT_SETTING_AMBIENT_LIGHT_LCA_SYNC = Sinal("car.light_setting.ambient_light.lca_sync")
    @JvmField val CAR_LIGHT_SETTING_AMBIENT_LIGHT_MULTICOLOR_COLOR_CONFIG = Sinal("car.light_setting.ambient_light.multicolor_color_config")
    @JvmField val CAR_LIGHT_SETTING_AMBIENT_LIGHT_MULTICOLOR_DYNAMIC_CONFIG = Sinal("car.light_setting.ambient_light.multicolor_dynamic_config")
    @JvmField val CAR_LIGHT_SETTING_AMBIENT_LIGHT_MULTICOLOR_STATIC_CONFIG = Sinal("car.light_setting.ambient_light.multicolor_static_config")
    @JvmField val CAR_LIGHT_SETTING_AMBIENT_LIGHT_MUSIC_FOLLOW = Sinal("car.light_setting.ambient_light.music_follow")
    @JvmField val CAR_LIGHT_SETTING_AMBIENT_LIGHT_OLE_MULTICOLOR_COLOR_CONFIG = Sinal("car.light_setting.ambient_light_ole.multicolor_color_config")
    @JvmField val CAR_LIGHT_SETTING_AMBIENT_LIGHT_OLE_MULTICOLOR_DYNAMIC_CONFIG = Sinal("car.light_setting.ambient_light_ole.multicolor_dynamic_config")
    @JvmField val CAR_LIGHT_SETTING_AMBIENT_LIGHT_OLE_MULTICOLOR_STATIC_CONFIG = Sinal("car.light_setting.ambient_light_ole.multicolor_static_config")
    @JvmField val CAR_LIGHT_SETTING_AMBIENT_LIGHT_RHYTHMIC_SWITCH = Sinal("car.light_setting.ambient_light.rhythmic_switch")
    @JvmField val CAR_LIGHT_SETTING_AMBIENT_LIGHT_SCENE_SELECTION = Sinal("car.light_setting.ambient_light.scene_selection")
    @JvmField val CAR_LIGHT_SETTING_AMBIENT_LIGHT_SYNC_AMP_MAIN_VOL = Sinal("car.light_setting.ambient_light.sync_amp_main_vol")
    @JvmField val CAR_LIGHT_SETTING_AMBIENT_LIGHT_SYNC_AMP_MUTE = Sinal("car.light_setting.ambient_light.sync_amp_mute")
    @JvmField val CAR_LIGHT_SETTING_AMBIENT_LIGHT_SYNC_MUSIC_FREQ = Sinal("car.light_setting.ambient_light.sync_music_freq")
    @JvmField val CAR_LIGHT_SETTING_DRIVER_AMBIENT_LIGHT_ENABLE = Sinal("car.light_setting.driver_ambient_light.enable")
    @JvmField val CAR_LIGHT_SETTING_EB_TURN_LIGHT_ENABLE = Sinal("car.light_setting.eb_turn_light_enable")
    @JvmField val CAR_LIGHT_SETTING_FIND_CAR_MODE_LIGHT = Sinal("car.light_setting.find_car_mode_light")
    @JvmField val CAR_LIGHT_SETTING_FOLLOW_HOME_DELAY_TIME = Sinal("car.light_setting.follow_home_delay_time")
    @JvmField val CAR_LIGHT_SETTING_FREIGHT_LIGHT_ENABLE = Sinal("car.light_setting.freight_light_enable")
    @JvmField val CAR_LIGHT_SETTING_HIGH_LOW_BEAM_AUTO_ENABLE = Sinal("car.light_setting.high_low_beam_auto_enable")
    @JvmField val CAR_LIGHT_SETTING_LOW_BEAM_HEIGHT = Sinal("car.light_setting.low_beam_height")
    @JvmField val CAR_LIGHT_SETTING_LOW_BEAM_HIGH = Sinal("car.light_setting.low_beam_high")
    @JvmField val CAR_LIGHT_SETTING_OLE_AMBIENT_LIGHT_BREATHING_MODE_SWITCH = Sinal("car.light_setting.ole_ambient_light.breathing_mode_switch")
    @JvmField val CAR_LIGHT_SETTING_OLE_AMBIENT_LIGHT_DYNAMIC_MODE = Sinal("car.light_setting.ole_ambient_light.dynamic_mode")
    @JvmField val CAR_LIGHT_SETTING_OLE_AMBIENT_LIGHT_FLOW_MODE_SWITCH = Sinal("car.light_setting.ole_ambient_light.flow_mode_switch")
    @JvmField val CAR_LIGHT_SETTING_OLE_AMBIENT_LIGHT_MULTICOLOR_COLOR_CONFIG = Sinal("car.light_setting.ole_ambient_light.multicolor_color_config")
    @JvmField val CAR_LIGHT_SETTING_OLE_AMBIENT_LIGHT_MULTICOLOR_STATIC_CONFIG = Sinal("car.light_setting.ole_ambient_light.multicolor_static_config")
    @JvmField val CAR_LIGHT_SETTING_OLE_AMBIENT_LIGHT_MUSIC_FOLLOW = Sinal("car.light_setting.ole_ambient_light.music_follow")
    @JvmField val CAR_LIGHT_SETTING_PARKING_LIGHT_CONFIG = Sinal("car.light_setting.parking_light_config")
    @JvmField val CAR_LIGHT_SETTING_PASSENGER_AMBIENT_LIGHT_ENABLE = Sinal("car.light_setting.passenger_ambient_light.enable")
    @JvmField val CAR_LIGHT_SETTING_POWER_SAVED_MODE = Sinal("car.light_setting.power_saved_mode")
    @JvmField val CAR_LIGHT_SETTING_REAR_FOG_LAMP_ENABLE = Sinal("car.light_setting.rear_fog_lamp_enable")
    @JvmField val CAR_LIGHT_SETTING_REAR_ROW_AMBIENT_LIGHT_ENABLE = Sinal("car.light_setting.rear_row_ambient_light.enable")
    @JvmField val CAR_LIGHT_SETTING_SPORT_MODE_LIGHT = Sinal("car.light_setting.sport_mode_light")
    @JvmField val CAR_LIGHT_SETTING_WELCOME_LIGHT_CONFIG = Sinal("car.light_setting.welcome_light_config")
    @JvmField val CAR_LIGHT_SETTING_WELCOME_LIGHT_DELAY_TIME = Sinal("car.light_setting.welcome_light_delay_time")
    @JvmField val CAR_MAP_TSR_NAV_CONTRY_TYPE = Sinal("car.map.tsr.nav_contry_type")
    @JvmField val CAR_MAP_TSR_NAV_ROAD_TYPE = Sinal("car.map.tsr.nav_road_type")
    @JvmField val CAR_MAP_TSR_NAV_SPEED_LIMIT = Sinal("car.map.tsr.nav_speed_limit")
    @JvmField val CAR_MAP_TSR_NAV_SPEED_LIMIT_SIGN_STATUS = Sinal("car.map.tsr.nav_speed_limit_sign_status")
    @JvmField val CAR_MAP_TSR_NAV_SPEED_LIMIT_TYPE = Sinal("car.map.tsr.nav_speed_limit_type")
    @JvmField val CAR_MAP_TSR_NAV_TO_TRAFFIC_EYE_DISTANCE = Sinal("car.map.tsr.nav_to_traffic_eye_distance")
    @JvmField val CAR_MAP_TSR_NAV_TRAFIC_SIGN = Sinal("car.map.tsr.nav_trafic_sign")
    @JvmField val CAR_OFF_ROAD_SETTING_ALL_TERRAIN_MEMORY = Sinal("car.off_road_setting.all_terrain_memory")
    @JvmField val CAR_OFF_ROAD_SETTING_COO_ENABLE = Sinal("car.off_road_setting.coo_enable")
    @JvmField val CAR_OFF_ROAD_SETTING_DRIVE_MODE_CONFIG = Sinal("car.off_road_setting.drive_mode_config")
    @JvmField val CAR_OFF_ROAD_SETTING_EXPERT_MODE_DRIVE_MODE = Sinal("car.off_road_setting.expert_mode_drive_mode")
    @JvmField val CAR_OFF_ROAD_SETTING_EXPERT_MODE_ENABLE = Sinal("car.off_road_setting.expert_mode_enable")
    @JvmField val CAR_OFF_ROAD_SETTING_EXPERT_MODE_ESC = Sinal("car.off_road_setting.expert_mode_esc")
    @JvmField val CAR_OFF_ROAD_SETTING_EXPERT_MODE_FWD = Sinal("car.off_road_setting.expert_mode_fwd")
    @JvmField val CAR_OFF_ROAD_SETTING_TAB_AVAILABLE = Sinal("car.off_road_setting.tab_available")
    @JvmField val CAR_OFF_ROAD_SETTING_TAB_EFFECT_DISPLAY = Sinal("car.off_road_setting.tab_effect_display")
    @JvmField val CAR_OFF_ROAD_SETTING_TAB_ENABLE = Sinal("car.off_road_setting.tab_enable")
    @JvmField val CAR_OMS_AGE_RECOGNITION_ENABLE = Sinal("car.oms.age_recognition_enable")
    @JvmField val CAR_OMS_BACK_HOME_BY_GESTURE_ENABLE = Sinal("car.oms.back_home_by_gesture_enable")
    @JvmField val CAR_OMS_CALL_THE_FUSION = Sinal("car.oms.call_the_fusion")
    @JvmField val CAR_OMS_EXPRESSION_RECOGNITION_ENABLE = Sinal("car.oms.expression_recognition_enable")
    @JvmField val CAR_OMS_FEEDBACK_AGE_STATE = Sinal("car.oms.feedback.age_state")
    @JvmField val CAR_OMS_FEEDBACK_EXPRESSION_STATE = Sinal("car.oms.feedback.expression_state")
    @JvmField val CAR_OMS_FEEDBACK_GENDER_STATE = Sinal("car.oms.feedback.gender_state")
    @JvmField val CAR_OMS_FRS = Sinal("car.oms.frs")
    @JvmField val CAR_OMS_FRS_CODRIVER_EXIST = Sinal("car.oms.frs.codriver_exist")
    @JvmField val CAR_OMS_FRS_CODRIVER_EXPRESSION = Sinal("car.oms.frs.codriver_expression")
    @JvmField val CAR_OMS_FRS_DYNAMIC_GESTURE_STATE = Sinal("car.oms.frs.dynamic_gesture_state")
    @JvmField val CAR_OMS_FRS_OMS_BEHAVIOR_CALL = Sinal("car.oms.frs.oms_behavior_call")
    @JvmField val CAR_OMS_FRS_OMS_BEHAVIOR_DRINK = Sinal("car.oms.frs.oms_behavior_drink")
    @JvmField val CAR_OMS_FRS_OMS_BEHAVIOR_SMOKE = Sinal("car.oms.frs.oms_behavior_smoke")
    @JvmField val CAR_OMS_FRS_OMS_CURRENT_STATE = Sinal("car.oms.frs.oms_current_state")
    @JvmField val CAR_OMS_FRS_OMS_FACEDB = Sinal("car.oms.frs.oms_facedb")
    @JvmField val CAR_OMS_FRS_OMS_FACEDB_NUM = Sinal("car.oms.frs.oms_facedb_num")
    @JvmField val CAR_OMS_FRS_OMS_NUMBER = Sinal("car.oms.frs.oms_number")
    @JvmField val CAR_OMS_FRS_PASSENGERL_ROW1_AGE = Sinal("car.oms.frs.passengerL_row1_age")
    @JvmField val CAR_OMS_FRS_PASSENGERL_ROW1_EXIST = Sinal("car.oms.frs.passengerL_row1_exist")
    @JvmField val CAR_OMS_FRS_PASSENGERL_ROW1_EXPRESSION = Sinal("car.oms.frs.passengerL_row1_expression")
    @JvmField val CAR_OMS_FRS_PASSENGERL_ROW1_GENDER = Sinal("car.oms.frs.passengerL_row1_gender")
    @JvmField val CAR_OMS_FRS_PASSENGERM_ROW1_AGE = Sinal("car.oms.frs.passengerM_row1_age")
    @JvmField val CAR_OMS_FRS_PASSENGERM_ROW1_EXIST = Sinal("car.oms.frs.passengerM_row1_exist")
    @JvmField val CAR_OMS_FRS_PASSENGERM_ROW1_EXPRESSION = Sinal("car.oms.frs.passengerM_row1_expression")
    @JvmField val CAR_OMS_FRS_PASSENGERM_ROW1_GENDER = Sinal("car.oms.frs.passengerM_row1_gender")
    @JvmField val CAR_OMS_FRS_PASSENGERR_ROW1_AGE = Sinal("car.oms.frs.passengerR_row1_age")
    @JvmField val CAR_OMS_FRS_PASSENGERR_ROW1_EXIST = Sinal("car.oms.frs.passengerR_row1_exist")
    @JvmField val CAR_OMS_FRS_PASSENGERR_ROW1_EXPRESSION = Sinal("car.oms.frs.passengerR_row1_expression")
    @JvmField val CAR_OMS_FRS_PASSENGERR_ROW1_GENDER = Sinal("car.oms.frs.passengerR_row1_gender")
    @JvmField val CAR_OMS_FRS_SEAT_STAFF_INFO = Sinal("car.oms.frs.seat_staff_info")
    @JvmField val CAR_OMS_FRS_STATIC_GESTURE_STATE = Sinal("car.oms.frs.static_gesture_state")
    @JvmField val CAR_OMS_GENDER_RECOGNITION_ENABLE = Sinal("car.oms.gender_recognition_enable")
    @JvmField val CAR_PARKING_SETTING_APA_FLANKING_PROTECTION = Sinal("car.parking_setting.apa.flanking_protection")
    @JvmField val CAR_PARKING_SETTING_AUTO_PARK_ACTION = Sinal("car.parking_setting.auto_park_action")
    @JvmField val CAR_PARKING_SETTING_AUTO_PARK_ENABLE = Sinal("car.parking_setting.auto_park_enable")
    @JvmField val CAR_PARKING_SETTING_AUTO_PARK_MODE_SELECT = Sinal("car.parking_setting.auto_park_mode_select")
    @JvmField val CAR_PARKING_SETTING_AUTO_PARK_OUT_DIRECTIONS = Sinal("car.parking_setting.auto_park_out_directions")
    @JvmField val CAR_PARKING_SETTING_AUTO_PARK_SOLT_NUM_SELECT = Sinal("car.parking_setting.auto_park_solt_num_select")
    @JvmField val CAR_PARKING_SETTING_AUTO_STEERING_ENABLE = Sinal("car.parking_setting.auto_steering_enable")
    @JvmField val CAR_PARKING_SETTING_AUTO_TURN_LIGHT = Sinal("car.parking_setting.auto_turn_light")
    @JvmField val CAR_PARKING_SETTING_AVM_AUTO_EXIT_SPEED_CONFIG = Sinal("car.parking_setting.avm_auto_exit_speed_config")
    @JvmField val CAR_PARKING_SETTING_AVM_SETTING_AVM_AUTO_ACTIVE = Sinal("car.parking_setting.avm_setting.avm_auto_active")
    @JvmField val CAR_PARKING_SETTING_AVM_SETTING_FACTORY_RESET_ENABLE = Sinal("car.parking_setting.avm_setting.factory_reset_enable")
    @JvmField val CAR_PARKING_SETTING_AVM_SETTING_ROAD_CALIBRATION_ENABLE = Sinal("car.parking_setting.avm_setting.road_calibration_enable")
    @JvmField val CAR_PARKING_SETTING_FPAS_ENABLE = Sinal("car.parking_setting.fpas_enable")
    @JvmField val CAR_PARKING_SETTING_RADAR_ALARM_SOUND_CONFIG = Sinal("car.parking_setting.radar_alarm_sound_config")
    @JvmField val CAR_PARKING_SETTING_RADAR_ALARM_SOUND_ENABLE = Sinal("car.parking_setting.radar_alarm_sound_enable")
    @JvmField val CAR_PARKING_SETTING_RADAR_SETTING_FRONT_RADAR_AUTO_TURN_ON = Sinal("car.parking_setting.radar_setting.front_radar_auto_turn_on")
    @JvmField val CAR_PARKING_SETTING_RADAR_SETTING_MEB_STATE = Sinal("car.parking_setting.radar_setting.meb_state")
    @JvmField val CAR_UNIT_SETTING_CLUSTER_SYSTEM = Sinal("car.unit_setting.cluster_system")
    @JvmField val CAR_UNIT_SETTING_ODOMETER = Sinal("car.unit_setting.odometer")
    @JvmField val CAR_WIRELESS_CHARGE_SETTING_CHARGE_STATE = Sinal("car.wireless_charge_setting.charge_state")
    @JvmField val CAR_WIRELESS_CHARGE_SETTING_CONFIGURE_ENABLE = Sinal("car.wireless_charge_setting.configure_enable")
    @JvmField val CAR_WIRELESS_CHARGE_SETTING_VOICE_PROMPT = Sinal("car.wireless_charge_setting.voice_prompt")
    @JvmField val SYS_AUTOLINK_PASSENGER_DISPLAYID = Sinal("sys.autolink.passenger.displayid")
    @JvmField val SYS_AVM_AUTO_PREVIEW_ENABLE = Sinal("sys.avm.auto_preview_enable")
    @JvmField val SYS_AVM_CAR_MOVE_FORWARD_MODE_ENABLE = Sinal("sys.avm.car_move_forward_mode_enable")
    @JvmField val SYS_AVM_CLEAN_CAR_MODE_ENABLE = Sinal("sys.avm.clean_car_mode_enable")
    @JvmField val SYS_AVM_DEVICE_SUPPORT = Sinal("sys.avm.device_support")
    @JvmField val SYS_AVM_FPAS_MODE_ENABLE = Sinal("sys.avm.fpas_mode_enable")
    @JvmField val SYS_AVM_MEMORY_PARKING_MODE_ENABLE = Sinal("sys.avm.memory_parking_mode_enable")
    @JvmField val SYS_AVM_MOD_MODE_ENABLE = Sinal("sys.avm.mod_mode_enable")
    @JvmField val SYS_AVM_MOD_MODE_STATE = Sinal("sys.avm.mod_mode_state")
    @JvmField val SYS_AVM_PARKING_ASSIST_MODE_ENABLE = Sinal("sys.avm.parking_assist_mode_enable")
    @JvmField val SYS_AVM_PARKING_ASSIST_MODE_STATE = Sinal("sys.avm.parking_assist_mode_state")
    @JvmField val SYS_AVM_PREVIEW_CONTROL_ACTION = Sinal("sys.avm.preview_control_action")
    @JvmField val SYS_AVM_PREVIEW_MODE_STATE = Sinal("sys.avm.preview_mode_state")
    @JvmField val SYS_AVM_PREVIEW_STATUS = Sinal("sys.avm.preview_status")
    @JvmField val SYS_AVM_PREVIEW_VEHICLE_TRANSPARENT_STATE = Sinal("sys.avm.preview_vehicle_transparent_state")
    @JvmField val SYS_AVM_RADAR_VIEW_MODE = Sinal("sys.avm.radar_view_mode")
    @JvmField val SYS_AVM_RIGHT_ASSIST_WARN = Sinal("sys.avm.right_assist_warn")
    @JvmField val SYS_AVM_SYSTEM_CHANNEL_STATE = Sinal("sys.avm.system_channel_state")
    @JvmField val SYS_AVM_VIEW_MODE_STATE = Sinal("sys.avm.view_mode_state")
    @JvmField val SYS_BASIC_AUDIO_SOURCE_APP = Sinal("sys.basic.audio.source_app")
    @JvmField val SYS_BASIC_HW_VERSION = Sinal("sys.basic.hw_version")
    @JvmField val SYS_BASIC_IVI_POWER_MODE = Sinal("sys.basic.ivi_power_mode")
    @JvmField val SYS_BASIC_MCU_VERSION = Sinal("sys.basic.mcu_version")
    @JvmField val SYS_BASIC_MIC_REQUEST_ACTION = Sinal("sys.basic.mic.request_action")
    @JvmField val SYS_BASIC_OS_VERSION = Sinal("sys.basic.os_version")
    @JvmField val SYS_BASIC_POWER_MODE = Sinal("sys.basic.power_mode")
    @JvmField val SYS_BASIC_PROJECT_ID = Sinal("sys.basic.project_id")
    @JvmField val SYS_BASIC_PROJECT_NAME = Sinal("sys.basic.project_name")
    @JvmField val SYS_BASIC_SN_CODE = Sinal("sys.basic.sn_code")
    @JvmField val SYS_BASIC_TSP_ENV_TYPE = Sinal("sys.basic.tsp_env_type")
    @JvmField val SYS_BEANTECHS_ACCOUNT_FEELLESS_ID = Sinal("sys.beantechs.account.feelless.id")
    @JvmField val SYS_BEAN_ACCOUNT_MODE = Sinal("sys.bean.account.mode")
    @JvmField val SYS_BEAN_BOOTANIMATION_COMPLETE = Sinal("sys.bean.bootAnimation_complete")
    @JvmField val SYS_BEAN_BRIGHTNESS_BAR_ENABLE = Sinal("sys.bean.brightness_bar_enable")
    @JvmField val SYS_BEAN_DATATRACK_ENABLE = Sinal("sys.bean.datatrack.enable")
    @JvmField val SYS_BEAN_GUIDE_COMPLETE = Sinal("sys.bean.guide_complete")
    @JvmField val SYS_BEAN_GUIDE_COMPLETE_TIME = Sinal("sys.bean.guide_complete_time")
    @JvmField val SYS_BEAN_GUIDE_STATE = Sinal("sys.bean.guide_state")
    @JvmField val SYS_BEAN_JOURNEY_ID = Sinal("sys.bean.journey.id")
    @JvmField val SYS_BEAN_REMOTE_UPGRADE_STATE = Sinal("sys.bean.remote_upgrade_state")
    @JvmField val SYS_BEAN_VOLUME_BAR_ENABLE = Sinal("sys.bean.volume_bar_enable")
    @JvmField val SYS_BLUETOOTH_A2DP_CONNECTED_DEVICE_INFO = Sinal("sys.bluetooth.a2dp_connected_device_info")
    @JvmField val SYS_BLUETOOTH_A2DP_CONNECTION_STATE = Sinal("sys.bluetooth.a2dp_connection_state")
    @JvmField val SYS_BLUETOOTH_AVRCP_CONNECTED_DEVICE_INFO = Sinal("sys.bluetooth.avrcp_connected_device_info")
    @JvmField val SYS_BLUETOOTH_AVRCP_CONNECTION_STATE = Sinal("sys.bluetooth.avrcp_connection_state")
    @JvmField val SYS_BLUETOOTH_AVRCP_FOCUS_STATE = Sinal("sys.bluetooth.avrcp_focus_state")
    @JvmField val SYS_BLUETOOTH_AVRCP_MUSIC_INFO = Sinal("sys.bluetooth.avrcp_music_info")
    @JvmField val SYS_BLUETOOTH_AVRCP_PLAY_ACTION = Sinal("sys.bluetooth.avrcp_play_action")
    @JvmField val SYS_BLUETOOTH_AVRCP_PLAY_STATE = Sinal("sys.bluetooth.avrcp_play_state")
    @JvmField val SYS_BLUETOOTH_HFP_CONNECTION_STATE = Sinal("sys.bluetooth.hfp_connection_state")
    @JvmField val SYS_BLUETOOTH_LOCAL_DEVICE_STATE = Sinal("sys.bluetooth.local_device_state")
    @JvmField val SYS_BLUETOOTH_PBAP_CONNECTION_STATE = Sinal("sys.bluetooth.pbap_connection_state")
    @JvmField val SYS_BYD_COUNTRYCODE = Sinal("sys.byd.countrycode")
    @JvmField val SYS_DEBUG_STORAGE_SLOW = Sinal("sys.debug.storage_slow")
    @JvmField val SYS_DEVICE_EXTERNAL_IMSI = Sinal("sys.device.external_imsi")
    @JvmField val SYS_DVR_DPC_ADDP_CONNECTED_STATE = Sinal("sys.dvr.dpc_addp_connected_state")
    @JvmField val SYS_DVR_EMERGENCY_RESCUE_VIDEO_UPLOADED_ENABLE = Sinal("sys.dvr.emergency_rescue_video_uploaded_enable")
    @JvmField val SYS_DVR_REMOTE_SWITCH = Sinal("sys.dvr.remote_switch")
    @JvmField val SYS_DVR_REMOTE_SWITCH_STATE = Sinal("sys.dvr.remote_switch_state")
    @JvmField val SYS_FFLAG_OVERRIDE = Sinal("sys.fflag.override")
    @JvmField val SYS_GESTURE_EVENT_NOTIFY = Sinal("sys.gesture.event_notify")
    @JvmField val SYS_HAP_CAMERA_RIGHT_ASSIST_WARN = Sinal("sys.hap.camera.right_assist_warn")
    @JvmField val SYS_HAP_RADAR_RIGHT_ASSIST_WARN = Sinal("sys.hap.radar.right_assist_warn")
    @JvmField val SYS_KEY_LEFT = Sinal("sys.key.left")
    @JvmField val SYS_KEY_RIGHT = Sinal("sys.key.right")
    @JvmField val SYS_NETWORK_DATA_NETWORK_ENABLE = Sinal("sys.network.data_network_enable")
    @JvmField val SYS_NETWORK_HOTSPOT_CLIENT_NUM = Sinal("sys.network.hotspot_client_num")
    @JvmField val SYS_NETWORK_HOTSPOT_STATE = Sinal("sys.network.hotspot_state")
    @JvmField val SYS_NETWORK_MOBILE_NETWORK_SIGNAL_LEVEL = Sinal("sys.network.mobile_network_signal_level")
    @JvmField val SYS_NETWORK_MOBILE_NETWORK_TYPE = Sinal("sys.network.mobile_network_type")
    @JvmField val SYS_NETWORK_SIM_ICCID = Sinal("sys.network.sim_iccid")
    @JvmField val SYS_NETWORK_SIM_IMSI = Sinal("sys.network.sim_imsi")
    @JvmField val SYS_NETWORK_SUPPORT_ANDROID_DATA_NETWORK_INTERFACE = Sinal("sys.network.support_android_data_network_interface")
    @JvmField val SYS_NETWORK_SUPPORT_ANDROID_HOTSPOT_INTERFACE = Sinal("sys.network.support_android_hotspot_interface")
    @JvmField val SYS_NETWORK_TBOX_CONNECTION_STATE = Sinal("sys.network.tbox_connection_state")
    @JvmField val SYS_NETWORK_TBOX_HARDWARE_VERSION = Sinal("sys.network.tbox.hardware_version")
    @JvmField val SYS_NETWORK_TBOX_SN_CODE = Sinal("sys.network.tbox.sn_code")
    @JvmField val SYS_NETWORK_TBOX_SOFEWARE_VERSION = Sinal("sys.network.tbox.sofeware_version")
    @JvmField val SYS_OTHER_KEYEVENT_NOTIFY = Sinal("sys.other.keyevent_notify")
    @JvmField val SYS_OTHER_REQUEST_EVENT_SYNC = Sinal("sys.other.request_event_sync")
    @JvmField val SYS_OTHER_REQUEST_UPDATE_AUDIO_ACOUSTICS_ACTION = Sinal("sys.other.request_update_audio_acoustics_action")
    @JvmField val SYS_PKI_HTTP_CODE = Sinal("sys.pki.http.code")
    @JvmField val SYS_RADIO_AM_FAVORITES_STATION_LIST = Sinal("sys.radio.am_favorites_station_list")
    @JvmField val SYS_RADIO_AM_VALID_STATION_LIST = Sinal("sys.radio.am_valid_station_list")
    @JvmField val SYS_RADIO_CUR_CHANNEL_INFO = Sinal("sys.radio.cur_channel_info")
    @JvmField val SYS_RADIO_DRM_FAVORITES_STATION_LIST = Sinal("sys.radio.drm_favorites_station_list")
    @JvmField val SYS_RADIO_DRM_VALID_STATION_LIST = Sinal("sys.radio.drm_valid_station_list")
    @JvmField val SYS_RADIO_FAVORITE_CUR_STATION_ACTION = Sinal("sys.radio.favorite_cur_station_action")
    @JvmField val SYS_RADIO_FM_FAVORITES_STATION_LIST = Sinal("sys.radio.fm_favorites_station_list")
    @JvmField val SYS_RADIO_FM_VALID_STATION_LIST = Sinal("sys.radio.fm_valid_station_list")
    @JvmField val SYS_RADIO_PLAY_CONTROL_ACTION = Sinal("sys.radio.play_control_action")
    @JvmField val SYS_RADIO_PLAY_STATE = Sinal("sys.radio.play_state")
    @JvmField val SYS_RADIO_RDS_AM_FAVORITE_STATION_LIST = Sinal("sys.radio.rds_am_favorite_station_list")
    @JvmField val SYS_RADIO_RDS_AM_VALID_STATION_LIST = Sinal("sys.radio.rds_am_valid_station_list")
    @JvmField val SYS_RADIO_RDS_CUR_CHANNEL_INFO = Sinal("sys.radio.rds_cur_channel_info")
    @JvmField val SYS_RADIO_RDS_DAB_ALTERNATIVE_FREQUENCY_STATE = Sinal("sys.radio.rds_dab_alternative_frequency_state")
    @JvmField val SYS_RADIO_RDS_DAB_FAVORITE_STATION_LIST = Sinal("sys.radio.rds_dab_favorite_station_list")
    @JvmField val SYS_RADIO_RDS_DAB_RESET_ALL_STATE = Sinal("sys.radio.rds_dab_reset_all_state")
    @JvmField val SYS_RADIO_RDS_DAB_VALID_STATION_LIST = Sinal("sys.radio.rds_dab_valid_station_list")
    @JvmField val SYS_RADIO_RDS_FAVORITE_CUR_STATION_ACTION = Sinal("sys.radio.rds_favorite_cur_station_action")
    @JvmField val SYS_RADIO_RDS_FM_ALTERNATIVE_FREQUENCY_STATE = Sinal("sys.radio.rds_fm_alternative_frequency_state")
    @JvmField val SYS_RADIO_RDS_FM_FAVORITE_STATION_LIST = Sinal("sys.radio.rds_fm_favorite_station_list")
    @JvmField val SYS_RADIO_RDS_FM_VALID_STATION_LIST = Sinal("sys.radio.rds_fm_valid_station_list")
    @JvmField val SYS_RADIO_RDS_REGIONAL_INFO = Sinal("sys.radio.rds_regional_info")
    @JvmField val SYS_RADIO_RDS_TRAFFIC_ANNOUNCEMENT_ACTIVE_STATE = Sinal("sys.radio.rds_traffic_announcement_active_state")
    @JvmField val SYS_RADIO_RDS_TRAFFIC_ANNOUNCEMENT_STATE = Sinal("sys.radio.rds_traffic_announcement_state")
    @JvmField val SYS_RADIO_RDS_TRAFFIC_PROGRAM_STATE = Sinal("sys.radio.rds_traffic_program_state")
    @JvmField val SYS_RADIO_SEARCH_PROGRESS = Sinal("sys.radio.search_progress")
    @JvmField val SYS_RADIO_SEARCH_STATE = Sinal("sys.radio.search_state")
    @JvmField val SYS_RVC_DEVICE_SUPPORT = Sinal("sys.rvc.device_support")
    @JvmField val SYS_RVC_FPAS_ENABLE = Sinal("sys.rvc.fpas_enable")
    @JvmField val SYS_RVC_MEB_ENABLE = Sinal("sys.rvc.meb_enable")
    @JvmField val SYS_RVC_RADAR_SYSTEM_CHANNEL_STATE = Sinal("sys.rvc.radar_system_channel_state")
    @JvmField val SYS_RVC_SYSTEM_CHANNEL_STATE = Sinal("sys.rvc.system_channel_state")
    @JvmField val SYS_RVC_TRAILER_MODE_ENABLE = Sinal("sys.rvc.trailer_mode_enable")
    @JvmField val SYS_RVC_VIEW_MODE = Sinal("sys.rvc.view_mode")
    @JvmField val SYS_SETTINGS_AUDIOCHAN_MODE = Sinal("sys.settings.audiochan.mode")
    @JvmField val SYS_SETTINGS_AUDIO_ANC_ENABLE = Sinal("sys.settings.audio.anc_enable")
    @JvmField val SYS_SETTINGS_AUDIO_BOOT_MUSIC_SOUND_ENABLE = Sinal("sys.settings.audio.boot_music_sound_enable")
    @JvmField val SYS_SETTINGS_AUDIO_DRIVER_SOUND_VIBRATION_LEVEL = Sinal("sys.settings.audio.driver_sound_vibration_level")
    @JvmField val SYS_SETTINGS_AUDIO_DRIVESEAT_RHYTHM_LEVEL = Sinal("sys.settings.audio.driveseat_rhythm_level")
    @JvmField val SYS_SETTINGS_AUDIO_EQ_ALTO_VALUE = Sinal("sys.settings.audio.eq_alto_value")
    @JvmField val SYS_SETTINGS_AUDIO_EQ_BASS_VALUE = Sinal("sys.settings.audio.eq_bass_value")
    @JvmField val SYS_SETTINGS_AUDIO_EQ_TREBLE_VALUE = Sinal("sys.settings.audio.eq_treble_value")
    @JvmField val SYS_SETTINGS_AUDIO_EQ_VALUE_RANGE = Sinal("sys.settings.audio.eq_value_range")
    @JvmField val SYS_SETTINGS_AUDIO_EXTERNAL_AMP_ENABLE = Sinal("sys.settings.audio.external_amp_enable")
    @JvmField val SYS_SETTINGS_AUDIO_MEDIA_MUTE_STATE = Sinal("sys.settings.audio.media_mute_state")
    @JvmField val SYS_SETTINGS_AUDIO_MEDIA_VOLUME = Sinal("sys.settings.audio.media_volume")
    @JvmField val SYS_SETTINGS_AUDIO_MEDIA_VOLUME_RANGE = Sinal("sys.settings.audio.media_volume_range")
    @JvmField val SYS_SETTINGS_AUDIO_MUTE_ADJUST_ACTION = Sinal("sys.settings.audio.mute_adjust_action")
    @JvmField val SYS_SETTINGS_AUDIO_MUTE_STATE = Sinal("sys.settings.audio.mute_state")
    @JvmField val SYS_SETTINGS_AUDIO_NAVI_MUTE_STATE = Sinal("sys.settings.audio.navi_mute_state")
    @JvmField val SYS_SETTINGS_AUDIO_NAVI_TTS_MIX_MEDIA_ENABLE = Sinal("sys.settings.audio.navi_tts_mix_media_enable")
    @JvmField val SYS_SETTINGS_AUDIO_NAVI_VOLUME = Sinal("sys.settings.audio.navi_volume")
    @JvmField val SYS_SETTINGS_AUDIO_NAVI_VOLUME_RANGE = Sinal("sys.settings.audio.navi_volume_range")
    @JvmField val SYS_SETTINGS_AUDIO_PASSENGERSEAT_RHYTHM_LEVEL = Sinal("sys.settings.audio.passengerseat_rhythm_level")
    @JvmField val SYS_SETTINGS_AUDIO_PASSENGER_HEADSET_MUTE_STATE = Sinal("sys.settings.audio.passenger_headset_mute_state")
    @JvmField val SYS_SETTINGS_AUDIO_PHONE_VOLUME = Sinal("sys.settings.audio.phone_volume")
    @JvmField val SYS_SETTINGS_AUDIO_PHONE_VOLUME_RANGE = Sinal("sys.settings.audio.phone_volume_range")
    @JvmField val SYS_SETTINGS_AUDIO_PRECISEBASS_STATE = Sinal("sys.settings.audio.precisebass_state")
    @JvmField val SYS_SETTINGS_AUDIO_RING_MUTE_STATE = Sinal("sys.settings.audio.ring_mute_state")
    @JvmField val SYS_SETTINGS_AUDIO_RING_VOLUME = Sinal("sys.settings.audio.ring_volume")
    @JvmField val SYS_SETTINGS_AUDIO_RING_VOLUME_RANGE = Sinal("sys.settings.audio.ring_volume_range")
    @JvmField val SYS_SETTINGS_AUDIO_SOUND_EFFECT_DTS_STATE = Sinal("sys.settings.audio.sound_effect_dts_state")
    @JvmField val SYS_SETTINGS_AUDIO_SOUND_FIELD_VALUE = Sinal("sys.settings.audio.sound_field_value")
    @JvmField val SYS_SETTINGS_AUDIO_SOUND_FIELD_VALUE_RANGE = Sinal("sys.settings.audio.sound_field_value_range")
    @JvmField val SYS_SETTINGS_AUDIO_SOUND_MODE_SWITCH = Sinal("sys.settings.audio.sound_mode_switch")
    @JvmField val SYS_SETTINGS_AUDIO_SOUND_PRECISE_BASS = Sinal("sys.settings.audio.sound_precise_bass")
    @JvmField val SYS_SETTINGS_AUDIO_SOUND_QUALITY_RESTORE_ENABLE = Sinal("sys.settings.audio.sound_quality_restore_enable")
    @JvmField val SYS_SETTINGS_AUDIO_SOUND_SURROUND_ASK = Sinal("sys.settings.audio.sound_surround_ask")
    @JvmField val SYS_SETTINGS_AUDIO_SOUND_SURROUND_ENABLE = Sinal("sys.settings.audio.sound_surround_enable")
    @JvmField val SYS_SETTINGS_AUDIO_SOUND_SURROUND_HARMAN_KARDON = Sinal("sys.settings.audio.sound_surround_harman_kardon")
    @JvmField val SYS_SETTINGS_AUDIO_SOUND_SURROUND_MODE = Sinal("sys.settings.audio.sound_surround_mode")
    @JvmField val SYS_SETTINGS_AUDIO_SYSTEM_ACOUSTICS_ENABLE = Sinal("sys.settings.audio.system_acoustics_enable")
    @JvmField val SYS_SETTINGS_AUDIO_TOUCH_TONE_ENABLE = Sinal("sys.settings.audio.touch_tone_enable")
    @JvmField val SYS_SETTINGS_AUDIO_VEHICLE_ALARM_ENABLE = Sinal("sys.settings.audio.vehicle_alarm_enable")
    @JvmField val SYS_SETTINGS_AUDIO_VEHICLE_WARNING_TTS_ENABLE = Sinal("sys.settings.audio.vehicle_warning_tts_enable")
    @JvmField val SYS_SETTINGS_AUDIO_VOICE_MUTE_STATE = Sinal("sys.settings.audio.voice_mute_state")
    @JvmField val SYS_SETTINGS_AUDIO_VOICE_VOLUME = Sinal("sys.settings.audio.voice_volume")
    @JvmField val SYS_SETTINGS_AUDIO_VOICE_VOLUME_RANGE = Sinal("sys.settings.audio.voice_volume_range")
    @JvmField val SYS_SETTINGS_AUDIO_VOLUME_ADAPTATION_ENABLE = Sinal("sys.settings.audio.volume_adaptation_enable")
    @JvmField val SYS_SETTINGS_AUDIO_VOLUME_ADJUST_ACTION = Sinal("sys.settings.audio.volume_adjust_action")
    @JvmField val SYS_SETTINGS_AUDIO_VOLUME_ADJUST_BY_SPEED_STATE = Sinal("sys.settings.audio.volume_adjust_by_speed_state")
    @JvmField val SYS_SETTINGS_AUDIO_VOLUME_ADJUST_BY_WIND_STATE = Sinal("sys.settings.audio.volume_adjust_by_wind_state")
    @JvmField val SYS_SETTINGS_CLUSTER_VIEW_DISPLAY = Sinal("sys.settings.cluster_view_display")
    @JvmField val SYS_SETTINGS_DISPLAY_AUTO_BRIGHTNESS_ENABLE = Sinal("sys.settings.display.auto_brightness_enable")
    @JvmField val SYS_SETTINGS_DISPLAY_BACKLIGHT_STATE = Sinal("sys.settings.display.backlight_state")
    @JvmField val SYS_SETTINGS_DISPLAY_BRIGHTNESS_LEVEL = Sinal("sys.settings.display.brightness_level")
    @JvmField val SYS_SETTINGS_DISPLAY_BRIGHTNESS_RANGE = Sinal("sys.settings.display.brightness_range")
    @JvmField val SYS_SETTINGS_DISPLAY_PASSENGER_BRIGHTNESS_LEVEL = Sinal("sys.settings.display_passenger.brightness_level")
    @JvmField val SYS_SETTINGS_DISPLAY_VEHICLE_BACKLIGHT = Sinal("sys.settings.display.vehicle_backlight")
    @JvmField val SYS_SETTINGS_EXT_BLE_VOLUME = Sinal("sys.settings.ext_ble_volume")
    @JvmField val SYS_SETTINGS_EXT_BLE_VOLUME_MUTE = Sinal("sys.settings.ext_ble_volume_mute")
    @JvmField val SYS_SETTINGS_GENERAL_FACTORY_RESET_ACTION = Sinal("sys.settings.general.factory_reset_action")
    @JvmField val SYS_SETTINGS_GENERAL_RESET_ALL_ACTION = Sinal("sys.settings.general.reset_all_action")
    @JvmField val SYS_SETTINGS_GENERAL_RESET_ALL_STATE = Sinal("sys.settings.general.reset_all_state")
    @JvmField val SYS_SETTINGS_GENERAL_TIME_ZONE_SYNCH = Sinal("sys.settings.general.time_zone_synch")
    @JvmField val SYS_SETTINGS_LANGUAGE = Sinal("sys.settings.language")
    @JvmField val SYS_STEM_CELLS_BOOT_TRIGGERS = Sinal("sys.stem_cells.boot_triggers")
    @JvmField val SYS_TBOX_CALL_ACTION = Sinal("sys.tbox.call_action")
    @JvmField val SYS_TBOX_CUR_CALL_INFO = Sinal("sys.tbox.cur_call_info")
    @JvmField val SYS_TEST_ID1 = Sinal("sys.test.id1")
    @JvmField val SYS_TEST_ID2 = Sinal("sys.test.id2")
    @JvmField val SYS_TEST_ID3 = Sinal("sys.test.id3")
    @JvmField val SYS_TEST_ID4 = Sinal("sys.test.id4")
    @JvmField val SYS_TEST_PROJECT_CODE = Sinal("sys.test.project.code")
    @JvmField val SYS_USB_MTP_DEVICE_TYPE = Sinal("sys.usb.mtp.device_type")
    @JvmField val SYS_WIFI_DRIVER_VERSION = Sinal("sys.wifi.driver.version")
    @JvmField val SYS_WIFI_FIRMWARE_VERSION = Sinal("sys.wifi.firmware.version")
    @JvmField val SYS_WIFI_HARDWARE_VERSION = Sinal("sys.wifi.hardware.version")
    @JvmField val SYS_WIFI_MAC = Sinal("sys.wifi.mac")
    @JvmField val SYS_WIFI_SOFTAP_INTERFACE_NAME = Sinal("sys.wifi.softap_interface_name")

    /** As 1014 chaves conhecidas, em ordem alfabética. */
    @JvmField val TODOS: List<Sinal> = listOf(
        CAR_BASIC_ACCELERATOR_PEDAL_POSITION,
        CAR_BASIC_ACCUMULATED_DRIVETIME,
        CAR_BASIC_ACCUMULATED_ODOMETER,
        CAR_BASIC_APP_MODE_FB,
        CAR_BASIC_AVG_FUEL_CONSUMPTION,
        CAR_BASIC_AVG_VEHICLE_SPEED_SINCE_STARTUP,
        CAR_BASIC_BARO_PRESSURE_STATE,
        CAR_BASIC_BAROPRESSURE,
        CAR_BASIC_BATTERY_POWER_LEVEL,
        CAR_BASIC_BATTERY_VOLTAGE,
        CAR_BASIC_BRAKE_PEDAL,
        CAR_BASIC_BRAKE_PEDAL_PRESS,
        CAR_BASIC_CONFIGURE_CODE,
        CAR_BASIC_COOLANT_TEMP,
        CAR_BASIC_COOLANT_TEMP_LEVEL,
        CAR_BASIC_COOLANT_TEMP_WARNING,
        CAR_BASIC_CUR_JOURNEY_AVG_FUEL_CONSUME,
        CAR_BASIC_CUR_JOURNEY_DRIVETIME,
        CAR_BASIC_CUR_JOURNEY_ODOMETER,
        CAR_BASIC_DOOR_CONTROL_ACTION,
        CAR_BASIC_DOOR_LOCK_STATUS,
        CAR_BASIC_DOOR_STATUS,
        CAR_BASIC_DRIVER_SEAT_CONTROL_ACTION,
        CAR_BASIC_DRIVING_READY_STATE,
        CAR_BASIC_DTC_PKI_STATE,
        CAR_BASIC_ENGINE_OIL_LOW_PRESSURE_WARNING,
        CAR_BASIC_ENGINE_SPEED,
        CAR_BASIC_ENGINE_STATE,
        CAR_BASIC_EPB_AVAILABLE_STATE,
        CAR_BASIC_EPB_ERROR_STATE,
        CAR_BASIC_EPB_STATE,
        CAR_BASIC_FATIGUE_WARNING,
        CAR_BASIC_FRAGRANCE_CONCENTRATION,
        CAR_BASIC_FRAGRANCE_STATUS,
        CAR_BASIC_FRAGRANCE_TYPE,
        CAR_BASIC_FRONT_FOG_LIGHT_STATUS,
        CAR_BASIC_FRONT_WIPWER_FREQUENCY,
        CAR_BASIC_FRONT_WIPWER_STATUS,
        CAR_BASIC_GEAR,
        CAR_BASIC_GEAR_STATUS,
        CAR_BASIC_HAND_BRAKE_STATUS,
        CAR_BASIC_HAZARD_LIGHT_STATUS,
        CAR_BASIC_HEAD_LIGHT_STATUS,
        CAR_BASIC_HIGH_BEAM_LIGHT_STATUS,
        CAR_BASIC_INSIDE_TEMP,
        CAR_BASIC_INSTANT_FUEL_CONSUMPTION,
        CAR_BASIC_IPK_TYPE,
        CAR_BASIC_KILOMETER_AVG_ELECT_CONSUMPTION,
        CAR_BASIC_KILOMETER_AVG_FUEL_CONSUMPTION,
        CAR_BASIC_LEFT_TURN_INDICATOR_LIGHT_STATUS,
        CAR_BASIC_LEFT_TURN_LIGHT_STATUS,
        CAR_BASIC_LEFT_TURN_SWITCH_STATUS,
        CAR_BASIC_LOW_BEAM_LIGHT_STATUS,
        CAR_BASIC_LOW_LIGHT_STATUS,
        CAR_BASIC_MAINTENANCE_WARNING,
        CAR_BASIC_MAINTENANCE_WARNING_MILEAGE,
        CAR_BASIC_OIL_LOW_WARNING,
        CAR_BASIC_OTA_UPGRADE_MODE_ACTION,
        CAR_BASIC_OTA_UPGRADE_MODE_STATE,
        CAR_BASIC_OUTSIDE_TEMP,
        CAR_BASIC_PASSWORD_FREE_LOGIN_NOTIFY,
        CAR_BASIC_POWER_MODE,
        CAR_BASIC_POWER_MODE_REPAIR_MODE,
        CAR_BASIC_POWER_SAVING_MODE,
        CAR_BASIC_REAR_FOG_LIGHT_STATUS,
        CAR_BASIC_REAR_WIPWER_STATUS,
        CAR_BASIC_REMAIN_FUEL_PERCENTAGE,
        CAR_BASIC_REMAIN_ODOMETER,
        CAR_BASIC_REMOTE_UPGRADE_STATE_ACTION,
        CAR_BASIC_RIGHT_TURN_INDICATOR_LIGHT_STATUS,
        CAR_BASIC_RIGHT_TURN_LIGHT_STATUS,
        CAR_BASIC_RIGHT_TURN_SWITCH_STATUS,
        CAR_BASIC_SEAT_BELT_WARNING,
        CAR_BASIC_SEATED_STATE,
        CAR_BASIC_SPEED,
        CAR_BASIC_STEER_WHEEL_HEATING_ENABLE,
        CAR_BASIC_STEERING_RESET_REMIND_ENABLE,
        CAR_BASIC_STEERING_WHEEL_ANGLE,
        CAR_BASIC_SUNROOF_STATUS,
        CAR_BASIC_SUNSHADE_STATUS,
        CAR_BASIC_TEMP_INSIDE,
        CAR_BASIC_TEMP_OUTSIDE,
        CAR_BASIC_TIREPRESS_WARNING,
        CAR_BASIC_TIRETEMP_WARNING,
        CAR_BASIC_TOTAL_ODOMETER,
        CAR_BASIC_TPMS_STATUS,
        CAR_BASIC_TPMS_UNITS,
        CAR_BASIC_TPMS_WARNING,
        CAR_BASIC_TRANSMISSION_OIL_TEMP,
        CAR_BASIC_VEHICLE_MODEL1,
        CAR_BASIC_VEHICLE_MODEL2,
        CAR_BASIC_VEHICLE_SPEED,
        CAR_BASIC_VEHICLE_SPEED_SINCE_RESET,
        CAR_BASIC_VEHICLE_VERSION,
        CAR_BASIC_VIN_CODE,
        CAR_BASIC_WINDOW_CLOSE_BY_DOOR_LOCK,
        CAR_BASIC_WINDOW_CLOSE_BY_LOCK_VEHICLE,
        CAR_BASIC_WINDOW_CLOSE_BY_SPEED,
        CAR_BASIC_WINDOW_CONTROL_ACTION,
        CAR_BASIC_WINDOW_CONTROL_ONESHOT,
        CAR_BASIC_WINDOW_REAR_LOCKED,
        CAR_BASIC_WINDOW_SHORT_UP_DOWN_SET,
        CAR_BASIC_WINDOW_STATUS,
        CAR_BASIC_WIPER_STATUS,
        CAR_BEANTECHS_EXCEPTION_REPORT,
        CAR_BEANTECHS_LICENSE_STATE,
        CAR_BEANTECHS_SOTA_START,
        CAR_BEANTECHS_SOTA_STATUS,
        CAR_BEANTECHS_VERSION,
        CAR_CAMERA_STATE,
        CAR_CAPACITY_VOICE_ENGINE_ACTION,
        CAR_CHILD_MODE_DOOR_WINDOW_LOCK_STATE,
        CAR_CHILD_MODE_LOCK_STATE,
        CAR_CHILD_MODE_LOCK_STATE_LAMP,
        CAR_CHILD_MODE_VMDR_ALARM_DELAY_ENABLE,
        CAR_CHILD_MODE_VMDR_TMP_MONITOR_STATE,
        CAR_COMFORT_SETTING_CHAIR_MEM_POSN_SET_SWT,
        CAR_COMFORT_SETTING_CHAIR_MEM_POSN_SET_SWT_FB,
        CAR_COMFORT_SETTING_CHAIR_REST_SET_SWT,
        CAR_COMFORT_SETTING_CHAIR_REST_SET_SWT_FB,
        CAR_COMFORT_SETTING_PASS_SEAT_MASSAGE_LEVEL,
        CAR_COMFORT_SETTING_SEAT_MASSAGE_ENABLE,
        CAR_COMFORT_SETTING_SEAT_MASSAGE_LEVEL,
        CAR_COMFORT_SETTING_SEAT_MASSAGE_MODE,
        CAR_COMFORT_SETTING_SEAT_WAIST_DIRECTION_CONFIG,
        CAR_COMFORT_SETTING_AUTO_OPEN_BACK_DOOR,
        CAR_COMFORT_SETTING_CHAIR_MEM_POS_SET_ACTION,
        CAR_COMFORT_SETTING_CHAIR_MEM_POS_SET_FEEDBACK,
        CAR_COMFORT_SETTING_CHAIR_MEMORY_AUTO_ENABLE,
        CAR_COMFORT_SETTING_DRIVE_SEAT_DIRECTION_CONFIG,
        CAR_COMFORT_SETTING_DRIVER_SEAT_HEATING_LEVEL,
        CAR_COMFORT_SETTING_DRIVER_SEAT_MASSAGE_ENABLE,
        CAR_COMFORT_SETTING_DRIVER_SEAT_MASSAGE_LEVEL,
        CAR_COMFORT_SETTING_DRIVER_SEAT_MASSAGE_MODE,
        CAR_COMFORT_SETTING_DRIVER_SEAT_SUPPORT_STATE,
        CAR_COMFORT_SETTING_DRIVER_SEAT_VENTILATION_LEVEL,
        CAR_COMFORT_SETTING_ELECTRIC_SIDE_PEDAL_ENABLE,
        CAR_COMFORT_SETTING_ELECTRIC_SIDE_PEDAL_ROOF_MODE_ENABLE,
        CAR_COMFORT_SETTING_ELECTRIC_SIDE_PEDAL_SPECIAL_MODE_ENABLE,
        CAR_COMFORT_SETTING_FRONT_WIPER_SENSITIVITY,
        CAR_COMFORT_SETTING_FRONT_WIPER_WORK_INTERVAL,
        CAR_COMFORT_SETTING_PASSENGER_CHAIR_MEM_POS_SET,
        CAR_COMFORT_SETTING_PASSENGER_SEAT_HEATING_LEVEL,
        CAR_COMFORT_SETTING_PASSENGER_SEAT_MASSAGE_ENABLE,
        CAR_COMFORT_SETTING_PASSENGER_SEAT_MASSAGE_LEVEL,
        CAR_COMFORT_SETTING_PASSENGER_SEAT_MASSAGE_MODE,
        CAR_COMFORT_SETTING_PASSENGER_SEAT_SUPPORT_STATE,
        CAR_COMFORT_SETTING_PASSENGER_SEAT_VENTILATION_LEVEL,
        CAR_COMFORT_SETTING_REAR_LEFT_SEAT_HEATING_LEVEL,
        CAR_COMFORT_SETTING_REAR_MIDDLE_SEAT_HEATING_LEVEL,
        CAR_COMFORT_SETTING_REAR_RIGHT_SEAT_HEATING_LEVEL,
        CAR_COMFORT_SETTING_REAR_RIGHT_SEAT_VENTILATION_LEVEL_NINE,
        CAR_COMFORT_SETTING_REAR_SEAT_HEATING_MAX_LEVEL,
        CAR_COMFORT_SETTING_REARVIEW_MIRROR_FOLDING_MODE,
        CAR_COMFORT_SETTING_REDUCE_SEAT_BELT_SLACK,
        CAR_COMFORT_SETTING_REST_MODE_CHAIR_MEM_SET,
        CAR_COMFORT_SETTING_REST_MODE_ENABLE,
        CAR_COMFORT_SETTING_SEAT_BELT_VIBRATION_WARNING,
        CAR_COMFORT_SETTING_SEAT_HEATING_ACTION,
        CAR_COMFORT_SETTING_SEAT_HEATING_MAX_LEVEL,
        CAR_COMFORT_SETTING_SEAT_VENTILATION_ACTION,
        CAR_COMFORT_SETTING_SEAT_VENTILATION_MAX_LEVEL,
        CAR_COMFORT_SETTING_SEAT_WELCOME_CONFIG,
        CAR_COMMAND_PARKING_SETTING_COME_GO_FREELY,
        CAR_COMMAND_PARKING_SETTING_OUTSIDE_CONTROL,
        CAR_COMMAND_PARKING_SETTING_RAISE_HAND_IGNITE,
        CAR_CONFIGURE_INDOOR_AUTO_DEFOGGING,
        CAR_CONFIGURE_ACTIVE_GRILLE,
        CAR_CONFIGURE_ACTIVELY_ENTER_SYSTEM,
        CAR_CONFIGURE_ADAPTIVE_FRONT_LIGHTING_SYSTEM,
        CAR_CONFIGURE_AEB_INTERSECTION_ASSIST,
        CAR_CONFIGURE_AIR_BAG,
        CAR_CONFIGURE_AIR_CONDITION_SYSTEM,
        CAR_CONFIGURE_AIR_CONDITION_TYPE,
        CAR_CONFIGURE_AIR_PURIFIER,
        CAR_CONFIGURE_AIR_QUALITY_SYSTEM,
        CAR_CONFIGURE_ALTERNATOR_PROPERTIES,
        CAR_CONFIGURE_AMPLIFIER,
        CAR_CONFIGURE_ANC_SYSTEM,
        CAR_CONFIGURE_ANTENNA,
        CAR_CONFIGURE_ASS_MEMORY_SETTING,
        CAR_CONFIGURE_ASSIST_BRAKE,
        CAR_CONFIGURE_ASSIST_UPHILL_DOWNHILL,
        CAR_CONFIGURE_AUDIO_PARSING_MULTICOLOR_RHYTHM_MOOD_LAMP,
        CAR_CONFIGURE_AUTO_EMERGENCY_STEERING,
        CAR_CONFIGURE_AUTO_HEADLIGHT,
        CAR_CONFIGURE_AUTO_PARKING,
        CAR_CONFIGURE_AUTO_PARKING_SYSTEM,
        CAR_CONFIGURE_BACK_AIR_CONDITION_SYSTEM,
        CAR_CONFIGURE_BATTERY_SENSOR,
        CAR_CONFIGURE_BIDIRECTION_CHARGING,
        CAR_CONFIGURE_BLUETOOTH_CALL,
        CAR_CONFIGURE_BODY_DOMAIN_CONTROLLER,
        CAR_CONFIGURE_BOOSTER_BRAKE_TYPE,
        CAR_CONFIGURE_BRAKING_ENERGY_RECOVERY,
        CAR_CONFIGURE_BUTTION_CONTROL,
        CAR_CONFIGURE_CATAPULT_START,
        CAR_CONFIGURE_CLUSTER_TIME_DISPLAY,
        CAR_CONFIGURE_CLUSTER_TYPE,
        CAR_CONFIGURE_COCKPIT_COMBINATION_TYPE,
        CAR_CONFIGURE_COMB_FRONT_LIGHT_SRC,
        CAR_CONFIGURE_CRUISE_CONTROL,
        CAR_CONFIGURE_DECK,
        CAR_CONFIGURE_DEFAULT_TEMP_UNIT,
        CAR_CONFIGURE_DIFFERENTIAL_LOCK,
        CAR_CONFIGURE_DISCHARGE_CONNECTING_DEVICE,
        CAR_CONFIGURE_DOOR_OPEN_WARNING,
        CAR_CONFIGURE_DOOR_UNLOCK_MODE,
        CAR_CONFIGURE_DRIVE_HAND_TYPE,
        CAR_CONFIGURE_DRIVE_MODE_MEMORY,
        CAR_CONFIGURE_DRIVER_SEAT,
        CAR_CONFIGURE_DRIVER_WAIST_SUPPORTER,
        CAR_CONFIGURE_DRIVER_WINDOW,
        CAR_CONFIGURE_DRIVING_MODE2_OPTIONAL,
        CAR_CONFIGURE_DRIVING_MODE_OPTIONAL,
        CAR_CONFIGURE_DRIVING_MODE_SIGNAL,
        CAR_CONFIGURE_DROWSINESS_WARNING_SYSTEM,
        CAR_CONFIGURE_DYNAMIC_DIRECTION_STAB_ASSIST,
        CAR_CONFIGURE_E_AXLE,
        CAR_CONFIGURE_ELEC_ENERGY_MANAGEMENT,
        CAR_CONFIGURE_ELEC_PARKING_GEAR,
        CAR_CONFIGURE_ELEC_STEERING_COL_LOCK,
        CAR_CONFIGURE_ELECTRIC_SOUND_REMINDER,
        CAR_CONFIGURE_ELECTRICAL_LANGUAGE,
        CAR_CONFIGURE_ELECTRONIC_GEAR_TYPE,
        CAR_CONFIGURE_EMERGENCY_STEERING_ASSIST,
        CAR_CONFIGURE_EMISSION_STANDARDS,
        CAR_CONFIGURE_ENERGY_TYPE,
        CAR_CONFIGURE_ENGINE_ANTI_THEFT,
        CAR_CONFIGURE_ENGINE_CONTROL_UNIT,
        CAR_CONFIGURE_ENGINE_MAIN_WATER_PUMP,
        CAR_CONFIGURE_ENHANCED_ASSIST_SYSTEM_VIBRATION_REMINDER,
        CAR_CONFIGURE_EV_DRIVE_ARCHITECTURE,
        CAR_CONFIGURE_FCW_AND_AEB,
        CAR_CONFIGURE_FORWARD_CROSS_WARNING_AND_BRAKE_SYSTEM,
        CAR_CONFIGURE_FRAGRANCE_SYSTEM,
        CAR_CONFIGURE_FRONT_BUMPER,
        CAR_CONFIGURE_FRONT_DEAD_ANGLE_SYSTEM,
        CAR_CONFIGURE_FRONT_RADAR_PROBE,
        CAR_CONFIGURE_FRONT_RADAR_SENSOR,
        CAR_CONFIGURE_FRONT_SEAT_BELT,
        CAR_CONFIGURE_FRONT_SEAT_COOLING_AND_MASSAGE,
        CAR_CONFIGURE_FRONT_WIPER,
        CAR_CONFIGURE_FUEL_TANK_SYSTEM,
        CAR_CONFIGURE_GRILLE,
        CAR_CONFIGURE_HAPTIC_REMINDER_OF_LANE_SUPPORT_SYSTEM,
        CAR_CONFIGURE_HEAD_LAMP_ADJUSTMENT,
        CAR_CONFIGURE_HEAD_LAMP_AUTO_OFF,
        CAR_CONFIGURE_HEADLAMP_CONTROL_FUNC,
        CAR_CONFIGURE_HIGHWAY_ASSIST_SYSTEM,
        CAR_CONFIGURE_HUD,
        CAR_CONFIGURE_HUT_ETHERNET_PORT,
        CAR_CONFIGURE_IDLE_STOP_AND_GO,
        CAR_CONFIGURE_INTELLIGENT_FRONT_VIEW_CONTROL,
        CAR_CONFIGURE_INTELLIGENT_NETWORK_SERVICE,
        CAR_CONFIGURE_LANE_ASSIST_SYSTEM_VIBRATION_REMINDER,
        CAR_CONFIGURE_LCA,
        CAR_CONFIGURE_LDW_AND_LKA,
        CAR_CONFIGURE_LED_ULTRA_LONG_ASSIST_HIGH_BEAM,
        CAR_CONFIGURE_LIGHT_AUTO_SWITCH_SYSTEM,
        CAR_CONFIGURE_MOBILE_BLUETOOTH_KEY,
        CAR_CONFIGURE_MOOD_LAMP,
        CAR_CONFIGURE_MOOD_LAMP_PARTITION,
        CAR_CONFIGURE_MP5_SCREEN,
        CAR_CONFIGURE_NAVIGATION,
        CAR_CONFIGURE_NFC,
        CAR_CONFIGURE_NIGHT_VISION_SYSTEM,
        CAR_CONFIGURE_NOMINAL_TIRE_PRESSURE,
        CAR_CONFIGURE_OFF_ROAD_CRUISE_CONTROL,
        CAR_CONFIGURE_OFF_ROAD_EXPERT_MODE,
        CAR_CONFIGURE_OFF_ROAD_INFO_DISPLAY,
        CAR_CONFIGURE_OMNIVEW_SYSTEM,
        CAR_CONFIGURE_ONEKEY_INNER_CYCLE_MODE,
        CAR_CONFIGURE_OUTSIDE_TEMP_DISPLAY,
        CAR_CONFIGURE_OUTSIDE_VIEW_MIRROR,
        CAR_CONFIGURE_PARKING_BRAKE,
        CAR_CONFIGURE_PARKING_LIGHT,
        CAR_CONFIGURE_PAS_AND_CTA,
        CAR_CONFIGURE_PASS_WAIST_SUPPORTER,
        CAR_CONFIGURE_PASS_WINDOW,
        CAR_CONFIGURE_PASSENGER,
        CAR_CONFIGURE_PEDAL_CONTROL,
        CAR_CONFIGURE_POWER_TYPE,
        CAR_CONFIGURE_PRODUCT_NAME,
        CAR_CONFIGURE_PROJECT_CODE,
        CAR_CONFIGURE_RAIN_SHELTER,
        CAR_CONFIGURE_REAR_BUMPER,
        CAR_CONFIGURE_REAR_COLLISION_WARNING,
        CAR_CONFIGURE_REAR_DOOR_OPENING,
        CAR_CONFIGURE_REAR_RADAR_SENSOR,
        CAR_CONFIGURE_REAR_WINDOW,
        CAR_CONFIGURE_RECORDER,
        CAR_CONFIGURE_REGIONAL,
        CAR_CONFIGURE_REMOTE_CONTROL,
        CAR_CONFIGURE_REMOTE_RESERVATION_CHARGING,
        CAR_CONFIGURE_REVERSE_VIDEO,
        CAR_CONFIGURE_ROBOT,
        CAR_CONFIGURE_ROLLOVER_SENSING_CURTAIN_AIRBAG_PROTECTION,
        CAR_CONFIGURE_ROTATING_SPEED_ALARM_THRESHOLD,
        CAR_CONFIGURE_SALES_AREA,
        CAR_CONFIGURE_SEAT_BELT_WARNING,
        CAR_CONFIGURE_SEAT_CONTROL_MODULE,
        CAR_CONFIGURE_SEAT_HEATING,
        CAR_CONFIGURE_SECOND_SEAT_COOLING_AND_MASSAGE,
        CAR_CONFIGURE_SHIFT_UNLOCK_KEY,
        CAR_CONFIGURE_SHOCK_ABSORBER,
        CAR_CONFIGURE_SIDE_PEDAL,
        CAR_CONFIGURE_SKYLIGHT_WINDOW,
        CAR_CONFIGURE_SMART_FUSE_BOX,
        CAR_CONFIGURE_SPEAKER,
        CAR_CONFIGURE_SPECIAL_EXPORT_COUNTRY,
        CAR_CONFIGURE_SPEED_METER,
        CAR_CONFIGURE_STANDARD_UNIT,
        CAR_CONFIGURE_STARTING_SYSTEM,
        CAR_CONFIGURE_STEER_WHEEL_HEATING,
        CAR_CONFIGURE_STEERING_ASSIST,
        CAR_CONFIGURE_STEERING_SYSTEM,
        CAR_CONFIGURE_SUB_AIRBAG_SWITCH,
        CAR_CONFIGURE_SUB_INSTRUMENT_SCREEN,
        CAR_CONFIGURE_SUPER_LOCK,
        CAR_CONFIGURE_TELEMATICS,
        CAR_CONFIGURE_TPMS,
        CAR_CONFIGURE_TRAFFIC_SIGN_WARNING,
        CAR_CONFIGURE_TRAILING_HOOK,
        CAR_CONFIGURE_TRANSMISSION_ANTI_THEFT,
        CAR_CONFIGURE_TRANSMISSION_TYPE,
        CAR_CONFIGURE_VEHICLE_SPLITTER,
        CAR_CONFIGURE_VEHICLE_TO_VEHICLE_CHARGE,
        CAR_CONFIGURE_VOICE_AND_FACIAL_RECOGNITION,
        CAR_CONFIGURE_VOICE_RECOGNITON,
        CAR_CONFIGURE_WPC,
        CAR_DASHBOARD_SETTING_BRIGHTNESS_CONFIG,
        CAR_DMS_CAMERA_SHELTER,
        CAR_DMS_FEEDBACK_DISTRACTED_STATE,
        CAR_DMS_FEEDBACK_EMOTIONAL_STATE,
        CAR_DMS_FEEDBACK_FATIGUE_DRIVING_STATE,
        CAR_DMS_FRS_CONFIG_ENABLE,
        CAR_DMS_FRS_DELE_DMS_FACEDB,
        CAR_DMS_FRS_DELE_DMS_FACEDB_RST,
        CAR_DMS_FRS_DELE_OMS_FACEDB,
        CAR_DMS_FRS_DELE_OMS_FACEDB_RST,
        CAR_DMS_FRS_DELETE_ACTION,
        CAR_DMS_FRS_DMS_BEHAVIOR_CALL_DETECT,
        CAR_DMS_FRS_DMS_BEHAVIOR_DRINK_DETECT,
        CAR_DMS_FRS_DMS_BEHAVIOR_SMOKE_DETECT,
        CAR_DMS_FRS_DRIVER_AGE,
        CAR_DMS_FRS_DRIVER_BEHAVIOR_DETECT,
        CAR_DMS_FRS_DRIVER_GENDER,
        CAR_DMS_FRS_DRIVER_SIGHT_TO_MONITOR_ENABLE,
        CAR_DMS_FRS_DRIVER_SIGHT_TO_MONITOR_RESULT,
        CAR_DMS_FRS_DRIVER_STAT,
        CAR_DMS_FRS_DRIVER_STAT_STATE,
        CAR_DMS_FRS_ENROLL_ACTION,
        CAR_DMS_FRS_ENROLL_AGE,
        CAR_DMS_FRS_ENROLL_GENDER,
        CAR_DMS_FRS_ENROLL_NUM,
        CAR_DMS_FRS_GAZE_AREA,
        CAR_DMS_FRS_IDENTIFY_ACTION,
        CAR_DMS_FRS_NOTIFY_DELTE_STATE,
        CAR_DMS_FRS_NOTIFY_ENROLL_STATE,
        CAR_DMS_FRS_NOTIFY_IDENTIFY_RESULT,
        CAR_DMS_FRS_NOTIFY_IDENTIFY_STATE,
        CAR_DMS_INFRAREDBLOCKING_GLASSES_DETECT,
        CAR_DMS_WORK_STATE,
        CAR_DOOR_LOCK_SETTING_INITIATIVE_TO_ENTER,
        CAR_DOOR_LOCK_SETTING_INITIATIVE_TO_EXIT,
        CAR_DOOR_LOCK_SETTING_LOCK_PROMPT,
        CAR_DOOR_LOCK_SETTING_LOCKED_BY_SPEED,
        CAR_DOOR_LOCK_SETTING_LOCKED_REMIND,
        CAR_DOOR_LOCK_SETTING_SUPER_LOCK_ENABLE,
        CAR_DOOR_LOCK_SETTING_UNLOCK_BY_FLAMEOUT,
        CAR_DOOR_LOCK_SETTING_UNLOCK_MODE,
        CAR_DRIVE_ETC_ACTIVATION_STATUS,
        CAR_DRIVE_SETTING_ACCELERATOR_PEDAL_SENSITIVITY,
        CAR_DRIVE_SETTING_BRAKING_MODE,
        CAR_DRIVE_SETTING_CREEPING_MODE_ENABLE,
        CAR_DRIVE_SETTING_DAMPER_MODE,
        CAR_DRIVE_SETTING_DRIVE_MODE_MEMORY,
        CAR_DRIVE_SETTING_EPB_ENABLE_AVAILABLE,
        CAR_DRIVE_SETTING_HDC_ENABLE,
        CAR_DRIVE_SETTING_OUTLINE_LAMPS_STATE,
        CAR_DRIVE_SETTING_OUTSIDE_VIEW_MIRROR_ASTERN_MODE,
        CAR_DRIVE_SETTING_OUTSIDE_VIEW_MIRROR_FOLD_STATE,
        CAR_DRIVE_SETTING_SPEED_LIMIT,
        CAR_DRIVE_SETTING_TRAILER_HOOK_ACTION,
        CAR_DRIVE_SETTING_TRAILER_HOOK_STATE,
        CAR_DRIVE_SETTING_ASS_STATE,
        CAR_DRIVE_SETTING_BRAKE_MODE_SELECT,
        CAR_DRIVE_SETTING_DAMPING_DRIVE_MODE,
        CAR_DRIVE_SETTING_DRIVE_MODE,
        CAR_DRIVE_SETTING_DRV_ASST_SWT,
        CAR_DRIVE_SETTING_DST_ENABLE,
        CAR_DRIVE_SETTING_ESP_ENABLE,
        CAR_DRIVE_SETTING_FATIGUE_MONITOR_STATE,
        CAR_DRIVE_SETTING_STEERING_WHEEL_ASSIST_MODE,
        CAR_DRIVE_SETTING_STEERING_WHEEL_CORRECTION_REMINDER_ENABLE,
        CAR_EV_SETTING_AVAS_CONFIG,
        CAR_EV_SETTING_AVAS_ENABLE,
        CAR_EV_SETTING_BATTERY_CHARGING_INSULATION_ENABLE,
        CAR_EV_SETTING_BATTERY_CHARGING_INSULATION_TYPE,
        CAR_EV_SETTING_BATTERY_PACK_AUTO_INSULATION_ENABLE,
        CAR_EV_SETTING_ENGINE_DISCHARGE_ENABLE,
        CAR_EV_SETTING_GMODE_GW_STATE,
        CAR_EV_SETTING_GMODE_HUT_SET,
        CAR_EV_SETTING_GMODE_HUT_STATE,
        CAR_EV_SETTING_GMODE_NOTIFY,
        CAR_EV_SETTING_GMODE_STATE,
        CAR_EV_SETTING_PEDAL_CONTROL_ENABLE,
        CAR_EV_SETTING_VEHICLE_TO_LOAD_DISCHARGE_ENABLE,
        CAR_EV_SETTING_VEHICLE_TO_VEHICLE_DISCHARGE_ENABLE,
        CAR_EV_SETTING_VEHICLE_TO_VEHICLE_DISCHARGE_NOTIFY,
        CAR_EV_SETTING_VSG_CONFIG,
        CAR_EV_SETTING_WADE_MODE_ENABLE,
        CAR_EV_SETTING_WASH_MODE_ENABLE,
        CAR_EV_INFO_INSTANT_ENERGY_CONSUMPTION,
        CAR_EV_INFO_ATTENUATION_OF_BATTERY,
        CAR_EV_INFO_AVERAGE_ENERGY_CONSUME_INFO,
        CAR_EV_INFO_AVG_ENERGY_CONSUME_INFO_SINCE_RESET,
        CAR_EV_INFO_AVG_ENERGY_CONSUME_INFO_SINCE_STARTUP,
        CAR_EV_INFO_BATT_HEAT_RUNAWAY_NOTIFY,
        CAR_EV_INFO_BATTERY_CHARGE_PERCENTAGE,
        CAR_EV_INFO_BMS_STATE,
        CAR_EV_INFO_CHARGE_PILE_ACTION,
        CAR_EV_INFO_CHARGE_PILE_ACTION_REPONSE,
        CAR_EV_INFO_CHARGE_PILE_BROADCAST,
        CAR_EV_INFO_CHARGE_PILE_CONNECT_STATE,
        CAR_EV_INFO_CHARGE_PILE_ID1,
        CAR_EV_INFO_CHARGE_PILE_ID2,
        CAR_EV_INFO_CHARGE_REMAINING_TIME,
        CAR_EV_INFO_CHARGING_GUN_AC_CONN_STATE,
        CAR_EV_INFO_CHARGING_GUN_CONN_STATE,
        CAR_EV_INFO_CHARGING_STATE,
        CAR_EV_INFO_CUR_BATTERY_POWER_PERCENTAGE,
        CAR_EV_INFO_CUR_CHARGE_CURRENT,
        CAR_EV_INFO_CYCLE_ENERGY_CONSUME_INFO,
        CAR_EV_INFO_CYCLE_FUEL_CONSUME_INFO,
        CAR_EV_INFO_ECONOMIC_GUIDE_LEVEL,
        CAR_EV_INFO_ECONOMIC_GUIDE_RANGE,
        CAR_EV_INFO_ELECTRIC_MODE_REMAIN_ODOMETER,
        CAR_EV_INFO_ENERGY_CONSUME_INFO,
        CAR_EV_INFO_ENERGY_DRIVE_STATE,
        CAR_EV_INFO_ENERGY_OUTPUT_PERCENTAGE,
        CAR_EV_INFO_ENERGY_OUTPUT_WARNING,
        CAR_EV_INFO_ENERGY_RECOVERY_INFO,
        CAR_EV_INFO_FAST_CHARGING_GUN_CONN_STATE,
        CAR_EV_INFO_FUEL_CONSUME_INFO,
        CAR_EV_INFO_FUEL_MODE_REMAIN_ODOMETER,
        CAR_EV_INFO_HCU_POWER_TRAIN_STATE,
        CAR_EV_INFO_LAST_CHARGE_TIME_ODOMETER,
        CAR_EV_INFO_MOTOR_POWER,
        CAR_EV_INFO_MOTOR_SPEED,
        CAR_EV_INFO_PHEV_AHD_VOLTAGE,
        CAR_EV_INFO_POWER_BATTERY_CURRENT,
        CAR_EV_INFO_POWER_BATTERY_VOLTAGE,
        CAR_EV_INFO_POWER_DOWN_COMPLETE,
        CAR_EV_INFO_POWER_VOLT_STATE,
        CAR_EV_INFO_REAR_MOTOR_SPEED,
        CAR_EV_INFO_SOC_OF_BATTERY,
        CAR_EV_INFO_TOTAL_ODOMETER,
        CAR_EV_INFO_VEHICLE_START_REQ,
        CAR_EV_SETTING_APPOINT_CHARGE_SET,
        CAR_EV_SETTING_AUTO_CHARGE_CONFIG,
        CAR_EV_SETTING_CHARGE_ACTION,
        CAR_EV_SETTING_CHARGE_CURRENT_CONFIG,
        CAR_EV_SETTING_CHARGE_MODE,
        CAR_EV_SETTING_CHARGE_SAVE_MODE_LIMIT_CONFIG,
        CAR_EV_SETTING_CHARGE_SOC_LIMIT_CONFIG,
        CAR_EV_SETTING_CHARGE_SOC_TARGET_CONFIG,
        CAR_EV_SETTING_DIAGNOSE_REPAIR_MODE_ENABLE,
        CAR_EV_SETTING_DRIVE_TIME_CONFIG,
        CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL,
        CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL_VALUE,
        CAR_EV_SETTING_ENGINE_START_SETTINGS,
        CAR_EV_SETTING_POWER_MODEL_CONFIG,
        CAR_EV_SETTING_POWER_RESERVE_CONFIG,
        CAR_EV_SETTING_WPT_ENABLE,
        CAR_EV_SETTING_WPT_LODSTS,
        CAR_FRAGRANCE_CHANNEL_LONG_TYPE,
        CAR_FRAGRANCE_CHANNEL_TIMEOUT_ALARM,
        CAR_FRAGRANCE_CHANNEL_TYPE,
        CAR_FRAGRANCE_CHANNEL_WORKING_STATUS,
        CAR_FRAGRANCE_CONCENTRATION_STATUS,
        CAR_FRAGRANCE_CUR_CHANNEL_TYPE,
        CAR_FRAGRANCE_STATUS,
        CAR_FRS_CURRENT_OMS_SEAT_INFO,
        CAR_FRS_SETTING_DISTRACTION_DETECTION_ENABLE,
        CAR_FRS_SETTING_DMS_ENABLE,
        CAR_FRS_SETTING_DRIVER_AGE,
        CAR_FRS_SETTING_DRIVER_BEHAVIOR_DETECT,
        CAR_FRS_SETTING_DRIVER_GENDER,
        CAR_FRS_SETTING_EXPRESSION_RECOGNITION_ENABLE,
        CAR_FRS_SETTING_FACIAL_DETECTION_ENABLE,
        CAR_FRS_SETTING_FATIGUE_MONITORING_ENABLE,
        CAR_FRS_SETTING_GESTURE_ENABLE,
        CAR_FRS_SETTING_OCCUPANT_IDENTIFICATION_SYSTEM,
        CAR_FRS_SETTING_OMS_AGE,
        CAR_FRS_SETTING_OMS_BEHAVIOR_DETECT_ENABLE,
        CAR_FRS_SETTING_OMS_CONFIG_ENABLE,
        CAR_FRS_SETTING_OMS_EXIST,
        CAR_FRS_SETTING_OMS_EXIST_STATE,
        CAR_FRS_SETTING_OMS_GENDER,
        CAR_FRS_SETTING_PASSENGER_EXPRESSION,
        CAR_FRS_SETTING_SCREEN_CONTROL_ENABLE,
        CAR_GENERNAL_SETTING_FACTORY_RESET_ACTION,
        CAR_GENERNAL_SETTING_FACTORY_RESET_RESULT,
        CAR_GENERNAL_SETTING_ULTRASONIC_ANTI_THEFT,
        CAR_HUD_SETTING_ADAS_DISPLAY_ENABLE,
        CAR_HUD_SETTING_AUTO_BRIGHTNESS_ENABLE,
        CAR_HUD_SETTING_BRIGHTNESS_CONFIG,
        CAR_HUD_SETTING_BTPHONE_DISPLAY_ENABLE,
        CAR_HUD_SETTING_DISPLAY_MODE_STATE,
        CAR_HUD_SETTING_ENABLE_STATE,
        CAR_HUD_SETTING_ENGINE_SPEED_DISPLAY_ENABLE,
        CAR_HUD_SETTING_HEIGHT_CONFIG,
        CAR_HUD_SETTING_MODE_CONFIG,
        CAR_HUD_SETTING_NAVIGATION_DISPLAY_ENABLE,
        CAR_HUD_SETTING_REALX_MOMENT_ENABLE,
        CAR_HUD_SETTING_ROTATION_ANGLE,
        CAR_HUD_SETTING_ROTATION_DIRECTION,
        CAR_HUD_SETTING_SNOW_MODE_ENABLE,
        CAR_HUD_SETTING_TOUCH_SCREEN_VIBRATION,
        CAR_HUD_SETTING_VIBRATION_CORRN_ENABLE,
        CAR_HVAC_INTELLIGENT_SWITCH_ENABLE,
        CAR_HVAC_INTELLIGENT_TEMPERATURE_RANGE,
        CAR_HVAC_AC_ENABLE,
        CAR_HVAC_ACMAX_ENABLE,
        CAR_HVAC_ANION_ENABLE,
        CAR_HVAC_AQS_ENABLE,
        CAR_HVAC_AUTO_ENABLE,
        CAR_HVAC_AUTO_LIMIT_ENABLE,
        CAR_HVAC_BLOWER_MODE,
        CAR_HVAC_CONFIG,
        CAR_HVAC_CONTROL_VIEW_DISPLAY_POSITION,
        CAR_HVAC_CYCLE_MODE,
        CAR_HVAC_DISPLAY_STATUS,
        CAR_HVAC_DRIVER_ELECTRIC_BLOWER_MODE,
        CAR_HVAC_DRIVER_TEMP_ACTION,
        CAR_HVAC_DRIVER_TEMPERATURE,
        CAR_HVAC_ELECTRIC_BLOWER_CENTER_LEFT_POSITION,
        CAR_HVAC_ELECTRIC_BLOWER_LEFT_POSITION,
        CAR_HVAC_ELECTRIC_BLOWER_RIGHT_POSITION,
        CAR_HVAC_EVAPORATOR_SELF_DRYING_ENABLE,
        CAR_HVAC_FAN_SPEED,
        CAR_HVAC_FAN_SPEED_ACTION,
        CAR_HVAC_FAN_SPEED_RANGE,
        CAR_HVAC_FRONT_DEFROST_ENABLE,
        CAR_HVAC_FRONT_TEMPERATURE_RANGE,
        CAR_HVAC_HEATING_ENABLE,
        CAR_HVAC_NATURAL_WIND_ENABLE,
        CAR_HVAC_NATURAL_WIND_MODE,
        CAR_HVAC_PANEL_DISPLAY_NOTIFY,
        CAR_HVAC_PASS_TEMP_ACTION,
        CAR_HVAC_PASS_TEMPERATURE,
        CAR_HVAC_PASSENGER_ELECTRIC_BLOWER_MODE,
        CAR_HVAC_PM2_5_VALUE,
        CAR_HVAC_POWER_MODE,
        CAR_HVAC_REAR_AC_LOCK,
        CAR_HVAC_REAR_CONTROL_LOCK,
        CAR_HVAC_REAR_DEFROST_ENABLE,
        CAR_HVAC_REAR_FAN_SPEED,
        CAR_HVAC_REAR_FAN_SPEED_RANGE,
        CAR_HVAC_REAR_PANEL_DISPLAY_NOTIFY,
        CAR_HVAC_REAR_TEMP_ACTION,
        CAR_HVAC_REAR_TEMPERATURE,
        CAR_HVAC_REAR_TEMPERATURE_RANGE,
        CAR_HVAC_REQUEST_HVAC_INFO,
        CAR_HVAC_SELF_DESICCATION_ENABLE,
        CAR_HVAC_SETTING_AUTO_DEFROST_ENABLE,
        CAR_HVAC_SETTING_COMFORT_CURVE,
        CAR_HVAC_SETTING_LIMIT_ENABLE,
        CAR_HVAC_SETTING_LEFT_SIDE_OUTLET_MODE,
        CAR_HVAC_SETTING_RIGHT_SIDE_OUTLET_MODE,
        CAR_HVAC_SYNC_ENABLE,
        CAR_HVAC_ULTRAVIOLET_STERILIZATION_ENABLE,
        CAR_HVAC_ZONE_ENABLE,
        CAR_HVAC_ZONE_SYNC_ENABLE,
        CAR_INTELLIGENT_HAVC_PRE_COOLING,
        CAR_INTELLIGENT_NAVIGATION_AMBIENT_LIGHTING_CONTROL_ACTION,
        CAR_INTELLIGENT_PULSATING_AMBIENT_LIGHTING,
        CAR_INTELLIGENT_VOICE_WINDOW_CONTROL_ACTION,
        CAR_INTELLIGENT_DRIVING_RAIN_MODE,
        CAR_INTELLIGENT_DRIVING_INFO_ACC_STATE,
        CAR_INTELLIGENT_DRIVING_INFO_AUTO_HOLD_STATE,
        CAR_INTELLIGENT_DRIVING_INFO_HAZARD_BRAKE_STATE,
        CAR_INTELLIGENT_DRIVING_INFO_TJA_FOLLOW_STATE,
        CAR_INTELLIGENT_DRIVING_INFO_TJA_HWA_STATE,
        CAR_INTELLIGENT_DRIVING_INFO_TJA_ICA_STATE,
        CAR_INTELLIGENT_DRIVING_INFO_TTS_PLAY_SET,
        CAR_INTELLIGENT_DRIVING_SETTING_CRUISING_SPEED_LIMIT,
        CAR_INTELLIGENT_DRIVING_SETTING_EAS_ASSIST_SENSITIVITY,
        CAR_INTELLIGENT_DRIVING_SETTING_EAS_CHANGE_LANE_ASSIST_STATE,
        CAR_INTELLIGENT_DRIVING_SETTING_EAS_HIGHWAY_ASSIST_SYSTEM_STATE,
        CAR_INTELLIGENT_DRIVING_SETTING_EAS_WARNING_WAY,
        CAR_INTELLIGENT_DRIVING_SETTING_EAS_ENABLE,
        CAR_INTELLIGENT_DRIVING_SETTING_EAS_SETTING,
        CAR_INTELLIGENT_DRIVING_SETTING_FAS_AUTO_EMERGENCY_TURN,
        CAR_INTELLIGENT_DRIVING_SETTING_FAS_EARLY_WARNING_MODE,
        CAR_INTELLIGENT_DRIVING_SETTING_FAS_EARLY_WARNING_SENSITIVITY,
        CAR_INTELLIGENT_DRIVING_SETTING_FAS_FRONT_CROSS_LATERAL_BRAKE,
        CAR_INTELLIGENT_DRIVING_SETTING_FAS_FRONT_CROSS_LATERAL_WRANING,
        CAR_INTELLIGENT_DRIVING_SETTING_FAS_INTELLIGENTCURVE_SWITCH,
        CAR_INTELLIGENT_DRIVING_SETTING_FAS_INTERSECTION_ASSIST_STATE,
        CAR_INTELLIGENT_DRIVING_SETTING_FAS_PCS_STATE,
        CAR_INTELLIGENT_DRIVING_SETTING_FAS_PPS_STATE,
        CAR_INTELLIGENT_DRIVING_SETTING_LAS_EARLY_WARNING_SENSITIVITY,
        CAR_INTELLIGENT_DRIVING_SETTING_LAS_ELK_STATE,
        CAR_INTELLIGENT_DRIVING_SETTING_LAS_ENABLE,
        CAR_INTELLIGENT_DRIVING_SETTING_LAS_LCA_STATE,
        CAR_INTELLIGENT_DRIVING_SETTING_LAS_LDW_STATE,
        CAR_INTELLIGENT_DRIVING_SETTING_LAS_LKA_STATE,
        CAR_INTELLIGENT_DRIVING_SETTING_LAS_TSI_STATE,
        CAR_INTELLIGENT_DRIVING_SETTING_LC_MODE,
        CAR_INTELLIGENT_DRIVING_SETTING_NAV_DEC_SPEED,
        CAR_INTELLIGENT_DRIVING_SETTING_NOH_AUXILIARY_NAVIGATION,
        CAR_INTELLIGENT_DRIVING_SETTING_NOH_FOLLOW_NAVIGATION,
        CAR_INTELLIGENT_DRIVING_SETTING_NOH_STATUS,
        CAR_INTELLIGENT_DRIVING_SETTING_OVER_SPEED_ALARM_SENSITIVITY,
        CAR_INTELLIGENT_DRIVING_SETTING_OVER_SPEED_WARNING_STATE,
        CAR_INTELLIGENT_DRIVING_SETTING_RESET,
        CAR_INTELLIGENT_DRIVING_SETTING_SMART_DODGE_STATE,
        CAR_INTELLIGENT_DRIVING_SETTING_SRAS_ALA_STATE,
        CAR_INTELLIGENT_DRIVING_SETTING_SRAS_DOOR_OPEN_WARNING,
        CAR_INTELLIGENT_DRIVING_SETTING_SRAS_RCW_STATE,
        CAR_INTELLIGENT_DRIVING_SETTING_SRAS_RSA_RSB_STATE,
        CAR_INTELLIGENT_DRIVING_SETTING_SRAS_RSA_RSB_WARNING_STATE,
        CAR_INTELLIGENT_DRIVING_SETTING_SRAS_RSA_STATE,
        CAR_INTELLIGENT_DRIVING_SETTING_TRAFFIC_LIGHT_CONTROL_ENABLE,
        CAR_INTELLIGENT_DRIVING_SETTING_VEHICLE_TO_EVERYTHING_ENABLE,
        CAR_INTELLIGENT_DRIVING_SETTING_WISDOM_CORNERING_ENABLE,
        CAR_IPK_SETTING_ENGINE_SPEED_UI_STATE,
        CAR_IPK_SETTING_ENGINE_SPEEDING_LIMIT_ALARM,
        CAR_IPK_INFO_AEB_JA_BRK_TRIG,
        CAR_IPK_INFO_AEB_JA_TRIG,
        CAR_IPK_INFO_AEB_PED_TRIG,
        CAR_IPK_INFO_BSD_LCA_WARNING_REQLEFT,
        CAR_IPK_INFO_BSD_LCA_WARNING_REQRIGHT,
        CAR_IPK_INFO_CLUSTER_ALARM_STATUS,
        CAR_IPK_INFO_DOW_WARNING_REQLEFT,
        CAR_IPK_INFO_DOW_WARNING_REQRIGHT,
        CAR_IPK_INFO_FCTA_WARNING,
        CAR_IPK_INFO_FCTB_TRIG,
        CAR_IPK_INFO_FCW_WARNING,
        CAR_IPK_INFO_WARNING_TTS_CONTENT,
        CAR_IPK_INFO_WARNING_TTS_NOTIFY,
        CAR_IPK_LIGHT_ABS_INDICATOR,
        CAR_IPK_LIGHT_AEB_VEH_TRIG,
        CAR_IPK_LIGHT_AIRBAG,
        CAR_IPK_LIGHT_ASS_INDICATOR,
        CAR_IPK_LIGHT_AUTO_PARKING_INDICATOR,
        CAR_IPK_LIGHT_BATTERY_CHARGE_INDICATOR,
        CAR_IPK_LIGHT_BATTERY_CUT_OFF_INDICATOR,
        CAR_IPK_LIGHT_BRAKE_ENERGE_RECYCLE,
        CAR_IPK_LIGHT_BRAKING_SYSTEM_INDICATOR,
        CAR_IPK_LIGHT_BSD_INDICATOR,
        CAR_IPK_LIGHT_CCO_INDICATOR,
        CAR_IPK_LIGHT_CHARGE_SYSTEM_INDICATOR,
        CAR_IPK_LIGHT_DOOR_WARNING,
        CAR_IPK_LIGHT_ENGINE_MALFUNCTION,
        CAR_IPK_LIGHT_ENGINE_OIL_LOW_PRESSURE_WARNING,
        CAR_IPK_LIGHT_ENGINE_SERVICE,
        CAR_IPK_LIGHT_ENGINE_WATER_TEMP_HIGH,
        CAR_IPK_LIGHT_EPARK_INDICATOR,
        CAR_IPK_LIGHT_EPS_ALERT,
        CAR_IPK_LIGHT_EPS_OFF,
        CAR_IPK_LIGHT_ESP_INDICATOR,
        CAR_IPK_LIGHT_ETC_ACTIVE_STATE,
        CAR_IPK_LIGHT_ETC_TAMPER_STATE,
        CAR_IPK_LIGHT_FCW_INDICATOR,
        CAR_IPK_LIGHT_FRONT_DIFFERENTIAL_LOCK_INDICATOR,
        CAR_IPK_LIGHT_FUEL_LOW,
        CAR_IPK_LIGHT_FUEL_LOW_LMPSTS,
        CAR_IPK_LIGHT_GLOBAL_ALARM_INDICATOR,
        CAR_IPK_LIGHT_HYBRID_POWER_SYSTEM,
        CAR_IPK_LIGHT_LDW_INDICATOR,
        CAR_IPK_LIGHT_LIMIT_120,
        CAR_IPK_LIGHT_LKA_INDICATOR,
        CAR_IPK_LIGHT_MOTOR_MALFUNCTION,
        CAR_IPK_LIGHT_PARKING_ALARM_INDICATOR,
        CAR_IPK_LIGHT_POWER_BATTERY_ALARM_INDICATOR,
        CAR_IPK_LIGHT_POWER_BATTERY_LOW,
        CAR_IPK_LIGHT_REAR_DIFFERENTIAL_LOCK_INDICATOR,
        CAR_IPK_LIGHT_REDUCE_POWER_INDICATOR,
        CAR_IPK_LIGHT_SEAT_BELT_WARNING_INDICATOR,
        CAR_IPK_LIGHT_SOC_LOW_WARN,
        CAR_IPK_LIGHT_SOUND_PROMPT_SYSTEM,
        CAR_IPK_LIGHT_TAB_INDICATOR,
        CAR_IPK_LIGHT_TPMS_WARNING,
        CAR_IPK_LIGHT_TRANSMISSION_OVER_HEAT,
        CAR_IPK_LIGHT_VCU_READY_STATE,
        CAR_IPK_SETTING_ALARM_HIS_INFO_LIST,
        CAR_IPK_SETTING_BRIGHTNESS_CONFIG,
        CAR_IPK_SETTING_BRIGHTNESS_RANGE,
        CAR_IPK_SETTING_CAST_SCREEN_READY_STATE,
        CAR_IPK_SETTING_DRIVE_INFO_RESET,
        CAR_IPK_SETTING_DRIVE_INFO_RESET_STATE,
        CAR_IPK_SETTING_MODE_CONFIG,
        CAR_IPK_SETTING_THEME_CONFIG,
        CAR_LIGHT_SETTING_ABS_LIGHT_ENABLE,
        CAR_LIGHT_SETTING_AMBIENT_LIGHT_INTELLIGENT_LINKAGE,
        CAR_LIGHT_SETTING_AMBIENT_LIGHT_AEB_FOLLOW,
        CAR_LIGHT_SETTING_AMBIENT_LIGHT_AREA_DRIVER_DOOR_SWITCH,
        CAR_LIGHT_SETTING_AMBIENT_LIGHT_AREA_FOOT_SWITCH,
        CAR_LIGHT_SETTING_AMBIENT_LIGHT_AREA_LEFT_PANEL_SWITCH,
        CAR_LIGHT_SETTING_AMBIENT_LIGHT_AREA_PASS_DOOR_SWITCH,
        CAR_LIGHT_SETTING_AMBIENT_LIGHT_AREA_RIGHT_PANEL_SWITCH,
        CAR_LIGHT_SETTING_AMBIENT_LIGHT_AREA_RIGHT_REAR_DOOR_SWITCH,
        CAR_LIGHT_SETTING_AMBIENT_LIGHT_AREA_ROOF_SWITCH,
        CAR_LIGHT_SETTING_AMBIENT_LIGHT_BREATHING_MODE_SWITCH,
        CAR_LIGHT_SETTING_AMBIENT_LIGHT_BRIGHTNESS,
        CAR_LIGHT_SETTING_AMBIENT_LIGHT_DOW_SYNC,
        CAR_LIGHT_SETTING_AMBIENT_LIGHT_DYNAMIC_MODE,
        CAR_LIGHT_SETTING_AMBIENT_LIGHT_ENABLE,
        CAR_LIGHT_SETTING_AMBIENT_LIGHT_FCTA_FCTB_SYNC,
        CAR_LIGHT_SETTING_AMBIENT_LIGHT_FCW_SYNC,
        CAR_LIGHT_SETTING_AMBIENT_LIGHT_FLOW_WATER_SWITCH,
        CAR_LIGHT_SETTING_AMBIENT_LIGHT_LCA_SYNC,
        CAR_LIGHT_SETTING_AMBIENT_LIGHT_MULTICOLOR_COLOR_CONFIG,
        CAR_LIGHT_SETTING_AMBIENT_LIGHT_MULTICOLOR_DYNAMIC_CONFIG,
        CAR_LIGHT_SETTING_AMBIENT_LIGHT_MULTICOLOR_STATIC_CONFIG,
        CAR_LIGHT_SETTING_AMBIENT_LIGHT_MUSIC_FOLLOW,
        CAR_LIGHT_SETTING_AMBIENT_LIGHT_RHYTHMIC_SWITCH,
        CAR_LIGHT_SETTING_AMBIENT_LIGHT_SCENE_SELECTION,
        CAR_LIGHT_SETTING_AMBIENT_LIGHT_SYNC_AMP_MAIN_VOL,
        CAR_LIGHT_SETTING_AMBIENT_LIGHT_SYNC_AMP_MUTE,
        CAR_LIGHT_SETTING_AMBIENT_LIGHT_SYNC_MUSIC_FREQ,
        CAR_LIGHT_SETTING_AMBIENT_LIGHT_OLE_MULTICOLOR_COLOR_CONFIG,
        CAR_LIGHT_SETTING_AMBIENT_LIGHT_OLE_MULTICOLOR_DYNAMIC_CONFIG,
        CAR_LIGHT_SETTING_AMBIENT_LIGHT_OLE_MULTICOLOR_STATIC_CONFIG,
        CAR_LIGHT_SETTING_DRIVER_AMBIENT_LIGHT_ENABLE,
        CAR_LIGHT_SETTING_EB_TURN_LIGHT_ENABLE,
        CAR_LIGHT_SETTING_FIND_CAR_MODE_LIGHT,
        CAR_LIGHT_SETTING_FOLLOW_HOME_DELAY_TIME,
        CAR_LIGHT_SETTING_FREIGHT_LIGHT_ENABLE,
        CAR_LIGHT_SETTING_HIGH_LOW_BEAM_AUTO_ENABLE,
        CAR_LIGHT_SETTING_LOW_BEAM_HEIGHT,
        CAR_LIGHT_SETTING_LOW_BEAM_HIGH,
        CAR_LIGHT_SETTING_OLE_AMBIENT_LIGHT_BREATHING_MODE_SWITCH,
        CAR_LIGHT_SETTING_OLE_AMBIENT_LIGHT_DYNAMIC_MODE,
        CAR_LIGHT_SETTING_OLE_AMBIENT_LIGHT_FLOW_MODE_SWITCH,
        CAR_LIGHT_SETTING_OLE_AMBIENT_LIGHT_MULTICOLOR_COLOR_CONFIG,
        CAR_LIGHT_SETTING_OLE_AMBIENT_LIGHT_MULTICOLOR_STATIC_CONFIG,
        CAR_LIGHT_SETTING_OLE_AMBIENT_LIGHT_MUSIC_FOLLOW,
        CAR_LIGHT_SETTING_PARKING_LIGHT_CONFIG,
        CAR_LIGHT_SETTING_PASSENGER_AMBIENT_LIGHT_ENABLE,
        CAR_LIGHT_SETTING_POWER_SAVED_MODE,
        CAR_LIGHT_SETTING_REAR_FOG_LAMP_ENABLE,
        CAR_LIGHT_SETTING_REAR_ROW_AMBIENT_LIGHT_ENABLE,
        CAR_LIGHT_SETTING_SPORT_MODE_LIGHT,
        CAR_LIGHT_SETTING_WELCOME_LIGHT_CONFIG,
        CAR_LIGHT_SETTING_WELCOME_LIGHT_DELAY_TIME,
        CAR_MAP_TSR_NAV_CONTRY_TYPE,
        CAR_MAP_TSR_NAV_ROAD_TYPE,
        CAR_MAP_TSR_NAV_SPEED_LIMIT,
        CAR_MAP_TSR_NAV_SPEED_LIMIT_SIGN_STATUS,
        CAR_MAP_TSR_NAV_SPEED_LIMIT_TYPE,
        CAR_MAP_TSR_NAV_TO_TRAFFIC_EYE_DISTANCE,
        CAR_MAP_TSR_NAV_TRAFIC_SIGN,
        CAR_OFF_ROAD_SETTING_ALL_TERRAIN_MEMORY,
        CAR_OFF_ROAD_SETTING_COO_ENABLE,
        CAR_OFF_ROAD_SETTING_DRIVE_MODE_CONFIG,
        CAR_OFF_ROAD_SETTING_EXPERT_MODE_DRIVE_MODE,
        CAR_OFF_ROAD_SETTING_EXPERT_MODE_ENABLE,
        CAR_OFF_ROAD_SETTING_EXPERT_MODE_ESC,
        CAR_OFF_ROAD_SETTING_EXPERT_MODE_FWD,
        CAR_OFF_ROAD_SETTING_TAB_AVAILABLE,
        CAR_OFF_ROAD_SETTING_TAB_EFFECT_DISPLAY,
        CAR_OFF_ROAD_SETTING_TAB_ENABLE,
        CAR_OMS_AGE_RECOGNITION_ENABLE,
        CAR_OMS_BACK_HOME_BY_GESTURE_ENABLE,
        CAR_OMS_CALL_THE_FUSION,
        CAR_OMS_EXPRESSION_RECOGNITION_ENABLE,
        CAR_OMS_FEEDBACK_AGE_STATE,
        CAR_OMS_FEEDBACK_EXPRESSION_STATE,
        CAR_OMS_FEEDBACK_GENDER_STATE,
        CAR_OMS_FRS,
        CAR_OMS_FRS_CODRIVER_EXIST,
        CAR_OMS_FRS_CODRIVER_EXPRESSION,
        CAR_OMS_FRS_DYNAMIC_GESTURE_STATE,
        CAR_OMS_FRS_OMS_BEHAVIOR_CALL,
        CAR_OMS_FRS_OMS_BEHAVIOR_DRINK,
        CAR_OMS_FRS_OMS_BEHAVIOR_SMOKE,
        CAR_OMS_FRS_OMS_CURRENT_STATE,
        CAR_OMS_FRS_OMS_FACEDB,
        CAR_OMS_FRS_OMS_FACEDB_NUM,
        CAR_OMS_FRS_OMS_NUMBER,
        CAR_OMS_FRS_PASSENGERL_ROW1_AGE,
        CAR_OMS_FRS_PASSENGERL_ROW1_EXIST,
        CAR_OMS_FRS_PASSENGERL_ROW1_EXPRESSION,
        CAR_OMS_FRS_PASSENGERL_ROW1_GENDER,
        CAR_OMS_FRS_PASSENGERM_ROW1_AGE,
        CAR_OMS_FRS_PASSENGERM_ROW1_EXIST,
        CAR_OMS_FRS_PASSENGERM_ROW1_EXPRESSION,
        CAR_OMS_FRS_PASSENGERM_ROW1_GENDER,
        CAR_OMS_FRS_PASSENGERR_ROW1_AGE,
        CAR_OMS_FRS_PASSENGERR_ROW1_EXIST,
        CAR_OMS_FRS_PASSENGERR_ROW1_EXPRESSION,
        CAR_OMS_FRS_PASSENGERR_ROW1_GENDER,
        CAR_OMS_FRS_SEAT_STAFF_INFO,
        CAR_OMS_FRS_STATIC_GESTURE_STATE,
        CAR_OMS_GENDER_RECOGNITION_ENABLE,
        CAR_PARKING_SETTING_APA_FLANKING_PROTECTION,
        CAR_PARKING_SETTING_AUTO_PARK_ACTION,
        CAR_PARKING_SETTING_AUTO_PARK_ENABLE,
        CAR_PARKING_SETTING_AUTO_PARK_MODE_SELECT,
        CAR_PARKING_SETTING_AUTO_PARK_OUT_DIRECTIONS,
        CAR_PARKING_SETTING_AUTO_PARK_SOLT_NUM_SELECT,
        CAR_PARKING_SETTING_AUTO_STEERING_ENABLE,
        CAR_PARKING_SETTING_AUTO_TURN_LIGHT,
        CAR_PARKING_SETTING_AVM_AUTO_EXIT_SPEED_CONFIG,
        CAR_PARKING_SETTING_AVM_SETTING_AVM_AUTO_ACTIVE,
        CAR_PARKING_SETTING_AVM_SETTING_FACTORY_RESET_ENABLE,
        CAR_PARKING_SETTING_AVM_SETTING_ROAD_CALIBRATION_ENABLE,
        CAR_PARKING_SETTING_FPAS_ENABLE,
        CAR_PARKING_SETTING_RADAR_ALARM_SOUND_CONFIG,
        CAR_PARKING_SETTING_RADAR_ALARM_SOUND_ENABLE,
        CAR_PARKING_SETTING_RADAR_SETTING_FRONT_RADAR_AUTO_TURN_ON,
        CAR_PARKING_SETTING_RADAR_SETTING_MEB_STATE,
        CAR_UNIT_SETTING_CLUSTER_SYSTEM,
        CAR_UNIT_SETTING_ODOMETER,
        CAR_WIRELESS_CHARGE_SETTING_CHARGE_STATE,
        CAR_WIRELESS_CHARGE_SETTING_CONFIGURE_ENABLE,
        CAR_WIRELESS_CHARGE_SETTING_VOICE_PROMPT,
        SYS_AUTOLINK_PASSENGER_DISPLAYID,
        SYS_AVM_AUTO_PREVIEW_ENABLE,
        SYS_AVM_CAR_MOVE_FORWARD_MODE_ENABLE,
        SYS_AVM_CLEAN_CAR_MODE_ENABLE,
        SYS_AVM_DEVICE_SUPPORT,
        SYS_AVM_FPAS_MODE_ENABLE,
        SYS_AVM_MEMORY_PARKING_MODE_ENABLE,
        SYS_AVM_MOD_MODE_ENABLE,
        SYS_AVM_MOD_MODE_STATE,
        SYS_AVM_PARKING_ASSIST_MODE_ENABLE,
        SYS_AVM_PARKING_ASSIST_MODE_STATE,
        SYS_AVM_PREVIEW_CONTROL_ACTION,
        SYS_AVM_PREVIEW_MODE_STATE,
        SYS_AVM_PREVIEW_STATUS,
        SYS_AVM_PREVIEW_VEHICLE_TRANSPARENT_STATE,
        SYS_AVM_RADAR_VIEW_MODE,
        SYS_AVM_RIGHT_ASSIST_WARN,
        SYS_AVM_SYSTEM_CHANNEL_STATE,
        SYS_AVM_VIEW_MODE_STATE,
        SYS_BASIC_AUDIO_SOURCE_APP,
        SYS_BASIC_HW_VERSION,
        SYS_BASIC_IVI_POWER_MODE,
        SYS_BASIC_MCU_VERSION,
        SYS_BASIC_MIC_REQUEST_ACTION,
        SYS_BASIC_OS_VERSION,
        SYS_BASIC_POWER_MODE,
        SYS_BASIC_PROJECT_ID,
        SYS_BASIC_PROJECT_NAME,
        SYS_BASIC_SN_CODE,
        SYS_BASIC_TSP_ENV_TYPE,
        SYS_BEAN_ACCOUNT_MODE,
        SYS_BEAN_BOOTANIMATION_COMPLETE,
        SYS_BEAN_BRIGHTNESS_BAR_ENABLE,
        SYS_BEAN_DATATRACK_ENABLE,
        SYS_BEAN_GUIDE_COMPLETE,
        SYS_BEAN_GUIDE_COMPLETE_TIME,
        SYS_BEAN_GUIDE_STATE,
        SYS_BEAN_JOURNEY_ID,
        SYS_BEAN_REMOTE_UPGRADE_STATE,
        SYS_BEAN_VOLUME_BAR_ENABLE,
        SYS_BEANTECHS_ACCOUNT_FEELLESS_ID,
        SYS_BLUETOOTH_A2DP_CONNECTED_DEVICE_INFO,
        SYS_BLUETOOTH_A2DP_CONNECTION_STATE,
        SYS_BLUETOOTH_AVRCP_CONNECTED_DEVICE_INFO,
        SYS_BLUETOOTH_AVRCP_CONNECTION_STATE,
        SYS_BLUETOOTH_AVRCP_FOCUS_STATE,
        SYS_BLUETOOTH_AVRCP_MUSIC_INFO,
        SYS_BLUETOOTH_AVRCP_PLAY_ACTION,
        SYS_BLUETOOTH_AVRCP_PLAY_STATE,
        SYS_BLUETOOTH_HFP_CONNECTION_STATE,
        SYS_BLUETOOTH_LOCAL_DEVICE_STATE,
        SYS_BLUETOOTH_PBAP_CONNECTION_STATE,
        SYS_BYD_COUNTRYCODE,
        SYS_DEBUG_STORAGE_SLOW,
        SYS_DEVICE_EXTERNAL_IMSI,
        SYS_DVR_DPC_ADDP_CONNECTED_STATE,
        SYS_DVR_EMERGENCY_RESCUE_VIDEO_UPLOADED_ENABLE,
        SYS_DVR_REMOTE_SWITCH,
        SYS_DVR_REMOTE_SWITCH_STATE,
        SYS_FFLAG_OVERRIDE,
        SYS_GESTURE_EVENT_NOTIFY,
        SYS_HAP_CAMERA_RIGHT_ASSIST_WARN,
        SYS_HAP_RADAR_RIGHT_ASSIST_WARN,
        SYS_KEY_LEFT,
        SYS_KEY_RIGHT,
        SYS_NETWORK_DATA_NETWORK_ENABLE,
        SYS_NETWORK_HOTSPOT_CLIENT_NUM,
        SYS_NETWORK_HOTSPOT_STATE,
        SYS_NETWORK_MOBILE_NETWORK_SIGNAL_LEVEL,
        SYS_NETWORK_MOBILE_NETWORK_TYPE,
        SYS_NETWORK_SIM_ICCID,
        SYS_NETWORK_SIM_IMSI,
        SYS_NETWORK_SUPPORT_ANDROID_DATA_NETWORK_INTERFACE,
        SYS_NETWORK_SUPPORT_ANDROID_HOTSPOT_INTERFACE,
        SYS_NETWORK_TBOX_HARDWARE_VERSION,
        SYS_NETWORK_TBOX_SN_CODE,
        SYS_NETWORK_TBOX_SOFEWARE_VERSION,
        SYS_NETWORK_TBOX_CONNECTION_STATE,
        SYS_OTHER_KEYEVENT_NOTIFY,
        SYS_OTHER_REQUEST_EVENT_SYNC,
        SYS_OTHER_REQUEST_UPDATE_AUDIO_ACOUSTICS_ACTION,
        SYS_PKI_HTTP_CODE,
        SYS_RADIO_AM_FAVORITES_STATION_LIST,
        SYS_RADIO_AM_VALID_STATION_LIST,
        SYS_RADIO_CUR_CHANNEL_INFO,
        SYS_RADIO_DRM_FAVORITES_STATION_LIST,
        SYS_RADIO_DRM_VALID_STATION_LIST,
        SYS_RADIO_FAVORITE_CUR_STATION_ACTION,
        SYS_RADIO_FM_FAVORITES_STATION_LIST,
        SYS_RADIO_FM_VALID_STATION_LIST,
        SYS_RADIO_PLAY_CONTROL_ACTION,
        SYS_RADIO_PLAY_STATE,
        SYS_RADIO_RDS_AM_FAVORITE_STATION_LIST,
        SYS_RADIO_RDS_AM_VALID_STATION_LIST,
        SYS_RADIO_RDS_CUR_CHANNEL_INFO,
        SYS_RADIO_RDS_DAB_ALTERNATIVE_FREQUENCY_STATE,
        SYS_RADIO_RDS_DAB_FAVORITE_STATION_LIST,
        SYS_RADIO_RDS_DAB_RESET_ALL_STATE,
        SYS_RADIO_RDS_DAB_VALID_STATION_LIST,
        SYS_RADIO_RDS_FAVORITE_CUR_STATION_ACTION,
        SYS_RADIO_RDS_FM_ALTERNATIVE_FREQUENCY_STATE,
        SYS_RADIO_RDS_FM_FAVORITE_STATION_LIST,
        SYS_RADIO_RDS_FM_VALID_STATION_LIST,
        SYS_RADIO_RDS_REGIONAL_INFO,
        SYS_RADIO_RDS_TRAFFIC_ANNOUNCEMENT_ACTIVE_STATE,
        SYS_RADIO_RDS_TRAFFIC_ANNOUNCEMENT_STATE,
        SYS_RADIO_RDS_TRAFFIC_PROGRAM_STATE,
        SYS_RADIO_SEARCH_PROGRESS,
        SYS_RADIO_SEARCH_STATE,
        SYS_RVC_DEVICE_SUPPORT,
        SYS_RVC_FPAS_ENABLE,
        SYS_RVC_MEB_ENABLE,
        SYS_RVC_RADAR_SYSTEM_CHANNEL_STATE,
        SYS_RVC_SYSTEM_CHANNEL_STATE,
        SYS_RVC_TRAILER_MODE_ENABLE,
        SYS_RVC_VIEW_MODE,
        SYS_SETTINGS_AUDIO_ANC_ENABLE,
        SYS_SETTINGS_AUDIO_BOOT_MUSIC_SOUND_ENABLE,
        SYS_SETTINGS_AUDIO_DRIVER_SOUND_VIBRATION_LEVEL,
        SYS_SETTINGS_AUDIO_DRIVESEAT_RHYTHM_LEVEL,
        SYS_SETTINGS_AUDIO_EQ_ALTO_VALUE,
        SYS_SETTINGS_AUDIO_EQ_BASS_VALUE,
        SYS_SETTINGS_AUDIO_EQ_TREBLE_VALUE,
        SYS_SETTINGS_AUDIO_EQ_VALUE_RANGE,
        SYS_SETTINGS_AUDIO_EXTERNAL_AMP_ENABLE,
        SYS_SETTINGS_AUDIO_MEDIA_MUTE_STATE,
        SYS_SETTINGS_AUDIO_MEDIA_VOLUME,
        SYS_SETTINGS_AUDIO_MEDIA_VOLUME_RANGE,
        SYS_SETTINGS_AUDIO_MUTE_ADJUST_ACTION,
        SYS_SETTINGS_AUDIO_MUTE_STATE,
        SYS_SETTINGS_AUDIO_NAVI_MUTE_STATE,
        SYS_SETTINGS_AUDIO_NAVI_TTS_MIX_MEDIA_ENABLE,
        SYS_SETTINGS_AUDIO_NAVI_VOLUME,
        SYS_SETTINGS_AUDIO_NAVI_VOLUME_RANGE,
        SYS_SETTINGS_AUDIO_PASSENGER_HEADSET_MUTE_STATE,
        SYS_SETTINGS_AUDIO_PASSENGERSEAT_RHYTHM_LEVEL,
        SYS_SETTINGS_AUDIO_PHONE_VOLUME,
        SYS_SETTINGS_AUDIO_PHONE_VOLUME_RANGE,
        SYS_SETTINGS_AUDIO_PRECISEBASS_STATE,
        SYS_SETTINGS_AUDIO_RING_MUTE_STATE,
        SYS_SETTINGS_AUDIO_RING_VOLUME,
        SYS_SETTINGS_AUDIO_RING_VOLUME_RANGE,
        SYS_SETTINGS_AUDIO_SOUND_EFFECT_DTS_STATE,
        SYS_SETTINGS_AUDIO_SOUND_FIELD_VALUE,
        SYS_SETTINGS_AUDIO_SOUND_FIELD_VALUE_RANGE,
        SYS_SETTINGS_AUDIO_SOUND_MODE_SWITCH,
        SYS_SETTINGS_AUDIO_SOUND_PRECISE_BASS,
        SYS_SETTINGS_AUDIO_SOUND_QUALITY_RESTORE_ENABLE,
        SYS_SETTINGS_AUDIO_SOUND_SURROUND_ASK,
        SYS_SETTINGS_AUDIO_SOUND_SURROUND_ENABLE,
        SYS_SETTINGS_AUDIO_SOUND_SURROUND_HARMAN_KARDON,
        SYS_SETTINGS_AUDIO_SOUND_SURROUND_MODE,
        SYS_SETTINGS_AUDIO_SYSTEM_ACOUSTICS_ENABLE,
        SYS_SETTINGS_AUDIO_TOUCH_TONE_ENABLE,
        SYS_SETTINGS_AUDIO_VEHICLE_ALARM_ENABLE,
        SYS_SETTINGS_AUDIO_VEHICLE_WARNING_TTS_ENABLE,
        SYS_SETTINGS_AUDIO_VOICE_MUTE_STATE,
        SYS_SETTINGS_AUDIO_VOICE_VOLUME,
        SYS_SETTINGS_AUDIO_VOICE_VOLUME_RANGE,
        SYS_SETTINGS_AUDIO_VOLUME_ADAPTATION_ENABLE,
        SYS_SETTINGS_AUDIO_VOLUME_ADJUST_ACTION,
        SYS_SETTINGS_AUDIO_VOLUME_ADJUST_BY_SPEED_STATE,
        SYS_SETTINGS_AUDIO_VOLUME_ADJUST_BY_WIND_STATE,
        SYS_SETTINGS_AUDIOCHAN_MODE,
        SYS_SETTINGS_CLUSTER_VIEW_DISPLAY,
        SYS_SETTINGS_DISPLAY_AUTO_BRIGHTNESS_ENABLE,
        SYS_SETTINGS_DISPLAY_BACKLIGHT_STATE,
        SYS_SETTINGS_DISPLAY_BRIGHTNESS_LEVEL,
        SYS_SETTINGS_DISPLAY_BRIGHTNESS_RANGE,
        SYS_SETTINGS_DISPLAY_VEHICLE_BACKLIGHT,
        SYS_SETTINGS_DISPLAY_PASSENGER_BRIGHTNESS_LEVEL,
        SYS_SETTINGS_EXT_BLE_VOLUME,
        SYS_SETTINGS_EXT_BLE_VOLUME_MUTE,
        SYS_SETTINGS_GENERAL_FACTORY_RESET_ACTION,
        SYS_SETTINGS_GENERAL_RESET_ALL_ACTION,
        SYS_SETTINGS_GENERAL_RESET_ALL_STATE,
        SYS_SETTINGS_GENERAL_TIME_ZONE_SYNCH,
        SYS_SETTINGS_LANGUAGE,
        SYS_STEM_CELLS_BOOT_TRIGGERS,
        SYS_TBOX_CALL_ACTION,
        SYS_TBOX_CUR_CALL_INFO,
        SYS_TEST_ID1,
        SYS_TEST_ID2,
        SYS_TEST_ID3,
        SYS_TEST_ID4,
        SYS_TEST_PROJECT_CODE,
        SYS_USB_MTP_DEVICE_TYPE,
        SYS_WIFI_DRIVER_VERSION,
        SYS_WIFI_FIRMWARE_VERSION,
        SYS_WIFI_HARDWARE_VERSION,
        SYS_WIFI_MAC,
        SYS_WIFI_SOFTAP_INTERFACE_NAME,
    )

    private val porChave = TODOS.associateBy { it.chave }

    /** O sinal desta chave, ou null se ela não está no catálogo. */
    @JvmStatic fun daChave(chave: String): Sinal? = porChave[chave]

    /** Só os textos das chaves, na mesma ordem. */
    @JvmStatic fun chaves(sinais: Array<out Sinal>): Array<String> = Array(sinais.size) { sinais[it].chave }

    /** Só os textos das chaves, na mesma ordem. */
    @JvmStatic fun chaves(sinais: Collection<Sinal>): Array<String> = sinais.map { it.chave }.toTypedArray()
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
