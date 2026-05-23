package com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.data.SettingsRepository
import com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.sensors.LocationTracker
import com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.sensors.SensorReader
import com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.ui.CameraScreen
import com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.ui.GalleryScreen
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
            val appLanguage by settingsRepository.appLanguageFlow.collectAsState(initial = 0)
            
            val locale = when (appLanguage) {
                1 -> java.util.Locale("zh", "CN")
                2 -> java.util.Locale("zh", "TW")
                3 -> java.util.Locale("en")
                4 -> java.util.Locale("ja")
                else -> java.util.Locale.getDefault()
            }
            
            val configuration = LocalConfiguration.current
            configuration.setLocale(locale)
            
            val activityContext = LocalContext.current
            val context = remember(activityContext, locale) {
                object : android.content.ContextWrapper(activityContext) {
                    override fun getResources(): android.content.res.Resources {
                        return activityContext.createConfigurationContext(configuration).resources
                    }
                }
            }
            
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides configuration
            ) {
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
                                onNavigateToGallery = { navController.navigate("gallery") },
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
                        composable("gallery") {
                            GalleryScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
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