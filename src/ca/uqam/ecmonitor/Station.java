package ca.uqam.ecmonitor;

import org.json.JSONException;
import org.json.JSONObject;

/** One monitored analyzer: where it lives, plus the last thing we learned about it. */
public class Station {

    public String id;
    public String name = "";
    public String host = "";
    public int port = 7200;
    public int pollSeconds = 2;
    public String note = "";

    // Last known result, kept so the list is useful before a refresh finishes.
    public int lastSeverity = -1;
    public String lastSummary = "";
    public long lastCheck = 0;

    public Station() {
        this.id = Long.toHexString(System.currentTimeMillis()) + Integer.toHexString((int) (Math.random() * 65536));
    }

    public String title() {
        if (name != null && name.trim().length() > 0) return name.trim();
        return host + ":" + port;
    }

    public String address() {
        return host + ":" + port;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("host", host);
        o.put("port", port);
        o.put("pollSeconds", pollSeconds);
        o.put("note", note);
        o.put("lastSeverity", lastSeverity);
        o.put("lastSummary", lastSummary);
        o.put("lastCheck", lastCheck);
        return o;
    }

    public static Station fromJson(JSONObject o) {
        Station s = new Station();
        s.id = o.optString("id", s.id);
        s.name = o.optString("name", "");
        s.host = o.optString("host", "");
        s.port = o.optInt("port", 7200);
        s.pollSeconds = Math.max(1, o.optInt("pollSeconds", 2));
        s.note = o.optString("note", "");
        s.lastSeverity = o.optInt("lastSeverity", -1);
        s.lastSummary = o.optString("lastSummary", "");
        s.lastCheck = o.optLong("lastCheck", 0);
        return s;
    }
}
