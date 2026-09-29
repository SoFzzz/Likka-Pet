package com.likkapet.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.likkapet.LikkaApplication
import com.likkapet.domain.model.ThemeMode
import com.likkapet.presentation.servicecontrol.ServiceControlContent
import com.likkapet.presentation.theme.LikkaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val monitoringController = (application as LikkaApplication).monitoringController
        // Fixed to DARK until theme_mode is read from DataStore (RF-S08).
        setContent {
            LikkaTheme(ThemeMode.DARK) {
                ServiceControlContent(
                    onStartClick = monitoringController::start,
                    onStopClick = monitoringController::stop,
                )
            }
        }
    }
}
