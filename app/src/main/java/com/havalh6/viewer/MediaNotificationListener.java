package com.havalh6.viewer;

import android.app.Notification;
import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.view.KeyEvent;
import java.util.List;

public final class MediaNotificationListener extends NotificationListenerService {
    private static final String TAG = "H6Media";
    private static volatile MediaNotificationListener instance;
    private MediaController controller;
    private MediaController.Callback controllerCb;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaSessionManager manager;
    private ComponentName selfComponent;
    private MediaSessionManager.OnActiveSessionsChangedListener sessionsListener;

    private Bitmap bitmapFromNotification(Notification notification) {
        if (notification == null) {
            return null;
        }
        Bitmap bitmapLarger = null;
        Bundle bundle = notification.extras;
        if (bundle != null) {
            bitmapLarger = larger(larger(larger(null, extraBitmap(bundle, "android.picture")), extraBitmap(bundle, "android.largeIcon.big")), extraBitmap(bundle, "android.largeIcon"));
            if (Build.VERSION.SDK_INT >= 31) {
                bitmapLarger = larger(bitmapLarger, iconToBitmap(extraIcon(bundle, "android.pictureIcon")));
            }
        }
        return usableArt(larger(bitmapLarger, iconToBitmap(notification.getLargeIcon())));
    }

    private void dispatchMediaKey(int i) {
        Throwable th;
        try {
            AudioManager audioManager = (AudioManager) getSystemService("audio");
            if (audioManager == null) {
                return;
            }
            long jUptimeMillis = SystemClock.uptimeMillis();
            try {
                audioManager.dispatchMediaKeyEvent(new KeyEvent(jUptimeMillis, jUptimeMillis, 0, i, 0));
                audioManager.dispatchMediaKeyEvent(new KeyEvent(jUptimeMillis, jUptimeMillis, 1, i, 0));
                Log.i("H6Media", "dispatched media key " + i);
                return;
            } catch (Throwable th2) {
                th = th2;
            }
        } catch (Throwable th3) {
            th = th3;
        }
        Log.w("H6Media", "media key dispatch failed", th);
    }

