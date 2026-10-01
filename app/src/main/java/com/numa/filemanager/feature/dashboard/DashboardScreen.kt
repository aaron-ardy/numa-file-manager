package com.numa.filemanager.feature.dashboard

import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.numa.filemanager.feature.app.NumaUiState
import com.numa.filemanager.feature.app.NumaViewModel
import com.numa.filemanager.feature.explorer.FileRow
import com.numa.filemanager.feature.explorer.formatBytes

private data class StorageSummary(val used: Long, val available: Long, val total: Long)

@Composable
fun DashboardScreen(
    state: NumaUiState,
    viewModel: NumaViewModel,
    onRequestAllFilesAccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val storage = remember {
        runCatching {
            @Suppress("DEPRECATION")
            val stats = StatFs(Environment.getExternalStorageDirectory().absolutePath)
            val total = stats.totalBytes
            val available = stats.availableBytes
            StorageSummary((total - available).coerceAtLeast(0), available, total)
        }.getOrElse { StorageSummary(0, 0, 0) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            StorageCard(storage)
        }
        item {
            Column {
                SectionHeading("Browse by type", "Four quick views")
                Spacer(Modifier.height(10.dp))
                val categories = listOf(
                    Triple("Documents", "documents", Icons.Outlined.Description),
                    Triple("Videos", "videos", Icons.Outlined.Movie),
                    Triple("Images", "images", Icons.Outlined.Image),
                    Triple("Audio", "audio", Icons.Outlined.AudioFile)
                )
                categories.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { (label, key, icon) ->
                            CategoryTile(label, icon, Modifier.weight(1f)) { viewModel.openCategory(key) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) { SectionHeading("Recent files", "Recently opened") }
                if (state.rootUri == null) {
                    Button(onClick = onRequestAllFilesAccess) { Text("Allow access") }
                } else if (state.recentFiles.isNotEmpty()) {
                    androidx.compose.material3.TextButton(onClick = viewModel::clearRecentFiles) { Text("Clear") }
                }
            }
        }
        if (state.rootUri == null) {
            item {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.FolderOpen, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(10.dp))
                        Text("Browse your phone storage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Allow all files access to browse and manage files across storage.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onRequestAllFilesAccess) { Text("Allow all files access") }
                    }
                }
            }
        } else {
            val recent = state.recentFiles.take(6)
            if (recent.isEmpty()) {
                item { Text("Files you open will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 18.dp)) }
            } else {
                items(recent, key = { it.uri.toString() }) { file ->
                    FileRow(
                        file = file,
                        tags = state.recentFileTags[file.uri.toString()].orEmpty(),
                        onClick = { viewModel.openFile(file.document) },
                        onLongClick = { viewModel.showQuickPeek(file.document) },
                        onSelect = { viewModel.toggleSelection(file.document) },
                        selected = file.uri.toString() in state.selectedUris,
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

@Composable
private fun StorageCard(storage: StorageSummary) {
    val progress = if (storage.total > 0) storage.used.toFloat() / storage.total.toFloat() else 0f
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("STORAGE OVERVIEW", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(if (storage.total > 0) "${formatBytes(storage.used)} used" else "Storage details unavailable", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    if (storage.total > 0) Text("${formatBytes(storage.available)} available", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)) {
                    Icon(Icons.Outlined.FolderOpen, null, Modifier.padding(13.dp).size(24.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
            if (storage.total > 0) {
                Spacer(Modifier.height(7.dp))
                Text("${formatBytes(storage.total)} total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CategoryTile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(88.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Icon(icon, null, Modifier.padding(10.dp).size(21.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(9.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}