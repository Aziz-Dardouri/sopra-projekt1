package service

import entity.Faculty
import entity.ResearchArea
import entity.ResearchTopic
import kotlin.test.Test
import org.junit.jupiter.api.assertDoesNotThrow

/**
 * Tests für die leeren Default-Implementierungen in [Refreshable].
 * GUI-Szenen überschreiben nur die Methoden, die sie brauchen; der Rest muss gefahrlos aufrufbar sein.
 */
class RefreshableTest {

    /**
     * Alle Default-Methoden lassen sich ohne Exception aufrufen.
     */
    @Test
    fun testDefaultMethodsDoNothing() {
        // Leeres Objekt: nutzt nur die Default-Implementierungen aus dem Interface
        val refreshable = object : Refreshable {}
        val faculty = Faculty("A")
        val researchCard = card(ResearchTopic.ACE, ResearchArea.AI)

        assertDoesNotThrow {
            refreshable.refreshAfterStartNewGame()
            refreshable.refreshAfterStartTurn()
            refreshable.refreshAfterEndTurn()
            refreshable.refreshAfterAdvanceResearch(researchCard, null)
            refreshable.refreshAfterGainNewIdeas(researchCard)
            refreshable.refreshAfterStartAdaptStrategy(listOf(researchCard))
            refreshable.refreshAfterFinishAdaptStrategy(researchCard, researchCard)
            refreshable.refreshAfterPublish(ResearchArea.AI, 1)
            refreshable.refreshAfterSlowDownCompetition(faculty, ResearchArea.AI, researchCard)
            refreshable.refreshAfterUseLiterature(researchCard)
            refreshable.refreshAfterGameEnd(listOf(faculty), mapOf(faculty to 0))
            refreshable.refreshAfterLog("log")
        }
    }
}
