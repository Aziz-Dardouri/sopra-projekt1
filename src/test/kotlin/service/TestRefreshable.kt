package service

import entity.Faculty
import entity.ResearchArea
import entity.ResearchCard

/**
 * Refreshable für Tests, welcher sich merkt, welche refresh-Methoden aufgerufen wurden.
 * Ermöglicht, dass jeder Service-Test prüfen kann ob Aktualisierung korrekt ausgelöst wurde.
 */
class TestRefreshable : Refreshable {

    /**
     * true sofern refreshAfterStartNewGame aufgerufen wurde.
     */
    var refreshAfterStartNewGameCalled = false
        private set

    /**
     * true sofern refreshAfterStartTurn aufgerufen wurde.
     */
    var refreshAfterStartTurnCalled = false
        private set

    /**
     * true sofern refreshAfterEndTurn aufgerufen wurde.
     */
    var refreshAfterEndTurnCalled = false
        private set

    /**
     * true sofern refreshAfterAdvanceResearch aufgerufen wurde.
     */
    var refreshAfterAdvanceResearchCalled = false
        private set

    /**
     * true sofern refreshAfterGainNewIdeas aufgerufen wurde.
     */
    var refreshAfterGainNewIdeasCalled = false
        private set

    /**
     * true sofern refreshAfterStartAdaptStrategy aufgerufen wurde.
     */
    var refreshAfterStartAdaptStrategyCalled = false
        private set

    /**
     * true sofern refreshAfterFinishAdaptStrategy aufgerufen wurde.
     */
    var refreshAfterFinishAdaptStrategyCalled = false
        private set

    /**
     * true sofern refreshAfterPublish aufgerufen wurde.
     */
    var refreshAfterPublishCalled = false
        private set

    /**
     * true sofern refreshAfterSlowDownCompetition aufgerufen wurde.
     */
    var refreshAfterSlowDownCompetitionCalled = false
        private set

    /**
     * true sofern refreshAfterUseLiterature aufgerufen wurde.
     */
    var refreshAfterUseLiteratureCalled = false
        private set

    /**
     * true sofern refreshAfterGameEnd aufgerufen wurde.
     */
    var refreshAfterGameEndCalled = false
        private set

    /**
     * Gewinner*innen aus dem letzten refreshAfterGameEnd, null sofern das Spiel noch nicht geendet hat.
     */
    var lastWinners: List<Faculty>? = null
        private set

    /**
     * true sofern refreshAfterLog aufgerufen wurde.
     */
    var refreshAfterLogCalled = false
        private set

    /**
     * Zuletzt geloggte Nachricht, null sofern noch nichts geloggt wurde.
     */
    var lastLogMessage: String? = null
        private set

    /**
     * Setzt alle Werte wieder auf false bzw. null
     */
    fun resetVars() {
        refreshAfterStartNewGameCalled = false
        refreshAfterStartTurnCalled = false
        refreshAfterEndTurnCalled = false
        refreshAfterAdvanceResearchCalled = false
        refreshAfterGainNewIdeasCalled = false
        refreshAfterStartAdaptStrategyCalled = false
        refreshAfterFinishAdaptStrategyCalled = false
        refreshAfterPublishCalled = false
        refreshAfterSlowDownCompetitionCalled = false
        refreshAfterUseLiteratureCalled = false
        refreshAfterGameEndCalled = false
        lastWinners = null
        refreshAfterLogCalled = false
        lastLogMessage = null
    }

    override fun refreshAfterStartNewGame() {
        refreshAfterStartNewGameCalled = true
    }

    override fun refreshAfterStartTurn() {
        refreshAfterStartTurnCalled = true
    }

    override fun refreshAfterEndTurn() {
        refreshAfterEndTurnCalled = true
    }

    override fun refreshAfterAdvanceResearch(playedCard: ResearchCard, newLiteratureCard: ResearchCard?) {
        refreshAfterAdvanceResearchCalled = true
    }

    override fun refreshAfterGainNewIdeas(drawnCard: ResearchCard) {
        refreshAfterGainNewIdeasCalled = true
    }

    override fun refreshAfterStartAdaptStrategy(drawnCards: List<ResearchCard>) {
        refreshAfterStartAdaptStrategyCalled = true
    }

    override fun refreshAfterFinishAdaptStrategy(keptCard: ResearchCard, discardedCard: ResearchCard) {
        refreshAfterFinishAdaptStrategyCalled = true
    }

    override fun refreshAfterPublish(area: ResearchArea, pointsGained: Int) {
        refreshAfterPublishCalled = true
    }

    override fun refreshAfterSlowDownCompetition(
        targetFaculty: Faculty,
        area: ResearchArea,
        removedCard: ResearchCard
    ) {
        refreshAfterSlowDownCompetitionCalled = true
    }

    override fun refreshAfterUseLiterature(usedCard: ResearchCard) {
        refreshAfterUseLiteratureCalled = true
    }

    override fun refreshAfterGameEnd(winners: List<Faculty>, scores: Map<Faculty, Int>) {
        refreshAfterGameEndCalled = true
        lastWinners = winners
    }

    override fun refreshAfterLog(message: String) {
        refreshAfterLogCalled = true
        lastLogMessage = message
    }
}
