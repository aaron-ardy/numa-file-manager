package com.numa.filemanager.core.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface NumaDao {
    @Query("SELECT * FROM tags ORDER BY created_at DESC")
    fun observeTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY created_at DESC")
    suspend fun allTags(): List<TagEntity>

    @Query("SELECT COUNT(*) FROM tags")
    suspend fun tagCount(): Int

    @Query("SELECT tags.id AS id, tags.tag_name AS name, tags.color_hex AS colorHex, tags.created_at AS createdAt, COUNT(file_tag_cross_ref.file_uri) AS fileCount FROM tags LEFT JOIN file_tag_cross_ref ON file_tag_cross_ref.tag_id = tags.id GROUP BY tags.id ORDER BY tags.created_at DESC")
    fun observeTagsWithCounts(): Flow<List<TagWithCount>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTag(tag: TagEntity): Long

    @Delete
    suspend fun deleteTag(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTagToFile(reference: FileTagCrossRef)

    @Query("DELETE FROM file_tag_cross_ref WHERE file_uri = :fileUri AND tag_id = :tagId")
    suspend fun removeTagFromFile(fileUri: String, tagId: Int)

    @Query("DELETE FROM file_tag_cross_ref WHERE file_uri = :fileUri")
    suspend fun removeAllTagsFromFile(fileUri: String)

    @Query("SELECT * FROM file_tag_cross_ref WHERE file_uri = :fileUri")
    fun observeFileTags(fileUri: String): Flow<List<FileTagCrossRef>>

    @Query("SELECT * FROM file_tag_cross_ref")
    suspend fun allFileTagReferences(): List<FileTagCrossRef>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTags(tags: List<TagEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFileTagReferences(references: List<FileTagCrossRef>)

    @Query("SELECT tag_id FROM file_tag_cross_ref WHERE file_uri = :fileUri")
    suspend fun tagIdsForFile(fileUri: String): List<Int>

    @Query("SELECT * FROM file_tag_cross_ref WHERE file_uri IN (:fileUris)")
    suspend fun tagsForFileUris(fileUris: List<String>): List<FileTagCrossRef>

    @Query("SELECT file_tag_cross_ref.file_uri AS fileUri, tags.id AS tagId, tags.tag_name AS name, tags.color_hex AS colorHex FROM file_tag_cross_ref INNER JOIN tags ON tags.id = file_tag_cross_ref.tag_id WHERE file_tag_cross_ref.file_uri IN (:fileUris)")
    suspend fun tagsForFiles(fileUris: List<String>): List<FileTagLabel>

    @Query("SELECT COUNT(*) FROM file_tag_cross_ref WHERE tag_id = :tagId")
    suspend fun taggedFileCount(tagId: Int): Int

    @Query("SELECT DISTINCT file_uri FROM file_tag_cross_ref WHERE tag_id IN (:tagIds)")
    suspend fun fileUrisForTags(tagIds: List<Int>): List<String>

    @Query("UPDATE file_tag_cross_ref SET file_uri = :newUri WHERE file_uri = :oldUri")
    suspend fun moveFileTags(oldUri: String, newUri: String)

    @Query("UPDATE tags SET tag_name = :name WHERE id = :tagId")
    suspend fun renameTag(tagId: Int, name: String)

    @Query("UPDATE tags SET tag_name = :name, color_hex = :colorHex WHERE id = :tagId")
    suspend fun updateTag(tagId: Int, name: String, colorHex: String)

    @Query("SELECT * FROM recent_files ORDER BY opened_at DESC LIMIT 100")
    fun observeRecentFiles(): Flow<List<RecentFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordRecentFile(file: RecentFileEntity)

    @Query("DELETE FROM recent_files")
    suspend fun clearRecentFiles()

    @Query("SELECT * FROM pinned_folders ORDER BY pinnedAt DESC")
    fun observePinnedFolders(): Flow<List<PinnedFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun pinFolder(folder: PinnedFolderEntity)

    @Delete
    suspend fun unpinFolder(folder: PinnedFolderEntity)

    @Query("SELECT * FROM trash_items ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<TrashEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrashItem(item: TrashEntity)

    @Query("DELETE FROM trash_items WHERE originalPath = :originalPath")
    suspend fun removeTrashItem(originalPath: String)
}

@Database(
    entities = [TagEntity::class, FileTagCrossRef::class, PinnedFolderEntity::class, TrashEntity::class, RecentFileEntity::class],
    version = 2,
    exportSchema = false
)
abstract class NumaDatabase : RoomDatabase() {
    abstract fun numaDao(): NumaDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recent_files` (`file_uri` TEXT NOT NULL, `display_name` TEXT NOT NULL, `mime_type` TEXT, `file_size` INTEGER NOT NULL, `opened_at` INTEGER NOT NULL, PRIMARY KEY(`file_uri`))"
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_recent_files_opened_at` ON `recent_files` (`opened_at`)")
            }
        }
    }
}

data class FileTagLabel(
    val fileUri: String,
    val tagId: Int,
    val name: String,
    val colorHex: String
)

data class TagWithCount(
    val id: Int,
    val name: String,
    val colorHex: String,
    val createdAt: Long,
    val fileCount: Int
)