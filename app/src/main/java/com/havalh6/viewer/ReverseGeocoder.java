package com.havalh6.viewer;

import android.os.SystemClock;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import org.json.JSONObject;

final class ReverseGeocoder {
    private static final int HTTP_TIMEOUT_MS = 8000;
    private static final String HTTP_UA = "HavalH6Viewer/1.0 (head-unit navigation glance)";
    private static final long MIN_GAP_MS = 1100;
    private static final Object RATE_LOCK = new Object();
    private static final String TAG = "H6Viewer";
    private static long lastRequestAt;

    static final class Place {
        String neighborhood = "";
        String city = "";
        String street = "";

        Place() {
        }

        boolean isEmpty() {
            return this.neighborhood.isEmpty() && this.city.isEmpty() && this.street.isEmpty();
        }

        String label() {
            if (this.neighborhood.isEmpty()) {
                return !this.city.isEmpty() ? this.city : this.street;
            }
            return this.neighborhood;
        }
    }

    private ReverseGeocoder() {
    }

    private static String first(JSONObject jSONObject, String... strArr) {
        for (String str : strArr) {
            String strTrim = jSONObject.optString(str, "").trim();
            if (!strTrim.isEmpty()) {
                return strTrim;
            }
        }
        return "";
    }

    static Place lookup(double d, double d2) {
        synchronized (RATE_LOCK) {
            long jElapsedRealtime = (lastRequestAt + 1100) - SystemClock.elapsedRealtime();
            if (jElapsedRealtime > 0) {
                try {
                    Thread.sleep(jElapsedRealtime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
            lastRequestAt = SystemClock.elapsedRealtime();
        }
        HttpURLConnection httpURLConnection = null;
        try {
            try {
                HttpURLConnection httpURLConnection2 = (HttpURLConnection) new URL(String.format(Locale.US, "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=%.6f&lon=%.6f&zoom=17&addressdetails=1&accept-language=pt-BR", Double.valueOf(d), Double.valueOf(d2))).openConnection();
                httpURLConnection2.setRequestMethod("GET");
                httpURLConnection2.setRequestProperty("User-Agent", "HavalH6Viewer/1.0 (head-unit navigation glance)");
                httpURLConnection2.setRequestProperty("Accept", "application/json");
                httpURLConnection2.setConnectTimeout(8000);
                httpURLConnection2.setReadTimeout(8000);
                int responseCode = httpURLConnection2.getResponseCode();
                if (responseCode != 200) {
                    Log.w("H6Viewer", "reverse geocode HTTP " + responseCode);
                    if (httpURLConnection2 != null) {
                        httpURLConnection2.disconnect();
                    }
                    return null;
                }
                StringBuilder sb = new StringBuilder();
                InputStream inputStream = httpURLConnection2.getInputStream();
                try {
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
                    while (true) {
                        try {
                            String line = bufferedReader.readLine();
                            if (line == null) {
                                break;
                            }
                            sb.append(line);
                        } catch (Throwable th) {
                            try {
                                bufferedReader.close();
                            } catch (Throwable th2) {
                                th.addSuppressed(th2);
                            }
                            throw th;
                        }
                    }
                    bufferedReader.close();
                    if (inputStream != null) {
                        inputStream.close();
                    }
                    Place place = parse(sb.toString());
                    if (httpURLConnection2 != null) {
                        httpURLConnection2.disconnect();
                    }
                    return place;
                } catch (Throwable th4) {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                    throw th4;
                }
            } catch (Exception e2) {
                Log.w("H6Viewer", "reverse geocode failed: " + e2.getMessage());
                if (0 != 0) {
                    httpURLConnection.disconnect();
                }
                return null;
            }
        } catch (Throwable th5) {
            if (0 != 0) {
                httpURLConnection.disconnect();
            }
            throw th5;
        }
    }

    static Place parse(String str) {
        try {
            JSONObject jSONObjectOptJSONObject = new JSONObject(str).optJSONObject("address");
            if (jSONObjectOptJSONObject == null) {
                return null;
            }
            Place place = new Place();
            place.neighborhood = first(jSONObjectOptJSONObject, "suburb", "neighbourhood", "city_district");
            place.city = first(jSONObjectOptJSONObject, "city", "town", "village", "municipality", "county");
            place.street = first(jSONObjectOptJSONObject, "road", "pedestrian", "footway");
            return place;
        } catch (Exception e) {
            Log.w("H6Viewer", "reverse geocode parse failed: " + e.getMessage());
            return null;
        }
    }
}