    private static Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable == null) {
            return null;
        }
        if (drawable instanceof BitmapDrawable) {
            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
            if (bitmap == null || bitmap.isRecycled()) {
                return null;
            }
            return bitmap;
        }
        int intrinsicWidth = drawable.getIntrinsicWidth();
        int intrinsicHeight = drawable.getIntrinsicHeight();
        if (intrinsicWidth <= 0) {
            intrinsicWidth = 256;
        }
        if (intrinsicHeight <= 0) {
            intrinsicHeight = 256;
        }
        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmapCreateBitmap);
        drawable.setBounds(0, 0, intrinsicWidth, intrinsicHeight);
        drawable.draw(canvas);
        return bitmapCreateBitmap;
    }

    private void emitCurrent() {
        MediaNowPlaying.emitFromController(this, this.controller);
    }

    private static Bitmap extraBitmap(Bundle bundle, String str) {
        if (bundle == null || str == null) {
            return null;
        }
        try {
            Object parcelable = Build.VERSION.SDK_INT >= 33 ? bundle.getParcelable(str, Bitmap.class) : bundle.getParcelable(str);
            if (parcelable instanceof Bitmap) {
                return (Bitmap) parcelable;
            }
            return null;
        } catch (Throwable th) {
            return null;
        }
    }

    private static Icon extraIcon(Bundle bundle, String str) {
        if (bundle == null || str == null) {
            return null;
        }
        try {
            Object parcelable = Build.VERSION.SDK_INT >= 33 ? bundle.getParcelable(str, Icon.class) : bundle.getParcelable(str);
            if (parcelable instanceof Icon) {
                return (Icon) parcelable;
            }
            return null;
        } catch (Throwable th) {
            return null;
        }
    }

    static MediaNotificationListener getInstance() {
        return instance;
    }

    private Bitmap iconToBitmap(Icon icon) {
        if (icon == null) {
            return null;
        }
        try {
            return drawableToBitmap(icon.loadDrawable(this));
        } catch (Throwable th) {
            return null;
        }
    }

    static boolean isConnected() {
        return instance != null;
    }

    private static Bitmap larger(Bitmap bitmap, Bitmap bitmap2) {
        if (bitmap2 == null || bitmap2.isRecycled()) {
            return bitmap;
        }
        if (bitmap == null || bitmap.isRecycled()) {
            return bitmap2;
        }
        return bitmap2.getWidth() * bitmap2.getHeight() > bitmap.getWidth() * bitmap.getHeight() ? bitmap2 : bitmap;
    }

    private void pickController(List<MediaController> list) {
        PlaybackState playbackState;
        MediaController mediaController = null;
        MediaController mediaController2 = null;
        if (list != null) {
            for (MediaController mediaController3 : list) {
                if (mediaController3 != null && ((playbackState = mediaController3.getPlaybackState()) == null || playbackState.getState() != 7)) {
                    if (playbackState != null && playbackState.getState() == 3) {
                        mediaController = mediaController3;
                        break;
                    } else if (mediaController2 == null) {
                        mediaController2 = mediaController3;
                    }
                }
            }
            if (mediaController == null) {
                mediaController = mediaController2;
            }
            if (mediaController == null) {
                for (MediaController mediaController4 : list) {
                    if (mediaController4 != null) {
                        mediaController = mediaController4;
                        break;
                    }
                }
            }
        }
        if (this.controller == mediaController) {
            emitCurrent();
            return;
        }
        unbindController();
        this.controller = mediaController;
        if (this.controller != null) {
            this.controllerCb = new MediaController.Callback() {
                @Override // android.media.session.MediaController.Callback
                public void onMetadataChanged(MediaMetadata mediaMetadata) {
                    MediaNotificationListener.this.emitCurrent();
                }

                @Override // android.media.session.MediaController.Callback
                public void onPlaybackStateChanged(PlaybackState playbackState) {
                    MediaNotificationListener.this.emitCurrent();
                    MediaNowPlaying.syncPositionFromListener(MediaNotificationListener.this.controller);
                }
            };
            try {
                this.controller.registerCallback(this.controllerCb, this.handler);
            } catch (Exception e) {
                Log.w("H6Media", "registerCallback failed", e);
            }
        }
        emitCurrent();
        MediaNowPlaying.syncPositionFromListener(this.controller);
    }

    private void stopSessionWatch() {
        if (this.manager != null && this.sessionsListener != null) {
            try {
                this.manager.removeOnActiveSessionsChangedListener(this.sessionsListener);
            } catch (Exception e) {
            }
        }
        this.sessionsListener = null;
        unbindController();
        this.controller = null;
    }

    private boolean transport(long j, Runnable runnable) {
        if (this.controller == null) {
            return false;
        }
        try {
            PlaybackState playbackState = this.controller.getPlaybackState();
            if (playbackState != null && playbackState.getState() == 7) {
                return false;
            }
            if (playbackState != null && (playbackState.getActions() & j) == 0) {
                return false;
            }
            runnable.run();
            return true;
        } catch (Throwable th) {
            Log.w("H6Media", "transport failed", th);
            return false;
        }
    }

    private void unbindController() {
        if (this.controller != null && this.controllerCb != null) {
            try {
                this.controller.unregisterCallback(this.controllerCb);
            } catch (Exception e) {
            }
        }
        this.controllerCb = null;
    }

    private static Bitmap usableArt(Bitmap bitmap) {
        int width;
        int height;
        int iMax;
        if (bitmap == null || bitmap.isRecycled() || (iMax = Math.max((width = bitmap.getWidth()), (height = bitmap.getHeight()))) < 48) {
            return null;
        }
        if (width <= 120 && height <= 90) {
            return null;
        }
        if (iMax >= 96 || Math.abs(width - height) >= 8) {
            return bitmap;
        }
        return null;
    }

    Bitmap artworkFromNotification(String str) {
        Bitmap bitmapBitmapFromNotification;
        int width;
        if (str == null || str.isEmpty()) {
            return null;
        }
        try {
            StatusBarNotification[] activeNotifications = getActiveNotifications();
            if (activeNotifications == null) {
                return null;
            }
            Bitmap bitmap = null;
            int i = 0;
            for (StatusBarNotification statusBarNotification : activeNotifications) {
                if (statusBarNotification != null && str.equals(statusBarNotification.getPackageName()) && (bitmapBitmapFromNotification = bitmapFromNotification(statusBarNotification.getNotification())) != null && !bitmapBitmapFromNotification.isRecycled() && (width = bitmapBitmapFromNotification.getWidth() * bitmapBitmapFromNotification.getHeight()) > i) {
                    bitmap = bitmapBitmapFromNotification;
                    i = width;
                }
            }
            return bitmap;
        } catch (Throwable th) {
            Log.w("H6Media", "getActiveNotifications failed", th);
            return null;
        }
    }

    boolean isPlaying() {
        PlaybackState playbackState;
        return (this.controller == null || (playbackState = this.controller.getPlaybackState()) == null || playbackState.getState() != 3) ? false : true;
    }

    void next() {
        if (transport(32L, () -> {
            this.controller.getTransportControls().skipToNext();
        })) {
            return;
        }
        dispatchMediaKey(87);
    }

    @Override // android.service.notification.NotificationListenerService
    public void onListenerConnected() {
        super.onListenerConnected();
        instance = this;
        this.selfComponent = new ComponentName(this, (Class<?>) MediaNotificationListener.class);
        this.manager = (MediaSessionManager) getSystemService("media_session");
        Log.i("H6Media", "Notification listener connected");
        startSessionWatch();
        MediaNowPlaying.onListenerConnected();
    }

    @Override // android.service.notification.NotificationListenerService
    public void onListenerDisconnected() {
        stopSessionWatch();
        instance = null;
        Log.i("H6Media", "Notification listener disconnected");
        MediaNowPlaying.onListenerDisconnected();
        super.onListenerDisconnected();
    }

    @Override // android.service.notification.NotificationListenerService
    public void onNotificationPosted(StatusBarNotification statusBarNotification) {
        if (this.controller == null || statusBarNotification == null || !statusBarNotification.getPackageName().equals(this.controller.getPackageName()) || !MediaNowPlaying.needsArtwork()) {
            return;
        }
        emitCurrent();
    }

    void playPause() {
        PlaybackState playbackState = this.controller == null ? null : this.controller.getPlaybackState();
        if (playbackState != null && playbackState.getState() == 3) {
            if (transport(2L, () -> {
                this.controller.getTransportControls().pause();
            })) {
                return;
            }
            dispatchMediaKey(127);
        } else {
            if (transport(4L, () -> {
                this.controller.getTransportControls().play();
            })) {
                return;
            }
            dispatchMediaKey(126);
        }
    }

    void prev() {
        if (transport(16L, () -> {
            this.controller.getTransportControls().skipToPrevious();
        })) {
            return;
        }
        dispatchMediaKey(88);
    }

    void requestPush() {
        emitCurrent();
    }

    void startSessionWatch() {
        stopSessionWatch();
        if (this.manager == null) {
            return;
        }
        this.sessionsListener = list -> this.pickController(list);
        try {
            this.manager.addOnActiveSessionsChangedListener(this.sessionsListener, this.selfComponent);
            pickController(this.manager.getActiveSessions(this.selfComponent));
        } catch (SecurityException e) {
            Log.w("H6Media", "NLS still missing media session access", e);
            MediaNowPlaying.emitNeedsListener();
        } catch (Throwable th) {
            Log.w("H6Media", "NLS session watch failed", th);
        }
    }
}
