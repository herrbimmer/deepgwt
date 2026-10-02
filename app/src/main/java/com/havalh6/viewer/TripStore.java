package com.havalh6.viewer;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.lang.reflect.Array;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class TripStore extends SQLiteOpenHelper {
    private static final String DB_NAME = "trips.db";
    private static final int DB_VERSION = 4;
    static final int DETAIL_TRIPS = 30;
    static final int ROUTE_POINTS = 300;

    static final class Parked {
        final long endMs;
        final double fuelPct;
        final double lat;
        final double lon;
        final double soc;

        Parked(double fuelPct, double soc, double lat, double lon, long endMs) {
            this.fuelPct = fuelPct;
            this.soc = soc;
            this.lat = lat;
            this.lon = lon;
            this.endMs = endMs;
        }
    }

    static final class PendingMap {
        final double endLat;
        final double endLon;
        final boolean needMap;
        final boolean needPlaces;
        final double startLat;
        final double startLon;
        final long startMs;

        PendingMap(long startMs, boolean needMap, boolean needPlaces, double startLat, double startLon, double endLat, double endLon) {
            this.startMs = startMs;
            this.needMap = needMap;
            this.needPlaces = needPlaces;
            this.startLat = startLat;
            this.startLon = startLon;
            this.endLat = endLat;
            this.endLon = endLon;
        }
    }

    TripStore(Context context) {
        super(context.getApplicationContext(), "trips.db", (SQLiteDatabase.CursorFactory) null, 4);
    }

    private static String camel(String str) {
        StringBuilder sb = new StringBuilder();
        boolean z = false;
        for (char c : str.toCharArray()) {
            if (c == '_') {
                z = true;
            } else {
                sb.append(z ? Character.toUpperCase(c) : c);
                z = false;
            }
        }
        return sb.toString();
    }

    private static void createStops(SQLiteDatabase sQLiteDatabase) {
        sQLiteDatabase.execSQL("CREATE TABLE trip_stops (id INTEGER PRIMARY KEY AUTOINCREMENT,start_ms INTEGER NOT NULL, t INTEGER NOT NULL, kind TEXT NOT NULL,lat REAL, lon REAL, level_before REAL, level_after REAL, amount REAL)");
        sQLiteDatabase.execSQL("CREATE INDEX trip_stops_trip ON trip_stops(start_ms)");
    }

    static String dayKey(long j) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(j));
    }

    private static Object num(double d) {
        return (Double.isNaN(d) || Double.isInfinite(d)) ? JSONObject.NULL : Double.valueOf(d);
    }

    private static JSONObject periodRow(Cursor cursor) throws JSONException {
        JSONObject jSONObject = new JSONObject();
        jSONObject.put("period", cursor.getString(0));
        jSONObject.put("trips", cursor.getLong(1));
        double d = cursor.getDouble(2);
        double d2 = cursor.getDouble(4);
        double d3 = cursor.getDouble(5) - cursor.getDouble(6);
        jSONObject.put("km", d);
        jSONObject.put("driveMs", cursor.getLong(3));
        jSONObject.put("fuelL", d2);
        jSONObject.put("kwhOut", cursor.getDouble(5));
        jSONObject.put("kwhIn", cursor.getDouble(6));
        jSONObject.put("evKm", cursor.getDouble(7));
        jSONObject.put("kmPerL", d2 >= 0.05d ? Double.valueOf(d / d2) : JSONObject.NULL);
        jSONObject.put("kwhPer100Km", d >= 0.1d ? Double.valueOf((100.0d * d3) / d) : JSONObject.NULL);
        return jSONObject;
    }

    private static JSONArray pointRow(Cursor cursor) {
        JSONArray jSONArray = new JSONArray();
        jSONArray.put(cursor.getLong(0));
        for (int i = 1; i <= 5; i++) {
            jSONArray.put(real(cursor, i));
        }
        jSONArray.put(cursor.getInt(6));
        jSONArray.put(real(cursor, 7));
        jSONArray.put(cursor.getInt(8));
        jSONArray.put(real(cursor, 9));
        return jSONArray;
    }

    private static void putReal(ContentValues contentValues, String str, double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            contentValues.putNull(str);
        } else {
            contentValues.put(str, Double.valueOf(d));
        }
    }

    private static String rawJson(TripSummary tripSummary) {
        try {
            JSONObject jSONObject = new JSONObject();
            JSONObject jSONObject2 = new JSONObject();
            for (Map.Entry<String, String> entry : tripSummary.rawFirst.entrySet()) {
                jSONObject2.put(entry.getKey(), entry.getValue());
            }
            JSONObject jSONObject3 = new JSONObject();
            for (Map.Entry<String, String> entry2 : tripSummary.rawLast.entrySet()) {
                jSONObject3.put(entry2.getKey(), entry2.getValue());
            }
            jSONObject.put("first", jSONObject2);
            jSONObject.put("last", jSONObject3);
            return jSONObject.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    private static Object real(Cursor cursor, int i) {
        return cursor.isNull(i) ? JSONObject.NULL : Double.valueOf(cursor.getDouble(i));
    }

    private static double round(double d, int i) {
        double dPow = Math.pow(10.0d, i);
        return Math.round(d * dPow) / dPow;
    }

    private JSONArray stopsArray(long j) throws JSONException {
        JSONArray jSONArray = new JSONArray();
        Cursor cursorRawQuery = getReadableDatabase().rawQuery("SELECT kind, t, lat, lon, level_before, level_after, amount FROM trip_stops WHERE start_ms = ? ORDER BY t", new String[]{String.valueOf(j)});
        while (cursorRawQuery.moveToNext()) {
            try {
                JSONObject jSONObject = new JSONObject();
                jSONObject.put("kind", cursorRawQuery.getString(0));
                jSONObject.put("t", cursorRawQuery.getLong(1));
                jSONObject.put("lat", real(cursorRawQuery, 2));
                jSONObject.put("lon", real(cursorRawQuery, 3));
                jSONObject.put("before", real(cursorRawQuery, 4));
                jSONObject.put("after", real(cursorRawQuery, 5));
                jSONObject.put("amount", real(cursorRawQuery, 6));
                jSONArray.put(jSONObject);
            } catch (Throwable th) {
                if (cursorRawQuery != null) {
                    try {
                        cursorRawQuery.close();
                    } catch (Throwable th2) {
                        th.addSuppressed(th2);
                    }
                }
                throw th;
            }
        }
        if (cursorRawQuery != null) {
            cursorRawQuery.close();
        }
        return jSONArray;
    }

    static String summaryJson(TripSummary tripSummary) {
        try {
            JSONObject jSONObject = new JSONObject();
            jSONObject.put("open", tripSummary.open);
            jSONObject.put("startMs", tripSummary.startMs);
            jSONObject.put("endMs", tripSummary.endMs);
            jSONObject.put("km", num(tripSummary.km));
            jSONObject.put("kmIntegrated", num(tripSummary.kmIntegrated));
            jSONObject.put("driveMs", tripSummary.driveMs);
            jSONObject.put("idleMs", tripSummary.idleMs);
            jSONObject.put("evKm", num(tripSummary.evKm));
            jSONObject.put("evMs", tripSummary.evMs);
            jSONObject.put("kwhOut", num(tripSummary.kwhOut));
            jSONObject.put("kwhIn", num(tripSummary.kwhIn));
            jSONObject.put("fuelL", num(tripSummary.fuelL));
            jSONObject.put("maxKmh", num(tripSummary.maxKmh));
            jSONObject.put("stops", tripSummary.stops);
            jSONObject.put("harshAccel", tripSummary.harshAccel);
            jSONObject.put("harshBrake", tripSummary.harshBrake);
            jSONObject.put("climbM", num(tripSummary.climbM));
            int i = 1;
            jSONObject.put("guided", tripSummary.guided ? 1 : 0);
            if (!tripSummary.arrived) {
                i = 0;
            }
            jSONObject.put("arrived", i);
            jSONObject.put("plannedKm", num(tripSummary.plannedKm));
            jSONObject.put("plannedS", tripSummary.plannedS);
            jSONObject.put("guidedKm", num(tripSummary.guidedKm));
            jSONObject.put("guidedS", tripSummary.guidedS);
            jSONObject.put("kmPerL", num(tripSummary.kmPerLitre()));
            jSONObject.put("kwhPer100Km", num(tripSummary.kwhPer100Km()));
            jSONObject.put("evShare", num(tripSummary.evShare()));
            jSONObject.put("avgKmh", num(tripSummary.avgMovingKmh()));
            jSONObject.put("fuelPctStart", num(tripSummary.fuelPctStart));
            jSONObject.put("fuelPctEnd", num(tripSummary.fuelPctEnd));
            jSONObject.put("socStart", num(tripSummary.socStart));
            jSONObject.put("socEnd", num(tripSummary.socEnd));
            return jSONObject.toString();
        } catch (JSONException e) {
            return "";
        }
    }

    private static JSONObject tripRow(Cursor cursor) throws JSONException {
        JSONObject jSONObject = new JSONObject();
        jSONObject.put("startMs", cursor.getLong(cursor.getColumnIndexOrThrow("start_ms")));
        jSONObject.put("endMs", cursor.getLong(cursor.getColumnIndexOrThrow("end_ms")));
        jSONObject.put("day", cursor.getString(cursor.getColumnIndexOrThrow("day")));
        String[] strArr = {"km", "km_integrated", "odo_start", "odo_end", "ev_km", "kwh_out", "kwh_in", "fuel_l", "max_kmh", "climb_m", "start_lat", "start_lon", "end_lat", "end_lon", "fuel_pct_start", "fuel_pct_end", "planned_km", "guided_km", "soc_start", "soc_end"};
        for (int i = 0; i < 20; i++) {
            String str = strArr[i];
            jSONObject.put(camel(str), real(cursor, cursor.getColumnIndexOrThrow(str)));
        }
        String[] strArr2 = {"drive_ms", "idle_ms", "ev_ms", "stops", "harsh_accel", "harsh_brake", "detail_kept", "guided", "arrived", "planned_s", "guided_s"};
        for (int i2 = 0; i2 < 11; i2++) {
            String str2 = strArr2[i2];
            jSONObject.put(camel(str2), cursor.getLong(cursor.getColumnIndexOrThrow(str2)));
        }
        String[] strArr3 = {"start_place", "end_place", "snapshot_path", "snapshot_view"};
        for (int i3 = 0; i3 < 4; i3++) {
            String str3 = strArr3[i3];
            String string = cursor.getString(cursor.getColumnIndexOrThrow(str3));
            jSONObject.put(camel(str3), string == null ? JSONObject.NULL : string);
        }
        double d = cursor.getDouble(cursor.getColumnIndexOrThrow("km"));
        double d2 = cursor.getDouble(cursor.getColumnIndexOrThrow("fuel_l"));
        double d3 = cursor.getDouble(cursor.getColumnIndexOrThrow("kwh_out")) - cursor.getDouble(cursor.getColumnIndexOrThrow("kwh_in"));
        jSONObject.put("kmPerL", d2 >= 0.05d ? Double.valueOf(d / d2) : JSONObject.NULL);
        jSONObject.put("kwhPer100Km", d >= 0.1d ? Double.valueOf((100.0d * d3) / d) : JSONObject.NULL);
        jSONObject.put("evShare", d >= 0.1d ? Double.valueOf(Math.min(1.0d, cursor.getDouble(cursor.getColumnIndexOrThrow("ev_km")) / d)) : JSONObject.NULL);
        return jSONObject;
    }

    private static double zero(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return 0.0d;
        }
        return d;
    }

    void appendPoints(long j, List<TripPoint> list) {
        if (list.isEmpty()) {
            return;
        }
        SQLiteDatabase writableDatabase = getWritableDatabase();
        writableDatabase.beginTransaction();
        try {
            ContentValues contentValues = new ContentValues();
            for (TripPoint tripPoint : list) {
                contentValues.clear();
                contentValues.put("start_ms", Long.valueOf(j));
                contentValues.put("t", Long.valueOf(tripPoint.t));
                putReal(contentValues, "lat", tripPoint.lat);
                putReal(contentValues, "lon", tripPoint.lon);
                putReal(contentValues, "alt", tripPoint.alt);
                putReal(contentValues, "kmh", tripPoint.kmh);
                putReal(contentValues, "kw", tripPoint.kw);
                contentValues.put("fuel_mode", Integer.valueOf(tripPoint.fuelMode));
                putReal(contentValues, "fuel_rate", tripPoint.fuelRate);
                contentValues.put("ice", Integer.valueOf(tripPoint.ice));
                putReal(contentValues, "km", tripPoint.km);
                writableDatabase.insert("trip_points", null, contentValues);
            }
            writableDatabase.setTransactionSuccessful();
        } finally {
            writableDatabase.endTransaction();
        }
    }

    void bumpMapAttempts(long j) {
        getWritableDatabase().execSQL("UPDATE trips SET map_attempts = map_attempts + 1 WHERE start_ms = ?", new Object[]{Long.valueOf(j)});
    }

    void clearCheckpoint() {
        getWritableDatabase().delete("open_trip", null, null);
    }

    String daysJson(String str, String str2) {
        JSONArray jSONArray = new JSONArray();
        try {
            Cursor cursorRawQuery = getReadableDatabase().rawQuery("SELECT day, trips, km, drive_ms, fuel_l, kwh_out, kwh_in, ev_km FROM days WHERE day >= ? AND day <= ? ORDER BY day", new String[]{str == null ? "" : str, str2 == null ? "9999" : str2});
            while (cursorRawQuery.moveToNext()) {
                try {
                    jSONArray.put(periodRow(cursorRawQuery));
                } catch (Throwable th) {
                    if (cursorRawQuery != null) {
                        try {
                            cursorRawQuery.close();
                        } catch (Throwable th2) {
                            th.addSuppressed(th2);
                        }
                    }
                    throw th;
                }
            }
            if (cursorRawQuery != null) {
                cursorRawQuery.close();
            }
        } catch (JSONException e) {
        }
        return jSONArray.toString();
    }

    void deleteOrphanPoints(long j) {
        getWritableDatabase().execSQL("DELETE FROM trip_points WHERE start_ms NOT IN (SELECT start_ms FROM trips) AND start_ms != ?", new Object[]{Long.valueOf(j)});
    }

    void deletePoints(long j) {
        getWritableDatabase().delete("trip_points", "start_ms = ?", new String[]{String.valueOf(j)});
    }

    double[][] fixes(long j, int i) {
        ArrayList arrayList = new ArrayList();
        Cursor cursorRawQuery = getReadableDatabase().rawQuery("SELECT lat, lon FROM trip_points WHERE start_ms = ? AND lat IS NOT NULL AND lon IS NOT NULL ORDER BY t", new String[]{String.valueOf(j)});
        try {
            int iMax = Math.max(1, (int) Math.ceil(((double) cursorRawQuery.getCount()) / ((double) Math.max(1, i))));
            int i2 = 0;
            while (cursorRawQuery.moveToNext()) {
                int i3 = i2 + 1;
                if (i2 % iMax == 0 || cursorRawQuery.isLast()) {
                    arrayList.add(new double[]{cursorRawQuery.getDouble(0), cursorRawQuery.getDouble(1)});
                }
                i2 = i3;
            }
            if (cursorRawQuery != null) {
                cursorRawQuery.close();
            }
            double[][] dArr = (double[][]) Array.newInstance((Class<?>) Double.TYPE, 2, arrayList.size());
            for (int i4 = 0; i4 < arrayList.size(); i4++) {
                dArr[0][i4] = ((double[]) arrayList.get(i4))[0];
                dArr[1][i4] = ((double[]) arrayList.get(i4))[1];
            }
            return dArr;
        } catch (Throwable th) {
            if (cursorRawQuery != null) {
                try {
                    cursorRawQuery.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
            }
            throw th;
        }
    }

    void insertStop(long j, TripStop tripStop) {
        ContentValues contentValues = new ContentValues();
        contentValues.put("start_ms", Long.valueOf(j));
        contentValues.put("t", Long.valueOf(tripStop.t));
        contentValues.put("kind", tripStop.kind);
        putReal(contentValues, "lat", tripStop.lat);
        putReal(contentValues, "lon", tripStop.lon);
        putReal(contentValues, "level_before", tripStop.before);
        putReal(contentValues, "level_after", tripStop.after);
        putReal(contentValues, "amount", tripStop.amount);
        getWritableDatabase().insert("trip_stops", null, contentValues);
    }

    void insertTrip(TripSummary tripSummary) {
        String strDayKey = dayKey(tripSummary.startMs);
        SQLiteDatabase writableDatabase = getWritableDatabase();
        writableDatabase.beginTransaction();
        try {
            ContentValues contentValues = new ContentValues();
            contentValues.put("start_ms", Long.valueOf(tripSummary.startMs));
            contentValues.put("end_ms", Long.valueOf(tripSummary.endMs));
            contentValues.put("day", strDayKey);
            putReal(contentValues, "km", tripSummary.km);
            putReal(contentValues, "km_integrated", tripSummary.kmIntegrated);
            putReal(contentValues, "odo_start", tripSummary.odoStart);
            putReal(contentValues, "odo_end", tripSummary.odoEnd);
            contentValues.put("drive_ms", Long.valueOf(tripSummary.driveMs));
            contentValues.put("idle_ms", Long.valueOf(tripSummary.idleMs));
            putReal(contentValues, "ev_km", tripSummary.evKm);
            contentValues.put("ev_ms", Long.valueOf(tripSummary.evMs));
            putReal(contentValues, "kwh_out", tripSummary.kwhOut);
            putReal(contentValues, "kwh_in", tripSummary.kwhIn);
            putReal(contentValues, "fuel_l", tripSummary.fuelL);
            putReal(contentValues, "max_kmh", tripSummary.maxKmh);
            contentValues.put("stops", Integer.valueOf(tripSummary.stops));
            contentValues.put("harsh_accel", Integer.valueOf(tripSummary.harshAccel));
            contentValues.put("harsh_brake", Integer.valueOf(tripSummary.harshBrake));
            putReal(contentValues, "climb_m", tripSummary.climbM);
            putReal(contentValues, "start_lat", tripSummary.startLat);
            putReal(contentValues, "start_lon", tripSummary.startLon);
            putReal(contentValues, "end_lat", tripSummary.endLat);
            putReal(contentValues, "end_lon", tripSummary.endLon);
            putReal(contentValues, "fuel_pct_start", tripSummary.fuelPctStart);
            putReal(contentValues, "fuel_pct_end", tripSummary.fuelPctEnd);
            contentValues.put("raw_json", rawJson(tripSummary));
            putReal(contentValues, "soc_start", tripSummary.socStart);
            putReal(contentValues, "soc_end", tripSummary.socEnd);
            contentValues.put("guided", Integer.valueOf(tripSummary.guided ? 1 : 0));
            contentValues.put("arrived", Integer.valueOf(tripSummary.arrived ? 1 : 0));
            if (tripSummary.guided) {
                putReal(contentValues, "planned_km", tripSummary.plannedKm);
                contentValues.put("planned_s", Long.valueOf(tripSummary.plannedS));
                putReal(contentValues, "guided_km", tripSummary.guidedKm);
                contentValues.put("guided_s", Long.valueOf(tripSummary.guidedS));
            }
            writableDatabase.insertWithOnConflict("trips", null, contentValues, 5);
            writableDatabase.execSQL("INSERT OR IGNORE INTO days(day) VALUES (?)", new Object[]{strDayKey});
            writableDatabase.execSQL("UPDATE days SET trips = trips + 1, km = km + ?, drive_ms = drive_ms + ?, fuel_l = fuel_l + ?, kwh_out = kwh_out + ?, kwh_in = kwh_in + ?, ev_km = ev_km + ? WHERE day = ?", new Object[]{Double.valueOf(zero(tripSummary.km)), Long.valueOf(tripSummary.driveMs), Double.valueOf(zero(tripSummary.fuelL)), Double.valueOf(zero(tripSummary.kwhOut)), Double.valueOf(zero(tripSummary.kwhIn)), Double.valueOf(zero(tripSummary.evKm)), strDayKey});
            writableDatabase.execSQL("DELETE FROM trip_points WHERE start_ms IN (SELECT start_ms FROM trips) AND start_ms NOT IN (SELECT start_ms FROM trips ORDER BY start_ms DESC LIMIT 30)");
            writableDatabase.execSQL("UPDATE trips SET detail_kept = 0 WHERE detail_kept = 1 AND start_ms NOT IN (SELECT start_ms FROM trips ORDER BY start_ms DESC LIMIT 30)");
            writableDatabase.setTransactionSuccessful();
        } finally {
            writableDatabase.endTransaction();
        }
    }

    Parked lastParked() {
        Cursor cursorRawQuery = getReadableDatabase().rawQuery("SELECT fuel_pct_end, soc_end, end_lat, end_lon, end_ms FROM trips ORDER BY start_ms DESC LIMIT 1", null);
        try {
            if (!cursorRawQuery.moveToFirst()) {
                if (cursorRawQuery != null) {
                    cursorRawQuery.close();
                }
                return null;
            }
            double d = Double.NaN;
            double d2 = cursorRawQuery.isNull(0) ? Double.NaN : cursorRawQuery.getDouble(0);
            double d3 = cursorRawQuery.isNull(1) ? Double.NaN : cursorRawQuery.getDouble(1);
            double d4 = cursorRawQuery.isNull(2) ? Double.NaN : cursorRawQuery.getDouble(2);
            if (!cursorRawQuery.isNull(3)) {
                d = cursorRawQuery.getDouble(3);
            }
            Parked parked = new Parked(d2, d3, d4, d, cursorRawQuery.getLong(4));
            if (cursorRawQuery != null) {
                cursorRawQuery.close();
            }
            return parked;
        } catch (Throwable th) {
            if (cursorRawQuery == null) {
                throw th;
            }
            try {
                cursorRawQuery.close();
                throw th;
            } catch (Throwable th2) {
                th.addSuppressed(th2);
                throw th;
            }
        }
    }

    String livePointsJson(long j, long j2, int i, List<TripPoint> list) {
        try {
            JSONArray jSONArray = new JSONArray();
            long j3 = j2;
            Cursor cursorRawQuery = getReadableDatabase().rawQuery("SELECT t, lat, lon, alt, kmh, kw, fuel_mode, fuel_rate, ice, km FROM trip_points WHERE start_ms = ? AND t > ? ORDER BY t", new String[]{String.valueOf(j), String.valueOf(j2)});
            try {
                try {
                    try {
                        int iMax = Math.max(1, (int) Math.ceil(((double) cursorRawQuery.getCount()) / ((double) Math.max(1, i))));
                        int i2 = 0;
                        while (cursorRawQuery.moveToNext()) {
                            i2++;
                            if (i2 % iMax == 0 || cursorRawQuery.isLast()) {
                                jSONArray.put(pointRow(cursorRawQuery));
                                j3 = cursorRawQuery.getLong(0);
                            }
                        }
                        if (cursorRawQuery != null) {
                            cursorRawQuery.close();
                        }
                        for (TripPoint tripPoint : list) {
                            if (tripPoint.t > j3) {
                                JSONArray jSONArray2 = new JSONArray();
                                jSONArray2.put(tripPoint.t);
                                jSONArray2.put(num(tripPoint.lat));
                                jSONArray2.put(num(tripPoint.lon));
                                jSONArray2.put(num(tripPoint.alt));
                                jSONArray2.put(num(tripPoint.kmh));
                                jSONArray2.put(num(tripPoint.kw));
                                jSONArray2.put(tripPoint.fuelMode);
                                jSONArray2.put(num(tripPoint.fuelRate));
                                jSONArray2.put(tripPoint.ice);
                                jSONArray2.put(num(tripPoint.km));
                                jSONArray.put(jSONArray2);
                                j3 = tripPoint.t;
                            }
                        }
                        JSONObject jSONObject = new JSONObject();
                        jSONObject.put("points", jSONArray);
                        jSONObject.put("stops", stopsArray(j));
                        return jSONObject.toString();
                    } catch (Throwable th) {
                        Throwable th2 = th;
                        if (cursorRawQuery == null) {
                            throw th2;
                        }
                        try {
                            cursorRawQuery.close();
                            throw th2;
                        } catch (Throwable th3) {
                            th2.addSuppressed(th3);
                            throw th2;
                        }
                    }
                } catch (JSONException e) {
                    return "null";
                }
            } catch (Throwable th4) {
                return "null";
            }
        } catch (Throwable e2) {
            return "null";
        }
    }

    String loadCheckpoint() {
        Cursor cursorRawQuery = getReadableDatabase().rawQuery("SELECT state FROM open_trip WHERE id = 1", null);
        try {
            String string = cursorRawQuery.moveToFirst() ? cursorRawQuery.getString(0) : null;
            if (cursorRawQuery != null) {
                cursorRawQuery.close();
            }
            return string;
        } catch (Throwable th) {
            if (cursorRawQuery != null) {
                try {
                    cursorRawQuery.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
            }
            throw th;
        }
    }

    String monthsJson() {
        JSONArray jSONArray = new JSONArray();
        try {
            Cursor cursorRawQuery = getReadableDatabase().rawQuery("SELECT substr(day, 1, 7) AS month, SUM(trips), SUM(km), SUM(drive_ms), SUM(fuel_l), SUM(kwh_out), SUM(kwh_in), SUM(ev_km) FROM days GROUP BY month ORDER BY month", null);
            while (cursorRawQuery.moveToNext()) {
                try {
                    jSONArray.put(periodRow(cursorRawQuery));
                } catch (Throwable th) {
                    if (cursorRawQuery != null) {
                        try {
                            cursorRawQuery.close();
                        } catch (Throwable th2) {
                            th.addSuppressed(th2);
                        }
                    }
                    throw th;
                }
            }
            if (cursorRawQuery != null) {
                cursorRawQuery.close();
            }
        } catch (JSONException e) {
        }
        return jSONArray.toString();
    }

    @Override // android.database.sqlite.SQLiteOpenHelper
    public void onCreate(SQLiteDatabase sQLiteDatabase) {
        sQLiteDatabase.execSQL("CREATE TABLE trips (start_ms INTEGER PRIMARY KEY, end_ms INTEGER NOT NULL, day TEXT NOT NULL,km REAL, km_integrated REAL, odo_start REAL, odo_end REAL,drive_ms INTEGER, idle_ms INTEGER, ev_km REAL, ev_ms INTEGER,kwh_out REAL, kwh_in REAL, fuel_l REAL, max_kmh REAL,stops INTEGER, harsh_accel INTEGER, harsh_brake INTEGER, climb_m REAL,start_lat REAL, start_lon REAL, end_lat REAL, end_lon REAL,fuel_pct_start REAL, fuel_pct_end REAL,start_place TEXT, end_place TEXT, snapshot_path TEXT,raw_json TEXT, detail_kept INTEGER NOT NULL DEFAULT 1,map_attempts INTEGER NOT NULL DEFAULT 0,guided INTEGER NOT NULL DEFAULT 0, arrived INTEGER NOT NULL DEFAULT 0,planned_km REAL, planned_s INTEGER, guided_km REAL, guided_s INTEGER,route TEXT, snapshot_view TEXT, soc_start REAL, soc_end REAL)");
        sQLiteDatabase.execSQL("CREATE INDEX trips_day ON trips(day)");
        sQLiteDatabase.execSQL("CREATE TABLE trip_points (start_ms INTEGER NOT NULL, t INTEGER NOT NULL,lat REAL, lon REAL, alt REAL, kmh REAL, kw REAL,fuel_mode INTEGER, fuel_rate REAL, ice INTEGER, km REAL)");
        sQLiteDatabase.execSQL("CREATE INDEX trip_points_trip ON trip_points(start_ms, t)");
        sQLiteDatabase.execSQL("CREATE TABLE days (day TEXT PRIMARY KEY, trips INTEGER NOT NULL DEFAULT 0,km REAL NOT NULL DEFAULT 0, drive_ms INTEGER NOT NULL DEFAULT 0,fuel_l REAL NOT NULL DEFAULT 0, kwh_out REAL NOT NULL DEFAULT 0,kwh_in REAL NOT NULL DEFAULT 0, ev_km REAL NOT NULL DEFAULT 0)");
        sQLiteDatabase.execSQL("CREATE TABLE open_trip (id INTEGER PRIMARY KEY CHECK (id = 1),state TEXT NOT NULL, updated_ms INTEGER NOT NULL)");
        createStops(sQLiteDatabase);
    }

    @Override // android.database.sqlite.SQLiteOpenHelper
    public void onUpgrade(SQLiteDatabase sQLiteDatabase, int i, int i2) {
        if (i < 2) {
            sQLiteDatabase.execSQL("ALTER TABLE trips ADD COLUMN map_attempts INTEGER NOT NULL DEFAULT 0");
        }
        if (i < 3) {
            sQLiteDatabase.execSQL("ALTER TABLE trips ADD COLUMN guided INTEGER NOT NULL DEFAULT 0");
            sQLiteDatabase.execSQL("ALTER TABLE trips ADD COLUMN arrived INTEGER NOT NULL DEFAULT 0");
            sQLiteDatabase.execSQL("ALTER TABLE trips ADD COLUMN planned_km REAL");
            sQLiteDatabase.execSQL("ALTER TABLE trips ADD COLUMN planned_s INTEGER");
            sQLiteDatabase.execSQL("ALTER TABLE trips ADD COLUMN guided_km REAL");
            sQLiteDatabase.execSQL("ALTER TABLE trips ADD COLUMN guided_s INTEGER");
        }
        if (i < 4) {
            sQLiteDatabase.execSQL("ALTER TABLE trips ADD COLUMN route TEXT");
            sQLiteDatabase.execSQL("ALTER TABLE trips ADD COLUMN snapshot_view TEXT");
            sQLiteDatabase.execSQL("ALTER TABLE trips ADD COLUMN soc_start REAL");
            sQLiteDatabase.execSQL("ALTER TABLE trips ADD COLUMN soc_end REAL");
            createStops(sQLiteDatabase);
            sQLiteDatabase.execSQL("UPDATE trips SET snapshot_path = NULL, map_attempts = 0 WHERE snapshot_path IS NOT NULL AND snapshot_path != ''");
        }
    }

    List<PendingMap> pendingMaps(int i, int i2) {
        ArrayList arrayList = new ArrayList();
        Cursor cursorRawQuery = getReadableDatabase().rawQuery("SELECT start_ms, snapshot_path IS NULL, start_place IS NULL, start_lat, start_lon, end_lat, end_lon FROM trips WHERE (snapshot_path IS NULL OR start_place IS NULL) AND map_attempts < ? ORDER BY start_ms DESC LIMIT ?", new String[]{String.valueOf(i), String.valueOf(i2)});
        while (cursorRawQuery.moveToNext()) {
            try {
                long j = cursorRawQuery.getLong(0);
                boolean z = cursorRawQuery.getInt(1) == 1;
                boolean z2 = cursorRawQuery.getInt(2) == 1;
                double d = Double.NaN;
                double d2 = cursorRawQuery.isNull(3) ? Double.NaN : cursorRawQuery.getDouble(3);
                double d3 = cursorRawQuery.isNull(4) ? Double.NaN : cursorRawQuery.getDouble(4);
                double d4 = cursorRawQuery.isNull(5) ? Double.NaN : cursorRawQuery.getDouble(5);
                if (!cursorRawQuery.isNull(6)) {
                    d = cursorRawQuery.getDouble(6);
                }
                arrayList.add(new PendingMap(j, z, z2, d2, d3, d4, d));
            } catch (Throwable th) {
                if (cursorRawQuery == null) {
                    throw th;
                }
                try {
                    cursorRawQuery.close();
                    throw th;
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                    throw th;
                }
            }
        }
        if (cursorRawQuery != null) {
            cursorRawQuery.close();
        }
        return arrayList;
    }

    List<Long> pendingRoutes(int i) {
        ArrayList arrayList = new ArrayList();
        Cursor cursorRawQuery = getReadableDatabase().rawQuery("SELECT start_ms FROM trips WHERE route IS NULL AND detail_kept = 1 ORDER BY start_ms DESC LIMIT ?", new String[]{String.valueOf(i)});
        while (cursorRawQuery.moveToNext()) {
            try {
                arrayList.add(Long.valueOf(cursorRawQuery.getLong(0)));
            } catch (Throwable th) {
                if (cursorRawQuery != null) {
                    try {
                        cursorRawQuery.close();
                    } catch (Throwable th2) {
                        th.addSuppressed(th2);
                    }
                }
                throw th;
            }
        }
        if (cursorRawQuery != null) {
            cursorRawQuery.close();
        }
        return arrayList;
    }

    void saveCheckpoint(String str, long j) {
        ContentValues contentValues = new ContentValues();
        contentValues.put("id", (Integer) 1);
        contentValues.put("state", str);
        contentValues.put("updated_ms", Long.valueOf(j));
        getWritableDatabase().insertWithOnConflict("open_trip", null, contentValues, 5);
    }

    void saveRoute(long j, int i) {
        JSONArray jSONArray = new JSONArray();
        try {
            Cursor cursorRawQuery = getReadableDatabase().rawQuery("SELECT lat, lon, kmh, kw, fuel_mode, fuel_rate FROM trip_points WHERE start_ms = ? AND lat IS NOT NULL AND lon IS NOT NULL ORDER BY t", new String[]{String.valueOf(j)});
            try {
                int iMax = Math.max(1, (int) Math.ceil(((double) cursorRawQuery.getCount()) / ((double) Math.max(1, i))));
                int i2 = 0;
                while (cursorRawQuery.moveToNext()) {
                    int i3 = i2 + 1;
                    if (i2 % iMax == 0 || cursorRawQuery.isLast()) {
                        JSONArray jSONArray2 = new JSONArray();
                        jSONArray2.put(round(cursorRawQuery.getDouble(0), 5));
                        jSONArray2.put(round(cursorRawQuery.getDouble(1), 5));
                        jSONArray2.put(cursorRawQuery.isNull(2) ? JSONObject.NULL : Long.valueOf(Math.round(cursorRawQuery.getDouble(2))));
                        jSONArray2.put(cursorRawQuery.isNull(3) ? JSONObject.NULL : Double.valueOf(round(cursorRawQuery.getDouble(3), 1)));
                        jSONArray2.put(cursorRawQuery.getInt(4));
                        jSONArray2.put(cursorRawQuery.isNull(5) ? JSONObject.NULL : Double.valueOf(round(cursorRawQuery.getDouble(5), 1)));
                        jSONArray.put(jSONArray2);
                    }
                    i2 = i3;
                }
                if (cursorRawQuery != null) {
                    cursorRawQuery.close();
                }
                ContentValues contentValues = new ContentValues();
                contentValues.put("route", jSONArray.toString());
                getWritableDatabase().update("trips", contentValues, "start_ms = ?", new String[]{String.valueOf(j)});
            } catch (Throwable th) {
                if (cursorRawQuery != null) {
                    try {
                        cursorRawQuery.close();
                    } catch (Throwable th2) {
                        th.addSuppressed(th2);
                    }
                }
                throw th;
            }
        } catch (JSONException e) {
        }
    }

    void setTripMap(long j, String str, String str2) {
        ContentValues contentValues = new ContentValues();
        contentValues.put("snapshot_path", str);
        if (str2 == null) {
            contentValues.putNull("snapshot_view");
        } else {
            contentValues.put("snapshot_view", str2);
        }
        getWritableDatabase().update("trips", contentValues, "start_ms = ?", new String[]{String.valueOf(j)});
    }

    void setTripPlaces(long j, String str, String str2) {
        ContentValues contentValues = new ContentValues();
        contentValues.put("start_place", str);
        contentValues.put("end_place", str2);
        getWritableDatabase().update("trips", contentValues, "start_ms = ?", new String[]{String.valueOf(j)});
    }

    String stopTotalsJson(long j) {
        try {
            JSONObject jSONObject = new JSONObject();
            jSONObject.put("refuels", 0);
            jSONObject.put("refuelPct", 0);
            jSONObject.put("charges", 0);
            jSONObject.put("chargePct", 0);
            Cursor cursorRawQuery = getReadableDatabase().rawQuery("SELECT kind, COUNT(*), SUM(level_after - level_before) FROM trip_stops WHERE t >= ? GROUP BY kind", new String[]{String.valueOf(j)});
            while (cursorRawQuery.moveToNext()) {
                try {
                    if ("refuel".equals(cursorRawQuery.getString(0))) {
                        jSONObject.put("refuels", cursorRawQuery.getLong(1));
                        jSONObject.put("refuelPct", cursorRawQuery.getDouble(2));
                    } else if ("charge".equals(cursorRawQuery.getString(0))) {
                        jSONObject.put("charges", cursorRawQuery.getLong(1));
                        jSONObject.put("chargePct", cursorRawQuery.getDouble(2));
                    }
                } catch (Throwable th) {
                    if (cursorRawQuery != null) {
                        try {
                            cursorRawQuery.close();
                        } catch (Throwable th2) {
                            th.addSuppressed(th2);
                        }
                    }
                    throw th;
                }
            }
            if (cursorRawQuery != null) {
                cursorRawQuery.close();
            }
            return jSONObject.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    String totalsJson() {
        try {
            JSONObject jSONObject = new JSONObject();
            Cursor cursorRawQuery = getReadableDatabase().rawQuery("SELECT SUM(trips), SUM(km), SUM(drive_ms), SUM(fuel_l), SUM(kwh_out), SUM(kwh_in), SUM(ev_km), MIN(day) FROM days", null);
            try {
                if (cursorRawQuery.moveToFirst()) {
                    jSONObject.put("trips", cursorRawQuery.getLong(0));
                    jSONObject.put("km", cursorRawQuery.getDouble(1));
                    jSONObject.put("driveMs", cursorRawQuery.getLong(2));
                    jSONObject.put("fuelL", cursorRawQuery.getDouble(3));
                    jSONObject.put("kwhOut", cursorRawQuery.getDouble(4));
                    jSONObject.put("kwhIn", cursorRawQuery.getDouble(5));
                    jSONObject.put("evKm", cursorRawQuery.getDouble(6));
                    jSONObject.put("firstDay", cursorRawQuery.isNull(7) ? JSONObject.NULL : cursorRawQuery.getString(7));
                }
                if (cursorRawQuery != null) {
                    cursorRawQuery.close();
                }
                jSONObject.put("refuels", 0);
                jSONObject.put("refuelL", 0);
                jSONObject.put("charges", 0);
                Cursor cursorRawQuery2 = getReadableDatabase().rawQuery("SELECT kind, COUNT(*), SUM(amount) FROM trip_stops GROUP BY kind", null);
                while (cursorRawQuery2.moveToNext()) {
                    try {
                        if ("refuel".equals(cursorRawQuery2.getString(0))) {
                            jSONObject.put("refuels", cursorRawQuery2.getLong(1));
                            jSONObject.put("refuelL", cursorRawQuery2.getDouble(2));
                        } else if ("charge".equals(cursorRawQuery2.getString(0))) {
                            jSONObject.put("charges", cursorRawQuery2.getLong(1));
                        }
                    } catch (Throwable th) {
                        if (cursorRawQuery2 != null) {
                            try {
                                cursorRawQuery2.close();
                            } catch (Throwable th2) {
                                th.addSuppressed(th2);
                            }
                        }
                        throw th;
                    }
                }
                if (cursorRawQuery2 != null) {
                    cursorRawQuery2.close();
                }
                Cursor cursorRawQuery3 = getReadableDatabase().rawQuery("SELECT COUNT(*), SUM(kwh_out - kwh_in), SUM(soc_start - soc_end) FROM trips WHERE soc_start IS NOT NULL AND soc_end IS NOT NULL AND ABS(soc_start - soc_end) >= 10 AND (soc_start - soc_end) * (kwh_out - kwh_in) > 0", null);
                try {
                    if (cursorRawQuery3.moveToFirst() && cursorRawQuery3.getLong(0) >= 3 && cursorRawQuery3.getDouble(2) != 0.0d) {
                        jSONObject.put("capacityKwh", (cursorRawQuery3.getDouble(1) / cursorRawQuery3.getDouble(2)) * 100.0d);
                        jSONObject.put("capacityTrips", cursorRawQuery3.getLong(0));
                    }
                    if (cursorRawQuery3 != null) {
                        cursorRawQuery3.close();
                    }
                    return jSONObject.toString();
                } catch (Throwable th3) {
                    if (cursorRawQuery3 != null) {
                        try {
                            cursorRawQuery3.close();
                        } catch (Throwable th4) {
                            th3.addSuppressed(th4);
                        }
                    }
                    throw th3;
                }
            } catch (Throwable th5) {
                if (cursorRawQuery != null) {
                    try {
                        cursorRawQuery.close();
                    } catch (Throwable th6) {
                        th5.addSuppressed(th6);
                    }
                }
                throw th5;
            }
        } catch (JSONException e) {
            return "{}";
        }
    }

    String tripJson(long j, int i) {
        JSONObject jSONObjectTripRow = null;
        try {
            Cursor cursorRawQuery = getReadableDatabase().rawQuery("SELECT * FROM trips WHERE start_ms = ?", new String[]{String.valueOf(j)});
            try {
                if (cursorRawQuery.moveToFirst()) {
                    jSONObjectTripRow = tripRow(cursorRawQuery);
                    int columnIndexOrThrow = cursorRawQuery.getColumnIndexOrThrow("route");
                    if (!cursorRawQuery.isNull(columnIndexOrThrow)) {
                        jSONObjectTripRow.put("route", new JSONArray(cursorRawQuery.getString(columnIndexOrThrow)));
                    }
                }
                if (cursorRawQuery != null) {
                    cursorRawQuery.close();
                }
                if (jSONObjectTripRow == null) {
                    return "null";
                }
                JSONArray jSONArray = new JSONArray();
                Cursor cursorRawQuery2 = getReadableDatabase().rawQuery("SELECT t, lat, lon, alt, kmh, kw, fuel_mode, fuel_rate, ice, km FROM trip_points WHERE start_ms = ? ORDER BY t", new String[]{String.valueOf(j)});
                try {
                    int iMax = Math.max(1, (int) Math.ceil(((double) cursorRawQuery2.getCount()) / ((double) Math.max(1, i))));
                    int i2 = 0;
                    while (cursorRawQuery2.moveToNext()) {
                        int i3 = i2 + 1;
                        if (i2 % iMax == 0 || cursorRawQuery2.isLast()) {
                            jSONArray.put(pointRow(cursorRawQuery2));
                        }
                        i2 = i3;
                    }
                    if (cursorRawQuery2 != null) {
                        cursorRawQuery2.close();
                    }
                    jSONObjectTripRow.put("pointFields", new JSONArray("[\"t\",\"lat\",\"lon\",\"alt\",\"kmh\",\"kw\",\"fuelMode\",\"fuelRate\",\"ice\",\"km\"]"));
                    jSONObjectTripRow.put("points", jSONArray);
                    jSONObjectTripRow.put("stops", stopsArray(j));
                    return jSONObjectTripRow.toString();
                } catch (Throwable th) {
                    if (cursorRawQuery2 != null) {
                        try {
                            cursorRawQuery2.close();
                        } catch (Throwable th2) {
                            th.addSuppressed(th2);
                        }
                    }
                    throw th;
                }
            } catch (Throwable th3) {
                if (cursorRawQuery != null) {
                    try {
                        cursorRawQuery.close();
                    } catch (Throwable th4) {
                        th3.addSuppressed(th4);
                    }
                }
                throw th3;
            }
        } catch (JSONException e) {
            return "null";
        }
    }

    String tripsJson(int i, int i2) {
        JSONArray jSONArray = new JSONArray();
        try {
            Cursor cursorRawQuery = getReadableDatabase().rawQuery("SELECT * FROM trips ORDER BY start_ms DESC LIMIT ? OFFSET ?", new String[]{String.valueOf(Math.max(1, Math.min(200, i2))), String.valueOf(Math.max(0, i))});
            while (cursorRawQuery.moveToNext()) {
                try {
                    jSONArray.put(tripRow(cursorRawQuery));
                } catch (Throwable th) {
                    if (cursorRawQuery != null) {
                        try {
                            cursorRawQuery.close();
                        } catch (Throwable th2) {
                            th.addSuppressed(th2);
                        }
                    }
                    throw th;
                }
            }
            if (cursorRawQuery != null) {
                cursorRawQuery.close();
            }
        } catch (JSONException e) {
        }
        return jSONArray.toString();
    }
}
