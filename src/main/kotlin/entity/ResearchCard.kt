package entity

/**
 * Einzelne Spielkarte des FacultyGames
 *
 * @property value Kartenwert (ACE bis KING)
 * @property suit Teilgebiet der Informatik, entspricht der Kartenfarbe
 */
class ResearchCard(val value: ResearchTopic, val suit: ResearchArea)
