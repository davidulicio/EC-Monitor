package ca.uqam.ecmonitor.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small toolkit for building the screens in code: colours, spacing, cards and rows. */
public class Ui {

    public static final int BG = 0xFF0F1419;
    public static final int CARD = 0xFF1A212A;
    public static final int CARD_EDGE = 0xFF27313D;
    public static final int TEXT = 0xFFE6EDF3;
    public static final int MUTED = 0xFF8B98A5;
    public static final int ACCENT = 0xFF4FC3F7;
    public static final int OK = 0xFF57C97A;
    public static final int WARN = 0xFFF0B429;
    public static final int CRIT = 0xFFEF5350;
    public static final int INFO = 0xFF7E8FA6;

    public static int severityColor(int severity) {
        switch (severity) {
            case 3: return CRIT;
            case 2: return WARN;
            case 1: return INFO;
            default: return OK;
        }
    }

    public static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics());
    }

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams weighted(float weight) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        return p;
    }

    public static TextView text(Context c, String s, int size, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s == null ? "" : s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    public static TextView mono(Context c, String s, int size, int color) {
        TextView t = text(c, s, size, color, false);
        t.setTypeface(Typeface.MONOSPACE);
        return t;
    }

    /** Rounded panel used for every block of content. */
    public static LinearLayout card(Context c) {
        LinearLayout l = column(c);
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(c, 12));
        g.setStroke(dp(c, 1), CARD_EDGE);
        l.setBackground(g);
        int p = dp(c, 14);
        l.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = match();
        lp.bottomMargin = dp(c, 10);
        l.setLayoutParams(lp);
        return l;
    }

    public static TextView cardTitle(Context c, String s) {
        TextView t = text(c, s.toUpperCase(), 11, MUTED, true);
        t.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams lp = match();
        lp.bottomMargin = dp(c, 10);
        t.setLayoutParams(lp);
        return t;
    }

    /** Label on the left, value on the right. */
    public static LinearLayout kv(Context c, String label, String value, int valueColor) {
        LinearLayout r = row(c);
        LinearLayout.LayoutParams lp = match();
        lp.bottomMargin = dp(c, 7);
        r.setLayoutParams(lp);
        TextView l = text(c, label, 14, MUTED, false);
        l.setLayoutParams(weighted(1f));
        TextView v = text(c, value, 14, valueColor, true);
        v.setGravity(Gravity.END);
        r.addView(l);
        r.addView(v);
        return r;
    }

    /** Big number with a caption, used in the overview grid. */
    public static LinearLayout tile(Context c, String caption, String value, String unit, int color) {
        LinearLayout t = column(c);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF141B23);
        g.setCornerRadius(dp(c, 10));
        g.setStroke(dp(c, 1), CARD_EDGE);
        t.setBackground(g);
        int p = dp(c, 10);
        t.setPadding(p, p, p, p);

        TextView cap = text(c, caption, 11, MUTED, false);
        cap.setMaxLines(2);
        t.addView(cap);

        LinearLayout vr = row(c);
        LinearLayout.LayoutParams vlp = match();
        vlp.topMargin = dp(c, 4);
        vr.setLayoutParams(vlp);
        TextView v = text(c, value, 20, color, true);
        vr.addView(v);
        if (unit != null && unit.length() > 0) {
            TextView u = text(c, " " + unit, 11, MUTED, false);
            LinearLayout.LayoutParams ulp = wrap();
            ulp.bottomMargin = dp(c, 3);
            u.setLayoutParams(ulp);
            vr.addView(u);
            vr.setGravity(Gravity.BOTTOM);
        }
        t.addView(vr);
        return t;
    }

    /** Coloured pill, for status and severity. */
    public static TextView chip(Context c, String label, int color) {
        TextView t = text(c, label, 11, color, true);
        GradientDrawable g = new GradientDrawable();
        g.setColor(withAlpha(color, 0x22));
        g.setCornerRadius(dp(c, 20));
        g.setStroke(dp(c, 1), withAlpha(color, 0x66));
        t.setBackground(g);
        t.setPadding(dp(c, 9), dp(c, 4), dp(c, 9), dp(c, 4));
        return t;
    }

    public static View dot(Context c, int color, int sizeDp) {
        View v = new View(c);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        v.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp));
        lp.rightMargin = dp(c, 10);
        v.setLayoutParams(lp);
        return v;
    }

    public static Button button(Context c, String label, boolean primary) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setTextColor(primary ? 0xFF06202B : TEXT);
        GradientDrawable g = new GradientDrawable();
        g.setColor(primary ? ACCENT : 0xFF222C37);
        g.setCornerRadius(dp(c, 8));
        b.setBackground(g);
        b.setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8));
        b.setMinHeight(dp(c, 42));
        b.setMinimumHeight(dp(c, 42));
        return b;
    }

    /** Tab strip that reports the selected index. */
    public interface OnTab {
        void onTab(int index);
    }

    public static LinearLayout tabs(final Context c, final String[] labels, final int selected, final OnTab cb) {
        LinearLayout bar = row(c);
        LinearLayout.LayoutParams lp = match();
        lp.bottomMargin = dp(c, 12);
        bar.setLayoutParams(lp);
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView t = text(c, labels[i], 13, index == selected ? 0xFF06202B : MUTED, index == selected);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(c, 6), dp(c, 9), dp(c, 6), dp(c, 9));
            GradientDrawable g = new GradientDrawable();
            g.setColor(index == selected ? ACCENT : 0xFF161D25);
            g.setCornerRadius(dp(c, 8));
            t.setBackground(g);
            LinearLayout.LayoutParams tp = weighted(1f);
            if (i > 0) tp.leftMargin = dp(c, 6);
            t.setLayoutParams(tp);
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    cb.onTab(index);
                }
            });
            bar.addView(t);
        }
        return bar;
    }

    public static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    public static View spacer(Context c, int heightDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, heightDp)));
        return v;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(CARD_EDGE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 0.5f)));
        lp.topMargin = dp(c, 6);
        lp.bottomMargin = dp(c, 10);
        v.setLayoutParams(lp);
        return v;
    }
}
