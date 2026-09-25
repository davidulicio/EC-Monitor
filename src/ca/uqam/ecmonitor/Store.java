package ca.uqam.ecmonitor;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Station list persistence, held as JSON in shared preferences. */
public class Store {

    private static final String PREFS = "ecmonitor";
    private static final String KEY = "stations";

    public static List<Station> load(Context c) {
        List<Station> out = new ArrayList<Station>();
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = p.getString(KEY, "");
        if (raw == null || raw.length() == 0) return out;
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                out.add(Station.fromJson(a.getJSONObject(i)));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static void save(Context c, List<Station> stations) {
        try {
            JSONArray a = new JSONArray();
            for (int i = 0; i < stations.size(); i++) a.put(stations.get(i).toJson());
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY, a.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static Station find(List<Station> stations, String id) {
        for (int i = 0; i < stations.size(); i++) {
            if (stations.get(i).id.equals(id)) return stations.get(i);
        }
        return null;
    }

    /** Readable export, also accepted by importFrom. */
    public static String export(List<Station> stations) {
        try {
            JSONArray a = new JSONArray();
            for (int i = 0; i < stations.size(); i++) {
                Station s = stations.get(i);
                JSONObject o = new JSONObject();
                o.put("name", s.name);
                o.put("host", s.host);
                o.put("port", s.port);
                o.put("pollSeconds", s.pollSeconds);
                if (s.note != null && s.note.length() > 0) o.put("note", s.note);
                a.put(o);
            }
            return a.toString(2);
        } catch (Exception e) {
            return "[]";
        }
    }

    /**
     * Accepts either the JSON produced by export, or plain lines of
     * "name, host, port" / "name host port" for quick setup.
     * Returns the stations parsed, never null.
     */
    public static List<Station> importFrom(String text) {
        List<Station> out = new ArrayList<Station>();
        if (text == null) return out;
        String t = text.trim();
        if (t.length() == 0) return out;

        if (t.startsWith("[") || t.startsWith("{")) {
            try {
                JSONArray a = t.startsWith("[") ? new JSONArray(t) : new JSONArray().put(new JSONObject(t));
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    Station s = new Station();
                    s.name = o.optString("name", "");
                    s.host = o.optString("host", "");
                    s.port = o.optInt("port", 7200);
                    s.pollSeconds = Math.max(1, o.optInt("pollSeconds", 2));
                    s.note = o.optString("note", "");
                    if (s.host.length() > 0) out.add(s);
                }
                return out;
            } catch (Exception ignored) {
                return out;
            }
        }

        String[] lines = t.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.length() == 0 || line.startsWith("#")) continue;
            String[] parts = line.split("[,;\t ]+");
            Station s = new Station();
            if (parts.length >= 2) {
                s.name = parts[0];
                s.host = parts[1];
                if (parts.length >= 3) {
                    try {
                        s.port = Integer.parseInt(parts[2]);
                    } catch (NumberFormatException ignored) {
                    }
                }
            } else if (parts.length == 1) {
                s.host = parts[0];
                s.name = parts[0];
            }
            if (s.host.length() > 0) out.add(s);
        }
        return out;
    }
}
