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
 * The byte relay makes its TCP connection use the selected Android Network, even with a VPN.
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

    @Synchronized fun start(endpoint: HouseEndpoint, rendererId: String) {
        if (process?.isAlive == true && (ready || SystemClock.elapsedRealtime() - startedAt < 10_000)) return
        close()
        try {
            val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
            listener = server
            thread(name = "house-audio-lan", isDaemon = true) {
                while (!server.isClosed) {
                    var local: Socket? = null
                    var remote: Socket? = null
                    try {
                        local = server.accept().also { sockets.add(it); it.tcpNoDelay = true }
                        remote = endpoint.network.socketFactory.createSocket().also { sockets.add(it); it.tcpNoDelay = true }
                        remote.connect(InetSocketAddress(endpoint.network.getAllByName(endpoint.host).first(), 1704), 2000)
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
                "--player", "opensl", "--logsink", "stdout", "--logfilter", "*:info")
                .redirectErrorStream(true).start()
            process = child
            startedAt = SystemClock.elapsedRealtime()
            error = null
            thread(name = "house-audio-events", isDaemon = true) {
                try {
                    child.inputStream.bufferedReader().useLines { lines -> lines.forEach { line ->
                        if (process === child) {
                            if (line.contains("(OpenSlPlayer)") && line.endsWith("Init done")) {
                                ready = true; changed()
                            } else if (line.contains("uninitOpensl") || line.contains("[Error]") || line.contains("Failed to connect")) {
                                ready = false; changed()
                            }
                        }
                    } }
                } catch (_: Exception) { }
                finally {
                    if (process === child) { ready = false; error = "Audio reconnecting"; changed() }
                }
            }
        } catch (_: Exception) { close(); error = "Audio receiver unavailable"; changed() }
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
