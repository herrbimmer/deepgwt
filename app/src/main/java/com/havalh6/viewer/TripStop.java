package com.havalh6.viewer;

final class TripStop {
    static final String CHARGE = "charge";
    static final String REFUEL = "refuel";
    double after;
    double amount;
    final double before;
    final String kind;
    final double lat;
    final double lon;
    final long t;

    TripStop(String kind, long t, double lat, double lon, double before, double after, double amount) {
        this.kind = kind;
        this.t = t;
        this.lat = lat;
        this.lon = lon;
        this.before = before;
        this.after = after;
        this.amount = amount;
    }
}
