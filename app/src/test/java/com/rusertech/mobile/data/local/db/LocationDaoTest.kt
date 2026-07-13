package com.rusertech.mobile.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocationDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: LocationDao

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        dao = db.locationDao()
    }
    @After fun teardown() { db.close() }

    @Test fun insertAndRetrieveUnsynced() = runTest {
        dao.insert(LocationEntity(latitude = -34.603, longitude = -58.381, accuracy = 10f,
            speed = 5f, heading = 180f, altitude = 25.0, battery = 80, timestamp = System.currentTimeMillis()))
        val unsynced = dao.getUnsynced()
        assertEquals(1, unsynced.size)
    }

    @Test fun markSyncedExcludesFromUnsynced() = runTest {
        val id = dao.insert(LocationEntity(latitude = 0.0, longitude = 0.0, accuracy = 10f,
            speed = 0f, heading = 0f, altitude = 0.0, battery = 100, timestamp = System.currentTimeMillis()))
        dao.markSynced(listOf(id))
        assertTrue(dao.getUnsynced().isEmpty())
    }

    @Test fun unsyncedCountFlow() = runTest {
        dao.insert(LocationEntity(latitude = 0.0, longitude = 0.0, accuracy = 10f,
            speed = 0f, heading = 0f, altitude = 0.0, battery = 100, timestamp = System.currentTimeMillis()))
        assertEquals(1, dao.getUnsyncedCount().first())
    }
}
