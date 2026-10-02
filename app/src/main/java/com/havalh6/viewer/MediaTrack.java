package com.havalh6.viewer;

import android.graphics.Bitmap;
import android.os.SystemClock;

final class MediaTrack {
    static final int SRC_CARPLAY = 3;
    static final int SRC_MEDIA_CENTER = 2;
    static final int SRC_SESSION = 1;
    Bitmap art;
    long durationMs;
    boolean playing;
    long positionMs;
    final int source;
    String packageName = "";
    String appLabel = "";
    String title = "";
    String artist = "";
    String album = "";
    long positionAtMs = SystemClock.elapsedRealtime();
    String artKey = "";
    String remoteArtUrl = "";
    String artSearchQuery = "";

    MediaTrack(int source) {
        this.source = source;
    }

    long currentPositionMs() {
        boolean z = this.playing;
        long j = this.positionMs;
        if (!z) {
            return j;
        }
        long jMax = j + Math.max(0L, SystemClock.elapsedRealtime() - this.positionAtMs);
        return this.durationMs > 0 ? Math.min(jMax, this.durationMs) : jMax;
    }

    boolean hasArtSource() {
        return (this.art == null && this.remoteArtUrl.isEmpty() && this.artSearchQuery.isEmpty()) ? false : true;
    }

    boolean hasTrack() {
        return (this.title.isEmpty() && this.artist.isEmpty() && this.art == null && this.remoteArtUrl.isEmpty()) ? false : true;
    }
}
