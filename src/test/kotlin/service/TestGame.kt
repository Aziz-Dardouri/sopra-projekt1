package service

import entity.Faculty
import entity.FacultyGame

/**
 * Startet ein Testspiel ohne Mischen und ohne Karten: die Fakultäten in der angegebenen Reihenfolge,
 * die erste ist am Zug und hat 2 AP. Karten legt jeder Test selbst passend auf die Stapel.
 *
 * @param facultyNames Name der Fakultäten in Reihenfolge des Zuges
 * @return das neue Spiel, das auch als [RootService.currentGame] gesetzt ist
 */
fun RootService.createTestGame(vararg facultyNames: String = arrayOf("Dortmund", "Bochum")): FacultyGame {
    val game = FacultyGame(facultyNames.map { Faculty(it) })
    game.faculties[0].resetActionPoints()
    currentGame = game
    return game
}
