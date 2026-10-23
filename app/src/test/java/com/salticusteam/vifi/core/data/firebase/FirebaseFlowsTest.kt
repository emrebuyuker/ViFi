package com.salticusteam.vifi.core.data.firebase

import app.cash.turbine.test
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseException
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseFlowsTest {

    // --- valueEvents ---

    @Test
    fun `valueEvents when data changes emits each snapshot`() = runTest {
        val listener = slot<ValueEventListener>()
        val query = mockk<Query>(relaxed = true)
        every { query.addValueEventListener(capture(listener)) } answers { listener.captured }
        val first = mockk<DataSnapshot>()
        val second = mockk<DataSnapshot>()

        query.valueEvents().test {
            listener.captured.onDataChange(first)
            assertSame(first, awaitItem())
            listener.captured.onDataChange(second)
            assertSame(second, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `valueEvents when collection cancelled removes listener`() = runTest {
        val listener = slot<ValueEventListener>()
        val query = mockk<Query>(relaxed = true)
        every { query.addValueEventListener(capture(listener)) } answers { listener.captured }

        query.valueEvents().test {
            cancelAndIgnoreRemainingEvents()
        }

        verify(exactly = 1) { query.removeEventListener(listener.captured) }
    }

    @Test
    fun `valueEvents when cancelled by server fails with database exception and removes listener`() = runTest {
        val listener = slot<ValueEventListener>()
        val query = mockk<Query>(relaxed = true)
        every { query.addValueEventListener(capture(listener)) } answers { listener.captured }
        val error = DatabaseError.fromCode(DatabaseError.PERMISSION_DENIED)

        query.valueEvents().test {
            listener.captured.onCancelled(error)
            assertTrue(awaitError() is DatabaseException)
        }

        verify(exactly = 1) { query.removeEventListener(listener.captured) }
    }

    // --- authStateChanges ---

    @Test
    fun `authStateChanges emits current user then null after sign out`() = runTest {
        val listener = slot<FirebaseAuth.AuthStateListener>()
        val auth = mockk<FirebaseAuth>(relaxed = true)
        every { auth.addAuthStateListener(capture(listener)) } answers { }
        val user = mockk<FirebaseUser>()

        auth.authStateChanges().test {
            every { auth.currentUser } returns user
            listener.captured.onAuthStateChanged(auth)
            assertSame(user, awaitItem())

            every { auth.currentUser } returns null
            listener.captured.onAuthStateChanged(auth)
            assertNull(awaitItem())

            cancelAndIgnoreRemainingEvents()
        }

        verify(exactly = 1) { auth.removeAuthStateListener(listener.captured) }
        assertTrue(listener.isCaptured)
    }
}
