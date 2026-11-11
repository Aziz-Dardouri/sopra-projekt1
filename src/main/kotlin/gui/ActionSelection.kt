package gui

import entity.Faculty
import entity.ResearchArea
import entity.ResearchCard

/**
 * Karten und Stapel, die in der [GameScene] angeklickt wurden und beim Submit an die Services gehen.
 * Hält nur Referenzen für die GUI, der Spielstand selbst bleibt in der Entity-Schicht.
 *
 * @property handCard gewählte Handkarte der aktiven Fakultät
 * @property literatureCard gewählte Karte aus der zentralen Literatur
 * @property keptCard Karte, die bei „Strategie anpassen“ von den zwei aufgedeckten behalten wird
 * @property ownArea gewählter eigener Forschungsstapel
 * @property targetFaculty Fakultät, der der gewählte gegnerische Stapel gehört
 * @property targetArea Gebiet des gewählten gegnerischen Stapels
 */
class ActionSelection {
    var handCard: ResearchCard? = null
    var literatureCard: ResearchCard? = null
    var keptCard: ResearchCard? = null
    var ownArea: ResearchArea? = null
    var targetFaculty: Faculty? = null
    var targetArea: ResearchArea? = null

    /**
     * Löscht die ganze Auswahl, z. B. nachdem eine Aktion ausgeführt wurde.
     */
    fun clear() {
        handCard = null
        literatureCard = null
        keptCard = null
        ownArea = null
        targetFaculty = null
        targetArea = null
    }
}
