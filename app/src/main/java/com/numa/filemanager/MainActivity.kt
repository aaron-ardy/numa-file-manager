package com.numa.filemanager

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.numa.filemanager.core.designsystem.AppThemeRegistry
import com.numa.filemanager.core.designsystem.NumaTheme
import com.numa.filemanager.core.designsystem.ThemeRepository
import com.numa.filemanager.feature.app.NumaViewModel
import com.numa.filemanager.feature.app.NumaApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: NumaViewModel = viewModel()
            val state by viewModel.state.collectAsState()
            val themeRepository = remember { ThemeRepository(applicationContext) }
            val theme by themeRepository.selectedTheme.collectAsState(initial = AppThemeRegistry.default)
            val scope = rememberCoroutineScope()
            var pendingMove by remember { mutableStateOf(false) }
            val destinationPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                if (uri != null) {
                    runCatching {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    }
                    viewModel.copySelectionTo(uri, pendingMove)
                }
            }
            val legacyStoragePermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
                val granted = grants[android.Manifest.permission.READ_EXTERNAL_STORAGE] == true &&
                    grants[android.Manifest.permission.WRITE_EXTERNAL_STORAGE] == true
                viewModel.updateAllFilesAccess(granted)
            }

            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    viewModel.updateAllFilesAccess(Environment.isExternalStorageManager())
                }
            }

            NumaTheme(appTheme = theme) {
                NumaApp(
                    state = state,
                    viewModel = viewModel,
                    theme = theme,
                    onThemeSelected = { selected -> scope.launch { themeRepository.setTheme(selected.id) } },
                    onRequestAllFilesAccess = {
                        when {
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager() ->
                                viewModel.updateAllFilesAccess(true)
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                                val appSettings = Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                                runCatching { startActivity(appSettings) }.onFailure {
                                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                                }
                            }
                            Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q -> legacyStoragePermissions.launch(
                                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            )
                        }
                    },
                    onChooseDestination = { move ->
                        pendingMove = move
                        destinationPicker.launch(null)
                    }
                )
            }
        }
    }
}