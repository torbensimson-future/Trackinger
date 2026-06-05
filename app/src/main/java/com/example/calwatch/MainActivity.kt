package com.example.calwatch

import android.Manifest
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val WORK_NAME = "calwatch-check"
    private lateinit var prefs: Prefs
    private val io = Executors.newSingleThreadExecutor()
    private val timeFmt = SimpleDateFormat("EEE dd.MM. · HH:mm", Locale.GERMAN)

    private var events: List<Event> = emptyList()
    private var user: Pair<Double, Double>? = null
    private var currentNext: Event? = null

    private lateinit var cardBanner: MaterialCardView
    private lateinit var tvBannerTitle: TextView
    private lateinit var tvBannerSub: TextView
    private lateinit var tvMonitor: TextView
    private lateinit var tvMyLoc: TextView
    private lateinit var tvEvent: TextView
    private lateinit var tvEventTime: TextView
    private lateinit var tvEventLoc: TextView
    private lateinit var tvDist: TextView
    private lateinit var tvStatus: TextView
    private lateinit var etUrl: EditText
    private lateinit var etRadius: EditText
    private lateinit var etWindow: EditText

    private var lm: LocationManager? = null
    private val locListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            user = location.latitude to location.longitude
            updateUi()
        }
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private val basePerms = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    private val requestBase = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val fineGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requestBackground.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            setStatus(if (fineGranted) "Standort erlaubt." else "Ohne Standortrecht kann nicht gewarnt werden.")
        }
        startLocationUpdates()
        updateUi()
    }

    private val requestBackground = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        setStatus(
            if (granted) "Hintergrund-Standort erlaubt – jetzt Überwachung starten."
            else "Tipp: Standort auf „Immer zulassen“ setzen (App-Einstellungen), sonst nur bei offener App."
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        Notifier.ensureChannel(this)
        lm = getSystemService(LOCATION_SERVICE) as LocationManager

        cardBanner = findViewById(R.id.cardBanner)
        tvBannerTitle = findViewById(R.id.tvBannerTitle)
        tvBannerSub = findViewById(R.id.tvBannerSub)
        tvMonitor = findViewById(R.id.tvMonitor)
        tvMyLoc = findViewById(R.id.tvMyLoc)
        tvEvent = findViewById(R.id.tvEvent)
        tvEventTime = findViewById(R.id.tvEventTime)
        tvEventLoc = findViewById(R.id.tvEventLoc)
        tvDist = findViewById(R.id.tvDist)
        tvStatus = findViewById(R.id.tvStatus)
        etUrl = findViewById(R.id.etUrl)
        etRadius = findViewById(R.id.etRadius)
        etWindow = findViewById(R.id.etWindow)

        etUrl.setText(prefs.icsUrl)
        etRadius.setText(prefs.radius.toString())
        etWindow.setText(prefs.windowMin.toString())

        findViewById<Button>(R.id.btnPerms).setOnClickListener { requestBase.launch(basePerms) }
        findViewById<Button>(R.id.btnStart).setOnClickListener { startMonitoring() }
        findViewById<Button>(R.id.btnStop).setOnClickListener { stopMonitoring() }
        findViewById<Button>(R.id.btnCheck).setOnClickListener { checkNow() }
        findViewById<Button>(R.id.btnRefresh).setOnClickListener { savePrefs(); loadData(true) }
        findViewById<Button>(R.id.btnAppSettings).setOnClickListener { openAppSettings() }
        findViewById<Button>(R.id.btnMapMe).setOnClickListener { openMyLocationMap() }
        findViewById<Button>(R.id.btnMapEvent).setOnClickListener { openEventMap() }

        updateUi()
        loadData(false)
    }

    override fun onResume() {
        super.onResume()
        if (user == null) user = Logic.lastKnownLocation(this)
        startLocationUpdates()
        updateUi()
    }

    override fun onPause() {
        super.onPause()
        try {
            lm?.removeUpdates(locListener)
        } catch (e: SecurityException) {
        }
    }

    private fun startLocationUpdates() {
        if (!Logic.hasLocationPermission(this)) return
        try {
            for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (lm?.isProviderEnabled(p) == true) {
                    lm?.requestLocationUpdates(p, 5000L, 5f, locListener)
                }
            }
            if (user == null) user = Logic.lastKnownLocation(this)
        } catch (e: SecurityException) {
        } catch (e: Exception) {
        }
    }

    private fun savePrefs() {
        prefs.icsUrl = etUrl.text.toString().trim()
        prefs.radius = etRadius.text.toString().toIntOrNull() ?: 500
        prefs.windowMin = etWindow.text.toString().toIntOrNull() ?: 90
    }

    private fun loadData(force: Boolean) {
        savePrefs()
        setStatus(if (force) "Kalender wird geladen …" else "Lade gespeicherte Termine …")
        io.execute {
            val list = Logic.ensureEvents(prefs, force)
            runOnUiThread {
                events = list
                setStatus(if (list.isEmpty()) "Keine Termine. URL prüfen und „Kalender aktualisieren“ tippen." else "${list.size} Termine geladen.")
                updateUi()
            }
        }
    }

    private fun checkNow() {
        savePrefs()
        if (prefs.icsUrl.isBlank()) { toast("Bitte zuerst die Kalender-URL eintragen."); return }
        setStatus("Prüfe …")
        io.execute {
            val list = Logic.ensureEvents(prefs, true)
            val loc = user ?: Logic.lastKnownLocation(this)
            runOnUiThread {
                events = list
                user = loc
                val now = System.currentTimeMillis()
                val next = Logic.nextEventWithCoords(list, now, null)
                when {
                    list.isEmpty() -> setStatus("Keine Termine geladen – stimmt die Kalender-URL?")
                    next == null -> setStatus("Kein kommender Termin mit Ort gefunden. Hat dein Termin eine Adresse?")
                    loc == null -> setStatus("Standort noch nicht verfügbar – kurz warten, am besten im Freien.")
                    else -> {
                        val d = Logic.distanceMeters(loc.first, loc.second, next.lat!!, next.lon!!)
                        if (d > prefs.radius) {
                            Notifier.show(this, next, d, prefs.radius)
                            prefs.lastNotifiedUid = next.uid
                            prefs.lastNotifiedAt = now
                            setStatus("Warnung gesendet: ${Notifier.distanceText(d)} entfernt.")
                        } else {
                            setStatus("Alles gut: nur ${Notifier.distanceText(d)} entfernt (im Radius).")
                        }
                    }
                }
                updateUi()
            }
        }
    }

    private fun updateUi() {
        tvMonitor.text = if (prefs.monitoring) "● Überwachung: aktiv" else "● Überwachung: gestoppt"
        tvMonitor.setTextColor(ContextCompat.getColor(this, if (prefs.monitoring) R.color.ok else R.color.muted))

        tvMyLoc.text = user?.let { "%.5f, %.5f".format(it.first, it.second) } ?: "noch nicht verfügbar"

        val now = System.currentTimeMillis()
        val next = Logic.nextEventWithCoords(events, now, null)
        currentNext = next

        if (next == null) {
            tvEvent.text = "—"
            tvEventTime.text = ""
            tvEventLoc.text = ""
            tvDist.text = ""
            setBanner(R.color.neutral, "Kein Termin mit Ort", "Lade einen Kalender mit Adressen und tippe „Jetzt prüfen“.")
            return
        }

        tvEvent.text = next.summary
        tvEventTime.text = next.startMillis?.let { timeFmt.format(Date(it)) } ?: ""
        tvEventLoc.text = if (next.locText.isNotBlank()) "📍 ${next.locText}"
            else "📍 %.5f, %.5f".format(next.lat, next.lon)

        val u = user
        if (u == null) {
            tvDist.text = ""
            setBanner(R.color.neutral, "Standort fehlt", "Tippe „1 · Berechtigungen erteilen“ und prüfe erneut.")
        } else {
            val d = Logic.distanceMeters(u.first, u.second, next.lat!!, next.lon!!)
            tvDist.text = "Entfernung: ${Notifier.distanceText(d)}"
            if (d > prefs.radius) {
                setBanner(R.color.warnBg, "⚠ Zu weit weg",
                    "Du bist ${Notifier.distanceText(d)} vom Termin entfernt (Radius ${prefs.radius} m).")
            } else {
                setBanner(R.color.okBg, "✓ In der Nähe",
                    "Du bist ${Notifier.distanceText(d)} vom Termin entfernt.")
            }
        }
    }

    private fun setBanner(bgColor: Int, title: String, sub: String) {
        cardBanner.setCardBackgroundColor(ContextCompat.getColor(this, bgColor))
        tvBannerTitle.text = title
        tvBannerSub.text = sub
    }

    private fun startMonitoring() {
        savePrefs()
        if (prefs.icsUrl.isBlank()) { toast("Bitte zuerst die Kalender-URL eintragen."); return }
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequestBuilder<CheckWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints).build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        prefs.monitoring = true
        setStatus("Überwachung aktiv – Prüfung etwa alle 15 Minuten.")
        toast("Überwachung gestartet")
        updateUi()
    }

    private fun stopMonitoring() {
        WorkManager.getInstance(this).cancelUniqueWork(WORK_NAME)
        prefs.monitoring = false
        setStatus("Überwachung gestoppt.")
        toast("Überwachung gestoppt")
        updateUi()
    }

    private fun openMyLocationMap() {
        val u = user ?: run { toast("Standort noch nicht verfügbar."); return }
        openMap(u.first, u.second, "Mein Standort")
    }

    private fun openEventMap() {
        val n = currentNext ?: run { toast("Kein Termin mit Ort."); return }
        openMap(n.lat!!, n.lon!!, n.summary)
    }

    private fun openMap(lat: Double, lon: Double, label: String) {
        val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(label)})")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            toast("Keine Karten-App gefunden.")
        }
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        })
    }

    private fun setStatus(text: String) { tvStatus.text = text }
    private fun toast(text: String) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show() }
}
