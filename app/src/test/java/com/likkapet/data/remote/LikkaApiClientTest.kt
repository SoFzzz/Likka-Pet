package com.likkapet.data.remote

import com.likkapet.domain.model.TargetApp
import com.likkapet.domain.model.TriggerReason
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** RF-I05 (the app's side of the Worker's answers) over a real HTTP exchange with MockWebServer. */
class LikkaApiClientTest {
    private val server = MockWebServer()
    private lateinit var client: LikkaApiClient

    private val request = RoastRequest(TargetApp.OTHER, minutes = 15, angle = 55, level = 1, reason = TriggerReason.USAGE_TIME)

    @Before
    fun setUp() {
        server.start()
        client = LikkaApiClient(server.url("/").toString(), TOKEN)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun call(): RoastApiResult = runBlocking { client.requestRoast(request) }

    private fun answer(
        code: Int,
        body: String = "",
    ) = server.enqueue(MockResponse().setResponseCode(code).setBody(body))

    @Test
    fun `200 with a roast is a success`() {
        answer(200, """{"roast":"Psst… tu cuello pidió asilo."}""")

        assertEquals(RoastApiResult.Success("Psst… tu cuello pidió asilo."), call())
    }

    @Test
    fun `402 no_credit is no credit`() {
        answer(402, """{"error":"no_credit"}""")

        assertEquals(RoastApiResult.NoCredit, call())
    }

    @Test
    fun `a 402 that is not the Worker's no_credit is just a rejection`() {
        answer(402, "Payment Required")

        assertEquals(RoastApiResult.Rejected(402), call())
    }

    @Test
    fun `429 is rate limited`() {
        answer(429, "Too many requests")

        assertEquals(RoastApiResult.RateLimited, call())
    }

    @Test
    fun `401, 502 and 504 are rejections the app answers with a local roast`() {
        listOf(401, 502, 504).forEach { code ->
            answer(code, "Upstream error")

            assertEquals(RoastApiResult.Rejected(code), call())
        }
    }

    @Test
    fun `a 200 whose body is not the expected JSON is a rejection`() {
        answer(200, "<html>gateway</html>")
        assertEquals(RoastApiResult.Rejected(200), call())

        answer(200, """{"something":"else"}""")
        assertEquals(RoastApiResult.Rejected(200), call())

        answer(200, """{"roast":null}""")
        assertEquals(RoastApiResult.Rejected(200), call())

        answer(200, """{"roast":5}""")
        assertEquals(RoastApiResult.Rejected(200), call())
    }

    @Test
    fun `an unreachable Worker is a network error`() {
        server.shutdown()

        assertEquals(RoastApiResult.NetworkError, call())
    }

    @Test
    fun `a Worker that never answers ends in a network error at the timeout`() {
        val impatient =
            LikkaApiClient(
                server.url("/").toString(),
                TOKEN,
                LikkaApiClient
                    .defaultClient()
                    .newBuilder()
                    .callTimeout(300, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .build(),
            )
        server.enqueue(MockResponse().setBodyDelay(5, java.util.concurrent.TimeUnit.SECONDS).setBody("""{"roast":"tarde"}"""))

        assertEquals(RoastApiResult.NetworkError, runBlocking { impatient.requestRoast(request) })
    }

    @Test
    fun `the request is a POST to roast with the token header and exactly the six fields`() {
        answer(200, """{"roast":"hola"}""")

        call()

        val sent = server.takeRequest()
        assertEquals("POST", sent.method)
        assertEquals("/roast", sent.path)
        assertEquals(TOKEN, sent.getHeader("X-Likka-Token"))
        val body = JSONObject(sent.body.readUtf8())
        assertEquals(setOf("app", "minutes", "angle", "level", "reason", "lang"), body.keys().asSequence().toSet())
        assertEquals("OTHER", body.getString("app"))
    }

    @Test
    fun `a base URL without a trailing slash still reaches roast`() {
        client = LikkaApiClient(server.url("/").toString().trimEnd('/'), TOKEN)
        answer(200, """{"roast":"hola"}""")

        call()

        assertEquals("/roast", server.takeRequest().path)
    }

    @Test
    fun `it is not configured without a URL or a token, and then makes no request`() {
        val noUrl = LikkaApiClient("", TOKEN)
        val noToken = LikkaApiClient(server.url("/").toString(), "")

        assertFalse(noUrl.isConfigured)
        assertFalse(noToken.isConfigured)
        assertTrue(client.isConfigured)
        assertEquals(RoastApiResult.Rejected(0), runBlocking { noUrl.requestRoast(request) })
        assertEquals(0, server.requestCount)
    }

    private companion object {
        const val TOKEN = "test-token"
    }
}
