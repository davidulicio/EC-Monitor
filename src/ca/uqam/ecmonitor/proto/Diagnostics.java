package ca.uqam.ecmonitor.proto;

import java.util.ArrayList;
import java.util.List;

/**
 * Decoding of the gas analyzer diagnostic words.
 *
 * LI-7500 family: 1 byte, 0 to 255.
 * LI-7200 family: 2 bytes, 0 to 8191, adding differential pressure, aux input,
 * inlet and outlet thermocouple and head detect bits.
 *
 * Bit maps are taken from the "Gas analyzer diagnostics" help topics shipped
 * with the LI-COR PC software.
 */
public class Diagnostics {

    public static class Flag {
        public final String name;
        public final boolean ok;
        public final String detail;

        public Flag(String name, boolean ok, String detail) {
            this.name = name;
            this.ok = ok;
            this.detail = detail;
        }
    }

    /** Signal strength encoded in bits 0 to 3, as a percentage. */
    public static double signalStrength(int diagVal) {
        return (diagVal & 0x0F) * 6.67;
    }

    /**
     * Decodes a diagnostic value.
     *
     * @param diagVal the raw value from (Data (DiagVal ...))
     * @param enclosed true for the enclosed LI-7200 family, which uses the wider bit map
     */
    public static List<Flag> decode(int diagVal, boolean enclosed) {
        List<Flag> out = new ArrayList<Flag>();
        out.add(new Flag("Sync", bit(diagVal, 4), "Detector and chopper synchronisation"));
        out.add(new Flag("PLL", bit(diagVal, 5), "Optical chopper wheel rotating at the correct rate"));
        out.add(new Flag("Detector", bit(diagVal, 6), "Detector temperature near setpoint"));
        out.add(new Flag("Chopper", bit(diagVal, 7), "Chopper wheel temperature near setpoint"));
        if (enclosed) {
            out.add(new Flag("Differential pressure", bit(diagVal, 8), "Sensor reading 0.1 to 4.9 V"));
            out.add(new Flag("Aux input", bit(diagVal, 9), "Internal reference voltages OK, otherwise the LI-7550 needs service"));
            out.add(new Flag("Inlet thermocouple", bit(diagVal, 10), "Thermocouple continuity"));
            out.add(new Flag("Outlet thermocouple", bit(diagVal, 11), "Thermocouple continuity"));
            out.add(new Flag("Head detect", bit(diagVal, 12), "Sensor head attached to the LI-7550"));
        }
        return out;
    }

    public static boolean bit(int value, int index) {
        return ((value >> index) & 1) == 1;
    }

    /** Renders a value as a binary string of the given width, most significant bit first. */
    public static String bits(int value, int width) {
        StringBuilder sb = new StringBuilder();
        for (int i = width - 1; i >= 0; i--) {
            sb.append(bit(value, i) ? '1' : '0');
            if (i % 4 == 0 && i != 0) sb.append(' ');
        }
        return sb.toString();
    }

    /**
     * Flags from a (Diagnostics ...) record, which the instrument emits once per
     * second and which carries the same information in named boolean form.
     */
    public static List<Flag> fromRecord(Node diagnostics) {
        List<Flag> out = new ArrayList<Flag>();
        if (diagnostics == null) return out;
        addIfPresent(out, diagnostics, "SYNC", "Sync", "Detector and chopper synchronisation");
        addIfPresent(out, diagnostics, "SYNCH", "Sync", "Detector and chopper synchronisation");
        addIfPresent(out, diagnostics, "PLL", "PLL", "Optical chopper wheel rotating at the correct rate");
        addIfPresent(out, diagnostics, "DetOK", "Detector", "Detector temperature near setpoint");
        addIfPresent(out, diagnostics, "Chopper", "Chopper", "Chopper wheel temperature near setpoint");
        addIfPresent(out, diagnostics, "PDif", "Differential pressure", "Differential pressure sensor in range");
        addIfPresent(out, diagnostics, "AuxIn", "Aux input", "Internal reference voltages OK");
        addIfPresent(out, diagnostics, "Tin", "Inlet thermocouple", "Thermocouple continuity");
        addIfPresent(out, diagnostics, "Tout", "Outlet thermocouple", "Thermocouple continuity");
        addIfPresent(out, diagnostics, "Head", "Head detect", "Sensor head attached");
        return out;
    }

    private static void addIfPresent(List<Flag> out, Node parent, String tag, String label, String detail) {
        Node n = parent.child(tag);
        if (n == null) return;
        for (int i = 0; i < out.size(); i++) {
            if (out.get(i).name.equals(label)) return; // SYNC and SYNCH both appear in the trees
        }
        out.add(new Flag(label, Fmt.toBool(n.value, true), detail));
    }

    /** USB state from (Info(USB(State ...))). */
    public static String usbState(String raw) {
        double d = Fmt.toDouble(raw);
        int v = (int) d;
        if (!Fmt.isNum(d)) return "Unknown";
        switch (v) {
            case 0: return "No drive";
            case 1: return "Logging";
            case 2: return "Drive present, not logging";
            case 3: return "Error";
            default: return "Unknown (" + v + ")";
        }
    }

    public static boolean usbStateOk(String raw) {
        double d = Fmt.toDouble(raw);
        return Fmt.isNum(d) && ((int) d == 1 || (int) d == 2);
    }
}
