package ca.uqam.ecmonitor.net;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

import ca.uqam.ecmonitor.proto.Node;
import ca.uqam.ecmonitor.proto.Parser;
import ca.uqam.ecmonitor.proto.Queries;
import ca.uqam.ecmonitor.proto.Snapshot;

/**
 * Read-only polling client for a LI-7x00 analyzer on TCP port 7200.
 *
 * The instrument answers queries and, depending on its own output configuration,
 * may also push data records continuously. Both are handled. Nothing is ever
 * written to the instrument's configuration: every command sent goes through
 * Queries.isReadOnly first.
 */
public class Client implements Runnable {

    public static final int STATE_IDLE = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;
    public static final int STATE_RETRYING = 3;
    public static final int STATE_STOPPED = 4;

    public interface Listener {
        /** Called off the UI thread whenever state or data changed. */
        void onClientEvent(int state, String message);
    }

    private final String host;
    private final int port;
    private final int pollMillis;
    private final Snapshot snapshot;
    private final Traffic traffic;
    private final Listener listener;

    private volatile boolean running = false;
    private volatile int state = STATE_IDLE;
    private volatile String stateMessage = "";
    private Thread thread;

    private final List<String> pending = new ArrayList<String>();
    private Socket socket;
    private OutputStream out;

    public Client(String host, int port, int pollMillis, Snapshot snapshot, Traffic traffic, Listener listener) {
        this.host = host;
        this.port = port;
        this.pollMillis = Math.max(500, pollMillis);
        this.snapshot = snapshot;
        this.traffic = traffic;
        this.listener = listener;
    }

    public int state() {
        return state;
    }

    public String stateMessage() {
        return stateMessage;
    }

    public void start() {
        if (running) return;
        running = true;
        thread = new Thread(this, "ecmon-" + host);
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        closeQuietly();
        Thread t = thread;
        if (t != null) t.interrupt();
        setState(STATE_STOPPED, "Disconnected");
    }

    /** Queue a user-typed query. Refused unless it is a pure read. */
    public boolean submit(String command) {
        if (!Queries.isReadOnly(command)) {
            traffic.add(Traffic.NOTE, "Refused, this app only sends read queries: " + command);
            fire();
            return false;
        }
        synchronized (pending) {
            pending.add(command.trim());
        }
        return true;
    }

    private void setState(int s, String msg) {
        state = s;
        stateMessage = msg == null ? "" : msg;
        fire();
    }

    private void fire() {
        if (listener != null) listener.onClientEvent(state, stateMessage);
    }

    @Override
    public void run() {
        int backoff = 2000;
        while (running) {
            try {
                setState(STATE_CONNECTING, "Connecting to " + host + ":" + port);
                traffic.add(Traffic.NOTE, "Connecting to " + host + ":" + port);
                socket = new Socket();
                socket.connect(new InetSocketAddress(host, port), 10000);
                socket.setSoTimeout(400);
                socket.setTcpNoDelay(true);
                out = socket.getOutputStream();
                InputStream in = socket.getInputStream();

                setState(STATE_CONNECTED, "Connected");
                traffic.add(Traffic.NOTE, "Connected");
                backoff = 2000;

                for (int i = 0; i < Queries.ON_CONNECT.length; i++) send(Queries.ON_CONNECT[i]);
                for (int i = 0; i < Queries.SLOW.length; i++) send(Queries.SLOW[i]);

                pumpSession(in);
            } catch (Exception e) {
                String msg = describe(e);
                traffic.add(Traffic.NOTE, "Connection problem: " + msg);
                setState(STATE_RETRYING, msg);
            } finally {
                closeQuietly();
            }

            if (!running) break;
            sleep(backoff);
            backoff = Math.min(backoff * 2, 30000);
        }
        setState(STATE_STOPPED, "Stopped");
    }

