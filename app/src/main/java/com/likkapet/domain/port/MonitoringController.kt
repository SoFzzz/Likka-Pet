package com.likkapet.domain.port

/**
 * Starts and stops Likka's background monitoring. Lets presentation/ trigger `LikkaService`
 * without depending on service/ (documentación §10.1).
 */
interface MonitoringController {
    fun start()

    fun stop()
}
