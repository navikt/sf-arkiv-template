package no.nav.sf.arkiv.api

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import mu.KotlinLogging
import no.nav.sf.arkiv.HEADER_KONFIDENTIELLT_SKIPPED
import no.nav.sf.arkiv.Metrics
import no.nav.sf.arkiv.api.model.Arkiv
import no.nav.sf.arkiv.api.model.Hente
import no.nav.sf.arkiv.api.model.hasValidDokumentDato
import no.nav.sf.arkiv.api.model.isEmpty
import no.nav.sf.arkiv.config_HENTE_ALLOWED
import no.nav.sf.arkiv.database.PostgresDatabase
import no.nav.sf.arkiv.database.tooLongFields
import no.nav.sf.arkiv.isDevTestBypassAllowed
import no.nav.sf.arkiv.token.TokenValidator
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status
import java.sql.SQLTransientConnectionException

val isHenteAllowed: Boolean = System.getenv(config_HENTE_ALLOWED)?.lowercase() == "true"

class ApiHandlers(
    private val postgresDatabase: PostgresDatabase,
    private val tokenValidator: TokenValidator,
    private val gson: Gson,
) {
    private val log = KotlinLogging.logger { }

    private val openApiSpec = javaClass.getResource("/openapi.yaml")!!.readText()

    val openApi: HttpHandler = {
        Response(Status.OK).header("Content-Type", "application/yaml").body(openApiSpec)
    }

    // Trailing slash ensures the browser resolves relative CSS and JavaScript URLs under /internal/swagger-ui/.
    val swaggerUiTrailingSlash =
        Filter { next ->
            { r ->
                if (r.uri.path ==
                    "/internal/swagger-ui"
                ) {
                    Response(Status.MOVED_PERMANENTLY).header("Location", "/internal/swagger-ui/")
                } else {
                    next(r)
                }
            }
        }

    val authPing: HttpHandler = { r ->
        Response(Status.OK).body("Auth: ${tokenValidator.firstValidToken(r) != null}")
    }

    val arkiv: HttpHandler = { r ->
        Metrics.requestArkiv.inc()
        try {
            val requestBody = r.bodyString()
            val typeToken = object : TypeToken<List<Arkiv>>() {}.type
            val jsonItems = JsonParser.parseString(requestBody)
            val validKonfidentielltValues = hasValidKonfidentielltValues(jsonItems)
            val parsedItems = if (validKonfidentielltValues) gson.fromJson<List<Arkiv?>?>(requestBody, typeToken) else null
            val devBypass =
                isDevTestBypassAllowed &&
                    jsonItems is JsonArray &&
                    jsonItems.firstOrNull() is JsonObject &&
                    jsonItems.first().asJsonObject.get("kilde")?.let {
                        it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString == "test"
                    } == true
            val firstValidToken = tokenValidator.firstValidToken(r)
            if (devBypass || firstValidToken != null) {
                log.info { "Authorized call to Arkiv" }
                if (jsonItems !is JsonArray || jsonItems.isEmpty()) {
                    Response(Status.BAD_REQUEST).body("Request must contain a non-empty array of archive items")
                } else if (!validKonfidentielltValues) {
                    Response(Status.BAD_REQUEST).body("konfidentiellt must be a JSON boolean: true or false")
                } else if (parsedItems.isNullOrEmpty() || parsedItems.any { it == null }) {
                    Response(Status.BAD_REQUEST).body("Request must contain a non-empty array of archive items")
                } else if (parsedItems.filterNotNull().any { !it.hasValidDokumentDato() }) {
                    Response(
                        Status.BAD_REQUEST,
                    ).body("One or more payload contain missing or invalid dokumentdato (correct format is yyyy-MM-dd)")
                } else if (parsedItems.filterNotNull().any { it.tooLongFields().isNotEmpty() }) {
                    val tooLong = parsedItems.filterNotNull().flatMap { it.tooLongFields() }.distinct()
                    Response(Status.BAD_REQUEST).body("One or more payload contain too long values: ${tooLong.joinToString()}")
                } else {
                    val arkivItems = parsedItems.filterNotNull()
                    val result = postgresDatabase.addArchive(arkivItems)
                    arkivItems.forEach {
                        Metrics.arkivItem.labels(it.opprettetAv, it.kilde, it.tema, it.konfidentiellt.toString()).inc()
                    }
                    Metrics.insertedEntries.inc(result.size.toDouble())
                    Response(Status.CREATED).header("Content-Type", "application/json").body(gson.toJson(result))
                }
            } else {
                log.info { "Arkiv call denied - missing valid token" }
                Response(Status.UNAUTHORIZED)
            }
        } catch (e: JsonParseException) {
            Metrics.issues.inc()
            log.warn { "Arkiv call with malformed JSON: ${e.message}" }
            Response(Status.BAD_REQUEST).body("Request must contain a JSON array of archive items")
        } catch (e: SQLTransientConnectionException) {
            Metrics.issues.inc()
            log.warn { "Arkiv call failed with transient connection exception: ${e.message}" }
            Response(Status.SERVICE_UNAVAILABLE).body("Caught transient connection exception, message: ${e.message}")
        }
    }

    private fun hasValidKonfidentielltValues(jsonItems: JsonElement): Boolean =
        jsonItems !is JsonArray ||
            jsonItems.all { item ->
                item !is JsonObject ||
                    !item.has("konfidentiellt") ||
                    item.get("konfidentiellt").let { value -> value.isJsonPrimitive && value.asJsonPrimitive.isBoolean }
            }

    val hente: HttpHandler = hente@{ r ->
        Metrics.requestHente.inc()
        if (!isHenteAllowed) {
            log.info { "Hente call denied - retrieval is disabled" }
            return@hente Response(Status.FORBIDDEN).body("Hente is disabled")
        }
        val henteModel =
            try {
                gson.fromJson<Hente?>(r.bodyString(), Hente::class.java)
            } catch (e: JsonParseException) {
                log.warn { "Hente call with malformed JSON: ${e.message}" }
                null
            }
        if (henteModel == null) {
            Metrics.issues.inc()
            Response(Status.BAD_REQUEST).body("Request must contain a valid JSON object with search parameters")
        } else if (isDevTestBypassAllowed && henteModel.kilde == "test" || tokenValidator.firstValidToken(r) != null) {
            log.info { "Authorized call to Hente" }
            if (henteModel.isEmpty()) {
                Response(Status.BAD_REQUEST).body("Request contains no search parameters, that is not allowed")
            } else if (!henteModel.hasValidDokumentDato()) {
                Response(Status.BAD_REQUEST).body("Request contains invalid dokumentdato (correct format is empty or yyyy-MM-dd)")
            } else if (henteModel.id.isNotEmpty() && henteModel.id.toLongOrNull() == null) {
                Response(Status.BAD_REQUEST).body("Request contains invalid id (must be an integer)")
            } else {
                val result = postgresDatabase.henteArchive(henteModel)
                log.info {
                    "Hente successful response with ${result.entries.size} entries, skipped ${result.skippedKonfidentiellt} konfidentiellt"
                }
                Response(Status.OK)
                    .header("Content-Type", "application/json")
                    .header(HEADER_KONFIDENTIELLT_SKIPPED, result.skippedKonfidentiellt.toString())
                    .body(gson.toJson(result.entries))
            }
        } else {
            Response(Status.UNAUTHORIZED).body("Hente call denied - missing valid token")
        }
    }
}
