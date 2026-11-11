package service

import entity.*
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests für [PlayerActionService.useLiterature].
 */
class UseLiteratureTest {

    private lateinit var rootService: RootService
    private lateinit var testRefreshable: TestRefreshable
    private lateinit var game: FacultyGame
    private lateinit var activeFaculty: Faculty


    /**
     * Testspiel: "TU Dortmund" am Zug mit 2 AP, drei Karten in der zentralen Literatur.
     */
    @BeforeTest
    fun setUp() {
        rootService = RootService()
        testRefreshable = TestRefreshable()
        rootService.addRefreshable(testRefreshable)


        activeFaculty = Faculty("TU Dortmund")
        val otherFaculty = Faculty("RUB")
        activeFaculty.resetActionPoints() // AP'yi 2 yapar

        game = FacultyGame(faculties = mutableListOf(activeFaculty, otherFaculty))
        game.facultyAtTurn = 0
        rootService.currentGame = game
    }

    /**
     * Erfolgsfall: die gewählte Karte wird aus der Literatur entfernt und kostet 2 AP.
     */
    @Test
    fun testUseLiteratureSuccess() {
        val cardToUse = ResearchCard(ResearchTopic.entries[0], ResearchArea.entries[0])
        val remainingCard = ResearchCard(ResearchTopic.entries[1], ResearchArea.entries[1])

        game.centralLiterature.clear()
        game.centralLiterature.add(cardToUse)
        game.centralLiterature.add(remainingCard)


        rootService.playerActionService.useLiterature(cardToUse)

        // 1. Karte entfernt, Rest bleibt?
        assertFalse(game.centralLiterature.contains(cardToUse), "Karte muss entfernt werden.")
        assertTrue(game.centralLiterature.contains(remainingCard), "Andere Karte muss bleiben.")

        // ist die karte auf dem korrekten stapel?
        val targetStack = activeFaculty.researchActivity.getValue(cardToUse.suit)
        assertFalse(targetStack.isEmpty(), "Stapel darf nicht leer sein.")
        assertEquals(cardToUse, targetStack.peek(), "Karte muss oben sein.")

        // wurde ap um 2 verringert?
        assertEquals(0, activeFaculty.actionPoints, "AP müssen 0 sein.")

        // wurde refereshable ausgelöst?
        assertTrue(testRefreshable.refreshAfterUseLiteratureCalled, "Methode muss aufgerufen werden.")
        //assertEquals(cardToUse, testRefreshable.lastUsedCard, "Richtige Karte übergeben.")
    }

    /**
     * Kein laufendes Spiel: IllegalStateException.
     */
    @Test
    fun testUseLiteratureNoActiveGame() {
        rootService.currentGame = null
        val dummyCard = ResearchCard(ResearchTopic.entries[0], ResearchArea.entries[0])

        assertFailsWith<IllegalStateException> {
            rootService.playerActionService.useLiterature(dummyCard)
        }
        assertFalse(testRefreshable.refreshAfterUseLiteratureCalled)
    }

    /**
     * Fehler: weniger als 2 AP --> IllegalStateException.
     */
    @Test
    fun testUseLiteratureNotEnoughAP() {
        val card = ResearchCard(ResearchTopic.entries[0], ResearchArea.entries[0])
        game.centralLiterature.add(card)
        activeFaculty.actionPoints = 1 // nicht genug ap

        assertFailsWith<IllegalStateException> {
            rootService.playerActionService.useLiterature(card)
        }

        // // Der Spielzustand darf sich nicht verändert haben
        assertTrue(game.centralLiterature.contains(card))
        assertEquals(1, activeFaculty.actionPoints)
        assertFalse(testRefreshable.refreshAfterUseLiteratureCalled)
    }

    /**
     * Fehler: die gewählte Karte liegt nicht in der zentralen Literatur --> IllegalArgumentException.
     */
    @Test
    fun testUseLiteratureCardNotInLiterature() {
        val cardInLiterature = ResearchCard(ResearchTopic.entries[0], ResearchArea.entries[0])
        val cardNotInLiterature = ResearchCard(ResearchTopic.entries[1], ResearchArea.entries[1])

        game.centralLiterature.clear()
        game.centralLiterature.add(cardInLiterature)

        assertFailsWith<IllegalArgumentException> {
            rootService.playerActionService.useLiterature(cardNotInLiterature)
        }

        // // Der Spielzustand darf sich nicht verändert haben
        assertEquals(2, activeFaculty.actionPoints)
        assertFalse(testRefreshable.refreshAfterUseLiteratureCalled)
    }

    /**
     * Spiel ist vorbei: IllegalStateException, die Karte bleibt in der Literatur.
     */
    @Test
    fun testUseLiteratureAfterGameEnd() {
        val cardInLiterature = ResearchCard(ResearchTopic.entries[0], ResearchArea.entries[0])
        game.centralLiterature.add(cardInLiterature)
        game.endgameTurnsLeft = 0

        assertFailsWith<IllegalStateException> {
            rootService.playerActionService.useLiterature(cardInLiterature)
        }

        assertTrue(cardInLiterature in game.centralLiterature)
        assertEquals(2, activeFaculty.actionPoints)
        assertFalse(testRefreshable.refreshAfterUseLiteratureCalled)
    }
}