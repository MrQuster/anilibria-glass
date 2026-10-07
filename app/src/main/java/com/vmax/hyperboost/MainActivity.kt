package com.vmax.hyperboost

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val perms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { Controller.refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        Controller.init(this)
        perms.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS))

        if (Controller.state.value.enabled) startBoostService()

        setContent {
            HyperTheme {
                AppScreen(onToggle = { on ->
                    Controller.setEnabled(on)
                    if (on) startBoostService() else stopService(Intent(this, BoostService::class.java))
                })
            }
        }
    }

    private fun startBoostService() =
        ContextCompat.startForegroundService(this, Intent(this, BoostService::class.java))

    override fun onStart() {
        super.onStart()
        Controller.acquirePolling()
    }

    override fun onStop() {
        Controller.releasePolling()
        super.onStop()
    }
}
