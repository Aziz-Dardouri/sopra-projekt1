package entity

import java.util.Stack

/**
 * Aktueller Spielzustand von FacultyGame
 *
 * @property faculties die 2 bis 4 Fakultäten in der Reihenfolge des Zuges
 * @property facultyAtTurn Index der Fakultät in [faculties], die am Zug ist
 * @property endgameTurnsLeft verbleibende Züge nach leerem Nachziehstapel, -1 solange das Endspiel nicht begonnen hat
 * @property drawStack Nachziehstapel, oberste Karte = zuletzt hinzugefügte
 * @property discardStack Ablagestapel
 * @property centralLiterature zentrale Literatur, Index 0 = älteste Karte, höchstens 6 Karten
 *
 */
class FacultyGame (val faculties: List<Faculty>){
    var facultyAtTurn: Int = 0
    var endgameTurnsLeft: Int = -1

//val hier, da Objektstruktur nicht veränderlich während Ausführung
//Wahl hier speziell als Stack (Indexwissen nicht nötig)
    val drawStack: Stack<ResearchCard> = Stack()
    val discardStack: Stack<ResearchCard> = Stack()


    val centralLiterature: MutableList<ResearchCard> = mutableListOf()

    /**
     * Gibt den Spielzug an die nächste Fakultät weiter.
     * Nach der letzten Fakultät ist wieder die erste am Zug (zyklisch).
     */
    fun nextFaculty() {
        facultyAtTurn = (facultyAtTurn +1 ) % faculties.size
    }
}