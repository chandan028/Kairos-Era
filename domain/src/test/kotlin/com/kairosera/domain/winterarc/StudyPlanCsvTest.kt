package com.kairosera.domain.winterarc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class StudyPlanCsvTest {
    private val sheet = """
Date,Day,Wk,Phase,Hrs,Track,Topic,Detailed Sub-Topics (cover every one),Resource (open the link),Practice Problems / Exercises,Spaced Revision + Checkpoints,Done?,Time Spent,Confidence 1-5,Notes
Sun 04 Oct 2026,Sun,3,P1 Foundations,3,DSA - Stacks,Monotonic Stack,"next greater, smaller",NeetCode,LC 739 Daily Temperatures; LC 496,-,,,,
Mon 05 Oct 2026,Mon,4,P2 Core DSA,1,Java Core,"OOP III: Interfaces, Records","default methods; ""sealed"" classes",Baeldung,Rewrite your BankAccount example,-,,,,
Tue 06 Oct 2026,Tue,4,P2 Core DSA,1,DSA - Linked List,Linked List Fundamentals,reversal,NeetCode,LC 206 Reverse Linked List; LC 92,COLD RE-SOLVE (3-day): LC 739 | more,,,,
Sat 21 Nov 2026,Sat,10,P4 Design+AI,3,AI/LLM,Transformers,attention,Jay Alammar,-,-,,,,
""".trimIndent()

    @Test fun sheetLayoutStartsFromTheCutoff() {
        val r = StudyPlanCsv.parse(sheet, LocalDate.of(2026, 10, 5))
        assertEquals(1, r.skippedBefore)
        assertTrue(r.tasks.none { it.date.isBefore(LocalDate.of(2026, 10, 5)) })
        val oct5 = r.tasks.filter { it.date == LocalDate.of(2026, 10, 5) }
        assertEquals(listOf("OOP III: Interfaces, Records", "Practice: Rewrite your BankAccount example"), oct5.map { it.title })
        assertEquals(StudyCategory.JAVA, oct5.first().category)
        assertEquals(60, oct5.sumOf { it.durationMinutes })
        assertTrue(oct5.first().notes.contains("\"sealed\""))
        val oct6 = r.tasks.filter { it.date == LocalDate.of(2026, 10, 6) }
        assertEquals(3, oct6.size)
        assertEquals("Practice: 2 problems", oct6[1].title)
        assertTrue(oct6[2].title.startsWith("Revision: COLD RE-SOLVE"))
        assertEquals(StudyCategory.DSA, oct6.first().category)
        assertEquals(StudyCategory.AI_LLM, r.tasks.last().category)
        assertEquals(1, r.tasks.count { it.date == LocalDate.of(2026, 11, 21) })
    }

    @Test fun simpleLayout() {
        val csv = "Date,Topic,Category,Duration,Notes,Start\n2026-10-05,Arrays — Advanced Problems,DSA,60 min,,08:00\n2026-10-05,Sliding Window,dsa,1h,\"a, b\",9:30\n2026-10-04,Old,Java,30,,\n"
        val r = StudyPlanCsv.parse(csv, LocalDate.of(2026, 10, 5))
        assertEquals(2, r.tasks.size)
        assertEquals(60, r.tasks[1].durationMinutes)
        assertEquals(LocalTime.of(8, 0), r.tasks[0].startTime)
        assertEquals("a, b", r.tasks[1].notes)
        assertEquals(1, r.tasks[1].position)
        assertEquals(1, r.skippedBefore)
    }

    @Test fun categories() {
        assertEquals(StudyCategory.SYSTEM_DESIGN, StudyPlanCsv.categoryFor("HLD"))
        assertEquals(StudyCategory.JAVA, StudyPlanCsv.categoryFor("Java Collections"))
        assertEquals(StudyCategory.INTERVIEW, StudyPlanCsv.categoryFor("Behavioural"))
        assertEquals(StudyCategory.DSA, StudyPlanCsv.categoryFor("Mock + DSA"))
    }
}
