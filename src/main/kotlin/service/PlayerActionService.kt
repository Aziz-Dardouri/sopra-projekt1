package service

import entity.Faculty
import entity.FacultyGame
import entity.ResearchArea
import entity.ResearchCard

/**
 * Service für die Aktionen, die eine Fakultät/ein Spieler in ihrem Zug ausführen können.
 * Aktionen funktionieren in 3 Schichten: Vorbedingungen prüfen, Spiellogik absolvieren,
 * Refreshables und Log benachrichtigen
 *
 * @param rootService hält das aktuelle Spiel und alle Services
 */
class PlayerActionService(private val rootService: RootService) : AbstractRefreshingService() {

    /**
     * Ermöglicht es einer Fakultät, die Forschung voranzutreiben (kostet 1 AP).
     *
     * Allgemeine Beschreibung:
     * Die gespielte Karte wird aus der Hand auf den passenden Forschungsstapel der Fakultät gelegt.
     * Zusätzlich wird eine Karte vom Nachziehstapel in die zentrale Literatur gelegt. Liegen dort
     * bereits 6 Karten, wird die älteste Karte zuvor entfernt.
     *
     * Vorbedingungen:
     * - Das Spiel muss gestartet sein und sich in einem laufenden Zustand befinden.
     * - Die aktive Fakultät muss mindestens 1 Aktionspunkt (AP) haben.
     * - Die übergebene Karte muss sich auf der Hand der aktiven Fakultät befinden.
     *
     * Nachbedingungen:
     * - Die gespielte Karte wurde von der Hand entfernt und auf den Forschungsstapel gelegt.
     * - Eine Karte wurde vom Nachziehstapel in die zentrale Literatur verschoben
     *   (falls > 6, wurde die älteste entfernt).
     * - Den Aktionspunkten der aktiven Fakultät wurde 1 AP abgezogen.
     * - Die Methode [Refreshable.refreshAfterAdvanceResearch] der Refreshables wurde aufgerufen.
     *
     * Parameter:
     * @param handCard Die [ResearchCard], die gespielt werden soll.
     *
     * Ergebnis:
     * - Diese Methode hat keinen Rückgabewert (Unit).
     *
     * Fehlerfälle:
     * @throws IllegalStateException Wird geworfen, wenn aktuell kein Spiel läuft oder die Fakultät < 1 AP hat.
     * @throws IllegalArgumentException Wird geworfen, wenn die gespielte Karte nicht auf der Hand ist.
     */
    fun advanceResearch(handCard: ResearchCard) {
        val game = checkNotNull(rootService.currentGame) { "Es läuft aktuell kein Spiel!" }
        check(game.endgameTurnsLeft != 0) { "Das Spiel ist bereits vorbei." }

        val currentFaculty = game.faculties[game.facultyAtTurn]

        if (currentFaculty.actionPoints < 1) {
            throw IllegalStateException("Nicht genug AP")
        } else if (!currentFaculty.handCards.contains(handCard)) {
            throw IllegalArgumentException("Die gespielte Karte ist nicht auf der Hand!")
        } else {
            currentFaculty.actionPoints -= 1
            currentFaculty.addResearch(handCard)
            currentFaculty.handCards.remove(handCard)
            if (!game.drawStack.isEmpty()) {
                val newCard = game.drawStack.pop()
                if (game.centralLiterature.size == 6) {
                    val lastCard = game.centralLiterature.removeAt(0)
                    game.discardStack.add(lastCard)
                }
                game.centralLiterature.add(newCard)
                onAllRefreshables { refreshAfterAdvanceResearch(handCard, newCard) }
            } else {
                onAllRefreshables { refreshAfterAdvanceResearch(handCard, null) }
            }
            log("${currentFaculty.name} treibt Forschung in ${areaName(handCard.suit)} voran (${cardName(handCard)})")
        }
    }

