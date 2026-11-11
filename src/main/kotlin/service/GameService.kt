package service

import entity.Faculty
import entity.FacultyGame
import entity.ResearchArea
import entity.ResearchCard
import entity.ResearchTopic

/**
 * Service layer class that handles game setup, turn changes and the final scoring
 * of "Exzellente Informatik-Forschung".
 *
 * @param rootService The [RootService] that gives access to the current game and the other services.
 */
class GameService(private val rootService: RootService) : AbstractRefreshingService() {
    /**
     * Starts a new game with the given faculties and prepares the board:
     * - the shuffled 52-card deck becomes the draw stack
     * - every faculty gets 5 hand cards
     * - 6 cards are placed in the central literature
     *
     * The first faculty in the (possibly randomized) list starts.
     *
     * @param facultyNames Names of the 2 to 4 faculties, in the desired turn order.
     * @param randomizeOrder If true, the turn order is shuffled instead of using the given order.
     *
     * @throws IllegalArgumentException if there are not 2 to 4 names, a name is blank,
     * or two names are the same.
     */
    fun startNewGame(facultyNames: List<String>, randomizeOrder: Boolean = false) {
        require(facultyNames.size in MIN_FACULTIES..MAX_FACULTIES) {
            "The game needs 2 to 4 faculties."
        }
        require(facultyNames.none { it.isBlank() }) {
            "Faculty names must not be blank."
        }
        val trimmedNames = facultyNames.map { it.trim() }
        require(trimmedNames.distinct().size == trimmedNames.size) {
            "Faculty names must be unique."
        }

        // Turn order: either as configured or randomized
        val orderedNames = if (randomizeOrder) trimmedNames.shuffled() else trimmedNames
        val faculties = orderedNames.map { Faculty(it) }

        val game = FacultyGame(faculties)

        // Draw stack: shuffled standard deck
        game.drawStack.addAll(createDeck())

        // Deal 5 hand cards to every faculty, one card per faculty per round
        repeat(HAND_CARDS_AT_START) {
            for (faculty in game.faculties) {
                faculty.handCards.add(game.drawStack.pop())
            }
        }

        // Central literature: 6 open cards from the draw stack
        repeat(CENTRAL_LITERATURE_SIZE) {
            game.centralLiterature.add(game.drawStack.pop())
        }
        // Save the new game so that all services can access it
        rootService.currentGame = game

        // Tell the GUI that a new game has started so it can show the board
        onAllRefreshables { refreshAfterStartNewGame() }
        log("Neues Spiel: ${orderedNames.joinToString()} – ${orderedNames.first()} beginnt")

        // Start the turn of the first faculty
        startTurn()
    }

    /**
     * Creates a shuffled deck with 52 cards
     * (4 research areas x 13 research topics).
     *
     * @return A list of 52 shuffled research cards.
     */
    private fun createDeck(): List<ResearchCard> {
        val deck = mutableListOf<ResearchCard>()

        for (area in ResearchArea.entries) {
            for (topic in ResearchTopic.entries) {
                deck.add(ResearchCard(topic, area))
            }
        }
        // Shuffle the cards so the order is random
        deck.shuffle()
        return deck
    }

    /**
     * Starts the turn of the faculty at turn: it gets 2 action points.
     *
     * @throws IllegalStateException if no game is running.
     */
    fun startTurn() {
        val game = checkNotNull(rootService.currentGame) { "No game is running." }
        check(game.endgameTurnsLeft != 0) { "The game is already over." }
        val faculty = game.faculties[game.facultyAtTurn]
        faculty.resetActionPoints()

        onAllRefreshables { refreshAfterStartTurn() }
        log("${faculty.name} ist am Zug (${faculty.actionPoints} AP)")
    }

