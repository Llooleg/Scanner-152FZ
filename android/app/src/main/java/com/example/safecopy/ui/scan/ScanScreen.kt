package com.example.safecopy.ui.scan

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import android.view.Surface
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.safecopy.R
import com.example.safecopy.domain.scan.AutoCaptureState
import com.example.safecopy.domain.scan.Quad
import com.example.safecopy.domain.scan.TiltReading
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.CancellationException
import kotlin.math.ceil
import kotlin.math.roundToInt

private val Accent = Color(0xFF4FD1C5)
private val Warning = Color(0xFFFFB74D)
private const val MAX_TILT_DEGREES = 3f

@Composable
fun ScanRouteScreen(
    onBack: () -> Unit,
    onPagesCaptured: (List<Bitmap>) -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current

    var hasPermission by remember { mutableStateOf(context.hasCameraPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    // Пользователь мог выдать разрешение в системных настройках и вернуться.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hasPermission = context.hasCameraPermission() }

    val camera = remember { DocumentCamera(context.applicationContext, viewModel::onFrame) }
    DisposableEffect(camera) { onDispose { camera.release() } }
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
    }
    var hasFlash by remember { mutableStateOf(false) }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        try {
            camera.bind(lifecycleOwner, previewView.surfaceProvider, view.display?.rotation ?: Surface.ROTATION_0)
            hasFlash = camera.hasFlash
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            viewModel.onCameraUnavailable(e)
        }
    }
    LaunchedEffect(state.torchEnabled) { camera.setTorch(state.torchEnabled) }
    LaunchedEffect(camera) {
        viewModel.captureEvents.collect {
            try {
                viewModel.onPhotoCaptured(camera.capture())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                viewModel.onCaptureFailed(e)
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::onImageImported)
    }
    val scannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            val result = GmsDocumentScanningResult.fromActivityResultIntent(it.data)
            viewModel.onScannerPages(result?.pages.orEmpty().map { page -> page.imageUri })
        }
    }

    ScanScreen(
        state = state,
        hasPermission = hasPermission,
        hasFlash = hasFlash,
        preview = { AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize()) },
        onBack = onBack,
        onNext = { onPagesCaptured(state.pages) },
        onShutter = viewModel::onShutterClick,
        onGallery = {
            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onSystemScanner = {
            if (activity == null) return@ScanScreen
            val options = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(false)
                .setPageLimit((ScanUiState.MAX_PAGES - state.pages.size).coerceAtLeast(1))
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE)
                .build()
            GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                .addOnSuccessListener { scannerLauncher.launch(IntentSenderRequest.Builder(it).build()) }
                .addOnFailureListener(viewModel::onScannerUnavailable)
        },
        onAutoToggle = viewModel::onAutoCaptureToggle,
        onTorchToggle = viewModel::onTorchToggle,
        onRemovePage = viewModel::onRemovePage,
        onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
        onOpenSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            )
        },
        onMessageShown = viewModel::onMessageShown,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    state: ScanUiState,
    hasPermission: Boolean,
    hasFlash: Boolean,
    preview: @Composable () -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onShutter: () -> Unit,
    onGallery: () -> Unit,
    onSystemScanner: () -> Unit,
    onAutoToggle: () -> Unit,
    onTorchToggle: () -> Unit,
    onRemovePage: (Int) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = state.message?.let {
        stringResource(
            when (it) {
                ScanMessage.CameraUnavailable -> R.string.scan_error_camera
                ScanMessage.CaptureFailed -> R.string.scan_error_capture
                ScanMessage.ImportFailed -> R.string.scan_error_import
                ScanMessage.ScannerUnavailable -> R.string.scan_error_scanner
            }
        )
    }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            onMessageShown()
        }
    }

    Scaffold(
        containerColor = Color.Black,
        contentColor = Color.White,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
                title = {
                    Column {
                        Text(stringResource(R.string.flow_scan_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.scan_page_number, state.pages.size + 1),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = onSystemScanner, enabled = state.canCaptureMore) {
                        Icon(Icons.Outlined.DocumentScanner, stringResource(R.string.scan_system_scanner))
                    }
                    if (hasFlash) {
                        IconButton(onClick = onTorchToggle) {
                            Icon(
                                if (state.torchEnabled) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                                stringResource(R.string.scan_torch),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (hasPermission) {
                    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                    Box(modifier = Modifier.aspectRatio(if (isLandscape) 4f / 3f else 3f / 4f)) {
                        preview()
                        QuadOverlay(quad = state.quad, highlighted = state.autoCaptureState is AutoCaptureState.Countdown)
                        if (state.isTiltSensorAvailable) {
                            state.tilt?.let {
                                TiltBadge(it, Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
                            }
                        }
                        StatusPill(
                            text = statusText(state),
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                        )
                        if (state.isProcessing) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Accent)
                        }
                    }
                } else {
                    PermissionPanel(onRequestPermission = onRequestPermission, onOpenSettings = onOpenSettings)
                }
            }

            if (state.pages.isNotEmpty()) {
                PageStrip(pages = state.pages, onRemovePage = onRemovePage)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onGallery, enabled = state.canCaptureMore && !state.isProcessing) {
                    Icon(Icons.Outlined.PhotoLibrary, stringResource(R.string.scan_gallery), tint = Color.White)
                }
                ShutterButton(
                    enabled = hasPermission && state.canCaptureMore && !state.isProcessing,
                    onClick = onShutter,
                )
                TextButton(onClick = onAutoToggle) {
                    Text(
                        stringResource(R.string.scan_auto),
                        color = if (state.autoCaptureEnabled) Accent else Color.White.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Button(
                onClick = onNext,
                enabled = state.pages.isNotEmpty() && !state.isProcessing,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp),
            ) {
                Text(stringResource(R.string.scan_next, state.pages.size))
            }
        }
    }
}

@Composable
private fun statusText(state: ScanUiState): String = when {
    !state.canCaptureMore -> stringResource(R.string.scan_hint_max_pages, ScanUiState.MAX_PAGES)
    state.isProcessing -> stringResource(R.string.scan_hint_processing)
    state.waitingForNextPage -> stringResource(R.string.scan_hint_next_page)
    state.quad == null -> stringResource(R.string.scan_hint_searching)
    !state.autoCaptureEnabled -> stringResource(R.string.scan_hint_manual)
    else -> when (val auto = state.autoCaptureState) {
        is AutoCaptureState.Tilted -> stringResource(R.string.scan_hint_tilted, auto.degrees.roundToInt())
        is AutoCaptureState.Countdown ->
            stringResource(R.string.scan_hint_countdown, ceil(auto.remainingMillis / 1000.0).toInt())
        AutoCaptureState.Ready -> stringResource(R.string.scan_hint_processing)
        AutoCaptureState.Searching -> stringResource(R.string.scan_hint_stabilizing)
    }
}

@Composable
private fun QuadOverlay(quad: Quad?, highlighted: Boolean) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (quad == null) return@Canvas
        val path = Path().apply {
            quad.corners.forEachIndexed { index, point ->
                val offset = Offset(point.x * size.width, point.y * size.height)
                if (index == 0) moveTo(offset.x, offset.y) else lineTo(offset.x, offset.y)
            }
            close()
        }
        val color = if (highlighted) Accent else Color.White
        drawPath(path, color.copy(alpha = 0.15f))
        drawPath(path, color, style = Stroke(width = 3.dp.toPx()))
    }
}

