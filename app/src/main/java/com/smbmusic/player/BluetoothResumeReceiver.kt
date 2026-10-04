package com.smbmusic.player

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import com.smbmusic.player.storage.StandaloneSessionStore

/** System Bluetooth broadcasts can wake a retained session without opening an Activity. */
@UnstableApi
class BluetoothResumeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (android.os.Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val audioConnect = when (intent.action) {
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED ->
                intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1) == BluetoothProfile.STATE_CONNECTED
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                // An ACL event can precede the audio route. Ignore watches/keyboards/etc.
                @Suppress("DEPRECATION")
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                runCatching { device?.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO }
                    .getOrDefault(false)
            }
            else -> false
        }
        if (!audioConnect || !StandaloneSessionStore(context).isResumeEligible()) return
        PlaybackDiagnostics.record(context, "Bluetooth connection broadcast; waking retained session")
        try {
            ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java)
                .setAction(PlaybackService.ACTION_BLUETOOTH_CONNECTED))
        } catch (e: Exception) {
            PlaybackDiagnostics.record(context, "Bluetooth service wake blocked: ${e.javaClass.simpleName}")
        }
    }
}
