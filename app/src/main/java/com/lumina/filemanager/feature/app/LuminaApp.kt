package com.lumina.filemanager.feature.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import com.lumina.filemanager.core.designsystem.AppTheme
import com.lumina.filemanager.core.designsystem.AppThemeRegistry
import com.lumina.filemanager.R
import com.lumina.filemanager.feature.dashboard.DashboardScreen
import com.lumina.filemanager.feature.dashboard.CategoryFilesScreen
import com.lumina.filemanager.feature.dashboard.HomeSearchResultsScreen
import com.lumina.filemanager.feature.explorer.ExplorerScreen
import com.lumina.filemanager.feature.tags.TagManagementScreen
import com.lumina.filemanager.core.filesystem.CategoryMatcher
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LuminaApp(
    state: LuminaUiState,
    viewModel: LuminaViewModel,
    theme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onChooseDestination: (Boolean) -> Unit
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
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Search all files") },
                                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                                singleLine = true,
                                shape = MaterialTheme.shapes.large
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
                NavigationBar {
                    listOf(
                        Destination.Home to Icons.Outlined.Home,
                        Destination.Tags to Icons.Outlined.Sell,
                        Destination.Files to Icons.Outlined.FolderOpen
                    ).forEach { (destination, icon) ->
                        NavigationBarItem(
                            selected = state.destination == destination,
                            onClick = { viewModel.selectDestination(destination) },
                            icon = { Icon(icon, contentDescription = destination.label) },
                            label = { Text(destination.label) }
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
                Destination.Files -> ExplorerScreen(state, viewModel, onRequestAllFilesAccess, onChooseDestination, Modifier.padding(contentPadding))
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
        var name by remember(file.uri) { mutableStateOf(file.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = viewModel::dismissRename,
            title = { Text("Rename file") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("File name") })
            },
            confirmButton = { TextButton(onClick = { viewModel.rename(file, name) }, enabled = name.isNotBlank()) { Text("Save") } },
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
}

@Composable
private fun DrawerContent(
    state: LuminaUiState,
    theme: AppTheme,
    onDestination: (Destination) -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onThemeSelected: (AppTheme) -> Unit,
    onPinnedFolder: (String) -> Unit,
    onUnpinFolder: (String, String) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Image(
                painter = painterResource(R.drawable.lumina_app_icon),
                contentDescription = "Lumina",
                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Lumina", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
        HorizontalDivider(Modifier.padding(vertical = 14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp, bottom = 8.dp)) {
            Icon(Icons.Outlined.Palette, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text("THEMES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppThemeRegistry.presets.forEach { preset ->
                Surface(
                    modifier = Modifier.width(84.dp).clickable { onThemeSelected(preset) },
                    shape = MaterialTheme.shapes.small,
                    color = if (theme.id == preset.id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.Start) {
                        BoxMark(color = preset.primaryColor, size = 18.dp)
                        Spacer(Modifier.height(7.dp))
                        Text(preset.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
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
        Text("L", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}