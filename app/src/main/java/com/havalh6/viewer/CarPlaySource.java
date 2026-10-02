package com.havalh6.viewer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.SystemClock;
import android.util.Log;
import java.io.InputStream;

final class CarPlaySource {
    private static final long BLANK_GRACE_MS = 4000;
    private static final String CALLBACK_DESCRIPTOR = "com.ts.carplay.common.aidl.INowPlayingUpdateCallback";
    private static final int HID_DOWN = 0;
    private static final int HID_NEXT = 4;
    private static final int HID_PAUSE = 2;
    private static final int HID_PLAY = 1;
    private static final long HID_PRESS_MS = 90;
    private static final int HID_PREVIOUS = 8;
    private static final int HID_UP = 1;
    private static final String PACKAGE = "com.ts.carplay";
    private static final int PLAYBACK_PLAYING = 1;
    private static final String SERVICE_ACTION = "com.ts.carplay.action.CarPlayService";
    private static final String SERVICE_CLASS = "com.ts.carplay.CarPlayService";
    private static final String SERVICE_DESCRIPTOR = "com.ts.carplay.common.aidl.ICarPlayService";
    private static final String TAG = "H6Media";
    private static final int TX_NOW_PLAYING_UPDATE = 1;
    private static final int TX_SEND_HID_OVER_IAP = 25;
    private static final int TX_START_NOW_PLAYING = 9;
    private static final int TX_STOP_NOW_PLAYING = 10;
    private static final long WATCHDOG_MS = 5000;
    private Context appContext;
    private volatile boolean callbackRegistered;
    private ServiceConnection connection;
    private Bitmap lastArt;
    private volatile long lastValidAtMs;
    private Listener listener;
    private volatile IBinder serviceBinder;
    private boolean started;
    private Handler worker;
    private String lastLogged = "";
    private String lastArtKey = "";
    private final NowPlayingCallback callback = new NowPlayingCallback();
    private final Runnable watchdog = new Runnable() {
        @Override // java.lang.Runnable
        public void run() {
            try {
                CarPlaySource.this.bind();
                if (CarPlaySource.this.serviceBinder != null && CarPlaySource.this.serviceBinder.isBinderAlive() && !CarPlaySource.this.callbackRegistered) {
                    CarPlaySource.this.registerCallback();
                }
            } catch (Throwable th) {
                Log.w("H6Media", "CarPlay watchdog failed", th);
            }
            if (CarPlaySource.this.worker != null) {
                CarPlaySource.this.worker.postDelayed(this, 5000L);
            }
        }
    };

    interface Listener {
        void onCarPlayTrack(MediaTrack mediaTrack);
    }

    private final class NowPlayingCallback extends Binder {

        NowPlayingCallback() {
            attachInterface(() -> this, "com.ts.carplay.common.aidl.INowPlayingUpdateCallback");
        }

        @Override // android.os.Binder
        protected boolean onTransact(int i, Parcel parcel, Parcel parcel2, int i2) {
            if (i == 1598968902) {
                if (parcel2 != null) {
                    parcel2.writeString("com.ts.carplay.common.aidl.INowPlayingUpdateCallback");
                }
                return true;
            }
            if (i != 1) {
                try {
                    return super.onTransact(i, parcel, parcel2, i2);
                } catch (Throwable th) {
                    return true;
                }
            }
            CarPlaySource.this.callbackRegistered = true;
            try {
                parcel.enforceInterface("com.ts.carplay.common.aidl.INowPlayingUpdateCallback");
                int i3 = parcel.readInt();
                CarPlaySource carPlaySource = CarPlaySource.this;
                if (i3 == 0) {
                    carPlaySource.scheduleBlankClear("empty now playing update");
                    return true;
                }
                carPlaySource.handleUpdate(CarPlaySource.readNowPlaying(parcel));
                return true;
            } catch (Throwable th2) {
                Log.w("H6Media", "CarPlay now-playing decode failed", th2);
            }
            return true;
        }
    }

    private static final class RawNowPlaying {
        String artist;
        byte[] artworkData;
        String artworkPath;
        long durationMs;
        long elapsedMs;
        int playbackStatus;
        String title;

        private RawNowPlaying() {
        }
    }

    CarPlaySource() {
    }

    private void bind() {
        if (this.connection == null && this.appContext != null) {
            this.connection = new ServiceConnection() {
                @Override // android.content.ServiceConnection
                public void onServiceConnected(ComponentName componentName, IBinder serviceBinder) {
                    CarPlaySource.this.serviceBinder = serviceBinder;
                    CarPlaySource.this.callbackRegistered = false;
                    Log.w("H6Media", "CarPlay service connected");
                    CarPlaySource.this.registerCallback();
                }

                @Override // android.content.ServiceConnection
                public void onServiceDisconnected(ComponentName componentName) {
                    CarPlaySource.this.serviceBinder = null;
                    CarPlaySource.this.callbackRegistered = false;
                    Log.w("H6Media", "CarPlay service disconnected");
                    CarPlaySource.this.publish(null, "service disconnected");
                }
            };
            boolean zBindService = false;
            try {
                zBindService = this.appContext.bindService(new Intent().setComponent(new ComponentName("com.ts.carplay", "com.ts.carplay.CarPlayService")), this.connection, 1);
                if (!zBindService) {
                    zBindService = this.appContext.bindService(new Intent("com.ts.carplay.action.CarPlayService").setPackage("com.ts.carplay"), this.connection, 1);
                }
            } catch (Throwable th) {
                Log.w("H6Media", "CarPlay bind failed", th);
            }
            if (zBindService) {
                return;
            }
            try {
                this.appContext.unbindService(this.connection);
            } catch (Throwable th2) {
            }
            this.connection = null;
        }
    }

