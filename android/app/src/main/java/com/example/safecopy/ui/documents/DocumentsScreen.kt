package com.example.safecopy.ui.documents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.safecopy.R
import com.example.safecopy.domain.model.Document
import com.example.safecopy.domain.model.DocumentType
import com.example.safecopy.ui.theme.SafecopyTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun DocumentsRouteScreen(
    onScanClick: () -> Unit,
    viewModel: DocumentsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DocumentsScreen(state = state, onScanClick = onScanClick)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    state: DocumentsUiState,
    onScanClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.documents_title), fontWeight = FontWeight.SemiBold)
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScanClick,
                icon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null) },
                text = { Text(stringResource(R.string.documents_scan)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { StorageBanner() }
            if (state.documents.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.documents_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                    )
                }
            } else {
                items(state.documents, key = { it.id }) { DocumentCard(it) }
            }
        }
    }
}

@Composable
private fun StorageBanner() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.documents_storage_banner),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private val DateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

@Composable
private fun DocumentCard(document: Document) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
            ) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(document.type.icon, contentDescription = null)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(document.type.titleRes),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = pluralStringResource(R.plurals.document_pages, document.pageCount, document.pageCount) +
                        " · " + stringResource(R.string.document_updated, document.updatedAt.format(DateFormatter)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                color = if (document.copiesCount > 0) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    text = if (document.copiesCount > 0) {
                        pluralStringResource(R.plurals.document_copies, document.copiesCount, document.copiesCount)
                    } else {
                        "—"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

private val DocumentType.icon: ImageVector
    get() = when (this) {
        DocumentType.PASSPORT_RF -> Icons.Outlined.Badge
        DocumentType.DRIVER_LICENSE -> Icons.Outlined.DirectionsCar
        DocumentType.SNILS, DocumentType.INN, DocumentType.OTHER -> Icons.Outlined.Description
    }

@Preview(showBackground = true)
@Composable
private fun DocumentsScreenPreview() {
    SafecopyTheme {
        DocumentsScreen(
            state = DocumentsUiState(
                documents = listOf(
                    Document(1, DocumentType.PASSPORT_RF, 2, LocalDate.of(2026, 9, 12), 3),
                    Document(2, DocumentType.SNILS, 1, LocalDate.of(2026, 8, 3), 1),
                    Document(3, DocumentType.DRIVER_LICENSE, 2, LocalDate.of(2026, 7, 21), 0),
                    Document(4, DocumentType.INN, 1, LocalDate.of(2026, 7, 21), 0),
                )
            ),
            onScanClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DocumentsScreenEmptyPreview() {
    SafecopyTheme { DocumentsScreen(state = DocumentsUiState(), onScanClick = {}) }
}
