import ca.uqam.ecmonitor.Report;
import ca.uqam.ecmonitor.Station;
import ca.uqam.ecmonitor.net.Client;
import ca.uqam.ecmonitor.net.Traffic;
import ca.uqam.ecmonitor.proto.Parser;
import ca.uqam.ecmonitor.proto.Snapshot;

/**
 * Exercises the shareable report against a live simulator, and against an empty
 * snapshot, since a report is often taken the moment a station looks wrong.
 */
public class ReportTest {

    static int pass = 0, fail = 0;

    static void check(String what, boolean ok) {
        if (ok) { pass++; System.out.println("  ok   " + what); }
        else { fail++; System.out.println("  FAIL " + what); }
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 7200;

        Station st = new Station();
        st.name = "UQAM_3";
        st.host = "127.0.0.1";
        st.port = port;
        st.note = "off grid, EFOY fuel cell";

        // Empty snapshot: the report must still come out rather than throw.
        Snapshot empty = new Snapshot();
        String emptyReport = Report.build(st, empty);
        check("report from an empty snapshot", emptyReport.length() > 50);
        check("empty report names the station", emptyReport.contains("UQAM_3"));

        // Partial snapshot with odd values.
        Snapshot partial = new Snapshot();
        partial.putRecord(Parser.parse("(Data (CO2MF )(Temp abc)(Pres 98.2))"));
        partial.putRecord(Parser.parse("(Info (USB (State 3)))"));
        String partialReport = Report.build(st, partial);
        check("report survives empty and non numeric values", partialReport.contains("Measurements"));

        // Live snapshot from the simulator.
        Snapshot live = new Snapshot();
        Traffic tr = new Traffic(100);
        String err = Client.probe("127.0.0.1", port, 6000, live, tr);
        check("probe succeeded" + (err == null ? "" : ": " + err), err == null);

        String report = Report.build(st, live);
        check("report includes the model", report.contains("Model"));
        check("report includes the assessment", report.contains("Assessment:"));
        check("report includes diagnostic flags", report.contains("Diagnostic flags"));
        check("report includes SmartFlux", report.contains("SmartFlux"));
        check("report includes measurements", report.contains("CO2 mole fraction"));
        check("report is a sensible length: " + report.length(), report.length() > 800 && report.length() < 12000);

        System.out.println();
        System.out.println("---- sample report ----");
        System.out.println(report);
        System.out.println("---- end ----");
        System.out.println(pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
