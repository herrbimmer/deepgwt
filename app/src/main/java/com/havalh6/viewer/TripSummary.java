package com.havalh6.viewer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

final class TripSummary {
    static final double MIN_FUEL_FOR_RATIO_L = 0.05d;
    final boolean arrived;
    final double climbM;
    final long driveMs;
    final double endLat;
    final double endLon;
    final long endMs;
    final double evKm;
    final long evMs;
    final double fuelL;
    final double fuelPctEnd;
    final double fuelPctStart;
    final boolean guided;
    final double guidedKm;
    final long guidedS;
    final int harshAccel;
    final int harshBrake;
    final long idleMs;
    final double km;
    final double kmIntegrated;
    final double kwhIn;
    final double kwhOut;
    final double maxKmh;
    final double odoEnd;
    final int odoJumps;
    final double odoStart;
    final boolean open;
    final double plannedKm;
    final long plannedS;
    final Map<String, String> rawFirst;
    final Map<String, String> rawLast;
    final double socEnd;
    final double socStart;
    final double startLat;
    final double startLon;
    final long startMs;
    final int stops;

    TripSummary(TripState tripState, long endMs, double km) {
        this.open = tripState.open;
        this.startMs = tripState.startMs;
        this.endMs = endMs;
        this.km = km;
        this.kmIntegrated = tripState.distInt;
        this.odoStart = tripState.odoStart;
        this.odoEnd = tripState.odoLast;
        this.odoJumps = tripState.odoJumps;
        this.driveMs = tripState.driveMs;
        this.idleMs = tripState.idleMs;
        double d2 = tripState.evKm + tripState.iceKm;
        this.evKm = d2 > 0.0d ? Math.min(1.0d, tripState.evKm / d2) * km : 0.0d;
        this.evMs = tripState.evMs;
        this.kwhOut = tripState.kwhOut;
        this.kwhIn = tripState.kwhIn;
        this.fuelL = tripState.fuelL;
        this.maxKmh = tripState.maxKmh;
        this.stops = tripState.stops;
        this.harshAccel = tripState.harshAccel;
        this.harshBrake = tripState.harshBrake;
        this.climbM = tripState.climbM;
        this.startLat = tripState.startLat;
        this.startLon = tripState.startLon;
        this.endLat = tripState.endLat;
        this.endLon = tripState.endLon;
        this.fuelPctStart = tripState.fuelPctStart;
        this.fuelPctEnd = tripState.fuelPctEnd;
        this.guided = tripState.guided;
        this.arrived = tripState.arrived;
        this.plannedKm = tripState.guided ? tripState.plannedKm : Double.NaN;
        this.plannedS = tripState.guided ? tripState.plannedS : 0L;
        double d3 = tripState.guideEndT > 0 ? tripState.guideEndKm : km;
        long j2 = tripState.guideEndT > 0 ? tripState.guideEndT : endMs;
        this.guidedKm = tripState.guided ? Math.max(0.0d, d3 - tripState.guideStartKm) : Double.NaN;
        this.guidedS = tripState.guided ? Math.max(0L, j2 - tripState.guideStartT) / 1000 : 0L;
        this.socStart = tripState.socStart;
        this.socEnd = tripState.socEnd;
        this.rawFirst = Collections.unmodifiableMap(new LinkedHashMap(tripState.rawFirst));
        this.rawLast = Collections.unmodifiableMap(new LinkedHashMap(tripState.rawLast));
    }

    double avgMovingKmh() {
        if (this.driveMs > 0) {
            return this.kmIntegrated / (this.driveMs / 3600000.0d);
        }
        return Double.NaN;
    }

    double evShare() {
        if (this.km >= 0.1d) {
            return Math.min(1.0d, this.evKm / this.km);
        }
        return Double.NaN;
    }

    double kmPerLitre() {
        if (this.fuelL >= 0.05d) {
            return this.km / this.fuelL;
        }
        return Double.NaN;
    }

    double kwhPer100Km() {
        if (this.km >= 0.1d) {
            return ((this.kwhOut - this.kwhIn) * 100.0d) / this.km;
        }
        return Double.NaN;
    }
}
