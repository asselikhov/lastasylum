package com.lastasylum.alliance.ui.util

import com.lastasylum.alliance.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Response
import retrofit2.HttpException
import okhttp3.ResponseBody.Companion.toResponseBody

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NetworkMessagesTest {
    private val res = RuntimeEnvironment.getApplication().resources

    @Test
    fun http500_internalServerError_mapsToRussianGeneric() {
        val body = """{"statusCode":500,"message":"Internal server error"}"""
            .toResponseBody()
        val response = Response.error<Any>(500, body)
        val ex = HttpException(response)
        val message = ex.toUserMessageRu(res)
        assertEquals(res.getString(R.string.err_server_generic), message)
    }
}
