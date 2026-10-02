package com.havalh6.viewer;

import android.webkit.JavascriptInterface;

final class TripBridge {
    private static final int MAX_POINTS = 1500;
    private final TripRecorder recorder;

    TripBridge(TripRecorder tripRecorder) {
        this.recorder = tripRecorder;
    }

    @JavascriptInterface
    public void finishTrip() {
        this.recorder.finishTrip();
    }

    @JavascriptInterface
    public String getDays(String str, String str2) {
        return this.recorder.daysJson(str, str2);
    }

    @JavascriptInterface
    public String getLiveTrip() {
        return this.recorder.liveJson();
    }

    @JavascriptInterface
    public String getMonths() {
        return this.recorder.monthsJson();
    }

    @JavascriptInterface
    public String getStopTotals(String str) {
        try {
            return this.recorder.stopTotalsJson(Long.parseLong(str.trim()));
        } catch (RuntimeException e) {
            return "{}";
        }
    }

    @JavascriptInterface
    public String getTotals() {
        return this.recorder.totalsJson();
    }

    @JavascriptInterface
    public String getTrip(String str, int i) {
        int iMin = 1500;
        if (i > 0) {
            try {
                iMin = Math.min(1500, i);
            } catch (RuntimeException e) {
                return "null";
            }
        }
        return this.recorder.tripJson(Long.parseLong(str.trim()), iMin);
    }

    @JavascriptInterface
    public String getTripMap(String str) {
        try {
            return this.recorder.tripMapDataUrl(Long.parseLong(str.trim()));
        } catch (RuntimeException e) {
            return "";
        }
    }

    @JavascriptInterface
    public String getTripPointsSince(String str, String str2, int i) {
        int iMin = 1500;
        if (i > 0) {
            try {
                iMin = Math.min(1500, i);
            } catch (RuntimeException e) {
                return "null";
            }
        }
        return this.recorder.livePointsJson(Long.parseLong(str.trim()), Long.parseLong(str2.trim()), iMin);
    }

    @JavascriptInterface
    public String getTrips(int i, int i2) {
        return this.recorder.tripsJson(i, i2);
    }
}
