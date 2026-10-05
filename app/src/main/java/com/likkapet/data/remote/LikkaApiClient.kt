package com.likkapet.data.remote

import com.likkapet.domain.EscalationConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * [LikkaApi] over OkHttp. It never logs the URL, the token or any body: the URL and the token come
 * from `local.properties` at build time and must stay out of the logs and the repository.
 */
class LikkaApiClient(
    baseUrl: String,
    private val token: String,
    private val client: OkHttpClient = defaultClient(),
) : LikkaApi {
    private val roastUrl: HttpUrl? =
        baseUrl
            .toHttpUrlOrNull()
            ?.newBuilder()
            ?.addPathSegment(ROAST_PATH)
            ?.build()

    override val isConfigured: Boolean = roastUrl != null && token.isNotBlank()

    override suspend fun requestRoast(request: RoastRequest): RoastApiResult {
        val url = roastUrl ?: return RoastApiResult.Rejected(NOT_CONFIGURED_STATUS)
        val httpRequest =
            Request
                .Builder()
                .url(url)
                .header(TOKEN_HEADER, token)
                .post(request.toJson().toRequestBody(JSON_MEDIA_TYPE))
                .build()
        return try {
            client.newCall(httpRequest).await().use(::toResult)
        } catch (_: IOException) {
            RoastApiResult.NetworkError
        }
    }

    private fun toResult(response: Response): RoastApiResult {
        val body = response.body.string()
        return when {
            response.isSuccessful -> parseRoast(body) ?: RoastApiResult.Rejected(response.code)
            response.code == STATUS_PAYMENT_REQUIRED && isNoCredit(body) -> RoastApiResult.NoCredit
            response.code == STATUS_TOO_MANY_REQUESTS -> RoastApiResult.RateLimited
            else -> RoastApiResult.Rejected(response.code)
        }
    }

    private fun parseRoast(body: String): RoastApiResult? =
        try {
            // optString turns a JSON null into the text "null", so require a real string.
            (JSONObject(body).opt(FIELD_ROAST) as? String)?.takeIf { it.isNotBlank() }?.let { RoastApiResult.Success(it) }
        } catch (_: JSONException) {
            null // A 200 that is not the expected JSON is just another failed request.
        }

    private fun isNoCredit(body: String): Boolean =
        try {
            JSONObject(body).optString(FIELD_ERROR) == ERROR_NO_CREDIT
        } catch (_: JSONException) {
            false
        }

    private suspend fun Call.await(): Response =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancel() }
            enqueue(
                object : Callback {
                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) = continuation.resume(response)

                    override fun onFailure(
                        call: Call,
                        e: IOException,
                    ) {
                        continuation.resumeWith(Result.failure(e))
                    }
                },
            )
        }

    companion object {
        private const val ROAST_PATH = "roast"
        private const val TOKEN_HEADER = "X-Likka-Token"
        private const val FIELD_ROAST = "roast"
        private const val FIELD_ERROR = "error"
        private const val ERROR_NO_CREDIT = "no_credit"
        private const val STATUS_PAYMENT_REQUIRED = 402
        private const val STATUS_TOO_MANY_REQUESTS = 429
        private const val NOT_CONFIGURED_STATUS = 0
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        fun defaultClient(): OkHttpClient =
            OkHttpClient
                .Builder()
                .callTimeout(EscalationConfig.ROAST_REQUEST_TIMEOUT_SEC.toLong(), TimeUnit.SECONDS)
                .build()
    }
}
