package com.yemen.watersurvey.data.repository

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import org.robolectric.RuntimeEnvironment
import com.yemen.watersurvey.data.dao.UserDao
import com.yemen.watersurvey.data.entity.UserEntity
import com.yemen.watersurvey.domain.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@Database(entities = [UserEntity::class], version = 1, exportSchema = false)
abstract class TestUserDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class UserRepositoryTest {

    private lateinit var database: TestUserDatabase
    private lateinit var userDao: UserDao
    private lateinit var repository: UserRepositoryImpl

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, TestUserDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        userDao = database.userDao()
        repository = UserRepositoryImpl(userDao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testProvisionUserAndAuthenticateSuccess() = runTest {
        val user = User(
            userId = UUID.randomUUID().toString(),
            username = "ahmed_field",
            fullNameAr = "أحمد محمد الميداني",
            fullNameEn = "Ahmed Mohammed",
            role = UserRole.ENUMERATOR,
            governorateCode = "YE22",
            districtCode = "YE2215"
        )

        val provisionResult = repository.provisionUser(user, "1234")
        assertTrue("Provision should succeed", provisionResult.isSuccess)

        val savedUser = repository.getUserByUsername("ahmed_field")
        assertNotNull("User should be found in DB", savedUser)
        assertEquals(user.fullNameAr, savedUser?.fullNameAr)
        assertEquals(UserRole.ENUMERATOR, savedUser?.role)

        // Authenticate with valid PIN
        val authResult = repository.authenticate(
            username = "ahmed_field",
            pin = "1234",
            currentDeviceId = "DEV-TEST-001"
        )

        assertTrue("Auth should be Success", authResult is AuthenticationResult.Success)
        val session = (authResult as AuthenticationResult.Success).session
        assertEquals("ahmed_field", session.user.username)
        assertEquals("DEV-TEST-001", session.deviceId)
        assertFalse(session.isExpired())

        // Active session should be observed
        val currentSession = repository.getCurrentSession()
        assertNotNull(currentSession)
        assertEquals(session.sessionId, currentSession?.sessionId)
    }

    @Test
    fun testAuthenticateInvalidPinFails() = runTest {
        val user = User(
            userId = UUID.randomUUID().toString(),
            username = "supervisor_saadah",
            fullNameAr = "مشرف صعدة",
            role = UserRole.DISTRICT_SUPERVISOR,
            governorateCode = "YE22",
            districtCode = "YE2215"
        )

        repository.provisionUser(user, "9999")

        val authResult = repository.authenticate(
            username = "supervisor_saadah",
            pin = "0000",
            currentDeviceId = "DEV-TEST-002"
        )

        assertTrue("Auth should be InvalidCredentials", authResult is AuthenticationResult.InvalidCredentials)
        assertNull(repository.getCurrentSession())
    }

    @Test
    fun testAuthenticateDeactivatedAccountFails() = runTest {
        val user = User(
            userId = UUID.randomUUID().toString(),
            username = "disabled_user",
            fullNameAr = "مستخدم معطل",
            role = UserRole.ENUMERATOR,
            isActive = false
        )

        repository.provisionUser(user, "4321")
        repository.setUserActiveStatus(user.userId, false)

        val authResult = repository.authenticate(
            username = "disabled_user",
            pin = "4321",
            currentDeviceId = "DEV-TEST-003"
        )

        assertTrue("Auth should be AccountDisabled", authResult is AuthenticationResult.AccountDisabled)
    }

    @Test
    fun testDeviceBindingMismatch() = runTest {
        val user = User(
            userId = UUID.randomUUID().toString(),
            username = "bound_user",
            fullNameAr = "مستخدم مقترن",
            role = UserRole.ENUMERATOR,
            assignedDeviceId = "DEV-BOUND-ORIGINAL"
        )

        repository.provisionUser(user, "5555")

        val authResult = repository.authenticate(
            username = "bound_user",
            pin = "5555",
            currentDeviceId = "DEV-DIFFERENT-DEVICE"
        )

        assertTrue("Auth should be DeviceMismatch", authResult is AuthenticationResult.DeviceMismatch)
        val mismatch = authResult as AuthenticationResult.DeviceMismatch
        assertEquals("DEV-BOUND-ORIGINAL", mismatch.expectedDeviceId)
        assertEquals("DEV-DIFFERENT-DEVICE", mismatch.actualDeviceId)
    }

    @Test
    fun testChangePinAndReAuthenticate() = runTest {
        val user = User(
            userId = UUID.randomUUID().toString(),
            username = "change_pin_user",
            fullNameAr = "مستخدم تغيير الرمز",
            role = UserRole.GOVERNORATE_SUPERVISOR
        )

        repository.provisionUser(user, "1111")

        // Change PIN to 2222
        val changeResult = repository.changePin(user.userId, "2222")
        assertTrue(changeResult.isSuccess)

        // Old PIN should now fail
        val oldAuth = repository.authenticate("change_pin_user", "1111", "DEV-01")
        assertTrue(oldAuth is AuthenticationResult.InvalidCredentials)

        // New PIN should succeed
        val newAuth = repository.authenticate("change_pin_user", "2222", "DEV-01")
        assertTrue(newAuth is AuthenticationResult.Success)
    }

    @Test
    fun testLogoutClearsSession() = runTest {
        val user = User(
            userId = UUID.randomUUID().toString(),
            username = "logout_user",
            fullNameAr = "مستخدم خروج",
            role = UserRole.CENTRAL_SUPERVISOR
        )

        repository.provisionUser(user, "7777")
        val auth = repository.authenticate("logout_user", "7777", "DEV-01")
        assertTrue(auth is AuthenticationResult.Success)
        assertNotNull(repository.getCurrentSession())

        repository.logout()
        assertNull(repository.getCurrentSession())
    }

    @Test
    fun testUserRoleAuthorityAndHierarchies() {
        assertTrue(UserRole.CENTRAL_SUPERVISOR.hasAuthorityOver(UserRole.GOVERNORATE_SUPERVISOR))
        assertTrue(UserRole.GOVERNORATE_SUPERVISOR.hasAuthorityOver(UserRole.DISTRICT_SUPERVISOR))
        assertTrue(UserRole.DISTRICT_SUPERVISOR.hasAuthorityOver(UserRole.ENUMERATOR))
        assertFalse(UserRole.ENUMERATOR.hasAuthorityOver(UserRole.DISTRICT_SUPERVISOR))

        assertTrue(UserRole.CENTRAL_SUPERVISOR.canProvisionUsers)
        assertTrue(UserRole.GOVERNORATE_SUPERVISOR.canProvisionUsers)
        assertFalse(UserRole.DISTRICT_SUPERVISOR.canProvisionUsers)
        assertFalse(UserRole.ENUMERATOR.canProvisionUsers)
    }
}
