package gui

import service.Refreshable
import service.RootService
import tools.aqua.bgw.components.uicomponents.Button
import tools.aqua.bgw.components.uicomponents.CheckBox
import tools.aqua.bgw.components.uicomponents.Label
import tools.aqua.bgw.components.uicomponents.TextField
import tools.aqua.bgw.core.Alignment
import tools.aqua.bgw.core.Color
import tools.aqua.bgw.core.MenuScene
import tools.aqua.bgw.util.Font
import tools.aqua.bgw.visual.ColorVisual

/**
 * Startmenü: 2 bis 4 Fakultätsnamen und ihre Reihenfolge festlegen, bevor ein neues Spiel startet.
 *
 * Setzt Issue #47 (Startmenü: Spieler*innen und Reihenfolge) um:
 * - 2 bis 4 nicht leere, verschiedene Namen eingeben (Felder zwischen 2 und 4 hinzufügen/entfernen).
 * - Reihenfolge per Hoch/Runter ändern, direkt mischen oder per [randomizeCheckbox] auslosen lassen.
 * - Prüft die Eingaben sofort, [startGameButton] ist nur bei 2 bis 4 gültigen, verschiedenen Namen aktiv.
 * - Exceptions der Service-Schicht werden angezeigt statt abzustürzen.
 * - [quitButton] („Beenden“) schließt das Programm.
 *
 * @param rootService zentrale [RootService]-Instanz, startet das neue Spiel
 * @param onQuit wird beim Klick auf „Beenden“ aufgerufen
 */
