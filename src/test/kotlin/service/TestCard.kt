package service

import entity.Faculty
import entity.FacultyGame
import entity.ResearchArea
import entity.ResearchCard
import entity.ResearchTopic

/**
 * Kurzschreibweise für Testkarten, z. B. `card(ResearchTopic.SEVEN, ResearchArea.AI)`.
 *
 * @param topic Kartenwert
 * @param area Teilgebiet
 * @return neue Karte (jede Instanz ist eine eigene Karte, `ResearchCard` vergleicht per Identität)
 */
fun card(topic: ResearchTopic, area: ResearchArea): ResearchCard = ResearchCard(topic, area)

/**
 * Erzeugt [count] verschiedene Karten des Teilgebiets [area], beginnend mit ACE.
 *
 * @param area Teilgebiet aller Karten
 * @param count Anzahl, höchstens 13
 * @return Liste neuer Karten
 */
fun cardsOf(area: ResearchArea, count: Int): List<ResearchCard> =
    ResearchTopic.entries.take(count).map { ResearchCard(it, area) }

/**
 * Legt [count] Karten des Teilgebiets [area] auf den Forschungsstapel von [faculty].
 *
 * @param faculty Fakultät, deren Stapel gefüllt wird
 * @param area Teilgebiet des Stapels
 * @param count Anzahl Karten
 */
fun fillStack(faculty: Faculty, area: ResearchArea, count: Int) {
    cardsOf(area, count).forEach { faculty.addResearch(it) }
}


/**
 * Legt [count] Karten des Teilgebiets [area] auf den Publikationsstapel von [faculty].
 *
 * @param faculty Fakultät, deren Publikationen gefüllt werden
 * @param area Teilgebiet der Karten
 * @param count Anzahl Karten
 */
fun publishCards(faculty: Faculty, area: ResearchArea, count: Int){
    faculty.publications.addAll(cardsOf(area,count))
}

/**
 * Anzahl aller Karten im Spiel (Hände, Stapel, Publikationen, Literatur, Nachzieh- und Ablagestapel).
 *
 * @param game das zu zählende Spiel
 * @return Gesamtzahl der Karten, bei einem korrekten Spiel immer 52
 */
fun countAllCards(game: FacultyGame): Int =
    game.drawStack.size + game.discardStack.size + game.centralLiterature.size +
            game.faculties.sumOf {
                faculty -> faculty.handCards.size + faculty.publications.size +
                    faculty.researchActivity.values.sumOf { it.size }
            }

