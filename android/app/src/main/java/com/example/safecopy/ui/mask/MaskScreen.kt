package com.example.safecopy.ui.mask

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.safecopy.R
import com.example.safecopy.domain.model.DocumentType
import com.example.safecopy.domain.recognition.FieldKind
import com.example.safecopy.domain.recognition.NormRect
import com.example.safecopy.domain.recognition.NumberMode
import com.example.safecopy.domain.recognition.PageMask
import com.example.safecopy.ui.copyflow.CopySource
import kotlin.math.max
import kotlin.math.min

@Composable
fun MaskRouteScreen(
    source: CopySource?,
    loadFailed: Boolean,
    onBack: () -> Unit,
    onMasked: (List<Bitmap>) -> Unit,
    viewModel: MaskViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(source) { source?.let(viewModel::start) }
    val currentOnMasked by rememberUpdatedState(onMasked)
    LaunchedEffect(state.rendered) {
        state.rendered?.let {
            currentOnMasked(it)
            viewModel.onRenderedHandled()
        }
    }
    MaskScreen(
        state = state,
        loadFailed = loadFailed,
        onBack = onBack,
        onNext = viewModel::onNext,
        onFieldToggle = viewModel::onFieldToggle,
        onNumberModeChange = viewModel::onNumberModeChange,
        onPageSelected = viewModel::onPageSelected,
        onDrawingToggle = viewModel::onDrawingToggle,
        onManualRect = viewModel::onManualRect,
        onUndo = viewModel::onUndoManual,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaskScreen(
    state: MaskUiState,
    loadFailed: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onFieldToggle: (FieldKind, Boolean) -> Unit,
    onNumberModeChange: (NumberMode) -> Unit,
    onPageSelected: (Int) -> Unit,
    onDrawingToggle: () -> Unit,
    onManualRect: (NormRect) -> Unit,
    onUndo: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.flow_mask_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    TextButton(onClick = onNext, enabled = state.canProceed) {
                        Text(stringResource(R.string.action_next), fontWeight = FontWeight.SemiBold)
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                loadFailed -> CenteredMessage(stringResource(R.string.mask_load_failed))
                state.isRecognizing -> Busy(stringResource(R.string.mask_recognizing))
                else -> MaskContent(
                    state = state,
                    onFieldToggle = onFieldToggle,
                    onNumberModeChange = onNumberModeChange,
                    onPageSelected = onPageSelected,
                    onDrawingToggle = onDrawingToggle,
                    onManualRect = onManualRect,
                    onUndo = onUndo,
                )
            }
            if (state.isRendering) Busy(stringResource(R.string.mask_rendering))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaskContent(
    state: MaskUiState,
    onFieldToggle: (FieldKind, Boolean) -> Unit,
    onNumberModeChange: (NumberMode) -> Unit,
    onPageSelected: (Int) -> Unit,
    onDrawingToggle: () -> Unit,
    onManualRect: (NormRect) -> Unit,
    onUndo: () -> Unit,
) {
    val page = state.currentPage
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        if (state.pages.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.pages.indices.forEach { index ->
                    FilterChip(
                        selected = index == page,
                        onClick = { onPageSelected(index) },
                        label = { Text(stringResource(R.string.mask_page, index + 1)) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        state.pages.getOrNull(page)?.let { bitmap ->
            PagePreview(
                page = bitmap,
                mask = state.masks.getOrNull(page) ?: PageMask(emptyList(), false),
                isDrawing = state.isDrawing,
                onRect = onManualRect,
            )
        }
        Spacer(Modifier.height(12.dp))
        TypeLine(state)
        Spacer(Modifier.height(4.dp))

        if (state.availableKinds.isEmpty()) {
            Note(stringResource(R.string.mask_no_fields))
        }
        val type = state.documentType
        state.availableKinds.forEach { kind ->
            val hidden = kind in state.settings.hidden
            ListItem(
                headlineContent = { Text(stringResource(kind.titleRes(type))) },
                supportingContent = { Text(stringResource(kind.subtitleRes(hidden, state.settings.numberMode))) },
                trailingContent = { Switch(checked = hidden, onCheckedChange = null) },
                modifier = Modifier.clickable { onFieldToggle(kind, !hidden) },
            )
            if (kind == FieldKind.NUMBER && hidden) {
                SingleChoiceSegmentedButtonRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    val modes = listOf(NumberMode.KEEP_LAST_4 to R.string.mask_number_last4, NumberMode.FULL to R.string.mask_number_full)
                    modes.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = state.settings.numberMode == mode,
                            onClick = { onNumberModeChange(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                        ) { Text(stringResource(label)) }
                    }
                }
                if (state.settings.numberMode == NumberMode.KEEP_LAST_4 && state.numberFallback) {
                    Note(stringResource(R.string.mask_number_fallback), isWarning = true)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = onDrawingToggle) {
                Icon(Icons.Outlined.Draw, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (state.isDrawing) R.string.mask_draw_done else R.string.mask_draw))
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onUndo, enabled = state.manual.getOrNull(page).orEmpty().isNotEmpty()) {
                Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.mask_undo))
            }
        }
        if (state.isDrawing) Note(stringResource(R.string.mask_draw_hint))
        Note(stringResource(R.string.mask_check_hint))
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TypeLine(state: MaskUiState) {
    val type = state.documentType ?: return
    val typeName = stringResource(type.titleRes)
    val detected = state.detectedType
    val (text, confirmed) = when {
        state.recognitionFailed -> stringResource(R.string.mask_recognition_failed) to false
        detected == type -> stringResource(R.string.mask_detected, typeName) to true
        detected != null -> stringResource(R.string.mask_type_mismatch, typeName, stringResource(detected.titleRes)) to false
        else -> stringResource(R.string.mask_type_manual, typeName) to false
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(
            if (confirmed) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
            contentDescription = null,
            tint = if (confirmed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Страница с масками поверх. Маски здесь — только превью; в пиксели они впечатываются при переходе дальше,
 * теми же координатами ([com.example.safecopy.processing.recognition.MaskRenderer]).
 */
@Composable
private fun PagePreview(page: Bitmap, mask: PageMask, isDrawing: Boolean, onRect: (NormRect) -> Unit) {
    val image = remember(page) { page.asImageBitmap() }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }
    val currentOnRect by rememberUpdatedState(onRect)
    val drawModifier = if (isDrawing) {
        Modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { dragStart = it; dragEnd = it },
                onDrag = { change, _ -> dragEnd = change.position; change.consume() },
                onDragEnd = {
                    val start = dragStart
                    val end = dragEnd
                    if (start != null && end != null) {
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        currentOnRect(
                            NormRect(
                                (min(start.x, end.x) / w).coerceIn(0f, 1f),
                                (min(start.y, end.y) / h).coerceIn(0f, 1f),
                                (max(start.x, end.x) / w).coerceIn(0f, 1f),
                                (max(start.y, end.y) / h).coerceIn(0f, 1f),
                            )
                        )
                    }
                    dragStart = null
                    dragEnd = null
                },
                onDragCancel = { dragStart = null; dragEnd = null },
            )
        }
    } else {
        Modifier
    }
    val outline = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(page.width.toFloat() / page.height)
            .clip(RoundedCornerShape(8.dp))
            .then(drawModifier),
    ) {
        Image(image, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            mask.rects.forEach { r ->
                drawRect(
                    Color.Black,
                    topLeft = Offset(r.left * size.width, r.top * size.height),
                    size = Size(r.width * size.width, r.height * size.height),
                )
            }
            val start = dragStart
            val end = dragEnd
            if (start != null && end != null) {
                val topLeft = Offset(min(start.x, end.x), min(start.y, end.y))
                val draft = Size(kotlin.math.abs(end.x - start.x), kotlin.math.abs(end.y - start.y))
                drawRect(Color.Black.copy(alpha = 0.5f), topLeft, draft)
                drawRect(outline, topLeft, draft, style = Stroke(width = 2.dp.toPx()))
            }
        }
    }
}

@Composable
private fun Note(text: String, isWarning: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
private fun Busy(text: String) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
    }
}

@StringRes
private fun FieldKind.titleRes(type: DocumentType?): Int = when (this) {
    FieldKind.RESIDENCE -> R.string.mask_field_residence
    FieldKind.NUMBER ->
        if (type == DocumentType.PASSPORT_RF || type == DocumentType.DRIVER_LICENSE) R.string.mask_field_series_number
        else R.string.mask_field_number
    FieldKind.DIVISION_CODE -> R.string.mask_field_division
    FieldKind.BIRTHPLACE -> R.string.mask_field_birthplace
    FieldKind.PHOTO -> R.string.mask_field_photo
}

@StringRes
private fun FieldKind.subtitleRes(hidden: Boolean, numberMode: NumberMode): Int = when {
    !hidden -> R.string.mask_show
    this == FieldKind.NUMBER && numberMode == NumberMode.KEEP_LAST_4 -> R.string.mask_keep_last4
    this == FieldKind.PHOTO -> R.string.mask_hide
    else -> R.string.mask_hide_fully
}
