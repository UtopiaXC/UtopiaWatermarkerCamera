package com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.data.SettingsRepository
import com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.sensors.LocationTracker
import com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.sensors.SensorReader
import com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.ui.CameraScreen
import com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.ui.PermissionScreen
import com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    
    private lateinit var sensorReader: SensorReader
    private lateinit var locationTracker: LocationTracker
    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        sensorReader = SensorReader(this)
        locationTracker = LocationTracker(this)
        settingsRepository = SettingsRepository(this)
        
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = "permissions") {
                        composable("permissions") {
                            PermissionScreen(
                                onPermissionsGranted = {
                                    navController.navigate("camera") {
                                        popUpTo("permissions") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("camera") {
                            CameraScreen(
                                onNavigateToSettings = { navController.navigate("settings") },
                                sensorReader = sensorReader,
                                locationTracker = locationTracker,
                                settingsRepository = settingsRepository
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                settingsRepository = settingsRepository,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sensorReader.start()
        locationTracker.start()
    }

    override fun onPause() {
        super.onPause()
        sensorReader.stop()
        locationTracker.stop()
    }
}