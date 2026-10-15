package com.silence.blocker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.silence.blocker.ui.SilenceAppNav
import com.silence.blocker.ui.theme.SilenceTheme
import com.silence.blocker.vpn.VpnController

class MainActivity : ComponentActivity() {
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    private val vpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                VpnController.start(this)
            } else {
                VpnController.setState(
                    com.silence.blocker.vpn.ProtectionState.OFF,
                    "VPN permission denied",
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        maybeRequestNotificationPermission()

        setContent {
            SilenceTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SilenceAppNav(
                        onRequestVpnPermission = {
                            val prepare = VpnController.prepareIntent(this)
                            if (prepare != null) {
                                VpnController.setState(
                                    com.silence.blocker.vpn.ProtectionState.REQUESTING_PERMISSION,
                                )
                                vpnPermission.launch(prepare)
                            } else {
                                VpnController.start(this)
                            }
                        },
                        onStopVpn = { VpnController.stop(this) },
                    )
                }
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
