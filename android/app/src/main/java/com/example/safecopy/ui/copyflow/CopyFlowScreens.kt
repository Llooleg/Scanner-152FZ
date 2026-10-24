package com.example.safecopy.ui.copyflow

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.safecopy.R
import com.example.safecopy.ui.theme.SafecopyTheme

/*
 * Заглушки шагов конвейера «Маскирование → Водяной знак → Экспорт» (съёмка — ui/scan/ScanScreen.kt).
 * Каждая будет заменена полноценным экраном со своей ViewModel. Общее состояние конвейера
 * хранится в CopyFlowViewModel, привязанной к вложенному графу навигации.
 */

@Composable
fun MaskScreen(pages: List<Bitmap>, onBack: () -> Unit, onNext: () -> Unit) = FlowStepPlaceholder(
    titleRes = R.string.flow_mask_title,
    descriptionRes = R.string.flow_mask_description,
    stage = 4,
    actionRes = R.string.action_next,
    onBack = onBack,
    onAction = onNext,
) {
    // Временно: показываем страницы, полученные со съёмки, чтобы было видно, что конвейер передаёт данные.
    Text(
        text = stringResource(R.string.flow_pages_received, pages.size),
        style = MaterialTheme.typography.titleSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        pages.forEachIndexed { index, page ->
            Image(
                bitmap = page.asImageBitmap(),
                contentDescription = stringResource(R.string.scan_page_number, index + 1),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .weight(1f)
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        }
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
fun WatermarkScreen(onBack: () -> Unit, onNext: () -> Unit) = FlowStepPlaceholder(
    titleRes = R.string.flow_watermark_title,
    descriptionRes = R.string.flow_watermark_description,
    stage = 5,
    actionRes = R.string.action_next,
    onBack = onBack,
    onAction = onNext,
)

@Composable
fun ExportScreen(onBack: () -> Unit, onDone: () -> Unit) = FlowStepPlaceholder(
    titleRes = R.string.flow_export_title,
    descriptionRes = R.string.flow_export_description,
    stage = 6,
    actionRes = R.string.action_done,
    onBack = onBack,
    onAction = onDone,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlowStepPlaceholder(
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    stage: Int,
    @StringRes actionRes: Int,
    onBack: () -> Unit,
    onAction: () -> Unit,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleRes)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(descriptionRes),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.not_implemented_stage, stage),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            content()
            Spacer(Modifier.height(32.dp))
            Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(actionRes))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FlowStepPlaceholderPreview() {
    SafecopyTheme { MaskScreen(pages = emptyList(), onBack = {}, onNext = {}) }
}
