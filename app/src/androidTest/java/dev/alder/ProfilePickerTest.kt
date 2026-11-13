package dev.alder

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProfilePickerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as Application
    private val account = app.getSharedPreferences("account", Context.MODE_PRIVATE)
    private val activities = mutableListOf<Activity>()
    private var originalProfiles: String? = null
    private var originalActive: String? = null
    private var fixtureInstalled = false
    private val callbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, state: Bundle?) { activities += activity }
        override fun onActivityDestroyed(activity: Activity) { activities -= activity }
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
    }

    @Before fun setUp() {
        check(!account.contains("authKey")) { "Use a fresh test install without legacy account data" }
        originalProfiles = account.getString("profiles", null)
        originalActive = account.getString("active", null)
        val profiles = """[{"id":"1","name":"Locked","color":0,"authKey":"test-locked","pin":"1234"}]"""
        check(originalProfiles == null || originalProfiles == "[]") { "Use a fresh test install without saved profiles" }
        fixtureInstalled = true
        assertTrue(account.edit().putString("profiles", profiles).putString("active", "1").commit())
        app.registerActivityLifecycleCallbacks(callbacks)
    }

    @After fun tearDown() {
        if (!fixtureInstalled) return
        onMain { activities.toList().forEach { it.finish() } }
        instrumentation.waitForIdleSync()
        app.unregisterActivityLifecycleCallbacks(callbacks)
        account.edit().putString("profiles", originalProfiles).putString("active", originalActive).commit()
    }

    @Test fun externalPickerBackDoesNotOpenActiveLockedProfile() {
        Profiles.add(app, "Open", "test-open")
        Profiles.setActive(app, "1")
        val picker = launch(Intent(app, ProfilesActivity::class.java).putExtra("pick", true))
        onMain {
            assertTrue(picker.hasText(app.getString(R.string.profiles_whos_here)))
            picker.onBackPressed()
        }
        instrumentation.waitForIdleSync()
        onMain {
            assertTrue(picker.isFinishing)
            assertFalse(activities.any { it is MainActivity || it is DetailsActivity })
        }
    }

    @Test fun externalLaunchRequiresCorrectPinAndPinBackReturnsToPicker() {
        val picker = launch(Intent(app, ProfilesActivity::class.java).putExtra("signIn", "1"))
        assertPinPrompt(picker)
        enterPin(picker, "0000")
        onMain {
            assertTrue(picker.hasText(app.getString(R.string.profiles_pin_wrong)))
            assertFalse(activities.any { it is MainActivity })
            picker.onBackPressed()
            assertTrue(picker.hasText(app.getString(R.string.profiles_whos_here)))
            assertTrue(picker.currentFocus?.performClick() == true)
        }
        assertPinPrompt(picker)
        enterPin(picker, "1234")
        waitForActivity(MainActivity::class.java)
    }

    @Test fun privatePickerKeepsInternalBackAndCannotBeOpenedByAnotherApp() {
        val component = ComponentName(app, "dev.alder.ProfilePickerActivity")
        assertFalse(app.packageManager.getActivityInfo(component, 0).exported)
        val picker = launch(Intent().setComponent(component))
        onMain {
            assertTrue(picker.hasText(app.getString(R.string.profiles_whos_here)))
            picker.onBackPressed()
        }
        waitForActivity(MainActivity::class.java)
    }

    @Test fun watchNextTitleOpensOnlyAfterPin() {
        val picker = launch(Intent(app, ProfilesActivity::class.java)
            .putExtra("type", "movie").putExtra("id", "tt0000001")
            .putExtra("name", "Watch Next test").putExtra("videoId", "tt0000001"))
        assertPinPrompt(picker)
        enterPin(picker, "0000")
        onMain { assertFalse(activities.any { it is DetailsActivity }) }
        enterPin(picker, "1234")
        val details = waitForActivity(DetailsActivity::class.java)
        assertEquals("tt0000001", details.intent.getStringExtra("id"))
        assertEquals("Watch Next test", details.intent.getStringExtra("name"))
    }

    @Test fun lockedPickerAndPinPromptDoNotReadHistoryArt() {
        Profiles.add(app, "Open", "test-open")
        Profiles.setActive(app, "1")
        launch(Intent(app, ProfilesActivity::class.java))
        val url = "https://api.strem.io/api/datastoreGet"
        val items = """{"result":[{"_id":"tt0000001","state":{"timeOffset":1000}}]}"""
        for (key in listOf("test-open", "test-locked")) {
            Http.keep(url, """{"authKey":"$key","collection":"libraryItem","ids":[],"all":true}""", items)
        }
        val cachedPicker = launch(Intent(app, ProfilesActivity::class.java))
        onMain { sendKey(cachedPicker, KeyEvent.KEYCODE_DPAD_RIGHT) }
        eventually { cachedPicker.backdrop().tag != null }
        onMain {
            sendKey(cachedPicker, KeyEvent.KEYCODE_DPAD_LEFT)
            assertNull(cachedPicker.backdrop().tag)
            assertTrue(cachedPicker.currentFocus?.performClick() == true)
        }
        assertPinPrompt(cachedPicker)
        // The cached title is usable for unlocked art. It must stay out of both protected views.
        repeat(10) {
            onMain { assertNull(cachedPicker.backdrop().tag) }
            Thread.sleep(50)
        }
    }

    private fun launch(intent: Intent): Activity = instrumentation.startActivitySync(
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    )

    private fun assertPinPrompt(activity: Activity) = onMain {
        assertTrue(activity.hasText(app.getString(R.string.profiles_enter_pin, "Locked")))
    }

    private fun enterPin(activity: Activity, pin: String) = onMain {
        pin.forEach { sendKey(activity, KeyEvent.KEYCODE_0 + it.digitToInt()) }
    }

    private fun sendKey(activity: Activity, code: Int) {
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun waitForActivity(type: Class<out Activity>): Activity {
        var found: Activity? = null
        eventually { activities.firstOrNull(type::isInstance).also { found = it } != null }
        return checkNotNull(found)
    }

    private fun eventually(condition: () -> Boolean) {
        val deadline = android.os.SystemClock.uptimeMillis() + 5_000
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            var met = false
            onMain { met = condition() }
            if (met) return
            Thread.sleep(50)
        }
        throw AssertionError("Expected UI state did not appear")
    }

    private fun Activity.backdrop(): ImageView =
        ((findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup).getChildAt(0) as ImageView)

    private fun Activity.hasText(text: String): Boolean =
        window.decorView.walk().filterIsInstance<TextView>().any { it.text.toString() == text }

    private fun View.walk(): Sequence<View> = sequence {
        yield(this@walk)
        if (this@walk is ViewGroup) for (index in 0 until childCount) yieldAll(getChildAt(index).walk())
    }

    private fun onMain(action: () -> Unit) {
        var result: Result<Unit>? = null
        instrumentation.runOnMainSync { result = runCatching(action) }
        checkNotNull(result).getOrThrow()
    }
}
