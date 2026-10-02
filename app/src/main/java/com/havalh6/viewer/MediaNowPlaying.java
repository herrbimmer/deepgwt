package com.havalh6.viewer;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

final class MediaNowPlaying {
    private static final int APP_ICON_PX = 96;
    private static final int ART_JPEG_QUALITY = 88;
    private static final int ART_MAX_PX = 720;
    private static volatile MediaNowPlaying INSTANCE = null;
    private static final long POSITION_INTERVAL_MS = 250;
    private static final long SEARCH_RETRY_MS = 20000;
    private static final int SEARCH_SCAN_LIMIT = 2097152;
    private static final String TAG = "H6Media";
    private Runnable accessRetry;
    private Context appContext;
    private Handler artHandler;
    private Callback callback;
    private MediaTrack carPlayTrack;
    private long failedSearchAtMs;
    private int lastWinnerSource;
    private ComponentName listenerComponent;
    private MediaTrack mediaCenterTrack;
    private Runnable positionTick;
    private MediaController sessionController;
    private MediaTrack sessionTrack;
    private boolean started;
    private static final Pattern YT_ID = Pattern.compile("(?:(?:youtube(?:-nocookie)?\\.com/(?:watch\\?(?:.*&)?v=|embed/|shorts/|live/|v/)|youtu\\.be/|ytimg\\.com/vi(?:_webp)?/))([A-Za-z0-9_-]{11})");
    private static final Pattern YT_ID_BARE = Pattern.compile("^[A-Za-z0-9_-]{11}$");
    private static final Pattern SEARCH_VIDEO_ID = Pattern.compile("\"videoId\"\\s*:\\s*\"([A-Za-z0-9_-]{11})\"");
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final MediaCenterSource mediaCenter = new MediaCenterSource();
    private final CarPlaySource carPlay = new CarPlaySource();
    private final Map<String, String> appIconCache = new HashMap();
    private String lastArtKey = "";
    private String lastArtData = "";
    private String inflightArtUri = "";
    private String lastPayloadSignature = "";
    private String lastArtMissLog = "";
    private String inflightSearch = "";
    private String failedSearch = "";
    private final HashMap<String, String> searchedArtUrl = new HashMap<>();
    private final LinkedHashMap<String, String> artCache = new LinkedHashMap<>();

    interface Callback {
        void onPosition(long j);

        void onUpdate(JSONObject jSONObject);
    }

    MediaNowPlaying() {
    }

