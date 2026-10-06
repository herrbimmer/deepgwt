# ---------------------------------------------------------------------------------------------
# Interfaces da central (pacote com.autolink)
# O app conversa com a central por contratos AIDL e Parcelable desse pacote. O R8 não pode
# renomear nem podar nada nele: o outro lado só conhece os nomes originais.
# ---------------------------------------------------------------------------------------------
-keep class com.autolink.** {
    <init>(...);
    <fields>;
    <methods>;
}

# DCM da central: Parcelable que o app lê; o nome e os campos precisam ficar como estão.
-keep class ts.car.dcm.common.data.ConnDevice { *; }

# ---------------------------------------------------------------------------------------------
# Log: nenhuma saída de depuração no APK de release (nível 7 = tudo, inclusive `wtf`).
# Os builds de debug e de simulador não passam por aqui e seguem com log para a bancada.
# ---------------------------------------------------------------------------------------------
-maximumremovedandroidloglevel 7
-assumenosideeffects public class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
    public static int println(...);
    public static boolean isLoggable(...);
    public static java.lang.String getStackTraceString(...);
}

# ---------------------------------------------------------------------------------------------
# Tela inicial 3D nova (pacote br.com.deepgwt.tela3d, dentro deste APK)
# Mesmas razões do bloco antigo abaixo: reflexão sobre API oculta do Android (janelas) e os métodos
# `@JavascriptInterface` das quatro pontes, que só o JavaScript da página chama. As Activities e os
# serviços chegam pelo manifesto, mas o pacote inteiro fica de fora do R8: o custo é pequeno e quebrar
# só em release é a pior forma de quebrar.
# ---------------------------------------------------------------------------------------------
-keep class br.com.deepgwt.tela3d.** {
    <init>(...);
    <fields>;
    <methods>;
}
-keepclassmembers class br.com.deepgwt.tela3d.** {
    @android.webkit.JavascriptInterface <methods>;
}

# Cascas de nome dos dois serviços da tela 3D (acesso concedido no carro é por componente).
-keep class com.havalh6.viewer.** { <init>(...); }
