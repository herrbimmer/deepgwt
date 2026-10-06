// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.managers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TopoDaTelaPrincipalTest {

    /** `am stack list` do Android 9 (ActivityManager.StackInfo.toString), a tela de fábrica na frente. */
    private val android9 = """
        Stack id=0 bounds=[0,0][1920,720] displayId=0 userId=0
         configuration={1.0 ?mcc?mnc [pt_BR] ldltr sw720dp w1792dp h720dp 160dpi lrg long land car finger -keyb/v/h -nav/h winConfig={ mBounds=Rect(0, 0 - 1920, 720) mAppBounds=Rect(128, 0 - 1920, 660) mWindowingMode=fullscreen mActivityType=home} s.12}
          taskId=4: com.beantechs.mediacenter/com.beantechs.mediacenter.MainActivity bounds=[0,0][1920,720] userId=0 visible=true topActivity=ComponentInfo{com.beantechs.mediacenter/com.beantechs.mediacenter.MainActivity}
        Stack id=7 bounds=[0,0][1920,660] displayId=0 userId=0
         configuration={1.0 winConfig={ mBounds=Rect(0, 0 - 1920, 660) mWindowingMode=freeform mActivityType=standard} s.12}
          taskId=31: com.spotify.music/.MainActivity bounds=[0,0][1920,660] userId=0 visible=false topActivity=ComponentInfo{com.spotify.music/com.spotify.music.MainActivity}
        Stack id=2 bounds=[0,62][1920,658] displayId=1 userId=0
         configuration={1.0 winConfig={ mWindowingMode=fullscreen mActivityType=standard} s.12}
          taskId=9: com.ts.carplay.app/.MainActivity bounds=[0,62][1920,658] userId=0 visible=true
    """.trimIndent()

    @Test
    fun `android 9, tela de fabrica na frente como inicio`() {
        assertEquals(TopoDaTelaPrincipal.Topo("com.beantechs.mediacenter", true), TopoDaTelaPrincipal.ler(android9))
    }

    @Test
    fun `app comum na frente nao e inicio, e outra tela nao conta`() {
        val spotify = android9.replace("mActivityType=home} s.12}\n  taskId=4: com.beantechs.mediacenter/com.beantechs.mediacenter.MainActivity bounds=[0,0][1920,720] userId=0 visible=true",
            "mActivityType=home} s.12}\n  taskId=4: com.beantechs.mediacenter/com.beantechs.mediacenter.MainActivity bounds=[0,0][1920,720] userId=0 visible=false")
            .replace("userId=0 visible=false topActivity=ComponentInfo{com.spotify", "userId=0 visible=true topActivity=ComponentInfo{com.spotify")
        assertEquals(TopoDaTelaPrincipal.Topo("com.spotify.music", false), TopoDaTelaPrincipal.ler(spotify))
        assertEquals(TopoDaTelaPrincipal.Topo("com.ts.carplay.app", false), TopoDaTelaPrincipal.ler(android9, displayId = 1))
        assertNull(TopoDaTelaPrincipal.ler(""))
    }

    @Test
    fun `android 14 escreve RootTask e continua valendo`() {
        val a14 = """
            RootTask id=1000642 bounds=[0,76][1080,676] displayId=0 userId=10
             configuration={1.0 winConfig={ mBounds=Rect(0, 76 - 1080, 676) mWindowingMode=freeform mDisplayWindowingMode=fullscreen mActivityType=home mAlwaysOnTop=undefined} as.2}
              taskId=1000642: br.com.deepgwt/br.com.deepgwt.tela3d.ponte.TelaInicial3dActivity bounds=[0,76][1080,676] userId=10 visible=true topActivity=ComponentInfo{br.com.deepgwt/br.com.deepgwt.tela3d.ponte.TelaInicial3dActivity}
        """.trimIndent()
        assertEquals(TopoDaTelaPrincipal.Topo("br.com.deepgwt", true), TopoDaTelaPrincipal.ler(a14))
    }

    @Test
    fun `janela que cobre so um pedaco da tela nao esconde a tela inicial atras dela`() {
        // Launcher automotivo do emulador: o mapa embutido é uma pilha à parte, de 1420 px.
        val embutido = """
            RootTask id=1000669 bounds=[500,0][1920,720] displayId=0 userId=10
             configuration={1.0 winConfig={ mWindowingMode=multi-window mActivityType=standard} as.2}
              taskId=1000669: com.android.car.mapsplaceholder/com.android.car.mapsplaceholder.MapsPlaceholderActivity bounds=[500,0][1920,720] userId=10 visible=true topActivity=ComponentInfo{com.android.car.mapsplaceholder/com.android.car.mapsplaceholder.MapsPlaceholderActivity}
            RootTask id=1000661 bounds=[0,0][1920,720] displayId=0 userId=10
             configuration={1.0 winConfig={ mWindowingMode=fullscreen mActivityType=home} as.2}
              taskId=1000662: com.android.car.carlauncher/com.android.car.carlauncher.CarLauncher bounds=[0,0][1920,720] userId=10 visible=true topActivity=ComponentInfo{com.android.car.carlauncher/com.android.car.carlauncher.CarLauncher}
        """.trimIndent()
        assertEquals(TopoDaTelaPrincipal.Topo("com.android.car.carlauncher", true), TopoDaTelaPrincipal.ler(embutido, 0, 1920, 720))
        // Sem o tamanho da tela, a regra antiga: a primeira visível.
        assertEquals(TopoDaTelaPrincipal.Topo("com.android.car.mapsplaceholder", false), TopoDaTelaPrincipal.ler(embutido))
        // Um app encolhido acima da barra (660 de 720) continua sendo "o da frente".
        val app = embutido.replace("bounds=[500,0][1920,720] displayId", "bounds=[0,0][1920,660] displayId")
        assertEquals(TopoDaTelaPrincipal.Topo("com.android.car.mapsplaceholder", false), TopoDaTelaPrincipal.ler(app, 0, 1920, 720))
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
