package ca.uqam.ecmonitor;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import java.util.List;

import ca.uqam.ecmonitor.ui.Ui;

/** Add or change one station. */
public class EditStationActivity extends Activity {

    private List<Station> stations;
    private Station station;
    private EditText name, host, port, poll, note;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        stations = Store.load(this);
        String id = getIntent().getStringExtra("id");
        station = id == null ? null : Store.find(stations, id);
        boolean isNew = station == null;
        if (isNew) station = new Station();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);

        LinearLayout root = Ui.column(this);
        int p = Ui.dp(this, 16);
        root.setPadding(p, Ui.dp(this, 20), p, p);

        root.addView(Ui.text(this, isNew ? "Add station" : "Edit station", 22, Ui.TEXT, true));
        root.addView(Ui.spacer(this, 16));

        LinearLayout card = Ui.card(this);
        name = field(card, "Name", "UQAM_3", station.name, InputType.TYPE_CLASS_TEXT);
        host = field(card, "Address", "tower address or host name", station.host,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        port = field(card, "Port", "7200", Integer.toString(station.port), InputType.TYPE_CLASS_NUMBER);
        poll = field(card, "Refresh every (seconds)", "2", Integer.toString(station.pollSeconds),
                InputType.TYPE_CLASS_NUMBER);
        note = field(card, "Note", "anything worth remembering about this site", station.note,
                InputType.TYPE_CLASS_TEXT);
        root.addView(card);

        LinearLayout hint = Ui.card(this);
        hint.addView(Ui.cardTitle(this, "About the connection"));
        hint.addView(Ui.text(this,
                "Port 7200 is the Ethernet service on the LI-7550 or LI-7500DS. The app sends read "
                        + "queries only, so it will not disturb SmartFlux, logging or a running "
                        + "calibration. A slower refresh is easier on a cellular link.",
                13, Ui.MUTED, false));
        root.addView(hint);

        LinearLayout actions = Ui.row(this);
        android.widget.Button save = Ui.button(this, "Save", true);
        save.setLayoutParams(Ui.weighted(1f));
        save.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                save();
            }
        });
        actions.addView(save);

        android.widget.Button cancel = Ui.button(this, "Cancel", false);
        LinearLayout.LayoutParams clp = Ui.wrap();
        clp.leftMargin = Ui.dp(this, 8);
        cancel.setLayoutParams(clp);
        cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });
        actions.addView(cancel);
        root.addView(actions);

        scroll.addView(root);
        setContentView(scroll);
    }

    private EditText field(LinearLayout parent, String label, String hint, String value, int inputType) {
        parent.addView(Ui.text(this, label, 12, Ui.MUTED, false));
        EditText e = new EditText(this);
        e.setText(value == null ? "" : value);
        e.setHint(hint);
        e.setInputType(inputType);
        e.setSingleLine(true);
        e.setTextColor(Ui.TEXT);
        e.setHintTextColor(0xFF5B6874);
        e.setTextSize(16);
        LinearLayout.LayoutParams lp = Ui.match();
        lp.bottomMargin = Ui.dp(this, 10);
        e.setLayoutParams(lp);
        parent.addView(e);
        return e;
    }

    private void save() {
        String h = host.getText().toString().trim();
        if (h.length() == 0) {
            Toast.makeText(this, "An address is needed", Toast.LENGTH_SHORT).show();
            return;
        }
        station.name = name.getText().toString().trim();
        station.host = h;
        station.port = intOr(port.getText().toString(), 7200);
        station.pollSeconds = Math.max(1, Math.min(60, intOr(poll.getText().toString(), 2)));
        station.note = note.getText().toString().trim();

        if (Store.find(stations, station.id) == null) stations.add(station);
        Store.save(this, stations);
        finish();
    }

    private static int intOr(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
