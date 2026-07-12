package com.ericdevwang.androidinputbridge

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ericdevwang.androidinputbridge.repository.DefaultTextRepository
import com.ericdevwang.androidinputbridge.repository.TextRepository
import org.junit.Assert.assertSame
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
    }
}
