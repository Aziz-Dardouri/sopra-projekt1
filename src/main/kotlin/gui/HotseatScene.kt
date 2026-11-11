package gui

import entity.Faculty
import entity.ResearchArea
import entity.ResearchCard
import entity.ResearchTopic
import service.Refreshable
import service.RootService
import tools.aqua.bgw.animation.Animation
import tools.aqua.bgw.animation.MovementAnimation
import tools.aqua.bgw.animation.ParallelAnimation
import tools.aqua.bgw.animation.RotationAnimation
import tools.aqua.bgw.components.uicomponents.Button
import tools.aqua.bgw.components.uicomponents.Label
import tools.aqua.bgw.core.Alignment
import tools.aqua.bgw.core.Color
import tools.aqua.bgw.core.MenuScene
import tools.aqua.bgw.util.Font
import tools.aqua.bgw.visual.ColorVisual

/**
 * Hotseat-Übergabe zwischen zwei Zügen (Issue #23), gestaltet nach dem GUI-Storyboard.
 *
 * Die Szene wird von der [SopraApplication] nach jedem [Refreshable.refreshAfterStartTurn] als
 * deckende [MenuScene] über die Spielszene gelegt:
 * - Keine Handkarten sichtbar, weil der Hintergrund die Spielszene komplett verdeckt.
 * - Die Spielszene ist erst wieder bedienbar, wenn die nächste Fakultät auf [readyButton] klickt.
 *
 * Alle Texte sind variabel und werden aus dem Spielzustand bzw. den Refresh-Aufrufen gebaut:
 * - der Name der nächsten Fakultät ("Weiter an <Name>", "Ich bin <Name> – Zug starten"),
 * - die Zusammenfassung des vorherigen Zugs ("<NAME>S ZUG" und eine Zeile pro Aktion).
 *
 * Die Szene ändert keine Entities und ruft keine Service-Methoden auf, sie liest nur.
 *
 * @param rootService Zugriff auf das laufende Spiel (nur lesend).
 * @param onReady Wird aufgerufen, sobald die nächste Fakultät auf "Zug starten" klickt.
 */
