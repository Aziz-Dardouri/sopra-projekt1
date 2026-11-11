package entity

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Testet die Faculty
 */
class FacultyTest {
    private lateinit var faculty: Faculty

    /**
     * Anlegen einer Dummy Faculty.
     */
    @BeforeTest
    fun init(){
        faculty = Faculty("Informatik Fakultaet - TU Dortmund")
    }

    /**
     * Testet den initialen Zustand einer Fakultät.
     */
    @Test
    fun testInit() {
        assertEquals("Informatik Fakultaet - TU Dortmund", faculty.name)
        assertEquals(0, faculty.actionPoints)
        assertEquals(0, faculty.researchPoints)
        assertEquals(ResearchArea.entries.toSet(),faculty.researchActivity.keys)
        assertTrue(faculty.handCards.isEmpty())
        assertTrue(faculty.publications.isEmpty())
        assertTrue(faculty.researchActivity.values.all { it.isEmpty() })
    }

    /**
     * Testet, ob resetActionPoints die actionPoints auf 2 setzt.
     */
    @Test
    fun testResetActionPoints() {
        faculty.resetActionPoints()
        assertEquals(2, faculty.actionPoints)
        faculty.actionPoints = 0
        faculty.resetActionPoints()
        assertEquals(2, faculty.actionPoints)
    }

    /**
     * Testet, ob addResearch Karten auf den passenden Stack packt.
     */
    @Test
    fun testAddResearch() {
        val firstCard = ResearchCard(ResearchTopic.KING, ResearchArea.AI)
        val lastCard = ResearchCard(ResearchTopic.QUEEN, ResearchArea.AI)

        faculty.addResearch(firstCard)
        faculty.addResearch(lastCard)

        val aiResearchStack = faculty.researchActivity.getValue(ResearchArea.AI)
        assertEquals(2, aiResearchStack.size)
        assertEquals(listOf(firstCard, lastCard), aiResearchStack.toList())
        assertEquals(lastCard,aiResearchStack.peek())

        assertTrue(faculty.researchActivity.getValue(ResearchArea.SE).isEmpty())
        assertTrue(faculty.researchActivity.getValue(ResearchArea.IT_SECURITY).isEmpty())
        assertTrue(faculty.researchActivity.getValue(ResearchArea.THEORETICAL).isEmpty())
    }
    /**
     * Prüft addResearch für alle ResearchAreas
     */
    @Test
    fun testAddResearchAll (){
        for (area in ResearchArea.entries) {
            faculty.addResearch(ResearchCard(ResearchTopic.KING, area))
        }
        for (area in ResearchArea.entries) {
            val researchStack = faculty.researchActivity.getValue(area)
            assertEquals(1 , researchStack.size)
            assertEquals(area, researchStack.peek().suit)
        }
    }

}