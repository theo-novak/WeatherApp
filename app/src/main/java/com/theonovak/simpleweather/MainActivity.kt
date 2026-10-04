package com.theonovak.simpleweather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Simple Weather — v1.2, location-aware.
 *
 * One screen. Finds YOUR position (network/Wi-Fi fix — the city-level,
 * indoor-friendly kind), reverse-geocodes it to a place name, then fetches
 * current conditions + 5-day rain chance from Open-Meteo (free, no API key).
 * If you deny the location permission — or the phone can't get a fix — it
 * shows the fallback city, clearly labeled, never silently wrong.
 *
 * ─── THE SPOTS YOU EDIT ────────────────────────────────────────────────
 *  1. FALLBACK city (used only if location is denied/unavailable)
 *  2. weatherCodeToText / weatherCodeToEmoji — wording & icons
 *  3. Colors   — res/values/colors.xml
 *  4. App name — res/values/strings.xml
 * Then rebuild + reinstall (see EDITING.md in the project root).
 * ───────────────────────────────────────────────────────────────────────
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WeatherScreen() }
    }
}

/* ── EDIT SPOT #1: fallback location — ONLY used if the location
 *    permission is denied or no fix is available ─────────────────────── */
private const val FALLBACK_NAME = "London (fallback)"
private const val FALLBACK_LAT = 51.5074
private const val FALLBACK_LON = -0.1278

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ACCESS_FINE_LOCATION,
)

private fun hasLocationPermission(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/* The place we're showing weather for. */
private data class Place(val name: String, val lat: Double, val lon: Double)

/* Coordinates → city name via Android's built-in Geocoder.
 * Falls back to a rounded "lat, lon" label if the geocoder has nothing. */
private suspend fun nameFor(context: Context, lat: Double, lon: Double): String =
    withContext(Dispatchers.IO) {
        try {
            val matches = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)
                ?: emptyList()
            matches.firstOrNull()?.locality
                ?: matches.firstOrNull()?.subLocality
                ?: "%.2f, %.2f".format(lat, lon)
        } catch (e: IOException) {
            Log.e(TAG, "geocoder unavailable: ${e.message}")
            "%.2f, %.2f".format(lat, lon)
        }
    }

/* Freshest already-cached fix across all enabled providers. No radio wake-up. */
@SuppressLint("MissingPermission")  // callers must check permission first
private fun bestCachedFix(manager: LocationManager): Location? =
    manager.getProviders(true)
        .mapNotNull { p ->
            try { manager.getLastKnownLocation(p) } catch (e: Exception) { null }
        }
        .maxByOrNull { it.time }

/* One fresh fix: suspends until the provider reports a location, or times out.
 * Prefers a NON-GPS provider (network = Wi-Fi/cell, works indoors, city-level
 * accuracy — exactly what weather needs). GPS only if nothing else exists. */
@SuppressLint("MissingPermission")
private suspend fun oneFreshFix(context: Context, timeoutMs: Long = 10_000): Location? {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val provider = manager.getProviders(true)
        .firstOrNull { it != LocationManager.GPS_PROVIDER } ?: return null
    return withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { cont ->
            var listener: LocationListener? = null
            listener = LocationListener { location ->
                manager.removeUpdates(listener!!)
                if (cont.isActive) cont.resume(location)
            }
            manager.requestLocationUpdates(provider, 0L, 0f, listener!!, context.mainLooper)
            cont.invokeOnCancellation { manager.removeUpdates(listener!!) }
        }
    }
}

/* Resolve the current place: cached fix → fresh fix → null (→ fallback). */
private suspend fun resolveCurrentPlace(context: Context): Place? {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val fix = bestCachedFix(manager) ?: oneFreshFix(context)
    return fix?.let {
        Place(nameFor(context, it.latitude, it.longitude), it.latitude, it.longitude)
    }
}

/* Open-Meteo — free, no key, no signup. Paste the built URL into a browser
 * to see the raw JSON yourself. Docs: https://open-meteo.com/en/docs      */
private fun buildApiUrl(lat: Double, lon: Double): String =
    "https://api.open-meteo.com/v1/forecast" +
        "?latitude=$lat&longitude=$lon" +
        "&current=temperature_2m,apparent_temperature,wind_speed_10m,weather_code" +
        "&daily=precipitation_probability_max" +
        "&forecast_days=5" +
        "&timezone=auto"

/* ── EDIT SPOT #2: wording. WMO codes → your words. ───────────────────── */
private fun weatherCodeToText(code: Int): String = when (code) {
    0 -> "Clear sky"
    1, 2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Fog"
    51, 53, 55 -> "Drizzle"
    61, 63, 65 -> "Rain"
    66, 67 -> "Freezing rain"
    71, 73, 75 -> "Snow"
    77 -> "Snow grains"
    80, 81, 82 -> "Rain showers"
    85, 86 -> "Snow showers"
    95 -> "Thunderstorm"
    96, 99 -> "Thunderstorm with hail"
    else -> "Unknown"
}

private fun weatherCodeToEmoji(code: Int): String = when (code) {
    0 -> "☀️"
    1, 2 -> "⛅"
    3 -> "☁️"
    45, 48 -> "🌫️"
    51, 53, 55 -> "🌦️"
    61, 63, 65 -> "🌧️"
    66, 67 -> "🌧️"
    71, 73, 75, 77 -> "❄️"
    80, 81, 82 -> "🌧️"
    85, 86 -> "🌨️"
    95, 96, 99 -> "⛈️"
    else -> "🌡️"
}

