package com.havalh6.viewer;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class MediaPackages {
    private static final Set<String> PACKAGES = new HashSet(Arrays.asList("com.spotify.music", "com.spotify.tv.android", "com.google.android.apps.youtube.music", "com.google.android.youtube.tvmusic", "deezer.android.app", "deezer.android.tv", "com.aspiro.tidal", "com.amazon.mp3", "com.apple.android.music"));

    private MediaPackages() {
    }

    static boolean isMediaApp(String str, String str2) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        if (PACKAGES.contains(str)) {
            return true;
        }
        String lowerCase = str.toLowerCase(Locale.US);
        String lowerCase2 = str2 == null ? "" : str2.toLowerCase(Locale.US);
        return lowerCase.contains("spotify") || lowerCase.contains("youtube.music") || lowerCase.contains("youtubemusic") || lowerCase.contains("deezer") || lowerCase.contains("tidal") || lowerCase.contains("amazon.mp3") || lowerCase.contains("apple.android.music") || lowerCase2.contains("youtube music") || lowerCase2.contains("spotify") || lowerCase2.contains("deezer");
    }
}
