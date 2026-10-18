# LOCATION_ENGINE.md

The second hard part. Every competitor gets this wrong and every field team notices.

## The abstraction

```kotlin
interface LocationEngine {
    val fixes: Flow<RawFix>
    suspend fun start(config: TrackingConfig)
    suspend fun stop()
    suspend fun lastKnown(): RawFix?
    val capabilities: EngineCapabilities
}
```

Three implementations:

| Impl | When |
|---|---|
| `FusedLocationEngine` | Play Services present and current |
| `PlatformLocationEngine` | `LocationManager` + `GPS_PROVIDER`, no Play Services |
| `ReplayLocationEngine` | Test and demo. Plays a GPX file at 1×/10×/instant. |

Select at DI time via `GoogleApiAvailability.isGooglePlayServicesAvailable`. Fall back
silently but record which engine is active in the diagnostics screen — a supervisor
debugging bad data needs to know.

`ReplayLocationEngine` is not a nice-to-have. It makes every tracking feature testable
without walking outside, and it is how you demo the app in a video call.

---

## Permissions

Android's location permission model has four steps and they must happen in order:

1. **`ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION`** — request together. On Android 12+
   the user can grant coarse only; detect that and explain why fine is required, because
   a 1 km fix is useless for a survey track.
2. **`POST_NOTIFICATIONS`** (Android 13+) — required or the foreground service
   notification is invisible, which makes the app look like it stopped.
3. **`ACCESS_BACKGROUND_LOCATION`** — must be a *separate* request, after foreground is
   granted, and it opens system settings rather than a dialog on Android 11+. Show a
   rationale screen explaining "Allow all the time" before sending them there.
4. **Battery optimisation exemption** — `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

Manifest:
```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<!-- Declared in :feature:tracking's manifest, so the class is
     et.alpha.meret.feature.tracking.service.TrackingService. -->
<service
    android:name=".service.TrackingService"
    android:foregroundServiceType="location"
    android:exported="false" />
```

If the app goes on Play, background location requires a declaration form and a demo
video. Budget time for it, or ship by APK/private channel for NGO deployments, which is
the more likely distribution path anyway.

---

## The foreground service

`TrackingService` rules:

- Started only from a user-visible action. Starting an FGS from the background throws
  `ForegroundServiceStartNotAllowedException` on Android 12+.
- `startForeground()` within 5 seconds of `onStartCommand`, always, including on the
  restart path.
- `START_STICKY`, plus persisted state so a restarted service resumes the active track
  rather than starting a new one.
- Holds a `PARTIAL_WAKE_LOCK` while actively tracking. Release it on pause and on stop.
  A foreground service does **not** guarantee the CPU stays awake; without the wakelock
  you get sparse, clumped fixes on some devices.
- The notification shows live state — duration, distance, point count, GPS quality — and
  has Pause and Stop actions. Field workers check it constantly; make it genuinely useful.
- `onTaskRemoved`: keep running. Do not stop on swipe-away.
- Boot receiver: if a track was active at shutdown, notify the user and offer to resume.
  Do not auto-start the service on boot — restrictions and user trust both argue against it.

---

## Request configuration

```kotlin
LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
    .setMinUpdateIntervalMillis(intervalMs / 2)
    .setMinUpdateDistanceMeters(minDistanceM)
    .setMaxUpdateDelayMillis(batchWindowMs)   // batching saves real power
    .setWaitForAccurateLocation(false)        // do not stall the first fix
    .build()
