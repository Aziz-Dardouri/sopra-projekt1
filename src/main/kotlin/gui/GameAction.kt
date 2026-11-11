package gui

import entity.FacultyGame
import entity.ResearchArea
import entity.ResearchCard
import entity.ResearchTopic
import service.PlayerActionService

private const val MAX_HAND_FOR_NEW_IDEA = 5
private const val MIN_STACK_FOR_PUBLISH = 2
private const val MIN_DRAW_STACK_FOR_ADAPT = 2
private const val MISSING_SELECTION = "Bitte zuerst Karte oder Stapel wählen."

/**
 * Die sechs Aktionen, die eine Fakultät in der [GameScene] wählen kann, jeweils mit ihrem Service-Aufruf.
 * Die Regeln prüfen weiterhin die Services, [isExecutable] entscheidet nur, welche Buttons ausgegraut sind.
 *
 * @property label Text des Aktions-Buttons
 * @property actionPoints AP, die die Aktion kostet
 * @property prompt sagt, was als Nächstes gewählt werden muss
 */
enum class GameAction(val label: String, val actionPoints: Int, val prompt: String) {
    ADVANCE_RESEARCH("Forschung vorantreiben", 1, "Karte aus der Hand wählen"),
    GAIN_NEW_IDEAS("Neue Ideen gewinnen", 1, "Mit Submit eine Karte ziehen"),
    ADAPT_STRATEGY("Strategie anpassen", 1, "Karte aus der Hand zum Abwerfen wählen"),
    PUBLISH("Publizieren", 2, "Eigenen Stapel wählen"),
    SLOW_DOWN_COMPETITION("Konkurrenz ausbremsen", 2, "Stapel einer anderen Fakultät wählen"),
    USE_LITERATURE("Literatur nutzen", 2, "Karte aus der Literatur wählen");

    /**
     * Prüft, ob die aktive Fakultät diese Aktion gerade ausführen kann.
     *
     * @param game das laufende Spiel
     * @return true, wenn der Button aktiv sein soll
     */
    fun isExecutable(game: FacultyGame): Boolean {
        val active = game.faculties[game.facultyAtTurn]
        val isPossible = when (this) {
            ADVANCE_RESEARCH -> active.handCards.isNotEmpty()
            GAIN_NEW_IDEAS -> active.handCards.size <= MAX_HAND_FOR_NEW_IDEA && game.drawStack.isNotEmpty()
            ADAPT_STRATEGY -> active.handCards.isNotEmpty() && game.drawStack.size >= MIN_DRAW_STACK_FOR_ADAPT
            PUBLISH -> active.researchActivity.values.any { it.size >= MIN_STACK_FOR_PUBLISH }
            SLOW_DOWN_COMPETITION -> game.faculties.any { faculty ->
                faculty !== active && faculty.researchActivity.values.any { it.isNotEmpty() }
            }
            USE_LITERATURE -> game.centralLiterature.isNotEmpty()
        }
        return active.actionPoints >= actionPoints && isPossible
    }

    /**
     * Prüft, ob alles gewählt ist, was die Aktion braucht.
     *
     * @param selection aktuelle Auswahl
     * @return true, wenn die Aktion abgeschickt werden kann
     */
    fun isSelectionComplete(selection: ActionSelection): Boolean = when (this) {
        ADVANCE_RESEARCH, ADAPT_STRATEGY -> selection.handCard != null
        GAIN_NEW_IDEAS -> true
        PUBLISH -> selection.ownArea != null
        SLOW_DOWN_COMPETITION -> selection.targetFaculty != null && selection.targetArea != null
        USE_LITERATURE -> selection.literatureCard != null
    }

    /**
     * Beschreibt den nächsten Schritt oder, wenn alles gewählt ist, was Submit gleich macht.
     *
     * @param selection aktuelle Auswahl
     * @return Text für den Hinweis neben der Hand
     */
    fun hint(selection: ActionSelection): String = when (this) {
        ADVANCE_RESEARCH -> selection.handCard?.let { "${it.shortName} gewählt → wird auf ${it.suit.symbol} gelegt" }
        GAIN_NEW_IDEAS -> null
        ADAPT_STRATEGY -> selection.handCard?.let { "${it.shortName} gewählt → wird abgeworfen" }
        PUBLISH -> selection.ownArea?.let { "Stapel ${it.symbol} gewählt → wird publiziert" }
        SLOW_DOWN_COMPETITION -> selection.targetArea?.let { area ->
            selection.targetFaculty?.let { "Stapel ${area.symbol} von ${it.name} gewählt → wird ausgebremst" }
        }
        USE_LITERATURE -> selection.literatureCard?.let {
            "${it.shortName} gewählt → wird auf ${it.suit.symbol} gelegt"
        }
    } ?: prompt

    /**
     * Ruft die Service-Methode der Aktion auf. „Strategie anpassen“ wird hier nur gestartet, die [GameScene]
     * schließt es ab, sobald eine der aufgedeckten Karten gewählt wurde.
     *
     * @param service Service, der die Aktion ausführt
     * @param selection liefert die Parameter für den Service-Aufruf
     * @throws IllegalStateException wenn die Auswahl unvollständig ist oder der Service die Aktion ablehnt
     * @throws IllegalArgumentException wenn der Service die gewählten Parameter ablehnt
     */
    fun execute(service: PlayerActionService, selection: ActionSelection) {
        when (this) {
            ADVANCE_RESEARCH -> service.advanceResearch(checkNotNull(selection.handCard) { MISSING_SELECTION })
            GAIN_NEW_IDEAS -> service.gainNewIdeas()
            ADAPT_STRATEGY -> service.startAdaptStrategy(checkNotNull(selection.handCard) { MISSING_SELECTION })
            PUBLISH -> service.publish(checkNotNull(selection.ownArea) { MISSING_SELECTION })
            SLOW_DOWN_COMPETITION -> service.slowDownCompetition(
                checkNotNull(selection.targetFaculty) { MISSING_SELECTION },
                checkNotNull(selection.targetArea) { MISSING_SELECTION }
            )
            USE_LITERATURE -> service.useLiterature(checkNotNull(selection.literatureCard) { MISSING_SELECTION })
        }
    }
}

/**
 * Kartensymbol eines Forschungsgebiets, gleiche Zuordnung wie in der Legende der [StartMenuScene].
 */
internal val ResearchArea.symbol: String
    get() = when (this) {
        ResearchArea.AI -> "♠"
        ResearchArea.IT_SECURITY -> "♥"
        ResearchArea.SE -> "♦"
        ResearchArea.THEORETICAL -> "♣"
    }

/**
 * Kurzer Kartenname für Texte in der GUI, z. B. „7♦“ oder „K♠“.
 */
private val ResearchCard.shortName: String
    get() {
        val valueName = when (value) {
            ResearchTopic.ACE -> "A"
            ResearchTopic.JACK -> "J"
            ResearchTopic.QUEEN -> "Q"
            ResearchTopic.KING -> "K"
            else -> "${value.ordinal + 1}"
        }
        return valueName + suit.symbol
    }

/**
 * Hinweistext, während eine der zwei aufgedeckten Karten zum Behalten gewählt wird.
 *
 * @param keptCard bisher gewählte Karte, null wenn noch keine
 * @return Text für den Hinweis neben der Hand
 */
internal fun keptCardHint(keptCard: ResearchCard?): String =
    keptCard?.let { "${it.shortName} gewählt → kommt auf die Hand" } ?: "Aufgedeckte Karte zum Behalten wählen"
