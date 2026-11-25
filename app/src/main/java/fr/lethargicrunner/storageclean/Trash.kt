package fr.lethargicrunner.storageclean

import android.content.Context
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Nom du dossier corbeille, a la racine de chaque volume. Exclu des scans. */
const val TRASH_DIR_NAME = ".StorageCleanTrash"

data class TrashItem(
    val trashPath: String,
    val originalPath: String,
    val size: Long,
    /** Date de mise a la corbeille (ms epoch), 0 pour les anciens elements sans date. */
    val trashedAt: Long = 0L,
)

/**
 * Corbeille maison, independante d'Android (testable en JVM).
 *
 * Les fichiers supprimes sont deplaces dans un dossier .StorageCleanTrash a la racine du
 * meme volume (deplacement instantane, aucune copie entre memoires), avec un index JSON
 * pour la restauration.
 *
 * Regle d'or : un fichier ne doit jamais se retrouver dans la corbeille sans etre dans
 * l'index. Si l'index ne peut pas etre ecrit, le deplacement est annule.
 */
class TrashStore(
    private val indexFile: File,
    private val trashDirFor: (String) -> File,
    private val now: () -> Long = { System.currentTimeMillis() },
) {

    fun load(): MutableList<TrashItem> {
        if (!indexFile.exists()) return mutableListOf()
        return try {
            val arr = JSONArray(indexFile.readText())
            val items = mutableListOf<TrashItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                items.add(
                    TrashItem(
                        o.getString("trash"),
                        o.getString("original"),
                        o.getLong("size"),
                        o.optLong("date", 0L),
                    ),
                )
            }
            items
        } catch (_: Exception) {
            // Index illisible : on le met de cote au lieu de l'ecraser.
            try {
                indexFile.renameTo(File(indexFile.parentFile, indexFile.name + ".corrompu-" + now()))
            } catch (_: Exception) {
            }
            mutableListOf()
        }
    }

    fun count(): Int = load().size

    private fun save(items: List<TrashItem>): Boolean {
        return try {
            val arr = JSONArray()
            items.forEach { t ->
                arr.put(
                    JSONObject()
                        .put("trash", t.trashPath)
                        .put("original", t.originalPath)
                        .put("size", t.size)
                        .put("date", t.trashedAt),
                )
            }
            // Ecriture atomique : fichier temporaire puis renommage.
            val tmp = File(indexFile.parentFile, indexFile.name + ".tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(indexFile)) {
                indexFile.writeText(arr.toString())
                tmp.delete()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun removeFromIndex(trashPath: String) {
        val items = load()
        items.removeAll { it.trashPath == trashPath }
        save(items)
    }

    /** Deplace un fichier vers la corbeille de son volume. Retourne null si echec. */
    fun move(path: String): TrashItem? {
        return try {
            val src = File(path)
            if (!src.isFile) return null
            // Jamais de fichier deja dans une corbeille.
            if (path.split('/').contains(TRASH_DIR_NAME)) return null
            val root = trashDirFor(path)
            if (!root.exists() && !root.mkdirs()) return null
            var dst = File(root, src.name)
            var n = 1
            while (dst.exists()) {
                dst = File(root, src.name + "." + n)
                n++
            }
            val size = src.length()
            val ok = if (src.renameTo(dst)) true else copyThenDelete(src, dst)
            if (!ok) return null
            val item = TrashItem(dst.absolutePath, src.absolutePath, size, now())
            val items = load()
            items.add(item)
            if (!save(items)) {
                // Index non ecrit : on remet le fichier a sa place plutot que de le perdre.
                if (!dst.renameTo(src)) {
                    try {
                        dst.copyTo(src, overwrite = false)
                        dst.delete()
                    } catch (_: Exception) {
                    }
                }
                return null
            }
            item
        } catch (_: Exception) {
            null
        }
    }

    private fun copyThenDelete(src: File, dst: File): Boolean {
        return try {
            src.copyTo(dst, overwrite = false)
            if (dst.length() != src.length()) {
                dst.delete()
                return false
            }
            if (!src.delete()) {
                dst.delete()
                return false
            }
            true
        } catch (_: Exception) {
            dst.delete()
            false
        }
    }

    /** Restaure un fichier a son emplacement d'origine (suffixe si l'emplacement est deja pris). */
    fun restore(item: TrashItem): Boolean {
        return try {
            val src = File(item.trashPath)
            if (!src.exists()) {
                removeFromIndex(item.trashPath)
                return false
            }
            val original = File(item.originalPath)
            var dst = original
            var n = 1
            while (dst.exists()) {
                val base = original.nameWithoutExtension
                val ext = original.extension
                dst = File(original.parentFile, base + " (" + n + ")" + if (ext.isEmpty()) "" else "." + ext)
                n++
            }
            dst.parentFile?.mkdirs()
            if (!src.renameTo(dst)) return false
            removeFromIndex(item.trashPath)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Suppression definitive d'un element de la corbeille.
     * L'element reste dans l'index si le fichier n'a pas pu etre supprime.
     */
    fun deleteForever(item: TrashItem): Boolean {
        val f = File(item.trashPath)
        val ok = !f.exists() || f.delete()
        if (ok) removeFromIndex(item.trashPath)
        return ok
    }

    /** Vide toute la corbeille. Retourne le nombre de fichiers definitivement supprimes. */
    fun empty(): Int {
        var deleted = 0
        load().forEach { item ->
            if (deleteForever(item)) deleted++
        }
        return deleted
    }
}

/** Facade Android : corbeille de l'application. */
object Trash {

    private const val INDEX_FILE = "trash_index.json"

    private fun volumeRoot(path: String): String {
        val parts = path.split('/')
        return if (parts.size >= 4 && parts[1] == "storage") {
            "/" + parts[2] + "/" + parts[3]
        } else {
            Environment.getExternalStorageDirectory().absolutePath
        }
    }

    private fun store(ctx: Context) = TrashStore(
        File(ctx.filesDir, INDEX_FILE),
        { path -> File(volumeRoot(path), TRASH_DIR_NAME) },
    )

    fun load(ctx: Context): MutableList<TrashItem> = store(ctx).load()

    fun count(ctx: Context): Int = store(ctx).count()

    fun move(ctx: Context, path: String): TrashItem? = store(ctx).move(path)

    fun restore(ctx: Context, item: TrashItem): Boolean = store(ctx).restore(item)

    fun deleteForever(ctx: Context, item: TrashItem): Boolean = store(ctx).deleteForever(item)

    fun empty(ctx: Context): Int = store(ctx).empty()
}
