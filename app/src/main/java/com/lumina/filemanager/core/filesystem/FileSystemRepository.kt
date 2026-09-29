package com.lumina.filemanager.core.filesystem

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.core.content.FileProvider
import com.lumina.filemanager.core.database.LuminaDao
import com.lumina.filemanager.core.database.PinnedFolderEntity
import com.lumina.filemanager.core.database.TrashEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

data class FileSystemItem(
    val document: DocumentFile,
    val uri: Uri,
    val name: String,
    val mimeType: String?,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long
)

class FileSystemRepository(
    private val context: Context,
    private val dao: LuminaDao
) {
    fun root(treeUri: Uri): DocumentFile? = if (treeUri.scheme == "file") {
        treeUri.path?.let { DocumentFile.fromFile(File(it)) }
    } else {
        DocumentFile.fromTreeUri(context, treeUri)
    }

    fun document(uri: Uri): DocumentFile? = if (uri.scheme == "file") {
        uri.path?.let { DocumentFile.fromFile(File(it)) }
    } else {
        DocumentFile.fromSingleUri(context, uri)
    }

    fun item(document: DocumentFile): FileSystemItem = FileSystemItem(
        document = document,
        uri = document.uri,
        name = document.name ?: "Unnamed item",
        mimeType = document.type,
        isDirectory = document.isDirectory,
        size = document.length(),
        lastModified = document.lastModified()
    )

    fun findByCategory(root: DocumentFile, category: String, maxResults: Int = 5000): List<FileSystemItem> {
        val folders = ArrayDeque<DocumentFile>()
        val matches = mutableListOf<FileSystemItem>()
        folders.add(root)
        var visitedFolders = 0

        while (folders.isNotEmpty() && matches.size < maxResults && visitedFolders < 20_000) {
            val folder = folders.removeFirst()
            visitedFolders++
            val children = runCatching { folder.listFiles() }.getOrDefault(emptyArray())
            children.forEach { child ->
                val name = child.name.orEmpty()
                if (child.isDirectory) {
                    if (name != ".trash" && name != ".lumina-filemanager" && !name.startsWith(".")) folders.addLast(child)
                } else if (CategoryMatcher.matches(category, name, child.type) && matches.size < maxResults) {
                    matches += item(child)
                }
            }
        }
        return matches.sortedBy { it.name.lowercase() }
    }

    fun search(root: DocumentFile, query: String, maxResults: Int = 5000): List<FileSystemItem> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return emptyList()

        val folders = ArrayDeque<DocumentFile>()
        val visited = mutableSetOf<String>()
        val matches = mutableListOf<FileSystemItem>()
        folders.add(root)
        while (folders.isNotEmpty() && matches.size < maxResults && visited.size < 20_000) {
            val folder = folders.removeFirst()
            if (!visited.add(folder.uri.toString())) continue
            val children = runCatching { folder.listFiles() }.getOrDefault(emptyArray())
            children.forEach { child ->
                if (child.isDirectory) {
                    if (child.name != ".trash" && child.name != ".lumina-filemanager") {
                        if (child.name.orEmpty().contains(normalizedQuery, ignoreCase = true) && matches.size < maxResults) {
                            matches += item(child)
                        }
                        folders.addLast(child)
                    }
                } else if (child.name.orEmpty().contains(normalizedQuery, ignoreCase = true) && matches.size < maxResults) {
                    matches += item(child)
                }
            }
        }
        return matches.sortedBy { it.name.lowercase() }
    }

    fun findDocument(root: DocumentFile, targetUri: Uri): DocumentFile? {
        if (targetUri.scheme == "file") return targetUri.path?.let { DocumentFile.fromFile(File(it)) }
        val pending = ArrayDeque<DocumentFile>()
        val visited = mutableSetOf<String>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            val folder = pending.removeFirst()
            if (folder.uri == targetUri) return folder
            if (!visited.add(folder.uri.toString())) continue
            folder.listFiles().forEach { child ->
                if (child.isDirectory) pending.addLast(child)
                else if (child.uri == targetUri) return child
            }
        }
        return null
    }

    fun list(folder: DocumentFile): List<FileSystemItem> = folder.listFiles()
        .filterNot { it.name == ".trash" || it.name == ".lumina-filemanager" }
        .map { child ->
            item(child)
        }
        .sortedWith(compareBy<FileSystemItem> { !it.isDirectory }.thenBy { it.name.lowercase() })

    suspend fun copy(source: DocumentFile, destination: DocumentFile, outputName: String = source.name ?: "file"): Result<DocumentFile> = withContext(Dispatchers.IO) {
        runCatching {
            require(!source.isDirectory) { "Only files can be copied by this action." }
            val output = requireNotNull(destination.createFile(source.type ?: "application/octet-stream", outputName))
            try {
                openInputStream(source.uri).use { input ->
                    requireNotNull(input) { "The selected file could not be opened." }
                    openOutputStream(output.uri).use { stream ->
                        requireNotNull(stream) { "The destination could not be written." }
                        input.copyTo(stream)
                    }
                }
                output
            } catch (error: Exception) {
                output.delete()
                throw error
            }
        }
    }

    suspend fun move(source: DocumentFile, destination: DocumentFile): Result<DocumentFile> = withContext(Dispatchers.IO) {
        runCatching {
            val output = copy(source, destination).getOrThrow()
            if (!source.delete()) {
                output.delete()
                error("The copy was created, but the original could not be removed.")
            }
            dao.moveFileTags(source.uri.toString(), output.uri.toString())
            output
        }
    }

    suspend fun rename(file: DocumentFile, name: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(!file.isDirectory) { "Folder renaming is not available in this view." }
            require(name.isNotBlank() && name.none { it == '/' || it == '\\' }) { "Enter a valid file name." }
            check(file.renameTo(name)) { "This storage provider does not support renaming." }
            file.parentFile?.findFile(name)?.uri?.toString() ?: file.uri.toString()
        }
    }

    suspend fun softDelete(file: DocumentFile, originalParent: DocumentFile, root: DocumentFile): Result<TrashEntity> = withContext(Dispatchers.IO) {
        runCatching {
            require(!file.isDirectory) { "Folders cannot be sent to the bin." }
            val originalName = requireNotNull(file.name)
            val size = file.length()
            val trashFolder = root.findFile(".trash") ?: root.createDirectory(".trash")
                ?: error("This storage provider could not create a bin folder.")
            val trashedFile = copy(file, trashFolder, "${System.currentTimeMillis()}_$originalName").getOrThrow()
            if (!file.delete()) {
                trashedFile.delete()
                error("The file was copied to the bin, but the original could not be removed.")
            }
            TrashEntity(
                originalPath = file.uri.toString(),
                trashedPath = trashedFile.uri.toString(),
                originalParentUri = originalParent.uri.toString(),
                fileName = originalName,
                fileSize = size
            )
        }
    }

    suspend fun restore(item: TrashEntity, root: DocumentFile): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val parent = findDocument(root, Uri.parse(item.originalParentUri))
                ?: error("The original folder could not be found. Reconnect its storage location first.")
            val trashedUri = Uri.parse(item.trashedPath)
            val trashed = documentFromUri(trashedUri)
            check(trashed != null && trashed.exists()) { "The binned item is no longer available." }
            val restored = requireNotNull(parent.createFile(trashed.type ?: "application/octet-stream", item.fileName))
            try {
                openInputStream(trashed.uri).use { input ->
                    requireNotNull(input) { "The binned file could not be opened." }
                    openOutputStream(restored.uri).use { output ->
                        requireNotNull(output) { "The original folder could not be written." }
                        input.copyTo(output)
                    }
                }
                check(trashed.delete()) { "The restored copy exists, but the bin copy could not be removed." }
                restored.uri.toString()
            } catch (error: Exception) {
                restored.delete()
                throw error
            }
        }
    }

    suspend fun shredFromBin(item: TrashEntity): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val uri = Uri.parse(item.trashedPath)
            val document = documentFromUri(uri)
            check(document != null && document.exists()) { "The binned item is no longer available." }
            val erased = if (uri.scheme == "file") {
                uri.path?.let { SecureWipeManager.secureDelete(File(it)) } ?: false
            } else {
                SecureWipeManager.secureDelete(context.contentResolver, uri, document::delete)
            }
            check(erased) {
                "This storage provider does not support seekable overwriting. The item remains in the bin."
            }
        }
    }

    suspend fun pin(folder: DocumentFile) {
        if (folder.isDirectory) {
            dao.pinFolder(PinnedFolderEntity(folder.uri.toString(), folder.name ?: "Folder"))
        }
    }

    fun viewIntent(uri: Uri, mimeType: String?): Intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uriForExternal(uri), mimeType ?: "*/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun uriForExternal(uri: Uri): Uri = if (uri.scheme == "file") {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(requireNotNull(uri.path)))
    } else uri

    suspend fun readTextPreview(uri: Uri, lines: Int): String? = withContext(Dispatchers.IO) {
        openInputStream(uri)?.bufferedReader()?.use { reader ->
            generateSequence { reader.readLine() }.take(lines).joinToString("\n")
        }
    }

    private fun documentFromUri(uri: Uri): DocumentFile? = if (uri.scheme == "file") {
        uri.path?.let { DocumentFile.fromFile(File(it)) }
    } else {
        DocumentFile.fromSingleUri(context, uri)
    }

    private fun openInputStream(uri: Uri): InputStream? = if (uri.scheme == "file") {
        FileInputStream(File(requireNotNull(uri.path)))
    } else {
        context.contentResolver.openInputStream(uri)
    }

    private fun openOutputStream(uri: Uri): OutputStream? = if (uri.scheme == "file") {
        FileOutputStream(File(requireNotNull(uri.path)), false)
    } else {
        context.contentResolver.openOutputStream(uri, "w")
    }

}