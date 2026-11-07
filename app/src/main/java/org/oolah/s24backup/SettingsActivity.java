package org.oolah.s24backup;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class SettingsActivity extends Activity {
    private EditText host, port, share, user, password, domain, destination;
    private TextView result;
    private String lastSavedSignature = "";

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        BackupConfig.Server c = new BackupConfig.Server();
        String error = "";
        try { c = BackupConfig.getServer(this); }
        catch (Exception e) { error = "Cannot decrypt saved password: " + e.getMessage(); }

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = Ui.column(this);
        scroll.addView(root);
        setContentView(scroll);

        Ui.heading(this, root, "SMB Settings");
        root.addView(Ui.text(this,
                "TEST checks the connection. SAVE actually saves these settings. "
                + "They are separate operations.", 14, Ui.MUTED));
        host = Ui.field(this, root, "Server address", c.host, InputType.TYPE_CLASS_TEXT);
        port = Ui.field(this, root, "Port", String.valueOf(c.port), InputType.TYPE_CLASS_NUMBER);
        share = Ui.field(this, root, "SMB share name", c.share, InputType.TYPE_CLASS_TEXT);
        user = Ui.field(this, root, "Samba username", c.user, InputType.TYPE_CLASS_TEXT);
        password = Ui.field(this, root, "Samba password", c.password,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        domain = Ui.field(this, root, "NTLM domain (usually blank)", c.domain, InputType.TYPE_CLASS_TEXT);
        destination = Ui.field(this, root, "Destination folder inside share", c.destination,
                InputType.TYPE_CLASS_TEXT);
        lastSavedSignature = signature();

        Button test = Ui.button(this, "Test Connection (does NOT save)", false);
        root.addView(test);
        Button save = Ui.button(this, "SAVE SETTINGS", true);
        root.addView(save);
        result = Ui.text(this, error.isEmpty() ? "Settings are not changed until you tap SAVE."
                : error, 15, Ui.TEAL);
        root.addView(result);

        test.setOnClickListener(v -> {
            BackupConfig.Server draft;
            try { draft = read(); draft.validate(); }
            catch (Exception ex) { result.setText("Check entries: " + ex.getMessage()); return; }
            test.setEnabled(false);
            result.setText("Testing SMB connection... nothing has been saved.");
            new Thread(() -> {
                try {
                    SmbTransport.test(draft);
                    runOnUiThread(() -> result.setText(
                            "Connection successful. Tap SAVE SETTINGS to keep these entries."));
                } catch (Exception ex) {
                    runOnUiThread(() -> result.setText(
                            "Connection failed: " + ex.getClass().getSimpleName() + " — "
                                    + ex.getMessage() + ". Entries retained; not saved."));
                } finally {
                    runOnUiThread(() -> test.setEnabled(true));
                }
            }, "SMB-test").start();
        });
        save.setOnClickListener(v -> {
            try {
                BackupConfig.saveServer(this, read());
                BackupScheduler.schedule(this);
                lastSavedSignature = signature();
                result.setText("✓ Settings saved successfully. You can safely leave this screen.");
                Toast.makeText(this, "SMB settings saved", Toast.LENGTH_LONG).show();
            } catch (Exception ex) {
                result.setText("NOT SAVED: " + ex.getMessage());
            }
        });
    }

    private BackupConfig.Server read() {
        BackupConfig.Server c = new BackupConfig.Server();
        c.host = host.getText().toString().trim();
        try { c.port = Integer.parseInt(port.getText().toString().trim()); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("Enter a valid port."); }
        c.share = share.getText().toString().trim();
        c.user = user.getText().toString().trim();
        c.password = password.getText().toString();
        c.domain = domain.getText().toString().trim();
        c.destination = destination.getText().toString().trim();
        return c;
    }

    private String signature() {
        return host.getText().toString() + "\u0000" + port.getText().toString()
                + "\u0000" + share.getText().toString()
                + "\u0000" + user.getText().toString()
                + "\u0000" + password.getText().toString()
                + "\u0000" + domain.getText().toString()
                + "\u0000" + destination.getText().toString();
    }

    @Override public void onBackPressed() {
        if (!signature().equals(lastSavedSignature)) {
            new AlertDialog.Builder(this)
                    .setTitle("Unsaved SMB settings")
                    .setMessage("You changed the settings without tapping SAVE. Leave and lose those changes?")
                    .setNegativeButton("Keep editing", null)
                    .setPositiveButton("Discard changes", (d, w) -> SettingsActivity.super.onBackPressed())
                    .show();
        } else super.onBackPressed();
    }
}
