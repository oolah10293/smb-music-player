package org.oolah.s24backup;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

public final class FolderPickerActivity extends Activity {
    private File root;
    private File current;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        root = Environment.getExternalStorageDirectory();
        current = root;
        show();
    }

    private void show() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = Ui.column(this);
        scroll.addView(layout);
        setContentView(scroll);
        Ui.heading(this, layout, "Choose Folder");
        String relative = root.toPath().relativize(current.toPath()).toString();
        layout.addView(Ui.text(this,
                relative.isEmpty() ? "Internal storage" : "Internal storage/" + relative,
                16, Ui.INK));
        if (!current.equals(root)) {
            Button choose = Ui.button(this, "Use This Folder", true);
            choose.setOnClickListener(v -> {
                Intent data = new Intent();
                data.putExtra("folder", root.toPath().relativize(current.toPath())
                        .toString().replace(File.separatorChar, '/'));
                setResult(RESULT_OK, data);
                finish();
            });
            layout.addView(choose);
            Button up = Ui.button(this, "↑ Parent folder", false);
            up.setOnClickListener(v -> {
                current = current.getParentFile();
                show();
            });
            layout.addView(up);
        }
        File[] items = current.listFiles(File::isDirectory);
        if (items == null) {
            layout.addView(Ui.text(this, "No access to this folder.", 14, Ui.MUTED));
            return;
        }
        Arrays.sort(items, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File dir : items) {
            Button b = Ui.button(this, "📁 " + dir.getName(), false);
            b.setOnClickListener(v -> {
                try {
                    File next = dir.getCanonicalFile();
                    if (!next.toPath().startsWith(root.getCanonicalFile().toPath())) return;
                    current = next;
                    show();
                } catch (Exception e) {
                    android.widget.Toast.makeText(this, e.toString(), android.widget.Toast.LENGTH_SHORT).show();
                }
            });
            layout.addView(b);
        }
    }

    @Override public void onBackPressed() {
        if (!current.equals(root)) {
            current = current.getParentFile();
            show();
        } else super.onBackPressed();
    }
}
