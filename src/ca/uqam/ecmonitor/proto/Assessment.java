package ca.uqam.ecmonitor.proto;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Turns a snapshot into a ranked list of things worth looking at, with the
 * check to make next. Thresholds follow the guidance in the LI-COR help and
 * ordinary field practice; they are starting points, not gospel.
 */
public class Assessment {

    public static final int OK = 0;
    public static final int INFO = 1;
    public static final int WARN = 2;
    public static final int CRIT = 3;

    public static class Issue {
        public final int severity;
        public final String title;
        public final String detail;

        public Issue(int severity, String title, String detail) {
            this.severity = severity;
            this.title = title;
            this.detail = detail;
        }
    }

    /** Overall severity, highest wins. */
    public static int worst(List<Issue> issues) {
        int w = OK;
        for (int i = 0; i < issues.size(); i++) {
            if (issues.get(i).severity > w) w = issues.get(i).severity;
        }
        return w;
    }

    public static String severityName(int s) {
        switch (s) {
            case CRIT: return "Fault";
            case WARN: return "Warning";
            case INFO: return "Note";
            default: return "OK";
        }
    }

    public static List<Issue> evaluate(Snapshot s, long staleAfterMillis) {
        List<Issue> out = new ArrayList<Issue>();
        if (s == null) return out;

        long age = s.lastData() == 0 ? -1 : System.currentTimeMillis() - s.lastData();
        if (s.lastData() == 0) {
            Node outputs = s.record("Outputs");
            double enetFreq = outputs == null ? Double.NaN : Fmt.toDouble(outputs.str("ENet", "Freq"));
            if (outputs != null && Fmt.isNum(enetFreq) && enetFreq <= 0) {
                out.add(new Issue(CRIT, "No data, Ethernet output rate is 0",
                        "The instrument answers configuration queries but its Ethernet output is switched off and it is not answering (Data ?) either. The PC software sets that rate when it connects; this app never changes a setting, so the rate has to be set once from the PC software or the instrument's own interface."));
            } else if (outputs != null) {
                out.add(new Issue(CRIT, "No measurements yet",
                        "The instrument is talking but has not returned a data record. Give it a few seconds, then check the Console tab to see what it answers to (Data ?)."));
            } else {
                out.add(new Issue(CRIT, "No data received",
                        "Connected but nothing has come back. Check that the analyzer is powered and that port " + "7200 reaches this address."));
            }
        } else if (age > staleAfterMillis) {
            out.add(new Issue(CRIT, "Data stale",
                    "Last data record " + Fmt.since(age) + " ago. The link or the analyzer has stopped responding."));
        }

        boolean enclosed = s.enclosed();

        // Diagnostic flags
        List<Diagnostics.Flag> flags = s.flags();
        for (int i = 0; i < flags.size(); i++) {
            Diagnostics.Flag f = flags.get(i);
            if (f.ok) continue;
            if (f.name.equals("Head detect")) {
                if (enclosed) {
                    out.add(new Issue(CRIT, "Sensor head not detected",
                            "The LI-7550 does not see the analyzer head. Check the head cable at both ends."));
                }
                continue;
            }
            if (f.name.equals("PLL")) {
                out.add(new Issue(CRIT, "PLL not locked",
                        "The optical chopper is not turning at the correct rate. Data are not usable. This usually means a chopper motor or main board fault."));
            } else if (f.name.equals("Chopper")) {
                out.add(new Issue(CRIT, "Chopper temperature off setpoint",
                        "Chopper housing is not at its control temperature. Common after a cold start, so recheck in 15 minutes; if it persists, the thermal control has failed."));
            } else if (f.name.equals("Detector")) {
                out.add(new Issue(CRIT, "Detector temperature off setpoint",
                        "Detector cooler cannot hold setpoint. Check the cooler voltage below, and whether the enclosure is overheating."));
            } else if (f.name.equals("Sync")) {
                out.add(new Issue(CRIT, "Sync fault",
                        "Detector and chopper are out of synchronisation. Data are not usable."));
            } else if (f.name.equals("Differential pressure")) {
                out.add(new Issue(WARN, "Differential pressure sensor out of range",
                        "Sensor is outside 0.1 to 4.9 V. Check the pressure tubing to the head and the sensor itself."));
            } else if (f.name.equals("Aux input")) {
                out.add(new Issue(CRIT, "Internal reference voltages bad",
                        "The LI-7550 reports its internal references are out of range and needs service."));
            } else if (f.name.equals("Inlet thermocouple")) {
                out.add(new Issue(WARN, "Inlet thermocouple open circuit",
                        "Tin is not reading. Cell temperature and the resulting fluxes will be wrong. Check the thermocouple wiring at the head."));
            } else if (f.name.equals("Outlet thermocouple")) {
                out.add(new Issue(WARN, "Outlet thermocouple open circuit",
                        "Tout is not reading. Check the thermocouple wiring at the head."));
            } else {
                out.add(new Issue(WARN, f.name + " fault", f.detail));
            }
        }

        // Signal strength
        double ss = s.signalStrength();
        if (Fmt.isNum(ss)) {
            if (ss < 40) {
                out.add(new Issue(CRIT, "Signal strength " + Fmt.dec(ss, 0) + " %",
                        "The optical path is heavily obstructed or the source is failing. Clean the windows; if cleaning does not recover it, suspect the source or detector."));
            } else if (ss < 70) {
                out.add(new Issue(WARN, "Signal strength " + Fmt.dec(ss, 0) + " %",
                        "Below the usual working range. Plan a window cleaning. Rain or snow in the path gives a temporary dip."));
            }
        }

        double dss = s.number("DeltaSS");
        if (Fmt.isNum(dss) && Math.abs(dss) > 5) {
            out.add(new Issue(WARN, "Delta signal strength " + Fmt.dec(dss, 1),
                    "Contamination is spectrally uneven, which shifts the calibration rather than just dimming the signal. Clean the optics and consider a zero and span."));
        }

        // Detector cooler
        double cooler = s.number("Cooler");
        if (Fmt.isNum(cooler)) {
            if (cooler > 2.6) {
                out.add(new Issue(WARN, "Detector cooler at " + Fmt.dec(cooler, 2) + " V",
                        "The cooler is working hard. Usually a hot enclosure or a failing cooler. Check enclosure ventilation."));
            } else if (cooler < 0.05) {
                out.add(new Issue(WARN, "Detector cooler near zero",
                        "Cooler drive is essentially off, which is unusual while the detector is in control."));
            }
        }

        // Enclosed path: flow module and cell pressure drop
        if (enclosed) {
            double flow = s.number("MeasFlowRate");
            if (Fmt.isNum(flow)) {
                if (flow < 1.0) {
                    out.add(new Issue(CRIT, "Flow rate " + Fmt.dec(flow, 2) + " SLPM",
                            "Essentially no flow. Check that the pump is connected to the flow module and running, and that the intake is not blocked. A disconnected flow module reports zero here."));
                } else if (flow < 10) {
                    out.add(new Issue(WARN, "Flow rate " + Fmt.dec(flow, 2) + " SLPM",
                            "Below the usual 15 SLPM working point. Suspect a clogged intake filter or a tiring pump."));
                }
            }
            double drive = s.number("FlowDrive");
            if (Fmt.isNum(drive) && drive > 90) {
                out.add(new Issue(WARN, "Pump drive at " + Fmt.dec(drive, 0) + " %",
                        "The pump is near full drive to hold the setpoint. Replace the intake filter; if that does not help the pump is wearing out."));
            }
            double dp = s.number("DPres");
            if (Fmt.isNum(dp) && Math.abs(dp) > 6) {
                out.add(new Issue(WARN, "Differential pressure " + Fmt.dec(dp, 2) + " kPa",
                        "Large pressure drop across the cell, typically a clogged filter or intake screen."));
            }
            double tin = s.number("TempIn");
            double tout = s.number("TempOut");
            if (Fmt.isNum(tin) && Fmt.isNum(tout) && Math.abs(tin - tout) > 5) {
                out.add(new Issue(INFO, "Inlet and outlet differ by " + Fmt.dec(Math.abs(tin - tout), 1) + " C",
                        "A large gradient across the cell affects the density corrections. Check the intake tube heater if one is fitted."));
            }
        }

        // Cell pressure sanity
        double pres = s.number("Pres");
        if (Fmt.isNum(pres) && (pres < 50 || pres > 110)) {
            out.add(new Issue(WARN, "Cell pressure " + Fmt.dec(pres, 1) + " kPa",
                    "Outside the range expected at a lowland site. Check the pressure sensor and its tubing."));
        }

        // Gas values that look impossible
        double co2 = s.number("CO2MF");
        if (Fmt.isNum(co2) && (co2 < 250 || co2 > 1200)) {
            out.add(new Issue(WARN, "CO2 reading " + Fmt.dec(co2, 1) + " umol/mol",
                    "Well outside a normal ambient range. Suspect calibration drift, contamination, or a zero and span that did not take."));
        }

        // USB logging
        Node info = s.record("Info");
        if (info != null) {
            String state = info.str("USB", "State");
            if (state != null && !Diagnostics.usbStateOk(state)) {
                out.add(new Issue(CRIT, "USB drive " + Diagnostics.usbState(state).toLowerCase(),
                        "Logging cannot continue without a working drive. This is the usual reason a site stops producing files."));
            }
            double free = Fmt.toDouble(info.str("USB", "Free"));
            double size = Fmt.toDouble(info.str("USB", "Size"));
            if (Fmt.isNum(free) && Fmt.isNum(size) && size > 0) {
                double pct = 100.0 * free / size;
                if (free < 200) {
                    out.add(new Issue(CRIT, "USB free space " + Fmt.bytesMb(free),
                            "The drive is about to fill. Swap or clear it before the next site visit."));
                } else if (pct < 15) {
                    out.add(new Issue(WARN, "USB " + Fmt.dec(pct, 0) + " % free",
                            "Plan a drive swap."));
                }
            }
        }

        // SmartFlux
        Node fluxes = s.record("Fluxes");
        if (fluxes != null) {
            double vin = Fmt.toDouble(fluxes.str("Status", "SmartFlux", "Vin"));
            if (Fmt.isNum(vin) && vin > 0 && vin < 11.5) {
                out.add(new Issue(CRIT, "SmartFlux input " + Fmt.dec(vin, 2) + " V",
                        "Supply is low enough to cause resets and lost files. Check the battery bank and the regulator."));
            }
            double sats = Fmt.toDouble(fluxes.str("Status", "SmartFlux", "GPS", "NumSat"));
            if (Fmt.isNum(sats) && sats < 4) {
                out.add(new Issue(WARN, "GPS satellites " + Fmt.dec(sats, 0),
                        "Fewer than four satellites. Timestamps may drift. Check the antenna is outside and its cable is intact."));
            }
            String lastFile = fluxes.str("Status", "EddyPro", "LastFile");
            long fileAge = fileAgeMillis(lastFile);
            if (fileAge > 0) {
                if (fileAge > 3 * 3600000L) {
                    out.add(new Issue(CRIT, "No new flux file for " + Fmt.since(fileAge),
                            "EddyPro should produce one file every half hour. This is the signature of a SmartFlux that has stopped writing and usually needs a power cycle."));
                } else if (fileAge > 75 * 60000L) {
                    out.add(new Issue(WARN, "Last flux file " + Fmt.since(fileAge) + " ago",
                            "More than two processing intervals have passed without a new file."));
                }
            }
            String run = fluxes.str("Status", "EddyPro", "RunStatus");
            if (run != null && run.length() > 0
                    && (run.toLowerCase().contains("error") || run.toLowerCase().contains("fail"))) {
                out.add(new Issue(WARN, "EddyPro run status: " + run,
                        "The last processing run did not complete cleanly."));
            }
        }

        // LI-7700
        if (s.hasCh4()) {
            double rssi = s.number("RSSI");
            if (Fmt.isNum(rssi)) {
                if (rssi < 10) {
                    out.add(new Issue(CRIT, "LI-7700 RSSI " + Fmt.dec(rssi, 0) + " %",
                            "The methane analyzer has lost its return signal. Mirrors need cleaning, or the washer and spinning motor are not working."));
                } else if (rssi < 20) {
                    out.add(new Issue(WARN, "LI-7700 RSSI " + Fmt.dec(rssi, 0) + " %",
                            "Mirror reflectivity is getting low. Schedule a mirror cleaning."));
                }
            }
            double drop = s.number("DROPRATE");
            if (Fmt.isNum(drop) && drop > 5) {
                out.add(new Issue(WARN, "LI-7700 dropping " + Fmt.dec(drop, 1) + " % of samples",
                        "Sample loss at this level will bias the methane flux. Check the network link to the LI-7700."));
            }
        }

        // Sonic
        double anem = s.number("AnemDiag");
        if (Fmt.isNum(anem) && anem != 0) {
            out.add(new Issue(WARN, "Anemometer diagnostic " + Fmt.dec(anem, 0),
                    "The sonic is flagging a problem. Rain or ice on the transducers is the usual cause; a persistent flag means a failing head."));
        }

        if (s.labelLess()) {
            out.add(new Issue(INFO, "Instrument is in label-less output mode",
                    "The Ethernet output has (Labels FALSE) set, so field names are inferred from the output configuration and could be misaligned. The Console tab shows the raw lines."));
        }

        sortBySeverity(out);
        return out;
    }

