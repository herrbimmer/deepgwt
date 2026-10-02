package com.havalh6.viewer;

final class TripSignals {
    static final int FUEL_IDLE = 4;
    static final int FUEL_RUNNING = 1;
    static final int FUEL_UNKNOWN = 0;

    static final class FuelRate {
        final int mode;
        final double value;

        FuelRate(int mode, double value) {
            this.mode = mode;
            this.value = value;
        }
    }

    private TripSignals() {
    }

    static boolean inRange(double d, double d2, double d3) {
        return !Double.isNaN(d) && !isSentinel(d) && d >= d2 && d <= d3;
    }

    static boolean isSentinel(double d) {
        return d == 65535.0d || d == 6553.5d || d == 1023.0d || d == 2047.0d;
    }

    static Boolean parseBool(String str) {
        if (str == null) {
            return null;
        }
        String strTrim = str.trim();
        if (strTrim.startsWith("{")) {
            String strStripBraces = stripBraces(strTrim);
            int iIndexOf = strStripBraces.indexOf(44);
            if (iIndexOf >= 0) {
                strStripBraces = strStripBraces.substring(0, iIndexOf);
            }
            strTrim = strStripBraces.trim();
        }
        if (strTrim.isEmpty()) {
            return null;
        }
        return Boolean.valueOf("1".equals(strTrim) || "true".equalsIgnoreCase(strTrim));
    }

    static Boolean parseFlowIce(String str) {
        if (str == null) {
            return null;
        }
        String[] strArrSplit = str.split("\\|");
        if (strArrSplit.length < 5 || !"v1".equals(strArrSplit[0])) {
            return null;
        }
        return Boolean.valueOf("1".equals(strArrSplit[2]));
    }

    static FuelRate parseFuelInst(String str) {
        int iRound;
        double d;
        if (str == null) {
            return null;
        }
        String strTrim = str.trim();
        try {
            if (strTrim.startsWith("{") && strTrim.contains(",")) {
                String[] strArrSplit = stripBraces(strTrim).split(",");
                iRound = (int) Math.round(Double.parseDouble(strArrSplit[0].trim()));
                d = Double.parseDouble(strArrSplit[1].trim());
            } else {
                iRound = 1;
                d = Double.parseDouble(strTrim);
            }
            if (isSentinel(d) || Double.isNaN(d) || d < 0.0d) {
                return null;
            }
            if (iRound == 1) {
                if (d <= 60.0d) {
                    return new FuelRate(1, d);
                }
                return null;
            }
            if (iRound != 4) {
                return new FuelRate(0, 0.0d);
            }
            if (d <= 10.0d) {
                return new FuelRate(4, d);
            }
            return null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    static double parseNumber(String str) {
        if (str == null) {
            return Double.NaN;
        }
        String strTrim = str.trim();
        if (strTrim.startsWith("{")) {
            String[] strArrSplit = stripBraces(strTrim).split(",");
            strTrim = (strArrSplit.length > 1 ? strArrSplit[1] : strArrSplit[0]).trim();
        }
        try {
            double d = Double.parseDouble(strTrim.replace(',', '.'));
            if (Double.isInfinite(d)) {
                return Double.NaN;
            }
            return d;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    static Boolean parseReady(String str) {
        if (str == null) {
            return null;
        }
        String strTrim = str.trim();
        boolean z = false;
        if (strTrim.startsWith("{")) {
            String strStripBraces = stripBraces(strTrim);
            int iIndexOf = strStripBraces.indexOf(44);
            if (iIndexOf >= 0) {
                strStripBraces = strStripBraces.substring(0, iIndexOf);
            }
            strTrim = strStripBraces.trim();
        }
        if (strTrim.isEmpty()) {
            return null;
        }
        if (!"0".equals(strTrim) && !"-1".equals(strTrim)) {
            z = true;
        }
        return Boolean.valueOf(z);
    }

    private static String stripBraces(String str) {
        String strSubstring = str;
        if (strSubstring.startsWith("{")) {
            strSubstring = strSubstring.substring(1);
        }
        return strSubstring.endsWith("}") ? strSubstring.substring(0, strSubstring.length() - 1) : strSubstring;
    }
}
