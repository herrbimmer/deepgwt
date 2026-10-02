package com.havalh6.viewer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import br.com.opengw.R;

final class ProjectionPresence {
    private static final String AA_ACTIVITY = "com.ts.androidauto.app.display.AapActivity";
    private static final String AA_APP = "com.ts.androidauto.app";
    private static final String AA_DESCRIPTOR = "com.ts.androidauto.sdk.aidl.LinkCommand";
    private static final String AA_SERVICE_ACTION = "com.ts.androidauto.action.AndroidAutoService";
    private static final String AA_SERVICE_CLASS = "com.ts.androidauto.projectionservice.AndroidAutoService";
    private static final String AA_SERVICE_PACKAGE = "com.ts.androidauto.projectionservice";
    private static final int AA_TX_GET_LINK_STATUS = 21;
    private static final String CP_ACTIVITY = "com.ts.carplay.app.ui.display.view.CarPlayDisplayActivity";
    private static final String CP_APP = "com.ts.carplay.app";
    private static final String CP_DESCRIPTOR = "com.ts.carplay.common.aidl.ICarPlayService";
    private static final String CP_HOST = "com.ts.carplay";
    private static final int CP_LINK_ACTIVATED = 2;
    private static final String CP_SERVICE_CLASS = "com.ts.carplay.CarPlayService";
    private static final int CP_TX_GET_LINK_STATUS = 29;
    private static final long POLL_MS = 2000;
    private static final String TAG = "H6Viewer";
    private volatile IBinder aaBinder;
    private ServiceConnection aaConnection;
    private final Context app;
    private volatile IBinder cpBinder;
    private ServiceConnection cpConnection;
    private Listener listener;
    private final Handler main;
    private final MediaNowPlaying media;
    private boolean started;
    private Handler worker;
    private volatile Kind lastKind = Kind.NONE;
    private final Runnable tick = new Runnable() {
        @Override // java.lang.Runnable
        public void run() {
            Kind kindDetect = ProjectionPresence.this.detect();
            if (kindDetect != ProjectionPresence.this.lastKind) {
                ProjectionPresence.this.lastKind = kindDetect;
                Log.w("H6Viewer", "Projection slot " + kindDetect);
                ProjectionPresence.this.main.post(() -> {
                    if (ProjectionPresence.this.listener != null) {
                        ProjectionPresence.this.listener.onProjectionChanged(kindDetect);
                    }
                });
            }
            if (ProjectionPresence.this.worker != null) {
                ProjectionPresence.this.worker.postDelayed(this, 2000L);
            }
        }
    };

    enum Kind {
        NONE,
        ANDROID_AUTO,
        CARPLAY
    }

    interface Listener {
        void onProjectionChanged(Kind kind);
    }

    ProjectionPresence(Context context, Handler main, MediaNowPlaying mediaNowPlaying) {
        this.app = context.getApplicationContext();
        this.main = main;
        this.media = mediaNowPlaying;
    }

    private static boolean activityLaunchable(PackageManager packageManager, ComponentName componentName) {
        try {
            ActivityInfo activityInfo = packageManager.getActivityInfo(componentName, 0);
            return activityInfo != null && activityInfo.exported;
        } catch (Throwable th) {
            return false;
        }
    }

    static String androidAutoAppPackage() {
        return "com.ts.androidauto.app";
    }

    private boolean androidAutoLinked() {
        Integer num = readInt(ensureAaBinder(), "com.ts.androidauto.sdk.aidl.LinkCommand", 21);
        return num != null && (num.intValue() == 3 || num.intValue() == 7 || num.intValue() == 8);
    }

    private boolean carPlayLinked() {
        Integer num = readInt(ensureCpBinder(), "com.ts.carplay.common.aidl.ICarPlayService", 29);
        return num != null && num.intValue() == 2;
    }

    static ComponentName componentFor(Kind kind) {
        if (kind == Kind.ANDROID_AUTO) {
            return new ComponentName("com.ts.androidauto.app", "com.ts.androidauto.app.display.AapActivity");
        }
        if (kind == Kind.CARPLAY) {
            return new ComponentName("com.ts.carplay.app", "com.ts.carplay.app.ui.display.view.CarPlayDisplayActivity");
        }
        return null;
    }

