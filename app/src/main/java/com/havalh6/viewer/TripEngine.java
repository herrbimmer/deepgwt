package com.havalh6.viewer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class TripEngine {
    static final long ACCEL_MAX_DT_MS = 3000;
    static final long ACCEL_MIN_DT_MS = 400;
    static final double ARRIVAL_M = 150.0d;
    static final double CHARGE_MIN_PCT = 8.0d;
    static final double CLIMB_HYSTERESIS_M = 3.0d;
    static final double CLIMB_MAX_ACCURACY_M = 25.0d;
    static final long FIX_FRESH_MS = 5000;
    static final double FIX_MAX_ACCURACY_M = 50.0d;
    static final long GUIDANCE_HOLD_MAX_MS = 3600000;
    static final double HARSH_ACCEL_MS2 = 3.0d;
    static final double HARSH_BRAKE_MS2 = -3.5d;
    static final String KEY_CURRENT = "car.ev_info.cur_charge_current";
    static final String KEY_FLOW = "haval.power.flow";
    static final String KEY_FUEL_INST = "car.basic.instant_fuel_consumption";
    static final String KEY_FUEL_PCT = "car.basic.remain_fuel_percentage";
    static final String KEY_ICE = "haval.power.ice";
    static final String KEY_NAV = "app.navigation.directions";
    static final String KEY_ODOMETER = "car.basic.total_odometer";
    static final String KEY_READY = "car.basic.driving_ready_state";
    static final String KEY_SOC = "car.ev_info.cur_battery_power_percentage";
    static final String KEY_SPEED = "car.basic.vehicle_speed";
    static final String KEY_VOLTAGE = "car.ev_info.power_battery_voltage";
    static final long LEVEL_SETTLE_MS = 180000;
    static final long MAX_STEP_MS = 10000;
    static final long MERGE_GAP_MS = 600000;
    static final long MIN_TRIP_DRIVE_MS = 60000;
    static final double MIN_TRIP_KM = 0.2d;
    static final double MOVING_KMH = 1.0d;
    static final double ODOMETER_MAX_KMH = 250.0d;
    static final double ODOMETER_SLACK_KM = 0.30000000000000004d;
    static final double ODOMETER_TICK_KM = 0.1d;
    static final long POINT_MOVING_MS = 1000;
    static final long POINT_STOPPED_MS = 10000;
    static final Set<String> RAW_KEYS = new HashSet(Arrays.asList("car.basic.total_odometer", "car.basic.remain_fuel_percentage", "car.basic.avg_fuel_consumption", "car.basic.cur_journey_avg_fuel_consume", "car.ev_info.avg_energy_consume_info_since_startup", "car.basic.accumulated_odometer", "car.basic.accumulated_drivetime", "car.basic.vehicle_speed_since_reset", "car.ev_info.cycle_fuel_consume_info", "car.ev_info.cycle_energy_consume_info"));
    static final double REFUEL_MIN_PCT = 4.0d;
    static final double STOP_ARMED_ABOVE_KMH = 5.0d;
    static final double STOP_BELOW_KMH = 1.0d;
    static final double TANK_LITRES = 55.0d;
    private TripStop chargeCandidate;
    private long fixT;
    private Boolean ice;
    private final Listener listener;
    private long parkedAt;
    private Boolean ready;
    private TripStop refuelCandidate;
    private long resumedAt;
    private TripState st = new TripState();
    private double kmh = Double.NaN;
    private double volt = Double.NaN;
    private double amp = Double.NaN;
    private double odo = Double.NaN;
    private double fuelPct = Double.NaN;
    private double soc = Double.NaN;
    private double parkedFuelPct = Double.NaN;
    private double parkedSoc = Double.NaN;
    private double parkedLat = Double.NaN;
    private double parkedLon = Double.NaN;
    private final List<TripStop> pendingStops = new ArrayList();
    private int fuelMode = 0;
    private double fuelRate = Double.NaN;
    private double lat = Double.NaN;
    private double lon = Double.NaN;
    private double alt = Double.NaN;
    private final Map<String, String> latestRaw = new HashMap();

    interface Listener {
        void onPoint(long j, TripPoint tripPoint);

        void onStop(long j, TripStop tripStop);

        void onTripClosed(TripSummary tripSummary);

        void onTripDiscarded(long j);

        void onTripOpened(long j);
    }

    TripEngine(Listener listener) {
        this.listener = listener;
    }

    private void advance(long j) {
        if (this.st.open) {
            long j2 = j - this.st.lastT;
            if (j2 <= 0) {
                if (j2 < 0) {
                    this.st.lastT = j;
                    return;
                }
                return;
            }
            this.st.lastT = j;
            if (j2 <= 10000 && Boolean.TRUE.equals(this.ready)) {
                double d = j2 / 3600000.0d;
                if (!Double.isNaN(this.kmh)) {
                    double d2 = this.kmh * d;
                    this.st.distInt += d2;
                    boolean z = this.kmh >= 1.0d;
                    TripState tripState = this.st;
                    if (z) {
                        tripState.driveMs += j2;
                    } else {
                        tripState.idleMs += j2;
                    }
                    if (z && Boolean.FALSE.equals(this.ice)) {
                        this.st.evKm += d2;
                        this.st.evMs += j2;
                    } else if (z && Boolean.TRUE.equals(this.ice)) {
                        this.st.iceKm += d2;
                    }
                    if (this.fuelMode == 1 && this.fuelRate > 0.0d) {
                        this.st.fuelL += (this.fuelRate / 100.0d) * d2;
                    }
                }
                if (this.fuelMode == 4 && this.fuelRate > 0) {
                    this.st.fuelL += this.fuelRate * d;
                }
                if (Double.isNaN(this.volt) || Double.isNaN(this.amp)) {
                    return;
                }
                double d3 = ((this.volt * this.amp) / 1000.0d) * d;
                TripState tripState2 = this.st;
                if (d3 >= 0.0d) {
                    tripState2.kwhOut += d3;
                } else {
                    tripState2.kwhIn -= d3;
                }
            }
        }
    }

    private static double amountOf(boolean z, double d, double d2) {
        double d3 = d2 - d;
        return z ? (d3 * 55.0d) / 100.0d : d3;
    }

    private void close(long j) {
        flushCandidates();
        if (this.st.guided && this.st.guideEndT == 0) {
            this.st.arrived = !Double.isNaN(this.st.lastRemainingM) && this.st.lastRemainingM <= 300.0d;
            this.st.guideEndT = j;
            this.st.guideEndKm = finalKm();
        }
        this.st.open = false;
        TripSummary tripSummary = new TripSummary(this.st, j, finalKm());
        long j2 = this.st.startMs;
        this.st = new TripState();
        if (tripSummary.km >= 0.2d || tripSummary.driveMs >= 60000) {
            this.listener.onTripClosed(tripSummary);
        } else {
            this.listener.onTripDiscarded(j2);
        }
    }

    private void countHarsh(double d, long j) {
        if (this.st.lastSpeedT > 0) {
            long j2 = j - this.st.lastSpeedT;
            if (j2 < 400) {
                return;
            }
            if (j2 <= 3000 && !Double.isNaN(this.st.vPrev)) {
                double d2 = ((d - this.st.vPrev) / 3.6d) / (j2 / 1000.0d);
                if (d2 >= 3.0d) {
                    if (!this.st.inAccel) {
                        this.st.harshAccel++;
                    }
                    this.st.inAccel = true;
                } else if (d2 < 1.5d) {
                    this.st.inAccel = false;
                }
                if (d2 <= -3.5d) {
                    if (!this.st.inBrake) {
                        this.st.harshBrake++;
                    }
                    this.st.inBrake = true;
                } else if (d2 > -1.75d) {
                    this.st.inBrake = false;
                }
            }
        }
        this.st.vPrev = d;
        this.st.lastSpeedT = j;
    }

    private void emitStop(TripStop tripStop) {
        if (this.st.open) {
            this.listener.onStop(this.st.startMs, tripStop);
        } else {
            this.pendingStops.add(tripStop);
        }
    }

    private void endGuidance(long j, boolean z) {
        this.st.arrived = z;
        this.st.guideEndT = j;
        this.st.guideEndKm = liveKm();
    }

    private double finalKm() {
        double dOdoDelta = odoDelta();
        boolean zIsNaN = Double.isNaN(dOdoDelta);
        TripState tripState = this.st;
        if (zIsNaN) {
            return tripState.distInt;
        }
        return (!tripState.odoFromOpen || dOdoDelta < 0.1d) ? Math.max(dOdoDelta, this.st.distInt) : dOdoDelta;
    }

    private void flushCandidates() {
        if (this.refuelCandidate != null) {
            emitStop(this.refuelCandidate);
        }
        if (this.chargeCandidate != null) {
            emitStop(this.chargeCandidate);
        }
        this.refuelCandidate = null;
        this.chargeCandidate = null;
    }

    private boolean guidingNow(long j) {
        return this.st.open && this.st.guided && this.st.guideEndT == 0 && this.st.lastRemainingM > 300.0d && j - this.st.lastNavT < 3600000;
    }

    private double liveKm() {
        double dOdoDelta = odoDelta();
        boolean zIsNaN = Double.isNaN(dOdoDelta);
        TripState tripState = this.st;
        if (zIsNaN) {
            return tripState.distInt;
        }
        double dMax = dOdoDelta + Math.max(0.0d, Math.min(0.1d, tripState.distInt - this.st.distIntAtOdo));
        return this.st.odoFromOpen ? dMax : Math.max(dMax, this.st.distInt);
    }

    private double odoDelta() {
        if (Double.isNaN(this.st.odoLast)) {
            return Double.NaN;
        }
        return this.st.odoKm;
    }

    private void onLevel(boolean z, double d, long j) {
        double d2 = z ? this.parkedFuelPct : this.parkedSoc;
        if (Double.isNaN(d2)) {
            return;
        }
        if (!Boolean.TRUE.equals(this.ready) || (this.resumedAt > 0 && j - this.resumedAt <= 180000)) {
            TripStop tripStop = z ? this.refuelCandidate : this.chargeCandidate;
            if (tripStop != null) {
                if (d > tripStop.after) {
                    tripStop.after = d;
                    tripStop.amount = amountOf(z, tripStop.before, d);
                    return;
                }
                return;
            }
            if (d - d2 < (z ? 4.0d : 8.0d)) {
                return;
            }
            TripStop tripStop2 = new TripStop(z ? "refuel" : "charge", this.parkedAt > 0 ? this.parkedAt : j, this.parkedLat, this.parkedLon, d2, d, amountOf(z, d2, d));
            if (z) {
                this.refuelCandidate = tripStop2;
            } else {
                this.chargeCandidate = tripStop2;
            }
        }
    }

    private void onOdometer(double odo, long j) {
        this.odo = odo;
        if (this.st.open) {
            boolean zIsNaN = Double.isNaN(this.st.odoLast);
            TripState tripState = this.st;
            if (zIsNaN) {
                tripState.odoStart = odo;
                this.st.odoLast = odo;
                this.st.lastOdoT = j;
                this.st.odoFromOpen = this.st.distInt < 0.05d;
                this.st.distIntAtOdo = this.st.distInt;
                return;
            }
            if (odo == tripState.odoLast) {
                return;
            }
            double d2 = odo - this.st.odoLast;
            double dMax = (((this.st.lastOdoT > 0 ? Math.max(0L, j - this.st.lastOdoT) : 0L) * 250.0d) / 3600000.0d) + 0.30000000000000004d;
            if (d2 <= 0.0d || d2 > dMax || Boolean.FALSE.equals(this.ready)) {
                this.st.odoJumps++;
            } else {
                this.st.odoKm += d2;
            }
            this.st.odoLast = odo;
            this.st.lastOdoT = j;
            this.st.distIntAtOdo = this.st.distInt;
        }
    }

    private void onSpeed(double kmh, long j) {
        if (this.st.open && Boolean.TRUE.equals(this.ready)) {
            if (kmh > this.st.maxKmh) {
                this.st.maxKmh = kmh;
            }
            if (kmh > 5.0d) {
                this.st.wasMoving = true;
            } else if (kmh < 1.0d && this.st.wasMoving) {
                this.st.stops++;
                this.st.wasMoving = false;
            }
            countHarsh(kmh, j);
        }
        this.kmh = kmh;
    }

    private void open(long j) {
        this.st = new TripState();
        this.st.open = true;
        this.st.startMs = j;
        this.st.lastT = j;
        if (!Double.isNaN(this.odo)) {
            this.st.odoStart = this.odo;
            this.st.odoLast = this.odo;
            this.st.lastOdoT = j;
            this.st.odoFromOpen = true;
        }
        this.st.fuelPctStart = this.fuelPct;
        this.st.fuelPctEnd = this.fuelPct;
        this.st.socStart = this.soc;
        this.st.socEnd = this.soc;
        this.st.rawFirst.putAll(this.latestRaw);
        this.st.rawLast.putAll(this.latestRaw);
        if (this.fixT > 0 && j - this.fixT <= 30000) {
            this.st.startLat = this.lat;
            this.st.startLon = this.lon;
            this.st.endLat = this.lat;
            this.st.endLon = this.lon;
        }
        this.st.vPrev = this.kmh;
        this.st.lastSpeedT = Double.isNaN(this.kmh) ? 0L : j;
        this.st.wasMoving = !Double.isNaN(this.kmh) && this.kmh > 5.0d;
        this.listener.onTripOpened(j);
        Iterator<TripStop> it = this.pendingStops.iterator();
        while (it.hasNext()) {
            this.listener.onStop(j, it.next());
        }
        this.pendingStops.clear();
    }

    private void park(long parkedAt) {
        flushCandidates();
        if (!Double.isNaN(this.fuelPct)) {
            this.parkedFuelPct = this.fuelPct;
        }
        if (!Double.isNaN(this.soc)) {
            this.parkedSoc = this.soc;
        }
        if (!Double.isNaN(this.lat)) {
            this.parkedLat = this.lat;
            this.parkedLon = this.lon;
        }
        this.parkedAt = parkedAt;
        this.resumedAt = 0L;
    }

    private void setReady(boolean z, long resumedAt) {
        Boolean bool = this.ready;
        this.ready = Boolean.valueOf(z);
        TripState tripState = this.st;
        if (!z) {
            if (tripState.open && this.st.readyOffAt == 0) {
                this.st.readyOffAt = resumedAt;
            }
            if (Boolean.FALSE.equals(bool)) {
                return;
            }
            park(resumedAt);
            return;
        }
        if (!tripState.open) {
            open(resumedAt);
        } else if (this.st.readyOffAt > 0) {
            if (resumedAt - this.st.readyOffAt < 600000 || guidingNow(resumedAt)) {
                this.st.readyOffAt = 0L;
            } else {
                close(this.st.readyOffAt);
                open(resumedAt);
            }
        }
        if (Boolean.TRUE.equals(bool)) {
            return;
        }
        this.resumedAt = resumedAt;
    }

    private void settleStops(long j) {
        if (this.resumedAt == 0 || !Boolean.TRUE.equals(this.ready) || j - this.resumedAt <= 180000) {
            return;
        }
        flushCandidates();
        this.parkedFuelPct = Double.NaN;
        this.parkedSoc = Double.NaN;
        this.resumedAt = 0L;
    }

    String checkpoint() {
        return this.st.encode();
    }

    void closeInterrupted(long j) {
        if (this.st.open) {
            this.ready = null;
            boolean zGuidingNow = guidingNow(j);
            TripState tripState = this.st;
            if (zGuidingNow) {
                if (tripState.readyOffAt == 0) {
                    this.st.readyOffAt = this.st.lastT;
                }
            } else {
                long j2 = tripState.readyOffAt;
                TripState tripState2 = this.st;
                close(j2 > 0 ? tripState2.readyOffAt : tripState2.lastT);
            }
        }
    }

    void finishNow(long j) {
        advance(j);
        if (this.st.open) {
            close(this.st.readyOffAt > 0 ? this.st.readyOffAt : j);
            if (Boolean.TRUE.equals(this.ready)) {
                open(j);
            }
        }
    }

    boolean isOpen() {
        return this.st.open;
    }

    TripSummary live(long j) {
        advance(j);
        if (this.st.open) {
            return new TripSummary(this.st, j, liveKm());
        }
        return null;
    }

    void onLocation(double lat, double lon, double alt, double d4, long fixT) {
        advance(fixT);
        if (Double.isNaN(lat) || Double.isNaN(lon) || d4 > 50.0d) {
            return;
        }
        this.lat = lat;
        this.lon = lon;
        this.alt = alt;
        this.fixT = fixT;
        if (this.st.open) {
            if (Double.isNaN(this.st.startLat)) {
                this.st.startLat = lat;
                this.st.startLon = lon;
            }
            this.st.endLat = lat;
            this.st.endLon = lon;
            if (Double.isNaN(alt) || d4 > 25.0d) {
                return;
            }
            boolean zIsNaN = Double.isNaN(this.st.altRef);
            TripState tripState = this.st;
            if (zIsNaN) {
                tripState.altRef = alt;
                return;
            }
            double d5 = tripState.altRef + 3.0d;
            TripState tripState2 = this.st;
            if (alt > d5) {
                tripState2.climbM += alt - this.st.altRef;
                this.st.altRef = alt;
            } else if (alt < tripState2.altRef - 3.0d) {
                this.st.altRef = alt;
            }
        }
    }

    void onNavigation(boolean z, double d, double d2, boolean z2, long j) {
        advance(j);
        if (this.st.open) {
            TripState tripState = this.st;
            if (!z) {
                if (tripState.guided && this.st.guideEndT == 0) {
                    if (Double.isNaN(this.st.lastRemainingM) || this.st.lastRemainingM > 300.0d) {
                        endGuidance(j, false);
                        return;
                    } else {
                        endGuidance(j, true);
                        return;
                    }
                }
                return;
            }
            if (!tripState.guided) {
                if (d <= 0.0d) {
                    return;
                }
                this.st.guided = true;
                this.st.plannedKm = d / 1000.0d;
                this.st.plannedS = d2 > 0.0d ? Math.round(d2) : 0L;
                this.st.guideStartKm = liveKm();
                this.st.guideStartT = j;
            }
            this.st.lastNavT = j;
            if (this.st.guideEndT != 0 || Double.isNaN(d)) {
                return;
            }
            this.st.lastRemainingM = d;
            if (d <= 150.0d || (z2 && d <= 300.0d)) {
                endGuidance(j, true);
            }
        }
    }

    void onSignal(String str, String str2, long j) {
        if (str == null || str2 == null) {
            return;
        }
        advance(j);
        if (RAW_KEYS.contains(str)) {
            this.latestRaw.put(str, str2);
            if (this.st.open) {
                if (!this.st.rawFirst.containsKey(str)) {
                    this.st.rawFirst.put(str, str2);
                }
                this.st.rawLast.put(str, str2);
            }
        }
        switch (str) {
            case "car.basic.driving_ready_state":
                Boolean ready = TripSignals.parseReady(str2);
                if (ready != null) {
                    setReady(ready.booleanValue(), j);
                }
                break;
            case "car.basic.vehicle_speed":
                double number = TripSignals.parseNumber(str2);
                if (TripSignals.inRange(number, 0.0d, 300.0d)) {
                    onSpeed(number, j);
                }
                break;
            case "car.basic.total_odometer":
                double number2 = TripSignals.parseNumber(str2);
                if (TripSignals.inRange(number2, 0.0d, 2000000.0d)) {
                    onOdometer(number2, j);
                }
                break;
            case "car.ev_info.power_battery_voltage":
                double number3 = TripSignals.parseNumber(str2);
                this.volt = TripSignals.inRange(number3, 1.0d, 1000.0d) ? number3 : Double.NaN;
                break;
            case "car.ev_info.cur_charge_current":
                double number4 = TripSignals.parseNumber(str2);
                this.amp = TripSignals.inRange(number4, -1000.0d, 1000.0d) ? number4 : Double.NaN;
                break;
            case "car.basic.instant_fuel_consumption":
                TripSignals.FuelRate fuelInst = TripSignals.parseFuelInst(str2);
                this.fuelMode = fuelInst != null ? fuelInst.mode : 0;
                this.fuelRate = fuelInst != null ? fuelInst.value : Double.NaN;
                break;
            case "haval.power.ice":
                Boolean bool = TripSignals.parseBool(str2);
                if (bool != null) {
                    this.ice = bool;
                }
                break;
            case "haval.power.flow":
                Boolean flowIce = TripSignals.parseFlowIce(str2);
                if (flowIce != null) {
                    this.ice = flowIce;
                }
                break;
            case "car.basic.remain_fuel_percentage":
                double number5 = TripSignals.parseNumber(str2);
                if (TripSignals.inRange(number5, 0.0d, 100.0d)) {
                    this.fuelPct = number5;
                    if (this.st.open) {
                        if (Double.isNaN(this.st.fuelPctStart)) {
                            this.st.fuelPctStart = number5;
                        }
                        this.st.fuelPctEnd = number5;
                    }
                    onLevel(true, number5, j);
                }
                break;
            case "car.ev_info.cur_battery_power_percentage":
                double number6 = TripSignals.parseNumber(str2);
                if (TripSignals.inRange(number6, 0.0d, 100.0d)) {
                    this.soc = number6;
                    if (this.st.open) {
                        if (Double.isNaN(this.st.socStart)) {
                            this.st.socStart = number6;
                        }
                        this.st.socEnd = number6;
                    }
                    onLevel(false, number6, j);
                }
                break;
        }
    }

    void primeParked(double parkedFuelPct, double parkedSoc, double parkedLat, double parkedLon, long parkedAt) {
        if (Double.isNaN(this.parkedFuelPct)) {
            this.parkedFuelPct = parkedFuelPct;
        }
        if (Double.isNaN(this.parkedSoc)) {
            this.parkedSoc = parkedSoc;
        }
        if (Double.isNaN(this.parkedLat)) {
            this.parkedLat = parkedLat;
            this.parkedLon = parkedLon;
        }
        if (this.parkedAt == 0) {
            this.parkedAt = parkedAt;
        }
    }

    void restore(TripState tripState, long j) {
        if (tripState == null || !tripState.open) {
            return;
        }
        this.st = tripState;
        this.ready = null;
        if (this.st.readyOffAt == 0) {
            this.st.readyOffAt = this.st.lastT;
        }
        if (j - this.st.readyOffAt < 600000 || guidingNow(j)) {
            return;
        }
        close(this.st.readyOffAt);
    }

    void tick(long j) {
        int i;
        advance(j);
        settleStops(j);
        if (this.st.open) {
            if (!Boolean.TRUE.equals(this.ready)) {
                if (this.st.readyOffAt <= 0 || j - this.st.readyOffAt < 600000 || guidingNow(j)) {
                    return;
                }
                close(this.st.readyOffAt);
                return;
            }
            long j2 = !Double.isNaN(this.kmh) && (this.kmh > 1.0d ? 1 : (this.kmh == 1.0d ? 0 : -1)) >= 0 ? 1000L : 10000L;
            if (this.st.lastPointT == 0 || j - this.st.lastPointT >= j2) {
                this.st.lastPointT = j;
                boolean z = this.fixT > 0 && j - this.fixT <= 5000;
                double d = (Double.isNaN(this.volt) || Double.isNaN(this.amp)) ? Double.NaN : (this.volt * this.amp) / 1000.0d;
                Listener listener = this.listener;
                long j3 = this.st.startMs;
                double d2 = z ? this.lat : Double.NaN;
                double d3 = z ? this.lon : Double.NaN;
                double d4 = z ? this.alt : Double.NaN;
                double d5 = this.kmh;
                double d6 = d2;
                double d7 = d3;
                int i2 = this.fuelMode;
                double d8 = this.fuelRate;
                if (this.ice == null) {
                    i = -1;
                } else {
                    i = this.ice.booleanValue() ? 1 : 0;
                }
                listener.onPoint(j3, new TripPoint(j, d6, d7, d4, d5, d, i2, d8, i, liveKm()));
            }
        }
    }
}
