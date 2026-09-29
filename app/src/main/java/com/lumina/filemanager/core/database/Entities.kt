package com.lumina.filemanager.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "tag_name") val name: String,
    @ColumnInfo(name = "color_hex") val colorHex: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "file_tag_cross_ref",
    primaryKeys = ["file_uri", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["tag_id"])]
)
data class FileTagCrossRef(
    @ColumnInfo(name = "file_uri") val fileUri: String,
    @ColumnInfo(name = "tag_id") val tagId: Int,
    @ColumnInfo(name = "is_directory") val isDirectory: Boolean = false
) {
    init {
        require(!isDirectory) { "Tags can only be attached to files." }
    }
}

@Entity(tableName = "pinned_folders")
data class PinnedFolderEntity(
    @PrimaryKey val folderPath: String,
    val folderName: String,
    val pinnedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "trash_items")
data class TrashEntity(
    @PrimaryKey val originalPath: String,
    val trashedPath: String,
    val originalParentUri: String = "",
    val fileName: String,
    val deletedAt: Long = System.currentTimeMillis(),
    val fileSize: Long
)

@Entity(tableName = "recent_files", indices = [Index(value = ["opened_at"])] )
data class RecentFileEntity(
    @PrimaryKey @ColumnInfo(name = "file_uri") val fileUri: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "mime_type") val mimeType: String?,
    @ColumnInfo(name = "file_size") val fileSize: Long,
    @ColumnInfo(name = "opened_at") val openedAt: Long = System.currentTimeMillis()
)