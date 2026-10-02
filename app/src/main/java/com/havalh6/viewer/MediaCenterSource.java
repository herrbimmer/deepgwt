package com.havalh6.viewer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;
import java.io.InputStream;
import java.util.Locale;

final class MediaCenterSource {
    private static final int BINDER_PLAY_SERVICE = 2;
    private static final String PACKAGE = "com.beantechs.mediacenter";
    private static final String PLAY_DESCRIPTOR = "com.beantechs.mediacenter.mediacentermodel.IPlayService";
    private static final long POLL_MS = 1500;
    private static final String SERVICE_CLASS = "com.beantechs.mediacenter.mediacentermodel.MediaCenterService";
    private static final String SERVICE_DESCRIPTOR = "com.beantechs.mediacenter.mediacentermodel.IMediaCenterService";
    static final int SOURCE_ANDROID_AUTO = 402;
    static final int SOURCE_USB = 2;
    private static final int STATE_PLAYING = 3;
    private static final String TAG = "H6Media";
    private static final int TX_GET_CURRENT_AUDIO_SOURCE = 26;
    private static final int TX_GET_CURRENT_SOURCE = 25;
    private static final int TX_GET_MEDIA_INFO_BY_SOURCE = 22;
    private static final int TX_GET_PLAY_STATE_BY_SOURCE = 19;
    private static final int TX_PAUSE_BY_SOURCE = 27;
    private static final int TX_QUERY_BINDER = 1;
    private static final int TX_RESUME_BY_SOURCE = 28;
    private volatile int activeSource;
    private Context appContext;
    private Bitmap cachedArt;
    private ServiceConnection connection;
    private Listener listener;
    private volatile IBinder playBinder;
    private Handler poll;
    private volatile IBinder serviceBinder;
    private boolean started;
    private String cachedArtKey = "";
    private String lastLogged = "";
    private final Runnable tick = new Runnable() {
        @Override // java.lang.Runnable
        public void run() {
            try {
                MediaCenterSource.this.pollOnce();
            } catch (Throwable th) {
                Log.w("H6Media", "MediaCenter poll failed", th);
            }
            if (MediaCenterSource.this.poll != null) {
                MediaCenterSource.this.poll.postDelayed(this, 1500L);
            }
        }
    };

    interface Listener {
        void onMediaCenterTrack(MediaTrack mediaTrack);
    }

    private static final class MediaInfo {
        String album;
        String artist;
        long durationMs;
        Bitmap imageBitmap;
        String imageUrl;
        String title;

        private MediaInfo() {
        }

        static MediaInfo read(Parcel parcel) {
            MediaInfo mediaInfo = new MediaInfo();
            parcel.readString();
            parcel.readString();
            parcel.readString();
            parcel.readString();
            parcel.readString();
            parcel.readString();
            mediaInfo.album = parcel.readString();
            parcel.readString();
            mediaInfo.title = parcel.readString();
            parcel.readString();
            mediaInfo.artist = parcel.readString();
            mediaInfo.imageUrl = parcel.readString();
            parcel.readString();
            parcel.readString();
            mediaInfo.durationMs = parcel.readLong();
            parcel.readInt();
            parcel.readInt();
            parcel.readByte();
            try {
                mediaInfo.imageBitmap = (Bitmap) parcel.readParcelable(Bitmap.class.getClassLoader());
            } catch (Throwable th) {
                mediaInfo.imageBitmap = null;
            }
            return mediaInfo;
        }
    }

    private static final class PlayState {
        long durationMs;
        long elapsedMs;
        int mediaSource;
        int state;

        private PlayState() {
        }
    }

    MediaCenterSource() {
    }

    private void bind() {
        if (this.connection != null || this.appContext == null) {
            return;
        }
        this.connection = new ServiceConnection() {
            @Override // android.content.ServiceConnection
            public void onServiceConnected(ComponentName componentName, IBinder serviceBinder) {
                MediaCenterSource.this.serviceBinder = serviceBinder;
                MediaCenterSource.this.playBinder = null;
                Log.w("H6Media", "MediaCenter service connected");
            }

            @Override // android.content.ServiceConnection
            public void onServiceDisconnected(ComponentName componentName) {
                MediaCenterSource.this.serviceBinder = null;
                MediaCenterSource.this.playBinder = null;
                Log.w("H6Media", "MediaCenter service disconnected");
            }
        };
        boolean zBindService = false;
        try {
            zBindService = this.appContext.bindService(new Intent().setComponent(new ComponentName("com.beantechs.mediacenter", "com.beantechs.mediacenter.mediacentermodel.MediaCenterService")), this.connection, 1);
        } catch (Throwable th) {
            Log.w("H6Media", "MediaCenter bind failed", th);
        }
        if (zBindService) {
            return;
        }
        try {
            this.appContext.unbindService(this.connection);
        } catch (Throwable th2) {
        }
        this.connection = null;
        Log.w("H6Media", "MediaCenter bind returned false");
    }

