package service

import entity.ResearchArea
import entity.ResearchCard
import entity.ResearchTopic
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Tests für [PlayerActionService.advanceResearch].
 */
class AdvanceResearchTest {

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
        rootService.createTestGame("Dortmund", "Bochum")
    }

    /**
     * Erfolg: Karte von der Hand auf den passenden Stapel, oberste Nachziehkarte in Literatur
     */
    @Test
    fun testAdvanceResearchMovesCard() {
        val game = checkNotNull(rootService.currentGame)
        val active = game.faculties[0]
        val handCard = ResearchCard(ResearchTopic.EIGHT, ResearchArea.IT_SECURITY)
        val drawCard = ResearchCard(ResearchTopic.KING, ResearchArea.AI)
        active.handCards.add(handCard)
        // Diese Karte muss danach als neueste in der Literatur landen
        game.drawStack.push(drawCard)

        //Aktion
        rootService.playerActionService.advanceResearch(handCard)

        assertFalse(handCard in active.handCards)
        assertSame(handCard, active.researchActivity.getValue(ResearchArea.IT_SECURITY).peek())
        assertSame(drawCard, game.centralLiterature.last())
        assertEquals(1, active.actionPoints)
        assertTrue(refreshable.refreshAfterAdvanceResearchCalled)
        assertTrue(refreshable.refreshAfterLogCalled)
    }

    /**
     * Fall: 6 Literaturkarten, wobei die älteste (Index 0) auf den Ablagestapel kommt.
     */

    @Test
    fun testAdvanceResearchRemovesOldestLiterature() {
        val game = checkNotNull(rootService.currentGame)
        val active = game.faculties[0]
        //Literatur voll, um rauswurd der Ältesten Karte zu erwzweingen
        val literature = List(6){
            ResearchCard(ResearchTopic.entries[it], ResearchArea.SE)
        }
        game.centralLiterature.addAll(literature)
        val handCard = ResearchCard(ResearchTopic.JACK, ResearchArea.AI)
        active.handCards.add(handCard)
        game.drawStack.push(ResearchCard(ResearchTopic.KING, ResearchArea.THEORETICAL))
        //Aktion
        rootService.playerActionService.advanceResearch(handCard)

        assertEquals(6, game.centralLiterature.size)
        assertSame(literature[1], game.centralLiterature.first())
        assertSame(literature[0], game.discardStack.peek())
    }

    /**
     * Leerer Nachziehstapel: die Karte wird trotzdem gespielt, die Literatur bleibt unverändert
     * und der Refresh meldet `null` als neue Literaturkarte.
     */
    @Test
    fun testAdvanceResearchWithEmptyDrawStack(){
        val game = checkNotNull(rootService.currentGame)
        val active = game.faculties[0]
        val handCard = ResearchCard(ResearchTopic.JACK, ResearchArea.AI)
        active.handCards.add(handCard)
        val literatureBefore = listOf(ResearchCard(ResearchTopic.KING, ResearchArea.IT_SECURITY))
        game.centralLiterature.addAll(literatureBefore)

        var refreshCalled= false
        var receivedLiteratureCard: ResearchCard? = ResearchCard(ResearchTopic.KING, ResearchArea.THEORETICAL)
        rootService.addRefreshable(object : Refreshable {
            override fun refreshAfterAdvanceResearch(playedCard: ResearchCard, newLiteratureCard: ResearchCard?) {
                refreshCalled = true
                receivedLiteratureCard = newLiteratureCard
            }
        })
        //Aktion
        rootService.playerActionService.advanceResearch(handCard)

        assertSame(handCard, active.researchActivity.getValue(ResearchArea.AI).peek())
        assertEquals(literatureBefore, game.centralLiterature)
        assertTrue(game.discardStack.isEmpty())
        assertEquals(1, active.actionPoints)
        assertTrue (refreshCalled)
        assertNull(receivedLiteratureCard)
        assertTrue(refreshable.refreshAfterLogCalled)

    }
    /**
     * Fehler: karte nicht auf Hand --> IllegalArgumentException, gleicherZustand, kein refresh
     */
    @Test
    fun testAdvanceResearchCardNotInHand(){
        val game = checkNotNull(rootService.currentGame)
        val active = game.faculties[0]

        val foreignCard = ResearchCard(ResearchTopic.THREE, ResearchArea.AI)

        assertFailsWith<IllegalArgumentException>{
            rootService.playerActionService.advanceResearch(foreignCard)
        }
        assertEquals(2 ,active.actionPoints)
        assertTrue(active.researchActivity.values.all { it.isEmpty() })
        assertFalse (refreshable.refreshAfterAdvanceResearchCalled)
    }

    /**
     * Fehler: kein Spiel --> wirft IllegalState
     */
    @Test
    fun testAdvanceResearchNoGame(){
        rootService.currentGame = null

        assertFailsWith<IllegalStateException>{
            rootService.playerActionService.advanceResearch(ResearchCard(ResearchTopic.TWO, ResearchArea.AI))
        }
        assertNull(refreshable.lastLogMessage)
    }

    /**
     * Fehler: keine AP --> IllegalStateException, Karte bleibt auf Hand
     */

    @Test
    fun testAdvanceResearchNoActionPoints(){
        val game = checkNotNull(rootService.currentGame)
        val active = game.faculties[0]
        val handCard = ResearchCard(ResearchTopic.KING, ResearchArea.AI)

        active.handCards.add(handCard)
        active.actionPoints = 0

        assertFailsWith<IllegalStateException> {
            rootService.playerActionService.advanceResearch(handCard)
        }
        assertTrue(handCard in active.handCards)

    }

    /**
     * Fehler: Spiel ist vorbei --> IllegalStateException, der Spielzustand hat sich nicht geändert
     */
    @Test
    fun testAdvanceResearchAfterGameEnd() {
        val game = checkNotNull(rootService.currentGame)
        val active = game.faculties[0]
        val handCard = ResearchCard(ResearchTopic.KING, ResearchArea.AI)
        active.handCards.add(handCard)
        game.endgameTurnsLeft = 0

        assertFailsWith<IllegalStateException> {
            rootService.playerActionService.advanceResearch(handCard)
        }
        assertTrue(handCard in active.handCards)
        assertEquals(2, active.actionPoints)
        assertFalse(refreshable.refreshAfterAdvanceResearchCalled)
    }
}
