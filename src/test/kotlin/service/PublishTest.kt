package service

import entity.*
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Testklasse für die Aktion [PlayerActionService.publish].
 */
class PublishTest {
    /**
     * Testspiel: "Anna" ist am Zug und hat 2 AP, "Bob" ist die andere Fakultät.
     */
    private lateinit var rootService: RootService
    private lateinit var refreshable: TestRefreshable
    private lateinit var game: FacultyGame
    private lateinit var activeFaculty: Faculty
    private lateinit var otherFaculty: Faculty

    /**
     * Initialisiert vor jedem Test ein neues Testspiel.
     */
    @BeforeTest
    fun setUp() {
        rootService = RootService()
        refreshable = TestRefreshable()
        rootService.addRefreshable(refreshable)
        game = rootService.createTestGame("Anna", "Bob")
        activeFaculty = game.faculties[0]
        otherFaculty = game.faculties[1]
    }

    /**
     * Hilfsfunktion: Legt [count] Karten in das angegebene Gebiet [area] der [faculty].
     *
     * @param faculty Fakultät, deren Stapel gefüllt wird
     * @param area Teilgebiet des Stapels
     * @param count Anzahl der Karten
     */
    private fun addCardsToStack(faculty: Faculty, area: ResearchArea, count: Int) {
        val values = ResearchTopic.entries
        repeat(count) { i ->
            val card = ResearchCard(values[i % values.size], area)
            faculty.addResearch(card)
        }
    }

    /**
     * Publiziert [cardCount] Karten als erste Fakultät im Gebiet und prüft alle Nachbedingungen
     *
     * @param cardCount Anzahl der Karten auf dem Stapel
     * @param expectedBasePoints erwartete Basispunkte
     */
    private fun checkPublish(cardCount: Int, expectedBasePoints: Int) {
        val area = ResearchArea.entries.first()
        addCardsToStack(activeFaculty, area, cardCount)

        var refreshCalled = false
        var refreshedArea: ResearchArea? = null
        var refreshedPoints = 0

        val testRefreshable = object : Refreshable {
            override fun refreshAfterPublish(area: ResearchArea, pointsGained: Int) {
                refreshCalled = true
                refreshedArea = area
                refreshedPoints = pointsGained
            }
        }
        rootService.addRefreshable(testRefreshable)

        // Aktion ausführen (erste Publikation in diesem Gebiet -> +1 Bonus)
        rootService.playerActionService.publish(area)

        val expectedTotal = expectedBasePoints + 1

        // 1. Stapel leer, alle Karten in publications?
        assertTrue(activeFaculty.researchActivity.getValue(area).isEmpty(), "Stapel muss leer sein.")
        assertEquals(cardCount, activeFaculty.publications.size, "Alle Karten müssen in publications liegen.")

        // 2. Punkte (Basis + 1 Bonus) korrekt?
        assertEquals(expectedTotal, activeFaculty.researchPoints, "Punkte für $cardCount Karten falsch.")

        // 3. AP um 2 reduziert?
        assertEquals(0, activeFaculty.actionPoints, "AP müssen um 2 reduziert werden.")

        // 4. Refreshable korrekt ausgelöst?
        assertTrue(refreshCalled, "refreshAfterPublish() muss aufgerufen werden.")
        assertEquals(area, refreshedArea, "Übergebenes Gebiet im Refreshable falsch.")
        assertEquals(expectedTotal, refreshedPoints, "Übergebene Punkte im Refreshable falsch.")

        // 5. Log-Zeile geschrieben???
        assertTrue(refreshable.refreshAfterLogCalled, "Publizieren muss geloggt werden.")

    }

    /**
     * Grenzwert 2 Karten: 3 Punkte + 1 Erstpublikation
     */
    @Test
    fun `test publish with 2 cards`() {
        checkPublish(2, 3)
    }

    /**
     * 3 Karten:  5 Punkte + 1
     */
    @Test
    fun `test publish with 3 cards`() {
        checkPublish(3, 5)
    }

    /**
     * 4 Karten: 7 Punkte + 1
     */
    @Test
    fun `test publish with 4 cards`() {
        checkPublish(4, 7)
    }

