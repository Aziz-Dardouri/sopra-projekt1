package service

import entity.FacultyGame
import entity.ResearchArea
import entity.ResearchTopic
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Tests für [PlayerActionService.gainNewIdeas].
 */
class GainNewIdeasTest {
    private lateinit var rootService: RootService
    private lateinit var refreshable: TestRefreshable
    private lateinit var game: FacultyGame

    /**
     * Testspiel mit zwei Fakultäten, "A" ist am Zug (2 AP), eine Karte liegt im Nachziehstapel.
     */
    @BeforeTest
    fun setUp() {
        rootService = RootService()
        refreshable = TestRefreshable()
        rootService.addRefreshable(refreshable)
        game = rootService.createTestGame("A", "B")
        game.drawStack.push(card(ResearchTopic.KING, ResearchArea.SE))
    }

    /**
     * Erfolgsfall: oberste Nachziehkarte kommt auf die Hand, 1 AP weniger, Refresh und Log.
     */
    @Test
    fun testGainNewIdeas() {
        val topCard = game.drawStack.peek()

        rootService.playerActionService.gainNewIdeas()

        val active = game.faculties[0]
        assertSame(topCard, active.handCards.single())
        assertTrue(game.drawStack.isEmpty())
        assertEquals(1, active.actionPoints)
        assertTrue(refreshable.refreshAfterGainNewIdeasCalled)
        assertEquals("A gewinnt eine neue Idee", refreshable.lastLogMessage)
    }

    /**
     * Grenzwert: mit genau 5 Handkarten darf noch gezogen werden (danach 6).
     */
    @Test
    fun testFiveHandCardsAllowed() {
        // Genau 5 ist noch erlaubt, danach hat man 6 Karten
        game.faculties[0].handCards.addAll(cardsOf(ResearchArea.AI, 5))

        rootService.playerActionService.gainNewIdeas()

        assertEquals(6, game.faculties[0].handCards.size)
    }

    /**
     * Grenzwert: mit 6 Handkarten ist das Limit erreicht. Der Spielzustand hat sich nicht geändert.
     */
    @Test
    fun testSixHandCardsNotAllowed() {
        val active = game.faculties[0]
        active.handCards.addAll(cardsOf(ResearchArea.AI, 6))

        assertFailsWith<IllegalStateException> { rootService.playerActionService.gainNewIdeas() }

        assertEquals(6, active.handCards.size)
        assertEquals(1, game.drawStack.size)
        assertEquals(2, active.actionPoints)
        assertFalse(refreshable.refreshAfterGainNewIdeasCalled)
    }

    /**
     * Leerer Nachziehstapel: IllegalStateException, AP bleiben.
     */
    @Test
    fun testEmptyDrawStack() {
        // Endspiel-Situation nachstellen: es gibt nichts mehr zu ziehen
        game.drawStack.clear()

        assertFailsWith<IllegalStateException> { rootService.playerActionService.gainNewIdeas() }

        assertEquals(2, game.faculties[0].actionPoints)
        assertTrue(game.faculties[0].handCards.isEmpty())
    }

    /**
     * Keine AP mehr: IllegalStateException, nichts wird gezogen.
     */
    @Test
    fun testNoActionPoints() {
        game.faculties[0].actionPoints = 0

        assertFailsWith<IllegalStateException> { rootService.playerActionService.gainNewIdeas() }

        assertEquals(1, game.drawStack.size)
    }

    /**
     * Kein Spiel: IllegalStateException.
     */
    @Test
    fun testNoGame() {
        rootService.currentGame = null

        assertFailsWith<IllegalStateException> { rootService.playerActionService.gainNewIdeas() }
    }

    /**
     * Spiel ist vorbei: IllegalStateException, der Spielzustand hat sich nicht geändert.
     */
    @Test
    fun testGameOver() {
        game.endgameTurnsLeft = 0

        assertFailsWith<IllegalStateException> { rootService.playerActionService.gainNewIdeas() }

        assertEquals(1, game.drawStack.size)
        assertEquals(2, game.faculties[0].actionPoints)
        assertFalse(refreshable.refreshAfterGainNewIdeasCalled)
    }
}
