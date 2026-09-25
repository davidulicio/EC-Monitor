package ca.uqam.ecmonitor.proto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Merged view of everything one station has told us: the latest data values,
 * the last diagnostics record, and the configuration and status records that
 * are queried less often.
 *
 * Thread safety: written by the polling thread, read on the UI thread, so all
 * access goes through synchronized methods and readers get copies.
 */
public class Snapshot {

    private final Map<String, String> data = new LinkedHashMap<String, String>();
    private final Map<String, Long> dataStamp = new LinkedHashMap<String, Long>();
    private final Map<String, Node> records = new LinkedHashMap<String, Node>();

    private long lastDataMillis = 0;
    private long lastAnyMillis = 0;
    private boolean labelLessSeen = false;

    public synchronized void putRecord(Node n) {
        if (n == null || n.name.length() == 0) return;
        long now = System.currentTimeMillis();
        lastAnyMillis = now;

        String key = n.name;
        if (key.equalsIgnoreCase("Data") || key.equalsIgnoreCase("CH4Data")
                || key.equalsIgnoreCase("SonicData")) {
            List<String[]> leaves = new ArrayList<String[]>();
            n.collectLeaves(leaves);
            for (int i = 0; i < leaves.size(); i++) {
                String tag = leaves.get(i)[0];
                String val = leaves.get(i)[1];
                if (val == null || val.length() == 0) continue;
                data.put(tag, val);
                dataStamp.put(tag, now);
            }
            if (!leaves.isEmpty()) lastDataMillis = now;
            return;
        }
        records.put(key, n);
    }

    /** Values decoded from a label-less (tab delimited) line. */
    public synchronized void putLabelLess(String[] tags, String[] values) {
        long now = System.currentTimeMillis();
        labelLessSeen = true;
        int n = Math.min(tags.length, values.length);
        for (int i = 0; i < n; i++) {
            data.put(tags[i], values[i]);
            dataStamp.put(tags[i], now);
        }
        if (n > 0) {
            lastDataMillis = now;
            lastAnyMillis = now;
        }
    }

    public synchronized String value(String tag) {
        return data.get(tag);
    }

    public synchronized double number(String tag) {
        return Fmt.toDouble(data.get(tag));
    }

    public synchronized boolean has(String tag) {
        return data.containsKey(tag);
    }

    public synchronized Map<String, String> dataCopy() {
        return new LinkedHashMap<String, String>(data);
    }

    public synchronized Node record(String name) {
        return records.get(name);
    }

    public synchronized List<String> recordNames() {
        return new ArrayList<String>(records.keySet());
    }

    public synchronized long lastData() {
        return lastDataMillis;
    }

    public synchronized long lastAnything() {
        return lastAnyMillis;
    }

    public synchronized boolean labelLess() {
        return labelLessSeen;
    }

    public synchronized void clear() {
        data.clear();
        dataStamp.clear();
        records.clear();
        lastDataMillis = 0;
        lastAnyMillis = 0;
        labelLessSeen = false;
    }

    // ---- derived identity -------------------------------------------------

    public synchronized String model() {
        Node sw = records.get("EmbeddedSW");
        String m = sw == null ? null : sw.str("Model");
        return m == null ? "" : m;
    }

    public synchronized String firmware() {
        Node sw = records.get("EmbeddedSW");
        String v = sw == null ? null : sw.str("Version");
        return v == null ? "" : v;
    }

    public synchronized String serial() {
        Node coef = records.get("Coef");
        String s = coef == null ? null : coef.str("Current", "SerialNo");
        return s == null ? "" : s;
    }

    public synchronized String hostName() {
        Node net = records.get("Network");
        String s = net == null ? null : net.str("Name");
        return s == null ? "" : s;
    }

    /**
     * True for the enclosed LI-7200 family, which uses the wider diagnostic bit
     * map and has a flow module. Decided from the model string when available,
     * otherwise from the presence of enclosed-only measurements.
     */
    public synchronized boolean enclosed() {
        String m = model().toLowerCase();
        if (m.contains("7200")) return true;
        if (m.contains("7500")) return false;
        return data.containsKey("TempIn") || data.containsKey("MeasFlowRate") || data.containsKey("DPres");
    }

    public synchronized boolean hasCh4() {
        if (data.containsKey("RSSI") || data.containsKey("CH4D") || data.containsKey("CH4")) return true;
        Node ch4 = records.get("CH4");
        if (ch4 == null) return false;
        String head = ch4.str("Head");
        return head != null && head.length() > 0 && !head.equalsIgnoreCase("None");
    }

    public synchronized boolean hasSonic() {
        return data.containsKey("U") || data.containsKey("TS") || data.containsKey("SOS");
    }

    /** Best available signal strength percentage, whichever field the instrument reports. */
    public synchronized double signalStrength() {
        double v = number("AvgSS");
        if (Fmt.isNum(v)) return v;
        v = number("Path");
        if (Fmt.isNum(v)) return v;
        Node d = records.get("Diagnostics");
        if (d != null) {
            v = Fmt.toDouble(d.str("Path"));
            if (Fmt.isNum(v)) return v;
        }
        double dv = number("DiagVal");
        if (Fmt.isNum(dv)) return Diagnostics.signalStrength((int) dv);
        return Double.NaN;
    }

    /** Diagnostic flags, preferring the named record and falling back to the bit field. */
    public synchronized List<Diagnostics.Flag> flags() {
        Node d = records.get("Diagnostics");
        List<Diagnostics.Flag> f = Diagnostics.fromRecord(d);
        if (!f.isEmpty()) return f;
        double dv = number("DiagVal");
        if (Fmt.isNum(dv)) return Diagnostics.decode((int) dv, enclosed());
        return new ArrayList<Diagnostics.Flag>();
    }
}