    /**
     * Grenzwert 5 Karten:  9 Punkte + 1
     */
    @Test
    fun `test publish with 5 cards`() {
        checkPublish(5, 9)
    }

    /**
     * Mehr als 5 Karten bleiben bei 9 Punkten + 1
     */
    @Test
    fun `test publish with 7 cards`() {
        checkPublish(7, 9)
    }

    /**
     * Kein Zusatzpunkt, wenn eine andere Fakultät im selben Gebiet schon publiziert hat
     */
    @Test
    fun `test no bonus if another faculty already published in same area`() {
        val area = ResearchArea.entries.first()

        // Andere Fakultät hat bereits eine Karte in diesem Gebiet publiziert
        otherFaculty.publications.add(ResearchCard(ResearchTopic.entries.first(), area))
        // Aktive Fakultät hat 3 Karten (Basis: 5 Punkte, Bonus: 0)
        addCardsToStack(activeFaculty, area, 3)

        rootService.playerActionService.publish(area)

        assertEquals(5, activeFaculty.researchPoints, "Es darf keinen Bonus (+1) geben.")
        assertEquals(0, activeFaculty.actionPoints, "AP müssen 0 sein.")
    }
    /**
     * Kein laufendes Spiel: IllegalStateException
     */
    @Test
    fun `test publish fails when no game is running`() {
        rootService.currentGame = null
        val area = ResearchArea.entries.first()

        assertFailsWith<IllegalStateException> {
            rootService.playerActionService.publish(area)
        }
    }

    /**
     * Grenzwert 1 AP: IllegalStateException, der Spielzustand bleibt unverändert.
     */
    @Test
    fun `test publish fails with insufficient AP and keeps state unchanged`() {
        val area = ResearchArea.entries.first()
        activeFaculty.actionPoints = 1
        addCardsToStack(activeFaculty, area, 2)

        assertFailsWith<IllegalStateException> {
            rootService.playerActionService.publish(area)
        }

        // Spielzustand muss unverändert bleiben
        assertEquals(1, activeFaculty.actionPoints, "AP dürfen sich nicht ändern.")
        assertEquals(2, activeFaculty.researchActivity.getValue(area).size, "Stapel muss unverändert bleiben.")
        assertTrue(activeFaculty.publications.isEmpty(), "Publications müssen leer bleiben.")
        assertEquals(0, activeFaculty.researchPoints, "Punkte müssen 0 bleiben.")
        assertFalse(refreshable.refreshAfterPublishCalled, "Kein Refresh bei Fehler.")
    }

    /**
     * Grenzwert 1 Karte: IllegalArgumentException, der Spielzustand bleibt unverändert.
     */
    @Test
    fun `test publish fails with fewer than 2 cards and keeps state unchanged`() {
        val area = ResearchArea.entries.first()
        addCardsToStack(activeFaculty, area, 1) // Nur 1 Karte auf dem Stapel

        assertFailsWith<IllegalArgumentException> {
            rootService.playerActionService.publish(area)
        }

        // Spielzustand muss unverändert bleiben
        assertEquals(2, activeFaculty.actionPoints, "AP dürfen sich nicht ändern.")
        assertEquals(1, activeFaculty.researchActivity.getValue(area).size, "Stapel muss unverändert bleiben.")
        assertTrue(activeFaculty.publications.isEmpty(), "Publications müssen leer bleiben.")
        assertEquals(0, activeFaculty.researchPoints, "Punkte müssen 0 bleiben.")
        assertFalse(refreshable.refreshAfterPublishCalled, "Kein Refresh bei Fehler.")
    }

    /**
     * Spiel ist vorbei: IllegalStateException, der Spielzustand bleibt unverändert.
     */
    @Test
    fun `test publish fails after game end`() {
        val area = ResearchArea.AI
        addCardsToStack(activeFaculty, area, 2)
        game.endgameTurnsLeft = 0

        assertFailsWith<IllegalStateException> {
            rootService.playerActionService.publish(area)
        }

        assertEquals(2, activeFaculty.actionPoints)
        assertEquals(2, activeFaculty.researchActivity.getValue(area).size)
        assertTrue(activeFaculty.publications.isEmpty())
        assertFalse(refreshable.refreshAfterPublishCalled)
    }
}