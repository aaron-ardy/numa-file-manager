package com.lumina.filemanager.feature.explorer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.lumina.filemanager.feature.app.LuminaUiState
import com.lumina.filemanager.feature.app.LuminaViewModel
import com.lumina.filemanager.core.filesystem.CategoryMatcher

private val categories = listOf("documents", "videos", "images", "audio")

@Composable
fun ExplorerScreen(
    state: LuminaUiState,
    viewModel: LuminaViewModel,
    onRequestAllFilesAccess: () -> Unit,
    onChooseDestination: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var filterMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var scopeMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var sortOption by rememberSaveable { mutableStateOf("date_newest") }

    Column(modifier.fillMaxSize()) {
        if (state.rootUri == null) {
            Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.FolderOpen, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(14.dp))
                Text("Browse your phone storage", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Allow all files access for device-wide browsing.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(18.dp))
                Button(onClick = onRequestAllFilesAccess) { Icon(Icons.Outlined.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Allow all files access") }
            }
            return
        }

        val displayedItems = when {
            state.selectedTagFilterIds.isNotEmpty() -> state.tagFilteredFiles
            state.wholeStorageSearch && state.query.isNotBlank() -> state.wholeStorageResults
            else -> state.items
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 2.dp)) {
            if (state.breadcrumbs.size > 1) {
                IconButton(onClick = viewModel::navigateUp) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Go to parent folder") }
            }
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                state.breadcrumbs.forEachIndexed { index, crumb ->
                    if (index > 0) Text("/", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp))
                    TextButton(onClick = { viewModel.navigateToBreadcrumb(index) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 2.dp)) {
                        Text(crumb, maxLines = 1, color = if (index == state.breadcrumbs.lastIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
            placeholder = { Text("Search this folder") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                androidx.compose.foundation.layout.Box {
                    IconButton(onClick = { scopeMenuExpanded = true }) {
                        Icon(
                            if (state.wholeStorageSearch) Icons.Outlined.Public else Icons.Outlined.FolderOpen,
                            contentDescription = if (state.wholeStorageSearch) "Search whole storage" else "Search this folder"
                        )
                    }
                    DropdownMenu(expanded = scopeMenuExpanded, onDismissRequest = { scopeMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("This folder") }, onClick = {
                            viewModel.setWholeStorageSearch(false)
                            scopeMenuExpanded = false
                        })
                        DropdownMenuItem(text = { Text("Whole storage") }, onClick = {
                            viewModel.setWholeStorageSearch(true)
                            scopeMenuExpanded = false
                        })
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (state.wholeStorageSearch) "Whole storage" else "This folder", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            androidx.compose.foundation.layout.Box {
                IconButton(onClick = { filterMenuExpanded = true }) {
                    Icon(Icons.Outlined.Tune, contentDescription = "Filter and sort files")
                }
                DropdownMenu(expanded = filterMenuExpanded, onDismissRequest = { filterMenuExpanded = false }) {
                    Text("SORT BY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    listOf(
                        "date_newest" to "Date - Newest first",
                        "date_oldest" to "Date - Oldest first",
                        "size_smallest" to "Size - Smallest first",
                        "size_largest" to "Size - Largest first"
                    ).forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(if (sortOption == value) "Selected: $label" else label) },
                            onClick = { sortOption = value; filterMenuExpanded = false }
                        )
                    }
                }
            }
            IconButton(onClick = viewModel::openTagFilter) {
                Icon(Icons.Outlined.Sell, contentDescription = "Filter by tags")
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FilterChip(selected = state.category == null, onClick = { viewModel.setCategory(null) }, label = { Text("All") })
            categories.forEach { category ->
                FilterChip(selected = state.category == category, onClick = { viewModel.setCategory(category) }, label = { Text(category.replaceFirstChar(Char::uppercase)) })
            }
        }

        if (state.selectedUris.isNotEmpty()) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp), shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${state.selectedUris.size} selected", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = {
                        viewModel.selectFilesForTags(
                            displayedItems.filter { it.uri.toString() in state.selectedUris }.map { it.document }
                        )
                    }) { Icon(Icons.Outlined.Sell, contentDescription = "Tag selected file") }
                    IconButton(onClick = { onChooseDestination(false) }) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy selected files") }
                    IconButton(onClick = { onChooseDestination(true) }) { Icon(Icons.Outlined.DriveFileMove, contentDescription = "Move selected files") }
                    IconButton(onClick = {
                        viewModel.requestDeleteSelected(
                            displayedItems.filter { it.uri.toString() in state.selectedUris && !it.isDirectory }.map { it.document }
                        )
                    }) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Move selected files to bin") }
                }
            }
        }

        if (state.isLoading || state.wholeStorageSearchLoading || state.tagFilterLoading) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(18.dp))
        } else {
            val visible = displayedItems.filter { file ->
                val name = file.name
                val queryMatches = state.query.isBlank() || name.contains(state.query, ignoreCase = true)
                val categoryMatches = state.category == null || !file.isDirectory && CategoryMatcher.matches(state.category, name, file.mimeType)
                queryMatches && categoryMatches
            }
            val sorted = when (sortOption) {
                "date_oldest" -> visible.sortedBy { it.lastModified }
                "size_smallest" -> visible.sortedBy { it.size }
                "size_largest" -> visible.sortedByDescending { it.size }
                else -> visible.sortedByDescending { it.lastModified }
            }
            if (sorted.isEmpty()) {
                Column(Modifier.fillMaxWidth().weight(1f).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(
                        when {
                            state.selectedTagFilterIds.isNotEmpty() -> "No files match the selected tags"
                            displayedItems.isEmpty() && state.query.isBlank() -> "This folder is empty"
                            else -> "No matching files"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text("Try another name or category.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)
                ) {
                    items(sorted, key = { it.uri.toString() }) { file ->
                        val uriKey = file.uri.toString()
                        FileRow(
                            file = file,
                            tags = state.fileTags[uriKey].orEmpty(),
                            selected = uriKey in state.selectedUris,
                            onClick = { if (state.selectedUris.isEmpty()) viewModel.openFile(file.document) else viewModel.toggleSelection(file.document) },
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
}