package org.oolah.s24backup;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.EditText;
import android.text.InputType;

public final class Ui {
    public static final int INK = Color.rgb(21, 37, 55);
    public static final int MUTED = Color.rgb(92, 108, 122);
    public static final int TEAL = Color.rgb(27, 100, 122);
    public static final int BG = Color.rgb(246, 248, 250);
    private Ui() {}

    public static int dp(Activity c, int n) {
        return Math.round(n * c.getResources().getDisplayMetrics().density);
    }
    public static LinearLayout column(Activity c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, 18), dp(c, 18), dp(c, 18), dp(c, 24));
        l.setBackgroundColor(BG);
        return l;
    }
    public static TextView text(Activity c, String value, float size, int color) {
        TextView t = new TextView(c);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setPadding(0, dp(c, 6), 0, dp(c, 6));
        return t;
    }
    public static void heading(Activity c, LinearLayout layout, String value) {
        TextView t = text(c, value, 22, INK);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(t);
    }
    public static Button button(Activity c, String label, boolean primary) {
        Button b = new Button(c);
        b.setText(label); b.setAllCaps(false); b.setTextSize(15);
        b.setTextColor(primary ? Color.WHITE : INK);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(c, 10));
        bg.setColor(primary ? TEAL : Color.WHITE);
        bg.setStroke(dp(c, 1), primary ? TEAL : 0xffccd6dd);
        b.setBackground(bg);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(c, 48));
        p.setMargins(0, dp(c, 7), 0, dp(c, 7));
        b.setLayoutParams(p);
        return b;
    }
    public static EditText field(Activity c, LinearLayout layout, String title, String value, int type) {
        TextView label = text(c, title, 13, MUTED);
        layout.addView(label);
        EditText edit = new EditText(c);
        edit.setText(value); edit.setTextSize(16); edit.setSingleLine(true);
        edit.setInputType(type);
        edit.setSelectAllOnFocus(false);
        edit.setPadding(dp(c, 12), 0, dp(c, 12), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE); bg.setCornerRadius(dp(c, 8));
        bg.setStroke(dp(c, 1), 0xffccd6dd);
        edit.setBackground(bg);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(c, 50));
        p.setMargins(0, 0, 0, dp(c, 8));
        layout.addView(edit, p);
        return edit;
    }
}
