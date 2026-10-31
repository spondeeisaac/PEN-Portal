package org.practicaleducationnetwork.penfieldlocation

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import org.json.JSONObject
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

class LocationTrackingService : Service() {
    companion object {
        const val ACTION_START = "org.practicaleducationnetwork.penfieldlocation.START"
        const val ACTION_STOP = "org.practicaleducationnetwork.penfieldlocation.STOP"
        const val EXTRA_ABORT = "abort"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_PROJECT_ID = "project_id"
        private const val CHANNEL_ID = "pen_lit_field_location"
        private const val NOTIFICATION_ID = 7666
        private const val INTERVAL_MS = 15L * 60L * 1000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val locationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var active = false
    private var captureCancellation: CancellationTokenSource? = null

    private val captureTask = object : Runnable {
        override fun run() {
            if (!active || SecureState.currentSession(this@LocationTrackingService) == null) return
            captureOneLocation()
            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action.orEmpty()
        if (action == ACTION_STOP) {
            stopCapture()
            val session = SecureState.currentSession(this)
            val requestedSession = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
            val requestedProject = intent.getStringExtra(EXTRA_PROJECT_ID).orEmpty()
            if (session != null && session.sessionId == requestedSession && session.projectId == requestedProject && session.projectId == "PRJ-7666A96146E1") {
                val abort = intent.getBooleanExtra(EXTRA_ABORT, false)
                val db = LocationDatabase(this)
                try {
                    if (abort) db.deleteSession(session.sessionId)
                    else db.markStopped(session.sessionId, Instant.now().toString(), false)
                } finally { db.close() }
                SecureState.clearSession(this)
                if (!abort) enqueueSync()
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (action == ACTION_START) {
            val incoming = sessionFromIntent(intent)
            if (incoming != null) {
                val existing = SecureState.currentSession(this)
                if (existing != null && existing.sessionId != incoming.sessionId) {
                    // Do not silently replace an active service session.
                    return START_NOT_STICKY
                }
                val db = LocationDatabase(this)
                try { db.saveSession(incoming) } finally { db.close() }
                SecureState.saveSession(this, incoming)
            }
        }

        val session = SecureState.currentSession(this)
        if (session == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (!hasLocationPermission()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        active = true
        schedulePeriodicSync()
        handler.removeCallbacks(captureTask)
        handler.post(captureTask) // first sample, then approximately every 15 minutes
        return START_STICKY
    }

    private fun sessionFromIntent(intent: Intent?): TrackingSession? {
        val uri = intent?.data ?: return null
        val token = uri.getQueryParameter("token").orEmpty()
        val api = uri.getQueryParameter("api").orEmpty()
        val project = uri.getQueryParameter("projectID").orEmpty()
        val visit = uri.getQueryParameter("visitID").orEmpty()
        val session = uri.getQueryParameter("sessionID").orEmpty()
        val parsedApi = try { android.net.Uri.parse(api) } catch (_: Exception) { null }
        val trustedApi = parsedApi != null && parsedApi.scheme == "https" && parsedApi.host == "script.google.com" && (parsedApi.path ?: "").contains("/macros/")
        if (token.isBlank() || !token.startsWith("PENOFF26.") || !trustedApi || project != "PRJ-7666A96146E1" || visit.isBlank() || session.isBlank()) return null
        return TrackingSession(token, api, project, visit, session, SecureState.deviceId(this), uri.getQueryParameter("appVersion") ?: "PEN-DLT-ANDROID-1.0.0")
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun captureOneLocation() {
        if (!hasLocationPermission() || SecureState.currentSession(this) == null) return
        captureCancellation?.cancel()
        captureCancellation = CancellationTokenSource()
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(30L * 1000L)
            .build()
        locationClient.getCurrentLocation(request, captureCancellation!!.token)
            .addOnSuccessListener { location -> if (location != null && active) storeLocation(location) }
            .addOnFailureListener { /* No fabricated location: the administrator sees a signal gap. */ }
    }

    private fun storeLocation(location: Location) {
        val session = SecureState.currentSession(this) ?: return
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val battery = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val mock = if (Build.VERSION.SDK_INT >= 31) location.isMock else location.isFromMockProvider
        val sample = JSONObject().apply {
            put("sampleID", "SMP-" + UUID.randomUUID().toString().replace("-", ""))
            put("capturedAt", Instant.ofEpochMilli(location.time).toString())
            put("latitude", location.latitude)
            put("longitude", location.longitude)
            put("accuracy", location.accuracy.toDouble())
            put("speedMps", if (location.hasSpeed()) location.speed.toDouble() else JSONObject.NULL)
            put("bearing", if (location.hasBearing()) location.bearing.toDouble() else JSONObject.NULL)
            put("provider", "FUSED")
            put("mockLocation", mock)
            put("sequence", SecureState.nextSequence(this))
            put("batteryPercent", if (battery in 0..100) battery else JSONObject.NULL)
            put("networkType", currentNetworkType())
        }
        val db = LocationDatabase(this)
        try { db.addSample(sample, session.sessionId) } finally { db.close() }
        enqueueSync()
    }

    private fun currentNetworkType(): String {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return "OFFLINE"
        val caps = cm.getNetworkCapabilities(network) ?: return "UNKNOWN"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            else -> "OTHER"
        }
    }

    private fun schedulePeriodicSync() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequestBuilder<LocationSyncWorker>(15, TimeUnit.MINUTES).setConstraints(constraints).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("pen-lit-location-periodic-sync", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun enqueueSync() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = OneTimeWorkRequestBuilder<LocationSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork("pen-lit-location-sync", ExistingWorkPolicy.KEEP, request)
    }

    private fun stopCapture() {
        active = false
        handler.removeCallbacks(captureTask)
        captureCancellation?.cancel()
        captureCancellation = null
    }

    private fun buildNotification(): Notification {
        createNotificationChannel()
        val openIntent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("PEN Field Visit Location")
            .setContentText("Location sharing is active during this LiT field visit. It stops at check-out.")
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pending)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "PEN field visit location", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Visible notice while location is collected during an active PEN field visit."
            })
        }
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }
}
