// Declaração equivalente à da fabricante (GWM/Beantechs/Autolink), central BUX1, Android 9.
// Fonte: BeanInputService (com.beantechs.inputservice 1.0.0, versionCode 1), system/priv-app/BeanInputService-app-Release/BeanInputService-app-Release.apk.
// Ordem dos métodos = número da transação (comentário no fim de cada linha), conferido no dex.
// Nomes de parâmetro escolhidos aqui (o dex da fabricante não os guarda).
package com.beantechs.inputservice;

import android.content.ComponentName;
import android.view.KeyEvent;
import com.beantechs.inputservice.IDataChangedListener;
import com.beantechs.inputservice.IInputListener;

interface IInputService {
    void registerKeyEventListener(in int[] arg0, IInputListener listener); // 1
    void unregisterKeyEventListener(in int[] arg0, IInputListener listener); // 2
    void registerGlobalButtonListener(in int[] arg0, in ComponentName component); // 3
    void unregisterGlobalButtonListener(in int[] arg0, in ComponentName component); // 4
    void registerDataListener(in String[] arg0, IDataChangedListener listener); // 5
    void unregisterDataListener(in String[] arg0, IDataChangedListener listener); // 6
    void injectKeyEvent(in KeyEvent event, int arg1); // 7
    void enterScene(int arg0); // 8
    void exitScene(int arg0); // 9
}
