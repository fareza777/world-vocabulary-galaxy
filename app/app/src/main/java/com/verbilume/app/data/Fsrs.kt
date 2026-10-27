package com.verbilume.app.data

import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.roundToLong

enum class FsrsRating(val value: Int) {
    AGAIN(1), HARD(2), GOOD(3), EASY(4);

    companion object {
        fun from(value: Int): FsrsRating = entries.first { it.value == value }
    }
}

enum class FsrsState { NEW, LEARNING, REVIEW, RELEARNING }

data class FsrsCard(
    val wordId: String,
    val word: String = "",
    val topic: Int = 0,
    val cefr: Int = 0,
    val due: Long,
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val elapsedDays: Int = 0,
    val scheduledDays: Int = 0,
    val learningSteps: Int = 0,
    val reps: Int = 0,
    val lapses: Int = 0,
    val state: FsrsState = FsrsState.NEW,
    val lastReview: Long? = null,
    val firstSeen: Long = System.currentTimeMillis(),
    val lastGrade: Int = 0,
) {
    val isMastered: Boolean get() = state == FsrsState.REVIEW && stability >= 21.0
}

/**
 * A faithful Kotlin port of the FSRS-6 scheduler from ts-fsrs 5.4.2 with the
 * configuration used by Word Atlas (request retention 0.9, short-term enabled,
 * 1m/10m learning steps, 10m relearning step).
 */
object Fsrs {
    val DEFAULT_W = doubleArrayOf(
        0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001,
        1.8722, 0.1666, 0.796, 1.4835, 0.0614, 0.2629, 1.6483, 0.6014,
        1.8729, 0.5425, 0.0912, 0.0658, 0.1542,
    )

    private const val S_MIN = 1e-3
    private const val S_MAX = 36500.0
    private const val MAX_INTERVAL = 36500
    private const val REQUEST_RETENTION = 0.9
    private const val ENABLE_FUZZ = true

    private val learningSteps = intArrayOf(1, 10)
    private val relearningSteps = intArrayOf(10)

    private val decay = -DEFAULT_W[20]
    private val factor = roundTo(exp((1.0 / decay) * ln(0.9)) - 1.0, 8)
    private val intervalModifier = roundTo((REQUEST_RETENTION.pow(1.0 / decay) - 1.0) / factor, 8)

    fun newCard(wordId: String, word: String, topic: Int, cefr: Int, now: Long = System.currentTimeMillis()): FsrsCard =
        FsrsCard(
            wordId = wordId,
            word = word,
            topic = topic,
            cefr = cefr,
            due = now,
            firstSeen = now,
        )

    fun retrievability(elapsedDays: Int, stability: Double): Double {
        if (stability <= 0.0) return 0.0
        return roundTo((1.0 + factor * elapsedDays / stability).pow(decay), 8)
    }

    fun preview(card: FsrsCard, now: Long): Map<FsrsRating, FsrsCard> =
        FsrsRating.entries.associateWith { review(card, now, it) }

    fun review(card: FsrsCard, now: Long, rating: FsrsRating): FsrsCard {
        val elapsedDays = if (card.state != FsrsState.NEW && card.lastReview != null) {
            dateDiffInDays(card.lastReview, now)
        } else {
            0
        }
        val seed = "${now}_${card.reps + 1}_${card.difficulty * card.stability}"
        val base = card.copy(
            reps = card.reps + 1,
            lastReview = now,
            elapsedDays = elapsedDays,
            lastGrade = rating.value,
        )
        return when (card.state) {
            FsrsState.NEW -> scheduleNew(base, rating, seed, now)
            FsrsState.LEARNING, FsrsState.RELEARNING -> scheduleLearning(base, rating, seed, now)
            FsrsState.REVIEW -> scheduleReview(base, rating, seed, now)
        }
    }

