package org.practicaleducationnetwork.penfieldlocation

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class LocationSyncWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    private val client = OkHttpClient.Builder()
        .followRedirects(true).followSslRedirects(true)
        .connectTimeout(25, TimeUnit.SECONDS).readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS).callTimeout(50, TimeUnit.SECONDS).build()

    override fun doWork(): Result {
        val db = LocationDatabase(applicationContext)
        db.pruneVeryOld()
        return try {
            for (queued in db.getSessions()) {
                val s = queued.tracking
                if (queued.abort) {
                    db.deleteSession(s.sessionId)
                    continue
                }
                var loops = 0
                while (loops < 20) {
                    loops++
                    val samples = db.pending(s.sessionId, 50)
                    if (samples.isEmpty()) break
                    val body = JSONObject().apply {
                        put("action", "LOCATION_BATCH")
                        put("token", s.token)
                        put("projectID", s.projectId)
                        put("visitID", s.visitId)
                        put("sessionID", s.sessionId)
                        put("deviceID", s.deviceId)
                        put("deviceModel", android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL)
                        put("androidVersion", android.os.Build.VERSION.RELEASE ?: "")
                        put("appVersion", s.appVersion)
                        put("samples", JSONArray(samples))
                    }
                    if (!post(s.apiUrl, body)) return Result.retry()
                    db.deleteSamples(samples.map { it.optString("sampleID") })
                    db.markServerCreated(s.sessionId)
                }
                if (db.hasPendingSamples(s.sessionId)) return Result.retry()

                // Re-read state because a checkout can arrive while this worker
                // is uploading queued samples. Send a stop even when no sample was
                // ever captured so the administrator can see a zero-GPS session.
                val latest = db.getSessions().firstOrNull { it.tracking.sessionId == s.sessionId }
                if (latest?.pendingStop == true) {
                    val stop = JSONObject().apply {
                        put("action", "LOCATION_STOP")
                        put("token", s.token)
                        put("projectID", s.projectId)
                        put("visitID", s.visitId)
                        put("sessionID", s.sessionId)
                        put("deviceID", s.deviceId)
                        put("endedAt", latest.endedAt)
                        put("deviceModel", android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL)
                        put("androidVersion", android.os.Build.VERSION.RELEASE ?: "")
                        put("appVersion", s.appVersion)
                    }
                    if (!post(s.apiUrl, stop)) return Result.retry()
                    db.deleteSession(s.sessionId)
                }
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        } finally {
            db.close()
        }
    }

    private fun post(url: String, json: JSONObject): Boolean {
        val request = Request.Builder().url(url)
            .post(json.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) return false
            val root = try { JSONObject(text) } catch (_: Exception) { return false }
            return root.optBoolean("success", false)
        }
    }
}
