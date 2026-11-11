package service

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Testet den rootService und die Funktionalität der Refreshables
 */
class RootServiceTest {
    private lateinit var rootService: RootService

    /**
     * Dummy RootService vor jedem Testen
     */
    @BeforeTest
    fun init() {
        rootService = RootService()
    }

    /**
     * Testet, dass zuvor kein Spiel läuft.
     */
    @Test
    fun testNoGameAtStart() {
        assertNull(rootService.currentGame)
    }

    /**
     * Testet, ob addRefreshable Funktionalität bei beiden Services korrekt handelt
     */
    @Test
    fun testAddRefreshable() {
        val refreshable = TestRefreshable()
        rootService.addRefreshable(refreshable)

        rootService.gameService.onAllRefreshables { refreshAfterStartTurn() }
        rootService.playerActionService.onAllRefreshables { refreshAfterLog("Testeintrag") }

        assertTrue(refreshable.refreshAfterStartTurnCalled)
        assertTrue(refreshable.refreshAfterLogCalled)
        assertEquals("Testeintrag", refreshable.lastLogMessage)
    }

    /**
     * Testet, ob addRefreshables mehrere Refreshables gleichzeitig anmeldet
     */
    @Test
    fun testAddRefreshables() {
        val first = TestRefreshable()
        val second = TestRefreshable()
        rootService.addRefreshables(first, second)

        rootService.playerActionService.onAllRefreshables { refreshAfterLog("Testeintrag") }

        assertTrue(first.refreshAfterLogCalled)
        assertTrue(second.refreshAfterLogCalled)
    }

    /**
     * Testet, ob createTestGame ein laufendes Testspiel mit 2 AP für die erste Fakultät anlegt
     */
    @Test
    fun testCreateTestGame() {
        val game = rootService.createTestGame()

        assertSame(game, rootService.currentGame)
        assertEquals(listOf("Dortmund", "Bochum"), game.faculties.map { it.name })
        assertEquals(2, game.faculties[0].actionPoints)
        assertEquals(0, game.faculties[1].actionPoints)
    }
}
