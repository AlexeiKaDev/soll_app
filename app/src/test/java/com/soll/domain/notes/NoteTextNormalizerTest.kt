package com.soll.domain.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteTextNormalizerTest {
    @Test
    fun `derive title uses explicit title first`() {
        assertEquals(
            "Моя идея",
            NoteTextNormalizer.deriveTitle("  Моя идея  ", "Первый текст"),
        )
    }

    @Test
    fun `derive title falls back to first content line`() {
        assertEquals(
            "План на вечер",
            NoteTextNormalizer.deriveTitle("", "\n# План на вечер\nКупить детали"),
        )
    }

    @Test
    fun `normalize tags merges comma input and inline hashtags`() {
        assertEquals(
            listOf("личное", "проект-дома", "важно", "идея"),
            NoteTextNormalizer.normalizeTags(
                "Личное, проект дома",
                "Текст #Важно и #идея",
            ),
        )
    }

    @Test
    fun `snippet compacts multiline note text`() {
        assertEquals(
            "Первая строка Вторая строка",
            NoteTextNormalizer.buildSnippet("  Первая строка\n\nВторая строка  "),
        )
    }
}