    private fun scheduleNew(card: FsrsCard, rating: FsrsRating, seed: String, now: Long): FsrsCard {
        val memory = nextMemoryState(card.state, card.difficulty, card.stability, 0, rating, null)
        val next = card.copy(
            stability = memory.first,
            difficulty = memory.second,
            scheduledDays = 0,
            due = now,
        )
        return applyLearningSteps(next, rating, FsrsState.LEARNING, now, seed, FsrsState.NEW)
    }

    private fun scheduleLearning(card: FsrsCard, rating: FsrsRating, seed: String, now: Long): FsrsCard {
        val memory = nextMemoryState(
            card.state, card.difficulty, card.stability,
            card.elapsedDays, rating,
            retrievability(card.elapsedDays, card.stability),
        )
        val next = card.copy(stability = memory.first, difficulty = memory.second)
        return applyLearningSteps(next, rating, card.state, now, seed, card.state)
    }

    private fun scheduleReview(card: FsrsCard, rating: FsrsRating, seed: String, now: Long): FsrsCard {
        val r = retrievability(card.elapsedDays, card.stability)
        val previews = FsrsRating.entries.associateWith { grade ->
            val memory = nextMemoryState(
                card.state, card.difficulty, card.stability, card.elapsedDays, grade, r,
            )
            card.copy(stability = memory.first, difficulty = memory.second)
        }.toMutableMap()

        val again = previews.getValue(FsrsRating.AGAIN)
        val hard = previews.getValue(FsrsRating.HARD)
        val good = previews.getValue(FsrsRating.GOOD)
        val easy = previews.getValue(FsrsRating.EASY)

        var hardInterval = nextInterval(hard.stability, card.elapsedDays, seed)
        var goodInterval = nextInterval(good.stability, card.elapsedDays, seed)
        hardInterval = min(hardInterval, goodInterval)
        goodInterval = max(goodInterval, hardInterval + 1)
        val easyInterval = max(nextInterval(easy.stability, card.elapsedDays, seed), goodInterval + 1)

        previews[FsrsRating.HARD] = hard.copy(
            state = FsrsState.REVIEW,
            learningSteps = 0,
            scheduledDays = hardInterval,
            due = now + hardInterval * DAY_MS,
        )
        previews[FsrsRating.GOOD] = good.copy(
            state = FsrsState.REVIEW,
            learningSteps = 0,
            scheduledDays = goodInterval,
            due = now + goodInterval * DAY_MS,
        )
        previews[FsrsRating.EASY] = easy.copy(
            state = FsrsState.REVIEW,
            learningSteps = 0,
            scheduledDays = easyInterval,
            due = now + easyInterval * DAY_MS,
        )
        val againCard = applyLearningSteps(
            again.copy(lapses = again.lapses + 1),
            FsrsRating.AGAIN,
            FsrsState.RELEARNING,
            now,
            seed,
            FsrsState.REVIEW,
        )
        previews[FsrsRating.AGAIN] = againCard
        return previews.getValue(rating)
    }

    private fun applyLearningSteps(
        card: FsrsCard,
        rating: FsrsRating,
        toState: FsrsState,
        now: Long,
        seed: String,
        strategyState: FsrsState,
    ): FsrsCard {
        val steps = if (strategyState == FsrsState.RELEARNING || strategyState == FsrsState.REVIEW) {
            relearningSteps
        } else {
            learningSteps
        }
        val curStep = card.learningSteps
        val stepsLength = steps.size
        var minutes = 0
        var nextStep = 0

        if (stepsLength > 0 && curStep < stepsLength) {
            val first = steps[0]
            if (strategyState == FsrsState.REVIEW) {
                if (rating == FsrsRating.AGAIN) minutes = first
            } else {
                when (rating) {
                    FsrsRating.AGAIN -> {
                        minutes = first
                        nextStep = 0
                    }
                    FsrsRating.HARD -> {
                        minutes = if (stepsLength == 1) {
                            round(first * 1.5).toInt()
                        } else {
                            round((first + steps[1]) / 2.0).toInt()
                        }
                        nextStep = curStep
                    }
                    FsrsRating.GOOD -> {
                        val nextIndex = curStep + 1
                        if (nextIndex < stepsLength) {
                            minutes = steps[nextIndex]
                            nextStep = nextIndex
                        }
                    }
                    FsrsRating.EASY -> Unit
                }
            }
        }

        return if (minutes in 1..1439) {
            card.copy(
                learningSteps = nextStep,
                scheduledDays = 0,
                state = toState,
                due = now + minutes * MINUTE_MS,
                elapsedDays = card.elapsedDays,
            )
        } else {
            card.copy(
                state = FsrsState.REVIEW,
                learningSteps = 0,
                scheduledDays = nextInterval(card.stability, card.elapsedDays, seed),
                due = now + nextInterval(card.stability, card.elapsedDays, seed) * DAY_MS,
            )
        }
    }

