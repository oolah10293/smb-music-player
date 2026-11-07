package org.oolah.s24backup;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int CHOOSE_FOLDER = 81;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status;
    private final Runnable updater = new Runnable() {
        @Override public void run() {
            if (status != null) status.setText(BackupConfig.status(MainActivity.this));
            handler.postDelayed(this, 1500);
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }
    }
    @Override protected void onResume() {
        super.onResume();
        show();
        handler.removeCallbacks(updater);
        handler.post(updater);
    }
    @Override protected void onPause() {
        handler.removeCallbacks(updater);
        super.onPause();
    }

    private boolean storageAllowed() {
        return Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager();
    }

    private void requestStorage() {
        if (Build.VERSION.SDK_INT >= 30) {
            Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            try { startActivity(i); }
            catch (Exception e) { startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); }
        }
    }

    private void show() {
        ScrollView scroller = new ScrollView(this);
        LinearLayout root = Ui.column(this);
        scroller.addView(root);
        setContentView(scroller);

        Ui.heading(this, root, "S24 Backup");
        root.addView(Ui.text(this, "One-way local photo backup. No cloud, ads, or subscriptions.", 14, Ui.MUTED));

        TextView sub = Ui.text(this, "", 15, Ui.INK);
        try {
            if (BackupConfig.hasServer(this)) {
                BackupConfig.Server s = BackupConfig.getServer(this);
                sub.setText("Destination: \\\\" + s.host + "\\" + s.share + "\\" + s.destination);
            } else sub.setText("Destination: not configured");
        } catch (Exception ex) {
            sub.setText("Unable to read saved SMB credentials: " + ex.getMessage());
        }
        root.addView(sub);

        Button server = Ui.button(this, "SMB Settings", false);
        server.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        root.addView(server);

        if (!storageAllowed()) {
            root.addView(Ui.text(this,
                    "Android needs All files access to read DCIM and other selected folders.", 14, Ui.MUTED));
            Button permit = Ui.button(this, "Grant File Access", true);
            permit.setOnClickListener(v -> requestStorage());
            root.addView(permit);
        }

        Button run = Ui.button(this, "Back Up Now", true);
        run.setOnClickListener(v -> {
            if (!BackupConfig.hasServer(this)) {
                Toast.makeText(this, "Save SMB settings first", Toast.LENGTH_LONG).show();
                startActivity(new Intent(this, SettingsActivity.class));
            } else if (!storageAllowed()) {
                requestStorage();
            } else {
                BackupScheduler.runNow(this);
                Toast.makeText(this, "Backup queued", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(run);

        TextView last = Ui.text(this, "Last successful backup: Never", 14, Ui.MUTED);
        long time = BackupConfig.lastSuccess(this);
        if (time > 0) last.setText("Last successful backup: "
                + DateFormat.getDateTimeInstance().format(new java.util.Date(time)));
        root.addView(last);
        status = Ui.text(this, BackupConfig.status(this), 14, Ui.INK);
        status.setTextIsSelectable(true);
        root.addView(status);
        root.addView(Ui.text(this,
                "Automatic checks: about every 6 hours on an unmetered connection. "
                + "Android may delay scheduled work.", 13, Ui.MUTED));

        Ui.heading(this, root, "Folders");
        root.addView(Ui.text(this, "Folders preserve their paths underneath your SMB destination.", 13, Ui.MUTED));
        List<BackupConfig.Folder> folders = BackupConfig.folders(this);
        for (BackupConfig.Folder item : folders) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, Ui.dp(this, 7), 0, Ui.dp(this, 7));
            Switch check = new Switch(this);
            check.setText(item.path);
            check.setTextSize(14);
            check.setTextColor(Ui.INK);
            check.setChecked(item.enabled);
            row.addView(check, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            check.setOnCheckedChangeListener((b, enabled) -> {
                item.enabled = enabled;
                BackupConfig.saveFolders(this, folders);
                BackupScheduler.schedule(this);
            });
            if (!item.path.equalsIgnoreCase("DCIM")) {
                Button remove = new Button(this);
                remove.setText("Remove");
                remove.setAllCaps(false);
                remove.setOnClickListener(v -> {
                    new android.app.AlertDialog.Builder(this).setTitle("Remove folder from backup?")
                            .setMessage(item.path + "\n\nFiles already on ShareDrive will not be deleted.")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Remove", (d, w) -> {
                                folders.remove(item);
                                BackupConfig.saveFolders(this, folders);
                                show();
                            }).show();
                });
                row.addView(remove);
            }
            root.addView(row);
        }
        Button add = Ui.button(this, "+ Add Folder", false);
        add.setOnClickListener(v -> {
            if (!storageAllowed()) requestStorage();
            else startActivityForResult(new Intent(this, FolderPickerActivity.class), CHOOSE_FOLDER);
        });
        root.addView(add);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CHOOSE_FOLDER && resultCode == RESULT_OK && data != null) {
            String path = data.getStringExtra("folder");
            if (path == null) return;
            List<BackupConfig.Folder> folders = BackupConfig.folders(this);
            for (BackupConfig.Folder f : folders) {
                if (f.path.equalsIgnoreCase(path)) {
                    Toast.makeText(this, "Already selected", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            folders.add(new BackupConfig.Folder(path, true));
            BackupConfig.saveFolders(this, folders);
            BackupScheduler.schedule(this);
            show();
        }
    }
}
