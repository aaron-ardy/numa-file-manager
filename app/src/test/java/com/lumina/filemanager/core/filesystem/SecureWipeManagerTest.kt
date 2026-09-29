package com.lumina.filemanager.core.filesystem

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SecureWipeManagerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun overwritesAndDeletesRegularFile() {
        val file = temporaryFolder.newFile("sensitive.bin")
        file.writeBytes(ByteArray(20_000) { it.toByte() })

        assertTrue(SecureWipeManager.secureDelete(file))
        assertFalse(file.exists())
    }

    @Test
    fun doesNotReportSuccessWhenOverwriteCannotRun() {
        val directory = temporaryFolder.newFolder("non-empty")
        File(directory, "child").writeText("data")

        assertFalse(SecureWipeManager.secureDelete(directory))
    }
}