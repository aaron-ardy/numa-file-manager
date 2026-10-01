package com.numa.filemanager.core.filesystem

object CategoryMatcher {
    private val documentExtensions = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp",
        "txt", "rtf", "md", "csv", "epub", "json", "xml"
    )
    private val videoExtensions = setOf("mp4", "m4v", "mkv", "webm", "mov", "avi", "3gp", "mpeg", "mpg", "mts", "m2ts")
    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg", "dng", "cr2", "nef", "arw")
    private val audioExtensions = setOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus", "wma", "aiff", "amr")

    fun matches(category: String, name: String, mimeType: String?): Boolean {
        val extension = name.substringAfterLast('.', "").lowercase()
        val mime = mimeType.orEmpty().lowercase()
        return when (category) {
            "documents" -> extension in documentExtensions || mime.startsWith("text/") ||
                mime == "application/pdf" || mime.contains("word") || mime.contains("spreadsheet") ||
                mime.contains("presentation") || mime.contains("opendocument") || mime == "application/rtf" ||
                mime == "application/epub+zip"
            "videos" -> extension in videoExtensions || mime.startsWith("video/")
            "images" -> extension in imageExtensions || mime.startsWith("image/")
            "audio" -> extension in audioExtensions || mime.startsWith("audio/")
            else -> false
        }
    }

    fun label(category: String): String = when (category) {
        "documents" -> "Documents"
        "videos" -> "Videos"
        "images" -> "Images"
        "audio" -> "Audio"
        else -> "Files"
    }
}