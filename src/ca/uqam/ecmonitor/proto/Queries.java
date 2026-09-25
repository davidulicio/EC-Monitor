package ca.uqam.ecmonitor.proto;

import java.util.ArrayList;
import java.util.List;

/** The read-only query set, plus the guard that keeps this app from writing to an instrument. */
public class Queries {

    /** Sent once on connect to identify the instrument and read its configuration. */
    public static final String[] ON_CONNECT = {
            "(EmbeddedSW ?)",
            "(Coef ?)",
            "(Network ?)",
            "(Outputs ?)",
            "(Inputs ?)",
            "(Calibrate ?)"
    };

    /** Sent every poll interval. */
    public static final String[] FAST = {
            "(Data ?)",
            "(Diagnostics ?)"
    };

    /** Sent on the slow cycle, roughly every half minute. */
    public static final String[] SLOW = {
            "(Info ?)",
            "(Fluxes(Status ?))",
            "(CH4 ?)",
            "(FlowBox ?)",
            "(Clock ?)",
            "(FluxDevices ?)",
            "(MeteoSensors ?)"
    };

    /**
     * True when the command only reads. A read is a command whose leaves are all
     * either a bare "?" or empty, with at least one "?" present. Anything that
     * carries a value would change the instrument and is refused.
     */
    public static boolean isReadOnly(String command) {
        if (command == null) return false;
        String c = command.trim();
        if (c.length() == 0) return false;
        if (!c.startsWith("(") || !c.endsWith(")")) return false;
        Node n = Parser.parse(c);
        if (n == null) return false;
        List<String[]> leaves = new ArrayList<String[]>();
        n.collectLeaves(leaves);
        boolean sawQuery = false;
        for (int i = 0; i < leaves.size(); i++) {
            String v = leaves.get(i)[1];
            if (v == null) continue;
            String t = v.trim();
            if (t.length() == 0) continue;
            if (t.equals("?")) {
                sawQuery = true;
                continue;
            }
            return false;
        }
        return sawQuery;
    }

    /**
     * Data field names enabled for the Ethernet output, in the order the
     * instrument writes them when labels are switched off.
     */
    public static String[] labelLessFields(Node outputs, boolean enclosed) {
        if (outputs == null) return new String[0];
        Node enet = outputs.child("ENet");
        if (enet == null) enet = outputs.child("RS232");
        if (enet == null) return new String[0];

        String[] order = enclosed ? Metrics.DATA_ORDER_7200 : Metrics.DATA_ORDER_7500;
        List<String> fields = new ArrayList<String>();
        for (int i = 0; i < order.length; i++) {
            Node f = enet.child(order[i]);
            if (f == null) continue;
            if (Fmt.toBool(f.value, false)) fields.add(order[i]);
        }
        return fields.toArray(new String[fields.size()]);
    }

    /** Splits a tab or space delimited data line into its values. */
    public static String[] splitValues(String line) {
        if (line == null) return new String[0];
        String t = line.trim();
        if (t.length() == 0) return new String[0];
        List<String> out = new ArrayList<String>();
        int start = -1;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            boolean sep = (c == ' ' || c == '\t');
            if (sep) {
                if (start >= 0) {
                    out.add(t.substring(start, i));
                    start = -1;
                }
            } else if (start < 0) {
                start = i;
            }
        }
        if (start >= 0) out.add(t.substring(start));
        return out.toArray(new String[out.size()]);
    }

    /** A line is plausible label-less data when every token parses as a number. */
    public static boolean looksNumeric(String line) {
        String[] parts = splitValues(line);
        if (parts.length < 3) return false;
        for (int i = 0; i < parts.length; i++) {
            if (!Fmt.isNum(Fmt.toDouble(parts[i]))) return false;
        }
        return true;
    }
}
