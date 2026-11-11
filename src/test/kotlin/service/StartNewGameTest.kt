package service

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.*

/**
 * Tests für [GameService.startNewGame] und [GameService.startTurn].
 */
class StartNewGameTest {
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
     * Prüft den Spielaufbau für 2, 3 und 4 Fakultäten: Handkarten, Literatur, Stapelgrößen, AP.
     */
    @Test
    fun testStartNewGameSetsUpBoard() {
        for (names in listOf(listOf("A", "B"), listOf("A", "B", "C"), listOf("A", "B", "C", "D"))) {
            rootService.gameService.startNewGame(names)
            val game = assertNotNull(rootService.currentGame)

            assertEquals(names, game.faculties.map { it.name })
            assertEquals(0, game.facultyAtTurn)
            assertEquals(-1, game.endgameTurnsLeft)
            assertEquals(52 - 5 * names.size - 6, game.drawStack.size)
            assertEquals(6, game.centralLiterature.size)
            assertTrue(game.discardStack.isEmpty())
            assertEquals(52, countAllCards(game))
            for (faculty in game.faculties) {
                assertEquals(5, faculty.handCards.size)
                assertTrue(faculty.publications.isEmpty())
                assertTrue(faculty.researchActivity.values.all { it.isEmpty() })
                assertEquals(0, faculty.researchPoints)
            }
            assertEquals(2, game.faculties[0].actionPoints)
            assertEquals(0, game.faculties[1].actionPoints)
        }
    }

    /**
     * Alle 52 Karten sind verschieden (jede Kombination aus Teilgebiet und Wert genau einmal).
     */
    @Test
    fun testStartNewGameUsesFullDeck() {
        rootService.gameService.startNewGame(listOf("A", "B"))
        val game = assertNotNull(rootService.currentGame)
        val allCards = game.drawStack + game.centralLiterature + game.faculties.flatMap { it.handCards }

        // Über (Farbe, Wert) vergleichen, weil ResearchCard keine data class ist
        assertEquals(52, allCards.map { it.suit to it.value }.toSet().size)
    }

    /**
     * Bei zufälliger Reihenfolge spielen dieselben Fakultäten mit, nur ggf. anders sortiert.
     */
    @Test
    fun testStartNewGameRandomOrderKeepsNames() {
        val names = listOf("A", "B", "C", "D")
        // Reihenfolge ist zufällig, also nur prüfen, dass dieselben Namen dabei sind
        rootService.gameService.startNewGame(names, randomizeOrder = true)

        assertEquals(names.toSet(), assertNotNull(rootService.currentGame).faculties.map { it.name }.toSet())
    }

    /**
     * Namen werden getrimmt, damit "A" und " A " als gleich erkannt werden.
     */
    @Test
    fun testStartNewGameTrimsNames() {
        rootService.gameService.startNewGame(listOf(" A ", "B"))
        assertEquals("A", assertNotNull(rootService.currentGame).faculties[0].name)

        assertFailsWith<IllegalArgumentException> {
            rootService.gameService.startNewGame(listOf("A", " A "))
        }
    }

    /**
     * Refreshes und Log werden ausgelöst.
     */
    @Test
    fun testStartNewGameNotifiesRefreshables() {
        rootService.gameService.startNewGame(listOf("A", "B"))

        assertTrue(refreshable.refreshAfterStartNewGameCalled)
        assertTrue(refreshable.refreshAfterStartTurnCalled)
        assertEquals("A ist am Zug (2 AP)", refreshable.lastLogMessage)
    }

    /**
     * Grenzwert: 1 Fakultät ist zu wenig. Es entsteht kein Spiel, kein Refresh.
     */
    @Test
    fun testOneFaculty() {
        assertFailsWith<IllegalArgumentException> { rootService.gameService.startNewGame(listOf("A")) }
        assertNoGameStarted()
    }

    /**
     * Grenzwert: 5 Fakultäten sind zu viele.
     */
    @Test
    fun testFiveFaculties() {
        assertFailsWith<IllegalArgumentException> {
            rootService.gameService.startNewGame(listOf("A", "B", "C", "D", "E"))
        }
        assertNoGameStarted()
    }

    /**
     * Ein Name besteht nur aus Leerzeichen.
     */
    @Test
    fun testBlankName() {
        assertFailsWith<IllegalArgumentException> { rootService.gameService.startNewGame(listOf("A", " ")) }
        assertNoGameStarted()
    }

    /**
     * Zwei gleiche Namen.
     */
    @Test
    fun testDuplicateName() {
        assertFailsWith<IllegalArgumentException> { rootService.gameService.startNewGame(listOf("A", "A")) }
        assertNoGameStarted()
    }

    /**
     * Der Spielzustand hat sich nicht geändert: kein Spiel, kein Refresh.
     */
    private fun assertNoGameStarted() {
        assertNull(rootService.currentGame)
        assertFalse(refreshable.refreshAfterStartNewGameCalled)
    }
}
