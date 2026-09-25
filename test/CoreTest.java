import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ca.uqam.ecmonitor.proto.Assessment;
import ca.uqam.ecmonitor.proto.Diagnostics;
import ca.uqam.ecmonitor.proto.Fmt;
import ca.uqam.ecmonitor.proto.Node;
import ca.uqam.ecmonitor.proto.Parser;
import ca.uqam.ecmonitor.proto.Queries;
import ca.uqam.ecmonitor.proto.Snapshot;

/** Checks the protocol layer against the records documented in the LI-COR help. */
public class CoreTest {

    static int pass = 0;
    static int fail = 0;

    static void check(String what, boolean ok) {
        if (ok) { pass++; System.out.println("  ok   " + what); }
        else { fail++; System.out.println("  FAIL " + what); }
    }

    static void eq(String what, String got, String want) {
        check(what + " [" + got + "]", want.equals(got));
    }

    static void near(String what, double got, double want, double tol) {
        check(what + " [" + got + "]", Math.abs(got - want) <= tol);
    }

    public static void main(String[] args) {
        System.out.println("Parser");
        String data = "(Data (Ndx 215713)(CO2Raw 1.2831902e-1)(CO2D 2.2083146e1)(H2ORaw 5.5372476e-2)"
                + "(H2OD 3.5485935e2)(Temp 2.5886261e1)(Pres 9.8157062e1)(Aux 0)(Cooler 1.0537354))";
        Node d = Parser.parse(data);
        check("data record parses", d != null && d.name.equals("Data"));
        near("CO2D", d.num("CO2D"), 22.083146, 1e-6);
        near("Pres", d.num("Pres"), 98.157062, 1e-6);
        eq("Ndx", d.str("Ndx"), "215713");

        Node diag = Parser.parse("(Diagnostics (SYNC TRUE)(PLL TRUE)(DetOK TRUE)(Chopper TRUE)(Path 63))");
        check("diagnostics parses", diag != null);
        near("Path", diag.num("Path"), 63, 1e-9);

        Node sw = Parser.parse("(EmbeddedSW (Version 4.0.0)(Model LI-7x00RS CO2/H2O Analyzer)(DSP 4.0.0)(FPGA 4.0.0|))");
        eq("model with spaces and slash", sw.str("Model"), "LI-7x00RS CO2/H2O Analyzer");
        eq("fpga with pipe", sw.str("FPGA"), "4.0.0|");

        Node outs = Parser.parse("(Outputs (BW 10)(Delay 0)(SDM (Address 7))(Dac1 (Source NONE)(Zero -5e-2)(Full 4e-1))"
                + "(RS232 (Baud 38400)(Freq 0)(Pres TRUE)(Temp TRUE)(Aux TRUE)(Cooler TRUE)(CO2Raw TRUE)(CO2D TRUE)"
                + "(H2ORaw TRUE)(H2OD TRUE)(Ndx TRUE)(DiagVal TRUE)(DiagRec FALSE)(Labels FALSE)(EOL \"0D0A\")))");
        eq("nested SDM address", outs.str("SDM", "Address"), "7");
        eq("quoted EOL unquoted", outs.str("RS232", "EOL"), "0D0A");
        eq("nested dac source", outs.str("Dac1", "Source"), "NONE");

        Node cal = Parser.parse("(Calibrate (ZeroCO2 (Val 0.8945)(Date 26 08 2009 10:37))(Span2CO2 (Val 0.0)(Target )"
                + "(Tdensity )(ic 0.106207)(act 0.105489)(Date 4Cal)))");
        eq("date with spaces", cal.str("ZeroCO2", "Date"), "26 08 2009 10:37");
        eq("empty value", cal.str("Span2CO2", "Target"), "");

        Node coef = Parser.parse("(Coef (Current (SerialNo 75H-Beta6)(Band (A 1.15))(CO2 (A 1.56704E+2)(B 2.15457E+4))"
                + "(Pressure (A0 56.129)(A1 15.250))))");
        eq("serial", coef.str("Current", "SerialNo"), "75H-Beta6");
        near("coef A", coef.num("Current", "CO2", "A"), 156.704, 1e-3);

        System.out.println("Splitter");
        Parser.Splitter sp = new Parser.Splitter();
        sp.feed("(Data (Ndx 1)(CO2D 1.0))(Data (Ndx 2)(CO2D ");
        List<String> r1 = sp.takeRecords();
        check("first of two concatenated records emitted", r1.size() == 1);
        sp.feed("2.0))\n");
        List<String> r2 = sp.takeRecords();
        check("record split across reads completes", r2.size() == 1 && r2.get(0).contains("Ndx 2"));

        sp.feed("252 250 0.15401 32.2167 0.03569 196.703 24.33 98.6 0 1.5730\n");
        List<String> raws = sp.takeRawLines();
        check("label-less line captured", raws.size() == 1);
        check("label-less line looks numeric", Queries.looksNumeric(raws.get(0)));

        sp.feed("(EOLtest (EOL \"0D0A(\"))\n");
        check("parenthesis inside quotes does not break framing", sp.takeRecords().size() == 1);

        System.out.println("Diagnostics bit maps");
        // Help example: 125 = 01111101 -> chopper not ok, signal strength 87 %
        near("7500 signal strength from 125", Diagnostics.signalStrength(125), 86.71, 0.01);
        List<Diagnostics.Flag> f125 = Diagnostics.decode(125, false);
        check("125 has 4 flags", f125.size() == 4);
        for (Diagnostics.Flag f : f125) {
            if (f.name.equals("Chopper")) check("125 chopper not ok", !f.ok);
            if (f.name.equals("Sync")) check("125 sync ok", f.ok);
            if (f.name.equals("PLL")) check("125 pll ok", f.ok);
            if (f.name.equals("Detector")) check("125 detector ok", f.ok);
        }
        // Help example: 8061 -> chopper not ok, signal strength 87 %, head detected
        near("7200 signal strength from 8061", Diagnostics.signalStrength(8061), 86.71, 0.01);
        List<Diagnostics.Flag> f8061 = Diagnostics.decode(8061, true);
        check("8061 has 9 flags", f8061.size() == 9);
        for (Diagnostics.Flag f : f8061) {
            if (f.name.equals("Chopper")) check("8061 chopper not ok", !f.ok);
            if (f.name.equals("Head detect")) check("8061 head detected", f.ok);
            if (f.name.equals("Aux input")) check("8061 aux input ok", f.ok);
            if (f.name.equals("Inlet thermocouple")) check("8061 tin ok", f.ok);
        }
        check("250 detector ok", Diagnostics.bit(250, 6));
        eq("usb state 1", Diagnostics.usbState("1"), "Logging");
        eq("usb state 3", Diagnostics.usbState("3"), "Error");
        check("usb state 3 not ok", !Diagnostics.usbStateOk("3"));

        System.out.println("Read-only guard");
        check("(Data ?) allowed", Queries.isReadOnly("(Data ?)"));
        check("(Fluxes(Status ?)) allowed", Queries.isReadOnly("(Fluxes(Status ?))"));
        check("(Outputs(RS232(Freq ?))) allowed", Queries.isReadOnly("(Outputs(RS232(Freq ?)))"));
        check("(Outputs(BW 10)) refused", !Queries.isReadOnly("(Outputs(BW 10))"));
        check("(Calibrate(ZeroCO2(Date \"now\"))) refused", !Queries.isReadOnly("(Calibrate(ZeroCO2(Date \"now\")))"));
        check("(Program(Reset TRUE)) refused", !Queries.isReadOnly("(Program(Reset TRUE))"));
        check("(Outputs(ENet(Freq 5))) refused", !Queries.isReadOnly("(Outputs(ENet(Freq 5)))"));
        check("empty refused", !Queries.isReadOnly(""));
        check("garbage refused", !Queries.isReadOnly("hello"));
        check("mixed query and set refused", !Queries.isReadOnly("(Outputs(BW 10)(Delay ?))"));

        System.out.println("Label-less field mapping");
        Node outs2 = Parser.parse("(Outputs (ENet (Freq 1)(Labels FALSE)(Ndx TRUE)(DiagVal TRUE)(CO2Raw TRUE)"
                + "(CO2D TRUE)(H2ORaw TRUE)(H2OD TRUE)(Temp TRUE)(Pres TRUE)(Aux TRUE)(Cooler TRUE)(DewPt FALSE)))");
        String[] fields = Queries.labelLessFields(outs2, false);
        StringBuilder fb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) { if (i > 0) fb.append(','); fb.append(fields[i]); }
        eq("field order matches the documented example", fb.toString(),
                "Ndx,DiagVal,CO2Raw,CO2D,H2ORaw,H2OD,Temp,Pres,Aux,Cooler");
        String[] vals = Queries.splitValues("252 250 0.15401 32.2167 0.03569 196.703 24.33 98.6 0 1.5730");
        check("ten values split", vals.length == 10);
        Snapshot lsnap = new Snapshot();
        lsnap.putLabelLess(fields, vals);
        near("label-less CO2D", lsnap.number("CO2D"), 32.2167, 1e-4);
        near("label-less Cooler", lsnap.number("Cooler"), 1.5730, 1e-4);
        check("label-less flagged", lsnap.labelLess());

