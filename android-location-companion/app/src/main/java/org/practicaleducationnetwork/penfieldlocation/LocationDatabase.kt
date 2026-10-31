package org.practicaleducationnetwork.penfieldlocation

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

class LocationDatabase(private val context: Context) : SQLiteOpenHelper(context, "pen_lit_location_queue.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE pending_samples (sample_id TEXT PRIMARY KEY, session_id TEXT NOT NULL, captured_at TEXT NOT NULL, payload_enc TEXT NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE tracking_sessions (session_id TEXT PRIMARY KEY, visit_id TEXT NOT NULL, project_id TEXT NOT NULL, device_id TEXT NOT NULL, token_enc TEXT NOT NULL, api_url TEXT NOT NULL, app_version TEXT NOT NULL, pending_stop INTEGER NOT NULL DEFAULT 0, abort_session INTEGER NOT NULL DEFAULT 0, ended_at TEXT NOT NULL DEFAULT '', server_session_created INTEGER NOT NULL DEFAULT 0)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS tracking_sessions (session_id TEXT PRIMARY KEY, visit_id TEXT NOT NULL, project_id TEXT NOT NULL, device_id TEXT NOT NULL, token_enc TEXT NOT NULL, api_url TEXT NOT NULL, app_version TEXT NOT NULL, pending_stop INTEGER NOT NULL DEFAULT 0, abort_session INTEGER NOT NULL DEFAULT 0, ended_at TEXT NOT NULL DEFAULT '', server_session_created INTEGER NOT NULL DEFAULT 0)")
        }
    }

    fun saveSession(session: TrackingSession) {
        val values = ContentValues().apply {
            put("session_id", session.sessionId)
            put("visit_id", session.visitId)
            put("project_id", session.projectId)
            put("device_id", session.deviceId)
            put("token_enc", LocalCrypto.encrypt(session.token))
            put("api_url", session.apiUrl)
            put("app_version", session.appVersion)
            put("pending_stop", 0)
            put("abort_session", 0)
            put("ended_at", "")
            put("server_session_created", 0)
        }
        writableDatabase.insertWithOnConflict("tracking_sessions", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun getSessions(): List<QueuedTrackingSession> {
        val out = mutableListOf<QueuedTrackingSession>()
        readableDatabase.query("tracking_sessions", null, null, null, null, null, "rowid ASC").use { cursor ->
            while (cursor.moveToNext()) {
                try {
                    val tracking = TrackingSession(
                        token = LocalCrypto.decrypt(cursor.getString(cursor.getColumnIndexOrThrow("token_enc"))),
                        apiUrl = cursor.getString(cursor.getColumnIndexOrThrow("api_url")),
                        projectId = cursor.getString(cursor.getColumnIndexOrThrow("project_id")),
                        visitId = cursor.getString(cursor.getColumnIndexOrThrow("visit_id")),
                        sessionId = cursor.getString(cursor.getColumnIndexOrThrow("session_id")),
                        deviceId = cursor.getString(cursor.getColumnIndexOrThrow("device_id")),
                        appVersion = cursor.getString(cursor.getColumnIndexOrThrow("app_version"))
                    )
                    out.add(QueuedTrackingSession(
                        tracking,
                        cursor.getInt(cursor.getColumnIndexOrThrow("pending_stop")) == 1,
                        cursor.getInt(cursor.getColumnIndexOrThrow("abort_session")) == 1,
                        cursor.getString(cursor.getColumnIndexOrThrow("ended_at")) ?: "",
                        cursor.getInt(cursor.getColumnIndexOrThrow("server_session_created")) == 1
                    ))
                } catch (_: Exception) { }
            }
        }
        return out
    }

    fun markStopped(sessionId: String, endedAt: String, abort: Boolean) {
        val values = ContentValues().apply {
            put("pending_stop", 1)
            put("abort_session", if (abort) 1 else 0)
            put("ended_at", endedAt)
        }
        writableDatabase.update("tracking_sessions", values, "session_id=?", arrayOf(sessionId))
    }

    fun markServerCreated(sessionId: String) {
        val values = ContentValues().apply { put("server_session_created", 1) }
        writableDatabase.update("tracking_sessions", values, "session_id=?", arrayOf(sessionId))
    }

    fun addSample(sample: JSONObject, sessionId: String) {
        val values = ContentValues().apply {
            put("sample_id", sample.optString("sampleID"))
            put("session_id", sessionId)
            put("captured_at", sample.optString("capturedAt"))
            put("payload_enc", LocalCrypto.encrypt(sample.toString()))
            put("created_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("pending_samples", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun pending(sessionId: String, limit: Int = 50): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        readableDatabase.query(
            "pending_samples", arrayOf("payload_enc"), "session_id=?", arrayOf(sessionId),
            null, null, "captured_at ASC", limit.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                try { out.add(JSONObject(LocalCrypto.decrypt(cursor.getString(0)))) } catch (_: Exception) { }
            }
        }
        return out
    }

    fun deleteSamples(sampleIds: List<String>) {
        if (sampleIds.isEmpty()) return
        writableDatabase.beginTransaction()
        try {
            sampleIds.forEach { writableDatabase.delete("pending_samples", "sample_id=?", arrayOf(it)) }
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
    }

    fun deleteSession(sessionId: String) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("pending_samples", "session_id=?", arrayOf(sessionId))
            writableDatabase.delete("tracking_sessions", "session_id=?", arrayOf(sessionId))
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
    }

    fun hasPendingSamples(sessionId: String): Boolean {
        readableDatabase.query("pending_samples", arrayOf("sample_id"), "session_id=?", arrayOf(sessionId), null, null, null, "1").use { return it.moveToFirst() }
    }

    fun pruneVeryOld() {
        val cutoff = System.currentTimeMillis() - 120L * 24L * 60L * 60L * 1000L
        writableDatabase.delete("pending_samples", "created_at<?", arrayOf(cutoff.toString()))
    }
}
