package com.havalh6.viewer;

import java.util.LinkedHashMap;
import java.util.Map;

final class TripState {
    boolean arrived;
    double climbM;
    double distInt;
    double distIntAtOdo;
    long driveMs;
    double evKm;
    long evMs;
    double fuelL;
    double guideEndKm;
    long guideEndT;
    double guideStartKm;
    long guideStartT;
    boolean guided;
    int harshAccel;
    int harshBrake;
    double iceKm;
    long idleMs;
    boolean inAccel;
    boolean inBrake;
    double kwhIn;
    double kwhOut;
    long lastNavT;
    long lastOdoT;
    long lastPointT;
    long lastSpeedT;
    long lastT;
    double maxKmh;
    boolean odoFromOpen;
    int odoJumps;
    double odoKm;
    boolean open;
    double plannedKm;
    long plannedS;
    long readyOffAt;
    long startMs;
    int stops;
    boolean wasMoving;
    double odoStart = Double.NaN;
    double odoLast = Double.NaN;
    double vPrev = Double.NaN;
    double altRef = Double.NaN;
    double startLat = Double.NaN;
    double startLon = Double.NaN;
    double endLat = Double.NaN;
    double endLon = Double.NaN;
    double fuelPctStart = Double.NaN;
    double fuelPctEnd = Double.NaN;
    double lastRemainingM = Double.NaN;
    double socStart = Double.NaN;
    double socEnd = Double.NaN;
    final Map<String, String> rawFirst = new LinkedHashMap();
    final Map<String, String> rawLast = new LinkedHashMap();

    TripState() {
    }

    static TripState decode(String str) {
        TripState tripState = new TripState();
        if (str == null) {
            return tripState;
        }
        for (String str2 : str.split("\n")) {
            int iIndexOf = str2.indexOf(61);
            if (iIndexOf > 0) {
                String strSubstring = str2.substring(0, iIndexOf);
                String strSubstring2 = str2.substring(iIndexOf + 1);
                try {
                    if (strSubstring.startsWith("rf.")) {
                        tripState.rawFirst.put(strSubstring.substring(3), strSubstring2);
                    } else if (!strSubstring.startsWith("rl.")) {
                        switch (strSubstring) {
                            case "open":
                                tripState.open = "1".equals(strSubstring2);
                                break;
                            case "startMs":
                                tripState.startMs = Long.parseLong(strSubstring2);
                                break;
                            case "lastT":
                                tripState.lastT = Long.parseLong(strSubstring2);
                                break;
                            case "readyOffAt":
                                tripState.readyOffAt = Long.parseLong(strSubstring2);
                                break;
                            case "lastPointT":
                                tripState.lastPointT = Long.parseLong(strSubstring2);
                                break;
                            case "odoStart":
                                tripState.odoStart = Double.parseDouble(strSubstring2);
                                break;
                            case "odoLast":
                                tripState.odoLast = Double.parseDouble(strSubstring2);
                                break;
                            case "lastOdoT":
                                tripState.lastOdoT = Long.parseLong(strSubstring2);
                                break;
                            case "odoKm":
                                tripState.odoKm = Double.parseDouble(strSubstring2);
                                break;
                            case "odoJumps":
                                tripState.odoJumps = Integer.parseInt(strSubstring2);
                                break;
                            case "odoFromOpen":
                                tripState.odoFromOpen = "1".equals(strSubstring2);
                                break;
                            case "distInt":
                                tripState.distInt = Double.parseDouble(strSubstring2);
                                break;
                            case "distIntAtOdo":
                                tripState.distIntAtOdo = Double.parseDouble(strSubstring2);
                                break;
                            case "driveMs":
                                tripState.driveMs = Long.parseLong(strSubstring2);
                                break;
                            case "idleMs":
                                tripState.idleMs = Long.parseLong(strSubstring2);
                                break;
                            case "evKm":
                                tripState.evKm = Double.parseDouble(strSubstring2);
                                break;
                            case "iceKm":
                                tripState.iceKm = Double.parseDouble(strSubstring2);
                                break;
                            case "evMs":
                                tripState.evMs = Long.parseLong(strSubstring2);
                                break;
                            case "kwhOut":
                                tripState.kwhOut = Double.parseDouble(strSubstring2);
                                break;
                            case "kwhIn":
                                tripState.kwhIn = Double.parseDouble(strSubstring2);
                                break;
                            case "fuelL":
                                tripState.fuelL = Double.parseDouble(strSubstring2);
                                break;
                            case "maxKmh":
                                tripState.maxKmh = Double.parseDouble(strSubstring2);
                                break;
                            case "stops":
                                tripState.stops = Integer.parseInt(strSubstring2);
                                break;
                            case "wasMoving":
                                tripState.wasMoving = "1".equals(strSubstring2);
                                break;
                            case "harshAccel":
                                tripState.harshAccel = Integer.parseInt(strSubstring2);
                                break;
                            case "harshBrake":
                                tripState.harshBrake = Integer.parseInt(strSubstring2);
                                break;
                            case "climbM":
                                tripState.climbM = Double.parseDouble(strSubstring2);
                                break;
                            case "altRef":
                                tripState.altRef = Double.parseDouble(strSubstring2);
                                break;
                            case "startLat":
                                tripState.startLat = Double.parseDouble(strSubstring2);
                                break;
                            case "startLon":
                                tripState.startLon = Double.parseDouble(strSubstring2);
                                break;
                            case "endLat":
                                tripState.endLat = Double.parseDouble(strSubstring2);
                                break;
                            case "endLon":
                                tripState.endLon = Double.parseDouble(strSubstring2);
                                break;
                            case "fuelPctStart":
                                tripState.fuelPctStart = Double.parseDouble(strSubstring2);
                                break;
                            case "fuelPctEnd":
                                tripState.fuelPctEnd = Double.parseDouble(strSubstring2);
                                break;
                            case "guided":
                                tripState.guided = "1".equals(strSubstring2);
                                break;
                            case "plannedKm":
                                tripState.plannedKm = Double.parseDouble(strSubstring2);
                                break;
                            case "plannedS":
                                tripState.plannedS = Long.parseLong(strSubstring2);
                                break;
                            case "guideStartKm":
                                tripState.guideStartKm = Double.parseDouble(strSubstring2);
                                break;
                            case "guideStartT":
                                tripState.guideStartT = Long.parseLong(strSubstring2);
                                break;
                            case "guideEndKm":
                                tripState.guideEndKm = Double.parseDouble(strSubstring2);
                                break;
                            case "guideEndT":
                                tripState.guideEndT = Long.parseLong(strSubstring2);
                                break;
                            case "arrived":
                                tripState.arrived = "1".equals(strSubstring2);
                                break;
                            case "lastRemainingM":
                                tripState.lastRemainingM = Double.parseDouble(strSubstring2);
                                break;
                            case "lastNavT":
                                tripState.lastNavT = Long.parseLong(strSubstring2);
                                break;
                            case "socStart":
                                tripState.socStart = Double.parseDouble(strSubstring2);
                                break;
                            case "socEnd":
                                tripState.socEnd = Double.parseDouble(strSubstring2);
                                break;
                        }
                    } else {
                        tripState.rawLast.put(strSubstring.substring(3), strSubstring2);
                    }
                } catch (NumberFormatException e) {
                }
            }
        }
        return tripState;
    }