    /**
     * Neue Ideen gewinnen (1 AP): Zieht die oberste Karte des Nachziehstapels auf die Hand.
     * Geht ausschließlich, wenn die spielende Fakultät höchstens 5 Karten auf der Hand hat (Handkartenlimit: 6).
     *
     * @throws IllegalStateException wenn kein Spiel läuft, die aktive Fakultät weniger als 1 AP hat,
     * die spielende Fakultät bereits 6 Handkarten hat oder der Nachziehstapel leer ist
     */
    fun gainNewIdeas() {
        // Retrieve the current game instance from rootService and validate that a game is running
        val game = checkNotNull(rootService.currentGame) { "No game is running." }
        check(game.endgameTurnsLeft != 0) { "Das Spiel ist bereits vorbei." }

        val currentFaculty = game.faculties[game.facultyAtTurn] // Get the faculty whose turn it currently is

        check(currentFaculty.actionPoints >= 1) { "Nicht genug AP" } // Check if the active faculty has at least 1 AP
        check(currentFaculty.handCards.size <= 5) { "Handkartenlimit erreicht" } // max 5 cards before drawing
        check(game.drawStack.isNotEmpty()) { "Nachziehstapel ist leer" } // Check if the draw stack is not empty

        currentFaculty.actionPoints -= 1 // Deduct 1 Action Point
        val drawnCard = game.drawStack.pop() // Draw the top card from the draw stack
        currentFaculty.handCards.add(drawnCard) // Add the drawn card to the faculty's hand cards
        onAllRefreshables { refreshAfterGainNewIdeas(drawnCard) } // Notify all refreshables/GUI
        log("${currentFaculty.name} gewinnt eine neue Idee")
    }

    /**
     * Strategie anpassen, Schritt 1 (1 AP): Wirft [discardedCard] ab und deckt die obersten
     * zwei Karten des Nachziehstapels auf. Die Karten bleiben auf dem Nachziehstapel liegen,
     * bis [finishAdaptStrategy] aufgerufen wird. So bleibt der Service zustandslos.
     *
     * @param discardedCard Karte aus der Hand der aktiven Fakultät, die abgeworfen wird
     * @return die beiden aufgedeckten Karten, die oberste zuerst
     * @throws IllegalStateException wenn kein Spiel läuft, die aktive Fakultät weniger als 1 AP hat
     * oder weniger als 2 Karten im Nachziehstapel liegen
     * @throws IllegalArgumentException wenn [discardedCard] nicht auf der Hand liegt
     */
    fun startAdaptStrategy(discardedCard: ResearchCard): List<ResearchCard> {
        val game = checkNotNull(rootService.currentGame) { "Es läuft aktuell kein Spiel!" }
        check(game.endgameTurnsLeft != 0) { "Das Spiel ist bereits vorbei." }

        val currentFaculty = game.faculties[game.facultyAtTurn]

        check(currentFaculty.actionPoints >= 1) { "Nicht genug AP" }
        if (!currentFaculty.handCards.contains(discardedCard)) {
            throw IllegalArgumentException("Diese Karte ist nicht auf der Hand")
        }
        if (game.drawStack.size < 2) {
            throw IllegalStateException("Nicht genug Karten im Nachziehstapel")
        } else {
            currentFaculty.actionPoints -= 1
            currentFaculty.handCards.remove(discardedCard)
            game.discardStack.add(discardedCard)

            val drawnCards = game.drawStack.takeLast(2).reversed()

            onAllRefreshables { refreshAfterStartAdaptStrategy(drawnCards) }
            log("${currentFaculty.name} passt die Strategie an und wirft ${cardName(discardedCard)} ab.")

            return drawnCards
        }

    }

