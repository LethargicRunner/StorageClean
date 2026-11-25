package fr.lethargicrunner.storageclean

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanupSafetyTest {

    private fun fi(path: String, mtime: Long, size: Long = 10) = FileInfo(path, size, mtime, "PDF")
    private val exists: (String) -> Boolean = { true }

    private val a = fi("/s/a.pdf", 100)
    private val b = fi("/s/b.pdf", 200)
    private val c = fi("/s/c.pdf", 300)
    private val group = DupGroup(listOf(b, a, c))

    @Test
    fun gardienEstLePlusAncien() {
        assertEquals(a.path, CleanupSafety.keeper(group).path)
    }

    @Test
    fun gardienADegalitePrendLePremierParChemin() {
        val x = fi("/s/z.pdf", 100)
        val y = fi("/s/m.pdf", 100)
        assertEquals("/s/m.pdf", CleanupSafety.keeper(DupGroup(listOf(x, y))).path)
    }

    @Test
    fun copiesACocherExcluentLeGardien() {
        assertEquals(setOf(b.path, c.path), CleanupSafety.copiesToSelect(group).map { it.path }.toSet())
    }

    @Test
    fun toutSelectionneEstBloque() {
        val blocked = CleanupSafety.blockedGroups(setOf(a.path, b.path, c.path), listOf(group), exists)
        assertEquals(1, blocked.size)
    }

    @Test
    fun selectionPartielleEstAutorisee() {
        assertTrue(CleanupSafety.blockedGroups(setOf(b.path, c.path), listOf(group), exists).isEmpty())
    }

    @Test
    fun groupeNonTouchePasBloque() {
        assertTrue(CleanupSafety.blockedGroups(setOf("/autre.pdf"), listOf(group), exists).isEmpty())
    }

    @Test
    fun exemplaireRestantDisparuDuDisqueBloque() {
        // a reste dans la selection de depart, mais a n'existe plus : b et c supprimes = plus rien
        val gone: (String) -> Boolean = { it != a.path }
        val blocked = CleanupSafety.blockedGroups(setOf(b.path, c.path), listOf(group), gone)
        assertEquals(1, blocked.size)
    }

    @Test
    fun exemplaireRestantEnCorbeilleNeCompteQuePourRien() {
        val trashed = fi("/s/.StorageCleanTrash/a.pdf", 50)
        val g = DupGroup(listOf(trashed, b))
        val blocked = CleanupSafety.blockedGroups(setOf(b.path), listOf(g), exists)
        assertEquals(1, blocked.size)
    }

    @Test
    fun planCalculeEspaceEtDedoublonneLesChemins() {
        val plan = CleanupSafety.plan(listOf(b, c, c), listOf(group), exists)
        assertEquals(2, plan.files.size)
        assertEquals(20L, plan.bytes)
        assertTrue(plan.allowed)
    }

    @Test
    fun planBloqueSiToutLeGroupeEstChoisi() {
        val plan = CleanupSafety.plan(listOf(a, b, c), listOf(group), exists)
        assertFalse(plan.allowed)
        assertEquals(setOf(a.path), CleanupSafety.pathsToUncheck(plan.blocked))
    }

    @Test
    fun planVideNEstPasAutorise() {
        assertFalse(CleanupSafety.plan(emptyList(), listOf(group), exists).allowed)
    }
}
