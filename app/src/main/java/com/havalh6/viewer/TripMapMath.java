package com.havalh6.viewer;

final class TripMapMath {
    static final double MAX_LAT = 85.05112878d;
    static final int TILE = 256;

    private TripMapMath() {
    }

    static int fitZoom(double d, double d2, double d3, double d4, int i, int i2, int i3, int i4, int i5) {
        for (int i6 = i5; i6 > i4; i6--) {
            double dWorldX = worldX(d4, i6) - worldX(d3, i6);
            double dWorldY = worldY(d, i6) - worldY(d2, i6);
            if (dWorldX <= i - (i3 * 2) && dWorldY <= i2 - (i3 * 2)) {
                return i6;
            }
        }
        return i4;
    }

    static double worldX(double d, int i) {
        return ((180.0d + d) / 360.0d) * 256.0d * (1 << i);
    }

    static double worldY(double d, int i) {
        double radians = Math.toRadians(Math.max(-85.05112878d, Math.min(85.05112878d, d)));
        return ((1.0d - (Math.log(Math.tan(radians) + (1.0d / Math.cos(radians))) / 3.141592653589793d)) / 2.0d) * 256.0d * (1 << i);
    }
}