    private void pumpSession(InputStream in) throws Exception {
        Parser.Splitter splitter = new Parser.Splitter();
        byte[] buf = new byte[8192];
        long nextFast = 0;
        long nextSlow = System.currentTimeMillis() + 30000;
        String[] labelLessFields = null;
        boolean fieldsResolved = false;

        while (running && socket != null && !socket.isClosed()) {
            long now = System.currentTimeMillis();

            if (now >= nextFast) {
                for (int i = 0; i < Queries.FAST.length; i++) send(Queries.FAST[i]);
                nextFast = now + pollMillis;
            }
            if (now >= nextSlow) {
                for (int i = 0; i < Queries.SLOW.length; i++) send(Queries.SLOW[i]);
                nextSlow = now + 30000;
            }
            synchronized (pending) {
                for (int i = 0; i < pending.size(); i++) send(pending.get(i));
                pending.clear();
            }

            int n;
            try {
                n = in.read(buf);
            } catch (SocketTimeoutException te) {
                continue;
            }
            if (n < 0) throw new java.io.IOException("Instrument closed the connection");
            if (n == 0) continue;

            splitter.feed(new String(buf, 0, n, "US-ASCII"));

            List<String> records = splitter.takeRecords();
            int start = Math.max(0, records.size() - 40); // never let a fast stream pile up
            for (int i = start; i < records.size(); i++) {
                String rec = records.get(i);
                traffic.add(Traffic.IN, rec);
                Node node = Parser.parse(rec);
                if (node != null) {
                    snapshot.putRecord(node);
                    if (node.name.equalsIgnoreCase("Outputs")) fieldsResolved = false;
                }
            }

            List<String> raw = splitter.takeRawLines();
            if (!raw.isEmpty()) {
                if (!fieldsResolved) {
                    labelLessFields = Queries.labelLessFields(snapshot.record("Outputs"), snapshot.enclosed());
                    fieldsResolved = true;
                }
                int rs = Math.max(0, raw.size() - 10);
                for (int i = rs; i < raw.size(); i++) {
                    String line = raw.get(i);
                    traffic.add(Traffic.IN, line);
                    if (labelLessFields != null && labelLessFields.length > 0 && Queries.looksNumeric(line)) {
                        snapshot.putLabelLess(labelLessFields, Queries.splitValues(line));
                    }
                }
            }

            if (!records.isEmpty() || !raw.isEmpty()) fire();
        }
    }

    private void send(String command) {
        try {
            if (out == null) return;
            if (!Queries.isReadOnly(command)) {
                traffic.add(Traffic.NOTE, "Blocked non-read command: " + command);
                return;
            }
            out.write((command + "\n").getBytes("US-ASCII"));
            out.flush();
            traffic.add(Traffic.OUT, command);
        } catch (Exception e) {
            // the read loop will notice the broken socket
        }
    }

    private void closeQuietly() {
        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {
        }
        socket = null;
        out = null;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    public static String describe(Exception e) {
        if (e == null) return "Unknown error";
        String n = e.getClass().getSimpleName();
        String m = e.getMessage();
        if (n.contains("UnknownHost")) return "Address not found: check the host name";
        if (n.contains("ConnectException")) return "Connection refused or unreachable";
        if (n.contains("SocketTimeout")) return "Timed out";
        if (n.contains("NoRouteToHost")) return "No route to host";
        return m == null || m.length() == 0 ? n : m;
    }

    /**
     * One-shot health check used by the station list: connect, ask everything
     * once, collect answers for a short while, disconnect.
     */
    public static String probe(String host, int port, int budgetMillis, Snapshot snapshot, Traffic traffic) {
        Socket s = null;
        try {
            s = new Socket();
            s.connect(new InetSocketAddress(host, port), Math.min(8000, budgetMillis));
            s.setSoTimeout(500);
            s.setTcpNoDelay(true);
            OutputStream os = s.getOutputStream();
            InputStream is = s.getInputStream();

            List<String> all = new ArrayList<String>();
            for (int i = 0; i < Queries.ON_CONNECT.length; i++) all.add(Queries.ON_CONNECT[i]);
            for (int i = 0; i < Queries.SLOW.length; i++) all.add(Queries.SLOW[i]);
            for (int i = 0; i < Queries.FAST.length; i++) all.add(Queries.FAST[i]);

            for (int i = 0; i < all.size(); i++) {
                String q = all.get(i);
                os.write((q + "\n").getBytes("US-ASCII"));
                if (traffic != null) traffic.add(Traffic.OUT, q);
            }
            os.flush();

            Parser.Splitter splitter = new Parser.Splitter();
            byte[] buf = new byte[8192];
            long deadline = System.currentTimeMillis() + budgetMillis;
            while (System.currentTimeMillis() < deadline) {
                int n;
                try {
                    n = is.read(buf);
                } catch (SocketTimeoutException te) {
                    continue;
                }
                if (n <= 0) break;
                splitter.feed(new String(buf, 0, n, "US-ASCII"));
                List<String> recs = splitter.takeRecords();
                for (int i = 0; i < recs.size(); i++) {
                    if (traffic != null) traffic.add(Traffic.IN, recs.get(i));
                    Node node = Parser.parse(recs.get(i));
                    if (node != null) snapshot.putRecord(node);
                }
                if (snapshot.lastData() > 0 && snapshot.record("EmbeddedSW") != null) {
                    // keep a little longer so the slower records arrive too
                    if (System.currentTimeMillis() > deadline - 500) break;
                }
            }
            return null;
        } catch (Exception e) {
            return describe(e);
        } finally {
            try {
                if (s != null) s.close();
            } catch (Exception ignored) {
            }
        }
    }
}
