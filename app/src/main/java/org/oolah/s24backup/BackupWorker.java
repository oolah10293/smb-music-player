package org.oolah.s24backup;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.hierynomus.smbj.share.DiskShare;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public final class BackupWorker extends Worker {
    private static final ReentrantLock LOCK = new ReentrantLock();
    private static final String CHANNEL_ID = "s24_backup_progress";
    private static final int NOTIFICATION_ID = 1907;
    private int copied;
    private int skipped;
    private int visited;
    private long lastStatusUpdate;

    public BackupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull @Override public Result doWork() {
        if (!LOCK.tryLock()) return Result.retry();
        BackupIndex index = new BackupIndex(getApplicationContext());
        try {
            if (!BackupConfig.hasServer(getApplicationContext())) {
                BackupConfig.status(getApplicationContext(), "Enter and save your SMB settings first.");
                return Result.failure();
            }
            if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
                BackupConfig.status(getApplicationContext(), "Grant All files access to back up DCIM.");
                return Result.failure();
            }
            // Foreground work keeps the initial large transfer active where Android permits.
            setForegroundAsync(foregroundInfo()).get(15, TimeUnit.SECONDS);
            BackupConfig.status(getApplicationContext(), "Connecting to SMB server...");
            BackupConfig.Server config = BackupConfig.getServer(getApplicationContext());
            List<BackupConfig.Folder> folders = BackupConfig.folders(getApplicationContext());
            File storageRoot = Environment.getExternalStorageDirectory().getCanonicalFile();
            SmbTransport.withShare(config, share -> {
                for (BackupConfig.Folder folder : folders) {
                    checkInterrupted();
                    if (!folder.enabled) continue;
                    File source = new File(storageRoot, folder.path).getCanonicalFile();
                    if (!source.toPath().startsWith(storageRoot.toPath()))
                        throw new IOException("Folder outside internal storage: " + folder.path);
                    if (!source.isDirectory()) throw new IOException("Folder not accessible: " + folder.path);
                    scan(share, index, config, storageRoot, source);
                }
                return null;
            });
            BackupConfig.success(getApplicationContext(), copied, skipped);
            return Result.success();
        } catch (Exception e) {
            String type = e.getClass().getSimpleName();
            String detail = e.getMessage();
            BackupConfig.status(getApplicationContext(),
                    "Backup stopped: " + type + (detail == null ? "" : " — " + detail)
                            + ". " + copied + " files saved before interruption.");
            // A future scheduled run or manual retry will continue from the successful file records.
            return Result.retry();
        } finally {
            index.close();
            LOCK.unlock();
        }
    }

    private void scan(DiskShare share, BackupIndex index, BackupConfig.Server config,
                      File storageRoot, File dir) throws Exception {
        checkInterrupted();
        File[] entries = dir.listFiles();
        if (entries == null) throw new IOException("Cannot list: " + dir.getAbsolutePath());
        for (File entry : entries) {
            checkInterrupted();
            if (entry.isDirectory()) {
                scan(share, index, config, storageRoot, entry);
            } else if (entry.isFile()) {
                uploadIfNeeded(share, index, config, storageRoot, entry);
            }
        }
    }

    private void uploadIfNeeded(DiskShare share, BackupIndex index, BackupConfig.Server config,
                                File root, File source) throws Exception {
        File canonical = source.getCanonicalFile();
        if (!canonical.toPath().startsWith(root.toPath()))
            throw new IOException("File outside storage: " + canonical.getAbsolutePath());
        String relative = root.toPath().relativize(canonical.toPath()).toString();
        String target = RemotePaths.join(config.destination, relative);
        String key = config.host + "|" + config.share + "|" + target;
        long size = canonical.length();
        long mtime = canonical.lastModified();

        if (index.isCurrent(key, size, mtime) && share.fileExists(target)
                && SmbTransport.remoteSize(share, target) == size) {
            skipped++;
        } else {
            SmbTransport.upload(share, canonical, target, this::checkInterrupted);
            index.record(key, size, mtime);
            copied++;
        }
        visited++;
        if (visited % 5 == 0 || System.currentTimeMillis() - lastStatusUpdate > 3000) {
            BackupConfig.status(getApplicationContext(),
                    "Backing up... " + copied + " copied, " + skipped + " unchanged. "
                            + "Last: " + relative);
            lastStatusUpdate = System.currentTimeMillis();
        }
    }

    private void checkInterrupted() throws IOException {
        if (isStopped() || Thread.currentThread().isInterrupted())
            throw new IOException("Interrupted; will retry later");
    }

    private ForegroundInfo foregroundInfo() {
        Context c = getApplicationContext();
        NotificationManager notifications =
                (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        notifications.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, "Photo backup", NotificationManager.IMPORTANCE_LOW));
        Notification notification = new Notification.Builder(c, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle("S24 Backup")
                .setContentText("Saving files to ShareDrive...")
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            return new ForegroundInfo(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        }
        return new ForegroundInfo(NOTIFICATION_ID, notification);
    }
}
