package com.myframe.tv.core

import kotlin.random.Random

/**
 * Pure playback state machine: which item is showing and what comes next.
 * It knows nothing about Android, timers or images, which keeps it unit-testable.
 */
class SlideshowEngine<T>(
    items: List<T>,
    val order: PlayOrder = PlayOrder.SEQUENTIAL,
    val repeat: RepeatMode = RepeatMode.ALL,
    private val random: Random = Random.Default,
    start: T? = null,
) {
    private var sequence: List<T> = arrange(items.distinct(), start)

    var position: Int = when {
        sequence.isEmpty() -> -1
        start != null && order == PlayOrder.SEQUENTIAL -> sequence.indexOf(start).coerceAtLeast(0)
        else -> 0
    }
        private set

    /** True once a "play once" show has run past its last item. */
    var finished: Boolean = false
        private set

    val size: Int get() = sequence.size
    val isEmpty: Boolean get() = sequence.isEmpty()
    val current: T? get() = sequence.getOrNull(position)
    val items: List<T> get() = sequence

    private fun arrange(distinct: List<T>, first: T?): List<T> {
        if (order != PlayOrder.SHUFFLE || distinct.size < 2) return distinct
        val shuffled = distinct.shuffled(random).toMutableList()
        if (first != null && shuffled.remove(first)) shuffled.add(0, first)
        return shuffled
    }

    /**
     * Move forward. [auto] is true when the timer fires rather than the remote:
     * only the timer honours "repeat one" and "play once".
     * Returns true when a different item is now current.
     */
    fun next(auto: Boolean = false): Boolean {
        if (sequence.isEmpty()) return false
        if (auto && repeat == RepeatMode.ONE) return false
        if (position < sequence.lastIndex) {
            position++
            finished = false
            return true
        }
        if (repeat == RepeatMode.OFF) {
            if (auto) finished = true
            return false
        }
        if (sequence.size == 1) return false
        if (order == PlayOrder.SHUFFLE) {
            val last = sequence[position]
            var reshuffled = sequence.shuffled(random)
            // Never show the same picture twice in a row across a reshuffle.
            if (reshuffled.first() == last) reshuffled = reshuffled.drop(1) + last
            sequence = reshuffled
        }
        position = 0
        finished = false
        return true
    }

    fun previous(): Boolean {
        if (sequence.isEmpty()) return false
        if (position > 0) {
            position--
            finished = false
            return true
        }
        if (repeat == RepeatMode.OFF || sequence.size == 1) return false
        position = sequence.lastIndex
        finished = false
        return true
    }

    /** Best guess at the following item, used for preloading. Null when the show would stop. */
    fun peekNext(): T? = when {
        sequence.isEmpty() -> null
        position < sequence.lastIndex -> sequence[position + 1]
        repeat == RepeatMode.OFF || sequence.size == 1 -> null
        else -> sequence.first()
    }

    fun jumpTo(item: T): Boolean {
        val i = sequence.indexOf(item)
        if (i < 0) return false
        position = i
        finished = false
        return true
    }

    /** Swap in a changed library while keeping the current item on screen when it still exists. */
    fun update(newItems: List<T>) {
        val cur = current
        val distinct = newItems.distinct()
        sequence = if (order == PlayOrder.SHUFFLE) {
            val incoming = distinct.toSet()
            val kept = sequence.filter { it in incoming }
            val keptSet = kept.toSet()
            kept + distinct.filter { it !in keptSet }.shuffled(random)
        } else {
            distinct
        }
        position = when {
            sequence.isEmpty() -> -1
            cur != null && sequence.contains(cur) -> sequence.indexOf(cur)
            else -> position.coerceIn(0, sequence.lastIndex)
        }
    }
}

/** Where a playlist's items come from. Implemented by the repository and by test fakes. */
interface MediaSource<T> {
    suspend fun art(favOnly: Boolean): List<T>
    suspend fun photos(favOnly: Boolean): List<T>
    suspend fun collection(id: Long): List<T>
}

object PlaylistBuilder {
    /** Concatenates the sources in order, dropping repeats so an item in two sources plays once. */
    suspend fun <T> build(sources: List<Source>, from: MediaSource<T>, idOf: (T) -> Long): List<T> {
        val seen = HashSet<Long>()
        val out = ArrayList<T>()
        for (s in sources) {
            val part = when (s.type) {
                SourceType.ALL_ART -> from.art(false)
                SourceType.FAV_ART -> from.art(true)
                SourceType.ALL_PHOTOS -> from.photos(false)
                SourceType.FAV_PHOTOS -> from.photos(true)
                SourceType.COLLECTION -> from.collection(s.collectionId)
            }
            for (m in part) if (seen.add(idOf(m))) out += m
        }
        return out
    }
}
