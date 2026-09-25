package ca.uqam.ecmonitor.net;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Ring buffer of the last lines exchanged with an instrument, for the console view. */
public class Traffic {

    public static final int OUT = 0;
    public static final int IN = 1;
    public static final int NOTE = 2;

    public static class Line {
        public final long time;
        public final int direction;
        public final String text;

        Line(int direction, String text) {
            this.time = System.currentTimeMillis();
            this.direction = direction;
            this.text = text;
        }

        public String stamp() {
            return new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(time));
        }

        public String prefix() {
            if (direction == OUT) return ">> ";
            if (direction == IN) return "<< ";
            return "-- ";
        }
    }

    private final int capacity;
    private final List<Line> lines = new ArrayList<Line>();

    public Traffic(int capacity) {
        this.capacity = capacity;
    }

    public synchronized void add(int direction, String text) {
        if (text == null) return;
        String t = text.trim();
        if (t.length() == 0) return;
        if (t.length() > 2000) t = t.substring(0, 2000) + " ...";
        lines.add(new Line(direction, t));
        while (lines.size() > capacity) lines.remove(0);
    }

    public synchronized List<Line> snapshot() {
        return new ArrayList<Line>(lines);
    }

    public synchronized void clear() {
        lines.clear();
    }

    public synchronized String asText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            sb.append(l.stamp()).append(' ').append(l.prefix()).append(l.text).append('\n');
        }
        return sb.toString();
    }
}
