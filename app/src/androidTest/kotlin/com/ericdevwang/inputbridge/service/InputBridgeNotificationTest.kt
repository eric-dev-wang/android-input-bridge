package com.ericdevwang.inputbridge.service

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.ericdevwang.inputbridge.MainActivity
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Test

class InputBridgeNotificationTest {
    @Test
    fun launchIntentTargetsMainActivityWithoutDuplicatingIt() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val intent = InputBridgeNotification.createLaunchIntent(context)

        assertEquals(
            MainActivity::class.java.name,
            intent.component?.className,
        )
        assertEquals(
            Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP,
            intent.flags and (Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
    }

    @Test
    fun notificationUsesAnImmutableContentIntent() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val pendingIntent = checkNotNull(InputBridgeNotification.build(context).contentIntent)

        assertEquals(context.packageName, pendingIntent.creatorPackage)
        assertEquals(true, pendingIntent.isImmutable)
    }

    @Test
    fun notificationContentIntentLaunchesMainActivity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activityMonitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)

        try {
            ContextCompat.startForegroundService(context, Intent(context, InputBridgeService::class.java))
            instrumentation.waitForIdleSync()
            InputBridgeNotification.build(context).contentIntent?.send()
            val launchedActivity = instrumentation.waitForMonitorWithTimeout(activityMonitor, 5_000)
            assertNotNull("Notification click did not launch MainActivity", launchedActivity)
            launchedActivity?.finish()
        } finally {
            instrumentation.removeMonitor(activityMonitor)
            context.stopService(Intent(context, InputBridgeService::class.java))
        }
    }
}
