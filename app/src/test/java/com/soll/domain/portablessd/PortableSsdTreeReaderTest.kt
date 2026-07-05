package com.soll.domain.portablessd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortableSsdTreeReaderTest {
    @Test
    fun `reads portable SSD root with wiki daily and tasks`() {
        val root = dir(
            "SSD",
            dir(
                "Projects",
                dir(
                    "Soll",
                    dir("server", file(".env", "TOKEN=secret")),
                    dir(
                        "Soll",
                        dir(
                            "wiki",
                            file("task-board.md", "# Task Board\n\n| A | today | SSD task |"),
                            file("soll-project.md", "# Soll Project\n\nKnowledge base"),
                        ),
                        dir("daily", file("2026-06-30.md", "# Daily\n\nSSD connected")),
                        dir(
                            ".soll",
                            file(
                                "tasks.json",
                                """[{"id":"t1","title":"Read SSD wiki","status":"today","project_name":"Soll app"}]""",
                            ),
                        ),
                    ),
                ),
            ),
        )

        val snapshot = PortableSsdTreeReader.read(root)

        assertEquals(PortableSsdSnapshotStatus.READY, snapshot.status)
        assertEquals("Projects/Soll", snapshot.projectPath)
        assertEquals("Projects/Soll/Soll", snapshot.vaultPath)
        assertEquals(listOf("Soll Project", "Task Board"), snapshot.wiki.map { it.title }.sorted())
        assertEquals(listOf("Daily"), snapshot.daily.map { it.title })
        assertTrue(snapshot.tasks.any { it.title == "Read SSD wiki" })
        assertFalse((snapshot.wiki + snapshot.daily + snapshot.tasks).any { it.relativePath.contains(".env") })
    }

    @Test
    fun `accepts project root and vault root selections`() {
        val vault = dir("Soll", dir("wiki", file("note.md", "# Note\nbody")))
        val project = dir("SollProject", dir("server"), dir("desktop"), vault)

        val fromProject = PortableSsdTreeReader.read(project)
        val fromVault = PortableSsdTreeReader.read(vault)

        assertEquals(PortableSsdSnapshotStatus.READY, fromProject.status)
        assertEquals("SollProject/Soll", fromProject.vaultPath)
        assertEquals(PortableSsdSnapshotStatus.READY, fromVault.status)
        assertEquals("Soll", fromVault.vaultPath)
    }

    @Test
    fun `invalid tree reports clear status`() {
        val snapshot = PortableSsdTreeReader.read(dir("USB", dir("Movies", file("video.txt", "x"))))

        assertEquals(PortableSsdSnapshotStatus.INVALID, snapshot.status)
        assertTrue(snapshot.message.contains("Не найден portable Soll vault"))
    }

    @Test
    fun `tasks fallback includes task board when json is missing`() {
        val root = dir(
            "Soll",
            dir("wiki", file("task-board.md", "# Task Board\n\nmanual task")),
        )

        val snapshot = PortableSsdTreeReader.read(root)

        assertEquals(PortableSsdSnapshotStatus.READY, snapshot.status)
        assertEquals(1, snapshot.tasks.size)
        assertEquals("Task Board", snapshot.tasks.first().title)
    }

    @Test
    fun `entry cache file name is stable and safe`() {
        val entry = PortableSsdEntry(
            id = "wiki:../unsafe/Новая заметка.md#fragment",
            title = "Новая заметка",
            relativePath = "../unsafe/Новая заметка.md#fragment",
            section = PortableSsdSection.WIKI,
        )

        val first = PortableSsdEntryCache.fileNameFor(entry)
        val second = PortableSsdEntryCache.fileNameFor(entry)

        assertEquals(first, second)
        assertTrue(first.startsWith("wiki_"))
        assertFalse(first.contains(".."))
        assertFalse(first.contains("/"))
        assertFalse(first.contains("\\"))
    }

    private fun dir(name: String, vararg children: FakeNode): FakeNode =
        FakeNode(name = name, directory = true, children = children.toList())

    private fun file(name: String, text: String): FakeNode =
        FakeNode(name = name, directory = false, text = text)

    private data class FakeNode(
        override val name: String,
        val directory: Boolean,
        val text: String = "",
        val children: List<FakeNode> = emptyList(),
    ) : PortableSsdNode {
        override val isDirectory: Boolean = directory
        override val isFile: Boolean = !directory
        override val sizeBytes: Long = text.length.toLong()
        override val updatedAt: Long = 0L
        override fun children(): List<PortableSsdNode> = children
        override fun readText(maxBytes: Int): String = text.take(maxBytes)
    }
}
