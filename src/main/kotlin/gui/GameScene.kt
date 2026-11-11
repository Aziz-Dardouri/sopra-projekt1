package gui

import entity.Faculty
import entity.FacultyGame
import entity.ResearchArea
import entity.ResearchCard
import entity.CardSuit
import entity.CardValue
import entity.ResearchTopic
import service.Refreshable
import service.RootService
import tools.aqua.bgw.components.ComponentView
import tools.aqua.bgw.components.layoutviews.Pane
import tools.aqua.bgw.components.uicomponents.Button
import tools.aqua.bgw.components.uicomponents.Label
import tools.aqua.bgw.components.uicomponents.ListView
import tools.aqua.bgw.components.uicomponents.SelectionMode
import tools.aqua.bgw.components.gamecomponentviews.CardView
import tools.aqua.bgw.core.Alignment
import tools.aqua.bgw.core.BoardGameScene
import tools.aqua.bgw.core.Color
import tools.aqua.bgw.style.BorderRadius
import tools.aqua.bgw.util.Font
import tools.aqua.bgw.visual.ColorVisual
import tools.aqua.bgw.visual.CompoundVisual
import tools.aqua.bgw.visual.TextVisual
import tools.aqua.bgw.visual.Visual

/**
 * Spielszene: zeigt den Spielstand rund um die Karten, die aktive Fakultät führt hier ihre Aktionen aus.
 *
 * Setzt Issue #63 nach dem Storyboard um (docs/Modell-GUI-Storyboard/Group_1.png):
 * - Header, gegnerische Fakultäten, zentrale Literatur, eigene Fakultät, Aktionen und Platz für das Log.
 * - Das Layout passt sich an 2, 3 und 4 Fakultäten an.
 * - Erst wird eine Aktion gewählt, dann über [updateSelection] Karte oder Stapel, Submit ruft den Service auf.
 * - Nicht mögliche Aktionen sind ausgegraut, Exceptions der Service-Schicht werden angezeigt statt abzustürzen.
 * - Zwischen Schritt 1 und 2 von „Strategie anpassen“ und nach Spielende sind alle Aktionen und
 *   „Zug beenden“ gesperrt.
 *
 * Die Karten selbst legt Issue #64 in die Panes dieser Szene.
 *
 * @param rootService zentrale [RootService]-Instanz, liest den Spielstand und ruft die Services auf
 */
