package com.example.amexbenefittracker

import com.example.amexbenefittracker.data.local.entities.BenefitType
import com.example.amexbenefittracker.worker.BenefitNotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class UnusedCreditNotificationTest {

    @Test
    fun testIsLastMonthOfPeriod() {
        // Monthly should always be true for any month (0-11)
        for (month in 0..11) {
            assertTrue(BenefitNotificationHelper.isLastMonthOfPeriod(BenefitType.MONTHLY, month))
        }

        // Quarterly: March (2), June (5), September (8), December (11) should be true
        assertTrue(BenefitNotificationHelper.isLastMonthOfPeriod(BenefitType.QUARTERLY, 2))
        assertTrue(BenefitNotificationHelper.isLastMonthOfPeriod(BenefitType.QUARTERLY, 5))
        assertTrue(BenefitNotificationHelper.isLastMonthOfPeriod(BenefitType.QUARTERLY, 8))
        assertTrue(BenefitNotificationHelper.isLastMonthOfPeriod(BenefitType.QUARTERLY, 11))

        // Quarterly: other months should be false
        assertFalse(BenefitNotificationHelper.isLastMonthOfPeriod(BenefitType.QUARTERLY, 0)) // Jan
        assertFalse(BenefitNotificationHelper.isLastMonthOfPeriod(BenefitType.QUARTERLY, 1)) // Feb
        assertFalse(BenefitNotificationHelper.isLastMonthOfPeriod(BenefitType.QUARTERLY, 3)) // Apr
        assertFalse(BenefitNotificationHelper.isLastMonthOfPeriod(BenefitType.QUARTERLY, 4)) // May

        // Semi-annual: June (5) and December (11) should be true
        assertTrue(BenefitNotificationHelper.isLastMonthOfPeriod(BenefitType.SEMI_ANNUAL, 5))
        assertTrue(BenefitNotificationHelper.isLastMonthOfPeriod(BenefitType.SEMI_ANNUAL, 11))

        // Semi-annual: other months should be false
        assertFalse(BenefitNotificationHelper.isLastMonthOfPeriod(BenefitType.SEMI_ANNUAL, 0)) // Jan
        assertFalse(BenefitNotificationHelper.isLastMonthOfPeriod(BenefitType.SEMI_ANNUAL, 4)) // May
        assertFalse(BenefitNotificationHelper.isLastMonthOfPeriod(BenefitType.SEMI_ANNUAL, 6)) // Jul
        assertFalse(BenefitNotificationHelper.isLastMonthOfPeriod(BenefitType.SEMI_ANNUAL, 10)) // Nov

        // Annual: December (11) should be true, others false
        assertTrue(BenefitNotificationHelper.isLastMonthOfPeriod(BenefitType.ANNUAL, 11))
        for (month in 0..10) {
            assertFalse(BenefitNotificationHelper.isLastMonthOfPeriod(BenefitType.ANNUAL, month))
        }
    }

    @Test
    fun testGetPeriodIdentifier() {
        val year = "2026"
        
        // Monthly
        assertEquals("2026-01", BenefitNotificationHelper.getPeriodIdentifier(BenefitType.MONTHLY, year, 0))
        assertEquals("2026-06", BenefitNotificationHelper.getPeriodIdentifier(BenefitType.MONTHLY, year, 5))
        assertEquals("2026-12", BenefitNotificationHelper.getPeriodIdentifier(BenefitType.MONTHLY, year, 11))

        // Quarterly
        assertEquals("2026-Q1", BenefitNotificationHelper.getPeriodIdentifier(BenefitType.QUARTERLY, year, 0)) // Jan
        assertEquals("2026-Q1", BenefitNotificationHelper.getPeriodIdentifier(BenefitType.QUARTERLY, year, 2)) // Mar
        assertEquals("2026-Q2", BenefitNotificationHelper.getPeriodIdentifier(BenefitType.QUARTERLY, year, 5)) // Jun
        assertEquals("2026-Q3", BenefitNotificationHelper.getPeriodIdentifier(BenefitType.QUARTERLY, year, 6)) // Jul
        assertEquals("2026-Q4", BenefitNotificationHelper.getPeriodIdentifier(BenefitType.QUARTERLY, year, 11)) // Dec

        // Semi-annual
        assertEquals("2026-H1", BenefitNotificationHelper.getPeriodIdentifier(BenefitType.SEMI_ANNUAL, year, 0)) // Jan
        assertEquals("2026-H1", BenefitNotificationHelper.getPeriodIdentifier(BenefitType.SEMI_ANNUAL, year, 5)) // Jun
        assertEquals("2026-H2", BenefitNotificationHelper.getPeriodIdentifier(BenefitType.SEMI_ANNUAL, year, 6)) // Jul
        assertEquals("2026-H2", BenefitNotificationHelper.getPeriodIdentifier(BenefitType.SEMI_ANNUAL, year, 11)) // Dec

        // Annual
        assertEquals("2026-Annual", BenefitNotificationHelper.getPeriodIdentifier(BenefitType.ANNUAL, year, 0))
        assertEquals("2026-Annual", BenefitNotificationHelper.getPeriodIdentifier(BenefitType.ANNUAL, year, 11))
    }

    @Test
    fun testGetNotificationMessage() {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"), Locale.US)
        
        // Monthly
        calendar.set(Calendar.MONTH, Calendar.JULY) // Month name: July
        val monthlyMessage = BenefitNotificationHelper.getNotificationMessage("Uber Cash", BenefitType.MONTHLY, calendar)
        assertEquals("Uber Cash is still unused for July", monthlyMessage)

        calendar.set(Calendar.MONTH, Calendar.JANUARY) // Month name: January
        val monthlyMessageJan = BenefitNotificationHelper.getNotificationMessage("Uber Cash", BenefitType.MONTHLY, calendar)
        assertEquals("Uber Cash is still unused for January", monthlyMessageJan)

        // Quarterly
        calendar.set(Calendar.MONTH, Calendar.SEPTEMBER) // Q3
        val quarterlyMessage = BenefitNotificationHelper.getNotificationMessage("Saks Credit", BenefitType.QUARTERLY, calendar)
        assertEquals("Saks Credit is still unused for Q3", quarterlyMessage)

        // Semi-annual
        calendar.set(Calendar.MONTH, Calendar.JUNE) // H1
        val semiAnnualMessage = BenefitNotificationHelper.getNotificationMessage("Dell Credit", BenefitType.SEMI_ANNUAL, calendar)
        assertEquals("Dell Credit is still unused for this half of the year", semiAnnualMessage)

        // Annual
        calendar.set(Calendar.MONTH, Calendar.DECEMBER)
        val annualMessage = BenefitNotificationHelper.getNotificationMessage("Airline Fee Credit", BenefitType.ANNUAL, calendar)
        assertEquals("Airline Fee Credit is still unused for this year", annualMessage)
    }
}