@Composable
private fun TiltBadge(reading: TiltReading, modifier: Modifier = Modifier) {
    val level = reading.degrees <= MAX_TILT_DEGREES
    val color = if (level) Accent else Warning
    Surface(modifier = modifier, color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(50)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Уровень с пузырьком: пузырёк смещается к приподнятому краю телефона.
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .border(1.5.dp, color, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val maxOffset = 6f
                Box(
                    modifier = Modifier
                        .offset(
                            x = (reading.bubbleX.coerceIn(-1f, 1f) * maxOffset * 4).coerceIn(-maxOffset, maxOffset).dp,
                            y = (reading.bubbleY.coerceIn(-1f, 1f) * maxOffset * 4).coerceIn(-maxOffset, maxOffset).dp,
                        )
                        .size(6.dp)
                        .background(color, CircleShape),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.scan_tilt, reading.degrees.roundToInt()),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun StatusPill(text: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.padding(horizontal = 16.dp), color = Color(0xFF0E6B67), shape = RoundedCornerShape(50)) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    val description = stringResource(R.string.scan_shutter)
    Box(
        modifier = Modifier
            .size(72.dp)
            .border(4.dp, Color.White.copy(alpha = if (enabled) 1f else 0.4f), CircleShape)
            .padding(8.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 1f else 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.PhotoCamera, contentDescription = description, tint = Color.Black.copy(alpha = 0.6f))
    }
}

@Composable
private fun PageStrip(pages: List<Bitmap>, onRemovePage: (Int) -> Unit) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        itemsIndexed(pages) { index, page ->
            Box {
                Image(
                    bitmap = page.asImageBitmap(),
                    contentDescription = stringResource(R.string.scan_page_number, index + 1),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .height(72.dp)
                        .aspectRatio(0.7f)
                        .clip(RoundedCornerShape(6.dp)),
                )
                IconButton(
                    onClick = { onRemovePage(index) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.scan_remove_page, index + 1),
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionPanel(onRequestPermission: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(48.dp), tint = Accent)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.scan_permission_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.scan_permission_text),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = Color.White.copy(alpha = 0.8f),
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequestPermission) { Text(stringResource(R.string.scan_permission_grant)) }
        OutlinedButton(onClick = onOpenSettings) {
            Text(stringResource(R.string.scan_permission_settings), color = Color.White)
        }
    }
}

private fun Context.hasCameraPermission() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
