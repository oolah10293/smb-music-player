package org.oolah.s24backup;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public final class BackupScheduler {
    private static final String AUTO = "s24-backup-periodic";
    private static final String MANUAL = "s24-backup-manual";

    private BackupScheduler() { }

    public static void schedule(Context context) {
        Constraints conditions = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .build();
        PeriodicWorkRequest periodic = new PeriodicWorkRequest.Builder(
                BackupWorker.class, 6, TimeUnit.HOURS)
                .setConstraints(conditions).build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                AUTO, ExistingPeriodicWorkPolicy.UPDATE, periodic);
    }

    public static void runNow(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(BackupWorker.class).build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                MANUAL, ExistingWorkPolicy.KEEP, request);
        BackupConfig.status(context, "Backup queued. It will begin shortly.");
    }
}
