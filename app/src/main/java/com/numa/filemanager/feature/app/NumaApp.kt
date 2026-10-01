package com.numa.filemanager.feature.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import com.numa.filemanager.core.designsystem.AppTheme
import com.numa.filemanager.core.designsystem.AppThemeRegistry
import com.numa.filemanager.R
import com.numa.filemanager.feature.dashboard.DashboardScreen
import com.numa.filemanager.feature.dashboard.CategoryFilesScreen
import com.numa.filemanager.feature.dashboard.HomeSearchResultsScreen
import com.numa.filemanager.feature.explorer.ExplorerScreen
import com.numa.filemanager.feature.tags.TagManagementScreen
import com.numa.filemanager.core.filesystem.CategoryMatcher
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumaApp(
    state: NumaUiState,
    viewModel: NumaViewModel,
    theme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
    onRequestAllFilesAccess: () -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }
    val interceptBack = drawerState.isOpen || state.activeCategory != null || state.selectedFile != null ||
        state.quickPeekFile != null || state.renameFile != null || state.confirmation != null || state.tagFilterOpen ||
        state.selectedUris.isNotEmpty() || state.homeQuery.isNotBlank() || state.destination != Destination.Home

    BackHandler(enabled = interceptBack) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            state.tagFilterOpen -> viewModel.closeTagFilter()
            state.confirmation != null -> viewModel.dismissConfirmation()
            state.quickPeekFile != null -> viewModel.showQuickPeek(null)
            state.selectedFile != null -> viewModel.selectFileForTags(null)
            state.renameFile != null -> viewModel.dismissRename()
            state.selectedUris.isNotEmpty() -> viewModel.clearSelection()
            state.destination == Destination.Home && state.homeQuery.isNotBlank() -> viewModel.setHomeQuery("")
            state.activeCategory != null -> viewModel.closeCategory()
            state.destination == Destination.Files && state.breadcrumbs.size > 1 -> viewModel.navigateUp()
            else -> viewModel.selectDestination(Destination.Home)
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                DrawerContent(
                    state = state,
                    theme = theme,
                    onDestination = { destination ->
                        viewModel.selectDestination(destination)
                        scope.launch { drawerState.close() }
                    },
                    onRequestAllFilesAccess = {
                        scope.launch { drawerState.close() }
                        onRequestAllFilesAccess()
                    },
                    onThemeSelected = onThemeSelected,
                    onPinnedFolder = {
                        viewModel.openPinned(it)
                        scope.launch { drawerState.close() }
                    },
                    onUnpinFolder = viewModel::unpinFolder
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        if (state.destination == Destination.Home && state.activeCategory == null) {
                            OutlinedTextField(
                                value = state.homeQuery,
                                onValueChange = viewModel::setHomeQuery,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                                textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                                placeholder = { Text("Search all files") },
                                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                                singleLine = true,
                                shape = RoundedCornerShape(18.dp)
                            )
                        } else if (state.activeCategory != null) {
                            Text(CategoryMatcher.label(state.activeCategory), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        } else if (state.destination != Destination.Home) {
                            Text(
                                when (state.destination) {
                                    Destination.Home -> ""
                                    Destination.Tags -> "Your tags"
                                    Destination.Files -> state.folderName
                                    Destination.Bin -> "Recycle bin"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (state.activeCategory != null) viewModel.closeCategory() else scope.launch { drawerState.open() }
                        }) {
                            Icon(
                                if (state.activeCategory != null) Icons.Outlined.ArrowBack else Icons.Outlined.Menu,
                                contentDescription = if (state.activeCategory != null) "Back to dashboard" else "Open navigation"
                            )
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    tonalElevation = 0.dp
                ) {
                    listOf(
                        Destination.Home to Icons.Outlined.Home,
                        Destination.Tags to Icons.Outlined.Sell,
                        Destination.Files to Icons.Outlined.FolderOpen
                    ).forEach { (destination, icon) ->
                        NavigationBarItem(
                            selected = state.destination == destination,
                            onClick = { viewModel.selectDestination(destination) },
                            icon = { Icon(icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                            alwaysShowLabel = true
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHost) }
        ) { contentPadding ->
            when (state.destination) {
                Destination.Home -> when {
                    state.activeCategory != null -> CategoryFilesScreen(state.activeCategory, state, viewModel, Modifier.padding(contentPadding))
                    state.homeQuery.isNotBlank() -> HomeSearchResultsScreen(state, viewModel, Modifier.padding(contentPadding))
                    else -> DashboardScreen(state, viewModel, onRequestAllFilesAccess, Modifier.padding(contentPadding))
                }
                Destination.Tags -> TagManagementScreen(state, viewModel, Modifier.padding(contentPadding))
                Destination.Files -> ExplorerScreen(state, viewModel, onRequestAllFilesAccess, Modifier.padding(contentPadding))
                Destination.Bin -> BinScreen(state, viewModel, Modifier.padding(contentPadding))
            }
        }
    }

    state.selectedFile?.let { file ->
        ModalBottomSheet(onDismissRequest = viewModel::closeTagAssignment) {
            FileTagSheet(file.name ?: "File", state, viewModel)
        }
    }
    if (state.tagFilterOpen) {
        ModalBottomSheet(onDismissRequest = viewModel::closeTagFilter) {
            TagFilterSheet(state, viewModel)
        }
    }
    state.quickPeekFile?.let { file ->
        ModalBottomSheet(onDismissRequest = { viewModel.showQuickPeek(null) }) {
            QuickPeekSheet(file, state.quickPeekText, state.quickPeekMetadata)
        }
    }
    state.renameFile?.let { file ->
        val fullName = file.name.orEmpty()
        val extension = fullName.substringAfterLast('.', missingDelimiterValue = "").takeIf { it.isNotBlank() }?.let { ".$it" } ?: ""
        val baseName = fullName.substringBeforeLast('.', missingDelimiterValue = fullName)
        var name by remember(file.uri) { mutableStateOf(baseName) }
        AlertDialog(
            onDismissRequest = viewModel::dismissRename,
            title = { Text("Rename file") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        label = { Text("File name") }
                    )
                    if (extension.isNotEmpty()) {
                        Text(extension, modifier = Modifier.padding(start = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.rename(file, "$name$extension") }, enabled = name.isNotBlank()) { Text("Save") } },
            dismissButton = { TextButton(onClick = viewModel::dismissRename) { Text("Cancel") } }
        )
    }
    state.confirmation?.let { confirmation ->
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirmation,
            title = { Text(confirmation.title) },
            text = { Text(confirmation.message) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDestructiveAction) {
                    Text(if (confirmation.action is DestructiveAction.SecureErase) "Erase" else "Delete")
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissConfirmation) { Text("Cancel") } }
        )
    }
    state.fileConflict?.let { conflict ->
        AlertDialog(
            onDismissRequest = viewModel::cancelPendingFileOperation,
            title = { Text("File already exists") },
            text = { Text("file name ${conflict.fileName} already exists in this folder") },
            confirmButton = {
                TextButton(onClick = { viewModel.resolveFileConflict(true) }) { Text("Replace the file") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.resolveFileConflict(false) }) { Text("Skip") }
            }
        )
    }
}