    /**
     * Strategie anpassen, Schritt 2: Nimmt [keptCard] auf die Hand und legt die andere der
     * beiden aufgedeckten Karten auf den Ablagestapel.
     *
     * Muss direkt nach [startAdaptStrategy] aufgerufen werden. Die GUI sperrt dazwischen alle
     * anderen Aktionen, weil der Service sich den offenen Schritt 1 nicht merkt.
     *
     * @param keptCard eine der beiden von [startAdaptStrategy] zurückgegebenen Karten
     * @throws IllegalStateException wenn kein Spiel läuft oder weniger als 2 Karten im Nachziehstapel liegen
     * @throws IllegalArgumentException wenn [keptCard] keine der beiden obersten Karten ist
     */
    fun finishAdaptStrategy(keptCard: ResearchCard) {
        val game = checkNotNull(rootService.currentGame) { "Es läuft aktuell kein Spiel!" }
        check(game.endgameTurnsLeft != 0) { "Das Spiel ist bereits vorbei." }

        val currentFaculty = game.faculties[game.facultyAtTurn]

        if (game.drawStack.size < 2) {
            throw IllegalStateException("Nicht genug Karten im Nachziehstapel")
        }
        val drawnCards = game.drawStack.takeLast(2)
        if (keptCard !in drawnCards) {
            throw IllegalArgumentException("Die Karte gehört nicht zu den beiden gezogenen Karten.")
        }
        game.drawStack.pop()
        game.drawStack.pop()
        val discardedCard = drawnCards.first { it !== keptCard }
        currentFaculty.handCards.add(keptCard)
        game.discardStack.add(discardedCard)

        onAllRefreshables { refreshAfterFinishAdaptStrategy(keptCard, discardedCard) }
        // Die behaltene Karte liegt danach auf der Hand, deshalb nur die abgeworfene nennen
        log("${currentFaculty.name} behält eine der beiden Karten und wirft ${cardName(discardedCard)} ab.")

    }

    /**
     * Publizieren (2 AP): Verschiebt alle Karten des eigenen Stapels [area] auf den Publikationsstapel.
     *
     * Punkte nach Stapelgröße: 2 → 3, 3 → 5, 4 → 7, ab 5 → 9. Hat noch keine Fakultät in [area]
     * publiziert, gibt es 1 Zusatzpunkt.
     *
     * @param area Teilgebiet des Stapels, der publiziert wird
     * @throws IllegalStateException wenn kein Spiel läuft oder die aktive Fakultät weniger als 2 AP hat
     * @throws IllegalArgumentException wenn auf dem Stapel weniger als 2 Karten liegen
     */
    fun publish(area: ResearchArea) {
        val game = requireGame()
        val activeFaculty = requireActiveFaculty()
        checkActionPoints(2, activeFaculty)

        val stack = activeFaculty.researchActivity.getValue(area)
        require(stack.size >= 2) {
            "Stapel $area muss mindestens 2 Karten enthalten (aktuell: ${stack.size})."
        }

        // 1. Basispunkte berechnen
        val basePoints = when (stack.size) {
            2 -> 3
            3 -> 5
            4 -> 7
            else -> 9
        }

        // 2. Prüfen, ob noch keine fakultät in diesem gebiet publiziert hat (+1 Bonus)
        val isFirstInArea = game.faculties.none { faculty ->
            faculty.publications.any { card -> card.suit == area }
        }
        val bonus = if (isFirstInArea) 1 else 0
        val totalPoints = basePoints + bonus

        // 3. Alle karten vom stapel nehmen und in publications legen
        val cardCount = stack.size
        while (!stack.isEmpty()) {
            activeFaculty.publications.add(stack.pop())
        }

        // 4. Punkte gutschreiben und AP um 2 reduzieren
        activeFaculty.researchPoints += totalPoints
        activeFaculty.actionPoints -= 2

        // 5. Loggen und UI aktualisieren
        onAllRefreshables { refreshAfterPublish(area, totalPoints) }
        log("${activeFaculty.name} publiziert $cardCount Karten in ${areaName(area)} → $basePoints + $bonus Punkte")
    }

