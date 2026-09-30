package com.smbmusic.player.house

import android.content.Context
import android.os.SystemClock
import java.io.Closeable
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/** Runs the bundled upstream Snapclient; it owns decoding, timing, and clock correction.
 * The byte relay uses normal Android routing after physical home-LAN qualification.
 * Muting destroys the local process/output; reconnecting always joins current stream time.
 */
class SnapcastReceiver(private val context: Context, private val changed: () -> Unit) : Closeable {
    @Volatile private var process: Process? = null
    private var listener: ServerSocket? = null
    private val sockets = CopyOnWriteArrayList<Socket>()
    @Volatile var ready = false
        private set
    @Volatile var error: String? = null
        private set
    private var startedAt = 0L
    @Volatile private var failedAt = 0L
    private var playingEndpoint: HouseEndpoint? = null
    private var latencyMs = 0
    @Volatile var timing = HouseAudioTiming()
        private set

    @Synchronized fun start(endpoint: HouseEndpoint, rendererId: String, offsetMs: Int = 0) {
        // Error callbacks also reconcile output. Do not spawn a tight crash/retry loop.
        if (playingEndpoint == endpoint && latencyMs == offsetMs && failedAt != 0L &&
            SystemClock.elapsedRealtime() - failedAt < 2000) return
        if (process?.isAlive == true && playingEndpoint == endpoint && latencyMs == offsetMs &&
            (ready || SystemClock.elapsedRealtime() - startedAt < 10_000)) return
        close()
        playingEndpoint = endpoint
        latencyMs = offsetMs
        try {
            val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
            listener = server
            thread(name = "house-audio-lan", isDaemon = true) {
                while (!server.isClosed) {
                    var local: Socket? = null
                    var remote: Socket? = null
                    try {
                        local = server.accept().also { sockets.add(it); it.tcpNoDelay = true }
                        check(HouseConnection.isPresent(context, endpoint)) { "Home network unavailable" }
                        remote = Socket().also { sockets.add(it); it.tcpNoDelay = true }
                        remote.connect(InetSocketAddress(endpoint.address, 1704), 2000)
                        val inbound = local
                        val outbound = remote
                        thread(name = "house-audio-up", isDaemon = true) {
                            try { inbound.getInputStream().copyTo(outbound.getOutputStream()) }
                            catch (_: Exception) { }
                            finally { runCatching { outbound.close() }; runCatching { inbound.close() } }
                        }
                        remote.getInputStream().copyTo(local.getOutputStream())
                    } catch (_: Exception) { }
                    finally {
                        local?.let { runCatching { it.close() }; sockets.remove(it) }
                        remote?.let { runCatching { it.close() }; sockets.remove(it) }
                        if (listener === server) { ready = false; changed() }
                    }
                }
            }
            val child = ProcessBuilder(context.applicationInfo.nativeLibraryDir + "/libsnapclient.so",
                "--host", "127.0.0.1", "--port", server.localPort.toString(), "--hostID", rendererId,
                "--player", "opensl", "--latency", offsetMs.toString(), "--logsink", "stdout", "--logfilter", "*:info")
                .redirectErrorStream(true).start()
            process = child
            startedAt = SystemClock.elapsedRealtime()
            error = null
            failedAt = 0L
            thread(name = "house-audio-events", isDaemon = true) {
                try {
                    child.inputStream.bufferedReader().useLines { lines -> lines.forEach { line ->
                        if (process === child) {
                            HouseAudioTiming.fromLog(line)?.let { reported ->
                                if (reported != timing) { timing = reported; changed() }
                            }
                            if (line.contains("(OpenSlPlayer)") && line.endsWith("Init done")) {
                                ready = true; changed()
                            } else if (line.contains("uninitOpensl") || line.contains("[Error]") || line.contains("Failed to connect")) {
                                ready = false; changed()
                            }
                        }
                    } }
                } catch (_: Exception) { }
                finally {
                    if (process === child) {
                        failedAt = SystemClock.elapsedRealtime()
                        ready = false; error = "Audio reconnecting"; changed()
                    }
                }
            }
        } catch (_: Exception) {
            close()
            failedAt = SystemClock.elapsedRealtime()
            error = "Audio receiver unavailable"
            changed()
        }
    }

    @Synchronized override fun close() {
        val old = process
        process = null
        ready = false
        runCatching { listener?.close() }
        listener = null
        sockets.forEach { runCatching { it.close() } }
        sockets.clear()
        // Stop local sound immediately, independently of server/control availability.
        old?.destroyForcibly()
    }
}
