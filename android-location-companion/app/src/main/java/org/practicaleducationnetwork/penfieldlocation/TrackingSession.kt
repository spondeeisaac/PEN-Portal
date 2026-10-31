package org.practicaleducationnetwork.penfieldlocation

data class TrackingSession(
    val token: String,
    val apiUrl: String,
    val projectId: String,
    val visitId: String,
    val sessionId: String,
    val deviceId: String,
    val appVersion: String = "PEN-DLT-ANDROID-1.0.0"
)

data class QueuedTrackingSession(
    val tracking: TrackingSession,
    val pendingStop: Boolean,
    val abort: Boolean,
    val endedAt: String,
    val serverSessionCreated: Boolean
)
