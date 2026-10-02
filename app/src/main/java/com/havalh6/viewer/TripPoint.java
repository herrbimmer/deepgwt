package com.havalh6.viewer;

final class TripPoint {
    final double alt;
    final int fuelMode;
    final double fuelRate;
    final int ice;
    final double km;
    final double kmh;
    final double kw;
    final double lat;
    final double lon;
    final long t;

    TripPoint(long t, double lat, double lon, double alt, double kmh, double kw, int fuelMode, double fuelRate, int ice, double km) {
        this.t = t;
        this.lat = lat;
        this.lon = lon;
        this.alt = alt;
        this.kmh = kmh;
        this.kw = kw;
        this.fuelMode = fuelMode;
        this.fuelRate = fuelRate;
        this.ice = ice;
        this.km = km;
    }

    boolean hasFix() {
        return (Double.isNaN(this.lat) || Double.isNaN(this.lon)) ? false : true;
    }
}
