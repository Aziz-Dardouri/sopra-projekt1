package gui

import entity.Faculty
import service.Refreshable
import service.RootService
import tools.aqua.bgw.core.BoardGameApplication

/**
 * Hauptanwendung für das SoPra-Spiel „Fakultäten“.
 * Erzeugt den [RootService], meldet alle Szenen als [Refreshable]s an und wechselt zwischen den Szenen.
 */
class SopraApplication : BoardGameApplication("Fakultäten – Exzellente Informatik-Forschung"), Refreshable {

    /**
     * Der [RootService], über ihn werden Service-Methoden aufgerufen und der Spielstand gelesen.
     */
    val rootService: RootService = RootService()

    /**
     * Startmenü, wird beim Programmstart angezeigt.
     */
    private val startMenuScene: StartMenuScene = StartMenuScene(rootService) { exit() }

    /**
     * Spielszene. „Zurück zum Menü“ bricht das Spiel ab, siehe [refreshAfterQuitGame].
     */
    private val gameScene: GameScene = GameScene(rootService)

    /**
     * Hotseat-Übergabe zwischen zwei Zügen (Issue #23). Deckt die Spielszene vollständig ab,
     * bis die nächste Fakultät auf „Zug starten“ klickt.
     */
    private val hotseatScene: HotseatScene = HotseatScene(rootService) { this.hideMenuScene() }

    /**
     * Ergebnis-Szene am Spielende (Issue #24). Ermöglicht den Start einer neuen Partie
     * über die [StartMenuScene] oder das Beenden der Anwendung.
     */
    private val endgameMenuScene: EndgameMenuScene = EndgameMenuScene().apply {
        newGameButton.onMouseClicked = {
            this@SopraApplication.hideMenuScene()
            this@SopraApplication.showMenuScene(startMenuScene)
        }
        exitButton.onMouseClicked = {
            this@SopraApplication.exit()
        }
    }

    /**
     * Meldet die Refreshables an und zeigt die [StartMenuScene].
     */
    init {
        rootService.addRefreshables(
            this,
            startMenuScene,
            gameScene,
            hotseatScene,
            endgameMenuScene
        )
        this.showGameScene(gameScene)
        this.showMenuScene(startMenuScene)
    }

    /**
     * Zeigt zu Beginn jedes Zuges die [HotseatScene], auch beim ersten Zug
     * ([service.GameService.startNewGame] ruft [service.GameService.startTurn] direkt auf).
     *
     * Die Hotseat-Szene ersetzt ein offenes Menü (z. B. die [StartMenuScene]), ein extra
     * [hideMenuScene] nach [refreshAfterStartNewGame] ist also nicht nötig. Handkarten und Aktionen
     * der Spielszene bleiben verdeckt bzw. gesperrt, bis die nächste Fakultät auf „Zug starten“ klickt.
     */
    override fun refreshAfterStartTurn() {
        this.showMenuScene(hotseatScene)
    }

    /**
     * Zeigt am Ende des Spiels die [EndgameMenuScene] mit der Abschlusstabelle an.
     */
    override fun refreshAfterGameEnd(winners: List<Faculty>, scores: Map<Faculty, Int>) {
        this.showMenuScene(endgameMenuScene)
    }

    /**
     * Nach „Zurück zum Menü“ ist das Spiel weg, es bleibt nur das Startmenü für ein neues Spiel.
     */
    override fun refreshAfterQuitGame() {
        this.showMenuScene(startMenuScene)
    }
}
