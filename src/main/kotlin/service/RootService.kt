package service
import entity.FacultyGame

/**
 * The root service class is responsible for managing services and the entity layer reference.
 * This class acts as a central hub for every other service within the application.
 *
 * @property currentGame das aktuell laufende Spiel, null solange kein Spiel gestartet wurde
 * @property gameService Service für Spielstart, Zugwechsel und Spielende
 * @property playerActionService Service für die Aktionen einer Fakultät
 */
class RootService {
    var currentGame: FacultyGame? = null

    val gameService = GameService(this)
    val playerActionService = PlayerActionService(this)

    /**
     * Meldet newRefreshable bei allen Services an
     * @param newRefreshable z. B. eine Szene der GUI
     */
    fun addRefreshable(newRefreshable: Refreshable) {
        gameService.addRefreshable(newRefreshable)
        playerActionService.addRefreshable(newRefreshable)
    }

    /**
     * Meldet mehrere Refreshables bei allen Services an
     * @param newRefreshables z. B. alle Szenen der GUI
     */
    fun addRefreshables(vararg newRefreshables: Refreshable){
        newRefreshables.forEach {addRefreshable(it)}

    }
}