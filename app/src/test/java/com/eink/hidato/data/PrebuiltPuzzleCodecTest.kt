package com.eink.hidato.data

import com.eink.hidato.domain.Difficulty
import com.eink.hidato.domain.HamiltonianPaths
import com.eink.hidato.domain.Puzzle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrebuiltPuzzleCodecTest {
    @Test
    fun `valid catalog entry round trips without changing the answer`() {
        val path = intArrayOf(0, 1, 2, 3, 7, 6, 5, 4, 8, 9, 10, 11, 15, 14, 13, 12)
        val clues = BooleanArray(17) { it == 1 || it == 7 || it == 16 }
        val encoded = PrebuiltPuzzleCodec.encode(Difficulty.HARD, Puzzle(4, 4, path, clues))
        val parsed = PrebuiltPuzzleCodec.parse(encoded)
        assertEquals(Difficulty.HARD, parsed?.first)
        assertTrue(parsed!!.second.path.contentEquals(path))
        assertTrue(parsed.second.clues.contentEquals(clues))
    }

    @Test
    fun `malformed or invalid catalog entries are discarded`() {
        assertNull(PrebuiltPuzzleCodec.parse("4\t4\tHARD\t0,1\t1,16"))
        assertNull(PrebuiltPuzzleCodec.parse("4\t4\tHARD\t0,1,2,3,7,6,5,4,8,9,10,11,15,14,13,12\t1,1,16"))
    }
}