    private fun nextMemoryState(
        state: FsrsState,
        difficulty: Double,
        stability: Double,
        t: Int,
        rating: FsrsRating,
        retrievability: Double?,
    ): Pair<Double, Double> {
        val d = difficulty
        val s = stability
        if (d == 0.0 && s == 0.0) {
            val initD = clampSigned(initDifficulty(rating), 1.0, 10.0)
            return initStability(rating) to initD
        }
        val r = retrievability ?: this.retrievability(t, s)
        var newS: Double
        if (t == 0) {
            newS = nextShortTermStability(s, rating)
        } else if (rating == FsrsRating.AGAIN) {
            val sAfterFail = nextForgetStability(d, s, r)
            val w17 = DEFAULT_W[17]
            val w18 = DEFAULT_W[18]
            val nextSMin = s / exp(w17 * w18)
            newS = clampSigned(roundTo(nextSMin, 8), S_MIN, sAfterFail)
        } else {
            newS = nextRecallStability(d, s, r, rating)
        }
        val newD = nextDifficulty(d, rating)
        return newS to newD
    }

    private fun initStability(rating: FsrsRating): Double = max(DEFAULT_W[rating.value - 1], 0.1)

    private fun initDifficulty(rating: FsrsRating): Double {
        val d = DEFAULT_W[4] - exp((rating.value - 1) * DEFAULT_W[5]) + 1.0
        return roundTo(d, 8)
    }

    private fun nextDifficulty(d: Double, rating: FsrsRating): Double {
        val deltaD = -DEFAULT_W[6] * (rating.value - 3)
        val nextD = d + linearDamping(deltaD, d)
        return clampSigned(meanReversion(initDifficulty(FsrsRating.EASY), nextD), 1.0, 10.0)
    }

    private fun linearDamping(deltaD: Double, oldD: Double): Double = roundTo(deltaD * (10.0 - oldD) / 9.0, 8)

    private fun meanReversion(init: Double, current: Double): Double =
        roundTo(DEFAULT_W[7] * init + (1.0 - DEFAULT_W[7]) * current, 8)

    private fun nextRecallStability(d: Double, s: Double, r: Double, rating: FsrsRating): Double {
        val hardPenalty = if (rating == FsrsRating.HARD) DEFAULT_W[15] else 1.0
        val easyBound = if (rating == FsrsRating.EASY) DEFAULT_W[16] else 1.0
        val next = s * (
            1.0 + exp(DEFAULT_W[8]) * (11.0 - d) * s.pow(-DEFAULT_W[9]) *
                (exp((1.0 - r) * DEFAULT_W[10]) - 1.0) * hardPenalty * easyBound
            )
        return roundTo(clampSigned(next, S_MIN, S_MAX), 8)
    }

    private fun nextForgetStability(d: Double, s: Double, r: Double): Double {
        val next = DEFAULT_W[11] * d.pow(-DEFAULT_W[12]) * ((s + 1.0).pow(DEFAULT_W[13]) - 1.0) *
            exp((1.0 - r) * DEFAULT_W[14])
        return roundTo(clampSigned(next, S_MIN, S_MAX), 8)
    }

