package com.housemusic.player.house

import android.content.Context
import com.housemusic.player.model.RemoteEntry
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

class HouseApiException(val status: Int, val code: String, message: String) : Exception(message)

/** One attempt per command. Never replay a write after an ambiguous HTTP failure. */
class HouseApi(private val context: Context, @Volatile var endpoint: HouseEndpoint) {
    fun get(path: String): JSONObject = request(path, null)
    fun post(path: String, body: JSONObject = JSONObject()): JSONObject = request(path, body)

    private fun request(path: String, body: JSONObject?): JSONObject {
        val target = endpoint
        check(HouseConnection.isPresent(context, target)) { "Home network unavailable" }
        val connection = URL("http://${target.httpHost}:8787$path").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 2000
            connection.readTimeout = 2500
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                val bytes = body.toString().toByteArray(Charsets.UTF_8)
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = JSONObject(stream?.bufferedReader()?.use { it.readText() } ?: "{}")
            if (status !in 200..299) throw HouseApiException(status, response.optString("error"), response.optString("detail", "House request failed ($status)"))
            check(response.optString("service") == "house-audio-server") { "Unexpected house service" }
            return response
        } finally { connection.disconnect() }
    }

    fun browse(path: String): List<RemoteEntry> {
        val entries = get("/browse?path=" + URLEncoder.encode(path, "UTF-8")).getJSONArray("entries")
        return (0 until entries.length()).mapNotNull { index ->
            val item = entries.getJSONObject(index)
            if (item.optString("type") !in setOf("file", "directory")) null else RemoteEntry(
                item.getString("name"), item.getString("path"), item.getString("type") == "directory",
                modifiedTime(item.optString("lastModified")), 0L)
        }
    }

    companion object {
        fun modifiedTime(value: String): Long = runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L)
        fun strings(values: List<String>) = JSONArray(values)
    }
}
