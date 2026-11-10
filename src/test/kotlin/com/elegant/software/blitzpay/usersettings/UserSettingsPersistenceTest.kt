package com.elegant.software.blitzpay.usersettings

import com.elegant.software.blitzpay.support.TestFixtureLoader
import com.elegant.software.blitzpay.usersettings.persistence.repository.UserSettingRepository
import org.junit.jupiter.api.Test
import org.springframework.data.jpa.repository.Query
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Exercises the migration and the repository's atomic upsert against PostgreSQL, including reconnects. */
@Testcontainers(disabledWithoutDocker = true)
class UserSettingsPersistenceTest {
    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @Test
    fun `upsert persists across connections and preserves independent preferences and accounts`() {
        val fixture = TestFixtureLoader.load("testdata/usersettings/languages.json", LanguageFixture::class.java)
        fun connect() = DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
        val migration = requireNotNull(javaClass.getResourceAsStream("/db/changelog/20261110-001-user-settings.sql"))
            .bufferedReader().use { it.readText() }.substringBefore("-- rollback")
        connect().use { connection ->
            connection.createStatement().use { it.execute("CREATE SCHEMA blitzpay"); it.execute(migration) }
        }
        val sql = UserSettingRepository::class.java.getMethod("upsert", String::class.java, String::class.java, String::class.java)
            .getAnnotation(Query::class.java).value.replace(Regex(":(?:userId|key|value)"), "?")
        fun write(user: String, key: String, value: String) = connect().use { connection ->
            connection.prepareStatement(sql).use {
                it.setString(1, user); it.setString(2, key); it.setString(3, value); it.executeUpdate()
            }
        }
        write(fixture.user, "unrelated", "keep")
        write(fixture.user, "shopperAppLanguage", "de")
        write(fixture.user, "merchantAppLanguage", "de")
        write(fixture.user, "shopperAppLanguage", "en")
        write(fixture.otherUser, "shopperAppLanguage", "de")
        connect().use { connection ->
            connection.prepareStatement("SELECT setting_key, setting_value FROM blitzpay.usersettings_preferences WHERE user_id = ?").use {
                it.setString(1, fixture.user)
                val values = mutableMapOf<String, String>()
                it.executeQuery().use { rows -> while (rows.next()) values[rows.getString(1)] = rows.getString(2) }
                assertEquals(mapOf("unrelated" to "keep", "shopperAppLanguage" to "en", "merchantAppLanguage" to "de"), values)
            }
        }
        assertFailsWith<java.sql.SQLException> { write(fixture.user, "shopperAppLanguage", "fr") }
    }
}
