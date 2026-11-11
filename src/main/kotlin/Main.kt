import gui.SopraApplication

/**
 * Einstiegspunkt, startet die [SopraApplication].
 *
 * Nach dem Schließen der Anwendung wird eine Meldung ausgegeben.
 */
fun main() {
    SopraApplication().show()
    println("Application ended. Goodbye")
}