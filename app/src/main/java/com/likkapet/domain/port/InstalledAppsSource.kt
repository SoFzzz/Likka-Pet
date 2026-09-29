package com.likkapet.domain.port

import com.likkapet.domain.model.InstalledApp

/** Apps with a launcher icon (the `MAIN` + `LAUNCHER` `<queries>` of the manifest, RF-S06). */
interface InstalledAppsSource {
    /** Every launcher app except Likka-Pet itself, in no particular order. */
    suspend fun launcherApps(): List<InstalledApp>
}
