package org.practicaleducationnetwork.penfieldlocation

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

object SecureState {
    private const val PREFS = "pen_lit_location_secure_state"
    private const val KEY_SEQUENCE = "sample_sequence"
    private const val KEY_DEVICE_ID = "device_id"

    private fun prefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            context, PREFS, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun deviceId(context: Context): String {
        val p = prefs(context)
        val existing = p.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val created = "PENDEV-" + UUID.randomUUID().toString().replace("-", "")
        p.edit().putString(KEY_DEVICE_ID, created).apply()
        return created
    }

    fun saveSession(context: Context, session: TrackingSession) {
        prefs(context).edit()
            .putString("token", session.token)
            .putString("api_url", session.apiUrl)
            .putString("project_id", session.projectId)
            .putString("visit_id", session.visitId)
            .putString("session_id", session.sessionId)
            .putString("device_id", session.deviceId)
            .putString("app_version", session.appVersion)
            .apply()
    }

    fun currentSession(context: Context): TrackingSession? {
        val p = prefs(context)
        val token = p.getString("token", null) ?: return null
        val api = p.getString("api_url", null) ?: return null
        val project = p.getString("project_id", null) ?: return null
        val visit = p.getString("visit_id", null) ?: return null
        val session = p.getString("session_id", null) ?: return null
        val device = p.getString("device_id", null) ?: return null
        return TrackingSession(token, api, project, visit, session, device, p.getString("app_version", "PEN-DLT-ANDROID-1.0.0") ?: "PEN-DLT-ANDROID-1.0.0")
    }

    fun nextSequence(context: Context): Long {
        val p = prefs(context)
        val next = p.getLong(KEY_SEQUENCE, 0L) + 1L
        p.edit().putLong(KEY_SEQUENCE, next).apply()
        return next
    }

    fun clearSession(context: Context) {
        prefs(context).edit()
            .remove("token").remove("api_url").remove("project_id")
            .remove("visit_id").remove("session_id").remove("app_version")
            .apply()
    }
}
