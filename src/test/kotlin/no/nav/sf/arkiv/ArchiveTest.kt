package no.nav.sf.arkiv

import com.google.gson.Gson
import no.nav.security.token.support.core.jwt.JwtToken
import no.nav.sf.arkiv.api.model.Arkiv
import no.nav.sf.arkiv.api.model.ArkivResponse
import no.nav.sf.arkiv.api.model.Hente
import no.nav.sf.arkiv.api.model.HenteResponse
import no.nav.sf.arkiv.api.model.hasValidDokumentDato
import no.nav.sf.arkiv.database.ArkivTable
import no.nav.sf.arkiv.database.PostgresDatabase
import no.nav.sf.arkiv.token.TokenValidator
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class ArchiveTest {
    private val gson = Gson()
    private lateinit var database: PostgresDatabase
    private lateinit var application: Application

    @Before
    fun setUp() {
        database =
            PostgresDatabase(
                Database.connect(
                    url = "jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                    driver = "org.h2.Driver",
                ),
            )
        database.createArkivTable()
        val validator =
            object : TokenValidator {
                override fun firstValidToken(request: Request): JwtToken = JwtToken("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.c2lnbmF0dXJl")
            }
        application = Application(validator, database)
    }

    @Test
    fun `POST accepts the existing array model and stores every field`() {
        val document = "a".repeat(31)
        val items =
            listOf(
                Arkiv(
                    opprettetAv = "example",
                    kilde = "salesforce",
                    dokumentasjonId = "doc1",
                    dokumentasjon = document,
                    dokumentdato = "2026-10-20",
                    aktoerid = "123",
                    fnr = "456",
                    orgnr = "789",
                    tema = "IPS",
                    konfidentiellt = true,
                ),
                Arkiv(dokumentdato = "2026-10-21", dokumentasjon = "short"),
            )

        val response = application.api()(Request(Method.POST, "/arkiv").body(gson.toJson(items)))

        assertEquals(Status.CREATED, response.status)
        assertEquals("application/json", response.header("Content-Type"))
        val results = gson.fromJson(response.bodyString(), Array<ArkivResponse>::class.java)
        assertEquals(2, results.size)
        assertTrue(results[0].id > 0)
        assertTrue(results[1].id > results[0].id)
        assertEquals("31 characters", results[0].dokumentasjonSummarized)
        assertEquals("5 characters", results[1].dokumentasjonSummarized)
        assertEquals(items[0].dokumentdato, results[0].dokumentdato)
        assertTrue(results[0].konfidentiellt)
        assertFalse(results[1].konfidentiellt)
        assertTrue(results[0].dato.isNotEmpty())
        for (identifying in listOf("aktoerid", "fnr", "orgnr", "tema")) {
            assertFalse(identifying, response.bodyString().contains("\"$identifying\""))
        }

        val stored = transaction(database.database) { ArkivTable.selectAll().where { ArkivTable.id eq results[0].id }.single() }
        assertEquals(items[0].opprettetAv, stored[ArkivTable.opprettetAv])
        assertEquals(items[0].kilde, stored[ArkivTable.kilde])
        assertEquals(items[0].dokumentasjonId, stored[ArkivTable.dokumentasjonId])
        assertEquals(document, stored[ArkivTable.dokumentasjon])
        assertEquals(items[0].aktoerid, stored[ArkivTable.aktoerid])
        assertEquals(items[0].fnr, stored[ArkivTable.fnr])
        assertEquals(items[0].orgnr, stored[ArkivTable.orgnr])
        assertEquals(items[0].tema, stored[ArkivTable.tema])
        assertTrue(stored[ArkivTable.konfidentiellt])
    }

    @Test
    fun `missing malformed and impossible document dates reject the entire request`() {
        val invalidItems =
            listOf(
                "{}",
                """{"dokumentdato":null}""",
                """{"dokumentdato":""}""",
                """{"dokumentdato":"2026-02-30"}""",
                """{"dokumentdato":"2026-1-01"}""",
                """{"dokumentdato":"20-10-2026"}""",
                """{"dokumentdato":"2026-10-20T12:00:00"}""",
            )
        for (invalid in invalidItems) {
            val response =
                application.api()(
                    Request(Method.POST, "/arkiv").body("""[{"dokumentdato":"2026-10-20"},$invalid]"""),
                )
            assertEquals(invalid, Status.BAD_REQUEST, response.status)
        }
        assertEquals(0L, rowCount())
    }

    @Test
    fun `empty null and malformed payloads return bad request`() {
        for (payload in listOf("[]", "null", "[null]", "{}", "[", "")) {
            val response = application.api()(Request(Method.POST, "/arkiv").body(payload))
            assertEquals(payload, Status.BAD_REQUEST, response.status)
        }
        assertEquals(0L, rowCount())
    }

    @Test
    fun `konfidentiellt must be omitted or a boolean`() {
        for (value in listOf("\"true\"", "\"false\"", "\"tru\"", "null", "0", "1", "{}", "[]")) {
            val payload = """[{"dokumentdato":"2026-10-20","konfidentiellt":$value}]"""
            val response = application.api()(Request(Method.POST, "/arkiv").body(payload))
            assertEquals(value, Status.BAD_REQUEST, response.status)
            assertTrue(response.bodyString(), response.bodyString().contains("konfidentiellt must be a JSON boolean"))
        }

        val accepted =
            application.api()(
                Request(Method.POST, "/arkiv")
                    .body("""[{"dokumentdato":"2026-10-20","konfidentiellt":false}]"""),
            )
        assertEquals(Status.CREATED, accepted.status)
    }

    @Test
    fun `too long values return bad request naming the fields`() {
        val payload = """[{"kilde":"test","dokumentdato":"2026-10-20","dokumentasjonId":"${"1".repeat(21)}","fnr":"${"1".repeat(14)}"}]"""
        val response = application.api()(Request(Method.POST, "/arkiv").body(payload))
        assertEquals(Status.BAD_REQUEST, response.status)
        assertTrue(response.bodyString(), response.bodyString().contains("dokumentasjonId (max 20 characters)"))
        assertTrue(response.bodyString(), response.bodyString().contains("fnr (max 13 characters)"))
        assertEquals("text/plain; charset=utf-8", response.header("Content-Type"))
        assertEquals(0L, rowCount())
    }

    @Test
    fun `malformed hente payloads return bad request`() {
        for (payload in listOf("""{"kilde": "test",}""", "null", "", "{", "[]")) {
            val response = application.api()(Request(Method.POST, "/hente").body(payload))
            assertEquals(payload, Status.BAD_REQUEST, response.status)
        }
    }

    @Test
    fun `unauthorized requests are rejected without storing anything`() {
        val validator =
            object : TokenValidator {
                override fun firstValidToken(request: Request): JwtToken? = null
            }
        val api = Application(validator, database).api()

        val response =
            api(
                Request(Method.POST, "/arkiv")
                    .body("""[{"kilde":"salesforce","dokumentdato":"2026-10-20"}]"""),
            )
        assertEquals(Status.UNAUTHORIZED, response.status)
        assertEquals(0L, rowCount())
    }

    @Test
    fun `unexpected errors return a generic internal server error`() {
        val validator =
            object : TokenValidator {
                override fun firstValidToken(request: Request): JwtToken = throw IllegalStateException("secret detail")
            }
        val response = Application(validator, database).api()(Request(Method.GET, "/authping"))
        assertEquals(Status.INTERNAL_SERVER_ERROR, response.status)
        assertEquals("Internal server error", response.bodyString())
        assertEquals("text/plain; charset=utf-8", response.header("Content-Type"))
    }

    @Test
    fun `a failed insert rolls back all items in the transaction`() {
        transaction(database.database) {
            exec("ALTER TABLE arkiv ADD CONSTRAINT reject_invalid_tema CHECK (tema <> 'invalid')")
        }
        val items =
            listOf(
                Arkiv(dokumentdato = "2026-10-20"),
                Arkiv(dokumentdato = "2026-10-20", tema = "invalid"),
            )
        assertThrows(ExposedSQLException::class.java) { database.addArchive(items) }
        assertEquals(0L, rowCount())
    }

    @Test
    fun `POST hente reads the same archive using combined filters`() {
        database.addArchive(
            listOf(
                Arkiv(kilde = "one", tema = "IPS", dokumentdato = "2026-10-20"),
                Arkiv(kilde = "one", tema = "OTHER", dokumentdato = "2026-10-20"),
                Arkiv(kilde = "two", tema = "IPS", dokumentdato = "2026-10-20"),
            ),
        )

        val response =
            application.api()(
                Request(Method.POST, "/hente").body("""{"kilde":"one","tema":"IPS","dokumentdato":"2026-10-20"}"""),
            )

        assertEquals(Status.OK, response.status)
        val results = gson.fromJson(response.bodyString(), Array<HenteResponse>::class.java)
        assertEquals(1, results.size)
        assertEquals("one", results.single().kilde)
        assertEquals("IPS", results.single().tema)
    }

    @Test
    fun `POST hente skips konfidentiellt entries and reports the count in a header`() {
        database.addArchive(
            listOf(
                Arkiv(fnr = "1", dokumentdato = "2026-10-20", dokumentasjon = "open"),
                Arkiv(fnr = "1", dokumentdato = "2026-10-20", dokumentasjon = "secret", konfidentiellt = true),
                Arkiv(fnr = "1", dokumentdato = "2026-10-20", dokumentasjon = "secret", konfidentiellt = true),
                Arkiv(fnr = "2", dokumentdato = "2026-10-20", konfidentiellt = true),
            ),
        )

        val response = application.api()(Request(Method.POST, "/hente").body("""{"fnr":"1"}"""))

        assertEquals(Status.OK, response.status)
        val results = gson.fromJson(response.bodyString(), Array<HenteResponse>::class.java)
        assertEquals(listOf("open"), results.map { it.dokumentasjon })
        assertEquals("2", response.header("x-konfidentiellt-skipped"))
    }

    @Test
    fun `document date validation requires a full valid date only for writes`() {
        assertFalse(Arkiv().hasValidDokumentDato())
        assertTrue(Arkiv(dokumentdato = "2024-02-29").hasValidDokumentDato())
        assertFalse(Arkiv(dokumentdato = "2025-02-29").hasValidDokumentDato())
        assertTrue(Hente().hasValidDokumentDato())
        assertFalse(Hente(dokumentdato = "invalid").hasValidDokumentDato())
    }

    @Test
    fun `swagger ui and openapi spec are served`() {
        val api = application.api()

        val redirect = api(Request(Method.GET, "/internal/swagger-ui"))
        assertEquals(Status.MOVED_PERMANENTLY, redirect.status)
        assertEquals("/internal/swagger-ui/", redirect.header("Location"))

        val page = api(Request(Method.GET, "/internal/swagger-ui/"))
        assertEquals(page.toString(), Status.OK, page.status)
        assertTrue(page.bodyString().contains("url: \"/internal/openapi.yaml\""))

        for (asset in listOf("swagger-ui.css", "swagger-ui-bundle.js", "swagger-ui-standalone-preset.js")) {
            assertEquals(asset, Status.OK, api(Request(Method.GET, "/internal/swagger-ui/$asset")).status)
        }

        val spec = api(Request(Method.GET, "/internal/openapi.yaml"))
        assertEquals(Status.OK, spec.status)
        assertTrue(spec.bodyString().startsWith("openapi: 3.0.1"))
        for (path in listOf("/arkiv:", "/hente:", "/authping:")) {
            assertTrue(path, spec.bodyString().contains(path))
        }
        for (path in listOf("/swagger-ui", "/swagger-ui/", "/swagger-ui/index.html", "/openapi.yaml")) {
            assertEquals(path, Status.NOT_FOUND, api(Request(Method.GET, path)).status)
        }
    }

    private fun rowCount(): Long = transaction(database.database) { ArkivTable.selectAll().count() }
}
