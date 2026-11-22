package com.example.safecopy.ui.scan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.safecopy.R
import com.example.safecopy.domain.model.DocumentType

/** Выбор типа документа перед сохранением в хранилище. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentTypeSheet(
    onTypeSelected: (DocumentType) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
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
            DocumentType.entries.forEach { type ->
                ListItem(
                    headlineContent = { Text(stringResource(type.titleRes)) },
                    modifier = Modifier.clickable { onTypeSelected(type) },
                )
            }
        }
    }
}
