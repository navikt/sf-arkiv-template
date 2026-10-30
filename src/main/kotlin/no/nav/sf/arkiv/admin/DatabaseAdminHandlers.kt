package no.nav.sf.arkiv.admin

import no.nav.sf.arkiv.config_DB_INIT_ALLOWED
import no.nav.sf.arkiv.config_HENTE_ALLOWED
import no.nav.sf.arkiv.database.PostgresDatabase
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status

val isDbInitAllowed: Boolean = System.getenv(config_DB_INIT_ALLOWED)?.lowercase() == "true"

class DatabaseAdminHandlers(
    private val postgresDatabase: PostgresDatabase,
) {
    val initDb: HttpHandler = {
        if (!isDbInitAllowed) {
            Response(Status.FORBIDDEN).body("DB init is disabled")
        } else {
            postgresDatabase.createArkivTable(false)
            Response(Status.OK).body("Tables created")
        }
    }
}