/* The shape of what we display. */
private data class Weather(
    val temperature: Double,
    val feelsLike: Double,
    val windSpeed: Double,
    val code: Int,
    val dailyDates: List<String>,    // ISO dates, today first
    val dailyRainChance: List<Int?>, // % chance of rain, same order (null = no data)
)

/* Fetch + parse, off the UI thread.
 * Uses HttpURLConnection with explicit timeouts — a plain URL.readText()
 * has NO timeout by default (waits forever), which is how apps get stuck. */
private suspend fun fetchWeather(lat: Double, lon: Double): Weather = withContext(Dispatchers.IO) {
    val url = URL(buildApiUrl(lat, lon))
    Log.d(TAG, "fetching: ${url}")
    val conn = url.openConnection() as HttpURLConnection
    try {
        conn.connectTimeout = 10_000   // 10s to establish the connection
        conn.readTimeout = 10_000      // 10s max for the response after connecting
        val code = conn.responseCode
        if (code != 200) {
            Log.e(TAG, "HTTP $code from Open-Meteo")
            throw IllegalStateException("HTTP $code from weather service")
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        Log.d(TAG, "received ${body.length} chars")
        val root = JSONObject(body)
        val current = root.getJSONObject("current")
        val daily = root.getJSONObject("daily")
        Weather(
            temperature = current.getDouble("temperature_2m"),
            feelsLike = current.getDouble("apparent_temperature"),
            windSpeed = current.getDouble("wind_speed_10m"),
            code = current.getInt("weather_code"),
            dailyDates = daily.getJSONArray("time").let { a ->
                List(a.length()) { a.getString(it) }
            },
            dailyRainChance = daily.getJSONArray("precipitation_probability_max").let { a ->
                List(a.length()) { if (a.isNull(it)) null else a.getInt(it) }
            },
        )
    } finally {
        conn.disconnect()
    }
}

private const val TAG = "SimpleWeather"

@Composable
fun WeatherScreen() {
    val context = LocalContext.current
    var place by remember { mutableStateOf<Place?>(null) }
    var usingFallback by remember { mutableStateOf(false) }
    var weather by remember { mutableStateOf<Weather?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    // NOTE: starts false, NOT true — load() has an if(loading) return guard,
    // and the initial LaunchedEffect call below must get through that guard.
    // (Starting true here = spinner forever with no fetch ever running.)
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    /* The whole data path: resolve place (or fallback) → fetch its weather. */
    fun load() {
        if (loading) return
        loading = true
        error = null
        scope.launch {
            try {
                val current = if (hasLocationPermission(context)) {
                    resolveCurrentPlace(context)
                } else null
                val target = current ?: Place(FALLBACK_NAME, FALLBACK_LAT, FALLBACK_LON)
                usingFallback = current == null
                if (current == null) Log.d(TAG, "no fix — using fallback city")
                place = target
                weather = fetchWeather(target.lat, target.lon)
            } catch (e: Exception) {
                error = e.message ?: "network error"
            } finally {
                loading = false
            }
        }
    }

    /* First launch: no permission yet → ask; the callback runs load() either
     * way (granted → locate; denied → labeled fallback). */
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { load() }

    LaunchedEffect(Unit) {
        if (hasLocationPermission(context)) load()
        else permissionLauncher.launch(LOCATION_PERMISSIONS)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when {
            loading -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Loading…", color = Color.White)
            }
            error != null -> {
                Text("Couldn't load weather", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(error ?: "", fontSize = 14.sp, color = Color.White)
                Spacer(Modifier.height(24.dp))
                Button(onClick = { load() }) { Text("Retry", color = Color.White) }
            }
            weather != null -> {
                val w = weather!!
                Text(place?.name ?: "", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                if (usingFallback) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Location off — showing fallback city",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(weatherCodeToEmoji(w.code), fontSize = 72.sp)
                Spacer(Modifier.height(16.dp))
                Text(
                    "%.1f°".format(w.temperature),
                    fontSize = 80.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(8.dp))
                Text(weatherCodeToText(w.code), fontSize = 20.sp, color = Color.White)
                Spacer(Modifier.height(24.dp))
                Text("Feels like %.1f°".format(w.feelsLike), fontSize = 16.sp, color = Color.White)
                Text("Wind %.1f km/h".format(w.windSpeed), fontSize = 16.sp, color = Color.White)
                Spacer(Modifier.height(40.dp))
                /* ── 5-day rain chance ───────────────────────────────── */
                Text("Chance of rain", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(16.dp))
                w.dailyDates.zip(w.dailyRainChance).forEachIndexed { i, (date, rain) ->
                    val label = if (i == 0) "Today"
                        else LocalDate.parse(date).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(label, fontSize = 16.sp, color = Color.White)
                        Text(
                            if (rain == null) "—" else "$rain%",
                            fontSize = 16.sp,
                            color = Color.White,
                            fontWeight = if (rain != null && rain >= 50) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(40.dp))
                /* Refresh re-resolves place too — weather for where you are NOW. */
                Button(onClick = { load() }) { Text("Refresh", color = Color.White) }
            }
        }
    }
}