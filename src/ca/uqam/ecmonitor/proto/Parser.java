package ca.uqam.ecmonitor.proto;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser for the LI-COR parenthetical grammar used by the LI-7500/LI-7200 family
 * on RS-232 and on TCP port 7200.
 *
 * Examples handled:
 *   (Data (Ndx 1545)(DiagVal 250)(CO2D 3.2183277e1)(Temp 2.4227569e1))
 *   (Diagnostics (SYNC TRUE)(PLL TRUE)(DetOK TRUE)(Chopper TRUE)(Path 63))
 *   (EmbeddedSW (Version 4.0.0)(Model LI-7x00RS CO2/H2O Analyzer)(DSP 4.0.0)(FPGA 4.0.0|))
 *   (Outputs (BW 10)(RS232 (Baud 38400)(EOL "0D0A")))
 *   (Calibrate (ZeroCO2 (Val 0.8945)(Date 26 08 2009 10:37))(Span2CO2 (Target )))
 */
public class Parser {

    /** Parses the first complete record found in s. Returns null when there is none. */
    public static Node parse(String s) {
        if (s == null) return null;
        int i = s.indexOf('(');
        if (i < 0) return null;
        int[] pos = new int[]{i};
        try {
            return parseNode(s, pos);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Node parseNode(String s, int[] pos) {
        int n = s.length();
        int i = pos[0];
        while (i < n && s.charAt(i) != '(') i++;
        if (i >= n) return null;
        i++; // consume '('

        // name
        StringBuilder nameSb = new StringBuilder();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '(' || c == ')' || isSpace(c)) break;
            nameSb.append(c);
            i++;
        }
        Node node = new Node(nameSb.toString());

        // skip whitespace after the name
        while (i < n && isSpace(s.charAt(i))) i++;

        if (i < n && s.charAt(i) == '(') {
            // container: parse children until the matching ')'
            while (i < n) {
                while (i < n && isSpace(s.charAt(i))) i++;
                if (i >= n) break;
                char c = s.charAt(i);
                if (c == ')') {
                    i++;
                    break;
                }
                if (c == '(') {
                    pos[0] = i;
                    Node child = parseNode(s, pos);
                    if (child == null) break;
                    node.children.add(child);
                    i = pos[0];
                } else {
                    // stray text inside a container, skip it
                    i++;
                }
            }
        } else {
            // leaf: raw value up to the closing ')' at this level
            StringBuilder val = new StringBuilder();
            boolean inQuote = false;
            while (i < n) {
                char c = s.charAt(i);
                if (c == '"') {
                    inQuote = !inQuote;
                    val.append(c);
                    i++;
                    continue;
                }
                if (!inQuote && c == ')') {
                    i++;
                    break;
                }
                val.append(c);
                i++;
            }
            node.value = trimValue(val.toString());
        }

        pos[0] = i;
        return node;
    }

    private static String trimValue(String v) {
        String t = v.trim();
        if (t.length() >= 2 && t.charAt(0) == '"' && t.charAt(t.length() - 1) == '"') {
            t = t.substring(1, t.length() - 1);
        }
        return t;
    }

    private static boolean isSpace(char c) {
        return c == ' ' || c == '\t' || c == '\r' || c == '\n';
    }

    /**
     * Splits a TCP byte stream into complete top level records.
     *
     * The instrument may concatenate records, split one across reads, and - when
     * (Labels FALSE) is configured - emit tab delimited numeric lines with no
     * parentheses at all. Both shapes are surfaced.
     */
    public static class Splitter {

        private static final int MAX_BUFFER = 256 * 1024;

        private final StringBuilder buf = new StringBuilder();
        private final List<String> records = new ArrayList<String>();
        private final List<String> rawLines = new ArrayList<String>();

        public void feed(String chunk) {
            if (chunk == null || chunk.length() == 0) return;
            buf.append(chunk);
            if (buf.length() > MAX_BUFFER) {
                // Runaway stream with no balanced record: keep only the tail.
                buf.delete(0, buf.length() - 8192);
            }
            extract();
        }

        private void extract() {
            int depth = 0;
            boolean inQuote = false;
            int recStart = -1;
            int consumed = 0;
            int lineStart = 0;

            for (int i = 0; i < buf.length(); i++) {
                char c = buf.charAt(i);

                if (c == '"') {
                    inQuote = !inQuote;
                    continue;
                }
                if (inQuote) continue;

                if (c == '(') {
                    if (depth == 0) {
                        // text before this record may be a label-less data line
                        emitRaw(buf.substring(lineStart, i));
                        recStart = i;
                    }
                    depth++;
                } else if (c == ')') {
                    if (depth > 0) {
                        depth--;
                        if (depth == 0 && recStart >= 0) {
                            records.add(buf.substring(recStart, i + 1));
                            consumed = i + 1;
                            lineStart = i + 1;
                            recStart = -1;
                        }
                    }
                } else if (depth == 0 && (c == '\n' || c == '\r')) {
                    emitRaw(buf.substring(lineStart, i));
                    lineStart = i + 1;
                    consumed = i + 1;
                }
            }

            if (depth == 0 && lineStart > consumed) consumed = lineStart;
            if (consumed > 0) buf.delete(0, consumed);
        }

        private void emitRaw(String s) {
            if (s == null) return;
            String t = s.trim();
            if (t.length() == 0) return;
            rawLines.add(t);
            if (rawLines.size() > 200) rawLines.remove(0);
        }

        /** Complete records received since the last call. */
        public List<String> takeRecords() {
            List<String> out = new ArrayList<String>(records);
            records.clear();
            return out;
        }

        /** Non-parenthetical lines received since the last call (label-less data mode). */
        public List<String> takeRawLines() {
            List<String> out = new ArrayList<String>(rawLines);
            rawLines.clear();
            return out;
        }
    }
}