        System.out.println("Snapshot and assessment");
        Snapshot s = new Snapshot();
        s.putRecord(Parser.parse("(EmbeddedSW (Version 8.8.0)(Model LI-7200RS Enclosed CO2/H2O Analyzer))"));
        s.putRecord(Parser.parse("(Coef (Current (SerialNo 72H-0421)))"));
        s.putRecord(Parser.parse("(Network (Name UQAM-3-7550))"));
        check("enclosed detected from model", s.enclosed());
        eq("serial surfaced", s.serial(), "72H-0421");
        eq("host name surfaced", s.hostName(), "UQAM-3-7550");

        s.putRecord(Parser.parse("(Data (Ndx 10)(DiagVal 8061)(CO2MF 412.5)(H2OMF 11.2)(Temp 21.5)(Pres 98.2)"
                + "(AvgSS 62.0)(DeltaSS 1.2)(Cooler 1.4)(MeasFlowRate 14.8)(FlowDrive 45)(DPres 2.1)"
                + "(TempIn 21.4)(TempOut 21.8))"));
        s.putRecord(Parser.parse("(Diagnostics (SYNCH TRUE)(PLL TRUE)(DetOK TRUE)(Chopper FALSE)(PDif TRUE)"
                + "(AuxIn TRUE)(Tin TRUE)(Tout TRUE)(Head 1))"));
        s.putRecord(Parser.parse("(Info (USB (Size 32000)(Free 120)(State 1)))"));

