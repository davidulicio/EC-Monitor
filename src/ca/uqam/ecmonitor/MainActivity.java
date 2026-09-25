package ca.uqam.ecmonitor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import ca.uqam.ecmonitor.net.Client;
import ca.uqam.ecmonitor.net.Traffic;
import ca.uqam.ecmonitor.proto.Assessment;
import ca.uqam.ecmonitor.proto.Fmt;
import ca.uqam.ecmonitor.proto.Snapshot;
import ca.uqam.ecmonitor.ui.Ui;

/** Station list: one line per analyzer, with a health check across all of them. */
public class MainActivity extends Activity {

    private final Handler ui = new Handler(Looper.getMainLooper());
    private List<Station> stations = new ArrayList<Station>();
    private LinearLayout listHolder;
    private TextView subtitle;
    private boolean refreshing = false;
    private int refreshDone = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        stations = Store.load(this);
        renderList();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setFillViewport(true);

        LinearLayout root = Ui.column(this);
        int p = Ui.dp(this, 14);
        root.setPadding(p, Ui.dp(this, 18), p, p);

        LinearLayout header = Ui.row(this);
        header.setLayoutParams(Ui.match());
        LinearLayout titles = Ui.column(this);
        titles.setLayoutParams(Ui.weighted(1f));
        titles.addView(Ui.text(this, "EC Monitor", 24, Ui.TEXT, true));
        subtitle = Ui.text(this, "", 13, Ui.MUTED, false);
        titles.addView(subtitle);
        header.addView(titles);

