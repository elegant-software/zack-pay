package com.elegant.software.blitzpay.usersettings

import com.elegant.software.blitzpay.support.TestFixtureLoader
import com.elegant.software.blitzpay.usersettings.domain.AppLanguage
import com.elegant.software.blitzpay.usersettings.persistence.model.*
import com.elegant.software.blitzpay.usersettings.persistence.repository.UserSettingRepository
import com.elegant.software.blitzpay.usersettings.service.UserSettingsService
import com.elegant.software.blitzpay.merchant.api.AccountDeleted
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import kotlin.test.assertEquals
import kotlin.test.assertNull

data class LanguageFixture(
    val user: String, val otherUser: String, val originalName: String,
    val originalDescription: String, val unitPrice: String, val quantity: Int, val expectedTotalMinor: Long,
)

class UserSettingsServiceTest {
    private val fixture = TestFixtureLoader.load("testdata/usersettings/languages.json", LanguageFixture::class.java)
    private val repository = mock<UserSettingRepository>()
    private val service = UserSettingsService(repository)

    @Test
    fun `preferences survive service recreation and remain independent and account scoped`() {
        val stored = mutableMapOf(UserSettingId(fixture.user, "unrelated") to "keep")
        whenever(repository.findAllByIdUserId(any())).thenAnswer { invocation ->
            stored.filterKeys { it.userId == invocation.getArgument<String>(0) }.map { UserSetting(it.key, it.value) }
        }
        doAnswer { invocation ->
            stored[UserSettingId(invocation.getArgument(0), invocation.getArgument(1))] = invocation.getArgument(2)
            null
        }.whenever(repository).upsert(any(), any(), any())

        service.update(fixture.user, AppLanguage.DE, null)
        assertEquals(AppLanguage.DE, UserSettingsService(repository).get(fixture.user).shopperAppLanguage)
        assertNull(service.get(fixture.user).merchantAppLanguage)
        service.update(fixture.user, null, AppLanguage.DE)
        service.update(fixture.user, AppLanguage.EN, null)
        assertEquals(AppLanguage.EN, service.get(fixture.user).shopperAppLanguage)
        assertEquals(AppLanguage.DE, service.get(fixture.user).merchantAppLanguage)
        assertNull(service.get(fixture.otherUser).merchantAppLanguage)
        assertEquals("keep", stored[UserSettingId(fixture.user, "unrelated")])
        service.update(fixture.user, null, null)
        assertEquals(AppLanguage.DE, service.get(fixture.user).merchantAppLanguage)
        verify(repository, never()).save(any())
    }

    @Test
    fun `account deletion removes only its preferences`() {
        service.on(AccountDeleted(fixture.user))
        verify(repository).deleteAllByIdUserId(fixture.user)
        verifyNoMoreInteractions(repository)
    }
}
