package com.elegant.software.blitzpay.merchant.persistence

import com.elegant.software.blitzpay.merchant.api.ThemeApp
import com.elegant.software.blitzpay.merchant.api.WhiteLabelSettings
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantTheme
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantWhiteLabel
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import liquibase.Scope
import liquibase.command.CommandScope
import liquibase.command.core.UpdateCommandStep
import liquibase.command.core.helpers.DbUrlConnectionArgumentsCommandStep
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.hibernate.cfg.Configuration
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.*

/** Real PostgreSQL coverage for schema constraints and Hibernate JSON/version mapping. */
@Testcontainers(disabledWithoutDocker = true)
class WhiteLabelPersistenceTest {
    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @Test
    fun `migration supports every app and JSON palettes round trip with optimistic revisions`() {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            val database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(connection))
            Scope.child(mapOf(Scope.Attr.resourceAccessor.name to ClassLoaderResourceAccessor())) {
                CommandScope(*UpdateCommandStep.COMMAND_NAME)
                    .addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, database)
                    .addArgumentValue(UpdateCommandStep.CHANGELOG_FILE_ARG, "db/changelog/db.changelog-master.yaml").execute()
            }
            connection.createStatement().use { it.execute(requireNotNull(javaClass.getResource("/testdata/merchant/white-label-seed.sql")).readText()) }
            if (!connection.autoCommit) connection.commit()
        }
        val settings = requireNotNull(javaClass.getResourceAsStream("/testdata/merchant/white-label.json"))
            .use { jacksonObjectMapper().readValue<WhiteLabelSettings>(it) }
        val merchantId = UUID.fromString("20400000-0000-0000-0000-000000000001")
        val config = Configuration().addAnnotatedClass(MerchantWhiteLabel::class.java).addAnnotatedClass(MerchantTheme::class.java)
            .setProperty("hibernate.connection.url", postgres.jdbcUrl)
            .setProperty("hibernate.connection.username", postgres.username)
            .setProperty("hibernate.connection.password", postgres.password)
            .setProperty("hibernate.hbm2ddl.auto", "validate")
        config.buildSessionFactory().use { factory ->
            val profiles = ThemeApp.entries.map { MerchantWhiteLabel(merchantId = merchantId, app = it, settings = settings) }
            factory.openSession().use { session ->
                val tx = session.beginTransaction()
                profiles.forEach { profile ->
                    session.persist(profile)
                    session.persist(MerchantTheme(merchantId = merchantId, name = "North", primaryColor = "#00C2FF",
                        accentColor = "#5856D6", app = profile.app, light = settings.light, dark = settings.dark))
                }
                tx.commit()
            }
            factory.openSession().use { session ->
                val tx = session.beginTransaction()
                val saved = session.find(MerchantWhiteLabel::class.java, profiles.first().id)
                assertEquals(settings, saved.settings)
                assertEquals(0, saved.revision)
                saved.settings = saved.settings.copy(title = "Updated title")
                tx.commit()
            }
            factory.openSession().use { session ->
                val saved = session.find(MerchantWhiteLabel::class.java, profiles.first().id)
                assertEquals(1, saved.revision)
                assertEquals("Updated title", saved.settings.title)
            }
        }
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            val duplicate = assertFailsWith<java.sql.SQLException> {
                connection.createStatement().use { it.execute("""
                    INSERT INTO blitzpay.merchant_white_label (id, merchant_id, app, settings)
                    VALUES ('20400000-0000-0000-0000-000000000002', '$merchantId', 'SHOPPER', '{}')
                """.trimIndent()) }
            }
            assertEquals("23505", duplicate.sqlState)
        }
    }
}
