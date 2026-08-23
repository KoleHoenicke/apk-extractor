package com.kolehoenicke.apkextractor

import org.junit.Assert.assertEquals
import org.junit.Test

class ExtractionSnapshotTest {
    @Test
    fun overallProgressIsWeightedByApkBytes() {
        val snapshot = ExtractionSnapshot(
            apps = listOf(
                ExtractionAppProgress("small", "Small", 100, 1f),
                ExtractionAppProgress("large", "Large", 900, 0f),
            ),
        )

        assertEquals(0.1f, snapshot.overallProgress, 0.0001f)
    }

    @Test
    fun progressFallsBackToAverageWhenSizesAreUnavailable() {
        val snapshot = ExtractionSnapshot(
            apps = listOf(
                ExtractionAppProgress("one", "One", 0, 0.25f),
                ExtractionAppProgress("two", "Two", 0, 0.75f),
            ),
        )

        assertEquals(0.5f, snapshot.overallProgress, 0.0001f)
    }
}
