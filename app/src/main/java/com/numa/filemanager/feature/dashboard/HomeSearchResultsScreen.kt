package com.numa.filemanager.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.numa.filemanager.feature.app.NumaUiState
import com.numa.filemanager.feature.app.NumaViewModel
import com.numa.filemanager.feature.explorer.FileRow

@Composable
fun HomeSearchResultsScreen(state: NumaUiState, viewModel: NumaViewModel, modifier: Modifier = Modifier) {
    when {
        state.homeSearchLoading -> CircularProgressIndicator(modifier.padding(24.dp).then(Modifier))
        state.rootUri == null -> Column(
            modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Allow storage access to search files.", style = MaterialTheme.typography.titleMedium)
        }
        state.homeSearchResults.isEmpty() -> Column(
            modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("No matching files", style = MaterialTheme.typography.titleMedium)
            Text("Search scans accessible shared storage.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> LazyColumn(
            modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            items(state.homeSearchResults, key = { it.uri.toString() }) { file ->
                FileRow(
                    file = file,
                    tags = state.fileTags[file.uri.toString()].orEmpty(),
                    onClick = { viewModel.showInFiles(file.document) },
                    onLongClick = { viewModel.showQuickPeek(file.document) },
                    onSelect = { viewModel.showInFiles(file.document) },
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
