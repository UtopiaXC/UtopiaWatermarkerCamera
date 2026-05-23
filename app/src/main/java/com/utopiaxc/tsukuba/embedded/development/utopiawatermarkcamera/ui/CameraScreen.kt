package com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.ui

import android.content.ContentUris
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaActionSound
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import androidx.camera.camera2.interop.Camera2CameraInfo
import android.hardware.camera2.CameraCharacteristics
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.sensors.DeviceOrientation
import android.view.Surface

@Composable
fun CameraScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToGallery: () -> Unit,
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
    var isProcessing by remember { mutableStateOf(false) }
    
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var minZoomRatio by remember { mutableFloatStateOf(0.5f) }
    var maxZoomRatio by remember { mutableFloatStateOf(10f) }
    
    // Dynamic zoom presets based on device lenses
    var zoomPresets by remember { mutableStateOf(listOf(1f)) }
    
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

    // Gallery thumbnail
    var lastPhotoThumbnail by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    
    // Load last photo thumbnail on mount and after taking photos
    var photoCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(photoCount) {
        lastPhotoThumbnail = loadLastPhotoThumbnail(context)
    }

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
    
    // UI adaptation rotation
    val uiRotation by animateFloatAsState(
        targetValue = when (sensorData.deviceOrientation) {
            DeviceOrientation.LANDSCAPE -> if ((sensorData.gravityX ?: 0f) > 0) 90f else -90f
            else -> 0f
        },
        animationSpec = tween(300),
        label = "uiRotation"
    )

    val takePhoto: () -> Unit = {
        if (countdown == 0 && !isProcessing) {
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
                // Set target rotation based on our custom orientation
                val rotation = when (sensorData.deviceOrientation) {
                    DeviceOrientation.PORTRAIT -> Surface.ROTATION_0
                    DeviceOrientation.LANDSCAPE -> {
                        if ((sensorData.gravityX ?: 0f) > 0) Surface.ROTATION_90 else Surface.ROTATION_270
                    }
                    DeviceOrientation.FLAT -> Surface.ROTATION_0
                }
                capture.targetRotation = rotation

                isProcessing = true
                val cacheFile = File(context.cacheDir, "temp_capture_${System.currentTimeMillis()}.jpg")
                val outputOptions = ImageCapture.OutputFileOptions.Builder(cacheFile).build()
                capture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            // Run watermark processing in a background coroutine
                            // so it doesn't block the UI or countdown timer
                            coroutineScope.launch {
                                try {
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
                                    val authorName = settingsRepository.authorNameFlow.first()
                                    
                                    val config = WatermarkEngine.WatermarkConfig(
                                        enabled = enabled,
                                        showLocation = showLoc,
                                        showAltitude = showAlt,
                                        showPressure = showPres,
                                        showDirection = showDir,
                                        showDeviceInfo = showDev,
                                        showCameraInfo = showCam,
                                        position = pos,
                                        colorMode = col,
                                        authorName = authorName
                                    )
                                    
                                    val uri = output.savedUri ?: Uri.fromFile(cacheFile)
                                    watermarkEngine.processAndSaveImage(
                                        uri,
                                        config,
                                        sensorReader.sensorData.value,
                                        locationTracker.locationData.value,
                                        currentFilter
                                    )
                                    // Trigger thumbnail reload
                                    photoCount++
                                } finally {
                                    isProcessing = false
                                }
                            }
                        }
                        override fun onError(exc: ImageCaptureException) {
                            Log.e("CameraScreen", "Capture failed", exc)
                            isProcessing = false
                        }
                    }
                )
            }
        }
    }
    
    // Shake to capture logic
    LaunchedEffect(shakeEvent) {
        if (shakeEvent > 0L && shakeToCapture && countdown == 0 && !isProcessing) {
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
                    
                    // Build dynamic zoom presets from device camera capabilities
                    zoomPresets = buildDynamicPresets(context, info, lensFacing, minZoomRatio, maxZoomRatio)
                },
                onTapToFocus = { offset, previewView ->
                    focusPoint = offset
                    exposureCompensation = 0f

                    val factory = previewView.meteringPointFactory
                    val point = factory.createPoint(offset.x, offset.y)
                    val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                        .setAutoCancelDuration(5, TimeUnit.SECONDS)
                        .build()
                    cameraControl?.startFocusAndMetering(action)
                },
                onScaleZoom = { scaleFactor ->
                    val newZoom = (zoomRatio * scaleFactor).coerceIn(minZoomRatio, maxZoomRatio)
                    val rounded = (newZoom * 10).roundToInt() / 10f
                    zoomRatio = rounded.coerceIn(minZoomRatio, maxZoomRatio)
                    cameraControl?.setZoomRatio(zoomRatio)
                }
            )
        }

        // 1.5 Filter preview overlay
        if (filterMode > 0) {
            val composeColorMatrix = remember(filterMode) {
                val androidMatrix = FilterEngine.getColorMatrix(filterMode)
                if (androidMatrix != null) {
                    ColorMatrix(androidMatrix.array)
                } else null
            }
            if (composeColorMatrix != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        color = Color.White,
                        colorFilter = ColorFilter.colorMatrix(composeColorMatrix),
                        blendMode = BlendMode.Color
                    )
                }
            }
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
            Box(modifier = Modifier.fillMaxSize()) { // Do not rotate the leveler wrapper, it handles its own angle internally
                HorizonLevelerOverlay(sensorData = sensorData, uiRotation = uiRotation)
            }
        }
        
        // 5. Focus + Exposure overlay
        Box(modifier = Modifier.fillMaxSize().graphicsLayer(rotationZ = uiRotation)) {
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
        }

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
                    color = Color.White,
                    modifier = Modifier.graphicsLayer(rotationZ = uiRotation)
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
                onClick = { showFilterPanel = !showFilterPanel },
                modifier = Modifier.graphicsLayer(rotationZ = uiRotation)
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = stringResource(R.string.filter_style),
                    tint = if (filterMode > 0) Color(0xFFFFCC00) else Color.White
                )
            }

            // Settings button (top right)
            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier.graphicsLayer(rotationZ = uiRotation)
            ) {
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
            Box(modifier = Modifier.graphicsLayer(rotationZ = uiRotation)) {
                ZoomDialControl(
                    currentZoom = zoomRatio,
                    minZoom = minZoomRatio,
                    maxZoom = maxZoomRatio,
                    presets = zoomPresets,
                    onZoomChange = { newZoom ->
                        zoomRatio = newZoom
                        cameraControl?.setZoomRatio(newZoom)
                    }
                )
            }

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
                        .clickable { onNavigateToGallery() },
                    contentAlignment = Alignment.Center
                ) {
                    if (lastPhotoThumbnail != null) {
                        Image(
                            bitmap = lastPhotoThumbnail!!.asImageBitmap(),
                            contentDescription = stringResource(R.string.photo_gallery),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Icon(
                            Icons.Filled.PhotoLibrary,
                            contentDescription = stringResource(R.string.photo_gallery),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp).graphicsLayer(rotationZ = uiRotation)
                        )
                    }
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
                            // Reset zoom when switching cameras
                            zoomRatio = 1f
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Cameraswitch,
                        contentDescription = stringResource(R.string.switch_lens),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp).graphicsLayer(rotationZ = uiRotation + flipRotation)
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
    onTapToFocus: (Offset, PreviewView) -> Unit,
    onScaleZoom: (Float) -> Unit
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
                    
                    // Set up gesture detectors for tap-to-focus and pinch-to-zoom
                    val scaleGestureDetector = ScaleGestureDetector(ctx,
                        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                            override fun onScale(detector: ScaleGestureDetector): Boolean {
                                onScaleZoom(detector.scaleFactor)
                                return true
                            }
                        }
                    )
                    
                    val gestureDetector = GestureDetector(ctx,
                        object : GestureDetector.SimpleOnGestureListener() {
                            override fun onSingleTapUp(e: MotionEvent): Boolean {
                                onTapToFocus(Offset(e.x, e.y), previewView)
                                return true
                            }
                        }
                    )
                    
                    previewView.setOnTouchListener { view, event ->
                        var handled = scaleGestureDetector.onTouchEvent(event)
                        // Only forward to gesture detector if scale detector isn't handling it
                        if (!scaleGestureDetector.isInProgress) {
                            handled = gestureDetector.onTouchEvent(event) || handled
                        }
                        view.performClick()
                        true
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

/**
 * Build dynamic zoom presets based on actual device camera capabilities.
 * For back camera: detect ultra-wide, main, and telephoto lenses.
 * For front camera: typically only 1x.
 */
@androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
private fun buildDynamicPresets(
    context: Context,
    cameraInfo: CameraInfo,
    lensFacing: Int,
    minZoom: Float,
    maxZoom: Float
): List<Float> {
    if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
        return listOf(1f)
    }
    
    val presets = mutableListOf<Float>()
    
    try {
        val camera2Info = Camera2CameraInfo.from(cameraInfo)
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
        val cameraId = camera2Info.cameraId
        val characteristics = manager.getCameraCharacteristics(cameraId)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val physicalIds = characteristics.physicalCameraIds
            
            if (physicalIds.isNotEmpty()) {
                val focalLengths = mutableListOf<Float>()
                for (id in physicalIds) {
                    val physChar = manager.getCameraCharacteristics(id)
                    val arr = physChar.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    if (arr != null && arr.isNotEmpty()) {
                        focalLengths.add(arr[0])
                    }
                }
                focalLengths.sort()
                
                if (focalLengths.isNotEmpty()) {
                    // Main lens is typically 4mm-7mm. If not found, guess the middle or second one.
                    val mainFocalLength = focalLengths.firstOrNull { it > 3.0f && it < 7.0f } 
                        ?: (if (minZoom < 0.9f && focalLengths.size > 1) focalLengths[1] else focalLengths[0])
                        
                    focalLengths.forEach { f ->
                        val ratio = f / mainFocalLength
                        val roundedRatio = when {
                            ratio < 0.8f -> (ratio * 10).roundToInt() / 10f // e.g., 0.5 or 0.6
                            ratio in 0.8f..1.2f -> 1.0f
                            else -> ratio.roundToInt().toFloat() // Telephoto: round to nearest integer (2x, 3x, 5x)
                        }
                        presets.add(roundedRatio.coerceIn(minZoom, maxZoom))
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    
    // Fallback if Camera2 failed or returned no focal lengths
    if (presets.isEmpty()) {
        if (minZoom < 0.9f) {
            val ultraWideZoom = (minZoom * 10).roundToInt() / 10f
            presets.add(ultraWideZoom.coerceAtLeast(0.5f))
        }
        presets.add(1f)
        if (maxZoom >= 2f) {
            when {
                maxZoom >= 5f -> {
                    presets.add(2f)
                    presets.add(5f)
                }
                maxZoom >= 3f -> presets.add(3f)
                maxZoom >= 2f -> presets.add(2f)
            }
        }
    }
    
    return presets.distinct().sorted().filter { it in minZoom..maxZoom }.ifEmpty { listOf(1f) }
}

/**
 * Load the thumbnail of the last photo taken by this app.
 */
private fun loadLastPhotoThumbnail(context: Context): android.graphics.Bitmap? {
    return try {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        )
        
        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        } else {
            "${MediaStore.Images.Media.DATA} LIKE ?"
        }
        val selectionArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf("Pictures/UtopiaCamera%")
        } else {
            arrayOf("%/UtopiaCamera/%")
        }
        
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val id = cursor.getLong(idColumn)
                val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                
                // Load a small thumbnail
                context.contentResolver.openInputStream(contentUri)?.use { inputStream ->
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = 8 // Load a small version
                    }
                    BitmapFactory.decodeStream(inputStream, null, options)
                }
            } else null
        }
    } catch (e: Exception) {
        Log.e("CameraScreen", "Failed to load thumbnail", e)
        null
    }
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