    private Kind detect() {
        boolean z = false;
        boolean z2 = carPlayLinked() || (this.media != null && this.media.hasCarPlayTrack());
        if (androidAutoLinked() || (this.media != null && this.media.hasAndroidAutoTrack())) {
            z = true;
        }
        if (!z2 || !z) {
            if (z2) {
                return Kind.CARPLAY;
            }
            return z ? Kind.ANDROID_AUTO : Kind.NONE;
        }
        if (this.media == null || !this.media.hasCarPlayTrack() || this.media.hasAndroidAutoTrack()) {
            return (this.media == null || !this.media.hasAndroidAutoTrack() || this.media.hasCarPlayTrack()) ? Kind.CARPLAY : Kind.ANDROID_AUTO;
        }
        return Kind.CARPLAY;
    }

    static String[] displayPackagesFor(Kind kind) {
        if (kind == Kind.ANDROID_AUTO) {
            return new String[]{"com.ts.androidauto.app", "com.autolink.androidauto.app"};
        }
        return kind == Kind.CARPLAY ? new String[]{"com.ts.carplay.app", "com.autolink.carplay.app"} : new String[0];
    }

    private IBinder ensureAaBinder() {
        IBinder iBinder = this.aaBinder;
        if (iBinder != null && iBinder.isBinderAlive()) {
            return iBinder;
        }
        if (this.aaConnection != null) {
            return this.aaBinder;
        }
        this.aaConnection = new ServiceConnection() {
            @Override // android.content.ServiceConnection
            public void onServiceConnected(ComponentName componentName, IBinder aaBinder) {
                ProjectionPresence.this.aaBinder = aaBinder;
            }

            @Override // android.content.ServiceConnection
            public void onServiceDisconnected(ComponentName componentName) {
                ProjectionPresence.this.aaBinder = null;
            }
        };
        try {
            Intent intent = new Intent("com.ts.androidauto.action.AndroidAutoService");
            intent.setComponent(new ComponentName("com.ts.androidauto.projectionservice", "com.ts.androidauto.projectionservice.AndroidAutoService"));
            if (!this.app.bindService(intent, this.aaConnection, 1)) {
                unbind(this.aaConnection);
                this.aaConnection = null;
            }
        } catch (Throwable th) {
            Log.w("H6Viewer", "Android Auto bind failed", th);
            this.aaConnection = null;
        }
        return this.aaBinder;
    }

    private IBinder ensureCpBinder() {
        IBinder iBinder = this.cpBinder;
        if (iBinder != null && iBinder.isBinderAlive()) {
            return iBinder;
        }
        if (this.cpConnection != null) {
            return this.cpBinder;
        }
        this.cpConnection = new ServiceConnection() {
            @Override // android.content.ServiceConnection
            public void onServiceConnected(ComponentName componentName, IBinder cpBinder) {
                ProjectionPresence.this.cpBinder = cpBinder;
            }

            @Override // android.content.ServiceConnection
            public void onServiceDisconnected(ComponentName componentName) {
                ProjectionPresence.this.cpBinder = null;
            }
        };
        try {
            if (!this.app.bindService(new Intent().setComponent(new ComponentName("com.ts.carplay", "com.ts.carplay.CarPlayService")), this.cpConnection, 1)) {
                unbind(this.cpConnection);
                this.cpConnection = null;
            }
        } catch (Throwable th) {
            Log.w("H6Viewer", "CarPlay bind failed", th);
            this.cpConnection = null;
        }
        return this.cpBinder;
    }

    static boolean isProjectionPackage(String str) {
        if (str == null) {
            return false;
        }
        String lowerCase = str.toLowerCase();
        return lowerCase.equals("com.ts.androidauto.app") || lowerCase.equals("com.ts.androidauto") || lowerCase.equals("com.ts.androidauto.projectionservice") || lowerCase.equals("com.ts.carplay.app") || lowerCase.equals("com.ts.carplay") || lowerCase.contains("androidauto") || lowerCase.contains("carplay") || lowerCase.contains("projection.gearhead");
    }

    static Kind kindForPackage(String str) {
        if (isProjectionPackage(str)) {
            return str.toLowerCase().contains("carplay") ? Kind.CARPLAY : Kind.ANDROID_AUTO;
        }
        return Kind.NONE;
    }

    private static String[] packagesFor(Kind kind) {
        if (kind == Kind.ANDROID_AUTO) {
            return new String[]{"com.ts.androidauto.app", "com.autolink.androidauto.app", "com.ts.androidauto", "com.ts.androidauto.projectionservice"};
        }
        return kind == Kind.CARPLAY ? new String[]{"com.ts.carplay.app", "com.autolink.carplay.app", "com.ts.carplay"} : new String[0];
    }

