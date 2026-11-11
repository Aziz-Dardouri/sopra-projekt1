package service

import entity.ResearchArea
import entity.ResearchCard
import entity.ResearchTopic

/**
 * Abstract service class that handles multiple [Refreshable]s (usually UI elements, such as
 * specialized [tools.aqua.bgw.core.BoardGameScene] classes/instances) which are notified
 * of changes to refresh via the [onAllRefreshables] method.
 */
abstract class AbstractRefreshingService {
    /** Alle registrierten Refreshables (GUI-Szenen, Test-Refreshables). */
    private val refreshables = mutableListOf<Refreshable>()

    /**
     * Adds a new [Refreshable] to the list of refreshables.
     *
     * @param newRefreshable The [Refreshable] to be added
     */
    fun addRefreshable(newRefreshable: Refreshable) {
        refreshables += newRefreshable
    }

    /**
     * Calls [method] on every registered [Refreshable], e.g. `onAllRefreshables { refreshAfterStartTurn() }`.
     *
     * @param method the refresh method to call on each [Refreshable]
     */
    fun onAllRefreshables(method: Refreshable.() -> Unit) =
        refreshables.forEach { it.method() }

    /**
     * Textueller Log: gibt [message] auf der Konsole aus und schickt sie per
     * [Refreshable.refreshAfterLog] an alle Refreshables (zB. an das Log der GUI).
     *
     * @param message menschenlesbare Beschreibung einer Aktion
     */
    protected fun log(message: String){
        println(message)
        onAllRefreshables { refreshAfterLog(message) }
    }

    /**
     * Kurzname einer Karte für das Log, z. B. „8♠“ oder „D♥“ (wie in der GUI).
     *
     * @param card die zu beschreibende Karte
     * @return Wert und Symbol des Teilgebiets
     */
    protected fun cardName(card: ResearchCard): String {
        val value = when (card.value) {
            ResearchTopic.ACE -> "A"
            ResearchTopic.JACK -> "B"
            ResearchTopic.QUEEN -> "D"
            ResearchTopic.KING -> "K"
            else -> "${card.value.ordinal + 1}"
        }
        val symbol = when (card.suit) {
            ResearchArea.AI -> "♠"
            ResearchArea.IT_SECURITY -> "♥"
            ResearchArea.SE -> "♦"
            ResearchArea.THEORETICAL -> "♣"
        }
        return value + symbol
    }

    /**
     * Deutscher Name eines Teilgebiets für das Log.
     *
     * @param area das Teilgebiet
     * @return z. B. „IT-Sicherheit“
     */
    protected fun areaName(area: ResearchArea): String = when (area) {
        ResearchArea.AI -> "KI"
        ResearchArea.IT_SECURITY -> "IT-Sicherheit"
        ResearchArea.SE -> "Software Engineering"
        ResearchArea.THEORETICAL -> "Theoretische Informatik"
    }
}