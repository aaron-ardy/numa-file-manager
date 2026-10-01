package com.numa.filemanager.feature.explorer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.numa.filemanager.feature.app.NumaUiState
import com.numa.filemanager.feature.app.NumaViewModel
import com.numa.filemanager.feature.app.ClipboardMode
import com.numa.filemanager.core.filesystem.CategoryMatcher

private val categories = listOf("documents", "videos", "images", "audio")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExplorerScreen(
    state: NumaUiState,
    viewModel: NumaViewModel,
    onRequestAllFilesAccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var filterMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var fileTypeMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var sortMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var scopeMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var multiSelectMode by rememberSaveable { mutableStateOf(false) }
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
            placeholder = { Text(if (state.wholeStorageSearch) "Search the whole system" else "Search in this folder") },
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
            Box(
                Modifier.size(48.dp).combinedClickable(
                    onClick = { multiSelectMode = !multiSelectMode; if (!multiSelectMode) viewModel.clearSelection() },
                    onLongClick = { android.widget.Toast.makeText(context, "Select multiple", android.widget.Toast.LENGTH_SHORT).show() }
                )
            ) {
                Icon(Icons.Outlined.CheckCircleOutline, contentDescription = if (multiSelectMode) "Close multi-select" else "Select files", modifier = Modifier.align(Alignment.Center))
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.foundation.layout.Box {
                    Box(
                        Modifier.size(48.dp).combinedClickable(
                            role = Role.Button,
                            onClick = {
                                filterMenuExpanded = true
                                sortMenuExpanded = false
                            },
                            onLongClick = {
                                android.widget.Toast.makeText(context, "Filter by", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.FilterAlt, contentDescription = "Filter by")
                    }
                    DropdownMenu(expanded = filterMenuExpanded, onDismissRequest = { filterMenuExpanded = false }) {
                        Text("FILTER BY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        DropdownMenuItem(
                            text = { Text("File type") },
                            onClick = {
                                filterMenuExpanded = false
                                fileTypeMenuExpanded = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Tags") },
                            onClick = {
                                filterMenuExpanded = false
                                viewModel.openTagFilter()
                            }
                        )
                    }
                    DropdownMenu(expanded = fileTypeMenuExpanded, onDismissRequest = { fileTypeMenuExpanded = false }) {
                        Text("FILE TYPE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        listOf(
                            "All" to null,
                            "Documents" to "documents",
                            "Videos" to "videos",
                            "Images" to "images",
                            "Audio" to "audio"
                        ).forEach { (label, category) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    viewModel.setCategory(category)
                                    fileTypeMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                androidx.compose.foundation.layout.Box {
                    Box(
                        Modifier.size(48.dp).combinedClickable(
                            role = Role.Button,
                            onClick = {
                                sortMenuExpanded = true
                                filterMenuExpanded = false
                            },
                            onLongClick = {
                                android.widget.Toast.makeText(context, "Sort by", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Sort, contentDescription = "Sort by")
                    }
                    DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                        Text("SORT BY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        listOf(
                            Triple("date", if (sortOption == "date_oldest") "Date - Oldest first" else "Date - Newest first", sortOption == "date_oldest"),
                            Triple("name", if (sortOption == "name_descending") "Name - Descending" else "Name - Ascending", sortOption == "name_descending"),
                            Triple("size", if (sortOption == "size_smallest") "Size - Smallest first" else "Size - Biggest first", sortOption == "size_smallest")
                        ).forEach { (value, label, descending) ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(label, modifier = Modifier.weight(1f))
                                        Icon(if (descending) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward, contentDescription = null)
                                    }
                                },
                                onClick = {
                                    val next = when (value) {
                                        "date" -> if (sortOption == "date_newest") "date_oldest" else "date_newest"
                                        "name" -> if (sortOption == "name_ascending") "name_descending" else "name_ascending"
                                        else -> if (sortOption == "size_largest") "size_smallest" else "size_largest"
                                    }
                                    sortOption = next
                                    sortMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        if (state.selectedUris.isNotEmpty() || state.pendingFileOperation != null) {
            val selectedCount = state.pendingFileOperation?.files?.size ?: state.selectedUris.size
            val selectedLabel = if (selectedCount == 1) "1 item selected" else "$selectedCount items selected"
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp), shape = RoundedCornerShape(14.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(selectedLabel, style = MaterialTheme.typography.labelLarge)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                    if (state.pendingFileOperation != null) {
                        IconButton(onClick = viewModel::pasteIntoCurrentFolder) { Icon(Icons.Outlined.ContentPaste, contentDescription = "Paste files here") }
                        IconButton(onClick = viewModel::cancelPendingFileOperation) { Icon(Icons.Outlined.Close, contentDescription = "Cancel copy or move") }
                    } else {
                        IconButton(onClick = {
                            displayedItems.firstOrNull { it.uri.toString() in state.selectedUris }?.document?.let(viewModel::requestRename)
                        }) { Icon(Icons.Outlined.Edit, contentDescription = "Rename selected file") }
                        IconButton(onClick = {
                            viewModel.selectFilesForTags(displayedItems.filter { it.uri.toString() in state.selectedUris }.map { it.document })
                        }) { Icon(Icons.Outlined.Sell, contentDescription = "Tag selected files") }
                        IconButton(onClick = { viewModel.stageSelection(ClipboardMode.COPY) }) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy selected files") }
                        IconButton(onClick = { viewModel.stageSelection(ClipboardMode.CUT) }) { Icon(Icons.Outlined.ContentCut, contentDescription = "Cut selected files") }
                        IconButton(onClick = {
                            viewModel.shareFiles(displayedItems.filter { it.uri.toString() in state.selectedUris }.map { it.document })
                        }) { Icon(Icons.Outlined.Share, contentDescription = "Share selected files") }
                        IconButton(onClick = {
                            viewModel.requestDeleteSelected(
                                displayedItems.filter { it.uri.toString() in state.selectedUris && !it.isDirectory }.map { it.document }
                            )
                        }) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Move selected files to bin") }
                    }
                    }
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
                "name_ascending" -> visible.sortedBy { it.name.lowercase() }
                "name_descending" -> visible.sortedByDescending { it.name.lowercase() }
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
                            onClick = {
                                if (multiSelectMode && !file.isDirectory) viewModel.toggleSelection(file.document)
                                else viewModel.openFile(file.document)
                            },
                            onLongClick = { viewModel.showQuickPeek(file.document) },
                            onSelect = { viewModel.toggleSelection(file.document) },
                            onQuickPeek = { viewModel.showQuickPeek(file.document) },
                            onEditTags = { viewModel.selectFileForTags(file.document) },
                            onPin = { viewModel.pin(file.document) },
                            onDelete = { viewModel.requestDelete(file.document) },
                            onShare = { viewModel.shareFile(file.document) },
                            onRename = { viewModel.requestRename(file.document) },
                            onCopy = { viewModel.stageFiles(listOf(file.document), ClipboardMode.COPY) },
                            onCut = { viewModel.stageFiles(listOf(file.document), ClipboardMode.CUT) },
                            showSelectionControl = multiSelectMode
                        )
                    }
                }
            }
        }
    }
}