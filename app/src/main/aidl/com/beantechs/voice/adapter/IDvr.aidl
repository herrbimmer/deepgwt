// Declaração equivalente à da fabricante (GWM/Beantechs/Autolink), central BUX1, Android 9.
// Fonte: VRAdapt (com.beantechs.voice.adapter 1.0.1, versionCode 20230815), system/priv-app/VRAdapt-app-asean/VRAdapt-app-asean.apk.
// Ordem dos métodos = número da transação (comentário no fim de cada linha), conferido no dex.
// Nomes de parâmetro escolhidos aqui (o dex da fabricante não os guarda).
package com.beantechs.voice.adapter;

interface IDvr {
    boolean isInDvrApp(); // 1
    int getCaptureStatus(); // 2
    boolean isInPreviewView(); // 3
    int getAVMSupport(int arg0, int arg1); // 4
    int getCurrentMode(); // 5
    boolean isDvrSupported(); // 6
    int getAvmAutoExitSpeed(); // 7
    oneway void startBeanDvr(); // 8
    oneway void closeBeanDvr(); // 9
    oneway void capturePhoto(int arg0, int arg1); // 10
    oneway void captureVideo(int arg0, int arg1); // 11
    oneway void setAVM(int arg0); // 12
    int getAvmPreviewStatus(); // 13
}
