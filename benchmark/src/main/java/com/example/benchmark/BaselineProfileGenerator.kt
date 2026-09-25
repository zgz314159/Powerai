package com.example.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * This test class generates a basic startup baseline profile for the target package.
 *
 * We recommend you use [androidx.benchmark.macro.junit4.BaselineProfileRule] to generate
 * the profile, which then gets included in the final APK to improve app startup and performance.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName = "com.example.powerai",
        includeInStartupProfile = true
    ) {
        pressHome()
        startActivityAndWait()
    }
}
