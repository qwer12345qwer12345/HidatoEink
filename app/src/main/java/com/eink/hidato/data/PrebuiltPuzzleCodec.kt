package com.eink.hidato.data

import com.eink.hidato.domain.Difficulty
import com.eink.hidato.domain.HamiltonianPaths
import com.eink.hidato.domain.Puzzle

/** One tab-separated, reproducible offline puzzle: rows, cols, difficulty, path, given numbers. */
object PrebuiltPuzzleCodec {
    fun parse(line: String): Pair<Difficulty, Puzzle>? = runCatching {
        val parts = line.split('\t')
        require(parts.size == 5)
        val rows = parts[0].toInt()
        val cols = parts[1].toInt()
        require(rows in 4..15 && cols in 4..15)
        val difficulty = Difficulty.valueOf(parts[2])
        val path = parts[3].split(',').map(String::toInt).toIntArray()
        val givenNumbers = parts[4].split(',').map(String::toInt)
        val total = rows * cols
        require(HamiltonianPaths.isValid(path, rows, cols))
        require(givenNumbers.size == givenNumbers.distinct().size)
        require(givenNumbers.all { it in 1..total })
        require(1 in givenNumbers && total in givenNumbers)
        val clues = BooleanArray(total + 1)
        for (number in givenNumbers) clues[number] = true
        difficulty to Puzzle(rows, cols, path, clues)
    }.getOrNull()

    fun encode(difficulty: Difficulty, puzzle: Puzzle): String =
        listOf(
            puzzle.rows.toString(),
            puzzle.cols.toString(),
            difficulty.name,
            puzzle.path.joinToString(","),
            (1..puzzle.total).filter(puzzle::isClueVisible).joinToString(","),
        ).joinToString("\t")
}