class HotseatScene(
    private val rootService: RootService,
    private val onReady: () -> Unit = {}
) : MenuScene(SCENE_WIDTH, SCENE_HEIGHT), Refreshable {

    /** Eine Zeile pro Aktion der Fakultät, die gerade am Zug ist. */
    private val turnActions: MutableList<String> = mutableListOf()

    /** Name der Fakultät, die zuletzt am Zug war; null direkt nach Spielstart. */
    private var previousFacultyName: String? = null

    /** Abgeworfene Handkarte aus Schritt 1 von "Strategie anpassen", für die Zeile in Schritt 2. */
    private var adaptStrategyDiscarded: ResearchCard? = null

    /**
     * Zähler für Animationsdurchläufe. Startet ein neuer Zug, während noch gemischt wird,
     * ignorieren die alten onFinished-Callbacks sich selbst (sie gehören zu einem alten Durchlauf).
     */
    @Volatile
    private var animationRun = 0

    private val captionLabel = Label(
        posX = CARD_X, posY = CAPTION_Y, width = CARD_WIDTH, height = CAPTION_HEIGHT,
        text = "ZUG BEENDET", font = FONT_CAPTION, alignment = Alignment.CENTER
    )

    /** Erster Teil der Überschrift, z. B. "Weiter an" (rechtsbündig, damit er direkt vor dem Namen endet). */
    private val titleLabel = Label(
        posX = CARD_X, posY = TITLE_Y, width = 0, height = TITLE_HEIGHT,
        text = "", font = FONT_TITLE, alignment = Alignment.CENTER_RIGHT
    )

    /** Zweiter Teil der Überschrift: Name der nächsten Fakultät in Gold. */
    private val nameLabel = Label(
        posX = CARD_X, posY = TITLE_Y, width = 0, height = TITLE_HEIGHT,
        text = "", font = FONT_TITLE_NAME, alignment = Alignment.CENTER_LEFT
    )

    /** Hinweis, Zeile 1 (enthält den Namen). Zwei Labels, weil "\n" in Labels nicht umbricht. */
    private val hintLabel = Label(
        posX = CONTENT_X, posY = HINT_Y, width = CONTENT_WIDTH, height = HINT_LINE_HEIGHT,
        text = "", font = FONT_HINT, alignment = Alignment.CENTER
    )

    /** Hinweis, Zeile 2. */
    private val hintLabel2 = Label(
        posX = CONTENT_X, posY = HINT_Y + HINT_LINE_HEIGHT, width = CONTENT_WIDTH, height = HINT_LINE_HEIGHT,
        text = "Bitte gib das Gerät weiter.", font = FONT_HINT, alignment = Alignment.CENTER
    )

    private val summaryHeaderLabel = Label(
        posX = SUMMARY_TEXT_X, posY = SUMMARY_Y + SUMMARY_PADDING,
        width = SUMMARY_TEXT_WIDTH, height = SUMMARY_HEADER_HEIGHT,
        text = "", font = FONT_SUMMARY_HEADER, alignment = Alignment.CENTER_LEFT
    )

    /** Eine Label-Zeile pro Aktion, weil "\n" in BGW-Labels nicht umbricht. */
    private val summaryLines: List<Label> = List(MAX_SUMMARY_LINES) { index ->
        Label(
            posX = SUMMARY_TEXT_X,
            posY = SUMMARY_Y + SUMMARY_PADDING + SUMMARY_HEADER_HEIGHT + index * SUMMARY_LINE_HEIGHT,
            width = SUMMARY_TEXT_WIDTH, height = SUMMARY_LINE_HEIGHT,
            text = "", font = FONT_SUMMARY_TEXT, alignment = Alignment.CENTER_LEFT
        )
    }

    /**
     * "Ich bin <Name> – Zug starten": erst danach werden Handkarten und Aktionen freigegeben.
     */
    val readyButton: Button = Button(
        posX = CONTENT_X, posY = BUTTON_Y, width = CONTENT_WIDTH, height = BUTTON_HEIGHT,
        text = "Zug starten", font = FONT_BUTTON, visual = ColorVisual(COLOR_GOLD)
    ).apply {
        onMouseClicked = {
            animationRun++
            onReady()
        }
    }

    /**
     * Drei verdeckte Karten oben in der Box (Symbol: Handkarten bleiben verdeckt).
     * Pro Karte zwei Labels: ein heller Rand (first) und die rote Rückseite (second).
     */
    private val cardBacks: List<Pair<Label, Label>> = List(BACK_COUNT) { index ->
        val border = Label(
            posX = backX(index) - BACK_BORDER, posY = BACKS_Y - BACK_BORDER,
            width = BACK_WIDTH + 2 * BACK_BORDER, height = BACK_HEIGHT + 2 * BACK_BORDER,
            visual = ColorVisual(COLOR_BACK_BORDER)
        )
        val back = Label(
            posX = backX(index), posY = BACKS_Y, width = BACK_WIDTH, height = BACK_HEIGHT,
            visual = ColorVisual(COLOR_BACK)
        )
        border to back
    }

    init {
        // Voll deckender Hintergrund: von der Spielszene darunter ist nichts zu sehen.
        background = ColorVisual(COLOR_SCENE_BG)
        addComponents(
            Label(
                posX = CARD_X - BORDER, posY = CARD_Y - BORDER,
                width = CARD_WIDTH + 2 * BORDER, height = CARD_HEIGHT + 2 * BORDER,
                visual = ColorVisual(COLOR_CARD_BORDER)
            ),
            Label(
                posX = CARD_X, posY = CARD_Y, width = CARD_WIDTH, height = CARD_HEIGHT,
                visual = ColorVisual(COLOR_CARD_BG)
            ),
            Label(
                posX = CONTENT_X, posY = SUMMARY_Y, width = CONTENT_WIDTH, height = SUMMARY_HEIGHT,
                visual = ColorVisual(COLOR_SUMMARY_BG)
            )
        )
        addCardBacks()
        addComponents(captionLabel, titleLabel, nameLabel, hintLabel, hintLabel2, summaryHeaderLabel, readyButton)
        summaryLines.forEach { addComponents(it) }
    }

    /** x-Position der Karte [index] im aufgefächerten Endzustand. */
    private fun backX(index: Int): Int {
        val totalWidth = BACK_COUNT * BACK_WIDTH + (BACK_COUNT - 1) * BACK_GAP
        return CARD_X + (CARD_WIDTH - totalWidth) / 2 + index * (BACK_WIDTH + BACK_GAP)
    }

    /** Endwinkel der Karte [index]: links nach links gekippt, Mitte gerade, rechts nach rechts. */
    private fun backAngle(index: Int): Double = (index - 1) * BACK_ROTATION

    private fun addCardBacks() {
        cardBacks.forEach { (border, back) -> addComponents(border, back) }
        cardBacks.forEachIndexed { index, (border, back) ->
            border.rotation = backAngle(index)
            back.rotation = backAngle(index)
        }
    }

    /**
     * Misch-Animation, läuft bei JEDEM Zugwechsel (aufgerufen in [refreshAfterStartTurn]):
     * 1. Sammeln: die drei Karten gleiten zu einem Stapel in der Mitte und richten sich gerade aus.
     * 2. Mischen: nacheinander springt eine Karte mit einer kleinen Drehung aus dem Stapel
     *    (abwechselnd nach links und rechts) und fällt wieder darauf.
     * 3. Auffächern: die Karten verteilen sich auf ihre Plätze und kippen wie im Storyboard.
     *
     * BGW-Animationen bewegen Komponenten nur optisch; die echte Position wird jeweils in
     * onFinished gesetzt. Die Phasen werden über onFinished nacheinander gestartet.
     */
    private fun playShuffleAnimation() {
        val run = ++animationRun
        // Ausgangslage: aufgefächert (so wie die Karten beim letzten Mal liegen geblieben sind)
        for (card in 0 until BACK_COUNT) {
            setCardPosition(card, backX(card).toDouble(), BACKS_Y.toDouble())
            setCardRotation(card, backAngle(card))
        }
        runAnimationAsync(run, delayMs = ANIMATION_START_DELAY) {
            stopAllAnimations()
            Thread.sleep(ANIMATION_STOP_DELAY)
            if (run == animationRun) {
                playGather(run)
            }
        }
    }

    /** Position der Karte im Stapel: leicht versetzt, damit der Stapel Tiefe bekommt. */
    private fun stackX(card: Int): Double = backX(1).toDouble() + (card - 1) * STACK_OFFSET
    private fun stackY(card: Int): Double = BACKS_Y.toDouble() - (card - 1) * STACK_OFFSET

    /** Phase 1: zum Stapel gleiten und gerade ausrichten. */
    private fun playGather(run: Int) {
        val animations = (0 until BACK_COUNT).flatMap { card ->
            cardAnimations(
                card, run, GATHER_DURATION,
                fromX = backX(card).toDouble(), fromY = BACKS_Y.toDouble(), fromAngle = backAngle(card),
                toX = stackX(card), toY = stackY(card), toAngle = 0.0
            )
        }
        playStep(run, animations) { playPop(0, run) }
    }

    /** Phase 2: eine Karte springt aus dem Stapel (hoch + zur Seite + Drehung) und fällt zurück. */
    private fun playPop(pop: Int, run: Int) {
        if (run != animationRun) return
        if (pop >= SHUFFLE_POPS) {
            playSpread(run)
            return
        }
        val card = POP_ORDER[pop % POP_ORDER.size]
        val side = if (pop % 2 == 0) -1 else 1
        val upX = stackX(card) + side * POP_SIDE
        val upY = stackY(card) - POP_LIFT
        val upAngle = side * POP_ANGLE

        val popUp = cardAnimations(
            card, run, POP_DURATION,
            fromX = stackX(card), fromY = stackY(card), fromAngle = 0.0,
            toX = upX, toY = upY, toAngle = upAngle
        )
        playStep(run, popUp) {
            val popDown = cardAnimations(
                card, run, POP_DURATION,
                fromX = upX, fromY = upY, fromAngle = upAngle,
                toX = stackX(card), toY = stackY(card), toAngle = 0.0
            )
            playStep(run, popDown) { playPop(pop + 1, run) }
        }
    }

    /** Phase 3: vom Stapel auf die Plätze verteilen und auffächern. */
    private fun playSpread(run: Int) {
        val animations = (0 until BACK_COUNT).flatMap { card ->
            cardAnimations(
                card, run, SPREAD_DURATION,
                fromX = stackX(card), fromY = stackY(card), fromAngle = 0.0,
                toX = backX(card).toDouble(), toY = BACKS_Y.toDouble(), toAngle = backAngle(card)
            )
        }
        playStep(run, animations)
    }

    /**
     * Erzeugt die Bewegungs- und Rotationsanimationen für Rand und Rückseite einer Karte.
     */
    @Suppress("LongParameterList")
    private fun cardAnimations(
        card: Int,
        run: Int,
        duration: Int,
        fromX: Double,
        fromY: Double,
        fromAngle: Double,
        toX: Double,
        toY: Double,
        toAngle: Double
    ): List<Animation> {
        val (border, back) = cardBacks[card]
        val animations = mutableListOf<Animation>()
        listOf(border to -BACK_BORDER, back to 0).forEach { (label, offset) ->
            animations += createMoveAnimation(
                label = label, run = run, duration = duration,
                fromX = fromX + offset, fromY = fromY + offset,
                toX = toX + offset, toY = toY + offset
            )
            if (fromAngle != toAngle) {
                animations += createRotateAnimation(label, run, duration, fromAngle, toAngle)
            }
        }
        return animations
    }

    /** Erzeugt eine [MovementAnimation] für [label] und übernimmt die Zielposition in `onFinished`. */
    @Suppress("LongParameterList")
    private fun createMoveAnimation(
        label: Label,
        run: Int,
        duration: Int,
        fromX: Double,
        fromY: Double,
        toX: Double,
        toY: Double
    ): MovementAnimation<Label> = MovementAnimation(
        componentView = label,
        fromX = fromX, toX = toX,
        fromY = fromY, toY = toY,
        duration = duration,
        persist = false
    ).apply {
        onFinished = {
            if (run == animationRun) {
                label.posX = toX
                label.posY = toY
            }
        }
    }

    /** Erzeugt eine [RotationAnimation] für [label] und übernimmt den Zielwinkel in `onFinished`. */
    private fun createRotateAnimation(
        label: Label,
        run: Int,
        duration: Int,
        fromAngle: Double,
        toAngle: Double
    ): RotationAnimation<Label> = RotationAnimation(
        componentView = label,
        fromAngle = fromAngle, toAngle = toAngle,
        duration = duration,
        persist = false
    ).apply {
        onFinished = { if (run == animationRun) label.rotation = toAngle }
    }

    /**
     * Spielt alle Teil-Animationen eines Schritts gebündelt als [ParallelAnimation] ab
     * und ruft danach [onDone] auf (sofern der Durchlauf [run] noch aktuell ist).
     */
    private fun playStep(run: Int, animations: List<Animation>, onDone: () -> Unit = {}) {
        val parallel = ParallelAnimation(animations).apply {
            onFinished = { if (run == animationRun) onDone() }
        }
        runAnimationAsync(run) {
            playAnimation(parallel)
        }
    }

    /**
     * Führt [block] auf einem Hintergrund-Thread aus, damit `playAnimation` (das in BGW `runBlocking`
     * nutzt) nicht den Ktor/Netty-WebSocket-Thread blockiert oder das initiale Szenen-Update abbricht.
     */
    private fun runAnimationAsync(run: Int, delayMs: Long = 0L, block: () -> Unit) {
        Thread {
            if (delayMs > 0L) {
                Thread.sleep(delayMs)
            }
            if (run == animationRun) {
                block()
            }
        }.apply { isDaemon = true }.start()
    }

    /** Setzt Rand und Rückseite einer Karte sofort auf eine Position (ohne Animation). */
    private fun setCardPosition(card: Int, x: Double, y: Double) {
        val (border, back) = cardBacks[card]
        border.posX = x - BACK_BORDER
        border.posY = y - BACK_BORDER
        back.posX = x
        back.posY = y
    }

    /** Setzt den Winkel von Rand und Rückseite einer Karte sofort. */
    private fun setCardRotation(card: Int, angle: Double) {
        val (border, back) = cardBacks[card]
        border.rotation = angle
        back.rotation = angle
    }

    // ---------------------------------------------------------------------------------------
    // Refreshes: Zusammenfassung des Zugs sammeln
    // ---------------------------------------------------------------------------------------

    /** Neues Spiel: alles zurücksetzen. */
    override fun refreshAfterStartNewGame() {
        turnActions.clear()
        previousFacultyName = null
        adaptStrategyDiscarded = null
    }

    /** "Forschung vorantreiben" für die Zusammenfassung merken. */
    override fun refreshAfterAdvanceResearch(playedCard: ResearchCard, newLiteratureCard: ResearchCard?) {
        val literature = newLiteratureCard?.let { " · Literatur: ${short(it)} neu" } ?: ""
        turnActions += "Forschung: ${short(playedCard)} → ${symbol(playedCard.suit)}$literature"
    }

    /** "Neue Ideen gewinnen": die gezogene Karte wird bewusst NICHT verraten. */
    override fun refreshAfterGainNewIdeas(drawnCard: ResearchCard) {
        turnActions += "Neue Ideen: 1 Karte gezogen"
    }

    /** Schritt 1 von "Strategie anpassen": abgeworfene Karte liegt jetzt oben auf dem Ablagestapel. */
    override fun refreshAfterStartAdaptStrategy(drawnCards: List<ResearchCard>) {
        adaptStrategyDiscarded = rootService.currentGame?.discardStack?.lastOrNull()
    }

    /** Schritt 2 von "Strategie anpassen". */
    override fun refreshAfterFinishAdaptStrategy(keptCard: ResearchCard, discardedCard: ResearchCard) {
        val discarded = adaptStrategyDiscarded?.let { short(it) } ?: short(discardedCard)
        turnActions += "Strategie anpassen: $discarded abgeworfen, ${short(keptCard)} behalten"
        adaptStrategyDiscarded = null
    }

    /** "Publizieren". */
    override fun refreshAfterPublish(area: ResearchArea, pointsGained: Int) {
        turnActions += "Publiziert: ${symbol(area)} (+$pointsGained P)"
    }

    /** "Konkurrenz ausbremsen". */
    override fun refreshAfterSlowDownCompetition(
        targetFaculty: Faculty,
        area: ResearchArea,
        removedCard: ResearchCard
    ) {
        turnActions += "Konkurrenz ausgebremst: ${targetFaculty.name} ${symbol(area)} (${short(removedCard)} entfernt)"
    }

    /** "Literatur nutzen". */
    override fun refreshAfterUseLiterature(usedCard: ResearchCard) {
        turnActions += "Literatur genutzt: ${short(usedCard)} → ${symbol(usedCard.suit)}"
    }

    /**
     * Ein neuer Zug beginnt: alle Texte für die nächste Fakultät setzen.
     * Das Einblenden der Szene übernimmt die [SopraApplication].
     */
    override fun refreshAfterStartTurn() {
        val game = rootService.currentGame ?: return
        val nextName = game.faculties[game.facultyAtTurn].name
        val previousName = previousFacultyName

        if (previousName == null) {
            captionLabel.text = "SPIELSTART"
            setTitle("Es beginnt", nextName)
            summaryHeaderLabel.text = "REIHENFOLGE"
            showSummary(listOf(game.faculties.joinToString(" → ") { it.name }))
        } else {
            captionLabel.text = "ZUG BEENDET"
            setTitle("Weiter an", nextName)
            summaryHeaderLabel.text = "${previousName.uppercase()}S ZUG"
            showSummary(turnActions.takeLast(MAX_SUMMARY_LINES).ifEmpty { listOf("Keine Aktionen") })
        }

        hintLabel.text = "Die Handkarten bleiben verdeckt, bis $nextName bereit ist."
        readyButton.text = "Ich bin $nextName – Zug starten"

        turnActions.clear()
        previousFacultyName = nextName

        playShuffleAnimation()
    }

    /**
     * Setzt die Überschrift aus zwei Labels (weißer Text + goldener Name) und zentriert beide
     * zusammen in der Box. Die Textbreite wird pro Buchstabe geschätzt (breite Buchstaben wie "m"
     * zählen mehr als schmale wie "i"). Passt die Überschrift nicht in die Box, wird die Schrift
     * kleiner, damit lange Namen nie abgeschnitten werden.
     */
    private fun setTitle(prefix: String, name: String) {
        val available = CARD_WIDTH - 2.0 * TITLE_MARGIN
        val widthAtFullSize = textWidth(prefix, TITLE_FONT_SIZE.toDouble()) + TITLE_GAP +
                textWidth(name, TITLE_FONT_SIZE.toDouble())
        val fontSize = if (widthAtFullSize <= available) {
            TITLE_FONT_SIZE.toDouble()
        } else {
            (TITLE_FONT_SIZE * available / widthAtFullSize).coerceAtLeast(MIN_TITLE_FONT_SIZE.toDouble())
        }

        val prefixWidth = textWidth(prefix, fontSize)
        val totalWidth = prefixWidth + TITLE_GAP + textWidth(name, fontSize)
        val startX = (CARD_X + (CARD_WIDTH - totalWidth) / 2.0).coerceAtLeast(CARD_X + TITLE_MARGIN.toDouble())
        val nameX = startX + prefixWidth + TITLE_GAP

        titleLabel.font = Font(size = fontSize, color = COLOR_TEXT, fontWeight = Font.FontWeight.BOLD)
        titleLabel.text = prefix
        titleLabel.posX = CARD_X.toDouble()
        titleLabel.width = startX + prefixWidth - CARD_X

        nameLabel.font = Font(size = fontSize, color = COLOR_GOLD, fontWeight = Font.FontWeight.BOLD)
        nameLabel.text = name
        nameLabel.posX = nameX
        nameLabel.width = CARD_X + CARD_WIDTH - nameX
    }

    /** Verteilt die Zeilen auf die Zeilen-Labels, übrige Labels werden geleert. */
    private fun showSummary(lines: List<String>) {
        summaryLines.forEachIndexed { index, label -> label.text = lines.getOrElse(index) { "" } }
    }

    /** Geschätzte Breite von [text] in fetter Schrift der Größe [fontSize]. */
    private fun textWidth(text: String, fontSize: Double): Double =
        text.sumOf { charWidthFactor(it) } * fontSize

    /** Ungefähre Buchstabenbreite relativ zur Schriftgröße (fette Standardschrift). */
    private fun charWidthFactor(char: Char): Double = when {
        char in "mwMW" -> WIDE_CHAR
        char in "iljI.,:;' |!" -> NARROW_CHAR
        char in "tfr" -> SMALL_CHAR
        char.isUpperCase() -> UPPER_CHAR
        else -> NORMAL_CHAR
    }

    /** Kurzname einer Karte, z. B. "7♦" oder "K♠". */
    private fun short(card: ResearchCard): String = "${valueText(card.value)}${symbol(card.suit)}"

    private fun valueText(value: ResearchTopic): String = when (value) {
        ResearchTopic.ACE -> "A"
        ResearchTopic.JACK -> "J"
        ResearchTopic.QUEEN -> "Q"
        ResearchTopic.KING -> "K"
        else -> (value.ordinal + 1).toString()
    }

    /** Farbsymbol wie in der Legende des Startmenüs. */
    private fun symbol(area: ResearchArea): String = when (area) {
        ResearchArea.AI -> "♠"
        ResearchArea.IT_SECURITY -> "♥"
        ResearchArea.SE -> "♦"
        ResearchArea.THEORETICAL -> "♣"
    }

    private companion object {
        const val SCENE_WIDTH = 1920
        const val SCENE_HEIGHT = 1080

        // Box in der Mitte
        const val CARD_WIDTH = 760
        const val CARD_HEIGHT = 640
        const val CARD_X = (SCENE_WIDTH - CARD_WIDTH) / 2
        const val CARD_Y = (SCENE_HEIGHT - CARD_HEIGHT) / 2
        const val BORDER = 2
        const val CONTENT_X = CARD_X + 50
        const val CONTENT_WIDTH = CARD_WIDTH - 100

        // Kartenrücken
        const val BACK_COUNT = 3
        const val BACK_WIDTH = 58
        const val BACK_HEIGHT = 80
        const val BACK_GAP = 10
        const val BACK_BORDER = 3
        const val BACK_ROTATION = 6.0
        const val BACKS_Y = CARD_Y + 50
        // Misch-Animation (Zeiten in Millisekunden, Abstände in Pixeln)
        const val ANIMATION_START_DELAY = 80L
        const val ANIMATION_STOP_DELAY = 40L
        const val GATHER_DURATION = 280
        const val POP_DURATION = 170
        const val SPREAD_DURATION = 320
        const val SHUFFLE_POPS = 4
        const val STACK_OFFSET = 3
        const val POP_SIDE = 70
        const val POP_LIFT = 45
        const val POP_ANGLE = 14.0

        /** Welche Karte bei welchem Sprung aus dem Stapel springt. */
        val POP_ORDER = listOf(2, 0, 1, 2)

        // Texte
        const val CAPTION_Y = CARD_Y + 160
        const val CAPTION_HEIGHT = 24
        const val TITLE_Y = CARD_Y + 200
        const val TITLE_HEIGHT = 64
        const val TITLE_FONT_SIZE = 46
        const val MIN_TITLE_FONT_SIZE = 22
        const val WIDE_CHAR = 0.92
        const val NARROW_CHAR = 0.3
        const val SMALL_CHAR = 0.4
        const val UPPER_CHAR = 0.74
        const val NORMAL_CHAR = 0.62
        const val TITLE_GAP = 14
        const val TITLE_MARGIN = 30
        const val HINT_Y = CARD_Y + 280
        const val HINT_LINE_HEIGHT = 28

        // Zusammenfassung
        const val SUMMARY_Y = CARD_Y + 360
        const val SUMMARY_HEIGHT = 130
        const val SUMMARY_PADDING = 16
        const val SUMMARY_HEADER_HEIGHT = 24
        const val SUMMARY_LINE_HEIGHT = 24
        const val SUMMARY_TEXT_X = CONTENT_X + SUMMARY_PADDING
        const val SUMMARY_TEXT_WIDTH = CONTENT_WIDTH - 2 * SUMMARY_PADDING
        const val MAX_SUMMARY_LINES = 3

        // Button
        const val BUTTON_Y = CARD_Y + 520
        const val BUTTON_HEIGHT = 64

        // Farben aus dem Storyboard
        val COLOR_SCENE_BG = Color(19, 38, 33)
        val COLOR_CARD_BG = Color(40, 73, 64)
        val COLOR_CARD_BORDER = Color(54, 92, 80)
        val COLOR_SUMMARY_BG = Color(26, 50, 43)
        val COLOR_BACK = Color(124, 58, 48)
        val COLOR_BACK_BORDER = Color(240, 232, 220)
        val COLOR_GOLD = Color(226, 165, 58)
        val COLOR_TEXT = Color(240, 236, 226)
        val COLOR_MUTED = Color(184, 198, 190)
        val COLOR_DARK_TEXT = Color(28, 32, 28)

        val FONT_CAPTION = Font(size = 15, color = COLOR_MUTED)
        val FONT_TITLE = Font(size = TITLE_FONT_SIZE, color = COLOR_TEXT, fontWeight = Font.FontWeight.BOLD)
        val FONT_TITLE_NAME = Font(size = TITLE_FONT_SIZE, color = COLOR_GOLD, fontWeight = Font.FontWeight.BOLD)
        val FONT_HINT = Font(size = 18, color = COLOR_MUTED)
        val FONT_SUMMARY_HEADER = Font(size = 13, color = COLOR_MUTED)
        val FONT_SUMMARY_TEXT = Font(size = 16, color = COLOR_TEXT, family = "Consolas")
        val FONT_BUTTON = Font(size = 20, color = COLOR_DARK_TEXT, fontWeight = Font.FontWeight.BOLD)
    }
}