// Declaração equivalente à da fabricante (GWM/Beantechs/Autolink), central BUX1, Android 9.
// Fonte: VRAdapt (com.beantechs.voice.adapter 1.0.1, versionCode 20230815), system/priv-app/VRAdapt-app-asean/VRAdapt-app-asean.apk.
// Ordem dos métodos = número da transação (comentário no fim de cada linha), conferido no dex.
// Nomes de parâmetro escolhidos aqui (o dex da fabricante não os guarda).
package com.beantechs.voice.adapter;

import com.beantechs.voice.adapter.IVehicleCallback;

interface IVehicle {
    oneway void registerVehicleCallback(IVehicleCallback callback); // 1
    oneway void unregisterVehicleCallback(IVehicleCallback callback); // 2
    int getVehicleSpeed(int arg0, int arg1); // 3
    double getRainIntensity(double arg0, int arg1); // 4
    double getPM25(double arg0, int arg1); // 5
    int[] getWindowsStatus(int arg0); // 6
    oneway void setWindowStatus(int arg0, int arg1); // 7
    int getSkylightLevel(int arg0); // 8
    oneway void setSkylightLevel(int arg0); // 9
    int getShadeScreensLevel(int arg0); // 10
    oneway void setShadeScreensLevel(int arg0); // 11
    int isWindowAndSunRoofAndSunCurtainAvailable(int arg0, int arg1, int arg2); // 12
    int isHudAvailable(int arg0, int arg1); // 13
    int getHudOpenState(int arg0, int arg1); // 14
    int getHudBrightness(int arg0, int arg1); // 15
    int getHudHeight(int arg0, int arg1); // 16
    int getHudAngle(int arg0, int arg1); // 17
    oneway void setHudOpen(int arg0); // 18
    oneway void setHudAutoBrightOpen(int arg0); // 19
    oneway void setHudBrightness(int arg0); // 20
    oneway void setHudHeight(int arg0); // 21
    oneway void setHudAngle(int arg0); // 22
    int isHeatingSteeringWheelAvailable(int arg0, int arg1); // 23
    int isHeatingSteeringWheelOpened(int arg0, int arg1); // 24
    oneway void setHeatingSteeringWheelOpen(int arg0); // 25
    int isDoorAvailable(int arg0, int arg1, int arg2); // 26
    int isDoorOpened(int arg0, int arg1, int arg2); // 27
    oneway void setDoorOpen(int arg0, int arg1); // 28
    int isSupportSeatHeating(int arg0); // 29
    int isSupportSeatVentilation(int arg0); // 30
    int[] getSeatHeatingMaxLevel(int arg0); // 31
    int[] getSeatVentilationMaxLevel(int arg0); // 32
    int getSeatHeatingLevel(int arg0, int arg1, int arg2); // 33
    int getSeatVentilationLevel(int arg0, int arg1, int arg2); // 34
    oneway void setSeatHeatingLevel(int arg0, int arg1); // 35
    oneway void setSeatVentilationLevel(int arg0, int arg1); // 36
    int isSupportSeatPositionAdjust(int arg0, int arg1); // 37
    int getSeatsAvailableForPositionAdjust(int arg0, int arg1, int arg2); // 38
    int[] getSeatPositions(int arg0, int arg1); // 39
    oneway void setSeatPosition(int arg0, int arg1); // 40
    int isSupportDriverSeatMemory(int arg0, int arg1); // 41
    int getDriverSeatMemory(int arg0, int arg1); // 42
    int isDriverSeatMatchMemory(int arg0, int arg1); // 43
    oneway void setDriverSeatToMemory(int arg0); // 44
    int isAmbientLightOpened(int arg0, int arg1); // 45
    int isAmbientLightStaticOpened(int arg0, int arg1); // 46
    int isAmbientLightDynamicOpened(int arg0, int arg1); // 47
    int isAmbientLightAvailable(int arg0, int arg1); // 48
    oneway void setAmbientLightOpen(int arg0); // 49
    oneway void setAmbientLightStaticOpen(int arg0); // 50
    oneway void setAmbientLightDynamicOpen(int arg0); // 51
    int getAmbientLightColor(int arg0, int arg1); // 52
    oneway void setAmbientLightColor(int arg0); // 53
    int getAmbientLightBrightness(int arg0, int arg1); // 54
    oneway void setAmbientLightBrightness(int arg0); // 55
    int isAmbientLightRhythmAvailable(int arg0, int arg1); // 56
    int isAmbientLightRhythmOpened(int arg0, int arg1); // 57
    oneway void setAmbientLightRhythmOpen(int arg0); // 58
    oneway void setVoiceWindowControlAction(in String[] arg0); // 59
    int getCarEngineType(); // 60
    int getSupportDriveMode(); // 61
    int getDriveMode(); // 62
    void setDriveMode(int arg0); // 63
    int getTotalOdometer(); // 64
    int getRemainOdometer(); // 65
    int getFrontWipwerStatus(); // 66
    void setFrontWipwerStatus(int arg0); // 67
    int getMeditationModeState(); // 68
    void setMeditationModeState(int arg0); // 69
    int getCarUnitSetting(); // 70
    float queryFuelConsumption(); // 71
    float queryEnergyConsumption(); // 72
    float queryMileage(); // 73
    int isSupportRearViewMirrorFold(); // 74
    int getRearViewMirrorFoldState(); // 75
    oneway void setRearViewMirrorFoldState(int arg0); // 76
    int[] getSeatHeatingMaxLevelBate(); // 77
    int[] getSeatVentilationMaxLevelBate(); // 78
    int isSupportHudSnowMode(); // 79
    int getHudSnowModeState(); // 80
    oneway void setHudSnowModeState(int arg0); // 81
    int[] getAmbientLightBrightnessRange(); // 82
    int getBasicPowerModeState(); // 83
    int isSupportHudModuleDisplay(int arg0, int arg1); // 84
    int getHudHudModuleDisplayState(int arg0); // 85
    oneway void setHudModuleDisplayState(int arg0, int arg1); // 86
    oneway void setWiperState(int arg0); // 87
    int getWiperState(); // 88
    int getDoorLockStatus(); // 89
}
