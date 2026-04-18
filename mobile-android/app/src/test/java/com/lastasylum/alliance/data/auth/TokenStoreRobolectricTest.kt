package com.lastasylum.alliance.data.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TokenStoreRobolectricTest {
    @Test
    fun createsWithoutCrashing() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        TokenStore(ctx)
    }
}