    private String appIconDataUrl(String str) {
        if (this.appContext == null || str == null || str.isEmpty()) {
            return "";
        }
        String str2 = this.appIconCache.get(str);
        if (str2 != null) {
            return str2;
        }
        String str3 = "";
        try {
            Drawable applicationIcon = this.appContext.getPackageManager().getApplicationIcon(str);
            if (applicationIcon != null) {
                Bitmap bitmapCreateBitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmapCreateBitmap);
                applicationIcon.setBounds(0, 0, 96, 96);
                applicationIcon.draw(canvas);
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                bitmapCreateBitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
                bitmapCreateBitmap.recycle();
                str3 = "data:image/png;base64," + Base64.encodeToString(byteArrayOutputStream.toByteArray(), 2);
            }
        } catch (Exception e) {
        }
        if (this.appIconCache.size() >= 8) {
            this.appIconCache.clear();
        }
        this.appIconCache.put(str, str3);
        return str3;
    }

    private static String appLabel(Context context, String str) {
        if (context == null || str.isEmpty()) {
            return str;
        }
        try {
            CharSequence applicationLabel = context.getPackageManager().getApplicationLabel(context.getPackageManager().getApplicationInfo(str, 0));
            if (applicationLabel != null) {
                return applicationLabel.toString();
            }
        } catch (Exception e) {
        }
        return str;
    }

    private String artDataUrl(MediaTrack mediaTrack) {
        String str = mediaTrack.source + "|" + mediaTrack.artKey;
        String str2 = this.artCache.get(str);
        if (str2 != null) {
            this.lastArtKey = str;
            this.lastArtData = str2;
            return str2;
        }
        if (mediaTrack.art != null && !mediaTrack.art.isRecycled()) {
            this.lastArtKey = str;
            this.lastArtData = encodeArt(mediaTrack.art);
            cacheArt(str, this.lastArtData);
            Log.w("H6Media", "art " + mediaTrack.art.getWidth() + "x" + mediaTrack.art.getHeight() + " src=" + mediaTrack.appLabel);
            return this.lastArtData;
        }
        if (!mediaTrack.remoteArtUrl.isEmpty()) {
            this.lastArtKey = str;
            this.lastArtData = mediaTrack.remoteArtUrl;
            scheduleArtworkDownload(mediaTrack.remoteArtUrl, str);
            return this.lastArtData;
        }
        if (!mediaTrack.artSearchQuery.isEmpty()) {
            scheduleYouTubeLookup(mediaTrack.artSearchQuery, str);
        }
        this.lastArtKey = "";
        this.lastArtData = "";
        return "";
    }

    private static Bitmap artworkBitmap(MediaMetadata mediaMetadata) {
        if (mediaMetadata == null) {
            return null;
        }
        Bitmap bitmapLarger = null;
        try {
            MediaDescription description = mediaMetadata.getDescription();
            if (description != null) {
                bitmapLarger = larger(null, description.getIconBitmap());
            }
        } catch (Throwable th) {
        }
        return larger(larger(larger(bitmapLarger, mediaMetadata.getBitmap("android.media.metadata.ALBUM_ART")), mediaMetadata.getBitmap("android.media.metadata.ART")), mediaMetadata.getBitmap("android.media.metadata.DISPLAY_ICON"));
    }

    private static String artworkUri(MediaMetadata mediaMetadata) {
        String string;
        if (mediaMetadata == null) {
            return "";
        }
        String strNz = nz(mediaMetadata.getString("android.media.metadata.ALBUM_ART_URI"));
        if (strNz.isEmpty()) {
            strNz = nz(mediaMetadata.getString("android.media.metadata.ART_URI"));
        }
        if (strNz.isEmpty()) {
            strNz = nz(mediaMetadata.getString("android.media.metadata.DISPLAY_ICON_URI"));
        }
        if (strNz.isEmpty()) {
            try {
                MediaDescription description = mediaMetadata.getDescription();
                if (description != null && description.getIconUri() != null) {
                    strNz = description.getIconUri().toString();
                }
            } catch (Throwable th) {
            }
        }
        if (!strNz.isEmpty()) {
            return strNz;
        }
        try {
            for (String str : mediaMetadata.keySet()) {
                if (str != null && (string = mediaMetadata.getString(str)) != null && !string.isEmpty()) {
                    String lowerCase = string.toLowerCase(Locale.US);
                    if (lowerCase.contains("ytimg.com") || lowerCase.contains("ggpht.com") || lowerCase.startsWith("http://") || lowerCase.startsWith("https://") || lowerCase.startsWith("content://")) {
                        String lowerCase2 = str.toLowerCase(Locale.US);
                        if (lowerCase2.contains("art") || lowerCase2.contains("icon") || lowerCase2.contains("thumb") || lowerCase2.contains("image") || lowerCase2.contains("cover") || lowerCase.contains("ytimg") || lowerCase.contains("youtube")) {
                            return string;
                        }
                    }
                }
            }
        } catch (Throwable th2) {
        }
        return "";
    }

    private void cacheArt(String str, String str2) {
        if (str.isEmpty() || str2.isEmpty()) {
            return;
        }
        if (this.artCache.size() >= 3) {
            Iterator<String> it = this.artCache.keySet().iterator();
            it.next();
            it.remove();
        }
        this.artCache.put(str, str2);
    }

    private boolean canLaunch(String str) {
        if (this.appContext == null || str == null || str.isEmpty()) {
            return false;
        }
        if (ProjectionPresence.isProjectionPackage(str)) {
            return true;
        }
        try {
            return this.appContext.getPackageManager().getLaunchIntentForPackage(str) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean changed(String lastPayloadSignature) {
        if (lastPayloadSignature.equals(this.lastPayloadSignature)) {
            return false;
        }
        this.lastPayloadSignature = lastPayloadSignature;
        return true;
    }

    private void clearSessionTrack() {
        this.sessionTrack = null;
        this.sessionController = null;
        emit();
    }

    private void dispatchMediaKey(int i) {
        Throwable th;
        if (this.appContext == null) {
            return;
        }
        try {
            AudioManager audioManager = (AudioManager) this.appContext.getSystemService("audio");
            if (audioManager == null) {
                return;
            }
            long jUptimeMillis = SystemClock.uptimeMillis();
            try {
                audioManager.dispatchMediaKeyEvent(new KeyEvent(jUptimeMillis, jUptimeMillis, 0, i, 0));
                audioManager.dispatchMediaKeyEvent(new KeyEvent(jUptimeMillis, jUptimeMillis, 1, i, 0));
                Log.w("H6Media", "media key " + i);
                return;
            } catch (Throwable th2) {
                th = th2;
            }
        } catch (Throwable th3) {
            th = th3;
        }
        Log.w("H6Media", "media key failed", th);
    }

    private Bitmap downloadArtwork(String str) {
        String strYoutubeVideoId = youtubeVideoId(str);
        if (strYoutubeVideoId == null) {
            return downloadBitmap(str);
        }
        for (String str2 : new String[]{"https://i.ytimg.com/vi/" + strYoutubeVideoId + "/maxresdefault.jpg", "https://i.ytimg.com/vi/" + strYoutubeVideoId + "/hq720.jpg", "https://i.ytimg.com/vi/" + strYoutubeVideoId + "/hqdefault.jpg", "https://i.ytimg.com/vi/" + strYoutubeVideoId + "/mqdefault.jpg"}) {
            Bitmap bitmapDownloadBitmap = downloadBitmap(str2);
            if (usable(bitmapDownloadBitmap) != null) {
                return bitmapDownloadBitmap;
            }
        }
        return null;
    }

    private static Bitmap downloadBitmap(String str) {
        HttpURLConnection httpURLConnection = null;
        try {
            httpURLConnection = (HttpURLConnection) new URL(str).openConnection();
            httpURLConnection.setConnectTimeout(5000);
            httpURLConnection.setReadTimeout(8000);
            httpURLConnection.setInstanceFollowRedirects(true);
            httpURLConnection.setRequestProperty("User-Agent", "H6Viewer/1.0");
            int responseCode = httpURLConnection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                InputStream inputStream = httpURLConnection.getInputStream();
                try {
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    byte[] bArr = new byte[8192];
                    while (true) {
                        int i = inputStream.read(bArr);
                        if (i == -1) {
                            break;
                        }
                        byteArrayOutputStream.write(bArr, 0, i);
                    }
                    byte[] byteArray = byteArrayOutputStream.toByteArray();
                    Bitmap bitmapDecodeByteArray = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);
                    if (inputStream != null) {
                        inputStream.close();
                    }
                    if (httpURLConnection != null) {
                        httpURLConnection.disconnect();
                    }
                    return bitmapDecodeByteArray;
                } catch (Throwable th) {
                    if (inputStream != null) {
                        try {
                            inputStream.close();
                        } catch (Throwable th2) {
                            th.addSuppressed(th2);
                        }
                    }
                    throw th;
                }
            }
            if (httpURLConnection != null) {
                httpURLConnection.disconnect();
            }
            return null;
        } catch (Throwable th3) {
            Log.w("H6Media", "art http failed " + str, th3);
            return null;
        }
    }

    private void emit() {
        if (this.callback == null) {
            return;
        }
        MediaTrack mediaTrackWinner = winner();
        boolean z = true;
        if (mediaTrackWinner == null) {
            if (hasListenerAccess() && MediaNotificationListener.isConnected()) {
                z = false;
            }
            this.lastArtKey = "";
            this.lastArtData = "";
            this.lastWinnerSource = 0;
            if (changed("empty|" + z)) {
                this.callback.onUpdate(emptyPayload(z));
                return;
            }
            return;
        }
        if (mediaTrackWinner.source != this.lastWinnerSource) {
            this.lastWinnerSource = mediaTrackWinner.source;
            Log.w("H6Media", "now playing source=" + mediaTrackWinner.appLabel + " (" + mediaTrackWinner.packageName + ")");
        }
        String strArtDataUrl = artDataUrl(mediaTrackWinner);
        if (changed(mediaTrackWinner.source + "|" + mediaTrackWinner.packageName + "|" + mediaTrackWinner.title + "|" + mediaTrackWinner.artist + "|" + mediaTrackWinner.album + "|" + mediaTrackWinner.durationMs + "|" + mediaTrackWinner.playing + "|" + mediaTrackWinner.appLabel + "|" + this.lastArtKey + "|" + strArtDataUrl.length() + "|" + canLaunch(mediaTrackWinner.packageName) + "|" + appIconDataUrl(mediaTrackWinner.packageName).length())) {
            JSONObject jSONObjectEmptyPayload = emptyPayload(false);
            try {
                jSONObjectEmptyPayload.put("title", mediaTrackWinner.title);
                jSONObjectEmptyPayload.put("artist", mediaTrackWinner.artist);
                jSONObjectEmptyPayload.put("album", mediaTrackWinner.album);
                jSONObjectEmptyPayload.put("durationMs", mediaTrackWinner.durationMs);
                jSONObjectEmptyPayload.put("positionMs", livePosition(mediaTrackWinner));
                jSONObjectEmptyPayload.put("playing", mediaTrackWinner.playing);
                jSONObjectEmptyPayload.put("appLabel", mediaTrackWinner.appLabel);
                jSONObjectEmptyPayload.put("packageName", mediaTrackWinner.packageName);
                if (mediaTrackWinner.title.isEmpty() && mediaTrackWinner.artist.isEmpty()) {
                    z = false;
                }
                jSONObjectEmptyPayload.put("hasTrack", z);
                jSONObjectEmptyPayload.put("artDataUrl", strArtDataUrl);
                jSONObjectEmptyPayload.put("appIcon", appIconDataUrl(mediaTrackWinner.packageName));
                jSONObjectEmptyPayload.put("canLaunch", canLaunch(mediaTrackWinner.packageName));
                jSONObjectEmptyPayload.put("needsListener", false);
            } catch (Exception e) {
                Log.w("H6Media", "emit failed", e);
            }
            this.callback.onUpdate(jSONObjectEmptyPayload);
        }
    }

    static void emitFromController(Context context, MediaController mediaController) {
        MediaNowPlaying mediaNowPlaying = INSTANCE;
        if (mediaNowPlaying == null || mediaNowPlaying.callback == null) {
            return;
        }
        mediaNowPlaying.handler.post(() -> mediaNowPlaying.readSession(context, mediaController));
    }

    static void emitNeedsListener() {
        MediaNowPlaying mediaNowPlaying = INSTANCE;
        if (mediaNowPlaying == null) {
            return;
        }
        Handler handler = mediaNowPlaying.handler;
        Objects.requireNonNull(mediaNowPlaying);
        handler.post(() -> mediaNowPlaying.clearSessionTrack());
    }

    private static JSONObject emptyPayload(boolean z) {
        JSONObject jSONObject = new JSONObject();
        try {
            jSONObject.put("title", "");
            jSONObject.put("artist", "");
            jSONObject.put("album", "");
            jSONObject.put("durationMs", 0);
            jSONObject.put("positionMs", 0);
            jSONObject.put("playing", false);
            jSONObject.put("appLabel", z ? "ENABLE MEDIA ACCESS" : "");
            jSONObject.put("packageName", "");
            jSONObject.put("artDataUrl", "");
            jSONObject.put("appIcon", "");
            jSONObject.put("canLaunch", false);
            jSONObject.put("hasTrack", false);
            jSONObject.put("needsListener", z);
        } catch (Exception e) {
        }
        return jSONObject;
    }

    private String encodeArt(Bitmap bitmap) {
        Bitmap bitmapScaleArt = scaleArt(bitmap);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmapScaleArt.compress(Bitmap.CompressFormat.JPEG, 88, byteArrayOutputStream);
        return "data:image/jpeg;base64," + Base64.encodeToString(byteArrayOutputStream.toByteArray(), 2);
    }

    private static long extrapolate(PlaybackState playbackState, long j) {
        long position = playbackState.getPosition();
        if (playbackState.getState() == 3 && playbackState.getLastPositionUpdateTime() > 0) {
            position = Math.max(0L, ((long) ((SystemClock.elapsedRealtime() - playbackState.getLastPositionUpdateTime()) * (playbackState.getPlaybackSpeed() > 0.0f ? playbackState.getPlaybackSpeed() : 1.0f))) + position);
            if (j > 0) {
                position = Math.min(position, j);
            }
        }
        return Math.max(0L, position);
    }

    private static boolean isHttp(String str) {
        if (str == null) {
            return false;
        }
        String lowerCase = str.toLowerCase(Locale.US);
        return lowerCase.startsWith("http://") || lowerCase.startsWith("https://");
    }

    private static boolean isYouTubeApp(String str) {
        return str != null && str.toLowerCase(Locale.US).contains("youtube");
    }

    private static Bitmap larger(Bitmap bitmap, Bitmap bitmap2) {
        if (bitmap2 == null) {
            return bitmap;
        }
        if (bitmap != null && bitmap2.getWidth() * bitmap2.getHeight() <= bitmap.getWidth() * bitmap.getHeight()) {
            return bitmap;
        }
        return bitmap2;
    }

    private long livePosition(MediaTrack mediaTrack) {
        PlaybackState playbackState;
        return (mediaTrack.source != 1 || this.sessionController == null || (playbackState = this.sessionController.getPlaybackState()) == null) ? mediaTrack.currentPositionMs() : extrapolate(playbackState, mediaTrack.durationMs);
    }

    private Bitmap loadLocalArtworkUri(String str) {
        if (this.appContext == null || str == null || str.isEmpty() || isHttp(str)) {
            return null;
        }
        try {
            Uri uri = Uri.parse(str);
            if (uri.getScheme() == null) {
                return null;
            }
            try {
                InputStream inputStreamOpenInputStream = this.appContext.getContentResolver().openInputStream(uri);
                if (inputStreamOpenInputStream == null) {
                    if (inputStreamOpenInputStream != null) {
                        inputStreamOpenInputStream.close();
                    }
                    return null;
                }
                try {
                    Bitmap bitmapDecodeStream = BitmapFactory.decodeStream(inputStreamOpenInputStream);
                    if (inputStreamOpenInputStream != null) {
                        inputStreamOpenInputStream.close();
                    }
                    return bitmapDecodeStream;
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
            } catch (Throwable th3) {
                Log.w("H6Media", "art uri load failed", th3);
                return null;
            }
        } catch (Throwable th4) {
            return null;
        }
    }

    private void logArtMiss(String str, MediaMetadata mediaMetadata) {
        StringBuilder sb = new StringBuilder();
        if (mediaMetadata != null) {
            try {
                Iterator<String> it = mediaMetadata.keySet().iterator();
                while (it.hasNext()) {
                    String next = it.next();
                    if (sb.length() > 0) {
                        sb.append(' ');
                    }
                    sb.append(next == null ? "?" : next.replace("android.media.metadata.", ""));
                    String string = null;
                    try {
                        string = mediaMetadata.getString(next);
                    } catch (Throwable th) {
                    }
                    if (string != null && !string.isEmpty()) {
                        sb.append('=').append(string.length() > 60 ? string.substring(0, 60) : string);
                    } else if (mediaMetadata.getBitmap(next) != null) {
                        sb.append("=<bitmap ").append(mediaMetadata.getBitmap(next).getWidth()).append('x').append(mediaMetadata.getBitmap(next).getHeight()).append('>');
                    }
                }
            } catch (Throwable th2) {
            }
        }
        String str2 = "art miss pkg=" + str + " meta[" + ((Object) sb) + "]";
        if (str2.equals(this.lastArtMissLog)) {
            return;
        }
        this.lastArtMissLog = str2;
        Log.w("H6Media", str2);
    }

    static boolean needsArtwork() {
        MediaNowPlaying mediaNowPlaying = INSTANCE;
        if (mediaNowPlaying == null) {
            return true;
        }
        MediaTrack mediaTrackWinner = mediaNowPlaying.winner();
        if (mediaTrackWinner != null && mediaTrackWinner.source != 1) {
            return false;
        }
        String str = mediaNowPlaying.lastArtData;
        return str == null || str.isEmpty() || str.startsWith("http");
    }

    private static String nz(String str) {
        return str == null ? "" : str;
    }

    static void onListenerConnected() {
        MediaNowPlaying mediaNowPlaying = INSTANCE;
        if (mediaNowPlaying == null || !mediaNowPlaying.started) {
            return;
        }
        mediaNowPlaying.handler.post(() -> {
            mediaNowPlaying.stopAccessRetry();
            MediaNotificationListener mediaNotificationListener = MediaNotificationListener.getInstance();
            if (mediaNotificationListener != null) {
                mediaNotificationListener.startSessionWatch();
                mediaNotificationListener.requestPush();
            }
        });
    }

    static void onListenerDisconnected() {
        MediaNowPlaying mediaNowPlaying = INSTANCE;
        if (mediaNowPlaying == null) {
            return;
        }
        mediaNowPlaying.handler.post(() -> {
            mediaNowPlaying.clearSessionTrack();
            if (mediaNowPlaying.started) {
                mediaNowPlaying.scheduleAccessRetry();
            }
        });
    }

    private void readSession(Context context, MediaController mediaController) {
        this.sessionController = mediaController;
        if (mediaController == null) {
            this.sessionTrack = null;
            emit();
            return;
        }
        Context context2 = context != null ? context : this.appContext;
        MediaMetadata metadata = mediaController.getMetadata();
        PlaybackState playbackState = mediaController.getPlaybackState();
        MediaTrack mediaTrack = new MediaTrack(1);
        mediaTrack.title = metadata == null ? "" : nz(metadata.getString("android.media.metadata.TITLE"));
        mediaTrack.artist = metadata == null ? "" : nz(metadata.getString("android.media.metadata.ARTIST"));
        mediaTrack.album = metadata == null ? "" : nz(metadata.getString("android.media.metadata.ALBUM"));
        mediaTrack.durationMs = metadata != null ? Math.max(0L, metadata.getLong("android.media.metadata.DURATION")) : 0L;
        if (playbackState != null) {
            mediaTrack.playing = playbackState.getState() == 3;
            mediaTrack.positionMs = extrapolate(playbackState, mediaTrack.durationMs);
            mediaTrack.positionAtMs = SystemClock.elapsedRealtime();
        }
        String packageName = mediaController.getPackageName() != null ? mediaController.getPackageName() : "";
        mediaTrack.packageName = packageName;
        mediaTrack.appLabel = appLabel(context2, packageName);
        resolveSessionArt(mediaTrack, metadata);
        this.sessionTrack = mediaTrack;
        emit();
    }

    private static String remoteArtworkUrl(MediaMetadata mediaMetadata, String str) {
        String strYoutubeVideoId = youtubeVideoId(str);
        if (strYoutubeVideoId == null) {
            strYoutubeVideoId = youtubeVideoIdFromMeta(mediaMetadata);
        }
        if (strYoutubeVideoId != null) {
            return "https://i.ytimg.com/vi/" + strYoutubeVideoId + "/maxresdefault.jpg";
        }
        return isHttp(str) ? str : "";
    }

    private void resolveSessionArt(MediaTrack mediaTrack, MediaMetadata mediaMetadata) {
        MediaNotificationListener mediaNotificationListener;
        String strArtworkUri = artworkUri(mediaMetadata);
        Bitmap bitmapLarger = larger(usable(artworkBitmap(mediaMetadata)), usable(loadLocalArtworkUri(strArtworkUri)));
        if (bitmapLarger == null && (mediaNotificationListener = MediaNotificationListener.getInstance()) != null) {
            bitmapLarger = usable(mediaNotificationListener.artworkFromNotification(mediaTrack.packageName));
        }
        String strRemoteArtworkUrl = remoteArtworkUrl(mediaMetadata, strArtworkUri);
        if (bitmapLarger != null && !strRemoteArtworkUrl.isEmpty() && Math.max(bitmapLarger.getWidth(), bitmapLarger.getHeight()) < 320) {
            bitmapLarger = null;
        }
        mediaTrack.art = bitmapLarger;
        mediaTrack.remoteArtUrl = bitmapLarger == null ? strRemoteArtworkUrl : "";
        if (bitmapLarger == null && strRemoteArtworkUrl.isEmpty() && isYouTubeApp(mediaTrack.packageName)) {
            mediaTrack.artSearchQuery = (mediaTrack.title + " " + mediaTrack.artist).trim();
        }
        mediaTrack.artKey = (bitmapLarger == null ? "0" : bitmapLarger.getWidth() + "x" + bitmapLarger.getHeight() + ":" + bitmapLarger.hashCode()) + "|" + strArtworkUri + "|" + strRemoteArtworkUrl + "|" + mediaTrack.artSearchQuery + "|" + mediaTrack.packageName;
        if (mediaTrack.hasArtSource()) {
            return;
        }
        logArtMiss(mediaTrack.packageName, mediaMetadata);
    }

    private static Bitmap scaleArt(Bitmap bitmap) {
        Bitmap bitmapCopy;
        Bitmap bitmap2 = bitmap;
        if (Build.VERSION.SDK_INT >= 26 && bitmap.getConfig() == Bitmap.Config.HARDWARE && (bitmapCopy = bitmap.copy(Bitmap.Config.ARGB_8888, false)) != null) {
            bitmap2 = bitmapCopy;
        }
        int width = bitmap2.getWidth();
        int height = bitmap2.getHeight();
        if (width <= 720 && height <= 720) {
            return bitmap2;
        }
        float fMin = Math.min(720.0f / width, 720.0f / height);
        return Bitmap.createScaledBitmap(bitmap2, Math.max(1, Math.round(width * fMin)), Math.max(1, Math.round(height * fMin)), true);
    }

    private void scheduleAccessRetry() {
        stopAccessRetry();
        this.accessRetry = () -> {
            MediaNotificationListener mediaNotificationListener = MediaNotificationListener.getInstance();
            if (mediaNotificationListener != null) {
                Log.w("H6Media", "Notification listener connected — starting session watch");
                mediaNotificationListener.startSessionWatch();
                mediaNotificationListener.requestPush();
            } else {
                if (!hasListenerAccess()) {
                    clearSessionTrack();
                }
                this.handler.postDelayed(this.accessRetry, 2500L);
            }
        };
        this.handler.postDelayed(this.accessRetry, 2500L);
    }

    private void scheduleArtworkDownload(String inflightArtUri, String lastArtKey) {
        if (this.artHandler == null || inflightArtUri == null || inflightArtUri.isEmpty() || inflightArtUri.equals(this.inflightArtUri)) {
            return;
        }
        this.inflightArtUri = inflightArtUri;
        this.artHandler.post(() -> {
            final Bitmap bitmap3 = downloadArtwork(inflightArtUri);
            this.handler.post(() -> {
                if (inflightArtUri.equals(this.inflightArtUri)) {
                    this.inflightArtUri = "";
                    if (bitmap3 == null || bitmap3.isRecycled()) {
                        Log.w("H6Media", "art download failed " + inflightArtUri);
                        return;
                    }
                    this.lastArtKey = lastArtKey;
                    this.lastArtData = encodeArt(bitmap3);
                    cacheArt(lastArtKey, this.lastArtData);
                    Log.w("H6Media", "art downloaded " + bitmap3.getWidth() + "x" + bitmap3.getHeight() + " " + inflightArtUri);
                    MediaNotificationListener mediaNotificationListener = MediaNotificationListener.getInstance();
                    if (mediaNotificationListener != null) {
                        mediaNotificationListener.requestPush();
                    } else {
                        emit();
                    }
                }
            });
        });
    }

    private void scheduleYouTubeLookup(String str, String str2) {
        if (this.artHandler == null || str.isEmpty() || str.equals(this.inflightSearch)) {
            return;
        }
        if (!str.equals(this.failedSearch) || SystemClock.elapsedRealtime() - this.failedSearchAtMs >= 20000) {
            String str3 = this.searchedArtUrl.get(str);
            if (str3 != null) {
                scheduleArtworkDownload(str3, str2);
            } else {
                this.inflightSearch = str;
                this.artHandler.post(() -> {
                    final String script = searchYouTubeVideoId(str);
                    this.handler.post(() -> {
                        this.inflightSearch = "";
                        if (script == null) {
                            this.failedSearch = str;
                            this.failedSearchAtMs = SystemClock.elapsedRealtime();
                            Log.w("H6Media", "youtube lookup failed for " + str);
                        } else {
                            String str4 = "https://i.ytimg.com/vi/" + script + "/maxresdefault.jpg";
                            if (this.searchedArtUrl.size() > 64) {
                                this.searchedArtUrl.clear();
                            }
                            this.searchedArtUrl.put(str, str4);
                            Log.w("H6Media", "youtube lookup " + str + " -> " + script);
                            scheduleArtworkDownload(str4, str2);
                        }
                    });
                });
            }
        }
    }

    private static String searchYouTubeVideoId(String str) {
        HttpURLConnection httpURLConnection = null;
        try {
            httpURLConnection = (HttpURLConnection) new URL("https://www.youtube.com/results?hl=en&search_query=" + URLEncoder.encode(str, "UTF-8")).openConnection();
            httpURLConnection.setConnectTimeout(6000);
            httpURLConnection.setReadTimeout(15000);
            httpURLConnection.setInstanceFollowRedirects(true);
            httpURLConnection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            httpURLConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36");
            int responseCode = httpURLConnection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                InputStream inputStream = httpURLConnection.getInputStream();
                try {
                    byte[] bArr = new byte[16384];
                    StringBuilder sb = new StringBuilder();
                    int i = 0;
                    while (true) {
                        int i2 = inputStream.read(bArr);
                        if (i2 == -1 || i >= 2097152) {
                            break;
                        }
                        i += i2;
                        sb.append(new String(bArr, 0, i2, "UTF-8"));
                        Matcher matcher = SEARCH_VIDEO_ID.matcher(sb);
                        if (matcher.find()) {
                            String strGroup = matcher.group(1);
                            if (inputStream != null) {
                                inputStream.close();
                            }
                            if (httpURLConnection != null) {
                                httpURLConnection.disconnect();
                            }
                            return strGroup;
                        }
                        if (sb.length() > 32768) {
                            sb.delete(0, sb.length() - 64);
                        }
                    }
                    if (inputStream != null) {
                        inputStream.close();
                    }
                    if (httpURLConnection != null) {
                        httpURLConnection.disconnect();
                    }
                    return null;
                } catch (Throwable th) {
                    if (inputStream != null) {
                        try {
                            inputStream.close();
                        } catch (Throwable th2) {
                            th.addSuppressed(th2);
                        }
                    }
                    throw th;
                }
            }
            if (httpURLConnection != null) {
                httpURLConnection.disconnect();
            }
            return null;
        } catch (Throwable th3) {
            Log.w("H6Media", "youtube lookup error: " + th3);
            return null;
        }
    }

    private void startProjectionSources() {
        if (this.appContext == null) {
            return;
        }
        try {
            this.mediaCenter.start(this.appContext, mediaTrack2 -> {
                this.handler.post(() -> {
                    this.mediaCenterTrack = mediaTrack2;
                    emit();
                });
            });
        } catch (Throwable th) {
            Log.w("H6Media", "MediaCenter source start failed", th);
        }
        try {
            this.carPlay.start(this.appContext, mediaTrack -> {
                this.handler.post(() -> {
                    this.carPlayTrack = mediaTrack;
                    emit();
                });
            });
        } catch (Throwable th2) {
            Log.w("H6Media", "CarPlay source start failed", th2);
        }
    }

    private void stopAccessRetry() {
        if (this.accessRetry != null) {
            this.handler.removeCallbacks(this.accessRetry);
        }
        this.accessRetry = null;
    }

    private void stopPositionTick() {
        if (this.positionTick != null) {
            this.handler.removeCallbacks(this.positionTick);
        }
        this.positionTick = null;
    }

    static void syncPositionFromListener(MediaController mediaController) {
        MediaNowPlaying mediaNowPlaying = INSTANCE;
        if (mediaNowPlaying == null) {
            return;
        }
        mediaNowPlaying.handler.post(() -> {
            mediaNowPlaying.sessionController = mediaController;
            mediaNowPlaying.syncPositionTick();
        });
    }

    private void syncPositionTick() {
        stopPositionTick();
        this.positionTick = new Runnable() {
            @Override // java.lang.Runnable
            public void run() {
                MediaTrack mediaTrackWinner = MediaNowPlaying.this.winner();
                if (mediaTrackWinner != null && mediaTrackWinner.playing && MediaNowPlaying.this.callback != null) {
                    MediaNowPlaying.this.callback.onPosition(MediaNowPlaying.this.livePosition(mediaTrackWinner));
                }
                MediaNowPlaying.this.handler.postDelayed(this, 250L);
            }
        };
        this.handler.postDelayed(this.positionTick, 250L);
    }

    private static Bitmap usable(Bitmap bitmap) {
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

    private MediaTrack winner() {
        MediaTrack mediaTrack = null;
        int i = Integer.MIN_VALUE;
        for (MediaTrack mediaTrack2 : new MediaTrack[]{this.carPlayTrack, this.mediaCenterTrack, this.sessionTrack}) {
            if (mediaTrack2 != null && mediaTrack2.hasTrack()) {
                int i2 = (mediaTrack2.playing ? 100 : 0) + mediaTrack2.source;
                if (i2 > i) {
                    i = i2;
                    mediaTrack = mediaTrack2;
                }
            }
        }
        return mediaTrack == null ? this.sessionTrack : mediaTrack;
    }

    private static String youtubeVideoId(String str) {
        if (str == null || str.isEmpty()) {
            return null;
        }
        Matcher matcher = YT_ID.matcher(str);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static String youtubeVideoIdFromMeta(MediaMetadata mediaMetadata) {
        if (mediaMetadata == null) {
            return null;
        }
        String strYoutubeVideoIdOrBare = youtubeVideoIdOrBare(nz(mediaMetadata.getString("android.media.metadata.MEDIA_ID")));
        if (strYoutubeVideoIdOrBare == null) {
            strYoutubeVideoIdOrBare = youtubeVideoId(nz(mediaMetadata.getString("android.media.metadata.MEDIA_URI")));
        }
        if (strYoutubeVideoIdOrBare == null) {
            strYoutubeVideoIdOrBare = youtubeVideoId(nz(mediaMetadata.getString("android.media.metadata.DISPLAY_DESCRIPTION")));
        }
        if (strYoutubeVideoIdOrBare != null) {
            return strYoutubeVideoIdOrBare;
        }
        try {
            MediaDescription description = mediaMetadata.getDescription();
            if (description != null) {
                String strYoutubeVideoIdOrBare2 = youtubeVideoIdOrBare(description.getMediaId());
                if (strYoutubeVideoIdOrBare2 == null && description.getMediaUri() != null) {
                    strYoutubeVideoIdOrBare2 = youtubeVideoId(description.getMediaUri().toString());
                }
                if (strYoutubeVideoIdOrBare2 != null) {
                    return strYoutubeVideoIdOrBare2;
                }
            }
        } catch (Throwable th) {
        }
        try {
            Iterator<String> it = mediaMetadata.keySet().iterator();
            while (it.hasNext()) {
                String strYoutubeVideoId = youtubeVideoId(mediaMetadata.getString(it.next()));
                if (strYoutubeVideoId != null) {
                    return strYoutubeVideoId;
                }
            }
        } catch (Throwable th2) {
        }
        return null;
    }

    private static String youtubeVideoIdOrBare(String str) {
        String strYoutubeVideoId = youtubeVideoId(str);
        if (strYoutubeVideoId != null) {
            return strYoutubeVideoId;
        }
        if (str == null || !YT_ID_BARE.matcher(str).matches()) {
            return null;
        }
        return str;
    }

    void attach(Context context, Callback callback) {
        this.appContext = context.getApplicationContext();
        this.callback = callback;
        this.listenerComponent = new ComponentName(context, (Class<?>) MediaNotificationListener.class);
        INSTANCE = this;
        if (this.artHandler == null) {
            HandlerThread handlerThread = new HandlerThread("H6Art");
            handlerThread.start();
            this.artHandler = new Handler(handlerThread.getLooper());
        }
    }

    boolean hasAndroidAutoTrack() {
        return this.mediaCenterTrack != null && this.mediaCenterTrack.hasTrack() && "ANDROID AUTO".equals(this.mediaCenterTrack.appLabel);
    }

    boolean hasCarPlayTrack() {
        return this.carPlayTrack != null && this.carPlayTrack.hasTrack();
    }

    boolean hasListenerAccess() {
        if (this.appContext == null || this.listenerComponent == null) {
            return false;
        }
        if (MediaNotificationListener.isConnected()) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= 27) {
            try {
                NotificationManager notificationManager = (NotificationManager) this.appContext.getSystemService("notification");
                if (notificationManager != null && notificationManager.isNotificationListenerAccessGranted(this.listenerComponent)) {
                    return true;
                }
            } catch (Throwable th) {
            }
        }
        String string = Settings.Secure.getString(this.appContext.getContentResolver(), "enabled_notification_listeners");
        if (string == null || string.isEmpty()) {
            return false;
        }
        String strFlattenToString = this.listenerComponent.flattenToString();
        String strFlattenToShortString = this.listenerComponent.flattenToShortString();
        for (String str : string.split(":")) {
            if (strFlattenToString.equals(str) || strFlattenToShortString.equals(str)) {
                return true;
            }
        }
        return false;
    }

    boolean isPlaying() {
        MediaTrack mediaTrackWinner = winner();
        return mediaTrackWinner != null && mediaTrackWinner.playing;
    }

    void next() {
        MediaTrack mediaTrackWinner = winner();
        if (mediaTrackWinner != null && mediaTrackWinner.source == 3 && this.carPlay.next()) {
            return;
        }
        MediaNotificationListener mediaNotificationListener = MediaNotificationListener.getInstance();
        if (mediaNotificationListener == null || !(mediaTrackWinner == null || mediaTrackWinner.source == 1)) {
            dispatchMediaKey(87);
        } else {
            mediaNotificationListener.next();
        }
    }

    void playPause() {
        MediaTrack mediaTrackWinner = winner();
        if (mediaTrackWinner != null && mediaTrackWinner.source == 3 && this.carPlay.setPlaying(!mediaTrackWinner.playing)) {
            return;
        }
        if (mediaTrackWinner != null && mediaTrackWinner.source == 2 && this.mediaCenter.setPlaying(!mediaTrackWinner.playing)) {
            return;
        }
        MediaNotificationListener mediaNotificationListener = MediaNotificationListener.getInstance();
        if (mediaNotificationListener == null || !(mediaTrackWinner == null || mediaTrackWinner.source == 1)) {
            dispatchMediaKey(85);
        } else {
            mediaNotificationListener.playPause();
        }
    }

    void prev() {
        MediaTrack mediaTrackWinner = winner();
        if (mediaTrackWinner != null && mediaTrackWinner.source == 3 && this.carPlay.previous()) {
            return;
        }
        MediaNotificationListener mediaNotificationListener = MediaNotificationListener.getInstance();
        if (mediaNotificationListener == null || !(mediaTrackWinner == null || mediaTrackWinner.source == 1)) {
            dispatchMediaKey(88);
        } else {
            mediaNotificationListener.prev();
        }
    }

    void pushNow() {
        this.lastPayloadSignature = "";
        MediaNotificationListener mediaNotificationListener = MediaNotificationListener.getInstance();
        if (mediaNotificationListener != null) {
            mediaNotificationListener.requestPush();
        } else {
            emit();
        }
    }

    void start() {
        this.started = true;
        stopAccessRetry();
        startProjectionSources();
        syncPositionTick();
        MediaNotificationListener mediaNotificationListener = MediaNotificationListener.getInstance();
        if (mediaNotificationListener != null) {
            Log.w("H6Media", "Using connected notification listener for sessions");
            mediaNotificationListener.startSessionWatch();
            mediaNotificationListener.requestPush();
        } else if (hasListenerAccess()) {
            Log.w("H6Media", "Listener allowed; waiting for service connection");
            clearSessionTrack();
            scheduleAccessRetry();
        } else {
            Log.w("H6Media", "Notification listener not enabled — session media unavailable");
            clearSessionTrack();
            scheduleAccessRetry();
        }
    }

    void stop() {
        this.started = false;
        stopAccessRetry();
        stopPositionTick();
        this.mediaCenter.stop();
        this.carPlay.stop();
    }
}
