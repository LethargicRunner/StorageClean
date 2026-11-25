package fr.lethargicrunner.storageclean

data class Category(val name: String, val extensions: Set<String>)

/**
 * Categories de fichiers proposées à l'utilisateur.
 * L'audio est présent mais non coche par defaut : les doublons MP3 sont
 * deja traites par DupScan.
 */
val CATEGORIES = listOf(
    Category("PDF", setOf("pdf")),
    Category("Images", setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "svg")),
    Category("Videos", setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "m4v", "flv")),
    Category("Documents", setOf("doc", "docx", "odt", "txt", "rtf", "xls", "xlsx", "ppt", "pptx", "csv")),
    Category("Archives", setOf("zip", "rar", "7z", "tar", "gz", "bz2")),
    Category("Audio", setOf("mp3", "m4a", "flac", "wav", "ogg", "opus", "aac")),
)

private val EXT_TO_CATEGORY = CATEGORIES
    .flatMap { c -> c.extensions.map { it to c.name } }
    .toMap()

/** Renvoie la categorie d'une extension, ou null si inconnue. */
fun categoryOf(ext: String): String? = EXT_TO_CATEGORY[ext.lowercase()]
