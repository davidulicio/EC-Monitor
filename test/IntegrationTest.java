import java.util.List;

import ca.uqam.ecmonitor.net.Client;
import ca.uqam.ecmonitor.net.Traffic;
import ca.uqam.ecmonitor.proto.Assessment;
import ca.uqam.ecmonitor.proto.Fmt;
import ca.uqam.ecmonitor.proto.Snapshot;

/** Runs the polling client against the simulator on localhost. */
public class IntegrationTest {

    static int pass = 0, fail = 0;

    static void check(String what, boolean ok) {
        if (ok) { pass++; System.out.println("  ok   " + what); }
        else { fail++; System.out.println("  FAIL " + what); }
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 7200;
        String expectFault = args.length > 1 ? args[1] : null;

        Snapshot snap = new Snapshot();
        Traffic traffic = new Traffic(400);
        final int[] events = new int[1];

        Client c = new Client("127.0.0.1", port, 1000, snap, traffic, new Client.Listener() {
            public void onClientEvent(int state, String message) {
                events[0]++;
            }
        });
        c.start();

        long deadline = System.currentTimeMillis() + 12000;
        while (System.currentTimeMillis() < deadline) {
            if (snap.lastData() > 0 && snap.record("Fluxes") != null && snap.record("EmbeddedSW") != null) break;
            Thread.sleep(200);
        }
        Thread.sleep(1500);

        check("client reached the connected state", c.state() == Client.STATE_CONNECTED);
        check("listener fired", events[0] > 0);
        check("data arrived", snap.lastData() > 0);
        check("identity known: " + snap.model(), snap.model().length() > 0);
        check("serial known: " + snap.serial(), snap.serial().length() > 0);
        check("host name known: " + snap.hostName(), snap.hostName().length() > 0);
        check("outputs config read", snap.record("Outputs") != null);
        check("usb info read", snap.record("Info") != null);
        check("smartflux status read", snap.record("Fluxes") != null);
        check("calibration read", snap.record("Calibrate") != null);
        check("co2 present: " + snap.value("CO2MF"), snap.has("CO2MF"));
        check("signal strength: " + Fmt.dec(snap.signalStrength(), 1), Fmt.isNum(snap.signalStrength()));
        check("diagnostic flags decoded", snap.flags().size() >= 4);

        System.out.println("  -- model " + snap.model());
        System.out.println("  -- enclosed=" + snap.enclosed() + " ch4=" + snap.hasCh4() + " sonic=" + snap.hasSonic());

        List<Assessment.Issue> issues = Assessment.evaluate(snap, 30000);
        System.out.println("  -- assessment: " + Assessment.severityName(Assessment.worst(issues))
                + ", " + issues.size() + " item(s)");
        for (int i = 0; i < issues.size(); i++) {
            System.out.println("     [" + Assessment.severityName(issues.get(i).severity) + "] " + issues.get(i).title);
        }

        if (expectFault != null) {
            boolean found = false;
            for (int i = 0; i < issues.size(); i++) {
                if (issues.get(i).title.toLowerCase().contains(expectFault.toLowerCase())) found = true;
            }
            check("expected issue mentioning '" + expectFault + "'", found);
        } else {
            check("healthy station reports no fault", Assessment.worst(issues) < Assessment.CRIT);
        }

        check("read-only guard refuses a write from the console", !c.submit("(Outputs(BW 20))"));
        check("read-only guard accepts a query from the console", c.submit("(Outputs(BW ?))"));
        Thread.sleep(1200);
        String log = traffic.asText();
        check("traffic log captured both directions", log.contains(">> (Data ?)") && log.contains("<< (Data "));
        check("blocked command recorded in the log", log.contains("Refused"));

        c.stop();
        Thread.sleep(300);

        System.out.println();
        System.out.println(pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
