package service

import entity.Faculty
import entity.FacultyGame
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Spielt komplette Partien nur über die Service-Schicht (Vorgabe Architektur-Vorlesung).
 * Die Spielenden folgen einer einfachen festen Strategie; geprüft werden Kartenerhalt,
 * die Endspiel-Regel und die Endwertung.
 */
class FullGameTest {
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
     * Komplette Partie mit 2 Fakultäten.
     */
    @Test
    fun testFullGameTwoFaculties() {
        playFullGame(listOf("Dortmund", "Bochum"))
    }

    /**
     * Komplette Partie mit 3 Fakultäten.
     */
    @Test
    fun testFullGameThreeFaculties() {
        playFullGame(listOf("Dortmund", "Bochum", "Essen"))
    }

    /**
     * Komplette Partie mit 4 Fakultäten, mehrfach wegen des zufälligen Mischens.
     */
    @Test
    fun testFullGameFourFaculties() {
        repeat(REPETITIONS) {
            // Jede Wiederholung braucht einen frischen RootService, sonst ist das Spiel schon vorbei
            setUp()
            playFullGame(listOf("Dortmund", "Bochum", "Essen", "Aachen"))
        }
    }

    /**
     * Startet ein Spiel und spielt Züge, bis [TestRefreshable.refreshAfterGameEndCalled] gesetzt ist.
     * Nach jeder Aktion müssen alle 52 Karten im Spiel sein.
     *
     * @param names Namen der Fakultäten
     */
    private fun playFullGame(names: List<String>) {
        rootService.gameService.startNewGame(names)
        val game = assertNotNull(rootService.currentGame)
        var turnsAfterEmptyDrawStack = -1
        var turns = 0

        while (!refreshable.refreshAfterGameEndCalled) {
            // Notbremse: falls die Endbedingung mal kaputtgeht, bricht der Test ab statt ewig zu laufen
            check(turns++ < MAX_TURNS) { "Spiel endet nicht" }
            if (turnsAfterEmptyDrawStack >= 0) turnsAfterEmptyDrawStack++
            playTurn(game)
            // Der wichtigste Check: es darf nie eine Karte verloren gehen oder doppelt auftauchen
            assertEquals(52, countAllCards(game))
            if (game.drawStack.isEmpty() && turnsAfterEmptyDrawStack < 0) turnsAfterEmptyDrawStack = 0
            rootService.gameService.endTurn()
        }

        assertTrue(game.drawStack.isEmpty())
        assertEquals(names.size - 1, turnsAfterEmptyDrawStack, "Jede andere Fakultät spielt genau einmal")
        val scores = rootService.gameService.calculateFinalScores()
        assertEquals(names.size, scores.size)
        assertTrue(scores.values.zipWithNext().all { (better, worse) -> better >= worse }, "Rangliste sortiert")
        game.faculties.forEach { assertTrue(scores.getValue(it) >= it.researchPoints) }
    }

    /**
     * Einfache Strategie: publizieren, sobald ein Stapel 3 Karten hat; sonst Handkarte ausspielen;
     * sonst neue Idee; sonst Literatur nutzen. Am Ende wird der Zug immer beendet.
     *
     * @param game das laufende Spiel
     */
    private fun playTurn(game: FacultyGame) {
        val actions = rootService.playerActionService
        val faculty: Faculty = game.faculties[game.facultyAtTurn]
        while (faculty.actionPoints > 0) {
            // Simple Strategie: muss nicht gut spielen, soll nur alle Aktionen oft genug auslösen
            val fullStack = faculty.researchActivity.entries.firstOrNull { it.value.size >= PUBLISH_AT }
            when {
                fullStack != null && faculty.actionPoints >= 2 -> actions.publish(fullStack.key)
                faculty.handCards.isNotEmpty() -> actions.advanceResearch(faculty.handCards.first())
                game.drawStack.isNotEmpty() -> actions.gainNewIdeas()
                game.centralLiterature.isNotEmpty() && faculty.actionPoints >= 2 ->
                    actions.useLiterature(game.centralLiterature.first())
                // Nichts mehr möglich (z. B. 1 AP und leerer Stapel) -> Zug einfach beenden
                else -> return
            }
        }
    }

    /** Konstanten des Testablaufs. */
    private companion object {
        const val MAX_TURNS = 500
        const val PUBLISH_AT = 3
        const val REPETITIONS = 20
    }
}