    private Bitmap decodeBytes(byte[] bArr) {
        if (bArr == null || bArr.length == 0) {
            return null;
        }
        try {
            return BitmapFactory.decodeByteArray(bArr, 0, bArr.length);
        } catch (Throwable th) {
            Log.w("H6Media", "CarPlay artwork decode failed", th);
            return null;
        }
    }

    private Bitmap decodePath(String str) {
        Bitmap bitmapDecodeStream;
        if (this.appContext == null || str == null || str.isEmpty()) {
            return null;
        }
        try {
            Uri uri = Uri.parse(str);
            String scheme = uri.getScheme();
            if (scheme != null && !scheme.isEmpty()) {
                if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                    if ("file".equalsIgnoreCase(scheme)) {
                        String path = uri.getPath();
                        if (path == null) {
                            return null;
                        }
                        return BitmapFactory.decodeFile(path);
                    }
                    InputStream inputStreamOpenInputStream = this.appContext.getContentResolver().openInputStream(uri);
                    if (inputStreamOpenInputStream == null) {
                        bitmapDecodeStream = null;
                    } else {
                        try {
                            bitmapDecodeStream = BitmapFactory.decodeStream(inputStreamOpenInputStream);
                        } catch (Throwable th) {
                            if (inputStreamOpenInputStream != null) {
                                try {
                                    inputStreamOpenInputStream.close();
                                } catch (Throwable th2) {
                                    th.addSuppressed(th2);
                                }
                            }
                            throw th;
                        }
                    }
                    if (inputStreamOpenInputStream != null) {
                        inputStreamOpenInputStream.close();
                    }
                    return bitmapDecodeStream;
                }
                return null;
            }
            return BitmapFactory.decodeFile(str);
        } catch (Throwable th3) {
            Log.w("H6Media", "CarPlay artwork path decode failed " + str, th3);
            return null;
        }
    }

    private void handleUpdate(RawNowPlaying rawNowPlaying) {
        boolean z = true;
        boolean z2 = rawNowPlaying.playbackStatus == 1;
        if (isBlank(rawNowPlaying.title) && isBlank(rawNowPlaying.artist) && isBlank(rawNowPlaying.artworkPath) && ((rawNowPlaying.artworkData == null || rawNowPlaying.artworkData.length <= 0) && !z2)) {
            z = false;
        }
        if (!z) {
            scheduleBlankClear("blank now playing update");
            return;
        }
        MediaTrack mediaTrack = new MediaTrack(3);
        mediaTrack.packageName = "com.ts.carplay";
        mediaTrack.appLabel = "CARPLAY";
        mediaTrack.title = nz(rawNowPlaying.title);
        mediaTrack.artist = nz(rawNowPlaying.artist);
        mediaTrack.durationMs = Math.max(0L, rawNowPlaying.durationMs);
        mediaTrack.positionMs = Math.max(0L, rawNowPlaying.elapsedMs);
        mediaTrack.positionAtMs = SystemClock.elapsedRealtime();
        mediaTrack.playing = z2;
        mediaTrack.artKey = "carplay|" + mediaTrack.title + "|" + mediaTrack.artist + "|" + (rawNowPlaying.artworkData != null ? rawNowPlaying.artworkData.length : 0) + "|" + nz(rawNowPlaying.artworkPath);
        if (!mediaTrack.artKey.equals(this.lastArtKey) || this.lastArt == null || this.lastArt.isRecycled()) {
            Bitmap bitmapDecodeBytes = decodeBytes(rawNowPlaying.artworkData);
            if (bitmapDecodeBytes == null) {
                bitmapDecodeBytes = decodePath(rawNowPlaying.artworkPath);
            }
            this.lastArtKey = mediaTrack.artKey;
            this.lastArt = bitmapDecodeBytes;
            mediaTrack.art = bitmapDecodeBytes;
        } else {
            mediaTrack.art = this.lastArt;
        }
        this.lastValidAtMs = SystemClock.elapsedRealtime();
        publish(mediaTrack, null);
    }

    private static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    private static String nz(String str) {
        return str == null ? "" : str;
    }

    private void publish(MediaTrack mediaTrack, String str) {
        String str2;
        if (mediaTrack == null) {
            this.lastValidAtMs = 0L;
            this.lastArtKey = "";
            this.lastArt = null;
        }
        if (mediaTrack == null) {
            str2 = "cleared:" + nz(str);
        } else {
            str2 = mediaTrack.title + "|" + mediaTrack.artist + "|" + mediaTrack.playing + "|" + (mediaTrack.art == null ? "noart" : mediaTrack.art.getWidth() + "x" + mediaTrack.art.getHeight());
        }
        if (!str2.equals(this.lastLogged)) {
            this.lastLogged = str2;
            Log.w("H6Media", "CarPlay " + str2);
        }
        Listener listener = this.listener;
        if (listener != null) {
            listener.onCarPlayTrack(mediaTrack);
        }
    }

    private static RawNowPlaying readNowPlaying(Parcel parcel) {
        RawNowPlaying rawNowPlaying = new RawNowPlaying();
        rawNowPlaying.title = parcel.readString();
        rawNowPlaying.artist = parcel.readString();
        rawNowPlaying.durationMs = parcel.readLong();
        rawNowPlaying.artworkPath = parcel.readString();
        rawNowPlaying.playbackStatus = parcel.readInt();
        rawNowPlaying.elapsedMs = parcel.readLong();
        parcel.readInt();
        parcel.readInt();
        int i = parcel.readInt();
        if (i > 0) {
            rawNowPlaying.artworkData = new byte[i];
            parcel.readByteArray(rawNowPlaying.artworkData);
        }
        return rawNowPlaying;
    }

    private void registerCallback() {
        IBinder iBinder = this.serviceBinder;
        if (iBinder == null) {
            return;
        }
        Parcel parcelObtain = Parcel.obtain();
        try {
            parcelObtain.writeInterfaceToken("com.ts.carplay.common.aidl.ICarPlayService");
            parcelObtain.writeStrongBinder(this.callback);
            this.callbackRegistered = iBinder.transact(9, parcelObtain, null, 1);
            Log.w("H6Media", "CarPlay now-playing callback registered=" + this.callbackRegistered);
        } catch (Throwable th) {
            try {
                this.callbackRegistered = false;
                Log.w("H6Media", "CarPlay callback registration failed", th);
            } finally {
                parcelObtain.recycle();
            }
        }
    }

    private void scheduleBlankClear(String str) {
        long j = this.lastValidAtMs;
        if (j <= 0 || SystemClock.elapsedRealtime() - j >= 4000) {
            publish(null, str);
        }
    }

    private boolean sendHid(int i, String str) {
        if (!sendHidEvent(i, 0, str)) {
            return false;
        }
        if (this.worker == null) {
            return true;
        }
        this.worker.postDelayed(() -> {
            sendHidEvent(i, 1, str + " release");
        }, 90L);
        return true;
    }

    private boolean sendHidEvent(int i, int i2, String str) {
        IBinder iBinder = this.serviceBinder;
        if (iBinder == null) {
            return false;
        }
        Parcel parcelObtain = Parcel.obtain();
        try {
            parcelObtain.writeInterfaceToken("com.ts.carplay.common.aidl.ICarPlayService");
            parcelObtain.writeInt(i);
            parcelObtain.writeInt(i2);
            boolean zTransact = iBinder.transact(25, parcelObtain, null, 1);
            Log.w("H6Media", "CarPlay HID " + str + " sent=" + zTransact);
            parcelObtain.recycle();
            return zTransact;
        } catch (Throwable th) {
            try {
                Log.w("H6Media", "CarPlay HID failed " + str, th);
                return false;
            } finally {
                parcelObtain.recycle();
            }
        }
    }

    private void unbind() {
        if (this.connection != null && this.appContext != null) {
            try {
                this.appContext.unbindService(this.connection);
            } catch (Throwable th) {
            }
        }
        this.connection = null;
        this.serviceBinder = null;
        this.callbackRegistered = false;
    }

    private void unregisterCallback() {
        IBinder iBinder = this.serviceBinder;
        if (iBinder == null) {
            return;
        }
        Parcel parcelObtain = Parcel.obtain();
        try {
            parcelObtain.writeInterfaceToken("com.ts.carplay.common.aidl.ICarPlayService");
            parcelObtain.writeStrongBinder(this.callback);
            iBinder.transact(10, parcelObtain, null, 1);
        } catch (Throwable th) {
        }
        this.callbackRegistered = false;
        parcelObtain.recycle();
    }

    boolean next() {
        return sendHid(4, "next");
    }

    boolean previous() {
        return sendHid(8, "previous");
    }

    boolean setPlaying(boolean z) {
        return sendHid(z ? 1 : 2, z ? "play" : "pause");
    }

    void start(Context context, Listener listener) {
        if (this.started) {
            return;
        }
        this.started = true;
        this.appContext = context.getApplicationContext();
        this.listener = listener;
        HandlerThread handlerThread = new HandlerThread("H6CarPlay");
        handlerThread.start();
        this.worker = new Handler(handlerThread.getLooper());
        this.worker.post(this.watchdog);
    }

    void stop() {
        this.started = false;
        if (this.worker != null) {
            this.worker.removeCallbacks(this.watchdog);
        }
        unregisterCallback();
        unbind();
    }
}
