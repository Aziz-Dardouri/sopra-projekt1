package entity

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests für FacultyGame (hält Spielzustand)
 */
class FacultyGameTest {
    private lateinit var game: FacultyGame

    /**
     * Dummy Testspiel mit 4 Fakultäten.
     */
    @BeforeTest
    fun setup() {
        val faculties = listOf(
            Faculty("Informatik Dortmund"),
            Faculty("Informatik RUB"),
            Faculty("Informatik Aachen"),
            Faculty("Informatik DuisburgEssen"))
        game = FacultyGame(faculties)
    }

    /**
     * Testet den Initialzustand eines neuen Spiels
     */
    @Test
    fun testInitialGameState() {
        assertEquals(4, game.faculties.size)
        assertEquals("Informatik Dortmund", game.faculties[0].name)
        assertEquals("Informatik RUB", game.faculties[1].name)
        assertEquals("Informatik Aachen", game.faculties[2].name)
        assertEquals("Informatik DuisburgEssen", game.faculties[3].name)

        assertEquals(0, game.facultyAtTurn)
        assertEquals(-1, game.endgameTurnsLeft)
        assertTrue(game.drawStack.isEmpty())
        assertTrue(game.centralLiterature.isEmpty())
        assertTrue(game.discardStack.isEmpty())
    }

    /**
     * Testet, ob nextFaculty den Index erhöht (nächste Fakultät am Zug)
     */
    @Test
    fun testNextFaculty() {
        game.nextFaculty()
        assertEquals(1, game.facultyAtTurn)

        game.nextFaculty()
        assertEquals(2, game.facultyAtTurn)

        game.nextFaculty()
        assertEquals(3, game.facultyAtTurn)
    }

    /**
     * Testet, ob nextFaculty im "Rundgang" funktioniert
     */
    @Test
    fun testNextFacultyCircuit(){
        // init = 3, (3+1) % 4 = 0
        // 0,1,2,3 (4 Fakultäten mit Indexierung 0 bis 3)
        game.facultyAtTurn = 3
        game.nextFaculty()
        assertEquals(0, game.facultyAtTurn)
    }

    /**
     * Testet, ob nextFaculty auch mit 2 Fakultäten richtig wechselt
     */
    @Test
    fun testNextFacultyTwoPlayers() {
        val twoPlayers = FacultyGame(listOf(Faculty("Informatik Dortmund"), Faculty("Informatik RUB")))

        twoPlayers.nextFaculty()
        assertEquals(1, twoPlayers.facultyAtTurn)

        twoPlayers.nextFaculty()
        assertEquals(0, twoPlayers.facultyAtTurn)
    }
    /**
     * Testet, ob nextFaculty auch mit 3 Fakultäten richtig wechselt
     */
    @Test
    fun testNextFacultyThreePlayers() {
        val threePlayers = FacultyGame(listOf(Faculty("Dortmund"), Faculty("RUB"), Faculty("Aachen")))

        threePlayers.nextFaculty()
        threePlayers.nextFaculty()
        assertEquals(2 , threePlayers.facultyAtTurn)

        threePlayers.nextFaculty()
        assertEquals(0, threePlayers.facultyAtTurn)

    }
}