package com.muradgalayev.brainbuddy

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

// instrumented test, runs on an Android device
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // context of the app under test
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.muradgalayev.brainbuddy", appContext.packageName)
    }
}