package com.numa.filemanager.core.database

import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class TagBackupStore {
    private val backupFile: File
        get() = File(Environment.getExternalStorageDirectory(), ".numa-filemanager/tags.json")

    fun read(): TagBackupSnapshot? = runCatching {
        if (!backupFile.isFile) return null
        val data = JSONObject(backupFile.readText())
        val tags = buildList {
            val array = data.optJSONArray("tags") ?: JSONArray()
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    TagEntity(
                        id = item.getInt("id"),
                        name = item.getString("name"),
                        colorHex = item.getString("colorHex"),
                        createdAt = item.getLong("createdAt")
                    )
                )
            }
        }
        val references = buildList {
            val array = data.optJSONArray("fileTags") ?: JSONArray()
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(FileTagCrossRef(fileUri = item.getString("fileUri"), tagId = item.getInt("tagId")))
            }
        }
        TagBackupSnapshot(tags, references)
    }.getOrNull()

    fun write(tags: List<TagEntity>, references: List<FileTagCrossRef>): Boolean = runCatching {
        val directory = backupFile.parentFile ?: return false
        if (!directory.exists() && !directory.mkdirs()) return false
        File(directory, ".nomedia").createNewFile()

        val tagArray = JSONArray().apply {
            tags.forEach { tag ->
                put(JSONObject().apply {
                    put("id", tag.id)
                    put("name", tag.name)
                    put("colorHex", tag.colorHex)
                    put("createdAt", tag.createdAt)
                })
            }
        }
        val referenceArray = JSONArray().apply {
            references.forEach { reference ->
                put(JSONObject().apply {
                    put("fileUri", reference.fileUri)
                    put("tagId", reference.tagId)
                })
            }
        }
        val temporary = File(directory, "tags.json.tmp")
        temporary.writeText(JSONObject().put("version", 1).put("tags", tagArray).put("fileTags", referenceArray).toString())
        if (backupFile.exists() && !backupFile.delete()) return false
        check(temporary.renameTo(backupFile)) { "Could not commit tag backup." }
        true
    }.getOrDefault(false)
}

data class TagBackupSnapshot(
    val tags: List<TagEntity>,
    val references: List<FileTagCrossRef>
)
