package com.verbilume.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FsrsTest {
    private val now = 1_700_000_000_000L
    private val minute = 60_000L
    private val day = 86_400_000L

    private fun newCard() = Fsrs.newCard("library", "library", 0, 0, now)

    @Test
    fun newCardStartsDueAndUnseen() {
        val card = newCard()
        assertEquals(FsrsState.NEW, card.state)
        assertEquals(now, card.due)
        assertEquals(0, card.reps)
        assertEquals(0.0, card.stability, 0.0)
        assertTrue(!card.isMastered)
    }

    @Test
    fun againOnNewCardReturnsInOneMinute() {
        val card = Fsrs.review(newCard(), now, FsrsRating.AGAIN)
        assertEquals(FsrsState.LEARNING, card.state)
        assertEquals(now + minute, card.due)
        assertEquals(1, card.reps)
        assertEquals(FsrsRating.AGAIN.value, card.lastGrade)
        assertTrue(card.stability > 0.0)
        assertTrue(card.difficulty in 1.0..10.0)
    }

    @Test
    fun hardOnNewCardUsesAverageOfLearningSteps() {
        val card = Fsrs.review(newCard(), now, FsrsRating.HARD)
        assertEquals(FsrsState.LEARNING, card.state)
        assertEquals(now + 6 * minute, card.due)
    }

    @Test
    fun goodOnNewCardMovesToSecondLearningStep() {
        val card = Fsrs.review(newCard(), now, FsrsRating.GOOD)
        assertEquals(FsrsState.LEARNING, card.state)
        assertEquals(now + 10 * minute, card.due)
        assertEquals(1, card.learningSteps)
    }

    @Test
    fun easyOnNewCardGraduatesImmediately() {
        val card = Fsrs.review(newCard(), now, FsrsRating.EASY)
        assertEquals(FsrsState.REVIEW, card.state)
        assertEquals(0, card.learningSteps)
        assertTrue(card.scheduledDays >= 1)
        assertTrue(card.due >= now + day)
    }

    @Test
    fun secondGoodReviewGraduatesToReviewState() {
        val first = Fsrs.review(newCard(), now, FsrsRating.GOOD)
        val second = Fsrs.review(first, first.due, FsrsRating.GOOD)
        assertEquals(FsrsState.REVIEW, second.state)
        assertTrue(second.scheduledDays >= 1)
        // FSRS-6 masks same-day short-term stability growth for GOOD to 1.0.
        assertTrue("first=${first.stability} second=${second.stability}", second.stability >= first.stability)
    }

    @Test
    fun intervalsAreMonotonicAcrossRatings() {
        val first = Fsrs.review(newCard(), now, FsrsRating.GOOD)
        val graduated = Fsrs.review(first, first.due, FsrsRating.GOOD)
        val reviewedAt = graduated.due

        val again = Fsrs.review(graduated, reviewedAt, FsrsRating.AGAIN)
        val hard = Fsrs.review(graduated, reviewedAt, FsrsRating.HARD)
        val good = Fsrs.review(graduated, reviewedAt, FsrsRating.GOOD)
        val easy = Fsrs.review(graduated, reviewedAt, FsrsRating.EASY)

        assertEquals(FsrsState.RELEARNING, again.state)
        assertTrue(again.lapses >= 1)
        assertEquals(FsrsState.REVIEW, hard.state)
        assertEquals(FsrsState.REVIEW, good.state)
        assertEquals(FsrsState.REVIEW, easy.state)
        assertTrue(hard.scheduledDays <= good.scheduledDays)
        assertTrue(good.scheduledDays < easy.scheduledDays)
    }

    @Test
    fun retrievabilityMatchesRequestedRetentionAtStability() {
        val r = Fsrs.retrievability(10, 10.0)
        assertEquals(0.9, r, 0.001)
        assertTrue(Fsrs.retrievability(0, 10.0) > 0.99)
        assertTrue(Fsrs.retrievability(20, 10.0) < r)
    }

    @Test
    fun masteredRequiresReviewStateAndThreeWeekStability() {
        val card = newCard().copy(state = FsrsState.REVIEW, stability = 21.0)
        assertTrue(card.isMastered)
        assertTrue(!card.copy(stability = 20.99).isMastered)
        assertTrue(!card.copy(state = FsrsState.LEARNING, stability = 90.0).isMastered)
    }

    @Test
    fun previewReturnsAllFourRatingsWithoutMutatingOriginal() {
        val card = Fsrs.review(Fsrs.review(newCard(), now, FsrsRating.GOOD), now + 10 * minute, FsrsRating.GOOD)
        val repsBefore = card.reps
        val previews = Fsrs.preview(card, card.due)
        assertEquals(FsrsRating.entries.toSet(), previews.keys)
        previews.forEach { (rating, next) ->
            assertEquals(repsBefore + 1, next.reps)
            assertEquals(rating.value, next.lastGrade)
            assertTrue(next.due >= card.due)
        }
        assertEquals(repsBefore, card.reps)
    }

    @Test
    fun sameDayReviewUsesShortTermStability() {
        val first = Fsrs.review(newCard(), now, FsrsRating.GOOD)
        val second = Fsrs.review(first, now + minute, FsrsRating.GOOD)
        assertEquals(second.lastReview, now + minute)
        assertTrue(second.stability > 0.0)
        assertTrue(second.difficulty in 1.0..10.0)
    }
}
