package ca.uqam.ecmonitor.proto;

import java.util.ArrayList;
import java.util.List;

/**
 * One node of a LI-COR parenthetical record, e.g. (Data (CO2D 22.08)(Temp 25.9)).
 *
 * A node is either a leaf carrying a raw string value, or a container carrying
 * child nodes. The grammar is described in the LI-7x00 "Configuration grammar"
 * help topic shipped with the LI-COR PC software.
 */
public class Node {

    public final String name;
    public String value;
    public final List<Node> children = new ArrayList<Node>();

    public Node(String name) {
        this.name = name;
        this.value = "";
    }

    public Node(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }

    /** Direct child by name, case-insensitive. Null when absent. */
    public Node child(String childName) {
        for (int i = 0; i < children.size(); i++) {
            Node c = children.get(i);
            if (c.name.equalsIgnoreCase(childName)) return c;
        }
        return null;
    }

    /**
     * Descend a path of names, e.g. get("Status", "SmartFlux", "Vin").
     * Returns null if any step is missing.
     */
    public Node get(String... path) {
        Node cur = this;
        for (int i = 0; i < path.length; i++) {
            if (cur == null) return null;
            cur = cur.child(path[i]);
        }
        return cur;
    }

    /** Value at a path, or null. */
    public String str(String... path) {
        Node n = get(path);
        return n == null ? null : n.value;
    }

    /** Value at a path parsed as a double, or Double.NaN. */
    public double num(String... path) {
        return Fmt.toDouble(str(path));
    }

    /**
     * Flatten every leaf into a name -> value map-like list of entries.
     * Leaf names are not qualified, matching how the instrument labels data
     * fields inside a (Data ...) or (Diagnostics ...) record.
     */
    public void collectLeaves(List<String[]> out) {
        if (isLeaf()) {
            out.add(new String[]{name, value});
            return;
        }
        for (int i = 0; i < children.size(); i++) {
            children.get(i).collectLeaves(out);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        write(sb);
        return sb.toString();
    }

    private void write(StringBuilder sb) {
        sb.append('(').append(name);
        if (isLeaf()) {
            if (value != null && value.length() > 0) sb.append(' ').append(value);
        } else {
            sb.append(' ');
            for (int i = 0; i < children.size(); i++) children.get(i).write(sb);
        }
        sb.append(')');
    }
}
