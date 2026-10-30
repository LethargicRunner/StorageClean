package fr.lethargicrunner.storageclean

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File
import kotlin.random.Random
import org.junit.Test

class FileScannerTest {

    @Test
    fun detecteDoublonsEtStatsParType() {
        val dir = createTempDir(prefix = "storageclean_test")
        try {
            val content = "le meme document".repeat(50)
            dir.resolve("a/doc1.pdf").apply { parentFile.mkdirs() }.writeText(content)
            dir.resolve("b/copie.pdf").apply { parentFile.mkdirs() }.writeText(content)
            dir.resolve("c/autre.pdf").apply { parentFile.mkdirs() }
                .writeText(content + Random.nextInt().toString())
            dir.resolve("a/image.jpg").apply { parentFile.mkdirs() }.writeText("photo")

            val report = FileScanner.scan(
                listOf(dir.absolutePath),
                setOf("pdf", "jpg"),
            )

            assertEquals(4, report.scanned)
            assertEquals(1, report.groups.size)
            assertEquals(content.length.toLong(), report.groups.first().gain)

            val pdfStat = report.typeStats.first { it.category == "PDF" }
            assertEquals(3, pdfStat.count)
            val imgStat = report.typeStats.first { it.category == "Images" }
            assertEquals(1, imgStat.count)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun filtreParExtension() {
        val dir = createTempDir(prefix = "storageclean_test")
        try {
            dir.resolve("note.txt").writeText("identique")
            dir.resolve("copie.txt").writeText("identique")
            dir.resolve("image.png").writeText("identique")

            val report = FileScanner.scan(listOf(dir.absolutePath), setOf("png"))
            assertEquals(1, report.scanned)
            assertTrue(report.groups.isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }
}
