package com.numa.filemanager.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.numa.filemanager.core.filesystem.CategoryMatcher
import com.numa.filemanager.feature.app.NumaUiState
import com.numa.filemanager.feature.app.NumaViewModel
import com.numa.filemanager.feature.explorer.FileRow

@Composable
fun CategoryFilesScreen(
    category: String,
    state: NumaUiState,
    viewModel: NumaViewModel,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(CategoryMatcher.label(category), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                if (state.categoryLoading) "Scanning storage…" else "${state.categoryFiles.size} matching files",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        when {
            state.categoryLoading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(24.dp))
            state.categoryFiles.isEmpty() -> Text(
                "No matching files found.",
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                items(state.categoryFiles, key = { it.uri.toString() }) { file ->
                    FileRow(
                        file = file,
                        tags = state.fileTags[file.uri.toString()].orEmpty(),
                        selected = file.uri.toString() in state.selectedUris,
                        onClick = { viewModel.openFile(file.document) },
                        onLongClick = { viewModel.showQuickPeek(file.document) },
                        onSelect = { viewModel.toggleSelection(file.document) },
                        onQuickPeek = { viewModel.showQuickPeek(file.document) },
                        onEditTags = { viewModel.selectFileForTags(file.document) },
                        onPin = { viewModel.pin(file.document) },
                        onDelete = { viewModel.requestDelete(file.document) },
                        onShare = { viewModel.shareFile(file.document) },
                        onRename = { viewModel.requestRename(file.document) }
                    )
                }
            }
        }
    }
}
