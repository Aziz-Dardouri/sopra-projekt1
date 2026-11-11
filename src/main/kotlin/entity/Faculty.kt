package entity

import java.util.Stack

/**
 * Repräsentiert einen Spieler (also eine Fakultät) des Spiels.
 *
 * @property name Name der Fakultät, eindeutig im Spiel
 * @property actionPoints verbleibende Aktionspunkte im aktuellen Zug
 * @property researchPoints durch Publikationen gesammelte Punkte (noch ohne End-Bonus)
 * @property researchActivity die vier offenen Forschungsstapel, je einer pro [ResearchArea]
 * @property handCards Handkarten, höchstens 6
 * @property publications offener Publikationsstapel
 */
class Faculty(val name: String) {
    var actionPoints: Int = 0
    var researchPoints: Int = 0

    val researchActivity: Map<ResearchArea, Stack<ResearchCard>> =
        ResearchArea.entries.associateWith { Stack<ResearchCard>()}

    val handCards: MutableList<ResearchCard> = mutableListOf()
    val publications: MutableList<ResearchCard> = mutableListOf()

    /**
     * Setzt actionPoints zu Beginn eines Zugs auf 2
     */
    fun resetActionPoints(){
        actionPoints = 2
    }

    /**
     * Legt [researchCard] oben auf den Forschungsstapel ihres Teilgebiets ([ResearchCard.suit]).
     * @param researchCard die jeweilige Karte
     */
    fun addResearch(researchCard: ResearchCard) {
        researchActivity.getValue(researchCard.suit).push(researchCard)

    }


}
