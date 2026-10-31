package org.practicaleducationnetwork.penfieldlocation

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {
    private var pendingUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingUri = intent?.data
        handleIncomingIntent()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingUri = intent.data
        handleIncomingIntent()
    }

    private fun handleIncomingIntent() {
        val uri = pendingUri ?: run { finish(); return }
        val action = uri.path.orEmpty().trim('/').lowercase()
        if (action == "stop") {
            // Only the same signed portal session that started collection can
            // stop it; arbitrary apps cannot terminate an active tracking run.
            val current = SecureState.currentSession(this)
            val requestedToken = uri.getQueryParameter("token").orEmpty()
            val requestedSession = uri.getQueryParameter("sessionID").orEmpty()
            val requestedProject = uri.getQueryParameter("projectID").orEmpty()
            if (current == null || current.token != requestedToken || current.sessionId != requestedSession || current.projectId != requestedProject) {
                finish(); return
            }
            // A stop command is not a location foreground-service start. This
            // remains reliable even if location permission was revoked.
            val serviceIntent = Intent(this, LocationTrackingService::class.java).apply {
                this.action = LocationTrackingService.ACTION_STOP
                putExtra(LocationTrackingService.EXTRA_ABORT, uri.getQueryParameter("abort") == "1")
                putExtra(LocationTrackingService.EXTRA_SESSION_ID, requestedSession)
                putExtra(LocationTrackingService.EXTRA_PROJECT_ID, requestedProject)
            }
            startService(serviceIntent)
            finish()
            return
        }
        if (action != "start" || uri.getQueryParameter("projectID") != "PRJ-7666A96146E1") { finish(); return }
        if (!trustedApiUrl(uri.getQueryParameter("api").orEmpty()) || !uri.getQueryParameter("token").orEmpty().startsWith("PENOFF26.")) { finish(); return }
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            setContentView(TextView(this).apply {
                text = "PEN Field Visit needs location permission on this PEN tablet. Allow location access to enable automatic reporting during authorised LiT field visits. No additional field information is required."
                textSize = 16f
                setPadding(28, 40, 28, 40)
            })
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 7666)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7667)
            return
        }
        startLocationService(uri)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 7666) {
            val granted = grantResults.any { it == PackageManager.PERMISSION_GRANTED }
            if (granted) handleIncomingIntent() else {
                setContentView(TextView(this).apply {
                    text = "Location permission was not granted. PEN cannot collect location during this field visit. Please contact the PEN administrator responsible for the tablet."
                    textSize = 16f
                    setPadding(28, 40, 28, 40)
                })
            }
        } else if (requestCode == 7667) {
            if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
                pendingUri?.let { startLocationService(it) }
            } else {
                setContentView(TextView(this).apply {
                    text = "PEN requires the location-sharing notification to remain visible while an authorised field visit is active. Please ask the PEN tablet administrator to enable notifications for PEN Field Visit. No location will be collected until this notice can be displayed."
                    textSize = 16f
                    setPadding(28, 40, 28, 40)
                })
            }
        }
    }

    private fun trustedApiUrl(value: String): Boolean {
        return try {
            val parsed = Uri.parse(value)
            parsed.scheme == "https" && parsed.host == "script.google.com" && (parsed.path ?: "").contains("/macros/")
        } catch (_: Exception) { false }
    }

    private fun startLocationService(uri: Uri) {
        val token = uri.getQueryParameter("token").orEmpty()
        val api = uri.getQueryParameter("api").orEmpty()
        val project = uri.getQueryParameter("projectID").orEmpty()
        val visit = uri.getQueryParameter("visitID").orEmpty()
        val session = uri.getQueryParameter("sessionID").orEmpty()
        if (token.isBlank() || !token.startsWith("PENOFF26.") || !trustedApiUrl(api) || project != "PRJ-7666A96146E1" || visit.isBlank() || session.isBlank()) { finish(); return }
        val serviceIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
            data = uri
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        finish()
    }
}
