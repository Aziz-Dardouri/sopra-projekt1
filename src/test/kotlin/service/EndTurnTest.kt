package service

import entity.ResearchArea
import entity.ResearchTopic
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests für [GameService.endTurn]: Zugwechsel, Endspiel-Countdown und Spielende.
 */
class EndTurnTest {
    private lateinit var rootService: RootService
    private lateinit var refreshable: TestRefreshable


    /**
     * Neuer RootService mit Test-Refreshable vor jedem Test.
     */
    @BeforeTest
    fun init() {
        rootService = RootService()
        refreshable = TestRefreshable()
        rootService.addRefreshable(refreshable)
    }

    /**
     * Erfolgsfall: die nächste Fakultät ist am Zug und hat 2 AP, der Refresh wurde ausgelöst.
     */
    @Test
    fun testEndTurnSwitchesFaculty() {
        val game = rootService.createTestGame("Do", "Bo", "Dus")
        //Eine Karte im Nachziehstapel, damit das Endspiel noch nicht beginnt
        game.drawStack.push(card(ResearchTopic.ACE, ResearchArea.AI))

        //Aktion
        rootService.gameService.endTurn()

        assertEquals(1, game.facultyAtTurn)
        assertEquals(2, game.faculties[1].actionPoints)
        assertEquals(-1, game.endgameTurnsLeft)
        assertTrue(refreshable.refreshAfterEndTurnCalled)
        assertTrue(refreshable.refreshAfterStartTurnCalled)
        assertFalse(refreshable.refreshAfterGameEndCalled)
    }

    /**
     * Nach letzter Fakultät ist wieder erste dran.
     */
    @Test
    fun testEndTurnIsCyclic(){
        val game = rootService.createTestGame("A", "B")
        game.drawStack.push(card(ResearchTopic.ACE, ResearchArea.AI))
        rootService.gameService.endTurn()
        rootService.gameService.endTurn()

        assertEquals(0, game.facultyAtTurn)
    }

    /**
     * Wird der Nachziehstapel leer, kommt jede andere Fakultät noch genau einmal dran.
     * Geprüft für 2, 3 und 4 Fakultäten.
     */
    @Test
    fun testGameEndsAfterEveryOtherFacultyPlayedOnce() {
        for (count in 2..4) {
            // Pro Spielerzahl frisch anfangen, sonst hängen die Refresh-Flags vom letzten Durchlauf nach
            init()
            val names = List(count) { "F$it" }.toTypedArray()
            val game = rootService.createTestGame(*names)
            // Bewusst nicht Fakultät 0, damit der Countdown nicht zufällig an der Startfakultät hängt
            game.facultyAtTurn = 1

            // Fakultät 1 beendet den Zug mit leerem Nachziehstapel
            rootService.gameService.endTurn()
            assertEquals(count - 1, game.endgameTurnsLeft)

            // Jede andere Fakultät spielt noch einmal
            repeat(count - 1) { turn ->
                assertFalse(
                    refreshable.refreshAfterGameEndCalled,
                    "Spiel endet zu früh ($count Fakultäten, Zug $turn)"
                )
                rootService.gameService.endTurn()
            }
            assertTrue(refreshable.refreshAfterGameEndCalled, "Spiel endet nicht ($count Fakultäten)")
            assertEquals(0, game.endgameTurnsLeft)
        }
    }

    /**
     * Nach Spielende ist kein weiterer Zugwechsel möglich.
     */
    @Test
    fun testEndTurnAfterGameEnd() {
        val game = rootService.createTestGame("A", "B")
        // Abkürzung: so tun, als wäre nur noch ein letzter Zug übrig
        game.endgameTurnsLeft = 1
        rootService.gameService.endTurn()
        assertTrue(refreshable.refreshAfterGameEndCalled)

        assertFailsWith<IllegalStateException> { rootService.gameService.endTurn() }
    }

    /**
     * Ohne Spiel wirft endTurn eine IllegalStateException.
     */
    @Test
    fun testEndTurnWithoutGame() {
        assertFailsWith<IllegalStateException> { rootService.gameService.endTurn() }
        assertFalse(refreshable.refreshAfterEndTurnCalled)
    }

    /**
     * Spielende mit eindeutiger Siegerin: genau eine Fakultät wird als Gewinnerin gemeldet.
     */
    @Test
    fun testGameEndSingleWinner() {
        val game = rootService.createTestGame("A", "B")
        game.faculties[0].researchPoints = 5
        game.faculties[1].researchPoints = 7
        // Abkürzung: so tun, als wäre nur noch ein letzter Zug übrig
        game.endgameTurnsLeft = 1

        rootService.gameService.endTurn()

        assertEquals(listOf(game.faculties[1]), refreshable.lastWinners)
        assertEquals("Spielende – Sieg: B mit 7 Punkten.", refreshable.lastLogMessage)
    }

    /**
     * Gleichstand: alle punktgleichen Fakultäten teilen sich den ersten Platz (in Zugreihenfolge).
     */
    @Test
    fun testGameEndTieSharesFirstPlace() {
        val game = rootService.createTestGame("A", "B", "C")
        game.faculties[0].researchPoints = 7
        game.faculties[1].researchPoints = 3
        game.faculties[2].researchPoints = 7
        game.endgameTurnsLeft = 1

        rootService.gameService.endTurn()

        assertEquals(listOf(game.faculties[0], game.faculties[2]), refreshable.lastWinners)
        assertEquals("Spielende – Sieg: A, C mit 7 Punkten.", refreshable.lastLogMessage)
    }




}