package com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.ui

import android.content.Context
import android.content.Intent
import android.media.MediaActionSound
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.view.MotionEvent
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.R
import com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.data.SettingsRepository
import com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.sensors.LocationTracker
import com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.sensors.SensorReader
import com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.ui.components.FocusExposureOverlay
import com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.ui.components.HorizonLevelerOverlay
import com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.ui.components.ZoomDialControl
import com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.watermark.WatermarkEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@Composable
fun CameraScreen(
    onNavigateToSettings: () -> Unit,
    sensorReader: SensorReader,
    locationTracker: LocationTracker,
    settingsRepository: SettingsRepository
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val watermarkEngine = remember { WatermarkEngine(context) }
    val sound = remember { MediaActionSound() }
    
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var cameraControl: CameraControl? by remember { mutableStateOf(null) }
    var cameraInfo: CameraInfo? by remember { mutableStateOf(null) }
    
    var flashScreen by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var minZoomRatio by remember { mutableFloatStateOf(0.5f) }
    var maxZoomRatio by remember { mutableFloatStateOf(10f) }
    
    // Focus & exposure state
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var exposureCompensation by remember { mutableFloatStateOf(0f) }
    var exposureRange by remember { mutableStateOf(-2f..2f) }
    var exposureStep by remember { mutableFloatStateOf(1f) }
    
    // Filter state
    var showFilterPanel by remember { mutableStateOf(false) }
    val filterMode by settingsRepository.filterModeFlow.collectAsState(initial = 0)
    
    // Sensor data
    val shakeEvent by sensorReader.shakeEvent.collectAsState()
    val shakeToCapture by settingsRepository.shakeToCaptureFlow.collectAsState(initial = false)
    val sensorData by sensorReader.sensorData.collectAsState()
    val gridMode by settingsRepository.gridModeFlow.collectAsState(initial = 0)
    val horizonLeveler by settingsRepository.horizonLevelerFlow.collectAsState(initial = false)
    val photoRatio by settingsRepository.photoRatioFlow.collectAsState(initial = 0)

    // Shutter animation
    var shutterPressed by remember { mutableStateOf(false) }
    val shutterScale by animateFloatAsState(
        targetValue = if (shutterPressed) 0.85f else 1f,
        animationSpec = tween(100),
        label = "shutterScale"
    )

    // Camera flip animation
    var flipTrigger by remember { mutableIntStateOf(0) }
    val flipRotation by animateFloatAsState(
        targetValue = flipTrigger * 180f,
        animationSpec = tween(300),
        label = "flipRotation"
    )

    val takePhoto: () -> Unit = {
        if (countdown == 0) {
            coroutineScope.launch {
                shutterPressed = true
                sound.play(MediaActionSound.SHUTTER_CLICK)
                flashScreen = true
                delay(100)
                flashScreen = false
                shutterPressed = false
            }
            
            val capture = imageCapture
            if (capture != null) {
                val cacheFile = File(context.cacheDir, "temp_capture_${System.currentTimeMillis()}.jpg")
                val outputOptions = ImageCapture.OutputFileOptions.Builder(cacheFile).build()
                capture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            coroutineScope.launch {
                                val enabled = settingsRepository.enableWatermarkFlow.first()
                                val showLoc = settingsRepository.showLocationFlow.first()
                                val showAlt = settingsRepository.showAltitudeFlow.first()
                                val showPres = settingsRepository.showPressureFlow.first()
                                val showDir = settingsRepository.showDirectionFlow.first()
                                val showDev = settingsRepository.showDeviceInfoFlow.first()
                                val showCam = settingsRepository.showCameraInfoFlow.first()
                                val pos = settingsRepository.watermarkPositionFlow.first()
                                val col = settingsRepository.watermarkColorFlow.first()
                                val currentFilter = settingsRepository.filterModeFlow.first()
                                
                                val config = WatermarkEngine.WatermarkConfig(
                                    enabled = enabled,
                                    showLocation = showLoc,
                                    showAltitude = showAlt,
                                    showPressure = showPres,
                                    showDirection = showDir,
                                    showDeviceInfo = showDev,
                                    showCameraInfo = showCam,
                                    position = pos,
                                    colorMode = col
                                )
                                
                                val uri = output.savedUri ?: Uri.fromFile(cacheFile)
                                watermarkEngine.processAndSaveImage(
                                    uri,
                                    0,
                                    config,
                                    sensorReader.sensorData.value,
                                    locationTracker.locationData.value,
                                    currentFilter
                                )
                            }
                        }
                        override fun onError(exc: ImageCaptureException) {
                            Log.e("CameraScreen", "Capture failed", exc)
                        }
                    }
                )
            }
        }
    }
    
    // Shake to capture logic
    LaunchedEffect(shakeEvent) {
        if (shakeEvent > 0L && shakeToCapture && countdown == 0) {
            for (i in 3 downTo 1) {
                countdown = i
                delay(1000)
            }
            countdown = 0
            takePhoto()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // 1. Camera Preview
        key(lensFacing) {
            CameraPreviewView(
                lensFacing = lensFacing,
                filterMode = filterMode,
                onCameraReady = { control, info, capture ->
                    cameraControl = control
                    cameraInfo = info
                    imageCapture = capture
                    
                    val zoomState = info.zoomState.value
                    zoomRatio = zoomState?.zoomRatio ?: 1f
                    minZoomRatio = zoomState?.minZoomRatio ?: 0.5f
                    maxZoomRatio = (zoomState?.maxZoomRatio ?: 10f).coerceAtMost(10f)

                    // Get exposure compensation range
                    val evRange = info.exposureState.exposureCompensationRange
                    val step = info.exposureState.exposureCompensationStep.toFloat()
                    exposureStep = step
                    exposureRange = (evRange.lower * step)..(evRange.upper * step)
                },
                onTapToFocus = { offset, previewView ->
                    focusPoint = offset
                    exposureCompensation = 0f

                    val factory = previewView.meteringPointFactory
                    val point = factory.createPoint(offset.x, offset.y)
                    val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                        .setAutoCancelDuration(3, TimeUnit.SECONDS)
                        .build()
                    cameraControl?.startFocusAndMetering(action)
                }
            )
        }

        // 2. Aspect ratio overlay mask
        if (photoRatio != 3) { // 3 = Full, no mask needed
            AspectRatioMask(ratio = photoRatio)
        }
        
        // 3. Grid overlay
        if (gridMode > 0) {
            GridOverlay(mode = gridMode, photoRatio = photoRatio)
        }
        
        // 4. Horizon leveler
        if (horizonLeveler) {
            HorizonLevelerOverlay(
                pitch = sensorData.pitch ?: 0f,
                roll = sensorData.roll ?: 0f
            )
        }
        
        // 5. Focus + Exposure overlay
        FocusExposureOverlay(
            focusPoint = focusPoint,
            exposureCompensation = exposureCompensation,
            exposureRange = exposureRange,
            onExposureChange = { newEv ->
                exposureCompensation = newEv
                if (exposureStep > 0) {
                    val index = (newEv / exposureStep).roundToInt()
                    cameraControl?.setExposureCompensationIndex(index)
                }
            },
            onDismiss = { focusPoint = null }
        )

        // 6. Flash screen
        AnimatedVisibility(
            visible = flashScreen,
            enter = fadeIn(animationSpec = tween(50)),
            exit = fadeOut(animationSpec = tween(80))
        ) {
            Box(Modifier.fillMaxSize().background(Color.White))
        }
        
        // 7. Countdown overlay
        AnimatedVisibility(
            visible = countdown > 0,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$countdown",
                    fontSize = 96.sp,
                    fontWeight = FontWeight.Thin,
                    color = Color.White
                )
            }
        }

        // 8. Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Filter button (top left)
            IconButton(
                onClick = { showFilterPanel = !showFilterPanel }
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = stringResource(R.string.filter_style),
                    tint = if (filterMode > 0) Color(0xFFFFCC00) else Color.White
                )
            }

            // Settings button (top right)
            IconButton(onClick = onNavigateToSettings) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings), tint = Color.White)
            }
        }

        // 9. Filter selection panel (below top bar)
        AnimatedVisibility(
            visible = showFilterPanel,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 56.dp)
        ) {
            FilterPanel(
                currentFilter = filterMode,
                onFilterSelected = { mode ->
                    coroutineScope.launch {
                        settingsRepository.updateIntSetting(SettingsRepository.FILTER_MODE, mode)
                    }
                    showFilterPanel = false
                }
            )
        }

        // 10. Bottom controls area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Zoom controls
            ZoomDialControl(
                currentZoom = zoomRatio,
                minZoom = minZoomRatio,
                maxZoom = maxZoomRatio,
                onZoomChange = { newZoom ->
                    zoomRatio = newZoom
                    cameraControl?.setZoomRatio(newZoom)
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Bottom bar: Gallery | Shutter | Flip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery thumbnail
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                        .border(1.5.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .clickable {
                            val viewIntent = Intent(
                                Intent.ACTION_VIEW,
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                            )
                            context.startActivity(viewIntent)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.PhotoLibrary,
                        contentDescription = stringResource(R.string.photo_gallery),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Shutter button
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .scale(shutterScale)
                        .clip(CircleShape)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            takePhoto()
                        },
                    contentAlignment = Alignment.Center
                ) { }

                // Camera switch button
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .clickable {
                            flipTrigger++
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                                CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Cameraswitch,
                        contentDescription = stringResource(R.string.switch_lens),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

// ───────── Camera Preview ─────────

@Composable
private fun CameraPreviewView(
    lensFacing: Int,
    filterMode: Int,
    onCameraReady: (CameraControl, CameraInfo, ImageCapture) -> Unit,
    onTapToFocus: (Offset, PreviewView) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
            
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                
                val imageCaptureBuilder = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)

                val imageCapture = imageCaptureBuilder.build()
                
                val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                
                try {
                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                    onCameraReady(camera.cameraControl, camera.cameraInfo, imageCapture)
                    
                    // Tap to focus using standard touch listener
                    previewView.setOnTouchListener { view, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                onTapToFocus(Offset(event.x, event.y), previewView)
                                view.performClick()
                                true
                            }
                            else -> false
                        }
                    }
                } catch (e: Exception) {
                    Log.e("CameraPreview", "Binding failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))
            
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

// ───────── Filter Panel ─────────

@Composable
private fun FilterPanel(
    currentFilter: Int,
    onFilterSelected: (Int) -> Unit
) {
    val filterNames = listOf(
        0 to stringResource(R.string.filter_none),
        1 to stringResource(R.string.filter_bw),
        2 to stringResource(R.string.filter_vintage),
        3 to stringResource(R.string.filter_cool),
        4 to stringResource(R.string.filter_vivid)
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        filterNames.forEach { (mode, name) ->
            val isSelected = currentFilter == mode
            val bgColor = if (isSelected) Color(0xFFFFCC00) else Color.Transparent
            val textColor = if (isSelected) Color.Black else Color.White

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(bgColor)
                    .clickable { onFilterSelected(mode) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name,
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

// ───────── Aspect Ratio Mask ─────────

@Composable
private fun AspectRatioMask(ratio: Int) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val screenWidth = size.width
        val screenHeight = size.height

        val (targetWidth, targetHeight) = when (ratio) {
            0 -> { // 4:3
                val h = screenWidth * 4f / 3f
                screenWidth to h.coerceAtMost(screenHeight)
            }
            1 -> { // 16:9
                val h = screenWidth * 16f / 9f
                screenWidth to h.coerceAtMost(screenHeight)
            }
            2 -> { // 1:1
                val s = screenWidth.coerceAtMost(screenHeight)
                s to s
            }
            else -> screenWidth to screenHeight
        }

        val topMask = (screenHeight - targetHeight) / 2f
        val bottomMask = topMask

        // Top black bar
        if (topMask > 0) {
            drawRect(
                Color.Black.copy(alpha = 0.7f),
                topLeft = Offset.Zero,
                size = androidx.compose.ui.geometry.Size(screenWidth, topMask)
            )
        }
        // Bottom black bar
        if (bottomMask > 0) {
            drawRect(
                Color.Black.copy(alpha = 0.7f),
                topLeft = Offset(0f, screenHeight - bottomMask),
                size = androidx.compose.ui.geometry.Size(screenWidth, bottomMask)
            )
        }
    }
}

// ───────── Grid Overlay ─────────

@Composable
private fun GridOverlay(mode: Int, photoRatio: Int) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val screenWidth = size.width
        val screenHeight = size.height

        // Calculate visible area based on ratio
        val visibleHeight = when (photoRatio) {
            0 -> (screenWidth * 4f / 3f).coerceAtMost(screenHeight)
            1 -> (screenWidth * 16f / 9f).coerceAtMost(screenHeight)
            2 -> screenWidth.coerceAtMost(screenHeight)
            else -> screenHeight
        }
        val topOffset = (screenHeight - visibleHeight) / 2f

        val lineColor = Color.White.copy(alpha = 0.35f)
        val strokeW = 1f

        when (mode) {
            1 -> { // Rule of thirds
                // Vertical lines
                drawLine(lineColor, Offset(screenWidth / 3, topOffset), Offset(screenWidth / 3, topOffset + visibleHeight), strokeWidth = strokeW)
                drawLine(lineColor, Offset(screenWidth * 2 / 3, topOffset), Offset(screenWidth * 2 / 3, topOffset + visibleHeight), strokeWidth = strokeW)
                // Horizontal lines
                drawLine(lineColor, Offset(0f, topOffset + visibleHeight / 3), Offset(screenWidth, topOffset + visibleHeight / 3), strokeWidth = strokeW)
                drawLine(lineColor, Offset(0f, topOffset + visibleHeight * 2 / 3), Offset(screenWidth, topOffset + visibleHeight * 2 / 3), strokeWidth = strokeW)
            }
            2 -> { // Golden ratio (φ ≈ 0.618)
                val phi = 0.618f
                // Vertical
                drawLine(lineColor, Offset(screenWidth * phi, topOffset), Offset(screenWidth * phi, topOffset + visibleHeight), strokeWidth = strokeW)
                drawLine(lineColor, Offset(screenWidth * (1 - phi), topOffset), Offset(screenWidth * (1 - phi), topOffset + visibleHeight), strokeWidth = strokeW)
                // Horizontal
                drawLine(lineColor, Offset(0f, topOffset + visibleHeight * phi), Offset(screenWidth, topOffset + visibleHeight * phi), strokeWidth = strokeW)
                drawLine(lineColor, Offset(0f, topOffset + visibleHeight * (1 - phi)), Offset(screenWidth, topOffset + visibleHeight * (1 - phi)), strokeWidth = strokeW)
            }
        }
    }
}
