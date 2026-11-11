package service

import entity.ResearchArea
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Tests für [GameService.calculateFinalScores]: alle vier Boni, Rangfolge und Gleichstand.
 */
class FinalScoresTest {
    private lateinit var rootService: RootService

    /**
     * Neuer RootService vor jedem Test.
     */
    @BeforeTest
    fun setUp() {
        rootService = RootService()
    }

    /**
     * Ohne Publikationen und Stapel gibt es keine Boni, nur die vorhandenen Forschungspunkte zählen
     */
    @Test
    fun testNoBonus() {
        val game = rootService.createTestGame("A", "B")
        game.faculties[0].researchPoints = 4

        val scores = rootService.gameService.calculateFinalScores()

        assertEquals(4, scores.getValue(game.faculties[0]))
        assertEquals(0, scores.getValue(game.faculties[1]))
    }

    /**
     * Sichtbarkeit: meiste Karten +5, zweitmeiste +2, pro Teilgebiet.
     */
    @Test
    fun testVisibilityFirstAndSecond() {
        val (a, b, c) = rootService.createTestGame("A", "B", "C").faculties
        // AI: a hat die meisten (+5), b die zweitmeisten (+2), c geht leer aus
        publishCards(a, ResearchArea.AI, 3)
        publishCards(b, ResearchArea.AI, 2)
        publishCards(c, ResearchArea.AI, 1)

        val scores = rootService.gameService.calculateFinalScores()

        assertEquals(5, scores.getValue(a))
        assertEquals(2, scores.getValue(b))
        assertEquals(0, scores.getValue(c))
    }

    /**
     * Gleichstand bei den meisten Karten: alle Führenden +5, niemand bekommt +2
     */
    @Test
    fun testVisibilityTie() {
        val (a, b, c) = rootService.createTestGame("A", "B", "C").faculties
        publishCards(a, ResearchArea.SE, 2)
        publishCards(b, ResearchArea.SE, 2)
        publishCards(c, ResearchArea.SE, 1)

        val scores = rootService.gameService.calculateFinalScores()

        assertEquals(5, scores.getValue(a))
        assertEquals(5, scores.getValue(b))
        assertEquals(0, scores.getValue(c))
    }

    /**
     * Breite Aufstellung (+3) bei Publikationen in allen 4 Gebieten; dazu je Gebiet + 5 Mehrheit
     */
    @Test
    fun testBroadPositioning() {
        val (a, b) = rootService.createTestGame("A", "B").faculties
        ResearchArea.entries.forEach { publishCards(a, it, 1) }
        ResearchArea.entries.take(3).forEach { publishCards(b, it, 2) }

        val scores = rootService.gameService.calculateFinalScores()

        // a: Mehrheit nur in THEORETICAL (+5), in den anderen 3 Gebieten zweiter (+2 je), breit (+3)
        assertEquals(5 + 3 * 2 + 3, scores.getValue(a))
        // b: Mehrheit in 3 Gebieten, nicht breit aufgestellt
        assertEquals(3 * 5, scores.getValue(b))
    }

    /**
     * Forschungsschwerpunkt (+4) ab 8 Karten in einem Gebiet, genau einmal
     */
    @Test
    fun testResearchFocus() {
        val (a, b) = rootService.createTestGame("A", "B").faculties
        // a knapp unter der Grenze (7), b hat zwei Gebiete mit 8 -> trotzdem nur einmal +4
        publishCards(a, ResearchArea.AI, 7)
        publishCards(b, ResearchArea.SE, 8)
        publishCards(b, ResearchArea.IT_SECURITY, 8)

        val scores = rootService.gameService.calculateFinalScores()

        assertEquals(5, scores.getValue(a))
        assertEquals(5 + 5 + 4, scores.getValue(b))
    }

    /**
     * Angefangene Forschung: +1 je Karte auf den Forschungsstapeln, Handkarten zählen nicht.
     */
    @Test
    fun testStartedResearch() {
        val (a, _) = rootService.createTestGame("A", "B").faculties
        fillStack(a, ResearchArea.AI, 3)
        fillStack(a, ResearchArea.SE, 1)
        // Handkarten zählen nicht als angefangene Forschung, nur die offenen Stapel
        a.handCards.addAll(cardsOf(ResearchArea.THEORETICAL, 4))

        assertEquals(4, rootService.gameService.calculateFinalScores().getValue(a))
    }

    /**
     * Rangfolge: absteigend nach Punkten; bei Gleichstand teilen sich die Fakultäten den Platz
     * und bleiben in Zugreihenfolge
     */
    @Test
    fun testRankingAndTie() {
        val (a, b, c) = rootService.createTestGame("A", "B", "C").faculties
        a.researchPoints = 10
        b.researchPoints = 15
        c.researchPoints = 15

        assertEquals(listOf(b, c, a), rootService.gameService.calculateFinalScores().keys.toList())

        // Beide AI, c 2 Karten (+5), b 1 Karte (+2)
        b.researchPoints = 18
        publishCards(b, ResearchArea.AI, 1)
        publishCards(c, ResearchArea.AI, 2)
        // b: 18 + 2 = 20, c: 15 + 5 = 20 → Gleichstand; mehr Publikationen bei c entscheiden nicht
        val scores = rootService.gameService.calculateFinalScores()
        assertEquals(listOf(b, c, a), scores.keys.toList())
        assertEquals(20, scores.getValue(b))
        assertEquals(20, scores.getValue(c))
    }

    /**
     * Mehrfacher Aufruf liefert dasselbe Ergebnis und verändert die Forschungspunkte nicht.
     */
    @Test
    fun testCalculateFinalScoresIsRepeatable() {
        val (a, _) = rootService.createTestGame("A", "B").faculties
        a.researchPoints = 7
        publishCards(a, ResearchArea.AI, 2)

        val first = rootService.gameService.calculateFinalScores()
        val second = rootService.gameService.calculateFinalScores()

        assertEquals(first, second)
        assertEquals(7, a.researchPoints)
    }

    /**
     * Ohne Spiel wirft calculateFinalScores eine IllegalStateException.
     */
    @Test
    fun testWithoutGame() {
        assertFailsWith<IllegalStateException> { rootService.gameService.calculateFinalScores() }
    }
}