    /**
     * Ends the turn of the current faculty.
     *
     * - If the draw stack is empty for the first time, the endgame starts:
     *   every other faculty gets exactly one more turn.
     * - If the last of those turns has just ended, the game ends with the final scoring.
     * - Otherwise the next faculty (cyclic) starts its turn with 2 action points.
     *
     * @throws IllegalStateException if no game is running or the game is already over.
     */
    fun endTurn() {
        val game = checkNotNull(rootService.currentGame) { "No game is running." }
        // Nach dem Spielende soll niemand aus Versehen noch einen Zug beenden können
        check(game.endgameTurnsLeft != 0) { "The game is already over." }
        val endingFaculty = game.faculties[game.facultyAtTurn]

        // Update the endgame counter
        if (game.endgameTurnsLeft > 0) {
            // One of the last turns is over
            game.endgameTurnsLeft--
        } else if (game.endgameTurnsLeft == NO_ENDGAME && game.drawStack.isEmpty()) {
            // Draw stack just became empty: every other faculty plays one more time
            game.endgameTurnsLeft = game.faculties.size - 1
            log("Nachziehstapel leer – alle anderen Fakultäten haben noch einen letzten Zug.")
        }

        // The very last turn is over -> game end
        if (game.endgameTurnsLeft == 0) {
            endGame()
            return
        }

        // Otherwise: next faculty (cyclic)
        game.nextFaculty()
        val nextFaculty = game.faculties[game.facultyAtTurn]

        onAllRefreshables { refreshAfterEndTurn() }
        log("${endingFaculty.name} beendet den Zug – ${nextFaculty.name} ist dran.")

        // Gives the next faculty 2 AP and sends refreshAfterStartTurn()
        startTurn()
    }

    /**
     * Bricht das laufende Spiel ab, z. B. über „Zurück zum Menü“. Danach läuft kein Spiel mehr,
     * die GUI räumt über [Refreshable.refreshAfterQuitGame] auf.
     *
     * @throws IllegalStateException wenn kein Spiel läuft
     */
    fun quitGame() {
        checkNotNull(rootService.currentGame) { "No game is running." }
        rootService.currentGame = null
        onAllRefreshables { refreshAfterQuitGame() }
    }

    /**
     * Ends the game: calculates the final scores, determines the winner
     * and notifies all refreshables.
     */
    private fun endGame() {
        // 1. Scores aus der bestehenden Methode abrufen
        val scores = calculateFinalScores()
        // Bei Gleichstand teilen sich alle Fakultäten mit den meisten Punkten den ersten Platz
        val bestScore = scores.values.max()
        val winners = scores.filterValues { it == bestScore }.keys.toList()

        // 2. Boni für die Detailtabelle im Endgame-Menü einmalig berechnen
        val game = checkNotNull(rootService.currentGame) { "No game is running." }
        val bonuses = game.faculties.associateWith { mutableListOf<Pair<String, Int>>() }

        // Bestehende Hilfsmethoden aufrufen, um die Boni zuzuordnen
        addVisibilityBonuses(game.faculties, bonuses)
        for (faculty in game.faculties) {
            addFacultyBonuses(faculty, bonuses.getValue(faculty))
        }

        log("Spielende – Sieg: ${winners.joinToString { it.name }} mit $bestScore Punkten.")
        onAllRefreshables {
            // Standardmäßiges Spielende für andere Komponenten aufrufen
            refreshAfterGameEnd(winners, scores)
            // Detaillierte Boni an die Endgame-Menü-Szene übergeben
            refreshAfterGameEndWithBonuses(winners.first(), scores, bonuses)
        }
    }

    /**
     * Calculates the final scores: the research points from publications plus all
     * end-of-game bonuses. The entity layer is not changed, so calling this method
     * several times always returns the same result.
     *
     * Bonuses:
     * - Visibility (per research area, only publications): most cards +5, second most +2
     * - Broad positioning: at least 1 publication in all 4 areas +3
     * - Research focus: 8 or more published cards in one area +4
     * - Started research: +1 for every card on the 4 research stacks
     *
     * @return The final score of every faculty, sorted from best to worst
     * (equal points share the place, turn order is kept).
     * @throws IllegalStateException if no game is running.
     */
    fun calculateFinalScores(): Map<Faculty, Int> {
        val game = checkNotNull(rootService.currentGame) { "No game is running." }

        // Collect every bonus as (description, points) per faculty
        val bonuses = game.faculties.associateWith { mutableListOf<Pair<String, Int>>() }

        addVisibilityBonuses(game.faculties, bonuses)
        for (faculty in game.faculties) {
            addFacultyBonuses(faculty, bonuses.getValue(faculty))
        }
        // Bewusst nicht in researchPoints zurückschreiben, sonst würden die Boni bei jedem Aufruf doppelt zählen
        val scores = game.faculties.associateWith { faculty ->
            faculty.researchPoints + bonuses.getValue(faculty).sumOf { it.second }
        }

        // Ranking: most points first; equal points share the place, turn order stays (stable sort)
        val ranking = game.faculties.sortedByDescending { scores.getValue(it) }

        for (faculty in ranking) {
            val details = bonuses.getValue(faculty)
                .joinToString { "${bonusName(it.first)} +${it.second}" }
                .ifEmpty { "kein Bonus" }
            log("Endwertung: ${faculty.name} ${scores.getValue(faculty)} Punkte ($details)")
        }
        // associateWith keeps the order, so the map is sorted from best to worst
        return ranking.associateWith { scores.getValue(it) }
    }

