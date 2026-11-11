package service

import entity.FacultyGame
import entity.ResearchArea
import entity.ResearchCard
import entity.ResearchTopic
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Tests für [PlayerActionService.startAdaptStrategy].
 */
class StartAdaptStrategyTest {
    private lateinit var rootService: RootService
    private lateinit var refreshable: TestRefreshable
    private lateinit var game: FacultyGame
    private lateinit var handCard: ResearchCard
    private lateinit var lower: ResearchCard
    private lateinit var top: ResearchCard

    /**
     * Testspiel: "A" hat 2 AP und eine Handkarte, im Nachziehstapel liegen [lower] und darüber [top].
     */
    @BeforeTest
    fun setUp() {
        rootService = RootService()
        refreshable = TestRefreshable()
        rootService.addRefreshable(refreshable)
        game = rootService.createTestGame("A", "B")
        handCard = card(ResearchTopic.TWO, ResearchArea.AI)
        lower = card(ResearchTopic.QUEEN, ResearchArea.SE)
        top = card(ResearchTopic.KING, ResearchArea.SE)
        game.faculties[0].handCards.add(handCard)
        // top liegt oben, lower direkt darunter -> erwartet wird top zuerst
        game.drawStack.push(lower)
        game.drawStack.push(top)
    }

    /**
     * Erfolgsfall: Handkarte abgeworfen, 1 AP weniger, die beiden obersten Karten (oberste zuerst)
     * werden zurückgegeben und bleiben bis Schritt 2 auf dem Nachziehstapel.
     */
    @Test
    fun testStartAdaptStrategy() {
        val drawn = rootService.playerActionService.startAdaptStrategy(handCard)

        assertEquals(listOf(top, lower), drawn)
        assertTrue(game.faculties[0].handCards.isEmpty())
        assertSame(handCard, game.discardStack.peek())
        assertEquals(2, game.drawStack.size)
        assertEquals(1, game.faculties[0].actionPoints)
        assertTrue(refreshable.refreshAfterStartAdaptStrategyCalled)
        assertTrue(refreshable.refreshAfterLogCalled)
    }

    /**
     * Karte nicht auf der Hand: IllegalArgumentException, der Spielzustand hat sich nicht geändert.
     */
    @Test
    fun testCardNotInHand() {
        assertFailsWith<IllegalArgumentException> {
            rootService.playerActionService.startAdaptStrategy(card(ResearchTopic.TWO, ResearchArea.AI))
        }
        assertEquals(1, game.faculties[0].handCards.size)
        assertTrue(game.discardStack.isEmpty())
        assertEquals(2, game.faculties[0].actionPoints)
        assertFalse(refreshable.refreshAfterStartAdaptStrategyCalled)
    }

    /**
     * Grenzwert: nur 1 Karte im Nachziehstapel reicht nicht.
     */
    @Test
    fun testOnlyOneCardInDrawStack() {
        // Eine Karte runternehmen, damit nur noch eine übrig ist
        game.drawStack.pop()

        assertFailsWith<IllegalStateException> { rootService.playerActionService.startAdaptStrategy(handCard) }
        assertEquals(1, game.faculties[0].handCards.size)
    }

    /**
     * Keine AP mehr: IllegalStateException.
     */
    @Test
    fun testNoActionPoints() {
        game.faculties[0].actionPoints = 0

        assertFailsWith<IllegalStateException> { rootService.playerActionService.startAdaptStrategy(handCard) }
        assertTrue(game.discardStack.isEmpty())
    }

    /**
     * Kein Spiel: IllegalStateException.
     */
    @Test
    fun testNoGame() {
        rootService.currentGame = null

        assertFailsWith<IllegalStateException> { rootService.playerActionService.startAdaptStrategy(handCard) }
    }

    /**
     * Spiel ist vorbei: IllegalStateException, der Spielzustand hat sich nicht geändert.
     */
    @Test
    fun testGameOver() {
        game.endgameTurnsLeft = 0

        assertFailsWith<IllegalStateException> { rootService.playerActionService.startAdaptStrategy(handCard) }

        assertEquals(1, game.faculties[0].handCards.size)
        assertTrue(game.discardStack.isEmpty())
        assertEquals(2, game.faculties[0].actionPoints)
        assertFalse(refreshable.refreshAfterStartAdaptStrategyCalled)
    }
}
