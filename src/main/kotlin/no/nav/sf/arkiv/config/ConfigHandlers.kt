package no.nav.sf.arkiv.config

import no.nav.sf.arkiv.config_DB_INIT_ALLOWED
import no.nav.sf.arkiv.config_DEV_TEST_BYPASS_ALLOWED
import no.nav.sf.arkiv.config_DUMMY_READ_TO_TEST_READINESS
import no.nav.sf.arkiv.config_HENTE_ALLOWED
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status

class ConfigHandlers {
    val configHandler: HttpHandler = {
        Response(Status.OK).header("Content-Type", "application/json").body(
            config_DEV_TEST_BYPASS_ALLOWED + ":" + System.getenv(config_DEV_TEST_BYPASS_ALLOWED) + "\n" +
                config_HENTE_ALLOWED + ":" + System.getenv(config_HENTE_ALLOWED) + "\n" +
                config_DB_INIT_ALLOWED + ":" + System.getenv(config_DB_INIT_ALLOWED) + "\n" +
                config_DUMMY_READ_TO_TEST_READINESS + ":" + System.getenv(config_DUMMY_READ_TO_TEST_READINESS),
        )
    }
}