        List<Assessment.Issue> issues = Assessment.evaluate(s, 60000);
        boolean chopper = false, ss = false, usb = false;
        for (int i = 0; i < issues.size(); i++) {
            String t = issues.get(i).title;
            if (t.contains("Chopper")) chopper = true;
            if (t.contains("Signal strength")) ss = true;
            if (t.contains("USB free space")) usb = true;
        }
        check("chopper fault raised from the named record", chopper);
        check("signal strength warning at 62 %", ss);
        check("usb nearly full raised", usb);
        check("worst severity is a fault", Assessment.worst(issues) == Assessment.CRIT);
        check("issues are sorted, faults first", issues.get(0).severity == Assessment.CRIT);

        Snapshot healthy = new Snapshot();
        healthy.putRecord(Parser.parse("(EmbeddedSW (Model LI-7500DS Open Path Analyzer))"));
        healthy.putRecord(Parser.parse("(Data (DiagVal 250)(CO2MF 415.0)(Temp 18.0)(Pres 99.0)(AvgSS 95.0)(Cooler 1.1))"));
        healthy.putRecord(Parser.parse("(Info (USB (Size 32000)(Free 20000)(State 1)))"));
        List<Assessment.Issue> none = Assessment.evaluate(healthy, 60000);
        check("healthy open path station is clean", Assessment.worst(none) == Assessment.OK);
        check("open path is not treated as enclosed", !healthy.enclosed());
        near("signal strength falls back to AvgSS", healthy.signalStrength(), 95.0, 1e-9);

        Snapshot ch4 = new Snapshot();
        ch4.putRecord(Parser.parse("(CH4Data (CH4 1.95)(CH4D 0.0812)(RSSI 8.5)(DROPRATE 0.2)(DIAG 0))"));
        check("ch4 presence detected", ch4.hasCh4());
        List<Assessment.Issue> ch4Issues = Assessment.evaluate(ch4, 600000);
        boolean rssi = false;
        for (int i = 0; i < ch4Issues.size(); i++) if (ch4Issues.get(i).title.contains("RSSI")) rssi = true;
        check("low LI-7700 RSSI raised", rssi);

