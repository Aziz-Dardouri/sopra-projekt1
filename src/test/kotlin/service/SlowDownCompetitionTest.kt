package service

import entity.*
import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.*

/**
 * Testklasse zur Überprüfung der Aktion "Konkurrenz ausbremsen" (Issue 17)
 * im [PlayerActionService].
 */
class SlowDownCompetitionTest {

    private lateinit var rootService: RootService
    private lateinit var activeFaculty: Faculty
    private lateinit var opponentFaculty: Faculty
    private lateinit var testRefreshable: TestRefreshable

    /**
     * Hilfsklasse, um zu prüfen, ob die Refreshables korrekt aufgerufen werden.
     */
    private class TestRefreshable : Refreshable {
        var slowDownCalled = false
        var lastTargetFaculty: Faculty? = null
        var lastArea: ResearchArea? = null
        var lastRemovedCard: ResearchCard? = null
        var lastLogMessage: String? = null

        override fun refreshAfterSlowDownCompetition(
            targetFaculty: Faculty,
            area: ResearchArea,
            removedCard: ResearchCard
        ) {
            slowDownCalled = true
            lastTargetFaculty = targetFaculty
            lastArea = area
            lastRemovedCard = removedCard
        }

        override fun refreshAfterLog(message: String) {
            lastLogMessage = message
        }
    }

    /**
     * Bereitet vor jedem Test ein sauberes Spiel mit zwei Fakultäten vor.
     */
    @BeforeTest
    fun setUp() {
        rootService = RootService()
        testRefreshable = TestRefreshable()
        rootService.addRefreshable(testRefreshable)

        activeFaculty = Faculty("TU Dortmund")
        opponentFaculty = Faculty("Uni Bochum")

        val game = FacultyGame(
            faculties = mutableListOf(activeFaculty, opponentFaculty)
        )
        game.facultyAtTurn = 0
        activeFaculty.actionPoints = 2

        rootService.currentGame = game
    }

    /**
     * Testet den Erfolgsfall: 2 AP werden abgezogen, die oberste Karte wird entfernt,
     * landet auf dem Ablagestapel und die Refreshables werden benachrichtigt.
     */
    @Test
    fun testSlowDownCompetitionSuccess() {
        val bottomCard = ResearchCard(ResearchTopic.TEN, ResearchArea.AI)
        val topCard = ResearchCard(ResearchTopic.ACE, ResearchArea.AI)
        val targetStack = opponentFaculty.researchActivity.getValue(ResearchArea.AI)
        targetStack.push(bottomCard)
        targetStack.push(topCard)

        rootService.playerActionService.slowDownCompetition(opponentFaculty, ResearchArea.AI)

        // 1. AP um 2 reduziert?
        assertEquals(0, activeFaculty.actionPoints)

        // 2. Oberste Karte vom Zielstapel entfernt, untere noch da?
        assertEquals(1, targetStack.size)
        assertEquals(bottomCard, targetStack.peek())

        // 3. Entfernte Karte liegt auf dem Ablagestapel?
        val game = checkNotNull(rootService.currentGame)
        assertTrue(game.discardStack.contains(topCard))
        assertEquals(topCard, game.discardStack.last())

        // 4. Refreshables wurden korrekt aufgerufen?
        assertTrue(testRefreshable.slowDownCalled)
        assertEquals(opponentFaculty, testRefreshable.lastTargetFaculty)
        assertEquals(ResearchArea.AI, testRefreshable.lastArea)
        assertEquals(topCard, testRefreshable.lastRemovedCard)
        assertNotNull(testRefreshable.lastLogMessage)
    }

    /**
     * Testet, ob eine [IllegalStateException] geworfen wird, wenn kein Spiel läuft.
     */
    @Test
    fun testNoActiveGameThrowsException() {
        rootService.currentGame = null

        assertFailsWith<IllegalStateException> {
            rootService.playerActionService.slowDownCompetition(opponentFaculty, ResearchArea.AI)
        }
    }

    /**
     * Testet, ob eine [IllegalStateException] geworfen wird, wenn weniger als 2 AP vorhanden sind.
     */
    @Test
    fun testNotEnoughActionPointsThrowsException() {
        activeFaculty.actionPoints = 1
        val card = ResearchCard(ResearchTopic.SEVEN, ResearchArea.AI)
        opponentFaculty.researchActivity.getValue(ResearchArea.AI).push(card)

        assertFailsWith<IllegalStateException> {
            rootService.playerActionService.slowDownCompetition(opponentFaculty, ResearchArea.AI)
        }
    }

    /**
     * Testet, ob eine [IllegalArgumentException] geworfen wird, wenn man sich selbst als Ziel wählt.
     */
    @Test
    fun testTargetSelfThrowsException() {
        val card = ResearchCard(ResearchTopic.EIGHT, ResearchArea.AI)
        activeFaculty.researchActivity.getValue(ResearchArea.AI).push(card)

        assertFailsWith<IllegalArgumentException> {
            rootService.playerActionService.slowDownCompetition(activeFaculty, ResearchArea.AI)
        }
    }

    /**
     * Testet, ob eine [IllegalArgumentException] geworfen wird, wenn die Zielfakultät nicht am Spiel teilnimmt.
     */
    @Test
    fun testTargetNotInGameThrowsException() {
        val outsiderFaculty = Faculty("Fremde Uni")
        val card = ResearchCard(ResearchTopic.TWO, ResearchArea.AI)
        outsiderFaculty.researchActivity.getValue(ResearchArea.AI).push(card)

        assertFailsWith<IllegalArgumentException> {
            rootService.playerActionService.slowDownCompetition(outsiderFaculty, ResearchArea.AI)
        }
    }

    /**
     * Testet, ob eine [IllegalArgumentException] geworfen wird, wenn der gewählte Forschungsstapel leer ist.
     */
    @Test
    fun testEmptyTargetStackThrowsException() {
        // Stapel für AI ist standardmäßig leer
        assertFailsWith<IllegalArgumentException> {
            rootService.playerActionService.slowDownCompetition(opponentFaculty, ResearchArea.AI)
        }
    }

    /**
     * Testet, ob nach Spielende eine [IllegalStateException] geworfen wird und der Stapel unverändert bleibt.
     */
    @Test
    fun testGameOverThrowsException() {
        val game = checkNotNull(rootService.currentGame)
        opponentFaculty.addResearch(ResearchCard(ResearchTopic.ACE, ResearchArea.AI))
        game.endgameTurnsLeft = 0

        assertFailsWith<IllegalStateException> {
            rootService.playerActionService.slowDownCompetition(opponentFaculty, ResearchArea.AI)
        }
        assertEquals(1, opponentFaculty.researchActivity.getValue(ResearchArea.AI).size)
        assertEquals(2, activeFaculty.actionPoints)
        assertFalse(testRefreshable.slowDownCalled)
    }
}