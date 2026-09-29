package com.lumina.filemanager.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FileTagCrossRefTest {
    @Test
    fun directoryReferencesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            FileTagCrossRef(fileUri = "content://storage/folder", tagId = 1, isDirectory = true)
        }
    }

    @Test
    fun fileReferencesAreAccepted() {
        val reference = FileTagCrossRef(fileUri = "content://storage/file", tagId = 1)

        assertEquals(false, reference.isDirectory)
    }
}