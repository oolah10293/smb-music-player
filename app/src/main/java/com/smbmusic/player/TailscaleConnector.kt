package com.smbmusic.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/** One app-wide, paced request path, available without a browser Activity. */
internal object TailscaleConnector {
    private val gate = ConnectRequestGate()
    @Synchronized fun request(context: Context, reason: String) {
        if (!gate.allow(SystemClock.elapsedRealtime())) return
        val app = context.applicationContext
        val intent = Intent("com.tailscale.ipn.CONNECT_VPN")
            .setComponent(ComponentName("com.tailscale.ipn", "com.tailscale.ipn.IPNReceiver"))
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        try {
            if (app.packageManager.queryBroadcastReceivers(intent, 0).isEmpty()) {
                PlaybackDiagnostics.record(app, "Tailscale receiver unavailable")
                return
            }
            app.sendBroadcast(intent)
            PlaybackDiagnostics.record(app, "Tailscale connect requested: $reason (SMB check still required)")
        } catch (e: Exception) {
            PlaybackDiagnostics.record(app, "Tailscale request failed: ${e.javaClass.simpleName}")
        }
    }
}
