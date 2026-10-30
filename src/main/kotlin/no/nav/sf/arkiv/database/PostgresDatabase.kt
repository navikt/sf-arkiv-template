package no.nav.sf.arkiv.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import no.nav.sf.arkiv.api.model.Arkiv
import no.nav.sf.arkiv.api.model.ArkivResponse
import no.nav.sf.arkiv.api.model.Hente
import no.nav.sf.arkiv.api.model.HenteResult
import no.nav.sf.arkiv.api.model.hasValidDokumentDato
import no.nav.sf.arkiv.diagnostics.model.DatabaseDiagnostic
import no.nav.sf.arkiv.diagnostics.model.DatabaseSourceDiagnostic
import no.nav.sf.arkiv.env
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction

// NAIS Db Prefix consists of NAIS_DATABASE_<app name>_<db_name>_
const val NAIS_DB_JDBC_URL = "NAIS_DATABASE_SF_ARKIV_ARKIV_JDBC_URL"

class PostgresDatabase(
    val database: Database = Database.connect(HikariDataSource(hikariConfig())),
) {
    private val log = KotlinLogging.logger { }

    companion object {
        private fun hikariConfig(): HikariConfig =
            HikariConfig().apply {
                jdbcUrl = env(NAIS_DB_JDBC_URL)
                driverClassName = "org.postgresql.Driver"
                maximumPoolSize = 5
                minimumIdle = 1
                connectionTimeout = 10_000
                validationTimeout = 5_000
                idleTimeout = 300_000
                keepaliveTime = 120_000
                maxLifetime = 1_800_000
                isAutoCommit = false
                // Isolation level that ensure the same snapshot of db during one transaction:
                transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            }
    }

    fun addArchive(items: List<Arkiv>): List<ArkivResponse> {
        require(items.isNotEmpty()) { "Archive must contain at least one item" }
        require(items.all { it.hasValidDokumentDato() }) { "dokumentdato must use yyyy-MM-dd" }
        return transaction(database) { items.map { ArkivTable.insert(it) } }
    }

    fun henteArchive(filter: Hente): HenteResult =
        transaction(database) {
            HenteResult(
                entries = ArkivTable.select(filter).andWhere { ArkivTable.konfidentiellt eq false }.map { it.toHenteResponse() },
                skippedKonfidentiellt = ArkivTable.select(filter).andWhere { ArkivTable.konfidentiellt eq true }.count(),
            )
        }

    fun dbSize(): DatabaseDiagnostic = transaction(database) { tableDiagnostic() }

    fun databaseSourceGrowth(): List<DatabaseSourceDiagnostic> = transaction(database) { sourceDiagnostics() }

    fun createArkivTable(dropFirst: Boolean = false) {
        transaction(database) {
            if (dropFirst) {
                log.info { "Dropping table arkiv" }
                val dropStatement =
                    TransactionManager.current().connection.prepareStatement("DROP TABLE arkiv", false)
                dropStatement.executeUpdate()
                log.info { "Drop performed of Arkiv" }
            }

            log.info { "Creating table Arkiv" }
            SchemaUtils.create(ArkivTable)
        }
    }
}