    private static Integer readInt(IBinder iBinder, String str, int i) {
        if (iBinder == null || !iBinder.isBinderAlive()) {
            return null;
        }
        Parcel parcelObtain = Parcel.obtain();
        Parcel parcelObtain2 = Parcel.obtain();
        try {
            parcelObtain.writeInterfaceToken(str);
            if (!iBinder.transact(i, parcelObtain, parcelObtain2, 0)) {
                parcelObtain2.recycle();
                parcelObtain.recycle();
                return null;
            }
            try {
                parcelObtain2.readException();
            } catch (Throwable th) {
            }
            Integer numValueOf = Integer.valueOf(parcelObtain2.readInt());
            parcelObtain2.recycle();
            parcelObtain.recycle();
            return numValueOf;
        } catch (Throwable th2) {
            parcelObtain2.recycle();
            parcelObtain.recycle();
            return null;
        }
    }

    private void sendAaTransact(int i) {
        IBinder iBinderEnsureAaBinder = ensureAaBinder();
        if (iBinderEnsureAaBinder == null || !iBinderEnsureAaBinder.isBinderAlive()) {
            return;
        }
        Parcel parcelObtain = Parcel.obtain();
        try {
            parcelObtain.writeInterfaceToken("com.ts.androidauto.sdk.aidl.LinkCommand");
            iBinderEnsureAaBinder.transact(i, parcelObtain, null, 1);
        } catch (Throwable th) {
        }
        parcelObtain.recycle();
    }

    private void sendCpTransact(int i) {
        IBinder iBinderEnsureCpBinder = ensureCpBinder();
        if (iBinderEnsureCpBinder == null || !iBinderEnsureCpBinder.isBinderAlive()) {
            return;
        }
        Parcel parcelObtain = Parcel.obtain();
        try {
            parcelObtain.writeInterfaceToken("com.ts.carplay.common.aidl.ICarPlayService");
            iBinderEnsureCpBinder.transact(i, parcelObtain, null, 1);
        } catch (Throwable th) {
        }
        parcelObtain.recycle();
    }

    private void unbind(ServiceConnection serviceConnection) {
        if (serviceConnection == null) {
            return;
        }
        try {
            this.app.unbindService(serviceConnection);
        } catch (Throwable th) {
        }
    }

    Kind current() {
        return this.lastKind;
    }

    Drawable iconFor(Kind kind) {
        if (kind == Kind.NONE) {
            return null;
        }
        try {
            return this.app.getDrawable(kind == Kind.ANDROID_AUTO ? R.drawable.ic_android_auto : R.drawable.ic_carplay);
        } catch (Throwable th) {
            return null;
        }
    }

    String labelFor(Kind kind) {
        if (kind == Kind.ANDROID_AUTO) {
            return "Android Auto";
        }
        return kind == Kind.CARPLAY ? "CarPlay" : "";
    }

    List<Intent> launchIntents(Kind kind) {
        ArrayList arrayList = new ArrayList();
        if (kind == null || kind == Kind.NONE) {
            return arrayList;
        }
        PackageManager packageManager = this.app.getPackageManager();
        ComponentName componentNameComponentFor = componentFor(kind);
        if (componentNameComponentFor != null && activityLaunchable(packageManager, componentNameComponentFor)) {
            arrayList.add(new Intent().setComponent(componentNameComponentFor));
        }
        for (String str : packagesFor(kind)) {
            try {
                Intent launchIntentForPackage = packageManager.getLaunchIntentForPackage(str);
                if (launchIntentForPackage != null && launchIntentForPackage.getComponent() != null && (componentNameComponentFor == null || !componentNameComponentFor.equals(launchIntentForPackage.getComponent()))) {
                    arrayList.add(launchIntentForPackage);
                }
            } catch (Throwable th) {
            }
        }
        return arrayList;
    }

    void requestShow(Kind kind) {
        if (kind != Kind.ANDROID_AUTO) {
            Kind kind2 = Kind.CARPLAY;
            return;
        }
        try {
            Intent intent = new Intent("ts.car.androidauto.view_state");
            intent.putExtra("state", 1);
            this.app.sendBroadcast(intent);
        } catch (Throwable th) {
        }
        sendAaTransact(23);
    }

    void start(Listener listener) {
        if (this.started) {
            return;
        }
        this.started = true;
        this.listener = listener;
        HandlerThread handlerThread = new HandlerThread("H6Projection");
        handlerThread.start();
        this.worker = new Handler(handlerThread.getLooper());
        this.worker.post(this.tick);
    }

    void stop() {
        this.started = false;
        if (this.worker != null) {
            this.worker.removeCallbacks(this.tick);
        }
        unbind(this.aaConnection);
        unbind(this.cpConnection);
        this.aaConnection = null;
        this.cpConnection = null;
        this.aaBinder = null;
        this.cpBinder = null;
    }
}
