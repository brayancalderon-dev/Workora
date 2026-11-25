package com.workora.app

import com.workora.app.data.remote.hiringroom.RawHiringRoomJob
import com.workora.app.domain.usecase.AnalyzeJobCompatibilityUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyzeJobCompatibilityUseCaseTest {
    private val useCase = AnalyzeJobCompatibilityUseCase()

    private fun job(title: String, description: String, requirements: String) = RawHiringRoomJob(
        id = "test", companyId = "test", companyName = "Test", companyLogoUrl = "", title = title,
        location = "", workMode = "", level = "", description = description,
        requirements = requirements, applyUrl = "", postedDate = "", daysAgo = 0
    )

    @Test
    fun unrelatedPayrollRoleDoesNotMatchSoftwareCvThroughGenericWords() {
        val result = useCase.execute(
            job(
                "Analista de Nómina",
                "Administración de datos de empleados y reportes en Excel.",
                "Manejo de sistemas y Excel. Experiencia en gestión de nómina."
            ),
            "Desarrollador backend con experiencia en Kotlin, APIs y bases de datos."
        )
        assertTrue(result.matchScore < 75)
        assertEquals(false, result.isRecommended)
    }

    @Test
    fun emptyCvCannotReceiveCompatibilityScore() {
        val result = useCase.execute(
            job("Ingeniero de datos", "Construcción de procesos ETL.", "Experiencia con SQL y Python."),
            ""
        )
        assertEquals(0, result.matchScore)
        assertEquals(false, result.isRecommended)
    }
}
