package no.nav.sf.arkiv

import com.google.gson.GsonBuilder
import mu.KotlinLogging
import no.nav.sf.arkiv.admin.DatabaseAdminHandlers
import no.nav.sf.arkiv.admin.isDbInitAllowed
import no.nav.sf.arkiv.api.ApiHandlers
import no.nav.sf.arkiv.api.isHenteAllowed
import no.nav.sf.arkiv.api.model.Hente
import no.nav.sf.arkiv.config.ConfigHandlers
import no.nav.sf.arkiv.database.PostgresDatabase
import no.nav.sf.arkiv.diagnostics.DiagnosticsHandlers
import no.nav.sf.arkiv.gui.filesHandler
import no.nav.sf.arkiv.token.DefaultTokenValidator
import no.nav.sf.arkiv.token.TokenValidator
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.routing.ResourceLoader
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static
import org.http4k.server.Http4kServer
import org.http4k.server.Netty
import org.http4k.server.asServer
import java.io.File
import java.lang.RuntimeException

const val NAIS_DEFAULT_PORT = 8080

const val HEADER_KONFIDENTIELLT_SKIPPED = "x-konfidentiellt-skipped"

val isDev: Boolean = System.getenv("NAIS_CLUSTER_NAME") == "dev-gcp"
val isDevTestBypassAllowed: Boolean = isDev && System.getenv(config_DEV_TEST_BYPASS_ALLOWED)?.lowercase() == "true"
val isDummyReadToTestReadiness = System.getenv(config_DUMMY_READ_TO_TEST_READINESS)?.lowercase() == "true"

private val log = KotlinLogging.logger { }

class Application(
    val tokenValidator: TokenValidator = DefaultTokenValidator(),
    val postgresDatabase: PostgresDatabase = PostgresDatabase(),
) {
    private val log = KotlinLogging.logger { }
    val gson = GsonBuilder().setPrettyPrinting().create()

    fun start() {
        log.info {
            "Starting ${if (isDev) "DEV" else "PROD"}, dev test bypass allowed: $isDevTestBypassAllowed, hente allowed:" +
                " $isHenteAllowed, db init Allowed $isDbInitAllowed, dummy read to test readiness $isDummyReadToTestReadiness"
        }

        val dir = File("/tmp/files")
        dir.mkdirs() // ensures /tmp/files exists

        apiServer(NAIS_DEFAULT_PORT).start()

        if (isDummyReadToTestReadiness) doDummySearch(postgresDatabase)

        isReady = true // If made it so far connection established kubernetes can allow traffic
    }

    fun apiServer(port: Int): Http4kServer = api().asServer(Netty(port))

    @Volatile
    var isReady: Boolean = false

    private val apiHandlers = ApiHandlers(postgresDatabase, tokenValidator, gson)
    private val diagnosticsHandlers = DiagnosticsHandlers(postgresDatabase, gson)
    private val databaseAdminHandlers = DatabaseAdminHandlers(postgresDatabase)
    private val configHandlers = ConfigHandlers()

    private val responseDefaults =
        Filter { next ->
            { r ->
                val response =
                    try {
                        next(r)
                    } catch (e: Exception) {
                        Metrics.issues.inc()
                        log.error(e) { "Unexpected error on ${r.method} ${r.uri.path}" }
                        Response(Status.INTERNAL_SERVER_ERROR).body("Internal server error")
                    }
                if (response.header("Content-Type") == null && response.body.length != 0L) {
                    response.header("Content-Type", "text/plain; charset=utf-8")
                } else {
                    response
                }
            }
        }

    fun api(): HttpHandler =
        responseDefaults.then(
            routes(
                "/internal/swagger-ui" bind apiHandlers.swaggerUiTrailingSlash.then(static(ResourceLoader.Classpath("/swagger-ui"))),
                "/internal/openapi.yaml" bind Method.GET to apiHandlers.openApi,
                "/internal/isAlive" bind Method.GET to { Response(OK) },
                "/internal/isReady" bind Method.GET to { if (isReady) Response(OK) else Response(Status.SERVICE_UNAVAILABLE) },
                "/internal/prometheus" bind Method.GET to diagnosticsHandlers.prometheus,
                "/internal/activeConfig" bind Method.GET to configHandlers.configHandler,
                "/internal/files" bind Method.GET to filesHandler(File("/tmp/files")),
                "/internal/files/{path:.*}" bind Method.GET to filesHandler(File("/tmp/files")),
                "/internal/initDb" bind Method.GET to databaseAdminHandlers.initDb,
                "/internal/dbSize" bind Method.GET to diagnosticsHandlers.databaseSize,
                "/internal/dbGrowth" bind Method.GET to diagnosticsHandlers.databaseSourceGrowth,
                "/authping" bind Method.GET to apiHandlers.authPing,
                "/arkiv" bind Method.POST to apiHandlers.arkiv,
                "/hente" bind Method.POST to apiHandlers.hente,
            ),
        )
}

fun doDummySearch(postgresDatabase: PostgresDatabase) {
    val henteModel = Hente(kilde = "test")
    try {
        File("/tmp/files/dummy-search-result").writeText(postgresDatabase.henteArchive(henteModel).entries.joinToString("\n"))
    } catch (e: Exception) {
        log.error { "Exception at henteArchive test call at application boot " + e.message }
        throw RuntimeException("Exception at henteArchive test call at application boot " + e.message)
    }
}