```

Adaptive profiles:

| Profile | Interval | Min distance | When |
|---|---|---|---|
| Precise | 1 s | 0 m | Area measurement, walking survey |
| Standard | 3 s | 5 m | Default field work |
| Economy | 10 s | 20 m | Battery < 20%, or long transit |
| Transit | 15 s | 50 m | Speed > 8 m/s sustained |

Switch automatically on speed and battery, but always let the user pin a profile — and
show which one is active. Silent behaviour changes destroy trust in survey data.

Also register a `GnssStatus.Callback` to surface satellites-used and a
signal-quality indicator. Users need to know whether a weak fix is the app's fault or the
sky's.

---

## The filter chain

Applied in order to each raw fix. **Raw values are always persisted first.**

**1. Validity gate — reject outright**
- `accuracy_m > maxAccuracy` (default 25 m, configurable 10–100)
- `elapsedRealtimeNanos` not greater than the previous fix
- Implied speed from the previous accepted fix > 60 m/s (216 km/h)
- Exact duplicate coordinates with a different timestamp and zero speed → treated as
  stationary, not as movement
- `isMock` when mock locations are disallowed for the project

Rejected fixes are stored with `is_outlier = 1`. They are evidence, not noise.

**2. Kalman smoothing** — a scalar position random walk, run independently on latitude and longitude. No velocity state, deliberately: an enumerator walks, stops, turns back, and a velocity model overshoots each of those into the measured area. See the KDoc on `KalmanLocationFilter`; `dt` comes from `elapsedRealtimeNanos`, not milliseconds.

State: position + variance. Process noise `Q` in metres²/second, default 3.0 for walking.

```
dt = (t - t_last) / 1000
variance += Q * dt
K = variance / (variance + accuracy²)
lat += K * (measured_lat - lat)
lon += K * (measured_lon - lon)
variance *= (1 - K)
```

Reset the filter on segment boundaries and after a gap > 60 s. Write output to
`filtered_lat/lon`. Unit-test against a fixture trace with known ground truth — a
straight walk down a known road, plus a synthetic trace with injected noise.

**3. Distance accumulation** — Haversine on *filtered* coordinates, but only accumulate
when the step exceeds `max(4 m, accuracy * 0.5)`. Without that gate, a device sitting on
a table accumulates several kilometres a day. This single rule is the difference between
credible and useless distance figures.

**4. Auto-pause** — if displacement over the last 60 s is < 15 m and speed < 0.5 m/s,
mark the track paused and stop accumulating moving time. Resume on 20 m of displacement.
Both thresholds configurable; auto-pause itself must be switchable off, because a
stationary observation point is legitimate survey activity.

**5. Simplification for display** — Douglas–Peucker with a perpendicular distance
threshold in metres (default 3 m), producing `simplified_geojson`. Run on track
completion and on demand, never on the fix path. Typical reduction is 80–95%.

---

## Battery

Targets, measured on a 4000 mAh device:

| Scenario | Target |
|---|---|
| Standard profile, screen off, 8 h | < 25% battery |
| Precise profile, screen off, 1 h | < 8% |
| Map screen visible, 1 h | < 15% |

Levers, in order of effect:
1. Batched updates via `setMaxUpdateDelayMillis` — the single biggest win
2. Batched Room writes (20 points or 10 s), never one transaction per fix
3. Sync deferred to unmetered network and charging where possible
4. Drop to Economy below 20% battery, with a notification saying so
5. Do not touch the map's `GeoJsonSource` while the map is not visible

Verify with Battery Historian on a real overnight run before claiming any number to a
client.

---

## OEM survival

Transsion devices — **Tecno, Infinix, Itel** — dominate the Ethiopian market and their
HiOS/XOS skins kill background services aggressively. Xiaomi's MIUI is equally hostile.
An app that works on a Pixel and dies on a Tecno has not solved the problem.

Build a `DeviceCompat` helper:

```kotlin
sealed interface OemGuidance {
    data class DeepLink(
        val targets: List<SettingsTarget>,   // tried in order
        val instructions: Int,
        val fallback: ManualSteps,
    ) : OemGuidance
    data class ManualSteps(val steps: List<Int>) : OemGuidance
    data object NotRequired : OemGuidance
}

fun oemGuidanceFor(manufacturer: String, brand: String = ""): OemGuidance
```

*(Changed from `DeepLink(intent, instructions)`: one family has several historical names
for the same screen, so a deep link is a list of package/class targets tried in order, with
its own manual fallback. Plain strings also keep the table testable on the JVM.
`DeviceCompat.open()` builds the `Intent`s. Implemented in
`core/location/.../compat/`.)*

Cover Transsion (Tecno/Infinix/Itel), Xiaomi, Oppo, Vivo, Huawei, Samsung, OnePlus, and a
generic fallback. Always wrap the deep-link `startActivity` in a try/catch — these
activities are undocumented, vary by firmware version, and will `ActivityNotFoundException`
on some builds. Fall back to `ManualSteps` with localised screenshots-in-text.

Onboarding must include, per device:
1. Disable battery optimisation for this app
2. Enable autostart / allow background activity
3. Lock the app in the recents screen (HiOS and MIUI both honour this)

**Then verify it worked.** A `TrackingHealthMonitor` records expected-vs-actual fix
counts per 10-minute window. If a gap exceeds 3× the configured interval while a track is
active, log a `tracking_gap` event and show a warning on the diagnostics screen: "Your
device stopped GPS for 14 minutes. Check battery settings." This turns an invisible OEM
bug into an actionable message, and it is the kind of detail that wins a contract.

---

## Diagnostics screen

Ship it. It is three hours of work and it will save you days of remote debugging on
devices you cannot touch.

Shows: active engine, permission states, battery-optimisation status, satellites used,
current accuracy, fix rate over the last hour, tracking-gap log, outbox depth, oldest
pending item, last sync attempt and error, tile pack status and free storage.

Add a "copy diagnostics" button that puts a plain-text report on the clipboard for
WhatsApp or Telegram. Field supervisors will use this constantly.
