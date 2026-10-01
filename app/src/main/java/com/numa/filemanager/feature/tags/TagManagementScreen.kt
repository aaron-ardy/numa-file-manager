package com.numa.filemanager.feature.tags

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.numa.filemanager.core.database.TagEntity
import com.numa.filemanager.feature.app.NumaUiState
import com.numa.filemanager.feature.app.NumaViewModel

private val tagSwatches = listOf("#E06D53", "#1F7A8C", "#6D8B74", "#D6A84F", "#9C6F9E", "#5377A6", "#D17D91", "#6D777A")

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TagManagementScreen(state: NumaUiState, viewModel: NumaViewModel, modifier: Modifier = Modifier) {
    var name by rememberSaveable { mutableStateOf("") }
    var colorHex by rememberSaveable { mutableStateOf(tagSwatches.first()) }
    var editingTag by remember { mutableStateOf<com.numa.filemanager.core.database.TagEntity?>(null) }
    var editName by rememberSaveable { mutableStateOf("") }
    var editColorHex by rememberSaveable { mutableStateOf(tagSwatches.first()) }
    var showCreator by rememberSaveable { mutableStateOf(false) }

    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Your tags", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("${state.tags.size} created", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = { showCreator = true }) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Create")
            }
        }
        if (state.tags.isEmpty()) {
            Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("Your tags will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.tags, key = { it.id }) { tag ->
                    val fallbackColor = MaterialTheme.colorScheme.primary
                    val color = remember(tag.colorHex) { runCatching { Color(AndroidColor.parseColor(tag.colorHex)) }.getOrDefault(fallbackColor) }
                    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(Modifier.size(36.dp), color = color.copy(alpha = 0.17f), shape = RoundedCornerShape(11.dp)) {
                                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                    Surface(Modifier.size(13.dp), color = color, shape = CircleShape) {}
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(tag.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text("${state.tagCounts[tag.id] ?: 0} files", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { editingTag = tag; editName = tag.name; editColorHex = tag.colorHex }) { Icon(Icons.Outlined.Edit, contentDescription = "Edit ${tag.name}") }
                            IconButton(onClick = { viewModel.deleteTag(tag) }) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete ${tag.name}") }
                        }
                    }
                }
            }
        }
    }

    if (showCreator) {
        AlertDialog(
            onDismissRequest = { showCreator = false },
            title = { Text("Create a tag") },
            text = {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    Text("Tags are for individual files", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(20) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Tag name") },
                        singleLine = true,
                        supportingText = { Text("${name.length}/20") }
                    )
                    Spacer(Modifier.height(9.dp))
                    Text("COLOR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(7.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        tagSwatches.forEach { color ->
                            val parsed = Color(AndroidColor.parseColor(color))
                            Surface(
                                modifier = Modifier.size(34.dp).clip(CircleShape).clickable { colorHex = color },
                                color = parsed,
                                shape = CircleShape,
                                border = if (colorHex == color) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null
                            ) {}
                        }
                    }
                    Spacer(Modifier.height(9.dp))
                    OutlinedTextField(
                        value = colorHex,
                        onValueChange = { colorHex = it.take(7).uppercase() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Custom hex color") },
                        singleLine = true,
                        leadingIcon = {
                            val fallbackColor = MaterialTheme.colorScheme.outline
                            val swatch = remember(colorHex) { runCatching { Color(AndroidColor.parseColor(colorHex)) }.getOrDefault(fallbackColor) }
                            Surface(Modifier.size(18.dp), color = swatch, shape = CircleShape) {}
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.createTag(name, colorHex); name = ""; showCreator = false },
                    enabled = name.isNotBlank() && colorHex.matches(Regex("#[0-9A-F]{6}"))
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreator = false }) { Text("Cancel") } }
        )
    }

    editingTag?.let { tag ->
        AlertDialog(
            onDismissRequest = { editingTag = null },
            title = { Text("Edit tag") },
            text = {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    Text("Tags are for individual files", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it.take(20) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Tag name") },
                        singleLine = true,
                        supportingText = { Text("${editName.length}/20") }
                    )
                    Spacer(Modifier.height(9.dp))
                    Text("COLOR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(7.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        tagSwatches.forEach { color ->
                            val parsed = Color(AndroidColor.parseColor(color))
                            Surface(
                                modifier = Modifier.size(34.dp).clip(CircleShape).clickable { editColorHex = color },
                                color = parsed,
                                shape = CircleShape,
                                border = if (editColorHex == color) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null
                            ) {}
                        }
                    }
                    Spacer(Modifier.height(9.dp))
                    OutlinedTextField(
                        value = editColorHex,
                        onValueChange = { editColorHex = it.take(7).uppercase() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Custom hex color") },
                        singleLine = true,
                        leadingIcon = {
                            val fallbackColor = MaterialTheme.colorScheme.outline
                            val swatch = remember(editColorHex) { runCatching { Color(AndroidColor.parseColor(editColorHex)) }.getOrDefault(fallbackColor) }
                            Surface(Modifier.size(18.dp), color = swatch, shape = CircleShape) {}
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateTag(tag, editName, editColorHex)
                        editingTag = null
                    },
                    enabled = editName.isNotBlank() && editColorHex.matches(Regex("#[0-9A-F]{6}")) && (editName.trim() != tag.name || editColorHex != tag.colorHex)
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingTag = null }) { Text("Cancel") } }
        )
    }
}