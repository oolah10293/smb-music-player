package com.smbmusic.player.smb

import com.smbmusic.player.media.AudioFormats
import com.smbmusic.player.model.RemoteEntry
import com.smbmusic.player.storage.CredentialStore
import com.smbmusic.player.storage.SmbCredentials
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import jcifs.smb.SmbFileInputStream
import java.util.Properties

class SmbClient(private val store: CredentialStore) {
    @Volatile private var cachedContext: CIFSContext? = null
    @Volatile private var cachedFingerprint: String = ""

    fun invalidate() {
        synchronized(this) {
            cachedContext = null
            cachedFingerprint = ""
        }
    }

    fun rootUrl(): String = SmbUrl.normalize(store.load().address)

    fun list(url: String): List<RemoteEntry> {
        val directory = SmbFile(url, context())
        return try {
            directory.listFiles().mapNotNull { file ->
                try {
                    val isDir = file.isDirectory
                    val name = file.name.trimEnd('/')
                    if (!isDir && !AudioFormats.isSupported(name)) {
                        null
                    } else {
                        RemoteEntry(
                            name = name,
                            url = file.url.toExternalForm(),
                            isDirectory = isDir,
                            modified = runCatching { file.lastModified() }.getOrDefault(0L),
                            size = if (isDir) 0L else runCatching { file.length() }.getOrDefault(0L)
                        )
                    }
                } finally {
                    runCatching { file.close() }
                }
            }
        } finally {
            runCatching { directory.close() }
        }
    }

    fun test(credentials: SmbCredentials): Int {
        val root = SmbUrl.normalize(credentials.address)
        val directory = SmbFile(root, buildContext(credentials))
        return try {
            directory.connect()
            directory.listFiles().also { children ->
                children.forEach { child -> runCatching { child.close() } }
            }.size
        } finally {
            runCatching { directory.close() }
        }
    }

    fun probeFile(url: String): Boolean {
        val file = SmbFile(url, context())
        return try {
            if (!file.exists() || file.isDirectory) return false
            SmbFileInputStream(file).use { input ->
                val b = ByteArray(1)
                input.read(b, 0, 1) >= 0
            }
        } catch (_: Exception) {
            false
        } finally {
            runCatching { file.close() }
        }
    }

    fun context(): CIFSContext {
        val credentials = store.load()
        val fingerprint = "${credentials.address}\u0000${credentials.username}\u0000${credentials.password}"
        val existing = cachedContext
        if (existing != null && fingerprint == cachedFingerprint) return existing

        synchronized(this) {
            val second = cachedContext
            if (second != null && fingerprint == cachedFingerprint) return second
            val created = buildContext(credentials)
            cachedContext = created
            cachedFingerprint = fingerprint
            return created
        }
    }

    private fun buildContext(credentials: SmbCredentials): CIFSContext {
        val props = Properties().apply {
            setProperty("jcifs.smb.client.minVersion", "SMB202")
            setProperty("jcifs.smb.client.maxVersion", "SMB311")
            setProperty("jcifs.smb.client.responseTimeout", "12000")
            setProperty("jcifs.smb.client.soTimeout", "12000")
            setProperty("jcifs.smb.client.connTimeout", "8000")
            // Streaming is latency-sensitive, especially through Tailscale over cellular.
            // Avoid TCP's small-packet delay on SMB request/response traffic.
            setProperty("jcifs.smb.client.tcpNoDelay", "true")
        }
        val base = BaseContext(PropertyConfiguration(props))

        val rawUser = credentials.username.trim()
        val slash = rawUser.indexOf('\')
        val domain = if (slash > 0) rawUser.substring(0, slash) else ""
        val user = if (slash > 0) rawUser.substring(slash + 1) else rawUser

        return base.withCredentials(NtlmPasswordAuthenticator(domain, user, credentials.password))
    }
}
