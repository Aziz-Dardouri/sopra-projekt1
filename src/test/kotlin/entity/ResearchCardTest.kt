package entity

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests für ResearchCard Klasse
 */
class ResearchCardTest {
    /**
     * Prüft das Setzen des Kartenwerts und der Kartensymbole (SE, Theoretical, IT_SEC, AI)
     */
    @Test
    fun testCardValueAndSuit() {
        val card = ResearchCard(ResearchTopic.KING, ResearchArea.AI)

        assertEquals(ResearchTopic.KING, card.value)
        assertEquals(ResearchArea.AI, card.suit)
    }

    /**
     * Testet, ob es 52 verschiedene Karten gibt.
     */
    @Test
    fun testSize() {
        assertEquals(4, ResearchArea.entries.size)
        assertEquals(13, ResearchTopic.entries.size)

        val cardDeck = mutableListOf<ResearchCard>()
        for(area in ResearchArea.entries) {
            for (itTopic in ResearchTopic.entries) {
                cardDeck.add(ResearchCard(itTopic, area))
            }
        }
        assertEquals(52, cardDeck.size)

    }
}