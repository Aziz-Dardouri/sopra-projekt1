package gui

import entity.Faculty
import service.Refreshable
import tools.aqua.bgw.components.layoutviews.Pane
import tools.aqua.bgw.components.uicomponents.Button
import tools.aqua.bgw.components.uicomponents.Label
import tools.aqua.bgw.core.Alignment
import tools.aqua.bgw.core.Color
import tools.aqua.bgw.core.MenuScene
import tools.aqua.bgw.util.Font
import tools.aqua.bgw.visual.ColorVisual

/**
 * [MenuScene], die am Ende des Spiels das Ergebnis und die detaillierte Punktwertung anzeigt (Issue #24).
 * Design entspricht den Vorgaben des Storyboards: dunkelgrüner Hintergrund und Gold-Akzente.
 */
class EndgameMenuScene : MenuScene(1920, 1080), Refreshable {

    /** Untertitel über der Gewinner-Anzeige. */
    private val subtitleLabel = Label(
        posX = 0, posY = 120,
        width = 1920, height = 30,
        text = "SPIELENDE • NACHZIEHSTAPEL LEER",
        font = Font(size = 18, color = COLOR_TEXT_MUTED, fontWeight = Font.FontWeight.BOLD),
        alignment = Alignment.CENTER
    )

    /** Haupttitel mit dem Namen und der Gesamtpunktzahl der siegreichen Fakultät. */
    private val titleLabel = Label(
        posX = 0, posY = 170, width = 1920, height = 80,
        font = Font(size = 64, color = COLOR_TEXT_PRIMARY, fontWeight = Font.FontWeight.BOLD),
        alignment = Alignment.CENTER
    )

    /** Container für die Ergebnistabelle aller Fakultäten. */
    private val tablePane = Pane<Label>(
        posX = 160, posY = 320,
        width = 1600, height = 400,
        visual = ColorVisual(COLOR_CARD_BG)
    )

    /** Legende zur Erklärung der vier Bonuskategorien unterhalb der Tabelle. */
    private val legendLabel = Label(
        posX = 0, posY = 750,
        width = 1920, height = 40,
        text = "Sichtbarkeit: +5 / +2 je Teilgebiet • Breite: +3 bei allen 4 Gebieten • " +
            "Schwerpunkt: +4 ab 8 Karten • Angefangen: +1 je ausliegender Karte",
        font = Font(size = 18, color = COLOR_TEXT_MUTED),
        alignment = Alignment.CENTER
    )

    /** Goldener Rahmen hinter dem [exitButton]. */
    private val exitButtonBorder = Label(
        posX = 638, posY = 838,
        width = 304, height = 64,
        visual = ColorVisual(COLOR_GOLD)
    )

    /** Button zum Beenden der Anwendung. */
    val exitButton = Button(
        posX = 640, posY = 840,
        width = 300, height = 60,
        text = "Beenden",
        font = Font(size = 24, color = COLOR_GOLD, fontWeight = Font.FontWeight.BOLD),
        visual = VISUAL_SCENE_BG
    )

    /** Button zum Zurückkehren ins Startmenü für eine neue Partie. */
    val newGameButton = Button(
        posX = 980, posY = 840,
        width = 300, height = 60,
        text = "Neues Spiel",
        font = Font(size = 24, color = COLOR_DARK_TEXT, fontWeight = Font.FontWeight.BOLD),
        visual = ColorVisual(COLOR_GOLD)
    )

    init {
        background = VISUAL_SCENE_BG
        drawTableHeader()
        addComponents(
            subtitleLabel, titleLabel,
            tablePane, legendLabel,
            exitButtonBorder, exitButton, newGameButton
        )
    }

    /**
     * Zeichnet die Kopfzeile der Ergebnistabelle in das [tablePane].
     */
    private fun drawTableHeader() {
        val headers = listOf(
            "#", "FAKULTÄT", "PUBLIKATIONEN", "SICHTBARKEIT", "BREITE", "SCHWERPUNKT", "ANGEFANGEN", "GESAMT"
        )
        for (i in headers.indices) {
            tablePane.add(
                Label(
                    posX = COLUMN_X_POSITIONS[i], posY = 20,
                    width = COLUMN_WIDTHS[i], height = 40,
                    text = headers[i],
                    font = Font(size = 16, color = COLOR_TEXT_MUTED, fontWeight = Font.FontWeight.BOLD),
                    alignment = if (i <= 1) Alignment.CENTER_LEFT else Alignment.CENTER
                )
            )
        }
    }

