package io.github.mateuxlucax.healthconnectbridge

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * Real Health Connect -> app sync -> exporter -> Prometheus, on an emulator. The workflow runs the
 * exporter and Prometheus on the host (10.0.2.2) and passes the exporter's token as `-e token`.
 * Also saves the README screenshots to [SCREENSHOT_DIR].
 */
class EndToEndTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val source = context.packageName
    private val settings = Settings(context)
    private val client = HealthConnectClient.getOrCreate(context)
    private val token = requireNotNull(InstrumentationRegistry.getArguments().getString("token")) {
        "pass the exporter's token with -e token <token>"
    }

    // Whole hours in the past, so the expected timestamps are exact.
    private val base = Instant.now().truncatedTo(ChronoUnit.HOURS).minus(Duration.ofDays(2))
    private val sleepStart = base.minus(Duration.ofHours(8))

    @Test
    fun syncsHealthConnectToPrometheus(): Unit = runBlocking {
        shell("mkdir -p $SCREENSHOT_DIR")
        // The activity schedules the periodic sync on start; keep it from running alongside the test.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "periodic-sync",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setInitialDelay(1, TimeUnit.DAYS).build(),
        ).result.get()
        settings.prefs.edit().clear().commit()

        val permissions = HealthSync.requestedPermissions(client) + WRITE_TYPES.map { HealthPermission.getWritePermission(it) }
        permissions.forEach { instrumentation.uiAutomation.grantRuntimePermission(source, it) }
        assertTrue(client.permissionController.getGrantedPermissions().containsAll(permissions))

        client.insertRecords(records())
        settings.serverUrl = "http://10.0.2.2:8080"

        // Wrong token: the app shows the 401 and nothing reaches Prometheus.
        settings.token = "wrong-$token"
        sync()
        assertEquals("Token rejeitado pelo servidor (401).", settings.lastError)
        assertEquals(0L, settings.lastSuccessMs)
        assertNull(settings.changesToken)
        assertEquals(0, query("""{__name__=~"health_.+"}[30d]""").length())
        screenshotMainScreen("main-error")

        // Right token: the first run reads the last 7 days.
        settings.token = token
        sync()
        assertNull(settings.lastError)
        assertTrue(settings.lastSuccessMs > 0)
        assertNotNull(settings.changesToken)
        assertExpectedSeries()

        // A late record 10 days in the past: outside the initial 7-day read, so only the Changes API can send it.
        val weightTime = Instant.now().truncatedTo(ChronoUnit.HOURS).minus(Duration.ofDays(10))
        client.insertRecords(
            listOf(WeightRecord(time = weightTime, zoneOffset = null, weight = Mass.kilograms(72.5), metadata = meta()))
        )
        sync()
        assertNull(settings.lastError)
        assertEquals(mapOf(labels() to listOf(weightTime to 72.5)), series("health_weight_kg"))
        assertExpectedSeries("health_weight_kg")

        screenshotMainScreen("main-light")
        shell("cmd uimode night yes")
        Thread.sleep(2_000)
        screenshotMainScreen("main-dark")
        shell("cmd uimode night no")
    }

    @Test
    fun launcherIconScreenshots() {
        shell("mkdir -p $SCREENSHOT_DIR")
        val device = UiDevice.getInstance(instrumentation)
        val plain = captureLauncherIcon(device)
        save("launcher-icon", plain)

        // Launcher3's own setting for Material You themed icons (what Wallpaper & style toggles).
        val uri = "content://${device.launcherPackageName}.grid_control/icon_themed"
        val out = shell("content update --uri $uri --bind boolean_value:b:true") + shell("content query --uri $uri")
        Thread.sleep(3_000)
        val themed = captureLauncherIcon(device)
        if (themed.sameAs(plain)) org.junit.Assert.fail("DEBUG themed: ${device.launcherPackageName} $out")
        else save("launcher-icon-themed", themed)
    }

    private suspend fun sync() = SyncWorker.runAndReport(settings) { HealthSync(context, settings).incremental() }

    private fun meta() = Metadata.manualEntry()

    private fun records() = listOf(
        HeartRateRecord(
            startTime = base, startZoneOffset = null,
            endTime = base.plus(Duration.ofMinutes(3)), endZoneOffset = null,
            samples = listOf(
                HeartRateRecord.Sample(base, 61),
                HeartRateRecord.Sample(base.plus(Duration.ofMinutes(1)), 72),
                HeartRateRecord.Sample(base.plus(Duration.ofMinutes(2)), 88),
            ),
            metadata = meta(),
        ),
        StepsRecord(
            startTime = base.plus(Duration.ofHours(1)), startZoneOffset = null,
            endTime = base.plus(Duration.ofMinutes(75)), endZoneOffset = null,
            count = 1234, metadata = meta(),
        ),
        ActiveCaloriesBurnedRecord(
            startTime = base.plus(Duration.ofHours(1)), startZoneOffset = null,
            endTime = base.plus(Duration.ofMinutes(75)), endZoneOffset = null,
            energy = Energy.kilocalories(56.5), metadata = meta(),
        ),
        SleepSessionRecord(
            startTime = sleepStart, startZoneOffset = null,
            endTime = sleepStart.plus(Duration.ofHours(7)), endZoneOffset = null,
            metadata = meta(),
            stages = listOf(
                stage(0, 60, SleepSessionRecord.STAGE_TYPE_LIGHT),
                stage(60, 150, SleepSessionRecord.STAGE_TYPE_DEEP),
                stage(150, 180, SleepSessionRecord.STAGE_TYPE_REM),
                stage(180, 190, SleepSessionRecord.STAGE_TYPE_AWAKE),
                stage(190, 420, SleepSessionRecord.STAGE_TYPE_LIGHT),
            ),
        ),
        ExerciseSessionRecord(
            startTime = base.plus(Duration.ofHours(2)), startZoneOffset = null,
            endTime = base.plus(Duration.ofMinutes(165)), endZoneOffset = null,
            metadata = meta(),
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        ),
    )

    private fun stage(startMinute: Long, endMinute: Long, type: Int) = SleepSessionRecord.Stage(
        sleepStart.plus(Duration.ofMinutes(startMinute)), sleepStart.plus(Duration.ofMinutes(endMinute)), type,
    )

    private fun sleepAt(minute: Long): Instant = sleepStart.plus(Duration.ofMinutes(minute))

    /** Every health_* series in Prometheus must be exactly the README mapping of [records] (plus [extra] names). */
    private fun assertExpectedSeries(vararg extra: String) {
        val expected = mapOf(
            "health_heart_rate_bpm" to mapOf(
                labels() to listOf(
                    base to 61.0, base.plus(Duration.ofMinutes(1)) to 72.0, base.plus(Duration.ofMinutes(2)) to 88.0,
                ),
            ),
            "health_steps" to mapOf(labels() to listOf(base.plus(Duration.ofHours(1)) to 1234.0)),
            "health_active_calories_kcal" to mapOf(labels() to listOf(base.plus(Duration.ofHours(1)) to 56.5)),
            "health_sleep_stage_seconds" to mapOf(
                labels("stage" to "light") to listOf(sleepAt(0) to 3600.0, sleepAt(190) to 13800.0),
                labels("stage" to "deep") to listOf(sleepAt(60) to 5400.0),
                labels("stage" to "rem") to listOf(sleepAt(150) to 1800.0),
                labels("stage" to "awake") to listOf(sleepAt(180) to 600.0),
            ),
            "health_sleep_session_seconds" to mapOf(labels() to listOf(sleepAt(420) to 25200.0)),
            "health_exercise_session_seconds" to mapOf(
                labels("exercise_type" to "running") to listOf(base.plus(Duration.ofHours(2)) to 2700.0),
            ),
        )
        for ((metric, series) in expected) assertEquals(metric, series, series(metric))

        val names = query("""{__name__=~"health_.+"}[30d]""")
        val actualNames = (0 until names.length()).map { names.getJSONObject(it).getJSONObject("metric").getString("__name__") }
        assertEquals(expected.keys + extra, actualNames.toSet())
    }

    private fun labels(vararg extra: Pair<String, String>) = mapOf("source" to source) + extra

    /** Raw samples of [metric] from the last 30 days, by label set (without `__name__`). */
    private fun series(metric: String): Map<Map<String, String>, List<Pair<Instant, Double>>> {
        val result = query("$metric[30d]")
        return (0 until result.length()).associate { i ->
            val item = result.getJSONObject(i)
            val metricLabels = item.getJSONObject("metric")
            val labels = metricLabels.keys().asSequence().filter { it != "__name__" }
                .associateWith { metricLabels.getString(it) }
            val values = item.getJSONArray("values")
            labels to (0 until values.length()).map { j ->
                val point = values.getJSONArray(j)
                Instant.ofEpochMilli(Math.round(point.getDouble(0) * 1000)) to point.getString(1).toDouble()
            }
        }
    }

    private fun query(promql: String): JSONArray {
        val url = URL("http://10.0.2.2:9090/api/v1/query?query=" + URLEncoder.encode(promql, "UTF-8"))
        val body = (url.openConnection() as HttpURLConnection).inputStream.bufferedReader().use { it.readText() }
        return JSONObject(body).getJSONObject("data").getJSONArray("result")
    }

    private fun screenshotMainScreen(name: String) {
        ActivityScenario.launch(MainActivity::class.java).use {
            Thread.sleep(2_000) // status is filled in asynchronously
            save(name, instrumentation.uiAutomation.takeScreenshot())
        }
    }

    /** Opens the app drawer and returns a crop of this app's icon and label. */
    private fun captureLauncherIcon(device: UiDevice): Bitmap {
        device.pressHome()
        device.waitForIdle()
        device.swipe(device.displayWidth / 2, device.displayHeight * 4 / 5, device.displayWidth / 2, device.displayHeight / 5, 20)
        val icon: UiObject2 = requireNotNull(device.wait(Until.findObject(By.text("Health Connect Bridge")), 10_000)) {
            "app icon not found in the launcher's app drawer"
        }
        Thread.sleep(1_000)
        val screen = instrumentation.uiAutomation.takeScreenshot()
        val pad = 24
        val r = Rect(icon.visibleBounds).apply {
            inset(-pad, -pad)
            intersect(0, 0, screen.width, screen.height)
        }
        device.pressHome()
        return Bitmap.createBitmap(screen, r.left, r.top, r.width(), r.height())
    }

    /** Writes a PNG to [SCREENSHOT_DIR], which survives the app being uninstalled after the run. */
    private fun save(name: String, bitmap: Bitmap) {
        val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val (stdout, stdin) = instrumentation.uiAutomation.executeShellCommandRw("dd of=$SCREENSHOT_DIR/$name.png")
        ParcelFileDescriptor.AutoCloseOutputStream(stdin).use { it.write(png) }
        ParcelFileDescriptor.AutoCloseInputStream(stdout).use { it.readBytes() }
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .use { String(it.readBytes()) }

    private companion object {
        const val SCREENSHOT_DIR = "/data/local/tmp/screenshots"
        val WRITE_TYPES = listOf(
            HeartRateRecord::class, StepsRecord::class, ActiveCaloriesBurnedRecord::class,
            SleepSessionRecord::class, ExerciseSessionRecord::class, WeightRecord::class,
        )
    }
}
