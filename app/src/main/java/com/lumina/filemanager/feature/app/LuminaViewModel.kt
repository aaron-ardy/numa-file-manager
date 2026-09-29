package com.lumina.filemanager.feature.app

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lumina.filemanager.LuminaApplication
import com.lumina.filemanager.core.database.FileTagCrossRef
import com.lumina.filemanager.core.database.FileTagLabel
import com.lumina.filemanager.core.database.PinnedFolderEntity
import com.lumina.filemanager.core.database.RecentFileEntity
import com.lumina.filemanager.core.database.TagBackupStore
import com.lumina.filemanager.core.database.TagEntity
import com.lumina.filemanager.core.database.TrashEntity
import com.lumina.filemanager.core.filesystem.FileSystemRepository
import com.lumina.filemanager.core.filesystem.FileSystemItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Destination(val label: String) {
    Home("Home"), Tags("Tags"), Files("Files"), Bin("Bin")
}

sealed interface DestructiveAction {
    data class DeleteFile(val file: DocumentFile) : DestructiveAction
    data class DeleteFiles(val files: List<DocumentFile>) : DestructiveAction
    data class DeleteTag(val tag: TagEntity) : DestructiveAction
    data class SecureErase(val item: TrashEntity) : DestructiveAction
}

data class DestructiveConfirmation(
    val title: String,
    val message: String,
    val action: DestructiveAction
)

data class LuminaUiState(
    val destination: Destination = Destination.Home,
    val rootUri: Uri? = null,
    val allFilesAccessGranted: Boolean = false,
    val folderName: String = "Internal storage",
    val breadcrumbs: List<String> = emptyList(),
    val items: List<FileSystemItem> = emptyList(),
    val recentFiles: List<FileSystemItem> = emptyList(),
    val recentFileTags: Map<String, List<FileTagLabel>> = emptyMap(),
    val homeQuery: String = "",
    val homeSearchResults: List<FileSystemItem> = emptyList(),
    val homeSearchLoading: Boolean = false,
    val activeCategory: String? = null,
    val categoryFiles: List<FileSystemItem> = emptyList(),
    val categoryLoading: Boolean = false,
    val tagFilterOpen: Boolean = false,
    val selectedTagFilterIds: Set<Int> = emptySet(),
    val tagFilteredFiles: List<FileSystemItem> = emptyList(),
    val tagFilterLoading: Boolean = false,
    val query: String = "",
    val wholeStorageSearch: Boolean = false,
    val wholeStorageResults: List<FileSystemItem> = emptyList(),
    val wholeStorageSearchLoading: Boolean = false,
    val category: String? = null,
    val tags: List<TagEntity> = emptyList(),
    val tagCounts: Map<Int, Int> = emptyMap(),
    val fileTags: Map<String, List<FileTagLabel>> = emptyMap(),
    val trash: List<TrashEntity> = emptyList(),
    val pinnedFolders: List<PinnedFolderEntity> = emptyList(),
    val selectedFile: DocumentFile? = null,
    val taggingFiles: List<DocumentFile> = emptyList(),
    val renameFile: DocumentFile? = null,
    val selectedTagIds: Set<Int> = emptySet(),
    val quickPeekFile: DocumentFile? = null,
    val quickPeekText: String? = null,
    val quickPeekMetadata: String? = null,
    val selectedUris: Set<String> = emptySet(),
    val message: String? = null,
    val confirmation: DestructiveConfirmation? = null,
    val isLoading: Boolean = false
)

class LuminaViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = (application as LuminaApplication).database.luminaDao()
    private val fileSystem = FileSystemRepository(application, dao)
    private val tagBackupStore = TagBackupStore()
    private val preferences = application.getSharedPreferences("lumina_storage", Application.MODE_PRIVATE)
    private var rootDocument: DocumentFile? = null
    private var currentFolder: DocumentFile? = null
    private val folderStack = mutableListOf<DocumentFile>()
    private val knownDocuments = mutableMapOf<String, DocumentFile>()
    private var homeSearchJob: Job? = null
    private var filesSearchJob: Job? = null
    private var tagFilterJob: Job? = null

    private val mutableState = MutableStateFlow(LuminaUiState())
    val state: StateFlow<LuminaUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            dao.observeTagsWithCounts().collect { rows ->
                val tags = rows.map { TagEntity(it.id, it.name, it.colorHex, it.createdAt) }
                val counts = rows.associate { it.id to it.fileCount }
                mutableState.update { it.copy(tags = tags, tagCounts = counts) }
            }
        }
        viewModelScope.launch {
            dao.observeTrash().collect { trash -> mutableState.update { it.copy(trash = trash) } }
        }
        viewModelScope.launch {
            dao.observePinnedFolders().collect { folders -> mutableState.update { it.copy(pinnedFolders = folders) } }
        }
        viewModelScope.launch {
            dao.observeRecentFiles().collect { recent ->
                val available = withContext(Dispatchers.IO) {
                    recent.mapNotNull { entry ->
                        runCatching {
                            val document = fileSystem.document(Uri.parse(entry.fileUri))
                            if (document != null && document.exists() && !document.isDirectory) fileSystem.item(document) else null
                        }.getOrNull()
                    }
                }
                val uris = available.map { it.uri.toString() }
                val labels = uris.chunked(500).flatMap { dao.tagsForFiles(it) }.groupBy { it.fileUri }
                mutableState.update { it.copy(recentFiles = available, recentFileTags = labels) }
            }
        }
        preferences.getString(KEY_TREE_URI, null)?.let { saved ->
            runCatching { Uri.parse(saved) }.getOrNull()?.let(::selectRoot)
        }
    }

    fun selectRoot(uri: Uri) {
        preferences.edit().putString(KEY_TREE_URI, uri.toString()).apply()
        viewModelScope.launch {
            rootDocument = withContext(Dispatchers.IO) { fileSystem.root(uri) }
            if (uri.scheme == "file" && hasSharedStorageAccess()) {
                if (dao.tagCount() == 0) restoreTagsIfDatabaseEmpty() else persistTagBackup()
            }
            currentFolder = rootDocument
            folderStack.clear()
            rootDocument?.let(folderStack::add)
            mutableState.update { it.copy(rootUri = uri, destination = Destination.Files, category = null) }
            refresh()
        }
    }

    fun updateAllFilesAccess(granted: Boolean) {
        val rootUri = Uri.fromFile(Environment.getExternalStorageDirectory())
        val alreadyOnRoot = rootDocument?.uri == rootUri
        mutableState.update { it.copy(allFilesAccessGranted = granted) }
        if (granted) {
            if (!alreadyOnRoot) selectRoot(rootUri)
        } else if (mutableState.value.rootUri?.scheme == "file") {
            preferences.edit().remove(KEY_TREE_URI).apply()
            rootDocument = null
            currentFolder = null
            folderStack.clear()
            mutableState.update { it.copy(rootUri = null, items = emptyList(), destination = Destination.Home) }
        }
    }

    fun selectDestination(destination: Destination) {
        mutableState.update { it.copy(destination = destination, activeCategory = null, message = null) }
    }

    fun setQuery(query: String) {
        mutableState.update { it.copy(query = query) }
        if (mutableState.value.wholeStorageSearch) searchWholeStorage(query)
    }

    fun setWholeStorageSearch(enabled: Boolean) {
        if (!enabled) filesSearchJob?.cancel()
        mutableState.update {
            it.copy(
                wholeStorageSearch = enabled,
                wholeStorageResults = emptyList(),
                wholeStorageSearchLoading = false
            )
        }
        if (enabled) searchWholeStorage(mutableState.value.query)
    }

    fun setHomeQuery(query: String) {
        mutableState.update { it.copy(homeQuery = query) }
        homeSearchJob?.cancel()
        if (query.isBlank()) {
            mutableState.update { it.copy(homeSearchResults = emptyList(), homeSearchLoading = false) }
            return
        }
        val root = rootDocument ?: return
        homeSearchJob = viewModelScope.launch {
            mutableState.update { it.copy(homeSearchLoading = true) }
            delay(250)
            val results = withContext(Dispatchers.IO) { fileSystem.search(root, query) }
            if (mutableState.value.homeQuery == query) {
                mutableState.update { it.copy(homeSearchResults = results, homeSearchLoading = false) }
            }
        }
    }

    private fun searchWholeStorage(query: String) {
        filesSearchJob?.cancel()
        if (query.isBlank()) {
            mutableState.update { it.copy(wholeStorageResults = emptyList(), wholeStorageSearchLoading = false) }
            return
        }
        val root = rootDocument ?: return
        filesSearchJob = viewModelScope.launch {
            mutableState.update { it.copy(wholeStorageSearchLoading = true) }
            delay(250)
            val results = withContext(Dispatchers.IO) { fileSystem.search(root, query) }
            if (mutableState.value.query == query && mutableState.value.wholeStorageSearch) {
                mutableState.update { it.copy(wholeStorageResults = results, wholeStorageSearchLoading = false) }
            }
        }
    }

    fun showInFiles(document: DocumentFile) {
        val root = rootDocument ?: return showMessage("Allow storage access before browsing files.")
        viewModelScope.launch {
            val targetFolder = withContext(Dispatchers.IO) {
                if (document.isDirectory) document else document.parentFile
            }
            if (targetFolder != null) setFolderPath(root, targetFolder) else {
                folderStack.clear()
                folderStack.add(root)
            }
            currentFolder = targetFolder ?: root
            mutableState.update { it.copy(destination = Destination.Files, activeCategory = null, selectedUris = emptySet(), query = "") }
            refresh()
        }
    }

    private fun setFolderPath(root: DocumentFile, target: DocumentFile) {
        val path = mutableListOf<DocumentFile>()
        val visited = mutableSetOf<String>()
        var current: DocumentFile? = target
        while (current != null && current.uri != root.uri && visited.add(current.uri.toString())) {
            path += current
            current = current.parentFile
        }
        folderStack.clear()
        folderStack.add(root)
        if (current?.uri == root.uri) path.asReversed().forEach(folderStack::add)
        else if (target.uri != root.uri) folderStack.add(target)
    }
    fun setCategory(category: String?) {
        mutableState.update { it.copy(destination = Destination.Files, category = category, query = "") }
    }

    fun openCategory(category: String) {
        val root = rootDocument
        if (root == null) {
            showMessage("Allow storage access before browsing categories.")
            return
        }
        mutableState.update { it.copy(destination = Destination.Home, activeCategory = category, categoryFiles = emptyList(), categoryLoading = true) }
        viewModelScope.launch {
            val matches = withContext(Dispatchers.IO) { fileSystem.findByCategory(root, category) }
            val uris = matches.map { it.uri.toString() }
            val labels = uris.chunked(500).flatMap { dao.tagsForFiles(it) }.groupBy { it.fileUri }
            mutableState.update { it.copy(categoryFiles = matches, categoryLoading = false, fileTags = it.fileTags + labels) }
        }
    }

    fun closeCategory() = mutableState.update { it.copy(activeCategory = null, categoryFiles = emptyList(), categoryLoading = false) }

    fun clearRecentFiles() {
        viewModelScope.launch { dao.clearRecentFiles() }
    }

    fun openTagFilter() = mutableState.update { it.copy(tagFilterOpen = true) }

    fun closeTagFilter() = mutableState.update { it.copy(tagFilterOpen = false) }

    fun toggleTagFilter(tag: TagEntity) {
        val nextIds = if (tag.id in mutableState.value.selectedTagFilterIds) {
            mutableState.value.selectedTagFilterIds - tag.id
        } else {
            mutableState.value.selectedTagFilterIds + tag.id
        }
        tagFilterJob?.cancel()
        mutableState.update {
            it.copy(
                selectedTagFilterIds = nextIds,
                tagFilterLoading = nextIds.isNotEmpty(),
                tagFilteredFiles = if (nextIds.isEmpty()) emptyList() else it.tagFilteredFiles
            )
        }
        if (nextIds.isEmpty()) return
        tagFilterJob = viewModelScope.launch {
            val files = withContext(Dispatchers.IO) {
                dao.fileUrisForTags(nextIds.toList()).mapNotNull { uri ->
                    runCatching {
                        val document = fileSystem.document(Uri.parse(uri))
                        if (document != null && document.exists() && !document.isDirectory) fileSystem.item(document) else null
                    }.getOrNull()
                }
            }
            val uris = files.map { it.uri.toString() }
            val labels = uris.chunked(500).flatMap { dao.tagsForFiles(it) }.groupBy { it.fileUri }
            if (mutableState.value.selectedTagFilterIds == nextIds) {
                mutableState.update { it.copy(tagFilteredFiles = files, tagFilterLoading = false, fileTags = it.fileTags + labels) }
            }
        }
    }

    fun refresh() {
        val folder = currentFolder ?: return
        viewModelScope.launch {
            mutableState.update { it.copy(isLoading = true) }
            val files = withContext(Dispatchers.IO) { fileSystem.list(folder) }
            val fileUris = files.filterNot { it.isDirectory }.map { it.uri.toString() }
            val assignments = fileUris.chunked(500).flatMap { dao.tagsForFiles(it) }
            val labels = assignments.groupBy { it.fileUri }
            files.forEach { knownDocuments[it.uri.toString()] = it.document }
            mutableState.update {
                it.copy(
                    items = files,
                    folderName = folder.name ?: "Internal storage",
                    breadcrumbs = folderStack.map { it.name ?: "Folder" },
                    fileTags = labels,
                    isLoading = false
                )
            }
        }
    }

    fun openFolder(folder: DocumentFile) {
        if (!folder.isDirectory) return
        currentFolder = folder
        if (folderStack.lastOrNull()?.uri != folder.uri) folderStack.add(folder)
        mutableState.update { it.copy(query = "", selectedUris = emptySet(), category = null) }
        refresh()
    }

    fun openPinned(folderPath: String) {
        val root = rootDocument
        if (root == null) {
            showMessage("Reconnect the storage folder before opening pinned locations.")
            return
        }
        viewModelScope.launch {
            val folder = withContext(Dispatchers.IO) {
                fileSystem.findDocument(root, Uri.parse(folderPath))
            }
            if (folder?.isDirectory == true) {
                setFolderPath(root, folder)
                currentFolder = folder
                selectDestination(Destination.Files)
                refresh()
            } else showMessage("This pinned folder is no longer available.")
        }
    }

    fun navigateUp() {
        val root = rootDocument ?: return
        if (folderStack.size <= 1) return
        folderStack.removeAt(folderStack.lastIndex)
        currentFolder = folderStack.lastOrNull() ?: root
        mutableState.update { it.copy(query = "", selectedUris = emptySet()) }
        refresh()
    }

    fun navigateToBreadcrumb(index: Int) {
        if (index !in folderStack.indices) return
        while (folderStack.lastIndex > index) folderStack.removeAt(folderStack.lastIndex)
        currentFolder = folderStack.last()
        mutableState.update { it.copy(query = "", selectedUris = emptySet()) }
        refresh()
    }

    fun selectFileForTags(file: DocumentFile?) {
        if (file == null) {
            clearTagAssignment()
            return
        }
        selectFilesForTags(listOfNotNull(file))
    }

    fun selectFilesForTags(files: List<DocumentFile>) {
        val eligibleFiles = files.filterNot { it.isDirectory }.distinctBy { it.uri.toString() }
        if (eligibleFiles.isEmpty()) {
            showMessage("Select at least one file. Folders cannot be tagged.")
            return
        }
        mutableState.update {
            it.copy(selectedFile = eligibleFiles.first(), taggingFiles = eligibleFiles, selectedTagIds = emptySet())
        }
        viewModelScope.launch {
            val fileUris = eligibleFiles.map { it.uri.toString() }
            val ids = fileUris.chunked(500).flatMap { dao.tagsForFileUris(it) }.map { it.tagId }.toSet()
            mutableState.update { it.copy(selectedTagIds = ids) }
        }
    }

    private fun clearTagAssignment() {
        mutableState.update { it.copy(selectedFile = null, taggingFiles = emptyList(), selectedTagIds = emptySet()) }
    }

    fun closeTagAssignment() = clearTagAssignment()

    fun toggleTag(tag: TagEntity) {
        val files = mutableState.value.taggingFiles
        if (files.isEmpty()) return
        val fileUris = files.map { it.uri.toString() }
        viewModelScope.launch {
            if (tag.id in mutableState.value.selectedTagIds) {
                fileUris.forEach { dao.removeTagFromFile(it, tag.id) }
                mutableState.update { it.copy(selectedTagIds = it.selectedTagIds - tag.id) }
            } else {
                fileUris.forEach { uri -> dao.addTagToFile(FileTagCrossRef(uri, tag.id)) }
                mutableState.update { it.copy(selectedTagIds = it.selectedTagIds + tag.id) }
            }
            persistTagBackup()
            refresh()
        }
    }

    fun createTag(name: String, colorHex: String) {
        val cleanName = name.trim()
        if (cleanName.isEmpty() || cleanName.length > 20 || !colorHex.matches(Regex("#[0-9A-Fa-f]{6}"))) {
            showMessage("Enter a tag name and a valid 6-digit hex color.")
            return
        }
        viewModelScope.launch {
            runCatching {
                dao.insertTag(TagEntity(name = cleanName, colorHex = colorHex))
                persistTagBackup()
            }
                .onFailure { showMessage("That tag could not be saved.") }
        }
    }

    fun deleteTag(tag: TagEntity) {
        mutableState.update {
            it.copy(confirmation = DestructiveConfirmation("Delete tag?", "${tag.name} will be removed from all assigned files.", DestructiveAction.DeleteTag(tag)))
        }
    }

    fun renameTag(tag: TagEntity, newName: String) {
        val cleanName = newName.trim()
        if (cleanName.isEmpty() || cleanName.length > 20) {
            showMessage("Tag names must contain 1 to 20 characters.")
            return
        }
        viewModelScope.launch { dao.renameTag(tag.id, cleanName); persistTagBackup() }
    }

    fun openFile(file: DocumentFile) {
        if (file.isDirectory) {
            openFolder(file)
            return
        }
        runCatching {
            val intent = fileSystem.viewIntent(file.uri, file.type)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            getApplication<Application>().startActivity(intent)
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    dao.recordRecentFile(
                        RecentFileEntity(
                            fileUri = file.uri.toString(),
                            displayName = file.name ?: "File",
                            mimeType = file.type,
                            fileSize = file.length()
                        )
                    )
                }
            }
        }.onFailure { showMessage("No installed app can open this file.") }
    }

    fun showQuickPeek(file: DocumentFile?) {
        mutableState.update { it.copy(quickPeekFile = file, quickPeekText = null, quickPeekMetadata = null) }
        if (file != null && (file.type?.startsWith("text/") == true || file.type?.startsWith("video/") == true || file.type?.startsWith("image/") == true)) {
            viewModelScope.launch {
                val details = withContext(Dispatchers.IO) {
                    if (file.type?.startsWith("text/") == true) {
                        val preview = runCatching { fileSystem.readTextPreview(file.uri, 10) }.getOrNull()
                        preview to null
                    } else {
                        val metadata = runCatching {
                            val retriever = MediaMetadataRetriever()
                            try {
                                retriever.setDataSource(getApplication<Application>(), file.uri)
                                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                                buildList {
                                    if (width != null && height != null) add("$width × $height px")
                                    if (duration != null && duration > 0) add("${duration / 1000}s")
                                }.joinToString(" · ").ifBlank { null }
                            } finally {
                                retriever.release()
                            }
                        }.getOrNull()
                        null to metadata
                    }
                }
                mutableState.update { it.copy(quickPeekText = details.first ?: "Preview unavailable", quickPeekMetadata = details.second) }
            }
        }
    }

    fun toggleSelection(file: DocumentFile) {
        val key = file.uri.toString()
        mutableState.update { state ->
            val next = if (key in state.selectedUris) state.selectedUris - key else state.selectedUris + key
            state.copy(selectedUris = next)
        }
    }

    fun clearSelection() = mutableState.update { it.copy(selectedUris = emptySet()) }

    fun requestDelete(file: DocumentFile) {
        mutableState.update {
            it.copy(confirmation = DestructiveConfirmation("Move to bin?", "${file.name ?: "This file"} will be moved to the bin.", DestructiveAction.DeleteFile(file)))
        }
    }

    fun requestDeleteSelected(files: List<DocumentFile>) {
        if (files.isEmpty()) return
        mutableState.update {
            it.copy(confirmation = DestructiveConfirmation("Move files to bin?", "${files.size} selected files will be moved to the bin.", DestructiveAction.DeleteFiles(files)))
        }
    }

    fun requestSecureErase(item: TrashEntity) {
        mutableState.update {
            it.copy(confirmation = DestructiveConfirmation("Permanently erase?", "${item.fileName} will be overwritten and permanently removed where the storage provider supports it.", DestructiveAction.SecureErase(item)))
        }
    }

    fun dismissConfirmation() = mutableState.update { it.copy(confirmation = null) }

    fun confirmDestructiveAction() {
        val action = mutableState.value.confirmation?.action ?: return
        dismissConfirmation()
        when (action) {
            is DestructiveAction.DeleteFile -> softDelete(action.file)
            is DestructiveAction.DeleteFiles -> action.files.forEach(::softDelete)
            is DestructiveAction.DeleteTag -> viewModelScope.launch { dao.deleteTag(action.tag); persistTagBackup() }
            is DestructiveAction.SecureErase -> performSecureErase(action.item)
        }
    }

    fun requestRename(file: DocumentFile) {
        if (!file.isDirectory) mutableState.update { it.copy(renameFile = file) }
    }

    fun dismissRename() = mutableState.update { it.copy(renameFile = null) }

    fun rename(file: DocumentFile, name: String) {
        viewModelScope.launch {
            fileSystem.rename(file, name)
                .onSuccess { newUri ->
                    dao.moveFileTags(file.uri.toString(), newUri)
                    mutableState.update { it.copy(renameFile = null) }
                    refresh()
                }
                .onFailure { showMessage(it.message ?: "Could not rename this file.") }
        }
    }

    fun copySelectionTo(destinationUri: Uri, move: Boolean) {
        val root = rootDocument ?: return
        val selected = mutableState.value.items.filter { it.uri.toString() in mutableState.value.selectedUris && !it.isDirectory }.map { it.document }
        viewModelScope.launch {
            val destination = withContext(Dispatchers.IO) { fileSystem.root(destinationUri) }
            if (destination == null || !destination.isDirectory) {
                showMessage("The selected destination could not be opened.")
                return@launch
            }
            var completed = 0
            var failed = false
            selected.forEach { file ->
                val result = if (move) fileSystem.move(file, destination) else fileSystem.copy(file, destination)
                if (result.isSuccess) completed++ else failed = true
            }
            if (move) persistTagBackup()
            clearSelection()
            refresh()
            showMessage(if (failed) "$completed file(s) copied or moved; some items could not be processed." else "$completed file(s) ${if (move) "moved" else "copied"}")
        }
    }

    fun shareFile(file: DocumentFile) {
        runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = file.type ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, fileSystem.uriForExternal(file.uri))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            getApplication<Application>().startActivity(Intent.createChooser(intent, "Share file").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { showMessage("This file could not be shared.") }
    }

    fun pin(folder: DocumentFile) {
        viewModelScope.launch {
            fileSystem.pin(folder)
            showMessage("Folder pinned")
        }
    }

    fun unpinFolder(folderPath: String, folderName: String) {
        viewModelScope.launch { dao.unpinFolder(PinnedFolderEntity(folderPath, folderName)) }
    }

    fun softDelete(file: DocumentFile) {
        val root = rootDocument ?: return
        val parent = currentFolder ?: return
        viewModelScope.launch {
            fileSystem.softDelete(file, parent, root)
                .onSuccess { item ->
                    dao.moveFileTags(item.originalPath, item.trashedPath)
                    dao.insertTrashItem(item)
                    persistTagBackup()
                    mutableState.update { it.copy(selectedFile = null, selectedUris = emptySet()) }
                    refresh()
                }
                .onFailure { showMessage(it.message ?: "Could not move this file to the bin.") }
        }
    }

    fun restore(item: TrashEntity) {
        val root = rootDocument ?: return showMessage("Reconnect the storage folder before restoring items.")
        viewModelScope.launch {
            fileSystem.restore(item, root)
                .onSuccess { restoredUri ->
                    dao.moveFileTags(item.trashedPath, restoredUri)
                    dao.removeTrashItem(item.originalPath)
                    persistTagBackup()
                    refresh()
                    showMessage("Restored to its original folder")
                }
                .onFailure { showMessage(it.message ?: "Could not restore this file.") }
        }
    }

    fun shred(item: TrashEntity) {
        requestSecureErase(item)
    }

    private fun performSecureErase(item: TrashEntity) {
        viewModelScope.launch {
            fileSystem.shredFromBin(item)
                .onSuccess {
                    dao.removeAllTagsFromFile(item.trashedPath)
                    dao.removeTrashItem(item.originalPath)
                    persistTagBackup()
                    showMessage("Secure overwrite completed")
                }
                .onFailure { showMessage(it.message ?: "Secure erase is unavailable.") }
        }
    }

    fun dismissMessage() = mutableState.update { it.copy(message = null) }
    private fun showMessage(message: String) = mutableState.update { it.copy(message = message) }

    private fun hasSharedStorageAccess(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q ->
            getApplication<Application>().checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                getApplication<Application>().checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        else -> false
    }

    private suspend fun restoreTagsIfDatabaseEmpty() {
        if (dao.tagCount() != 0) return
        val snapshot = withContext(Dispatchers.IO) { tagBackupStore.read() } ?: return
        if (snapshot.tags.isEmpty()) return
        dao.insertTags(snapshot.tags)
        val validReferences = withContext(Dispatchers.IO) {
            snapshot.references.filter { reference ->
                runCatching {
                    val document = fileSystem.document(Uri.parse(reference.fileUri))
                    document != null && document.exists() && !document.isDirectory
                }.getOrDefault(false)
            }
        }
        if (validReferences.isNotEmpty()) dao.insertFileTagReferences(validReferences)
    }

    private suspend fun persistTagBackup() {
        if (!hasSharedStorageAccess()) return
        val tags = dao.allTags()
        val references = dao.allFileTagReferences()
        withContext(Dispatchers.IO) { tagBackupStore.write(tags, references) }
    }

    private companion object {
        const val KEY_TREE_URI = "tree_uri"
    }
}