    /**
     * Aktualisiert die Ergebnistabelle und die Gewinner-Überschrift nach Spielende.
     *
     * @param winner die Fakultät auf dem 1. Platz
     * @param scores Gesamtpunkte aller Fakultäten in absteigender Rangfolge
     * @param bonuses detaillierte Liste der erhaltenen Boni pro Fakultät
     */
    override fun refreshAfterGameEndWithBonuses(
        winner: Faculty,
        scores: Map<Faculty, Int>,
        bonuses: Map<Faculty, List<Pair<String, Int>>>
    ) {
        val scoreStr = scores.getValue(winner).toString()
        titleLabel.text = "${winner.name} gewinnt mit $scoreStr Punkten"

        tablePane.clear()
        drawTableHeader()

        var currentY = 80.0
        var rank = 1

        for ((faculty, score) in scores) {
            // Hintergrund-Highlight für den 1. Platz
            if (rank == 1) {
                tablePane.add(
                    Label(
                        posX = 0, posY = currentY,
                        width = 1600, height = 60,
                        visual = ColorVisual(COLOR_WINNER_ROW_BG)
                    )
                )
            }

            val rankColor = COLOR_GOLD
            val textColor = if (rank == 1) COLOR_TEXT_PRIMARY else COLOR_TEXT_MUTED

            // Echte Bonuswerte aus der Map extrahieren und summieren
            val facBonuses = bonuses[faculty] ?: emptyList()
            val sichtbarkeit = facBonuses
                .filter { it.first.contains("majority") || it.first.contains("second") }
                .sumOf { it.second }
            val breite = facBonuses.filter { it.first == "broad positioning" }.sumOf { it.second }
            val schwerpunkt = facBonuses.filter { it.first == "research focus" }.sumOf { it.second }
            val angefangen = facBonuses.filter { it.first == "started research" }.sumOf { it.second }

            tablePane.add(createCell(0, currentY, rank.toString(), rankColor, true))
            tablePane.add(createCell(1, currentY, faculty.name, COLOR_TEXT_PRIMARY, true))
            tablePane.add(createCell(2, currentY, faculty.researchPoints.toString(), textColor, false))

            // Bonuswerte in die Tabelle eintragen
            tablePane.add(createCell(3, currentY, "+$sichtbarkeit", textColor, false))
            tablePane.add(createCell(4, currentY, "+$breite", textColor, false))
            tablePane.add(createCell(5, currentY, "+$schwerpunkt", textColor, false))
            tablePane.add(createCell(6, currentY, "+$angefangen", textColor, false))

            tablePane.add(createCell(7, currentY, score.toString(), COLOR_TEXT_PRIMARY, true))

            currentY += 60.0
            rank++
        }
    }

    /**
     * Erzeugt ein einzelnes [Label] als Tabellenzelle in der Spalte [colIndex] auf Höhe [y].
     */
    private fun createCell(colIndex: Int, y: Double, text: String, color: Color, isBold: Boolean): Label {
        return Label(
            posX = COLUMN_X_POSITIONS[colIndex], posY = y,
            width = COLUMN_WIDTHS[colIndex], height = 60,
            text = text,
            font = Font(
                size = 22, color = color,
                fontWeight = if (isBold) Font.FontWeight.BOLD else Font.FontWeight.NORMAL
            ),
            alignment = if (colIndex <= 1) Alignment.CENTER_LEFT else Alignment.CENTER
        )
    }

    private companion object {
        val COLOR_SCENE_BG = Color(25, 46, 40)
        val COLOR_CARD_BG = Color(33, 56, 49)
        val COLOR_GOLD = Color(224, 169, 69)
        val COLOR_TEXT_PRIMARY = Color(243, 246, 244)
        val COLOR_TEXT_MUTED = Color(167, 190, 182)
        val COLOR_DARK_TEXT = Color(20, 35, 30)
        val COLOR_WINNER_ROW_BG = Color(52, 72, 58)

        val VISUAL_SCENE_BG = ColorVisual(COLOR_SCENE_BG)

        val COLUMN_X_POSITIONS = listOf(40.0, 120.0, 400.0, 600.0, 800.0, 950.0, 1150.0, 1400.0)
        val COLUMN_WIDTHS = listOf(60.0, 250.0, 200.0, 180.0, 150.0, 200.0, 200.0, 150.0)
    }
}