    private static void sortBySeverity(List<Issue> list) {
        // Simple insertion sort, highest severity first; the list is always short.
        for (int i = 1; i < list.size(); i++) {
            Issue cur = list.get(i);
            int j = i - 1;
            while (j >= 0 && list.get(j).severity < cur.severity) {
                list.set(j + 1, list.get(j));
                j--;
            }
            list.set(j + 1, cur);
        }
    }

    /**
     * Age of the last GHG file, from names such as
     * 2026-05-27T103000_AIU-1234.ghg or 2026-05-27T1030. Returns -1 when the
     * name carries no readable timestamp.
     */
    public static long fileAgeMillis(String lastFile) {
        Date d = parseFileDate(lastFile);
        if (d == null) return -1;
        long age = System.currentTimeMillis() - d.getTime();
        return age < 0 ? -1 : age;
    }

    public static Date parseFileDate(String lastFile) {
        if (lastFile == null) return null;
        String s = lastFile.trim();
        if (s.length() < 15) return null;
        int t = s.indexOf('T');
        if (t < 10) return null;
        String candidate = s.substring(t - 10);
        if (candidate.length() > 17) candidate = candidate.substring(0, 17);
        String[] patterns = {"yyyy-MM-dd'T'HHmmss", "yyyy-MM-dd'T'HHmm", "yyyy-MM-dd'T'HH:mm"};
        for (int i = 0; i < patterns.length; i++) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(patterns[i], Locale.US);
                f.setLenient(false);
                Date d = f.parse(candidate, new ParsePosition(0));
                if (d != null) return d;
            } catch (RuntimeException ignored) {
                // try the next pattern
            }
        }
        return null;
    }
}
