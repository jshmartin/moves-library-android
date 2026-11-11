package com.example.myapplication.ui.backup

import com.example.myapplication.data.backup.BackupSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class DescribeBackupTest {
    @Test
    fun listsEverythingWithPlurals() {
        assertEquals(
            "25 moves, 3 sets, 2 practices, 10 logged sessions and 4 videos",
            describeBackup(BackupSummary(moves = 25, sets = 3, practices = 2, sessions = 10, videos = 4)),
        )
    }

    @Test
    fun usesSingularForOneAndPluralForNone() {
        assertEquals(
            "1 move, 0 sets, 1 practice, 1 logged session and 0 videos",
            describeBackup(BackupSummary(moves = 1, sets = 0, practices = 1, sessions = 1, videos = 0)),
        )
    }
}
