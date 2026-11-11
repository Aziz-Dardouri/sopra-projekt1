package service

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests für [GameService.startTurn].
 */
class StartTurnTest {
    private lateinit var rootService: RootService
    private lateinit var refreshable: TestRefreshable

    /**
     * Neuer RootService mit Test-Refreshable vor jedem Test.
     */
    @BeforeTest
    fun setUp() {
        rootService = RootService()
        refreshable = TestRefreshable()
        rootService.addRefreshable(refreshable)
    }

    /**
     * Die Fakultät am Zug bekommt 2 AP, Refresh und Log werden ausgelöst.
     */
    @Test
    fun testStartTurnResetsActionPoints() {
        val game = rootService.createTestGame("A", "B")
        // Absichtlich nicht die erste Fakultät, damit wirklich der Index geprüft wird
        game.facultyAtTurn = 1

        rootService.gameService.startTurn()

        assertEquals(2, game.faculties[1].actionPoints)
        assertTrue(refreshable.refreshAfterStartTurnCalled)
        assertEquals("B ist am Zug (2 AP)", refreshable.lastLogMessage)
    }

    /**
     * Ohne Spiel wirft startTurn eine IllegalStateException.
     */
    @Test
    fun testStartTurnWithoutGame() {
        assertFailsWith<IllegalStateException> { rootService.gameService.startTurn() }
        assertFalse(refreshable.refreshAfterStartTurnCalled)
    }

    /**
     * Nach Spielende wirft startTurn eine IllegalStateException und vergibt keine neuen AP.
     */
    @Test
    fun testStartTurnAfterGameEnd() {
        val game = rootService.createTestGame("A", "B")
        game.faculties[0].actionPoints = 0
        game.endgameTurnsLeft = 0

        assertFailsWith<IllegalStateException> { rootService.gameService.startTurn() }

        assertEquals(0, game.faculties[0].actionPoints)
        assertFalse(refreshable.refreshAfterStartTurnCalled)
    }
}
