package com.likkapet

import android.app.Application
import com.likkapet.domain.port.MonitoringController
import com.likkapet.service.ServiceMonitoringController

/** Manual dependency injection root (documentación §9.4): exposes `domain/port` interfaces only. */
class LikkaApplication : Application() {
    lateinit var monitoringController: MonitoringController
        private set

    override fun onCreate() {
        super.onCreate()
        monitoringController = ServiceMonitoringController(this)
    }
}