@Composable
private fun DrawerContent(
    state: NumaUiState,
    theme: AppTheme,
    onDestination: (Destination) -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onThemeSelected: (AppTheme) -> Unit,
    onPinnedFolder: (String) -> Unit,
    onUnpinFolder: (String, String) -> Unit
) {
    var themesExpanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Image(
                painter = painterResource(R.drawable.numa_app_icon),
                contentDescription = "Numa",
                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Numa", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("FILE MANAGER", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        DrawerItem("All files", Icons.Outlined.FolderOpen, state.destination == Destination.Files) {
            onDestination(Destination.Files)
            if (!state.allFilesAccessGranted) onRequestAllFilesAccess()
        }
        DrawerItem("Home", Icons.Outlined.Home, state.destination == Destination.Home) { onDestination(Destination.Home) }
        DrawerItem("Tags", Icons.Outlined.Sell, state.destination == Destination.Tags) { onDestination(Destination.Tags) }
        DrawerItem("Themes", Icons.Outlined.Palette, themesExpanded) { themesExpanded = !themesExpanded }
        if (themesExpanded) {
            Column(Modifier.padding(start = 12.dp)) {
                AppThemeRegistry.presets.forEach { preset ->
                    NavigationDrawerItem(
                        label = { Text(preset.name, modifier = Modifier.weight(1f)) },
                        selected = theme.id == preset.id,
                        onClick = { onThemeSelected(preset) },
                        badge = { if (theme.id == preset.id) Text("selected", style = MaterialTheme.typography.labelSmall) },
                        icon = { BoxMark(color = preset.primaryColor, size = 18.dp) },
                        modifier = Modifier.padding(vertical = 1.dp),
                        colors = NavigationDrawerItemDefaults.colors()
                    )
                }
            }
        }
        DrawerItem("Recycle bin", Icons.Outlined.DeleteOutline, state.destination == Destination.Bin) { onDestination(Destination.Bin) }
        HorizontalDivider(Modifier.padding(vertical = 14.dp))
        Text("PINNED FOLDERS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 14.dp, bottom = 6.dp))
        if (state.pinnedFolders.isEmpty()) {
            Text("No pinned folders", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 8.dp))
        } else {
            state.pinnedFolders.take(6).forEach { folder ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DrawerItem(folder.folderName, Icons.Outlined.FolderOpen, false, Modifier.weight(1f)) { onPinnedFolder(folder.folderPath) }
                    IconButton(onClick = { onUnpinFolder(folder.folderPath, folder.folderName) }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Unpin ${folder.folderName}")
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label, maxLines = 1) },
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        modifier = modifier.padding(vertical = 2.dp),
        colors = NavigationDrawerItemDefaults.colors()
    )
}

@Composable
private fun BoxMark(color: Color = MaterialTheme.colorScheme.primary, size: androidx.compose.ui.unit.Dp = 28.dp) {
    androidx.compose.foundation.layout.Box(
        Modifier.size(size).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Outlined.Palette, contentDescription = null, tint = Color.White, modifier = Modifier.size(size / 2))
    }
}