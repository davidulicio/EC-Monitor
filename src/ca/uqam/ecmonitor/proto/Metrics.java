package ca.uqam.ecmonitor.proto;

import java.util.HashMap;
import java.util.Map;

/**
 * Catalogue of data tags: display label, unit, decimals and grouping.
 * Tag names follow the instrument grammar exactly (case sensitive on the wire,
 * matched case-insensitively here).
 */
public class Metrics {

    public static final String G_GAS = "Gas";
    public static final String G_CELL = "Cell";
    public static final String G_SIGNAL = "Signal";
    public static final String G_FLOW = "Flow module";
    public static final String G_SONIC = "Sonic anemometer";
    public static final String G_CH4 = "LI-7700 CH4";
    public static final String G_AUX = "Auxiliary";
    public static final String G_SYS = "System";
    public static final String G_OTHER = "Other";

    /**
     * Two lookups: the grammar is case sensitive, and some tags differ only by
     * case. Temp is the analyzer block temperature while TEMP is the LI-7700
     * temperature, so an exact match has to win before falling back to a
     * case-insensitive one.
     */
    private static final Map<String, String[]> EXACT = new HashMap<String, String[]>();
    private static final Map<String, String[]> LOOSE = new HashMap<String, String[]>();

    // {label, unit, decimals, group}
    private static void m(String tag, String label, String unit, int dec, String group) {
        String[] row = new String[]{label, unit, Integer.toString(dec), group};
        EXACT.put(tag, row);
        String lower = tag.toLowerCase();
        if (!LOOSE.containsKey(lower)) LOOSE.put(lower, row);
    }

    private static String[] lookup(String tag) {
        if (tag == null) return null;
        String[] r = EXACT.get(tag);
        if (r != null) return r;
        return LOOSE.get(tag.toLowerCase());
    }

    static {
        m("Ndx", "Index", "", 0, G_SYS);
        m("Time", "Instrument time", "", 0, G_SYS);
        m("Date", "Instrument date", "", 0, G_SYS);
        m("SECONDS", "Timestamp seconds", "s", 0, G_SYS);
        m("NANOSECONDS", "Timestamp nanoseconds", "ns", 0, G_SYS);

        m("CO2D", "CO2 density", "mmol/m3", 3, G_GAS);
        m("CO2MF", "CO2 mole fraction", "umol/mol", 2, G_GAS);
        m("CO2MFd", "CO2 dry mole fraction", "umol/mol", 2, G_GAS);
        m("CO2MG", "CO2 mass density", "mg/m3", 3, G_GAS);
        m("CO2Raw", "CO2 absorptance", "", 5, G_SIGNAL);
        m("H2OD", "H2O density", "mmol/m3", 3, G_GAS);
        m("H2OMF", "H2O mole fraction", "mmol/mol", 3, G_GAS);
        m("H2OMFd", "H2O dry mole fraction", "mmol/mol", 3, G_GAS);
        m("H2OG", "H2O mass density", "g/m3", 3, G_GAS);
        m("H2ORaw", "H2O absorptance", "", 5, G_SIGNAL);
        m("DewPt", "Dew point", "C", 2, G_GAS);

        m("AvgSS", "Average signal strength", "%", 1, G_SIGNAL);
        m("CO2SS", "CO2 signal strength", "%", 1, G_SIGNAL);
        m("H2OSS", "H2O signal strength", "%", 1, G_SIGNAL);
        m("DeltaSS", "Delta signal strength", "", 2, G_SIGNAL);
        m("Path", "Signal strength (path)", "%", 1, G_SIGNAL);
        m("AGC", "AGC", "", 1, G_SIGNAL);
        m("CO2AW", "CO2 source signal", "counts", 0, G_SIGNAL);
        m("CO2AWO", "CO2 reference signal", "counts", 0, G_SIGNAL);
        m("H2OAW", "H2O source signal", "counts", 0, G_SIGNAL);
        m("H2OAWO", "H2O reference signal", "counts", 0, G_SIGNAL);

        m("Temp", "Block temperature", "C", 2, G_CELL);
        m("AvgTemp", "Average cell temperature", "C", 2, G_CELL);
        m("TempIn", "Inlet temperature", "C", 2, G_CELL);
        m("TempOut", "Outlet temperature", "C", 2, G_CELL);
        m("Pres", "Total pressure", "kPa", 3, G_CELL);
        m("APres", "Absolute pressure", "kPa", 3, G_CELL);
        m("DPres", "Differential pressure", "kPa", 4, G_CELL);
        m("Cooler", "Detector cooler", "V", 4, G_CELL);
        m("ChopperCooler", "Chopper cooler", "V", 4, G_CELL);

        m("MeasFlowRate", "Flow rate", "SLPM", 2, G_FLOW);
        m("VolFlowRate", "Volumetric flow", "LPM", 2, G_FLOW);
        m("FlowPressure", "Flow module pressure", "kPa", 3, G_FLOW);
        m("FlowPower", "Flow module voltage", "V", 2, G_FLOW);
        m("FlowDrive", "Pump drive", "%", 1, G_FLOW);
        m("SetFlowRate", "Flow setpoint", "SLPM", 2, G_FLOW);

        m("Aux", "Aux input 1", "V", 4, G_AUX);
        m("Aux2", "Aux input 2", "V", 4, G_AUX);
        m("Aux3", "Aux input 3", "V", 4, G_AUX);
        m("Aux4", "Aux input 4", "V", 4, G_AUX);
        m("AUXTC1", "Thermocouple 1", "C", 2, G_AUX);
        m("AUXTC2", "Thermocouple 2", "C", 2, G_AUX);
        m("AUXTC3", "Thermocouple 3", "C", 2, G_AUX);
        m("AUXTCDIAG", "Thermocouple diagnostic", "", 0, G_AUX);

        m("SFVin", "SmartFlux input voltage", "V", 2, G_SYS);
        m("DSIVin", "DSI input voltage", "V", 2, G_SYS);
        m("DiagVal", "Diagnostic value", "", 0, G_SYS);
        m("DiagVal2", "Diagnostic value 2", "", 0, G_SYS);
        m("DiagBits", "Diagnostic bits", "", 0, G_SYS);

        m("U", "Wind u", "m/s", 3, G_SONIC);
        m("V", "Wind v", "m/s", 3, G_SONIC);
        m("W", "Wind w", "m/s", 3, G_SONIC);
        m("TS", "Sonic temperature", "C", 2, G_SONIC);
        m("SOS", "Speed of sound", "m/s", 2, G_SONIC);
        m("AnemDiag", "Anemometer diagnostic", "", 0, G_SONIC);

        m("CH4", "CH4 mole fraction", "umol/mol", 4, G_CH4);
        m("CH4D", "CH4 density", "mmol/m3", 5, G_CH4);
        m("RSSI", "LI-7700 signal (RSSI)", "%", 1, G_CH4);
        m("DROPRATE", "Dropped samples", "%", 1, G_CH4);
        m("DIAG", "LI-7700 diagnostic", "", 0, G_CH4);
        m("TEMP", "LI-7700 temperature", "C", 2, G_CH4);
        m("PRESSURE", "LI-7700 pressure", "kPa", 3, G_CH4);

        m("Integral", "Integral", "", 4, G_OTHER);
        m("Peak", "Peak", "", 4, G_OTHER);
        m("Drift", "Drift", "", 4, G_OTHER);
        m("MinDrift", "Minimum drift", "", 4, G_OTHER);
        m("YZ", "YZ", "", 4, G_OTHER);
    }

