package com.smbmusic.player

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import com.smbmusic.player.storage.StandaloneSessionStore

/** CPU protection for bounded work; an inexact wakeup is a fallback, not a 15-second Doze promise. */
internal class RecoveryWake(private val context: Context) {
    private val power = context.getSystemService(PowerManager::class.java)
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val lock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SMBMusic:recovery").apply { setReferenceCounted(false) }
    private val pending = PendingIntent.getBroadcast(context, 6201, Intent(context, RecoveryWakeReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private var burstUntil = 0L
    fun beginBurst() {
        burstUntil = SystemClock.elapsedRealtime() + 90_000L
        work(90_000L)
    }
    fun work(timeout: Long = 35_000L) {
        lock.acquire(maxOf(timeout, burstUntil - SystemClock.elapsedRealtime()))
    }
    fun waiting(delay: Long) {
        if (SystemClock.elapsedRealtime() >= burstUntil) release()
        // Keep Handler's normal retry pacing; this independent fallback wakes a sleeping CPU.
        alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + delay.coerceAtLeast(60_000L), pending)
    }
    fun release() { if (lock.isHeld) lock.release() }
    fun cancel() { alarms.cancel(pending); burstUntil = 0; release() }
    fun description(): String = "wake=${lock.isHeld},idle=${power.isDeviceIdleMode},exempt=${power.isIgnoringBatteryOptimizations(context.packageName)}"
}

@UnstableApi
class RecoveryWakeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val saved = StandaloneSessionStore(context).load() ?: return
        if (saved.explicitlyStopped || !saved.shouldPlay) return
        try {
            ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java)
                .setAction(PlaybackService.ACTION_RECOVERY_WAKE))
        } catch (e: Exception) {
            PlaybackDiagnostics.record(context, "Recovery wake blocked: ${e.javaClass.simpleName}")
            RecoveryWake(context).waiting(60_000)
        }
    }
}
