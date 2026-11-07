package org.oolah.s24backup;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class BackupConfig {
    private static final String PREFS = "s24_backup";
    private static final String KEY_NAME = "s24backup_password_aes_v1";

    public static class Server {
        public String host = "192.168.1.12";
        public int port = 445;
        public String share = "ShareDrive";
        public String user = "";
        public String password = "";
        public String domain = "";
        public String destination = "S24 Backup";

        public void validate() {
            if (host.trim().isEmpty() || share.trim().isEmpty() || user.trim().isEmpty()) {
                throw new IllegalArgumentException("Server, share, and username are required.");
            }
            if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid port.");
            RemotePaths.safe(destination);
        }
    }

    public static class Folder {
        public final String path;
        public boolean enabled;
        public Folder(String path, boolean enabled) {
            this.path = path;
            this.enabled = enabled;
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean hasServer(Context context) {
        return prefs(context).getBoolean("serverSaved", false);
    }

    public static Server getServer(Context context) throws Exception {
        SharedPreferences p = prefs(context);
        Server c = new Server();
        if (!hasServer(context)) return c;
        c.host = p.getString("host", c.host);
        c.port = p.getInt("port", c.port);
        c.share = p.getString("share", c.share);
        c.user = p.getString("user", "");
        c.domain = p.getString("domain", "");
        c.destination = p.getString("destination", c.destination);
        String sealed = p.getString("password", "");
        c.password = sealed.isEmpty() ? "" : decrypt(sealed);
        return c;
    }

    public static void saveServer(Context context, Server s) throws Exception {
        s.validate();
        String encrypted = encrypt(s.password);
        boolean ok = prefs(context).edit()
                .putString("host", s.host.trim())
                .putInt("port", s.port)
                .putString("share", s.share.trim())
                .putString("user", s.user.trim())
                .putString("password", encrypted)
                .putString("domain", s.domain.trim())
                .putString("destination", RemotePaths.safe(s.destination))
                .putBoolean("serverSaved", true).commit();
        if (!ok) throw new IllegalStateException("Android failed to save settings.");
    }

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(KEY_NAME)) return (SecretKey) ks.getKey(KEY_NAME, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_NAME,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }

    private static String encrypt(String plain) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] iv = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
        return Base64.encodeToString(combined, Base64.NO_WRAP);
    }

    private static String decrypt(String data) throws Exception {
        byte[] combined = Base64.decode(data, Base64.NO_WRAP);
        if (combined.length <= 12) throw new IllegalStateException("Stored password is corrupt.");
        byte[] iv = java.util.Arrays.copyOfRange(combined, 0, 12);
        byte[] ciphertext = java.util.Arrays.copyOfRange(combined, 12, combined.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(ciphertext), java.nio.charset.StandardCharsets.UTF_8);
    }

    public static List<Folder> folders(Context context) {
        String json = prefs(context).getString("folders", "");
        List<Folder> result = new ArrayList<>();
        if (!json.isEmpty()) {
            try {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject o = array.getJSONObject(i);
                    String path = RemotePaths.safe(o.getString("path")).replace('\\', '/');
                    if (!path.isEmpty()) result.add(new Folder(path, o.optBoolean("enabled", true)));
                }
            } catch (Exception ignored) { /* fall back to DCIM */ }
        }
        boolean found = false;
        for (Folder f : result) if (f.path.equalsIgnoreCase("DCIM")) found = true;
        if (!found) result.add(0, new Folder("DCIM", true));
        return result;
    }

    public static void saveFolders(Context context, List<Folder> folders) {
        JSONArray array = new JSONArray();
        for (Folder folder : folders) {
            JSONObject o = new JSONObject();
            try {
                o.put("path", RemotePaths.safe(folder.path).replace('\\', '/'));
                o.put("enabled", folder.enabled);
                array.put(o);
            } catch (Exception ex) { throw new IllegalArgumentException("Bad folder name", ex); }
        }
        if (!prefs(context).edit().putString("folders", array.toString()).commit())
            throw new IllegalStateException("Could not save selected folders.");
    }

    public static void status(Context c, String text) {
        prefs(c).edit().putString("status", text).apply();
    }
    public static String status(Context c) {
        return prefs(c).getString("status", "No backup has run yet.");
    }
    public static void success(Context c, int copied, int skipped) {
        prefs(c).edit().putLong("lastSuccess", System.currentTimeMillis())
                .putString("status", "Backup completed: " + copied + " copied, " + skipped + " unchanged.")
                .apply();
    }
    public static long lastSuccess(Context c) {
        return prefs(c).getLong("lastSuccess", 0L);
    }
}
