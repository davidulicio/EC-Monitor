package ca.uqam.ecmonitor.proto;

/** Small formatting and parsing helpers, kept free of android imports so they can be unit tested. */
public class Fmt {

    public static double toDouble(String s) {
        if (s == null) return Double.NaN;
        String t = s.trim();
        if (t.length() == 0) return Double.NaN;
        // Values arrive as 2.2083146e1, 1E-7, -3.47e-09, plain integers, or TRUE/FALSE.
        try {
            return Double.parseDouble(t);
        } catch (NumberFormatException e) {
            if (t.equalsIgnoreCase("TRUE")) return 1;
            if (t.equalsIgnoreCase("FALSE")) return 0;
            return Double.NaN;
        }
    }

    public static boolean isNum(double d) {
        return !Double.isNaN(d) && !Double.isInfinite(d);
    }

    /** TRUE / FALSE / 1 / 0, defaulting to def when unreadable. */
    public static boolean toBool(String s, boolean def) {
        if (s == null) return def;
        String t = s.trim();
        if (t.equalsIgnoreCase("TRUE")) return true;
        if (t.equalsIgnoreCase("FALSE")) return false;
        if (t.equals("1")) return true;
        if (t.equals("0")) return false;
        return def;
    }

    /** Fixed-decimal formatting without pulling in String.format locale surprises. */
    public static String dec(double v, int decimals) {
        if (!isNum(v)) return "--";
        boolean neg = v < 0;
        double a = Math.abs(v);
        long scale = 1;
        for (int i = 0; i < decimals; i++) scale *= 10;
        long r = Math.round(a * scale);
        long whole = r / scale;
        long frac = r % scale;
        StringBuilder sb = new StringBuilder();
        if (neg && (whole != 0 || frac != 0)) sb.append('-');
        sb.append(whole);
        if (decimals > 0) {
            sb.append('.');
            String f = Long.toString(frac);
            for (int i = f.length(); i < decimals; i++) sb.append('0');
            sb.append(f);
        }
        return sb.toString();
    }

    /** Human duration for "updated 2 m 14 s ago". */
    public static String since(long millis) {
        if (millis < 0) return "--";
        long s = millis / 1000;
        if (s < 60) return s + " s";
        long m = s / 60;
        s = s % 60;
        if (m < 60) return m + " m " + s + " s";
        long h = m / 60;
        m = m % 60;
        if (h < 48) return h + " h " + m + " m";
        return (h / 24) + " d " + (h % 24) + " h";
    }

    public static String bytesMb(double mb) {
        if (!isNum(mb)) return "--";
        if (mb >= 1024) return dec(mb / 1024.0, 2) + " GB";
        return dec(mb, 0) + " MB";
    }
}
