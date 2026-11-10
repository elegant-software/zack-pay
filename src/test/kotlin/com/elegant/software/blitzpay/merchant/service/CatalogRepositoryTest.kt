package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantProduct
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantProductRepository
import com.elegant.software.blitzpay.support.TestFixtureLoader
import jakarta.persistence.EntityManager
import liquibase.Liquibase
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.hibernate.SessionFactory
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.math.BigDecimal
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.*

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogRepositoryTest {
    companion object {
        @Container
        @JvmField
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    private val fixture = TestFixtureLoader.load("testdata/merchant/mobile-catalog.json", CatalogFixture::class.java)
    private lateinit var factory: SessionFactory
    private lateinit var entityManager: EntityManager
    private lateinit var repository: MerchantProductRepository
    private val pageSize = PageRequest.of(0, 2)

    @BeforeAll
    fun migrateAndBuildRepository() {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            val schema = requireNotNull(javaClass.classLoader.getResourceAsStream("testdata/merchant/catalog-schema-before-thumbnail.sql"))
                .bufferedReader().use { it.readText() }
            connection.createStatement().use { it.execute(schema) }
            Liquibase("db/changelog/20261110-001-mobile-catalog.sql", ClassLoaderResourceAccessor(), JdbcConnection(connection))
                .use { it.update() }
        }
        val registry = StandardServiceRegistryBuilder().applySettings(mapOf(
            "jakarta.persistence.jdbc.url" to postgres.jdbcUrl,
            "jakarta.persistence.jdbc.user" to postgres.username,
            "jakarta.persistence.jdbc.password" to postgres.password,
            "hibernate.hbm2ddl.auto" to "none",
            "hibernate.default_schema" to "blitzpay",
        )).build()
        factory = MetadataSources(registry).addAnnotatedClass(MerchantProduct::class.java).buildMetadata().buildSessionFactory()
    }

    @BeforeEach
    fun seed() {
        entityManager = factory.createEntityManager()
        entityManager.transaction.begin()
        fixture.productIds.forEachIndexed { index, id ->
            entityManager.persist(product(id, category = if (index == 2) null else fixture.categoryId, active = index != 1))
        }
        entityManager.persist(product(UUID(0, 1), branch = UUID(0, 2)))
        entityManager.persist(product(UUID(0, 3), merchant = UUID(0, 4)))
        entityManager.flush()
        entityManager.clear()
        entityManager.createNativeQuery("SET LOCAL ROLE catalog_reader").executeUpdate()
        entityManager.createNativeQuery("SELECT set_config('app.current_merchant_id', :merchant, true)")
            .setParameter("merchant", fixture.merchantId.toString()).singleResult
        repository = JpaRepositoryFactory(entityManager).getRepository(MerchantProductRepository::class.java)
    }

    @AfterEach
    fun rollback() {
        if (entityManager.transaction.isActive) entityManager.transaction.rollback()
        entityManager.close()
    }

    @AfterAll
    fun closeFactory() { if (::factory.isInitialized) factory.close() }

    @Test
    fun `real PostgreSQL keyset handles equal timestamps and terminal pages without leaking branches`() {
        val first = repository.findCatalogPage(fixture.merchantId, fixture.branchId, null, false, true, null, null, pageSize)
        assertEquals(fixture.productIds.take(2), first.map { it.id })
        val second = repository.findCatalogPage(fixture.merchantId, fixture.branchId, null, false, true,
            first.last().createdAt, first.last().id, pageSize)
        assertEquals(listOf(fixture.productIds[2]), second.map { it.id })
        assertTrue(repository.findCatalogPage(fixture.merchantId, fixture.branchId, null, false, true,
            second.last().createdAt, second.last().id, pageSize).isEmpty())
    }

    @Test
    fun `real PostgreSQL filters active products category and uncategorized before limiting`() {
        assertEquals(listOf(fixture.productIds[0]), repository.findCatalogPage(fixture.merchantId, fixture.branchId,
            fixture.categoryId, false, false, null, null, pageSize).map { it.id })
        assertEquals(listOf(fixture.productIds[2]), repository.findCatalogPage(fixture.merchantId, fixture.branchId,
            null, true, false, null, null, pageSize).map { it.id })
        val activeCounts = repository.catalogCategoryCounts(fixture.merchantId, fixture.branchId, false).associate { it.categoryId to it.productCount }
        assertEquals(mapOf(fixture.categoryId to 1L, null to 1L), activeCounts)
        val allCounts = repository.catalogCategoryCounts(fixture.merchantId, fixture.branchId, true).associate { it.categoryId to it.productCount }
        assertEquals(mapOf(fixture.categoryId to 2L, null to 1L), allCounts)
    }

    @Test
    fun `thumbnail attachment is conditional and does not modify full image or product timestamp`() {
        val id = fixture.productIds[0]
        assertEquals(0, repository.attachCatalogThumbnail(id, fixture.merchantId, "replaced.jpg", fixture.createdAt, "thumb.jpg"))
        assertEquals(1, repository.attachCatalogThumbnail(id, fixture.merchantId, fixture.detailKey, fixture.createdAt, "thumb.jpg"))
        assertEquals(0, repository.attachCatalogThumbnail(id, fixture.merchantId, fixture.detailKey, fixture.createdAt, "other-thumb.jpg"))
        entityManager.clear()
        val stored = repository.findById(id).orElseThrow()
        assertEquals(fixture.detailKey, stored.imageStorageKey)
        assertEquals("thumb.jpg", stored.thumbnailStorageKey)
        assertEquals(fixture.createdAt, stored.updatedAt)
    }

    @Test
    fun `row security blocks a foreign merchant even when requested explicitly`() {
        assertTrue(repository.findCatalogPage(UUID(0, 4), fixture.branchId, null, false, true, null, null, pageSize).isEmpty())
    }

    private fun product(id: UUID, merchant: UUID = fixture.merchantId, branch: UUID = fixture.branchId, category: UUID? = fixture.categoryId, active: Boolean = true) =
        MerchantProduct(id = id, merchantId = merchant, merchantBranchId = branch, productCategoryId = category,
            name = "Coffee", unitPrice = BigDecimal.TEN, active = active, createdAt = fixture.createdAt, imageStorageKey = fixture.detailKey)
}