// Allein Refreshable braucht hier 11 Overrides, siehe GUI-Klassendiagramm
@Suppress("TooManyFunctions")
class GameScene(
    private val rootService: RootService
) : BoardGameScene(SCENE_WIDTH, SCENE_HEIGHT), Refreshable {

    private val drawStackLabel = Label(
        posX = DRAW_STACK_X, posY = STACK_SLOT_Y + STACK_SLOT_HEIGHT + 5,
        width = STACK_SLOT_WIDTH, height = 24,
        font = FONT_CAPTION_TEXT, alignment = Alignment.CENTER
    )
    private val discardStackLabel = Label(
        posX = DISCARD_STACK_X, posY = STACK_SLOT_Y + STACK_SLOT_HEIGHT + 5,
        width = STACK_SLOT_WIDTH, height = 24,
        font = FONT_CAPTION_TEXT, alignment = Alignment.CENTER
    )

    /**
     * Bisher gewählte Karten und Stapel. Ändern nur über [updateSelection].
     */
    val selection: ActionSelection = ActionSelection()
    private val cardImageLoader = CardImageLoader()


    private val cardViewCache = java.util.IdentityHashMap<ResearchCard, CardView>()

    private val drawStackBackView = CardView(
        posX = 0.0,
        posY = 0.0,
        width = STACK_SLOT_WIDTH,
        height = STACK_SLOT_HEIGHT,
        front = cardImageLoader.backImage,
        back = cardImageLoader.backImage
    ).apply {
        showBack()
    }

    /**
     * Platz für die Karten der gegnerischen Fakultäten, in der Reihenfolge von [opponents].
     */
    val opponentCardAreas: List<Pane<ComponentView>>
        get() = opponentPanels.map { it.cardArea }

    /**
     * Platz für die Karten der zentralen Literatur.
     */
    val literatureArea: Pane<ComponentView> =
        Pane(LITERATURE_X, LITERATURE_CARDS_Y, LITERATURE_WIDTH, LITERATURE_HEIGHT)

    /**
     * Platz für den Nachziehstapel.
     */
    val drawStackArea: Pane<ComponentView> = Pane(DRAW_STACK_X, STACK_SLOT_Y, STACK_SLOT_WIDTH, STACK_SLOT_HEIGHT)

    /**
     * Platz für den Ablagestapel.
     */
    val discardStackArea: Pane<ComponentView> = Pane(DISCARD_STACK_X, STACK_SLOT_Y, STACK_SLOT_WIDTH, STACK_SLOT_HEIGHT)

    /**
     * Platz für die Forschungsstapel der aktiven Fakultät.
     */
    val ownStackAreas: Map<ResearchArea, Pane<ComponentView>> = ResearchArea.entries.associateWith { area ->
        Pane(ownStackX(area), OWN_STACK_Y, OWN_STACK_WIDTH, OWN_STACK_HEIGHT, VISUAL_SLOT)
    }

    /**
     * Platz für die Publikationen der aktiven Fakultät.
     */
    val publicationsArea: Pane<ComponentView> =
        Pane(PUBLICATIONS_X, OWN_STACK_Y, PUBLICATIONS_WIDTH, OWN_STACK_HEIGHT, VISUAL_DARK_PANEL)

    /**
     * Platz für die Handkarten der aktiven Fakultät.
     */
    val handArea: Pane<ComponentView> = Pane(HAND_X, HAND_Y, HAND_WIDTH, HAND_HEIGHT)

    /**
     * Log der Aktionen als scrollbare Liste, neueste Zeile oben (Issue #58).
     * Hintergrund leer, damit das dunkle Log-Panel durchscheint (Standard der ListView wäre weiß).
     * Auswahl unsichtbar statt [SelectionMode.NONE]: BGW schickt beim Klick trotzdem eine Auswahl
     * und wirft dann bei NONE eine IllegalStateException.
     */
    val logArea: ListView<String> = ListView(
        posX = LOG_CONTENT_X,
        posY = LOG_CONTENT_Y,
        width = LOG_CONTENT_WIDTH,
        height = LOG_CONTENT_HEIGHT,
        font = FONT_LOG,
        visual = Visual.EMPTY,
        selectionBackground = ColorVisual.TRANSPARENT
    )

    /**
     * Overlay-Dialog für Schritt 2 von „Strategie anpassen“ (Auswahl aus 2 gezogenen Karten).
     */
    val adaptStrategyOverlay: Pane<ComponentView> = Pane<ComponentView>(
        posX = DIALOG_X,
        posY = DIALOG_Y,
        width = DIALOG_WIDTH,
        height = DIALOG_HEIGHT,
        visual = VISUAL_DARK_PANEL
    ).apply {
        isVisible = false
    }

    private var selectedAction: GameAction? = null
    private var adaptStrategyPending: Boolean = false

    private var isLogExpanded: Boolean = false

    private val activeCaptionLabel: Label =
        headerLabel(HEADER_CAPTION_X, HEADER_CAPTION_WIDTH, "AM ZUG", FONT_HEADER_CAPTION)
    private val activeNameLabel: Label = headerLabel(HEADER_NAME_X, HEADER_NAME_WIDTH, "", FONT_HEADER_NAME)
    private val apCaptionLabel: Label = headerLabel(0, AP_CAPTION_WIDTH, "AP", FONT_HEADER_CAPTION)
    private val apDotsLabel: Label = headerLabel(0, AP_DOTS_WIDTH, "", FONT_AP_DOTS)
    private val apTextLabel: Label = headerLabel(0, AP_TEXT_WIDTH, "", FONT_HEADER_TEXT)

    private val turnOrderPills: List<Label> = List(MAX_FACULTIES) {
        Label(posY = PILL_Y, height = PILL_HEIGHT, font = FONT_PILL)
    }
    private val turnOrderArrows: List<Label> = List(MAX_FACULTIES - 1) {
        Label(posY = PILL_Y, width = ARROW_WIDTH, height = PILL_HEIGHT, text = "→", font = FONT_HEADER_TEXT)
    }

    private val drawCountLabel: Label =
        headerLabel(DRAW_COUNT_X, HEADER_COUNT_WIDTH, "", FONT_HEADER_VALUE)
    private val pointsLabel: Label =
        headerLabel(POINTS_X, HEADER_COUNT_WIDTH, "", FONT_HEADER_POINTS)

    private val opponentPanels: List<OpponentPanel> = List(MAX_FACULTIES - 1) { OpponentPanel() }

    private val ownTitleLabel: Label = Label(
        posX = OWN_CONTENT_X,
        posY = OWN_TITLE_Y,
        width = OWN_TITLE_WIDTH,
        height = OWN_TITLE_HEIGHT,
        font = FONT_PANEL_TITLE,
        alignment = Alignment.CENTER_LEFT
    )

    private val ownStackLabels: Map<ResearchArea, Label> = ResearchArea.entries.associateWith { area ->
        Label(
            posX = ownStackX(area) + STACK_SYMBOL_WIDTH,
            posY = OWN_STACK_LABEL_Y,
            width = OWN_STACK_WIDTH - STACK_SYMBOL_WIDTH,
            height = OWN_STACK_LABEL_HEIGHT,
            font = FONT_CAPTION_TEXT,
            alignment = Alignment.CENTER_LEFT
        )
    }

    private val handLabel: Label = Label(
        posX = OWN_CONTENT_X,
        posY = HAND_LABEL_Y,
        width = HAND_LABEL_WIDTH,
        height = HAND_LABEL_HEIGHT,
        font = FONT_CAPTION_TEXT,
        alignment = Alignment.CENTER_LEFT,
        isWrapText = true
    )

    private val hintLabel: Label = Label(
        posX = HINT_X,
        posY = HINT_Y,
        width = HINT_WIDTH,
        height = HINT_HEIGHT,
        font = FONT_HINT,
        alignment = Alignment.CENTER_LEFT,
        isWrapText = true
    )

    /**
     * Buttons zum Wählen einer Aktion.
     */
    private val actionButtons: Map<GameAction, Button> = GameAction.entries.associateWith { action ->
        Button(
            posX = ACTION_X,
            posY = ACTION_START_Y + action.ordinal * ACTION_STEP_Y,
            width = ACTION_WIDTH,
            height = ACTION_HEIGHT,
            visual = actionVisual(action, FONT_ACTION, VISUAL_ACTION_ENABLED)
        ).apply {
            onMouseClicked = { selectAction(action) }
        }
    }

    /**
     * Button, der die gewählte Aktion ausführt.
     */
    private val submitButton: Button = Button(
        posX = ACTION_X,
        posY = SUBMIT_Y,
        width = ACTION_WIDTH,
        height = SUBMIT_HEIGHT,
        text = "Submit",
        font = FONT_SUBMIT,
        visual = VISUAL_BUTTON_DISABLED
    ).apply {
        isDisabled = true
        onMouseClicked = { submit() }
    }

    /**
     * Zeigt Fehlermeldungen der Service-Schicht.
     */
    private val statusLabel: Label = Label(
        posX = ACTION_X,
        posY = STATUS_Y,
        width = ACTION_WIDTH,
        height = STATUS_HEIGHT,
        font = FONT_STATUS_ERROR,
        alignment = Alignment.TOP_LEFT,
        isWrapText = true
    )

    /**
     * Button, der den Zug der aktiven Fakultät beendet.
     */
    private val endTurnButton: Button = Button(
        posX = SIDE_X,
        posY = END_TURN_Y,
        width = SIDE_WIDTH,
        height = END_TURN_HEIGHT,
        text = "Zug beenden",
        font = FONT_END_TURN,
        visual = VISUAL_GOLD
    ).apply {
        onMouseClicked = { endTurn() }
    }

    /**
     * Schaltet das Log zwischen Panel und Vollbild um, siehe [setLogExpanded].
     */
    private val expandLogButton: Button = Button(
        posX = EXPAND_BUTTON_X,
        posY = MENU_BUTTON_Y,
        width = EXPAND_BUTTON_WIDTH,
        height = MENU_BUTTON_HEIGHT,
        text = EXPAND_TEXT,
        font = FONT_MENU_BUTTON,
        visual = VISUAL_ACTION_ENABLED
    ).apply {
        onMouseClicked = { setLogExpanded(!isLogExpanded) }
    }

    /**
     * Bricht das laufende Spiel ab. Das Startmenü zeigt danach die [SopraApplication]
     * über [refreshAfterQuitGame].
     */
    private val backToMenuButton: Button = Button(
        posX = LOG_CONTENT_X + LOG_CONTENT_WIDTH - MENU_BUTTON_WIDTH,
        posY = MENU_BUTTON_Y,
        width = MENU_BUTTON_WIDTH,
        height = MENU_BUTTON_HEIGHT,
        text = "Zurück zum Menü",
        font = FONT_MENU_BUTTON,
        visual = VISUAL_ACTION_ENABLED
    ).apply {
        onMouseClicked = { callService { rootService.gameService.quitGame() } }
    }

    init {
        background = VISUAL_SCENE_BG

        addHeader()
        opponentPanels.flatMap { it.components }.forEach { addComponents(it) }
        addLiteraturePanel()
        addOwnFacultyPanel()
        addActionPanel()
        addLogPanel()
        addComponents(endTurnButton)

        addComponents(adaptStrategyOverlay)
    }

    /**
     * Liefert die Fakultäten, die nicht am Zug sind, beginnend mit der nächsten.
     *
     * @return die gegnerischen Fakultäten in Zugreihenfolge, leer wenn kein Spiel läuft
     */
    fun opponents(): List<Faculty> {
        val game = rootService.currentGame ?: return emptyList()
        return List(game.faculties.size - 1) { offset ->
            game.faculties[(game.facultyAtTurn + offset + 1) % game.faculties.size]
        }
    }

    /**
     * Ändert die [selection] und passt Submit-Button und Hinweis an.
     * Die Klick-Handler von Karten und Stapeln müssen diese Methode verwenden.
     *
     * @param change die Änderung, z. B. `{ handCard = card }`
     */
    fun updateSelection(change: ActionSelection.() -> Unit) {
        selection.change()
        updateActionControls()
    }

    /**
     * Wählt die Aktion, die der Submit-Button ausführt.
     *
     * @param action die gewählte Aktion
     */
    private fun selectAction(action: GameAction) {
        selectedAction = action
        statusLabel.text = ""
        updateActionControls()
    }

    /**
     * Führt die gewählte Aktion aus oder schließt „Strategie anpassen“ ab, wenn Schritt 1 schon lief.
     */
    private fun submit() {
        val action = selectedAction ?: return
        callService {
            val service = rootService.playerActionService
            if (adaptStrategyPending) {
                service.finishAdaptStrategy(checkNotNull(selection.keptCard) { "Bitte zuerst eine Karte wählen." })
            } else {
                action.execute(service, selection)
            }
        }
    }

    /**
     * Beendet den Zug der aktiven Fakultät.
     */
    private fun endTurn() {
        callService { rootService.gameService.endTurn() }
    }

    /**
     * Setzt die Szene für das neue Spiel zurück.
     */
    override fun refreshAfterStartNewGame() {
        clearGameState()
        refreshAfterAction()
    }

    /**
     * Räumt die Szene nach „Zurück zum Menü“ komplett leer, damit nichts vom alten Spiel stehen bleibt.
     */
    override fun refreshAfterQuitGame() {
        clearGameState()
        opponentPanels.forEach { it.isShown = false }
        turnOrderPills.forEach { it.isVisible = false }
        turnOrderArrows.forEach { it.isVisible = false }
        val gameLabels = listOf(
            activeNameLabel, apDotsLabel, apTextLabel, drawCountLabel, pointsLabel,
            ownTitleLabel, handLabel, hintLabel, statusLabel, drawStackLabel, discardStackLabel
        )
        (gameLabels + ownStackLabels.values).forEach { it.text = "" }
    }

    /**
     * Setzt alles zurück, was zu einem einzelnen Spiel gehört: Auswahl, Strategie-Dialog, Log und Karten.
     */
    private fun clearGameState() {
        selectedAction = null
        adaptStrategyPending = false
        selection.clear()
        adaptStrategyOverlay.isVisible = false
        logArea.items.clear()
        setLogExpanded(false)
        cardViewCache.clear()
        opponentPanels.forEach { it.cardArea.clear() }
        handArea.clear()
        literatureArea.clear()
        ownStackAreas.values.forEach { it.clear() }
        publicationsArea.clear()
        drawStackArea.clear()
        discardStackArea.clear()
    }

    /**
     * Schreibt [message] als neueste Zeile oben ins Log.
     */
    override fun refreshAfterLog(message: String) {
        logArea.items.add(0, message)
    }

    /**
     * Zeigt das Log im Vollbild über der ganzen Szene oder wieder klein im Log-Panel.
     *
     * @param expanded true für Vollbild
     */
    private fun setLogExpanded(expanded: Boolean) {
        isLogExpanded = expanded
        if (expanded) {
            logArea.visual = VISUAL_DARK_PANEL
            logArea.posX = 0.0
            logArea.posY = 0.0
            logArea.width = SCENE_WIDTH.toDouble()
            logArea.height = SCENE_HEIGHT.toDouble()
            expandLogButton.posX = (SCENE_WIDTH - EXPAND_BUTTON_WIDTH - EXPANDED_BUTTON_MARGIN).toDouble()
            expandLogButton.posY = EXPANDED_BUTTON_MARGIN.toDouble()
            expandLogButton.text = COLLAPSE_TEXT
            // Liste und Button über alle anderen Komponenten legen
            logArea.toFront()
            expandLogButton.toFront()
        } else {
            logArea.visual = Visual.EMPTY
            logArea.posX = LOG_CONTENT_X.toDouble()
            logArea.posY = LOG_CONTENT_Y.toDouble()
            logArea.width = LOG_CONTENT_WIDTH.toDouble()
            logArea.height = LOG_CONTENT_HEIGHT.toDouble()
            expandLogButton.posX = EXPAND_BUTTON_X.toDouble()
            expandLogButton.posY = MENU_BUTTON_Y.toDouble()
            expandLogButton.text = EXPAND_TEXT
        }
    }

    /**
     * Zeigt die Fakultät, deren Zug begonnen hat.
     */
    override fun refreshAfterStartTurn() = refreshAfterAction()

    /**
     * Bewusst leer: [service.GameService.endTurn] ruft direkt danach startTurn auf,
     * [refreshAfterStartTurn] zeichnet dann die Szene für die nächste Fakultät neu.
     */
    override fun refreshAfterEndTurn() {
        // Sonst würde die Szene bei jedem Zugwechsel doppelt aufgebaut
    }

    /**
     * Zeigt den Spielstand nach „Forschung vorantreiben“.
     */
    override fun refreshAfterAdvanceResearch(
        playedCard: ResearchCard,
        newLiteratureCard: ResearchCard?
    ) = refreshAfterAction()

    /**
     * Zeigt den Spielstand nach „Neue Ideen gewinnen“.
     */
    override fun refreshAfterGainNewIdeas(drawnCard: ResearchCard) = refreshAfterAction()

    /**
     * Sperrt alle anderen Aktionen, bis eine der aufgedeckten Karten gewählt ist.
     */
    override fun refreshAfterStartAdaptStrategy(drawnCards: List<ResearchCard>) {
        adaptStrategyPending = true
        refreshView()
        showAdaptStrategyDialog(drawnCards)
    }

    /**
     * Gibt die Aktionen nach „Strategie anpassen“ wieder frei.
     */
    override fun refreshAfterFinishAdaptStrategy(
        keptCard: ResearchCard,
        discardedCard: ResearchCard
    ) {
        adaptStrategyPending = false
        adaptStrategyOverlay.isVisible = false
        refreshAfterAction()
    }

    /**
     * Zeigt den Spielstand nach „Publizieren“.
     */
    override fun refreshAfterPublish(area: ResearchArea, pointsGained: Int) = refreshAfterAction()

    /**
     * Zeigt den Spielstand nach „Konkurrenz ausbremsen“.
     */
    override fun refreshAfterSlowDownCompetition(
        targetFaculty: Faculty,
        area: ResearchArea,
        removedCard: ResearchCard
    ) = refreshAfterAction()

    /**
     * Zeigt den Spielstand nach „Literatur nutzen“.
     */
    override fun refreshAfterUseLiterature(usedCard: ResearchCard) = refreshAfterAction()

    /**
     * Sperrt alle Aktionen, das Spiel ist vorbei.
     */
    override fun refreshAfterGameEnd(winners: List<Faculty>, scores: Map<Faculty, Int>) {
        adaptStrategyPending = false
        refreshAfterAction()
    }

    private fun refreshAfterAction() {
        selectedAction = null
        refreshView()
    }

    private fun refreshView() {
        val game = rootService.currentGame ?: return
        selection.clear()
        statusLabel.text = ""


        opponentPanels.forEach { it.cardArea.clear() }
        handArea.clear()
        literatureArea.clear()
        ownStackAreas.values.forEach { it.clear() }
        publicationsArea.clear()
        drawStackArea.clear()
        discardStackArea.clear()


        updateHeader(game)
        updateOpponents()
        updateOwnFaculty(game)
        updateActionControls()

        updateHandCards(game)
        updateLiterature(game)
        updateResearchStacks(game)
        updatePublications(game)
        updateDrawAndDiscardStacks(game)
    }

    private fun updateHeader(game: FacultyGame) {
        val active = game.faculties[game.facultyAtTurn]
        val activeName = shorten(active.name, MAX_HEADER_NAME_CHARS)
        val actionPoints = active.actionPoints.coerceIn(0, MAX_ACTION_POINTS)

        activeNameLabel.text = activeName
        apDotsLabel.text = "●".repeat(actionPoints) + "○".repeat(MAX_ACTION_POINTS - actionPoints)
        apTextLabel.text = "$actionPoints von $MAX_ACTION_POINTS übrig"
        drawCountLabel.text = "${game.drawStack.size}"
        pointsLabel.text = "${active.researchPoints}"

        val apX = HEADER_NAME_X + activeName.length * HEADER_NAME_CHAR_WIDTH + AP_GAP
        apCaptionLabel.posX = apX.toDouble()
        apDotsLabel.posX = (apX + AP_CAPTION_WIDTH).toDouble()
        apTextLabel.posX = (apX + AP_CAPTION_WIDTH + AP_DOTS_WIDTH).toDouble()

        updateTurnOrder(game)
    }

    private fun updateTurnOrder(game: FacultyGame) {
        val names = game.faculties.map { shorten(it.name, MAX_PILL_CHARS) }
        val widths = names.map { PILL_PADDING + it.length * PILL_CHAR_WIDTH }
        var nextX = (SCENE_WIDTH - widths.sum() - (names.size - 1) * ARROW_WIDTH) / 2

        turnOrderPills.forEachIndexed { index, pill -> pill.isVisible = index < names.size }
        turnOrderArrows.forEachIndexed { index, arrow -> arrow.isVisible = index < names.size - 1 }

        names.forEachIndexed { index, name ->
            val isActive = index == game.facultyAtTurn
            turnOrderPills[index].apply {
                text = name
                posX = nextX.toDouble()
                width = widths[index].toDouble()
                visual = if (isActive) VISUAL_PILL_ACTIVE else VISUAL_PILL
                font = if (isActive) FONT_PILL_ACTIVE else FONT_PILL
            }
            nextX += widths[index]
            turnOrderArrows.getOrNull(index)?.posX = nextX.toDouble()
            nextX += ARROW_WIDTH
        }
    }

    private fun updateOpponents() {
        val opponents = opponents()
        val panelWidth = (OPPONENTS_WIDTH - (opponents.size - 1) * PANEL_GAP) / opponents.size

        opponentPanels.forEachIndexed { index, panel ->
            val faculty = opponents.getOrNull(index)
            panel.isShown = faculty != null
            if (faculty != null) {
                panel.show(faculty, PANEL_X + index * (panelWidth + PANEL_GAP), panelWidth)
                updateOpponentCards(faculty, panel)
            }
        }
    }

    private fun updateOwnFaculty(game: FacultyGame) {
        val active = game.faculties[game.facultyAtTurn]
        ownTitleLabel.text = "Deine Fakultät · ${shorten(active.name, MAX_OWN_NAME_CHARS)}"
        handLabel.text = "Hand (${active.handCards.size}/$MAX_HAND_CARDS)"
        ownStackLabels.forEach { (area, label) ->
            label.text = "${area.shortLabel} (${active.researchActivity.getValue(area).size})"
        }
    }

    /**
     * Liefert die [CardView] für [card] aus dem Cache oder legt eine neue an.
     */
    private fun createCardView(
        card: ResearchCard,
        width: Number = STACK_SLOT_WIDTH,
        height: Number = STACK_SLOT_HEIGHT
    ): CardView {
        val cachedView = cardViewCache[card]
        if (cachedView != null) {
            if (cachedView.parent != null) {
                cachedView.removeFromParent()
            }

            cachedView.width = width.toDouble()
            cachedView.height = height.toDouble()
            cachedView.onMouseClicked = null
            cachedView.showFront()
            return cachedView
        }

        val newView = CardView(
            width = width,
            height = height,
            front = cardImageLoader.frontImageFor(card.suit.toCardSuit(), card.value.toCardValue()),
            back = cardImageLoader.backImage
        ).apply {
            showFront()
        }

        cardViewCache[card] = newView
        return newView
    }

    /**
     * Zeichnet die zentrale Literatur in die [literatureArea]
     * und bindet die Klick-Auswahl an [updateSelection] an.
     */
    private fun updateLiterature(game: FacultyGame) {
        literatureArea.clear()
        val cardViews = mutableListOf<CardView>()

        val cardStepX = 110.0
        val defaultY = 15.0
        val selectedY = 0.0

        game.centralLiterature.forEachIndexed { index, card ->
            val cardView = createCardView(card).apply {
                posX = index * cardStepX
                posY = if (selection.literatureCard == card) selectedY else defaultY
            }
            cardViews.add(cardView)

            cardView.onMouseClicked = {
                val isAlreadySelected = (selection.literatureCard == card)

                updateSelection {
                    literatureCard = if (isAlreadySelected) null else card
                }

                cardViews.forEach { it.posY = defaultY }
                if (!isAlreadySelected) {
                    cardView.posY = selectedY
                }
            }

            literatureArea.add(cardView)
        }
    }

    /**
     * Zeichnet die Handkarten der aktiven Fakultät in die [handArea]
     * und bindet die Klick-Auswahl an [updateSelection] an.
     */
    private fun updateHandCards(game: FacultyGame) {
        handArea.clear()
        val activeFaculty = game.faculties[game.facultyAtTurn]
        val cardViews = mutableListOf<CardView>()

        val cardStepX = 95.0
        val defaultY = 30.0
        val selectedY = 5.0

        activeFaculty.handCards.forEachIndexed { index, card ->
            val cardView = createCardView(card).apply {
                posX = index * cardStepX
                posY = if (selection.handCard == card) selectedY else defaultY
            }
            cardViews.add(cardView)

            cardView.onMouseClicked = {
                val isAlreadySelected = (selection.handCard == card)

                updateSelection {
                    handCard = if (isAlreadySelected) null else card
                }

                cardViews.forEach { it.posY = defaultY }
                if (!isAlreadySelected) {
                    cardView.posY = selectedY
                }
            }

            handArea.add(cardView)
        }
    }

    /**
     * Hebt den aktuell gewählten eigenen Forschungsstapel in [ownStackAreas] farblich hervor.
     */
    private fun updateStackSelectionVisuals() {
        ResearchArea.entries.forEach { area ->
            val stackPane = ownStackAreas.getValue(area)
            val isAreaSelected = (selection.ownArea == area)
            stackPane.visual = if (isAreaSelected) VISUAL_GOLD else VISUAL_SLOT
        }
    }

    /**
     * Zeichnet die 4 Forschungsstapel der aktiven Fakultät in [ownStackAreas]
     * und verknüpft die Gebietsauswahl mit [updateSelection].
     */
    private fun updateResearchStacks(game: FacultyGame) {
        val activeFaculty = game.faculties[game.facultyAtTurn]
        val cardStartX = (OWN_STACK_WIDTH - STACK_SLOT_WIDTH) / 2.0
        val cardStepY = 22.0

        updateStackSelectionVisuals()

        ResearchArea.entries.forEach { area ->
            val stackPane = ownStackAreas.getValue(area)
            stackPane.clear()

            val onAreaClicked: () -> Unit = {
                val alreadySelected = (selection.ownArea == area)
                updateSelection {
                    ownArea = if (alreadySelected) null else area
                }
                updateStackSelectionVisuals()
            }

            stackPane.onMouseClicked = { onAreaClicked() }

            val stack = activeFaculty.researchActivity[area] ?: emptyList()
            val stepY = fittingStep(stack.size, cardStepY, OWN_STACK_HEIGHT - 2 * 10.0, STACK_SLOT_HEIGHT)
            stack.forEachIndexed { index, card ->
                val cardView = createCardView(card).apply {
                    posX = cardStartX
                    posY = 10.0 + index * stepY
                    onMouseClicked = { onAreaClicked() }
                }
                stackPane.add(cardView)
            }
        }
    }

    /**
     * Zeichnet den verdeckten Nachziehstapel in [drawStackArea]
     * und die oberste Karte des Ablagestapels in [discardStackArea].
     */
    private fun updateDrawAndDiscardStacks(game: FacultyGame) {
        drawStackArea.clear()
        drawStackArea.visual = VISUAL_SLOT

        if (game.drawStack.isNotEmpty()) {
            if (drawStackBackView.parent != null) {
                drawStackBackView.removeFromParent()
            }
            drawStackArea.add(drawStackBackView)
        }

        discardStackArea.clear()
        discardStackArea.visual = VISUAL_SLOT

        val topDiscardCard = game.discardStack.lastOrNull()
        if (topDiscardCard != null) {
            val discardCardView = createCardView(topDiscardCard).apply {
                posX = 0.0
                posY = 0.0
                showFront()
            }
            discardStackArea.add(discardCardView)
        }
        drawStackLabel.text = "Stapel ${game.drawStack.size}"
        discardStackLabel.text = "Ablage ${game.discardStack.size}"
    }

    /**
     * Öffnet den Auswahldialog für „Strategie anpassen“ mit den beiden [drawnCards].
     */
    private fun showAdaptStrategyDialog(drawnCards: List<ResearchCard>) {
        adaptStrategyOverlay.clear()

        val background = Label(
            width = DIALOG_WIDTH,
            height = DIALOG_HEIGHT,
            visual = VISUAL_GOLD
        )
        val innerBox = Label(
            posX = DIALOG_BORDER,
            posY = DIALOG_BORDER,
            width = DIALOG_WIDTH - 2 * DIALOG_BORDER,
            height = DIALOG_HEIGHT - 2 * DIALOG_BORDER,
            visual = VISUAL_DARK_PANEL
        )

        val titleLabel = Label(
            posX = DIALOG_PADDING,
            posY = DIALOG_TITLE_Y,
            width = DIALOG_TEXT_WIDTH,
            height = DIALOG_TITLE_HEIGHT,
            text = "STRATEGIE ANPASSEN",
            font = FONT_SECTION_TITLE,
            alignment = Alignment.CENTER
        )
        val instructionLabel = Label(
            posX = DIALOG_PADDING,
            posY = DIALOG_TEXT_Y,
            width = DIALOG_TEXT_WIDTH,
            height = DIALOG_TEXT_HEIGHT,
            text = "Wähle eine Karte zum Behalten (die andere wird abgeworfen):",
            font = FONT_CAPTION_TEXT,
            alignment = Alignment.CENTER
        )

        adaptStrategyOverlay.add(background)
        adaptStrategyOverlay.add(innerBox)
        adaptStrategyOverlay.add(titleLabel)
        adaptStrategyOverlay.add(instructionLabel)

        val cardViews = mutableListOf<CardView>()
        val defaultY = 95.0
        val selectedY = 80.0
        val cardPositionsX = listOf(115.0, 260.0)

        drawnCards.forEachIndexed { index, card ->
            val cardView = createCardView(card).apply {
                posX = cardPositionsX.getOrElse(index) { 115.0 }
                posY = if (selection.keptCard == card) selectedY else defaultY
            }
            cardViews.add(cardView)

            cardView.onMouseClicked = {
                updateSelection {
                    keptCard = card
                }

                cardViews.forEach { it.posY = defaultY }
                cardView.posY = selectedY
            }

            adaptStrategyOverlay.add(cardView)
        }

        adaptStrategyOverlay.isVisible = true
    }

    /**
     * Zeichnet die Forschungsstapel und Publikationen einer gegnerischen [faculty] in ihr [panel].
     */
    private fun updateOpponentCards(faculty: Faculty, panel: OpponentPanel) {
        panel.cardArea.clear()

        val stackWidth = 65.0
        val stackHeight = 105.0
        val stackGap = 12.0
        val cardStepY = 14.0

        ResearchArea.entries.forEachIndexed { index, area ->
            val stack = faculty.researchActivity[area] ?: emptyList()
            val stackX = index * (stackWidth + stackGap)

            val stepY = fittingStep(stack.size, cardStepY, OPPONENT_CARDS_HEIGHT - SELECTION_BORDER, stackHeight)
            val isSelected = (selection.targetFaculty == faculty && selection.targetArea == area)
            val slotBox = Label(
                posX = stackX - SELECTION_BORDER,
                posY = 0.0,
                width = stackWidth + 2 * SELECTION_BORDER,
                height = stackHeight + (stack.size - 1).coerceAtLeast(0) * stepY + 2 * SELECTION_BORDER,
                visual = if (isSelected) VISUAL_GOLD else VISUAL_SLOT
            ).apply {
                onMouseClicked = { onOpponentStackClicked(faculty, area, stack) }
            }
            panel.cardArea.add(slotBox)

            stack.forEachIndexed { cardIndex, card ->
                val cardView = createCardView(card, width = stackWidth, height = stackHeight).apply {
                    posX = stackX
                    posY = SELECTION_BORDER + cardIndex * stepY
                    onMouseClicked = { onOpponentStackClicked(faculty, area, stack) }
                }
                panel.cardArea.add(cardView)
            }
        }

        val pubStartX = 4 * (stackWidth + stackGap) + 40.0
        val pubStepX = fittingStep(faculty.publications.size, 20.0, panel.cardArea.width - pubStartX, stackWidth)

        faculty.publications.forEachIndexed { index, card ->
            val cardView = createCardView(card, width = stackWidth, height = stackHeight).apply {
                posX = pubStartX + index * pubStepX
                posY = 0.0
            }
            panel.cardArea.add(cardView)
        }
    }

    /**
     * Wählt den gegnerischen Stapel [area] von [faculty] für „Konkurrenz ausbremsen“ an oder ab.
     */
    private fun onOpponentStackClicked(faculty: Faculty, area: ResearchArea, stack: List<ResearchCard>) {
        if (stack.isNotEmpty()) {
            val alreadySelected = (selection.targetFaculty == faculty && selection.targetArea == area)
            updateSelection {
                targetFaculty = if (alreadySelected) null else faculty
                targetArea = if (alreadySelected) null else area
            }
            if (rootService.currentGame != null) {
                updateOpponents()
            }
        }
    }

    /**
     * Zeichnet die publizierten Karten der aktiven Fakultät in die [publicationsArea].
     */
    private fun updatePublications(game: FacultyGame) {
        publicationsArea.clear()
        val activeFaculty = game.faculties[game.facultyAtTurn]
        val cardStepX =
            fittingStep(activeFaculty.publications.size, 35.0, PUBLICATIONS_WIDTH - 2 * 15.0, STACK_SLOT_WIDTH)

        activeFaculty.publications.forEachIndexed { index, card ->
            val cardView = createCardView(card).apply {
                posX = 15.0 + index * cardStepX
                posY = 15.0
            }
            publicationsArea.add(cardView)
        }
    }

    private fun updateActionControls() {
        val game = rootService.currentGame ?: return
        val isGameOver = game.endgameTurnsLeft == 0
        val isLocked = adaptStrategyPending || isGameOver

        actionButtons.forEach { (action, button) ->
            val isExecutable = !isLocked && action.isExecutable(game)
            button.isDisabled = !isExecutable
            button.visual = when {
                action == selectedAction -> actionVisual(action, FONT_ACTION_SELECTED, VISUAL_GOLD)
                isExecutable -> actionVisual(action, FONT_ACTION, VISUAL_ACTION_ENABLED)
                else -> actionVisual(action, FONT_ACTION_DISABLED, VISUAL_ACTION_DISABLED)
            }
        }

        val canSubmit = !isGameOver && isSelectionComplete()
        submitButton.isDisabled = !canSubmit
        submitButton.visual = if (canSubmit) VISUAL_GOLD else VISUAL_BUTTON_DISABLED

        endTurnButton.isDisabled = isLocked
        endTurnButton.visual = if (isLocked) VISUAL_BUTTON_DISABLED else VISUAL_GOLD

        hintLabel.text = when {
            adaptStrategyPending -> keptCardHint(selection.keptCard)
            else -> selectedAction?.hint(selection).orEmpty()
        }
    }

    private fun isSelectionComplete(): Boolean =
        if (adaptStrategyPending) {
            selection.keptCard != null
        } else {
            selectedAction?.isSelectionComplete(selection) == true
        }

    private fun callService(serviceCall: () -> Unit) {
        try {
            serviceCall()
        } catch (exception: IllegalArgumentException) {
            showError(exception.message ?: "Ungültige Auswahl.")
        } catch (exception: IllegalStateException) {
            showError(exception.message ?: "Die Aktion ist gerade nicht möglich.")
        }
    }

    private fun showError(message: String) {
        statusLabel.text = "Fehler: $message"
    }

    private fun addHeader() {
        val headerBackground = Label(width = SCENE_WIDTH, height = HEADER_HEIGHT, visual = VISUAL_HEADER_BG)
        val drawCaption = headerLabel(DRAW_CAPTION_X, DRAW_CAPTION_WIDTH, "Nachziehstapel:", FONT_HEADER_TEXT)
        val pointsCaption = headerLabel(POINTS_CAPTION_X, POINTS_CAPTION_WIDTH, "Punkte:", FONT_HEADER_TEXT)
        drawCaption.alignment = Alignment.CENTER_RIGHT
        pointsCaption.alignment = Alignment.CENTER_RIGHT

        addComponents(
            headerBackground,
            activeCaptionLabel,
            activeNameLabel,
            apCaptionLabel,
            apDotsLabel,
            apTextLabel,
            drawCaption,
            drawCountLabel,
            pointsCaption,
            pointsLabel
        )
        turnOrderPills.forEach { addComponents(it) }
        turnOrderArrows.forEach { addComponents(it) }
    }

    private fun addLiteraturePanel() {
        addPanel(LITERATURE_PANEL_Y, LITERATURE_PANEL_HEIGHT, VISUAL_PANEL_BORDER, VISUAL_DARK_PANEL)
        val title = Label(
            posX = LITERATURE_X,
            posY = LITERATURE_TITLE_Y,
            width = LITERATURE_TITLE_WIDTH,
            height = LITERATURE_TITLE_HEIGHT,
            text = "ZENTRALE LITERATUR",
            font = FONT_SECTION_TITLE,
            alignment = Alignment.CENTER_LEFT
        )
        val orderLabel = Label(
            posX = LITERATURE_ORDER_X,
            posY = LITERATURE_TITLE_Y,
            width = LITERATURE_ORDER_WIDTH,
            height = LITERATURE_TITLE_HEIGHT,
            text = "← älteste · neueste →",
            font = FONT_CAPTION_TEXT,
            alignment = Alignment.CENTER_RIGHT
        )
        addComponents(title, orderLabel, drawStackArea, literatureArea, discardStackArea)
        addComponents(drawStackLabel, discardStackLabel)
    }

    private fun addOwnFacultyPanel() {
        addPanel(OWN_PANEL_Y, OWN_PANEL_HEIGHT, VISUAL_GOLD, VISUAL_PANEL_BG)
        val visibilityLabel = Label(
            posX = OWN_VISIBILITY_X,
            posY = OWN_TITLE_Y,
            width = OWN_VISIBILITY_WIDTH,
            height = OWN_TITLE_HEIGHT,
            text = "Stapel sind für alle sichtbar",
            font = FONT_CAPTION_TEXT,
            alignment = Alignment.CENTER_RIGHT
        )
        val publicationsLabel = Label(
            posX = PUBLICATIONS_X,
            posY = OWN_STACK_LABEL_Y,
            width = PUBLICATIONS_WIDTH,
            height = OWN_STACK_LABEL_HEIGHT,
            text = "Publikationsstapel",
            font = FONT_CAPTION_TEXT,
            alignment = Alignment.CENTER_LEFT
        )
        addComponents(ownTitleLabel, visibilityLabel, publicationsLabel, publicationsArea)
        addComponents(handLabel, handArea, hintLabel)
        ResearchArea.entries.forEach { area ->
            addComponents(stackSymbolLabel(area), ownStackLabels.getValue(area), ownStackAreas.getValue(area))
        }
    }

    private fun addActionPanel() {
        addSidePanel(ACTION_PANEL_Y, ACTION_PANEL_HEIGHT, VISUAL_PANEL_BG)
        val title = Label(
            posX = ACTION_X,
            posY = ACTION_TITLE_Y,
            width = ACTION_WIDTH,
            height = ACTION_TITLE_HEIGHT,
            text = "AKTIONEN",
            font = FONT_SECTION_TITLE,
            alignment = Alignment.CENTER_LEFT
        )
        addComponents(title)
        actionButtons.values.forEach { addComponents(it) }
        addComponents(submitButton, statusLabel)
    }

    private fun addLogPanel() {
        addSidePanel(LOG_PANEL_Y, LOG_PANEL_HEIGHT, VISUAL_DARK_PANEL)
        val title = Label(
            posX = LOG_CONTENT_X,
            posY = LOG_TITLE_Y,
            width = LOG_CONTENT_WIDTH,
            height = ACTION_TITLE_HEIGHT,
            text = "LOG",
            font = FONT_SECTION_TITLE,
            alignment = Alignment.CENTER_LEFT
        )

        // Steht direkt hinter „LOG“, rechts sitzen Vollbild- und Menü-Button
        val orderLabel = Label(
            posX = LOG_ORDER_X,
            posY = LOG_TITLE_Y,
            width = EXPAND_BUTTON_X - LOG_ORDER_X - LOG_BUTTON_GAP,
            height = ACTION_TITLE_HEIGHT,
            text = "↓ neueste oben",
            font = FONT_CAPTION_TEXT,
            alignment = Alignment.CENTER_LEFT
        )

        addComponents(title, orderLabel, logArea, expandLogButton, backToMenuButton)
    }

    private fun addPanel(posY: Int, height: Int, border: ColorVisual, fill: ColorVisual) {
        addFramedPanel(PANEL_X, posY, OPPONENTS_WIDTH, height, border, fill)
    }

    private fun addSidePanel(posY: Int, height: Int, fill: ColorVisual) {
        addFramedPanel(SIDE_X, posY, SIDE_WIDTH, height, VISUAL_PANEL_BORDER, fill)
    }

    private fun addFramedPanel(posX: Int, posY: Int, width: Int, height: Int, border: ColorVisual, fill: ColorVisual) {
        framedPanel(posX, posY, width, height, border, fill).forEach { addComponents(it) }
    }

    /**
     * Bereich für eine gegnerische Fakultät. Handkarten bleiben verdeckt, nur ihre Anzahl wird gezeigt.
     */
    private class OpponentPanel {
        private val border = Label(posY = OPPONENT_Y - BORDER_THICKNESS, visual = VISUAL_PANEL_BORDER)
        private val fill = Label(posY = OPPONENT_Y, height = OPPONENT_HEIGHT, visual = VISUAL_PANEL_BG)
        private val nameLabel = Label(
            posY = OPPONENT_TEXT_Y,
            height = OPPONENT_TEXT_HEIGHT,
            font = FONT_OPPONENT_NAME,
            alignment = Alignment.CENTER_LEFT
        )
        private val handLabel = Label(
            posY = OPPONENT_TEXT_Y,
            width = OPPONENT_HAND_WIDTH,
            height = OPPONENT_TEXT_HEIGHT,
            font = FONT_HEADER_TEXT,
            alignment = Alignment.CENTER_RIGHT
        )
        private val pointsLabel = Label(
            posY = OPPONENT_TEXT_Y,
            width = OPPONENT_POINTS_WIDTH,
            height = OPPONENT_TEXT_HEIGHT,
            font = FONT_HEADER_POINTS,
            alignment = Alignment.CENTER_RIGHT
        )

        /**
         * Platz für die Stapel und Publikationen dieser Fakultät.
         */
        val cardArea: Pane<ComponentView> =
            Pane(posY = OPPONENT_CARDS_Y, width = OPPONENTS_WIDTH, height = OPPONENT_CARDS_HEIGHT)

        /**
         * Alle Komponenten des Panels in Zeichenreihenfolge.
         */
        val components: List<ComponentView> = listOf(border, fill, nameLabel, handLabel, pointsLabel, cardArea)

        /**
         * Ob das Panel angezeigt wird. Bei weniger als 4 Fakultäten werden nicht alle gebraucht.
         */
        var isShown: Boolean = true
            set(value) {
                field = value
                components.forEach { it.isVisible = value }
            }

        init {
            border.height = (OPPONENT_HEIGHT + 2 * BORDER_THICKNESS).toDouble()
        }

        /**
         * Platziert das Panel und zeigt die Daten von [faculty].
         *
         * @param faculty die anzuzeigende Fakultät
         * @param posX linker Rand des Panels
         * @param width Breite des Panels
         */
        fun show(faculty: Faculty, posX: Int, width: Int) {
            border.posX = (posX - BORDER_THICKNESS).toDouble()
            border.width = (width + 2 * BORDER_THICKNESS).toDouble()
            fill.posX = posX.toDouble()
            fill.width = width.toDouble()

            val contentX = posX + PANEL_PADDING
            val contentWidth = width - 2 * PANEL_PADDING
            val pointsX = contentX + contentWidth - OPPONENT_POINTS_WIDTH

            nameLabel.posX = contentX.toDouble()
            nameLabel.width = (contentWidth - OPPONENT_HAND_WIDTH - OPPONENT_POINTS_WIDTH).toDouble()
            nameLabel.text = shorten(faculty.name, MAX_OPPONENT_NAME_CHARS)
            val pointsText = "${faculty.researchPoints} P"
            val pointsTextWidth = pointsText.length * OPPONENT_POINTS_CHAR_WIDTH
            handLabel.posX = (pointsX + OPPONENT_POINTS_WIDTH - pointsTextWidth - OPPONENT_HAND_WIDTH).toDouble()
            handLabel.text = "Hand ${faculty.handCards.size} ·"
            pointsLabel.posX = pointsX.toDouble()
            pointsLabel.text = pointsText
            cardArea.posX = contentX.toDouble()
            cardArea.width = contentWidth.toDouble()
        }
    }

    private companion object {
        const val SCENE_WIDTH = 1920
        const val SCENE_HEIGHT = 1080
        const val MAX_FACULTIES = 4
        const val MAX_ACTION_POINTS = 2
        const val MAX_HAND_CARDS = 6

        const val BORDER_THICKNESS = 2
        const val PANEL_PADDING = 16
        const val PANEL_GAP = 17

        const val HEADER_HEIGHT = 77
        const val HEADER_CAPTION_X = 28
        const val HEADER_CAPTION_WIDTH = 90
        const val HEADER_NAME_X = 132
        const val HEADER_NAME_WIDTH = 240
        const val HEADER_NAME_CHAR_WIDTH = 17
        const val MAX_HEADER_NAME_CHARS = 12
        const val AP_GAP = 34
        const val AP_CAPTION_WIDTH = 40
        const val AP_DOTS_WIDTH = 62
        const val AP_TEXT_WIDTH = 170

        const val PILL_Y = 20
        const val PILL_HEIGHT = 36
        const val PILL_PADDING = 34
        const val PILL_CHAR_WIDTH = 11
        const val MAX_PILL_CHARS = 9
        const val ARROW_WIDTH = 36

        const val DRAW_CAPTION_X = 1490
        const val DRAW_CAPTION_WIDTH = 190
        const val DRAW_COUNT_X = 1686
        const val POINTS_CAPTION_X = 1740
        const val POINTS_CAPTION_WIDTH = 96
        const val POINTS_X = 1842
        const val HEADER_COUNT_WIDTH = 50

        const val PANEL_X = 17
        const val OPPONENTS_WIDTH = 1415

        const val OPPONENT_Y = 96
        const val OPPONENT_HEIGHT = 182
        const val OPPONENT_TEXT_Y = 110
        const val OPPONENT_TEXT_HEIGHT = 30
        const val OPPONENT_HAND_WIDTH = 110
        const val OPPONENT_POINTS_WIDTH = 66
        const val OPPONENT_POINTS_CHAR_WIDTH = 12
        const val OPPONENT_CARDS_Y = 148
        const val OPPONENT_CARDS_HEIGHT = 120
        const val SELECTION_BORDER = 3.0
        const val MAX_OPPONENT_NAME_CHARS = 18

        const val LITERATURE_PANEL_Y = 293
        const val LITERATURE_PANEL_HEIGHT = 211
        const val STACK_SLOT_Y = 329
        const val STACK_SLOT_WIDTH = 84
        const val STACK_SLOT_HEIGHT = 141
        const val DRAW_STACK_X = 39
        const val DISCARD_STACK_X = 1328
        const val LITERATURE_X = 147
        const val LITERATURE_TITLE_Y = 307
        const val LITERATURE_TITLE_WIDTH = 400
        const val LITERATURE_TITLE_HEIGHT = 26
        const val LITERATURE_ORDER_X = 1100
        const val LITERATURE_ORDER_WIDTH = 304
        const val LITERATURE_CARDS_Y = 337
        const val LITERATURE_WIDTH = 1157
        const val LITERATURE_HEIGHT = 114

        const val OWN_PANEL_Y = 521
        const val OWN_PANEL_HEIGHT = 538
        const val OWN_CONTENT_X = 40
        const val OWN_TITLE_Y = 535
        const val OWN_TITLE_WIDTH = 760
        const val OWN_TITLE_HEIGHT = 30
        const val OWN_VISIBILITY_X = 1010
        const val OWN_VISIBILITY_WIDTH = 400
        const val OWN_STACK_LABEL_Y = 575
        const val OWN_STACK_LABEL_HEIGHT = 24
        const val OWN_STACK_Y = 604
        const val OWN_STACK_WIDTH = 174
        const val OWN_STACK_HEIGHT = 225
        const val OWN_STACK_STEP = 198
        const val STACK_SYMBOL_WIDTH = 22
        const val MAX_OWN_NAME_CHARS = 30
        const val PUBLICATIONS_X = 832
        const val PUBLICATIONS_WIDTH = 578
        const val HAND_LABEL_Y = 920
        const val HAND_LABEL_WIDTH = 66
        const val HAND_LABEL_HEIGHT = 56
        const val HAND_X = 136
        const val HAND_Y = 830
        const val HAND_WIDTH = 610
        const val HAND_HEIGHT = 240
        const val HINT_X = 762
        const val HINT_Y = 860
        const val HINT_WIDTH = 648
        const val HINT_HEIGHT = 60

        const val SIDE_X = 1450
        const val SIDE_WIDTH = 450
        const val ACTION_PANEL_Y = 96
        const val ACTION_PANEL_HEIGHT = 522
        const val ACTION_X = 1470
        const val ACTION_WIDTH = 410
        const val ACTION_TITLE_Y = 108
        const val ACTION_TITLE_HEIGHT = 26
        const val ACTION_START_Y = 140
        const val ACTION_STEP_Y = 62
        const val ACTION_HEIGHT = 54
        const val ACTION_TEXT_OFFSET = 18
        const val SUBMIT_Y = 514
        const val SUBMIT_HEIGHT = 54
        const val STATUS_Y = 572
        const val STATUS_HEIGHT = 42

        const val LOG_PANEL_Y = 630
        const val LOG_PANEL_HEIGHT = 343
        const val LOG_TITLE_Y = 640
        const val LOG_CONTENT_X = 1470
        const val LOG_CONTENT_Y = 668
        const val LOG_CONTENT_WIDTH = 410
        const val LOG_CONTENT_HEIGHT = 295
        const val MENU_BUTTON_Y = 634
        const val MENU_BUTTON_WIDTH = 180
        const val MENU_BUTTON_HEIGHT = 30
        const val LOG_BUTTON_GAP = 6
        const val EXPAND_BUTTON_WIDTH = 44
        const val EXPAND_BUTTON_X =
            LOG_CONTENT_X + LOG_CONTENT_WIDTH - MENU_BUTTON_WIDTH - LOG_BUTTON_GAP - EXPAND_BUTTON_WIDTH
        const val EXPANDED_BUTTON_MARGIN = 10
        const val LOG_ORDER_X = LOG_CONTENT_X + 50
        const val EXPAND_TEXT = "[ ]"
        const val COLLAPSE_TEXT = "✕"

        const val DIALOG_X = 650
        const val DIALOG_Y = 330
        const val DIALOG_WIDTH = 460
        const val DIALOG_HEIGHT = 300
        const val DIALOG_BORDER = 3
        const val DIALOG_PADDING = 20
        const val DIALOG_TEXT_WIDTH = DIALOG_WIDTH - 2 * DIALOG_PADDING
        const val DIALOG_TITLE_Y = 18
        const val DIALOG_TITLE_HEIGHT = 28
        const val DIALOG_TEXT_Y = 50
        const val DIALOG_TEXT_HEIGHT = 22

        const val END_TURN_Y = 988
        const val END_TURN_HEIGHT = 72

        const val FONT_SIZE_NAME = 28
        const val FONT_SIZE_TITLE = 22
        const val FONT_SIZE_BODY = 20
        const val FONT_SIZE_CAPTION = 18
        const val FONT_SIZE_SMALL = 16
        const val FONT_SIZE_STATUS = 14

        val COLOR_SCENE_BG = Color(25, 46, 40)
        val COLOR_PANEL_BG = Color(33, 56, 49)
        val COLOR_PANEL_BORDER = Color(52, 82, 72)
        val COLOR_DARK_BG = Color(22, 40, 35)
        val COLOR_SLOT = Color(28, 50, 43)
        val COLOR_BUTTON_SECONDARY = Color(42, 70, 61)
        val COLOR_BUTTON_DISABLED = Color(75, 92, 86)
        val COLOR_GOLD = Color(224, 169, 69)
        val COLOR_TEXT_PRIMARY = Color(243, 246, 244)
        val COLOR_TEXT_MUTED = Color(167, 190, 182)
        val COLOR_TEXT_DISABLED = Color(98, 122, 113)
        val COLOR_DARK_TEXT = Color(20, 35, 30)
        val COLOR_ERROR = Color(235, 105, 95)

        val VISUAL_SCENE_BG = ColorVisual(COLOR_SCENE_BG)
        val VISUAL_HEADER_BG = ColorVisual(COLOR_DARK_BG)
        val VISUAL_PANEL_BG = rounded(COLOR_PANEL_BG)
        val VISUAL_PANEL_BORDER = rounded(COLOR_PANEL_BORDER)
        val VISUAL_DARK_PANEL = rounded(COLOR_DARK_BG)
        val VISUAL_SLOT = rounded(COLOR_SLOT)
        val VISUAL_GOLD = rounded(COLOR_GOLD)
        val VISUAL_BUTTON_DISABLED = rounded(COLOR_BUTTON_DISABLED)
        val VISUAL_ACTION_ENABLED = rounded(COLOR_BUTTON_SECONDARY)
        val VISUAL_ACTION_DISABLED = rounded(COLOR_SLOT)
        val VISUAL_PILL = ColorVisual(COLOR_BUTTON_SECONDARY).apply { style.borderRadius = BorderRadius.FULL }
        val VISUAL_PILL_ACTIVE = ColorVisual(COLOR_GOLD).apply { style.borderRadius = BorderRadius.FULL }

        val FONT_HEADER_CAPTION = Font(size = FONT_SIZE_CAPTION, color = COLOR_TEXT_MUTED)
        val FONT_HEADER_NAME = boldFont(FONT_SIZE_NAME, COLOR_TEXT_PRIMARY)
        val FONT_HEADER_TEXT = Font(size = FONT_SIZE_BODY, color = COLOR_TEXT_MUTED)
        val FONT_HEADER_VALUE = boldFont(FONT_SIZE_BODY, COLOR_TEXT_PRIMARY)
        val FONT_HEADER_POINTS = boldFont(FONT_SIZE_BODY, COLOR_GOLD)
        val FONT_AP_DOTS = Font(size = FONT_SIZE_TITLE, color = COLOR_GOLD)
        val FONT_PILL = Font(size = FONT_SIZE_CAPTION, color = COLOR_TEXT_PRIMARY)
        val FONT_PILL_ACTIVE = boldFont(FONT_SIZE_CAPTION, COLOR_DARK_TEXT)
        val FONT_OPPONENT_NAME = boldFont(FONT_SIZE_BODY, COLOR_TEXT_PRIMARY)
        val FONT_PANEL_TITLE = boldFont(FONT_SIZE_BODY, COLOR_TEXT_PRIMARY)
        val FONT_SECTION_TITLE = boldFont(FONT_SIZE_SMALL, COLOR_TEXT_MUTED)
        val FONT_CAPTION_TEXT = Font(size = FONT_SIZE_SMALL, color = COLOR_TEXT_MUTED)
        val FONT_STACK_SYMBOL = Font(size = FONT_SIZE_SMALL, color = COLOR_TEXT_PRIMARY)
        val FONT_STACK_SYMBOL_RED = Font(size = FONT_SIZE_SMALL, color = COLOR_ERROR)
        val FONT_HINT = Font(size = FONT_SIZE_CAPTION, color = COLOR_GOLD)
        val FONT_ACTION = boldFont(FONT_SIZE_CAPTION, COLOR_TEXT_PRIMARY)
        val FONT_ACTION_SELECTED = Font(size = FONT_SIZE_CAPTION, color = COLOR_DARK_TEXT)
        val FONT_ACTION_DISABLED = Font(size = FONT_SIZE_CAPTION, color = COLOR_TEXT_DISABLED)
        val FONT_SUBMIT = boldFont(FONT_SIZE_BODY, COLOR_DARK_TEXT)
        val FONT_MENU_BUTTON = boldFont(FONT_SIZE_SMALL, COLOR_TEXT_PRIMARY)
        val FONT_LOG = Font(size = FONT_SIZE_SMALL, color = COLOR_TEXT_PRIMARY)
        val FONT_END_TURN = boldFont(FONT_SIZE_TITLE, COLOR_DARK_TEXT)
        val FONT_STATUS_ERROR = boldFont(FONT_SIZE_STATUS, COLOR_ERROR)

        /**
         * Kurznamen der Gebiete wie im Storyboard.
         */
        val ResearchArea.shortLabel: String
            get() = when (this) {
                ResearchArea.AI -> "KI"
                ResearchArea.IT_SECURITY -> "IT-Sich."
                ResearchArea.SE -> "SE"
                ResearchArea.THEORETICAL -> "Theorie"
            }

        fun boldFont(size: Int, color: Color): Font =
            Font(size = size, color = color, fontWeight = Font.FontWeight.BOLD)

        fun rounded(color: Color): ColorVisual =
            ColorVisual(color).apply { style.borderRadius = BorderRadius.MEDIUM }

        fun shorten(name: String, maxChars: Int): String =
            if (name.length > maxChars) name.take(maxChars - 1) + "…" else name

        /**
         * Abstand zwischen überlappenden Karten: [maxStep], außer die Karten würden sonst über
         * [available] hinauslaufen. Dann rücken sie so weit zusammen, dass alle hineinpassen.
         *
         * @param count Anzahl der Karten
         * @param maxStep Abstand bei wenigen Karten
         * @param available verfügbarer Platz in Stapelrichtung
         * @param cardSize Größe einer Karte in Stapelrichtung
         * @return der zu verwendende Abstand, nie negativ
         */
        fun fittingStep(count: Int, maxStep: Number, available: Number, cardSize: Number): Double {
            if (count <= 1) return maxStep.toDouble()
            val fitting = (available.toDouble() - cardSize.toDouble()) / (count - 1)
            return fitting.coerceIn(0.0, maxStep.toDouble())
        }

        fun ownStackX(area: ResearchArea): Int = OWN_CONTENT_X + area.ordinal * OWN_STACK_STEP

        fun headerLabel(posX: Int, width: Int, text: String, font: Font): Label = Label(
            posX = posX,
            posY = 0,
            width = width,
            height = HEADER_HEIGHT,
            text = text,
            font = font,
            alignment = Alignment.CENTER_LEFT
        )

        fun stackSymbolLabel(area: ResearchArea): Label = Label(
            posX = ownStackX(area),
            posY = OWN_STACK_LABEL_Y,
            width = STACK_SYMBOL_WIDTH,
            height = OWN_STACK_LABEL_HEIGHT,
            text = area.symbol,
            font = if (area == ResearchArea.IT_SECURITY || area == ResearchArea.SE) {
                FONT_STACK_SYMBOL_RED
            } else {
                FONT_STACK_SYMBOL
            },
            alignment = Alignment.CENTER_LEFT
        )

        fun framedPanel(
            posX: Int,
            posY: Int,
            width: Int,
            height: Int,
            border: ColorVisual,
            fill: ColorVisual
        ): List<Label> = listOf(
            Label(
                posX = posX - BORDER_THICKNESS,
                posY = posY - BORDER_THICKNESS,
                width = width + 2 * BORDER_THICKNESS,
                height = height + 2 * BORDER_THICKNESS,
                visual = border
            ),
            Label(posX = posX, posY = posY, width = width, height = height, visual = fill)
        )

        fun actionVisual(action: GameAction, font: Font, background: ColorVisual): CompoundVisual = CompoundVisual(
            background,
            TextVisual(action.label, font, Alignment.CENTER_LEFT, ACTION_TEXT_OFFSET),
            TextVisual("${action.actionPoints} AP", font, Alignment.CENTER_RIGHT, -ACTION_TEXT_OFFSET)
        )
    }
}


private fun ResearchArea.toCardSuit(): CardSuit = when (this) {
    ResearchArea.AI -> CardSuit.SPADES
    ResearchArea.IT_SECURITY -> CardSuit.HEARTS
    ResearchArea.SE -> CardSuit.DIAMONDS
    ResearchArea.THEORETICAL -> CardSuit.CLUBS
}

private fun ResearchTopic.toCardValue(): CardValue = when (this) {
    ResearchTopic.ACE -> CardValue.ACE
    ResearchTopic.TWO -> CardValue.TWO
    ResearchTopic.THREE -> CardValue.THREE
    ResearchTopic.FOUR -> CardValue.FOUR
    ResearchTopic.FIVE -> CardValue.FIVE
    ResearchTopic.SIX -> CardValue.SIX
    ResearchTopic.SEVEN -> CardValue.SEVEN
    ResearchTopic.EIGHT -> CardValue.EIGHT
    ResearchTopic.NINE -> CardValue.NINE
    ResearchTopic.TEN -> CardValue.TEN
    ResearchTopic.JACK -> CardValue.JACK
    ResearchTopic.QUEEN -> CardValue.QUEEN
    ResearchTopic.KING -> CardValue.KING
}