    public static String label(String tag) {
        String[] r = lookup(tag);
        return r == null ? tag : r[0];
    }

    public static String unit(String tag) {
        String[] r = lookup(tag);
        return r == null ? "" : r[1];
    }

    public static int decimals(String tag) {
        String[] r = lookup(tag);
        return r == null ? 4 : Integer.parseInt(r[2]);
    }

    public static String group(String tag) {
        String[] r = lookup(tag);
        return r == null ? G_OTHER : r[3];
    }

    public static boolean known(String tag) {
        return lookup(tag) != null;
    }

    /** Display order of groups. */
    public static final String[] GROUP_ORDER = {
            G_GAS, G_SIGNAL, G_CELL, G_FLOW, G_SONIC, G_CH4, G_AUX, G_SYS, G_OTHER
    };

    /**
     * Field order used by the instrument when (Labels FALSE) is configured,
     * taken from the order of the Data node in the grammar tree shipped with
     * the PC software (7200tree.dtd). Only fields enabled in (Outputs(ENet ...))
     * appear on the wire, in this order.
     */
    public static final String[] DATA_ORDER_7200 = {
            "Ndx", "DiagVal", "DiagVal2", "DiagBits", "AGC", "AvgSS", "CO2SS", "H2OSS", "DeltaSS",
            "CO2Raw", "CO2D", "CO2MG", "H2ORaw", "H2OD", "H2OG", "Temp", "Pres",
            "Aux", "Aux2", "Aux3", "Aux4", "Cooler", "ChopperCooler", "SFVin",
            "Time", "Date", "CO2MF", "CO2MFd", "H2OMF", "H2OMFd", "DewPt",
            "APres", "DPres", "AvgTemp", "TempIn", "TempOut",
            "H2OAW", "H2OAWO", "CO2AW", "CO2AWO",
            "MeasFlowRate", "VolFlowRate", "FlowPressure", "FlowPower", "FlowDrive",
            "SECONDS", "NANOSECONDS", "CH4", "CH4D", "RSSI", "DIAG",
            "U", "V", "W", "TS", "SOS", "AnemDiag", "DSIVin"
    };

    /** Same idea for the older LI-7500A tree (7500tree.dtd). */
    public static final String[] DATA_ORDER_7500 = {
            "Ndx", "DiagVal", "CO2Raw", "CO2D", "H2ORaw", "H2OD", "Temp", "Pres",
            "Aux", "Aux2", "Aux3", "Aux4", "Cooler",
            "Time", "Date", "CO2MF", "CO2MFd", "H2OMF", "H2OMFd", "DewPt",
            "DPres", "TempIn", "TempOut"
    };
}