    /**
     * Deutscher Name eines Bonus für das Log. Die Schlüssel selbst bleiben englisch,
     * weil die Endszene sie auswertet.
     *
     * @param key z. B. „majority AI“ oder „broad positioning“
     * @return z. B. „Mehrheit KI“
     */
    private fun bonusName(key: String): String = when {
        key.startsWith("majority ") -> "Mehrheit " + areaName(ResearchArea.valueOf(key.removePrefix("majority ")))
        key.startsWith("second ") -> "Zweitmeiste " + areaName(ResearchArea.valueOf(key.removePrefix("second ")))
        key == "broad positioning" -> "breite Aufstellung"
        key == "research focus" -> "Forschungsschwerpunkt"
        key == "started research" -> "angefangene Forschung"
        else -> key
    }

    /**
     * Adds the visibility bonuses (majorities) for every research area.
     * Only published cards count. Most cards: +5, second most: +2.
     * If several faculties share the most cards, all of them get +5 and nobody gets +2.
     *
     * @param faculties All faculties of the game.
     * @param bonuses The bonus list of every faculty, which is extended here.
     */
    private fun addVisibilityBonuses(
        faculties: List<Faculty>,
        bonuses: Map<Faculty, MutableList<Pair<String, Int>>>
    ) {
        for (area in ResearchArea.entries) {
            val counts = faculties.associateWith { faculty ->
                faculty.publications.count { it.suit == area }
            }
            val best = counts.values.maxOrNull() ?: 0
            //wenn keine Fakultät published hat in dem Bereich: keiner bekommt Bonus
            if (best == 0) continue

            val leaders = counts.filterValues { it == best }.keys
            leaders.forEach { bonuses.getValue(it).add("majority $area" to VISIBILITY_FIRST_BONUS) }

            val second = counts.values.filter { it in 1 until best }.maxOrNull()
            if (leaders.size == 1 && second != null) {
                counts.filterValues { it == second }.keys
                    .forEach { bonuses.getValue(it).add("second $area" to VISIBILITY_SECOND_BONUS) }
            }
        }
    }


    /**
     * Adds the bonuses that only depend on one faculty:
     * broad positioning, research focus and started research.
     *
     * @param faculty The faculty to check.
     * @param facultyBonuses The bonus list of this faculty, which is extended here.
     */
    private fun addFacultyBonuses(faculty: Faculty, facultyBonuses: MutableList<Pair<String, Int>>) {
        val publishedPerArea = ResearchArea.entries.map { area ->
            faculty.publications.count { it.suit == area }
        }

        if (publishedPerArea.all { it > 0 }) {
            facultyBonuses.add("broad positioning" to BROAD_POSITIONING_BONUS)
        }
        if (publishedPerArea.any { it >= RESEARCH_FOCUS_MIN_CARDS }) {
            facultyBonuses.add("research focus" to RESEARCH_FOCUS_BONUS)
        }

        val startedResearch = faculty.researchActivity.values.sumOf { it.size }
        if (startedResearch > 0) {
            facultyBonuses.add("started research" to startedResearch)
        }
    }

    /** Constants for the game rules, so detekt does not flag magic numbers. */
    private companion object {
        const val MIN_FACULTIES = 2
        const val MAX_FACULTIES = 4
        const val HAND_CARDS_AT_START = 5
        const val CENTRAL_LITERATURE_SIZE = 6
        const val NO_ENDGAME = -1
        const val VISIBILITY_FIRST_BONUS = 5
        const val VISIBILITY_SECOND_BONUS = 2
        const val BROAD_POSITIONING_BONUS = 3
        const val RESEARCH_FOCUS_BONUS = 4
        const val RESEARCH_FOCUS_MIN_CARDS = 8
    }
}
