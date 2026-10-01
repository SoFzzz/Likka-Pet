package com.likkapet

import android.app.Application
import com.likkapet.data.apps.PackageManagerAppIconLoader
import com.likkapet.data.apps.PackageManagerInstalledAppsSource
import com.likkapet.data.permissions.AndroidPermissionChecker
import com.likkapet.data.preferences.DataStoreStatsStore
import com.likkapet.data.preferences.LikkaDataStore
import com.likkapet.data.time.SystemWallClock
import com.likkapet.domain.port.InstalledAppsSource
import com.likkapet.domain.port.MonitoringController
import com.likkapet.domain.port.StatsStore
import com.likkapet.domain.port.WallClock
import com.likkapet.presentation.components.AppIconLoader
import com.likkapet.presentation.permissions.PermissionMonitor
import com.likkapet.service.ServiceMonitoringController
import java.time.ZoneId

/**
 * Manual dependency injection root (documentación §9.4). Callers only see `domain/port`
 * interfaces; the `data/` implementations are created here and nowhere else.
 */
class LikkaApplication : Application() {
    lateinit var monitoringController: MonitoringController
        private set
    lateinit var statsStore: StatsStore
        private set
    lateinit var installedAppsSource: InstalledAppsSource
        private set
    lateinit var permissionMonitor: PermissionMonitor
        private set
    lateinit var appIconLoader: AppIconLoader
        private set
    val wallClock: WallClock = SystemWallClock

    override fun onCreate() {
        super.onCreate()
        monitoringController = ServiceMonitoringController(this)
        statsStore = DataStoreStatsStore(LikkaDataStore.create(this), wallClock, ZoneId::systemDefault)
        installedAppsSource = PackageManagerInstalledAppsSource(this)
        permissionMonitor = PermissionMonitor(AndroidPermissionChecker(this))
        appIconLoader = PackageManagerAppIconLoader(this)::load
    }
}
