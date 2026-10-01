package com.numa.filemanager.feature.app

import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.numa.filemanager.core.database.TagEntity
import com.numa.filemanager.core.database.TrashEntity
import com.numa.filemanager.feature.explorer.formatBytes
import com.numa.filemanager.feature.explorer.formatModified

@Composable
fun TagFilterSheet(state: NumaUiState, viewModel: NumaViewModel) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp)) {
        Text("Filter by tags", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("Files with any selected tag", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        when {
            state.tags.isEmpty() -> Text(
                "No tags created yet.",
                modifier = Modifier.padding(vertical = 20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.tagFilterLoading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(16.dp))
            else -> state.tags.forEach { tag ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val fallbackColor = MaterialTheme.colorScheme.primary
                    val color = remember(tag.colorHex) { runCatching { Color(AndroidColor.parseColor(tag.colorHex)) }.getOrDefault(fallbackColor) }
                    Surface(Modifier.size(14.dp), color = color, shape = CircleShape) {}
                    Text(tag.name, modifier = Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                    Checkbox(
                        checked = tag.id in state.selectedTagFilterIds,
                        onCheckedChange = { viewModel.toggleTagFilter(tag) }
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun FileTagSheet(fileName: String, state: NumaUiState, viewModel: NumaViewModel) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp)) {
        Text("Edit tags", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            if (state.taggingFiles.size > 1) "${state.taggingFiles.size} files selected; folders skipped" else fileName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(14.dp))
        if (state.tags.isEmpty()) {
            Text("Create a tag in the Tags tab first.", modifier = Modifier.padding(vertical = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            state.tags.forEach { tag ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val fallbackColor = MaterialTheme.colorScheme.primary
                    val color = remember(tag.colorHex) { runCatching { Color(AndroidColor.parseColor(tag.colorHex)) }.getOrDefault(fallbackColor) }
                    Surface(Modifier.size(14.dp), color = color, shape = CircleShape) {}
                    Text(tag.name, modifier = Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                    Checkbox(
                        checked = tag.id in state.selectedTagIds,
                        onCheckedChange = { viewModel.toggleTag(tag) }
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun QuickPeekSheet(file: DocumentFile, textPreview: String?, mediaMetadata: String?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp)) {
        val isPreviewable = file.type?.startsWith("image/") == true || file.type?.startsWith("video/") == true
        if (isPreviewable) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(file.uri).size(256, 256).allowRgb565(true).build(),
                contentDescription = file.name,
                modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(16.dp))
        }
        Text(file.name ?: "File details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(5.dp))
        Text(file.type ?: if (file.isDirectory) "Folder" else "Unknown file type", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        mediaMetadata?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetadataPill("Size", if (file.isDirectory) "Folder" else formatBytes(file.length()))
            MetadataPill("Modified", formatModified(file.lastModified()))
        }
        if (file.type?.startsWith("text/") == true) {
            Spacer(Modifier.height(16.dp))
            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text(textPreview ?: "Loading preview…", modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall, maxLines = 10)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MetadataPill(label: String, value: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun BinScreen(state: NumaUiState, viewModel: NumaViewModel, modifier: Modifier = Modifier) {
    if (state.trash.isEmpty()) {
        Column(modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text("The bin is empty", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Files you move to the bin will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "Secure overwrite requires a seekable storage provider. Flash storage may retain physical copies; unsupported items stay in the bin.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        items(state.trash, key = TrashEntity::originalPath) { item ->
            BinItem(item, onRestore = { viewModel.restore(item) }, onShred = { viewModel.shred(item) })
        }
    }
}

@Composable
private fun BinItem(item: TrashEntity, onRestore: () -> Unit, onShred: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            val uri = remember(item.trashedPath) { Uri.parse(item.trashedPath) }
            val color = MaterialTheme.colorScheme.primaryContainer
            Surface(Modifier.size(42.dp), color = color, shape = RoundedCornerShape(12.dp)) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    AsyncImage(model = uri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(item.fileName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${formatBytes(item.fileSize)} · ${formatModified(item.deletedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onRestore) { Icon(Icons.Outlined.Restore, contentDescription = "Restore ${item.fileName}") }
            IconButton(onClick = onShred) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Secure erase ${item.fileName}", tint = MaterialTheme.colorScheme.error) }
        }
    }
}