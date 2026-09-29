package com.lumina.filemanager.feature.explorer

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lumina.filemanager.core.database.FileTagLabel
import com.lumina.filemanager.core.filesystem.FileSystemItem
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    file: FileSystemItem,
    tags: List<FileTagLabel> = emptyList(),
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSelect: () -> Unit,
    onQuickPeek: () -> Unit,
    onEditTags: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit = {},
    onRename: () -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        tonalElevation = if (selected) 1.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(start = 10.dp, end = 2.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileThumbnail(file, Modifier.size(48.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(file.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(
                    if (file.isDirectory) "Folder" else "${formatBytes(file.size)}  ·  ${file.mimeType?.substringAfter('/') ?: "File"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                if (tags.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        tags.take(3).forEach { tag ->
                            val fallbackColor = MaterialTheme.colorScheme.primary
                            val color = remember(tag.colorHex) { runCatching { Color(AndroidColor.parseColor(tag.colorHex)) }.getOrDefault(fallbackColor) }
                            Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(50)) {
                                Text(tag.name, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = color, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
            IconButton(onClick = onSelect) {
                Icon(if (selected) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank, contentDescription = if (selected) "Deselect" else "Select")
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "File actions") }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Quick peek") }, onClick = { menuExpanded = false; onQuickPeek() })
                    if (!file.isDirectory) DropdownMenuItem(text = { Text("Edit tags") }, onClick = { menuExpanded = false; onEditTags() })
                    if (!file.isDirectory) DropdownMenuItem(text = { Text("Rename") }, onClick = { menuExpanded = false; onRename() })
                    if (!file.isDirectory) DropdownMenuItem(text = { Text("Share") }, onClick = { menuExpanded = false; onShare() })
                    if (file.isDirectory) DropdownMenuItem(text = { Text("Pin folder") }, onClick = { menuExpanded = false; onPin() })
                    if (!file.isDirectory) DropdownMenuItem(text = { Text("Move to bin") }, onClick = { menuExpanded = false; onDelete() })
                }
            }
        }
    }
}

@Composable
fun FileThumbnail(file: FileSystemItem, modifier: Modifier = Modifier) {
    val mime = file.mimeType.orEmpty()
    val previewable = !file.isDirectory && (mime.startsWith("image/") || mime.startsWith("video/"))
    val request = ImageRequest.Builder(LocalContext.current)
        .data(file.uri)
        .size(256, 256)
        .allowRgb565(true)
        .build()
    val background = when {
        file.isDirectory -> Color(0xFFE8EDE8)
        mime.startsWith("image/") -> Color(0xFFE9E3D9)
        mime.startsWith("video/") -> Color(0xFFE4E9EF)
        mime.startsWith("audio/") -> Color(0xFFEDE3EB)
        else -> Color(0xFFECE8E1)
    }
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(background), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = when {
                file.isDirectory -> Icons.Outlined.Folder
                mime.startsWith("video/") -> Icons.Outlined.Movie
                mime.startsWith("audio/") -> Icons.Outlined.AudioFile
                mime.startsWith("image/") -> Icons.Outlined.Image
                else -> Icons.Outlined.Description
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        if (previewable) {
            AsyncImage(
                model = request,
                contentDescription = file.name,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "${bytes / (1024L * 1024 * 1024)} GB"
}

fun formatModified(timestamp: Long): String = if (timestamp <= 0) "Date unavailable" else DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))