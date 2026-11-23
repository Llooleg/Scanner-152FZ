package com.example.safecopy.ui.scan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.safecopy.R
import com.example.safecopy.domain.model.DocumentType

/**
 * Выбор типа документа перед сохранением в хранилище. Пока идёт распознавание, показывается индикатор;
 * распознанный тип ставится первым и помечается. Решение всё равно за пользователем.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentTypeSheet(
    isRecognizing: Boolean,
    suggestedType: DocumentType?,
    onTypeSelected: (DocumentType) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.scan_type_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Text(
                text = stringResource(R.string.scan_type_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                if (isRecognizing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = stringResource(
                        when {
                            isRecognizing -> R.string.scan_type_recognizing
                            suggestedType != null -> R.string.scan_type_recognized
                            else -> R.string.scan_type_unknown
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val ordered = DocumentType.entries.sortedBy { if (it == suggestedType) 0 else 1 }
            ordered.forEach { type ->
                ListItem(
                    headlineContent = { Text(stringResource(type.titleRes)) },
                    trailingContent = if (type == suggestedType) {
                        {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = stringResource(R.string.scan_type_suggested),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        null
                    },
                    modifier = Modifier.clickable { onTypeSelected(type) },
                )
            }
        }
    }
}
