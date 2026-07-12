package com.ericdevwang.androidinputbridge

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ericdevwang.androidinputbridge.http.HttpTextRepository
import com.ericdevwang.androidinputbridge.repository.DefaultTextRepository
import com.ericdevwang.androidinputbridge.repository.TextRepository
import com.ericdevwang.androidinputbridge.server.InputHttpServer
import org.junit.Assert.assertSame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class KoinWiringTest {
    @Test
    fun applicationStartsKoinWithSingletonTextRepository() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val koin = GlobalContext.get()

        val firstRepository = koin.get<TextRepository>()
        val secondRepository = koin.get<TextRepository>()

        assertTrue(application is AndroidInputBridgeApplication)
        assertTrue(firstRepository is DefaultTextRepository)
        assertSame(firstRepository, secondRepository)

        assertTrue(koin.get<HttpTextRepository>() === koin.get<HttpTextRepository>())
        assertTrue(koin.get<InputHttpServer>() === koin.get<InputHttpServer>())

        val expectedVersion = application.packageManager
            .getPackageInfo(application.packageName, 0)
            .versionName
        assertEquals(expectedVersion, koin.get<HttpTextRepository>().health().appVersion)
    }
}
