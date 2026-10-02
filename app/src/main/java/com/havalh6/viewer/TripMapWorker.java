package com.havalh6.viewer;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Base64;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.util.Iterator;

final class TripMapWorker {
    private static final int BATCH = 5;
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_ROUTE_POINTS = 800;
    private static final long SWEEP_MS = 600000;
    private static final String TAG = "H6Trip";
    private final Handler handler;
    private final TripMapRenderer renderer;
    private final TripStore store;
    private final Runnable sweep = new Runnable() {
        @Override // java.lang.Runnable
        public void run() {
            try {
                TripMapWorker.this.sweepOnce();
            } catch (Throwable th) {
                Log.w("H6Trip", "trip map sweep failed", th);
            }
            TripMapWorker.this.handler.removeCallbacks(this);
            TripMapWorker.this.handler.postDelayed(this, 600000L);
        }
    };

    TripMapWorker(Context context, TripStore tripStore) {
        this.store = tripStore;
        this.renderer = new TripMapRenderer(context.getApplicationContext());
        HandlerThread handlerThread = new HandlerThread("h6-trip-map");
        handlerThread.start();
        this.handler = new Handler(handlerThread.getLooper());
    }

    private void sweepOnce() {
        ReverseGeocoder.Place placeLookup;
        Iterator<Long> it = this.store.pendingRoutes(20).iterator();
        while (it.hasNext()) {
            this.store.saveRoute(it.next().longValue(), 300);
        }
        for (TripStore.PendingMap pendingMap : this.store.pendingMaps(5, 5)) {
            if (pendingMap.needMap) {
                double[][] dArrFixes = this.store.fixes(pendingMap.startMs, 800);
                if (dArrFixes[0].length < 2) {
                    this.store.setTripMap(pendingMap.startMs, "", null);
                } else {
                    try {
                        TripMapRenderer.Result resultRender = this.renderer.render(pendingMap.startMs, dArrFixes[0], dArrFixes[1]);
                        if (resultRender == null) {
                            return;
                        } else {
                            this.store.setTripMap(pendingMap.startMs, resultRender.file.getName(), resultRender.view);
                        }
                    } catch (Exception e) {
                        Log.w("H6Trip", "trip map render failed for " + pendingMap.startMs, e);
                        this.store.bumpMapAttempts(pendingMap.startMs);
                    }
                }
            }
            if (!pendingMap.needPlaces) {
                continue;
            } else if (Double.isNaN(pendingMap.startLat) || Double.isNaN(pendingMap.endLat)) {
                this.store.setTripPlaces(pendingMap.startMs, "", "");
            } else {
                ReverseGeocoder.Place placeLookup2 = ReverseGeocoder.lookup(pendingMap.startLat, pendingMap.startLon);
                if (placeLookup2 == null || (placeLookup = ReverseGeocoder.lookup(pendingMap.endLat, pendingMap.endLon)) == null) {
                    return;
                }
                this.store.setTripPlaces(pendingMap.startMs, placeLookup2.label(), placeLookup.label());
                Log.w("H6Trip", "trip places " + pendingMap.startMs + ": " + placeLookup2.label() + " -> " + placeLookup.label());
            }
        }
    }

    String dataUrl(long j) {
        int i;
        File fileFileFor = this.renderer.fileFor(j);
        if (!fileFileFor.isFile() || fileFileFor.length() > 1000000) {
            return "";
        }
        byte[] bArr = new byte[(int) fileFileFor.length()];
        try {
            FileInputStream fileInputStream = new FileInputStream(fileFileFor);
            int i2 = 0;
            while (i2 < bArr.length && (i = fileInputStream.read(bArr, i2, bArr.length - i2)) >= 0) {
                try {
                    i2 += i;
                } catch (Throwable th) {
                    try {
                        fileInputStream.close();
                    } catch (Throwable th2) {
                        th.addSuppressed(th2);
                    }
                    throw th;
                }
            }
            if (i2 != bArr.length) {
                fileInputStream.close();
                return "";
            }
            fileInputStream.close();
            return "data:image/jpeg;base64," + Base64.encodeToString(bArr, 2);
        } catch (Exception e) {
            return "";
        }
    }

    void sweepSoon(long j) {
        this.handler.removeCallbacks(this.sweep);
        this.handler.postDelayed(this.sweep, j);
    }
}
