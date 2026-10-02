package com.havalh6.viewer;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import java.util.List;
import java.util.Locale;
import org.json.JSONObject;

final class PlaceGlance {
    static final String KEY = "app.location.place";
    private static final long PERIOD_MS = 45000;
    private static final float REGEOCODE_MIN_MOVE_M = 120.0f;
    private static final String TAG = "H6Viewer";
    private final Context app;
    private Location lastFixed;
    private final Listener listener;
    private final Handler main;
    private boolean platformGeocoderDead;
    private boolean started;
    private Handler worker;
    private String lastJson = "";
    private final Runnable tick = new Runnable() {
        @Override // java.lang.Runnable
        public void run() {
            try {
                PlaceGlance.this.pollOnce();
            } catch (Throwable th) {
                Log.w("H6Viewer", "PlaceGlance poll failed", th);
            }
            if (PlaceGlance.this.worker != null) {
                PlaceGlance.this.worker.postDelayed(this, 45000L);
            }
        }
    };

    interface Listener {
        void onPlace(String str);
    }

    PlaceGlance(Context context, Handler main, Listener listener) {
        this.app = context.getApplicationContext();
        this.main = main;
        this.listener = listener;
    }

    private ReverseGeocoder.Place geocodePlatform(Location location) {
        if (!Geocoder.isPresent()) {
            this.platformGeocoderDead = true;
            return null;
        }
        try {
            List<Address> fromLocation = new Geocoder(this.app, new Locale("pt", "BR")).getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (fromLocation != null && !fromLocation.isEmpty()) {
                Address address = fromLocation.get(0);
                ReverseGeocoder.Place place = new ReverseGeocoder.Place();
                if (address.getSubLocality() != null && !address.getSubLocality().isEmpty()) {
                    place.neighborhood = address.getSubLocality();
                }
                if (address.getLocality() != null && !address.getLocality().isEmpty()) {
                    place.city = address.getLocality();
                } else if (address.getSubAdminArea() != null) {
                    place.city = address.getSubAdminArea();
                }
                if (address.getThoroughfare() != null) {
                    place.street = address.getThoroughfare();
                }
                return place;
            }
            return null;
        } catch (Exception e) {
            this.platformGeocoderDead = true;
            Log.w("H6Viewer", "PlaceGlance platform geocoder unavailable (" + e.getMessage() + ") — using HTTP reverse geocode");
            return null;
        }
    }

    private boolean hasLocationPermission() {
        return this.app.checkSelfPermission("android.permission.ACCESS_FINE_LOCATION") == 0 || this.app.checkSelfPermission("android.permission.ACCESS_COARSE_LOCATION") == 0;
    }

    private void pollOnce() {
        LocationManager locationManager;
        ReverseGeocoder.Place placeReverseGeocode;
        if (hasLocationPermission() && (locationManager = (LocationManager) this.app.getSystemService("location")) != null) {
            Location location = null;
            String[] strArr = {"gps", "network", "passive"};
            for (int i = 0; i < 3; i++) {
                try {
                    Location lastKnownLocation = locationManager.getLastKnownLocation(strArr[i]);
                    if (lastKnownLocation != null && (location == null || lastKnownLocation.getTime() > location.getTime())) {
                        location = lastKnownLocation;
                    }
                } catch (SecurityException e) {
                    return;
                } catch (Throwable th) {
                }
            }
            if (location == null) {
                return;
            }
            if ((this.lastFixed != null && !this.lastJson.isEmpty() && location.distanceTo(this.lastFixed) < 120.0f) || (placeReverseGeocode = reverseGeocode(location)) == null || placeReverseGeocode.isEmpty()) {
                return;
            }
            this.lastFixed = location;
            String provider = location.getProvider() != null ? location.getProvider() : "gps";
            try {
                JSONObject jSONObject = new JSONObject();
                jSONObject.put("neighborhood", placeReverseGeocode.neighborhood);
                jSONObject.put("city", placeReverseGeocode.city);
                jSONObject.put("street", placeReverseGeocode.street);
                jSONObject.put("provider", provider);
                String string = jSONObject.toString();
                if (string.equals(this.lastJson)) {
                    return;
                }
                this.lastJson = string;
                this.main.post(() -> {
                    this.listener.onPlace(string);
                });
            } catch (Exception e2) {
            }
        }
    }

    private ReverseGeocoder.Place reverseGeocode(Location location) {
        ReverseGeocoder.Place placeGeocodePlatform;
        return (this.platformGeocoderDead || (placeGeocodePlatform = geocodePlatform(location)) == null || placeGeocodePlatform.isEmpty()) ? ReverseGeocoder.lookup(location.getLatitude(), location.getLongitude()) : placeGeocodePlatform;
    }

    void pokeNow() {
        Handler handler = this.worker;
        if (handler == null) {
            return;
        }
        handler.post(() -> {
            try {
                pollOnce();
            } catch (Throwable th) {
                Log.w("H6Viewer", "PlaceGlance poke failed", th);
            }
        });
    }

    void start() {
        if (this.started) {
            return;
        }
        this.started = true;
        HandlerThread handlerThread = new HandlerThread("h6-place");
        handlerThread.start();
        this.worker = new Handler(handlerThread.getLooper());
        this.worker.post(this.tick);
    }

    void stop() {
        this.started = false;
        if (this.worker != null) {
            this.worker.removeCallbacksAndMessages(null);
            this.worker.getLooper().quitSafely();
            this.worker = null;
        }
    }
}