class StartMenuScene(
    private val rootService: RootService,
    private val onQuit: () -> Unit = {}
) : MenuScene(SCENE_WIDTH, SCENE_HEIGHT), Refreshable {

    private var activePlayerCount: Int = INITIAL_PLAYERS

    private val playerInputs: MutableList<TextField> = MutableList(MAX_PLAYERS) { index ->
        createPlayerTextField(index)
    }

    private val rowNumberLabels: List<Label> = List(MAX_PLAYERS) { index ->
        Label(
            posX = NUMBER_X,
            posY = rowY(index),
            width = NUMBER_WIDTH,
            height = ROW_HEIGHT,
            text = "${index + 1}",
            font = FONT_ROW_NUMBER,
            alignment = Alignment.CENTER
        )
    }

    private val rowHeaderLabels: List<Label> = List(MAX_PLAYERS) { index ->
        Label(
            posX = INPUT_X,
            posY = rowY(index) - ROW_HEADER_OFFSET_Y,
            width = INPUT_WIDTH,
            height = ROW_HEADER_HEIGHT,
            text = "FAKULTÄT ${index + 1}",
            font = FONT_ROW_HEADER,
            alignment = Alignment.CENTER_LEFT
        )
    }

    private val upButtons: List<Button> = List(MAX_PLAYERS) { index ->
        Button(
            posX = UP_BUTTON_X,
            posY = rowY(index),
            width = ICON_BUTTON_SIZE,
            height = ROW_HEIGHT,
            text = "↑",
            font = FONT_ICON_BUTTON,
            visual = VISUAL_BUTTON_SECONDARY
        ).apply {
            onMouseClicked = { movePlayerUp(index) }
        }
    }

    private val downButtons: List<Button> = List(MAX_PLAYERS) { index ->
        Button(
            posX = DOWN_BUTTON_X,
            posY = rowY(index),
            width = ICON_BUTTON_SIZE,
            height = ROW_HEIGHT,
            text = "↓",
            font = FONT_ICON_BUTTON,
            visual = VISUAL_BUTTON_SECONDARY
        ).apply {
            onMouseClicked = { movePlayerDown(index) }
        }
    }

    private val removeButtons: List<Button> = List(MAX_PLAYERS) { index ->
        Button(
            posX = REMOVE_BUTTON_X,
            posY = rowY(index),
            width = ICON_BUTTON_SIZE,
            height = ROW_HEIGHT,
            text = "×",
            font = FONT_REMOVE_BUTTON,
            visual = VISUAL_BUTTON_SECONDARY
        ).apply {
            onMouseClicked = { removePlayerField(index) }
        }
    }

    private val playerCountLabel: Label = Label(
        posX = COUNT_LABEL_X,
        posY = CARD_HEADER_Y,
        width = COUNT_LABEL_WIDTH,
        height = CARD_HEADER_HEIGHT,
        text = "$INITIAL_PLAYERS / $MAX_PLAYERS",
        font = FONT_COUNT_LABEL,
        alignment = Alignment.CENTER_RIGHT
    )

    private val addPlayerButton: Button = Button(
        posX = INPUT_X,
        posY = rowY(INITIAL_PLAYERS),
        width = ADD_BUTTON_WIDTH,
        height = ROW_HEIGHT,
        text = "+ Spieler hinzufügen",
        font = FONT_ADD_BUTTON,
        visual = VISUAL_BUTTON_SECONDARY
    ).apply {
        onMouseClicked = { addPlayerField() }
    }

    private val randomizeCheckbox: CheckBox = CheckBox(
        posX = INPUT_X,
        posY = CHECKBOX_Y,
        width = CHECKBOX_WIDTH,
        height = CHECKBOX_HEIGHT,
        text = "Reihenfolge beim Start zusätzlich zufällig auslosen",
        isChecked = false,
        font = FONT_CHECKBOX
    )

    private val shuffleNowButton: Button = Button(
        posX = SHUFFLE_BUTTON_X,
        posY = ACTION_ROW_Y,
        width = SHUFFLE_BUTTON_WIDTH,
        height = ACTION_BUTTON_HEIGHT,
        text = "Zufällige Reihenfolge",
        font = FONT_SHUFFLE_BUTTON,
        visual = VISUAL_BUTTON_SECONDARY
    ).apply {
        onMouseClicked = { shufflePlayerInputs() }
    }

    private val startGameButton: Button = Button(
        posX = START_BUTTON_X,
        posY = ACTION_ROW_Y,
        width = START_BUTTON_WIDTH,
        height = ACTION_BUTTON_HEIGHT,
        text = "Spiel starten",
        font = FONT_START_BUTTON,
        visual = VISUAL_BUTTON_DISABLED
    ).apply {
        isDisabled = true
        onMouseClicked = { startGame() }
    }

    /**
     * Button, der das Programm schließt.
     */
    val quitButton: Button = Button(
        posX = INPUT_X,
        posY = QUIT_BUTTON_Y,
        width = FULL_CONTROLS_WIDTH,
        height = QUIT_BUTTON_HEIGHT,
        text = "Beenden",
        font = FONT_QUIT_BUTTON,
        visual = VISUAL_INPUT_BG
    ).apply {
        onMouseClicked = { onQuit() }
    }

    private val statusLabel: Label = Label(
        posX = INPUT_X,
        posY = STATUS_LABEL_Y,
        width = FULL_CONTROLS_WIDTH,
        height = STATUS_LABEL_HEIGHT,
        text = DEFAULT_HINT_TEXT,
        font = FONT_STATUS_MUTED,
        alignment = Alignment.CENTER_LEFT
    )

    init {
        background = VISUAL_SCENE_BG
        // Deckend statt halbdurchsichtig, damit hinter dem Menü nichts vom Spielfeld durchscheint
        backgroundOpacity = 1.0

        addHeroSection()
        addCardContainer()
        for (index in 0 until MAX_PLAYERS) {
            addComponents(
                rowHeaderLabels[index],
                rowNumberLabels[index],
                playerInputs[index],
                upButtons[index],
                downButtons[index],
                removeButtons[index]
            )
        }
        addComponents(
            playerCountLabel,
            addPlayerButton,
            randomizeCheckbox,
            shuffleNowButton,
            startGameButton,
            statusLabel,
            quitButton
        )

        updatePlayerRowsLayout()
        validateInputs()
    }

    /**
     * Setzt alte Fehlermeldungen zurück, sobald ein neues Spiel gestartet wurde.
     */
    override fun refreshAfterStartNewGame() {
        statusLabel.text = DEFAULT_HINT_TEXT
        statusLabel.font = FONT_STATUS_MUTED
    }

    private fun createPlayerTextField(index: Int): TextField =
        TextField(
            posX = INPUT_X,
            posY = rowY(index),
            width = INPUT_WIDTH,
            height = ROW_HEIGHT,
            prompt = INPUT_PROMPT,
            font = FONT_INPUT
        ).apply {
            visual = VISUAL_INPUT_BG
            onTextChanged = { validateInputs() }
        }

    private fun addHeroSection() {
        val suitsLabel = Label(
            posX = HERO_X,
            posY = SUITS_Y,
            width = HERO_WIDTH,
            height = SUITS_HEIGHT,
            text = "♠   ♥   ♦   ♣",
            font = Font(size = FONT_SUBTITLE, color = COLOR_GOLD, fontWeight = Font.FontWeight.BOLD),
            alignment = Alignment.CENTER_LEFT
        )
        val titleLabel = Label(
            posX = HERO_X,
            posY = TITLE_Y,
            width = HERO_WIDTH,
            height = TITLE_HEIGHT,
            text = "Fakultäten",
            font = Font(size = FONT_HERO_TITLE, color = COLOR_TEXT_PRIMARY, fontWeight = Font.FontWeight.BOLD),
            alignment = Alignment.CENTER_LEFT
        )
        val subtitleLabel = Label(
            posX = HERO_X,
            posY = SUBTITLE_Y,
            width = HERO_WIDTH,
            height = SUBTITLE_HEIGHT,
            text = "Taktisches Kartenspiel für 2–4 Fakultäten.\nForschen, zitieren, publizieren.",
            font = Font(size = FONT_SUBTITLE, color = COLOR_TEXT_MUTED),
            alignment = Alignment.TOP_LEFT
        )
        val areaAiLabel = createAreaLegendLabel(HERO_X, LEGEND_ROW_1_Y, "♠  Künstliche Intelligenz")
        val areaSecLabel = createAreaLegendLabel(HERO_COL_2_X, LEGEND_ROW_1_Y, "♥  IT-Sicherheit")
        val areaSeLabel = createAreaLegendLabel(HERO_X, LEGEND_ROW_2_Y, "♦  Software Engineering")
        val areaTheoLabel = createAreaLegendLabel(HERO_COL_2_X, LEGEND_ROW_2_Y, "♣  Theoretische Informatik")

        addComponents(
            suitsLabel,
            titleLabel,
            subtitleLabel,
            areaAiLabel,
            areaSecLabel,
            areaSeLabel,
            areaTheoLabel
        )
    }

    private fun createAreaLegendLabel(posX: Int, posY: Int, text: String): Label =
        Label(
            posX = posX,
            posY = posY,
            width = LEGEND_COL_WIDTH,
            height = LEGEND_ROW_HEIGHT,
            text = text,
            font = Font(size = FONT_SMALL, color = COLOR_TEXT_MUTED),
            alignment = Alignment.CENTER_LEFT
        )

    private fun addCardContainer() {
        val cardBorder = Label(
            posX = CARD_X - BORDER_THICKNESS,
            posY = CARD_Y - BORDER_THICKNESS,
            width = CARD_WIDTH + BORDER_THICKNESS * 2,
            height = CARD_HEIGHT + BORDER_THICKNESS * 2,
            visual = VISUAL_CARD_BORDER
        )
        val cardBackground = Label(
            posX = CARD_X,
            posY = CARD_Y,
            width = CARD_WIDTH,
            height = CARD_HEIGHT,
            visual = VISUAL_CARD_BG
        )
        val cardHeader = Label(
            posX = NUMBER_X,
            posY = CARD_HEADER_Y,
            width = CARD_HEADER_WIDTH,
            height = CARD_HEADER_HEIGHT,
            text = "Spieler & Reihenfolge",
            font = Font(size = FONT_CARD_TITLE, color = COLOR_TEXT_PRIMARY, fontWeight = Font.FontWeight.BOLD),
            alignment = Alignment.CENTER_LEFT
        )
        addComponents(cardBorder, cardBackground, cardHeader)
    }

    private fun movePlayerUp(index: Int) {
        if (index !in 1 until activePlayerCount) return
        val previousField = playerInputs[index - 1]
        playerInputs[index - 1] = playerInputs[index]
        playerInputs[index] = previousField
        updatePlayerRowsLayout()
        validateInputs()
    }

    private fun movePlayerDown(index: Int) {
        if (index !in 0 until (activePlayerCount - 1)) return
        val nextField = playerInputs[index + 1]
        playerInputs[index + 1] = playerInputs[index]
        playerInputs[index] = nextField
        updatePlayerRowsLayout()
        validateInputs()
    }

    private fun addPlayerField() {
        if (activePlayerCount >= MAX_PLAYERS) return
        playerInputs[activePlayerCount].text = ""
        activePlayerCount++
        updatePlayerRowsLayout()
        validateInputs()
    }

    private fun removePlayerField(index: Int) {
        if (activePlayerCount <= MIN_PLAYERS || index !in 0 until activePlayerCount) return
        val removedField = playerInputs.removeAt(index)
        removedField.text = ""
        playerInputs.add(removedField)
        activePlayerCount--
        updatePlayerRowsLayout()
        validateInputs()
    }

    private fun shufflePlayerInputs() {
        val activeFields = playerInputs.subList(0, activePlayerCount).toList()
        val (filledFields, emptyFields) = activeFields.partition { field -> field.text.trim().isNotEmpty() }
        if (filledFields.size < MIN_PLAYERS) {
            statusLabel.text = "Bitte mindestens 2 Namen eingeben, um die Reihenfolge auszulosen."
            statusLabel.font = FONT_STATUS_ERROR
            return
        }

        var shuffledFilled = filledFields.shuffled()
        if (filledFields.size > 1) {
            var attempts = 0
            while (shuffledFilled == filledFields && attempts < MAX_SHUFFLE_ATTEMPTS) {
                shuffledFilled = filledFields.shuffled()
                attempts++
            }
        }

        val reorderedActive = shuffledFilled + emptyFields
        for (index in 0 until activePlayerCount) {
            playerInputs[index] = reorderedActive[index]
        }
        updatePlayerRowsLayout()
        validateInputs()
        statusLabel.text = "Reihenfolge wurde zufällig ausgelost."
        statusLabel.font = FONT_STATUS_SUCCESS
    }

    private fun updatePlayerRowsLayout() {
        playerCountLabel.text = "$activePlayerCount / $MAX_PLAYERS"

        for (index in 0 until MAX_PLAYERS) {
            val visible = index < activePlayerCount
            rowHeaderLabels[index].isVisible = visible
            rowNumberLabels[index].isVisible = visible
            playerInputs[index].posY = rowY(index).toDouble()
            playerInputs[index].isVisible = visible
            upButtons[index].isVisible = visible
            downButtons[index].isVisible = visible
            removeButtons[index].isVisible = visible

            upButtons[index].isDisabled = !visible || index == 0
            downButtons[index].isDisabled = !visible || index == activePlayerCount - 1
            removeButtons[index].isDisabled = !visible || activePlayerCount <= MIN_PLAYERS
        }

        val canAddMore = activePlayerCount < MAX_PLAYERS
        addPlayerButton.isVisible = canAddMore
        addPlayerButton.isDisabled = !canAddMore
        if (canAddMore) {
            addPlayerButton.posY = rowY(activePlayerCount).toDouble()
        }
    }

    private fun validateInputs(): Boolean {
        val activeNames = (0 until activePlayerCount).map { index -> playerInputs[index].text.trim() }
        val hasEmptyName = activeNames.any { name -> name.isEmpty() }
        val nonBlankNames = activeNames.filter { name -> name.isNotEmpty() }
        val hasDuplicateName = nonBlankNames.distinct().size != nonBlankNames.size
        val validCount = activePlayerCount in MIN_PLAYERS..MAX_PLAYERS
        val isValid = validCount && !hasEmptyName && !hasDuplicateName

        startGameButton.isDisabled = !isValid
        startGameButton.visual = if (isValid) {
            VISUAL_BUTTON_ENABLED
        } else {
            VISUAL_BUTTON_DISABLED
        }

        when {
            hasDuplicateName -> {
                statusLabel.text = "Jeder Fakultätsname muss eindeutig sein."
                statusLabel.font = FONT_STATUS_ERROR
            }
            hasEmptyName -> {
                statusLabel.text = DEFAULT_HINT_TEXT
                statusLabel.font = FONT_STATUS_MUTED
            }
            else -> {
                statusLabel.text = "Bereit: $activePlayerCount Fakultäten konfiguriert."
                statusLabel.font = FONT_STATUS_SUCCESS
            }
        }
        return isValid
    }

    private fun startGame() {
        val names = (0 until activePlayerCount).map { index -> playerInputs[index].text.trim() }
        try {
            rootService.gameService.startNewGame(names, randomizeCheckbox.isChecked)
        } catch (exception: IllegalArgumentException) {
            showError(exception.message ?: "Ungültige Spieler-Konfiguration.")
        } catch (exception: IllegalStateException) {
            showError(exception.message ?: "Das Spiel konnte nicht gestartet werden.")
        }
    }

    private fun showError(message: String) {
        statusLabel.text = "Fehler: $message"
        statusLabel.font = FONT_STATUS_ERROR
    }

    private companion object {
        const val SCENE_WIDTH = 1920
        const val SCENE_HEIGHT = 1080
        const val MIN_PLAYERS = 2
        const val INITIAL_PLAYERS = 2
        const val MAX_PLAYERS = 4
        const val MAX_SHUFFLE_ATTEMPTS = 16

        const val HERO_X = 160
        const val HERO_WIDTH = 660
        const val SUITS_Y = 350
        const val SUITS_HEIGHT = 36
        const val TITLE_Y = 395
        const val TITLE_HEIGHT = 96
        const val SUBTITLE_Y = 505
        const val SUBTITLE_HEIGHT = 75
        const val LEGEND_ROW_1_Y = 615
        const val LEGEND_ROW_2_Y = 655
        const val HERO_COL_2_X = 460
        const val LEGEND_COL_WIDTH = 280
        const val LEGEND_ROW_HEIGHT = 32

        const val CARD_X = 930
        const val CARD_Y = 170
        const val CARD_WIDTH = 830
        const val CARD_HEIGHT = 740
        const val BORDER_THICKNESS = 2

        const val CARD_HEADER_Y = 210
        const val CARD_HEADER_WIDTH = 450
        const val CARD_HEADER_HEIGHT = 40
        const val COUNT_LABEL_X = 1580
        const val COUNT_LABEL_WIDTH = 106

        const val NUMBER_X = 980
        const val NUMBER_WIDTH = 36
        const val INPUT_X = 1025
        const val INPUT_WIDTH = 495
        const val ROW_START_Y = 290
        const val ROW_STEP_Y = 82
        const val ROW_HEIGHT = 46
        const val ROW_HEADER_OFFSET_Y = 22
        const val ROW_HEADER_HEIGHT = 20

        const val ICON_BUTTON_SIZE = 46
        const val UP_BUTTON_X = 1532
        const val DOWN_BUTTON_X = 1586
        const val REMOVE_BUTTON_X = 1640

        const val ADD_BUTTON_WIDTH = 661

        const val CHECKBOX_Y = 635
        const val CHECKBOX_WIDTH = 550
        const val CHECKBOX_HEIGHT = 36

        const val ACTION_ROW_Y = 690
        const val ACTION_BUTTON_HEIGHT = 54
        const val SHUFFLE_BUTTON_X = 1025
        const val SHUFFLE_BUTTON_WIDTH = 315
        const val START_BUTTON_X = 1356
        const val START_BUTTON_WIDTH = 330

        const val STATUS_LABEL_Y = 760
        const val STATUS_LABEL_HEIGHT = 30
        const val FULL_CONTROLS_WIDTH = 661

        const val QUIT_BUTTON_Y = 810
        const val QUIT_BUTTON_HEIGHT = 44

        const val FONT_HERO_TITLE = 72
        const val FONT_CARD_TITLE = 28
        const val FONT_SUBTITLE = 22
        const val FONT_BODY = 18
        const val FONT_SMALL = 16
        const val FONT_CAPTION = 14

        const val INPUT_PROMPT = "Fakultätsname eingeben…"
        const val DEFAULT_HINT_TEXT =
            "Alle aktiven Felder ausfüllen (2–4 eindeutige Namen), um das Spiel zu starten."

        val COLOR_SCENE_BG = Color(25, 46, 40)
        val COLOR_CARD_BG = Color(33, 56, 49)
        val COLOR_CARD_BORDER = Color(52, 82, 72)
        val COLOR_INPUT_BG = Color(22, 40, 35)
        val COLOR_BUTTON_SECONDARY = Color(42, 70, 61)
        val COLOR_BUTTON_DISABLED = Color(75, 92, 86)
        val COLOR_GOLD = Color(224, 169, 69)
        val COLOR_TEXT_PRIMARY = Color(243, 246, 244)
        val COLOR_TEXT_MUTED = Color(167, 190, 182)
        val COLOR_DARK_TEXT = Color(20, 35, 30)
        val COLOR_ERROR = Color(235, 105, 95)
        val COLOR_SUCCESS = Color(130, 205, 155)

        val VISUAL_SCENE_BG = ColorVisual(COLOR_SCENE_BG)
        val VISUAL_CARD_BG = ColorVisual(COLOR_CARD_BG)
        val VISUAL_CARD_BORDER = ColorVisual(COLOR_CARD_BORDER)
        val VISUAL_INPUT_BG = ColorVisual(COLOR_INPUT_BG)
        val VISUAL_BUTTON_SECONDARY = ColorVisual(COLOR_BUTTON_SECONDARY)
        val VISUAL_BUTTON_DISABLED = ColorVisual(COLOR_BUTTON_DISABLED)
        val VISUAL_BUTTON_ENABLED = ColorVisual(COLOR_GOLD)

        val FONT_INPUT = Font(size = FONT_BODY, color = COLOR_TEXT_PRIMARY)
        val FONT_ROW_NUMBER = Font(size = FONT_SUBTITLE, color = COLOR_GOLD, fontWeight = Font.FontWeight.BOLD)
        val FONT_ROW_HEADER = Font(size = FONT_CAPTION, color = COLOR_TEXT_MUTED, fontWeight = Font.FontWeight.BOLD)
        val FONT_ICON_BUTTON = Font(size = FONT_SUBTITLE, color = COLOR_TEXT_PRIMARY, fontWeight = Font.FontWeight.BOLD)
        val FONT_REMOVE_BUTTON = Font(size = FONT_SUBTITLE, color = COLOR_TEXT_MUTED, fontWeight = Font.FontWeight.BOLD)
        val FONT_COUNT_LABEL = Font(size = FONT_BODY, color = COLOR_TEXT_MUTED)
        val FONT_ADD_BUTTON = Font(size = FONT_SMALL, color = COLOR_TEXT_PRIMARY, fontWeight = Font.FontWeight.BOLD)
        val FONT_CHECKBOX = Font(size = FONT_SMALL, color = COLOR_TEXT_MUTED)
        val FONT_SHUFFLE_BUTTON = Font(size = FONT_SMALL, color = COLOR_GOLD, fontWeight = Font.FontWeight.BOLD)
        val FONT_START_BUTTON = Font(size = FONT_BODY, color = COLOR_DARK_TEXT, fontWeight = Font.FontWeight.BOLD)
        val FONT_QUIT_BUTTON = Font(size = FONT_SMALL, color = COLOR_TEXT_MUTED, fontWeight = Font.FontWeight.BOLD)
        val FONT_STATUS_MUTED = Font(size = FONT_CAPTION, color = COLOR_TEXT_MUTED)
        val FONT_STATUS_ERROR = Font(size = FONT_CAPTION, color = COLOR_ERROR, fontWeight = Font.FontWeight.BOLD)
        val FONT_STATUS_SUCCESS = Font(size = FONT_CAPTION, color = COLOR_SUCCESS)

        fun rowY(index: Int): Int = ROW_START_Y + index * ROW_STEP_Y
    }
}