    /**
     * Konkurrenz ausbremsen (2 AP): Legt die oberste Karte des Stapels [area] von
     * [targetFaculty] auf den Ablagestapel.
     *
     * @param targetFaculty eine andere Fakultät desselben Spiels
     * @param area Teilgebiet des Stapels, von dem die Karte entfernt wird
     * @throws IllegalStateException wenn kein Spiel läuft oder die aktive Fakultät weniger als 2 AP hat
     * @throws IllegalArgumentException wenn [targetFaculty] die aktive Fakultät ist, nicht im Spiel ist
     * oder ihr Stapel [area] leer ist
     */
    fun slowDownCompetition(targetFaculty: Faculty, area: ResearchArea) {
        val game = requireGame()
        val activeFaculty = requireActiveFaculty()

        checkActionPoints(2, activeFaculty)

        require(targetFaculty != activeFaculty) {
            "Ungültiges Ziel: Eine Fakultät kann sich nicht selbst ausbremsen."
        }

        require(targetFaculty in game.faculties) {
            "Ungültiges Ziel: Die gewählte Fakultät (${targetFaculty.name}) nimmt nicht am aktuellen Spiel teil."
        }

        val targetStack = targetFaculty.researchActivity.getValue(area)

        require(targetStack.isNotEmpty()) {
            "Der Forschungsstapel im Gebiet $area von ${targetFaculty.name} ist leer."
        }

        // oberste karte vom gegnerischen stapel nehmen und auf ablagestapel legen
        val removedCard = targetStack.pop()
        game.discardStack.add(removedCard)

        // 6. Aktionspunkte der aktiven Fakultät um 2 AP verringern
        activeFaculty.actionPoints -= 2

        // gui/test-refreshables über das erfolgreiche ausbremsen informieren und loggen
        onAllRefreshables { refreshAfterSlowDownCompetition(targetFaculty, area, removedCard) }
        log("${activeFaculty.name} bremst ${targetFaculty.name} in ${areaName(area)} aus (${cardName(removedCard)})")
    }


    /**
     * Gibt das aktuelle Spiel zurück.
     *
     * @return das laufende Spiel
     * @throws IllegalStateException wenn kein Spiel läuft
     */
    private fun requireGame(): FacultyGame {
        val game = checkNotNull(rootService.currentGame) { "Es läuft im moment kein Spiel" }
        check(game.endgameTurnsLeft != 0) { "Das Spiel ist bereits vorbei." }
        return game
    }

    /**
     * Ermittelt die Fakultät, die gerade am Zug ist.
     *
     * @return die aktive Fakultät
     * @throws IllegalStateException wenn kein Spiel läuft
     */
    private fun requireActiveFaculty(): Faculty {
        val game = requireGame()
        return game.faculties[game.facultyAtTurn]
    }

    /**
     * Prüft, ob die aktive Fakultät über ausreichend AP verfügt.
     *
     * @param required benötigte Aktionspunkte
     * @param activeFaculty die zu prüfende Fakultät
     * @throws IllegalStateException wenn weniger als [required] AP vorhanden sind
     */
    private fun checkActionPoints(required: Int, activeFaculty: Faculty) {
        check(activeFaculty.actionPoints >= required) {
            "Nicht genügend AP: Mindestens $required AP erforderlich (aktuell: ${activeFaculty.actionPoints} AP)."
        }
    }



    /**
     * Literatur nutzen (2 AP): Legt [card] aus der zentralen Literatur auf den passenden
     * eigenen Forschungsstapel. Die übrigen Karten rücken zusammen, es wird nicht nachgelegt.
     *
     * @param card Karte aus der zentralen Literatur
     * @throws IllegalStateException wenn kein Spiel läuft oder die aktive Fakultät weniger als 2 AP hat
     * @throws IllegalArgumentException wenn [card] nicht in der zentralen Literatur liegt
     */
    fun useLiterature(card: ResearchCard) {
        val activeFaculty = requireActiveFaculty()
        checkActionPoints(2, activeFaculty) // prüft ob spieler genug ap hat

        val game = requireGame()
        if (!game.centralLiterature.contains(card)) {
            throw IllegalArgumentException("Die gewählte Karte liegt nicht in der Literatur.")
        }

        // 1. karte aus der zentralen literatur entfernen
        game.centralLiterature.remove(card)

        // 2. dem eigenen forschungsbereich hinzufügen
        activeFaculty.addResearch(card)

        // 3. AP -=2 setzen
        activeFaculty.actionPoints -= 2

        // 4. log und gui aktualisieren
        onAllRefreshables { refreshAfterUseLiterature(usedCard = card) }
        log("${activeFaculty.name} nutzt Literatur: ${cardName(card)} in ${areaName(card.suit)}")
    }
}