        Snapshot flow = new Snapshot();
        flow.putRecord(Parser.parse("(EmbeddedSW (Model LI-7200RS))"));
        flow.putRecord(Parser.parse("(Data (DiagVal 8189)(MeasFlowRate 0.0)(FlowDrive 100)(Pres 98)(Temp 20))"));
        List<Assessment.Issue> flowIssues = Assessment.evaluate(flow, 600000);
        boolean zeroFlow = false;
        for (int i = 0; i < flowIssues.size(); i++) if (flowIssues.get(i).title.contains("Flow rate")) zeroFlow = true;
        check("zero flow raised as a fault", zeroFlow);

        Snapshot silent = new Snapshot();
        silent.putRecord(Parser.parse("(EmbeddedSW (Model LI-7500DS))"));
        silent.putRecord(Parser.parse("(Outputs (BW 10)(ENet (Freq 0)(Labels TRUE)(CO2D TRUE)))"));
        List<Assessment.Issue> silentIssues = Assessment.evaluate(silent, 60000);
        boolean enetOff = false;
        for (int i = 0; i < silentIssues.size(); i++) {
            if (silentIssues.get(i).title.contains("Ethernet output rate is 0")) enetOff = true;
        }
        check("silent instrument with Freq 0 is explained", enetOff);

        Snapshot talking = new Snapshot();
        talking.putRecord(Parser.parse("(Outputs (ENet (Freq 5)))"));
        boolean generic = false;
        List<Assessment.Issue> talkIssues = Assessment.evaluate(talking, 60000);
        for (int i = 0; i < talkIssues.size(); i++) {
            if (talkIssues.get(i).title.contains("No measurements yet")) generic = true;
        }
        check("silent instrument with a live output rate gets the other message", generic);

        System.out.println("Flux file age");
        check("ghg file name parses", Assessment.parseFileDate("2026-05-27T103000_AIU-1234.ghg") != null);
        check("short form parses", Assessment.parseFileDate("2026-05-27T1030") != null);
        check("nonsense returns null", Assessment.parseFileDate("no-timestamp-here") == null);

        System.out.println("Tag table");
        eq("Temp is the analyzer block temperature", ca.uqam.ecmonitor.proto.Metrics.label("Temp"),
                "Block temperature");
        eq("TEMP is the LI-7700 temperature", ca.uqam.ecmonitor.proto.Metrics.label("TEMP"),
                "LI-7700 temperature");
        eq("Temp is grouped with the cell", ca.uqam.ecmonitor.proto.Metrics.group("Temp"), "Cell");
        eq("TEMP is grouped with the methane analyzer",
                ca.uqam.ecmonitor.proto.Metrics.group("TEMP"), "LI-7700 CH4");
        eq("Pres stays the analyzer pressure", ca.uqam.ecmonitor.proto.Metrics.label("Pres"),
                "Total pressure");
        eq("PRESSURE is the LI-7700 pressure", ca.uqam.ecmonitor.proto.Metrics.label("PRESSURE"),
                "LI-7700 pressure");
        eq("unknown tags fall back to the raw name",
                ca.uqam.ecmonitor.proto.Metrics.label("SomethingNew"), "SomethingNew");
        check("case-insensitive fallback still works for stray case",
                ca.uqam.ecmonitor.proto.Metrics.known("co2mf"));

        Snapshot both = new Snapshot();
        both.putRecord(Parser.parse("(Data (Temp 21.4)(Pres 98.1))"));
        both.putRecord(Parser.parse("(CH4Data (TEMP 15.2)(PRESSURE 97.9)(RSSI 62))"));
        near("analyzer temperature kept separate", both.number("Temp"), 21.4, 1e-6);
        near("methane temperature kept separate", both.number("TEMP"), 15.2, 1e-6);

        System.out.println("Formatting");
        eq("dec rounds", Fmt.dec(412.456, 2), "412.46");
        eq("dec pads", Fmt.dec(1.5, 3), "1.500");
        eq("dec negative", Fmt.dec(-0.25, 1), "-0.3");
        eq("dec NaN", Fmt.dec(Double.NaN, 2), "--");
        eq("since minutes", Fmt.since(125000), "2 m 5 s");
        eq("since hours", Fmt.since(7500000), "2 h 5 m");
        eq("mb to gb", Fmt.bytesMb(2048), "2.00 GB");

        System.out.println();
        System.out.println(pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
