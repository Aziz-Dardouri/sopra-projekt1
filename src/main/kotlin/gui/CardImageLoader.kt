package gui

import entity.CardSuit
import entity.CardValue
import tools.aqua.bgw.visual.ImageVisual

/**
 * Rasterbild mit allen Karten: Farben als Zeilen (plus eine Zeile für leere Karte/Rückseite)
 * und Werte als Spalten (beginnend mit dem Ass). Die Reihenfolge im Bild ist eine andere als
 * in [CardSuit], deshalb wird über [row] und [column] umgerechnet.
 */
private const val CARDS_FILE = "card_deck.png"

private const val IMG_HEIGHT = 200
private const val IMG_WIDTH = 130

/**
 * Liefert die Kartenbilder aus src/main/resources/card_deck.png, in der alle Karten als Raster liegen.
 * Die [ImageVisual]s von [frontImageFor], [blankImage] und [backImage] sind 130x200 Pixel groß.
 */
class CardImageLoader {

    /**
     * Leere (weiße) Karte
     */
    val blankImage : ImageVisual get() = getImageByCoordinates(0, 4)

    /**
     * Rückseite der Karten
     */
    val backImage: ImageVisual get() = getImageByCoordinates(2, 4)

    /**
     * Kartenbild für die angegebene [CardSuit] und den [CardValue]
     */
    fun frontImageFor(suit: CardSuit, value: CardValue) =
        getImageByCoordinates(value.column, suit.row)

    /**
     * Schneidet aus dem Rasterbild [CARDS_FILE] das Bild in Spalte [x] und Zeile [y] aus.
     *
     * @param x Spalte im Rasterbild, beginnend bei 0
     * @param y Zeile im Rasterbild, beginnend bei 0
     */
    private fun getImageByCoordinates (x: Int, y: Int) = ImageVisual(
        CARDS_FILE,
        IMG_WIDTH,
        IMG_HEIGHT,
        x * IMG_WIDTH,
        y * IMG_HEIGHT,
    )

}

/**
 * In [CARDS_FILE] stehen die Farben in einer anderen Reihenfolge als in [CardSuit],
 * diese Property liefert deshalb die passende Zeile.
 */
private val CardSuit.row get() = when (this) {
    CardSuit.CLUBS -> 0
    CardSuit.DIAMONDS -> 1
    CardSuit.HEARTS -> 2
    CardSuit.SPADES -> 3
}


/**
 * In [CARDS_FILE] stehen die Werte in einer anderen Reihenfolge als in [CardValue],
 * diese Property liefert deshalb die passende Spalte.
 */
private val CardValue.column get() = when (this) {
    CardValue.ACE -> 0
    CardValue.TWO -> 1
    CardValue.THREE -> 2
    CardValue.FOUR -> 3
    CardValue.FIVE -> 4
    CardValue.SIX -> 5
    CardValue.SEVEN -> 6
    CardValue.EIGHT -> 7
    CardValue.NINE -> 8
    CardValue.TEN -> 9
    CardValue.JACK -> 10
    CardValue.QUEEN -> 11
    CardValue.KING -> 12
}