    private fun nextShortTermStability(s: Double, rating: FsrsRating): Double {
        val sinc = s.pow(-DEFAULT_W[19]) * exp(DEFAULT_W[17] * (rating.value - 3 + DEFAULT_W[18]))
        val masked = if (rating.value >= FsrsRating.HARD.value) max(sinc, 1.0) else sinc
        return roundTo(clampSigned(s * masked, S_MIN, S_MAX), 8)
    }

    private fun nextInterval(stability: Double, elapsedDays: Int, seed: String): Int {
        val interval = min(
            max(1, (stability * intervalModifier).roundToInt()),
            MAX_INTERVAL,
        )
        return applyFuzz(interval, elapsedDays, seed)
    }

    private fun applyFuzz(interval: Int, elapsedDays: Int, seed: String): Int {
        if (!ENABLE_FUZZ || interval < 3) return interval
        val generator = Alea(seed)
        val fuzzFactor = generator.next()
        var delta = 1.0
        for ((start, end, fuzz) in fuzzRanges) {
            delta += fuzz * max(min(interval.toDouble(), end) - start, 0.0)
        }
        val bounded = min(interval, MAX_INTERVAL)
        var minIvl = max(2, round(bounded - delta).toInt())
        val maxIvl = min(round(bounded + delta).toInt(), MAX_INTERVAL)
        if (interval > elapsedDays) minIvl = max(minIvl, elapsedDays + 1)
        minIvl = min(minIvl, maxIvl)
        return floor(fuzzFactor * (maxIvl - minIvl + 1) + minIvl).toInt()
    }

    private val fuzzRanges = listOf(
        Triple(2.5, 7.0, 0.15),
        Triple(7.0, 20.0, 0.10),
        Triple(20.0, Double.POSITIVE_INFINITY, 0.05),
    )

    private fun dateDiffInDays(last: Long, now: Long): Int {
        val lastDate = java.time.Instant.ofEpochMilli(last).atZone(java.time.ZoneOffset.UTC).toLocalDate()
        val nowDate = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneOffset.UTC).toLocalDate()
        return java.time.temporal.ChronoUnit.DAYS.between(lastDate, nowDate).toInt().coerceAtLeast(0)
    }

    private fun clampSigned(value: Double, min: Double, max: Double): Double = minOf(maxOf(value, min), max)

    private fun roundTo(value: Double, decimals: Int): Double {
        val f = 10.0.pow(decimals)
        return round(value * f) / f
    }

    private const val MINUTE_MS = 60_000L
    private const val DAY_MS = 86_400_000L

    /** Alea PRNG (Johannes Baagøe) as used by ts-fsrs for interval fuzzing. */
    private class Alea(seed: String) {
        private var c = 1.0
        private var s0: Double
        private var s1: Double
        private var s2: Double
        private val mash = Mash()

        init {
            s0 = mash.next(" ")
            s1 = mash.next(" ")
            s2 = mash.next(" ")
            s0 -= mash.next(seed); if (s0 < 0) s0 += 1.0
            s1 -= mash.next(seed); if (s1 < 0) s1 += 1.0
            s2 -= mash.next(seed); if (s2 < 0) s2 += 1.0
        }

        fun next(): Double {
            val t = 2091639.0 * s0 + c * 2.3283064365386963e-10
            s0 = s1
            s1 = s2
            c = t.toLong().toDouble()
            s2 = t - c
            return s2
        }
    }

    private class Mash {
        private var n = 4022871197.0

        fun next(data: String): Double {
            for (ch in data) {
                n += ch.code
                var h = 0.02519603282416938 * n
                n = toUint32(h)
                h -= n
                h *= n
                n = toUint32(h)
                h -= n
                n += h * 4294967296.0
            }
            return toUint32(n) * 2.3283064365386963e-10
        }

        private fun toUint32(value: Double): Double {
            val truncated = if (value >= 0) floor(value) else -floor(-value)
            val mod = truncated % 4294967296.0
            return if (mod < 0) mod + 4294967296.0 else mod
        }
    }

    @Suppress("unused")
    private fun jsRoundHalfUp(value: Double): Long = floor(value + 0.5).toLong()
}
