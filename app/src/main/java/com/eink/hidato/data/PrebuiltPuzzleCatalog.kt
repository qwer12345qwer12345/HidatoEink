package com.eink.hidato.data

import android.content.Context
import com.eink.hidato.domain.Difficulty
import com.eink.hidato.domain.Puzzle
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/** Offline-certified content first; the runtime generator is the verified fallback. */
@Singleton
class PrebuiltPuzzleCatalog @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val puzzles: List<Pair<Difficulty, Puzzle>> by lazy {
        try {
            context.assets.open("unique-puzzles.tsv").bufferedReader().use { reader ->
                reader.lineSequence()
                    .filter { it.isNotBlank() && !it.startsWith("#") }
                    .mapNotNull(PrebuiltPuzzleCodec::parse)
                    .toList()
            }
        } catch (_: IOException) {
            emptyList()
        }
    }

    fun choose(rows: Int, cols: Int, difficulty: Difficulty, random: Random = Random.Default): Puzzle? {
        val options = puzzles.filter { (level, puzzle) ->
            level == difficulty && puzzle.rows == rows && puzzle.cols == cols
        }
        if (options.isEmpty()) return null
        return options.random(random).second
    }
}
