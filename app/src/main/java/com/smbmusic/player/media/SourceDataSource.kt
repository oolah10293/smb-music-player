package com.smbmusic.player.media

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import com.smbmusic.player.smb.SmbClient
import com.smbmusic.player.smb.SmbDataSource

@UnstableApi
class SourceDataSource(private val smb: DataSource.Factory, private val http: DataSource.Factory) : DataSource {
    private var delegate: DataSource? = null
    private val listeners = mutableListOf<TransferListener>()
    override fun addTransferListener(transferListener: TransferListener) { listeners += transferListener }
    override fun open(dataSpec: DataSpec): Long {
        val factory = when (dataSpec.uri.scheme?.lowercase()) {
            "smb" -> smb
            "http", "https" -> http
            else -> throw java.io.IOException("Unsupported playback source")
        }
        return factory.createDataSource().also { source ->
            delegate = source; listeners.forEach { source.addTransferListener(it) }
        }.open(dataSpec)
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = checkNotNull(delegate).read(buffer, offset, length)
    override fun getUri(): Uri? = delegate?.uri
    override fun getResponseHeaders(): Map<String, List<String>> = delegate?.responseHeaders ?: emptyMap()
    override fun close() { try { delegate?.close() } finally { delegate = null } }
    class Factory(client: SmbClient) : DataSource.Factory {
        private val smb = SmbDataSource.Factory(client)
        private val http = DefaultHttpDataSource.Factory().setUserAgent("SMBMusic/0.6.0")
            .setConnectTimeoutMs(12_000).setReadTimeoutMs(15_000).setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(mapOf("Icy-MetaData" to "1"))
        override fun createDataSource(): DataSource = SourceDataSource(smb, http)
    }
}
