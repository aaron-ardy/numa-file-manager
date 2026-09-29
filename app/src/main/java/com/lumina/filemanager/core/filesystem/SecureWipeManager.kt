package com.lumina.filemanager.core.filesystem

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.io.RandomAccessFile
import java.security.SecureRandom

object SecureWipeManager {
    fun secureDelete(resolver: ContentResolver, uri: Uri, unlink: () -> Boolean): Boolean {
        return try {
            val descriptor = resolver.openFileDescriptor(uri, "rw") ?: return false
            ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { stream ->
                val channel = stream.channel
                val length = channel.size()
                if (length > 0) {
                    overwritePass(channel, length, 0x00.toByte())
                    overwritePass(channel, length, 0xFF.toByte())
                    randomOverwritePass(channel, length)
                    channel.truncate(0)
                    channel.force(true)
                }
            }
            unlink()
        } catch (_: Exception) {
            false
        }
    }

    fun secureDelete(file: File): Boolean {
        if (!file.exists()) return true
        if (file.isDirectory) return file.delete()

        return try {
            val length = file.length()
            if (length > 0) {
                RandomAccessFile(file, "rws").use { stream ->
                    overwritePass(stream, length, 0x00.toByte())
                    overwritePass(stream, length, 0xFF.toByte())
                    randomOverwritePass(stream, length)
                    stream.setLength(0)
                    stream.fd.sync()
                }
            }
            file.delete()
        } catch (_: Exception) {
            false
        }
    }

    private fun overwritePass(stream: RandomAccessFile, length: Long, value: Byte) {
        stream.seek(0)
        val buffer = ByteArray(8192) { value }
        var written = 0L
        while (written < length) {
            val count = minOf(buffer.size.toLong(), length - written).toInt()
            stream.write(buffer, 0, count)
            written += count
        }
        stream.fd.sync()
    }

    private fun randomOverwritePass(stream: RandomAccessFile, length: Long) {
        stream.seek(0)
        val random = SecureRandom()
        val buffer = ByteArray(8192)
        var written = 0L
        while (written < length) {
            random.nextBytes(buffer)
            val count = minOf(buffer.size.toLong(), length - written).toInt()
            stream.write(buffer, 0, count)
            written += count
        }
        stream.fd.sync()
    }

    private fun overwritePass(channel: FileChannel, length: Long, value: Byte) {
        channel.position(0)
        val bytes = ByteArray(8192) { value }
        var written = 0L
        while (written < length) {
            val count = minOf(bytes.size.toLong(), length - written).toInt()
            val buffer = ByteBuffer.wrap(bytes, 0, count)
            while (buffer.hasRemaining()) channel.write(buffer)
            written += count
        }
        channel.force(true)
    }

    private fun randomOverwritePass(channel: FileChannel, length: Long) {
        channel.position(0)
        val random = SecureRandom()
        val bytes = ByteArray(8192)
        var written = 0L
        while (written < length) {
            random.nextBytes(bytes)
            val count = minOf(bytes.size.toLong(), length - written).toInt()
            val buffer = ByteBuffer.wrap(bytes, 0, count)
            while (buffer.hasRemaining()) channel.write(buffer)
            written += count
        }
        channel.force(true)
    }
}