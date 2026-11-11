package service
import entity.Faculty
import entity.ResearchArea
import entity.ResearchCard

/**
 * This interface provides a mechanism for the service layer classes to communicate
 * (usually to the GUI classes) that certain changes have been made to the entity
 * layer, so that the user interface can be updated accordingly.
 *
 * Default (empty) implementations are provided for all methods, so that implementing
 * GUI classes only need to react to events relevant to them.
 *
 * @see AbstractRefreshingService
 */
interface Refreshable {
    /**
     * Wird nach dem Start eines neuen Spiels aufgerufen.
     */
    fun refreshAfterStartNewGame(){}

    /**
     * Wird zu Beginn des Zugs einer Fakultät aufgerufen.
     */
    fun refreshAfterStartTurn(){}

    /**
     * Wird aufgerufen, wenn der Zug einer Fakultät endet.
     */
    fun refreshAfterEndTurn(){}

    /**
     * Wird nach "Forschung vorantreiben" aufgerufen.
     * @param playedCard die von der Hand gespielte Karte
     * @param newLiteratureCard die neu in die Literatur gelegte Karte, "null" bei leerem Nachziehstapel
     */
    fun refreshAfterAdvanceResearch(playedCard: ResearchCard, newLiteratureCard: ResearchCard?) {}

    /**
     * Wird nach "neue Idee gewinnen" aufgerufen.
     *
     * @param drawnCard die gezogene Karte
     */
    fun refreshAfterGainNewIdeas(drawnCard: ResearchCard) {}

    /**
     * Wird nach dem ersten Schritt von "Strategie anpassen" aufgerufen
     *
     * @param drawnCards die beiden aufgedeckten Karten, die oberste geht zurück
     */
    fun refreshAfterStartAdaptStrategy(drawnCards: List<ResearchCard>) {}

    /**
     * Wird nach dem zweiten Schritt von "Strategie anpassen" aufgerufen
     *
     * @param keptCard die auf die Hand genommene Karte
     * @param discardedCard die abgeworfene Karte
     */
    fun refreshAfterFinishAdaptStrategy(keptCard: ResearchCard, discardedCard: ResearchCard) {}

    /**
     * Wird nach "Publizieren" aufgerufen
     *
     * @param area das publizierte Teilgebiet
     * @param pointsGained erhaltene Punkte inklusive Erstpublikations-Bonus
     */
    fun refreshAfterPublish(area: ResearchArea, pointsGained: Int) {}

    /**
     * Wird nach "Konkurrenz ausbremsen" aufgerufen
     *
     * @param targetFaculty die ausgebremste Fakultät
     * @param area das Teilgebiet des betroffenen Stapels
     * @param removedCard die entfernte Karte
     */
    fun refreshAfterSlowDownCompetition(targetFaculty: Faculty, area: ResearchArea, removedCard: ResearchCard) {}

    /**
     * Wird nach "Literatur nutzen" aufgerufen
     *
     * @param usedCard die aus der Literatur genommene Karte
     */
    fun refreshAfterUseLiterature(usedCard: ResearchCard) {}

    /**
     * Wird aufgerufen, wenn das Spiel vorbei ist
     *
     * @param winners alle Fakultäten auf dem 1. Platz, bei Gleichstand mehrere (in Zugreihenfolge)
     * @param scores Endpunkte aller Fakultäten, descending order
     */
    fun refreshAfterGameEnd(winners: List<Faculty>, scores: Map<Faculty, Int>) {}

    /**
     * Wird aufgerufen, wenn das Spiel vorbei ist, inklusive der detaillierten Boni für die Bestenliste.
     *
     * @param winner die Fakultät auf dem 1. Platz
     * @param scores Endpunkte aller Fakultäten
     * @param bonuses Detaillierte Liste der erhaltenen Boni pro Fakultät
     */
    fun refreshAfterGameEndWithBonuses(winner: Faculty, scores: Map<Faculty, Int>,
        bonuses: Map<Faculty, List<Pair<String, Int>>>
    ) {}

    /**
     * Wird aufgerufen, wenn das laufende Spiel abgebrochen wurde ([GameService.quitGame]).
     * Es läuft danach kein Spiel mehr.
     */
    fun refreshAfterQuitGame() {}

    /**
     * Wird für jeden Eintrag des textuellen Logs aufgerufen.
     *
     * @param message lesbare Beschreibung der Aktion
     */
    fun refreshAfterLog(message: String) {}

}