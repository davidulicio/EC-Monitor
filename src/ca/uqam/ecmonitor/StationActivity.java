package ca.uqam.ecmonitor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ca.uqam.ecmonitor.net.Client;
import ca.uqam.ecmonitor.net.Traffic;
import ca.uqam.ecmonitor.proto.Assessment;
import ca.uqam.ecmonitor.proto.Diagnostics;
import ca.uqam.ecmonitor.proto.Fmt;
import ca.uqam.ecmonitor.proto.Metrics;
import ca.uqam.ecmonitor.proto.Node;
import ca.uqam.ecmonitor.proto.Queries;
import ca.uqam.ecmonitor.proto.Snapshot;
import ca.uqam.ecmonitor.ui.Ui;

/** Live view of one analyzer: overview, checks, every value, and a read-only console. */
public class StationActivity extends Activity implements Client.Listener {

    private interface Binder {
        void bind();
    }

    private static final String[] TAB_LABELS = {"Overview", "Checks", "Values", "Console"};

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Snapshot snapshot = new Snapshot();
    private final Traffic traffic = new Traffic(600);
    private final List<Binder> binders = new ArrayList<Binder>();

    private Station station;
    private Client client;
    private int tab = 0;
    private String structure = "";
    private long lastRender = 0;
    private boolean renderScheduled = false;