    private static void put(StringBuilder sb, String str, Object obj) {
        sb.append(str).append('=').append(String.valueOf(obj).replace('\n', ' ')).append('\n');
    }

    String encode() {
        StringBuilder sb = new StringBuilder();
        put(sb, "open", Integer.valueOf(this.open ? 1 : 0));
        put(sb, "startMs", Long.valueOf(this.startMs));
        put(sb, "lastT", Long.valueOf(this.lastT));
        put(sb, "readyOffAt", Long.valueOf(this.readyOffAt));
        put(sb, "lastPointT", Long.valueOf(this.lastPointT));
        put(sb, "odoStart", Double.valueOf(this.odoStart));
        put(sb, "odoLast", Double.valueOf(this.odoLast));
        put(sb, "lastOdoT", Long.valueOf(this.lastOdoT));
        put(sb, "odoKm", Double.valueOf(this.odoKm));
        put(sb, "odoJumps", Integer.valueOf(this.odoJumps));
        put(sb, "odoFromOpen", Integer.valueOf(this.odoFromOpen ? 1 : 0));
        put(sb, "distInt", Double.valueOf(this.distInt));
        put(sb, "distIntAtOdo", Double.valueOf(this.distIntAtOdo));
        put(sb, "driveMs", Long.valueOf(this.driveMs));
        put(sb, "idleMs", Long.valueOf(this.idleMs));
        put(sb, "evKm", Double.valueOf(this.evKm));
        put(sb, "iceKm", Double.valueOf(this.iceKm));
        put(sb, "evMs", Long.valueOf(this.evMs));
        put(sb, "kwhOut", Double.valueOf(this.kwhOut));
        put(sb, "kwhIn", Double.valueOf(this.kwhIn));
        put(sb, "fuelL", Double.valueOf(this.fuelL));
        put(sb, "maxKmh", Double.valueOf(this.maxKmh));
        put(sb, "stops", Integer.valueOf(this.stops));
        put(sb, "wasMoving", Integer.valueOf(this.wasMoving ? 1 : 0));
        put(sb, "harshAccel", Integer.valueOf(this.harshAccel));
        put(sb, "harshBrake", Integer.valueOf(this.harshBrake));
        put(sb, "climbM", Double.valueOf(this.climbM));
        put(sb, "altRef", Double.valueOf(this.altRef));
        put(sb, "startLat", Double.valueOf(this.startLat));
        put(sb, "startLon", Double.valueOf(this.startLon));
        put(sb, "endLat", Double.valueOf(this.endLat));
        put(sb, "endLon", Double.valueOf(this.endLon));
        put(sb, "fuelPctStart", Double.valueOf(this.fuelPctStart));
        put(sb, "fuelPctEnd", Double.valueOf(this.fuelPctEnd));
        put(sb, "guided", Integer.valueOf(this.guided ? 1 : 0));
        put(sb, "plannedKm", Double.valueOf(this.plannedKm));
        put(sb, "plannedS", Long.valueOf(this.plannedS));
        put(sb, "guideStartKm", Double.valueOf(this.guideStartKm));
        put(sb, "guideStartT", Long.valueOf(this.guideStartT));
        put(sb, "guideEndKm", Double.valueOf(this.guideEndKm));
        put(sb, "guideEndT", Long.valueOf(this.guideEndT));
        put(sb, "arrived", Integer.valueOf(this.arrived ? 1 : 0));
        put(sb, "lastRemainingM", Double.valueOf(this.lastRemainingM));
        put(sb, "lastNavT", Long.valueOf(this.lastNavT));
        put(sb, "socStart", Double.valueOf(this.socStart));
        put(sb, "socEnd", Double.valueOf(this.socEnd));
        for (Map.Entry<String, String> entry : this.rawFirst.entrySet()) {
            put(sb, "rf." + entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, String> entry2 : this.rawLast.entrySet()) {
            put(sb, "rl." + entry2.getKey(), entry2.getValue());
        }
        return sb.toString();
    }
}
