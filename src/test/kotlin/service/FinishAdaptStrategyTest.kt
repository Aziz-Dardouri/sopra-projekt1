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
 * Tests für [PlayerActionService.finishAdaptStrategy].
 */
class FinishAdaptStrategyTest {
    private lateinit var rootService: RootService
    private lateinit var refreshable: TestRefreshable
    private lateinit var game: FacultyGame
    private lateinit var lower: ResearchCard
    private lateinit var top: ResearchCard

    /**
     * Testspiel nach Schritt 1: "A" hat eine Handkarte abgeworfen, [lower] und [top] liegen aufgedeckt.
     */
    @BeforeTest
    fun setUp() {
        rootService = RootService()
        refreshable = TestRefreshable()
        rootService.addRefreshable(refreshable)
        game = rootService.createTestGame("A", "B")
        lower = card(ResearchTopic.QUEEN, ResearchArea.SE)
        top = card(ResearchTopic.KING, ResearchArea.SE)
        // Das ACE liegt ganz unten, damit nach Schritt 2 noch eine Karte im Stapel bleibt
        game.drawStack.push(card(ResearchTopic.ACE, ResearchArea.AI))
        game.drawStack.push(lower)
        game.drawStack.push(top)
        val handCard = card(ResearchTopic.TWO, ResearchArea.AI)
        game.faculties[0].handCards.add(handCard)
        rootService.playerActionService.startAdaptStrategy(handCard)
        // Flags aus Schritt 1 zurücksetzen, in diesen Tests interessiert uns nur Schritt 2
        refreshable.resetVars()
    }

    /**
     * Die oberste Karte behalten: sie kommt auf die Hand, die andere auf den Ablagestapel.
     */
    @Test
    fun testKeepTopCard() {
        rootService.playerActionService.finishAdaptStrategy(top)

        assertSame(top, game.faculties[0].handCards.single())
        assertSame(lower, game.discardStack.peek())
        assertEquals(1, game.drawStack.size)
        assertTrue(refreshable.refreshAfterFinishAdaptStrategyCalled)
        assertTrue(refreshable.refreshAfterLogCalled)
    }

    /**
     * Die untere der beiden Karten behalten.
     */
    @Test
    fun testKeepLowerCard() {
        rootService.playerActionService.finishAdaptStrategy(lower)

        assertSame(lower, game.faculties[0].handCards.single())
        assertSame(top, game.discardStack.peek())
    }

    /**
     * Schritt 2 kostet keine weiteren AP (die Aktion kostet insgesamt 1 AP).
     */
    @Test
    fun testNoExtraActionPoints() {
        rootService.playerActionService.finishAdaptStrategy(top)

        assertEquals(1, game.faculties[0].actionPoints)
    }

    /**
     * Karte gehört nicht zu den beiden aufgedeckten: IllegalArgumentException, Zustand unverändert.
     */
    @Test
    fun testForeignCard() {
        assertFailsWith<IllegalArgumentException> {
            rootService.playerActionService.finishAdaptStrategy(card(ResearchTopic.KING, ResearchArea.SE))
        }
        assertEquals(3, game.drawStack.size)
        assertTrue(game.faculties[0].handCards.isEmpty())
        assertFalse(refreshable.refreshAfterFinishAdaptStrategyCalled)
    }

    /**
     * Weniger als 2 Karten im Nachziehstapel: IllegalStateException.
     */
    @Test
    fun testTooFewCards() {
        // Nur noch eine Karte da, z. B. weil zwischendurch jemand gezogen hat
        game.drawStack.clear()
        game.drawStack.push(top)

        assertFailsWith<IllegalStateException> { rootService.playerActionService.finishAdaptStrategy(top) }
    }

    /**
     * Kein Spiel: IllegalStateException.
     */
    @Test
    fun testNoGame() {
        rootService.currentGame = null

        assertFailsWith<IllegalStateException> { rootService.playerActionService.finishAdaptStrategy(top) }
    }

    /**
     * Spiel ist vorbei: IllegalStateException, der Spielzustand hat sich nicht geändert.
     */
    @Test
    fun testGameOver() {
        game.endgameTurnsLeft = 0

        assertFailsWith<IllegalStateException> { rootService.playerActionService.finishAdaptStrategy(top) }

        assertEquals(3, game.drawStack.size)
        assertTrue(game.faculties[0].handCards.isEmpty())
        assertFalse(refreshable.refreshAfterFinishAdaptStrategyCalled)
    }
}
