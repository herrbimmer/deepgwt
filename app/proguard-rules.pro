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
# Tela inicial 3D (pacote com.havalh6.viewer, dentro deste APK)
# Mantemos o pacote inteiro porque ele é cheio de reflexão sobre API oculta do Android
# (`resizeTask`, `setTaskAlwaysOnTop`, `setHiddenApiExemptions` e outras): o R8 não vê esses usos,
# renomearia ou removeria os membros e a tela inicial quebraria só em release, a pior forma de
# quebrar. Pelo mesmo motivo ficam os métodos `@JavascriptInterface`, que só o JavaScript chama.
# ---------------------------------------------------------------------------------------------
-keep class com.havalh6.viewer.** {
    <init>(...);
    <fields>;
    <methods>;
}
-keepclassmembers class com.havalh6.viewer.** {
    @android.webkit.JavascriptInterface <methods>;
}
