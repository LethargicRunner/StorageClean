package fr.lethargicrunner.storageclean

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class TrashStoreTest {

    private lateinit var base: File
    private lateinit var data: File
    private lateinit var trashRoot: File
    private lateinit var store: TrashStore

    @Before
    fun setUp() {
        base = createTempDir(prefix = "trash_test")
        data = File(base, "data").apply { mkdirs() }
        trashRoot = File(base, TRASH_DIR_NAME)
        store = TrashStore(File(data, "index.json"), { trashRoot }, { 1_000L })
    }

    @After
    fun tearDown() {
        base.deleteRecursively()
    }

    private fun file(rel: String, content: String = "contenu"): File =
        File(base, rel).apply { parentFile.mkdirs(); writeText(content) }

    @Test
    fun deplaceUnFichierEtEnregistreIndex() {
        val f = file("docs/a.pdf", "12345")
        val item = store.move(f.absolutePath)
        assertNotNull(item)
        assertFalse(f.exists())
        assertTrue(File(item!!.trashPath).exists())
        assertEquals(f.absolutePath, item.originalPath)
        assertEquals(5L, item.size)
        assertEquals(1_000L, item.trashedAt)
        assertEquals(1, store.count())
    }

    @Test
    fun fichierInexistantNeChangeRien() {
        assertNull(store.move(File(base, "absent.pdf").absolutePath))
        assertEquals(0, store.count())
    }

    @Test
    fun refuseUnFichierDejaDansLaCorbeille() {
        val f = file("$TRASH_DIR_NAME/deja.pdf")
        assertNull(store.move(f.absolutePath))
        assertTrue(f.exists())
    }

    @Test
    fun deuxFichiersDeMemeNomSontConserves() {
        val a = file("x/note.txt", "A")
        val b = file("y/note.txt", "B")
        val ia = store.move(a.absolutePath)!!
        val ib = store.move(b.absolutePath)!!
        assertNotEquals(ia.trashPath, ib.trashPath)
        assertEquals("A", File(ia.trashPath).readText())
        assertEquals("B", File(ib.trashPath).readText())
        assertTrue(store.restore(ia))
        assertTrue(store.restore(ib))
        assertEquals("A", a.readText())
        assertEquals("B", b.readText())
    }

    @Test
    fun restaureAEmplacementDorigine() {
        val f = file("docs/a.pdf", "abc")
        val item = store.move(f.absolutePath)!!
        assertTrue(store.restore(item))
        assertEquals("abc", f.readText())
        assertEquals(0, store.count())
    }

    @Test
    fun restaureAvecSuffixeSiEmplacementPris() {
        val f = file("docs/a.pdf", "ancien")
        val item = store.move(f.absolutePath)!!
        f.writeText("nouveau") // quelqu'un a recree le fichier entre-temps
        assertTrue(store.restore(item))
        assertEquals("nouveau", f.readText())
        assertEquals("ancien", File(base, "docs/a (1).pdf").readText())
    }

    @Test
    fun restaurerUnFichierDisparuNettoieLIndex() {
        val f = file("docs/a.pdf")
        val item = store.move(f.absolutePath)!!
        File(item.trashPath).delete()
        assertFalse(store.restore(item))
        assertEquals(0, store.count())
    }

    @Test
    fun indexCorrompuNePerdPasLeFichierDindex() {
        val idx = File(data, "index.json")
        idx.writeText("{pas du json")
        assertTrue(store.load().isEmpty())
        assertFalse(idx.exists())
        assertTrue(data.listFiles()!!.any { it.name.startsWith("index.json.corrompu-") })
    }

    @Test
    fun lIndexSurvitAuRechargement() {
        val f = file("docs/a.pdf")
        val item = store.move(f.absolutePath)!!
        val reloaded = TrashStore(File(data, "index.json"), { trashRoot }).load()
        assertEquals(listOf(item), reloaded)
    }

    @Test
    fun ancienIndexSansDateResteLisible() {
        val f = file("docs/a.pdf")
        val trashed = File(trashRoot, "a.pdf").apply { parentFile.mkdirs(); writeText("x") }
        File(data, "index.json").writeText(
            """[{"trash":"${trashed.absolutePath}","original":"${f.absolutePath}","size":1}]""",
        )
        val items = store.load()
        assertEquals(1, items.size)
        assertEquals(0L, items[0].trashedAt)
    }

    @Test
    fun supprimerDefinitivementRetireFichierEtIndex() {
        val item = store.move(file("docs/a.pdf").absolutePath)!!
        assertTrue(store.deleteForever(item))
        assertFalse(File(item.trashPath).exists())
        assertEquals(0, store.count())
    }

    @Test
    fun viderCompteLesFichiersSupprimes() {
        store.move(file("a/1.txt").absolutePath)
        store.move(file("a/2.txt").absolutePath)
        store.move(file("a/3.txt").absolutePath)
        assertEquals(3, store.empty())
        assertEquals(0, store.count())
        assertTrue(trashRoot.listFiles()!!.isEmpty())
    }

    @Test
    fun siLIndexNEstPasEcritLeFichierResteEnPlace() {
        // Le dossier de l'index n'existe pas : l'ecriture de l'index echoue.
        val broken = TrashStore(File(base, "inexistant/index.json"), { trashRoot })
        val f = file("docs/a.pdf", "important")
        assertNull(broken.move(f.absolutePath))
        assertEquals("important", f.readText())
        assertTrue(trashRoot.listFiles().orEmpty().isEmpty())
    }
}
