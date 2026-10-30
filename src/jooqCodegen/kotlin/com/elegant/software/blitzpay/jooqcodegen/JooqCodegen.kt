package com.elegant.software.blitzpay.jooqcodegen

import liquibase.Scope
import liquibase.command.CommandScope
import liquibase.command.core.UpdateCommandStep
import liquibase.command.core.helpers.DbUrlConnectionArgumentsCommandStep
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.DirectoryResourceAccessor
import org.jooq.codegen.GenerationTool
import org.jooq.meta.jaxb.Configuration
import org.jooq.meta.jaxb.Database
import org.jooq.meta.jaxb.Generate
import org.jooq.meta.jaxb.Generator
import org.jooq.meta.jaxb.Jdbc
import org.jooq.meta.jaxb.Target
import org.testcontainers.postgresql.PostgreSQLContainer
import java.io.File
import java.sql.DriverManager

private const val CHANGELOG = "db/changelog/db.changelog-master.yaml"
private const val SCHEMA = "blitzpay"

// Tables queried through jOOQ by the reporting module. Add a table here before using it in a query.
private val INCLUDED_TABLES = listOf(
    "appointments",
    "merchant_branches",
    "merchants",
    "order_items",
    "order_orders",
    "order_payment_attempts",
    "payment_settlements",
)

/**
 * Generates jOOQ sources from the real schema: starts PostgreSQL, applies the Liquibase changelog,
 * then runs jOOQ codegen against it. Invoked by `./gradlew generateJooq`.
 *
 * Args: <resources dir containing db/changelog> <output dir>
 */
fun main(args: Array<String>) {
    val (resourcesDir, outputDir) = args

    PostgreSQLContainer("postgres:16-alpine").use { postgres ->
        postgres.start()
        migrate(postgres, File(resourcesDir))
        generate(postgres, outputDir)
    }
}

private fun migrate(postgres: PostgreSQLContainer, resourcesDir: File) {
    DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
        val database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(connection))
        // Mirrors spring.liquibase.default-schema / liquibase-schema in application.yml
        database.defaultSchemaName = SCHEMA
        database.liquibaseSchemaName = "public"

        Scope.child(mapOf(Scope.Attr.resourceAccessor.name to DirectoryResourceAccessor(resourcesDir))) {
            CommandScope(*UpdateCommandStep.COMMAND_NAME)
                .addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, database)
                .addArgumentValue(UpdateCommandStep.CHANGELOG_FILE_ARG, CHANGELOG)
                .execute()
        }
    }
}

private fun generate(postgres: PostgreSQLContainer, outputDir: String) {
    GenerationTool.generate(
        Configuration()
            .withJdbc(
                Jdbc()
                    .withDriver("org.postgresql.Driver")
                    .withUrl(postgres.jdbcUrl)
                    .withUser(postgres.username)
                    .withPassword(postgres.password)
            )
            .withGenerator(
                Generator()
                    .withDatabase(
                        Database()
                            .withName("org.jooq.meta.postgres.PostgresDatabase")
                            .withInputSchema(SCHEMA)
                            .withIncludes(INCLUDED_TABLES.joinToString("|"))
                    )
                    .withGenerate(
                        Generate()
                            .withDeprecated(false)
                            .withRecords(true)
                            .withImmutablePojos(false)
                            .withFluentSetters(false)
                    )
                    .withTarget(
                        Target()
                            .withPackageName("com.elegant.software.blitzpay.jooq")
                            .withDirectory(outputDir)
                    )
            )
    )
}