    private TextView statusChip;
    private TextView headerSub;
    private LinearLayout content;
    private ScrollView scroll;
    private TextView consoleLog;
    private ScrollView consoleScroll;
    private EditText consoleInput;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        List<Station> all = Store.load(this);
        station = Store.find(all, getIntent().getStringExtra("id"));
        if (station == null) {
            finish();
            return;
        }
        buildScreen();
        rebuildTab();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (station == null) return;
        if (client == null) {
            client = new Client(station.host, station.port, station.pollSeconds * 1000,
                    snapshot, traffic, this);
        }
        client.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (client != null) {
            client.stop();
            client = null;
        }
    }

    // ---- chrome -----------------------------------------------------------

    private void buildScreen() {
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        int p = Ui.dp(this, 14);
        root.setPadding(p, Ui.dp(this, 16), p, 0);

        LinearLayout header = Ui.row(this);
        header.setLayoutParams(Ui.match());
        TextView back = Ui.text(this, "‹", 30, Ui.MUTED, true);
        back.setPadding(0, 0, Ui.dp(this, 14), Ui.dp(this, 4));
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });
        header.addView(back);

        LinearLayout titles = Ui.column(this);
        titles.setLayoutParams(Ui.weighted(1f));
        titles.addView(Ui.text(this, station.title(), 20, Ui.TEXT, true));
        headerSub = Ui.text(this, station.address(), 12, Ui.MUTED, false);
        titles.addView(headerSub);
        header.addView(titles);

        statusChip = Ui.chip(this, "Idle", Ui.MUTED);
        header.addView(statusChip);

        TextView share = Ui.text(this, "⋯", 22, Ui.MUTED, true);
        share.setPadding(Ui.dp(this, 12), 0, 0, Ui.dp(this, 4));
        share.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showMenu();
            }
        });
        header.addView(share);
        root.addView(header);
        root.addView(Ui.spacer(this, 14));

        root.addView(Ui.tabs(this, TAB_LABELS, tab, new Ui.OnTab() {
            public void onTab(int index) {
                tab = index;
                rebuildChrome();
            }
        }));

        scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        content = Ui.column(this);
        content.setPadding(0, 0, 0, Ui.dp(this, 20));
        scroll.addView(content);
        root.addView(scroll);

        setContentView(root);
    }

    private void rebuildChrome() {
        buildScreen();
        structure = "";
        rebuildTab();
    }

    private void showMenu() {
        final String[] items = {"Share this check", "Edit station", "Copy console"};
        new AlertDialog.Builder(this).setItems(items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int which) {
                if (which == 0) {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("text/plain");
                    i.putExtra(Intent.EXTRA_SUBJECT, "EC station check: " + station.title());
                    i.putExtra(Intent.EXTRA_TEXT, Report.build(station, snapshot));
                    startActivity(Intent.createChooser(i, "Share"));
                } else if (which == 1) {
                    Intent i = new Intent(StationActivity.this, EditStationActivity.class);
                    i.putExtra("id", station.id);
                    startActivity(i);
                } else {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("text/plain");
                    i.putExtra(Intent.EXTRA_SUBJECT, "EC Monitor console: " + station.title());
                    i.putExtra(Intent.EXTRA_TEXT, traffic.asText());
                    startActivity(Intent.createChooser(i, "Share"));
                }
            }
        }).show();
    }

    // ---- client callback --------------------------------------------------

    @Override
    public void onClientEvent(int state, String message) {
        // Called on the polling thread; all view work has to happen on the UI thread.
        ui.post(new Runnable() {
            public void run() {
                scheduleRender();
            }
        });
    }

    private void scheduleRender() {
        if (renderScheduled) return;
        renderScheduled = true;
        long wait = Math.max(0, 400 - (System.currentTimeMillis() - lastRender));
        ui.postDelayed(new Runnable() {
            public void run() {
                renderScheduled = false;
                lastRender = System.currentTimeMillis();
                try {
                    render();
                } catch (Throwable t) {
                    // A display problem should never take the app down in the field.
                    traffic.add(Traffic.NOTE, "Display error: " + t);
                }
            }
        }, wait);
    }

    private void render() {
        if (isFinishing()) return;
        updateStatusChip();
        String sig = signature();
        if (!sig.equals(structure)) {
            structure = sig;
            rebuildTab();
            return;
        }
        for (int i = 0; i < binders.size(); i++) binders.get(i).bind();
        if (tab == 3) updateConsole();
    }

    private void updateStatusChip() {
        int state = client == null ? Client.STATE_IDLE : client.state();
        String label;
        int color;
        switch (state) {
            case Client.STATE_CONNECTED:
                label = "Live";
                color = Ui.OK;
                break;
            case Client.STATE_CONNECTING:
                label = "Connecting";
                color = Ui.ACCENT;
                break;
            case Client.STATE_RETRYING:
                label = "Retrying";
                color = Ui.WARN;
                break;
            case Client.STATE_STOPPED:
                label = "Stopped";
                color = Ui.MUTED;
                break;
            default:
                label = "Idle";
                color = Ui.MUTED;
        }
        ViewGroup parent = (ViewGroup) statusChip.getParent();
        int index = parent.indexOfChild(statusChip);
        parent.removeView(statusChip);
        statusChip = Ui.chip(this, label, color);
        parent.addView(statusChip, index);

        String sub = station.address();
        if (snapshot.lastData() > 0) {
            sub = sub + "  ·  data " + Fmt.since(System.currentTimeMillis() - snapshot.lastData()) + " ago";
        } else if (client != null && client.stateMessage().length() > 0
                && client.state() == Client.STATE_RETRYING) {
            sub = sub + "  ·  " + client.stateMessage();
        }
        headerSub.setText(sub);
    }

    /** Changes to this string mean the screen has to be rebuilt rather than refreshed. */
    private String signature() {
        StringBuilder sb = new StringBuilder();
        sb.append(tab).append('|');
        if (tab == 1) {
            List<Assessment.Issue> issues = Assessment.evaluate(snapshot, staleMillis());
            for (int i = 0; i < issues.size(); i++) sb.append(issues.get(i).title).append(';');
            List<Diagnostics.Flag> flags = snapshot.flags();
            for (int i = 0; i < flags.size(); i++) {
                sb.append(flags.get(i).name).append(flags.get(i).ok ? '1' : '0');
            }
        } else if (tab == 2) {
            Map<String, String> d = snapshot.dataCopy();
            for (Map.Entry<String, String> e : d.entrySet()) sb.append(e.getKey()).append(',');
        } else if (tab == 0) {
            sb.append(snapshot.enclosed()).append(snapshot.hasCh4()).append(snapshot.hasSonic());
            sb.append(snapshot.record("Fluxes") != null).append(snapshot.record("Info") != null);
            sb.append(snapshot.model());
            List<Assessment.Issue> issues = Assessment.evaluate(snapshot, staleMillis());
            sb.append(Assessment.worst(issues)).append('/').append(issues.size());
            if (!issues.isEmpty()) sb.append(issues.get(0).title);
        }
        return sb.toString();
    }

    private long staleMillis() {
        return Math.max(20000L, station.pollSeconds * 6000L);
    }

    // ---- tabs -------------------------------------------------------------

    private void rebuildTab() {
        binders.clear();
        content.removeAllViews();
        if (tab == 0) buildOverview();
        else if (tab == 1) buildChecks();
        else if (tab == 2) buildValues();
        else buildConsole();
        for (int i = 0; i < binders.size(); i++) binders.get(i).bind();
    }

    private void buildOverview() {
        // Health banner
        final LinearLayout banner = Ui.card(this);
        final TextView bannerTitle = Ui.text(this, "Checking...", 16, Ui.TEXT, true);
        final TextView bannerBody = Ui.text(this, "", 13, Ui.MUTED, false);
        LinearLayout bannerTop = Ui.row(this);
        bannerTop.setLayoutParams(Ui.match());
        final View bannerDot = Ui.dot(this, Ui.MUTED, 10);
        bannerTop.addView(bannerDot);
        bannerTop.addView(bannerTitle);
        banner.addView(bannerTop);
        banner.addView(Ui.spacer(this, 6));
        banner.addView(bannerBody);
        content.addView(banner);
        binders.add(new Binder() {
            public void bind() {
                List<Assessment.Issue> issues = Assessment.evaluate(snapshot, staleMillis());
                int worst = Assessment.worst(issues);
                int color = issues.isEmpty() ? Ui.OK : Ui.severityColor(worst);
                android.graphics.drawable.GradientDrawable g =
                        new android.graphics.drawable.GradientDrawable();
                g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                g.setColor(color);
                bannerDot.setBackground(g);
                if (snapshot.lastAnything() == 0) {
                    bannerTitle.setText("Waiting for the instrument");
                    bannerBody.setText(client != null && client.stateMessage().length() > 0
                            ? client.stateMessage() : "Opening the connection.");
                } else if (issues.isEmpty()) {
                    bannerTitle.setText("Nothing flagged");
                    bannerBody.setText("All checks pass at the thresholds this app uses.");
                } else {
                    bannerTitle.setText(issues.get(0).title);
                    String extra = issues.size() > 1
                            ? "  ·  " + (issues.size() - 1) + " more in Checks" : "";
                    bannerBody.setText(issues.get(0).detail + extra);
                }
                bannerTitle.setTextColor(color);
            }
        });

        // Live tiles
        final LinearLayout tiles = Ui.card(this);
        tiles.addView(Ui.cardTitle(this, "Live"));
        final LinearLayout tileHolder = Ui.column(this);
        tiles.addView(tileHolder);
        content.addView(tiles);

        List<String[]> wanted = new ArrayList<String[]>();
        wanted.add(new String[]{"CO2MF", "CO2"});
        wanted.add(new String[]{"H2OMF", "H2O"});
        wanted.add(new String[]{"__SS", "Signal strength"});
        wanted.add(new String[]{snapshot.enclosed() ? "AvgTemp" : "Temp", "Cell temperature"});
        wanted.add(new String[]{"Pres", "Cell pressure"});
        wanted.add(new String[]{"Cooler", "Detector cooler"});
        if (snapshot.enclosed()) {
            wanted.add(new String[]{"MeasFlowRate", "Flow rate"});
            wanted.add(new String[]{"FlowDrive", "Pump drive"});
        }
        if (snapshot.hasCh4()) {
            wanted.add(new String[]{"CH4", "CH4"});
            wanted.add(new String[]{"RSSI", "LI-7700 RSSI"});
        }
        if (snapshot.hasSonic()) {
            wanted.add(new String[]{"__WIND", "Wind speed"});
            wanted.add(new String[]{"TS", "Sonic temperature"});
        }

        final List<TextView> tileValues = new ArrayList<TextView>();
        final List<String> tileTags = new ArrayList<String>();
        LinearLayout currentRow = null;
        for (int i = 0; i < wanted.size(); i++) {
            if (i % 2 == 0) {
                currentRow = Ui.row(this);
                LinearLayout.LayoutParams rp = Ui.match();
                rp.bottomMargin = Ui.dp(this, 8);
                currentRow.setLayoutParams(rp);
                tileHolder.addView(currentRow);
            }
            String tag = wanted.get(i)[0];
            String caption = wanted.get(i)[1];
            String unit = tag.equals("__SS") ? "%" : tag.equals("__WIND") ? "m/s" : Metrics.unit(tag);
            LinearLayout t = Ui.tile(this, caption, "--", unit, Ui.TEXT);
            LinearLayout.LayoutParams tp = Ui.weighted(1f);
            if (i % 2 == 1) tp.leftMargin = Ui.dp(this, 8);
            t.setLayoutParams(tp);
            currentRow.addView(t);
            LinearLayout valueRow = (LinearLayout) t.getChildAt(1);
            tileValues.add((TextView) valueRow.getChildAt(0));
            tileTags.add(tag);
        }
        binders.add(new Binder() {
            public void bind() {
                for (int i = 0; i < tileTags.size(); i++) {
                    String tag = tileTags.get(i);
                    TextView v = tileValues.get(i);
                    double value;
                    int decimals;
                    if (tag.equals("__SS")) {
                        value = snapshot.signalStrength();
                        decimals = 1;
                    } else if (tag.equals("__WIND")) {
                        double u = snapshot.number("U"), vv = snapshot.number("V"), w = snapshot.number("W");
                        value = (Fmt.isNum(u) && Fmt.isNum(vv))
                                ? Math.sqrt(u * u + vv * vv + (Fmt.isNum(w) ? w * w : 0)) : Double.NaN;
                        decimals = 2;
                    } else {
                        value = snapshot.number(tag);
                        decimals = Metrics.decimals(tag);
                        if (decimals > 3) decimals = 3;
                    }
                    v.setText(Fmt.dec(value, decimals));
                    v.setTextColor(tileColor(tag, value));
                }
            }
        });

        // Instrument identity
        final LinearLayout ident = Ui.card(this);
        ident.addView(Ui.cardTitle(this, "Instrument"));
        final LinearLayout identBody = Ui.column(this);
        ident.addView(identBody);
        content.addView(ident);
        binders.add(new Binder() {
            public void bind() {
                identBody.removeAllViews();
                addKv(identBody, "Model", orDash(snapshot.model()));
                addKv(identBody, "Head serial", orDash(snapshot.serial()));
                addKv(identBody, "Firmware", orDash(snapshot.firmware()));
                addKv(identBody, "Network name", orDash(snapshot.hostName()));
                Node clock = snapshot.record("Clock");
                if (clock != null) {
                    String d = clock.str("Date");
                    String t = clock.str("Time");
                    if (d != null || t != null) {
                        addKv(identBody, "Instrument clock", (d == null ? "" : d) + " " + (t == null ? "" : t));
                    }
                    String ptp = clock.str("PTP");
                    if (ptp != null && ptp.length() > 0) addKv(identBody, "PTP", ptp);
                }
            }
        });

        // Logging and SmartFlux
        final LinearLayout logging = Ui.card(this);
        logging.addView(Ui.cardTitle(this, "Logging and SmartFlux"));
        final LinearLayout loggingBody = Ui.column(this);
        logging.addView(loggingBody);
        content.addView(logging);
        binders.add(new Binder() {
            public void bind() {
                loggingBody.removeAllViews();
                Node info = snapshot.record("Info");
                if (info != null) {
                    String state = info.str("USB", "State");
                    addKv(loggingBody, "Drive", Diagnostics.usbState(state),
                            Diagnostics.usbStateOk(state) ? Ui.TEXT : Ui.CRIT);
                    double free = Fmt.toDouble(info.str("USB", "Free"));
                    double size = Fmt.toDouble(info.str("USB", "Size"));
                    if (Fmt.isNum(free)) {
                        int c = Ui.TEXT;
                        if (Fmt.isNum(size) && size > 0) {
                            double pct = 100 * free / size;
                            if (free < 200) c = Ui.CRIT;
                            else if (pct < 15) c = Ui.WARN;
                        }
                        addKv(loggingBody, "Free space", Fmt.bytesMb(free), c);
                    }
                    if (Fmt.isNum(size)) addKv(loggingBody, "Drive size", Fmt.bytesMb(size));
                }
                Node f = snapshot.record("Fluxes");
                if (f != null) {
                    addKvIf(loggingBody, "SmartFlux", f.str("Status", "SmartFlux", "Model"));
                    addKvIf(loggingBody, "Serial", f.str("Status", "SmartFlux", "SerialNo"));
                    addKvIf(loggingBody, "Firmware", f.str("Status", "SmartFlux", "Version"));
                    addKvIf(loggingBody, "EddyPro", f.str("Status", "SmartFlux", "EPVersion"));
                    double vin = Fmt.toDouble(f.str("Status", "SmartFlux", "Vin"));
                    if (Fmt.isNum(vin)) {
                        addKv(loggingBody, "Input voltage", Fmt.dec(vin, 2) + " V",
                                vin < 11.5 ? Ui.CRIT : vin < 12.0 ? Ui.WARN : Ui.TEXT);
                    }
                    double sats = Fmt.toDouble(f.str("Status", "SmartFlux", "GPS", "NumSat"));
                    if (Fmt.isNum(sats)) {
                        addKv(loggingBody, "GPS satellites", Fmt.dec(sats, 0),
                                sats < 4 ? Ui.WARN : Ui.TEXT);
                    }
                    addKvIf(loggingBody, "Run status", f.str("Status", "EddyPro", "RunStatus"));
                    String lastFile = f.str("Status", "EddyPro", "LastFile");
                    if (lastFile != null && lastFile.length() > 0) {
                        long age = Assessment.fileAgeMillis(lastFile);
                        String shown = age > 0 ? Fmt.since(age) + " ago" : lastFile;
                        int c = Ui.TEXT;
                        if (age > 3 * 3600000L) c = Ui.CRIT;
                        else if (age > 75 * 60000L) c = Ui.WARN;
                        addKv(loggingBody, "Last flux file", shown, c);
                        if (age > 0) addKv(loggingBody, "File name", lastFile);
                    }
                }
                if (loggingBody.getChildCount() == 0) {
                    loggingBody.addView(Ui.text(StationActivity.this,
                            "No logging or SmartFlux information returned by this instrument.",
                            13, Ui.MUTED, false));
                }
            }
        });
    }

    private int tileColor(String tag, double value) {
        if (!Fmt.isNum(value)) return Ui.MUTED;
        if (tag.equals("__SS")) {
            if (value < 40) return Ui.CRIT;
            if (value < 70) return Ui.WARN;
            return Ui.OK;
        }
        if (tag.equals("RSSI")) {
            if (value < 10) return Ui.CRIT;
            if (value < 20) return Ui.WARN;
            return Ui.OK;
        }
        if (tag.equals("MeasFlowRate")) {
            if (value < 1) return Ui.CRIT;
            if (value < 10) return Ui.WARN;
            return Ui.TEXT;
        }
        if (tag.equals("FlowDrive")) {
            if (value > 90) return Ui.WARN;
            return Ui.TEXT;
        }
        if (tag.equals("Cooler")) {
            if (value > 2.6) return Ui.WARN;
            return Ui.TEXT;
        }
        return Ui.TEXT;
    }

    private void buildChecks() {
        List<Assessment.Issue> issues = Assessment.evaluate(snapshot, staleMillis());

        LinearLayout card = Ui.card(this);
        card.addView(Ui.cardTitle(this, "Checks"));
        if (issues.isEmpty()) {
            card.addView(Ui.text(this, snapshot.lastAnything() == 0
                            ? "Nothing to check yet, still waiting for the instrument."
                            : "Nothing flagged.",
                    14, snapshot.lastAnything() == 0 ? Ui.MUTED : Ui.OK, false));
        } else {
            for (int i = 0; i < issues.size(); i++) {
                Assessment.Issue is = issues.get(i);
                int color = Ui.severityColor(is.severity);
                LinearLayout row = Ui.column(this);
                row.setLayoutParams(Ui.match());
                LinearLayout head = Ui.row(this);
                head.setLayoutParams(Ui.match());
                head.addView(Ui.chip(this, Assessment.severityName(is.severity), color));
                TextView t = Ui.text(this, is.title, 15, Ui.TEXT, true);
                LinearLayout.LayoutParams tp = Ui.weighted(1f);
                tp.leftMargin = Ui.dp(this, 10);
                t.setLayoutParams(tp);
                head.addView(t);
                row.addView(head);
                row.addView(Ui.spacer(this, 6));
                row.addView(Ui.text(this, is.detail, 13, Ui.MUTED, false));
                card.addView(row);
                if (i < issues.size() - 1) card.addView(Ui.divider(this));
            }
        }
        content.addView(card);

        List<Diagnostics.Flag> flags = snapshot.flags();
        if (!flags.isEmpty()) {
            LinearLayout fc = Ui.card(this);
            fc.addView(Ui.cardTitle(this, "Diagnostic flags"));
            for (int i = 0; i < flags.size(); i++) {
                Diagnostics.Flag f = flags.get(i);
                LinearLayout row = Ui.row(this);
                LinearLayout.LayoutParams lp = Ui.match();
                lp.bottomMargin = Ui.dp(this, 9);
                row.setLayoutParams(lp);
                row.addView(Ui.dot(this, f.ok ? Ui.OK : Ui.CRIT, 8));
                LinearLayout col = Ui.column(this);
                col.setLayoutParams(Ui.weighted(1f));
                col.addView(Ui.text(this, f.name, 14, Ui.TEXT, false));
                col.addView(Ui.text(this, f.detail, 11, Ui.MUTED, false));
                row.addView(col);
                row.addView(Ui.text(this, f.ok ? "ok" : "fault", 12, f.ok ? Ui.OK : Ui.CRIT, true));
                fc.addView(row);
            }
            String dv = snapshot.value("DiagVal");
            if (dv != null) {
                double d = Fmt.toDouble(dv);
                if (Fmt.isNum(d)) {
                    fc.addView(Ui.divider(this));
                    fc.addView(Ui.kv(this, "Diagnostic value", dv, Ui.TEXT));
                    LinearLayout bitsRow = Ui.row(this);
                    bitsRow.setLayoutParams(Ui.match());
                    bitsRow.addView(Ui.text(this, "Bits", 14, Ui.MUTED, false));
                    TextView bits = Ui.mono(this, Diagnostics.bits((int) d, snapshot.enclosed() ? 16 : 8),
                            12, Ui.ACCENT);
                    bits.setGravity(Gravity.END);
                    bits.setLayoutParams(Ui.weighted(1f));
                    bitsRow.addView(bits);
                    fc.addView(bitsRow);
                }
            }
            content.addView(fc);
        }

        Node cal = snapshot.record("Calibrate");
        if (cal != null) {
            LinearLayout cc = Ui.card(this);
            cc.addView(Ui.cardTitle(this, "Last calibration"));
            addCal(cc, "CO2 zero", cal.get("ZeroCO2"));
            addCal(cc, "CO2 span", cal.get("SpanCO2"));
            addCal(cc, "H2O zero", cal.get("ZeroH2O"));
            addCal(cc, "H2O span", cal.get("SpanH2O"));
            content.addView(cc);
        }
    }

    private void addCal(LinearLayout parent, String label, Node n) {
        if (n == null) return;
        String date = n.str("Date");
        String val = n.str("Val");
        if ((date == null || date.length() == 0) && (val == null || val.length() == 0)) return;
        parent.addView(Ui.kv(this, label, (date == null || date.length() == 0) ? "--" : date, Ui.TEXT));
        if (val != null && val.length() > 0) {
            TextView t = Ui.text(this, "value " + val, 11, Ui.MUTED, false);
            LinearLayout.LayoutParams lp = Ui.match();
            lp.bottomMargin = Ui.dp(this, 8);
            t.setLayoutParams(lp);
            parent.addView(t);
        }
    }

    private void buildValues() {
        Map<String, String> data = snapshot.dataCopy();
        if (data.isEmpty()) {
            LinearLayout card = Ui.card(this);
            card.addView(Ui.text(this, "No measurements received yet.", 14, Ui.MUTED, false));
            content.addView(card);
            return;
        }

        for (int g = 0; g < Metrics.GROUP_ORDER.length; g++) {
            final String group = Metrics.GROUP_ORDER[g];
            List<String> tags = new ArrayList<String>();
            for (Map.Entry<String, String> e : data.entrySet()) {
                if (Metrics.group(e.getKey()).equals(group)) tags.add(e.getKey());
            }
            if (tags.isEmpty()) continue;

            LinearLayout card = Ui.card(this);
            card.addView(Ui.cardTitle(this, group));
            for (int i = 0; i < tags.size(); i++) {
                final String tag = tags.get(i);
                LinearLayout row = Ui.row(this);
                LinearLayout.LayoutParams lp = Ui.match();
                lp.bottomMargin = Ui.dp(this, 8);
                row.setLayoutParams(lp);

                LinearLayout labels = Ui.column(this);
                labels.setLayoutParams(Ui.weighted(1f));
                labels.addView(Ui.text(this, Metrics.label(tag), 14, Ui.TEXT, false));
                labels.addView(Ui.text(this, tag, 10, Ui.MUTED, false));
                row.addView(labels);

                final TextView value = Ui.text(this, "--", 15, Ui.ACCENT, true);
                value.setGravity(Gravity.END);
                row.addView(value);
                String unit = Metrics.unit(tag);
                if (unit.length() > 0) {
                    TextView u = Ui.text(this, " " + unit, 11, Ui.MUTED, false);
                    row.addView(u);
                }
                card.addView(row);

                binders.add(new Binder() {
                    public void bind() {
                        String raw = snapshot.value(tag);
                        double d = Fmt.toDouble(raw);
                        value.setText(Fmt.isNum(d) ? Fmt.dec(d, Metrics.decimals(tag)) : (raw == null ? "--" : raw));
                    }
                });
            }
            content.addView(card);
        }

        List<String> records = snapshot.recordNames();
        if (!records.isEmpty()) {
            LinearLayout card = Ui.card(this);
            card.addView(Ui.cardTitle(this, "Records received"));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < records.size(); i++) {
                if (i > 0) sb.append("   ");
                sb.append(records.get(i));
            }
            card.addView(Ui.text(this, sb.toString(), 12, Ui.MUTED, false));
            content.addView(card);
        }
    }

    private void buildConsole() {
        LinearLayout notice = Ui.card(this);
        notice.addView(Ui.cardTitle(this, "Read only"));
        notice.addView(Ui.text(this,
                "Queries end in a question mark and only read. Anything that would set a value is "
                        + "refused before it reaches the instrument, so this console cannot start a "
                        + "calibration, change an output or stop logging.",
                12, Ui.MUTED, false));
        content.addView(notice);

        LinearLayout input = Ui.card(this);
        input.addView(Ui.cardTitle(this, "Send a query"));
        consoleInput = new EditText(this);
        consoleInput.setHint("(Data ?)");
        consoleInput.setText("(Outputs ?)");
        consoleInput.setSingleLine(true);
        consoleInput.setTextColor(Ui.TEXT);
        consoleInput.setHintTextColor(0xFF5B6874);
        consoleInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        consoleInput.setLayoutParams(Ui.match());
        input.addView(consoleInput);
        input.addView(Ui.spacer(this, 10));

        LinearLayout sendRow = Ui.row(this);
        sendRow.setLayoutParams(Ui.match());
        android.widget.Button send = Ui.button(this, "Send", true);
        send.setLayoutParams(Ui.weighted(1f));
        send.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                submit(consoleInput.getText().toString());
            }
        });
        sendRow.addView(send);
        android.widget.Button clear = Ui.button(this, "Clear log", false);
        LinearLayout.LayoutParams clp = Ui.wrap();
        clp.leftMargin = Ui.dp(this, 8);
        clear.setLayoutParams(clp);
        clear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                traffic.clear();
                updateConsole();
            }
        });
        sendRow.addView(clear);
        input.addView(sendRow);
        input.addView(Ui.spacer(this, 12));

        final String[] quick = {"(Data ?)", "(Diagnostics ?)", "(Info ?)", "(Fluxes(Status ?))",
                "(Outputs ?)", "(Coef ?)", "(Calibrate ?)", "(EmbeddedSW ?)", "(Network ?)",
                "(Inputs ?)", "(FlowBox ?)", "(CH4 ?)", "(Clock ?)"};
        LinearLayout chipRow = null;
        for (int i = 0; i < quick.length; i++) {
            if (i % 2 == 0) {
                chipRow = Ui.row(this);
                LinearLayout.LayoutParams rp = Ui.match();
                rp.bottomMargin = Ui.dp(this, 6);
                chipRow.setLayoutParams(rp);
                input.addView(chipRow);
            }
            final String q = quick[i];
            TextView c = Ui.mono(this, q, 12, Ui.ACCENT);
            android.graphics.drawable.GradientDrawable g =
                    new android.graphics.drawable.GradientDrawable();
            g.setColor(0xFF141B23);
            g.setCornerRadius(Ui.dp(this, 8));
            g.setStroke(Ui.dp(this, 1), Ui.CARD_EDGE);
            c.setBackground(g);
            c.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
            c.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams cp = Ui.weighted(1f);
            if (i % 2 == 1) cp.leftMargin = Ui.dp(this, 6);
            c.setLayoutParams(cp);
            c.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    consoleInput.setText(q);
                    submit(q);
                }
            });
            chipRow.addView(c);
        }
        content.addView(input);

        LinearLayout logCard = Ui.card(this);
        logCard.addView(Ui.cardTitle(this, "Traffic"));
        consoleScroll = new ScrollView(this);
        consoleScroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 320)));
        consoleLog = Ui.mono(this, "", 11, Ui.TEXT);
        consoleLog.setTextIsSelectable(true);
        consoleScroll.addView(consoleLog);
        logCard.addView(consoleScroll);
        content.addView(logCard);

        updateConsole();
    }

    private void submit(String command) {
        if (client == null) {
            Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Queries.isReadOnly(command)) {
            Toast.makeText(this, "Refused: that command would write to the instrument",
                    Toast.LENGTH_LONG).show();
        }
        client.submit(command);
        updateConsole();
    }

    private void updateConsole() {
        if (consoleLog == null) return;
        List<Traffic.Line> lines = traffic.snapshot();
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, lines.size() - 250);
        for (int i = start; i < lines.size(); i++) {
            Traffic.Line l = lines.get(i);
            sb.append(l.stamp()).append(' ').append(l.prefix()).append(l.text).append('\n');
        }
        consoleLog.setText(sb.toString());
        if (consoleScroll != null) {
            consoleScroll.post(new Runnable() {
                public void run() {
                    consoleScroll.fullScroll(View.FOCUS_DOWN);
                }
            });
        }
    }

    // ---- small helpers ----------------------------------------------------

    private void addKv(LinearLayout parent, String label, String value) {
        addKv(parent, label, value, Ui.TEXT);
    }

    private void addKv(LinearLayout parent, String label, String value, int color) {
        parent.addView(Ui.kv(this, label, value == null ? "--" : value, color));
    }

    private void addKvIf(LinearLayout parent, String label, String value) {
        if (value == null || value.length() == 0) return;
        addKv(parent, label, value, Ui.TEXT);
    }

    private static String orDash(String s) {
        return s == null || s.length() == 0 ? "--" : s;
    }
}
