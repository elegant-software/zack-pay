package com.elegant.software.blitzpay.merchant.service

import liquibase.ChecksumVersion
import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.changelog.ChangeLogParameters
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.parser.ChangeLogParserFactory
import liquibase.resource.ClassLoaderResourceAccessor
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.assertEquals

class LanguageCatalogueMigrationTest {
    private val original = "db/changelog/20261108-003-shared-language-catalogue.sql"
    private val oldFixture = "testdata/merchant/original-shared-language-catalogue.sql"

    @Test
    fun `adding preconditions preserves the published migration checksum`() {
        ClassLoaderResourceAccessor().use { resources ->
            val parser = ChangeLogParserFactory.getInstance()
            fun checksum(path: String) = parser.getParser(path, resources)
                .parse(path, ChangeLogParameters(), resources).changeSets.single()
                .generateCheckSum(ChecksumVersion.latest())
            assertEquals(checksum(oldFixture), checksum(original))
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["fresh", "partial", "existing"])
    fun `migration handles fresh partial and existing columns without losing menu data`(state: String) {
        PostgreSQLContainer("postgres:16.2").use { postgres ->
            postgres.start()
            DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
                executeFixture(connection, "testdata/merchant/language-migration-baseline.sql")
                if (state == "partial") executeFixture(connection, "testdata/merchant/language-migration-partial.sql")
                if (state == "existing") executeFixture(connection, oldFixture)
                val database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(connection))
                database.defaultSchemaName = "blitzpay"
                database.liquibaseSchemaName = "public"
                ClassLoaderResourceAccessor().use { resources ->
                    Liquibase("testdata/merchant/language-migration-changelog.yaml", resources, database).use { liquibase ->
                        liquibase.update(Contexts(), LabelExpression())
                        // A second startup must validate the recorded checksums and perform no duplicate DDL.
                        liquibase.update(Contexts(), LabelExpression())
                        connection.createStatement().use { statement ->
                            statement.executeQuery("SELECT direction, flag_code, admin_ui_enabled, menu_enabled FROM blitzpay.merchant_menu_languages WHERE code = 'ar'").use { rows ->
                                check(rows.next())
                                assertEquals("rtl", rows.getString(1))
                                assertEquals("SA", rows.getString(2))
                                assertEquals(true, rows.getBoolean(3))
                                assertEquals(true, rows.getBoolean(4))
                            }
                            statement.executeQuery("SELECT language FROM blitzpay.merchant_supported_menu_languages WHERE merchant_id = 'merchant-1'").use { rows ->
                                check(rows.next())
                                assertEquals("ar", rows.getString(1))
                            }
                            statement.executeQuery("SELECT name, description FROM blitzpay.merchant_product_translations WHERE product_id = 'product-1'").use { rows ->
                                check(rows.next())
                                assertEquals("Translated name", rows.getString(1))
                                assertEquals("Translated description", rows.getString(2))
                            }
                            if (state == "partial") {
                                statement.executeQuery("SELECT flag_code FROM blitzpay.merchant_menu_languages WHERE code = 'fr'").use { rows ->
                                    check(rows.next())
                                    assertEquals("FR", rows.getString(1))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun executeFixture(connection: Connection, path: String) {
        val sql = requireNotNull(javaClass.getResourceAsStream("/$path"))
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        connection.createStatement().use { it.execute(sql) }
    }
}
