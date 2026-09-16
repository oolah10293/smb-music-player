package com.smbmusic.player.smb

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import jcifs.smb.SmbFile
import jcifs.smb.SmbFileInputStream
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

class SmbDataSource(private val client: SmbClient) : BaseDataSource(true) {
    private var currentSpec: DataSpec? = null
    private var currentUri: Uri? = null
    private var file: SmbFile? = null
    private var stream: InputStream? = null
    private var remaining: Long = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        currentSpec = dataSpec
        currentUri = dataSpec.uri

        try {
            val smbFile = SmbFile(dataSpec.uri.toString(), client.context())
            val fileLength = smbFile.length()
            if (dataSpec.position > fileLength) {
                smbFile.close()
                throw EOFException("Seek ${dataSpec.position} is beyond file length $fileLength")
            }

            val rawInput = SmbFileInputStream(smbFile)
            // Keep the raw stream reachable by closeQuietly() if seeking/wrapping fails.
            stream = rawInput

            // jcifs-ng's skip() only advances its 64-bit file pointer; it does not read/discard
            // bytes over the network, so arbitrary Media3 seeks remain cheap.
            val skipped = rawInput.skip(dataSpec.position)
            if (skipped != dataSpec.position) {
                rawInput.close()
                smbFile.close()
                throw EOFException("Could only seek to $skipped of ${dataSpec.position}")
            }

            file = smbFile

            // SmbFileInputStream is intentionally unbuffered. Media extractors commonly ask a
            // DataSource for relatively small chunks; forwarding every one of those requests
            // straight to SMB turns WAN/cellular RTT into a throughput limiter. Buffer one full
            // 64 KiB SMB-sized chunk locally so Media3 can sip from RAM while jcifs performs
            // larger network reads. The much larger ExoPlayer buffer still sits above this layer.
            stream = BufferedInputStream(rawInput, SMB_READ_AHEAD_BYTES)

            remaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
                fileLength - dataSpec.position
            } else {
                minOf(dataSpec.length, fileLength - dataSpec.position)
            }
            opened = true
            transferStarted(dataSpec)
            return remaining
        } catch (e: IOException) {
            closeQuietly()
            throw e
        } catch (e: Exception) {
            closeQuietly()
            throw IOException("SMB open failed: ${e.message}", e)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT

        val toRead = minOf(length.toLong(), remaining).toInt()
        val input = stream ?: throw IOException("SMB stream is not open")

        val read = try {
            input.read(buffer, offset, toRead)
        } catch (e: Exception) {
            throw if (e is IOException) e else IOException("SMB read failed: ${e.message}", e)
        }

        if (read == -1) return C.RESULT_END_OF_INPUT
        remaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = currentUri

    override fun close() {
        closeQuietly()
    }

    private fun closeQuietly() {
        runCatching { stream?.close() }
        runCatching { file?.close() }
        stream = null
        file = null
        currentUri = null
        currentSpec = null
        remaining = 0L
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    class Factory(private val client: SmbClient) : DataSource.Factory {
        override fun createDataSource(): DataSource = SmbDataSource(client)
    }

    companion object {
        // One full SMB-sized read ahead. This is deliberately separate from ExoPlayer's
        // 120-600 second playback buffer in PlaybackService.
        private const val SMB_READ_AHEAD_BYTES = 64 * 1024
    }
}