        TextView menu = Ui.text(this, "⋯", 22, Ui.MUTED, true);
        menu.setPadding(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4));
        menu.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showMenu();
            }
        });
        header.addView(menu);
        root.addView(header);

        root.addView(Ui.spacer(this, 14));

        LinearLayout actions = Ui.row(this);
        actions.setLayoutParams(Ui.match());
        android.widget.Button refresh = Ui.button(this, "Check all stations", true);
        refresh.setLayoutParams(Ui.weighted(1f));
        refresh.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                refreshAll();
            }
        });
        actions.addView(refresh);

        android.widget.Button add = Ui.button(this, "Add", false);
        LinearLayout.LayoutParams alp = Ui.wrap();
        alp.leftMargin = Ui.dp(this, 8);
        add.setLayoutParams(alp);
        add.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                openEditor(null);
            }
        });
        actions.addView(add);
        root.addView(actions);

        root.addView(Ui.spacer(this, 16));

        listHolder = Ui.column(this);
        listHolder.setLayoutParams(Ui.match());
        root.addView(listHolder);

        scroll.addView(root);
        setContentView(scroll);
    }

    private void renderList() {
        listHolder.removeAllViews();
        subtitle.setText(stations.size() == 0 ? "No stations yet"
                : stations.size() + (stations.size() == 1 ? " station" : " stations"));

        if (stations.isEmpty()) {
            LinearLayout empty = Ui.card(this);
            empty.addView(Ui.text(this, "Add your first station", 16, Ui.TEXT, true));
            empty.addView(Ui.spacer(this, 8));
            empty.addView(Ui.text(this,
                    "Enter the address of the LI-7550 or LI-7500DS at the tower and the port its "
                            + "Ethernet service answers on, normally 7200. The app only reads: it sends "
                            + "queries and never changes a setting on the instrument.",
                    13, Ui.MUTED, false));
            empty.addView(Ui.spacer(this, 12));
            android.widget.Button b = Ui.button(this, "Add a station", true);
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    openEditor(null);
                }
            });
            empty.addView(b);
            listHolder.addView(empty);
            return;
        }

        for (int i = 0; i < stations.size(); i++) {
            listHolder.addView(stationCard(stations.get(i)));
        }
    }

    private View stationCard(final Station s) {
        LinearLayout card = Ui.card(this);

        LinearLayout top = Ui.row(this);
        top.setLayoutParams(Ui.match());
        int color = s.lastSeverity < 0 ? Ui.MUTED : Ui.severityColor(s.lastSeverity);
        top.addView(Ui.dot(this, color, 10));

        LinearLayout names = Ui.column(this);
        names.setLayoutParams(Ui.weighted(1f));
        names.addView(Ui.text(this, s.title(), 17, Ui.TEXT, true));
        names.addView(Ui.text(this, s.address(), 12, Ui.MUTED, false));
        top.addView(names);

        String chipLabel = s.lastSeverity < 0 ? "Not checked" : Assessment.severityName(s.lastSeverity);
        top.addView(Ui.chip(this, chipLabel, color));
        card.addView(top);

        if (s.lastSummary != null && s.lastSummary.length() > 0) {
            card.addView(Ui.spacer(this, 10));
            TextView sum = Ui.text(this, s.lastSummary, 13, Ui.TEXT, false);
            card.addView(sum);
        }

        if (s.lastCheck > 0) {
            card.addView(Ui.spacer(this, 6));
            card.addView(Ui.text(this, "Checked " + Fmt.since(System.currentTimeMillis() - s.lastCheck) + " ago",
                    11, Ui.MUTED, false));
        }

        card.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, StationActivity.class);
                i.putExtra("id", s.id);
                startActivity(i);
            }
        });
        card.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                showStationMenu(s);
                return true;
            }
        });
        return card;
    }

    private void showStationMenu(final Station s) {
        final String[] items = {"Open", "Edit", "Duplicate", "Delete"};
        new AlertDialog.Builder(this)
                .setTitle(s.title())
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            Intent i = new Intent(MainActivity.this, StationActivity.class);
                            i.putExtra("id", s.id);
                            startActivity(i);
                        } else if (which == 1) {
                            openEditor(s.id);
                        } else if (which == 2) {
                            Station copy = new Station();
                            copy.name = s.name + " copy";
                            copy.host = s.host;
                            copy.port = s.port;
                            copy.pollSeconds = s.pollSeconds;
                            copy.note = s.note;
                            stations.add(copy);
                            Store.save(MainActivity.this, stations);
                            renderList();
                        } else {
                            confirmDelete(s);
                        }
                    }
                })
                .show();
    }

    private void confirmDelete(final Station s) {
        new AlertDialog.Builder(this)
                .setTitle("Remove " + s.title() + "?")
                .setMessage("This only removes it from the list on this phone.")
                .setPositiveButton("Remove", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        stations.remove(s);
                        Store.save(MainActivity.this, stations);
                        renderList();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openEditor(String id) {
        Intent i = new Intent(this, EditStationActivity.class);
        if (id != null) i.putExtra("id", id);
        startActivity(i);
    }

    private void showMenu() {
        final String[] items = {"Import stations", "Export stations", "Test with the simulator", "About"};
        new AlertDialog.Builder(this)
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) importDialog();
                        else if (which == 1) exportDialog();
                        else if (which == 2) simulatorHelp();
                        else about();
                    }
                })
                .show();
    }

    private void importDialog() {
        final EditText input = new EditText(this);
        input.setHint("UQAM_1, 192.168.13.20, 7200\nUQAM_2, 192.168.14.20, 7200");
        input.setMinLines(6);
        input.setGravity(Gravity.TOP);
        input.setTextColor(Ui.TEXT);
        input.setHintTextColor(Ui.MUTED);
        new AlertDialog.Builder(this)
                .setTitle("Import stations")
                .setMessage("One per line as name, address, port. Exported JSON is accepted too.")
                .setView(input)
                .setPositiveButton("Import", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        List<Station> added = Store.importFrom(input.getText().toString());
                        if (added.isEmpty()) {
                            toast("Nothing recognised");
                            return;
                        }
                        stations.addAll(added);
                        Store.save(MainActivity.this, stations);
                        renderList();
                        toast("Added " + added.size());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void exportDialog() {
        final String text = Store.export(stations);
        final EditText out = new EditText(this);
        out.setText(text);
        out.setTextColor(Ui.TEXT);
        out.setMinLines(6);
        new AlertDialog.Builder(this)
                .setTitle("Export stations")
                .setView(out)
                .setPositiveButton("Share", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        share("EC Monitor stations", text);
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void simulatorHelp() {
        new AlertDialog.Builder(this)
                .setTitle("Test without an instrument")
                .setMessage("licor_sim.py, shipped with this app's source, pretends to be an analyzer.\n\n"
                        + "On a computer on the same WiFi:\n"
                        + "    python licor_sim.py\n\n"
                        + "Then add a station pointing at that computer's address on port 7200. "
                        + "Options such as --fault flow or --fault smartflux reproduce specific "
                        + "failures so you can see what the app shows before it matters.")
                .setPositiveButton("Close", null)
                .show();
    }

    private void about() {
        new AlertDialog.Builder(this)
                .setTitle("EC Monitor")
                .setMessage("Read-only field check for LI-7500 and LI-7200 family analyzers, over the "
                        + "LI-COR configuration grammar on TCP port 7200.\n\n"
                        + "The app sends queries only. It never writes a setting, never calibrates and "
                        + "never starts or stops logging, so it cannot disturb a running station.\n\n"
                        + "Thresholds in the checks are sensible starting points, not a substitute for "
                        + "the manual.")
                .setPositiveButton("Close", null)
                .show();
    }

    private void refreshAll() {
        if (refreshing) return;
        if (stations.isEmpty()) {
            toast("Add a station first");
            return;
        }
        refreshing = true;
        refreshDone = 0;
        subtitle.setText("Checking 0 of " + stations.size() + " ...");

        final List<Station> targets = new ArrayList<Station>(stations);
        for (int i = 0; i < targets.size(); i++) {
            final Station s = targets.get(i);
            Thread t = new Thread(new Runnable() {
                public void run() {
                    Snapshot snap = new Snapshot();
                    Traffic tr = new Traffic(50);
                    String err = Client.probe(s.host, s.port, 6000, snap, tr);
                    final int severity;
                    final String summary;
                    if (err != null) {
                        severity = Assessment.CRIT;
                        summary = "Unreachable: " + err;
                    } else {
                        List<Assessment.Issue> issues = Assessment.evaluate(snap, 60000);
                        severity = Assessment.worst(issues);
                        if (issues.isEmpty()) {
                            StringBuilder sb = new StringBuilder("Healthy");
                            double ss = snap.signalStrength();
                            if (Fmt.isNum(ss)) sb.append(" · signal ").append(Fmt.dec(ss, 0)).append(" %");
                            double co2 = snap.number("CO2MF");
                            if (Fmt.isNum(co2)) sb.append(" · CO2 ").append(Fmt.dec(co2, 1));
                            summary = sb.toString();
                        } else {
                            summary = issues.get(0).title
                                    + (issues.size() > 1 ? "  (+" + (issues.size() - 1) + " more)" : "");
                        }
                    }
                    ui.post(new Runnable() {
                        public void run() {
                            s.lastSeverity = severity;
                            s.lastSummary = summary;
                            s.lastCheck = System.currentTimeMillis();
                            Station stored = Store.find(stations, s.id);
                            if (stored != null) {
                                stored.lastSeverity = severity;
                                stored.lastSummary = summary;
                                stored.lastCheck = s.lastCheck;
                            }
                            refreshDone++;
                            if (refreshDone >= targets.size()) {
                                refreshing = false;
                                Store.save(MainActivity.this, stations);
                                renderList();
                                subtitle.setText(stations.size() + " stations · checked "
                                        + new SimpleDateFormat("HH:mm", Locale.US).format(new Date()));
                            } else {
                                subtitle.setText("Checking " + refreshDone + " of " + targets.size() + " ...");
                                renderList();
                            }
                        }
                    });
                }
            }, "probe-" + s.host);
            t.setDaemon(true);
            t.start();
        }
    }

    private void share(String subject, String body) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, subject);
        i.putExtra(Intent.EXTRA_TEXT, body);
        startActivity(Intent.createChooser(i, "Share"));
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