    private Bitmap decodeUri(String str) {
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
            Log.w("H6Media", "MediaCenter art decode failed " + str, th3);
            return null;
        }
    }

    private static boolean isHttp(String str) {
        if (str == null) {
            return false;
        }
        String lowerCase = str.toLowerCase(Locale.US);
        return lowerCase.startsWith("http://") || lowerCase.startsWith("https://");
    }

    private static boolean isSource(Integer num, int i) {
        return num != null && num.intValue() == i;
    }

    private static String nz(String str) {
        return str == null ? "" : str;
    }

    private static int pickSource(Integer num, Integer num2) {
        if (isSource(num, 402) || isSource(num2, 402)) {
            return 402;
        }
        return (isSource(num, 2) || isSource(num2, 2)) ? 2 : 0;
    }

    private void pollOnce() {
        IBinder iBinderResolvePlayBinder = resolvePlayBinder();
        if (iBinderResolvePlayBinder == null) {
            publish(null);
            return;
        }
        int iPickSource = pickSource(readInt(iBinderResolvePlayBinder, 25), readInt(iBinderResolvePlayBinder, 26));
        this.activeSource = iPickSource;
        if (iPickSource == 0) {
            publish(null);
            return;
        }
        MediaInfo mediaInfo = readMediaInfo(iBinderResolvePlayBinder, iPickSource);
        PlayState playState = readPlayState(iBinderResolvePlayBinder, iPickSource);
        if (mediaInfo == null && playState == null) {
            publish(null);
        } else {
            publish(toTrack(iPickSource, mediaInfo, playState));
        }
    }

    private void publish(MediaTrack mediaTrack) {
        String str;
        if (mediaTrack == null) {
            str = "none";
        } else {
            str = mediaTrack.appLabel + "|" + mediaTrack.title + "|" + mediaTrack.artist + "|" + mediaTrack.playing + "|" + (mediaTrack.art == null ? "noart" : mediaTrack.art.getWidth() + "x" + mediaTrack.art.getHeight()) + "|" + mediaTrack.remoteArtUrl;
        }
        if (!str.equals(this.lastLogged)) {
            this.lastLogged = str;
            Log.w("H6Media", "MediaCenter " + str);
        }
        Listener listener = this.listener;
        if (listener != null) {
            listener.onMediaCenterTrack(mediaTrack);
        }
    }

    private Integer readInt(IBinder iBinder, int i) {
        Parcel parcelObtain = Parcel.obtain();
        Parcel parcelObtain2 = Parcel.obtain();
        try {
            parcelObtain.writeInterfaceToken("com.beantechs.mediacenter.mediacentermodel.IPlayService");
            if (!iBinder.transact(i, parcelObtain, parcelObtain2, 0)) {
                parcelObtain2.recycle();
                parcelObtain.recycle();
                return null;
            }
            parcelObtain2.readException();
            Integer numValueOf = Integer.valueOf(parcelObtain2.readInt());
            parcelObtain2.recycle();
            parcelObtain.recycle();
            return numValueOf;
        } catch (Throwable th) {
            try {
                this.playBinder = null;
                return null;
            } finally {
                parcelObtain2.recycle();
                parcelObtain.recycle();
            }
        }
    }

    private MediaInfo readMediaInfo(IBinder iBinder, int i) {
        Parcel parcelObtain = Parcel.obtain();
        Parcel parcelObtain2 = Parcel.obtain();
        try {
            parcelObtain.writeInterfaceToken("com.beantechs.mediacenter.mediacentermodel.IPlayService");
            parcelObtain.writeInt(i);
            if (!iBinder.transact(22, parcelObtain, parcelObtain2, 0)) {
                parcelObtain2.recycle();
                parcelObtain.recycle();
                return null;
            }
            parcelObtain2.readException();
            if (parcelObtain2.readInt() == 0) {
                parcelObtain2.recycle();
                parcelObtain.recycle();
                return null;
            }
            int i2 = parcelObtain2.readInt();
            parcelObtain2.readByte();
            if (parcelObtain2.readString() != null) {
                parcelObtain2.createByteArray();
            }
            if (parcelObtain2.readString() == null) {
                parcelObtain2.recycle();
                parcelObtain.recycle();
                return null;
            }
            if (i2 != 0 && i2 != i) {
                parcelObtain2.recycle();
                parcelObtain.recycle();
                return null;
            }
            MediaInfo mediaInfo = MediaInfo.read(parcelObtain2);
            parcelObtain2.recycle();
            parcelObtain.recycle();
            return mediaInfo;
        } catch (Throwable th) {
            try {
                this.playBinder = null;
                Log.w("H6Media", "MediaCenter media info read failed source=" + i, th);
                return null;
            } finally {
                parcelObtain2.recycle();
                parcelObtain.recycle();
            }
        }
    }

    private PlayState readPlayState(IBinder iBinder, int i) {
        Parcel parcelObtain = Parcel.obtain();
        Parcel parcelObtain2 = Parcel.obtain();
        try {
            parcelObtain.writeInterfaceToken("com.beantechs.mediacenter.mediacentermodel.IPlayService");
            parcelObtain.writeInt(i);
            if (!iBinder.transact(19, parcelObtain, parcelObtain2, 0)) {
                parcelObtain2.recycle();
                parcelObtain.recycle();
                return null;
            }
            parcelObtain2.readException();
            if (parcelObtain2.readInt() == 0) {
                parcelObtain2.recycle();
                parcelObtain.recycle();
                return null;
            }
            PlayState playState = new PlayState();
            playState.mediaSource = parcelObtain2.readInt();
            playState.state = parcelObtain2.readInt();
            playState.durationMs = Math.max(0L, parcelObtain2.readLong());
            parcelObtain2.readLong();
            parcelObtain2.readFloat();
            parcelObtain2.readLong();
            parcelObtain2.readString();
            playState.elapsedMs = Math.max(0L, parcelObtain2.readLong());
            if (playState.mediaSource == 0 || playState.mediaSource == i) {
                parcelObtain2.recycle();
                parcelObtain.recycle();
                return playState;
            }
            parcelObtain2.recycle();
            parcelObtain.recycle();
            return null;
        } catch (Throwable th) {
            try {
                this.playBinder = null;
                Log.w("H6Media", "MediaCenter play state read failed source=" + i, th);
                return null;
            } finally {
                parcelObtain2.recycle();
                parcelObtain.recycle();
            }
        }
    }

    private Bitmap resolveArt(String cachedArtKey, MediaInfo mediaInfo) {
        if (cachedArtKey.equals(this.cachedArtKey) && this.cachedArt != null && !this.cachedArt.isRecycled()) {
            return this.cachedArt;
        }
        Bitmap bitmap = mediaInfo == null ? null : mediaInfo.imageBitmap;
        if (bitmap != null && bitmap.isRecycled()) {
            bitmap = null;
        }
        Bitmap bitmapDecodeUri = mediaInfo != null ? decodeUri(mediaInfo.imageUrl) : null;
        if (bitmapDecodeUri != null && !bitmapDecodeUri.isRecycled() && (bitmap == null || bitmapDecodeUri.getWidth() * bitmapDecodeUri.getHeight() > bitmap.getWidth() * bitmap.getHeight())) {
            bitmap = bitmapDecodeUri;
        }
        this.cachedArtKey = cachedArtKey;
        this.cachedArt = bitmap;
        return bitmap;
    }

    private IBinder resolvePlayBinder() {
        IBinder iBinder = this.playBinder;
        if (iBinder != null && iBinder.isBinderAlive()) {
            return iBinder;
        }
        this.playBinder = null;
        IBinder iBinder2 = this.serviceBinder;
        if (iBinder2 == null || !iBinder2.isBinderAlive()) {
            this.serviceBinder = null;
            unbind();
            bind();
            return null;
        }
        Parcel parcelObtain = Parcel.obtain();
        Parcel parcelObtain2 = Parcel.obtain();
        try {
            parcelObtain.writeInterfaceToken("com.beantechs.mediacenter.mediacentermodel.IMediaCenterService");
            parcelObtain.writeInt(2);
            if (!iBinder2.transact(1, parcelObtain, parcelObtain2, 0)) {
                parcelObtain2.recycle();
                parcelObtain.recycle();
                return null;
            }
            parcelObtain2.readException();
            IBinder strongBinder = parcelObtain2.readStrongBinder();
            if (strongBinder != null) {
                this.playBinder = strongBinder;
                Log.w("H6Media", "MediaCenter play binder resolved");
            }
            parcelObtain2.recycle();
            parcelObtain.recycle();
            return strongBinder;
        } catch (Throwable th) {
            try {
                Log.w("H6Media", "MediaCenter play binder query failed", th);
                return null;
            } finally {
                parcelObtain2.recycle();
                parcelObtain.recycle();
            }
        }
    }

    private MediaTrack toTrack(int i, MediaInfo mediaInfo, PlayState playState) {
        MediaTrack mediaTrack = new MediaTrack(2);
        mediaTrack.packageName = i == 402 ? ProjectionPresence.androidAutoAppPackage() : "com.beantechs.mediacenter";
        mediaTrack.appLabel = i == 402 ? "ANDROID AUTO" : "USB";
        if (mediaInfo != null) {
            mediaTrack.title = nz(mediaInfo.title);
            mediaTrack.artist = nz(mediaInfo.artist);
            mediaTrack.album = nz(mediaInfo.album);
        }
        long j = 0;
        if (playState != null && playState.durationMs > 0) {
            j = playState.durationMs;
        } else if (mediaInfo != null && mediaInfo.durationMs > 0) {
            j = mediaInfo.durationMs;
        }
        mediaTrack.durationMs = j;
        if (playState != null) {
            mediaTrack.playing = playState.state == 3;
            mediaTrack.positionMs = Math.max(0L, playState.elapsedMs);
        }
        mediaTrack.remoteArtUrl = (mediaInfo == null || !isHttp(mediaInfo.imageUrl)) ? "" : mediaInfo.imageUrl;
        String str = i + "|" + mediaTrack.title + "|" + mediaTrack.artist + "|" + mediaTrack.album + "|" + (mediaInfo != null ? nz(mediaInfo.imageUrl) : "") + "|" + ((mediaInfo == null || mediaInfo.imageBitmap == null) ? "0" : mediaInfo.imageBitmap.getWidth() + "x" + mediaInfo.imageBitmap.getHeight());
        mediaTrack.artKey = str;
        mediaTrack.art = resolveArt(str, mediaInfo);
        if (mediaTrack.hasTrack()) {
            return mediaTrack;
        }
        return null;
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
        this.playBinder = null;
    }

    boolean setPlaying(boolean z) {
        IBinder iBinderResolvePlayBinder = resolvePlayBinder();
        int i = this.activeSource;
        if (iBinderResolvePlayBinder == null || i == 0) {
            return false;
        }
        Parcel parcelObtain = Parcel.obtain();
        try {
            parcelObtain.writeInterfaceToken("com.beantechs.mediacenter.mediacentermodel.IPlayService");
            parcelObtain.writeInt(i);
            boolean zTransact = iBinderResolvePlayBinder.transact(z ? 28 : 27, parcelObtain, null, 1);
            Log.w("H6Media", "MediaCenter " + (z ? "resume" : "pause") + " source=" + i + " sent=" + zTransact);
            parcelObtain.recycle();
            return zTransact;
        } catch (Throwable th) {
            try {
                this.playBinder = null;
                Log.w("H6Media", "MediaCenter transport failed", th);
                return false;
            } finally {
                parcelObtain.recycle();
            }
        }
    }

    void start(Context context, Listener listener) {
        if (this.started) {
            return;
        }
        this.started = true;
        this.appContext = context.getApplicationContext();
        this.listener = listener;
        HandlerThread handlerThread = new HandlerThread("H6MediaCenter");
        handlerThread.start();
        this.poll = new Handler(handlerThread.getLooper());
        bind();
        this.poll.post(this.tick);
    }

    void stop() {
        this.started = false;
        if (this.poll != null) {
            this.poll.removeCallbacks(this.tick);
        }
        unbind();
    }
}
