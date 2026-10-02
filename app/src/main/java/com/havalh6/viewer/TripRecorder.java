package com.havalh6.viewer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.json.JSONObject;

final class TripRecorder implements TripEngine.Listener {
    private static final String ACTION_CAR_DATA_UPDATE = "br.com.opengw.CAR_DATA_UPDATE";
    private static final String ACTION_REQUEST_SNAPSHOT = "com.haval.vehicle.REQUEST_SNAPSHOT";
    private static final String ACTION_VEHICLE_EVENT_CHANGED = "com.haval.vehicle.EVENT_CHANGED";
    private static final long CHECKPOINT_MS = 15000;
    private static final long GPS_MIN_MS = 1000;
    private static final Set<String> KEYS = new HashSet(Arrays.asList("car.basic.driving_ready_state", "car.basic.vehicle_speed", "car.basic.total_odometer", "car.ev_info.power_battery_voltage", "car.ev_info.cur_charge_current", "car.basic.instant_fuel_consumption", "haval.power.ice", "haval.power.flow", "car.basic.remain_fuel_percentage", "app.navigation.directions", "car.ev_info.cur_battery_power_percentage"));
    private static final int POINT_FLUSH = 30;
    private static final String TAG = "H6Trip";
    private static final long TICK_MS = 1000;
    private static TripRecorder instance;
    private final Context app;
    private TripEngine engine;
    private boolean gpsOn;
    private long lastCheckpointAt;
    private String lastReadyRaw;
    private long lastTickElapsed;
    private volatile TripMapWorker maps;
    private long pendingStartMs;
    private volatile TripStore store;
    private final Handler worker;
    private final List<TripPoint> pending = new ArrayList();
    private volatile String liveJson = "";
    private final BroadcastReceiver signals = new BroadcastReceiver() {
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }
            String stringExtra = intent.getStringExtra("key");
            String stringExtra2 = intent.getStringExtra("value");
            if (stringExtra == null || stringExtra2 == null) {
                return;
            }
            if (TripRecorder.KEYS.contains(stringExtra) || TripEngine.RAW_KEYS.contains(stringExtra)) {
                TripRecorder.this.onSignal(stringExtra, stringExtra2, TripRecorder.now());
            }
        }
    };
    private final LocationListener gps = new LocationListener() {
        @Override // android.location.LocationListener
        public void onLocationChanged(Location location) {
            if (TripRecorder.this.engine == null || location == null) {
                return;
            }
            TripRecorder.this.engine.onLocation(location.getLatitude(), location.getLongitude(), location.hasAltitude() ? location.getAltitude() : Double.NaN, location.hasAccuracy() ? location.getAccuracy() : 30.0d, TripRecorder.now());
        }

        @Override // android.location.LocationListener
        public void onProviderDisabled(String str) {
        }

        @Override // android.location.LocationListener
        public void onProviderEnabled(String str) {
        }

        @Override // android.location.LocationListener
        public void onStatusChanged(String str, int i, Bundle bundle) {
        }
    };
    private final Runnable tick = new Runnable() {
        @Override // java.lang.Runnable
        public void run() {
            try {
                TripRecorder.this.onTick(TripRecorder.now());
            } catch (Throwable th) {
                Log.w("H6Trip", "tick failed", th);
            }
            TripRecorder.this.worker.postDelayed(this, 1000L);
        }
    };
    private final HandlerThread thread = new HandlerThread("h6-trip");

    private TripRecorder(Context app) {
        this.app = app;
        this.thread.start();
        this.worker = new Handler(this.thread.getLooper());
        this.worker.post(() -> {
            try {
                TripStore tripStore = new TripStore(this.app);
                this.engine = new TripEngine(this);
                this.store = tripStore;
                this.maps = new TripMapWorker(this.app, tripStore);
                this.maps.sweepSoon(20000L);
                String strLoadCheckpoint = tripStore.loadCheckpoint();
                if (strLoadCheckpoint != null) {
                    this.engine.restore(TripState.decode(strLoadCheckpoint), now());
                }
                TripStore.Parked parkedLastParked = tripStore.lastParked();
                if (parkedLastParked != null) {
                    this.engine.primeParked(parkedLastParked.fuelPct, parkedLastParked.soc, parkedLastParked.lat, parkedLastParked.lon, parkedLastParked.endMs);
                }
            } catch (Throwable th) {
                Log.w("H6Trip", "trip store unavailable", th);
            }
            try {
                IntentFilter intentFilter = new IntentFilter("com.haval.vehicle.EVENT_CHANGED");
                intentFilter.addAction("br.com.opengw.CAR_DATA_UPDATE");
                this.app.registerReceiver(this.signals, intentFilter, null, this.worker);
            } catch (Exception e) {
                Log.w("H6Trip", "trip telemetry receiver not registered", e);
            }
            this.worker.post(this.tick);
        });
    }

    private void flushPoints() {
        TripStore tripStore = this.store;
        if (tripStore == null) {
            return;
        }
        synchronized (this.pending) {
            if (this.pending.isEmpty()) {
                return;
            }
            ArrayList arrayList = new ArrayList(this.pending);
            tripStore.appendPoints(this.pendingStartMs, arrayList);
            synchronized (this.pending) {
                this.pending.subList(0, Math.min(arrayList.size(), this.pending.size())).clear();
            }
        }
    }

    static synchronized TripRecorder get(Context context) {
        if (instance == null) {
            instance = new TripRecorder(context.getApplicationContext());
        }
        return instance;
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private void onNavigation(String str, long j) {
        try {
            try {
                JSONObject jSONObject = new JSONObject(str);
                try {
                    this.engine.onNavigation(jSONObject.optBoolean("active", false), jSONObject.isNull("remaining_m") ? Double.NaN : jSONObject.optDouble("remaining_m", Double.NaN), jSONObject.isNull("remaining_s") ? Double.NaN : jSONObject.optDouble("remaining_s", Double.NaN), "DESTINATION".equals(jSONObject.optString("turn", "")), j);
                } catch (Exception e) {
                }
            } catch (Exception e2) {
            }
        } catch (Exception e3) {
        }
    }

    private void onSignal(String str, String lastReadyRaw, long j) {
        if (this.engine == null) {
            return;
        }
        if ("app.navigation.directions".equals(str)) {
            onNavigation(lastReadyRaw, j);
            return;
        }
        if ("car.basic.driving_ready_state".equals(str) && !lastReadyRaw.equals(this.lastReadyRaw)) {
            this.lastReadyRaw = lastReadyRaw;
            Log.w("H6Trip", "ready " + lastReadyRaw);
        }
        boolean zIsOpen = this.engine.isOpen();
        this.engine.onSignal(str, lastReadyRaw, j);
        if ("car.basic.driving_ready_state".equals(str) || zIsOpen != this.engine.isOpen()) {
            persist(j);
        }
    }

    private void onTick(long j) {
        if (this.engine == null) {
            return;
        }
        long jElapsedRealtime = SystemClock.elapsedRealtime();
        if (this.lastTickElapsed > 0 && jElapsedRealtime - this.lastTickElapsed >= 600000) {
            Log.w("H6Trip", "woke after " + ((jElapsedRealtime - this.lastTickElapsed) / 1000) + " s");
            this.engine.closeInterrupted(j);
            requestSnapshot("woke from sleep");
        }
        this.lastTickElapsed = jElapsedRealtime;
        this.engine.tick(j);
        TripSummary tripSummaryLive = this.engine.live(j);
        this.liveJson = tripSummaryLive == null ? "" : TripStore.summaryJson(tripSummaryLive);
        setGps(this.engine.isOpen());
        if (!this.engine.isOpen() || j - this.lastCheckpointAt < 15000) {
            return;
        }
        persist(j);
    }

    private void persist(long lastCheckpointAt) {
        TripStore tripStore = this.store;
        if (tripStore == null || this.engine == null) {
            return;
        }
        this.lastCheckpointAt = lastCheckpointAt;
        if (this.engine.isOpen()) {
            flushPoints();
            tripStore.saveCheckpoint(this.engine.checkpoint(), lastCheckpointAt);
        }
    }

    private void requestSnapshot(String str) {
        try {
            Intent intent = new Intent("com.haval.vehicle.REQUEST_SNAPSHOT");
            intent.setPackage("br.com.opengw");
            intent.putExtra("requester", this.app.getPackageName());
            this.app.sendBroadcast(intent);
            Log.w("H6Trip", "snapshot requested: " + str);
        } catch (Exception e) {
            Log.w("H6Trip", "snapshot request failed", e);
        }
    }

    private void setGps(boolean z) {
        LocationManager locationManager;
        if (z == this.gpsOn || (locationManager = (LocationManager) this.app.getSystemService("location")) == null) {
            return;
        }
        if (!z) {
            locationManager.removeUpdates(this.gps);
            this.gpsOn = false;
        } else {
            if (this.app.checkSelfPermission("android.permission.ACCESS_FINE_LOCATION") != 0) {
                return;
            }
            try {
                locationManager.requestLocationUpdates("gps", 1000L, 0.0f, this.gps, this.thread.getLooper());
                this.gpsOn = true;
            } catch (IllegalArgumentException | SecurityException e) {
                Log.w("H6Trip", "GPS updates unavailable: " + e.getMessage());
            }
        }
    }

    String daysJson(String str, String str2) {
        TripStore tripStore = this.store;
        return tripStore == null ? "[]" : tripStore.daysJson(str, str2);
    }

    void finishTrip() {
        this.worker.post(() -> {
            if (this.engine == null) {
                return;
            }
            long jNow = now();
            Log.w("H6Trip", "trip finished by the driver");
            this.engine.finishNow(jNow);
            TripSummary tripSummaryLive = this.engine.live(jNow);
            this.liveJson = tripSummaryLive == null ? "" : TripStore.summaryJson(tripSummaryLive);
            persist(jNow);
        });
    }

    String liveJson() {
        return this.liveJson;
    }

    /**
     * Reconstruido a partir do dump de instrucoes que o jadx deixou no lugar do corpo
     * (ele falhou em fechar o bloco do monitor-exit). O bytecode e literal: se o inicio
     * pedido coincide com o do buffer em memoria, tira uma copia dele sob o lock; senao
     * manda uma lista vazia. Quem monta o JSON e o TripStore.
     */
    String livePointsJson(long j, long j2, int i) {
        TripStore tripStore = this.store;
        if (tripStore == null) {
            return "null";
        }
        ArrayList<TripPoint> arrayList;
        synchronized (this.pending) {
            arrayList = this.pendingStartMs == j ? new ArrayList<>(this.pending) : new ArrayList<>();
        }
        return tripStore.livePointsJson(j, j2, i, arrayList);
    }

    String monthsJson() {
        TripStore tripStore = this.store;
        return tripStore == null ? "[]" : tripStore.monthsJson();
    }

    @Override // com.havalh6.viewer.TripEngine.Listener
    public void onPoint(long pendingStartMs, TripPoint tripPoint) {
        boolean z;
        synchronized (this.pending) {
            if (pendingStartMs != this.pendingStartMs) {
                this.pending.clear();
                this.pendingStartMs = pendingStartMs;
            }
            this.pending.add(tripPoint);
            z = this.pending.size() >= 30;
        }
        if (z) {
            flushPoints();
        }
    }

    @Override // com.havalh6.viewer.TripEngine.Listener
    public void onStop(long j, TripStop tripStop) {
        TripStore tripStore = this.store;
        if (tripStore == null) {
            return;
        }
        tripStore.insertStop(j, tripStop);
        Log.w("H6Trip", String.format(Locale.US, "trip stop %d: %s %.0f%% -> %.0f%% (%.1f)", Long.valueOf(j), tripStop.kind, Double.valueOf(tripStop.before), Double.valueOf(tripStop.after), Double.valueOf(tripStop.amount)));
    }

    @Override // com.havalh6.viewer.TripEngine.Listener
    public void onTripClosed(TripSummary tripSummary) {
        TripStore tripStore = this.store;
        if (tripStore == null) {
            return;
        }
        flushPoints();
        tripStore.insertTrip(tripSummary);
        tripStore.saveRoute(tripSummary.startMs, 300);
        tripStore.clearCheckpoint();
        TripMapWorker tripMapWorker = this.maps;
        if (tripMapWorker != null) {
            tripMapWorker.sweepSoon(5000L);
        }
        Log.w("H6Trip", String.format(Locale.US, "trip closed %d: %.1f km (int %.2f, odo jumps %d) fuel %.2f L out %.2f kWh in %.2f kWh ev %.0f%% raw=%s", Long.valueOf(tripSummary.startMs), Double.valueOf(tripSummary.km), Double.valueOf(tripSummary.kmIntegrated), Integer.valueOf(tripSummary.odoJumps), Double.valueOf(tripSummary.fuelL), Double.valueOf(tripSummary.kwhOut), Double.valueOf(tripSummary.kwhIn), Double.valueOf(tripSummary.evShare() * 100.0d), tripSummary.rawLast));
    }

    @Override // com.havalh6.viewer.TripEngine.Listener
    public void onTripDiscarded(long j) {
        TripStore tripStore = this.store;
        synchronized (this.pending) {
            this.pending.clear();
        }
        if (tripStore == null) {
            return;
        }
        tripStore.deletePoints(j);
        tripStore.clearCheckpoint();
        Log.w("H6Trip", "trip discarded " + j);
    }

    @Override // com.havalh6.viewer.TripEngine.Listener
    public void onTripOpened(long pendingStartMs) {
        synchronized (this.pending) {
            this.pending.clear();
            this.pendingStartMs = pendingStartMs;
        }
        Log.w("H6Trip", "trip opened " + pendingStartMs);
    }

    void persistSoon() {
        this.worker.post(() -> {
            persist(now());
        });
    }

    String stopTotalsJson(long j) {
        TripStore tripStore = this.store;
        return tripStore == null ? "{}" : tripStore.stopTotalsJson(j);
    }

    String totalsJson() {
        TripStore tripStore = this.store;
        return tripStore == null ? "{}" : tripStore.totalsJson();
    }

    String tripJson(long j, int i) {
        TripStore tripStore = this.store;
        return tripStore == null ? "null" : tripStore.tripJson(j, i);
    }

    String tripMapDataUrl(long j) {
        TripMapWorker tripMapWorker = this.maps;
        return tripMapWorker == null ? "" : tripMapWorker.dataUrl(j);
    }

    String tripsJson(int i, int i2) {
        TripStore tripStore = this.store;
        return tripStore == null ? "[]" : tripStore.tripsJson(i, i2);
    }
}
