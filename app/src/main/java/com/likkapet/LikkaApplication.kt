package com.likkapet

import android.app.Application
import android.os.Build
import com.likkapet.domain.port.MonitoringController
import com.likkapet.presentation.state.FakeAppState
import com.likkapet.presentation.state.FakeAppStateStore
import com.likkapet.presentation.state.pendingPermissions
import com.likkapet.service.ServiceMonitoringController

/**
 * Manual dependency injection root (documentación §9.4). It exposes `domain/port` interfaces
 * and, until `data/` exists, the in-memory [FakeAppStateStore] the screens read and write.
 */
class LikkaApplication : Application() {
    lateinit var monitoringController: MonitoringController
        private set
    lateinit var appStateStore: FakeAppStateStore
        private set

    override fun onCreate() {
        super.onCreate()
        monitoringController = ServiceMonitoringController(this)
        appStateStore =
            FakeAppStateStore(
                FakeAppState(
                    permissions = pendingPermissions(includeNotifications = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU),
                ),
            )
    }
}
