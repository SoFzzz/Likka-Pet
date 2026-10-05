package com.likkapet.data.remote

/** What the Worker answered, reduced to what the app acts on (documentación §7.2, RF-I05). */
sealed interface RoastApiResult {
    data class Success(
        val roast: String,
    ) : RoastApiResult

    /** `402 {"error":"no_credit"}`: DeepSeek has no balance left. */
    data object NoCredit : RoastApiResult

    /** `429`: too many requests from this IP. */
    data object RateLimited : RoastApiResult

    /** The phone could not reach the Worker (no network, DNS, timeout). */
    data object NetworkError : RoastApiResult

    /** Any other answer (400, 401, 404, 5xx, a body that is not the expected JSON): use a local roast and try later. */
    data class Rejected(
        val status: Int,
    ) : RoastApiResult
}

interface LikkaApi {
    /** False when no Worker URL or token was configured at build time: then no request is ever made. */
    val isConfigured: Boolean

    suspend fun requestRoast(request: RoastRequest): RoastApiResult
}
