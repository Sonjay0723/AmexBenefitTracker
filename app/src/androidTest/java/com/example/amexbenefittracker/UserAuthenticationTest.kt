package com.example.amexbenefittracker

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import com.example.amexbenefittracker.data.local.AppDatabase
import com.example.amexbenefittracker.data.local.entities.Benefit
import com.example.amexbenefittracker.data.local.entities.BenefitType
import com.example.amexbenefittracker.data.repository.BenefitRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserAuthenticationTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: BenefitRepository
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = BenefitRepository(
            db.cardDao(),
            db.benefitDao(),
            db.usageHistoryDao(),
            testScope
        )
        // Sign out any active user before running tests
        auth.signOut()
    }

    @After
    fun tearDown() {
        db.close()
        auth.signOut()
    }

    @Test
    fun testLoginFlowAndRemoteSync() = runBlocking {
        val email = "jpitta0723@gmail.com"
        val correctPassword = "Will1022"
        val incorrectPassword = "WrongPassword"

        // 1. Try signing in with a fake/incorrect password
        var loginFailedWithFakePassword = false
        try {
            auth.signInWithEmailAndPassword(email, incorrectPassword).await()
        } catch (e: Exception) {
            loginFailedWithFakePassword = true
        }
        assertTrue("Login should fail with an incorrect password", loginFailedWithFakePassword)
        assertNull("FirebaseAuth should not have a current user after failed login", auth.currentUser)

        // 2. Sign in with the correct password
        try {
            auth.signInWithEmailAndPassword(email, correctPassword).await()
        } catch (e: Exception) {
            fail("Login failed with correct credentials: ${e.message}")
        }

        val currentUser = auth.currentUser
        assertNotNull("User should be successfully logged in", currentUser)
        assertEquals("Logged in user email mismatch", email, currentUser?.email)

        val userId = currentUser?.uid
        assertNotNull("No user UID found", userId)
        if (userId == null) return@runBlocking

        // 3. Clear existing remote database data for clean test
        try {
            firestore.collection("users").document(userId).delete().await()
        } catch (e: Exception) {
            // Ignore if document didn't exist or delete failed
        }

        // 4. Setup mock local data in Room DB
        val cardId = db.cardDao().insertCard(
            com.example.amexbenefittracker.data.local.entities.Card(
                name = "Test Platinum Card",
                annualFee = 695.0,
                corporateCredit = 150.0,
                corporateCreditClaimed = false,
                isDefault = true
            )
        )

        val benefit = Benefit(
            cardId = cardId,
            name = "Test Uber Cash",
            description = "Test Description",
            totalValue = 200.0,
            type = BenefitType.MONTHLY,
            monthlyValue = 15.0,
            decemberValue = 35.0,
            displayOrder = 1
        )
        db.benefitDao().insertBenefit(benefit)

        // 5. Toggle usage to trigger local database insert and asynchronous Firestore sync
        val periodIdentifier = "2026-06"
        val timestamp = System.currentTimeMillis()
        repository.toggleUsage(benefit, periodIdentifier, timestamp)

        // Give a short delay to allow background sync to complete
        kotlinx.coroutines.delay(2000)

        // 6. Verify state is stored remotely in Firestore
        val documentSnapshot = firestore.collection("users").document(userId).get().await()
        assertTrue("Firestore user document should exist", documentSnapshot.exists())

        val remoteData = documentSnapshot.data
        assertNotNull("Remote data should not be null", remoteData)

        // Verify claims map matches what was toggled
        @Suppress("UNCHECKED_CAST")
        val claims = remoteData?.get("claims") as? Map<String, Any>
        assertNotNull("Claims map in Firestore should not be null", claims)

        @Suppress("UNCHECKED_CAST")
        val cardClaims = claims?.get("test_platinum_card") as? Map<String, Any>
        assertNotNull("test_platinum_card claims should exist in Firestore", cardClaims)

        @Suppress("UNCHECKED_CAST")
        val yearClaims = cardClaims?.get("2026") as? Map<String, Any>
        assertNotNull("2026 claims should exist in Firestore", yearClaims)

        @Suppress("UNCHECKED_CAST")
        val benefitClaims = yearClaims?.get("test_uber_cash") as? Map<String, Any>
        assertNotNull("test_uber_cash claims should exist in Firestore", benefitClaims)

        @Suppress("UNCHECKED_CAST")
        val periodClaims = benefitClaims?.get("06") as? Map<String, Any>
        assertNotNull("06 claims should exist in Firestore", periodClaims)

        assertEquals("Synced amount mismatch", 15.0, (periodClaims?.get("a") as? Number)?.toDouble())
    }
}
