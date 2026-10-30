package fr.lethargicrunner.storageclean

import java.io.File
import java.security.MessageDigest

data class FileInfo(
    val path: String,
    val size: Long,
    val mtime: Long,
    val category: String?,
) {
    var digest: String? = null
}

data class DupGroup(val files: List<FileInfo>) {
    val size: Long get() = files.first().size
    val gain: Long get() = size * (files.size - 1)
    val oldest: FileInfo get() = files.minBy { it.mtime }
}

data class TypeStat(val category: String, val count: Int, val bytes: Long)

data class ScanReport(
    val scanned: Int,
    val totalBytes: Long,
    val groups: List<DupGroup>,
    val typeStats: List<TypeStat>,
) {
    val redundant: Int get() = groups.sumOf { it.files.size - 1 }
    val spaceGain: Long get() = groups.sumOf { it.gain }
}

data class ScanProgress(val phase: String, val done: Int, val total: Int)

/**
 * Scanner de stockage, en lecture seule stricte :
 *  1. inventaire des fichiers par type ;
 *  2. regroupement par taille ;
 *  3. hash SHA-256 des candidats uniquement ;
 *  4. doublons tries par espace recuperable decroissant.
 *
 * total < 0 dans ScanProgress signifie "en cours, total inconnu".
 */
object FileScanner {

    private const val CHUNK = 1024 * 1024

    fun scan(
        roots: List<String>,
        extensions: Set<String>,
        minSize: Long = 1,
        onProgress: (ScanProgress) -> Unit = {},
    ): ScanReport {
        val files = ArrayList<FileInfo>()
        var count = 0
        for (root in roots) {
            val dir = File(root)
            if (!dir.isDirectory) continue
            dir.walkTopDown()
                .onFail { _, _ -> } // repertoires illisibles (ex : Android/data) : on ignore
                .forEach { f ->
                    try {
                        if (!f.isFile) return@forEach
                        if (f.extension.lowercase() !in extensions) return@forEach
                        val size = f.length()
                        if (size < minSize) return@forEach
                        files.add(
                            FileInfo(
                                f.absolutePath,
                                size,
                                f.lastModified(),
                                categoryOf(f.extension),
                            )
                        )
                        if (++count % 500 == 0) {
                            onProgress(ScanProgress("inventaire", count, -1))
                        }
                    } catch (_: SecurityException) {
                    }
                }
        }
        val totalBytes = files.sumOf { it.size }
        onProgress(ScanProgress("inventaire", files.size, files.size))

        val bySize = files.groupBy { it.size }.filterValues { it.size > 1 }
        val candidates = bySize.values.flatten()
        onProgress(ScanProgress("candidats", 0, candidates.size))

        val byDigest = HashMap<Pair<Long, String>, MutableList<FileInfo>>()
        candidates.forEachIndexed { i, fi ->
            val digest = sha256(File(fi.path))
            if (digest != null) {
                fi.digest = digest
                byDigest.getOrPut(fi.size to digest) { mutableListOf() }.add(fi)
            }
            if ((i + 1) % 10 == 0 || i + 1 == candidates.size) {
                onProgress(ScanProgress("hachage", i + 1, candidates.size))
            }
        }

        val groups = byDigest.values
            .filter { it.size > 1 }
            .map { DupGroup(it) }
            .sortedByDescending { it.gain }

        val typeStats = files
            .groupBy { it.category ?: "Autres" }
            .map { (cat, list) -> TypeStat(cat, list.size, list.sumOf { it.size }) }
            .sortedByDescending { it.bytes }

        return ScanReport(files.size, totalBytes, groups, typeStats)
    }

    private fun sha256(file: File): String? {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(CHUNK)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }
}
