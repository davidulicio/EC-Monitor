package ca.uqam.ecmonitor;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ca.uqam.ecmonitor.proto.Assessment;
import ca.uqam.ecmonitor.proto.Diagnostics;
import ca.uqam.ecmonitor.proto.Fmt;
import ca.uqam.ecmonitor.proto.Metrics;
import ca.uqam.ecmonitor.proto.Node;
import ca.uqam.ecmonitor.proto.Snapshot;

/** Plain text snapshot of a station, for sharing by email or message. */
public class Report {

    public static String build(Station station, Snapshot s) {
        StringBuilder b = new StringBuilder();
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());

        b.append("EC station check\n");
        b.append("Station : ").append(station.title()).append('\n');
        b.append("Address : ").append(station.address()).append('\n');
        b.append("Taken   : ").append(now).append('\n');
        if (station.note != null && station.note.length() > 0) {
            b.append("Note    : ").append(station.note).append('\n');
        }
        b.append('\n');

        if (s.model().length() > 0) b.append("Model    : ").append(s.model()).append('\n');
        if (s.serial().length() > 0) b.append("Head s/n : ").append(s.serial()).append('\n');
        if (s.firmware().length() > 0) b.append("Firmware : ").append(s.firmware()).append('\n');
        if (s.hostName().length() > 0) b.append("Hostname : ").append(s.hostName()).append('\n');
        if (s.lastData() > 0) {
            b.append("Last data: ").append(Fmt.since(System.currentTimeMillis() - s.lastData())).append(" ago\n");
        }
        b.append('\n');

        List<Assessment.Issue> issues = Assessment.evaluate(s, 60000L);
        b.append("Assessment: ").append(Assessment.severityName(Assessment.worst(issues)));
        b.append(" (").append(issues.size()).append(issues.size() == 1 ? " item)" : " items)").append("\n");
        if (issues.isEmpty()) {
            b.append("  Nothing flagged.\n");
        } else {
            for (int i = 0; i < issues.size(); i++) {
                Assessment.Issue is = issues.get(i);
                b.append("  [").append(Assessment.severityName(is.severity)).append("] ")
                        .append(is.title).append('\n');
                b.append("      ").append(is.detail).append('\n');
            }
        }
        b.append('\n');

        List<Diagnostics.Flag> flags = s.flags();
        if (!flags.isEmpty()) {
            b.append("Diagnostic flags\n");
            for (int i = 0; i < flags.size(); i++) {
                Diagnostics.Flag f = flags.get(i);
                b.append("  ").append(f.ok ? "ok   " : "FAULT").append("  ").append(f.name).append('\n');
            }
            String dv = s.value("DiagVal");
            if (dv != null) {
                double d = Fmt.toDouble(dv);
                if (Fmt.isNum(d)) {
                    b.append("  DiagVal ").append(dv).append("  (")
                            .append(Diagnostics.bits((int) d, s.enclosed() ? 16 : 8)).append(")\n");
                }
            }
            b.append('\n');
        }

        Node fluxes = s.record("Fluxes");
        if (fluxes != null) {
            b.append("SmartFlux\n");
            appendIf(b, "  Model      : ", fluxes.str("Status", "SmartFlux", "Model"));
            appendIf(b, "  Serial     : ", fluxes.str("Status", "SmartFlux", "SerialNo"));
            appendIf(b, "  Version    : ", fluxes.str("Status", "SmartFlux", "Version"));
            appendIf(b, "  EddyPro    : ", fluxes.str("Status", "SmartFlux", "EPVersion"));
            appendIf(b, "  Input volts: ", fluxes.str("Status", "SmartFlux", "Vin"));
            appendIf(b, "  Satellites : ", fluxes.str("Status", "SmartFlux", "GPS", "NumSat"));
            appendIf(b, "  Run status : ", fluxes.str("Status", "EddyPro", "RunStatus"));
            String lf = fluxes.str("Status", "EddyPro", "LastFile");
            if (lf != null && lf.length() > 0) {
                b.append("  Last file  : ").append(lf);
                long age = Assessment.fileAgeMillis(lf);
                if (age > 0) b.append("  (").append(Fmt.since(age)).append(" ago)");
                b.append('\n');
            }
            b.append('\n');
        }

        Node info = s.record("Info");
        if (info != null) {
            b.append("Logging drive\n");
            b.append("  State: ").append(Diagnostics.usbState(info.str("USB", "State"))).append('\n');
            double free = Fmt.toDouble(info.str("USB", "Free"));
            double size = Fmt.toDouble(info.str("USB", "Size"));
            if (Fmt.isNum(free)) b.append("  Free : ").append(Fmt.bytesMb(free)).append('\n');
            if (Fmt.isNum(size)) b.append("  Size : ").append(Fmt.bytesMb(size)).append('\n');
            b.append('\n');
        }

        b.append("Measurements\n");
        Map<String, String> data = s.dataCopy();
        for (int g = 0; g < Metrics.GROUP_ORDER.length; g++) {
            String group = Metrics.GROUP_ORDER[g];
            boolean wroteHeader = false;
            for (Map.Entry<String, String> e : data.entrySet()) {
                if (!Metrics.group(e.getKey()).equals(group)) continue;
                if (!wroteHeader) {
                    b.append("  ").append(group).append('\n');
                    wroteHeader = true;
                }
                b.append("    ").append(pad(Metrics.label(e.getKey()), 28)).append(' ');
                double v = Fmt.toDouble(e.getValue());
                b.append(Fmt.isNum(v) ? Fmt.dec(v, Metrics.decimals(e.getKey())) : e.getValue());
                String unit = Metrics.unit(e.getKey());
                if (unit.length() > 0) b.append(' ').append(unit);
                b.append('\n');
            }
        }

        b.append("\nProduced by EC Monitor, read-only check over the LI-COR grammar on port ")
                .append(station.port).append(".\n");
        return b.toString();
    }

    private static void appendIf(StringBuilder b, String label, String value) {
        if (value == null || value.length() == 0) return;
        b.append(label).append(value).append('\n');
    }

    private static String pad(String s, int width) {
        StringBuilder sb = new StringBuilder(s == null ? "" : s);
        while (sb.length() < width) sb.append(' ');
        return sb.toString();
    }
}
