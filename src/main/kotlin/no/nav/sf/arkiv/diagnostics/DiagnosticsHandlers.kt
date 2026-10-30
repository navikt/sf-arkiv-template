package no.nav.sf.arkiv.diagnostics

import com.google.gson.Gson
import io.prometheus.client.exporter.common.TextFormat
import mu.KotlinLogging
import no.nav.sf.arkiv.Metrics
import no.nav.sf.arkiv.database.PostgresDatabase
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status
import java.io.StringWriter

class DiagnosticsHandlers(
    private val postgresDatabase: PostgresDatabase,
    private val gson: Gson,
) {
    private val log = KotlinLogging.logger { }

    val prometheus: HttpHandler = {
        val writer = StringWriter()
        TextFormat.write004(writer, Metrics.cRegistry.metricFamilySamples())
        Response(Status.OK).body(writer.toString())
    }

    val databaseSize: HttpHandler = {
        val diagnostic = postgresDatabase.dbSize()
        log.info { "Database diagnostic: $diagnostic" }
        Response(Status.OK).header("Content-Type", "application/json").body(gson.toJson(diagnostic))
    }

    val databaseSourceGrowth: HttpHandler = {
        val diagnostics = postgresDatabase.databaseSourceGrowth()
        log.info { "Database source diagnostics: $diagnostics" }
        Response(Status.OK).header("Content-Type", "application/json").body(gson.toJson(diagnostics))
    }
}
