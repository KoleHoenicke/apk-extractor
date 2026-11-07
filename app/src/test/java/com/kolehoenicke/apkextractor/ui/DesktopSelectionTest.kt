package com.kolehoenicke.apkextractor.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopSelectionTest {
    @Test fun rangeUsesTheVisibleSortOrderInEitherDirection() {
        val visible = listOf("d", "b", "a", "c")
        assertEquals(setOf("b", "a", "c"), selectionRange(visible, "b", "c"))
        assertEquals(setOf("b", "a", "c"), selectionRange(visible, "c", "b"))
    }

    @Test fun missingOrFilteredAnchorSelectsOnlyTheTarget() {
        assertEquals(setOf("b"), selectionRange(listOf("a", "b"), null, "b"))
        assertEquals(setOf("b"), selectionRange(listOf("a", "b"), "hidden", "b"))
    }

    @Test fun removedTargetDoesNotSelectUnrelatedApps() {
        assertEquals(emptySet<String>(), selectionRange(listOf("a", "b"), "a", "removed"))
    }
}
